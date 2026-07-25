package io.github.stoicswe.eyeandsickle.protocol.provenance;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import java.util.Objects;

/**
 * One signature over a canonicalized provenance payload.
 *
 * <p>From {@code docs/architecture/04-item-provenance.md} §3. A single-issuer record carries exactly
 * one of these; a duel outcome carries one per validator that signed (§3.1).
 *
 * @param alg JOSE algorithm identifier; always {@code EdDSA}
 * @param kid the DID fragment identifying the signing key, e.g. {@code did:plc:yyyyyyyy#key1}
 * @param sig base64url-encoded detached signature bytes
 */
public record SignatureBlock(String alg, String kid, String sig) {

    public SignatureBlock {
        Objects.requireNonNull(alg, "alg");
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(sig, "sig");
    }

    /**
     * Creates a signature block using the one algorithm this game signs with.
     *
     * @param kid the DID fragment identifying the signing key
     * @param sig base64url-encoded detached signature bytes
     * @return the block
     */
    public static SignatureBlock eddsa(String kid, String sig) {
        return new SignatureBlock(Ed25519Signatures.JOSE_ALG, kid, sig);
    }

    /**
     * The DID portion of {@link #kid()}, i.e. everything before the {@code #} fragment.
     *
     * <p>This is the identity a verifier resolves to a public key, and the identity whose validator
     * reputation gets updated after a quorum round.
     *
     * @return the signer's DID
     */
    public String signerDid() {
        int fragment = kid.indexOf('#');
        return fragment < 0 ? kid : kid.substring(0, fragment);
    }
}
