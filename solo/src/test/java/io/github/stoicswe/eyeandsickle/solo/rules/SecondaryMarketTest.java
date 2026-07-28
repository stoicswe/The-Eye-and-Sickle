package io.github.stoicswe.eyeandsickle.solo.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The unsafe market, and the third reputation it needs.
 *
 * <p>The load-bearing test is {@link Separate} — three reputations that must never be conflated, one
 * of which did not exist until this feature. The rest is the shape of the risk.
 */
class SecondaryMarketTest {

    @Nested
    @DisplayName("⚠ three reputations, and they are not each other")
    class Separate {

        @Test
        @DisplayName("trading dishonestly does not touch faction standing")
        void traderIsNotFaction() {
            // CLAUDE.md and the glossary forbid conflating factionReputation and validatorReputation.
            // This is the third, and the same rule applies: a Sickle hero can be a thief.
            SoloSave save = new SoloSave();
            save.factionReputationSickle = 40;

            SecondaryMarket.defect(save, new Random(1));
            SecondaryMarket.defect(save, new Random(1));

            assertThat(save.factionReputationSickle).isEqualTo(40);
            assertThat(save.factionReputationEye).isZero();
        }

        @Test
        @DisplayName("delivering honestly does not buy faction standing either")
        void deliveryIsNotFaction() {
            SoloSave save = new SoloSave();
            SecondaryMarket.deliver(save);
            assertThat(save.traderReputation).isPositive();
            assertThat(save.factionReputationSickle).isZero();
        }
    }

    @Nested
    @DisplayName("the shape of the risk")
    class Risk {

        @Test
        @DisplayName("⚠ detection RISES with each defection, so a habit is what gets caught")
        void detectionRises() {
            // A flat chance would make defection a price, and a price is something a player budgets
            // for. A rising one cannot be budgeted: the first is usually free, the fifth usually is
            // not, and the seller never knows which one costs them.
            assertThat(SecondaryMarket.detectionChance(0))
                    .isEqualTo(SecondaryMarket.BASE_DETECTION_PERCENT);
            assertThat(SecondaryMarket.detectionChance(1))
                    .isGreaterThan(SecondaryMarket.detectionChance(0));
            assertThat(SecondaryMarket.detectionChance(4))
                    .isGreaterThan(SecondaryMarket.detectionChance(2));
        }

        @Test
        @DisplayName("it saturates below certainty, so nothing is ever a foregone conclusion")
        void neverCertain() {
            assertThat(SecondaryMarket.detectionChance(100)).isLessThan(100);
        }

        @Test
        @DisplayName("⚠ reputation is slow to build and quick to lose")
        void asymmetric() {
            // If honesty paid back as fast as defection cost, the optimal play would be to
            // alternate and the score would measure nothing but volume.
            assertThat(SecondaryMarket.DELIVERY_REWARD)
                    .isLessThan(SecondaryMarket.DEFECTION_PENALTY);
        }

        @Test
        @DisplayName("an uncaught defection still counts toward the next one being caught")
        void uncaughtStillCounts() {
            SoloSave save = new SoloSave();
            // A source that never triggers detection.
            Random never = new Random() {
                @Override
                public int nextInt(int bound) {
                    return 99;
                }
            };
            SecondaryMarket.defect(save, never);

            assertThat(save.traderReputation).isZero();
            // The tally moved even though the penalty did not — which is what makes the second one
            // riskier than the first.
            assertThat(save.traderDefections).isEqualTo(1);
        }

        @Test
        @DisplayName("a caught defection costs reputation and is recorded once, not re-rolled")
        void caughtCostsOnce() {
            SoloSave save = new SoloSave();
            Random always = new Random() {
                @Override
                public int nextInt(int bound) {
                    return 0;
                }
            };
            assertThat(SecondaryMarket.defect(save, always)).isTrue();
            int after = save.traderReputation;

            // Reading it again does not re-roll. A chance a player can re-roll by reloading is not a
            // chance, it is a delay — the same rule the sweep's frozen result follows.
            assertThat(SecondaryMarket.reputation(save)).isEqualTo(after);
            assertThat(after).isEqualTo(-SecondaryMarket.DEFECTION_PENALTY);
        }

        @Test
        @DisplayName("standing reads as risk, not as morality")
        void standingIsAboutDelivery() {
            SoloSave save = new SoloSave();
            assertThat(SecondaryMarket.standing(save)).contains("unproven");
            save.traderReputation = -80;
            // The market does not care whether you are a good person, only whether it arrives.
            assertThat(SecondaryMarket.standing(save)).contains("do not pay this trader first");
        }
    }
}
