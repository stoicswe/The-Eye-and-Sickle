package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.ScanReport;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The AUDIT window — searching your own rig for processes that are not yours.
 *
 * <h2>Why two tabs</h2>
 *
 * The window was one page holding both the scan controls and the {@code ps} / {@code ss} / {@code df}
 * output, which conflated two different acts: <b>running</b> an audit and <b>reading</b> the machine.
 * They are used at different moments — you press a button once and then watch, or you sit and compare
 * three listings — and stacking them meant the readout a player wants to study kept being pushed down
 * the page by controls they had already used.
 *
 * <ul>
 *   <li><b>SCANNER</b> — the tiers, what each costs, and a live account of the scan in flight.
 *   <li><b>STATUS</b> — the three listings, and the history of every audit this rig has completed.
 * </ul>
 *
 * <h2>⚠ The investigation still lives in STATUS, and the split must not weaken it</h2>
 *
 * {@code docs/design/04-mining.md} §3.1 requires that a careful player can find a rootkit-wrapped
 * miner by noticing that two of the three listings disagree. That is why they stay together on one
 * tab rather than becoming three, and why the history sits <em>beneath</em> them rather than in a
 * window of its own: "this listing changed since the last clean scan" is the same investigation with
 * a date attached.
 */
public final class AuditView {

    private AuditView() {}

    /** Which tab is showing. */
    private enum Tab {
        SCANNER("Scanner", "run an audit and watch it work"),
        STATUS("Status", "processes, connections, storage, and every audit so far");

        private final String label;
        private final String description;

