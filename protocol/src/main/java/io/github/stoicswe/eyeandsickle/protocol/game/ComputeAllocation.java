package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a rig's compute ledger: some cycles, charged to some rig, on behalf of some consumer.
 *
 * <p>{@code docs/design/01-core-resources.md} §1.4 requires the player to see, at a glance, total
 * cycles, allocated <em>by consumer</em>, available, and recovering with a time-to-recover. This
 * record is one row of that readout; {@link ComputeBudget} is the readout.
 *
 * <h2>Two rigs, because of Invariant I6</h2>
 *
 * "A deployed miner consumes the host's compute, not the deployer's." That single rule means an
 * allocation cannot be described by one rig identity:
 *
 * <ul>
 *   <li>the <em>host's</em> ledger carries a {@link ComputeConsumer#DEPLOYED_MINER} row charged to
 *       the host, whose counterparty is the deployer's rig;
 *   <li>the <em>deployer's</em> ledger carries a {@link ComputeConsumer#CONTROL_CHANNEL} row charged
 *       to the deployer, whose counterparty is the host.
 * </ul>
 *
 * They are two allocations on two rigs, not one allocation seen from two sides. Collapsing them —
 * "the miner costs 20 cycles, charge it once" — is the exact mistake I6 exists to prevent, and it
 * would delete the reason a host ever spends compute on detection.
 *
 * <p>{@code counterpartyRigId} is therefore <em>informational</em>: it names the far end of the
 * relationship, never a second charge. Whether the server discloses it at all is the server's call —
 * a rootkit-wrapped miner ({@code docs/design/09-defense-and-hardening.md}) is hidden by being absent
 * from the host's readout entirely, and revealing the deployer for free would hand the host what the
 * Provenance Tracer is supposed to earn.
 *
 * <h2>Why {@code recoversAt} is an {@link Instant} here and a {@code String} in provenance</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenancePayload} types its timestamp
 * as a {@code String} because those exact bytes get canonicalized and signed, and a JSON library's
 * choice of instant rendering must not be able to break a signature. Nothing signs a compute
 * allocation — it is a live readout with a lifetime of seconds — so the typed form is simply safer
 * here. Note that this class still may not <em>read</em> a clock: the instant is chosen by the
 * server and passed in, which {@code ArchitectureRulesTest} enforces.
 *
 * <p>The client renders a countdown from that instant. It must not attempt to derive it: the
 * recovery curve is a Thermal Budget function ({@code docs/design/01-core-resources.md} §1.3, itself
 * still {@code [PROPOSAL]}) and lives on the server with every other balance value.
 *
 * @param allocationId stable identity for this row, so a HUD can diff a refreshed budget instead of
 *     rebuilding it
 * @param chargedRigId the rig whose ceiling these cycles count against — the <em>host</em> for a
 *     foreign miner, the <em>deployer</em> for a control channel
 * @param counterpartyRigId the rig at the far end of the relationship, or {@code null} when the
 *     allocation is entirely local (the common case)
 * @param consumer what the cycles are going to
 * @param consumerRef the specific miner, bot or tool, or {@code null} where the consumer is not a
 *     distinct entity (self-mining is the rig itself)
 * @param cycles how many cycles this row accounts for
 * @param state whether the cycles are held or coming back
 * @param recoversAt when recovering cycles return; {@code null} exactly when the state is {@link
 *     State#ACTIVE}
 */
public record ComputeAllocation(
        UUID allocationId,
        UUID chargedRigId,
        UUID counterpartyRigId,
        ComputeConsumer consumer,
        UUID consumerRef,
        Cycles cycles,
        State state,
        Instant recoversAt) {

    /**
     * Whether cycles are currently held, or on their way back.
     *
     * <p>Spent cycles do not return instantly ({@code docs/design/01-core-resources.md} §1.3); they
     * come back on a curve that is slower the closer the rig sits to capacity. Recovering cycles are
     * therefore neither allocated nor available, which is why they are a third state and not simply
     * absent from the ledger — "where did my 35 cycles go" has to have an answer on screen.
     */
    public enum State {

        /** Held: reserved while the consumer runs, or in use right now. */
        ACTIVE,

        /** Spent and returning under the Thermal Budget curve; unusable until {@code recoversAt}. */
        RECOVERING
    }

    public ComputeAllocation {
        Objects.requireNonNull(allocationId, "allocationId");
        Objects.requireNonNull(chargedRigId, "chargedRigId");
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(state, "state");

        // A counterparty equal to the charged rig is not a harmless redundancy: it is the shape a
        // double-charge takes when someone "simplifies" I6 away, and it would read on screen as a
        // rig hosting a parasite for itself.
        if (chargedRigId.equals(counterpartyRigId)) {
            throw new IllegalArgumentException(
                    "counterpartyRigId names the far end of a cross-rig relationship and must differ "
                            + "from chargedRigId (Invariant I6); both were " + chargedRigId);
        }

        // Both markers must agree, the same discipline ProvenancePayload applies to genesis records.
        // A recovering row without a time cannot satisfy §1.4's "with time-to-recover"; an active row
        // carrying one is claiming to be two states at once.
        boolean recovering = state == State.RECOVERING;
        if (recovering != (recoversAt != null)) {
            throw new IllegalArgumentException("A RECOVERING allocation carries recoversAt and an ACTIVE one "
                    + "does not; got state=" + state + ", recoversAt=" + recoversAt);
        }
    }

    /** Whether these cycles are returning rather than held. */
    public boolean isRecovering() {
        return state == State.RECOVERING;
    }

    /**
     * Whether this row describes a relationship spanning two rigs — a foreign miner on this rig, or
     * this rig's control channel to a miner elsewhere.
     */
    public boolean crossesRigs() {
        return counterpartyRigId != null;
    }
}
