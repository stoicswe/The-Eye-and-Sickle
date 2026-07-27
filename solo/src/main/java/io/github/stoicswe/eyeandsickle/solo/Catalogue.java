package io.github.stoicswe.eyeandsickle.solo;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.util.List;

/**
 * What the market offers, and behind which gate.
 *
 * <h2>SOLO-3, closed for solo play</h2>
 *
 * The market refused everything because offerings are content and nobody had written any — the same
 * gap the server has as <b>W-3</b> ({@code GatedOfferingCatalog} is empty). This is a small, honest
 * first catalogue, priced inside the bands {@code docs/design/03-economy.md} §2 publishes and gated by
 * the rule in {@code docs/design/02-unlock-gates.md} §5.
 *
 * <h2>Every entry obeys the gate-assignment rule, not taste</h2>
 *
 * §5's checklist is: classify the gate, price against {@code 03}, add to the right table. The
 * classification is not a judgement call — {@code 02} assigns it:
 *
 * <ul>
 *   <li><b>Ethecoin</b> — consumables, replacements, horizontal options. Never a ceiling (I2).
 *   <li><b>Schematic</b> — permanent capability. Found or earned, never bought. This is what stops
 *       money from becoming progress, so nothing here is purchasable with ethecoin at any price.
 *   <li><b>Reputation</b> — things that would distort the economy if simply free.
 *   <li><b>Proof-of-skill</b> — automation shortcuts, tier-gated never count-gated (I7).
 *   <li><b>Heat-state</b> — access that swings both ways; some contacts only deal with you cold.
 * </ul>
 *
 * <p><b>Nothing here sells compute or vault capacity at any price</b>, which is Invariants I1 and I12
 * made structural: there is no offering to buy, so there is no code path to review.
 *
 * <h2>Why this lives in `solo` and not in the client</h2>
 *
 * Because it is a rule, not a rendering. When the server's own catalogue lands, this is the shape it
 * should take and the two should be reconciled — a single catalogue serving both is the right end
 * state, and W-3 tracks it.
 */
public final class Catalogue {

    private Catalogue() {}

    /**
     * One thing the market can offer.
     *
     * @param priceMinorUnits the ethecoin price, or 0 when the gate is not ethecoin — a non-zero
     *     price on a schematic-gated item would be exactly the I2 violation the gate exists to stop
     */
    public record Offering(
            String id,
            String name,
            String description,
            UnlockGate gate,
            long priceMinorUnits,
            long equippedCycles,
            String gateRequirement) {

        /** Whether ethecoin alone unlocks this. */
        public boolean purchasable() {
            return gate == UnlockGate.ETHECOIN;
        }
    }

    /**
     * The solo catalogue.
     *
     * <p>Deliberately short. A long list of invented items would be content decisions made in code,
     * which is what {@code CLAUDE.md} asks not to happen — every entry here either already exists in
     * a design document's tool tables or is a plain consumable whose only property is its price.
     */
    public static List<Offering> offerings() {
        return List.of(
                new Offering(
                        "canary-token",
                        "Canary Token",
                        "A file with no purpose but to tell you somebody touched it, and to tag who. "
                                + "The cheapest useful detection in the game, and the only one with "
                                + "essentially no false alarms — nothing legitimate ever opens it.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_CANARY_PRICE,
                        Balance.DEFENSE_CANARY_CYCLES,
                        ""),
                new Offering(
                        "tarpit",
                        "Tarpit",
                        "Does not stop an intruder. Slows every action they take, which buys you the "
                                + "seconds your response actually needs.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_TARPIT_PRICE,
                        Balance.DEFENSE_TARPIT_CYCLES,
                        ""),
                // ── the sweep ladder (docs/design/17) ────────────────────────────────────────────
                //
                // ⚠ Both are ETHECOIN-gated, and that classification is the ordered procedure in
                // docs/design/02-unlock-gates.md §1.1 rather than taste. What they buy is the
                // PROBABILITY of detecting what is already within reach — no new hop, no new field,
                // no new class of node — which is breadth, and §1.1 step 4 puts breadth on ethecoin.
                //
                // ⚠ Neither changes the hop ceiling, at any price. Reach is the Topology Mapper's,
                // and docs/design/07-recon-tools.md §2 makes it schematic-gated precisely because
                // Invariant I2 says ethecoin never buys a ceiling. There is no code path from an
                // offering to NetRules.hopCeiling, and a test enumerates this list to prove it.
                //
                // equippedCycles is 0: a sweep tool holds nothing while idle. Its compute is held for
                // the duration of a sweep and released into recovery when the sweep ends.
                new Offering(
                        "net-sweep-wide",
                        "Net Sweep (Wide)",
                        "A wider sweep of the same distance. Finds quieter machines inside the reach "
                                + "you already have. It does not reach further — reach is not for sale.",
                        UnlockGate.ETHECOIN,
                        Balance.NET_SWEEP_WIDE_PRICE,
                        0,
                        ""),
                new Offering(
                        "net-sweep-deep",
                        "Net Sweep (Deep)",
                        "The most sensitive instrument money buys. Near-certain on infrastructure, and "
                                + "it finally makes quiet desktops reliable. Still one hop.",
                        UnlockGate.ETHECOIN,
                        Balance.NET_SWEEP_DEEP_PRICE,
                        0,
                        ""),
                new Offering(
                        "relay-hop",
                        "Relay hop (one session)",
                        "One more hop in a relay chain. Harder to trace, slower to act — the trade is "
                                + "the point, and it is charged per session rather than owned.",
                        UnlockGate.ETHECOIN,
                        Balance.RELAY_HOP_UPKEEP,
                        0,
                        ""),
                new Offering(
                        "detection-array-t1",
                        "Detection Array T1",
                        "Standing detection. Reserves compute permanently while armed, in exchange for "
                                + "a continuous chance of noticing what routine listings miss.",
                        UnlockGate.SCHEMATIC,
                        0,
                        Balance.DEFENSE_DETECTION_ARRAY_T1_CYCLES,
                        "Requires the Detection Array schematic. Schematics are found or earned, never "
                                + "bought — that is what stops ethecoin from buying a ceiling."),
                new Offering(
                        "honeypot-stash",
                        "Honeypot Stash",
                        "A decoy store of junk that a raider cannot tell from a real one until they "
                                + "have paid to extract from it.",
                        UnlockGate.REPUTATION,
                        0,
                        Balance.DEFENSE_HONEYPOT_STASH_CYCLES,
                        "Requires standing with a faction. Decoy infrastructure would distort the "
                                + "economy if anyone could simply buy it."),
                new Offering(
                        "auto-counter-daemon",
                        "Auto-Counter Daemon",
                        "Fires back on your behalf while you are logged off. In this fiction. See "
                                + "hack-back(7) before you assume that maps onto anything you may do.",
                        UnlockGate.SCHEMATIC,
                        0,
                        Balance.DEFENSE_AUTO_COUNTER_CYCLES,
                        "Requires the schematic, and the heaviest standing compute cost of any defence."));
    }

    /** Looks an offering up by id. */
    public static java.util.Optional<Offering> byId(String id) {
        return offerings().stream().filter(o -> o.id().equals(id)).findFirst();
    }
}
