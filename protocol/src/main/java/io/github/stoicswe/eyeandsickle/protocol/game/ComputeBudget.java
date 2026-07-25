package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A rig's compute ledger as the server sees it: the total ceiling, every allocation against it, and
 * how many cycles are free right now.
 *
 * <p>This is the readout {@code docs/design/01-core-resources.md} §1.4 makes mandatory — total,
 * allocated by consumer, available, recovering with time-to-recover — and the reason the multi-window
 * client gets a dedicated always-on-top rig monitor ({@code docs/architecture/01-tech-stack.md}).
 *
 * <h2>{@code available} is reported, not derived — and that is the whole point</h2>
 *
 * The obvious "simplification" is to drop the field and compute {@code total - allocated}. Do not.
 * {@code docs/architecture/06-data-model.md} §1 constraint 4 spells out what the gap is for: "sum of
 * active allocations must reconcile against {@code rigs.compute_cores}, and a discrepancy is exactly
 * what a manual auditor (or a hidden hostile miner) creates." A rootkit-wrapped miner ({@code
 * docs/design/09-defense-and-hardening.md}) steals the host's cycles without appearing in the host's
 * allocation list; the only trace it leaves is that the numbers no longer add up.
 *
 * <p>Deriving {@code available} would silently paper over precisely that trace, deleting the manual-audit
 * loop ({@code docs/design/04-mining.md} §3.1) by way of a tidy refactor. {@link #unaccountedFor()}
 * surfaces the gap instead of hiding it.
 *
 * <p>The arithmetic offered here — summing rows the server already sent, grouping them by consumer —
 * is <em>presentation</em> arithmetic. It decides nothing. Whether an allocation may be made, what it
 * costs, and how fast it recovers are all server questions (Invariant I14).
 *
 * @param rigId the rig this ledger describes
 * @param total the rig's cycle ceiling ({@code docs/design/11-rig-infrastructure.md}); raised only by
 *     schematics and story milestones, never bought (Invariant I1)
 * @param available cycles free for a new allocation right now, as reported by the server
 * @param allocations every disclosed allocation charged to this rig
 */
public record ComputeBudget(UUID rigId, Cycles total, Cycles available, List<ComputeAllocation> allocations) {

    public ComputeBudget {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(allocations, "allocations");
        allocations = List.copyOf(allocations);

        for (ComputeAllocation allocation : allocations) {
            // A ledger holds the rows charged to its own rig. The deployer's control channel belongs
            // in the deployer's budget and the parasite belongs in the host's; mixing them is how a
            // HUD ends up telling a player that someone else's miner is costing them cycles it isn't
            // (Invariant I6).
            if (!rigId.equals(allocation.chargedRigId())) {
                throw new IllegalArgumentException("Allocation " + allocation.allocationId() + " is charged to rig "
                        + allocation.chargedRigId() + ", which is not this budget's rig " + rigId);
            }
        }

        Cycles accounted = sumOf(allocations, ComputeAllocation.State.ACTIVE)
                .plus(sumOf(allocations, ComputeAllocation.State.RECOVERING))
                .plus(available);
        // Under-reconciliation is legal and meaningful (see unaccountedFor()). Over-reconciliation is
        // not: a rig cannot have more capacity in play than it has. If oversubscription is ever
        // designed — an overclock module, a borrowed-cycles mechanic — this check is the thing to
        // revisit, deliberately, rather than to loosen in passing.
        if (accounted.compareTo(total) > 0) {
            throw new IllegalArgumentException("Allocated + recovering + available (" + accounted
                    + ") exceeds the rig's total capacity (" + total + ")");
        }
    }

    /** Cycles currently held by an active allocation. */
    public Cycles allocated() {
        return sumOf(allocations, ComputeAllocation.State.ACTIVE);
    }

    /** Cycles spent and returning under the Thermal Budget curve — neither held nor usable. */
    public Cycles recovering() {
        return sumOf(allocations, ComputeAllocation.State.RECOVERING);
    }

    /**
     * The by-consumer breakdown §1.4 requires, over active allocations only.
     *
     * <p>Iteration order follows {@link ComputeConsumer}'s declaration order rather than whatever
     * order the allocations arrived in, so the HUD's rows do not reshuffle on every refresh — a
     * jumping list is unreadable at a glance, and glanceability is the entire requirement.
     *
     * @return an unmodifiable map containing only consumers that actually hold cycles
     */
    public Map<ComputeConsumer, Cycles> allocatedByConsumer() {
        Map<ComputeConsumer, Cycles> byConsumer = new EnumMap<>(ComputeConsumer.class);
        for (ComputeAllocation allocation : allocations) {
            if (allocation.state() == ComputeAllocation.State.ACTIVE) {
                byConsumer.merge(allocation.consumer(), allocation.cycles(), Cycles::plus);
            }
        }
        return Collections.unmodifiableMap(byConsumer);
    }

    /**
     * Cycles the rig has that nothing on this ledger accounts for: {@code total - allocated -
     * recovering - available}.
     *
     * <p>This is the manual-audit signal ({@code docs/design/04-mining.md} §3.1). It is an
     * <em>observation</em>, never an accusation: a non-zero value may be a hidden parasite, or it may
     * be something the server simply chose not to disclose in this response. Interpreting it is the
     * player's job — that interpretation is the gameplay — and confirming it is the server's.
     *
     * @return the gap; {@link Cycles#ZERO} when the ledger reconciles exactly
     */
    public Cycles unaccountedFor() {
        return total.minus(allocated()).minus(recovering()).minus(available);
    }

    /** Whether the ledger reconciles exactly against the rig's ceiling. */
    public boolean reconciles() {
        return unaccountedFor().isZero();
    }

    private static Cycles sumOf(List<ComputeAllocation> allocations, ComputeAllocation.State state) {
        Cycles sum = Cycles.ZERO;
        for (ComputeAllocation allocation : allocations) {
            if (allocation.state() == state) {
                sum = sum.plus(allocation.cycles());
            }
        }
        return sum;
    }
}
