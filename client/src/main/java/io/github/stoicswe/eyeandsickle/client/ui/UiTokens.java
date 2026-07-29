package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.scene.text.Font;

/**
 * Every number the deck is drawn with.
 *
 * <h2>Why this class has to exist at all</h2>
 *
 * {@code docs/design/ui-design-language.md} §7.2 names the gap: JavaFX <b>looked-up colours are
 * colours only</b>. There is no numeric equivalent of a CSS custom property, so a spacing scale
 * expressed in CSS would be sixty literal {@code 9px}s that drift apart one commit at a time. The
 * split the design language mandates is therefore: <b>colours live in {@code theme.css} and nothing
 * else does; sizes, spacings and durations live here and nowhere else.</b>
 *
 * <p>That split is also what makes §10 criterion 2 — "no hex literals in Java" — checkable rather
 * than aspirational, and {@code UiTokensTest} checks it across the whole {@code ui} package.
 *
 * <h2>The scale is tight on purpose</h2>
 *
 * §2.3 fixes it at {@code 1, 5, 7, 9, 12, 14} and nothing between. Density is the aesthetic; a 16px
 * gutter appearing because a panel "felt cramped" is the first step towards the failure mode §1
 * names — <em>a competent dark-mode developer tool</em>.
 */
public final class UiTokens {

    private UiTokens() {}

    // ── Spacing (§2.3). The whole scale. Do not add a value between two of these. ──────────────

    public static final double HAIR = 1;
    public static final double SPACE_1 = 1;
    public static final double SPACE_2 = 5;
    public static final double SPACE_3 = 7;
    public static final double SPACE_4 = 9;
    public static final double SPACE_5 = 12;
    public static final double SPACE_6 = 14;

    // ── Geometry (§2.3) ───────────────────────────────────────────────────────────────────────

    /** The 45° corner cut, top-right of every major panel. Fixed at 18px at every window size. */
    public static final double NOTCH = 18;

    /**
     * Window corner radius, when the rounded-corners setting is on (§9.3).
     *
     * <h2>⚠ THE FIGURE IS UNVERIFIED — it is an approximation of macOS Tahoe, not a measurement</h2>
     *
     * The brief was "match macOS Tahoe's window curvature". Tahoe's windows are visibly rounder than
     * the ~10pt that Big Sur through Sequoia used, and this is set to reflect that — but
     * <b>the exact value has not been checked against the real thing</b>, and {@code CLAUDE.md} is
     * explicit that a real-world fact nobody verified must not be stated as one. It lives here, as a
     * single constant, precisely so confirming it is a one-line change rather than an archaeology
     * expedition through a stylesheet and two view classes.
     *
     * <h2>⚠ It will not match exactly however the number is tuned</h2>
     *
     * macOS corners are a <b>continuous curve</b> — a squircle — and {@link javafx.scene.shape.Rectangle}'s
     * {@code arcWidth}/{@code arcHeight} produce a <b>circular arc</b>. A circular corner reads
     * slightly "tighter" at the same nominal radius because the curvature changes abruptly where the
     * arc meets the straight edge, which is the whole thing a squircle exists to avoid. Matching
     * properly would mean building the clip from a Bézier path rather than a rounded rectangle;
     * that is a real option and is deliberately not taken yet, because a wrong <em>radius</em> is
     * one number and a wrong <em>curve family</em> is a shape nobody can adjust.
     *
     * <p>⚠ Not part of §2.3's spacing scale, and must not be added to it. That scale is
     * {@code 1, 5, 7, 9, 12, 14} and is closed — this is a geometry constant like {@link #NOTCH},
     * which is also outside it and for the same reason.
     */
    public static final double WINDOW_RADIUS = 16;

    /** Base cell for meters and the cycle grid. */
    public static final double CELL = 11;

    /** Cycle-grid cells, 25 to a row (§4). */
    public static final int CYCLE_CELLS = 100;

    public static final int CYCLE_PER_ROW = 25;

    /** Narrow layouts drop to 20 and then 10 per row, matching the reference's breakpoints. */
    public static final int CYCLE_PER_ROW_NARROW = 20;

    public static final int CYCLE_PER_ROW_TIGHT = 10;

    /** A cell meter's bars: 3 wide, 9 tall, 1 apart. Never a continuous bar (§4). */
    public static final double METER_BAR_WIDTH = 3;

    public static final double METER_BAR_HEIGHT = 9;

    /** Buffer indicator: 8 cells span the 4-hour cap, one per half hour (§4). */
    public static final int BUFFER_CELLS = 8;

    public static final double BUFFER_CELL_WIDTH = 6;

    public static final double BUFFER_CELL_HEIGHT = 10;

    /** Left rail width (§3). Hidden below {@link #NARROW_WIDTH}. */
    public static final double RAIL_WIDTH = 34;

    /** Header strip minimum height (§3) — every region has one. */
    public static final double STRIP_HEIGHT = 24;

