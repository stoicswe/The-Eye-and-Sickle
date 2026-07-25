package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The two sides, plus not having picked one.
 *
 * <p>From {@code docs/design/01-core-resources.md} §5 and {@code docs/design/00-vision-and-pillars.md}.
 * Reputation eventually forces a <strong>binary commitment</strong>, and abandoning a side resets that
 * reputation, spikes heat temporarily, and forfeits faction-specific tools — so this enum is a
 * two-way choice with a waiting room, not a three-way one.
 *
 * <p>Mirrors the {@code players.faction} enum proposed in {@code docs/architecture/06-data-model.md}
 * §2.
 */
public enum Faction {

    /**
     * The surveillance state. The systemic, automatic pursuer — heat is its attention
     * ({@code docs/design/01-core-resources.md} §4).
     */
    EYE,

    /**
     * The decentralized resistance coalition, which maps onto the federation of self-hosted home
     * servers ({@code docs/architecture/03-server-and-federation.md}) — the fiction and the topology
     * are deliberately the same shape.
     */
    SICKLE,

    /**
     * Uncommitted: a player who has not yet chosen, or one who has just abandoned a side and had that
     * standing reset.
     *
     * <p>Not a third faction. Nothing is gated on being {@code NONE}, and there is no such thing as
     * standing with it — see {@link FactionReputation}.
     */
    NONE
}
