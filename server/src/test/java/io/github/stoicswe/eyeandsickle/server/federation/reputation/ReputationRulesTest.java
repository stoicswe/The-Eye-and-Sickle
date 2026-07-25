package io.github.stoicswe.eyeandsickle.server.federation.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The AIMD reputation update and the separate uptime decay — {@code
 * docs/architecture/05-validator-quorum.md} §3 and §4.
 *
 * <p>The three §3 cases are deliberately <em>not</em> one formula, and §4's no-show is deliberately on
 * a different field. "Reward slowly, punish fast" is the security shape: a validator must not be able
 * to build trust cheaply and spend it in one betrayal, and an offline validator must not be branded a
 * liar. These tests pin each case's arithmetic and the boundaries between them.
 */
class ReputationRulesTest {

    private static final double ALPHA = 0.05; // §3.1 recommended
    private static final double BETA = 0.25; // §3.2 recommended
    private static final double GAMMA = 0.10; // §4 recommended
    private static final double FLOOR = 0.10; // §3.3 recommended

    @Nested
    @DisplayName("§3.1 correct vote — additive increase toward 1")
    class CorrectVote {

        @Test
        @DisplayName("moves reputation up by alpha times the remaining distance to 1")
        void additiveIncrease() {
            // r = 0.4 + 0.05 * (1 - 0.4) = 0.43
            assertThat(ReputationRules.afterCorrectVote(0.40, ALPHA)).isCloseTo(0.43, within(1e-12));
            // r = 0 + 0.05 * 1 = 0.05
            assertThat(ReputationRules.afterCorrectVote(0.0, ALPHA)).isCloseTo(0.05, within(1e-12));
        }

