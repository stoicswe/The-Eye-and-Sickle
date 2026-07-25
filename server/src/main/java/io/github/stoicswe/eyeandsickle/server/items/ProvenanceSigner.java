package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;

/**
 * Signs the canonical bytes of a provenance payload with this server's own key.
 *
 * <p>The one place a server acts as an <em>issuer</em> rather than a verifier. Minting an item, and
 * granting or trading one, all reduce to producing a {@link SignatureBlock} over the canonical payload
 * ({@code docs/architecture/04-item-provenance.md} §2). Kept behind an interface for two reasons: the
 * private key is loaded from configuration and must be swappable without touching the minting logic,
 * and a test can sign with a freshly generated key pair instead of reading a file.
 *
 * <p>Only single-issuer events go through here. A {@code duel_grant} is signed by a validator committee
 * ({@code 04} §3.1), not by one server, and this server never holds the authority to produce one on
 * its own — Invariant I15.
 */
public interface ProvenanceSigner {

    /**
     * This server's DID — the {@code issuerDid} that appears in every record it signs, and which the
     * verifier checks equals the signing key's DID.
     *
     * @return the issuer DID
     * @throws IllegalStateException if no signing identity is configured
     */
    String issuerDid();

    /**
     * The {@code kid} carried in the signature block, of the form {@code did:...#key1}.
     *
     * @return the key identifier
     * @throws IllegalStateException if no signing identity is configured
     */
    String signingKeyId();

    /**
     * Signs a canonicalized payload.
     *
     * @param canonicalPayloadBytes output of {@link
     *     io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson#canonicalBytes} — never
     *     raw JSON, or the signature will not reproduce on a verifier
     * @return the EdDSA signature block, {@code kid} set to {@link #signingKeyId()}
     * @throws IllegalStateException if no signing identity is configured
     */
    SignatureBlock sign(byte[] canonicalPayloadBytes);

    /** Whether this server actually holds a key it can sign with. */
    boolean canSign();
}
