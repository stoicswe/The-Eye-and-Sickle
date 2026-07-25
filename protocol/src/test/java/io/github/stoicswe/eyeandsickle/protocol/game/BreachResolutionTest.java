package io.github.stoicswe.eyeandsickle.protocol.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The breach attempt's economy-facing output: {@link BreachOutcome}, {@link TargetState}, {@link
 * DifficultyTier} and the {@link ResolutionRecord} that carries them.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §2 is explicit that this record is the stable API the
 * rest of the game reads, even while the puzzle content churns. Two invariants ride on it — I7
 * (proof-of-skill is tier-gated, never count-gated, and only against a live target) and I13 (salvage
 * is gated on engagement tier) — so these tests are as much about what the record refuses to decide
 * as about what it holds.
 */
class BreachResolutionTest {

    @Nested
    @DisplayName("the outcome vocabulary")
    class Outcomes {

        @Test
        @DisplayName("is breached, failed or aborted — the three §2 names")
        void closedSet() {
            assertThat(Arrays.stream(BreachOutcome.values()).map(Enum::name).toList())
                    .containsExactly("BREACHED", "FAILED", "ABORTED");
        }

        @Test
        @DisplayName("distinguishes walking away from being traced")
        void abortIsNotFailure() {
            // They differ in consequence (§4): a completed trace fires the target's response, an
            // abort spends only the noise already made. Collapsing them would remove the escape hatch.
            assertThat(BreachOutcome.ABORTED).isNotEqualTo(BreachOutcome.FAILED);
        }
    }

    @Nested
    @DisplayName("the target state")
    class Targets {

        @Test
        @DisplayName("is live or dormant")
        void closedSet() {
            assertThat(Arrays.stream(TargetState.values()).map(Enum::name).toList())
                    .containsExactly("LIVE", "DORMANT");
        }
    }

    @Nested
    @DisplayName("the difficulty tier")
    class Tiers {

        @Test
        @DisplayName("accepts every tier on the proposed scale")
        void acceptsTheWholeScale() {
            for (int tier = DifficultyTier.LOWEST; tier <= DifficultyTier.HIGHEST; tier++) {
                assertThat(DifficultyTier.of(tier).tier()).isEqualTo(tier);
            }
        }

