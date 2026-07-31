package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.NodeReport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
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
 * <h2>⚠ TWO refreshes, and separating them is what makes the row editable at all</h2>
 *
 * Every row carries ages, which are wall-clock derived, so they need a one-second pulse. But the first
 * version rebuilt the whole row list on that pulse — which would tear down an open name editor or tag
 * field <b>mid-keystroke, once a second</b>, and nobody can type into a control that is replaced
 * before the second character arrives.
 *
 * <p>So the row list is rebuilt only when the <em>data</em> changes, and the pulse touches nothing but
 * the age labels through {@code ticking}. It is the same split {@code Views.ledger} already makes for
 * its block ages, and here it is not a nicety — it is the difference between a working editor and one
 * that silently eats input. A data rebuild is additionally suppressed while an editor is open, because
 * a scan completing behind the player must not delete the name they are half way through typing.
 *
 * <h2>Names and tags are the player's, and the game supplies only the search</h2>
 *
 * A machine's address is what it <em>is</em>; a name is what the player decided to call it. Both are
 * shown and both are searched, because the whole job of the box is to find a report from whatever
 * happens to be remembered about it.
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

        Set<String> filter = new LinkedHashSet<>();
        BreachView.Chip filterChip = new BreachView.Chip("Filter", "es-breach-chip-quiet");
        Label count = Ui.micro("");
        HBox bar = Ui.row(UiTokens.SPACE_3, search, filterChip, count);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox rows = new VBox(UiTokens.SPACE_1);
        List<Runnable> ticking = new ArrayList<>();
        boolean[] editing = {false};
        Runnable[] repaint = new Runnable[1];

        repaint[0] = () -> {
            // ⚠ Never while an editor is open. A scan finishing behind the player must not delete the
            // name they are half way through typing.
            if (editing[0]) {
                return;
            }
            paint(rows, ticking, count, session, search.getText(), filter, open, repaint[0], editing);
            filterChip.setText(filter.isEmpty() ? "FILTER" : Ui.upper("Filter: " + String.join(", ", filter)));
        };
        repaint[0].run();
        search.textProperty().addListener((observable, was, now) -> repaint[0].run());

        filterChip.setAccessibleText(
                "Filter the list by tag. Choosing more than one narrows it: a " + "report has to carry all of them.");
        filterChip.onInvoke(() -> showFilter(session, filterChip, filter, repaint[0]));

        root.getChildren()
                .addAll(
                        Views.secondary("Every machine a port scan has come back from. Rows marked [i] in "
                                + "the network list have a file here. Double-click a name to rename it; "
                                + "`man port-scan` has what a scan costs and what it is a model of."),
                        bar,
                        rows);

        AutoCloseable onSession = session.onChange(s -> repaint[0].run());
        // ⚠ Ages ONLY. See the class comment: rebuilding rows here would destroy any open editor.
        AutoCloseable clock = Pulse.shared().every(1_000, () -> ticking.forEach(Runnable::run));
        Views.releaseOnDetach(root, onSession, clock);
        return Views.scrollable(root);
    }

    /**
     * The tag filter.
     *
     * <p>⚠ Selecting more than one <b>narrows</b>: a report must carry every chosen tag. That is what
     * a filter means — adding a criterion makes the list shorter — and the alternative, matching any
     * of them, makes each extra tag <em>widen</em> the result, which reads as the control working
     * backwards.
     */
    private static void showFilter(
            GameSession session, javafx.scene.Node anchor, Set<String> filter, Runnable repaint) {
        Set<String> known = new TreeSet<>();
        session.nodeReports().forEach(report -> known.addAll(report.tags()));

        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        if (known.isEmpty()) {
            javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem("No tags yet");
            none.setDisable(true);
            menu.getItems().add(none);
        }
        for (String tag : known) {
            javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(tag);
            item.setSelected(filter.contains(tag));
            item.setOnAction(event -> {
                if (item.isSelected()) {
                    filter.add(tag);
                } else {
                    filter.remove(tag);
                }
                repaint.run();
            });
            menu.getItems().add(item);
        }
        if (!filter.isEmpty()) {
            menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            javafx.scene.control.MenuItem clear = new javafx.scene.control.MenuItem("Show all");
            clear.setOnAction(event -> {
                filter.clear();
                repaint.run();
            });
            menu.getItems().add(clear);
        }
        // ⚠ Anchored to the WINDOW, not to the chip — the chip's own label changes when the filter
        // does, and a popup owned by a node that is re-laid-out underneath it is the crash
        // NodeMenuTest exists for. Screen coordinates put it in the same place either way.
        javafx.stage.Window window =
                anchor.getScene() == null ? null : anchor.getScene().getWindow();
        if (window == null) {
            return;
        }
        var where = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
        if (where != null) {
            menu.show(window, where.getX(), where.getY());
        }
    }

    private static void paint(
            VBox into,
            List<Runnable> ticking,
            Label count,
            GameSession session,
            String query,
            Set<String> filter,
            java.util.function.Consumer<String> open,
            Runnable repaint,
            boolean[] editing) {
        into.getChildren().clear();
        ticking.clear();
        List<NodeReport> all = session.nodeReports();
        List<NodeReport> shown = all.stream()
                .filter(report -> report.matches(query))
                .filter(report -> report.tags().containsAll(filter))
                .toList();

        // ⚠ Both figures when anything is narrowing. "3 reports" over a filtered list would let a
        // player conclude they had only ever scanned three machines.
        boolean narrowed = (query != null && !query.isBlank()) || !filter.isEmpty();
        count.setText(
                narrowed
                        ? shown.size() + " of " + all.size()
                        : all.size() + (all.size() == 1 ? " report" : " reports"));

        if (all.isEmpty()) {
            into.getChildren()
                    .add(Views.secondary(
                            "Nothing collected yet. Port-scan a machine from the map or the network list and "
                                    + "its report appears here."));
            return;
        }
        if (shown.isEmpty()) {
            into.getChildren()
                    .add(Views.secondary(
                            "Nothing matches. The search looks at the address, the name you gave it and your "
                                    + "tags; the filter needs a report to carry every tag you picked."));
            return;
        }
        for (NodeReport report : shown) {
            into.getChildren().add(row(session, report, ticking, open, repaint, editing));
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
            List<Runnable> ticking,
            java.util.function.Consumer<String> open,
            Runnable repaint,
            boolean[] editing) {

        HBox nameLine = Ui.row(UiTokens.SPACE_2);
        nameLine.setAlignment(Pos.CENTER_LEFT);
        nameLine.getChildren().add(nameField(session, report, nameLine, repaint, editing));

        HBox addressLine = Ui.row(UiTokens.SPACE_2);
        addressLine.setAlignment(Pos.CENTER_LEFT);
        addressLine
                .getChildren()
                .addAll(Ui.micro(report.address()), addTag(session, report, addressLine, repaint, editing));

        Label figures = Ui.micro("");
        Runnable retime = () -> figures.setText(report.known() + "/" + NodeReport.total() + " known"
                + "  ·  " + report.scans() + (report.scans() == 1 ? " scan" : " scans")
                + "  ·  opened " + NodeReportView.age(report.createdAt(), session.now())
                + "  ·  updated " + NodeReportView.age(report.updatedAt(), session.now()));
        retime.run();
        ticking.add(retime);

        VBox text = new VBox(nameLine, addressLine, figures);
        HBox.setHgrow(text, Priority.ALWAYS);

        FlowPane tags = new FlowPane(UiTokens.SPACE_1, UiTokens.SPACE_1);
        tags.setMinWidth(210);
        tags.setPrefWidth(210);
        for (String tag : report.tags()) {
            tags.getChildren().add(tagPill(session, report, tag, repaint));
        }

        BreachView.Chip openIt = new BreachView.Chip("Open", "es-breach-chip-quiet");
        openIt.setAccessibleText("Open the report on " + report.address() + ".");
        openIt.onInvoke(() -> open.accept(report.address()));

        HBox row = Ui.row(UiTokens.SPACE_3, text, tags, openIt);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("es-recon-row");
        row.setAccessibleText("Report on " + report.address()
                + (report.alias().isBlank() ? "" : ", which you called " + report.alias())
                + ". " + report.known() + " of " + NodeReport.total() + " findings"
                + (report.tags().isEmpty() ? "" : ", tagged " + String.join(", ", report.tags()))
                + ". Double-click the name to rename it.");
        return row;
    }

    /**
     * The name, and the editor it becomes on a double-click.
     *
     * <h2>⚠ Once renamed, the machine's ORIGINAL name shifts right and is marked</h2>
     *
     * A player's name replaces nothing. The name the world gave the machine is how it is referred to
     * everywhere else in the game, so it stays on the row as an <b>identifier</b> — labelled in words
     * rather than by position or colour alone, so it survives greyscale and a screen reader (§4.4).
     * Dropping it would leave the player's own name as the only handle on a row whose subject is
     * called something else in every other window.
     */
    private static Region nameField(
            GameSession session, NodeReport report, HBox line, Runnable repaint, boolean[] editing) {
        Label name = new Label(report.displayName());
        name.getStyleClass().addAll("es-mono", "es-recon-name");
        Cursors.shared().clickable(name);
        tip(name, "Double-click to rename. The machine's own name stays beside it.");

        name.setOnMouseClicked(event -> {
            if (event.getClickCount() < 2) {
                return;
            }
            event.consume();
            editing[0] = true;
            TextField field = new TextField(report.alias());
            field.setPromptText(report.displayName());
            field.getStyleClass().add("es-recon-rename");
            field.setPrefColumnCount(18);

            // ⚠ Committed at most ONCE. Enter fires the action AND drops focus, so without the guard
            // the same rename runs twice and the second repaints a list the first already rebuilt.
            boolean[] done = {false};
            java.util.function.Consumer<Boolean> finish = commit -> {
                if (done[0]) {
                    return;
                }
                done[0] = true;
                editing[0] = false;
                if (commit) {
                    session.nameNode(report.address(), field.getText());
                }
                repaint.run();
            };
            field.setOnAction(event2 -> finish.accept(true));
            // Clicking away applies, which is what rename-in-place does everywhere else.
            field.focusedProperty().addListener((observable, was, has) -> {
                if (!has) {
                    finish.accept(true);
                }
            });
            field.setOnKeyPressed(key -> {
                if (key.getCode() == KeyCode.ESCAPE) {
                    key.consume();
                    finish.accept(false);
                }
            });

            line.getChildren().setAll(field);
            field.requestFocus();
            field.selectAll();
        });

        // The machine's own name, once the player has given it another one.
        //
        // ⚠ Shown only when the machine HAS a name of its own. A world label is not guaranteed — a
        // machine a sweep found and nothing has identified has only an address — and falling back to
        // the address printed "identifier: 10.0.0.2" directly above the line that already says
        // 10.0.0.2. Two renderings of one fact, stacked, which reads as the panel repeating itself.
        String given = report.label();
        if (report.alias().isBlank() || given.isBlank() || given.equals(report.displayName())) {
            return name;
        }
        Label identifier = Ui.micro("identifier: " + given);
        identifier.getStyleClass().add("es-recon-identifier");
        HBox both = Ui.row(UiTokens.SPACE_2, name, identifier);
        both.setAlignment(Pos.CENTER_LEFT);
        return both;
    }

    /**
     * The {@code +} beside the address, and the tag editor it opens.
     *
     * <h2>⚠ Existing tags are OFFERED, not merely typeable</h2>
     *
     * A tag is worth something only when the same string is used twice, and a bare text box invites
     * {@code rich}, {@code Rich} and {@code wealthy} across three machines the player meant to group.
     * Every tag already in use is offered as a one-click choice with the field still there for a new
     * one — so consistency is the path of least effort rather than something to remember. The rules
     * lowercase and de-duplicate as a backstop, but a backstop is not a design.
     */
    private static Region addTag(
            GameSession session, NodeReport report, HBox line, Runnable repaint, boolean[] editing) {
        Label plus = new Label("+");
        plus.getStyleClass().addAll("es-mono", "es-recon-add");
        plus.setAccessibleText("Add a tag to " + report.address() + ".");
        Cursors.shared().clickable(plus);
        tip(plus, "Add a tag. Tags you already use are offered; anything else is a new one.");

        plus.setOnMouseClicked(event -> {
            event.consume();
            editing[0] = true;
            TextField field = new TextField();
            field.setPromptText("new tag");
            field.getStyleClass().add("es-recon-tagfield");
            field.setPrefColumnCount(10);

            boolean[] done = {false};
            java.util.function.Consumer<String> add = tag -> {
                if (done[0]) {
                    return;
                }
                done[0] = true;
                editing[0] = false;
                if (tag != null && !tag.isBlank()) {
                    List<String> next = new ArrayList<>(report.tags());
                    next.add(tag);
                    session.tagNode(report.address(), next);
                }
                repaint.run();
            };
            field.setOnAction(event2 -> add.accept(field.getText()));
            field.focusedProperty().addListener((observable, was, has) -> {
                if (!has) {
                    add.accept(field.getText());
                }
            });
            field.setOnKeyPressed(key -> {
                if (key.getCode() == KeyCode.ESCAPE) {
                    key.consume();
                    add.accept(null);
                }
            });

            line.getChildren().setAll(Ui.micro(report.address()), field);
            field.requestFocus();

            // Everything already in use, minus what this machine already carries.
            Set<String> known = new TreeSet<>();
            session.nodeReports().forEach(other -> known.addAll(other.tags()));
            known.removeAll(report.tags());
            if (known.isEmpty()) {
                return;
            }
            javafx.scene.control.ContextMenu offer = new javafx.scene.control.ContextMenu();
            for (String tag : known) {
                javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(tag);
                item.setOnAction(event2 -> add.accept(tag));
                offer.getItems().add(item);
            }
            javafx.stage.Window window =
                    line.getScene() == null ? null : line.getScene().getWindow();
            var where = field.localToScreen(0, 0);
            if (window != null && where != null) {
                offer.setAutoHide(true);
                offer.show(window, where.getX(), where.getY() + 22);
                // ⚠ Focus goes back to the FIELD. The menu is an offer, not a modal — a picker that
                // kept focus would make typing a new tag impossible without dismissing it first,
                // which is the common case.
                field.requestFocus();
            }
        });
        return plus;
    }

    /** One tag, with the {@code x} that removes it. */
    private static Region tagPill(GameSession session, NodeReport report, String tag, Runnable repaint) {
        Label text = new Label("#" + tag);
        text.getStyleClass().add("es-recon-tagtext");
        // ⚠ ASCII x, not a multiplication sign. GlyphCoverageTest fails the build on anything outside
        // the two bundled faces, and a fallback font breaks the character-cell metrics.
        Label remove = new Label("x");
        remove.getStyleClass().add("es-recon-tagx");
        remove.setAccessibleText("Remove the tag " + tag + " from " + report.address() + ".");
        Cursors.shared().clickable(remove);
        remove.setOnMouseClicked(event -> {
            event.consume();
            List<String> next = new ArrayList<>(report.tags());
            next.remove(tag);
            session.tagNode(report.address(), next);
            repaint.run();
        });

        HBox pill = Ui.row(UiTokens.SPACE_1, text, remove);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.getStyleClass().addAll("es-pill", "es-recon-pill");
        return pill;
    }

    /** A wrapped tooltip — three controls on this panel want one. */
    private static void tip(javafx.scene.control.Control on, String text) {
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        javafx.scene.control.Tooltip.install(on, tooltip);
    }
}
