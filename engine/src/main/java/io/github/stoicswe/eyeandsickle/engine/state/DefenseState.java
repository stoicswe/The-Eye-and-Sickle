package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One armed defence, holding compute for as long as it stays armed.
 *
 * <p>Defending your own rig never generates heat (Invariant I9), so nothing here writes to
 * {@code personalHeat}. That is worth stating because the opposite is a natural-seeming mistake: it
 * would make hardening feel costly in two currencies at once and quietly punish the safest thing a
 * player can do.
 */
public final class DefenseState {

    public String kind = "";
    public int tier = 1;
    public long reservedCycles = 0L;
    public Instant armedAt = Instant.now();

    /**
     * The compute reservation this defence is holding, so disarming can give it back.
     *
     * <h2>⚠ Stored rather than looked up by label, and the difference is not stylistic</h2>
     *
     * {@code ComputeRules.reserve} sets {@code allocation.label = kind}, so a search for "the
     * DEFENSIVE_ARRAY allocation labelled {@code firewall}" would usually find the right one — and
     * "usually" is the problem. A label is a display string with no uniqueness rule behind it, so
     * that search is one duplicate away from releasing somebody else's cycles, and nothing on screen
     * would report it: the rig would simply have compute back that it never gave up. The id is the
     * identifier, so this holds the id.
     *
     * <p>Empty means the reservation could not be made or is already gone; {@code GameEngine.disarm}
     * treats that as "nothing to release" rather than as an error, because a defence with no
     * allocation is still a defence the player asked to take down.
     */
    public String allocationId = "";

    /** Canary tokens tag whoever touched them; that tag is the evidence path in {@code design/12}. */
    public boolean triggered = false;

    public String triggeredBy = "";
}
