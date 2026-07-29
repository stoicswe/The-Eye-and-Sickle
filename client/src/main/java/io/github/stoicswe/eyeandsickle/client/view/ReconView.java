package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.NodeReport;
import java.util.Arrays;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * RECON — the intelligence files, and a way to find one of them again.
 *
 * <h2>⚠ This window used to be a page ABOUT recon; now it is the recon</h2>
 *
 * It opened as a cost model and two paragraphs on what a port scan teaches, because there was nothing
 * collected to show. That reference belongs in {@code man port-scan}, where a player reads it once and
 * can find it deliberately — a window that made them scroll past the same explanation every time they
 * wanted a report would be teaching the explanation and hiding the data.
 *
 * <h2>Names and tags are the player's, and the game supplies only the search</h2>
 *
 * A machine's address is what it <em>is</em>; a name is what the player decided to call it. Both are
 * shown and both are searched, because the whole job of the box is to find a report from whatever
 * happens to be remembered about it — "the one I called the bank", "10.0.4.", or "rich". A tag
 * vocabulary the game defined would be the game deciding what is worth noticing about a machine,
 * which is exactly the judgement the player is here to make.
 *
 * <p>⚠ Only a machine with a file can be named or tagged. Otherwise this becomes a bookmark folder
 * with the reports buried inside it, which is a different window.
 */
public final class ReconView {

    private ReconView() {}

    /**
     * Builds the panel.
     *
     * @param open how a row is opened — the desk hands back a per-machine report window
     */
    public static Region create(GameSession session, java.util.function.Consumer<String> open) {
        VBox root = Views.panel("RECON — collected reports");

        TextField search = new TextField();
        search.setPromptText("Search address, name or tag");
        search.getStyleClass().add("es-recon-search");
        HBox.setHgrow(search, Priority.ALWAYS);

        Label count = Ui.micro("");
        HBox bar = Ui.row(UiTokens.SPACE_3, search, count);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(UiTokens.SPACE_1);
        Runnable[] repaint = new Runnable[1];
        repaint[0] = () -> paint(rows, count, session, search.getText(), open, repaint[0]);
        repaint[0].run();
        search.textProperty().addListener((observable, was, now) -> repaint[0].run());

        root.getChildren().addAll(
                Views.secondary("Every machine a port scan has come back from. Rows marked "
                        + "[i] in the network list have a file here. `man port-scan` has what a scan "
                        + "costs and what it is a model of."),
                bar,
                rows);

        AutoCloseable onSession = session.onChange(s -> repaint[0].run());
        // Every row carries an age, which is wall-clock derived — see NodeReportView.
        AutoCloseable clock = Pulse.shared().every(1_000, repaint[0]);
        Views.releaseOnDetach(root, onSession, clock);
        return Views.scrollable(root);
    }

    private static void paint(
            VBox into,
            Label count,
            GameSession session,
            String query,
            java.util.function.Consumer<String> open,
            Runnable repaint) {
        into.getChildren().clear();
        List<NodeReport> all = session.nodeReports();
        List<NodeReport> shown = all.stream().filter(report -> report.matches(query)).toList();

        // ⚠ Both figures when a search is narrowing. "3 reports" over a filtered list would let a
        // player conclude they had only ever scanned three machines.
        count.setText(query == null || query.isBlank()
                ? all.size() + (all.size() == 1 ? " report" : " reports")
                : shown.size() + " of " + all.size());

        if (all.isEmpty()) {
            into.getChildren().add(Views.secondary(
                    "Nothing collected yet. Port-scan a machine from the map or the network list and "
                            + "its report appears here."));
            return;
        }
        if (shown.isEmpty()) {
            into.getChildren().add(Views.secondary(
                    "No report matches \"" + query.trim() + "\". The search looks at the address, the "
                            + "name you gave it, and your tags."));
            return;
        }
        var now = session.now();
        for (NodeReport report : shown) {
            into.getChildren().add(row(session, report, now, open, repaint));
        }
    }

    /**
     * One report.
     *
     * <p>⚠ The address is always printed, whatever the machine has been named. Two machines called
     * "backup" are one row twice otherwise, and the address is the field every other window keys on.
     */
    private static Region row(
            GameSession session,
            NodeReport report,
            java.time.Instant now,
            java.util.function.Consumer<String> open,
            Runnable repaint) {
        Label name = new Label(report.displayName());
        name.getStyleClass().addAll("es-mono", "es-recon-name");
        Label address = Ui.micro(report.address());

        Label figures = Ui.micro(report.known() + "/" + NodeReport.total() + " known  ·  "
                + report.scans() + (report.scans() == 1 ? " scan" : " scans")
                + "  ·  opened " + NodeReportView.age(report.createdAt(), now)
                + "  ·  updated " + NodeReportView.age(report.updatedAt(), now));

        VBox text = new VBox(name, address, figures);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label tags = new Label(report.tags().isEmpty() ? "" : "#" + String.join("  #", report.tags()));
        tags.getStyleClass().addAll("es-mono", "es-recon-tags");
        tags.setMinWidth(180);
        tags.setPrefWidth(180);

        BreachView.Chip openIt = new BreachView.Chip("Open", "es-breach-chip-quiet");
        openIt.setAccessibleText("Open the report on " + report.address() + ".");
        openIt.onInvoke(() -> open.accept(report.address()));

        BreachView.Chip rename = new BreachView.Chip("Name", "es-breach-chip-quiet");
        rename.setAccessibleText("Give " + report.address() + " a name of your own.");
        rename.onInvoke(() -> ask(session, report, true, repaint));

        BreachView.Chip retag = new BreachView.Chip("Tags", "es-breach-chip-quiet");
        retag.setAccessibleText("Set your own tags on " + report.address() + ".");
        retag.onInvoke(() -> ask(session, report, false, repaint));

        HBox row = Ui.row(UiTokens.SPACE_3, text, tags, openIt, rename, retag);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("es-recon-row");
        Cursors.shared().clickable(row);
        // ⚠ Double-click opens, matching every other list in this client. Single-click does NOT —
        // the row carries three controls, and a single-click that opened a window would fire on the
        // way to pressing one of them.
        row.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                open.accept(report.address());
            }
        });
        row.setAccessibleText("Report on " + report.address()
                + (report.alias().isBlank() ? "" : ", which you called " + report.alias())
                + ". " + report.known() + " of " + NodeReport.total() + " findings, updated "
                + NodeReportView.age(report.updatedAt(), now)
                + (report.tags().isEmpty() ? "" : ", tagged " + String.join(", ", report.tags()))
                + ". Double-click to open it.");
        return row;
    }

    /**
     * The one-field prompt behind Name and Tags.
     *
     * <p>A {@code TextInputDialog} rather than an inline editor: both are rare, deliberate acts on a
     * row that already carries three controls, and an inline field would make the list reflow every
     * time somebody tabbed through it.
     */
    private static void ask(GameSession session, NodeReport report, boolean naming, Runnable repaint) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(
                naming ? report.alias() : String.join(", ", report.tags()));
        dialog.setTitle(naming ? "Name a machine" : "Tag a machine");
        dialog.setHeaderText(report.address());
        dialog.setContentText(naming
                ? "Call it:"
                : "Tags, comma separated:");
        dialog.showAndWait().ifPresent(value -> {
            if (naming) {
                session.nameNode(report.address(), value);
            } else {
                session.tagNode(report.address(), Arrays.asList(value.split(",")));
            }
            repaint.run();
        });
    }
}
