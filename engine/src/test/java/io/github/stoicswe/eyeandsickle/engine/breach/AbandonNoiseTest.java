package io.github.stoicswe.eyeandsickle.engine.breach;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.net.SweepTier;
import io.github.stoicswe.eyeandsickle.engine.rules.NoiseRules;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Walking away from a live breach leaves a short burst of noise behind.
 *
 * <p>Until 2026-07-27 abandoning was the <em>quietest</em> possible exit — the breach's noise simply
 * stopped — which made "open a breach, read the board, leave if it looks ugly" a free reroll on
 * difficulty. The penalty is a window in which the rig is easier to find, not a number taken away.
 */
class AbandonNoiseTest {

    private static final class Rig {
        Instant now = Instant.parse("2026-07-27T09:00:00Z");
        final GameEngine game;

        Rig(Path dir) {
            game = GameEngine.open(
                    io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                    "op",
                    new java.time.Clock() {
                        public java.time.ZoneId getZone() {
                            return java.time.ZoneOffset.UTC;
                        }

                        public java.time.Clock withZone(java.time.ZoneId z) {
                            return this;
                        }

                        public Instant instant() {
                            return now;
                        }
                    });
            game.sweep(SweepTier.BASE);
            for (int i = 0; i < 30 && !game.state().tasks.isEmpty(); i++) {
                now = now.plusSeconds(5);
                game.tick();
            }
        }

        long noise() {
            return NoiseRules.outwardCycles(game.state(), now);
        }
    }

    @Test
    @DisplayName("abandoning spikes the noise, and it decays on its own")
    void abandoningSpikes(@TempDir Path dir) {
        Rig rig = new Rig(dir);
        rig.game.beginBreach(rig.game.breachTargets().get(0).targetId());
        long during = rig.noise();

        rig.game.abortBreach();
        long after = rig.noise();

        // Louder than the attempt was. That is the point: the exit is the conspicuous act.
        assertThat(after).isGreaterThan(during);
        assertThat(after).isGreaterThanOrEqualTo(Balance.BREACH_ABANDON_SPIKE_CYCLES);

        long seconds =
                Duration.between(rig.now, rig.game.state().noiseSpikeUntil).toSeconds();
        assertThat(seconds)
                .isBetween(Balance.BREACH_ABANDON_SPIKE_MIN_SECONDS, Balance.BREACH_ABANDON_SPIKE_MAX_SECONDS);

        // ⚠ It expires on the SESSION clock, not a countdown. A remaining-seconds field would pause
        // with the game and leave a spike waiting to be served the next time the client opened.
        rig.now = rig.now.plusSeconds(seconds + 1);
        assertThat(rig.noise()).isZero();
    }

    @Test
    @DisplayName("⚠ the spike stays below the cheapest sweep — the documented ordering holds")
    void quieterThanASweep() {
        // BREACH_NOISE_CEILING < spike < NET_SWEEP_BASE_NOISE. The exit is louder than anything the
        // attempt could do, and the cheapest sweep is still louder than the exit.
        assertThat(Balance.BREACH_ABANDON_SPIKE_CYCLES)
                .isGreaterThan(Balance.BREACH_NOISE_CEILING)
                .isLessThan(Balance.NET_SWEEP_BASE_NOISE);
    }

    @Test
    @DisplayName("⚠ a crack on your own rig never spikes — Invariant I9")
    void aCrackOnYourOwnRigIsSilent(@TempDir Path dir) {
        Rig rig = new Rig(dir);
        // Same reason resolve() zeroes a crack's heat: it is the player's own machine, and defending
        // your own rig never makes you more findable. A spike here would punish backing out of a
        // fight on your own hardware, which is the tutorial breach (04 §5.1).
        rig.game.state().activeBreach = null;
        BreachRulesTestSupport.spikeAsCrack(rig.game.state(), rig.now);
        assertThat(rig.game.state().noiseSpikeCycles).isZero();
        assertThat(rig.noise()).isZero();
    }
}
