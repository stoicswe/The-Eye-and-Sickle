package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import io.github.stoicswe.eyeandsickle.server.persistence.Mutations;
import io.github.stoicswe.eyeandsickle.server.persistence.Timestamps;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the {@code validators} registry — the opted-in servers eligible for quorum sampling
 * ({@code docs/architecture/05-validator-quorum.md} §2).
 *
 * <p>Data access is {@code JdbcClient} over hand-written SQL mapping to the {@link Validator} record —
 * no JPA, no {@code SELECT *} (open question A-4, resolved). The rules live in the pure classes
 * ({@code ReputationRules}, {@code AResSampler}); this class only reads and writes rows and enforces
 * the concurrency discipline the schema's {@code row_version} column is there for.
 */
@Repository
public class ValidatorRegistry {

    private final JdbcClient jdbcClient;

    ValidatorRegistry(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Enrolls a new validator at the cold-start floor (§2.5).
     *
     * <p>The floor is applied <em>here</em>, at enrollment, not clamped in at sampling time: a fresh
     * validator's reputation genuinely starts at {@code newcomerReputation} so its sampling weight is
     * positive from the first duel, and it decays or grows from there like any other. Starting it at
     * zero is the cold-start deadlock §2.5 describes — never sampled, so never able to earn a record.
     * Uptime starts at 1 (the schema default): a validator that just opted in is assumed available
     * until a no-show proves otherwise.
     *
     * @param validatorDid the server's DID; the database enforces DID shape and uniqueness
     * @param newcomerReputation the cold-start floor from {@code QuorumProperties}, in {@code (0, 1]}
     * @param now enrollment time
     * @return the enrolled validator
     * @throws org.springframework.dao.DuplicateKeyException if this DID is already enrolled
     */
    public Validator enroll(String validatorDid, double newcomerReputation, Instant now) {
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO validators (validator_did, validator_reputation, uptime, is_new,
                                                enrolled_at, votes_correct, votes_divergent, no_shows, row_version)
                        VALUES (:did, :reputation, 1, true, :now, 0, 0, 0, 0)
                        """)
                .param("did", validatorDid)
                .param("reputation", reputation(newcomerReputation))
                .param("now", Timestamps.at(now))
                .update();
        Mutations.requireInserted(inserted, "validators");
        return find(validatorDid).orElseThrow();
    }

    /**
     * @param validatorDid the server's DID
     * @return the validator, or empty if not enrolled
     */
    public Optional<Validator> find(String validatorDid) {
        return jdbcClient
                .sql("SELECT " + ValidatorRows.COLUMNS + " FROM validators WHERE validator_did = :did")
                .param("did", validatorDid)
                .query(ValidatorRows.MAPPER)
                .optional();
    }

    /** @return every enrolled validator, newest-reputation first, for a status listing */
    public List<Validator> findAll() {
        return jdbcClient
                .sql("SELECT " + ValidatorRows.COLUMNS
                        + " FROM validators ORDER BY validator_reputation DESC, validator_did")
                .query(ValidatorRows.MAPPER)
                .list();
    }

    /**
     * The candidates a duel samples from: every validator with a positive sampling weight (§2.2).
     *
     * <p>Filtered to {@code reputation > 0 AND uptime > 0} in SQL, because a zero in either factor is a
     * zero weight, which {@code AResSampler} cannot draw. The newcomer floor is what keeps a fresh
     * validator on this list; a validator decayed to zero has earned its way off it and must climb back
     * only if it is somehow sampled again — which is why the floor, not a sampling-time clamp, is the
     * anti-deadlock mechanism.
     *
     * @return the eligible candidates with their frozen weight factors
     */
    public List<SampledValidator> eligibleCandidates() {
        return jdbcClient
                .sql("SELECT " + ValidatorRows.VALIDATOR_DID + ", " + ValidatorRows.VALIDATOR_REPUTATION + ", "
                        + ValidatorRows.UPTIME + " FROM validators"
                        + " WHERE " + ValidatorRows.VALIDATOR_REPUTATION + " > 0 AND " + ValidatorRows.UPTIME + " > 0")
                .query((rs, n) -> SampledValidator.of(
                        rs.getString(ValidatorRows.VALIDATOR_DID),
                        rs.getBigDecimal(ValidatorRows.VALIDATOR_REPUTATION).doubleValue(),
                        rs.getBigDecimal(ValidatorRows.UPTIME).doubleValue()))
                .list();
    }

    /**
     * Reads a validator and takes a row lock on it, for a read-modify-write reputation update.
     *
     * <p>{@code SELECT ... FOR UPDATE} serialises this validator's conduct updates against every other
     * one: the AIMD step reads the current reputation and writes a value derived from it, and two
     * concurrent adjudications that both read the same reputation would otherwise lose one update.
     * Must be called inside a transaction, and — for a duel that touches several validators — callers
     * must lock in a consistent DID order to avoid deadlock, the same discipline {@code Mutations}
     * documents for the compute ledger.
     *
     * @param validatorDid the server's DID
     * @return the locked validator, or empty if it is not enrolled
     */
    public Optional<Validator> lockForUpdate(String validatorDid) {
        return jdbcClient
                .sql("SELECT " + ValidatorRows.COLUMNS + " FROM validators WHERE validator_did = :did FOR UPDATE")
                .param("did", validatorDid)
                .query(ValidatorRows.MAPPER)
                .optional();
    }

    /**
     * Writes the mutable columns of a validator, guarded by its {@code row_version}.
     *
     * <p>The update is conditioned on the version the record was read with, so a concurrent writer
     * that advanced it turns this into an {@link org.springframework.dao.OptimisticLockingFailureException}
     * rather than a silent lost write. Callers that took {@link #lockForUpdate(String)} hold the row,
     * so the guard is belt-and-suspenders there; callers that read without a lock rely on it.
     *
     * @param validator the desired new state, carrying the {@code rowVersion} it was read with
     * @return the same state with {@code rowVersion} advanced to what the database now holds
     */
    public Validator save(Validator validator) {
        int affected = jdbcClient
                .sql("""
                        UPDATE validators
                           SET validator_reputation = :reputation,
                               uptime = :uptime,
                               is_new = :isNew,
                               last_sampled_at = :lastSampledAt,
                               last_vote_at = :lastVoteAt,
                               votes_correct = :votesCorrect,
                               votes_divergent = :votesDivergent,
                               no_shows = :noShows,
                               row_version = row_version + 1
                         WHERE validator_did = :did
                           AND row_version = :expectedVersion
                        """)
                .param("reputation", validator.validatorReputation())
                .param("uptime", validator.uptime())
                .param("isNew", validator.isNew())
                .param("lastSampledAt", Timestamps.atOrNull(validator.lastSampledAt()))
                .param("lastVoteAt", Timestamps.atOrNull(validator.lastVoteAt()))
                .param("votesCorrect", validator.votesCorrect())
                .param("votesDivergent", validator.votesDivergent())
                .param("noShows", validator.noShows())
                .param("did", validator.validatorDid())
                .param("expectedVersion", validator.rowVersion())
                .update();
        Mutations.requireUpdated(affected, "validators", validator.validatorDid());
        return new Validator(
                validator.validatorDid(),
                validator.validatorReputation(),
                validator.uptime(),
                validator.isNew(),
                validator.enrolledAt(),
                validator.lastSampledAt(),
                validator.lastVoteAt(),
                validator.votesCorrect(),
                validator.votesDivergent(),
                validator.noShows(),
                Mutations.nextRowVersion(validator.rowVersion()));
    }

    /**
     * Stamps {@code last_sampled_at} on the validators a duel just drew.
     *
     * <p>A blind update by primary key rather than a version-checked one: it records that a draw
     * happened and touches no value any consensus decision depends on, so racing it against a
     * concurrent reputation update is harmless — the last writer's timestamp wins, and neither
     * clobbers the other's reputation. Skips silently if the DID set is empty.
     *
     * @param validatorDids the sampled validators
     * @param now the sampling time
     */
    public void markSampled(Collection<String> validatorDids, Instant now) {
        if (validatorDids.isEmpty()) {
            return;
        }
        jdbcClient
                .sql("""
                        UPDATE validators
                           SET last_sampled_at = :now,
                               row_version = row_version + 1
                         WHERE validator_did IN (:dids)
                        """)
                .param("now", Timestamps.at(now))
                .param("dids", List.copyOf(validatorDids))
                .update();
    }

    /** Reputation as the {@code numeric(9,8)} the column stores, clamped defensively into {@code [0,1]}. */
    private static BigDecimal reputation(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(clamped).setScale(8, java.math.RoundingMode.HALF_UP);
    }
}
