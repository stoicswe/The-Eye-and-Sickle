package io.github.stoicswe.eyeandsickle.server.items;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainVerdict;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainVerificationContext;
import io.github.stoicswe.eyeandsickle.protocol.provenance.DuelCommitteeLookup;
import io.github.stoicswe.eyeandsickle.protocol.provenance.IssuerAuthority;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceChainVerifier;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Runs the protocol's {@link ProvenanceChainVerifier} with this server's key resolution, issuer
 * authority, and duel-committee lookup.
 *
 * <p>The verifier itself is pure and does no I/O; this service is the thing that assembles the {@link
 * ChainVerificationContext} — the {@link SigningKeyDirectory}, {@link IssuerAuthority} and {@link
 * DuelCommitteeLookup} the verifier calls back into, plus the clock instant and skew to judge
 * timestamps against. The same byte-identical verifier runs here and on a player's client ({@code
 * docs/architecture/04-item-provenance.md} §6.2); only the inputs differ.
 *
 * <h2>The verdict names what it was verified against</h2>
 *
 * A verdict is not timeless. It was reached against a particular instant and skew tolerance, and a
 * record that is "future-dated beyond tolerance" today may be fine in a minute. So {@link #verify} does
 * not return a bare verdict — it returns a {@link Result} that also carries the instant and skew, so a
 * caller that stores or forwards the conclusion stores what it was conditioned on rather than a boolean
 * that has quietly expired.
 */
public class ProvenanceVerificationService {

    private final Clock clock;
    private final Duration maxFutureSkew;
    private final SigningKeyDirectory signingKeys;
    private final IssuerAuthority issuerAuthority;
    private final DuelCommitteeLookup duelCommittees;

    /**
     * @param clock the instant timestamps are judged against
     * @param properties supplies the tolerated clock skew
     * @param signingKeys resolves each signature's {@code kid}
     * @param issuerAuthority decides which DID may issue a single-issuer event
     * @param duelCommittees supplies the sampling record for a {@code duel_grant}
     */
    public ProvenanceVerificationService(
            Clock clock,
            ItemsProperties properties,
            SigningKeyDirectory signingKeys,
            IssuerAuthority issuerAuthority,
            DuelCommitteeLookup duelCommittees) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxFutureSkew = Objects.requireNonNull(properties, "properties").maxFutureSkew();
        this.signingKeys = Objects.requireNonNull(signingKeys, "signingKeys");
        this.issuerAuthority = Objects.requireNonNull(issuerAuthority, "issuerAuthority");
        this.duelCommittees = Objects.requireNonNull(duelCommittees, "duelCommittees");
    }

    /**
     * Verifies a chain, genesis-first.
     *
     * @param chain the item's records in ascending depth order
     * @return the verdict together with the instant and skew it was reached against
     */
    public Result verify(List<ProvenanceEnvelope> chain) {
        Objects.requireNonNull(chain, "chain");
        Instant verifiedAt = clock.instant();
        ChainVerificationContext context =
                new ChainVerificationContext(signingKeys, issuerAuthority, duelCommittees, verifiedAt, maxFutureSkew);
        ChainVerdict verdict = ProvenanceChainVerifier.verify(chain, context);
        return new Result(verdict, verifiedAt, maxFutureSkew);
    }

    /**
     * A verdict and the basis it was reached on.
     *
     * @param verdict recognized, or the list of exactly what failed
     * @param verifiedAt the instant timestamps were judged against
     * @param maxFutureSkew the clock-skew tolerance in force
     */
    public record Result(ChainVerdict verdict, Instant verifiedAt, Duration maxFutureSkew) {

        public Result {
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(verifiedAt, "verifiedAt");
            Objects.requireNonNull(maxFutureSkew, "maxFutureSkew");
        }

        /** Whether the chain passed every check in {@code 04} §7. */
        public boolean recognized() {
            return verdict.isRecognized();
        }
    }
}
