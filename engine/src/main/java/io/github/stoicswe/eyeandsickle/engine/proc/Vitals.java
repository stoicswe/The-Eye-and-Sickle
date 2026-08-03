package io.github.stoicswe.eyeandsickle.engine.proc;

import java.time.Duration;

/**
 * How a process's figures move — the arithmetic that makes the table look like a machine.
 *
 * <h2>Two kinds of number, and conflating them is what looked fake</h2>
 *
 * <ul>
 *   <li><b>Gauges</b> — {@code %CPU}, threads, resident memory, idle wakeups. These <em>wander</em>:
 *       up a bit, down a bit, around a resting level the process keeps. {@link #gauge} is a
 *       mean-reverting wander rather than a fresh random number each interval, because white noise
 *       does not look like a computer. It looks like a slot machine, and a player watching one row
 *       learns nothing from it.
 *   <li><b>Counters</b> — CPU time, bytes read and written, packets in and out. These only ever go
 *       <em>up</em>. {@link #counter} is monotonic by construction, not by tuning, because a byte
 *       count that ticked backwards is the single most obviously-broken thing a process table can do
 *       — and because the {@code STOPPED_CLOCK} disguise is <b>caught</b> by comparing an honest
 *       accumulating counter against a dishonest one.
 * </ul>
 *
 * <h2>Deterministic, and never from the persisted generator</h2>
 *
 * Every figure is a pure function of {@code (identity, interval index)}. Two reads inside the same
 * five-second interval are identical — which is what lets a player compare two rows without racing
 * them — and nothing here touches {@code Rng}. That generator is persisted and every draw from it is
 * a commitment, so decorating a readout from it would let opening a window change a breach board.
 *
 * <h2>⚠ The interval is the unit everything is expressed in</h2>
 *
 * {@link #INTERVAL_SECONDS} is the tick the whole table moves on, and the client repaints on the same
 * period. A gauge that changed on a different cadence from the repaint would either be invisible
 * (changed and not drawn) or appear to change at random (drawn on an unrelated schedule).
 */
public final class Vitals {

    private Vitals() {}

    /** How often every figure in the table advances. The client repaints on the same period. */
    public static final long INTERVAL_SECONDS = 5L;

    /** Which interval a moment falls in, counted from an epoch the caller chooses. */
    public static long intervalAt(long secondsSinceEpoch) {
        return Math.floorDiv(secondsSinceEpoch, INTERVAL_SECONDS);
    }

    // ================================================================== gauges

    /**
     * A figure that wanders around {@code resting}, by up to {@code swing} of itself.
     *
     * <h2>Why this is smoothed rather than a plain hash</h2>
     *
     * A hash of {@code (seed, interval)} is white noise: every five seconds the value teleports
     * somewhere unrelated, which reads as broken instrumentation. Averaging three consecutive taps
     * gives the same determinism with a correlated walk — the value moves <em>toward</em> where it
     * is going and arrives over two or three intervals, which is what a real load average does.
     *
     * <p>⚠ Cheap on purpose. This runs for every figure of every row on every repaint, and the
     * temptation to reach for a proper noise function should be resisted: three hashes and two adds
     * is already indistinguishable from one at the resolution a table prints.
     *
     * @param seed the process's identity — the same seed always draws the same walk
     * @param interval which five-second step
     * @param resting the level it returns to
     * @param swing how far it strays, as a fraction of {@code resting}. {@code 0.4} is a lively
     *     userland process; {@code 0.1} is a daemon that mostly sleeps
     */
    public static double gauge(long seed, long interval, double resting, double swing) {
        double smoothed = (unit(seed, interval) + unit(seed, interval - 1) + unit(seed, interval - 2)) / 3.0d;
        // Centred on zero so the wander is symmetric: a figure that only ever drifted upward would
        // creep away from its resting level over a long session.
        return Math.max(0.0d, resting * (1.0d + swing * (smoothed * 2.0d - 1.0d)));
    }

    /**
     * A figure that changes <em>rarely</em> — a thread count, a port count.
     *
     * <p>Threads do not wander every five seconds on a real machine; they sit still for a minute and
     * then a worker starts or stops. Holding the value across {@code every} intervals is what makes
     * the movement mean something when it happens, and it is the difference between a table that
     * looks alive and one that looks like it is being shaken.
     *
     * @param spread how many above the base it may reach. The result is never below {@code base}
     * @param every how many intervals it holds before it may change; {@code 12} is a minute
     */
    public static int steps(long seed, long interval, int base, int spread, int every) {
        if (spread <= 0) {
            return base;
        }
        long held = Math.floorDiv(interval, Math.max(1, every));
        return base + (int) Math.floorMod(mix(seed, held), spread + 1L);
    }

    // ================================================================== counters

    /**
     * A monotonically increasing total, in whatever unit {@code perInterval} is.
     *
     * <h2>⚠ Monotonic by construction, and the construction is the whole point</h2>
     *
     * The obvious version — {@code base + rate × elapsed}, with the rate wandering — is not
     * monotonic: the moment the rate dips, the total goes backwards. A player who sees a byte counter
     * fall has been told, correctly, that the table is fabricated.
     *
     * <p>So the total is {@code intervals × mean + partial(interval)}, where {@code partial} is a
     * hash bounded strictly below {@code mean}. The step between consecutive intervals is therefore
     * {@code mean + partial(n+1) − partial(n)}, which lies in {@code (0, 2·mean)} — always positive,
     * and visibly uneven, which is what an I/O counter actually looks like.
     *
     * @param intervals how many intervals the process has been alive for; negative reads as zero
     * @param perInterval the average step. Zero means the process genuinely does none of this, and
     *     the total stays exactly zero — a kernel thread does no disk I/O and must not appear to
     */
    public static long counter(long seed, long intervals, long perInterval) {
        if (perInterval <= 0 || intervals <= 0) {
            return 0L;
        }
        long partial = Math.floorMod(mix(seed, intervals), perInterval);
        return intervals * perInterval + partial;
    }

    /**
     * Accumulated processor time for a process that has been running {@code intervals} intervals at
     * roughly {@code restingPercent} of the machine.
     *
     * <p>Real, in the sense that matters: a process burning a fifth of a core banks about a fifth of
     * a second per second, so the figure a player reads is one they could have predicted from the
     * {@code %CPU} column beside it. That predictability <b>is</b> the {@code STOPPED_CLOCK} tell —
     * the disguise works precisely because every other row obeys this and one does not.
     *
     * <p>⚠ <b>Takes the RESTING share, never the wandering one, and a test caught this.</b> Feeding
     * the live gauge in makes the rate itself move — and {@code intervals × rate} then <em>falls</em>
     * the moment the gauge dips, so the total goes backwards. A byte or time counter that ticks down
     * is the single most obviously-fabricated thing a process table can do. Accumulating at the
     * resting level is also the more honest reading: a lifetime total reflects a process's average
     * share, not whatever it happened to be doing in the last five seconds.
     */
    public static Duration cpuTime(long seed, long intervals, double restingPercent) {
        long millisPerInterval = Math.max(1L, Math.round(INTERVAL_SECONDS * 1_000L * restingPercent / 100.0d));
        return Duration.ofMillis(counter(seed, intervals, millisPerInterval));
    }

    // ================================================================== hashing

    /** A hash in {@code [0, 1)}. Not a generator: nothing here advances any state. */
    private static double unit(long seed, long step) {
        return Math.floorMod(mix(seed, step), 1_000_000L) / 1_000_000.0d;
    }

    /** splitmix64's finaliser, used as a mixing function rather than as a stream. */
    public static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xD1B54A32D192ED03L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
