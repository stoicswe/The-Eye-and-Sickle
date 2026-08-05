package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.Archives;
import io.github.stoicswe.eyeandsickle.engine.rules.DownloadQueue;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The download queue, and the bundle that arrives as one archive.
 *
 * <h2>Why this is at the SESSION level rather than on the rules</h2>
 *
 * The queue's rules and the transfer's rules are each individually correct and were each
 * individually covered before they were joined. What this repo has been bitten by twice —
 * {@code reconcileFootholds}, and the shell window's {@code onClosed} — is a defect in the
 * <b>join</b>, where a unit test cannot look: a promotion that nothing calls, or a hold whose clock
 * shift never reaches the tick. So these drive {@code LocalGameSession} and {@code GameEngine.tick}
 * and assert on what a player would see.
 */
class DownloadQueueFlowTest {

    private static final Instant T0 = Instant.parse("2026-08-04T09:00:00Z");

    /** A clock that can be wound, because everything here is a deadline. */
    private static final class Winding extends Clock {
        private Instant at;

        Winding(Instant at) {
            this.at = at;
        }

        void wind(Duration by) {
            at = at.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }

    private record Fixture(GameEngine game, LocalGameSession session, Winding clock) {}

    private static Fixture open(Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        game.credit(Balance.ec("5000"), "TEST", "seed");
        return new Fixture(game, new LocalGameSession(game), clock);
    }

    /** Two things that are on the shelf and are not each other. */
    private static List<String> twoOfferings() {
        return io.github.stoicswe.eyeandsickle.engine.Catalogue.offerings().stream()
                .filter(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::purchasable)
                .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::id)
                .limit(2)
                .toList();
    }

    @Nested
    @DisplayName("one at a time")
    class OneAtATime {

        @Test
        @DisplayName("the first purchase starts immediately; the second waits its turn")
        void theSecondPurchaseQueues(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();

            f.session().purchase(two.get(0));
            assertThat(f.session().transfers())
                    .as("⚠ a lone purchase must start in the SAME call — a queue entry that sits "
                            + "inert until the next tick reads as a purchase that did not work")
                    .hasSize(1);

            f.session().purchase(two.get(1));
            assertThat(f.session().transfers())
                    .as("still one in flight: a queue with a concurrency of two is a list with a decoration")
                    .hasSize(1);

            List<DownloadOrder> queue = f.session().downloads();
            assertThat(queue).hasSize(2);
            assertThat(queue.get(0).active()).isTrue();
            assertThat(queue.get(1).waiting()).isTrue();
            assertThat(queue.get(1).paused()).isFalse();
        }

        @Test
        @DisplayName("⚠ waiting and held are DIFFERENT states, and only one of them is the player's doing")
        void waitingIsNotHeld(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));

            String second = f.session().downloads().get(1).orderId();
            f.session().pauseDownload(second);

