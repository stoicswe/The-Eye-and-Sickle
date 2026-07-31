package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.breach.Rng;
import io.github.stoicswe.eyeandsickle.solo.state.RigState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Difficulty must have no <em>trend</em>.
 *
 * <p>{@code ChainState} is explicit that this chain's network hashrate never changes, so every
 * retarget's adjustment should land near 1.0 and difficulty should sit at
 * {@code Balance.chainDifficultyFor} give or take the couple of percent that 1440 random block times
 * are worth. It is also strongly mean-reverting by construction: the adjustment is
 * {@code expected / actual}, and {@code actual} is itself proportional to the current difficulty, so
 * one retarget pulls a displaced difficulty most of the way home. A drift that survives several
 * retargets is therefore not variance — it is a bug in how the window is being measured.
 *
 * <p>The symptom is a chain that feels stuck: difficulty 21% high makes the real block interval
 * ~17 minutes against a published ~14, so the mempool panel reads "running long" most of the time
 * and the strip's gaps stretch.
 */
class DifficultyDriftTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static SoloSave chainOnly() {
        SoloSave save = new SoloSave();
        save.rig = new RigState();
        save.rngSeed = 0x5EED_1234_5678_9ABCL;
        Rng rng = Rng.of(save);
        save.chain = ChainRules.genesis(T0, rng);
        rng.commit(save);
        return save;
    }

    /** How far from equilibrium a difficulty is, as a fraction. */
    private static double drift(SoloSave save) {
        return save.chain.difficulty / Balance.chainDifficultyFor(save.chain.networkHashrate) - 1.0d;
    }

    @Test
    @DisplayName("one long offline fill leaves difficulty at equilibrium")
    void offlineFillDoesNotDrift() {
        SoloSave save = chainOnly();
        Rng rng = Rng.of(save);
        // Six retarget windows' worth — long enough that a per-retarget bias compounds into
        // something obvious and short enough to run in well under a second.
        long seconds = 6L * Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
        var sync = ChainRules.sync(save, T0, T0.plusSeconds(seconds), rng);
        rng.commit(save);

        double interval = seconds / (double) sync.report().blocks();
        System.out.printf(
                "offline: %d blocks, %d retargets, mean interval %.1fs (target %d), "
                        + "difficulty %.2f (equilibrium %.2f, drift %+.2f%%)%n",
                sync.report().blocks(),
                sync.report().retargets(),
                interval,
                Balance.CHAIN_TARGET_BLOCK_SECONDS,
                save.chain.difficulty,
                Balance.chainDifficultyFor(save.chain.networkHashrate),
                drift(save) * 100);

        assertThat(sync.report().retargets()).isGreaterThanOrEqualTo(5);
        assertThat(drift(save))
                .as("difficulty must not trend — the network's hashrate never moves")
                .isBetween(-0.10d, 0.10d);
        assertThat(interval)
                .as("and the chain must actually produce blocks at its published interval")
                .isBetween(Balance.CHAIN_TARGET_BLOCK_SECONDS * 0.9d, Balance.CHAIN_TARGET_BLOCK_SECONDS * 1.1d);
    }

    @Test
    @DisplayName("the same span ticked online leaves difficulty at equilibrium too")
    void onlineTicksDoNotDrift() {
        SoloSave save = chainOnly();
        Rng rng = Rng.of(save);
        // ⚠ A COARSE tick, so six windows are affordable to simulate. Granularity is itself the
        // hypothesis under test — see the third case — so running only the fine one would measure
        // the wrong thing anyway.
        long step = 60L;
        long seconds = 6L * Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
        Duration tick = Duration.ofSeconds(step);
        Instant at = T0;
        int blocks = 0;
        for (long elapsed = 0; elapsed < seconds; elapsed += step) {
            at = at.plusSeconds(step);
            blocks += ChainRules.advanceNetwork(save, tick, at, rng).blocks();
        }
        rng.commit(save);

        double interval = seconds / (double) blocks;
        System.out.printf(
                "online(%ds): %d blocks, mean interval %.1fs, difficulty %.2f, drift %+.2f%%%n",
                step, blocks, interval, save.chain.difficulty, drift(save) * 100);

        assertThat(drift(save)).isBetween(-0.10d, 0.10d);
        assertThat(interval)
                .isBetween(Balance.CHAIN_TARGET_BLOCK_SECONDS * 0.9d, Balance.CHAIN_TARGET_BLOCK_SECONDS * 1.1d);
    }

    /**
     * ⚠ The tick interval must not change what the chain produces.
     *
     * <p>A player on a slow machine and a player on a fast one are on the same chain, and a rule
     * whose output depended on how often the client happened to call it would make the block
     * interval a hardware property.
     */
    /**
     * ⚠ A save's network hashrate must follow the balance constant, and difficulty must follow it.
     *
     * <p>Found on a real save: a character created 2026-07-26 was still on the 2352-cycle network,
     * where a character created two days later was on the 1680-cycle one. Nothing looked wrong — that
     * chain's difficulty had correctly converged to its <em>own</em> equilibrium — and mining income
     * is inversely proportional to the network's size, so the older character had been earning 71% of
     * what {@code docs/design/03-economy.md} §1 prices, silently, for the life of the character.
     *
     * <p>The rescale of difficulty is the half that is easy to leave out and expensive to get wrong:
     * shrinking the network alone stretches the block interval until the next retarget, and retargets
     * are 1440 blocks apart.
     */
    @Test
    @DisplayName("a save on an old network is re-tuned, and its block interval does not move")
    void anOldNetworkIsRetunedWithoutMovingTheInterval() {
        SoloSave save = chainOnly();
        // The network this game shipped with before the interval moved to fourteen minutes.
        double old = 2352L * Balance.HASHES_PER_CYCLE_SECOND;
        save.chain.networkHashrate = old;
        save.chain.difficulty = Balance.chainDifficultyFor(old);
        double intervalBefore = ChainRules.expectedSeconds(save.chain.difficulty, save.chain.networkHashrate);

        // What SoloGame.backfill does on load, as the rule rather than through the file layer.
        double factor = Balance.chainNetworkHashrate() / save.chain.networkHashrate;
        save.chain.networkHashrate = Balance.chainNetworkHashrate();
        save.chain.difficulty *= factor;

        assertThat(ChainRules.expectedSeconds(save.chain.difficulty, save.chain.networkHashrate))
                .as("the block interval must not move — a retarget is 1440 blocks away")
                .isCloseTo(intervalBefore, org.assertj.core.data.Offset.offset(1e-6d));
        assertThat(drift(save))
                .as("and the chain must land on the current equilibrium, not the old one")
                .isBetween(-0.001d, 0.001d);
    }

    @Test
    @DisplayName("the block rate does not depend on how often the client ticks")
    void tickGranularityDoesNotChangeTheRate() {
        long seconds = 2L * Balance.CHAIN_RETARGET_BLOCKS * Balance.CHAIN_TARGET_BLOCK_SECONDS;
        for (long step : new long[] {1L, 5L, 60L, 900L}) {
            SoloSave save = chainOnly();
            Rng rng = Rng.of(save);
            Duration tick = Duration.ofSeconds(step);
            Instant at = T0;
            int blocks = 0;
            for (long elapsed = 0; elapsed < seconds; elapsed += step) {
                at = at.plusSeconds(step);
                blocks += ChainRules.advanceNetwork(save, tick, at, rng).blocks();
            }
            rng.commit(save);
            double interval = seconds / (double) blocks;
            System.out.printf(
                    "  step %4ds -> %d blocks, interval %.1fs, drift %+.2f%%%n",
                    step, blocks, interval, drift(save) * 100);
            assertThat(interval)
                    .as("tick of %ds", step)
                    .isBetween(Balance.CHAIN_TARGET_BLOCK_SECONDS * 0.85d, Balance.CHAIN_TARGET_BLOCK_SECONDS * 1.15d);
        }
    }
}
