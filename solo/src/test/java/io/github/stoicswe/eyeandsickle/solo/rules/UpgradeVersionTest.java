package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeOffer;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeVersion;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Catalogue;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Upgrade versions: what they are, and — more importantly — what they are not.
 *
 * <h2>⚠ The invariant this whole feature turns on</h2>
 *
 * A newer build is <b>worth more and supersedes an older one. It is not a better tool.</b> If it were,
 * raiding harder machines would be a capability ladder with no gate on it — a ceiling reachable by
 * grinding, which is Invariant <b>I2</b> arriving from an unexpected direction, and <b>I3</b> broken
 * as well because the item would then sit behind its catalogue gate and a raiding ladder at once.
 * {@link Capability} is the guard, and it is the test to keep if any other here is ever dropped.
 */
class UpgradeVersionTest {

    private static final Instant T0 = Instant.parse("2026-07-30T09:00:00Z");

    private static SoloGame game(Path dir) {
        return SoloGame.open(new SaveStore(dir.resolve("save.json")), "operator", Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("the version type")
    class Type {

        @Test
        @DisplayName("compares numerically, so v1.10 is newer than v1.9")
        void comparesNumerically() {
            // ⚠ The one reason this is a record and not a string. Lexically "v1.10" sorts BEFORE
            // "v1.9", so the single question the type exists to answer would get a wrong answer that
            // looks right — and a player would be told their newer build was the older one.
            assertThat(new UpgradeVersion(1, 10)).isGreaterThan(new UpgradeVersion(1, 9));
            assertThat(new UpgradeVersion(2, 0)).isGreaterThan(new UpgradeVersion(1, 99));
            assertThat(new UpgradeVersion(2, 4).supersedes(new UpgradeVersion(2, 4)))
                    .as("equal is not superseding")
                    .isFalse();
        }

        @Test
        @DisplayName("prints and parses as vMAJOR.MINOR, and round-trips")
        void roundTrips() {
            assertThat(new UpgradeVersion(2, 4)).hasToString("v2.4");
            assertThat(UpgradeVersion.parse("v2.4")).isEqualTo(new UpgradeVersion(2, 4));
            assertThat(UpgradeVersion.parse("2.4")).isEqualTo(new UpgradeVersion(2, 4));
        }

        @Test
        @DisplayName("anything unreadable is the absence of a version, never an exception")
        void unreadableIsUnknown() {
            // ⚠ This parses a field out of a SAVE, which the player can edit and which older builds
            // wrote without the field at all. A save carrying nonsense here must open.
            for (String bad : new String[] {null, "", "  ", "latest", "v", "v1", "v1.x", "vX.2", "1.2.3"}) {
                assertThat(UpgradeVersion.parse(bad)).as("%s", bad).isEqualTo(UpgradeVersion.UNKNOWN);
            }
            assertThat(UpgradeVersion.UNKNOWN.known()).isFalse();
            assertThatThrownBy(() -> new UpgradeVersion(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("⚠ a version buys VALUE, never capability")
    class Capability {

        /**
         * ⚠ The I2 guard. Read this before changing anything in {@code Versions}.
         *
         * <p>Every capability figure the catalogue publishes comes from the catalogue and from
         * nowhere else, so no build of a tool can be a better tool than another build of it. The test
         * is written against the whole catalogue rather than one item, because the failure it guards
         * against is somebody adding a version-aware branch to one offering.
         */
        @Test
        @DisplayName("no build of any tool differs in what it can do")
        void everyBuildIsTheSameTool() {
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                for (int tier = 1; tier <= 5; tier++) {
                    UpgradeVersion version = Versions.on(offering.id(), "10.0.0." + tier, tier);
                    assertThat(version.major())
                            .as("%s tier %d", offering.id(), tier)
                            .isEqualTo(tier);
                    // The catalogue is the single source of every capability figure. If a version
                    // ever reached one, it would have to come through here.
                    assertThat(Catalogue.byId(offering.id()).orElseThrow().equippedCycles())
                            .as("%s cycles must not depend on the build", offering.id())
                            .isEqualTo(offering.equippedCycles());
                    assertThat(Catalogue.byId(offering.id()).orElseThrow().gate())
                            .as("%s gate must not depend on the build", offering.id())
                            .isEqualTo(offering.gate());
                }
            }
        }

        @Test
        @DisplayName("resale rises with the build, and never reaches retail")
        void resaleRisesButStaysBelowRetail() {
            String item = "net-sweep-wide";
            BigInteger retail = Catalogue.byId(item).orElseThrow().priceWei();
            BigInteger low = Repac.resaleValue(item, new UpgradeVersion(1, 0));
            BigInteger high = Repac.resaleValue(item, new UpgradeVersion(5, 0));

            assertThat(high).as("a newer build is worth more").isGreaterThan(low);
            // ⚠ Above retail would make buy-to-resell a money printer. Clamped in Versions rather
            // than trusted to the constants, because the constants are what a re-tune moves — so this
            // must hold at an absurd version too.
            assertThat(Repac.resaleValue(item, new UpgradeVersion(50, 0)))
                    .as("resale must never reach retail, at any build")
                    .isLessThan(retail);
        }

        /**
         * ⚠ Invariant I2's original rule, unchanged by any of this.
         *
         * <p>Only an ethecoin-gated item may be sold at all. A version does not make a
         * schematic-gated tool sellable — if it did, a version would have become a way to launder a
         * gated item into currency, which is the exact thing {@code Repac.sellable} exists to refuse.
         */
        @Test
        @DisplayName("a version never makes a gated tool sellable")
        void versionsDoNotUnlockResale() {
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                for (int major = 1; major <= 5; major++) {
                    boolean sellable = Repac.sellable(offering.id());
                    assertThat(sellable)
                            .as("%s sellability must not depend on the build", offering.id())
                            .isEqualTo(offering.purchasable());
                    if (!sellable) {
                        assertThat(Repac.resaleValue(offering.id(), new UpgradeVersion(major, 0)))
                                .as("%s is not sellable at any build", offering.id())
                                .isZero();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("what a machine carries")
    class OnAMachine {

        @Test
        @DisplayName("the same machine carries the same build every time you look")
        void isDeterministic() {
            // The reason "was this here before?" is answerable at all — the same rule upgradeBytes
            // already follows. A drawn version would change under the player between visits.
            assertThat(Versions.on("net-sweep-wide", "10.0.0.7", 3))
                    .isEqualTo(Versions.on("net-sweep-wide", "10.0.0.7", 3));
        }

        @Test
        @DisplayName("harder machines carry newer builds — the whole reward loop")
        void tierDrivesTheMajor() {
            for (int tier = 1; tier <= 5; tier++) {
                assertThat(Versions.on("net-sweep-wide", "10.0.0.9", tier).major())
                        .isEqualTo(tier);
            }
            // Out-of-range tiers clamp rather than producing a version nothing can read.
            assertThat(Versions.on("x", "y", 0).major()).isEqualTo(1);
            assertThat(Versions.on("x", "y", 99).major()).isEqualTo(5);
        }

        @Test
        @DisplayName("two machines of the same tier are not carrying identical copies")
        void minorScatters() {
            // Otherwise every tier-3 machine in the world would be interchangeable, and the compare
            // view would have nothing to say between two of them.
            boolean anyDifferent = false;
            for (int i = 0; i < 40 && !anyDifferent; i++) {
                anyDifferent = Versions.on("net-sweep-wide", "10.0.0." + i, 3).minor()
                        != Versions.on("net-sweep-wide", "10.0.1." + i, 3).minor();
            }
            assertThat(anyDifferent).isTrue();
        }

        @Test
        @DisplayName("the market sits in the middle of the ladder")
        void theMarketIsTheMiddle() {
            // ⚠ The loop: a hard estate carries something the shop does not, a cheap desktop carries
            // something worse. Selling the newest would make raiding pointless; selling the oldest
            // would make the catalogue a trap.
            assertThat(Balance.MARKET_UPGRADE_VERSION_MAJOR).isBetween(2, 4);
        }
    }

    @Nested
    @DisplayName("Get info, on a package still sitting on somebody else's machine")
    class Inspecting {

        /**
         * A foreign upgrade that resolves to a real tool.
         *
         * <p>⚠ The FIRST resolvable one, not simply the first one. Some app bundles advertise
         * upgrades for tools the solo catalogue does not carry — {@code Breach.app} is one — and those
         * packages are duds that {@code install} already refuses. Taking whichever came first made
         * this test fail on a pre-existing content gap rather than on the feature, which is a test
         * reporting the wrong defect.
         */
        private static String upgradePathOn(SoloGame game, String address) {
            return game.list(address, "/Applications").stream()
                    .filter(entry -> entry.name().endsWith(".app"))
                    .map(entry -> entry.path() + "/Contents/Upgrades")
                    .flatMap(dir -> game.list(address, dir).stream())
                    .map(entry -> entry.path())
                    .filter(path -> game.upgradeAt(address, path).isPresent())
                    .findFirst()
                    .orElse("");
        }

        /**
         * ⚠ And the dud says it is a dud, rather than falling through to the generic bundle note.
         *
         * <p>An unidentifiable package can still be downloaded and then refuses to install, so the
         * cost lands after the transfer. Naming it beforehand is the whole reason this panel exists.
         */
        @Test
        @DisplayName("a package for a tool this rig cannot identify says so before the transfer")
        void unknownPackagesAreNamed(@TempDir Path dir) {
            SoloGame game = game(dir);
            var host = game.state().topology.hosts.stream()
                    .filter(entry -> !"SELF".equals(entry.kind))
                    .findFirst()
                    .orElseThrow();
            host.foothold = true;
            String dud = game.list(host.address, "/Applications").stream()
                    .filter(entry -> entry.name().endsWith(".app"))
                    .map(entry -> entry.path() + "/Contents/Upgrades")
                    .flatMap(d -> game.list(host.address, d).stream())
                    .map(entry -> entry.path())
                    .filter(path -> game.upgradeAt(host.address, path).isEmpty())
                    .findFirst()
                    .orElse("");
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    !dud.isBlank(), "every bundle in this world resolves — nothing to check");
            assertThat(game.info(host.address, dud)).anyMatch(line -> line.contains("no catalogue entry"));
        }

        /**
         * ⚠ The whole point of the feature: answered WITHOUT taking the file.
         *
         * <p>Before this, a stolen upgrade was an opaque {@code .pkg} — a player could not tell what
         * it was, whether they had it, or whether it was worth the transfer, until after paying for
         * the transfer. Everything asserted here is readable off the package's own metadata, which is
         * what a real package carries so a manager can say what it is about to install.
         */
        @Test
        @DisplayName("a foreign upgrade names its tool, its build, and how it compares")
        void answersBeforeTheTransfer(@TempDir Path dir) {
            SoloGame game = game(dir);
            var host = game.state().topology.hosts.stream()
                    .filter(entry -> !"SELF".equals(entry.kind))
                    .findFirst()
                    .orElseThrow();
            host.foothold = true;
            String path = upgradePathOn(game, host.address);
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    !path.isBlank(), "this world's first host carries no app bundle; the shape is covered elsewhere");

            var offer = game.upgradeAt(host.address, path);
            assertThat(offer).isPresent();
            assertThat(offer.get().displayName()).isNotBlank();
            assertThat(offer.get().summary()).isNotBlank();
            assertThat(offer.get().version().known()).isTrue();
            assertThat(offer.get().version().major())
                    .as("the build tracks the host's tier")
                    .isEqualTo(Math.max(1, Math.min(5, host.tier)));
            assertThat(offer.get().standing()).isEqualTo(UpgradeOffer.Standing.NEW);

            // ⚠ And `stat` says the same thing. One source, two surfaces — a terminal that said less
            // than a right-click would send players to the mouse to learn things.
            assertThat(game.info(host.address, path))
                    .anyMatch(line -> line.contains(offer.get().version().toString()));
        }

        @Test
        @DisplayName("a path that is not an upgrade answers nothing rather than something invented")
        void nonUpgradesAreEmpty(@TempDir Path dir) {
            SoloGame game = game(dir);
            assertThat(game.upgradeAt("", "/System/etc/rc.conf")).isEmpty();
            assertThat(game.upgradeAt("", "/Applications")).isEmpty();
        }
    }

    @Nested
    @DisplayName("how it compares to what you hold")
    class Standing {

        private static void own(SoloGame game, String itemType, String version) {
            ItemState item = new ItemState();
            item.itemType = itemType;
            item.displayName = itemType;
            item.version = version;
            game.state().items.add(item);
        }

        @Test
        @DisplayName("owning none of the tool is NEW")
        void newTool(@TempDir Path dir) {
            SoloGame game = game(dir);
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(3, 1)))
                    .isEqualTo(Versions.Standing.NEW);
        }

        @Test
        @DisplayName("newer is UPGRADE, identical is SAME, older is OLDER")
        void theThreeAnswers(@TempDir Path dir) {
            SoloGame game = game(dir);
            own(game, "net-sweep-wide", "v3.1");
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(4, 0)))
                    .isEqualTo(Versions.Standing.UPGRADE);
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(3, 1)))
                    .isEqualTo(Versions.Standing.SAME);
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(2, 8)))
                    .isEqualTo(Versions.Standing.OLDER);
        }

