package io.github.stoicswe.eyeandsickle.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Every time the client writes to the player's disk, counted.
 *
 * <p>Drives the drive lamp on the command strip ({@code ui/widgets/DiskLamp}). The lamp is the
 * fiction's version of the activity LED on the front of a machine, and it holds to the same rule the
 * rest of this client does: <b>it reports a real event or it does not light</b>. A decorative flicker
 * on a timer would be the one indicator in the game that lies about the player's own hardware.
 *
 * <h2>⚠ Poked at the two chokepoints, not at the call sites</h2>
 *
 * There are exactly two places this client writes a file the player owns — {@code ClientProfile.save}
 * and the session's {@code persist} — and both call {@link #wrote()} themselves. Instrumenting the
 * <em>callers</em> instead would have been quieter to write and wrong within a week: a settings
 * change, an avatar, a window move and the thirty-second autosave all reach those two methods by
 * different routes, and a new route added later would silently not light the lamp.
 *
 * <h2>A counter, not a timestamp</h2>
 *
 * The lamp needs to know "has anything been written since I last looked", and a counter answers that
 * without a clock. That matters here: {@code CLAUDE.md} records that anything with a deadline must
 * take the session's clock rather than {@code Instant.now()}, because a wall-clock deadline reports
 * nonsense under a test clock. A monotonic count has no deadline in it at all, so there is nothing to
 * get wrong — the lamp's own dwell is counted in ticks of the shared {@code Pulse}.
 *
 * <p>{@link AtomicLong} because a write need not happen on the FX thread. Nothing here blocks, and
 * the lamp only ever reads.
 */
public final class DiskActivity {

    private static final AtomicLong WRITES = new AtomicLong();

    private DiskActivity() {}

    /** Called by the two writers, immediately after the bytes have actually landed. */
    public static void wrote() {
        WRITES.incrementAndGet();
    }

    /**
     * How many writes have happened this run.
     *
     * <p>The absolute value means nothing; the lamp compares it with the value it saw last tick. It
     * is exposed rather than the difference because a difference would have to be consumed, and two
     * readers consuming the same signal would each see half the writes.
     */
    public static long writes() {
        return WRITES.get();
    }
}
