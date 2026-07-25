package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link LoadFactorThermalRecovery} — the first-pass Thermal Budget curve
 * ({@code docs/design/01-core-resources.md} §1.3).
 *
 * <p>The formula's constants are {@code [PROPOSAL]} and exist to be replaced, so these tests assert the
 * <em>shape</em> the source design fixes, not the exact numbers: recovery is slower the closer a rig
 * sits to capacity, that slowdown is superlinear near the ceiling, a better thermal tier recovers no
 * slower, and a fully-pinned (even over-subscribed) rig recovers slowly rather than never. Those four
 * are exactly the contract {@link ThermalRecoveryStrategy} promises callers of {@code spend}.
 */
class LoadFactorThermalRecoveryTest {

    private final LoadFactorThermalRecovery recovery =
            new LoadFactorThermalRecovery(new ComputeProperties(null, null, null, null, null));

    private static final Cycles TOTAL = Cycles.of(100);

    /** Time to recover {@code spent} cycles at a given remaining load, tier 1. */
    private long seconds(long spent, long remainingLoad) {
        return recovery.recoveryDuration(Cycles.of(spent), Cycles.of(remainingLoad), TOTAL, 1)
                .toSeconds();
    }

    @Nested
    @DisplayName("the trivial cases")
    class Trivial {

        @Test
        @DisplayName("spending nothing takes no time")
        void zeroSpentIsZero() {
            assertThat(recovery.recoveryDuration(Cycles.ZERO, Cycles.of(50), TOTAL, 1))
                    .isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("any real spend takes at least one second (a rig is not recovered until the last cycle is back)")
        void roundsUpToAtLeastOneSecond() {
            // One cycle on an idle rig recovers fast, but never instantly — truncating to 0 would hand
            // the cycle back a moment early.
            assertThat(recovery.recoveryDuration(Cycles.of(1), Cycles.ZERO, TOTAL, 1))
                    .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        }
    }

    @Nested
    @DisplayName("monotonic in load: a heavier rig never recovers faster (§1.3)")
    class MonotonicInLoad {

        @Test
        @DisplayName("recovering the same spend takes longer at higher remaining load")
        void higherLoadTakesLonger() {
            long atIdle = seconds(35, 0);
            long atHalf = seconds(35, 50);
            long atHeavy = seconds(35, 85);

            // Strictly increasing: overextension is punished by the physics of the rig.
            assertThat(atIdle).isLessThan(atHalf);
            assertThat(atHalf).isLessThan(atHeavy);
        }

        @Test
        @DisplayName("monotonic across the whole 0..max load range, never a local speed-up")
        void monotonicAcrossTheRange() {
            long previous = -1;
            for (int load = 0; load <= 94; load += 2) {
                long current = seconds(50, load);
                assertThat(current)
                        .as("recovery at load %d must be >= recovery at the lighter load", load)
                        .isGreaterThanOrEqualTo(previous);
                previous = current;
            }
        }
    }

    @Nested
    @DisplayName("superlinear near capacity: the penalty accelerates toward 100% (§1.3)")
    class Superlinear {

        @Test
        @DisplayName("equal steps in load produce ever-larger jumps in recovery time (convex)")
        void equalLoadStepsProduceGrowingDurationJumps() {
            // A large spend keeps whole-second rounding negligible against the shape.
            long spent = 20_000;
            long d0 = recovery.recoveryDuration(Cycles.of(spent), Cycles.of(0), TOTAL, 1)
                    .toSeconds();
            long d25 = recovery.recoveryDuration(Cycles.of(spent), Cycles.of(25), TOTAL, 1)
                    .toSeconds();
            long d50 = recovery.recoveryDuration(Cycles.of(spent), Cycles.of(50), TOTAL, 1)
                    .toSeconds();
            long d75 = recovery.recoveryDuration(Cycles.of(spent), Cycles.of(75), TOTAL, 1)
                    .toSeconds();

            long jumpLow = d25 - d0;
            long jumpMid = d50 - d25;
            long jumpHigh = d75 - d50;

            // Convexity is the whole point: a linear curve would make these jumps equal.
            assertThat(jumpMid).isGreaterThan(jumpLow);
            assertThat(jumpHigh).isGreaterThan(jumpMid);
        }

