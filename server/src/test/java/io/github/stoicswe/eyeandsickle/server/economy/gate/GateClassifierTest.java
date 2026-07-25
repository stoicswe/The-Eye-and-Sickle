package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The gate-assignment decision procedure of {@code docs/design/02-unlock-gates.md} §1.1.
 *
 * <p>The single-fact cases prove each question maps to its gate; the multi-fact cases are the ones that
 * matter, because they are where "ask in order, first yes wins" enforces Invariants I2 (a ceiling never
 * classifies as ethecoin) and I3 (exactly one gate) by control flow rather than by policy.
 */
class GateClassifierTest {

    private final GateClassifier classifier = new GateClassifier();

    /** All five booleans false — the "does not classify cleanly" case. */
    private static OfferingFacts facts(
            boolean ceiling, boolean automates, boolean distorting, boolean sidegrade, boolean vendor) {
        return new OfferingFacts(ceiling, automates, distorting, sidegrade, vendor);
    }

    @Nested
    @DisplayName("each question, asked alone, maps to its gate")
    class SingleFact {

        @Test
        @DisplayName("raises a permanent ceiling -> SCHEMATIC (question 1)")
        void ceilingIsSchematic() {
            assertThat(classifier.classify(facts(true, false, false, false, false)))
                    .isEqualTo(UnlockGate.SCHEMATIC);
        }

        @Test
        @DisplayName("automates or skips a puzzle -> PROOF_OF_SKILL (question 2)")
        void automationIsProofOfSkill() {
            assertThat(classifier.classify(facts(false, true, false, false, false)))
                    .isEqualTo(UnlockGate.PROOF_OF_SKILL);
        }

        @Test
        @DisplayName("economy-distorting if free -> REPUTATION (question 3)")
        void distortingIsReputation() {
            assertThat(classifier.classify(facts(false, false, true, false, false)))
                    .isEqualTo(UnlockGate.REPUTATION);
        }

        @Test
        @DisplayName("consumable / replaceable / sidegrade -> ETHECOIN (question 4)")
        void sidegradeIsEthecoin() {
            assertThat(classifier.classify(facts(false, false, false, true, false)))
                    .isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("a vendor / contact / market -> HEAT_STATE (question 5)")
        void vendorIsHeatState() {
            assertThat(classifier.classify(facts(false, false, false, false, true)))
                    .isEqualTo(UnlockGate.HEAT_STATE);
        }
    }

    @Nested
    @DisplayName("ordering enforces the invariants for free")
    class Ordering {

        @Test
        @DisplayName("a ceiling that is ALSO a sidegrade is a schematic, never ethecoin (Invariant I2)")
        void ceilingBeatsEthecoin() {
            // This is the whole point of asking the ceiling question first: there is no control-flow
            // path that returns ETHECOIN for a ceiling-raising offering, so money cannot buy a ceiling.
            assertThat(classifier.classify(facts(true, false, false, true, false)))
                    .isEqualTo(UnlockGate.SCHEMATIC);
        }

        @Test
        @DisplayName("a ceiling that also automates a puzzle stays a schematic (question 1 wins)")
        void ceilingBeatsProofOfSkill() {
            assertThat(classifier.classify(facts(true, true, false, false, false)))
                    .isEqualTo(UnlockGate.SCHEMATIC);
        }

        @Test
        @DisplayName("automation outranks the economy-distortion and sidegrade questions")
        void automationBeatsLaterQuestions() {
            assertThat(classifier.classify(facts(false, true, true, true, true)))
                    .isEqualTo(UnlockGate.PROOF_OF_SKILL);
        }

        @Test
        @DisplayName("reputation outranks the ethecoin and heat questions")
        void reputationBeatsLaterQuestions() {
            assertThat(classifier.classify(facts(false, false, true, true, true)))
                    .isEqualTo(UnlockGate.REPUTATION);
        }

        @Test
        @DisplayName("ethecoin outranks heat state (a sellable sidegrade is not a vendor)")
        void ethecoinBeatsHeat() {
            assertThat(classifier.classify(facts(false, false, false, true, true)))
                    .isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("every fact true classifies as SCHEMATIC — exactly one gate, the first (Invariant I3)")
        void allTrueYieldsExactlyTheFirst() {
            assertThat(classifier.classify(facts(true, true, true, true, true))).isEqualTo(UnlockGate.SCHEMATIC);
        }
    }

    @Nested
    @DisplayName("an offering that classifies as nothing is surfaced, not defaulted")
    class NoClean {

        @Test
        @DisplayName("all five questions 'no' throws rather than inventing a default gate")
        void noYesThrows() {
            // §1.1: an item that answers no to all five is probably badly designed. A silent default is
            // exactly how such an item slips in gated by whatever the reading code checked first.
            assertThatThrownBy(() -> classifier.classify(facts(false, false, false, false, false)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not classify cleanly")
                    .hasMessageContaining("02-unlock-gates.md");
        }

        @Test
        @DisplayName("null facts are rejected")
        void nullFactsRejected() {
            assertThatThrownBy(() -> classifier.classify(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
