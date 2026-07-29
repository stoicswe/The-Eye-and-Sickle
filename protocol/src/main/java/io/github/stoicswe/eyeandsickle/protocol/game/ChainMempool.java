package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;
import java.util.List;

/**
 * The mempool: everything waiting for a miner, and when each of it is expected to confirm.
 *
 * <h2>⚠ "Projected" is doing real work in that word</h2>
 *
 * A projected block is <b>not a promise</b>. It is what the next block would contain <em>if</em> it
 * were mined right now from the current pending set — which is exactly what a real explorer shows and
 * exactly as provisional. Blocks arrive on a Poisson schedule, more transactions arrive meanwhile, and
 * a miner is free to include whatever it likes. Presenting projections as a schedule would be the
 * same lie a progress bar on mining would be, one step removed.
 *
 * <h2>⚠ {@link ProjectedBlock#etaAt} is an estimate that is allowed to be overtaken (2026-07-27)</h2>
 *
 * This type used to publish <b>no</b> instant at all, on the grounds that blocks are memoryless and a
 * countdown claims that waiting brings you closer. That reasoning is still correct and it produced a
 * panel whose three cards read {@code ~14m / ~28m / ~42m} forever — a readout that never moved, which
 * players read as broken rather than as principled. The same complaint had already been filed once
 * against the block ages, for the same reason.
 *
 * <p>So an ETA is published, with the honesty moved out of the omission and into the behaviour:
 *
 * <ul>
 *   <li><b>It is anchored, never accumulated.</b> {@code etaAt} is
 *       {@code lastBlockAt + (index + 1) × expectedNextBlockSeconds} — the <em>mean</em> arrival of
 *       the (index+1)-th block from the last one. Nothing is stored, nothing accrues, and the anchor
 *       jumps forward whole when a block lands. There is no hidden progress counter behind it.
 *   <li><b>It can be overtaken, and that is the normal case, not an error.</b> An exponential wait
 *       exceeds its own mean {@code 1/e ≈ 37%} of the time. A client that reached zero and said
 *       "overdue" would teach exactly the gambler's fallacy {@code ChainState} warns about, so past
 *       {@code etaAt} the honest reading is {@link ProjectedBlock#waitPercentile} — "you have waited
 *       longer than 63% of waits" — which is a true statement about the distribution and is the thing
 *       a player should be learning to read.
 *   <li><b>It never reveals the draw.</b> The solo engine really does know when its next block lands
 *       ({@code ChainState.networkWorkTarget} is drawn up front), and this deliberately is not it.
 *       Publishing the draw would make being overdue observable and would delete the lesson outright.
 * </ul>
 *
 * <p>{@link #expectedNextBlockSeconds} therefore remains the chain's <b>mean interval</b> and is not
 * a deadline; {@link #secondsSinceLastBlock} remains a fact. See
 * {@code docs/design/04-mining.md} §1.3b and the 2026-07-27 entry in {@code docs/design/15}.
 *
 * @param queued everything of this rig's that is waiting, highest fee rate first, each carrying the
 *     block it currently projects into
 * @param yoursPending how many are waiting — the same count as {@code queued.size()}, kept as its own
 *     field because a remote session can report a depth it has no bodies for
 * @param projected what the next few blocks would hold if mined now, nearest first
 * @param expectedNextBlockSeconds the chain's mean block interval — an average, not a deadline
 * @param secondsSinceLastBlock how long it has actually been
 * @param lowFeeRate the cheapest fee rate currently getting into a projected block
 * @param highFeeRate what the top of the pending set is paying
 */
