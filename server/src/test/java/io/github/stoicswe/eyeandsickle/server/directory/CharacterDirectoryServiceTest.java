package io.github.stoicswe.eyeandsickle.server.directory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.server.directory.CharacterDirectoryService.IngestResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CharacterDirectoryService} — the one place a signed home binding converges.
 *
 * <p>The convergence rule is the load-bearing claim of Option E ({@code
 * docs/architecture/09-player-state-portability.md} §4): last-writer-wins is safe here <em>only</em>
 * because it is decided on the signed monotonic sequence, never on a clock. These tests drive an
 * in-memory repository so they need no database, and they prove the outcomes (new, advanced, duplicate,
 * conflict, stale) plus the two refusals a hostile federation forces — a capacity flood and a rolled-back
 * sequence — and that a migration's home change advances the binding while a replay of the old home is
 * refused.
 */
class CharacterDirectoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private final CharacterHomeFixture fixture = new CharacterHomeFixture();
    private final FakeCharacterDirectoryRepository repository = new FakeCharacterDirectoryRepository();

    private static CharacterDirectoryProperties properties() {
        return new CharacterDirectoryProperties(null, null, null);
    }

    private static CharacterDirectoryProperties properties(int maxDirectorySize) {
        return new CharacterDirectoryProperties(null, maxDirectorySize, null);
    }

    private CharacterDirectoryService service(CharacterDirectoryProperties properties) {
        return new CharacterDirectoryService(
                repository, new CharacterHomeRecordVerifier(properties, fixture.resolver()), properties);
    }

    private CharacterDirectoryService service() {
        return service(properties());
    }

    /** A record for a different home server (a migration target), same account/slot, chosen sequence. */
    private CharacterHomeRecord atOtherHome(UUID characterId, int slot, long sequence) {
        // accept() does not re-verify, so the signing key is irrelevant here; the point is a different home
        // DID and a different character id, i.e. a genuine home change.
        return CharacterHomeRecord.sign(
                CharacterHomeFixture.ACCOUNT_DID,
                characterId,
                slot,
                CharacterHomeFixture.OTHER_HOME_DID,
                CharacterHomeFixture.OTHER_HOME_DID + "#key1",
                "https://other-home.example.org",
                fixture.transportKey,
                sequence,
                fixture.signing.getPrivate());
    }

    // ==================================================================== accept — convergence

    @Nested
    @DisplayName("accept — convergence on the signed sequence")
    class Accept {

        @Test
        @DisplayName("an unknown binding under the cap is added")
        void unknownIsAdded() {
            HomeAcceptOutcome outcome = service().accept(fixture.record(5), NOW);

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.ACCEPTED_NEW);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a home change advances the binding to the new home on a strictly-higher sequence")
        void homeChangeAdvances() {
            CharacterDirectoryService service = service();
            service.accept(fixture.record(5), NOW);

            UUID freshId = UUID.fromString("22222222-2222-2222-2222-222222222222");
            HomeAcceptOutcome outcome =
                    service.accept(atOtherHome(freshId, CharacterHomeFixture.SLOT, 6), NOW.plusSeconds(10));

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.ACCEPTED_UPDATED);
            CharacterHomeEntry entry = repository
                    .findByAccountAndSlot(CharacterHomeFixture.ACCOUNT_DID, CharacterHomeFixture.SLOT)
                    .orElseThrow();
            assertThat(entry.homeServerDid()).isEqualTo(CharacterHomeFixture.OTHER_HOME_DID);
            assertThat(entry.characterId()).isEqualTo(freshId);
            assertThat(entry.sequenceNumber()).isEqualTo(6);
        }

        @Test
        @DisplayName("an identical re-announcement (same sequence, same signature) is a harmless refresh")
        void duplicateIsRefreshed() {
            CharacterDirectoryService service = service();
            service.accept(fixture.record(5), NOW);

            HomeAcceptOutcome outcome = service.accept(fixture.record(5), NOW.plusSeconds(30));

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.IGNORED_DUPLICATE);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a different binding at the SAME sequence is a fork, refused — the stored one stands")
        void equalSequenceConflictIsRefused() {
            CharacterDirectoryService service = service();
            service.accept(fixture.record(5), NOW);

            // Same account/slot/sequence, but a different home (hence a different signature). Two servers
            // claiming the slot at once: refuse, do not overwrite (open question Q-cap-race, safe side).
            UUID otherId = UUID.fromString("33333333-3333-3333-3333-333333333333");
            HomeAcceptOutcome outcome = service.accept(atOtherHome(otherId, CharacterHomeFixture.SLOT, 5), NOW);

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.IGNORED_CONFLICT);
            CharacterHomeEntry entry = repository
                    .findByAccountAndSlot(CharacterHomeFixture.ACCOUNT_DID, CharacterHomeFixture.SLOT)
                    .orElseThrow();
            assertThat(entry.homeServerDid()).isEqualTo(CharacterHomeFixture.HOME_DID);
        }

        @Test
        @DisplayName("a lower sequence is refused as stale — replaying an old home cannot resurrect a moved character")
        void staleRollbackIsRefused() {
            CharacterDirectoryService service = service();
            // Character starts at the original home, seq 5, then migrates away to the new home at seq 6.
            service.accept(fixture.record(5), NOW);
            UUID freshId = UUID.fromString("44444444-4444-4444-4444-444444444444");
            service.accept(atOtherHome(freshId, CharacterHomeFixture.SLOT, 6), NOW.plusSeconds(10));

            // An attacker replays the original home's seq-5 record. It must not roll the character back.
            HomeAcceptOutcome outcome = service.accept(fixture.record(5), NOW.plusSeconds(20));

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.IGNORED_STALE);
            CharacterHomeEntry entry = repository
                    .findByAccountAndSlot(CharacterHomeFixture.ACCOUNT_DID, CharacterHomeFixture.SLOT)
                    .orElseThrow();
            assertThat(entry.homeServerDid()).isEqualTo(CharacterHomeFixture.OTHER_HOME_DID);
            assertThat(entry.sequenceNumber()).isEqualTo(6);
        }

        @Test
        @DisplayName("a new binding beyond the directory capacity is refused")
        void capacityFloodingIsRefused() {
            // maxDirectorySize must be positive (0 is rejected by the properties), so "at capacity" is
            // modelled by filling the directory to its cap of 1 with an UNRELATED binding first. A new
            // (account, slot) then finds the directory full.
            CharacterDirectoryService service = service(properties(1));
            FakeCharacterDirectoryRepository.Stored seeded = new FakeCharacterDirectoryRepository.Stored();
            seeded.record = fixture.record(1);
            seeded.firstSeen = NOW;
            seeded.lastSeen = NOW;
            repository.byKey.put("did:plc:unrelatedaccount00#1", seeded);

            HomeAcceptOutcome outcome = service.accept(fixture.record(1), NOW);

            assertThat(outcome).isEqualTo(HomeAcceptOutcome.IGNORED_AT_CAPACITY);
            // The flood was refused: the directory still holds only the one pre-existing binding.
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a concurrent insert winning the race is treated as an existing binding, not a lost write")
        void concurrentInsertIsHandled() {
            CharacterDirectoryService service = service();
            // Simulate another connection inserting the same (account, slot) between our count and insert.
            repository.onInsertAttempt = () -> {
                if (repository.byKey.isEmpty()) {
                    repository.byKey.put(
                            CharacterHomeFixture.ACCOUNT_DID + "#" + CharacterHomeFixture.SLOT, winningRow());
                }
            };

            HomeAcceptOutcome outcome = service.accept(fixture.record(5), NOW);

            // The concurrent row was seq 5 too and identical content is not guaranteed, so it converges as a
            // conflict or duplicate — never a second row, and never a crash.
            assertThat(outcome).isIn(HomeAcceptOutcome.IGNORED_DUPLICATE, HomeAcceptOutcome.IGNORED_CONFLICT);
            assertThat(repository.count()).isEqualTo(1);
        }

        private FakeCharacterDirectoryRepository.Stored winningRow() {
            FakeCharacterDirectoryRepository.Stored stored = new FakeCharacterDirectoryRepository.Stored();
            stored.record = fixture.record(5);
            stored.firstSeen = NOW;
            stored.lastSeen = NOW;
            return stored;
        }
    }

    // ==================================================================== ingest — verify then converge

    @Nested
    @DisplayName("ingest — verify, then converge")
    class Ingest {

        @Test
        @DisplayName("a valid published record is verified and stored")
        void validIsStored() {
            IngestResult result = service().ingest(fixture.signed(3), NOW);

            assertThat(result.verification().isAccepted()).isTrue();
            assertThat(result.outcome()).isEqualTo(HomeAcceptOutcome.ACCEPTED_NEW);
            assertThat(result.stored()).isTrue();
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("an unverifiable record is refused and never touches storage")
        void unverifiableIsNeverStored() {
            // A verifier that resolves no key refuses every record; nothing must be written.
            CharacterDirectoryService service = new CharacterDirectoryService(
                    repository,
                    new CharacterHomeRecordVerifier(properties(), CharacterHomeKeyResolver.empty()),
                    properties());

            IngestResult result = service.ingest(fixture.signed(3), NOW);

            assertThat(result.verification().isAccepted()).isFalse();
            assertThat(result.verification().fault()).isEqualTo(CharacterHomeFault.UNKNOWN_SIGNING_KEY);
            assertThat(result.outcome()).isNull();
            assertThat(result.stored()).isFalse();
            assertThat(repository.count()).isZero();
        }
    }

    // ==================================================================== resolve + recognized count

    @Nested
    @DisplayName("resolve and recognized-character count")
    class ResolveAndCount {

        @Test
        @DisplayName("resolveHomes returns an account's bindings ordered by slot, and passes the cap through")
        void resolveOrdersBySlot() {
            CharacterDirectoryService service = service();
            service.accept(fixture.record(UUID.randomUUID(), 3, 1, fixture.signing.getPrivate()), NOW);
            service.accept(fixture.record(UUID.randomUUID(), 1, 1, fixture.signing.getPrivate()), NOW);
            service.accept(fixture.record(UUID.randomUUID(), 2, 1, fixture.signing.getPrivate()), NOW);

            List<CharacterHomeEntry> homes = service.resolveHomes(CharacterHomeFixture.ACCOUNT_DID);

            assertThat(homes).extracting(CharacterHomeEntry::slot).containsExactly(1, 2, 3);
            assertThat(repository.lastFindByAccountLimit)
                    .isEqualTo(CharacterDirectoryProperties.DEFAULT_MAX_HOMES_PER_RESOLVE);
        }

        @Test
        @DisplayName("recognizedCharacterCount counts one per occupied slot — the federation-wide cap number")
        void recognizedCountIsPerSlot() {
            CharacterDirectoryService service = service();
            service.accept(fixture.record(UUID.randomUUID(), 1, 1, fixture.signing.getPrivate()), NOW);
            service.accept(fixture.record(UUID.randomUUID(), 2, 1, fixture.signing.getPrivate()), NOW);
            service.accept(fixture.record(UUID.randomUUID(), 3, 1, fixture.signing.getPrivate()), NOW);
            // A re-announcement of slot 2 must not inflate the count.
            service.accept(fixture.record(UUID.randomUUID(), 2, 2, fixture.signing.getPrivate()), NOW);

            assertThat(service.recognizedCharacterCount(CharacterHomeFixture.ACCOUNT_DID))
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("an account with no bindings resolves to empty and counts zero")
        void emptyAccount() {
            CharacterDirectoryService service = service();
            assertThat(service.resolveHomes("did:plc:nobody000000000000")).isEmpty();
            assertThat(service.recognizedCharacterCount("did:plc:nobody000000000000"))
                    .isZero();
        }
    }
}
