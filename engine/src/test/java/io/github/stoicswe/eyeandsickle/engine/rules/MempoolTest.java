package io.github.stoicswe.eyeandsickle.engine.rules;

import static io.github.stoicswe.eyeandsickle.engine.support.Money.ec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Pools;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fee market, the mempool, and the chain a player can actually inspect.
 *
 * <h2>What has to be true for any of this to mean anything</h2>
 *
 * A fee buys position in a queue. That needs three things to hold at once: the queue has to be longer
 * than a block, a higher fee has to actually get in sooner, and a low fee must not be stranded
 * forever. Break any one and the tiers become a cosmetic choice — which is the failure this class
 * exists to catch, because nothing on screen would say so.
 */
class MempoolTest {

    /**
     * Puts a rig at the top of the compute ladder.
     *
     * <h2>⚠ A starting rig is 24 cycles as of 2026-08-06, and these tests need room</h2>
     *
     * The allocations below were written when a starting rig was 100. They are about MINING — how a
     * fill competes, what a pool pays — and not about the compute ladder, so the fixture gives them
     * the rig their subject needs and {@code ComputeLadderTest} owns the ladder itself. Without it
     * the allocation is refused, the rig mines nothing, and the failure points at the chain.
     *
     * <p>⚠ Grants the ITEMS rather than writing {@code totalCycles}: the ceiling is derived and a
     * written one is stomped by the next reconcile, which is the anti-cheat property that derivation
     * exists for.
     */
    private static void atTopOfLadder(GameSave save) {
        for (var rung : io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()) {
            var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
            item.itemType = rung.itemType();
            item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
            save.items.add(item);
        }
        io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.reconcile(save);
    }


    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    /** A game with a controllable clock, at the chain's start height. */
    private static final class Rig {
        Instant now = T0;
        final GameEngine game;

        Rig(Path dir) {
            game = GameEngine.open(
                    io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                    "operator",
                    new java.time.Clock() {
                        public java.time.ZoneId getZone() {
                            return java.time.ZoneOffset.UTC;
                        }

                        public java.time.Clock withZone(java.time.ZoneId zone) {
                            return this;
                        }

                        public Instant instant() {
                            return now;
                        }
                    });
        }

        void advance(Duration by) {
            now = now.plus(by);
            game.tick();
        }

        GameSave save() {
            return game.state();
        }
    }

    @Nested
    @DisplayName("the chain a new character joins")
    class Genesis {

        @Test
        @DisplayName("⚠ starts at height 124, and all 124 blocks are inspectable")
        void startsAt124(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            assertThat(rig.game.mining().height()).isEqualTo(124L);

            // Every one of them renders, with a header and a body — which is the whole point of
            // deriving a block from its height rather than storing it. A chain that began at the
            // player's first session would say it had been waiting for them.
            for (long height = 0; height <= 124; height++) {
                ChainBlock block = rig.game.chainBlock(height);
                assertThat(block).as("block %d", height).isNotNull();
                assertThat(block.hash()).as("block %d hash", height).hasSize(66).startsWith("0x");
                assertThat(block.body()).as("block %d body", height).isNotEmpty();
                assertThat(block.transactions()).isEqualTo(block.body().size());
            }
        }

        @Test
        @DisplayName("each block names its parent, so the whole history is one chain")
        void hashesChain(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            for (long height = 1; height <= 124; height++) {
                // The property that makes it a chain rather than a list. If these ever stop lining
                // up, an explorer is showing a history that does not connect.
                assertThat(rig.game.chainBlock(height).parentHash())
                        .as("block %d parent", height)
                        .isEqualTo(rig.game.chainBlock(height - 1).hash());
            }
            // Genesis names all zeroes, because there is nothing before it.
            assertThat(rig.game.chainBlock(0).parentHash()).isEqualTo("0x" + "0".repeat(64));
        }

        @Test
        @DisplayName("a block renders identically every time it is asked for")
        void derivationIsStable(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainBlock first = rig.game.chainBlock(77);
            ChainBlock again = rig.game.chainBlock(77);
            // Storage-free rendering only works if it is deterministic. A hash that changed between
            // renders would make the explorer's own two readouts disagree.
            assertThat(again.hash()).isEqualTo(first.hash());
            assertThat(again.transactions()).isEqualTo(first.transactions());
            assertThat(again.body().getFirst().hash())
                    .isEqualTo(first.body().getFirst().hash());
        }

