package io.github.stoicswe.eyeandsickle.protocol.channel;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.crypto.SecureChannelException;
import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Objects;
import java.util.UUID;

/**
 * A signed statement that "character {@code C}, slot {@code N}, of account DID {@code D} is homed at
 * server {@code S} (its DID and endpoint), at sequence {@code K}" — the record behind the character
 * directory ({@code docs/architecture/09-player-state-portability.md} §4, "Option E").
 *
 * <h2>DNS for your character</h2>
 *
 * Every online character has exactly one home server holding its authoritative state (Invariant I14, 09
 * §1.1). A player on a new machine needs to <em>find</em> that home from nothing but their DID. This
 * record is the pointer that answers it: the home server signs "I host this character", and any
 * federated server that can resolve the home server's DID key can verify the binding and answer "where
 * are DID {@code D}'s characters?". No game state moves — only a location is read (09 §4).
 *
 * <h2>Why the home server is the signer</h2>
 *
 * The binding is <em>self-asserted, non-adversarial</em> location data, exactly like a server descriptor
 * ({@code docs/architecture/08-discovery-and-sync.md} §2): only the home server can sign for itself, so
 * accepting the record with the highest {@link #sequenceNumber()} it has signed is not a trust decision
 * about an adversary — it is the home updating its own contact card. A {@code rogue} home claiming to
 * host a character it does not is a separate, deferred concern (09 §4, open question Q-home-auth); the
 * v1 anchor is the home-server signature alone.
 *
 * <h2>Monotonic sequence is the anti-rollback</h2>
 *
 * A home change (a migration, 09 §6) advances {@link #sequenceNumber()}; a lower one is refused. That is
 * what stops a captured, stale record from resurrecting a character at a home it has already left — the
 * same monotonicity the discovery descriptors enforce (08 §3). The counter is a <em>signed</em> value
 * the home controls, never a wall clock: a clock is attacker-controlled and self-hosted clocks
 * legitimately disagree, whereas a counter the home signs cannot be advanced by anyone else. Ordering is
 * the directory's job; this record only carries the number it signed.
 *
 * <h2>No game vocabulary crosses into this record (Invariant I14, module charter)</h2>
 *
 * The character is named by its home-relative {@link #characterId()} and {@link #slot()} as bare values,
 * not by the game package's {@code CharacterRef}: the transport channel must stay agnostic to what it
 * carries ({@code ArchitectureRulesTest}), and a slot's product cap is a server rule, never a wire fact.
 * The one structural invariant asserted here is that a slot is a positive index.
 *
 * @param accountDid the AT Protocol account DID {@code D} the character belongs to
 * @param characterId the character's id {@code C} at its home server; home-relative, since migrating to a
 *     new home mints a fresh id (09 §6)
 * @param slot the save slot {@code N} the character occupies within its account; 1 or greater
 * @param homeServerDid the DID {@code S} of the home server that hosts the character and signs this record
 * @param signingKeyId the DID fragment naming the home server's signing key, e.g.
 *     {@code did:plc:home#key1}; must belong to {@link #homeServerDid()} — a server may only speak for
 *     itself
 * @param homeEndpoint where to reach the home server; {@code http(s)://...}. An endpoint moves when a
 *     self-hoster changes address, which is why nothing is keyed off it — the DID is
 * @param homeTransportPublicKey the X.509-encoded X25519 transport key of the home server, so a resolver
 *     can seal traffic to it without a second lookup ({@code docs/architecture/07-transport-security.md})
 * @param sequenceNumber the signed monotonic version counter {@code K}; a higher value supersedes a lower
 *     one
 * @param signature the Ed25519 signature by {@link #homeServerDid()}'s key over {@link #signingBytes}
 */
