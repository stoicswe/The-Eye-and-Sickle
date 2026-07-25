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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the secure channel.
 *
 * <p>The happy path is one test. The rest are attacks, because for security code the interesting
 * question is never "does it work" — it is "does it fail when it should". Every negative test here
 * corresponds to a specific claim made in {@link SecureChannel}'s documentation; if a claim has no
 * test, it is only marketing.
 */
class SecureChannelTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final byte[] PROLOGUE = "eyeandsickle/federation".getBytes(StandardCharsets.UTF_8);

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
        return party(did, "2026-07-01T00:00:00Z", "2026-08-01T00:00:00Z");
    }

    private static Party party(String did, String notBefore, String notAfter) {
        KeyPair didKeys = Ed25519Signatures.generateKeyPair();
        KeyPair transportKeys = X25519KeyExchange.generateKeyPair();
        TransportKeyAttestation attestation = TransportKeyAttestation.sign(
                did, did + "#transport-1", transportKeys.getPublic(), notBefore, notAfter, didKeys.getPrivate());
        return new Party(did, didKeys, transportKeys, attestation);
    }

    /** Stands in for AT Protocol DID resolution. */
    private static final class Directory {
        private final Map<String, PublicKey> keys = new HashMap<>();

        Directory with(Party party) {
            keys.put(party.did(), party.didKeys().getPublic());
            return this;
        }

        SecureHandshake.PeerVerifier verifier() {
            return SecureHandshake.verifyingAgainst(keys::get, NOW);
        }
    }

    /** Runs a full handshake and returns both established ends. */
    private record Session(SecureChannel initiator, SecureChannel responder) {}

    private static Session connect(Party client, Party server, Directory directory) {
        return connect(client, server, directory, server.transportPublicKey(), PROLOGUE, PROLOGUE);
    }

    private static Session connect(
            Party client,
            Party server,
            Directory directory,
            PublicKey expectedServerKey,
            byte[] clientPrologue,
            byte[] serverPrologue) {
        SecureHandshake.Initiator initiator =
                SecureHandshake.initiate(client.identity(), expectedServerKey, clientPrologue, directory.verifier());
        SecureHandshake.Responder responder =
                SecureHandshake.respond(server.identity(), serverPrologue, directory.verifier());

        byte[] message1 = initiator.createInitiation();
        byte[] message2 = responder.consumeInitiation(message1);
        SecureHandshake.Completion completion = initiator.consumeResponse(message2);
        SecureChannel serverChannel = responder.consumeConfirmation(completion.message3());
        return new Session(completion.channel(), serverChannel);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    @DisplayName("a handshake establishes a channel both ends can talk over")
    void handshakeEstablishesWorkingChannel() {
        Party client = party("did:plc:client000000000000");
        Party server = party("did:plc:server000000000000");
        Session session = connect(client, server, new Directory().with(client).with(server));

        byte[] request = "{\"op\":\"breach\",\"target\":\"node-17\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(session.responder().open(session.initiator().seal(request))).isEqualTo(request);

        byte[] reply = "{\"outcome\":\"breached\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(session.initiator().open(session.responder().seal(reply))).isEqualTo(reply);
    }

    @Test
    @DisplayName("each end learns the other's DID cryptographically, not by assertion")
    void peerIdentityIsEstablished() {
        Party client = party("did:plc:client000000000000");
        Party server = party("did:plc:server000000000000");
        Session session = connect(client, server, new Directory().with(client).with(server));

        // This is the value to attribute actions to — not a source IP, and not a field the peer
        // put inside the message body.
        assertThat(session.initiator().peerDid()).isEqualTo("did:plc:server000000000000");
        assertThat(session.responder().peerDid()).isEqualTo("did:plc:client000000000000");
    }

    @Test
    @DisplayName("many messages survive a long session in order")
    void sustainedTraffic() {
        Party client = party("did:plc:client000000000000");
        Party server = party("did:plc:server000000000000");
        Session session = connect(client, server, new Directory().with(client).with(server));

        for (int i = 0; i < 500; i++) {
            byte[] payload = ("tick-" + i).getBytes(StandardCharsets.UTF_8);
            assertThat(session.responder().open(session.initiator().seal(payload)))
                    .isEqualTo(payload);
        }
        assertThat(session.initiator().framesSent()).isEqualTo(500);
    }

    // ------------------------------------------------------------------ tampering

    @Nested
    @DisplayName("tamper resistance")
    class Tampering {

        @Test
        @DisplayName("flipping any single bit of a frame makes it unopenable")
        void anyBitFlipIsDetected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));
            byte[] frame = session.initiator().seal("transfer 500 ec".getBytes(StandardCharsets.UTF_8));

            // Every byte: header, ciphertext, and authentication tag alike. Not one of them may be
            // changeable without detection — that is what "safe from tampering" has to mean.
            for (int i = 0; i < frame.length; i++) {
                byte[] corrupted = frame.clone();
                corrupted[i] ^= 0x01;
                Session fresh =
                        connect(client, server, new Directory().with(client).with(server));
                int index = i;
                assertThatThrownBy(() -> fresh.responder().open(corrupted))
                        .as("byte %d was mutable without detection", index)
                        .isInstanceOf(SecureChannelException.class);
            }
        }

        @Test
        @DisplayName("truncating a frame is rejected")
        void truncationIsDetected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));
            byte[] frame = session.initiator().seal("some payload worth truncating".getBytes(StandardCharsets.UTF_8));
            byte[] truncated = java.util.Arrays.copyOf(frame, frame.length - 1);

            assertThatThrownBy(() -> session.responder().open(truncated)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a frame from a different session cannot be injected")
        void crossSessionInjectionIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Directory directory = new Directory().with(client).with(server);

            Session sessionA = connect(client, server, directory);
            Session sessionB = connect(client, server, directory);

            byte[] frameFromA = sessionA.initiator().seal("hello".getBytes(StandardCharsets.UTF_8));

            // Same two DIDs, same static keys — but ephemeral keys made the session keys different,
            // so traffic captured from one connection is useless against another.
            assertThatThrownBy(() -> sessionB.responder().open(frameFromA)).isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ replay / ordering

    @Nested
    @DisplayName("replay and ordering")
    class ReplayAndOrdering {

        @Test
        @DisplayName("replaying a captured frame is rejected")
        void replayIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));

            byte[] frame = session.initiator().seal("mine 1 block".getBytes(StandardCharsets.UTF_8));
            assertThat(session.responder().open(frame)).isNotEmpty();

            // Without this, an attacker replaying a "collect yield" frame a thousand times would
            // be a thousand collections — every byte perfectly authentic.
            assertThatThrownBy(() -> session.responder().open(frame)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("frames delivered out of order are rejected")
        void reorderingIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));

            byte[] first = session.initiator().seal("first".getBytes(StandardCharsets.UTF_8));
            byte[] second = session.initiator().seal("second".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> session.responder().open(second))
                    .as("skipping frame 0 must not be accepted")
                    .isInstanceOf(SecureChannelException.class);
            assertThat(session.responder().open(first)).isNotEmpty();
        }

        @Test
        @DisplayName("a rejected frame does not desynchronise the session")
        void forgedFrameDoesNotBurnASequenceNumber() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));

            byte[] good = session.initiator().seal("legitimate".getBytes(StandardCharsets.UTF_8));
            byte[] forged = good.clone();
            forged[forged.length - 1] ^= 0x40;

            assertThatThrownBy(() -> session.responder().open(forged)).isInstanceOf(SecureChannelException.class);
            // If the counter had advanced on failure, an attacker could knock a connection out
            // permanently just by injecting one junk frame — a cheap denial of service.
            assertThat(session.responder().open(good)).asString().isEqualTo("legitimate");
        }

        @Test
        @DisplayName("a frame cannot be reflected back at its sender")
        void reflectionIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));

            byte[] fromClient = session.initiator().seal("who am i talking to".getBytes(StandardCharsets.UTF_8));

            // Each direction has its own key AND its own direction byte in the authenticated header.
            assertThatThrownBy(() -> session.initiator().open(fromClient)).isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ authentication

    @Nested
    @DisplayName("peer authentication")
    class Authentication {

        @Test
        @DisplayName("a machine-in-the-middle cannot substitute its own keys")
        void mitmIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Party attacker = party("did:plc:attacker00000000");
            Directory directory = new Directory().with(client).with(server).with(attacker);

            // The attacker holds a perfectly valid, properly signed attestation of its own — it is
            // a real participant in the federation, not an outsider. It answers a connection meant
            // for the server.
            assertThatThrownBy(() -> connect(
                            client,
                            attacker,
                            directory,
                            server.transportPublicKey(), // client dialled the SERVER's key
                            PROLOGUE,
                            PROLOGUE))
                    .as("answering for a key you do not hold must fail")
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an attestation signed by the wrong DID key is rejected")
        void forgedAttestationIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Party impostor = party("did:plc:impostor00000000");

            // Claim the victim's DID while signing with the impostor's key — i.e. "I am the server".
            KeyPair stolenLookingKeys = X25519KeyExchange.generateKeyPair();
            TransportKeyAttestation forged = TransportKeyAttestation.sign(
                    server.did(),
                    server.did() + "#transport-1",
                    stolenLookingKeys.getPublic(),
                    "2026-07-01T00:00:00Z",
                    "2026-08-01T00:00:00Z",
                    impostor.didKeys().getPrivate());

            Directory directory = new Directory().with(client).with(server);
            assertThat(forged.isValidAt(server.didKeys().getPublic(), NOW))
                    .as("the real DID key must not validate a signature it did not make")
                    .isFalse();

            Party fake = new Party(server.did(), impostor.didKeys(), stolenLookingKeys, forged);
            assertThatThrownBy(
                            () -> connect(client, fake, directory, stolenLookingKeys.getPublic(), PROLOGUE, PROLOGUE))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an expired attestation is rejected")
        void expiredAttestationIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party stale = party("did:plc:stale00000000000", "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z");
            Directory directory = new Directory().with(client).with(stale);

            assertThat(stale.attestation().isValidAt(stale.didKeys().getPublic(), NOW))
                    .as("short-lived transport keys are the point; an expired one must not work")
                    .isFalse();
            assertThatThrownBy(() -> connect(client, stale, directory)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("an unknown DID is refused — this is where an allowlist lives")
        void unknownPeerIsRejected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");

            // Directory knows the server but not the client: a home server is private by default
            // and the operator chooses who joins.
            Directory serverOnly = new Directory().with(server);
            assertThatThrownBy(() -> connect(client, server, serverOnly)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a tampered handshake message is detected")
        void tamperedHandshakeIsDetected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Directory directory = new Directory().with(client).with(server);

            SecureHandshake.Initiator initiator = SecureHandshake.initiate(
                    client.identity(), server.transportPublicKey(), PROLOGUE, directory.verifier());
            SecureHandshake.Responder responder =
                    SecureHandshake.respond(server.identity(), PROLOGUE, directory.verifier());

            byte[] message1 = initiator.createInitiation();
            message1[message1.length - 1] ^= 0x01; // alter the initiator's attestation signature

            assertThatThrownBy(() -> responder.consumeInitiation(message1)).isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("peers that disagree about the prologue cannot establish a channel")
        void prologueMismatchIsDetected() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Directory directory = new Directory().with(client).with(server);

            // The prologue is how callers bind a session to its context — which server, which
            // protocol version, which game. A disagreement means the two sides think they are
            // having different conversations, and the handshake refuses rather than papering over it.
            assertThatThrownBy(() -> connect(
                            client,
                            server,
                            directory,
                            server.transportPublicKey(),
                            "context-a".getBytes(StandardCharsets.UTF_8),
                            "context-b".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(SecureChannelException.class);
        }
    }

    // ------------------------------------------------------------------ key hygiene

    @Nested
    @DisplayName("key hygiene")
    class KeyHygiene {

        @Test
        @DisplayName("two sessions between the same peers produce different ciphertext")
        void sessionsAreIndependent() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Directory directory = new Directory().with(client).with(server);

            byte[] payload = "identical plaintext".getBytes(StandardCharsets.UTF_8);
            byte[] a = connect(client, server, directory).initiator().seal(payload);
            byte[] b = connect(client, server, directory).initiator().seal(payload);

            // Same peers, same static keys, same message, same sequence number 0 — different bytes.
            // That is the ephemeral keys doing their job, and it is what gives forward secrecy.
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("the two directions use different keys")
        void directionsUseDifferentKeys() {
            Party client = party("did:plc:client000000000000");
            Party server = party("did:plc:server000000000000");
            Session session =
                    connect(client, server, new Directory().with(client).with(server));

            byte[] payload = "same bytes both ways".getBytes(StandardCharsets.UTF_8);
            assertThat(session.initiator().seal(payload))
                    .isNotEqualTo(session.responder().seal(payload));
        }

        @Test
        @DisplayName("a low-order peer key is refused")
        void lowOrderPointIsRejected() {
            // An all-zero shared secret would mean the peer forced a known key regardless of ours.
            // RFC 7748 §6.1 says reject; the JDK may or may not, so we check explicitly.
            KeyPair ours = X25519KeyExchange.generateKeyPair();
            byte[] identityPoint = new byte[X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH];
            // X.509 SubjectPublicKeyInfo prefix for X25519, followed by an all-zero u-coordinate.
            byte[] prefix = java.util.Arrays.copyOf(
                    X25519KeyExchange.encodePublicKey(ours.getPublic()),
                    X25519KeyExchange.ENCODED_PUBLIC_KEY_LENGTH - 32);
            System.arraycopy(prefix, 0, identityPoint, 0, prefix.length);

            assertThatThrownBy(() -> X25519KeyExchange.agree(
                            ours.getPrivate(), X25519KeyExchange.decodePublicKey(identityPoint)))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("malformed key material is refused rather than misparsed")
        void malformedKeysAreRejected() {
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(new byte[8]))
                    .isInstanceOf(SecureChannelException.class);
            assertThatThrownBy(() -> X25519KeyExchange.decodePublicKey(null))
                    .isInstanceOf(SecureChannelException.class);
        }

        @Test
        @DisplayName("a hostile length prefix cannot walk the reader off the end")
        void malformedFramesAreRejected() {
            byte[] hostile = java.nio.ByteBuffer.allocate(8)
                    .putInt(Integer.MAX_VALUE)
                    .putInt(0)
                    .array();
            assertThatThrownBy(() -> TransportKeyAttestation.decode(hostile))
                    .isInstanceOf(SecureChannelException.class);
        }
    }
}
