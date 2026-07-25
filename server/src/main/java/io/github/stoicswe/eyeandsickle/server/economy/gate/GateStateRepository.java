package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The two authoritative reads the reputation and proof-of-skill gates need, both keyed on the acting
 * <strong>character</strong>.
 *
 * <p>Kept together because both answer "what has this character earned" for gate evaluation, and both are
 * read-only — this repository never writes. Faction standing is moved by the faction system; breach
 * resolutions are written by the breach system. The economy slice only <em>reads</em> them to decide a
 * gate.
 *
 * <p>Both reads are per-character ({@code docs/architecture/09-player-state-portability.md} §3, §9), and
 * they reach that two ways because the two tables key differently: {@code faction_reputations} keys on the
 * character's local {@code player_id}, so its standing is resolved by joining {@code players} on the
 * character's {@code (did, slot)}; {@code breach_resolutions} keys on a {@code player_did} string, which is
 * the character DID itself, so proof-of-skill matches it directly. Keying the reputation read on the account
 * DID alone would also reintroduce 09 §9's bug — an account with two characters would match two standings and
 * throw on {@code .optional()}.
 */
@Repository
public class GateStateRepository {

    private final JdbcClient jdbcClient;

    GateStateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    /**
     * A character's standing with one named faction.
     *
     * <p>Joined from {@code faction_reputations} through {@code players} on the character's
     * {@code (did, slot)}, because standing is keyed by the local {@code player_id} while the caller holds
     * a character DID. Resolving the specific character — not just the account DID — is what keeps standing
     * per-character and keeps the join returning one row for an account that holds several. Absent means the
     * character has no row for that faction yet, which the caller reads as a standing of zero — not as an
     * error, because "no standing recorded" and "standing of zero" are the same position (uncommitted).
     *
     * @param character the acting character
     * @param faction the named faction; {@link Faction#NONE} has no standing and is rejected upstream
     *     by {@link GateCondition.ReputationRequirement}
     * @return the standing, or empty if none is recorded
     */
    public Optional<Long> factionStanding(CharacterDid character, Faction faction) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(faction, "faction");
        return jdbcClient
                .sql("""
                        SELECT fr.standing
                          FROM faction_reputations fr
                          JOIN players p ON p.player_id = fr.player_id
                         WHERE p.did = :accountDid
                           AND p.slot = :slot
                           AND p.status = 'active'
                           AND fr.faction = :faction
                        """)
                .param("accountDid", character.accountDid())
                .param("slot", character.slot())
                .param("faction", EnumColumns.faction(faction))
                .query(Long.class)
                .optional();
    }

    /**
     * The highest difficulty this character has <em>breached against a live target</em> in one puzzle
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
     * <p>Matched on {@code player_did} directly, because that column stores the character DID string. No
     * breach-resolution writer exists yet ({@code docs/design/15-open-questions.md}); when the minigame
     * lands it will stamp {@code CharacterDid.of(...)} there, and this read already keys on the same value,
     * so proof of skill is per-character.
     *
     * @param character the acting character
     * @param puzzleClass the class the automation shortcut belongs to
     * @return the highest qualifying tier, or empty if the character has never breached a live target of
     *     that class
     */
    public Optional<DifficultyTier> highestLiveBreachTier(CharacterDid character, PuzzleClass puzzleClass) {
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(puzzleClass, "puzzleClass");
        return jdbcClient
                .sql("""
                        SELECT difficulty_tier
                          FROM breach_resolutions
                         WHERE player_did = :characterDid
                           AND puzzle_class = :class
                           AND outcome = 'breached'
                           AND live_or_dormant = 'live'
                         ORDER BY difficulty_tier DESC
                         LIMIT 1
                        """)
                .param("characterDid", character.value())
                .param("class", EnumColumns.puzzleClass(puzzleClass))
                .query(Integer.class)
                .optional()
                .map(DifficultyTier::of);
    }
}
