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

    /**
     * Which end the pinned child sits at.
     *
     * <p>⚠ Exists for exactly one reason: <b>macOS puts window controls on the left</b> and every
     * other platform puts them on the right. This deck draws its own controls (§0), so it also
     * inherits the obligation to put them where the player's OS would — a close button on the wrong
     * side is the kind of thing that is not merely unfamiliar but actively mis-clicked, because the
     * hand goes where it has gone ten thousand times before.
     */
    private boolean pinnedLeft;

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
        setPinned(node, false);
    }

    /** @param onLeft true to hold it at the top-LEFT instead — macOS's side. See {@link #pinnedLeft}. */
    public void setPinned(Node node, boolean onLeft) {
        this.pinned = node;
        this.pinnedLeft = onLeft;
        getChildren().add(node);
    }

    private double widthOf(Node node) {
        return node.prefWidth(-1);
    }

    /**
     * Whether a child takes part in the layout at all.
     *
     * <h2>⚠ An empty cell is not a narrow cell — it is not a cell</h2>
     *
     * A cell carries {@code -fx-padding: 7 14 7 14} and a 1px divider, so a cell whose label has no
     * text is still <b>29 pixels wide and still draws a rule</b>. The strip's refusal cell is empty
     * almost always, and those 29 pixels were charged against the width budget on every layout pass.
     *
     * <p>Measured: at a 1200px deck the strip needed 1113 and had 1104 — it wrapped by <b>nine
     * pixels</b>, doubling the height of the chrome and dropping the clock onto a row of its own. The
     * dead cell was three times the overflow. So this asks {@code isManaged()}, JavaFX's own word for
     * "in the layout", and a caller collapses a cell by unmanaging it.
     *
     * <p>⚠ Deliberately <b>not</b> {@code isVisible()}. {@link #layoutChildren} sets the spacer
     * invisible when it wraps; keying off visibility would change the next pass's measurement, which
     * would change whether it wraps, which would flip the visibility back — a strip that oscillates
     * between one row and two forever.
     */
    private boolean counts(Node node) {
        return node.isManaged();
    }

    private double rowHeight() {
        double tallest = 0;
        for (Node node : flow) {
            if (counts(node)) {
                tallest = Math.max(tallest, node.prefHeight(-1));
            }
        }
        if (pinned != null) {
            tallest = Math.max(tallest, pinned.prefHeight(-1));
        }
        return tallest;
    }

    /** The width every participating flow child wants, which is what decides whether it wraps. */
    private double flowWidth() {
        double total = 0;
        for (Node node : flow) {
            if (counts(node)) {
                total += widthOf(node);
            }
        }
        return total;
    }

    @Override
    protected double computePrefHeight(double width) {
        double usable = (width < 0 ? getWidth() : width)
                - getInsets().getLeft()
                - getInsets().getRight()
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
        if (usable <= 0 || flowWidth() <= usable) {
            return 1;
        }
        int rows = 1;
        double used = 0;
        for (Node node : flow) {
            if (!counts(node)) {
                continue;
            }
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
            pinned.resizeRelocate(pinnedLeft ? left : left + full - pinnedWidth, top, pinnedWidth, row);
        }
        // Everything else starts after the controls when they are on the left, so the flow never
        // runs underneath them.
        double flowLeft = pinnedLeft ? left + pinnedWidth : left;

        // ⚠ The pinned width is reserved on EVERY row, not just the first. Rows below it could use
        // the full width, and doing so would be a hundred pixels better — but then rowsNeeded() and
        // layoutChildren() would be computing against different budgets, and a disagreement there
        // clips the last row instead of merely wasting space. Consistency wins.
        double usable = full - pinnedWidth;
        double total = flowWidth();

        if (total <= usable) {
            // The HBox case, reproduced exactly: one row, and the spacer eats the difference so the
            // trailing cells finish flush against the pinned controls.
            double x = flowLeft;
            double slack = usable - total;
            for (Node node : flow) {
                if (!counts(node)) {
                    continue;
                }
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
        double x = flowLeft;
        double y = top;
        for (Node node : flow) {
            if (!counts(node)) {
                continue;
            }
            if (node == spacer) {
                node.setVisible(false);
                node.resizeRelocate(x, y, 0, 0);
                continue;
            }
            node.setVisible(true);
            double w = Math.min(widthOf(node), usable);
            if (x > flowLeft && x + w > flowLeft + usable) {
                x = flowLeft;
                y += row;
            }
            node.resizeRelocate(x, y, w, row);
            x += w;
        }
    }
}
