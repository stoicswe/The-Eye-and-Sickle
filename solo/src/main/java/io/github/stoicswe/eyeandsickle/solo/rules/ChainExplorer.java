package io.github.stoicswe.eyeandsickle.solo.rules;

import java.math.BigInteger;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainBlock;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainMempool;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainTransaction;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Pools;
import io.github.stoicswe.eyeandsickle.solo.state.ChainState;
import io.github.stoicswe.eyeandsickle.solo.state.ContributionState;
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
        // ⚠ The rules' total, not a sum of the rendered body. This is the figure the winner is
        // actually PAID (MempoolRules.blockFeesWei), and summing the body instead would make
        // the card disagree with the ledger row the moment the player has a transaction in the
        // block — their row displaces network traffic rather than adding to it.
        BigInteger fees = MempoolRules.blockFeesWei(save, height);
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
                Balance.BLOCK_SUBSIDY_WEI,
                fees,
                label,
                List.of());
    }

    /** A block header with every transaction in it, for the detail view. */
    public static ChainBlock blockWithBody(SoloSave save, long height) {
        return header(save, height).withBody(body(save, height));
    }

    // ================================================================== contributions

    /**
     * Every block this character put hashrate into, newest first — the CONTRIBUTOR tab.
     *
     * <h2>⚠ Four of these fields are DERIVED and must never be stored</h2>
     *
     * The transaction count, the fee total and the subsidy are stable functions of the height, and
     * they are read here from exactly the places the block card reads them — {@code MempoolRules} and
     * {@code Balance} — so a contributor row and the card for the block it names cannot disagree.
     * Storing them in the save would be caching game state, and a cache of game state eventually
     * disagrees with it on the one surface {@code docs/design/04-mining.md} §3.1 trains a player to
     * treat a disagreement as evidence.
     *
     * <p>What the save does hold is what was <b>rolled</b> and what the world looked like at the
     * time: the height, the mode, the rig's allocation, the network's hashrate, the difficulty, and
     * what was actually credited. See {@code ContributionState}.
     *
     * <h2>Blocks that paid nothing are here on purpose</h2>
     *
     * A pay-per-share pool does not divide up the blocks it finds — it pays a fixed price per accepted
     * share out of its own balance — so a PPS row carries a real hashrate and a zero credit. That is
     * not a gap in the record; it is the difference between the two pool schemes, which
     * {@code MiningRules.rewardBaseWei} spends three paragraphs on and no screen had ever
     * shown.
     */
    public static List<BlockContribution> contributions(SoloSave save, int limit) {
        ChainState chain = save.chain;
        if (chain == null || chain.contributions.isEmpty()) {
            return List.of();
        }
        List<BlockContribution> out = new ArrayList<>();
        int from = Math.max(0, chain.contributions.size() - Math.max(1, limit));
        for (int i = chain.contributions.size() - 1; i >= from; i--) {
            out.add(contribution(save, chain.contributions.get(i)));
        }
        return out;
    }

    /** One stored row, with everything derivable derived. */
    private static BlockContribution contribution(SoloSave save, ContributionState row) {
        MiningMode mode;
        try {
            mode = MiningMode.valueOf(row.mode);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            // Pooled, matching MiningRules.modeOf — a hand-edited save must not make a pooled row
            // render as a solo win, which is the reading that would over-state what the rig did.
            mode = MiningMode.POOLED;
        }
        boolean solo = row.won;
        String poolName = solo || row.poolId == null || row.poolId.isBlank()
                ? ""
                : Pools.byId(row.poolId).name();
        return new BlockContribution(
                row.height,
                row.at,
                mode,
                row.scheme == null || row.scheme.isBlank() ? "PPS" : row.scheme,
                solo ? "" : row.poolId == null ? "" : row.poolId,
                poolName,
                row.won,
                row.offline,
                row.hashrate,
                row.networkHashrate,
                row.difficulty,
                MempoolRules.blockTransactionCount(save, row.height),
                Balance.BLOCK_SUBSIDY_WEI,
                MempoolRules.blockFeesWei(save, row.height),
                row.creditedWei);
    }

    /**
     * When a block was found — back-dated from the tip, and <b>jittered</b> (2026-07-27).
     *
     * <h2>⚠ This used to be an exactly even cadence, and the strip showed it</h2>
     *
     * Every card was 14.0 minutes after the one below it, twenty-four times, forever. Measured, and
     * reported as the chain looking like a metronome. The old comment defended it — "a history with
     * plausible-looking random gaps would be inventing a past the chain never had" — which is a fair
     * argument and lost to the fact that a perfectly even chain is *also* an invented past, and an
     * obviously false one. Real proof-of-work never produces two identical intervals in a row.
     *
     * <h2>The jitter is applied to the position, not to the interval, and that is the whole trick</h2>
     *
     * Each height is displaced by up to {@link #TIMESTAMP_JITTER_SECONDS} either way, derived from
     * its own height. Adjacent gaps therefore come out at
     * {@code mean + jitter(h) − jitter(h − 1)} — inside {@code 14 ± 6} minutes, so <b>8 to 20</b>
     * with the peak at 14. Three properties fall out of doing it this way rather than by summing
     * per-block intervals:
     *
     * <ul>
     *   <li><b>O(1).</b> A summed history would need every interval between the tip and the height
     *       asked for. {@link #body} calls this once per transaction and a full strip is ~4800 calls,
     *       so a walk of even a few dozen digests each would be tens of thousands of hashes a frame.
     *   <li><b>Monotone by construction.</b> The smallest possible gap is
     *       {@code mean − 2 × jitter}, which stays comfortably positive — no block can ever render
     *       before its own parent, which a naive per-height random offset would eventually do.
     *   <li><b>The mean is exactly the published interval.</b> The jitters telescope over any span,
     *       so the strip cannot drift away from the "a block every ~14 min" printed above it.
     * </ul>
     *
     * <p>⚠ The tip's own jitter is subtracted, so the newest block renders at exactly
     * {@code lastBlockAt}. That one timestamp is a <b>measurement</b> — the chain really did record
     * it — and displacing it would put the explorer minutes out of step with the mempool panel's
     * "last one 3m ago", which is derived from the same field.
     *
     * <p>⚠ The band is deliberately <b>narrower than the live process</b>, which is exponential and
     * measured at 0 → 95 minutes with a median of 10.3. This is a back-dated derivation of a history
     * nobody watched, and its job is to look like a chain rather than to be a second sampler of the
     * distribution; the honest readout of the real distribution is the mempool panel's estimate and
     * its percentile, which are computed from the live process. If the two are ever asked to agree,
     * this is the one that is wrong.
     */
    public static Instant timestampOf(SoloSave save, long height) {
        ChainState chain = save.chain;
        long behind = Math.max(0, chain.height - height);
        Instant last = chain.lastBlockAt == null || chain.lastBlockAt.equals(Instant.EPOCH)
                ? Instant.now()
                : chain.lastBlockAt;
        return last.minusSeconds(behind * Balance.CHAIN_TARGET_BLOCK_SECONDS)
                .plusSeconds(jitterOf(chain, height) - jitterOf(chain, chain.height));
    }

    /**
     * How far a block's rendered timestamp may sit from its even position, in seconds.
     *
     * <p>Three minutes, which puts adjacent gaps in 8–20 minutes against the 14-minute target. Any
     * value at or above half the block interval would let a block render before its parent.
     */
    public static final long TIMESTAMP_JITTER_SECONDS = 180L;

    /** This height's displacement, stable and derived — nothing about a block is stored. */
    private static long jitterOf(ChainState chain, long height) {
        // A suffix of its own, so the displacement does not correlate with the transaction count,
        // which is drawn from the unsuffixed digest. Two readouts moving together would look like a
        // rule ("busy blocks come faster") that does not exist.
        byte[] seed = digest(chain.blockSeed + ":" + height + ":t");
        long span = 2 * TIMESTAMP_JITTER_SECONDS + 1;
        return Math.floorMod(readLong(seed, 0), span) - TIMESTAMP_JITTER_SECONDS;
    }

    /**
     * How many transactions a block carries, stable per height.
     *
     * <p>⚠ Delegates. This number decides what {@code MempoolRules.blockFeesWei} pays a
     * miner, so it stopped being a presentation detail on 2026-07-27 and moved to the rules — see
     * this class's own charter, which is that nothing here decides anything.
     */
    public static int bodySize(SoloSave save, long height) {
        return MempoolRules.blockTransactionCount(save, height);
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
            // ⚠ Drawn in hundredths and scaled up, not drawn across the wei range. A uniform draw
            // over 18 decimals gives every NPC transfer an eighteen-digit tail, and a block full of
            // those reads as machine output rather than as people sending each other money.
            BigInteger value = Ethecoin.ofDecimal("0.01").wei()
                    .multiply(BigInteger.valueOf(25L + Math.floorMod(readLong(seed, 0), 250_000L)));
            // ⚠ The rules' fee, not a second derivation of one. These are summed and PAID to whoever
            // mined the block, so a copy here would be a block whose card and whose payout disagreed
            // — the exact class of bug the mempool projection had until this morning.
            BigInteger fee = MempoolRules.npcFeeWei(save, height, i);
            out.add(new ChainTransaction(
                    "0x" + hex(seed, 32),
                    height,
                    timestampOf(save, height),
                    "0x" + hex(digest("npc:" + height + ":" + i + ":from"), 20),
                    "0x" + hex(digest("npc:" + height + ":" + i + ":to"), 20),
                    value,
                    false,
                    BigInteger.ZERO,
                    Math.floorMod(readLong(seed, 16), 4096L),
                    GAS_PER_TRANSFER,
                    "TRANSFER",
                    "",
                    fee,
                    gasPrice(fee),
                    false,
                    ""));
        }
        out.sort(Comparator.comparingDouble(ChainTransaction::gasPriceWei).reversed());
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
        boolean incoming = entry.deltaWei.signum() >= 0;
        // A block reward has no sender: the coins did not exist before the block. Explorers render
        // that as a transfer from the zero address, and a coinbase costs no gas because there was no
        // transaction to execute.
        // ⚠ A pool payout is NOT a coinbase, and telling them apart is what the counterparty is
        // for. Both arrive as SELF_MINING, so keying off the type alone marked every pooled payout
        // as minted: the row rendered "from: coinbase", the pool address SoloGame had carefully
        // stamped on it was discarded, and the table contradicted LedgerEntryState's own rule that
        // the pool paid it out of its own balance. A minted entry has no sender; a pool payout has
        // one, and that is the test.
        boolean fromNamedParty = entry.counterparty != null && !entry.counterparty.isBlank();
        boolean coinbase = incoming && isMinted(entry.type) && !fromNamedParty;
        String counterparty = fromNamedParty
                ? entry.counterparty
                : address("counterparty:" + entry.type);
        BigInteger fee = feeOf(save, entry);
        return new ChainTransaction(
                txHash(save, entry),
                entry.blockNumber,
                entry.at,
                coinbase ? ChainTransaction.ZERO_ADDRESS : incoming ? counterparty : mine,
                incoming ? mine : counterparty,
                entry.deltaWei.abs(),
                incoming,
                entry.balanceAfterWei,
                nonce,
                coinbase ? 0L : GAS_PER_TRANSFER,
                entry.type,
                entry.description,
                coinbase ? BigInteger.ZERO : fee,
                coinbase ? 0.0d : gasPrice(fee),
                true,
                labelFor(counterparty));
    }

    /** What this entry paid to be included, from the mempool record if it is still waiting. */
    private static BigInteger feeOf(SoloSave save, LedgerEntryState entry) {
        // ⚠ The ROW's own record first. The mempool loop below only ever answered for a transaction
        // still waiting — confirmInto deletes the pending record — so a confirmed priority
        // transaction reported the STANDARD fee, and a block's rows are sorted by fee rate, so the
        // player's own row also sorted into the wrong part of the block they had paid to be at the
        // top of. Caught by rendering a block that contained one.
        if (entry.feeWei.signum() >= 0) {
            return entry.feeWei;
        }
        if (save.chain != null) {
            for (PendingTxState pending : save.chain.mempool) {
                if (pending.entryId.equals(entry.entryId)) {
                    return pending.feeWei;
                }
            }
        }
        // Only reached by an entry written before the fee was recorded on the row.
        return Balance.FEE_STANDARD_WEI;
    }

    /**
     * A readable name for an address, when the client can actually verify one.
     *
     * <p>Only pools qualify, and only because their addresses are <b>derived from public ids</b> —
     * every save renders the same address for THE COMMONS, so matching one is a fact rather than a
     * claim. Anything else gets no label: a name shown where an address belongs is how a transfer
     * from a stranger gets mistaken for a payout, and the explorer still prints the address either
     * way so the player can check the two against each other ({@code 04} §3.1).
     */
    private static String labelFor(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        for (MiningPool pool : Pools.all()) {
            if (addressOf(pool).equals(address)) {
                return pool.name();
            }
        }
        return "";
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
     *
     * <h2>⚠ The ETA is anchored on {@code lastBlockAt} and must never be anchored on {@code now}</h2>
     *
     * Every projection's {@code etaAt} is {@code lastBlockAt + (index + 1) × the mean interval}. That
     * is what lets a client tick it down second by second: the instant is fixed until a block lands
     * and moves it, so a panel polling once a second sees it approach. Anchoring on {@code now}
     * instead would recompute the same fourteen minutes on every poll and the countdown would sit
     * perfectly still — which is the bug this replaced, arrived at from the other direction.
     *
     * <p>⚠ It is <b>not</b> {@code chain.networkWorkTarget − chain.networkWorkDone}, which is the real
     * answer and is sitting right there. Publishing the draw would make "overdue" an observable fact
     * and delete the memorylessness lesson {@code ChainState} exists to teach. See
     * {@code ChainMempool}'s type comment for the reasoning that survived the change.
     */
    public static ChainMempool mempool(SoloSave save, Instant now) {
        ChainState chain = save.chain;
        if (chain == null) {
            return new ChainMempool(List.of(), 0, List.of(), 0, 0,
                    BigInteger.ZERO, BigInteger.ZERO);
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
                    tx.valueWei,
                    !tx.outgoing,
                    BigInteger.ZERO,
                    chain.nonce,
                    GAS_PER_TRANSFER,
                    tx.kind,
                    tx.description,
                    tx.feeWei,
                    gasPrice(tx.feeWei),
                    true,
                    labelFor(tx.counterparty)));
        }
        pending.sort(Comparator.comparingDouble(ChainTransaction::gasPriceWei).reversed());

        // The network's queue is a depth, not a list — see MempoolRules for why it is derived. The
        // projections pack the player's transactions against it rather than instead of it.
        int backlog = MempoolRules.backlog(save);
        BigInteger clearing = MempoolRules.clearingFee(save);

        // The anchor. Falls back to now only on a chain that has never recorded a block, which is a
        // state genesis() does not produce — without the fallback a fresh save would date every ETA
        // from the epoch and every card would read "running long" on the first frame.
        Instant anchor = chain.lastBlockAt == null || chain.lastBlockAt.equals(Instant.EPOCH)
                ? now
                : chain.lastBlockAt;

        List<ChainMempool.ProjectedBlock> projected = new ArrayList<>();
        // Which projection each waiting transaction lands in, by its position in the fee-sorted
        // queue. -1 is "further out than the projections reach" and is a real outcome for a floor-fee
        // transaction behind a deep backlog — the panel says so rather than inventing a fourth block.
        int[] landsIn = new int[pending.size()];
        java.util.Arrays.fill(landsIn, -1);
        int placed = 0;
        // ⚠ 3 to 5, derived from chain state rather than drawn — the panel repaints once a second
        // and a drawn count would add and remove a card every repaint. MempoolRules owns the rule
        // because how far a queue can honestly be read ahead is a fact about the queue.
        int depth = MempoolRules.projectionDepth(save);
        for (int index = 0; index < depth; index++) {
            int slots = Balance.BLOCK_TRANSACTION_LIMIT;
            // ⚠ EACH block's own queue, not a single snapshot drained across the strip. Draining
            // meant the third card onward rendered "0 txs" — one dead card at a fixed three, up to
            // three of them at 3–5 — which claims the chain is about to go quiet. It does not: a
            // real mempool has inflow roughly equal to throughput, which is the entire reason there
            // is a fee market to bid into. See MempoolRules.backlogAt.
            long height = chain.height + 1L + index;
            int npc = Math.min(slots, MempoolRules.backlogAt(save, height));
            BigInteger clearingHere = MempoolRules.clearingFeeAt(save, height);
            // ⚠ MempoolRules' rule, not a second copy of it. The old local `slots - npc` had no
            // floor, so a block the backlog filled reported zero slots for the player here while
            // confirmInto went on giving them one — a 0.30 EC priority transaction whose card
            // promised block +3 and then confirmed in the very next block.
            int free = MempoolRules.slotsAgainst(npc);
            int ours = 0;
            while (ours < free && placed < pending.size()) {
                // ⚠ Checked against THIS block's clearing price, at every index rather than only the
                // first. It used to be `&& index == 0`, which was right while every card quoted one
                // shared price and became wrong the moment they each quoted their own: a transaction
                // outbid for the next block would then be placed unconditionally in the one after,
                // however expensive that block's queue happened to be.
                if (pending.get(placed).feeWei().compareTo(clearingHere) < 0) {
                    // Outbid for this block. It shows up in a later projection instead, which is
                    // exactly what an under-priced transaction does rather than vanishing.
                    break;
                }
                landsIn[placed] = index;
                placed++;
                ours++;
            }
            // ⚠ How many the block CARRIES, from the same rule the mined card will use — not how
            // many are queued for it.
            //
            // Those are two different quantities and the panel needs both: the backlog above is
            // queue pressure and decides how many slots the player wins, while this is what the
            // block ends up holding. Rendering the backlog as the count meant a card reading
            // "200 txs" that landed two minutes later as a 47-transaction block, and — once the fee
            // total below became real — "200 txs · fees 7.60 EC", which a player can falsify with
            // arithmetic. Asking MempoolRules for both keeps the projection and the block it becomes
            // reconcilable, which docs/design/04-mining.md §3.1 makes the difference between a
            // readout and a false-positive generator.
            int carried = MempoolRules.blockTransactionCount(save, height);
            // ⚠ The player's transactions DISPLACE network ones, they do not extend the block.
            // Adding them on top would render a 201-transaction block against a 200 limit, and a
            // fill bar over 100% — which is what happens if the contested slot is granted without
            // taking it off somebody. Same rule as body(); max() only matters in the degenerate case
            // where a near-empty queue hands the player more slots than the block carries.
            int total = Math.min(slots, Math.max(carried, ours));
            // ⚠ THE WHOLE BLOCK'S fees, estimated over exactly the transactions counted above — not
            // this rig's, which is what it was and why the card read "fees 0.00 EC" on every
            // projection the player had nothing waiting in. It is the same call the mined block card
            // makes at the same height, so the estimate and the realised figure are the same number
            // arrived at once. The rig's own fee is deliberately not added: it displaces network
            // traffic rather than adding to it, so the block's total does not move for it — see
            // MempoolRules.blockFeesWei.
            BigInteger fees = MempoolRules.blockFeesWei(save, height);
            // ⚠ THIS block's clearing price, not the strip's. Every card quoted the current one
            // before the queues were split apart, so five cards printed the same figure five times
            // and a player comparing them learned nothing from looking past the first.
            projected.add(new ChainMempool.ProjectedBlock(
                    index, total, ours, (long) total * GAS_PER_TRANSFER,
                    BLOCK_GAS_LIMIT, fees, gasPrice(clearingHere), etaOf(anchor, index)));
        }

        List<ChainMempool.Queued> queued = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            int index = landsIn[i];
            queued.add(new ChainMempool.Queued(
                    pending.get(i), index, index < 0 ? null : etaOf(anchor, index)));
        }

        // ⚠ BOTH as fee AMOUNTS, in the unit the fee tiers are quoted in. They used to be gas
        // prices — fee per million gas — which was readable at two decimal places and became an
        // eighteen-digit integer the moment the scale moved to wei. The comparison the pairing exists
        // for survives: shipping them in different units is what made "cheapest slot 8, top of the
        // queue 1429" read as a 180x spread when it was under 4x, and two amounts are comparable.
        BigInteger clearingRate = clearing;
        BigInteger top = pending.isEmpty() ? clearingRate : pending.getFirst().feeWei();
        long since = chain.lastBlockAt == null
                ? 0L
                : Math.max(0L, java.time.Duration.between(chain.lastBlockAt, now).toSeconds());
        return new ChainMempool(
                queued,
                queued.size(),
                projected,
                Balance.CHAIN_TARGET_BLOCK_SECONDS,
                since,
                clearingRate,
                top.max(clearingRate));
    }

    /** The mean arrival of the {@code (index + 1)}-th block after {@code anchor}. */
    private static Instant etaOf(Instant anchor, int index) {
        return anchor.plusSeconds(Balance.CHAIN_TARGET_BLOCK_SECONDS * (index + 1L));
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
    public static double gasPrice(double feeWei) {
        return feeWei / (double) GAS_PER_TRANSFER * 1_000_000;
    }

    /**
     * The same, from a wei amount.
     *
     * <p>⚠ A gas price is a RATE — fee per million gas — so a double is the right output and the
     * division is what makes it safe: the wei scale cancels. Converting the fee to a double first and
     * then dividing would round the amount before using it, which at 18 decimals loses digits the
     * sort actually depends on.
     */
    public static double gasPrice(BigInteger feeWei) {
        return new java.math.BigDecimal(feeWei)
                .divide(java.math.BigDecimal.valueOf(GAS_PER_TRANSFER), java.math.MathContext.DECIMAL64)
                .multiply(java.math.BigDecimal.valueOf(1_000_000))
                .doubleValue();
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
