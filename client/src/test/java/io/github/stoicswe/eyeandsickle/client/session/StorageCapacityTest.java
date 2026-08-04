package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.StorageRules;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The shop will not sell you something you have nowhere to put.
 *
 * <h2>What is enforced here, and what deliberately is not</h2>
 *
 * {@code Balance.storageCapacity}'s own note warns that a hard cap of six on the vault with no way
 * to raise it "is a different game from the one that document describes" — and it is right. So
 * nothing here caps the <b>vault</b> and nothing refuses a <b>move</b>. What is enforced is the one
 * narrow thing that was asked for: a purchase needs a slot in the arrivals tier, and the arrivals
 * tier is the roomy one.
 */
class StorageCapacityTest {

    private static final Instant T0 = Instant.parse("2026-08-04T09:00:00Z");

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
        game.credit(Balance.ec("500000"), "TEST", "seed");
        return new Fixture(game, new LocalGameSession(game), clock);
    }

    private static String anOffering() {
        return io.github.stoicswe.eyeandsickle.engine.Catalogue.offerings().stream()
                .filter(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::purchasable)
                .map(io.github.stoicswe.eyeandsickle.engine.Catalogue.Offering::id)
                .findFirst()
                .orElseThrow();
    }

    /** Puts {@code count} items straight into a tier, bypassing the shop. */
    private static void fill(Fixture f, StorageTier tier, int count) {
        for (int i = 0; i < count; i++) {
            ItemState item = new ItemState();
            item.itemType = "filler";
            item.displayName = "Filler " + i;
            item.tier = tier.name();
            f.game().state().items.add(item);
        }
    }

    @Nested
    @DisplayName("where a purchase lands")
    class Arrivals {

        @Test
        @DisplayName("⚠ the arrivals tier is the EXPOSED one, not the vault")
        void arrivalsAreExposed() {
            // If this ever becomes VAULT the whole feature inverts silently: goods would file
            // themselves safely, the tier system would become a setting nobody touches, and the
            // capacity check below would start binding against six slots instead of sixty.
            assertThat(StorageRules.ARRIVALS).isEqualTo(StorageTier.HIGH_HACKABLE_ZONE);
        }
    }

    @Nested
    @DisplayName("what claims a slot")
    class Committed {

        @Test
        @DisplayName("⚠ a QUEUED download claims one, or a player buys a hundred against sixty")
        void queuedOrdersClaimSlots(@TempDir Path dir) {
            Fixture f = open(dir);
            int before = StorageRules.committed(f.game().state(), StorageRules.ARRIVALS);
            f.session().purchase(anOffering());
            assertThat(StorageRules.committed(f.game().state(), StorageRules.ARRIVALS))
                    .as("counting only installed items would let the queue run away and the problem "
                            + "would surface forty installs later, with the money gone")
                    .isEqualTo(before + 1);
        }

        @Test
        @DisplayName("⚠ and a package still sitting in Downloads keeps claiming it")
        void deliveredPackagesStillClaimSlots(@TempDir Path dir) {
            Fixture f = open(dir);
            f.session().purchase(anOffering());
            for (int i = 0; i < 40 && !f.session().downloads().isEmpty(); i++) {
                f.clock().wind(Duration.ofSeconds(5));
                f.game().tick();
            }
            assertThat(f.game().state().files).hasSize(1);
            assertThat(StorageRules.committed(f.game().state(), StorageRules.ARRIVALS))
                    .as("the order is gone but the package is not — a slot released here would let "
                            + "the shop oversell against packages waiting to be installed")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("⚠ a STOLEN package claims nothing — it lands in the vault, not here")
        void stolenPackagesDoNotClaimSlots(@TempDir Path dir) {
            Fixture f = open(dir);
            var stolen = io.github.stoicswe.eyeandsickle.engine.rules.Repac.arrive(
                    f.game().state(),
                    "/Users/operator/Downloads",
                    "tarpit.pkg",
                    "10.0.0.4",
                    1024,
                    "tarpit",
                    null,
                    T0);
            assertThat(stolen).isNotNull();
            assertThat(StorageRules.committed(f.game().state(), StorageRules.ARRIVALS))
                    .as("counting it would make somebody else's shelf refuse a sale over a file the "
                            + "player took for free")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("the refusal")
    class Refusal {

        @Test
        @DisplayName("a full arrivals tier stops the sale, and no money moves")
        void aFullTierRefusesTheSale(@TempDir Path dir) {
            Fixture f = open(dir);
            fill(f, StorageRules.ARRIVALS, Balance.storageCapacity(StorageRules.ARRIVALS));
            java.math.BigInteger before = f.session().balance().wei();

            var refused = f.session().purchase(anOffering());
            assertThat(refused.succeeded()).isFalse();
            assertThat(f.session().balance().wei())
                    .as("refused BEFORE the debit — there is no refund mechanism in this game")
                    .isEqualTo(before);
            assertThat(f.session().downloads()).isEmpty();
        }

        @Test
        @DisplayName("⚠ the refusal names the numbers and the way out")
        void theRefusalIsActionable(@TempDir Path dir) {
            Fixture f = open(dir);
            fill(f, StorageRules.ARRIVALS, Balance.storageCapacity(StorageRules.ARRIVALS));
            String message = f.session().purchase(anOffering()).message();
            // "Not enough room" is a dead end. The way out is that the arrivals tier is the exposed
            // one and moving things off it is what the player was supposed to be doing anyway, so
            // the sentence teaches the mechanic at the moment it starts to matter.
            assertThat(message).contains("high-risk zone");
            assertThat(message).contains(String.valueOf(Balance.storageCapacity(StorageRules.ARRIVALS)));
            assertThat(message).containsAnyOf("vault", "sell it");
        }

        @Test
        @DisplayName("filling the VAULT does not stop a sale — arrivals are a different tier")
        void theVaultIsNotTheConstraint(@TempDir Path dir) {
            Fixture f = open(dir);
            fill(f, StorageTier.VAULT, Balance.storageCapacity(StorageTier.VAULT) + 4);
            assertThat(f.session().purchase(anOffering()).succeeded())
                    .as("the vault's cap is deliberately NOT enforced — Balance.storageCapacity's own "
                            + "note explains why a hard six with no expansion is a different game")
                    .isTrue();
        }

        @Test
        @DisplayName("a bundle needs room for EVERY member, checked once")
        void aBundleNeedsRoomForAllOfIt(@TempDir Path dir) {
            Fixture f = open(dir);
            var bundle = f.session().market().bundle();
            org.junit.jupiter.api.Assumptions.assumeTrue(bundle.isPresent(), "no bundle on this shelf");
            int members = bundle.get().offeringIds().size();
            // One slot short of what the bundle needs.
            fill(f, StorageRules.ARRIVALS, Balance.storageCapacity(StorageRules.ARRIVALS) - members + 1);
            java.math.BigInteger before = f.session().balance().wei();

            assertThat(f.session().purchaseBundle().succeeded())
                    .as("asking per member would pass for the first two and fail on the third with "
                            + "the money already gone")
                    .isFalse();
            assertThat(f.session().balance().wei()).isEqualTo(before);
        }
    }
}
