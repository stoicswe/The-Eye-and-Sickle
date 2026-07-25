package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two-factor sampling weight — {@code docs/architecture/05-validator-quorum.md} §2.2:
 * {@code weight = reputation × uptime}, both captured at sampling time and both bounded to
 * {@code [0, 1]}.
 */
class SampledValidatorTest {

    private static final String DID = "did:plc:validator1";

    @Nested
    @DisplayName("the sampling weight")
    class Weight {

        @Test
        @DisplayName("is reputation times uptime")
        void isProduct() {
            assertThat(SampledValidator.of(DID, 0.8, 0.5).weight()).isCloseTo(0.40, within(1e-12));
            assertThat(SampledValidator.of(DID, 1.0, 1.0).weight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("is zero when either factor is zero, so the sampler skips it")
        void isZeroWhenAFactorIsZero() {
            // A validator with no reputation OR no uptime is unsamplable — the property AResSampler
            // relies on to exclude it.
            assertThat(SampledValidator.of(DID, 0.0, 1.0).weight()).isZero();
            assertThat(SampledValidator.of(DID, 0.9, 0.0).weight()).isZero();
        }
    }

    @Nested
    @DisplayName("range validation")
    class Range {

        @Test
        @DisplayName("accepts both factors at the closed bounds")
        void acceptsBounds() {
            assertThat(SampledValidator.of(DID, 0.0, 0.0).weight()).isZero();
            assertThat(SampledValidator.of(DID, 1.0, 1.0).weight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("rejects a reputation outside [0, 1]")
        void rejectsReputationOutOfRange() {
            // Out-of-range reputation is a corrupt candidate; it must never reach the weighted draw.
            assertThatThrownBy(() -> SampledValidator.of(DID, -0.01, 1.0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SampledValidator.of(DID, 1.01, 1.0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects an uptime outside [0, 1]")
        void rejectsUptimeOutOfRange() {
            assertThatThrownBy(() -> SampledValidator.of(DID, 1.0, -0.5)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SampledValidator.of(DID, 1.0, 2.0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects NaN in either factor, which would make a key sort unpredictably")
        void rejectsNaN() {
            // A NaN factor makes weight NaN and a NaN A-Res key sorts nondeterministically — the last
            // thing a sampling draw may do.
            assertThatThrownBy(() -> SampledValidator.of(DID, Double.NaN, 1.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SampledValidator.of(DID, 1.0, Double.NaN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a null DID")
        void rejectsNullDid() {
            assertThatThrownBy(() -> SampledValidator.of(null, 0.5, 0.5)).isInstanceOf(NullPointerException.class);
        }
    }
}
