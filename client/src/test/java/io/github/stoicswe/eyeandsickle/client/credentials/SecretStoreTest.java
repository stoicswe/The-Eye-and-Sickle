package io.github.stoicswe.eyeandsickle.client.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The OS credential stores.
 *
 * <h2>⚠ ONE PROPERTY MATTERS MORE THAN ANYTHING ELSE HERE</h2>
 *
 * <b>A secret must never appear in a process's argument list.</b> Arguments are world-readable —
 * {@code ps} shows them to every user on the machine — and macOS's own {@code security} tool says so
 * in its usage text. The failure is invisible: the credential stores correctly, the feature works,
 * and the password is on the process table the whole time. {@link NeverInArgv} is that property, and
 * it is worth more than every other test in this file.
 *
 * <h2>Why most of this is source inspection rather than round trips</h2>
 *
 * A real round trip writes to the developer's own keychain, prompts, and only exercises one of three
 * platforms. {@link Roundtrip} does it anyway on macOS, behind an assumption and with a throwaway
 * item it deletes — because the alternative is trusting a command shape nobody ever ran.
 */
class SecretStoreTest {

    private static final String SECRET = "hunter2-not-a-real-app-password";

    @Nested
    @DisplayName("⚠ a secret never reaches an argument list")
    class NeverInArgv {

        /**
         * Reads the three stores' source and refuses any command list that could carry the secret.
         *
         * <p>⚠ Source inspection, and not out of laziness: nothing at run time can ask a process
         * "which of your arguments came from a password", and the only way a secret reaches argv is
         * that somebody wrote it into the list. That is a property of the text, so the text is what
         * is checked — the same reasoning {@code CommandSpecTest} records for reading source to find
         * flags a lambda inspects.
         */
        @Test
        @DisplayName("no store passes its secret parameter to ToolRunner's command list")
        void secretIsNeverAnArgument() throws IOException {
            for (String store : List.of("MacKeychain", "SecretServiceStore", "WindowsCredentialVault")) {
                String source = read(store);
                // Every ToolRunner.run(...) call takes the command as its FIRST argument and the
                // stdin payload as its second. The command is built from List.of(...) literals and
                // the account; `secret` may appear only in the second position.
                for (String line : source.split("\n")) {
                    String code = line.strip();
                    if (!code.contains("List.of(") || !code.contains("\"-")) {
                        continue;
                    }
                    assertThat(code)
                            .as("%s builds a command list that mentions the secret — it would be "
                                    + "visible in `ps` to every user on the machine", store)
                            .doesNotContain("secret");
                }
            }
        }

        /**
         * ⚠ The other half: the runner's shape must make the mistake hard rather than merely absent.
         *
         * <p>{@code ToolRunner.run} takes the command and the stdin payload as two parameters and
         * offers no single-string overload — there is nowhere to interpolate a password into a
         * command line. A convenience overload added later is what would undo this whole package.
         */
        @Test
        @DisplayName("ToolRunner offers no overload that could take a composed command string")
        void theRunnerHasNoStringOverload() {
            List<String> signatures = java.util.Arrays.stream(ToolRunner.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("run"))
                    .map(m -> java.util.Arrays.toString(m.getParameterTypes()))
                    .toList();

            assertThat(signatures)
                    .as("exactly one run(), taking the command and the stdin payload separately")
                    .hasSize(1);
            assertThat(signatures.getFirst()).contains("List").contains("String");
        }

        /** ⚠ And nothing may log the OUTPUT, because for a lookup the output is the secret. */
        @Test
        @DisplayName("the runner never logs stdout")
        void outputIsNeverLogged() throws IOException {
            String source = read("ToolRunner");
            for (String line : source.split("\n")) {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//")) {
                    continue;
                }
                if (code.contains("LOG.")) {
                    assertThat(code)
                            .as("a log line that carries the tool's output is a log line that "
                                    + "carries the credential — and this client offers its log to "
                                    + "the player to send in")
                            .doesNotContain("output")
                            .doesNotContain("stdin");
                }
            }
        }

