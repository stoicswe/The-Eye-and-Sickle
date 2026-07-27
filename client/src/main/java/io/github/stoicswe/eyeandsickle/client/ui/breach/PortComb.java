package io.github.stoicswe.eyeandsickle.client.ui.breach;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.BandReading;
import io.github.stoicswe.eyeandsickle.protocol.game.EnumerationBoard;
import io.github.stoicswe.eyeandsickle.protocol.game.PortSlot;
import io.github.stoicswe.eyeandsickle.protocol.game.PortState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The Enumeration board: a comb of port slots, a banner, and the brackets a sweep leaves behind.
 *
 * <h2>The banner is the puzzle, not the label</h2>
 *
 * Architect's decision D-8 makes the service banner Enumeration's human-read step. The target's role
 * — {@code EDGE RELAY}, {@code STORAGE ARRAY}, {@code AUTH BROKER}, {@code MEDIA CACHE} —
 * <em>constrains which slots can be open</em>, by a rule that is printed nowhere and is learned by
 * playing. A player who reads the banner eliminates whole bands without paying to probe them; a
 * fixed heuristic cannot, which is Invariant I10 made mechanical rather than asserted. So the banner
 * is drawn first, at value weight, above the comb — not tucked into a footer where it reads as
 * decoration.
 *
 * <h2>Two characters per slot, and why they are not one</h2>
 *
 * A single character per slot makes a sixteen-slot comb four characters shorter and unreadable: at
 * one cell there is no room for the frame to separate slots, and {@code ░} beside {@code ▒} beside
 * {@code ·} becomes a texture rather than four countable states. Two cells inside a {@code │}
 * divider gives each slot a visible box, which is what makes "how many bands have I actually swept"
 * answerable by looking.
 *
 * <p>The four states differ in <b>weight</b>, not hue: {@code ██} open, {@code ▒▒} filtered,
 * {@code ░░} closed, {@code ··} unknown. That ramp is the same one the viewport's layer bands use,
 * so a player learns it once. It survives greyscale, which matters because this board is where the
 * player is counting.
 *
 * <h2>Motion means "not yet established", and nothing else</h2>
 *
 * D-6 permits motion inside a live breach only where the motion <em>is</em> the readout. The only
 * moving thing here is the unknown slots, which alternate {@code ··} and {@code :·} on
 * {@link UiTokens#BREACH_TICKER_MS} — slower than the viewport's scan, because it is peripheral for
 * the whole layer and fast flicker in the corner of the eye is the most tiring thing an interface
 * can do. Every slot the player has established is <b>dead still</b>. Under reduced motion the
 * flicker never fires and the unknown slots simply rest on {@code ··}, which is the state they mean.
 *
 * <h2>What a sweep leaves on the board</h2>
 *
 * {@code sweep} returns a <em>count</em>, never a location, and the bracket run {@code └── 2 ──┘}
 * under the covered slots is that count sitting where it applies. Readings are de-duplicated by band
 * and packed onto as few rows as will hold them without overlapping, so a player who swept the same
 * band twice sees one bracket rather than a stack — the second sweep bought no information and the
 * board should not pretend it did.
 */
public final class PortComb extends VBox {

    /** Characters per slot: one divider plus the two-cell state. */
    private static final int SLOT_WIDTH = 3;

    private final Label banner = Ui.value("");
    private final Label bannerNote = Ui.small("");
    private final Label topRule = plain();
    private final HBox slotRow = new HBox();
    private final Label bottomRule = plain();
    private final Label declaredRule = plain();
    private final Label indexRule = plain();
    private final VBox brackets = new VBox();
    private final Label openList = Ui.small("");
    private final Label declaredList = Ui.small("");

    private final List<Label> slots = new ArrayList<>();

    private EnumerationBoard board;
    private IntConsumer onSlot = index -> {};
    private boolean flickerOn;
    private AutoCloseable ticker;

    public PortComb() {
        super(UiTokens.SPACE_2);
        getStyleClass().add("es-port-comb");
        banner.getStyleClass().add("es-port-banner");
        bannerNote.setWrapText(true);
        openList.setWrapText(true);
        declaredList.setWrapText(true);

        slotRow.setAlignment(Pos.CENTER_LEFT);
        slotRow.setSpacing(0);

        VBox comb = new VBox(0, topRule, slotRow, bottomRule, declaredRule, indexRule);
        comb.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(
                Ui.row(UiTokens.SPACE_4, Ui.label("Banner"), banner),
                bannerNote,
                comb,
                brackets,
                openList,
                declaredList);

        ticker = Pulse.shared().animate(UiTokens.BREACH_TICKER_MS, this::flicker);
    }

    public void setOnSlot(IntConsumer handler) {
        this.onSlot = handler == null ? index -> {} : handler;
    }

    /** Rebuilds the comb. Null-safe: a null board leaves an empty, silent panel. */
    public void show(EnumerationBoard next) {
        this.board = next;
        if (next == null) {
            slots.clear();
            slotRow.getChildren().clear();
            brackets.getChildren().clear();
            topRule.setText("");
            bottomRule.setText("");
            declaredRule.setText("");
            indexRule.setText("");
            banner.setText("");
            bannerNote.setText("");
            openList.setText("");
            declaredList.setText("");
            return;
        }
        banner.setText(Ui.upper(next.banner()));
        bannerNote.setText(next.bannerNote());
        buildComb(next);
        buildBrackets(next);
        buildLists(next);
        setAccessibleText(describe(next));
    }

    private void buildComb(EnumerationBoard next) {
        List<PortSlot> ports = next.ports();
        int count = ports.size();

        // Rebuilt whole rather than diffed: a board this small costs nothing to rebuild and a diff
        // is a cache that can disagree with the model — see CycleGrid.show for the same call.
        slots.clear();
        slotRow.getChildren().clear();

        for (int i = 0; i < count; i++) {
            slots.add(slotCell(ports.get(i)));
            slotRow.getChildren().add(slots.get(i));
        }
        // The closing divider, so the last slot has a right-hand wall like every other one.
        Label tail = plain();
        tail.setText(String.valueOf(AsciiCanvas.LIGHT_V));
        tail.getStyleClass().add("es-port");
        slotRow.getChildren().add(tail);

        StringBuilder top = new StringBuilder().append(AsciiCanvas.LIGHT_TL);
        StringBuilder bottom = new StringBuilder().append(AsciiCanvas.LIGHT_BL);
        StringBuilder index = new StringBuilder(" ");
        StringBuilder declared = new StringBuilder(" ");
        for (int i = 0; i < count; i++) {
            top.append(AsciiCanvas.LIGHT_H).append(AsciiCanvas.LIGHT_H)
                    .append(i == count - 1 ? AsciiCanvas.LIGHT_TR : AsciiCanvas.LIGHT_T_DOWN);
            bottom.append(AsciiCanvas.LIGHT_H).append(AsciiCanvas.LIGHT_H)
                    .append(i == count - 1 ? AsciiCanvas.LIGHT_BR : AsciiCanvas.LIGHT_T_UP);
            index.append(String.format(Locale.ROOT, "%02d", i)).append(' ');
            // The declared marks sit on their own row rather than replacing the slot glyph, so a
            // player composing a set never loses sight of what they believe each slot IS. The two
            // facts are independent and the board draws them independently.
            boolean isDeclared = next.declared().contains(i);
            declared.append(isDeclared ? AsciiCanvas.UNDERSCORE_HI : ' ')
                    .append(isDeclared ? AsciiCanvas.UNDERSCORE_HI : ' ')
                    .append(' ');
        }
        topRule.setText(top.toString());
        bottomRule.setText(bottom.toString());
        indexRule.setText(index.toString());
        declaredRule.setText(declared.toString());
        topRule.getStyleClass().add("es-port");
        bottomRule.getStyleClass().add("es-port");
        indexRule.getStyleClass().add("es-port");
        declaredRule.getStyleClass().addAll("es-port", "es-port-declared");
    }

    private Label slotCell(PortSlot slot) {
        Label cell = plain();
        cell.getStyleClass().addAll("es-port", stateClass(slot.state()), "es-focusable");
        cell.setText(AsciiCanvas.LIGHT_V + glyphFor(slot.state()));

        String service = slot.service().isBlank() ? "" : " " + slot.service();
        String words = String.format(Locale.ROOT, "SLOT %02d %s%s",
                slot.index(), stateWord(slot.state()), Ui.upper(service));
        Tooltip tip = new Tooltip(words);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        Tooltip.install(cell, tip);
        // The glyph is the only visual channel carrying the state, so the words go down the second
        // path — docs/client/07 §5.2, the same two-path fix ThermoMeter uses for its heat band.
        cell.setAccessibleText(words);

        cell.setFocusTraversable(true);
        Cursors.shared().clickable(cell);
        cell.setOnMouseClicked(e -> onSlot.accept(slot.index()));
        cell.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                onSlot.accept(slot.index());
                e.consume();
            }
        });
        return cell;
    }

    /**
     * Lays the sweep brackets out under the slots they cover.
     *
     * <p>De-duplicated by band, then packed greedily: a band whose bracket would overlap one already
     * on a row starts a new row. In practice the bands partition the comb and everything lands on
     * one row, but a Side-Channel read or a future overlapping sweep must not draw two counts on top
     * of each other — a wrong number here is worse than no number, because the player will act on it.
     */
    private void buildBrackets(EnumerationBoard next) {
        brackets.getChildren().clear();
        if (next.readings().isEmpty()) {
            return;
        }
        Map<String, BandReading> latest = new LinkedHashMap<>();
        for (BandReading reading : next.readings()) {
            latest.put(reading.fromSlot() + ":" + reading.toSlot(), reading);
        }

        int width = next.ports().size() * SLOT_WIDTH + 1;
        List<char[]> lines = new ArrayList<>();
        for (BandReading reading : latest.values()) {
            int from = Math.max(0, reading.fromSlot());
            int to = Math.min(next.ports().size() - 1, reading.toSlot());
            if (to < from) {
                continue;
            }
            // ⚠ Offset by one and shortened by one, so the bracket spans the band's GLYPH cells and
            // leaves the │ dividers either side of it visible. Anchoring it on the dividers instead
            // makes two adjacent bands' brackets meet at a single column, and └┘ colliding there
            // reads as a drawing error rather than as two readings.
            int start = from * SLOT_WIDTH + 1;
            int span = (to - from + 1) * SLOT_WIDTH - 1;
            char[] target = null;
            for (char[] line : lines) {
                if (isClear(line, start, span)) {
                    target = line;
                    break;
                }
            }
            if (target == null) {
                target = new char[width];
                java.util.Arrays.fill(target, ' ');
                lines.add(target);
            }
            writeBracket(target, start, span, reading.openCount());
        }
        for (char[] line : lines) {
            Label label = plain();
            label.getStyleClass().addAll("es-port", "es-port-reading");
            label.setText(new String(line));
            brackets.getChildren().add(label);
        }
    }

    private static boolean isClear(char[] line, int start, int span) {
        for (int i = start; i < Math.min(line.length, start + span); i++) {
            if (line[i] != ' ') {
                return false;
            }
        }
        return true;
    }

    /** {@code └── 2 ──┘}, centred on the band it covers. */
    private static void writeBracket(char[] line, int start, int span, int count) {
        if (span < 2 || start >= line.length) {
            return;
        }
        String middle = " " + count + " ";
        int inner = span - 2;
        int pad = Math.max(0, inner - middle.length());
        int left = pad / 2;

        StringBuilder run = new StringBuilder().append(AsciiCanvas.LIGHT_BL);
        run.append(String.valueOf(AsciiCanvas.LIGHT_H).repeat(left));
        run.append(inner >= middle.length() ? middle : "");
        run.append(String.valueOf(AsciiCanvas.LIGHT_H).repeat(Math.max(0, pad - left)));
        run.append(AsciiCanvas.LIGHT_BR);
        for (int i = 0; i < run.length() && start + i < line.length; i++) {
            line[start + i] = run.charAt(i);
        }
    }

    /**
     * The two lists under the comb.
     *
     * <p>Services live here rather than in the comb because a slot is two characters wide and a
     * service name is not. The open list is the payoff of every probe the player has paid for, and
     * the declared list is the set they are about to submit — {@code declare} is cheap and being
     * wrong is not, so the set is printed in full before it is sent.
     */
    private void buildLists(EnumerationBoard next) {
        StringBuilder open = new StringBuilder();
        for (PortSlot slot : next.ports()) {
            if (slot.state() == PortState.OPEN) {
                if (!open.isEmpty()) {
                    open.append(' ').append(AsciiCanvas.BULLET).append(' ');
                }
                open.append(String.format(Locale.ROOT, "%02d", slot.index()));
                if (!slot.service().isBlank()) {
                    open.append(' ').append(slot.service());
                }
            }
        }
        String knownTotal = next.knownOpenTotal() < 0
                ? "UNKNOWN"
                : Integer.toString(next.knownOpenTotal());
        openList.setText(Ui.upper("open found: ")
                + (open.isEmpty() ? "NONE YET" : open.toString())
                + "   " + AsciiCanvas.BULLET + "   " + Ui.upper("open total: ") + knownTotal);

        StringBuilder declared = new StringBuilder();
        for (int index : next.declared()) {
            if (!declared.isEmpty()) {
                declared.append(' ');
            }
            declared.append(String.format(Locale.ROOT, "%02d", index));
        }
        declaredList.setText(Ui.upper("declaring: ")
                + (declared.isEmpty() ? "NOTHING — MARK SLOTS FIRST" : declared.toString()));
    }

    /**
     * The unknown-slot flicker.
     *
     * <p>Only the unknown slots, and only their second character. A whole-cell swap would read as
     * the slot changing state, which is the one thing it must not say.
     */
    private void flicker() {
        flickerOn = !flickerOn;
        if (board == null) {
            return;
        }
        List<PortSlot> ports = board.ports();
        for (int i = 0; i < slots.size() && i < ports.size(); i++) {
            if (ports.get(i).state() != PortState.UNKNOWN) {
                continue;
            }
            slots.get(i).setText(AsciiCanvas.LIGHT_V
                    + (flickerOn ? AsciiCanvas.PORT_UNKNOWN_ALT : AsciiCanvas.PORT_UNKNOWN));
        }
    }

    private static String glyphFor(PortState state) {
        return switch (state == null ? PortState.UNKNOWN : state) {
            case OPEN -> AsciiCanvas.PORT_OPEN;
            case CLOSED -> AsciiCanvas.PORT_CLOSED;
            case FILTERED -> AsciiCanvas.PORT_FILTERED;
            case UNKNOWN -> AsciiCanvas.PORT_UNKNOWN;
        };
    }

    private static String stateClass(PortState state) {
        return switch (state == null ? PortState.UNKNOWN : state) {
            case OPEN -> "es-port-open";
            case CLOSED -> "es-port-closed";
            case FILTERED -> "es-port-filtered";
            case UNKNOWN -> "es-port-unknown";
        };
    }

    private static String stateWord(PortState state) {
        return state == null ? "UNKNOWN" : state.name();
    }

    private String describe(EnumerationBoard next) {
        int unknown = 0;
        int open = 0;
        for (PortSlot slot : next.ports()) {
            if (slot.state() == PortState.UNKNOWN) {
                unknown++;
            } else if (slot.state() == PortState.OPEN) {
                open++;
            }
        }
        return "Port comb. Banner " + next.banner() + ". " + next.ports().size() + " slots, "
                + open + " established open, " + unknown + " still unknown, "
                + next.declared().size() + " marked for declaration.";
    }

    private static Label plain() {
        Label label = new Label();
        label.setWrapText(false);
        return label;
    }

    /** The comb as text, top rule to index row. Test seam for the column arithmetic. */
    public String frame() {
        StringBuilder out = new StringBuilder();
        out.append(topRule.getText()).append('\n');
        for (Label slot : slots) {
            out.append(slot.getText());
        }
        out.append(AsciiCanvas.LIGHT_V).append('\n');
        out.append(bottomRule.getText()).append('\n');
        out.append(declaredRule.getText()).append('\n');
        out.append(indexRule.getText()).append('\n');
        for (javafx.scene.Node node : brackets.getChildren()) {
            out.append(((Label) node).getText()).append('\n');
        }
        return out.toString();
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
