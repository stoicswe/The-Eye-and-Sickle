package io.github.stoicswe.eyeandsickle.solo.state;

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

    /** Canary tokens tag whoever touched them; that tag is the evidence path in {@code design/12}. */
    public boolean triggered = false;

    public String triggeredBy = "";
}
