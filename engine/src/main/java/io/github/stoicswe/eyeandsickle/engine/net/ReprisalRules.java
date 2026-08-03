package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.rules.AccessLog;
import io.github.stoicswe.eyeandsickle.engine.rules.EventLog;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.MinerState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import java.time.Instant;
import java.util.List;

/**
 * What a machine does when it notices you looking at it.
 *
 * <h2>Why being detected has to cost something other than the scan</h2>
 *
 * A detection that merely wasted the scan would make depth a pure expected-value calculation: run the
 * deepest one, eat the occasional wasted cycles, and the information is free in the long run. The
 * decision only exists if being seen can cost more than the thing you were doing when you were seen.
 *
 * <p>So a target that notices gets a turn. Three of them, in rising order of how much they take:
 *
 * <ul>
 *   <li><b>Nothing but memory.</b> It logs your vantage and hardens. Most detections are this.
 *   <li><b>Theft.</b> It reaches into your Downloads and takes a package. ⚠ <b>Downloads only</b> —
 *       see below.
 *   <li><b>A miner.</b> It plants one on your rig, which costs you cycles until you remove it.
 * </ul>
 *
 * <h2>⚠ It can only take what is in DOWNLOADS, and that is Invariant I12's line</h2>
 *
 * A reprisal reaches the download folder and stops. It cannot touch the vault, because vault capacity
 * is the one protection that is never purchasable ({@code docs/design/01-core-resources.md} §6) and a
 * counter-attack that emptied it would make the tiers decorative. The lesson is the one the tier
 * system exists to teach: <b>a package left in the folder it downloaded into is exposed</b>, and
 * moving it is the player's job.
 *
 * <h2>⚠ A planted miner is removed through the breach board, not through a new mechanic</h2>
 *
 * {@code solo/breach/Targets} already enumerates {@code save.rig.foreignMiners} as breach targets, so
 * a miner planted here is removed by exactly the puzzle {@code docs/design/04-mining.md} §5.1 says
 * removes one — the same board the tutorial parasite teaches. Nothing new is needed and nothing new
 * should be added: two different minigames for "get this thing off my machine" would be two things to
 * learn for one act.
 *
 * <p>⚠ It draws the <b>host's</b> cycles, not the player's, by Invariant <b>I6</b> — which here means
 * the player's own rig is the host. That is the whole cost: the rig is smaller until it is dealt with.
 */
public final class ReprisalRules {

    private ReprisalRules() {}

    /** What the target did about it. */
    public enum Response {
        /** Logged the vantage and hardened. No material loss. */
        NOTED,

        /** Took a package out of the download folder. */
        STOLE,

        /** Planted a miner on the player's rig. */
        PLANTED
    }

    /** What happened, and what it took. */
    public record Reprisal(Response response, String took, String message) {

        static Reprisal noted(String message) {
            return new Reprisal(Response.NOTED, "", message);
        }
    }

    /**
     * How much of the player's rig a planted miner takes.
     *
     * <p>Matched to the tutorial parasite, so a player who has cracked one already knows what this
     * costs and how to be rid of it. A reprisal that planted something bigger than the thing the
     * tutorial taught them to remove would be teaching the lesson and then changing the exam.
     */
    public static final long PLANTED_MINER_CYCLES = Balance.TUTORIAL_MINER_HOST_CYCLES;

    /**
     * Rolls the target's answer to a detected scan.
     *
     * <p>⚠ The draw happens <b>unconditionally</b> and before anything branches on it, so the RNG
     * stream does not depend on the outcome — {@code Rng}'s contract that a stored seed is not a
     * replay.
     *
     * <p>Weighted toward doing nothing. A detection that always cost something would make the whole
     * scan ladder feel like a trap rather than a risk, and most of the value of being noticed is
     * knowing that you were.
     *
     * @param host the machine that noticed. A <b>defended</b> one hits back harder — that flag is
     *     what {@code docs/design/09} spends on actually being dangerous rather than merely closed.
     */
    public static Reprisal answer(GameSave save, HostState host, Rng rng, Instant now) {
        int roll = rng.nextInt(100);
        boolean dangerous = host != null && host.defended;
        // Undefended: 80% nothing, 15% theft, 5% a miner. Defended: 45 / 30 / 25.
        int quiet = dangerous ? 45 : 80;
        int thieving = dangerous ? 75 : 95;

        String from = host == null ? "somewhere" : host.address;
        if (roll < quiet) {
            EventLog.notice(
                    save, "net", from + " noticed the scan and logged where it came from. Nothing was taken.", now);
            return Reprisal.noted("noticed, and let it go — this time.");
        }
        if (roll < thieving) {
            return steal(save, from, now);
        }
        return plant(save, from, rng, now);
    }

