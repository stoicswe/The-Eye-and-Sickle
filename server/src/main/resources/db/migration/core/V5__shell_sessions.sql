-- Shell sessions (2026-07-28).
--
-- A player may hold an open shell on any machine they have a foothold on, and each one reserves
-- compute for as long as it stays open. That reservation is a compute_allocations row like any
-- other, so the only schema change it needs is one more value in the consumer vocabulary.
--
-- ⚠ Its OWN value, and specifically not folded into 'control_channel'. That one is the deployer's
-- half of Invariant I6, and its total is the self-correcting cap on how many miners a player can
-- run (docs/design/04-mining.md §2.2) — a cap that works only because the number means exactly one
-- thing. Any query that summed shells into it would tighten the miner cap every time a player
-- opened a window, and would make the rig monitor report miners nobody deployed.
--
-- A CHECK constraint cannot be extended in place, so it is dropped and re-added. That is a table
-- rewrite in the sense that Postgres re-validates every existing row against the new predicate —
-- which is exactly what should happen, and is cheap at this table's size.

ALTER TABLE compute_allocations
    DROP CONSTRAINT ck_compute_allocations_consumer;

ALTER TABLE compute_allocations
    ADD CONSTRAINT ck_compute_allocations_consumer CHECK (consumer_type IN (
        'active_tool', 'bot_frame', 'self_mining', 'control_channel',
        'deployed_miner', 'defensive_array', 'relay_hop', 'shell_session'));
