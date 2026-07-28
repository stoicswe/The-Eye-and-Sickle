package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import io.github.stoicswe.eyeandsickle.solo.state.ChainState;
import io.github.stoicswe.eyeandsickle.solo.state.LedgerEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import io.github.stoicswe.eyeandsickle.solo.state.PendingTxState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * The chain as a block explorer renders it: addresses, hashes, blocks and transactions.
 *
 * <h2>⚠ Everything here is DERIVED. Nothing here decides anything.</h2>
 *
 * This class turns state that already exists — a height, a winner, a ledger row — into the shapes an
 * explorer draws. It rolls no dice, awards no coins and changes no balances. That separation is the
 * whole reason it is not part of {@code ChainRules}: a presentation layer that could quietly mint a
 * transaction would be a second, invisible economy.
 *
 * <h2>Why a hash can be computed instead of stored</h2>
 *
 * A real block's hash is the thing the miner searched for, so it has to be recorded. Here there is no
 * search — the winner is drawn — so a block's hash carries no information and only has to be
 * <em>stable</em>: the same height must render the same hash every time it is asked for. A digest
 * over {@code (blockSeed, height)} gives exactly that for no storage at all, which is what lets the
 * save keep two dozen blocks and still answer for any height a ledger row names.
 *
 * <p>The seed is per-character. Without it every save would render identical hashes at identical
 * heights, and the chain would read as a shared fixture rather than each character's own world.
 *
 * <h2>Ethereum's shapes, this chain's meanings</h2>
 *
 * Addresses are 20 bytes and hashes are 32, {@code 0x}-prefixed and lower-case hex, because that is
 * what a reader recognises. The header fields are pre-Merge Ethereum's, which was a proof-of-work
 * chain and therefore had all of them honestly. Gas is real arithmetic and not decoration: every
 * transaction on this chain is a plain value transfer, which is the 21 000 gas Ethereum charges for
 * one, so a block's {@code gasUsed} is its transaction count times 21 000 and nothing else.
 */
public final class ChainExplorer {

    private ChainExplorer() {}

    /** What Ethereum charges for a plain value transfer, and the only kind this chain has. */
    public static final long GAS_PER_TRANSFER = 21_000L;

    /**
     * A block's gas ceiling: 200 transfers' worth.
     *
     * <p><b>Not</b> Ethereum's 30 000 000, deliberately. A gas limit is a per-chain figure that miners
     * vote on, so borrowing Ethereum's would say this chain is Ethereum — and it would make every
     * fill bar on the explorer read 2%, which is a bar that tells the player nothing. At 200 transfers
     * a block the cards span roughly 6% to 96% full and the strip becomes readable at a glance.
     */
    public static final long BLOCK_GAS_LIMIT = 200 * GAS_PER_TRANSFER;

    // ================================================================== addresses

    /**
     * This character's address — {@code 0x} + 40 lower-case hex, derived from their id.
     *
     * <p>Stable for the life of the character and derived rather than stored, so it cannot drift out
     * of step with the identity it belongs to.
     */
    public static String addressOf(SoloSave save) {
        return address("character:" + save.characterId);
    }

    /** A pool's address. Derived from its id, so it is the same on every save — pools are public. */
    public static String addressOf(MiningPool pool) {
        return address("pool:" + pool.id());
    }

    /** An address for anything with a stable name. */
    public static String address(String seed) {
        return "0x" + hex(digest(seed), 20);
    }

    // ================================================================== blocks

    /** The explorer's strip: the newest {@code ChainState.RECENT_BLOCKS} headers, newest first. */
    public static List<ChainBlock> recentBlocks(SoloSave save) {
        ChainState chain = save.chain;
        if (chain == null) {
            return List.of();
        }
        List<ChainBlock> out = new ArrayList<>();
        long from = Math.max(0, chain.height - ChainState.RECENT_BLOCKS + 1);
        for (long height = chain.height; height >= from; height--) {
            out.add(header(save, height));
        }
        return out;
    }

