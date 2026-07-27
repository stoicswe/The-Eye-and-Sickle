package io.github.stoicswe.eyeandsickle.solo.breach;

import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.T0;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.active;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.crackTarget;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.focus;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.give;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.nodeTarget;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.save;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.solveActiveLayer;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.solveAll;
import static io.github.stoicswe.eyeandsickle.solo.breach.BreachTestKit.withNode;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the breach engine.
 *
 * <p>These concentrate on the properties a player would actually notice going wrong, and on the
 * invariants a breach is capable of violating: I9 (a crack is heat-free on every outcome), I1/I2
 * (breach loot never mints ethecoin), I7/I13 (tier-gated, never count-gated), I10 (the human read
 * beats a fixed heuristic), and the two correctness properties nothing else can catch — that the
 * RNG is committed, and that a snapshot never carries the answer.
 */
class BreachRulesTest {

    @Nested
    @DisplayName("opening an attempt")
    class Opening {

        @Test
        @DisplayName("a breach holds its compute for the whole attempt, and does not create a task")
        void holdsComputeWithoutATask() {
            SoloSave save = withNode(11L, 3, 0, false, false);
            BreachTarget target = nodeTarget(save);
            long free = ComputeRules.availableCycles(save.rig);

            assertThat(BreachRules.begin(save, target, T0).applied()).isTrue();

            // Held, not spent: nothing is recovering yet. Same shape as a scan under UI-6.
            assertThat(ComputeRules.availableCycles(save.rig)).isEqualTo(free - target.computeCost());
            assertThat(ComputeRules.recoveringCycles(save.rig)).isZero();
            // ⚠ No TaskState. design/05 §4 removed the wall clock, so there is no deadline for
            // settleTasks to settle and the breach needs no settlement path at all.
            assertThat(save.tasks).isEmpty();
        }

        @Test
        @DisplayName("only one breach at a time")
        void oneAtATime() {
            SoloSave save = withNode(11L, 1, 0, false, false);
            BreachTarget target = nodeTarget(save);
            BreachRules.begin(save, target, T0);

            BreachResult second = BreachRules.begin(save, target, T0);
            assertThat(second.applied()).isFalse();
            assertThat(second.message()).contains("already open");
        }

        @Test
        @DisplayName("a rig that cannot afford the attempt is refused, with the arithmetic")
        void refusedWhenBroke() {
            SoloSave save = withNode(11L, 1, 0, false, false);
            save.rig.selfMiningCycles = Balance.STARTING_CYCLES;

            BreachResult result = BreachRules.begin(save, nodeTarget(save), T0);
            assertThat(result.applied()).isFalse();
            // The wording mirrors LocalGameSession.scan's, so the two refusals read alike.
            assertThat(result.message()).contains("not enough available compute").contains("needed");
        }

        @Test
        @DisplayName("every layer is generated up front, and a pending layer publishes no board")
        void boardsAreGeneratedOnceAndNotPublishedEarly() {
            SoloSave save = withNode(4242L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            assertThat(save.activeBreach.layers).hasSize(3);
            assertThat(save.activeBreach.layers.get(1).state).isEqualTo("PENDING");
            // Generated (D-4: a lazily generated layer is a rerollable one)...
            assertThat(save.activeBreach.layers.get(1).puzzleClass).isNotBlank();
            // ...but not published. Sending three layers of answers the moment the attempt opens is
            // the same leak as sending the secret, arriving one indirection later.
            assertThat(BreachSnapshots.of(save).layers().get(1).board()).isNull();
        }
    }

    @Nested
    @DisplayName("the attention ledger")
    class Ledger {

        @Test
        @DisplayName("every accepted move appends a row, and a strike appends its penalty separately")
        void everyMoveIsItemised() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = active(save);

            BreachRules.act(save, "sweep", "0", T0);
            BreachRules.act(save, "probe", "0", T0);
            assertThat(save.activeBreach.ledger).hasSize(2);
            assertThat(save.activeBreach.ledger.getFirst().cost).isEqualTo(Balance.ATTENTION_QUIET_READ);
            assertThat(save.activeBreach.ledger.getLast().spentAfter).isEqualTo(layer.spent);

