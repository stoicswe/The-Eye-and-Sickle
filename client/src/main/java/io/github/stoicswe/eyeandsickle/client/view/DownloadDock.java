package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.DownloadOrder;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * What is coming down the wire, docked at the foot of the store.
 *
 * <h2>Why it is an overlay and not a section of the page</h2>
 *
 * A download starts because the player pressed Buy, and the thing they pressed is halfway down a
 * scrolling shelf. A panel in the page flow would appear <em>somewhere</em> — above the fold if
 * they had scrolled up, below it if they had not — so the one confirmation that the purchase
 * worked would be invisible about half the time. Docked to the window it is always in the same
 * place, which is the entire job.
 *
 * <h2>⚠ It occupies NO layout space, and that is a rule this repo has already paid for</h2>
 *
 * {@code CLAUDE.md}: nothing transient may take space in a strip. The balance delta learned this
 * the hard way — a label that got wider while it showed pushed the top strip past its width budget
 * and wrapped the chrome onto two rows. This is the same shape of problem one storey down, so the
 * dock lives in a {@code StackPane} above the page rather than in it, and the shelf does not move
 * when a download starts.
 *
 * <h2>⚠ Two clocks, and mixing them up freezes the bar</h2>
 *
 * The queue's <em>membership</em> changes when the player buys, pauses or reorders — that is
 * {@code onChange}. Its <em>progress</em> changes because wall time passed, and nothing about the
 * save changes as bytes move, so {@code onChange} would leave the bar exactly where it was when
 * the panel opened. That is the file manager's transfer-bar bug verbatim, so the split here is the
 * same: rebuild on data, repaint the figures on {@link Pulse#every}.
 */
public final class DownloadDock {

    private DownloadDock() {}

    /**
     * Cells in the dock's meter.
     *
     * <p>Discrete, per §4 — a continuous fill implies a precision the model does not have, and
     * progress here is computed from two timestamps. Fewer than the file manager's twenty because
     * this bar is a status light rather than the subject of its panel.
     */
    private static final int CELLS = 16;

    /**
     * Builds the dock.
     *
     * @param session where the queue comes from
     * @return a node to lay over the store, which hides itself when nothing is owed
     */
    public static Region create(GameSession session) {
        VBox dock = new VBox(UiTokens.SPACE_2);
        dock.getStyleClass().add("es-market-dock");
        // ⚠ Bottom-CENTRE of the StackPane it is put into, and shrink-wrapped in both directions. A
        // StackPane stretches a resizable child to fill it, so without the maximums this would be a
        // full-window pane over the shelf, swallowing every click meant for the page beneath.
        StackPaneAlignment.bottomCentre(dock);

        // ⚠ boolean[] rather than a field: this view is static factories, and two open MARKET
        // windows must not share an expansion state.
        boolean[] expanded = {false};

        Label caption = Ui.small("");
        HBox cells = new HBox(1);
        cells.setAlignment(Pos.CENTER_LEFT);
        Label figures = Ui.micro("");
        Label hint = Ui.micro("");

        Button toggle = new Button("↑");
        toggle.getStyleClass().add("es-market-dock-toggle");
        toggle.setAccessibleText("Show the download queue");

        HBox summary = Ui.row(UiTokens.SPACE_3, caption, cells, figures, Ui.spacer(), hint, toggle);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.getStyleClass().add("es-market-dock-summary");

        VBox body = new VBox(UiTokens.SPACE_2);
        Runnable[] repaint = new Runnable[1];

        repaint[0] = () -> {
            List<DownloadOrder> orders = session.downloads();
            boolean any = !orders.isEmpty();
            dock.setVisible(any);
            dock.setManaged(any);
            if (!any) {
                // ⚠ Collapse when the queue empties. A dock left expanded over an empty store would
                // re-open expanded on the next purchase, which is a panel the player never asked
                // for covering the shelf they are shopping on.
                expanded[0] = false;
                body.getChildren().clear();
                return;
            }
            // ⚠ The ACTIVE order, not the head. They are the same until somebody pauses the first
            // one, and then the head is the paused one — a summary keyed on position would report a
            // frozen bar as the current download forever.
            DownloadOrder shown =
                    orders.stream().filter(DownloadOrder::active).findFirst().orElse(orders.get(0));
            caption.setText(shown.label());
            paintCells(cells, shown.progress());
            figures.setText(shown.active()
                    ? String.format(
                            Locale.ROOT,
                            "%d%%  ·  %ds left",
                            Math.round(shown.progress() * 100),
                            shown.remaining().toSeconds())
                    : "all downloads held");
            int others = orders.size() - 1;
            hint.setText(others > 0 ? others + " more queued" : "");
            summary.setAccessibleText("Downloads: " + shown.label() + ", "
                    + Math.round(shown.progress() * 100) + " percent"
                    + (others > 0 ? ", " + others + " more queued" : ""));
            body.getChildren().setAll(expanded[0] ? expandedRows(session, orders, repaint) : List.of());
        };

        toggle.setOnAction(event -> {
            expanded[0] = !expanded[0];
            toggle.setText(expanded[0] ? "↓" : "↑");
            toggle.setAccessibleText(expanded[0] ? "Hide the download queue" : "Show the download queue");
            repaint[0].run();
        });

        dock.getChildren().addAll(summary, body);
        // ⚠ PAINTED ONCE HERE, and this is the inverse of the carousel's trap rather than the same
        // one. Pulse.every does NOT invoke its action immediately — only Pulse.animate does, so a
        // widget that would otherwise be blank until its first tick is never blank. This one is on
        // `every` because it is data, so without this call the dock opens as an empty box and stays
        // one for half a second — and in a synchronous render, which fires no ticks at all, forever.
        repaint[0].run();
        session.onChange(s -> repaint[0].run());

        // ⚠ Pulse.every — DATA. The figures are wall-clock derived and the save does not change as
        // bytes move, so this is the only thing that advances the bar. Suppressing it under Reduce
        // motion would leave a download that never appears to progress.
        AutoCloseable clock = Pulse.shared().every(500, repaint[0]);
        Views.releaseOnDetach(dock, clock);
        return dock;
    }

    /** The queue, one row each, with the controls that make it a queue rather than a list. */
    private static List<Region> expandedRows(GameSession session, List<DownloadOrder> orders, Runnable[] repaint) {
        java.util.List<Region> rows = new java.util.ArrayList<>();
        Region rule = new Region();
        rule.getStyleClass().add("es-market-dock-rule");
        rows.add(rule);
        for (int i = 0; i < orders.size(); i++) {
            DownloadOrder order = orders.get(i);
            Label position = Ui.micro(String.valueOf(i + 1));
            position.setMinWidth(16);

            Label name = Ui.small(order.label());
            name.setMinWidth(200);

            // ⚠ THREE states, not two. "waiting" and "paused" look identical if only running is
            // distinguished, and the difference between them is entirely the player's own doing —
            // collapsing them would hide the effect of the control they just pressed.
            Label state = Ui.micro(order.active()
                    ? Math.round(order.progress() * 100) + "%"
                    : order.paused() ? "held" : "waiting");
            state.getStyleClass().add(order.paused() ? "es-market-dock-held" : "es-market-dock-state");
            state.setMinWidth(56);

            Button hold = new Button(order.paused() ? "Resume" : "Hold");
            hold.getStyleClass().add("es-market-dock-action");
            hold.setOnAction(event -> {
                if (order.paused()) {
                    session.resumeDownload(order.orderId());
                } else {
                    session.pauseDownload(order.orderId());
                }
                repaint[0].run();
            });

            Button up = arrow("↑", "Move " + order.label() + " up the queue");
            Button down = arrow("↓", "Move " + order.label() + " down the queue");
            // ⚠ Disabled at the ends rather than silently doing nothing. A control that visibly
            // fails to act reads as broken; one that is greyed reads as "you are already there".
            up.setDisable(i == 0);
            down.setDisable(i == orders.size() - 1);
            up.setOnAction(event -> {
                session.moveDownload(order.orderId(), -1);
                repaint[0].run();
            });
            down.setOnAction(event -> {
                session.moveDownload(order.orderId(), 1);
                repaint[0].run();
            });

            HBox row = Ui.row(UiTokens.SPACE_2, position, name, state, Ui.spacer(), hold, up, down);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("es-market-dock-row");
            rows.add(row);

            if (order.bundle() && !order.memberNames().isEmpty()) {
                Label contents = Ui.micro("contains " + String.join(", ", order.memberNames()));
                contents.getStyleClass().add("es-market-dock-contents");
                rows.add(contents);
            }
        }
        return rows;
    }

    private static Button arrow(String glyph, String description) {
        Button button = new Button(glyph);
        button.getStyleClass().add("es-market-dock-arrow");
        button.setAccessibleText(description);
        button.setTooltip(new javafx.scene.control.Tooltip(description));
        return button;
    }

    private static void paintCells(HBox cells, double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * CELLS);
        if (cells.getChildren().size() != CELLS) {
            cells.getChildren().clear();
            for (int i = 0; i < CELLS; i++) {
                cells.getChildren()
                        .add(Ui.block(UiTokens.METER_BAR_WIDTH, UiTokens.METER_BAR_HEIGHT, "es-files-cell-off"));
            }
        }
        for (int i = 0; i < CELLS; i++) {
            Region cell = (Region) cells.getChildren().get(i);
            // ⚠ setAll, not add. These cells are reused across repaints, and adding leaves a stale
            // `-on` class behind so a bar that had reached 80% never appears to go back down.
            cell.getStyleClass().setAll(i < filled ? "es-files-cell-on" : "es-files-cell-off");
        }
    }

    /**
     * Where the dock sits in the {@code StackPane} it is laid over.
     *
     * <p>⚠ A {@code StackPane} RESIZES a resizable child to fill it — alignment only decides where
     * the child sits once it has a size it does not fill. Without the maximums the dock would be a
     * transparent full-window pane over the shelf, eating every click meant for the page beneath
     * it, with nothing on screen to suggest why the store had stopped responding.
     */
    private static final class StackPaneAlignment {

        private StackPaneAlignment() {}

        static void bottomCentre(Region node) {
            javafx.scene.layout.StackPane.setAlignment(node, Pos.BOTTOM_CENTER);
            javafx.scene.layout.StackPane.setMargin(node, new javafx.geometry.Insets(UiTokens.SPACE_5));
            node.setMaxWidth(Region.USE_PREF_SIZE);
            node.setMaxHeight(Region.USE_PREF_SIZE);
            // ⚠ Unmanaged is NOT wanted here, unlike the desk's windows. A StackPane child that is
            // unmanaged is never laid out at all, so it would keep a size of zero and never appear.
            node.setPickOnBounds(false);
        }
    }
}
