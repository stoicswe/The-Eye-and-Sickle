package io.github.stoicswe.eyeandsickle.server.directory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the character directory slice's default seam implementations.
 *
 * <p>{@link CharacterDirectoryProperties} is a {@code @ConfigurationProperties} record registered by the
 * application's {@code @ConfigurationPropertiesScan}, so it needs no wiring here. What does is the
 * home-server key resolver seam ({@link CharacterHomeKeyResolver}): the verifier must be constructible on
 * any server — federating or not — even before the identity slice's real DID resolver is wired.
 */
@Configuration(proxyBeanMethods = false)
class CharacterDirectoryConfiguration {

    /**
     * The default {@link CharacterHomeKeyResolver}: resolves nothing, so every published home binding is
     * unverifiable and therefore refused ({@link CharacterHomeFault#UNKNOWN_SIGNING_KEY}). That is the
     * safe closed default — refusing to recognize a binding whose home-server key cannot be resolved is
     * safe, whereas guessing a key is not. When the identity slice contributes a live DID resolver,
     * {@code @ConditionalOnMissingBean} steps this default aside. A wiring seam, reported in {@code
     * undecidedByDocs}.
     *
     * @return a resolver that resolves nothing
     */
    @Bean
    @ConditionalOnMissingBean
    CharacterHomeKeyResolver characterHomeKeyResolver() {
        return CharacterHomeKeyResolver.empty();
    }
}
