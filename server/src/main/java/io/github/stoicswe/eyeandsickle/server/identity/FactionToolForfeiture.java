package io.github.stoicswe.eyeandsickle.server.identity;

import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.util.UUID;

/**
 * The seam by which abandoning a faction forfeits that faction's tools
 * ({@code docs/design/01-core-resources.md} §5).
 *
 * <h2>Why this is an interface owned here, and not a reach into the item system</h2>
 *
 * Faction abandonment is an identity-slice state transition, but "forfeits faction-specific tools" acts
 * on items — a table the item/inventory system owns, not this one. Rather than reach across the
 * boundary, the identity slice declares the narrow effect it needs and lets the owning system implement
 * it. {@link FactionService} calls this as part of the transition; what "forfeit" concretely does
 * (unequip, seize, mark unusable) is the item system's decision, made where that data lives.
 *
 * <h2>[PROPOSAL] — nothing implements the effect yet</h2>
 *
 * The only implementation in this slice is {@link NoOpFactionToolForfeiture}, which does nothing. The
 * mechanics of forfeiture are not specified by the Established docs — how faction-specific tools are
 * identified, and whether forfeiture destroys them or merely locks them, is undecided — so this slice
 * defines the trigger and leaves the effect to whoever owns items. Logged for the integrator in the
 * undecided list.
 */
public interface FactionToolForfeiture {

    /**
     * Forfeits the tools tied to a faction the player is abandoning.
     *
     * <p>Called inside the abandonment transaction, so an implementation's writes commit atomically with
     * the faction reset and heat spike, or all of them roll back together. An implementation must be
     * safe to call when the player owns no such tools — abandonment is common and usually forfeits
     * nothing.
     *
     * @param playerId the abandoning player
     * @param abandonedFaction the side being left; its tools are the ones forfeited
     */
    void forfeitFactionTools(UUID playerId, Faction abandonedFaction);
}
