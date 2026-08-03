package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.RigEvent;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
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
    public static void add(GameSave save, int severity, String facility, String message, Instant now) {
        save.log.add(new RigEvent(severity, facility, now, message));
        while (save.log.size() > GameSave.LOG_CAPACITY) {
            save.log.removeFirst();
        }
    }

    public static void info(GameSave save, String facility, String message, Instant now) {
        add(save, RigEvent.INFORMATIONAL, facility, message, now);
    }

    public static void notice(GameSave save, String facility, String message, Instant now) {
        add(save, RigEvent.NOTICE, facility, message, now);
    }

    public static void warning(GameSave save, String facility, String message, Instant now) {
        add(save, RigEvent.WARNING, facility, message, now);
    }

    /**
     * Something the player asked for and did not get.
     *
     * <h2>⚠ Refusals are logged so the notification that carries them is still "the log, filtered"</h2>
     *
     * {@code client/ui/Notifications} is emphatic that every notice it shows is a line the rig
     * already emitted — a toast with its own copy of an event is one that can disagree with the log,
     * and {@code docs/design/04-mining.md} §3.1 makes noticing that two readouts disagree the way a
     * player catches a hidden miner. Panels used to print their refusals inline instead, which meant
     * the one class of message a player most needs to see was the only class that never reached the
     * notification system <em>or</em> the journal. Writing them here fixes both at once and keeps the
     * "not a second source of truth" rule intact.
     *
     * <p><b>ERROR rather than WARNING</b> and that is deliberate: 3 passes every threshold a player
     * is likely to set (the default is 5), and a refusal is a direct answer to something they just
     * did. A filter that swallowed it would leave a button that silently does nothing, which is the
     * failure the inline notice existed to prevent.
     *
     * <p>⚠ <b>A repeat of the last line is dropped.</b> A player mashing a control they cannot afford
     * would otherwise write one entry per press and push everything else off both the toast stack and
     * the 500-line journal. Real syslog does the same thing for the same reason, so the behaviour is
     * one more thing that transfers.
     */
    public static void error(GameSave save, String facility, String message, Instant now) {
        if (!save.log.isEmpty()) {
            RigEvent last = save.log.getLast();
            if (last.severity == RigEvent.ERROR && last.facility.equals(facility) && last.message.equals(message)) {
                return;
            }
        }
        add(save, RigEvent.ERROR, facility, message, now);
    }

    /**
     * Something a player must not miss.
     *
     * <p>Reserved. {@code alert-fatigue(7)} is a page in this game's own manual, and a log that cries
     * wolf teaches its reader to stop looking — which disables the investigation the whole design
     * rests on. If everything is an alert, nothing is.
     */
    public static void alert(GameSave save, String facility, String message, Instant now) {
        add(save, RigEvent.ALERT, facility, message, now);
    }
}
