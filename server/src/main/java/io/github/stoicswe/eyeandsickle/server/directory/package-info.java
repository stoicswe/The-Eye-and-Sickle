/**
 * The character directory — finding a character's home server from its account DID ("Option E").
 *
 * <h2>What this package is for</h2>
 *
 * Every online character has exactly one home server holding its authoritative state, and nowhere else
 * (Invariant I14, {@code docs/architecture/09-player-state-portability.md} §1.1). A player who signs in
 * from a new machine, with the same Bluesky account, needs to <em>find</em> that home from nothing but
 * their DID. This package is the answer: a lightweight, signed, gossip-safe directory mapping an account
 * DID to the current home of each of its characters — "DNS for your character" (09 §4). No game state
 * moves; only a location is read.
 *
 * <h2>What this package deliberately is NOT (Invariant I14, I15)</h2>
 *
 * A <strong>non-adversarial location index, never an authority.</strong> A home binding says "this
 * character's home is that server", and nothing about the character's items, ethecoin, heat or standing
 * — all of which live only in the home server's own Postgres and never travel as self-asserted data (03
 * §2). The directory is the safe half of continuity, exactly as {@code discovery} is the safe half of
 * "sync latest state": convergence is on a <em>signed monotonic sequence number</em> the home server
 * controls, never a wall clock an attacker can claim. It is the player-character analogue of the {@code
 * federation_peers} directory, and it carries the same anti-rollback trigger.
 *
 * <h2>The soft slot cap rides on it</h2>
 *
 * The cap of {@code EYEANDSICKLE_MAX_CHARACTERS} characters per account is enforced without a central
 * authority (I15): honest servers consult this directory and refuse a character beyond the recognized
 * count, and a defecting server's excess character is simply never a recognized binding (09 §2, 03 §4).
 * {@link io.github.stoicswe.eyeandsickle.server.directory.DirectoryRecognizedCharacterCount} is what
 * widens the identity slice's local {@code RecognizedCharacterCount} default from "this server" to "the
 * whole federation", superseding it with no change to the identity slice.
 *
 * <h2>Everything here treats its input as hostile</h2>
 *
 * Every published home record comes from an untrusted home server. So the input is bounded (record bytes,
 * directory size, resolution length), it is cryptographically verified before a byte becomes a row
 * ({@link io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeRecordVerifier}), and every
 * malformation — an oversized body, a bad DID, an out-of-range slot, a number that overflows {@code
 * long}, a signature that does not decode — is a typed refusal ({@link
 * io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeFault}), never an exception thrown into
 * the ingest path.
 *
 * <h2>Seams to other slices</h2>
 *
 * Where this package needs something another slice owns, it declares a narrow interface here rather than
 * reaching across: {@link io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeKeyResolver} (a
 * home server's DID&nbsp;-&gt;&nbsp;key, the identity slice). It consumes the identity slice's {@code
 * RecognizedCharacterCount} seam to publish the federation-wide count back. The signed wire record itself
 * is {@code protocol.channel.CharacterHomeRecord}, so the record's bytes stay reproducible on every
 * server that verifies them.
 */
package io.github.stoicswe.eyeandsickle.server.directory;
