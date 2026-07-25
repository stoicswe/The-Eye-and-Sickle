package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The authoritative compute ledger: rigs, and the allocation of their cycles to consumers ({@code
 * docs/design/01-core-resources.md} §1).
 *
 * <p>Everything a cheating client could lie about is decided here (Invariant I14). A request for more
 * cycles than exist is refused, not clamped; a rig's ceiling is set once at provisioning and never
 * raised by anything touching a balance (Invariant I1); a deployed miner charges its host, while the
 * deployer separately reserves a control channel on their own rig (Invariant I6).
 *
 * <h2>Concurrency: lock the rig, then decide</h2>
 *
 * "May this rig reserve these cycles" is a question about the <em>sum</em> of the rig's allocations,
 * and a concurrently-inserted row is invisible to any single row a caller has read. So every mutation
 * that depends on or changes that sum takes the rig's row lock first ({@link
 * ComputeLedgerRepository#lockRig}), in a consistent rig-id order for cross-rig work, exactly as
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.Mutations} prescribes. Under that
 * discipline the reconciliation the decision reads cannot move underneath it, and no two allocations
 * can claim the same last cycle.
 *
 * <h2>Time is injected</h2>
 *
 * Recovery is time-driven ({@code docs/design/01-core-resources.md} §1.3), so the service holds a
 * {@link Clock} rather than calling {@code Instant.now()}. That is what makes "these cycles return in
 * 20 minutes" a testable assertion instead of a race against the wall.
 */
@Service
public class ComputeLedgerService {

    private final ComputeLedgerRepository repository;
    private final ThermalRecoveryStrategy thermalRecovery;
    private final AllocationDisclosurePolicy disclosurePolicy;
    private final ComputeProperties properties;
    private final Clock clock;
    private final TransactionOperations transactions;

    public ComputeLedgerService(
            ComputeLedgerRepository repository,
            ThermalRecoveryStrategy thermalRecovery,
            AllocationDisclosurePolicy disclosurePolicy,
            ComputeProperties properties,
            Clock clock,
            TransactionOperations transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.thermalRecovery = Objects.requireNonNull(thermalRecovery, "thermalRecovery");
        this.disclosurePolicy = Objects.requireNonNull(disclosurePolicy, "disclosurePolicy");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    // ------------------------------------------------------------------ provisioning

    /**
     * Provisions a rig with a compute ceiling — the only place {@code total_cycles} is ever written.
     *
     * <p>Invariant I1 is structural here by omission: this method takes a {@link Cycles} ceiling that
     * comes from progression (a schematic, a story milestone — {@code
     * docs/design/11-rig-infrastructure.md}), and there is no companion method that raises a ceiling in
     * exchange for ethecoin, because such a method is the one thing the whole economy is built to
     * forbid. Onboarding a new player provisions {@link ComputeProperties#startingRigCycles} (100 by
     * default, {@code docs/design/01-core-resources.md} §1) via {@link #provisionStartingRig}.
     *
     * @param playerId the owning player (must already exist; the FK enforces it)
     * @param totalCycles the ceiling, from progression — never bought
     * @param thermalBudgetTier the recovery-rate governor (at least 1)
     * @param bandwidth the simultaneity cap (positive)
     * @param memoryBuffer equipped-tool slots (non-negative)
     * @return the provisioned rig
     */
    public Rig createRig(UUID playerId, Cycles totalCycles, int thermalBudgetTier, int bandwidth, int memoryBuffer) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(totalCycles, "totalCycles");
        Rig rig = new Rig(
                UUID.randomUUID(),
                playerId,
                totalCycles,
                thermalBudgetTier,
                bandwidth,
                memoryBuffer,
                "{}",
                clock.instant(),
                0L);
        return transactions.execute(status -> {
            repository.insertRig(rig);
            return rig;
        });
    }

