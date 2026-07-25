package io.github.stoicswe.eyeandsickle.server.economy;

import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one place the economy slice's calibrated numbers reach the code.
 *
 * <h2>Why a properties class and not scattered constants</h2>
 *
 * {@code CLAUDE.md}'s working agreements: the figures in {@code docs/design/03-economy.md} and
 * {@code docs/design/01-core-resources.md} are calibrated <em>as a set</em>, so changing one means
 * re-checking the others. A number copied into three services in three packages cannot be re-checked;
 * a number in one bound properties class can, and a self-hoster can tune it without a rebuild. This
 * mirrors {@code persistence/PersistenceProperties}, deliberately.
 *
 * <h2>Everything here is a {@code [PROPOSAL]}</h2>
 *
 * The storage capacities below come from {@code docs/design/01-core-resources.md} §6's first-pass
 * proposal ("Vault 6 / Standard 20 / High-Hackable 60", expansion {@code +4,+3,+2,+1}, hard cap 16).
 * The doc is explicit that the <em>numbers</em> are for playtest and only the <em>shape</em> — vault
 * capacity scaling <strong>sub-linearly</strong> (Invariant I12) — is established. So the defaults
 * encode the proposal and the shape is enforced by {@link #vaultSlots(int)} constructing capacity
 * from a decreasing increment list rather than from a formula anyone could make linear by accident.
 *
 * <h2>What is deliberately not here</h2>
 *
 * No prices, no reputation thresholds, no proof-of-skill tiers, no heat thresholds. Those are
 * <em>per-offering</em> values that belong to the gate/catalogue definitions ({@code
 * docs/design/02-unlock-gates.md} §5 asks a designer to name them per item), not to a global bag of
 * economy constants — which is the same scattering problem with a tidier name. This class holds only
 * the capacity schedule, because a storage tier's capacity is a property of the tier, not of any one
 * item placed in it.
 *
 * @param vaultBaseSlots capacity of a fresh Encrypted Vault, before any Cold Storage Expansion
 *     ({@code docs/design/01-core-resources.md} §6). Never purchasable (Invariant I12); expansion is
 *     schematic + reputation gated.
 * @param vaultExpansionIncrements the sub-linear expansion schedule: the slots each successive Cold
 *     Storage Expansion adds. Must be non-increasing so capacity growth is sub-linear
 *     <em>by construction</em> — the established half of §6, the guard against the late-game
 *     unraidable veteran.
 * @param vaultHardCapSlots the ceiling the schedule may never exceed, however many expansions a player
 *     stacks. A backstop even if the increment list is misconfigured.
 * @param standardStorageSlots capacity of Standard Storage — larger than the vault, exposed while the
 *     owner is online (§6).
 * @param highHackableZoneSlots capacity of the High-Hackable Zone — largest, always exposed and
 *     raidable even while the owner is offline (§6).
 */
@ConfigurationProperties(prefix = "eyeandsickle.economy")
public record EconomyProperties(
        Integer vaultBaseSlots,
        List<Integer> vaultExpansionIncrements,
        Integer vaultHardCapSlots,
        Integer standardStorageSlots,
        Integer highHackableZoneSlots) {

    /** {@code docs/design/01-core-resources.md} §6 first pass. [PROPOSAL] — playtest figure. */
    public static final int DEFAULT_VAULT_BASE_SLOTS = 6;

    /** The sub-linear expansion schedule {@code +4,+3,+2,+1}. [PROPOSAL] — §6, playtest figure. */
    public static final List<Integer> DEFAULT_VAULT_EXPANSION_INCREMENTS = List.of(4, 3, 2, 1);

    /** {@code 6 + 4 + 3 + 2 + 1 = 16}. [PROPOSAL] — §6 "hard cap 16". */
    public static final int DEFAULT_VAULT_HARD_CAP_SLOTS = 16;

    /** {@code docs/design/01-core-resources.md} §6 first pass. [PROPOSAL] — playtest figure. */
    public static final int DEFAULT_STANDARD_STORAGE_SLOTS = 20;

    /** {@code docs/design/01-core-resources.md} §6 first pass. [PROPOSAL] — playtest figure. */
    public static final int DEFAULT_HIGH_HACKABLE_ZONE_SLOTS = 60;

    public EconomyProperties {
        vaultBaseSlots = vaultBaseSlots == null ? DEFAULT_VAULT_BASE_SLOTS : vaultBaseSlots;
        vaultExpansionIncrements = vaultExpansionIncrements == null || vaultExpansionIncrements.isEmpty()
                ? DEFAULT_VAULT_EXPANSION_INCREMENTS
                : List.copyOf(vaultExpansionIncrements);
        vaultHardCapSlots = vaultHardCapSlots == null ? DEFAULT_VAULT_HARD_CAP_SLOTS : vaultHardCapSlots;
        standardStorageSlots = standardStorageSlots == null ? DEFAULT_STANDARD_STORAGE_SLOTS : standardStorageSlots;
        highHackableZoneSlots =
                highHackableZoneSlots == null ? DEFAULT_HIGH_HACKABLE_ZONE_SLOTS : highHackableZoneSlots;

        if (vaultBaseSlots <= 0 || standardStorageSlots <= 0 || highHackableZoneSlots <= 0) {
            throw new IllegalArgumentException(
                    "Every storage tier must have positive capacity; a zero-slot tier is a place items "
                            + "silently cannot go. Was vault=" + vaultBaseSlots + " standard=" + standardStorageSlots
                            + " high-hackable=" + highHackableZoneSlots);
        }
        // Non-increasing is the established half of §6: sub-linear scaling by construction. A later
        // increment larger than an earlier one is super-linear growth sneaking in, which is exactly the
        // "late-game unraidable veteran" Invariant I12 exists to prevent.
        int previous = Integer.MAX_VALUE;
        for (Integer increment : vaultExpansionIncrements) {
            if (increment == null || increment < 0) {
                throw new IllegalArgumentException(
                        "Vault expansion increments must be non-negative; was " + vaultExpansionIncrements);
            }
            if (increment > previous) {
                throw new IllegalArgumentException(
                        "Vault expansion increments must be non-increasing so capacity stays sub-linear "
                                + "(Invariant I12); was " + vaultExpansionIncrements);
            }
            previous = increment;
        }
        if (vaultHardCapSlots < vaultBaseSlots) {
            throw new IllegalArgumentException("Vault hard cap (" + vaultHardCapSlots
                    + ") cannot be below the base capacity (" + vaultBaseSlots + ")");
        }
    }

    /**
     * The vault's capacity at a given Cold Storage Expansion level.
     *
     * <p>Level 0 is a fresh vault ({@link #vaultBaseSlots}). Each level consumes one increment from
     * {@link #vaultExpansionIncrements}, in order; levels beyond the list add nothing (the schedule is
     * exhausted). The result is clamped to {@link #vaultHardCapSlots} so no configuration can make the
     * vault grow without bound.
     *
     * <p>The expansion level is <em>schematic-derived</em> state, supplied by the progression slice —
     * it is never a function of ethecoin, which is what keeps capacity unpurchasable (Invariant I12).
     *
     * @param expansionLevel how many Cold Storage Expansions the holder has installed; never negative
     * @return the vault slot count at that level
     * @throws IllegalArgumentException if the level is negative
     */
    public int vaultSlots(int expansionLevel) {
        if (expansionLevel < 0) {
            throw new IllegalArgumentException("Vault expansion level is never negative, was " + expansionLevel);
        }
        int slots = vaultBaseSlots;
        int applied = Math.min(expansionLevel, vaultExpansionIncrements.size());
        for (int i = 0; i < applied; i++) {
            slots += vaultExpansionIncrements.get(i);
        }
        return Math.min(slots, vaultHardCapSlots);
    }

    /**
     * Capacity of a tier for a holder at a given vault-expansion level.
     *
     * <p>Only {@link StorageTier#VAULT} depends on the expansion level; Standard Storage and the
     * High-Hackable Zone are flat. Routed through one method so a caller never has to remember which
     * tiers scale.
     *
     * @param tier the tier
     * @param vaultExpansionLevel the holder's Cold Storage Expansion level; ignored for non-vault tiers
     * @return the capacity in slots
     */
    public int slotsFor(StorageTier tier, int vaultExpansionLevel) {
        return switch (tier) {
            case VAULT -> vaultSlots(vaultExpansionLevel);
            case STANDARD_STORAGE -> standardStorageSlots;
            case HIGH_HACKABLE_ZONE -> highHackableZoneSlots;
        };
    }
}
