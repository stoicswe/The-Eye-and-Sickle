package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Group;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The ring wallpaper's glitch envelope.
 *
 * <h2>What is worth testing here and what is not</h2>
 *
 * The <em>look</em> is a render question and is checked with {@code DeckSnapshot -Ddeck.wallpaper=…}
 * — no assertion can tell a convincing tear from an unconvincing one. What a test can hold is the
 * part that is arithmetic and that would fail silently: that the glitch <b>returns to nothing</b>, and
 * that switching it off really does leave a clean ring rather than a frozen mid-tear.
 *
 * <p>⚠ No toolkit: {@code Region} does its own layout maths and the field only subscribes to
 * {@link io.github.stoicswe.eyeandsickle.client.ui.Pulse} when it is glitching.
 */
@DisplayName("the ring wallpaper")
class RingFieldTest {

    private RingField field;

    @BeforeEach
    void setUp() {
        field = new RingField();
        field.resize(1200, 800);
        field.layout();
    }

    /** How far the slice axis is turned: 0 for horizontal tearing, 90 for vertical. */
    private double rotation() {
        for (Node child : field.getChildrenUnmodifiable()) {
            if (child instanceof Group stack && !stack.getTransforms().isEmpty()) {
                return ((javafx.scene.transform.Rotate) stack.getTransforms().getFirst()).getAngle();
            }
        }
        return -1;
    }

    /** The largest sideways displacement any slice currently has. */
    private double maxSlip() {
        double most = 0;
        for (Node child : field.getChildrenUnmodifiable()) {
            if (child instanceof Group stack) {
                for (Node band : stack.getChildren()) {
                    most = Math.max(most, Math.abs(band.getTranslateX()));
                }
            }
        }
        return most;
    }

    @Nested
    @DisplayName("the envelope")
    class Envelope {

        @Test
        @DisplayName("it never fully rests, but the floor is a shimmer rather than a tear")
        void neverFullyRests() {
            field.setGlitching(true);
            field.seekForRender(0);
            double floor = maxSlip();
            field.seekForRender(0.5);
            double peak = maxSlip();

            // ⚠ Not zero any more. The fault is meant to be continuously alive, so the emblem is
            // never perfectly whole — but EXTREMITY is steep enough that the calm phase is a fraction
            // of the peak rather than a visibly wobbling wallpaper behind text.
            assertThat(floor).isGreaterThan(0);
            assertThat(floor).as("the calm phase is barely there").isLessThan(peak / 20);
        }

        @Test
        @DisplayName("it develops and then comes back")
        void developsAndRecedes() {
            field.setGlitching(true);

            field.seekForRender(0.5);
            double peak = maxSlip();
            assertThat(peak).as("something has torn at the peak").isGreaterThan(1);

            field.seekForRender(0.9);
            assertThat(maxSlip())
                    .as("a fault that never settles is a broken wallpaper, not an effect")
                    .isLessThan(peak);
        }

        @Test
        @DisplayName("the far end dominates — the calm stretches are genuinely calm")
        void theFarEndIsDisproportionate() {
            field.setGlitching(true);
            field.seekForRender(0.5);
            double peak = maxSlip();
            field.seekForRender(0.25);
            double halfway = maxSlip();

            // Displacement goes as a power of the envelope, not linearly with it. A linear ramp puts
            // halfway at half the peak, which is a wallpaper that spends most of its life visibly
            // wobbling behind text.
            assertThat(halfway).isLessThan(peak / 3);
        }

        @Test
        @DisplayName("the slice axis flips between cycles, and flips while it is quiet")
        void theAxisTurns() {
            field.setGlitching(true);

            field.seekForRender(0.5);
            double firstAxis = rotation();
            field.seekForRender(1.5);
            double secondAxis = rotation();

            // Horizontal tearing for a cycle, then vertical. The ring is a circle, so this is a
            // rotation of the whole stack rather than a second set of slices.
            assertThat(firstAxis).isNotEqualTo(secondAxis);
            assertThat(java.util.List.of(firstAxis, secondAxis)).containsExactlyInAnyOrder(0.0, 90.0);

            // ⚠ And it turns at the FLOOR. Flipping mid-tear would snap every displaced slice across
            // the screen at once, which reads as a rendering fault rather than as the fault turning.
            // Relative to the peak rather than an absolute pixel count, which would only be a
            // statement about this test's window size.
            field.seekForRender(0.5);
            double atPeak = maxSlip();
            field.seekForRender(1.0);
            assertThat(maxSlip())
                    .as("almost nothing is displaced at the moment the axis turns")
                    .isLessThan(atPeak / 20);
        }
    }

    @Nested
    @DisplayName("the colour shift")
    class Colour {

        /** The strongest fringe opacity currently set on any slice. */
        private double fringeStrength() {
            double most = 0;
            for (Node child : field.getChildrenUnmodifiable()) {
                if (child instanceof Group stack) {
                    for (Node band : stack.getChildren()) {
                        for (Node piece : ((Group) band).getChildren()) {
                            if (piece instanceof javafx.scene.shape.Circle circle && circle.isVisible()) {
                                most = Math.max(most, circle.getOpacity());
                            }
                        }
                    }
                }
            }
            return most;
        }

        @Test
        @DisplayName("colour intensifies and falls back on its own period")
        void colourBreathes() {
            field.setGlitching(true);
            field.setChromatic(true);

            // ⚠ Sampled across the COLOUR cycle, which is co-prime with the tear cycle — the whole
            // point is that the two do not repeat together. Two effects on one clock read as one.
            double least = Double.MAX_VALUE;
            double most = 0;
            for (double phase = 0; phase <= 2; phase += 0.05) {
                field.seekForRender(phase);
                double strength = fringeStrength();
                least = Math.min(least, strength);
                most = Math.max(most, strength);
            }
            assertThat(most).as("the colour reaches full somewhere").isGreaterThan(least);
            assertThat(least)
                    .as("and never goes out entirely while it is switched on")
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("no fringe at all until it is asked for")
        void offMeansNoColour() {
            field.setGlitching(true);
            field.seekForRender(0.5);
            // A fringe is an artefact OF the displacement, so with the option off there is nothing
            // coloured on screen however hard the ring is tearing.
            assertThat(fringeStrength()).isZero();
        }
    }

    @Nested
    @DisplayName("switching it off")
    class Off {

        @Test
        @DisplayName("it snaps back to a clean ring rather than freezing mid-tear")
        void offIsClean() {
            field.setGlitching(true);
            field.seekForRender(0.5);
            assertThat(maxSlip()).isGreaterThan(1);

            field.setGlitching(false);
            // The still mode is WCAG 2.2.2's pause for this wallpaper. A pause that froze the tear
            // would leave the player looking at a permanently broken emblem, which is the one state
            // the effect must never settle into.
            assertThat(maxSlip()).isZero();
        }

        @Test
        @DisplayName("a field that was never glitching has nothing displaced")
        void neverGlitchedIsClean() {
            assertThat(maxSlip()).isZero();
        }
    }
}
