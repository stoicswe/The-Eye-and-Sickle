package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * The network marks this client draws — Bluesky's butterfly and YouTube's play plate.
 *
 * <h2>⚠ THESE ARE NOT THE OFFICIAL LOGO FILES AND MUST NOT BE MISTAKEN FOR THEM</h2>
 *
 * Both paths were authored in this repository, drawn to each mark's silhouette so a reader knows what
 * kind of handle or service follows — in a client that <b>bundles no third-party artwork and
 * downloads nothing at run time</b>. If the official assets are ever wanted they replace these two
 * constants and nothing else about the code changes.
 *
 * <h2>⚠ Extracted from {@code view/Credits} on 2026-08-06, and duplicating it was the alternative</h2>
 *
 * The paths lived in a private enum inside {@code Credits}, whose own comment promises that swapping
 * in the official assets is a two-constant edit. The moment a second copy existed anywhere that
 * promise was false — and a drifted copy of somebody else's mark is a worse failure than a missing
 * one, because nobody would notice. So there is one definition and both callers use it.
 *
 * <h2>⚠ §9's rounded-corner ban is not in play</h2>
 *
 * That rule governs the interface's own geometry — panels, cells, meters. This is somebody else's
 * mark quoted inside it, drawn as a path rather than as a {@code -fx-background-radius} the contract
 * test could even see.
 */
public enum SocialMark {

    /** ⚠ Symmetric about x=12, so an edit to one half has to be mirrored in the other. */
    BLUESKY(
            "M12 7"
                    + "C10.5 4.2 6.5 1.5 3.8 2.2 C1.2 2.9 1.1 6.4 2.6 9.1 C3.6 10.9 5.2 12.2 6.9 12.9 "
                    + "C5.1 13.4 3.7 14.6 3.6 16.2 C3.5 18.6 6.1 20.4 8.6 19.6 "
                    + "C10.6 19.0 11.7 16.6 12 14.4 C12.3 16.6 13.4 19.0 15.4 19.6 "
                    + "C17.9 20.4 20.5 18.6 20.4 16.2 C20.3 14.6 18.9 13.4 17.1 12.9 "
                    + "C18.8 12.2 20.4 10.9 21.4 9.1 C22.9 6.4 22.8 2.9 20.2 2.2 C17.5 1.5 13.5 4.2 12 7 Z",
            FillRule.NON_ZERO,
            "Bluesky"),

    /**
     * ⚠ A rounded plate with the triangle as a <b>hole</b> rather than a second filled shape, which
     * is why it needs {@link FillRule#EVEN_ODD} — under the default non-zero rule the triangle fills
     * in and the mark becomes a solid lozenge.
     */
    YOUTUBE(
            "M5 5 H19 A4 4 0 0 1 23 9 V15 A4 4 0 0 1 19 19 H5 A4 4 0 0 1 1 15 V9 A4 4 0 0 1 5 5 Z"
                    + "M10 8.6 V15.4 L16 12 Z",
            FillRule.EVEN_ODD,
            "YouTube");

    /** The box both paths were drawn in. The scale factor derives from it, so it is never guessed. */
    private static final double BOX = 24;

    private final String path;
    private final FillRule fill;
    private final String spokenName;

    SocialMark(String path, FillRule fill, String spokenName) {
        this.path = path;
        this.fill = fill;
        this.spokenName = spokenName;
    }

    /** What a screen reader is told, since it cannot see a butterfly. */
    public String spokenName() {
        return spokenName;
    }

    /**
     * The mark at {@code size}, in a frame that reserves exactly that much room.
     *
     * <p>⚠ <b>A scale transform, not a resize</b>: an {@code SVGPath} has no width to set, and
     * letting a layout stretch it would distort a symmetric mark asymmetrically.
     *
     * <p>⚠ <b>And it must be wrapped.</b> {@code scaleX}/{@code scaleY} are applied <em>after</em>
     * layout, so the path still asks its row for the full authoring box — a label beside an unwrapped
     * mark sits a wing's width too far right. The {@code StackPane} reserves the drawn size instead
     * of the authored one.
     *
     * @param styleClass the fill class to apply, so a caller can be quieter or louder than another
     */
    public Region node(double size, String styleClass) {
        SVGPath mark = new SVGPath();
        mark.setContent(path);
        mark.setFillRule(fill);
        mark.getStyleClass().add(styleClass);
        double scale = size / BOX;
        mark.setScaleX(scale);
        mark.setScaleY(scale);

        StackPane frame = new StackPane(mark);
        frame.setMinSize(size, size);
        frame.setPrefSize(size, size);
        frame.setMaxSize(size, size);
        return frame;
    }
}
