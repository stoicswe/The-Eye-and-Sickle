package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import java.util.Objects;

/**
 * The kinds of ledger transaction, and the faucet/transfer distinction the economy depends on.
 *
 * <h2>Why a server-side enum and not a protocol type</h2>
 *
 * The transaction type is a value the ledger renders as a gameplay surface, but it is not a wire
 * contract the client authors — the client never <em>writes</em> the ledger, it reads it (Invariant
 * I14). So this vocabulary lives on the server, next to the service that enforces it, spelled to the
 * six {@code ledger_transactions.tx_type} values the schema permits.
 *
 * <h2>The one distinction that protects the economy: faucet vs transfer</h2>
 *
 * {@code docs/design/03-economy.md} §5 rule 3: player-to-player takings — crack seizures, raid loot —
 * are <strong>transfers, not faucets</strong>. They move ethecoin that already exists; they never mint
 * it. Only mining rewards mint. Getting this wrong is not a rounding error, it is inflation: a crack
 * seizure that minted would create currency every time a miner was cracked.
 *
 * <p>So exactly one constant, {@link #MINING_REWARD}, is a {@link #isFaucet() faucet}, and the ledger
 * service uses that flag to route the two fundamentally different operations — {@code mint} (one
 * narrow, auditable path, no payer) and {@code transfer} (a payer is always named). The database
 * echoes the same rule in {@code ck_ledger_faucet}: a payerless row must be a mining reward. Belt and
 * braces, because on an authoritative server the database is the last line of defence.
 *
 * <h2>Exhaustive switches, like {@code persistence/EnumColumns}</h2>
 *
 * The database spelling comes from an exhaustive switch, not {@code name().toLowerCase()}, so renaming
 * a constant is a compile error rather than a silent vocabulary drift that starts failing
 * {@code ck_ledger_tx_type} at runtime on someone's self-hosted server.
 */
public enum LedgerEntryType {

    /**
     * The economy's faucet: newly minted ethecoin, no payer. Self-mining and active-hacking payouts
     * ({@code docs/design/04-mining.md}). The <em>only</em> operation that increases the total supply.
     */
    MINING_REWARD(true),

    /** A voluntary player-to-player exchange. Moves ethecoin between two parties. */
    TRADE(false),

    /**
     * Seizure of a cracked miner's yield buffer ({@code docs/design/04-mining.md} §5.1). A transfer —
     * the buffer already exists on the host; cracking moves it, it does not mint it.
     */
    CRACK_SEIZURE(false),

    /** Loot taken in a raid on exposed storage ({@code docs/design/01-core-resources.md} §6). A transfer. */
    RAID_LOOT(false),

    /**
     * Ethecoin converted toward Sickle reputation via the Payout Splitter ({@code
     * docs/design/11-rig-infrastructure.md}) — a voluntary EC sink. A transfer to the faction sink, not
     * a mint.
     */
    PAYOUT_SPLITTER(false),

    /** Buying from a vendor: a transfer to the seller, an ethecoin sink when the seller is an NPC. */
    PURCHASE(false);

    private final boolean faucet;

    LedgerEntryType(boolean faucet) {
        this.faucet = faucet;
    }

    /**
     * Whether this type mints ethecoin rather than moving it.
     *
     * @return {@code true} only for {@link #MINING_REWARD}
     */
    public boolean isFaucet() {
        return faucet;
    }

    /**
     * This type's {@code ledger_transactions.tx_type} spelling.
     *
     * @return the database value
     */
    public String wireValue() {
        return switch (this) {
            case MINING_REWARD -> "mining_reward";
            case TRADE -> "trade";
            case CRACK_SEIZURE -> "crack_seizure";
            case RAID_LOOT -> "raid_loot";
            case PAYOUT_SPLITTER -> "payout_splitter";
            case PURCHASE -> "purchase";
        };
    }

    /**
     * The constant a stored {@code tx_type} names.
     *
     * @param value a stored value
     * @return the constant
     * @throws IllegalArgumentException if the value is not one of the six defined types — rejected, not
     *     mapped to a fallback, because a type this build does not understand is one it cannot apply a
     *     rule to
     */
    public static LedgerEntryType fromWire(String value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case "mining_reward" -> MINING_REWARD;
            case "trade" -> TRADE;
            case "crack_seizure" -> CRACK_SEIZURE;
            case "raid_loot" -> RAID_LOOT;
            case "payout_splitter" -> PAYOUT_SPLITTER;
            case "purchase" -> PURCHASE;
            default ->
                throw new IllegalArgumentException("Unknown ledger tx_type '" + value
                        + "'; a migration added a value this build predates, " + "or the row bypassed LedgerEntryType");
        };
    }
}
