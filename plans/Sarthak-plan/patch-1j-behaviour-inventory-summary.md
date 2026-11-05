# Patch 1j1 and 1j2: Behaviour inventory

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1j1, 1j2

## Problem
The rewrite ports behaviour, never code, but the behaviour was written down only as prose in a 2,000-line document. Nothing listed the rules a new module must preserve, or the old defects it must not.

## Solution
`docs/behaviour-inventory.md`: one table per area, 98 rows, each with an ID (`BR-<AREA>-nn`), the rule, a Kind (**Keep**, **Change** or **Drop**), the source section of `docs/admin-portal-module-documentation.md`, and the phase that writes the test. Linked from the Reference Documents table in `CLAUDE.md`.

| Area | Rows | Phase |
| --- | --- | --- |
| Platform (email, files, jobs) | 12 | 2 (one row is phase 4: job expiry) |
| Identity | 14 | 3 |
| careers | 28 | 4 |
| leads | 21 | 5 |
| content | 20 | 6 |
| reporting | 3 | 7 |

Totals: 66 Keep, 29 Change, 3 Drop. A Change row says what the new system does instead, so it can be turned into a test before the feature.

## Verification (real runs, 2026-11-05)
- Every row has a well-formed ID, a Kind of Keep/Change/Drop, one or more source sections, and a phase from 2 to 7. No duplicate IDs. All 21 source sections I read (4.7 to 4.9, 5.1 to 5.18) are cited at least once.
- 0 em-dashes.

## An error of mine, caught and repaired
I first ran `sed` to swap escaped pipes for `&#124;`. The pattern matched the empty string and inserted `&#124;` between every character, ruining the file. My own check showed it at once (0 rows found). I restored the file by rewriting it from the content I had written, with the pipe written as `&#124;` in the three rows that need it (BR-LED-02, BR-LED-03, BR-CON-11), then re-ran every check. Lesson: test a `sed` on a copy first.

## Rulings
- Rows are tagged with phases 2 to 7. The plan said 3 to 7, but its own Phase 2 owns email and file storage, so those rules need a phase-2 test.
- The inventory is built from the module documentation only. Rows were not re-checked against the old source code in this step.

## Limitations and follow-up
- **Not covered:** section 7 (the login flow in detail), sections 4.1 to 4.6, 4.10 to 4.12 and 8 to 11. Phase 3 must read section 7 and add identity rows before writing the identity tests.
- Rows marked "(open)" wait for gates D-08, D-14, D-15 and D-16.
- BR-CON-16 and BR-CON-17 (fields that can be set on create but not on update) look accidental. They are marked Keep with "confirm this is intended"; ask the product owner.
