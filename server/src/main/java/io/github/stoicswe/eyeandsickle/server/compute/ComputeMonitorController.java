package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST over the compute ledger: read the rig monitor, and allocate or release cycles.
 *
 * <h2>The client renders this; it never decides it</h2>
 *
 * Every endpoint here is a thin edge over {@link ComputeLedgerService}, which is authoritative
 * (Invariant I14). The controller's only jobs are to turn a URL and a JSON body into a service call and
 * a service result or a typed error into a status code. No rule lives here — not what a reservation
 * costs, not whether one is allowed, not how fast cycles recover. In particular, a request for more
 * cycles than exist becomes an {@link InsufficientComputeException} and a 409, never a silently
 * shrunken allocation.
 *
 * <h2>Two reads, because they answer different questions</h2>
 *
 * <ul>
 *   <li>{@code GET .../compute} is the §1.4 HUD: what the owner is <em>shown</em> — total, disclosed
 *       allocations, available, recovering — with the unaccounted-for gap a hidden miner leaves.
 *   <li>{@code GET .../compute/reconciliation} is the audit surface: the authoritative arithmetic over
 *       <em>all</em> allocations, with a signed available that reads negative on an over-subscribed rig
 *       ({@code docs/design/04-mining.md} §3.1). It hides nothing and clamps nothing.
 * </ul>
 *
 * <h2>What this controller does not do</h2>
 *
 * Principal-based authorization — proving the caller owns {@code rigId} — belongs to the
 * identity/security slice ({@code docs/architecture/02-identity-and-auth.md}) and is expected to sit in
 * front of this controller as a filter or method-security rule. What this slice guarantees on its own
 * is the narrower structural rule that an allocation can only be released through the rig it is charged
 * to ({@link ComputeLedgerService#release}), so even absent that filter one rig cannot touch another's
 * allocations. Opening a control channel and charging a host for a parasite are cross-rig, deployment
 * -driven operations and are intentionally not exposed as a rig-owner endpoint here.
 */
@RestController
@RequestMapping("/api/rigs/{rigId}/compute")
public class ComputeMonitorController {

    private final ComputeLedgerService ledger;

    ComputeMonitorController(ComputeLedgerService ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * The rig monitor — the §1.4 HUD readout.
     *
     * @param rigId the rig
     * @return its live compute budget (200); 404 if the rig does not exist
     */
    @GetMapping
    public ComputeBudget monitor(@PathVariable UUID rigId) {
        return ledger.readMonitor(rigId);
    }

    /**
     * The authoritative reconciliation — the manual-audit surface, unclamped and undisclosed.
     *
     * @param rigId the rig
     * @return its reconciliation over all allocations (200); 404 if the rig does not exist
     */
    @GetMapping("/reconciliation")
    public RigComputeReconciliation reconciliation(@PathVariable UUID rigId) {
        return ledger.readReconciliation(rigId);
    }

    /**
     * Reserves cycles for one of the rig owner's own consumers.
     *
     * @param rigId the rig to charge
     * @param request the consumer and amount
     * @return 201 with the created allocation and a {@code Location} pointing at it; 404 if the rig does
     *     not exist, 409 if it cannot spare the cycles, 400 if the request is malformed or names a
     *     cross-rig consumer
     */
    @PostMapping("/allocations")
    public ResponseEntity<ComputeAllocation> allocate(
            @PathVariable UUID rigId, @Valid @RequestBody AllocateComputeRequest request) {
        ComputeAllocation allocation =
                ledger.reserve(rigId, request.consumer(), request.consumerRef(), Cycles.of(request.cycles()));
        URI location = URI.create("/api/rigs/" + rigId + "/compute/allocations/" + allocation.allocationId());
        return ResponseEntity.created(location).body(allocation);
    }

    /**
     * Releases an active reservation, returning its cycles to the pool.
     *
     * @param rigId the rig the allocation is charged to
     * @param allocationId the allocation to release
     * @return 204; 404 if the allocation does not exist or belongs to another rig, 409 if it is
     *     recovering rather than active
     */
    @DeleteMapping("/allocations/{allocationId}")
    public ResponseEntity<Void> release(@PathVariable UUID rigId, @PathVariable UUID allocationId) {
        ledger.release(rigId, allocationId);
        return ResponseEntity.noContent().build();
    }
}
