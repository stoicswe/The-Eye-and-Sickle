package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The compute ledger: consumers, allocations, and the budget that adds them up.
 *
 * <p>{@code docs/design/01-core-resources.md} §1.4 calls the compute ledger "the game's most
 * important HUD element" and requires total, allocated-by-consumer, available and recovering to be
 * visible at a glance. These tests pin the shape of that readout, and — more importantly — pin the
 * two places where an obvious simplification would delete a mechanic: charging one rig for another's
 * cycles (Invariant I6), and deriving {@code available} instead of reporting it (which would hide a
 * parasite).
 */
class ComputeBudgetTest {

    private static final UUID HOST_RIG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DEPLOYER_RIG = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /** Supplied by the server; this module may never read a clock. */
    private static final Instant RECOVERS_AT = Instant.parse("2026-07-23T18:04:00Z");

    private static UUID id(int n) {
        return UUID.fromString("%08d-0000-4000-8000-000000000000".formatted(n));
    }

    private static ComputeAllocation active(int n, UUID rig, ComputeConsumer consumer, long cycles) {
        return new ComputeAllocation(
                id(n), rig, null, consumer, null, Cycles.of(cycles), ComputeAllocation.State.ACTIVE, null);
    }

    private static ComputeAllocation recovering(int n, UUID rig, ComputeConsumer consumer, long cycles) {
        return new ComputeAllocation(
                id(n), rig, null, consumer, null, Cycles.of(cycles), ComputeAllocation.State.RECOVERING, RECOVERS_AT);
    }

    @Nested
    @DisplayName("the consumer vocabulary")
    class ConsumerVocabulary {

        @Test
        @DisplayName("names every consumer in docs/design/01 §1.1, in HUD order")
        void namesEveryDocumentedConsumer() {
            // containsExactly pins the order too: EnumMap iteration drives the HUD's row order, and a
            // list that reshuffles between refreshes is unreadable at a glance, which is the entire
            // requirement in §1.4.
            assertThat(Arrays.stream(ComputeConsumer.values()).map(Enum::name).toList())
                    .containsExactly(
                            "ACTIVE_TOOL",
                            "BOT_FRAME",
                            "SELF_MINING",
                            "CONTROL_CHANNEL",
                            "DEPLOYED_MINER",
                            "DEFENSIVE_ARRAY",
                            "RELAY_HOP");
        }

        @Test
        @DisplayName("the deployer's control channel and the host's parasite are different consumers")
        void bothSidesOfInvariantI6AreNameable() {
            // If these collapsed into one constant, the host's stolen cycles and the deployer's
            // reservation would be summed somewhere, which is exactly what Invariant I6 forbids.
            assertThat(ComputeConsumer.CONTROL_CHANNEL).isNotEqualTo(ComputeConsumer.DEPLOYED_MINER);
        }
    }

    @Nested
    @DisplayName("an allocation")
    class Allocation {

