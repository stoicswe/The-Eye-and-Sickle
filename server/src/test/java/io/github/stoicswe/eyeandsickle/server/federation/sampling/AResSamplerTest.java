package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The A-Res weighted-reservoir sampler — {@code docs/architecture/05-validator-quorum.md} §2.3.
 *
 * <p>Correctness here is Invariant I15's foundation: the committee for a duel must be unpredictable in
 * advance (so it cannot be colluded), weighted toward proven validators (so reputation matters), drawn
 * without replacement (so nobody votes twice), and never able to deadlock a fresh pool (§2.5). Every
 * randomness-dependent assertion runs against a <em>seeded</em> {@link RandomGenerator}, so the whole
 * suite is reproducible — the statistical claims are asserted with bounds wide enough to hold for any
 * seed yet tight enough to catch a broken weighting.
 */
class AResSamplerTest {

    private static SampledValidator v(String suffix, double reputation, double uptime) {
        return SampledValidator.of("did:plc:validator" + suffix, reputation, uptime);
    }

    private static List<String> dids(List<SampledValidator> committee) {
        return committee.stream().map(SampledValidator::validatorDid).toList();
    }

    @Nested
    @DisplayName("determinism under a fixed seed")
    class Determinism {

        @Test
        @DisplayName("the same seed and candidates draw the identical committee")
        void reproducibleUnderSeed() {
            List<SampledValidator> candidates =
                    List.of(v("1", 0.9, 1.0), v("2", 0.5, 1.0), v("3", 0.3, 1.0), v("4", 0.7, 0.8), v("5", 0.2, 1.0));

            List<SampledValidator> first = AResSampler.sample(candidates, 3, new Random(20260724L));
            List<SampledValidator> second = AResSampler.sample(candidates, 3, new Random(20260724L));

            // Identical order, not merely identical membership: the draw must be byte-for-byte
            // reproducible, which is what lets its distribution be asserted at all.
            assertThat(dids(first)).isEqualTo(dids(second));
        }

        @Test
        @DisplayName("different seeds draw different committees (unpredictability)")
        void differentSeedsDiffer() {
            List<SampledValidator> candidates = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                candidates.add(v(String.valueOf(i), 0.5, 1.0));
            }

            // Collect the 7-of-20 draw for a spread of seeds; if the committee were predictable
            // regardless of the entropy source they would all coincide. §2.4 is precisely that "which
            // specific committee" must not be knowable in advance.
            Set<List<String>> distinctDraws = new HashSet<>();
            for (long seed = 0; seed < 8; seed++) {
                distinctDraws.add(dids(AResSampler.sample(candidates, 7, new Random(seed))));
            }
            assertThat(distinctDraws).hasSizeGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("committee size and without-replacement")
    class Shape {

        @Test
        @DisplayName("draws exactly the requested committee size when the pool is large enough")
        void respectsCommitteeSize() {
            List<SampledValidator> candidates = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                candidates.add(v(String.valueOf(i), 0.5, 1.0));
            }

            assertThat(AResSampler.sample(candidates, 7, new Random(3L))).hasSize(7);
        }

        @Test
        @DisplayName("never returns a validator twice")
        void withoutReplacement() {
            List<SampledValidator> candidates = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                candidates.add(v(String.valueOf(i), 0.4 + i * 0.05, 1.0));
            }

            for (long seed = 0; seed < 50; seed++) {
                List<String> drawn = dids(AResSampler.sample(candidates, 5, new Random(seed)));
                // A duplicate would let one server's reputation count twice toward a threshold.
                assertThat(drawn).doesNotHaveDuplicates().hasSize(5);
            }
        }