        @Test
        @DisplayName("is a fixed point at 1 — a fully trusted validator has nothing to gain")
        void fixedPointAtOne() {
            assertThat(ReputationRules.afterCorrectVote(1.0, ALPHA)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("never exceeds 1, even after a thousand correct votes")
        void neverExceedsOne() {
            double r = 0.4;
            for (int i = 0; i < 1_000; i++) {
                r = ReputationRules.afterCorrectVote(r, ALPHA);
            }
            assertThat(r).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("builds trust over dozens of duels, not instantly")
        void buildsSlowly() {
            // One correct vote barely moves a newcomer — the anti-defection property.
            double afterOne = ReputationRules.afterCorrectVote(0.40, ALPHA);
            assertThat(afterOne).isLessThan(0.44);

            // It takes many correct votes to cross a high-trust bar. Count how many to exceed 0.9.
            double r = 0.40;
            int votes = 0;
            while (r < 0.90) {
                r = ReputationRules.afterCorrectVote(r, ALPHA);
                votes++;
            }
            // "Dozens", not a handful: a validator cannot rocket to high trust right before defecting.
            assertThat(votes).isGreaterThan(30);
        }

        @Test
        @DisplayName("is asymptotic — approaches but does not reach 1 over a long clean record")
        void asymptotic() {
            double r = 0.40;
            for (int i = 0; i < 100; i++) {
                r = ReputationRules.afterCorrectVote(r, ALPHA);
            }
            // gap shrinks by (1 - alpha) each step: still short of 1 but very close.
            assertThat(r).isGreaterThan(0.99).isLessThan(1.0);
        }

        @Test
        @DisplayName("each step is strictly larger than the last, up to the fixed point")
        void monotoneIncreasing() {
            double previous = 0.20;
            for (int i = 0; i < 20; i++) {
                double next = ReputationRules.afterCorrectVote(previous, ALPHA);
                assertThat(next).isGreaterThan(previous);
                previous = next;
            }
        }
    }

    @Nested
    @DisplayName("§3.2 divergent vote — multiplicative decrease")
    class DivergentVote {

        @Test
        @DisplayName("cuts reputation by a fixed proportion")
        void multiplicativeDecrease() {
            // r = 0.8 * (1 - 0.25) = 0.6
            assertThat(ReputationRules.afterDivergentVote(0.80, BETA)).isCloseTo(0.60, within(1e-12));
        }

        @Test
        @DisplayName("is not catastrophic on its own — divergence can be an honest race")
        void notCatastrophic() {
            // A single divergence leaves most of a high reputation intact; it is not the slash.
            assertThat(ReputationRules.afterDivergentVote(0.80, BETA)).isGreaterThan(0.50);
        }

        @Test
        @DisplayName("bites a high-reputation validator harder in absolute terms than a low one")
        void proportionalBite() {
            double highDrop = 0.80 - ReputationRules.afterDivergentVote(0.80, BETA);
            double lowDrop = 0.20 - ReputationRules.afterDivergentVote(0.20, BETA);
            assertThat(highDrop).isGreaterThan(lowDrop);
        }

        @Test
        @DisplayName("never drives reputation negative, however many times it is applied")
        void neverNegative() {
            double r = 0.80;
            for (int i = 0; i < 200; i++) {
                r = ReputationRules.afterDivergentVote(r, BETA);
                assertThat(r).isGreaterThanOrEqualTo(0.0);
            }
            // Approaches zero asymptotically but never crosses it.
            assertThat(r).isGreaterThanOrEqualTo(0.0).isLessThan(1e-6);
        }
    }

    @Nested
    @DisplayName("§3.3 equivocation — the hard slash to the floor")
    class Equivocation {

        @Test
        @DisplayName("slams a trusted validator down to the floor")
        void slashesToFloor() {
            assertThat(ReputationRules.afterEquivocation(0.95, FLOOR)).isCloseTo(FLOOR, within(1e-12));
        }

        @Test
        @DisplayName("never raises a validator already below the floor — getting caught cannot help")
        void neverRaisesBelowFloorValidator() {
            // min(r, floor), not an assignment: double-signing must never be a path to MORE trust.
            assertThat(ReputationRules.afterEquivocation(0.05, FLOOR)).isCloseTo(0.05, within(1e-12));
        }

        @Test
        @DisplayName("leaves a validator exactly at the floor at the floor")
        void idempotentAtFloor() {
            assertThat(ReputationRules.afterEquivocation(FLOOR, FLOOR)).isCloseTo(FLOOR, within(1e-12));
        }

        @Test
        @DisplayName("is far harsher than a single divergence for the same validator")
        void harsherThanDivergence() {
            double afterDivergence = ReputationRules.afterDivergentVote(0.95, BETA);
            double afterSlash = ReputationRules.afterEquivocation(0.95, FLOOR);
            // Provable dishonesty gets no benefit of the doubt; honest disagreement does.
            assertThat(afterSlash).isLessThan(afterDivergence);
        }
    }

    @Nested
    @DisplayName("§4 no-show — decays uptime, never reputation")
    class NoShow {

        @Test
        @DisplayName("multiplicatively decays uptime by gamma")
        void decaysUptime() {
            // uptime = 1 * (1 - 0.10) = 0.90
            assertThat(ReputationRules.afterNoShow(1.0, GAMMA)).isCloseTo(0.90, within(1e-12));
        }

        @Test
        @DisplayName("is lighter than the divergence penalty — offline is weaker evidence than wrong")
        void lighterThanDivergence() {
            double uptimeDrop = 1.0 - ReputationRules.afterNoShow(1.0, GAMMA);
            double reputationDrop = 1.0 - ReputationRules.afterDivergentVote(1.0, BETA);
            assertThat(uptimeDrop).isLessThan(reputationDrop);
        }

        @Test
        @DisplayName("never drives uptime negative")
        void neverNegative() {
            double uptime = 1.0;
            for (int i = 0; i < 200; i++) {
                uptime = ReputationRules.afterNoShow(uptime, GAMMA);
                assertThat(uptime).isGreaterThanOrEqualTo(0.0);
            }
        }
    }

    @Nested
    @DisplayName("applyToReputation — the case split keyed by conduct")
    class ApplyToReputation {

        @Test
        @DisplayName("routes each conduct to its own arithmetic")
        void routesEachConduct() {
            assertThat(ReputationRules.applyToReputation(ValidatorConduct.CORRECT, 0.40, ALPHA, BETA, FLOOR))
                    .isCloseTo(ReputationRules.afterCorrectVote(0.40, ALPHA), within(1e-12));
            assertThat(ReputationRules.applyToReputation(ValidatorConduct.DIVERGENT, 0.80, ALPHA, BETA, FLOOR))
                    .isCloseTo(ReputationRules.afterDivergentVote(0.80, BETA), within(1e-12));
            assertThat(ReputationRules.applyToReputation(ValidatorConduct.EQUIVOCATED, 0.95, ALPHA, BETA, FLOOR))
                    .isCloseTo(ReputationRules.afterEquivocation(0.95, FLOOR), within(1e-12));
        }

        @Test
        @DisplayName("leaves reputation untouched for a no-show — the §4 separation, made mechanical")
        void noShowLeavesReputationUntouched() {
            // The single most important cross-check in this class: a no-show must never move
            // reputation. If this ever changes, §4's whole reason for existing is gone.
            double reputation = 0.73;
            assertThat(ReputationRules.applyToReputation(ValidatorConduct.NO_SHOW, reputation, ALPHA, BETA, FLOOR))
                    .isEqualTo(reputation);
        }
    }

    @Nested
    @DisplayName("clamping keeps results inside the schema's [0, 1]")
    class Clamping {

        @Test
        @DisplayName("collapses a NaN result to 0 rather than propagating it")
        void nanCollapsesToZero() {
            // A NaN coefficient that slipped past validation would make reputation NaN, and a NaN
            // reputation makes every later comparison false — excluding the validator from sampling
            // forever. Clamping NaN to 0 keeps it recoverable and comparable.
            assertThat(ReputationRules.afterCorrectVote(0.5, Double.NaN)).isEqualTo(0.0);
            assertThat(ReputationRules.afterNoShow(0.5, Double.NaN)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("holds the correct-vote result at 1 rather than overshooting")
        void clampsUpperBound() {
            // A large alpha cannot push reputation past 1; the schema CHECK would refuse it otherwise.
            assertThat(ReputationRules.afterCorrectVote(0.99, 0.99)).isLessThanOrEqualTo(1.0);
        }
    }
}
