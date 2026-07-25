package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Where an item lives, and therefore how exposed it is.
 *
 * <p>From {@code docs/design/01-core-resources.md} §6. The tier carries <em>exposure</em> semantics,
 * not just capacity — that trade is the whole point: safety and productivity are mutually exclusive
 * by design.
 *
 * <h2>Bot-socketed items are NOT a fourth tier</h2>
 *
 * Anything assigned to a bot leaves the vault and becomes mid-risk ({@code
 * docs/design/10-botnets.md}), which makes it tempting to add a {@code SOCKETED_IN_BOT} constant
 * here. Don't. The design closes this set at three, and {@code
 * docs/architecture/06-data-model.md} §2 models socketing as a <em>separate</em> nullable {@code
 * socketed_in} reference alongside a nullable {@code storage_tier} — one column says which tier,
 * another says which bot. Adding a fourth constant would quietly invent a different location model
 * than the one the docs propose.
 */
public enum StorageTier {

    /**
     * Small capacity, never exposed.
     *
     * <p>Capacity scales sub-linearly and is never purchasable (Invariant I12) — linear-or-better
     * scaling produces late-game veterans who are functionally unraidable, which kills the risk
     * economy for exactly the players holding the most valuable gear.
     */
    VAULT,

    /** Limited capacity, exposed while the owner is online. */
    STANDARD_STORAGE,

    /** Large capacity, always exposed — raidable even while the owner is offline. */
    HIGH_HACKABLE_ZONE
}
