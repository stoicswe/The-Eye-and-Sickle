/**
 * Item provenance: the signed, per-item event chain that proves an item's history is legitimate.
 *
 * <p>This is the positive half of the anti-cheat model ({@code
 * docs/architecture/03-server-and-federation.md} §4). Because anyone can self-host a server, a
 * dishonest one could try to mint items from nothing or rewrite their history. Provenance makes those
 * items <em>unverifiable</em>, and an unverifiable chain is not recognized — which is how a cheating
 * server's fabricated items become worthless everywhere except inside its own walls.
 *
 * <h2>Chains are per-item, not per-holder</h2>
 *
 * Decided in {@code docs/architecture/04-item-provenance.md} §6. Per-item chains are simpler to
 * verify and audit in isolation, which matters because items move between servers and players
 * independently. It also makes the player-facing requirement free: "show me this item's history" is
 * just walking {@code prevRecordHash} back to genesis.
 *
 * <h2>This package must not import the game package</h2>
 *
 * A signed payload has to be self-contained and independently verifiable years later, by someone with
 * only the record and a public key. If a payload could reference a live game type, verification would
 * start depending on game state — and the record would stop being the authoritative item definition
 * it is supposed to be. {@code ArchitectureRulesTest} enforces the direction.
 */
package io.github.stoicswe.eyeandsickle.protocol.provenance;
