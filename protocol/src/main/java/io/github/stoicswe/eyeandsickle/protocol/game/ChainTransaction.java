package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;

/**
 * One movement of ethecoin, shaped the way a block explorer shows a transaction.
 *
 * <h2>This is the ledger, re-rendered — not a second copy of it</h2>
 *
 * Every one of these is a {@code LedgerRow} wearing chain clothes: same amount, same moment, same
 * running balance. The explorer view and the ledger table are two renderings of one list, which is
 * the property {@code docs/design/04-mining.md} §3.1 depends on — a player who adds up the
 * transactions and compares against the balance must get the same answer, or neither surface is
 * evidence of anything.
 *
 * <h2>⚠ A mining payout comes from the zero address, and that is real</h2>
 *
 * A block reward has no sender: the coins did not exist before the block. Explorers render that as a
 * transfer from {@code 0x0000…0000}, and so does this. It is also why a coinbase costs no gas — there
 * was no transaction to execute.
 *
 * @param hash {@code 0x} + 64 hex
 * @param blockNumber the block that carries it, or -1 if it has not been mined into one yet
 * @param at when it happened
 * @param from sender address; the zero address for a block reward
 * @param to recipient address
 * @param valueMinorUnits the amount moved, always positive — direction is {@link #incoming}
 * @param incoming whether this rig received it
 * @param balanceAfterMinorUnits the running balance, carried so the log reconciles
 * @param nonce this sender's transaction count at the time
 * @param gasUsed 21 000 for a transfer, 0 for a block reward
 * @param kind the engine's own type, e.g. {@code SELF_MINING}
 * @param description the engine's own words
 * @param feeMinorUnits what the sender paid a miner to include it; 0 for a coinbase
 * @param gasPriceMinorUnits fee per gas — what a miner sorts on, and what buys priority
 * @param yours whether this rig sent or received it
 */
public record ChainTransaction(
        String hash,
        long blockNumber,
        Instant at,
        String from,
        String to,
        long valueMinorUnits,
        boolean incoming,
        long balanceAfterMinorUnits,
        long nonce,
        long gasUsed,
        String kind,
        String description,
        long feeMinorUnits,
        double gasPriceMinorUnits,
        boolean yours) {

    /** The zero address. A block reward has no sender because the coins did not exist before it. */
    public static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    /** Still in the mempool, waiting for a miner to pick it up. */
    public boolean pending() {
        return blockNumber < 0 && !coinbase();
    }

    /** Whether this was minted by a block rather than sent by anyone. */
    public boolean coinbase() {
        return ZERO_ADDRESS.equals(from);
    }

    public String shortHash() {
        return ChainBlock.shorten(hash);
    }
}
