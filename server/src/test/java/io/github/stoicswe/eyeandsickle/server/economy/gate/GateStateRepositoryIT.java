package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two authoritative reads the reputation and proof-of-skill gates need, against a real PostgreSQL.
 *
 * <p>The proof-of-skill query is the one that matters most: it must read the highest live-and-breached
 * tier and <em>nothing else</em> — never a count, never a dormant or failed attempt (Invariant I7). A
 * player who cleared tier 1 a hundred times has not earned a tier-3 unlock, and this is where that is
 * enforced at the SQL level.
 */
class GateStateRepositoryIT extends PostgresIntegrationTestBase {

    private static final String DID = "did:plc:operator00000000000000";
    private static final String OTHER = "did:plc:someoneelse0000000000";

    private final GateStateRepository repository = new GateStateRepository(jdbcClient());

    @Test
    @DisplayName("faction standing is read per named faction, joined through the DID")
    void factionStanding() {
        UUID playerId = insertPlayer(DID);
        insertFactionReputation(playerId, "sickle", 120);
        insertFactionReputation(playerId, "eye", -40);

        assertThat(repository.factionStanding(DID, Faction.SICKLE)).contains(120L);
        assertThat(repository.factionStanding(DID, Faction.EYE)).contains(-40L);
    }

    @Test
    @DisplayName("no recorded standing is empty (the caller reads it as zero), and an unknown DID is empty")
    void absentStandingIsEmpty() {
        insertPlayer(DID); // a player, but with no faction_reputations row

        assertThat(repository.factionStanding(DID, Faction.SICKLE)).isEmpty();
        assertThat(repository.factionStanding("did:plc:ghost00000000000000000", Faction.EYE))
                .isEmpty();
    }

    @Test
    @DisplayName("proof-of-skill reads the highest LIVE, BREACHED tier — never a count (Invariant I7)")
    void highestLiveBreachTierIsTierGated() {
        // Three tier-1 wins: farming the weakest target must not add up to a higher-tier unlock.
        insertResolution(DID, "logic", 1, "live", "breached");
        insertResolution(DID, "logic", 1, "live", "breached");
        insertResolution(DID, "logic", 1, "live", "breached");
        // A tier-4 win, but against a DORMANT target — worth loot, never worth an unlock.
        insertResolution(DID, "logic", 4, "dormant", "breached");
        // A tier-5 attempt against a live target, but FAILED — competence not demonstrated.
        insertResolution(DID, "logic", 5, "live", "failed");
        // The genuine article: tier 3, live, breached.
        insertResolution(DID, "logic", 3, "live", "breached");

        assertThat(repository.highestLiveBreachTier(DID, PuzzleClass.LOGIC)).contains(DifficultyTier.of(3));
    }

    @Test
    @DisplayName("a class the player has never breached live is empty")
    void neverBreachedIsEmpty() {
        insertResolution(DID, "logic", 3, "live", "breached");

        // Different class, and a different player, are both isolated.
        assertThat(repository.highestLiveBreachTier(DID, PuzzleClass.TIMING)).isEmpty();
        assertThat(repository.highestLiveBreachTier(OTHER, PuzzleClass.LOGIC)).isEmpty();
    }

    @Test
    @DisplayName("a dormant-only or failed-only history yields no proof of skill")
    void onlyDisqualifyingRowsIsEmpty() {
        insertResolution(DID, "traversal", 5, "dormant", "breached");
        insertResolution(DID, "traversal", 5, "live", "failed");
        insertResolution(DID, "traversal", 4, "live", "aborted");

        assertThat(repository.highestLiveBreachTier(DID, PuzzleClass.TRAVERSAL)).isEmpty();
    }

    private UUID insertPlayer(String did) {
        UUID playerId = UUID.randomUUID();
        jdbcClient()
                .sql("INSERT INTO players (player_id, did, handle) VALUES (:id, :did, 'operator')")
                .param("id", playerId)
                .param("did", did)
                .update();
        return playerId;
    }

    private void insertFactionReputation(UUID playerId, String faction, long standing) {
        jdbcClient()
                .sql("""
                        INSERT INTO faction_reputations (player_id, faction, standing)
                        VALUES (:player, :faction, :standing)
                        """)
                .param("player", playerId)
                .param("faction", faction)
                .param("standing", standing)
                .update();
    }

    private void insertResolution(String did, String puzzleClass, int tier, String targetState, String outcome) {
        jdbcClient()
                .sql("""
                        INSERT INTO breach_resolutions
                            (resolution_id, player_did, puzzle_class, difficulty_tier, live_or_dormant, outcome)
                        VALUES (:id, :did, :class, :tier, :target, :outcome)
                        """)
                .param("id", UUID.randomUUID())
                .param("did", did)
                .param("class", puzzleClass)
                .param("tier", tier)
                .param("target", targetState)
                .param("outcome", outcome)
                .update();
    }
}
