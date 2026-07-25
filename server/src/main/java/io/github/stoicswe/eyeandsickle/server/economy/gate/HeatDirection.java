package io.github.stoicswe.eyeandsickle.server.economy.gate;

/**
 * Which way a heat-state gate runs — the direction half of {@code docs/design/02-unlock-gates.md}
 * §2.5.
 *
 * <p>The heat-state gate is the only gate that runs <strong>both directions</strong>, and that is the
 * whole reason it needs a type rather than a single threshold. Being clean and being hunted each open
 * doors the other closes, so "reachable at this heat" cannot be expressed as "heat above X" alone —
 * you also have to say which side of X is the reachable one.
 *
 * <p>Heat state gates <em>access</em>, never ownership ({@code docs/design/01-core-resources.md} §4.4):
 * going cold does not confiscate a black-market purchase, and going hot does not lock the vault. This
 * enum decides reachability and nothing else.
 */
public enum HeatDirection {

    /**
     * Reachable only while <em>cold</em>: heat at or below the threshold.
     *
     * <p>Respectable fixers do not meet wanted people ({@code docs/design/02-unlock-gates.md} §2.5).
     * Heat rising past the threshold closes the door.
     */
    COLD_GATED,

    /**
     * Reachable only while <em>hot</em>: heat at or above the threshold.
     *
     * <p>Black-market brokers are reachable only while hunted — the sole sanctioned route to zero-days
     * ({@code docs/design/02-unlock-gates.md} §2.5), which means the power is bought with exposure.
     * Heat is the key, not the lock.
     */
    HOT_GATED
}
