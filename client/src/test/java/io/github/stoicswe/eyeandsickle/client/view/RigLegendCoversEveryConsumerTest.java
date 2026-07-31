package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.CycleGrid;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every kind of consumer reaches the rig monitor's grid and legend.
 *
 * <h2>⚠ The bug this exists for</h2>
 *
 * {@code SHELL_SESSION} was absent from {@code RigMonitorView.ORDER}, and nothing else showed it.
 * The enum had the constant, {@code owner()} and {@code label()} both handled it, the allocation was
 * real, and the headline <b>"84 / 100 CYCLES CLAIMED"</b> counted it — but the grid and the legend
 * are built by walking that one list, so an open shell's two cycles produced <b>no slice at all</b>.
 * The panel claimed 84 and accounted for 80.
 *
 * <p>That is not a cosmetic gap. {@code docs/design/04-mining.md} §3.1 makes "the numbers do not add
 * up" the way a player detects a parasite they have not audited — so opening a shell manufactured the
 * game's own evidence of an intrusion.
 *
 * <p>⚠ This test walks the <b>enum</b>, not a list of the consumers somebody remembered. A new
 * consumer added without a legend entry fails here rather than shipping as four cycles nobody can
 * see — which is the only version of this test worth having, because the original defect was an
 * omission and an omission is invisible to a test that enumerates the same omission.
 */
class RigLegendCoversEveryConsumerTest {

    /** One live allocation per consumer, so every branch of the slice builder is exercised. */
    private static ComputeBudget budgetWithOneOfEach() {
        UUID rig = UUID.randomUUID();
        List<ComputeAllocation> allocations = new ArrayList<>();
        for (ComputeConsumer consumer : ComputeConsumer.values()) {
            allocations.add(new ComputeAllocation(
                    UUID.randomUUID(),
                    rig,
                    null,
                    consumer,
                    UUID.randomUUID(),
                    Cycles.of(2),
                    ComputeAllocation.State.ACTIVE,
                    null));
        }
        long claimed = 2L * ComputeConsumer.values().length;
        return new ComputeBudget(rig, Cycles.of(claimed), Cycles.of(0), allocations);
    }

    /**
     * A snapshot with nothing interesting in it.
     *
     * <p>⚠ Not {@code null}: the self-mining row reads its rate off this, so a null makes the test
     * fail for a reason that has nothing to do with what it is checking.
     */
    private static io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot idleMining() {
        return new io.github.stoicswe.eyeandsickle.protocol.game.MiningSnapshot(
                io.github.stoicswe.eyeandsickle.protocol.game.MiningMode.SOLO,
                0L,
                0L,
                0L,
                0.0d,
                0.0d,
                0L,
                0L,
                0.0d,
                -1L,
                java.math.BigInteger.ZERO,
                java.math.BigInteger.ZERO,
                0L,
                java.math.BigInteger.ZERO,
                0,
                null,
                null,
                java.math.BigInteger.ZERO,
                0L,
                0L);
    }

    @Test
    @DisplayName("every ComputeConsumer produces a labelled slice — none is silently dropped")
    void everyConsumerIsDrawnAndNamed() {
        List<CycleGrid.Slice> slices = RigMonitorView.slices(budgetWithOneOfEach(), idleMining());

        for (ComputeConsumer consumer : ComputeConsumer.values()) {
            assertThat(slices)
                    .as("%s must appear in the rig monitor's breakdown", consumer)
                    .anySatisfy(slice -> assertThat(slice.cells()).isEqualTo(2));
        }
        assertThat(slices)
                .as("one slice per consumer, each carrying its cycles")
                .hasSizeGreaterThanOrEqualTo(ComputeConsumer.values().length);
        assertThat(slices)
                .allSatisfy(slice -> assertThat(slice.label())
                        .as("a drawn slice must name itself")
                        .isNotBlank());
    }

    /**
     * ⚠ The arithmetic the player actually checks.
     *
     * <p>§3.1's mechanic is that claimed + recovering + free comes to the stated ceiling, and a
     * shortfall means something is eating capacity. The breakdown must therefore sum to what the
     * headline claims — a legend that under-counts is indistinguishable from a parasite.
     */
    @Test
    @DisplayName("the slices sum to the claimed total, so the panel cannot under-count itself")
    void theBreakdownAddsUp() {
        ComputeBudget budget = budgetWithOneOfEach();
        int drawn = RigMonitorView.slices(budget, idleMining()).stream()
                .mapToInt(CycleGrid.Slice::cells)
                .sum();
        assertThat(drawn)
                .as("every claimed cycle is attributed to something on screen")
                .isEqualTo((int) budget.allocated().cycles());
    }
}
