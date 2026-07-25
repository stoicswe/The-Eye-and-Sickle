package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How hard a breach layer was, on the game's one shared difficulty scale.
 *
 * <h2>The scale is established; its range is a proposal</h2>
 *
 * That puzzle instances carry a difficulty tier is <em>established</em> and load-bearing:
 * proof-of-skill unlocks ({@code docs/design/02-unlock-gates.md} §2.4, Invariant I7) and the
 * bot-salvage exploit guard ({@code docs/design/10-botnets.md}, Invariant I13) both key off "at or
 * above tier T", and §2.4 requires the scale to be small and legible so a designer can reason about
 * it. One scale, used by both systems, deliberately.
 *
 * <p><strong>[PROPOSAL] — the 1–5 range.</strong> {@code docs/design/05-hacking-minigame.md} §3.3
 * proposes 1–5 ("matching the five heat bands loosely for designer intuition"), and that whole
 * document is tagged {@code [PROPOSAL]}. {@link #LOWEST} and {@link #HIGHEST} encode that proposal
 * and nothing more.
 *
 * <p>Widening the range later is a <strong>wire-compatibility event</strong>, not a tuning change: an
 * older client will reject a tier-6 record it has never heard of, and it will do so at deserialization
 * time, which looks like a corrupt response rather than a version skew. If the range moves, both ends
 * ship together.
 *
 * <h2>What is not here</h2>
 *
 * Any threshold. "Overflow Kit needs Logic at tier ≥ 3" is a balance value: change it and a player
 * gains something, which is the litmus test that sends it to the server. This type only says which
 * tiers <em>exist</em> — the domain of the scale, which both ends must agree on to render or validate
 * a record at all. Comparison is offered because a HUD sorts and orders; it is not an unlock check,
 * and there is nothing here to compare a threshold <em>against</em>.
 *
 * @param tier the tier, between {@link #LOWEST} and {@link #HIGHEST} inclusive
 */
public record DifficultyTier(int tier) implements Comparable<DifficultyTier> {

    /** Tutorial-grade. [PROPOSAL] — see the class documentation. */
    public static final int LOWEST = 1;

    /** Late-game Eye infrastructure. [PROPOSAL] — see the class documentation. */
    public static final int HIGHEST = 5;

    public DifficultyTier {
        if (tier < LOWEST || tier > HIGHEST) {
            throw new IllegalArgumentException(
                    "difficultyTier must be within the " + LOWEST + ".." + HIGHEST + " scale, was " + tier);
        }
    }

    /**
     * A tier on the shared scale.
     *
     * @param tier between {@link #LOWEST} and {@link #HIGHEST} inclusive
     * @return the tier
     * @throws IllegalArgumentException if the value is off the scale
     */
    public static DifficultyTier of(int tier) {
        return new DifficultyTier(tier);
    }

    /** Orders by difficulty, lowest first. */
    @Override
    public int compareTo(DifficultyTier other) {
        return Integer.compare(tier, other.tier);
    }
}
