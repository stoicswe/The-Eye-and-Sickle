package io.github.stoicswe.eyeandsickle.server.federation;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the federation slice's configuration and its two ambient dependencies.
 *
 * <h2>Why the properties are registered here</h2>
 *
 * {@link QuorumProperties} is a record, bound by constructor, so it has to be registered through
 * {@code @EnableConfigurationProperties} — annotating the record {@code @Component} fails at startup
 * with a constructor-binding message, the same footgun {@code PersistenceConfiguration} documents.
 *
 * <h2>Randomness is a bean, not a {@code new} in the sampler</h2>
 *
 * Validator sampling must be <em>unpredictable</em> in production ({@code
 * docs/architecture/05-validator-quorum.md} §2.4 — a predictable committee is a collusion target),
 * yet <em>deterministic</em> under test so the distribution can be asserted. A single injected
 * {@link RandomGenerator} squares that: production gets a {@link SecureRandom}, and a test constructs
 * the service with a seeded generator. {@code SecureRandom} is thread-safe, so one instance serves
 * every concurrent duel.
 *
 * @see io.github.stoicswe.eyeandsickle.server.federation.sampling.AResSampler
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(QuorumProperties.class)
class FederationConfiguration {

    /**
     * The randomness source for validator sampling. A cryptographic source, deliberately: a
     * predictable draw makes the committee knowable in advance, which §2.4 identifies as the exact
     * thing weighted-random sampling exists to prevent.
     *
     * @return a thread-safe cryptographic generator
     */
    @Bean
    RandomGenerator validatorSamplingRandom() {
        return new SecureRandom();
    }

    /**
     * The clock the service stamps {@code last_sampled_at}, {@code last_vote_at}, {@code resolved_at}
     * and flag timestamps with. Injected rather than {@code Instant.now()} so a test can pin time and
     * assert ordering without sleeping.
     *
     * @return the system UTC clock
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock federationClock() {
        return Clock.systemUTC();
    }

    /**
     * The default validator key directory: resolves nothing.
     *
     * <p>A placeholder so the context wires before the identity slice ({@code
     * docs/architecture/02-identity-and-auth.md}) provides real DID-to-key resolution. With it, every
     * REST-submitted validator signature is unverifiable and no duel resolves over HTTP — the safe
     * failure. The identity slice contributes a {@link ValidatorKeyDirectory} bean and {@code
     * @ConditionalOnMissingBean} steps this one aside. A wiring seam, reported in {@code
     * undecidedByDocs}.
     *
     * @return an empty directory
     */
    @Bean
    @ConditionalOnMissingBean
    ValidatorKeyDirectory emptyValidatorKeyDirectory() {
        return kid -> null;
    }
}