        @Test
        @DisplayName("requires an identity, a charged rig, a consumer, a quantity and a state")
        void requiredFields() {
            assertThatThrownBy(() -> active(0, null, ComputeConsumer.SELF_MINING, 40))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeAllocation(
                            null,
                            HOST_RIG,
                            null,
                            ComputeConsumer.SELF_MINING,
                            null,
                            Cycles.of(40),
                            ComputeAllocation.State.ACTIVE,
                            null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(1), HOST_RIG, null, null, null, Cycles.of(40), ComputeAllocation.State.ACTIVE, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(1),
                            HOST_RIG,
                            null,
                            ComputeConsumer.SELF_MINING,
                            null,
                            null,
                            ComputeAllocation.State.ACTIVE,
                            null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(1), HOST_RIG, null, ComputeConsumer.SELF_MINING, null, Cycles.of(40), null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a local allocation names one rig")
        void localAllocation() {
            ComputeAllocation selfMining = active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40);

            assertThat(selfMining.crossesRigs()).isFalse();
            assertThat(selfMining.isRecovering()).isFalse();
            assertThat(selfMining.counterpartyRigId()).isNull();
        }

        @Test
        @DisplayName("a cross-rig allocation names the far end without charging it")
        void crossRigAllocation() {
            ComputeAllocation controlChannel = new ComputeAllocation(
                    id(2),
                    DEPLOYER_RIG,
                    HOST_RIG,
                    ComputeConsumer.CONTROL_CHANNEL,
                    id(99),
                    Cycles.of(3),
                    ComputeAllocation.State.ACTIVE,
                    null);

            assertThat(controlChannel.crossesRigs()).isTrue();
            assertThat(controlChannel.chargedRigId()).isEqualTo(DEPLOYER_RIG);
            assertThat(controlChannel.counterpartyRigId()).isEqualTo(HOST_RIG);
        }

        @Test
        @DisplayName("a rig cannot be its own counterparty — that shape is a double charge")
        void selfCounterpartyRejected() {
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(3),
                            HOST_RIG,
                            HOST_RIG,
                            ComputeConsumer.DEPLOYED_MINER,
                            null,
                            Cycles.of(20),
                            ComputeAllocation.State.ACTIVE,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("I6");
        }

        @Test
        @DisplayName("a recovering allocation must say when the cycles come back")
        void recoveringNeedsAnInstant() {
            // §1.4 requires "recovering (with time-to-recover)". A recovering row without one cannot
            // be rendered, and the HUD would have to invent a curve it must not know.
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(4),
                            HOST_RIG,
                            null,
                            ComputeConsumer.ACTIVE_TOOL,
                            null,
                            Cycles.of(35),
                            ComputeAllocation.State.RECOVERING,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("recoversAt");
        }

        @Test
        @DisplayName("an active allocation must not carry a recovery instant")
        void activeMustNotCarryAnInstant() {
            assertThatThrownBy(() -> new ComputeAllocation(
                            id(5),
                            HOST_RIG,
                            null,
                            ComputeConsumer.ACTIVE_TOOL,
                            null,
                            Cycles.of(35),
                            ComputeAllocation.State.ACTIVE,
                            RECOVERS_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a recovering allocation carries the server's instant verbatim")
        void recoveringCarriesTheInstant() {
            ComputeAllocation spent = recovering(6, HOST_RIG, ComputeConsumer.ACTIVE_TOOL, 35);

            assertThat(spent.isRecovering()).isTrue();
            assertThat(spent.recoversAt()).isEqualTo(RECOVERS_AT);
        }
    }

    @Nested
    @DisplayName("a budget")
    class Budget {

        @Test
        @DisplayName("reports allocated, recovering and available as three separate quantities")
        void reportsAllThreeQuantities() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(25),
                    List.of(
                            active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40),
                            recovering(2, HOST_RIG, ComputeConsumer.ACTIVE_TOOL, 35)));

            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.recovering()).isEqualTo(Cycles.of(35));
            assertThat(budget.available()).isEqualTo(Cycles.of(25));
        }

