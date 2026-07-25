package io.github.stoicswe.eyeandsickle.protocol.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CharacterHomeRecord} — the signed pointer the character directory resolves a DID's
 * home from ({@code docs/architecture/09-player-state-portability.md} §4).
 *
 * <p>The record is the only thing standing between "a server claims to host this character" and "this
 * character's home is that DID". Every property it claims is tested here as an attack: a signature that
 * transfers to a record with a different field split, a binding altered after signing, a wrong key that
 * must not validate, an array a caller can mutate out from under a verified record. The
 * length-prefix and domain-separation discipline is pinned so a regression names itself.
 */
class CharacterHomeRecordTest {

    private static final String ACCOUNT_DID = "did:plc:account0000000000";
    private static final UUID CHARACTER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final int SLOT = 2;
    private static final String HOME_DID = "did:plc:home000000000000";
    private static final String SIGNING_KID = HOME_DID + "#key1";
    private static final String ENDPOINT = "https://home.example.org";
    private static final long SEQUENCE = 7;

    /** Domain-separation prefix, mirrored from the private constant it must keep matching. */
    private static final String CONTEXT = "eyeandsickle/character-home-record/v1";

    private final KeyPair homeKeys = Ed25519Signatures.generateKeyPair();
    private final KeyPair transportKeys = X25519KeyExchange.generateKeyPair();

    private CharacterHomeRecord signed() {
        return signed(SEQUENCE);
    }

    private CharacterHomeRecord signed(long sequence) {
        return CharacterHomeRecord.sign(
                ACCOUNT_DID,
                CHARACTER_ID,
                SLOT,
                HOME_DID,
                SIGNING_KID,
                ENDPOINT,
                transportKeys.getPublic(),
                sequence,
                homeKeys.getPrivate());
    }

    private PublicKey homeKey() {
        return homeKeys.getPublic();
    }

    private byte[] encodedTransportKey() {
        return X25519KeyExchange.encodePublicKey(transportKeys.getPublic());
    }

    // ------------------------------------------------------------------ signing and verification

    @Nested
    @DisplayName("signing and verification")
    class SigningAndVerification {

        @Test
        @DisplayName("a record verifies against the home-server key that signed it, and carries its fields")
        void signedRecordVerifies() {
            CharacterHomeRecord record = signed();

            assertThat(record.accountDid()).isEqualTo(ACCOUNT_DID);
            assertThat(record.characterId()).isEqualTo(CHARACTER_ID);
            assertThat(record.slot()).isEqualTo(SLOT);
            assertThat(record.homeServerDid()).isEqualTo(HOME_DID);
            assertThat(record.signingKeyId()).isEqualTo(SIGNING_KID);
            assertThat(record.homeEndpoint()).isEqualTo(ENDPOINT);
            assertThat(record.homeTransportPublicKey()).isEqualTo(encodedTransportKey());
            assertThat(record.sequenceNumber()).isEqualTo(SEQUENCE);
            assertThat(record.verify(homeKey())).isTrue();
        }

        @Test
        @DisplayName("the attested transport key decodes back to the key that was attested")
        void attestedKeyDecodes() {
            assertThat(X25519KeyExchange.encodePublicKey(signed().transportKey()))
                    .isEqualTo(encodedTransportKey());
        }

        @Test
        @DisplayName("another server's key does not validate this record")
        void wrongHomeKeyIsRejected() {
            // The whole point of the record: if any key validated it, the binding would prove nothing about
            // which server actually hosts the character.
            KeyPair otherHome = Ed25519Signatures.generateKeyPair();
            assertThat(signed().verify(otherHome.getPublic())).isFalse();
        }

        @Test
        @DisplayName("a bit-flipped signature is rejected, not fatal")
        void corruptedSignatureIsRejected() {
            CharacterHomeRecord original = signed();
            byte[] sig = original.signature();
            sig[0] ^= 0x01;

            CharacterHomeRecord tampered = new CharacterHomeRecord(
                    original.accountDid(),
                    original.characterId(),
                    original.slot(),
                    original.homeServerDid(),
                    original.signingKeyId(),
                    original.homeEndpoint(),
                    original.homeTransportPublicKey(),
                    original.sequenceNumber(),
                    sig);
            assertThat(tampered.verify(homeKey())).isFalse();
        }

        @Test
        @DisplayName("advancing the sequence is a different record and the old signature does not carry")
        void sequenceIsSigned() {
            // A home change advances the sequence; the signature must cover it, or a captured record could
            // be replayed under a new sequence to defeat the anti-rollback.
            CharacterHomeRecord atFive = signed(5);
            CharacterHomeRecord atSix = new CharacterHomeRecord(
                    atFive.accountDid(),
                    atFive.characterId(),
                    atFive.slot(),
                    atFive.homeServerDid(),
                    atFive.signingKeyId(),
                    atFive.homeEndpoint(),
                    atFive.homeTransportPublicKey(),
                    6,
                    atFive.signature());
            assertThat(atSix.verify(homeKey())).isFalse();
        }

        @Test
        @DisplayName("rewriting the home endpoint after signing is rejected — sign-benign-ship-malicious")
        void tamperedEndpointIsRejected() {
            CharacterHomeRecord original = signed();
            CharacterHomeRecord moved = new CharacterHomeRecord(
                    original.accountDid(),
                    original.characterId(),
                    original.slot(),
                    original.homeServerDid(),
                    original.signingKeyId(),
                    "https://attacker.example.evil",
                    original.homeTransportPublicKey(),
                    original.sequenceNumber(),
                    original.signature());
            assertThat(moved.verify(homeKey())).isFalse();
        }
    }

