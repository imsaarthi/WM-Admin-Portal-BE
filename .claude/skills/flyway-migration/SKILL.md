---
name: flyway-migration
description: Write correct, safe Flyway migrations for WM-Admin-Portal-BE (PostgreSQL, single-tenant, module-owned tables). Use when creating a table, adding or changing columns, indexes or constraints, backfilling data, or any other schema change.
user-invocable: true
---

# Flyway Migration: WM-Admin-Portal-BE Standards

This is a **single-tenant** application: one organisation, one schema. Tables carry no `tenant_id`,
and no migration adds one. Each table belongs to exactly one **module** (see `modular-monolith`).

## Flyway Is the Standard

**Flyway owns the schema.** Every table, column, index, constraint and sequence change is a
versioned migration under `src/main/resources/db/migration/`, applied automatically when the
application starts. Hibernate only **validates** the schema (`spring.jpa.hibernate.ddl-auto=validate`).
It never creates or alters tables. Stack: Spring Boot 4.1.2 with Flyway 12 and PostgreSQL. In Boot 4
Flyway runs only with `spring-boot-starter-flyway` plus `flyway-database-postgresql`.

### Set-up in this repository (done 2026-11-05)

The schema is built **from scratch** on a new, empty database (ADR-004, ADR-009). There is no
baseline from the old repository's database: production data arrives through per-module
data-migration scripts at the cutover.

- **Dependencies:** `flyway-core` and `flyway-database-postgresql`, versions managed by Spring Boot
  (no `<version>`). Without `flyway-database-postgresql`, startup fails with "Unsupported
  Database: PostgreSQL".
- **Properties** (non-secret, so ours to set) in `application.yml`:

  ```yaml
  spring:
    jpa:
      hibernate:
        ddl-auto: validate
    flyway:
      enabled: true
      baseline-on-migrate: false
      locations: classpath:db/migration
  ```

  Every profile (`local`, `staging`, `test`) uses `validate`. Never `update`, `create` or
  `create-drop`: they hide a missing migration. Don't touch the datasource credentials in those
  files: their values are DevOps's.
- **Folder:** `src/main/resources/db/migration/`, empty until the first module lands. Flyway scans
  it at startup.
- **No baseline.** `baseline-on-migrate` stays `false`. A database that already holds tables is the
  wrong database, and Flyway should refuse it rather than adopt it.
- **Ported objects.** The old repository's `schema.sql` objects (for example the
  `careers_in_job_number_seq` sequence) become part of the owning module's first migration.
  Nothing is created outside Flyway.

---

## Naming

```
V{version}__{description}.sql

Examples (illustrative):
  V1__baseline.sql
  V2__create_newsletter_tables.sql
  V3__add_reminder_sent_at_to_applicant_in.sql
```

- Version: the next integer after the highest existing file. Check, don't guess:
  `ls src/main/resources/db/migration | sort -V | tail -3`
- Description: snake_case, says what the migration does.
- Double underscore between version and description (Flyway requires it).
- Never edit a migration that has been applied anywhere. Write a new one.

**Header comment.** Start the file with what it does and why:

```sql
-- V{N}: Add reminder_sent_at to applicant_in
-- Lets the reminder job skip applications it already emailed.
```

---

## Module Table Template

For a table owned by one module:

```sql
-- V{N}: Create {table_name}
-- {Why this table exists, and which module owns it.}

CREATE TABLE IF NOT EXISTS {table_name} (
    id              BIGSERIAL       PRIMARY KEY,

    -- domain columns here

    created_date    TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Indexes for the real query patterns only, for example:
-- CREATE INDEX IF NOT EXISTS idx_{table}_status ON {table_name}(status);
```

Why these columns:
- `id BIGSERIAL`: entities map `Long id` with `GenerationType.IDENTITY`. Never UUID keys.
- Timestamps: entities map them as `Instant`, so `TIMESTAMPTZ`. Match the entity's column name
  (the IN module uses `created_date`). There is no shared base entity, so there are no
  mandatory audit columns. Add `created_by` / `updated_by` only if the feature stores who did
  it.
- Match the entity. If the table and the entity disagree, the entity or the migration is wrong.

**Soft delete is optional.** Add `is_deleted BOOLEAN NOT NULL DEFAULT FALSE` (and `deleted_at
TIMESTAMPTZ`) only when the feature needs to hide rows but keep them. If you add it, make unique
indexes partial:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_{table}_name
    ON {table_name}(name)
    WHERE is_deleted = FALSE;
