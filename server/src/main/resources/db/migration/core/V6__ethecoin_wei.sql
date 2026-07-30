-- V6 — ethecoin moves from hundredths to wei (18 decimal places).
--
-- WHY
-- ---
-- Ethecoin's scale was a [PROPOSAL] of two decimal places, chosen because two was the finest
-- granularity any published figure used. That missed what a currency players SEND EACH OTHER needs:
-- room for amounts nobody published. The scale is now Ethereum's — 1e-18 EC, the relationship wei
-- has to ether — so a player can send 0.037097927036961408 EC and have it mean exactly that.
--
-- WHY THE TYPE HAD TO CHANGE, NOT JUST THE MEANING
-- -----------------------------------------------
-- A Postgres `bigint` is int8, max 9223372036854775807. At 18 decimals that is 9.22 EC — less than
-- one firmware image costs. The columns are therefore `numeric(78,0)`: 78 digits, which is the width
-- the Ethereum ecosystem settled on for a uint256 and is far more than this economy will ever use.
-- Integral, not scaled: the value is a COUNT of the smallest unit, exactly as it was before, and
-- letting the database hold a fraction would reintroduce the rounding the integral model exists to
-- prevent.
--
-- WHY THE COLUMNS ARE RENAMED
-- ---------------------------
-- `_ec_minor` meant "integral hundredths". A column named that and holding wei is a lie that would
-- outlive everyone who knew better. The suffix is also load-bearing: `EconomyColumns` REFUSES to
-- read or bind an ethecoin value through a column whose name does not carry it, which is how the
-- I1 conversion (ethecoin into cycles) is made a loud failure rather than a plausible line of code.
-- Renaming the suffix keeps that guard pointing at the truth.
--
-- ⚠ THE MULTIPLY IS THE MIGRATION
-- -------------------------------
-- Every stored amount is 10^16 times too small under the new unit. `USING (col * 10^16)` is applied
-- in the same statement that widens the type, so there is no window in which the column is wide
-- enough to hold a wei amount but still contains an hundredths one. A balance left unmultiplied
-- would read as dust — 500 EC would become 0.000000000000005 EC — and nothing would report it.

-- players.ethecoin_balance_ec_minor → ethecoin_balance_wei
ALTER TABLE players
    DROP CONSTRAINT ck_players_balance_non_negative;
ALTER TABLE players
    ALTER COLUMN ethecoin_balance_ec_minor TYPE numeric(78, 0)
        USING (ethecoin_balance_ec_minor::numeric * 10 ^ 16);
ALTER TABLE players
    RENAME COLUMN ethecoin_balance_ec_minor TO ethecoin_balance_wei;
ALTER TABLE players
    ADD CONSTRAINT ck_players_balance_non_negative CHECK (ethecoin_balance_wei >= 0);
COMMENT ON COLUMN players.ethecoin_balance_wei IS
    'Spendable balance in wei (1e-18 EC). Materialised alongside the ledger row that moved it, in '
    'the same transaction, so the two can never disagree.';

-- ledger_transactions.amount_ec_minor → amount_wei
ALTER TABLE ledger_transactions
    DROP CONSTRAINT ck_ledger_amount;
ALTER TABLE ledger_transactions
    ALTER COLUMN amount_ec_minor TYPE numeric(78, 0)
        USING (amount_ec_minor::numeric * 10 ^ 16);
ALTER TABLE ledger_transactions
    RENAME COLUMN amount_ec_minor TO amount_wei;
ALTER TABLE ledger_transactions
    ADD CONSTRAINT ck_ledger_amount CHECK (amount_wei > 0);

-- deployed_miners.buffer_ec_minor → buffer_wei
ALTER TABLE deployed_miners
    DROP CONSTRAINT ck_deployed_miners_buffer;
ALTER TABLE deployed_miners
    ALTER COLUMN buffer_ec_minor TYPE numeric(78, 0)
        USING (buffer_ec_minor::numeric * 10 ^ 16);
ALTER TABLE deployed_miners
    RENAME COLUMN buffer_ec_minor TO buffer_wei;
ALTER TABLE deployed_miners
    ADD CONSTRAINT ck_deployed_miners_buffer CHECK (buffer_wei >= 0);
COMMENT ON COLUMN deployed_miners.buffer_wei IS
    'Accrued, uncollected yield in wei. Seizable — this is what a crack takes (design/04 §5.1).';