            // An empty declaration is wrong, so it strikes. Two rows: the move and the alarm.
            BreachRules.act(save, "declare", "", T0);
            assertThat(save.activeBreach.ledger).hasSize(4);
            assertThat(save.activeBreach.ledger.getLast().label).isEqualTo("STRIKE");
            assertThat(save.activeBreach.ledger.getLast().cost).isEqualTo(Balance.ATTENTION_ALARM_PENALTY);
            assertThat(save.activeBreach.ledger.getLast().alarm).isTrue();
            // Without the second row the alarm's three attention would show up only as a gap between
            // one row's running total and the next — an unexplained discrepancy in exactly the
            // artefact that exists to explain a loss (design/05 §1 constraint 4).
            assertThat(save.activeBreach.ledger.get(2).spentAfter + Balance.ATTENTION_ALARM_PENALTY)
                    .isEqualTo(save.activeBreach.ledger.get(3).spentAfter);
        }

        @Test
        @DisplayName("composing your own notes is never charged and never ledgered")
        void bookkeepingIsFree() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = active(save);

            BreachRules.act(save, "mark", "0", T0);
            BreachRules.act(save, "mark", "1", T0);
            BreachRules.act(save, "mark", "0", T0);

            assertThat(layer.declared).containsExactly(1);
            assertThat(layer.spent).isZero();
            // A ledger mostly full of rows about the player's own scratchpad is the burial
            // alert-fatigue(7) describes, in the one readout that must stay readable.
            assertThat(save.activeBreach.ledger).isEmpty();
        }

        @Test
        @DisplayName("a failed move still costs: attention is spent by doing, not by succeeding")
        void spentByDoing() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = active(save);

