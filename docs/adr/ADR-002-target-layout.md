# ADR-002: Target Package Layout

## Status
Accepted 2026-11-05 (gate D-06)

## Context
The rewrite happens in `D:\WM-Admin-Portal-BE`, whose base package is already `com.wealthmax`. The
old `careers_in` module has an inner layout (`controller`, `dto`, `entity`, `mapper`, `repository`,
`service`, `service.impl`) that matches the project's coding standards. Earlier drafts of the audit
report proposed renaming the base package to `com.wealthmax.portal` and splitting every module into
`api/application/domain/infrastructure`.

## Decision
- Base package `com.wealthmax`.
- `common` is the shared kernel. Feature modules are `identity`, `careers`, `leads`, `content`,
  `reporting` and `notification`.
- Every module uses the `careers_in` inner layers: `controller`, `dto/request`, `dto/response`,
  `entity`, `mapper`, `repository`, `service` (the module's public API) and `service/impl`, plus an
  optional `event`.
- No package rename, and no `api/application/domain/infrastructure` split.
- Which module owns which table is a separate decision (gate D-08, proposed in
  `docs/revamp-implementation-plan.md` section 6) and is not settled by this ADR.

## Consequences

### Positive
- `careers_in` ports with a package move, not a redesign.
- The layout matches the `coding-standards` and `modular-monolith` skills.

### Negative
- There is no explicit ports-and-adapters split. The vendor-SDK rule and the boundary test (ADR-005)
  cover the main risk.

### Neutral
- A module's `service` package is its public API. Everything else in the module is off limits to other
  modules.

## Alternatives Considered
**Rename to `com.wealthmax.portal`:** rejected. It has a cost and no stated benefit.

**`api/application/domain/infrastructure` inside each module:** rejected. It contradicts the shipped
`careers_in` layout and the project's own skills.

## References
- `docs/revamp-audit-report.md`, sections 6.1 and 6.6
- `.claude/skills/coding-standards/SKILL.md`
- `.claude/skills/modular-monolith/SKILL.md`
