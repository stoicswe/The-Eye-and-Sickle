package io.github.stoicswe.eyeandsickle.server.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the identity slice's default seam implementations.
 *
 * <p>{@link CharacterProperties} and {@link IdentityProperties} are {@code @ConfigurationProperties}
 * records, registered by the application's {@code @ConfigurationPropertiesScan}, so they need no wiring
 * here. What does is the recognized-character-count seam
 * ({@code docs/architecture/09-player-state-portability.md} §2): the identity slice must ship a working
 * default without assuming the discovery slice's federation directory exists yet.
 */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    /**
     * The default {@link RecognizedCharacterCount}: counts only this server's own active characters
     * (09 §2). Correct and exact for a single, non-federating home server, and the honest floor for a
     * federating one until the discovery slice contributes a directory-backed count. When it does,
     * {@code @ConditionalOnMissingBean} steps this default aside — {@link CharacterService} sees only the
     * one bean either way. A wiring seam, reported in {@code undecidedByDocs}.
     *
     * @param players the character table the local count reads from
     * @return the single-server default count
     */
    @Bean
    @ConditionalOnMissingBean
    RecognizedCharacterCount localRecognizedCharacterCount(PlayerRepository players) {
        return new LocalRecognizedCharacterCount(players);
    }
}
