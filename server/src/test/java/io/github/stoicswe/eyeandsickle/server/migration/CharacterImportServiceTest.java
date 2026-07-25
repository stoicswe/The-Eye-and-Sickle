package io.github.stoicswe.eyeandsickle.server.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterSlotExceededException;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The destination side (§6, §6.1). Most of this file is refusal, because that is where the invariants
 * live: a stale sequence, an oversized courier bundle, a cap that is full, and a chain that does not verify
 * must each be handled the safe way, and only then does a fresh character land with its verified gear.
 */
class CharacterImportServiceTest {

    private static final Did ACCOUNT = Did.of("did:plc:account00000000000000");
    private static final CharacterRef SOURCE = CharacterRef.of(UUID.randomUUID(), 2);
    private static final String HOME = "did:plc:homeserver0000000000";

    private final FakeMigrationCharacters characters = new FakeMigrationCharacters();
    private final FakeMigrationItemImporter itemImporter = new FakeMigrationItemImporter();
    private final InMemoryCharacterHomeDirectory directory = new InMemoryCharacterHomeDirectory();

    private CharacterImportService service(MigrationProperties properties) {
        return new CharacterImportService(characters, itemImporter, directory, properties);
    }

    private CharacterImportService service() {
        return service(new MigrationProperties(null, null, null, null));
    }

    private static CharacterMigrationBundle bundle(long homeSequence, List<ItemChain> chains) {
        return new CharacterMigrationBundle(ACCOUNT.value(), SOURCE, HOME, homeSequence, chains);
    }

    // ------------------------------------------------------------------ the happy path (Option C)

    @Test
    @DisplayName("creates a fresh, economy-reset character and recognizes only the verified items (§6)")
    void importsVerifiedItemsOntoFreshCharacter() {
        UUID good = UUID.randomUUID();
        UUID tampered = UUID.randomUUID();
        CharacterMigrationBundle bundle =
                bundle(0, List.of(FakeMigrationItemImporter.ok(good), FakeMigrationItemImporter.bad(tampered)));

        MigrationImportResult result = service().importVerified(bundle);

        assertThat(result.economyReset()).as("Option C resets the economy").isTrue();
        assertThat(result.recognizedItemIds()).containsExactly(good);
        assertThat(result.rejectedItemIds())
                .as("the tampered item is dropped, not recognized")
                .containsExactly(tampered);
        assertThat(result.newHomeSequence()).isEqualTo(1);

        Player fresh = characters.current(result.newCharacterId());
        assertThat(fresh.did()).isEqualTo(ACCOUNT);
        assertThat(fresh.status().isPlayable()).isTrue();
        // The reset, asserted on the actual fresh character: base economy, no matter what the source held.
        assertThat(fresh.ethecoinBalance()).isEqualTo(Ethecoin.ZERO);
        assertThat(fresh.personalHeat()).isEqualTo(Heat.ZERO);
        assertThat(fresh.faction()).isEqualTo(Faction.NONE);
    }

    @Test
    @DisplayName("a character with an empty inventory still migrates")
    void importsWithNoItems() {
        MigrationImportResult result = service().importVerified(bundle(0, List.of()));
        assertThat(result.newCharacterId()).isNotNull();
        assertThat(result.recognizedItemIds()).isEmpty();
    }

    @Test
    @DisplayName("a chain whose records name a different item than the manifest is not recognized")
    void distrustsManifestOverRecords() {
        UUID claimed = UUID.randomUUID();
        UUID actual = UUID.randomUUID();
        CharacterMigrationBundle bundle = bundle(0, List.of(FakeMigrationItemImporter.mismatch(claimed, actual)));

        MigrationImportResult result = service().importVerified(bundle);

        assertThat(result.recognizedItemIds()).isEmpty();
        assertThat(result.rejectedItemIds()).containsExactly(claimed);
    }

    // ------------------------------------------------------------------ no rollback (§6.1)

    @Test
    @DisplayName("a bundle presenting a stale home sequence is refused, and no character is created")
    void refusesStaleSequence() {
        // The directory already recognizes a later sequence for this character (a prior move).
        directory.advanceHomeToLocal(ACCOUNT, SOURCE, UUID.randomUUID(), 5); // recognizes 6

        assertThatThrownBy(() ->
                        service().importVerified(bundle(3, List.of(FakeMigrationItemImporter.ok(UUID.randomUUID())))))
                .isInstanceOf(StaleHomeSequenceException.class);
        assertThat(characters.total())
                .as("nothing is created when the bundle is refused")
                .isZero();
    }

