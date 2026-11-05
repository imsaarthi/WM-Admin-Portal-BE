# Patch 1h1 to 1h3: Test infrastructure (local PostgreSQL with guards)

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1h1, 1h2, 1h3

## Problem
ADR-007 chose local PostgreSQL with guards, but no guard, no database-test annotation and no database test existed, and the kernel persistence classes (1d) had never run against a database.

## Solution
- 1h1: `LocalDatabaseOnlyInitializer` (ported from the old repo, wording made module-neutral) and its 22-case test. `src/test/resources/application-test.yml` rewritten: datasource `jdbc:postgresql://localhost:5432/wm_admin_portal_test`, user `${WM_TEST_DB_USER:postgres}`, password `${WM_TEST_DB_PASSWORD:}`, Flyway locations `db/migration` plus `db/test-migration`, fake `jwt.*` and `app.*`. The Azure and Mailgun blocks are gone until Phase 2, which clears the hook's C2 block on that file.
- 1h2: `@PostgresDataJpaTest` (a composed annotation: `@DataJpaTest` with `validate`, no embedded database, `test` profile, the local-only initializer, the empty-tables extension, auditing imports, `@Tag("database")`), `@TablesMustBeEmpty` and `TablesMustBeEmptyExtension` (counts only, never deletes, refuses non-plain table names), a 5-case test for the extension's logic, and the dead `org.testcontainers` logger removed from `logback-test.xml`.
- 1h3: the kernel probe: `KernelProbeEntity extends AuditableEntity`, its repository, `KernelProbeJpaTestConfig`, the test-only migration `db/test-migration/V9000__kernel_probe.sql`, and `KernelPersistenceDatabaseTest` (6 tests).
- Prerequisite, done by me: `CREATE DATABASE wm_admin_portal_test` on the local PostgreSQL 18 (port 5432, `postgres` user, no password needed from localhost). It is new and empty. None of the 17 other databases on that server was touched.

## Verification (real runs, 2026-11-05)
- **First real PostgreSQL run:** Flyway applied V9000, `ddl-auto=validate` accepted the entity, the `test` profile took effect. This settles the "unverified until 1h3" note from patch 1d.
- `mvn -B clean test`: **Tests run: 101, Failures: 0, Errors: 0, BUILD SUCCESS** (6 of them against the real database).
- **Guard 1, remote host:** run with `-Dspring.datasource.url=jdbc:postgresql://db.example.invalid:5432/anything`: refused with `...points to db.example.invalid`, 0 connection-type errors in the log.
- **Guard 2, non-empty table:** I inserted one row into my own test table. The run stopped with `kernel_probe has 1 row(s). The tests never delete data...`, and the row was still there afterwards. I then removed that row myself; the table is empty again.
- One earlier check of mine was **invalid**: a shell variable broke on the space in `Program Files`, so the run "passed" with exit 0. I discarded it and redid the check step by step.

## A real bug found by the database test
The auditing timestamp had 100 ns precision (`…229756200Z`) but PostgreSQL `TIMESTAMPTZ` stores microseconds (`…229756Z`), so a just-saved entity differed from the same row reloaded. Fixed in `JpaAuditingConfig` (`truncatedTo(ChronoUnit.MICROS)`), with a new unit test written first (red, then green). This would have broken any equality or cooling-period comparison.

## Rulings
- The test database is `wm_admin_portal_test` on the local server. This was an AI proposal in the plan, and I created it because you asked for the plan to be implemented.
- `${WM_TEST_DB_PASSWORD:}` has an empty default. The pre-commit hook accepts it. A developer whose server needs a password sets the variable.

## Limitations and follow-up
- `@PostgresDataJpaTest` reads `spring.profiles.active=test`; the base file's `${SPRING_PROFILES_ACTIVE:local}` is overridden (log: `The following 1 profile is active: "test"`).
- `GlobalExceptionHandlerTest` and the other `@WebMvcTest` classes still run under the default `local` profile. They pass, but they read `application-local.yml` if it exists. Move them to the `test` profile later.
- CI cannot run any `@Tag("database")` test (ADR-013).
