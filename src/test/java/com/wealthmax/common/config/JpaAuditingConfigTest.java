package com.wealthmax.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No database: only the two beans the auditing needs. That a row really gets its timestamps is proven
 * on PostgreSQL in KernelPersistenceDatabaseTest.
 */
class JpaAuditingConfigTest {

    private final JpaAuditingConfig config = new JpaAuditingConfig();

    @Test
    @DisplayName("there is no current auditor until the identity module supplies one, so created_by stays null")
    void auditorIsEmptyUntilIdentityExists() {
        assertThat(config.auditorAware().getCurrentAuditor()).isEmpty();
    }

    @Test
    @DisplayName("audit timestamps come from the application Clock, not from the server's time zone")
    void auditTimestampsUseTheClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

        assertThat(config.auditingDateTimeProvider(fixed).getNow()).contains(Instant.parse("2026-11-05T12:00:00Z"));
    }

    @Test
    @DisplayName("audit timestamps are cut to microseconds, the precision PostgreSQL stores, so a saved entity equals the reloaded row")
    void auditTimestampsAreCutToMicroseconds() {
        Clock fine = Clock.fixed(Instant.parse("2026-11-05T12:00:00.123456789Z"), ZoneOffset.UTC);

        assertThat(config.auditingDateTimeProvider(fine).getNow())
                .contains(Instant.parse("2026-11-05T12:00:00.123456Z"));
    }
}
