package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * What the rig is doing right now — the Activity Monitor of the deck.
 *
 * <h2>Progress is counted, not swept</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 is unambiguous about meters: "3px × 9px cells with 1px
 * gaps. <b>Never a continuous bar or gradient.</b>" So a task's progress is {@link CellMeter}, not a
 * {@link javafx.scene.control.ProgressBar} — and the reason is the same one behind
 * {@link CycleGrid}: a smooth bar implies a continuous quantity and invites a precision the model
 * does not have. Twenty cells of a thirty-second scan is a figure a player can read at a glance and
 * cannot over-read.
 *
 * <p>The exact remaining time sits beside it in words ({@code 04:12 LEFT}), because §6 asks for
 * units on every value and because {@code docs/client/07-accessibility.md} §5.2 forbids meaning that
 * rests on appearance alone — a meter with no number is a picture of progress rather than a report
 * of it.
 *
 * <h2>Unknown progress is shown as unknown</h2>
 *
 * {@link GameSession.RunningTask#progress()} returns a negative value when the start time was never
 * recorded — which happens on state written before the field existed. That is <b>not</b> zero, and
 * rendering it as an empty meter would tell the player a nearly-finished recovery had not started.
 * Those rows get the {@link SweepPanel} treatment instead: §5's linear sweep, which is the design
 * language's one signal for "in progress, duration unknown".
 *
 * <h2>Empty is an instruction</h2>
 *
 * §6: "Empty states are an instruction, not a mood piece." An idle rig says what would put something
 * here, rather than reporting that there is nothing here — which the player can already see.
 */
public final class ActivityList extends VBox {

    /** Cells across a task's progress meter. Enough to read a proportion, few enough to count. */
    private static final int PROGRESS_CELLS = 24;

    private final VBox rows = new VBox(UiTokens.HAIR);
    private final Label heading = Ui.label("Activity");
    private final Label count = Ui.value("0");
    private AutoCloseable ticker;

    public ActivityList() {
        super(UiTokens.SPACE_2);
        heading.getStyleClass().add("es-kv-key");
        HBox head = Ui.row(UiTokens.SPACE_3, heading, count);
        head.setAlignment(Pos.BASELINE_LEFT);
        rows.getStyleClass().add("es-activity");
        getChildren().addAll(head, rows);

        // A second is the right granularity: these are wall-clock waits measured in tens of seconds
        // to minutes, and the design language's motion rules make values twitch to a new figure
        // rather than tween towards one (§5), so there is nothing to interpolate between ticks.
        ticker = Pulse.shared().every(1000, this::retime);
    }

    private List<GameSession.RunningTask> current = List.of();
    private final List<Row> live = new ArrayList<>();

    /**
     * Replaces the task list.
     *
     * <p>Rebuilds only when the <em>set</em> of tasks changes; otherwise {@link #retime()} updates
     * the figures in place. Rebuilding every second would drop the player's hover and re-create
     * two dozen nodes a second for a panel whose content is four numbers.
     */
    public void show(List<GameSession.RunningTask> tasks) {
        boolean sameSet = tasks.size() == current.size();
        if (sameSet) {
            for (int i = 0; i < tasks.size(); i++) {
                if (!tasks.get(i).id().equals(current.get(i).id())) {
                    sameSet = false;
                    break;
                }
            }
        }
        current = List.copyOf(tasks);
        count.setText(String.valueOf(tasks.size()));

        if (sameSet && !live.isEmpty()) {
            retime();
            return;
        }

        live.clear();
        rows.getChildren().clear();
        if (tasks.isEmpty()) {
            rows.getChildren().add(Note.empty(
                    "Nothing running. A scan, or cycles returning from one, appears here with its "
                            + "time remaining."));
            return;
        }
        for (GameSession.RunningTask task : tasks) {
            Row row = new Row(task);
            live.add(row);
            rows.getChildren().add(row);
        }
        retime();
    }

    private void retime() {
        for (int i = 0; i < live.size() && i < current.size(); i++) {
            live.get(i).update(current.get(i));
        }
    }

    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
        for (Row row : live) {
            row.dispose();
        }
    }

    /** One task: name, facility, cycles held, progress, time remaining. */
    private static final class Row extends VBox {

        private final Label remaining = Ui.micro("");
        private final CellMeter meter = new CellMeter(PROGRESS_CELLS);
        private final SweepPanel unknown;
        private final Label detail;

        private Row(GameSession.RunningTask task) {
            super(3);
            getStyleClass().add("es-activity-row");

            Label name = Ui.label(task.label());
            name.getStyleClass().add("es-activity-name");
            Label facility = Ui.label(task.facility());
            facility.getStyleClass().add("es-legend-sub");

            Label cycles = new Label(task.cycles() + "C");
            cycles.getStyleClass().add("es-legend-n");
            remaining.getStyleClass().add("es-buffer-text");

            HBox top = Ui.row(UiTokens.SPACE_4, name, facility, Ui.spacer(), cycles, remaining);
            HBox.setHgrow(top, Priority.ALWAYS);

            detail = Ui.micro(task.detail());
            detail.getStyleClass().add("es-legend-sub");

            unknown = new SweepPanel();
            unknown.setMinHeight(UiTokens.METER_BAR_HEIGHT);
            unknown.setVisible(false);
            unknown.setManaged(false);

            getChildren().addAll(top, meter, unknown, detail);
        }

        private void update(GameSession.RunningTask task) {
            double progress = task.progress();
            boolean indeterminate = progress < 0;

            meter.setVisible(!indeterminate);
            meter.setManaged(!indeterminate);
            unknown.setVisible(indeterminate);
            unknown.setManaged(indeterminate);
            unknown.setWorking(indeterminate);

            if (indeterminate) {
                remaining.setText(Ui.upper("elapsed unknown"));
                return;
            }
            meter.setFraction(progress, false);
            Duration left = task.remaining();
            remaining.setText(left.isZero()
                    ? Ui.upper("finishing")
                    : Ui.upper(clock(left) + " left"));
        }

        private void dispose() {
            unknown.dispose();
        }

        /**
         * {@code M:SS}, or {@code H:MM:SS} past an hour.
         *
         * <p>Not a humanised "about 4 minutes". §6 wants operational readouts with units, and a
         * countdown a player is timing an action against has to be exact — "about 4 minutes" is
         * unusable for deciding whether there is room to start something else.
         */
        private static String clock(Duration d) {
            long total = Math.max(0, d.toSeconds());
            long hours = total / 3600;
            long minutes = (total % 3600) / 60;
            long seconds = total % 60;
            return hours > 0
                    ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                    : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
        }
    }
}
