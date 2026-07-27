package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The schematic-material gate — {@code docs/design/02-unlock-gates.md} §2.2, {@code
 * docs/design/10-botnets.md} §1a, Invariant I13.
 *
 * <p>Every test here is about the guard rather than the rate. The rate is a playtest figure; the
 * guard is what makes any rate safe, and it is the thing that would be quietly wrong in a way nobody
 * noticed until a player had ground it.
 */
class SalvageRulesTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");

    private static ResolutionState record(String outcome, String liveOrDormant, int tier) {
        ResolutionState record = new ResolutionState();
        record.outcome = outcome;
        record.liveOrDormant = liveOrDormant;
        record.difficultyTier = tier;
        record.at = T0;
        return record;
    }

    private static SoloSave save() {
        return SoloGame.newCharacter("operator", T0);
    }

    @Nested
    @DisplayName("the tier gate (Invariant I13)")
    class TierGate {

        @Test
        @DisplayName("a successful breach of a live target at or above the threshold pays")
        void qualifyingBreachPays() {
            SoloSave save = save();

            assertThat(SalvageRules.award(save, record("BREACHED", "LIVE", Balance.SCHEMATIC_MATERIAL_MIN_TIER)))
                    .isEqualTo(Balance.SCHEMATIC_MATERIAL_PER_BREACH);
            assertThat(save.schematicMaterial).isEqualTo(Balance.SCHEMATIC_MATERIAL_PER_BREACH);
        }

        @Test
        @DisplayName("below the threshold pays nothing, however many times you do it")
        void lowTiersNeverPay() {
            SoloSave save = save();
            for (int i = 0; i < 50; i++) {
                assertThat(SalvageRules.award(save, record("BREACHED", "LIVE", 1))).isZero();
                assertThat(SalvageRules.award(save, record("BREACHED", "LIVE", 2))).isZero();
            }
            // design/10 §1a's failure in a different costume: farm the softest target you can reach.
            // The tier gate sets pace, never reach — so fifty easy wins are worth exactly nothing.
            assertThat(save.schematicMaterial).isZero();
        }

        @Test
        @DisplayName("a dormant target is worth loot and never worth an unlock")
        void dormantTargetsNeverPay() {
            SoloSave save = save();
            assertThat(SalvageRules.award(save, record("BREACHED", "DORMANT", 5))).isZero();
            assertThat(save.schematicMaterial).isZero();
        }

        @Test
        @DisplayName("only a success pays")
        void failuresAndAbortsNeverPay() {
            SoloSave save = save();
            assertThat(SalvageRules.award(save, record("FAILED", "LIVE", 5))).isZero();
            assertThat(SalvageRules.award(save, record("ABORTED", "LIVE", 5))).isZero();
            assertThat(save.schematicMaterial).isZero();
        }

        @Test
        @DisplayName("a null record is a no-op rather than a crash")
        void nullIsSafe() {
            SoloSave save = save();
            assertThat(SalvageRules.award(save, null)).isZero();
        }
    }

    @Nested
    @DisplayName("the conversion rate (02 §2.2, closing OQ-5)")
    class Rate {

        @Test
        @DisplayName("an unlock costs twelve qualifying breaches, and the shortfall is reported")
        void unlockCostIsReachable() {
            SoloSave save = save();
            assertThat(SalvageRules.unlockCost()).isEqualTo(Balance.SCHEMATIC_MATERIAL_PER_UNLOCK);
            assertThat(SalvageRules.remainingForUnlock(save)).isEqualTo(SalvageRules.unlockCost());

            for (int i = 0; i < SalvageRules.unlockCost(); i++) {
                SalvageRules.award(save, record("BREACHED", "LIVE", 4));
            }
            assertThat(save.schematicMaterial).isEqualTo(SalvageRules.unlockCost());
            assertThat(SalvageRules.remainingForUnlock(save)).isZero();
        }

        @Test
        @DisplayName("the shortfall never goes negative")
        void overshootIsClamped() {
            SoloSave save = save();
            save.schematicMaterial = SalvageRules.unlockCost() * 3;
            assertThat(SalvageRules.remainingForUnlock(save)).isZero();
        }
    }

    @Test
    @DisplayName("⚠ the gate reads one record's tier and never counts the resolution list")
    void nothingIsCountGated() {
        SoloSave save = save();
        // Fifty rows in the history, all below the threshold. If anything here were count-gated,
        // this would pay — and ResolutionRecord's javadoc calls a count over these rows "the exploit
        // arriving" (Invariant I7, design/02 §2.4).
        for (int i = 0; i < 50; i++) {
            ResolutionState row = record("BREACHED", "LIVE", 1);
            save.resolutions.add(row);
            SalvageRules.award(save, row);
        }
        assertThat(save.resolutions).hasSize(50);
        assertThat(save.schematicMaterial).isZero();

        // One hard win pays what fifty easy ones did not.
        ResolutionState real = record("BREACHED", "LIVE", 5);
        save.resolutions.add(real);
        assertThat(SalvageRules.award(save, real)).isPositive();
    }
}
