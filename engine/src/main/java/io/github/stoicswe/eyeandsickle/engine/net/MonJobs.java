package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;

/**
 * Who is watching a bridge, and at what tier — {@code docs/design/17-bridges-and-surveillance.md} §4.
 *
 * <p>A MonJob is a monitoring job left on a bridge. Tier 1 tells its owner that somebody crossed;
 * tier 2 tells the owner <em>and tells the intruder they were Watched</em>. This class answers the
 * NPC half: which bridges out in the world already carry one, before the player ever arrives.
 *
 * <h2>⚠ DERIVED, NEVER DRAWN, NEVER STORED — and each of those three is a separate trap</h2>
 *
 * <ul>
 *   <li><b>Never drawn.</b> {@code TopologyGenerator}'s draw count is a pure function of the world's
 *       shape, so one {@code nextDouble()} per bridge would <b>re-roll every existing world</b> —
 *       every name, every detect roll, every document — and {@code SweepDeterminismTest} asserts the
 *       exact number of draws a world consumes. {@code NpcNames} records this trap as having bitten
 *       <em>twice</em>. Everything here goes through {@link AddressHash}.
 *   <li><b>Never stored.</b> A field on {@code HostState} would be a second copy of a derived value,
 *       free to drift from the rule that produced it — {@code ChainState.networkHashrate} is the one
 *       that already cost a real character 29% of their income, silently, for weeks.
 *   <li><b>Never re-evaluated per look.</b> {@code NetRules}' rule is absolute: <em>"Detection is a
 *       roll made once, at world generation, and stored. Nothing here draws for detection, ever."</em>
 *       A hash satisfies it exactly — scouting the same bridge twice cannot give two answers, and
 *       quitting without saving changes nothing.
 * </ul>
 *
 * <h2>⚠ BRIDGES ONLY</h2>
 *
 * A MonJob is <em>a route the bridge itself keeps</em> rather than a file on a filesystem, which is
 * why bridges and only bridges can carry one (§4.2). It is also what makes it un-removable by anyone
 * but its owner: if it were a file, {@code Repac.delete}'s rule and {@code AccessLog}'s would both
 * have opinions about who may erase it.
 *
 * <h2>⚠ This is the NPC half only</h2>
 *
 * Player-placed MonJobs are save state — somebody chose to put one there, and the owner can take it
 * off again (§4.2, MJ-1) — so they will live in {@code GameSave}, not here. Nothing in this class
 * knows about them, and a caller asking "is this bridge monitored" has to consider both.
 */
public final class MonJobs {

    private MonJobs() {}

    /** Salt for the presence roll. See {@link AddressHash#unitOf}. */
    private static final String PRESENCE = "monjob";

    /**
     * ⚠ A DIFFERENT SALT FROM {@link #PRESENCE}, and that is load-bearing rather than tidy.
     *
     * <p>Reusing one would tie the two answers together: the monitored bridges would be exactly the
     * ones whose value sat in a particular band, so tier 2 would land on the <em>same</em> bridges
     * that were most likely to be monitored at all — a correlation a player would eventually read
     * even without being able to name it. Two salts make the questions independent.
     */
    private static final String TIER = "monjob-tier";

    /** Tier 1 watches and says nothing to the intruder. Tier 2 watches and tells them. */
    public static final int TIER_SILENT = 1;

    public static final int TIER_ANNOUNCING = 2;

    /**
     * Whether an NPC has a MonJob on this bridge.
     *
     * <p>Always false for anything that is not a {@code BRIDGE}, and always false at home — see
     * {@link Balance#MONJOB_DENSITY_HOME} for why the home floor is its own named constant.
     *
     * @param depth the server's {@code depthFromHome}
     */
    public static boolean watched(HostState host, int depth) {
        if (host == null || !HostKind.BRIDGE.name().equals(host.kind)) {
            return false;
        }
        return AddressHash.unitOf(host.address, PRESENCE) < Balance.monJobDensity(depth);
    }

    /**
     * The tier of the MonJob on this bridge, or {@code 0} if there is none.
     *
     * <p>⚠ Asks {@link #watched} first rather than trusting the caller. A tier for an absent MonJob is
     * a number that reads as real everywhere it is displayed, and the two questions are answered from
     * different salts, so nothing about the tier value implies the job exists.
     */
    public static int tier(HostState host, int depth) {
        if (!watched(host, depth)) {
            return 0;
        }
        return AddressHash.unitOf(host.address, TIER) < Balance.monJobTierTwoShare(depth)
                ? TIER_ANNOUNCING
                : TIER_SILENT;
    }

    /**
     * Whether crossing this bridge would tell the intruder they were seen.
     *
     * <p>The one question the interface actually asks, named so that call sites read as the rule
     * rather than as a comparison somebody has to decode.
     */
    public static boolean announces(HostState host, int depth) {
        return tier(host, depth) == TIER_ANNOUNCING;
    }
}
