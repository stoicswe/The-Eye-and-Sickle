package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.calc.CalcOp;
import io.github.stoicswe.eyeandsickle.client.ui.calc.Calculator;
import io.github.stoicswe.eyeandsickle.client.ui.calc.Radix;
import io.github.stoicswe.eyeandsickle.client.ui.calc.WordSize;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.KeyValue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The calculator window — one register, shown four ways and as its bits.
 *
 * <h2>Why the game has one at all</h2>
 *
 * Every other window in this client hands the player numbers in the machine's notation: an address,
 * a cycle count, a digest, a byte figure, a task id. A player who cannot move between hex, decimal
 * and bits reads all of those as opaque strings — and the teaching layer's own subject
 * ({@code docs/education/01-foundations.md}, domain 01) is precisely that they are not opaque. A
 * pocket calculator does not help, because the operations that matter here — mask, test, shift,
 * swap the byte order — are not on one.
 *
 * <p>It stands in for {@code bc}, {@code printf %x} and the calculator every operating system ships
 * in a programmer mode, and it names those in the window catalogue for the same cheap-teaching
 * reason every other window names its analogue.
 *
 * <h2>⚠ It touches no game state, and that is the design rather than an omission</h2>
 *
 * This is the only tool window that does not take a {@link
 * io.github.stoicswe.eyeandsickle.client.session.GameSession}. It spends no compute, costs no
 * ethecoin, sits behind no gate and cannot be lost. Invariant <b>I14</b> is about state a cheater
 * would forge and there is nothing here to forge — the answer to {@code 0xFF + 1} is not the
 * server's opinion. A calculator that charged compute would be a tax on understanding the game, and
 * a calculator behind a gate would be a paywall on the same.
 *
 * <h2>One value, and every readout derived from it</h2>
 *
 * {@link Calculator} holds a single {@code long} and nothing else. The four base rows, the bit grid,
 * the byte rows and the character row are all recomputed from it in {@link #repaint}, every time,
 * with nothing cached. So they cannot disagree — flipping a bit in the grid moves all four bases at
 * once, which is the entire point being made. The rule is the same one the map window follows for
 * its two views: one read, one instance, every surface.
 *
 * <h2>The keyboard is the real interface</h2>
 *
 * The keys are on screen because a calculator with no keys is a puzzle, but anybody who uses this
 * twice will type at it. Digits, {@code a}–{@code f}, the operator symbols, {@code Enter},
 * {@code Backspace} and {@code Escape} all work, and the handler is a <b>bubbling handler on the
 * root rather than a filter</b> — a filter would run before the focused key chip's own
 * {@code ENTER}/{@code SPACE} handler and swallow it, which would break keyboard activation of every
 * control in the window.
 */
public final class CalcView {

    private CalcView() {}

    /** Bits per row in the grid. A wider row stops being scannable; a narrower one wastes height. */
    private static final int BITS_PER_ROW = 16;

    /** Bits per visual group, matching hex: one group is one hex digit, so the rows line up. */
    private static final int BITS_PER_GROUP = 4;

    public static Region create() {
        Calculator calc = new Calculator();

        VBox root = new VBox(UiTokens.SPACE_4);
        root.getStyleClass().addAll("es-calc", "es-body-pad");
        root.setFocusTraversable(true);

        Runnable[] repaint = new Runnable[1];

        // ---------------------------------------------------------------- the readout
        Label display = new Label();
        display.getStyleClass().add("es-calc-display");
        Label status = new Label();
        status.getStyleClass().add("es-calc-status");

        // ---------------------------------------------------------------- modes
        Map<Radix, BreachView.Chip> radixKeys = new EnumMap<>(Radix.class);
        HBox radixRow = new HBox(UiTokens.SPACE_2);
        radixRow.setAlignment(Pos.CENTER_LEFT);
        radixRow.getChildren().add(Ui.label("Base"));
        for (Radix in : Radix.values()) {
            BreachView.Chip chip = key(in.label());
            chip.setAccessibleText("Show and enter numbers in base " + in.base() + ".");
            tip(chip, baseTip(in));
            chip.onInvoke(() -> {
                calc.setRadix(in);
                repaint[0].run();
            });
            radixKeys.put(in, chip);
            radixRow.getChildren().add(chip);
        }

        Map<WordSize, BreachView.Chip> wordKeys = new EnumMap<>(WordSize.class);
        HBox wordRow = new HBox(UiTokens.SPACE_2);
        wordRow.setAlignment(Pos.CENTER_LEFT);
        wordRow.getChildren().add(Ui.label("Width"));
        for (WordSize size : WordSize.values()) {
            BreachView.Chip chip = key(size.label());
            chip.setAccessibleText("Work in " + size.bits() + " bits, also called a "
                    + size.traditionalName() + ".");
            tip(chip, "A " + size.bits() + "-bit register" + " (" + size.traditionalName() + ").\n\n"
                    + "Every result is folded back into this width, so narrowing it throws away the "
                    + "bits that no longer fit — which is exactly what storing a value in a narrower "
                    + "variable does. Largest value it holds: 0x"
                    + Long.toHexString(size.max()).toUpperCase(Locale.ROOT) + ".");
            chip.onInvoke(() -> {
                calc.setWord(size);
                repaint[0].run();
            });
            wordKeys.put(size, chip);
            wordRow.getChildren().add(chip);
        }

        BreachView.Chip signKey = key("Signed");
        signKey.setAccessibleText("Read the decimal row as a two's complement signed number.");
        tip(signKey,
                "Read the decimal row as two's complement.\n\n"
                        + "It changes no bits at all — only what they are taken to mean. 0xFF is 255 "
                        + "and it is also -1, and which one it is was decided by whatever code is "
                        + "looking at it. Division, remainder and ASR follow this setting too.");
        signKey.onInvoke(() -> {
            calc.setSigned(!calc.signed());
            repaint[0].run();
        });

        HBox modes = new HBox(UiTokens.SPACE_5, radixRow, wordRow, signKey);
        modes.setAlignment(Pos.CENTER_LEFT);

        // ---------------------------------------------------------------- the four rows
        Map<Radix, Label> rows = new EnumMap<>(Radix.class);
        VBox baseRows = new VBox(UiTokens.SPACE_1);
        for (Radix in : Radix.values()) {
            Label name = Ui.label(in.label());
            name.getStyleClass().add("es-calc-rowkey");
            name.setMinWidth(34);
            Label figure = new Label();
            figure.getStyleClass().add("es-calc-row");
            rows.put(in, figure);
            HBox line = new HBox(UiTokens.SPACE_3, name, figure);
            line.setAlignment(Pos.CENTER_LEFT);
            baseRows.getChildren().add(line);
        }

        // ---------------------------------------------------------------- the bit grid
        VBox grid = new VBox(UiTokens.SPACE_1);
        grid.setAccessibleText(
                "The bits of the current value, most significant first. Click one to flip it.");

        // ---------------------------------------------------------------- the keys
        VBox keypad = keypad(calc, repaint);

        // ---------------------------------------------------------------- derived facts
        KeyValue setBits = KeyValue.of("Set bits", "0");
        KeyValue zeros = KeyValue.of("Lead/trail 0", "0 / 0");
        KeyValue bigEndian = KeyValue.of("Bytes BE", "");
        KeyValue littleEndian = KeyValue.of("Bytes LE", "");
        KeyValue chars = KeyValue.of("Chars", "");
        setBits.setAccessibleText("How many bits are set. The same count a popcount instruction returns.");
        zeros.setAccessibleText("Zeros above the highest set bit, and below the lowest.");
        bigEndian.setAccessibleText("The bytes stored most significant first.");
        littleEndian.setAccessibleText("The same bytes stored least significant first.");
        chars.setAccessibleText(
                "Each byte as a printable character, a dot where it is not one. The convention every "
                        + "hex dump uses.");
        HBox facts = new HBox(UiTokens.SPACE_5, setBits, zeros, bigEndian, littleEndian, chars);
        facts.setAlignment(Pos.CENTER_LEFT);

        Label note = Ui.small(
                "Type digits, a-f, the operator symbols, Enter for =, Backspace and Escape. "
                        + "Keys apply left to right with no precedence, like every desk calculator: "
                        + "2 + 3 * 4 is 20. Click any bit to flip it.");
        note.setWrapText(true);

        // ---------------------------------------------------------------- assembly
        //
        // Only the area below the readout scrolls. The value and the mode controls are chrome — the
        // same argument the map window's server strip makes: a readout you can scroll away from is a
        // readout you will scroll away from, and then press a key against.
        VBox body = new VBox(UiTokens.SPACE_5, baseRows, grid, keypad, facts, note);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        root.getChildren().addAll(display, status, modes, scroll);

        repaint[0] = () -> {
            display.setText(calc.display());
            display.setAccessibleText(spoken(calc));

            String pending = calc.pending().map(op -> op.label() + " pending").orElse("");
            status.setText(calc.error().isEmpty() ? pending : Ui.upper(calc.error()));
            status.getStyleClass().remove("es-calc-status-error");
            if (!calc.error().isEmpty()) {
                status.getStyleClass().add("es-calc-status-error");
            }

            for (Radix in : Radix.values()) {
                rows.get(in).setText(calc.row(in));
                mark(radixKeys.get(in), in == calc.radix());
            }
            for (WordSize size : WordSize.values()) {
                mark(wordKeys.get(size), size == calc.word());
            }
            mark(signKey, calc.signed());
            signKey.setText(calc.signed() ? "SIGNED" : "UNSIGNED");

            rebuildGrid(grid, calc, repaint[0]);

            setBits.set(calc.setBits() + " of " + calc.word().bits());
            zeros.set(calc.leadingZeros() + " / " + calc.trailingZeros());
            bigEndian.set(calc.bigEndian());
            littleEndian.set(calc.littleEndian());
            chars.set(calc.characters());
        };

        // ⚠ A HANDLER, not a filter. See the class comment: a filter on the root runs before the
        // focused chip's own ENTER/SPACE handler and would swallow it, breaking keyboard activation
        // of every control in this window.
        root.setOnKeyPressed(event -> {
            if (handle(calc, event)) {
                event.consume();
                repaint[0].run();
            }
        });
        // Clicking anywhere in the panel puts the keyboard back on it, so a player who clicked a bit
        // and then typed is not silently typing at nothing.
        root.setOnMousePressed(event -> root.requestFocus());

        repaint[0].run();
        return root;
    }

    // ------------------------------------------------------------------ the keypad

    /**
     * Every key, in two blocks: the bitwise operations, then the number pad.
     *
     * <p>Bitwise first and on its own row group, because that is the half of this tool that a pocket
     * calculator does not have and therefore the half worth putting where the eye lands. The digit
     * pad below it is in the conventional 7-8-9 / 4-5-6 / 1-2-3 arrangement, which is muscle memory
     * worth not fighting; {@code A}–{@code F} sit under it rather than beside it so the ten decimal
     * digits keep the shape a player already knows.
     */
    private static VBox keypad(Calculator calc, Runnable[] repaint) {
        VBox pad = new VBox(UiTokens.SPACE_3);

        Map<String, List<CalcOp>> groups = new LinkedHashMap<>();
        groups.put("Bitwise", List.of(CalcOp.AND, CalcOp.OR, CalcOp.XOR));
        groups.put("Shift", List.of(CalcOp.SHL, CalcOp.SHR, CalcOp.SAR, CalcOp.ROL, CalcOp.ROR));
        groups.put("Maths", List.of(CalcOp.ADD, CalcOp.SUB, CalcOp.MUL, CalcOp.DIV, CalcOp.MOD));

        for (Map.Entry<String, List<CalcOp>> group : groups.entrySet()) {
            HBox row = new HBox(UiTokens.SPACE_2);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = Ui.label(group.getKey());
            name.setMinWidth(56);
            row.getChildren().add(name);
            for (CalcOp op : group.getValue()) {
                BreachView.Chip chip = key(op.label());
                chip.setAccessibleText(op.gloss());
                tip(chip, Ui.upper(op.label()) + "\n\n" + op.gloss()
                        + (op.shift() ? "\n\nThe second number is a count of bits, not a value. A "
                                + "count of the register width or more gives zero." : ""));
                chip.onInvoke(() -> {
                    calc.operator(op);
                    repaint[0].run();
                });
                row.getChildren().add(chip);
            }
            pad.getChildren().add(row);
        }

        // The one-operand keys. NOT and NEG are the pair worth having adjacent: on a signed reading
        // they differ by exactly one, which is the whole of two's complement in a single comparison.
        HBox unary = new HBox(UiTokens.SPACE_2);
        unary.setAlignment(Pos.CENTER_LEFT);
        Label unaryName = Ui.label("Single");
        unaryName.setMinWidth(56);
        unary.getChildren().add(unaryName);
        unary.getChildren().addAll(
                action("NOT", "Invert every bit. On a signed reading that is also minus x, minus one.",
                        calc::not, repaint),
                action("NEG", "Two's complement negation: invert the bits and add one.",
                        calc::negate, repaint),
                action("SWAP", "Reverse the byte order. The bytes are the same set; only the order "
                                + "they are stored in changes, which is the whole of endianness.",
                        calc::swapBytes, repaint));
        pad.getChildren().add(unary);

        // Digits. Rows of three in the conventional arrangement, with the hex letters beneath.
        List<String> digitRows = List.of("789", "456", "123", "0AB", "CDEF");
        VBox digits = new VBox(UiTokens.SPACE_2);
        for (String line : digitRows) {
            HBox row = new HBox(UiTokens.SPACE_2);
            row.setAlignment(Pos.CENTER_LEFT);
            for (char c : line.toCharArray()) {
                BreachView.Chip chip = key(String.valueOf(c));
                chip.getStyleClass().add("es-calc-digit");
                chip.setAccessibleText("Digit " + c);
                chip.onInvoke(() -> {
                    calc.digit(c);
                    repaint[0].run();
                });
                row.getChildren().add(chip);
            }
            digits.getChildren().add(row);
        }

        VBox commands = new VBox(UiTokens.SPACE_2,
                action("=", "Complete the pending operation.", calc::equals, repaint),
                action("BKSP", "Drop the lowest digit.", calc::backspace, repaint),
                action("CE", "Clear the number, keep the pending operation.", calc::clearEntry, repaint),
                action("C", "Clear everything. Base, width and sign are settings and stay.",
                        calc::clear, repaint));

        HBox bottom = new HBox(UiTokens.SPACE_5, digits, commands);
        bottom.setAlignment(Pos.TOP_LEFT);
        pad.getChildren().add(bottom);
        return pad;
    }

    // ------------------------------------------------------------------ the bit grid

    /**
     * Redraws the bit grid.
     *
     * <p>Rebuilt rather than updated in place because the <em>number</em> of cells changes with the
     * word size, and a grid that grew but never shrank is how a 64-bit row survives a switch to
     * BYTE and offers the player bits that are not there to click.
     *
     * <p>Cells are clickable but deliberately not focus-traversable: sixty-four tab stops between
     * the controls above and the keys below would make the keyboard route through this window
     * unusable to get a keyboard route into the grid, which is a bad trade. The keyboard route to any
     * single bit already exists — switch to BIN and type it.
     */
    private static void rebuildGrid(VBox grid, Calculator calc, Runnable repaint) {
        grid.getChildren().clear();
        boolean[] bits = calc.bits();
        int perRow = Math.min(BITS_PER_ROW, bits.length);

        for (int top = bits.length - 1; top >= 0; top -= perRow) {
            HBox row = new HBox(UiTokens.SPACE_2);
            row.setAlignment(Pos.CENTER_LEFT);

            Label high = Ui.label(String.valueOf(top));
            high.getStyleClass().add("es-calc-bitindex");
            high.setMinWidth(20);
            high.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(high);

            HBox cells = new HBox(UiTokens.SPACE_3);
            cells.setAlignment(Pos.CENTER_LEFT);
            HBox group = newGroup();
            for (int index = top; index > top - perRow; index--) {
                if ((top - index) > 0 && (top - index) % BITS_PER_GROUP == 0) {
                    cells.getChildren().add(group);
                    group = newGroup();
                }
                group.getChildren().add(bitCell(index, bits[index], calc, repaint));
            }
            cells.getChildren().add(group);
            row.getChildren().add(cells);

            Label low = Ui.label(String.valueOf(top - perRow + 1));
            low.getStyleClass().add("es-calc-bitindex");
            row.getChildren().add(low);

            grid.getChildren().add(row);
        }
    }

    private static HBox newGroup() {
        HBox group = new HBox(UiTokens.SPACE_1);
        group.setAlignment(Pos.CENTER_LEFT);
        return group;
    }

    private static Label bitCell(int index, boolean on, Calculator calc, Runnable repaint) {
        Label cell = new Label(on ? "1" : "0");
        cell.getStyleClass().add(on ? "es-calc-bit-on" : "es-calc-bit");
        cell.setMinWidth(11);
        cell.setAlignment(Pos.CENTER);
        cell.setAccessibleText("Bit " + index + " is " + (on ? "1" : "0") + ". Click to flip it.");
        Tooltip.install(cell, quickTip("BIT " + index + "\n\nWorth "
                + (index < 63 ? "0x" + Long.toHexString(1L << index).toUpperCase(Locale.ROOT) : "the sign bit")
                + ". Click to flip it."));
        Cursors.shared().clickable(cell);
        cell.setOnMouseClicked(event -> {
            event.consume();
            calc.toggleBit(index);
            repaint.run();
        });
        return cell;
    }

    // ------------------------------------------------------------------ the keyboard

    /**
     * Maps a keypress onto the calculator.
     *
     * @return whether the key meant something here, so the caller can leave anything else alone —
     *     a window that consumed every keystroke would eat the deck's own accelerators
     */
    private static boolean handle(Calculator calc, KeyEvent event) {
        if (event.isShortcutDown() || event.isAltDown()) {
            return false;
        }
        switch (event.getCode()) {
            case ENTER -> {
                calc.equals();
                return true;
            }
            case BACK_SPACE -> {
                calc.backspace();
                return true;
            }
            case DELETE -> {
                calc.clearEntry();
                return true;
            }
            // ⚠ NOT Escape. Escape is the deck's pause menu and is installed as a scene filter, so
            // it never reaches here anyway — claiming it would be a control that silently does not
            // work. `C` is the clear key, which is also what the on-screen key says.
            default -> { }
        }
        String typed = event.getText();
        if (typed == null || typed.isEmpty()) {
            return false;
        }
        char c = typed.charAt(0);
        if (c == '=') {
            calc.equals();
            return true;
        }
        // Operators before digits: none of them is a digit in any base offered, but checking in this
        // order means adding a base later cannot silently steal a symbol.
        java.util.Optional<CalcOp> op = CalcOp.byToken(String.valueOf(c));
        if (op.isPresent()) {
            calc.operator(op.get());
            return true;
        }
        if (calc.radix().accepts(c)) {
            return calc.digit(c);
        }
        // `c` clears, matching the on-screen key. Checked after the digit test on purpose: C is a
        // hex digit, and in HEX the digit has to win or the letter row is unusable.
        if (c == 'c' || c == 'C') {
            calc.clear();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private static BreachView.Chip key(String text) {
        return new BreachView.Chip(text, "es-calc-key");
    }

    private static BreachView.Chip action(String text, String help, Runnable act, Runnable[] repaint) {
        BreachView.Chip chip = key(text);
        chip.setAccessibleText(help);
        tip(chip, Ui.upper(text) + "\n\n" + help);
        chip.onInvoke(() -> {
            act.run();
            repaint[0].run();
        });
        return chip;
    }

    /** Marks a toggle as the one in force. Paired with the key's own text, never colour alone. */
    private static void mark(Node node, boolean on) {
        node.getStyleClass().remove("es-calc-key-on");
        if (on) {
            node.getStyleClass().add("es-calc-key-on");
        }
    }

    private static void tip(Node node, String text) {
        Tooltip.install(node, quickTip(text));
    }

    /**
     * A tooltip that keeps up with a pointer moving across a keypad.
     *
     * <p>JavaFX defaults to a one-second delay, which on a grid of small keys means a player
     * scanning them never sees one. Same figures as the deck rail's, and for the same reason.
     */
    private static Tooltip quickTip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setWrapText(true);
        tip.setMaxWidth(320);
        tip.setShowDelay(javafx.util.Duration.millis(220));
        tip.setShowDuration(javafx.util.Duration.seconds(30));
        return tip;
    }

    private static String baseTip(Radix in) {
        String extra = switch (in) {
            case HEX -> "One hex digit is exactly four bits, which is why an address, a colour and a "
                    + "byte are all written this way.";
            case DEC -> "The only row here that says nothing about the bits.";
            case OCT -> "One octal digit is exactly three bits — which is the whole reason Unix file "
                    + "modes look the way they do.";
            case BIN -> "The bits themselves, spelled out.";
        };
        return "BASE " + in.base() + "\n\n" + extra
                + "\n\nSwitching base moves nothing. It is the same value, written differently.";
    }

    /** The readout as a sentence, because a grouped hex figure read aloud is a run of letters. */
    private static String spoken(Calculator calc) {
        List<String> parts = new ArrayList<>();
        for (Radix in : Radix.values()) {
            parts.add(in.label() + " " + calc.row(in).replace(" ", ""));
        }
        return String.join(". ", parts) + ". " + calc.word().bits() + " bits, "
                + (calc.signed() ? "signed" : "unsigned") + ".";
    }
}