            BreachRules.act(save, "probe", "999", T0);
            assertThat(layer.spent).isEqualTo(Balance.ATTENTION_PROBE);
            assertThat(save.activeBreach.ledger).hasSize(1);
        }
    }

    @Nested
    @DisplayName("defences")
    class Defences {

        @Test
        @DisplayName("a Firewall cuts the budget; a Tarpit surcharges every action instead")
        void firewallAndTarpitActOnDifferentAxes() {
            SoloSave plain = withNode(11L, 3, 0, false, false);
            SoloSave walled = withNode(11L, 3, 3, false, false);
            SoloSave tarped = withNode(11L, 3, 0, true, false);
            BreachRules.begin(plain, nodeTarget(plain), T0);
            BreachRules.begin(walled, nodeTarget(walled), T0);
            BreachRules.begin(tarped, nodeTarget(tarped), T0);

            int base = plain.activeBreach.layers.getFirst().budget;
            assertThat(walled.activeBreach.layers.getFirst().budget)
                    .isEqualTo(base - 3 * Balance.FIREWALL_BUDGET_PENALTY_PER_TIER);
            // The Tarpit does NOT touch the budget: cutting it would make it a second Firewall,
            // which is the one thing design/09 §1 gives the Firewall to do.
            assertThat(tarped.activeBreach.layers.getFirst().budget).isEqualTo(base);
            assertThat(BreachRules.attentionCost(tarped, "probe"))
                    .isEqualTo(BreachRules.attentionCost(plain, "probe") + Balance.TARPIT_ATTENTION_SURCHARGE);
        }

        @Test
        @DisplayName("the Side-Channel Reader stays free even under a Tarpit")
        void sideChannelIsAlwaysZero() {
            SoloSave save = withNode(11L, 1, 0, true, false);
            give(save, "side-channel-reader");
            BreachRules.begin(save, nodeTarget(save), T0);

            // design/06 §2 calls zero attention the Reader's "whole identity" and design/05 §4 makes
            // it the only zero-attention action in the game. A defence that made it cost one would
            // delete the single property that distinguishes it.
            assertThat(BreachRules.attentionCost(save, "sidechannel")).isZero();
        }

        @Test
        @DisplayName("no defence can push a layer below the floor")
        void budgetHasAFloor() {
            SoloSave save = withNode(11L, 5, 3, true, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            // An unwinnable board is not difficulty; it is the game deciding, which design/05 §1
            // constraint 4 forbids outright.
            assertThat(save.activeBreach.layers)
                    .allSatisfy(layer -> assertThat(layer.budget).isGreaterThanOrEqualTo(Balance.BREACH_ATTENTION_FLOOR));
        }
    }

    @Nested
    @DisplayName("tools")
    class Tools {

        @Test
        @DisplayName("a missing tool is a gate with its requirement in words, not a bare refusal")
        void missingToolsAreGates() {
            SoloSave save = withNode(11L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            BreachResult result = BreachRules.act(save, "volley", "", T0);
            // docs/client/04 §3.5 gives a gate its own exit status precisely so the requirement gets
            // printed — that is what makes a gate legible rather than merely obstructive.
            assertThat(result.gated()).isTrue();
            assertThat(result.message()).contains("Fuzzer");
        }

        @Test
        @DisplayName("the Rainbow Table is hard-countered by salting, and costs nothing when it is")
        void saltingRefundsTheRainbowTable() {
            SoloSave save = withNode(4L, 4, 0, false, false);
            give(save, "rainbow-table");
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");
            assertThat(layer.salted).isTrue(); // tier 4 is always salted

            int before = layer.spent;
            BreachRules.act(save, "rainbow", "", T0);

            assertThat(layer.spent).isEqualTo(before);
            assertThat(layer.known).allMatch(String::isEmpty);
            // Still ledgered, at zero. A tool that silently did nothing would be indistinguishable
            // from a bug, and would teach the player to distrust the readout instead of the target.
            assertThat(save.activeBreach.ledger.getLast().cost).isZero();
            assertThat(save.activeBreach.ledger.getLast().result).contains("salted");
        }

        @Test
        @DisplayName("the Overflow Kit bypasses ONE layer per attempt, not one per layer")
        void bypassIsOncePerAttempt() {
            SoloSave save = withNode(202L, 3, 0, false, false);
            give(save, "overflow-kit");
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState first = active(save);

            assertThat(BreachRules.attentionCost(save, "bypass"))
                    .isEqualTo((int) Math.ceil(first.budget * Balance.ATTENTION_BYPASS_FRACTION));
            assertThat(BreachRules.act(save, "bypass", "", T0).applied()).isTrue();
            assertThat(first.state).isEqualTo("BYPASSED");

            // design/05 §3.1: "clearing every layer OR bypassing one". Once per layer would let a
            // tier-4 attempt be bypassed end to end, which is CLAUDE.md's "never let anything skip
            // the puzzle wholesale" — and would make the Kit a default rather than a panic button.
            BreachResult second = BreachRules.act(save, "bypass", "", T0);
            assertThat(second.applied()).isFalse();
            assertThat(second.message()).contains("spent");
        }

        @Test
        @DisplayName("a bypassed layer is not a solved one")
        void aBypassIsNotASolve() {
            SoloSave save = withNode(202L, 3, 0, false, false);
            give(save, "overflow-kit");
            BreachRules.begin(save, nodeTarget(save), T0);
            BreachRules.act(save, "bypass", "", T0);
            solveAll(save);

            assertThat(save.activeBreach.outcome).isEqualTo("BREACHED");
            // design/02 §2.4 requires the class to have been SOLVED. Crediting a bypass would let
            // the proof-of-skill item unlock the next proof-of-skill item.
            assertThat(save.resolutions.getFirst().classesCleared).containsExactly("LOGIC");
        }
    }

    @Nested
    @DisplayName("resolution")
    class Resolution {

        @Test
        @DisplayName("INVARIANT I9 — a miner crack generates zero heat on EVERY outcome")
        void crackIsAlwaysHeatFree() {
            for (String ending : new String[] {"win", "lose"}) {
                SoloSave save = save(7L);
                MinerState miner = Targets.plantTutorialMiner(save, T0);
                miner.bufferedMinorUnits = 5_000L;
                BreachRules.begin(save, crackTarget(save), T0);

                if ("win".equals(ending)) {
                    solveAll(save);
                } else {
                    for (int i = 0; i < 100 && save.activeBreach.outcome.isEmpty(); i++) {
                        BreachRules.act(save, "declare", "", T0);
                    }
                }
                assertThat(save.activeBreach.resolvedHeat).as(ending).isZero();
                assertThat(save.personalHeat).as(ending).isZero();
            }
        }

        @Test
        @DisplayName("a successful crack is a transfer: the buffer moves, nothing is minted")
        void crackSeizesTheBuffer() {
            SoloSave save = save(7L);
            MinerState miner = Targets.plantTutorialMiner(save, T0);
            miner.bufferedMinorUnits = 5_000L;
            long reclaimable = miner.hostCycles;
            long freeBefore = ComputeRules.availableCycles(save.rig);

            BreachRules.begin(save, crackTarget(save), T0);
            solveAll(save);

            assertThat(save.activeBreach.outcome).isEqualTo("BREACHED");
            assertThat(save.ethecoinMinorUnits).isEqualTo(5_000L);
            assertThat(save.activeBreach.resolvedLootMinorUnits).isEqualTo(5_000L);
            // The EC was already on the player's own disk — design/04 §5.1, design/03 §5 rule 3.
            assertThat(save.ledger).hasSize(1);
            assertThat(save.ledger.getFirst().type).isEqualTo("CRACK");
            // Compute reclaimed. The breach's own hold is recovering, so compare against the
            // parasite's cycles rather than against the whole rig.
            assertThat(save.rig.foreignMiners).isEmpty();
            assertThat(ComputeRules.availableCycles(save.rig) + save.activeBreach.reservedCycles)
                    .isEqualTo(freeBefore + reclaimable);
        }

        @Test
        @DisplayName("a botched crack is the dead-man switch, and it must not be softened")
        void failedCrackFlushesToTheDeployer() {
            SoloSave save = save(7L);
            MinerState miner = Targets.plantTutorialMiner(save, T0);
            miner.bufferedMinorUnits = 5_000L;
            miner.deployerHandle = "ninefold";
            BreachRules.begin(save, crackTarget(save), T0);

            for (int i = 0; i < 100 && save.activeBreach.outcome.isEmpty(); i++) {
                BreachRules.act(save, "declare", "", T0);
            }

            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
            // design/04 §5.1: "Without this, cracking would strictly dominate killing."
            assertThat(save.ethecoinMinorUnits).isZero();
            assertThat(save.rig.foreignMiners).isEmpty();
            assertThat(save.activeBreach.consequences)
                    .anyMatch(line -> line.contains("dead-man switch"))
                    .anyMatch(line -> line.contains("self-destructed"))
                    .anyMatch(line -> line.contains("ninefold"));
        }

        @Test
        @DisplayName("INVARIANT I1/I2 — an offensive breach yields an item, never ethecoin")
        void offensiveLootIsNeverMoney() {
            SoloSave save = withNode(606L, 3, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            solveAll(save);

            assertThat(save.activeBreach.outcome).isEqualTo("BREACHED");
            // Minting currency on a successful breach would be a faucet attached to the game's main
            // progression loop — design/03 §5 rule 3, and the shortest path to breaking I1 and I2.
            assertThat(save.ethecoinMinorUnits).isZero();
            assertThat(save.activeBreach.resolvedLootMinorUnits).isZero();
            assertThat(save.items).anyMatch(item -> "breached".equals(item.origin));
        }

        @Test
        @DisplayName("a FAILED attempt always states at least one consequence")
        void failureIsNeverSilent() {
            SoloSave save = withNode(70L, 3, 0, false, true);
            BreachRules.begin(save, nodeTarget(save), T0);
            for (int i = 0; i < 200 && save.activeBreach.outcome.isEmpty(); i++) {
                BreachRules.act(save, "probe", "999", T0);
            }

            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
            // A failure with no stated consequence reads as "the game decided", which is the one
            // reading design/05 §1 constraint 4 forbids.
            assertThat(save.activeBreach.consequences).isNotEmpty();
            assertThat(save.activeBreach.consequences).anyMatch(line -> line.contains("canary"));
        }

        @Test
        @DisplayName("an abort is a persisted outcome, and the noise already made stays made")
        void abortIsRecorded() {
            SoloSave save = withNode(9L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            BreachRules.act(save, "listen", "", T0);
            int noise = save.activeBreach.noise;

            assertThat(BreachRules.abort(save, T0).applied()).isTrue();
            assertThat(save.activeBreach.outcome).isEqualTo("ABORTED");
            assertThat(save.resolutions).hasSize(1);
            assertThat(save.activeBreach.resolvedNoise).isGreaterThanOrEqualTo(noise);
            assertThat(save.activeBreach.consequences).isNotEmpty();
        }

        @Test
        @DisplayName("resolving releases the held cycles onto the recovery curve")
        void computeRecoversAtResolution() {
            SoloSave save = withNode(9L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            long held = save.activeBreach.reservedCycles;

            BreachRules.abort(save, T0);

            // Hold, then recover — the same shape UI-6 gave a scan (design/04 §3.2).
            assertThat(ComputeRules.recoveringCycles(save.rig)).isEqualTo(held);
        }

        @Test
        @DisplayName("the outcome slate survives until dismissed, and dismiss is not idempotent-true")
        void dismissIsSeparateFromResolving() {
            SoloSave save = withNode(9L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            BreachRules.abort(save, T0);

            // A resolution that cleared itself would mean a player who quit in frustration came back
            // with no way to read why they lost.
            assertThat(save.activeBreach).isNotNull();
            assertThat(BreachSnapshots.of(save).resolved()).isTrue();
            assertThat(BreachRules.dismiss(save)).isTrue();
            assertThat(save.activeBreach).isNull();
            assertThat(BreachRules.dismiss(save)).isFalse();
        }

        @Test
        @DisplayName("a live breach cannot be dismissed out from under itself")
        void liveBreachCannotBeDismissed() {
            SoloSave save = withNode(9L, 2, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            assertThat(BreachRules.dismiss(save)).isFalse();
            assertThat(save.activeBreach).isNotNull();
        }
    }

    @Nested
    @DisplayName("layers")
    class Layers {

        @Test
        @DisplayName("clearing a layer promotes the next one; clearing the last one resolves")
        void layersAdvance() {
            SoloSave save = withNode(4242L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            solveActiveLayer(save);
            assertThat(save.activeBreach.layers.get(0).state).isEqualTo("CLEARED");
            assertThat(save.activeBreach.layers.get(1).state).isEqualTo("ACTIVE");
            assertThat(save.activeBreach.activeLayer).isEqualTo(1);

            solveAll(save);
            assertThat(save.activeBreach.outcome).isEqualTo("BREACHED");
            assertThat(save.activeBreach.activeLayer).isEqualTo(-1);
        }

        @Test
        @DisplayName("striking out locks the layer, and a locked layer ends the attempt")
        void lockoutEndsTheAttempt() {
            SoloSave save = withNode(70L, 5, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = focus(save, "LOGIC");
            int limit = layer.strikeLimit;

            // Fill the draft with one symbol, then repeat it: the second submission is provably
            // impossible given the first, so it strikes, and so does every one after it.
            for (int i = 0; i < layer.secret.size(); i++) {
                BreachRules.act(save, "set", (i + 1) + ":" + layer.alphabet.getFirst(), T0);
            }
            for (int i = 0; i < limit + 2 && save.activeBreach.outcome.isEmpty(); i++) {
                BreachRules.act(save, "probe", "", T0);
            }

            assertThat(layer.strikes).isGreaterThanOrEqualTo(limit);
            assertThat(layer.state).isEqualTo("LOCKED");
            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("an exhausted budget fails the attempt")
        void exhaustionFails() {
            SoloSave save = withNode(31337L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            LayerState layer = active(save);

            for (int i = 0; i < 200 && save.activeBreach.outcome.isEmpty(); i++) {
                BreachRules.act(save, "probe", "999", T0);
            }
            assertThat(layer.spent).isGreaterThanOrEqualTo(layer.budget);
            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
        }
    }

    @Nested
    @DisplayName("the action list")
    class Actions {

        @Test
        @DisplayName("every action carries its cost, before the click")
        void costsAreAlwaysPublished() {
            SoloSave save = withNode(11L, 4, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            for (String puzzleClass : new String[] {"ENUMERATION", "LOGIC", "TRAVERSAL"}) {
                focus(save, puzzleClass);
                assertThat(BreachRules.actions(save)).as(puzzleClass).isNotEmpty();
                for (BreachAction action : BreachRules.actions(save)) {
                    assertThat(action.attentionCost()).as(action.actionId()).isNotNegative();
                    assertThat(action.label()).as(action.actionId()).isNotBlank();
                    // A disabled action must say why. design/05 §4's legibility requirement is not
                    // only about price: an unexplained grey chip teaches nothing.
                    if (!action.enabled()) {
                        assertThat(action.refusal()).as(action.actionId()).isNotBlank();
                    } else {
                        assertThat(action.refusal()).as(action.actionId()).isEmpty();
                    }
                }
            }
        }

        @Test
        @DisplayName("there are no actions once the attempt has resolved")
        void noActionsAfterResolution() {
            SoloSave save = withNode(9L, 1, 0, false, false);
            BreachRules.begin(save, nodeTarget(save), T0);
            BreachRules.abort(save, T0);

            assertThat(BreachRules.actions(save)).isEmpty();
            assertThat(BreachRules.act(save, "probe", "0", T0).applied()).isFalse();
        }
    }
}
