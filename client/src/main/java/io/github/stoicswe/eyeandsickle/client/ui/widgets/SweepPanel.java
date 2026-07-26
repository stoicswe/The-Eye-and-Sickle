package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * An inset well with a bar sweeping across it — the panel that is actually working.
 *
 * <h2>Only where something is genuinely in progress</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 restricts it, and the restriction is the point. A
 * sweep that runs permanently is a spinner: it stops carrying information within about ten seconds
 * and then just moves. {@link #setWorking(boolean)} is therefore the normal way to use this — the
 * panel exists whether or not anything is happening, and the motion is what changes.
 *
 * <h2>The one place with its own Timeline</h2>
 *
 * §7.3 says all timers share one driver, and {@link Pulse} is that driver. This is the documented
 * exception: the shared driver ticks at 100ms, which would turn a 2.6-second traverse into 26
 * visible jumps. §5 asks for a <b>linear</b> sweep, not a stepped one — it is the one continuous
 * motion the design language permits, alongside the caret's step blink and everything else's
 * discrete jumps. A per-instance {@link Timeline} with {@link Interpolator#LINEAR} is the correct
 * tool, and the count stays at one or two because of the restriction above.
 */
public final class SweepPanel extends StackPane {

    private static final double BAR_WIDTH = 70;

    private final VBox content = new VBox(UiTokens.SPACE_2);
    private final Region bar = new Region();
    private Timeline sweep;
    private boolean working;

    public SweepPanel(Node... rows) {
        getStyleClass().add("es-working");
        content.getChildren().addAll(rows);

        bar.getStyleClass().add("es-sweep");
        bar.setPrefWidth(BAR_WIDTH);
        bar.setMinWidth(BAR_WIDTH);
        bar.setMaxWidth(BAR_WIDTH);
        bar.setMouseTransparent(true);
        bar.setVisible(false);
        // Left-aligned so translateX is measured from the well's leading edge, and clipped to the
        // well so the bar does not paint over the panel's hairline on its way out.
        StackPane.setAlignment(bar, javafx.geometry.Pos.CENTER_LEFT);

        // A Rectangle, not a Region: Region's width/height are read-only and cannot be bound, which
        // is the trap that makes a Region look like the obvious clip node and then fail to compile.
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        getChildren().addAll(bar, content);
        widthProperty().addListener((obs, was, now) -> {
            if (working) {
                restart();
            }
        });
    }

    public VBox rows() {
        return content;
    }

    /** Starts or stops the sweep. Under reduced motion the bar simply never appears (§5). */
    public void setWorking(boolean working) {
        if (this.working == working) {
            return;
        }
        this.working = working;
        if (working && !Pulse.shared().reducedMotion()) {
            restart();
        } else {
            stop();
        }
    }

    public boolean isWorking() {
        return working;
    }

    private void restart() {
        stop();
        double travel = getWidth() > 0 ? getWidth() : UiTokens.NARROW_WIDTH;
        bar.setVisible(true);
        bar.setTranslateX(-BAR_WIDTH);
        sweep = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(bar.translateXProperty(), -BAR_WIDTH)),
                new KeyFrame(
                        Duration.millis(UiTokens.SWEEP_MS),
                        new KeyValue(bar.translateXProperty(), travel, Interpolator.LINEAR)));
        sweep.setCycleCount(Animation.INDEFINITE);
        sweep.play();
    }

    private void stop() {
        if (sweep != null) {
            sweep.stop();
            sweep = null;
        }
        bar.setVisible(false);
    }

    /** Stops the animation. Call when the panel leaves the scene. */
    public void dispose() {
        stop();
    }
}