```

**Element collections** (`@ElementCollection`) get their own table, owned by the same module as the
parent, with a foreign key back to the parent and an index on that column.

---

## Illustrative Example

Derived from the `CareersINEntity` shape (not a file in the repo). Names of columns follow Hibernate's
default naming of the entity fields:

```sql
CREATE TABLE IF NOT EXISTS careers_in (
    id              BIGSERIAL       PRIMARY KEY,
    job_id          VARCHAR(255),
    status          VARCHAR(50),
    date_posted     DATE,
    created_date    TIMESTAMPTZ,
    job_title       VARCHAR(255),
    job_description TEXT,

    CONSTRAINT uk_careers_in_job_id UNIQUE (job_id)
);

CREATE SEQUENCE IF NOT EXISTS careers_in_job_number_seq;
```

The real `careers_in` table has many more columns. Read the entity and the live schema, not this
snippet.

---

## Adding a Column

```sql
-- Nullable column: instant, no table rewrite
ALTER TABLE {table_name}
    ADD COLUMN IF NOT EXISTS {column_name} {type};

-- NOT NULL with a constant default: also instant on PostgreSQL 11+
ALTER TABLE {table_name}
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

-- Index it only if a real query filters on it
CREATE INDEX IF NOT EXISTS idx_{table}_status ON {table_name}(status);
```

A **volatile** default (`gen_random_uuid()`, `clock_timestamp()`) rewrites the whole table under
an exclusive lock. Add the column nullable, backfill, then add the constraint (see below).

---

## Column Type Reference

| What to store | Use | Never |
|---|---|---|
| Primary key | `BIGSERIAL PRIMARY KEY` | UUID, INT |
| Reference to another row | `BIGINT` | UUID, VARCHAR |
| Money | `NUMERIC(14,2)` (`BigDecimal` in Java) | FLOAT, DOUBLE PRECISION, REAL, MONEY |
| Percentage | `NUMERIC(5,2)` | FLOAT |
| Short text (names, codes) | `VARCHAR(n)`, sized to the DTO's `@Size` | |
| Long text | `TEXT` | |
| Status / enum | `VARCHAR(50)`, mapped with `@Enumerated(EnumType.STRING)` | PostgreSQL `ENUM` types |
| Boolean flag | `BOOLEAN NOT NULL DEFAULT FALSE` | nullable booleans |
| Timestamp | `TIMESTAMPTZ` | plain `TIMESTAMP` in new tables |
| Date only | `DATE` | |
| JSON | `JSONB` | `JSON` |

Hibernate's old `ddl-auto=update` created the existing columns, often with other types (for
example `VARCHAR(255)` everywhere, or `timestamp(6) with time zone`), and the baseline will carry
them. When adding to an existing table, match what is there. New tables follow the table above.

Enums stay `VARCHAR` because adding a value to a PostgreSQL `ENUM` needs a migration, and there
are no `ENUM` types in this schema.

---

## Constraint and Index Names

Name every constraint and index. Use these prefixes:

| Kind | Pattern | Example |
|---|---|---|
| Unique | `uk_{table}_{cols}` | `uk_careers_in_job_id` |
| Foreign key | `fk_{table}_{ref}` | `fk_careers_in_skill_options_careers_in` |
| Check | `chk_{table}_{rule}` | |
| Index | `idx_{table}_{cols}` | `idx_applicant_in_email_id` |

**Foreign keys stay inside a module.** A table may reference its own module's tables (a child to
its parent). Prefer **no** foreign key to another module's table: it welds the two modules
together at the database level, and the owning module should be reached through its service
interface. If integrity really needs one, record the exception in the plan. Use
`ON DELETE CASCADE` only for pure child tables (element collections). Elsewhere, let the
application decide what happens to children.

---

## Idempotency

- `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`.
- `DROP ... IF EXISTS` for every drop.
- PostgreSQL has no `ADD CONSTRAINT IF NOT EXISTS`. Drop first, then add:

```sql
ALTER TABLE {table_name} DROP CONSTRAINT IF EXISTS uk_{table}_{cols};
ALTER TABLE {table_name} ADD CONSTRAINT uk_{table}_{cols} UNIQUE ({col});
```

---

## Zero-Downtime and Locking

Flyway runs migrations when a new app instance starts, while the old instance may still be serving
traffic. Two things follow:

1. Most `ALTER TABLE` forms take an `ACCESS EXCLUSIVE` lock. If a slow query holds the table, the
   `ALTER` waits, and **every later query on that table queues behind it**. That's an outage.
2. The old code runs against the new schema for a while. A rename or drop breaks it.

None of our tables is large yet, so most migrations are fine as written. Use these rules for
anything on a big or busy table, and in every migration once the product has real traffic.

**Cap the lock wait.** Flyway wraps each PostgreSQL migration in a transaction, so `SET LOCAL`
scopes the setting to this migration:

```sql
SET LOCAL lock_timeout = '5s';
ALTER TABLE applicant_in ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;
```

If the lock doesn't come within 5 seconds, the migration fails and startup stops cleanly. Retry
later. That's better than freezing the table.

**Indexes on big tables: `CONCURRENTLY`, in a migration of its own.**

```sql
-- V{N}: Index applicant_in by email
-- Its own file: CONCURRENTLY can't run inside a transaction.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_applicant_in_email_id
    ON applicant_in(email_id);
