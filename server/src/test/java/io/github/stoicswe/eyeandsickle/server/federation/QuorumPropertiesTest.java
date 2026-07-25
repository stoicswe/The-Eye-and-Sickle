package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validation of the tunable quorum coefficients — {@code docs/architecture/05-validator-quorum.md} §6.
 *
 * <p>These are "recommended starting points", not invariants, but a mis-set one is a silent behaviour
 * change: a zeroed alpha means trust never builds, a floor above the newcomer reputation rewards
 * getting caught. Boxed nullable parameters let "unset" fall to the documented default, while an
 * explicit out-of-range value is refused loudly at startup rather than corrupting the reputation math.
 */
class QuorumPropertiesTest {

    /** Builds properties with every value explicit, so a test can vary exactly one. */
    private static QuorumProperties props(
            Integer committee, Double alpha, Double beta, Double gamma, Double floor, Double newcomer) {
        return new QuorumProperties(committee, alpha, beta, gamma, floor, newcomer);
    }

    private static QuorumProperties valid() {
        return props(7, 0.05, 0.25, 0.10, 0.10, 0.40);
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("fall back to the documented §6 starting points when every value is unset")
        void allNullYieldsDocumentedDefaults() {
            QuorumProperties defaults = props(null, null, null, null, null, null);

            assertThat(defaults.committeeSize())
                    .isEqualTo(QuorumProperties.DEFAULT_COMMITTEE_SIZE)
                    .isEqualTo(7);
            assertThat(defaults.reputationIncreaseAlpha())
                    .isEqualTo(QuorumProperties.DEFAULT_ALPHA)
                    .isEqualTo(0.05);
            assertThat(defaults.reputationDecreaseBeta())
                    .isEqualTo(QuorumProperties.DEFAULT_BETA)
                    .isEqualTo(0.25);
            assertThat(defaults.uptimeDecayGamma())
                    .isEqualTo(QuorumProperties.DEFAULT_GAMMA)
                    .isEqualTo(0.10);
            assertThat(defaults.equivocationFloor())
                    .isEqualTo(QuorumProperties.DEFAULT_EQUIVOCATION_FLOOR)
                    .isEqualTo(0.10);
            assertThat(defaults.newcomerReputation())
                    .isEqualTo(QuorumProperties.DEFAULT_NEWCOMER_REPUTATION)
                    .isEqualTo(0.40);
        }

        @Test
        @DisplayName("a single unset value falls back without disturbing the others")
        void partialNulls() {
            QuorumProperties p = props(5, null, 0.30, null, null, 0.50);
            assertThat(p.committeeSize()).isEqualTo(5);
            assertThat(p.reputationIncreaseAlpha()).isEqualTo(QuorumProperties.DEFAULT_ALPHA);
            assertThat(p.reputationDecreaseBeta()).isEqualTo(0.30);
            assertThat(p.newcomerReputation()).isEqualTo(0.50);
        }
    }

    @Nested
    @DisplayName("committee size")
    class CommitteeSize {

        @Test
        @DisplayName("rejects a committee smaller than one")
        void rejectsBelowOne() {
            // A committee of zero adjudicates nothing; even the degenerate lower bound is 1.
            assertThatThrownBy(() -> props(0, 0.05, 0.25, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("open-unit-interval coefficients (0, 1)")
    class OpenUnit {

        @Test
        @DisplayName("reject alpha at either closed bound")
        void rejectsAlphaBounds() {
            // alpha = 1 jumps straight to full trust on one vote; alpha = 0 never recovers.
            assertThatThrownBy(() -> props(7, 0.0, 0.25, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> props(7, 1.0, 0.25, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reject beta and gamma at either closed bound")
        void rejectsBetaGammaBounds() {
            assertThatThrownBy(() -> props(7, 0.05, 0.0, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> props(7, 0.05, 1.0, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.0, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 1.0, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reject NaN, which would poison every later reputation step")
        void rejectsNaN() {
            assertThatThrownBy(() -> props(7, Double.NaN, 0.25, 0.10, 0.10, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("the equivocation floor [0, 1)")
    class Floor {

        @Test
        @DisplayName("accepts zero but rejects one")
        void boundaries() {
            // floor = 0 is a legitimate hardest slash; floor = 1 would leave a caught equivocator at
            // full trust.
            assertThatCode(() -> props(7, 0.05, 0.25, 0.10, 0.0, 0.40)).doesNotThrowAnyException();
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.10, 1.0, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a negative floor")
        void rejectsNegative() {
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.10, -0.01, 0.40))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("the newcomer reputation (0, 1]")
    class Newcomer {

        @Test
        @DisplayName("rejects zero — a zero floor deadlocks the pool (§2.5)")
        void rejectsZero() {
            // A non-positive floor gives a fresh validator zero weight, so it is never sampled and can
            // never earn a record: the cold-start deadlock the floor exists to prevent.
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.10, 0.10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts one but rejects above one")
        void upperBound() {
            assertThatCode(() -> props(7, 0.05, 0.25, 0.10, 0.10, 1.0)).doesNotThrowAnyException();
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.10, 0.10, 1.01))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("must not sit below the equivocation floor — a fresh validator outranking a slashed one")
        void mustNotBeBelowFloor() {
            // newcomer 0.05 < floor 0.10: a just-enrolled validator would rank a caught equivocator's
            // equal, rewarding the equivocation.
            assertThatThrownBy(() -> props(7, 0.05, 0.25, 0.10, 0.10, 0.05))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepts newcomer equal to the floor")
        void equalToFloorIsAllowed() {
            assertThatCode(() -> props(7, 0.05, 0.25, 0.10, 0.10, 0.10)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("a fully valid configuration constructs")
    void validConstructs() {
        assertThatCode(QuorumPropertiesTest::valid).doesNotThrowAnyException();
    }
}