    /**
     * One block's header, derived entirely from its height.
     *
     * <h2>⚠ Nothing about a block is stored, including who mined it</h2>
     *
     * A real block's hash is the thing the miner searched for, so it has to be recorded. Here the
     * winner is <em>drawn</em>, so a block's hash carries no information and only has to be
     * <b>stable</b> — the same height must render identically every time. One digest over
     * {@code (blockSeed, height)} gives that for no storage, which is what lets the chain open at
     * height 124 with all 124 blocks fully inspectable, and keep growing while the save does not.
     *
     * <p>Even the miner is derived, from the same weighted table {@code ChainRules.drawWinner} uses,
     * so the historical distribution matches the live one. The one exception is the player's own
     * wins, which were genuinely rolled and are indexed in {@code ChainState.blocksWon} — a derived
     * winner is overridden there. A pool "losing" a block the player won is correct: somebody had to.
     */
    public static ChainBlock header(SoloSave save, long height) {
        ChainState chain = save.chain;
        byte[] seed = digest(chain.blockSeed + ":" + height);
        boolean yours = chain.blocksWon != null && chain.blocksWon.contains(height);

        int transactions = bodySize(save, height);
        long gasUsed = transactions * GAS_PER_TRANSFER;
        // A rough serialised size. Not load-bearing — it is a number explorers show and players
        // compare, and it has to move with the transaction count or it would obviously be invented.
        int sizeBytes = 620 + transactions * 226 + Math.floorMod(seed[1] & 0xFF, 200);

        String label = yours ? "YOUR RIG" : winnerLabel(derivedWinner(chain, height));
        long fees = 0L;
        for (ChainTransaction tx : body(save, height)) {
            fees += tx.feeMinorUnits();
        }
        return new ChainBlock(
                height,
                blockHash(chain, height),
                blockHash(chain, height - 1),
                timestampOf(save, height),
                label,
                yours ? addressOf(save) : winnerAddress(save, height),
                yours,
                chain.difficulty,
                "0x" + hex(seed, 8),
                transactions,
                gasUsed,
                BLOCK_GAS_LIMIT,
                sizeBytes,
                Balance.BLOCK_SUBSIDY_MINOR_UNITS,
                fees,
                label,
                List.of());
    }

    /** A block header with every transaction in it, for the detail view. */
    public static ChainBlock blockWithBody(SoloSave save, long height) {
        return header(save, height).withBody(body(save, height));
    }

    /**
     * When a block was found.
     *
     * <p>Back-dated from the current height at the target interval for anything the player was not
     * present for. Real block times jitter and these do not, which is the one place this derivation
     * is visibly a derivation — but a history with plausible-looking random gaps would be inventing
     * a past the chain never had, and an even cadence at least does not claim to be a measurement.
     */
    public static Instant timestampOf(SoloSave save, long height) {
        ChainState chain = save.chain;
        long behind = Math.max(0, chain.height - height);
        Instant last = chain.lastBlockAt == null || chain.lastBlockAt.equals(Instant.EPOCH)
                ? Instant.now()
                : chain.lastBlockAt;
        return last.minusSeconds(behind * Balance.CHAIN_TARGET_BLOCK_SECONDS);
    }

    /** How many transactions a block carries, stable per height. */
    public static int bodySize(SoloSave save, long height) {
        byte[] seed = digest(save.chain.blockSeed + ":" + height);
        // Up to the block limit, and often well short of it — a chain whose every block was full
        // would have no fee market, because the clearing price would never fall.
        return 12 + Math.floorMod(seed[0] & 0xFF, Balance.BLOCK_TRANSACTION_LIMIT - 12);
    }

