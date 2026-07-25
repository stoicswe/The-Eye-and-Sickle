package io.github.stoicswe.eyeandsickle.server.compute;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * The body of a reserve request: charge a rig's cycles to one of its owner's consumers.
 *
 * <h2>What a client may say, and what it may not</h2>
 *
 * A client names a consumer and an amount; it does not name a rig (that is the path), and it never
 * sends anything ethecoin-shaped — there is no field here, anywhere in this slice, through which money
 * could ask for capacity (Invariant I1). The amount is validated to be positive at the edge, but the
 * decision that actually matters — whether the rig can spare it — is made by the server against the
 * authoritative available, and an over-ask is refused, not clamped (Invariant I14).
 *
 * <p>The bean-validation annotations reject the two malformed shapes (absent consumer, non-positive
 * amount) with a 400 before any rig is touched. They are an input filter, not an authority: passing
 * them means the request is well-formed, never that it is permitted.
 *
 * @param consumer what the cycles are going to; one of the local consumers {@link
 *     ComputeLedgerService#reserve} accepts (a cross-rig {@code CONTROL_CHANNEL} or {@code
 *     DEPLOYED_MINER} is refused, since each needs a second rig the reserve endpoint does not carry)
 * @param consumerRef the specific bot or tool, or null where the consumer is not a distinct entity
 *     (self-mining is the rig itself)
 * @param cycles how many cycles to reserve; must be positive
 */
public record AllocateComputeRequest(
        @NotNull ComputeConsumer consumer,
        UUID consumerRef,
        @Positive long cycles) {}
