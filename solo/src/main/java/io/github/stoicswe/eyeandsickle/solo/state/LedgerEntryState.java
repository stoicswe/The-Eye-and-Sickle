package io.github.stoicswe.eyeandsickle.solo.state;

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
    public long deltaMinorUnits = 0L;

    /** Balance after this entry, so the log reconciles without replaying it. */
    public long balanceAfterMinorUnits = 0L;

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
}
