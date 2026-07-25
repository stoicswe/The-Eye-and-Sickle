package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Invariants of the compute value type.
 *
 * <p>Compute is the master scarcity ({@code docs/design/01-core-resources.md} §1), so the arithmetic
 * that moves it has to fail loudly rather than quietly produce a number. A wrapped or negative cycle
 * count would show up as a rig with impossible capacity, which is indistinguishable from the exploit
 * the whole compute economy exists to make impossible.
 *
 * <p>The complementary tests — that cycles can never be produced from ethecoin — live in {@code
 * EthecoinTest.SeparationFromCycles}.
 */
class CyclesTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a negative quantity is rejected")
        void negativeRejected() {
            assertThatThrownBy(() -> Cycles.of(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("zero is a legal quantity — an unallocated rig is not an error")
        void zeroIsLegal() {
            assertThat(Cycles.ZERO.cycles()).isZero();
            assertThat(Cycles.ZERO.isZero()).isTrue();
            assertThat(Cycles.of(0)).isEqualTo(Cycles.ZERO);
        }

        @Test
        @DisplayName("a starting rig's capacity round-trips")
        void carriesWholeCycles() {
            // 100 is the starting rig in docs/design/01 §1. The number lives in the test as a
            // plausible input, not in the type — the ceiling is a server-side balance value.
            assertThat(Cycles.of(100).cycles()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("addition sums cycles")
        void additionSums() {
            assertThat(Cycles.of(35).plus(Cycles.of(15))).isEqualTo(Cycles.of(50));
        }

        @Test
        @DisplayName("addition that would overflow throws rather than wrapping")
        void additionOverflows() {
            Cycles huge = Cycles.of(Long.MAX_VALUE);
            assertThatThrownBy(() -> huge.plus(Cycles.of(1))).isInstanceOf(ArithmeticException.class);
        }

        @Test
        @DisplayName("subtraction reduces the quantity")
        void subtractionReduces() {
            assertThat(Cycles.of(100).minus(Cycles.of(40))).isEqualTo(Cycles.of(60));
        }

        @Test
        @DisplayName("subtracting everything leaves exactly zero")
        void subtractionToZero() {
            assertThat(Cycles.of(100).minus(Cycles.of(100))).isEqualTo(Cycles.ZERO);
        }

        @Test
        @DisplayName("subtracting past zero is rejected — usually it means the wrong rig was charged")
        void subtractionBelowZeroRejected() {
            assertThatThrownBy(() -> Cycles.of(3).minus(Cycles.of(4)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never negative");
        }

        @Test
        @DisplayName("arithmetic rejects a null operand rather than treating it as zero")
        void nullOperandsRejected() {
            assertThatThrownBy(() -> Cycles.ZERO.plus(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Cycles.ZERO.minus(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ordering and identity")
    class OrderingAndIdentity {

        @Test
        @DisplayName("orders by quantity")
        void ordersByQuantity() {
            assertThat(Cycles.of(3)).isLessThan(Cycles.of(35));
            assertThat(Cycles.of(35)).isGreaterThan(Cycles.of(3));
            assertThat(Cycles.of(35)).isEqualByComparingTo(Cycles.of(35));
        }

        @Test
        @DisplayName("sorts low to high")
        void sortsAscending() {
            List<Cycles> quantities =
                    Stream.of(Cycles.of(35), Cycles.ZERO, Cycles.of(3)).sorted().toList();

            assertThat(quantities).containsExactly(Cycles.ZERO, Cycles.of(3), Cycles.of(35));
        }

        @Test
        @DisplayName("equal quantities are equal values")
        void valueEquality() {
            assertThat(Cycles.of(20)).isEqualTo(Cycles.of(20)).hasSameHashCodeAs(Cycles.of(20));
        }
    }
}
