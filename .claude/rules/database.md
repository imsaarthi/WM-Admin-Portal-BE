---
paths:
  - "src/main/resources/db/migration/**"
  - "**/db/migration/**"
---

# Database Migration Rules

You are editing Flyway migrations. The schema is built **from scratch** by these files on a new,
empty database (`ddl-auto: validate`, `baseline-on-migrate: false`). Once applied anywhere they're
permanent: editing an applied file breaks Flyway's checksum check on every database that already
ran it. Full detail and templates: the `flyway-migration` skill.

## Naming
- `V{version}__{module}_{description}.sql`, snake_case, for example
  `V1__careers_create_jobs.sql`.
- Version = highest existing + 1: `ls src/main/resources/db/migration | sort -V | tail -3`.
  The first migration is `V1`.
- Never reuse or skip a version. Never edit an applied migration; write a new one.
- Start the file with `-- V{N} ({module}): <what>` and a line on why.

## Ownership
- Every table belongs to exactly one module. A migration only touches tables its own module owns.
- A foreign key only to a table in the same module. Another module's record is a plain `BIGINT` id
  column, with no foreign key.
- No tenant column: the application is single tenant.

## New tables
```sql
CREATE TABLE IF NOT EXISTS <table> (
    id          BIGSERIAL   PRIMARY KEY,
    -- domain columns
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  BIGINT,                         -- only if the entity extends AuditableEntity
    updated_by  BIGINT                          -- only if the entity extends AuditableEntity
);
```
- `created_by` / `updated_by` hold user ids: `BIGINT`, never `VARCHAR`.
- Soft delete (`is_deleted`, `deleted_at`) only when the feature needs it.
- Entity columns must match the migration exactly, or `ddl-auto=validate` stops startup.

## Types
- IDs: `BIGSERIAL` (PK), `BIGINT` (FK and cross-module ids). Never UUID keys.
- Money: `NUMERIC(14,2)`. Percentages: `NUMERIC(5,2)`. Never `FLOAT`, `DOUBLE PRECISION` or
  `MONEY`.
- Timestamps: `TIMESTAMPTZ`.
- Enums: `VARCHAR(50)` with a `CHECK` listing the values, never PostgreSQL `ENUM` types.
  JSON: `JSONB`.
- Flags: `BOOLEAN NOT NULL DEFAULT ...`.
- Ordered business numbers (for example job ids): a sequence created in the same migration.

## Names and indexes
- `uk_{table}_{cols}`, `fk_{table}_{ref}`, `chk_{table}_{rule}`, `idx_{table}_{cols}`.
- Index the real query patterns: filter columns (status), sort columns (`created_at`), lookups.

## Idempotency and safety
- `IF NOT EXISTS` / `IF EXISTS` on every create, add and drop.
- No `DELETE FROM` or `TRUNCATE`. `DROP TABLE IF EXISTS` only for a confirmed dead table, with the
  reason in the header. `UPDATE` only for targeted fixes and small backfills.
- No secrets and no real personal data, seed data included.
- Big or busy tables: `SET LOCAL lock_timeout`, `CREATE INDEX CONCURRENTLY` alone in its own file,
  `NOT VALID` then `VALIDATE` for new constraints, expand and contract for renames and drops.
- Prove it on a fresh local database: a repository test (`@PostgresDataJpaTest`, Flyway plus
  `validate`) or an application start, with local PostgreSQL running.
- Production data from the old repository arrives through each module's data-migration script at
  the cutover (ADR-009), never through a Flyway migration.
