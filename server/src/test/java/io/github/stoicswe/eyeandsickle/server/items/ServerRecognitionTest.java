package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ServerRecognition} and the {@link ServerIssuerAuthority} built on it — the seam that decides
 * whose mints this server will recognize. A cheating server's items become worthless precisely because
 * its DID is <em>not</em> recognized here ({@code docs/architecture/03} §4, Invariant I15).
 */
class ServerRecognitionTest {

    private static final String HOME = "did:plc:homeserver0000000000";
    private static final String PEER = "did:plc:peerserver0000000000";
    private static final String ROGUE = "did:plc:rogueserver000000000";

    private static ProvenancePayload mintBy(String issuerDid) {
        return new ProvenancePayload(
                ProvenancePayload.CURRENT_RECORD_VERSION,
                TestChains.ITEM_ID,
                TestChains.ITEM_TYPE,
                Map.of("power", 1),
                ProvenanceEventType.INITIAL_MINT,
                TestChains.HOLDER,
                issuerDid,
                null,
                0,
                "2026-08-01T12:00:00Z",
                "nonce-1");
    }

    // ------------------------------------------------------------------ recognition

    @Nested
    @DisplayName("recognition")
    class Recognition {

        @Test
        @DisplayName("selfOnly recognizes this server's DID and nothing else")
        void selfOnlyRecognizesSelf() {
            ServerRecognition recognition = ServerRecognition.selfOnly(HOME);

            assertThat(recognition.recognizesIssuer(HOME)).isTrue();
            assertThat(recognition.recognizesIssuer(PEER)).isFalse();
        }

        @Test
        @DisplayName("selfOnly(null) — an unconfigured server — recognizes no one")
        void selfOnlyNullRecognizesNothing() {
            // Correct for a receive-only node: it accepts only externally supplied items verified against
            // a real directory, and trusts nothing it minted (because it minted nothing).
            ServerRecognition recognition = ServerRecognition.selfOnly(null);

            assertThat(recognition.recognizesIssuer(HOME)).isFalse();
            assertThat(recognition.recognizesIssuer(null)).isFalse();
        }

        @Test
        @DisplayName("of(set) recognizes exactly the configured DIDs")
        void ofSetRecognizesMembers() {
            ServerRecognition recognition = ServerRecognition.of(Set.of(HOME, PEER));

            assertThat(recognition.recognizesIssuer(HOME)).isTrue();
            assertThat(recognition.recognizesIssuer(PEER)).isTrue();
            assertThat(recognition.recognizesIssuer(ROGUE)).isFalse();
        }

        @Test
        @DisplayName("an empty recognition set recognizes no one")
        void emptySetRecognizesNothing() {
            assertThat(ServerRecognition.of(Set.of()).recognizesIssuer(HOME)).isFalse();
        }
    }

    // ------------------------------------------------------------------ issuer authority

    @Nested
    @DisplayName("issuer authority")
    class IssuerAuthorityTests {

        @Test
        @DisplayName("authorizes a mint from a recognized issuer")
        void recognizedIssuerIsAuthorized() {
            ServerIssuerAuthority authority = new ServerIssuerAuthority(ServerRecognition.of(Set.of(HOME)));

            assertThat(authority.isAuthorizedIssuer(mintBy(HOME))).isTrue();
        }

        @Test
        @DisplayName("refuses a mint from an unrecognized issuer")
        void unrecognizedIssuerIsRefused() {
            ServerIssuerAuthority authority = new ServerIssuerAuthority(ServerRecognition.selfOnly(HOME));

            // This is what makes a fabricating server's items worthless federation-wide.
            assertThat(authority.isAuthorizedIssuer(mintBy(ROGUE))).isFalse();
        }

        @Test
        @DisplayName("the decision keys on the payload's issuerDid")
        void authorityKeysOnIssuerDid() {
            ServerIssuerAuthority authority = new ServerIssuerAuthority(ServerRecognition.of(Set.of(PEER)));

            assertThat(authority.isAuthorizedIssuer(mintBy(PEER))).isTrue();
            assertThat(authority.isAuthorizedIssuer(mintBy(HOME))).isFalse();
        }

        @Test
        @DisplayName("a null payload is a programming error, not a silent 'no'")
        void nullPayloadThrows() {
            ServerIssuerAuthority authority = new ServerIssuerAuthority(ServerRecognition.selfOnly(HOME));

            assertThatThrownBy(() -> authority.isAuthorizedIssuer(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
