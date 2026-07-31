package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two value wrappers render as themselves, not as their generated record form.
 *
 * <h2>⚠ What this is actually guarding</h2>
 *
 * A record's generated {@code toString} is <b>the thing you get by accident</b>. Any
 * {@code "you have " + balance} compiles, renders without complaint, and prints
 * {@code Ethecoin[minorUnits=480]} at the player — and it did, on five separate surfaces, before
 * anybody noticed: a delete confirmation, a storage log line, the {@code inv} balance row, a balance
 * readout, and a refusal telling the player what they could afford. {@code Views.spec} had even grown
 * a comment warning the next person.
 *
 * <p>These tests are cheap and the failure they prevent is one that looks like a leaked internal on a
 * screen the player is reading. Keep them.
 */
class ValueTypeRenderingTest {

    /** The shape a generated record {@code toString} has, and the thing that must never appear. */
    private static void assertNotTheRecordForm(String rendered, String typeName) {
        assertThat(rendered)
                .as("a value type must never render as its generated record form")
                .doesNotContain(typeName + "[")
                .doesNotContain("=");
    }

    @Nested
    @DisplayName("Ethecoin")
    class Money {

        @Test
        @DisplayName("renders as an amount, never as Ethecoin[minorUnits=…]")
        void rendersAsAnAmount() {
            assertThat(Ethecoin.ofDecimal("4.80")).hasToString("4.8 EC");
            assertThat(Ethecoin.ZERO).hasToString("0 EC");
            assertNotTheRecordForm(Ethecoin.ofDecimal("4.80").toString(), "Ethecoin");
        }

        /**
         * ⚠ Only SIGNIFICANT decimals are shown, and trimming is never rounding.
         *
         * <p>At 18 places a fixed-width format would render every ordinary amount as
         * {@code 8.000000000000000000 EC} — eighteen characters of noise on every ledger row, burying
         * the amounts that genuinely have a tail. What is dropped is only zeros that carry no
         * information; every significant digit is printed however many there are.
         */
        @Test
        @DisplayName("trailing zeros are trimmed, and nothing significant ever is")
        void onlySignificantDecimals() {
            assertThat(Ethecoin.ofDecimal("0.05")).hasToString("0.05 EC");
            assertThat(Ethecoin.ofDecimal("8.00")).hasToString("8 EC");
            assertThat(Ethecoin.ofDecimal("500")).hasToString("500 EC");
            // The full-precision case from the request — exact, all eighteen places.
            assertThat(Ethecoin.ofDecimal("0.037097927036961408")).hasToString("0.037097927036961408 EC");
            // One wei: the smallest thing that exists, and it still renders.
            assertThat(Ethecoin.ofWei(1L)).hasToString("0.000000000000000001 EC");
        }

        /**
         * ⚠ {@code stripTrailingZeros} can leave a NEGATIVE scale — {@code 500} becomes {@code 5E+2}
         * — and {@code toString} would print scientific notation at the player. Caught by rendering,
         * not by reasoning.
         */
        @Test
        @DisplayName("a whole amount never renders in scientific notation")
        void noScientificNotation() {
            for (String whole : new String[] {"1", "10", "100", "500", "1000", "1000000"}) {
                // ⚠ "E+"/"E-", not "E" — the unit is "EC" and contains an E, so the naive check
                // fails on every correct value. BigDecimal's scientific form always carries a sign.
                assertThat(Ethecoin.ofDecimal(whole).toString())
                        .as("%s", whole)
                        .doesNotContain("E+")
                        .doesNotContain("E-")
                        .isEqualTo(whole + " EC");
            }
        }

        @Test
        @DisplayName("a bare concatenation — the thing that actually went wrong — is now correct")
        void concatenationIsSafe() {
            assertThat("you have " + Ethecoin.ofDecimal("123.45")).isEqualTo("you have 123.45 EC");
        }

        /**
         * ⚠ The capped formatter ROUNDS, and the split between it and {@code format} is the point.
         *
         * <p>A derived figure — a rate, an expectation — is computed through a double somewhere, so
         * its low digits are arithmetic residue. The rig monitor read {@code ~39.99999999999999802
         * EC/hr}, which is not a more precise answer than 40. An exact amount must never go through
         * this: a rounded balance is a lie the player cannot detect.
         */
        @Test
        @DisplayName("formatApprox caps decimals; format never does")
        void approximateAndExactAreDifferentMethods() {
            BigInteger noisyRate = Ethecoin.ofDecimal("39.99999999999999802").wei();
            assertThat(Ethecoin.formatApprox(noisyRate, 4)).isEqualTo("40 EC");
            assertThat(Ethecoin.format(noisyRate))
                    .as("the exact formatter must still show everything")
                    .isEqualTo("39.99999999999999802 EC");

            BigInteger share = Ethecoin.ofDecimal("0.333333333333333361").wei();
            assertThat(Ethecoin.formatApprox(share, 4)).isEqualTo("0.3333 EC");

            // Trailing zeros are trimmed after the cap, and the sign survives it.
            assertThat(Ethecoin.formatApprox(Ethecoin.ofDecimal("40.00001").wei(), 4))
                    .isEqualTo("40 EC");
            assertThat(Ethecoin.formatApprox(Ethecoin.ofDecimal("1.5").wei().negate(), 4))
                    .isEqualTo("-1.5 EC");
            // ⚠ Zero decimals is a whole number, not an empty fraction.
            assertThat(Ethecoin.formatApprox(Ethecoin.ofDecimal("40.6").wei(), 0))
                    .isEqualTo("41 EC");
        }

        @Test
        @DisplayName("the wire form is untouched")
        void theWireFormIsUnchanged() {
            // ⚠ Nothing serialises through toString. Serialisation is the record component, and this
            // change must not have moved it.
            assertThat(Ethecoin.ofDecimal("4.80").wei())
                    .isEqualTo(java.math.BigInteger.valueOf(4_800_000_000_000_000_000L));
        }
    }

    @Nested
    @DisplayName("Cycles")
    class Compute {

        @Test
        @DisplayName("renders as a quantity, never as Cycles[cycles=…]")
        void rendersAsAQuantity() {
            assertThat(Cycles.of(12L)).hasToString("12 cycles");
            assertNotTheRecordForm(Cycles.of(12L).toString(), "Cycles");
        }

        @Test
        @DisplayName("one cycle is singular")
        void oneIsSingular() {
            assertThat(Cycles.of(1L)).hasToString("1 cycle");
            assertThat(Cycles.of(0L)).hasToString("0 cycles");
        }
    }
}
