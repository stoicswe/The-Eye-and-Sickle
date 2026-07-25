package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One concrete, server-side requirement standing between a player and an offering.
 *
 * <h2>Why a condition, and not just an {@link UnlockGate}</h2>
 *
 * {@link UnlockGate} names the <em>kind</em> of gate; it deliberately carries no threshold, because a
 * threshold is a balance value and the protocol module ({@code protocol/game/UnlockGate}) keeps those
 * server-side (Invariant I14). But the server, to answer "is this gate satisfied", needs the actual
 * numbers: how much ethecoin, which schematic, which faction and standing, which puzzle class and
 * tier, which heat threshold and direction. A {@code GateCondition} is a gate <em>plus</em> those
 * parameters — and it exists only here, on the authoritative side, never on the wire as something a
 * client could set to make its own unlock cheap.
 *
 * <h2>The variants are the five gates, one-to-one</h2>
 *
 * Sealed to exactly the five gates of {@code docs/design/02-unlock-gates.md} §1, so a sixth gate is a
 * compile event, not a quiet addition — the same discipline {@link UnlockGate} applies (OQ-2: the plan
 * of record is to <em>collapse</em> gates, never to add a sixth).
 *
 * <p>Each variant reports its {@link #gate()} so a {@link GatedOffering} can be checked against the
 * structural rules of {@code protocol/game/GateRequirement} (I3: exactly one primary; a split's
 * secondary is never {@code SCHEMATIC}). Evaluation against a specific player lives in {@link
 * GateEvaluator}, not here — a condition is data, not a query.
 */
public sealed interface GateCondition {

    /**
     * The gate class this condition belongs to.
     *
     * @return the gate
     */
    UnlockGate gate();

    /**
     * An ethecoin price the player must be able to afford.
     *
     * <p>The ethecoin gate covers consumables, replaceable tools and horizontal options
     * ({@code docs/design/02-unlock-gates.md} §2.1), and everything behind it must be losable and
     * replaceable. It never covers a ceiling — that is Invariant I2, enforced not here but in {@link
     * GateClassifier}, which can never classify a ceiling-raising offering as ethecoin because the
     * ceiling question is asked first (§1.1).
     *
     * <p>Satisfaction is <em>affordability</em>, not payment: whether the player <em>could</em> buy
     * this, read from their materialised balance. The actual spend is a separate, transactional act on
     * the ledger ({@code LedgerService}); reading the balance here does not move it.
     *
     * @param price the amount required; may be {@link Ethecoin#ZERO} for a free offering that is still
     *     ethecoin-classified (a sidegrade a vendor gives away)
     */
    record EthecoinCost(Ethecoin price) implements GateCondition {

        public EthecoinCost {
            Objects.requireNonNull(price, "price");
        }

        @Override
        public UnlockGate gate() {
            return UnlockGate.ETHECOIN;
        }
    }

    /**
     * A schematic the player must hold.
     *
     * <p>The schematic gate covers permanent capability increases and all rig infrastructure
     * ({@code docs/design/02-unlock-gates.md} §2.2) — found or earned at designer-paced milestones,
     * never sold for ethecoin. The schematic identifier is opaque here; the authority on <em>who holds
     * what</em> is the {@link SchematicHoldings} port, owned by the progression slice.
     *
     * @param schematicId the schematic's identifier; must not be blank
     */
    record SchematicRequirement(String schematicId) implements GateCondition {

        public SchematicRequirement {
            Objects.requireNonNull(schematicId, "schematicId");
            if (schematicId.isBlank()) {
                throw new IllegalArgumentException("schematicId must not be blank");
            }
        }

        @Override
        public UnlockGate gate() {
            return UnlockGate.SCHEMATIC;
        }
    }

    /**
     * A minimum standing with a named faction.
     *
     * <p>The reputation gate covers anything economy- or trust-distorting if freely purchasable
     * ({@code docs/design/02-unlock-gates.md} §2.3). Standing may be negative (a player actively
     * hostile to a side), and so may the threshold — a contact that will deal with anyone not openly
     * against them is {@code minimumStanding} of, say, {@code -10}. The comparison is
     * {@code standing >= minimumStanding}.
     *
     * @param faction whose standing is required; never {@link Faction#NONE}, because standing with
     *     nobody is a category error rather than a smaller standing ({@code protocol/game/FactionReputation})
     * @param minimumStanding the least standing that satisfies the gate; may be negative
     */
    record ReputationRequirement(Faction faction, long minimumStanding) implements GateCondition {

        public ReputationRequirement {
            Objects.requireNonNull(faction, "faction");
            if (faction == Faction.NONE) {
                throw new IllegalArgumentException(
                        "A reputation gate names a faction to have standing with; Faction.NONE is the "
                                + "absence of a standing, not a threshold against one");
            }
        }

        @Override
        public UnlockGate gate() {
            return UnlockGate.REPUTATION;
        }
    }

    /**
     * A demonstrated competence: this puzzle class solved at or above a tier, against a live target.
     *
     * <p>The proof-of-skill gate covers automation shortcuts specifically ({@code
     * docs/design/02-unlock-gates.md} §2.4): prove you can do it manually before you skip it.
     * <strong>Tier-gated, never count-gated</strong> (Invariant I7) — the requirement is the single
     * tier, and {@link GateEvaluator} answers it with the player's highest live-and-breached tier in
     * this class, never a count. Storing a tier here rather than a count is what makes the anti-farming
     * rule inexpressible to bypass.
     *
     * @param puzzleClass the class that must have been solved
     * @param minimumTier the least difficulty that counts, on the shared 1..5 scale; a dormant-target
     *     or failed attempt at this tier does not satisfy it — only a live breach does
     */
    record ProofOfSkillRequirement(PuzzleClass puzzleClass, DifficultyTier minimumTier) implements GateCondition {

        public ProofOfSkillRequirement {
            Objects.requireNonNull(puzzleClass, "puzzleClass");
            Objects.requireNonNull(minimumTier, "minimumTier");
        }

        @Override
        public UnlockGate gate() {
            return UnlockGate.PROOF_OF_SKILL;
        }
    }

    /**
     * A heat-state requirement: reachable only while cold, or only while hot.
     *
     * <p>The heat-state gate governs vendor and contact <em>access</em> ({@code
     * docs/design/02-unlock-gates.md} §2.5), and it is the one gate that runs both directions — see
     * {@link HeatDirection}. The threshold is compared against the player's <em>personal</em> heat
     * ({@code docs/design/01-core-resources.md} §4.1), the tier that "changes vendor availability".
     * Held as a {@link BigDecimal} because personal heat is a {@code numeric} in the schema, and a
     * float comparison against a threshold is the kind of rounding disagreement Invariant I14 cannot
     * tolerate.
     *
     * @param direction whether the contact is reachable while cold or while hot
     * @param threshold the heat level at the boundary; never negative, because heat never is
     */
    record HeatStateRequirement(HeatDirection direction, BigDecimal threshold) implements GateCondition {

        public HeatStateRequirement {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(threshold, "threshold");
            if (threshold.signum() < 0) {
                throw new IllegalArgumentException("A heat threshold is never negative, was " + threshold);
            }
        }

        @Override
        public UnlockGate gate() {
            return UnlockGate.HEAT_STATE;
        }
    }
}
