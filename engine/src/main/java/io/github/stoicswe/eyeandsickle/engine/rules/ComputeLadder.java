package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.util.List;
import java.util.Optional;

/**
 * The rig's compute ceiling: 24 → 32 → 48 → 64.
 *
 * <h2>⚠ CAPACITY IS DERIVED FROM WHAT IS HELD, NEVER READ FROM THE SAVE'S OWN NUMBER</h2>
 *
 * {@code rig.totalCycles} is a <b>cache</b> of {@link #capacityOf}, reconciled on load and after
 * every upgrade. It is not the authority, and the reason is a bug this codebase has already shipped
 * once: {@code ChainState.networkHashrate} was a stored copy of a derived balance value, it went
 * stale against a re-tune, and a real character mined at 71% of the published rate <b>forever</b>
 * with no readout saying so. A stored capacity fails the same way and worse — it is also the number
 * a hand-edited save would raise to grant itself the whole ladder for free.
 *
 * <p>So the ceiling is a function of the upgrade items in the vault, and {@link #reconcile} is the
 * one place that writes it. {@code ComputeLadderTest.theCacheAlwaysAgreesWithTheLadder} pins that.
 *
 * <h2>⚠ THE FIRST RUNG IS BOUGHT AND THE REST ARE NOT — this is Invariant I1, amended</h2>
 *
 * I1 reads "compute is never purchasable with ethecoin", because otherwise mining buys mining
 * capacity and the master scarcity becomes a compounding flywheel. On explicit direction
 * (2026-08-06, {@code design/15} §3) exactly <b>one</b> rung is purchasable: 24 → 32, at
 * {@link Balance#COMPUTE_32_PRICE}.
 *
 * <p>⚠ <b>One rung cannot close the loop.</b> The flywheel needs mine → buy capacity → mine faster →
 * buy <em>more</em> capacity, and the step above 32 cannot be bought at any price. Money moves a
 * player up once, ever. That is a head start, not a compounding one — and the difference is the
 * entire safety argument, so it is enforced by a test rather than by this paragraph.
 */
public final class ComputeLadder {

    private ComputeLadder() {}

    /**
     * One step up the ladder.
     *
     * @param capacity what the rig can hold once this is applied
     * @param itemType the catalogue id of the upgrade that grants it
     * @param materials rare items the Compiler consumes to build it, beyond the schematic.
     *     ⚠ <b>Placeholders</b> — the content decision is open (see {@code design/15} AS-2). They are
     *     named rather than left empty so the Compiler has something to require and so the shape is
     *     visible; a real item list replaces these ids and nothing else.
     */
    public record Rung(long capacity, String itemType, List<String> materials) {}

    /**
     * The ladder above the starting rig, in order.
     *
     * <p>⚠ Derived from {@link Balance#COMPUTE_RUNGS} rather than restating it, so the capacities
     * and the item ids cannot drift apart — the id is built from the number.
     */
    public static List<Rung> rungs() {
        return List.of(
                new Rung(Balance.COMPUTE_RUNGS[1], "compute-32", List.of()),
                // ⚠ FILL-INS. See Rung.materials.
                new Rung(Balance.COMPUTE_RUNGS[2], "compute-48", List.of("rare-substrate", "coolant-cell")),
                new Rung(
                        Balance.COMPUTE_RUNGS[3],
                        "compute-64",
                        List.of("exotic-substrate", "coolant-cell", "lattice-core")));
    }

    /** The rung an upgrade item grants, if it is one. */
    public static Optional<Rung> rungFor(String itemType) {
        return rungs().stream().filter(r -> r.itemType().equals(itemType)).findFirst();
    }

    /**
     * What this rig's ceiling actually is, from what it holds.
     *
     * <p>⚠ The <b>highest</b> rung held, not a sum. Holding 48 and 64 is 64, not 112 — the upgrades
     * replace a ceiling rather than stacking onto one, and summing them would let a player who
     * acquired them out of order end up somewhere no rung exists.
     */
    public static long capacityOf(GameSave save) {
        long capacity = Balance.STARTING_CYCLES;
        if (save == null) {
            return capacity;
        }
        for (Rung rung : rungs()) {
            boolean held = save.items.stream().anyMatch(item -> rung.itemType().equals(item.itemType));
            if (held && rung.capacity() > capacity) {
                capacity = rung.capacity();
            }
        }
        return capacity;
    }

    /**
     * Writes the derived ceiling into the rig, and reports whether it moved.
     *
     * <p>Called on load and after an upgrade lands. Idempotent by construction — it computes from
     * the items every time, so calling it twice is calling it once.
     *
     * @return true if the ceiling changed, which is what tells the caller to log it and persist
     */
    public static boolean reconcile(GameSave save) {
        if (save == null || save.rig == null) {
            return false;
        }
        long derived = capacityOf(save);
        if (save.rig.totalCycles == derived) {
            return false;
        }
        save.rig.totalCycles = derived;
        return true;
    }

    /** The next rung this rig could climb to, or empty at the top. */
    public static Optional<Rung> next(GameSave save) {
        long at = capacityOf(save);
        return rungs().stream().filter(rung -> rung.capacity() > at).findFirst();
    }

    /**
     * Whether this rig already holds a rung's upgrade.
     *
     * <p>⚠ Used to refuse a second purchase of the same one. Items do not stack
     * ({@code rules/StorageRules}), so a duplicate would be a second thing in the vault granting
     * nothing — money for an item whose only property is a ceiling the rig already has.
     */
    public static boolean holds(GameSave save, String itemType) {
        return save != null && save.items.stream().anyMatch(item -> itemType.equals(item.itemType));
    }

    /**
     * Whether the rungs below this one are all held.
     *
     * <p>⚠ The ladder is climbed in order. Skipping to 64 with a schematic and nothing below it
     * would make the two purchases beneath it pointless, and — worse — would make the 24 → 32
     * amendment's "one rung, once" argument false by letting a player leave it unbought forever.
     */
    public static boolean rungsBelowAreHeld(GameSave save, Rung rung) {
        return rungs().stream()
                .filter(lower -> lower.capacity() < rung.capacity())
                .allMatch(lower -> holds(save, lower.itemType()));
    }
}
