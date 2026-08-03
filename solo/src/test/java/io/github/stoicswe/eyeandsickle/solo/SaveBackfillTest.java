package io.github.stoicswe.eyeandsickle.solo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.net.SweepTier;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A character created before the world generator existed can reach the network.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * {@code SoloSave.topology} is null on a save written before it was added, and the field was
 * documented as deliberately left that way so an old character would "keep working with an empty map
 * rather than being handed a freshly rolled world on load". The reasoning is right about
 * regeneration and wrong about the outcome, because a null topology is not a small world — it is
 * <em>no</em> world:
 *
 * <ul>
 *   <li>{@code NetRules.view} returns an empty map, so the graph, the list and {@code net} all show
 *       nothing, forever.
 *   <li>{@code NetRules.beginSweep} returns empty at every tier, so no sweep can ever run.
 *   <li>The refusal that reached the player said <em>"not enough available compute"</em> — naming a
 *       resource they had ninety cycles of, and sending them to fix something that was not broken.
 * </ul>
 *
 * <p>It reproduced on a real save. The three assertions below are, in order: the world arrives, the
 * sweep works, and nothing was rerolled.
 */
class SaveBackfillTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    /** A save from the build before topologies existed: real progress, no world. */
    private static SoloSave pretopology() {
        SoloSave save = SoloGame.newCharacter("operator", T0);
        save.topology = null;
        save.knownNodes.clear();
        save.ethecoinWei = Balance.ec("11.53");
        return save;
    }

    @Test
    @DisplayName("opening an old save brings up a world, and the sweep the player could not run now runs")
    void backfillsAndSweeps(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        store.save(pretopology());

        SoloGame game = SoloGame.open(store, "operator", new TestClock(T0));

        assertThat(game.hasNetwork()).isTrue();
        assertThat(game.net().vantageAddress()).isNotBlank();
        // The whole point. This returned empty before, and the client reported it as a compute
        // shortage on a rig that had plenty.
        assertThat(game.sweep(SweepTier.BASE)).isPresent();
    }

    @Test
    @DisplayName("the world is rolled from the save's own seed, so it is a backfill and not a reroll")
    void deterministicFromTheSavedSeed(@TempDir Path dir) {
        SoloSave first = pretopology();
        SoloSave second = pretopology();
        second.rngSeed = first.rngSeed;
        second.characterId = first.characterId;

        SaveStore storeA = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("a.json"));
        SaveStore storeB = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("b.json"));
        storeA.save(first);
        storeB.save(second);

        SoloGame a = SoloGame.open(storeA, "operator", new TestClock(T0));
        SoloGame b = SoloGame.open(storeB, "operator", new TestClock(T0));

        // Same seed, same world — which is what makes this a repair of a save rather than a new
        // character wearing the old one's balance.
        assertThat(a.state().topology.hosts.size())
                .isEqualTo(b.state().topology.hosts.size());
        assertThat(a.state().topology.playerAddress).isEqualTo(b.state().topology.playerAddress);
    }

    @Test
    @DisplayName("a save from before the chain existed joins it, pooled, at its current height")
    void backfillsTheChain(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloSave old = pretopology();
        old.chain = null;
        store.save(old);

        SoloGame game = SoloGame.open(store, "operator", new TestClock(T0));

        // Joining a chain with a past rather than starting one. A height of 0 would say the chain
        // had been waiting for this player, which is the opposite of what a shared ledger is.
        assertThat(game.state().chain).isNotNull();
        // 124 blocks of history, all of them inspectable — the chain existed before this character
        // and says so. See Balance.CHAIN_START_HEIGHT.
        assertThat(game.mining().height()).isEqualTo(io.github.stoicswe.eyeandsickle.solo.Balance.CHAIN_START_HEIGHT);
        assertThat(game.mining().difficulty()).isPositive();
        // ⚠ Pooled, not solo. A character who predates the choice must not be opted into the
        // lottery by a migration — I4 makes self-mining the floor, and a floor that sometimes pays
        // nothing is not one.
        assertThat(game.mining().mode()).isEqualTo(io.github.stoicswe.eyeandsickle.protocol.game.MiningMode.POOLED);
    }

    @Test
    @DisplayName("a save that already has a world is left exactly as it was")
    void neverRegenerates(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloSave save = SoloGame.newCharacter("operator", T0);
        store.save(save);

        int hosts = save.topology.hosts.size();
        String player = save.topology.playerAddress;

        SoloGame reopened = SoloGame.open(store, "operator", new TestClock(T0));
        SoloGame twice = SoloGame.open(store, "operator", new TestClock(T0));

        // The generator's idempotence guard is what makes the backfill safe to run on every open.
        // If it ever stopped holding, a player's world would change under them every launch.
        assertThat(reopened.state().topology.hosts).hasSize(hosts);
        assertThat(reopened.state().topology.playerAddress).isEqualTo(player);
        assertThat(twice.state().topology.playerAddress).isEqualTo(player);
    }

    @Test
    @DisplayName("a breach still open when the game closed is abandoned as an aborted attempt")
    void breachesDoNotSurviveASession(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloSave save = SoloGame.newCharacter("operator", T0);
        // The audit that makes the tutorial parasite a target; see SoloGameTest for the pipeline.
        save.rig.foreignMiners.getFirst().discovered = true;
        var target = io.github.stoicswe.eyeandsickle.solo.breach.Targets.available(save)
                .getFirst();
        io.github.stoicswe.eyeandsickle.solo.breach.BreachRules.begin(save, target, T0);
        assertThat(save.activeBreach).isNotNull();
        assertThat(save.activeBreach.outcome).isEmpty();
        store.save(save);

        SoloGame reopened = SoloGame.open(store, "operator", new TestClock(T0));

        // ⚠ Recorded, not deleted. Clearing activeBreach outright is a line shorter and hands the
        // player a free escape from a losing attempt: quit, and it never happened. Every other roll
        // in this engine is frozen precisely so reloading cannot undo it, and an attempt is no
        // different — so quitting mid-breach costs exactly what walking away costs.
        assertThat(reopened.state().resolutions).isNotEmpty();
        assertThat(reopened.state().resolutions.getLast().outcome).isEqualTo("ABORTED");
        // And the player comes back to the target list, not to a slate for an attempt they never
        // saw end. The log line is where "this happened while you were away" belongs.
        //
        // ⚠ The wording is shared with closing the breach window, which abandons an attempt by the
        // same method: closing the console and closing the client are the same gesture as far as the
        // attempt is concerned, and two implementations of "abandon" would be two chances for one of
        // them to forget to release the cycles.
        assertThat(reopened.breachSnapshot()).isEmpty();
        assertThat(reopened.state().log.stream().map(e -> e.message))
                .anySatisfy(message -> assertThat(message).contains("abandoned"));
    }

    @Test
    @DisplayName("a breach that had already resolved is left alone, so its outcome can still be read")
    void resolvedBreachesSurvive(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloSave save = SoloGame.newCharacter("operator", T0);
        save.rig.foreignMiners.getFirst().discovered = true;
        var target = io.github.stoicswe.eyeandsickle.solo.breach.Targets.available(save)
                .getFirst();
        io.github.stoicswe.eyeandsickle.solo.breach.BreachRules.begin(save, target, T0);
        io.github.stoicswe.eyeandsickle.solo.breach.BreachRules.abort(save, T0);
        store.save(save);

        // The outcome slate is where a loss becomes comprehensible (docs/design/05 §1 constraint 4).
        // A player who quit rather than read it should still get to.
        assertThat(SoloGame.open(store, "operator", new TestClock(T0)).breachSnapshot())
                .isPresent();
    }

    @Test
    @DisplayName("abandoning on demand is the same act as abandoning on load, and frees the cycles")
    void abandonOnDemand(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloGame game = SoloGame.open(store, "operator", new TestClock(T0));
        game.state().rig.foreignMiners.getFirst().discovered = true;
        var target = game.breachTargets().getFirst();
        assertThat(game.beginBreach(target.targetId()).applied()).isTrue();

        long heldDuringBreach =
                io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules.availableCycles(game.state().rig);

        // What closing the breach window does. Same method the load-time path uses, because closing
        // the console and closing the client are the same gesture as far as the attempt is concerned.
        assertThat(game.abandonBreach()).isTrue();

        assertThat(game.breachSnapshot()).isEmpty();
        assertThat(game.state().resolutions.getLast().outcome).isEqualTo("ABORTED");
        // The cycles are released onto the thermal curve rather than held forever by a console
        // nobody is sitting at — which is the whole reason this happens on close at all.
        assertThat(io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules.recoveringCycles(game.state().rig))
                .isPositive();
        assertThat(io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules.availableCycles(game.state().rig))
                .isEqualTo(heldDuringBreach);
    }

    @Test
    @DisplayName("abandoning when nothing is running is a no-op, not a refusal")
    void abandonIsSilentWhenIdle(@TempDir Path dir) {
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                "operator",
                new TestClock(T0));
        // Closing an idle breach window is a perfectly ordinary thing to do, and complaining about
        // it would be the client narrating its own bookkeeping.
        assertThat(game.abandonBreach()).isFalse();
        assertThat(game.state().resolutions).isEmpty();
    }

    @Test
    @DisplayName("a resolved breach is not abandoned — its slate is still the player's to read")
    void resolvedIsNotAbandoned(@TempDir Path dir) {
        SoloGame game = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                "operator",
                new TestClock(T0));
        game.state().rig.foreignMiners.getFirst().discovered = true;
        game.beginBreach(game.breachTargets().getFirst().targetId());
        game.abortBreach();

        assertThat(game.abandonBreach()).isFalse();
        assertThat(game.breachSnapshot()).isPresent();
    }

    @Test
    @DisplayName("an old save has no filing and gets an empty one rather than a null")
    void filingIsInitialised(@TempDir Path dir) {
        SaveStore store = new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json"));
        SoloSave save = pretopology();
        save.netFolders = null;
        store.save(save);

        SoloGame game = SoloGame.open(store, "operator", new TestClock(T0));
        // Empty rather than seeded: a folder is the player's own decision and there is no default
        // filing that would not be somebody's clutter.
        assertThat(game.folders()).isEmpty();
        assertThat(game.state().netFolders).isNotNull().isEmpty();
    }
}
