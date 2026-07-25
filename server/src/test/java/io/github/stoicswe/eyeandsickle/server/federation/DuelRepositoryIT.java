package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledCommittee;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The {@code duels} table against a real PostgreSQL — the frozen committee round-trip that makes
 * {@code docs/architecture/04} §7 step 1 checkable, the resolved-pair and resolution-time constraints,
 * and the version-checked resolution that stops two adjudications both closing one duel.
 */
class DuelRepositoryIT extends PostgresIntegrationTestBase {

    private static final Instant OPENED = Instant.parse("2026-07-24T12:00:00Z");
    private static final Instant RESOLVED = Instant.parse("2026-07-24T12:05:00Z");
    private static final List<String> PARTICIPANTS =
            List.of("did:plc:participanta000000a", "did:plc:participantb000000a");

    private DuelRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DuelRepository(jdbcClient());
    }

    private static SampledCommittee committee(String duelId) {
        return new SampledCommittee(
                duelId,
                List.of(
                        SampledValidator.of("did:plc:validator0000000001a", 0.8, 0.5), // weight 0.40
                        SampledValidator.of("did:plc:validator0000000002a", 1.0, 1.0))); // weight 1.00
    }

    @Nested
    @DisplayName("open and read back")
    class OpenAndFind {

        @Test
        @DisplayName("freezes the committee and reconstructs it from the stored weights")
        void freezesCommittee() {
            UUID duelId = UUID.randomUUID();
            repository.open(duelId, PARTICIPANTS, committee(duelId.toString()), OPENED);

            DuelRecord duel = repository.find(duelId).orElseThrow();

            assertThat(duel.isResolved()).isFalse();
            assertThat(duel.participants()).isEqualTo(PARTICIPANTS);
            assertThat(duel.committee().size()).isEqualTo(2);
            // The weight is read back verbatim from what was frozen at sampling time — reputation×uptime
            // as of the draw, never re-derived from today's reputations (which would re-adjudicate it).
            assertThat(duel.committee().weightOf("did:plc:validator0000000001a"))
                    .isCloseTo(0.40, within(1e-9));
            assertThat(duel.committee().weightOf("did:plc:validator0000000002a"))
                    .isCloseTo(1.00, within(1e-9));
        }

        @Test
        @DisplayName("refuses a duel with fewer than two participants")
        void refusesTooFewParticipants() {
            UUID duelId = UUID.randomUUID();
            // A duel is between at least two servers; one participant is a malformed adjudication.
            assertThatThrownBy(() -> repository.open(
                            duelId, List.of("did:plc:lonelyparticipant00a"), committee(duelId.toString()), OPENED))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("resolution")
    class Resolve {

        @Test
        @DisplayName("records the outcome and closes the duel")
        void resolvesDuel() {
            UUID duelId = UUID.randomUUID();
            repository.open(duelId, PARTICIPANTS, committee(duelId.toString()), OPENED);

            repository.resolve(duelId, "{\"winner\":\"did:plc:participanta000000a\"}", "[]", RESOLVED, 0);

            DuelRecord resolved = repository.find(duelId).orElseThrow();
            assertThat(resolved.isResolved()).isTrue();
            assertThat(resolved.resolvedAt()).isEqualTo(RESOLVED);
            assertThat(resolved.outcomeJson()).contains("participanta");
            assertThat(resolved.rowVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("refuses a second resolution at the stale version, so one duel cannot resolve twice")
        void refusesConcurrentResolution() {
            UUID duelId = UUID.randomUUID();
            repository.open(duelId, PARTICIPANTS, committee(duelId.toString()), OPENED);

            repository.resolve(duelId, "{\"winner\":\"a\"}", "[]", RESOLVED, 0); // advances to version 1

            // The loser of a resolution race read version 0 too; its write must be refused, not silently
            // overwrite the first signed outcome with a second.
            assertThatThrownBy(() -> repository.resolve(duelId, "{\"winner\":\"b\"}", "[]", RESOLVED, 0))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("refuses a resolution timestamped before the duel was opened")
        void refusesResolvedBeforeOpened() {
            UUID duelId = UUID.randomUUID();
            repository.open(duelId, PARTICIPANTS, committee(duelId.toString()), OPENED);

            Instant beforeOpen = OPENED.minusSeconds(60);
            assertThatThrownBy(() -> repository.resolve(duelId, "{\"winner\":\"a\"}", "[]", beforeOpen, 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("findUnresolved returns still-open duels, oldest first")
    void findUnresolvedOldestFirst() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        repository.open(older, PARTICIPANTS, committee(older.toString()), OPENED);
        repository.open(newer, PARTICIPANTS, committee(newer.toString()), OPENED.plusSeconds(3600));
        repository.resolve(older, "{\"winner\":\"a\"}", "[]", RESOLVED, 0);

        // Only the unresolved one remains in the work queue.
        assertThat(repository.findUnresolved()).extracting(DuelRecord::duelId).containsExactly(newer);
    }

    @Test
    @DisplayName("the database refuses a half-written duel: an outcome with no resolution time")
    void refusesHalfWrittenDuel() {
        // ck_duels_resolved_pair: an outcome present while resolved_at is NULL is a half-written row.
        // This can only be reached below the repository (raw SQL), which is exactly why the constraint,
        // not the service, is the guarantee.
        assertThatThrownBy(() -> jdbcClient()
                        .sql("""
                                INSERT INTO duels (duel_id, participants, sampled_validators, committee_size, outcome)
                                VALUES (:duelId, :participants::jsonb, :sampled::jsonb, 1, :outcome::jsonb)
                                """)
                        .param("duelId", UUID.randomUUID())
                        .param("participants", "[\"did:plc:pa000000000000000000a\",\"did:plc:pb000000000000000000a\"]")
                        .param("sampled", "[{\"did\":\"did:plc:v1\",\"weight\":1.0}]")
                        .param("outcome", "{\"winner\":\"a\"}")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
