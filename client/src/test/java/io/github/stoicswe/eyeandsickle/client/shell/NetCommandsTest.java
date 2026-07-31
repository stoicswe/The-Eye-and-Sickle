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
 * Tests for the network verbs.
 *
 * <h2>What is worth asserting on a shell surface</h2>
 *
 * Mostly the refusals and the shapes, for the reason {@code ShellTest} gives: the interesting
 * question is not "does it run" but "does it refuse what it promised to refuse, and does it print
 * what it promised not to guess". Three promises are checked here — that {@code net} may head a
 * pipeline and the three intents may not, that a dry run takes nothing and delivers no verdict, and
 * that a flag that eats its own argument does not turn a valid line into a usage error.
 *
 * <h2>The registry is built here rather than assumed</h2>
 *
 * {@link NetCommands#register} is called explicitly instead of relying on
 * {@link BuiltinCommands#registry()} having been taught to call it. That keeps this suite green
 * before the integrator wires item 12 and harmless afterwards — a second {@code add} of the same
 * name replaces the entry with an identical one.
 */
class NetCommandsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC);

    private static Shell shell(Path dir) {
        GameSession session = new LocalGameSession(TestSaves.bare(new SaveStore(dir.resolve("s.json")), "op", CLOCK));
        Shell.CommandRegistry registry = BuiltinCommands.registry();
        NetCommands.register(registry);
        return new Shell(session, registry);
    }

    @Nested
    @DisplayName("sources and actions")
    class Catalogue {

        @Test
        @DisplayName("all four verbs are in the catalogue a player can see")
        void registered(@TempDir Path dir) {
            List<String> names = shell(dir).registry().names();
            assertThat(names).contains("net", "sweep", "connect", "download");
        }

        @Test
        @DisplayName("net is a source, so it may head a pipeline")
        void netIsASource(@TempDir Path dir) {
            // `net | grep bridge` is the documented use, and it is why a bridge row carries the
            // lowercase word as well as the uppercase kind — grep here is case-sensitive.
            Shell.Result result = shell(dir).run("net | grep bridge");
            assertThat(result.status()).isEqualTo(ExitStatus.OK);
        }

        @Test
        @DisplayName("the three intents are refused inside a pipeline, before any of it runs")
        void actionsAreRefusedInPipelines(@TempDir Path dir) {
            Shell shell = shell(dir);
            for (String line : List.of("net | sweep", "net | connect 10.0.0.4", "net | download 10.0.0.4")) {
                Shell.Result result = shell.run(line);
                assertThat(result.status()).as("%s", line).isEqualTo(ExitStatus.USAGE);
                assertThat(result.lines().getFirst()).as("%s", line).contains("changes something");
            }
        }
    }

    @Nested
    @DisplayName("net")
    class Net {

        @Test
        @DisplayName("it answers, whatever has been discovered")
        void answers(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("net");
            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(result.lines()).isNotEmpty();
        }

        @Test
        @DisplayName("-v leads with the server strip, which the window keeps permanently on screen")
        void verboseLeadsWithTheStrip(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("net -v");
            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(result.lines().getFirst()).startsWith("SERVER").contains("CEILING");
        }

        @Test
        @DisplayName("--docs on a fresh character says nothing has been recovered, and where they live")
        void noDocumentsYet(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("net --docs");
            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(String.join(" ", result.lines()))
                    .contains("nothing recovered yet")
                    // N-4 made visible: the reading starts one bridge out, so nothing on the early
                    // critical path can depend on it.
                    .contains("never on the home server");
        }

        @Test
        @DisplayName("--docs with an id answers about the collection, not about the id, when it is empty")
        void unknownDocumentBeforeAnyAreRecovered(@TempDir Path dir) {
            // "you asked for a fragment you have not recovered" is a worse answer than "you have not
            // recovered any", because the second one is the fact that explains the first.
            Shell.Result result = shell(dir).run("net --docs doc.nothing");
            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(String.join(" ", result.lines())).contains("nothing recovered yet");
        }
    }

    @Nested
    @DisplayName("sweep")
    class Sweep {

        @Test
        @DisplayName("a dry run prints the published cost, the duration and the ceiling")
        void dryRunPrintsTheFigures(@TempDir Path dir) {
            Shell shell = shell(dir);
            long before = shell.session().computeBudget().available().cycles();

            Shell.Result result = shell.run("sweep -n");
            String output = String.join("\n", result.lines());

            assertThat(result.status()).isEqualTo(ExitStatus.OK);
            assertThat(output).contains("net-sweep").contains("2 cycles").contains("20s");
            assertThat(output).contains("ceiling: 1 hop");
            assertThat(shell.session().computeBudget().available().cycles())
                    .as("a dry run takes nothing")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("a dry run delivers no verdict — no affordability, no prediction")
        void dryRunDeliversNoVerdict(@TempDir Path dir) {
            // docs/client/04 §3.4 and Invariant I14: gate evaluation belongs to the rules. And
            // detection is a roll made once at world generation and stored, so an estimate of what
            // a sweep would find would be reading the answer out of the save.
            String output =
                    String.join("\n", shell(dir).run("sweep -n").lines()).toLowerCase(java.util.Locale.ROOT);
            assertThat(output)
                    .doesNotContain("affordable")
                    .doesNotContain("would find")
                    .doesNotContain("expect");
        }

        @Test
        @DisplayName("each tier prints its own published figures")
        void perTierFigures(@TempDir Path dir) {
            Shell shell = shell(dir);
            assertThat(String.join("\n", shell.run("sweep --wide -n").lines()))
                    .contains("net-sweep-wide")
                    .contains("5 cycles")
                    .contains("45s");
            assertThat(String.join("\n", shell.run("sweep --deep -n").lines()))
                    .contains("net-sweep-deep")
                    .contains("9 cycles")
                    .contains("90s");
        }

        @Test
        @DisplayName("--wide and --deep together is a usage error, not a silent precedence rule")
        void twoTiersAtOnce(@TempDir Path dir) {
            Shell.Result result = shell(dir).run("sweep --wide --deep -n");
            assertThat(result.status()).isEqualTo(ExitStatus.USAGE);
            assertThat(result.lines().getFirst()).contains("pick one");
        }

        @Test
        @DisplayName("the help says the two things a player most needs and most easily gets wrong")
        void help(@TempDir Path dir) {
            String help = String.join("\n", shell(dir).run("sweep -h").lines());
            // That reach is not for sale is Invariant I2 in the player's language, and that this is
            // not `scan` is decision N-2 in the player's language. Both belong where somebody
            // confused enough to type -h will read them.
            assertThat(help).contains("REACH IS NOT FOR SALE");
            assertThat(help).contains("This is not scan(1)");
        }
    }

    @Nested
    @DisplayName("connect and download")
    class Traversal {

        @Test
        @DisplayName("both refuse an empty address with a usage line that names where to look")
        void missingAddress(@TempDir Path dir) {
            Shell shell = shell(dir);
            Shell.Result connect = shell.run("connect");
            assertThat(connect.status()).isEqualTo(ExitStatus.USAGE);
            assertThat(connect.lines().getFirst()).contains("`net`");

            Shell.Result download = shell.run("download");
            assertThat(download.status()).isEqualTo(ExitStatus.USAGE);
            assertThat(download.lines().getFirst()).contains("grep document");
        }

        @Test
        @DisplayName("`-n <address>` still names the address, even though the parser eats it as a value")
        void shortDryRunFlagDoesNotSwallowTheAddress(@TempDir Path dir) {
            // CommandLine.takesValue lists "n" — it is head -n 5's flag as much as it is --dry-run's
            // short form — so `connect -n 10.0.0.9` parses as flags{n: "10.0.0.9"} with NO positional
            // argument at all. A verb reading only argument(0) would answer "connect <address>" to a
            // line that plainly named one.
            Shell shell = shell(dir);
            Shell.Result connect = shell.run("connect -n 10.0.0.9");
            assertThat(connect.status()).isEqualTo(ExitStatus.OK);
            assertThat(connect.lines().getFirst()).contains("10.0.0.9");

            Shell.Result download = shell.run("download -n 10.0.0.9");
            assertThat(download.status()).isEqualTo(ExitStatus.OK);
            assertThat(download.lines().getFirst()).contains("10.0.0.9");
        }

        @Test
        @DisplayName("a connect dry run moves nothing and says where the ceiling is measured from")
        void connectDryRun(@TempDir Path dir) {
            Shell shell = shell(dir);
            String vantageBefore = shell.session().net().vantageAddress();

            String output = String.join("\n", shell.run("connect -n 10.0.0.9").lines());
            assertThat(output).contains("measured from there, not from your rig");
            assertThat(shell.session().net().vantageAddress()).isEqualTo(vantageBefore);
        }

        @Test
        @DisplayName("a download dry run says outright that nothing in a document is required")
        void downloadDryRun(@TempDir Path dir) {
            // Decision N-4, stated where a player will actually meet it. Progression must not depend
            // on the narrative layer, and the surface that recovers the narrative layer is the
            // honest place to say so.
            String output =
                    String.join("\n", shell(dir).run("download -n 10.0.0.9").lines());
            assertThat(output).contains("nothing in one is required to advance");
        }
    }

    @Nested
    @DisplayName("the published sweep ladder")
    class SweepOptions {

        @Test
        @DisplayName("⚠ every published flag is one `sweep` actually accepts")
        void flagsRoundTrip(@TempDir Path dir) {
            // The map window's controls are built from these flags and press `session.sweep(flag)`
            // with them. A published flag the verb does not recognise would be a control that
            // reports "unknown sweep tier" on a label the panel itself wrote — which reads as the
            // game being broken rather than as a typo.
            GameSession session = shell(dir).session();
            for (var option : session.sweepOptions()) {
                assertThat(io.github.stoicswe.eyeandsickle.solo.net.SweepTier.byFlag(option.flag()))
                        .as("`sweep %s` is a tier the rules know", option.flag())
                        .isPresent();
            }
            assertThat(session.sweepOptions())
                    .extracting(GameSession.SweepOption::flag)
                    .containsExactly("", "--wide", "--deep");
        }

        @Test
        @DisplayName("a new character owns the base rung and neither of the bought ones")
        void startingKit(@TempDir Path dir) {
            // docs/design/06 §2: the base sweep is starting kit, not free content — it is the floor
            // every price is measured from. The other two are ethecoin-gated (I2: sensitivity, never
            // reach).
            var options = shell(dir).session().sweepOptions();
            assertThat(options.getFirst().available()).isTrue();
            assertThat(options.getFirst().requirement()).isEmpty();
            assertThat(options.get(1).available()).isFalse();
            assertThat(options.get(2).available()).isFalse();
        }

        @Test
        @DisplayName("a locked rung names the tool and its price — never a bare 'locked'")
        void requirementIsInWords(@TempDir Path dir) {
            // docs/client/05 §5: never a generic "locked". A player told only a price has been given
            // a number, not a route.
            var wide = shell(dir).session().sweepOptions().get(1);
            assertThat(wide.requirement()).contains("Net Sweep (Wide)").contains("EC");
            assertThat(wide.priceWei()).isPositive();
        }

        @Test
        @DisplayName("⚠ the published verdict is the same one `sweep` enforces")
        void verdictMatchesTheRefusal(@TempDir Path dir) {
            // The failure this rules out: a control that reads locked and a sweep that then works,
            // or the reverse. Both paths go through NetRules.owns, and this is the assertion that
            // says they must keep doing so.
            Shell shell = shell(dir);
            var wide = shell.session().sweepOptions().get(1);
            assertThat(wide.available()).isFalse();
            assertThat(shell.session().sweep(wide.flag()).status()).isEqualTo(GameSession.Outcome.NOPERM);
        }
    }
}
