package io.github.stoicswe.eyeandsickle.server.discovery;

import java.util.Optional;

/**
 * Supplies this server's own identity for discovery: its DID, and its current signed self-descriptor.
 *
 * <h2>A seam to the identity slice</h2>
 *
 * Producing a signed self-descriptor requires this server's DID Ed25519 private key, which the identity
 * slice holds ({@code docs/architecture/02-identity-and-auth.md}) — discovery must never hold a signing
 * key. So discovery declares what it needs as this interface and the identity/federation slice provides
 * it, minting a descriptor with a monotonic sequence number via {@link ServerDescriptorCodec#sign}.
 *
 * <p>Discovery uses it for two things: serving {@code GET /federation/descriptor} so peers can learn
 * this server's transport key, and knowing its own DID so gossip never re-ingests this server into its
 * own directory. Both degrade gracefully when no descriptor is available — a purely local server has
 * no descriptor and no need of one — which is why both methods return {@link Optional}.
 */
public interface LocalDescriptorSource {

    /**
     * This server's DID, if it has one.
     *
     * @return the DID, or empty for a local-only server with no federation identity
     */
    Optional<String> localServerDid();

    /**
     * This server's current signed self-descriptor envelope, verbatim.
     *
     * @return the descriptor JSON, or empty if this server does not federate or has not minted one
     */
    Optional<String> currentSelfDescriptor();

    /** A source for a server that has no federation identity — the local-only default. */
    static LocalDescriptorSource none() {
        return new LocalDescriptorSource() {
            @Override
            public Optional<String> localServerDid() {
                return Optional.empty();
            }

            @Override
            public Optional<String> currentSelfDescriptor() {
                return Optional.empty();
            }
        };
    }
}
