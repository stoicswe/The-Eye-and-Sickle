package io.github.stoicswe.eyeandsickle.client.support;

import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.time.Clock;
import java.util.List;

/**
 * Characters for tests that are not about the tutorial.
 *
 * <h2>Why this exists</h2>
 *
 * {@code SoloGame.newCharacter} plants a foreign miner on every new rig. That is deliberate —
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
 * <p>Tests that ARE about the tutorial must call {@link SoloGame#open} directly and assert on the
 * parasite — see {@code SoloGameTest.Breach} in the solo module.
 */
public final class TestSaves {

    private TestSaves() {}

    /** A fresh character with the tutorial parasite and its host allocation removed. */
    public static SoloGame bare(SaveStore store, String handle, Clock clock) {
        SoloGame game = SoloGame.open(store, handle, clock);
        var rig = game.state().rig;
        // The allocation goes with the miner. Leaving it behind would hold the cycles with nothing
        // owning them, and the compute budget would stop reconciling — which is the one readout
        // docs/design/04 §3.1 depends on being exact.
        for (var miner : List.copyOf(rig.foreignMiners)) {
            rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
        }
        rig.foreignMiners.clear();
        return game;
    }
}
