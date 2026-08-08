package io.github.stoicswe.eyeandsickle.engine.breach;

import static io.github.stoicswe.eyeandsickle.engine.breach.BreachTestKit.T0;
import static io.github.stoicswe.eyeandsickle.engine.breach.BreachTestKit.nodeTarget;
import static io.github.stoicswe.eyeandsickle.engine.breach.BreachTestKit.withNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.net.NodeReports;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which puzzle a breach draws, and what recon has to do with it.
 *
 * <h2>The rule</h2>
 *
 * The <b>offset cipher is the default</b> — it is the puzzle that needs nothing from the far side,
 * because deriving an offset from ciphertext is what you do when you have no other handle. Breach
 * Protocol is the puzzle of someone who already knows the host, so its odds rise with how much of the
 * port-scan report is filled in: nothing known means the cipher, everything known means the grid
 * about nineteen times in twenty.
 *
 * <h2>⚠ What this must not become</h2>
 *
 * A <b>different</b> puzzle, never an easier one. Nothing here touches the tier, the attention budget,
 * the strike limit or the layer count, and {@link Pricing} asserts that directly — if recon ever buys
 * a cheaper board rather than a different one, it has turned into a discount and Invariant <b>I7</b>'s
 * claim that the gate certifies skill stops being true.
 */
class BreachPuzzleWeightingTest {

    private static final String ADDRESS = "10.0.0.5";

    /** Establishes the first {@code findings} of the report, in the order a scanner reaches them. */
    private static void scanned(GameSave save, int findings) {
        NodeReportState report = new NodeReportState();
        report.address = ADDRESS;
        report.createdAt = T0;
        report.updatedAt = T0;
        report.scans = 1;
        // ⚠ THE FINDINGS THIS MACHINE ACTUALLY HAS, in depth order — not the first N of values().
        // The ladder became per-kind on 2026-08-07, and 10.0.0.5 is a BRIDGE in the generated world,
        // whose set is IDENTITY, FIREWALL, OS_VERSION, PEERS, MONITORED. Walking values() blind
        // established CYCLE_CAPABILITY on a router: `known` correctly refused to count it, so the
        // fourth "finding" moved the fraction by nothing and the linearity this file exists to prove
        // was broken by the fixture rather than by the code.
        List<PortScanTarget> targets = java.util.Arrays.stream(PortScanTarget.values())
                .filter(target -> target.appliesTo(kindAt(save)))
                .sorted(java.util.Comparator.comparingInt(PortScanTarget::depth))
                .toList();
        for (int i = 0; i < Math.min(findings, targets.size()); i++) {
            report.learnedAt.put(targets.get(i).name(), T0);
        }
        save.nodeReports.add(report);
    }

    /**
     * What kind of machine {@link #ADDRESS} is in this world — the denominator {@code known} uses.
     *
     * <p>⚠ Read from the topology rather than assumed. {@code withNode} adds a known NODE at a fixed
     * address over a GENERATED world, so what that address is depends on the seed, and for these
     * seeds it is a bridge. A hard-coded kind here was wrong twice over: it named the wrong one, and
     * it would have gone on being wrong silently the next time the generator moved.
     */
    private static io.github.stoicswe.eyeandsickle.protocol.game.HostKind kindAt(GameSave save) {
        return save.topology.hosts.stream()
                .filter(host -> ADDRESS.equals(host.address))
                .findFirst()
                .map(host -> io.github.stoicswe.eyeandsickle.engine.net.HostArchetypes.kindOrUnknown(host.kind))
                .orElse(io.github.stoicswe.eyeandsickle.protocol.game.HostKind.UNKNOWN);
    }

