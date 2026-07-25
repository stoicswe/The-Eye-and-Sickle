package io.github.stoicswe.eyeandsickle.client.window;

import java.util.Objects;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * One tool, one OS window.
 *
 * <p>The client's whole presentation model is that each tool the operator uses — network map,
 * terminal, rig monitor, recon — is an independent top-level {@link Stage} they can move, resize and
 * arrange across monitors. This type is the seam that keeps that uniform: a tool declares what it is
 * called and how to build its content, and window plumbing stays in one place.
 *
 * <p>Constructing a {@code ToolWindow} deliberately does not touch the JavaFX toolkit. Only {@link
 * #show(Stage)} and {@link #openNew()} do. That keeps a tool's identity and default geometry testable
 * without booting a graphics stack.
 */
public final class ToolWindow {

    private final String id;
    private final String title;
    private final double defaultWidth;
    private final double defaultHeight;
    private final boolean alwaysOnTop;
    private final Supplier<Parent> content;

    private ToolWindow(
            String id,
            String title,
            double defaultWidth,
            double defaultHeight,
            boolean alwaysOnTop,
            Supplier<Parent> content) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.alwaysOnTop = alwaysOnTop;
        this.content = Objects.requireNonNull(content, "content");
    }

    /**
     * The rig monitor: total cycles, allocated by consumer, available, and recovering with
     * time-to-recover.
     *
     * <p>{@code docs/design/01-core-resources.md} §1.4 calls the compute ledger the game's most
     * important HUD element and specifically justifies a dedicated, always-on-top window for it.
     * Compute is the master scarcity — every meaningful decision in the game is a question of where to
     * spend cycles — so the player must be able to read this at a glance, always.
     *
     * @return the rig monitor window definition
     */
    public static ToolWindow rigMonitor() {
        return new ToolWindow("rig-monitor", "The Eye and Sickle — Rig Monitor", 420, 560, true, () -> {
            // Placeholder. Deliberately shows no numbers: compute state is server-owned, and
            // inventing plausible-looking values here is how a client starts being believed.
            VBox root = new VBox(12);
            root.setPadding(new Insets(20));
            root.getChildren().addAll(heading("RIG MONITOR"), body("Not connected to a home server."), body("""
                                    This window will show the compute ledger: total cycles, \
                                    allocation by consumer, what is available, and what is \
                                    recovering with time-to-recover.

                                    All four values are owned by the server. The client renders \
                                    them; it never computes them."""));
            return root;
        });
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-3");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    /**
     * Shows this tool in a caller-supplied stage — used for the primary stage handed to the
     * application at startup.
     *
     * @param stage the stage to populate and show
     * @return the same stage, now showing
     */
    public Stage show(Stage stage) {
        stage.setTitle(title);
        stage.setScene(new Scene(content.get(), defaultWidth, defaultHeight));
        stage.setAlwaysOnTop(alwaysOnTop);
        stage.show();
        return stage;
    }

    /**
     * Opens this tool in a brand-new OS window.
     *
     * <p>Must be called on the JavaFX application thread.
     *
     * @return the new stage, now showing
     */
    public Stage openNew() {
        return show(new Stage());
    }

    /** Stable identifier, for persisting window layout between sessions. */
    public String id() {
        return id;
    }

    /** Window title. */
    public String title() {
        return title;
    }

    /** Whether this tool floats above the others; true only for the rig monitor. */
    public boolean alwaysOnTop() {
        return alwaysOnTop;
    }
}
