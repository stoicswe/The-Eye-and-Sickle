package io.github.stoicswe.eyeandsickle.solo.state;

import java.math.BigInteger;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import java.time.Instant;
import java.util.UUID;

/**
 * One of the player's transactions, waiting for a miner.
 *
 * <h2>⚠ The balance has already moved. This is the receipt, not the money.</h2>
 *
 * When a player spends, the ledger is debited and the item is theirs immediately — the same instant a
 * real wallet shows the send and deducts it from your spendable balance. What waits here is the
 * <em>chain record</em>: the transaction a miner has not yet packed into a block.
 *
 * <p>That split is deliberate, and it is the one place this simulation declines to be faithful. A
 * purchase that withheld the goods for fourteen minutes would be accurate and would also make buying
 * a consumable mid-breach impossible, which is a worse game for a lesson the player has already had.
 * What the fee buys here is how soon the transaction is <em>confirmed</em> — visible, checkable, and
 * costing nothing but position in a queue.
 */
public final class PendingTxState {

    public String txId = UUID.randomUUID().toString();

    /** {@code 0x} + 64 hex, fixed at creation so it does not change when the transaction confirms. */
    public String txHash = "";

    /** The ledger row this belongs to, so the two can never disagree about the amount. */
    public String entryId = "";

    public Instant createdAt = Instant.EPOCH;

    /** Positive: the amount moved, direction is {@link #outgoing}. */
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code valueMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("valueMinorUnits")
    public BigInteger valueWei = BigInteger.ZERO;

    public boolean outgoing = true;

    /** The other end, as an address. */
    public String counterparty = "";

    /** {@code ECONOMY}, {@code STANDARD} or {@code PRIORITY}. */
    public String feeTier = "STANDARD";

    /** What the sender paid to be included, in minor units. */
    /**
     * ⚠ {@code @JsonAlias} carries the PRE-WEI key, and a save is lost without it.
     *
     * <p>The field was {@code feeMinorUnits} when an ethecoin was 100 minor units. Jackson has
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} off — deliberately, so a save from a newer build still opens
     * — which means a key it does not recognise is <b>silently dropped</b>. Renaming the field without
     * this alias therefore does not fail: it loads the save, leaves every amount at its initialiser,
     * and hands the player a balance of zero with nothing anywhere saying why.
     *
     * <p>Measured, not theorised. A real pre-migration save loaded as {@code 0 EC} across the board,
     * and the only reason it was noticed at all is that one field had no initialiser and threw.
     */
    @com.fasterxml.jackson.annotation.JsonAlias("feeMinorUnits")
    public BigInteger feeWei = Balance.FEE_STANDARD_WEI;

    /** The engine's own type and words, carried so the explorer and the ledger read alike. */
    public String kind = "";

    public String description = "";
}
