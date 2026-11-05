# Patch 1c1: ADR index and ADR-001 to ADR-004

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1c1

## Problem
No `docs/adr/` existed, so the structural decisions lived only as rows in the audit report's register.

## Solution
Created `docs/adr/README.md` (index) and ADR-001 (modular monolith, single tenant), ADR-002 (target
layout), ADR-003 (careers UK/IN merge), ADR-004 (Flyway owns the schema), from the template and the
register in `docs/revamp-audit-report.md` 6.6.

| ADR | Status written | Why |
| --- | --- | --- |
| 001, 002 | Accepted 2026-11-05 | Gate D-06 |
| 003 | Proposed | Gate D-16 is open (cooldown per region) |
| 004 | Proposed | The register says Proposed. **Open question for the user:** their Flyway decisions match it, so mark it Accepted? |

## Verification (real runs)
- All four ADRs contain `## Status`, `## Context`, `## Decision`, `## Consequences`,
  `## Alternatives Considered` and `## References`.
- Every ADR link in the README resolves. 0 em-dashes in the new files.

## Rulings
None new.

## Limitations and follow-up
- ADR-002 says table ownership (gate D-08) is not settled by it.
- ADR-005 to 009 follow in 1c2, and 011 to 013 in 1c3.
