package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How a rig's self-mining cycles are pointed at the chain.
 *
 * <h2>One rate, two variances</h2>
 *
 * Both modes are self-mining and both are covered by Invariant <b>I4</b> — silent, unseizable, zero
 * heat. They differ in exactly one thing: <b>how lumpy the income is</b>. That is not two systems; it
 * is one Poisson process read at two difficulties, which is what a real mining pool actually is.
 *
 * <p>{@link #POOLED} is the default and the thing {@code docs/design/03-economy.md} §1 prices as the
 * floor. {@link #SOLO} is opt-in, pays slightly more in expectation because there is no pool fee to
 * hand over, and can pay nothing at all for a very long time.
 */
public enum MiningMode {

    /**
     * Mining against the pool's share difficulty, paid per share.
     *
     * <p>Pay-per-share: the pool pays a fixed amount for every accepted share whether or not the pool
     * found a block, and charges a fee for carrying that risk. Income is near-constant.
     */
    POOLED,

    /**
     * Mining against the full network difficulty, paid the whole block subsidy or nothing.
     *
     * <p>The same equation with a difficulty several hundred times higher: the same expected income,
     * arriving hundreds of times less often in lumps hundreds of times larger.
     */
    SOLO
}
