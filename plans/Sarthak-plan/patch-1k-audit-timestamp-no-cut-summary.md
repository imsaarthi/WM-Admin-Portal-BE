# Patch 1k: Audit timestamps are no longer cut to microseconds

**Author:** Sarthak · **Date:** 2026-11-06 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1k1

## Problem
Patch 1h1 to 1h3 cut the audit timestamp to microseconds in `JpaAuditingConfig`, so a just-saved entity equalled the same row reloaded from PostgreSQL. The cooling-off rule will compare dates only, so that precision is not needed, and the cut was extra code to explain and test.

## Solution
- `JpaAuditingConfig`: `auditingDateTimeProvider` returns `clock.instant()` as it is. The `ChronoUnit` import and the long comment are gone. `auditorAware()` is kept (still empty) because Phase 3 replaces it with the signed-in user's id.
- `JpaAuditingConfigTest`: the microsecond test is deleted. The `Clock` test and the empty-auditor test stay.
- `KernelPersistenceDatabaseTest`: `reloaded.getCreatedAt()` is compared with `isCloseTo(createdAt, within(1, ChronoUnit.MILLIS))`, with a one-line comment saying why. The saved object holds the Java clock's nanoseconds and PostgreSQL stores microseconds.

## Verification (real runs, 2026-11-06)
- `mvn -B test -Dtest="JpaAuditingConfigTest,ModuleBoundaryArchitectureTest,KernelPersistenceDatabaseTest"`: Tests run: 12, Failures: 0, Errors: 0, BUILD SUCCESS (6 of them against the local PostgreSQL).
- `mvn -B test`: Tests run: 103, Failures: 0, Errors: 0, BUILD SUCCESS. This is 104 minus the deleted microsecond test. The database tests ran.
- Not run: `mvn clean verify`.
- I did not run the database test with the exact `isEqualTo` and no cut, so "it would fail" is a reading of the code and of patch 1h1 to 1h3, not a measured result.

## Rulings
- The tolerance is 1 ms. Why: the real difference is under 1 microsecond, so 1 ms is loose enough to never flake and tight enough to catch a wrong value. Cost if wrong: one number.

## Limitations and follow-up
- Any future test that compares an instant held in memory with the same instant reloaded from PostgreSQL needs a tolerance, or must truncate the value itself.
- `.claude/skills/dependency-upgrade/SKILL.md` line 134 still says to cut timestamps to microseconds (see `JpaAuditingConfig`). It is now out of date. Not changed in this patch.
- Patch summaries 1h1 to 1h3 and 1i still describe the cut. They are history and were left as written.
