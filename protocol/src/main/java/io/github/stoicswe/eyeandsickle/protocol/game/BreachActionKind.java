package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What kind of move a breach action is — the five rows of {@code docs/design/05-hacking-minigame.md}
 * §4's cost table, with the costs deliberately left behind.
 *
 * <p>§4 decided (2026-07-26) that a breach is turn-based and that there is no wall clock anywhere in
 * it. Attention became the only currency the puzzle spends, and per-action cost became "the whole
 * mechanic": it is what makes the loud-versus-patient trade real, and what satisfies §1 constraint 5's
 * requirement that noise scale with <em>how</em> a player solved a layer rather than merely that they
 * did.
 *
 * <h2>Why there is no {@code cost()} accessor here</h2>
 *
 * §4 prints a number beside each of these rows — 1, 2, 6, most of the bar, 0 — and putting them on the
 * enum would be a one-line change that reads like documentation. It is not. Every one of those numbers
 * changes what a player gains, which is the litmus test {@code package-info} sets for this module: a
 * constant that moves an outcome is a balance value and belongs to the authoritative side.
 *
 * <p>The deeper reason is that the cost is not a property of the <em>kind</em> at all. It is a property
 * of the kind, the layer, the target's defence profile and the player's loadout together — a Tarpit
 * ({@code docs/design/09-defense-and-hardening.md} §1) surcharges every action, a Firewall tier does
 * not, and the Rainbow Table's price collapses to nothing against a salted lock ({@code
 * docs/design/06-intrusion-tools.md} §2). An enum accessor could only ever encode the base case, and
 * the first tuning pass would turn it into a lie the client keeps rendering with confidence.
 *
 * <p>So the engine computes the price for <em>this</em> move against <em>this</em> layer and transmits
 * it: {@link BreachAction#attentionCost()}.
 *
 * <h2>What the kind is for, then</h2>
 *
 * Two things, both presentational. It lets an action be named and coloured by its <em>character</em>
 * before its number is read — "loud" and "free" are the decisions the player is actually making, and
 * the number only prices them. And it lets the itemised ledger §4 requires ("the player must always be
 * able to see which action cost what") group a run of quiet reads without re-deriving anything from
 * the action ids.
 */
public enum BreachActionKind {

    /**
     * Quiet read, passive observation — §4's patient baseline. Sweeping a band for a count, listening
     * on an adjacent node, drawing a fact: information bought without commitment.
     */
    QUIET_READ,

    /** The default move. One probe, one answer, the middle of §4's cost table. */
    PROBE,

    /**
     * A Fuzzer volley, a brute attempt, a traceroute — power bought with exposure. §4 prices these at
     * three times an ordinary probe, and {@code docs/design/06-intrusion-tools.md} §5 is explicit about
     * what the extra buys: noise gates stealth. A loud tool is not merely expensive; it is the reason a
     * failure afterwards reads as "I was too loud" (§1 constraint 4) instead of as bad luck.
     */
    LOUD_TOOL,

    /**
     * The Overflow Kit. It clears a layer outright ({@code docs/design/06-intrusion-tools.md} §1) and
     * spends nearly the whole attention budget doing it — §4: "the cost is the point."
     *
     * <p>Separate from {@link #LOUD_TOOL} because it is the one move that <em>skips</em> the puzzle
     * rather than playing it, which is precisely why it is the definitional proof-of-skill item
     * ({@code docs/design/02-unlock-gates.md} §2.4): you must have cleared that class manually before
     * you may buy your way past it. Pillar 1 is "the puzzle is the game"; this is the sanctioned
     * exception, and it should never be cheap enough to become a default.
     */
    BYPASS,

    /**
     * The Side-Channel Reader: infers a node's contents without entering it. Under §4 it is the only
     * action in the entire game costing <strong>zero</strong> attention, and {@code
     * docs/design/06-intrusion-tools.md} §2 calls that its whole identity — everything else you do to a
     * node spends from the bar, and this does not.
     *
     * <p>It is not free, it is paid for somewhere the breach cannot see: 14 standing compute and a late
     * schematic gate. That is what stops it being a universal scanner.
     */
    SIDE_CHANNEL
}
