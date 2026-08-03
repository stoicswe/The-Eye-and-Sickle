package io.github.stoicswe.eyeandsickle.server.lan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.ResolvedIdentity;
import io.github.stoicswe.eyeandsickle.server.identity.SignInCredentials;
import io.github.stoicswe.eyeandsickle.server.identity.SignInException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** LAN mode: identity, the quarantine, and the address interlock. */
class LanModeTest {

    @Nested
    @DisplayName("LAN identity")
    class Identity {

        @Test
        @DisplayName("is a well-formed DID the database will accept")
        void isAValidDid() {
            // did:eas-lan would be rejected three layers down: a DID method is [a-z0-9]+ in both
            // Did's pattern and the is_did CHECK, which are deliberately identical.
            Did did = LanIdentity.mint();

            assertThat(did.value()).startsWith("did:easlan:");
            assertThat(Did.of(did.value())).isEqualTo(did);
        }

        @Test
        @DisplayName("⚠ is UNGUESSABLE — it is a bearer token, so a counter would be a free identity")
        void isUnguessable() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 500; i++) {
                seen.add(LanIdentity.mint().value());
            }
            assertThat(seen).hasSize(500);
        }

        @Test
        @DisplayName("carries its own quarantine — no table lookup, no flag that can go stale")
        void carriesItsQuarantine() {
            assertThat(LanIdentity.isLanIdentity(LanIdentity.mint())).isTrue();
            assertThat(LanIdentity.isLanIdentity(Did.of("did:plc:abcdefghijklmnopqrstuvwx")))
                    .isFalse();
            assertThat(LanIdentity.isLanIdentity(Did.of("did:web:home.example")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("the LAN identity provider")
    class Provider {

        private final LanIdentityProvider provider = new LanIdentityProvider();

        @Test
        @DisplayName("accepts a LAN identity at face value — that is the mode, not a bug")
        void acceptsLanIdentity() {
            Did did = LanIdentity.mint();

            ResolvedIdentity resolved =
                    provider.authenticate(new SignInCredentials("ghost", did.value(), null, null, null));

            assertThat(resolved.did()).isEqualTo(did);
            assertThat(resolved.handle()).isEqualTo("ghost");
        }

        @Test
        @DisplayName("⚠ REFUSES a real did:plc — a LAN server must not be an impersonation oracle")
        void refusesFederatedIdentity() {
            // Without this, anyone on the LAN presents a stranger's real DID and is admitted as them
            // with no proof whatsoever.
            assertThatThrownBy(() -> provider.authenticate(
                            new SignInCredentials("ghost", "did:plc:abcdefghijklmnopqrstuvwx", null, null, null)))
                    .isInstanceOf(SignInException.class)
                    .hasMessageContaining("only LAN identities");
        }

        @Test
        @DisplayName("refuses an empty claim rather than minting one")
        void refusesNothing() {
            // Joining is a separate, explicit act. Authenticating must not create an identity.
            assertThatThrownBy(() -> provider.authenticate(new SignInCredentials(null, null, null, null, null)))
                    .isInstanceOf(SignInException.class);
        }
    }

    @Nested
    @DisplayName("the quarantine")
    class QuarantineRule {

        @Test
        @DisplayName("⚠ refuses to let a LAN character cross into federated state")
        void refusesLan() {
            assertThatThrownBy(() -> Quarantine.refuseIfLan(LanIdentity.mint()))
                    .isInstanceOf(Quarantine.QuarantinedException.class)
                    .hasMessageContaining("LAN");
        }

        @Test
        @DisplayName("lets a federated identity through")
        void permitsFederated() {
            Quarantine.refuseIfLan(Did.of("did:plc:abcdefghijklmnopqrstuvwx"));
        }
    }

    @Nested
    @DisplayName("the address interlock")
    class Interlock {

        @Test
        @DisplayName("private ranges are accepted")
        void privateIsPrivate() throws Exception {
            for (String literal : new String[] {"127.0.0.1", "10.0.0.5", "192.168.1.20", "172.16.4.4", "169.254.1.1"}) {
                assertThat(LanAddressInterlock.isPrivate(InetAddress.getByName(literal)))
                        .as("%s", literal)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("⚠ IPv6 unique-local is private — isSiteLocalAddress only covers DEPRECATED fec0::/10")
        void uniqueLocalIsPrivate() throws Exception {
            // Without the hand-written fc00::/7 branch a modern private IPv6 network reads as PUBLIC
            // and the server refuses to start on exactly the setup LAN mode exists for.
            assertThat(InetAddress.getByName("fd12:3456::1").isSiteLocalAddress())
                    .isFalse();
            assertThat(LanAddressInterlock.isPrivate(InetAddress.getByName("fd12:3456::1")))
                    .isTrue();
        }

        @Test
        @DisplayName("⚠ public addresses are NOT private — the whole mode rests on this")
        void publicIsPublic() throws Exception {
            // A LAN-mode server on a public address is an open server that mints an identity for any
            // stranger who asks, and the failure is completely silent.
            for (String literal : new String[] {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"}) {
                assertThat(LanAddressInterlock.isPrivate(InetAddress.getByName(literal)))
                        .as("%s", literal)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("mode defaults")
    class Defaults {

        @Test
        @DisplayName("⚠ an unset mode is FEDERATED — a typo must not disable authentication")
        void defaultsToFederated() {
            assertThat(new LanProperties(ServerMode.FEDERATED, false).mode().isLan())
                    .isFalse();
            assertThat(ServerMode.FEDERATED.isLan()).isFalse();
            assertThat(ServerMode.LAN.isLan()).isTrue();
        }
    }
}
