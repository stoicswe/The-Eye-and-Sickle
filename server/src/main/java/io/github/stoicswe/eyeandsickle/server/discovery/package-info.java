/**
 * Peer discovery and state convergence between federated home servers.
 *
 * <h2>What this package is for</h2>
 *
 * A home server is self-hosted and there is no central registry ({@code
 * docs/architecture/03-server-and-federation.md} §2, §5). For cross-server play to work at all, a
 * server has to <em>find</em> other servers and keep a usable, current picture of how to reach them.
 * That is discovery: seed from configuration, then gossip peer lists so the network heals and grows
 * without anyone owning the list. The storage contract is the {@code federation_peers} table.
 *
 * <h2>The one thing this package deliberately does NOT do</h2>
 *
 * The originating request asked discovery to also "sync latest game state if newer" — i.e.
 * last-writer-wins replication keyed on a timestamp. <strong>That is not built, on purpose, because it
 * is an Invariant&nbsp;I15 violation.</strong> Adversarial, untrusted servers exist by design ({@code
 * 03} §1); "newest timestamp wins" is a win button for a cheating self-host, which simply claims a
 * newer clock and overwrites honest servers' state. The full reasoning, and what replaced it, is in
 * {@code docs/architecture/08-discovery-and-sync.md}.
 *
 * <p>The replacement is a split by data kind:
 *
 * <ul>
 *   <li><strong>Self-asserted, non-adversarial data</strong> — a server's own descriptor (endpoint,
 *       transport key, capabilities). Here last-writer-wins is safe because only that server may sign
 *       its own record, so convergence is on a <em>signed monotonic sequence number</em>, never a wall
 *       clock. {@link io.github.stoicswe.eyeandsickle.server.discovery.ServerDescriptorVerifier} and
 *       {@link io.github.stoicswe.eyeandsickle.server.discovery.PeerDirectoryService} implement this.
 *   <li><strong>Adversarial / shared state</strong> — item ownership and duel outcomes. Convergence is
 *       by cryptographic validity, never recency: a chain that verifies and properly extends a known
 *       chain is accepted; a conflicting chain is a <em>fork</em>, evidence of misbehaviour, routed to
 *       flagging rather than merged.
 *       {@link io.github.stoicswe.eyeandsickle.server.discovery.ProvenanceConvergence} decides this by
 *       reusing the protocol's {@code ProvenanceChainVerifier} — there is exactly one trust path.
 *   <li><strong>Flag evidence</strong> — converges on self-verifying evidence (both conflicting
 *       signatures exist), handed to the federation package that owns {@code flagged_servers} through
 *       the narrow {@link io.github.stoicswe.eyeandsickle.server.discovery.FederationFlagSink} seam.
 * </ul>
 *
 * <h2>Everything here treats its input as hostile</h2>
 *
 * Every descriptor, peer list, and probe response comes from an untrusted server. So every input is
 * bounded (directory size, peer-list length, descriptor bytes, gossip fan-out), every descriptor is
 * cryptographically verified before it is stored, and an unreachable or misbehaving peer is backed
 * off. Nothing in {@code federation_peers} is ever treated as adjudicating anything — it is a
 * low-trust index, not an authority (Invariant I14/I15).
 *
 * <h2>Seams to other slices</h2>
 *
 * Where this package needs something another slice owns, it declares a narrow interface here rather
 * than reaching across:
 * {@link io.github.stoicswe.eyeandsickle.server.discovery.PeerKeyResolver} (DID&nbsp;-&gt;&nbsp;key,
 * identity slice), {@link io.github.stoicswe.eyeandsickle.server.discovery.LocalDescriptorSource}
 * (this server's own signed descriptor, identity slice),
 * {@link io.github.stoicswe.eyeandsickle.server.discovery.FederationFlagSink} and
 * {@link io.github.stoicswe.eyeandsickle.server.discovery.PeerUptimeSource} (federation slice).
 */
package io.github.stoicswe.eyeandsickle.server.discovery;
