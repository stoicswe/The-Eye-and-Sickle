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

    public static final int REVEAL_STEPS = 9;

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

    /** How often live readouts twitch to a new figure. */
    public static final double TWITCH_MS = 1900;

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
