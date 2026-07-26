package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The small pieces every panel is assembled from.
 *
 * <h2>Why uppercase lives here</h2>
 *
 * {@code docs/design/ui-design-language.md} §7.2: JavaFX has no {@code text-transform}. §6 requires
 * uppercase for every label in the client. Left to each view, that is ninety-odd chances to forget,
 * and a single sentence-case label is instantly visible as wrong. {@link #label} applies it once, so
 * the rule is enforced by the only constructor anyone calls rather than by review.
 *
 * <p>{@link Locale#ROOT} rather than the default locale, deliberately: Turkish locales map
 * {@code i} to {@code İ}, which would turn {@code IDENTITY} into a label that renders with a dot
 * above the I on a Turkish player's machine and nowhere else. The same trap sits behind
 * {@code String.format} — every formatted number in this client passes {@code Locale.ROOT} for the
 * same reason, so a German player's ledger does not read {@code 1,25 EC} against an
 * {@code EC/HR} projection that used a period.
 */
public final class Ui {

    private Ui() {}

    /** A Martian Mono label: uppercase, tracked by the face, {@code dim-2} unless restyled. */
    public static Label label(String text) {
        Label label = new Label(text == null ? "" : text.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("es-label");
        return label;
    }

    /** A body-text label, left in the case it was written in — §6's "sentence case for consequence". */
    public static Label body(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-body");
        return label;
    }

    public static Label small(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-small");
        return label;
    }

    public static Label micro(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-micro");
        return label;
    }

    /** A value: Plex, {@code text-hi}. Add {@code es-value-live} only when it is earning (§2.1). */
    public static Label value(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-value");
        return label;
    }

    /**
     * The one large numeral on a panel (§2.2), with its unit beside it at body size.
     *
     * <p>Amber by default because the display figure is nearly always the live one. When it is not —
     * a count of something dormant — remove {@code es-display} and use {@link #value}, rather than
     * leaving an amber number that is not earning anything.
     */
    public static HBox display(String number, String unit) {
        Label big = new Label(number);
        big.getStyleClass().add("es-display");
        Label small = new Label(unit == null ? "" : unit.toUpperCase(Locale.ROOT));
        small.getStyleClass().add("es-display-unit");
        HBox box = new HBox(UiTokens.SPACE_2, big, small);
        box.setAlignment(Pos.BASELINE_LEFT);
        return box;
    }

    /** A horizontal spacer that eats the slack. The layout's one flex point (§3). */
    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public static Region vspacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    /** A fixed-size block — the primitive behind every meter cell, tick and caret. */
    public static Region block(double width, double height, String... styleClasses) {
        Region region = new Region();
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        region.getStyleClass().addAll(styleClasses);
        return region;
    }

    /** A 1px hairline, the only divider the language has (§2.3). */
    public static Region hairline(double length, boolean vertical) {
        return vertical
                ? block(UiTokens.HAIR, length, "es-panel-edge")
                : block(length, UiTokens.HAIR, "es-panel-edge");
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    /** Uppercase, {@link Locale#ROOT}. See the class comment for why the locale is spelled out. */
    public static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }
}
