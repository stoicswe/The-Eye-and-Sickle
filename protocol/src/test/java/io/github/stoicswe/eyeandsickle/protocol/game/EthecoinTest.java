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
            assertThatThrownBy(() -> Ethecoin.ofMinorUnits(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("zero is a legal amount")
        void zeroIsLegal() {
            assertThat(Ethecoin.ZERO.minorUnits()).isZero();
            assertThat(Ethecoin.ZERO.isZero()).isTrue();
            assertThat(Ethecoin.ofMinorUnits(0)).isEqualTo(Ethecoin.ZERO);
        }

        @Test
        @DisplayName("whole ethecoin scales by the minor-unit factor")
        void wholeUnitsScale() {
            assertThat(Ethecoin.ofWholeEthecoin(25))
                    .isEqualTo(Ethecoin.ofMinorUnits(25 * Ethecoin.MINOR_UNITS_PER_ETHECOIN));
            assertThat(Ethecoin.ofWholeEthecoin(0)).isEqualTo(Ethecoin.ZERO);
        }

        @Test
        @DisplayName("a whole amount too large to scale fails loudly instead of wrapping")
        void wholeUnitsOverflow() {
            assertThatThrownBy(() -> Ethecoin.ofWholeEthecoin(Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
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
            assertThat(Ethecoin.ofMinorUnits(2_500).plus(Ethecoin.ofMinorUnits(267)))
                    .isEqualTo(Ethecoin.ofMinorUnits(2_767));
        }

        @Test
        @DisplayName("addition that would overflow throws rather than wrapping into a plausible balance")
        void additionOverflows() {
            Ethecoin huge = Ethecoin.ofMinorUnits(Long.MAX_VALUE);
            assertThatThrownBy(() -> huge.plus(Ethecoin.ofMinorUnits(1))).isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("subtraction reduces the amount")
        void subtractionReduces() {
            assertThat(Ethecoin.ofMinorUnits(2_500).minus(Ethecoin.ofMinorUnits(500)))
                    .isEqualTo(Ethecoin.ofMinorUnits(2_000));
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
            assertThatThrownBy(() -> Ethecoin.ofMinorUnits(100).minus(Ethecoin.ofMinorUnits(101)))
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
            assertThat(Ethecoin.ofMinorUnits(100)).isLessThan(Ethecoin.ofMinorUnits(101));
            assertThat(Ethecoin.ofMinorUnits(101)).isGreaterThan(Ethecoin.ofMinorUnits(100));
            assertThat(Ethecoin.ofMinorUnits(100)).isEqualByComparingTo(Ethecoin.ofMinorUnits(100));
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
            assertThat(Ethecoin.ofWholeEthecoin(25))
                    .isEqualTo(Ethecoin.ofMinorUnits(2_500))
                    .hasSameHashCodeAs(Ethecoin.ofMinorUnits(2_500));
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
            assertThat((Object) Ethecoin.ofMinorUnits(100)).isNotEqualTo(Cycles.of(100));
            assertThat((Object) Cycles.of(100)).isNotEqualTo(Ethecoin.ofMinorUnits(100));
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
        assertThat(Ethecoin.MINOR_UNITS_PER_ETHECOIN).isPositive();
        assertThat(Ethecoin.ofWholeEthecoin(1).minorUnits()).isEqualTo(Ethecoin.MINOR_UNITS_PER_ETHECOIN);
    }

    @Test
    @DisplayName("no display formatting leaks into the wire type")
    void noDisplayFormatting() {
        // Localized currency formatting is the client's job; a wire type that formats invites a
        // second, subtly different formatter to appear on the server.
        Method[] methods = Ethecoin.class.getDeclaredMethods();
        assertThat(Arrays.stream(methods).map(Method::getName))
                .doesNotContain("format", "toDisplayString", "toPlainString");
    }
}
