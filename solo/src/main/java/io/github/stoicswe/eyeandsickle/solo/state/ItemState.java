package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * One owned thing, in one storage tier.
 *
 * <h2>No provenance chain, and that is not an omission</h2>
 *
 * A federated item carries a signed per-item history proving legitimate custody ({@code
 * docs/architecture/04}). A solo item has nobody to prove anything to: there is exactly one party,
 * the save is player-editable by construction, and a chain signed by a key on the same disk would
 * prove only that the disk agreed with itself.
 *
 * <p>Manufacturing a chain here would be actively harmful — it would produce an artefact that
 * <em>looks</em> verifiable, and the client's own {@code verify(1)} teaches that a chain proves what
 * a set of keys attested rather than what happened. So solo items carry an origin note instead, and
 * {@code verify} says plainly that a local item has no chain to check.
 */
public final class ItemState {

    public String itemId = UUID.randomUUID().toString();
    public String itemType = "";
    public String displayName = "";

    /** {@code VAULT}, {@code STANDARD_STORAGE} or {@code HIGH_HACKABLE_ZONE}. */
    public String tier = "VAULT";

    public Instant acquiredAt = Instant.now();

    /** Where it came from, in words. Shown by {@code item-history} in place of a chain. */
    public String origin = "";

    /** Compute reserved while this item is equipped, if any. */
    public long equippedCycles = 0L;

    public boolean equipped = false;
}
