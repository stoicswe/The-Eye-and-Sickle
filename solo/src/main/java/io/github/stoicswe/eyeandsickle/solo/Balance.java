package io.github.stoicswe.eyeandsickle.solo;

/**
 * Every tunable number the solo runtime uses, in one place, each cited to the design document that
 * owns it.
 *
 * <h2>Why this class is a wall of constants instead of scattered literals</h2>
 *
 * This module is a <em>second implementation</em> of a subset of the game's rules — the server is the
 * first, and the authoritative one. Duplication is the price of the small footprint (see the module
 * description in {@code solo/pom.xml}), and the only honest way to pay it is to make the duplicated
 * values <em>findable</em>. A number inlined at its use site drifts silently. A number here, with the
 * document and section it came from on the line above it, drifts visibly: anyone re-tuning {@code
 * docs/design/03-economy.md} can grep this file and see the whole blast radius at once.
 *
 * <p>{@code docs/design/03-economy.md} is explicit that the economy figures are calibrated
 * <em>as a set</em>. Changing one here without re-reading that document is how a solo game ends up
 * subtly easier or harder than the real one, which is worse than either.
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * Nothing in this class may be read as authority for online play. A federated server computes its own
 * numbers from its own configuration; these exist so a player with no network can still play a game
 * whose arithmetic matches the design. Solo characters never federate, so the two can never meet in a
 * transaction where a disagreement would matter.
 *
 * @see io.github.stoicswe.eyeandsickle.solo.rules.MiningRules
 */
public final class Balance {

    private Balance() {}

    // ------------------------------------------------------------------ compute

    /**
     * A starting rig has 100 cycles — {@code docs/design/01-core-resources.md} §1.
     *
     * <p>This is the denominator of the entire game. Every cost below is legible only relative to it:
     * a Thorough Scan at 35 is a third of a starting rig, which is why it is a decision rather than a
     * button.
     */
    public static final long STARTING_CYCLES = 100L;

    /**
     * A live deployed miner reserves 3 cycles of the <em>deployer's</em> rig for its control channel,
     * permanently, while it runs — {@code docs/design/01-core-resources.md} §1 and {@code
     * docs/design/04-mining.md} §2.
     *
     * <p>Note which rig pays: the control channel is the deployer's cost, while the miner's actual
     * work is charged to the host (Invariant I6). Getting this backwards would make deployment free,
     * which is the single most load-bearing cost in the mining economy.
     */
    public static final long CONTROL_CHANNEL_CYCLES = 3L;

    // ------------------------------------------------------------------ mining

    /**
     * Self-mining yields 0.4 EC per cycle-hour — derived from {@code docs/design/03-economy.md} §1,
     * which prices a full 100-cycle rig at 40 EC/hr, and stated directly in {@code
     * docs/design/glossary.md}.
     *
     * <p>Expressed in minor units per cycle-hour so the arithmetic stays integral; see {@link
     * io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin} on why money is never a double.
     */
    public static final long SELF_MINING_MINOR_UNITS_PER_CYCLE_HOUR = 40L;

    /**
     * A deployed miner's on-host yield buffer caps at 4 hours — {@code docs/design/04-mining.md} §2.3
     * and {@code docs/design/glossary.md}.
     *
     * <p>This cap is what stops offline income scaling with time away, and it is the prize an attacker
     * takes when they crack a miner. {@code docs/design/15-open-questions.md} OQ-4 flags the figure as
     * a starting value pending session-length telemetry.
     */
    public static final long YIELD_BUFFER_HOURS = 4L;

    // ------------------------------------------------------------------ scanning

    /**
     * Scan tiers cost 5, 15 and 35 cycles — {@code docs/design/04-mining.md} §3.2.
     *
     * <p>What the player buys with the difference is <em>signal strength</em>, not certainty. The
     * curriculum leans on this hard: {@code docs/education/08-detection-and-defence.md} §3.5 uses
     * these three numbers to teach the false-positive trade, so changing them changes a teaching
     * example as well as a cost.
     */
    public static final long SCAN_QUICK_CYCLES = 5L;

    public static final long SCAN_FULL_CYCLES = 15L;
    public static final long SCAN_THOROUGH_CYCLES = 35L;

    /** Wall-clock duration of each scan tier, in seconds — {@code docs/design/04-mining.md} §3.2. */
    public static final long SCAN_QUICK_SECONDS = 30L;

