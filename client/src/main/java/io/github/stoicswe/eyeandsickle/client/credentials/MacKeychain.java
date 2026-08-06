package io.github.stoicswe.eyeandsickle.client.credentials;

import java.util.List;
import java.util.Optional;

/**
 * macOS Keychain, through the {@code security} tool.
 *
 * <h2>⚠ THE SECRET IS PROMPTED FOR AND FED ON stdin — this is the tool's own instruction</h2>
 *
 * {@code security add-generic-password}'s usage text ends with: <i>"Use of the -p or -w options is
 * insecure. Specify -w as the last option to be prompted."</i> So {@code -w} is passed <b>last and
 * with no value</b>, which makes the tool read the password from standard input instead of taking it
 * from the argument list where {@code ps} would show it to every user on the machine.
 *
 * <p>⚠ <b>It prompts TWICE</b> — password, then retype — so the secret is written twice, separated by
 * newlines. Verified on macOS 26 by round-tripping a throwaway item: sending it once fails with
 * "passwords don't match" and, critically, still <b>exits zero</b>, so the retype is not optional and
 * its absence would not have been noticed from the status code.
 *
 * <h2>⚠ Exit 44 means "no such item", not "failure"</h2>
 *
 * Verified the same way. A lookup that cannot find anything must return empty rather than report a
 * broken store, or a player who has never saved a credential is told their Keychain is unavailable.
 *
 * <h2>⚠ {@code -U} is required and easy to leave off</h2>
 *
 * Without it {@code add-generic-password} refuses when the item already exists, so re-entering a
 * changed app password would silently keep the old one. With it the item is updated in place.
 *
 * <h2>⚠ {@code -A} is NOT used, deliberately</h2>
 *
 * That flag lets <em>any</em> application read the item without the user being asked — the tool's own
 * usage calls it "insecure, not recommended". The default is that the creating application is trusted
 * and anything else prompts the user, which is exactly the behaviour a credential store is for.
 */
final class MacKeychain implements SecretStore {

    /** What {@code security} exits with when the item simply is not there. Verified, not assumed. */
    private static final int NOT_FOUND = 44;

    private static final String SECURITY = "/usr/bin/security";

    @Override
    public boolean available() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                && new java.io.File(SECURITY).canExecute();
    }

    @Override
    public boolean store(String account, String secret) {
        if (account == null || account.isBlank() || secret == null || secret.isEmpty()) {
            return false;
        }
        // ⚠ `-w` LAST and with no value. Anywhere else in this list and the tool takes the next
        // argument as the password, putting it in argv.
        List<String> command = List.of(
                SECURITY, "add-generic-password", "-a", account, "-s", SERVICE, "-l", LABEL, "-U", "-w");
        // ⚠ Twice. The tool asks for a retype and a single copy fails the comparison while still
        // exiting zero — so the count is load-bearing and the status code will not tell you.
        return ToolRunner.run(command, secret + "\n" + secret + "\n").ok();
    }

    @Override
    public Optional<String> lookup(String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        ToolRunner.Result result =
                ToolRunner.run(List.of(SECURITY, "find-generic-password", "-a", account, "-s", SERVICE, "-w"), null);
        if (result.exitCode() == NOT_FOUND || !result.ok() || result.output().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(result.output());
    }

    @Override
    public boolean forget(String account) {
        if (account == null || account.isBlank()) {
            return false;
        }
        return ToolRunner.run(List.of(SECURITY, "delete-generic-password", "-a", account, "-s", SERVICE), null)
                .ok();
    }

    @Override
    public String describe() {
        return "macOS Keychain";
    }
}
