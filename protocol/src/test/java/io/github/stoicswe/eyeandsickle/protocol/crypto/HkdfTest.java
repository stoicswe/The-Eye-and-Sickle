package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Conformance tests for {@link Hkdf} against the RFC 5869 published test vectors.
 *
 * <h2>Why the vectors matter more than the round trip</h2>
 *
 * Every other test in this module can be satisfied by an implementation that is self-consistently
 * wrong. A key-derivation round trip passes if both ends compute the same garbage. These vectors
 * cannot: they are fixed output for fixed input, published in 2010, matched by every correct HKDF in
 * every language. Passing them is the closest this codebase gets to a proof that a home server
 * written in something other than Java can join the federation and derive the same session keys.
 *
 * <p>That matters because the project is explicitly federated and self-hostable
 * ({@code docs/architecture/03-server-and-federation.md}): the second implementation is not
 * hypothetical, it is the plan.
 *
 * <p>Only the SHA-256 cases (A.1–A.3) are transcribed. The RFC's remaining cases (A.4–A.7) use
 * SHA-1, which {@code docs/architecture/07-transport-security.md} §4.4 does not use and which
 * nothing here should be able to select.
 */
class HkdfTest {

    private static final HexFormat HEX = HexFormat.of();

    /** Strips the RFC's line grouping so a vector can be transcribed exactly as it is printed. */
    private static byte[] hex(String grouped) {
        return HEX.parseHex(grouped.replaceAll("\\s", ""));
    }

    @Nested
    @DisplayName("RFC 5869 test vectors")
    class Rfc5869Vectors {

        @Test
        @DisplayName("A.1 — basic case with SHA-256")
        void testCase1() {
            byte[] ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
            byte[] salt = hex("000102030405060708090a0b0c");
            byte[] info = hex("f0f1f2f3f4f5f6f7f8f9");

            byte[] okm = Hkdf.derive(salt, ikm, info, 42);

            assertThat(okm).isEqualTo(hex("""
                            3cb25f25faacd57a90434f64d0362f2a
                            2d2d0a90cf1a5a4c5db02d56ecc4c5bf
                            34007208d5b887185865
                            """));
        }

        @Test
        @DisplayName("A.2 — longer inputs and outputs, spanning several expand blocks")
        void testCase2() {
            // 82 output bytes is three HMAC blocks, so this vector is the one that catches a broken
            // expand loop: an implementation that forgets to feed block N-1 back into block N still
            // passes A.1, where 42 bytes fits in two blocks and the error is easy to miss.
            byte[] ikm = hex("""
                    000102030405060708090a0b0c0d0e0f
                    101112131415161718191a1b1c1d1e1f
                    202122232425262728292a2b2c2d2e2f
                    303132333435363738393a3b3c3d3e3f
                    404142434445464748494a4b4c4d4e4f
                    """);
            byte[] salt = hex("""
                    606162636465666768696a6b6c6d6e6f
                    707172737475767778797a7b7c7d7e7f
                    808182838485868788898a8b8c8d8e8f
                    909192939495969798999a9b9c9d9e9f
                    a0a1a2a3a4a5a6a7a8a9aaabacadaeaf
                    """);
            byte[] info = hex("""
                    b0b1b2b3b4b5b6b7b8b9babbbcbdbebf
                    c0c1c2c3c4c5c6c7c8c9cacbcccdcecf
                    d0d1d2d3d4d5d6d7d8d9dadbdcdddedf
                    e0e1e2e3e4e5e6e7e8e9eaebecedeeef
                    f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff
                    """);

            byte[] okm = Hkdf.derive(salt, ikm, info, 82);

            assertThat(okm).isEqualTo(hex("""
                            b11e398dc80327a1c8e7f78c596a4934
                            4f012eda2d4efad8a050cc4c19afa97c
                            59045a99cac7827271cb41c65e590e09
                            da3275600c2f09b8367793a9aca3db71
                            cc30c58179ec3e87c14c01d5c1f3434f
                            1d87
                            """));
        }

        @Test
        @DisplayName("A.3 — zero-length salt and info")
        void testCase3() {
            // The degenerate case, and the one implementations get wrong. RFC 5869 §2.2 says an
            // absent salt means HashLen zero bytes; HMAC then pads that key with zeros to the block
            // size, which is also what it does to a zero-length key. So "no salt" and "empty salt"
            // must agree, and this vector is what says they do rather than us assuming it.
            byte[] ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");

            byte[] okm = Hkdf.derive(new byte[0], ikm, new byte[0], 42);

            assertThat(okm).isEqualTo(hex("""
                            8da4e775a563c18f715f802a063c5a31
                            b8a11f5c5ee1879ec3454e5f3c738d2d
                            9d201395faa4b61a96c8
                            """));
        }
    }

    @Nested
    @DisplayName("domain separation")
    class DomainSeparation {

        private static final byte[] SALT = "transcript-hash-stand-in".getBytes(StandardCharsets.UTF_8);
        private static final byte[] IKM = "dh1||dh2||dh3".getBytes(StandardCharsets.UTF_8);