    /**
     * Every transaction in a block: the network's, plus any of the player's that were mined into it.
     *
     * <h2>Sorted by fee rate, highest first — because that is how it got packed</h2>
     *
     * A block is what a miner chose, and a miner chooses on fee. Rendering it in fee order is not
     * cosmetic: it is how a player sees that their priority transaction went in near the top and their
     * economy one scraped the bottom, which is the entire lesson the fee tiers exist to teach.
     */
    public static List<ChainTransaction> body(SoloSave save, long height) {
        List<ChainTransaction> out = new ArrayList<>();
        String mine = addressOf(save);

        // The player's own, from the ledger. Authoritative — these really happened.
        for (int i = 0; i < save.ledger.size(); i++) {
            LedgerEntryState entry = save.ledger.get(i);
            if (entry.blockNumber != height) {
                continue;
            }
            out.add(transaction(save, entry, i, mine));
        }

        // The network's, derived. Count excludes the player's so the header's figure stays honest.
        int npc = Math.max(0, bodySize(save, height) - out.size());
        for (int i = 0; i < npc; i++) {
            byte[] seed = digest(save.chain.blockSeed + ":" + height + ":" + i);
            long value = 25L + Math.floorMod(readLong(seed, 0), 250_000L);
            // ⚠ Capped at the priority rate, not twice it. An NPC population that routinely outbid
            // the most a player can pay would break FeeTier's promise from the other side: the top
            // tier would buy nothing and the mechanic would read as broken rather than as competitive.
            long fee = Balance.FEE_ECONOMY_MINOR_UNITS
                    + Math.floorMod(readLong(seed, 8),
                            Balance.FEE_PRIORITY_MINOR_UNITS - Balance.FEE_ECONOMY_MINOR_UNITS + 1);
            out.add(new ChainTransaction(
                    "0x" + hex(seed, 32),
                    height,
                    timestampOf(save, height),
                    "0x" + hex(digest("npc:" + height + ":" + i + ":from"), 20),
                    "0x" + hex(digest("npc:" + height + ":" + i + ":to"), 20),
                    value,
                    false,
                    0L,
                    Math.floorMod(readLong(seed, 16), 4096L),
                    GAS_PER_TRANSFER,
                    "TRANSFER",
                    "",
                    fee,
                    gasPrice(fee),
                    false));
        }
        out.sort(Comparator.comparingDouble(ChainTransaction::gasPriceMinorUnits).reversed());
        return out;
    }

    /** A block's hash, as a stable function of its height. See {@link #header}. */
    public static String blockHash(ChainState chain, long height) {
        if (height < 0) {
            // The parent of block zero. A real chain's genesis names all zeroes for the same reason:
            // there is nothing before it, and a plausible-looking hash would claim there was.
            return "0x" + "0".repeat(64);
        }
        return "0x" + hex(digest(chain.blockSeed + ":" + height), 32);
    }

    private static String winnerLabel(String winner) {
        if ("unpooled".equals(winner)) {
            return "unpooled";
        }
        return Pools.exists(winner) ? Pools.byId(winner).name() : "unpooled";
    }

    private static String winnerAddress(SoloSave save, long height) {
        String winner = derivedWinner(save.chain, height);
        return Pools.exists(winner) ? addressOf(Pools.byId(winner)) : address("unpooled:" + height);
    }

    /** The winner a block would have had, from the same weighted table the live draw uses. */
    private static String derivedWinner(ChainState chain, long height) {
        byte[] seed = digest(chain.blockSeed + ":" + height + ":winner");
        double roll = (readLong(seed, 0) >>> 11) / (double) (1L << 53);
        double at = 0;
        for (MiningPool pool : Pools.all()) {
            at += pool.networkShare();
            if (roll < at) {
                return pool.id();
            }
        }
        return "unpooled";
    }

    // ================================================================== transactions

