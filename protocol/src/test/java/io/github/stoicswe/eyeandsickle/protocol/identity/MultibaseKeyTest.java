package io.github.stoicswe.eyeandsickle.protocol.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Decoding a DID document's {@code publicKeyMultibase}.
 *
 * <h2>Why these tests sign rather than compare keys</h2>
 *
 * Asserting that a decoded key {@code equals} the original proves the bytes survived the trip. It
 * does <em>not</em> prove the key works — and the JDK trap this class exists to avoid produces a key
 * that compares fine, reports the right algorithm, initialises a {@code Signature} without complaint,
 * and only fails at {@code verify()}. So the assertion that matters is a real signature verified with
 * the decoded key: the first thing that would break if the curve were reconstructed wrongly.
 */
class MultibaseKeyTest {

    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    /** The encoder, so the tests build their own vectors instead of trusting a pasted literal. */
    private static String multibase(int codec, byte[] key) {
        byte[] varint = codec < 0x80
                ? new byte[] {(byte) codec}
                : new byte[] {(byte) ((codec & 0x7F) | 0x80), (byte) (codec >> 7)};
        byte[] all = new byte[varint.length + key.length];
        System.arraycopy(varint, 0, all, 0, varint.length);
        System.arraycopy(key, 0, all, varint.length, key.length);

        StringBuilder out = new StringBuilder();
        BigInteger value = new BigInteger(1, all);
        BigInteger radix = BigInteger.valueOf(58);
        while (value.signum() > 0) {
            BigInteger[] divmod = value.divideAndRemainder(radix);
            out.append(BASE58.charAt(divmod[1].intValue()));
            value = divmod[0];
        }
        for (byte b : all) {
            if (b != 0) {
                break;
            }
            out.append('1');
        }
        return "z" + out.reverse();
    }

    /** SEC 1 point compression: 0x02/0x03 by y's parity, then x, left-padded to the field size. */
    private static byte[] compress(ECPublicKey key) {
        byte[] x = key.getW().getAffineX().toByteArray();
        byte[] fixed = new byte[32];
        int from = Math.max(0, x.length - 32);
        System.arraycopy(x, from, fixed, 32 - (x.length - from), x.length - from);
        byte[] out = new byte[33];
        out[0] = (byte) (key.getW().getAffineY().testBit(0) ? 0x03 : 0x02);
        System.arraycopy(fixed, 0, out, 1, 32);
        return out;
    }

