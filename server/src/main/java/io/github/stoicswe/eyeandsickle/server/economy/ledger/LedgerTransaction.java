package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the public ledger ({@code docs/design/01-core-resources.md} §2.2).
 *
 * <p>The ledger is a <em>gameplay surface</em>, not bookkeeping: investigators, player and NPC, follow
 * ethecoin flows to build evidence. So a transaction records a <strong>direction</strong> ({@link
 * #fromDid()} → {@link #toDid()}) and a <strong>magnitude</strong> ({@link #amount()}) — never a signed
 * amount, because a sign would encode direction a second time and the two would eventually disagree
 * (the same rule {@code protocol/game/Ethecoin} enforces).
 *
 * <h2>Dead Drops are here, just flagged</h2>
 *
 * {@link #traceable()} is {@code false} for a Dead Drop ({@code docs/design/08-stealth-and-noise.md}).
 * The row still exists — laundering is a gameplay verb, so an untraceable transfer must leave
 * <em>something</em> for an investigator to eventually find. What hides it is the investigator-facing
 * query, which shows an untraceable row only to its own counterparties ({@link LedgerRepository}), not
 * the absence of a row.
 *
 * @param txId the transaction's identifier
 * @param fromDid the payer, or {@code null} for a {@link LedgerEntryType#MINING_REWARD} — the faucet
 *     has no payer
 * @param toDid the payee; always present
 * @param amount the magnitude moved; always positive
 * @param type the transaction type
 * @param traceable {@code false} only for a Dead Drop
 * @param memo investigator-readable context — which miner, which duel, which vendor
 * @param createdAt when the row was written
 */
public record LedgerTransaction(
        UUID txId,
        String fromDid,
        String toDid,
        Ethecoin amount,
        LedgerEntryType type,
        boolean traceable,
        Map<String, Object> memo,
        Instant createdAt) {

    public LedgerTransaction {
        Objects.requireNonNull(txId, "txId");
        Objects.requireNonNull(toDid, "toDid");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        // A faucet has no payer; everything else moves existing ethecoin between two parties. This
        // mirrors the schema's ck_ledger_faucet, so a record that could not have been stored also
        // cannot be constructed.
        if (fromDid == null && !type.isFaucet()) {
            throw new IllegalArgumentException(
                    "Only a mining reward may have no payer; " + type + " moves existing ethecoin and needs a fromDid");
        }
        memo = memo == null ? Map.of() : Map.copyOf(memo);
    }
}
