package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The rig monitor's strip form — the compute readout as a horizontal band.
 *
 * <h2>This is chrome, not a pane</h2>
 *
 * {@code docs/client/05-tool-windows-and-layout.md} §5.2 is emphatic about the distinction: the strip
 * <em>never scrolls, never collapses, never hosts a tab.</em> That is how client pillar <b>C2</b>
 * survives single-window mode — the compute ledger structurally cannot be occluded, because there is
 * no z-order for it to lose. In the multi-window layout the rig monitor is always-on-top; here it is
 * simply part of the frame, which is a stronger guarantee than always-on-top ever was.
 *
 * <h2>The trace row is always present, even when idle</h2>
 *
 * §5.2 again: it shows {@code no active engagement} rather than disappearing, so its arrival during a
 * breach never reflows the strip. A HUD that changes shape at the exact moment the player is under
 * time pressure is a HUD that moves the thing they were about to read.
 */
public final class RigStripView {

    private RigStripView() {}

    /** Comfortable height from §5.2. The compact variant is 80px. */
    public static final double HEIGHT_COMFORTABLE = 96;

    public static final double HEIGHT_COMPACT = 80;

    public static Region create(GameSession session) {
        HBox strip = new HBox(24);
        strip.getStyleClass().add("es-strip");
        strip.setPadding(new Insets(8, 16, 8, 16));
        strip.setMinHeight(HEIGHT_COMFORTABLE);
        strip.setPrefHeight(HEIGHT_COMFORTABLE);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setAccessibleText("Rig status. Always visible.");

        // ---- compute, left third
        Label computeHeading = new Label("COMPUTE");
        computeHeading.getStyleClass().addAll("es-text-secondary");
        Label computeValue = new Label();
        computeValue.getStyleClass().addAll("es-numeric", "es-compute");
        computeValue.setStyle("-fx-font-size: 18px;");
        ProgressBar gauge = new ProgressBar(0);
        gauge.setPrefWidth(220);
        Label consumers = new Label();
        consumers.getStyleClass().addAll("es-numeric", "es-text-secondary");

        VBox compute = new VBox(2, computeHeading, computeValue, gauge, consumers);
        compute.setMinWidth(260);

        // ---- trace, centre. Always present; see the class comment.
        Label traceHeading = new Label("TRACE");
        traceHeading.getStyleClass().add("es-text-secondary");
        Label traceValue = new Label("no active engagement");
        traceValue.getStyleClass().addAll("es-numeric", "es-text-secondary");
        ProgressBar traceGauge = new ProgressBar(0);
        traceGauge.setPrefWidth(200);
        VBox trace = new VBox(2, traceHeading, traceValue, traceGauge);
        trace.setMinWidth(220);

        // ---- balance and heat, right
        Label balanceHeading = new Label("BALANCE");
        balanceHeading.getStyleClass().add("es-text-secondary");
        Label balanceValue = new Label();
        balanceValue.getStyleClass().addAll("es-numeric", "es-ethecoin");
        Label heatValue = new Label();
        heatValue.getStyleClass().add("es-numeric");
        VBox money = new VBox(2, balanceHeading, balanceValue, heatValue);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label mode = new Label();
        mode.getStyleClass().add("es-text-secondary");

        Label warning = new Label();
        warning.getStyleClass().add("es-state-refused");
        warning.setWrapText(true);
        warning.setVisible(false);
        warning.setManaged(false);

        strip.getChildren().addAll(compute, trace, money, spacer, warning, mode);

        Runnable refresh = () -> {
            ComputeBudget b = session.computeBudget();
            long total = b.total().cycles();
            long available = b.available().cycles();

            computeValue.setText(available + " / " + total);
            gauge.setProgress(total == 0 ? 0 : (double) (total - available) / total);
            consumers.setText("alloc " + b.allocated().cycles() + "  recov " + b.recovering().cycles());

            // The accessible text carries the whole reading, because a screen reader user gets no
            // benefit from a gauge (docs/client/07 §4.4).
            gauge.setAccessibleText(available + " of " + total + " cycles available, "
                    + b.allocated().cycles() + " allocated, " + b.recovering().cycles() + " recovering");
            Tooltip.install(gauge, new Tooltip(gauge.getAccessibleText()));

            StringBuilder byConsumer = new StringBuilder();
            for (ComputeAllocation a : b.allocations()) {
                byConsumer.append(a.consumer().name().toLowerCase(Locale.ROOT).replace('_', ' '))
                        .append(' ')
                        .append(a.cycles().cycles())
                        .append("   ");
            }
            compute.setAccessibleText("Compute. " + gauge.getAccessibleText()
                    + (byConsumer.isEmpty() ? "" : ". Held by: " + byConsumer));

            balanceValue.setText(session.balance().toString());
            heatValue.setText("heat " + session.personalHeat());
            mode.setText(session.mode().label() + (session.connected() ? "" : " · disconnected"));
            mode.getStyleClass().removeAll("es-state-unreachable");
            if (!session.connected()) {
                // Refused and unreachable must never look the same (docs/client/01 §9.4).
                mode.getStyleClass().add("es-state-unreachable");
            }

            boolean reconciles = b.reconciles();
            warning.setVisible(!reconciles);
            warning.setManaged(!reconciles);
            if (!reconciles) {
                warning.setText(b.unaccountedFor().cycles() + " cycles unaccounted for");
            }
        };

        refresh.run();
        session.onChange(s -> refresh.run());
        return strip;
    }
}
