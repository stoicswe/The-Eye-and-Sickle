package io.github.stoicswe.eyeandsickle.server.economy;

import io.github.stoicswe.eyeandsickle.server.economy.gate.SchematicHoldings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the economy slice's cross-slice ports.
 *
 * <p>This is the {@code EconomyConfiguration} that {@link SchematicHoldings}'s Javadoc already refers
 * to — it was part of the slice's design but had not been written when the slice was interrupted.
 */
@Configuration(proxyBeanMethods = false)
class EconomyConfiguration {

    /**
     * Schematic-ownership state, which the progression slice owns and has not yet implemented. Until
     * it does, the safe default denies every schematic and reports every vault at base capacity, so
     * the schematic gate ({@code docs/design/02-unlock-gates.md} §2.2) fails closed — no player is
     * granted a ceiling they have not earned (Invariant I12). {@code @ConditionalOnMissingBean} lets a
     * real implementation supersede this without touching this file.
     *
     * @return a port that grants nothing
     */
    @Bean
    @ConditionalOnMissingBean
    SchematicHoldings schematicHoldings() {
        return new SchematicHoldings.Denying();
    }
}
