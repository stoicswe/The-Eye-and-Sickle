package io.github.stoicswe.eyeandsickle.server.economy.account;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A character's money-and-heat state, as read from its {@code players} row.
 *
 * <h2>A character, not an account</h2>
 *
 * A DID is now an <em>account</em> that may hold several <em>characters</em>
 * ({@code docs/architecture/09-player-state-portability.md} §1), and money is per-character: balance and
 * heat sit on the character's own {@code players} row. So the counterparty spelling this snapshot carries
 * onto the ledger is the {@link #characterDid() character DID} ({@code did:eyeandsickle:<slot>:<accountDid>}),
 * derived from {@link #accountDid()} + {@link #slot()} — never the raw account DID, which two characters of
 * one account share and which would make them share a balance (the bug 09 §9 fixes).
 *
 * <p>These fields travel together because every economy decision that touches one tends to need the
 * others: a transfer needs the {@link #playerId()} and {@link #rowVersion()} to write a version-checked
 * balance update, and a gate check reads {@link #balance()} and {@link #personalHeat()} in the same breath
 * ({@code docs/design/02-unlock-gates.md} §2.1, §2.5). Reading them as one row is one query rather than
 * several.
 *
 * <p>This is a snapshot, valid only as long as the {@link #rowVersion()} it carries. A balance written
 * against a stale version matches no row and is rejected ({@code persistence/Mutations}) — which is the
 * point: the snapshot going stale is how a lost update is caught.
 *
 * @param playerId this server's local key for the character — what a version-checked update targets, and
 *     the deadlock-free lock order key
 * @param accountDid the character's account (AT Proto) DID. Nullable in the schema for local-only solo
 *     play; {@code null} together with {@code slot} for a local character, set together with it for a
 *     DID-bound one. A character that participates in the ledger has one
 * @param slot the character's save slot within the account (1..{@code Player.MAX_SLOT}); {@code null} for
 *     a local character, set exactly when {@code accountDid} is set
 * @param balance the materialised spendable balance ({@code docs/design/01-core-resources.md} §2)
 * @param personalHeat long-horizon Eye attention from this character's own actions (§4.1); the value the
 *     heat-state gate reads
 * @param rowVersion the optimistic-concurrency token this snapshot was read at
 */
public record Account(
        UUID playerId, String accountDid, Integer slot, Ethecoin balance, BigDecimal personalHeat, long rowVersion) {

    public Account {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(personalHeat, "personalHeat");
        // accountDid and slot are null together (local character) or set together (DID-bound character),
        // mirroring the schema's ck_players_slot_pairing and the identity Player record. A snapshot that
        // could carry one without the other could not produce a well-formed character DID.
        if ((accountDid == null) != (slot == null)) {
            throw new IllegalArgumentException("accountDid and slot must be null together (local character) or "
                    + "set together (DID-bound character); was accountDid=" + accountDid + ", slot=" + slot);
        }
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion is never negative, was " + rowVersion);
        }
    }

    /**
     * This character's derived, per-character identity — the ownership/counterparty id stamped into the
     * ledger's {@code from_did}/{@code to_did} and matched against when locking, instead of the shared
     * account DID ({@code docs/architecture/09-player-state-portability.md} §9, Q-item-keying option 3).
     *
     * @return the character DID for a DID-bound character, or {@code null} for a local, DID-less one
     *     (exempt from the federated economy, 09 §1)
     */
    public CharacterDid characterDid() {
        return accountDid == null ? null : new CharacterDid(accountDid, slot);
    }
}