    /** Main splits 1.32fr / 1fr above this width, one column below it (§3). */
    public static final double NARROW_WIDTH = 900;

    public static final double TIGHT_WIDTH = 520;

    /** §10 criterion 9: the layout must hold across this range. */
    public static final double MIN_SUPPORTED_WIDTH = 1280;

    public static final double MAX_SUPPORTED_WIDTH = 2560;

    /** The desk's snap lattice, in the character-cell language §11 question 1 asks about. */
    public static final double SNAP_GRID = 22;

    /**
     * The mascot on the rig monitor's ABOUT tab, in width only.
     *
     * <p>⚠ Width alone, and its partner is {@code preserveRatio} rather than a second token. A
     * height here would be a second source of truth for the drawing's aspect ratio, and the day the
     * artwork is redrawn at a different shape the picture would silently start stretching. Medium by
     * intent: large enough to read as a drawing rather than an icon, small enough that the
     * specification sheet under it is still the panel's subject.
     */
    public static final double MASCOT_WIDTH = 224;

    /**
     * Measure for the ABOUT tab's rule and its footnote.
     *
     * <p>Both are set from one number so the paragraph wraps exactly at the hairline above it. A
     * free-running wrap in a scrollable panel is as wide as the window, which at
     * {@link #MAX_SUPPORTED_WIDTH} is a line nobody's eye tracks back from.
     */
    public static final double ABOUT_RULE_WIDTH = 460;

    /** The ABOUT tab's key column, wide enough for {@code RUNTIME} without the values jittering. */
    public static final double ABOUT_KEY_WIDTH = 78;

    /**
     * A portrait on Settings → Credits.
     *
     * <p>Smaller than the login screen's face: that one is a target the player clicks, this one is
     * an illustration beside a name. Big enough to recognise somebody, not so big the page becomes
     * a gallery.
     */
    public static final double CREDIT_FACE = 56;

    /** The network mark beside a credits handle. Sized to the cap height of the line it sits on. */
    public static final double SOCIAL_MARK = 13;

    // ── Type (§2.2) ───────────────────────────────────────────────────────────────────────────

    /** Labels, keys, headers, buttons — Martian Mono 500, uppercase. */
    public static final double LABEL_SIZE = 8.5;

    public static final double TABLE_HEADER_SIZE = 8;

    /** Body, data, tables, numbers — IBM Plex Mono. */
    public static final double BODY_SIZE = 12;

    public static final double SMALL_SIZE = 11;

    public static final double MICRO_SIZE = 9.5;

    /** The one large thing on a panel — Martian Mono 700. */
    public static final double DISPLAY_SIZE = 30;

    public static final double DISPLAY_SIZE_TIGHT = 24;

    // ── Motion (§5). Step and linear only; every duration here is in milliseconds. ────────────

    /** Panel reveal: a horizontal clip wipe in exactly {@link #REVEAL_STEPS} discrete jumps. */
    public static final double REVEAL_MS = 340;

    /**
     * How long the deck takes to come up out of the dark after the boot log.
     *
     * <p>Longer than a panel reveal because it is a different event: a panel wiping in is the
     * interface responding, and this is the machine turning on. Short enough that it never feels
     * like a wait for someone who has seen it a hundred times.
     */
    public static final double WAKE_MS = 900;

    public static final int REVEAL_STEPS = 9;

    /**
     * One frame of a stepped readout animation, in milliseconds.
     *
     * <p>Not a frame rate — a <b>step</b> rate. §5 permits step timing only, so anything animated
     * here advances in whole jumps at this cadence rather than tweening between them. 40ms is fast
     * enough that a counting balance reads as continuous motion and slow enough that it is visibly
     * a sequence of values rather than a blur.
     */
    public static final double FRAME_MS = 40;

    /** Stagger between panes, so the deck wakes up in sequence rather than all at once. */
    public static final double REVEAL_STAGGER_MS = 170;

    /** The in-progress sweep bar, one linear pass. */
    public static final double SWEEP_MS = 2600;

    /** Command-strip caret, a step blink — not a fade. */
    public static final double CARET_MS = 1060;

    /** Thermal-recovery cells blink between two states. */
    public static final double RECOVERY_BLINK_MS = 1000;

    /** Greeble regenerates on this period; it means nothing, and it must keep meaning nothing. */
    public static final double GREEBLE_MS = 4200;

    /**
     * The desk wallpaper steps one character cell on this period.
     *
     * <p>Slower than the greeble it is made of, and that is the point: greeble sits inside a panel
     * the player is already looking at, while this is behind everything and in peripheral vision the
     * whole session. Fast ambient motion in the periphery is the most tiring thing an interface can
     * do. §5 allows step timing only, so this is a whole-cell jump — nothing here interpolates.
     */
    public static final double SUBSTRATE_DRIFT_MS = 1100;

    /** How often live readouts twitch to a new figure. */
    public static final double TWITCH_MS = 1900;

