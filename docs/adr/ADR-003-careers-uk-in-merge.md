# ADR-003: careers: UK and IN Merged, Region as an Attribute

## Status
Proposed. Needs a per-region cooldown policy (gate D-16) before Phase 4.

## Context
The old application has two careers implementations: the original one for the UK market (`careers`,
`applicant`) and `careers_in` for the India market, which was written as a separate module with its
own tables, URLs and rules (6-month cooldown, `WFA-IN-0001` job numbers, CTC in LPA). The new
repository starts with empty tables, so merging them costs a mapping in the data-migration script
instead of a change to live tables.

## Decision (proposed)
One `careers` module. `region` (UK or IN) is an attribute of a job, not a copied module. The job
number comes from a sequence per region. The cooldown becomes a per-region rule: IN keeps 6 months,
and UK needs a value or none.

## Consequences

### Positive
- One code path to maintain, one front-end contract.
- The merge is cheap now because the tables are new.

### Negative
- The `careers_in` URLs and response shapes change, so the front end has to move.
- Application fields differ by region (the UK application is flat, the IN one has five sections), so
  the shared model needs a decision on which fields are common.

### Neutral
- Data-migration maps both old table families into the new tables.

## Alternatives Considered
**Keep UK and IN as separate modules:** not rejected yet. It duplicates logic, but avoids the shared
model decision.

## Open questions
- Cooldown value for UK, and whether it applies at all.
- UK job-number series.
- Default region for phone normalisation (E.164).

## References
- `docs/revamp-audit-report.md`, section 6.6
- `docs/careers-applicant-in-api.md`
- `docs/revamp-implementation-plan.md`, gate D-16
