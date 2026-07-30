package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
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
 * @param valueWei the amount moved, always positive — direction is {@link #incoming}
 * @param incoming whether this rig received it
 * @param balanceAfterWei the running balance, carried so the log reconciles
 * @param nonce this sender's transaction count at the time
 * @param gasUsed 21 000 for a transfer, 0 for a block reward
 * @param kind the engine's own type, e.g. {@code SELF_MINING}
 * @param description the engine's own words
 * @param feeWei what the sender paid a miner to include it; 0 for a coinbase
 * @param gasPriceWei fee per gas — what a miner sorts on, and what buys priority
 * @param yours whether this rig sent or received it
 */
public record ChainTransaction(
        String hash,
        long blockNumber,
        Instant at,
        String from,
        String to,
        BigInteger valueWei,
        boolean incoming,
        BigInteger balanceAfterWei,
        long nonce,
        long gasUsed,
        String kind,
        String description,
        BigInteger feeWei,
        double gasPriceWei,
        boolean yours,
        String counterpartyLabel) {

    public ChainTransaction {
        counterpartyLabel = counterpartyLabel == null ? "" : counterpartyLabel;
    }

    /**
     * A readable name for the other end, or empty when there is only an address.
     *
     * <h2>⚠ A label, never a substitute for the address</h2>
     *
     * A pool payout comes from a pool that has a name, and rendering it as
     * {@code 0x8f3c…a219} makes the one row a player most needs to recognise the least
     * recognisable thing in the table. But the address is what is actually on the chain, so this is
     * carried <em>beside</em> it rather than replacing it — the ledger prints the name and the
     * explorer still has the address to check it against, which is the whole point of {@code 04}
     * §3.1's audit.
     *
     * <p>⚠ Never populated for a counterparty the client cannot verify. An attacker-supplied name
     * rendered where an address belongs is how a transfer gets mistaken for a payout.
     */
    public String counterpartyLabel() {
        return counterpartyLabel;
    }

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
