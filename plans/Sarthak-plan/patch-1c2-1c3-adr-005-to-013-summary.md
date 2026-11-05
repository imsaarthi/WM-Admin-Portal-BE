# Patch 1c2 and 1c3: ADR-005 to ADR-009, ADR-011 to ADR-013

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1c2 and 1c3

## Solution
- 1c2 (5 files): ADR-005 module boundaries (ArchUnit, generic test), ADR-006 notification outbox, ADR-007 test infrastructure, ADR-008 runtime, ADR-009 new repository and one cutover.
- 1c3 (4 files): ADR-011 error contract and exception names, ADR-012 MapStruct, ADR-013 CI pipeline, and the full `docs/adr/README.md` index.

| ADR | Status written | Basis |
| --- | --- | --- |
| 005 | Accepted | Register (Accepted 2026-11-05). The generic-test refinement was "Proposed" there; it is included here because the user approved the plan that implements it. **Flag for review.** |
| 006 | Proposed | Gate D-09 not decided |
| 007 | Accepted | D-02: local PostgreSQL with guards |
| 008 | Accepted | Register, plus D-01: Boot 4.1.2 |
| 009 | Accepted | D-06 |
| 011, 012, 013 | Accepted | D-04, D-05, D-07 |
| 010 | Reserved | D-03 (URL scheme) is undecided |

## Verification (real runs)
- 12 ADR files, numbered 001 to 009 and 011 to 013.
- Every file has all six template headings (`bad=0`). Every README link resolves. 0 em-dashes.

## Rulings
- ADR-005 refinement marked Accepted (see above). Cost if wrong: change one status line.

## Limitations and follow-up
- ADR-013 describes `azure-pipelines.yml`, which does not exist until step 1i1.
- ADR-004 stays Proposed until the user confirms.
