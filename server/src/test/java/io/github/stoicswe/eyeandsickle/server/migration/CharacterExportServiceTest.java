package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterStatus;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The source side (§5, §6, §6.1): building bundles, and the one-way retire.
 *
 * <p>Two things are load-bearing and get their own assertions: the untrusted Option-C bundle carries
 * <em>no</em> economy while the trusted Option-B export carries all of it (§3), and {@code commitMigration}
 * is one-way — a second commit is refused, which is the no-double-play/no-replay guarantee (§6.1).
 */
class CharacterExportServiceTest {

    private static final Did ACCOUNT = Did.of("did:plc:account00000000000000");
    private static final Did OTHER = Did.of("did:plc:otheraccount00000000");
    private static final String HOME = "did:plc:homeserver0000000000";

    private final FakeMigrationCharacters characters = new FakeMigrationCharacters();
    private final FakeMigrationItemChains itemChains = new FakeMigrationItemChains();
    private final InMemoryCharacterHomeDirectory directory = new InMemoryCharacterHomeDirectory();

    private final CharacterExportService service =
            new CharacterExportService(characters, itemChains, directory, () -> HOME);

    // ------------------------------------------------------------------ Option C export

    @Test
    @DisplayName("exports the DID, source reference, home binding and item chains — and no economy (§3)")
    void exportsPortableStateOnly() {
        Player source = characters.seed(ACCOUNT, 2, Faction.EYE, 500, 5);
        UUID itemId = UUID.randomUUID();
        List<ItemChain> chains = List.of(new ItemChain(itemId, List.of("{env}")));
        itemChains.put(new CharacterDid(ACCOUNT.value(), 2), chains);

        CharacterMigrationBundle bundle = service.exportForMigration(ACCOUNT, source.playerId());

        assertThat(bundle.accountDid()).isEqualTo(ACCOUNT.value());
        assertThat(bundle.sourceCharacter().characterId()).isEqualTo(source.playerId());
        assertThat(bundle.sourceCharacter().slot()).isEqualTo(2);
        assertThat(bundle.sourceHomeServerDid()).isEqualTo(HOME);
        assertThat(bundle.homeSequence()).isZero();
        assertThat(bundle.itemChains()).isEqualTo(chains);
        // The economy the source plainly has (500 EC, heat 5, EYE) has no field to ride on: the bundle's
        // only components are identity + items. That structural fact is the §3 guarantee.
        assertThat(CharacterMigrationBundle.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("ethecoinBalance", "personalHeat", "faction");
    }

    @Test
    @DisplayName("export is read-only — it does not retire the character")
    void exportDoesNotRetire() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        service.exportForMigration(ACCOUNT, source.playerId());
        assertThat(characters.current(source.playerId()).status()).isEqualTo(CharacterStatus.ACTIVE);
    }

    @Test
    @DisplayName("a character owned by another account is not found")
    void exportRejectsForeignCharacter() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        assertThatThrownBy(() -> service.exportForMigration(OTHER, source.playerId()))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    @DisplayName("a local, DID-less character is exempt from federated migration and reads as not found")
    void exportRejectsLocalCharacter() {
        Player local = characters.seedLocal();
        assertThatThrownBy(() -> service.exportForMigration(ACCOUNT, local.playerId()))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    @DisplayName("an already-migrated character cannot be exported for migration again")
    void exportRejectsMigratedCharacter() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        characters.markMigrated(source.playerId());
        assertThatThrownBy(() -> service.exportForMigration(ACCOUNT, source.playerId()))
                .isInstanceOf(CharacterNotActiveException.class);
    }

    // ------------------------------------------------------------------ Option B full-state export

    @Test
    @DisplayName("the trusted full-state export carries the whole economy (§5)")
    void fullStateExportCarriesEconomy() {
        Player source = characters.seed(ACCOUNT, 3, Faction.SICKLE, 1000, 7);
        itemChains.put(
                new CharacterDid(ACCOUNT.value(), 3), List.of(new ItemChain(UUID.randomUUID(), List.of("{env}"))));

        TrustedCharacterExport export = service.exportFullState(source.playerId());

        assertThat(export.accountDid()).isEqualTo(ACCOUNT.value());
        assertThat(export.faction()).isEqualTo(Faction.SICKLE);
        assertThat(export.ethecoinBalance()).isEqualTo(Ethecoin.ofDecimal("10"));
        assertThat(export.personalHeat().value()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(export.itemChains()).hasSize(1);
    }

    @Test
    @DisplayName("a local character cannot be full-state exported")
    void fullStateRejectsLocal() {
        Player local = characters.seedLocal();
        assertThatThrownBy(() -> service.exportFullState(local.playerId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ the no-double-play commit

    @Test
    @DisplayName("commit retires the character here (active -> migrated)")
    void commitMarksMigrated() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);

        service.commitMigration(ACCOUNT, source.playerId());

        assertThat(characters.current(source.playerId()).status()).isEqualTo(CharacterStatus.MIGRATED);
    }

    @Test
    @DisplayName("committing a second time is refused — no double-play, no replay (§6.1)")
    void commitIsOneWay() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        service.commitMigration(ACCOUNT, source.playerId());

        assertThatThrownBy(() -> service.commitMigration(ACCOUNT, source.playerId()))
                .isInstanceOf(CharacterNotActiveException.class);
    }

    @Test
    @DisplayName("committing another account's character is not found")
    void commitRejectsForeignCharacter() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        assertThatThrownBy(() -> service.commitMigration(OTHER, source.playerId()))
                .isInstanceOf(PlayerNotFoundException.class);
        assertThat(characters.current(source.playerId()).status())
                .as("a refused commit leaves the character active")
                .isEqualTo(CharacterStatus.ACTIVE);
    }

    @Test
    @DisplayName("the operator commit path is also one-way")
    void operatorCommitIsOneWay() {
        Player source = characters.seed(ACCOUNT, 1, Faction.NONE, 0, 0);
        service.commitMigrationAsOperator(source.playerId());
        assertThat(characters.current(source.playerId()).status()).isEqualTo(CharacterStatus.MIGRATED);

        assertThatThrownBy(() -> service.commitMigrationAsOperator(source.playerId()))
                .isInstanceOf(CharacterNotActiveException.class);
    }
}
