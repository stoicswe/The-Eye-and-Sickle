package io.github.stoicswe.eyeandsickle.server.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the persistence layer's bound configuration.
 *
 * <h2>Why this class has to exist</h2>
 *
 * {@link PersistenceProperties} is a record, so Spring binds it by constructor. Constructor-bound
 * {@code @ConfigurationProperties} types must be registered through
 * {@code @EnableConfigurationProperties} or {@code @ConfigurationPropertiesScan} — annotating the
 * record {@code @Component} looks equivalent and fails at startup with a message about constructor
 * binding. Recording that here so the next person does not rediscover it.
 *
 * <h2>What is deliberately not here</h2>
 *
 * No {@code DataSource}, no {@code JdbcClient}, no transaction manager bean. Spring Boot
 * auto-configures all three from {@code application.yml}, and hand-rolling one would quietly opt this
 * server out of Boot's Hikari pooling and its transaction-manager wiring. Inject {@code JdbcClient}
 * and let Boot own the plumbing.
 *
 * <p>No {@code Flyway} bean either. Flyway owns the schema outright and nothing at runtime alters it;
 * an implicit schema change on a self-hosted server is an unreviewed migration running on somebody
 * else's data.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersistenceProperties.class)
class PersistenceConfiguration {}
