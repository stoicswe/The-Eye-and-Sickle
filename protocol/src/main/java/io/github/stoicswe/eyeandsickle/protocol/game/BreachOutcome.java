package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How a breach attempt ended.
 *
 * <p>From the economy-facing contract in {@code docs/design/05-hacking-minigame.md} §2. That doc is
 * tagged {@code [PROPOSAL]} as a whole, but §2 is written to be the part that lasts: whatever the
 * puzzle turns out to <em>be</em>, this is the vocabulary the economy consumes. Three outcomes, closed
 * set — a fourth would mean a new consequence path through {@code docs/design/02-unlock-gates.md}
 * §2.4 and {@code docs/design/10-botnets.md}, which is a design decision rather than a code change.
 */
public enum BreachOutcome {

    /**
     * The player cleared or bypassed every layer before the trace completed. Loot on success, and the
     * only outcome that can earn proof-of-skill credit ({@code docs/design/05-hacking-minigame.md}
     * §4).
     */
    BREACHED,

    /**
     * The trace completed first. Consequence scales with the target — a cracked miner's dead-man
     * switch, or tool loss, heat and counter-attack on an offensive breach.
     *
     * <p>{@code docs/design/05-hacking-minigame.md} §1 constraint 4 makes this outcome's legibility
     * load-bearing: a loss has to read as "I was too loud or too slow", never as "the game decided".
     */
    FAILED,

    /**
     * The player walked away mid-attempt. No loot, no proof-of-skill credit, and the noise already
     * generated stays generated — the escape hatch when a read goes bad.
     */
    ABORTED
}
