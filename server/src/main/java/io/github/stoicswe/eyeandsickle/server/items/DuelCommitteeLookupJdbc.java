package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.DuelCommitteeLookup;
import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The server-side {@link DuelCommitteeLookup}: supplies a duel's frozen sampling record from the {@code
 * duels} table so a {@code duel_grant} can be verified.
 *
 * <p>A duel outcome's {@code issuerDid} is {@code duel:<duelId>} ({@code
 * docs/architecture/04-item-provenance.md} §3.1); the verifier pulls the {@code duelId} out and asks
 * here who was sampled and what each vote weighed. That evidence is the {@code sampled_validators}
 * column — a snapshot of weights frozen at sampling time ({@code docs/architecture/05} §2), stored as
 * jsonb precisely because a foreign key into {@code validators} would resolve to <em>today's</em>
 * reputation and silently re-adjudicate an old duel with new numbers.
 *
 * <p>Returning {@code null} for an unknown duel is the contract, and it is a rejection, not a pass:
 * without the committee there is no way to tell a real quorum from a handful of freshly generated keys,
 * so an unknown duel is exactly as unrecognizable as a forged one.
 *
 * <p>This lookup does I/O — it is the caller-supplied edge that keeps the verifier itself pure. It is
 * consulted only for {@code duel_grant} records; a chain with none never touches the database here.
 */
public final class DuelCommitteeLookupJdbc implements DuelCommitteeLookup {

    private static final String DID = "did";
    private static final String WEIGHT = "weight";
    private static final String REPUTATION = "reputation";
    private static final String UPTIME = "uptime";

    private final JdbcClient jdbcClient;

    /**
     * @param jdbcClient the database this server owns
     */
    public DuelCommitteeLookupJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public QuorumCommittee committeeFor(String duelId) {
        if (duelId == null || duelId.isBlank()) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(duelId);
        } catch (IllegalArgumentException e) {
            // A duelId that is not a UUID names no row in this server's duels table. That is "unknown
            // committee", not a malformed-input error to throw over — a peer may legitimately reference
            // a duel adjudicated on a server we do not hold records for.
            return null;
        }

        // Only the sampling snapshot is needed to check §7 steps 1-2; never SELECT *.
        return jdbcClient
                .sql("SELECT sampled_validators FROM duels WHERE duel_id = :duelId")
                .param("duelId", id)
                .query((rs, rowNum) -> parseCommittee(duelId, rs.getString("sampled_validators")))
                .optional()
                .orElse(null);
    }

    /**
     * Parses a {@code sampled_validators} jsonb array into a {@link QuorumCommittee}. Package-private
     * and static so the weight extraction is unit-testable without a database.
     *
     * <p>Each element is a {@code {did, reputation, uptime, weight}} object ({@code
     * docs/architecture/05} §2). The sampling {@code weight} is used directly when present; if a record
     * predates the weight being materialised, it falls back to {@code reputation × uptime}, which is the
     * definition of that weight ({@code 05} §2.2). {@link QuorumCommittee} validates that every weight
     * is finite and non-negative.
     *
     * @param duelId the duel identifier, for the committee and for error messages
     * @param sampledValidatorsJson the jsonb array as stored
     * @return the committee
     * @throws IllegalArgumentException if the stored snapshot is not a usable array of validator entries
     */
    static QuorumCommittee parseCommittee(String duelId, String sampledValidatorsJson) {
        List<Object> elements = Jsonb.readArray(sampledValidatorsJson);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException(
                    "Duel '" + duelId + "' has an empty sampled_validators snapshot; a sampled committee has at "
                            + "least one validator");
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Object element : elements) {
            if (!(element instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException(
                        "Duel '" + duelId + "' sampled_validators entry is not an object: " + element);
            }
            Object didValue = raw.get(DID);
            if (!(didValue instanceof String validatorDid) || validatorDid.isBlank()) {
                throw new IllegalArgumentException("Duel '" + duelId + "' sampled_validators entry is missing a 'did'");
            }
            double weight = weightOf(duelId, validatorDid, raw);
            if (weights.putIfAbsent(validatorDid, weight) != null) {
                throw new IllegalArgumentException("Duel '" + duelId + "' samples '" + validatorDid + "' twice");
            }
        }
        return new QuorumCommittee(duelId, weights);
    }

    private static double weightOf(String duelId, String validatorDid, Map<?, ?> entry) {
        Object weight = entry.get(WEIGHT);
        if (weight instanceof Number number) {
            return number.doubleValue();
        }
        Object reputation = entry.get(REPUTATION);
        Object uptime = entry.get(UPTIME);
        if (reputation instanceof Number rep && uptime instanceof Number up) {
            // The sampling weight's own definition (05 §2.2), reconstructed for a snapshot written
            // before the weight was materialised.
            return rep.doubleValue() * up.doubleValue();
        }
        throw new IllegalArgumentException("Duel '" + duelId + "' validator '" + validatorDid
                + "' has no 'weight' and no 'reputation'/'uptime' to derive it from");
    }
}
