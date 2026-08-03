package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * One movement of ethecoin.
 *
 * <p>The solo ledger is append-only in the same sense the real one is: the engine only ever adds
 * rows, never rewrites them. That is not a security property here — it is a teaching one. {@code
 * docs/education/07-distributed-systems-and-identity.md} §3.22 uses the ledger as the player's first
 * concrete append-only log, and a solo ledger that quietly rewrote history would contradict the page
 * that explains why it does not.
 */
public final class LedgerEntryState {

    public String entryId = UUID.randomUUID().toString();
    public Instant at = Instant.now();

    /** Signed: positive is income, negative is a sink. */
    public BigInteger deltaWei = BigInteger.ZERO;

    /** Balance after this entry, so the log reconciles without replaying it. */
    public BigInteger balanceAfterWei = BigInteger.ZERO;

    public String type = "";
    public String description = "";

    /**
     * The block that carries this, or -1 for anything that never touched the chain.
     *
     * <p>⚠ A pool payout is <b>not</b> a block reward and does not get a block number: the pool paid
     * it out of its own balance, and pretending otherwise would put transactions on the chain that no
     * miner ever mined. Only a solo block win names a block.
     */
    public long blockNumber = -1L;

    /** {@code 0x} + 64 hex, stable across reloads. Derived on first render if absent. */
    public String txHash = "";

    /** The other end of the transfer, as an address. Empty means "derive one from the type". */
    public String counterparty = "";

    /**
     * The sentinel for "no fee was ever recorded on this row".
     *
     * <p>⚠ A NEGATIVE amount, which no real fee can be, so it cannot collide with a genuine value.
     * It was {@code -1L} when this field was a {@code long}; it has to be a named constant now
     * because {@code BigInteger.valueOf(-1)} scattered through comparisons is unreadable and
     * {@code equals} on it is easy to get wrong.
     */
    public static final BigInteger MISSING = BigInteger.valueOf(-1L);

    /**
     * What this transaction paid to be included, in wei. {@link #MISSING} when it never had a fee.
     *
     * <h2>⚠ Stored, because the mempool record is DESTROYED on confirmation</h2>
     *
     * The fee lived only on {@code PendingTxState}, which {@code MempoolRules.confirmInto} removes
     * the moment a miner takes the transaction — so the explorer's lookup missed and fell back to the
     * standard rate. A priority transaction therefore reported the standard fee <em>from the instant
     * it confirmed</em>, and since a block's rows are sorted by fee rate, the player's own row also
     * sorted into the wrong group: they paid for the top of the block and were rendered in the
     * middle of it. Found by rendering a block that contained one.
     *
     * <p>{@link #MISSING} rather than zero on an entry that predates this field, so "no fee
     * recorded" and "a fee of zero" stay different answers — the explorer falls back only for the
     * first.
     */
    public BigInteger feeWei = MISSING;
}