        @Test
        @DisplayName("recovery near the ceiling is dramatically slower than at half load, not merely a bit")
        void nearCeilingIsFarWorseThanHalfLoad() {
            // The design sketch anchors ~2x at 50% load and a much steeper penalty by 85%; assert the
            // qualitative gulf rather than the exact multiplier (the constants are [PROPOSAL]).
            long atHalf = seconds(35, 50);
            long atNearCeiling = seconds(35, 90);
            assertThat(atNearCeiling).isGreaterThan(atHalf * 3);
        }
    }

    @Nested
    @DisplayName("monotonic in thermal tier: a better rig never recovers slower")
    class MonotonicInTier {

        @Test
        @DisplayName("a higher thermal tier recovers the same spend at the same load no slower")
        void higherTierIsNoSlower() {
            long tier1 = recovery.recoveryDuration(Cycles.of(50), Cycles.of(60), TOTAL, 1)
                    .toSeconds();
            long tier2 = recovery.recoveryDuration(Cycles.of(50), Cycles.of(60), TOTAL, 2)
                    .toSeconds();
            long tier3 = recovery.recoveryDuration(Cycles.of(50), Cycles.of(60), TOTAL, 3)
                    .toSeconds();

            assertThat(tier2).isLessThan(tier1);
            assertThat(tier3).isLessThan(tier2);
        }
    }

    @Nested
    @DisplayName("the clamp: a fully-pinned or over-subscribed rig recovers slowly, never never")
    class Clamp {

        @Test
        @DisplayName("at full load recovery is finite (the (1-load)^k term is clamped below 1)")
        void fullLoadIsFiniteNotInfinite() {
            Duration atFull = recovery.recoveryDuration(Cycles.of(35), TOTAL, TOTAL, 1);
            assertThat(atFull).isPositive().isLessThan(Duration.ofDays(100).plusSeconds(1));
        }

        @Test
        @DisplayName("a parasite past the ceiling (Invariant I6) clamps to the same worst-case, not beyond")
        void overSubscribedLoadClampsAtTheCeiling() {
            // remainingLoad > total is real when a parasite over-subscribes the host. The load factor is
            // clamped, so 120% load recovers no slower than the clamp allows — and identically to 100%.
            long atCeiling =
                    recovery.recoveryDuration(Cycles.of(35), TOTAL, TOTAL, 1).toSeconds();
            long pastCeiling = recovery.recoveryDuration(Cycles.of(35), Cycles.of(120), TOTAL, 1)
                    .toSeconds();
            long farPastCeiling = recovery.recoveryDuration(Cycles.of(35), Cycles.of(10_000), TOTAL, 1)
                    .toSeconds();

            assertThat(pastCeiling).isEqualTo(atCeiling);
            assertThat(farPastCeiling).isEqualTo(atCeiling);
        }

        @Test
        @DisplayName("an extreme spend is capped at a finite ceiling rather than overflowing Duration")
        void extremeSpendIsCappedAtHundredDays() {
            // A pathological configuration must not overflow Duration.ofSeconds; the guard caps at 100 days.
            Duration capped = recovery.recoveryDuration(Cycles.of(1_000_000_000L), TOTAL, TOTAL, 1);
            assertThat(capped).isEqualTo(Duration.ofDays(100));
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        @DisplayName("recovery is undefined for a rig with no capacity")
        void zeroTotalRejected() {
            // Rig forbids a zero ceiling, but the strategy guards anyway rather than trusting an
            // invariant maintained elsewhere — the division below would otherwise be by zero.
            assertThatThrownBy(() -> recovery.recoveryDuration(Cycles.of(1), Cycles.ZERO, Cycles.ZERO, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a thermal tier below 1 is rejected")
        void tierBelowOneRejected() {
            assertThatThrownBy(() -> recovery.recoveryDuration(Cycles.of(1), Cycles.ZERO, TOTAL, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null quantities are rejected")
        void nullsRejected() {
            assertThatThrownBy(() -> recovery.recoveryDuration(null, Cycles.ZERO, TOTAL, 1))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> recovery.recoveryDuration(Cycles.of(1), null, TOTAL, 1))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> recovery.recoveryDuration(Cycles.of(1), Cycles.ZERO, null, 1))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the constructor requires the calibrated properties")
        void constructorRequiresProperties() {
            assertThatThrownBy(() -> new LoadFactorThermalRecovery(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
