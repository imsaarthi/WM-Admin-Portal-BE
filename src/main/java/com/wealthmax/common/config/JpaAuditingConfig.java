package com.wealthmax.common.config;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on created_at / updated_at / created_by / updated_by.
 *
 * Kept in its own class, not on the application class: @EnableJpaAuditing on the application class would
 * force every @WebMvcTest slice to build JPA, because slice tests load the application class. Database
 * slice tests import this class explicitly.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /** Returns nobody until Phase 3 (identity) can read the signed-in user's id. */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return Optional::empty;
    }

    /**
     * Cut to microseconds on purpose: PostgreSQL TIMESTAMPTZ keeps microseconds, but the Java clock can have 100 ns
     * (Windows) or 1 ns (Linux). Without this, an entity just saved holds a different instant than the same row
     * reloaded, and any equality check or cooling-period comparison between them is wrong by a few nanoseconds.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
