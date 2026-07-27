package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the player knows about the breach they have open: the target, every layer, the moves
 * available right now, the itemised ledger, and — once it is over — how it ended.
 *
 * <p>This is the breach's equivalent of {@link ComputeBudget}, and for the same reason. That record is
 * the seam that makes a local rules engine and a remote home server indistinguishable to a view: the
 * authoritative side computes, this module describes, and the client renders without ever learning
 * which side it is talking to. A breach is the same shape of thing — a live readout of authoritative
 * state — and a real home server will send one, so it is described here rather than as a nested type
 * inside the client's session port. No new module edges are needed for that: the single-player engine
 * and the client both already depend on {@code protocol}.
 *
 * <h2>A snapshot carries only revealed information</h2>
 *
 * The hidden secret of a layer — the Logic code, the true port states, which lattice candidate is the
 * objective, which nodes are trapped — <strong>never</strong> appears here, at any depth. Unknown is
 * encoded as {@link PortState#UNKNOWN}, as {@code ""}, or as absence from a list, and every board type
 * is shaped so there is no field the answer could occupy. See {@link BreachBoard}.
 *
 * <p>The reason is not that a single-player save is hard to edit — it is a JSON file the player owns.
 * It is that these records go over a wire, and the only version of this discipline that survives is the
 * one where the honest path <em>cannot</em> leak. A renderer holding a snapshot is physically unable to
 * draw a cheat, because the information to draw it never arrived, and that property holds without
 * anyone re-auditing the client on every release.
 *
 * <h2>Invariant I9 is checked here, because this is the only place both halves are present</h2>
 *
 * {@link BreachResolution} carries the heat and does not know whether the attempt was a crack;
 * {@link BreachTarget} knows and is long gone by resolution time. This record holds both, so this is
 * where "a miner crack generates zero heat on <em>every</em> outcome" stops being prose. It matters
 * beyond bookkeeping: {@code docs/design/04-mining.md} §5.1 makes cracking the tutorial vector
 * specifically because losing one costs no heat, so a crack that charged heat on failure would punish
 * the exact behaviour the design wants a new player to try repeatedly.
 *
 * @param breachId stable identity for this attempt, so a view can tell a refresh from a new breach
 * @param targetId what the attempt was opened against
 * @param targetLabel what to call it on screen
 * @param difficultyTier the attempt's tier, on the one shared scale
 * @param liveOrDormant whether the target was defended — the field proof-of-skill credit turns on
 * @param minerCrack whether this is a crack on the player's own rig, and therefore heat-free
 * @param activeLayer index into {@code layers}; {@code -1} once the attempt is resolved
 * @param layers every layer of the target, outermost first
 * @param actions the legal moves right now, in display order, each already priced
 * @param ledger the whole attempt's itemised attention, oldest first, append-only
 * @param noiseSoFar §2's {@code noiseGenerated}, accumulated so far
 * @param reservedCycles compute held for the duration of the attempt, released into recovery at
 *     resolution ({@code docs/design/01-core-resources.md} §1.3)
 * @param resolution {@code null} while the breach is live; non-null once it has ended and before the
 *     player has dismissed the outcome
 */
public record BreachSnapshot(
        String breachId,
        String targetId,
        String targetLabel,
        DifficultyTier difficultyTier,
        TargetState liveOrDormant,
        boolean minerCrack,
        int activeLayer,
        List<BreachLayer> layers,
        List<BreachAction> actions,
        List<AttentionEntry> ledger,
        int noiseSoFar,
        long reservedCycles,
        BreachResolution resolution) {

    public BreachSnapshot {
        Objects.requireNonNull(breachId, "breachId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(targetLabel, "targetLabel");
        Objects.requireNonNull(difficultyTier, "difficultyTier");
        Objects.requireNonNull(liveOrDormant, "liveOrDormant");

        layers = List.copyOf(layers);
        actions = List.copyOf(actions);
        ledger = List.copyOf(ledger);

        // -1 is the resolved sentinel; anything below it is a counter that has gone backwards. An
        // activeLayer past the end of the list is deliberately NOT rejected — a producer trimming
        // layers off a snapshot is making a disclosure decision, not a mistake — and active() reports
        // it as "no active layer" rather than throwing out of a repaint.
        if (activeLayer < -1) {
            throw new IllegalArgumentException("activeLayer is an index or -1 when resolved, was " + activeLayer);
        }
        if (noiseSoFar < 0) {
            throw new IllegalArgumentException("noiseSoFar must not be negative, was " + noiseSoFar);
        }
        if (reservedCycles < 0) {
            throw new IllegalArgumentException("reservedCycles must not be negative, was " + reservedCycles);
        }
        if (minerCrack && resolution != null && resolution.heatGained() != 0) {
            throw new IllegalArgumentException("A miner crack runs on the player's own rig and generates no heat "
                    + "on any outcome (Invariant I9, docs/design/04-mining.md §5.1); this one reported "
                    + resolution.heatGained());
        }
    }

    /** Whether the attempt has ended. A resolved snapshot is still worth rendering — the outcome is the point. */
    public boolean resolved() {
        return resolution != null;
    }

    /**
     * The layer the player is working right now.
     *
     * @return the active layer, or empty once the attempt is resolved or when the producer did not send
     *     the layer it named
     */
    public Optional<BreachLayer> active() {
        if (resolved() || activeLayer < 0 || activeLayer >= layers.size()) {
            return Optional.empty();
        }
        return Optional.of(layers.get(activeLayer));
    }

    /**
     * Attention over the whole attempt: every layer's spend against every layer's budget.
     *
     * <p>The per-attempt figure §2's contract calls {@code traceProgress}, while the breach is still
     * live. Once it resolves, {@link BreachResolution#traceProgress()} is the engine's own final
     * reading and is the one to trust — this method sums what was disclosed, and a producer that
     * withheld a layer would sum less than the engine did.
     *
     * @return the summed budget; a nominal empty budget when there are no layers to sum
     */
    public AttentionBudget totalAttention() {
        int spent = 0;
        int budget = 0;
        for (BreachLayer layer : layers) {
            spent += layer.attention().spent();
            budget += layer.attention().budget();
        }
        // AttentionBudget forbids a zero budget, since a bar with nothing in it cannot express a
        // fraction. A snapshot with no layers is not a breach, but a view can still ask during teardown
        // or against a stub, and a getter that throws mid-repaint is a worse answer than "nothing spent".
        return budget == 0 ? new AttentionBudget(0, 1) : new AttentionBudget(spent, budget);
    }
}
