package io.github.stoicswe.eyeandsickle.server.economy.gate;

/**
 * The five yes/no facts about an offering that {@link GateClassifier} needs to assign its gate.
 *
 * <p>One boolean per question in {@code docs/design/02-unlock-gates.md} §1.1, in the order the
 * decision procedure asks them. Modelled as a record of named booleans rather than five positional
 * arguments so a call site cannot silently transpose "raises a ceiling" and "is a consumable" — a
 * transposition that would misclassify an item and quietly break Invariant I3 (exactly one gate, by
 * rule not taste).
 *
 * <p>These are <em>design facts</em> about an item, decided when the item is designed ({@code
 * docs/design/02-unlock-gates.md} §5's checklist), not player state. Classification is the same for
 * every player; whether a given player <em>satisfies</em> the resulting gate is {@link
 * GateEvaluator}'s separate question.
 *
 * @param raisesPermanentCeiling does it raise a permanent ceiling — compute, vault size, a new
 *     permanent capability? Question 1. If yes it is a schematic (or story milestone; same track), and
 *     this is where Invariant I2 lives: because the ceiling question is asked first, a ceiling can
 *     never fall through to the ethecoin gate.
 * @param automatesOrSkipsPuzzle does it automate or skip a puzzle the player would otherwise solve
 *     manually? Question 2 — the definitional proof-of-skill case (Overflow Kit skips a layer).
 * @param economyDistortingIfFree would it distort the economy or the trust game if anyone could buy it
 *     freely — untraceable transfers, decoy stashes, forged identity? Question 3 — reputation.
 * @param consumableReplaceableOrSidegrade is it a consumable, a replaceable tool, or a sidegrade?
 *     Question 4 — ethecoin. Everything here must be losable and replaceable.
 * @param vendorContactOrMarket is it not an <em>item</em> at all, but a vendor, contact or market
 *     whose <em>reachability</em> is the question? Question 5 — heat state governs access.
 */
public record OfferingFacts(
        boolean raisesPermanentCeiling,
        boolean automatesOrSkipsPuzzle,
        boolean economyDistortingIfFree,
        boolean consumableReplaceableOrSidegrade,
        boolean vendorContactOrMarket) {}
