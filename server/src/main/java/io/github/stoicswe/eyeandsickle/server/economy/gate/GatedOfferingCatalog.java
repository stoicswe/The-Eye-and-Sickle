package io.github.stoicswe.eyeandsickle.server.economy.gate;

import java.util.List;
import java.util.Optional;

/**
 * The set of offerings the server knows how to gate — the authoritative source of "what can I
 * unlock".
 *
 * <h2>Why a port, and why empty by default</h2>
 *
 * The "what can I unlock" question is answered by evaluating <em>server-defined</em> offerings against
 * a player's state (Invariant I14 — the requirements must not come from the request). Which offerings
 * exist, and their prices and thresholds, is a catalogue: item and vendor definitions that belong to
 * the item/tool slices ({@code docs/design/06}–{@code 11}, {@code docs/design/02-unlock-gates.md} §5's
 * per-item checklist), each calibrated against the {@code docs/design/03-economy.md} anchors as a set.
 * The economy slice owns gate <em>evaluation</em>, not the catalogue of what to evaluate.
 *
 * <p>So this slice depends on the port and {@link EconomyConfiguration} registers an {@link #empty()}
 * default. An empty catalogue is the honest state until the item slices land: the unlock endpoint
 * returns "nothing to offer yet" rather than a set of invented offerings with invented prices, which
 * would scatter balance numbers this slice has no authority to choose ({@code CLAUDE.md} working
 * agreements).
 */
public interface GatedOfferingCatalog {

    /**
     * Every offering the server can evaluate.
     *
     * @return the offerings; possibly empty, never {@code null}
     */
    List<GatedOffering> all();

    /**
     * One offering by id.
     *
     * @param offeringId the identifier
     * @return the offering, or empty if the catalogue has no such id
     */
    default Optional<GatedOffering> find(String offeringId) {
        return all().stream().filter(o -> o.offeringId().equals(offeringId)).findFirst();
    }

    /**
     * The empty catalogue — the default until the item slices supply a real one.
     *
     * @return a catalogue offering nothing
     */
    static GatedOfferingCatalog empty() {
        return List::of;
    }
}
