package io.github.stoicswe.eyeandsickle.client.support;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.SaveStore;
import java.time.Clock;
import java.util.List;

/**
 * Characters for tests that are not about the tutorial.
 *
 * <h2>Why this exists</h2>
 *
 * {@code GameEngine.newCharacter} plants a foreign miner on every new rig. That is deliberate —
 * {@code docs/design/04} §5.1 makes cracking one the tutorial for the whole breach system, and
 * without it a fresh character has no reachable breach target at all, which would leave the game's
 * central pillar unreachable until the player discovers a node.
 *
 * <p>By <b>Invariant I6</b> a deployed miner spends the <em>host's</em> compute, so a brand-new rig
 * genuinely has {@code 100 - Balance.TUTORIAL_MINER_HOST_CYCLES} cycles free rather than 100. That
 * is the invariant working, not a bug — and it is also the thing that makes
 * {@code docs/design/04} §3.1's audit mechanic true on day one, because the ledger stops adding up
 * and there is finally a discrepancy to notice.
 *
 * <p>⚠ Most tests in this module are about something else entirely — exit statuses, shortcut
 * parity, an income projection — and they assert against a rig's free capacity as a convenient
 * constant. Rewriting each of those to {@code 100 - 6} would bury the thing under test beneath an
 * unrelated number and would have to be redone the day the tutorial's cost changes. Removing the
 * parasite keeps every assertion saying what it was written to say.
 *
 * <p>Tests that ARE about the tutorial must call {@link GameEngine#open} directly and assert on the
 * parasite — see {@code GameEngineTest.Breach} in the solo module.
 */
public final class TestSaves {

    private TestSaves() {}

    /** A fresh character with the tutorial parasite and its host allocation removed. */
    public static GameEngine bare(SaveStore store, String handle, Clock clock) {
        GameEngine game = GameEngine.open(store, handle, clock);
        var rig = game.state().rig;
        // The allocation goes with the miner. Leaving it behind would hold the cycles with nothing
        // owning them, and the compute budget would stop reconciling — which is the one readout
        // docs/design/04 §3.1 depends on being exact.
        for (var miner : List.copyOf(rig.foreignMiners)) {
            rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
        }
        rig.foreignMiners.clear();
        atTopOfLadder(game);
        return game;
    }

    /**
     * Puts the rig at the top of the compute ladder.
     *
     * <h2>⚠ A starting rig is 24 cycles as of 2026-08-06 and these tests were written against 100</h2>
     *
     * Exactly the same argument as removing the parasite, one number along. Most tests in this
     * module are about something else entirely — an exit status, a shortcut, an income projection —
     * and they allocate 40 or 80 cycles as a convenient constant. Against a 24-cycle rig every one
     * of those allocations is <b>refused</b>, the rig silently does nothing, and the failure surfaces
     * somewhere unrelated: a rate assertion, a task that was never created, a log line that never
     * appeared. Giving the fixture the rig its subject needs keeps each assertion saying what it was
     * written to say.
     *
     * <p>⚠ Grants the ITEMS rather than writing {@code totalCycles}. The ceiling is derived from what
     * the rig holds, and a written one is stomped by the next {@code reconcile} — which is the
     * anti-cheat property that derivation exists for. A fixture that fought it would work until the
     * first tick.
     *
     * <p>Tests that ARE about the ladder must build their own rig — see
     * {@code ComputeLadderTest} in the engine module.
     */
    private static void atTopOfLadder(GameEngine game) {
        for (var rung : io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()) {
            var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
            item.itemType = rung.itemType();
            item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
            game.state().items.add(item);
        }
        io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.reconcile(game.state());
    }
}
