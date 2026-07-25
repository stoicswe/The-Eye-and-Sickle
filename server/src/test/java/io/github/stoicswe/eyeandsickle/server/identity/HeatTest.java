package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Heat as a stored reading ({@code docs/design/01-core-resources.md} §4). The invariant-bearing parts:
 * it is never negative, its arithmetic is exact decimal (never {@code double}), and — Invariant I9 —
 * there is deliberately <em>no</em> path on this type that accrues heat automatically. The absence is the
 * enforcement, so one of these tests asserts the absence.
 */
class HeatTest {

    @Test
    @DisplayName("ZERO is a reading of zero")
    void zero() {
        assertThat(Heat.ZERO.value()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a negative reading cannot be constructed")
    void negativeRejected() {
        // ck_players_heat_non_negative / ck_server_state_heat_non_negative in the schema; the type mirrors
        // them so an invalid reading cannot exist even in memory. "Negative attention" is not a state.
        assertThatThrownBy(() -> new Heat(new BigDecimal("-0.0001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Nested
    @DisplayName("plus")
    class Plus {

        @Test
        @DisplayName("a positive delta accrues")
        void accrues() {
            assertThat(new Heat(new BigDecimal("3.0000"))
                            .plus(new BigDecimal("2.5"))
                            .value())
                    .isEqualByComparingTo("5.5");
        }

        @Test
        @DisplayName("a negative delta models decay (laying low, §4.3)")
        void decays() {
            assertThat(new Heat(new BigDecimal("5")).plus(new BigDecimal("-2")).value())
                    .isEqualByComparingTo("3");
        }

        @Test
        @DisplayName("decay exactly to zero is fine; heat decays to zero, it does not invert")
        void decayToZero() {
            assertThatCode(() -> new Heat(new BigDecimal("2.5")).plus(new BigDecimal("-2.5")))
                    .doesNotThrowAnyException();
            assertThat(new Heat(new BigDecimal("2.5"))
                            .plus(new BigDecimal("-2.5"))
                            .value())
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a decay that overshoots zero throws rather than silently flooring")
        void overDecayThrows() {
            // A decay that overshoots is arithmetic the caller got wrong; clamping it to zero would hide a
            // bug behind a plausible-looking value.
            assertThatThrownBy(() -> new Heat(new BigDecimal("1")).plus(new BigDecimal("-1.5")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the arithmetic is exact decimal — 0.1 + 0.2 is 0.3, not 0.30000000000000004")
        void exactDecimal() {
            // A federation cannot tolerate two servers disagreeing about whether a threshold was cleared;
            // binary floating point would make that disagreement indistinguishable from cheating.
            assertThat(new Heat(new BigDecimal("0.1"))
                            .plus(new BigDecimal("0.2"))
                            .value())
                    .isEqualByComparingTo("0.3");
        }

        @Test
        @DisplayName("a null delta is a programming error")
        void nullDelta() {
            assertThatThrownBy(() -> Heat.ZERO.plus(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @DisplayName("Invariant I9: the type offers no automatic accrual — only an explicit, attributed plus")
    void noHiddenAccrualPath() {
        // Heat gates ACCESS not ownership, and defending your own rig never generates heat. The invariant
        // is that no code path adds heat for a defence — so the type must expose only `plus`, an explicit
        // caller-attributed adjustment, and nothing that could quietly accrue. If someone ever adds an
        // `accrueForDefence`/`onDefend`/`increment` here, this test fails and forces the conversation.
        boolean hasProducerOtherThanPlus = Arrays.stream(Heat.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == Heat.class)
                .map(Method::getName)
                .anyMatch(name -> !name.equals("plus"));
        assertThat(hasProducerOtherThanPlus)
                .as("Heat must produce a new reading only via the explicit plus(), never an automatic accrual")
                .isFalse();
    }

    @Test
    @DisplayName("null value is rejected")
    void nullValue() {
        assertThatThrownBy(() -> new Heat(null)).isInstanceOf(NullPointerException.class);
    }
}
