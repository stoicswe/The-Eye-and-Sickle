package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/**
 * Column names and the row mapper for the {@code validators} table — the house pattern from {@code
 * RowMappers}: one place per table, so a column rename is one edit and no query says {@code SELECT *}.
 */
final class ValidatorRows {

    static final String VALIDATOR_DID = "validator_did";
    static final String VALIDATOR_REPUTATION = "validator_reputation";
    static final String UPTIME = "uptime";
    static final String IS_NEW = "is_new";
    static final String ENROLLED_AT = "enrolled_at";
    static final String LAST_SAMPLED_AT = "last_sampled_at";
    static final String LAST_VOTE_AT = "last_vote_at";
    static final String VOTES_CORRECT = "votes_correct";
    static final String VOTES_DIVERGENT = "votes_divergent";
    static final String NO_SHOWS = "no_shows";
    static final String ROW_VERSION = "row_version";

    /** Every column, in declaration order, for an explicit SELECT list. */
    static final String COLUMNS = String.join(
            ", ",
            VALIDATOR_DID,
            VALIDATOR_REPUTATION,
            UPTIME,
            IS_NEW,
            ENROLLED_AT,
            LAST_SAMPLED_AT,
            LAST_VOTE_AT,
            VOTES_CORRECT,
            VOTES_DIVERGENT,
            NO_SHOWS,
            ROW_VERSION);

    static final RowMapper<Validator> MAPPER = RowMappers.of(
            Validator.class,
            row -> new Validator(
                    row.text(VALIDATOR_DID),
                    row.decimal(VALIDATOR_REPUTATION),
                    row.decimal(UPTIME),
                    row.bool(IS_NEW),
                    row.instant(ENROLLED_AT),
                    row.instantOrNull(LAST_SAMPLED_AT),
                    row.instantOrNull(LAST_VOTE_AT),
                    row.int64(VOTES_CORRECT),
                    row.int64(VOTES_DIVERGENT),
                    row.int64(NO_SHOWS),
                    row.int64(ROW_VERSION)));

    private ValidatorRows() {}
}
