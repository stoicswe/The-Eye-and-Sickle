package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.FactionReputation;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.DevSignin;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.Operator;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link FactionService} wired to the real repositories against PostgreSQL, so the commit and abandon
 * transitions are exercised as the SQL side effects they actually produce — proving the orchestration and
 * the schema agree. The finer branch logic is covered with fakes in {@link FactionServiceTest}; this
 * confirms the wiring end to end.
 */
class FactionServiceIT extends PostgresIntegrationTestBase {

    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final BigDecimal SPIKE = new BigDecimal("10");

    private PlayerRepository players;
    private FactionReputationRepository reputations;
    private RecordingFactionToolForfeiture forfeiture;
    private FactionService service;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        players = new PlayerRepository(jdbcClient());
        reputations = new FactionReputationRepository(jdbcClient());
        forfeiture = new RecordingFactionToolForfeiture();
        IdentityProperties properties = new IdentityProperties(
                new Operator(null, null, null), new DevSignin(false), Duration.ofHours(24), SPIKE);
        service = new FactionService(players, reputations, forfeiture, properties, CLOCK);
        playerId = players.createCharacter(DID, "alice.bsky.social", 1, NOW).playerId();
    }

    @Test
    @DisplayName("commit persists the committed side to the players row")
    void commitPersists() {
        service.commit(playerId, Faction.EYE);
        assertThat(players.requireCharacter(playerId).faction()).isEqualTo(Faction.EYE);
    }

    @Test
    @DisplayName("abandon resets the abandoned standing, spikes heat, and forfeits tools — all persisted")
    void abandonAppliesFullTransition() {
        service.commit(playerId, Faction.EYE);
        reputations.adjustStanding(playerId, Faction.EYE, 100, NOW);

        Player after = service.abandon(playerId);

        // The returned player and the persisted row agree: back to NONE, heat raised by the configured
        // spike.
        assertThat(after.faction()).isEqualTo(Faction.NONE);
        assertThat(after.personalHeat().value()).isEqualByComparingTo("10");

        Player reloaded = players.requireCharacter(playerId);
        assertThat(reloaded.faction()).isEqualTo(Faction.NONE);
        assertThat(reloaded.personalHeat().value()).isEqualByComparingTo("10");

        // The abandoned side's standing is reset to zero in the table.
        assertThat(reputations.findByPlayer(playerId)).containsExactly(new FactionReputation(Faction.EYE, 0));

        // And the forfeiture seam fired for the side left behind.
        assertThat(forfeiture.calls())
                .singleElement()
                .satisfies(call -> assertThat(call.abandonedFaction()).isEqualTo(Faction.EYE));
    }
}