    // ------------------------------------------------------------------ size bounds

    @Test
    @DisplayName("a bundle over the item-count limit is refused before any verification")
    void refusesTooManyItems() {
        MigrationProperties tiny = new MigrationProperties(1, null, null, null);
        CharacterMigrationBundle bundle = bundle(
                0,
                List.of(
                        FakeMigrationItemImporter.ok(UUID.randomUUID()),
                        FakeMigrationItemImporter.ok(UUID.randomUUID())));

        assertThatThrownBy(() -> service(tiny).importVerified(bundle))
                .isInstanceOf(MigrationBundleTooLargeException.class);
        assertThat(characters.total()).isZero();
    }

    @Test
    @DisplayName("a single item with too long a chain is refused")
    void refusesOverlongChain() {
        MigrationProperties tiny = new MigrationProperties(null, 1, null, null);
        CharacterMigrationBundle bundle = bundle(0, List.of(new ItemChain(UUID.randomUUID(), List.of("a", "b"))));

        assertThatThrownBy(() -> service(tiny).importVerified(bundle))
                .isInstanceOf(MigrationBundleTooLargeException.class);
    }

    @Test
    @DisplayName("a bundle over the total-size limit is refused")
    void refusesOversizeBytes() {
        MigrationProperties tiny = new MigrationProperties(null, null, 4L, null);
        CharacterMigrationBundle bundle =
                bundle(0, List.of(new ItemChain(UUID.randomUUID(), List.of("this is longer than four bytes"))));

        assertThatThrownBy(() -> service(tiny).importVerified(bundle))
                .isInstanceOf(MigrationBundleTooLargeException.class);
    }

    // ------------------------------------------------------------------ malformed vs. unverifiable

    @Test
    @DisplayName("a malformed envelope is a bad request, distinct from a chain that fails verification")
    void refusesMalformedDocument() {
        CharacterMigrationBundle bundle = bundle(0, List.of(FakeMigrationItemImporter.malformed(UUID.randomUUID())));

        assertThatThrownBy(() -> service().importVerified(bundle)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ the cap

    @Test
    @DisplayName("a migration that would exceed the account's cap is refused, and the binding is not advanced")
    void refusesWhenAtCap() {
        characters.capAt(0); // the account may hold no more characters

        assertThatThrownBy(() -> service().importVerified(bundle(0, List.of())))
                .isInstanceOf(CharacterSlotExceededException.class);
        assertThat(directory.currentSequence(ACCOUNT, SOURCE))
                .as("the home binding is only advanced after the character is created")
                .isZero();
    }

    // ------------------------------------------------------------------ Option B (trusted) import

    @Test
    @DisplayName("the trusted import restores standing but not the balance, and does not reset the economy")
    void trustedImportRestoresStanding() {
        TrustedCharacterExport export = new TrustedCharacterExport(
                ACCOUNT.value(),
                SOURCE,
                HOME,
                0,
                "neo",
                Faction.SICKLE,
                Ethecoin.ofMinorUnits(1000),
                Heat.ZERO.plus(BigDecimal.valueOf(7)),
                List.of(FakeMigrationItemImporter.ok(UUID.randomUUID())));

        MigrationImportResult result = service().importTrusted(export);

        assertThat(result.economyReset())
                .as("Option B carries standing, it does not reset")
                .isFalse();
        assertThat(result.recognizedCount()).isEqualTo(1);

        Player fresh = characters.current(result.newCharacterId());
        assertThat(fresh.faction())
                .as("faction carries on the trusted path (§5)")
                .isEqualTo(Faction.SICKLE);
        assertThat(fresh.personalHeat().value()).isEqualByComparingTo(BigDecimal.valueOf(7));
        // The balance is deliberately NOT written here — re-applying it is a ledger transaction the economy
        // slice owns (Invariant I1). Documented seam; the lossless path is a pg dump/restore.
        assertThat(fresh.ethecoinBalance())
                .as("balance carry is a documented ledger seam")
                .isEqualTo(Ethecoin.ZERO);
    }
}