        @Test
        @DisplayName("different info gives independent output — this is what separates the two directions")
        void infoSeparatesOutputs() {
            // The property docs/architecture/07-transport-security.md §4.2 leans on: one shared
            // secret, one key per direction. If these two derivations agreed, a frame captured from
            // the server could be replayed at the server as though the client had sent it, and the
            // direction byte in the header would be the only thing standing in the way.
            byte[] clientToServer = Hkdf.derive(SALT, IKM, "c2s".getBytes(StandardCharsets.UTF_8), 32);
            byte[] serverToClient = Hkdf.derive(SALT, IKM, "s2c".getBytes(StandardCharsets.UTF_8), 32);

            assertThat(clientToServer).isNotEqualTo(serverToClient);
            // Not merely unequal: independent. A one-character label change must not shift the
            // output by a byte or two, which a naive "append the label" construction would.
            assertThat(sharedPrefixLength(clientToServer, serverToClient))
                    .as("outputs share a prefix, which suggests the label is not truly mixed in")
                    .isLessThan(4);
        }

        @Test
        @DisplayName("different salt gives different output — this is what binds keys to the transcript")
        void saltSeparatesOutputs() {
            // The salt is the handshake transcript hash. Change one byte anywhere in the handshake
            // and the two peers derive different keys, so a tampered handshake dies at the first
            // frame instead of silently continuing in whatever state the attacker steered it into.
            byte[] info = "c2s".getBytes(StandardCharsets.UTF_8);
            byte[] fromOneTranscript = Hkdf.derive(SALT, IKM, info, 32);
            byte[] fromATamperedTranscript =
                    Hkdf.derive("transcript-hash-stand-iN".getBytes(StandardCharsets.UTF_8), IKM, info, 32);

            assertThat(fromOneTranscript).isNotEqualTo(fromATamperedTranscript);
        }

        @Test
        @DisplayName("different input key material gives different output")
        void ikmSeparatesOutputs() {
            byte[] info = "c2s".getBytes(StandardCharsets.UTF_8);

            assertThat(Hkdf.derive(SALT, IKM, info, 32))
                    .isNotEqualTo(Hkdf.derive(SALT, "different dh output".getBytes(StandardCharsets.UTF_8), info, 32));
        }

        @Test
        @DisplayName("the same inputs always give the same output")
        void derivationIsDeterministic() {
            // Obvious, and load-bearing: the two peers never exchange the derived key, they each
            // compute it. Any nondeterminism here would be a handshake that fails at random.
            byte[] info = "c2s".getBytes(StandardCharsets.UTF_8);

            assertThat(Hkdf.derive(SALT, IKM, info, 48)).isEqualTo(Hkdf.derive(SALT, IKM, info, 48));
        }

        private static int sharedPrefixLength(byte[] a, byte[] b) {
            int i = 0;
            while (i < a.length && i < b.length && a[i] == b[i]) {
                i++;
            }
            return i;
        }
    }

    @Nested
    @DisplayName("output length")
    class OutputLength {

        private static final byte[] SALT = new byte[16];
        private static final byte[] IKM = "input key material".getBytes(StandardCharsets.UTF_8);
        private static final byte[] INFO = "label".getBytes(StandardCharsets.UTF_8);

        @Test
        @DisplayName("the requested length is honoured exactly")
        void lengthIsHonoured() {
            for (int length : new int[] {1, 16, 31, 32, 33, 64, 255, 1024}) {
                assertThat(Hkdf.derive(SALT, IKM, INFO, length))
                        .as("requested %d bytes", length)
                        .hasSize(length);
            }
        }

        @Test
        @DisplayName("a shorter request is a prefix of a longer one")
        void shorterOutputIsAPrefix() {
            // A property of HKDF-Expand rather than of this wrapper, but worth pinning: it means a
            // caller cannot get "fresh" key material by asking for fewer bytes. Two keys must come
            // from two info labels, never from two length requests.
            byte[] shortOutput = Hkdf.derive(SALT, IKM, INFO, 16);
            byte[] longOutput = Hkdf.derive(SALT, IKM, INFO, 64);

            assertThat(java.util.Arrays.copyOf(longOutput, 16)).isEqualTo(shortOutput);
        }

        @Test
        @DisplayName("the HKDF-SHA256 ceiling of 255 hash blocks is accepted")
        void ceilingIsAccepted() {
            assertThat(Hkdf.derive(SALT, IKM, INFO, 8160)).hasSize(8160);
        }

        @Test
        @DisplayName("a request past the ceiling is refused")
        void pastCeilingIsRefused() {
            // 255 * 32 is HKDF's hard limit. Past it the counter byte in Expand wraps, so an
            // implementation that did not check would start repeating earlier blocks — handing out
            // two "independent" keys that are the same bytes.
            assertThatThrownBy(() -> Hkdf.derive(SALT, IKM, INFO, 8161))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8161");
        }

        @Test
        @DisplayName("zero and negative lengths are refused")
        void nonPositiveLengthsAreRefused() {
            // A zero-length key is not a key. Refusing here means the mistake surfaces at the
            // derivation site rather than as an empty AES key several layers away.
            assertThatThrownBy(() -> Hkdf.derive(SALT, IKM, INFO, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Hkdf.derive(SALT, IKM, INFO, -1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Hkdf.derive(SALT, IKM, INFO, Integer.MIN_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
