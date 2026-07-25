package io.github.stoicswe.eyeandsickle.server;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The one application clock.
 *
 * <p>Time is an injected dependency across this server — the compute ledger's thermal recovery, the
 * quorum's reputation decay, provenance timestamps, session expiry — so that all of it is
 * deterministic under test rather than reading the wall clock inline. Several slices, built
 * independently, each declared their own {@code Clock} bean; two live beans made a bare
 * {@code Clock} injection ambiguous and stopped the context from starting.
 *
 * <p>This bean is the single source of truth. It is {@link Primary} so any {@code Clock} injection
 * resolves here, and the slice-level clocks are now {@code @ConditionalOnMissingBean}, so in the full
 * application exactly one clock exists. A test can still supply a fixed clock — a {@code @Primary}
 * test bean, or a {@code @MockBean} — and it will win over this one.
 *
 * <p>UTC on purpose: provenance timestamps are signed and compared across servers in different
 * timezones ({@code docs/architecture/04-item-provenance.md}), and a local-zone clock would make the
 * same instant serialize two ways.
 */
@Configuration(proxyBeanMethods = false)
class ServerClockConfiguration {

    @Bean
    @Primary
    Clock clock() {
        return Clock.systemUTC();
    }
}
