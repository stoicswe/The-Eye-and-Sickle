package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * A horizontal on/off switch — this deck's replacement for {@link javafx.scene.control.CheckBox}.
 *
 * <h2>Why not a checkbox</h2>
 *
 * A tick box is a form control: it means "include this in what I am about to submit". Every one of
 * these settings takes effect the moment it is changed — there is no submit — and a switch is the
 * control that says so. It also stops Modena's own checkbox skin reaching the screen, which is the
 * same argument {@code §0} makes about window chrome: nothing in this client should be recognisably
 * the host toolkit's.
 *
 * <h2>⚠ Square, and a pill only under {@code .es-rounded}</h2>
 *
 * The obvious switch is a lozenge, and §9's radius ban is unamended — a non-zero radius is permitted
 * only under the rounded-windows opt-in, and {@code UiContractTest} scans the stylesheet to enforce
 * it. So the default is two rectangles, and the softer shape is something the player opts into along
 * with everything else. That is the same treatment the block cards' miner pill got, for the same
 * reason.
 *
 * <h2>⚠ The knob SNAPS; it does not slide</h2>
 *
 * §5 permits no easing anywhere and rations continuous motion to the firmware handover by filename.
 * A sliding knob would be a tween, and a stepped one over 200ms would be a {@code Timeline} in a
 * widget — both fail the contract test. It also makes reduced motion free rather than a special
 * case: there is no motion to reduce, and the state is legible in a single frame either way.
 *
 * <h2>⚠ Never colour alone (§4.4)</h2>
 *
 * On and off differ by the knob's <b>position</b> first — left or right, which is what a switch is —
 * and by fill second. A player who cannot separate the two fills still reads the position, and the
 * accessible role is {@code TOGGLE_BUTTON} so a screen reader announces the state rather than
 * describing a shape.
 */
public final class Switch extends HBox {

    /** Track width. Wide enough that the knob's two positions are unambiguous at a glance. */
    private static final double TRACK_WIDTH = 30;

    private static final double TRACK_HEIGHT = 16;

    /** The knob is square and inset by a hair, so the track reads as a track on both sides. */
    private static final double KNOB = 12;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Region knob = new Region();
    private final StackPane track = new StackPane();
    private final Label label;

    /**
     * The text as it was given, before {@code Ui.label} uppercased it for display.
     *
     * <p>⚠ This is what a screen reader is told. The deck sets its labels in caps as a typographic
     * choice, and several readers spell an all-caps run out letter by letter — so announcing the
     * display text would turn "Signal glitch" into thirteen letters. The caps are a look; the words
     * are the content.
     */
    private String spokenText;

    public Switch(String text) {
        super(UiTokens.SPACE_3);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("es-switch");

        track.getStyleClass().add("es-switch-track");
        track.setMinSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        track.setMaxSize(TRACK_WIDTH, TRACK_HEIGHT);

        knob.getStyleClass().add("es-switch-knob");
        knob.setMinSize(KNOB, KNOB);
        knob.setPrefSize(KNOB, KNOB);
        knob.setMaxSize(KNOB, KNOB);
        track.getChildren().add(knob);

        spokenText = text == null ? "" : text;
        label = Ui.label(text);
        label.getStyleClass().add("es-switch-label");
        label.setWrapText(true);
        getChildren().addAll(track, label);

        // ⚠ The whole row is the target, not the 30px track. A setting whose only hit area is a
        // small rectangle is a setting people miss — and a checkbox's label was always clickable, so
        // taking that away would be a regression dressed as a restyle.
        setFocusTraversable(true);
        Cursors.shared().clickable(this);
        setOnMouseClicked(e -> {
            e.consume();
            requestFocus();
            selected.set(!selected.get());
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                e.consume();
                selected.set(!selected.get());
            }
        });

        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        selected.addListener((obs, was, now) -> apply());
        apply();
    }

    /** Repaints the knob's side and the track's state class. */
    private void apply() {
        boolean on = selected.get();
        // ⚠ Position FIRST — that is what makes it a switch, and what a player who cannot tell the
        // two fills apart is actually reading (§4.4).
        StackPane.setAlignment(knob, on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        track.getStyleClass().remove("es-switch-on");
        if (on) {
            track.getStyleClass().add("es-switch-on");
        }
        setAccessibleText(spokenText + ", " + (on ? "on" : "off"));
    }

    /**
     * The state, bindable — the same property name a {@code CheckBox} exposes.
     *
     * <p>⚠ Deliberately API-compatible with {@code CheckBox} for {@code selectedProperty},
     * {@code isSelected} and {@code setSelected}. Fifteen call sites changed only their type name,
     * which is what kept a visual change from turning into fifteen chances to invert a setting.
     */
    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    /**
     * Installs a tooltip, the way a {@code Control} would.
     *
     * <p>⚠ Present purely so call sites did not have to change. This is an {@link HBox} rather than a
     * {@code Control} — deliberately, to keep Modena's skin off the screen — and {@code setTooltip}
     * is a {@code Control} method, so without this every site that explained a setting would have had
     * to switch to {@code Tooltip.install}. A restyle that forces unrelated edits is a restyle that
     * introduces unrelated bugs.
     */
    public void setTooltip(javafx.scene.control.Tooltip tooltip) {
        javafx.scene.control.Tooltip.install(this, tooltip);
    }

    /** The text beside the switch. */
    public String getText() {
        return label.getText();
    }

    public void setText(String text) {
        spokenText = text == null ? "" : text;
        label.setText(Ui.upper(text));
        apply();
    }
}
