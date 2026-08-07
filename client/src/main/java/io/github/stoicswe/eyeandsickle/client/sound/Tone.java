package io.github.stoicswe.eyeandsickle.client.sound;

import java.util.Random;

/**
 * Sounds the client draws for itself, in the way it already draws its cursor, its window chrome, its
 * icons and its wallpaper.
 *
 * <h2>⚠ WHY A SYNTHESISER AND NOT FIVE MORE WAV FILES</h2>
 *
 * This client bundles no third-party artwork, hand-rolls its own window manager and draws every mark
 * on screen as geometry — {@code SecurityMark}, {@code SectionMark}, {@code MailMark} and the flash
 * overlay's warning triangle are all polygons rather than glyphs, and §9 bans an icon set outright.
 * Generating a confirmation blip is the same decision one sense along, and it buys the same things:
 * nothing to license, nothing to ship, and a sound that follows a constant rather than a file when it
 * needs to change.
 *
 * <p>It also answers the size problem honestly. Every generated effect costs <b>zero bytes</b> in
 * five platform uber jars and a jpackage image, where a recorded one costs its length times six.
 *
 * <h2>⚠ THIS IS NOT A REPLACEMENT FOR RECORDED AUDIO, AND IT MUST NOT BECOME ONE</h2>
 *
 * What is here suits an interface: short, tonal, unmistakable, and completely uninteresting to listen
 * to twice. It cannot make a room tone, a mechanism, a voice or a music bed, and attempting any of
 * those with oscillators is how a game ends up sounding like a 1980s answering machine. Anything
 * atmospheric is a {@code .wav} through {@link Sample#load}.
 *
 * <h2>⚠ GENERATION IS DETERMINISTIC. PER-PLAY VARIATION IS NOT. THE TWO ARE DIFFERENT THINGS.</h2>
 *
 * Every sample here is built from a fixed seed, so the same constant produces bit-identical audio on
 * every machine and every launch. That is not fussiness: a generated asset that differed per run
 * would mean two players hearing different games, and no render or regression check could ever
 * compare one against a previous one.
 *
 * <p>The randomness that <i>is</i> allowed is the small pitch spread {@link Sfx} applies when a sound
 * is triggered, and it is safe for the reason all decoration here is safe — <b>nothing derived from
 * it reaches a rule</b>. No gate, price, threshold or outcome can see it. The moment audio influences
 * anything the engine decides, it stops being decoration and this exemption stops applying.
 */
final class Tone {

    private Tone() {}

    private static final float RATE = 44100.0f;

    /**
     * A pitched blip with a percussive decay: the interface's "yes".
     *
     * <p>⚠ The decay is exponential rather than linear. A linear fade to zero leaves a discontinuity
     * in the <i>slope</i> at the end which is audible as a faint tick — the sound stops rather than
     * ends. Exponential decay approaches zero asymptotically and simply disappears.
     */
    static Sample blip(String name, double hz, int ms, double harmonic) {
        int frames = frames(ms);
        float[] out = new float[frames * 2];
        double step = 2 * Math.PI * hz / RATE;
        // Time constant chosen so the tail is ~60 dB down by the end of the requested length; a
        // shorter one truncates audibly, a longer one leaves the sound still sounding when it stops.
        double decay = 6.9 / frames;
        for (int f = 0; f < frames; f++) {
            double envelope = Math.exp(-decay * f) * attack(f);
            // A single sine is thin and synthetic. One quiet harmonic gives it enough body to sit in
            // a mix without becoming a chord.
            double value = (Math.sin(step * f) + 0.3 * Math.sin(step * harmonic * f)) * 0.7 * envelope;
            write(out, f, (float) value);
        }
        return Sample.of(name, out);
    }

