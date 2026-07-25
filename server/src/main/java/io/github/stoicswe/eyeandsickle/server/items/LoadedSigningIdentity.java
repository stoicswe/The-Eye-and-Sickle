package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.crypto.Ed25519Signatures;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SignatureBlock;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * A configured signing identity backed by a real Ed25519 key.
 *
 * <p>Produced by {@link ServerSigningKeyLoader} from {@link ServerSigningProperties}. Holds the private
 * key in memory for the life of the process — that is unavoidable for a server that signs on demand,
 * and it is why the key file itself is kept off disk-in-repo and out of logs.
 *
 * <p>Signatures are emitted as unpadded base64url, the JOSE convention the verifier decodes with
 * {@code Base64.getUrlDecoder()} ({@code
 * io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceChainVerifier}).
 */
final class LoadedSigningIdentity implements ServerSigningIdentity {

    private final String did;
    private final String kid;
    private final PrivateKey privateKey;
    private final Map<String, PublicKey> localKeys;

    /**
     * @param did this server's DID
     * @param kid the full key identifier, {@code did#keyId}
     * @param privateKey the Ed25519 private key that signs
     * @param publicKey the matching public key, or {@code null} if it was not configured
     */
    LoadedSigningIdentity(String did, String kid, PrivateKey privateKey, PublicKey publicKey) {
        this.did = Objects.requireNonNull(did, "did");
        this.kid = Objects.requireNonNull(kid, "kid");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        // Only the server's own key goes in the local set. Peer keys are resolved through DID
        // resolution at verification time, not held here, because this map is meant to be small and
        // authoritative rather than a cache that can go stale.
        this.localKeys = publicKey == null ? Map.of() : Map.of(kid, publicKey);
    }

    @Override
    public String issuerDid() {
        return did;
    }

    @Override
    public String issuerDidOrNull() {
        return did;
    }

    @Override
    public String signingKeyId() {
        return kid;
    }

    @Override
    public SignatureBlock sign(byte[] canonicalPayloadBytes) {
        Objects.requireNonNull(canonicalPayloadBytes, "canonicalPayloadBytes");
        byte[] signature = Ed25519Signatures.sign(privateKey, canonicalPayloadBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return SignatureBlock.eddsa(kid, encoded);
    }

    @Override
    public boolean canSign() {
        return true;
    }

    @Override
    public Map<String, PublicKey> localVerificationKeys() {
        return localKeys;
    }
}