        private static String read(String simpleName) throws IOException {
            Path path = Path.of(
                    "src/main/java/io/github/stoicswe/eyeandsickle/client/credentials/" + simpleName + ".java");
            Assumptions.assumeTrue(Files.exists(path), "source not on this classpath layout");
            return Files.readString(path);
        }
    }

    @Nested
    @DisplayName("failing closed")
    class FailClosed {

        /**
         * ⚠ The whole point of the design. A machine with no agent gets no storage, not a file.
         *
         * <p>A credential in the profile directory is a credential in every backup, screen share and
         * bug report, and the player would have no way to know it had happened.
         */
        @Test
        @DisplayName("the null store reports failure rather than pretending it kept anything")
        void noneRefuses() {
            SecretStore none = SecretStores.none();
            assertThat(none.available()).isFalse();
            assertThat(none.store("someone.bsky.social", SECRET))
                    .as("a silent success would tell the player their credential was saved")
                    .isFalse();
            assertThat(none.lookup("someone.bsky.social")).isEmpty();
            assertThat(none.forget("someone.bsky.social")).isFalse();
        }

        @Test
        @DisplayName("a blank account or an empty secret is refused everywhere")
        void blanksAreRefused() {
            for (SecretStore store :
                    List.of(new MacKeychain(), new SecretServiceStore(), new WindowsCredentialVault())) {
                assertThat(store.store("", SECRET)).as("%s", store.describe()).isFalse();
                assertThat(store.store(null, SECRET)).as("%s", store.describe()).isFalse();
                assertThat(store.store("acct", "")).as("%s", store.describe()).isFalse();
                assertThat(store.store("acct", null)).as("%s", store.describe()).isFalse();
                assertThat(store.lookup("")).as("%s", store.describe()).isEmpty();
                assertThat(store.forget("")).as("%s", store.describe()).isFalse();
            }
        }

        /** ⚠ Exactly one store may claim this machine, or two of them would fight over an item. */
        @Test
        @DisplayName("at most one store claims any given platform")
        void oneStorePerPlatform() {
            long claiming = List.of(new MacKeychain(), new WindowsCredentialVault()).stream()
                    .filter(SecretStore::available)
                    .count();
            assertThat(claiming)
                    .as("the mac and windows stores must never both answer for one machine")
                    .isLessThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PowerShell quoting")
    class Quoting {

        /**
         * ⚠ THE INJECTION SURFACE ON WINDOWS. The script — secret included — is delivered on stdin,
         * so a secret containing a quote would end the string literal and the rest would be code.
         */
        @Test
        @DisplayName("a single quote is doubled, which is PowerShell's own escape")
        void quotesAreDoubled() {
            assertThat(WindowsCredentialVault.escape("it's")).isEqualTo("it''s");
            assertThat(WindowsCredentialVault.escape("';exit 1;'")).isEqualTo("'';exit 1;''");
            assertThat(WindowsCredentialVault.escape("plain")).isEqualTo("plain");
            assertThat(WindowsCredentialVault.escape(null)).isEmpty();
        }

        /**
         * ⚠ Single quotes, not double — a double-quoted PowerShell string INTERPOLATES, so a
         * password containing {@code $(...)} would be executed rather than stored.
         */
        @Test
        @DisplayName("every interpolated value in the scripts sits inside single quotes")
        void scriptsUseSingleQuotes() throws IOException {
            Path path = Path.of(
                    "src/main/java/io/github/stoicswe/eyeandsickle/client/credentials/WindowsCredentialVault.java");
            Assumptions.assumeTrue(Files.exists(path), "source not on this classpath layout");
            String source = Files.readString(path);
            int checked = 0;
            for (String line : source.split("\n")) {
                String code = line.strip();
                // ⚠ Only lines that INTERPOLATE into a script string. The method's own declaration
                // and the javadoc both say "escape(" and neither is a call site — a check that
                // fired on those would have to be loosened until it stopped meaning anything.
                if (!code.contains("+ escape(") || code.startsWith("*") || code.startsWith("//")) {
                    continue;
                }
                checked++;
                assertThat(code)
                        .as("an escaped value must be wrapped in SINGLE quotes: %s", code)
                        .contains("'\" + escape(");
            }
            assertThat(checked)
                    .as("the scan found no interpolation at all, so it was asserting nothing")
                    .isGreaterThan(3);
        }
    }

    @Nested
    @DisplayName("a real round trip")
    class Roundtrip {

        /**
         * ⚠ Runs against the DEVELOPER'S OWN keychain, and cleans up after itself.
         *
         * <p>Skipped everywhere but macOS. It exists because the alternative is trusting a command
         * shape nobody ever executed — and this one had two surprises that only running it found:
         * {@code security} prompts for the password <b>twice</b>, and sending it once fails the
         * comparison while <b>still exiting zero</b>, so the status code would not have told us.
         *
         * <p>The item is deleted in a {@code finally}, and its account name says what it is so a
         * failed cleanup is recognisable in Keychain Access rather than mysterious.
         */
        @Test
        @DisplayName("macOS: store, read back, forget")
        void macKeychainRoundTrip() {
            MacKeychain keychain = new MacKeychain();
            Assumptions.assumeTrue(keychain.available(), "not macOS, or /usr/bin/security is absent");

            String account = "eyeandsickle-test-delete-me";
            try {
                assertThat(keychain.store(account, SECRET)).as("store").isTrue();
                assertThat(keychain.lookup(account))
                        .as("what went in comes back out, byte for byte")
                        .contains(SECRET);

                // ⚠ Overwrite, which is what changing an app password does. Without `-U` the tool
                // refuses an existing item and the OLD secret would silently survive.
                String changed = SECRET + "-changed";
                assertThat(keychain.store(account, changed)).as("overwrite").isTrue();
                assertThat(keychain.lookup(account)).contains(changed);
            } finally {
                keychain.forget(account);
            }
            assertThat(keychain.lookup(account))
                    .as("and forget really removes it — exit 44, which is 'no such item'")
                    .isEmpty();
        }

        @Test
        @DisplayName("a lookup for something never stored is empty, not an error")
        void missingIsEmptyNotBroken(@TempDir Path unused) {
            MacKeychain keychain = new MacKeychain();
            Assumptions.assumeTrue(keychain.available(), "not macOS");
            Optional<String> found = keychain.lookup("eyeandsickle-account-that-does-not-exist");
            assertThat(found)
                    .as("a player who has never saved a credential must not be told the store is broken")
                    .isEmpty();
        }
    }
}