        Tab(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static Region create(GameSession session, Shell shell) {
        VBox root = Views.panel("AUDIT — ps · netstat · df");

        Region scanner = scanner(session);
        Region status = status(session, shell);
        VBox.setVgrow(scanner, Priority.ALWAYS);
        VBox.setVgrow(status, Priority.ALWAYS);

        Tab[] showing = {Tab.SCANNER};
        HBox tabs = new HBox(UiTokens.SPACE_3);
        tabs.getStyleClass().add("es-breach-picker");
        List<BreachView.Chip> chips = new ArrayList<>();
        Runnable[] apply = new Runnable[1];
        for (Tab tab : Tab.values()) {
            BreachView.Chip chip = new BreachView.Chip(tab.label, "es-breach-chip-quiet");
            chip.setAccessibleText(tab.description);
            chip.onInvoke(() -> {
                showing[0] = tab;
                apply[0].run();
            });
            chips.add(chip);
            tabs.getChildren().add(chip);
        }
        apply[0] = () -> {
            for (int i = 0; i < chips.size(); i++) {
                Tab tab = Tab.values()[i];
                BreachView.Chip chip = chips.get(i);
                chip.setText(Ui.upper(tab == showing[0] ? "[ " + tab.label + " ]" : tab.label));
                chip.getStyleClass().remove("es-breach-chip-loud");
                if (tab == showing[0]) {
                    chip.getStyleClass().add("es-breach-chip-loud");
                }
            }
            show(scanner, showing[0] == Tab.SCANNER);
            show(status, showing[0] == Tab.STATUS);
        };
        apply[0].run();

        root.getChildren().addAll(tabs, scanner, status);
        return root;
    }

    /** ⚠ Unmanaged as well as invisible, or the hidden tab still claims its height. */
    private static void show(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ------------------------------------------------------------------ scanner

    /**
     * The tiers, and a live account of the audit in flight.
     *
     * <p>Pillar <b>C1</b>: a tool's cost is shown where the tool is used. Each button carries its
     * published cycles and duration, and an unaffordable tier is <em>refused with a reason</em>
     * rather than greyed out with none — a disabled control that will not say why is the least
     * helpful thing on a panel.
     */
    private static Region scanner(GameSession session) {
        VBox box = new VBox(UiTokens.SPACE_3);

        Label heading = new Label(
                Views.t("ui.audit.scan-search-your-own", "SCAN — search your own rig for adversarial processes"));
        heading.getStyleClass().add("es-panel-title");
        heading.setWrapText(true);

        Label result = new Label();
        result.setWrapText(true);

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        record Tier(String flag, String label, long cycles, String seconds) {}
        for (Tier t : List.of(
                new Tier("quick", "Quick", 5, "30s"),
                new Tier("full", "Full", 15, "2m"),
                new Tier("thorough", "Thorough", 35, "6m"))) {
            Button button = new Button(t.label() + "  ·  " + t.cycles() + " cycles  ·  " + t.seconds());
            button.setMinHeight(30);
            button.setTooltip(new javafx.scene.control.Tooltip(
                    "scan --" + t.flag() + "\n\nWhat a more expensive tier buys is signal strength, "
                            + "not certainty. The cycles come back on the Thermal Budget curve."));
            button.setAccessibleText("Run a " + t.label() + " scan, costing " + t.cycles() + " cycles");
            button.setOnAction(e -> {
                GameSession.Outcome outcome = session.scan(t.flag());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
            });
            row.getChildren().add(button);
        }

        Label note = Views.secondary("Scanning your own rig never generates heat. Cycles spent here "
                + "return slowly, and more slowly the busier the rig already was.");
        note.setWrapText(true);

        box.getChildren().addAll(heading, row, result, note, new Separator(), running(session));
        return box;
    }

    /**
     * The running panel: a line per file as the audit reaches it, and a bar.
     *
     * <h2>⚠ The lines are DERIVED from progress, never appended on a timer</h2>
     *
     * The file list comes from {@link GameSession#auditPaths()} and is stable, so the lines shown are
     * simply the first {@code progress × n} of it. That is what makes the panel survive a repaint,
     * a window close and reopen, and a scan that ran while the client was shut: a panel that appended
     * as it ticked would restart empty every time it was rebuilt, on a readout whose whole subject is
     * work that continues without it.
     *
     * <p>⚠ On {@code Pulse.every} — <b>data</b>, not decoration — so Reduce motion keeps it. A player
     * who cannot see the audit advancing has no way to tell it from a frozen client, which is the
     * same argument the firmware flash makes.
     *
     * <p>⚠ The bar is a fill against a track with square corners: §9's radius ban is unamended for
     * anything a value is read off, and a soft-ended fill reads as a shorter fill.
     */
    private static Region running(GameSession session) {
        VBox box = new VBox(UiTokens.SPACE_2);

        Label heading = new Label(Views.t("ui.audit.running", "RUNNING"));
        heading.getStyleClass().add("es-panel-title");

        Label caption = new Label();
        caption.getStyleClass().add("es-text-secondary");
        caption.setWrapText(true);

        Region track = new Region();
        track.getStyleClass().add("es-audit-track");
        track.setPrefHeight(12);
        track.setMinHeight(12);
        Region fill = new Region();
        fill.getStyleClass().add("es-audit-fill");
        fill.setPrefHeight(12);
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        javafx.beans.property.SimpleDoubleProperty progress = new javafx.beans.property.SimpleDoubleProperty(0);
        // ⚠ BOUND to the track's live width. Setting a pref width from `track.getWidth()` reads 0
        // before the first layout pass, so the bar is empty on the frame the panel opens — the same
        // defect a render caught on the firmware overlay.
        fill.prefWidthProperty().bind(track.widthProperty().multiply(progress));

        VBox lines = new VBox(1);
        lines.getStyleClass().add("es-audit-lines");
        ScrollPane scroll = new ScrollPane(lines);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(180);
        scroll.getStyleClass().add("es-audit-scroll");

        Runnable refresh = () -> {
            var scan = session.tasks().stream()
                    .filter(task -> "scan".equals(task.facility()))
                    .findFirst();
            if (scan.isEmpty()) {
                progress.set(0);
                caption.setText("Nothing running. A scan walks this rig's filesystem looking for "
                        + "processes that are not yours, and prints each file as it reaches it.");
                lines.getChildren().clear();
                return;
            }
            GameSession.RunningTask task = scan.get();
            double done = Math.clamp(task.progress(), 0.0d, 1.0d);
            progress.set(done);

            List<String> paths = session.auditPaths();
            int reached = (int) Math.round(paths.size() * done);
            caption.setText(String.format(
                    java.util.Locale.ROOT,
                    "%s · %s / %s · %d%% · %d of %d files",
                    task.label(),
                    Ui.clock(elapsed(task)),
                    Ui.clock(total(task)),
                    Math.round(done * 100),
                    reached,
                    paths.size()));

            // ⚠ Rebuilt only when the COUNT changes. Clearing and refilling a hundred labels every
            // second would fight the player's scroll position on the one panel they are watching.
            if (lines.getChildren().size() != reached) {
                lines.getChildren().clear();
                for (String path : paths.subList(0, Math.min(reached, paths.size()))) {
                    Label line = new Label(path);
                    line.getStyleClass().addAll("es-mono", "es-audit-line");
                    lines.getChildren().add(line);
                }
                // ⚠ Deferred a pulse: a ScrollPane clamps vvalue against a content height it does
                // not know until it has laid out, so setting it in the same frame the labels are
                // added silently keeps the view at the top — the newest line, which is the only one
                // a player is watching for, ends up off the bottom of the panel.
                javafx.application.Platform.runLater(() -> scroll.setVvalue(1.0d));
            }
        };
        refresh.run();
        AutoCloseable subscription = Pulse.shared().every(500, refresh);
        box.sceneProperty().addListener((obs, was, now) -> {
            if (now == null) {
                try {
                    subscription.close();
                } catch (Exception ignored) {
                    // Best-effort teardown; nothing to say and nothing to do.
                }
            }
        });

        box.getChildren().addAll(heading, caption, bar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return box;
    }

    private static long elapsed(GameSession.RunningTask task) {
        return task.startedAt() == null
                ? 0L
                : Math.max(
                        0L,
                        java.time.Duration.between(task.startedAt(), task.asOf())
                                .toSeconds());
    }

    private static long total(GameSession.RunningTask task) {
        return task.startedAt() == null
                ? 0L
                : Math.max(
                        0L,
                        java.time.Duration.between(task.startedAt(), task.endsAt())
                                .toSeconds());
    }

    // ------------------------------------------------------------------ status

    /**
     * The three listings, and every audit this rig has completed.
     *
     * <p>{@code docs/design/04-mining.md} §3.1's investigation: the three should agree, and when they
     * do not something is hiding. The history sits beneath them because "this changed since the last
     * clean scan" is the same question with a date on it.
     */
    private static Region status(GameSession session, Shell shell) {
        VBox box = new VBox(UiTokens.SPACE_3);

        Label hint = Views.wrapped(Views.t(
                "ui.audit.three-views-of-your",
                "Three views of your own rig. They should agree. When they do not, something is "
                        + "hiding — a connection with no owning process, or storage that grew while "
                        + "nothing was running. That discrepancy is the game."));

        VBox output = new VBox(2);
        ScrollPane scroll = new ScrollPane(output);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Runnable refresh = () -> {
            output.getChildren().clear();
            for (String verb : new String[] {"ps", "ss", "df"}) {
                Label heading = new Label("$ " + verb);
                heading.getStyleClass().addAll("es-mono", "es-panel-title");
                output.getChildren().add(heading);
                for (String line : shell.run(verb).lines()) {
                    Label l = new Label(line);
                    l.getStyleClass().add("es-mono");
                    output.getChildren().add(l);
                }
                output.getChildren().add(new Separator());
            }
            output.getChildren().add(history(session));
        };
        refresh.run();
        session.onChange(s -> refresh.run());

        box.getChildren().addAll(hint, scroll);
        return box;
    }

    /**
     * Every completed audit, newest first.
     *
     * <p>⚠ A clean scan is listed like any other. Zero found is a real result — it is the row that
     * gives a later finding its date, and a history of only the hits would say the rig had always
     * been compromised.
     */
    private static Region history(GameSession session) {
        VBox box = new VBox(2);
        Label heading = new Label(Views.t("ui.audit.scan-history", "SCAN HISTORY"));
        heading.getStyleClass().addAll("es-mono", "es-panel-title");
        box.getChildren().add(heading);

        List<ScanReport> reports = session.scanReports();
        if (reports.isEmpty()) {
            Label empty = new Label(Views.t(
                    "ui.audit.no-audits-yet-a",
                    "No audits yet. A scan's result is recorded here when it "
                            + "finishes, including a clean one — a clean row is what dates a later finding."));
            empty.setWrapText(true);
            empty.getStyleClass().add("es-text-secondary");
            box.getChildren().add(empty);
            return box;
        }

        Label columns = new Label(pad("TYPE", 12) + pad("TOOK", 8) + "FOUND");
        columns.getStyleClass().addAll("es-mono", "es-text-secondary");
        box.getChildren().add(columns);

        for (ScanReport report : reports) {
            Label row = new Label(pad(report.tierLabel(), 12) + pad(report.duration(), 8) + report.summary());
            row.getStyleClass().addAll("es-mono", report.clean() ? "es-audit-clean" : "es-audit-hit");
            row.setWrapText(true);
            box.getChildren().add(row);
        }
        return box;
    }

    /** Character-cell padding, the way every other table in this client aligns. */
    private static String pad(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value.substring(0, Math.max(0, width - 1)) + " ";
        }
        return value + " ".repeat(width - value.length());
    }
}
