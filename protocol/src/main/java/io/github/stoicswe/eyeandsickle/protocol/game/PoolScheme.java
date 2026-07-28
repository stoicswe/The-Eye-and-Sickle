package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How a pool decides what it owes you — the axis that decides whether pooling actually smooths.
 *
 * <h2>Both pay the same in expectation, and that is the point</h2>
 *
 * Your expected income is your hashrate's share of the chain times the subsidy, less the fee, under
 * <em>every</em> scheme. What a scheme buys is the shape: how often you are paid, and therefore how
 * far a bad week can drift from the average. A player comparing pools on headline rate alone will
 * conclude they are all identical, which is very nearly true and entirely misses the choice.
 */
public enum PoolScheme {

    /**
     * Pay-per-share: a fixed amount for every accepted share, whether or not the pool found a block.
     *
     * <p>The operator carries the variance and charges a fee for it. Payouts arrive at whatever pace
     * the pool's share target is tuned to — seconds to a minute — so income is very nearly a
     * straight line, <b>however small the pool is</b>. That last part is what people get wrong: a
     * tiny PPS pool smooths your income exactly as well as a huge one, because the smoothing comes
     * from the share target and not from the pool's size.
     */
    PPS,

    /**
     * Pay-per-last-N-shares: paid out of blocks the pool actually finds, in proportion to your work.
     *
     * <p>The operator carries nothing, so the fee is lower — and the pool's luck becomes your luck.
     * Payouts arrive only when the <em>pool</em> finds a block, so a pool with 5% of the chain pays
     * you roughly every three hours and a pool with 40% pays roughly every twenty-five minutes.
     * <b>Under PPLNS, pool size is the variance knob.</b>
     */
    PPLNS
}
