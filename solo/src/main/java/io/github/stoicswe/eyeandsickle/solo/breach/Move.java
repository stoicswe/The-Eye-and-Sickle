package io.github.stoicswe.eyeandsickle.solo.breach;

/**
 * What one class rule hands back to {@link BreachRules} after resolving a move.
 *
 * <h2>Why the three class rules do not touch the breach's bookkeeping</h2>
 *
 * Attention, strikes, noise, the ledger and the layer's lifecycle are the same in all three classes,
 * and {@code docs/design/05-hacking-minigame.md} §4 makes them the same on purpose — the per-action
 * cost table is one table, not three. So {@link EnumerationRules}, {@link LogicRules} and
 * {@link TraversalRules} decide only what <em>happened</em>, and {@link BreachRules} decides what
 * that costs. A class rule that charged its own attention would be a fourth place for the cost table
 * to drift, and drift in that table is the "comprehensible failure" constraint failing quietly.
 *
 * @param result what came back, in words, for the ledger row — never a code
 * @param strike whether this tripped an alarm
 * @param extraNoise noise beyond what the action's kind already costs; canaries add here
 * @param refunded whether the whole charge is handed back; see {@link #refunded(String)}
 * @param cleared whether the layer is now solved
 * @param bookkeeping whether this was composition rather than a move — never charged, never ledgered
 * @param consequence a line for the resolution's consequence list, or {@code ""}
 */
public record Move(
        String result,
        boolean strike,
        int extraNoise,
        boolean refunded,
        boolean cleared,
        boolean bookkeeping,
        String consequence) {

    /** An ordinary move that did something. */
    public static Move of(String result) {
        return new Move(result, false, 0, false, false, false, "");
    }

    /** A move that solved the layer. */
    public static Move cleared(String result) {
        return new Move(result, false, 0, false, true, false, "");
    }

    /** A move that tripped an alarm. It still costs its attention: you paid to be wrong. */
    public static Move strike(String result) {
        return new Move(result, true, 0, false, false, false, "");
    }

    /**
     * A move that tripped a canary — an alarm plus a consequence that outlives the attempt.
     *
     * <p>{@code docs/design/09-defense-and-hardening.md} §2: a Canary Token "both alerts you
     * <em>and</em> tags the toucher's handle", and that handle-tag "feeds directly into the evidence
     * and informant systems ({@code 12}) — a canary is how you build a case against a raider". So a
     * canary is not a louder strike; it is a strike that leaves a name behind.
     */
    public static Move canary(String result, String consequence, int extraNoise) {
        return new Move(result, true, extraNoise, false, false, false, consequence);
    }

    /**
     * Composition rather than a move: never charged, never ledgered.
     *
     * <p>Marking a slot or setting a tumbler is the player thinking, and {@code
     * docs/design/05-hacking-minigame.md} §4 prices <em>actions</em> against the target. Charging for
     * arranging your own notes would make the ledger — the artefact that has to explain a loss —
     * mostly full of rows about the player's scratchpad, which is exactly the burial {@code
     * alert-fatigue(7)} describes.
     */
    public static Move bookkeeping(String result) {
        return new Move(result, false, 0, false, false, true, "");
    }

    /**
     * A move that was charged and then handed its whole attention back.
     *
     * <p>Exactly one thing uses this: a Rainbow Table against a salted code ({@code
     * docs/design/06-intrusion-tools.md} §2's hard counter). It costs nothing because the tool never
     * engaged, but it still earns a ledger row at zero, because "the table was useless here" is
     * information the player needs and a silent no-op is indistinguishable from a bug.
     *
     * <p>Charge-then-refund rather than never-charge so that {@link BreachRules} keeps one rule —
     * charge first, evaluate second — instead of a special case that has to know in advance which
     * actions might decline. The whole charge comes back, including any Tarpit surcharge: a defence
     * that taxed an action which never happened would be charging for a move the target refused.
     */
    public static Move refunded(String result) {
        return new Move(result, false, 0, true, false, false, "");
    }
}