public record CharacterHomeRecord(
        String accountDid,
        UUID characterId,
        int slot,
        String homeServerDid,
        String signingKeyId,
        String homeEndpoint,
        byte[] homeTransportPublicKey,
        long sequenceNumber,
        byte[] signature) {

    /** The lowest slot number. A slot is a positive index; 0 or negative is not a slot (mirrors {@code CharacterRef}). */
    public static final int MIN_SLOT = 1;

    /**
     * Domain-separation prefix, so a character-home signature can never be confused with a transport-key
     * attestation, a provenance record, or any other Ed25519 signature in the game.
     */
    private static final byte[] CONTEXT = "eyeandsickle/character-home-record/v1".getBytes(StandardCharsets.UTF_8);

    public CharacterHomeRecord {
        Objects.requireNonNull(accountDid, "accountDid");
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(homeServerDid, "homeServerDid");
        Objects.requireNonNull(signingKeyId, "signingKeyId");
        Objects.requireNonNull(homeEndpoint, "homeEndpoint");
        homeTransportPublicKey = Objects.requireNonNull(homeTransportPublicKey, "homeTransportPublicKey")
                .clone();
        signature = Objects.requireNonNull(signature, "signature").clone();
        if (slot < MIN_SLOT) {
            throw new IllegalArgumentException("slot is a positive index (>= " + MIN_SLOT + "), was " + slot);
        }
        if (sequenceNumber < 0) {
            // The directory schema forbids a negative sequence too; catching it at construction means a
            // caller building a record by hand fails here rather than at the INSERT.
            throw new IllegalArgumentException("sequenceNumber must not be negative, was " + sequenceNumber);
        }
    }

    /**
     * Produces the exact bytes that get signed.
     *
     * <p>Every field is length-prefixed, the same discipline as {@link TransportKeyAttestation}. That is
     * not decoration: if the fields were concatenated, an account DID of {@code "did:x:ab"} with home
     * {@code "cd"} and an account of {@code "did:x:a"} with home {@code "bcd"} could produce identical
     * signing input, and a signature over one would validate the other. Length prefixes make the encoding
     * unambiguous. Numbers (slot, sequence) and the UUID are rendered to their canonical strings and
     * length-prefixed like every other field, so the same logical record serializes to the same bytes on
     * any machine — a signature made on the home server reproduces on any server that verifies it.
     *
     * @param accountDid the account DID the character belongs to
     * @param characterId the character's home-server id
     * @param slot the save slot within the account
     * @param homeServerDid the home server's DID
     * @param signingKeyId the DID fragment naming the home server's signing key
     * @param homeEndpoint the home server's endpoint
     * @param homeTransportPublicKey the X.509-encoded X25519 transport key of the home server
     * @param sequenceNumber the monotonic version counter
     * @return the canonical signing input
     */
    public static byte[] signingBytes(
            String accountDid,
            UUID characterId,
            int slot,
            String homeServerDid,
            String signingKeyId,
            String homeEndpoint,
            byte[] homeTransportPublicKey,
            long sequenceNumber) {
        WireFormat.Writer writer = new WireFormat.Writer();
        writer.writeBytes(CONTEXT);
        writer.writeString(accountDid);
        writer.writeString(characterId.toString());
        writer.writeString(Integer.toString(slot));
        writer.writeString(homeServerDid);
        writer.writeString(signingKeyId);
        writer.writeString(homeEndpoint);
        writer.writeBytes(homeTransportPublicKey);
        writer.writeString(Long.toString(sequenceNumber));
        return writer.toByteArray();
    }

    /**
     * Creates and signs a character-home record.
     *
     * @param accountDid the account DID the character belongs to
     * @param characterId the character's home-server id
     * @param slot the save slot within the account (>= {@link #MIN_SLOT})
     * @param homeServerDid the home server's DID
     * @param signingKeyId the DID fragment naming the home server's signing key
     * @param homeEndpoint the home server's endpoint
     * @param homeTransportPublicKey the X25519 transport key of the home server to attest
     * @param sequenceNumber the monotonic version counter (>= 0)
     * @param homeSigningKey the Ed25519 private key belonging to {@code homeServerDid}
     * @return the signed record
     */
    public static CharacterHomeRecord sign(
            String accountDid,
            UUID characterId,
            int slot,
            String homeServerDid,
            String signingKeyId,
            String homeEndpoint,
            PublicKey homeTransportPublicKey,
            long sequenceNumber,
            PrivateKey homeSigningKey) {
        byte[] encodedKey = X25519KeyExchange.encodePublicKey(homeTransportPublicKey);
        byte[] sig = Ed25519Signatures.sign(
                homeSigningKey,
                signingBytes(
                        accountDid,
                        characterId,
                        slot,
                        homeServerDid,
                        signingKeyId,
                        homeEndpoint,
                        encodedKey,
                        sequenceNumber));
        return new CharacterHomeRecord(
                accountDid,
                characterId,
                slot,
                homeServerDid,
                signingKeyId,
                homeEndpoint,
                encodedKey,
                sequenceNumber,
                sig);
    }

    /**
     * Verifies the home server's signature over this record.
     *
     * <p>Returns {@code false} rather than throwing on a bad signature, mirroring {@link
     * Ed25519Signatures#verify}: an invalid signature is an expected outcome for a record that arrives
     * from an untrusted server, not an error. The caller supplies the key {@link #homeServerDid()}
     * resolves to; this method does not itself resolve DIDs.
     *
     * @param homeServerKey the Ed25519 public key resolved from {@link #signingKeyId()} / {@link
     *     #homeServerDid()}
     * @return whether the signature is authentic for this record's contents
     */
    public boolean verify(PublicKey homeServerKey) {
        byte[] expected = signingBytes(
                accountDid,
                characterId,
                slot,
                homeServerDid,
                signingKeyId,
                homeEndpoint,
                homeTransportPublicKey,
                sequenceNumber);
        return Ed25519Signatures.verify(homeServerKey, expected, signature);
    }

    /**
     * The attested home-server transport key, decoded.
     *
     * @return the X25519 transport public key of the home server
     * @throws SecureChannelException if the stored bytes are not a valid X25519 key — which a server-side
     *     verifier already rejected, so this only fires on a record built without going through it
     */
    public PublicKey transportKey() {
        return X25519KeyExchange.decodePublicKey(homeTransportPublicKey);
    }

    @Override
    public byte[] homeTransportPublicKey() {
        return homeTransportPublicKey.clone();
    }

    @Override
    public byte[] signature() {
        return signature.clone();
    }
}
