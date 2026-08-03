package io.github.stoicswe.eyeandsickle.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Properties the balance tables must have, whatever anyone tunes them to.
 *
 * <h2>What this file is for, and what it deliberately is not</h2>
 *
 * It does <b>not</b> pin the numbers. {@code docs/design/03-economy.md} says the economy figures are
 * calibrated as a set and most of them are tagged {@code [PROPOSAL]}; a test that asserted a tarpit
 * chance of exactly 0.15 would fail on every legitimate tuning pass and teach whoever hit it to
 * delete the test rather than think.
 *
 * <p>It pins the <b>shapes</b> — the relationships a re-tune must preserve, each of which breaks a
 * named system if it inverts. Two of them additionally protect against a crash rather than a
 * mis-tune: {@code netFirewallTier} must never emit a 4, because {@code BreachTarget}'s compact
 * constructor throws on one, and {@code netTier} must stay inside the shared 1–5 scale for the same
 * reason.
 *
 * <p>Currently scoped to the network tables added with {@code docs/design/17}. The rest of
 * {@link Balance} has always been covered indirectly, through the rules that read it.
 */
class BalanceTest {

    /** Fine enough that a band boundary off by a thousandth still shows up. */
    private static final int STEPS = 2_000;

    private static double u(int step) {
        return step / (double) STEPS;
    }

    @Nested
    @DisplayName("ranges — a generated value must never crash a protocol type")
    class Ranges {

        @Test
        @DisplayName("firewall tier is never 4, at any depth, for any roll")
        void firewallNeverLeavesTheScale() {
            // ⚠ Not a balance concern. BreachTarget's compact constructor THROWS above 3, so a fourth
            // band would be an exception raised while building the target list — a save that cannot
            // render its own network. Checked exhaustively because a single stray band boundary in a
            // re-tune is all it takes.
            for (int depth = -3; depth <= 9; depth++) {
                for (int step = 0; step < STEPS; step++) {
                    assertThat(Balance.netFirewallTier(depth, u(step)))
                            .as("depth %d, u %f", depth, u(step))
                            .isBetween(0, 3);
                }
            }
        }

        @Test
        @DisplayName("difficulty tier stays on the shared 1-5 scale")
        void tierNeverLeavesTheScale() {
            for (int depth = -3; depth <= 9; depth++) {
                for (int step = 0; step < STEPS; step++) {
                    assertThat(Balance.netTier(depth, u(step)))
                            .isBetween(DifficultyTier.LOWEST, DifficultyTier.HIGHEST);
                }
            }
        }

        @Test
        @DisplayName("host kind is always one of the four rollable archetypes")
        void kindIsAlwaysRollable() {
            // GATEWAY, BRIDGE and SELF are assigned structurally and must never come out of a roll —
            // a rolled gateway would mean two on one server, and a rolled SELF would mean a second
            // machine claiming to be the player's.
            List<String> rollable = List.of(
                    HostKind.TERMINAL.name(), HostKind.RELAY.name(),
                    HostKind.STORE.name(), HostKind.SENTRY.name());
            for (int depth = -3; depth <= 9; depth++) {
                for (int step = 0; step < STEPS; step++) {
                    assertThat(Balance.netHostKind(depth, u(step))).isIn(rollable);
                }
            }
        }

        @Test
        @DisplayName("depth is clamped to the rows the tables were tuned against")
        void depthClamps() {
            assertThat(Balance.netDepth(-1)).isZero();
            assertThat(Balance.netDepth(0)).isZero();
            assertThat(Balance.netDepth(4)).isEqualTo(4);
            // A depth-biased tree over seven servers can reach six. Inventing rows for depths nobody
            // tuned would be tuning by extrapolation, so deeper servers read the deepest row.
            assertThat(Balance.netDepth(9)).isEqualTo(4);
        }