    /**
     * Provisions a starting rig: {@link ComputeProperties#startingRigCycles} cycles at thermal tier 1.
     *
     * <p>The compute ceiling is the one number this slice owns and can default. Bandwidth and memory
     * buffer are the other slices' progression axes ({@code docs/design/11-rig-infrastructure.md} §2)
     * and are not this slice's to calibrate, so they are required arguments rather than invented here.
     *
     * @param playerId the owning player
     * @param bandwidth the starting simultaneity cap
     * @param memoryBuffer the starting equipped-tool slots
     * @return the provisioned starting rig
     */
    public Rig provisionStartingRig(UUID playerId, int bandwidth, int memoryBuffer) {
        return createRig(playerId, Cycles.of(properties.startingRigCycles()), 1, bandwidth, memoryBuffer);
    }

    // ------------------------------------------------------------------ reads (the HUD)

    /**
     * The §1.4 HUD readout for a rig: total, allocated-by-consumer, available, and recovering — the
     * game's most important HUD element.
     *
     * <p>Reads the authoritative reconciliation (over all allocations) and the allocations the {@link
     * AllocationDisclosurePolicy} says the owner may see, then assembles them so that {@link
     * ComputeBudget#unaccountedFor()} surfaces exactly the cycles a hidden miner is stealing ({@code
     * docs/design/04-mining.md} §3.1). The true {@code available} is reported regardless of what is
     * disclosed — the gap is never closed by omitting it from the arithmetic.
     *
     * @param rigId the rig
     * @return its live compute budget
     * @throws RigNotFoundException if no such rig exists on this server
     */
    public ComputeBudget readMonitor(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        RigComputeReconciliation reconciliation = readReconciliation(rigId);
        List<ComputeAllocation> charged = repository.allocationsChargedTo(rigId);
        List<ComputeAllocation> disclosed = disclosurePolicy.disclosedTo(rigId, charged);
        return ComputeBudgetAssembler.assemble(reconciliation, disclosed);
    }

    /**
     * The authoritative, undisclosed arithmetic for a rig — the audit surface.
     *
     * <p>Unlike {@link #readMonitor}, this hides nothing and clamps nothing: {@link
     * RigComputeReconciliation#availableCycles()} is signed and reports an over-subscribed rig as the
     * negative number it is. This is the manual-audit view ({@code docs/design/04-mining.md} §3.1) —
     * "cycle totals that don't add up" made available to a careful player, without naming the parasite
     * for them.
     *
     * @param rigId the rig
     * @return its reconciliation over all allocations
     * @throws RigNotFoundException if no such rig exists on this server
     */
    public RigComputeReconciliation readReconciliation(UUID rigId) {
        Objects.requireNonNull(rigId, "rigId");
        return repository.reconciliation(rigId).orElseThrow(() -> new RigNotFoundException(rigId));
    }

    // ------------------------------------------------------------------ the owner's own allocations

    /**
     * Reserves cycles for one of the rig owner's own local consumers, refusing the request if the rig
     * cannot spare them.
     *
     * <p>Handles the consumers that sit on the owner's own rig with no far end: self-mining, a bot
     * frame, a defensive array, an equipped/used tool, a relay hop. The two cross-rig consumers are
     * rejected here and have dedicated methods, because each names a second rig (Invariant I6): a
     * control channel is opened with {@link #openControlChannel}, and a parasite is charged with {@link
     * #chargeHostForParasite}.
     *
     * <p>The check is made against the authoritative available (over all allocations) while holding the
     * rig lock, so it is exact even against concurrently-inserted rows. Over-ask is refused with {@link
     * InsufficientComputeException}; it is never silently clamped to what happens to be free.
     *
     * @param rigId the rig to charge
     * @param consumer a local consumer (not {@code CONTROL_CHANNEL} or {@code DEPLOYED_MINER})
     * @param consumerRef the specific bot/tool, or null where the consumer is not a distinct entity
     *     (self-mining is the rig itself)
     * @param cycles the amount to reserve; must be positive
     * @return the created active allocation
     * @throws RigNotFoundException if the rig does not exist
     * @throws InsufficientComputeException if the rig has fewer free cycles than requested
     * @throws IllegalArgumentException if the consumer is a cross-rig one or the amount is not positive
     */
    public ComputeAllocation reserve(UUID rigId, ComputeConsumer consumer, UUID consumerRef, Cycles cycles) {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(cycles, "cycles");
        if (consumer == ComputeConsumer.CONTROL_CHANNEL || consumer == ComputeConsumer.DEPLOYED_MINER) {
            throw new IllegalArgumentException("Consumer " + consumer + " names a second rig (Invariant I6) and is"
                    + " reserved through openControlChannel / chargeHostForParasite, not reserve(...)");
        }
        if (cycles.isZero()) {
            throw new IllegalArgumentException("A reservation is for a positive number of cycles; got 0");
        }
        return transactions.execute(status -> {
            requireRigLocked(rigId);
            requireAvailable(rigId, cycles);
            return repository.insertActiveAllocation(
                    UUID.randomUUID(), rigId, null, consumer, consumerRef, cycles, clock.instant());
        });
    }

