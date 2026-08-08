package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.TopologyState;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Moving the vantage changes <b>where a sweep measures from</b>, and nothing else.
 *
 * <h2>What this exists to stop coming back</h2>
 *
 * {@code Sighting.vantage} was, for a long time, the only flag on the wire that was <em>usually</em>
 * true of the player's own rig — because the vantage starts there and most players never move it. So
 * five separate views used it to mean "this machine is mine", and every one of them was correct right
 * up until somebody used the mechanic. After a {@code connect}:
 *
 * <ul>
 *   <li>the node menu offered to <b>breach and port-scan the player's own rig</b>, and hid both on
 *       the machine they were standing on;
 *   <li>{@code SessionRules} stopped treating the rig as local — ⚠ <b>harmlessly</b>, and it is worth
 *       recording that this one was not a live bug: the only thing it gates is a discovered/foothold
 *       check, and the generator sets both on the rig, so nothing was ever refused. It was fixed for
 *       being wrong rather than for breaking;
 *   <li>the file manager dropped the vantage out of its Network list, losing file access to a
 *       machine you hold for the sole reason that you were standing on it;
 *   <li>the host list greyed the player's own rig out as somewhere they could not operate from, and
 *       labelled it a <b>contact</b>.
 * </ul>
 *
 * The fix was to publish {@code self} — which {@code NetRules.sighting} had computed and discarded
 * since it was written — so the question "is this mine" has an answer that is not the vantage.
 */
@DisplayName("the vantage is a sweep origin and nothing else")
class VantageIsOnlyASweepOriginTest {

    private static Sighting at(NetMap map, String address) {
        return map.sightings().stream()
                .filter(s -> s.address().equals(address))
                .findFirst()
                .orElseThrow();
    }

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    private static final String RIG = "10.0.0.1";

    private static final String HELD = "10.0.0.9";

    /**
     * A two-machine world: the player's rig, and one other they hold a foothold on.
     *
     * <p>⚠ Hand-built rather than generated, in {@code SessionRulesTest}'s style. A generated world
     * would need a sweep to make anything visible and a breach to produce a foothold, which is three
     * mechanics of setup for a test about a fourth. ⚠ Both {@code playerAddress} and
     * {@code vantageAddress} are set, and they are the two fields this whole file is about: a fixture
     * that set only one would make half these assertions vacuous.
     *
     * <p>⚠ The held machine is in {@code knownNodes} too. {@code NetRules.view} shows the rig
     * unconditionally and everything else only if it has been discovered, so a host flagged on
     * {@code HostState} alone never reaches a {@code Sighting} and every lookup here throws.
     */
    private static GameSave world() {
        GameSave save = new GameSave();
        save.rig.totalCycles = 100;
        save.topology = new TopologyState();

        HostState rig = new HostState();
        rig.address = RIG;
        rig.kind = "SELF";
        rig.discovered = true;
        rig.foothold = true;

        HostState held = new HostState();
        held.address = HELD;
        held.kind = "TERMINAL";
        held.discovered = true;
        held.foothold = true;
        held.tier = 2;

        // ⚠ LINKED, IN BOTH DIRECTIONS. Without this the fixture is two islands: `TopologyGenerator
        // .bfs` walks `HostState.links`, reaches nothing, and every distance falls back to the
        // getOrDefault(…, 0) that exists for hand-edited saves. Every hop assertion in this file then
        // passes by comparing 0 to 0 — which is exactly what the first version of it did, and it took
        // a test that should have been vacuously green failing for an unrelated reason to notice.
        rig.links.add(HELD);
        held.links.add(RIG);

        save.topology.hosts.add(rig);
        save.topology.hosts.add(held);
        save.topology.playerAddress = RIG;
        save.topology.vantageAddress = RIG;

        NodeState known = new NodeState();
        known.address = HELD;
        save.knownNodes.add(known);
        return save;
    }

    @Nested
    @DisplayName("self and vantage are different facts")
    class TwoFacts {

        @Test
        @DisplayName("before moving, the rig is both — which is why the conflation survived so long")
        void initiallyTheSame() {
            GameSave save = world();
            NetMap map = NetRules.view(save);
            Sighting rig = at(map, save.topology.playerAddress);

            assertThat(rig.self()).isTrue();
            assertThat(rig.vantage()).isTrue();
        }

