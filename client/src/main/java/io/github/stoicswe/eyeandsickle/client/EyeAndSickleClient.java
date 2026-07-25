package io.github.stoicswe.eyeandsickle.client;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.Theme;
import io.github.stoicswe.eyeandsickle.client.window.ToolWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The operator's client.
 *
 * <h2>Multi-window is the fantasy, not an implementation detail</h2>
 *
 * Each tool gets its own top-level OS window — a JavaFX {@link Stage} — so a player's screen ends up
 * looking like an actual operator's desk: network map here, terminal there, rig monitor on the second
 * monitor. This is a native capability of the toolkit rather than something fought for, which is a
 * large part of why JavaFX was chosen over a game engine ({@code
 * docs/architecture/01-tech-stack.md} §1).
 *
 * <p>It matters most during a live breach, when the target graph, the active puzzle layer, the
 * compute/trace readout and the flavour logs a human-read step depends on are genuinely different
 * surfaces ({@code docs/design/05-hacking-minigame.md} §5).
 *
 * <h2>The accessibility obligation</h2>
 *
 * Window management under time pressure is a real barrier. A single-window docked layout <strong>must
 * exist</strong> — multi-window is the default and the fantasy, but it must not be the only option.
 * Both architecture and design docs flag this for review before the layout is committed to. Whoever
 * builds the second tool window should build the docked fallback in the same change, while it is
 * still cheap.
 *
 * <h2>What this class must never become</h2>
 *
 * A view and input layer. The client renders server-owned state and sends intent; it decides nothing
 * a cheating client would want to forge — item ownership, balances, compute allocation, duel outcomes
 * (Invariant I14). The one thing it computes for itself is provenance verification, and that is
 * precisely so it does <em>not</em> have to trust the server's UI.
 */
public class EyeAndSickleClient extends Application {

    /** Dark by default; this is a game about being watched. */
    private static final Theme DEFAULT_THEME = new PrimerDark();

    @Override
    public void start(Stage primaryStage) {
        // AtlantaFX styles the whole application, so every Stage opened later inherits it without
        // repeating the wiring.
        setUserAgentStylesheet(DEFAULT_THEME.getUserAgentStylesheet());

        ToolWindow.rigMonitor().show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
