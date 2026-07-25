package io.github.stoicswe.eyeandsickle.protocol.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code prevRecordHash} format and the "chains to" relation.
 *
 * <p>Like the wire format, this is a one-way door: once chains exist, the string form cannot change
 * without invalidating every record that links to one. See {@link RecordHash} for why hex with an
 * algorithm prefix was chosen, and why the digest covers the canonical payload rather than the
 * envelope.
 */
class RecordHashTest {

    private final ChainFixture fixture = new ChainFixture();

    @Nested
    @DisplayName("the string form")
    class StringForm {

        @Test
        @DisplayName("is 'sha256-' followed by 64 lowercase hex characters")
        void isAlgorithmPrefixedLowercaseHex() {
            String hash = RecordHash.of(fixture.genesis());

            assertThat(hash).startsWith("sha256-").hasSize("sha256-".length() + 64);
            assertThat(hash.substring("sha256-".length())).matches("[0-9a-f]{64}");
            assertThat(RecordHash.isWellFormed(hash)).isTrue();
        }

        @Test
        @DisplayName("rejects anything else as unreadable rather than mis-comparing it")
        void rejectsOtherShapes() {
            assertThat(RecordHash.isWellFormed(null)).isFalse();
            assertThat(RecordHash.isWellFormed("")).isFalse();
            assertThat(RecordHash.isWellFormed("sha256-of-previous-record-in-chain"))
                    .isFalse();
            // Uppercase hex is a different string for the same digest, and the string itself is
            // signed, so admitting both spellings would let one history hash two ways.
            String hash = RecordHash.of(fixture.genesis());
            String upperHex = "sha256-" + hash.substring("sha256-".length()).toUpperCase();
            assertThat(RecordHash.isWellFormed(upperHex)).isFalse();
            assertThat(RecordHash.isWellFormed("sha512-" + "a".repeat(64))).isFalse();
        }
    }

    @Nested
    @DisplayName("the digest")
    class Digest {

        @Test
        @DisplayName("is deterministic for equal payloads")
        void isDeterministic() {
            ProvenancePayload payload = fixture.genesis();
            ProvenancePayload same = ChainFixture.Edit.of(payload).build();

            assertThat(RecordHash.of(payload)).isEqualTo(RecordHash.of(same));
        }

        @Test
        @DisplayName("covers the canonical payload bytes, so signing input and chain link agree")
        void coversTheCanonicalPayloadBytes() {
            ProvenancePayload payload = fixture.genesis();

            assertThat(RecordHash.ofCanonicalBytes(ProvenanceJson.canonicalBytes(payload)))
                    .isEqualTo(RecordHash.of(payload));
        }

        @Test
        @DisplayName("changes when any signed field changes")
        void changesWithThePayload() {
            ProvenancePayload payload = fixture.genesis();
            String original = RecordHash.of(payload);

            assertThat(RecordHash.of(ChainFixture.Edit.of(payload)
                            .itemAttrs(Map.of("power", 9001))
                            .build()))
                    .isNotEqualTo(original);
            assertThat(RecordHash.of(ChainFixture.Edit.of(payload)
                            .holderDid("did:plc:thief")
                            .build()))
                    .isNotEqualTo(original);
            assertThat(RecordHash.of(
                            ChainFixture.Edit.of(payload).nonce("nonce-other").build()))
                    .isNotEqualTo(original);
        }
    }

    @Nested
    @DisplayName("the chains-to relation")
    class ChainsTo {

        @Test
        @DisplayName("holds for a correctly built successor")
        void holdsForACorrectSuccessor() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload second = fixture.following(
                    genesis, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER);

            assertThat(RecordHash.links(second.prevRecordHash(), genesis)).isTrue();
        }

        @Test
        @DisplayName("fails if the predecessor was edited by so much as one attribute")
        void failsForAnEditedPredecessor() {
            ProvenancePayload genesis = fixture.genesis();
            ProvenancePayload second = fixture.following(
                    genesis, ProvenanceEventType.TRADE, ChainFixture.OTHER_HOLDER, ChainFixture.HOME_SERVER);
            ProvenancePayload buffed = ChainFixture.Edit.of(genesis)
                    .itemAttrs(Map.of("power", 9001, "durability", 0.87))
                    .build();

            assertThat(RecordHash.links(second.prevRecordHash(), buffed)).isFalse();
        }

        @Test
        @DisplayName("a genesis record chains to nothing")
        void genesisChainsToNothing() {
            assertThat(RecordHash.links(null, fixture.genesis())).isFalse();
        }
    }
}
