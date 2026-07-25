package io.github.stoicswe.eyeandsickle.server.economy.account;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's money-and-heat state, as read from the {@code players} row.
 *
 * <p>These four fields travel together because every economy decision that touches one tends to need
 * the others: a transfer needs the {@link #playerId()} and {@link #rowVersion()} to write a
 * version-checked balance update, and a gate check reads {@link #balance()} and {@link #personalHeat()}
 * in the same breath ({@code docs/design/02-unlock-gates.md} §2.1, §2.5). Reading them as one row is
 * one query rather than three.
 *
 * <p>This is a snapshot, valid only as long as the {@link #rowVersion()} it carries. A balance written
 * against a stale version matches no row and is rejected ({@code persistence/Mutations}) — which is the
 * point: the snapshot going stale is how a lost update is caught.
 *
 * @param playerId this server's local key for the player — what a version-checked update targets
 * @param did the player's portable identity; the counterparty spelling on the ledger. Nullable in the
 *     schema for local-only solo play, but an account that participates in the ledger has one
 * @param balance the materialised spendable balance ({@code docs/design/01-core-resources.md} §2)
 * @param personalHeat long-horizon Eye attention from this player's own actions (§4.1); the value the
 *     heat-state gate reads
 * @param rowVersion the optimistic-concurrency token this snapshot was read at
 */
public record Account(UUID playerId, String did, Ethecoin balance, BigDecimal personalHeat, long rowVersion) {

    public Account {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(personalHeat, "personalHeat");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion is never negative, was " + rowVersion);
        }
    }
}
