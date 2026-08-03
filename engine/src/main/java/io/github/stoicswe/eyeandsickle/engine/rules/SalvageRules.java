package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;

/**
 * Generic schematic contribution material: partial progress toward a schematic unlock.
 *
 * <h2>The rate, and where it came from</h2>
 *
 * {@code docs/design/02-unlock-gates.md} §2.2, decided 2026-07-26 closing OQ-5: "a schematic costs
 * material equivalent to roughly ten destroyed bot instances", anchored to {@code
 * docs/design/10-botnets.md} §2's published frame costs of 25-35 EC per instance — "about 300 EC of
 * deliberately destroyed value, derived from a number already in the economy rather than invented
 * beside it".
 *
 * <p>This is the <em>other</em> stream feeding the same pool. {@code 10} §1a's version drops material
 * from a lost bot; this one drops it from a breach. Both pay into {@link GameSave#schematicMaterial}
 * and both are gated the same way, because a second stream with a second gate would be a second
 * place for the guard to be got wrong.
 *
 * <h2>The gate is the whole safety argument (Invariant I13)</h2>
 *
 * {@code 10} §1a: "the material drop is gated on <b>engagement tier</b> — the bot must have been lost
 * against a defended target above a difficulty threshold. Without this, the optimal play is to build
 * the cheapest junk bot and feed it to a loss, turning bot sacrifice into a grind path toward
 * ceiling raises — the exact failure the gate rule ({@code 02}) exists to prevent."
 *
 * <p>The breach has the same failure in a different costume: farm the softest live target you can
 * reach, repeatedly, for material. The same guard closes it, reading the same field —
 * {@code resolutionRecord.difficultyTier}. §2.2 is explicit that the tier gate is what makes any rate
 * safe: "the rate sets <em>pace</em>, never <em>reach</em>."
 *
 * <h2>⚠ Never count the resolutions</h2>
 *
 * This class reads one record's three gate fields and the running total, and nothing else. It never
 * looks at how many rows {@link GameSave#resolutions} holds. Counting is what count-gating is, and
 * {@code ResolutionRecord}'s javadoc calls reaching for a count over those rows "the exploit
 * arriving" — see Invariant I7 and {@code docs/design/02-unlock-gates.md} §2.4.
 *
 * <h2>What is not implemented, and why the shape still matters</h2>
 *
 * The bot-loss path does not exist here: solo has no bots ({@code docs/design/10-botnets.md} is
 * unimplemented). When it lands it should reuse this method and this gate rather than growing its
 * own, and the tier check should stay a single expression in a single place — I13 is one rule, and
 * two implementations of one rule is how a guard ends up applied on one path and not the other.
 */
public final class SalvageRules {

    private SalvageRules() {}

    /**
     * Awards material for a resolved attempt, if the gate opens.
     *
     * <p>Three conditions, all required: the attempt succeeded, the target was live or defended, and
     * the engagement was at or above {@link Balance#SCHEMATIC_MATERIAL_MIN_TIER}. The middle one is
     * {@code docs/design/02-unlock-gates.md} §2.4's live-or-dormant distinction doing the same work
     * it does for proof-of-skill: a dormant target is "still worth loot; never worth an unlock"
     * ({@code TargetState}'s own javadoc), and material is progress toward an unlock.
     *
     * @return units granted, or 0 when the gate did not open
     */
    public static int award(GameSave save, ResolutionState record) {
        if (record == null) {
            return 0;
        }
        boolean breached = BreachOutcome.BREACHED.name().equals(record.outcome);
        boolean live = TargetState.LIVE.name().equals(record.liveOrDormant);
        if (!breached || !live || record.difficultyTier < Balance.SCHEMATIC_MATERIAL_MIN_TIER) {
            return 0;
        }
        save.schematicMaterial += Balance.SCHEMATIC_MATERIAL_PER_BREACH;
        return Balance.SCHEMATIC_MATERIAL_PER_BREACH;
    }

    /** Material for one schematic — {@code docs/design/02-unlock-gates.md} §2.2's conversion rate. */
    public static int unlockCost() {
        return Balance.SCHEMATIC_MATERIAL_PER_UNLOCK;
    }

    /** How many more units the player needs. Never negative. */
    public static int remainingForUnlock(GameSave save) {
        return Math.max(0, unlockCost() - save.schematicMaterial);
    }
}