        @Test
        @DisplayName("allocated + recovering + available reconciles against the total")
        void reconcilesAgainstTotal() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(25),
                    List.of(
                            active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40),
                            recovering(2, HOST_RIG, ComputeConsumer.ACTIVE_TOOL, 35)));

            assertThat(budget.allocated().plus(budget.recovering()).plus(budget.available()))
                    .isEqualTo(budget.total());
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.ZERO);
            assertThat(budget.reconciles()).isTrue();
        }

        @Test
        @DisplayName("an empty rig is all available")
        void emptyRig() {
            ComputeBudget budget = new ComputeBudget(HOST_RIG, Cycles.of(100), Cycles.of(100), List.of());

            assertThat(budget.allocated()).isEqualTo(Cycles.ZERO);
            assertThat(budget.recovering()).isEqualTo(Cycles.ZERO);
            assertThat(budget.allocatedByConsumer()).isEmpty();
            assertThat(budget.reconciles()).isTrue();
        }

        @Test
        @DisplayName("cycles nothing accounts for are surfaced, not smoothed away")
        void unaccountedCyclesAreVisible() {
            // A rootkit-wrapped miner steals 20 of the host's cycles without appearing in the host's
            // allocation list (docs/design/09). The server reports a smaller `available`; the gap is
            // the only trace, and finding it is the manual-audit loop (docs/design/04 §3.1).
            ComputeBudget raided = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(40),
                    List.of(active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40)));

            assertThat(raided.unaccountedFor()).isEqualTo(Cycles.of(20));
            assertThat(raided.reconciles()).isFalse();
        }

        @Test
        @DisplayName("more capacity in play than the rig has is rejected")
        void overSubscriptionRejected() {
            assertThatThrownBy(() -> new ComputeBudget(
                            HOST_RIG,
                            Cycles.of(100),
                            Cycles.of(30),
                            List.of(
                                    active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40),
                                    active(2, HOST_RIG, ComputeConsumer.BOT_FRAME, 40))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("a ledger holds only rows charged to its own rig")
        void foreignRowsRejected() {
            assertThatThrownBy(() -> new ComputeBudget(
                            HOST_RIG,
                            Cycles.of(100),
                            Cycles.of(97),
                            List.of(active(1, DEPLOYER_RIG, ComputeConsumer.CONTROL_CHANNEL, 3))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("charged to rig");
        }

        @Test
        @DisplayName("requires a rig, a total, an available figure and a list")
        void requiredFields() {
            assertThatThrownBy(() -> new ComputeBudget(null, Cycles.of(100), Cycles.of(100), List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeBudget(HOST_RIG, null, Cycles.of(100), List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeBudget(HOST_RIG, Cycles.of(100), null, List.of()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeBudget(HOST_RIG, Cycles.of(100), Cycles.of(100), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the allocation list is defensively copied")
        void allocationsAreImmutable() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(60),
                    List.of(active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40)));

            assertThatThrownBy(() -> budget.allocations().add(active(2, HOST_RIG, ComputeConsumer.BOT_FRAME, 10)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("the by-consumer breakdown")
    class ByConsumer {

        @Test
        @DisplayName("sums each consumer's active rows and ignores recovering ones")
        void sumsPerConsumer() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(10),
                    List.of(
                            active(1, HOST_RIG, ComputeConsumer.BOT_FRAME, 15),
                            active(2, HOST_RIG, ComputeConsumer.BOT_FRAME, 15),
                            active(3, HOST_RIG, ComputeConsumer.SELF_MINING, 40),
                            recovering(4, HOST_RIG, ComputeConsumer.ACTIVE_TOOL, 20)));

            assertThat(budget.allocatedByConsumer())
                    .containsOnly(
                            entry(ComputeConsumer.BOT_FRAME, Cycles.of(30)),
                            entry(ComputeConsumer.SELF_MINING, Cycles.of(40)));
            assertThat(budget.recovering()).isEqualTo(Cycles.of(20));
        }

        @Test
        @DisplayName("iterates in declaration order regardless of the order rows arrived in")
        void iteratesInDeclarationOrder() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(52),
                    List.of(
                            active(1, HOST_RIG, ComputeConsumer.RELAY_HOP, 5),
                            active(2, HOST_RIG, ComputeConsumer.DEFENSIVE_ARRAY, 8),
                            active(3, HOST_RIG, ComputeConsumer.ACTIVE_TOOL, 35)));

            assertThat(budget.allocatedByConsumer())
                    .containsExactly(
                            entry(ComputeConsumer.ACTIVE_TOOL, Cycles.of(35)),
                            entry(ComputeConsumer.DEFENSIVE_ARRAY, Cycles.of(8)),
                            entry(ComputeConsumer.RELAY_HOP, Cycles.of(5)));
        }

        @Test
        @DisplayName("the breakdown is not modifiable by the renderer that reads it")
        void breakdownIsUnmodifiable() {
            ComputeBudget budget = new ComputeBudget(
                    HOST_RIG,
                    Cycles.of(100),
                    Cycles.of(60),
                    List.of(active(1, HOST_RIG, ComputeConsumer.SELF_MINING, 40)));

            assertThatThrownBy(() -> budget.allocatedByConsumer().put(ComputeConsumer.BOT_FRAME, Cycles.of(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Invariant I6 — a deployed miner costs the host, the deployer pays a control channel")
    class InvariantI6 {

        @Test
        @DisplayName("the two costs live on two rigs and are never summed into one")
        void twoRigsTwoRows() {
            ComputeAllocation parasite = new ComputeAllocation(
                    id(10),
                    HOST_RIG,
                    DEPLOYER_RIG,
                    ComputeConsumer.DEPLOYED_MINER,
                    id(50),
                    Cycles.of(20),
                    ComputeAllocation.State.ACTIVE,
                    null);
            ComputeAllocation controlChannel = new ComputeAllocation(
                    id(11),
                    DEPLOYER_RIG,
                    HOST_RIG,
                    ComputeConsumer.CONTROL_CHANNEL,
                    id(50),
                    Cycles.of(3),
                    ComputeAllocation.State.ACTIVE,
                    null);

            ComputeBudget host = new ComputeBudget(HOST_RIG, Cycles.of(100), Cycles.of(80), List.of(parasite));
            ComputeBudget deployer =
                    new ComputeBudget(DEPLOYER_RIG, Cycles.of(100), Cycles.of(97), List.of(controlChannel));

            assertThat(host.allocated()).isEqualTo(Cycles.of(20));
            assertThat(host.allocatedByConsumer()).containsOnlyKeys(ComputeConsumer.DEPLOYED_MINER);
            assertThat(deployer.allocated()).isEqualTo(Cycles.of(3));
            assertThat(deployer.allocatedByConsumer()).containsOnlyKeys(ComputeConsumer.CONTROL_CHANNEL);

            // Both rows point at the same miner, and each rig pays only its own share.
            assertThat(parasite.consumerRef()).isEqualTo(controlChannel.consumerRef());
        }

        @Test
        @DisplayName("the deployer's control channel cannot be filed against the host's ledger")
        void controlChannelCannotBeChargedToTheHost() {
            ComputeAllocation controlChannel = new ComputeAllocation(
                    id(12),
                    DEPLOYER_RIG,
                    HOST_RIG,
                    ComputeConsumer.CONTROL_CHANNEL,
                    id(50),
                    Cycles.of(3),
                    ComputeAllocation.State.ACTIVE,
                    null);

            assertThatThrownBy(
                            () -> new ComputeBudget(HOST_RIG, Cycles.of(100), Cycles.of(97), List.of(controlChannel)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
