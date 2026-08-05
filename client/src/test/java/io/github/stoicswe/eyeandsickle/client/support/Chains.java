package io.github.stoicswe.eyeandsickle.client.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;

/**
 * Chain control for tests whose subject is not the chain.
 *
 * <h2>Why this exists: two tests were flaky and for two different reasons</h2>
 *
 * A purchase settles on-chain, so any test of buying, downloading, unpacking or installing has to say
 * something about whether a block lands while it runs. Saying it by hand went wrong twice, and both
 * failures were rare enough to survive review and re-runs — the worst frequency there is, because a
 * red build that goes green on retry teaches its reader to retry.
 *
 * <h2>⚠ The two hazards, named, because neither is obvious from the assertion that fails</h2>
 *
 * <b>1. Holding the chain off is not the same as holding it off from the start.</b>
 * {@code DownloadQueueFlowTest} set the guard <em>after</em> its {@code settle()} helper, which winds
 * the clock until the download lands — up to a few dozen seconds of chain time, unguarded. A block
 * lands every ~14 minutes, so it usually did not; when it did, the payment confirmed, every member of
 * the bundle was correctly released, and the assertion that they are still locked failed against code
 * that was working perfectly. <b>Install the guard before the first tick, not before the first
 * assertion.</b>
 *
 * <p><b>2. A block landing is not the same as a payment confirming.</b> Getting mined is a race
 * against the derived backlog, not just against time. Measured against the real constants: the
 * backlog swings uniformly over roughly 120–480 against a 200-slot block, and a {@code STANDARD}
 * 0.06 fee clears only while the backlog is at or under ~257 — so <b>each block confirms a standard
 * transaction with probability ≈0.38</b>. Waiting a fixed three hours is ~13 blocks, i.e. it fails
 * about one run in five hundred. The seed differs every run (a {@code @TempDir} path gives a fresh
 * character id, which seeds {@code chain.blockSeed}), so it is a genuine dice roll per execution and
 * not something a fixed fixture would have caught.
 *
 * <p>The fix for the second is not a bigger constant — it is to wait for <em>the event</em> rather
 * than for a duration that usually contains it.
 */
public final class Chains {

    /**
     * How long {@link #holdOff} stops the chain for, as a multiple of the expected block interval.
     *
     * <p>500 expected blocks is several days at the shipped ~14-minute interval, so no test that
     * winds minutes or hours can reach one. It is an outstanding {@code Exp(1)} work draw, not a
     * mock: the chain runs normally and simply has not found this block yet.
     */
    private static final double NEVER = 500.0d;

    /** An outstanding draw small enough that the next block lands almost immediately. */
    private static final double IMMINENT = 0.001d;

    /**
     * How many hours {@link #settlePayment} will wind before giving up.
     *
     * <p>At ≈0.38 per block and ~4.3 blocks an hour, 48 hours is ~200 chances; the odds of every one
     * of them missing are about 10^-42. Bounded rather than unbounded so a genuine regression fails
     * the suite instead of hanging it.
     */
    private static final int SETTLE_HOURS = 48;

    private Chains() {}

    /**
     * Stops any block landing for the rest of the test.
     *
     * <p>⚠ Call this <b>before the first tick</b>, not before the first assertion — see hazard 1 in
     * the class comment. Anything that winds the clock, including a helper that only means to let a
     * download finish, is long enough for this to matter.
     *
     * @param game the engine whose chain should stall
     */
    public static void holdOff(GameEngine game) {
        game.state().chain.networkWorkTarget = NEVER;
    }

    /**
     * Lets the chain run again, with the next block due at once.
     *
     * <p>⚠ This releases the <b>block</b>, which is not the same as confirming a transaction — the
     * player's payment still has to win a slot against the backlog. Follow it with
     * {@link #settlePayment} rather than with a fixed wind.
     *
     * @param game the engine whose chain should resume
     */
    public static void release(GameEngine game) {
        game.state().chain.networkWorkTarget = IMMINENT;
    }

    /**
     * Runs the chain until every pending transaction has been mined.
     *
     * <p>Waits for the event rather than for a duration that usually contains it — see hazard 2. The
     * mempool is the right thing to watch because it is exactly what {@code confirmInto} drains, and
     * an under-priced transaction stays in it and re-bids next block rather than being dropped.
     *
     * @param game the engine to advance
     * @param windOneHour advances the test's clock by one hour; the caller owns the clock
     */
    public static void settlePayment(GameEngine game, Runnable windOneHour) {
        release(game);
        for (int hour = 0; hour < SETTLE_HOURS && !game.state().chain.mempool.isEmpty(); hour++) {
            windOneHour.run();
            game.tick();
        }
        assertThat(game.state().chain.mempool)
                .as(
                        "the payment should have been mined within %d hours of chain time. If this "
                                + "fails, the fee market has moved: a STANDARD fee now loses to the "
                                + "derived backlog far more often than the ~62%% a block that it did "
                                + "when this bound was set (MempoolRules.clearingFeeAt).",
                        SETTLE_HOURS)
                .isEmpty();
    }
}