    /**
     * The player's ledger, rendered as chain transactions, newest first.
     *
     * <h2>⚠ Two renderings of one list, never two lists</h2>
     *
     * Every transaction here is a ledger row wearing chain clothes — same amount, same moment, same
     * running balance. {@code docs/design/04-mining.md} §3.1 makes "add these up and compare against
     * the balance" a thing a player does to catch an intruder, so the explorer and the ledger table
     * must be incapable of disagreeing. They are, because there is only one list.
     */
    public static List<ChainTransaction> transactions(SoloSave save, int limit) {
        List<ChainTransaction> out = new ArrayList<>();
        String mine = addressOf(save);
        int from = Math.max(0, save.ledger.size() - Math.max(1, limit));
        for (int i = save.ledger.size() - 1; i >= from; i--) {
            out.add(transaction(save, save.ledger.get(i), i, mine));
        }
        return out;
    }

    /** One ledger row as a chain transaction. The single builder both surfaces go through. */
    private static ChainTransaction transaction(
            SoloSave save, LedgerEntryState entry, int nonce, String mine) {
        boolean incoming = entry.deltaMinorUnits >= 0;
        // A block reward has no sender: the coins did not exist before the block. Explorers render
        // that as a transfer from the zero address, and a coinbase costs no gas because there was no
        // transaction to execute.
        boolean coinbase = incoming && isMinted(entry.type);
        String counterparty = entry.counterparty == null || entry.counterparty.isBlank()
                ? address("counterparty:" + entry.type)
                : entry.counterparty;
        long fee = feeOf(save, entry);
        return new ChainTransaction(
                txHash(save, entry),
                entry.blockNumber,
                entry.at,
                coinbase ? ChainTransaction.ZERO_ADDRESS : incoming ? counterparty : mine,
                incoming ? mine : counterparty,
                Math.abs(entry.deltaMinorUnits),
                incoming,
                entry.balanceAfterMinorUnits,
                nonce,
                coinbase ? 0L : GAS_PER_TRANSFER,
                entry.type,
                entry.description,
                coinbase ? 0L : fee,
                coinbase ? 0.0d : gasPrice(fee),
                true);
    }

    /** What this entry paid to be included, from the mempool record if it is still waiting. */
    private static long feeOf(SoloSave save, LedgerEntryState entry) {
        if (save.chain != null) {
            for (PendingTxState pending : save.chain.mempool) {
                if (pending.entryId.equals(entry.entryId)) {
                    return pending.feeMinorUnits;
                }
            }
        }
        return Balance.FEE_STANDARD_MINOR_UNITS;
    }

    /** Whether this kind of entry mints coins rather than moving them. */
    private static boolean isMinted(String type) {
        return "SELF_MINING".equals(type) || "MINING_COLLECT".equals(type) || "CRACK".equals(type);
    }

    /** A transaction's hash, derived from the entry id so it is stable across reloads. */
    public static String txHash(SoloSave save, LedgerEntryState entry) {
        if (entry.txHash != null && !entry.txHash.isBlank()) {
            return entry.txHash;
        }
        return "0x" + hex(digest("tx:" + save.characterId + ":" + entry.entryId), 32);
    }

    // ================================================================== mempool

