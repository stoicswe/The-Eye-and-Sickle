package io.github.stoicswe.eyeandsickle.solo.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.solo.SoloGame;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.ResolutionState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A breached machine must show as held on the map.
 *
 * <h2>⚠ The bug this exists for: {@code reconcileFootholds} had no caller</h2>
 *
 * {@code NetRules.reconcileFootholds} is what turns a {@code BREACHED} resolution into a foothold and
 * pays the host's one-time loot. It was written, documented, and covered by five tests — and
 * <b>nothing in the shipping game ever called it</b>. Every caller was a test. So a player could clear
 * every layer of a breach, watch the attempt report success, and find the machine still reading
 * {@code contact} on the map, still refusing {@code connect}, and still holding its loot.
 *
 * <p>It is the exact shape of failure a well-tested unit invites: the unit is correct, its tests pass,
 * and the wiring is the part nobody asserted. These tests are deliberately written against
 * {@link SoloGame} rather than {@code NetRules} — one level up from the unit, which is the only level
 * at which the defect is visible at all.
 */
class FootholdAfterBreachTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    private static SoloGame game(Path dir) {
        return SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    /**
     * A machine on the map that has been found but not yet taken.
     *
     * <p>⚠ Visibility on the map comes from {@code knownNodes}, NOT from {@code host.discovered} —
     * {@code NetRules.view} builds its sighting list from the former. A fixture that sets only the
     * flag produces a host the map has never heard of, and the lookup below fails with
     * {@code NoSuchElement} rather than with anything that names the real problem. Both are set here
     * because a sweep sets both.
     */
    private static HostState aDiscoveredHost(SoloGame game) {
        HostState host = game.state().topology.hosts.stream()
                .filter(entry -> !"SELF".equals(entry.kind))
                .filter(entry -> !entry.foothold)
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        io.github.stoicswe.eyeandsickle.solo.state.NodeState node =
                new io.github.stoicswe.eyeandsickle.solo.state.NodeState();
        node.address = host.address;
        node.label = host.label;
        game.state().knownNodes.add(node);
        return host;
    }

    /**
     * Files a cleared attempt against a host, the way {@code BreachRules.record} does.
     *
     * <p>Written directly rather than by playing a breach: the puzzles are the subject of their own
     * suite, and threading a solved board through here would make this test fail for reasons that
     * have nothing to do with whether a win reaches the map.
     */
    private static void breached(SoloGame game, HostState host) {
        ResolutionState resolution = new ResolutionState();
        resolution.targetId = "node:" + host.address;
        resolution.outcome = "BREACHED";
        resolution.difficultyTier = 1;
        resolution.at = T0;
        game.state().resolutions.add(resolution);
    }

    private static Sighting on(NetMap map, String address) {
        return map.sightings().stream()
                .filter(sighting -> sighting.address().equals(address))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("a cleared breach shows as a foothold on the map, not as contact")
    void aWinReachesTheMap(@TempDir Path dir) {
        SoloGame game = game(dir);
        HostState host = aDiscoveredHost(game);
        assertThat(on(game.net(), host.address).foothold())
                .as("the fixture must start from a machine that is NOT held")
                .isFalse();

        breached(game, host);
        game.settleBreachOutcomes();

        // This is the whole bug: the STATE column reads `foothold` off the sighting, so a resolution
        // that never becomes one leaves the map saying `contact` on a machine the player just took.
        assertThat(on(game.net(), host.address).foothold())
                .as("a BREACHED resolution must grant the foothold")
                .isTrue();
    }

    @Test
    @DisplayName("the foothold survives a reload, and is not paid for twice")
    void itSettlesOnLoadToo(@TempDir Path dir) {
        SoloGame first = game(dir);
        HostState host = aDiscoveredHost(first);
        breached(first, host);
        first.persist();

        // ⚠ A save written before the fix carries the resolution and no foothold, so the load path
        // has to settle it as well — otherwise the bug is permanent for anyone who already breached
        // something. This is also the idempotence check: reconcileFootholds is safe to replay because
        // `foothold` and `looted` are both one-way, so the loot must not be credited a second time.
        SoloGame reloaded = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(on(reloaded.net(), host.address).foothold()).isTrue();

        java.math.BigInteger afterFirstLoad = reloaded.balance().wei();
        reloaded.persist();
        SoloGame again = SoloGame.open(
                new io.github.stoicswe.eyeandsickle.solo.save.FileSaveStore(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(again.balance().wei())
                .as("a host's one-time loot is one-time across loads")
                .isEqualTo(afterFirstLoad);
    }

    @Test
    @DisplayName("holding the machine is what lets you connect to it")
    void theFootholdIsUsable(@TempDir Path dir) {
        SoloGame game = game(dir);
        HostState host = aDiscoveredHost(game);
        // Refused before, allowed after — the foothold is not decoration on the map, it is the thing
        // `connect` checks, and a player who cannot connect to a machine they breached is stuck.
        assertThat(game.connectTo(host.address)).isFalse();

        breached(game, host);
        game.settleBreachOutcomes();
        assertThat(game.connectTo(host.address))
                .as("connect must be allowed once the machine is held")
                .isTrue();
    }
}
