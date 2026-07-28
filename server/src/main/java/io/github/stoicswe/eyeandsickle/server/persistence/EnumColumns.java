package io.github.stoicswe.eyeandsickle.server.persistence;

import io.github.stoicswe.eyeandsickle.protocol.game.BreachOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.game.TargetState;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEventType;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceJson;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The only place a protocol enum is spelled as a database value.
 *
 * <h2>Why {@code text} + CHECK, and not a PostgreSQL {@code ENUM} type</h2>
 *
 * PostgreSQL enum values are cheap to add and effectively impossible to remove or rename — every
 * change is a migration that rewrites dependent objects. Half these vocabularies are still tagged
 * {@code [PROPOSAL]}: the five puzzle classes may become two or three (open question P-1), the 1–5
 * difficulty range may widen (P-10), {@code DEPLOYED_MINER} as a consumer is itself a proposal (P-9).
 * Text plus a named CHECK constraint keeps those changes to a one-line migration.
 *
 * <h2>Why exhaustive switches, and not {@code name().toLowerCase()}</h2>
 *
 * A convention-derived spelling makes renaming a Java constant a silent database-vocabulary change:
 * the build stays green, the CHECK constraint starts rejecting rows at runtime, and on a self-hosted
 * server that surfaces as somebody's data quietly failing to save. An exhaustive switch over an enum
 * is a compile error the moment a constant is added or renamed — the same discipline, and the same
 * reasoning, as protocol {@code ProvenanceJson}'s event-type mapping.
 *
 * <p>Provenance event types are not re-spelled here; they are delegated to {@link ProvenanceJson},
 * because those four values are fixed by {@code docs/architecture/04-item-provenance.md} §2 and
 * appear inside signed bytes. One authority for a wire spelling, not two.
 *
 * <h2>The vocabulary sets</h2>
 *
 * Each {@code *_VALUES} set is the complete database vocabulary for that column.
 * {@code SchemaVocabularyTest} reads the migration SQL and asserts that each CHECK constraint lists
 * exactly the corresponding set — so Java and SQL cannot drift apart without a red build, and without
 * anyone needing a database to find out.
 */
public final class EnumColumns {

    private EnumColumns() {}

    // ------------------------------------------------------------------ faction

    /** Database vocabulary for {@code players.faction}. */
    public static final Set<String> FACTION_VALUES = valuesOf(Faction.values(), EnumColumns::faction);

    /**
     * Database vocabulary for {@code faction_reputations.faction}: the named factions only.
     *
     * <p>{@link Faction#NONE} is excluded deliberately. Standing with nobody is a category error, not
     * a smaller standing — a player who abandoned a side has zero standing with a <em>named</em>
     * faction, which is the same rule protocol {@code FactionReputation} enforces.
     */
    public static final Set<String> NAMED_FACTION_VALUES = Set.of(faction(Faction.EYE), faction(Faction.SICKLE));

