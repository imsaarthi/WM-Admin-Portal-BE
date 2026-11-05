# Patch 1d1 to 1d3: Kernel persistence and config

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1d1, 1d2, 1d3

## Problem
The `common` kernel was empty: no typed configuration, no entity base classes, no clock, no auditing.

## Solution
- 1d1: `common/config/AppProperties` and `JwtProperties` (records bound to the real `app.*` and `jwt.*` keys, `@Validated`), and `@ConfigurationPropertiesScan` on `WmAdminPortalApplication`.
  - `jwt.secret` must be at least 32 characters, so a weak HS256 key stops startup. This is an addition beyond the plan.
  - Token expiries bind as `Duration`; a plain number is milliseconds, which matches the existing yml.
- 1d2: `common/entity/BaseEntity` (id IDENTITY, `created_at`, `updated_at` as `Instant`) and `AuditableEntity` (`created_by`, `updated_by` as `Long`). `common/config/ClockConfig`, a UTC system `Clock` bean.
- 1d3: `common/config/JpaAuditingConfig`: an `AuditorAware<Long>` that returns empty until Phase 3, and a `DateTimeProvider` that reads the `Clock`.

## Verification (real runs, 2026-11-05)
- RED first each time, for the right reason: test compile failed with `symbol: class AppProperties / JwtProperties`, `ClockConfig`, `JpaAuditingConfig`.
- GREEN: `mvn -B test` gives `Tests run: 8, Failures: 0, Errors: 0` (`AppPropertiesBindingTest` 5, `ClockConfigTest` 1, `JpaAuditingConfigTest` 2), `BUILD SUCCESS`.

## Rulings
None new.

## Limitations and follow-up
- `BaseEntity`, `AuditableEntity` and the auditing wiring were written without a failing test first. A mapped superclass cannot be tested without a table. They are proven on PostgreSQL in step 1h3 (`KernelPersistenceDatabaseTest`). **Until then they are unverified at runtime.**
- `AppProperties` and `JwtProperties` require every key to exist. The real `application.yml` has `${...}` placeholders, so a context start in 1h4 shows whether they resolve in the test profile.
- The secret values stay DevOps's. Nothing here reads, copies or quotes one.
