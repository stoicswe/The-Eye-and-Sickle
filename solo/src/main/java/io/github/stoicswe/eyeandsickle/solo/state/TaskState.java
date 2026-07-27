package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.UUID;

/**
 * A piece of work with a start, an end, and a wall-clock duration in between.
 *
 * <h2>Why this had to be added</h2>
 *
 * {@code docs/design/04-mining.md} §3.2 publishes a <b>Duration</b> column beside the compute cost
 * for every scan tier — ~30s, ~2 min, ~6 min — and until now nothing in the engine tracked it. The
 * duration was announced in the log ({@code "scan --thorough started: 35 cycles, ~360s"}) and then
 * simply never elapsed: {@code scan()} spent the cycles and returned, so the scan was instantaneous
 * and the published figure was decoration. A rig readout cannot show what is running if nothing
 * running is modelled.
 *
 * <h2>A task holds its cycles (UI-6, decided 2026-07-26)</h2>
 *
 * <b>The cycles are held for the task's duration and begin recovering when it ends</b>, via
 * {@link io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules#beginRecovery}. This class shipped
 * on 2026-07-26 with the opposite behaviour — spend immediately, recover in parallel with the scan —
 * on the reading that §3.2 lists Compute and Duration as separate columns. That was raised as
 * <b>UI-6</b> and decided the other way, because §3.2's own next sentence promises the player is
 * "effectively down 35 cycles for far longer than the scan runs" and under spend-immediately that
 * was false on any lean rig: the 35 cycles came back in about four minutes, inside the six-minute
 * scan that bought them.
 *
 * <p>⚠ It roughly doubles a Thorough Scan's real cost, which is a <em>price</em> change and not a
 * clock change — {@code CLAUDE.md} is explicit that {@code design/03} and {@code 04} are calibrated
 * as a set. See {@code docs/design/15-open-questions.md} §3 for what was re-checked with it.
 *
 * <h2>Persistence</h2>
 *
 * Serialised into the save with everything else, so a scan survives quitting and resuming — which is
 * the honest behaviour for something that takes six minutes of real time, and which the resume path
 * already reports for deployed miners. A task whose {@code endsAt} has passed while the game was
 * closed completes on the first tick after load.
 */
public final class TaskState {

    public String taskId = UUID.randomUUID().toString();

    /**
     * What kind of work this is. A string rather than an enum for the same reason every other
     * {@code state} class uses strings: this is a JSON document that outlives the code that wrote
     * it, and an unknown enum constant is a hard deserialisation failure rather than a task the
     * player can cancel.
     */
    public String kind = "scan";

    /** What the readout calls it — {@code scan --thorough}. Already in the operator's vocabulary. */
    public String label = "";

    /** The allocation whose cycles paid for this, so the readout can show the two together. */
    public String allocationId = "";

    /**
     * What this cost, in cycles.
     *
     * <p>Stored rather than looked up through {@link #allocationId}. Under the old spend-immediately
     * model this was load-bearing — the allocation could finish recovering mid-scan and the readout
     * showed "0C" halfway through a scan the player had just paid 35 cycles for. Hold-then-recover
     * removes that particular failure, since the allocation now outlives the task by construction.
     * It stays stored anyway: the completion log line quotes it <em>after</em> the task is off the
     * list, and a readout that has to chase a foreign object to name its own cost is one refactor
     * away from the same bug in a new shape.
     */
    public long cycles = 0L;

    public Instant startedAt = Instant.now();

    public Instant endsAt = Instant.now();

    /**
     * What the player gets when it finishes.
     *
     * <p>Held on the task rather than computed at completion so that a scan resolved after a
     * restart says the same thing it would have said in-session. A finding that depended on the
     * rig's state at completion time would quietly change depending on whether the player watched.
     */
    public String outcome = "";

    public TaskState() {}

    public TaskState(
            String kind, String label, String allocationId, long cycles, Instant startedAt, Instant endsAt) {
        this.kind = kind;
        this.label = label;
        this.allocationId = allocationId;
        this.cycles = cycles;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    /** Fraction complete at {@code now}, clamped to 0–1. */
    public double progressAt(Instant now) {
        long total = java.time.Duration.between(startedAt, endsAt).toMillis();
        if (total <= 0) {
            return 1.0d;
        }
        long done = java.time.Duration.between(startedAt, now).toMillis();
        return Math.max(0.0d, Math.min(1.0d, done / (double) total));
    }

    public boolean isFinishedAt(Instant now) {
        return !now.isBefore(endsAt);
    }
}
