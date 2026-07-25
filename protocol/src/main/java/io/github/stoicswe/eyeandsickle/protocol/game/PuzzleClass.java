package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The kind of puzzle a target node presents in the core hacking minigame.
 *
 * <h2>⚠ [PROPOSAL] — the constants, not the concept</h2>
 *
 * That puzzle instances <strong>must</strong> carry a class and a difficulty tier is
 * <em>established</em>: proof-of-skill unlocks and bot-salvage guards both key off "solved class X at
 * tier ≥ T against a live or defended target" ({@code docs/design/02-unlock-gates.md} §2.4,
 * Invariants I7 and I13). So this enum has to exist.
 *
 * <p>Which five classes, however, is a first-pass proposal ({@code
 * docs/design/05-hacking-minigame.md} §3.1) — the whole minigame doc is tagged {@code [PROPOSAL]},
 * and §6 keeps open whether five classes is the right breadth or whether it dilutes mastery (P-1).
 * Expect these constants to change. Do not build anything that depends on there being exactly five,
 * and keep the economy-facing contract (§2 of that doc) as the stable surface instead.
 *
 * <p>The design intent behind the split: classes should feel like <em>different kinds of
 * thinking</em>, not reskins. If two of them reduce to the same optimal input pattern, merge them.
 */
public enum PuzzleClass {

    /** Map a node's open ports and services before you can act — reading structure under time pressure. */
    ENUMERATION,

    /** Defeat an auth layer — pattern deduction and reuse detection. */
    CREDENTIAL,

    /** Reconstruct a lock's rule from probe responses — deductive, Mastermind-family reasoning. */
    LOGIC,

    /** Exploit a race or timing window — sequencing, rhythm, patience. */
    TIMING,

    /** Route through an internal graph to the data node — pathfinding under a noise budget. */
    TRAVERSAL
}
