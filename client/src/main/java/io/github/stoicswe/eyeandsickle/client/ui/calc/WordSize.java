package io.github.stoicswe.eyeandsickle.client.ui.calc;

import java.util.Optional;

/**
 * How many bits the calculator is working in — 8, 16, 32 or 64.
 *
 * <h2>Why a word size is the first control on a programmer's calculator</h2>
 *
 * An ordinary calculator has one number line. A machine does not: it has a register of a fixed width,
 * and every operation on it is <em>modulo</em> that width. {@code 255 + 1} is {@code 256} in
 * arithmetic and {@code 0} in a byte, and a player who has not seen that happen has not understood
 * why a length field wraps or why a counter resets. {@code docs/education/01-foundations.md} §3.7
 * ({@code bit-width(7)}) and §3.8 ({@code integer-overflow(7)}) both name it as the misconception to
 * attack, and the fastest way to attack it is to let someone do it on purpose and watch.
 *
 * <p>So the width is not a display preference here. {@link #mask} is applied to the result of every
 * operation, which means narrowing the word <em>truncates the value you are holding</em> — visibly,
 * in every base at once. That is the lesson, not a bug.
 *
 * <h2>Real names, because they are the ones the player will meet again</h2>
 *
 * {@code BYTE} / {@code WORD} / {@code DWORD} / {@code QWORD} is the x86 and Windows-API vocabulary,
 * and it is the vocabulary a debugger, a hex editor and a struct definition will use at them later.
 * It is also, in the honest ledger {@code docs/education/00} §1.4 asks for, <b>not universal</b>: a
 * "word" is 16 bits here and 32 bits on ARM's own documentation and 64 bits in a lot of C code. The
 * label the player sees is therefore the bit count, which is unambiguous, and the traditional name is
 * offered beside it as the thing to recognise rather than the thing to trust.
 */
public enum WordSize {

    /** 8 bits. Where overflow is easy to reach on purpose, which is why it is offered at all. */
    BYTE(8, "byte"),

    /** 16 bits. */
    WORD(16, "word"),

    /** 32 bits. The default: wide enough for an address or a colour, narrow enough to read as bits. */
    DWORD(32, "dword"),

    /** 64 bits. The whole register, and the width Java's {@code long} actually is. */
    QWORD(64, "qword");

    private final int bits;
    private final String traditionalName;

    WordSize(int bits, String traditionalName) {
        this.bits = bits;
        this.traditionalName = traditionalName;
    }

    public int bits() {
        return bits;
    }

    /** How many whole bytes wide. */
    public int bytes() {
        return bits / 8;
    }

    /** The x86 name. See the class comment on why it is offered rather than used as the label. */
    public String traditionalName() {
        return traditionalName;
    }

    /** The label the controls carry: the bit count, which means the same thing everywhere. */
    public String label() {
        return String.valueOf(bits);
    }

    /**
     * The value, with everything above this width thrown away.
     *
     * <p>⚠ The 64-bit case is special-cased and must stay so: {@code 1L << 64} is {@code 1} in Java,
     * not {@code 0} — the shift distance is taken modulo 64 — so the obvious
     * {@code (1L << bits) - 1} produces a mask of {@code 0} for a {@code QWORD} and silently zeroes
     * every value the calculator holds.
     */
    public long mask(long value) {
        return bits == 64 ? value : value & ((1L << bits) - 1L);
    }

    /**
     * The same bits read as a two's-complement signed number.
     *
     * <p>This is the whole of what "signed" means: the bits do not change, the <em>reading</em> does.
     * {@code 0xFF} is 255 and it is also −1, and which one it is depends entirely on what the code
     * looking at it decided. {@code docs/education/01-foundations.md} §3.7's misconception is exactly
     * this — that a number "is" signed — so the calculator never stores a sign, only a reading.
     */
    public long signed(long value) {
        long masked = mask(value);
        if (bits == 64) {
            return masked;
        }
        long signBit = 1L << (bits - 1);
        return (masked & signBit) == 0 ? masked : masked - (1L << bits);
    }

    /** The largest value this width can hold, read as unsigned. */
    public long max() {
        return mask(-1L);
    }

    /** Whether the top bit is set — the bit that decides the signed reading. */
    public boolean negative(long value) {
        return (mask(value) & (1L << (bits - 1))) != 0;
    }

    public static Optional<WordSize> ofBits(int bits) {
        for (WordSize size : values()) {
            if (size.bits == bits) {
                return Optional.of(size);
            }
        }
        return Optional.empty();
    }
}
