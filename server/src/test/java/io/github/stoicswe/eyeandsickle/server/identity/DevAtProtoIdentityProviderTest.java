package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.DevSignin;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.Operator;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The development identity provider, which trusts a claimed DID with no real authentication. It is
 * fail-closed: unless {@code eyeandsickle.identity.dev-signin.enabled} is true it refuses every attempt,
 * so as the sole provider in a normal deployment nobody signs in. Both sides of that switch are tested,
 * because the whole safety of the stub is the switch.
 */
class DevAtProtoIdentityProviderTest {

    private static IdentityProperties props(boolean devSigninEnabled) {
        return new IdentityProperties(
                new Operator(null, null, null), new DevSignin(devSigninEnabled), Duration.ofHours(24), (BigDecimal)
                        null);
    }

    private static SignInCredentials claiming(String did) {
        return new SignInCredentials("alice.bsky.social", did, null, null, null);
    }

    @Nested
    @DisplayName("disabled (the default on any real deployment)")
    class Disabled {

        @Test
        @DisplayName("refuses sign-in as unavailable, even with a valid claimed DID")
        void refusesWhenDisabled() {
            // The self-guard: fail-closed. A perfectly well-formed claimed DID must still be refused when
            // the switch is off, or the "disabled by default" guarantee is hollow.
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(false));
            assertThatThrownBy(() -> provider.authenticate(claiming("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa")))
                    .isInstanceOf(SignInUnavailableException.class)
                    .hasMessageContaining("dev-signin");
        }

        @Test
        @DisplayName("SignInUnavailableException is a SignInException — sign-in failed, coarsely")
        void unavailableIsASignInException() {
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(false));
            assertThatThrownBy(() -> provider.authenticate(claiming("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa")))
                    .isInstanceOf(SignInException.class);
        }
    }

    @Nested
    @DisplayName("enabled (local development only)")
    class Enabled {

        @Test
        @DisplayName("authenticates the claimed DID and carries the handle through")
        void authenticatesClaimedDid() {
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(true));
            ResolvedIdentity identity = provider.authenticate(claiming("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"));

            assertThat(identity.did()).isEqualTo(Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"));
            assertThat(identity.handle()).isEqualTo("alice.bsky.social");
        }

        @Test
        @DisplayName("a missing handle is allowed — the handle is not the identity")
        void handleMayBeAbsent() {
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(true));
            ResolvedIdentity identity = provider.authenticate(
                    new SignInCredentials(null, "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa", null, null, null));
            assertThat(identity.handle()).isNull();
        }

        @Test
        @DisplayName("a missing claimed DID is refused as a sign-in failure, not as unavailable")
        void missingClaimedDid() {
            // Even the dev shortcut needs to be told which DID to impersonate; but this is an
            // authentication failure (401), distinct from the provider being unwired (503).
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(true));
            assertThatThrownBy(() -> provider.authenticate(claiming(null)))
                    .isInstanceOf(SignInException.class)
                    .isNotInstanceOf(SignInUnavailableException.class);
            assertThatThrownBy(() -> provider.authenticate(claiming("   "))).isInstanceOf(SignInException.class);
        }

        @Test
        @DisplayName("a malformed claimed DID fails as an authentication failure, not a raw IllegalArgument")
        void malformedClaimedDid() {
            // A real provider would not leak which step failed; the dev provider mirrors that by turning a
            // bad-shape DID into a SignInException rather than surfacing the underlying IllegalArgument.
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(true));
            assertThatThrownBy(() -> provider.authenticate(claiming("not-a-did")))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("well-shaped");
        }

        @Test
        @DisplayName("null credentials are a programming error")
        void nullCredentials() {
            DevAtProtoIdentityProvider provider = new DevAtProtoIdentityProvider(props(true));
            assertThatThrownBy(() -> provider.authenticate(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
