package io.github.stoicswe.eyeandsickle.client.sound;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * One thing currently making a noise.
 *
 * <p>A voice is asked to add itself to a buffer and says whether it is still alive. That is the whole
 * contract, and it is what lets {@link SoftMixer} treat a two-minute streamed music bed and a 40 ms
 * click as the same kind of object: the mixer never learns which it has.
 *
 * <h2>⚠ EVERY METHOD HERE RUNS ON THE AUDIO THREAD AND MUST NOT BLOCK</h2>
 *
 * There is a hard deadline behind these calls — the device consumes samples at a fixed rate, and a
 * buffer delivered late is a gap the player hears as a click. So no file I/O, no locks that anything
 * else holds, no allocation in the steady state. {@link Streamed} obeys this by reading its file into
 * memory <i>before</i> it becomes a voice: decoding is arithmetic and is fine here, waiting on a disk
 * is not.
 */
sealed interface Voice {

    /** Which slider governs this voice. */
    Bus bus();

    /**
     * Adds this voice into {@code out}, which is interleaved stereo and already holds other voices.
     *
     * <h2>⚠ THE BUS GAIN IS HANDED IN PER BUFFER AND NEVER BAKED INTO THE VOICE</h2>
     *
     * It would be simpler to fold the slider into a voice's gain when it is built. Two things break
     * if you do. A volume change would then apply only to sounds started <i>after</i> it, so dragging
     * the music slider would do nothing to the music currently playing — the one case anybody tests
     * it in. And ducking, which moves the music bus every buffer, would be impossible to express at
     * all.
     *
     * <p>⚠ Applying it to the summed buffer afterwards instead is the other tempting shape and it is
     * wrong outright: once music and effects are in the same accumulator there is no gain that is
     * correct for both, so whichever is scaled correctly leaves the other at the wrong level.
     *
     * @param out interleaved stereo accumulator, {@code frames * 2} long
     * @param frames how many stereo frames to contribute
     * @param busGain the current multiplier for this voice's bus, including any duck
     * @return false once the voice is finished and should be dropped
     */
    boolean mix(float[] out, int frames, float busGain);

    /** Asks the voice to fade out over roughly {@code frames} and then finish. */
    void release(int frames);

    /**
     * Roughly how loud this voice is right now, for voice stealing.
     *
     * <p>⚠ Used to choose a victim when the mixer is full — the quietest loses. Stealing the
     * <i>oldest</i> is the obvious alternative and is worse: the oldest voice is very often a long
     * one that is still prominent, while the quietest is by definition the one whose disappearance is
     * least likely to be noticed.
     */
    float level();

    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A voice over a fully decoded {@link Sample}: effects, and anything short enough to hold.
     *
     * <h2>⚠ THE CURSOR IS FRACTIONAL, WHICH IS WHAT MAKES PITCH VARIATION POSSIBLE</h2>
     *
     * Reading the sample at a rate other than 1.0 frame per output frame plays it higher or lower.
     * That is not an effect for its own sake: it is the fix for the single most fatiguing thing a game
     * can do with audio, which is to play the <i>identical</i> waveform for a repeated action. Ten
     * keystrokes with a few percent of pitch spread read as a keyboard; ten bit-identical ones read as
     * a machine, and after a minute they read as a fault.
     *
     * <p>⚠ Interpolated linearly between neighbouring frames rather than taking the nearest. Nearest
     * -neighbour resampling at a non-integer ratio adds a broadband hiss that is clearly audible on
     * the quiet tail of a sound — where, unhelpfully, the ear is most sensitive to it.
     */
    final class Sampled implements Voice {

        private final Sample sample;
        private final Bus bus;
        private final float gainLeft;
        private final float gainRight;
        private final double rate;
        private final boolean looping;

        private double cursor;
        private float envelope = 1.0f;
        private float envelopeStep;

