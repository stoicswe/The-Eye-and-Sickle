package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.server.persistence.RowMappers;
import org.springframework.jdbc.core.RowMapper;

/** Column names and the row mapper for the {@code flagged_servers} table. */
final class FlaggedServerRows {

    static final String FLAG_ID = "flag_id";
    static final String SERVER_DID = "server_did";
    static final String REASON = "reason";
    static final String EVIDENCE = "evidence";
    static final String RAISED_BY_DID = "raised_by_did";
    static final String FLAGGED_AT = "flagged_at";
    static final String CLEARED_AT = "cleared_at";
    static final String CLEARED_NOTE = "cleared_note";

    static final String COLUMNS = String.join(
            ", ", FLAG_ID, SERVER_DID, REASON, EVIDENCE, RAISED_BY_DID, FLAGGED_AT, CLEARED_AT, CLEARED_NOTE);

    static final RowMapper<FlaggedServer> MAPPER = RowMappers.of(
            FlaggedServer.class,
            row -> new FlaggedServer(
                    row.uuid(FLAG_ID),
                    row.text(SERVER_DID),
                    row.text(REASON),
                    // The evidence document is read verbatim, not re-parsed: it holds signatures a peer
                    // will re-verify byte-for-byte, so round-tripping it through a parser could change a
                    // byte and break the very proof the flag exists to carry.
                    row.json(EVIDENCE),
                    row.textOrNull(RAISED_BY_DID),
                    row.instant(FLAGGED_AT),
                    row.instantOrNull(CLEARED_AT),
                    row.textOrNull(CLEARED_NOTE)));

    private FlaggedServerRows() {}
}
