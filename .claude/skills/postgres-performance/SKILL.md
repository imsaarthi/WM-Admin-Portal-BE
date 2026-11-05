---
name: postgres-performance
description: Use when a WM-Admin-Portal-BE endpoint or job is slow, when adding a query, index or list endpoint on a table that will grow, when reading an EXPLAIN plan, or when seeing lock waits, deadlocks or connection-pool timeouts. PostgreSQL with Hibernate 6 and Spring Data JPA; measure first, then fix indexes, fetch plans, pagination, transactions and pool use.
user-invocable: true
---

# PostgreSQL Performance: WM-Admin-Portal-BE

Topics adapted from Supabase's `supabase-postgres-best-practices` (MIT), condensed and rewritten
for this stack. Row-level security is left out: this application is single tenant and enforces
access in code, not in the database.

**Measure first.** A guessed index is as likely to slow writes as to speed reads.

## Our Setup (checked 2026-11-04)

- PostgreSQL (ask DevOps for the server version; developer machines run 17 and 18); Hibernate 7.4
  (Boot 4.1.2); Spring Data JPA.
- HikariCP: Spring Boot's default pool is 10 connections unless `application*.yml`
  overrides it. Read the non-secret `spring.datasource.hikari.*` keys before tuning anything.
- Check `spring.jpa.open-in-view`: Boot defaults it to `true`. With `false`, lazy loading outside a
  transaction throws `LazyInitializationException` instead of silently running extra queries.
  Prefer `false`.
- The IN entities use IDENTITY (`BIGSERIAL`) keys, which stops Hibernate from batching inserts.
- The IN module uses `@BatchSize(size = 50)` on its element collections and JPA `Specification`
  searches. Check any `JOIN FETCH` or `@EntityGraph` in the area you touch.
- Schema changes, including every index this skill recommends, go through Flyway
  (`flyway-migration`).

## 1. Measure

**See the SQL.** Add the SQL loggers to your **local** config temporarily:
`logging.level.org.hibernate.SQL=DEBUG` and `logging.level.org.hibernate.orm.jdbc.bind=TRACE`.
Count the statements one request runs. More than a handful for one screen usually means N+1. Bound
values can contain personal data: don't paste them into tickets or chat.

**Read the plan** on realistic data (a few rows teach nothing):

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... ;   -- paste the real statement, with real parameter values
```

Look for: `Seq Scan` on a big table under a selective `WHERE`, estimated vs actual rows far apart
(stale statistics: run `ANALYZE <table>`), a `Sort` spilling to disk, nested loops over many rows.
`EXPLAIN ANALYZE` runs the statement: wrap writes in `BEGIN; ... ROLLBACK;`. Do this on a local
database, never on a shared one without DevOps.

**Find the worst queries** with `pg_stat_statements` if it's enabled on the server (ask whoever
runs the database):

```sql
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20;
```

## 2. Indexes

- **Index what real queries filter and sort on.** Read the `WHERE` and `ORDER BY` of the slow
  query. A composite index lists the most selective equality column first, then the other equality
  columns, then the sort or range column:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_careers_in_status_job_id
      ON careers_in(status, job_id DESC);   -- illustrative: findAllByStatus sorted by job id
  ```
- **Foreign keys aren't indexed automatically** in PostgreSQL. Index a foreign-key column that's
  used in joins, or whose parent rows get deleted (otherwise every parent delete scans the child
  table). Element-collection tables (`@ElementCollection`) are the usual case here.
- **Partial indexes** for queries that always carry the same condition: soft delete
  (`WHERE is_deleted = FALSE`), or a status (for example `WHERE status = 'ACTIVE'` for a public
  jobs list).
- **Covering indexes** (`INCLUDE (...)`) for a hot list query that reads a few extra columns, so
  PostgreSQL can answer from the index alone.
- **Index types:** B-tree by default. GIN for JSONB containment (`@>`) or full-text search, only
  if you actually query that way. `pg_trgm` GIN indexes can help case-insensitive `LIKE '%x%'`
  searches (the IN list endpoints search this way), once the table is big enough to matter.
  BRIN can suit huge append-only, time-ordered tables.
