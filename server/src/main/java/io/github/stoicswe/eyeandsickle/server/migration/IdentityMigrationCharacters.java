package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterService;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The default {@link MigrationCharacters}: delegates straight to the identity core.
 *
 * <p>Migration owns no character persistence of its own — a character is a {@code players} row, and the
 * identity core is authoritative over it (Invariant I14). So this adapter simply routes migration's four
 * needs to the core's own methods: a read and the cap-checked create through {@code CharacterService}, the
 * one-way {@code markMigrated} through it too, and the standing restore through {@code PlayerRepository}'s
 * version-checked write. Nothing here re-implements a rule the core already owns; the migration policy —
 * ordering, ownership, the reset — sits in {@link CharacterExportService} / {@link CharacterImportService}.
 */
@Component
class IdentityMigrationCharacters implements MigrationCharacters {

    private final CharacterService characters;
    private final PlayerRepository players;

    IdentityMigrationCharacters(CharacterService characters, PlayerRepository players) {
        this.characters = Objects.requireNonNull(characters, "characters");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public Player requireCharacter(UUID characterId) {
        return players.requireCharacter(characterId);
    }

    @Override
    public Player createFreshCharacter(Did accountDid, String handle) {
        return characters.createCharacter(accountDid, handle);
    }

    @Override
    public void markMigrated(UUID characterId) {
        characters.markMigrated(characterId);
    }

    @Override
    public void restoreStanding(UUID characterId, Faction faction, Heat heat, long expectedRowVersion) {
        players.updateFactionAndHeat(characterId, faction, heat, expectedRowVersion);
    }
}