        @Test
        @DisplayName("two characters get different chains")
        void seedIsPerCharacter(@TempDir Path a, @TempDir Path b) {
            // Without a per-character seed every save would render identical hashes at identical
            // heights, and the chain would read as a shared fixture rather than each character's world.
            assertThat(new Rig(a).game.chainBlock(50).hash())
                    .isNotEqualTo(new Rig(b).game.chainBlock(50).hash());
        }

        @Test
        @DisplayName("⚠ block times jitter — no two gaps in the strip are the same 14.0 minutes")
        void blockTimesJitter(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            GameSave save = rig.save();
            long tip = save.chain.height;

            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            double total = 0;
            int gaps = 0;
            Instant previous = ChainExplorer.timestampOf(save, 0);
            for (long height = 1; height <= tip; height++) {
                Instant at = ChainExplorer.timestampOf(save, height);
                double minutes = Duration.between(previous, at).toSeconds() / 60.0d;
                lo = Math.min(lo, minutes);
                hi = Math.max(hi, minutes);
                total += minutes;
                gaps++;
                previous = at;
            }

            double target = Balance.CHAIN_TARGET_BLOCK_SECONDS / 60.0d;
            double band = ChainExplorer.TIMESTAMP_JITTER_SECONDS * 2 / 60.0d;
            // The band the strip actually draws in: 14 ± 6 minutes, so 8 to 20.
            assertThat(lo).isGreaterThanOrEqualTo(target - band);
            assertThat(hi).isLessThanOrEqualTo(target + band);
            // ⚠ Strictly positive, always. A block rendering before its own parent is what a naive
            // per-height offset produces the moment the jitter reaches half the interval.
            assertThat(lo).as("monotone").isPositive();
            // ⚠ And it really does vary. The whole point: this was 14.0 exactly, every gap, and the
            // strip read as a metronome.
            assertThat(hi - lo).as("spread").isGreaterThan(4.0d);
            // The jitters telescope, so the mean cannot drift from the interval printed above the
            // strip — a history that averaged 15 minutes under a "~14 min" heading is a readout
            // disagreeing with itself.
            assertThat(total / gaps).isCloseTo(target, within(0.05d));
        }

        @Test
        @DisplayName("⚠ the newest block renders at exactly lastBlockAt, which is a measurement")
        void theTipIsNotDisplaced(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            // The one timestamp the chain genuinely recorded. Jittering it would put the explorer
            // out of step with the mempool panel's "last one 3m ago", read off the same field.
            assertThat(ChainExplorer.timestampOf(rig.save(), rig.save().chain.height))
                    .isEqualTo(rig.save().chain.lastBlockAt);
        }
    }

    @Nested
    @DisplayName("the fee market")
    class Fees {

        @Test
        @DisplayName("⚠ the queue usually outruns a block, or a fee buys nothing")
        void thereIsAQueue(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            int deep = 0;
            long total = 0;
            // Sampled across heights rather than at one, because the backlog SWINGS — a quiet block
            // where everything clears is deliberate, and it is what makes ECONOMY a gamble on other
            // people's traffic rather than a fixed slower speed. What must hold is the average.
            for (int i = 0; i < 400; i++) {
                rig.save().chain.height++;
                int backlog = MempoolRules.backlog(rig.save());
                total += backlog;
                if (backlog > Balance.BLOCK_TRANSACTION_LIMIT) {
                    deep++;
                }
            }
            // The precondition for the entire mechanic: a block that could usually hold everything
            // waiting would make every tier confirm identically and the choice cosmetic.
            assertThat(total / 400.0d).isGreaterThan(Balance.BLOCK_TRANSACTION_LIMIT * 1.2d);
            assertThat(deep).as("heights where the queue outran a block").isGreaterThan(280);
            // ...and it must sometimes clear, or ECONOMY would never confirm cheaply.
            assertThat(deep).as("but not always").isLessThan(400);
        }

        @Test
        @DisplayName("⚠ the mempool's two rates are the same unit, so they can be compared")
        void ratesAreComparable(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "urgent", FeeTier.PRIORITY, "0x" + "ef".repeat(20));

            ChainMempool pool = rig.game.mempool();
            // The panel prints "cheapest slot X, top of the queue Y" side by side. Shipping one in
            // minor units and the other as a gas price made an under-4x spread look like 180x — and
            // once amounts were wei, a gas price printed as eighteen digits at the player. Both are
            // now AMOUNTS, which is the unit the fee tiers are quoted in and the one a player is
            // choosing between.
            assertThat(pool.highFeeWei()).isGreaterThanOrEqualTo(pool.lowFeeWei());
            assertThat(ec(pool.highFeeWei()) / ec(pool.lowFeeWei())).isLessThan(30.0d);
            // ⚠ The top of the queue IS what the player is paying — an amount, comparable by eye to
            // the tier they picked, rather than a derived rate they would have to divide back out.
            assertThat(pool.highFeeWei()).isEqualTo(Balance.feeFor(FeeTier.PRIORITY));
        }

