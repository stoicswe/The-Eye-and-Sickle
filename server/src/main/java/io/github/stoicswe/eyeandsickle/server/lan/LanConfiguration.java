package io.github.stoicswe.eyeandsickle.server.lan;

import io.github.stoicswe.eyeandsickle.server.identity.AtProtoIdentityProvider;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires LAN mode, and — more importantly — is the reason federation is <em>absent</em> rather than
 * <em>skipped</em> in it.
 *
 * <h2>⚠ Absent, not flag-checked</h2>
 *
 * {@code docs/architecture/12-lan-mode.md} §3: a flag consulted in fifteen places is a flag somebody
 * forgets in the sixteenth, and the sixteenth here is a quarantined item reaching the federation. So
 * the federation components are <strong>not created</strong> in a LAN-mode context: a call that should
 * not happen fails to wire, loudly, at startup, rather than failing to check, silently, in production.
 *
 * <p>The switches are on the configurations that own each subsystem
 * ({@code @ConditionalOnProperty(name = "eyeandsickle.mode", havingValue = "FEDERATED",
 * matchIfMissing = true)}), so the default — an unset or misspelled property — is the mode with the
 * security machinery on.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eyeandsickle.mode", havingValue = "LAN")
public class LanConfiguration {

    /**
     * The identity provider for LAN mode.
     *
     * <p>⚠ Not {@code @ConditionalOnMissingBean}. In LAN mode this must be the <em>only</em> identity
     * provider — an AT Protocol one wired alongside it would mean two ways in, one of which resolves
     * DIDs over a network this mode is not supposed to touch.
     */
    @Bean
    AtProtoIdentityProvider lanIdentityProvider() {
        LoggerFactory.getLogger(LanConfiguration.class)
                .warn("LAN MODE: identity is a server-assigned UUID with no proof behind it, federation is off in "
                        + "every direction, and nothing created here can ever move to a federated server. "
                        + "See docs/architecture/12-lan-mode.md");
        return new LanIdentityProvider();
    }

    @Bean
    LanAddressInterlock lanAddressInterlock(LanProperties properties) {
        return new LanAddressInterlock(properties);
    }

    @Bean
    LanJoinController lanJoinController() {
        return new LanJoinController();
    }
}
