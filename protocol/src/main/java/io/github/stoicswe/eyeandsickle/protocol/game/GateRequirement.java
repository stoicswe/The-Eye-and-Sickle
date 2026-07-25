package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Objects;

/**
 * What stands between a player and an item: one primary {@link UnlockGate}, and optionally one
 * secondary.
 *
 * <h2>Why this is not a {@code Set<UnlockGate>}</h2>
 *
 * A set of equals is the obvious model and it is wrong. Invariant I3 says every item sits behind
 * <strong>exactly one</strong> gate, assigned by the decision procedure in {@code
 * docs/design/02-unlock-gates.md} §1.1 — ask five questions in order, first "yes" wins — and that
 * single answer is what makes the progression economy legible and auditable. In a set, "which one is
 * <em>the</em> gate" has no answer, so I3 becomes unenforceable, and an item quietly ends up gated by
 * whichever member the reading code happened to look at first. The invariant would not be violated
 * loudly; it would just stop meaning anything.
 *
 * <p>Split gates are nonetheless real and sanctioned (§1.1): the Relay Chain <em>framework</em> is
 * schematic-gated while additional hops cost ethecoin per session; the Rainbow Table is bought with
 * ethecoin but the capability to use it is found; Cold Storage Expansion is schematic plus
 * reputation. In every sanctioned split the pattern is the same — <strong>the ceiling component is
 * always on the non-ethecoin side</strong>, because money never raises a ceiling (Invariant I2).
 *
 * <p>So: primary is the classification, secondary is the additional cost. Ordered, not symmetric.
 *
 * <h2>The two structural rules</h2>
 *
 * <ol>
 *   <li><strong>The secondary may not be {@link UnlockGate#SCHEMATIC}.</strong> The schematic gate is
 *       the ceiling gate, and "does it raise a permanent ceiling?" is question <em>one</em> of §1.1 —
 *       so a real schematic component always wins primacy. A schematic in the secondary slot is
 *       either a mis-assignment or an attempt to smuggle a ceiling in behind an ethecoin price, which
 *       is the exact shape Invariant I2 forbids.
 *   <li><strong>The secondary may not repeat the primary.</strong> Pairing a gate with itself is not
 *       a split; it is a duplicate, and {@code ETHECOIN + ETHECOIN} in particular is how "one gate,
 *       plus a second price on the same gate" gets written when someone means "expensive".
 * </ol>
 *
 * <h2>What is deliberately not enforced</h2>
 *
 * Whether an {@link UnlockGate#ETHECOIN} <em>primary</em> may carry a reputation or proof-of-skill
 * secondary. §1.1's ordering puts both of those questions above ethecoin, which implies they would
 * win primacy — but §3's Zero-Day row ("Heat state (access) + EC (400+)") shows the tables list the
 * two halves of a split without asserting which is primary, so reading an ordering rule out of them
 * would be inventing one. Left open, and flagged for {@code docs/design/15-open-questions.md}.
 *
 * <p>And, of course, whether a given player <em>satisfies</em> this requirement. That reads
 * authoritative state — schematics found, faction standing, persisted {@link ResolutionRecord}s — and
 * is precisely what a cheating client would forge (Invariant I14).
 *
 * @param primary the gate the item is classified under; the ceiling component in a split
 * @param secondary an additional, non-ceiling requirement, or {@code null} for the common single-gate
 *     case
 */
public record GateRequirement(UnlockGate primary, UnlockGate secondary) {

    public GateRequirement {
        Objects.requireNonNull(primary, "primary");

        if (secondary != null) {
            if (secondary == primary) {
                throw new IllegalArgumentException("A split gate pairs two different gates; both halves were " + primary
                        + ". If an item needs more of one gate, that is a price, not a second gate.");
            }
            if (secondary == UnlockGate.SCHEMATIC) {
                throw new IllegalArgumentException(
                        "SCHEMATIC is the ceiling gate and always takes primacy (docs/design/02-unlock-gates.md "
                                + "§1.1 asks about ceilings first); it cannot be the secondary of a "
                                + primary + " gate");
            }
        }
    }

    /**
     * The common case: one gate, no split.
     *
     * @param gate the item's single gate
     * @return the requirement
     */
    public static GateRequirement single(UnlockGate gate) {
        return new GateRequirement(gate, null);
    }

    /**
     * A sanctioned split: the classifying gate plus a recurring or additional requirement.
     *
     * @param primary the gate the item is classified under — the ceiling component, where there is
     *     one
     * @param secondary the additional requirement; never {@link UnlockGate#SCHEMATIC} and never equal
     *     to {@code primary}
     * @return the requirement
     */
    public static GateRequirement split(UnlockGate primary, UnlockGate secondary) {
        return new GateRequirement(primary, Objects.requireNonNull(secondary, "secondary"));
    }

    /** Whether this requirement has a second component. */
    public boolean isSplit() {
        return secondary != null;
    }

    /**
     * Whether {@code gate} appears in this requirement at all, in either slot.
     *
     * <p>Answers "does this item involve reputation?" — a question about the item's shape, for filters
     * and iconography. It does not answer "can this player have it", which is a server question.
     *
     * @param gate the gate to look for
     * @return whether it is the primary or the secondary
     */
    public boolean involves(UnlockGate gate) {
        return primary == gate || secondary == gate;
    }
}
