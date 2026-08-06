package io.github.stoicswe.eyeandsickle.engine;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeKind;
import java.math.BigInteger;
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
     * @param priceWei the ethecoin price, or 0 when the gate is not ethecoin — a non-zero
     *     price on a schematic-gated item would be exactly the I2 violation the gate exists to stop
     */
    public record Offering(
            String id,
            String name,
            String description,
            UnlockGate gate,
            BigInteger priceWei,
            long equippedCycles,
            String gateRequirement,
            UpgradeKind kind,
            String requiresSchematic,
            String stopsTool,
            Durability durability,
            java.util.List<String> tags) {

        /**
         * What kind of thing this is — {@code defence}, {@code recon}, {@code stealth}, {@code mining}.
         *
         * <h2>⚠ The FIRST tag, by convention, and the convention is now enforced</h2>
         *
         * Every offering's tag list already opens with the word a player would file it under and
         * continues with search terms ({@code "defence", "detection", "tripwire", "cheap"}). Reading
         * the category off tag zero rather than adding a parallel field is what stops the two
         * disagreeing — a separate {@code category} would be a second answer to a question the tags
         * already settle, and the day somebody edited one and not the other the shelf and the search
         * would file the same item in two places.
         *
         * <p>⚠ {@code CatalogueTest} holds that every offering has one, because an empty list here
         * would silently file an item under "other" and it would be findable only by scrolling.
         *
         * @return the category, or {@code other} when an offering carries no tags at all
         */
        public String category() {
            return tags.isEmpty() ? "other" : tags.getFirst();
        }

        /**
         * An ordinary software offering — the shape every entry had before firmware existed.
         *
         * <p>⚠ Defaults to {@link Durability#PERMANENT}, which is the CAUTIOUS direction: permanent
         * carries the shallower discount band, so an entry added without thinking about durability
         * gets the smaller sale rather than the larger one. A default of consumable would put a new
         * tool on the deepest discount in the game by omission.
         */
        public Offering(
                String id,
                String name,
                String description,
                UnlockGate gate,
                BigInteger priceWei,
                long equippedCycles,
                String gateRequirement) {
            this(
                    id,
                    name,
                    description,
                    gate,
                    priceWei,
                    equippedCycles,
                    gateRequirement,
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.PERMANENT,
                    java.util.List.of());
        }

        /** An ordinary offering with search tags. */
        public Offering(
                String id,
                String name,
                String description,
                UnlockGate gate,
                BigInteger priceWei,
                long equippedCycles,
                String gateRequirement,
                java.util.List<String> tags) {
            this(
                    id,
                    name,
                    description,
                    gate,
                    priceWei,
                    equippedCycles,
                    gateRequirement,
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.PERMANENT,
                    tags);
        }

        /** A consumable — spent, or bought again for the next use. See {@link Durability}. */
        public static Offering consumable(
                String id,
                String name,
                String description,
                BigInteger priceWei,
                long equippedCycles,
                java.util.List<String> tags) {
            return new Offering(
                    id,
                    name,
                    description,
                    UnlockGate.ETHECOIN,
                    priceWei,
                    equippedCycles,
                    "",
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.CONSUMABLE,
                    tags);
        }

        /**
         * Whether this offering answers a search.
         *
         * <p>⚠ Matches the NAME, the DESCRIPTION and the TAGS. Tags alone would miss a player typing
         * a word straight off the card they are looking at, and name alone would make the tags
         * decorative — the point of a tag is to find something whose name you do not know.
         *
         * @param query lower-cased, already trimmed
         * @return whether it matches
         */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String needle = query.toLowerCase(java.util.Locale.ROOT);
            return name.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || description.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || tags.stream().anyMatch(tag -> tag.contains(needle));
        }

        public Offering {
            kind = kind == null ? UpgradeKind.SOFTWARE : kind;
            durability = durability == null ? Durability.PERMANENT : durability;
            // ⚠ Lower-cased at construction, so search never has to. A tag that differed only in case
            // would be a second tag nobody could tell from the first — the shelf would show two
            // "Defence" filters returning different sets.
            tags = tags == null
                    ? java.util.List.of()
                    : tags.stream()
                            .map(tag -> tag.toLowerCase(java.util.Locale.ROOT).trim())
                            .filter(tag -> !tag.isBlank())
                            .distinct()
                            .toList();
            requiresSchematic = requiresSchematic == null ? "" : requiresSchematic;
            stopsTool = stopsTool == null ? "" : stopsTool;
            // ⚠ Firmware without a schematic would be a permanent capability reachable with money
            // alone, which is Invariant I2 and docs/design/11 §4 rule 1 ("It MUST be schematic/story-
            // gated. No EC path. No exceptions."). Enforced rather than documented, because the
            // tempting edit is exactly to add a firmware entry and leave this blank.
            if (kind == UpgradeKind.FIRMWARE && requiresSchematic.isBlank()) {
                throw new IllegalArgumentException("firmware must name the schematic that authorises it: " + id);
            }
        }

        /** Whether ethecoin alone unlocks this. */
        public boolean purchasable() {
            return gate == UnlockGate.ETHECOIN;
        }

        /** Whether this is firmware, with everything that implies — see {@link UpgradeKind}. */
        public boolean firmware() {
            return kind == UpgradeKind.FIRMWARE;
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
                // ── the compute ladder (docs/design/01 §1.1) ─────────────────────────────────────
                //
                // ⚠ THE FIRST RUNG IS THE ONE PLACE IN THIS GAME WHERE ETHECOIN BUYS COMPUTE, AND
                // THAT IS INVARIANT I1 AMENDED ON EXPLICIT DIRECTION (2026-08-06, design/15 §3).
                //
                // I1 exists because mining that buys mining capacity is a compounding flywheel. One
                // rung cannot compound: the step above 32 is not for sale at any price, so money
                // moves a player up ONCE, ever. `ComputeLadderTest.onlyTheFirstRungIsForSale` fails
                // the build if a second rung acquires a price — if that ever happens, I1 has been
                // abandoned rather than amended, and it should be a red build rather than a
                // conversation nobody had.
                //
                // ⚠ SOFTWARE, not FIRMWARE, and the distinction is doing real work. Offering's
                // compact constructor REFUSES firmware with no schematic named — that guard is what
                // stops firmware becoming money-reachable, and marking this one firmware would have
                // forced a schematic onto the rung that is meant to be bought. So the shape says
                // what it is: the first rung is a PRODUCT, and the two above it are things you
                // COMPILE.
                new Offering(
                        "compute-32",
                        "Capacity Board — 32C",
                        "A daughter board and the firmware to drive it. Takes this rig from 24 cycles "
                                + "to 32, once. There is no second one, and nothing above it is sold.",
                        UnlockGate.ETHECOIN,
                        Balance.COMPUTE_32_PRICE,
                        0,
                        "",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "expensive")),
                // ⚠ NO PRICE, EVER. See Balance.COMPUTE_32_PRICE for why exactly one rung has one.
                new Offering(
                        "compute-48",
                        "Capacity Lattice — 48C",
                        "Compiled, not bought. Needs the lattice schematic and the materials to build "
                                + "it, and it will not go on a rig that has not already taken 32.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Requires the 48C lattice schematic and rare materials, assembled in the "
                                + "compiler. Capacity above the first rung is never sold.",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "schematic")),
                new Offering(
                        "compute-64",
                        "Capacity Lattice — 64C",
                        "The top of the ladder. A rarer schematic, rarer materials, and every rung "
                                + "below it already in place.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Requires the 64C lattice schematic and rare materials, assembled in the "
                                + "compiler. Capacity above the first rung is never sold.",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "schematic")),
                // ── the firewall ladder (docs/design/09 §1) ──────────────────────────────────────
                //
                // ⚠ ALL THREE ARE ETHECOIN, INCLUDING THE TOP ONE, and that is `09` §2's own
                // classification rather than a relaxation of I2: a firewall is horizontal protection
                // and "the escalating compute cost (5/9/15 while armed) is the real limiter". T3 is
                // what `03` §2 calls a TOP PURCHASABLE — money reaches the highest rung of a ladder,
                // never a rung above the ladder.
                //
                // ⚠ T1 IS THE ONE DEFENCE A NEW CHARACTER ALREADY OWNS. `GameEngine.newCharacter`
                // grants it, so a fresh rig is not defenceless and the FIREWALL panel is not a screen
                // of ten refusals. It is still a catalogue entry with a price, because it is
                // losable, sellable and re-buyable like everything else on the ethecoin gate (`02`
                // §2.1) — being granted is a starting position, not an exemption from the rules.
                new Offering(
                        "firewall-t1",
                        "Firewall T1",
                        "A flat difficulty increase on anything trying to breach this rig. The cheapest "
                                + "standing defence there is, and the one every rig starts with.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T1_PRICE,
                        Balance.DEFENSE_FIREWALL_T1_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing", "starter")),
                new Offering(
                        "firewall-t2",
                        "Firewall T2",
                        "Twice the standing cost of a T1 for a larger flat difficulty add. The cycles "
                                + "are the price you actually pay — they are gone for as long as it is armed.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T2_PRICE,
                        Balance.DEFENSE_FIREWALL_T2_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing")),
                new Offering(
                        "firewall-t3",
                        "Firewall T3",
                        "The hardest wall money buys. Fifteen permanent cycles is fifteen you are not "
                                + "mining or attacking with, which is the whole of the decision.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T3_PRICE,
                        Balance.DEFENSE_FIREWALL_T3_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing", "top-purchasable")),
                // ⚠ CONSUMABLE: a canary is planted and spent. It is bought repeatedly, which is
                // what makes it the right side of a sale — a discount changes a decision a player
                // makes often and never accumulates into a capability.
                Offering.consumable(
                        "canary-token",
                        "Canary Token",
                        "A file with no purpose but to tell you somebody touched it, and to tag who. "
                                + "The cheapest useful detection in the game, and the only one with "
                                + "essentially no false alarms — nothing legitimate ever opens it.",
                        Balance.DEFENSE_CANARY_PRICE,
                        Balance.DEFENSE_CANARY_CYCLES,
                        java.util.List.of("defence", "detection", "tripwire", "cheap", "starter")),
                new Offering(
                        "tarpit",
                        "Tarpit",
                        "Does not stop an intruder. Slows every action they take, which buys you the "
                                + "seconds your response actually needs.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_TARPIT_PRICE,
                        Balance.DEFENSE_TARPIT_CYCLES,
                        "",
                        java.util.List.of("defence", "delay", "intruder", "response")),
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
                        "",
                        java.util.List.of("recon", "sweep", "discovery", "network", "scanning")),
                new Offering(
                        "net-sweep-deep",
                        "Net Sweep (Deep)",
                        "The most sensitive instrument money buys. Near-certain on infrastructure, and "
                                + "it finally makes quiet desktops reliable. Still one hop.",
                        UnlockGate.ETHECOIN,
                        Balance.NET_SWEEP_DEEP_PRICE,
                        0,
                        "",
                        java.util.List.of("recon", "sweep", "discovery", "network", "scanning", "sensitive")),
                // ⚠ CONSUMABLE, and the offering's own text says why: "charged per session rather
                // than owned". This is the one entry where the classification is not a judgement.
                Offering.consumable(
                        "relay-hop",
                        "Relay hop (one session)",
                        "One more hop in a relay chain. Harder to trace, slower to act — the trade is "
                                + "the point, and it is charged per session rather than owned.",
                        Balance.RELAY_HOP_UPKEEP,
                        0,
                        java.util.List.of("stealth", "relay", "anonymity", "per-session", "cheap")),
                // ── the detection-array ladder (docs/design/09 §1, AMENDED 2026-08-06) ───────────
                //
                // ⚠ T1 AND T2 MOVED FROM THE SCHEMATIC GATE TO ETHECOIN; T3 DID NOT, AND THE SPLIT
                // IS WHAT KEEPS I2 TRUE. The rule, on explicit direction and logged in
                // `docs/design/15` §3: low-level base tools and low-level upgrades are purchasable
                // and cost more than a consumable; high-level and rare items need a schematic.
                //
                // ⚠ It is the firewall's own shape, one item along — a ladder whose top rung is out
                // of the market's reach. What the Array sells is PRECISION (`09` §2: it "improves
                // the quality of the signal rather than the chance of a hit", cutting the
                // false-positive rate), and the ladder's ceiling is the tier that money cannot get
                // to. Give T3 a price and ethecoin has bought a permanent capability, with the shop
                // still rendering correctly and the purchase still working.
                //
                // ⚠ I3 is untouched: these are three ITEMS, not one item with two gates. Each sits
                // behind exactly one.
                new Offering(
                        "detection-array-t1",
                        "Detection Array T1",
                        "Standing detection. Reserves compute permanently while armed, in exchange for "
                                + "a scan on this rig lying to you less often than a scan on a bare one.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_DETECTION_ARRAY_T1_PRICE,
                        Balance.DEFENSE_DETECTION_ARRAY_T1_CYCLES,
                        "",
                        java.util.List.of("defence", "detection", "standing", "precision")),
                new Offering(
                        "detection-array-t2",
                        "Detection Array T2",
                        "Better instrumentation, at more than twice the standing cost. You are paying, "
                                + "continuously, not to be sent chasing ghosts.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_DETECTION_ARRAY_T2_PRICE,
                        Balance.DEFENSE_DETECTION_ARRAY_T2_CYCLES,
                        "",
                        java.util.List.of("defence", "detection", "standing", "precision")),
                // ⚠ NO PRICE, AND IT MUST STAY THAT WAY. See Balance.DEFENSE_DETECTION_ARRAY_T1_PRICE.
                new Offering(
                        "detection-array-t3",
                        "Detection Array T3",
                        "The best instrumentation there is, and twenty-five permanent cycles for it. "
                                + "Compiled from a schematic, never sold.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        Balance.DEFENSE_DETECTION_ARRAY_T3_CYCLES,
                        "Requires the Detection Array T3 schematic. The top of a ladder is never for "
                                + "sale — that is what stops ethecoin from buying a ceiling.",
                        java.util.List.of("defence", "detection", "standing", "schematic")),
                // ⚠ NOT PURCHASABLE, AND THE ZERO PRICE IS LOAD-BEARING. See Catalogue.TOR_MODULE.
                // It arrives in the COMS inbox from rules/BlackMarket and by no other route.
                new Offering(
                        TOR_MODULE,
                        "TOR Module",
                        "An onion router. It does not hide you from anybody — what it does is reach "
                                + "addresses ordinary lookups refuse to resolve, which is the only way "
                                + "to see the Marknet board at all.",
                        UnlockGate.HEAT_STATE,
                        BigInteger.ZERO,
                        0,
                        "Sent to you, once the people who run the Marknet decide you are worth "
                                + "talking to. It takes standing with a faction and enough heat to be "
                                + "worth approaching. It is not for sale.",
                        java.util.List.of("access", "darknet", "routing", "marknet")),
                new Offering(
                        "honeypot-stash",
                        "Honeypot Stash",
                        "A decoy store of junk that a raider cannot tell from a real one until they "
                                + "have paid to extract from it.",
                        UnlockGate.REPUTATION,
                        BigInteger.ZERO,
                        Balance.DEFENSE_HONEYPOT_STASH_CYCLES,
                        "Requires standing with a faction. Decoy infrastructure would distort the "
                                + "economy if anyone could simply buy it.",
                        java.util.List.of("defence", "decoy", "deception", "reputation")),
                new Offering(
                        "auto-counter-daemon",
                        "Auto-Counter Daemon",
                        "Fires back on your behalf while you are logged off. In this fiction. See "
                                + "hack-back(7) before you assume that maps onto anything you may do.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        Balance.DEFENSE_AUTO_COUNTER_CYCLES,
                        "Requires the schematic, and the heaviest standing compute cost of any defence.",
                        java.util.List.of("defence", "counter-attack", "automation", "schematic")),
                // ── firmware (docs/design/11-rig-infrastructure.md §3) ───────────────────────────
                //
                // ⚠ THE IMAGE IS THE PURCHASABLE HALF; THE SCHEMATIC IS THE CEILING.
                //
                // `11` §1 establishes the Firmware Implant as "recovered from deep inside Eye
                // infrastructure — acquiring it is itself a late-game objective, not a shop
                // transaction", and §4 rule 1 forbids any EC path to a permanent capability. Both
                // still hold: what the market sells here is the firmware IMAGE, which does nothing
                // whatsoever without the schematic that authorises flashing it.
                //
                // That split is `02` §1.1's own sanctioned pattern — "Rainbow Table is EC + schematic
                // (buy the table, but the capability to use it is found)" — under its standing
                // condition that the ceiling component sits on the non-EC side. It does: no amount of
                // ethecoin produces the schematic, and `02` §2.2 keeps schematics unsellable and
                // un-farmable.
                //
                // ⚠ §4 rule 2: it touches mining income and adds NO cycles. Surviving a host wipe
                // changes how long a deployed miner lives, never how much compute exists — so there
                // is no compute-buys-compute loop (I1) and no ceiling bought with money (I2).
                new Offering(
                        "firmware-implant",
                        "Firmware Implant (image)",
                        "The flashable image for the Firmware Implant: deployed miners survive a host "
                                + "wipe. Worthless on its own -- flashing it needs the schematic, which "
                                + "is recovered rather than bought. Mining must be stopped to install, "
                                + "because firmware sits underneath the program using it.",
                        UnlockGate.ETHECOIN,
                        Balance.FIRMWARE_IMPLANT_IMAGE_PRICE,
                        0,
                        "",
                        UpgradeKind.FIRMWARE,
                        FIRMWARE_IMPLANT_SCHEMATIC,
                        MINING_TOOL,
                        // ⚠ PERMANENT, and the shallowest band in the game applies to it. Firmware is
                        // flashed once and kept; a deep discount on it would take a fixed lump out of
                        // the sink for a purchase a player makes exactly one time.
                        Durability.PERMANENT,
                        java.util.List.of("firmware", "mining", "persistence", "schematic", "flash")));
    }

    /**
     * The schematic that authorises flashing the Firmware Implant.
     *
     * <p>Held in {@code GameSave.schematics}. ⚠ Never sold, never RNG-farmable — {@code 02} §2.2, and
     * {@code 11} §1 names where it comes from: deep inside Eye infrastructure, as a late-game
     * objective. Nothing in this class or in {@code Repac} grants it; the progression slice does.
     */
    public static final String FIRMWARE_IMPLANT_SCHEMATIC = "firmware-implant";

    /** The tool that must be stopped before mining firmware can be flashed. */
    public static final String MINING_TOOL = "mining";

    /**
     * The onion router that makes the TOR Marknet reachable.
     *
     * <h2>⚠ HEAT_STATE-gated, which means it is not for sale at ANY price and never will be</h2>
     *
     * {@code docs/design/02} §2.5: the heat-state gate is "vendor and contact <em>access</em>. Never
     * ownership." This item is the access — it does not defend, mine, breach or hold compute, and
     * owning it changes exactly one thing: a tab appears. It arrives in the COMS inbox when
     * {@code rules/BlackMarket} decides the player has been noticed, and there is no other route to
     * it. A price would turn a relationship into a transaction and would let anybody with money skip
     * the standing and the heat that are the whole gate.
     *
     * <p>⚠ <b>The real Tor is a privacy tool, not a crime tool</b>, and the fiction here should not
     * be read as claiming otherwise: what the module does in this game is resolve addresses ordinary
     * lookups will not, which is what onion routing actually provides. Journalists, whistleblowers
     * and people under censorship are its largest real user groups by some margin. If this ever gets
     * a {@code terms/} page, that is the fact the page has to carry.
     */
    public static final String TOR_MODULE = "tor-module";

    /** Looks an offering up by id. */
    public static java.util.Optional<Offering> byId(String id) {
        return offerings().stream().filter(o -> o.id().equals(id)).findFirst();
    }

    /**
     * The catalogue id for a defence the player arms, from the {@code (kind, tier)} pair the rules use.
     *
     * <h2>⚠ ONE mapping, because there are three callers and they must not drift</h2>
     *
     * {@code LocalGameSession.armIntent} needs it to decide whether the player owns the thing;
     * {@code Views.firewall} needs it to say <em>why</em> a row is locked before the player clicks;
     * and the market needs the offering itself. Written out three times, the day somebody adds a
     * tier is the day the panel offers a row nothing sells and the refusal names an id the shop has
     * never heard of.
     *
     * <p>⚠ <b>The tier is only meaningful for the two ladders.</b> A canary has no tiers, so
     * {@code ("canary", 7)} is still {@code canary-token} rather than an error — the rules call this
     * with whatever the save carries, and a hand-edited tier must not make an owned defence
     * unrecognisable.
     *
     * @return the catalogue id, or empty when the kind is not a defence this game has
     */
    public static java.util.Optional<String> defenceOfferingId(String kind, int tier) {
        if (kind == null) {
            return java.util.Optional.empty();
        }
        // ⚠ Clamped rather than rejected, for the reason above. A ladder has exactly three rungs.
        int rung = Math.max(1, Math.min(3, tier));
        return switch (kind) {
            case "firewall" -> java.util.Optional.of("firewall-t" + rung);
            case "detection-array" -> java.util.Optional.of("detection-array-t" + rung);
            case "canary" -> java.util.Optional.of("canary-token");
            case "tarpit" -> java.util.Optional.of("tarpit");
            case "honeypot-stash" -> java.util.Optional.of("honeypot-stash");
            case "auto-counter-daemon" -> java.util.Optional.of("auto-counter-daemon");
            default -> java.util.Optional.empty();
        };
    }

    /**
     * The one defence a new character already owns — {@code GameEngine.newCharacter} grants it.
     *
     * <h2>⚠ Granted, not exempted</h2>
     *
     * Arming requires owning, with no special cases, so a starting rig needs something to own or the
     * FIREWALL panel opens as ten refusals and the player's first impression of the tool is that
     * none of it works. What this buys is a starting position; the item itself is an ordinary
     * ethecoin-gated one — losable, sellable and re-buyable like everything else on that gate
     * ({@code docs/design/02} §2.1). Selling it and going undefended is a decision the player is
     * allowed to make.
     */
    public static final String STARTING_DEFENCE = "firewall-t1";
}
