package com.wealthmax.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.wealthmax.common.config.ClockConfig;
import com.wealthmax.common.config.JpaAuditingConfig;

/**
 * A JPA test slice that runs on a PostgreSQL database on this machine (ADR-007). No H2, no Docker.
 *
 * - replace = NONE: Spring Boot would otherwise swap in an embedded database.
 * - ddl-auto=validate: Flyway runs first, so every run also proves the migrations apply and every entity matches
 *   its table. Never use create, create-drop or update here.
 * - The test profile: application-test.yml points at the local test database.
 * - LocalDatabaseOnlyInitializer: refuses to start unless the datasource host is this machine, even if an
 *   environment variable points at a shared database.
 * - TablesMustBeEmptyExtension: stops with a message if a table named in @TablesMustBeEmpty holds rows.
 * - JpaAuditingConfig and ClockConfig: slices skip @Configuration classes, so auditing is imported by hand.
 * - Every test runs in a transaction that is rolled back, so nothing is left behind.
 * - @Tag("database"): CI skips these (ADR-013), because CI has no database.
 *
 * Add @Import(<Module>JpaTestConfig.class) so a module's tests load only its own entities and repositories.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, ClockConfig.class})
@ContextConfiguration(initializers = LocalDatabaseOnlyInitializer.class)
@ExtendWith(TablesMustBeEmptyExtension.class)
@Tag("database")
public @interface PostgresDataJpaTest {
}
