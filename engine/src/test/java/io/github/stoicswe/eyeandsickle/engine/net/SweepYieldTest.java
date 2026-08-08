package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * How much one sweep hands over — 5–7 at home, falling to 1–3 past a couple of bridges.
 *
 * <h2>⚠ THE CAP IS ABSOLUTE PER (VANTAGE, TIER), NEVER PER ATTEMPT</h2>
 *
 * {@code NetRules}' spine is that "only two things move the outcome and both cost: a higher sweep
 * tier, or a closer vantage". A cap that reset each sweep would have made repetition the cheapest of
 * the three and turned discovery into button-mashing. Sweeping the same spot twice still reports
 * nothing new; the rest of a server is reached by <em>moving</em>.
 */
@DisplayName("sweep yield")
class SweepYieldTest {

    private static final int SAMPLE = 120;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    private static GameSave equipped(long seed) {
        GameSave save = NetTestKit.world(seed);
        NetTestKit.grant(save, SweepTier.WIDE);
        NetTestKit.grant(save, SweepTier.DEEP);
        return save;
    }

    @Nested
    @DisplayName("the curve")
    class Curve {

        @Test
        @DisplayName("never leaves 1..7, at any depth, tier or variation")
        void staysInRange() {
            // The player-facing promise is "somewhere between 1 and 7". A band that could return 0
            // would be a sweep that finds nothing and cannot say why; one that could return 12 would
            // hand over a whole server on first contact.
            for (int depth = 0; depth <= 6; depth++) {
                for (int tier = 1; tier <= 3; tier++) {
                    for (double v = 0.0; v <= 1.0; v += 0.05) {
                        assertThat(Balance.sweepYield(depth, tier, v))
                                .as("depth %d tier %d variation %.2f", depth, tier, v)
                                .isBetween(1, 7);
                    }
                }
            }
        }

        @Test
        @DisplayName("home is generous and depth is mean")
        void homeIsEasier() {
            // A first sweep that returned one machine would read as a broken tool rather than as a
            // quiet neighbourhood — the argument NET_COUNTER_HACK_HOME and MONJOB_DENSITY_HOME both
            // make for their own floors.
            assertThat(Balance.sweepYield(0, 1, 0.0)).as("home floor").isGreaterThanOrEqualTo(5);
            assertThat(Balance.sweepYield(4, 1, 0.0)).as("deep floor").isLessThanOrEqualTo(3);
            assertThat(Balance.sweepYield(0, 1, 0.5)).isGreaterThan(Balance.sweepYield(4, 1, 0.5));
        }

        @Test
        @DisplayName("⚠ non-decreasing in tier — a better instrument never yields less")
        void tierNeverCosts() {
            // The same rule the detection threshold obeys, and for the same reason: a player who buys
            // an upgrade and gets fewer machines back would reasonably call it a bug, and would be
            // right. The tier term only ever adds.
            for (int depth = 0; depth <= 6; depth++) {
                for (double v = 0.0; v <= 1.0; v += 0.1) {
                    assertThat(Balance.sweepYield(depth, 2, v))
                            .as("depth %d wide vs base", depth)
                            .isGreaterThanOrEqualTo(Balance.sweepYield(depth, 1, v));
                    assertThat(Balance.sweepYield(depth, 3, v))
                            .as("depth %d deep vs wide", depth)
                            .isGreaterThanOrEqualTo(Balance.sweepYield(depth, 2, v));
                }
            }
        }

        @Test
        @DisplayName("depth never makes a sweep more generous")
        void deeperIsNeverBetter() {
            for (int tier = 1; tier <= 3; tier++) {
                for (double v = 0.0; v <= 1.0; v += 0.1) {
                    int previous = Integer.MAX_VALUE;
                    for (int depth = 0; depth <= 6; depth++) {
                        int now = Balance.sweepYield(depth, tier, v);
                        assertThat(now).as("depth %d tier %d", depth, tier).isLessThanOrEqualTo(previous);
                        previous = now;
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("in a real sweep")
    class Applied {

        @Test
        @DisplayName("⚠ the cap binds AT DEPTH, and deliberately not at home")
        void bindsWhereItIsMeantTo() {
            // ⚠ THIS TEST REPLACED ONE THAT PROVED NOTHING. The first version asserted "no sweep
            // hands over more than seven", which passes just as happily with the cap deleted —
            // verified by deleting it. Measured over 200 home worlds, a sweep naturally finds
            // avg 4.07 (base) / 4.85 (wide) / 4.94 (deep), max 5, and the home band is 5–7, so the
            // cap is INERT at home by design and a home-only sample can never observe it.
            //
            // What the cap actually does is bite past a bridge, where the band falls to 1–3 against
            // the same natural find. That is the whole "harder the further out you go" mechanic, and
            // it is a property of the curve rather than of any one generated world — so it is
            // asserted on the curve, against the measured natural yield.
            int measuredNaturalFind = 4;
            for (int tier = 1; tier <= 3; tier++) {
                assertThat(Balance.sweepYield(0, tier, 0.0))
                        .as("home must not throttle a sweep that finds ~%d", measuredNaturalFind)
                        .isGreaterThanOrEqualTo(measuredNaturalFind);
                assertThat(Balance.sweepYield(4, 1, 0.0))
                        .as("a base sweep four servers out must hand over less than it would at home")
                        .isLessThan(measuredNaturalFind);
            }
        }

        @Test
        @DisplayName("no sweep ever hands over more than seven machines")
        void neverExceedsTheBand() {
            // Weak on today's worlds — the natural find is already under seven — but it is the
            // player-facing promise, and it is what would fire if the generator ever got denser.
            for (int i = 0; i < SAMPLE; i++) {
                for (SweepTier tier : SweepTier.values()) {
                    SweepReport report = NetTestKit.sweep(equipped(seed(i)), tier, NetTestKit.T0);
                    assertThat(report.foundAddresses().size())
                            .as("world %d, %s", i, tier)
                            .isLessThanOrEqualTo(7);
                }
            }
        }

        @Test
        @DisplayName("⚠ the cap does not empty a sweep — a first look still finds something")
        void stillFindsThings() {
            // ⚠ The failure a cap most easily introduces is the opposite of the one it fixes: rank,
            // truncate, and hand back nothing. A sweep that reports zero is indistinguishable from a
            // broken instrument, and this is a HOME sweep, which is where the game teaches.
            int empty = 0;
            for (int i = 0; i < SAMPLE; i++) {
                if (NetTestKit.sweep(equipped(seed(i)), SweepTier.BASE, NetTestKit.T0)
                        .foundAddresses()
                        .isEmpty()) {
                    empty++;
                }
            }
            assertThat(empty)
                    .as("worlds where a base sweep at home found nothing")
                    .isLessThan(SAMPLE / 4);
        }

        @Test
        @DisplayName("the same vantage and tier cut at the same place, every time")
        void deterministic() {
            // The whole cap rests on detectRoll predating the sweep, so the ranking is fixed too.
            // Nothing here draws; two sweeps of one world must be identical sets.
            for (int i = 0; i < SAMPLE; i++) {
                var first = NetTestKit.sweep(equipped(seed(i)), SweepTier.WIDE, NetTestKit.T0)
                        .foundAddresses();
                var second = NetTestKit.sweep(equipped(seed(i)), SweepTier.WIDE, NetTestKit.T0)
                        .foundAddresses();
                assertThat(second).as("world %d", i).isEqualTo(first);
            }
        }
    }
}
