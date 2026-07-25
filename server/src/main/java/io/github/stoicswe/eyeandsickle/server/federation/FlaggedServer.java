package io.github.stoicswe.eyeandsickle.server.federation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A federation-wide non-recognition flag — one row of {@code flagged_servers} ({@code
 * docs/architecture/03-server-and-federation.md} §4).
 *
 * <p>The negative half of the anti-cheat model: a server caught minting fraudulent items or running
 * dishonest validators has its items refused by honest servers across the federation. There is no
 * authority that bans it — there isn't one (Invariant I15) — it simply gets ignored, which makes its
 * fraudulent items worthless outside its own walls.
 *
 * <p>A flag is cleared, never deleted: {@code clearedAt} is nullable so an un-flagging is auditable.
 * "Why did we stop recognising that server, and why did we start again" is a question a federation
 * will ask, and a deleted row cannot answer it.
 *
 * @param flagId the flag's id
 * @param serverDid the non-recognised server's DID
 * @param reason why it was flagged; free text, because the flagging mechanism beyond provable
 *     equivocation is {@code [PROPOSAL]} (§4)
 * @param evidenceJson the proof, verbatim, where one exists — for equivocation, the two conflicting
 *     signed outcomes, so any peer can re-verify the flag rather than trust whoever raised it
 * @param raisedByDid the server that raised the flag, or {@code null} if raised locally/automatically
 * @param flaggedAt when it was raised
 * @param clearedAt when it was cleared, or {@code null} while active
 * @param clearedNote why it was cleared, or {@code null} while active
 */
public record FlaggedServer(
        UUID flagId,
        String serverDid,
        String reason,
        String evidenceJson,
        String raisedByDid,
        Instant flaggedAt,
        Instant clearedAt,
        String clearedNote) {

    /** The reason string an automatic equivocation flag carries (§3.3 → §4). */
    public static final String REASON_EQUIVOCATION = "validator_equivocation";

    public FlaggedServer {
        Objects.requireNonNull(flagId, "flagId");
        Objects.requireNonNull(serverDid, "serverDid");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(evidenceJson, "evidenceJson");
        Objects.requireNonNull(flaggedAt, "flaggedAt");
    }

    /** Whether this flag is still in force (uncleared). */
    public boolean isActive() {
        return clearedAt == null;
    }
}
