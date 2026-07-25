package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The five unlock gates. Every item in the game sits behind exactly one of these (Invariant I3).
 *
 * <p>From {@code docs/design/02-unlock-gates.md} §1. Gate assignment follows the decision procedure
 * in §1.1 — ask in order, first "yes" wins — not per-item taste. If an item does not classify
 * cleanly, the item is probably badly designed.
 *
 * <p><strong>Do not add a sixth constant.</strong> {@code docs/design/02} §4 tracks gate count as a
 * known tension (OQ-2): five progression currencies may already be too much cognitive load, and the
 * plan of record if playtests show bloat is to <em>collapse</em> {@link #SCHEMATIC} and {@link
 * #PROOF_OF_SKILL} into one "field research" track — not to expand. Adding a gate is a design
 * decision that has to go through {@code docs/design/15-open-questions.md}, not a code change.
 *
 * <p>This enum lives in {@code protocol} because the client needs it to render what is and is not
 * available. Deciding whether a gate is <em>satisfied</em> is server-side: that check reads
 * authoritative state (schematics found, faction reputation, persisted breach resolution records)
 * and is exactly the kind of thing a cheating client would forge (Invariant I14).
 */
public enum UnlockGate {

    /**
     * Consumables, replaceable tools, horizontal options. Money never raises a ceiling (Invariant
     * I2).
     *
     * <p>Everything behind this gate must be losable and replaceable — that is what makes bot
     * destruction, raid losses and failed hacks economically survivable.
     */
    ETHECOIN,

    /**
     * Permanent capability increases and all rig infrastructure. Found or earned at designer-paced
     * milestones; never sold for ethecoin, never RNG-farmable from repeatable content.
     */
    SCHEMATIC,

    /**
     * Anything that would distort the economy or the trust game if freely purchasable — untraceable
     * transfers, decoy stashes, forged identity.
     */
    REPUTATION,

    /**
     * Automation shortcuts specifically: prove you can do it manually before you skip it.
     *
     * <p><strong>Tier-gated, never count-gated</strong> (Invariant I7). The unlock fires when the
     * player has solved that puzzle class at or above a threshold difficulty <em>against a live or
     * defended target</em> — never "solve it N times", which would just reward farming the weakest
     * available target.
     */
    PROOF_OF_SKILL,

    /**
     * Vendor and contact <em>access</em>. Never ownership.
     *
     * <p>Runs both directions: some fixers are reachable only while cold, and black-market brokers
     * only while hot. Going cold does not confiscate what you bought; going hot does not lock your
     * vault.
     */
    HEAT_STATE
}
