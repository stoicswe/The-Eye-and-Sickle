package io.github.stoicswe.eyeandsickle.server.identity;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A heat reading — long-horizon attention from The Eye ({@code docs/design/01-core-resources.md} §4),
 * held as stored state.
 *
 * <p>The same type carries both tiers, because they are the same quantity at different scopes:
 * <strong>personal heat</strong> ({@code players.personal_heat}, §4.1) accrues from one player's
 * actions, and <strong>server heat</strong> ({@code server_state.server_heat}, §4.2) is the
 * population-wide reading. Neither is noise (§3): noise is short-horizon and decaying and is not
 * persisted; heat is accumulated standing and is.
 *
 * <h2>Why {@link BigDecimal} and never {@code double}</h2>
 *
 * The schema stores heat as {@code numeric(12,4)}, and heat is compared against thresholds that gate
 * access (vendor reachability, named-hacker status). Binary floating point makes "is this over the
 * threshold" answerable differently on two machines, which on a federated game is indistinguishable
 * from one of them cheating. Decimal keeps the arithmetic exact and the comparison total.
 *
 * <h2>Non-negative by construction</h2>
 *
 * {@code ck_players_heat_non_negative} and {@code ck_server_state_heat_non_negative} both forbid a
 * negative reading, so this type does too: heat can decay to zero (laying low, §4.3) but "negative
 * attention" is not a state. A reduction that would go below zero is a caller bug, and surfacing it
 * here — rather than clamping it silently — keeps it a bug rather than a quietly-wrong balance.
 *
 * <h2>What this type deliberately does not decide (Invariant I9)</h2>
 *
 * Heat gates <em>access</em>, never ownership, and <strong>defending your own rig never generates
 * it</strong> ({@code docs/design/01-core-resources.md} §4.4). This type is a passive amount: it offers
 * {@link #plus(BigDecimal)} for an explicit, attributed adjustment and no automatic accrual of any
 * kind. There is deliberately no "add heat for a defence" path here or anywhere it could be called,
 * because the invariant is that no such path exists — the absence is the enforcement. The magnitudes of
 * the accruals that <em>do</em> apply (a breach, a faction abandonment, decay) are balance values owned
 * by the systems that trigger them, not constants of this type.
 *
 * @param value the reading, in whole-and-fractional heat units; never negative
 */
public record Heat(BigDecimal value) {

    /** A reading of zero — a freshly-created player, or a server with no Sickle activity yet. */
    public static final Heat ZERO = new Heat(BigDecimal.ZERO);

    public Heat {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    "Heat is never negative (Invariant-adjacent: heat decays to zero, it does not invert); was "
                            + value);
        }
    }

    /**
     * Adds an explicit, caller-attributed delta.
     *
     * <p>The delta may be negative to model decay (laying low, §4.3); the result must still be a valid,
     * non-negative reading, so an over-decay throws rather than silently flooring — a decay that
     * overshoots zero is arithmetic the caller got wrong, not a heat of zero.
     *
     * @param delta the change; positive for an accrual, negative for decay
     * @return the new reading
     * @throws IllegalArgumentException if the result would be negative
     */
    public Heat plus(BigDecimal delta) {
        Objects.requireNonNull(delta, "delta");
        return new Heat(value.add(delta));
    }
}
