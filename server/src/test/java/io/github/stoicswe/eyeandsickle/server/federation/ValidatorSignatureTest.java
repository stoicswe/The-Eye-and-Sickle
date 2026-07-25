package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * One validator's signed vote — {@code docs/architecture/05-validator-quorum.md} §5 steps 3–4. A vote
 * IS a candidate {@code duel_grant} payload plus a signature over its canonical bytes; the constructor
 * enforces that shape so a malformed or mis-typed outcome cannot enter adjudication as a vote.
 */
class ValidatorSignatureTest {

    private final FederationFixture fx = new FederationFixture();

    private static SignatureBlock anyBlock() {
        return SignatureBlock.eddsa("did:plc:validator1#key1", "AAAA");
    }

    @Nested
    @DisplayName("derived accessors")
    class Accessors {

        @Test
        @DisplayName("reads the duel id from the outcome's duel: issuer")
        void duelId() {
            ValidatorSignature vote = fx.vote("abcd-1234", FederationFixture.HOLDER_A, "did:plc:validator1");
            assertThat(vote.duelId()).isEqualTo("abcd-1234");
        }

        @Test
        @DisplayName("reads the signer from the signature's kid, not from the outcome")
        void validatorDid() {
            // The outcome names duel:<id> as issuer, so the signer must come from the signature — a
            // committee-issued document has no single author.
            ValidatorSignature vote = fx.vote("d1", FederationFixture.HOLDER_A, "did:plc:validator7");
            assertThat(vote.validatorDid()).isEqualTo("did:plc:validator7");
        }

        @Test
        @DisplayName("exposes exactly the outcome's RFC 8785 canonical bytes")
        void canonicalBytes() {
            ProvenancePayload outcome = fx.outcome("d1", FederationFixture.HOLDER_A);
            ValidatorSignature vote = new ValidatorSignature(outcome, fx.sign(outcome, "did:plc:validator1"));
            assertThat(vote.canonicalBytes()).isEqualTo(ProvenanceJson.canonicalBytes(outcome));
        }
    }

    @Nested
    @DisplayName("construction rules")
    class Construction {

        @Test
        @DisplayName("refuses an outcome that is not a duel_grant")
        void refusesNonDuelGrant() {
            ProvenancePayload trade = new ProvenancePayload(
                    ProvenancePayload.CURRENT_RECORD_VERSION,
                    UUID.randomUUID(),
                    "hacking_tool_tier2",
                    Map.of(),
                    ProvenanceEventType.TRADE,
                    FederationFixture.HOLDER_A,
                    "duel:d1",
                    "11",
                    1,
                    "2026-07-24T00:00:00Z",
                    "n1");
            // A vote is a candidate duel outcome; a trade is not something a committee votes into being.
            assertThatThrownBy(() -> new ValidatorSignature(trade, anyBlock()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a duel_grant whose issuer is not a duel: identifier")
        void refusesNonDuelIssuer() {
            ProvenancePayload wrongIssuer = new ProvenancePayload(
                    ProvenancePayload.CURRENT_RECORD_VERSION,
                    UUID.randomUUID(),
                    "hacking_tool_tier2",
                    Map.of(),
                    ProvenanceEventType.DUEL_GRANT,
                    FederationFixture.HOLDER_A,
                    "did:plc:server0000000000000", // a single server, not a committee
                    "11",
                    1,
                    "2026-07-24T00:00:00Z",
                    "n1");
            // A duel outcome is committee-issued; its issuer must be the synthetic duel:<id>.
            assertThatThrownBy(() -> new ValidatorSignature(wrongIssuer, anyBlock()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a bare duel: prefix with no duel id after it")
        void refusesEmptyDuelId() {
            ProvenancePayload emptyId = new ProvenancePayload(
                    ProvenancePayload.CURRENT_RECORD_VERSION,
                    UUID.randomUUID(),
                    "hacking_tool_tier2",
                    Map.of(),
                    ProvenanceEventType.DUEL_GRANT,
                    FederationFixture.HOLDER_A,
                    "duel:",
                    "11",
                    1,
                    "2026-07-24T00:00:00Z",
                    "n1");
            assertThatThrownBy(() -> new ValidatorSignature(emptyId, anyBlock()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses null outcome or null signature")
        void refusesNulls() {
            ProvenancePayload outcome = fx.outcome("d1", FederationFixture.HOLDER_A);
            assertThatThrownBy(() -> new ValidatorSignature(null, anyBlock())).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ValidatorSignature(outcome, null)).isInstanceOf(NullPointerException.class);
        }
    }
}
