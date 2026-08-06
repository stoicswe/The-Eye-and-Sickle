package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * The envelope on the messenger's send button.
 *
 * <h2>⚠ DRAWN, NEVER A GLYPH — and this is not a preference</h2>
 *
 * {@code U+2709} is in neither bundled face, and {@code GlyphCoverageTest} scans <b>source</b> for
 * literals and fails the build on an uncovered one. It has already rejected {@code U+26A0} in this
 * very window and four block elements in the file manager. A glyph here would not degrade — it would
 * render as somebody else's font at somebody else's advance width, differently on each platform.
 *
 * <h2>⚠ ONE MARK FOR ONE CONTROL, which is what keeps §9's icon-set ban intact</h2>
 *
 * {@code docs/design/ui-design-language.md} §9 makes <b>icon sets</b> build-blocking. What is banned
 * is a <em>vocabulary</em> — a general-purpose tray of symbols that ends up standing in for words all
 * over the interface. This is a single shape for a single button, on the same footing as
 * {@code SecurityMark}'s shield and {@code SectionMark}'s detective. The day something wants a second
 * one, that is the moment to ask whether a set is being assembled, not to add it here.
 *
 * <h2>⚠ STROKED, NOT FILLED, and drawn AT SIZE rather than scaled</h2>
 *
 * An outline is the deck's own idiom — §9 wants hairlines, not filled shapes. And the geometry is
 * computed from the requested size instead of being authored in a box and scaled down, because a
 * scale transform scales the <b>stroke</b> with it: at the ~13px this renders at, a 1px hairline
 * authored in a 24px box arrives at roughly half a pixel and greys out into a smudge. Same trap the
 * ring wallpaper records from the other direction, where scaling offsets without their stroke widths
 * banded the glow.
 *
 * <h2>⚠ A MARK ALONE CANNOT LABEL A BUTTON</h2>
 *
 * {@code SocialMark}'s note says a shape sits outside {@code ContrastTest} and that this is fine
 * because "the words carry it, the mark reinforces". <b>That argument does not hold here</b> — there
 * are no words; the mark is the whole control. So two things are required of the caller and neither
 * is optional: an {@code accessibleText}, because a screen reader cannot see an envelope, and a
 * tooltip, because neither can a person who has not met this icon before. The stroke also has to be a
 * token {@code ContrastTest} already measures against the button's ground, rather than a literal.
 */
public final class MailMark {

    private MailMark() {}

    /** Width as a fraction of the requested size, so the envelope is a letter rather than a square. */
    private static final double ASPECT = 0.72;

    /**
     * An envelope {@code size} wide, in a frame that reserves exactly that much room.
     *
     * <p>⚠ Inset by half the stroke width on every side. A path drawn on the frame's own edge is
     * clipped along its outer half, which reads as a lighter line on two sides and looks like a
     * rendering fault rather than a thin box.
     */
    public static Region node(double size, double strokeWidth) {
        double half = strokeWidth / 2;
        double w = size;
        double h = size * ASPECT;

        double x0 = half;
        double x1 = w - half;
        double y0 = half;
        double y1 = h - half;
        // Where the flap meets in the middle. Below halfway, because a real envelope's flap covers
        // rather more than half the face — at the exact centre it reads as a box with an X in it.
        double apexY = y0 + (y1 - y0) * 0.62;
        double centreX = w / 2;

        SVGPath mark = new SVGPath();
        mark.setContent(String.format(
                java.util.Locale.ROOT,
                // The body, then the flap as a second subpath. Two subpaths on one node so the whole
                // mark takes one stroke colour and one style class.
                "M %.2f %.2f L %.2f %.2f L %.2f %.2f L %.2f %.2f Z M %.2f %.2f L %.2f %.2f L %.2f %.2f",
                x0, y0, x1, y0, x1, y1, x0, y1,
                x0, y0, centreX, apexY, x1, y0));
        mark.setStrokeWidth(strokeWidth);
        mark.getStyleClass().add("es-mail-mark");

        StackPane frame = new StackPane(mark);
        frame.setMinSize(w, h);
        frame.setPrefSize(w, h);
        frame.setMaxSize(w, h);
        // ⚠ A mark is decoration to a reader; the BUTTON carries the label. Left announceable, this
        // is an unlabelled graphic read out before the control's own name.
        frame.setAccessibleText("");
        return frame;
    }
}
