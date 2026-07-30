package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the ethecoin value type — and, in the last section, the mechanical part of the case
 * that ethecoin can never turn into compute.
 *
 * <p>Invariant I1 (compute is never purchasable with ethecoin) is the rule that stops mining income
 * buying the capacity to mine more. It is enforced by the type system rather than by a check, so the
 * tests that matter most here are the ones asserting that no conversion exists to be called.
 */
class EthecoinTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a negative amount is rejected — the ledger carries a direction, not a sign")
        void negativeAmountsRejected() {
            assertThatThrownBy(() -> Ethecoin.ofWei(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("zero is a legal amount")
        void zeroIsLegal() {
            assertThat(Ethecoin.ZERO.wei().signum()).isZero();
            assertThat(Ethecoin.ZERO.isZero()).isTrue();
            assertThat(Ethecoin.ofWei(0)).isEqualTo(Ethecoin.ZERO);
        }

        @Test
        @DisplayName("whole ethecoin scales by the minor-unit factor")
        void wholeUnitsScale() {
            assertThat(Ethecoin.ofWholeEthecoin(25))
                    .isEqualTo(Ethecoin.ofWei(
                            java.math.BigInteger.valueOf(25).multiply(Ethecoin.WEI_PER_ETHECOIN)));
            assertThat(Ethecoin.ofWholeEthecoin(0)).isEqualTo(Ethecoin.ZERO);
        }

        /**
         * ⚠ Was "a whole amount too large to scale fails loudly instead of wrapping".
         *
         * <p>{@link BigInteger} does not overflow, so the failure mode that test guarded — a wrapped
         * balance that looks legitimate to every layer above it — cannot occur any more. The property
         * worth keeping is the one underneath it: a very large amount stays exact rather than
         * silently losing its low digits, which is what a {@code double} would have done.
         */
        @Test
        @DisplayName("a very large amount stays exact rather than wrapping or rounding")
        void hugeAmountsAreExact() {
            Ethecoin huge = Ethecoin.ofWholeEthecoin(Long.MAX_VALUE);
            assertThat(huge.wei())
                    .isEqualTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)
                            .multiply(Ethecoin.WEI_PER_ETHECOIN));
            assertThat(huge.plus(Ethecoin.ofWei(1)).wei())
                    .isEqualTo(huge.wei().add(java.math.BigInteger.ONE));
        }

        @Test
        @DisplayName("a negative whole amount is rejected before it is scaled")
        void negativeWholeUnitsRejected() {
            assertThatThrownBy(() -> Ethecoin.ofWholeEthecoin(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("addition sums minor units")
        void additionSums() {
            assertThat(Ethecoin.ofWei(2_500).plus(Ethecoin.ofWei(267)))
                    .isEqualTo(Ethecoin.ofWei(2_767));
        }

        @Test
        @DisplayName("addition is exact at any magnitude")
        void additionIsExact() {
            // Was "addition that would overflow throws rather than wrapping". BigInteger has no
            // overflow, so exactness is the property left to assert.
            Ethecoin huge = Ethecoin.ofWholeEthecoin(Long.MAX_VALUE);
            assertThat(huge.plus(huge).wei()).isEqualTo(huge.wei().shiftLeft(1));
        }

        @Test
        @DisplayName("subtraction reduces the amount")
        void subtractionReduces() {
            assertThat(Ethecoin.ofWei(2_500).minus(Ethecoin.ofWei(500)))
                    .isEqualTo(Ethecoin.ofWei(2_000));
        }

        @Test
        @DisplayName("subtracting the whole balance leaves exactly zero")
        void subtractionToZero() {
            Ethecoin balance = Ethecoin.ofWholeEthecoin(400);
            assertThat(balance.minus(balance)).isEqualTo(Ethecoin.ZERO);
        }

        @Test
        @DisplayName("an overdraw is rejected — balances do not go negative")
        void subtractionBelowZeroRejected() {
            assertThatThrownBy(() -> Ethecoin.ofWei(100).minus(Ethecoin.ofWei(101)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("arithmetic rejects a null operand rather than treating it as zero")
        void nullOperandsRejected() {
            assertThatThrownBy(() -> Ethecoin.ZERO.plus(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Ethecoin.ZERO.minus(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ordering and identity")
    class OrderingAndIdentity {

        @Test
        @DisplayName("orders by amount")
        void ordersByAmount() {
            assertThat(Ethecoin.ofWei(100)).isLessThan(Ethecoin.ofWei(101));
            assertThat(Ethecoin.ofWei(101)).isGreaterThan(Ethecoin.ofWei(100));
            assertThat(Ethecoin.ofWei(100)).isEqualByComparingTo(Ethecoin.ofWei(100));
        }

        @Test
        @DisplayName("sorts low to high")
        void sortsAscending() {
            List<Ethecoin> amounts = Stream.of(
                            Ethecoin.ofWholeEthecoin(400), Ethecoin.ZERO, Ethecoin.ofWholeEthecoin(25))
                    .sorted()
                    .toList();

            assertThat(amounts)
                    .containsExactly(Ethecoin.ZERO, Ethecoin.ofWholeEthecoin(25), Ethecoin.ofWholeEthecoin(400));
        }

        @Test
        @DisplayName("equal amounts are equal values")
        void valueEquality() {
            // ⚠ Both sides built the SAME way is not a test. One side is the whole-unit factory and
            // the other is the decimal parser, so this checks the two entry points agree — which is
            // the property that broke when the scale moved from 2 places to 18.
            assertThat(Ethecoin.ofWholeEthecoin(25))
                    .isEqualTo(Ethecoin.ofDecimal("25"))
                    .hasSameHashCodeAs(Ethecoin.ofDecimal("25.000000000000000000"));
        }
    }

    /**
     * Invariant I1, checked as far as a runtime test can check a compile-time property.
     *
     * <p>The real guarantee is structural and cannot be expressed as an assertion: none of the
     * following compiles, and that is the point.
     *
     * <pre>{@code
     * Cycles cycles = Ethecoin.ofWholeEthecoin(400);        // incompatible types
     * Ethecoin.ZERO.plus(Cycles.of(100));                   // no such method
     * Ethecoin.ZERO.compareTo(Cycles.of(100));              // no such method
     * List<Ethecoin> wallet = List.of(Cycles.of(100));      // incompatible types
     * }</pre>
     *
     * What is testable is the absence of every door someone could later add: no method on either type
     * mentions the other, and no comparison entry point crosses them. If one appears, this fails
     * before a reviewer has to notice it.
     */
    @Nested
    @DisplayName("ethecoin and cycles cannot be mixed up")
    class SeparationFromCycles {

        @Test
        @DisplayName("no method on either type mentions the other")
        void noConversionMethodExists() {
            assertThat(referencedTypes(Ethecoin.class))
                    .as("a method taking or returning Cycles would be a compute-for-money conversion (Invariant I1)")
                    .doesNotContain(Cycles.class);
            assertThat(referencedTypes(Cycles.class))
                    .as("a method taking or returning Ethecoin would be a money-for-compute conversion (Invariant I1)")
                    .doesNotContain(Ethecoin.class);
        }

        @Test
        @DisplayName("comparison is typed to self, so the two never sort against each other")
        void comparisonIsSelfTyped() throws Exception {
            assertThat(Ethecoin.class.getDeclaredMethod("compareTo", Ethecoin.class))
                    .isNotNull();
            assertThat(Cycles.class.getDeclaredMethod("compareTo", Cycles.class))
                    .isNotNull();

            assertThatThrownBy(() -> Ethecoin.class.getDeclaredMethod("compareTo", Cycles.class))
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatThrownBy(() -> Cycles.class.getDeclaredMethod("compareTo", Ethecoin.class))
                    .isInstanceOf(NoSuchMethodException.class);
        }

        @Test
        @DisplayName("equal magnitudes are still not equal values")
        void sameMagnitudeIsNotEquality() {
            // 100 minor units of ethecoin and a starting rig's 100 cycles are the same number and
            // nothing else. A collection that accepted both would have already lost the invariant.
            assertThat((Object) Ethecoin.ofWei(100)).isNotEqualTo(Cycles.of(100));
            assertThat((Object) Cycles.of(100)).isNotEqualTo(Ethecoin.ofWei(100));
        }

        @Test
        @DisplayName("neither type is assignable to the other")
        void neitherIsAssignableToTheOther() {
            assertThat(Ethecoin.class.isAssignableFrom(Cycles.class)).isFalse();
            assertThat(Cycles.class.isAssignableFrom(Ethecoin.class)).isFalse();
        }

        private static List<Class<?>> referencedTypes(Class<?> type) {
            return Arrays.stream(type.getDeclaredMethods())
                    .flatMap(method ->
                            Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes())))
                    .distinct()
                    .toList();
        }
    }

    @Test
    @DisplayName("the minor-unit scale is a whole-number factor")
    void scaleIsSane() {
        // Not a balance value — a precision decision (see the class javadoc). It still has to be a
        // positive whole factor, or every amount in the game rounds differently on the two sides.
        assertThat(Ethecoin.WEI_PER_ETHECOIN).isPositive();
        assertThat(Ethecoin.ofWholeEthecoin(1).wei()).isEqualTo(Ethecoin.WEI_PER_ETHECOIN);
    }

    /**
     * ⚠ This test used to assert the OPPOSITE, and the reversal is worth reading.
     *
     * <p>It required that no {@code format} method exist here at all, reasoning that <em>"a wire type
     * that formats invites a second, subtly different formatter to appear on the server."</em> The
     * risk was real and the conclusion was backwards: what a type with <b>no</b> formatter invites is
     * a private copy everywhere one is needed. There were <b>thirteen</b>, twelve of them carrying
     * the same sign bug — {@code minorUnits / 100} truncates toward zero, so every fee in the game
     * rendered without its minus sign. The canonical formatter is the fix for the very thing the old
     * test was guarding against.
     *
     * <p>The surviving half of the original guarantee is asserted below: no <b>localized</b>
     * formatting here. A grouped, symbol-placed, abbreviated amount is a presentation decision and
     * still belongs to the client; what lives on the type is the invariant machine form.
     */
    @Test
    @DisplayName("one canonical formatter, and no localized one")
    void oneCanonicalFormatter() {
assertThat(Ethecoin.format(Ethecoin.ofDecimal("4.80").wei())).isEqualTo("4.8 EC");
        // ⚠ The bug the consolidation fixed: a debit under one whole ethecoin kept its magnitude and
        // lost its sign, which on a ledger makes a fee indistinguishable from a credit.
        assertThat(Ethecoin.format(Ethecoin.ofDecimal("0.05").wei().negate())).isEqualTo("-0.05 EC");
        assertThat(Ethecoin.format(Ethecoin.ofDecimal("0.99").wei().negate())).isEqualTo("-0.99 EC");
        assertThat(Ethecoin.format(Ethecoin.ofDecimal("1.50").wei().negate())).isEqualTo("-1.5 EC");
        assertThat(Ethecoin.format(java.math.BigInteger.ZERO)).isEqualTo("0 EC");

        // No Locale-taking overload: that is where a localized format would arrive, and it belongs to
        // the client. Reflection rather than a comment, because the point is to notice a new one.
        assertThat(Arrays.stream(Ethecoin.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("format"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .map(Class::getName))
                .as("a localized formatter belongs to the client, not to the wire type")
                .doesNotContain("java.util.Locale");
        assertThat(Arrays.stream(Ethecoin.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("toDisplayString", "toPlainString");
    }

    /**
     * ⚠ {@code Long.MIN_VALUE} has no positive counterpart, so {@code Math.abs} returns it unchanged.
     *
     * <p>Guarded because the obvious implementation — absolute value first, sign bolted on after —
     * produces a negative magnitude here and prints something impossible. It cannot arise from play;
     * it can arise from a hand-edited save, and a formatter that throws on a value a readout is
     * trying to draw takes the window down with it.
     */
    @Test
    @DisplayName("the extreme negative renders without blowing up")
    void extremeNegative() {
        assertThat(Ethecoin.format(Long.MIN_VALUE)).startsWith("-").endsWith(" EC");
    }
}
