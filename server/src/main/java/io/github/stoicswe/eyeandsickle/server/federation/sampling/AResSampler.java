package io.github.stoicswe.eyeandsickle.server.federation.sampling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Weighted random sampling without replacement, by the A-Res algorithm — {@code
 * docs/architecture/05-validator-quorum.md} §2.3.
 *
 * <h2>Why not "sort by weight, take the top N"</h2>
 *
 * Deterministic top-N would pick the same high-reputation servers for <em>every</em> duel, so an
 * attacker who compromised or bribed those few would own every outcome (§2.4). A-Res keeps the good
 * property — high weight means more likely to be picked — while making <em>which</em> specific
 * committee is drawn unknowable in advance. That unpredictability is the security property; the
 * randomness source is therefore cryptographic in production (see {@code FederationConfiguration}).
 *
 * <h2>The algorithm (Efraimidis–Spirakis, 2006)</h2>
 *
 * Each candidate {@code i} with weight {@code wᵢ > 0} draws {@code uᵢ ~ Uniform(0,1)} and is given a
 * key {@code kᵢ = uᵢ^(1/wᵢ)}. The {@code N} candidates with the largest keys are the sample. This
 * yields, for a single draw ({@code N = 1}), selection probability exactly {@code wᵢ / Σw}, and for
 * larger {@code N} an inclusion probability that rises monotonically with weight — the behaviour the
 * distribution test asserts.
 *
 * <p>Keys are computed in log space: {@code ln(kᵢ) = ln(uᵢ) / wᵢ}. Two reasons. First, {@code
 * uᵢ^(1/wᵢ)} underflows to 0 for a small {@code u} and a large {@code 1/wᵢ} (a low-weight validator),
 * which would collapse many distinct keys to a tie at zero and destroy the ordering exactly among the
 * candidates the algorithm most needs to distinguish. Second, {@code ln(uᵢ)} is negative, so dividing
 * by a larger weight gives a key closer to zero — i.e. larger — so "largest key" still means "favour
 * high weight", with no precision lost at the bottom of the range.
 *
 * <h2>Offline and deterministic under a fixed seed</h2>
 *
 * The only entropy is the injected {@link RandomGenerator}. A seeded generator makes a draw
 * reproducible, which is what lets the distribution be asserted in a plain unit test with no database
 * and no flakiness.
 */
public final class AResSampler {

    private AResSampler() {}

    /**
     * Draws a committee of up to {@code committeeSize} validators, weighted by {@link
     * SampledValidator#weight()}, without replacement.
     *
     * <p>Candidates with zero weight are skipped — a validator with no reputation or no uptime is
     * unsamplable (§2.2), and a newcomer stays samplable only because §2.5's floor keeps its
     * reputation above zero. If fewer than {@code committeeSize} candidates have positive weight, the
     * result is that smaller set: the caller gets a smaller committee rather than a padded one, and
     * the BFT threshold then scales to the size actually drawn ({@code QuorumCommittee} derives {@code
     * f} from the committee it is given). A caller that requires a full committee must check the size.
     *
     * @param candidates every eligible validator with its frozen weight factors; not copied defensively
     *     for cost reasons, so do not mutate it during the call
     * @param committeeSize {@code N}, the desired committee size; must be positive
     * @param random the entropy source — cryptographic in production, seeded in tests
     * @return the sampled validators, ordered highest key first; never larger than the count of
     *     positive-weight candidates
     */
    public static List<SampledValidator> sample(
            List<SampledValidator> candidates, int committeeSize, RandomGenerator random) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(random, "random");
        if (committeeSize < 1) {
            throw new IllegalArgumentException("committeeSize must be >= 1, was " + committeeSize);
        }

        List<Keyed> keyed = new ArrayList<>(candidates.size());
        for (SampledValidator candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            double weight = candidate.weight();
            if (weight <= 0.0) {
                // Skipped, not assigned a sentinel key: a zero-weight validator is not "least likely",
                // it is not a candidate at all (§2.2). Including it as an also-ran would let it be
                // drawn whenever the pool is smaller than N, handing authority to a validator with no
                // standing.
                continue;
            }
            keyed.add(new Keyed(candidate, logKey(weight, random)));
        }

        // Full sort rather than a bounded heap: a home server's validator pool is small, the clarity
        // is worth more than the asymptotics, and a stable descending sort makes the draw's ordering
        // reproducible under a fixed seed down to ties.
        keyed.sort(Comparator.comparingDouble(Keyed::logKey).reversed());

        int take = Math.min(committeeSize, keyed.size());
        List<SampledValidator> committee = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            committee.add(keyed.get(i).validator());
        }
        return committee;
    }

    /**
     * {@code ln(kᵢ) = ln(uᵢ) / wᵢ}.
     *
     * <p>{@code nextDouble()} yields {@code [0, 1)}; a drawn {@code 0} would make {@code ln(0) = −∞}
     * and hand the validator the smallest possible key deterministically, so it is nudged to the
     * smallest positive double instead. This costs nothing to a fair draw — the event has probability
     * about {@code 2⁻⁵³} — and removes a degenerate {@code −∞} tie from the sort.
     */
    private static double logKey(double weight, RandomGenerator random) {
        double u = random.nextDouble();
        if (u <= 0.0) {
            u = Double.MIN_VALUE;
        }
        return Math.log(u) / weight;
    }

    /** A candidate paired with its log-space A-Res key, for sorting. */
    private record Keyed(SampledValidator validator, double logKey) {}
}
