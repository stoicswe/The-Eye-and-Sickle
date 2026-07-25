package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * A player's standing with one faction ({@code docs/design/01-core-resources.md} §5).
 *
 * <h2>⚠ This is not validator reputation. It is never validator reputation.</h2>
 *
 * The glossary flags "reputation" as a word with two unrelated meanings in this project, and this is
 * the trap it is warning about:
 *
 * <ul>
 *   <li><strong>{@code factionReputation}</strong> — <em>this type</em>. A player's Eye/Sickle
 *       standing. Moved by in-fiction choices; gates economy-distorting items ({@code
 *       docs/design/02-unlock-gates.md} §2.3); reset when a player abandons a side.
 *   <li><strong>{@code validatorReputation}</strong> — a federated <em>server's</em> trust score,
 *       used to weight quorum votes on cross-server duel outcomes ({@code
 *       docs/architecture/05-validator-quorum.md}). Different subject, different lifetime, different
 *       consequences: slashing a validator is an anti-cheat action, losing Sickle standing is a story
 *       beat.
 * </ul>
 *
 * They must never share a field, a column, or a type. {@code ArchitectureRulesTest} forbids a class
 * simply named {@code Reputation} in this module for exactly that reason — a generic type is the
 * shape the merge would arrive in, so the shape is banned outright rather than reviewed for.
 *
 * <h2>What is deliberately absent</h2>
 *
 * No thresholds and no tier names. {@code docs/design/02-unlock-gates.md} §5 asks a designer to "name
 * the faction and the standing tier" for every reputation-gated item, but the docs never enumerate
 * those tiers, and a threshold is a balance value by definition — classifying a standing is the
 * server's job. The client is told what it may see; it does not work it out.
 *
 * <p><strong>[PROPOSAL] — the scale.</strong> {@code docs/architecture/06-data-model.md} types
 * {@code faction_reputation} as {@code numeric}, and no doc says whether standing can go
 * <em>negative</em> (actively hostile) or only fall to zero. This type takes an integral point score
 * and permits negatives rather than inventing a floor: a wire type that rejects a value the design
 * later wants is a worse failure than one that carries a value the server never sends. Both the
 * integrality and the sign question need a design ruling.
 *
 * @param faction whose standing this is; never {@link Faction#NONE}
 * @param standing the point score, server-supplied
 */
public record FactionReputation(Faction faction, long standing) {

    public FactionReputation {
        Objects.requireNonNull(faction, "faction");
        // Standing with nobody is not a smaller standing, it is a category error — and a player who
        // has abandoned a side has had that standing reset, which is a value of zero against a named
        // faction, not a standing against NONE.
        if (faction == Faction.NONE) {
            throw new IllegalArgumentException(
                    "Reputation is standing with a named faction; Faction.NONE means uncommitted, "
                            + "which is the absence of a standing rather than a standing of its own");
        }
    }
}
