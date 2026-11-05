# ADR-007: Test Infrastructure: Local PostgreSQL with Guards

## Status
Accepted 2026-11-05 (gate D-02)

## Context
Some behaviour only a real PostgreSQL can prove: unique constraints, sequences, native queries,
`Specification` searches, Flyway migrations. There is no H2 in this project. The pom first carried
Testcontainers, which needs Docker on every developer machine. Docker is not installed on at least one
developer machine.

## Decision
Database tests run on the developer's own local PostgreSQL, with safety guards:

- `LocalDatabaseOnlyInitializer` refuses to start a test context unless the datasource host is
  `localhost`, `127.0.0.1` or `::1`.
- `TablesMustBeEmptyExtension` stops a test class when the tables it owns already hold rows. It never
  deletes anything.
- `@PostgresDataJpaTest` runs `@DataJpaTest` with `ddl-auto=validate`, so every run also proves that
  Flyway applied and the entities match.
- Every database test is tagged `database`. CI runs `mvn verify -DexcludedGroups=database`.

Testcontainers is removed from the pom.

## Consequences

### Positive
- No Docker requirement.
- The guards make it hard to point a test at a shared database by accident.

### Negative
- CI cannot run database tests, so migrations and queries are proven only on developer machines.
- A developer needs a local PostgreSQL and a test database.

### Neutral
- Tests that need no database (unit, controller, security-rules, boundary) run everywhere.

## Alternatives Considered
**Testcontainers:** rejected for now. It would let CI run database tests, but needs Docker on every
machine. This can be revisited if every developer gets Docker.

## References
- `docs/revamp-audit-report.md`, section 6.6
- `.claude/skills/integration-testing/SKILL.md`