    /**
     * Opens the deployer's control channel for a live deployed miner — the deployer's half of Invariant
     * I6.
     *
     * <p>Reserves {@link ComputeProperties#controlChannelCycles} (3 by default, {@code
     * docs/design/04-mining.md} §2) on the <em>deployer's</em> rig, charged and capacity-checked there.
     * That check is the self-correcting network cap (§2.2): channels accumulate until the deployer has
     * no cycles left to open another or to defend themselves — no hard limit is needed or wanted.
     *
     * <p>{@code hostRigId} is the informational far end and is stored as the counterparty only when the
     * host is a local player rig; it is null for an NPC host or a host on another server, whose rig is
     * not a row here. A local host that equals the deployer is rejected — deploying onto your own rig is
     * a bookkeeping accident, not a strategy (self-mining is the safe path, Invariant I4).
     *
     * @param deployerRigId the deployer's rig, which pays the channel
     * @param hostRigId the local host rig, or null for an NPC/remote host
     * @param minerRef the deployed miner this channel serves
     * @return the created control-channel allocation
     * @throws RigNotFoundException if the deployer's rig does not exist
     * @throws InsufficientComputeException if the deployer cannot spare the channel's cycles
     * @throws IllegalArgumentException if a local host rig equals the deployer's
     */
    public ComputeAllocation openControlChannel(UUID deployerRigId, UUID hostRigId, UUID minerRef) {
        Objects.requireNonNull(deployerRigId, "deployerRigId");
        Objects.requireNonNull(minerRef, "minerRef");
        if (deployerRigId.equals(hostRigId)) {
            throw new IllegalArgumentException("A control channel spans deployer and host (Invariant I6);"
                    + " both were " + deployerRigId + ". Deploying onto your own rig is not a strategy.");
        }
        Cycles cost = Cycles.of(properties.controlChannelCycles());
        return transactions.execute(status -> {
            requireRigLocked(deployerRigId);
            requireAvailable(deployerRigId, cost);
            return repository.insertActiveAllocation(
                    UUID.randomUUID(),
                    deployerRigId,
                    hostRigId,
                    ComputeConsumer.CONTROL_CHANNEL,
                    minerRef,
                    cost,
                    clock.instant());
        });
    }

