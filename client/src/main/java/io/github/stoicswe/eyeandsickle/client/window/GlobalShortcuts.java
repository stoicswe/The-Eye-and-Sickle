package io.github.stoicswe.eyeandsickle.client.window;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * The application-wide shortcuts from {@code docs/client/00-client-overview.md} §6.3.
 *
 * <h2>Separate from the window accelerators, because they answer a different question</h2>
 *
 * {@link WindowSpec}'s accelerators mean "show me tool <i>n</i>". These mean "do a thing to the
 * client" — open the palette, change the layout, cycle the teaching level. Keeping them apart means
 * the window table stays a transcription of §2.1's catalogue rather than a grab-bag.
 *
 * <h2>Installed on every Scene, for the same reason the window accelerators are</h2>
 *
 * §3.6's accelerator-installation trap applies identically here: a shortcut registered on one Scene
 * fires only while that Scene has focus. {@code Shortcut+K} that works only when the terminal is
 * already in front of you is not a command palette, it is a decoration.
 *
 * <h2>Two of these are deliberately not remappable</h2>
 *
 * {@code Shortcut+0} (rig monitor) and {@code Shortcut+.} (abort) — the first because pillar C2 says
 * the most important number in the game must always be one keystroke away and a player must not be
 * able to lose that by accident, and the second because an abort a player cannot reach is worse than
 * no abort at all.
 */
public final class GlobalShortcuts {

    private GlobalShortcuts() {}

    /** What the client can be asked to do from anywhere. */
    public interface Handlers {

        /** {@code Shortcut+K} — the command palette. */
        void openPalette();

        /** {@code Shortcut+Shift+T} — cycle theme. */
        void cycleTheme();

        /** {@code Shortcut+Shift+E} — cycle teaching level: explain → terms → off. */
        void cycleTeaching();

        /** {@code Shortcut+Shift+D} — multi-window ↔ docked. */
        void toggleLayout();

        /** {@code Shortcut+`} — cycle open tool windows. */
        void cycleWindows();

        /**
         * {@code Shortcut+.} — abort the current breach.
         *
         * <p>Always confirms. {@code aborted} is a persisted outcome with real consequences
         * ({@code docs/design/05} §4), so a mis-key must not be able to spend one.
         */
        void abort();
    }

    public static void install(Scene scene, Handlers handlers) {
        bind(scene, KeyCode.K, false, handlers::openPalette);
        bind(scene, KeyCode.T, true, handlers::cycleTheme);
        bind(scene, KeyCode.E, true, handlers::cycleTeaching);
        bind(scene, KeyCode.D, true, handlers::toggleLayout);
        bind(scene, KeyCode.BACK_QUOTE, false, handlers::cycleWindows);
        bind(scene, KeyCode.PERIOD, false, handlers::abort);
    }

    private static void bind(Scene scene, KeyCode code, boolean shift, Runnable action) {
        KeyCombination combination = shift
                ? new KeyCodeCombination(code, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN)
                : new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
        scene.getAccelerators().put(combination, action);
    }

    /** The bindings, for a help sheet and for a test that checks none collide with a window's. */
    public static java.util.List<KeyCombination> bindings() {
        return java.util.List.of(
                new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                new KeyCodeCombination(KeyCode.T, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN),
                new KeyCodeCombination(KeyCode.E, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN),
                new KeyCodeCombination(KeyCode.D, KeyCombination.SHIFT_DOWN, KeyCombination.SHORTCUT_DOWN),
                new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.SHORTCUT_DOWN),
                new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.SHORTCUT_DOWN));
    }
}
