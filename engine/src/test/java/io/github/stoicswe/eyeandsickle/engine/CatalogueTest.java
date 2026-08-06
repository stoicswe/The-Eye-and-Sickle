package io.github.stoicswe.eyeandsickle.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The unlock gates, checked against {@code docs/design/02-unlock-gates.md} rather than against taste.
 *
 * <h2>Why a whole class for this</h2>
 *
 * Invariant <b>I3</b> says every item sits behind exactly one gate and that gate assignment follows
 * {@code 02} §1.1's ordered procedure. Both are prose, and prose erodes under the entirely reasonable
 * pressure to make one more thing purchasable — every individual case sounds fine and the aggregate
 * is an economy where money buys everything. These are the mechanical half.
 */
class CatalogueTest {

    @Nested
    @DisplayName("the gates hold")
    class Gates {

        /**
         * ⚠ <b>THE ONE THAT GUARDS I2 ON A LADDER, and the failure it prevents is silent.</b>
         *
         * <p>The firewall and the detection array are both tiered, and on 2026-08-06 the array's T1
         * and T2 moved onto the ethecoin gate on explicit direction — low-level tools are
         * purchasable, high-level ones need a schematic. What makes that safe rather than an I2
         * violation is that the ladder's <b>top rung stayed behind the schematic</b>: money reaches
         * the highest rung below the ceiling and never the ceiling itself, which is exactly the
         * "top purchasable" shape {@code docs/design/03} §2 already uses for the firewall.
         *
         * <p>Give the array's T3 a price and nothing breaks visibly — the shop renders, the purchase
         * succeeds, the item works — and ethecoin has bought a permanent capability. That is the
         * whole reason this is a test and not a comment.
         *
         * <p>⚠ The firewall is deliberately NOT in this check. Its whole ladder is ethecoin-gated,
         * because {@code docs/design/09} §2 classifies it as horizontal protection whose real limiter
         * is the escalating standing compute (5/10/15). If that argument is ever extended to the
         * array, this test is where the change has to be argued.
         */
        @Test
        @DisplayName("the top of the detection-array ladder is not for sale, at any price")
        void theTopOfEveryDefenceLadderIsNotForSale() {
            var top = Catalogue.byId("detection-array-t3").orElseThrow();

            assertThat(top.gate())
                    .as("the ladder's ceiling is found or earned, never bought")
                    .isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(top.priceWei())
                    .as("a price here is ethecoin buying a permanent capability — Invariant I2")
                    .isEqualTo(BigInteger.ZERO);
            assertThat(top.gateRequirement())
                    .as("and a gated item has to say what would open it")
                    .isNotBlank();
        }

        /**
         * ⚠ A price on a non-ethecoin gate is I2 arriving by accident.
         *
         * <p>{@code Offering}'s own javadoc already says a non-zero price on a schematic-gated item
         * "would be exactly the I2 violation the gate exists to stop". This is that sentence made
         * mechanical, across the whole catalogue rather than the one entry somebody remembered.
         */
        @Test
        @DisplayName("nothing off the ethecoin gate carries a price")
        void onlyEthecoinOfferingsArePriced() {
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                if (offering.gate() != UnlockGate.ETHECOIN) {
                    assertThat(offering.priceWei())
                            .as("%s is %s-gated and must not have a price", offering.id(), offering.gate())
                            .isEqualTo(BigInteger.ZERO);
                }
            }
        }

        /** And the converse: an ethecoin item nobody can pay for is an item nobody can get. */
        @Test
        @DisplayName("every ethecoin offering is actually priced")
        void everyEthecoinOfferingIsPriced() {
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                if (offering.gate() == UnlockGate.ETHECOIN) {
                    assertThat(offering.priceWei())
                            .as("%s is on the money gate and needs a price", offering.id())
                            .isGreaterThan(BigInteger.ZERO);
                }
            }
        }

        /**
         * ⚠ A gate that does not explain itself sends the player to the shop for something not in it.
         *
         * <p>{@code docs/design/02} §1's premise is that a gate is <em>legible</em>. An ethecoin
         * offering explains itself with its price, so its requirement line is blank by design;
         * everything else has to carry the sentence.
         */
        @Test
        @DisplayName("every non-ethecoin gate says what would open it")
        void gatedOfferingsExplainThemselves() {
            for (Catalogue.Offering offering : Catalogue.offerings()) {
                if (offering.gate() != UnlockGate.ETHECOIN) {
                    assertThat(offering.gateRequirement())
                            .as("%s is %s-gated and says nothing about how to get it", offering.id(), offering.gate())
                            .isNotBlank();
                }
            }
        }
    }

    @Nested
    @DisplayName("defences resolve to catalogue entries")
    class Defences {

        /**
         * ⚠ Every armable defence must be a thing that exists, or the panel offers a row the rules
         * cannot satisfy and the refusal names an id the shop has never heard of.
         */
        @Test
        @DisplayName("every (kind, tier) the firewall panel offers resolves to a real offering")
        void everyDefenceHasAnOffering() {
            record Row(String kind, int tier) {}
            List<Row> panel = List.of(
                    new Row("firewall", 1),
                    new Row("firewall", 2),
                    new Row("firewall", 3),
                    new Row("canary", 1),
                    new Row("tarpit", 1),
                    new Row("honeypot-stash", 1),
                    new Row("detection-array", 1),
                    new Row("detection-array", 2),
                    new Row("detection-array", 3),
                    new Row("auto-counter-daemon", 1));

            for (Row row : panel) {
                String id = Catalogue.defenceOfferingId(row.kind(), row.tier())
                        .orElseThrow(() -> new AssertionError("no offering id for " + row));
                assertThat(Catalogue.byId(id))
                        .as("%s tier %d maps to '%s', which is not in the catalogue", row.kind(), row.tier(), id)
                        .isPresent();
            }
        }

        /** ⚠ A tier a save should never carry must still resolve, not throw. See the method's note. */
        @Test
        @DisplayName("a hand-edited tier clamps rather than becoming unrecognisable")
        void anAbsurdTierStillResolves() {
            assertThat(Catalogue.defenceOfferingId("firewall", 99)).contains("firewall-t3");
            assertThat(Catalogue.defenceOfferingId("firewall", -4)).contains("firewall-t1");
            // A kind with no tiers ignores it entirely rather than inventing canary-token-t2.
            assertThat(Catalogue.defenceOfferingId("canary", 2)).contains("canary-token");
        }

        @Test
        @DisplayName("something that is not a defence resolves to nothing")
        void unknownKindIsEmpty() {
            assertThat(Catalogue.defenceOfferingId("trebuchet", 1)).isEmpty();
            assertThat(Catalogue.defenceOfferingId(null, 1)).isEmpty();
        }

        /**
         * ⚠ The starting grant has to be a real, ethecoin-gated entry.
         *
         * <p>If it were schematic- or reputation-gated, a new character would hold something the
         * game's own rules say cannot be obtained that way — and, worse, could sell it for ethecoin
         * and turn a non-EC item into money, which is what {@code Repac.sellable} exists to refuse.
         */
        @Test
        @DisplayName("the defence a new rig is issued is an ordinary purchasable item")
        void theStartingDefenceIsOrdinary() {
            var starting = Catalogue.byId(Catalogue.STARTING_DEFENCE).orElseThrow();
            assertThat(starting.gate()).isEqualTo(UnlockGate.ETHECOIN);
            assertThat(starting.priceWei()).isGreaterThan(BigInteger.ZERO);
        }
    }
}
