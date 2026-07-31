package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.Catalogue;
import io.github.stoicswe.eyeandsickle.solo.breach.Targets;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.solo.state.ServerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import io.github.stoicswe.eyeandsickle.solo.state.TopologyState;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reach, settlement, documents and the read model.
 *
 * <p>Four of these tests exist to hold an invariant that nothing else in the codebase can hold for
 * them: {@code noPurchasableItemMovesTheCeiling} is Invariant I2, {@code lootIsCollectedExactlyOnce}
 * is {@code docs/design/03-economy.md} §5's faucet discipline, {@code materialNeedsTierThree} is
 * Invariant I13, and {@code aSweepNeverNamesAType} is {@code docs/design/02-unlock-gates.md} §5's
 * pricing check. Each of the four would fail silently in play — the game would keep working and be a
 * different, easier game — which is exactly the class of bug a test has to catch.
 */
class NetRulesTest {

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    @Nested
    @DisplayName("reach — Invariant I2")
    class Reach {

        @Test
        @DisplayName("nothing on the market moves the hop ceiling, at any price")
        void noPurchasableItemMovesTheCeiling() {
            // ⚠ docs/design/07-recon-tools.md §2: the Topology Mapper is "a CEILING on information
            // (1 hop → 2 hops), hence schematic-gated not purchasable (Invariant I2)". This
            // enumerates the whole catalogue rather than checking the two sweep tiers, because the
            // failure this guards against is a FUTURE offering quietly gaining a reach effect.
            SoloSave save = NetTestKit.world(seed(1));
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                ItemState item = new ItemState();
                item.itemType = offering.id();
                item.displayName = offering.name();
                item.acquiredAt = NetTestKit.T0;
                save.items.add(item);
                assertThat(NetRules.hopCeiling(save))
                        .as("ceiling after owning %s", offering.id())
                        .isEqualTo(1);
            }
            // Owning every sweep tier at once changes nothing either: what they buy is sensitivity.
            assertThat(NetRules.hopCeiling(save)).isEqualTo(1);

            // One thing moves it, and it is not for sale.
            save.schematics.add(NetRules.TOPOLOGY_MAPPER);
            assertThat(NetRules.hopCeiling(save)).isEqualTo(2);
        }

