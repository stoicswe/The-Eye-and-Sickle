-- Ties the engine's state row to the player who owns it.
--
-- ⚠ THIS CONSTRAINT IS THE CORE TIER'S, NOT THE ENGINE TIER'S, AND THAT IS THE POINT OF THE SPLIT.
-- `V7__engine_state.sql` creates `character_game_state` with no foreign key so that single player can
-- run the engine tier alone — one character on one machine has no `players` table to point at, and
-- requiring one would pull the whole authority model into a mode with no authority to model.
--
-- On a server the reference is real and worth enforcing: a character's state belongs to a player, and
-- ON DELETE CASCADE is what stops a deleted player leaving an orphan row holding their whole game.
-- Deleting the state separately would be a second place to remember, and the one that gets forgotten.
ALTER TABLE character_game_state
    ADD CONSTRAINT fk_character_game_state_player
    FOREIGN KEY (character_id) REFERENCES players (player_id) ON DELETE CASCADE;
