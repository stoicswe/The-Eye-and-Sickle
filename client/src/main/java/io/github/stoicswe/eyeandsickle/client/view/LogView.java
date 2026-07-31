package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.LocalTime;
import java.time.ZoneId;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The rig log — a live stream of what the machine has been doing.
 *
 * <h2>What this is for</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 asks that a careful player be able to reconstruct what
 * happened to their rig well enough to notice something that should not be there. The audit window
 * shows the machine's <em>current</em> state; this shows how it got there. Between them the
 * discrepancy the whole investigation rests on becomes findable rather than merely present.
 *
 * <p>Its other job is quieter and matters more day to day: telling a returning player what happened
 * while they were away. Offline income accrues silently, and silent income is indistinguishable from
 * a bug.
 *
 * <h2>Real severities, real filter flag</h2>
 *
 * {@code docs/client/04-terminology-and-education.md} §3.10 maps this to {@code journalctl -f} and
 * specifies RFC 5424's eight levels. The filter here is the same one {@code journalctl -p} takes,
 * with the same backwards-feeling numbering — lower is more severe, and filtering to {@code 4} shows
 * warnings <em>and worse</em>. Every glyph is paired with its keyword, because a glyph alone is a
 * private code and {@code docs/client/07} §5.2 forbids meaning that rests on appearance.
 *
 * <h2>Why it does not log every tick</h2>
 *
 * A line each second saying self-mining earned 0.011 EC would bury the one line that mattered. That
 * is {@code alert-fatigue(7)} — a page in this game's own manual — and a log that cries wolf teaches
 * its reader to stop looking, which disables the investigation this panel exists to support. State
 * changes are logged; ticks are not.
 */
public final class LogView {

    private LogView() {}

    /** Refresh cadence. Matches the game's heartbeat, so `-f` behaviour feels live without polling hard. */
    private static final Duration FOLLOW_INTERVAL = Duration.seconds(1);