    /** How often {@code findings} worth of report produces a protocol grid, over many seeds. */
    private static double protocolRate(int findings, int seeds) {
        int protocol = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            GameSave save = withNode(seed, 3, 0, false, false);
            if (findings > 0) {
                scanned(save, findings);
            }
            BreachRules.begin(save, nodeTarget(save), T0);
            if ("BREACH_PROTOCOL".equals(save.activeBreach.puzzleClass)) {
                protocol++;
            }
        }
        return protocol / (double) seeds;
    }

    @Nested
    @DisplayName("how complete a report is")
    class Knowledge {

        @Test
        @DisplayName("a machine nobody has looked at is zero, a fully scanned one is one")
        void theEnds() {
            GameSave blank = withNode(1L, 3, 0, false, false);
            assertThat(NodeReports.known(blank, ADDRESS)).isZero();
            // ⚠ A machine with no file at all, not merely an empty one. Both must read zero, or an
            // unscanned target stops behaving like the default.
            assertThat(NodeReports.known(blank, "10.9.9.9")).isZero();

            GameSave full = withNode(1L, 3, 0, false, false);
            scanned(full, PortScanTarget.values().length);
            assertThat(NodeReports.known(full, ADDRESS)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("each finding is worth the same increment, so there is no threshold to discover")
        void isLinear() {
            // ⚠ THE APPLICABLE COUNT, NOT values().length. Bridges gained two findings of their
            // own on 2026-08-07; an ordinary machine still has exactly eight, and this test is what
            // proves the addition did not quietly re-weight every breach in the game — `known` feeds
            // breachProtocolShare, so an 8/10 denominator would have moved the odds on every target
            // a player has ever scanned, silently, with every screen still rendering.
            // ⚠ ONE SAVE, REUSED — never two calls to withNode expecting the same world.
            //
            // Measured 2026-08-07: `withNode(1L, …)` returns a DIFFERENT world on every call, despite
            // taking a seed. Four consecutive calls gave 148, 136, 219 and 171 hosts, and the machine
            // at 10.0.0.5 came back TERMINAL, TERMINAL, TERMINAL, BRIDGE. So a denominator taken from
            // one call and findings established against another disagree about what kind of machine
            // this even is — which is invisible while every kind has the same number of findings, and
            // is exactly what broke here the moment bridges got their own set.
            //
            // ⚠ Not fixed in BreachTestKit from here: `save(seed)`'s determinism is relied on by the
            // whole breach suite and is its own change. Logged for the user; this test simply stops
            // depending on it.
            GameSave save = withNode(1L, 3, 0, false, false);
            int total = PortScanTarget.countFor(kindAt(save));
            for (int found = 0; found <= total; found++) {
                save.nodeReports.clear();
                if (found > 0) {
                    scanned(save, found);
                }
                assertThat(NodeReports.known(save, ADDRESS))
                        .as("%d of %d findings", found, total)
                        .isCloseTo(found / (double) total, offset(1e-9));
            }
        }
    }

    @Nested
    @DisplayName("the draw")
    class Draw {

        @Test
        @DisplayName("an unscanned machine always gives the offset cipher")
        void blindIsAlwaysTheCipher() {
            // Exact, not a band: the share at zero knowledge is zero, so this is a property rather
            // than a rate. A single protocol grid here would mean the default is not the default.
            assertThat(protocolRate(0, 200)).isZero();
        }

        @Test
        @DisplayName("a fully scanned machine gives the protocol grid about 95% of the time")
        void informedIsAlmostAlwaysTheGrid() {
            double rate = protocolRate(PortScanTarget.values().length, 400);
            assertThat(rate)
                    .as("fully scanned protocol rate was %.3f", rate)
                    .isCloseTo(Balance.BREACH_PROTOCOL_SHARE_INFORMED, offset(0.06d));
            // ⚠ And NOT a certainty. The residual is deliberate: a machine that can still surprise a
            // well-prepared operator once in twenty is the fiction working, and a guaranteed puzzle
            // means the cipher stops being practised by anyone who scans.
            assertThat(rate).isLessThan(1.0d);
        }

        @Test
        @DisplayName("partial knowledge sits between the two, rising with the report")
        void partialKnowledgeRises() {
            double none = protocolRate(0, 300);
            double half = protocolRate(PortScanTarget.values().length / 2, 300);
            double all = protocolRate(PortScanTarget.values().length, 300);
            assertThat(half).isGreaterThan(none).isLessThan(all);
        }
    }

    @Nested
    @DisplayName("what recon must NOT buy")
    class Pricing {

        /**
         * ⚠ The invariant guard, and the reason this test exists at all.
         *
         * <p>Recon changes which puzzle is drawn and nothing else. If it ever also moved the budget,
         * the strike limit or the layer count, it would be buying a cheaper breach — and a
         * proof-of-skill gate that can be bought down is no longer proof of skill (<b>I7</b>).
         */
        @Test
        @DisplayName("a scanned target's board is priced exactly like an unscanned one")
        void knowledgeBuysNoAdvantage() {
            GameSave blind = withNode(77L, 3, 2, false, false);
            GameSave informed = withNode(77L, 3, 2, false, false);
            scanned(informed, PortScanTarget.values().length);

            BreachRules.begin(blind, nodeTarget(blind), T0);
            BreachRules.begin(informed, nodeTarget(informed), T0);

            assertThat(informed.activeBreach.layers).hasSameSizeAs(blind.activeBreach.layers);
            assertThat(informed.activeBreach.difficultyTier).isEqualTo(blind.activeBreach.difficultyTier);
            assertThat(informed.activeBreach.reservedCycles)
                    .as("a breach costs what it costs")
                    .isEqualTo(blind.activeBreach.reservedCycles);
            for (int i = 0; i < blind.activeBreach.layers.size(); i++) {
                assertThat(informed.activeBreach.layers.get(i).budget)
                        .as("layer %d attention", i)
                        .isEqualTo(blind.activeBreach.layers.get(i).budget);
                assertThat(informed.activeBreach.layers.get(i).strikeLimit)
                        .as("layer %d strikes", i)
                        .isEqualTo(blind.activeBreach.layers.get(i).strikeLimit);
            }
        }

        /**
         * ⚠ {@code Rng}'s contract: the class roll costs one draw whatever the weight.
         *
         * <p>Skipping the roll when the weight is zero would be the obvious optimisation and would
         * break replay: every later draw in the breach would then depend on whether the player had
         * scanned the target, so two otherwise identical attempts would generate different boards
         * from the same seed.
         *
         * <h2>⚠ Only comparable when both attempts drew the same class</h2>
         *
         * Once the classes differ the streams legitimately diverge — a grid and a cipher are built
         * from different numbers of draws, which was already true when the class was a coin flip. So
         * the test finds a seed on which the <em>informed</em> attempt lands in the 5% that still
         * gets a cipher, and then the two are comparable: same class, same board, and therefore the
         * same final RNG position if and only if the roll before it cost the same.
         */
        @Test
        @DisplayName("the class roll costs one draw whether or not the target was scanned")
        void theRollCostsTheSameEitherWay() {
            for (long seed = 1; seed <= 500; seed++) {
                GameSave blind = withNode(seed, 4, 0, false, false);
                GameSave informed = withNode(seed, 4, 0, false, false);
                scanned(informed, PortScanTarget.values().length);

                BreachRules.begin(blind, nodeTarget(blind), T0);
                BreachRules.begin(informed, nodeTarget(informed), T0);
                if (!"OFFSET_CIPHER".equals(informed.activeBreach.puzzleClass)) {
                    continue;
                }

                assertThat(blind.activeBreach.puzzleClass).isEqualTo("OFFSET_CIPHER");
                assertThat(informed.rngSeed)
                        .as("same class from seed %d, so the same draws must have been taken", seed)
                        .isEqualTo(blind.rngSeed);
                return;
            }
            // ⚠ Not a silent pass. At a 5% residual over 500 seeds this is astronomically unlikely,
            // so reaching here means the residual is gone — which is itself the thing to know.
            throw new AssertionError(
                    "no seed in 500 gave a cipher against a fully scanned target — the 5% residual is missing");
        }
    }
}
