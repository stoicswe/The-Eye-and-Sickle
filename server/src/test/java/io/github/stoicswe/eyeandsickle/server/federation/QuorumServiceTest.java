package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledCommittee;
import io.github.stoicswe.eyeandsickle.server.federation.sampling.SampledValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The full validator-quorum loop wired over persistence — {@code
 * docs/architecture/05-validator-quorum.md} §5 — tested with NO database.
 *
 * <p>The service only sequences the pure pieces and commits their effects; the fakes below stand in
 * for the three repositories, letting the whole loop be exercised deterministically with a seeded
 * randomness source and a fixed clock. What is asserted here is the <em>wiring</em>: that the right
 * conduct reaches the right validator, that a no-show decays uptime and not reputation, that an
 * equivocator is both slashed and flagged, and that a duel that never reaches quorum leaves its
 * unclassified voters untouched. The arithmetic itself is proven in {@link
 * io.github.stoicswe.eyeandsickle.server.federation.reputation.ReputationRulesTest}.
 */
class QuorumServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-07-24T09:00:00Z");
    private static final String A = FederationFixture.HOLDER_A;
    private static final String B = FederationFixture.HOLDER_B;

    private final FederationFixture fx = new FederationFixture();
    private final SigningKeyDirectory keys = fx.directory();

    private FakeValidatorRegistry validators;
    private FakeDuelRepository duels;
    private FakeFlaggedServerRegistry flags;
    private QuorumService service;

    @BeforeEach
    void setUp() {
        validators = new FakeValidatorRegistry();
        duels = new FakeDuelRepository();
        flags = new FakeFlaggedServerRegistry();
        service = new QuorumService(
                validators,
                duels,
                flags,
                new QuorumProperties(7, 0.05, 0.25, 0.10, 0.10, 0.40),
                new Random(1234L),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Seeds seven validators at the given reputation with full uptime, DIDs validator1..7. */
    private void seedSevenValidators(double reputation) {
        for (int i = 1; i <= 7; i++) {
            validators.put(new Validator(
                    "did:plc:validator" + i, scaled(reputation), scaled(1.0), true, EARLIER, null, null, 0, 0, 0, 0));
        }
    }

    private ValidatorSignature vote(UUID duelId, String winner, int validatorIndex) {
        return fx.vote(duelId.toString(), winner, "did:plc:validator" + validatorIndex);
    }

    private static BigDecimal scaled(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }

    // ================================================================= enrollment

    @Test
    @DisplayName("enrollment starts a validator at the cold-start floor")
    void enrollsAtFloor() {
        Validator enrolled = service.enrollValidator("did:plc:newcomer0000000000000");

        assertThat(enrolled.validatorReputation().doubleValue()).isCloseTo(0.40, within(1e-9));
        assertThat(enrolled.uptime().doubleValue()).isCloseTo(1.0, within(1e-9));
        assertThat(enrolled.isNew()).isTrue();
    }

    // ================================================================= openDuel

    @Nested
    @DisplayName("openDuel")
    class OpenDuel {

        @Test
        @DisplayName("samples a committee, freezes it on the duel, and stamps the drawn validators")
        void samplesAndFreezes() {
            seedSevenValidators(0.5);
            UUID duelId = UUID.randomUUID();

            SampledCommittee committee = service.openDuel(duelId, List.of("did:plc:pa", "did:plc:pb"));

            assertThat(committee.size()).isEqualTo(7);
            assertThat(duels.find(duelId)).isPresent();
            assertThat(duels.find(duelId).orElseThrow().isResolved()).isFalse();
            // Every drawn validator was stamped sampled — the committee and the mark are one action.
            assertThat(validators.lastMarkedSampled).hasSize(7);
        }

        @Test
        @DisplayName("refuses to open a duel when no validator has positive sampling weight")
        void refusesWithNoEligibleValidators() {
            // A registry with only zero-weight validators cannot yield a committee; adjudicating a
            // cross-server outcome without one would violate I15.
            validators.put(new Validator(
                    "did:plc:validator1", scaled(0.0), scaled(1.0), true, EARLIER, null, null, 0, 0, 0, 0));
            UUID duelId = UUID.randomUUID();

            assertThatThrownBy(() -> service.openDuel(duelId, List.of("did:plc:pa", "did:plc:pb")))
                    .isInstanceOf(IllegalStateException.class);
            // Nothing was written — the duel was not half-opened.
            assertThat(duels.find(duelId)).isEmpty();
        }
    }

    // ================================================================= adjudicate

    @Nested
    @DisplayName("adjudicate")
    class Adjudicate {

        @Test
        @DisplayName("throws when the duel was never opened on this server")
        void unknownDuel() {
            assertThatThrownBy(() -> service.adjudicate(UUID.randomUUID(), List.of(), keys))
                    .isInstanceOf(DuelNotFoundException.class);
        }

        @Test
        @DisplayName("refuses to re-resolve an already-resolved duel")
        void alreadyResolved() {
            UUID duelId = UUID.randomUUID();
            QuorumCommittee committee = FederationFixture.weightedCommittee(duelId.toString(), 1, 1, 1);
            duels.putResolved(duelId, committee);

            // Re-resolving would overwrite a signed outcome.
            assertThatThrownBy(() -> service.adjudicate(duelId, List.of(), keys))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("resolves on quorum and moves each validator by its own conduct")
        void resolvesAndUpdatesReputations() {
            seedSevenValidators(0.5);
            UUID duelId = UUID.randomUUID();
            service.openDuel(duelId, List.of("did:plc:pa", "did:plc:pb"));

            List<ValidatorSignature> votes = List.of(
                    vote(duelId, A, 1),
                    vote(duelId, A, 2),
                    vote(duelId, A, 3),
                    vote(duelId, A, 4),
                    vote(duelId, A, 5),
                    vote(duelId, B, 6)); // validator6 diverges, validator7 is silent

            AdjudicationResult result = service.adjudicate(duelId, votes, keys);

            assertThat(result.resolved()).isTrue();
            assertThat(duels.resolvedIds).contains(duelId);

            // CORRECT: reputation rose by alpha·(1−r) = 0.525, a correct vote was counted, no longer new.
            Validator correct = validators.get("did:plc:validator1");
            assertThat(correct.validatorReputation().doubleValue()).isCloseTo(0.525, within(1e-9));
            assertThat(correct.votesCorrect()).isEqualTo(1);
            assertThat(correct.uptime().doubleValue()).isCloseTo(1.0, within(1e-9));
            assertThat(correct.isNew()).isFalse();
            assertThat(correct.lastVoteAt()).isEqualTo(NOW);

            // DIVERGENT: reputation cut to 0.375, uptime untouched.
            Validator divergent = validators.get("did:plc:validator6");
            assertThat(divergent.validatorReputation().doubleValue()).isCloseTo(0.375, within(1e-9));
            assertThat(divergent.votesDivergent()).isEqualTo(1);
            assertThat(divergent.uptime().doubleValue()).isCloseTo(1.0, within(1e-9));

            // NO_SHOW: uptime decayed to 0.9, reputation UNTOUCHED, last_vote_at not advanced.
            Validator noShow = validators.get("did:plc:validator7");
            assertThat(noShow.uptime().doubleValue()).isCloseTo(0.9, within(1e-9));
            assertThat(noShow.validatorReputation().doubleValue()).isCloseTo(0.5, within(1e-9));
            assertThat(noShow.noShows()).isEqualTo(1);
            assertThat(noShow.lastVoteAt()).isNull();
        }

        @Test
        @DisplayName("slashes and flags an equivocator while still resolving on the honest majority")
        void equivocatorSlashedAndFlagged() {
            seedSevenValidators(0.5);
            UUID duelId = UUID.randomUUID();
            service.openDuel(duelId, List.of("did:plc:pa", "did:plc:pb"));

            List<ValidatorSignature> votes = List.of(
                    vote(duelId, A, 1),
                    vote(duelId, A, 2),
                    vote(duelId, A, 3),
                    vote(duelId, A, 4),
                    vote(duelId, A, 5),
                    vote(duelId, A, 6), // validator6 signs A ...
                    vote(duelId, B, 6)); // ... and B

            AdjudicationResult result = service.adjudicate(duelId, votes, keys);

            assertThat(result.resolved()).isTrue();
            // The hard slash: reputation dropped to the equivocation floor.
            assertThat(validators
                            .get("did:plc:validator6")
                            .validatorReputation()
                            .doubleValue())
                    .isCloseTo(0.10, within(1e-9));
            // And the federation-wide flag, raised automatically with the equivocation reason.
            assertThat(flags.raised).anySatisfy(f -> {
                assertThat(f.serverDid()).isEqualTo("did:plc:validator6");
                assertThat(f.reason()).isEqualTo(FlaggedServer.REASON_EQUIVOCATION);
            });
        }

        @Test
        @DisplayName("does not resolve below quorum and leaves unclassified voters untouched")
        void noQuorumLeavesVotersUntouched() {
            seedSevenValidators(0.5);
            UUID duelId = UUID.randomUUID();
            service.openDuel(duelId, List.of("did:plc:pa", "did:plc:pb"));

            // Only four of seven agree: no quorum.
            List<ValidatorSignature> votes =
                    List.of(vote(duelId, A, 1), vote(duelId, A, 2), vote(duelId, A, 3), vote(duelId, A, 4));

            AdjudicationResult result = service.adjudicate(duelId, votes, keys);

            assertThat(result.resolved()).isFalse();
            assertThat(duels.resolvedIds).doesNotContain(duelId);

            // A voter with no quorum to judge it against is deliberately not persisted — still pristine.
            Validator voter = validators.get("did:plc:validator1");
            assertThat(voter.validatorReputation().doubleValue()).isCloseTo(0.5, within(1e-9));
            assertThat(voter.votesCorrect()).isZero();
            assertThat(voter.isNew()).isTrue();

            // A silent validator is still a no-show regardless of the non-result.
            Validator silent = validators.get("did:plc:validator7");
            assertThat(silent.uptime().doubleValue()).isCloseTo(0.9, within(1e-9));
            assertThat(silent.noShows()).isEqualTo(1);
        }
    }

    // ================================================================= fakes

    /**
     * In-memory {@link ValidatorRegistry}. Subclassing works because the constructor is
     * package-private and this test shares the package; {@code super(null)} is safe since every method
     * that would touch the {@code JdbcClient} is overridden.
     */
    private static final class FakeValidatorRegistry extends ValidatorRegistry {

        private final Map<String, Validator> byDid = new LinkedHashMap<>();
        private List<String> lastMarkedSampled = List.of();

        private FakeValidatorRegistry() {
            super(null);
        }

        void put(Validator validator) {
            byDid.put(validator.validatorDid(), validator);
        }

        Validator get(String did) {
            return byDid.get(did);
        }

        @Override
        public Validator enroll(String validatorDid, double newcomerReputation, Instant now) {
            Validator enrolled = new Validator(
                    validatorDid, scaled(newcomerReputation), scaled(1.0), true, now, null, null, 0, 0, 0, 0);
            byDid.put(validatorDid, enrolled);
            return enrolled;
        }

        @Override
        public List<SampledValidator> eligibleCandidates() {
            List<SampledValidator> candidates = new ArrayList<>();
            for (Validator v : byDid.values()) {
                if (v.validatorReputation().signum() > 0 && v.uptime().signum() > 0) {
                    candidates.add(v.toSamplingCandidate());
                }
            }
            return candidates;
        }

        @Override
        public Optional<Validator> lockForUpdate(String validatorDid) {
            return Optional.ofNullable(byDid.get(validatorDid));
        }

        @Override
        public Validator save(Validator validator) {
            byDid.put(validator.validatorDid(), validator);
            return validator;
        }

        @Override
        public void markSampled(Collection<String> validatorDids, Instant now) {
            lastMarkedSampled = List.copyOf(validatorDids);
        }
    }

    /** In-memory {@link DuelRepository}. */
    private static final class FakeDuelRepository extends DuelRepository {

        private final Map<UUID, DuelRecord> byId = new LinkedHashMap<>();
        private final List<UUID> resolvedIds = new ArrayList<>();

        private FakeDuelRepository() {
            super(null);
        }

        void putResolved(UUID duelId, QuorumCommittee committee) {
            byId.put(
                    duelId,
                    new DuelRecord(
                            duelId,
                            List.of("did:plc:pa", "did:plc:pb"),
                            committee,
                            "{}",
                            "[]",
                            EARLIER,
                            NOW, // resolved
                            0));
        }

        @Override
        public void open(UUID duelId, List<String> participants, SampledCommittee committee, Instant openedAt) {
            byId.put(
                    duelId,
                    new DuelRecord(duelId, participants, committee.toQuorumCommittee(), null, "[]", openedAt, null, 0));
        }

        @Override
        public Optional<DuelRecord> find(UUID duelId) {
            return Optional.ofNullable(byId.get(duelId));
        }

        @Override
        public void resolve(
                UUID duelId, String outcomeJson, String signaturesJson, Instant resolvedAt, long expectedVersion) {
            resolvedIds.add(duelId);
            DuelRecord current = byId.get(duelId);
            byId.put(
                    duelId,
                    new DuelRecord(
                            duelId,
                            current.participants(),
                            current.committee(),
                            outcomeJson,
                            signaturesJson,
                            current.openedAt(),
                            resolvedAt,
                            expectedVersion + 1));
        }
    }

    /** In-memory {@link FlaggedServerRegistry}, capturing the flags raised. */
    private static final class FakeFlaggedServerRegistry extends FlaggedServerRegistry {

        private final List<FlaggedServer> raised = new ArrayList<>();

        private FakeFlaggedServerRegistry() {
            super(null);
        }

        @Override
        public FlaggedServer flag(
                String serverDid, String reason, Map<String, Object> evidence, String raisedByDid, Instant now) {
            FlaggedServer flag =
                    new FlaggedServer(UUID.randomUUID(), serverDid, reason, "{}", raisedByDid, now, null, null);
            raised.add(flag);
            return flag;
        }
    }
}
