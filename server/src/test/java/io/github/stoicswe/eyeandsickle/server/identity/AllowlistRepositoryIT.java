package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AllowlistRepository} against a real PostgreSQL. The behaviour that genuinely needs the database:
 * the {@code ON CONFLICT (did)} idempotency, the soft-revoke that keeps the row, the partial index's
 * "active entry" semantics, and the consequence that re-adding a revoked DID does NOT un-revoke it.
 */
class AllowlistRepositoryIT extends DatabaseIntegrationTestBase {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did OTHER = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Did OPERATOR = Did.of("did:plc:cccccccccccccccccccccccc");

    private static final Instant ADDED = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant REVOKED = Instant.parse("2026-07-24T12:00:00Z");

    private AllowlistRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AllowlistRepository(jdbcClient());
    }

    @Test
    @DisplayName("closed by default: an empty allowlist admits nobody")
    void emptyTableAdmitsNobody() {
        assertThat(repository.isAllowed(DID)).isFalse();
    }

    @Test
    @DisplayName("insert makes a DID allowed; a second insert of the same DID is a harmless no-op")
    void insertIsIdempotentByDid() {
        assertThat(repository.insertIfAbsent(DID, null, AllowlistSeeder.SEED_NOTE, ADDED))
                .isTrue();
        assertThat(repository.isAllowed(DID)).isTrue();

        // Seeding the same config twice, or re-adding an existing DID, must not fail on the unique key.
        assertThat(repository.insertIfAbsent(DID, null, "again", ADDED)).isFalse();
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("a seeded entry records its note and has no adder")
    void seedEntryShape() {
        repository.insertIfAbsent(DID, null, AllowlistSeeder.SEED_NOTE, ADDED);

        AllowlistEntry entry = repository.findByDid(DID).orElseThrow();
        assertThat(entry.did()).isEqualTo(DID);
        assertThat(entry.addedBy()).isNull();
        assertThat(entry.note()).isEqualTo(AllowlistSeeder.SEED_NOTE);
        assertThat(entry.addedAt()).isEqualTo(ADDED);
        assertThat(entry.isActive()).isTrue();
    }

    @Test
    @DisplayName("revoke is soft: the DID becomes disallowed but the row and its attribution survive")
    void revokeIsSoft() {
        repository.insertIfAbsent(DID, null, null, ADDED);

        assertThat(repository.revoke(DID, OPERATOR, REVOKED)).isTrue();
        assertThat(repository.isAllowed(DID)).isFalse();

        AllowlistEntry entry = repository.findByDid(DID).orElseThrow();
        assertThat(entry.isActive()).isFalse();
        assertThat(entry.revokedAt()).isEqualTo(REVOKED);
        assertThat(entry.revokedBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("revoking an absent or already-revoked DID reports no active entry was affected")
    void revokeIsOnlyForActiveEntries() {
        // The operator's intent — "this DID is not welcome" — is already satisfied in both cases, so it is
        // a false return, not a failure.
        assertThat(repository.revoke(DID, OPERATOR, REVOKED)).isFalse(); // absent

        repository.insertIfAbsent(DID, null, null, ADDED);
        assertThat(repository.revoke(DID, OPERATOR, REVOKED)).isTrue();
        assertThat(repository.revoke(DID, OPERATOR, REVOKED.plusSeconds(60))).isFalse(); // already revoked
    }

    @Test
    @DisplayName("re-adding a REVOKED DID does nothing and does NOT silently re-admit it")
    void reAddingRevokedDidDoesNotUnRevoke() {
        // The conflict target is the unique DID, so an insert against a revoked row is a no-op; un-revoking
        // is a separate, deliberate action, never a side effect of an add. This is the whole reason the
        // runtime table wins over the config seed.
        repository.insertIfAbsent(DID, null, null, ADDED);
        repository.revoke(DID, OPERATOR, REVOKED);

        assertThat(repository.insertIfAbsent(DID, null, "seeded again", ADDED)).isFalse();
        assertThat(repository.isAllowed(DID))
                .as("a revoked DID stays revoked despite a re-seed")
                .isFalse();
        assertThat(repository.findByDid(DID).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("findAll returns active and revoked entries, newest first")
    void findAllIsTheAuditView() {
        repository.insertIfAbsent(DID, null, null, ADDED);
        repository.insertIfAbsent(OTHER, OPERATOR, null, ADDED.plusSeconds(3600));
        repository.revoke(DID, OPERATOR, REVOKED);

        assertThat(repository.findAll())
                .extracting(AllowlistEntry::did)
                .containsExactly(OTHER, DID); // added_at DESC: OTHER is later
        assertThat(repository.findAll()).anyMatch(e -> !e.isActive());
    }

    @Test
    @DisplayName("a DID with no entry resolves to empty")
    void findByDidAbsent() {
        assertThat(repository.findByDid(DID)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("revoke demands an attributing actor — the schema requires one and so does the method")
    void revokeRequiresRevoker() {
        repository.insertIfAbsent(DID, null, null, ADDED);
        // Passing a null revoker would be a ck_allowlist_entries_revoked_pair violation; the method refuses
        // it earlier, as a null argument.
        assertThatThrownBy(() -> repository.revoke(DID, null, REVOKED)).isInstanceOf(NullPointerException.class);
    }
}
