package io.github.stoicswe.eyeandsickle.server.compute;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wires the compute ledger's beans, and nothing more than it must.
 *
 * <h2>Why each bean is here, and why each yields to an override</h2>
 *
 * Every bean below is a default the rest of the server can replace. {@link ThermalRecoveryStrategy} is
 * the recovery curve — still {@code [PROPOSAL]} ({@code docs/design/01-core-resources.md} §1.4), so a
 * playtested replacement must be able to drop in. {@link AllocationDisclosurePolicy} is the seam the
 * defensive/deployed-mining slice takes over to hide rootkit-wrapped miners. {@link Clock} and {@link
 * TransactionOperations} are infrastructure that other slices may also want to define. Each is guarded
 * by {@link ConditionalOnMissingBean} so this configuration supplies a working default without
 * colliding with a slice that supplies its own.
 *
 * <p>{@link ComputeProperties} is a record, so — like {@code PersistenceProperties} — it is registered
 * with {@link EnableConfigurationProperties} rather than {@code @Component}; constructor-bound property
 * records fail at startup if annotated as components.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ComputeProperties.class)
public class ComputeConfiguration {

    /**
     * The default Thermal Budget curve: {@link LoadFactorThermalRecovery}.
     *
     * @param properties the calibrated recovery constants
     * @return the [PROPOSAL] curve, unless a bean already supplies one
     */
    @Bean
    @ConditionalOnMissingBean(ThermalRecoveryStrategy.class)
    ThermalRecoveryStrategy thermalRecoveryStrategy(ComputeProperties properties) {
        return new LoadFactorThermalRecovery(properties);
    }

    /**
     * The default disclosure policy: {@link DiscloseAllAllocations}. A server with no concealment shows
     * every allocation and reconciles exactly; the deployed-mining slice replaces this to hide
     * rootkit-wrapped parasites.
     *
     * @return the disclose-all policy, unless a bean already supplies one
     */
    @Bean
    @ConditionalOnMissingBean(AllocationDisclosurePolicy.class)
    AllocationDisclosurePolicy allocationDisclosurePolicy() {
        return new DiscloseAllAllocations();
    }

    /**
     * The clock every time-driven decision in this slice reads — recovery deadlines, provisioning
     * timestamps. A single injected {@link Clock} is what makes those decisions testable; UTC because
     * the schema stores {@code timestamptz} and a self-hoster's local zone must not change the game.
     *
     * @return a UTC system clock, unless a bean already supplies one
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock computeClock() {
        return Clock.systemUTC();
    }

    /**
     * Programmatic transaction control for the ledger's critical sections.
     *
     * <p>The service uses explicit {@link TransactionOperations} rather than {@code @Transactional} so
     * its lock-then-decide sections are one visible transaction that also works outside a Spring proxy
     * (an integration test can drive the service with a plain template). Boot auto-configures the
     * {@link PlatformTransactionManager} from the datasource; this wraps it.
     *
     * @param transactionManager Boot's auto-configured transaction manager
     * @return a transaction template, unless a bean already supplies one
     */
    @Bean
    @ConditionalOnMissingBean(TransactionOperations.class)
    TransactionOperations computeTransactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