        @Test
        @DisplayName("hop distance is measured over the full graph, not over what the player knows")
        void undiscoveredMachinesStillConduct() {
            SoloSave save = NetTestKit.world(seed(2));
            var hopsBefore = NetRules.hopsFrom(save, save.topology.playerAddress);
            NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            var hopsAfter = NetRules.hopsFrom(save, save.topology.playerAddress);

            // ⚠ A BFS over the discovered subgraph would make the ceiling widen as the player learned
            // things, which is reach for free — Invariant I2 wearing a graph algorithm's clothes.
            assertThat(hopsAfter).isEqualTo(hopsBefore);
            assertThat(hopsBefore).hasSize(save.topology.hosts.size());
        }
    }

    @Nested
    @DisplayName("settlement — footholds and the one-time payout")
    class Settlement {

        @Test
        @DisplayName("a successful breach grants a foothold and pays out")
        void breachingGrantsAFoothold() {
            SoloSave save = NetTestKit.world(seed(3));
            SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            String address = report.foundAddresses().getFirst();
            HostState host = NetTestKit.host(save.topology, address);
            java.math.BigInteger before = save.ethecoinWei;

            save.resolutions.add(breached(address));
            assertThat(NetRules.reconcileFootholds(save, NetTestKit.T0)).isTrue();

            assertThat(host.foothold).isTrue();
            assertThat(host.looted).isTrue();
            assertThat(save.ethecoinWei.subtract(before)).isEqualTo(host.lootWei);
            assertThat(NetRules.connect(save, address, NetTestKit.T0)).isTrue();
        }

        @Test
        @DisplayName("a host pays exactly once, however many times settlement runs")
        void lootIsCollectedExactlyOnce() {
            // ⚠ The economic guard. reconcileFootholds is called on every resume and after every
            // breach, and there is no "settled" flag on the resolution — idempotency comes from the
            // host recording that it has been looted, one way. Without that, a payout would repeat on
            // every load, which turns a fixed stock into an unbounded faucet and blows straight
            // through docs/design/03-economy.md §5 rule 1.
            SoloSave save = NetTestKit.world(seed(4));
            SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            String address = report.foundAddresses().getFirst();
            HostState host = NetTestKit.host(save.topology, address);
            java.math.BigInteger before = save.ethecoinWei;

            save.resolutions.add(breached(address));
            for (int i = 0; i < 10; i++) {
                NetRules.reconcileFootholds(save, NetTestKit.T0.plusSeconds(i));
            }
            assertThat(save.ethecoinWei.subtract(before)).isEqualTo(host.lootWei);

            // And a duplicated resolution row — a bad merge, a hand-edited save — still pays once.
            save.resolutions.add(breached(address));
            NetRules.reconcileFootholds(save, NetTestKit.T0.plusSeconds(99));
            assertThat(save.ethecoinWei.subtract(before)).isEqualTo(host.lootWei);
        }

        @Test
        @DisplayName("a failed or aborted attempt grants nothing")
        void onlyBreachedCounts() {
            SoloSave save = NetTestKit.world(seed(5));
            SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            String address = report.foundAddresses().getFirst();
            java.math.BigInteger before = save.ethecoinWei;

            ResolutionState failed = breached(address);
            failed.outcome = "FAILED";
            ResolutionState aborted = breached(address);
            aborted.outcome = "ABORTED";
            save.resolutions.add(failed);
            save.resolutions.add(aborted);

            assertThat(NetRules.reconcileFootholds(save, NetTestKit.T0)).isFalse();
            assertThat(NetTestKit.host(save.topology, address).foothold).isFalse();
            assertThat(save.ethecoinWei).isEqualTo(before);
        }

        @Test
        @DisplayName("a crack against the player's own rig is not a network settlement")
        void cracksAreNotFootholds() {
            SoloSave save = NetTestKit.world(seed(6));
            ResolutionState crack = new ResolutionState();
            crack.targetId = "miner:whatever";
            crack.outcome = "BREACHED";
            save.resolutions.add(crack);

            // The two target kinds are not variants of one thing (Targets' class note), and a crack's
            // prize is a buffer that already exists rather than a host's payout.
            assertThat(NetRules.reconcileFootholds(save, NetTestKit.T0)).isFalse();
        }

        private static ResolutionState breached(String address) {
            ResolutionState resolution = new ResolutionState();
            resolution.targetId = "node:" + address;
            resolution.outcome = "BREACHED";
            resolution.at = NetTestKit.T0;
            return resolution;
        }
    }

    @Nested
    @DisplayName("documents — Invariant I13 and decision N-4")
    class Documents {

        @Test
        @DisplayName("schematic material needs tier 3, however deep the machine is")
        void materialNeedsTierThree() {
            // ⚠ Invariant I13. docs/design/10-botnets.md §1a's exploit guard, in a new costume: if a
            // deep-but-easy host paid material, the optimal play would be to farm the softest thing
            // that qualifies — the exact failure the tier gate exists to close. A deep-but-easy host
            // yields flavour and nothing else.
            SoloSave save = worldWithADocument(7);
            HostState carrier = firstDocumentHost(save.topology);
            carrier.foothold = true;
            carrier.tier = Balance.SCHEMATIC_MATERIAL_MIN_TIER - 1;

            int materialBefore = save.schematicMaterial;
            NetDocument recovered =
                    NetRules.download(save, carrier.address, NetTestKit.T0).orElseThrow();

            assertThat(recovered.schematicMaterial()).isZero();
            assertThat(save.schematicMaterial).isEqualTo(materialBefore);
            assertThat(recovered.title()).isNotEmpty();
        }

        @Test
        @DisplayName("a tier-3 machine pays material into the same pool as every other source")
        void tierThreePaysMaterial() {
            SoloSave save = worldWithADocument(8);
            HostState carrier = firstDocumentHost(save.topology);
            carrier.foothold = true;
            carrier.tier = Balance.SCHEMATIC_MATERIAL_MIN_TIER;

            int before = save.schematicMaterial;
            NetDocument recovered =
                    NetRules.download(save, carrier.address, NetTestKit.T0).orElseThrow();

            assertThat(recovered.schematicMaterial()).isEqualTo(Balance.SCHEMATIC_MATERIAL_PER_BREACH);
            assertThat(save.schematicMaterial).isEqualTo(before + Balance.SCHEMATIC_MATERIAL_PER_BREACH);
            // Pace, never reach: the pool still needs SCHEMATIC_MATERIAL_PER_UNLOCK to unlock
            // anything, and the tier gate above means material never shortcuts a ceiling the player
            // has not already reached.
            assertThat(Balance.SCHEMATIC_MATERIAL_PER_UNLOCK).isGreaterThan(1);
        }

        @Test
        @DisplayName("a fragment can be pulled once, and only from a machine you hold")
        void downloadNeedsAFootholdAndPaysOnce() {
            SoloSave save = worldWithADocument(9);
            HostState carrier = firstDocumentHost(save.topology);

            assertThat(NetRules.download(save, carrier.address, NetTestKit.T0)).isEmpty();
            carrier.foothold = true;
            assertThat(NetRules.download(save, carrier.address, NetTestKit.T0)).isPresent();
            assertThat(NetRules.download(save, carrier.address, NetTestKit.T0.plusSeconds(1)))
                    .isEmpty();

            assertThat(NetRules.documents(save)).hasSize(1);
            assertThat(NetRules.documents(save).getFirst().recoveredFrom()).isEqualTo(carrier.address);
            assertThat(save.topology.documents).containsExactly(carrier.documentId);
        }

        @Test
        @DisplayName("home carries no fragments at all — N-4 made structural")
        void nothingOnTheEarlyPathDependsOnStory() {
            for (int i = 0; i < 1_000; i++) {
                TopologyState topology = NetTestKit.world(seed(i)).topology;
                for (HostState host : NetTestKit.hostsOn(topology, NetTestKit.home(topology).serverId)) {
                    // docs/design/15-open-questions.md N-2's ordered critical-path beats are unwritten.
                    // Wiring progression to the flavour layer would block this whole feature on a
                    // narrative pass that has not happened.
                    assertThat(host.documentId).isEmpty();
                }
            }
        }

        private static HostState firstDocumentHost(TopologyState topology) {
            for (HostState host : topology.hosts) {
                if (!host.documentId.isEmpty()) {
                    return host;
                }
            }
            return null;
        }

        /**
         * The first world from {@code start} onwards that has a fragment anywhere in it.
         *
         * <p>Searched rather than assumed. Document chance is 0.05 at depth 1 and only stores and
         * sentries are eligible, so a shallow world can legitimately carry none — and a test that
         * pinned one seed would be one re-tune away from failing for a reason that has nothing to do
         * with what it is checking.
         */
        private static SoloSave worldWithADocument(int start) {
            for (int i = start; i < start + 200; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                if (firstDocumentHost(save.topology) != null) {
                    return save;
                }
            }
            throw new IllegalStateException("no world in 200 seeds carried a story fragment");
        }
    }

    @Nested
    @DisplayName("the counter-hack")
    class CounterHack {

        @Test
        @DisplayName("a player who has never left home is never counter-hacked")
        void homeIsSafe() {
            // ⚠ Asserted over seeds AND as a constant, because these are different claims. The
            // constant says the table cannot be re-tuned into hurting a beginner by accident; the
            // seeds say the code actually reads it.
            assertThat(Balance.netCounterHackChance(0)).isEqualTo(Balance.NET_COUNTER_HACK_HOME);
            assertThat(Balance.NET_COUNTER_HACK_HOME).isZero();

            for (int i = 0; i < 2_000; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                int minersBefore = save.rig.foreignMiners.size();
                SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

                assertThat(report.counterHacked()).as("seed %d", seed(i)).isFalse();
                assertThat(save.rig.foreignMiners).hasSize(minersBefore);
                assertThat(save.personalHeat).isZero();
            }
        }

        @Test
        @DisplayName("sweeping deep can get you hacked back, and it plants a real, crackable miner")
        void depthBitesBack() {
            int hits = 0;
            int attempts = 0;
            for (int i = 0; i < 800 && hits < 10; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                HostState deep = deepestFoothold(save.topology);
                if (deep == null) {
                    continue;
                }
                attempts++;
                deep.foothold = true;
                NetRules.connect(save, deep.address, NetTestKit.T0);

                int minersBefore = save.rig.foreignMiners.size();
                SweepReport report = NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                if (!report.counterHacked()) {
                    continue;
                }
                hits++;
                assertThat(save.rig.foreignMiners).hasSize(minersBefore + 1);
                assertThat(save.personalHeat).isPositive();

                var planted = save.rig.foreignMiners.getLast();
                assertThat(planted.tier).isBetween(1, 3);
                // ⚠ The clock discipline. Both fields default to Instant.now(); leaving them there
                // dates a fresh miner to the real world's present regardless of the session clock,
                // and every accrual computed from it is wrong under a test clock and invisible in
                // production.
                assertThat(planted.deployedAt).isEqualTo(NetTestKit.T0);
                assertThat(planted.lastAccruedAt).isEqualTo(NetTestKit.T0);

                // ⚠ Invariant I9's payoff: the planted miner is a crack target on the player's own
                // rig, which generates NO heat on any outcome. Getting counter-hacked hands the
                // player the safest teaching target in the game.
                // ⚠ Audited first, because an unaudited parasite is not a target. The counter-hack
                // announces itself in the log — "something swept back" — but announcing the EVENT
                // and naming the PROCESS are different things, and only the second makes it
                // crackable. What is asserted here is that a real, crackable miner was planted.
                planted.discovered = true;
                boolean crackable = Targets.available(save).stream().anyMatch(BreachTarget::minerCrack);
                assertThat(crackable).isTrue();
            }
            assertThat(attempts).as("some world had a deep server to stand on").isPositive();
            assertThat(hits).as("a deep sweep sometimes bites back").isPositive();
        }

        /** A host on the deepest server this world has, or null when it never got past depth 1. */
        private static HostState deepestFoothold(TopologyState topology) {
            ServerState deepest = null;
            for (ServerState server : topology.servers) {
                if (deepest == null || server.depthFromHome > deepest.depthFromHome) {
                    deepest = server;
                }
            }
            if (deepest == null || deepest.depthFromHome < 2) {
                return null;
            }
            return NetTestKit.hostsOn(topology, deepest.serverId).getFirst();
        }
    }

    @Nested
    @DisplayName("the read model — four products, four gates, no overlap")
    class ReadModel {

        @Test
        @DisplayName("a sweep never names a type: existence and adjacency is all it sells")
        void aSweepNeverNamesAType() {
            // ⚠ docs/design/02-unlock-gates.md §5's pricing check. A sweep that named types would
            // delete the 15 EC Passive Sniffer at the point of rendering. What the sweep sells is
            // existence and adjacency; what the Sniffer sells is identity; what the Traffic Analyzer
            // sells is live-versus-dormant; what the Honeypot Detector sells is traps, with residual
            // doubt.
            SoloSave save = NetTestKit.world(seed(11));
            NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

            for (var node : save.knownNodes) {
                assertThat(node.kind).isEqualTo(HostKind.UNKNOWN.name());
                assertThat(node.trafficAnalyzed).isFalse();
                assertThat(node.honeypotSuspected).isFalse();
                assertThat(node.discoveredAt).isEqualTo(NetTestKit.T0);
            }
            for (Sighting sighting : NetRules.view(save).sightings()) {
                if (sighting.address().equals(save.topology.playerAddress)) {
                    continue;
                }
                assertThat(sighting.kind()).isEqualTo(HostKind.UNKNOWN);
                assertThat(sighting.bridgePeerServerName()).isEmpty();
                assertThat(sighting.honeypotSuspected()).isFalse();
            }
            // And the breach target list keeps the banner empty, which is the same claim one module
            // over — Targets reads NodeState.kind and prints nothing for UNKNOWN.
            for (BreachTarget target : Targets.available(save)) {
                if (!target.minerCrack()) {
                    assertThat(target.role()).isEmpty();
                }
            }
        }

        @Test
        @DisplayName("the map always names the server the player is standing on")
        void theCurrentServerIsAlwaysPublished() {
            SoloSave save = NetTestKit.world(seed(12));
            NetMap map = NetRules.view(save);

            // The brief: "the graph always shows the server the player is currently connected to."
            assertThat(map.currentServer().serverId()).isEqualTo(save.topology.homeServerId);
            assertThat(map.currentServer().home()).isTrue();
            assertThat(map.currentServer().depthFromHome()).isZero();
            assertThat(map.currentServer().name()).isNotEmpty();
            assertThat(map.vantageAddress()).isEqualTo(save.topology.playerAddress);
            assertThat(map.hopCeiling()).isEqualTo(1);
        }

        @Test
        @DisplayName("an ungenerated world yields an empty map rather than an exception")
        void anAbsentTopologyIsNotACrash() {
            // A save written before the topology existed must open. Null reads as "no network",
            // which is honest — those characters keep working with an empty map rather than being
            // handed a freshly rolled world on load.
            //
            // ⚠ Built from a bare SoloSave rather than from SoloGame.newCharacter, and that is the
            // accurate model as well as the durable one. A legacy save reaches the engine through
            // Jackson, which leaves a field the document does not mention at its initialiser — so
            // `new SoloSave()` is literally what deserialising one produces. Going through
            // newCharacter would additionally break the day the integrator adds
            // TopologyGenerator.generate to it, and would then be asserting the opposite of the
            // thing that had just been built.
            SoloSave legacy = new SoloSave();
            assertThat(legacy.topology).isNull();
            assertThat(NetRules.view(legacy)).isEqualTo(NetMap.empty());
            assertThat(NetRules.view(legacy).isEmpty()).isTrue();
            assertThat(NetRules.beginSweep(legacy, SweepTier.BASE, NetTestKit.T0))
                    .isEmpty();
            assertThat(NetRules.documents(legacy)).isEmpty();
            assertThat(NetRules.connect(legacy, "10.0.0.2", NetTestKit.T0)).isFalse();
            assertThat(NetRules.reconcileFootholds(legacy, NetTestKit.T0)).isFalse();
        }

        @Test
        @DisplayName("the player's own rig is on the map, as the vantage, with no breach tier")
        void theRigIsAlwaysDrawn() {
            SoloSave save = NetTestKit.world(seed(13));
            NetMap map = NetRules.view(save);

            Optional<Sighting> rig = map.at(save.topology.playerAddress);
            assertThat(rig).isPresent();
            assertThat(rig.get().kind()).isEqualTo(HostKind.SELF);
            assertThat(rig.get().vantage()).isTrue();
            assertThat(rig.get().hopsFromVantage()).isZero();
            assertThat(rig.get().label()).isEqualTo("localhost");
            // ⚠ Null, deliberately. DifficultyTier is a 1–5 scale for "how hard is this to breach",
            // and the answer for your own machine is not a number on that scale. The list renders it
            // as "--"; a fabricated T1 would read as an invitation.
            assertThat(rig.get().tier()).isNull();
        }

        @Test
        @DisplayName("every link the map publishes has both ends on the map")
        void theMapIsInternallyConsistent() {
            for (int i = 0; i < 200; i++) {
                SoloSave save = NetTestKit.world(seed(i));
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                NetMap map = NetRules.view(save);

                for (var link : map.links()) {
                    assertThat(map.at(link.fromAddress())).isPresent();
                    assertThat(map.at(link.toAddress())).isPresent();
                }
                for (Sighting sighting : map.sightings()) {
                    assertThat(map.knownServers())
                            .as("server %s is published", sighting.serverId())
                            .anyMatch(s -> s.serverId().equals(sighting.serverId()));
                }
            }
        }
    }
}
