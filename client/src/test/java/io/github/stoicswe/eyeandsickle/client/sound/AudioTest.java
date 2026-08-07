package io.github.stoicswe.eyeandsickle.client.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The facade, and the promise that matters most: none of it may ever throw.
 *
 * <h2>⚠ THESE RUN ON A MACHINE WITH NO SOUND CARD, DELIBERATELY</h2>
 *
 * That is the interesting case rather than a limitation. Every call here is made from a path that was
 * about to do something the player actually cares about — deliver a notification, open a window,
 * change a screen — so an exception from the audio engine would take that with it. A headless CI box
 * is the cheapest available simulation of a player whose audio device is missing, busy or broken.
 */
@DisplayName("the audio facade")
class AudioTest {

    @AfterEach
    void forget() {
        Audio.shared().reset();
    }

    @Nested
    @DisplayName("with no usable device")
    class Degraded {

        @Test
        @DisplayName("playing an effect never throws")
        void playIsSafe() {
            // ⚠ The whole reason the package catches Throwable in its loading paths. A machine with
            // no audio stack fails in the NATIVE layer, which is an Error rather than an Exception,
            // and a `catch (Exception)` would let it past — taking down the notification that was
            // being delivered when it happened.
            assertThatCode(() -> {
                        for (Sfx effect : Sfx.values()) {
                            Audio.shared().play(effect);
                        }
                    })
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("every control is safe to call, in any order, at any value")
        void controlsAreSafe() {
            assertThatCode(() -> {
                        Audio audio = Audio.shared();
                        audio.setMasterVolume(-5);
                        audio.setMasterVolume(1000);
                        audio.setBusVolume(Bus.MUSIC, 50);
                        audio.setBusVolume(Bus.EFFECTS, 0);
                        audio.setDuckDepth(-1);
                        audio.setDuckingEnabled(true);
                        audio.setCrossfadeMs(-100);
                        audio.setDevice(null);
                        audio.setDevice("a device that certainly does not exist");
                        audio.setMuted(true);
                        audio.setMuted(false);
                        audio.warmUp();
                    })
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("asking for music that does not exist is silence, not a failure")
        void missingMusicIsSilent() {
            // No soundtrack ships, so this is the normal path for every cue on today's build.
            assertThatCode(() -> {
                        for (MusicCue cue : MusicCue.values()) {
                            Audio.shared().music(cue);
                        }
                    })
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the device list is never null, even where enumeration fails outright")
        void deviceListIsSafe() {
            assertThat(Audio.outputDevices()).isNotNull();
        }

        @Test
        @DisplayName("status reports a real state rather than guessing")
        void statusIsHonest() {
            Audio.Status status = Audio.shared().status();
            assertThat(status).isNotNull();
            assertThat(status.device()).isNotNull();
            // ⚠ Running and failed are mutually exclusive. The Settings page branches on exactly
            // these two, and a state where both were true would render "playing through" over a
            // device that had failed to open.
            assertThat(status.running() && status.failed()).isFalse();
        }
    }

    @Nested
    @DisplayName("music")
    class Music {

        @Test
        @DisplayName("asking for the cue already playing is free")
        void idempotent() {
            // ⚠ THE MOST LIKELY WAY FOR THIS TO GO WRONG IN PLAY. Music call sites are screen
            // changes, and a screen is re-entered constantly — a window closes, a dialog dismisses,
            // a tab is reselected. Without this the bed restarts from the top every time the player
            // closes a window, which is far more noticeable than any bug in the mixer.
            Audio audio = Audio.shared();
            audio.music(MusicCue.DECK);
            assertThat(audio.currentMusic()).isEqualTo(MusicCue.DECK);
            audio.music(MusicCue.DECK);
            assertThat(audio.currentMusic()).isEqualTo(MusicCue.DECK);
        }

        @Test
        @DisplayName("null is silence rather than a crash")
        void nullIsNone() {
            Audio.shared().music(null);
            assertThat(Audio.shared().currentMusic()).isEqualTo(MusicCue.NONE);
        }

        @Test
        @DisplayName("muting remembers the cue, so unmuting resumes rather than forgetting")
        void mutingKeepsTheCue() {
            // ⚠ Mute must not be implemented by writing zero into the volume setting — that would
            // destroy whatever the player had chosen the first time they alt-tabbed, and they would
            // come back to a game that had forgotten its own volume.
            Audio audio = Audio.shared();
            audio.music(MusicCue.MENU);
            audio.setMuted(true);
            assertThat(audio.currentMusic()).as("the cue survives a mute").isEqualTo(MusicCue.MENU);
            audio.setMuted(false);
            assertThat(audio.currentMusic()).isEqualTo(MusicCue.MENU);
        }
    }

    @Nested
    @DisplayName("what the package exposes")
    class Encapsulation {

        @Test
        @DisplayName("only Audio, Sfx, MusicCue and Bus are public")
        void oneDoor() throws IOException {
            // ⚠ A view that reached SoftMixer directly could open a device, place a voice, or apply a
            // gain outside the player's sliders — and none of that would be visible from Settings.
            // Checked in source rather than by reflection because the rule is about what a call site
            // can WRITE, and the compiler is what enforces it; this is what stops somebody widening
            // a class to fix a compile error, which is exactly how Status came to be on the facade.
            Path sources = Path.of("src/main/java/io/github/stoicswe/eyeandsickle/client/sound");
            List<Path> files;
            try (Stream<Path> walk = Files.walk(sources)) {
                files = walk.filter(path -> path.toString().endsWith(".java")).toList();
            }
            assertThat(files).isNotEmpty();
            for (Path file : files) {
                String name = file.getFileName().toString().replace(".java", "");
                boolean isPublic = Files.readString(file).contains("public final class " + name)
                        || Files.readString(file).contains("public enum " + name);
                if (isPublic) {
                    assertThat(name)
                            .as("%s is public; only the facade and the catalogues may be", name)
                            .isIn("Audio", "Sfx", "MusicCue", "Bus");
                }
            }
        }
    }

    /**
     * The one test that needs real hardware.
     *
     * <h2>⚠ OPT-IN, AND THE GATE IS ON THE CLASS RATHER THAN EACH METHOD</h2>
     *
     * Every other test in this file is inert — it builds objects and looks at them. These make a
     * <b>noise on the developer's machine</b> and hold a real device, which is a side effect a build
     * has no business having; and on a machine with no device they would pass by doing nothing, which
     * is worse than not running. Same arrangement, for the same reasons, as
     * {@code SecretStoreTest.Roundtrip}.
     *
     * <pre>{@code
     * mvn -pl client test -Deyeandsickle.audio.device=true
     * }</pre>
     */
    @Nested
    @DisplayName("against a real device (opt-in)")
    @EnabledIfSystemProperty(named = "eyeandsickle.audio.device", matches = "true")
    class RealDevice {

        @Test
        @DisplayName("opens a device and reports itself running")
        void opens() throws InterruptedException {
            Audio audio = Audio.shared();
            audio.setMasterVolume(20);
            audio.setBusVolume(Bus.EFFECTS, 100);
            audio.play(Sfx.MESSAGE);
            // The engine opens the line on the mixer thread, so the state is not immediate.
            Thread.sleep(400);
            Audio.Status status = audio.status();
            assertThat(status.failed())
                    .as("a machine running this test has a device")
                    .isFalse();
            assertThat(status.running()).isTrue();
            assertThat(status.device()).isNotBlank();
        }

        @Test
        @DisplayName("plays several at once rather than cutting the first one off")
        void polyphony() throws InterruptedException {
            Audio audio = Audio.shared();
            audio.setMasterVolume(20);
            audio.warmUp();
            Thread.sleep(200);
            // ⚠ Different effects, because the same one twice would be stopped by its own retrigger
            // guard — which is correct behaviour and would make this test prove nothing.
            audio.play(Sfx.CONFIRM);
            audio.play(Sfx.TICK);
            audio.play(Sfx.DONE);
            Thread.sleep(50);
            assertThat(audio.status().voices())
                    .as("a Clip-per-sound engine could not do this")
                    .isGreaterThan(1);
        }
    }
}
