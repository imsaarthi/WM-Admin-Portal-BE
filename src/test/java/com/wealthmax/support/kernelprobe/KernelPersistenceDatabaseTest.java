package com.wealthmax.support.kernelprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.wealthmax.support.PostgresDataJpaTest;
import com.wealthmax.support.TablesMustBeEmpty;

/**
 * The first database test (ADR-007). It runs the kernel persistence classes against the local test database:
 * Flyway applies the migrations, ddl-auto=validate accepts an entity built on AuditableEntity, and JPA auditing
 * fills the columns. The table kernel_probe exists only for tests (src/test/resources/db/test-migration).
 */
@PostgresDataJpaTest
@Import(KernelProbeJpaTestConfig.class)
@TablesMustBeEmpty("kernel_probe")
class KernelPersistenceDatabaseTest {

    @Autowired
    private KernelProbeRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway applied the test migration (and ddl-auto=validate accepted the entity, or this context would not start)")
    void flywayAppliedTheTestMigration() {
        Long applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '9000' and success", Long.class);

        assertThat(applied).isEqualTo(1L);
    }

    @Test
    @DisplayName("a saved row gets its id and both audit timestamps from the application clock")
    void savedRowGetsIdAndTimestamps() {
        Instant before = Instant.now();

        KernelProbeEntity saved = repository.saveAndFlush(new KernelProbeEntity("first"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("created_by and updated_by stay null until the identity module supplies a user")
    void auditorColumnsStayNull() {
        KernelProbeEntity saved = repository.saveAndFlush(new KernelProbeEntity("nobody"));

        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("an update keeps created_at and moves updated_at forward")
    void updateKeepsCreatedAtAndMovesUpdatedAt() {
        KernelProbeEntity saved = repository.saveAndFlush(new KernelProbeEntity("before"));
        Instant createdAt = saved.getCreatedAt();
        Instant firstUpdate = saved.getUpdatedAt();
        entityManager.clear();

        KernelProbeEntity loaded = repository.findById(saved.getId()).orElseThrow();
        loaded.rename("after");
        repository.saveAndFlush(loaded);
        entityManager.clear();

        KernelProbeEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("after");
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(firstUpdate);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(reloaded.getCreatedAt());
    }

    @Test
    @DisplayName("timestamps are stored as TIMESTAMPTZ, so no server time zone is baked in")
    void timestampsAreTimestamptz() {
        String type = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_name = 'kernel_probe' and column_name = 'created_at'", String.class);

        assertThat(type).isEqualTo("timestamp with time zone");
    }

    @Test
    @DisplayName("a duplicate on a unique column fails in the database with SQLState 23505 (what GlobalExceptionHandler maps to 409)")
    void duplicateViolatesTheUniqueConstraint() {
        repository.saveAndFlush(new KernelProbeEntity("same"));

        // One failing call only: after a failed flush the persistence session is not reliable any more.
        Throwable thrown = catchThrowable(() -> repository.saveAndFlush(new KernelProbeEntity("same")));

        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class);
        ConstraintViolationException violation = (ConstraintViolationException) thrown.getCause();
        assertThat(violation.getSQLState()).isEqualTo("23505");
        assertThat(violation.getConstraintName()).isEqualToIgnoringCase("uk_kernel_probe_name");
    }
}