        @Test
        @DisplayName("rejects a tier below the scale")
        void rejectsBelowScale() {
            assertThatThrownBy(() -> DifficultyTier.of(DifficultyTier.LOWEST - 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scale");
            assertThatThrownBy(() -> DifficultyTier.of(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DifficultyTier.of(-3)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a tier above the scale")
        void rejectsAboveScale() {
            // [PROPOSAL] boundary (docs/design/05 §3.3). If the range ever widens, both ends of the
            // wire ship together — an older client rejects an unknown tier at deserialization time,
            // which reads as a corrupt response rather than a version skew.
            assertThatThrownBy(() -> DifficultyTier.of(DifficultyTier.HIGHEST + 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scale");
            assertThatThrownBy(() -> DifficultyTier.of(Integer.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("is a small legible scale, as §2.4 requires of the gate knob")
        void scaleIsSmallAndLegible() {
            assertThat(DifficultyTier.LOWEST).isEqualTo(1);
            assertThat(DifficultyTier.HIGHEST)
                    .isGreaterThan(DifficultyTier.LOWEST)
                    .isLessThanOrEqualTo(9);
        }

        @Test
        @DisplayName("orders low to high")
        void orders() {
            assertThat(DifficultyTier.of(2)).isLessThan(DifficultyTier.of(3));
            assertThat(DifficultyTier.of(5)).isGreaterThan(DifficultyTier.of(1));
            assertThat(DifficultyTier.of(3)).isEqualByComparingTo(DifficultyTier.of(3));

            List<DifficultyTier> sorted = Stream.of(DifficultyTier.of(4), DifficultyTier.of(1), DifficultyTier.of(3))
                    .sorted()
                    .toList();
            assertThat(sorted).containsExactly(DifficultyTier.of(1), DifficultyTier.of(3), DifficultyTier.of(4));
        }

        @Test
        @DisplayName("equal tiers are equal values")
        void valueEquality() {
            assertThat(DifficultyTier.of(3)).isEqualTo(new DifficultyTier(3)).hasSameHashCodeAs(new DifficultyTier(3));
        }
    }

    @Nested
    @DisplayName("the resolution record")
    class Records {

        @Test
        @DisplayName("carries exactly the four fields the contract names")
        void carriesTheContract() {
            ResolutionRecord record = new ResolutionRecord(
                    PuzzleClass.LOGIC, DifficultyTier.of(4), TargetState.LIVE, BreachOutcome.BREACHED);

            assertThat(record.puzzleClass()).isEqualTo(PuzzleClass.LOGIC);
            assertThat(record.difficultyTier()).isEqualTo(DifficultyTier.of(4));
            assertThat(record.liveOrDormant()).isEqualTo(TargetState.LIVE);
            assertThat(record.outcome()).isEqualTo(BreachOutcome.BREACHED);

            assertThat(ResolutionRecord.class.getRecordComponents())
                    .as("adding a fifth field changes an API docs/design/05 §2 asks to be built to last")
                    .hasSize(4);
        }

        @Test
        @DisplayName("requires every field — a partial record cannot be gated on")
        void everyFieldRequired() {
            assertThatThrownBy(() ->
                            new ResolutionRecord(null, DifficultyTier.of(1), TargetState.LIVE, BreachOutcome.BREACHED))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() ->
                            new ResolutionRecord(PuzzleClass.LOGIC, null, TargetState.LIVE, BreachOutcome.BREACHED))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() ->
                            new ResolutionRecord(PuzzleClass.LOGIC, DifficultyTier.of(1), null, BreachOutcome.BREACHED))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> new ResolutionRecord(PuzzleClass.LOGIC, DifficultyTier.of(1), TargetState.LIVE, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("records losses and dormant targets too — it is a log, not a certificate")
        void recordsTheBoringCases() {
            // If only wins were representable, the history an auditor or an anti-cheat review reads
            // would be missing exactly the rows they care about.
            assertThat(new ResolutionRecord(
                            PuzzleClass.TIMING, DifficultyTier.of(1), TargetState.DORMANT, BreachOutcome.FAILED))
                    .isNotNull();
            assertThat(new ResolutionRecord(
                            PuzzleClass.TRAVERSAL, DifficultyTier.of(5), TargetState.LIVE, BreachOutcome.ABORTED))
                    .isNotNull();
        }

        @Test
        @DisplayName("two attempts at the same class and tier are the same value — so counting them is meaningless")
        void identicalAttemptsAreEqual() {
            // Invariant I7: proof-of-skill is tier-gated, never count-gated. Two indistinguishable
            // records are one fact ("this class was solved at this tier, live"), which is the only
            // fact the gate is allowed to read. Any code reaching for count(*) over these rows is the
            // farming exploit arriving.
            ResolutionRecord first = new ResolutionRecord(
                    PuzzleClass.CREDENTIAL, DifficultyTier.of(3), TargetState.LIVE, BreachOutcome.BREACHED);
            ResolutionRecord second = new ResolutionRecord(
                    PuzzleClass.CREDENTIAL, DifficultyTier.of(3), TargetState.LIVE, BreachOutcome.BREACHED);

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }

        @Test
        @DisplayName("a live and a dormant solve are different records")
        void liveIsNotDormant() {
            ResolutionRecord live = new ResolutionRecord(
                    PuzzleClass.ENUMERATION, DifficultyTier.of(3), TargetState.LIVE, BreachOutcome.BREACHED);
            ResolutionRecord dormant = new ResolutionRecord(
                    PuzzleClass.ENUMERATION, DifficultyTier.of(3), TargetState.DORMANT, BreachOutcome.BREACHED);

            assertThat(live).isNotEqualTo(dormant);
        }

        @Test
        @DisplayName("answers what happened, never what it unlocks")
        void noGateEvaluationHelpers() {
            // The charter's line, made mechanical for the one type most likely to cross it: an
            // `isProofOfSkillEligible()` here would be half a gate check living in the module that
            // forbids gate checks, and the per-class threshold would follow within a release.
            List<String> ruleShapedMethods = Arrays.stream(ResolutionRecord.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(name -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        return lower.contains("eligib")
                                || lower.contains("unlock")
                                || lower.contains("satisf")
                                || lower.contains("proof")
                                || lower.contains("qualif")
                                || lower.contains("credit");
                    })
                    .toList();

            assertThat(ruleShapedMethods)
                    .as("gate evaluation is the server's job (Invariant I14)")
                    .isEmpty();
        }
    }
}
