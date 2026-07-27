package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachLayer;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.LayerOutcome;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The target, drawn: a cutaway elevation of the thing being broken into.
 *
 * <h2>It is a meter, not a picture of one</h2>
 *
 * The tower is a stack of horizontal bands, <b>layer 0 at the bottom</b> because that is the one the
 * player stands on first and everything above it is still sealed. Each band's fill states its
 * outcome, the active band is framed heavier and carries a rotating texture in its own class's
 * character, and the plinth under the tower is a strike gauge. Every mark on it is a readout of
 * something in {@code BreachSnapshot}; nothing here is invented for atmosphere.
 *
 * <p>That is the whole argument for it existing under D-6. {@code docs/client/01-visual-language.md}
 * §7.3 forbids "anything at all while a breach is live, beyond the meters themselves", and the
 * amendment permits motion "if and only if the motion is itself the readout". The texture rotates on
 * exactly the band that is live and on no other; the scan marker travels the height of the tower
 * that is actually open. Stop the breach and both stop.
 *
 * <h2>Depth from glyph weight</h2>
 *
 * Four fills, four weights, in the order a player experiences them: {@code ▓} sealed, the class
 * texture while live, {@code ░} cleared, {@code ▒} bypassed, {@code █} locked. The distinction
 * survives greyscale, a monochrome phosphor palette and the high-visibility theme — which matters
 * more here than anywhere else in the client, because a player reading this panel is deciding
 * whether to spend attention or walk away.
 *
 * <h2>Ink, and the one place alarm is spent</h2>
 *
 * §2.1 rations alarm to twice per screen and D-7 rations amber to <em>one</em> element in the entire
 * feature, which is not on this panel. So the viewport has exactly two levels of grey and one alarm
 * use: {@link LayerOutcome#LOCKED}. A locked layer ends the attempt, so it is hostile state by the
 * strictest reading, and it can never appear twice in a live breach — the first one resolves it.
 *
 * <h2>An idle instrument holds still</h2>
 *
 * {@link #advance()} returns without touching the grid when there is no active layer. That is
 * {@code CoreCage}'s rule verbatim and it is the difference between a gauge and a screensaver: a
 * still viewport has told the player something true — nothing is open — before they read a word of
 * it. Under reduced motion {@link Pulse#animate} never fires again after construction, so the
 * widget holds whichever frame {@link #show} last painted; there is nothing extra to write, because
 * every render is complete in itself and no state is carried across frames except the phase.
 */
public final class BreachViewport extends StackPane {

    /** Frame width in characters. Two columns are left outside it for the scan marker. */
    private static final int BOX_COLS = UiTokens.VIEWPORT_COLS - 2;

    /** The frame occupies everything above the plinth. */
    private static final int BOX_ROWS = UiTokens.VIEWPORT_ROWS - 2;

    private static final int INTERIOR_COL = 1;

    private static final int INTERIOR_WIDTH = BOX_COLS - 2;

    /** First interior row available to the tower: below the frame top and the header rule. */
    private static final int TOWER_TOP = 3;

    /** Interior rows the tower may use, between the header rule and the frame bottom. */
    private static final int TOWER_ROWS = BOX_ROWS - TOWER_TOP - 1;

    private static final int PLINTH_ROW = UiTokens.VIEWPORT_ROWS - 2;

    private static final int STRIKE_ROW = UiTokens.VIEWPORT_ROWS - 1;

    /** Column of the travelling scan marker, outside the frame. */
    private static final int MARKER_COL = UiTokens.VIEWPORT_COLS - 1;

    /** Chevron spacing on the plinth. Wide enough to read as feet rather than as a hazard band. */
    private static final int PLINTH_PITCH = 6;

    /** Left inset for the plinth and the strike gauge. */
    private static final int GAUGE_COL = 4;

    /**
     * Where the strike gauge's words start, regardless of how many pips precede them.
     *
     * <p>⚠ Measured the hard way in the headless harness: laying the text immediately after the last
     * pip moves the whole sentence sideways every time a strike is spent. §5 permits a value to
     * twitch to a new figure; it does not permit the label beside it to slide, and a caption that
     * changes column between two renders reads as the panel re-laying out. Six is above the highest
     * {@code strikeLimit} the tier table in the implementation spec §3.5 grants (4).
     */
    private static final int GAUGE_TEXT_COL = GAUGE_COL + 6 + 2;

    private final AsciiCanvas canvas =
            new AsciiCanvas(UiTokens.VIEWPORT_ROWS, UiTokens.VIEWPORT_COLS);
    private final Label caption = Ui.micro("");

    private BreachSnapshot snapshot;
    private int step;
    private AutoCloseable ticker;

    public BreachViewport() {
        caption.getStyleClass().add("es-viewport-caption");
        VBox column = new VBox(UiTokens.SPACE_2, canvas, caption);
        column.setAlignment(Pos.TOP_LEFT);
        getChildren().add(column);
        setAlignment(Pos.TOP_LEFT);
        render();
        // Decorative by Pulse's classification, which is the correct one: freezing it removes no
        // information, because every band's state is also stated in words on the band itself.
        ticker = Pulse.shared().animate(UiTokens.BREACH_SCAN_MS, this::advance);
    }

    /** Null-safe: a null or resolved snapshot paints a frame that then holds still. */
    public void show(BreachSnapshot next) {
        this.snapshot = next;
        render();
    }

    /**
     * One step of the scan.
     *
     * <p>Holds the frame outright when nothing is open. See the class comment — this is the rule
     * {@code CoreCage} established and the reason the panel reads as instrumentation.
     */
    private void advance() {
        if (snapshot == null || snapshot.resolved() || !hasLiveLayer()) {
            return;
        }
        step++;
        render();
    }

    /**
     * ⚠ Not merely "{@code active()} returned something".
     *
     * <p>{@code BreachSnapshot.active()} yields whatever sits at {@code activeLayer}, and a layer
     * that has just been <b>locked out</b> is still sitting there for the frame before the engine
     * resolves the attempt. Sweeping a scan marker over a tower that has already failed is the
     * viewport claiming work is in progress when it is not — the exact lie the idle rule exists to
     * prevent. The state, not the index, decides.
     */
    private boolean hasLiveLayer() {
        return snapshot.active().filter(BreachViewport::isActive).isPresent();
    }

    /** Advances exactly one step. Exposed so a harness can walk the animation deterministically. */
    public void tick() {
        advance();
    }

    private void render() {
        canvas.clear();
        canvas.box(0, 0, BOX_ROWS, BOX_COLS, AsciiCanvas.INK_DIM);
        if (snapshot == null) {
            idle();
        } else {
            head();
            tower();
            plinth();
            strikes();
        }
        canvas.paint();
    }

    /**
     * The empty state.
     *
     * <p>§6: "Empty states are an instruction, not a mood piece." So the second line says what to do
     * rather than restating that the first line is empty.
     */
    private void idle() {
        canvas.rule(2, 0, BOX_COLS, AsciiCanvas.INK_DIM);
        int middle = TOWER_TOP + TOWER_ROWS / 2;
        canvas.centre(middle - 1, INTERIOR_COL, INTERIOR_WIDTH, "NO BREACH OPEN", AsciiCanvas.INK_DIM);
        canvas.centre(middle + 1, INTERIOR_COL, INTERIOR_WIDTH, "PICK A TARGET TO BEGIN",
                AsciiCanvas.INK_DIM);
        caption.setText(Ui.upper("idle " + AsciiCanvas.BULLET + " no compute reserved"));
        setAccessibleText("Breach viewport. No breach open.");
    }

    /**
     * The header strip: what is being broken into, and what it is costing.
     *
     * <p>The right-hand mark is {@code OWN RIG} on a miner crack, and that is not decoration. §4.1
     * and Invariant I9 make a crack generate <b>zero heat on every outcome, including failure</b> —
     * it is the one attempt a player can lose repeatedly at no cost, which is why the tutorial is
     * built on it. A player who can see that from the viewport does not have to remember it.
     */
    private void head() {
        int tier = snapshot.difficultyTier() == null ? 0 : snapshot.difficultyTier().tier();
        String state = snapshot.liveOrDormant() == null ? "" : snapshot.liveOrDormant().name();
        String mark = snapshot.minerCrack() ? "OWN RIG" : "NOISE " + snapshot.noiseSoFar();
        String suffix = " " + AsciiCanvas.BULLET + " T" + tier
                + " " + AsciiCanvas.BULLET + " " + state;

        // ⚠ Clip the LABEL, never the assembled line. Truncating the whole string is what the first
        // build did, and a long hostname ate the tier and the LIVE/DORMANT flag from the right —
        // losing exactly the two fields the player is deciding on and keeping the one they already
        // know. The designator is the field that can afford to lose characters.
        int room = INTERIOR_WIDTH - 2 - mark.length() - 2;
        String label = Ui.upper(nullToEmpty(snapshot.targetLabel()));
        int labelRoom = Math.max(0, room - suffix.length());
        if (label.length() > labelRoom) {
            label = label.substring(0, labelRoom).stripTrailing();
        }
        canvas.text(1, INTERIOR_COL + 1, label + suffix, AsciiCanvas.INK_DIM);
        canvas.right(1, INTERIOR_COL, INTERIOR_WIDTH - 1, mark, AsciiCanvas.INK_DIM);
        canvas.rule(2, 0, BOX_COLS, AsciiCanvas.INK_DIM);
    }

    /** The stack of layer bands, layer 0 at the bottom. */
    private void tower() {
        List<BreachLayer> layers = snapshot.layers();
        if (layers.isEmpty()) {
            return;
        }
        int count = layers.size();
        // Bands share the tower evenly and the separators cost one row each. A single-layer target
        // therefore fills the whole frame, which is exactly right: at tier 1 the one layer IS the
        // target, and a small band floating in a tall box would read as a layout fault.
        int bandHeight = Math.max(1, (TOWER_ROWS - (count - 1)) / count);
        // ⚠ The remainder goes to layer 0, at the bottom. Centring the stack instead — which the
        // first build did — leaves a blank interior row between the lowest band and the frame, and
        // a tower floating a row above its own floor reads as a rendering fault rather than as
        // deliberate air. Any spare row belongs to the layer the player is standing on.
        int spare = TOWER_ROWS - (count * bandHeight + (count - 1));

        int bandTop = TOWER_TOP;
        for (int position = 0; position < count; position++) {
            // Screen position 0 is the TOP of the tower, which is the LAST layer.
            BreachLayer layer = layers.get(count - 1 - position);
            int height = bandHeight + (position == count - 1 ? Math.max(0, spare) : 0);
            band(layer, bandTop, height);

            if (position < count - 1) {
                BreachLayer below = layers.get(count - 2 - position);
                boolean live = isActive(layer) || isActive(below);
                canvas.rule(bandTop + height, 0, BOX_COLS, AsciiCanvas.INK_DIM, live);
            }
            bandTop += height + 1;
        }
        // The topmost band borders the header rule rather than a separator of its own, so the frame
        // has to be completed there or the active band is boxed on three sides and open at the top.
        if (isActive(layers.getLast())) {
            canvas.rule(TOWER_TOP - 1, 0, BOX_COLS, AsciiCanvas.INK_DIM, true);
        }
        marker();
    }

    private void band(BreachLayer layer, int bandTop, int bandHeight) {
        LayerOutcome state = layer.state() == null ? LayerOutcome.PENDING : layer.state();
        // ⚠ A LOCKED band's FILL is dim and only its label is alarm — a knowing narrowing of the
        // implementation spec §4.2's table, which gives the whole band alarm ink. Rendered, that is
        // two hundred characters of loss colour on one panel, against §2.1's cap of two alarm
        // elements per screen and §4.0's explicit list of the three places alarm may appear in this
        // feature. The glyph already carries the state — █ is the heaviest fill in the vocabulary
        // and nothing else uses it — so the ration is spent on the word instead, where it is read.
        int ink = state == LayerOutcome.ACTIVE ? AsciiCanvas.INK_LIVE : AsciiCanvas.INK_DIM;
        int labelInk = state == LayerOutcome.LOCKED ? AsciiCanvas.INK_ALARM : ink;

        if (state == LayerOutcome.ACTIVE) {
            // The one moving thing on the panel, and it moves on the one band that is live.
            String texture = textureFor(layer.puzzleClass());
            for (int r = 0; r < bandHeight; r++) {
                // Alternate rows counter-rotate, so the band reads as a surface being scanned rather
                // than as a strip sliding past. One sheet moving as a unit is the raster reading §9
                // rejects, and it is the same argument Substrate's three drift rates answer.
                int phase = (r % 2 == 0) ? step : -step;
                canvas.pattern(bandTop + r, INTERIOR_COL, INTERIOR_WIDTH, texture, phase, ink);
            }
        } else {
            canvas.fill(bandTop, INTERIOR_COL, bandHeight, INTERIOR_WIDTH, fillFor(state), ink);
        }

        // Padded with a space either side so the words separate from the texture they sit in. That
        // padding is doing real work: the label and the fill are the same ink on every settled band
        // — the accent is rationed and INK_LIVE means "this layer is open" — so the only thing
        // holding the two apart is the gap.
        String label = " L" + layer.index()
                + " " + className(layer.puzzleClass())
                + " " + AsciiCanvas.BULLET + " " + word(state) + " ";
        canvas.centre(bandTop + bandHeight / 2, INTERIOR_COL, INTERIOR_WIDTH, label, labelInk);
    }

    /**
     * The travelling scan marker, outside the frame.
     *
     * <p>A triangle wave over the tower's height rather than a loop, so it reverses at the ends
     * instead of jumping back — a marker that teleports reads as a dropped frame.
     */
    private void marker() {
        if (!hasLiveLayer()) {
            return;
        }
        int period = Math.max(1, 2 * (TOWER_ROWS - 1));
        int phase = step % period;
        int offset = phase < TOWER_ROWS ? phase : period - phase;
        canvas.put(TOWER_TOP + offset, MARKER_COL, AsciiCanvas.ARROW_LEFT, AsciiCanvas.INK_LIVE);
    }

    /** The plinth. Structure, and the only mark on this panel that carries no reading. */
    private void plinth() {
        for (int col = GAUGE_COL; col + 1 < UiTokens.VIEWPORT_COLS; col += PLINTH_PITCH) {
            canvas.put(PLINTH_ROW, col, AsciiCanvas.DIAG_UP, AsciiCanvas.INK_DIM);
            canvas.put(PLINTH_ROW, col + 1, AsciiCanvas.DIAG_DOWN, AsciiCanvas.INK_DIM);
        }
    }

    /**
     * The strike gauge: one pip per strike still in hand, one dot per strike spent.
     *
     * <p>Counted rather than measured, for {@code CycleGrid}'s reason — "three left" is a number the
     * player acts on and a proportion is not. The words are printed beside it because
     * {@code docs/client/07-accessibility.md} §5.2 does not let meaning rest on a glyph alone.
     */
    private void strikes() {
        BreachLayer layer = snapshot.active().orElse(null);
        if (layer == null) {
            caption();
            return;
        }
        int limit = Math.max(0, layer.strikeLimit());
        int spent = Math.max(0, Math.min(limit, layer.strikes()));
        int left = limit - spent;

        int col = GAUGE_COL;
        for (int i = 0; i < left; i++) {
            canvas.put(STRIKE_ROW, col++, AsciiCanvas.PIP, AsciiCanvas.INK_LIVE);
        }
        for (int i = 0; i < spent; i++) {
            canvas.put(STRIKE_ROW, col++, AsciiCanvas.BULLET, AsciiCanvas.INK_DIM);
        }
        canvas.text(STRIKE_ROW, Math.max(col + 2, GAUGE_TEXT_COL),
                "STRIKES " + left + " OF " + limit + " REMAINING", AsciiCanvas.INK_DIM);
        caption();
    }

    private void caption() {
        var total = snapshot.totalAttention();
        BreachLayer layer = snapshot.active().orElse(null);
        int probes = 0;
        for (BreachLayer each : snapshot.layers()) {
            probes += each.probesUsed();
        }
        String position = layer == null
                ? "resolved"
                : "layer " + (layer.index() + 1) + " of " + snapshot.layers().size()
                        + " " + AsciiCanvas.BULLET + " " + className(layer.puzzleClass()).toLowerCase(Locale.ROOT);
        // Values snap. §7.3 forbids a count-up or an odometer on any numeric readout, without
        // exception, and this string is rebuilt whole on every render rather than eased toward.
        caption.setText(Ui.upper(position
                + " " + AsciiCanvas.BULLET + " attention " + total.spent() + "/" + total.budget()
                + " " + AsciiCanvas.BULLET + " noise " + snapshot.noiseSoFar()
                + " " + AsciiCanvas.BULLET + " " + probes + " probes"
                + " " + AsciiCanvas.BULLET + " " + snapshot.reservedCycles() + " cycles held"));
        setAccessibleText(describe());
    }

    /**
     * The whole panel in words.
     *
     * <p>The tower is glyph weight and position, neither of which reaches assistive technology.
     * {@code docs/client/07-accessibility.md} §5.2 requires a second path for anything whose meaning
     * rests on appearance, and this is it — the same two-path fix {@code ThermoMeter} uses.
     */
    private String describe() {
        StringBuilder out = new StringBuilder("Breach on ")
                .append(nullToEmpty(snapshot.targetLabel()))
                .append(", tier ")
                .append(snapshot.difficultyTier() == null ? 0 : snapshot.difficultyTier().tier())
                .append(snapshot.minerCrack() ? ", your own rig, no heat on any outcome" : "")
                .append(". ");
        for (BreachLayer layer : snapshot.layers()) {
            out.append("Layer ").append(layer.index())
                    .append(' ').append(className(layer.puzzleClass()).toLowerCase(Locale.ROOT))
                    .append(' ').append(word(layer.state()).toLowerCase(Locale.ROOT))
                    .append(". ");
        }
        return out.toString();
    }

    private static boolean isActive(BreachLayer layer) {
        return layer != null && layer.state() == LayerOutcome.ACTIVE;
    }

    private static char fillFor(LayerOutcome state) {
        return switch (state) {
            case CLEARED -> AsciiCanvas.FILL_CLEARED;
            case BYPASSED -> AsciiCanvas.FILL_BYPASSED;
            case LOCKED -> AsciiCanvas.FILL_LOCKED;
            // ACTIVE never reaches here; PENDING is the sealed weight and the heaviest dim fill,
            // because an unopened layer is the most present thing on the panel.
            default -> AsciiCanvas.FILL_SEALED;
        };
    }

    private static String word(LayerOutcome state) {
        return switch (state == null ? LayerOutcome.PENDING : state) {
            case PENDING -> "SEALED";
            case ACTIVE -> "ACTIVE";
            case CLEARED -> "CLEARED";
            case BYPASSED -> "BYPASSED";
            case LOCKED -> "LOCKED";
        };
    }

    /**
     * The repeating fill for a live band.
     *
     * <p>⚠ <b>Every period here is ODD, and that is load-bearing rather than aesthetic.</b>
     * {@link #band} counter-rotates alternate rows by using {@code +step} and {@code -step}, which
     * is what makes the band read as a surface being scanned instead of a sheet sliding past. On an
     * EVEN period that is a no-op: {@code floorMod(step, 2)} and {@code floorMod(-step, 2)} are
     * equal for every integer {@code step}, so the two phases land on the same character and every
     * row renders identically.
     *
     * <p>All three textures were originally two characters wide, so the counter-rotation never once
     * did anything and the viewport rendered as a flat uniform hatch — measured by rendering it, not
     * by reading it, because in source the intent is stated plainly in a comment and looks correct.
     * A three-character period makes the two phases genuinely differ and gives the fill some
     * internal structure besides.
     */
    private static String textureFor(PuzzleClass puzzleClass) {
        if (puzzleClass == PuzzleClass.LOGIC) {
            // A tumbler bus: the doubled rail is the "two readings of one lock" the class is about.
            return AsciiCanvas.TEXTURE_LOGIC + "" + AsciiCanvas.BOX_H + " ";
        }
        if (puzzleClass == PuzzleClass.TRAVERSAL) {
            // Opposed diagonals plus a gap — a weave, which is what a routed graph should feel like.
            return AsciiCanvas.TEXTURE_TRAVERSAL + " ";
        }
        // Port stubs hanging off a bus: the shape Enumeration is literally reading.
        return AsciiCanvas.TEXTURE_ENUMERATION + "" + AsciiCanvas.BOX_H + " ";
    }

    private static String className(PuzzleClass puzzleClass) {
        return puzzleClass == null ? "UNKNOWN" : puzzleClass.name();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** The current frame as text. Test seam — see {@code AsciiCanvas.frame()}. */
    public String frame() {
        return canvas.frame();
    }

    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // AutoCloseable's checked exception; unsubscribing cannot fail.
            }
            ticker = null;
        }
    }
}
