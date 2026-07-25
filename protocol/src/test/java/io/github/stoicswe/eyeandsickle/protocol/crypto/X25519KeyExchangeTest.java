package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Conformance and behaviour tests for {@link X25519KeyExchange}.
 *
 * <h2>Getting the RFC 7748 vectors into the JCA</h2>
 *
 * {@link X25519KeyExchange} deals in X.509 {@code SubjectPublicKeyInfo} encodings because that is
 * what travels on the wire, and RFC 7748 publishes raw little-endian scalars and u-coordinates. That
 * looks at first like a reason to fall back to testing algebraic properties only — but it is not:
 * the JCA also accepts {@link XECPrivateKeySpec} and {@link XECPublicKeySpec}, which take the raw
 * scalar and the u-coordinate as a {@code BigInteger}. So the published vectors go in directly, and
 * this suite tests the real thing rather than a property that a subtly wrong implementation could
 * also satisfy. The two conversions needed are handled in {@link #publicKeyFromU(String)}: reverse
 * the RFC's little-endian bytes, and mask the most significant bit of the final byte as RFC 7748 §5
 * requires.
 *
 * <p>Algebraic properties are tested too, but underneath the vectors rather than instead of them.
 *
 * <p>{@code docs/architecture/07-transport-security.md} §4.1 explains why this is a separate key
 * type from the Ed25519 DID key rather than a conversion of it.
 */
class X25519KeyExchangeTest {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * The 12-byte X.509 {@code SubjectPublicKeyInfo} header for an X25519 key: a SEQUENCE wrapping
     * the {@code id-X25519} OID (1.3.101.110) and a 33-byte BIT STRING holding the u-coordinate.
     */
    private static final byte[] SPKI_PREFIX = HEX.parseHex("302a300506032b656e032100");

    // RFC 7748 §6.1 — the ECDH worked example.
    private static final String ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
    private static final String ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
    private static final String BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
    private static final String BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
    private static final String SHARED_SECRET = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

    /** Builds a private key from an RFC 7748 raw scalar. */
    private static PrivateKey privateKeyFromScalar(String scalarHex) {
        try {
            return KeyFactory.getInstance(X25519KeyExchange.ALGORITHM)
                    .generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, HEX.parseHex(scalarHex)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a public key from an RFC 7748 raw u-coordinate. */
    private static PublicKey publicKeyFromU(String uHex) {
        byte[] littleEndian = HEX.parseHex(uHex);
        // RFC 7748 §5: implementations MUST mask the most significant bit of the final byte before
        // using a received u-coordinate. Vector §5.2's second case has it set, and skipping the mask
        // silently produces a different point.
        littleEndian[littleEndian.length - 1] &= 0x7f;
        byte[] bigEndian = new byte[littleEndian.length];
        for (int i = 0; i < littleEndian.length; i++) {
            bigEndian[i] = littleEndian[littleEndian.length - 1 - i];
        }
        try {
            return KeyFactory.getInstance(X25519KeyExchange.ALGORITHM)
                    .generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, new BigInteger(1, bigEndian)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("RFC 7748 test vectors")
    class Rfc7748Vectors {

        @Test
        @DisplayName("§6.1 — Alice and Bob derive the published shared secret, from both sides")
        void ecdhWorkedExample() {
            byte[] fromAlice = X25519KeyExchange.agree(privateKeyFromScalar(ALICE_PRIVATE), publicKeyFromU(BOB_PUBLIC));
            byte[] fromBob = X25519KeyExchange.agree(privateKeyFromScalar(BOB_PRIVATE), publicKeyFromU(ALICE_PUBLIC));

            assertThat(HEX.formatHex(fromAlice)).isEqualTo(SHARED_SECRET);
            assertThat(HEX.formatHex(fromBob)).isEqualTo(SHARED_SECRET);
        }

        @Test
        @DisplayName("§6.1 — a public key really is X25519(private, 9)")
        void publicKeyIsTheBasePointMultiple() {
            // Pinned because it is the definition the RFC gives, and because it lets the vector's
            // private and public halves check each other rather than being two unrelated constants.
            PublicKey basePoint = publicKeyFromU("0900000000000000000000000000000000000000000000000000000000000000");

            assertThat(HEX.formatHex(X25519KeyExchange.agree(privateKeyFromScalar(ALICE_PRIVATE), basePoint)))
                    .isEqualTo(ALICE_PUBLIC);
            assertThat(HEX.formatHex(X25519KeyExchange.agree(privateKeyFromScalar(BOB_PRIVATE), basePoint)))
                    .isEqualTo(BOB_PUBLIC);
        }

        @Test
        @DisplayName("§5.2 — the raw scalar-multiplication vectors")
        void scalarMultiplicationVectors() {
            // These exercise X25519(scalar, u) directly through the same agree() the handshake uses.
            // Case two's input u-coordinate has its high bit set, which is what makes the mask in
            // publicKeyFromU load-bearing rather than decorative.
            assertThat(HEX.formatHex(X25519KeyExchange.agree(
                            privateKeyFromScalar("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"),
                            publicKeyFromU("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"))))
                    .isEqualTo("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552");

            assertThat(HEX.formatHex(X25519KeyExchange.agree(
                            privateKeyFromScalar("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d"),
                            publicKeyFromU("e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493"))))
                    .isEqualTo("95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957");
        }

        @Test
        @DisplayName("the vector's public key encodes to the SPKI form we put on the wire")
        void vectorKeyEncodesToTheWireForm() {
            byte[] encoded = X25519KeyExchange.encodePublicKey(publicKeyFromU(ALICE_PUBLIC));

            assertThat(encoded).hasSize(X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH);
            assertThat(Arrays.copyOf(encoded, SPKI_PREFIX.length)).isEqualTo(SPKI_PREFIX);
            // The trailing 32 bytes are the RFC's little-endian u-coordinate verbatim — the X.509
            // wrapper is a header, not a re-encoding.
            assertThat(HEX.formatHex(Arrays.copyOfRange(encoded, SPKI_PREFIX.length, encoded.length)))
                    .isEqualTo(ALICE_PUBLIC);
        }
    }

    @Nested
    @DisplayName("agreement")
    class Agreement {

        @Test
        @DisplayName("both sides of a generated pair derive the same secret")
        void agreementIsSymmetric() {
            KeyPair client = X25519KeyExchange.generateKeyPair();
            KeyPair server = X25519KeyExchange.generateKeyPair();

            byte[] clientSide = X25519KeyExchange.agree(client.getPrivate(), server.getPublic());
            byte[] serverSide = X25519KeyExchange.agree(server.getPrivate(), client.getPublic());

            assertThat(clientSide).isEqualTo(serverSide).hasSize(X25519KeyExchange.SHARED_SECRET_LENGTH);
        }

        @Test
        @DisplayName("the secret is 32 bytes and is not the peer's public key")
        void secretShape() {
            KeyPair ours = X25519KeyExchange.generateKeyPair();
            KeyPair theirs = X25519KeyExchange.generateKeyPair();

            byte[] secret = X25519KeyExchange.agree(ours.getPrivate(), theirs.getPublic());

            assertThat(secret).hasSize(32);
            // A guard against the dumbest possible implementation error, which does not look dumb
            // in a debugger: both values are 32 opaque bytes.
            assertThat(secret)
                    .isNotEqualTo(Arrays.copyOfRange(theirs.getPublic().getEncoded(), 12, 44));
        }

        @Test
        @DisplayName("different pairs give different secrets")
        void distinctPairsGiveDistinctSecrets() {
            KeyPair ours = X25519KeyExchange.generateKeyPair();

            byte[] first = X25519KeyExchange.agree(
                    ours.getPrivate(), X25519KeyExchange.generateKeyPair().getPublic());
            byte[] second = X25519KeyExchange.agree(
                    ours.getPrivate(), X25519KeyExchange.generateKeyPair().getPublic());

            // If these ever matched, generateKeyPair() would be returning a fixed key pair and
            // every session in the federation would share one transport secret.
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("a third party's private key derives a different secret")
        void anotherPartyCannotReachTheSecret() {
            // Attack: an eavesdropper who has both public keys and a key pair of their own.
            KeyPair client = X25519KeyExchange.generateKeyPair();
            KeyPair server = X25519KeyExchange.generateKeyPair();
            KeyPair eavesdropper = X25519KeyExchange.generateKeyPair();

            assertThat(X25519KeyExchange.agree(eavesdropper.getPrivate(), server.getPublic()))
                    .isNotEqualTo(X25519KeyExchange.agree(client.getPrivate(), server.getPublic()));
        }
    }

    @Nested
    @DisplayName("wire encoding")
    class WireEncoding {

        @Test
        @DisplayName("encode then decode gives back a key that agrees identically")
        void roundTrips() {
            KeyPair ours = X25519KeyExchange.generateKeyPair();
            KeyPair theirs = X25519KeyExchange.generateKeyPair();

            byte[] encoded = X25519KeyExchange.encodePublicKey(theirs.getPublic());
            PublicKey decoded = X25519KeyExchange.decodePublicKey(encoded);

            assertThat(encoded).hasSize(X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH);
            assertThat(X25519KeyExchange.encodePublicKey(decoded)).isEqualTo(encoded);
            assertThat(X25519KeyExchange.agree(ours.getPrivate(), decoded))
                    .isEqualTo(X25519KeyExchange.agree(ours.getPrivate(), theirs.getPublic()));
        }

        @Test
        @DisplayName("a null encoding is refused")
        void nullIsRefused() {
            // Attack: a truncated frame that leaves the key field absent, hoping "no key" reads as
            // "any key".
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(null))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a wrong-length encoding is refused before parsing")
        void wrongLengthIsRefused() {
            // Attack: a hostile length that a lenient parser might pad, truncate, or read past.
            for (int length : new int[] {0, 8, 32, 43, 45, 4096}) {
                byte[] wrong = new byte[length];
                assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(wrong))
                        .as("encoding of %d bytes", length)
                        .isInstanceOf(SecureChannelException.class);
            }
        }

        @Test
        @DisplayName("44 structurally invalid bytes are refused")
        void malformedDerIsRefused() {
            // Attack: get the length right and the contents wrong. The length check alone would let
            // this through, so the KeyFactory's parse has to be the thing that says no.
            byte[] allZero = new byte[X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH];
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(allZero))
                    .isInstanceOf(SecureChannelException.class);

            byte[] wrongHeader = X25519KeyExchange.encodePublicKey(
                    X25519KeyExchange.generateKeyPair().getPublic());
            wrongHeader[0] = 0x00; // not a DER SEQUENCE any more
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(wrongHeader))
                    .isInstanceOf(SecureChannelException.class);

            byte[] wrongCurve = X25519KeyExchange.encodePublicKey(
                    X25519KeyExchange.generateKeyPair().getPublic());
            wrongCurve[8] = 0x6f; // id-X448's OID final byte in place of id-X25519's
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(wrongCurve))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    @Nested
    @DisplayName("low-order points")
    class LowOrderPoints {

        @Test
        @DisplayName("§6.1 — an all-zero peer point is rejected, not agreed with")
        void allZeroPointIsRejected() {
            // Attack: send u = 0. It has small order, so the agreement produces the all-zero secret
            // no matter what private key we hold — meaning the attacker knows the session key
            // without knowing anything else. RFC 7748 §6.1 says to check for the all-zero result and
            // abort, and this is that check.
            //
            // Note for anyone tracing a failure here: on the JDK's SunEC provider the point is
            // refused during the agreement itself, before the explicit all-zero comparison in
            // agree() ever runs, and both paths surface as SecureChannelException. The explicit
            // check is defence in depth against a provider that is more permissive — deliberately
            // redundant, not dead.
            KeyPair ours = X25519KeyExchange.generateKeyPair();
            byte[] encoded = new byte[X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH];
            System.arraycopy(SPKI_PREFIX, 0, encoded, 0, SPKI_PREFIX.length);

            PublicKey lowOrder = X25519KeyExchange.decodePublicKey(encoded);

            assertThatThrownBy(() -> X25519KeyExchange.agree(ours.getPrivate(), lowOrder))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("u = 1 is rejected too")
        void otherSmallOrderPointsAreRejected() {
            // The order-1 point. RFC 7748 §7 lists the small-order inputs that force a known secret;
            // zero is the famous one, and it is not the only one.
            KeyPair ours = X25519KeyExchange.generateKeyPair();
            PublicKey lowOrder = publicKeyFromU("0100000000000000000000000000000000000000000000000000000000000000");

            assertThatThrownBy(() -> X25519KeyExchange.agree(ours.getPrivate(), lowOrder))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    @Nested
    @DisplayName("key generation")
    class KeyGeneration {

        @Test
        @DisplayName("generated keys carry the X25519 OID, whatever they call themselves")
        void generatedKeysAreX25519() {
            KeyPair pair = X25519KeyExchange.generateKeyPair();

            // The same JCA trap as on the Ed25519 side: a key generated for "X25519" reports its
            // family, "XDH", from getAlgorithm() — X448 keys say "XDH" too. The curve is only
            // identifiable from the encoding, where the id-X25519 OID sits in the SPKI header. So
            // that header, not the name, is what this test checks.
            assertThat(pair.getPublic().getAlgorithm()).isEqualTo("XDH");
            assertThat(pair.getPublic().getEncoded()).hasSize(X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH);
            assertThat(Arrays.copyOf(pair.getPublic().getEncoded(), SPKI_PREFIX.length))
                    .isEqualTo(SPKI_PREFIX);
        }

        @Test
        @DisplayName("successive calls give different keys")
        void generationIsNotFixed() {
            assertThat(X25519KeyExchange.encodePublicKey(
                            X25519KeyExchange.generateKeyPair().getPublic()))
                    .isNotEqualTo(X25519KeyExchange.encodePublicKey(
                            X25519KeyExchange.generateKeyPair().getPublic()));
        }

        @Test
        @DisplayName("the declared sizes are the ones the algorithm produces")
        void constantsMatchTheAlgorithm() {
            assertThat(X25519KeyExchange.ALGORITHM).isEqualTo("X25519");
            assertThat(X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH).isEqualTo(44);
            assertThat(X25519KeyExchange.SHARED_SECRET_LENGTH).isEqualTo(32);
        }
    }
}
