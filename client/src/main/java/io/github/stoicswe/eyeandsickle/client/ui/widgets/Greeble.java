package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.util.Random;
import javafx.scene.control.Label;

/**
 * Machine texture. Unreadable by design.
 *
 * <h2>This is not decoration to be cut in review</h2>
 *
 * {@code docs/design/ui-design-language.md} §4 says so directly, and §9 lists "removing greeble
 * because it doesn't do anything" as build-blocking. The argument is worth restating because it will
 * be questioned again: greeble is "the single largest difference between this look and a dashboard",
 * and the budget is roughly 10–15% of pixels carrying no meaning. A screen where every pixel is
 * informative reads as a well-designed admin panel, which is exactly the failure §1 names.
 *
 * <p>It has to stay genuinely unreadable. The moment a fragment resolves into a word, players start
 * reading it, and a decorative string that is being read is a string that is lying to them.
 *
 * <h2>The one thing it does mean</h2>
 *
 * {@link #setAgitation(double)} thickens the alignment marks as personal heat rises. Nothing becomes
 * legible and no figure is encoded — the texture simply gets busier as the Eye gets closer. It is
 * the client's quietest signal, and the only one that works peripherally: a player who has stopped
 * reading the heat chip still notices the machine getting noisier around them. Because it is driven
 * by real state rather than a timer, it cannot reassure at the wrong moment.
 */
public final class Greeble extends Label {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    /**
     * ⚠ Every glyph in this class is verified present in <b>IBM Plex Mono</b>, and the strip is
     * pinned to that face in {@code theme.css}.
     *
     * <p>It previously used {@code ▮ ▯ ⋮}, none of which is in either bundled TTF, and {@code ▚},
     * which is in Plex but not Martian — while the strip was styled with Martian. So four of the
     * five fragment kinds were being drawn by whatever the host OS substituted. §2.2 bundles the
     * fonts precisely so that cannot happen, and the failure was worse than cosmetic: substituted
     * glyphs carry their own advance widths, so the clipped length of the strip differed per
     * platform too.
     */
    private static final String BLOCKS = "▐▌▐▌";

    private static final String DOTS = "·····";

    /** Alignment marks. Meaningless, and more of them when the rig is being watched. */
    private static final String[] MARKS = {"//", "//", "╞╞", "▚▚"};

    private final Random random = new Random();

    /**
     * How many characters the strip generates.
     *
     * <p>⚠ No longer final. {@link #filling()} makes it follow the widget's real width, because a
     * fixed count leaves a wide slot visibly short of its own edge — the strip is texture, and
     * texture that stops two-thirds of the way across reads as content that ran out.
     */
    private int length;

    /** Whether the count follows the width. Off by default: a fixed-width slot wants a fixed count. */
    private boolean filling;

    /** One character's advance in the face the stylesheet applied. Measured, never assumed. */
    private double cellWidth;

    private double agitation;
    private AutoCloseable subscription;

    public Greeble(int length) {
        this.length = length;
        getStyleClass().add("es-greeble");
        setMinWidth(0);
        // Clipped at the edge rather than wrapped or ellipsised (§4). An ellipsis is punctuation,
        // and punctuation implies something was omitted from a message — there is no message.
        setWrapText(false);
        setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        subscription = Pulse.shared().animate(UiTokens.GREEBLE_MS, this::repaint);
    }

    /**
     * Makes the strip generate as many characters as its width can show.
     *
     * <h2>⚠ Opt-in, and deliberately not the default</h2>
     *
     * Several greebles sit in slots whose width is decided by something else — the command strip's,
     * for one — and a texture that grew to fill those would change a layout that is already right.
     * What this is for is a strip spanning a panel, where a fixed count stops short of the edge and
     * the eye reads the gap as a fault rather than as texture.
     *
     * <p>The advance is <b>measured</b> off the applied font, exactly as {@code Substrate} and
     * {@code HexStream} do: the face and its size live in {@code theme.css}, so a hard-coded width
     * in Java is a second source of truth that drifts the first time the stylesheet changes — and
     * the symptom is a strip that no longer reaches its edge, which fails no build.
     */
    public Greeble filling() {
        filling = true;
        setMaxWidth(Double.MAX_VALUE);
        widthProperty().addListener((obs, was, now) -> refit());
        refit();
        return this;
    }

    private void refit() {
        double available = getWidth();
        if (!filling || available <= 0) {
            return;
        }
        if (cellWidth <= 0) {
            applyCss();
            javafx.scene.text.Text probe = new javafx.scene.text.Text("0");
            probe.setFont(getFont());
            cellWidth = probe.getLayoutBounds().getWidth();
        }
        if (cellWidth <= 0) {
            return;
        }
        int wanted = Math.max(8, (int) Math.floor(available / cellWidth));
        if (wanted != length) {
            length = wanted;
            repaint();
        }
    }

    /** A greeble strip with the hairline and padding that separates it from the panel body. */
    public static Greeble strip(int length) {
        Greeble greeble = new Greeble(length);
        greeble.getStyleClass().add("es-greeble-strip");
        greeble.setMaxWidth(Double.MAX_VALUE);
        return greeble;
    }

    /**
     * How agitated the texture is, 0–1. Drive it from personal heat, nothing else.
     *
     * <p>Takes effect on the next repaint rather than immediately: greeble that reacted the instant
     * a value changed would be a readout, and it must not be one.
     */
    public void setAgitation(double agitation) {
        this.agitation = Math.max(0, Math.min(1, agitation));
    }

    private void repaint() {
        StringBuilder out = new StringBuilder(length + 8);
        int guard = 0;
        // ⚠ The guard scales with the length. It was a flat 80, which was fine while every
        // strip was ~82 characters and silently truncated a filled one on a wide panel —
        // the loop ran out of iterations before the string ran out of room.
        while (out.length() < length && guard++ < length + 20) {
            double roll = random.nextDouble();
            // Agitation steals probability from the quiet fragments and gives it to the marks. The
            // distribution below is the reference implementation's, shifted by that one term.
            double markThreshold = 0.92 - agitation * 0.22;
            if (roll < 0.40) {
                for (int i = 0; i < 4; i++) {
                    out.append(HEX[random.nextInt(16)]);
                }
            } else if (roll < 0.62) {
                out.append(BLOCKS, 0, 2 + random.nextInt(3));
            } else if (roll < 0.80) {
                out.append(DOTS, 0, 1 + random.nextInt(4));
            } else if (roll < markThreshold) {
                out.append(1000 + random.nextInt(8999));
            } else {
                out.append(MARKS[random.nextInt(agitation > 0.5 ? MARKS.length : 2)]);
            }
            out.append(' ');
        }
        setText(out.substring(0, Math.min(out.length(), length)));
    }

    public void dispose() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            subscription = null;
        }
    }
}
