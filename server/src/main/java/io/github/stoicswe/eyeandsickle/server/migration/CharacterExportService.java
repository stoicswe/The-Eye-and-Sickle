package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The <strong>source</strong> side of a migration: bundling a character up to leave, and the one-way
 * commit that retires it here ({@code docs/architecture/09-player-state-portability.md} §5, §6, §6.1).
 *
 * <h2>Export is read-only; the commit is separate — and that is deliberate</h2>
 *
 * Building a bundle changes nothing: the exact same read serves a backup (§5) and a migration (§6), and a
 * player may take a bundle without yet giving up their character. The no-double-play retire is a
 * <em>separate</em> step ({@link #commitMigration}), the one-way {@code active -> migrated} transition
 * that must happen before the character goes live at its new home (§6.1). Keeping them apart means a
 * failed hand-off leaves the character exactly where it was, recoverable, rather than stranded migrated —
 * and the operational full-fidelity safety net remains a PostgreSQL restore ({@code deploy/BACKUP.md}).
 *
 * <h2>Two exports, one trust line (§3)</h2>
 *
 * {@link #exportForMigration} produces the untrusted, verifiable Option-C bundle — DID and item chains
 * only, no economy, because the destination is not trusted. {@link #exportFullState} produces the trusted
 * Option-B bundle — the whole character, economy included — and is guarded so only an operator can call it
 * (the REST layer enforces {@link OperatorAuthorization} before this runs). They are different return
 * types on purpose, so a full-state bundle can never be fed to the untrusted-import path.
 */
@Service
public class CharacterExportService {

    private final MigrationCharacters characters;
    private final MigrationItemChains itemChains;
    private final CharacterHomeDirectory directory;
    private final LocalHomeServerDid homeServerDid;

    /**
     * @param characters the identity-core seam (read, create, migrate, restore)
     * @param itemChains the export read of provenanced items and their chains
     * @param directory the character-home directory seam (current sequence, monotonic advance)
     * @param homeServerDid this server's own DID, named as the releasing home
     */
    public CharacterExportService(
            MigrationCharacters characters,
            MigrationItemChains itemChains,
            CharacterHomeDirectory directory,
            LocalHomeServerDid homeServerDid) {
        this.characters = Objects.requireNonNull(characters, "characters");
        this.itemChains = Objects.requireNonNull(itemChains, "itemChains");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.homeServerDid = Objects.requireNonNull(homeServerDid, "homeServerDid");
    }

    // ------------------------------------------------------------------ Option C — untrusted, verifiable

    /**
     * Builds the untrusted, verifiable migration bundle for one of the account's characters (Option C,
     * §6). Player-authenticated: the character must belong to the account and be {@code active}. Read-only.
     *
     * <p>The bundle carries only the portable half of the character's state (§3): the account DID and the
     * provenanced items with their signed chains. It carries no economy — the destination resets that
     * because it cannot trust freely-assertable values from an untrusted source (§6). The home-binding
     * sequence is stamped from the directory so the destination can advance monotonically past it (§4).
     *
     * @param accountDid the authenticated account
     * @param characterId the character to bundle
     * @return the untrusted, verifiable bundle
     * @throws PlayerNotFoundException if the character does not exist or belongs to another account
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    public CharacterMigrationBundle exportForMigration(Did accountDid, UUID characterId) {
        Player character = requireOwnedActive(accountDid, characterId);
        CharacterRef ref = refOf(character);
        List<ItemChain> chains = itemChains.chainsForHolder(character.characterDid());
        long sequence = directory.currentSequence(accountDid, ref);
        return new CharacterMigrationBundle(accountDid.value(), ref, homeServerDid.value(), sequence, chains);
    }

    // ------------------------------------------------------------------ Option B — trusted, cooperative

    /**
     * Builds the trusted, full-state export for one character (Option B, §5). <strong>Operator-only</strong>
     * — the REST layer proves operator authority ({@link OperatorAuthorization}) before this is reached, and
     * this method must never be exposed on a player-facing path. Read-only.
     *
     * <p>Unlike Option C, this carries the whole character, economy included (committed faction, personal
     * heat, ethecoin balance) alongside the item chains, because both operators cooperate and trust each
     * other. That is the only legitimate way the non-portable economy moves (§3, §5).
     *
     * @param characterId the character to export in full
     * @return the trusted, full-state export
     * @throws PlayerNotFoundException if the character does not exist
     * @throws IllegalArgumentException if the character is local (DID-less) — local play is exempt from this
     *     system entirely (§1)
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    public TrustedCharacterExport exportFullState(UUID characterId) {
        Player character = requireOnlineActive(characters.requireCharacter(characterId));
        Did accountDid = character.did();
        CharacterRef ref = refOf(character);
        List<ItemChain> chains = itemChains.chainsForHolder(character.characterDid());
        long sequence = directory.currentSequence(accountDid, ref);
        return new TrustedCharacterExport(
                accountDid.value(),
                ref,
                homeServerDid.value(),
                sequence,
                character.handle(),
                character.faction(),
                character.ethecoinBalance(),
                character.personalHeat(),
                chains);
    }

    // ------------------------------------------------------------------ the no-double-play commit (§6.1)

    /**
     * Retires one of the account's characters here because it is moving to another home — the one-way
     * {@code active -> migrated} transition (§6.1). Player-authenticated. Idempotency is refusal: a second
     * call finds the character already migrated and throws, which is exactly the no-replay guarantee.
     *
     * @param accountDid the authenticated account
     * @param characterId the character being handed off
     * @throws PlayerNotFoundException if the character does not exist or belongs to another account
     * @throws CharacterNotActiveException if the character is already migrated or retired (no double-play,
     *     no replay)
     */
    @Transactional
    public void commitMigration(Did accountDid, UUID characterId) {
        requireOwnedActive(accountDid, characterId);
        characters.markMigrated(characterId);
    }

    /**
     * Retires a character here by id, for the operator-driven Option-B hand-off — the server-internal
     * counterpart to {@link #commitMigration}. The REST layer proves operator authority before this runs.
     *
     * @param characterId the character being handed off
     * @throws PlayerNotFoundException if the character does not exist
     * @throws CharacterNotActiveException if the character is already migrated or retired
     */
    @Transactional
    public void commitMigrationAsOperator(UUID characterId) {
        characters.markMigrated(characterId);
    }

    // ------------------------------------------------------------------ internals

    /**
     * A character reference from a DID-bound character. The slot is non-null for a DID-bound character (the
     * {@code active}/online cases this service handles), so this never dereferences a null slot.
     */
    private static CharacterRef refOf(Player character) {
        return CharacterRef.of(character.playerId(), character.slot());
    }

    /**
     * Reads a character, checks it belongs to the account and is playable. A mismatch is reported as
     * not-found rather than forbidden, so the endpoint discloses nothing about other accounts' characters —
     * the same discretion {@code CharacterService} uses. A local (DID-less) character is never owned by an
     * account in this system (§1), so it too reads as not-found here.
     */
    private Player requireOwnedActive(Did accountDid, UUID characterId) {
        Objects.requireNonNull(accountDid, "accountDid");
        Objects.requireNonNull(characterId, "characterId");
        Player character = characters.requireCharacter(characterId);
        if (character.isLocal() || !accountDid.equals(character.did())) {
            throw new PlayerNotFoundException(characterId);
        }
        if (!character.status().isPlayable()) {
            throw new CharacterNotActiveException(characterId, character.status());
        }
        return character;
    }

    /**
     * Requires an online (DID-bound), playable character — the operator-path check, which has no account to
     * check ownership against.
     */
    private static Player requireOnlineActive(Player character) {
        if (character.isLocal()) {
            throw new IllegalArgumentException("Character " + character.playerId()
                    + " is a local, DID-less character, which is exempt from federated migration "
                    + "(docs/architecture/09-player-state-portability.md §1).");
        }
        if (!character.status().isPlayable()) {
            throw new CharacterNotActiveException(character.playerId(), character.status());
        }
        return character;
    }
}
