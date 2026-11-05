# ADR-012: MapStruct Mapping with Unmapped Targets as Errors

## Status
Accepted 2026-11-05 (gate D-05)

## Context
The old application mixed ModelMapper and hand-written mappers. ModelMapper matches by name at
runtime, so a forgotten field is silent (finding DTO-11). The skills allowed either style per module.

## Decision
Every new module maps between entities and DTOs with MapStruct. The compiler option
`-Amapstruct.unmappedTargetPolicy=ERROR` is set in the pom, so a target field that nothing maps fails
the build. `-Amapstruct.defaultComponentModel=spring` makes mappers Spring beans. Mapper interfaces
live in the module's `mapper` package, which other modules may not import (ADR-005). ModelMapper is
not used.

## Consequences

### Positive
- A forgotten or renamed field is a compile error, not a null in production.
- Generated code is plain Java with no reflection.

### Negative
- Fields that are deliberately not mapped (ids, audit fields) must be listed with `@Mapping(target = ..., ignore = true)`.

### Neutral
- Proven in step 1b1: a mapper with an unmapped target field fails with `Unmapped target property`.

## Alternatives Considered
**Hand-written mappers:** allowed by the skills, but a forgotten field is still silent.

**ModelMapper:** rejected. Runtime name matching is the source of DTO-11.

## References
- `docs/revamp-audit-report.md`, finding DTO-11
- `plans/Sarthak-plan/patch-1b1-boot-4-pom-summary.md`
