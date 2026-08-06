package io.github.stoicswe.eyeandsickle.client.credentials;

import java.util.List;
import java.util.Optional;

/**
 * Windows, through the WinRT {@code PasswordVault} driven by a PowerShell script on stdin.
 *
 * <h2>⚠ WHY NOT {@code cmdkey}, WHICH IS THE OBVIOUS TOOL</h2>
 *
 * Because {@code cmdkey /generic:X /user:Y /pass:SECRET} puts the secret in the <b>argument list</b>,
 * and arguments are readable by other processes. That is the one property this whole package exists
 * to hold, and no amount of convenience buys it back. {@code PasswordVault} is the platform's own
 * per-user credential store and takes the secret from script text delivered on standard input.
 *
 * <h2>⚠ {@code powershell.exe}, NOT {@code pwsh}</h2>
 *
 * WinRT types are reachable from Windows PowerShell 5.1, which ships with every Windows 10 and 11.
 * PowerShell 7 ({@code pwsh}) is a separate install and does <b>not</b> load WinRT assemblies by
 * default, so a machine with both would break on the newer one. The older name is the reliable one
 * here, which is the opposite of the usual advice.
 *
 * <h2>⚠ {@code -Command -} reads the SCRIPT from stdin, which is what keeps the secret off argv</h2>
 *
 * The whole script — including the literal secret — arrives on standard input. Arguments carry only
 * the interpreter's own flags. ⚠ That makes <b>quoting the injection surface</b>: a secret containing
 * a single quote would end the PowerShell string literal early and the remainder would be parsed as
 * code. {@link #escape} doubles single quotes, which is PowerShell's own escape inside a
 * single-quoted string, and single-quoted strings do no variable interpolation — so {@code $(...)}
 * and {@code $env:} in a password are inert.
 *
 * <h2>⚠ NOT VERIFIED ON A REAL WINDOWS MACHINE</h2>
 *
 * The macOS path was round-tripped against a live Keychain; this one could not be. It fails closed by
 * construction — a missing interpreter is an {@code IOException} that {@code ToolRunner} reports as
 * unavailable, and a non-zero exit is a refusal — so the risk is that it reports unavailable where it
 * would have worked, never that a credential lands somewhere it should not. <b>Round-trip it on
 * Windows before relying on it.</b>
 */
final class WindowsCredentialVault implements SecretStore {

    private static final String POWERSHELL = "powershell.exe";

    /** Loads the WinRT type. Repeated at the head of every script because each run is a fresh shell. */
    private static final String VAULT =
            "$v = New-Object Windows.Security.Credentials.PasswordVault,"
                    + "Windows.Security.Credentials,ContentType=WindowsRuntime;";

    @Override
    public boolean available() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return false;
        }
        // ⚠ Asks whether the VAULT TYPE loads, not merely whether PowerShell runs. A machine can
        // have the interpreter and not the WinRT assembly — Server core, or PowerShell 7 aliased
        // over the name — and finding that out at store() time means telling the player their
        // credential was kept when it was not.
        return run(VAULT + "exit 0").ok();
    }

    @Override
    public boolean store(String account, String secret) {
        if (account == null || account.isBlank() || secret == null || secret.isEmpty()) {
            return false;
        }
        // ⚠ Removed first, or a second Add for the same resource and user leaves TWO entries and
        // Retrieve picks one of them — so changing an app password would work about half the time.
        // The remove is allowed to fail: on the first store there is nothing there.
        String script = VAULT
                + "try { $v.Remove($v.Retrieve('" + escape(SERVICE) + "','" + escape(account) + "')) } catch {};"
                + "$c = New-Object Windows.Security.Credentials.PasswordCredential,"
                + "Windows.Security.Credentials,ContentType=WindowsRuntime "
                + "-ArgumentList '" + escape(SERVICE) + "','" + escape(account) + "','" + escape(secret) + "';"
                + "$v.Add($c); exit 0";
        return run(script).ok();
    }

    @Override
    public Optional<String> lookup(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        // ⚠ RetrievePassword() must be called before .Password is populated — the WinRT object comes
        // back with the secret withheld until it is asked for, so reading .Password directly returns
        // empty and looks exactly like "no credential stored".
        String script = VAULT
                + "try { $c = $v.Retrieve('" + escape(SERVICE) + "','" + escape(account) + "');"
                + "$c.RetrievePassword(); [Console]::Out.Write($c.Password); exit 0 } catch { exit 1 }";
        ToolRunner.Result result = run(script);
        if (!result.ok() || result.output().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.output());
    }

    @Override
    public boolean forget(String account) {
        if (account == null || account.isBlank()) {
            return false;
        }
        String script = VAULT
                + "try { $v.Remove($v.Retrieve('" + escape(SERVICE) + "','" + escape(account) + "'));"
                + "exit 0 } catch { exit 1 }";
        return run(script).ok();
    }

    /**
     * ⚠ The script — secret and all — goes on <b>stdin</b>. Arguments carry only the flags.
     *
     * <p>{@code -NoProfile} so a user's profile script cannot change the meaning of what is run, and
     * {@code -NonInteractive} so nothing can sit waiting for input that is never coming.
     */
    private static ToolRunner.Result run(String script) {
        return ToolRunner.run(
                List.of(POWERSHELL, "-NoProfile", "-NonInteractive", "-Command", "-"), script + "\n");
    }

    /**
     * Doubles single quotes — PowerShell's escape inside a single-quoted string.
     *
     * <p>⚠ Single-quoted, not double: a double-quoted PowerShell string interpolates, so a password
     * containing {@code $(...)} would be <em>executed</em>. Inside single quotes the only character
     * with meaning is the quote itself.
     */
    static String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    @Override
    public String describe() {
        return "Windows Credential Manager";
    }
}
