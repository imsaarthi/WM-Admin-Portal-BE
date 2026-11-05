# ADR-009: New Repository, New Database, One Rehearsed Cutover

## Status
Accepted 2026-11-05 (gate D-06)

## Context
The old application is unsafe at its base (open routes, hard-coded secrets, no schema migrations) and
runs production. The new repository already exists, with its own empty database.

## Decision
Rewrite in `D:\WM-Admin-Portal-BE` against its own empty database. Production keeps running the old
repository, with safety fixes only, until the cutover. Each module brings a data-migration script from
the old tables. On cutover day all scripts run, the front end switches its API base URL, and the old
application stays read-only as the rollback path for a short, agreed period.

**Why one cutover and not module by module:** every admin screen depends on `identity`, so switching
module by module would make the admin UI log in to two back ends with two token formats. It would also
leave admin screens reading one database while the website forms write to the other. The application
is small (20 entities, 107 endpoints), so one rehearsed migration costs less than running two back
ends in production.

## Consequences

### Positive
- The new code starts clean, with no legacy schema to carry.
- The old and new systems never write the same tables.

### Negative
- A long build before the first release.
- The old repository still needs its Phase 0 fixes in the meantime.

### Neutral
- Every module is rehearsed on staging with production data as soon as it is built.

## Alternatives Considered
**Restructure the old repository in place:** rejected. The new repository exists, and the old base is
unsafe.

**New application on the existing database, sharing tables:** rejected. Two applications would write
the same tables, and the old one runs `ddl-auto=update`, which alters them.

**Strangler, switching module by module:** rejected for the reasons above.

## References
- `docs/revamp-audit-report.md`, section 6.6
- `docs/revamp-implementation-plan.md`, Phase 8
