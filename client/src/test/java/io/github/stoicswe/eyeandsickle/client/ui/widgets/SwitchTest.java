package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The switch that replaced every checkbox.
 *
 * <h2>What is actually worth guarding</h2>
 *
 * A restyle is only safe if the control it replaces behaves identically. These check the three things
 * a checkbox did that a hand-built layout can silently lose: it toggles from the keyboard, its
 * <b>label</b> is part of the hit target, and it announces its state rather than its shape.
 *
 * <p>⚠ There is also a real regression this class exists downstream of. The shell's command menu read
 * {@code control instanceof CheckBox box && box.isSelected()} — a pattern match that compiles
 * unchanged when the widget type moves and simply stops matching, so a flag the player had switched
 * on would never reach the command line. Nothing fails; the wrong command runs. That call site is now
 * typed to {@code Switch}, and this is the class it depends on behaving like a toggle.
 */
class SwitchTest {

    /** Starts the toolkit, or skips — the repo's convention; see {@code NodeMenuTest}. */
    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        } catch (UnsupportedOperationException | NoClassDefFoundError | ExceptionInInitializerError headless) {
            Assumptions.abort("no display — the JavaFX toolkit cannot start here: " + headless.getMessage());
        }
        if (!up.await(20, TimeUnit.SECONDS)) {
            Assumptions.abort("the JavaFX toolkit did not start within 20s");
        }
    }

    private static <T> T onFxThread(java.util.function.Supplier<T> body) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(body.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("the FX thread threw", failure.get());
        }
        return result.get();
    }

    private static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                4,
                4,
                4,
                4,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }

    @Test
    @DisplayName("clicking anywhere on the row toggles it, label included")
    void clickToggles() throws Exception {
        boolean[] states = onFxThread(() -> {
            Switch control = new Switch("Reduce motion");
            boolean before = control.isSelected();
            control.fireEvent(click());
            boolean after = control.isSelected();
            control.fireEvent(click());
            return new boolean[] {before, after, control.isSelected()};
        });
        assertThat(states[0]).isFalse();
        assertThat(states[1]).isTrue();
        assertThat(states[2]).as("it toggles back").isFalse();
    }

    /**
     * ⚠ A checkbox is keyboard-operable for free; a hand-built layout is not.
     *
     * <p>Losing this would make every setting in the client unreachable without a mouse, which
     * {@code docs/client/07-accessibility.md} does not permit and no render would reveal.
     */
    @Test
    @DisplayName("space and enter toggle it, and it is focus-traversable")
    void keyboardToggles() throws Exception {
        boolean[] states = onFxThread(() -> {
            Switch control = new Switch("Full screen");
            boolean traversable = control.isFocusTraversable();
            control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
            boolean afterSpace = control.isSelected();
            control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return new boolean[] {traversable, afterSpace, control.isSelected()};
        });
        assertThat(states[0]).as("reachable by tab").isTrue();
        assertThat(states[1]).isTrue();
        assertThat(states[2]).as("enter toggles too").isFalse();
    }

    /**
     * ⚠ §4.4 — never colour alone.
     *
     * <p>On and off differ by the knob's position first and its fill second, so the state has to be
     * announced rather than shown. A screen reader that got only "switch" would be reading the shape.
     */
    @Test
    @DisplayName("it announces its state, not its shape")
    void announcesState() throws Exception {
        String[] spoken = onFxThread(() -> {
            Switch control = new Switch("Signal glitch");
            String off = control.getAccessibleText();
            control.setSelected(true);
            return new String[] {off, control.getAccessibleText(), String.valueOf(control.getAccessibleRole())};
        });
        assertThat(spoken[0]).isEqualTo("Signal glitch, off");
        assertThat(spoken[1]).isEqualTo("Signal glitch, on");
        assertThat(spoken[2]).isEqualTo(AccessibleRole.TOGGLE_BUTTON.name());
    }

    @Test
    @DisplayName("setSelected drives the property without a click, for restoring a saved setting")
    void setSelectedIsQuiet() throws Exception {
        boolean seeded = onFxThread(() -> {
            Switch control = new Switch("Rounded window corners");
            control.setSelected(true);
            return control.selectedProperty().get();
        });
        assertThat(seeded).isTrue();
    }
}
