package io.github.stoicswe.eyeandsickle.client.credentials;

import java.util.List;
import java.util.Optional;

/**
 * Linux and the BSDs, through {@code secret-tool} — the freedesktop Secret Service API.
 *
 * <p>That is the interface GNOME Keyring and KWallet both implement, so this covers the desktops
 * where a credential store exists at all. It is <b>not</b> universally installed: {@code secret-tool}
 * ships in {@code libsecret-tools} on Debian and Ubuntu and {@code libsecret} on Fedora and Arch, and
 * a headless or minimal system may have neither it nor an agent to talk to. That is a supported
 * outcome — {@link #available()} answers false and the feature that wanted a credential is off.
 *
 * <h2>⚠ The secret is fed on stdin, and here that is the tool's normal interface</h2>
 *
 * {@code secret-tool store} reads the secret from standard input by design — there is no argument
 * that takes one, which is the freedesktop tool making the same decision macOS's usage text
 * recommends. <b>Once</b>, not twice: it does not ask for a retype, which is the one behavioural
 * difference from {@link MacKeychain} and the sort of thing that is silently wrong if copied across.
 *
 * <h2>⚠ NOT VERIFIED ON A REAL SECRET SERVICE</h2>
 *
 * The macOS path was round-tripped against a live Keychain; this one was not — no Linux desktop was
 * available. The command shapes come from {@code secret-tool}'s documented interface and the failure
 * mode is fail-closed by construction (a missing tool is an {@code IOException}, which
 * {@code ToolRunner} turns into "unavailable"), so the risk is that it reports unavailable on a
 * machine where it would have worked — never that it stores a credential somewhere it should not.
 * <b>Round-trip it on a real desktop before relying on it.</b>
 */
final class SecretServiceStore implements SecretStore {

    private static final String SECRET_TOOL = "secret-tool";

    /** The attribute pair every item is filed under, so lookup and clear find exactly one thing. */
    private static List<String> attributes(String account) {
        return List.of("service", SERVICE, "account", account);
    }

    @Override
    public boolean available() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac") || os.contains("win")) {
            return false;
        }
        // ⚠ Asks the tool, rather than looking for a file on PATH. `--version` is cheap, and it
        // fails the same way a missing binary does — ToolRunner turns the IOException into a
        // non-zero result — so there is one answer rather than a PATH walk that can disagree with
        // what exec() would actually resolve.
        return ToolRunner.run(List.of(SECRET_TOOL, "--version"), null).ok();
    }

    @Override
    public boolean store(String account, String secret) {
        if (account == null || account.isBlank() || secret == null || secret.isEmpty()) {
            return false;
        }
        List<String> command = new java.util.ArrayList<>(List.of(SECRET_TOOL, "store", "--label=" + LABEL));
        command.addAll(attributes(account));
        // ⚠ ONCE. secret-tool does not ask for a retype, unlike macOS's `security` — sending it
        // twice would store the secret with a newline and a copy of itself appended.
        return ToolRunner.run(command, secret + "\n").ok();
    }

    @Override
    public Optional<String> lookup(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        List<String> command = new java.util.ArrayList<>(List.of(SECRET_TOOL, "lookup"));
        command.addAll(attributes(account));
        ToolRunner.Result result = ToolRunner.run(command, null);
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
        List<String> command = new java.util.ArrayList<>(List.of(SECRET_TOOL, "clear"));
        command.addAll(attributes(account));
        return ToolRunner.run(command, null).ok();
    }

    @Override
    public String describe() {
        return "Secret Service (GNOME Keyring / KWallet)";
    }
}
