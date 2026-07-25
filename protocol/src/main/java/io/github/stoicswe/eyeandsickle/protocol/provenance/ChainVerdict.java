package io.github.stoicswe.eyeandsickle.protocol.provenance;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of a chain walk: recognized, or a list of exactly what was wrong.
 *
 * <p>"Recognized" is the word {@code docs/architecture/04-item-provenance.md} §7 uses, and it is the
 * one that matters — a chain failing any check is not recognized, which federation-wide is how a
 * cheating server's fabricated items become worthless ({@code 03} §4). There is no partial credit and
 * no severity ladder: one fault is enough.
 *
 * <h2>All faults, not just the first</h2>
 *
 * The walk keeps going after a failure and reports everything it finds. Stopping at the first fault
 * would be marginally faster and considerably less useful: a tampered record typically produces two
 * faults at once — its own signature stops verifying, and the record after it stops chaining — and
 * seeing both is what tells an operator that a record was <em>edited</em> rather than that a key
 * rotated. Every check is computable from the records supplied, so continuing costs nothing but CPU.
 *
 * @param faults every reason the chain was rejected, in walk order; empty means recognized
 */
public record ChainVerdict(List<ChainFault> faults) {

    public ChainVerdict {
        faults = List.copyOf(faults);
    }

    /** Whether the chain passed every check in {@code 04} §7. */
    public boolean isRecognized() {
        return faults.isEmpty();
    }

    /**
     * The earliest fault in walk order — usually the most informative one, since later faults are
     * often consequences of it.
     *
     * @return the first fault, or empty if the chain is recognized
     */
    public Optional<ChainFault> firstFault() {
        return faults.isEmpty() ? Optional.empty() : Optional.of(faults.getFirst());
    }

    /**
     * @param reason a classification
     * @return whether any fault has that reason
     */
    public boolean hasFault(ChainFault.Reason reason) {
        return faults.stream().anyMatch(fault -> fault.reason() == reason);
    }

    /**
     * @return the reasons, in walk order, for logging and assertions
     */
    public List<ChainFault.Reason> reasons() {
        return faults.stream().map(ChainFault::reason).toList();
    }
}
