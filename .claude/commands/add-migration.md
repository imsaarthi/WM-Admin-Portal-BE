---
name: add-migration
argument-hint: "[module] [description of change] e.g. 'careers add jobs table' or 'leads add status column to leads'"
---

Create a new Flyway migration for: $ARGUMENTS

Load the `flyway-migration` skill first. It holds the full rules, including the zero-downtime and
locking section. This command is the short path.

The database is built from scratch by these migrations (`ddl-auto: validate`,
`baseline-on-migrate: false`). There is no baseline and no tenant column.

Step 1: Find the next version
```bash
ls src/main/resources/db/migration | sort -V | tail -3
```
New version = highest + 1 (the first one is `V1`). Never reuse or edit an existing version.

Step 2: Name the owning module
Every table belongs to exactly one module (`identity`, `careers`, `leads`, `content`,
`notification`...). A migration only touches tables its own module owns. If the change needs
another module's table, stop: the design is wrong (see `modular-monolith`).

Step 3: Decide the type
- New table: `CREATE TABLE IF NOT EXISTS`.
- New column: `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`.
- New index: `CREATE INDEX IF NOT EXISTS` (on a big or busy table: `CONCURRENTLY`, alone in its
  own file).
- Constraint on an existing big table: `NOT VALID`, then `VALIDATE` in a later migration.
- Rename or drop a column that deployed code still uses: stop. That needs expand and contract
  across releases (see the skill).

Step 4: Create the file
Path: `src/main/resources/db/migration/V{version}__{module}_{snake_case_description}.sql`

Start it with a header comment:
```sql
-- V{version} ({module}): <what this does>
-- <why it's needed>
```

New table:
```sql
CREATE TABLE IF NOT EXISTS <table_name> (
    id              BIGSERIAL       PRIMARY KEY,
    -- domain columns here
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      BIGINT,         -- only if the entity extends AuditableEntity
    updated_by      BIGINT          -- only if the entity extends AuditableEntity
);

-- indexes for real query patterns only (filters, sorts, lookups)
CREATE INDEX IF NOT EXISTS idx_<table>_<column> ON <table_name>(<column>);
```

A foreign key is allowed only to a table **in the same module**. Another module's record is a plain
`BIGINT` id column with no foreign key.

New column:
```sql
ALTER TABLE <table_name> ADD COLUMN IF NOT EXISTS <column_name> <type>;
-- NOT NULL with a constant DEFAULT is instant on PostgreSQL 11+
CREATE INDEX IF NOT EXISTS idx_<table>_<column> ON <table_name>(<column_name>);  -- only if queried
```

Types: money `NUMERIC(14,2)`, percentages `NUMERIC(5,2)`, enums `VARCHAR(50)` with a `CHECK`
listing the values, JSON `JSONB`, timestamps `TIMESTAMPTZ`. Business numbers that must be unique
and ordered (for example job ids) use a sequence created in the same migration.

Step 5: Validate
- No `DELETE FROM` or `TRUNCATE`. `DROP TABLE IF EXISTS` only for a confirmed dead table, with
  the reason in the header.
- Every constraint and index is named (`uk_`, `fk_`, `chk_`, `idx_`).
- No secrets and no real personal data, seed data included.
- The filename matches `V{version}__{module}_{description}.sql`.
- Prove it on a fresh local database: start the application (or a repository test, which runs
  Flyway with `ddl-auto=validate`) and check that Flyway applied it and validation passed.
  ```bash
  mvn test -Dtest=<Entity>RepositoryTest -q; echo "exit=$?"
  ```
  Local PostgreSQL must be running. If it isn't, report "not run".

Step 6: Report
- The full file content and path.
- The verification result, or "not run" and why.
