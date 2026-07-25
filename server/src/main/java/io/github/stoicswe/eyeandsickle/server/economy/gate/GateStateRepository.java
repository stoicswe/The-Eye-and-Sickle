package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The two authoritative reads the reputation and proof-of-skill gates need.
 *
 * <p>Kept together because both answer "what has this player earned" for gate evaluation, and both are
 * read-only — this repository never writes. Faction standing is moved by the faction system; breach
 * resolutions are written by the breach system. The economy slice only <em>reads</em> them to decide a
 * gate.
 */
@Repository
public class GateStateRepository {

    private final JdbcClient jdbcClient;

    GateStateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * A player's standing with one named faction.
     *
     * <p>Joined from {@code faction_reputations} through {@code players} on the DID, because standing
     * is keyed by the local {@code player_id} while the caller holds a DID. Absent means the player has
     * no row for that faction yet, which the caller reads as a standing of zero — not as an error,
     * because "no standing recorded" and "standing of zero" are the same position (uncommitted).
     *
     * @param did the player's DID
     * @param faction the named faction; {@link Faction#NONE} has no standing and is rejected upstream
     *     by {@link GateCondition.ReputationRequirement}
     * @return the standing, or empty if none is recorded
     */
    public Optional<Long> factionStanding(String did, Faction faction) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(faction, "faction");
        return jdbcClient
                .sql("""
                        SELECT fr.standing
                          FROM faction_reputations fr
                          JOIN players p ON p.player_id = fr.player_id
                         WHERE p.did = :did
                           AND fr.faction = :faction
                        """)
                .param("did", did)
                .param("faction", EnumColumns.faction(faction))
                .query(Long.class)
                .optional();
    }

    /**
     * The highest difficulty this player has <em>breached against a live target</em> in one puzzle
     * class.
     *
     * <h2>Tier-gated, never count-gated (Invariant I7)</h2>
     *
     * This is the single query the proof-of-skill gate is allowed to ask. It reads the top tier, not a
     * count: three tier-1 wins do not add up to a tier-3 unlock, and a dormant-target or failed attempt
     * does not count at all. The {@code WHERE outcome = 'breached' AND live_or_dormant = 'live'} clause
     * and the {@code ORDER BY difficulty_tier DESC LIMIT 1} are shaped to ride the partial index
     * {@code ix_breach_resolutions_proof_of_skill} — and, more importantly, to make counting
     * structurally awkward here, so the anti-farming rule cannot be bypassed by reaching for
     * {@code count(*)}.
     *
     * @param did the player's DID
     * @param puzzleClass the class the automation shortcut belongs to
     * @return the highest qualifying tier, or empty if the player has never breached a live target of
     *     that class
     */
    public Optional<DifficultyTier> highestLiveBreachTier(String did, PuzzleClass puzzleClass) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(puzzleClass, "puzzleClass");
        return jdbcClient
                .sql("""
                        SELECT difficulty_tier
                          FROM breach_resolutions
                         WHERE player_did = :did
                           AND puzzle_class = :class
                           AND outcome = 'breached'
                           AND live_or_dormant = 'live'
                         ORDER BY difficulty_tier DESC
                         LIMIT 1
                        """)
                .param("did", did)
                .param("class", EnumColumns.puzzleClass(puzzleClass))
                .query(Integer.class)
                .optional()
                .map(DifficultyTier::of);
    }
}