    /**
     * Two blips, the second lower: the interface's "no".
     *
     * <p>⚠ Falling rather than rising, because that reading is close to universal and costs the
     * player nothing to learn. It is also the only distinction between confirm and refuse that
     * survives a very short sound — timbre does not, at 90 ms.
     */
    static Sample refusal(String name, double hz, int ms) {
        int frames = frames(ms);
        float[] out = new float[frames * 2];
        int half = frames / 2;
        for (int f = 0; f < frames; f++) {
            boolean second = f >= half;
            int local = second ? f - half : f;
            double pitch = second ? hz * 0.72 : hz;
            double decay = 6.9 / half;
            double envelope = Math.exp(-decay * local) * attack(local);
            double value = Math.sin(2 * Math.PI * pitch / RATE * local) * 0.7 * envelope;
            write(out, f, (float) value);
        }
        return Sample.of(name, out);
    }

    /**
     * A dry filtered noise tick: keystrokes, row selection, anything mechanical.
     *
     * <p>⚠ Deliberately unpitched. A tonal sound repeated dozens of times a minute becomes a melody
     * the ear starts predicting, and then resenting; noise never does. It is also the one effect that
     * genuinely needs {@link Sfx}'s pitch spread, because it is the one that repeats fastest.
     */
    static Sample tick(String name, int ms, double brightness) {
        int frames = frames(ms);
        float[] out = new float[frames * 2];
        // ⚠ Fixed seed. See the class note: a generated asset must be the same every run.
        Random noise = new Random(0x7A11L);
        double previous = 0;
        double decay = 6.9 / frames;
        for (int f = 0; f < frames; f++) {
            double white = noise.nextDouble() * 2 - 1;
            // One-pole low pass. Raw white noise is a hiss; rolling the top off turns it into a tap.
            previous = previous + brightness * (white - previous);
            double value = previous * 0.6 * Math.exp(-decay * f) * attack(f);
            write(out, f, (float) value);
        }
        return Sample.of(name, out);
    }

    /**
     * A rising or falling sweep: something started, something finished.
     *
     * <p>⚠ The phase is accumulated, never computed as {@code sin(2π·f(t)·t)}. That closed form is the
     * obvious way to write a sweep and it is wrong — it makes the <i>argument</i> to sine sweep rather
     * than the frequency, so the pitch changes at twice the intended rate and the start of the sound
     * is not the frequency asked for.
     */
    static Sample sweep(String name, double fromHz, double toHz, int ms) {
        int frames = frames(ms);
        float[] out = new float[frames * 2];
        double phase = 0;
        for (int f = 0; f < frames; f++) {
            double progress = (double) f / frames;
            double hz = fromHz + (toHz - fromHz) * progress;
            phase += 2 * Math.PI * hz / RATE;
            // A gentle arch rather than a decay: a sweep that faded out would put all its emphasis on
            // the pitch it started from, which is the opposite of what a sweep is for.
            double envelope = Math.sin(Math.PI * progress) * attack(f);
            write(out, f, (float) (Math.sin(phase) * 0.55 * envelope));
        }
        return Sample.of(name, out);
    }

    /**
     * The first few milliseconds of any sound, ramped from zero.
     *
     * <h2>⚠ WITHOUT THIS, EVERY GENERATED SOUND STARTS WITH A CLICK</h2>
     *
     * A waveform that begins at a non-zero value is a step discontinuity, and a step contains every
     * frequency — so a sine that starts at its peak is heard as a click followed by a tone. Two
     * milliseconds of ramp is inaudible as a fade and removes it completely. This is the single most
     * common defect in hand-written game audio.
     */
    private static double attack(int frame) {
        int ramp = (int) (RATE * 0.002);
        return frame >= ramp ? 1.0 : (double) frame / ramp;
    }

    private static int frames(int ms) {
        return Math.max(1, (int) (RATE * ms / 1000.0));
    }

    /** Generated sounds are centred, so both channels get the same value. */
    private static void write(float[] interleaved, int frame, float value) {
        interleaved[frame * 2] = value;
        interleaved[frame * 2 + 1] = value;
    }
}
