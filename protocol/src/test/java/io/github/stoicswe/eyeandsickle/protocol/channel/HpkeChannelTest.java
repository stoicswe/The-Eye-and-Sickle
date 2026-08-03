package io.github.stoicswe.eyeandsickle.protocol.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The three properties {@code docs/architecture/07-transport-security.md} §1 asks for, each asserted
 * against real BouncyCastle rather than against a fixture.
 *
 * <p>These were run as a standalone probe <em>before</em> {@link HpkeChannel} was written, to confirm
 * RFC 9180 could carry the requirement at all. They are kept as tests because "the library does this"
 * is a claim that should keep being checked across upgrades.
 */
class HpkeChannelTest {

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record Pair(HpkeChannel initiator, HpkeChannel responder) {}

    private static Pair session() {
        AsymmetricCipherKeyPair server = HpkeChannel.generateStaticKeyPair();
        AsymmetricCipherKeyPair client = HpkeChannel.generateStaticKeyPair();
        HpkeChannel initiator = HpkeChannel.initiate(server.getPublic(), client);
        HpkeChannel responder = HpkeChannel.respond(initiator.encapsulation(), server, client.getPublic());
        return new Pair(initiator, responder);
    }

    @Nested
    @DisplayName("encrypted")
    class Encrypted {

        @Test
        @DisplayName("a sealed frame round-trips")
        void roundTrip() {
            Pair session = session();

            byte[] sealed = session.initiator().seal(utf8("allocate 12 cycles"), utf8("intent"));

            assertThat(new String(session.responder().open(sealed, utf8("intent")), StandardCharsets.UTF_8))
                    .isEqualTo("allocate 12 cycles");
        }

        @Test
        @DisplayName("the plaintext is not in the frame")
        void notInTheClear() {
            Pair session = session();

            byte[] sealed = session.initiator().seal(utf8("the-secret-intent"), null);

            assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("the-secret-intent");
        }

        @Test
        @DisplayName("tampering is detected")
        void tampering() {
            Pair session = session();
            byte[] sealed = session.initiator().seal(utf8("allocate 12"), null);
            sealed[sealed.length - 1] ^= 0x01;

            assertThatThrownBy(() -> session.responder().open(sealed, null)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("associated data is authenticated — a changed one fails")
        void associatedDataIsBound() {
            Pair session = session();
            byte[] sealed = session.initiator().seal(utf8("allocate 12"), utf8("intent"));

            assertThatThrownBy(() -> session.responder().open(sealed, utf8("something-else")))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    @Nested
    @DisplayName("authenticated")
    class Authenticated {

        @Test
        @DisplayName("⚠ a frame from the WRONG sender key does not open — this IS the authentication")
        void impostorRejected() {
            // HPKE mode_auth folds the sender's STATIC key into the key schedule, so an impostor does
            // not produce a mismatched-identity flag for somebody to check — the frames simply do not
            // open. Authentication that cannot be forgotten, because there is no branch to forget.
            AsymmetricCipherKeyPair server = HpkeChannel.generateStaticKeyPair();
            AsymmetricCipherKeyPair realClient = HpkeChannel.generateStaticKeyPair();
            AsymmetricCipherKeyPair impostor = HpkeChannel.generateStaticKeyPair();

            HpkeChannel forged = HpkeChannel.initiate(server.getPublic(), impostor);
            HpkeChannel responder = HpkeChannel.respond(forged.encapsulation(), server, realClient.getPublic());

            assertThatThrownBy(() -> responder.open(forged.seal(utf8("i am alice"), null), null))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a session to the wrong RESPONDER key does not open either")
        void wrongResponder() {
            AsymmetricCipherKeyPair intended = HpkeChannel.generateStaticKeyPair();
            AsymmetricCipherKeyPair other = HpkeChannel.generateStaticKeyPair();
            AsymmetricCipherKeyPair client = HpkeChannel.generateStaticKeyPair();

            HpkeChannel initiator = HpkeChannel.initiate(intended.getPublic(), client);
            HpkeChannel wrong = HpkeChannel.respond(initiator.encapsulation(), other, client.getPublic());

            assertThatThrownBy(() -> wrong.open(initiator.seal(utf8("x"), null), null))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    @Nested
    @DisplayName("replay-proof")
    class ReplayProof {

        @Test
        @DisplayName("⚠ the SAME frame cannot be opened twice")
        void replayRejected() {
            // Captured intent must not be re-sendable. This is the context's sequence number, not a
            // nonce cache somebody has to maintain and can forget to prune.
            Pair session = session();
            byte[] sealed = session.initiator().seal(utf8("transfer everything"), null);

            assertThat(session.responder().open(sealed, null)).isNotEmpty();
            assertThatThrownBy(() -> session.responder().open(sealed, null)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("frames out of ORDER are rejected too")
        void reorderRejected() {
            Pair session = session();
            byte[] first = session.initiator().seal(utf8("one"), null);
            byte[] second = session.initiator().seal(utf8("two"), null);

            // Presenting the second before the first breaks the sequence, so it does not open.
            assertThatThrownBy(() -> session.responder().open(second, null)).isInstanceOf(SecureChannelException.class);
            assertThat(first).isNotEmpty();
        }

        @Test
        @DisplayName("a long run of frames stays in step")
        void sustained() {
            Pair session = session();
            for (int i = 0; i < 200; i++) {
                byte[] sealed = session.initiator().seal(utf8("frame-" + i), null);
                assertThat(new String(session.responder().open(sealed, null), StandardCharsets.UTF_8))
                        .isEqualTo("frame-" + i);
            }
        }
    }

    @Nested
    @DisplayName("the reverse direction")
    class Reverse {

        @Test
        @DisplayName("both sides derive the SAME key with no extra round trip")
        void agreesWithoutARoundTrip() {
            // ⚠ HPKE contexts are one-directional. Reusing one for both ways would repeat sequence
            // numbers under one key, which is the catastrophic AEAD failure. RFC 9180 §9.8 derives the
            // reverse direction from the exporter secret instead.
            Pair session = session();

            assertThat(session.initiator().reverseDirectionKey())
                    .isEqualTo(session.responder().reverseDirectionKey())
                    .hasSize(32);
        }

        @Test
        @DisplayName("two different sessions derive different reverse keys")
        void perSession() {
            assertThat(session().initiator().reverseDirectionKey())
                    .isNotEqualTo(session().initiator().reverseDirectionKey());
        }
    }
}
