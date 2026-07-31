package io.github.stoicswe.eyeandsickle.client.ui.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The programmer's calculator, as a state machine with no toolkit anywhere in it.
 *
 * <h2>What it is for</h2>
 *
 * Everything else in this client hands the player numbers in the machine's own notation — a compute
 * budget in cycles, an address, a task id, a digest, a byte count. A player who cannot move between
 * hex, decimal and bits is reading those as opaque strings. This is the tool that makes them
 * legible, and it is a <b>client-side utility with no game state in it at all</b>: it spends nothing,
 * reads nothing through {@code GameSession}, changes nothing, and is not gated. That is deliberate.
 * Invariant <b>I14</b> is about state a cheater would forge, and there is nothing here to forge —
 * a calculator that charged compute would be a tax on understanding the game.
 *
 * <h2>Bits are the state; every readout is a view of them</h2>
 *
 * There is exactly one value, held as a {@code long} and always masked to the current
 * {@link WordSize}. The four base rows, the bit grid, the byte view and the character view are all
 * derived from it on demand, and nothing is cached. So they cannot disagree: flipping one bit in the
 * grid moves every row, and switching base moves nothing at all. That is the lesson
 * {@code docs/education/01-foundations.md} §3.4 asks for — hex is a notation, not a number — made
 * structural rather than stated.
 *
 * <p><b>Signed is a reading, not a flag on the value.</b> {@link #signed()} changes what the decimal
 * row says and how {@code DIV}, {@code MOD} and {@code ASR} interpret their operands. It does not
 * change a single bit. See {@link WordSize#signed}.
 *
 * <h2>⚠ No operator precedence, deliberately, and it is written on the tin</h2>
 *
 * {@code 2 + 3 * 4} is <b>20</b> here, not 14. Every desk calculator ever built works this way, and
 * an expression tool that quietly applied precedence would be a third thing — neither the keypad the
 * player is pressing nor the language they will type this into later. The shipped {@code calc(1)}
 * page says so in its {@code CAVEATS}, and {@link #evaluate} runs the same left-to-right chain the
 * keys do so the two can never drift.
 *
 * <h2>Not thread-safe, and does not need to be</h2>
 *
 * Every caller is the JavaFX application thread or a test. There is no clock in here, no timer and
 * nothing that settles later — which is also why this class has none of the {@code Instant.now()}
 * hazard that {@code RunningTask} and {@code ComputeRules.spend} carry.
 */
public final class Calculator {

    private Radix radix = Radix.HEX;
    private WordSize word = WordSize.DWORD;
    private boolean signed;

    /** The register. ⚠ Invariant: always already masked to {@link #word}. */
    private long value;

    /** The left operand of a pending operation. Only meaningful while {@link #pending} is set. */
    private long left;

    private CalcOp pending;

    /**
     * An operator was the last key pressed, so the next digit starts a new number.
     *
     * <p>Also true immediately after {@code =} and after anything that replaces the register
     * wholesale (a base change, a width change, a bit flip). One flag rather than three, because
     * three flags describing the same "what does the next digit mean" question is how a calculator
     * ends up with a key sequence that produces a different number on Tuesdays.
     */
    private boolean awaitingOperand = true;

    /** Non-empty only while something has gone wrong, and cleared by the next key. */
    private String error = "";

    // ── What is on screen ────────────────────────────────────────────────────────────────────

    public Radix radix() {
        return radix;
    }

    public WordSize word() {
        return word;
    }

    public boolean signed() {
        return signed;
    }

    /** The register, masked. The one piece of state; everything else is derived from it. */
    public long value() {
        return value;
    }

    /** The register read as two's complement, whatever {@link #signed()} currently says. */
    public long signedValue() {
        return word.signed(value);
    }

    public Optional<CalcOp> pending() {
        return Optional.ofNullable(pending);
    }

    public String error() {
        return error;
    }

    /** The value in the base currently selected, grouped and padded. */
    public String display() {
        return radix.format(value, word, signed);
    }

    /** The value in one particular base — the four rows are four calls to this. */
    public String row(Radix in) {
        return in.format(value, word, signed);
    }

    /**
     * The bits, <b>index 0 is bit 0</b>, least significant first.
     *
     * <p>Least significant first because that is the numbering every document the player will meet
     * uses — "bit 7 of the flags byte" counts from the right. The grid draws it reversed; the array
     * is not the drawing order and must not be reordered to match one.
     */
    public boolean[] bits() {
        boolean[] out = new boolean[word.bits()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((value >>> i) & 1L) == 1L;
        }
        return out;
    }

    /** How many bits are set. The operation a permission mask, a popcount and a Hamming weight share. */
    public int setBits() {
        return Long.bitCount(word.mask(value));
    }

    /** Zeros above the highest set bit, within this width. {@code word.bits()} when the value is zero. */
    public int leadingZeros() {
        return value == 0 ? word.bits() : Long.numberOfLeadingZeros(word.mask(value)) - (64 - word.bits());
    }

    /** Zeros below the lowest set bit — how many times it divides by two. */
    public int trailingZeros() {
        return value == 0 ? word.bits() : Long.numberOfTrailingZeros(word.mask(value));
    }

    /**
     * The bytes as they would sit in memory, most significant first, space separated.
     *
     * <p>Paired with {@link #littleEndian()} on screen rather than offered alone: the two differ for
     * every value wider than a byte, and seeing them side by side is the cheapest possible statement
     * of the thing {@code docs/education/02-computer-architecture.md} exists partly to teach — that a
     * number has no byte order, only a <em>stored</em> number does.
     */
    public String bigEndian() {
        return byteText(true);
    }

    public String littleEndian() {
        return byteText(false);
    }

    /**
     * The bytes as printable characters, one per byte, most significant first.
     *
     * <p>A dot for anything outside printable ASCII, which is the convention every hex dump uses and
     * the reason a player will recognise it when {@code xxd} does the same. It is not a claim that
     * the byte is meaningless — see {@code character-encoding(7)}: a byte over 127 is a perfectly
     * good byte and simply is not one character on its own in UTF-8.
     */
    public String characters() {
        StringBuilder out = new StringBuilder();
        for (int i = word.bytes() - 1; i >= 0; i--) {
            int b = (int) ((value >>> (i * 8)) & 0xFFL);
            out.append(b >= 32 && b < 127 ? (char) b : '.');
        }
        return out.toString();
    }

    private String byteText(boolean mostSignificantFirst) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < word.bytes(); i++) {
            long b = (value >>> (i * 8)) & 0xFFL;
            String hex = String.format(Locale.ROOT, "%02X", b);
            if (mostSignificantFirst) {
                parts.addFirst(hex);
            } else {
                parts.add(hex);
            }
        }
        return String.join(" ", parts);
    }

    // ── Keys ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Appends a digit, if it is one in the current base.
     *
     * @return false when the key does not belong to this base or the register is already full, so a
     *     caller can decide whether that deserves a sound. Never throws — a keyboard handler that
     *     could throw on a stray keypress would take the window with it
     */
    public boolean digit(char c) {
        if (!radix.accepts(c)) {
            return false;
        }
        error = "";
        String current = awaitingOperand ? "" : radix.raw(value, word, false);
        // Leading zeros are padding rather than typed digits — a hex DWORD displays as eight
        // characters whether the player has pressed one key or eight, and treating that padding as
        // entry would make the register full before it had anything in it.
        String trimmed = current.replaceFirst("^0+(?=.)", "");
        if (!awaitingOperand && trimmed.length() >= radix.maxDigits(word)) {
            return false;
        }
        String next = (awaitingOperand ? "" : trimmed) + Character.toUpperCase(c);
        Optional<Long> parsed = radix.parse(next);
        if (parsed.isEmpty()) {
            return false;
        }
        value = word.mask(parsed.get());
        awaitingOperand = false;
        return true;
    }

    /** Drops the lowest digit in the current base — the backspace a wrong keypress needs. */
    public void backspace() {
        error = "";
        if (awaitingOperand) {
            value = 0;
            return;
        }
        String current = radix.raw(value, word, false).replaceFirst("^0+(?=.)", "");
        String shorter = current.length() <= 1 ? "0" : current.substring(0, current.length() - 1);
        value = word.mask(radix.parse(shorter).orElse(0L));
    }

    /** Clears the register but keeps a pending operation — the {@code CE} of a desk calculator. */
    public void clearEntry() {
        value = 0;
        error = "";
        awaitingOperand = true;
    }

    /** Clears everything, including the pending operation. Base, width and sign are settings, and stay. */
    public void clear() {
        value = 0;
        left = 0;
        pending = null;
        error = "";
        awaitingOperand = true;
    }

    /**
     * Presses an operator.
     *
     * <p>Pressing two operators in a row <b>replaces</b> the first rather than evaluating with the
     * register twice — the behaviour of every physical calculator, and the one that makes a mis-hit
     * recoverable without clearing.
     */
    public void operator(CalcOp op) {
        if (op == null) {
            return;
        }
        error = "";
        if (awaitingOperand && pending != null) {
            pending = op;
            return;
        }
        if (pending != null && !compute()) {
            return;
        }
        left = value;
        pending = op;
        awaitingOperand = true;
    }

    /** Completes the pending operation. Nothing pending is not an error; it is a no-op. */
    public void equals() {
        error = "";
        if (pending == null) {
            return;
        }
        compute();
        pending = null;
        awaitingOperand = true;
    }

    /**
     * Runs the pending operation, leaving the answer in the register.
     *
     * @return false when it refused, in which case {@link #error} says why and the register is
     *     untouched — a divide by zero must not silently produce a zero
     */
    private boolean compute() {
        try {
            value = pending.apply(left, value, word, signed);
            awaitingOperand = true;
            return true;
        } catch (ArithmeticException e) {
            error = e.getMessage();
            pending = null;
            awaitingOperand = true;
            return false;
        }
    }

    // ── One-operand keys ─────────────────────────────────────────────────────────────────────

    /** Every bit inverted. On a signed reading this is also {@code -x - 1}, which is worth noticing. */
    public void not() {
        replace(~value);
    }

    /** Two's-complement negation: invert and add one. The same bits {@code 0 - x} would give. */
    public void negate() {
        replace(~value + 1);
    }

    /**
     * Reverses the byte order.
     *
     * <p>The one key here that has no arithmetic meaning at all, and the reason it earns a place:
     * byte order is not a property of a number, it is a property of how a number was <em>written
     * down</em>, and nothing explains that as quickly as pressing this and watching the decimal row
     * change while the set of bytes does not. A no-op on a byte, correctly.
     */
    public void swapBytes() {
        long swapped = 0;
        for (int i = 0; i < word.bytes(); i++) {
            long b = (value >>> (i * 8)) & 0xFFL;
            swapped |= b << ((word.bytes() - 1 - i) * 8);
        }
        replace(swapped);
    }

    /** Flips one bit. Out-of-range indices are ignored rather than throwing — see {@link #digit}. */
    public void toggleBit(int index) {
        if (index < 0 || index >= word.bits()) {
            return;
        }
        replace(value ^ (1L << index));
    }

    private void replace(long next) {
        value = word.mask(next);
        error = "";
        awaitingOperand = true;
    }

    // ── Settings ─────────────────────────────────────────────────────────────────────────────

    /**
     * Changes the base the entry and the main readout are in.
     *
     * <p>The value does not move — only its notation does, which is the entire claim the tool makes.
     * The next digit starts a new number, because half-typed digits in the old base would be
     * ambiguous in the new one.
     */
    public void setRadix(Radix next) {
        if (next != null) {
            radix = next;
            awaitingOperand = true;
        }
    }

    /**
     * Changes the register width.
     *
     * <p>⚠ <b>This truncates, on purpose.</b> Narrowing a value that does not fit throws the high
     * bits away exactly as storing it in a narrower variable would, and that is the demonstration —
     * {@code docs/education/01-foundations.md} §3.7. It applies to the pending left operand too, so
     * a half-finished sum cannot smuggle a wider number through the change.
     */
    public void setWord(WordSize next) {
        if (next == null) {
            return;
        }
        word = next;
        value = word.mask(value);
        left = word.mask(left);
        awaitingOperand = true;
    }

    /** Switches the decimal reading between unsigned and two's complement. Changes no bits. */
    public void setSigned(boolean twosComplement) {
        signed = twosComplement;
    }

    /** Loads a value directly — how the window restores what it had and how a test sets one up. */
    public void set(long next) {
        replace(next);
        error = "";
    }

    /**
     * Loads a value as a <b>completed operand</b> — what typing its digits would have left behind.
     *
     * <p>⚠ Not the same as {@link #set}, and collapsing the two silently changes every answer with
     * more than one operator in it. {@code set} leaves {@link #awaitingOperand} true, which is right
     * for restoring a register but tells {@link #operator} that no number has been entered since the
     * last operator — so the next operator <em>replaces</em> the pending one instead of evaluating
     * it. Written with {@code set}, {@code 2 + 3 * 4} came out as 8: the {@code +} was discarded and
     * the chain silently became {@code 2 * 4}.
     */
    private void enter(long next) {
        value = word.mask(next);
        error = "";
        awaitingOperand = false;
    }

    // ── The same chain, from a string ────────────────────────────────────────────────────────

    /**
     * Evaluates a written expression with the <b>same</b> left-to-right chain the keys use.
     *
     * <p>Exists for client pillar <b>C1</b>: everything a tool window can do must be reachable from
     * the terminal. It shares this class rather than parsing into its own evaluator precisely so the
     * two surfaces cannot come to different answers — the failure mode C1 is hardest to notice is
     * not a missing command, it is a command that quietly disagrees with its window.
     *
     * <p>Literals carry their base: {@code 0x1f}, {@code 0b1011}, {@code 0o755}, or plain decimal.
     * Operators are the symbols or the words ({@code and}, {@code xor}, {@code rol}), and {@code ~}
     * before a literal inverts it. Underscores inside a number are ignored, as they are in Java.
     *
     * @return the loaded calculator on success, or empty with the reason in {@code error}
     */
    public static Result evaluate(String expression, WordSize word, boolean signed) {
        Calculator calc = new Calculator();
        calc.setWord(word);
        calc.setSigned(signed);
        List<String> tokens = tokenize(expression);
        if (tokens.isEmpty()) {
            return new Result(false, calc, "nothing to evaluate");
        }
        boolean expectingValue = true;
        for (String token : tokens) {
            if (expectingValue) {
                boolean invert = token.startsWith("~");
                Optional<Long> literal = literal(invert ? token.substring(1) : token);
                if (literal.isEmpty()) {
                    return new Result(false, calc, "not a number: " + token);
                }
                calc.enter(invert ? ~literal.get() : literal.get());
                expectingValue = false;
                continue;
            }
            Optional<CalcOp> op = CalcOp.byToken(token);
            if (op.isEmpty()) {
                return new Result(false, calc, "not an operator: " + token);
            }
            calc.operator(op.get());
            if (!calc.error().isEmpty()) {
                return new Result(false, calc, calc.error());
            }
            expectingValue = true;
        }
        if (expectingValue) {
            return new Result(false, calc, "expression ends with an operator");
        }
        calc.equals();
        return calc.error().isEmpty() ? new Result(true, calc, "") : new Result(false, calc, calc.error());
    }

    /** What {@link #evaluate} came back with. */
    public record Result(boolean ok, Calculator calculator, String error) {}

    /**
     * Splits on whitespace after padding the symbol operators out, so {@code 0xff&0x0f} works.
     *
     * <p>⚠ The multi-character shifts are rewritten to their <b>word</b> forms, longest first, and
     * the order is load-bearing: pad {@code >} on its own and {@code >>>} has already become three
     * unrecoverable tokens. Rewriting to words rather than to spaced symbols also means the
     * single-symbol pass below cannot reach them and split them again.
     */
    private static List<String> tokenize(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        String padded =
                expression.replace(">>>", " RSH ").replace(">>", " ASR ").replace("<<", " LSH ");
        for (String symbol : List.of("+", "*", "/", "%", "&", "|", "^")) {
            padded = padded.replace(symbol, " " + symbol + " ");
        }
        // ⚠ Minus is NOT padded with the rest. It is the only symbol that is also part of a literal
        // ("-1"), and padding it turns every negative number into a dangling operator.
        padded = padded.replaceAll("(?<=[\\w)])\\s*-\\s*", " - ");
        List<String> tokens = new ArrayList<>();
        for (String token : padded.trim().split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** A literal in whatever base its prefix names, decimal when it has none. */
    private static Optional<Long> literal(String token) {
        String trimmed = token.replace("_", "").trim();
        boolean minus = trimmed.startsWith("-");
        String text = minus ? trimmed.substring(1) : trimmed;
        // ⚠ Not `prefixed.map(r -> r.parse(...))`. `parse` already returns an Optional, and mapping
        // over one gives an Optional<Optional<Long>> that is never empty — which reports every
        // malformed literal as a successful parse of nothing.
        Radix radix = Radix.byPrefix(text).orElse(Radix.DEC);
        String digits = text.substring(radix.prefix().length());
        return radix.parse(digits).map(v -> minus ? -v : v);
    }
}
