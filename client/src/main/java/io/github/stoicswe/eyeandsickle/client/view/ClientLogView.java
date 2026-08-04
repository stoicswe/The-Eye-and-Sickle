package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.log.ClientLog;
import io.github.stoicswe.eyeandsickle.client.log.LogEntry;
import io.github.stoicswe.eyeandsickle.client.log.LogLevel;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * CLIENT LOGS — the application's own log, at every level, with a filter per level.
 *
 * <h2>What this is, and what it is not</h2>
 *
 * It is <strong>not fiction</strong>. OVERVIEW is the rig's journal and EVENTS is the game's internal
 * traffic; both are things inside the world. This is the Java program reporting on itself — the same
 * lines that would be on stdout, plus the libraries', in one ordered stream. It is the tab whose
 * contents belong in a bug report.
 *
 * <h2>⚠ TRACE is captured but not shown</h2>
 *
 * {@link ClientLog} takes every level; this panel starts with {@link LogLevel#TRACE} filtered out.
 * The difference matters: a player asked to turn trace on sees <strong>the records that led up to the
 * problem</strong>, not merely what happens after they flip the switch. A panel that only started
 * capturing when the filter was enabled would make every trace-level investigation begin with "now
 * reproduce it again".
 *
 * <h2>⚠ The filter is per LEVEL, not a minimum severity</h2>
 *
 * Five independent toggles rather than a threshold slider. A threshold cannot express "warnings and
 * errors only, without the info chatter", which is the single most useful view when something has
 * gone wrong — and it is the one a reader wants first.
 */
public final class ClientLogView {

    private ClientLogView() {}

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /**
     * ⚠ Everything except TRACE. The default is what a reader wants on opening the tab, and trace is
     * dense enough that including it would push the interesting lines off the top before they read
     * them.
     */
    private static Set<LogLevel> defaultLevels() {
        return EnumSet.complementOf(EnumSet.of(LogLevel.TRACE));
    }

    /**
     * @return the CLIENT LOGS panel
     */
    public static Region create() {
        VBox root = new VBox(UiTokens.SPACE_3);
        ClientLog log = ClientLog.shared();

        Label explain = new Label(Views.t(
                "ui.client-log.the-applications-own-log",
                "The application's own log — this client and the libraries it uses, oldest first. "
                        + "Not the rig's journal: these are real lines from the running program, and they are "
                        + "what a bug report needs."));
        explain.setWrapText(true);
        explain.getStyleClass().add("es-text-secondary");

        // ── one toggle per level ─────────────────────────────────────────────────────────────────
        Set<LogLevel> shown = EnumSet.copyOf(defaultLevels());
        Runnable[] refresh = new Runnable[1];
        HBox levels = new HBox(UiTokens.SPACE_3);
        levels.setAlignment(Pos.CENTER_LEFT);
        for (LogLevel level : LogLevel.values()) {
            Switch toggle = new Switch(level.label());
            toggle.setSelected(shown.contains(level));
            // ⚠ Says what turning it on DOES, not what it is. A screen reader announcing "TRACE"
            // tells somebody nothing about whether they want it.
            toggle.setAccessibleText("Show " + level.label() + " lines");
            toggle.setTooltip(new Tooltip(tooltipFor(level)));
            toggle.selectedProperty().addListener((observable, was, now) -> {
                if (now) {
                    shown.add(level);
                } else {
                    shown.remove(level);
                }
                refresh[0].run();
            });
            levels.getChildren().add(toggle);
        }

        TextField search = new TextField();
        search.setPromptText("Filter by logger, message or stack trace");
        HBox.setHgrow(search, Priority.ALWAYS);

        Switch follow = new Switch(Views.t("ui.client-log.follow", "Follow"));
        follow.setSelected(true);
        follow.setAccessibleText("Scroll to the newest line as it arrives");

        Label count = Ui.micro("");
        BreachView.Chip clear = new BreachView.Chip("Clear", "es-breach-chip-quiet");
        clear.setAccessibleText("Forget every held line, so the next reproduction stands alone.");

        HBox controls = Ui.row(UiTokens.SPACE_3, search, follow, clear, count);
        controls.setAlignment(Pos.CENTER_LEFT);

        ListView<LogEntry> list = new ListView<>();
        list.getStyleClass().add("es-terminal");
        list.setPlaceholder(new Label(Views.t(
                "ui.client-log.nothing-at-these-levels",
                "Nothing at these levels yet. Turn more of them on above, or use the client for a moment.")));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(LogEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                // ⚠ Every style class this cell can carry is removed before one is added. A ListCell
                // is RECYCLED — the same node shows a different row as the list scrolls — so a class
                // left behind paints an INFO line in the error colour, which is the worst possible
                // direction for that mistake to go.
                for (LogLevel level : LogLevel.values()) {
                    getStyleClass().remove(level.styleClass());
                }
                if (empty || entry == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                getStyleClass().add(entry.level().styleClass());
                setText(String.format(
                        "%s  %-5s %-38s %s",
                        STAMP.format(entry.at()),
                        entry.level().label(),
                        entry.logger(),
                        firstLine(entry.message())));
                // ⚠ The stack trace goes in the tooltip, never the row. A row is a fixed-height line
                // in a monospaced list and a trace is forty of them; inlining one would push every
                // other line off the screen at the exact moment the surrounding context matters most.
                StringBuilder tip = new StringBuilder()
                        .append(entry.julLevel())
                        .append("  ")
                        .append(entry.logger())
                        .append("\nthread  ")
                        .append(entry.thread())
                        .append("\n\n")
                        .append(entry.message());
                if (!entry.throwable().isBlank()) {
                    tip.append("\n\n").append(entry.throwable());
                }
                Tooltip tooltip = new Tooltip(tip.toString());
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(720);
                setTooltip(tooltip);
                setAccessibleText(entry.level().label() + " from " + entry.logger() + ". " + entry.message());
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        refresh[0] = () -> {
            String query =
                    search.getText() == null ? "" : search.getText().trim().toLowerCase();
            List<LogEntry> visible = log.entries().stream()
                    .filter(entry -> shown.contains(entry.level()))
                    .filter(entry -> query.isEmpty()
                            || entry.logger().toLowerCase().contains(query)
                            || entry.message().toLowerCase().contains(query)
                            || entry.throwable().toLowerCase().contains(query))
                    .toList();
            // Only touch the list when it actually changed — replacing the items every tick would
            // fight the reader's scroll position and selection. The same rule OVERVIEW and EVENTS
            // follow, and it matters more here because this is the tab somebody reads while a
            // problem is still happening.
            if (!visible.equals(list.getItems())) {
                list.getItems().setAll(visible);
                if (follow.isSelected() && !visible.isEmpty()) {
                    list.scrollTo(visible.size() - 1);
                }
            }
            count.setText(log.size() + " held"
                    + (log.dropped() > 0 ? "  ·  " + log.dropped() + " dropped" : "")
                    + "  ·  " + visible.size() + " shown");
        };
        refresh[0].run();
        search.textProperty().addListener((observable, was, now) -> refresh[0].run());
        clear.onInvoke(() -> {
            log.clear();
            refresh[0].run();
        });

        // ⚠ Pulse.every — DATA, not decoration. Under reduced motion this keeps ticking, because
        // suppressing it would leave a log that stops updating rather than an animation that stops
        // moving. Same call the EVENTS tab makes, same reason.
        AutoCloseable clock = Pulse.shared().every(500, refresh[0]);
        Views.releaseOnDetach(root, clock);

        root.getChildren().addAll(explain, levels, controls, list);
        return root;
    }

    /** ⚠ One line in the row. A multi-line message would break the character-cell alignment. */
    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline) + " …";
    }

    private static String tooltipFor(LogLevel level) {
        return switch (level) {
            case ERROR -> "Something failed.";
            case WARN -> "Something is wrong, and the client carried on.";
            case INFO -> "The ordinary record of what the client did.";
            case DEBUG -> "Detail for chasing a specific problem.";
            case TRACE -> "Everything else. Captured even while this is off, so turning it on shows "
                    + "what led up to a problem rather than only what happens next.";
        };
    }
}