    private static KeyPair ecKeyPair(String curve) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static void assertSignsAndVerifies(KeyPair original, MultibaseKey.AtprotoKey decoded, String algorithm)
            throws Exception {
        byte[] message = "the eye and the sickle".getBytes();
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(original.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(decoded.key());
        verifier.update(message);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Nested
    @DisplayName("round trips that actually verify a signature")
    class RoundTrips {

        @Test
        @DisplayName("p256-pub (0x1200) — a two-byte varint")
        void p256() throws Exception {
            KeyPair pair = ecKeyPair("secp256r1");
            String encoded = multibase(0x1200, compress((ECPublicKey) pair.getPublic()));

            MultibaseKey.AtprotoKey decoded = MultibaseKey.decode(encoded);

            assertThat(decoded.jwsAlgorithm()).isEqualTo("ES256");
            assertThat(decoded.key()).isEqualTo(pair.getPublic());
            assertSignsAndVerifies(pair, decoded, "SHA256withECDSA");
        }

        @Test
        @DisplayName("secp256k1-pub (0xe7) — the curve most did:plc accounts actually sign with")
        void secp256k1() throws Exception {
            // ⚠ SKIPPED, not passed, on a JVM without the curve — stock OpenJDK is one. A test that
            // reported success here without executing would be worse than none, which is the same
            // rule NodeMenuTest follows for the absent JavaFX toolkit.
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    MultibaseKey.secp256k1Available(),
                    "this JVM's provider cannot verify secp256k1 — see MultibaseKey's class comment");

            KeyPair pair = ecKeyPair("secp256k1");
            String encoded = multibase(0xE7, compress((ECPublicKey) pair.getPublic()));

            MultibaseKey.AtprotoKey decoded = MultibaseKey.decode(encoded);

            assertThat(decoded.jwsAlgorithm()).isEqualTo("ES256K");
            assertSignsAndVerifies(pair, decoded, "SHA256withECDSA");
        }

        @Test
        @DisplayName("ed25519-pub (0xed) — what our own servers publish for provenance")
        void ed25519() throws Exception {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            // The X.509 encoding's last 32 bytes are the raw key; that is what multicodec wraps.
            byte[] encodedKey = pair.getPublic().getEncoded();
            byte[] raw = Arrays.copyOfRange(encodedKey, encodedKey.length - 32, encodedKey.length);

            MultibaseKey.AtprotoKey decoded = MultibaseKey.decode(multibase(0xED, raw));

            assertThat(decoded.jwsAlgorithm()).isEqualTo("EdDSA");
            assertSignsAndVerifies(pair, decoded, "Ed25519");
        }
    }

    @Nested
    @DisplayName("the JDK trap this class exists for")
    class JdkBehaviour {

        @Test
        @DisplayName("⚠ the cheap availability checks LIE about secp256k1 — this is the whole trap")
        void curveNameResolvesEvenWhereVerificationCannot() throws Exception {
            // The finding, pinned. On stock OpenJDK both of these succeed and initVerify then fails;
            // on Semeru/OpenJ9 all three succeed. So neither of these two calls can be used to decide
            // whether the curve is usable — which is exactly what a reasonable person would reach for.
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256k1"));
            ECParameterSpec named = parameters.getParameterSpec(ECParameterSpec.class);
            assertThat(named).isNotNull();

            var key = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.ECPublicKeySpec(named.getGenerator(), named));
            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("EC");

            // ⚠ initVerify succeeds too — on BOTH runtimes. It was this code's first probe and it
            // reported the curve as usable on the JVM where it is not.
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(key);
            signature.update(new byte[] {0});

            // Only verify() tells the truth. Both outcomes are legitimate depending on the JVM, so
            // this asserts the AGREEMENT between the probe and reality rather than one answer.
            boolean verifyWorks;
            try {
                signature.verify(new byte[] {0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01});
                verifyWorks = true;
            } catch (Exception refused) {
                verifyWorks = false;
            }
            assertThat(MultibaseKey.secp256k1Available())
                    .as("secp256k1Available() must track verify(), not any of the three calls above")
                    .isEqualTo(verifyWorks);
        }

        @Test
        @DisplayName("decoding a key the JVM cannot verify with is refused UP FRONT, with the cause named")
        void unusableCurveIsRefusedEarly() {
            // Without this, a stock-OpenJDK server resolves a did:plc account, builds its key
            // successfully, and only discovers the problem when a federated signature arrives — with
            // an exception naming neither the curve nor the cause.
            byte[] anyPoint = new byte[33];
            anyPoint[0] = 0x02;

            if (MultibaseKey.secp256k1Available()) {
                // The curve works here, so this input fails for the OTHER reason: it is not a point.
                assertThatThrownBy(() -> MultibaseKey.decode(multibase(0xE7, anyPoint)))
                        .isInstanceOf(IdentityResolutionException.class)
                        .hasMessageContaining("not on curve");
            } else {
                assertThatThrownBy(() -> MultibaseKey.decode(multibase(0xE7, anyPoint)))
                        .isInstanceOf(IdentityResolutionException.class)
                        .hasMessageContaining("cannot verify secp256k1")
                        .extracting(e -> ((IdentityResolutionException) e).kind())
                        .isEqualTo(IdentityResolutionException.Kind.REFUSED_BY_POLICY);
            }
        }

        @Test
        @DisplayName("P-256 IS available everywhere — DPoP and provenance are unaffected")
        void p256IsAlwaysThere() {
            // The blast radius of the secp256k1 gap. DPoP is ES256/P-256 and provenance is Ed25519,
            // so only service-auth JWTs from k256 accounts are affected.
            assertThat(MultibaseKey.decode(multibase(0x1200, compressedGenerator("secp256r1")))
                            .jwsAlgorithm())
                    .isEqualTo("ES256");
        }

        private static byte[] compressedGenerator(String curve) {
            try {
                AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
                parameters.init(new ECGenParameterSpec(curve));
                ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
                var generator = spec.getGenerator();
                byte[] x = generator.getAffineX().toByteArray();
                byte[] fixed = new byte[32];
                int from = Math.max(0, x.length - 32);
                System.arraycopy(x, from, fixed, 32 - (x.length - from), x.length - from);
                byte[] out = new byte[33];
                out[0] = (byte) (generator.getAffineY().testBit(0) ? 0x03 : 0x02);
                System.arraycopy(fixed, 0, out, 1, 32);
                return out;
            } catch (Exception impossible) {
                throw new AssertionError("P-256 must be available on every JVM", impossible);
            }
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a point that is not on the curve is refused, not turned into a key")
        void offCurvePointIsRefused() {
            // modPow always returns something, so without the explicit square check an arbitrary
            // 32-byte string becomes a "key" off the curve — an invalid-curve attack for free.
            byte[] notAPoint = new byte[33];
            notAPoint[0] = 0x02;
            Arrays.fill(notAPoint, 1, 33, (byte) 0x11);

            assertThatThrownBy(() -> MultibaseKey.decode(multibase(0x1200, notAPoint)))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("not on curve");
        }

        @Test
        @DisplayName("a non-base58btc multibase prefix is refused rather than guessed at")
        void wrongMultibasePrefix() {
            assertThatThrownBy(() -> MultibaseKey.decode("mQ3shXjHei"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("base58btc");
        }

        @Test
        @DisplayName("characters base58btc excludes are refused")
        void ambiguousCharactersRefused() {
            // 0, O, I and l are absent from the alphabet precisely because they are confusable.
            assertThatThrownBy(() -> MultibaseKey.decode("zO0Il"))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("base58btc character");
        }

        @Test
        @DisplayName("an unknown multicodec is refused")
        void unknownCodec() {
            assertThatThrownBy(() -> MultibaseKey.decode(multibase(0x99, new byte[33])))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("unsupported multicodec");
        }

        @Test
        @DisplayName("an UNCOMPRESSED point is refused — atproto encodes compressed keys")
        void uncompressedPointRefused() {
            byte[] uncompressed = new byte[65];
            uncompressed[0] = 0x04;

            assertThatThrownBy(() -> MultibaseKey.decode(multibase(0x1200, uncompressed)))
                    .isInstanceOf(IdentityResolutionException.class)
                    .hasMessageContaining("33-byte compressed point");
        }

        @Test
        @DisplayName("null and blank are refused without a NullPointerException")
        void nothingAtAll() {
            assertThatThrownBy(() -> MultibaseKey.decode(null)).isInstanceOf(IdentityResolutionException.class);
            assertThatThrownBy(() -> MultibaseKey.decode("")).isInstanceOf(IdentityResolutionException.class);
        }
    }
}
