package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A bridge is the way <em>onward</em>, and a base sweep may not find one.
 *
 * <h2>What this changed, and why it is not an I2 violation</h2>
 *
 * Until 2026-08-07 a bridge was the single most reliable thing the free starting instrument could
 * see: bridges are {@code SignalStrength.HIGH}, so {@code Balance.netSweepBase} gave them 0.85 at
 * tier 1 — finding the exit from a server was easier than finding anything on it worth taking.
 *
 * <p>The gate withholds <b>which kinds a tier can see</b>, never <b>how far it can see</b>.
 * {@code NetRules.hopCeiling} still takes no sweep tier, so a wide sweep buys the knowledge that a
 * door is there and buys nothing about reaching the far side of it — that is still breach, foothold,
 * {@code connect}, sweep again. Ethecoin bought sensitivity, which {@code docs/design/02} §1.1 allows;
 * it did not buy a ceiling, which Invariant <b>I2</b> forbids. {@link ReachIsUnchanged} is the half
 * of this file that holds that line.
 */
@DisplayName("bridges and the sweep tiers")
class BridgeVisibilityTest {

    private static final int SAMPLE = 200;

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    private static GameSave equipped(long seed) {
        GameSave save = NetTestKit.world(seed);
        NetTestKit.grant(save, SweepTier.WIDE);
        NetTestKit.grant(save, SweepTier.DEEP);
        return save;
    }

    private static Set<String> detected(GameSave save, SweepTier tier) {
        return new HashSet<>(NetTestKit.sweep(save, tier, NetTestKit.T0).foundAddresses());
    }

    /** Addresses of every bridge in a world, whether or not anybody has found it. */
    private static Set<String> bridges(GameSave save) {
        Set<String> found = new HashSet<>();
        for (HostState host : save.topology.hosts) {
            if (HostKind.BRIDGE.name().equals(host.kind)) {
                found.add(host.address);
            }
        }
        return found;
    }

    @Nested
    @DisplayName("a base sweep")
    class BaseSweep {

        @Test
        @DisplayName("never finds a bridge, in any world")
        void findsNoBridges() {
            // ⚠ Swept across many worlds rather than one. A bridge's position and its stored
            // detectRoll both vary with the seed, so a single world can pass this by having no bridge
            // within the hop ceiling at all — which would be a test that asserts nothing and reports
            // success. `everyOneOfTheseWorldsHasABridgeToMiss` is what stops that.
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = equipped(seed(i));
                assertThat(detected(save, SweepTier.BASE)).as("world %d", i).doesNotContainAnyElementsOf(bridges(save));
            }
        }

