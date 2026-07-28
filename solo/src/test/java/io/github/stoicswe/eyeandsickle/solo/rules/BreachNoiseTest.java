package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.state.BreachState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A breach is heard, and it is heard less than a sweep.
 *
 * <h2>The ordering is the balance statement</h2>
 *
 * A sweep touches every machine within reach and announces itself to all of them; a breach is one
 * connection to one machine. <b>The cheapest sweep must be louder than the worst breach</b>, or the
 * sweep ladder's price — two cycles for the loudest act in the game — stops making sense. That is
 * asserted here rather than left to whoever next re-tunes either number.
 */
class BreachNoiseTest {

    private static final Instant T0 = Instant.parse("2026-07-27T09:00:00Z");

    private static SoloSave rig() {
        SoloSave save = new SoloSave();
        save.rig.totalCycles = 100L;
        return save;
    }

    private static BreachState breach(SoloSave save, boolean crack, int noise) {
        return breach(save, crack, noise, "BREACH_PROTOCOL");
    }

    private static BreachState breach(SoloSave save, boolean crack, int noise, String puzzleClass) {
        BreachState breach = new BreachState();
        breach.minerCrack = crack;
        breach.noise = noise;
        breach.puzzleClass = puzzleClass;
        save.activeBreach = breach;
        return breach;
    }

    @Nested
    @DisplayName("while it is open")
    class Live {

        @Test
        @DisplayName("an offensive breach is audible from the moment it opens")
        void neverSilent() {
            SoloSave save = rig();
            breach(save, false, 0);
            // Being inside a machine that is not yours is an act, and docs/design/08 §1 charges acts.
            // Without the floor the safest possible play would be to sit in a breach indefinitely.
            assertThat(NoiseRules.outwardCycles(save, T0)).isEqualTo(Balance.BREACH_NOISE_FLOOR);
        }

        @Test
        @DisplayName("it gets louder as the player does louder things in it")
        void climbsWithChoices() {
            SoloSave save = rig();
            BreachState breach = breach(save, false, 0);
            long quiet = NoiseRules.outwardCycles(save, T0);

            breach.noise += Balance.NOISE_BYPASS;
            long afterBypass = NoiseRules.outwardCycles(save, T0);

            // The same choice docs/design/05 §4 prices as trace INSIDE the puzzle, showing up outside
            // it. Having both is what stops "bypass everything" being free once the trace bar is
            // survivable.
            assertThat(afterBypass).isGreaterThan(quiet);
        }

        @Test
        @DisplayName("however badly it goes, the cheapest sweep is still louder")
        void quieterThanASweep() {
            SoloSave save = rig();
            breach(save, false, 10_000);
            assertThat(NoiseRules.outwardCycles(save, T0))
                    .isEqualTo(Balance.BREACH_NOISE_CEILING)
                    .isLessThan(Balance.NET_SWEEP_BASE_NOISE);
        }

        @Test
        @DisplayName("an offset cipher is louder than a protocol grid that did the same things")
        void theCipherIsLouder() {
            SoloSave grid = rig();
            breach(grid, false, 8, "BREACH_PROTOCOL");
            SoloSave cipher = rig();
            breach(cipher, false, 8, "OFFSET_CIPHER");

            // ⚠ This is the cipher's price for having no clock. Breach Protocol is bounded by its
            // buffer, so it ends either way in a handful of picks; the cipher lets a player sit and
            // subtract for as long as they like. What answers "why not take all day" is that all day
            // is spent on somebody else's wire.
            assertThat(NoiseRules.outwardCycles(cipher, T0))
                    .isGreaterThan(NoiseRules.outwardCycles(grid, T0));
        }

        @Test
        @DisplayName("the ceiling binds both classes, so patience is never unboundedly expensive")
        void theCeilingStillHolds() {
            SoloSave save = rig();
            breach(save, false, 10_000, "OFFSET_CIPHER");
            // The multiplier scales the points, not the cap. A cipher that could out-shout a sweep
            // would make the sweep ladder's price read as a mistake — see quieterThanASweep.
            assertThat(NoiseRules.outwardCycles(save, T0))
                    .isEqualTo(Balance.BREACH_NOISE_CEILING)
                    .isLessThan(Balance.NET_SWEEP_BASE_NOISE);
        }

        @Test
        @DisplayName("a crack is silent, however loud the puzzle got — Invariant I9")
        void crackIsSilent() {
            SoloSave save = rig();
            breach(save, true, 500);
            // A crack runs on the player's own rig; nothing leaves the machine, so there is nothing
            // for anyone to hear. That is what makes it safe to lose repeatedly and therefore usable
            // as the tutorial (docs/design/04 §5.1) — a crack that ticked the meter would undo it.
            assertThat(NoiseRules.outwardCycles(save, T0)).isZero();
        }

        @Test
        @DisplayName("a resolved breach is silent — noise is a rate, heat is what it leaves behind")
        void resolvedIsSilent() {
            SoloSave save = rig();
            BreachState breach = breach(save, false, 40);
            assertThat(NoiseRules.outwardCycles(save, T0)).isPositive();

            breach.outcome = "BREACHED";
            // The attempt is over and the connection is closed. What a loud one leaves behind is
            // heat, which is persisted and charged by different rules.
            assertThat(NoiseRules.outwardCycles(save, T0)).isZero();
        }
    }

    @Nested
    @DisplayName("what it risks")
    class CounterHack {

        @Test
        @DisplayName("home never bites back, however loud the breach was")
        void depthZeroIsSafe() {
            // The same rule NET_COUNTER_HACK_HOME fixes for sweeps: the home server is where the game
            // teaches, and a teaching space that occasionally plants a parasite on the student is one
            // they learn to avoid.
            assertThat(Balance.breachCounterHackChance(200, 0)).isZero();
        }

        @Test
        @DisplayName("a quiet breach is markedly safer than a loud one at the same depth")
        void noiseIsTheVariable() {
            double quiet = Balance.breachCounterHackChance(Balance.NOISE_BASE, 2);
            double loud = Balance.breachCounterHackChance(Balance.NOISE_BASE + 4 * Balance.NOISE_BYPASS, 2);
            assertThat(loud).isGreaterThan(quiet * 2.0d);
        }

        @Test
        @DisplayName("depth still matters — the same noise is worse further out")
        void depthStillMatters() {
            for (int depth = 1; depth < 4; depth++) {
                assertThat(Balance.breachCounterHackChance(20, depth + 1))
                        .as("depth %d against %d", depth, depth + 1)
                        .isGreaterThan(Balance.breachCounterHackChance(20, depth));
            }
        }

        @Test
        @DisplayName("a silent breach is never certain to be safe, and a loud one is never certain to be caught")
        void neverCertainEitherWay() {
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.breachCounterHackChance(0, depth))
                        .as("silent at depth %d", depth)
                        .isPositive();
                assertThat(Balance.breachCounterHackChance(10_000, depth))
                        .as("deafening at depth %d", depth)
                        .isLessThan(1.0d);
            }
        }

        @Test
        @DisplayName("a breach at the reference noise carries the same risk a sweep of that depth does")
        void anchoredToTheSweepTable() {
            // Scaled off the same depth table a sweep uses, so the two paths cannot drift apart when
            // one of them is re-tuned.
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.breachCounterHackChance(Balance.BREACH_NOISE_REFERENCE, depth))
                        .as("depth %d", depth)
                        .isEqualTo(Balance.netCounterHackChance(depth));
            }
        }
    }
}