    /**
     * @param faction the constant
     * @return its database spelling
     */
    public static String faction(Faction faction) {
        Objects.requireNonNull(faction, "faction");
        return switch (faction) {
            case EYE -> "eye";
            case SICKLE -> "sickle";
            case NONE -> "none";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static Faction faction(String value) {
        return switch (require(value, "faction")) {
            case "eye" -> Faction.EYE;
            case "sickle" -> Faction.SICKLE;
            case "none" -> Faction.NONE;
            default -> throw unknown("faction", value, FACTION_VALUES);
        };
    }

    // ------------------------------------------------------------------ storage tier

    /** Database vocabulary for {@code items.storage_tier}. */
    public static final Set<String> STORAGE_TIER_VALUES = valuesOf(StorageTier.values(), EnumColumns::storageTier);

    /**
     * @param tier the constant
     * @return its database spelling
     */
    public static String storageTier(StorageTier tier) {
        Objects.requireNonNull(tier, "tier");
        return switch (tier) {
            case VAULT -> "vault";
            case STANDARD_STORAGE -> "standard_storage";
            case HIGH_HACKABLE_ZONE -> "high_hackable_zone";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static StorageTier storageTier(String value) {
        return switch (require(value, "storage_tier")) {
            case "vault" -> StorageTier.VAULT;
            case "standard_storage" -> StorageTier.STANDARD_STORAGE;
            case "high_hackable_zone" -> StorageTier.HIGH_HACKABLE_ZONE;
            default -> throw unknown("storage_tier", value, STORAGE_TIER_VALUES);
        };
    }

    // ------------------------------------------------------------------ compute consumer

    /** Database vocabulary for {@code compute_allocations.consumer_type}. */
    public static final Set<String> COMPUTE_CONSUMER_VALUES =
            valuesOf(ComputeConsumer.values(), EnumColumns::computeConsumer);

    /**
     * @param consumer the constant
     * @return its database spelling
     */
    public static String computeConsumer(ComputeConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return switch (consumer) {
            case ACTIVE_TOOL -> "active_tool";
            case BOT_FRAME -> "bot_frame";
            case SELF_MINING -> "self_mining";
            case CONTROL_CHANNEL -> "control_channel";
            // The host-side draw of a foreign miner. Invariant I6 charges it to the HOST's rig; the
            // deployer's CONTROL_CHANNEL row is a different row on a different rig, never the same
            // allocation counted twice.
            case DEPLOYED_MINER -> "deployed_miner";
            case DEFENSIVE_ARRAY -> "defensive_array";
            case RELAY_HOP -> "relay_hop";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static ComputeConsumer computeConsumer(String value) {
        return switch (require(value, "consumer_type")) {
            case "active_tool" -> ComputeConsumer.ACTIVE_TOOL;
            case "bot_frame" -> ComputeConsumer.BOT_FRAME;
            case "self_mining" -> ComputeConsumer.SELF_MINING;
            case "control_channel" -> ComputeConsumer.CONTROL_CHANNEL;
            case "deployed_miner" -> ComputeConsumer.DEPLOYED_MINER;
            case "defensive_array" -> ComputeConsumer.DEFENSIVE_ARRAY;
            case "relay_hop" -> ComputeConsumer.RELAY_HOP;
            default -> throw unknown("consumer_type", value, COMPUTE_CONSUMER_VALUES);
        };
    }

    // ------------------------------------------------------------------ allocation state

    /** Database vocabulary for {@code compute_allocations.state}. */
    public static final Set<String> ALLOCATION_STATE_VALUES =
            valuesOf(ComputeAllocation.State.values(), EnumColumns::allocationState);

    /**
     * @param state the constant
     * @return its database spelling
     */
    public static String allocationState(ComputeAllocation.State state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case ACTIVE -> "active";
            case RECOVERING -> "recovering";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static ComputeAllocation.State allocationState(String value) {
        return switch (require(value, "state")) {
            case "active" -> ComputeAllocation.State.ACTIVE;
            case "recovering" -> ComputeAllocation.State.RECOVERING;
            default -> throw unknown("state", value, ALLOCATION_STATE_VALUES);
        };
    }

    // ------------------------------------------------------------------ breach vocabulary

    /**
     * Database vocabulary for {@code breach_resolutions.puzzle_class}.
     *
     * <p>Two values since V4, where {@code docs/design/15-open-questions.md} P-1 was resolved against
     * five. ⚠ The retired spellings are not accepted here even though rows carrying them may exist in
     * a backup: V4 rewrites them on the way in, and a reader that quietly understood a value no
     * writer produces would let a half-migrated database look healthy.
     */
    public static final Set<String> PUZZLE_CLASS_VALUES = valuesOf(PuzzleClass.values(), EnumColumns::puzzleClass);

    /**
     * @param puzzleClass the constant
     * @return its database spelling
     */
    public static String puzzleClass(PuzzleClass puzzleClass) {
        Objects.requireNonNull(puzzleClass, "puzzleClass");
        return switch (puzzleClass) {
            case BREACH_PROTOCOL -> "breach_protocol";
            case OFFSET_CIPHER -> "offset_cipher";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static PuzzleClass puzzleClass(String value) {
        return switch (require(value, "puzzle_class")) {
            case "breach_protocol" -> PuzzleClass.BREACH_PROTOCOL;
            case "offset_cipher" -> PuzzleClass.OFFSET_CIPHER;
            default -> throw unknown("puzzle_class", value, PUZZLE_CLASS_VALUES);
        };
    }

    /** Database vocabulary for {@code breach_resolutions.live_or_dormant}. */
    public static final Set<String> TARGET_STATE_VALUES = valuesOf(TargetState.values(), EnumColumns::targetState);

    /**
     * @param state the constant
     * @return its database spelling
     */
    public static String targetState(TargetState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            // Only LIVE can earn proof-of-skill credit (Invariant I7). Stored as a named value rather
            // than a boolean precisely so an inverted flag cannot silently hand out unlocks.
            case LIVE -> "live";
            case DORMANT -> "dormant";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static TargetState targetState(String value) {
        return switch (require(value, "live_or_dormant")) {
            case "live" -> TargetState.LIVE;
            case "dormant" -> TargetState.DORMANT;
            default -> throw unknown("live_or_dormant", value, TARGET_STATE_VALUES);
        };
    }

    /** Database vocabulary for {@code breach_resolutions.outcome}. */
    public static final Set<String> BREACH_OUTCOME_VALUES =
            valuesOf(BreachOutcome.values(), EnumColumns::breachOutcome);

    /**
     * @param outcome the constant
     * @return its database spelling
     */
    public static String breachOutcome(BreachOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return switch (outcome) {
            case BREACHED -> "breached";
            case FAILED -> "failed";
            case ABORTED -> "aborted";
        };
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not in the vocabulary
     */
    public static BreachOutcome breachOutcome(String value) {
        return switch (require(value, "outcome")) {
            case "breached" -> BreachOutcome.BREACHED;
            case "failed" -> BreachOutcome.FAILED;
            case "aborted" -> BreachOutcome.ABORTED;
            default -> throw unknown("outcome", value, BREACH_OUTCOME_VALUES);
        };
    }

    // ------------------------------------------------------------------ provenance event type

    /** Database vocabulary for {@code provenance_records.event_type}. */
    public static final Set<String> PROVENANCE_EVENT_TYPE_VALUES =
            valuesOf(ProvenanceEventType.values(), EnumColumns::provenanceEventType);

    /**
     * Delegates to {@link ProvenanceJson#wireName(ProvenanceEventType)}.
     *
     * <p>These four spellings live inside signed bytes ({@code docs/architecture/04} §2). Re-declaring
     * them here would create a second authority for a wire format, and the two would eventually
     * disagree in a way that reads as a federation full of cheaters rather than as a bug.
     *
     * @param eventType the constant
     * @return its database spelling, which is also its wire spelling
     */
    public static String provenanceEventType(ProvenanceEventType eventType) {
        return ProvenanceJson.wireName(eventType);
    }

    /**
     * @param value a stored value
     * @return the constant it names
     * @throws IllegalArgumentException if the value is not one of the four defined events
     */
    public static ProvenanceEventType provenanceEventType(String value) {
        return ProvenanceJson.eventType(require(value, "event_type"));
    }

    // ------------------------------------------------------------------ internals

    private static <E> Set<String> valuesOf(E[] constants, Function<E, String> spelling) {
        // LinkedHashSet wrapped unmodifiable, NOT Set.copyOf: copyOf discards iteration order, and the
        // order is worth keeping so a failure message reads in the same order as the enum declaration
        // and as the CHECK constraint in the migration.
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(constants).map(spelling).forEach(values::add);
        return Collections.unmodifiableSet(values);
    }

    private static String require(String value, String column) {
        if (value == null) {
            throw new IllegalArgumentException("Column '" + column + "' was NULL where a value was required");
        }
        return value;
    }

    private static IllegalArgumentException unknown(String column, String value, Set<String> vocabulary) {
        // Rejected, never mapped to an "unknown" constant. A value this build does not understand is a
        // value it cannot apply a rule to, and guessing is how an unrecognised state becomes a
        // permitted one.
        return new IllegalArgumentException(
                "Column '" + column + "' holds '" + value + "', which is not in its vocabulary " + vocabulary
                        + ". Either a migration added a value this build predates, or the row was written by"
                        + " something that bypassed EnumColumns.");
    }
}
