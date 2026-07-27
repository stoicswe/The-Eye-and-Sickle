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
     * Thermal Budget recovery — {@code docs/design/01-core-resources.md} §1.3, which is explicitly
     * tagged <strong>[PROPOSAL]</strong> with numbers "for playtest".
     *
     * <p>The curve is <b>bounded</b>: recovery is quick in general, slower the closer the rig sits to
     * capacity, and can never take longer than {@link #THERMAL_MAX_CLEAN_SECONDS} on a rig with
     * nothing stealing from it. That <em>shape</em> is the design commitment; the numbers are not.
     *
     * <h2>⚠ The old formulation was unbounded, and that was the bug</h2>
     *
     * It was {@code rate = 0.5 × (1 − load)² × thermalBudget}, with the time as {@code cycles / rate}.
     * The shape was right and the tail was not: as load approaches capacity the rate approaches zero,
     * so the time approaches <em>infinity</em>. Measured on a real save — a Thorough Scan's 35 cycles
     * on a rig at 90% load took <b>36 minutes</b> to come back, and two cycles at 82% load took a
     * hundred seconds. A player who over-commits should be inconvenienced, not benched, and there was
     * no number in the design that said where the ceiling was because the formula had none.
     *
     * <p>The replacement expresses the ceiling directly: the time is a <em>fraction</em> of a
     * published maximum rather than a quotient that can run away. See {@code ThermalRules}.
     *
     * <p>{@code docs/education/02-computer-architecture.md} deliberately states the shape and no
     * number in its {@code thermal-budget(7)} page, precisely so a tuning pass here cannot falsify a
     * teaching page. Keep it that way.
     */
    public static final double THERMAL_LOAD_EXPONENT = 2.0d;

    /**
     * The longest a recovery may take on a rig with nothing stealing from it: <b>five minutes</b>.
     *
     * <p>Reached only in the corner it describes — returning most of the rig's capacity while the
     * rest of it is pinned. It is an asymptote rather than a clip, so load still reads all the way up
     * instead of flattening into a plateau where 80% and 95% feel identical.
     */
    public static final long THERMAL_MAX_CLEAN_SECONDS = 300L;

    /**
     * The longest a recovery may take at all: <b>ten minutes</b>, and only a rig being comprehensively
     * robbed gets near it.
     *
     * <p>⚠ <b>Rogue processes are the only thing that may lift the ceiling above
     * {@link #THERMAL_MAX_CLEAN_SECONDS}, and this is the second of two ways they slow a rig down.</b>
     * The first is ordinary and needs no special case: a parasite holds cycles, so it raises the load
     * factor, so it slows recovery through the curve every other consumer uses. This one is the
     * thermal half — a machine with something else running on it sheds heat worse — and it is
     * separate on purpose, because a player who has cleared their own allocations down to nothing and
     * <em>still</em> sees a slow recovery has been handed the discrepancy
     * {@code docs/design/04-mining.md} §3.1 is built on.
     */
    public static final long THERMAL_MAX_INFESTED_SECONDS = 600L;

    /**
     * What fraction of the ceiling an <em>idle</em> rig still charges, so recovery is never free.
     *
     * <p>Zero here would make an idle rig return cycles instantly, which deletes the resource: the
     * whole point of {@code 01} §1.3 is that spending is a commitment over time rather than a toll.
     */
    public static final double THERMAL_IDLE_FLOOR = 0.12d;

    /** Nothing takes less than this, so a completed recovery is always something the player saw. */
    public static final long THERMAL_MIN_SECONDS = 2L;

    /**
     * How much slower work runs when parasites are eating the rig — {@code 1.0} means a rig with half
     * its capacity stolen runs everything <b>50% slower</b>.
     *
     * <p><strong>[PROPOSAL]</strong>. Proportional and honest: the machine has less of itself to give,
     * so everything it does takes longer. It applies to a task's <em>duration</em> and not to its
     * price, because the cycles a tool needs are a property of the tool and the time it takes is a
     * property of the machine running it.
     *
     * <p>⚠ <b>It applies whether or not the player has found the parasite</b>, which is the point.
     * Alongside the cycles that simply are not there, a rig that has quietly become sluggish is the
     * cheapest possible hint that something is wrong — and unlike a warning, it cannot be dismissed,
     * ignored or read as a false positive.
     */
    public static final double THEFT_SLOWDOWN = 1.0d;

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

    // ------------------------------------------------------------------ scan precision

    /**
     * Per-tier false-positive rates — {@code docs/design/04-mining.md} §3.2a, decided 2026-07-26.
     *
     * <p>§3.2a publishes the shape in words and not in numbers: Quick is "cheap, fast, and it will
     * send you chasing ghosts", Full is "the working default", Thorough is "expensive in both
     * compute and attention, and it earns it". These are the first numbers for that shape and are
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>The gap between them is the point rather than the values: a Quick Scan that lies a third of
     * the time and a Thorough Scan that lies one time in twenty-five is what makes the 5-versus-35
     * compute price legible. Compressing the range would make the expensive tier's price
     * unjustifiable; widening it would make the cheap tier useless rather than unreliable, and a
     * tier nobody uses teaches nothing.
     *
     * <p>⚠ These feed {@code docs/education/08-detection-and-defence.md}'s {@code false-positive(7)},
     * {@code base-rate-fallacy(7)} and {@code alert-fatigue(7)} pages, which are three of the
     * curriculum's strongest. Re-tuning here means re-reading those.
     */
    public static final double SCAN_FALSE_POSITIVE_QUICK = 0.35d;

    public static final double SCAN_FALSE_POSITIVE_FULL = 0.15d;
    public static final double SCAN_FALSE_POSITIVE_THOROUGH = 0.04d;

    /**
     * What a standing Detection Array multiplies the rate above by —
     * {@code docs/design/09-defense-and-hardening.md} §2, which closed OQ-6 by redefining the Array
     * as <em>precision</em> rather than sensitivity: "scans buy sensitivity, the Array buys
     * precision, and the two are different axes."
     *
     * <p>Multipliers, not subtractions, and that is the load-bearing choice. A subtraction would let
     * a T3 Array drive the Thorough Scan's 4% to zero and make one tier of one defence into a
     * perfect detector — which removes the doubt the whole detection system exists to create, the
     * same argument {@code docs/design/07-recon-tools.md} §2 makes for the Honeypot Detector's
     * mandatory false-negative rate. Multiplying preserves the ordering of the tiers and can never
     * reach certainty.
     *
     * <p>All three <strong>[PROPOSAL]</strong>. The permanent compute they cost (6 / 14 / 25) is
     * established; what it buys was decided in prose and never in figures.
     */
    public static final double DETECTION_ARRAY_PRECISION_T1 = 0.60d;

    public static final double DETECTION_ARRAY_PRECISION_T2 = 0.35d;
    public static final double DETECTION_ARRAY_PRECISION_T3 = 0.15d;

    /**
     * The chance a Full Scan sees a rootkit-wrapped miner —
     * {@code docs/design/04-mining.md} §3.2, whose Finds column says a Full Scan gets "all unhidden
     * miners; <em>some</em> rootkit-wrapped". This is the number behind "some".
     *
     * <p>Sensitivity, not precision, so the Detection Array does <em>not</em> move it. That
     * separation is what makes the Array non-redundant by construction rather than by tuning
     * ({@code 09} §2) and is the whole content of OQ-6's resolution.
     *
     * <p><strong>[PROPOSAL]</strong>. A coin flip is the honest reading of "some" and keeps
     * {@code docs/design/09}'s Rootkit Wrapper worth its 50 EC without making it a wall — the wall
     * is the deliberate audit ({@code 04} §3.1), which always finds it.
     */
    public static final double SCAN_ROOTKIT_SENSITIVITY_FULL = 0.50d;

    /**
     * The scripted tutorial miner planted on a new character's rig —
     * {@code docs/design/04-mining.md} §5.1, established: "the tutorial flow should <em>plant</em> a
     * weak scripted miner early."
     *
     * <p>Six cycles is deliberately below the 8-cycle default a real deployed miner carries: it is
     * weak, it is meant to be found, and it is the thing the first Quick Scan is wrong <em>about</em>
     * half the time. Tier 1 because the crack it enables is the tutorial for the whole breach system,
     * and because a tier-1 crack can never clear {@link #SCHEMATIC_MATERIAL_MIN_TIER} — the safest
     * introduction in the game must not also be a progression source.
     *
     * <p><strong>[PROPOSAL]</strong> for the figures; the plant itself is established.
     */
    public static final long TUTORIAL_MINER_HOST_CYCLES = 6L;

    public static final int TUTORIAL_MINER_TIER = 1;

    // ------------------------------------------------------------------ schematic material

    /**
     * The engagement tier below which a breach yields no schematic material —
     * {@code docs/design/10-botnets.md} §1a and Invariant I13.
     *
     * <p>§1a's exploit guard, applied to the breach: "the material drop is gated on engagement tier
     * — the bot must have been lost against a defended target above a difficulty threshold. Without
     * this, the optimal play is to build the cheapest junk bot and feed it to a loss." The same
     * failure exists here in a different costume — farm the softest live target for material — and
     * the same guard closes it, reading the same {@code resolutionRecord.difficultyTier}.
     *
     * <p>Tier 3 because that is where {@code docs/design/05-hacking-minigame.md} §3.3's mix first
     * stacks two classes: below it, an attempt tests one kind of thinking, which is not the
     * "engagement" §1a means. <strong>[PROPOSAL]</strong>.
     */
    public static final int SCHEMATIC_MATERIAL_MIN_TIER = 3;

    /** One unit per qualifying breach. <strong>[PROPOSAL]</strong> — see {@link #SCHEMATIC_MATERIAL_PER_UNLOCK}. */
    public static final int SCHEMATIC_MATERIAL_PER_BREACH = 1;

    /**
     * Material for one schematic unlock — {@code docs/design/02-unlock-gates.md} §2.2, decided
     * 2026-07-26 (closing OQ-5).
     *
     * <p>§2.2 fixes the rate against the bot-loss stream: "a schematic costs material equivalent to
     * roughly ten destroyed bot instances", anchored to {@code 10} §2's published 25–35 EC per
     * instance, so about 300 EC of deliberately destroyed value. Twelve qualifying breaches is that
     * figure carried across to the other stream that pays into the same pool.
     *
     * <p>Twelve rather than ten because the two streams cost different things. A bot loss costs
     * ethecoin the player already spent; a tier-3-or-better breach costs an attention budget, a
     * compute reservation, and — on a live target — heat. Pricing them identically would make
     * whichever is cheaper on the day the only one anyone used. ⚠ Both guards from §2.2 stay in
     * force and are what make any rate at this level safe: the tier gate above means material never
     * shortcuts a ceiling the player has not already reached, so this number sets <em>pace</em>,
     * never <em>reach</em>.
     *
     * <p><strong>[PROPOSAL]</strong>, like every other figure in {@code 03}.
     */
    public static final int SCHEMATIC_MATERIAL_PER_UNLOCK = 12;

    // ------------------------------------------------------------------ breach: attention

    /**
     * The published per-action attention costs — {@code docs/design/05-hacking-minigame.md} §4's
     * table, which is <b>decided</b> rather than proposed.
     *
     * <p>These four numbers are the whole loud-versus-patient trade, and §4's own column explains
     * each: a quiet read is "the patient baseline", an ordinary probe is "the default move", a loud
     * tool is "power bought with exposure", and the Side-Channel Reader's zero is "its entire
     * identity" — the only action in the game that costs nothing from the bar.
     *
     * <p>⚠ Do not add a fifth. A new cost tier would need a new row in §4 and a reason a player can
     * feel, and the ratio 1 : 2 : 6 is what makes the choice legible before the click.
     */
    public static final int ATTENTION_QUIET_READ = 1;

    public static final int ATTENTION_PROBE = 2;
    public static final int ATTENTION_LOUD_TOOL = 6;
    public static final int ATTENTION_SIDE_CHANNEL = 0;

    /**
     * The Credential Harvester's in-breach cost — twice an ordinary probe, below a loud tool.
     *
     * <p>The one figure not on {@code docs/design/05-hacking-minigame.md} §4's table, and it needs a
     * reason. {@code docs/design/06-intrusion-tools.md} §2 says harvested credentials "open linked
     * nodes without re-solving the rule", which inside a layer means the tool <em>skips a deduction
     * step</em> — strictly more than a probe buys. But it is a reputation-gated tool, not a loud one,
     * and §1 rates its noise Moderate rather than Very high, so pricing it at the loud tier would
     * make it a worse Fuzzer at the same price.
     *
     * <p>Four is the only value that keeps both relationships true. <strong>[PROPOSAL]</strong>, and
     * flagged in {@code docs/design/16-breach-implementation.md} §7 as the one action cost that is
     * not simply read off §4.
     */
    public static final int ATTENTION_CREDENTIAL_HARVESTER = 4;

    /**
     * What an Overflow Kit bypass costs, as a fraction of the layer's whole budget —
     * {@code docs/design/05-hacking-minigame.md} §4, which prices it as "most of the bar" and adds
     * "the cost is the point".
     *
     * <p>Eighty percent is "most of the bar" made a number, and the residue matters: at 100% the Kit
     * would be a layer-shaped suicide button, and at 50% it would be the default opening on every
     * layer, which is precisely the "panic button with a siren attached, never a default" that
     * {@code docs/design/06-intrusion-tools.md} §2 says it must not become. Twenty percent left is
     * enough to finish a layer you had already half-read and never enough to start one.
     *
     * <p><strong>[PROPOSAL]</strong> for the fraction; the "most of the bar" shape is decided.
     */
    public static final double ATTENTION_BYPASS_FRACTION = 0.80d;

    /**
     * Extra attention a strike burns, on top of whatever the failing action already cost —
     * {@code docs/design/05-hacking-minigame.md} §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)", expressed in the only currency §4 leaves.
     *
     * <p>Three is a probe and a half: enough that a guess costs meaningfully more than a deduction,
     * which is the mechanical difference between the Logic class being reasoning and being
     * enumeration, and small enough that a player who miscounts once is not finished.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int ATTENTION_ALARM_PENALTY = 3;

    /**
     * Attention a Tarpit adds to <em>every</em> action's cost —
     * {@code docs/design/09-defense-and-hardening.md} §1: "Slows every intruder action; doesn't stop
     * them, buys response time."
     *
     * <p>A surcharge on each action rather than a cut to the budget, and the distinction is the
     * whole translation: cutting the budget would make a Tarpit a flat difficulty add, which is the
     * <em>Firewall's</em> published function. A per-action surcharge punishes exactly the play the
     * Tarpit is written to punish — many small moves — and leaves a patient reader who takes few
     * moves nearly untouched.
     *
     * <p>⚠ {@code 09} §2 also ties the Tarpit to the bot-backlog timer in {@code 10} §1, which is
     * real-time and completely unaffected by this. The two effects coexist; neither replaces the
     * other. <strong>[PROPOSAL]</strong>.
     */
    public static final int TARPIT_ATTENTION_SURCHARGE = 1;

    /**
     * Attention a Firewall removes from each layer's budget, per tier —
     * {@code docs/design/09-defense-and-hardening.md} §1, whose Function column for the Firewall is
     * exactly "Flat difficulty increase on incoming breach attempts" and nothing more specific.
     *
     * <p>This is {@code docs/design/05-hacking-minigame.md} §8's unpublished item 7 given a number.
     * Two per tier means a T3 Firewall takes six attention off every layer — roughly three ordinary
     * probes, against a budget of twenty-odd. That is felt without being decisive, which is what
     * "flat difficulty increase" asks for and what keeps the Firewall's real cost its 15 permanent
     * cycles ({@code 09} §2) rather than its effect. <strong>[PROPOSAL]</strong>.
     */
    public static final int FIREWALL_BUDGET_PENALTY_PER_TIER = 2;

    /**
     * The floor a layer's budget can never be pushed below by defences.
     *
     * <p>Without it, a T3 Firewall on a tier-5 target would leave 14, and stacking any future
     * defence on the same axis would eventually produce a layer that cannot be cleared by any
     * sequence of legal moves. An unwinnable board is not difficulty; it is the game deciding, which
     * is the one reading {@code docs/design/05-hacking-minigame.md} §1 constraint 4 forbids
     * outright. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ATTENTION_FLOOR = 8;

    // ------------------------------------------------------------------ breach: noise

    /**
     * Noise a breach generates just by happening, before any action —
     * {@code docs/design/01-core-resources.md} §3 ("noise is generated by acting") and
     * {@code docs/design/05-hacking-minigame.md} §2, which makes {@code noiseGenerated} a
     * first-class output of every attempt.
     *
     * <p>Non-zero on purpose: an attempt that generated nothing would make an aborted breach free,
     * and {@code 05} §4.1 is explicit that on an abort "the noise already generated stays
     * generated". <strong>[PROPOSAL]</strong> — {@code 01} §3.2's own concrete model is tagged the
     * same way.
     */
    public static final int NOISE_BASE = 2;

    /**
     * Per-action noise, mapping {@code docs/design/06-intrusion-tools.md} §1's qualitative Noise
     * column onto {@code 01} §3.2's "noise is a scalar per player-pool".
     *
     * <p>The column gives None / Low / Moderate / Very high; this is that ladder as 0 / 1 / 5 / 12.
     * The Side-Channel Reader's zero is stated twice in the docs ({@code 06} §1's table and §2's
     * "zero noise"), so it is the one value here that is not an interpretation. The Overflow Kit's
     * "**Very high**" is bolded in the source table and is the loudest thing in the tool set, which
     * is why the gap between it and a loud tool is wider than the gap between loud and quiet.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_QUIET_READ = 0;

    public static final int NOISE_PROBE = 1;
    public static final int NOISE_LOUD_TOOL = 5;
    public static final int NOISE_BYPASS = 12;
    public static final int NOISE_SIDE_CHANNEL = 0;

    /**
     * Noise added per alarm tripped.
     *
     * <p>An alarm is the defender noticing, which is the definition of being loud. Pricing it at
     * four puts a single strike between an ordinary probe and a loud tool, so a clumsy quiet run can
     * end up noisier than a clean loud one — which is the correct lesson and the one
     * {@code docs/design/08-stealth-and-noise.md} §4 assumes when it pairs each offensive escalation
     * with a stealth counter. <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_PER_ALARM = 4;

    /**
     * How much noise converts into one point of personal heat.
     *
     * <p>{@code docs/design/01-core-resources.md} §3.2: "noise is per-action and decaying; heat is
     * accumulated standing. Noise is tactical, heat is strategic." A divisor is that sentence made
     * arithmetic — most of a breach's noise never becomes heat at all, and only a genuinely loud
     * attempt leaves a mark that outlives the session.
     *
     * <p>At eight, a clean quiet breach of a small target leaves nothing, and a bypass-and-alarms
     * attempt leaves two or three points on a 0–100 scale. Reaching the named-hacker band ({@code
     * 01} §4.1) therefore takes a campaign rather than an evening, which is what that band is for.
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>⚠ Invariant I9 short-circuits all of this on a miner crack: zero heat, on every outcome,
     * including failure. Defending your own rig never contributes to being wanted.
     */
    public static final int NOISE_PER_HEAT_POINT = 8;

    /** Personal heat is a 0–100 scale — {@code docs/design/01-core-resources.md} §4.1's bands. */
    public static final int PERSONAL_HEAT_MAX = 100;

    // ------------------------------------------------------------------ breach: session cost

    /**
     * Cycles a breach attempt reserves for itself, before the loadout —
     * {@code docs/design/05-hacking-minigame.md} §2, which instantiates an attempt with
     * {@code equippedTools} "each of which modifies the attempt", implying a cost for the attempt
     * itself that the tool list does not carry.
     *
     * <p>Bracketed by numbers that already exist: a Quick Scan is 5 and a Full Scan is 15 ({@code
     * 04} §3.2), and the Overflow Kit — the one tool defined entirely in terms of being inside a
     * breach — is 10 ({@code 06} §1). Ten puts an attempt above a cheap look and below a serious
     * sweep, which is the right relative price for something that is a commitment rather than a
     * glance.
     *
     * <p>Held for the whole attempt and released into recovery at resolution, exactly like a scan
     * (UI-6). <strong>[PROPOSAL]</strong>.
     */
    public static final long BREACH_SESSION_CYCLES = 10L;

    // ------------------------------------------------------------------ breach: tier tables

    /**
     * Layers per attempt, by difficulty tier — {@code docs/design/05-hacking-minigame.md} §3.3,
     * which makes {@code difficultyTier} scale "layer count, class mix, time pressure and error
     * tolerance", and §3.1, where "a given target composes 1-N layers".
     *
     * <p>1, 1, 2, 3, 3. Growth stops at three because §3.1 fixed the class set at three and a fourth
     * layer would have to repeat a class within one attempt; tier 5 does repeat Traversal, which is
     * a deliberate exception (see {@link #breachClasses}) rather than the rule.
     *
     * <p>A method rather than an array constant: {@code public static final int[]} is writable by
     * anyone who holds it, and a balance table that any caller can silently edit is worse than no
     * table. <strong>[PROPOSAL]</strong>.
     */
    public static int breachLayers(int tier) {
        return switch (clampTier(tier)) {
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * Attention granted per layer, by tier, before defensive modifiers — §3.3's "time pressure" knob
     * translated into the only currency §4 leaves.
     *
     * <p>26, 24, 22, 22, 20. It <em>falls</em> as boards grow, which is the whole difficulty curve:
     * a tier-5 Logic board has a keyspace of 100 000 against a tier-2 board's 1296, and it gets four
     * fewer attention to crack it. Reading the two tables together is the only way either makes
     * sense, which is why they sit next to each other.
     *
     * <p>⚠ {@code 05} §3.3 still says "time pressure (trace timer speed)". That is residual pre-§4
     * wording — §4 removed the wall clock outright and there is no timer. Flagged for the
     * integrator. <strong>[PROPOSAL]</strong>.
     */
    public static int breachAttention(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 26;
            case 2 -> 24;
            case 3, 4 -> 22;
            default -> 20;
        };
    }

    /**
     * Strikes a layer tolerates before it locks out — §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)".
     *
     * <p>4, 3, 3, 2, 2. Four at tier 1 is the tutorial being forgiving on the player's own rig,
     * where Invariant I9 already guarantees a loss costs no heat; two at the top is where a wrong
     * read is genuinely expensive. <strong>[PROPOSAL]</strong>.
     */
    public static int breachStrikeLimit(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 4;
            case 2, 3 -> 3;
            default -> 2;
        };
    }

    /**
     * The class mix, by tier — §3.3's "class mix (higher tiers stack harder classes)".
     *
     * <p>The progression teaches in the order the classes teach in. Enumeration first, because
     * reading structure is the prerequisite for everything and Port Sweep is in the starting kit
     * ({@code 06} §2). Logic second, because it is the one class with a closed-form answer and
     * therefore the one where a player can feel deduction working. Traversal last, because its
     * human-read step needs a player who already trusts that reading the flavour data pays.
     *
     * <p>Tier 5 repeats Traversal deliberately: two lattices in one attempt is the only place the
     * design asks a player to hold two graphs in mind at once, and §3.1's merge rule is about two
     * <em>classes</em> reducing to the same optimal input, not about a class appearing twice.
     *
     * <p><strong>[PROPOSAL]</strong>. Returns names rather than the enum so this class stays free of
     * any dependency on which constants {@code PuzzleClass} currently has.
     */
    public static java.util.List<String> breachClasses(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> java.util.List.of("ENUMERATION");
            case 2 -> java.util.List.of("LOGIC");
            case 3 -> java.util.List.of("ENUMERATION", "LOGIC");
            case 4 -> java.util.List.of("ENUMERATION", "LOGIC", "TRAVERSAL");
            default -> java.util.List.of("LOGIC", "TRAVERSAL", "TRAVERSAL");
        };
    }

    /** Clamps to the 1–5 scale {@code DifficultyTier} publishes, so a hand-edited save cannot crash generation. */
    private static int clampTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    // ------------------------------------------------------------------ breach: board generation

    /**
     * Enumeration board size — {@code docs/design/05-hacking-minigame.md} §3.1 ("map a node's open
     * ports/services before you can act") and §3.3's layer scaling.
     *
     * <p>12 slots at tier 1 growing by 2 a tier, in bands of 4. Twelve is three full bands, which is
     * the smallest board on which a sweep is worth its 1 attention against a probe's 2 — on a
     * smaller board, probing everything is simply cheaper, and the class's central trade would not
     * exist at tier 1 where it is being taught. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ENUM_SLOTS_BASE = 12;

    public static final int BREACH_ENUM_SLOTS_PER_TIER = 2;
    public static final int BREACH_ENUM_BAND_SIZE = 4;

    /**
     * Logic code length and alphabet size — {@code docs/design/05-hacking-minigame.md} §3.1's
     * "Mastermind-family reasoning".
     *
     * <p>Length {@code 3 + tier/2} against an alphabet of {@code 5 + tier}: keyspaces of 216, 1296,
     * 4096, 6561 and 100 000. The jump at tier 5 is deliberate — it is where the class stops being
     * solvable by holding candidates in your head and starts requiring the readout the board
     * publishes.
     *
     * <p>Ten symbols is the ceiling because the alphabet is a fixed ASCII set (see
     * {@code LogicRules}); adding an eleventh means adding a character, and every character the
     * client draws has to exist in the bundled font. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_LENGTH_BASE = 3;

    public static final int BREACH_LOGIC_ALPHABET_BASE = 5;

    /**
     * The tier at and above which a Logic code is always salted, and the chance it is salted below
     * that — {@code docs/design/06-intrusion-tools.md} §2, which makes the Rainbow Table
     * "hard-countered by salting, by design" and "a conditional power spike: devastating against
     * lazy targets, useless against prepared ones, so it rewards recon".
     *
     * <p>The 30% below tier 3 is what makes it conditional rather than binary. If low tiers were
     * never salted the Table would be an unconditional win on exactly the targets a new owner can
     * reach, and §2's "know before you buy the attempt" would have nothing to know.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_ALWAYS_SALTED_TIER = 3;

    public static final double BREACH_LOGIC_SALT_CHANCE = 0.30d;

    /**
     * Probes in one Fuzzer volley, and the tier at which a volley starts also costing a strike.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 calls the Fuzzer "the entry-level 'I don't know
     * the rule, so I'll hammer it' tool" with "moderate noise as the cost of impatience". Four
     * guesses for 6 attention is breadth at three times a probe's price, paid in quality: a volley
     * returns exact counts only, never partials.
     *
     * <p>From tier 4 the hammer starts setting off alarms, which is where the tool stops scaling and
     * the class stops being brute-forceable — the mechanical form of §3.2's "a defended/high-tier
     * node can be built to defeat a fixed strategy". <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_VOLLEY_SIZE = 4;

    public static final int BREACH_LOGIC_VOLLEY_ALARM_TIER = 4;

    /**
     * Positions a Rainbow Table reveals against an unsalted code —
     * {@code docs/design/06-intrusion-tools.md} §1, whose Function column is "instant crack against
     * weak or reused credentials".
     *
     * <p>Two rather than all of them. A full reveal would make the Table skip the layer, which is the
     * Overflow Kit's job and is proof-of-skill-gated for exactly that reason ({@code 02} §2.4) — an
     * EC-plus-schematic item must not do a proof-of-skill item's work. Two positions collapse the
     * keyspace by a factor of the alphabet squared and still leave a deduction to finish.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_RAINBOW_REVEALS = 2;

    /**
     * Traversal lattice shape — {@code docs/design/05-hacking-minigame.md} §3.1 ("route through an
     * internal graph to the data node") and §3.2's decoy requirement.
     *
     * <p>{@code 3 + tier/2} ranks, 3–4 nodes wide, with {@code 2 + tier/2} objective candidates on
     * the final rank. The candidate count is what {@code P-3} is measured against: a fixed heuristic
     * that cannot read the logs must extract at random and averages {@code (K+1)/2} attempts, while
     * a reader gets it in one. That gap is the number, and it must not be tuned away — see
     * {@code docs/design/16-breach-implementation.md} §5. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_RANKS_BASE = 3;

    public static final int BREACH_TRAVERSAL_WIDTH_MIN = 3;
    public static final int BREACH_TRAVERSAL_WIDTH_MAX = 4;
    public static final int BREACH_TRAVERSAL_OBJECTIVES_BASE = 2;

    /**
     * Extra attention a tarpit node charges on entry, and how many ranks a {@code traceroute}
     * reveals ahead of the current node.
     *
     * <p>Two ranks is the Topology Mapper's published reach ({@code docs/design/07-recon-tools.md}
     * §1: "extends graph visibility from one hop to two"), reused here because a loud in-breach tool
     * should buy what the quiet pre-breach tool buys and pay for it in noise instead of a schematic.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_TARPIT_STEP_COST = 2;

    public static final int BREACH_TRACEROUTE_RANKS = 2;

    // ================================================================== the network (design/17)
    //
    // Two sentences hold this whole block together, and every number below is one of them made
    // arithmetic:
    //
    //   1. SCHEMATICS BUY REACH, ETHECOIN BUYS SENSITIVITY. The hop ceiling is 1, or 2 with the
    //      Topology Mapper schematic — docs/design/07-recon-tools.md §2 calls that tool "a CEILING on
    //      information ... hence schematic-gated not purchasable (Invariant I2)". No sweep tier at
    //      any price appears in NetRules.hopCeiling, and there is no constant here it could read.
    //      What the tiers move is the PROBABILITY of detecting what is already in reach, which is
    //      breadth, which docs/design/02-unlock-gates.md §1.1 step 4 puts on ethecoin.
    //
    //   2. DETECTION IS ROLLED ONCE, AT GENERATION, AND STORED. Every table below that produces a
    //      threshold is compared against HostState.detectRoll, which the world was built with. A
    //      sweep makes no detection draw, so re-sweeping is never a reroll and save-scumming is
    //      defeated by construction rather than by a cooldown.

    // ------------------------------------------------------------------ network: world size

    /**
     * Five to seven virtual servers — the brief's ceiling, and a floor that keeps the depth gradient
     * usable.
     *
     * <p>Seven is a hard cap on how much world one save may carry: at the machine cap below it is
     * 350 hosts in a file rewritten every thirty seconds. Five is the floor because a depth-biased
     * spanning tree over four servers frequently produces a maximum depth of two, and the difficulty
     * tables run to four — a world whose deep rows never appear is a world where half the tuning is
     * decoration. <strong>[PROPOSAL]</strong>.
     */
    public static final int NET_SERVERS_MIN = 5;

    public static final int NET_SERVERS_MAX = 7;

    /**
     * The chance a newly placed server attaches to one of the currently deepest servers rather than
     * to any already-placed one.
     *
     * <p>This single number is the shape of the world. At 0.0 the tree is a bush — every server one
     * hop from home, no gradient at all. At 1.0 it is a chain — one path, no choice about which way
     * to push, and a depth that is forced rather than chosen. Sixty percent leans deep while still
     * branching often enough that the player is regularly choosing between two directions, which is
     * the decision the map exists to present. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_DEEPEN_BIAS = 0.60d;

    /**
     * Chance and cap for the extra server-graph edges added on top of the spanning tree.
     *
     * <p>Chords are what make the picture read as a network rather than a taxonomy: without them
     * every route is the unique tree path and the graph view is a family tree. Two is enough to be
     * felt on a five-to-seven node graph and few enough that the tree is still obviously the
     * skeleton.
     *
     * <p>⚠ A chord may only join servers whose depths differ by at most one, and that constraint is
     * load-bearing rather than cosmetic — see {@link io.github.stoicswe.eyeandsickle.solo.state.ServerState}.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_CHORD_CHANCE = 0.35d;

    public static final int NET_SERVER_CHORD_MAX = 2;

    /**
     * Chance of an extra link between two machines on the same server, per host.
     *
     * <p>The intra-server graph is a spanning tree first (so every machine is reachable by
     * construction, never by a retry loop) and then this. Without it, every host has exactly one
     * route and taking a foothold never opens more than one new direction, which would make the
     * vantage mechanic — reposition to see further — a straight line instead of a choice.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_INTRA_CHORD_CHANCE = 0.22d;

    /** The brief's hard ceiling: never more than fifty machines on one server. */
    public static final int NET_MACHINES_HARD_CAP = 50;

    // ------------------------------------------------------------------ network: the home floor

    /**
     * How many machines the player's rig is guaranteed to sit one link from, and how many of those
     * are guaranteed to be workable first contacts.
     *
     * <p>⚠ These two are not tuning. They are the fix for "discovery is unusable at the start", and
     * they are applied deterministically after every roll: the first three non-gateway hosts at one
     * link from the rig are forced to {@code detectRoll = 0.0}, tier 1, firewall 0, undefended, with
     * a payout floor. Zero is below the base T1 sweep's <em>worst</em> threshold (0.35, a quiet
     * machine at one hop), so <b>the first sweep a new player runs always returns at least three
     * breachable, un-firewalled targets worth at least 3 EC each</b>, on every seed, forever.
     *
     * <p>Five neighbours rather than three so the guarantee is not also the whole board — a new
     * player should have something to miss and then find with a better instrument, which is what
     * teaches that sensitivity is a purchase. <strong>[PROPOSAL]</strong> in the figures; the
     * guarantee itself is not.
     */
    public static final int NET_HOME_SEED_NEIGHBOURS = 5;

    public static final int NET_HOME_GUARANTEED_CONTACTS = 3;

    /**
     * The payout floor on a guaranteed first contact, in minor units — 3 EC.
     *
     * <p>The bottom of the T1 loot band, restated as a floor so that the three machines a new player
     * is guaranteed to find are also guaranteed to be worth breaching. Three of them is 9 EC against
     * the 15 EC Passive Sniffer, which is the intended first purchase and the intended second or
     * third session's worth of work. <strong>[PROPOSAL]</strong>.
     */
    public static final long NET_LOOT_FLOOR_MINOR_UNITS = 300L;

    // ------------------------------------------------------------------ network: the sweep line

    /**
     * How much a hop of distance costs a sweep's detection chance.
     *
     * <p>{@code docs/design/07-recon-tools.md} §2 makes the Topology Mapper a <em>reach</em>
     * purchase — "extends graph visibility from one hop to two" — and pointedly not a clarity one.
     * So the second hop is real and coarse: everything visible at one hop is more likely to be seen
     * than the same machine at two, and the schematic buys the ability to look at all rather than a
     * better look. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_HOP_FACTOR_1 = 1.00d;

    public static final double NET_HOP_FACTOR_2 = 0.60d;

    /**
     * Compute each sweep tier holds while it runs — inside {@code docs/design/07-recon-tools.md}
     * §1's established 2–14 recon range.
     *
     * <p>⚠ <b>These are the sweep's price in capacity, and are no longer also its noise.</b> They
     * used to be both, via the {@code CONTROL_CHANNEL} reservation, and the identity was elegant and
     * measurably wrong on screen: noise is drawn as outward cycles over <em>rig capacity</em>, so a
     * base sweep on a 100-cycle rig moved the meter by two percent — indistinguishable from silence,
     * and getting quieter the bigger the player's rig grew, which inverts the reading. Loudness is
     * now stated separately in {@link #NET_SWEEP_BASE_NOISE} and the two are free to differ, because
     * how much of your machine a job occupies and how much racket it makes outside it are genuinely
     * different quantities. See {@code docs/design/08-stealth-and-noise.md} §1: noise is generated by
     * <em>acting</em>, and the act here is touching machines that are not yours.
     */
    public static final long NET_SWEEP_BASE_CYCLES = 2L;

    public static final long NET_SWEEP_WIDE_CYCLES = 5L;
    public static final long NET_SWEEP_DEEP_CYCLES = 9L;

    /**
     * How loud each sweep tier is while it runs, on the same 0–{@code totalCycles} scale the noise
     * meter reads — so 35 on a 100-cycle rig is a bit over a third of the meter.
     *
     * <p><strong>[PROPOSAL]</strong>. A sweep is intrusive by construction: it puts packets on
     * machines the player does not own and has no business touching, and
     * {@code docs/design/08-stealth-and-noise.md} §1 makes that exactly what noise measures. Every
     * tier is therefore loud, and the ladder is loudness, not just sensitivity — a deep sweep listens
     * harder <em>by shouting louder</em>, which is what makes buying up the ladder a decision rather
     * than a strict upgrade. The precedent is {@code docs/design/07-recon-tools.md} §1's Ping Sweep,
     * the one recon tool the table already marks <b>High</b> for exactly this reason.
     *
     * <p>⚠ <b>It is loud only while it runs.</b> The allocation goes into thermal recovery the moment
     * the sweep settles, and recovering cycles are excluded from the noise sum — so the meter drops
     * back to whatever the rig was doing before. Noise is a rate, not a debt; what persists after a
     * loud act is <em>heat</em>, which is a different field and is charged by different rules.
     *
     * <p>⚠ Deliberately below {@code totalCycles} even at the top of the ladder. A tier that pinned
     * the meter would erase the distinction between "loud" and "as loud as this rig gets", and the
     * player needs the headroom to read a sweep running <em>on top of</em> something else.
     */
    public static final long NET_SWEEP_BASE_NOISE = 35L;

    public static final long NET_SWEEP_WIDE_NOISE = 55L;
    public static final long NET_SWEEP_DEEP_NOISE = 80L;

    /**
     * Wall-clock duration of each sweep tier, in seconds.
     *
     * <p>Bracketed by the scan ladder ({@code docs/design/04-mining.md} §3.2: 30 s / 120 s / 360 s).
     * A base sweep is shorter than a Quick Scan because it is the verb a new player runs before they
     * own anything, and a tool whose floor is a thirty-second wait is a tool they run once.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final long NET_SWEEP_BASE_SECONDS = 20L;

    public static final long NET_SWEEP_WIDE_SECONDS = 45L;
    public static final long NET_SWEEP_DEEP_SECONDS = 90L;

    /**
     * Ethecoin prices for the two purchasable sweep tiers.
     *
     * <p>Priced against {@code docs/design/03-economy.md} §2's bands, and 25 EC sits deliberately
     * <em>between</em> the consumable band (5–15) and the mid-tier band (40–60). Both edges are
     * forced: it is a permanent tool, so it must cost more than the 15 EC Passive Sniffer, and it is
     * the first upgrade a new player buys, so pricing it at 40 would put it roughly two hours out of
     * reach on §3's 20–30 EC/hr cautious net. The Provenance Tracer's established 30 EC is the
     * precedent for a permanent tool priced off-band.
     *
     * <p>55 EC is squarely inside the mid-tier band and is about one cautious session — {@code 03}
     * §2's own rule for that band is that losing one costs an evening. <strong>[PROPOSAL]</strong>.
     */
    public static final long NET_SWEEP_WIDE_PRICE = 2_500L; // 25 EC

    public static final long NET_SWEEP_DEEP_PRICE = 5_500L; // 55 EC

    /**
     * Depth 0 is never counter-hacked, and this is a constant rather than a table row that happens to
     * be zero.
     *
     * <p>⚠ Stated as its own named value so a re-tune of {@link #netCounterHackChance} cannot make it
     * non-zero by accident. <b>A player who has never left home is never counter-hacked</b>: the home
     * server is where the game teaches, and a teaching space that occasionally plants a parasite on
     * the student is a teaching space they learn to avoid.
     */
    public static final double NET_COUNTER_HACK_HOME = 0.0d;

    // ------------------------------------------------------------------ network: depth tables
    //
    // Methods, never int[]. See breachLayers above: "a balance table that any caller can silently
    // edit is worse than no table." Every one of these clamps its depth through netDepth first, so a
    // hand-edited save carrying a depth of 40 reads the deepest published row rather than crashing.

    /**
     * Clamps a server's BFS depth to the {@code 0..4} range every table below publishes.
     *
     * <p>Deeper servers read the row for 4. A depth-biased tree over seven servers can reach six, and
     * inventing rows for depths the tables were never tuned against would be tuning by extrapolation.
     */
    public static int netDepth(int depth) {
        return Math.max(0, Math.min(4, depth));
    }

    /**
     * Machines per server, by depth — the brief's "max 50 machines per server", with home smallest.
     *
     * <p>Home is 12–20 because it is the tutorial floor and has to be legible: the list view is
     * exhaustive and the graph view clamps at ten rows a layer, so a home server of fifty machines
     * would introduce the overflow marker on the first screen a player ever sees. Deeper servers grow
     * to the cap, which is where the split between the legible surface and the exhaustive one starts
     * doing real work. <strong>[PROPOSAL]</strong>.
     */
    public static int netMachinesMin(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 12;
            case 1 -> 18;
            case 2 -> 24;
            case 3 -> 30;
            default -> 34;
        };
    }

    /** @see #netMachinesMin */
    public static int netMachinesMax(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 20;
            case 1 -> 30;
            case 2 -> 38;
            case 3 -> 46;
            default -> NET_MACHINES_HARD_CAP;
        };
    }

    /**
     * Host kind for a rolled {@code u}, by depth — cumulative left to right over TERMINAL, RELAY,
     * STORE, SENTRY.
     *
     * <p>The gradient is the world getting less civilian and more instrumented as you leave home:
     * TERMINAL falls from 0.60 to 0.20 while SENTRY rises from 0.03 to 0.40. A {@code TERMINAL} is a
     * citizen's or clerk's desktop — the bread-and-butter low-level NPC the brief asks for — and a
     * {@code SENTRY} is {@code docs/design/14}'s "new defended infrastructure appearing on the graph".
     *
     * <p>⚠ {@code GATEWAY} and {@code BRIDGE} are assigned structurally and override this. The roll is
     * still made, because the RNG contract is draw-unconditionally-discard-conditionally: a draw
     * count that depended on whether a host happened to be a bridge would make the stream shape
     * depend on earlier draws, and a replay from a stored seed would stop being a replay.
     *
     * @return a {@code HostKind.name()}
     */
    public static String netHostKind(int depth, double u) {
        double terminal;
        double relay;
        double store;
        switch (netDepth(depth)) {
            case 0 -> {
                terminal = 0.60d;
                relay = 0.22d;
                store = 0.15d;
            }
            case 1 -> {
                terminal = 0.50d;
                relay = 0.22d;
                store = 0.20d;
            }
            case 2 -> {
                terminal = 0.38d;
                relay = 0.20d;
                store = 0.25d;
            }
            case 3 -> {
                terminal = 0.28d;
                relay = 0.18d;
                store = 0.26d;
            }
            default -> {
                terminal = 0.20d;
                relay = 0.15d;
                store = 0.25d;
            }
        }
        if (u < terminal) {
            return "TERMINAL";
        }
        if (u < terminal + relay) {
            return "RELAY";
        }
        if (u < terminal + relay + store) {
            return "STORE";
        }
        return "SENTRY";
    }

    /**
     * Difficulty tier for a rolled {@code u}, by depth. Means 1.30 / 1.85 / 2.80 / 3.80 / 4.55.
     *
     * <p>⚠ The bands do not merely shift, they <em>slide</em>: tier 1 is unreachable from depth 2
     * and tier 5 unreachable below depth 3. That is the brief's "the more bridge hops from home, the
     * harder on average" made a floor as well as an average — a player two bridges out cannot stumble
     * onto a tutorial-grade machine, and a player at home cannot stumble onto a wall.
     *
     * <p>Home tops out at tier 2 by the table and is clamped to 2 again by the home floor pass, so
     * the clamp is belt and braces on the one server where a bad roll is unrecoverable.
     * <strong>[PROPOSAL]</strong>.
     *
     * @return a tier on the shared 1–5 scale
     */
    public static int netTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.70d ? 1 : 2;
            case 1 -> u < 0.35d ? 1 : (u < 0.80d ? 2 : 3);
            case 2 -> u < 0.40d ? 2 : (u < 0.80d ? 3 : 4);
            case 3 -> u < 0.40d ? 3 : (u < 0.80d ? 4 : 5);
            default -> u < 0.45d ? 4 : 5;
        };
    }

    /**
     * Firewall tier for a rolled {@code u}, by depth — the flat difficulty add from
     * {@code docs/design/09-defense-and-hardening.md} §1.
     *
     * <p>⚠ <b>Never returns 4.</b> {@code BreachTarget}'s compact constructor throws outside 0..3,
     * so a fourth band here would not be a balance mistake, it would be an exception thrown while
     * building the target list — which is to say, a save that cannot render its own network.
     * <strong>[PROPOSAL]</strong>.
     */
    public static int netFirewallTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.85d ? 0 : 1;
            case 1 -> u < 0.45d ? 0 : (u < 0.90d ? 1 : 2);
            case 2 -> u < 0.15d ? 0 : (u < 0.55d ? 1 : (u < 0.90d ? 2 : 3));
            case 3 -> u < 0.05d ? 0 : (u < 0.25d ? 1 : (u < 0.70d ? 2 : 3));
            default -> u < 0.10d ? 1 : (u < 0.45d ? 2 : 3);
        };
    }

    /**
     * Chance a host runs a Tarpit, by depth.
     *
     * <p>Zero at home, and that is the home floor rather than the table: a Tarpit surcharges every
     * action, which is exactly the defence that punishes a player still learning to read a board.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netTarpitChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.25d;
            default -> 0.35d;
        };
    }

    /**
     * Chance a host carries canary tokens, by depth.
     *
     * <p>A canary tags the toucher's handle rather than stopping them ({@code docs/design/09} §1), so
     * the gradient is really a gradient in how much of the deep network knows who you are.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netCanaryChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.08d;
            case 2 -> 0.20d;
            case 3 -> 0.32d;
            default -> 0.45d;
        };
    }

    /**
     * Chance a host is genuinely live and defended, by depth.
     *
     * <p>⚠ This is <b>ground truth</b> and it is not what the player is told. A target reports
     * {@code LIVE} only once the Traffic Analyzer has established it ({@code docs/design/07} §1,
     * §2 — "directly supports proof-of-skill"), so this number sets how much of the deep network is
     * <em>worth</em> proof-of-skill credit, not how much of it hands credit out. A generator that
     * also set {@code trafficAnalyzed} would give away a reputation-gated tool's entire product and
     * with it Invariant I7. <strong>[PROPOSAL]</strong>.
     */
    public static double netDefendedChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.05d;
            case 1 -> 0.25d;
            case 2 -> 0.50d;
            case 3 -> 0.70d;
            default -> 0.85d;
        };
    }

    /**
     * Chance a host is actually an Eye trap, by depth.
     *
     * <p>Ground truth again, and the Honeypot Detector's mandatory false-negative rate
     * ({@code docs/design/07} §2) sits on top of it — so the player's worst case is not fourteen
     * percent of deep machines being traps, it is fourteen percent being traps and a clean reading
     * never being a guarantee. That residual doubt is the product. <strong>[PROPOSAL]</strong>.
     */
    public static double netHoneypotChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.02d;
            case 2 -> 0.06d;
            case 3 -> 0.10d;
            default -> 0.14d;
        };
    }

    /**
     * Chance a document-eligible host carries a story fragment, by depth.
     *
     * <p>⚠ Home is zero, and that is decision <b>N-4</b> made structural rather than promised: story
     * documents are flavour plus schematic material and must never be a critical path, so the
     * flavour layer starts one bridge out and nothing on the early path can depend on it. Only
     * {@code STORE} and {@code SENTRY} hosts are eligible at all. <strong>[PROPOSAL]</strong>.
     */
    public static double netDocumentChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.28d;
            default -> 0.40d;
        };
    }

    /**
     * Chance one sweep provokes a counter-hack, measured against the deepest server the sweep
     * reached.
     *
     * <p>The brief's "the more bridge hops from home, ... the more likely the machine hacks the
     * player back". Rolled against the <em>candidate</em> set rather than the detected set: the
     * machines notice you probing them whether or not you learn anything, which is also the honest
     * reading of a sweep as an intrusive outbound action.
     *
     * <p>⚠ Depth 0 returns {@link #NET_COUNTER_HACK_HOME}, a named constant, and a test asserts it.
     * <strong>[PROPOSAL]</strong> above home.
     */
    public static double netCounterHackChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> NET_COUNTER_HACK_HOME;
            case 1 -> 0.04d;
            case 2 -> 0.10d;
            case 3 -> 0.18d;
            default -> 0.28d;
        };
    }

    /**
     * Personal heat a counter-hack leaves, by the depth that provoked it.
     *
     * <p>⚠ The heat lands on the <b>player</b>, and that is correct rather than harsh: the player's
     * own sweep reached another machine, which is an intrusive outbound action and heat-bearing under
     * {@code docs/design/01-core-resources.md} §3. Invariant I9 then applies to what happens next —
     * cracking the planted miner on your own rig generates <b>no heat on any outcome</b>, so getting
     * counter-hacked hands the player the safest teaching target in the game. On a 0–100 scale, one
     * to three points means the named-hacker band still takes a campaign. <strong>[PROPOSAL]</strong>.
     */
    public static int netCounterHackHeat(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0;
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * A host's one-time payout, by breach tier, in minor units — 3–6 / 7–12 / 14–22 / 26–38 / 45–65 EC.
     *
     * <p>⚠ <b>A stock, not a flow.</b> {@code docs/design/03-economy.md} §5 rule 1 caps new faucets at
     * 70 EC/hr effective; nothing here produces a rate, because each host pays exactly once and the
     * world contains a fixed number of hosts. Home's whole pool is roughly fifteen hosts at a mean of
     * about 4.5 EC — call it 68 EC, which buys the 15 EC Passive Sniffer and the 25 EC wide sweep with
     * change, and is then gone. The deep bands are steep because the risk is: a tier-5 SENTRY at depth
     * 4 is 45–65 EC behind a firewall 2–3, likely tarpitted and canaried, with a 14% chance of being a
     * trap and a 28% chance of hacking you back for merely sweeping near it.
     *
     * <p>Interpolated rather than banded so two hosts of the same tier are rarely worth exactly the
     * same, which is what makes a payout read as a thing found rather than a number awarded.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param u a roll in {@code [0, 1)}
     */
    public static long netLootMinorUnits(int tier, double u) {
        long lo;
        long hi;
        switch (clampTier(tier)) {
            case 1 -> {
                lo = 300L;
                hi = 600L;
            }
            case 2 -> {
                lo = 700L;
                hi = 1_200L;
            }
            case 3 -> {
                lo = 1_400L;
                hi = 2_200L;
            }
            case 4 -> {
                lo = 2_600L;
                hi = 3_800L;
            }
            default -> {
                lo = 4_500L;
                hi = 6_500L;
            }
        }
        return lo + Math.round(Math.max(0.0d, Math.min(1.0d, u)) * (hi - lo));
    }

    /**
     * The chance one sweep tier detects one signal strength at one hop, before the hop factor.
     *
     * <p>⚠ <b>Strictly increasing in tier for every signal, and that is a required property rather
     * than a happy accident.</b> A player who buys a better instrument must never lose a contact they
     * already had — the monotonicity is what makes an upgrade legible, and a test asserts
     * {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} from the same vantage.
     *
     * <p>The signal axis is {@code docs/design/04-mining.md} §2.1's Low / Moderate / High, generalised
     * from miners to hosts: infrastructure is chatty, stores and sentries middling, a citizen's
     * desktop quiet. So the base sweep reliably finds the loud furniture of a network — gateways,
     * relays, bridges — and unreliably finds the machines actually worth breaching, which is what the
     * upgrade is for.
     *
     * <p>⚠ <b>No entry here, at any tier, changes the hop ceiling.</b> That is Invariant I2 made
     * structural: there is no code path from ethecoin to reach, and this method returns a probability
     * that is multiplied by a hop factor after a hard gate has already decided candidacy.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param sweepTier 1, 2 or 3 — see {@code SweepTier}
     * @param signal a {@code SignalStrength.name()}
     */
    public static double netSweepBase(int sweepTier, String signal) {
        String s = signal == null ? "LOW" : signal.trim().toUpperCase(java.util.Locale.ROOT);
        int t = Math.max(1, Math.min(3, sweepTier));
        return switch (s) {
            case "HIGH" -> switch (t) {
                case 1 -> 0.85d;
                case 2 -> 0.95d;
                default -> 0.99d;
            };
            case "MODERATE" -> switch (t) {
                case 1 -> 0.60d;
                case 2 -> 0.78d;
                default -> 0.90d;
            };
            default -> switch (t) {
                case 1 -> 0.35d;
                case 2 -> 0.55d;
                default -> 0.72d;
            };
        };
    }
}
