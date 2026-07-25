package io.github.stoicswe.eyeandsickle.protocol.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link SecureHandshake} state machine, as opposed to its cryptography.
 *
 * <p>{@link SecureChannelTest} drives the handshake down its happy path and then attacks the
 * <em>keys</em>. This file attacks the <em>steps</em>: what happens when a connection handler calls
 * them out of order, and what happens when a peer sends nothing, garbage, or a message that has been
 * cut short. Those are the paths a real server hits first — long before anyone gets around to forging
 * a signature — and they are the ones where a protocol implementation usually leaks an
 * {@code ArrayIndexOutOfBoundsException} instead of a clean refusal.
 *
 * <p>Two distinct failure kinds are pinned here on purpose. {@link IllegalStateException} means
 * <em>we</em> called the steps wrong; {@link SecureChannelException} means <em>the peer</em> sent
 * something unacceptable. A server that cannot tell them apart will eventually ban peers for its own
 * bugs.
 */
class SecureHandshakeStateTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final String NOT_BEFORE = "2026-07-01T00:00:00Z";
    private static final String NOT_AFTER = "2026-08-01T00:00:00Z";
    private static final byte[] PROLOGUE = "eyeandsickle/federation".getBytes(StandardCharsets.UTF_8);

    /** Deterministic non-handshake bytes: the first four spell a length far above any field cap. */
    private static final byte[] GARBAGE = "not a handshake message".getBytes(StandardCharsets.UTF_8);

    /** A DID with an Ed25519 identity key and an attested X25519 transport key. */
    private record Party(String did, KeyPair didKeys, KeyPair transportKeys, TransportKeyAttestation attestation) {
        SecureHandshake.LocalIdentity identity() {
            return new SecureHandshake.LocalIdentity(attestation, transportKeys);
        }

        PublicKey transportPublicKey() {
            return transportKeys.getPublic();
        }
    }

    private static Party party(String did) {
        return party(did, NOT_BEFORE, NOT_AFTER);
    }

    private static Party party(String did, String notBefore, String notAfter) {
        KeyPair didKeys = Ed25519Signatures.generateKeyPair();
        KeyPair transportKeys = X25519KeyExchange.generateKeyPair();
        TransportKeyAttestation attestation = TransportKeyAttestation.sign(
                did, did + "#transport-1", transportKeys.getPublic(), notBefore, notAfter, didKeys.getPrivate());
        return new Party(did, didKeys, transportKeys, attestation);
    }

    private static SecureHandshake.PeerVerifier verifierFor(Party... known) {
        Map<String, PublicKey> keys = new HashMap<>();
        for (Party party : known) {
            keys.put(party.did(), party.didKeys().getPublic());
        }
        return SecureHandshake.verifyingAgainst(keys::get, NOW);
    }

    /** Both ends of a handshake that has not started yet. */
    private record Pending(SecureHandshake.Initiator initiator, SecureHandshake.Responder responder) {}

    private static Pending pending(Party client, Party server) {
        SecureHandshake.PeerVerifier verifier = verifierFor(client, server);
        return new Pending(
                SecureHandshake.initiate(client.identity(), server.transportPublicKey(), PROLOGUE, verifier),
                SecureHandshake.respond(server.identity(), PROLOGUE, verifier));
    }

    /** Both established ends. */
    private record Session(SecureChannel initiator, SecureChannel responder) {}

    private static Session connect(Party client, Party server, byte[] clientPrologue, byte[] serverPrologue) {
        SecureHandshake.PeerVerifier verifier = verifierFor(client, server);
        SecureHandshake.Initiator initiator =
                SecureHandshake.initiate(client.identity(), server.transportPublicKey(), clientPrologue, verifier);
        SecureHandshake.Responder responder = SecureHandshake.respond(server.identity(), serverPrologue, verifier);

        byte[] message1 = initiator.createInitiation();
        byte[] message2 = responder.consumeInitiation(message1);
        SecureHandshake.Completion completion = initiator.consumeResponse(message2);
        return new Session(completion.channel(), responder.consumeConfirmation(completion.message3()));
    }

    // ------------------------------------------------------------------ step ordering

    @Nested
    @DisplayName("step ordering")
    class StepOrdering {

        private final Party client = party("did:plc:client000000000000");
        private final Party server = party("did:plc:server000000000000");

        @Test
        @DisplayName("an initiator cannot be used twice")
        void createInitiationTwiceIsRejected() {
            // Defends against key reuse: a second initiation would reuse the same ephemeral key, and
            // reusing an ephemeral key across sessions is what forward secrecy is bought with. It is
            // also our own bug, not the peer's — hence IllegalStateException and not the exception a
            // caller would use to decide the peer is hostile.
            Pending handshake = pending(client, server);
            handshake.initiator().createInitiation();

            assertThatThrownBy(() -> handshake.initiator().createInitiation())
                    .isInstanceOf(IllegalStateException.class)
                    .isNotInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a response cannot be consumed before an initiation is created")
        void consumeResponseBeforeInitiationIsRejected() {
            // Defends against: deriving a transcript over a message1 that was never sent.
            Pending handshake = pending(client, server);

            assertThatThrownBy(() -> handshake.initiator().consumeResponse(new byte[0]))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("an initiator refuses a second response after the channel is established")
        void consumeResponseAfterCompletionIsRejected() {
            // Defends against: a replayed message 2 producing a second channel with sequence numbers
            // reset to zero, which would reopen every replay the counters exist to close.
            Pending handshake = pending(client, server);
            byte[] message1 = handshake.initiator().createInitiation();
            byte[] message2 = handshake.responder().consumeInitiation(message1);
            handshake.initiator().consumeResponse(message2);

            assertThatThrownBy(() -> handshake.initiator().consumeResponse(message2))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a responder cannot consume two initiations")
        void consumeInitiationTwiceIsRejected() {
            // Defends against the same ephemeral-key reuse on the listening side, and against a
            // second initiation quietly overwriting the transcript of a handshake in flight.
            Pending handshake = pending(client, server);
            byte[] message1 = handshake.initiator().createInitiation();
            handshake.responder().consumeInitiation(message1);

            assertThatThrownBy(() -> handshake.responder().consumeInitiation(message1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a confirmation cannot be consumed before an initiation")
        void consumeConfirmationBeforeInitiationIsRejected() {
            // Defends against: verifying a confirmation against a transcript that does not exist yet
            // — where a null transcript could plausibly become "no checking at all".
            Pending handshake = pending(client, server);

            assertThatThrownBy(() -> handshake.responder().consumeConfirmation(new byte[0]))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a responder refuses a second confirmation after the channel is established")
        void consumeConfirmationTwiceIsRejected() {
            Pending handshake = pending(client, server);
            byte[] message1 = handshake.initiator().createInitiation();
            byte[] message2 = handshake.responder().consumeInitiation(message1);
            SecureHandshake.Completion completion = handshake.initiator().consumeResponse(message2);
            handshake.responder().consumeConfirmation(completion.message3());

            assertThatThrownBy(() -> handshake.responder().consumeConfirmation(completion.message3()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ------------------------------------------------------------------ malformed messages

    @Nested
    @DisplayName("malformed handshake messages")
    class MalformedMessages {

        private final Party client = party("did:plc:client000000000000");
        private final Party server = party("did:plc:server000000000000");

        /** Drives a handshake to the point where the responder is waiting for message 3. */
        private SecureHandshake.Responder awaitingConfirmation() {
            Pending handshake = pending(client, server);
            handshake.responder().consumeInitiation(handshake.initiator().createInitiation());
            return handshake.responder();
        }

        /** Drives a handshake to the point where the initiator is waiting for message 2. */
        private SecureHandshake.Initiator awaitingResponse() {
            Pending handshake = pending(client, server);
            handshake.initiator().createInitiation();
            return handshake.initiator();
        }

        @Test
        @DisplayName("a null initiation is a protocol error, not a NullPointerException")
        void nullInitiationIsRejected() {
            // Defends against: a null from a transport layer surfacing as an NPE that reads like a
            // local bug, in the one place where the right answer is to drop the connection.
            assertThatThrownBy(() -> pending(client, server).responder().consumeInitiation(null))
                    .isInstanceOf(SecureChannelException.class)
                    .isNotInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("an empty initiation is rejected")
        void emptyInitiationIsRejected() {
            // Defends against: a zero-byte message parsing into empty fields and an empty attestation.
            assertThatThrownBy(() -> pending(client, server).responder().consumeInitiation(new byte[0]))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a garbage initiation is rejected")
        void garbageInitiationIsRejected() {
            // Defends against: arbitrary bytes — a port scanner, a mis-dialled protocol, a fuzzer —
            // reaching the key agreement at all.
            assertThatThrownBy(() -> pending(client, server).responder().consumeInitiation(GARBAGE))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an initiation with appended bytes is rejected")
        void paddedInitiationIsRejected() {
            // Defends against transcript confusion: the responder hashes message 1 exactly as
            // received, so a peer able to append ignored bytes could make two sides agree on the
            // parse and disagree on the hash.
            Pending handshake = pending(client, server);
            byte[] message1 = handshake.initiator().createInitiation();
            byte[] padded = Arrays.copyOf(message1, message1.length + 1);

            assertThatThrownBy(() -> handshake.responder().consumeInitiation(padded))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a null, empty or garbage response is rejected")
        void malformedResponseIsRejected() {
            assertThatThrownBy(() -> awaitingResponse().consumeResponse(null))
                    .isInstanceOf(SecureChannelException.class)
                    .isNotInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> awaitingResponse().consumeResponse(new byte[0]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> awaitingResponse().consumeResponse(GARBAGE))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a truncated response is rejected")
        void truncatedResponseIsRejected() {
            // Defends against: a short read being parsed as a valid message 2 with a short
            // confirmation, which is the one field an attacker most wants to shorten.
            Pending handshake = pending(client, server);
            byte[] message1 = handshake.initiator().createInitiation();
            byte[] message2 = handshake.responder().consumeInitiation(message1);
            byte[] truncated = Arrays.copyOf(message2, message2.length - 1);

            assertThatThrownBy(() -> handshake.initiator().consumeResponse(truncated))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a null, empty or garbage confirmation is rejected")
        void malformedConfirmationIsRejected() {
            assertThatThrownBy(() -> awaitingConfirmation().consumeConfirmation(null))
                    .isInstanceOf(SecureChannelException.class)
                    .isNotInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> awaitingConfirmation().consumeConfirmation(new byte[0]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> awaitingConfirmation().consumeConfirmation(GARBAGE))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a confirmation with appended bytes is rejected")
        void paddedConfirmationIsRejected() {
            // Defends against: an unauthenticated tail riding along on the last handshake message.
            Pending handshake = pending(client, server);
            byte[] message2 = handshake
                    .responder()
                    .consumeInitiation(handshake.initiator().createInitiation());
            SecureHandshake.Completion completion = handshake.initiator().consumeResponse(message2);
            byte[] padded = Arrays.copyOf(completion.message3(), completion.message3().length + 1);

            assertThatThrownBy(() -> handshake.responder().consumeConfirmation(padded))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an empty-but-well-formed confirmation is rejected")
        void emptyConfirmationFieldIsRejected() {
            // Defends against the degenerate case the parser alone would accept: a syntactically
            // perfect message 3 carrying a zero-length confirmation. Only the constant-time compare
            // against the derived value stops it.
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeBytes(new byte[0]);

            assertThatThrownBy(() -> awaitingConfirmation().consumeConfirmation(writer.toByteArray()))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ prologue

    /**
     * <strong>[PROPOSAL]</strong> — the prologue is the caller's context binding
     * ({@code docs/architecture/07-transport-security.md} §4.2), and the docs do not say what an
     * absent one means. These tests pin the implementation's answer: null is exactly an empty
     * prologue, and nothing more permissive than that. A second implementation would have to be told.
     */
    @Nested
    @DisplayName("prologue handling")
    class PrologueHandling {

        private final Party client = party("did:plc:client000000000000");
        private final Party server = party("did:plc:server000000000000");

        @Test
        @DisplayName("a null prologue is treated as an empty one")
        void nullPrologueMatchesAnExplicitlyEmptyOne() {
            // The two spellings must be the same transcript, or a caller passing null and a caller
            // passing new byte[0] would silently fail to interoperate — a bug that only shows up
            // between two independently written implementations.
            Session session = connect(client, server, null, new byte[0]);

            byte[] payload = "prologue-free".getBytes(StandardCharsets.UTF_8);
            assertThat(session.responder().open(session.initiator().seal(payload)))
                    .isEqualTo(payload);
        }

        @Test
        @DisplayName("two null prologues interoperate")
        void bothNullProloguesWork() {
            Session session = connect(client, server, null, null);

            byte[] payload = "still fine".getBytes(StandardCharsets.UTF_8);
            assertThat(session.responder().open(session.initiator().seal(payload)))
                    .isEqualTo(payload);
        }

        @Test
        @DisplayName("a null prologue is not a wildcard")
        void nullPrologueIsNotAWildcard() {
            // Defends against the failure mode the previous two tests could hide: "null means empty"
            // must not decay into "null matches anything", which would let a peer drop the context
            // binding entirely.
            assertThatThrownBy(() -> connect(client, server, null, PROLOGUE))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ construction

    @Nested
    @DisplayName("construction")
    class Construction {

        private final Party us = party("did:plc:client000000000000");

        @Test
        @DisplayName("a local identity needs both an attestation and its key pair")
        void localIdentityRejectsNulls() {
            // Defends against a half-built identity reaching the handshake, where a missing static
            // key would surface as an NPE mid-key-agreement instead of at construction.
            assertThatThrownBy(() -> new SecureHandshake.LocalIdentity(null, us.transportKeys()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("attestation");
            assertThatThrownBy(() -> new SecureHandshake.LocalIdentity(us.attestation(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("staticKeyPair");
        }

        @Test
        @DisplayName("initiating needs an identity, an expected peer key and a verifier")
        void initiateRejectsNulls() {
            // The expected responder key is what makes this the IK pattern rather than a blind dial:
            // without it there is nothing to compare the peer's attestation against, so it must not
            // be optional.
            SecureHandshake.PeerVerifier verifier = verifierFor(us);
            assertThatThrownBy(() -> SecureHandshake.initiate(null, us.transportPublicKey(), PROLOGUE, verifier))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("self");
            assertThatThrownBy(() -> SecureHandshake.initiate(us.identity(), null, PROLOGUE, verifier))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("expectedResponderStaticKey");
            assertThatThrownBy(() -> SecureHandshake.initiate(us.identity(), us.transportPublicKey(), PROLOGUE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("peerVerifier");
        }

        @Test
        @DisplayName("responding needs an identity and a verifier")
        void respondRejectsNulls() {
            // A null verifier would mean "accept anyone", which for a home server is the difference
            // between private-by-default and open to the federation.
            assertThatThrownBy(() -> SecureHandshake.respond(null, PROLOGUE, verifierFor(us)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("self");
            assertThatThrownBy(() -> SecureHandshake.respond(us.identity(), PROLOGUE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("peerVerifier");
        }
    }

    // ------------------------------------------------------------------ peer verifier

    @Nested
    @DisplayName("the default peer verifier")
    class DefaultPeerVerifier {

        private final Party known = party("did:plc:known000000000000");

        @Test
        @DisplayName("a resolvable DID with a valid attestation is accepted")
        void resolvableDidIsAccepted() {
            Function<String, PublicKey> resolver =
                    did -> known.did().equals(did) ? known.didKeys().getPublic() : null;

            assertThat(SecureHandshake.verifyingAgainst(resolver, NOW).accept(known.attestation()))
                    .isTrue();
        }

        @Test
        @DisplayName("an unresolvable DID is refused")
        void unresolvableDidIsRefused() {
            // Defends against fail-open on a lookup miss: an unknown DID, a directory outage and a
            // deliberately unlisted attacker are indistinguishable here, and all three must be no.
            // This is also where a home server's allowlist lives.
            assertThat(SecureHandshake.verifyingAgainst(did -> null, NOW).accept(known.attestation()))
                    .isFalse();
        }

        @Test
        @DisplayName("a DID that resolves to the wrong key is refused")
        void wrongResolvedKeyIsRefused() {
            // Defends against a poisoned or stale directory entry being enough to impersonate a DID.
            KeyPair other = Ed25519Signatures.generateKeyPair();

            assertThat(SecureHandshake.verifyingAgainst(did -> other.getPublic(), NOW)
                            .accept(known.attestation()))
                    .isFalse();
        }

        @Test
        @DisplayName("an attestation outside its window is refused even for a known DID")
        void expiredAttestationIsRefused() {
            Party stale = party("did:plc:stale00000000000", "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z");

            assertThat(SecureHandshake.verifyingAgainst(did -> stale.didKeys().getPublic(), NOW)
                            .accept(stale.attestation()))
                    .isFalse();
        }

        @Test
        @DisplayName("the verifier needs both a resolver and an instant")
        void verifyingAgainstRejectsNulls() {
            // Time is a parameter here rather than a clock read, so that verification is
            // deterministic and a recorded session can be re-verified at the instant that applied
            // then. That only works if the instant is actually supplied.
            assertThatThrownBy(() -> SecureHandshake.verifyingAgainst(null, NOW))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("didResolver");
            assertThatThrownBy(() -> SecureHandshake.verifyingAgainst(did -> null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("now");
        }
    }
}
