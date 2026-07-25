package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The faction commitment state machine ({@code docs/design/01-core-resources.md} §5).
 *
 * <h2>The transitions this owns</h2>
 *
 * <ul>
 *   <li><strong>Commit</strong> — an uncommitted player picks a side. Reputation "eventually forces a
 *       binary commitment", so a player commits to exactly one of Eye or Sickle, and cannot commit to
 *       the opposite side without abandoning the current one first.
 *   <li><strong>Abandon</strong> — leaving a side <em>resets that reputation, spikes heat temporarily,
 *       and forfeits faction-specific tools</em>. All three happen together: the faction returns to
 *       {@link Faction#NONE}, the abandoned side's standing is reset to zero, personal heat rises, and
 *       the tool forfeiture seam fires — in one transaction, so a reader never sees the transition half
 *       done.
 * </ul>
 *
 * <h2>Two things this deliberately does not decide (balance values, not mine to invent)</h2>
 *
 * <ol>
 *   <li>The <strong>heat-spike magnitude</strong>. The design says abandonment "spikes heat" but never
 *       by how much, and a heat magnitude is calibrated with the rest of the economy. So the amount is
 *       supplied — either explicitly to {@link #abandon(UUID, BigDecimal)} or from configuration via
 *       {@link #abandon(UUID)} — and this service refuses to fabricate one. Logged as undecided.
 *   <li>The <strong>commitment threshold</strong>. <em>When</em> reputation forces or permits a
 *       commitment is a balance rule the docs leave open, so {@link #commit(UUID, Faction)} records a
 *       commitment without gating it on a standing threshold. A rules layer that decides eligibility is
 *       expected to call this once it has decided; that gate is not invented here.
 * </ol>
 */
@Service
public class FactionService {

    private final PlayerRepository players;
    private final FactionReputationRepository reputations;
    private final FactionToolForfeiture forfeiture;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param players the player table (holds the committed faction and personal heat)
     * @param reputations the per-faction standings
     * @param forfeiture the tool-forfeiture seam (see {@link FactionToolForfeiture})
     * @param properties supplies the optional configured heat spike
     * @param clock the source of the transition instant
     */
    public FactionService(
            PlayerRepository players,
            FactionReputationRepository reputations,
            FactionToolForfeiture forfeiture,
            IdentityProperties properties,
            Clock clock) {
        this.players = Objects.requireNonNull(players, "players");
        this.reputations = Objects.requireNonNull(reputations, "reputations");
        this.forfeiture = Objects.requireNonNull(forfeiture, "forfeiture");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Commits an uncommitted player to a side.
     *
     * <p>Idempotent for the side already held. Committing to the <em>other</em> named side is refused:
     * a switch is an abandonment followed by a fresh commitment, and folding those into one silent step
     * would skip the reset, heat spike and forfeiture that leaving a side is supposed to cost.
     *
     * @param playerId the player
     * @param faction the side to commit to; must be {@link Faction#EYE} or {@link Faction#SICKLE}
     * @return the player after the commitment
     * @throws IllegalArgumentException if {@code faction} is {@link Faction#NONE}
     * @throws IllegalStateException if the player is already committed to the opposite side
     * @throws PlayerNotFoundException if no such player exists
     */
    @Transactional
    public Player commit(UUID playerId, Faction faction) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(faction, "faction");
        if (faction == Faction.NONE) {
            throw new IllegalArgumentException(
                    "Commit is to a named side; Faction.NONE is uncommitted, which is where a player starts, "
                            + "not somewhere they commit to. Use abandon to return there.");
        }
        Player player = players.requireCharacter(playerId);
        if (player.faction() == faction) {
            return player; // already there
        }
        if (player.faction() != Faction.NONE) {
            throw new IllegalStateException("Player " + playerId + " is committed to " + player.faction()
                    + "; abandon that side before committing to " + faction
                    + " (docs/design/01-core-resources.md §5).");
        }
        players.updateFaction(playerId, faction, player.rowVersion());
        return players.requireCharacter(playerId);
    }

    /**
     * Abandons the player's current side, applying the configured heat spike.
     *
     * @param playerId the player
     * @return the player after the transition
     * @throws IllegalStateException if the player has no committed side, or if no heat-spike magnitude is
     *     configured (it is an undecided balance value — see the class notes and
     *     {@link IdentityProperties#factionAbandonmentHeatSpike()})
     * @throws PlayerNotFoundException if no such player exists
     */
    @Transactional
    public Player abandon(UUID playerId) {
        BigDecimal spike = properties.factionAbandonmentHeatSpike();
        if (spike == null) {
            throw new IllegalStateException(
                    "Faction abandonment applies a heat spike, but its magnitude is an undecided balance value "
                            + "(docs/design/01-core-resources.md §5, docs/design/15). Configure "
                            + "eyeandsickle.identity.faction-abandonment-heat-spike, or call abandon(playerId, heatSpike) "
                            + "with the calibrated value. [PROPOSAL]");
        }
        return abandon(playerId, spike);
    }

    /**
     * Abandons the player's current side, applying an explicit heat spike.
     *
     * <p>The reset, the spike and the forfeiture are one transaction. The faction change and heat spike
     * are a single version-checked row update ({@link PlayerRepository#updateFactionAndHeat}), so they
     * cannot be observed apart; the standing reset and forfeiture then run in the same transaction.
     *
     * @param playerId the player
     * @param heatSpike the heat to add; a caller-supplied balance value, never negative
     * @return the player after the transition
     * @throws IllegalArgumentException if {@code heatSpike} is negative
     * @throws IllegalStateException if the player has no committed side to abandon
     * @throws PlayerNotFoundException if no such player exists
     */
    @Transactional
    public Player abandon(UUID playerId, BigDecimal heatSpike) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(heatSpike, "heatSpike");
        if (heatSpike.signum() < 0) {
            throw new IllegalArgumentException("heatSpike is a magnitude and cannot be negative, was " + heatSpike);
        }
        Player player = players.requireCharacter(playerId);
        Faction abandoned = player.faction();
        if (abandoned == Faction.NONE) {
            throw new IllegalStateException("Player " + playerId + " has no committed faction to abandon");
        }
        Heat newHeat = player.personalHeat().plus(heatSpike);
        // Faction reset + heat spike as one version-checked update: indivisible, and it detects a
        // concurrent writer.
        players.updateFactionAndHeat(playerId, Faction.NONE, newHeat, player.rowVersion());
        // Reset the abandoned side's standing (docs/design/01 §5). The other side's standing, if any, is
        // left untouched — the player only left one side.
        reputations.setStanding(playerId, abandoned, 0, clock.instant());
        // Forfeit that side's tools. A seam: the item system owns the effect (see FactionToolForfeiture).
        forfeiture.forfeitFactionTools(playerId, abandoned);
        return players.requireCharacter(playerId);
    }
}
