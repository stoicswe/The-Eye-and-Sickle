package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;

/**
 * The mempool: everything waiting for a miner, and what the next few blocks will probably hold.
 *
 * <h2>⚠ "Projected" is doing real work in that word</h2>
 *
 * A projected block is <b>not a promise</b>. It is what the next block would contain <em>if</em> it
 * were mined right now from the current pending set — which is exactly what a real explorer shows and
 * exactly as provisional. Blocks arrive on a Poisson schedule, more transactions arrive meanwhile, and
 * a miner is free to include whatever it likes. Presenting projections as a schedule would be the
 * same lie a progress bar on mining would be, one step removed.
 *
 * <p>For the same reason {@link #expectedNextBlockSeconds} is an <b>average</b> and never a countdown.
 * The chain has no idea when the next block is coming; nobody does. {@link #secondsSinceLastBlock} is
 * a fact and is shown beside it, and a player comparing the two is reading the distribution rather
 * than a timer.
 *
 * @param pending everything waiting, highest fee rate first
 * @param yoursPending how many of those are this rig's
 * @param projected what the next few blocks would hold if mined now, nearest first
 * @param expectedNextBlockSeconds the chain's mean block interval — an average, not a deadline
 * @param secondsSinceLastBlock how long it has actually been
 * @param lowFeeRate the cheapest fee rate currently getting into a projected block
 * @param highFeeRate what the top of the pending set is paying
 */
public record ChainMempool(
        List<ChainTransaction> pending,
        int yoursPending,
        List<ProjectedBlock> projected,
        double expectedNextBlockSeconds,
        long secondsSinceLastBlock,
        double lowFeeRate,
        double highFeeRate) {

    public ChainMempool {
        pending = pending == null ? List.of() : List.copyOf(pending);
        projected = projected == null ? List.of() : List.copyOf(projected);
    }

    /**
     * One block the mempool would produce if it were mined now.
     *
     * @param index 0 is the next block, 1 the one after, and so on
     * @param transactions how many would fit
     * @param yours how many of those are this rig's
     * @param gasUsed the gas they would consume
     * @param gasLimit the ceiling they are packed against
     * @param feesMinorUnits what a miner would collect in fees
     * @param lowFeeRate the cheapest fee rate that still made it into this block
     */
    public record ProjectedBlock(
            int index,
            int transactions,
            int yours,
            long gasUsed,
            long gasLimit,
            long feesMinorUnits,
            double lowFeeRate) {

        public double fullness() {
            return gasLimit <= 0 ? 0.0d : Math.min(1.0d, gasUsed / (double) gasLimit);
        }

        /** Roughly how long until this one, on average. Not a deadline — see the enclosing type. */
        public double expectedSeconds(double blockSeconds) {
            return blockSeconds * (index + 1);
        }
    }

    public boolean empty() {
        return pending.isEmpty();
    }
}
