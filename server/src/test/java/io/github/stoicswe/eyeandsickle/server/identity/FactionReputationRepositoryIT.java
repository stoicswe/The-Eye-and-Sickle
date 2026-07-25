package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.FactionReputation;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@link FactionReputationRepository} against a real PostgreSQL. What needs the database: the additive
 * {@code standing = standing + :delta} upsert (so two adjustments both land), the ability to hold
 * standing with both sides at once before the binary commitment, and the foreign key to {@code players}.
 */
class FactionReputationRepositoryIT extends PostgresIntegrationTestBase {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-24T11:00:00Z");

    private FactionReputationRepository repository;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        repository = new FactionReputationRepository(jdbcClient());
        // A standing row references a player; create one first.
        playerId = new PlayerRepository(jdbcClient())
                .createCharacter(DID, "alice.bsky.social", 1, NOW)
                .playerId();
    }

    @Test
    @DisplayName("adjustStanding creates the row at the delta, then accumulates additively")
    void adjustAccumulates() {
        repository.adjustStanding(playerId, Faction.SICKLE, 100, NOW);
        repository.adjustStanding(playerId, Faction.SICKLE, 25, LATER);

        assertThat(repository.findByPlayer(playerId)).containsExactly(new FactionReputation(Faction.SICKLE, 125));
    }

    @Test
    @DisplayName("standing may go negative — actively hostile is a real state")
    void standingMayGoNegative() {
        repository.adjustStanding(playerId, Faction.EYE, -40, NOW);
        assertThat(repository.findByPlayer(playerId)).containsExactly(new FactionReputation(Faction.EYE, -40));
    }

    @Test
    @DisplayName("setStanding overrides the accumulated value — this is how abandonment resets to zero")
    void setOverrides() {
        repository.adjustStanding(playerId, Faction.EYE, 200, NOW);
        repository.setStanding(playerId, Faction.EYE, 0, LATER);

        assertThat(repository.findByPlayer(playerId)).containsExactly(new FactionReputation(Faction.EYE, 0));
    }

    @Test
    @DisplayName("a player can hold standing with BOTH sides before committing, read back ordered by faction")
    void bothSidesBeforeCommitment() {
        // Exactly what the single-column sketch in docs/architecture/06 §2 could not represent, and why
        // standings are per-(player, faction) rows (docs/design/01 §5).
        repository.adjustStanding(playerId, Faction.EYE, -40, NOW);
        repository.adjustStanding(playerId, Faction.SICKLE, 120, NOW);

        assertThat(repository.findByPlayer(playerId))
                .containsExactly(new FactionReputation(Faction.EYE, -40), new FactionReputation(Faction.SICKLE, 120));
    }

    @Test
    @DisplayName("a standing cannot be recorded for a player that does not exist")
    void foreignKeyBites() {
        assertThatThrownBy(() -> repository.adjustStanding(UUID.randomUUID(), Faction.EYE, 10, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a player with no standing rows reads back empty")
    void emptyForUnknownPlayer() {
        assertThat(repository.findByPlayer(playerId)).isEmpty();
    }
}