        @Test
        @DisplayName("the newest copy held is what a candidate is compared against")
        void comparesAgainstTheBestHeld(@TempDir Path dir) {
            // A player holding v2.0 and v4.0 is not offered v3.0 as an upgrade — they already have
            // better. Comparing against the first found would make the answer depend on vault order.
            SoloGame game = game(dir);
            own(game, "net-sweep-wide", "v2.0");
            own(game, "net-sweep-wide", "v4.0");
            assertThat(Versions.owned(game.state(), "net-sweep-wide")).isEqualTo(new UpgradeVersion(4, 0));
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(3, 0)))
                    .isEqualTo(Versions.Standing.OLDER);
        }

        /**
         * ⚠ An item installed before versions existed has no recorded build.
         *
         * <p>Saying "you already have this" about a build nobody knows the number of is a claim the
         * game cannot support, so the harmless reading wins: it is an upgrade, and the player may
         * replace it.
         */
        @Test
        @DisplayName("an unversioned item held is superseded rather than matched")
        void legacyItemsAreSuperseded(@TempDir Path dir) {
            SoloGame game = game(dir);
            own(game, "net-sweep-wide", "");
            assertThat(Versions.standing(game.state(), "net-sweep-wide", new UpgradeVersion(1, 0)))
                    .isEqualTo(Versions.Standing.UPGRADE);
        }

        @Test
        @DisplayName("the verdict always names a reason to care, including when yours is newer")
        void olderStillSaysWhy() {
            // ⚠ "Older" is not "don't bother" — an older build is still worth real ethecoin, and a
            // verdict that stopped at the comparison would be the interface deciding for the player.
            UpgradeOffer older = new UpgradeOffer(
                    "net-sweep-wide",
                    "Net Sweep (Wide)",
                    "summary",
                    new UpgradeVersion(2, 0),
                    new UpgradeVersion(4, 0),
                    UpgradeOffer.Standing.OLDER,
                    io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate.ETHECOIN,
                    1_000L,
                    Balance.ec("5"),
                    true,
                    0L);
            assertThat(older.verdict()).contains("sell");
            assertThat(older.worthInstalling()).isFalse();
        }
    }
}
