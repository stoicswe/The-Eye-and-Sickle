package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.util.Objects;

/**
 * The gate-assignment decision procedure of {@code docs/design/02-unlock-gates.md} §1.1, made
 * executable.
 *
 * <h2>What this is for</h2>
 *
 * Invariant I3: every item sits behind <strong>exactly one</strong> gate, assigned by a rule and not
 * by taste. The rule is five questions asked in order, first "yes" wins. Writing it as prose in a doc
 * lets it erode under the reasonable-sounding pressure to gate "just this one item" the convenient
 * way; writing it as a classifier makes the assignment reproducible and testable, and makes a
 * misclassification a failing test rather than an economy that quietly stops being legible.
 *
 * <h2>Why the ordering is load-bearing, not cosmetic</h2>
 *
 * The order is how two invariants are enforced for free:
 *
 * <ul>
 *   <li><strong>Invariant I2</strong> (ethecoin never buys a ceiling): the ceiling question is asked
 *       <em>first</em>, so a ceiling-raising offering is classified {@link UnlockGate#SCHEMATIC}
 *       before the ethecoin question is ever reached. There is no code path that returns
 *       {@code ETHECOIN} for a ceiling — not by policy, by control flow.
 *   <li><strong>Invariant I3</strong> (exactly one gate): "first yes wins" returns exactly one gate
 *       for any offering that classifies at all.
 * </ul>
 *
 * <h2>An offering that classifies as nothing is a design error, surfaced</h2>
 *
 * §1.1: "if it doesn't classify cleanly, the item is probably badly designed." When all five facts are
 * false the classifier throws rather than inventing a default, because a silent default is precisely
 * how a badly-designed item slips into the game gated by whatever the reading code happened to check
 * first.
 *
 * <p>Splits ({@code docs/design/02-unlock-gates.md} §1.1 — Relay Chain, Rainbow Table, Cold Storage
 * Expansion) are <em>not</em> this classifier's job: it assigns the single primary gate, and the
 * secondary cost is attached where the offering is defined, always with the ceiling component on the
 * non-ethecoin side ({@code protocol/game/GateRequirement} enforces that structurally).
 */
public final class GateClassifier {

    /**
     * Assigns the one primary gate an offering sits behind.
     *
     * @param facts the five design facts, in §1.1's terms
     * @return the primary gate; the first question that answers "yes"
     * @throws IllegalArgumentException if no question answers "yes" — the offering does not classify
     *     cleanly, which §1.1 reads as a design smell rather than a case to paper over with a default
     */
    public UnlockGate classify(OfferingFacts facts) {
        Objects.requireNonNull(facts, "facts");

        // Ask in order; first "yes" wins (docs/design/02-unlock-gates.md §1.1).
        if (facts.raisesPermanentCeiling()) {
            return UnlockGate.SCHEMATIC;
        }
        if (facts.automatesOrSkipsPuzzle()) {
            return UnlockGate.PROOF_OF_SKILL;
        }
        if (facts.economyDistortingIfFree()) {
            return UnlockGate.REPUTATION;
        }
        if (facts.consumableReplaceableOrSidegrade()) {
            return UnlockGate.ETHECOIN;
        }
        if (facts.vendorContactOrMarket()) {
            return UnlockGate.HEAT_STATE;
        }
        throw new IllegalArgumentException(
                "This offering answers 'no' to all five gate questions (docs/design/02-unlock-gates.md "
                        + "§1.1), so it does not classify cleanly. Per §1.1 that is the signal the item is "
                        + "badly designed — reconsider the item, do not force a gate onto it.");
    }
}
