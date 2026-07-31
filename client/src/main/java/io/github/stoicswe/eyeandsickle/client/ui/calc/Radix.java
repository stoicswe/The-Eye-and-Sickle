package io.github.stoicswe.eyeandsickle.client.ui.calc;

import java.util.Locale;
import java.util.Optional;

/**
 * A base the calculator can read and write a number in: 16, 10, 8 or 2.
 *
 * <h2>Why all four are on screen at once</h2>
 *
 * A calculator that shows one base at a time is a base <em>converter</em>, and conversion is the part
 * a player does not need help with. What they need is the thing
 * {@code docs/education/01-foundations.md} §3.4 ({@code hexadecimal(7)}) exists to teach: that hex is
 * not a different number, it is a different <b>notation</b> for the same bits — one hex digit is
 * exactly one nibble, which is why an address, a colour and a byte are all written that way and a
 * decimal reading of any of them tells you nothing you can use. Four simultaneous rows over one bit
 * pattern makes that structural. Flip one bit and every row moves together.
 *
 * <h2>Grouping is a space, everywhere, and it is not decoration</h2>
 *
 * Hex and binary group in <b>fours</b> because four bits is a nibble and one hex digit, so the groups
 * line up between the two rows and a player can read a hex digit off the binary by eye. Octal groups
 * in <b>threes</b> for the same reason — an octal digit is three bits, which is also the whole
 * explanation of why Unix file modes are octal. Decimal groups in threes because that is how decimal
 * is read, and it is the one row where the grouping means nothing about the machine.
 *
 * <p>A space rather than a comma or an underscore: a comma is a decimal point in half of Europe
 * ({@link Locale#ROOT} is used everywhere in this client for exactly that reason), and the separator
 * has to survive being read aloud and pasted into a bug report.
 */
public enum Radix {

    /** Base 16. The default, because it is the base the rest of the machine is written in. */
    HEX(16, "0x", 4, "hex"),

    /** Base 10. The only row here that says nothing about the bits. */
    DEC(10, "", 3, "dec"),

    /** Base 8. Three bits a digit — which is the whole reason file modes look the way they do. */
    OCT(8, "0o", 3, "oct"),

    /** Base 2. The bits themselves, spelled out. */
    BIN(2, "0b", 4, "bin");

    private static final String DIGITS = "0123456789abcdef";

    private final int base;
    private final String prefix;
    private final int groupSize;
    private final String label;

    Radix(int base, String prefix, int groupSize, String label) {
        this.base = base;
        this.prefix = prefix;
        this.groupSize = groupSize;
        this.label = label;
    }

    public int base() {
        return base;
    }

    /** The literal prefix a programmer would write, or empty for decimal. */
    public String prefix() {
        return prefix;
    }

    public String label() {
        return label;
    }

    /** Whether {@code c} is a digit in this base. Case-insensitive, so {@code ff} and {@code FF} both go in. */
    public boolean accepts(char c) {
        int index = DIGITS.indexOf(Character.toLowerCase(c));
        return index >= 0 && index < base;
    }

    /**
     * How many digits it takes to write the widest value of {@code word} in this base.
     *
     * <p>Used to stop the entry buffer growing past what the register can hold — typing a ninth hex
     * digit into a 32-bit word is not an overflow to demonstrate, it is a keystroke that would
     * silently discard the digit the player just pressed.
     */
    public int maxDigits(WordSize word) {
        return switch (this) {
            case HEX -> word.bits() / 4;
            case BIN -> word.bits();
            case OCT -> (word.bits() + 2) / 3;
            // 64 bits unsigned is 20 digits; every narrower word needs fewer, and the ceiling only
            // has to be safe rather than exact, because an over-long decimal entry is caught by the
            // parse rather than by the length.
            case DEC -> word == WordSize.QWORD ? 20 : String.valueOf(word.max()).length();
        };
    }

    /**
     * The value written in this base, grouped, with no prefix.
     *
     * <p>Zero-padded to the register's width for hex, octal and binary — a byte is {@code 0F} and not
     * {@code F}, because the leading zero is the half of the byte that is actually there and hiding it
     * is how a player comes to believe a value has a length. Decimal is not padded, because a decimal
     * number has no width.
     *
     * @param signed whether the decimal row reads the bits as two's complement. Ignored by the other
     *     three bases, which always show what is stored — a negative binary row would be a lie about
     *     the register.
     */
    public String format(long value, WordSize word, boolean signed) {
        if (this == DEC) {
            long reading = signed ? word.signed(value) : word.mask(value);
            String digits = signed ? Long.toString(Math.abs(reading)) : Long.toUnsignedString(word.mask(value));
            // Math.abs(Long.MIN_VALUE) is still Long.MIN_VALUE — the one value whose magnitude does
            // not fit. Its unsigned text is the same digits, so take them from there instead.
            if (signed && reading == Long.MIN_VALUE) {
                digits = Long.toUnsignedString(reading).substring(1);
            }
            return (signed && reading < 0 ? "-" : "") + group(digits);
        }
        String raw = Long.toUnsignedString(word.mask(value), base).toUpperCase(Locale.ROOT);
        int width = maxDigits(word);
        String padded = raw.length() >= width ? raw : "0".repeat(width - raw.length()) + raw;
        return group(padded);
    }

    /** The same, with no grouping — what goes back into the entry buffer. */
    public String raw(long value, WordSize word, boolean signed) {
        return format(value, word, signed).replace(" ", "");
    }

    /**
     * Splits from the right, so the least significant group is always whole.
     *
     * <p>From the right and not the left: the low nibble is the one being compared against something,
     * and a grouping that put the ragged group at the bottom would move every digit's position as the
     * value grew.
     */
    private String group(String digits) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            if (count > 0 && count % groupSize == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
            count++;
        }
        return out.reverse().toString();
    }

    /**
     * Reads a run of digits in this base, or empty when it is not one.
     *
     * <p>Overflow past 64 bits is <b>not</b> an error: the digits are folded into the register the
     * same way the hardware would, which is the behaviour {@code integer-overflow(7)} describes. A
     * player who types twenty {@code F}s should see what a register does with them, not a complaint.
     */
    public Optional<Long> parse(String digits) {
        if (digits == null || digits.isBlank()) {
            return Optional.empty();
        }
        long accumulated = 0;
        for (char c : digits.trim().toCharArray()) {
            if (c == ' ' || c == '_') {
                continue;
            }
            int digit = DIGITS.indexOf(Character.toLowerCase(c));
            if (digit < 0 || digit >= base) {
                return Optional.empty();
            }
            accumulated = accumulated * base + digit;
        }
        return Optional.of(accumulated);
    }

    /** The base a literal prefix names — {@code 0x}, {@code 0b}, {@code 0o}. */
    public static Optional<Radix> byPrefix(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (Radix radix : values()) {
            if (!radix.prefix.isEmpty() && lower.startsWith(radix.prefix)) {
                return Optional.of(radix);
            }
        }
        return Optional.empty();
    }

    public static Optional<Radix> byLabel(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        for (Radix radix : values()) {
            if (radix.label.equals(lower)) {
                return Optional.of(radix);
            }
        }
        return Optional.empty();
    }
}
