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
 *   <li><b>What moves obeys §5.</b> ⚠ This condition read "nothing here moves" until
 *       {@link #TERMINAL} landed with blinking status lamps. The rule was never "a casing must be
 *       still" — §9.1's actual condition is that motion artefacts step rather than tween and that
 *       {@code prefers-reduced-motion} stops them. The lamps run on {@code Pulse.animate}, the
 *       decorative channel, which is exactly that. Every other style is still inert.
 *       <p>⚠ Reduced motion freezes the lamps <b>lit</b>, not dark. A panel whose indicators all
 *       went out would read as powered off — a wrong statement about the machine, where a still
 *       lamp is merely a less lively one.
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
     * Gothic industrial: heavy plate, buttressed corners, rivet rows and warning chevrons.
     *
     * <p>The grimdark-machinery register — a screen bolted into something enormous and badly
     * maintained. Deep plate, a rivet line following every edge, corner buttresses that step inward,
     * and hazard chevrons on the flanks.
     *
     * <p>⚠ <b>Genre, not iconography.</b> This is heavy-industrial gothic drawn from rivets, plate
     * and chevrons — deliberately none of the protected emblems the obvious reference is known for.
     * The look comes from the construction, which is free to borrow; the insignia are not.
     */
    GOTHIC("gothic", "Gothic plate", 46,
            "Heavy riveted plate, buttressed corners and hazard chevrons. The loudest option."),

    /**
     * A hardware front panel: status LEDs that actually blink, switches, a grille and labels.
     *
     * <p>⚠ The <b>only</b> style that moves, which is why §9.2's third condition had to be amended
     * from "nothing here moves" to "what moves obeys §5". The LEDs run on {@code Pulse.animate} —
     * the decorative channel — so {@code prefers-reduced-motion} freezes them lit rather than
     * dark. A panel whose lamps all went out under reduced motion would read as powered off, which
     * is a worse answer than a still one.
     */
    TERMINAL("terminal", "Terminal panel", 40,
            "A front panel with blinking status lamps, switches and a grille."),

    /**
     * Beveled chrome in the 3.1 idiom: raised outer frame, title bar, drawn control boxes.
     *
     * <p>⚠ §9 bans "native window chrome of any kind" and this deliberately imitates some. The ban
     * protects §0's premise that <em>the player never sees their own operating system</em> — and a
     * thirty-year-old window manager is nobody's operating system. It reads as a retro machine
     * rather than as the host showing through, which is the thing the rule exists to prevent.
     *
     * <p>The bevel is legal on its own terms: §2.1 says depth comes from <b>brightness, never from
     * shadow</b>, and a bevel is exactly a light edge against a dark one. No blur, no drop shadow.
     */
    CHROME_31("chrome31", "Chrome 3.1", 30,
            "Raised bevel, title bar and drawn control boxes. A retro window manager."),

    /**
     * The old Unix frame: thick double bevel, segmented border with corner grips, square buttons.
     *
     * <p>The Motif/{@code mwm} look — the border split into runs by short perpendicular rules, with
     * the corner segments reading as resize grips, and a title bar carrying a menu box on the left
     * and two square buttons on the right. Squarer, heavier and greyer than {@link #CHROME_31}.
     */
    MOTIF("motif", "Motif", 34,
            "Thick double bevel with corner grips and square buttons. The old Unix frame."),

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
