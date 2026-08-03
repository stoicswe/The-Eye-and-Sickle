-- The rules engine's state, one row per character.
--
-- ⚠ WHY `text` AND NOT `jsonb`.
--
-- jsonb would let SQL read inside a character's state, and that is precisely the reason not to use
-- it. The engine owns this document; a query that reached into it would be a SECOND way to read game
-- state, able to disagree with the first — and the disagreement would be invisible, because both
-- would look authoritative. Anything the server needs to answer in SQL (balance, heat, status) is
-- already a real column on `players`.
--
-- ⚠ Invariant I14: this is the server's database. The engine runs here, against this row. A client's
-- copy is a render.
CREATE TABLE character_game_state (
    character_id uuid PRIMARY KEY REFERENCES players (player_id) ON DELETE CASCADE,
    state        text        NOT NULL,
    format       integer     NOT NULL,
    updated_at   timestamptz NOT NULL
);

COMMENT ON TABLE character_game_state IS
    'Rules-engine state per character. Opaque to SQL on purpose - see V7 migration comment.';