    /**
     * Charges a host rig for a foreign miner running on it — the host's half of Invariant I6, and the
     * one allocation path that is <em>not</em> capacity-checked.
     *
     * <p>A parasite does not respect its host's budget: it steals the host's cycles whether or not the
     * host has them free. So this deliberately skips the available check that {@link #reserve} and
     * {@link #openControlChannel} enforce, and can push the host past its ceiling. That is not a bug —
     * the resulting over-subscription (a negative available on the reconciliation, cycles the host's
     * disclosed ledger cannot account for) is precisely the manual-audit signal a host is meant to
     * discover ({@code docs/design/04-mining.md} §3.1, §5). Whether the host is <em>shown</em> this row
     * is a separate decision made by the {@link AllocationDisclosurePolicy} at read time from {@code
     * deployed_miners.rootkit_wrapped}; the row itself always exists and is always charged.
     *
     * <p>This is the seam the deployed-mining slice drives: it inserts the {@code deployed_miners} row
     * and calls this to charge the host. {@code deployerRigId} is stored as the counterparty when the
     * deployer is local, and is null when they are on another server.
     *
     * @param hostRigId the local rig the miner runs on and steals cycles from
     * @param deployerRigId the deployer's rig if local (the far end), or null if remote
     * @param minerRef the deployed miner
     * @param cycles the host compute the miner's tier steals ({@code docs/design/04-mining.md} §2.1)
     * @return the created (unvalidated, possibly over-subscribing) allocation charged to the host
     * @throws RigNotFoundException if the host rig does not exist on this server
     * @throws IllegalArgumentException if a local deployer rig equals the host's, or cycles are zero
     */
    public ComputeAllocation chargeHostForParasite(UUID hostRigId, UUID deployerRigId, UUID minerRef, Cycles cycles) {
        Objects.requireNonNull(hostRigId, "hostRigId");
        Objects.requireNonNull(minerRef, "minerRef");
        Objects.requireNonNull(cycles, "cycles");
        if (hostRigId.equals(deployerRigId)) {
            throw new IllegalArgumentException(
                    "A parasite's host and deployer are different rigs (Invariant I6); both were " + hostRigId);
        }
        if (cycles.isZero()) {
            throw new IllegalArgumentException("A miner steals a positive number of host cycles; got 0");
        }
        return transactions.execute(status -> {
            // Verify the host exists to answer 404 rather than surface a foreign-key violation, but do
            // NOT lock it or check its budget: over-subscription is the intended outcome here.
            if (repository.findRig(hostRigId).isEmpty()) {
                throw new RigNotFoundException(hostRigId);
            }
            return repository.insertActiveAllocation(
                    UUID.randomUUID(),
                    hostRigId,
                    deployerRigId,
                    ComputeConsumer.DEPLOYED_MINER,
                    minerRef,
                    cycles,
                    clock.instant());
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Releases an active reservation, returning its cycles to the pool immediately.
     *
     * <p>This is how a reservation ends when the thing it powered stops: a bot is shut down, a defensive
     * array is disarmed, self-mining is reduced, a control channel drops because its miner was killed,
     * or a host reclaims the compute a foreign miner was stealing. The cycles come back whole and at
     * once, because they were merely <em>occupied</em>, not spent — nothing was drawn down that the
     * Thermal Budget must replenish.
     *
     * <p>Only {@link ComputeAllocation.State#ACTIVE} allocations may be released. A {@link
     * ComputeAllocation.State#RECOVERING} row cannot be released — its cycles are already spent and
     * returning on the curve, and letting it be deleted early would hand back the very cost §1.3 exists
     * to impose. Recovering cycles return on their own, via {@link #sweepRecovered}.
     *
     * <p>Authorization is structural: the allocation must be charged to {@code rigId}. An allocation
     * belonging to another rig is reported as not found, so this endpoint cannot be used to probe or
     * release another player's allocations.
     *
     * @param rigId the rig the allocation is charged to
     * @param allocationId the allocation to release
     * @throws RigNotFoundException if the rig does not exist
     * @throws AllocationNotFoundException if the allocation does not exist or is charged to another rig
     * @throws IllegalStateException if the allocation is recovering rather than active
     */
    public void release(UUID rigId, UUID allocationId) {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(allocationId, "allocationId");
        transactions.executeWithoutResult(status -> {
            requireRigLocked(rigId);
            ComputeAllocation allocation = requireAllocationOfRig(rigId, allocationId);
            if (allocation.isRecovering()) {
                throw new IllegalStateException("Allocation " + allocationId + " is recovering; its cycles are"
                        + " already spent and return on the Thermal Budget curve, not on release");
            }
            repository.deleteAllocation(allocationId);
        });
    }

    /**
     * Marks a per-use allocation as spent, sending its cycles into recovery under the Thermal Budget
     * curve.
     *
     * <p>Called when a discrete action completes — a scan finishes ({@code docs/design/04-mining.md}
     * §3.2), a relay session ends. The cycles do not return instantly; they enter {@link
     * ComputeAllocation.State#RECOVERING} with a deadline the {@link ThermalRecoveryStrategy} computes
     * from how loaded the rig <em>remains</em> after this spend. That is the §1.3 physics: on a lean rig
     * the cycles are back quickly, on an overextended one they are gone for a long stretch.
     *
     * <p>Only {@link ConsumptionModel#PER_USE} consumers may be spent. A reservation (a bot frame, a
     * control channel) is never "spent" — it is released; asking to recover a reservation would model a
     * running thing as a finished one.
     *
     * @param rigId the rig the allocation is charged to
     * @param allocationId the active per-use allocation to spend
     * @return the allocation in its new recovering state, carrying the return deadline
     * @throws RigNotFoundException if the rig does not exist
     * @throws AllocationNotFoundException if the allocation does not exist or is charged to another rig
     * @throws IllegalStateException if the allocation is not an active per-use charge
     */
    public ComputeAllocation spend(UUID rigId, UUID allocationId) {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(allocationId, "allocationId");
        return transactions.execute(status -> {
            Rig rig = requireRigLocked(rigId);
            ComputeAllocation allocation = requireAllocationOfRig(rigId, allocationId);
            if (allocation.state() != ComputeAllocation.State.ACTIVE) {
                throw new IllegalStateException(
                        "Only an active allocation can be spent; " + allocationId + " is " + allocation.state());
            }
            if (ConsumptionModel.of(allocation.consumer()) != ConsumptionModel.PER_USE) {
                throw new IllegalStateException("Consumer " + allocation.consumer() + " is a reservation, not a"
                        + " per-use charge; it is released, not recovered");
            }

            RigComputeReconciliation reconciliation = readReconciliation(rigId);
            // The load the recovery curve reads against is what remains ACTIVE once these cycles leave
            // the active set for the recovering set. Subtracting this allocation's own cycles keeps the
            // recovering block from counting itself as load.
            Cycles remainingLoad = reconciliation.activeCycles().minus(allocation.cycles());
            Duration recovery = thermalRecovery.recoveryDuration(
                    allocation.cycles(), remainingLoad, rig.totalCycles(), rig.thermalBudgetTier());
            Instant recoversAt = clock.instant().plus(recovery);

            repository.markRecovering(allocationId, recoversAt);
            return new ComputeAllocation(
                    allocation.allocationId(),
                    allocation.chargedRigId(),
                    allocation.counterpartyRigId(),
                    allocation.consumer(),
                    allocation.consumerRef(),
                    allocation.cycles(),
                    ComputeAllocation.State.RECOVERING,
                    recoversAt);
        });
    }

    /**
     * Returns every recovered allocation's cycles to the pool — the time-driven half of §1.3.
     *
     * <p>Deletes recovering allocations whose deadline has passed as of the injected clock. Intended to
     * be called on a timer; wiring the scheduler is left to the application so this slice does not
     * impose one. Idempotent: an allocation is reclaimed once and then gone.
     *
     * @return how many allocations were reclaimed
     */
    public int sweepRecovered() {
        return transactions.execute(status -> repository.deleteRecoveredBefore(clock.instant()));
    }

    // ------------------------------------------------------------------ internals

    private Rig requireRigLocked(UUID rigId) {
        return repository.lockRig(rigId).orElseThrow(() -> new RigNotFoundException(rigId));
    }

    private void requireAvailable(UUID rigId, Cycles requested) {
        RigComputeReconciliation reconciliation = readReconciliation(rigId);
        // Compared against the SIGNED available so an already over-subscribed rig (negative available)
        // refuses any further voluntary reservation, rather than a clamped zero making the comparison
        // look survivable.
        if (requested.cycles() > reconciliation.availableCycles()) {
            throw new InsufficientComputeException(rigId, requested, reconciliation.availableForAllocation());
        }
    }

    private ComputeAllocation requireAllocationOfRig(UUID rigId, UUID allocationId) {
        return repository
                .findAllocation(allocationId)
                .filter(allocation -> allocation.chargedRigId().equals(rigId))
                .orElseThrow(() -> new AllocationNotFoundException(rigId, allocationId));
    }
}
