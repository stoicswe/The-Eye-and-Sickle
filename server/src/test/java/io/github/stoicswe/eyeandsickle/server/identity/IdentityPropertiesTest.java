package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.DevSignin;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.Operator;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Identity/session/operator configuration. Every knob here is operational or explicitly undecided; the
 * one balance-adjacent value — the faction-abandonment heat spike — deliberately has no default, because
 * a heat magnitude is a balance value this slice must not invent.
 */
class IdentityPropertiesTest {

    private static IdentityProperties with(Duration ttl, BigDecimal spike) {
        return new IdentityProperties(new Operator(null, null, null), new DevSignin(false), ttl, spike);
    }

    @Nested
    @DisplayName("session TTL")
    class SessionTtl {

        @Test
        @DisplayName("a positive duration is accepted and returned")
        void positiveAccepted() {
            assertThat(with(Duration.ofHours(12), null).sessionTtl()).isEqualTo(Duration.ofHours(12));
        }

        @Test
        @DisplayName("null, zero or negative TTL is refused")
        void nonPositiveRejected() {
            // A zero-length session would authenticate nobody; a negative one is nonsense. Fail at binding,
            // not in the sign-in path.
            assertThatThrownBy(() -> with(null, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> with(Duration.ZERO, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> with(Duration.ofSeconds(-1), null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("faction abandonment heat spike")
    class HeatSpike {

        @Test
        @DisplayName("is null when unconfigured — there is deliberately no default")
        void nullByDefault() {
            // The design says abandonment "spikes heat" but never by how much; inventing a default here
            // would promote a [PROPOSAL] to a decision in config.
            assertThat(with(Duration.ofHours(1), null).factionAbandonmentHeatSpike())
                    .isNull();
        }

        @Test
        @DisplayName("a configured non-negative magnitude is accepted")
        void configuredAccepted() {
            assertThat(with(Duration.ofHours(1), new BigDecimal("12.5")).factionAbandonmentHeatSpike())
                    .isEqualByComparingTo("12.5");
        }

        @Test
        @DisplayName("a negative magnitude is refused — a spike is a magnitude, not a direction")
        void negativeRejected() {
            assertThatThrownBy(() -> with(Duration.ofHours(1), new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("operator credentials")
    class OperatorCredentials {

        @Test
        @DisplayName("configured only when both username and password are present and non-blank")
        void isConfigured() {
            // Locked by default, the same posture as the allowlist: no credential means the operator
            // endpoints admit no one.
            assertThat(new Operator(null, null, null).isConfigured()).isFalse();
            assertThat(new Operator("op", null, null).isConfigured()).isFalse();
            assertThat(new Operator("op", "  ", null).isConfigured()).isFalse();
            assertThat(new Operator("op", "secret", null).isConfigured()).isTrue();
        }

        @Test
        @DisplayName("the attribution DID is validated, and blank is treated as unset")
        void parsedDid() {
            assertThat(new Operator("op", "secret", null).parsedDid()).isNull();
            assertThat(new Operator("op", "secret", "   ").parsedDid()).isNull();
            assertThat(new Operator("op", "secret", "did:plc:aaaaaaaaaaaa").parsedDid())
                    .isEqualTo(Did.of("did:plc:aaaaaaaaaaaa"));
        }

        @Test
        @DisplayName("a malformed attribution DID fails loudly")
        void malformedDidRejected() {
            assertThatThrownBy(() -> new Operator("op", "secret", "nope").parsedDid())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("development sign-in is a plain flag")
    void devSignin() {
        assertThatCode(() -> new DevSignin(true)).doesNotThrowAnyException();
        assertThat(new DevSignin(true).enabled()).isTrue();
        assertThat(new DevSignin(false).enabled()).isFalse();
    }
}
