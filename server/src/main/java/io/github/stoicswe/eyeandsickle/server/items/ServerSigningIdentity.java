package io.github.stoicswe.eyeandsickle.server.items;

import java.security.PublicKey;
import java.util.Map;

/**
 * This server's signing identity: the {@link ProvenanceSigner} it uses to issue records, plus the
 * public keys a verifier resolves for records it signed itself.
 *
 * <p>Extends {@link ProvenanceSigner} because signing and self-verification are two halves of one
 * identity — the same key pair both signs a mint and, later, is what lets this server re-verify that
 * mint when the item is traded away and comes home. Splitting them into two beans would let a
 * misconfiguration leave a server able to sign with a key it cannot then resolve, which reads on a peer
 * as a server signing with a key nobody recognizes.
 *
 * <p>There are exactly two implementations: {@link LoadedSigningIdentity} once a key is configured, and
 * {@link MissingSigningIdentity} when none is — the latter refuses to sign loudly rather than letting
 * the server invent a fresh identity ({@link ServerSigningProperties}).
 */
public interface ServerSigningIdentity extends ProvenanceSigner {

    /**
     * The public keys this server can resolve locally, keyed by {@code kid}.
     *
     * <p>Contains this server's own signing key when a public key is configured, so {@link
     * DidSigningKeyDirectory} can verify records this server signed without a network round-trip
     * ({@code docs/architecture/04-item-provenance.md} §6.2). Empty when signing is unconfigured, or
     * when only the private key was supplied — in which case records this server signs are still
     * resolvable by peers through DID resolution, just not by this server offline.
     *
     * @return an immutable {@code kid -> public key} map, possibly empty
     */
    Map<String, PublicKey> localVerificationKeys();

    /**
     * This server's DID, or {@code null} if signing is not configured — the non-throwing form the
     * recognition and directory wiring need, which must tolerate an unconfigured server.
     *
     * @return the issuer DID or {@code null}
     */
    String issuerDidOrNull();
}
