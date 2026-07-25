/**
 * Character migration across home servers — Options B and C of
 * {@code docs/architecture/09-player-state-portability.md} (§5, §6, §6.1).
 *
 * <h2>Two paths, one hard line between them (§3)</h2>
 *
 * A character's state splits cleanly into what is <em>cryptographically yours</em> (your DID and your
 * provenanced items, verifiable by any server) and what a server <em>merely asserts</em> (ethecoin
 * balance, rig/compute, personal heat, faction reputation). Everything in this slice follows from that
 * split, and the two migration paths sit on opposite sides of it:
 *
 * <ul>
 *   <li><strong>Option C — verifiable migration to an untrusted destination.</strong> You carry only the
 *       portable half in a {@link io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle}.
 *       The destination re-verifies every item chain with {@code ProvenanceChainVerifier} before
 *       recognizing it, mints a <em>fresh</em> character, and <em>resets</em> the economy — because those
 *       values cannot be trusted from an untrusted source. This is
 *       {@link io.github.stoicswe.eyeandsickle.server.migration.CharacterExportService#exportForMigration}
 *       plus {@link io.github.stoicswe.eyeandsickle.server.migration.CharacterImportService#importVerified}.
 *   <li><strong>Option B — cooperative migration between operators who trust each other.</strong> A
 *       {@link io.github.stoicswe.eyeandsickle.server.migration.TrustedCharacterExport} carries the whole
 *       character, economy included, because both operators cooperate. It is guarded so it is
 *       <em>operator</em>-authenticated, never player-triggered — importing a full-state bundle from an
 *       untrusted source would be an I14 / §3 violation, which is exactly why B and C are different types.
 *       The full-fidelity operational form of B is a PostgreSQL dump/restore (see {@code deploy/BACKUP.md});
 *       state never leaves trusted hands, so no invariant is touched.
 * </ul>
 *
 * <h2>The three security rules every path obeys (§6.1)</h2>
 *
 * <ul>
 *   <li><strong>No double-play.</strong> The source character is marked {@code migrated} (the identity
 *       core's one-way {@code CharacterService.markMigrated}) before the destination character becomes
 *       live; a migrated character cannot be played or migrated again.
 *   <li><strong>No rollback / no fork.</strong> The home binding's sequence only advances. A bundle
 *       presenting a stale sequence is refused with {@link
 *       io.github.stoicswe.eyeandsickle.server.migration.StaleHomeSequenceException} — the same
 *       monotonicity the discovery descriptors enforce (§4). The binding itself lives in the character
 *       <em>directory</em> (§4, Option E), owned by another slice; this slice depends on it only through
 *       the narrow {@link io.github.stoicswe.eyeandsickle.server.migration.CharacterHomeDirectory} seam.
 *   <li><strong>Provenance is re-verified at the destination.</strong> The bundle is untrusted transport;
 *       the chains inside it carry their own proof, checked afresh, never trusted from the bundle.
 * </ul>
 *
 * <h2>Invariant posture (I14, I15)</h2>
 *
 * Character state still lives only in a home server's Postgres (I14): C imports discard the untrusted
 * economy and recognize only provenance-verified items; the economy reset is a fresh character row, never
 * an edit of the old one (§8). The 3-slot cap and the home bindings are enforced by honest servers via
 * non-recognition, not by any central authority (I15): the fresh character is created through the core's
 * cap-checked path, and the directory seam is where a defecting home is out-voted, not overruled.
 */
package io.github.stoicswe.eyeandsickle.server.migration;
