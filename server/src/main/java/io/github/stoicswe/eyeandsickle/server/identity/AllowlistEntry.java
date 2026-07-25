package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of the operator-controlled join allowlist ({@code allowlist_entries},
 * {@code docs/architecture/03-server-and-federation.md} §1).
 *
 * <h2>Revocation is soft, and carries who did it</h2>
 *
 * A home server is private by default and the operator chooses who joins; when they change their mind,
 * the entry is <em>revoked</em>, not deleted. Who was allowed in and when they stopped being allowed in
 * is exactly the record an operator wants after an incident, and a DELETE destroys it. The schema pairs
 * {@code revoked_at} with {@code revoked_by_did} ({@code ck_allowlist_entries_revoked_pair}): a
 * revocation always names an actor, because an unattributable moderation action is the one an operator
 * will later need to explain.
 *
 * <p>{@link #isActive()} is the whole point of the row from the sign-in path's perspective: a DID may
 * join right now iff it has an entry whose {@code revokedAt} is null.
 *
 * @param entryId primary key
 * @param did the allowed identity
 * @param addedAt when the entry was created
 * @param addedBy the DID that added it, or {@code null} — nullable because the initial seed from
 *     configuration has no in-game actor to attribute it to
 * @param note free-text operator note (why they were added, or which operator added them)
 * @param revokedAt when access was withdrawn, or {@code null} while active
 * @param revokedBy the DID that revoked it; null iff {@code revokedAt} is null
 */
public record AllowlistEntry(
        UUID entryId, Did did, Instant addedAt, Did addedBy, String note, Instant revokedAt, Did revokedBy) {

    public AllowlistEntry {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(addedAt, "addedAt");
        // Mirror ck_allowlist_entries_revoked_pair so an inconsistent in-memory entry cannot exist even
        // transiently: both revocation markers agree, or neither is set.
        if ((revokedAt == null) != (revokedBy == null)) {
            throw new IllegalArgumentException(
                    "A revocation must name an actor: revokedAt and revokedBy are set together or not at all");
        }
    }

    /**
     * @return whether this DID may currently join — i.e. the entry has not been revoked
     */
    public boolean isActive() {
        return revokedAt == null;
    }
}
