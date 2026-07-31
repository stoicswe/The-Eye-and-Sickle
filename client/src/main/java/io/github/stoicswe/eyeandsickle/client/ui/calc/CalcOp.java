package io.github.stoicswe.eyeandsickle.client.ui.calc;

import java.util.Locale;
import java.util.Optional;

/**
 * The two-operand operations, arithmetic and bitwise, in one table.
 *
 * <h2>Why the bitwise half is the point</h2>
 *
 * Arithmetic is the part every calculator already has. What a programmer's calculator is <em>for</em>
 * is the other half: masking a field out of a value, setting a flag, testing whether a bit is on,
 * moving a byte into position. Those are the operations that actually appear in a protocol header, a
 * permission bitmask and a colour value, and they are the ones that have no notation on a phone
 * keypad. Putting both halves in one enum, applied through one method, is what keeps the calculator
 * honest about them being the same kind of thing — every one of them is a function of two registers.
 *
 * <h2>Every result goes back through the word size</h2>
 *
 * {@link #apply} masks on the way out, without exception. A multiply that overflows a byte wraps,
 * exactly as it would in the machine, and the player sees the wrap rather than a widened result.
 * That is {@code docs/education/01-foundations.md} §3.8's whole subject and it is not an edge case
 * here — it is the default behaviour of the tool.
 *
 * <h2>⚠ Shifts do not wrap their count, and that is a deliberate divergence</h2>
 *
 * Java's {@code <<} takes the shift distance modulo 64 for a {@code long}, and x86 masks it to 6 bits
 * ({@code 5} for 32-bit operands), so {@code 1 << 64} is {@code 1} on both — which is a real and
 * genuinely surprising hardware behaviour, and also one that would make this calculator lie about
 * what shifting means. Here a shift of the word width or more gives <b>zero</b>, which is the
 * arithmetic answer, and the divergence is written down in the {@code CAVEATS} of the shipped page
 * rather than hidden. A tool that teaches has to be allowed to be simpler than the hardware; it is
 * not allowed to be quietly different from it.
 */
public enum CalcOp {
    ADD("+", "add"),
    SUB("-", "subtract"),
    MUL("*", "multiply"),
    DIV("/", "divide"),
    MOD("MOD", "remainder"),

    AND("AND", "keep only the bits set in both — a mask"),
    OR("OR", "set every bit that is on in either"),
    XOR("XOR", "set the bits that differ — its own inverse"),

    SHL("LSH", "shift left, zeros in at the bottom"),
    SHR("RSH", "shift right, zeros in at the top"),
    SAR("ASR", "shift right, the sign bit copied in at the top"),
    ROL("ROL", "rotate left — bits leaving the top come back at the bottom"),
    ROR("ROR", "rotate right");

    private final String label;
    private final String gloss;

    CalcOp(String label, String gloss) {
        this.label = label;
        this.gloss = gloss;
    }

    /** What the key says. Uppercase already, because §6 wants every label that way. */
    public String label() {
        return label;
    }

    /** One line on what it does, for the key's tooltip. */
    public String gloss() {
        return gloss;
    }

    /** The arithmetic half — the keys that go in the number pad's right-hand column. */
    public boolean arithmetic() {
        return ordinal() <= MOD.ordinal();
    }

    /** Whether the right operand is a bit count rather than a value. */
    public boolean shift() {
        return this == SHL || this == SHR || this == SAR || this == ROL || this == ROR;
    }

    /**
     * Applies the operation and folds the result back into {@code word}.
     *
     * @param signed whether {@code DIV}, {@code MOD} and {@code SAR} read their operands as two's
     *     complement. The other operations do not care — bitwise operations have no sign, and add,
     *     subtract and multiply produce the same bits either way, which is itself worth knowing
     * @throws ArithmeticException on division or remainder by zero. Thrown rather than returned as a
     *     sentinel because there is no value that means "no answer": zero is an answer
     */
    public long apply(long left, long right, WordSize word, boolean signed) {
        long a = word.mask(left);
        long b = word.mask(right);
        long result =
                switch (this) {
                    case ADD -> a + b;
                    case SUB -> a - b;
                    case MUL -> a * b;
                    case DIV -> divide(a, b, word, signed);
                    case MOD -> remainder(a, b, word, signed);
                    case AND -> a & b;
                    case OR -> a | b;
                    case XOR -> a ^ b;
                    case SHL -> shiftLeft(a, count(b, word));
                    case SHR -> shiftRight(a, count(b, word));
                    case SAR -> arithmeticShiftRight(word.signed(a), count(b, word), word);
                    case ROL -> rotate(a, (int) (count(b, word) % word.bits()), word, true);
                    case ROR -> rotate(a, (int) (count(b, word) % word.bits()), word, false);
                };
        return word.mask(result);
    }

    private static long divide(long a, long b, WordSize word, boolean signed) {
        if (b == 0) {
            throw new ArithmeticException("divide by zero");
        }
        return signed ? word.signed(a) / word.signed(b) : Long.divideUnsigned(a, b);
    }

    private static long remainder(long a, long b, WordSize word, boolean signed) {
        if (b == 0) {
            throw new ArithmeticException("remainder by zero");
        }
        return signed ? word.signed(a) % word.signed(b) : Long.remainderUnsigned(a, b);
    }

    /**
     * A shift count, as an unsigned number of bits.
     *
     * <p>Read unsigned deliberately: a "shift by −1" is not a right shift, it is a shift by a very
     * large number, and on a register that is what it would be. Clamped at the word width because
     * everything past that is the same answer.
     */
    private static long count(long b, WordSize word) {
        long unsigned = word.mask(b);
        return Long.compareUnsigned(unsigned, word.bits()) >= 0 ? word.bits() : unsigned;
    }

    private static long shiftLeft(long a, long by) {
        return by >= 64 ? 0 : a << by;
    }

    private static long shiftRight(long a, long by) {
        return by >= 64 ? 0 : a >>> by;
    }

    /**
     * Right shift with the sign bit copied in — division by a power of two that stays negative.
     *
     * <p>Takes the sign-extended value so the copied-in bits are the register's own sign rather than
     * bit 63 of a value that has been sitting in a wider Java {@code long} all along. Shifting a
     * narrow negative number without extending it first is the single easiest way to get this wrong.
     */
    private static long arithmeticShiftRight(long extended, long by, WordSize word) {
        if (by >= word.bits()) {
            return extended < 0 ? -1L : 0L;
        }
        return extended >> by;
    }

    private static long rotate(long a, int by, WordSize word, boolean left) {
        int bits = word.bits();
        if (by == 0) {
            return a;
        }
        long value = word.mask(a);
        int amount = left ? by : bits - by;
        return word.mask((value << amount) | (value >>> (bits - amount)));
    }

    /** The operator a typed token names — the symbol or the word, either way. */
    public static Optional<CalcOp> byToken(String token) {
        String t = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "+" -> Optional.of(ADD);
            case "-" -> Optional.of(SUB);
            case "*", "x" -> Optional.of(MUL);
            case "/" -> Optional.of(DIV);
            case "%", "mod" -> Optional.of(MOD);
            case "&", "and" -> Optional.of(AND);
            case "|", "or" -> Optional.of(OR);
            case "^", "xor" -> Optional.of(XOR);
            case "<<", "lsh", "shl" -> Optional.of(SHL);
            case ">>>", "rsh", "shr" -> Optional.of(SHR);
            case ">>", "asr", "sar" -> Optional.of(SAR);
            case "rol" -> Optional.of(ROL);
            case "ror" -> Optional.of(ROR);
            default -> Optional.empty();
        };
    }
}
