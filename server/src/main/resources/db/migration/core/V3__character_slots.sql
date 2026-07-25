-- ===========================================================================
-- V3 — character slots.
--
-- A DID stops being a player and becomes an ACCOUNT
-- (docs/architecture/09-player-state-portability.md §1): "An account is a DID —
-- one Bluesky/AT Proto identity" and "A character is a save slot ... exactly what
-- a players row already models." An account may hold up to
-- EYEANDSICKLE_MAX_CHARACTERS characters (default 3) in online, DID-bound play.
--
-- This migration is the schema half of 09 §8. It keeps the `players` table and
-- the `Player` record as the character entity and adds the slot + status + account
-- concepts around it, rather than renaming anything.
--
--   * The single-row-per-DID assumption (uq_players_did) is what blocks 3 slots,
--     so it is dropped and uniqueness MOVES to (did, slot) — one row per slot per
--     account, exactly the shape 09 §8 names.
--   * A DID-bound (online) character has a slot in [1..bound]; a local, DID-less
--     character (docs/architecture/02-identity-and-auth.md §4 option 1;
--     players.did nullable since V2) has slot NULL and is OUTSIDE this system:
--     no slot, no cap, no directory, no federation (09 §1).
--   * The slot CHECK here is a GENEROUS structural bound, not the product cap.
--     The real cap (default 3) is EYEANDSICKLE_MAX_CHARACTERS — a soft, product
--     knob enforced in the service from config against a signed character
--     directory (09 §2), NOT a database constant. Invariant I15 forbids a single
--     arbiter, so the cap cannot be a hard, central database rule; the database
--     only guarantees the structural facts (one row per slot, slots within a sane
--     range). Keeping the DB bound generous (1..16) also means migrated/retired
--     shells that retain their slot number (09 §6.1) never exhaust the slot space
--     while the live cap stays at 3.
--
-- Nothing here is authoritative over the cap. Honest servers enforce it; a
-- defecting server can mint a 4th character on its own turf, and that character is
-- simply not recognized federation-wide (09 §2, docs/architecture/03 §4). This
-- schema is the local, structural substrate under that soft rule.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- Drop the single-character-per-DID assumption.
--
-- V2's uq_players_did UNIQUE(did) is precisely what stops an account holding more
-- than one character (09 §8: "players.did loses its UNIQUE constraint; uniqueness
-- moves to (did, slot)"). It also happens to be what PlayerRepository's old
-- INSERT ... ON CONFLICT (did) sign-in upsert keyed on — that upsert is replaced,
-- in the same change, by an explicit create-character path, because "create on
-- first sign-in" is no longer the model: sign-in yields an account and its
-- characters, and character creation is a separate, cap-checked step (09 §1-§2).
-- ---------------------------------------------------------------------------
ALTER TABLE players DROP CONSTRAINT uq_players_did;


-- ---------------------------------------------------------------------------
-- The two new columns (09 §8).
--
--   slot   — which save slot this character occupies within its account. NULL for
--            a local (DID-less) character, which is exempt from the whole system.
--   status — the character lifecycle. 'active' is a live, playable character;
--            'migrated' has moved its authoritative home elsewhere (09 §5, §6) and
--            'retired' has been decommissioned. Both terminal states are a shell
--            the old home keeps for dispute/audit (09 §6.1, open question
--            Q-retire-window) and can never be played or migrated again — that is
--            the no-double-play rule (09 §6.1) made representable.
--
-- status defaults to 'active' so every pre-existing V2 row becomes a live
-- character with no backfill needed for it.
-- ---------------------------------------------------------------------------
ALTER TABLE players ADD COLUMN slot   smallint NULL;
ALTER TABLE players ADD COLUMN status text     NOT NULL DEFAULT 'active';


-- ---------------------------------------------------------------------------
-- Backfill existing DID-bound rows to slot 1.
--
-- Before this migration uq_players_did guaranteed at most one row per DID, so each
-- existing account has exactly one character: it becomes slot 1. Local rows
-- (did IS NULL) keep slot NULL. This has to run BEFORE the pairing CHECK below,
-- because a DID-bound row with a NULL slot would violate it — a migration that
-- added the constraint first would refuse to apply on any server that already has
-- players, which is the hardest kind of failure to recover from.
-- ---------------------------------------------------------------------------
UPDATE players SET slot = 1 WHERE did IS NOT NULL AND slot IS NULL;


-- ---------------------------------------------------------------------------
-- The constraints.
-- ---------------------------------------------------------------------------

-- The status vocabulary. text + CHECK, never a PostgreSQL ENUM, for the same
-- reason V2 gives: enum values are cheap to add and effectively impossible to
-- remove or rename, and this whole feature is [PROPOSAL] (09 header). The Java
-- side (identity/CharacterStatus) maps these with an exhaustive switch, so a
-- renamed constant is a compile error and CharacterSlotsMigrationTest proves the
-- Java spellings and this list agree.
ALTER TABLE players ADD CONSTRAINT ck_players_status
    CHECK (status IN ('active', 'migrated', 'retired'));

-- did and slot agree, in both directions: a DID-bound character HAS a slot, and a
-- local character has neither. This is the schema-level statement of "the 3-slot
-- rule is a property of online, DID-bound play only" (09 §1). It also mirrors the
-- Java Player record's own pairing invariant, so an in-memory value that would be
-- rejected here cannot be constructed and reach an INSERT.
ALTER TABLE players ADD CONSTRAINT ck_players_slot_pairing
    CHECK ((did IS NULL) = (slot IS NULL));

-- A generous structural bound, NOT the product cap (see the file header). 1..16
-- leaves ample headroom above the default 3-character cap so retired/migrated
-- shells that keep their slot number (09 §6.1) do not exhaust the space, while
-- still rejecting a nonsense slot like 0 or 5000. The live cap is enforced in the
-- service from EYEANDSICKLE_MAX_CHARACTERS, because it is a soft/product limit
-- (09 §2), not a database invariant.
ALTER TABLE players ADD CONSTRAINT ck_players_slot_bound
    CHECK (slot IS NULL OR slot BETWEEN 1 AND 16);

-- Uniqueness MOVES here from did to (did, slot): one character per slot per
-- account (09 §8). PostgreSQL treats NULLs as distinct in a UNIQUE constraint by
-- default, so every local (NULL, NULL) row is permitted — local play is uncapped
-- and un-slotted, exactly as intended. For DID-bound rows this is what stops two
-- characters colliding on the same slot, and it is the structural backstop under
-- the (soft) cap when two creations race (09 §2, open question Q-cap-race).
ALTER TABLE players ADD CONSTRAINT uq_players_did_slot UNIQUE (did, slot);


-- ---------------------------------------------------------------------------
-- Documentation.
-- ---------------------------------------------------------------------------
COMMENT ON COLUMN players.slot IS
    'Save slot within the account (docs/architecture/09-player-state-portability.md §1). '
    '1..16 structural bound for a DID-bound character; NULL for a local, DID-less character (exempt from the cap). '
    'The product cap (default 3) is enforced in the service from EYEANDSICKLE_MAX_CHARACTERS, not here (09 §2).';
COMMENT ON COLUMN players.status IS
    'Character lifecycle (docs/architecture/09 §6.1): active | migrated | retired. '
    'migrated/retired are terminal shells kept for audit; a character in either state can never be played or '
    'migrated again (no double-play).';
