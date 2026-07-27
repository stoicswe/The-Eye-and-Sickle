package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.LogicProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The Logic board: a rack of tumblers to compose a guess on, and the responses to deduce from.
 *
 * <h2>{@code KEYSPACE 4096 → 37} is the class's diegetic soul</h2>
 *
 * It is the one number on the panel that tells the player their deduction is working, and it is a
 * real figure the engine computes by filtering the candidate set against every response so far —
 * the same filter that decides whether a guess is <em>provably inconsistent</em> and therefore costs
 * a strike. Printing it turns Mastermind from "keep trying" into "watch the space collapse", which
 * is the difference between guessing and deducing, and it is why an inconsistent probe can be
 * punished fairly: the player was shown the size of what they knew.
 *
 * <h2>Response weight, not response colour</h2>
 *
 * Exact hits render as a run of {@code █} and partials as a run of {@code ▌}, <b>and the numbers are
 * printed beside them</b>. The reflexive choice here is a row of {@code ● ○} pegs, which is what
 * every physical Mastermind set uses — and neither character exists in IBM Plex Mono, so it would
 * fail {@code GlyphCoverageTest} at build time or, worse, fall back to a host font with a different
 * advance width and shear the whole column. Full and half blocks carry the same "one is worth more
 * than the other" reading at a glance and are in the bundled face.
 *
 * <h2>⚠ The one animation here has a reduced-motion trap in it</h2>
 *
 * A tumbler whose symbol changes steps through three intermediate alphabet symbols before landing —
 * discrete steps on {@link UiTokens#BREACH_PULSE_MS}, nothing tweens, and it reads as a lock
 * settling. But {@link Pulse#animate} classifies that as decoration, and under reduced motion a
 * decorative subscription fires <b>once at construction and never again</b>. A tumbler mid-settle
 * would therefore stop on an intermediate symbol and stay there — showing the player a guess they
 * did not compose, which is worse than any amount of missing animation. {@link #show} asks
 * {@link Pulse#reducedMotion()} and lands the symbol immediately when it is on.
 *
 * <h2>Salting is drawn because it is a real counter</h2>
 *
 * {@code docs/design/06-tools-and-consumables.md} §1 makes the Rainbow Table hard-countered by
 * salting, and the implementation spec turns that into a refusal that costs zero attention. A player
 * who can read {@code SALTED} before spending the action learns the counter rather than being taxed
 * by it, which is the whole point of publishing a hard counter.
 */
public final class TumblerRack extends VBox {

    /** How many intermediate symbols a tumbler steps through before it lands. */
    private static final int SETTLE_STEPS = 3;

    /** {@code ┌───┐} — five characters, one of them the symbol. */
    private static final int TUMBLER_COLS = 5;

    /** A position with no symbol chosen yet. Deliberately the same "unknown" mark the comb uses. */
    private static final String UNSET = String.valueOf(AsciiCanvas.BULLET);

    private static final double COL_SEQUENCE = 26;

    private static final double COL_GUESS = 110;

    private static final double COL_EXACT = 78;

    private static final double COL_PARTIAL = 78;

    private final HBox rack = new HBox(UiTokens.SPACE_2);
    private final Label salt = Ui.label("");
    private final Label keyspace = Ui.value("");
    private final VBox facts = new VBox(UiTokens.SPACE_1);
    private final VBox history = new VBox();

    private final List<Label> tumblers = new ArrayList<>();

    private List<String> alphabet = List.of();
    private String[] displayed = new String[0];
    private String[] target = new String[0];
    private int[] settling = new int[0];
    private boolean[] locked = new boolean[0];

    private BiConsumer<Integer, Integer> onCycle = (position, delta) -> {};
    private AutoCloseable ticker;

    public TumblerRack() {
        super(UiTokens.SPACE_3);
        getStyleClass().add("es-tumbler");
        salt.getStyleClass().add("es-tumbler-salt");
        keyspace.getStyleClass().add("es-tumbler-keyspace");
        rack.setAlignment(Pos.TOP_LEFT);

        VBox readout = new VBox(UiTokens.SPACE_1, salt, keyspace);
        readout.setAlignment(Pos.TOP_LEFT);
        HBox top = Ui.row(UiTokens.SPACE_6, rack, Ui.spacer(), readout);
        top.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(top, facts, historyHead(), history);
        ticker = Pulse.shared().animate(UiTokens.BREACH_PULSE_MS, this::advance);
    }

    /** Called with (position, ±1) when the player cycles a tumbler. */
    public void setOnCycle(BiConsumer<Integer, Integer> handler) {
        this.onCycle = handler == null ? (position, delta) -> {} : handler;
    }

    public void show(LogicBoard board) {
        if (board == null) {
            rack.getChildren().clear();
            tumblers.clear();
            facts.getChildren().clear();
            history.getChildren().clear();
            salt.setText("");
            keyspace.setText("");
            return;
        }
        this.alphabet = board.alphabet();

        int length = Math.max(0, board.length());
        boolean resized = displayed.length != length;
        if (resized) {
            displayed = new String[length];
            target = new String[length];
            settling = new int[length];
            locked = new boolean[length];
        }
        for (int i = 0; i < length; i++) {
            String known = value(board.known(), i);
            String draft = value(board.draft(), i);
            locked[i] = !known.isBlank();
            String want = locked[i] ? known : (draft.isBlank() ? UNSET : draft);
            target[i] = want;
            if (displayed[i] == null) {
                displayed[i] = want;
            } else if (!displayed[i].equals(want)) {
                // ⚠ See the class comment. A decorative subscription does not fire under reduced
                // motion, so scheduling a settle there would strand the tumbler on an intermediate.
                if (Pulse.shared().reducedMotion() || locked[i] || alphabet.isEmpty()) {
                    displayed[i] = want;
                    settling[i] = 0;
                } else {
                    settling[i] = SETTLE_STEPS;
                }
            }
        }

        buildRack();
        salt.setText(board.salted() ? "SALTED" : "UNSALTED");
        salt.getStyleClass().removeAll("es-tumbler-salted", "es-tumbler-unsalted");
        salt.getStyleClass().add(board.salted() ? "es-tumbler-salted" : "es-tumbler-unsalted");
        // Values snap (§7.3). The arrow is the collapse, and the two figures are the collapse's size.
        keyspace.setText("KEYSPACE " + board.keyspace()
                + " " + AsciiCanvas.ARROW_RIGHT + " " + board.candidatesRemaining());
        keyspace.setAccessibleText("Keyspace " + board.keyspace()
                + " candidates at the start, " + board.candidatesRemaining()
                + " still consistent with every response so far.");

        buildFacts(board);
        buildHistory(board);
        setAccessibleText(describe(board));
    }

    private void buildRack() {
        rack.getChildren().clear();
        tumblers.clear();
        for (int i = 0; i < displayed.length; i++) {
            rack.getChildren().add(tumblerColumn(i));
        }
    }

    private VBox tumblerColumn(int position) {
        Label up = caret(AsciiCanvas.ARROW_UP, position, 1);
        Label down = caret(AsciiCanvas.ARROW_DOWN, position, -1);
        Label cell = plain(boxFor(position));
        cell.getStyleClass().addAll("es-tumbler-cell",
                locked[position] ? "es-tumbler-locked" : "es-tumbler-draft", "es-focusable");
        tumblers.add(cell);

        String words = locked[position]
                ? "Position " + (position + 1) + " is known to be " + displayed[position]
                : "Position " + (position + 1) + ", currently " + displayed[position]
                        + ". Up and down cycle the symbol.";
        Tooltip tip = new Tooltip(words);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        Tooltip.install(cell, tip);
        cell.setAccessibleText(words);

        if (!locked[position]) {
            cell.setFocusTraversable(true);
            Cursors.shared().clickable(cell);
            // Click cycles forward; the alphabet wraps, so a player with neither a scroll wheel nor
            // a keyboard can still reach every symbol. The carets are the direct route.
            cell.setOnMouseClicked(e -> onCycle.accept(position, 1));
            cell.setOnScroll(e -> onCycle.accept(position, e.getDeltaY() > 0 ? 1 : -1));
            cell.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.UP) {
                    onCycle.accept(position, 1);
                    e.consume();
                } else if (e.getCode() == KeyCode.DOWN) {
                    onCycle.accept(position, -1);
                    e.consume();
                }
            });
        }

        VBox column = new VBox(0, up, cell, down);
        column.setAlignment(Pos.TOP_CENTER);
        return column;
    }

    private Label caret(char glyph, int position, int delta) {
        Label caret = plain(centre(String.valueOf(glyph)));
        caret.getStyleClass().add("es-tumbler-caret");
        if (locked[position]) {
            caret.setText(centre(" "));
            return caret;
        }
        Cursors.shared().clickable(caret);
        caret.setOnMouseClicked(e -> onCycle.accept(position, delta));
        caret.setAccessibleText(delta > 0 ? "Next symbol" : "Previous symbol");
        return caret;
    }

    /**
     * A known position gets the heavy frame.
     *
     * <p>The same vocabulary {@link LatticeMap} uses for "you are here": heavy means <em>settled,
     * right now, this one</em>. A position the Rainbow Table or the Credential Harvester established
     * is no longer part of the guess, and the frame says so before the colour does.
     */
    private String boxFor(int position) {
        boolean known = locked[position];
        char tl = known ? AsciiCanvas.HEAVY_TL : AsciiCanvas.LIGHT_TL;
        char tr = known ? AsciiCanvas.HEAVY_TR : AsciiCanvas.LIGHT_TR;
        char bl = known ? AsciiCanvas.HEAVY_BL : AsciiCanvas.LIGHT_BL;
        char br = known ? AsciiCanvas.HEAVY_BR : AsciiCanvas.LIGHT_BR;
        char horizontal = known ? AsciiCanvas.HEAVY_H : AsciiCanvas.LIGHT_H;
        char vertical = known ? AsciiCanvas.HEAVY_V : AsciiCanvas.LIGHT_V;
        String run = String.valueOf(horizontal).repeat(TUMBLER_COLS - 2);
        return tl + run + tr + "\n"
                + vertical + " " + displayed[position] + " " + vertical + "\n"
                + bl + run + br;
    }

    /**
     * One settle step per tick.
     *
     * <p>Holds when nothing is settling — {@code CoreCage}'s idle rule again. A rack that keeps
     * flicking with no pending change would be the one thing D-6 does not permit inside a live
     * breach: motion that is not a readout.
     */
    private void advance() {
        boolean any = false;
        for (int i = 0; i < settling.length; i++) {
            if (settling[i] <= 0) {
                continue;
            }
            any = true;
            settling[i]--;
            // Walks toward the landing symbol rather than stepping randomly, so the three frames
            // read as one movement arriving rather than as three unrelated flickers.
            displayed[i] = settling[i] == 0 ? target[i] : offset(target[i], settling[i]);
        }
        if (!any) {
            return;
        }
        for (int i = 0; i < tumblers.size() && i < displayed.length; i++) {
            tumblers.get(i).setText(boxFor(i));
        }
    }

    /** Advances exactly one settle step. Test seam. */
    public void tick() {
        advance();
    }

    private String offset(String landing, int by) {
        if (alphabet.isEmpty()) {
            return landing;
        }
        int at = Math.max(0, alphabet.indexOf(landing));
        return alphabet.get(Math.floorMod(at + by, alphabet.size()));
    }

    private void buildFacts(LogicBoard board) {
        facts.getChildren().clear();
        for (String fact : board.facts()) {
            Label line = Ui.small(AsciiCanvas.BULLET + " " + fact);
            line.getStyleClass().add("es-tumbler-fact");
            line.setWrapText(true);
            facts.getChildren().add(line);
        }
    }

    private HBox historyHead() {
        HBox head = new HBox(UiTokens.SPACE_2,
                cell("NO", COL_SEQUENCE),
                cell("GUESS", COL_GUESS),
                cell("EXACT", COL_EXACT),
                cell("PARTIAL", COL_PARTIAL),
                grow(cell("", 0)));
        head.getStyleClass().addAll("es-row-head", "es-tumbler-head");
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    private void buildHistory(LogicBoard board) {
        history.getChildren().clear();
        if (board.history().isEmpty()) {
            history.getChildren().add(io.github.stoicswe.eyeandsickle.client.ui.widgets.Note.empty(
                    "No responses yet. Compose a guess on the rack and probe it."));
            return;
        }
        for (LogicProbe probe : board.history()) {
            history.getChildren().add(historyRow(probe));
        }
    }

    private HBox historyRow(LogicProbe probe) {
        String exact = String.valueOf(AsciiCanvas.BAR_FULL).repeat(Math.max(0, probe.exact()))
                + " " + probe.exact();
        // ⚠ A volley's partial is -1, not 0 — the Fuzzer buys breadth by returning only exact counts
        // (05 §4). Printing 0 would be a lie the player would deduce from, so the column carries a
        // rule instead and the flag says which tool produced it.
        String partial = probe.volley() || probe.partial() < 0
                ? String.valueOf(AsciiCanvas.LIGHT_H)
                : String.valueOf(AsciiCanvas.BAR_HALF).repeat(probe.partial()) + " " + probe.partial();

        HBox row = new HBox(UiTokens.SPACE_2,
                cell(String.format(Locale.ROOT, "%02d", probe.sequence()), COL_SEQUENCE),
                cell(String.join(" ", probe.guess()), COL_GUESS),
                cell(exact, COL_EXACT),
                cell(partial, COL_PARTIAL),
                grow(cell(flag(probe), 0)));
        row.getStyleClass().add("es-tumbler-row");
        if (probe.inconsistent()) {
            row.getStyleClass().add("es-tumbler-inconsistent");
        } else if (probe.volley()) {
            row.getStyleClass().add("es-tumbler-volley");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setAccessibleText(describe(probe));
        return row;
    }

    private static String flag(LogicProbe probe) {
        if (probe.inconsistent()) {
            return "INCONSISTENT";
        }
        return probe.volley() ? "VOLLEY" : "";
    }

    private static String describe(LogicProbe probe) {
        return "Guess " + String.join(" ", probe.guess())
                + ": " + probe.exact() + " exact"
                + (probe.volley() ? ", volley, partials withheld" : ", " + probe.partial() + " partial")
                + (probe.inconsistent() ? ". Provably inconsistent with earlier responses; cost a strike." : ".");
    }

    private String describe(LogicBoard board) {
        return "Logic lock, " + board.length() + " positions, "
                + board.alphabet().size() + " symbols, "
                + (board.salted() ? "salted" : "unsalted") + ". "
                + board.candidatesRemaining() + " candidates remain of " + board.keyspace() + ". "
                + board.history().size() + " responses so far.";
    }

    private static String value(List<String> list, int index) {
        return list != null && index < list.size() && list.get(index) != null ? list.get(index) : "";
    }

    private static String centre(String glyph) {
        int pad = (TUMBLER_COLS - glyph.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + glyph;
    }

    private static Label cell(String text, double width) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("es-tumbler-col");
        if (width > 0) {
            label.setMinWidth(width);
            label.setPrefWidth(width);
            label.setMaxWidth(width);
        }
        return label;
    }

    private static Label grow(Label label) {
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private static Label plain(String text) {
        Label label = new Label(text);
        label.setWrapText(false);
        return label;
    }

    /** The symbols currently shown on the rack. Test seam for the settle animation. */
    public List<String> face() {
        return java.util.Arrays.stream(displayed).map(s -> s == null ? "" : s).toList();
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
