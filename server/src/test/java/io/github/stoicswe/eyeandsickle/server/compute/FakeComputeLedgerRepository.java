package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A hand-written, in-memory stand-in for {@link ComputeLedgerRepository}, used by the pure-logic
 * {@link ComputeLedgerServiceTest} so the service's rules can be exercised with no database and no
 * container (Docker-free {@code mvn verify}).
 *
 * <h2>Why subclass the concrete repository rather than mock it</h2>
 *
 * The service depends on the concrete {@code ComputeLedgerRepository}, whose methods are
 * package-private — so a fake has to live in this package and override them. Every method the service
 * calls is overridden here against two {@link Map}s, so the {@link JdbcClient} handed to {@code super}
 * is never touched; it exists only to satisfy the non-null constructor. A fake reads far better than a
 * pile of Mockito stubs when, as here, the "repository" is really a tiny key-value store plus the one
 * piece of arithmetic that matters — the reconciliation, which mirrors the {@code
 * rig_compute_reconciliation} view: {@code available = total - SUM(all rows charged to the rig)},
 * <em>signed</em>, so an over-subscribed rig reads negative exactly as the real view does.
 *
 * <h2>What it records, and why</h2>
 *
 * It logs every call and every {@link #lockRig} target, so a test can assert the service's
 * lock-then-decide discipline (reserve locks before it reads the sum) and that the parasite path
 * deliberately does <em>not</em> lock its host.
 */
final class FakeComputeLedgerRepository extends ComputeLedgerRepository {

    private final Map<UUID, Rig> rigs = new LinkedHashMap<>();
    private final Map<UUID, ComputeAllocation> allocations = new LinkedHashMap<>();

    /** Every repository method invoked, in order, for asserting the lock-then-decide sequence. */
    final List<String> calls = new ArrayList<>();

    /** The rig ids passed to {@link #lockRig}, in order. */
    final List<UUID> lockedRigs = new ArrayList<>();

    FakeComputeLedgerRepository() {
        // The base constructor requires a non-null JdbcClient; a template with no data source is never
        // exercised because every method below is overridden away from SQL.
        super(JdbcClient.create(new JdbcTemplate()));
    }

    // ------------------------------------------------------------------ test seeding + inspection

    void seedRig(Rig rig) {
        rigs.put(rig.rigId(), rig);
    }

    /** Places an allocation directly, for setting up pre-existing load or an over-subscription. */
    void seedAllocation(ComputeAllocation allocation) {
        allocations.put(allocation.allocationId(), allocation);
    }

    Optional<ComputeAllocation> stored(UUID allocationId) {
        return Optional.ofNullable(allocations.get(allocationId));
    }

    int allocationCount() {
        return allocations.size();
    }

    long lockCountFor(UUID rigId) {
        return lockedRigs.stream().filter(rigId::equals).count();
    }

    // ------------------------------------------------------------------ overrides: rigs

    @Override
    void insertRig(Rig rig) {
        calls.add("insertRig");
        rigs.put(rig.rigId(), rig);
    }

    @Override
    Optional<Rig> findRig(UUID rigId) {
        calls.add("findRig");
        return Optional.ofNullable(rigs.get(rigId));
    }

    @Override
    Optional<Rig> lockRig(UUID rigId) {
        calls.add("lockRig");
        lockedRigs.add(rigId);
        return Optional.ofNullable(rigs.get(rigId));
    }

    // ------------------------------------------------------------------ overrides: reconciliation

    @Override
    Optional<RigComputeReconciliation> reconciliation(UUID rigId) {
        calls.add("reconciliation");
        Rig rig = rigs.get(rigId);
        if (rig == null) {
            return Optional.empty();
        }
        long active = 0L;
        long recovering = 0L;
        for (ComputeAllocation allocation : allocations.values()) {
            if (!allocation.chargedRigId().equals(rigId)) {
                continue;
            }
            if (allocation.state() == ComputeAllocation.State.ACTIVE) {
                active += allocation.cycles().cycles();
            } else {
                recovering += allocation.cycles().cycles();
            }
        }
        // Signed, mirroring the view: negative means over-subscribed. Deliberately NOT clamped.
        long available = rig.totalCycles().cycles() - active - recovering;
        return Optional.of(new RigComputeReconciliation(
                rigId, rig.playerId(), rig.totalCycles(), Cycles.of(active), Cycles.of(recovering), available));
    }

    // ------------------------------------------------------------------ overrides: allocations

    @Override
    List<ComputeAllocation> allocationsChargedTo(UUID rigId) {
        calls.add("allocationsChargedTo");
        List<ComputeAllocation> charged = new ArrayList<>();
        for (ComputeAllocation allocation : allocations.values()) {
            if (allocation.chargedRigId().equals(rigId)) {
                charged.add(allocation);
            }
        }
        return charged;
    }

    @Override
    Optional<ComputeAllocation> findAllocation(UUID allocationId) {
        calls.add("findAllocation");
        return Optional.ofNullable(allocations.get(allocationId));
    }

    @Override
    ComputeAllocation insertActiveAllocation(
            UUID allocationId,
            UUID chargedRigId,
            UUID counterpartyRigId,
            ComputeConsumer consumer,
            UUID consumerRef,
            Cycles cycles,
            Instant createdAt) {
        calls.add("insertActiveAllocation");
        ComputeAllocation allocation = new ComputeAllocation(
                allocationId,
                chargedRigId,
                counterpartyRigId,
                consumer,
                consumerRef,
                cycles,
                ComputeAllocation.State.ACTIVE,
                null);
        allocations.put(allocationId, allocation);
        return allocation;
    }

    @Override
    void markRecovering(UUID allocationId, Instant recoversAt) {
        calls.add("markRecovering");
        ComputeAllocation current = allocations.get(allocationId);
        if (current == null) {
            throw new IllegalStateException("markRecovering on absent allocation " + allocationId);
        }
        allocations.put(
                allocationId,
                new ComputeAllocation(
                        current.allocationId(),
                        current.chargedRigId(),
                        current.counterpartyRigId(),
                        current.consumer(),
                        current.consumerRef(),
                        current.cycles(),
                        ComputeAllocation.State.RECOVERING,
                        recoversAt));
    }

    @Override
    void deleteAllocation(UUID allocationId) {
        calls.add("deleteAllocation");
        allocations.remove(allocationId);
    }

    @Override
    int deleteRecoveredBefore(Instant now) {
        calls.add("deleteRecoveredBefore");
        int before = allocations.size();
        allocations
                .values()
                .removeIf(allocation -> allocation.isRecovering()
                        && allocation.recoversAt() != null
                        && !allocation.recoversAt().isAfter(now));
        return before - allocations.size();
    }
}
