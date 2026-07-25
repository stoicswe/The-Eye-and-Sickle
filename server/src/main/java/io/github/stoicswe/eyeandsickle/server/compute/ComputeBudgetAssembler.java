package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.util.List;
import java.util.Objects;

/**
 * Builds the wire-facing {@link ComputeBudget} — the §1.4 HUD readout — from the authoritative
 * reconciliation and the allocations the server chose to disclose. Pure and side-effect free, so the
 * one piece of logic that decides what the player's most important HUD element says can be unit-tested
 * exhaustively without a database.
 *
 * <h2>Why {@code available} comes from the reconciliation, not from the disclosed rows</h2>
 *
 * This is the crux of the manual-audit design ({@code docs/design/04-mining.md} §3.1). The budget's
 * {@code available} is the <em>true</em> free capacity, computed by the {@code
 * rig_compute_reconciliation} view over <b>every</b> allocation — including any a rootkit-wrapped miner
 * keeps out of the disclosed list. The disclosed list, by contrast, is only what the owner may see. So
 * when a row is hidden, {@code disclosedActive + disclosedRecovering + available} falls short of
 * {@code total} by exactly the hidden amount, and {@link ComputeBudget#unaccountedFor()} surfaces that
 * gap. Deriving {@code available} from the disclosed rows instead would make the numbers always add up
 * and silently delete the audit signal.
 *
 * <p>Algebra worth stating, because it is what guarantees the protocol invariant holds:
 * {@code accounted = disclosedActive + disclosedRecovering + available = total - (hiddenActive +
 * hiddenRecovering) <= total}, always, because hidden amounts are non-negative. The budget therefore
 * never over-reconciles, and it reconciles <em>exactly</em> when nothing is hidden.
 *
 * <h2>The over-subscription boundary</h2>
 *
 * {@link RigComputeReconciliation#availableCycles()} is signed and can be negative on a rig a parasite
 * has pushed past its ceiling (Invariant I6). {@link ComputeBudget#available()} is a non-negative
 * {@link Cycles}, so it is fed {@link RigComputeReconciliation#availableForAllocation()} (clamped at
 * zero): the HUD reports "nothing free". The raw negative is not lost — it is exposed through {@link
 * RigComputeReconciliation} itself, which the audit endpoint returns unclamped. A visible parasite that
 * over-subscribes a rig is a state the protocol type deliberately cannot represent; the disclosure
 * policy hides such parasites (rootkit-wrapping is what lets one over-subscribe undetected), so the
 * disclosed set stays within the ceiling and the budget stays constructible.
 */
final class ComputeBudgetAssembler {

    private ComputeBudgetAssembler() {}

    /**
     * Assembles the HUD budget.
     *
     * @param reconciliation the authoritative arithmetic over all allocations
     * @param disclosedAllocations the allocations the owner may see (a subset of those charged to the
     *     rig); every element must be charged to {@code reconciliation.rigId()}
     * @return the §1.4 readout: total, disclosed allocations (which yield allocated-by-consumer and
     *     recovering), true available, and the unaccounted-for gap
     */
    static ComputeBudget assemble(
            RigComputeReconciliation reconciliation, List<ComputeAllocation> disclosedAllocations) {
        Objects.requireNonNull(reconciliation, "reconciliation");
        Objects.requireNonNull(disclosedAllocations, "disclosedAllocations");
        return new ComputeBudget(
                reconciliation.rigId(),
                reconciliation.totalCycles(),
                reconciliation.availableForAllocation(),
                disclosedAllocations);
    }
}
