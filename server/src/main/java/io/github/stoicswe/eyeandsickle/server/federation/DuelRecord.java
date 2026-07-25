package io.github.stoicswe.eyeandsickle.server.federation;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A cross-server adjudication and its frozen sampling record — one row of {@code duels} ({@code
 * docs/architecture/05-validator-quorum.md} §2, §5).
 *
 * <p>The sampling record is not decoration: {@code docs/architecture/04-item-provenance.md} §7 step 1
 * requires a verifier to confirm each signature "resolves to a validator that was actually sampled for
 * that duel", and this row is where that record lives. {@link #committee()} rebuilds the protocol
 * {@link QuorumCommittee} from the stored, frozen weights — the value the BFT threshold is judged
 * against, computed from the reputations that existed <em>at sampling time</em>, never re-derived from
 * today's. That is why the weights are persisted rather than looked up live: a reputation that moved
 * after the duel must not silently re-adjudicate it.
 *
 * @param duelId the adjudication's id; the provenance {@code issuerDid} is {@code duel:<duelId>}
 * @param participants the DIDs of the servers whose players fought (at least two)
 * @param committee the frozen committee — DID to sampling weight — rebuilt from {@code
 *     sampled_validators}
 * @param outcomeJson the agreed outcome document, or {@code null} while unresolved
 * @param signaturesJson the validator signature blocks over the outcome, as stored (an empty array
 *     while unresolved)
 * @param openedAt when the duel was opened and its committee sampled
 * @param resolvedAt when quorum was reached, or {@code null} while unresolved (present iff {@code
 *     outcomeJson} is)
 * @param rowVersion optimistic-concurrency version
 */
public record DuelRecord(
        UUID duelId,
        List<String> participants,
        QuorumCommittee committee,
        String outcomeJson,
        String signaturesJson,
        Instant openedAt,
        Instant resolvedAt,
        long rowVersion) {

    public DuelRecord {
        Objects.requireNonNull(duelId, "duelId");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        Objects.requireNonNull(committee, "committee");
        Objects.requireNonNull(signaturesJson, "signaturesJson");
        Objects.requireNonNull(openedAt, "openedAt");
    }

    /** Whether this duel has reached quorum and carries an agreed outcome. */
    public boolean isResolved() {
        return resolvedAt != null;
    }
}
