package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * A horizontal strip that stays one row while it fits and wraps into more when it does not.
 *
 * <h2>Why this is not an HBox and not a FlowPane</h2>
 *
 * The top status strip needs both behaviours and neither container has both:
 *
 * <ul>
 *   <li>An <b>HBox</b> gives the flex spacer that right-aligns the balance and the clock, which is
 *       what {@code ui-design-language.md} §3 specifies. It cannot wrap — past its preferred width
 *       it squeezes and then clips its children, so the readouts on the right silently disappear.
 *       At 200% UI scale in a 1280px window the deck is 640 logical pixels wide and most of the
 *       strip was gone.
 *   <li>A <b>FlowPane</b> wraps, and has no concept of a growing child — so at ordinary widths every
 *       cell packs to the left and the right-hand group stops being right-aligned. That is a
 *       regression at the width nearly everyone plays at, to fix one that only appears when narrow.
 * </ul>
 *
 * <p>So this does the HBox thing when the content fits and the FlowPane thing when it does not.
 * <b>At any width where the strip fitted before, the layout is byte-for-byte what the HBox produced</b>
 * — the spacer absorbs the slack and the trailing cells sit on the right edge.
 *
 * <h2>⚠ The pinned child never wraps</h2>
 *
 * The window controls are the only way to minimise, maximise or close an undecorated Stage. If they
 * flowed with everything else they would move to the second row — or the third — as the window
 * narrowed, which is the one control that must be in the same place every time. {@link #setPinned}
 * holds a child at the top-right corner and takes its width out of the first row's budget.
 */
public final class WrapStrip extends Region {

    private final List<Node> flow = new ArrayList<>();
    private Node spacer;
    private Node pinned;

    /** Adds a child that participates in the flow and may wrap. */
    public void add(Node node) {
        flow.add(node);
        getChildren().add(node);
    }

    /**
     * Marks which child absorbs slack on a single row, and collapses to nothing when wrapped.
     *
     * <p>Collapsing it is the point: a spacer that kept growing across a wrapped layout would push
     * the trailing cells onto a row of their own and leave a band of empty strip above them.
     *
     * <p>⚠ This <b>only records the reference</b> — the spacer is added like any other child, with
     * {@link #add}, because where it sits in the flow is what decides which cells end up on the
     * right. Adding it here as well produced {@code IllegalArgumentException: duplicate children
     * added}, which is JavaFX refusing the same node twice in one parent.
     */
    public void setSpacer(Node node) {
        this.spacer = node;
    }

    /** The child held at the top-right corner, out of the flow. See the class comment. */
    public void setPinned(Node node) {
        this.pinned = node;
        getChildren().add(node);
    }

    private double widthOf(Node node) {
        return node.prefWidth(-1);
    }

    private double rowHeight() {
        double tallest = 0;
        for (Node node : flow) {
            tallest = Math.max(tallest, node.prefHeight(-1));
        }
        if (pinned != null) {
            tallest = Math.max(tallest, pinned.prefHeight(-1));
        }
        return tallest;
    }

    @Override
    protected double computePrefHeight(double width) {
        double usable = (width < 0 ? getWidth() : width)
                - getInsets().getLeft() - getInsets().getRight()
                - (pinned == null ? 0 : widthOf(pinned));
        double row = rowHeight();
        return getInsets().getTop() + getInsets().getBottom() + row * rowsNeeded(usable);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /** How many rows the flow children need at this usable width. Always at least one. */
    private int rowsNeeded(double usable) {
        double total = 0;
        for (Node node : flow) {
            total += widthOf(node);
        }
        if (usable <= 0 || total <= usable) {
            return 1;
        }
        int rows = 1;
        double used = 0;
        for (Node node : flow) {
            double w = widthOf(node);
            // ⚠ `used > 0` guards the pathological case: a single child wider than the whole strip
            // would otherwise start a new row, find it still does not fit, and loop forever.
            if (used > 0 && used + w > usable) {
                rows++;
                used = 0;
            }
            used += w;
        }
        return rows;
    }

    @Override
    protected void layoutChildren() {
        double left = getInsets().getLeft();
        double top = getInsets().getTop();
        double full = getWidth() - left - getInsets().getRight();
        double pinnedWidth = pinned == null ? 0 : widthOf(pinned);
        double row = rowHeight();

        if (pinned != null) {
            pinned.resizeRelocate(left + full - pinnedWidth, top, pinnedWidth, row);
        }

        // ⚠ The pinned width is reserved on EVERY row, not just the first. Rows below it could use
        // the full width, and doing so would be a hundred pixels better — but then rowsNeeded() and
        // layoutChildren() would be computing against different budgets, and a disagreement there
        // clips the last row instead of merely wasting space. Consistency wins.
        double usable = full - pinnedWidth;
        double total = 0;
        for (Node node : flow) {
            total += widthOf(node);
        }

        if (total <= usable) {
            // The HBox case, reproduced exactly: one row, and the spacer eats the difference so the
            // trailing cells finish flush against the pinned controls.
            double x = left;
            double slack = usable - total;
            for (Node node : flow) {
                if (node == spacer) {
                    double w = widthOf(node) + slack;
                    node.setVisible(true);
                    node.resizeRelocate(x, top, w, row);
                    x += w;
                    continue;
                }
                double w = widthOf(node);
                node.resizeRelocate(x, top, w, row);
                x += w;
            }
            return;
        }

        // Wrapped. The spacer is taken out of the layout entirely rather than given zero width — a
        // zero-width spacer still paints its 1px right border, which reads as an extra cell divider
        // sitting in the middle of a row for no reason.
        double x = left;
        double y = top;
        for (Node node : flow) {
            if (node == spacer) {
                node.setVisible(false);
                node.resizeRelocate(x, y, 0, 0);
                continue;
            }
            node.setVisible(true);
            double w = Math.min(widthOf(node), usable);
            if (x > left && x + w > left + usable) {
                x = left;
                y += row;
            }
            node.resizeRelocate(x, y, w, row);
            x += w;
        }
    }
}
