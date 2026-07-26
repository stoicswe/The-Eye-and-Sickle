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

    /**
     * Cells across a task's progress meter.
     *
     * <p>Sized so the meter visibly advances on a human timescale. A six-minute Thorough Scan across
     * 24 cells moves one cell every fifteen seconds, which reads as stuck; at 40 it is every nine.
     * The countdown beside it is what actually carries per-second progress — §4 forbids a continuous
     * bar, so the meter is a coarse instrument by design and the number is the fine one.
     */
    private static final int PROGRESS_CELLS = 40;

    /** How often the panel re-reads the session. See the constructor's note. */
    private static final double REFRESH_MS = 400;

    private final VBox rows = new VBox(UiTokens.HAIR);
    private final Label heading = Ui.label("Activity");
    private final Label count = Ui.value("0");
    private final java.util.function.Supplier<List<GameSession.RunningTask>> source;
    private AutoCloseable ticker;

    /**
     * @param source re-read on every tick — <b>not</b> a list handed in once
     *     <p>⚠ This is the whole reason the panel is alive. {@link GameSession.RunningTask} is an
     *     immutable snapshot stamped with the engine's clock at the moment it was built, so holding
     *     onto one and asking it for {@code progress()} again returns the same answer forever. The
     *     panel used to do exactly that and only refreshed when the session fired a change event —
     *     which a running scan does not, because nothing about the rig is changing while it runs.
     *     The result was a progress meter and a countdown that both sat still for six minutes.
     */
    public ActivityList(java.util.function.Supplier<List<GameSession.RunningTask>> source) {
        super(UiTokens.SPACE_2);
        this.source = source;
        heading.getStyleClass().add("es-kv-key");
        HBox head = Ui.row(UiTokens.SPACE_3, heading, count);
        head.setAlignment(Pos.BASELINE_LEFT);
        rows.getStyleClass().add("es-activity");
        getChildren().addAll(head, rows);

        // Faster than the once-a-second granularity of the countdown itself, so a second never
        // *appears* to hang: at 1000ms the readout can be up to a full second stale, which is
        // precisely the interval a watching player is checking it against. This is a data
        // subscription, so it keeps running under reduced motion — a countdown is information, not
        // animation (§5).
        ticker = Pulse.shared().every(REFRESH_MS, this::refresh);
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

    /** Re-reads the session and updates in place. Called on the shared driver, not on change. */
    public void refresh() {
        show(source.get());
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
        private final Label elapsed = Ui.micro("");
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

            // Elapsed / total / percent, under the meter. §4 makes the meter a coarse instrument on
            // purpose — 40 cells across six minutes advances once every nine seconds — so the fine
            // reading has to be a number, and it has to be one that moves every second or the panel
            // reads as frozen no matter how correct it is.
            elapsed.getStyleClass().add("es-buffer-text");

            unknown = new SweepPanel();
            unknown.setMinHeight(UiTokens.METER_BAR_HEIGHT);
            unknown.setVisible(false);
            unknown.setManaged(false);

            getChildren().addAll(top, meter, unknown, Ui.row(UiTokens.SPACE_4, elapsed, detail));
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
                elapsed.setText("");
                return;
            }
            meter.setFraction(progress, false);
            Duration left = task.remaining();
            remaining.setText(left.isZero()
                    ? Ui.upper("finishing")
                    : Ui.upper(clock(left) + " left"));

            Duration total = Duration.between(task.startedAt(), task.endsAt());
            Duration done = total.minus(left);
            elapsed.setText(Ui.upper(clock(done) + " / " + clock(total)
                    + " · " + Math.round(progress * 100) + "%"));
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
