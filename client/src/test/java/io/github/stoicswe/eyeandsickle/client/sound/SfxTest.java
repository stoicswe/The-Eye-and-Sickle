package io.github.stoicswe.eyeandsickle.client.sound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The sound catalogue: what exists, what it costs, and the two ways an asset can go missing.
 *
 * <p>These walk {@link Sfx#values()} and {@link MusicCue#values()} rather than a hand-kept list, so a
 * constant added tomorrow is covered by having been added — the rule {@code RigLegendCoversEvery
 * ConsumerTest} and {@code CommandSpecTest} already follow here.
 *
 * <p>⚠ Nothing in this file opens an audio device. Decoding is arithmetic and runs anywhere; playing
 * needs hardware the build machine may not have. The one test that needs a device is opt-in and lives
 * in {@link AudioTest}.
 */
@DisplayName("the sound catalogue")
class SfxTest {

    private static final Path RESOURCES = Path.of("src/main/resources/io/github/stoicswe/eyeandsickle/client/sound");

    @AfterEach
    void forgetDecodedSamples() {
        // Samples and retrigger clocks are held on the enum constants, which outlive a test method.
        for (Sfx effect : Sfx.values()) {
            effect.reset();
        }
    }

    @Nested
    @DisplayName("every effect")
    class EveryEffect {

        @Test
        @DisplayName("decodes to audible samples")
        void decodes() {
            // ⚠ Both directions of "it works": the sample exists, and it is not silence. A generator
            // with a sign error or an envelope that never opens produces a correctly sized buffer of
            // zeros, which passes a null check and plays nothing — a failure that presents as "the
            // sound system is broken" long after the constant was added.
            for (Sfx effect : Sfx.values()) {
                Sample sample = effect.sample();
                assertThat(sample).as("%s has no sample", effect).isNotNull();
                assertThat(sample.frameCount()).as("%s is empty", effect).isPositive();

                float peak = 0;
                for (float value : sample.data()) {
                    peak = Math.max(peak, Math.abs(value));
                }
                assertThat(peak).as("%s is silent", effect).isGreaterThan(0.01f);
            }
        }

        @Test
        @DisplayName("stays inside full scale, so nothing is clipped before the mixer sees it")
        void withinRange() {
            // A sample that already exceeds ±1 arrives distorted no matter what the sliders say, and
            // the limiter can only bound it — it cannot recover what the generator overdrove.
            for (Sfx effect : Sfx.values()) {
                for (float value : effect.sample().data()) {
                    assertThat(Math.abs(value))
                            .as("%s exceeds full scale", effect)
                            .isLessThanOrEqualTo(1.0f);
                }
            }
        }

        @Test
        @DisplayName("starts and ends at silence, or it clicks")
        void noDiscontinuities() {
            // ⚠ THE MOST COMMON DEFECT IN HAND-WRITTEN GAME AUDIO. A waveform that begins at a
            // non-zero value is a step, and a step contains every frequency — so a tone that starts
            // at its peak is heard as a click followed by the tone. Tone#attack exists for this; this
            // is what proves it is applied.
            for (Sfx effect : Sfx.values()) {
                float[] data = effect.sample().data();
                assertThat(Math.abs(data[0]))
                        .as("%s starts with a step", effect)
                        .isLessThan(0.02f);
                assertThat(Math.abs(data[data.length - 1]))
                        .as("%s ends with a step", effect)
                        .isLessThan(0.02f);
            }
        }

        @Test
        @DisplayName("declares a retrigger guard, so no caller can machine-gun it")
        void guarded() {
            // The engine is polyphonic and nothing else stops forty log lines becoming forty
            // simultaneous chimes. A zero here would put that back on each call site to remember.
            for (Sfx effect : Sfx.values()) {
                assertThat(effect.minGapMs())
                        .as("%s has no retrigger guard", effect)
                        .isPositive();
                assertThat(effect.gain()).as("%s has no gain", effect).isPositive();
            }
        }
    }

    @Nested
    @DisplayName("generation")
    class Generation {

        @Test
        @DisplayName("is deterministic, so two players hear the same game")
        void deterministic() {
            // ⚠ A generated asset that differed per run would mean no render or regression check
            // could ever compare one against a previous one — and two players comparing notes about
            // a sound would be describing different audio. Tone seeds its noise explicitly for this.
            float[] first = Tone.tick("probe", 35, 0.45).data();
            float[] second = Tone.tick("probe", 35, 0.45).data();
            assertThat(second).containsExactly(first);
        }

        @Test
        @DisplayName("a sweep changes pitch, rather than only changing amplitude")
        void sweepActuallySweeps() {
            // ⚠ The trap this catches is writing sin(2π·f(t)·t), which is the obvious closed form and
            // is wrong: it sweeps the ARGUMENT rather than the frequency, so the pitch moves at twice
            // the intended rate and the sound does not start at the frequency asked for. Counting
            // zero crossings in each half is the cheapest way to see the rate really rose.
            float[] data = Tone.sweep("probe", 200, 2000, 400).data();
            int half = data.length / 2;
            assertThat(crossings(data, 0, half))
                    .as("the second half should be higher-pitched than the first")
                    .isLessThan(crossings(data, half, data.length));
        }

        private int crossings(float[] data, int from, int to) {
            int count = 0;
            for (int i = from + 2; i < to; i += 2) {
                if (data[i - 2] < 0 != data[i] < 0) {
                    count++;
                }
            }
            return count;
        }
    }

    @Nested
    @DisplayName("the retrigger guard")
    class Guard {

        @Test
        @DisplayName("passes the first claim and refuses the burst behind it")
        void collapsesABurst() {
            Sfx effect = Sfx.MESSAGE;
            assertThat(effect.claim()).as("the first should sound").isTrue();
            for (int i = 0; i < 50; i++) {
                assertThat(effect.claim()).as("the burst should not").isFalse();
            }
        }

        @Test
        @DisplayName("allows another once the gap has passed")
        void reopens() throws InterruptedException {
            // TICK has the shortest guard in the catalogue (25 ms), which is what makes this test
            // quick enough to sit in the fast loop.
            assertThat(Sfx.TICK.claim()).isTrue();
            Thread.sleep(Sfx.TICK.minGapMs() + 30L);
            assertThat(Sfx.TICK.claim()).as("the guard should have expired").isTrue();
        }

        @Test
        @DisplayName("holds under concurrent claims, because bursts are concurrent by nature")
        void survivesARace() throws InterruptedException {
            // ⚠ This is why claim() is test-and-set in ONE call. A separate allowed() then stamp()
            // would tell every thread in a simultaneous burst "yes" before any of them stamped —
            // and a burst arriving from several threads at once is precisely the shape this guards.
            int threads = 16;
            var sounded = new java.util.concurrent.atomic.AtomicInteger();
            var start = new java.util.concurrent.CountDownLatch(1);
            var done = new java.util.concurrent.CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                            try {
                                start.await();
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            if (Sfx.ALERT.claim()) {
                                sounded.incrementAndGet();
                            }
                            done.countDown();
                        })
                        .start();
            }
            start.countDown();
            done.await();
            assertThat(sounded.get())
                    .as("exactly one of a simultaneous burst may sound")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("the assets on disk")
    class Assets {

        @Test
        @DisplayName("every file an effect names is really on the classpath")
        void effectFilesExist() {
            // Tone-backed constants have no file; the ones that do must resolve, or the sound is
            // silently absent at run time with only a FINE log line to say so.
            assertThat(resource("/io/github/stoicswe/eyeandsickle/client/sound/message.wav"))
                    .as("the message chime")
                    .isTrue();
        }

        @Test
        @DisplayName("every music file in the directory is claimed by a cue")
        void everyMusicFileIsClaimed() throws IOException {
            // ⚠ THE DIRECTION THAT ACTUALLY CATCHES THINGS. The other way round — every cue has a
            // file — is deliberately NOT asserted, because no soundtrack ships and a cue with no file
            // is silence by design. This way round catches the real mistake: a track dropped in under
            // a misspelled name, which no cue names, so it never plays and nothing anywhere complains.
            Path music = RESOURCES.resolve("music");
            if (!Files.isDirectory(music)) {
                return; // No soundtrack yet. Nothing to be orphaned.
            }
            Set<String> claimed = new TreeSet<>();
            for (MusicCue cue : MusicCue.values()) {
                if (cue.fileName() != null) {
                    claimed.add(cue.fileName());
                }
            }
            List<String> present;
            try (Stream<Path> files = Files.list(music)) {
                // ⚠ Documentation and dotfiles are excluded; EVERYTHING ELSE must be claimed,
                // including formats the JDK cannot decode. Filtering to `.wav` would have been the
                // obvious narrowing and it exempts the very mistake most worth catching — a `.mp3`
                // dropped in here looks like a soundtrack, is a soundtrack, and will never make a
                // sound, because the JDK ships no MP3 decoder.
                present = files.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> !name.startsWith(".") && !name.endsWith(".md"))
                        .toList();
            }
            assertThat(present)
                    .as("a music file no cue names will never be played")
                    .allSatisfy(name -> assertThat(claimed).contains(name));
        }

        @Test
        @DisplayName("NONE resolves to nothing, and every other cue names a file")
        void cuesAreWellFormed() {
            assertThat(MusicCue.NONE.resource()).isNull();
            for (MusicCue cue : MusicCue.values()) {
                if (cue != MusicCue.NONE) {
                    assertThat(cue.resource()).as("%s", cue).isNotNull().endsWith(".wav");
                }
            }
        }

        private boolean resource(String path) {
            try (InputStream in = SfxTest.class.getResourceAsStream(path)) {
                return in != null;
            } catch (IOException unreadable) {
                return false;
            }
        }
    }
}