    public static Region create(GameSession session) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(12));

        Label heading = new Label(Views.t("ui.log.log-journalctl-f", "LOG — journalctl -f"));
        heading.getStyleClass().add("es-panel-title");

        Label explain = new Label(Views.t(
                "ui.log.what-the-rig-has",
                "What the rig has been doing, newest last. This is where offline income, recovered "
                        + "cycles and anything that changed while you were not watching show up."));
        explain.setWrapText(true);
        explain.getStyleClass().add("es-text-secondary");

        // Severity filter, using journalctl's own numbering so the habit transfers.
        ChoiceBox<Severity> filter = new ChoiceBox<>();
        filter.getItems().addAll(Severity.values());
        filter.setValue(Severity.ALL);
        filter.setAccessibleText("Filter the log by severity, lowest number is most severe");
        filter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Severity s) {
                return s == null ? "" : s.label();
            }

            @Override
            public Severity fromString(String s) {
                return Severity.ALL;
            }
        });

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch follow =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(Views.t("ui.log.follow", "Follow"));
        follow.setSelected(true);
        follow.setAccessibleText("Scroll to the newest entry as it arrives, like tail -f");
        follow.setTooltip(new javafx.scene.control.Tooltip(
                "Keeps the newest line in view as entries arrive — what `tail -f` and `journalctl -f` do."));

        HBox controls = new HBox(10, new Label(Views.t("ui.log.severity", "Severity")), filter, follow);
        controls.setAlignment(Pos.CENTER_LEFT);

        ListView<GameSession.LogLine> list = new ListView<>();
        list.getStyleClass().add("es-terminal");
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(GameSession.LogLine line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll("es-state-refused", "es-state-unreachable", "es-text-secondary");
                if (empty || line == null) {
                    setText(null);
                    return;
                }
                LocalTime t = line.at().atZone(ZoneId.systemDefault()).toLocalTime();
                setText(String.format(
                        "%02d:%02d:%02d  %s %-8s %-9s %s",
                        t.getHour(),
                        t.getMinute(),
                        t.getSecond(),
                        line.glyph(),
                        line.keyword(),
                        line.facility(),
                        line.message()));

                // Colour reinforces the glyph and keyword; it never replaces them.
                if (line.severity() <= 3) {
                    getStyleClass().add("es-state-refused");
                } else if (line.severity() == 4) {
                    getStyleClass().add("es-state-unreachable");
                } else if (line.severity() >= 6) {
                    getStyleClass().add("es-text-secondary");
                }
                setAccessibleText(line.keyword() + " from " + line.facility() + ": " + line.message());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Label empty = new Label(Views.t(
                "ui.log.nothing-yet-allocate-some",
                "Nothing yet. Allocate some cycles, arm a defence, or run a scan."));
        empty.setWrapText(true);
        empty.getStyleClass().add("es-text-secondary");
        list.setPlaceholder(empty);

        Runnable refresh = () -> {
            var lines = session.log(filter.getValue().level(), 500);
            // Only touch the list when something actually changed: replacing the items every second
            // would fight the player's scroll position and their selection.
            if (lines.size() != list.getItems().size() || !lines.equals(list.getItems())) {
                list.getItems().setAll(lines);
                if (follow.isSelected() && !lines.isEmpty()) {
                    list.scrollTo(lines.size() - 1);
                }
            }
        };
        filter.valueProperty().addListener((o, was, now) -> {
            list.getItems().clear();
            refresh.run();
        });

        refresh.run();
        session.onChange(s -> refresh.run());

        Timeline pulse = new Timeline(new KeyFrame(FOLLOW_INTERVAL, e -> refresh.run()));
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();

        Label footer = new Label(Views.t(
                "ui.log.log-prints-this-in",
                "`log` prints this in the terminal; `log -p 4` filters to warnings and worse. "
                        + "The numbering is RFC 5424's, where 0 is most severe and 7 is least."));
        footer.setWrapText(true);
        footer.getStyleClass().add("es-text-secondary");

        // ── the two tabs ──────────────────────────────────────────────────────────────────────
        //
        // ⚠ OVERVIEW is everything this window already was, unchanged and in the same order. The
        // EVENTS tab is a debugging surface added beside it, not a replacement — a player who opens
        // LOG is looking for what the rig has been doing, and putting a stream of CloudEvent types in
        // front of that would answer a question only a developer is asking.
        VBox overview = new VBox(UiTokens.SPACE_3, explain, controls, list, footer);
        VBox.setVgrow(overview, Priority.ALWAYS);
        VBox.setVgrow(list, Priority.ALWAYS);
        Region events = EventLogView.create(session);
        VBox.setVgrow(events, Priority.ALWAYS);

        LogTab[] tab = {LogTab.OVERVIEW};
        HBox tabs = new HBox(UiTokens.SPACE_3);
        tabs.getStyleClass().add("es-breach-picker");
        java.util.List<BreachView.Chip> chips = new java.util.ArrayList<>();
        Runnable[] applyTab = new Runnable[1];
        for (LogTab which : LogTab.values()) {
            BreachView.Chip chip = new BreachView.Chip(which.control(LogTab.OVERVIEW), "es-breach-chip-quiet");
            chip.setAccessibleText(which.description());
            chip.onInvoke(() -> {
                tab[0] = which;
                applyTab[0].run();
            });
            chips.add(chip);
            tabs.getChildren().add(chip);
        }
        applyTab[0] = () -> {
            for (int i = 0; i < chips.size(); i++) {
                LogTab which = LogTab.values()[i];
                BreachView.Chip chip = chips.get(i);
                chip.setText(Ui.upper(which.control(tab[0])));
                chip.getStyleClass().remove("es-breach-chip-loud");
                if (which == tab[0]) {
                    chip.getStyleClass().add("es-breach-chip-loud");
                }
            }
            show(overview, tab[0] == LogTab.OVERVIEW);
            show(events, tab[0] == LogTab.EVENTS);
        };
        applyTab[0].run();

        root.getChildren().addAll(heading, tabs, overview, events);
        return root;
    }

    /** ⚠ Unmanaged as well as invisible, or the hidden tab still claims its height. */
    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * The filter levels offered, using {@code journalctl -p}'s numbering.
     *
     * <p>Not every RFC 5424 level is offered, because the game does not emit every level and a filter
     * for a severity nothing produces is a control that does nothing.
     */
    private enum Severity {
        ALL(7, "everything"),
        NOTICE(5, "notice and worse (-p 5)"),
        WARNING(4, "warnings and worse (-p 4)"),
        ERROR(3, "errors only (-p 3)");

        private final int level;
        private final String label;

        Severity(int level, String label) {
            this.level = level;
            this.label = label;
        }

        int level() {
            return level;
        }

        String label() {
            return label;
        }
    }
}
