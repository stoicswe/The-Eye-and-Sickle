-- The rules engine's state, one row per character.
--
-- ⚠ THIS IS THE `engine` MIGRATION TIER, AND IT IS THE ONLY ONE SINGLE PLAYER RUNS.
--
-- The engine is one implementation (`solo`), driven by a server for LAN and federated play and by
-- the client in single player. Its state therefore has to live in the same shape in both, or the
-- store becomes two implementations again and the save format drifts between modes — which is the
-- duplication this split exists to remove.
--
-- So: three locations, applied in version order out of one shared history.
--
--   engine      (this file)   the rules engine's own state.     Solo runs THIS AND NOTHING ELSE.
--   core        V1..V6, V8    the authority tables — players, items, ledger, compute.
--   federation  V1001+        peers, validators, duels, the directory.
--
-- ⚠ NO FOREIGN KEY HERE, and its absence is the whole reason this file is separate. The reference to
-- `players` is real and worth having on a server, but `players` is an AUTHORITY table and single
-- player has no authority to model — there is one character, on one machine, and nothing to be
-- authoritative against. Requiring the table would drag the whole core tier (and with it the
-- federation vocabulary, the DID alias and the append-only triggers) into a mode that can use none
-- of it. `V8__character_game_state_owner.sql` adds the constraint where it means something.
--
-- ⚠ THE VERSION IS 7 AND SITS BETWEEN CORE'S V6 AND V8 ON PURPOSE. Flyway shares one history across
-- every configured location and applies in version order, so numbering this below V8 is what makes
-- "the table exists before the constraint that references it" true for a server. A server runs
-- engine + core (+ federation) and gets 1,2,…,6,7,8,1001,1002; single player configures the engine
-- location alone and gets 7. Flyway does not require the versions it applies to be contiguous.
--
-- ⚠ WHY `text` AND NOT `JSON`.
--
-- JSON would let SQL read inside a character's state, and that is precisely the reason not to use
-- it. The engine owns this document; a query that reached into it would be a SECOND way to read game
-- state, able to disagree with the first — and the disagreement would be invisible, because both
-- would look authoritative. Anything the server needs to answer in SQL (balance, heat, status) is
-- already a real column on `players`.
--
-- ⚠ Invariant I14 is unaffected by single player using this table. I14 governs whose machine holds
-- state that others must trust; a solo character is local-only and can never federate (the quarantine
-- rule, docs/architecture/12 §1), which is what has always made a player-editable save safe. It was
-- never the file format doing that work, so moving the same bytes into a local database changes
-- nothing about it — and on a server, this row is still the server's.
CREATE TABLE character_game_state (
    character_id uuid PRIMARY KEY,
    state        text        NOT NULL,
    format       integer     NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

COMMENT ON TABLE character_game_state IS
    'Rules-engine state per character. Opaque to SQL on purpose - see the V7 migration comment.';