- **Every index costs writes and disk.** Add one for a measured query, and drop indexes nothing
  uses (`pg_stat_user_indexes.idx_scan = 0` over a long period).
- On a big, busy table, create indexes `CONCURRENTLY`, alone in their own migration (see
  `flyway-migration`).

## 3. Queries Through JPA

- **N+1:** a list that touches a lazy association per row runs one query per row. Fix it in the
  query that loads the list: `JOIN FETCH`, an `@EntityGraph`, `@BatchSize`, or a DTO projection for
  read-only lists. Don't fetch two collections with `JOIN FETCH` in one query (row explosion; with
  `List` it's `MultipleBagFetchException`).
- **Don't filter in Java.** `findAll()` then `.stream().filter(...)` loads the whole table. Put the
  condition in the query, or in a `Specification`.
- **Pagination:** `Pageable` runs `LIMIT/OFFSET` plus a `COUNT(*)` query.
  - Deep pages get slower (PostgreSQL still walks every skipped row). For large tables or infinite
    scroll, use keyset pagination: `WHERE (created_date, id) < (:lastCreated, :lastId) ORDER BY
    created_date DESC, id DESC LIMIT :size`.
  - Return a `Slice` instead of a `Page` when the screen doesn't need the total. It skips the count
    query.
  - The API doc promises `PageResponseDto` for lists, so changing the shape is an API change
    (`api-contract`).
- **Load-by-id is cheap.** `findById` uses the primary key index. For several ids at once, use
  `findAllById`: one query instead of one per id.

## 4. Writes

- `saveAll` on IDENTITY entities is one `INSERT` per row: Hibernate can't batch inserts when the
  database generates the key. For a real bulk load (thousands of rows), use
  `JdbcTemplate.batchUpdate` (raw SQL bypasses the entity mapping, so set every column
  yourself), or run the load outside the request path.
- **Upserts:** `INSERT ... ON CONFLICT (...) DO UPDATE` in a native query, backed by a unique
  constraint, instead of "select, then insert or update" (which races under load).
  The unique constraint comes from a Flyway migration.
- **Big backfills** belong in batches from a job, not in one migration `UPDATE` (see
  `flyway-migration`).

## 5. Transactions, Locking and Jobs

- **Keep transactions short.** No Mailgun or Azure Blob calls inside `@Transactional`: a slow
  provider holds row locks and a pool connection the whole time. Send after commit
  (`TransactionTemplate`, or an event handled by `@TransactionalEventListener`).
- **Deadlocks:** lock rows in a consistent order (for example by `id`) when a transaction updates
  several rows.
- **Scheduled jobs run on every app instance.** With two instances, every `@Scheduled` job
  would run on both. Before running more than one instance, make jobs safe:
  `SELECT ... FOR UPDATE SKIP LOCKED` to split work, or a PostgreSQL advisory lock (or ShedLock)
  so only one instance runs each job. (`ApplicantINServiceImpl` already takes an advisory lock for
  its duplicate check.)
- **Virtual threads** (Java 21, `spring.threads.virtual.enabled=true`) don't add database
  connections. A burst of blocking requests still queues on the Hikari pool.

## 6. Connection Pool

- Symptom: `Connection is not available, request timed out after ...ms`.
- Usual causes: long transactions, remote calls inside transactions, slow queries, a connection
  leak. Raising `maximum-pool-size` hides the cause and moves the pressure to the database.
- Size it against the database: pool size × number of app instances must stay well under
  PostgreSQL's `max_connections`.

## 7. Maintenance

- Autovacuum handles normal churn. High-churn tables (OTP rows, applicant and lead submissions)
  can bloat; check `pg_stat_user_tables.n_dead_tup` and last autovacuum times.
- Run `ANALYZE` after large backfills so the planner's statistics are current.

## 8. Types (see `flyway-migration`)

`BIGINT` keys, `TIMESTAMPTZ`, `NUMERIC(14,2)` for money, `JSONB` over `JSON`, `TEXT` and
`VARCHAR(n)` perform the same (choose by the validation you want).

## Report

For each change: the statement, the plan before and after (key lines), the timing on realistic
data, and the write cost of any new index. If you couldn't measure, say so, and don't claim a
speed-up (`verification-before-completion`).
