package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The intelligence file: what a scan leaves behind, and how old it says it is.
 *
 * <p>The file is persisted, which reverses an earlier decision — a report was session-only because
 * the cycle-load line is a snapshot and a stored one would present last week's figure with today's
 * confidence. Dating each finding individually answers that instead of discarding the intelligence,
 * so these tests are mostly about the dates being per-field and honest.
 */
class NodeReportTest {

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

    /**
     * A machine the player has found.
     *
     * <p>⚠ Marked discovered here rather than swept for. A scan refuses a machine no sweep has found —
     * which is the rule, not a fixture convenience — and running a real sweep would make every test
     * in this class depend on the sweep's own draws as well as its own.
     */
    private static String someMachine(SoloGame game) {
        var host = game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        // ⚠ UNDEFENDED, and this is not cosmetic. A deep scan is noticed about a third of the time,
        // and a machine that notices AND is defended cuts the scan off — which files no findings at
        // all. Every test in this class is about what a completed scan leaves behind, so a fixture
        // that let the scan be blocked would fail roughly one run in three for a reason that has
        // nothing to do with what it is testing. ReprisalRules covers the blocked path.
        host.defended = false;
        var node = new io.github.stoicswe.eyeandsickle.solo.state.NodeState();
        node.address = host.address;
        node.label = host.label;
        node.serverId = host.serverId;
        node.tier = host.tier;
        if (game.state().knownNodes.stream().noneMatch(n -> host.address.equals(n.address))) {
            game.state().knownNodes.add(node);
        }
        return host.address;
    }

    /** Runs one scan to completion and returns the game. */
    private static SoloGame scanned(Winding clock, SoloGame game, String address, PortScanTarget target) {
        game.portScan(address, target);
        clock.advance(PortScanRules.durationFor(target).plusSeconds(2));
        game.tick();
        return game;
    }

    @Test
    @DisplayName("a completed scan files a report, and the map marks the machine")
    void aScanFilesAReport(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
        String address = someMachine(game);

        assertThat(NodeReports.any(game.state(), address)).isFalse();
        assertThat(game.net().at(address).orElseThrow().reported()).isFalse();

        scanned(clock, game, address, PortScanTarget.OS_VERSION);

        var report = NodeReports.at(game.state(), address).orElseThrow();
        assertThat(report.any()).isTrue();
        assertThat(report.scans()).isEqualTo(1);
        assertThat(report.knows(PortScanTarget.FIREWALL)).isTrue();
        assertThat(report.knows(PortScanTarget.OS_VERSION)).isTrue();
        // ⚠ Nothing below the depth paid for. A file claiming a vault count from an OS scan would be
        // inventing intelligence.
        assertThat(report.knows(PortScanTarget.VAULT_HIGH)).isFalse();
        assertThat(report.vaultHighCount()).isNegative();
        // And the map now marks it, which is what the list's [i] reads.
        assertThat(game.net().at(address).orElseThrow().reported()).isTrue();
    }

    /**
     * ⚠ A deeper scan ADDS to the file; a shallower one later does not erase what it never looked at.
     *
     * <p>Replacing the report wholesale would throw away a deep scan's vault estimate the next time
     * the player ran a cheap firewall check — which is the exact behaviour that would make paying for
     * a deep scan feel pointless.
     */
    @Test
    @DisplayName("findings accumulate across scans and a shallow rescan erases nothing")
    void findingsAccumulate(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
        String address = someMachine(game);

        scanned(clock, game, address, PortScanTarget.VAULT_MEDIUM);
        var deep = NodeReports.at(game.state(), address).orElseThrow();
        assertThat(deep.known()).isEqualTo(io.github.stoicswe.eyeandsickle.protocol.game
                .NodeReport.total());
        int estimate = deep.vaultMediumEstimate();
        Instant deepAt = deep.when(PortScanTarget.VAULT_MEDIUM);

        clock.advance(Duration.ofHours(6));
        scanned(clock, game, address, PortScanTarget.FIREWALL);

        var after = NodeReports.at(game.state(), address).orElseThrow();
        assertThat(after.vaultMediumEstimate())
                .as("a cheap rescan must not erase what a deep one paid for")
                .isEqualTo(estimate);
        assertThat(after.when(PortScanTarget.VAULT_MEDIUM))
                .as("nor re-date it — that would be stale intelligence wearing a fresh timestamp")
                .isEqualTo(deepAt);
        assertThat(after.when(PortScanTarget.FIREWALL))
                .as("the firewall reading IS fresh, and says so")
                .isAfter(deepAt);
        assertThat(after.scans()).isEqualTo(2);
    }

    /**
     * ⚠ One timestamp for the file would not do, and this is the assertion that says why.
     *
     * <p>{@code updatedAt} moves with any scan. If the panel dated findings from it, a firewall
     * re-check this morning would make a week-old vault estimate look measured this morning.
     */
    @Test
    @DisplayName("updatedAt moves with any scan, but each finding keeps its own date")
    void datesArePerFinding(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
        String address = someMachine(game);

        scanned(clock, game, address, PortScanTarget.CYCLE_LOAD);
        var first = NodeReports.at(game.state(), address).orElseThrow();
        Instant loadAt = first.when(PortScanTarget.CYCLE_LOAD);
        Instant created = first.createdAt();

        clock.advance(Duration.ofDays(7));
        scanned(clock, game, address, PortScanTarget.FIREWALL);

        var later = NodeReports.at(game.state(), address).orElseThrow();
        assertThat(later.createdAt()).as("the file was opened once").isEqualTo(created);
        assertThat(later.updatedAt()).as("and touched again").isAfter(created);
        assertThat(later.when(PortScanTarget.CYCLE_LOAD))
                .as("but the snapshot is still a week old, and the file must say so")
                .isEqualTo(loadAt);
    }

    @Test
    @DisplayName("the file survives a save and reload")
    void itPersists(@TempDir Path dir) {
        Path file = dir.resolve("s.json");
        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(file), "operator", clock);
        String address = someMachine(game);
        scanned(clock, game, address, PortScanTarget.DOWNLOADS);
        long downloads = NodeReports.at(game.state(), address).orElseThrow().downloadsBytes();
        game.persist();

        SoloGame reopened = SoloGame.open(
                new SaveStore(file), "operator", Clock.fixed(clock.instant(), ZoneOffset.UTC));
        var report = NodeReports.at(reopened.state(), address).orElseThrow();
        assertThat(report.downloadsBytes()).isEqualTo(downloads);
        assertThat(report.knows(PortScanTarget.DOWNLOADS)).isTrue();
        assertThat(reopened.net().at(address).orElseThrow().reported()).isTrue();
    }

    /** Every file, newest first — the order somebody looks for one in. */
    @Test
    @DisplayName("RECON lists every file, most recently updated first")
    void reportsAreListedNewestFirst(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        SoloGame game = SoloGame.open(new SaveStore(dir.resolve("s.json")), "operator", clock);
        var machines = game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .limit(3)
                .peek(h -> {
                    h.discovered = true;
                    h.defended = false;
                })
                .map(h -> h.address)
                .toList();
        assertThat(machines).hasSizeGreaterThan(1);

        for (String address : machines) {
            scanned(clock, game, address, PortScanTarget.FIREWALL);
            clock.advance(Duration.ofMinutes(5));
        }
        var all = NodeReports.all(game.state());
        assertThat(all).hasSize(machines.size());
        assertThat(all.getFirst().address())
                .as("the one scanned last is at the top")
                .isEqualTo(machines.getLast());
    }
}
