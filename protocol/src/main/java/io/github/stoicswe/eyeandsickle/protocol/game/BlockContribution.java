package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One block this character put hashrate into — the CONTRIBUTOR tab's row.
 *
 * <h2>What "contributed to" means, and why it is wider than "won"</h2>
 *
 * A solo miner contributes to exactly the blocks they win. A pooled miner contributes to every block
 * their pool wins, whether or not any of it comes back to them — and under {@link PoolScheme#PPS}
 * none of it does, because a share pool pays a fixed price per accepted share out of its own balance
 * and is not dividing up a block at all. All three are listed, because the question the tab answers
 * is "where did my hashrate go", and a tab that showed only the blocks that paid would make PPS look
 * like a mode that mines nothing.
 *
 * <p>That is also the tab's teaching. {@link #creditedWei} sitting at zero on a PPS row beside
 * a real {@link #hashrate} is the difference between the two pool schemes rendered as a table, which
 * is a thing {@code MiningRules.rewardBaseWei} spends three paragraphs on and no screen had
 * ever shown.
 *
 * <h2>⚠ The subsidy and the fees are separate fields and must stay separate</h2>
 *
 * They are one credit in the ledger and two different things on the chain: the subsidy is
 * <b>minted</b> — those coins did not exist before this block — and the fees were <b>paid by the
 * senders</b> of the transactions in it. {@code proof-of-work(7)} teaches exactly that split, and a
 * single "reward" total hides it. {@link #rewardWei()} adds them for anyone who wants the sum.
 *
 * <h2>Nothing here is stored except what was rolled</h2>
 *
 * The save keeps the height, the mode, the rig's hashrate at the time and what was credited. The
 * transaction count, the fee total and the subsidy are <b>derived from the height</b> the same way
 * {@code ChainExplorer} derives them for the block cards, so a contributor row and the block card it
 * names cannot disagree — see {@code ChainState.blocksWon} for why that matters.
 *
 * @param height the block
 * @param at when it was found
 * @param mode how the rig was mining at the time
 * @param scheme the payout scheme in force: {@code SOLO}, or the pool's
 * @param poolId the pool, or empty when solo
 * @param poolName its display name, or empty when solo
 * @param won whether this rig itself mined the block, as against its pool doing so
 * @param offline whether it landed during the post-logout spin-down window ({@code 04} §1.2)
 * @param hashrate what this rig was contributing, in hashes per second
 * @param networkHashrate the whole chain's, at the time
 * @param difficulty the difficulty the block was mined against
 * @param transactions how many transactions it carried
 * @param subsidyWei the coinbase — newly minted coins
 * @param feesWei what the senders in the block paid, collected by whoever mined it
 * @param creditedWei what actually reached this character. The whole reward when solo, a cut
 *     of it under PPLNS, and zero under PPS, which pays per share instead.
 */
public record BlockContribution(
        long height,
        Instant at,
        MiningMode mode,
        String scheme,
        String poolId,
        String poolName,
        boolean won,
        boolean offline,
        long hashrate,
        long networkHashrate,
        double difficulty,
        int transactions,
        BigInteger subsidyWei,
        BigInteger feesWei,
        BigInteger creditedWei) {

    /** Everything the block was worth to whoever mined it: minted coins plus collected fees. */
    public BigInteger rewardWei() {
        return subsidyWei.add(feesWei);
    }

    /**
     * This rig's share of the chain when the block was found, as a 0–1 fraction.
     *
     * <p>The number the whole tab is built to make checkable: on a solo row it is the probability
     * this block was going to be yours, and over enough rows the share of them that were should
     * converge on it. {@code ChainRules.drawWinner} really does draw against exactly this.
     */
    public double networkShare() {
        return networkHashrate <= 0 ? 0.0d : Math.min(1.0d, hashrate / (double) networkHashrate);
    }

    /** What this row paid as a fraction of what the block was worth. 1 when solo, 0 under PPS. */
    public double takeFraction() {
        BigInteger reward = rewardWei();
        // ⚠ A ratio, so a double is the right output type — but the DIVISION is done in BigDecimal.
        // At 18 decimals a wei count routinely exceeds 2^53, past which a double cannot hold an
        // integer exactly, and `credited / (double) reward` would quietly lose the low digits of both
        // operands before dividing them.
        return reward.signum() <= 0
                ? 0.0d
                : new java.math.BigDecimal(creditedWei)
                        .divide(new java.math.BigDecimal(reward), java.math.MathContext.DECIMAL64)
                        .doubleValue();
    }

    /** Whether this row paid anything at all — false for a PPS block, which pays per share. */
    public boolean paid() {
        return creditedWei.signum() > 0;
    }

    /** Who mined it, for a column that has to name one: this rig, or the pool it was in. */
    public String minerLabel() {
        if (won) {
            return "YOUR RIG";
        }
        return poolName == null || poolName.isBlank() ? "POOL" : poolName;
    }
}
