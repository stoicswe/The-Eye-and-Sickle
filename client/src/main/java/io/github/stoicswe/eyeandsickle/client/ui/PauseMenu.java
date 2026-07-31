package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.Greeble;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;

/**
 * The pause menu, drawn on top of the deck.
 *
 * <h2>The one modal the design language gets</h2>
 *
 * {@code docs/design/ui-design-language.md} §3 says "Nothing is hidden. No hamburgers, no modals, no
 * collapsed drawers." That rule is about <b>game information</b> — a number the player needs in
 * order to decide something must not be behind a click. This is not that: it is the session-level
 * menu, it contains no game state, and stopping the player is the entire function. The rule survives
 * intact because nothing here is a readout.
 *
 * <p>Everything else about it obeys the language: the same notched panel geometry (§2.3), a header
 * strip with a dim identifier (§3), uppercase Martian labels (§2.2), no radius, no shadow, and the
 * scrim is flat rather than blurred (§9 bans blur outright).
 *
 * <h2>Escape opens it. Escape does not leave the game.</h2>
 *
 * Escape used to go straight back to the main menu, which closed and persisted the session in one
 * keystroke. That is a destructive action bound to the key players press to mean "wait, stop" — the
 * one key whose whole cultural meaning is <em>get me out of the thing I am in</em>, not
 * <em>get me out of the game</em>. Now it opens this, and Escape again closes it.
 *
 * <h2>Quit asks, and it asks in place</h2>
 *
 * {@link javafx.scene.control.Alert} would open a second, OS-decorated {@code Stage} — real macOS
 * traffic lights on top of a game whose §0 premise is that the player never sees their own operating
 * system. The confirmation is therefore drawn in this panel, swapping the button row for a question.
 * Only leaving the game entirely asks; returning to the menu does not, because the menu is where a
 * session goes to be saved and is trivially re-entered.
 */
public final class PauseMenu extends StackPane {

    /** What the pause menu can do. Settings is deliberately not one of them — see {@link #create}. */
    public interface Actions {
        void save();

        void openSettings();

        void quitToMenu();

        void quitGame();
    }

    private final VBox buttons = new VBox(UiTokens.SPACE_1);
    private final Label status = Ui.micro("");
    private final Actions actions;
    private Greeble greeble;

    private PauseMenu(Actions actions) {
        this.actions = actions;
        getStyleClass().add("es-scrim");
        setAlignment(Pos.CENTER);
        setVisible(false);
        setManaged(false);
        // The scrim eats clicks, so a mis-click at the edge cannot reach a tool window behind it and
        // arm something while the player believes the game is paused.
        setOnMouseClicked(e -> e.consume());
        getChildren().add(panel());
    }

    public static PauseMenu create(Actions actions) {
        return new PauseMenu(actions);
    }

    private Region panel() {
        Label title = Ui.label("Paused");
        title.getStyleClass().add("es-strip-label");
        Label identifier = new Label(Ui.upper("sys/halt · esc"));
        identifier.getStyleClass().add("es-strip-id");
        HBox strip = Ui.row(UiTokens.SPACE_5, title, Ui.spacer(), identifier);
        strip.getStyleClass().add("es-strip");
        strip.setMinHeight(UiTokens.STRIP_HEIGHT);

        greeble = new Greeble(34);
        status.getStyleClass().add("es-buffer-text");
        buttons.setFillWidth(true);

        VBox body = new VBox(UiTokens.SPACE_5, buttons, status, greeble);
        body.getStyleClass().add("es-body-pad");

        VBox inner = new VBox(strip, body);
        inner.getStyleClass().add("es-panel");
        inner.setMinWidth(360);
        inner.setMaxSize(360, Region.USE_PREF_SIZE);

        StackPane framed = new StackPane(inner);
        framed.getStyleClass().add("es-panel-edge");
        framed.setPadding(new javafx.geometry.Insets(UiTokens.HAIR));
        framed.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Same 18px notch as every other panel. Recomputed on resize for the same reason
        // WindowFrame does it: -fx-shape would scale the cut instead of holding it (§7.2).
        framed.layoutBoundsProperty().addListener((obs, was, now) -> {
            framed.setClip(new Polygon(io.github.stoicswe.eyeandsickle.client.ui.chrome.WindowFrame.notchPoints(
                    now.getWidth(), now.getHeight())));
            inner.setClip(new Polygon(io.github.stoicswe.eyeandsickle.client.ui.chrome.WindowFrame.notchPoints(
                    now.getWidth() - 2 * UiTokens.HAIR, now.getHeight() - 2 * UiTokens.HAIR)));
        });
        return framed;
    }

    /** Shows the menu and rebuilds its default button row. */
    public void open() {
        showDefaultActions();
        status.setText("");
        setVisible(true);
        setManaged(true);
        toFront();
        requestFocus();
    }

    public void close() {
        setVisible(false);
        setManaged(false);
    }

    public void toggle() {
        if (isVisible()) {
            close();
        } else {
            open();
        }
    }

    private void showDefaultActions() {
        buttons.getChildren()
                .setAll(
                        item("Save now", () -> {
                            actions.save();
                            // Confirmed in words, in place. A save that reports nothing is indistinguishable
                            // from a save that did not happen, and this game autosaves on a timer — so the
                            // player has no other way to tell whether pressing this did anything.
                            status.setText(Ui.upper("saved"));
                        }),
                        item("Settings", () -> {
                            // Opens the Settings TOOL on the desk rather than inside this overlay. Settings
                            // is a window in the catalogue with an id, an accelerator and a switcher entry;
                            // duplicating it here would be a second copy of controls that write the same
                            // profile, and the two would drift.
                            close();
                            actions.openSettings();
                        }),
                        item("Quit to menu", actions::quitToMenu),
                        item("Quit game", this::confirmQuit),
                        item("Resume", this::close));
    }

    private void confirmQuit() {
        Label question = Ui.body("Quit The Eye and Sickle? The session is saved first.");
        question.setWrapText(true);
        question.getStyleClass().add("es-note-text");

        VBox confirm = new VBox(
                UiTokens.SPACE_2,
                question,
                item("Yes, quit", actions::quitGame),
                item("No, go back", this::showDefaultActions));
        buttons.getChildren().setAll(confirm);
    }

    /**
     * One menu row.
     *
     * <p>A {@link Label} in a styled row rather than a {@link javafx.scene.control.Button}: the
     * button style in {@code theme.css} is sized for a panel's action bar, and five of them stacked
     * reads as a form. It keeps everything a real button has that matters — focus traversal, an
     * accessible name, and Space/Enter activation ({@code docs/client/07} §3).
     */
    private Region item(String text, Runnable action) {
        Label label = Ui.label(text);
        label.getStyleClass().add("es-menu-item-label");
        HBox row = Ui.row(UiTokens.SPACE_3, label);
        row.getStyleClass().addAll("es-menu-item", "es-focusable");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setFocusTraversable(true);
        row.setAccessibleText(text);
        row.setOnMouseClicked(e -> {
            e.consume();
            action.run();
        });
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(row);
        row.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE || e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                action.run();
            }
        });
        return row;
    }

    /** Stops the greeble strip. Called when the shell is torn down. */
    public void dispose() {
        if (greeble != null) {
            greeble.dispose();
            greeble = null;
        }
    }
}
