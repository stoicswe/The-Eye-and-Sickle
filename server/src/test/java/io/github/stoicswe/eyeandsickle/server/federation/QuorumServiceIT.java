package io.github.stoicswe.eyeandsickle.server.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.provenance.SigningKeyDirectory;
import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole §5 loop end to end against a real PostgreSQL: enroll validators, open a duel and freeze
 * its committee, submit genuinely-signed votes, adjudicate, and assert the persisted consequences —
 * the resolved duel, the moved reputations, and the automatic equivocation flag.
 *
 * <p>This is the one place the pure pieces, the repositories and the database meet. The service is
 * {@code @Transactional}, but a directly-constructed instance has no Spring proxy, so each call is run
 * inside the base's {@link #transactions()} template — which is also what makes the {@code SELECT ...
 * FOR UPDATE} in the reputation update a real lock rather than an autocommit read.
 */
class QuorumServiceIT extends DatabaseIntegrationTestBase {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final String A = FederationFixture.HOLDER_A;
    private static final String B = FederationFixture.HOLDER_B;
    private static final List<String> PARTICIPANTS =
            List.of("did:plc:participanta000000a", "did:plc:participantb000000a");

    private final FederationFixture fx = new FederationFixture();
    private final SigningKeyDirectory keys = fx.directory();

    private QuorumService service;
    private ValidatorRegistry validators;
    private DuelRepository duels;
    private FlaggedServerRegistry flags;

    @BeforeEach
    void setUp() {
        validators = new ValidatorRegistry(jdbcClient());
        duels = new DuelRepository(jdbcClient());
        flags = new FlaggedServerRegistry(jdbcClient());
        service = new QuorumService(
                validators,
                duels,
                flags,
                new QuorumProperties(7, 0.05, 0.25, 0.10, 0.10, 0.40),
                new Random(9L),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void enrollSeven() {
        transactions().executeWithoutResult(status -> {
            for (int i = 1; i <= 7; i++) {
                service.enrollValidator("did:plc:validator" + i);
            }
        });
    }

    private UUID openDuel() {
        UUID duelId = UUID.randomUUID();
        transactions().executeWithoutResult(status -> service.openDuel(duelId, PARTICIPANTS));
        return duelId;
    }

    private AdjudicationResult adjudicate(UUID duelId, List<ValidatorSignature> votes) {
        return transactions().execute(status -> service.adjudicate(duelId, votes, keys));
    }

    private ValidatorSignature vote(UUID duelId, String winner, int index) {
        return fx.vote(duelId.toString(), winner, "did:plc:validator" + index);
    }

    @Test
    @DisplayName("resolves a duel and persists every validator's reputation move")
    void resolvesAndPersists() {
        enrollSeven();
        UUID duelId = openDuel();
        assertThat(duels.find(duelId).orElseThrow().isResolved()).isFalse();

        List<ValidatorSignature> votes = new ArrayList<>(List.of(
                vote(duelId, A, 1),
                vote(duelId, A, 2),
                vote(duelId, A, 3),
                vote(duelId, A, 4),
                vote(duelId, A, 5),
                vote(duelId, B, 6))); // validator6 diverges; validator7 silent

        AdjudicationResult result = adjudicate(duelId, votes);
        assertThat(result.resolved()).isTrue();

        // The duel row is closed with an outcome.
        DuelRecord resolved = duels.find(duelId).orElseThrow();
        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.outcomeJson()).isNotNull();

        // CORRECT: 0.40 + 0.05·(1 − 0.40) = 0.43, one correct vote, no longer a newcomer.
        Validator correct = validators.find("did:plc:validator1").orElseThrow();
        assertThat(correct.validatorReputation().doubleValue()).isCloseTo(0.43, within(1e-9));
        assertThat(correct.votesCorrect()).isEqualTo(1);
        assertThat(correct.isNew()).isFalse();
        assertThat(correct.lastVoteAt()).isEqualTo(NOW);

        // DIVERGENT: 0.40 · (1 − 0.25) = 0.30, uptime untouched.
        Validator divergent = validators.find("did:plc:validator6").orElseThrow();
        assertThat(divergent.validatorReputation().doubleValue()).isCloseTo(0.30, within(1e-9));
        assertThat(divergent.votesDivergent()).isEqualTo(1);
        assertThat(divergent.uptime().doubleValue()).isCloseTo(1.0, within(1e-9));

        // NO_SHOW: uptime 1 · (1 − 0.10) = 0.90, reputation UNTOUCHED at 0.40.
        Validator noShow = validators.find("did:plc:validator7").orElseThrow();
        assertThat(noShow.uptime().doubleValue()).isCloseTo(0.90, within(1e-9));
        assertThat(noShow.validatorReputation().doubleValue()).isCloseTo(0.40, within(1e-9));
        assertThat(noShow.noShows()).isEqualTo(1);
    }

    @Test
    @DisplayName("slashes and federation-flags an equivocator, all in one committed step")
    void equivocatorSlashedAndFlagged() {
        enrollSeven();
        UUID duelId = openDuel();

        List<ValidatorSignature> votes = new ArrayList<>(List.of(
                vote(duelId, A, 1),
                vote(duelId, A, 2),
                vote(duelId, A, 3),
                vote(duelId, A, 4),
                vote(duelId, A, 5),
                vote(duelId, A, 6), // validator6 signs A ...
                vote(duelId, B, 6))); // ... and B — provable equivocation

        AdjudicationResult result = adjudicate(duelId, votes);

        assertThat(result.resolved()).isTrue();
        // The hard slash landed and was persisted.
        assertThat(validators
                        .find("did:plc:validator6")
                        .orElseThrow()
                        .validatorReputation()
                        .doubleValue())
                .isCloseTo(0.10, within(1e-9));
        // The federation-wide flag was raised automatically, with the equivocation reason and its proof.
        assertThat(flags.isFlagged("did:plc:validator6")).isTrue();
        assertThat(flags.findActive("did:plc:validator6").orElseThrow().reason())
                .isEqualTo(FlaggedServer.REASON_EQUIVOCATION);
        assertThat(flags.findActive("did:plc:validator6").orElseThrow().evidenceJson())
                .contains("equivocation");
    }

    @Test
    @DisplayName("does not resolve below quorum and writes no outcome")
    void belowQuorumWritesNoOutcome() {
        enrollSeven();
        UUID duelId = openDuel();

        // Only four agree.
        List<ValidatorSignature> votes = new ArrayList<>(
                List.of(vote(duelId, A, 1), vote(duelId, A, 2), vote(duelId, A, 3), vote(duelId, A, 4)));

        AdjudicationResult result = adjudicate(duelId, votes);

        assertThat(result.resolved()).isFalse();
        DuelRecord duel = duels.find(duelId).orElseThrow();
        assertThat(duel.isResolved()).isFalse();
        assertThat(duel.outcomeJson()).isNull();
    }
}
