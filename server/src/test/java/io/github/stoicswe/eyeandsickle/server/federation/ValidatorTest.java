package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code validators} row record. Its one piece of logic is the projection to a sampling
 * candidate; the rest is a null-guarded value carrier whose reputation and uptime are {@link
 * BigDecimal} to match the schema's authoritative {@code numeric(9,8)}.
 */
class ValidatorTest {

    private static final Instant ENROLLED = Instant.parse("2026-07-24T00:00:00Z");

    private static Validator validator(BigDecimal reputation, BigDecimal uptime) {
        return new Validator("did:plc:validator1", reputation, uptime, true, ENROLLED, null, null, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("projects to a sampling candidate carrying reputation and uptime as its weight factors")
    void toSamplingCandidate() {
        Validator validator = validator(new BigDecimal("0.80000000"), new BigDecimal("0.50000000"));

        SampledValidator candidate = validator.toSamplingCandidate();

        assertThat(candidate.validatorDid()).isEqualTo("did:plc:validator1");
        assertThat(candidate.reputation()).isCloseTo(0.80, within(1e-9));
        assertThat(candidate.uptime()).isCloseTo(0.50, within(1e-9));
        assertThat(candidate.weight()).isCloseTo(0.40, within(1e-9));
    }

    @Test
    @DisplayName("rejects the null fields the schema declares NOT NULL")
    void rejectsNulls() {
        BigDecimal half = new BigDecimal("0.50000000");
        assertThatThrownBy(() -> new Validator(null, half, half, true, ENROLLED, null, null, 0, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Validator("did:plc:v1", null, half, true, ENROLLED, null, null, 0, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Validator("did:plc:v1", half, null, true, ENROLLED, null, null, 0, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Validator("did:plc:v1", half, half, true, null, null, null, 0, 0, 0, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