        @Test
        @DisplayName("returns the smaller pool, not a padded committee, when fewer candidates qualify")
        void poolSmallerThanCommittee() {
            List<SampledValidator> candidates = List.of(v("1", 0.9, 1.0), v("2", 0.5, 1.0), v("3", 0.3, 1.0));

            List<SampledValidator> drawn = AResSampler.sample(candidates, 7, new Random(5L));

            // The caller gets 3 and the BFT threshold scales to 3; padding to 7 would invent authority.
            assertThat(drawn).hasSize(3);
            assertThat(dids(drawn))
                    .containsExactlyInAnyOrder("did:plc:validator1", "did:plc:validator2", "did:plc:validator3");
        }
    }

    @Nested
    @DisplayName("zero-weight candidates")
    class ZeroWeight {

        @Test
        @DisplayName("a validator with zero reputation is never drawn")
        void skipsZeroReputation() {
            List<SampledValidator> candidates =
                    List.of(v("1", 0.0, 1.0), v("2", 0.6, 1.0), v("3", 0.6, 1.0), v("4", 0.6, 1.0));

            for (long seed = 0; seed < 40; seed++) {
                List<String> drawn = dids(AResSampler.sample(candidates, 3, new Random(seed)));
                // Zero reputation means zero weight (§2.2); such a validator is not an also-ran, it is
                // not a candidate. Drawing it whenever the pool is short would hand it authority.
                assertThat(drawn).doesNotContain("did:plc:validator1");
            }
        }

        @Test
        @DisplayName("a validator with zero uptime is never drawn")
        void skipsZeroUptime() {
            List<SampledValidator> candidates =
                    List.of(v("1", 0.9, 0.0), v("2", 0.6, 1.0), v("3", 0.6, 1.0), v("4", 0.6, 1.0));

            for (long seed = 0; seed < 40; seed++) {
                List<String> drawn = dids(AResSampler.sample(candidates, 3, new Random(seed)));
                assertThat(drawn).doesNotContain("did:plc:validator1");
            }
        }

        @Test
        @DisplayName("an all-zero-weight pool yields an empty committee (the caller must detect deadlock)")
        void allZeroWeightIsEmpty() {
            List<SampledValidator> candidates = List.of(v("1", 0.0, 1.0), v("2", 0.6, 0.0), v("3", 0.0, 0.0));

            // The sampler returns empty rather than padding; QuorumService turns this into the "no
            // eligible validators" failure, never a silent committee of the unsamplable.
            assertThat(AResSampler.sample(candidates, 7, new Random(7L))).isEmpty();
        }

        @Test
        @DisplayName("an empty candidate list yields an empty committee")
        void emptyCandidatesIsEmpty() {
            assertThat(AResSampler.sample(List.of(), 7, new Random(7L))).isEmpty();
        }
    }

    @Nested
    @DisplayName("the cold-start floor prevents deadlock (§2.5)")
    class ColdStart {

        @Test
        @DisplayName("a pool of only floor-reputation newcomers is still samplable")
        void newcomerPoolDoesNotDeadlock() {
            // Every validator is a brand-new one sitting at the cold-start floor (0.4) with full uptime.
            List<SampledValidator> newcomers = List.of(v("1", 0.4, 1.0), v("2", 0.4, 1.0), v("3", 0.4, 1.0));

            List<SampledValidator> drawn = AResSampler.sample(newcomers, 7, new Random(11L));

            // If the floor did not keep weight positive, this pool would be unsamplable forever — the
            // cold-start deadlock §2.5 exists to prevent.
            assertThat(drawn).hasSize(3);
        }

        @Test
        @DisplayName("a fresh validator can still be drawn alongside a near-perfect one")
        void newcomerIsSelectableAgainstProvenValidator() {
            SampledValidator proven = v("proven", 0.99, 1.0);
            SampledValidator newcomer = v("new", 0.40, 1.0);
            List<SampledValidator> pool = List.of(proven, newcomer);

            int newcomerDraws = 0;
            RandomGenerator random = new Random(4242L);
            for (int i = 0; i < 10_000; i++) {
                List<String> drawn = dids(AResSampler.sample(pool, 1, random));
                if (drawn.contains("did:plc:validatornew")) {
                    newcomerDraws++;
                }
            }

            // Single-draw probability is weight/Σweight = 0.40/1.39 ≈ 0.288, so ~2880 of 10000. A
            // floor validator that could NEVER be drawn (count 0) is the deadlock; assert it is drawn
            // a large number of times, but still a clear minority to the proven validator.
            assertThat(newcomerDraws).isGreaterThan(1_500).isLessThan(4_500);
        }
    }

    @Nested
    @DisplayName("weight biases selection frequency (§2.3)")
    class WeightBias {

        @Test
        @DisplayName("single-draw selection frequency tracks weight / total weight")
        void singleDrawFrequencyMatchesWeightShare() {
            // Weights sum to 1.0, so each validator's expected single-draw share equals its weight.
            SampledValidator heavy = v("heavy", 0.9, 1.0);
            SampledValidator light = v("light", 0.1, 1.0);
            List<SampledValidator> pool = List.of(heavy, light);

            int heavyDraws = 0;
            RandomGenerator random = new Random(2024L);
            int trials = 10_000;
            for (int i = 0; i < trials; i++) {
                if (dids(AResSampler.sample(pool, 1, random)).contains("did:plc:validatorheavy")) {
                    heavyDraws++;
                }
            }

            // Expected 9000; std dev ≈ 30, so [8500, 9500] is >15σ — safe for any seed, yet it fails
            // hard if the weighting is inverted or ignored.
            assertThat(heavyDraws).isBetween(8_500, 9_500);
        }

        @Test
        @DisplayName("higher weight is selected strictly more often across three tiers")
        void higherWeightSelectedMoreOften() {
            SampledValidator a = v("a", 0.6, 1.0);
            SampledValidator b = v("b", 0.3, 1.0);
            SampledValidator c = v("c", 0.1, 1.0);
            List<SampledValidator> pool = List.of(a, b, c);

            Map<String, Integer> counts = new HashMap<>();
            RandomGenerator random = new Random(77L);
            for (int i = 0; i < 12_000; i++) {
                String drawn = dids(AResSampler.sample(pool, 1, random)).getFirst();
                counts.merge(drawn, 1, Integer::sum);
            }

            int ca = counts.getOrDefault("did:plc:validatora", 0);
            int cb = counts.getOrDefault("did:plc:validatorb", 0);
            int cc = counts.getOrDefault("did:plc:validatorc", 0);

            // Monotone with weight: 0.6 > 0.3 > 0.1. Means are 7200 / 3600 / 1200, well separated.
            assertThat(ca).isGreaterThan(cb);
            assertThat(cb).isGreaterThan(cc);
            assertThat(ca).isBetween(6_600, 7_800);
            assertThat(cc).isBetween(800, 1_600);
        }

        @Test
        @DisplayName("within a committee draw, a heavier validator has a higher inclusion rate")
        void heavierValidatorIncludedMoreOftenInCommittee() {
            // One heavy validator among many light ones; draw a committee of 3 from 6, repeatedly.
            List<SampledValidator> pool = List.of(
                    v("heavy", 0.95, 1.0),
                    v("l1", 0.15, 1.0),
                    v("l2", 0.15, 1.0),
                    v("l3", 0.15, 1.0),
                    v("l4", 0.15, 1.0),
                    v("l5", 0.15, 1.0));

            int heavyIncluded = 0;
            int lightIncluded = 0;
            RandomGenerator random = new Random(555L);
            for (int i = 0; i < 5_000; i++) {
                List<String> drawn = dids(AResSampler.sample(pool, 3, random));
                if (drawn.contains("did:plc:validatorheavy")) {
                    heavyIncluded++;
                }
                if (drawn.contains("did:plc:validatorl1")) {
                    lightIncluded++;
                }
            }

            // The heavy validator's inclusion probability far exceeds any single light one's.
            assertThat(heavyIncluded).isGreaterThan(lightIncluded);
        }
    }

    @Nested
    @DisplayName("argument validation")
    class Validation {

        @Test
        @DisplayName("rejects a committee size below one")
        void rejectsNonPositiveCommitteeSize() {
            List<SampledValidator> candidates = List.of(v("1", 0.5, 1.0));
            // A committee of zero adjudicates nothing; it is a caller bug, not an empty draw.
            assertThatThrownBy(() -> AResSampler.sample(candidates, 0, new Random(1L)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AResSampler.sample(candidates, -3, new Random(1L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects null candidates and null randomness")
        void rejectsNulls() {
            List<SampledValidator> candidates = List.of(v("1", 0.5, 1.0));
            assertThatThrownBy(() -> AResSampler.sample(null, 1, new Random(1L)))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> AResSampler.sample(candidates, 1, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects a null candidate element rather than sampling around it")
        void rejectsNullCandidate() {
            List<SampledValidator> candidates = Arrays.asList(v("1", 0.5, 1.0), null);
            assertThatThrownBy(() -> AResSampler.sample(candidates, 1, new Random(1L)))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
