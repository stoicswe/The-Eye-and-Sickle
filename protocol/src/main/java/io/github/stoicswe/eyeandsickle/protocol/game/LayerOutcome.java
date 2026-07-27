package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Where one layer of a breach stands.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1: a target composes 1–N layers, each an instance of
 * some {@link PuzzleClass}, and breaching means clearing every layer or bypassing one with the Overflow
 * Kit. §1 constraint 6 makes that structure non-negotiable — the Overflow Kit's published function is
 * "bypasses a puzzle layer entirely" ({@code docs/design/06-intrusion-tools.md} §1), so a layer has to
 * be a thing that can be individually cleared, skipped, or lost.
 *
 * <h2>{@link #CLEARED} and {@link #BYPASSED} are not the same outcome</h2>
 *
 * Collapsing them to a single {@code DONE} is the obvious tidy-up and it throws away the distinction
 * the design is built on: a cleared layer is a solve, a bypassed one is a purchase. Pillar 1 says the
 * puzzle <em>is</em> the game, and a UI that paints the two identically teaches the player that they
 * are interchangeable — which is exactly the belief the Overflow Kit's very-high noise cost and
 * proof-of-skill gate exist to prevent.
 *
 * <p>Nothing downstream reads the distinction <em>today</em>: §2's {@link ResolutionRecord} describes
 * the attempt, not each layer, so proof-of-skill credit is currently attempt-level. That is an argument
 * for keeping the two constants apart, not for merging them — the information is free to carry now and
 * unrecoverable later.
 *
 * <h2>{@link #LOCKED} ends the attempt, not just the layer</h2>
 *
 * §3.3 lists error tolerance — "how many wrong probes before an alarm/lockout" — as one of the four
 * things {@code difficultyTier} scales. A locked layer is one whose strike limit ran out, and since
 * breaching requires clearing <em>every</em> layer, a layer that can no longer be cleared has already
 * decided the attempt. The state exists separately from the attempt's {@link BreachOutcome#FAILED} so
 * the player can see <em>which</em> layer ended it, which is §1 constraint 4's comprehensible failure.
 */
public enum LayerOutcome {

    /**
     * Not reached yet.
     *
     * <p>A pending layer's board may legitimately be absent from a snapshot: the target's later layers
     * are not information the player has bought, and disclosing a tier-3 Logic board's alphabet while
     * the player is still on layer 0 would hand them free planning time the design never sold them.
     * See {@link BreachLayer#board()}.
     */
    PENDING,

    /** The layer the player is playing right now. Exactly one layer of a live breach is in this state. */
    ACTIVE,

    /** Solved. The player worked the class out within its attention budget and its strike limit. */
    CLEARED,

    /**
     * Skipped with the Overflow Kit — the layer is behind the player but was never solved. Costs nearly
     * the whole budget and screams while it does it ({@code docs/design/06-intrusion-tools.md} §2:
     * "a panic button with a siren attached").
     */
    BYPASSED,

    /**
     * The strike limit ran out. The layer cannot be cleared, so the attempt cannot succeed; expect the
     * next snapshot to carry a {@link BreachOutcome#FAILED} resolution.
     */
    LOCKED
}
