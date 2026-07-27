package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.solo.Balance;
import java.util.Locale;
import java.util.Optional;

/**
 * The three sweep tiers, and the one thing they all have in common: <b>none of them changes the hop
 * ceiling.</b>
 *
 * <h2>Schematics buy reach, ethecoin buys sensitivity</h2>
 *
 * {@code docs/design/07-recon-tools.md} §2 makes the Topology Mapper "a <b>ceiling</b> on information
 * (1 hop → 2 hops), hence schematic-gated not purchasable (Invariant I2)". Every row here leaves the
 * ceiling at one hop and moves only the <em>probability</em> of detecting what is already in reach —
 * which is breadth, and {@code docs/design/02-unlock-gates.md} §1.1 step 4 puts consumables,
 * replaceables and sidegrades on ethecoin. Invariant I2 is satisfied structurally rather than by
 * discipline: this enum carries no reach value, and {@code NetRules.hopCeiling} does not take a tier.
 *
 * <h2>Gate classification, per {@code 02} §1.1's ordered procedure</h2>
 *
 * <ul>
 *   <li><b>{@link #BASE}</b> — <b>starting kit</b>, the same class as Port Sweep
 *       ({@code docs/design/06-intrusion-tools.md} §2: "the free starting enumerator. Everyone has
 *       it; it's the baseline the Enumeration class is tuned against"). It is not free content, it is
 *       the floor the price of everything else is measured from. Without it a new player has no way to
 *       find the machines next to them, which is the problem this whole system exists to fix.
 *   <li><b>{@link #WIDE}</b>, <b>{@link #DEEP}</b> — <b>ethecoin</b>. They raise no ceiling: no new
 *       hop, no new field, no new class of node. Losing one costs an evening, which is {@code 03}
 *       §2's own rule for the mid-tier band.
 * </ul>
 *
 * <h2>⚠ These ids are deliberately {@code net-sweep*}</h2>
 *
 * Not {@code sweep*}, because {@code port-sweep} already exists in {@code Targets.TOOL_CYCLES} and a
 * near-collision in a map keyed by item id is the kind of bug that shows up as a breach costing three
 * cycles more than the readout said. And these ids must <b>never</b> be added to {@code TOOL_CYCLES}:
 * a sweep tool is not a breach loadout tool, and adding one would silently raise
 * {@code Targets.attemptCycles} for every breach the player ever opens.
 */
public enum SweepTier {

    /** The starting instrument. Two cycles, twenty seconds, one hop, and it finds the loud things. */
    BASE("net-sweep", 1, Balance.NET_SWEEP_BASE_CYCLES, Balance.NET_SWEEP_BASE_SECONDS, "sweep"),

    /** 25 EC. The same distance, listened to harder — the first upgrade a new player buys. */
    WIDE("net-sweep-wide", 2, Balance.NET_SWEEP_WIDE_CYCLES, Balance.NET_SWEEP_WIDE_SECONDS, "sweep --wide"),

    /** 55 EC. Near-certain on infrastructure, and it finally makes quiet desktops reliable. */
    DEEP("net-sweep-deep", 3, Balance.NET_SWEEP_DEEP_CYCLES, Balance.NET_SWEEP_DEEP_SECONDS, "sweep --deep");

    private final String itemId;
    private final int tier;
    private final long cycles;
    private final long seconds;
    private final String label;

    SweepTier(String itemId, int tier, long cycles, long seconds, String label) {
        this.itemId = itemId;
        this.tier = tier;
        this.cycles = cycles;
        this.seconds = seconds;
        this.label = label;
    }

    public String itemId() {
        return itemId;
    }

    /** 1, 2 or 3 — the sensitivity axis {@code Balance.netSweepBase} reads. Never a reach value. */
    public int tier() {
        return tier;
    }

    /**
     * Cycles held for the sweep's whole duration.
     *
     * <p>⚠ Also the sweep's noise, by construction. Reserved as a {@code CONTROL_CHANNEL} allocation,
     * which the implemented noise model counts as "work that reaches other machines"
     * ({@code docs/design/15-open-questions.md} §3, 2026-07-26) — so 2 / 5 / 9 lands as
     * Low / Low / Moderate on {@code docs/design/07-recon-tools.md} §1's scale with no second
     * constant to keep in step.
     */
    public long cycles() {
        return cycles;
    }

    public long seconds() {
        return seconds;
    }

    /** What the readout and the log call it — already in the operator's vocabulary. */
    public String label() {
        return label;
    }

    /**
     * The tier a command-line flag selects: {@code ""}, {@code --wide} or {@code --deep}.
     *
     * <p>Lenient about the leading dashes so {@code sweep wide} works too, and empty rather than
     * throwing on anything else — the shell prints a refusal, and a rules engine that threw on a typo
     * would be deciding how the client reports errors.
     */
    public static Optional<SweepTier> byFlag(String flag) {
        String f = flag == null ? "" : flag.trim().toLowerCase(Locale.ROOT);
        while (f.startsWith("-")) {
            f = f.substring(1);
        }
        return switch (f) {
            case "", "base" -> Optional.of(BASE);
            case "wide" -> Optional.of(WIDE);
            case "deep" -> Optional.of(DEEP);
            default -> Optional.empty();
        };
    }

    public static Optional<SweepTier> byItemId(String id) {
        String wanted = id == null ? "" : id.trim();
        for (SweepTier tier : values()) {
            if (tier.itemId.equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
