package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.GateRequirement;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.util.Objects;

/**
 * A server-side offering with the concrete requirements a player must meet to unlock it.
 *
 * <h2>Why the requirements live here and never arrive from a client</h2>
 *
 * This is the authoritative half of the gate system (Invariant I14). The client may render what looks
 * available; the server decides what <em>is</em>. If the price, the reputation threshold or the
 * proof-of-skill tier travelled in the request, a cheating client would send "reputation ≥ -9999" and
 * unlock everything. So an offering — its id and its {@link GateCondition}s — is defined on the server
 * (a catalogue, {@link GatedOfferingCatalog}) and a request only ever names an offering by id.
 *
 * <h2>Structure mirrors {@code GateRequirement}, and is validated against it</h2>
 *
 * One primary condition, one optional secondary — the same shape as {@code protocol/game/GateRequirement},
 * and validated by constructing one. That reuse is deliberate: {@code GateRequirement} already encodes
 * the structural rules of Invariants I2/I3 (the secondary is never {@link UnlockGate#SCHEMATIC}, so a
 * ceiling can never hide behind an ethecoin price; the secondary never repeats the primary). Rather
 * than re-checking those here, this record <em>delegates</em> to the one type that owns them, so the
 * two can never drift.
 *
 * @param offeringId the stable identifier a request names; must not be blank
 * @param primary the gate the offering is classified under — the {@link GateClassifier} output, and
 *     the ceiling component where there is one
 * @param secondary an additional, non-ceiling requirement for a sanctioned split, or {@code null} for
 *     the common single-gate case
 */
public record GatedOffering(String offeringId, GateCondition primary, GateCondition secondary) {

    public GatedOffering {
        Objects.requireNonNull(offeringId, "offeringId");
        if (offeringId.isBlank()) {
            throw new IllegalArgumentException("offeringId must not be blank");
        }
        Objects.requireNonNull(primary, "primary");

        // Delegate the split rules to the protocol type that owns them (I2/I3). Constructing one throws
        // if the secondary is SCHEMATIC or repeats the primary — the two shapes that would smuggle a
        // ceiling behind a price, or dress a single gate up as a split. The result is discarded; the
        // validation is the whole point.
        UnlockGate secondaryGate = secondary == null ? null : secondary.gate();
        new GateRequirement(primary.gate(), secondaryGate);
    }

    /**
     * A single-gate offering.
     *
     * @param offeringId the identifier
     * @param condition the one requirement
     * @return the offering
     */
    public static GatedOffering single(String offeringId, GateCondition condition) {
        return new GatedOffering(offeringId, condition, null);
    }

    /**
     * A sanctioned split: a primary classification plus an additional requirement.
     *
     * @param offeringId the identifier
     * @param primary the classifying gate — the ceiling component where there is one
     * @param secondary the additional requirement; never a {@link UnlockGate#SCHEMATIC} condition, and
     *     never the same gate as {@code primary}
     * @return the offering
     */
    public static GatedOffering split(String offeringId, GateCondition primary, GateCondition secondary) {
        return new GatedOffering(offeringId, primary, Objects.requireNonNull(secondary, "secondary"));
    }

    /** Whether this offering has a second requirement. */
    public boolean isSplit() {
        return secondary != null;
    }

    /**
     * This offering's gate shape, as the protocol type — for rendering an icon or a filter, not for
     * deciding satisfaction.
     *
     * @return the requirement
     */
    public GateRequirement requirement() {
        return new GateRequirement(primary.gate(), secondary == null ? null : secondary.gate());
    }
}
