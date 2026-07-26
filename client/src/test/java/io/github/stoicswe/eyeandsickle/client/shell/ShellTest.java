package io.github.stoicswe.eyeandsickle.client.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.NodeState;
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
 * Tests for the shell.
 *
 * <p>Most of these are refusals, because for this surface the interesting question is not "does it
 * run a command" but "does it refuse what it promised to refuse". {@code docs/client/04} §3.1 states
 * the safety boundary as seven checkable rules, and a boundary with no test is a comment.
 */
class ShellTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private static Shell shell(Path dir) {
        GameSession session = new LocalGameSession(SoloGame.open(new SaveStore(dir.resolve("s.json")), "op", CLOCK));
        return new Shell(session, BuiltinCommands.registry());
    }

    @Nested
    @DisplayName("the safety boundary")
    class Boundary {

        @Test
        @DisplayName("an unknown verb is exit 127, never a fallthrough")
        void unknownVerb(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("rm -rf /");
            assertThat(result.status()).isEqualTo(ExitStatus.NO_SUCH_COMMAND);
            assertThat(result.lines().getFirst()).contains("no such command");
        }

        @Test
        @DisplayName("redirection is refused with a message that teaches, not a generic syntax error")
        void redirectionRefused(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("ps > /tmp/out");
            assertThat(result.status()).isEqualTo(ExitStatus.USAGE);
            // A generic error invites the player to keep guessing at a capability that does not
            // exist; a specific one teaches the boundary (§3.11).
            assertThat(result.lines().getFirst()).contains("Redirection is not available");
            assertThat(result.lines().getFirst()).contains("shell(7)");
        }

        @Test
        @DisplayName("chaining, substitution and background are each refused by name")
        void otherSyntaxRefused(@TempDir Path dir) {
            Shell s = shell(dir);
            assertThat(s.run("ps && ls").lines().getFirst()).contains("chaining");
            assertThat(s.run("ps ; ls").lines().getFirst()).contains("chaining");
            assertThat(s.run("ls $(ps)").lines().getFirst()).contains("substitution");
            assertThat(s.run("ps &").lines().getFirst()).contains("Background");
        }

        @Test
        @DisplayName("an action inside a pipeline is refused before any of it runs")
        void noActionsInPipelines(@TempDir Path dir) {
            Shell s = shell(dir);
            Shell.Result result = s.run("ps | mine --allocate=50");

            assertThat(result.status()).isEqualTo(ExitStatus.USAGE);
            assertThat(result.lines().getFirst()).contains("may only");
            // The refusal must happen before execution: nothing was allocated.
            assertThat(s.session().computeBudget().available().cycles()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @Test
        @DisplayName("short flags cluster, as POSIX guideline 5 says they should")
        void clustering() {
            CommandLine line = CommandLine.parse("scan -vn");
            assertThat(line.first().hasFlag("v")).isTrue();
            assertThat(line.first().hasFlag("n")).isTrue();
        }

        @Test
        @DisplayName("long flags take a value either way round")
        void longFlagValues() {
            assertThat(CommandLine.parse("mine --allocate=40").first().flag("allocate")).contains("40");
            assertThat(CommandLine.parse("mine --allocate 40").first().flag("allocate")).contains("40");
        }

        @Test
        @DisplayName("-- ends options, so an argument may begin with a dash")
        void endOfOptions() {
            // Needed for a real reason here: a node address or handle may start with '-'.
            CommandLine.Stage stage = CommandLine.parse("verify -- -weird-name").first();
            assertThat(stage.arguments()).containsExactly("-weird-name");
            assertThat(stage.flags()).isEmpty();
        }

        @Test
        @DisplayName("quotes hold a value together, and both kinds are literal")
        void quoting() {
            CommandLine.Stage stage = CommandLine.parse("mv \"Old Ledger Dump\" vault").first();
            assertThat(stage.arguments()).containsExactly("Old Ledger Dump", "vault");

            // Deliberate divergence from a real shell, stated on quoting(7)'s CAVEATS: there is
            // nothing to interpolate because §3.1 rule 4 forbids expansion entirely.
            assertThat(CommandLine.parse("verify \"$HOME\"").first().arguments()).containsExactly("$HOME");
        }

        @Test
        @DisplayName("commands are case-insensitive, which a real shell is not")
        void caseInsensitive(@TempDir Path dir) {
            assertThat(shell(dir).run("PS").status()).isEqualTo(ExitStatus.OK);
        }
    }

    @Nested
    @DisplayName("pipelines")
    class Pipelines {

        @Test
        @DisplayName("a pipeline filters the previous stage's output")
        void filtering(@TempDir Path dir) {
            Shell s = shell(dir);
            s.session().allocateSelfMining(40);

            Shell.Result all = s.run("ps");
            Shell.Result filtered = s.run("ps | grep self");

            assertThat(filtered.lines()).hasSizeLessThan(all.lines().size());
            assertThat(filtered.lines()).allMatch(l -> l.toLowerCase().contains("self"));
        }

        @Test
        @DisplayName("grep -v inverts, which is the flag that turns a search into a filter")
        void invert(@TempDir Path dir) {
            Shell s = shell(dir);
            s.session().allocateSelfMining(40);
            assertThat(s.run("ps | grep -v self").lines()).noneMatch(l -> l.toLowerCase().contains("self_mining"));
        }

        @Test
        @DisplayName("wc counts, and head limits")
        void countingAndLimiting(@TempDir Path dir) {
            Shell s = shell(dir);
            assertThat(s.run("df | wc").lines()).containsExactly("4");
            assertThat(s.run("df | head -n 2").lines()).hasSize(2);
        }

        @Test
        @DisplayName("the pipeline's exit status is the last command's, as without pipefail")
        void statusIsLastCommand(@TempDir Path dir) {
            Shell s = shell(dir);
            // `grep` finding nothing is still a successful grep in this shell's terms; the point of
            // the test is that the pipeline reports the RIGHT-hand side, not the left.
            assertThat(s.run("ps | grep zzz-nothing-matches").status()).isEqualTo(ExitStatus.OK);
        }
    }

    @Nested
    @DisplayName("the namespace")
    class NamespaceRules {

        @Test
        @DisplayName("/net/ lists only nodes the player has actually discovered")
        void onlyKnownNodes(@TempDir Path dir) {
            Shell s = shell(dir);
            assertThat(Namespace.list(s.session(), "/net")).isEmpty();

            LocalGameSession local = (LocalGameSession) s.session();
            NodeState node = new NodeState();
            node.address = "10.0.0.7";
            local.game().state().knownNodes.add(node);

            assertThat(Namespace.list(s.session(), "/net")).containsExactly("10.0.0.7/");
        }

        @Test
        @DisplayName("completion never offers an undiscovered node")
        void completionDoesNotLeak(@TempDir Path dir) {
            // Completing an unscanned address would hand the player, for free, what recon is priced
            // to sell (docs/design/07 §3). This is the least obvious way the client could become
            // authoritative and a one-line mistake to make.
            Shell s = shell(dir);
            assertThat(s.complete("ls /net/")).isEmpty();
        }

        @Test
        @DisplayName("the root lists the four documented trees")
        void rootLayout(@TempDir Path dir) {
            assertThat(Namespace.list(shell(dir).session(), "/"))
                    .containsExactly("rig/", "net/", "ledger/", "man/");
        }
    }

    @Nested
    @DisplayName("completion")
    class Completion {

        @Test
        @DisplayName("the first word completes against commands")
        void commandCompletion(@TempDir Path dir) {
            assertThat(shell(dir).complete("le")).contains("ledger");
        }

        @Test
        @DisplayName("a word beginning with - completes against flags")
        void flagCompletion(@TempDir Path dir) {
            assertThat(shell(dir).complete("scan --")).contains("--help", "--dry-run");
        }
    }

    @Nested
    @DisplayName("the universal flags")
    class UniversalFlags {

        @Test
        @DisplayName("-h prints the synopsis without running anything")
        void helpDoesNotRun(@TempDir Path dir) {
            Shell s = shell(dir);
            Shell.Result result = s.run("mine -h");

            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(s.session().computeBudget().available().cycles()).isEqualTo(100);
        }

        @Test
        @DisplayName("--dry-run prints published costs and never a verdict")
        void dryRunPrintsNoVerdict(@TempDir Path dir) {
            Shell s = shell(dir);
            Shell.Result result = s.run("scan --thorough -n");

            String text = String.join(" ", result.lines()).toLowerCase();
            assertThat(text).contains("35");
            // Pillar C4 and Invariant I14: the client prints the numbers and lets the player do the
            // arithmetic. It must never say "affordable" — gate evaluation is not its job.
            assertThat(text).doesNotContain("affordable").doesNotContain("you can afford");
            assertThat(s.session().computeBudget().available().cycles()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("exit statuses reach the caller")
    class Statuses {

        @Test
        @DisplayName("a refused action reports 1, and nothing changed")
        void refusalIsOne(@TempDir Path dir) {
            Shell s = shell(dir);
            Shell.Result result = s.run("mine --allocate=5000");

            assertThat(result.status()).isEqualTo(ExitStatus.REFUSED);
            assertThat(s.session().computeBudget().available().cycles()).isEqualTo(100);
        }

        @Test
        @DisplayName("a successful action reports 0 and is applied")
        void successIsZero(@TempDir Path dir) {
            Shell s = shell(dir);
            assertThat(s.run("mine --allocate=40").status()).isEqualTo(ExitStatus.OK);
            assertThat(s.session().computeBudget().available().cycles()).isEqualTo(60);
        }

        @Test
        @DisplayName("$? is remembered between lines")
        void lastStatusRemembered(@TempDir Path dir) {
            Shell s = shell(dir);
            s.run("mine --allocate=5000");
            assertThat(s.lastStatus()).isEqualTo(ExitStatus.REFUSED);
            s.run("ps");
            assertThat(s.lastStatus()).isEqualTo(ExitStatus.OK);
        }

        @Test
        @DisplayName("EX_UNAVAILABLE and refused are different numbers, and must stay that way")
        void refusedAndUnavailableDiffer() {
            // docs/client/01 §9.4: "the server refused this" and "we could not reach the server"
            // must never collapse into one message. Different numbers make that structural.
            assertThat(ExitStatus.REFUSED).isNotEqualTo(ExitStatus.UNAVAILABLE);
            assertThat(ExitStatus.UNAVAILABLE).isEqualTo(69);
            assertThat(ExitStatus.TEMPFAIL).isEqualTo(75);
            assertThat(ExitStatus.NOPERM).isEqualTo(77);
            assertThat(ExitStatus.ABORTED).isEqualTo(130);
        }
    }

    @Nested
    @DisplayName("verify is honest about solo items")
    class Verify {

        @Test
        @DisplayName("a solo item reports no chain rather than inventing one")
        void noFakeProvenance(@TempDir Path dir) {
            Shell s = shell(dir);
            LocalGameSession local = (LocalGameSession) s.session();
            var item = new io.github.stoicswe.eyeandsickle.solo.state.ItemState();
            item.displayName = "Overflow Kit";
            item.tier = "VAULT";
            local.game().state().items.add(item);

            List<String> lines = s.run("verify \"Overflow Kit\"").lines();
            assertThat(String.join(" ", lines)).contains("no provenance chain");
            assertThat(String.join(" ", lines)).doesNotContain("verified to genesis");
        }
    }
}
