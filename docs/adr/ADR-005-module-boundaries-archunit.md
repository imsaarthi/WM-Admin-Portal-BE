# ADR-005: Module Boundaries Enforced by ArchUnit

## Status
Accepted 2026-11-05. The refinement (one generic test for every module) is part of this decision and
is implemented in Phase 1 step 1g.

## Context
ADR-001 makes module boundaries the main protection against coupling, and the compiler does not
enforce them. Spring Modulith's `ApplicationModules.verify()` was the other option.

## Decision
Enforce boundaries with ArchUnit, not Spring Modulith. One generic `ModuleBoundaryArchitectureTest`
applies the rules to every top-level package under `com.wealthmax`, so a new module cannot be
forgotten. The rules:

1. No class in one module depends on another module's `entity`, `repository`, `mapper` or
   `service.impl` package.
2. `common` depends on no module.
3. There are no dependency cycles between top-level packages.
4. Nothing depends on the old `com.wealthmax.adminPortal` package.

The test needs no database and runs in CI. The rules are tested against small fixture classes, so a
rule that stops detecting violations fails the build. The old per-module `INModuleBoundaryTest` is not
ported; its rules are.

## Consequences

### Positive
- Boundaries are checked on every build, for every module, from day one.
- No framework dependency, and no expectations about package layout beyond the rules above.

### Negative
- The rules match on package names, so a class in the wrong package escapes them.
- The test uses ArchUnit core with a plain `@Test`, not the ArchUnit JUnit engine.

### Neutral
- A rule is never relaxed with an exception. A shared piece moves into `common` instead.

## Alternatives Considered
**Spring Modulith `verify()`:** rejected. It adds a framework dependency and expects package
conventions of its own.

**One hand-written test per module:** rejected. A new module without a test would go unchecked.

## References
- `docs/revamp-audit-report.md`, section 6.6
- `.claude/skills/modular-monolith/SKILL.md`
