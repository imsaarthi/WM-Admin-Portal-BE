# ADR Template: WM-Admin-Portal-BE

## ADR Format

```markdown
# ADR-{number}: {Title}

## Status
[Proposed | Accepted | Deprecated | Superseded by ADR-XXX]

## Context
[The situation and forces at play. What's the problem? What constraints exist?]

## Decision
[State the decision clearly. What are we going to do?]

## Consequences

### Positive
- [Benefit]

### Negative
- [Drawback]

### Neutral
- [Side effect that's neither]

## Alternatives Considered
[What else was evaluated, and why was it rejected?]

## References
- [Link to a doc under docs/, a skill under .claude/skills/, or a plan]
```

---

## Example

```markdown
# ADR-001: Modular Monolith, Single Tenant

## Status
Proposed

## Context
WM-Admin-Portal-BE is the back end of the Wealthmax admin portal and the website forms that
feed it (careers, applicants, referrals, newsletters, leads). Constraints:

- It serves one organisation: one database, one auth subsystem, no tenants
- Small team: fast iteration matters more than independent deployability
- Azure-hosted, single deployment
- No evidence any area needs independent horizontal scaling
- The old repository is flat layers plus one module (careers_in); this repository rewrites it
  as modules from the start

## Decision
Use a modular monolith. Each business capability is a package under `com.wealthmax`
that owns its endpoints, logic and tables. Modules communicate through each other's service
interfaces, DTOs and Spring application events, never through entities or repositories. `common`
holds shared kernel code and depends on no module. The application is single tenant, so there is
no tenant column, context or filter.

## Consequences

### Positive
- One deployable, one process to debug, simple transactions
- A clear home for every class, and for every table
- A module can be extracted later if a real scaling trigger appears

### Negative
- The compiler doesn't enforce boundaries, so `ModuleBoundaryArchitectureTest` (ArchUnit) does
- Every feature from the old repository must be ported module by module
- One module's bug can still affect the whole process

### Neutral
- All modules share one PostgreSQL database and one schema

## Alternatives Considered

**Microservices:** rejected. Premature for the team size and domain. It would add network calls,
distributed transactions and deployment overhead with no present scaling need.

**Stay flat:** rejected. Without boundaries, every feature can reach every other feature's data
layer, which is the coupling the audit report describes.

## References
- `docs/admin-portal-module-documentation.md`
- `docs/revamp-audit-report.md`, section 6
- `.claude/skills/modular-monolith/SKILL.md`
- `src/test/java/com/wealthmax/ModuleBoundaryArchitectureTest.java`
```

---

## Naming Convention

ADRs live in:

```
docs/adr/
├── ADR-001-modular-monolith-single-tenant.md
├── ADR-002-adopt-flyway-for-schema-changes.md
└── ADR-003-spring-application-events-for-cross-module-comms.md
```

Filename format: `ADR-{zero-padded-3-digit-number}-{kebab-case-title}.md`. Check `docs/adr/` for
the highest existing number before assigning the next one. Don't guess or reuse a number. The
folder doesn't exist until the first ADR is written.

## Quick Reference

| Section | Purpose | Key question |
|---|---|---|
| Status | Current state | Is this decision still active? |
| Context | Background | Why decide? What constraints exist? |
| Decision | The choice | What exactly are we doing? |
| Consequences | Impact | Positive and negative effects |
| Alternatives | Options | What else was considered, and why rejected? |

Not every decision needs an ADR. See the "Architecture Decision Records" section of
`architecture-designer/SKILL.md` for when to write one and when a plan or patch summary is enough.
