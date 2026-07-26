package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.state.RigEvent;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;

/**
 * The only thing that writes to the rig log.
 *
 * <h2>One writer, for the same reason the ledger has one</h2>
 *
 * A log assembled from several places drifts in format, in severity discipline, and in whether it
 * remembers to cap itself. More importantly it drifts in <em>coverage</em>: the second writer is
 * always the one somebody forgets to call. Funnelling every event through one method makes "did this
 * get logged" a question with one answer.
 *
 * <h2>What is worth logging, and what is not</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 is the standard: the player must be able to reconstruct what
 * happened to their rig well enough to notice something that should not be there. So state changes
 * are logged and mere ticks are not — a line every second saying "self-mining earned 0.011 EC" would
 * bury the one line that mattered, which is the failure mode {@code alert-fatigue(7)} describes.
 */
public final class EventLog {

    private EventLog() {}

    /** Appends an event, dropping the oldest if the log is full. */
    public static void add(SoloSave save, int severity, String facility, String message, Instant now) {
        save.log.add(new RigEvent(severity, facility, now, message));
        while (save.log.size() > SoloSave.LOG_CAPACITY) {
            save.log.removeFirst();
        }
    }

    public static void info(SoloSave save, String facility, String message, Instant now) {
        add(save, RigEvent.INFORMATIONAL, facility, message, now);
    }

    public static void notice(SoloSave save, String facility, String message, Instant now) {
        add(save, RigEvent.NOTICE, facility, message, now);
    }

    public static void warning(SoloSave save, String facility, String message, Instant now) {
        add(save, RigEvent.WARNING, facility, message, now);
    }

    /**
     * Something a player must not miss.
     *
     * <p>Reserved. {@code alert-fatigue(7)} is a page in this game's own manual, and a log that cries
     * wolf teaches its reader to stop looking — which disables the investigation the whole design
     * rests on. If everything is an alert, nothing is.
     */
    public static void alert(SoloSave save, String facility, String message, Instant now) {
        add(save, RigEvent.ALERT, facility, message, now);
    }
}