            DownloadOrder held = f.session().downloads().get(1);
            assertThat(held.paused()).isTrue();
            assertThat(held.waiting())
                    .as("a paused order is not 'waiting' — collapsing the two would hide the effect "
                            + "of the control the player just pressed")
                    .isFalse();
        }

        @Test
        @DisplayName("pausing the active one promotes the next")
        void pausingPromotesTheNext(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));

            String first = f.session().downloads().get(0).orderId();
            f.session().pauseDownload(first);
            f.clock().wind(Duration.ofSeconds(1));
            f.game().tick();

            List<DownloadOrder> queue = f.session().downloads();
            assertThat(queue.get(0).paused()).isTrue();
            assertThat(queue.get(1).active())
                    .as("the second is now the first that is not paused, so it is the one running")
                    .isTrue();
            assertThat(f.session().transfers())
                    .as("⚠ TWO tasks exist — the held one keeps its bytes — but only one progresses")
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("holding")
    class Holding {

        @Test
        @DisplayName("⚠ a held download's progress does not move, however long it is held")
        void heldProgressIsFrozen(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));

            f.clock().wind(Duration.ofSeconds(2));
            f.game().tick();
            double before = f.session().downloads().get(0).progress();
            assertThat(before).isGreaterThan(0.0d);

            f.session().pauseDownload(f.session().downloads().get(0).orderId());
            for (int i = 0; i < 20; i++) {
                f.clock().wind(Duration.ofSeconds(5));
                f.game().tick();
            }

            DownloadOrder held = f.session().downloads().stream()
                    .filter(DownloadOrder::paused)
                    .findFirst()
                    .orElseThrow();
            assertThat(held.progress())
                    .as("⚠ BOTH ends of the clock are pushed forward. Shifting only endsAt stretches "
                            + "the transfer instead of pausing it, and the bar crawls backwards")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("⚠ a hold survives the client being closed — the absence must not complete it")
        void aHoldSurvivesAnAbsence(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));
            f.clock().wind(Duration.ofSeconds(2));
            f.game().tick();

            f.session().pauseDownload(f.session().downloads().get(0).orderId());
            double before = f.session().downloads().stream()
                    .filter(DownloadOrder::paused)
                    .findFirst()
                    .orElseThrow()
                    .progress();

            // Four days away. Without the shift in resume(), the held transfer's deadline is long
            // past and it lands on the first tick back — the pause doing exactly the opposite of
            // what it says, and only ever for a player who closed the client.
            f.clock().wind(Duration.ofDays(4));
            f.game().resume();

            DownloadOrder held = f.session().downloads().stream()
                    .filter(DownloadOrder::paused)
                    .findFirst()
                    .orElse(null);
            assertThat(held).as("the held order is still owed").isNotNull();
            assertThat(held.progress()).isEqualTo(before);
        }

        @Test
        @DisplayName("resuming puts it back at the front and it finishes")
        void resumingCompletesIt(@TempDir Path dir) {
            Fixture f = open(dir);
            f.session().purchase(twoOfferings().get(0));
            String only = f.session().downloads().get(0).orderId();

            f.session().pauseDownload(only);
            f.clock().wind(Duration.ofMinutes(5));
            f.game().tick();
            assertThat(f.session().downloads()).as("still owed while held").hasSize(1);

            f.session().resumeDownload(only);
            f.clock().wind(Duration.ofMinutes(5));
            f.game().tick();
            assertThat(f.session().downloads()).as("and it lands once released").isEmpty();
        }
    }

    @Nested
    @DisplayName("reordering")
    class Reordering {

        @Test
        @DisplayName("moving one to the front makes it the download that progresses")
        void theFrontIsWhatRuns(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));

            String second = f.session().downloads().get(1).orderId();
            f.session().moveDownload(second, -1);
            f.clock().wind(Duration.ofSeconds(1));
            f.game().tick();

            List<DownloadOrder> queue = f.session().downloads();
            assertThat(queue.get(0).orderId()).isEqualTo(second);
            assertThat(queue.get(0).active())
                    .as("⚠ a move CAN displace what is downloading — 'put this one first' is the "
                            + "single most useful thing a queue offers, and nothing is lost because "
                            + "the displaced transfer is held rather than cancelled")
                    .isTrue();
        }

        @Test
        @DisplayName("a move past either end is refused rather than silently clamped to a no-op")
        void theEndsRefuse(@TempDir Path dir) {
            Fixture f = open(dir);
            f.session().purchase(twoOfferings().get(0));
            String only = f.session().downloads().get(0).orderId();
            assertThat(f.session().moveDownload(only, -1).succeeded()).isFalse();
            assertThat(f.session().moveDownload(only, 1).succeeded()).isFalse();
        }
    }

    @Nested
    @DisplayName("the bundle")
    class Bundle {

        /** Winds until the bundle's archive has landed, or gives up loudly. */
        private static void settle(Fixture f) {
            for (int i = 0; i < 400 && !f.session().downloads().isEmpty(); i++) {
                f.clock().wind(Duration.ofSeconds(5));
                f.game().tick();
            }
            assertThat(f.session().downloads())
                    .as("the download should have landed by now")
                    .isEmpty();
        }

        @Test
        @DisplayName("one debit at the bundle price, one archive, and the members are inside it")
        void oneDebitOneArchive(@TempDir Path dir) {
            Fixture f = open(dir);
            var window = f.session().market();
            org.junit.jupiter.api.Assumptions.assumeTrue(window.bundle().isPresent(), "no bundle on this shelf");
            var bundle = window.bundle().get();
            java.math.BigInteger before = f.session().balance().wei();

            assertThat(f.session().purchaseBundle().succeeded()).isTrue();

            assertThat(before.subtract(f.session().balance().wei()))
                    .as("⚠ the BUNDLE price plus the fee, never the sum of the retail prices — a loop "
                            + "over purchase() would charge retail per item and discard the discount")
                    .isGreaterThanOrEqualTo(bundle.priceWei())
                    .isLessThan(bundle.fullPriceWei());
            assertThat(f.session().downloads())
                    .as("one purchase, so one download")
                    .hasSize(1);
            assertThat(f.session().downloads().getFirst().bundle()).isTrue();

            settle(f);
            List<StoredFileState> files = f.game().state().files;
            assertThat(files).hasSize(1);
            assertThat(files.getFirst().name).endsWith(Archives.SUFFIX);
            assertThat(files.getFirst().archiveItemTypes)
                    .as("⚠ the archive is the ONLY place the contents are recorded")
                    .containsExactlyElementsOf(bundle.offeringIds());
        }

        @Test
        @DisplayName("unpacking takes time, consumes the archive, and yields one locked package each")
        void extractionYieldsLockedPackages(@TempDir Path dir) {
            Fixture f = open(dir);
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    f.session().market().bundle().isPresent(), "no bundle on this shelf");
            int members = f.session().market().bundle().get().offeringIds().size();
            // ⚠ BEFORE THE FIRST TICK, not before the first assertion. This guard used to sit below
            // settle() — which winds the clock until the download lands — so a block could be found
            // in that window, the payment confirmed, every member correctly released, and the
            // assertion that they are still locked failed against code that was working. Rare
            // enough to pass on a re-run, which is the worst frequency there is.
            // See support/Chains for the measured numbers.
            io.github.stoicswe.eyeandsickle.client.support.Chains.holdOff(f.game());
            f.session().purchaseBundle();
            settle(f);

            StoredFileState archive = f.game().state().files.getFirst();
            String path = archive.directory + "/" + archive.name;

            assertThat(f.session().extract(path).succeeded()).isTrue();
            assertThat(f.game().state().files)
                    .as("⚠ the archive is consumed at COMPLETION, never at the start — an extraction "
                            + "interrupted by a quit must cost nothing rather than everything")
                    .hasSize(1);

            f.clock().wind(Duration.ofMinutes(30));
            f.game().tick();

            List<StoredFileState> out = f.game().state().files;
            assertThat(out).as("the archive is gone and its contents are not").hasSize(members);
            assertThat(out).noneMatch(Archives::isArchive);
            assertThat(out)
                    .as("⚠ every member lands as a vendor .pkg held by the BUNDLE's own payment — "
                            + "if unpacking released them, a bundle would be the one purchase that "
                            + "skips the settlement every other purchase waits for")
                    .allSatisfy(file -> {
                        assertThat(file.name)
                                .endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.PAYLOAD_SUFFIX);
                        assertThat(file.lockedByEntryId).isEqualTo(archive.lockedByEntryId);
                        assertThat(file.lockedByEntryId).isNotBlank();
                        assertThat(io.github.stoicswe.eyeandsickle.engine.rules.Repac.locked(
                                        f.game().state(), file))
                                .isTrue();
                    });

            // And releasing that ONE payment releases the whole bundle — which is the other half of
            // the claim, and the half that would silently not work if the members had been given
            // their own entry ids.
            // ⚠ Waits for the payment to be MINED, not for a duration that usually contains it: a
            // block landing and a transaction confirming are different events, and a standard fee
            // wins its slot against the derived backlog only about 38% of blocks.
            io.github.stoicswe.eyeandsickle.client.support.Chains.settlePayment(
                    f.game(), () -> f.clock().wind(Duration.ofHours(1)));
            assertThat(f.game().state().files)
                    // ⚠ `installableSuffix`, NEVER a literal `.upg`. Firmware releases as `.frm` and
                    // software as `.upg`, and WHICH items a bundle holds is derived from the
                    // character id — random per @TempDir. A hard-coded suffix passes on most runs
                    // and fails whenever the shelf happens to offer the firmware image, which is a
                    // test that trains its reader to re-run.
                    .allSatisfy(file -> assertThat(file.name)
                            .endsWith(io.github.stoicswe.eyeandsickle.engine.rules.Repac.installableSuffix(
                                    file.itemType)));
        }

        @Test
        @DisplayName("⚠ all or nothing: one sold-out member refuses the bundle before any money moves")
        void aBundleIsAllOrNothing(@TempDir Path dir) {
            Fixture f = open(dir);
            var bundle = f.session().market().bundle();
            org.junit.jupiter.api.Assumptions.assumeTrue(bundle.isPresent(), "no bundle on this shelf");

            // ⚠ SOLD OUT is the trigger now, not "already queued". Owning or having queued a copy
            // stopped being a refusal when items stopped stacking (2026-08-04) — the property under
            // test is unchanged and only the way to provoke it moved. Buying the member out is the
            // honest provocation: the shelf genuinely cannot supply the bundle.
            String scarce = bundle.get().offeringIds().getFirst();
            var offering = io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(scarce)
                    .orElseThrow();
            var held = new io.github.stoicswe.eyeandsickle.engine.rules.SaveMarketStock(
                    f.game().state());
            // ⚠ The item's REAL on-offer flag, not a hard-coded `true`. An item on offer is stocked
            // shorter, so taking against the on-offer ration when the item is NOT on offer empties
            // the smaller shelf and leaves the real one still stocked — the bundle then succeeds and
            // the test fails, but only for the character seeds whose bundle happens to be off-offer.
            // Passed alone, failed in the full suite.
            boolean onOffer = f.session().market().dealFor(scarce).isPresent();
            while (io.github.stoicswe.eyeandsickle.engine.rules.MarketStock.inStock(
                    held, offering, onOffer, f.game().now())) {
                io.github.stoicswe.eyeandsickle.engine.rules.MarketStock.take(
                        held, scarce, f.game().now());
            }
            java.math.BigInteger before = f.session().balance().wei();

            assertThat(f.session().purchaseBundle().succeeded()).isFalse();
            assertThat(f.session().balance().wei())
                    .as("refused BEFORE the debit — selling three-quarters of a bundle at the bundle "
                            + "price is worse than refusing, and there is no refund mechanism")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("extracting something that is not an archive is refused in words")
        void onlyArchivesUnpack(@TempDir Path dir) {
            Fixture f = open(dir);
            f.session().purchase(twoOfferings().get(0));
            settle(f);
            StoredFileState pkg = f.game().state().files.getFirst();
            var refused = f.session().extract(pkg.directory + "/" + pkg.name);
            assertThat(refused.succeeded()).isFalse();
            assertThat(refused.message()).contains("not an archive");
        }
    }

    @Nested
    @DisplayName("the order survives")
    class Persistence {

        @Test
        @DisplayName("⚠ the queue is in the SAVE, because the money already moved")
        void theQueueIsPersisted(@TempDir Path dir) {
            Fixture f = open(dir);
            List<String> two = twoOfferings();
            f.session().purchase(two.get(0));
            f.session().purchase(two.get(1));
            f.session().pauseDownload(f.session().downloads().get(1).orderId());
            f.game().persist();

            GameEngine reopened = GameEngine.open(
                    io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                    "operator",
                    f.clock());
            assertThat(DownloadQueue.orders(reopened.state()))
                    .as("a queue that lived in the client would lose paid-for downloads when the "
                            + "window closed, which is indistinguishable from being robbed")
                    .hasSize(2);
            assertThat(DownloadQueue.orders(reopened.state()).get(1).paused).isTrue();
        }
    }
}
