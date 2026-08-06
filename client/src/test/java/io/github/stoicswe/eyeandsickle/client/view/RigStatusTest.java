package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
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

    /**
     * The fixture's ceiling — the top of the compute ladder, which {@code TestSaves.bare} grants.
     *
     * <p>⚠ These assertions were written when a starting rig was 100 cycles. A starting rig is 24
     * now and the fixture is 64, so every rate and load figure below is DERIVED from this rather
     * than written out — a literal would be asserting the ladder's shape from a test about readouts.
     */
    private static final long CAPACITY =
            io.github.stoicswe.eyeandsickle.engine.Balance.COMPUTE_RUNGS[
                    io.github.stoicswe.eyeandsickle.engine.Balance.COMPUTE_RUNGS.length - 1];

    /** design/03 §1's published rate: 0.4 EC per cycle-hour. The per-cycle figure is the invariant. */
    private static final double EC_PER_CYCLE_HOUR = 0.4d;

    /**
     * What a full rig earns an hour, pooled, at this fixture's ceiling.
     *
     * <p>⚠ Every literal {@code 40.0} in this class was this figure for a 100-cycle rig. It is
     * derived now, because the number that {@code design/03} §1 actually publishes is the
     * <b>per-cycle-hour</b> rate — the total was always a consequence of the ceiling, and became a
     * misleading constant the moment the ceiling moved.
     */
    private static final double FULL_RATE = CAPACITY * EC_PER_CYCLE_HOUR;

    private static LocalGameSession session(Path dir) {
        return new LocalGameSession(io.github.stoicswe.eyeandsickle.client.support.TestSaves.bare(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")), "op", CLOCK));
    }

    /**
     * Arms a defence, granting the item first.
     *
     * <h2>⚠ Why the grant, and why it does not weaken these tests</h2>
     *
     * Arming has required OWNING since 2026-08-06 ({@code LocalGameSession.armIntent}), so these
     * assertions began failing on a refusal rather than on the arithmetic they are about. The tests
     * in this class are about <b>defensive posture</b> — how many measures are up and what they cost
     * — and not about the unlock ladder, which {@code CatalogueTest} and
     * {@code ShortcutsTest.gatesExplainThemselves} cover. Rewriting each one to buy its way up the
     * catalogue would bury the posture arithmetic under an unrelated concern and would have to be
     * redone the day a price changes. Same argument, and the same shape, as
     * {@code GameEngineTest.bare()} removing the tutorial parasite.
     *
     * <p>⚠ It still goes through {@code arm}, so a refusal for any OTHER reason — not enough cycles,
     * the kind already armed — still fails the test it should.
     */
    private static void stockAndArm(LocalGameSession s, String kind, int tier) {
        String offeringId = io.github.stoicswe.eyeandsickle.engine.Catalogue.defenceOfferingId(kind, tier)
                .orElseThrow(() -> new IllegalArgumentException("no such defence: " + kind));
        io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(offeringId).ifPresent(offering -> {
            var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
            item.itemType = offering.id();
            item.displayName = offering.name();
            item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
            s.game().state().items.add(item);
        });
        s.arm(kind, tier);
    }

    /** How much a block's fees add to a reward that includes them. See MiningRules.rewardBase. */
    private static final double FEE_EXPOSURE = new java.math.BigDecimal(
                    io.github.stoicswe.eyeandsickle.engine.Balance.BLOCK_SUBSIDY_WEI.add(
                            io.github.stoicswe.eyeandsickle.engine.Balance.expectedBlockFeesWei()))
            .divide(
                    new java.math.BigDecimal(io.github.stoicswe.eyeandsickle.engine.Balance.BLOCK_SUBSIDY_WEI),
                    java.math.MathContext.DECIMAL64)
            .doubleValue();

    @Nested
    @DisplayName("income projection")
    class Income {

        @Test
        @DisplayName("a full rig projects the design/03 §1 figure of 40 EC/hr")
        void fullRigRate(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(CAPACITY);

            RigStatus status = RigStatus.of(s);
            assertThat(status.incomePerHour())
                    .isEqualTo(String.format(java.util.Locale.ROOT, "%.2f", CAPACITY * EC_PER_CYCLE_HOUR));
            // 40 EC/hr ÷ 3600 = 0.0111 EC/s. Four decimals because two would show a flat 0.01 that
            // never moves, which defeats the point of a live readout.
            assertThat(status.incomePerSecond())
                    .isEqualTo(String.format(
                            java.util.Locale.ROOT, "%.4f", CAPACITY * EC_PER_CYCLE_HOUR / 3600.0d));
        }

        @Test
        @DisplayName("⚠ the strip's +EC/HR tracks the mode, not a flat rate")
        void rateTracksTheMode(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(CAPACITY);
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
            assertThat(Double.parseDouble(soloRate)).isCloseTo(FULL_RATE / 0.98d * FEE_EXPOSURE, within(0.05d));
        }

        @Test
        @DisplayName("⚠ the strip's +EC/HR tracks the pool's fee too")
        void rateTracksThePool(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(CAPACITY);

            s.setMiningPool("meridian");
            double dear = Double.parseDouble(RigStatus.of(s).incomePerHour());
            s.setMiningPool("small-hours");
            double cheap = Double.parseDouble(RigStatus.of(s).incomePerHour());

            // ⚠ These two differ by MORE than their fees now, and deliberately: meridian is
            // pay-per-share and small-hours is PPLNS, so only the second one is paid any of the
            // block fees its pool collects. The strip has to show the whole gap — it is the only
            // place a player sees their rate without opening a panel.
            assertThat(cheap).isGreaterThan(dear);
            double dearExpected = FULL_RATE * (1 - 0.035d) / 0.98d;
            double cheapExpected = FULL_RATE * (1 - 0.005d) / 0.98d * FEE_EXPOSURE;
            assertThat(dear).isCloseTo(dearExpected, within(0.1d));
            assertThat(cheap).isCloseTo(cheapExpected, within(0.1d));
        }

        @Test
        @DisplayName("the projected rate is what a hypothetical allocation would earn, asked of the engine")
        void previewsAnUncommittedAllocation(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            s.allocateSelfMining(CAPACITY);

            // What the MINING panel prices its slider with while the player is still dragging. The
            // panel must not scale the committed figure itself — that is how the third copy of this
            // rate got into the client and stayed wrong.
            // ⚠ To double precision, not to the wei — the rate is derived through the network
            // hashrate, which is a double. See MiningChainTest.defaultPoolIsTheAnchor.
            assertThat(ec(s.miningRateFor(CAPACITY)))
                    .isCloseTo(CAPACITY * EC_PER_CYCLE_HOUR, withinPercentage(1e-10d));
            assertThat(ec(s.miningRateFor(CAPACITY / 2)))
                    .isCloseTo(FULL_RATE / 2, withinPercentage(1e-10d));
            assertThat(s.miningRateFor(0)).isZero();
            // And the preview must not disturb what the rig is actually doing.
            assertThat(s.mining().selfMiningCycles()).isEqualTo(CAPACITY);
            assertThat(ec(s.miningChain().expectedWeiPerHour()))
                    .isCloseTo(FULL_RATE, withinPercentage(1e-10d));
        }

        @Test
        @DisplayName("the rate is proportional to the allocation, and zero at zero")
        void rateScales(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            assertThat(RigStatus.of(s).incomePerHour()).isEqualTo("0.00");

            s.allocateSelfMining(CAPACITY / 2);
            assertThat(RigStatus.of(s).incomePerHour())
                    .isEqualTo(String.format(java.util.Locale.ROOT, "%.2f", FULL_RATE / 2));
        }

        @Test
        @DisplayName("the projection matches what the engine actually pays out")
        void projectionMatchesReality(@TempDir Path dir) {
            // The whole risk of a derived readout is that it drifts from the rules. This pins the
            // projection against a real hour of the engine's own arithmetic.
            LocalGameSession s = session(dir);
            s.allocateSelfMining(CAPACITY);

            java.math.BigInteger projectedPerHour = RigStatus.of(s).incomeWeiPerHour();
            java.math.BigInteger enginePerHour = s.miningChain().expectedWeiPerHour();

            // ⚠ Read off the port, not recomputed. Since self-mining became a Poisson process the
            // rate depends on the MODE — a solo miner keeps the pool's fee — so a readout with its
            // own constant would be right for pooled players and wrong for everyone else.
            assertThat(projectedPerHour).isEqualTo(enginePerHour);
            assertThat(ec(projectedPerHour)).isCloseTo(FULL_RATE, withinPercentage(1e-10d));
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
            stockAndArm(s, "canary", 1); // 1 cycle
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.MINIMAL);

            stockAndArm(s, "firewall", 1); // +5
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.PARTIAL);

            stockAndArm(s, "tarpit", 1); // +8 -> 3 armed, 14 cycles
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.LAYERED);

            stockAndArm(s, "detection-array", 3); // +25 -> 39 cycles
            stockAndArm(s, "honeypot-stash", 1); // +12 -> 51 cycles
            assertThat(RigStatus.of(s).posture()).isEqualTo(RigStatus.DefensePosture.PARANOID);
        }

        @Test
        @DisplayName("armed defences hold cycles, and the readout says how many")
        void defenceCyclesAreCounted(@TempDir Path dir) {
            LocalGameSession s = session(dir);
            stockAndArm(s, "firewall", 3); // 15 cycles

            RigStatus status = RigStatus.of(s);
            assertThat(status.armedDefenses()).isEqualTo(1);
            assertThat(status.defenseCycles()).isEqualTo(15);
            // And it comes out of the same budget everything else draws on.
            assertThat(status.budget().available().cycles())
                    .isEqualTo(CAPACITY - io.github.stoicswe.eyeandsickle.engine.Balance.DEFENSE_FIREWALL_T3_CYCLES);
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

            // ⚠ A QUARTER of the ceiling, derived. A literal 25 was a quarter of a 100-cycle
            // rig and is 39% of this one, so the assertion would have been about the ladder.
            s.allocateSelfMining(CAPACITY / 4);
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
