# ADR-001: Modular Monolith, Single Tenant

## Status
Accepted 2026-11-05 (gate D-06)

## Context
WM-Admin-Portal-BE is the back end of the Wealthmax admin portal and the website forms that feed it
(careers, leads, referrals, newsletter). Constraints:

- It serves one organisation: one database, one authentication subsystem, no tenants.
- The team is small (three developers), so fast iteration matters more than independent deployability.
- It runs as a single deployment, and nothing shows that one area needs to scale on its own.
- The old repository is flat layers plus one module (`careers_in`). This repository rewrites it as
  modules from the start.

## Decision
Build a modular monolith. Each business capability is a package under `com.wealthmax` that owns its
endpoints, logic and tables. Modules talk through each other's service interfaces, DTOs, enums and
events, never through entities or repositories. `common` is the shared kernel and depends on no
module. The application is single tenant: no tenant column, tenant context or tenant filter anywhere.

## Consequences

### Positive
- One deployable and one process to debug, with simple transactions.
- Every class and every table has a clear home.
- A module can be extracted later if a real scaling reason appears.

### Negative
- The compiler does not enforce boundaries, so `ModuleBoundaryArchitectureTest` does (ADR-005).
- Every feature of the old repository has to be ported module by module.
- One module's bug can still affect the whole process.

### Neutral
- All modules share one PostgreSQL database and one schema.

## Alternatives Considered
**Microservices:** rejected. They add network calls, distributed transactions and deployment work
with no present scaling need for this team size.

**Stay flat:** rejected. Without boundaries any feature can reach any other feature's data layer,
which is the coupling the audit report describes.

## References
- `docs/revamp-audit-report.md`, section 6
- `docs/admin-portal-module-documentation.md`
- `.claude/skills/modular-monolith/SKILL.md`
