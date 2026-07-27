package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * One line of the itemised attention ledger.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §4: "attention is visible and itemised at all times,
 * which is where the <em>comprehensible failure</em> constraint lives: the player must always be able
 * to see which action cost what." §1 constraint 4 is the constraint being satisfied, and it is not
 * decoration — cracking is the game's tutorial vector ({@code docs/design/04-mining.md} §5.1)
 * specifically because a player can lose it safely and understand why. Take the ledger away and
 * cracking stops teaching anything.
 *
 * <p>So a row exists for <strong>every</strong> action that spent attention, including the ones that
 * achieved nothing and the ones the fiction refused. A gap in the ledger is the bug: it is the exact
 * moment where the player's account of what happened stops matching the game's.
 *
 * <h2>Why {@code result} is prose and {@code spentAfter} is a number</h2>
 *
 * Both are the engine's, and both are already-decided facts rather than things a renderer should
 * reconstruct. {@code result} says what came back in the same words the layer would have used —
 * {@code "07 OPEN — ssh"}, {@code "salted — the table is useless here"} — because a ledger of costs
 * without outcomes tells the player how much they spent and not what they learned. {@code spentAfter}
 * is the running total <em>within the layer</em>, so the row can be read against the attention bar
 * beside it without the reader summing the column in their head.
 *
 * @param sequence 1-based, counted per breach rather than per layer, so the ledger reads as one
 *     continuous account of the attempt
 * @param layerIndex which layer this happened on, 0-based
 * @param actionId the {@link BreachAction#actionId()} that was invoked
 * @param kind the action's character, so quiet reads and loud tools stay distinguishable after the fact
 * @param label what the player pressed, with its argument resolved: {@code "PROBE 07"}
 * @param cost attention charged by this action; never negative, and zero only for a Side-Channel read
 * @param spentAfter running attention total within {@code layerIndex} once this row was applied
 * @param result what came back, in words; {@code ""} only when the action genuinely produced no reading
 * @param alarm whether this action tripped a strike — the one row kind that is allowed to be loud on
 *     screen, and the reason a failure is attributable to a move rather than to the game
 */
public record AttentionEntry(
        int sequence,
        int layerIndex,
        String actionId,
        BreachActionKind kind,
        String label,
        int cost,
        int spentAfter,
        String result,
        boolean alarm) {

    public AttentionEntry {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(result, "result");

        if (sequence < 1) {
            throw new IllegalArgumentException("sequence is 1-based within a breach, was " + sequence);
        }
        if (layerIndex < 0) {
            throw new IllegalArgumentException("layerIndex must not be negative, was " + layerIndex);
        }
        if (cost < 0) {
            throw new IllegalArgumentException("cost must not be negative, was " + cost);
        }
        if (spentAfter < 0) {
            throw new IllegalArgumentException("spentAfter must not be negative, was " + spentAfter);
        }
    }
}
