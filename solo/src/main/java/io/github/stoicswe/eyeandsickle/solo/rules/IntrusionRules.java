package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.proc.Disguise;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;

/**
 * Somebody else getting onto the player's rig — the one implementation, for every way it can happen.
 *
 * <h2>Why this is its own class</h2>
 *
 * Being counter-hacked has two causes now: a <b>sweep</b> that reached machines which noticed
 * ({@code NetRules}), and a <b>loud breach</b> that got answered ({@code BreachRules}). Those live in
 * two packages that must not depend on each other, and the planting itself is neither a network rule
 * nor a puzzle rule — it is a fact about the rig. Duplicating it would be worse than the coupling: a
 * parasite planted by one path and not dressed, or not charged heat, or not given an allocation,
 * would be a second class of parasite that behaves differently from the first and nobody would notice
 * until a player found one that could not be audited.
 *
 * <h2>⚠ What the caller still owns</h2>
 *
 * The <b>roll</b>. This plants; it never decides whether to. Sweeps roll at commission and freeze the
 * answer ({@code NetRules.beginSweep}); a breach rolls at resolution against the noise it made. Both
 * of those are decisions about <em>that</em> mechanic's fairness, and moving them here would put two
 * unrelated tuning knobs in a class that should have none.
 */
public final class IntrusionRules {

    private IntrusionRules() {}

    /**
     * Plants a foreign miner on the player's own rig and charges the heat the intrusion earned.
     *
     * <p>⚠ <b>The heat lands on the player, and Invariant I9 is not violated.</b> The player reached
     * another machine, which is an intrusive outbound action and heat-bearing under
     * {@code docs/design/01-core-resources.md} §3. What I9 protects is the <em>next</em> step: the
     * crack of the planted miner runs on the player's own rig and generates no heat on any outcome
     * ({@code docs/design/04-mining.md} §5.1). So being counter-hacked is not only a punishment — it
     * hands the player the safest teaching target in the game, on the already-built crack path.
     *
     * <p>The miner holds a real {@code DEPLOYED_MINER} allocation, which is what makes it findable by
     * the audit in §3.1: the cycle totals genuinely stop adding up. A rig too full to reserve gets the
     * miner <em>without</em> the allocation rather than no miner at all — the same fallback
     * {@code Targets.plantTutorialMiner} takes, because a parasite that declined to install because
     * the machine was busy would be the wrong lesson entirely.
     *
     * @param depth how deep the provocation was; sets the miner's tier, its appetite, whether it is
     *     rootkit-wrapped, and the heat
     * @param now the session clock. ⚠ Never {@code Instant.now()} — {@code deployedAt} and
     *     {@code lastAccruedAt} both default to it, and leaving them there dates a fresh parasite to
     *     the real world's present however the caller's clock is set
     * @return the planted miner, so the caller can name it in a log line
     */
    public static MinerState plantCounterHack(SoloSave save, int depth, Instant now) {
        MinerState miner = new MinerState();
        miner.tier = Math.max(1, Math.min(3, depth));
        miner.hostCycles = Balance.TUTORIAL_MINER_HOST_CYCLES + depth;
        miner.label = "unregistered process";
        miner.deployerHandle = "unknown";
        miner.rootkitWrapped = depth >= 3;
        miner.deployedAt = now;
        miner.lastAccruedAt = now;

        AllocationState allocation =
                ComputeRules.reserve(save.rig, ComputeConsumer.DEPLOYED_MINER, miner.label, miner.hostCycles);
        if (allocation != null) {
            allocation.startedAt = now;
            miner.allocationId = allocation.allocationId;
        }
        save.rig.foreignMiners.add(miner);

        // Dressed on the way in. A parasite planted by a counter-hack hides in the process table
        // exactly as well as the tutorial one — the log announces the EVENT, and finding the PROCESS
        // is still the player's job.
        Rng rng = Rng.of(save);
        Disguise.dress(save, miner, rng);
        rng.commit(save);

        int heat = Balance.netCounterHackHeat(depth);
        save.personalHeat = Math.min(Balance.PERSONAL_HEAT_MAX, save.personalHeat + heat);
        EventLog.warning(
                save,
                "rig",
                "something answered in the other direction: an unregistered process is running on "
                        + "your rig" + (heat > 0 ? ", and personal heat rose by " + heat : "")
                        + ". `scan` names it, the process table shows it, and cracking it costs no heat.",
                now);
        return miner;
    }
}
