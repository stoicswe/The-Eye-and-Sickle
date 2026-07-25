/**
 * Wire types shared by the JavaFX client and the Spring Boot home server, plus the item-provenance
 * verifier that both sides must run identically.
 *
 * <h2>The charter</h2>
 *
 * This module is <em>not</em> a "common" junk drawer. It holds exactly two things:
 *
 * <ol>
 *   <li>Records, enums and sealed types describing what crosses the wire.
 *   <li>The provenance chain verifier, which {@code docs/architecture/04-item-provenance.md} §6.2
 *       requires to run client-side and offline so a player can confirm an item's history
 *       <em>without trusting the server's UI</em> to have checked it.
 * </ol>
 *
 * It holds no game rules. No thresholds, no prices, no yields, no compute-recovery curves, no gate
 * evaluation, no clock and no randomness. Those are authoritative state, and authoritative state
 * belongs to the server — Invariant I14: the client is never authoritative over anything a cheater
 * would forge.
 *
 * <p>{@code ArchitectureRulesTest} enforces this at build time, because a charter that is only prose
 * is a charter that erodes.
 *
 * <h2>Package layering</h2>
 *
 * <pre>
 *   game  ──►  provenance  ──►  crypto  ◄──  channel
 * </pre>
 *
 * Dependencies point one way only, and {@code crypto} is the shared floor.
 *
 * <ul>
 *   <li>{@code provenance} must never import {@code game}: a signed provenance payload must not be
 *       able to reference a live game value like a compute budget or a faction reputation, or the
 *       signed record stops being self-contained and independently verifiable.
 *   <li>{@code channel} — the authenticated encrypted transport ({@code
 *       docs/architecture/07-transport-security.md}) — must never import {@code game} or {@code
 *       provenance}. A transport that knows what it is carrying invites protocol logic to leak into
 *       framing, and the two have completely different threat models: the channel defends bytes in
 *       flight, provenance defends items across years and across servers.
 * </ul>
 *
 * <p>One consequence worth stating: {@code provenance} and {@code game} may not read a clock or an
 * RNG (their serialization must be byte-reproducible), while {@code crypto} and {@code channel}
 * must (ephemeral keys and nonces are their entire job). {@code ArchitectureRulesTest} scopes that
 * rule accordingly rather than applying one blanket ban.
 *
 * @see <a href="../../../../../../../../docs/architecture/04-item-provenance.md">architecture/04 —
 *     Item Provenance</a>
 */
package io.github.stoicswe.eyeandsickle.protocol;
