# Patch 1l: Plain `@Configuration` on the common config classes

**Author:** Sarthak · **Date:** 2026-11-06 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1l1

## Problem
`ClockConfig`, `JpaAuditingConfig` and `PropertiesScanConfig` were marked `@Configuration(proxyBeanMethods = false)`. The setting skips a CGLIB proxy that only matters when one `@Bean` method calls another. None of the three does that, so the setting did nothing useful and was one more thing to explain.

## Solution
- The three classes now use plain `@Configuration`. No other line changed.
- `JpaAuditingConfig.auditingDateTimeProvider(Clock clock)` already receives the `Clock` as a parameter, so it works the same with or without a proxy.

## Verification (real run, 2026-11-06)
- `mvn -B test`: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS. The database tests ran, including `WmAdminPortalApplicationTests`, which starts the whole app.
- A search of the repository (without `target/`) after the edit finds no mention of `proxyBeanMethods`.
- Not run: `mvn clean verify`.

## Rulings
- The setting was removed from all three classes, not only `ClockConfig`. Why: the question covered the project's habit, and one class with a different annotation would be confusing. Cost if wrong: put it back on two lines.

## Limitations and follow-up
- If a future `@Bean` method calls another `@Bean` method directly, it gets a second object, because there is no proxy to hand back the first one. Pass the bean in as a method parameter instead. This is the rule in the plan, not an enforced check.
- `JpaAuditingConfig.java` also carries the uncommitted changes from patch 1k, so its diff shows both patches.
