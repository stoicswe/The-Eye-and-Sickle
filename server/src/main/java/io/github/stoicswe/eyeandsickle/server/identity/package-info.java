/**
 * Account identity, AT Protocol authentication (authentication-only), characters and their save slots,
 * sessions, the operator allowlist, faction commitment, and personal / server heat as stored state.
 *
 * <h2>The one boundary this package exists to hold</h2>
 *
 * AT Protocol is used to <em>authenticate</em> an account and obtain its DID, and for nothing else
 * ({@code docs/architecture/02-identity-and-auth.md} §3, Invariant I14). The DID is the portable,
 * stable account ID every other system keys off; the handle is display-only and may change without
 * breaking the mapping (§5). No game state is ever written to a player's PDS, and the client — or the
 * PDS — is never authoritative for anything a cheater would forge. Everything in this package treats
 * the AT Proto side as a source of a verified identity and treats this server's Postgres as the source
 * of truth for the characters it belongs to.
 *
 * <h2>Accounts and characters ({@code docs/architecture/09-player-state-portability.md} §1)</h2>
 *
 * A DID is an <em>account</em>; a {@code players} row is a <em>character</em> (a save slot with its own
 * ethecoin, rig, faction, heat and items). An account may hold up to
 * {@code eyeandsickle.characters.max-characters} (default 3) online, DID-bound characters, each in its
 * own slot; a local, DID-less character is exempt from the cap entirely. Sign-in authenticates the
 * account and enumerates its characters; creating, selecting and retiring one are the character
 * lifecycle, owned by {@link io.github.stoicswe.eyeandsickle.server.identity.CharacterService}. The
 * slot cap is soft (Invariant I15): it is enforced against a
 * {@link io.github.stoicswe.eyeandsickle.server.identity.RecognizedCharacterCount} seam that a
 * directory-aware deployment widens from this server's rows to the whole federation.
 *
 * <h2>What is real and what is a seam</h2>
 *
 * The parts that a self-hosted, authoritative server must own — the allowlist gate, character creation
 * and the slot cap, the character record, sessions, and the faction/heat state machine — are implemented
 * against the database. The network-calling half of AT Proto OAuth (dynamic client registration, PAR, DPoP,
 * handle→DID resolution against a live PDS) is behind {@link
 * io.github.stoicswe.eyeandsickle.server.identity.AtProtoIdentityProvider} so it can be supplied for
 * real without touching the authoritative logic, and so the build and tests do not depend on a live
 * PDS. See that interface and {@link
 * io.github.stoicswe.eyeandsickle.server.identity.DevAtProtoIdentityProvider} for exactly what is and
 * is not wired.
 */
package io.github.stoicswe.eyeandsickle.server.identity;
