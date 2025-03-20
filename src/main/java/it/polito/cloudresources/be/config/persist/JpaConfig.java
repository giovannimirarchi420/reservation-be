package it.polito.cloudresources.be.config.persist;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Configurazione unificata per JPA con supporto auditing e ZonedDateTime
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {
    
    /**
     * Provider DateTimeProvider personalizzato che fornisce istanze ZonedDateTime per l'auditing
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID));
    }

    /**
     * Oracle Dialect configuration with ZonedDateTime support
     */
    @Configuration
    @Profile("pro")
    public static class OracleSQLConfig {
        @Bean
        public org.hibernate.dialect.OracleDialect oracleDialect() {
            return new org.hibernate.dialect.OracleDialect();
        }
    }

    /**
     * PostgreSQL Dialect configuration with ZonedDateTime support
     */
    @Configuration
    @Profile("test")
    public static class PostgreSQLConfig {
        @Bean
        public org.hibernate.dialect.PostgreSQLDialect postgresDialect() {
            return new org.hibernate.dialect.PostgreSQLDialect();
        }
    }
    /**
     * H2 Dialect configuration with ZonedDateTime support
     */
    @Configuration
    @Profile("dev")
    public static class H2Config {
        @Bean
        public org.hibernate.dialect.H2Dialect h2Dialect() {
            return new org.hibernate.dialect.H2Dialect();
        }
    }
}