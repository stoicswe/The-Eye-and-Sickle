package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachAction;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachActionKind;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.net.NodeReports;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.rules.EventLog;
import io.github.stoicswe.eyeandsickle.solo.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.solo.rules.SalvageRules;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.AttentionEntryState;
import io.github.stoicswe.eyeandsickle.solo.state.BreachState;
import io.github.stoicswe.eyeandsickle.solo.state.ItemState;
import io.github.stoicswe.eyeandsickle.solo.state.LayerState;
import io.github.stoicswe.eyeandsickle.solo.state.MinerState;
import io.github.stoicswe.eyeandsickle.solo.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The breach: open one, take a turn, resolve it.
 *
 * <h2>Turn-based, and therefore clock-free</h2>
 *
 * {@code docs/design/05-hacking-minigame.md} §4, decided 2026-07-26: "a breach is turn-based. There
 * is no wall clock anywhere in it." Every method here takes a {@code now} for one purpose only —
 * timestamping what it writes — and nothing here has a deadline. That is why a breach needs no
 * settlement path in {@code SoloGame.resume()} or {@code tick()}, and why it survives a quit for
 * free: reloading puts the player back on the same turn.
 *
 * <h2>Attention is spent by doing, not by succeeding</h2>
 *
 * {@link #act} charges the action's cost <em>before</em> it evaluates the move. A probe that finds
 * nothing costs the same as one that finds everything, because §4 prices actions against the target
 * and the target does not know whether you learned anything. The one exception is a tool that never
 * engaged at all — a Rainbow Table against a salted code — which is charged and then fully refunded
 * so that the rule stays "charge first, evaluate second" with a single visible exception rather than
 * a set of actions that might decline.
 *
 * <h2>The ledger is the feature, not the log</h2>
 *
 * Every accepted action appends a row, including ones that achieved nothing and ones the fiction
 * refused. {@code 05} §1 constraint 4 requires a loss to read as <em>"I was too loud"</em> and never
 * as <em>"the game decided"</em>, and §4 puts that requirement here: "the player must always be able
 * to see which action cost what." A strike appends a <b>second</b> row for its penalty, so the
 * three-attention alarm surcharge is attributable to the move that caused it rather than appearing
 * as an unexplained gap in the arithmetic.
 *
 * <h2>Compute is held, then recovered (D-5, UI-6)</h2>
 *
 * An attempt reserves cycles once and holds them for its whole duration, releasing them onto the
 * Thermal Budget curve at resolution — exactly the shape a scan takes since UI-6 ({@code
 * docs/design/04-mining.md} §3.2). It creates no {@link io.github.stoicswe.eyeandsickle.solo.state.TaskState},
 * because there is no duration to model.
 */
public final class BreachRules {

    private BreachRules() {}

    /**
     * Actions whose refusal is a <em>gate</em> rather than a state problem.
     *
     * <p>{@code docs/client/04} §3.5 gives a gate its own exit status precisely so the requirement
     * gets printed instead of a bare refusal — "a gate blocks this, and the requirement is printed",
     * which is what makes a gate legible rather than merely obstructive. Every id here is blocked by
     * not owning a tool from {@code docs/design/06-intrusion-tools.md} or {@code 07}.
     *
     * <p>⚠ One entry, and that is not an oversight. The four class-specific tools that used to sit
     * here — {@code sidechannel}, {@code volley}, {@code rainbow}, {@code harvest} — each countered
     * a mechanic of a puzzle class that no longer exists, and a gate on an action nothing publishes
     * is dead weight that reads as a feature. The Overflow Kit survives because it applies to any
     * layer of any class: it does not ask what was behind the door.
     */
    private static final Set<String> TOOL_GATED = Set.of("bypass");

    // ================================================================== opening

    /**
     * Opens an attempt against {@code target}.
     *
     * <p>Every layer is generated here and persisted (D-4), including the ones the player has not
     * reached — see {@link BoardFactory} for why generating lazily would be rerollable.
     */
    public static BreachResult begin(SoloSave save, BreachTarget target, Instant now) {
        if (save.activeBreach != null) {
            // ⚠ TWO STATES, TWO SENTENCES, AND CONFLATING THEM WAS A DEAD END.
            //
            // A resolved-but-undismissed breach is not open — the player already aborted it, and
            // being told to "abort it first" is an instruction they have carried out and cannot
            // carry out again. It reads as the game refusing to let them try the same target twice.
            // The outcome slate is deliberately not self-clearing (docs/design/05 §1 constraint 4:
            // a loss has to stay readable), so the correct answer names the control that clears it.
            return BreachResult.refused(save.activeBreach.outcome.isEmpty()
                    ? "a breach is already open; abort it first"
                    : "the last attempt is still on screen; dismiss it to start another");
        }
        if (target == null) {
            return BreachResult.refused("no such target");
        }
        long cycles = Targets.attemptCycles(save);
        AllocationState allocation = ComputeRules.reserve(
                save.rig,
                ComputeConsumer.ACTIVE_TOOL,
                "breach --t" + target.difficultyTier().tier(),
                cycles);
        if (allocation == null) {
            return BreachResult.refused("not enough available compute - " + cycles + " needed, "
                    + ComputeRules.availableCycles(save.rig) + " free");
        }
        // Held, not spent: resolution hands it to ComputeRules.beginRecovery. Stamped so the rig
        // monitor can draw the hold the same way it draws a scan's.
        allocation.startedAt = now;

        BreachState breach = new BreachState();
        breach.targetId = target.targetId();
        breach.targetLabel = target.label();
        breach.difficultyTier = target.difficultyTier().tier();
        breach.liveOrDormant = target.liveOrDormant().name();
        breach.minerCrack = target.minerCrack();
        breach.targetFirewallTier = target.firewallTier();
        breach.targetTarpit = target.tarpit();
        breach.targetCanaries = target.canaries();
        breach.allocationId = allocation.allocationId;
        breach.reservedCycles = cycles;

        Rng rng = Rng.of(save);
        // What the player has learned about this machine weights which puzzle they draw — see
        // BoardFactory. A target with no address (the tutorial miner crack) has no report and no
        // knowledge, which is the default and the right answer.
        BoardFactory.build(breach, rng, NodeReports.known(save, target.address()));
        rng.commit(save);

        save.activeBreach = breach;
        int budget = breach.layers.isEmpty() ? 0 : breach.layers.getFirst().budget;
        EventLog.notice(save, "breach",
                "breach opened on " + breach.targetLabel + ": tier " + breach.difficultyTier + ", "
                        + breach.layers.size() + " layer(s), " + budget + " attention.",
                now);
        return BreachResult.applied("breach opened on " + breach.targetLabel + "; " + cycles + " cycles held");
    }

    // ================================================================== a turn

    /**
     * Takes one turn.
     *
     * <p>The order is deliberate and is the same order the ledger reads in: charge, act, record,
     * punish, resolve. Charging first is §4's "attention is spent by doing". Recording before
     * punishing is what puts the strike's penalty on its own row underneath the move that caused it.
     */
    public static BreachResult act(SoloSave save, String actionId, String argument, Instant now) {
        BreachState breach = save.activeBreach;
        if (breach == null) {
            return BreachResult.refused("no breach is open");
        }
        if (!breach.outcome.isEmpty()) {
            return BreachResult.refused("this breach has already resolved; dismiss it to continue");
        }
        LayerState layer = activeLayer(breach);
        if (layer == null) {
            return BreachResult.refused("no layer is active");
        }
        BreachAction action = null;
        for (BreachAction candidate : actions(save)) {
            if (candidate.actionId().equals(actionId)) {
                action = candidate;
                break;
            }
        }
        if (action == null) {
            return BreachResult.refused("no such move on this layer: " + actionId);
        }
        if (!action.enabled()) {
            return TOOL_GATED.contains(actionId)
                    ? BreachResult.gated(action.refusal())
                    : BreachResult.refused(action.refusal());
        }

        Rng rng = Rng.of(save);
        // ⚠ The published cost is the whole cost, on both boards.
        //
        // The retired Traversal class charged a per-destination surcharge on top of its chip's price,
        // which meant the number on the control was not the number the player paid. Neither of the
        // two puzzles that replaced it does that: a pick costs a pick and a commit costs a commit,
        // whichever cell or row it lands on. Keep it that way — docs/design/05 §1 constraint 4 wants
        // a failure to read as "I was too loud", and it cannot if the prices were never the prices.
        int cost = action.attentionCost();
        int spentBefore = layer.spent;
        if (!isBookkeeping(actionId)) {
            layer.spent = Math.min(layer.budget, layer.spent + cost);
        }

        Move move = dispatch(save, layer, actionId, argument, rng);

        if (move.bookkeeping()) {
            // Composition, not a move: no charge, no ledger row, no probe count. The RNG is still
            // committed because a dispatch may have drawn even when nothing was spent.
            rng.commit(save);
            return BreachResult.applied(move.result());
        }
        if (move.refunded()) {
            // Restored, not decremented. The charge above clamps at the budget, so on a nearly
            // exhausted layer subtracting the nominal cost would refund less than was taken — the
            // ledger's running total would stop reconciling with the meter in the one situation
            // where the player is counting most carefully.
            layer.spent = spentBefore;
            cost = 0;
        }
        if (action.kind() == BreachActionKind.PROBE || action.kind() == BreachActionKind.LOUD_TOOL) {
            layer.probesUsed++;
        }
        breach.noise += noiseFor(action.kind()) + move.extraNoise();
        ledger(breach, layer, action, cost, move.result(), move.strike());

        if (move.strike()) {
            strike(save, breach, layer, move, now);
        }

        if (move.cleared()) {
            // A bypass is not a solve, and the distinction survives all the way to the resolution
            // record: docs/design/02 §2.4's proof-of-skill needs the class *solved*, and the Overflow
            // Kit exists precisely to skip solving it (docs/design/06 §2).
            layer.state = "bypass".equals(actionId) ? "BYPASSED" : "CLEARED";
            advance(save, breach, now);
        } else if (move.locked() || layer.strikes >= layer.strikeLimit) {
            // A lockout ends the layer, and a layer that cannot be cleared ends the attempt. Two
            // ways in: striking out, and a class rule saying the board has no legal move left.
            layer.state = "LOCKED";
            resolve(save, breach, BreachOutcome.FAILED, now);
        } else if (layer.spent >= layer.budget) {
            resolve(save, breach, BreachOutcome.FAILED, now);
        }

        rng.commit(save);
        return BreachResult.applied(move.result());
    }

    /** Walks away. Attention spent is gone, noise made stays made ({@code 05} §4.1). */
    public static BreachResult abort(SoloSave save, Instant now) {
        BreachState breach = save.activeBreach;
        if (breach == null) {
            return BreachResult.refused("no breach is open");
        }
        if (!breach.outcome.isEmpty()) {
            return BreachResult.refused("this breach has already resolved; dismiss it to continue");
        }
        boolean crack = breach.minerCrack;
        String label = breach.targetLabel;
        resolve(save, breach, BreachOutcome.ABORTED, now);
        spikeOnAbandon(save, crack, now);
        return BreachResult.applied("disengaged from " + label);
    }

    /**
     * Leaves a short burst of noise behind after an abandonment.
     *
     * <h2>⚠ Abandonment only, and never a crack on your own rig</h2>
     *
     * Failing out of strikes or budget is not this: the player stayed and lost, and the attempt's
     * own noise already priced that. Abandoning is walking away from a live connection, which is the
     * conspicuous act — and until this existed it was also the <em>quietest</em> possible exit, which
     * made "open a breach, read the board, leave if it looks ugly" a free reroll on difficulty.
     *
     * <p>⚠ A miner crack is excluded, for the same reason {@code resolve} zeroes its heat: it is the
     * player's <b>own rig</b>, and Invariant <b>I9</b> is that defending your own machine never makes
     * you more findable. A spike there would make the tutorial breach ({@code 04} §5.1) punish the
     * player for backing out of a fight on their own hardware.
     *
     * <p>⚠ The duration is drawn <b>unconditionally</b>, before the crack test — {@code Rng}'s
     * contract is that consumption must not depend on what was produced, so a crack consumes the
     * same stream an offensive breach does and discards it.
     */
    private static void spikeOnAbandon(SoloSave save, boolean minerCrack, Instant now) {
        Rng rng = Rng.of(save);
        long span = Balance.BREACH_ABANDON_SPIKE_MAX_SECONDS
                - Balance.BREACH_ABANDON_SPIKE_MIN_SECONDS + 1;
        long seconds = Balance.BREACH_ABANDON_SPIKE_MIN_SECONDS + rng.nextInt((int) span);
        rng.commit(save);

        if (minerCrack) {
            return;
        }
        save.noiseSpikeCycles = Balance.BREACH_ABANDON_SPIKE_CYCLES;
        save.noiseSpikeUntil = now.plusSeconds(seconds);
        EventLog.notice(save, "breach",
                "disengaged mid-session — the dropped connection is radiating for about "
                        + seconds + "s. You are easier to find until it settles.",
                now);
    }

    /**
     * Clears a resolved breach off the save.
     *
     * <p>Separate from resolution on purpose: the outcome slate is where a loss becomes
     * comprehensible ({@code 05} §1 constraint 4), and a resolution that cleared itself would mean a
     * player who quit in frustration came back with no way to read why they lost.
     *
     * @return false when there was nothing to dismiss, or the breach is still live
     */
    public static boolean dismiss(SoloSave save) {
        if (save.activeBreach == null || save.activeBreach.outcome.isEmpty()) {
            return false;
        }
        save.activeBreach = null;
        return true;
    }

    // ================================================================== the move list

    /**
     * Every move that is legal right now, priced.
     *
     * <p>The price is attached to the action rather than left for the client to derive, which is
     * {@code docs/design/05-hacking-minigame.md} §4's legibility requirement made structural: the
     * cost is visible before the click, on every action, always. A client that computed costs itself
     * would be a second implementation of the balance table, in the module that is never
     * authoritative (Invariant I14).
     */
    public static List<BreachAction> actions(SoloSave save) {
        BreachState breach = save.activeBreach;
        if (breach == null || !breach.outcome.isEmpty()) {
            return List.of();
        }
        LayerState layer = activeLayer(breach);
        if (layer == null) {
            return List.of();
        }
        List<BreachAction> out = new ArrayList<>();
        switch (layer.puzzleClass) {
            case "OFFSET_CIPHER" -> cipherActions(breach, layer, out);
            default -> matrixActions(breach, layer, out);
        }
        out.add(bypassAction(save, breach, layer));
        return List.copyOf(out);
    }

    /** What one action would cost right now, or {@code -1} if it is not on the board. */
    public static int attentionCost(SoloSave save, String actionId) {
        for (BreachAction action : actions(save)) {
            if (action.actionId().equals(actionId)) {
                return action.attentionCost();
            }
        }
        return -1;
    }

    /**
     * The moves a protocol grid offers: one.
     *
     * <h2>⚠ There is deliberately no probe, no tool and no assist here</h2>
     *
     * Every code, every goal and the whole buffer are published from the first frame, so there is
     * nothing to ask and nothing to buy — a "reveal" action on an open-information board would have
     * to invent something to reveal. The single move is the whole interface, which is what makes this
     * puzzle read as spatial rather than transactional, and is exactly the contrast with the cipher
     * that {@code PuzzleClass} exists to preserve.
     *
     * <p>The Overflow Kit still applies, because it applies to every layer of every class — it does
     * not ask what was behind the door.
     */
    private static void matrixActions(BreachState breach, LayerState layer, List<BreachAction> out) {
        boolean room = layer.matrixBuffer.size() < layer.matrixBufferSize;
        out.add(action(MatrixRules.PICK, BreachActionKind.PROBE, "TAKE CODE",
                layer.matrixRowTurn
                        ? "the path is in row " + layer.matrixCursorRow + " this pick"
                        : "the path is in column " + layer.matrixCursorColumn + " this pick",
                surcharged(breach, Balance.ATTENTION_PROBE), "row:column", room,
                "the buffer is full - nothing more can be taken"));
    }

    /**
     * The moves a cipher offers: compose, commit, and one way out.
     *
     * <h2>⚠ No probe either, and for the opposite reason</h2>
     *
     * The grid has nothing to ask because everything is visible; the cipher has nothing to ask
     * because the answer is arithmetic. A "check one cell" action would be a probe in all but name
     * and would turn a test of care into a test of budget — the player would simply buy the answer
     * one cell at a time. {@code CARRY} exists as the single escape hatch and is priced so that using
     * it on every cell costs more than the layer is worth.
     */
    private static void cipherActions(BreachState breach, LayerState layer, List<BreachAction> out) {
        int last = Math.max(0, layer.cipherObserved.size() - 1);
        out.add(action(OffsetRules.TYPE, BreachActionKind.PROBE, "TYPE OFFSET",
                "writes an offset under a byte; free, and reversible until you commit",
                0, "index:value, e.g. 0:-9", true, ""));

        boolean full = layer.cipherEntered.stream().noneMatch(java.util.Objects::isNull);
        out.add(action(OffsetRules.COMMIT, BreachActionKind.PROBE, "COMMIT",
                "submits every offset; a wrong one is a strike, and it only tells you which",
                surcharged(breach, Balance.ATTENTION_PROBE), "", full,
                "every cell needs an offset before you can commit"));

        out.add(action(OffsetRules.CARRY, BreachActionKind.LOUD_TOOL, "CARRY",
                "solves one byte for you, loudly",
                surcharged(breach, Balance.ATTENTION_LOUD_TOOL), "index 0-" + last, true, ""));
    }

    /**
     * The Overflow Kit chip.
     *
     * <h2>⚠ One bypass per attempt, not per layer</h2>
     *
     * {@code docs/design/05-hacking-minigame.md} §3.1: "Breaching means clearing every layer <b>or
     * bypassing one</b> with the Overflow Kit." Read as once-per-layer, a tier-4 attempt could be
     * bypassed end to end for three presses — the Kit would skip the entire puzzle, which is the
     * meta-rule {@code CLAUDE.md} states as <em>"the puzzle is the game — never let anything skip it
     * wholesale"</em>. Once per attempt is the reading that leaves the Kit what {@code
     * docs/design/06-intrusion-tools.md} §2 calls it: "a panic button with a siren attached, never a
     * default."
     *
     * <p>Caught by running a tier-3 attempt to a {@code BREACHED} outcome without solving a single
     * layer.
     */
    private static BreachAction bypassAction(SoloSave save, BreachState breach, LayerState layer) {
        boolean owned = Targets.owns(save, "overflow-kit");
        boolean spent = breach.layers.stream().anyMatch(l -> "BYPASSED".equals(l.state));
        return action("bypass", BreachActionKind.BYPASS, "OVERFLOW KIT",
                "clears this layer outright, once per attempt; the cost is the point",
                surcharged(breach, bypassCost(layer)), "", owned && !spent,
                !owned
                        ? "requires the Overflow Kit, which is proof-of-skill gated - solve this class first"
                        : "the overflow kit is spent; one layer per attempt");
    }

    /** {@code ceil(budget * 0.80)} — {@code docs/design/05-hacking-minigame.md} §4's "most of the bar". */
    static int bypassCost(LayerState layer) {
        return (int) Math.ceil(layer.budget * Balance.ATTENTION_BYPASS_FRACTION);
    }

    /**
     * Adds the Tarpit surcharge.
     *
     * <p>{@code docs/design/09-defense-and-hardening.md} §1: a Tarpit "slows every intruder action".
     * With no clock left in the breach, "slows" can only mean "costs more", and a surcharge per
     * action is the translation that punishes the play the Tarpit was written to punish — many small
     * moves — rather than duplicating the Firewall's flat difficulty add.
     *
     * <p>Zero-cost actions stay zero: a surcharge on composing your own guess would be charging the
     * player for thinking.
     */
    private static int surcharged(BreachState breach, int base) {
        if (base <= 0 || !breach.targetTarpit) {
            return base;
        }
        return base + Balance.TARPIT_ATTENTION_SURCHARGE;
    }

    private static BreachAction action(
            String id,
            BreachActionKind kind,
            String label,
            String detail,
            int cost,
            String argumentHint,
            boolean enabled,
            String refusal) {
        return new BreachAction(id, kind, label, detail, cost, argumentHint, enabled, enabled ? "" : refusal);
    }

    // ================================================================== bookkeeping

    /**
     * Composition rather than a move — never charged, never ledgered.
     *
     * <p>{@code docs/design/05} §3.7: writing an offset into a cell is not a move any more than
     * hovering over one is. Only {@code commit} is, which is why a player can rewrite the whole row
     * as many times as they like and still only pay when they submit it.
     */
    private static boolean isBookkeeping(String actionId) {
        return OffsetRules.isBookkeeping(actionId);
    }

    private static Move dispatch(SoloSave save, LayerState layer, String actionId, String argument, Rng rng) {
        if ("bypass".equals(actionId)) {
            return Move.cleared("layer bypassed - the kit does not ask what was behind it");
        }
        // ⚠ Neither rule takes the Rng, and that is structural rather than incidental. Every draw an
        // attempt makes happens once, in BoardFactory, at commission — so a reload cannot re-roll a
        // board, and a class rule that could draw would be the one place that guarantee could break.
        return "OFFSET_CIPHER".equals(layer.puzzleClass)
                ? OffsetRules.act(layer, actionId, argument)
                : MatrixRules.act(layer, actionId, argument);
    }

    private static int noiseFor(BreachActionKind kind) {
        return switch (kind) {
            case QUIET_READ -> Balance.NOISE_QUIET_READ;
            case PROBE -> Balance.NOISE_PROBE;
            case LOUD_TOOL -> Balance.NOISE_LOUD_TOOL;
            case BYPASS -> Balance.NOISE_BYPASS;
            case SIDE_CHANNEL -> Balance.NOISE_SIDE_CHANNEL;
        };
    }

    private static void ledger(
            BreachState breach, LayerState layer, BreachAction action, int cost, String result, boolean alarm) {
        AttentionEntryState entry = new AttentionEntryState();
        entry.sequence = ++breach.sequence;
        entry.layerIndex = layer.index;
        entry.actionId = action.actionId();
        entry.kind = action.kind().name();
        entry.label = action.label();
        entry.cost = cost;
        entry.spentAfter = layer.spent;
        entry.result = result;
        entry.alarm = alarm;
        breach.ledger.add(entry);
    }

    /**
     * Records a strike: its own ledger row, its own attention penalty, its own log line.
     *
     * <p>The second row is not duplication. Without it the alarm's three attention would appear as a
     * discrepancy between the previous row's running total and the next one's — an unexplained gap
     * in exactly the artefact that exists to make a loss explicable.
     */
    private static void strike(SoloSave save, BreachState breach, LayerState layer, Move move, Instant now) {
        layer.strikes++;
        layer.spent = Math.min(layer.budget, layer.spent + Balance.ATTENTION_ALARM_PENALTY);
        breach.alarms++;

        AttentionEntryState entry = new AttentionEntryState();
        entry.sequence = ++breach.sequence;
        entry.layerIndex = layer.index;
        entry.actionId = "strike";
        entry.kind = BreachActionKind.PROBE.name();
        entry.label = "STRIKE";
        entry.cost = Balance.ATTENTION_ALARM_PENALTY;
        entry.spentAfter = layer.spent;
        entry.result = "alarm raised - " + layer.strikes + " of " + layer.strikeLimit;
        entry.alarm = true;
        breach.ledger.add(entry);

        if (!move.consequence().isEmpty()) {
            breach.consequences.add(move.consequence());
        }
        EventLog.warning(save, "breach",
                "alarm on " + breach.targetLabel + ": " + layer.strikes + " of " + layer.strikeLimit
                        + " strikes on layer " + layer.index + ".",
                now);
    }

    /** Promotes the next pending layer, or resolves the attempt when there is none. */
    private static void advance(SoloSave save, BreachState breach, Instant now) {
        for (LayerState layer : breach.layers) {
            if ("PENDING".equals(layer.state)) {
                layer.state = "ACTIVE";
                breach.activeLayer = layer.index;
                EventLog.notice(save, "breach",
                        "layer " + layer.index + " open on " + breach.targetLabel + ": "
                                + layer.puzzleClass.toLowerCase(Locale.ROOT) + ", " + layer.budget + " attention.",
                        now);
                return;
            }
        }
        resolve(save, breach, BreachOutcome.BREACHED, now);
    }

    static LayerState activeLayer(BreachState breach) {
        for (LayerState layer : breach.layers) {
            if ("ACTIVE".equals(layer.state)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * Rolls whether the machine answered in the other direction.
     *
     * <h2>Noise is the variable, which is what makes quiet play worth the trouble</h2>
     *
     * A breach that never went past a quiet read resolves at {@code Balance.NOISE_BASE} and is very
     * nearly safe; one that leant on the Overflow Kit and tripped two canaries is several times that
     * and is genuinely dangerous. {@code docs/design/05} §4 already prices loudness <em>inside</em> the
     * puzzle as trace; this is the price <em>outside</em> it, and having both is what stops "bypass
     * everything" being free the moment the trace bar is survivable.
     *
     * <h2>⚠ Rolled at resolution, not at commission — and that is the opposite of a sweep</h2>
     *
     * {@code NetRules.beginSweep} freezes its counter-hack at the start, because a sweep's whole
     * outcome is decided before it runs and a reload must replay nothing. A breach's noise <em>does
     * not exist yet</em> at commission — it is the sum of choices the player has not made — so rolling
     * early would either ignore those choices or predict them. It is rolled once, here, when the
     * figure it depends on is final, and the breach resolves in the same call so there is nothing to
     * reload into.
     *
     * <h2>⚠ Never for a crack, and never at home</h2>
     *
     * A crack runs on the player's own rig; nothing leaves the machine, so there is nobody to answer
     * (Invariant <b>I9</b>, and the reason the crack is the tutorial). And depth zero never bites
     * back, the same rule {@code Balance.NET_COUNTER_HACK_HOME} fixes for sweeps: the home server is
     * where the game teaches, and a teaching space that occasionally plants a parasite on the student
     * is one they learn to avoid.
     *
     * <p>Fires on <b>every</b> outcome, including a failure and an abort. What provoked the machine is
     * the noise, and walking away does not un-make it — {@code 05} §4.1's "the noise you made stays
     * made", now with something behind it.
     */
    private static void answerBack(SoloSave save, BreachState breach, Instant now) {
        int depth = depthOf(save, breach.targetId);
        double chance = Balance.breachCounterHackChance(breach.resolvedNoise, depth);

        // ⚠ Drawn UNCONDITIONALLY, before the chance is tested. Rng's contract is that a generator
        // whose consumption depends on what it produced makes a replay from a stored seed stop being
        // a replay — so the draw happens even when the chance is zero and the value is discarded.
        Rng rng = Rng.of(save);
        double roll = rng.nextDouble();
        rng.commit(save);

        if (chance <= 0.0d || roll >= chance) {
            return;
        }
        io.github.stoicswe.eyeandsickle.solo.rules.IntrusionRules.plantCounterHack(save, depth, now);
        breach.consequences.add(
                "the machine answered: something of theirs is running on your rig now. "
                        + "You were loud enough to be worth it.");
    }

    /**
     * How deep the breached machine's server sits from home, or {@code 0}.
     *
     * <p>Zero for anything this build cannot place — a target id from an older save, a node that has
     * gone. Zero is the reading that cannot invent a counter-hack nobody earned.
     */
    private static int depthOf(SoloSave save, String targetId) {
        if (save.topology == null || targetId == null || !targetId.startsWith("node:")) {
            return 0;
        }
        String address = targetId.substring("node:".length());
        for (var host : save.topology.hosts) {
            if (!host.address.equals(address)) {
                continue;
            }
            for (var server : save.topology.servers) {
                if (server.serverId.equals(host.serverId)) {
                    return server.depthFromHome;
                }
            }
        }
        return 0;
    }

    // ================================================================== resolution

    private static void resolve(SoloSave save, BreachState breach, BreachOutcome outcome, Instant now) {
        // The held cycles start recovering on the Thermal Budget curve, exactly like a finished
        // scan. Dated from now rather than from when the attempt opened, because unlike a scan the
        // attempt has no published duration to date it from — it ended when the player ended it.
        ComputeRules.beginRecovery(save.rig, breach.allocationId, now);

        // ⚠ The class multiplier lands here, on the total, and nowhere else. See
        // Balance.breachNoisePoints for why scaling per action would have made the cipher QUIETER.
        int noise = Balance.NOISE_BASE
                + Balance.breachNoisePoints(breach.puzzleClass, breach.noise)
                + breach.alarms * Balance.NOISE_PER_ALARM;
        breach.resolvedNoise = noise;

        // Invariant I9: a miner crack generates zero heat on EVERY outcome, including failure.
        // Defending your own rig never contributes to being wanted, and that is exactly what makes
        // the crack safe to lose repeatedly and therefore usable as the tutorial (04 §5.1).
        int gain = breach.minerCrack ? 0 : noise / Balance.NOISE_PER_HEAT_POINT;
        int heatBefore = save.personalHeat;
        save.personalHeat = Math.min(Balance.PERSONAL_HEAT_MAX, save.personalHeat + gain);
        breach.resolvedHeat = save.personalHeat - heatBefore;

        if (breach.minerCrack) {
            resolveCrack(save, breach, outcome, now);
        } else {
            resolveOffensive(save, breach, outcome, now);
            answerBack(save, breach, now);
        }

        ResolutionState record = record(breach, outcome, now);
        save.resolutions.add(record);
        breach.resolvedSchematicMaterial = SalvageRules.award(save, record);
        if (breach.resolvedSchematicMaterial > 0) {
            breach.consequences.add("recovered " + breach.resolvedSchematicMaterial
                    + " unit of schematic material (" + SalvageRules.remainingForUnlock(save) + " more for an unlock)");
        }

        if (outcome == BreachOutcome.ABORTED) {
            breach.consequences.add("you walked away; the noise you made stays made");
        }
        if (outcome == BreachOutcome.FAILED && breach.consequences.isEmpty()) {
            // ⚠ A failure with no stated consequence reads as "the game decided" — the one reading
            // docs/design/05 §1 constraint 4 forbids. This can only fire on a very quiet loss
            // against an undefended target, and it must still say something true.
            breach.consequences.add("the attempt failed; the attention you spent is gone");
        }

        breach.outcome = outcome.name();
        breach.activeLayer = -1;

        String summary = breach.targetLabel + ": " + outcome.name().toLowerCase(Locale.ROOT)
                + ", noise " + noise + (breach.resolvedHeat > 0 ? ", heat +" + breach.resolvedHeat : ", no heat");
        if (outcome == BreachOutcome.BREACHED) {
            EventLog.notice(save, "breach", summary, now);
        } else {
            EventLog.warning(save, "breach", summary, now);
        }
    }

    /**
     * A crack against a foreign miner on the player's own rig — {@code docs/design/04-mining.md}
     * §5.1.
     *
     * <p>Success seizes the buffer and reclaims the compute. It is a <b>transfer, not a faucet</b>:
     * "the buffer physically resides on the host's machine ... so the EC is already there to take —
     * no new currency enters the economy" ({@code docs/design/03-economy.md} §5 rule 3).
     *
     * <p>⚠ Failure is the dead-man switch, and it must not be softened. §5.1: "a botched crack
     * flushes the buffer to the deployer immediately and the miner self-destructs. Host reclaims
     * compute but gains nothing, and the deployer is alerted with the host's handle attached ...
     * <b>Without this, cracking would strictly dominate killing.</b>" The four-response menu in §5 is
     * core game content; making a failed crack merely disappointing would collapse it to one option.
     */
    private static void resolveCrack(SoloSave save, BreachState breach, BreachOutcome outcome, Instant now) {
        MinerState miner = foreignMiner(save, breach.targetId);
        if (miner == null) {
            breach.consequences.add("the miner was already gone by the time this resolved");
            return;
        }
        if (outcome == BreachOutcome.ABORTED) {
            breach.consequences.add("you backed out; it is still running, and still earning for somebody");
            return;
        }
        java.math.BigInteger buffer = miner.bufferedWei;
        long reclaimed = miner.hostCycles;
        miner.bufferedWei = java.math.BigInteger.ZERO;
        save.rig.foreignMiners.remove(miner);
        ComputeRules.release(save.rig, miner.allocationId);

        if (outcome == BreachOutcome.BREACHED) {
            if (buffer.signum() > 0) {
                LedgerRules.apply(save, buffer, "CRACK", "Cracked " + breach.targetLabel, now);
            }
            breach.resolvedLootWei = buffer;
            breach.resolvedLootLabel = Ethecoin.format(buffer) + " seized from the buffer";
            breach.consequences.add("the miner is gone and " + reclaimed + " cycles came back");
            breach.consequences.add("the deployer learns nothing");
            return;
        }
        breach.consequences.add("dead-man switch: " + Ethecoin.format(buffer) + " flushed to the deployer");
        breach.consequences.add("the miner self-destructed; " + reclaimed + " cycles came back and nothing else did");
        breach.consequences.add("your handle was exposed to "
                + (miner.deployerHandle.isBlank() ? "the deployer" : miner.deployerHandle));
    }

    /**
     * An offensive breach of a node out in the world.
     *
     * <p>⚠ Loot is an <b>item, never ethecoin</b>. Minting currency on a successful breach would be a
     * faucet ({@code docs/design/03-economy.md} §5 rule 3), and ethecoin must never buy a ceiling
     * (Invariants I1 and I2) — an income source attached to the game's main progression loop is the
     * shortest path to both.
     */
    private static void resolveOffensive(SoloSave save, BreachState breach, BreachOutcome outcome, Instant now) {
        if (outcome == BreachOutcome.BREACHED) {
            ItemState item = new ItemState();
            item.itemType = "data-cache";
            item.displayName = "data cache from " + breach.targetLabel;
            item.tier = StorageTier.STANDARD_STORAGE.name();
            item.acquiredAt = now;
            item.origin = "breached";
            save.items.add(item);
            breach.resolvedLootLabel = item.displayName;
            breach.consequences.add("took " + item.displayName + " into standard storage");
            return;
        }
        if (outcome == BreachOutcome.FAILED) {
            breach.consequences.add("the attempt failed and the attention you spent is gone");
            if (breach.targetCanaries) {
                breach.consequences.add("a canary token on the target tagged your handle");
            }
            if (breach.resolvedHeat > 0) {
                breach.consequences.add("personal heat rose by " + breach.resolvedHeat
                        + "; laying low or self-mining is the way down");
            }
        }
    }

    /**
     * Builds the persisted resolution record.
     *
     * <p>{@code puzzleClass} names the layer where the attempt <em>ended</em>: the deepest one on a
     * success, the one that stopped the player otherwise. One class, because that is the shape
     * {@code docs/design/05-hacking-minigame.md} §2 fixed and the shape the server persists. Every
     * class actually solved is listed in {@code classesCleared} instead of being given a row of its
     * own — extra rows would be a countable artefact, and counting these is the exploit
     * ({@code ResolutionRecord}'s javadoc, Invariant I7).
     *
     * <p>A <b>bypassed</b> layer is not a cleared one. {@code docs/design/02-unlock-gates.md} §2.4
     * requires the class to have been <em>solved</em>, and the Overflow Kit exists to skip solving
     * it — crediting a bypass would let the proof-of-skill item unlock the next proof-of-skill item.
     */
    private static ResolutionState record(BreachState breach, BreachOutcome outcome, Instant now) {
        ResolutionState record = new ResolutionState();
        // The one line the network rules need out of this engine. NetRules.reconcileFootholds reads
        // it to grant a foothold and the host's one-time payout for every BREACHED row; without it
        // there is no way to tell which machine an attempt was against, and the breach engine would
        // have to learn that a topology exists. See ResolutionState#targetId.
        record.targetId = breach.targetId;
        record.difficultyTier = breach.difficultyTier;
        record.liveOrDormant = breach.liveOrDormant;
        record.outcome = outcome.name();
        record.at = now;

        String deepest = breach.layers.isEmpty() ? "ENUMERATION" : breach.layers.getFirst().puzzleClass;
        for (LayerState layer : breach.layers) {
            record.probesUsed += layer.probesUsed;
            if (!"PENDING".equals(layer.state)) {
                deepest = layer.puzzleClass;
            }
            if ("CLEARED".equals(layer.state)) {
                record.classesCleared.add(layer.puzzleClass);
            }
        }
        record.puzzleClass = deepest;
        return record;
    }

    private static MinerState foreignMiner(SoloSave save, String targetId) {
        String minerId = targetId.startsWith("miner:") ? targetId.substring("miner:".length()) : targetId;
        for (MinerState miner : save.rig.foreignMiners) {
            if (miner.minerId.equals(minerId)) {
                return miner;
            }
        }
        return null;
    }

}
