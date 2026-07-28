-- ---------------------------------------------------------------------------
-- V4 — the breach puzzle vocabulary drops from five classes to two.
--
-- docs/design/15-open-questions.md P-1 asked whether five classes were too many
-- and this answers it: two. `breach_protocol` is a spatial route through a code
-- matrix under a buffer budget; `offset_cipher` is arithmetic against a clock
-- that is deliberately absent. They differ along the axis the design cares
-- about — pressure of PLACE against pressure of PRECISION — where the five
-- differed mostly by re-skin, which is why proof-of-skill (Invariant I7) could
-- be farmed against whichever class a player found easiest and still read as
-- five separate competences.
--
-- ⚠ This migration is written to be safe on a database that already holds rows
-- and is written NOT to lose them. The three retired spellings are mapped onto
-- whichever survivor they most resembled, because breach_resolutions is the
-- proof-of-skill ledger: deleting a row would silently revoke an unlock a
-- player has already earned, which is the one failure mode a gate table must
-- not have. The mapping follows docs/design/16 §2's own table.
-- ---------------------------------------------------------------------------

ALTER TABLE breach_resolutions DROP CONSTRAINT ck_breach_resolutions_class;

UPDATE breach_resolutions
   SET puzzle_class = CASE puzzle_class
       -- Both were "read the board and route through it".
       WHEN 'enumeration' THEN 'breach_protocol'
       WHEN 'traversal'   THEN 'breach_protocol'
       -- Both were "get the value exactly right or lose attention for it".
       WHEN 'credential'  THEN 'offset_cipher'
       WHEN 'logic'       THEN 'offset_cipher'
       WHEN 'timing'      THEN 'offset_cipher'
       ELSE puzzle_class
   END
 WHERE puzzle_class IN ('enumeration', 'traversal', 'credential', 'logic', 'timing');

ALTER TABLE breach_resolutions ADD CONSTRAINT ck_breach_resolutions_class
    CHECK (puzzle_class IN ('breach_protocol', 'offset_cipher'));