    public static final long SCAN_FULL_SECONDS = 120L;
    public static final long SCAN_THOROUGH_SECONDS = 360L;

    // ------------------------------------------------------------------ defense

    /**
     * Standing compute reservations for armed defences — {@code docs/design/09-defense-and-hardening.md}
     * §1.
     *
     * <p>These are the numbers behind that document's §3 observation that a fully paranoid loadout
     * costs more than a starting rig has. That tension is the point and must survive re-tuning: if
     * every defence can be armed at once, the defensive-budget decision disappears and so does the
     * lesson in {@code docs/education/08-detection-and-defence.md} §3.8.
     */
    public static final long DEFENSE_FIREWALL_T1_CYCLES = 5L;

    public static final long DEFENSE_FIREWALL_T2_CYCLES = 10L;
    public static final long DEFENSE_FIREWALL_T3_CYCLES = 15L;
    public static final long DEFENSE_CANARY_CYCLES = 1L;
    public static final long DEFENSE_TARPIT_CYCLES = 8L;
    public static final long DEFENSE_HONEYPOT_STASH_CYCLES = 12L;
    public static final long DEFENSE_AUTO_COUNTER_CYCLES = 18L;
    public static final long DEFENSE_DETECTION_ARRAY_T1_CYCLES = 6L;
    public static final long DEFENSE_DETECTION_ARRAY_T2_CYCLES = 14L;
    public static final long DEFENSE_DETECTION_ARRAY_T3_CYCLES = 25L;

    /** Ethecoin prices for the purchasable defences — {@code docs/design/09-defense-and-hardening.md} §1. */
    public static final long DEFENSE_CANARY_PRICE = 800L; // 8 EC

    public static final long DEFENSE_TARPIT_PRICE = 7_000L; // 70 EC

    // ------------------------------------------------------------------ market price bands

    /**
     * The price bands from {@code docs/design/03-economy.md} §2, in minor units.
     *
     * <p>Bands rather than prices: the document gives ranges because the exact price of any one item
     * is a content decision, and a solo catalogue that invented precise numbers would be asserting
     * authority it does not have. Offerings are priced inside these bands and say so.
     */
    public static final long PRICE_CONSUMABLE_MIN = 500L; // 5 EC

    public static final long PRICE_CONSUMABLE_MAX = 1_500L; // 15 EC
    public static final long PRICE_MID_TIER_MIN = 4_000L; // 40 EC
    public static final long PRICE_MID_TIER_MAX = 6_000L; // 60 EC
    public static final long PRICE_TOP_PURCHASABLE = 20_000L; // ~200 EC

    /** Relay-chain upkeep, ~8 EC per hop per session — {@code docs/design/03-economy.md} §4. */
    public static final long RELAY_HOP_UPKEEP = 800L;

    // ------------------------------------------------------------------ thermal budget

    /**
     * Thermal Budget recovery, first pass — {@code docs/design/01-core-resources.md} §1.3, which is
     * explicitly tagged <strong>[PROPOSAL]</strong> with numbers "for playtest".
     *
     * <p>The curve is {@code base_rate × (1 − load_factor)^k}: recovery is slower the closer the rig
     * sits to capacity, so a rig running flat out takes much longer to get spent cycles back. That
     * <em>shape</em> is the design commitment; these two constants are not.
     *
     * <p>{@code docs/education/02-computer-architecture.md} deliberately states the shape and no
     * number in its {@code thermal-budget(7)} page, precisely so a tuning pass here cannot falsify a
     * teaching page. Keep it that way.
     */
    public static final double THERMAL_BASE_RATE_CYCLES_PER_SECOND = 0.5d;

    public static final double THERMAL_LOAD_EXPONENT = 2.0d;

    // ------------------------------------------------------------------ starting position

    /**
     * A new solo character starts with 0 EC and a base rig.
     *
     * <p>{@code docs/design/15-open-questions.md} logs this as <strong>Q-economy-seed</strong> — an
     * undecided question about whether a reset character gets a small onboarding grant. Zero is chosen
     * here because it is the option that cannot be wrong in the direction that matters: a grant can be
     * added later without invalidating anyone's save, whereas taking one away cannot.
     */
    public static final long STARTING_ETHECOIN_MINOR_UNITS = 0L;

    /** The Encrypted Vault's starting capacity, in items — {@code docs/design/01-core-resources.md} §6. */
    public static final int STARTING_VAULT_CAPACITY = 6;
}
