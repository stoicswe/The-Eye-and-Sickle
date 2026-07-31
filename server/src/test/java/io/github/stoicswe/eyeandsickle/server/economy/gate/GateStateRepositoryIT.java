package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two authoritative reads the reputation and proof-of-skill gates need, against a real PostgreSQL,
 * both keyed on the character (09 §9).
 *
 * <p>Two things matter here. Faction standing is resolved through the character's {@code (did, slot)}, so
 * an account with two characters returns each character's own standing and the lookup never matches two
 * rows and throws — the reputation face of 09 §9's bug. And the proof-of-skill query must read the highest
 * live-and-breached tier and <em>nothing else</em> — never a count, never a dormant or failed attempt
 * (Invariant I7) — with {@code player_did} now holding the character DID.
 */
class GateStateRepositoryIT extends PostgresIntegrationTestBase {

    private static final CharacterDid CHARACTER = new CharacterDid("did:plc:operator00000000000000", 1);
    private static final CharacterDid CHARACTER_SLOT_2 = new CharacterDid("did:plc:operator00000000000000", 2);
    private static final CharacterDid OTHER = new CharacterDid("did:plc:someoneelse0000000000", 1);

    private final GateStateRepository repository = new GateStateRepository(jdbcClient());

    @Test
    @DisplayName("faction standing is read per named faction, resolved through the character's (did, slot)")
    void factionStanding() {
        UUID playerId = insertPlayer(CHARACTER);
        insertFactionReputation(playerId, "sickle", 120);
        insertFactionReputation(playerId, "eye", -40);

        assertThat(repository.factionStanding(CHARACTER, Faction.SICKLE)).contains(120L);
        assertThat(repository.factionStanding(CHARACTER, Faction.EYE)).contains(-40L);
    }

    @Test
    @DisplayName("faction standing is per character — two characters of one account keep separate standing")
    void factionStandingIsPerCharacter() {
        UUID first = insertPlayer(CHARACTER);
        UUID second = insertPlayer(CHARACTER_SLOT_2);
        insertFactionReputation(first, "sickle", 120);
        insertFactionReputation(second, "sickle", 5);

        // Same account DID, two slots: the join resolves the slot, so each character sees only its own
        // standing and the query returns exactly one row (the >1-character lookup no longer throws).
        assertThat(repository.factionStanding(CHARACTER, Faction.SICKLE)).contains(120L);
        assertThat(repository.factionStanding(CHARACTER_SLOT_2, Faction.SICKLE)).contains(5L);
    }

    @Test
    @DisplayName("no recorded standing is empty (the caller reads it as zero), and an unknown character is empty")
    void absentStandingIsEmpty() {
        insertPlayer(CHARACTER); // a character, but with no faction_reputations row

        assertThat(repository.factionStanding(CHARACTER, Faction.SICKLE)).isEmpty();
        assertThat(repository.factionStanding(new CharacterDid("did:plc:ghost00000000000000000", 1), Faction.EYE))
                .isEmpty();
    }

    @Test
    @DisplayName("proof-of-skill reads the highest LIVE, BREACHED tier — never a count (Invariant I7)")
    void highestLiveBreachTierIsTierGated() {
        // Three tier-1 wins: farming the weakest target must not add up to a higher-tier unlock.
        insertResolution(CHARACTER, "offset_cipher", 1, "live", "breached");
        insertResolution(CHARACTER, "offset_cipher", 1, "live", "breached");
        insertResolution(CHARACTER, "offset_cipher", 1, "live", "breached");
        // A tier-4 win, but against a DORMANT target — worth loot, never worth an unlock.
        insertResolution(CHARACTER, "offset_cipher", 4, "dormant", "breached");
        // A tier-5 attempt against a live target, but FAILED — competence not demonstrated.
        insertResolution(CHARACTER, "offset_cipher", 5, "live", "failed");
        // The genuine article: tier 3, live, breached.
        insertResolution(CHARACTER, "offset_cipher", 3, "live", "breached");

        assertThat(repository.highestLiveBreachTier(CHARACTER, PuzzleClass.OFFSET_CIPHER))
                .contains(DifficultyTier.of(3));
    }

    @Test
    @DisplayName("a class the character has never breached live is empty, and another character is isolated")
    void neverBreachedIsEmpty() {
        insertResolution(CHARACTER, "offset_cipher", 3, "live", "breached");

        // Different class, and a different character, are both isolated.
        assertThat(repository.highestLiveBreachTier(CHARACTER, PuzzleClass.BREACH_PROTOCOL))
                .isEmpty();
        assertThat(repository.highestLiveBreachTier(OTHER, PuzzleClass.OFFSET_CIPHER))
                .isEmpty();
    }

    @Test
    @DisplayName("proof-of-skill is per character — one character's breaches do not unlock another's gate")
    void proofOfSkillIsPerCharacter() {
        // Two characters of one account: only slot 1 has breached the class, live.
        insertResolution(CHARACTER, "offset_cipher", 4, "live", "breached");

        assertThat(repository.highestLiveBreachTier(CHARACTER, PuzzleClass.OFFSET_CIPHER))
                .contains(DifficultyTier.of(4));
        assertThat(repository.highestLiveBreachTier(CHARACTER_SLOT_2, PuzzleClass.OFFSET_CIPHER))
                .isEmpty();
    }

    @Test
    @DisplayName("a dormant-only or failed-only history yields no proof of skill")
    void onlyDisqualifyingRowsIsEmpty() {
        insertResolution(CHARACTER, "breach_protocol", 5, "dormant", "breached");
        insertResolution(CHARACTER, "breach_protocol", 5, "live", "failed");
        insertResolution(CHARACTER, "breach_protocol", 4, "live", "aborted");

        assertThat(repository.highestLiveBreachTier(CHARACTER, PuzzleClass.BREACH_PROTOCOL))
                .isEmpty();
    }

    private UUID insertPlayer(CharacterDid character) {
        UUID playerId = UUID.randomUUID();
        jdbcClient()
                .sql("INSERT INTO players (player_id, did, slot, handle) VALUES (:id, :did, :slot, 'operator')")
                .param("id", playerId)
                .param("did", character.accountDid())
                .param("slot", character.slot())
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

    private void insertResolution(
            CharacterDid character, String puzzleClass, int tier, String targetState, String outcome) {
        // player_did stores the character DID string — what the future minigame writer will stamp.
        jdbcClient()
                .sql("""
                        INSERT INTO breach_resolutions
                            (resolution_id, player_did, puzzle_class, difficulty_tier, live_or_dormant, outcome)
                        VALUES (:id, :did, :class, :tier, :target, :outcome)
                        """)
                .param("id", UUID.randomUUID())
                .param("did", character.value())
                .param("class", puzzleClass)
                .param("tier", tier)
                .param("target", targetState)
                .param("outcome", outcome)
                .update();
    }
}
