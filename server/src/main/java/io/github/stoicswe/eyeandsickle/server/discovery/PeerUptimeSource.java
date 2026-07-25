package io.github.stoicswe.eyeandsickle.server.discovery;

import java.util.Optional;

/**
 * Exposes discovery's measured peer reachability to the federation slice, which folds it into the
 * validator sampling weight ({@code docs/architecture/05-validator-quorum.md} §2.2).
 *
 * <h2>A seam pointing outward</h2>
 *
 * Discovery <em>produces</em> this; the federation slice <em>consumes</em> it. Discovery is the layer
 * that actually dials peers and knows whether they answer, so reachability is measured here. But how
 * reachability becomes the {@code uptime} term of {@code weight = reputation × uptime} — including the
 * γ no-show decay of {@code 05} §4 — is a quorum policy the federation slice owns. This interface is
 * the boundary: a measurement crosses it, never a finished score.
 *
 * <p>Declaring the interface in the discovery package (rather than reaching into the federation
 * package) keeps the dependency direction explicit and lets the federation slice depend on a small,
 * intention-revealing type instead of on {@link FederationPeerRepository}.
 */
public interface PeerUptimeSource {

    /**
     * The measured reachability of a peer.
     *
     * @param peerDid the peer's DID
     * @return the measurement, or empty if this server has never heard of the peer
     */
    Optional<PeerLiveness> livenessOf(String peerDid);
}
