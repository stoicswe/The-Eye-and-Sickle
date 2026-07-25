package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Everything {@link ProvenanceChainVerifier} needs from the outside world, gathered before the walk
 * starts.
 *
 * <p>The verifier resolves no keys, fetches no sampling records and reads no clock. That is what
 * makes it runnable client-side and offline ({@code docs/architecture/04-item-provenance.md} §6.2),
 * and it is also what makes it testable: a chain either verifies against a given set of inputs or it
 * does not, with no hidden third variable.
 *
 * <h2>Why {@code now} is a parameter</h2>
 *
 * The provenance package is forbidden from reading a clock ({@code ArchitectureRulesTest}), because
 * ambient time would make the same verification give different answers on two machines. Passing the
 * instant in also means a server can re-run an old dispute exactly as it was decided, rather than as
 * it looks today.
 *
 * @param signingKeys resolves each signature's {@code kid} to a public key
 * @param issuerAuthority decides which DID may issue which single-issuer event
 * @param duelCommittees supplies the sampling record for each {@code duel_grant}; use {@link
 *     DuelCommitteeLookup#none()} for an item that has never been fought over
 * @param now the instant to judge timestamps against
 * @param maxFutureSkew how far ahead of {@code now} a record's timestamp may sit before it is
 *     implausible. Clocks genuinely drift between self-hosted servers, so this cannot be zero; it is
 *     the caller's operational judgement, not a game balance value, and it belongs to whoever runs
 *     the federation
 */
public record ChainVerificationContext(
        SigningKeyDirectory signingKeys,
        IssuerAuthority issuerAuthority,
        DuelCommitteeLookup duelCommittees,
        Instant now,
        Duration maxFutureSkew) {

    public ChainVerificationContext {
        Objects.requireNonNull(signingKeys, "signingKeys");
        Objects.requireNonNull(issuerAuthority, "issuerAuthority");
        Objects.requireNonNull(duelCommittees, "duelCommittees");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(maxFutureSkew, "maxFutureSkew");
        if (maxFutureSkew.isNegative()) {
            throw new IllegalArgumentException("maxFutureSkew must not be negative, was " + maxFutureSkew);
        }
    }

    /**
     * Context for a chain with no duel outcomes in it.
     *
     * @param signingKeys resolves each signature's {@code kid} to a public key
     * @param issuerAuthority decides which DID may issue which event
     * @param now the instant to judge timestamps against
     * @param maxFutureSkew tolerated clock drift
     * @return the context; a {@code duel_grant} encountered anyway will be reported as an unknown
     *     committee rather than waved through
     */
    public static ChainVerificationContext withoutDuels(
            SigningKeyDirectory signingKeys, IssuerAuthority issuerAuthority, Instant now, Duration maxFutureSkew) {
        return new ChainVerificationContext(
                signingKeys, issuerAuthority, DuelCommitteeLookup.none(), now, maxFutureSkew);
    }

    /** The newest timestamp a record may carry without being treated as implausibly future-dated. */
    public Instant latestAcceptableTimestamp() {
        return now.plus(maxFutureSkew);
    }
}
