package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * The rig powering on: a glowing ring, a progress bar, and a name that assembles itself.
 *
 * <h2>The ring is the O</h2>
 *
 * {@code u} and {@code S} fade in on either side of it as the bar fills, and what has been on screen
 * the whole time turns out to have been the middle letter of <b>uOS</b>. That is the entire idea, and
 * it is why the letters arrive on the bar's progress rather than on a timer of their own — the thing
 * the player is watching complete and the thing that completes are the same thing.
 *
 * <h2>Two boot screens, and why neither is the other one</h2>
 *
 * This is <b>firmware</b>. It plays once, before the login screen, and it knows nothing: no operator
 * has been chosen yet, so there is no rig, no ledger and no capacity to report.
 * {@link BootSequence} is <b>uOS</b> — it plays after a character is opened and every line it prints
 * is that save's real state. The order is the fiction and the fiction is a real machine's:
 *
 * <pre>
 *   power on  →  [ this ]  →  who are you?  →  [ BootSequence ]  →  the deck
 * </pre>
 *
 * <p>⚠ Once per <em>process</em>, not once per visit to the menu. Returning to the menu from a game
 * is logging out, and a machine that cold-boots every time somebody logs out is a machine with a
 * fault. {@code EyeAndSickleClient} holds that flag.
 *
 * <h2>⚠ White on black, and the palette cannot reach it</h2>
 *
 * Every other surface in this client is themed. This one is not, deliberately: firmware runs before
 * anything knows who the player is, and a splash that came up in their chosen accent would be
 * claiming otherwise. Nothing in here resolves an {@code -es-} palette token — {@code .es-poweron}
 * declares its own two colours, so the five palette overlays have nothing to override. §10 criterion
 * 2 still holds: the colours are in the stylesheet, they are simply not the palette's.
 *
 * <p>The visible consequence is at the handover, where black gives way to the menu's own ground. On
 * the four dark palettes that is invisible; on {@code classic} the ground is light and the swap is a
 * real change. That is the correct reading — the firmware is the machine's, the desktop is yours.
 *
 * <h2>The glow</h2>
 *
 * {@link GlowRing} draws it, and its header carries the argument: eight overlapping strokes rather
 * than an effect, because §9's ban on drop shadows and blur is still standing. Here it also
 * <b>breathes</b>, on a sine over the halo's opacity, on wall time rather than on progress — a glow
 * that slowed as the bar filled would be reporting on a load, and there is nothing to report. That
 * is continuous motion, permitted by §5.1 and nowhere else; see {@link Fade} for the argument.
 *
 * <h2>The bar measures nothing, and that is why it may be continuous</h2>
 *
 * §4's component catalog says a meter is "3px × 9px cells with 1px gaps. Never a continuous bar or
 * gradient." That rule protects <b>measurements</b> — the whole point of a discrete meter is that a
 * player can count it, and a smooth bar turns a number into a vibe. Nothing here is a number. There
 * is no work to wait for; solo loads in milliseconds. The bar is time passing, drawn the way a
 * firmware bar is drawn, and it is honest precisely because it is not shaped like anything the
 * player reads a quantity off. See §4.1 for the amendment.
 *
 * <p>⚠ It slides, and that took an {@code AnimationTimer}. The first cut drove it off {@link Pulse},
 * whose driver ticks at 100ms — twenty-four increments across the fill, so the bar lurched forward
 * in eleven-pixel jumps. Pulse's rate is right for everything that twitches and wrong for the one
 * thing here that slides.
 *
 * <p>⚠ The rounded ends are {@link Rectangle#arcWidthProperty} — <b>geometry, not CSS</b>. §9.3
 * permits a non-zero {@code -fx-background-radius} only under {@code .es-rounded}, and
 * {@code UiContractTest} fails the build on one anywhere else. Same answer the login screen's
 * circles reached, for the same reason.
 *
 * <h2>The handover is a fade (§5.1)</h2>
 *
 * When the bar lands, the content fades out and the login screen fades in — {@link Fade}, whose
 * header carries the argument for why §5's ban on continuous ramps does not reach a title card.
 *
 * <h2>Skipped whole under reduced motion</h2>
 *
 * §5 makes atmosphere the first thing to go, and this screen is nothing but atmosphere. Any key or
 * click also ends it — {@link BootSequence}'s rule, and this one plays before every session.
 */
public final class PowerOn extends StackPane {

    /** How long the bar takes to fill. Short: nothing is loading, and the player wants the game. */
    private static final double FILL_MS = 2600;

    /** The bar's own geometry. Thin and wide, the proportions a firmware bar has. */
    private static final double BAR_WIDTH = 248;

    private static final double BAR_HEIGHT = 6;

    /**
     * The bright ring's radius, in points.
     *
     * <p>Sized against the wordmark's cap height rather than picked: the ring is a letter, and a
     * letter that does not sit on the same optical line as its neighbours reads as a graphic beside
     * a word instead of a word.
     */
    private static final double RING_RADIUS = 33;

    /** One full breath of the halo. Slow enough to read as glow rather than as a blink. */
    private static final double BREATH_MS = 2200;

    // Where each letter arrives, as a fraction of the bar. Staggered, so the name assembles left to
    // right — both at once reads as one fade with a gap in the middle rather than as spelling.
    private static final double U_IN = 0.12;
    private static final double U_FULL = 0.46;
    private static final double S_IN = 0.46;
    private static final double S_FULL = 0.80;

    private final Runnable onFinished;
    private final Rectangle fill;
    private final VBox column;
    private final Label leading;
    private final Label trailing;
    private final GlowRing ring;
    private javafx.animation.AnimationTimer ticker;
    private boolean finished;

    private PowerOn(Runnable onFinished) {
        this.onFinished = onFinished;
        getStyleClass().add("es-poweron");
        setAlignment(Pos.CENTER);

        leading = letter("u");
        trailing = letter("S");

        // ── the ring ──────────────────────────────────────────────────────────────────────────
        //
        // The emblem lives in GlowRing, which carries the whole argument for why the glow is eight
        // overlapping strokes rather than an effect, and why it overflows its own layout box.
        GlowRing ring = new GlowRing(RING_RADIUS);
        this.ring = ring;

        HBox wordmark = new HBox(UiTokens.SPACE_2, leading, ring, trailing);
        wordmark.setAlignment(Pos.CENTER);

        // ── the bar ───────────────────────────────────────────────────────────────────────────
        Rectangle track = new Rectangle(BAR_WIDTH, BAR_HEIGHT);
        track.setArcWidth(BAR_HEIGHT);
        track.setArcHeight(BAR_HEIGHT);
        track.getStyleClass().add("es-poweron-track");

        fill = new Rectangle(0, BAR_HEIGHT);
        fill.setArcWidth(BAR_HEIGHT);
        fill.setArcHeight(BAR_HEIGHT);
        fill.getStyleClass().add("es-poweron-fill");

        // ⚠ CENTER_LEFT on the fill, CENTER on the pane. A StackPane centres its children by
        // default, so a growing rectangle would expand from the middle outwards in both directions —
        // which reads as a thing opening rather than a thing filling.
        StackPane bar = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.setMinSize(BAR_WIDTH, BAR_HEIGHT);
        bar.setPrefSize(BAR_WIDTH, BAR_HEIGHT);
        bar.setMaxSize(BAR_WIDTH, BAR_HEIGHT);

        column = new VBox(UiTokens.SPACE_6 * 3, wordmark, bar);
        column.setAlignment(Pos.CENTER);
        column.setMaxWidth(Region.USE_PREF_SIZE);
        column.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(column);

        renderAt(0);

        setOnMouseClicked(event -> finish());
        setFocusTraversable(true);
        setOnKeyPressed(event -> finish());
    }

    private static Label letter(String glyph) {
        Label label = new Label(glyph);
        label.getStyleClass().add("es-poweron-letter");
        return label;
    }

    /**
     * Starts the splash.
     *
     * @param onFinished run on the JavaFX thread when the bar fills or the player skips — exactly
     *     once either way. A skip that dropped the callback would leave the player on a dead screen.
     */
    public static PowerOn play(Runnable onFinished) {
        PowerOn splash = new PowerOn(onFinished);
        splash.start();
        return splash;
    }

    /**
     * A splash that is built but not running, for {@code PowerOnSnapshot}.
     *
     * <p>⚠ Not {@code play()} with the timers ignored. Under reduced motion {@code play} finishes on
     * the spot and the handover fade takes the content's opacity to zero — correct behaviour that
     * renders as an entirely black page, which is exactly what the first snapshot produced.
     */
    static PowerOn still() {
        return new PowerOn(() -> {});
    }

    private void start() {
        if (Pulse.shared().reducedMotion()) {
            finish();
            return;
        }
        // ⚠ Per FRAME, not on Pulse. Pulse's driver ticks at 100ms, which is right for everything
        // that twitches and wrong for the two things here that slide. This is the client's only
        // per-frame animation besides Fade, and UiContractTest rations both by name.
        ticker = new javafx.animation.AnimationTimer() {
            private long startedAt;

            @Override
            public void handle(long now) {
                if (startedAt == 0) {
                    startedAt = now;
                    return;
                }
                step((now - startedAt) / 1_000_000.0);
            }
        };
        ticker.start();
    }

    private void step(double elapsedMs) {
        if (finished) {
            return;
        }
        // The breath runs on wall time, not on progress: a glow that slowed down as the bar filled
        // would be reporting on the load, and there is nothing to report.
        ring.setGlow(0.55 + 0.45 * Math.sin(elapsedMs / BREATH_MS * 2 * Math.PI));
        double progress = Math.min(1.0, elapsedMs / FILL_MS);
        renderAt(progress);
        if (progress >= 1.0) {
            finish();
        }
    }

    /**
     * Paints the splash at one point on the bar.
     *
     * <p>Package-private so {@code PowerOnSnapshot} can render a frame without a running timer.
     * Everything that moves with progress lives here and nowhere else, so the still the harness
     * writes is the same arithmetic the screen runs.
     */
    void renderAt(double progress) {
        fill.setWidth(BAR_WIDTH * progress);
        leading.setOpacity(ramp(progress, U_IN, U_FULL));
        trailing.setOpacity(ramp(progress, S_IN, S_FULL));
    }

    /** 0 below {@code from}, 1 above {@code to}, linear between. */
    private static double ramp(double value, double from, double to) {
        if (value <= from) {
            return 0;
        }
        if (value >= to) {
            return 1;
        }
        return (value - from) / (to - from);
    }

    /**
     * Ends the splash, exactly once — fading out first unless the player skipped.
     *
     * <p>⚠ The fade is on the {@code column}, not on this pane. The pane paints the ground, and
     * fading that would show the Scene's own fill through it — which is {@code TRANSPARENT} on this
     * Stage (§0), so the window would go momentarily see-through on the way to the login screen. The
     * content fades over an opaque black that never moves, which is also what a real firmware
     * handover looks like.
     */
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        Fade.out(column, onFinished);
    }
}
