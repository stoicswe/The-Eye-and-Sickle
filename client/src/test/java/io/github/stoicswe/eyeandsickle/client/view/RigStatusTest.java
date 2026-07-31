package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the derived rig readout.
 *
 * <p>These matter because the readout is the surface {@code docs/design/04-mining.md} §3.1 trains the
 * player to trust: a discrepancy is supposed to be evidence, which only works if the numbers normally
 * agree with each other and with the rules engine.
 */
class RigStatusTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T12:00:00Z"), ZoneOffset.UTC);

    private static LocalGameSession session(Path dir) {
        return new LocalGameSession(io.github.stoicswe.eyeandsickle.client.support.TestSaves.bare(
                new SaveStore(dir.resolve("s.json")), "op", CLOCK));
    }

    /** How much a block's fees add to a reward that includes them. See MiningRules.rewardBase. */
    private static final double FEE_EXPOSURE = new java.math.BigDecimal(
                    io.github.stoicswe.eyeandsickle.solo.Balance.BLOCK_SUBSIDY_WEI.add(
                            io.github.stoicswe.eyeandsickle.solo.Balance.expectedBlockFeesWei()))
            .divide(
                    new java.math.BigDecimal(io.github.stoicswe.eyeandsickle.solo.Balance.BLOCK_SUBSIDY_WEI),
                    java.math.MathContext.DECIMAL64)
            .doubleValue();

    @Nested
    @DisplayName("income projection")
    class Income {

        @Test
        @DisplayName("a full rig projects the design/03 §1 figure of 40 EC/hr")
        void fullRigRate(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(100);

            RigStatus status = RigStatus.of(s);
            assertThat(status.incomePerHour()).isEqualTo("40.00");
            // 40 EC/hr ÷ 3600 = 0.0111 EC/s. Four decimals because two would show a flat 0.01 that
            // never moves, which defeats the point of a live readout.
            assertThat(status.incomePerSecond()).isEqualTo("0.0111");
        }

        @Test
        @DisplayName("⚠ the strip's +EC/HR tracks the mode, not a flat rate")
        void rateTracksTheMode(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(100);
            String pooledRate = RigStatus.of(s).incomePerHour();

            s.setMiningMode(MiningMode.SOLO);
            String soloRate = RigStatus.of(s).incomePerHour();

            // This is the readout in the deck's top strip, right under the balance. It used to be
            // `cycles × 40` computed here, which was right for a pooled miner and wrong for a solo
            // one by exactly the pool fee they are no longer paying. It reads the engine now.
            assertThat(soloRate).isNotEqualTo(pooledRate);
            assertThat(Double.parseDouble(soloRate)).isGreaterThan(Double.parseDouble(pooledRate));
            // ⚠ Two factors since 2026-07-27. The default pool is pay-per-share, so it neither
            // waives its 2% nor passes on block fees; solo gets both. See MiningRules.rewardBase.
            assertThat(Double.parseDouble(soloRate)).isCloseTo(40.0d / 0.98d * FEE_EXPOSURE, within(0.05d));
        }

        @Test
        @DisplayName("⚠ the strip's +EC/HR tracks the pool's fee too")
        void rateTracksThePool(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(100);

            s.setMiningPool("meridian");
            double dear = Double.parseDouble(RigStatus.of(s).incomePerHour());
            s.setMiningPool("small-hours");
            double cheap = Double.parseDouble(RigStatus.of(s).incomePerHour());

            // ⚠ These two differ by MORE than their fees now, and deliberately: meridian is
            // pay-per-share and small-hours is PPLNS, so only the second one is paid any of the
            // block fees its pool collects. The strip has to show the whole gap — it is the only
            // place a player sees their rate without opening a panel.
            assertThat(cheap).isGreaterThan(dear);
            double dearExpected = 40.0d * (1 - 0.035d) / 0.98d;
            double cheapExpected = 40.0d * (1 - 0.005d) / 0.98d * FEE_EXPOSURE;
            assertThat(dear).isCloseTo(dearExpected, within(0.1d));
            assertThat(cheap).isCloseTo(cheapExpected, within(0.1d));
        }

        @Test
        @DisplayName("the projected rate is what a hypothetical allocation would earn, asked of the engine")
        void previewsAnUncommittedAllocation(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(100);

            // What the MINING panel prices its slider with while the player is still dragging. The
            // panel must not scale the committed figure itself — that is how the third copy of this
            // rate got into the client and stayed wrong.
            // ⚠ To double precision, not to the wei — the rate is derived through the network
            // hashrate, which is a double. See MiningChainTest.defaultPoolIsTheAnchor.
            assertThat(ec(s.miningRateFor(100))).isCloseTo(40.0d, withinPercentage(1e-10d));
            assertThat(ec(s.miningRateFor(50))).isCloseTo(20.0d, withinPercentage(1e-10d));
            assertThat(s.miningRateFor(0)).isZero();
            // And the preview must not disturb what the rig is actually doing.
            assertThat(s.mining().selfMiningCycles()).isEqualTo(100L);
            assertThat(ec(s.miningChain().expectedWeiPerHour())).isCloseTo(40.0d, withinPercentage(1e-10d));
        }

        @Test
        @DisplayName("the rate is proportional to the allocation, and zero at zero")
        void rateScales(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            assertThat(RigStatus.of(s).incomePerHour()).isEqualTo("0.00");

            s.allocateSelfMining(50);
            assertThat(RigStatus.of(s).incomePerHour()).isEqualTo("20.00");
        }

        @Test
        @DisplayName("the projection matches what the engine actually pays out")
        void projectionMatchesReality(@TempDir Path dir) {
            // The whole risk of a derived readout is that it drifts from the rules. This pins the
            // projection against a real hour of the engine's own arithmetic.
            LocalGameSession s = session(dir);
            s.allocateSelfMining(100);

            java.math.BigInteger projectedPerHour = RigStatus.of(s).incomeWeiPerHour();
            java.math.BigInteger enginePerHour = s.miningChain().expectedWeiPerHour();

            // ⚠ Read off the port, not recomputed. Since self-mining became a Poisson process the
            // rate depends on the MODE — a solo miner keeps the pool's fee — so a readout with its
            // own constant would be right for pooled players and wrong for everyone else.
            assertThat(projectedPerHour).isEqualTo(enginePerHour);
            assertThat(ec(projectedPerHour)).isCloseTo(40.0d, withinPercentage(1e-10d));
        }
    }

    @Nested
    @DisplayName("defence posture")
    class Posture {

        @Test
        @DisplayName("nothing armed is undefended, and says so")
        void undefended(@TempDir Path dir) {
            RigStatus s = RigStatus.of(session(dir));
            assertThat(s.posture()).isEqualTo(RigStatus.DefensePosture.NONE);
            assertThat(s.posture().pips()).isZero();
            assertThat(s.posture().explanation()).contains("no resistance");
        }

        @Test
        @DisplayName("posture reflects both count and committed cycles")
        void countAndCycles(@TempDir Path dir) {
            // Three cheap defences and one expensive one are different postures even though the
            // first has more of them: layering is about independent failure modes, committed
            // capacity is about what you gave up.
            LocalGameSession s = session(dir);
            s.arm("canary", 1); // 1 cycle
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.MINIMAL);

            s.arm("firewall", 1); // +5
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.PARTIAL);

            s.arm("tarpit", 1); // +8 -> 3 armed, 14 cycles
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.LAYERED);

            s.arm("detection-array", 3); // +25 -> 39 cycles
            s.arm("honeypot-stash", 1); // +12 -> 51 cycles
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.PARANOID);
        }

        @Test
        @DisplayName("armed defences hold cycles, and the readout says how many")
        void defenceCyclesAreCounted(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.arm("firewall", 3); // 15 cycles

            RigStatus status = RigStatus.of(s);
            assertThat(status.armedDefenses()).isEqualTo(1);
            assertThat(status.defenseCycles()).isEqualTo(15);
            // And it comes out of the same budget everything else draws on.
            assertThat(status.budget().available().cycles()).isEqualTo(85);
        }
    }

    @Nested
    @DisplayName("heat bands")
    class Heat {

        @Test
        @DisplayName("the five bands are the ones design/04 §4 fixes")
        void fiveBands() {
            assertThat(RigStatus.HeatBand.values()).hasSize(5);
            assertThat(RigStatus.HeatBand.of(0)).isEqualTo(RigStatus.HeatBand.ZERO);
            assertThat(RigStatus.HeatBand.of(15)).isEqualTo(RigStatus.HeatBand.LOW);
            assertThat(RigStatus.HeatBand.of(40)).isEqualTo(RigStatus.HeatBand.MODERATE);
            assertThat(RigStatus.HeatBand.of(60)).isEqualTo(RigStatus.HeatBand.HIGH);
            assertThat(RigStatus.HeatBand.of(95)).isEqualTo(RigStatus.HeatBand.NAMED);
        }

        @Test
        @DisplayName("every band carries a name and a consequence, not just a colour")
        void bandsCarryMeaning() {
            // docs/client/01 §2.2.4: heat renders as a banded chip carrying the band NAME, never as
            // a continuous meter — and §5.2's never-colour-alone rule means the name is what the
            // player actually reads.
            for (RigStatus.HeatBand band : RigStatus.HeatBand.values()) {
                assertThat(band.label()).isNotBlank();
                assertThat(band.consequence())
                        .as("%s must say what it means", band)
                        .isNotBlank();
                assertThat(band.styleClass()).isEqualTo("es-heat-" + band.index());
            }
        }

        @Test
        @DisplayName("bands are ordered, so the pip count is monotonic")
        void bandsAreOrdered() {
            int previous = -1;
            for (RigStatus.HeatBand band : RigStatus.HeatBand.values()) {
                assertThat(band.index()).isGreaterThan(previous);
                previous = band.index();
            }
        }
    }

    @Nested
    @DisplayName("the readout stays honest")
    class Honesty {

        @Test
        @DisplayName("a fresh rig reconciles")
        void reconciles(@TempDir Path dir) {
            assertThat(RigStatus.of(session(dir)).reconciles()).isTrue();
        }

        @Test
        @DisplayName("load tracks what is actually committed")
        void loadIsAccurate(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            assertThat(RigStatus.of(s).load()).isZero();

            s.allocateSelfMining(25);
            assertThat(RigStatus.of(s).load()).isEqualTo(0.25d);
        }

        @Test
        @DisplayName("buffer fill is zero with no miners, and never divides by zero")
        void bufferFillIsSafe(@TempDir Path dir) {
            RigStatus s = RigStatus.of(session(dir));
            assertThat(s.deployedMiners()).isZero();
            assertThat(s.bufferFill()).isZero();
        }
    }

    /**
     * A wei amount as ethecoin, for the rate assertions below.
     *
     * <p>⚠ Local rather than shared: {@code solo}'s test helper is not on this module's test
     * classpath, and adding a test-jar dependency between modules to reach one method would be a
     * build change with a much longer shadow than three lines.
     */
    private static double ec(java.math.BigInteger wei) {
        return new java.math.BigDecimal(wei)
                .divide(new java.math.BigDecimal(
                        io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.WEI_PER_ETHECOIN))
                .doubleValue();
    }
}
