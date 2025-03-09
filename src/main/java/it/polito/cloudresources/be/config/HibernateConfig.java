package it.polito.cloudresources.be.config;

import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for Hibernate dialect with proper ZonedDateTime handling
 */
@Configuration
@Profile("pro")
public class HibernateConfig {

    /**
     * PostgreSQL dialect that supports ZonedDateTime
     */
    @Bean
    public PostgreSQLDialect postgresDialect() {
        return new PostgreSQLDialect();
    }
}

/**
 * Configuration for Hibernate dialect with H2 database
 */
@Configuration
@Profile("dev")
class H2HibernateConfig {

    /**
     * H2 dialect that supports ZonedDateTime
     */
    @Bean
    public org.hibernate.dialect.H2Dialect h2Dialect() {
        return new org.hibernate.dialect.H2Dialect();
    }
}