package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The casing drawn around the deck — the cyberdeck's physical frame.
 *
 * <h2>⚠ This is a BEZEL, which §9 cut twice. Read this before extending it.</h2>
 *
 * {@code docs/design/ui-design-language.md} §9 listed bezel as build-blocking and §9.1 pointedly
 * kept it cut when four other screen artefacts were permitted: <em>"a drawn monitor casing, screen
 * curvature, or any frame implying the interface sits inside a pictured device. Still cut, without
 * exception."</em> It is permitted now on explicit direction, by the same mechanism and under the
 * same four conditions §9.1 established — which are what make the amendment safe rather than a hole
 * in the list:
 *
 * <ol>
 *   <li><b>Off by default and switchable off permanently.</b> {@link #OFF} is the default and the
 *       shipped look is unchanged for anyone who does not go looking. An effect the player switches
 *       on is a costume; an effect welded to the interface is a claim about fidelity the interface
 *       then has to keep making while they are trying to read a number.
 *   <li><b>It may not cost legibility.</b> This is the condition that shaped the implementation:
 *       the frame is drawn in a <b>margin</b>, and the deck is inset by exactly that margin. It
 *       never overlays content. A casing painted on top of the top strip would hide the compute
 *       readout, which is client pillar C2 and structural.
 *   <li><b>No blur, no glow.</b> §9's ban is unchanged and machine-checked. Every style here is
 *       flat fills and hairlines with hard edges — the same vocabulary the panels already use.
 *   <li><b>Nothing here moves.</b> A casing is a physical object; a physical object does not
 *       animate. That also means §5 and {@code prefers-reduced-motion} have nothing to suppress.
 * </ol>
 *
 * <p>⚠ <b>Vignette is still cut, and this does not reopen it.</b> §9's argument against it is not
 * about frames — it is that a vignette "dims real content by position rather than by meaning, and
 * the corners are where tiled windows go". A bezel in a margin dims nothing, because no content is
 * ever underneath it.
 *
 * <h2>⚠ JavaFX-free on purpose</h2>
 *
 * Same reason as {@link WallpaperMode}, {@link WindowSize} and {@code cursors/CursorSkin}: it can be
 * read, persisted and tested without a toolkit.
 */
public enum BezelStyle {

    /** No casing. The default, and the look the client has always shipped. */
    OFF("off", "Off", 0, "No casing. The deck runs to the edge of the window."),

    /**
     * A hairline double rule inset from the edge.
     *
     * <p>The quietest option and the one that reads as a machine rather than as a picture of one:
     * two rules and a gap is what an instrument face does at its edge.
     */
    HAIRLINE("hairline", "Hairline", 10, "Two thin rules inset from the edge. The quietest option."),

    /**
     * Corner brackets and edge ticks, with the middle of each run left open.
     *
     * <p>Reads as a targeting overlay rather than a casing. Open runs are the point: a frame that
     * closes on all four sides puts the interface inside a picture, which is what §9 objected to.
     */
    BRACKETS("brackets", "Corner brackets", 14,
            "Brackets at the corners and ticks along the edges. Open in the middle."),

    /**
     * The machine: a casing band with vents, fixings, a port block and a designator plate.
     *
     * <p>The most literal reading of "cyberdeck" available without breaking §9. Everything on it is
     * a flat hard-edged shape in a palette token — vent slots are rectangles, fixings are small
     * squares, the ports are a run of blocks down one side — so it reads as fabricated hardware
     * rather than as a drawn picture of hardware. Corners are notched at 45°, matching the panel
     * geometry §2.3 already specifies.
     *
     * <p>⚠ <b>Asymmetric on purpose.</b> Ports on one side and a designator on another is what
     * separates "a machine" from "a frame": real equipment has a front, and a perfectly symmetric
     * border reads as decoration around a picture — which is the thing §9 objected to about bezels.
     */
    CASING("casing", "Casing", 26, "Vents, fixings and a port block. The machine itself."),

    /**
     * The loom: cable runs with right-angle bends, junctions and terminated ends.
     *
     * <p>Wiring routed around the screen the way a harness is dressed inside a case — orthogonal
     * runs, a junction pad where two meet, and a terminator block at each end. No curves, because
     * §9 has no vocabulary for one and a dressed loom does not have any either.
     */
    LOOM("loom", "Cable loom", 30, "Cable runs, junctions and terminators, dressed around the screen."),

    /**
     * A ruled measure along all four edges.
     *
     * <p>Ticks at a fixed interval with heavier marks every fifth. Machine texture in the same
     * spirit as the greeble strips — it says the surface is an instrument, and it is the one style
     * that stays legible at the smallest margin.
     */
    RULE("rule", "Ruled edge", 12, "A tick scale along all four edges, heavier every fifth mark.");

    private final String id;
    private final String label;
    private final int margin;
    private final String note;

    BezelStyle(String id, String label, int margin, String note) {
        this.id = id;
        this.label = label;
        this.margin = margin;
        this.note = note;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /**
     * How many pixels the deck is inset by, which is the whole width the casing has to draw in.
     *
     * <p>⚠ Condition 2 above lives in this number. The deck is pushed in by exactly this much, so
     * the casing never has content underneath it — and a style that wanted to draw wider than its
     * own margin would be overlaying the interface, which is the thing that is not allowed.
     */
    public int margin() {
        return margin;
    }

    /** One sentence of what this looks like, shown in Settings. */
    public String note() {
        return note;
    }

    public static List<BezelStyle> selectable() {
        return List.of(values());
    }

    /**
     * Looks up a persisted id.
     *
     * <p>Empty rather than an exception on an unknown value, so a profile written by a client with
     * one more style than this one still loads.
     */
    public static Optional<BezelStyle> byId(String id) {
        return Arrays.stream(values()).filter(style -> style.id.equals(id)).findFirst();
    }
}