    // ------------------------------------------------------------------ unambiguous signing bytes

    @Nested
    @DisplayName("unambiguous signing bytes")
    class SigningBytes {

        @Test
        @DisplayName("the signing input is domain-separated from every other signature in the game")
        void domainSeparated() {
            // ISO-8859-1 so the search is byte-for-byte and cannot be perturbed by UTF-8 decoding.
            String bytes = new String(
                    CharacterHomeRecord.signingBytes(
                            ACCOUNT_DID,
                            CHARACTER_ID,
                            SLOT,
                            HOME_DID,
                            SIGNING_KID,
                            ENDPOINT,
                            encodedTransportKey(),
                            SEQUENCE),
                    StandardCharsets.ISO_8859_1);
            assertThat(bytes).contains(CONTEXT);
        }

        @Test
        @DisplayName("length-prefixing makes a field-boundary shift produce different bytes")
        void lengthPrefixingPreventsFieldConfusion() {
            // Without length prefixes, moving a character from account "…ab"/home "cd…" to account
            // "…a"/home "bcd…" — the boundary shifted by one byte — could hash identically. Length prefixes
            // make the two records' signing input distinct, so a signature over one never validates the
            // other.
            byte[] first = CharacterHomeRecord.signingBytes(
                    "did:plc:xab",
                    CHARACTER_ID,
                    SLOT,
                    "cd" + HOME_DID,
                    SIGNING_KID,
                    ENDPOINT,
                    encodedTransportKey(),
                    SEQUENCE);
            byte[] second = CharacterHomeRecord.signingBytes(
                    "did:plc:xa",
                    CHARACTER_ID,
                    SLOT,
                    "bcd" + HOME_DID,
                    SIGNING_KID,
                    ENDPOINT,
                    encodedTransportKey(),
                    SEQUENCE);
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("the same logical record serializes to identical bytes — signatures reproduce across servers")
        void deterministic() {
            byte[] a = CharacterHomeRecord.signingBytes(
                    ACCOUNT_DID, CHARACTER_ID, SLOT, HOME_DID, SIGNING_KID, ENDPOINT, encodedTransportKey(), SEQUENCE);
            byte[] b = CharacterHomeRecord.signingBytes(
                    ACCOUNT_DID, CHARACTER_ID, SLOT, HOME_DID, SIGNING_KID, ENDPOINT, encodedTransportKey(), SEQUENCE);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("a change in slot or sequence changes the signed bytes")
        void slotAndSequenceAreCovered() {
            byte[] base = CharacterHomeRecord.signingBytes(
                    ACCOUNT_DID, CHARACTER_ID, SLOT, HOME_DID, SIGNING_KID, ENDPOINT, encodedTransportKey(), SEQUENCE);
            byte[] otherSlot = CharacterHomeRecord.signingBytes(
                    ACCOUNT_DID,
                    CHARACTER_ID,
                    SLOT + 1,
                    HOME_DID,
                    SIGNING_KID,
                    ENDPOINT,
                    encodedTransportKey(),
                    SEQUENCE);
            byte[] otherSeq = CharacterHomeRecord.signingBytes(
                    ACCOUNT_DID,
                    CHARACTER_ID,
                    SLOT,
                    HOME_DID,
                    SIGNING_KID,
                    ENDPOINT,
                    encodedTransportKey(),
                    SEQUENCE + 1);
            assertThat(base).isNotEqualTo(otherSlot);
            assertThat(base).isNotEqualTo(otherSeq);
        }
    }

    // ------------------------------------------------------------------ defensive construction

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the record defends its mutable arrays on the way in and out")
        void arraysAreCopied() {
            byte[] key = encodedTransportKey();
            byte[] sig = new byte[64];
            CharacterHomeRecord record = new CharacterHomeRecord(
                    ACCOUNT_DID, CHARACTER_ID, SLOT, HOME_DID, SIGNING_KID, ENDPOINT, key, SEQUENCE, sig);

            key[0] ^= 0x7f; // mutate the source after construction
            sig[0] ^= 0x7f;
            assertThat(record.homeTransportPublicKey()).isNotEqualTo(key);
            assertThat(record.signature()).isNotEqualTo(sig);

            byte[] handedOut = record.signature();
            handedOut[0] ^= 0x7f; // mutate what we were handed
            assertThat(record.signature()).isNotEqualTo(handedOut);
        }

        @Test
        @DisplayName("a slot below 1 is not a slot")
        void slotMustBePositive() {
            assertThatThrownBy(() -> new CharacterHomeRecord(
                            ACCOUNT_DID,
                            CHARACTER_ID,
                            0,
                            HOME_DID,
                            SIGNING_KID,
                            ENDPOINT,
                            encodedTransportKey(),
                            SEQUENCE,
                            new byte[64]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("slot");
        }

        @Test
        @DisplayName("a negative sequence is refused at construction, before it can reach an INSERT")
        void sequenceMustNotBeNegative() {
            assertThatThrownBy(() -> new CharacterHomeRecord(
                            ACCOUNT_DID,
                            CHARACTER_ID,
                            SLOT,
                            HOME_DID,
                            SIGNING_KID,
                            ENDPOINT,
                            encodedTransportKey(),
                            -1,
                            new byte[64]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sequenceNumber");
        }
    }
}
