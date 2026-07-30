package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.BlockContribution;
import io.github.stoicswe.eyeandsickle.protocol.game.ChainSync;
import io.github.stoicswe.eyeandsickle.protocol.game.MiningMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The LEDGER window's three tabs, and the report the {@code SYNCHRONIZING} panel reads.
 *
 * <p>What is testable here is the arithmetic and the wording; the layout is not, and is covered by
 * {@code LedgerSnapshot} instead — a ten-column table and a discrete meter are exactly the things an
 * assertion cannot see.
 */
class LedgerWindowTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    @Nested
    @DisplayName("the tab strip")
    class Tabs {

        /**
         * ⚠ Every tab must answer for itself, because the call site used to use a ternary.
         *
         * <p>A two-branch conditional over an enum silently gives a third constant the second one's
         * description: the chip renders, reads out wrong, and nothing anywhere reports it — only a
         * screen-reader user ever finds out. The switch in {@link LedgerTab#description} is
         * exhaustive, so the compiler now refuses a fourth tab that forgets one.
         */
        @Test
        @DisplayName("each tab has its own description, and none is blank or shared")
        void everyTabDescribesItself() {
            assertThat(Arrays.stream(LedgerTab.values()).map(LedgerTab::description))
                    .doesNotContain("")
                    .doesNotHaveDuplicates()
                    .hasSize(LedgerTab.values().length);
        }

        @Test
        @DisplayName("CONTRIBUTOR comes last, after LEDGER")
        void contributorIsLast() {
            // It presupposes both of the others — a reader has to know what a block is and what a
            // payout looks like before "you were 4.1% of this block" means anything.
            assertThat(LedgerTab.values()[LedgerTab.values().length - 1])
                    .isEqualTo(LedgerTab.CONTRIBUTOR);
            assertThat(LedgerTab.CONTRIBUTOR.ordinal()).isGreaterThan(LedgerTab.LEDGER.ordinal());
        }

        /** Brackets, not colour — the selected state has to survive greyscale and a screen reader. */
        @Test
        @DisplayName("selection is indicated by brackets, at a constant width")
        void selectionIsBracketed() {
            for (LedgerTab tab : LedgerTab.values()) {
                assertThat(tab.control(tab)).isEqualTo("[ " + tab.label() + " ]");
                assertThat(tab.control(LedgerTab.CHAIN == tab ? LedgerTab.LEDGER : LedgerTab.CHAIN))
                        .hasSameSizeAs(tab.control(tab));
            }
        }
    }

    @Nested
    @DisplayName("the synchronisation report")
    class Sync {

        private ChainSync away(long awaySeconds, long minedSeconds, int blocks, int competed) {
            return new ChainSync(
                    T0, T0.plusSeconds(awaySeconds), awaySeconds, minedSeconds,
                    4412L, 4412L + blocks, blocks, competed, 1, 0, Balance.ec("175.30"), 1,
                    344.53d, 351.06d, 2, false);
        }

        @Test
        @DisplayName("a load with nothing to fill in shows no screen")
        void nothingToShow() {
            assertThat(ChainSync.none(T0).any()).isFalse();
            assertThat(ChainSync.none(T0).capped()).isFalse();
            assertThat(ChainSync.none(T0).uncontestedBlocks()).isZero();
        }

        /**
         * ⚠ {@code capped()} answers "did the cap bite", NOT "was the window applied".
         *
         * <p>A player who came back the moment their rig stopped has had nothing withheld, so the
         * panel must not tell them a cap took something. The two questions differ only at the exact
         * boundary, which is precisely where a naive {@code >=} would be wrong.
         */
        @Test
        @DisplayName("the cap reads as having bitten only when it actually took something")
        void cappedOnlyWhenItBit() {
            assertThat(away(14_400, 14_400, 17, 17).capped()).isFalse();
            assertThat(away(14_401, 14_400, 17, 17).capped()).isTrue();
            assertThat(away(604_800, 14_400, 720, 17).capped()).isTrue();
        }

        @Test
        @DisplayName("uncontested blocks are the ones that landed after the rig stopped")
        void uncontestedIsTheRemainder() {
            assertThat(away(604_800, 14_400, 720, 17).uncontestedBlocks()).isEqualTo(703);
            // Never negative, even on a report that has been hand-edited into nonsense.
            assertThat(away(100, 100, 5, 9).uncontestedBlocks()).isZero();
        }

        /**
         * ⚠ The replay is paced by STEPS, not by blocks, and the height must still land exactly.
         *
         * <p>51 blocks and 5 100 blocks take the same few seconds on screen, because the honest thing
         * a bar can report here is how far through *showing* you it is — the work finished before the
         * panel existed. What it must not do is stop short of the real height, which is what a
         * fraction that rounded down at the last step would do.
         */
        @Test
        @DisplayName("the replay lands exactly on the real height, whatever it covers")
        void replayEndsOnTheTip() {
            for (int blocks : new int[] {1, 51, 5_100, 200_000}) {
                ChainSync sync = away(604_800, 14_400, blocks, 17);
                assertThat(sync.heightAt(0, ChainSyncPanel.STEPS)).isEqualTo(sync.fromHeight());
                assertThat(sync.heightAt(ChainSyncPanel.STEPS, ChainSyncPanel.STEPS))
                        .as("%d blocks", blocks)
                        .isEqualTo(sync.toHeight());
            }
        }

        @Test
        @DisplayName("progress clamps rather than throwing on a zero-step or over-run replay")
        void progressIsTotal() {
            ChainSync sync = away(604_800, 14_400, 720, 17);
            assertThat(sync.progress(5, 0)).isEqualTo(1.0d);
            assertThat(sync.progress(-3, 32)).isEqualTo(0.0d);
            assertThat(sync.progress(99, 32)).isEqualTo(1.0d);
        }

        /**
         * ⚠ The credit is grafted on by the caller, and the wither must not drop anything.
         *
         * <p>{@code ChainRules} decides who won a block and {@code MiningRules} decides what a block
         * is worth, so the chain leaves the figure at zero and the caller fills it in from the one
         * credit it actually wrote to the ledger. A hand-written copy constructor over sixteen
         * components is exactly the place a field goes missing silently.
         */
        @Test
        @DisplayName("withCredit changes the credit and nothing else")
        void withCreditKeepsEverythingElse() {
            ChainSync sync = away(604_800, 14_400, 720, 17);
            ChainSync credited = sync.withCredit(Balance.ec("99.99"));

            assertThat(credited.creditedWei()).isEqualTo(Balance.ec("99.99"));
            assertThat(credited).isEqualTo(sync.withCredit(sync.creditedWei())
                    .withCredit(Balance.ec("99.99")));
            assertThat(credited.withCredit(sync.creditedWei())).isEqualTo(sync);
        }

        @Test
        @DisplayName("durations read the way a person would say them")
        void humanDurations() {
            assertThat(ChainSyncPanel.human(Duration.ofMinutes(42))).isEqualTo("42m");
            assertThat(ChainSyncPanel.human(Duration.ofHours(4))).isEqualTo("4h");
            assertThat(ChainSyncPanel.human(Duration.ofMinutes(714))).isEqualTo("11h 54m");
            assertThat(ChainSyncPanel.human(Duration.ofDays(3))).isEqualTo("3d");
            assertThat(ChainSyncPanel.human(Duration.ofHours(75))).isEqualTo("3d 3h");
            // Never negative, never blank — a clock that went backwards is a real hazard here.
            assertThat(ChainSyncPanel.human(Duration.ofSeconds(-500))).isEqualTo("0m");
        }
    }

    @Nested
    @DisplayName("a contributor row")
    class Contributions {

        private BlockContribution row(String scheme, java.math.BigInteger credited) {
            return new BlockContribution(
                    4428L, T0, scheme.equals("SOLO") ? MiningMode.SOLO : MiningMode.POOLED,
                    scheme, scheme.equals("SOLO") ? "" : "commons",
                    scheme.equals("SOLO") ? "" : "THE COMMONS",
                    scheme.equals("SOLO"), false,
                    62_914_560L, 1_761_607_680L, 344.53d, 143, Balance.ec("160"), Balance.ec("17.30"), credited);
        }

        @Test
        @DisplayName("a solo win takes the whole reward — both halves of it")
        void soloTakesEverything() {
            BlockContribution solo = row("SOLO", Balance.ec("177.3"));
            assertThat(solo.rewardWei()).isEqualTo(Balance.ec("177.30"));
            assertThat(solo.creditedWei()).isEqualTo(solo.rewardWei());
            assertThat(solo.takeFraction()).isEqualTo(1.0d);
            assertThat(solo.minerLabel()).isEqualTo("YOUR RIG");
            assertThat(solo.paid()).isTrue();
        }

        /**
         * ⚠ Zero is the CORRECT credit under pay-per-share, and the row must still be a real row.
         *
         * <p>A share pool does not divide up the blocks it finds — it buys accepted shares out of its
         * own balance. The hashrate is real, the block is real, and nothing from it reached the
         * player. That pairing is the only place in the client where the difference between the two
         * pool schemes is visible, so a row that reported it as "unpaid and therefore empty" would
         * delete the teaching along with the noise.
         */
        @Test
        @DisplayName("pay-per-share credits nothing from the block and still names a real hashrate")
        void payPerShareCreditsNothing() {
            BlockContribution pps = row("PPS", Balance.ec("0.0"));
            assertThat(pps.paid()).isFalse();
            assertThat(pps.takeFraction()).isZero();
            assertThat(pps.hashrate()).isPositive();
            assertThat(pps.rewardWei()).isPositive();
            assertThat(pps.minerLabel()).isEqualTo("THE COMMONS");
        }

        /**
         * The number the whole tab exists for: the rig's share of the chain is exactly the
         * probability {@code ChainRules.drawWinner} rolled against for that block.
         */
        @Test
        @DisplayName("share is the rig's fraction of the chain, clamped and never dividing by zero")
        void shareIsTheDrawProbability() {
            assertThat(row("SOLO", Balance.ec("0.01")).networkShare()).isCloseTo(0.0357d, org.assertj.core.data.Offset.offset(0.001d));

            BlockContribution noNetwork = new BlockContribution(
                    1L, T0, MiningMode.SOLO, "SOLO", "", "", true, false,
                    1_000L, 0L, 1.0d, 1, Balance.ec("160"), java.math.BigInteger.ZERO, Balance.ec("160"));
            assertThat(noNetwork.networkShare()).isZero();

            BlockContribution wholeChain = new BlockContribution(
                    1L, T0, MiningMode.SOLO, "SOLO", "", "", true, false,
                    9_000L, 1_000L, 1.0d, 1, Balance.ec("160"), java.math.BigInteger.ZERO, Balance.ec("160"));
            assertThat(wholeChain.networkShare())
                    .as("a rig cannot be more than all of the chain")
                    .isEqualTo(1.0d);
        }

        @Test
        @DisplayName("the coinbase and the fees stay separate, and sum to the reward")
        void subsidyAndFeesAreSeparate() {
            BlockContribution any = row("PPLNS", Balance.ec("35.0"));
            // One credit in the ledger, two different things on the chain: the subsidy is minted and
            // the fees were paid by the senders. proof-of-work(7) teaches that split and a single
            // "reward" total is exactly the readout that hides it.
            assertThat(any.subsidyWei()).isEqualTo(Balance.ec("160"));
            assertThat(any.feesWei()).isEqualTo(Balance.ec("17.30"));
            assertThat(any.rewardWei())
                    .isEqualTo(any.subsidyWei().add(any.feesWei()));
            assertThat(any.takeFraction()).isLessThan(1.0d);
        }
    }
}
