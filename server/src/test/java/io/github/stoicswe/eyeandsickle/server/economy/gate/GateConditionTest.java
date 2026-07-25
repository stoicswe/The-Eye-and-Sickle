package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.EthecoinCost;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.HeatStateRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ProofOfSkillRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ReputationRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.SchematicRequirement;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The five gate conditions — each carries the parameters a gate needs, and each reports its own gate.
 *
 * <p>The validation cases defend the two rules a condition can enforce on its own, before any player is
 * involved: a reputation gate against {@link Faction#NONE} is a category error, and a heat threshold is
 * never negative. The rest of the rules (a split's structure, whether a player satisfies it) belong to
 * {@code GatedOffering} and {@code GateEvaluator} respectively.
 */
class GateConditionTest {

    @Nested
    @DisplayName("each variant reports the gate it belongs to, one-to-one")
    class GateMapping {

        @Test
        @DisplayName("the five conditions map to the five gates")
        void gatesMapOneToOne() {
            assertThat(new EthecoinCost(Ethecoin.ZERO).gate()).isEqualTo(UnlockGate.ETHECOIN);
            assertThat(new SchematicRequirement("topology-mapper").gate()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(new ReputationRequirement(Faction.SICKLE, 120).gate()).isEqualTo(UnlockGate.REPUTATION);
            assertThat(new ProofOfSkillRequirement(PuzzleClass.LOGIC, DifficultyTier.of(3)).gate())
                    .isEqualTo(UnlockGate.PROOF_OF_SKILL);
            assertThat(new HeatStateRequirement(HeatDirection.HOT_GATED, BigDecimal.TEN).gate())
                    .isEqualTo(UnlockGate.HEAT_STATE);
        }
    }

    @Nested
    @DisplayName("ethecoin cost")
    class EthecoinCostRules {

        @Test
        @DisplayName("a zero price is allowed — a vendor may give a sidegrade away and it is still EC-gated")
        void zeroPriceAllowed() {
            assertThatCode(() -> new EthecoinCost(Ethecoin.ZERO)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a null price is rejected")
        void nullPriceRejected() {
            assertThatThrownBy(() -> new EthecoinCost(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("schematic requirement")
    class SchematicRules {

        @Test
        @DisplayName("a blank schematic id is rejected")
        void blankIdRejected() {
            // A blank id would evaluate the schematic gate against nothing, which is a mis-specified
            // offering, not a satisfiable one.
            assertThatThrownBy(() -> new SchematicRequirement("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("a null schematic id is rejected")
        void nullIdRejected() {
            assertThatThrownBy(() -> new SchematicRequirement(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("reputation requirement")
    class ReputationRules {

        @Test
        @DisplayName("Faction.NONE is rejected — standing with nobody is a category error, not a threshold")
        void noneFactionRejected() {
            assertThatThrownBy(() -> new ReputationRequirement(Faction.NONE, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Faction.NONE");
        }

        @Test
        @DisplayName("a negative minimum standing is allowed — a contact deals with anyone not openly against them")
        void negativeThresholdAllowed() {
            assertThatCode(() -> new ReputationRequirement(Faction.EYE, -10)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a null faction is rejected")
        void nullFactionRejected() {
            assertThatThrownBy(() -> new ReputationRequirement(null, 5)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("proof-of-skill requirement")
    class ProofOfSkillRules {

        @Test
        @DisplayName("both the puzzle class and the tier are required")
        void nullsRejected() {
            assertThatThrownBy(() -> new ProofOfSkillRequirement(null, DifficultyTier.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ProofOfSkillRequirement(PuzzleClass.LOGIC, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the requirement stores a tier, not a count — the anti-farming rule made inexpressible")
        void storesATierNotACount() {
            // Invariant I7: because the parameter is a single tier, there is nowhere to put "solve it N
            // times". The type itself is what forbids count-gating.
            ProofOfSkillRequirement requirement = new ProofOfSkillRequirement(PuzzleClass.LOGIC, DifficultyTier.of(3));
            assertThat(requirement.minimumTier()).isEqualTo(DifficultyTier.of(3));
        }
    }

    @Nested
    @DisplayName("heat-state requirement")
    class HeatStateRules {

        @Test
        @DisplayName("a negative heat threshold is rejected — heat is never negative")
        void negativeThresholdRejected() {
            assertThatThrownBy(() -> new HeatStateRequirement(HeatDirection.COLD_GATED, new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("a zero threshold is allowed")
        void zeroThresholdAllowed() {
            assertThatCode(() -> new HeatStateRequirement(HeatDirection.HOT_GATED, BigDecimal.ZERO))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("both the direction and the threshold are required")
        void nullsRejected() {
            assertThatThrownBy(() -> new HeatStateRequirement(null, BigDecimal.ONE))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new HeatStateRequirement(HeatDirection.COLD_GATED, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
