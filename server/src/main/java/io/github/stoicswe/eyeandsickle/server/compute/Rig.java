package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's rig as this server holds it: the machine, and therefore the compute ceiling ({@code
 * docs/design/11-rig-infrastructure.md}).
 *
 * <p>This is a server-side record, not a wire type. The client is sent a {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget} — the live readout — and never the rig
 * row itself, because most of what is here (the thermal tier that governs the recovery curve, the
 * installed-module set) is input to server-side balance decisions the client must not be able to
 * predict (Invariant I14).
 *
 * <h2>{@code totalCycles} is a {@link Cycles}, and that is the whole point (Invariant I1)</h2>
 *
 * The ceiling is the master scarcity's headline number ({@code docs/design/01-core-resources.md} §1; a
 * starting rig is 100). It is raised only by schematics and story milestones — never bought — and
 * typing it as {@link Cycles} rather than a bare {@code int} keeps it in the same no-conversion regime
 * as every other cycle quantity. There is deliberately no method on this record that takes an amount
 * of ethecoin, so there is no line of code where money could become capacity.
 *
 * @param rigId this rig's identity
 * @param playerId the owning player ({@code players.player_id})
 * @param totalCycles the compute ceiling; raised only by progression, never by ethecoin (Invariant I1)
 * @param thermalBudgetTier the recovery-rate governor ({@code docs/design/01-core-resources.md} §1.3);
 *     the curve itself is server-side, only the tier is stored
 * @param bandwidth the simultaneity cap ({@code docs/design/11-rig-infrastructure.md} §2)
 * @param memoryBuffer equipped-tool slots, a separate axis from storage capacity
 * @param installedModules the module set as raw JSON ({@code rigs.installed_modules}); an object, kept
 *     verbatim because each module carries its own shape
 * @param createdAt when the rig was provisioned
 * @param rowVersion the optimistic-concurrency version
 */
public record Rig(
        UUID rigId,
        UUID playerId,
        Cycles totalCycles,
        int thermalBudgetTier,
        int bandwidth,
        int memoryBuffer,
        String installedModules,
        Instant createdAt,
        long rowVersion) {

    public Rig {
        Objects.requireNonNull(rigId, "rigId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(totalCycles, "totalCycles");
        Objects.requireNonNull(installedModules, "installedModules");
        Objects.requireNonNull(createdAt, "createdAt");
        // These mirror the CHECK constraints in V2 so an in-memory Rig cannot describe a machine the
        // database would refuse: a zero-cycle rig has no capacity to allocate, and thermal tier 0 would
        // divide the recovery curve by a tier that does not exist.
        if (totalCycles.isZero()) {
            throw new IllegalArgumentException("A rig has a positive compute ceiling; total_cycles was 0");
        }
        if (thermalBudgetTier < 1) {
            throw new IllegalArgumentException("thermalBudgetTier is at least 1, was " + thermalBudgetTier);
        }
        if (bandwidth <= 0) {
            throw new IllegalArgumentException("bandwidth is positive, was " + bandwidth);
        }
        if (memoryBuffer < 0) {
            throw new IllegalArgumentException("memoryBuffer is never negative, was " + memoryBuffer);
        }
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion is never negative, was " + rowVersion);
        }
    }
}