public record ChainMempool(
        List<Queued> queued,
        int yoursPending,
        List<ProjectedBlock> projected,
        double expectedNextBlockSeconds,
        long secondsSinceLastBlock,
        double lowFeeRate,
        double highFeeRate) {

    public ChainMempool {
        queued = queued == null ? List.of() : List.copyOf(queued);
        projected = projected == null ? List.of() : List.copyOf(projected);
    }

    /**
     * One of this rig's waiting transactions, and where the current queue puts it.
     *
     * <p>The projection is carried <em>beside</em> the transaction rather than on it, because it is a
     * fact about the queue at this instant and not about the transaction: the same transaction
     * projects into a different block the moment the backlog moves. A field on
     * {@link ChainTransaction} would have survived into the mined row and claimed a block had been
     * predicted.
     *
     * @param tx the transaction itself
     * @param projectedIndex which {@link ProjectedBlock} it currently lands in, or {@code -1} for
     *     "further out than the projections reach" — an under-priced transaction in a deep queue
     * @param etaAt the mean arrival of that block, or {@code null} when {@code projectedIndex} is -1
     */
    public record Queued(ChainTransaction tx, int projectedIndex, Instant etaAt) {

        public boolean beyondProjection() {
            return projectedIndex < 0 || etaAt == null;
        }
    }

    /**
     * One block the mempool would produce if it were mined now.
     *
     * @param index 0 is the next block, 1 the one after, and so on
     * @param transactions how many this block would carry
     * @param yours how many of those are this rig's. ⚠ They <b>displace</b> network traffic rather
     *     than adding to the block, so this is a share of {@link #transactions} and never an
     *     addition to it — otherwise a contested slot renders a 201-transaction block against a 200
     *     limit, and a fill bar over 100%.
     * @param gasUsed the gas they would consume
     * @param gasLimit the ceiling they are packed against
     * @param feesMinorUnits <b>the whole block's</b> estimated fee total — what a miner would
     *     collect for mining it.
     *     <p>⚠ Not this rig's fees, which is what it used to be and why the card read
     *     "fees 0.00 EC" on every projection a player had nothing waiting in — which is nearly all
     *     of them. A projection is a block, and the interesting figure about a block is what mining
     *     it is worth.
     *     <p>⚠ It is an <b>estimate</b> and must stay reconcilable with the block card that
     *     eventually replaces it: both are summed over the same simulated transactions at the same
     *     height, so the figure a player reads here is the figure they read again once it lands.
     *     Estimating it from the queue's depth instead would have made the two disagree by a factor
     *     of several — a projection promising 32 EC in fees followed two minutes later by a block
     *     card saying 7.60, which is the kind of contradiction {@code docs/design/04-mining.md} §3.1
     *     trains players to read as evidence of tampering.
     *     <p>The rig's own fees are deliberately absent: a player's transaction displaces network
     *     traffic rather than adding to it, so the block's total does not move when they have
     *     something in it. Their own fee is on their row under YOUR PENDING, where it is theirs.
     * @param lowFeeRate the cheapest fee rate that still made it into this block
     * @param etaAt when this block arrives <em>on average</em> — an estimate the chain is free to
     *     overtake, never a deadline. See the enclosing type.
     */
    public record ProjectedBlock(
            int index,
            int transactions,
            int yours,
            long gasUsed,
            long gasLimit,
            long feesMinorUnits,
            double lowFeeRate,
            Instant etaAt) {

        public double fullness() {
            return gasLimit <= 0 ? 0.0d : Math.min(1.0d, gasUsed / (double) gasLimit);
        }

        /** Roughly how long until this one, on average. Not a deadline — see the enclosing type. */
        public double expectedSeconds(double blockSeconds) {
            return blockSeconds * (index + 1);
        }

        /**
         * The share of waits that finish sooner than {@code elapsedSeconds} — what a client shows
         * once {@link #etaAt} has come and gone.
         *
         * <p>Waiting for the (index+1)-th block is a sum of that many independent exponentials, which
         * is the <b>Erlang</b> distribution, so this is its CDF and not an approximation. At exactly
         * the mean it reads 63% for the next block: being past the estimate is the ordinary case, and
         * a readout that said "overdue" instead would be teaching the gambler's fallacy.
         *
         * @param elapsedSeconds how long it has been since the last block
         * @param blockSeconds the chain's mean interval
         */
        public double waitPercentile(double elapsedSeconds, double blockSeconds) {
            if (blockSeconds <= 0) {
                return 0.0d;
            }
            return erlangCdf(index + 1, elapsedSeconds / blockSeconds);
        }
    }

    /**
     * {@code P(S_n ≤ t)} for a sum of {@code n} unit-rate exponentials, with {@code lambdaT = t/mean}.
     *
     * <p>Summed forward from {@code e^-λt} rather than evaluated term by term: each term is the last
     * one times {@code λt/i}, so no factorial is ever formed and the series cannot overflow for a
     * shape a projection strip would ever ask about.
     */
    public static double erlangCdf(int shape, double lambdaT) {
        if (shape < 1 || !(lambdaT > 0)) {
            return 0.0d;
        }
        double term = Math.exp(-lambdaT);
        double sum = term;
        for (int i = 1; i < shape; i++) {
            term *= lambdaT / i;
            sum += term;
        }
        return Math.max(0.0d, Math.min(1.0d, 1.0d - sum));
    }

    /** The waiting transactions themselves, in the order the queue holds them. */
    public List<ChainTransaction> pending() {
        return queued.stream().map(Queued::tx).toList();
    }

    public boolean empty() {
        return queued.isEmpty();
    }
}