        Sampled(Sample sample, Bus bus, float gain, float pan, double rate, boolean looping) {
            this.sample = sample;
            this.bus = bus;
            this.rate = rate;
            this.looping = looping;
            // Constant-power pan, the same reasoning as the crossfade: a linear pan loses ~3 dB in
            // the middle, so a sound panned to centre is quieter than the same sound hard left.
            double angle = (Math.max(-1.0f, Math.min(1.0f, pan)) + 1.0) * Math.PI / 4.0;
            this.gainLeft = (float) (gain * Math.cos(angle));
            this.gainRight = (float) (gain * Math.sin(angle));
        }

        @Override
        public Bus bus() {
            return bus;
        }

        @Override
        public float level() {
            return Math.max(gainLeft, gainRight) * envelope;
        }

        @Override
        public void release(int frames) {
            if (envelopeStep == 0.0f) {
                envelopeStep = envelope / Math.max(1, frames);
            }
        }

        @Override
        public boolean mix(float[] out, int frames, float busGain) {
            float[] data = sample.data();
            int total = sample.frameCount();
            if (total == 0) {
                return false;
            }
            for (int f = 0; f < frames; f++) {
                if (cursor >= total - 1) {
                    if (!looping) {
                        return false;
                    }
                    // ⚠ Wrapped by subtraction, never reset to zero. Setting it to 0 discards the
                    // fractional part, so at a non-integer rate every loop starts a fraction of a
                    // frame earlier than the last and the loop point drifts audibly over minutes.
                    cursor -= total - 1;
                }
                int index = (int) cursor;
                float fraction = (float) (cursor - index);
                int left = index * 2;
                // Linear interpolation between this frame and the next.
                float l = data[left] + (data[left + 2] - data[left]) * fraction;
                float r = data[left + 1] + (data[left + 3] - data[left + 1]) * fraction;

                int slot = f * 2;
                out[slot] += l * gainLeft * envelope * busGain;
                out[slot + 1] += r * gainRight * envelope * busGain;

                cursor += rate;
                if (envelopeStep > 0.0f) {
                    envelope -= envelopeStep;
                    if (envelope <= 0.0f) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * A voice that decodes as it goes: music beds, and anything too long to hold decoded.
     *
     * <h2>⚠ THE FILE IS IN MEMORY; THE DECODED AUDIO IS NOT. THAT IS THE WHOLE POINT.</h2>
     *
     * A two-minute bed as 22 kHz mono WAV is ~2.6 MB on disk and <b>42 MB</b> once decoded to the
     * engine's 44.1 kHz stereo float — sixteen times larger, held for as long as the track is
     * playing, on top of a JavaFX client that already holds window snapshots for the frost. So the
     * compressed bytes are read once into a {@code byte[]} and the decode happens a buffer at a time.
     *
     * <p>⚠ Reading the file into memory <i>first</i> is what keeps {@link #mix} free of I/O. Decoding
     * from a {@code ByteArrayInputStream} is pure arithmetic and safe on the audio thread; decoding
     * from a jar entry or a file would put a disk wait behind a hard deadline.
     *
     * <h2>⚠ LOOPING RE-OPENS THE DECODER RATHER THAN SEEKING</h2>
     *
     * {@code AudioInputStream} has no reliable seek — {@code reset()} depends on mark support that a
     * converted stream does not promise. Re-opening the chain over the same byte array is exact,
     * costs one allocation per loop rather than per buffer, and cannot drift.
     */
    final class Streamed implements Voice {

        private final byte[] file;
        private final String name;
        private final boolean looping;
        private final float gain;

        private AudioInputStream decoder;
        private byte[] scratch = new byte[0];

        /** Ramp state. See {@link #gainAt}. */
        private int fadeLength;

        private int fadeCursor;
        private boolean fadingOut;
        private boolean finished;

        Streamed(String name, byte[] file, boolean looping, float gain, int fadeInFrames) {
            this.name = name;
            this.file = file;
            this.looping = looping;
            this.gain = gain;
            this.fadeLength = Math.max(1, fadeInFrames);
            this.fadeCursor = 0;
            this.fadingOut = false;
            open();
        }

        String name() {
            return name;
        }

        @Override
        public Bus bus() {
            return Bus.MUSIC;
        }

        @Override
        public float level() {
            return gain * gainAt(fadeCursor);
        }

        @Override
        public void release(int frames) {
            if (fadingOut) {
                return;
            }
            fadingOut = true;
            // ⚠ Restarted from zero rather than continued. A track told to stop part-way through its
            // own fade-in would otherwise take the remainder of that ramp as its fade-out length,
            // which is an arbitrary number and is sometimes one frame.
            fadeLength = Math.max(1, frames);
            fadeCursor = 0;
        }

        @Override
        public boolean mix(float[] out, int frames, float busGain) {
            if (finished || decoder == null) {
                return false;
            }
            int wanted = frames * 4; // 2 channels × 2 bytes
            if (scratch.length < wanted) {
                scratch = new byte[wanted];
            }
            int filled = read(scratch, wanted);
            if (filled <= 0) {
                return false;
            }

            // ⚠ The ramp is evaluated at the two ENDS of the buffer and interpolated across it,
            // rather than per frame. A buffer is ~20 ms, so the error against a true cosine is far
            // below audibility, and it turns 882 trigonometric calls per buffer into two.
            float startGain = gain * gainAt(fadeCursor) * busGain;
            float endGain = gain * gainAt(fadeCursor + frames) * busGain;

            int usableFrames = filled / 4;
            for (int f = 0; f < usableFrames; f++) {
                int byteAt = f * 4;
                float l = pcm(scratch, byteAt);
                float r = pcm(scratch, byteAt + 2);
                float ramp = startGain + (endGain - startGain) * ((float) f / usableFrames);
                int slot = f * 2;
                out[slot] += l * ramp;
                out[slot + 1] += r * ramp;
            }

            fadeCursor += usableFrames;
            if (fadingOut && fadeCursor >= fadeLength) {
                finished = true;
                return false;
            }
            return true;
        }

        /** Reads up to {@code wanted} bytes, re-opening at the end of the file when looping. */
        private int read(byte[] into, int wanted) {
            int filled = 0;
            while (filled < wanted) {
                int got;
                try {
                    got = decoder.read(into, filled, wanted - filled);
                } catch (Exception broken) {
                    return filled;
                }
                if (got > 0) {
                    filled += got;
                    continue;
                }
                if (!looping) {
                    break;
                }
                open();
                if (decoder == null) {
                    break;
                }
            }
            // ⚠ Anything not filled is zeroed. The scratch buffer is reused, so a short final read
            // would otherwise mix the tail of the PREVIOUS buffer — a fragment of audio repeating at
            // the very end of a track, which sounds exactly like a corrupt file.
            java.util.Arrays.fill(into, filled, wanted, (byte) 0);
            return filled;
        }

        private void open() {
            close();
            try {
                AudioInputStream source =
                        AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(file)));
                decoder = AudioSystem.getAudioInputStream(Sample.CANONICAL, source);
            } catch (Exception | UnsatisfiedLinkError unusable) {
                decoder = null;
            }
        }

        void close() {
            if (decoder != null) {
                try {
                    decoder.close();
                } catch (Exception ignored) {
                    // Closing a decoder over a byte array cannot fail in a way that matters.
                }
                decoder = null;
            }
        }

        /**
         * The fade envelope at a frame offset — equal power in both directions.
         *
         * <p>⚠ Once a fade-in has completed this returns exactly 1.0 and stays there. A ramp that
         * kept evaluating would eventually run its progress past 1, and cosine past π/2 goes
         * <i>negative</i> — the track would invert rather than hold.
         */
        private float gainAt(int frameOffset) {
            double progress = (double) frameOffset / fadeLength;
            if (fadingOut) {
                return Gain.fadeOut(progress);
            }
            return progress >= 1.0 ? 1.0f : Gain.fadeIn(progress);
        }

        private static float pcm(byte[] bytes, int at) {
            int lo = bytes[at] & 0xFF;
            int hi = bytes[at + 1];
            return ((hi << 8) | lo) / 32768.0f;
        }
    }
}