        @Test
        @DisplayName("⚠ the network never routinely outbids the top tier a player can pay")
        void npcsDoNotOutbidPriority(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            BigInteger priority = Balance.feeFor(FeeTier.PRIORITY);
            for (long height = 1; height <= 124; height++) {
                for (ChainTransaction tx : rig.game.chainBlock(height).body()) {
                    // If the NPC population could routinely pay more than the most a player can, the
                    // top tier would buy nothing and the mechanic would read as broken rather than
                    // competitive — FeeTier's promise failing from the other side.
                    assertThat(tx.feeWei()).as("block %d tx fee", height).isLessThanOrEqualTo(priority);
                }
            }
        }

        @Test
        @DisplayName("a higher tier costs more and promises sooner")
        void tiersAreOrdered() {
            assertThat(Balance.feeFor(FeeTier.ECONOMY)).isLessThan(Balance.feeFor(FeeTier.STANDARD));
            assertThat(Balance.feeFor(FeeTier.STANDARD)).isLessThan(Balance.feeFor(FeeTier.PRIORITY));
            for (FeeTier tier : FeeTier.values()) {
                assertThat(tier.promise()).as("%s", tier).isNotBlank();
            }
        }

        @Test
        @DisplayName("⚠ the fee is negligible against income — it orders a queue, it is not a sink")
        void feesAreNotASink(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            java.math.BigInteger hourlyIncome =
                    Balance.SELF_MINING_WEI_PER_CYCLE_HOUR.multiply(java.math.BigInteger.valueOf(100));
            // docs/design/03 §4 lists the sinks the economy is balanced against and this is not one.
            // If a priority fee ever costs more than a percent of an hour's income it has become a
            // sink and §4 has to know about it.
            assertThat(Balance.feeFor(FeeTier.PRIORITY))
                    .isLessThan(hourlyIncome.divide(java.math.BigInteger.valueOf(100)));
        }

        @Test
        @DisplayName("the fee is charged on top, so a sender cannot short the recipient")
        void feeIsChargedOnTop(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("10"), "TEST", "seed");
            java.math.BigInteger before = rig.game.balance().wei();

            assertThat(rig.game.debit(Balance.ec("5"), "TRANSFER", "test", FeeTier.PRIORITY, "0x" + "ab".repeat(20)))
                    .isTrue();

