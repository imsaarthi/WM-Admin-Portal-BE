# Architecture Decision Records

One file per structural decision. Template: `.claude/skills/architecture-designer/references/adr-template.md`.
Filename: `ADR-{3-digit number}-{kebab-case-title}.md`. Check this index for the highest number before
adding one, and never reuse a number.

Status values: Proposed, Accepted, Deprecated, Superseded by ADR-XXX.

| ADR | Decision | Status |
| --- | --- | --- |
| [ADR-001](ADR-001-modular-monolith-single-tenant.md) | Modular monolith, single tenant | Accepted 2026-11-05 |
| [ADR-002](ADR-002-target-layout.md) | Target package layout | Accepted 2026-11-05 |
| [ADR-003](ADR-003-careers-uk-in-merge.md) | careers: UK and IN merged, region as an attribute | Proposed |
| [ADR-004](ADR-004-flyway-schema-ownership.md) | Flyway owns the schema | Proposed |
| [ADR-005](ADR-005-module-boundaries-archunit.md) | Module boundaries enforced by ArchUnit | Accepted 2026-11-05 |
| [ADR-006](ADR-006-notification-outbox.md) | Notification outbox | Proposed |
| [ADR-007](ADR-007-test-infrastructure.md) | Test infrastructure: local PostgreSQL with guards | Accepted 2026-11-05 |
| [ADR-008](ADR-008-runtime-java-21-spring-boot-4.md) | Runtime: Java 21 and Spring Boot 4.1.2 | Accepted 2026-11-05 |
| [ADR-009](ADR-009-new-repository-one-cutover.md) | New repository, new database, one rehearsed cutover | Accepted 2026-11-05 |
| ADR-010 | URL scheme and filter chains (gate D-03) | **Reserved**, not written yet. Decide before Phase 2a |
| [ADR-011](ADR-011-error-contract.md) | Error contract and shared exceptions | Accepted 2026-11-05 |
| [ADR-012](ADR-012-mapstruct-mapping.md) | MapStruct, unmapped targets are errors | Accepted 2026-11-05 |
| [ADR-013](ADR-013-ci-pipeline.md) | CI pipeline from Phase 1 | Accepted 2026-11-05 |

Source of the proposals: `docs/revamp-audit-report.md` section 6.6.
