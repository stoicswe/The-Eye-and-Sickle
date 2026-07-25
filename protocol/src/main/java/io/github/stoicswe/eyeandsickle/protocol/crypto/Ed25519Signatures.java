package io.github.stoicswe.eyeandsickle.protocol.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Ed25519 (EdDSA) signing and verification for item provenance and validator-quorum outcomes.
 *
 * <p>Two reasons this algorithm and no other, both from {@code
 * docs/architecture/04-item-provenance.md} §5: Ed25519 has smaller keys and signatures and faster
 * verification than RSA, and it is the DID key type AT Protocol already uses (§{@code 02} §5). So the
 * game runs <em>one</em> crypto stack for player identity and item provenance rather than two.
 *
 * <p>There is deliberately no third-party crypto dependency here. Ed25519 has been in {@code
 * java.base} via the SunEC provider since JDK 15, so BouncyCastle would add several megabytes — and a
 * module that would have to be jlinked into the client image — for an algorithm the platform already
 * ships.
 */
public final class Ed25519Signatures {

    /** JCA algorithm name. Also the value of the {@code alg} field's EdDSA JOSE mapping. */
    public static final String ALGORITHM = "Ed25519";

    /** The {@code alg} value that appears in a provenance envelope's signature block. */
    public static final String JOSE_ALG = "EdDSA";

    private Ed25519Signatures() {}

    /**
     * Generates a fresh Ed25519 key pair.
     *
     * <p>Intended for tests and for a home server minting its own signing identity at first boot.
     * Player keys come from AT Protocol DIDs and are never generated here.
     *
     * @return a new key pair
     */
    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Ed25519 unavailable on this JVM; it is required for provenance signing", e);
        }
    }

    /**
     * Signs canonical bytes.
     *
     * @param privateKey the issuer's Ed25519 private key
     * @param canonicalBytes output of {@link JsonCanonicalization#canonicalize(String)} — never raw,
     *     uncanonicalized JSON, or the signature will not reproduce
     * @return the detached 64-byte signature
     */
    public static byte[] sign(PrivateKey privateKey, byte[] canonicalBytes) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(canonicalBytes);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign provenance payload", e);
        }
    }

    /**
     * Verifies a detached signature over canonical bytes.
     *
     * <p>Returns {@code false} rather than throwing on a bad signature: an invalid signature is an
     * expected outcome here, not an error. A chain that fails verification is simply not recognized
     * (see {@code docs/architecture/03-server-and-federation.md} §4), which is how a cheating
     * server's fabricated items become worthless across the federation.
     *
     * @param publicKey the public key the {@code kid} resolved to
     * @param canonicalBytes the canonical payload bytes
     * @param signatureBytes the detached signature
     * @return whether the signature is valid for this key and these bytes
     */
    public static boolean verify(PublicKey publicKey, byte[] canonicalBytes, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(canonicalBytes);
            return signature.verify(signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
