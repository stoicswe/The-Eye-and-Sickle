package io.github.stoicswe.eyeandsickle.client.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.support.TestSaves;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The commands that run on a machine.
 *
 * <h2>The two properties worth pinning</h2>
 *
 * <b>The catalogue is the menu is the parser.</b> {@link NodeCommands#catalogue()} supplies the
 * right-click menu's entries, the options it offers, and the flags {@link NodeCommands#run} reads. If
 * those ever came apart, the menu would insert flags the parser ignores — silently, because an
 * ignored flag produces output that looks fine.
 *
 * <p><b>Nothing here decides readability.</b> Every listing comes from the port and carries the
 * rules' own {@code readable} verdict (I14). The tests below check the commands render that verdict
 * rather than computing one.
 */
class NodeCommandsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC);

    private static GameSession session(Path dir) {
        return new LocalGameSession(TestSaves.bare(new SaveStore(dir.resolve("s.json")), "op", CLOCK));
    }

    private static String output(GameSession session, String line) {
        return String.join("\n", NodeCommands.run(session, "", "/", line).lines());
    }

    @Nested
    @DisplayName("the catalogue is one source")
    class Catalogue {

        @Test
        @DisplayName("every command the menu would offer is one the parser can run")
        void menuAndParserAgree(@TempDir Path dir) {
            // The failure this rules out: a menu entry that inserts a line the shell answers with
            // "command not found", which reads as the game being broken rather than as a stale menu.
            GameSession session = session(dir);
            for (NodeCommands.NodeCommand command : NodeCommands.catalogue()) {
                assertThat(NodeCommands.find(command.name()))
                        .as("%s is offerable but not runnable", command.name())
                        .isPresent();
                assertThat(String.join("\n", NodeCommands.run(session, "", "/", command.name()).lines()))
                        .as("%s reports itself as unknown", command.name())
                        .doesNotContain("command not found");
            }
        }

        @Test
        @DisplayName("the usage line is built from the options, so it cannot describe a flag that is gone")
        void usageIsDerived() {
            NodeCommands.NodeCommand ls = NodeCommands.find("ls").orElseThrow();
            assertThat(ls.usage()).isEqualTo("ls [-l] [-a] [-h] [path]");
            // A required argument reads as <angle brackets> and an optional one as [square], which is
            // the convention every real man page uses.
            assertThat(NodeCommands.find("cat").orElseThrow().usage()).isEqualTo("cat <file>");
        }

        @Test
        @DisplayName("every command has a group, so nothing falls out of the menu")
        void everyCommandIsInAGroup() {
            // The menu is built by grouping. A command with a blank group would still be in the
            // catalogue and would still run — and would be unreachable by right-click, which is the
            // route this whole feature exists to provide.
            for (NodeCommands.NodeCommand command : NodeCommands.catalogue()) {
                assertThat(command.group()).as("%s", command.name()).isNotBlank();
                assertThat(command.synopsis()).as("%s", command.name()).isNotBlank();
            }
            assertThat(NodeCommands.byGroup().values().stream().mapToInt(List::size).sum())
                    .isEqualTo(NodeCommands.catalogue().size());
        }

        @Test
        @DisplayName("every CHOICE option offers its values, because that is the point of the kind")
        void choicesAreNonEmpty() {
            for (NodeCommands.NodeCommand command : NodeCommands.catalogue()) {
                for (NodeCommands.CommandOption option : command.options()) {
                    if (option.kind() == NodeCommands.OptionKind.CHOICE) {
                        assertThat(option.choices())
                                .as("%s %s offers no values", command.name(), option.name())
                                .isNotEmpty();
                    }
                    assertThat(option.help()).as("%s %s", command.name(), option.name()).isNotBlank();
                }
            }
        }
    }

    @Nested
    @DisplayName("the rig's own filesystem")
    class OwnRig {

        @Test
        @DisplayName("⚠ the root is FOUR entries, macOS-shaped — the FHS moved inside /System")
        void macOsShapedRoot(@TempDir Path dir) {
            // Replaced a twenty-directory Linux FHS root on 2026-07-28. Those directories ARE the
            // operating system, and putting them at the root was what made a Unix filesystem look
            // forbidding. They did not disappear — they moved into /System, where `man hier`
            // describes them.
            String listing = output(session(dir), "ls");
            assertThat(listing)
                    .contains("Applications/").contains("Library/")
                    .contains("System/").contains("Users/");
            assertThat(listing).doesNotContain("etc/").doesNotContain("home/");
        }

        @Test
        @DisplayName("the FreeBSD base system is inside /System, and none of it opens")
        void systemIsFreeBsdShapedAndClosed(@TempDir Path dir) {
            GameSession session = session(dir);
            assertThat(output(session, "ls /System"))
                    .contains("bin/").contains("etc/").contains("rescue/").contains("usr/");
            // ⚠ /usr/local is the FreeBSD/Linux difference in one directory, and it is listed.
            assertThat(output(session, "ls /System/usr")).contains("local/");
        }

        @Test
        @DisplayName("⚠ your OWN /System reads — it is read-only, not unlookable")
        void ownSystemIsReadable() {
            // The regression this pins: /System entries were hard-coded unreadable, so the file
            // manager told players to "breach it first" about their own rig. The base system is not
            // yours to EDIT (every mode in it is r-xr-xr-x); it was never meant to be unopenable.
            GameSession session = session(java.nio.file.Path.of(
                    System.getProperty("java.io.tmpdir"), "es-own-system"));
            String rc = output(session, "cat /System/etc/rc.conf");
            assertThat(rc).contains("sshd_enable").contains("hostname=");
            assertThat(rc).doesNotContain("breach it first");
        }

        @Test
        @DisplayName("⚠ a DIRECTORY is never read as a file, however deep in /System it is")
        void directoriesAreNotFiles(@TempDir Path dir) {
            // The regression: /System/bin came back described as "ELF 64-bit LSB executable" because
            // isBinary asked "system path with no text contents" and a folder has none. The file
            // manager showed a folder as a stripped x86-64 binary.
            GameSession session = session(dir);
            assertThat(output(session, "cat /System/bin")).contains("Is a directory");
            assertThat(output(session, "cat /System/bin")).doesNotContain("ELF");
            // And it still LISTS, which is what you actually wanted from it.
            assertThat(output(session, "ls /System/bin")).contains("ls").contains("sh");
        }

        @Test
        @DisplayName("`stat` on a system directory explains what it is FOR")
        void statTeaches(@TempDir Path dir) {
            // The same note the file manager's Get info shows. One source, two surfaces — a `stat`
            // that said less than a right-click would send players to the mouse to learn things.
            String out = output(session(dir), "stat /System/rescue");
            assertThat(out).contains("Statically linked").contains("shared libraries are gone");
            assertThat(out).contains("read-only");
        }

        @Test
        @DisplayName("a binary says it is a binary; it does not print invented bytes")
        void binariesDoNotPretend(@TempDir Path dir) {
            // The half of the original argument that was right: a game cannot ship a real kernel,
            // and a file printing made-up contents would teach something false. `file`'s answer,
            // not `cat`'s screenful of noise.
            assertThat(output(session(dir), "cat /System/boot/kernel/kernel"))
                    .contains("ELF").contains("nothing here a person reads");
        }

        @Test
        @DisplayName("⚠ master.passwd stays closed on your OWN machine — for the real reason")
        void modeRestrictedStaysClosed(@TempDir Path dir) {
            // 0600, owner root, on every FreeBSD box alive. Meeting that by being refused is how a
            // player learns why /etc/passwd sits beside it world-readable with asterisks.
            String out = output(session(dir), "cat /System/etc/master.passwd");
            assertThat(out).contains("Permission denied").contains("0600");
            // And the file that IS readable explains the split.
            assertThat(output(session(dir), "cat /System/etc/passwd"))
                    .contains("*").contains("master.passwd");
        }

        @Test
        @DisplayName("the three tiers live in ~/.VaultStore, and df states their exposure")
        void tiersAreInTheVaultStore(@TempDir Path dir) {
            // ⚠ They stopped being /mnt mount points on 2026-07-28. Nobody mounted them, and a
            // `/mnt/vault` sitting in the sidebar of a machine an intruder is standing on is a
            // signpost to the one place that is meant to be safe. What did NOT change is that §6's
            // exposure ladder is what the tiers mean — `df` is where that is stated, and the
            // exposure column is the one a real df has no equivalent to.
            String df = output(session(dir), "df");
            assertThat(df).contains(".VaultStore/vault").contains("never exposed");
            assertThat(df).contains(".VaultStore/hot").contains("always exposed");
        }

        @Test
        @DisplayName("⚠ the vault is NOT in /mnt, where anyone browsing would find it first")
        void vaultIsNotAMount(@TempDir Path dir) {
            GameSession session = session(dir);
            assertThat(output(session, "ls /mnt")).doesNotContain("vault");
            // And it is hidden, so it does not lead an idle browse straight to itself either.
            String home = "/Users/" + session.handle();
            assertThat(String.join("\n", NodeCommands.run(session, "", home, "ls").lines()))
                    .doesNotContain("VaultStore");
            assertThat(String.join("\n", NodeCommands.run(session, "", home, "ls -a").lines()))
                    .contains(".VaultStore");
        }

        @Test
        @DisplayName("an application is a DIRECTORY, which is the real thing about a macOS bundle")
        void applicationsAreBundles(@TempDir Path dir) {
            GameSession session = session(dir);
            String apps = "/Applications";
            assertThat(String.join("\n", NodeCommands.run(session, "", apps, "ls").lines()))
                    .contains("Network.app/")
                    .contains("Breach.app/");
            // ⚠ /Applications at the ROOT, as on macOS — not in the home.
            // The real bundle layout, all the way down to the executable.
            assertThat(String.join("\n",
                            NodeCommands.run(session, "", apps + "/Network.app/Contents", "ls").lines()))
                    .contains("Info.plist").contains("Upgrades/")
                    // ⚠ uOS, not MacOS. A real bundle names that directory after the operating
                    // system, and these machines do not run macOS — a folder claiming otherwise
                    // would be the one dishonest thing in an otherwise real layout.
                    .contains("uOS/")
                    .doesNotContain("MacOS");
        }

        @Test
        @DisplayName("Recents is a real directory, so the shell reaches it too")
        void recentsIsAPlace(@TempDir Path dir) {
            // ⚠ It is GNOME's own location. Making it a place rather than a sidebar widget is what
            // lets `ls ~/.local/share/recently-used` work — and what means an intruder standing in
            // it can read what the owner has been doing.
            GameSession session = session(dir);
            String home = "/Users/" + session.handle();
            assertThat(String.join("\n",
                            NodeCommands.run(session, "", home + "/.local/share", "ls").lines()))
                    .contains("recently-used/");

            session.noteAccess("", home + "/Documents");
            assertThat(String.join("\n", NodeCommands.run(
                            session, "", home + "/.local/share/recently-used", "ls").lines()))
                    .contains("Documents");
        }

        @Test
        @DisplayName("⚠ Recents never lists itself, or it would never leave the top")
        void recentsDoesNotRecordItself(@TempDir Path dir) {
            GameSession session = session(dir);
            String home = "/Users/" + session.handle();
            session.noteAccess("", home + "/.local/share/recently-used");
            assertThat(String.join("\n", NodeCommands.run(
                            session, "", home + "/.local/share/recently-used", "ls").lines()))
                    .doesNotContain("recently-used");
        }

        @Test
        @DisplayName("looking at the same thing twice moves it rather than listing it twice")
        void recentsDeduplicates(@TempDir Path dir) {
            GameSession session = session(dir);
            String home = "/Users/" + session.handle();
            session.noteAccess("", home + "/Documents");
            session.noteAccess("", home + "/Music");
            session.noteAccess("", home + "/Documents");

            String listing = String.join("\n", NodeCommands.run(
                    session, "", home + "/.local/share/recently-used", "ls").lines());
            assertThat(listing.split("Documents", -1)).hasSize(2);
        }

        @Test
        @DisplayName("the remote-access log exists and is readable before anything has happened")
        void accessLogAlwaysExists(@TempDir Path dir) {
            // A log that materialised only once something had happened would be a log nobody had
            // learned to check, and the habit is the point.
            GameSession session = session(dir);
            // ⚠ /Library/Logs, not /System/var/log: the base system does not open, and a record of
            // an intrusion that nobody can read is not a record.
            assertThat(output(session, "ls /Library/Logs")).contains("remote-access.log");
            assertThat(output(session, "cat /Library/Logs/remote-access.log"))
                    .contains("No one has been on this machine but you");
        }

        @Test
        @DisplayName("`ls -l` shows mode, owner and date; plain `ls` does not")
        void longForm(@TempDir Path dir) {
            GameSession session = session(dir);
            assertThat(output(session, "ls -l")).contains("drwxr-xr-x").contains("total ");
            assertThat(output(session, "ls")).doesNotContain("drwxr-xr-x");
        }

        @Test
        @DisplayName("a dotfile is hidden until -a, exactly as a real ls hides it")
        void dotfiles(@TempDir Path dir) {
            GameSession session = session(dir);
            String home = "/Users/" + session.handle();
            assertThat(String.join("\n", NodeCommands.run(session, "", home, "ls").lines()))
                    .doesNotContain(".bash_history");
            assertThat(String.join("\n", NodeCommands.run(session, "", home, "ls -a").lines()))
                    .contains(".bash_history");
        }
    }

    @Nested
    @DisplayName("refusals read like the real ones")
    class Refusals {

        @Test
        @DisplayName("an unknown verb is `command not found`, and it names the way out")
        void unknownVerb(@TempDir Path dir) {
            assertThat(output(session(dir), "rm -rf /"))
                    .contains("command not found")
                    .contains("help");
        }

        @Test
        @DisplayName("a missing file is ENOENT's own wording")
        void missingFile(@TempDir Path dir) {
            // The message a player will meet again on any Unix. A refusal that reads like the real
            // one teaches the real one, which is the cheapest teaching in this client.
            assertThat(output(session(dir), "cat /Users/nothing"))
                    .contains("No such file or directory");
        }

        @Test
        @DisplayName("cat on a directory says so rather than printing nothing")
        void catOnADirectory(@TempDir Path dir) {
            assertThat(output(session(dir), "cat /System/etc")).contains("Is a directory");
        }

        @Test
        @DisplayName("⚠ a quoted argument survives, because the shell's own parser is used")
        void quotingWorks(@TempDir Path dir) {
            // A private split(" ") here would make `cat "my file"` behave differently in this window
            // than in the deck terminal — the kind of difference nobody reports and everybody stops
            // trusting. The assertion is that the whole quoted name reaches the lookup as one token.
            assertThat(output(session(dir), "cat \"/Users/no such file\""))
                    .contains("/Users/no such file")
                    .doesNotContain("such: ");
        }
    }

    @Nested
    @DisplayName("`exit` is the one command that ends the session")
    class Exit {

        @Test
        @DisplayName("it reports closeSession, and nothing else does")
        void onlyExitCloses(@TempDir Path dir) {
            GameSession session = session(dir);
            assertThat(NodeCommands.run(session, "", "/", "exit").closeSession()).isTrue();
            for (String other : List.of("ls", "pwd", "help", "df", "whoami")) {
                assertThat(NodeCommands.run(session, "", "/", other).closeSession())
                        .as("%s must not end the session", other)
                        .isFalse();
            }
        }
    }
}
