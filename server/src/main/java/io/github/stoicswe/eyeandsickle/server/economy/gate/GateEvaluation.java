package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.util.List;
import java.util.Objects;

/**
 * The server's verdict on whether one player can unlock one offering.
 *
 * <p>This is the authoritative answer to "what can I unlock" (Invariant I14): the client renders it,
 * the server decides it. An offering is {@link #satisfied()} only when <em>every</em> condition is —
 * for a split, both the primary and the secondary — because a split gate is a conjunction, not a
 * choice.
 *
 * <p>Per-condition outcomes are included so the client can explain <em>why</em> something is locked
 * ("needs Sickle standing 120, you have 40") without a second round trip. Exposing the threshold is
 * safe: the danger Invariant I14 guards against is a client <em>setting</em> a threshold, not seeing
 * one — the design already expects the client to render availability.
 *
 * @param offeringId the offering evaluated
 * @param satisfied whether the player meets every condition
 * @param conditions the per-condition results, primary first; one entry for a single gate, two for a
 *     split
 */
public record GateEvaluation(String offeringId, boolean satisfied, List<ConditionOutcome> conditions) {

    public GateEvaluation {
        Objects.requireNonNull(offeringId, "offeringId");
        Objects.requireNonNull(conditions, "conditions");
        conditions = List.copyOf(conditions);
    }

    /**
     * One condition's contribution to the verdict.
     *
     * @param gate which gate this condition belongs to
     * @param met whether the player satisfies it
     * @param detail a short, player-safe explanation — the requirement and the player's standing
     *     against it — for a locked tooltip; never a secret, never a stack trace
     */
    public record ConditionOutcome(UnlockGate gate, boolean met, String detail) {

        public ConditionOutcome {
            Objects.requireNonNull(gate, "gate");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