        @Test
        @DisplayName("machine counts are a valid range and never exceed the brief's cap of fifty")
        void machineCountsAreSane() {
            for (int depth = 0; depth <= 4; depth++) {
                assertThat(Balance.netMachinesMin(depth)).isPositive();
                assertThat(Balance.netMachinesMax(depth))
                        .isGreaterThan(Balance.netMachinesMin(depth))
                        .isLessThanOrEqualTo(Balance.NET_MACHINES_HARD_CAP);
            }
        }
    }

    @Nested
    @DisplayName("gradients — deeper is harder, richer and more dangerous")
    class Gradients {

        @Test
        @DisplayName("every hazard rises with depth, and none of them touches home")
        void hazardsRiseAndHomeIsClean() {
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.netTarpitChance(depth)).isGreaterThan(Balance.netTarpitChance(depth - 1));
                assertThat(Balance.netCanaryChance(depth)).isGreaterThan(Balance.netCanaryChance(depth - 1));
                assertThat(Balance.netDefendedChance(depth)).isGreaterThan(Balance.netDefendedChance(depth - 1));
                assertThat(Balance.netHoneypotChance(depth)).isGreaterThan(Balance.netHoneypotChance(depth - 1));
                assertThat(Balance.netDocumentChance(depth)).isGreaterThan(Balance.netDocumentChance(depth - 1));
                assertThat(Balance.netCounterHackChance(depth)).isGreaterThan(Balance.netCounterHackChance(depth - 1));
            }
            // Home is where the game teaches. A tarpit surcharges every action, which is exactly the
            // defence that punishes a player still learning to read a board.
            assertThat(Balance.netTarpitChance(0)).isZero();
            assertThat(Balance.netCanaryChance(0)).isZero();
            assertThat(Balance.netHoneypotChance(0)).isZero();
            // N-4 made structural: the flavour layer starts one bridge out.
            assertThat(Balance.netDocumentChance(0)).isZero();
        }

        @Test
        @DisplayName("a player who has never left home is never counter-hacked")
        void homeNeverBitesBack() {
            // ⚠ A named constant, not a table row that happens to be zero. A re-tune that walked the
            // whole column up by a few points would otherwise start planting parasites on beginners,
            // and the failure would look like a difficulty change rather than a broken promise.
            assertThat(Balance.NET_COUNTER_HACK_HOME).isZero();
            assertThat(Balance.netCounterHackChance(0)).isEqualTo(Balance.NET_COUNTER_HACK_HOME);
            assertThat(Balance.netCounterHackChance(-5)).isEqualTo(Balance.NET_COUNTER_HACK_HOME);
            assertThat(Balance.netCounterHackHeat(0)).isZero();
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.netCounterHackHeat(depth)).isPositive();
                // On a 0–100 scale, reaching the named-hacker band has to take a campaign rather than
                // an evening — docs/design/01-core-resources.md §4.1's bands.
                assertThat(Balance.netCounterHackHeat(depth)).isLessThanOrEqualTo(3);
            }
        }

        @Test
        @DisplayName("machine counts grow with depth")
        void deeperServersAreBigger() {
            for (int depth = 1; depth <= 4; depth++) {
                assertThat(Balance.netMachinesMin(depth)).isGreaterThan(Balance.netMachinesMin(depth - 1));
                assertThat(Balance.netMachinesMax(depth)).isGreaterThan(Balance.netMachinesMax(depth - 1));
            }
        }

        @Test
        @DisplayName("tier bands slide rather than merely shift — home cannot roll a wall")
        void tierBandsSlide() {
            // Tier 1 must be unreachable from depth 2 and tier 5 unreachable below depth 3. That is
            // the brief's "harder on average" made a FLOOR as well as an average: a player two bridges
            // out cannot stumble onto a tutorial-grade machine, and a player at home cannot stumble
            // onto a wall.
            for (int step = 0; step < STEPS; step++) {
                assertThat(Balance.netTier(0, u(step))).isLessThanOrEqualTo(2);
                assertThat(Balance.netTier(1, u(step))).isLessThanOrEqualTo(3);
                assertThat(Balance.netTier(2, u(step))).isBetween(2, 4);
                assertThat(Balance.netTier(3, u(step))).isBetween(3, 5);
                assertThat(Balance.netTier(4, u(step))).isBetween(4, 5);
            }
        }

        @Test
        @DisplayName("payout rises with tier, and every band is a real range")
        void lootRisesWithTier() {
            java.math.BigInteger previousTop = java.math.BigInteger.valueOf(-1);
            for (int tier = DifficultyTier.LOWEST; tier <= DifficultyTier.HIGHEST; tier++) {
                java.math.BigInteger low = Balance.netLootWei(tier, 0.0d);
                java.math.BigInteger high = Balance.netLootWei(tier, 1.0d);
                assertThat(low).as("tier %d floor", tier).isGreaterThan(previousTop);
                assertThat(high).as("tier %d ceiling", tier).isGreaterThan(low);
                previousTop = high;
            }
            // The T1 floor is what the home contact floor promises, so the two must agree.
            assertThat(Balance.netLootWei(1, 0.0d)).isEqualTo(Balance.NET_LOOT_FLOOR_WEI);
            // Out-of-range rolls clamp rather than extrapolate past the band.
            assertThat(Balance.netLootWei(1, -3.0d)).isEqualTo(Balance.netLootWei(1, 0.0d));
            assertThat(Balance.netLootWei(1, 4.0d)).isEqualTo(Balance.netLootWei(1, 1.0d));
        }
    }

    @Nested
    @DisplayName("the sweep ladder")
    class SweepLadder {

        @Test
        @DisplayName("a better instrument never finds less, for any signal")
        void sensitivityIsMonotoneInTier() {
            // ⚠ The property that makes an upgrade legible. If a tier could ever be less sensitive
            // than the one below it for some signal, a player who spent 25 EC would lose a contact
            // they already had — and illegible purchases are how a player learns to stop buying
            // things.
            for (SignalStrength signal : SignalStrength.values()) {
                double t1 = Balance.netSweepBase(1, signal.name());
                double t2 = Balance.netSweepBase(2, signal.name());
                double t3 = Balance.netSweepBase(3, signal.name());
                assertThat(t2).as("%s: T2 beats T1", signal).isGreaterThan(t1);
                assertThat(t3).as("%s: T3 beats T2", signal).isGreaterThan(t2);
                // Never certainty. A sweep that always found everything would delete the whole
                // sensitivity axis and with it the reason to buy anything.
                assertThat(t3).isLessThan(1.0d);
                assertThat(t1).isPositive();
            }
        }

        @Test
        @DisplayName("a louder machine is easier to find, at every tier")
        void sensitivityIsMonotoneInSignal() {
            for (int tier = 1; tier <= 3; tier++) {
                double low = Balance.netSweepBase(tier, SignalStrength.LOW.name());
                double moderate = Balance.netSweepBase(tier, SignalStrength.MODERATE.name());
                double high = Balance.netSweepBase(tier, SignalStrength.HIGH.name());
                assertThat(moderate).isGreaterThan(low);
                assertThat(high).isGreaterThan(moderate);
            }
        }

        @Test
        @DisplayName("an unreadable signal or tier reads as the least generous option")
        void badInputIsNotAWindfall() {
            // A hand-edited save must open, and it must not open into a free upgrade.
            assertThat(Balance.netSweepBase(1, null)).isEqualTo(Balance.netSweepBase(1, "LOW"));
            assertThat(Balance.netSweepBase(1, "nonsense")).isEqualTo(Balance.netSweepBase(1, "LOW"));
            assertThat(Balance.netSweepBase(0, "HIGH")).isEqualTo(Balance.netSweepBase(1, "HIGH"));
            assertThat(Balance.netSweepBase(99, "HIGH")).isEqualTo(Balance.netSweepBase(3, "HIGH"));
        }

        @Test
        @DisplayName("the second hop is real but coarse — reach is a purchase, clarity is not")
        void theSecondHopIsWorse() {
            // docs/design/07-recon-tools.md §2 makes the Topology Mapper a reach purchase and
            // pointedly not a clarity one.
            assertThat(Balance.NET_HOP_FACTOR_1).isEqualTo(1.00d);
            assertThat(Balance.NET_HOP_FACTOR_2).isBetween(0.0d, 1.0d).isLessThan(Balance.NET_HOP_FACTOR_1);
        }

        @Test
        @DisplayName("prices and costs climb together, and sit where design/03 and design/07 put them")
        void thePricesAreWhereTheDocsSayTheyAre() {
            assertThat(Balance.NET_SWEEP_WIDE_PRICE).isLessThan(Balance.NET_SWEEP_DEEP_PRICE);
            // Above the 15 EC Passive Sniffer, because it is a permanent tool; below the mid-tier
            // band's floor, because at 40 EC the first upgrade a new player buys would be two hours
            // out of reach on docs/design/03-economy.md §3's cautious net.
            assertThat(Balance.NET_SWEEP_WIDE_PRICE).isGreaterThan(Balance.ec("15"));
            assertThat(Balance.NET_SWEEP_WIDE_PRICE).isLessThan(Balance.PRICE_MID_TIER_MIN);
            // Squarely inside the mid-tier band — about one cautious session.
            assertThat(Balance.NET_SWEEP_DEEP_PRICE).isBetween(Balance.PRICE_MID_TIER_MIN, Balance.PRICE_MID_TIER_MAX);

            // Inside docs/design/07-recon-tools.md §1's established 2–14 recon compute range, and
            // rising — the cycles ARE the noise, so this ordering is also the noise ordering.
            assertThat(Balance.NET_SWEEP_BASE_CYCLES).isGreaterThanOrEqualTo(2L);
            assertThat(Balance.NET_SWEEP_DEEP_CYCLES).isLessThanOrEqualTo(14L);
            assertThat(Balance.NET_SWEEP_WIDE_CYCLES).isGreaterThan(Balance.NET_SWEEP_BASE_CYCLES);
            assertThat(Balance.NET_SWEEP_DEEP_CYCLES).isGreaterThan(Balance.NET_SWEEP_WIDE_CYCLES);

            assertThat(Balance.NET_SWEEP_WIDE_SECONDS).isGreaterThan(Balance.NET_SWEEP_BASE_SECONDS);
            assertThat(Balance.NET_SWEEP_DEEP_SECONDS).isGreaterThan(Balance.NET_SWEEP_WIDE_SECONDS);
            // A base sweep is shorter than a Quick Scan: it is the verb a new player runs before they
            // own anything, and a tool whose floor is a thirty-second wait is a tool they run once.
            assertThat(Balance.NET_SWEEP_BASE_SECONDS).isLessThan(Balance.SCAN_QUICK_SECONDS);
        }

        @Test
        @DisplayName("the world is five to seven servers, and never more")
        void worldSizeIsBounded() {
            assertThat(Balance.NET_SERVERS_MIN).isLessThan(Balance.NET_SERVERS_MAX);
            assertThat(Balance.NET_SERVERS_MAX).isEqualTo(7);
            assertThat(Balance.NET_MACHINES_HARD_CAP).isEqualTo(50);
            assertThat(Balance.NET_SERVER_DEEPEN_BIAS).isBetween(0.0d, 1.0d);
            assertThat(Balance.NET_SERVER_CHORD_CHANCE).isBetween(0.0d, 1.0d);
            assertThat(Balance.NET_INTRA_CHORD_CHANCE).isBetween(0.0d, 1.0d);
            assertThat(Balance.NET_HOME_SEED_NEIGHBOURS).isGreaterThan(Balance.NET_HOME_GUARANTEED_CONTACTS);
        }
    }
}