            // Amount AND fee. Folding the fee into the amount would leave the recipient short and the
            // ledger's arithmetic wrong.
            assertThat(rig.game.balance().wei())
                    .isEqualTo(before.subtract(Balance.ec("5")).subtract(Balance.feeFor(FeeTier.PRIORITY)));
        }

        @Test
        @DisplayName("a sender who cannot cover amount plus fee is refused, and nothing moves")
        void insufficientFundsChangeNothing(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            java.math.BigInteger balance = rig.game.balance().wei();

            assertThat(rig.game.debit(balance, "TRANSFER", "test", FeeTier.PRIORITY, ""))
                    .as("spending the whole balance leaves nothing for the fee")
                    .isFalse();
            assertThat(rig.game.balance().wei()).isEqualTo(balance);
            assertThat(rig.game.mempool().pending()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a transaction's life")
    class Lifecycle {

        @Test
        @DisplayName("a spend enters the mempool, then confirms into a block")
        void pendingThenConfirmed(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("10"), "TRANSFER", "a purchase", FeeTier.PRIORITY, "0x" + "cd".repeat(20));

            ChainMempool pool = rig.game.mempool();
            assertThat(pool.yoursPending()).isEqualTo(1);
            ChainTransaction pending = pool.pending().getFirst();
            assertThat(pending.pending()).isTrue();
            assertThat(pending.blockNumber()).isNegative();
            String hash = pending.hash();

            // Run the chain until a block takes it. At 14 minutes a few hours is plenty.
            for (int i = 0; i < 40 && rig.game.mempool().yoursPending() > 0; i++) {
                rig.advance(Duration.ofMinutes(14));
            }
            assertThat(rig.game.mempool().yoursPending()).as("still waiting").isZero();

            ChainTransaction confirmed = rig.game.chainTransactions(50).stream()
                    .filter(tx -> tx.hash().equals(hash))
                    .findFirst()
                    .orElseThrow();
            // ⚠ The SAME hash. A hash that changed on confirmation would make the pending row and the
            // mined row two different transactions, which is exactly the readout disagreement
            // docs/design/04 §3.1 teaches a player to read as evidence of an intruder.
            assertThat(confirmed.blockNumber()).isNotNegative();
            assertThat(confirmed.pending()).isFalse();

            // And it is genuinely in that block's body, marked as the player's.
            ChainBlock block = rig.game.chainBlock(confirmed.blockNumber());
            assertThat(block.body()).anyMatch(tx -> tx.hash().equals(hash) && tx.yours());
        }

        @Test
        @DisplayName("a mining payout is a coinbase: no sender, no fee, no gas")
        void coinbaseHasNoSender(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            // ⚠ SOLO explicitly. This used to pass on the default (pooled) rig, which was the bug:
            // both modes credit SELF_MINING, so keying "is this minted?" off the type alone marked
            // every POOL payout as a coinbase — from the zero address, discarding the pool address
            // the engine had stamped on it. Only a block you won yourself mints anything.
            rig.game.setMiningMode(io.github.stoicswe.eyeandsickle.protocol.game.MiningMode.SOLO);
            atTopOfLadder(rig.game.state());
            rig.game.allocateSelfMining(50);

            // ⚠ Mine UNTIL a block is won, rather than for a fixed stretch. A solo rig at 90 cycles
            // expects a block about every 4.3 hours and the wait is exponential, so a fixed 5-hour
            // run finds nothing roughly 30% of the time — this test was flaky for exactly one run
            // between being switched to SOLO and this comment. The early break keeps it fast (~50
            // steps typically) and 50 simulated hours puts P(never) around one in a hundred
            // thousand. It cannot be made exact: the character's RNG seed is derived per character,
            // which is the bug fix that stopped every save generating an identical world.
            ChainTransaction payout = null;
            for (int i = 0; i < 600 && payout == null; i++) {
                rig.advance(Duration.ofMinutes(5));
                payout = rig.game.chainTransactions(50).stream()
                        .filter(ChainTransaction::coinbase)
                        .findFirst()
                        .orElse(null);
            }
            assertThat(payout).as("a solo rig eventually wins a block").isNotNull();
            // The coins did not exist before the block, so there is nobody to have sent them and no
            // transaction to have executed. Explorers really do render it this way.
            assertThat(payout.from()).isEqualTo(ChainTransaction.ZERO_ADDRESS);
            assertThat(payout.gasUsed()).isZero();
            assertThat(payout.feeWei()).isZero();
        }

        @Test
        @DisplayName("⚠ a pool payout is NOT a coinbase, and it names the pool that sent it")
        void poolPayoutNamesThePool(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            atTopOfLadder(rig.game.state());
            rig.game.allocateSelfMining(50);
            for (int i = 0; i < 60; i++) {
                rig.advance(Duration.ofMinutes(5));
            }
            ChainTransaction payout = rig.game.chainTransactions(50).stream()
                    .filter(tx -> "SELF_MINING".equals(tx.kind()))
                    .findFirst()
                    .orElseThrow();

            // The pool paid this out of its own balance, so it has a sender — which is exactly why
            // LedgerEntryState refuses to stamp a block number on it either.
            assertThat(payout.coinbase()).isFalse();
            assertThat(payout.from()).isNotEqualTo(ChainTransaction.ZERO_ADDRESS);
            assertThat(payout.from()).isEqualTo(ChainExplorer.addressOf(Pools.byId(Pools.DEFAULT_ID)));
            // And the ledger prints the name rather than the hex, because this is the row a player
            // most needs to recognise. The address is still carried, so §3.1's audit still works.
            assertThat(payout.counterpartyLabel())
                    .isEqualTo(Pools.byId(Pools.DEFAULT_ID).name());
        }

        @Test
        @DisplayName("a label is only ever attached to an address the client can verify")
        void strangersGetNoLabel(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "to a stranger", FeeTier.STANDARD, "0x" + "ab".repeat(20));
            ChainTransaction sent = rig.game.chainTransactions(10).stream()
                    .filter(tx -> "TRANSFER".equals(tx.kind()))
                    .findFirst()
                    .orElseThrow();
            // A pool address is derived from a public id, so matching one is a fact. Anything else
            // gets no name: a label rendered where an address belongs is how a transfer from a
            // stranger gets mistaken for a payout.
            assertThat(sent.counterpartyLabel()).isEmpty();
        }

        @Test
        @DisplayName("the explorer and the ledger are one list, and cannot disagree")
        void oneListTwoRenderings(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            atTopOfLadder(rig.game.state());
            rig.game.allocateSelfMining(50);
            for (int i = 0; i < 40; i++) {
                rig.advance(Duration.ofMinutes(7));
            }
            var ledger = rig.save().ledger;
            var chain = rig.game.chainTransactions(1000);
            assertThat(chain).hasSameSizeAs(ledger);

            // Newest first against the ledger's oldest first, same amounts, same running balances.
            // docs/design/04 §3.1 makes "add these up and compare against the balance" the way a
            // player catches a hidden miner, so these two surfaces must be incapable of disagreeing.
            for (int i = 0; i < chain.size(); i++) {
                var entry = ledger.get(ledger.size() - 1 - i);
                assertThat(chain.get(i).valueWei()).as("row %d value", i).isEqualTo(entry.deltaWei.abs());
                assertThat(chain.get(i).balanceAfterWei())
                        .as("row %d balance", i)
                        .isEqualTo(entry.balanceAfterWei);
            }
        }
    }

    @Nested
    @DisplayName("replace-by-fee")
    class Boosting {

        private ChainTransaction pendingOn(Rig rig) {
            return rig.game.mempool().queued().getFirst().tx();
        }

        @Test
        @DisplayName("a boost charges the difference, not the whole new fee")
        void chargesTheDifference(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("5"), "TRANSFER", "Sent", FeeTier.ECONOMY, "0xabc");
            java.math.BigInteger before = rig.game.balance().wei();
            java.math.BigInteger was = pendingOn(rig).feeWei();

            var boost = MempoolRules.boost(rig.game.state(), pendingOn(rig).hash(), FeeTier.PRIORITY, rig.game.now());

            assertThat(boost.ok()).isTrue();
            java.math.BigInteger difference = Balance.FEE_PRIORITY_WEI.subtract(was);
            assertThat(boost.paidWei()).isEqualTo(difference);
            // ⚠ The DIFFERENCE. The first fee was debited when the transaction was broadcast, so
            // charging the new tier in full would take it twice — and the player would end up having
            // paid more than priority costs.
            assertThat(rig.game.balance().wei()).isEqualTo(before.subtract(difference));
            assertThat(pendingOn(rig).feeWei()).isEqualTo(Balance.FEE_PRIORITY_WEI);
        }

        /**
         * ⚠ BOTH records, because they are read at different times.
         *
         * <p>The pending record is what {@code confirmInto} sorts on and what the mempool panel
         * draws; the ledger row is what the explorer reads once the transaction is mined and the
         * pending record has been deleted. Updating one would make a boosted transaction sort at its
         * new fee and render at its old one.
         */
        @Test
        @DisplayName("the boost reaches the ledger row as well as the pending record")
        void bothRecordsMove(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("5"), "TRANSFER", "Sent", FeeTier.ECONOMY, "0xabc");
            String hash = pendingOn(rig).hash();
            MempoolRules.boost(rig.game.state(), hash, FeeTier.PRIORITY, rig.game.now());

            var entry = rig.game.state().ledger.stream()
                    .filter(e -> "TRANSFER".equals(e.type))
                    .findFirst()
                    .orElseThrow();
            assertThat(entry.feeWei).isEqualTo(Balance.FEE_PRIORITY_WEI);
        }

        /**
         * ⚠ Real replace-by-fee has this rule for a harder reason than tidiness: a replacement that
         * paid less would let anyone rewrite a transaction the network has already relayed, for free,
         * as many times as they liked.
         */
        @Test
        @DisplayName("a bump only ever goes up")
        void neverDownwards(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("5"), "TRANSFER", "Sent", FeeTier.PRIORITY, "0xabc");
            java.math.BigInteger before = rig.game.balance().wei();

            for (FeeTier down : new FeeTier[] {FeeTier.ECONOMY, FeeTier.STANDARD, FeeTier.PRIORITY}) {
                var boost = MempoolRules.boost(rig.game.state(), pendingOn(rig).hash(), down, rig.game.now());
                assertThat(boost.ok()).as("%s", down).isFalse();
                assertThat(boost.refusal()).isEqualTo(MempoolRules.BoostRefusal.NOT_HIGHER);
            }
            assertThat(rig.game.balance().wei()).isEqualTo(before);
        }

        @Test
        @DisplayName("a confirmed transaction cannot be boosted, and says why")
        void notAfterItIsMined(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("5"), "TRANSFER", "Sent", FeeTier.PRIORITY, "0xabc");
            String hash = pendingOn(rig).hash();

            MempoolRules.confirmInto(rig.game.state(), rig.game.state().chain.height + 1, rig.game.now());
            assertThat(rig.game.state().chain.mempool).isEmpty();

            var boost = MempoolRules.boost(rig.game.state(), hash, FeeTier.PRIORITY, rig.game.now());
            assertThat(boost.refusal()).isEqualTo(MempoolRules.BoostRefusal.NOT_PENDING);
            // The ordinary case, not an error — it confirmed while the player was deciding.
            assertThat(boost.message()).contains("already been mined");
        }

        @Test
        @DisplayName("a boost that cannot be afforded takes nothing")
        void cannotAfford(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.FEE_ECONOMY_WEI.add(Balance.ec("0.1")), "TEST", "seed");
            rig.game.debit(Balance.ec("0"), "TRANSFER", "Sent", FeeTier.ECONOMY, "0xabc");
            java.math.BigInteger before = rig.game.balance().wei();

            var boost = MempoolRules.boost(rig.game.state(), pendingOn(rig).hash(), FeeTier.PRIORITY, rig.game.now());
            assertThat(boost.refusal()).isEqualTo(MempoolRules.BoostRefusal.CANNOT_AFFORD);
            assertThat(rig.game.balance().wei()).isEqualTo(before);
            assertThat(pendingOn(rig).feeWei()).isEqualTo(Balance.FEE_ECONOMY_WEI);
        }

        /** The whole point: a boosted transaction is packed before one that did not boost. */
        @Test
        @DisplayName("boosting moves a transaction ahead of one that did not")
        void boostingJumpsTheQueue(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "first, cheap", FeeTier.ECONOMY, "0xaaa");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "second, cheap", FeeTier.ECONOMY, "0xbbb");

            var second = rig.game.state().chain.mempool.get(1);
            MempoolRules.boost(rig.game.state(), second.txHash, FeeTier.PRIORITY, rig.game.now());

            assertThat(rig.game.mempool().queued().getFirst().tx().hash())
                    .as("the fee-sorted queue puts the boosted one first")
                    .isEqualTo(second.txHash);
        }
    }

    @Nested
    @DisplayName("the projections")
    class Projections {

        @Test
        @DisplayName("three to five blocks ahead, and none of them is a promise")
        void projectsThreeToFiveBlocks(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainMempool pool = rig.game.mempool();
            // 3–5 since 2026-07-29, derived from chain state. How far a queue can honestly be read
            // ahead is a property of the queue, and a fixed three claimed otherwise.
            assertThat(pool.projected().size()).isBetween(MempoolRules.MIN_PROJECTIONS, MempoolRules.MAX_PROJECTIONS);
            for (int i = 0; i < pool.projected().size(); i++) {
                ChainMempool.ProjectedBlock p = pool.projected().get(i);
                assertThat(p.index()).isEqualTo(i);
                assertThat(p.transactions()).isLessThanOrEqualTo(Balance.BLOCK_TRANSACTION_LIMIT);
                // Further out means longer, on average. The type's own comment carries the warning
                // that this is a queue snapshot and not a schedule.
                assertThat(p.expectedSeconds(pool.expectedNextBlockSeconds()))
                        .isEqualTo(pool.expectedNextBlockSeconds() * (i + 1));
            }
        }

        /**
         * ⚠ The depth must not change between two reads of the same chain state.
         *
         * <p>The panel repaints once a second ({@code Views.ledger}'s {@code refreshClock}). A drawn
         * count would add and remove a card on every repaint, which reads as the interface glitching
         * rather than as the chain being uncertain — so the depth is derived from
         * {@code (blockSeed, height)} and this is the assertion that keeps it that way.
         */
        @Test
        @DisplayName("the depth is stable per height, not redrawn per repaint")
        void projectionDepthIsStable(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            int first = rig.game.mempool().projected().size();
            for (int repaint = 0; repaint < 32; repaint++) {
                assertThat(rig.game.mempool().projected()).hasSize(first);
            }
        }

        /**
         * ⚠ The estimate and the block that replaces it are ONE number, arrived at once.
         *
         * <p>A projection card is superseded by a block card at the same height, and a player who
         * read "fees 32.40 EC" and then saw "fees 7.60 EC" two minutes later has been shown a
         * contradiction about mining income — which is precisely what
         * {@code docs/design/04-mining.md} §3.1 trains them to treat as evidence of tampering. So the
         * projection does not estimate from the queue's depth; it asks {@code MempoolRules} the same
         * question the mined card asks, at the same height.
         */
        @Test
        @DisplayName("a projected block's fees are the fees the block actually pays")
        void projectedFeesMatchTheMinedBlock(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainMempool pool = rig.game.mempool();
            long height = rig.game.state().chain.height;

            for (ChainMempool.ProjectedBlock p : pool.projected()) {
                long at = height + 1 + p.index();
                assertThat(p.feesWei())
                        .as("projection %d must agree with block %d", p.index(), at)
                        .isEqualTo(MempoolRules.blockFeesWei(rig.game.state(), at));
                assertThat(p.transactions())
                        .as("and so must its transaction count")
                        .isEqualTo(MempoolRules.blockTransactionCount(rig.game.state(), at));
            }
        }

        /**
         * ⚠ The fee total must be non-zero on a projection the player has nothing waiting in.
         *
         * <p>That is the whole bug: the field carried <em>this rig's</em> fees, so every card on a
         * quiet wallet read "fees 0.00 EC" — a block explorer reporting that mining the next block is
         * worth nothing. A regression to the old meaning would show up here and nowhere else, because
         * the value is arithmetically fine, just about the wrong subject.
         */
        @Test
        @DisplayName("fees are the block's, so a player with nothing queued still sees a real total")
        void feesAreTheBlocksNotTheRigs(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainMempool pool = rig.game.mempool();
            assertThat(pool.queued()).isEmpty();

            assertThat(pool.projected()).allSatisfy(p -> {
                assertThat(p.yours()).isZero();
                assertThat(p.feesWei())
                        .as("mining the next block is not worth nothing")
                        .isPositive();
                // Sanity on the magnitude: a block carries 12–200 transactions between the floor and
                // priority fee, so the total has to sit inside those bounds by construction.
                assertThat(p.feesWei())
                        .isBetween(
                                Balance.FEE_ECONOMY_WEI.multiply(java.math.BigInteger.valueOf(12)),
                                Balance.FEE_PRIORITY_WEI.multiply(
                                        java.math.BigInteger.valueOf(Balance.BLOCK_TRANSACTION_LIMIT)));
            });
        }

        /** ⚠ A card can never render more transactions than a block holds — the fill bar caps at 100%. */
        @Test
        @DisplayName("a projection never overflows the block limit, however the queue falls")
        void projectionsNeverOverflow(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            for (int block = 0; block < 200; block++) {
                for (ChainMempool.ProjectedBlock p : rig.game.mempool().projected()) {
                    assertThat(p.transactions()).isBetween(0, Balance.BLOCK_TRANSACTION_LIMIT);
                    assertThat(p.yours()).isLessThanOrEqualTo(p.transactions());
                    assertThat(p.fullness()).isBetween(0.0d, 1.0d);
                }
                rig.game.state().chain.height++;
            }
        }

        /** And it does actually vary — a rule that always returned 3 would pass the two above. */
        @Test
        @DisplayName("the depth varies as the chain advances")
        void projectionDepthVaries(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (int block = 0; block < 400; block++) {
                seen.add(MempoolRules.projectionDepth(rig.game.state()));
                rig.game.state().chain.height++;
            }
            assertThat(seen).as("every depth in the range is reachable").containsExactlyInAnyOrder(3, 4, 5);
        }

        @Test
        @DisplayName("a priority transaction projects into the next block; the queue is what it beats")
        void priorityProjectsSooner(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "urgent", FeeTier.PRIORITY, "0x" + "ef".repeat(20));

            ChainMempool pool = rig.game.mempool();
            int placed = pool.projected().stream()
                    .mapToInt(ChainMempool.ProjectedBlock::yours)
                    .sum();
            // It is in one of the projections rather than nowhere. FeeTier promises every tier gets
            // in eventually, and a projection set that never contained a paying transaction would
            // mean the queue could strand it.
            assertThat(placed).isEqualTo(1);
        }

        @Test
        @DisplayName("the expected interval is an average and the elapsed time is a fact")
        void theIntervalIsStillAnAverage(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainMempool pool = rig.game.mempool();
            // The ETA added on 2026-07-27 is derived from this and does not replace it. A countdown
            // published without the average it estimates would read as a deadline.
            assertThat(pool.expectedNextBlockSeconds()).isEqualTo(Balance.CHAIN_TARGET_BLOCK_SECONDS);
            assertThat(pool.secondsSinceLastBlock()).isNotNegative();
        }
    }

    /**
     * The estimate a player counts down against, and the four properties that keep it honest.
     *
     * <p>See {@code ChainMempool}'s type comment: an ETA is published, but it is anchored rather than
     * accumulated, it is allowed to be overtaken, and it must never be the engine's own draw.
     */
    @Nested
    @DisplayName("the confirmation estimate")
    class Estimate {

        @Test
        @DisplayName("every projection is anchored on the last block at the mean interval")
        void anchoredOnTheLastBlock(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            ChainMempool pool = rig.game.mempool();
            Instant last = rig.save().chain.lastBlockAt;
            for (ChainMempool.ProjectedBlock p : pool.projected()) {
                assertThat(p.etaAt())
                        .as("projection %d", p.index())
                        .isEqualTo(last.plusSeconds(Balance.CHAIN_TARGET_BLOCK_SECONDS * (p.index() + 1L)));
            }
        }

        @Test
        @DisplayName("it holds still while the chain does, which is what lets a client tick it down")
        void holdsStillBetweenBlocks(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            Instant before = rig.game.mempool().projected().getFirst().etaAt();

            // A minute of wall clock with no block found. An ETA recomputed from `now` would slide
            // forward by exactly this minute and the countdown would sit still forever — which is
            // the frozen readout this whole change exists to fix, arrived at from the other side.
            long height = rig.save().chain.height;
            rig.advance(Duration.ofSeconds(60));
            if (rig.save().chain.height != height) {
                return; // A block landed; the anchor is supposed to move. Tested below.
            }
            assertThat(rig.game.mempool().projected().getFirst().etaAt()).isEqualTo(before);
        }

        @Test
        @DisplayName("a landed block moves the anchor forward whole")
        void aBlockMovesTheAnchor(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            Instant before = rig.game.mempool().projected().getFirst().etaAt();
            long height = rig.save().chain.height;
            for (int i = 0; i < 240 && rig.save().chain.height == height; i++) {
                rig.advance(Duration.ofSeconds(60));
            }
            assertThat(rig.save().chain.height).as("a block eventually lands").isGreaterThan(height);
            assertThat(rig.game.mempool().projected().getFirst().etaAt()).isAfter(before);
        }

        @Test
        @DisplayName("⚠ it is never the engine's own draw — being overdue must stay unobservable")
        void doesNotPublishTheDraw(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            // The engine really does know: networkWorkTarget is drawn up front, so the true seconds
            // to the next block are (target − done) × mean and sitting right there. Publishing it
            // would make "overdue" a fact a player could read, which is precisely the gambler's
            // fallacy ChainState exists to refuse to teach.
            double mean = ChainRules.expectedSeconds(rig.save().chain.difficulty, rig.save().chain.networkHashrate);
            double trueRemaining = (rig.save().chain.networkWorkTarget - rig.save().chain.networkWorkDone) * mean;
            long published = Duration.between(
                            rig.now, rig.game.mempool().projected().getFirst().etaAt())
                    .toSeconds();

            assertThat(published).isEqualTo(Balance.CHAIN_TARGET_BLOCK_SECONDS);
            // The draw is exponential, so agreeing with the mean to the second would be a one-in-a-
            // million coincidence — and would mean the published figure had been derived from it.
            assertThat(Math.abs(trueRemaining - published)).isGreaterThan(1.0d);
        }

        @Test
        @DisplayName("the percentile is the Erlang CDF, so past the estimate is the ordinary case")
        void thePercentileIsExact() {
            // Shape 1 at exactly the mean: 1 − e⁻¹. The number the UI shows the instant an estimate
            // is overtaken, and the reason it says "running long" rather than "overdue".
            assertThat(ChainMempool.erlangCdf(1, 1.0d)).isCloseTo(0.6321d, within(1e-4));
            // Shape 2 at twice the mean: 1 − e⁻²(1 + 2).
            assertThat(ChainMempool.erlangCdf(2, 2.0d)).isCloseTo(0.5940d, within(1e-4));
            assertThat(ChainMempool.erlangCdf(1, 0.0d)).isZero();
            assertThat(ChainMempool.erlangCdf(3, 100.0d)).isCloseTo(1.0d, within(1e-9));
        }

        @Test
        @DisplayName("a waiting transaction carries the block it projects into, and that block's ETA")
        void queuedCarriesItsProjection(@TempDir Path dir) {
            Rig rig = new Rig(dir);
            rig.game.credit(Balance.ec("500"), "TEST", "seed");
            rig.game.debit(Balance.ec("1"), "TRANSFER", "urgent", FeeTier.PRIORITY, "0x" + "ef".repeat(20));

            ChainMempool pool = rig.game.mempool();
            assertThat(pool.queued()).hasSize(1);
            ChainMempool.Queued q = pool.queued().getFirst();
            assertThat(q.beyondProjection()).isFalse();
            // Its estimate is its projected block's, because that is what it is actually waiting for.
            assertThat(q.etaAt())
                    .isEqualTo(pool.projected().get(q.projectedIndex()).etaAt());
            assertThat(pool.pending().getFirst()).isSameAs(q.tx());
        }
    }
}