        @Test
        @DisplayName("still finds the rest of the loud furniture — this is not a blanket gate")
        void stillFindsOtherInfrastructure() {
            // ⚠ Gateways and relays are deliberately NOT gated with bridges. A gateway is a signpost —
            // host index 0, no loot — so hiding it would leave a base sweep finding nothing but quiet
            // desktops on a server it has already reached. What is withheld is the way ONWARD, and if
            // this ever fails the gate has been widened into something else.
            int worldsWithAFind = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = equipped(seed(i));
                Set<String> base = detected(save, SweepTier.BASE);
                if (!base.isEmpty()) {
                    worldsWithAFind++;
                }
            }
            assertThat(worldsWithAFind)
                    .as("a base sweep that found nothing anywhere would mean the gate caught everything")
                    .isGreaterThan(SAMPLE / 2);
        }
    }

    @Nested
    @DisplayName("a wide sweep")
    class WideSweep {

        @Test
        @DisplayName("can find bridges the base sweep could not")
        void findsBridges() {
            // The positive half. Asserted across the sample as "somewhere, wide finds a bridge base
            // did not" rather than per world, because whether any bridge sits inside the hop ceiling
            // is a property of the generated world and not of the sweep.
            int worldsWhereWideFoundABridge = 0;
            for (int i = 0; i < SAMPLE; i++) {
                GameSave save = equipped(seed(i));
                Set<String> wide = detected(equipped(seed(i)), SweepTier.WIDE);
                wide.retainAll(bridges(save));
                if (!wide.isEmpty()) {
                    worldsWhereWideFoundABridge++;
                }
            }
            assertThat(worldsWhereWideFoundABridge)
                    .as("a wide sweep must actually reveal bridges, or the gate is simply a wall")
                    .isPositive();
        }

        @Test
        @DisplayName("every one of these worlds has a bridge for the base sweep to miss")
        void everyOneOfTheseWorldsHasABridgeToMiss() {
            // ⚠ THE GUARD ON THE GUARD. Without this, `findsNoBridges` passes just as happily in a
            // world with no bridges in it — the shape of test this repo has already been bitten by
            // twice ("a regression test that passes both ways is worse than none").
            for (int i = 0; i < SAMPLE; i++) {
                assertThat(bridges(equipped(seed(i))))
                        .as("world %d has no bridge at all", i)
                        .isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("reach — Invariant I2")
    class ReachIsUnchanged {

        @Test
        @DisplayName("the hop ceiling still ignores the sweep tier entirely")
        void ceilingTakesNoTier() {
            // ⚠ THE LOAD-BEARING ASSERTION IN THIS FILE. The gate is defensible only because it moves
            // which KINDS a tier hears and never how FAR it hears. If a sweep tier ever reaches the
            // ceiling, ethecoin has bought reach and I2 is broken — and every screen would still
            // render correctly while it was.
            GameSave save = equipped(seed(1));
            int ceiling = NetRules.hopCeiling(save);
            NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0);
            assertThat(NetRules.hopCeiling(save))
                    .as("a deep sweep must not widen the ceiling")
                    .isEqualTo(ceiling);
        }

        @Test
        @DisplayName("monotonic: a better instrument never loses a contact")
        void monotonic() {
            // A gate that only ever ADDS kinds as the tier rises preserves detected(T1) ⊆ detected(T2)
            // by construction. Gating a kind OUT at a higher tier would break it, and a player who
            // bought an upgrade and lost a machine they already had would reasonably call it a bug.
            for (int i = 0; i < SAMPLE; i++) {
                Set<String> base = detected(equipped(seed(i)), SweepTier.BASE);
                Set<String> wide = detected(equipped(seed(i)), SweepTier.WIDE);
                Set<String> deep = detected(equipped(seed(i)), SweepTier.DEEP);
                assertThat(wide).as("world %d: wide ⊇ base", i).containsAll(base);
                assertThat(deep).as("world %d: deep ⊇ wide", i).containsAll(wide);
            }
        }
    }

    @Nested
    @DisplayName("the rule itself")
    class Predicate {

        @Test
        @DisplayName("only bridges are gated, and only below the floor")
        void gatesBridgesAlone() {
            for (HostKind kind : HostKind.values()) {
                boolean gated = kind == HostKind.BRIDGE;
                assertThat(HostArchetypes.detectableBySweep(kind.name(), 1))
                        .as("%s at tier 1", kind)
                        .isEqualTo(!gated);
                assertThat(HostArchetypes.detectableBySweep(kind.name(), Balance.NET_SWEEP_BRIDGE_MIN_TIER))
                        .as("%s at the floor", kind)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("an unparseable kind is not gated — a hand-edited save must not hide machines")
        void unknownKindIsVisible() {
            assertThat(HostArchetypes.detectableBySweep("nonsense", 1)).isTrue();
            assertThat(HostArchetypes.detectableBySweep(null, 1)).isTrue();
        }

        @Test
        @DisplayName("a bridge accepts no deployed work; everything else does")
        void bridgesTakeNoWork() {
            // ⚠ NOTHING CALLS acceptsDeployedWork YET — there is no player deploy action in the
            // engine, because bots are docs/design/10 and deliberately unbuilt. This is the rule
            // having something to be wrong against until the action exists, and the note on the
            // method is explicit that the action must consult it on the day it is written.
            assertThat(HostArchetypes.acceptsDeployedWork(HostKind.BRIDGE.name()))
                    .isFalse();
            for (HostKind kind : HostKind.values()) {
                if (kind != HostKind.BRIDGE) {
                    assertThat(HostArchetypes.acceptsDeployedWork(kind.name()))
                            .as("%s", kind)
                            .isTrue();
                }
            }
        }
    }
}
