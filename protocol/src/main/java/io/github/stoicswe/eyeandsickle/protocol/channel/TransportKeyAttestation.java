package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * A signed statement that "the DID {@code X} owns the X25519 transport key {@code K} for this
 * window of time".
 *
 * <h2>Why this exists</h2>
 *
 * The channel needs to know who it is talking to. TLS answers that question with a certificate
 * authority vouching for a <em>hostname</em>, which is the wrong anchor for this game: home servers
 * are self-hosted on whatever address their operator has today, players are identified by portable
 * DIDs that survive handle changes, and the federation explicitly has no central authority to be a
 * CA (Invariant I15). A hostname says nothing about which operator is behind it.
 *
 * <p>So identity is anchored where the rest of the system already anchors it — the AT Protocol DID —
 * and this record is the bridge: an Ed25519 signature, made by the DID's key, over an X25519 key
 * used for key agreement. Anyone who can resolve the DID to its public key can verify the binding,
 * with nobody to trust in between.
 *
 * <h2>Why not just convert the Ed25519 key to X25519</h2>
 *
 * It is mathematically possible and it is a bad idea — see {@link X25519KeyExchange}. A separate
 * attested key also means the transport key can be rotated weekly without disturbing the long-lived
 * identity that every provenance chain in the game is signed against.
 *
 * @param did the AT Protocol DID making the claim
 * @param keyId a DID fragment identifying this transport key, e.g. {@code did:plc:xxx#transport-1}
 * @param transportPublicKey the X.509-encoded X25519 public key being attested
 * @param notBefore ISO-8601 UTC instant the attestation becomes valid
 * @param notAfter ISO-8601 UTC instant it expires — keep this short, that is the point
 * @param signature Ed25519 signature by the DID key over {@link #signingBytes}
 */
public record TransportKeyAttestation(
        String did, String keyId, byte[] transportPublicKey, String notBefore, String notAfter, byte[] signature) {

    /** Domain-separation prefix, so this signature can never be confused with a provenance one. */
    private static final byte[] CONTEXT = "eyeandsickle/transport-key-attestation/v1".getBytes(StandardCharsets.UTF_8);

    public TransportKeyAttestation {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
        transportPublicKey =
                Objects.requireNonNull(transportPublicKey, "transportPublicKey").clone();
        signature = Objects.requireNonNull(signature, "signature").clone();
    }

    /**
     * Produces the exact bytes that get signed.
     *
     * <p>Every field is length-prefixed. That is not decoration: if the fields were simply
     * concatenated, a DID of {@code "alice"} with key id {@code "bob"} and a DID of {@code "ali"}
     * with key id {@code "cebob"} would produce identical signing input, and a signature over one
     * would validate the other. Length prefixes make the encoding unambiguous.
     *
     * @param did the DID making the claim
     * @param keyId the transport key's DID fragment
     * @param transportPublicKey X.509-encoded X25519 public key
     * @param notBefore ISO-8601 UTC
     * @param notAfter ISO-8601 UTC
     * @return the canonical signing input
     */
    public static byte[] signingBytes(
            String did, String keyId, byte[] transportPublicKey, String notBefore, String notAfter) {
        WireFormat.Writer writer = new WireFormat.Writer();
        writer.writeBytes(CONTEXT);
        writer.writeString(did);
        writer.writeString(keyId);
        writer.writeBytes(transportPublicKey);
        writer.writeString(notBefore);
        writer.writeString(notAfter);
        return writer.toByteArray();
    }

    /**
     * Creates and signs an attestation.
     *
     * @param did the DID making the claim
     * @param keyId the transport key's DID fragment
     * @param transportPublicKey the X25519 public key to attest
     * @param notBefore ISO-8601 UTC instant validity starts
     * @param notAfter ISO-8601 UTC instant validity ends
     * @param didSigningKey the Ed25519 private key belonging to {@code did}
     * @return the signed attestation
     */
    public static TransportKeyAttestation sign(
            String did,
            String keyId,
            PublicKey transportPublicKey,
            String notBefore,
            String notAfter,
            PrivateKey didSigningKey) {
        byte[] encodedKey = X25519KeyExchange.encodePublicKey(transportPublicKey);
        byte[] sig = Ed25519Signatures.sign(didSigningKey, signingBytes(did, keyId, encodedKey, notBefore, notAfter));
        return new TransportKeyAttestation(did, keyId, encodedKey, notBefore, notAfter, sig);
    }

    /**
     * Verifies the DID's signature over this attestation and that it is valid at the given instant.
     *
     * <p>Time is a parameter rather than read from a clock so that verification is deterministic and
     * testable, and so a caller replaying a recorded session can supply the instant that applied
     * then.
     *
     * @param didPublicKey the Ed25519 public key resolved from {@link #did()}
     * @param now the instant to evaluate validity at
     * @return whether the attestation is authentic and currently valid
     */
    public boolean isValidAt(PublicKey didPublicKey, Instant now) {
        byte[] expected = signingBytes(did, keyId, transportPublicKey, notBefore, notAfter);
        if (!Ed25519Signatures.verify(didPublicKey, expected, signature)) {
            return false;
        }
        try {
            Instant from = Instant.parse(notBefore);
            Instant until = Instant.parse(notAfter);
            return !now.isBefore(from) && now.isBefore(until);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The attested X25519 key, decoded.
     *
     * @return the transport public key
     * @throws SecureChannelException if the encoded bytes are not a valid X25519 key
     */
    public PublicKey transportKey() {
        return X25519KeyExchange.decodePublicKey(transportPublicKey);
    }

    /** Serializes this attestation for the wire. */
    byte[] encode() {
        WireFormat.Writer writer = new WireFormat.Writer();
        writer.writeString(did);
        writer.writeString(keyId);
        writer.writeBytes(transportPublicKey);
        writer.writeString(notBefore);
        writer.writeString(notAfter);
        writer.writeBytes(signature);
        return writer.toByteArray();
    }

    /** Parses an attestation received from the wire. */
    static TransportKeyAttestation decode(byte[] encoded) {
        WireFormat.Reader reader = new WireFormat.Reader(encoded);
        TransportKeyAttestation attestation = new TransportKeyAttestation(
                reader.readString(),
                reader.readString(),
                reader.readBytes(),
                reader.readString(),
                reader.readString(),
                reader.readBytes());
        reader.requireExhausted();
        return attestation;
    }

    @Override
    public byte[] transportPublicKey() {
        return transportPublicKey.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
