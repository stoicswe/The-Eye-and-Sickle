package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * What the chain did while the client was closed — the report the {@code SYNCHRONIZING} screen reads.
 *
 * <h2>Why a load produces a report at all</h2>
 *
 * Until 2026-07-29 the chain froze at the last tick, so a character played on Monday and again on
 * Friday found four days of wall-clock time and zero blocks — on the one readout in the game whose
 * whole subject is that nobody owns it and nobody can stop it. The blocks are filled in now
 * ({@code docs/design/04-mining.md} §1.3d), and a height that silently jumped from 4 412 to 4 463
 * would replace one wrong impression with another: the player would have no way to tell a chain that
 * ran without them from a save that had been tampered with. So the fill is <b>shown</b>, and this is
 * what it shows.
 *
 * <h2>⚠ {@link #minedSeconds} is the invariant, stated as a number</h2>
 *
 * Invariant <b>I5</b> as amended: the rig keeps hashing for a bounded window after the client closes
 * and then stops dead. This field is how much of {@link #awaySeconds} fell inside that window, and
 * {@link #competedBlocks} is how many blocks landed in it. Everything past the cap happened with the
 * rig off — its hashrate was zero and it was drawn against nothing — which is the whole of what stops
 * a sync screen becoming a reward for closing the game.
 *
 * <p>A report where {@code competedBlocks} equalled {@code blocks} over a long absence would be that
 * invariant broken, which is why the two are carried separately rather than as one count and a flag.
 *
 * @param from when the client was last running
 * @param to the instant the chain was synchronised to — the load
 * @param awaySeconds the whole absence
 * @param minedSeconds how much of it the rig was still hashing for; never more than the cap
 * @param fromHeight the height the chain was left at
 * @param toHeight the height it was caught up to
 * @param blocks how many blocks were filled in
 * @param competedBlocks how many of them landed while the rig was still running
 * @param blocksWon solo blocks this rig won inside that window
 * @param poolBlocks blocks the rig's pool won inside it, that the rig was paid a cut of
 * @param creditedWei everything the fill actually paid — subsidy and fees together
 * @param retargets difficulty adjustments that closed during the absence
 * @param difficultyBefore difficulty at {@link #from}
 * @param difficultyAfter difficulty at {@link #to}
 * @param transactionsConfirmed the player's own pending transactions that were mined while away
 * @param truncated whether the fill hit its block limit and stopped short — see
 *     {@code Balance.CHAIN_SYNC_BLOCK_LIMIT}. Reported rather than swallowed, because a chain that
 *     quietly gave up would look identical to one that had caught up.
 */
public record ChainSync(
        Instant from,
        Instant to,
        long awaySeconds,
        long minedSeconds,
        long fromHeight,
        long toHeight,
        int blocks,
        int competedBlocks,
        int blocksWon,
        int poolBlocks,
        BigInteger creditedWei,
        int retargets,
        double difficultyBefore,
        double difficultyAfter,
        int transactionsConfirmed,
        boolean truncated) {

    /** A sync that found nothing to do — a fresh character, or a reload seconds after a save. */
    public static ChainSync none(Instant at) {
        return new ChainSync(at, at, 0L, 0L, 0L, 0L, 0, 0, 0, 0, BigInteger.ZERO, 0, 0.0d, 0.0d, 0, false);
    }

    /**
     * The same report with what the fill actually paid.
     *
     * <p>⚠ The chain half and the money half are settled by different rules and the split is
     * deliberate: {@code ChainRules} decides who won a block and {@code MiningRules} decides what a
     * block is worth. A payout figure computed in both places is two places for it to be computed
     * differently, so the chain leaves this field at zero and the caller fills it from the one credit
     * it actually wrote to the ledger.
     */
    public ChainSync withCredit(BigInteger creditedWei) {
        return new ChainSync(
                from, to, awaySeconds, minedSeconds, fromHeight, toHeight, blocks, competedBlocks,
                blocksWon, poolBlocks, creditedWei, retargets, difficultyBefore,
                difficultyAfter, transactionsConfirmed, truncated);
    }

    /** Whether there is anything here worth showing a screen for. */
    public boolean any() {
        return blocks > 0;
    }

    /** Whether the rig stopped before the player came back — i.e. the spin-down cap actually bit. */
    public boolean capped() {
        return awaySeconds > minedSeconds;
    }

    /** Blocks that landed after the rig had spun down. Nobody was competing for these. */
    public int uncontestedBlocks() {
        return Math.max(0, blocks - competedBlocks);
    }

    /** Every payout the fill produced, solo and pooled. */
    public int payouts() {
        return blocksWon + poolBlocks;
    }

    /**
     * How far through the fill a replay is at {@code step}, as a 0–1 fraction.
     *
     * <p>Here rather than in the view because the {@code SYNCHRONIZING} screen replays the fill over a
     * fixed duration regardless of how many blocks it covers — 51 blocks and 5100 take the same few
     * seconds — and a view that divided by {@link #blocks} itself would divide by zero on the empty
     * sync that {@link #none} produces.
     */
    public double progress(int step, int steps) {
        if (steps <= 0) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, step / (double) steps));
    }

    /** The height the replay is showing at {@code step} of {@code steps}. */
    public long heightAt(int step, int steps) {
        return fromHeight + Math.round(progress(step, steps) * blocks);
    }
}
