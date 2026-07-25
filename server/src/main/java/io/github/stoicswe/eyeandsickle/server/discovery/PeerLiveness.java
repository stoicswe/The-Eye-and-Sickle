package io.github.stoicswe.eyeandsickle.server.discovery;

import java.time.Instant;
import java.util.Objects;

/**
 * A peer's measured reachability, the raw availability signal the validator quorum's sampling weight
 * needs ({@code docs/architecture/05-validator-quorum.md} §2.2, where {@code weight = reputation ×
 * uptime}).
 *
 * <h2>Measurement here, policy there</h2>
 *
 * This is deliberately just the measurement — how often this server has reached the peer, and how
 * recently. It is <strong>not</strong> the {@code validators.uptime} score itself. The federation slice
 * owns that column and decides how to fold this measurement into it, including the separate
 * no-show decay ({@code 05} §4, γ) that keeps availability distinct from correctness. Handing the
 * federation slice a finished uptime number would smuggle a policy decision across a slice boundary;
 * handing it this measurement keeps the decision where it belongs.
 *
 * @param peerDid the peer this describes
 * @param successRatio successes / (successes + failures), or a caller-chosen default when there is no
 *     contact history yet
 * @param successes count of successful contacts
 * @param failures count of failed contacts
 * @param consecutiveFailures failures since the last success
 * @param lastSuccessfulContactAt last time the peer was reached, or {@code null} if never
 */
public record PeerLiveness(
        String peerDid,
        double successRatio,
        long successes,
        long failures,
        int consecutiveFailures,
        Instant lastSuccessfulContactAt) {

    public PeerLiveness {
        Objects.requireNonNull(peerDid, "peerDid");
    }

    /**
     * Projects a stored peer row to its liveness measurement.
     *
     * @param record the stored peer
     * @param ratioWhenNoData the value to report when no contact has happened yet
     * @return the measurement
     */
    public static PeerLiveness of(PeerRecord record, double ratioWhenNoData) {
        return new PeerLiveness(
                record.peerDid(),
                record.successRatio(ratioWhenNoData),
                record.contactSuccesses(),
                record.contactFailures(),
                record.consecutiveFailures(),
                record.lastSuccessfulContactAt());
    }
}
