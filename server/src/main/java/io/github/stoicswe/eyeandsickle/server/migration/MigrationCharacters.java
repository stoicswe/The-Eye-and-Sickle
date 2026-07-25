package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import java.util.UUID;

/**
 * The narrow view of the identity core's character lifecycle that migration needs.
 *
 * <h2>Policy stays in the migration services; this port is primitives</h2>
 *
 * Ownership, the {@code active}-only checks, and the ordering that guarantees no double-play all live in
 * {@link CharacterExportService} / {@link CharacterImportService} where they can be unit-tested against a
 * fake of this port. The port itself only exposes the four identity operations migration composes: read a
 * character, create a fresh one (which resets the economy — the whole point on the untrusted path, §6),
 * mark one migrated (the one-way no-double-play commit, §6.1), and — for the trusted Option-B path only —
 * restore the standing fields the identity core owns.
 *
 * <p>The default {@code IdentityMigrationCharacters} delegates straight to {@code CharacterService} and
 * {@code PlayerRepository}; a fake serves the unit tests with no database.
 */
public interface MigrationCharacters {

    /**
     * Reads a character by its id.
     *
     * @param characterId the character (a {@code players} row) id
     * @return the character
     * @throws io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException if no such row
     *     exists
     */
    Player requireCharacter(UUID characterId);

    /**
     * Creates a fresh, active, DID-bound character for the account — the economy-reset destination of a
     * migration (§6). The new character starts at base ethecoin, zero heat and no committed faction,
     * because those are exactly the freely-assertable values an untrusted source cannot be trusted for.
     * The identity core cap-checks this against the recognized-character count, so it is also where a
     * migration that would exceed the account's slot cap is refused.
     *
     * @param accountDid the account the new character belongs to
     * @param handle the display handle to give it, or {@code null}
     * @return the created character, at base economy state
     * @throws io.github.stoicswe.eyeandsickle.server.identity.CharacterSlotExceededException if the
     *     account is already at its recognized-character cap
     */
    Player createFreshCharacter(Did accountDid, String handle);

    /**
     * Marks a character migrated — the one-way {@code active -> migrated} transition that retires the old
     * home before the character goes live at the new one (§6.1). A second attempt on an already-terminal
     * character is refused, which is what prevents replay and double-play.
     *
     * @param characterId the character whose home is moving away
     * @throws io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException if the character
     *     is already migrated or retired
     * @throws io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException if it does not exist
     */
    void markMigrated(UUID characterId);

    /**
     * Restores the identity-owned standing fields onto a freshly created character — <strong>Option B
     * only</strong> (§5). This carries the committed faction and personal heat, which are legitimate to
     * move only because both operators cooperate and trust each other. It deliberately does not touch the
     * ethecoin balance (a ledger transaction the economy slice owns, Invariant I1) or faction reputation
     * (a separate table) — those remain documented seams the trusted path leaves to their owning slices.
     *
     * @param characterId the freshly created character to restore standing onto
     * @param faction the committed faction to restore
     * @param heat the personal heat to restore
     * @param expectedRowVersion the row version the caller read the fresh character at
     * @throws org.springframework.dao.OptimisticLockingFailureException if the row was concurrently changed
     */
    void restoreStanding(UUID characterId, Faction faction, Heat heat, long expectedRowVersion);
}
