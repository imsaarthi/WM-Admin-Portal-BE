---
name: plan-review
argument-hint: "[path to plan file] e.g. plans/Sarthak-plan/2026-11-10-careers-module.md"
---

Review the implementation plan at: $ARGUMENTS

You are a plan reviewer. Check that this plan is complete, matches the real codebase, and is safe
to implement. The plan format is defined by the `writing-plans` skill. Read it if you haven't.

## What to Check

### 1. Format and location
- Saved as `plans/<DevName>-plan/YYYY-MM-DD-<name>.md`, with the date in the filename.
- Header has Author, Date, Time, Goal, Module, Migration required.
- Phases of at most 5 files each (CLAUDE.md).

### 2. Completeness
- No TODO, TBD, "implement later" or "similar to Task N".
- Every code step shows the actual code.
- Every command step has an exact command and an `Expected:` line.
- Exact file paths, no placeholders.
- Maven commands have no `-pl` flag (single-module build). Narrow runs use `-Dtest=`.

### 3. Module placement and boundaries
- The module is named, and every new table has exactly one owning module.
- No imports of another module's `entity`, `repository`, `mapper` or `service.impl`; cross-module
  calls go through service interfaces, DTOs, enums or events.
- No JPA relationship, join or foreign key across modules.
- Nothing imported from the old repository (`com.wealthmax.adminPortal`). A ported feature lists
  the business rules it keeps.
- No tenant column, context or filter anywhere.

### 4. Access control
- Every new route has a level (Public, Authenticated, Admin) and a position in the security
  configuration relative to existing wildcards.
- Every admin use case has `@PreAuthorize` on its service method.
- Any new public route is justified and returns no personal data.
- `@Valid` on every `@RequestBody`; request DTOs carry no server-owned fields.

### 5. Test-first order
- Each task writes the failing test first, runs it expecting FAIL, implements, runs it expecting
  PASS.
- Queries, constraints and sequences are proven by a repository test on local PostgreSQL
  (`@PostgresDataJpaTest`), not only by Mockito tests.
- New routes have a security-rules test.

### 6. Task independence
- Each task compiles without the next one.
- Method names match across tasks: if Task 5 calls `jobRepository.findByJobId(...)`, Task 3
  defines exactly that signature.

### 7. Data and migrations
- Flyway version is the next free number (check the folder).
- Filename is `V{N}__{module}_{description}.sql`.
- Templates follow `flyway-migration`: `BIGSERIAL`, `TIMESTAMPTZ`, `BIGINT` audit columns only for
  `AuditableEntity`, `IF NOT EXISTS`, named constraints.
- Entity columns match the migration exactly (`ddl-auto=validate`).
- No `DELETE FROM` or `TRUNCATE`. `DROP TABLE` only with a stated reason. No edits to applied
  migrations.
- A ported feature includes its data-migration script from the old database (ADR-009).
- Money is `BigDecimal` in Java and `NUMERIC(14,2)` in SQL. Never `double` or `float`.

## Output Format

## Plan Review

**Status:** APPROVED / NEEDS FIXES

**Blocking issues (fix before implementing):**
- [Task N, Step X]: [issue]. [Why it blocks.]

**Warnings (fix before committing):**
- [Task N]: [issue]

**Good:**
- [what the plan does well]

## Rule
Block only for things that would cause real problems: wrong behaviour, open routes, boundary
breaks, compile errors, security gaps. Don't block for style or wording.
