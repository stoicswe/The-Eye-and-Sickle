package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * One move the player may make right now, with its price already attached.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §4 states the requirement this record exists to make
 * structural: "attention is visible and itemised at all times", and a loss "has to read as <em>I was
 * too loud</em>, never <em>the game decided</em>". Itemising <em>after</em> the fact is half of that;
 * the other half is that the cost is on screen <em>before</em> the click, on every action, always.
 *
 * <h2>Why the cost travels on the action instead of being derived</h2>
 *
 * A client could plausibly compute it: §4 publishes a cost per {@link BreachActionKind}, and a lookup
 * table is five lines. It would also be wrong within one balance pass. The real price is the kind, the
 * layer, the target's defence profile and the loadout together — a Tarpit surcharges every action, the
 * Overflow Kit's cost is a fraction of <em>this</em> layer's budget, and the Rainbow Table charges
 * nothing at all against a salted lock because it refuses instead of firing ({@code
 * docs/design/06-intrusion-tools.md} §2). A derived number would be right in the tutorial and quietly
 * wrong everywhere the game gets interesting, which is the worst possible failure mode for the one
 * readout the design says a player must be able to trust.
 *
 * <p>So the engine prices the move and sends it. That is also what keeps {@code protocol} clean: no
 * cost table ever has to live here (see {@link BreachActionKind}).
 *
 * <h2>No mute refusals</h2>
 *
 * {@code enabled} and {@code refusal} are checked against each other, the same both-markers-must-agree
 * discipline {@link ComputeAllocation} applies to its recovery timestamp. A disabled action with an
 * empty refusal is a chip the player cannot press and cannot be told why — §1 constraint 4 again, one
 * level down from the attempt. If the engine has decided a move is unavailable it already knows the
 * reason; making that reason mandatory on the wire is what stops it being dropped on the way out.
 *
 * @param actionId stable id the client echoes back — {@code "probe"}, {@code "declare"}, {@code "sweep"}.
 *     Never localised and never derived from the label
 * @param kind what character of move this is, for grouping and for reading the trade before the number
 * @param label what the chip says, e.g. {@code "PROBE SLOT"}
 * @param detail one clause of what it does; {@code ""} when the label says everything
 * @param attentionCost what this move costs on <em>this</em> layer with <em>this</em> loadout; never
 *     negative, and zero for a Side-Channel read or for pure bookkeeping
 * @param argumentHint {@code ""} when the action takes no argument, else what to supply, e.g.
 *     {@code "slot 0-15"}
 * @param enabled whether the move can be made right now
 * @param refusal {@code ""} when enabled; otherwise why not, in words the player can act on
 */
public record BreachAction(
        String actionId,
        BreachActionKind kind,
        String label,
        String detail,
        int attentionCost,
        String argumentHint,
        boolean enabled,
        String refusal) {

    public BreachAction {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(kind, "kind");
        // Unknown is the empty string throughout the breach vocabulary, never null — the lists these
        // records sit in are built with List.copyOf, which rejects nulls outright, and a field that is
        // sometimes "" and sometimes null is the shape that produces a chip labelled "null".
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(argumentHint, "argumentHint");
        Objects.requireNonNull(refusal, "refusal");

        if (attentionCost < 0) {
            throw new IllegalArgumentException("attentionCost must not be negative, was " + attentionCost);
        }

        if (enabled && !refusal.isEmpty()) {
            throw new IllegalArgumentException(
                    "An enabled action carries no refusal; " + actionId + " was enabled with \"" + refusal + "\"");
        }
        if (!enabled && refusal.isEmpty()) {
            throw new IllegalArgumentException("A disabled action must say why it is disabled "
                    + "(docs/design/05-hacking-minigame.md §1 constraint 4); " + actionId + " gave no refusal");
        }
    }
}
