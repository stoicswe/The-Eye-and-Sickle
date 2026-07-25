package io.github.stoicswe.eyeandsickle.protocol.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Standalone tests for {@link AeadSeal}.
 *
 * <p>{@code SecureChannelTest} shows that a whole session is tamper-evident. This suite pins the
 * primitive underneath, one property at a time, so that a failure says which property broke instead
 * of "the handshake stopped working".
 *
 * <h2>The one thing this class exists to prevent</h2>
 *
 * {@link AeadSeal} refuses to generate nonces, and that refusal is the reason for
 * {@link NonceDiscipline#nonceReuseLeaksPlaintext()} below — a test that performs the attack rather
 * than asserting that it is bad. GCM is not merely weakened by nonce reuse the way a block cipher in
 * CTR mode is. Encrypting two messages under one key and nonce hands the attacker the XOR of the
 * plaintexts <em>and</em> enough information to recover the GHASH authentication subkey, after which
 * they can forge arbitrary authenticated messages on that key for as long as it lives. There is no
 * "somewhat degraded" state in between.
 *
 * <p>{@code docs/architecture/07-transport-security.md} §4.3 is where the counter-based construction
 * is specified and where the reasoning lives.
 */
class AeadSealTest {

    private static final byte[] KEY = key((byte) 0x2a);
    private static final byte[] NONCE = AeadSeal.nonceForCounter(0);
    private static final byte[] HEADER = {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] PLAINTEXT = "{\"op\":\"collect\",\"minerId\":17}".getBytes(StandardCharsets.UTF_8);

    private static byte[] key(byte fill) {
        byte[] key = new byte[AeadSeal.KEY_LENGTH];
        Arrays.fill(key, fill);
        return key;
    }

    @Nested
    @DisplayName("sealing and opening")
    class RoundTrip {

        @Test
        @DisplayName("a sealed message opens back to the original plaintext")
        void roundTrips() {
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);

            assertThat(AeadSeal.open(KEY, NONCE, HEADER, sealed)).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("ciphertext is the plaintext plus a 16-byte tag, and nothing more")
        void ciphertextExpansionIsExactlyTheTag() {
            // Pinned because the frame layout depends on it: a reader that budgeted for a different
            // expansion would mis-slice frames, and GCM's expansion is not obvious from the API.
            for (int length : new int[] {0, 1, 15, 16, 17, 4096}) {
                byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, new byte[length]);
                assertThat(sealed).as("plaintext of %d bytes", length).hasSize(length + AeadSeal.TAG_LENGTH);
            }
            assertThat(AeadSeal.TAG_LENGTH).isEqualTo(16);
        }

        @Test
        @DisplayName("an empty plaintext still produces an authenticated frame")
        void emptyPlaintext() {
            // A zero-length message is still a message — a keepalive, an empty ack — and it must
            // still be authenticated rather than becoming a free-to-forge frame.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, new byte[0]);

            assertThat(sealed).hasSize(AeadSeal.TAG_LENGTH);
            assertThat(AeadSeal.open(KEY, NONCE, HEADER, sealed)).isEmpty();
        }

        @Test
        @DisplayName("null associated data means no associated data, symmetrically")
        void nullAssociatedData() {
            byte[] sealed = AeadSeal.seal(KEY, NONCE, null, PLAINTEXT);

            assertThat(AeadSeal.open(KEY, NONCE, null, sealed)).isEqualTo(PLAINTEXT);
            // GCM cannot distinguish "no AAD" from "empty AAD", so the two must be interchangeable.
            // Worth knowing rather than discovering: a caller cannot use the difference as a signal.
            assertThat(AeadSeal.open(KEY, NONCE, new byte[0], sealed)).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("the same plaintext under different nonces gives different ciphertext")
        void nonceChangesCiphertext() {
            byte[] first = AeadSeal.seal(KEY, AeadSeal.nonceForCounter(0), HEADER, PLAINTEXT);
            byte[] second = AeadSeal.seal(KEY, AeadSeal.nonceForCounter(1), HEADER, PLAINTEXT);

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("tamper detection")
    class TamperDetection {

        @Test
        @DisplayName("flipping any bit of the ciphertext or tag makes it unopenable")
        void anyBitFlipIsDetected() {
            // Attack: alter a message in flight — change an amount, retarget a transfer, or grind
            // for a tag that happens to validate. Every byte must be covered, including the tag's,
            // which is what makes forging cost 2^128 attempts rather than 2^7.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);

            for (int i = 0; i < sealed.length; i++) {
                byte[] corrupted = sealed.clone();
                corrupted[i] ^= 0x01;
                int index = i;
                assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, HEADER, corrupted))
                        .as("byte %d was mutable without detection", index)
                        .isInstanceOf(SecureChannelException.class);
            }
        }

        @Test
        @DisplayName("altering the associated data makes it unopenable")
        void modifiedAssociatedDataIsDetected() {
            // Attack: leave the ciphertext alone and rewrite the frame header — renumber the
            // sequence to replay a frame, or flip the direction byte to reflect it at its sender.
            // The header is authenticated but not encrypted precisely so that this fails.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);
            byte[] renumbered = HEADER.clone();
            renumbered[renumbered.length - 1] ^= 0x01;

            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, renumbered, sealed))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("dropping the associated data entirely makes it unopenable")
        void strippedAssociatedDataIsDetected() {
            // Attack: strip the header instead of altering it, in case the verifier treats "absent"
            // as "nothing to check".
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);

            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, null, sealed))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a different key cannot open the frame")
        void wrongKeyIsDetected() {
            // Attack: a machine-in-the-middle that completed its own handshake and holds a session
            // key — just not this session's.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);

            assertThatThrownBy(() -> AeadSeal.open(key((byte) 0x2b), NONCE, HEADER, sealed))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a different nonce cannot open the frame")
        void wrongNonceIsDetected() {
            // Attack: replay frame 7 as frame 8. The nonce is derived from the sequence number, so
            // a replayed frame is decrypted under the wrong nonce and fails its tag.
            byte[] sealed = AeadSeal.seal(KEY, AeadSeal.nonceForCounter(7), HEADER, PLAINTEXT);

            assertThatThrownBy(() -> AeadSeal.open(KEY, AeadSeal.nonceForCounter(8), HEADER, sealed))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("truncating a frame makes it unopenable")
        void truncationIsDetected() {
            // Attack: cut the last byte off, hoping a length-tolerant decryptor returns the prefix.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);

            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, HEADER, Arrays.copyOf(sealed, sealed.length - 1)))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a frame too short to hold a tag is refused before decryption")
        void undersizedFrameIsRefused() {
            // Attack: a length-prefix or slicing bug turned into a crash. Anything shorter than the
            // tag cannot be a frame, and the guard runs before the cipher gets a chance to react.
            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, HEADER, new byte[AeadSeal.TAG_LENGTH - 1]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, HEADER, new byte[0]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> AeadSeal.open(KEY, NONCE, HEADER, null))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("every failure reports the same thing")
        void failuresAreIndistinguishable() {
            // The oracle defence from SecureChannelException's class doc, checked rather than
            // assumed. An attacker who could tell "wrong key" from "bad tag" from "too short" would
            // learn how far their forgery got, and could grind toward a valid one.
            byte[] sealed = AeadSeal.seal(KEY, NONCE, HEADER, PLAINTEXT);
            byte[] corrupted = sealed.clone();
            corrupted[0] ^= 0x01;

            List<String> messages = new ArrayList<>();
            for (Runnable attempt : List.<Runnable>of(
                    () -> AeadSeal.open(key((byte) 0x2b), NONCE, HEADER, sealed),
                    () -> AeadSeal.open(KEY, AeadSeal.nonceForCounter(9), HEADER, sealed),
                    () -> AeadSeal.open(KEY, NONCE, new byte[] {0x7f}, sealed),
                    () -> AeadSeal.open(KEY, NONCE, HEADER, corrupted),
                    () -> AeadSeal.open(KEY, NONCE, HEADER, new byte[4]))) {
                try {
                    attempt.run();
                    throw new AssertionError("expected the frame to be rejected");
                } catch (SecureChannelException e) {
                    messages.add(e.getMessage());
                }
            }

            assertThat(messages).hasSize(5).containsOnly("Frame rejected");
        }
    }

    @Nested
    @DisplayName("key and nonce validation")
    class KeyAndNonceValidation {

        @Test
        @DisplayName("a key that is not 32 bytes is refused")
        void wrongKeyLengthIsRefused() {
            // Not an attack so much as the bug that precedes one: a 16-byte key here would silently
            // be AES-128 in a system documented as AES-256, or a truncated derivation nobody
            // noticed. IllegalArgumentException rather than SecureChannelException on purpose —
            // this is a programming error on our side of the wire, not a hostile peer.
            for (int length : new int[] {0, 16, 31, 33, 64}) {
                byte[] wrong = new byte[length];
                assertThatThrownBy(() -> AeadSeal.seal(wrong, NONCE, HEADER, PLAINTEXT))
                        .as("key of %d bytes", length)
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> AeadSeal.open(wrong, NONCE, HEADER, new byte[32]))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> AeadSeal.seal(null, NONCE, HEADER, PLAINTEXT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a nonce that is not 12 bytes is refused")
        void wrongNonceLengthIsRefused() {
            // GCM accepts other nonce sizes by hashing them down to 96 bits, which is a silent
            // change of construction. Refusing anything but 12 keeps one construction in play.
            for (int length : new int[] {0, 8, 11, 13, 16}) {
                byte[] wrong = new byte[length];
                assertThatThrownBy(() -> AeadSeal.seal(KEY, wrong, HEADER, PLAINTEXT))
                        .as("nonce of %d bytes", length)
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> AeadSeal.open(KEY, wrong, HEADER, new byte[32]))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> AeadSeal.seal(KEY, null, HEADER, PLAINTEXT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the declared sizes are the ones the algorithm requires")
        void constantsMatchTheAlgorithm() {
            assertThat(AeadSeal.KEY_LENGTH).isEqualTo(32);
            assertThat(AeadSeal.NONCE_LENGTH).isEqualTo(12);
            assertThat(AeadSeal.TAG_LENGTH_BITS).isEqualTo(128);
            assertThat(AeadSeal.TAG_LENGTH).isEqualTo(AeadSeal.TAG_LENGTH_BITS / 8);
        }
    }

    @Nested
    @DisplayName("nonce discipline")
    class NonceDiscipline {

        @Test
        @DisplayName("a nonce is 12 bytes of big-endian counter behind four zero bytes")
        void nonceLayout() {
            assertThat(AeadSeal.nonceForCounter(0))
                    .hasSize(AeadSeal.NONCE_LENGTH)
                    .containsOnly((byte) 0);
            // Big-endian, so 258 is 0x01 0x02 in the last two bytes rather than the reverse. The
            // layout is on the wire in effect — both peers must build the same nonce for the same
            // sequence number — so it is pinned here rather than left to ByteBuffer's default.
            assertThat(AeadSeal.nonceForCounter(258)).isEqualTo(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x02});
            assertThat(AeadSeal.nonceForCounter(1)).isEqualTo(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01});
            assertThat(AeadSeal.nonceForCounter(Long.MAX_VALUE))
                    .isEqualTo(new byte[] {0, 0, 0, 0, 0x7f, -1, -1, -1, -1, -1, -1, -1});
        }

        @Test
        @DisplayName("distinct counters give distinct nonces")
        void nonceIsInjective() {
            // The whole safety argument rests on this one function being injective. If two counters
            // could ever map to one nonce, the session would reuse a nonce under a live key.
            Set<String> seen = new HashSet<>();
            List<Long> counters = new ArrayList<>();
            for (long i = 0; i < 2048; i++) {
                counters.add(i);
            }
            // Plus the byte, int and long boundaries, where a carry bug would show up.
            counters.add(0x1_0000L);
            counters.add(0xFFFF_FFFFL);
            counters.add(0x1_0000_0000L);
            counters.add(0x1_0000_0000_0000L);
            counters.add(Long.MAX_VALUE - 1);
            counters.add(Long.MAX_VALUE);

            for (long counter : counters) {
                assertThat(seen.add(Arrays.toString(AeadSeal.nonceForCounter(counter))))
                        .as("counter %d collided with an earlier nonce", counter)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("a negative counter is refused")
        void negativeCounterIsRefused() {
            // The only way to reach a negative counter is a long overflow after 2^63 frames, which
            // would wrap the sequence back through values already used — i.e. nonce reuse. Refusing
            // turns a catastrophic silent failure into a loud one.
            assertThatThrownBy(() -> AeadSeal.nonceForCounter(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("overflow");
            assertThatThrownBy(() -> AeadSeal.nonceForCounter(Long.MIN_VALUE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("why this class refuses to pick nonces: reuse hands over the plaintext")
        void nonceReuseLeaksPlaintext() {
            // Performed rather than asserted, because "nonce reuse is bad" is the kind of statement
            // that gets traded away in a refactor by someone who has never seen it cash out.
            //
            // GCM is counter mode underneath: ciphertext = plaintext XOR keystream(key, nonce). Use
            // one nonce twice and the keystream cancels, so anyone holding both ciphertexts gets the
            // XOR of the plaintexts — and with one known plaintext, the other outright. (The second
            // half of the damage is worse and not shown here: the same reuse also exposes the GHASH
            // subkey, which turns eavesdropping into unlimited forgery under that key.)
            byte[] secret = "transfer 5000 ec to did:plc:me".getBytes(StandardCharsets.UTF_8);
            byte[] known = new byte[secret.length];
            Arrays.fill(known, (byte) 'A');

            byte[] sealedSecret = AeadSeal.seal(KEY, NONCE, HEADER, secret);
            byte[] sealedKnown = AeadSeal.seal(KEY, NONCE, HEADER, known);

            byte[] recovered = new byte[secret.length];
            for (int i = 0; i < secret.length; i++) {
                recovered[i] = (byte) (sealedSecret[i] ^ sealedKnown[i] ^ known[i]);
            }

            assertThat(new String(recovered, StandardCharsets.UTF_8))
                    .as("if this ever fails to recover the plaintext, GCM has changed, not our luck")
                    .isEqualTo("transfer 5000 ec to did:plc:me");
        }
    }
}
