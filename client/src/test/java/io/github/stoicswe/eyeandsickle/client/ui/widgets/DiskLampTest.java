package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.DiskActivity;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.DiskLamp.Flicker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The drive lamp's two-second stutter.
 *
 * <p>⚠ This exists because the thing it checks is <b>invisible to inspection</b>. The lamp flickers
 * for two seconds after a write and then settles, which no screenshot will catch and no amount of
 * staring at the deck will confirm tick by tick. Everything below is behaviour a real drive LED has
 * and that a three-line version of this widget would get subtly wrong.
 *
 * <p>Nothing here starts a toolkit: {@link Flicker} is a value and the pattern is a constant, which
 * is the entire reason the state machine was pulled out of the widget.
 */
class DiskLampTest {

    /** Ticks a write keeps the lamp working. Mirrors DiskLamp's constant; see {@link #burstLength}. */
    private static final int DWELL = 20;

    /** Runs {@code ticks} quiet ticks from {@code start}, collecting what the lamp shows. */
    private static List<Boolean> quiet(Flicker start, int ticks) {
        List<Boolean> frames = new ArrayList<>();
        Flicker state = start;
        for (int i = 0; i < ticks; i++) {
            // Same count in, same count out: a tick on which nothing was written.
            state = state.next(7, 7);
            frames.add(state.lit());
        }
        return frames;
    }

    private static long lit(List<Boolean> frames) {
        return frames.stream().filter(Boolean::booleanValue).count();
    }

    @Nested
    @DisplayName("a write")
    class OnWrite {

        /**
         * ⚠ The guarantee the pattern's leading {@code 1} exists for. A lamp that could start on a
         * dark tick would appear to ignore something the player had just done, up to 300ms at a
         * time — which is exactly long enough to read as "it did not notice".
         */
        @Test
        @DisplayName("lights the lamp on the very tick it lands")
        void litImmediately() {
            assertThat(Flicker.DARK.next(4, 5).lit()).isTrue();
        }

        @Test
        @DisplayName("mid-burst, restarts the stutter rather than extending it")
        void restarts() {
            Flicker deepInATail = Flicker.DARK.next(4, 5);
            for (int i = 0; i < 15; i++) {
                deepInATail = deepInATail.next(5, 5);
            }
            assertThat(deepInATail.phase()).as("well into the sparse tail").isGreaterThan(12);

            Flicker afterWrite = deepInATail.next(5, 6);
            assertThat(afterWrite.phase()).as("phase restarts, not just the countdown").isZero();
            assertThat(afterWrite.remaining()).isEqualTo(DWELL);
            assertThat(afterWrite.lit()).isTrue();
        }

        /**
         * The autosave and a settings write can land in the same 100ms tick. A lamp that queued them
         * would run one burst after another and read as a light that is simply on during a busy
         * moment, which is the opposite of what an activity indicator is for.
         */
        @Test
        @DisplayName("several writes in one tick are one burst, not a queue")
        void doesNotAccumulate() {
            assertThat(Flicker.DARK.next(4, 9).remaining()).isEqualTo(DWELL);
        }
    }

    @Nested
    @DisplayName("the burst")
    class Burst {

        @Test
        @DisplayName("runs for two seconds and then settles dark")
        void burstLength() {
            List<Boolean> frames = quiet(Flicker.DARK.next(0, 1), DWELL + 10);
            // The write's own tick is lit; the pattern then plays out over the remaining ticks.
            assertThat(frames.subList(DWELL, frames.size()))
                    .as("dark once the drive has settled")
                    .containsOnly(false);
            assertThat(lit(frames.subList(0, DWELL - 1)))
                    .as("still working through the burst")
                    .isGreaterThan(0);
        }

        /**
         * ⚠ The shape of the pattern, not just its presence. A burst that settles reads as a drive
         * finishing; a burst at constant density reads as a blinking widget. This is the assertion
         * that would fail if someone "tidied" FLICKER into an even alternation.
         */
        @Test
        @DisplayName("is dense at the head and sparse at the tail")
        void settles() {
            List<Boolean> frames = quiet(Flicker.DARK.next(0, 1), DWELL);
            long head = lit(frames.subList(0, 8));
            long tail = lit(frames.subList(11, 19));
            assertThat(head).as("busy while the write is landing").isGreaterThanOrEqualTo(4);
            assertThat(tail).as("thinning out as it settles").isLessThan(head);
        }

        @Test
        @DisplayName("actually blinks — it is not solid for two seconds")
        void blinks() {
            List<Boolean> frames = quiet(Flicker.DARK.next(0, 1), DWELL);
            assertThat(frames).as("goes dark at some point mid-burst").contains(false);
            assertThat(frames).as("and comes back on again").contains(true);
        }
    }

    @Nested
    @DisplayName("at rest")
    class Idle {

        @Test
        @DisplayName("stays dark, and stays dark")
        void steady() {
            assertThat(quiet(Flicker.DARK, 30)).containsOnly(false);
        }

        /**
         * Nothing may drift negative and read as "not lit" while the arithmetic underneath is
         * quietly wrong — the lamp would look right for months and then misbehave on a long session.
         */
        @Test
        @DisplayName("never counts past zero, however long it idles after a burst")
        void floor() {
            Flicker state = Flicker.DARK.next(0, 1);
            for (int i = 0; i < DWELL + 100; i++) {
                state = state.next(7, 7);
            }
            assertThat(state.remaining()).isNotNegative();
            assertThat(state).isEqualTo(Flicker.DARK);
            assertThat(state.lit()).isFalse();
        }
    }

    @Nested
    @DisplayName("the counter behind it")
    class Counter {

        /**
         * ⚠ Compared, never consumed. If {@code writes()} reset on read, the lamp would eat the
         * signal and any second reader — a test, a future readout — would see nothing.
         */
        @Test
        @DisplayName("is monotonic and non-consuming")
        void monotonic() {
            long before = DiskActivity.writes();
            assertThat(DiskActivity.writes()).isEqualTo(before);
            DiskActivity.wrote();
            assertThat(DiskActivity.writes()).isEqualTo(before + 1);
            assertThat(DiskActivity.writes()).as("reading does not clear it").isEqualTo(before + 1);
        }
    }
}
