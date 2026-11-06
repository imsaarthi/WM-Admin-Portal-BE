package com.wealthmax.common.config;

import java.time.Clock;
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
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /** Returns nobody until Phase 3 (identity) can read the signed-in user's id. */
    @Bean
    public AuditorAware<Long> auditorAware() {
        return Optional::empty;
    }

    /** Reads the application Clock, so tests can fix the time and nothing depends on the server's time zone. */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
