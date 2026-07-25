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
 * A {@link GatedOffering} is the server-side definition of what a player must meet. Its constructor
 * delegates the split-structure rules to {@code protocol/game/GateRequirement}, so the tests here
 * confirm that delegation actually bites — in particular that the ceiling half of a split can never be
 * the secondary (Invariant I2) and that a "split" is never a gate paired with itself (Invariant I3).
 */
class GatedOfferingTest {

    private static final EthecoinCost EC = new EthecoinCost(Ethecoin.ofWholeEthecoin(25));
    private static final SchematicRequirement SCHEMATIC = new SchematicRequirement("relay-chain-framework");
    private static final ReputationRequirement REP = new ReputationRequirement(Faction.SICKLE, 120);

    @Nested
    @DisplayName("single-gate offerings")
    class Single {

        @Test
        @DisplayName("a single offering is not split and its requirement carries one gate")
        void singleShape() {
            GatedOffering offering = GatedOffering.single("fuzzer", EC);

            assertThat(offering.isSplit()).isFalse();
            assertThat(offering.secondary()).isNull();
            assertThat(offering.requirement().isSplit()).isFalse();
            assertThat(offering.requirement().primary()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("a blank offering id is rejected")
        void blankIdRejected() {
            assertThatThrownBy(() -> GatedOffering.single(" ", EC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("a null offering id or null primary is rejected")
        void nullsRejected() {
            assertThatThrownBy(() -> GatedOffering.single(null, EC)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> GatedOffering.single("x", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("sanctioned splits")
    class Splits {

        @Test
        @DisplayName("Relay Chain: schematic framework (ceiling) + per-session ethecoin hop cost")
        void relayChain() {
            GatedOffering offering = GatedOffering.split("relay-chain", SCHEMATIC, EC);

            assertThat(offering.isSplit()).isTrue();
            assertThat(offering.primary()).isEqualTo(SCHEMATIC);
            assertThat(offering.secondary()).isEqualTo(EC);
            // The ceiling component is the schematic, and it is the primary — never hidden behind a price.
            assertThat(offering.requirement().primary()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(offering.requirement().secondary()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("Zero-Day: hot-gated black-market access + a consumable ethecoin price")
        void zeroDay() {
            HeatStateRequirement hot = new HeatStateRequirement(HeatDirection.HOT_GATED, new BigDecimal("60"));
            GatedOffering offering =
                    GatedOffering.split("zero-day", hot, new EthecoinCost(Ethecoin.ofWholeEthecoin(400)));

            assertThat(offering.requirement().primary()).isEqualTo(UnlockGate.HEAT_STATE);
            assertThat(offering.requirement().secondary()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("a schematic secondary is refused — a ceiling can never hide behind a price (Invariant I2)")
        void schematicSecondaryRefused() {
            // Delegated to GateRequirement, which forbids SCHEMATIC as a secondary. This is the exact
            // shape I2 forbids: an ethecoin price with a permanent capability smuggled in behind it.
            assertThatThrownBy(() -> GatedOffering.split("smuggled-ceiling", EC, SCHEMATIC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SCHEMATIC");
        }

        @Test
        @DisplayName("a split that repeats the primary's gate is refused — that is a price, not a second gate")
        void duplicateGateRefused() {
            assertThatThrownBy(
                            () -> GatedOffering.split("double-ec", EC, new EthecoinCost(Ethecoin.ofWholeEthecoin(5))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("split() rejects a null secondary")
        void nullSecondaryRejected() {
            assertThatThrownBy(() -> GatedOffering.split("x", EC, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a proof-of-skill secondary on an ethecoin primary is allowed (left open by the docs)")
        void proofOfSkillSecondaryAllowed() {
            // GateRequirement deliberately does not forbid this; only SCHEMATIC-as-secondary and
            // primary==secondary are structural errors. The offering must not invent a stricter rule.
            ProofOfSkillRequirement proof = new ProofOfSkillRequirement(PuzzleClass.TIMING, DifficultyTier.of(2));
            assertThatCode(() -> GatedOffering.split("ec-plus-skill", EC, proof))
                    .doesNotThrowAnyException();
            assertThatCode(() -> GatedOffering.split("ec-plus-rep", EC, REP)).doesNotThrowAnyException();
        }
    }
}
