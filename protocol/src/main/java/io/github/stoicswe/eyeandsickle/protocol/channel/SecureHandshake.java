package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Hkdf;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * The three-message handshake that establishes a {@link SecureChannel}.
 *
 * <pre>
 *   initiator                                             responder
 *      |-- 1. ephemeral pubkey + transport attestation ------&gt;|
 *      |&lt;- 2. ephemeral pubkey + attestation + confirmation --|
 *      |-- 3. confirmation ----------------------------------&gt;|
 *      |                  channel established                 |
 * </pre>
 *
 * <h2>Design, and what each piece buys</h2>
 *
 * The shape follows the Noise framework's {@code IK} pattern: the initiator already knows the
 * responder's static key (from the DID document or the federation directory), and both sides
 * contribute an ephemeral key. Three Diffie-Hellman operations feed the key derivation:
 *
 * <ul>
 *   <li><strong>ephemeral &times; ephemeral</strong> — forward secrecy. Neither party's long-term key
 *       can decrypt a recorded session afterwards, so seizing a home server's disk next year does
 *       not retroactively open this year's traffic.
 *   <li><strong>initiator ephemeral &times; responder static</strong> — authenticates the responder.
 *       Only the holder of the responder's transport private key can complete it.
 *   <li><strong>initiator static &times; responder ephemeral</strong> — authenticates the initiator,
 *       symmetrically.
 * </ul>
 *
 * Because both static keys participate, a machine-in-the-middle cannot substitute its own ephemeral
 * keys: it does not hold either static key, so the two sides derive different keys and the
 * confirmation step fails immediately.
 *
 * <p>The <strong>transcript hash</strong> — a running SHA-256 over the protocol label, the caller's
 * prologue, and every handshake byte — is used as the HKDF salt. Any tampering anywhere in the
 * handshake changes it, so the two sides derive different keys and the session dies rather than
 * quietly continuing in a weakened state. This is what makes downgrade and injection attacks fail
 * loudly.
 *
 * <h2>Non-goals, stated so nobody assumes otherwise</h2>
 *
 * <ul>
 *   <li><strong>No identity hiding.</strong> Both DIDs are visible to a network observer. Noise's IK
 *       pattern can encrypt the initiator's static key; this does not, because the added complexity
 *       buys little here — a client's DID is already known to the server it is dialling, and in
 *       federation both servers are publicly listed anyway.
 *   <li><strong>This is not a replacement for TLS.</strong> Run it inside TLS 1.3. This layer defends
 *       against a compromised or hostile TLS terminator — which self-hosters running a reverse proxy
 *       genuinely have — and it anchors identity in DIDs instead of hostnames. It does not
 *       reimplement everything TLS does well.
 *   <li><strong>⚠ [PROPOSAL] — needs review before it guards anything real.</strong> This is a
 *       hand-rolled protocol following a well-understood pattern, which is safer than inventing one
 *       but is still not the same as a reviewed, audited implementation. Have a cryptographer read
 *       it, or swap in a real Noise library, before it protects a live federation.
 * </ul>
 */
public final class SecureHandshake {

    /**
     * Protocol label. Mixed into the transcript so a signature or key from this protocol can never
     * be replayed into a different one — and so a version bump is a hard incompatibility rather
     * than a silent downgrade.
     */
    private static final byte[] PROTOCOL_LABEL =
            "EyeAndSickle_SecureChannel_v1_X25519_HKDF-SHA256_AES256-GCM".getBytes(StandardCharsets.UTF_8);

    private static final byte[] INFO_INITIATOR_TO_RESPONDER = label("i2r");
    private static final byte[] INFO_RESPONDER_TO_INITIATOR = label("r2i");
    private static final byte[] INFO_CONFIRM_INITIATOR = label("confirm-i");
    private static final byte[] INFO_CONFIRM_RESPONDER = label("confirm-r");

    static final byte DIRECTION_INITIATOR_TO_RESPONDER = 1;
    static final byte DIRECTION_RESPONDER_TO_INITIATOR = 2;

    private static final int KEY_LENGTH = 32;
    private static final int CONFIRMATION_LENGTH = 32;

