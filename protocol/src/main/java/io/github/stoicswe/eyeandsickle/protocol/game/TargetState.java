package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Whether a breach target was defended at the time of the attempt — the {@code liveOrDormant} field
 * of the breach contract ({@code docs/design/05-hacking-minigame.md} §2).
 *
 * <p>Small enum, large consequence. {@code docs/design/02-unlock-gates.md} §2.4 states as an
 * <em>established</em> rule that an automation tool unlocks when the player has solved that puzzle
 * class at or above a set difficulty <strong>against a live or defended target — not a dormant
 * one</strong>. Without this distinction, proof-of-skill degenerates into farming the softest target
 * available, which is the failure mode the gate was designed to prevent (Invariant I7).
 *
 * <p>Modelled as an enum rather than a {@code boolean live} so the wire form stays self-describing
 * and the field keeps the name the docs use. A boolean would also invite the inverted-flag bug, whose
 * symptom here is silently handing out automation unlocks.
 */
public enum TargetState {

    /** Defended and active. The only state that can earn proof-of-skill credit. */
    LIVE,

    /** Undefended or inactive. Still worth loot; never worth an unlock. */
    DORMANT
}
