package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Pure-logic tests for {@link ComputeLedgerService} — the authoritative compute ledger (Invariant
 * I14). No database: the repository is an in-memory {@link FakeComputeLedgerRepository}, time is a
 * fixed {@link Clock}, and recovery is a recording fake, so every rule the service enforces is
 * exercised without a container.
 *
 * <p>The emphasis is the refusals. A green "reserve succeeds" proves almost nothing; what the rest of
 * the economy relies on is that an over-ask is <em>refused, not clamped</em> (I14), that a cross-rig
 * consumer cannot be reserved through the local path (I6), that a parasite over-subscribes its host on
 * purpose (I6, the audit signal), and that one rig can never touch another's allocations.
 */
class ComputeLedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private FakeComputeLedgerRepository repository;
    private RecordingRecovery recovery;
    private ComputeLedgerService service;

    @BeforeEach
    void setUp() {
        repository = new FakeComputeLedgerRepository();
        recovery = new RecordingRecovery(Duration.ofMinutes(20));
        service = new ComputeLedgerService(
                repository,
                recovery,
                new DiscloseAllAllocations(),
                properties(),
                CLOCK,
                TransactionOperations.withoutTransaction());
    }

    // ================================================================== construction

    @Nested
    @DisplayName("construction rejects null collaborators")
    class Construction {

        @Test
        @DisplayName("every collaborator is required")
        void nullsRejected() {
            // A ledger missing any collaborator would fail later at an unpredictable call site; fail now.
            assertThatThrownBy(() -> new ComputeLedgerService(
                            null,
                            recovery,
                            new DiscloseAllAllocations(),
                            properties(),
                            CLOCK,
                            TransactionOperations.withoutTransaction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeLedgerService(
                            repository,
                            null,
                            new DiscloseAllAllocations(),
                            properties(),
                            CLOCK,
                            TransactionOperations.withoutTransaction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeLedgerService(
                            repository,
                            recovery,
                            null,
                            properties(),
                            CLOCK,
                            TransactionOperations.withoutTransaction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeLedgerService(
                            repository,
                            recovery,
                            new DiscloseAllAllocations(),
                            null,
                            CLOCK,
                            TransactionOperations.withoutTransaction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeLedgerService(
                            repository,
                            recovery,
                            new DiscloseAllAllocations(),
                            properties(),
                            null,
                            TransactionOperations.withoutTransaction()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ComputeLedgerService(
                            repository, recovery, new DiscloseAllAllocations(), properties(), CLOCK, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ================================================================== provisioning

    @Nested
    @DisplayName("provisioning writes the ceiling once, from progression")
    class Provisioning {

        @Test
        @DisplayName("createRig persists a rig with the supplied ceiling and the injected timestamp")
        void createRigPersists() {
            UUID playerId = UUID.randomUUID();

            Rig rig = service.createRig(playerId, Cycles.of(250), 2, 4, 6);

            assertThat(rig.playerId()).isEqualTo(playerId);
            assertThat(rig.totalCycles()).isEqualTo(Cycles.of(250));
            assertThat(rig.thermalBudgetTier()).isEqualTo(2);
            assertThat(rig.bandwidth()).isEqualTo(4);
            assertThat(rig.memoryBuffer()).isEqualTo(6);
            // No wall-clock read: the timestamp is exactly the injected clock's instant.
            assertThat(rig.createdAt()).isEqualTo(NOW);
            assertThat(rig.rowVersion()).isZero();
            assertThat(rig.installedModules()).isEqualTo("{}");
            // It was actually stored, not merely returned.
            assertThat(service.readReconciliation(rig.rigId()).totalCycles()).isEqualTo(Cycles.of(250));
        }

        @Test
        @DisplayName("a starting rig is 100 cycles at thermal tier 1 (docs/design/01 §1)")
        void startingRigDefaults() {
            Rig rig = service.provisionStartingRig(UUID.randomUUID(), 4, 6);

            assertThat(rig.totalCycles()).isEqualTo(Cycles.of(100));
            assertThat(rig.thermalBudgetTier()).isEqualTo(1);
        }

        @Test
        @DisplayName("the configured starting ceiling is honoured, not the hardcoded default")
        void startingRigHonoursConfiguredCeiling() {
            ComputeLedgerService configured = new ComputeLedgerService(
                    repository,
                    recovery,
                    new DiscloseAllAllocations(),
                    new ComputeProperties(3, 500, null, null, null),
                    CLOCK,
                    TransactionOperations.withoutTransaction());

            assertThat(configured.provisionStartingRig(UUID.randomUUID(), 4, 6).totalCycles())
                    .isEqualTo(Cycles.of(500));
        }
    }

    // ================================================================== reserve (owner's own load)

    @Nested
    @DisplayName("reserve: the owner's own local consumers")
    class Reserve {

        @Test
        @DisplayName("reserves cycles for a local consumer and stores an active, purely-local allocation")
        void reservesLocalConsumer() {
            UUID rig = seedRig(100);

            ComputeAllocation allocation = service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(40));

            assertThat(allocation.chargedRigId()).isEqualTo(rig);
            assertThat(allocation.counterpartyRigId()).isNull();
            assertThat(allocation.consumer()).isEqualTo(ComputeConsumer.SELF_MINING);
            assertThat(allocation.cycles()).isEqualTo(Cycles.of(40));
            assertThat(allocation.state()).isEqualTo(ComputeAllocation.State.ACTIVE);
            assertThat(repository.stored(allocation.allocationId())).contains(allocation);
        }

        @Test
        @DisplayName("locks the rig BEFORE reading the sum it decides against (lock-then-decide)")
        void locksBeforeDeciding() {
            UUID rig = seedRig(100);

            service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(10));

            // The concurrency contract: the FOR UPDATE lock must precede the reconciliation read, or a
            // concurrently-inserted row is invisible to the availability check.
            int lockAt = repository.calls.indexOf("lockRig");
            int reconcileAt = repository.calls.indexOf("reconciliation");
            int insertAt = repository.calls.indexOf("insertActiveAllocation");
            assertThat(lockAt).isGreaterThanOrEqualTo(0).isLessThan(reconcileAt);
            assertThat(reconcileAt).isLessThan(insertAt);
        }

        @Test
        @DisplayName("an over-ask is REFUSED, not clamped, and nothing is written (Invariant I14)")
        void overAskIsRefusedNotClamped() {
            UUID rig = seedRig(100);
            service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(80));

            // 20 free, asking 40. A clamp would hand back 20 and let a client probe exact free capacity.
            assertThatThrownBy(() -> service.reserve(rig, ComputeConsumer.BOT_FRAME, UUID.randomUUID(), Cycles.of(40)))
                    .isInstanceOf(InsufficientComputeException.class)
                    .satisfies(e -> {
                        InsufficientComputeException ice = (InsufficientComputeException) e;
                        assertThat(ice.rigId()).isEqualTo(rig);
                        assertThat(ice.requested()).isEqualTo(Cycles.of(40));
                        assertThat(ice.available()).isEqualTo(Cycles.of(20));
                    });

            // The refusal left the ledger untouched: only the first (80) allocation exists.
            assertThat(repository.allocationCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("reserving exactly the free amount is allowed; one more cycle is refused (boundary)")
        void exactFitIsAllowedOneMoreIsRefused() {
            UUID rig = seedRig(100);
            service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(70));

            // 30 free: exactly 30 fits.
            assertThatCode(() ->
                            service.reserve(rig, ComputeConsumer.DEFENSIVE_ARRAY, UUID.randomUUID(), Cycles.of(30)))
                    .doesNotThrowAnyException();
            // Now 0 free: a single cycle is over the ceiling.
            assertThatThrownBy(() -> service.reserve(rig, ComputeConsumer.RELAY_HOP, UUID.randomUUID(), Cycles.of(1)))
                    .isInstanceOf(InsufficientComputeException.class);
        }

        @Test
        @DisplayName("an already over-subscribed rig refuses even a one-cycle voluntary reservation")
        void oversubscribedRigRefusesFurtherReservation() {
            UUID host = seedRig(100);
            UUID deployer = seedRig(100);
            // A parasite pushes the host to -20 available (checked against the SIGNED figure, not a
            // clamped zero that would look survivable).
            service.chargeHostForParasite(host, deployer, UUID.randomUUID(), Cycles.of(120));

            assertThatThrownBy(() -> service.reserve(host, ComputeConsumer.SELF_MINING, null, Cycles.of(1)))
                    .isInstanceOf(InsufficientComputeException.class);
        }

        @Test
        @DisplayName("a cross-rig consumer cannot be reserved through the local path (Invariant I6)")
        void crossRigConsumersRejected() {
            UUID rig = seedRig(100);

            // CONTROL_CHANNEL and DEPLOYED_MINER each name a second rig; they have dedicated methods.
            assertThatThrownBy(() ->
                            service.reserve(rig, ComputeConsumer.CONTROL_CHANNEL, UUID.randomUUID(), Cycles.of(3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invariant I6");
            assertThatThrownBy(
                            () -> service.reserve(rig, ComputeConsumer.DEPLOYED_MINER, UUID.randomUUID(), Cycles.of(3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invariant I6");
            // The rejection happens before any rig work.
            assertThat(repository.allocationCount()).isZero();
        }

        @Test
        @DisplayName("a zero-cycle reservation is refused")
        void zeroCyclesRejected() {
            UUID rig = seedRig(100);
            assertThatThrownBy(() -> service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reserving against a rig that does not exist is a 404, not a 500")
        void missingRigRejected() {
            assertThatThrownBy(
                            () -> service.reserve(UUID.randomUUID(), ComputeConsumer.SELF_MINING, null, Cycles.of(1)))
                    .isInstanceOf(RigNotFoundException.class);
        }

        @Test
        @DisplayName("null rig, consumer or amount are rejected")
        void nullsRejected() {
            UUID rig = seedRig(100);
            assertThatThrownBy(() -> service.reserve(null, ComputeConsumer.SELF_MINING, null, Cycles.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> service.reserve(rig, null, null, Cycles.of(1)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> service.reserve(rig, ComputeConsumer.SELF_MINING, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ================================================================== I6: the two-rig case

    @Nested
    @DisplayName("openControlChannel: the deployer's half of Invariant I6")
    class OpenControlChannel {

        @Test
        @DisplayName("charges the deployer's own rig, never the host, and names the host as counterparty")
        void chargesDeployerNotHost() {
            UUID deployer = seedRig(100);
            UUID host = seedRig(100);
            UUID miner = UUID.randomUUID();

            ComputeAllocation channel = service.openControlChannel(deployer, host, miner);

            assertThat(channel.chargedRigId()).isEqualTo(deployer);
            assertThat(channel.counterpartyRigId()).isEqualTo(host);
            assertThat(channel.consumer()).isEqualTo(ComputeConsumer.CONTROL_CHANNEL);
            assertThat(channel.consumerRef()).isEqualTo(miner);
            // 3 cycles by default (docs/design/04 §2).
            assertThat(channel.cycles()).isEqualTo(Cycles.of(3));
            // The host is NOT charged by this half; only the deployer holds a row.
            assertThat(repository.allocationsChargedTo(host)).isEmpty();
            assertThat(repository.allocationsChargedTo(deployer)).containsExactly(channel);
        }

        @Test
        @DisplayName("an NPC / remote host has no local rig, so the counterparty is null")
        void remoteHostHasNullCounterparty() {
            UUID deployer = seedRig(100);

            ComputeAllocation channel = service.openControlChannel(deployer, null, UUID.randomUUID());

            assertThat(channel.counterpartyRigId()).isNull();
            assertThat(channel.chargedRigId()).isEqualTo(deployer);
        }

        @Test
        @DisplayName("the channel is capacity-checked — the self-correcting network cap (§2.2)")
        void channelIsCapacityChecked() {
            UUID deployer = seedRig(100);
            UUID host = seedRig(100);
            // Leave only 2 cycles free; a 3-cycle channel cannot fit.
            service.reserve(deployer, ComputeConsumer.SELF_MINING, null, Cycles.of(98));

            assertThatThrownBy(() -> service.openControlChannel(deployer, host, UUID.randomUUID()))
                    .isInstanceOf(InsufficientComputeException.class);
            // No channel was opened; the deployer keeps only its self-mining row.
            assertThat(repository.allocationsChargedTo(deployer)).hasSize(1);
        }

        @Test
        @DisplayName("deploying onto your own rig is rejected — that is a bookkeeping accident, not a strategy")
        void deployerEqualsHostRejected() {
            UUID deployer = seedRig(100);

            assertThatThrownBy(() -> service.openControlChannel(deployer, deployer, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invariant I6");
        }

        @Test
        @DisplayName("a missing deployer rig is a 404")
        void missingDeployerRejected() {
            assertThatThrownBy(() -> service.openControlChannel(UUID.randomUUID(), null, UUID.randomUUID()))
                    .isInstanceOf(RigNotFoundException.class);
        }

        @Test
        @DisplayName("null deployer or miner reference are rejected")
        void nullsRejected() {
            UUID deployer = seedRig(100);
            assertThatThrownBy(() -> service.openControlChannel(null, null, UUID.randomUUID()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> service.openControlChannel(deployer, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("chargeHostForParasite: the host's half of I6, deliberately NOT capacity-checked")
    class ChargeHostForParasite {

        @Test
        @DisplayName("charges the host past its ceiling — the over-subscription IS the audit signal (§3.1)")
        void overSubscribesHostOnPurpose() {
            UUID host = seedRig(100);
            UUID deployer = seedRig(100);
            service.reserve(host, ComputeConsumer.SELF_MINING, null, Cycles.of(90));

            // A parasite does not respect its host's budget: 90 + 35 = 125 > 100, and it is charged anyway.
            ComputeAllocation parasite =
                    service.chargeHostForParasite(host, deployer, UUID.randomUUID(), Cycles.of(35));

            assertThat(parasite.chargedRigId()).isEqualTo(host);
            assertThat(parasite.counterpartyRigId()).isEqualTo(deployer);
            assertThat(parasite.consumer()).isEqualTo(ComputeConsumer.DEPLOYED_MINER);

            RigComputeReconciliation reconciliation = service.readReconciliation(host);
            assertThat(reconciliation.availableCycles()).isEqualTo(-25L);
            assertThat(reconciliation.isOverSubscribed()).isTrue();
        }

        @Test
        @DisplayName("the host is NOT locked — over-subscription is intended, so there is nothing to serialise")
        void doesNotLockTheHost() {
            UUID host = seedRig(100);
            UUID deployer = seedRig(100);

            service.chargeHostForParasite(host, deployer, UUID.randomUUID(), Cycles.of(35));

            // reserve()/openControlChannel() lock; this path deliberately only findRig()s to answer 404.
            assertThat(repository.lockCountFor(host)).isZero();
            assertThat(repository.calls).contains("findRig").doesNotContain("lockRig");
        }

        @Test
        @DisplayName("a remote deployer has no local rig, so the counterparty is null")
        void remoteDeployerHasNullCounterparty() {
            UUID host = seedRig(100);

            ComputeAllocation parasite = service.chargeHostForParasite(host, null, UUID.randomUUID(), Cycles.of(20));

            assertThat(parasite.counterpartyRigId()).isNull();
            assertThat(parasite.chargedRigId()).isEqualTo(host);
        }

        @Test
        @DisplayName("host and deployer must be different rigs (Invariant I6)")
        void hostEqualsDeployerRejected() {
            UUID host = seedRig(100);
            assertThatThrownBy(() -> service.chargeHostForParasite(host, host, UUID.randomUUID(), Cycles.of(20)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invariant I6");
        }

        @Test
        @DisplayName("a zero-cycle parasite is refused")
        void zeroCyclesRejected() {
            UUID host = seedRig(100);
            assertThatThrownBy(() ->
                            service.chargeHostForParasite(host, UUID.randomUUID(), UUID.randomUUID(), Cycles.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a missing host rig is a 404, not a foreign-key surprise")
        void missingHostRejected() {
            assertThatThrownBy(() ->
                            service.chargeHostForParasite(UUID.randomUUID(), null, UUID.randomUUID(), Cycles.of(1)))
                    .isInstanceOf(RigNotFoundException.class);
            // Nothing was inserted for a host that does not exist.
            assertThat(repository.allocationCount()).isZero();
        }
    }

    @Nested
    @DisplayName("one deployment produces two allocation rows on two rigs (Invariant I6)")
    class TwoRigCase {

        @Test
        @DisplayName("the deployer holds a 3-cycle control channel; the host holds the parasite it steals")
        void deploymentSpansTwoRigs() {
            UUID deployer = seedRig(100);
            UUID host = seedRig(100);
            UUID miner = UUID.randomUUID();

            ComputeAllocation channel = service.openControlChannel(deployer, host, miner);
            ComputeAllocation parasite = service.chargeHostForParasite(host, deployer, miner, Cycles.of(20));

            // Two rows, two rigs, never one charge counted twice.
            assertThat(service.readReconciliation(deployer).activeCycles()).isEqualTo(Cycles.of(3));
            assertThat(service.readReconciliation(host).activeCycles()).isEqualTo(Cycles.of(20));

            assertThat(channel.chargedRigId()).isEqualTo(deployer);
            assertThat(channel.counterpartyRigId()).isEqualTo(host);
            assertThat(parasite.chargedRigId()).isEqualTo(host);
            assertThat(parasite.counterpartyRigId()).isEqualTo(deployer);
            // The two rows are distinct; collapsing them into one charge is the mistake I6 forbids.
            assertThat(channel.allocationId()).isNotEqualTo(parasite.allocationId());
        }
    }

    // ================================================================== release

    @Nested
    @DisplayName("release: a reservation is handed back whole")
    class Release {

        @Test
        @DisplayName("releasing an active reservation removes it and frees its cycles immediately")
        void releasesActiveReservation() {
            UUID rig = seedRig(100);
            ComputeAllocation held = service.reserve(rig, ComputeConsumer.BOT_FRAME, UUID.randomUUID(), Cycles.of(30));

            service.release(rig, held.allocationId());

            assertThat(repository.stored(held.allocationId())).isEmpty();
            assertThat(service.readReconciliation(rig).availableCycles()).isEqualTo(100L);
        }

        @Test
        @DisplayName("a recovering allocation may NOT be released — its cycles return on the curve, not on demand")
        void recoveringCannotBeReleased() {
            UUID rig = seedRig(100);
            ComputeAllocation tool =
                    service.reserve(rig, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(35));
            service.spend(rig, tool.allocationId());

            // Releasing it would hand back the very cost §1.3 exists to impose.
            assertThatThrownBy(() -> service.release(rig, tool.allocationId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("recovering");
        }

        @Test
        @DisplayName("an allocation charged to ANOTHER rig cannot be released — reported as not found")
        void cannotReleaseAnotherRigsAllocation() {
            UUID mine = seedRig(100);
            UUID theirs = seedRig(100);
            ComputeAllocation theirAllocation =
                    service.reserve(theirs, ComputeConsumer.SELF_MINING, null, Cycles.of(10));

            // Mis-ownership resolves to 404, so this endpoint cannot probe or release another rig's rows.
            assertThatThrownBy(() -> service.release(mine, theirAllocation.allocationId()))
                    .isInstanceOf(AllocationNotFoundException.class);
            // Their allocation is untouched.
            assertThat(repository.stored(theirAllocation.allocationId())).isPresent();
        }

        @Test
        @DisplayName("releasing an unknown allocation is not found")
        void unknownAllocationNotFound() {
            UUID rig = seedRig(100);
            assertThatThrownBy(() -> service.release(rig, UUID.randomUUID()))
                    .isInstanceOf(AllocationNotFoundException.class);
        }

        @Test
        @DisplayName("releasing through a missing rig is a 404")
        void missingRigRejected() {
            assertThatThrownBy(() -> service.release(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(RigNotFoundException.class);
        }
    }

    // ================================================================== spend / recovery

    @Nested
    @DisplayName("spend: a per-use charge enters recovery on the Thermal Budget curve")
    class Spend {

        @Test
        @DisplayName("marks the allocation recovering with recoversAt = clock + strategy duration")
        void spendEntersRecovery() {
            UUID rig = seedRig(100);
            ComputeAllocation tool =
                    service.reserve(rig, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(35));

            ComputeAllocation recovering = service.spend(rig, tool.allocationId());

            assertThat(recovering.state()).isEqualTo(ComputeAllocation.State.RECOVERING);
            // The deadline is the injected clock plus the strategy's duration — no wall-clock read.
            assertThat(recovering.recoversAt()).isEqualTo(NOW.plus(Duration.ofMinutes(20)));
            assertThat(repository.stored(tool.allocationId()))
                    .get()
                    .extracting(ComputeAllocation::state)
                    .isEqualTo(ComputeAllocation.State.RECOVERING);
        }

        @Test
        @DisplayName("the recovery curve is read against the load that REMAINS active, this charge excluded")
        void remainingLoadExcludesTheSpentAllocationItself() {
            UUID rig = seedRig(100);
            service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(50));
            ComputeAllocation tool =
                    service.reserve(rig, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(35));

            service.spend(rig, tool.allocationId());

            // activeCycles was 85; the spent 35 must not count itself as load, so remainingLoad = 50.
            assertThat(recovery.lastSpent).isEqualTo(Cycles.of(35));
            assertThat(recovery.lastRemainingLoad).isEqualTo(Cycles.of(50));
            assertThat(recovery.lastTotal).isEqualTo(Cycles.of(100));
            assertThat(recovery.lastTier).isEqualTo(1);
        }

        @Test
        @DisplayName("a RESERVATION consumer cannot be spent — it is released, never recovered")
        void reservationCannotBeSpent() {
            UUID rig = seedRig(100);
            ComputeAllocation bot = service.reserve(rig, ComputeConsumer.BOT_FRAME, UUID.randomUUID(), Cycles.of(20));

            // "Spending" a running bot frame would hand its reserved cycles back while the bot still runs.
            assertThatThrownBy(() -> service.spend(rig, bot.allocationId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("reservation");
        }

        @Test
        @DisplayName("an already-recovering allocation cannot be spent again")
        void alreadyRecoveringCannotBeSpent() {
            UUID rig = seedRig(100);
            ComputeAllocation tool =
                    service.reserve(rig, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(35));
            service.spend(rig, tool.allocationId());

            assertThatThrownBy(() -> service.spend(rig, tool.allocationId())).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("spending another rig's allocation is reported as not found")
        void cannotSpendAnotherRigsAllocation() {
            UUID mine = seedRig(100);
            UUID theirs = seedRig(100);
            ComputeAllocation theirTool =
                    service.reserve(theirs, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(10));

            assertThatThrownBy(() -> service.spend(mine, theirTool.allocationId()))
                    .isInstanceOf(AllocationNotFoundException.class);
        }

        @Test
        @DisplayName("spending through a missing rig is a 404")
        void missingRigRejected() {
            assertThatThrownBy(() -> service.spend(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(RigNotFoundException.class);
        }
    }

    // ================================================================== sweep

    @Nested
    @DisplayName("sweepRecovered: the time-driven return of spent cycles")
    class Sweep {

        @Test
        @DisplayName("reclaims only allocations whose deadline has passed as of the injected clock")
        void reclaimsOnlyDueAllocations() {
            UUID rig = seedRig(100);
            // Two due (recoversAt in the past / exactly now), one not yet due.
            repository.seedAllocation(recovering(rig, Cycles.of(10), NOW.minusSeconds(1)));
            repository.seedAllocation(recovering(rig, Cycles.of(10), NOW));
            ComputeAllocation notYet = recovering(rig, Cycles.of(10), NOW.plusSeconds(1));
            repository.seedAllocation(notYet);

            int reclaimed = service.sweepRecovered();

            assertThat(reclaimed).isEqualTo(2);
            // The not-yet-due allocation survives.
            assertThat(repository.stored(notYet.allocationId())).isPresent();
        }

        @Test
        @DisplayName("a sweep with nothing due reclaims zero (idempotent)")
        void nothingDueReclaimsZero() {
            UUID rig = seedRig(100);
            repository.seedAllocation(recovering(rig, Cycles.of(10), NOW.plusSeconds(60)));

            assertThat(service.sweepRecovered()).isZero();
        }
    }

    // ================================================================== the §1.4 HUD contract

    @Nested
    @DisplayName("readMonitor / readReconciliation: the §1.4 HUD and the manual-audit surface")
    class HudContract {

        @Test
        @DisplayName("with everything disclosed the ledger reconciles exactly (unaccounted-for is zero)")
        void reconcilesWhenNothingHidden() {
            UUID rig = seedRig(100);
            service.reserve(rig, ComputeConsumer.SELF_MINING, null, Cycles.of(40));
            ComputeAllocation tool =
                    service.reserve(rig, ComputeConsumer.ACTIVE_TOOL, UUID.randomUUID(), Cycles.of(35));
            service.spend(rig, tool.allocationId());

            ComputeBudget budget = service.readMonitor(rig);

            assertThat(budget.total()).isEqualTo(Cycles.of(100));
            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.recovering()).isEqualTo(Cycles.of(35));
            assertThat(budget.available()).isEqualTo(Cycles.of(25));
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.ZERO);
            assertThat(budget.reconciles()).isTrue();
        }

        @Test
        @DisplayName("a hidden parasite surfaces as an unaccounted-for gap while true available stays honest")
        void hiddenParasiteSurfacesAsGap() {
            // A disclosure policy that conceals the deployed miner, exactly as a rootkit wrap would.
            ComputeLedgerService concealing = new ComputeLedgerService(
                    repository,
                    recovery,
                    hidingDeployedMiners(),
                    properties(),
                    CLOCK,
                    TransactionOperations.withoutTransaction());
            UUID host = seedRig(100);
            UUID deployer = seedRig(100);
            concealing.reserve(host, ComputeConsumer.SELF_MINING, null, Cycles.of(40));
            concealing.chargeHostForParasite(host, deployer, UUID.randomUUID(), Cycles.of(30));

            ComputeBudget budget = concealing.readMonitor(host);

            // The disclosed rows show 40 allocated; true available (over ALL rows) is 30. The 30 the
            // hidden miner steals appears precisely as the gap the numbers cannot account for.
            assertThat(budget.allocated()).isEqualTo(Cycles.of(40));
            assertThat(budget.available()).isEqualTo(Cycles.of(30));
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(30));
            assertThat(budget.reconciles()).isFalse();
        }

        @Test
        @DisplayName("an over-subscribed rig surfaces the discrepancy — HUD clamps available, audit shows the sign")
        void overSubscriptionIsVisibleNotSilentlyClamped() {
            ComputeLedgerService concealing = new ComputeLedgerService(
                    repository,
                    recovery,
                    hidingDeployedMiners(),
                    properties(),
                    CLOCK,
                    TransactionOperations.withoutTransaction());
            UUID host = seedRig(100);
            UUID deployer = seedRig(100);
            concealing.reserve(host, ComputeConsumer.SELF_MINING, null, Cycles.of(40));
            concealing.chargeHostForParasite(host, deployer, UUID.randomUUID(), Cycles.of(80));

            // HUD: available clamps to zero (the protocol type cannot hold a negative), but the gap the
            // player is meant to notice is loud — 60 cycles unaccounted for.
            ComputeBudget budget = concealing.readMonitor(host);
            assertThat(budget.available()).isEqualTo(Cycles.ZERO);
            assertThat(budget.unaccountedFor()).isEqualTo(Cycles.of(60));

            // Audit surface: the raw, signed, undisclosed truth — a manual auditor's whole job (§3.1).
            RigComputeReconciliation audit = concealing.readReconciliation(host);
            assertThat(audit.availableCycles()).isEqualTo(-20L);
            assertThat(audit.isOverSubscribed()).isTrue();
        }

        @Test
        @DisplayName("reading a rig that does not exist is a 404 on both the HUD and the audit surface")
        void missingRigRejected() {
            assertThatThrownBy(() -> service.readMonitor(UUID.randomUUID())).isInstanceOf(RigNotFoundException.class);
            assertThatThrownBy(() -> service.readReconciliation(UUID.randomUUID()))
                    .isInstanceOf(RigNotFoundException.class);
        }
    }

    // ================================================================== helpers

    private UUID seedRig(long totalCycles) {
        Rig rig = new Rig(UUID.randomUUID(), UUID.randomUUID(), Cycles.of(totalCycles), 1, 4, 6, "{}", NOW, 0L);
        repository.seedRig(rig);
        return rig.rigId();
    }

    private static ComputeAllocation recovering(UUID rigId, Cycles cycles, Instant recoversAt) {
        return new ComputeAllocation(
                UUID.randomUUID(),
                rigId,
                null,
                ComputeConsumer.ACTIVE_TOOL,
                UUID.randomUUID(),
                cycles,
                ComputeAllocation.State.RECOVERING,
                recoversAt);
    }

    private static ComputeProperties properties() {
        return new ComputeProperties(null, null, null, null, null);
    }

    /** A disclosure policy that drops the host-side parasite, standing in for a rootkit wrap. */
    private static AllocationDisclosurePolicy hidingDeployedMiners() {
        return (rigId, charged) -> charged.stream()
                .filter(allocation -> allocation.consumer() != ComputeConsumer.DEPLOYED_MINER)
                .toList();
    }

    /** Records the arguments the service passes to the recovery curve, so the spend math is checkable. */
    private static final class RecordingRecovery implements ThermalRecoveryStrategy {
        private final Duration toReturn;
        private Cycles lastSpent;
        private Cycles lastRemainingLoad;
        private Cycles lastTotal;
        private int lastTier;

        RecordingRecovery(Duration toReturn) {
            this.toReturn = toReturn;
        }

        @Override
        public Duration recoveryDuration(Cycles spent, Cycles remainingLoad, Cycles total, int thermalBudgetTier) {
            this.lastSpent = spent;
            this.lastRemainingLoad = remainingLoad;
            this.lastTotal = total;
            this.lastTier = thermalBudgetTier;
            return toReturn;
        }
    }
}
