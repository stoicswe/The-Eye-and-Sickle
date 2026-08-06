package io.github.stoicswe.eyeandsickle.client.sound;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * The client's sound effects. There is currently one.
 *
 * <h2>⚠ {@code javax.sound.sampled}, NOT {@code javafx-media}, and that is the whole design</h2>
 *
 * JavaFX Media plays MP3 and would have been the obvious choice. It is also a <b>new module</b>, and
 * this client ships <b>five platform uber jars</b> — so it would add {@code libjfxmedia} natives to
 * every one of them, and a sixth dependency to the enforcer's surface, for a one-second notification
 * chime. {@code javax.sound.sampled} is in the JDK and costs nothing.
 *
 * <p>The price is that it does not decode MP3: it handles WAV, AU and AIFF. So the supplied
 * {@code youGotmail.mp3} was converted once, at authoring time, and the <b>WAV is what ships</b>:
 *
 * <pre>{@code
 * afconvert -f WAVE -d LEI16@11025 -c 1 youGotmail.mp3 message.wav
 * }</pre>
 *
 * <p>Mono, 11 kHz, 16-bit — the source's own rate, so nothing was resampled — and <b>23 KB</b>, which
 * is a fifth of what one platform's media native would have cost. Same reasoning
 * {@code presence/DiscordIpc} records for writing its own IPC rather than taking a library.
 *
 * <h2>⚠ THE CLIP IS LOADED ONCE AND REUSED</h2>
 *
 * Opening a {@link Clip} allocates a line on the system mixer, and a machine has a finite number.
 * Opening one per notification leaks lines until playback silently stops working — which presents as
 * "the sound stopped after a while" and is very hard to attribute.
 *
 * <h2>⚠ EVERY FAILURE IS SILENT, and that is deliberate</h2>
 *
 * A headless CI box, a machine with no mixer, a locked audio device, a WAV that will not decode: none
 * of those is worth a dialog, a log spike, or a thrown exception on the path that was about to tell
 * the player they had mail. Sound is decoration. The message still arrives.
 */
public final class Sfx {

    private Sfx() {}

    private static final Logger LOG = Logger.getLogger(Sfx.class.getName());

    private static final String MESSAGE = "/io/github/stoicswe/eyeandsickle/client/sound/message.wav";

    /** Loaded lazily and kept. Null once loading has failed, so it is not retried on every message. */
    private static volatile Clip messageClip;

    private static volatile boolean tried;

    /**
     * How loud, 0–100. Read from the player's settings on every play.
     *
     * <p>⚠ Read rather than cached, for the same reason {@code profile.appearance()} must not be
     * cached: the player can change it in Settings while the client is running, and a cached value
     * would leave the slider apparently doing nothing until a restart.
     */
    private static volatile int volumePercent = 60;

    /** ⚠ Called by Settings on every change. See {@link #volumePercent}. */
    public static void setVolumePercent(int percent) {
        volumePercent = Math.max(0, Math.min(100, percent));
    }

    public static int volumePercent() {
        return volumePercent;
    }

    /**
     * Plays the new-message chime, if sound is on and the machine can.
     *
     * <p>⚠ <b>Zero means silent and returns immediately</b> — not "play at zero gain". A muted client
     * should not be opening mixer lines at all, and on some drivers a zero-gain play is still an
     * audible click.
     *
     * <p>⚠ Safe to call from any thread. It is called from the FX thread, and playback is handed to
     * the mixer's own thread by {@code Clip.start()} — nothing here blocks on the sound finishing.
     */
    public static void message() {
        if (volumePercent <= 0) {
            return;
        }
        Clip clip = clip();
        if (clip == null) {
            return;
        }
        try {
            synchronized (Sfx.class) {
                // ⚠ Rewound before every play, or a second notification inside a second finds the
                // clip already at its end and plays nothing — which reads as the sound being
                // unreliable rather than as a cursor that was never reset.
                clip.stop();
                clip.setFramePosition(0);
                applyGain(clip);
                clip.start();
            }
        } catch (RuntimeException failed) {
            LOG.log(Level.FINE, "could not play the message chime", failed);
        }
    }

    /**
     * Maps 0–100 onto the mixer's gain control, in decibels.
     *
     * <h2>⚠ A LINEAR SLIDER IS NOT A LINEAR LOUDNESS, and dB is not a percentage</h2>
     *
     * {@code MASTER_GAIN} is in <b>decibels</b>, and its range is typically about −80…+6. Feeding it
     * a percentage directly makes 50 mean "+50 dB" — clamped to the maximum, so every setting above a
     * few percent would sound identical and full volume. The conversion is
     * {@code 20 · log10(fraction)}, which is what turns a slider into a volume control rather than an
     * on/off switch with extra steps.
     */
    private static void applyGain(Clip clip) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        double fraction = volumePercent / 100.0d;
        float decibels = (float) (20.0d * Math.log10(fraction));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }

    /** Loads the clip once. Returns null forever after a failure rather than retrying per message. */
    private static Clip clip() {
        Clip existing = messageClip;
        if (existing != null || tried) {
            return existing;
        }
        synchronized (Sfx.class) {
            if (tried) {
                return messageClip;
            }
            tried = true;
            try (InputStream raw = Sfx.class.getResourceAsStream(MESSAGE)) {
                if (raw == null) {
                    LOG.fine("no message chime on the classpath");
                    return null;
                }
                // ⚠ Read fully into memory first. AudioSystem needs a mark-supporting stream to sniff
                // the format, and a resource stream inside a jar does not always provide one — the
                // failure is an UnsupportedAudioFileException on a file that is perfectly valid.
                byte[] bytes = raw.readAllBytes();
                try (AudioInputStream audio =
                        AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(audio);
                    messageClip = clip;
                    return clip;
                }
            } catch (Exception | UnsatisfiedLinkError unavailable) {
                // ⚠ Catches Error as well as Exception: a headless or driverless machine fails at the
                // native layer, and a notification path that threw would take the notification with
                // it. Sound is decoration; the message still arrives.
                LOG.log(Level.FINE, "sound is unavailable on this machine", unavailable);
                return null;
            }
        }
    }

    /** Test seam — releases the mixer line and allows a reload. */
    static synchronized void reset() {
        if (messageClip != null) {
            messageClip.close();
            messageClip = null;
        }
        tried = false;
    }
}
