package io.github.stoicswe.eyeandsickle.server.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The vault capacity schedule — the one calibrated number set the economy slice owns. The established
 * half of {@code docs/design/01-core-resources.md} §6 is the <em>shape</em>: vault capacity scales
 * <strong>sub-linearly</strong> and is never a function of ethecoin (Invariant I12). These tests defend
 * that shape against a misconfiguration that would sneak super-linear growth — the late-game unraidable
 * veteran — back in.
 */
class EconomyPropertiesTest {

    /** The proposal defaults, obtained by leaving every field null. */
    private static EconomyProperties defaults() {
        return new EconomyProperties(null, null, null, null, null);
    }

    @Nested
    @DisplayName("the proposal defaults")
    class Defaults {

        @Test
        @DisplayName("null fields fall back to the §6 first-pass figures")
        void nullsBecomeDefaults() {
            EconomyProperties properties = defaults();
            assertThat(properties.vaultBaseSlots()).isEqualTo(6);
            assertThat(properties.vaultExpansionIncrements()).containsExactly(4, 3, 2, 1);
            assertThat(properties.vaultHardCapSlots()).isEqualTo(16);
            assertThat(properties.standardStorageSlots()).isEqualTo(20);
            assertThat(properties.highHackableZoneSlots()).isEqualTo(60);
        }

        @Test
        @DisplayName("an empty increment list is treated as absent and falls back to the default schedule")
        void emptyIncrementsBecomeDefault() {
            EconomyProperties properties = new EconomyProperties(6, List.of(), 16, 20, 60);
            assertThat(properties.vaultExpansionIncrements()).containsExactly(4, 3, 2, 1);
        }
    }

    @Nested
    @DisplayName("vault capacity is sub-linear (Invariant I12)")
    class SubLinear {

        @Test
        @DisplayName("each expansion level adds a smaller increment, and level 0 is the bare base")
        void increasesByDecreasingSteps() {
            EconomyProperties properties = defaults();
            assertThat(properties.vaultSlots(0)).isEqualTo(6);
            assertThat(properties.vaultSlots(1)).isEqualTo(10); // +4
            assertThat(properties.vaultSlots(2)).isEqualTo(13); // +3
            assertThat(properties.vaultSlots(3)).isEqualTo(15); // +2
            assertThat(properties.vaultSlots(4)).isEqualTo(16); // +1

            // The deltas are strictly non-increasing — that is what "sub-linear" means here, and it is
            // the guard against a rig that just keeps getting safer.
            int[] deltas = {
                properties.vaultSlots(1) - properties.vaultSlots(0),
                properties.vaultSlots(2) - properties.vaultSlots(1),
                properties.vaultSlots(3) - properties.vaultSlots(2),
                properties.vaultSlots(4) - properties.vaultSlots(3),
            };
            assertThat(deltas).containsExactly(4, 3, 2, 1);
        }

        @Test
        @DisplayName("levels beyond the schedule add nothing, and the hard cap is never exceeded")
        void scheduleExhaustsAndCapHolds() {
            EconomyProperties properties = defaults();
            assertThat(properties.vaultSlots(5)).isEqualTo(16); // schedule exhausted, no more growth
            assertThat(properties.vaultSlots(50)).isEqualTo(16);
            assertThat(properties.vaultSlots(10_000)).isEqualTo(16); // the cap is a backstop against runaway config
        }

        @Test
        @DisplayName("a lower hard cap clamps the schedule below its natural total")
        void hardCapClamps() {
            EconomyProperties properties = new EconomyProperties(6, List.of(4, 3, 2, 1), 12, 20, 60);
            assertThat(properties.vaultSlots(2)).isEqualTo(12); // 6+4+3=13 clamped to 12
            assertThat(properties.vaultSlots(4)).isEqualTo(12);
        }

        @Test
        @DisplayName(
                "capacity is a pure function of the schematic-derived level — the same level always yields the same slots")
        void capacityIsDeterministicInLevelAlone() {
            // Invariant I12: the only input is the Cold Storage Expansion level, never ethecoin. There is
            // no parameter here that a balance could vary, and the result is monotonic and repeatable.
            EconomyProperties properties = defaults();
            for (int level = 0; level < 8; level++) {
                assertThat(properties.vaultSlots(level)).isEqualTo(properties.vaultSlots(level));
                if (level > 0) {
                    assertThat(properties.vaultSlots(level))
                            .as("never shrinks as expansion grows")
                            .isGreaterThanOrEqualTo(properties.vaultSlots(level - 1));
                }
            }
        }

        @Test
        @DisplayName("a negative expansion level is rejected")
        void negativeLevelRejected() {
            assertThatThrownBy(() -> defaults().vaultSlots(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }
    }

    @Nested
    @DisplayName("slotsFor dispatches per tier")
    class SlotsFor {

        @Test
        @DisplayName("only the vault scales with expansion; the exposed tiers are flat")
        void onlyVaultScales() {
            EconomyProperties properties = defaults();
            assertThat(properties.slotsFor(StorageTier.VAULT, 0)).isEqualTo(6);
            assertThat(properties.slotsFor(StorageTier.VAULT, 2)).isEqualTo(13);
            assertThat(properties.slotsFor(StorageTier.STANDARD_STORAGE, 99)).isEqualTo(20);
            assertThat(properties.slotsFor(StorageTier.HIGH_HACKABLE_ZONE, 99)).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("misconfiguration is rejected at construction")
    class Validation {

        @Test
        @DisplayName("an increasing increment schedule is super-linear growth and is refused (Invariant I12)")
        void increasingScheduleRejected() {
            assertThatThrownBy(() -> new EconomyProperties(6, List.of(1, 2), 16, 20, 60))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invariant I12");
        }

        @Test
        @DisplayName("a negative increment is refused")
        void negativeIncrementRejected() {
            assertThatThrownBy(() -> new EconomyProperties(6, List.of(4, -1), 16, 20, 60))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("a non-positive tier capacity is refused — a zero-slot tier silently swallows items")
        void zeroTierRejected() {
            assertThatThrownBy(() -> new EconomyProperties(0, null, 16, 20, 60))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EconomyProperties(6, null, 16, 0, 60))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EconomyProperties(6, null, 16, 20, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a hard cap below the base capacity is refused")
        void hardCapBelowBaseRejected() {
            assertThatThrownBy(() -> new EconomyProperties(6, List.of(1), 5, 20, 60))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hard cap");
        }
    }
}
