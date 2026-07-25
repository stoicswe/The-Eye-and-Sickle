package io.github.stoicswe.eyeandsickle.server.discovery;

import java.util.List;
import java.util.Optional;

/**
 * The server-to-server calls discovery makes over the network, behind an interface so the gossip and
 * liveness logic can be tested without a network.
 *
 * <h2>REST now; a live-push seam later</h2>
 *
 * Inter-server transport is REST inside TLS for now ({@code
 * docs/architecture/07-transport-security.md}; the DID-authenticated channel is a {@code [PROPOSAL]}
 * that a cryptographer must review before it guards a live federation). This interface is the seam:
 * the production implementation ({@code RestClientPeerTransport}) uses a bounded {@code RestClient},
 * and a future push transport can replace it without touching {@link DiscoveryOrchestrator}.
 *
 * <h2>Every response is bounded and untrusted</h2>
 *
 * A peer list, a descriptor, and a probe all come from a server this one does not control. The
 * implementation must therefore cap what it reads — a peer that returns a million descriptors or a
 * gigabyte body is an attack, not a bug — and the caller verifies every descriptor before storing it.
 * These methods return raw descriptor JSON, never a parsed {@link ServerDescriptor}, precisely so that
 * verification cannot be skipped by getting a "trusted" object straight from the transport.
 */
public interface PeerTransport {

    /**
     * Fetches a peer's own signed self-descriptor — used to bootstrap from a seed endpoint, where the
     * peer's DID is not yet known.
     *
     * @param endpoint the peer's base endpoint URL
     * @return the raw self-descriptor JSON, or empty if the peer did not answer or answered with
     *     nothing usable
     */
    Optional<String> fetchSelfDescriptor(String endpoint);

    /**
     * Fetches a bounded sample of the peer's known directory — the peer-exchange call that lets the
     * network heal and grow without a central registry ({@code
     * docs/architecture/03-server-and-federation.md} §2).
     *
     * @param endpoint the peer's base endpoint URL
     * @param max the most descriptors to accept; the implementation must not return more, regardless of
     *     how many the peer offers
     * @return raw self-descriptor JSON strings, at most {@code max} of them; empty on any failure
     */
    List<String> fetchDirectory(String endpoint, int max);

    /**
     * Checks whether a peer is reachable, for the liveness signal.
     *
     * @param endpoint the peer's base endpoint URL
     * @return whether the peer answered
     */
    boolean probe(String endpoint);
}