    /**
     * The mempool as the panel draws it: what is waiting, and what the next blocks would hold.
     *
     * <h2>⚠ Projections are provisional and the type says so</h2>
     *
     * These are what the next blocks would contain <em>if mined right now from the current queue</em>.
     * Blocks arrive on a Poisson schedule and more transactions arrive meanwhile, so a projection is
     * a snapshot of a queue and never a schedule. {@code ChainMempool} carries that warning at the
     * type level because it is the one thing a player could reasonably misread as a promise.
     */
    public static ChainMempool mempool(SoloSave save, Instant now) {
        ChainState chain = save.chain;
        if (chain == null) {
            return new ChainMempool(List.of(), 0, List.of(), 0, 0, 0, 0);
        }
        String mine = addressOf(save);
        List<ChainTransaction> pending = new ArrayList<>();
        for (PendingTxState tx : chain.mempool) {
            pending.add(new ChainTransaction(
                    tx.txHash,
                    -1L,
                    tx.createdAt,
                    tx.outgoing ? mine : tx.counterparty,
                    tx.outgoing ? tx.counterparty : mine,
                    tx.valueMinorUnits,
                    !tx.outgoing,
                    0L,
                    chain.nonce,
                    GAS_PER_TRANSFER,
                    tx.kind,
                    tx.description,
                    tx.feeMinorUnits,
                    gasPrice(tx.feeMinorUnits),
                    true));
        }
        pending.sort(Comparator.comparingDouble(ChainTransaction::gasPriceMinorUnits).reversed());

        // The network's queue is a depth, not a list — see MempoolRules for why it is derived. The
        // projections pack the player's transactions against it rather than instead of it.
        int backlog = MempoolRules.backlog(save);
        double clearing = MempoolRules.clearingFee(save);
        List<ChainMempool.ProjectedBlock> projected = new ArrayList<>();
        int placed = 0;
        int remaining = backlog;
        for (int index = 0; index < 3; index++) {
            int slots = Balance.BLOCK_TRANSACTION_LIMIT;
            int npc = Math.min(slots, remaining);
            remaining -= npc;
            int free = slots - npc;
            int ours = 0;
            while (ours < free && placed < pending.size()) {
                if (pending.get(placed).feeMinorUnits() < Math.floor(clearing) && index == 0) {
                    // Outbid for the next block. It shows up in a later projection instead, which is
                    // exactly what an under-priced transaction does rather than vanishing.
                    break;
                }
                placed++;
                ours++;
            }
            long fees = 0;
            for (int i = placed - ours; i < placed; i++) {
                fees += pending.get(i).feeMinorUnits();
            }
            projected.add(new ChainMempool.ProjectedBlock(
                    index, npc + ours, ours, (long) (npc + ours) * GAS_PER_TRANSFER,
                    BLOCK_GAS_LIMIT, fees, clearing));
        }

        // ⚠ BOTH as gas prices. clearingFee() is in minor units and gasPriceMinorUnits() is per
        // million gas, and the panel prints them side by side — shipping them in different units made
        // "cheapest slot 8, top of the queue 1429" look like a 180x spread when it is under 4x.
        double clearingRate = gasPrice(clearing);
        double top = pending.isEmpty() ? clearingRate : pending.getFirst().gasPriceMinorUnits();
        long since = chain.lastBlockAt == null
                ? 0L
                : Math.max(0L, java.time.Duration.between(chain.lastBlockAt, now).toSeconds());
        return new ChainMempool(
                pending,
                pending.size(),
                projected,
                Balance.CHAIN_TARGET_BLOCK_SECONDS,
                since,
                clearingRate,
                Math.max(top, clearingRate));
    }

    // ================================================================== hex

    private static byte[] digest(String seed) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is required of every Java platform. If it is genuinely absent, the save layer
            // and the provenance verifier are already broken and a mining readout is not the problem.
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /**
     * A fee as a <b>gas price</b>: minor units per million gas.
     *
     * <p>The one unit everything comparable is expressed in. A fee in minor units and a gas price are
     * different scales by a factor of 21 — mixing them in one readout made the mempool's cheapest slot
     * and the top of its queue look 180× apart when they were under 4×. Real explorers quote a rate
     * (sat/vB, gwei) for exactly this reason: a total tells you nothing about priority.
     */
    public static double gasPrice(double feeMinorUnits) {
        return feeMinorUnits / (double) GAS_PER_TRANSFER * 1_000_000;
    }

    /** Eight bytes of a digest as a non-negative long, for a derived value. */
    private static long readLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[(offset + i) % bytes.length] & 0xFFL);
        }
        return value & Long.MAX_VALUE;
    }

    private static String hex(byte[] bytes, int count) {
        return HexFormat.of().formatHex(bytes, 0, Math.min(count, bytes.length));
    }
}
