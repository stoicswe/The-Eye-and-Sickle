package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How much of a layer's attention budget is gone, and how much is left.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §4, decided 2026-07-26: "a breach is turn-based. There
 * is no wall clock anywhere in it. Each layer grants an attention budget set by {@code difficultyTier},
 * and every action the player takes spends from it. Breach the layer before the budget empties, or
 * fail." This record is that bar.
 *
 * <p>Two of §4's three stated reasons for replacing the original real-time trace live directly in this
 * type. Invariant I10 becomes <em>measurable</em>, because the bot-versus-human gap is now denominated
 * in a countable resource rather than in seconds that vary with a player's hardware and reflexes. And
 * the accessibility risk §5 flagged — timed pressure across a windowed interface — is simply absent:
 * there is nothing here to run out on its own while the player thinks.
 *
 * <h2>{@link #traceProgress()} is how §2's contract survived §4</h2>
 *
 * The economy-facing contract in §2 has always produced a {@code traceProgress} scalar, and §4 changed
 * what it measures without changing that it exists: it is now <strong>attention consumed as a fraction
 * of the budget</strong>. §2 was written separately from the puzzle content precisely so a mechanism
 * change underneath it would not ripple outward, and this is the one place that promise had to be kept.
 *
 * <h2>There is deliberately no {@code spend(int)}</h2>
 *
 * Returning a new budget with the cost subtracted looks harmless and is not: charging attention is a
 * rule, not arithmetic. The engine clamps at the budget rather than going negative, adds a penalty when
 * an action trips a strike, and charges before it evaluates the move — attention is spent by
 * <em>doing</em>, not by succeeding. A client that spent locally would drift from the server on the
 * first of those and would then render a bar that disagrees with the ledger beside it.
 *
 * <p>What this record does offer is presentation arithmetic over numbers the engine already sent, the
 * same line {@link ComputeBudget} draws. It decides nothing.
 *
 * @param spent attention already consumed on this layer, including strike penalties; never negative and
 *     never past {@code budget}
 * @param budget the layer's total attention, set by {@code difficultyTier} and modified by the target's
 *     defence profile before the attempt begins
 */
public record AttentionBudget(int spent, int budget) {

    public AttentionBudget {
        // A zero budget is not a degenerate layer, it is an unplayable one: no action could ever be
        // afforded, and traceProgress() would divide by zero on the first repaint.
        if (budget < 1) {
            throw new IllegalArgumentException("budget must be positive, was " + budget);
        }
        if (spent < 0) {
            throw new IllegalArgumentException("spent must not be negative, was " + spent);
        }
        // The engine clamps at the budget when an action would overshoot, so an overshooting value on
        // the wire means a caller summed two layers' spend against one layer's budget — a bug that
        // otherwise shows up much later as a trace bar reading 140%.
        if (spent > budget) {
            throw new IllegalArgumentException("spent " + spent + " exceeds budget " + budget);
        }
    }

    /** Attention still available on this layer. */
    public int remaining() {
        return budget - spent;
    }

    /**
     * Whether the bar is empty.
     *
     * <p>§4.1: budget exhausted is failure, with the consequence scaled by target. This reports the
     * condition; the engine decides what follows from it.
     */
    public boolean exhausted() {
        return spent >= budget;
    }

    /**
     * Whether there is room in the bar for a cost of this size.
     *
     * <p>This answers "would it fit", <strong>not</strong> "may I do this". The authoritative answer is
     * {@link BreachAction#enabled()} with its {@link BreachAction#refusal()}, because affordability is
     * only one of the reasons a move can be unavailable — a tool may not be in the loadout, a
     * once-per-layer read may be used up, an action may need an argument the player has not selected. A
     * client that greys chips out on this method alone will disagree with the engine the first time a
     * Tarpit surcharges a cost ({@code docs/design/09-defense-and-hardening.md} §1).
     *
     * @param cost the attention a move would charge
     * @return whether {@code cost} is a sane non-negative amount that fits in {@link #remaining()}
     */
    public boolean canAfford(int cost) {
        return cost >= 0 && cost <= remaining();
    }

    /**
     * §2's {@code traceProgress}: attention consumed as a fraction of the budget, 0..1 inclusive.
     *
     * @return 0.0 with nothing spent, 1.0 when the bar is empty
     */
    public double traceProgress() {
        return spent / (double) budget;
    }
}