    // ── The breach (docs/design/05) ───────────────────────────────────────────────────────────
    //
    // Every one of these is load-bearing, not a probe. §5 allows step timing only, so the three
    // durations below are periods between discrete repaints — nothing here interpolates.

    /** The viewport's scan line advances one row on this period. */
    public static final double BREACH_SCAN_MS = 220;

    /** Fast flicker for an unknown port slot — the only thing in the breach that moves quickly. */
    public static final double BREACH_PULSE_MS = 90;

    /** Ambient re-draw for the lattice packet and other slow instrument motion. */
    public static final double BREACH_TICKER_MS = 1400;

    /**
     * The attention meter is CELLS, never a continuous bar (§4) — same argument as the cycle grid:
     * a smooth bar implies a precision the model does not have, and attention is countable.
     */
    public static final int ATTENTION_CELLS_MAX = 40;

    public static final int ATTENTION_CELLS_PER_ROW = 10;

    public static final double ATTENTION_CELL_WIDTH = 6;

    public static final double ATTENTION_CELL_HEIGHT = 10;

    /**
     * Width reserved for the attention meter's preview caption, whether or not it says anything.
     *
     * <h2>⚠ This is a HOVER-FEEDBACK LOOP FIX, not a spacing preference</h2>
     *
     * The caption is empty at rest and reads {@code NEXT: FUZZER VOLLEY -6} while an action is
     * hovered. It lives inside the meter, the meter sits beside the cost strip in one row, and the
     * strip is a {@code FlowPane} — so the caption appearing widened the meter, which narrowed the
     * strip, which reflowed the chips, which moved the chip out from under the pointer. That fired
     * MOUSE_EXITED, which cleared the caption, which shrank the meter, which moved the chip back
     * under the pointer, which fired MOUSE_ENTERED. The strip visibly oscillated for as long as the
     * pointer rested near a chip's edge.
     *
     * <p>Reserving the space means the meter's width never depends on what the pointer is doing, so
     * the loop cannot start. Wide enough for the longest action name the game ships plus its prefix
     * and cost; a caption longer than this clips rather than pushes, which is the correct failure —
     * the same figure is printed in the chip the player is already looking at.
     */
    public static final double ATTENTION_PREVIEW_WIDTH = 190;

    /** The character grid the breach viewport draws its ASCII render into. */
    public static final int VIEWPORT_ROWS = 18;

    public static final int VIEWPORT_COLS = 54;

    /** How many actions the attention ledger keeps. §4 requires the player can always see what
     * each action cost, so this is deep enough to cover a whole breach rather than a screenful. */
    public static final int LEDGER_MAX_ROWS = 60;

    // ── The network map (docs/design/07) ──────────────────────────────────────────────────────
    //
    // The graph is laid out in CHARACTER CELLS, not pixels — a node box is a fixed rectangle of
    // glyphs and edges are routed along cell lanes between them. These are counts, not sizes, which
    // is why they are ints.
    //
    // ⚠ A character cell is roughly twice as tall as it is wide. A node box that is square in cells
    // renders as a tall rectangle on screen, so NET_NODE_COLS is deliberately about double
    // NET_NODE_LINES to come out visually square. Same correction CoreCage's project() applies for
    // the same reason.

    /** Glyph columns in one node box. */
    public static final int NET_NODE_COLS = 18;

    /** Glyph rows in one node box. About half the columns — see the aspect note above. */
    public static final int NET_NODE_LINES = 4;

    /** Columns reserved between hop layers for edge routing. */
    public static final int NET_LATERAL_COLS = 10;

    /** Blank columns between adjacent node boxes in the same layer. */
    public static final int NET_GAP_COLS = 3;

    /** How many parallel routing lanes an edge may pick, so parallel edges do not overdraw. */
    public static final int NET_LANES = 3;

    /** The tallest a rendered column may get before the view scrolls rather than shrinking. */
    public static final int NET_MAX_ROWS = 60;

    /** A packet steps one cell along its edge on this period. Step timing (§5), never a tween. */
    public static final double NET_PACKET_MS = 240;

    // ── Fonts ─────────────────────────────────────────────────────────────────────────────────

    /**
     * The bundled family names, as the TTFs report them once loaded.
     *
     * <p>Java-side constants rather than CSS-side, because {@link Fonts} has to name the family to
     * verify it actually registered — a silently-missing font is the single most likely way this
     * design language degrades into "a monospace dark theme" on someone else's machine.
     */
    public static final String DISPLAY_FAMILY = "Martian Mono";

    public static final String BODY_FAMILY = "IBM Plex Mono";

    /** A Martian Mono face at a given size, for the places that need a {@link Font} not a class. */
    public static Font display(double size) {
        return Font.font(DISPLAY_FAMILY, size);
    }

    public static Font body(double size) {
        return Font.font(BODY_FAMILY, size);
    }
}