    private SecureHandshake() {}

    private static byte[] label(String suffix) {
        return ("EyeAndSickle_SecureChannel_v1/" + suffix).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The local side's long-lived transport identity.
     *
     * @param attestation this key's DID-signed attestation, sent to the peer
     * @param staticKeyPair the X25519 key pair the attestation covers
     */
    public record LocalIdentity(TransportKeyAttestation attestation, KeyPair staticKeyPair) {
        public LocalIdentity {
            Objects.requireNonNull(attestation, "attestation");
            Objects.requireNonNull(staticKeyPair, "staticKeyPair");
        }
    }

    /**
     * Decides whether a peer's attestation is acceptable.
     *
     * <p>Implementations resolve the DID to its Ed25519 public key — from an AT Protocol DID
     * document, the federation directory, or a pinned local allowlist — and check the signature via
     * {@link TransportKeyAttestation#isValidAt}. This is the single point where "who may talk to
     * us" is decided, which is exactly where a home server's allowlist belongs.
     */
    @FunctionalInterface
    public interface PeerVerifier {
        /**
         * @param attestation the peer's claimed transport-key attestation
         * @return true to accept the peer
         */
        boolean accept(TransportKeyAttestation attestation);
    }

    /** Accepts a peer whose attestation is signed by a known DID key and currently valid. */
    public static PeerVerifier verifyingAgainst(
            java.util.function.Function<String, PublicKey> didResolver, Instant now) {
        Objects.requireNonNull(didResolver, "didResolver");
        Objects.requireNonNull(now, "now");
        return attestation -> {
            PublicKey didKey = didResolver.apply(attestation.did());
            return didKey != null && attestation.isValidAt(didKey, now);
        };
    }

    // ---------------------------------------------------------------- initiator

    /** The dialling side of a handshake. Single use. */
    public static final class Initiator {
        private final LocalIdentity self;
        private final PublicKey expectedResponderStaticKey;
        private final byte[] prologue;
        private final PeerVerifier peerVerifier;
        private final KeyPair ephemeral = X25519KeyExchange.generateKeyPair();

        private byte[] message1;
        private State state = State.NEW;

        private Initiator(
                LocalIdentity self, PublicKey expectedResponderStaticKey, byte[] prologue, PeerVerifier peerVerifier) {
            this.self = Objects.requireNonNull(self, "self");
            this.expectedResponderStaticKey =
                    Objects.requireNonNull(expectedResponderStaticKey, "expectedResponderStaticKey");
            this.prologue = prologue == null ? new byte[0] : prologue.clone();
            this.peerVerifier = Objects.requireNonNull(peerVerifier, "peerVerifier");
        }

        /** Produces message 1. */
        public byte[] createInitiation() {
            require(state == State.NEW);
            state = State.AWAITING_RESPONSE;
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeBytes(X25519KeyExchange.encodePublicKey(ephemeral.getPublic()));
            writer.writeBytes(self.attestation().encode());
            message1 = writer.toByteArray();
            return message1.clone();
        }

        /**
         * Consumes message 2 and produces message 3 plus the established channel.
         *
         * @param message2 the responder's reply
         * @return the confirmation to send and the ready channel
         */
        public Completion consumeResponse(byte[] message2) {
            require(state == State.AWAITING_RESPONSE);
            WireFormat.Reader reader = new WireFormat.Reader(message2);
            byte[] responderEphemeralEncoded = reader.readBytes();
            byte[] responderAttestationBytes = reader.readBytes();
            byte[] responderConfirmation = reader.readBytes();
            reader.requireExhausted();

            TransportKeyAttestation responderAttestation = TransportKeyAttestation.decode(responderAttestationBytes);
            if (!peerVerifier.accept(responderAttestation)) {
                throw new SecureChannelException("Peer rejected");
            }
            // The attested key must be the key we intended to dial. Without this check an attacker
            // who can supply ANY validly-attested identity could answer a connection meant for
            // someone else — the classic identity-misbinding attack.
            PublicKey responderStatic = responderAttestation.transportKey();
            if (!MessageDigest.isEqual(
                    X25519KeyExchange.encodePublicKey(responderStatic),
                    X25519KeyExchange.encodePublicKey(expectedResponderStaticKey))) {
                throw new SecureChannelException("Peer rejected");
            }

            PublicKey responderEphemeral = X25519KeyExchange.decodePublicKey(responderEphemeralEncoded);
            byte[] core2 = responseCore(responderEphemeralEncoded, responderAttestationBytes);
            byte[] transcript = transcript(prologue, message1, core2);

            byte[] ikm = concat(
                    X25519KeyExchange.agree(ephemeral.getPrivate(), responderEphemeral),
                    X25519KeyExchange.agree(ephemeral.getPrivate(), responderStatic),
                    X25519KeyExchange.agree(self.staticKeyPair().getPrivate(), responderEphemeral));

            byte[] expectedConfirmation = Hkdf.derive(transcript, ikm, INFO_CONFIRM_RESPONDER, CONFIRMATION_LENGTH);
            if (!MessageDigest.isEqual(expectedConfirmation, responderConfirmation)) {
                throw new SecureChannelException("Peer rejected");
            }

            byte[] sendKey = Hkdf.derive(transcript, ikm, INFO_INITIATOR_TO_RESPONDER, KEY_LENGTH);
            byte[] receiveKey = Hkdf.derive(transcript, ikm, INFO_RESPONDER_TO_INITIATOR, KEY_LENGTH);
            byte[] ourConfirmation = Hkdf.derive(transcript, ikm, INFO_CONFIRM_INITIATOR, CONFIRMATION_LENGTH);

            state = State.DONE;
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeBytes(ourConfirmation);
            return new Completion(
                    writer.toByteArray(),
                    new SecureChannel(
                            sendKey,
                            receiveKey,
                            DIRECTION_INITIATOR_TO_RESPONDER,
                            DIRECTION_RESPONDER_TO_INITIATOR,
                            responderAttestation.did()));
        }
    }

    /**
     * Message 3 plus the channel it completes.
     *
     * @param message3 send this to the responder
     * @param channel ready for application traffic
     */
    public record Completion(byte[] message3, SecureChannel channel) {}

    /**
     * Starts a handshake as the dialling side.
     *
     * @param self our attested transport identity
     * @param expectedResponderStaticKey the responder's transport key, known in advance
     * @param prologue optional context both sides must agree on, mixed into the transcript
     * @param peerVerifier decides whether the responder's attestation is acceptable
     * @return the initiator state machine
     */
    public static Initiator initiate(
            LocalIdentity self, PublicKey expectedResponderStaticKey, byte[] prologue, PeerVerifier peerVerifier) {
        return new Initiator(self, expectedResponderStaticKey, prologue, peerVerifier);
    }

    // ---------------------------------------------------------------- responder

    /** The listening side of a handshake. Single use. */
    public static final class Responder {
        private final LocalIdentity self;
        private final byte[] prologue;
        private final PeerVerifier peerVerifier;
        private final KeyPair ephemeral = X25519KeyExchange.generateKeyPair();

        private byte[] transcript;
        private byte[] ikm;
        private String initiatorDid;
        private State state = State.NEW;

        private Responder(LocalIdentity self, byte[] prologue, PeerVerifier peerVerifier) {
            this.self = Objects.requireNonNull(self, "self");
            this.prologue = prologue == null ? new byte[0] : prologue.clone();
            this.peerVerifier = Objects.requireNonNull(peerVerifier, "peerVerifier");
        }

        /**
         * Consumes message 1 and produces message 2.
         *
         * @param message1 the initiation
         * @return the reply to send
         */
        public byte[] consumeInitiation(byte[] message1) {
            require(state == State.NEW);
            WireFormat.Reader reader = new WireFormat.Reader(message1);
            byte[] initiatorEphemeralEncoded = reader.readBytes();
            byte[] initiatorAttestationBytes = reader.readBytes();
            reader.requireExhausted();

            TransportKeyAttestation initiatorAttestation = TransportKeyAttestation.decode(initiatorAttestationBytes);
            if (!peerVerifier.accept(initiatorAttestation)) {
                throw new SecureChannelException("Peer rejected");
            }
            initiatorDid = initiatorAttestation.did();

            PublicKey initiatorEphemeral = X25519KeyExchange.decodePublicKey(initiatorEphemeralEncoded);
            PublicKey initiatorStatic = initiatorAttestation.transportKey();

            byte[] responderEphemeralEncoded = X25519KeyExchange.encodePublicKey(ephemeral.getPublic());
            byte[] responderAttestationBytes = self.attestation().encode();
            byte[] core2 = responseCore(responderEphemeralEncoded, responderAttestationBytes);
            transcript = transcript(prologue, message1, core2);

            // Mirrors the initiator's ordering exactly. If the two sides ordered these differently
            // they would derive different keys and every handshake would fail — which is at least
            // loud, but the ordering is fixed here on purpose.
            ikm = concat(
                    X25519KeyExchange.agree(ephemeral.getPrivate(), initiatorEphemeral),
                    X25519KeyExchange.agree(self.staticKeyPair().getPrivate(), initiatorEphemeral),
                    X25519KeyExchange.agree(ephemeral.getPrivate(), initiatorStatic));

            byte[] confirmation = Hkdf.derive(transcript, ikm, INFO_CONFIRM_RESPONDER, CONFIRMATION_LENGTH);

            state = State.AWAITING_CONFIRMATION;
            WireFormat.Writer writer = new WireFormat.Writer();
            writer.writeBytes(responderEphemeralEncoded);
            writer.writeBytes(responderAttestationBytes);
            writer.writeBytes(confirmation);
            return writer.toByteArray();
        }

        /**
         * Consumes message 3 and completes the channel.
         *
         * @param message3 the initiator's confirmation
         * @return the established channel
         */
        public SecureChannel consumeConfirmation(byte[] message3) {
            require(state == State.AWAITING_CONFIRMATION);
            WireFormat.Reader reader = new WireFormat.Reader(message3);
            byte[] confirmation = reader.readBytes();
            reader.requireExhausted();

            byte[] expected = Hkdf.derive(transcript, ikm, INFO_CONFIRM_INITIATOR, CONFIRMATION_LENGTH);
            if (!MessageDigest.isEqual(expected, confirmation)) {
                throw new SecureChannelException("Peer rejected");
            }

            state = State.DONE;
            return new SecureChannel(
                    Hkdf.derive(transcript, ikm, INFO_RESPONDER_TO_INITIATOR, KEY_LENGTH),
                    Hkdf.derive(transcript, ikm, INFO_INITIATOR_TO_RESPONDER, KEY_LENGTH),
                    DIRECTION_RESPONDER_TO_INITIATOR,
                    DIRECTION_INITIATOR_TO_RESPONDER,
                    initiatorDid);
        }
    }

    /**
     * Starts a handshake as the listening side.
     *
     * @param self our attested transport identity
     * @param prologue optional context both sides must agree on
     * @param peerVerifier decides whether the initiator may connect — a home server's allowlist
     *     lives here
     * @return the responder state machine
     */
    public static Responder respond(LocalIdentity self, byte[] prologue, PeerVerifier peerVerifier) {
        return new Responder(self, prologue, peerVerifier);
    }

    // ---------------------------------------------------------------- internals

    private enum State {
        NEW,
        AWAITING_RESPONSE,
        AWAITING_CONFIRMATION,
        DONE
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("Handshake step called out of order");
        }
    }

    /** The part of message 2 that is hashed — everything except the confirmation itself. */
    private static byte[] responseCore(byte[] ephemeralEncoded, byte[] attestationBytes) {
        WireFormat.Writer writer = new WireFormat.Writer();
        writer.writeBytes(ephemeralEncoded);
        writer.writeBytes(attestationBytes);
        return writer.toByteArray();
    }

    private static byte[] transcript(byte[] prologue, byte[] message1, byte[] responseCore) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PROTOCOL_LABEL);
            digest.update(prologue);
            digest.update(message1);
            digest.update(responseCore);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
