package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.server.items.ServerSigningIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the migration slice's default seam implementations.
 *
 * <p>Each is a safe default that a fuller deployment supersedes via {@code @ConditionalOnMissingBean},
 * exactly as the identity and integration slices do:
 *
 * <ul>
 *   <li>{@link CharacterHomeDirectory} — the in-JVM monotonic guard stands in until the signed, gossiped
 *       character directory (Option E, §4) contributes a persistent, federation-wide binding. Both refuse
 *       a stale sequence; only the real one is durable and cross-server.
 *   <li>{@link OperatorAuthorization} — a configured-token gate that denies when no token is set, so
 *       full-state (Option B) transfer is off until an operator turns it on (§5).
 *   <li>{@link LocalHomeServerDid} — this server's own DID, read from its provenance signing identity, so
 *       a migration it originates can name its home (§4).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class MigrationConfiguration {

    /**
     * The default character-home directory: an in-memory monotonic guard (a stand-in for Option E's signed
     * directory). Reported as a wiring seam.
     *
     * @return the in-memory directory
     */
    @Bean
    @ConditionalOnMissingBean
    CharacterHomeDirectory characterHomeDirectory() {
        return new InMemoryCharacterHomeDirectory();
    }

    /**
     * The default operator authorization: a shared-secret token that denies when unset (§5).
     *
     * @param properties supplies the configured operator token
     * @return the token-based authorization
     */
    @Bean
    @ConditionalOnMissingBean
    OperatorAuthorization operatorAuthorization(MigrationProperties properties) {
        return new TokenOperatorAuthorization(properties);
    }

    /**
     * This server's DID, taken from its provenance signing identity. A server that hosts online characters
     * must have a signing identity; if it has none, originating a migration fails loudly rather than naming
     * a null home (§4).
     *
     * @param signingIdentity this server's signing identity
     * @return the local home-server DID seam
     */
    @Bean
    @ConditionalOnMissingBean
    LocalHomeServerDid localHomeServerDid(ServerSigningIdentity signingIdentity) {
        return () -> {
            String did = signingIdentity.issuerDidOrNull();
            if (did == null) {
                throw new IllegalStateException(
                        "This server has no configured signing DID, so it cannot originate a character migration "
                                + "(docs/architecture/09-player-state-portability.md §4). Configure "
                                + "eyeandsickle.items.signing.*.");
            }
            return did;
        };
    }
}
