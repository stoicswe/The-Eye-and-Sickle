package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterStatus;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole Option-C flow composed end to end (§6, §6.1), with real export and import services over the
 * fakes. This is where the two headline guarantees meet: the economy does <strong>not</strong> carry (the
 * fresh character starts at base, even though the source was rich), and a migrated character cannot be
 * migrated again (no double-play / no replay).
 */
class CharacterMigrationServiceTest {

    private static final Did ACCOUNT = Did.of("did:plc:account00000000000000");
    private static final String HOME = "did:plc:homeserver0000000000";

    private final FakeMigrationCharacters characters = new FakeMigrationCharacters();
    private final FakeMigrationItemChains itemChains = new FakeMigrationItemChains();
    private final FakeMigrationItemImporter itemImporter = new FakeMigrationItemImporter();
    private final InMemoryCharacterHomeDirectory directory = new InMemoryCharacterHomeDirectory();

    private final CharacterExportService exportService =
            new CharacterExportService(characters, itemChains, directory, () -> HOME);
    private final CharacterImportService importService = new CharacterImportService(
            characters, itemImporter, directory, new MigrationProperties(null, null, null, null));
    private final CharacterMigrationService migration = new CharacterMigrationService(exportService, importService);

    @Test
    @DisplayName("migrates a character: source retired, fresh economy-reset character holds the verified gear")
    void migratesWithinServer() {
        // A rich source: committed EYE, 500 EC, heat 5, one good item and one tampered one.
        Player source = characters.seed(ACCOUNT, 1, Faction.EYE, 500, 5);
        UUID good = UUID.randomUUID();
        UUID tampered = UUID.randomUUID();
        itemChains.put(
                new CharacterDid(ACCOUNT.value(), 1),
                List.of(FakeMigrationItemImporter.ok(good), FakeMigrationItemImporter.bad(tampered)));

        MigrationImportResult result = migration.migrateVerifiableWithinServer(ACCOUNT, source.playerId());

        // No double-play: the source is retired before the destination is live (§6.1).
        assertThat(characters.current(source.playerId()).status()).isEqualTo(CharacterStatus.MIGRATED);

        // A distinct, fresh character now holds the account, with a RESET economy (§6) ...
        assertThat(result.newCharacterId()).isNotEqualTo(source.playerId());
        Player fresh = characters.current(result.newCharacterId());
        assertThat(fresh.status()).isEqualTo(CharacterStatus.ACTIVE);
        assertThat(fresh.did()).isEqualTo(ACCOUNT);
        assertThat(fresh.ethecoinBalance()).as("balance does not carry").isEqualTo(Ethecoin.ZERO);
        assertThat(fresh.personalHeat()).as("heat does not carry").isEqualTo(Heat.ZERO);
        assertThat(fresh.faction()).as("faction does not carry").isEqualTo(Faction.NONE);

        // ... and only the verified item; the tampered one was dropped.
        assertThat(result.economyReset()).isTrue();
        assertThat(result.recognizedItemIds()).containsExactly(good);
        assertThat(result.rejectedItemIds()).containsExactly(tampered);
        assertThat(result.newHomeSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("a migrated character cannot be migrated again — no double-play, no replay (§6.1)")
    void refusesReplay() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        itemChains.put(new CharacterDid(ACCOUNT.value(), 1), List.of());

        migration.migrateVerifiableWithinServer(ACCOUNT, source.playerId());

        assertThatThrownBy(() -> migration.migrateVerifiableWithinServer(ACCOUNT, source.playerId()))
                .isInstanceOf(CharacterNotActiveException.class);
    }
}
