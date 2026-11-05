# ADR-013: CI Pipeline from Phase 1

## Status
Accepted 2026-11-05 (gate D-07)

## Context
The old repository has no pipeline definition, and its pipeline does not run tests. The boundary test
and the unit tests can guard every merge from the first module, but only if something runs them.
Database tests cannot run in CI, because ADR-007 chose local PostgreSQL and removed Testcontainers.

## Decision
An Azure Pipelines definition (`azure-pipelines.yml`) runs on every pull request into `Dev`:
`mvn -B verify -DexcludedGroups=database` on JDK 21, publishing the surefire results. Database tests
carry the JUnit tag `database` and are excluded in CI. A repository administrator sets the pipeline as
a build-validation policy on `Dev` in Azure DevOps.

## Consequences

### Positive
- The boundary test, controller, service and security-rules tests guard every merge.

### Negative
- CI never runs a migration. A broken migration is caught only by a developer's local database test.

### Neutral
- If Docker becomes available to every developer, ADR-007 can be revisited and the exclusion removed.

## Alternatives Considered
**No CI until later:** rejected. The boundary rules are cheapest to enforce from the first module.

## References
- `docs/adr/ADR-007-test-infrastructure.md`
- `docs/revamp-implementation-plan.md`, gate D-07
