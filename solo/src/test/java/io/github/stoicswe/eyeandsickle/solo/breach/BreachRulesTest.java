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
            assertThat(result.message())
                    .contains("not enough available compute")
                    .contains("needed");
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
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 3);
            LayerState layer = focus(save, "OFFSET_CIPHER");

            // ⚠ Editable columns, chosen rather than assumed. Since 2026-07-27 a cipher can arrive
            // with a few columns already solved and LOCKED, so cells 0 and 1 are not reliably the
            // player's to type into — a TYPE aimed at a given column is refused and ledgers nothing,
            // which made this test fail on the boards that got a give.
            int first = -1;
            int second = -1;
            for (int c = 0; c < layer.cipherObserved.size(); c++) {
                if (!OffsetRules.isGiven(layer, c)) {
                    if (first < 0) {
                        first = c;
                    } else if (second < 0) {
                        second = c;
                    }
                }
            }

            // One correct byte, one carried, and the rest left blank — the two paid moves that do not
            // strike. Typing is composition and ledgers nothing (see bookkeepingIsFree).
            BreachRules.act(save, OffsetRules.TYPE, first + ":" + OffsetRules.expected(layer, first), T0);
            BreachRules.act(save, OffsetRules.CARRY, String.valueOf(second), T0);
            assertThat(save.activeBreach.ledger).hasSize(1);
            assertThat(save.activeBreach.ledger.getLast().spentAfter).isEqualTo(layer.spent);

            // A row of zeroes is wrong in every column, so committing it strikes. Two rows: the move
            // and the alarm.
            // Zero into every column the player owns. A given column keeps its correct answer, so
            // the row is still wrong overall — which is what this test needs.
            for (int i = 0; i < layer.cipherObserved.size(); i++) {
                if (!OffsetRules.isGiven(layer, i)) {
                    BreachRules.act(save, OffsetRules.TYPE, i + ":0", T0);
                }
            }
            BreachRules.act(save, OffsetRules.COMMIT, "", T0);
            assertThat(save.activeBreach.ledger).hasSize(3);
            assertThat(save.activeBreach.ledger.getLast().label).isEqualTo("STRIKE");
            assertThat(save.activeBreach.ledger.getLast().cost).isEqualTo(Balance.ATTENTION_ALARM_PENALTY);
            assertThat(save.activeBreach.ledger.getLast().alarm).isTrue();
            // Without the second row the alarm's three attention would show up only as a gap between
            // one row's running total and the next — an unexplained discrepancy in exactly the
            // artefact that exists to explain a loss (design/05 §1 constraint 4).
            assertThat(save.activeBreach.ledger.get(1).spentAfter + Balance.ATTENTION_ALARM_PENALTY)
                    .isEqualTo(save.activeBreach.ledger.get(2).spentAfter);
        }

        @Test
        @DisplayName("composing your own notes is never charged and never ledgered")
        void bookkeepingIsFree() {
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 1);
            LayerState layer = focus(save, "OFFSET_CIPHER");

            // Typing an offset is composition, and composition is reversible until COMMIT
            // (docs/design/05 §3.7) — a player who rewrote the row twice must not have paid for it.
            BreachRules.act(save, OffsetRules.TYPE, "0:-9", T0);
            BreachRules.act(save, OffsetRules.TYPE, "1:12", T0);
            BreachRules.act(save, OffsetRules.TYPE, "0:7", T0);

            assertThat(layer.cipherEntered.get(0)).isEqualTo(7);
            assertThat(layer.cipherEntered.get(1)).isEqualTo(12);
            assertThat(layer.spent).isZero();
            // A ledger mostly full of rows about the player's own scratchpad is the burial
            // alert-fatigue(7) describes, in the one readout that must stay readable.
            assertThat(save.activeBreach.ledger).isEmpty();
        }

        @Test
        @DisplayName("a failed move still costs: attention is spent by doing, not by succeeding")
        void spentByDoing() {
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 1);
            LayerState layer = focus(save, "OFFSET_CIPHER");

            BreachTestKit.spendOneBadly(save);
            // A commit that got every column wrong costs exactly what a commit that got them all
            // right costs. The target does not know whether you learned anything.
            assertThat(layer.spent).isGreaterThanOrEqualTo(Balance.ATTENTION_PROBE);
            assertThat(save.activeBreach.ledger).isNotEmpty();
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
            String paid = plain.activeBreach.layers.getFirst().puzzleClass.equals("OFFSET_CIPHER")
                    ? OffsetRules.COMMIT
                    : MatrixRules.PICK;
            assertThat(BreachRules.attentionCost(tarped, paid))
                    .isEqualTo(BreachRules.attentionCost(plain, paid) + Balance.TARPIT_ATTENTION_SURCHARGE);
        }

        @Test
        @DisplayName("no defence can push a layer below the floor")
        void budgetHasAFloor() {
            SoloSave save = withNode(11L, 5, 3, true, false);
            BreachRules.begin(save, nodeTarget(save), T0);

            // An unwinnable board is not difficulty; it is the game deciding, which design/05 §1
            // constraint 4 forbids outright.
            assertThat(save.activeBreach.layers)
                    .allSatisfy(
                            layer -> assertThat(layer.budget).isGreaterThanOrEqualTo(Balance.BREACH_ATTENTION_FLOOR));
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

            BreachResult result = BreachRules.act(save, "bypass", "", T0);
            // docs/client/04 §3.5 gives a gate its own exit status precisely so the requirement gets
            // printed — that is what makes a gate legible rather than merely obstructive.
            assertThat(result.gated()).isTrue();
            assertThat(result.message()).contains("Overflow Kit");
        }

        @Test
        @DisplayName("a move that engaged nothing is refunded, and is still ledgered at zero")
        void nothingToDoIsRefunded() {
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 4);
            LayerState layer = focus(save, "OFFSET_CIPHER");

            // Fill every cell correctly by hand, then ask CARRY to solve one. There is nothing left
            // for it to solve, so it must not take the attention its chip advertises.
            for (int i = 0; i < layer.cipherObserved.size(); i++) {
                BreachRules.act(save, OffsetRules.TYPE, i + ":" + OffsetRules.expected(layer, i), T0);
            }
            int before = layer.spent;
            BreachRules.act(save, OffsetRules.CARRY, "", T0);

            assertThat(layer.spent).isEqualTo(before);
            // Still ledgered, at zero. A tool that silently did nothing would be indistinguishable
            // from a bug, and would teach the player to distrust the readout instead of the target.
            assertThat(save.activeBreach.ledger.getLast().cost).isZero();
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
            // the proof-of-skill item unlock the next proof-of-skill item. A tier-3 attempt is two
            // layers of one class, so bypassing one and solving the other credits it exactly once.
            assertThat(save.resolutions.getFirst().classesCleared).containsExactly(save.activeBreach.puzzleClass);
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
                miner.bufferedWei = Balance.ec("50");
                BreachRules.begin(save, crackTarget(save), T0);

                if ("win".equals(ending)) {
                    solveAll(save);
                } else {
                    BreachTestKit.loseAll(save);
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
            miner.bufferedWei = Balance.ec("50");
            long reclaimable = miner.hostCycles;
            long freeBefore = ComputeRules.availableCycles(save.rig);

            BreachRules.begin(save, crackTarget(save), T0);
            solveAll(save);

            assertThat(save.activeBreach.outcome).isEqualTo("BREACHED");
            assertThat(save.ethecoinWei).isEqualTo(Balance.ec("50"));
            assertThat(save.activeBreach.resolvedLootWei).isEqualTo(Balance.ec("50"));
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
            miner.bufferedWei = Balance.ec("50");
            miner.deployerHandle = "ninefold";
            BreachRules.begin(save, crackTarget(save), T0);

            BreachTestKit.loseAll(save);

            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
            // design/04 §5.1: "Without this, cracking would strictly dominate killing."
            assertThat(save.ethecoinWei).isZero();
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
            assertThat(save.ethecoinWei).isZero();
            assertThat(save.activeBreach.resolvedLootWei).isZero();
            assertThat(save.items).anyMatch(item -> "breached".equals(item.origin));
        }

        @Test
        @DisplayName("a FAILED attempt always states at least one consequence")
        void failureIsNeverSilent() {
            SoloSave save = withNode(70L, 3, 0, false, true);
            BreachRules.begin(save, nodeTarget(save), T0);
            BreachTestKit.loseAll(save);

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
            BreachTestKit.spendOneBadly(save);
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
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 5);
            LayerState layer = focus(save, "OFFSET_CIPHER");
            int limit = layer.strikeLimit;

            // Fill every cell with an offset that is provably wrong — the answer is a subtraction, so
            // one more than the answer never is one — and commit it over and over.
            for (int i = 0; i < layer.cipherObserved.size(); i++) {
                BreachRules.act(save, OffsetRules.TYPE, i + ":" + (OffsetRules.expected(layer, i) + 1), T0);
            }
            for (int i = 0; i < limit + 2 && save.activeBreach.outcome.isEmpty(); i++) {
                BreachRules.act(save, OffsetRules.COMMIT, "", T0);
            }

            assertThat(layer.strikes).isGreaterThanOrEqualTo(limit);
            assertThat(layer.state).isEqualTo("LOCKED");
            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("an exhausted budget fails the attempt")
        void exhaustionFails() {
            SoloSave save = BreachTestKit.attemptWith("OFFSET_CIPHER", 1);
            LayerState layer = focus(save, "OFFSET_CIPHER");

            BreachTestKit.loseAll(save);
            // Either the budget ran out or the strikes did. Both end the attempt, and which one gets
            // there first is a tuning question rather than a rule.
            assertThat(layer.spent >= layer.budget || layer.strikes >= layer.strikeLimit)
                    .isTrue();
            assertThat(save.activeBreach.outcome).isEqualTo("FAILED");
        }
    }

    @Nested
    @DisplayName("the action list")
    class Actions {

        @Test
        @DisplayName("every action carries its cost, before the click")
        void costsAreAlwaysPublished() {
            SoloSave save;

            for (String puzzleClass : new String[] {"BREACH_PROTOCOL", "OFFSET_CIPHER"}) {
                save = BreachTestKit.attemptWith(puzzleClass, 4);
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
            assertThat(BreachRules.act(save, MatrixRules.PICK, "0:0", T0).applied())
                    .isFalse();
        }
    }
}
