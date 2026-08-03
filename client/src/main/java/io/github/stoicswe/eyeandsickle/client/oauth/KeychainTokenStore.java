package io.github.stoicswe.eyeandsickle.client.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Stores credentials in the operating system's own credential store.
 *
 * <h2>⚠ This is the first time this client has ever spawned a process</h2>
 *
 * {@code CLAUDE.md} records that the client has never started a subprocess — {@code SystemReport}
 * gives up two readouts' specificity rather than run {@code sysctl}. That is a real property and this
 * breaks it, deliberately, because the alternative is keeping a refresh token in a file next to the
 * key that decrypts it. The cost is bounded to this class, and the rules below are what keep it
 * bounded.
 *
 * <ul>
 *   <li>⚠ <strong>Never a shell.</strong> {@link ProcessBuilder} with an argument list, so nothing is
 *       parsed by {@code sh} and a handle or DID containing a quote cannot become a command.
 *   <li>⚠ <strong>The secret goes in on STDIN, never as an argument.</strong> Process arguments are
 *       world-readable on Linux (each process's {@code cmdline} under {@code /proc}) and visible to
 *       {@code ps} everywhere — a
 *       refresh token on a command line is a refresh token every other process on the machine can
 *       read, which would be worse than the file fallback this is meant to improve on.
 *   <li>⚠ <strong>Bounded wait, and the process is destroyed on timeout.</strong> A locked keychain
 *       that prompts and is never answered would otherwise hang the client on launch.
 *   <li>⚠ <strong>The account name is fixed</strong>, never derived from player input.
 * </ul>
 *
 * <h2>The three platforms</h2>
 *
 * <ul>
 *   <li><strong>macOS</strong> — {@code security add-generic-password} / {@code find-generic-password}.
 *   <li><strong>Windows</strong> — PowerShell + DPAPI ({@code ProtectedData}, {@code CurrentUser}
 *       scope). Windows has no first-party CLI for the credential vault that stores arbitrary blobs
 *       readably, so DPAPI-encrypt-to-file is the platform mechanism: the key is held by the OS and
 *       derived from the user's login, so the ciphertext on disk is useless on another machine or to
 *       another user. That is the property the file fallback lacks.
 *   <li><strong>Linux</strong> — {@code secret-tool} (libsecret), present wherever GNOME Keyring or
 *       KWallet is.
 * </ul>
 *
 * <p>⚠ {@link #isAvailable()} <strong>probes by doing</strong> — it writes and reads back a canary.
 * Checking that a binary exists says nothing about whether a keychain is unlocked, whether
 * {@code secret-tool} has a D-Bus session to talk to, or whether the user will deny the prompt; and a
 * store that reports itself available and then silently loses credentials is worse than one that
 * declines up front.
 */
final class KeychainTokenStore implements TokenStore {

    /** Fixed, never derived from player input. */
    private static final String SERVICE = "EAS-uOS-Client";

    private static final String ACCOUNT = "atproto-session";

    private static final long TIMEOUT_SECONDS = 15;

    private final Platform platform;

    private enum Platform {
        MACOS,
        WINDOWS,
        LINUX
    }

    private KeychainTokenStore(Platform platform) {
        this.platform = platform;
    }

    /**
     * @return a working keychain store, or null if this platform has none that answers
     */
    static KeychainTokenStore available() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Platform platform;
        if (os.contains("mac")) {
            platform = Platform.MACOS;
        } else if (os.contains("win")) {
            platform = Platform.WINDOWS;
        } else if (os.contains("linux")) {
            platform = Platform.LINUX;
        } else {
            return null;
        }
        KeychainTokenStore store = new KeychainTokenStore(platform);
        return store.isAvailable() ? store : null;
    }

    /** Writes and reads back a canary, because "the binary exists" is not the question. */
    private boolean isAvailable() {
        try {
            String canary = "probe-" + Long.toHexString(System.nanoTime());
            writeSecret(canary);
            String read = readSecret();
            return canary.equals(read);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public boolean isPlatformSecured() {
        return true;
    }

    @Override
    public String describe() {
        return switch (platform) {
            case MACOS -> "macOS Keychain";
            case WINDOWS -> "Windows DPAPI (your account)";
            case LINUX -> "Secret Service (libsecret)";
        };
    }

    @Override
    public Credentials load() {
        String blob = readSecret();
        return blob == null || blob.isBlank() ? null : CredentialCodec.decode(blob);
    }

    @Override
    public void save(Credentials credentials) {
        writeSecret(CredentialCodec.encode(credentials));
    }

    @Override
    public void clear() {
        try {
            switch (platform) {
                case MACOS -> run(List.of("security", "delete-generic-password", "-s", SERVICE, "-a", ACCOUNT), null);
                case LINUX -> run(List.of("secret-tool", "clear", "service", SERVICE, "account", ACCOUNT), null);
                case WINDOWS -> writeSecret("");
            }
        } catch (RuntimeException alreadyGone) {
            // Deleting something that is not there is the desired end state, not a failure.
        }
    }

    // ── The platform calls ─────────────────────────────────────────────────────────────────────

    private void writeSecret(String value) {
        switch (platform) {
            case MACOS ->
                // -U updates in place. -w with NO value makes `security` read the secret from stdin
                // rather than take it as an argument, which is the whole reason this form is used.
                //
                // ⚠ IT PROMPTS TWICE — "password data for new item:" then "retype password for new
                // item:" — so the value must be written TWICE or the two do not match. Measured
                // 2026-08-02: a single write prints "passwords don't match", stores nothing, and
                // still EXITS 0. It therefore fails as a success, and the only thing that catches it
                // is reading the value back — which is exactly what the canary in isAvailable() does,
                // and why that probe round-trips instead of checking an exit code.
                run(
                        List.of("security", "add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT, "-w"),
                        value + "\n" + value + "\n");
            case LINUX ->
                run(
                        List.of("secret-tool", "store", "--label=" + SERVICE, "service", SERVICE, "account", ACCOUNT),
                        value);
            case WINDOWS -> run(powershell(WINDOWS_WRITE), value);
        }
    }

    /**
     * Reads the stored blob, treating "no such item" as absence.
     *
     * <p>⚠ A missing item is reported as a <strong>non-zero exit</strong>, not as empty output —
     * {@code security} exits 44 and {@code secret-tool} exits 1. Without this, the first launch after
     * a sign-out throws instead of returning null, and the player sees a storage error where they
     * should see a sign-in button. Found by probing the real keychain, not in review.
     *
     * <p>A genuinely broken store is also swallowed to null here, deliberately: for a <em>read</em>,
     * "no credentials" and "cannot get at the credentials" have the same remedy — sign in again — and
     * the next {@code save} will fail loudly if the store really is broken.
     */
    private String readSecret() {
        try {
            return readSecretOrThrow();
        } catch (IllegalStateException absent) {
            return null;
        }
    }

    private String readSecretOrThrow() {
        return switch (platform) {
            case MACOS ->
                trimOrNull(run(List.of("security", "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"), null));
            case LINUX ->
                trimOrNull(run(List.of("secret-tool", "lookup", "service", SERVICE, "account", ACCOUNT), null));
            case WINDOWS -> trimOrNull(run(powershell(WINDOWS_READ), null));
        };
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> powershell(String script) {
        return List.of(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)));
    }

    /**
     * DPAPI-encrypts stdin to a file under the user's profile.
     *
     * <p>⚠ {@code CurrentUser} scope: the ciphertext is decryptable only by this Windows account on
     * this machine, so a copied file or a synced backup is useless. That is exactly the property the
     * plain-file fallback cannot provide.
     */
    private static final String WINDOWS_WRITE = """
            $ErrorActionPreference='Stop'
            Add-Type -AssemblyName System.Security
            $dir = Join-Path $env:APPDATA 'The Eye and Sickle'
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            $in = [Console]::In.ReadToEnd()
            $bytes = [Text.Encoding]::UTF8.GetBytes($in)
            $enc = [Security.Cryptography.ProtectedData]::Protect($bytes, $null, 'CurrentUser')
            [IO.File]::WriteAllBytes((Join-Path $dir 'session.dpapi'), $enc)
            """;

    private static final String WINDOWS_READ = """
            $ErrorActionPreference='Stop'
            Add-Type -AssemblyName System.Security
            $p = Join-Path (Join-Path $env:APPDATA 'The Eye and Sickle') 'session.dpapi'
            if (Test-Path $p) {
              $enc = [IO.File]::ReadAllBytes($p)
              $b = [Security.Cryptography.ProtectedData]::Unprotect($enc, $null, 'CurrentUser')
              [Console]::Out.Write([Text.Encoding]::UTF8.GetString($b))
            }
            """;

    /**
     * Runs a command, feeding {@code stdin} if given.
     *
     * @param command the argument list — never a shell string
     * @param stdin the secret, or null
     * @return the process's standard output
     */
    private static String run(List<String> command, String stdin) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // Errors are read explicitly rather than inherited: inheritIO would print a keychain
            // error to a console a packaged client does not have.
            builder.redirectErrorStream(false);
            process = builder.start();

            try (OutputStream out = process.getOutputStream()) {
                if (stdin != null) {
                    out.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            String stdout = drain(process.getInputStream());
            drain(process.getErrorStream());

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new OauthException(
                        OauthException.Kind.STORAGE, "the system credential store did not answer in time");
            }
            if (process.exitValue() != 0) {
                // Not an OauthException: a non-zero exit is also how "no such item" is reported, and
                // the callers above treat that as absence rather than as a fault.
                throw new IllegalStateException(command.get(0) + " exited " + process.exitValue());
            }
            return stdout;
        } catch (IOException | IllegalStateException failed) {
            throw new IllegalStateException("could not use the system credential store", failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new OauthException(
                    OauthException.Kind.STORAGE, "interrupted using the system credential store", interrupted);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String drain(InputStream stream) throws IOException {
        try (stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            stream.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
