package io.github.stoicswe.eyeandsickle.client.sound;

import java.util.function.Supplier;

/**
 * Every noise the game can make, and the rules for making it.
 *
 * <h2>⚠ A CLOSED ENUM, FOR THE REASON {@code PresenceState} IS ONE</h2>
 *
 * A method that took a resource path would let any call site invent a sound, and the set of things
 * the game can do to a player's ears would then be discoverable only by grepping. As an enum it is a
 * list — one place to read, one place to add to, and a compiler error rather than a silent miss when
 * something is removed. {@code SfxTest} walks {@link #values()} rather than a hand-kept list, so a
 * new constant is checked by existing.
 *
 * <h2>⚠ WHAT IS DECLARED HERE IS AVAILABLE, NOT NECESSARILY WIRED</h2>
 *
 * Only {@link #MESSAGE} is currently triggered by the game — from {@code ui/Notifications} and
 * {@code view/DirectView}. The rest are a palette with no call sites yet, and that is a deliberate
 * boundary rather than an oversight: deciding that a refusal makes a noise is a <b>design</b>
 * decision about the attention ladder ({@code docs/client/05} §6), not a plumbing one, and it should
 * be taken deliberately per surface. Wiring one is a single call —
 * {@code Audio.shared().play(Sfx.REFUSE)} — at the moment somebody decides that surface should speak.
 *
 * <h2>⚠ EVERY CONSTANT CARRIES ITS OWN RETRIGGER GUARD, AND THAT IS NOT A DETAIL</h2>
 *
 * The engine is polyphonic, so nothing stops forty log lines from becoming forty simultaneous
 * chimes. {@code DirectView} already had to solve this by hand — one chime per poll rather than one
 * per message — and the general answer belongs here rather than at each call site, because the next
 * caller will not know they were supposed to. {@link #minGapMs} is the shortest interval at which a
 * given effect may retrigger; anything faster is dropped, silently and by design.
 */
public enum Sfx {

    /**
     * A message arrived — the rig's inbox, or a Bluesky DM.
     *
     * <p>The only recorded asset in the game, and the only constant with a call site. 250 ms guard,
     * which is comfortably shorter than any interval a person reads a notification at and long enough
     * to collapse a burst that arrives in one poll.
     */
    MESSAGE(() -> Sample.load("message", "/io/github/stoicswe/eyeandsickle/client/sound/message.wav"), 1.0f, 250, 0.0),

    /** An action was accepted. Rising, tonal, short. */
    CONFIRM(() -> Tone.blip("confirm", 880, 90, 2.0), 0.5f, 80, 0.02),

    /**
     * An action was refused.
     *
     * <p>⚠ Quieter than {@link #CONFIRM}, not louder. A refusal is already visible — every refusal in
     * this client puts a sentence on screen — and a loud noise for "no" trains players to brace
     * rather than to read. It marks the moment; the words carry the reason.
     */
    REFUSE(() -> Tone.refusal("refuse", 440, 130), 0.45f, 120, 0.02),

    /**
     * A small mechanical tick: selection, a step, a keystroke.
     *
     * <p>⚠ The widest pitch spread in the catalogue and the shortest guard, because this is the one
     * that repeats fastest and the one where identical repetition is most fatiguing. 6% either way is
     * enough to break the pattern and too little to read as a change in pitch.
     */
    TICK(() -> Tone.tick("tick", 35, 0.45), 0.30f, 25, 0.06),

    /** Something long finished — a transfer, an extraction, a flash. */
    DONE(() -> Tone.sweep("done", 520, 1040, 220), 0.5f, 300, 0.01),

    /**
     * Something wants attention now.
     *
     * <p>⚠ The one effect here allowed to be conspicuous, and it is rationed the way {@code -es-alarm}
     * is rationed in §2.1: this is for loss and hostile state, not for anything merely important. A
     * game that plays its alarm for ordinary events has no alarm.
     */
    ALERT(() -> Tone.sweep("alert", 300, 760, 400), 0.6f, 1000, 0.0);

    private final Supplier<Sample> source;
    private final float gain;
    private final int minGapMs;
    private final double pitchSpread;

    /**
     * ⚠ Resolved once and kept, including a failed load. A sound whose file is missing must not send
     * the loader back to the classpath on every trigger.
     */
    private volatile Sample resolved;

    private volatile boolean attempted;

    /**
     * ⚠ {@code System.nanoTime}, and this is one of the documented inversions of the always-use-the
     * -session-clock rule — the same one {@code Frost} and the event bus record. The question here is
     * "how long since <i>this machine</i> last made this noise", which is a fact about the process
     * rather than a game deadline, and a wound-forward test clock would make a retrigger guard
     * believe a burst of chimes was spread over an afternoon.
     */
    private volatile long lastPlayedNanos;

    Sfx(Supplier<Sample> source, float gain, int minGapMs, double pitchSpread) {
        this.source = source;
        this.gain = gain;
        this.minGapMs = minGapMs;
        this.pitchSpread = pitchSpread;
    }

    public float gain() {
        return gain;
    }

    public int minGapMs() {
        return minGapMs;
    }

    public double pitchSpread() {
        return pitchSpread;
    }

    /** Loads on first ask and caches. ⚠ Never call from the FX thread — {@code Audio} handles that. */
    Sample sample() {
        Sample existing = resolved;
        if (existing != null || attempted) {
            return existing;
        }
        synchronized (this) {
            if (!attempted) {
                attempted = true;
                resolved = source.get();
            }
            return resolved;
        }
    }

    /** Whether this sound has already been decoded, so a caller can take the non-blocking path. */
    boolean resident() {
        return resolved != null || attempted;
    }

    /**
     * Whether enough time has passed, stamping the clock when it has.
     *
     * <p>⚠ Test-and-set in one call rather than a separate {@code allowed()} and {@code stamp()}. Two
     * threads asking at the same moment would both be told yes by a read-only check, which is exactly
     * the burst this exists to stop — and bursts are, by their nature, concurrent.
     */
    synchronized boolean claim() {
        long now = System.nanoTime();
        if (lastPlayedNanos != 0 && now - lastPlayedNanos < minGapMs * 1_000_000L) {
            return false;
        }
        lastPlayedNanos = now;
        return true;
    }

    /** Test seam — forgets the decoded sample and the retrigger clock. */
    synchronized void reset() {
        resolved = null;
        attempted = false;
        lastPlayedNanos = 0;
    }
}
