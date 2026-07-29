package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.PendingTxState;
import io.github.stoicswe.eyeandsickle.solo.state.StoredFileState;
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
        return save.chain == null ? 0 : backlogAt(save, save.chain.height);
    }

    /**
     * The same queue depth at a stated height — what the panel's projections pack against.
     *
     * <h2>⚠ Each projected block gets its OWN queue, and draining one snapshot was wrong</h2>
     *
     * The projections used to subtract from a single current backlog: the next block took 200 of
     * ~300, the one after took the remaining ~100, and everything past that rendered <b>empty</b>.
     * With a fixed three cards that put one dead card at the end of the strip; at 3–5 it would put up
     * to three, and a card reading "0 txs" claims the chain is about to go quiet, which is a
     * prediction the model does not make and the fee market flatly contradicts.
     *
     * <p>The queue does not drain, because transactions keep arriving — a real mempool at steady
     * state has inflow roughly equal to throughput, which is exactly why there is a fee market to
     * bid into at all. A queue that emptied after two blocks would price every later slot at the
     * floor, and {@link FeeTier}'s middle tiers would stop meaning anything.
     *
     * <p>Derived per height rather than modelled as an arrival rate: the depth already varies ±60%
     * with height, so reading it at {@code height + n} gives each future block a plausible and
     * <em>stable</em> queue for free, and stability is what stops the strip reshuffling on a panel
     * that repaints once a second.
     */
    public static int backlogAt(SoloSave save, long height) {
        if (save.chain == null) {
            return 0;
        }
        long mixed = mix(save.chain.blockSeed ^ (height * 0x9E3779B97F4A7C15L));
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
        return save.chain == null
                ? Balance.FEE_ECONOMY_MINOR_UNITS
                : clearingFeeAt(save, save.chain.height);
    }

    /**
     * The same price at a stated height, so each projected block quotes its own.
     *
     * <p>It is a function of that block's queue depth ({@link #backlogAt}), so a strip of five cards
     * shows the price moving with the queue rather than repeating one figure five times — which is
     * the whole reason a player would look at more than the next block.
     */
    public static double clearingFeeAt(SoloSave save, long height) {
        int waiting = backlogAt(save, height);
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
        // ⚠ Copied onto the LEDGER row, not left on the pending record alone. confirmInto removes
        // the pending record, so a fee that lived only there vanished at the exact moment the
        // transaction became interesting to look at.
        entry.feeMinorUnits = tx.feeMinorUnits;
        // ⚠ -1 until a miner takes it. The explorer prints a dash, and a number here would claim a
        // block carried a transaction it does not.
        entry.blockNumber = -1L;

        save.chain.mempool.add(tx);
        save.chain.nonce++;
        return tx;
    }

    // ================================================================== replace-by-fee

    /** Why a boost was refused. */
    public enum BoostRefusal {
        /** No transaction under that hash is waiting — most often because it already confirmed. */
        NOT_PENDING,

        /**
         * ⚠ The new fee is not higher than the old one.
         *
         * <p>Real replace-by-fee has the same rule and for a harder reason than tidiness: a
         * replacement that paid <em>less</em> would let anyone rewrite a transaction the network has
         * already relayed, at no cost, as many times as they liked. A bump only ever goes up.
         */
        NOT_HIGHER,

        /** The difference cannot be afforded. */
        CANNOT_AFFORD
    }

    /** What a boost did, or why it did not. */
    public record Boost(boolean ok, BoostRefusal refusal, long paidMinorUnits, String message) {

        static Boost refused(BoostRefusal refusal, String message) {
            return new Boost(false, refusal, 0L, message);
        }
    }

    /**
     * Raises a waiting transaction's fee — <b>replace-by-fee</b>.
     *
     * <h2>What this is a model of</h2>
     *
     * A transaction sitting in a mempool is not committed to anything. Its sender can rebroadcast it
     * offering more, and miners — who sort by fee rate — will prefer the new version. Bitcoin calls
     * this RBF, and it is the mechanism behind every "stuck transaction, bump the fee" support thread
     * on the internet. It is worth modelling because it is the piece that makes a fee feel like a
     * <em>bid</em> rather than a price: a player who under-paid, watched their purchase sit three
     * blocks out, and paid the difference to jump the queue has learned what a fee market is in a way
     * no tooltip achieves.
     *
     * <h2>⚠ Only the DIFFERENCE is charged, and that is not a discount</h2>
     *
     * The original fee was already debited when the transaction was broadcast, so charging the full
     * new fee would take it twice. What the player ends up having paid is exactly the new tier's fee,
     * which is what a replacement costs.
     *
     * <p>⚠ Both records are updated. The pending transaction is what {@link #confirmInto} sorts on and
     * what the mempool panel draws; the ledger row is what the block explorer reads once it is mined
     * and the pending record is gone ({@code LedgerEntryState.feeMinorUnits}). Updating one would make
     * a boosted transaction sort at the new fee and render at the old one.
     *
     * <h2>⚠ The hash does NOT change</h2>
     *
     * A real replacement is a different transaction with a different txid; this one keeps its hash.
     * That is a deliberate simplification, for the reason {@link #submit} already gives: a hash that
     * changed underneath the player would make the pending row and the mined row two different
     * transactions on a readout {@code docs/design/04-mining.md} §3.1 asks them to reconcile. The
     * simplification is stated in {@code rbf(7)} rather than hidden.
     *
     * @param txHash the waiting transaction, as the panel shows it
     * @param tier the tier to raise it to
     */
    public static Boost boost(SoloSave save, String txHash, FeeTier tier, Instant now) {
        if (save == null || save.chain == null || txHash == null) {
            return Boost.refused(BoostRefusal.NOT_PENDING, "no such transaction is waiting.");
        }
        PendingTxState tx = save.chain.mempool.stream()
                .filter(pending -> txHash.equals(pending.txHash))
                .findFirst()
                .orElse(null);
        if (tx == null) {
            // Overwhelmingly the ordinary case rather than an error: it confirmed while the player
            // was deciding. Said as what happened, not as a failure.
            return Boost.refused(BoostRefusal.NOT_PENDING,
                    "that transaction is no longer waiting — it has already been mined.");
        }
        long wanted = Balance.feeFor(tier);
        if (wanted <= tx.feeMinorUnits) {
            return Boost.refused(BoostRefusal.NOT_HIGHER,
                    "a replacement has to offer more than the transaction it replaces. This one is "
                            + "already paying " + tier(tx) + ".");
        }
        long difference = wanted - tx.feeMinorUnits;
        if (!LedgerRules.canDebit(save, difference)) {
            return Boost.refused(BoostRefusal.CANNOT_AFFORD,
                    "not enough ethecoin to raise the fee — the difference is "
                            + money(difference) + ".");
        }

        LedgerRules.apply(save, -difference, "TX_FEE",
                "Fee boost to " + tier.label().toLowerCase(java.util.Locale.ROOT)
                        + " (" + money(difference) + " on top)", now);
        String was = tier(tx);
        tx.feeTier = tier.name();
        tx.feeMinorUnits = wanted;
        for (LedgerEntryState entry : save.ledger) {
            if (entry.entryId.equals(tx.entryId)) {
                entry.feeMinorUnits = wanted;
                break;
            }
        }
        EventLog.info(save, "chain",
                "fee boosted " + was + " -> " + tier.label().toLowerCase(java.util.Locale.ROOT)
                        + " on " + shortHash(tx.txHash) + "; miners sort by fee rate, so it moves up "
                        + "the queue.", now);
        return new Boost(true, null, difference,
                "boosted to " + tier.label().toLowerCase(java.util.Locale.ROOT) + " for "
                        + money(difference) + " more. " + tier.promise() + ".");
    }

    /** The tier a waiting transaction is currently paying, in words. */
    private static String tier(PendingTxState tx) {
        try {
            return FeeTier.valueOf(tx.feeTier).label().toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return money(tx.feeMinorUnits);
        }
    }

    /** The next tier up from what this transaction is paying, or empty when it is already at the top. */
    public static java.util.Optional<FeeTier> nextTierUp(PendingTxState tx) {
        long paying = tx == null ? 0L : tx.feeMinorUnits;
        return java.util.Arrays.stream(FeeTier.values())
                .filter(tier -> Balance.feeFor(tier) > paying)
                .min(java.util.Comparator.comparingLong(Balance::feeFor));
    }

    private static String money(long minorUnits) {
        return String.format(java.util.Locale.ROOT, "%d.%02d EC",
                minorUnits / 100, Math.abs(minorUnits % 100));
    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() < 14 ? String.valueOf(hash)
                : hash.substring(0, 8) + "…" + hash.substring(hash.length() - 4);
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
            stamp(save, tx, height, now);
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
        return slotsAgainst(backlog(save));
    }

    /**
     * The same rule, against a stated queue depth — what the explorer's projections pack with.
     *
     * <h2>⚠ This exists because the projection and the confirmation had drifted apart</h2>
     *
     * {@code ChainExplorer.mempool} used to compute its own {@code slots - npc} with no floor, so on
     * any block where the derived backlog reached the limit it reported <b>zero</b> slots for the
     * player while {@link #confirmInto} — using {@code slotsFor} — went on giving them one. Rendered:
     * a 0.30 EC priority transaction whose card read "block +3, ~41:59" and which then confirmed in
     * the very next block. That is the explorer disagreeing with the engine about the player's own
     * money, which is exactly the failure {@code docs/design/04-mining.md} §3.1 trains players to
     * read as evidence of an intruder. One rule, called from both.
     */
    public static int slotsAgainst(int waiting) {
        int slots = Balance.BLOCK_TRANSACTION_LIMIT;
        int free = Math.max(0, slots - waiting);
        // At least one contested slot, always. A mempool that could shut a paying transaction out
        // entirely for an arbitrary number of blocks turns a purchase into a wait of unbounded
        // length, and FeeTier promises every tier gets in eventually.
        return Math.max(1, free);
    }

    /**
     * Marks a transaction mined, on both the pending record and the ledger row it belongs to.
     *
     * <p>⚠ This one assignment is what releases a bought upgrade. {@code Repac.locked} derives the
     * hold from exactly this field, so there is no unlock step to forget and no flag to go stale —
     * the package becomes installable the instant a miner packs the payment, whether or not anything
     * was on screen to watch it happen.
     */
    private static void stamp(SoloSave save, PendingTxState tx, long height, Instant now) {
        for (LedgerEntryState entry : save.ledger) {
            if (entry.entryId.equals(tx.entryId)) {
                entry.blockNumber = height;
                released(save, entry, height, now);
                return;
            }
        }
    }

    /**
     * Says so when a confirmation has unlocked something the player is waiting on.
     *
     * <p>A package that silently becomes installable is a package the player checks on by accident,
     * minutes or hours late — and this is a wait they were told to expect, which makes its end the
     * kind of event {@code EventLog} exists for. Nothing is logged for a confirmation that releases
     * nothing, because most of them do not.
     */
    private static void released(SoloSave save, LedgerEntryState entry, long height, Instant now) {
        for (StoredFileState file : List.copyOf(save.files)) {
            if (!entry.entryId.equals(file.lockedByEntryId)) {
                continue;
            }
            // ⚠ THE CONFIRMATION IS WHAT RUNS REPAC, and that is the whole lock.
            //
            // `.pkg` already means "a vendor's package" and `.upg` means "one this rig can install"
            // — Repac is the documented step between them. A bought package therefore lands as a
            // `.pkg` and stays one until the payment is mined, so the lock needs no new state, no
            // new glyph and no plumbing into the filesystem: it is visible in `ls`, in the file
            // manager and in the shell, in a vocabulary the game already teaches. You do not own it
            // until you have paid, and on a chain "paid" means "in a block".
            String was = file.name;
            Repac.repack(save, file, now).ifPresent(packaged -> EventLog.notice(save, "storage",
                    "payment confirmed in block " + height + " — repac: " + was + " -> "
                            + packaged.name + " (installable; double-click it, or sell it)", now));
        }
    }

    // ================================================================== what a block carries

    /**
     * How many transactions a block at this height carries.
     *
     * <h2>⚠ This lives in the rules, not in the explorer, since 2026-07-27</h2>
     *
     * It used to be {@code ChainExplorer.bodySize} — presentation, deriving a number for a card.
     * Then {@link #blockFeesMinorUnits} started <b>paying</b> that number's worth of fees to whoever
     * mined the block, and a figure the payout is computed from cannot live in a class whose own
     * charter is "everything here is DERIVED, nothing here decides anything". The explorer now asks
     * for it. Same value, one owner.
     *
     * <p>Never a full block and never empty: a chain whose every block was full would have no fee
     * market, because the clearing price could never fall.
     */
    public static int blockTransactionCount(SoloSave save, long height) {
        if (save.chain == null) {
            return 0;
        }
        long mixed = mix(save.chain.blockSeed ^ (height * 0x9E3779B97F4A7C15L) ^ 0x5BF0_3635L);
        return 12 + (int) Math.floorMod(mixed, Balance.BLOCK_TRANSACTION_LIMIT - 12L);
    }

    /**
     * What the {@code index}-th piece of network traffic in this block paid to be included.
     *
     * <p>⚠ Capped at the priority rate, never above it. A network population that routinely outbid
     * the most a player can pay would break {@code FeeTier}'s promise from the other side: the top
     * tier would buy nothing and the mechanic would read as broken rather than as competitive.
     */
    public static long npcFeeMinorUnits(SoloSave save, long height, int index) {
        if (save.chain == null) {
            return 0L;
        }
        long mixed = mix(save.chain.blockSeed
                ^ (height * 0x9E3779B97F4A7C15L)
                ^ ((index + 1L) * 0xD1B5_4A32_D192_ED03L));
        long span = Balance.FEE_PRIORITY_MINOR_UNITS - Balance.FEE_ECONOMY_MINOR_UNITS + 1;
        return Balance.FEE_ECONOMY_MINOR_UNITS + Math.floorMod(mixed, span);
    }

    /**
     * Everything the miner of this block collects in fees, on top of the subsidy.
     *
     * <h2>⚠ This is income, so read the note in {@code Balance.chainNetworkHashrate} first</h2>
     *
     * A real miner is paid {@code subsidy + fees}, and until 2026-07-27 this game paid only the
     * subsidy — the fees the mempool charged were debited from players and then vanished, which made
     * the fee market a pure sink and contradicted the block card that had been printing a fee total
     * all along. Paying it out is what makes that card mean something.
     *
     * <p>It averages about <b>1690 minor units</b> — roughly 10.6% of the 16 000 subsidy — because a
     * block carries ~105 transactions at a mean fee of ~16. That is a real change to mining income
     * and {@code Balance} absorbs it rather than letting it move the {@code design/03} anchor.
     *
     * <h2>⚠ The total is the derived one, even when the player's own rows are in the block</h2>
     *
     * A player's transaction <em>displaces</em> a piece of network traffic rather than adding to the
     * block, so the count — and therefore this total — does not move when they have something in it.
     * The alternative is a fee total that changes depending on who is looking, and the gain from the
     * displacement is bounded by one transaction's fee against a fee they had to pay to get in. It
     * is not a lever: sending yourself transactions to inflate a block you have a 4% chance of
     * winning costs strictly more than it can return.
     */
    public static long blockFeesMinorUnits(SoloSave save, long height) {
        int count = blockTransactionCount(save, height);
        long total = 0L;
        for (int i = 0; i < count; i++) {
            total += npcFeeMinorUnits(save, height, i);
        }
        return total;
    }

    /**
     * How many blocks ahead the mempool panel projects — 3 to 5, varying with the chain.
     *
     * <h2>Why it varies at all</h2>
     *
     * A fixed count said something the chain does not: that the queue is always legible exactly three
     * blocks out. How far ahead a mempool can honestly be read depends on how much is in it — a thin
     * queue is predictable further out because there is less traffic to displace what is waiting, and
     * a deep one stops being a projection and starts being a guess after a block or two. Varying the
     * depth is the panel admitting that its horizon is a property of the queue rather than of the
     * panel.
     *
     * <h2>⚠ Derived from chain state, never drawn, and NOT from the wall clock</h2>
     *
     * Three properties, and each one is a bug avoided:
     *
     * <ul>
     *   <li><b>Stable per height.</b> The panel repaints once a second ({@code Views.ledger}'s
     *       {@code refreshClock}). A drawn count would add and remove a card every second, which
     *       reads as the interface glitching rather than as the chain being uncertain.
     *   <li><b>Advances with the chain, not with time.</b> Same rule as {@link #backlog}: a queue
     *       that changed while the chain stood still would let a player watch for a good moment
     *       without paying a block for it.
     *   <li><b>Reproducible.</b> It is a function of {@code (blockSeed, height)}, so the same chain
     *       state always renders the same panel — which is what the whole {@code solo} module's
     *       "pure function of (save, clock)" discipline is for.
     * </ul>
     *
     * <p>The correlation with the backlog is deliberate rather than decorative: the mix is over the
     * same height, so a deep-queue block tends to project shallower and a thin one deeper. It is not
     * a hard rule — that would be a rule players would learn to read the backlog off — but it is the
     * right tendency.
     */
    public static int projectionDepth(SoloSave save) {
        if (save.chain == null) {
            return MIN_PROJECTIONS;
        }
        long mixed = mix(save.chain.blockSeed
                ^ (save.chain.height * 0x9E3779B97F4A7C15L)
                ^ 0x7F4A_7C15_1234_5678L);
        int span = MAX_PROJECTIONS - MIN_PROJECTIONS + 1;
        return MIN_PROJECTIONS + (int) Math.floorMod(mixed, (long) span);
    }

    /**
     * The shallowest the panel ever projects.
     *
     * <p>Three, because the fee tiers promise three distinguishable outcomes — priority lands in the
     * next block, standard soon after, economy when the queue thins — and a panel that could show two
     * would have nowhere to render the third.
     */
    public static final int MIN_PROJECTIONS = 3;

    /**
     * The deepest.
     *
     * <p>Five, which is about seventy minutes at the target interval. Past that the projection is
     * describing a queue that has been entirely replaced by traffic that has not arrived yet, and
     * {@code ChainMempool}'s type comment is explicit that a projection is a snapshot of a queue and
     * never a schedule — a sixth card would be the panel over-claiming.
     */
    public static final int MAX_PROJECTIONS = 5;

    /** splitmix64 finalizer. Same mixing the save's own Rng uses, so the two look alike. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
