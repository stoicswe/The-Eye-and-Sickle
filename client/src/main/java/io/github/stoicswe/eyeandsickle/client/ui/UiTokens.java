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
     * What fraction of its declared size a tool window actually opens at.
     *
     * <h2>⚠ Every size in {@code WindowSpec} is NOMINAL, and this is why</h2>
     *
     * {@code docs/client/05} §2.1 publishes a default size per window and {@code WindowSpec}
     * transcribes that table, but the deck draws its own window manager inside a single Stage — so
     * those figures, which were written for OS windows on a whole screen, open too large on a desk
     * that is already inset by the strip, the rail and the command line. All three of
     * {@code DeckShell}'s open paths scale by this, and {@code DeskManager} then snaps the result to
     * {@link #SNAP_GRID}.
     *
     * <p>⚠ <b>So a window's on-screen size is {@code round(nominal × 0.72 / 22) × 22}, not the number
     * beside its name.</b> It was an unnamed {@code 0.72} written out at three call sites until
     * 2026-08-06, which made that arithmetic invisible at exactly the moment somebody wanted a
     * specific size — the Security Center's row records the reverse calculation. Changing this moves
     * every window at once; {@code WindowCatalogueTest} pins the one size that was asked for
     * explicitly, so that lands as a failure rather than as a window that quietly drifted.
     */
    public static final double WINDOW_OPEN_SCALE = 0.72;

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

    /**
     * One tile on the market's shelf.
     *
     * <p>Fed to a {@code TilePane}, which sizes <b>every</b> tile alike, so this is the shelf's
     * whole geometry: a store reads as a store because the boxes are the same box repeated, and
     * ragged tiles read as a list that has been styled rather than a shelf.
     */
    public static final double MARKET_CARD_WIDTH = 300;

    /**
     * The storefront's content column.
     *
     * <h2>⚠ ONE measure for the whole page, and that is what makes it a shop</h2>
     *
     * The masthead, the search bar, the carousel, the bundle and the shelf all take this and are
     * centred in whatever the window happens to be. Every real storefront does this, and the reason
     * is legibility rather than fashion: text that reflows to 2560px is a line nobody's eye tracks
     * back from, and a shelf that silently goes from three tiles to eight is a different shop at
     * every window size.
     *
     * <p>⚠ A <b>maximum</b>, not a preferred width. A market window narrower than this still works —
     * the column simply stops being the constraint and the tiles wrap to two, then one.
     *
     * <p>Sized to hold three {@link #MARKET_CARD_WIDTH} tiles and their gaps with a little slack, so
     * the shelf's natural row and the column's edge agree instead of leaving a ragged gutter.
     */
    public static final double MARKET_CONTENT_WIDTH = 960;

    /**
     * The clear space above and below the bundle.
     *
     * <p>⚠ On top of the page's own spacing, not instead of it. The bundle is a different <em>kind</em>
     * of offer from the carousel above it and the shelf below — one price for several things — and
     * without a band around it the three read as one undifferentiated stack of cards. Inside §2.3's
     * closed scale, because a bespoke gutter here is the first step towards sixty of them.
     */
    public static final double MARKET_BAND_GAP = SPACE_6;

    /**
     * The Shadow Market's buy/sell drawer handle.
     *
     * <p>⚠ A fixed box, because the caption inside it is <b>rotated</b> — a rotation is a transform
     * and leaves the node's layout bounds alone, so a rotated label left to size itself reserves its
     * horizontal width and the handle comes out as wide as "BUY / SELL" is long.
     */
    /**
     * The Security Center's section rail.
     *
     * <p>⚠ Wider than the deck's own 34px rail and for the opposite reason: that one is a legend of
     * single accelerator characters, this one carries words. A player reads AUDIT and DEFENSE here
     * rather than decoding a letter, because unlike the deck rail it is not backed by a keyboard
     * shortcut they could learn.
     */
    /**
     * How tall the search overlay is when open.
     *
     * <p>Fixed, because the slide has to know how far to travel before the content is measured — a
     * height derived from the results would change mid-animation as they arrived.
     */
    public static final double ANON_OVERLAY_HEIGHT = 280;

    /** Room on the left of the value chart for its value labels. ⚠ Follows ANON_AXIS_TEXT_SIZE. */
    public static final double ANON_AXIS_GUTTER = 64;

    /** Room under the value chart for its time labels. ⚠ Follows ANON_AXIS_TEXT_SIZE. */
    public static final double ANON_AXIS_BASELINE = 20;

    /**
     * Axis label size.
     *
     * <p>⚠ It was 9 and that was too small to read against the plot — an axis is glanced at rather
     * than studied, which argues for restraint and not for illegibility. A gridline whose number
     * cannot be read is a gridline that means nothing.
     */
    public static final double ANON_AXIS_TEXT_SIZE = 11;

    /** How far a value label sits below its gridline, so the two do not collide. */
    public static final double ANON_AXIS_TEXT_RISE = 9;

    /** How far the pointer readout sits from the cursor, so it never lands under it. */
    public static final double ANON_HOVER_OFFSET = 14;

    /** The stock-detail overlay's column. */
    public static final double ANON_DETAIL_WIDTH = 460;

    /** The column of symbols beside an open watchlist's chart. */
    public static final double ANON_WATCH_WIDTH = 230;

    /** AnonShare's account summary column — the broker layout's left rail. */
    public static final double ANON_ACCOUNT_WIDTH = 150;

    /** AnonShare's quote-and-listings column. */
    public static final double ANON_SIDE_WIDTH = 260;

    public static final double SECURITY_RAIL_WIDTH = 104;

    /**
     * The Security Center's state mark — shield, warning or quarantine trefoil.
     *
     * <p>Large, because it is the second thing a player's eye lands on after the verdict and the two
     * are saying the same thing from opposite sides of the panel. Small enough that it never
     * competes with the sentence: §4.4 makes the words the signal and this the reinforcement.
     */
    public static final double SECURITY_MARK = 128;

    /**
     * The illustration in the top-right of a Security Center SECTION — see {@code SectionMark}.
     *
     * <p>⚠ Smaller than {@link #SECURITY_MARK}. That one is the panel's verdict and is meant to be
     * the loudest thing on the screen; these label a section the player has already navigated to, so
     * a mark at the same size would compete with the content it is captioning.
     */
    public static final double SECTION_MARK = 92;

    /**
     * The verdict block that pairs with the mark.
     *
     * <p>⚠ A <b>preferred</b> width, and it has to be. A {@code FlowPane} places children at their
     * preferred size, and {@code setMaxWidth} does not constrain what a {@code wrapText} Label
     * <em>prefers</em> — that is its whole string on one line. Setting only the maximum left this
     * column reporting ~900px, so the mark wrapped to the next row in a window with ample space.
     *
     * <p>Narrow enough that the pair still fits when the window is tiled: this plus
     * {@link #SECURITY_MARK} and a gap clears the panel at its minimum.
     */
    public static final double SECURITY_HEADLINE_WIDTH = 340;

    /**
     * How long one step of the mark's motion holds.
     *
     * <p>⚠ A STEP, not a frame. §5 permits no easing, so the shield's sweep and the trefoil's turn
     * move in whole jumps on the shared {@code Pulse} — smoothness comes from a finer ladder, never
     * from interpolation, which is the same rule {@code REVEAL_STEPS} and the ring wallpaper follow.
     */
    public static final double SECURITY_MARK_STEP_MS = 90;

    /**
     * One status card in the Security Center.
     *
     * <p>⚠ A MAXIMUM, so the cards stack in a readable column instead of stretching to whatever the
     * window is. A status line that reflows to 2000px is one nobody's eye tracks back from — the same
     * reasoning as {@link #MARKET_CONTENT_WIDTH}, applied to a narrower thing.
     */
    public static final double SECURITY_CARD_WIDTH = 520;

    public static final double SHMARK_TAB_WIDTH = 22;

    /** @see #SHMARK_TAB_WIDTH */
    public static final double SHMARK_TAB_HEIGHT = 104;

    /**
     * The drive activity lamp at the head of the command strip.
     *
     * <p>Small on purpose: an indicator lamp that competes with the prompt beside it has stopped
     * being peripheral, and this one is meant to be noticed only when it moves.
     */
    public static final double DISK_LAMP = 6;

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

    /**
     * How often the frosted backdrop re-captures, in milliseconds — 24 frames a second.
     *
     * <h2>⚠ Its own clock, and why it is not {@code Pulse}</h2>
     *
     * {@code Pulse} drives at 100ms and <b>quantises every subscription to a multiple of that</b>, so
     * asking it for 24fps silently rounds up to 10fps. Reaching 24 through Pulse would mean lowering
     * the shared driver's period, which speeds up every decorative widget in the client at once — a
     * change to everything, to fix one thing.
     *
     * <h2>⚠ 24 is affordable ONLY because a refresh is one snapshot</h2>
     *
     * Measured on this project, four windows, 1600×1000: a per-window cycle cost <b>~40ms</b> (a
     * 24fps <em>ceiling</em>, i.e. the whole thread), and a single shared capture costs <b>~9ms</b>
     * and does not grow with the window count. At 24fps that is about 22% of the FX thread, which is
     * real and is the price of the effect. ⚠ Raising this number raises that proportionally — 60fps
     * would be over half the thread and the deck would start dropping input.
     */
    public static final double FROST_MS = 1000.0 / 24.0;

    /**
     * The sync mark's own clock — 30fps.
     *
     * <h2>⚠ WHY THIS IS NOT A {@code Pulse} PERIOD, which is the mistake it was written as</h2>
     *
     * {@code SyncSpin} asked {@code Pulse} for 60ms and got <b>100</b>. Pulse quantises every
     * subscription to a multiple of its own 100ms driver —
     * {@code Math.max(TICK_MS, round(periodMs / TICK_MS) * TICK_MS)} — so anything under 150ms
     * silently becomes 10fps. Nothing reports it; the widget simply steps a third as often as the
     * number beside it says, which is what made a hand-tuned 20-entry table read as a stutter.
     *
     * <p>⚠ The fix is not to lower Pulse's driver: that would speed up <b>every</b> decorative widget
     * in the client to smooth one mark. This is the same reasoning — and the same resolution —
     * {@link #FROST_MS} already records.
     *
     * <p>⚠ 30 rather than 24 because this one is <b>rotation</b>. A blurred backdrop is forgiving of
     * a dropped frame; a rotating shape at 24fps beats visibly against the eye's motion tracking, and
     * the cost here is one {@code setRotate} per tick against the frost's several snapshots.
     */
    public static final double SPIN_MS = 1000.0 / 30.0;

    /**
     * The largest share of wall-clock time the frosted backdrop may spend on itself.
     *
     * <h2>⚠ Why 24fps has to be a CEILING rather than a rate</h2>
     *
     * A refresh costs one snapshot per <em>overlapping</em> window plus one shared: tiled, that is
     * <b>~9ms</b> and 24fps is comfortable; four windows fully cascaded is <b>~34ms</b>, and eight
     * would be worse. A fixed 24fps would hand the whole thread to the blur exactly when the player
     * has the most on screen — the deck would stutter under the interaction that caused it, which is
     * the worst possible moment.
     *
     * <p>So {@code DeskManager} measures each refresh and refuses to start the next one until the
     * gap is at least {@code cost / FROST_BUDGET}. At this share, a 9ms refresh is free to run at the
     * full 24fps and a 34ms one paces itself down to about 7fps. The frost stays <b>correct</b> at
     * every window count and only its <em>frequency</em> degrades, which is the right thing to give
     * up: a slightly stale blur is invisible at this radius, and a stuttering desk is not.
     */
    public static final double FROST_BUDGET = 0.25;

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

    /**
     * Glyph rows in one node box. About half the columns — see the aspect note above.
     *
     * <p>⚠ <b>Five, not four, since 2026-08-07.</b> The fifth is the machine's name, under its
     * address, and it appears only once {@code PortScanTarget.IDENTITY} has established one. The
     * <em>slot</em> is reserved whether or not the name is known: a box that grew a line when a scan
     * came back would re-flow the whole map underneath the player, so an unnamed machine keeps a
     * blank line and the column geometry never moves.
     *
     * <p>Every consumer derives its arithmetic from this constant rather than from a literal 4
     * ({@code NetCanvas} in five places, {@code NetGraph}'s blank slot, {@code NetGraphTest}), which
     * is what made the change one line here instead of a hunt.
     */
    public static final int NET_NODE_LINES = 5;

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
