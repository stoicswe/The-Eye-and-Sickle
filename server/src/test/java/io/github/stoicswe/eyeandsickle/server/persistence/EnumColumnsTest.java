package io.github.stoicswe.eyeandsickle.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Round-tripping and rejection for every enum vocabulary that reaches a column. */
class EnumColumnsTest {

    @TestFactory
    @DisplayName("every constant of every vocabulary round-trips through its database spelling")
    Stream<DynamicTest> everyConstantRoundTrips() {
        // Explicit type witnesses and explicitly-typed lambdas throughout: each of these vocabularies
        // is an OVERLOADED pair of methods (constant -> text, text -> constant), and leaving the
        // compiler to infer which overload a bare method reference means is a fragile way to write a
        // test whose entire job is to pin down spellings.
        return Stream.of(
                        EnumColumnsTest.<Faction>roundTrip(
                                "faction",
                                Faction.values(),
                                (Faction value) -> EnumColumns.faction(value),
                                (String stored) -> EnumColumns.faction(stored)),
                        EnumColumnsTest.<StorageTier>roundTrip(
                                "storage_tier",
                                StorageTier.values(),
                                (StorageTier value) -> EnumColumns.storageTier(value),
                                (String stored) -> EnumColumns.storageTier(stored)),
                        EnumColumnsTest.<ComputeConsumer>roundTrip(
                                "consumer_type",
                                ComputeConsumer.values(),
                                (ComputeConsumer value) -> EnumColumns.computeConsumer(value),
                                (String stored) -> EnumColumns.computeConsumer(stored)),
                        EnumColumnsTest.<ComputeAllocation.State>roundTrip(
                                "state",
                                ComputeAllocation.State.values(),
                                (ComputeAllocation.State value) -> EnumColumns.allocationState(value),
                                (String stored) -> EnumColumns.allocationState(stored)),
                        EnumColumnsTest.<PuzzleClass>roundTrip(
                                "puzzle_class",
                                PuzzleClass.values(),
                                (PuzzleClass value) -> EnumColumns.puzzleClass(value),
                                (String stored) -> EnumColumns.puzzleClass(stored)),
                        EnumColumnsTest.<TargetState>roundTrip(
                                "live_or_dormant",
                                TargetState.values(),
                                (TargetState value) -> EnumColumns.targetState(value),
                                (String stored) -> EnumColumns.targetState(stored)),
                        EnumColumnsTest.<BreachOutcome>roundTrip(
                                "outcome",
                                BreachOutcome.values(),
                                (BreachOutcome value) -> EnumColumns.breachOutcome(value),
                                (String stored) -> EnumColumns.breachOutcome(stored)),
                        EnumColumnsTest.<ProvenanceEventType>roundTrip(
                                "event_type",
                                ProvenanceEventType.values(),
                                (ProvenanceEventType value) -> EnumColumns.provenanceEventType(value),
                                (String stored) -> EnumColumns.provenanceEventType(stored)))
                .flatMap(Function.identity());
    }

    @Test
    @DisplayName("a value this build does not recognise is rejected, never mapped to a fallback")
    void unknownValuesAreRejected() {
        // Guessing is how an unrecognised state becomes a permitted one. A value we cannot name is a
        // value we cannot apply a rule to.
        assertThatThrownBy(() -> EnumColumns.faction("neutral"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faction")
                .hasMessageContaining("neutral");

        assertThatThrownBy(() -> EnumColumns.storageTier("bot_socket")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.computeConsumer("gpu")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.allocationState("paused")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.puzzleClass("social_engineering"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.targetState("asleep")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.breachOutcome("partial")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.provenanceEventType("gift")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("case matters: the database vocabulary is lowercase and only lowercase")
    void spellingIsExact() {
        // A lenient parse here would let two spellings of the same value coexist in the column, and
        // the CHECK constraint would then be the only thing keeping them out — on the write path only.
        assertThatThrownBy(() -> EnumColumns.faction("EYE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnumColumns.storageTier("VAULT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a NULL column value is reported as such, not passed through")
    void nullValuesAreReported() {
        assertThatThrownBy(() -> EnumColumns.faction((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NULL");
    }

    @Test
    @DisplayName("faction reputation's vocabulary excludes 'none'")
    void factionReputationHasNoNeutralStanding() {
        // Standing with nobody is a category error, not a smaller standing: a player who abandoned a
        // side has zero standing with a NAMED faction (docs/design/01-core-resources.md §5).
        assertThat(EnumColumns.NAMED_FACTION_VALUES).containsExactlyInAnyOrder("eye", "sickle");
        assertThat(EnumColumns.FACTION_VALUES).contains("none");
    }

    @Test
    @DisplayName("provenance event spellings come from the protocol, not from a second copy")
    void provenanceSpellingHasOneAuthority() {
        // These four values live inside signed bytes (docs/architecture/04 §2). A second authority for
        // them would eventually disagree with the first, and the symptom would read as a federation
        // full of cheaters rather than as a bug.
        for (ProvenanceEventType eventType : ProvenanceEventType.values()) {
            assertThat(EnumColumns.provenanceEventType(eventType)).isEqualTo(ProvenanceJson.wireName(eventType));
        }
    }

    @Test
    @DisplayName("the host-side draw of a foreign miner has a name (Invariant I6)")
    void aParasiteIsRepresentable() {
        // Without a spelling for this, a discovered foreign miner cannot be written to the host's
        // ledger at all, and docs/architecture/06 §1 constraint 4 fails. [PROPOSAL] P-9.
        assertThat(EnumColumns.computeConsumer(ComputeConsumer.DEPLOYED_MINER)).isEqualTo("deployed_miner");
        assertThat(EnumColumns.COMPUTE_CONSUMER_VALUES).contains("deployed_miner", "control_channel");
    }

    @Test
    @DisplayName("every vocabulary is complete: one spelling per constant, no collisions")
    void vocabulariesAreComplete() {
        assertThat(EnumColumns.FACTION_VALUES).hasSize(Faction.values().length);
        assertThat(EnumColumns.STORAGE_TIER_VALUES).hasSize(StorageTier.values().length);
        assertThat(EnumColumns.COMPUTE_CONSUMER_VALUES).hasSize(ComputeConsumer.values().length);
        assertThat(EnumColumns.ALLOCATION_STATE_VALUES).hasSize(ComputeAllocation.State.values().length);
        assertThat(EnumColumns.PUZZLE_CLASS_VALUES).hasSize(PuzzleClass.values().length);
        assertThat(EnumColumns.TARGET_STATE_VALUES).hasSize(TargetState.values().length);
        assertThat(EnumColumns.BREACH_OUTCOME_VALUES).hasSize(BreachOutcome.values().length);
        assertThat(EnumColumns.PROVENANCE_EVENT_TYPE_VALUES).hasSize(ProvenanceEventType.values().length);
    }

    private static <E> Stream<DynamicTest> roundTrip(
            String column, E[] constants, Function<E, String> toColumn, Function<String, E> fromColumn) {
        return Arrays.stream(constants)
                .map(constant -> DynamicTest.dynamicTest(column + " <- " + constant, () -> {
                    String stored = toColumn.apply(constant);
                    assertThat(stored).isLowerCase().doesNotContain(" ");
                    assertThat(fromColumn.apply(stored)).isEqualTo(constant);
                }));
    }
}
