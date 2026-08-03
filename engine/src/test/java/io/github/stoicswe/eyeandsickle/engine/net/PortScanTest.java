package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Looking at somebody else's machine, and the price of looking harder.
 *
 * <p>The mechanic only works if three things hold together: depth costs strictly more on every axis,
 * the findings are stable enough to compare between scans, and the deepest rung never hands over a
 * count of the middle vault. Break any one and the ladder is decoration.
 */
class PortScanTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    private static GameEngine game(Path dir) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("s.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    /**
     * A machine the player has found.
     *
     * <p>⚠ Marked discovered. A scan refuses a machine no sweep has found — the refusal says so in as
     * many words — and every host in the world is in the topology whether or not anyone has seen it.
     */
    private static HostState target(GameEngine game) {
        HostState host = game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        return host;
    }

    /** The same machine, left undiscovered — for the refusal that check exists to produce. */
    private static HostState unseen(GameEngine game) {
        return game.state().topology.hosts.stream()
                .filter(h -> !"SELF".equals(h.kind))
                .filter(h -> !h.discovered)
                .findFirst()
                .orElseThrow();
    }

    /** ⚠ Every axis rises together, or "deeper" is not a decision. */
    @Test
    @DisplayName("depth costs strictly more cycles, more time and more risk")
    void depthCostsMoreOnEveryAxis(@TempDir Path dir) {
        HostState host = target(game(dir));
        long cycles = -1;
        long seconds = -1;
        int risk = -1;
        for (PortScanTarget rung : PortScanTarget.values()) {
            assertThat(PortScanRules.cyclesFor(rung)).as("%s cycles", rung).isGreaterThan(cycles);
            assertThat(PortScanRules.durationFor(rung).toSeconds())
                    .as("%s seconds", rung)
                    .isGreaterThan(seconds);
            assertThat(PortScanRules.riskPercent(host, rung))
                    .as("%s risk", rung)
                    .isGreaterThan(risk);
            cycles = PortScanRules.cyclesFor(rung);
            seconds = PortScanRules.durationFor(rung).toSeconds();
            risk = PortScanRules.riskPercent(host, rung);
        }
        // ⚠ Never free and never certain. A floor stops the shallowest scan farming information at
        // no risk; a ceiling stops the deepest being pointless against a hard target, which would
        // remove the option rather than price it.
        assertThat(PortScanRules.riskPercent(host, PortScanTarget.FIREWALL)).isGreaterThanOrEqualTo(3);
        assertThat(PortScanRules.riskPercent(host, PortScanTarget.deepest())).isLessThanOrEqualTo(70);
    }

    /** A better-defended machine is harder to look at, which is what a firewall is. */
    @Test
    @DisplayName("a harder firewall raises the risk at every depth")
    void firewallRaisesRisk(@TempDir Path dir) {
        HostState soft = target(game(dir));
        soft.firewallTier = 0;
        HostState hard = target(game(dir));
        hard.firewallTier = 3;
        for (PortScanTarget rung : PortScanTarget.values()) {
            assertThat(PortScanRules.riskPercent(hard, rung))
                    .as("%s", rung)
                    .isGreaterThan(PortScanRules.riskPercent(soft, rung));
        }
    }

    /**
     * ⚠ Two scans of an unchanged machine must AGREE.
     *
     * <p>Findings are derived from the host rather than rolled, because "was this here before?" is a
     * question this game asks the player constantly, and a readout that answered differently each
     * time would be noise dressed as intelligence.
     */
    @Test
    @DisplayName("findings are stable between scans of the same machine")
    void findingsAreStable(@TempDir Path dir) {
        HostState host = target(game(dir));
        assertThat(PortScanRules.osOf(host)).isEqualTo(PortScanRules.osOf(host)).isNotBlank();
        assertThat(PortScanRules.capabilityOf(host)).isEqualTo(PortScanRules.capabilityOf(host));
        assertThat(PortScanRules.vaultHighOf(host)).isEqualTo(PortScanRules.vaultHighOf(host));
        assertThat(PortScanRules.vaultMediumOf(host)).isEqualTo(PortScanRules.vaultMediumOf(host));
    }

    /**
     * ⚠ The load is the one finding that MOVES, and it has to.
     *
     * <p>It is published as "a snapshot, stale the moment it is taken". A figure that never changed
     * would make that warning a lie.
     */
    @Test
    @DisplayName("the cycle load moves between snapshots but stays inside the machine")
    void loadIsASnapshot(@TempDir Path dir) {
        HostState host = target(game(dir));
        long capability = PortScanRules.capabilityOf(host);
        boolean moved = false;
        for (int minute = 0; minute < 40; minute++) {
            long load = PortScanRules.loadOf(host, T0.plus(Duration.ofMinutes(minute)));
            assertThat(load).isBetween(0L, capability);
            moved |= load != PortScanRules.loadOf(host, T0);
        }
        assertThat(moved)
                .as("a load that never changed would make the snapshot warning a lie")
                .isTrue();
    }

    /**
     * ⚠ The band NARROWS with repeated deep scans and NEVER closes.
     *
     * <p>The middle tier is not readable from outside at any depth — that is what
     * {@code docs/design/01-core-resources.md} §6 buys with the tier. A band that reached zero would
     * hand over a count and make the tier a formality.
     */
    @Test
    @DisplayName("the medium-vault estimate narrows with rescans and never becomes a count")
    void theEstimateNarrowsAndNeverCloses(@TempDir Path dir) {
        HostState host = target(game(dir));
        host.firewallTier = 2;
        int previous = Integer.MAX_VALUE;
        for (int scans = 0; scans < 20; scans++) {
            int error = PortScanRules.vaultMediumErrorOf(host, scans);
            assertThat(error).as("scan %d", scans).isPositive().isLessThanOrEqualTo(previous);
            previous = error;
        }
        assertThat(PortScanRules.vaultMediumErrorOf(host, 1_000)).isPositive();
    }

    /** ⚠ The refusal promises "no machine that a sweep has found", so the rule must mean it. */
    @Test
    @DisplayName("a machine no sweep has found cannot be scanned")
    void notAnUndiscoveredMachine(@TempDir Path dir) {
        GameEngine game = game(dir);
        assertThat(game.portScan(unseen(game).address, PortScanTarget.FIREWALL).refusal())
                .isEqualTo(PortScanRules.Refusal.UNKNOWN_HOST);
    }

    @Test
    @DisplayName("scanning your own rig is refused, and says what to use instead")
    void notYourOwnRig(@TempDir Path dir) {
        GameEngine game = game(dir);
        String self = game.state().topology.hosts.stream()
                .filter(h -> "SELF".equals(h.kind))
                .findFirst()
                .map(h -> h.address)
                .orElseThrow();
        assertThat(game.portScan(self, PortScanTarget.FIREWALL).refusal())
                .isEqualTo(PortScanRules.Refusal.YOUR_OWN_RIG);
    }

    /** ⚠ The cycles are HELD, so the readout still reconciles while a scan is in flight. */
    @Test
    @DisplayName("a running scan holds its cycles and the budget still adds up")
    void cyclesAreHeld(@TempDir Path dir) {
        GameEngine game = game(dir);
        long before = game.computeBudget().available().cycles();
        var unaccounted = game.computeBudget().unaccountedFor();
        var started = game.portScan(target(game).address, PortScanTarget.DOWNLOADS);

        assertThat(started.refusal()).as("refusal").isNull();
        assertThat(started.succeeded()).isTrue();
        assertThat(game.computeBudget().available().cycles()).isEqualTo(before - started.cycles());
        // ⚠ NOT reconciles(). A fresh rig carries the tutorial parasite, and an undiscovered miner
        // making the numbers fail to add up is the DESIGNED behaviour — docs/design/04 §3.1 makes
        // that discrepancy the way a player detects one. What must hold is that a port scan does not
        // change it: the scan's cycles are accounted for, so the gap is exactly what it was.
        assertThat(game.computeBudget().unaccountedFor()).isEqualTo(unaccounted);
        // One at a time per machine — a second would charge twice for one answer.
        assertThat(game.portScan(target(game).address, PortScanTarget.DOWNLOADS).refusal())
                .isEqualTo(PortScanRules.Refusal.ALREADY_RUNNING);
    }
}
