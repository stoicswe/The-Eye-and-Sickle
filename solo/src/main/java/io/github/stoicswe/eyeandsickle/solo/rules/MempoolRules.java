package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.PendingTxState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The fee market: who gets into the next block, and who waits.
 *
 * <h2>The whole mechanic in three sentences</h2>
 *
 * A block holds {@link Balance#BLOCK_TRANSACTION_LIMIT} transactions. More than that are waiting.
 * Miners take the highest fee rates first, so a fee is a bid for one of a fixed number of slots — and
 * a queue is the only thing that makes a bid mean anything.
 *
 * <h2>⚠ The NPC mempool is derived, not stored, and that is not a shortcut</h2>
 *
 * Storing a few hundred synthetic transactions per session would grow the save for a readout that
 * shows a histogram and three projected blocks. Instead the backlog is a function of
 * {@code (blockSeed, height)}: deterministic, so the same chain state always shows the same queue, and
 * free. What <em>is</em> stored is the player's own pending transactions, because those are real state
 * — they paid for them.
 *
 * <p>The backlog drifts with height rather than with the wall clock, deliberately. A mempool that
 * changed while the game was paused would let a player watch the queue for a good moment without the
 * chain advancing, which is a way to game the fee market by doing nothing.
 */
public final class MempoolRules {

    private MempoolRules() {}

    /**
     * How many transactions the rest of the network has waiting, at this height.
     *
     * <p>Varies around {@link Balance#MEMPOOL_BASELINE_DEPTH} so the queue is sometimes short and a
     * cheap transaction gets lucky. That variance is the reason {@code ECONOMY} is a gamble on other
     * people's traffic rather than a fixed slower speed.
     */
    public static int backlog(SoloSave save) {
        if (save.chain == null) {
            return 0;
        }
        long mixed = mix(save.chain.blockSeed ^ (save.chain.height * 0x9E3779B97F4A7C15L));
        // ±60% around the baseline. Wide enough that a quiet block genuinely happens.
        double swing = ((mixed >>> 11) / (double) (1L << 53)) * 1.2d - 0.6d;
        return Math.max(0, (int) Math.round(Balance.MEMPOOL_BASELINE_DEPTH * (1 + swing)));
    }

    /**
     * The fee rate the cheapest slot in the next block is going for, in minor units.
     *
     * <p>Derived from the backlog: a deep queue prices the last slot high, a thin one lets the floor
     * in. This is the number a player is really deciding against when they pick a tier, and it is why
     * the mempool panel prints it.
     */
    public static double clearingFee(SoloSave save) {
        int waiting = backlog(save);
        int slots = Balance.BLOCK_TRANSACTION_LIMIT;
        if (waiting <= slots) {
            // Everyone gets in. The floor clears, and an economy transaction confirms immediately.
            return Balance.FEE_ECONOMY_MINOR_UNITS;
        }
        // Linear between the floor and priority as the queue deepens past one block. At three blocks'
        // worth of backlog the clearing price is at the priority rate — which is when paying it stops
        // being optional.
        double over = Math.min(1.0d, (waiting - slots) / (double) (slots * 2));
        return Balance.FEE_ECONOMY_MINOR_UNITS
                + over * (Balance.FEE_PRIORITY_MINOR_UNITS - Balance.FEE_ECONOMY_MINOR_UNITS);
    }

    /**
     * Adds one of the player's transactions to the mempool.
     *
     * <p>⚠ The hash is fixed here, at creation, and never changes when the transaction confirms. A
     * hash that changed on confirmation would make the explorer's pending row and its mined row two
     * different transactions, which is precisely the disagreement between readouts that
     * {@code docs/design/04-mining.md} §3.1 teaches a player to treat as evidence.
     */
    public static PendingTxState submit(
            SoloSave save,
            LedgerEntryState entry,
            FeeTier tier,
            String counterparty,
            boolean outgoing,
            Instant now) {
        PendingTxState tx = new PendingTxState();
        tx.entryId = entry.entryId;
        tx.txHash = ChainExplorer.txHash(save, entry);
        tx.createdAt = now;
        tx.valueMinorUnits = Math.abs(entry.deltaMinorUnits);
        tx.outgoing = outgoing;
        tx.counterparty = counterparty == null ? "" : counterparty;
        tx.feeTier = (tier == null ? FeeTier.STANDARD : tier).name();
        tx.feeMinorUnits = Balance.feeFor(tier);
        tx.kind = entry.type;
        tx.description = entry.description;

        entry.txHash = tx.txHash;
        entry.counterparty = tx.counterparty;
        // ⚠ -1 until a miner takes it. The explorer prints a dash, and a number here would claim a
        // block carried a transaction it does not.
        entry.blockNumber = -1L;

        save.chain.mempool.add(tx);
        save.chain.nonce++;
        return tx;
    }

    /**
     * Packs whatever fits into the block just mined, best fee rate first.
     *
     * <p>The player's transactions compete for the block's slots against the derived backlog. How many
     * slots are left over for them is {@link #slotsFor}: a priority fee beats most of the queue and a
     * floor fee beats it only when the queue is short.
     */
    public static List<PendingTxState> confirmInto(SoloSave save, long height, Instant now) {
        if (save.chain == null || save.chain.mempool.isEmpty()) {
            return List.of();
        }
        List<PendingTxState> queue = new ArrayList<>(save.chain.mempool);
        // Highest fee first, oldest first as the tiebreak — which is what a miner sorting on fee rate
        // does, and it stops two equal-fee transactions swapping places between renders.
        queue.sort(Comparator.<PendingTxState>comparingLong(tx -> -tx.feeMinorUnits)
                .thenComparing(tx -> tx.createdAt));

        List<PendingTxState> confirmed = new ArrayList<>();
        int slots = slotsFor(save);
        for (PendingTxState tx : queue) {
            if (confirmed.size() >= slots) {
                break;
            }
            if (tx.feeMinorUnits < Math.floor(clearingFee(save))) {
                // Outbid. It stays in the mempool and tries again next block, which is what a real
                // under-priced transaction does — it is not dropped.
                continue;
            }
            confirmed.add(tx);
        }
        for (PendingTxState tx : confirmed) {
            save.chain.mempool.remove(tx);
            stamp(save, tx, height);
        }
        if (!confirmed.isEmpty()) {
            EventLog.info(save, "chain",
                    confirmed.size() == 1
                            ? "transaction confirmed in block " + height + "."
                            : confirmed.size() + " transactions confirmed in block " + height + ".",
                    now);
        }
        return confirmed;
    }

    /**
     * How many of this block's slots the player's transactions can reach.
     *
     * <p>The block holds {@code BLOCK_TRANSACTION_LIMIT}; the derived backlog wants all of them. A
     * player is one wallet among a network, so they get the slots the backlog leaves plus a share of
     * the contested ones — never the whole block, which would make the queue theatre.
     */
    public static int slotsFor(SoloSave save) {
        int slots = Balance.BLOCK_TRANSACTION_LIMIT;
        int waiting = backlog(save);
        int free = Math.max(0, slots - waiting);
        // At least one contested slot, always. A mempool that could shut a paying transaction out
        // entirely for an arbitrary number of blocks turns a purchase into a wait of unbounded
        // length, and FeeTier promises every tier gets in eventually.
        return Math.max(1, free);
    }

    /** Marks a transaction mined, on both the pending record and the ledger row it belongs to. */
    private static void stamp(SoloSave save, PendingTxState tx, long height) {
        for (LedgerEntryState entry : save.ledger) {
            if (entry.entryId.equals(tx.entryId)) {
                entry.blockNumber = height;
                return;
            }
        }
    }

    /** splitmix64 finalizer. Same mixing the save's own Rng uses, so the two look alike. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
