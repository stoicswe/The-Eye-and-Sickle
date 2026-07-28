package io.github.stoicswe.eyeandsickle.client.ui.calc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The calculator's arithmetic, its chain and its readouts — all without a toolkit.
 *
 * <h2>What is worth testing here and what is not</h2>
 *
 * Nothing here checks that a key is wired to a method; that is one line each and reading it is
 * cheaper than asserting it. What is worth pinning is everything where the <em>machine's</em>
 * behaviour and the obvious Java expression differ, because those are the places a well-meaning
 * simplification silently changes what the tool teaches:
 *
 * <ul>
 *   <li>the 64-bit mask, where {@code (1L << 64) - 1} is {@code 0} and not {@code -1};
 *   <li>sign extension out of a narrow word, where a naive right shift brings in the wrong bits;
 *   <li>shift counts at or past the register width, where Java and x86 both wrap and this tool
 *       deliberately does not;
 *   <li>unsigned division, where {@code /} on a {@code long} is the wrong operator entirely.
 * </ul>
 *
 * <p>Every one of those has a comment in the class under test saying so. A test that did not exist
 * would make each of them a comment somebody could delete.
 */
class CalculatorTest {

    @Nested
    @DisplayName("word width")
    class Width {

        @Test
        @DisplayName("⚠ a 64-bit mask keeps every bit — the shift-by-64 trap")
        void sixtyFourIsNotZero() {
            // (1L << 64) is 1 in Java, not 0: the shift distance is taken modulo 64. Written the
            // obvious way, the QWORD mask comes out as 0 and every value the calculator holds is
            // silently zeroed. This is the assertion that says the special case has to stay.
            assertThat(WordSize.QWORD.mask(-1L)).isEqualTo(-1L);
            assertThat(WordSize.QWORD.mask(0x0123456789ABCDEFL)).isEqualTo(0x0123456789ABCDEFL);
            assertThat(WordSize.BYTE.mask(0x1FF)).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("the same bits read signed and unsigned are two different numbers")
        void twosComplementIsAReading() {
            assertThat(WordSize.BYTE.signed(0xFF)).isEqualTo(-1);
            assertThat(WordSize.BYTE.mask(0xFF)).isEqualTo(255);
            assertThat(WordSize.BYTE.signed(0x80)).isEqualTo(-128);
            assertThat(WordSize.BYTE.signed(0x7F)).isEqualTo(127);
            assertThat(WordSize.QWORD.signed(-1L)).isEqualTo(-1L);
        }

        @Test
        @DisplayName("narrowing the width truncates the register, and that is the demonstration")
        void narrowingTruncates() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.DWORD);
            calc.set(0xDEADBEEFL);
            calc.setWord(WordSize.WORD);
            assertThat(calc.value()).isEqualTo(0xBEEFL);
            // And it does not come back. The high half is gone, exactly as it would be gone from a
            // narrower variable — docs/education/01-foundations.md §3.7.
            calc.setWord(WordSize.DWORD);
            assertThat(calc.value()).isEqualTo(0xBEEFL);
        }
    }

    @Nested
    @DisplayName("operations")
    class Operations {

        @Test
        @DisplayName("a multiply that overflows the word wraps rather than widening")
        void overflowWraps() {
            // The behaviour docs/education/01-foundations.md §3.8 is about. 16 * 16 is 256, which is
            // 0 in a byte — and a calculator that answered 256 here would be teaching the opposite
            // of what the width control claims to mean.
            assertThat(CalcOp.MUL.apply(16, 16, WordSize.BYTE, false)).isZero();
            assertThat(CalcOp.ADD.apply(255, 1, WordSize.BYTE, false)).isZero();
            assertThat(CalcOp.SUB.apply(0, 1, WordSize.BYTE, false)).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("⚠ unsigned division is not `/`")
        void unsignedDivision() {
            // 0xFFFFFFFFFFFFFFFF / 2 is -1/2 = 0 with Java's operator and a very large number with
            // the right one. There is no width at which this is a rounding difference.
            long all = -1L;
            assertThat(CalcOp.DIV.apply(all, 2, WordSize.QWORD, false))
                    .isEqualTo(Long.divideUnsigned(all, 2));
            assertThat(CalcOp.DIV.apply(all, 2, WordSize.QWORD, true)).isZero();
            assertThat(CalcOp.DIV.apply(0xFF, 16, WordSize.BYTE, false)).isEqualTo(0x0F);
            assertThat(CalcOp.DIV.apply(0xFF, 16, WordSize.BYTE, true)).isZero();
        }

        @Test
        @DisplayName("dividing by zero refuses; it does not answer zero")
        void divideByZero() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.set(10);
            calc.operator(CalcOp.DIV);
            calc.set(0);
            calc.equals();
            assertThat(calc.error()).contains("zero");
            // The register is untouched. An answer of 0 would be indistinguishable from a real one.
            assertThat(calc.value()).isZero();
        }

        @Test
        @DisplayName("⚠ ASR copies the register's sign bit, not bit 63 of the Java long")
        void arithmeticShiftExtendsFromTheWord() {
            // 0x80 in a byte is -128. Shifted right by one it is -64, which is 0xC0. Without
            // sign-extending from the WORD first, the top bit shifted in is a zero from the wider
            // long and the answer comes out 0x40 — a positive number, silently.
            assertThat(CalcOp.SAR.apply(0x80, 1, WordSize.BYTE, true)).isEqualTo(0xC0);
            assertThat(CalcOp.SHR.apply(0x80, 1, WordSize.BYTE, true)).isEqualTo(0x40);
        }

        @Test
        @DisplayName("⚠ a shift of the register width gives zero — the documented divergence")
        void shiftPastTheWidth() {
            // Java and x86 both take the count modulo the width, so `1 << 64` is 1 on the hardware.
            // This tool answers the arithmetic instead, and the shipped page says so in CAVEATS. If
            // that changes, it must change in both places.
            assertThat(CalcOp.SHL.apply(1, 64, WordSize.QWORD, false)).isZero();
            assertThat(CalcOp.SHL.apply(1, 8, WordSize.BYTE, false)).isZero();
            assertThat(CalcOp.SHR.apply(0xFF, 8, WordSize.BYTE, false)).isZero();
            // ASR of a negative value saturates at all-ones, which IS the arithmetic answer.
            assertThat(CalcOp.SAR.apply(0x80, 8, WordSize.BYTE, true)).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("a rotate loses nothing — every bit that leaves comes back")
        void rotatePreservesBits() {
            for (WordSize word : WordSize.values()) {
                long value = word.mask(0x8000000000000001L);
                long round = CalcOp.ROR.apply(CalcOp.ROL.apply(value, 5, word, false), 5, word, false);
                assertThat(round).as("%s round-trips", word).isEqualTo(value);
                assertThat(Long.bitCount(CalcOp.ROL.apply(value, 3, word, false)))
                        .as("%s keeps its bit count", word)
                        .isEqualTo(Long.bitCount(value));
            }
        }
    }

    @Nested
    @DisplayName("the chain")
    class Chain {

        @Test
        @DisplayName("keys apply left to right, with no precedence")
        void noPrecedence() {
            // 20, not 14. Every desk calculator does this and the shipped page says so; a tool that
            // quietly applied precedence would be neither the keypad nor the language.
            Calculator calc = new Calculator();
            calc.setRadix(Radix.DEC);
            calc.digit('2');
            calc.operator(CalcOp.ADD);
            calc.digit('3');
            calc.operator(CalcOp.MUL);
            calc.digit('4');
            calc.equals();
            assertThat(calc.value()).isEqualTo(20);
        }

        @Test
        @DisplayName("two operators in a row replace, rather than evaluating twice")
        void operatorReplaces() {
            Calculator calc = new Calculator();
            calc.setRadix(Radix.DEC);
            calc.digit('8');
            calc.operator(CalcOp.ADD);
            calc.operator(CalcOp.SUB);
            calc.digit('3');
            calc.equals();
            assertThat(calc.value()).isEqualTo(5);
        }

        @Test
        @DisplayName("digits accumulate in the current base and stop at the register's width")
        void entryRespectsTheWidth() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.setRadix(Radix.HEX);
            assertThat(calc.digit('F')).isTrue();
            assertThat(calc.digit('F')).isTrue();
            assertThat(calc.value()).isEqualTo(0xFF);
            // A third hex digit does not fit in a byte, and silently discarding it would be worse
            // than refusing it.
            assertThat(calc.digit('1')).isFalse();
            assertThat(calc.value()).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("a digit that is not one in this base is refused, not coerced")
        void wrongBaseDigit() {
            Calculator calc = new Calculator();
            calc.setRadix(Radix.BIN);
            assertThat(calc.digit('1')).isTrue();
            assertThat(calc.digit('2')).isFalse();
            assertThat(calc.digit('F')).isFalse();
            assertThat(calc.value()).isEqualTo(1);
        }

        @Test
        @DisplayName("backspace drops the lowest digit of the base on screen")
        void backspace() {
            Calculator calc = new Calculator();
            calc.setRadix(Radix.HEX);
            calc.digit('A');
            calc.digit('B');
            calc.digit('C');
            assertThat(calc.value()).isEqualTo(0xABC);
            calc.backspace();
            assertThat(calc.value()).isEqualTo(0xAB);
        }
    }

    @Nested
    @DisplayName("readouts")
    class Readouts {

        @Test
        @DisplayName("switching base moves nothing — it is the same value, written differently")
        void baseIsNotation() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.set(0xFF);
            assertThat(calc.row(Radix.HEX)).isEqualTo("FF");
            assertThat(calc.row(Radix.DEC)).isEqualTo("255");
            assertThat(calc.row(Radix.OCT)).isEqualTo("377");
            assertThat(calc.row(Radix.BIN)).isEqualTo("1111 1111");
            calc.setRadix(Radix.BIN);
            assertThat(calc.value()).isEqualTo(0xFF);
        }

        @Test
        @DisplayName("only the decimal row takes a sign; the others always show what is stored")
        void onlyDecimalSigns() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.setSigned(true);
            calc.set(0xFF);
            assertThat(calc.row(Radix.DEC)).isEqualTo("-1");
            // A negative binary row would be a lie about the register — the bits are still all ones.
            assertThat(calc.row(Radix.BIN)).isEqualTo("1111 1111");
            assertThat(calc.row(Radix.HEX)).isEqualTo("FF");
        }

        @Test
        @DisplayName("hex, octal and binary are padded to the register's width; decimal is not")
        void paddingSaysHowWideTheRegisterIs() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.DWORD);
            calc.set(0x0F);
            assertThat(calc.row(Radix.HEX)).isEqualTo("0000 000F");
            assertThat(calc.row(Radix.DEC)).isEqualTo("15");
            assertThat(calc.row(Radix.BIN)).startsWith("0000 0000");
        }

        @Test
        @DisplayName("swapping byte order keeps the bytes and changes the number")
        void endianness() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.DWORD);
            calc.set(0x11223344L);
            assertThat(calc.bigEndian()).isEqualTo("11 22 33 44");
            assertThat(calc.littleEndian()).isEqualTo("44 33 22 11");
            calc.swapBytes();
            assertThat(calc.value()).isEqualTo(0x44332211L);
            // A byte has no byte order, and the key correctly does nothing to one.
            calc.setWord(WordSize.BYTE);
            long before = calc.value();
            calc.swapBytes();
            assertThat(calc.value()).isEqualTo(before);
        }

        @Test
        @DisplayName("bits() is least-significant-first, whatever the grid draws")
        void bitOrder() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.set(0b1000_0001);
            boolean[] bits = calc.bits();
            assertThat(bits).hasSize(8);
            assertThat(bits[0]).isTrue();
            assertThat(bits[7]).isTrue();
            assertThat(bits[1]).isFalse();
        }

        @Test
        @DisplayName("flipping a bit moves every base row at once")
        void oneValueManyViews() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.set(0);
            calc.toggleBit(7);
            assertThat(calc.row(Radix.HEX)).isEqualTo("80");
            assertThat(calc.row(Radix.DEC)).isEqualTo("128");
            assertThat(calc.setBits()).isEqualTo(1);
            calc.setSigned(true);
            assertThat(calc.row(Radix.DEC)).isEqualTo("-128");
        }

        @Test
        @DisplayName("a bit outside the register is ignored rather than throwing")
        void outOfRangeBit() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.BYTE);
            calc.set(0);
            calc.toggleBit(9);
            calc.toggleBit(-1);
            assertThat(calc.value()).isZero();
        }

        @Test
        @DisplayName("non-printable bytes read as a dot, the convention every hex dump uses")
        void characterRow() {
            Calculator calc = new Calculator();
            calc.setWord(WordSize.DWORD);
            calc.set(0x00414243L);
            assertThat(calc.characters()).isEqualTo(".ABC");
        }
    }

    @Nested
    @DisplayName("the written form — pillar C1")
    class Expressions {

        @Test
        @DisplayName("literals carry their base, and mixing them is the point")
        void mixedBases() {
            var result = Calculator.evaluate("0xff and 0b1010", WordSize.DWORD, false);
            assertThat(result.ok()).isTrue();
            assertThat(result.calculator().value()).isEqualTo(0b1010);
        }

        @Test
        @DisplayName("symbols and words are the same operators")
        void symbolsAndWords() {
            assertThat(Calculator.evaluate("12 & 10", WordSize.DWORD, false).calculator().value())
                    .isEqualTo(Calculator.evaluate("12 and 10", WordSize.DWORD, false)
                            .calculator().value());
            assertThat(Calculator.evaluate("1 << 12", WordSize.DWORD, false).calculator().value())
                    .isEqualTo(0x1000);
        }

        @Test
        @DisplayName("the written form and the keys give the same answer, precedence included")
        void agreesWithTheKeys() {
            // The C1 failure that is hardest to notice is not a missing command, it is a command
            // that quietly disagrees with its window. Both paths run the same chain.
            Calculator keys = new Calculator();
            keys.setRadix(Radix.DEC);
            keys.digit('2');
            keys.operator(CalcOp.ADD);
            keys.digit('3');
            keys.operator(CalcOp.MUL);
            keys.digit('4');
            keys.equals();

            var written = Calculator.evaluate("2 + 3 * 4", WordSize.DWORD, false);
            assertThat(written.ok()).isTrue();
            assertThat(written.calculator().value()).isEqualTo(keys.value());
        }

        @Test
        @DisplayName("a minus keeps working as both an operator and a sign")
        void minusIsBothThings() {
            assertThat(Calculator.evaluate("8 - 1", WordSize.DWORD, false).calculator().value())
                    .isEqualTo(7);
            assertThat(Calculator.evaluate("-1", WordSize.BYTE, false).calculator().value())
                    .isEqualTo(0xFF);
        }

        @Test
        @DisplayName("nonsense is refused with a reason, and never guessed at")
        void refusals() {
            assertThat(Calculator.evaluate("", WordSize.DWORD, false).ok()).isFalse();
            assertThat(Calculator.evaluate("0xzz + 1", WordSize.DWORD, false).error())
                    .contains("not a number");
            assertThat(Calculator.evaluate("1 +", WordSize.DWORD, false).error())
                    .contains("ends with an operator");
            assertThat(Calculator.evaluate("1 / 0", WordSize.DWORD, false).error()).contains("zero");
        }

        @Test
        @DisplayName("the width applies to the written form too")
        void widthApplies() {
            var result = Calculator.evaluate("0xFF + 1", WordSize.BYTE, false);
            assertThat(result.ok()).isTrue();
            assertThat(result.calculator().value()).isZero();
        }
    }
}