    /**
     * Takes one package out of the download folder.
     *
     * <h2>⚠ Downloads ONLY, and the newest one</h2>
     *
     * The vault is untouchable — see the class note. Taking the newest is deliberate rather than
     * random: it is almost always the thing the player just bought or just stole, which makes the
     * loss legible ("they took the tool I was about to install") instead of a silent decrement of an
     * inventory nobody was looking at. A loss the player cannot name teaches nothing.
     */
    private static Reprisal steal(GameSave save, String from, Instant now) {
        StoredFileState took = null;
        for (StoredFileState file : save.files) {
            if (!file.directory.endsWith("/Downloads")) {
                continue;
            }
            if (took == null || file.at.isAfter(took.at)) {
                took = file;
            }
        }
        if (took == null) {
            EventLog.notice(
                    save,
                    "net",
                    from + " answered the scan and went through your downloads. There was nothing "
                            + "in there to take.",
                    now);
            return Reprisal.noted("came looking, and found the download folder empty.");
        }
        save.files.remove(took);
        // ⚠ Logged as an access with the address it came from, so the theft is legible after the
        // fact rather than being a file that silently is not there any more. AccessLog is the
        // counter-forensics surface and this is exactly the kind of event it exists for.
        AccessLog.record(save, from, "took", took.path(), now);
        EventLog.warning(
                save,
                "net",
                from + " answered the scan and took " + took.name + " out of your downloads. "
                        + "Anything left in that folder is reachable; the vault is not.",
                now);
        return new Reprisal(Response.STOLE, took.name, "took " + took.name + " out of your download folder.");
    }

    /**
     * Plants a miner on the player's rig.
     *
     * <p>Removed through the breach board like any other foreign miner — {@code solo/breach/Targets}
     * already lists them, so this needs no new mechanic and deliberately does not get one.
     */
    private static Reprisal plant(GameSave save, String from, Rng rng, Instant now) {
        // One at a time. A rig carrying three of these is not a harder problem, it is the same
        // problem three times, and it turns a bad roll into an evening of identical breaches.
        if (!save.rig.foreignMiners.isEmpty()) {
            EventLog.warning(
                    save,
                    "net",
                    from + " answered the scan and probed your rig. It found the parasite already "
                            + "there and left it alone.",
                    now);
            return Reprisal.noted("probed the rig, and found somebody else had already been.");
        }
        // ⚠ Built the way the tutorial parasite is built, allocation and disguise included. A miner
        // that skipped the ComputeRules.reserve would cost the player NOTHING — Invariant I6 puts a
        // deployed miner's cost on the host, and here the host is the player's own rig, so the
        // reservation IS the entire consequence. One without it is a log line pretending to be a
        // problem. And one without a disguise sits in the process table under its own name, which
        // makes the audit it is supposed to reward pointless.
        MinerState miner = new MinerState();
        miner.hostCycles = PLANTED_MINER_CYCLES;
        miner.tier = Balance.TUTORIAL_MINER_TIER;
        miner.label = "unregistered process";
        miner.deployerHandle = from;
        miner.deployedAt = now;
        miner.lastAccruedAt = now;
        var allocation = io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules.reserve(
                save.rig,
                io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer.DEPLOYED_MINER,
                miner.label,
                miner.hostCycles);
        if (allocation != null) {
            allocation.startedAt = now;
            miner.allocationId = allocation.allocationId;
        }
        io.github.stoicswe.eyeandsickle.engine.proc.Disguise.dress(save, miner, rng);
        save.rig.foreignMiners.add(miner);
        AccessLog.record(save, from, "planted a miner", "/proc/" + miner.label, now);
        EventLog.warning(
                save,
                "net",
                from + " answered the scan by planting a miner on your rig — "
                        + PLANTED_MINER_CYCLES + " of your cycles are now working for somebody else. "
                        + "`crack` it off, or the audit window will show you where it is.",
                now);
        return new Reprisal(
                Response.PLANTED,
                from,
                "planted a miner on your rig. It is drawing " + PLANTED_MINER_CYCLES + " cycles.");
    }

    /** Every foreign miner currently squatting, for a readout that wants to name them. */
    public static List<MinerState> squatters(GameSave save) {
        return save == null ? List.of() : List.copyOf(save.rig.foreignMiners);
    }
}
