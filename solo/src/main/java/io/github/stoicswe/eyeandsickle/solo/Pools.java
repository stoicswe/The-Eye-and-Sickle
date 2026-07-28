package io.github.stoicswe.eyeandsickle.solo;

import io.github.stoicswe.eyeandsickle.protocol.game.MiningPool;
import io.github.stoicswe.eyeandsickle.protocol.game.PoolScheme;
import java.util.List;

/**
 * The pools on this chain. <strong>[PROPOSAL]</strong> — {@code docs/design/04-mining.md} §1.3a.
 *
 * <h2>⚠ Nothing here dominates anything else, and that is the whole design</h2>
 *
 * There are exactly two axes and they pull against each other:
 *
 * <ul>
 *   <li><b>Fee</b> is expected income, directly and only. A 0.5% pool really does pay more than a
 *       3.5% pool, every hour, forever.
 *   <li><b>Steadiness</b> is variance, and under {@link PoolScheme#PPLNS} it comes from the pool's
 *       <em>size</em>: you are paid when the pool finds a block, so a pool with 5% of the chain pays
 *       you every three hours or so. Under {@link PoolScheme#PPS} it comes from the share target
 *       instead, which is why a small PPS pool smooths just as well as a large one.
 * </ul>
 *
 * So the cheapest pool on this list is also the lumpiest, the steadiest is the priciest, and a
 * player who reads only the fee column will pick the one that behaves most like the solo mining they
 * were trying to avoid. That is a real lesson about real pools and it is the reason the list is not
 * simply sorted by fee.
 *
 * <h2>Why the shares do not add to 100%</h2>
 *
 * They come to 91%. The rest is solo miners and operations too small to list — which is roughly what
 * a real chain looks like, and it is also what keeps a player's own solo mining meaningful: the
 * unpooled remainder is a real place to be rather than an empty one.
 *
 * <h2>⚠ Every PPLNS pool must out-hash a maxed player rig</h2>
 *
 * A PPLNS payout is {@code playerHashrate / poolHashrate} of a block, clamped at 1. If a rig ever
 * grew past its own pool, the clamp would fire and the pool would quietly behave like solo mining
 * with a fee attached — the worst of both, and silent. The chain is 1680 cycle-equivalents, so a
 * 100-cycle rig is 6% of it; the smallest PPLNS pool here is 12%, which leaves room for a rig to
 * roughly double before anything degenerates. {@code MiningChainTest.pplnsPoolsOutHashAMaxedRig}
 * fails the build if a re-tune breaks that, because nothing else would notice.
 *
 * <h2>⚠ THE_COMMONS is the anchor and must stay at the economy's fee</h2>
 *
 * {@code docs/design/03-economy.md} §1 prices self-mining at 0.4 EC per cycle-hour, and
 * {@link Balance#chainNetworkHashrate()} is derived from that figure and {@link Balance#POOL_FEE}.
 * The default pool's fee must equal {@code POOL_FEE} or the documented rate stops being the rate a
 * new character actually gets. Every other pool's fee is a deliberate few tenths of a percent either
 * side of it — the whole spread is ±1.5%, which is a real choice and not a re-tune.
 */
public final class Pools {

    private Pools() {}

    /** The pool a character mines with until they choose otherwise. */
    public static final String DEFAULT_ID = "commons";

    private static final List<MiningPool> ALL = List.of(
            new MiningPool(
                    "commons",
                    "THE COMMONS",
                    PoolScheme.PPS,
                    Balance.POOL_FEE_BASIS_POINTS,
                    0.22d,
                    30.0d,
                    "A co-operative. Unremarkable, dependable, and it has never once explained itself.",
                    ""),
            new MiningPool(
                    "meridian",
                    "MERIDIAN CLEARING",
                    PoolScheme.PPS,
                    350,
                    0.32d,
                    15.0d,
                    "Institutional. Pays like a clock, charges like one, and files everything.",
                    "A third of the chain sits here. A pool past half could rewrite history on its "
                            + "own; nobody is obliged to tell you when it gets close."),
            new MiningPool(
                    "pale-lantern",
                    "PALE LANTERN",
                    PoolScheme.PPS,
                    250,
                    0.07d,
                    45.0d,
                    "One operator, one rack, a very good uptime record.",
                    "Small, and pay-per-share means the operator is fronting your income out of "
                            + "their own pocket through every unlucky week."),
            new MiningPool(
                    "glass-teeth",
                    "GLASS TEETH",
                    PoolScheme.PPLNS,
                    100,
                    0.18d,
                    30.0d,
                    "Cheap because it promises nothing. You are paid out of what it finds.",
                    ""),
            new MiningPool(
                    "small-hours",
                    "SMALL HOURS",
                    PoolScheme.PPLNS,
                    50,
                    0.12d,
                    60.0d,
                    "The lowest fee on the chain, and the smallest operation that will still pay you out of real blocks.",
                    "Paid only when the pool finds a block, and at this size that is about once "
                            + "every two hours. Cheapest is not steadiest."));

    public static List<MiningPool> all() {
        return ALL;
    }

    /** The pool with this id, or the default — never null, and never a throw on a hand-edited save. */
    public static MiningPool byId(String id) {
        for (MiningPool pool : ALL) {
            if (pool.id().equalsIgnoreCase(id)) {
                return pool;
            }
        }
        return defaultPool();
    }

    public static MiningPool defaultPool() {
        for (MiningPool pool : ALL) {
            if (pool.id().equals(DEFAULT_ID)) {
                return pool;
            }
        }
        return ALL.getFirst();
    }

    /** Whether an id names a pool at all — for a command that has to refuse a typo. */
    public static boolean exists(String id) {
        for (MiningPool pool : ALL) {
            if (pool.id().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }
}
