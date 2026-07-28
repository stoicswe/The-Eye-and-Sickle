package io.github.stoicswe.eyeandsickle.client.ui.chrome;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * A notched panel with its own drawn chrome.
 *
 * <h2>The notch is a clip, and it must not be an {@code -fx-shape}</h2>
 *
 * {@code docs/design/ui-design-language.md} §7.2 is unusually specific here, and it is right:
 * {@code -fx-shape} accepts an SVG path but <b>scales the shape to the region</b>. A fixed 18px
 * 45° cut would become a proportional wedge that grows with the window — correct at the size it was
 * authored and wrong at every other, which is §10 criterion 6's failure exactly. So the notch is a
 * {@link Polygon} recomputed on every resize and applied with {@link Node#setClip}.
 *
 * <p>Two clipped layers rather than one, because §2.3 says panels are <b>drawn, not filled</b>: an
 * outer {@code rule-hi} layer and an inner {@code panel} layer inset by one pixel. The result is a
 * 1px hairline that follows the diagonal too, which a border property cannot do.
 *
 * <h2>Every region has a header strip</h2>
 *
 * §3: "{@code LABEL} left, {@code [−] [□] [×]} glyph controls, then a dim right-aligned identifier
 * ({@code PROC/ALLOC · 0x2F}). Unlabeled regions are a bug." The identifier is not decoration — it
 * is what makes a panel look like a subsystem rather than a card, and it is the cheapest diegetic
 * detail in the client.
 *
 * <p>The controls are ASCII in brackets, per §9's ban on icon fonts and Material/Lucide sets. They
 * are also {@link Label}s rather than {@link javafx.scene.control.Button}s: a real button brings
 * Modena's focus traversal and padding model with it, and these have to sit on a 24px strip.
 */
public final class WindowFrame extends Pane {

    private final Region edge = new Region();
    private final BorderPane inner = new BorderPane();
    private final HBox strip = new HBox(UiTokens.SPACE_5);
    private final Label titleLabel;
    private final Label identifierLabel;
    private final HBox controls = new HBox(UiTokens.SPACE_3);
    private final BooleanProperty focused = new SimpleBooleanProperty(false);

    private Runnable onMinimize;
    private Runnable onMaximize;
    private Runnable onClose;

    /**
     * @param title the panel's name, uppercased for display
     * @param identifier the dim right-hand designator, e.g. {@code PROC/ALLOC · 0x2F}
     */
    public WindowFrame(String title, String identifier) {
        getStyleClass().add("es-window");
        edge.getStyleClass().add("es-panel-edge");
        inner.getStyleClass().add("es-panel");

        titleLabel = Ui.label(title);
        titleLabel.getStyleClass().add("es-strip-label");

        identifierLabel = new Label(identifier == null ? "" : identifier.toUpperCase(Locale.ROOT));
        identifierLabel.getStyleClass().add("es-strip-id");

        strip.getStyleClass().add("es-strip");
        strip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        strip.getChildren().addAll(titleLabel, controls, Ui.spacer(), identifierLabel);
        strip.setMinHeight(UiTokens.STRIP_HEIGHT);

        inner.setTop(strip);
        getChildren().addAll(edge, inner);

        focused.addListener((obs, was, now) -> {
            getStyleClass().removeAll("es-window-focused");
            if (now) {
                getStyleClass().add("es-window-focused");
            }
        });
    }

    /** The strip is the drag handle. {@link DeskManager} needs it by identity, not by lookup. */
    public HBox headerStrip() {
        return strip;
    }

    public BooleanProperty focusedFlag() {
        return focused;
    }

    public void setTitle(String title) {
        titleLabel.setText(Ui.upper(title));
    }

    public void setIdentifier(String identifier) {
        identifierLabel.setText(Ui.upper(identifier));
    }

    public void setContent(Node content) {
        inner.setCenter(content);
        if (content instanceof Region region) {
            region.setMinSize(0, 0);
        }
    }

    public Node getContent() {
        return inner.getCenter();
    }

    /**
     * Installs the {@code [−] [□] [×]} controls.
     *
     * <p>A null handler omits that glyph rather than showing a dead one. The rig monitor is not
     * closable — {@code docs/design/01-core-resources.md} §1.4 makes the compute readout mandatory
     * and always visible, client pillar <b>C2</b> — and the way that rule is kept here is that no
     * {@code ×} is drawn at all. A disabled-looking control the player keeps trying is worse than
     * an absent one.
     */
    public void setControls(Runnable minimize, Runnable maximize, Runnable close) {
        this.onMinimize = minimize;
        this.onMaximize = maximize;
        this.onClose = close;
        rebuildControls();
    }

    /**
     * Which order desk windows put their controls in.
     *
     * <p>⚠ Static for the same reason {@link #rounded} is: it is one appearance flag for every
     * window in the game, and threading it through each frame's constructor would make a global fact
     * look like a per-window one. ⚠ It is <b>order only</b> — the side is the host OS's business and
     * this never touches it.
     */
    private static ControlOrder order = ControlOrder.SYSTEM;

    private static boolean onMac;

    public static void setControlOrder(ControlOrder value, boolean mac) {
        order = value == null ? ControlOrder.SYSTEM : value;
        onMac = mac;
    }

    /**
     * Rebuilds the control row in the current order.
     *
     * <p>Called on every order change as well as at construction, so a toggle reaches windows that
     * are already open. A frame that kept the order it was born with would make the setting look
     * like it only applied to windows opened afterwards — the same failure the rounded-corners
     * toggle had before its clip was re-laid.
     */
    void rebuildControls() {
        controls.getChildren().clear();
        Label minimizeControl = onMinimize == null ? null : control("[−]", onMinimize, false);
        Label maximizeControl = onMaximize == null
                ? null : control("[+]", onMaximize, false, "es-strip-ctl-max");
        Label closeControl = onClose == null ? null : control("[×]", onClose, true);

        // ⚠ Reordered, not mirrored. macOS runs close, minimise, zoom; Windows runs minimise,
        // maximise, close. Reversing the row would put minimise where the other convention puts
        // maximise, so a player who chose "macOS" would get neither convention.
        for (Label control : order.closeFirst(onMac)
                ? new Label[] {closeControl, minimizeControl, maximizeControl}
                : new Label[] {minimizeControl, maximizeControl, closeControl}) {
            if (control != null) {
                controls.getChildren().add(control);
            }
        }
    }

    private Label control(String glyph, Runnable action, boolean destructive) {
        return control(glyph, action, destructive, null);
    }

    private Label control(String glyph, Runnable action, boolean destructive, String extra) {
        Label label = new Label(glyph);
        label.getStyleClass().add("es-strip-ctl");
        if (destructive) {
            label.getStyleClass().add("es-strip-ctl-close");
        }
        if (extra != null) {
            label.getStyleClass().add(extra);
        }
        label.setOnMouseClicked(e -> {
            // Consumed so the click does not also reach the strip's drag handler and leave the
            // window one pixel from where it was as a parting gift.
            e.consume();
            action.run();
        });
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(label);
        // Reachable without a mouse: the strip controls are in the focus order and respond to
        // Space/Enter, because docs/client/07 §3 requires every action to have a keyboard route.
        label.setFocusTraversable(true);
        label.getStyleClass().add("es-focusable");
        label.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE
                    || e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                action.run();
            }
        });
        return label;
    }

    public Runnable minimizeAction() {
        return onMinimize;
    }

    public Runnable maximizeAction() {
        return onMaximize;
    }

    public Runnable closeAction() {
        return onClose;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();

        edge.resizeRelocate(0, 0, w, h);
        inner.resizeRelocate(UiTokens.HAIR, UiTokens.HAIR,
                Math.max(0, w - 2 * UiTokens.HAIR), Math.max(0, h - 2 * UiTokens.HAIR));

        edge.setClip(clip(w, h));
        inner.setClip(clip(Math.max(0, w - 2 * UiTokens.HAIR), Math.max(0, h - 2 * UiTokens.HAIR)));
    }

    /**
     * Whether windows are drawn with rounded corners (§9.3, opt-in, off by default).
     *
     * <h2>⚠ Static, and that is deliberate rather than lazy</h2>
     *
     * It is one appearance flag for every window in the game — the setting says so in as many words
     * — and threading it through {@code DeskManager} into each frame's constructor would make a
     * global fact look like a per-window one, which is an invitation for two windows to disagree.
     * {@link #setRounded} re-lays every live frame so a toggle takes effect immediately.
     */
    private static boolean rounded;

    public static boolean isRounded() {
        return rounded;
    }

    public static void setRounded(boolean on) {
        rounded = on;
    }

    /**
     * The clip: the notch, and the corner radius when it is switched on.
     *
     * <h2>⚠ THE CLIP IS WHY CSS COULD NOT DO THIS</h2>
     *
     * Both painted parts of a frame are already {@link Node#setClip}ped to a {@link Polygon} for the
     * 18px notch, and <b>a polygon clip cuts square corners no matter what {@code
     * -fx-background-radius} says</b>. The first attempt at this feature set the CSS property, the
     * toggle appeared to do nothing, and nothing anywhere reported a problem — the radius was being
     * applied and then clipped off. Shape is the thing to change here; the stylesheet is not.
     *
     * <p>{@link Shape#intersect} rather than a hand-built path, because the notch geometry is
     * already correct and tested ({@link #notchPoints}) and re-deriving it with arcs would be a
     * second implementation of §10 criterion 6.
     */
    private static Shape clip(double w, double h) {
        Polygon notch = new Polygon(notchPoints(w, h));
        if (!rounded || w <= 0 || h <= 0) {
            return notch;
        }
        Rectangle round = new Rectangle(w, h);
        // ⚠ UiTokens, not a literal. Sizes live there and nowhere else (§7.2) — and this one is
        // shared with the outer window, which must curve by the same amount or the deck looks like
        // two programs stacked on each other.
        double radius = Math.min(UiTokens.WINDOW_RADIUS, Math.min(w, h) / 2);
        round.setArcWidth(radius * 2);
        round.setArcHeight(radius * 2);
        return Shape.intersect(round, notch);
    }

    /**
     * The 18px 45° cut, top-right (§2.3), as flat x/y pairs.
     *
     * <p>Separated from the {@link Polygon} so it can be tested without a live toolkit — and because
     * §10 criterion 6 ("notched corners render correctly at three window widths without distortion")
     * is a claim about <em>these numbers</em>. The cut is a constant, not a fraction: that is the
     * whole reason {@code -fx-shape} is unusable here (§7.2), since it would scale the shape to the
     * region and turn a fixed 18px notch into a wedge that grows with the window.
     *
     * <p>Degrades to a plain rectangle when the panel is smaller than the cut itself. A notch larger
     * than the thing it notches produces a self-intersecting polygon, which JavaFX renders as a
     * triangle pointing the wrong way — visually startling and easy to reach by dragging a window
     * small.
     */
    public static double[] notchPoints(double w, double h) {
        double cut = Math.min(UiTokens.NOTCH, Math.min(w, h));
        if (cut <= 0) {
            return new double[] {0, 0, w, 0, w, h, 0, h};
        }
        return new double[] {
            0, 0,
            w - cut, 0,
            w, cut,
            w, h,
            0, h
        };
    }

    @Override
    protected double computePrefHeight(double width) {
        Node content = inner.getCenter();
        double contentHeight = content instanceof Region region ? region.prefHeight(width) : 0;
        return strip.prefHeight(width) + contentHeight + 2 * UiTokens.HAIR;
    }

    @Override
    protected double computePrefWidth(double height) {
        Node content = inner.getCenter();
        double contentWidth = content instanceof Region region ? region.prefWidth(height) : 0;
        return Math.max(strip.prefWidth(height), contentWidth) + 2 * UiTokens.HAIR;
    }

    /** A frame that fills whatever cell it is placed in — the tiling case (§3). */
    public WindowFrame filling() {
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);
        javafx.scene.layout.VBox.setVgrow(this, Priority.ALWAYS);
        return this;
    }
}