        @Test
        @DisplayName("⚠ after moving, the rig is STILL self and is no longer the vantage")
        void movingSeparatesThem() {
            GameSave save = world();
            String elsewhere = HELD;
            NetRules.connect(save, elsewhere, NOW);

            NetMap map = NetRules.view(save);
            Sighting rig = at(map, save.topology.playerAddress);
            Sighting there = at(map, elsewhere);

            // The whole point, in four assertions.
            assertThat(rig.self()).as("your rig is still yours").isTrue();
            assertThat(rig.vantage()).as("but you are not operating from it").isFalse();
            assertThat(there.self())
                    .as("somebody else's machine never becomes yours")
                    .isFalse();
            assertThat(there.vantage()).as("you are operating from it").isTrue();
        }

        @Test
        @DisplayName("exactly one machine is self, and exactly one is the vantage")
        void bothAreUnique() {
            GameSave save = world();
            NetRules.connect(save, HELD, NOW);
            NetMap map = NetRules.view(save);

            assertThat(map.sightings().stream().filter(Sighting::self).count())
                    .as("two rigs would make 'your own machine' ambiguous everywhere")
                    .isEqualTo(1);
            assertThat(map.sightings().stream().filter(Sighting::vantage).count())
                    .as("two vantages would make the hop ceiling meaningless")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("what moving it does change")
    class TheSweepOrigin {

        @Test
        @DisplayName("hop distances are measured from the new vantage")
        void hopsMoveWithIt() {
            // This IS the mechanic, and it is the only thing on this list.
            GameSave save = world();
            String elsewhere = HELD;
            NetRules.connect(save, elsewhere, NOW);

            NetMap map = NetRules.view(save);
            assertThat(at(map, elsewhere).hopsFromVantage())
                    .as("the vantage is zero hops from itself")
                    .isZero();
            assertThat(map.vantageAddress()).isEqualTo(elsewhere);
        }

        @Test
        @DisplayName("⚠ it does NOT change the hop ceiling — Invariant I2")
        void reachIsUnchanged() {
            // Repositioning is how a player sees further, and it is earned. What it must never do is
            // widen the instrument: the ceiling is 1, or 2 with the Topology Mapper schematic, from
            // wherever you happen to be standing.
            GameSave save = world();
            int before = NetRules.hopCeiling(save);
            NetRules.connect(save, HELD, NOW);
            assertThat(NetRules.hopCeiling(save)).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("what moving it must NOT change")
    class EverythingElse {

        @Test
        @DisplayName("a shell on your own rig still opens after the vantage has moved")
        void ownRigStaysLocal() {
            // ⚠ THIS ONE PASSES AGAINST THE OLD CODE TOO, and that is stated rather than hidden — a
            // test that passes both ways is worse than none if it is presented as a regression guard.
            // The old expression made `self` false on the player's own rig once the vantage moved,
            // but the `!self` branch only demands a discovered host with a foothold and the generator
            // sets both, so nothing was refused. Kept as a standing guard on the OUTCOME rather than
            // as evidence of a fix: the day something makes the rig's flags less certain, this fires.
            GameSave save = world();
            String rig = RIG;
            NetRules.connect(save, HELD, NOW);

            SessionRules.Opened opened = SessionRules.open(save, rig, NOW);
            assertThat(opened.refusal())
                    .as("refused a shell on the player's own machine")
                    .isNull();
            assertThat(opened.session()).isNotNull();
        }

        @Test
        @DisplayName("the rig keeps its identity — no tier, and it is not a breach target")
        void ownRigKeepsItsIdentity() {
            // `self` drives more than a label in NetRules: the rig has no difficulty tier, and its
            // report is never consulted. All of that keyed on playerAddress and always did — this is
            // the assertion that it did not quietly start keying on the vantage instead.
            GameSave save = world();
            NetRules.connect(save, HELD, NOW);
            Sighting rig = at(NetRules.view(save), save.topology.playerAddress);

            assertThat(rig.tier())
                    .as("your own machine has no breach difficulty")
                    .isNull();
            assertThat(rig.self()).isTrue();
        }

        @Test
        @DisplayName("the machine you moved to keeps its tier — it is still somebody else's")
        void theVantageIsStillATarget() {
            // The mirror of the above, and the one that made the node menu hide Breach: standing on a
            // machine does not make it yours, and nothing about it should read as though it did.
            GameSave save = world();
            String elsewhere = HELD;
            NetRules.connect(save, elsewhere, NOW);
            Sighting there = at(NetRules.view(save), elsewhere);

            assertThat(there.self()).isFalse();
            assertThat(there.tier())
                    .as("somebody else's machine still has a difficulty")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("the map's frame does not move")
    class TheDrawing {

        @Test
        @DisplayName("⚠ the rig stays at layer zero, and hops-from-rig do not change")
        void repositioningDoesNotRerootTheGraph() {
            // ⚠ THE BUG THIS PINS WAS VISIBLE ON SCREEN. NetLayout drew its columns on
            // hopsFromVantage, so a connect re-rooted the whole graph: 10.0.0.2 jumped to the
            // leftmost column and the player's own rig was demoted to H1 among strangers. A map whose
            // frame moves when you move is not a map you can build a picture from.
            GameSave save = world();

            int rigBefore = at(NetRules.view(save), RIG).hopsFromRig();
            int heldBefore = at(NetRules.view(save), HELD).hopsFromRig();

            NetRules.connect(save, HELD, NOW);

            assertThat(at(NetRules.view(save), RIG).hopsFromRig())
                    .as("the rig is column zero before and after")
                    .isEqualTo(rigBefore)
                    .isZero();
            assertThat(at(NetRules.view(save), HELD).hopsFromRig())
                    .as("nothing already drawn moves")
                    .isEqualTo(heldBefore);
        }

        @Test
        @DisplayName("the two distances disagree once the vantage moves — which is why both are published")
        void theDistancesAreDifferentFacts() {
            // Before the move they are equal for every machine, which is precisely why one of them
            // could stand in for the other for so long without anybody noticing.
            GameSave save = world();
            NetRules.connect(save, HELD, NOW);
            Sighting rig = at(NetRules.view(save), RIG);

            assertThat(rig.hopsFromRig()).as("zero from itself, always").isZero();
            assertThat(rig.hopsFromVantage())
                    .as("but you are standing somewhere else now")
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("any machine you hold can be the vantage, including the rig")
    class WhatMayBeAVantage {

        @Test
        @DisplayName("⚠ the vantage can be moved BACK to the player's own rig")
        void theRigIsALegalVantage() {
            // ⚠ The rules always allowed this — `connect` reads `if (!ownRig && !host.foothold)`.
            // It was the node MENU that refused it, with a guard that read "you cannot move the
            // vantage to yourself" and, once `self` stopped meaning `vantage`, locked the player out
            // of returning to localhost entirely. Pinned here so the engine's half can never quietly
            // acquire the restriction the interface used to imply.
            GameSave save = world();
            NetRules.connect(save, HELD, NOW);
            assertThat(NetRules.vantageAddress(save)).isEqualTo(HELD);

            assertThat(NetRules.connect(save, RIG, NOW))
                    .as("returning home is a legal move")
                    .isTrue();
            assertThat(NetRules.vantageAddress(save)).isEqualTo(RIG);
        }

        @Test
        @DisplayName("a machine with no foothold is refused, which is what keeps reach earned")
        void strangersAreRefused() {
            // The other half, and the one that matters for I2: position substitutes for reach, so it
            // has to be earned with a breach rather than picked off the map.
            GameSave save = world();
            HostState stranger = new HostState();
            stranger.address = "10.0.0.44";
            stranger.kind = "TERMINAL";
            stranger.discovered = true;
            stranger.foothold = false;
            save.topology.hosts.add(stranger);

            assertThat(NetRules.connect(save, stranger.address, NOW)).isFalse();
            assertThat(NetRules.vantageAddress(save)).isEqualTo(RIG);
        }
    }

    @Nested
    @DisplayName("the map still marks it")
    class StillVisible {

        @Test
        @DisplayName("the vantage is findable on the wire, which is what the box is drawn from")
        void theFlagSurvives() {
            // The client draws the only heavy frame on the map around this. Removing the flag would
            // have been the wrong fix to the conflation — the visual marker is the half of the
            // mechanic the player is meant to see.
            GameSave save = world();
            String elsewhere = HELD;
            NetRules.connect(save, elsewhere, NOW);

            Optional<Sighting> marked = NetRules.view(save).sightings().stream()
                    .filter(Sighting::vantage)
                    .findFirst();
            assertThat(marked).isPresent();
            assertThat(marked.get().address()).isEqualTo(elsewhere);
        }
    }
}
