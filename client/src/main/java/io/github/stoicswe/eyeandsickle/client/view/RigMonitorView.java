package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The compute readout — the one window that is always on screen.
 *
 * <h2>Why this window is not closable</h2>
 *
 * {@code docs/design/01-core-resources.md} §1.4 makes the compute ledger mandatory and always
 * visible, and client pillar <b>C2</b> restates it: compute is the master scarcity, every meaningful
 * decision in the game is a question of where to spend cycles, and a player must be able to read this
 * at a glance without going to find it.
 *
 * <h2>Three states, not two</h2>
 *
 * Allocated, recovering and available are shown separately because they <em>are</em> separate.
 * Spent cycles do not return instantly ({@code docs/design/01} §1.3), so folding "recovering" into
 * either of the other two would make the readout lie about what the player can commit right now —
 * and would leave "where did my 35 cycles go" without an answer on screen.
 *
 * <h2>The reconciliation warning</h2>
 *
 * If the three do not sum to the total, this says so in as many words. That is not defensive
 * programming; it is the game's central investigation ({@code docs/design/04-mining.md} §3.1). A
 * player is supposed to be able to catch a hidden miner by noticing the numbers do not add up, and a
 * HUD that quietly rounded the discrepancy away would disable the skill the whole game is built on.
 */
public final class RigMonitorView {

    private RigMonitorView() {}

    public static Region create(GameSession session) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));
        root.getStyleClass().add("es-panel");

        Label title = new Label("COMPUTE");
        title.getStyleClass().add("es-panel-title");

        Label headline = new Label();
        headline.getStyleClass().addAll("es-numeric", "es-compute");
        headline.setStyle("-fx-font-size: 26px;");

        ProgressBar gauge = new ProgressBar(0);
        gauge.setMaxWidth(Double.MAX_VALUE);

        Label breakdown = new Label();
        breakdown.getStyleClass().addAll("es-numeric", "es-text-secondary");

        VBox consumers = new VBox(4);

        Label warning = new Label();
        warning.getStyleClass().add("es-state-refused");
        warning.setWrapText(true);
        warning.setVisible(false);
        warning.setManaged(false);

        Label balance = new Label();
        balance.getStyleClass().addAll("es-numeric", "es-ethecoin");

        Label mode = new Label();
        mode.getStyleClass().add("es-text-secondary");
        mode.setWrapText(true);

        root.getChildren()
                .addAll(title, headline, gauge, breakdown, new Label("BY CONSUMER"), consumers, warning,
                        new Label("BALANCE"), balance, mode);
        VBox.setVgrow(consumers, Priority.ALWAYS);

        Runnable refresh = () -> {
            ComputeBudget b = session.computeBudget();
            long total = b.total().cycles();
            long available = b.available().cycles();

            headline.setText(available + " / " + total + " cycles");
            gauge.setProgress(total == 0 ? 0 : (double) (total - available) / total);
            breakdown.setText("allocated " + b.allocated().cycles()
                    + "   recovering " + b.recovering().cycles()
                    + "   available " + available);

            consumers.getChildren().clear();
            if (b.allocations().isEmpty()) {
                Label idle = new Label("nothing allocated");
                idle.getStyleClass().add("es-text-secondary");
                consumers.getChildren().add(idle);
            }
            for (ComputeAllocation a : b.allocations()) {
                HBox row = new HBox(8);
                Label name = new Label(a.consumer().name().toLowerCase(Locale.ROOT).replace('_', ' '));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label cycles = new Label(a.cycles().cycles() + (a.isRecovering() ? " ↻" : ""));
                cycles.getStyleClass().add("es-numeric");
                if (a.isRecovering()) {
                    // Recovering cycles are shown in the pending style, because they are not yours
                    // to spend yet. Same visual language as an unconfirmed server value (pillar C4).
                    cycles.getStyleClass().add("es-state-pending");
                }
                row.setAlignment(Pos.CENTER_LEFT);
                row.getChildren().addAll(name, spacer, cycles);
                consumers.getChildren().add(row);
            }

            boolean reconciles = b.reconciles();
            warning.setVisible(!reconciles);
            warning.setManaged(!reconciles);
            if (!reconciles) {
                warning.setText(b.unaccountedFor().cycles()
                        + " cycles unaccounted for. Something is consuming this rig that is not in "
                        + "this list. Try `scan --full`.");
            }

            balance.setText(session.balance().toString());
            mode.setText(session.mode().label() + " — " + session.mode().explanation());
        };

        refresh.run();
        session.onChange(s -> refresh.run());
        return root;
    }
}
