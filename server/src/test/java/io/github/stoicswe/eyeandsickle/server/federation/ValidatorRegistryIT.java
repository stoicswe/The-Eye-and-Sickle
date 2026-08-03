package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The {@code validators} registry against a real PostgreSQL — the round-trips, the {@code CHECK}
 * constraints biting, the {@code row_version} optimistic-lock guard, and the eligible-candidate filter
 * that the A-Res sampler relies on.
 *
 * <p>The emphasis is the rejections: on an authoritative server (Invariant I14) the database is the
 * last line of defence, so what matters is that it refuses a reputation outside {@code [0, 1]}, a
 * malformed DID, and a stale version — not merely that it stores a valid row.
 */
class ValidatorRegistryIT extends DatabaseIntegrationTestBase {

    private static final String DID = "did:plc:validator0000000000a";
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");

    private ValidatorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ValidatorRegistry(jdbcClient());
    }

    private void rawInsert(String did, double reputation, double uptime) {
        jdbcClient()
                .sql("""
                        INSERT INTO validators (validator_did, validator_reputation, uptime, is_new, enrolled_at)
                        VALUES (:did, :reputation, :uptime, true, :now)
                        """)
                .param("did", did)
                .param("reputation", BigDecimal.valueOf(reputation))
                .param("uptime", BigDecimal.valueOf(uptime))
                .param("now", Timestamps.at(NOW))
                .update();
    }

    @Nested
    @DisplayName("enrollment")
    class Enrollment {

        @Test
        @DisplayName("stores a validator at the cold-start floor with full uptime and zeroed counters")
        void enrollsAtFloor() {
            Validator enrolled = registry.enroll(DID, 0.40, NOW);

            assertThat(enrolled.validatorReputation().doubleValue()).isCloseTo(0.40, within(1e-9));
            assertThat(enrolled.uptime().doubleValue()).isCloseTo(1.0, within(1e-9));
            assertThat(enrolled.isNew()).isTrue();
            assertThat(enrolled.votesCorrect()).isZero();
            assertThat(enrolled.rowVersion()).isZero();

            assertThat(registry.find(DID)).isPresent();
        }

        @Test
        @DisplayName("rounds the stored reputation to the column's numeric(9,8) scale")
        void roundsToScale() {
            registry.enroll(DID, 0.123456789, NOW);
            // HALF_UP at 8 decimals: 0.123456789 -> 0.12345679.
            assertThat(registry.find(DID).orElseThrow().validatorReputation())
                    .isEqualByComparingTo(new BigDecimal("0.12345679"));
        }

        @Test
        @DisplayName("refuses a second enrollment of the same DID")
        void refusesDuplicate() {
            registry.enroll(DID, 0.40, NOW);
            // A server is one validator row; a duplicate would split its trust score across two rows.
            assertThatThrownBy(() -> registry.enroll(DID, 0.40, NOW)).isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        @DisplayName("refuses a malformed DID at the database boundary")
        void refusesMalformedDid() {
            assertThatThrownBy(() -> registry.enroll("not-a-did", 0.40, NOW))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("eligibleCandidates — the sampler's input")
    class Eligible {

        @Test
        @DisplayName("returns only validators with positive reputation AND positive uptime")
        void filtersZeroWeight() {
            registry.enroll(DID, 0.40, NOW); // positive on both factors
            rawInsert("did:plc:zeroreputation00000a", 0.0, 1.0); // zero weight
            rawInsert("did:plc:zerouptime0000000000a", 0.7, 0.0); // zero weight

            List<SampledValidator> candidates = registry.eligibleCandidates();

            // A zero in either factor is a zero weight, which the sampler cannot draw; such rows must
            // never appear as candidates or a validator with no standing could be handed authority.
            assertThat(candidates).extracting(SampledValidator::validatorDid).containsExactly(DID);
        }
    }

    @Nested
    @DisplayName("the optimistic-lock guard on save")
    class Save {

        @Test
        @DisplayName("persists the mutable columns and advances the row version")
        void persistsAndAdvances() {
            Validator enrolled = registry.enroll(DID, 0.40, NOW);
            Validator updated = new Validator(
                    DID,
                    new BigDecimal("0.50000000"),
                    new BigDecimal("0.90000000"),
                    false,
                    enrolled.enrolledAt(),
                    NOW,
                    NOW,
                    3,
                    1,
                    2,
                    enrolled.rowVersion());

            Validator saved = registry.save(updated);

            assertThat(saved.rowVersion()).isEqualTo(1);
            Validator reread = registry.find(DID).orElseThrow();
            assertThat(reread.validatorReputation().doubleValue()).isCloseTo(0.50, within(1e-9));
            assertThat(reread.uptime().doubleValue()).isCloseTo(0.90, within(1e-9));
            assertThat(reread.isNew()).isFalse();
            assertThat(reread.votesCorrect()).isEqualTo(3);
            assertThat(reread.votesDivergent()).isEqualTo(1);
            assertThat(reread.noShows()).isEqualTo(2);
            assertThat(reread.rowVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejects a save at a stale row version rather than losing an update")
        void rejectsStaleVersion() {
            Validator enrolled = registry.enroll(DID, 0.40, NOW); // version 0
            registry.save(enrolled); // advances the stored version to 1

            // Saving again at the version we first read (0) must fail — a concurrent writer moved it.
            assertThatThrownBy(() -> registry.save(enrolled)).isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("the database refuses a reputation outside [0, 1] even when the record does not")
        void databaseRefusesOutOfRangeReputation() {
            Validator enrolled = registry.enroll(DID, 0.40, NOW);
            Validator outOfRange = new Validator(
                    DID,
                    new BigDecimal("1.50000000"), // > 1: the Validator record does not police this; the DB must
                    new BigDecimal("1.00000000"),
                    false,
                    enrolled.enrolledAt(),
                    null,
                    null,
                    0,
                    0,
                    0,
                    enrolled.rowVersion());

            assertThatThrownBy(() -> registry.save(outOfRange)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("markSampled")
    class MarkSampled {

        @Test
        @DisplayName("stamps last_sampled_at and bumps the row version without touching reputation")
        void stampsSampled() {
            registry.enroll(DID, 0.40, NOW);

            registry.markSampled(List.of(DID), NOW);

            Validator reread = registry.find(DID).orElseThrow();
            assertThat(reread.lastSampledAt()).isEqualTo(NOW);
            assertThat(reread.rowVersion()).isEqualTo(1);
            // A blind bookkeeping stamp: the reputation the draw was weighted by is untouched.
            assertThat(reread.validatorReputation().doubleValue()).isCloseTo(0.40, within(1e-9));
        }

        @Test
        @DisplayName("is a silent no-op on an empty DID set")
        void emptyIsNoOp() {
            registry.markSampled(List.of(), NOW); // must not throw
            assertThat(registry.findAll()).isEmpty();
        }
    }

    @Test
    @DisplayName("findAll lists validators highest-reputation first")
    void findAllOrdersByReputation() {
        rawInsert("did:plc:lowvalidator0000000a", 0.30, 1.0);
        rawInsert("did:plc:highvalidator000000a", 0.90, 1.0);
        rawInsert("did:plc:midvalidator0000000a", 0.60, 1.0);

        assertThat(registry.findAll())
                .extracting(Validator::validatorDid)
                .containsExactly(
                        "did:plc:highvalidator000000a", "did:plc:midvalidator0000000a", "did:plc:lowvalidator0000000a");
    }
}