```

- Flyway runs a file containing `CONCURRENTLY` outside a transaction. If that file also holds
  ordinary statements, Flyway refuses to run it (unless `mixed` is enabled, which we don't do). So
  keep the statement alone in its file.
- A failed `CONCURRENTLY` build leaves an INVALID index, and `IF NOT EXISTS` then skips it. Drop
  it before retrying.

**New constraint on an existing big table: `NOT VALID`, then `VALIDATE`.**

```sql
-- V{N}: adds the foreign key without scanning existing rows (brief lock only)
ALTER TABLE {child_table}
    ADD CONSTRAINT fk_{child}_{parent}
    FOREIGN KEY ({parent}_id) REFERENCES {parent_table}(id) NOT VALID;

-- V{N+1}: checks existing rows without blocking writes
ALTER TABLE {child_table} VALIDATE CONSTRAINT fk_{child}_{parent};
```

The same works for `CHECK` constraints. (Don't put a `CHECK` on an enum column. Every new enum
value would then need a migration, which is the reason enums are `VARCHAR`.)

For `SET NOT NULL` on a big table: add `CHECK (col IS NOT NULL) NOT VALID`, validate it, then
`SET NOT NULL` (PostgreSQL 12+ uses the validated check and skips the scan), then drop the check.

**Renames and drops: expand, then contract, across releases.**

1. Release N: add the new column. The code writes both columns.
2. Backfill the new column.
3. Release N+1: the code reads and writes only the new column.
4. Release N+2: drop the old column.

Never rename or drop a column that the currently deployed code still uses. The IN rename from
`*_v2` to `*_in` is the cautionary example: `ddl-auto=update` created the new tables and left the
old ones, and a constraint had to be dropped by hand.

**Type changes.** Changing a column's type rewrites the table under an exclusive lock. Widening
`VARCHAR(n)` to a larger `n`, or to `TEXT`, is the safe exception (no rewrite).

**Backfills.** A single `UPDATE` on a small table is fine. On a big table, a single `UPDATE` locks
every row it touches for the whole migration. Backfill in batches from a job or script instead,
and keep the migration to schema only.

---

## Absolute Rules

- Never `DELETE FROM` or `TRUNCATE` in a migration.
- `DROP TABLE IF EXISTS` only for a table confirmed dead, with the reason in the header.
- `UPDATE` only for targeted data fixes and small backfills.
- Never modify an applied migration.
- Never put secrets or real personal data in a migration (seed data included).
- Never run a migration, or a test that changes schema, against a shared database (dtg, prod).
  Database tests must refuse to start unless the datasource is local (the ported
  `LocalDatabaseOnlyInitializer`, see `integration-testing`). Keep that guard.

---

## Verify

`mvn compile -q` doesn't read SQL. For every migration:

1. Start against a **fresh local** PostgreSQL database (a new, empty one) and read the startup
   log: Flyway lists every migration it applied. A syntax error stops startup here.
2. With `ddl-auto=validate`, Hibernate then checks every entity against the schema. A missing
   column or a wrong column type fails here. (This is what catches an entity change that has no
   migration.)
3. Start again on the same database: Flyway should report "Schema ... is up to date" and apply
   nothing, which proves the migration ran once and isn't broken by a restart.
4. Run the module's repository tests (`integration-testing`) against that database.

If a step can't run (no local PostgreSQL, database not empty), report it as not run and say why
(`verification-before-completion`). Never test a migration against a shared database.
