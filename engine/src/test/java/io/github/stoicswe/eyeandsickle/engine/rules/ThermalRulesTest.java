package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The recovery curve: quick in general, slower under load, and <b>bounded</b>.
 *
 * <p>The bound is the reason this class exists. The previous formulation was
 * {@code cycles ÷ (0.5 × (1 − load)² × budget)}, whose denominator approaches zero as the rig fills —
 * so a Thorough Scan's 35 cycles at 90% load took thirty-six minutes and nothing in the design said
 * that was wrong, because nothing in the design said where the ceiling was. Every assertion below is
 * about a number somebody chose.
 */
class ThermalRulesTest {

    private static final long RIG = 100L;

    private static long seconds(long cycles, double load) {
        return seconds(cycles, load, 0.0d);
    }

    private static long seconds(long cycles, double load, double theft) {
        return ThermalRules.recoveryTime(cycles, RIG, load, 1, theft).toSeconds();
    }

    @Nested
    @DisplayName("the ceiling")
    class Ceiling {

        @Test
        @DisplayName("nothing on a clean rig ever takes longer than five minutes")
        void cleanCap() {
            // Swept across the whole domain rather than probed at a corner: the old curve was fine
            // everywhere except the tail, and a spot check at 50% load would have passed on it.
            for (long cycles = 1; cycles <= RIG; cycles++) {
                for (int percent = 0; percent <= 100; percent++) {
                    assertThat(seconds(cycles, percent / 100.0d))
                            .as("%d cycles at %d%% load", cycles, percent)
                            .isLessThanOrEqualTo(Balance.THERMAL_MAX_CLEAN_SECONDS);
                }
            }
        }

        @Test
        @DisplayName("a rig being robbed may go past five minutes, and never past ten")
        void infestedCap() {
            for (int theft = 0; theft <= 100; theft++) {
                for (int percent = 0; percent <= 100; percent += 5) {
                    assertThat(seconds(RIG, percent / 100.0d, theft / 100.0d))
                            .as("full return at %d%% load with %d%% stolen", percent, theft)
                            .isLessThanOrEqualTo(Balance.THERMAL_MAX_INFESTED_SECONDS);
                }
            }
            // And the extra headroom is reachable, or the ten-minute figure would be decoration.
            assertThat(seconds(RIG, 1.0d, 1.0d)).isGreaterThan(Balance.THERMAL_MAX_CLEAN_SECONDS);
        }

        @Test
        @DisplayName("the published ceiling moves only with theft")
        void ceilingSeconds() {
            assertThat(ThermalRules.ceilingSeconds(0.0d)).isEqualTo(Balance.THERMAL_MAX_CLEAN_SECONDS);
            assertThat(ThermalRules.ceilingSeconds(1.0d)).isEqualTo(Balance.THERMAL_MAX_INFESTED_SECONDS);
            assertThat(ThermalRules.ceilingSeconds(0.5d))
                    .isBetween(Balance.THERMAL_MAX_CLEAN_SECONDS, Balance.THERMAL_MAX_INFESTED_SECONDS);
            // Out-of-range inputs clamp rather than extrapolate — a malformed save must not be able
            // to invent an eleven-minute recovery.
            assertThat(ThermalRules.ceilingSeconds(-3.0d)).isEqualTo(Balance.THERMAL_MAX_CLEAN_SECONDS);
            assertThat(ThermalRules.ceilingSeconds(9.0d)).isEqualTo(Balance.THERMAL_MAX_INFESTED_SECONDS);
        }
    }

    @Nested
    @DisplayName("the shape")
    class Shape {

        @Test
        @DisplayName("recovery is slower the closer the rig sits to capacity — all the way up")
        void monotonicInLoad() {
            // docs/design/01 §1.3's actual commitment. Asserted as strictly increasing rather than
            // non-decreasing so a naive min(time, 300) clip — which would flatten the top of the
            // range into a plateau where 80% and 95% feel identical — fails here.
            long previous = -1;
            for (int percent = 0; percent <= 95; percent += 5) {
                long now = seconds(35, percent / 100.0d);
                assertThat(now).as("35 cycles at %d%% load", percent).isGreaterThan(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("returning more takes longer")
        void monotonicInSize() {
            long previous = -1;
            for (long cycles = 1; cycles <= RIG; cycles += 7) {
                long now = seconds(cycles, 0.5d);
                assertThat(now).as("%d cycles", cycles).isGreaterThanOrEqualTo(previous);
                previous = now;
            }
        }

        @Test
        @DisplayName("an idle rig still charges something — recovery is a commitment, not a toll")
        void neverFree() {
            assertThat(seconds(35, 0.0d)).isGreaterThanOrEqualTo(Balance.THERMAL_MIN_SECONDS);
            assertThat(seconds(1, 0.0d)).isGreaterThanOrEqualTo(Balance.THERMAL_MIN_SECONDS);
        }

        @Test
        @DisplayName("the cases that used to be pathological are now minutes at worst")
        void theOldTail() {
            // Regression figures, measured against the old curve: 35 cycles at 90% load was 2160s
            // and two cycles at 82% was 123s. Over-committing should be felt, not benched.
            assertThat(seconds(35, 0.90d)).isLessThan(240L);
            assertThat(seconds(2, 0.82d)).isLessThan(60L);
        }

        @Test
        @DisplayName("a better Thermal Budget recovers faster")
        void thermalBudgetHelps() {
            long base = ThermalRules.recoveryTime(35, RIG, 0.6d, 1, 0).toSeconds();
            long better = ThermalRules.recoveryTime(35, RIG, 0.6d, 3, 0).toSeconds();
            assertThat(better).isLessThan(base);
        }

        @Test
        @DisplayName("theft slows recovery on top of the load it already causes")
        void theftIsASecondEffect() {
            // Same load, same size, different theft — so this isolates the thermal half from the
            // ordinary "a parasite holds cycles so the rig is busier" effect, which is already
            // carried by loadFactor and needs no special case.
            assertThat(seconds(35, 0.6d, 0.4d)).isGreaterThan(seconds(35, 0.6d, 0.0d));
        }
    }

    @Nested
    @DisplayName("malformed input cannot hang a save")
    class Degenerate {

        @Test
        @DisplayName("nothing to recover is no wait at all")
        void nothing() {
            assertThat(ThermalRules.recoveryTime(0, RIG, 0.5d, 1, 0)).isEqualTo(Duration.ZERO);
            assertThat(ThermalRules.recoveryTime(-5, RIG, 0.5d, 1, 0)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("a rig with no stated capacity, a load past 1, or a NaN still terminates")
        void hostile() {
            assertThat(ThermalRules.recoveryTime(35, 0, 0.5d, 1, 0).toSeconds())
                    .isBetween(Balance.THERMAL_MIN_SECONDS, Balance.THERMAL_MAX_CLEAN_SECONDS);
            assertThat(ThermalRules.recoveryTime(35, RIG, 4.0d, 1, 0).toSeconds())
                    .isLessThanOrEqualTo(Balance.THERMAL_MAX_CLEAN_SECONDS);
            assertThat(ThermalRules.recoveryTime(35, RIG, Double.NaN, 1, 0).toSeconds())
                    .isBetween(Balance.THERMAL_MIN_SECONDS, Balance.THERMAL_MAX_CLEAN_SECONDS);
            assertThat(ThermalRules.recoveryTime(35, RIG, 0.5d, 0, 0).toSeconds())
                    .as("a zero thermal budget must not divide by zero")
                    .isPositive();
        }
    }
}
