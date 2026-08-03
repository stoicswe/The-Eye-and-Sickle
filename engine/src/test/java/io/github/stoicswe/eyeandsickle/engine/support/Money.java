package io.github.stoicswe.eyeandsickle.engine.support;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Amounts, for tests that reason about the economy rather than about exact wei.
 *
 * <h2>Why a test helper rather than converting every assertion by hand</h2>
 *
 * The economy's guard tests are written against the figures {@code docs/design/03-economy.md}
 * publishes — "about 68 EC", "40 EC/hr", "one cautious session" — and those are statements about
 * <b>ethecoin</b>, not about wei. Rewriting them into eighteen-digit integers would make each one
 * unreadable and would sever the link between the assertion and the document it is checking, which is
 * the only reason those tests are worth having.
 *
 * <p>So: statistical and band assertions convert to EC and compare as doubles, and exact-equality
 * assertions stay in {@link BigInteger}. The split is deliberate — see {@link #ec(BigInteger)}.
 */
public final class Money {

    private Money() {}

    /**
     * A wei amount as a count of ethecoin, for a band or a mean.
     *
     * <h2>⚠ Only for assertions that were always approximate</h2>
     *
     * A double cannot hold a wei amount exactly past about 0.009 EC, so this <b>loses precision by
     * construction</b>. That is fine for "the mean payout at depth 3 is higher than at depth 2" and
     * catastrophic for "the balance is exactly this". Anything asserting an exact amount must compare
     * {@code BigInteger} to {@code BigInteger} — {@link #ec(String)} builds the expected side.
     */
    public static double ec(BigInteger wei) {
        return new BigDecimal(wei)
                .divide(new BigDecimal(Ethecoin.WEI_PER_ETHECOIN))
                .doubleValue();
    }

    /** An exact amount written the way the design docs write one: {@code ec("68")}. */
    public static BigInteger ec(String amount) {
        return Ethecoin.ofDecimal(amount).wei();
    }

    /** An exact whole number of ethecoin. */
    public static BigInteger ec(long ethecoin) {
        return Ethecoin.ofWholeEthecoin(ethecoin).wei();
    }
}
