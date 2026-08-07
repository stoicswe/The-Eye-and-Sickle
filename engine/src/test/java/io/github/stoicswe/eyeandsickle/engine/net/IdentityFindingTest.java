package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
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
 * What a machine is called, and what it costs to find out.
 *
 * <h2>The rule this file exists to hold</h2>
 *
 * A machine's name and its operator's account are a <b>finding</b>, not a fact the world hands over.
 * They come from {@link PortScanTarget#IDENTITY} — the cheapest rung on the port-scan ladder — or from
 * having breached the host, where both are simply in the prompt. Until one of those has happened the
 * map shows an address and nothing else.
 *
 * <p>⚠ Before 2026-08-07 the sweep copied the name straight off ground truth, so every machine
 * arrived already named. That is the regression {@link Gating} guards, and it is the sort that is
 * invisible in review: the field was populated, the map rendered, and nothing said the name had been
 * free.
 */
class IdentityFindingTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    private static final class Winding extends Clock {
        private Instant at;

        Winding(Instant at) {
            this.at = at;
        }

        void advance(Duration by) {
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

    private static GameEngine open(Path dir, Clock clock) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")), "operator", clock);
    }

    /**
     * A machine the player has found, undefended so a noticed scan is not cut off.
     *
     * <p>Same fixture reasoning as {@code NodeReportTest.someMachine}: a defended host that notices
     * blocks the scan and files no findings, which would fail these tests about a third of the time
     * for a reason that has nothing to do with what they are testing.
     */
    private static HostState discovered(GameEngine game) {
        HostState host = game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        host.defended = false;
        if (game.state().knownNodes.stream().noneMatch(n -> host.address.equals(n.address))) {
            NodeState node = new NodeState();
            node.address = host.address;
            node.serverId = host.serverId;
            node.tier = host.tier;
            game.state().knownNodes.add(node);
        }
        return host;
    }

    /** Puts the rig at the top of the compute ladder — a 24-cycle starting rig cannot afford two scans. */
    private static void atTopOfLadder(GameEngine game) {
        for (var rung : io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()) {
            var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
            item.itemType = rung.itemType();
            item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
            game.state().items.add(item);
        }
        io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.reconcile(game.state());
    }

    private static void scan(Winding clock, GameEngine game, String address, PortScanTarget target) {
        atTopOfLadder(game);
        game.portScan(address, target);
        clock.advance(PortScanRules.durationFor(target).plusSeconds(2));
        game.tick();
    }

    @Nested
    @DisplayName("the ladder")
    class Ladder {

        /**
         * ⚠ THE SEVEN CALIBRATED RUNGS MUST NOT HAVE MOVED.
         *
         * <p>{@code IDENTITY} was inserted at the bottom of a ladder whose costs were all
         * {@code f(depth)}, which shifted every other rung's depth up by one. Left alone, that would
         * have raised the price, the duration, the noise and the detection risk of all seven as a side
         * effect — invisibly, because every screen still renders. {@code CLAUDE.md} makes the economy
         * numbers a set that is re-checked together rather than spot-edited, so the formulas were
         * rebased onto {@code PortScanTarget.steps()} instead.
         *
         * <p>These are literal figures rather than a re-derivation of the formula. A formula compared
         * against itself proves nothing; the claim is that these seven costs are what they were
         * before, so the seven numbers have to be written down.
         */
        @Test
        @DisplayName("adding a rung at the bottom did not re-price the seven above it")
        void theCalibratedRungsAreUnchanged() {
            assertThat(PortScanRules.cyclesFor(PortScanTarget.FIREWALL)).isEqualTo(5L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.OS_VERSION)).isEqualTo(7L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.CYCLE_CAPABILITY)).isEqualTo(9L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.CYCLE_LOAD)).isEqualTo(11L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.DOWNLOADS)).isEqualTo(13L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.VAULT_HIGH)).isEqualTo(15L);
            assertThat(PortScanRules.cyclesFor(PortScanTarget.VAULT_MEDIUM)).isEqualTo(17L);

            assertThat(PortScanRules.durationFor(PortScanTarget.FIREWALL).toSeconds())
                    .isEqualTo(15L);
            assertThat(PortScanRules.durationFor(PortScanTarget.VAULT_MEDIUM).toSeconds())
                    .isEqualTo(105L);

            assertThat(PortScanRules.noiseFor(PortScanTarget.FIREWALL)).isEqualTo(2L);
            assertThat(PortScanRules.noiseFor(PortScanTarget.VAULT_MEDIUM)).isEqualTo(14L);
        }

        /**
         * ⚠ Cheapest, but never free — the floors exist so the bottom rung is still a decision.
         *
         * <p>At {@code steps == 0} the duration and noise multipliers both yield zero, and a rung that
         * takes no time and makes no sound is one every player runs on every machine without thinking
         * about it. The floors bind here and provably nowhere else: at {@code steps ≥ 1} the
         * multipliers already exceed them, which is what the rung above being unchanged proves.
         */
        @Test
        @DisplayName("the identity rung is the cheapest on every axis, and still costs something")
        void identityIsCheapestButNotFree() {
            assertThat(PortScanRules.cyclesFor(PortScanTarget.IDENTITY))
                    .isLessThan(PortScanRules.cyclesFor(PortScanTarget.FIREWALL))
                    .isPositive();
            assertThat(PortScanRules.durationFor(PortScanTarget.IDENTITY))
                    .isLessThan(PortScanRules.durationFor(PortScanTarget.FIREWALL))
                    .isPositive();
            assertThat(PortScanRules.noiseFor(PortScanTarget.IDENTITY))
                    .isLessThan(PortScanRules.noiseFor(PortScanTarget.FIREWALL))
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("gating")
    class Gating {

        /**
         * ⚠ THE REGRESSION. Verified against the unfixed code: with {@code node.label = host.label}
         * restored in {@code NetRules.nodeFor} this fails on the first assertion.
         */
        @Test
        @DisplayName("a discovered machine has no name until something establishes one")
        void aSweepDoesNotNameTheMachine(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = open(dir, clock);
            HostState host = discovered(game);

            var sighting = game.net().at(host.address).orElseThrow();
            assertThat(sighting.label()).as("name is not free at discovery").isEmpty();
            assertThat(sighting.operatorName())
                    .as("operator is not free either")
                    .isEmpty();
            // The world still knows perfectly well what it is called — the point is that the PLAYER
            // does not. A test that let ground truth be empty would pass for the wrong reason.
            assertThat(host.label).isNotBlank();
        }

        @Test
        @DisplayName("the identity rung establishes both halves, and the map shows them")
        void theIdentityRungNamesIt(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = open(dir, clock);
            HostState host = discovered(game);

            scan(clock, game, host.address, PortScanTarget.IDENTITY);

            var sighting = game.net().at(host.address).orElseThrow();
            assertThat(sighting.label()).isEqualTo(host.label);
            assertThat(sighting.operatorName()).isEqualTo(VirtualFs.hostUser(host));

            var file = NodeReports.find(game.state(), host.address).orElseThrow();
            assertThat(file.learnedAt).containsKey(PortScanTarget.IDENTITY.name());
        }

        /**
         * The rungs above it come with it, because a scan that reached further necessarily passed
         * through — that is {@code PortScanTarget}'s whole "pick a question, not a tier" design.
         */
        @Test
        @DisplayName("a deeper scan answers the identity rung on the way past")
        void aDeeperScanIncludesIt(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = open(dir, clock);
            HostState host = discovered(game);

            scan(clock, game, host.address, PortScanTarget.OS_VERSION);

            assertThat(game.net().at(host.address).orElseThrow().label()).isEqualTo(host.label);
        }

        /** Your own rig is not something you scan for the name of. */
        @Test
        @DisplayName("the player's own rig is named from the first second")
        void ownRigIsAlwaysNamed(@TempDir Path dir) {
            GameEngine game = open(dir, new Winding(T0));
            String self = game.state().topology.hosts.stream()
                    .filter(h -> "SELF".equals(h.kind))
                    .findFirst()
                    .orElseThrow()
                    .address;
            assertThat(game.net().at(self).orElseThrow().label()).isEqualTo("localhost");
        }
    }

    @Nested
    @DisplayName("a breach")
    class Breaches {

        /** Standing on a machine, the name and the account are in the prompt. Nothing else leaks. */
        @Test
        @DisplayName("establishes the identity, and only the identity")
        void breachingNamesItButTellsYouNothingElse(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = open(dir, clock);
            HostState host = discovered(game);

            breach(game, host);
            game.settleBreachOutcomes();

            var file = NodeReports.find(game.state(), host.address).orElseThrow();
            assertThat(file.hostName).isEqualTo(host.label);
            assertThat(file.operatorName).isEqualTo(VirtualFs.hostUser(host));
            // ⚠ The rest of the ladder is untouched. A foothold does not hand over the vault estimate,
            // and if it did, breaching first would delete the whole scan ladder.
            assertThat(file.learnedAt.keySet()).containsExactly(PortScanTarget.IDENTITY.name());
            assertThat(file.firewallTier).isEqualTo(-1);
            assertThat(file.vaultMediumEstimate).isEqualTo(-1);
            // ⚠ And it is not counted as a scan. A file whose only entry came from a break-in has had
            // no scans, so reporting one would make the detection ratio beside it a fraction of a
            // number that never happened.
            assertThat(file.scans).isZero();
        }

        /**
         * ⚠ "Retains the name chosen on the first successful breach" — the write-once rule.
         *
         * <p>Names are derived from the address, so re-deriving would usually agree. This is what
         * happens when it would not: the stored name is pinned, and a later scan reports the name the
         * player already knows rather than silently renaming a machine they have been working with.
         * Simulated by editing ground truth, which is the same shape as a name-pool edit shifting
         * every derived name at once.
         */
        @Test
        @DisplayName("keeps the name from the first breach even when the world would now say another")
        void theFirstNameSticks(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = open(dir, clock);
            HostState host = discovered(game);

            breach(game, host);
            game.settleBreachOutcomes();
            String asFirstLearned = NodeReports.find(game.state(), host.address).orElseThrow().hostName;

            host.label = "renamed-elsewhere";
            scan(clock, game, host.address, PortScanTarget.IDENTITY);

            assertThat(NodeReports.find(game.state(), host.address).orElseThrow().hostName)
                    .isEqualTo(asFirstLearned)
                    .isNotEqualTo("renamed-elsewhere");
        }

        /**
         * Records a cleared breach the way the engine's own settlement path reads it.
         *
         * <p>⚠ Settled with {@code settleBreachOutcomes()} rather than {@code tick()}. The tick does
         * not call it — {@code resume()} and {@code breachAction} do — so a fixture that ticked would
         * find no file and fail with {@code NoSuchElementException}, which reads as "the breach did
         * not record an identity" rather than as "nothing settled the breach".
         */
        private void breach(GameEngine game, HostState host) {
            ResolutionState resolution = new ResolutionState();
            resolution.outcome = "BREACHED";
            resolution.targetId = "node:" + host.address;
            game.state().resolutions.add(resolution);
        }
    }

    @Nested
    @DisplayName("a character made before machine names existed")
    class LegacyRelabel {

        /** The scheme this replaced: `<server name>-<two-digit index>`. */
        private void wearOldLabels(GameEngine game) {
            int index = 0;
            for (HostState host : game.state().topology.hosts) {
                if (!"SELF".equals(host.kind)) {
                    host.label = String.format("home-relay-%02d", index++);
                }
            }
        }

        @Test
        @DisplayName("gets generated names on load, and the rig is left alone")
        void oldLabelsAreReplaced(@TempDir Path dir) {
            GameEngine game = open(dir, new Winding(T0));
            wearOldLabels(game);

            assertThat(TopologyGenerator.relabelLegacy(game.state())).isTrue();

            for (HostState host : game.state().topology.hosts) {
                if ("SELF".equals(host.kind)) {
                    // ⚠ The rig is called `localhost`, which is not a generated name — without the
                    // SELF guard it would be renamed to something like `sultry-adleman`, which is the
                    // most confusing single outcome available here.
                    assertThat(host.label).isEqualTo("localhost");
                } else {
                    assertThat(NpcNames.looksGenerated(host.label))
                            .as("%s -> %s", host.address, host.label)
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("still gives every machine a distinct name")
        void relabellingKeepsNamesUnique(@TempDir Path dir) {
            GameEngine game = open(dir, new Winding(T0));
            wearOldLabels(game);
            TopologyGenerator.relabelLegacy(game.state());

            List<String> names = game.state().topology.hosts.stream()
                    .filter(h -> !"SELF".equals(h.kind))
                    .map(h -> h.label)
                    .toList();
            assertThat(names).doesNotHaveDuplicates();
        }

        /**
         * ⚠ Write-once would otherwise defend the OLD name forever.
         *
         * <p>A machine breached before this shipped has `home-relay-00` pinned on its recon file, and
         * `NodeReports.merge` refuses to overwrite an established identity — so without correcting the
         * file in the same pass, the map would show the new name and the RECON report the old one, on
         * the same machine, permanently.
         */
        @Test
        @DisplayName("corrects a name already pinned on a recon file")
        void pinnedNamesAreCorrectedToo(@TempDir Path dir) {
            GameEngine game = open(dir, new Winding(T0));
            HostState host = discovered(game);
            wearOldLabels(game);
            NodeReports.establishIdentity(game.state(), host, T0);
            assertThat(NodeReports.find(game.state(), host.address).orElseThrow().hostName)
                    .startsWith("home-relay-");

            TopologyGenerator.relabelLegacy(game.state());

            assertThat(NodeReports.find(game.state(), host.address).orElseThrow().hostName)
                    .isEqualTo(host.label)
                    .matches(NpcNames::looksGenerated);
        }

        /** Idempotent by construction — no flag, so there is no flag to get out of step. */
        @Test
        @DisplayName("does nothing on a world that already has generated names")
        void secondPassIsANoOp(@TempDir Path dir) {
            GameEngine game = open(dir, new Winding(T0));
            wearOldLabels(game);
            TopologyGenerator.relabelLegacy(game.state());
            List<String> after =
                    game.state().topology.hosts.stream().map(h -> h.label).toList();

            assertThat(TopologyGenerator.relabelLegacy(game.state())).isFalse();
            assertThat(game.state().topology.hosts.stream().map(h -> h.label).toList())
                    .isEqualTo(after);
        }
    }
}
