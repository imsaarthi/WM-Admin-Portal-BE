# ADR-004: Flyway Owns the Schema

## Status
Proposed. The decision itself was made by the team on 2026-11-04 (finding OTH-01) and the settings
below were set up on 2026-11-05. It stays Proposed until the team marks it Accepted.

## Context
The old application used `ddl-auto=update` and a startup `schema.sql`, so no schema change was
versioned or reviewable (OTH-01). The new repository starts on a new, empty database.

## Decision
- Flyway runs every schema change. Migrations live in `src/main/resources/db/migration` and are named
  `V{version}__{module}_{description}.sql`.
- `spring.jpa.hibernate.ddl-auto` is `validate` in every profile, so a missing migration stops startup.
- `spring.flyway.baseline-on-migrate` is `false`. There is no baseline from the old database.
- Old data arrives through each module's data-migration script at the cutover (ADR-009), never through
  a Flyway migration.
- The old `schema.sql` and the `careers_in_job_number_seq` sequence become the careers module's first
  migration.
- A migration that has been applied is never edited. A new migration replaces it.

## Consequences

### Positive
- Schema changes are reviewed, versioned and repeatable on a fresh database.
- Entity and table drift fails at startup instead of in production.

### Negative
- Every entity change needs a migration written by hand.
- The data-migration scripts must read the old schema as it really is in production, drift included.

### Neutral
- In Boot 4, `spring-boot-starter-flyway` is needed for auto-configuration, along with
  `flyway-database-postgresql`.

## Alternatives Considered
**Keep `ddl-auto=update`:** rejected. It never alters or drops, so the real schema diverges from the
code.

**Baseline from production:** rejected. Production data arrives by migration script, so a baseline
adds nothing.

## References
- `docs/revamp-audit-report.md`, finding OTH-01 and section 6.6
- `.claude/skills/flyway-migration/SKILL.md`
