# Patch 1i1 and 1i2: CI pipeline and stack documentation sync

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1i1, 1i2

## Problem
No CI guarded merges, and `CLAUDE.md`, ten skills and three agent definitions still described Boot 3.5.16, Hibernate 6.6, Flyway 11 and an undecided test infrastructure.

## Solution
- `azure-pipelines.yml` (ADR-013): `Maven@4` on `ubuntu-latest` with JDK 21, `verify -B -DexcludedGroups=database`, JUnit results published. `trigger: none`: Azure Repos does not read a `pr:` block, so a repository administrator adds it as a build-validation policy on `Dev`.
- `CLAUDE.md`: stack line (Boot 4.1.2, Hibernate 7.4, Flyway 12, Jackson 3, Security 7), the CI command and the `database` tag, the one-time test-database set-up, a note that a running app locks the jar on Windows, springdoc 3.2, and the Current Phase (pointing at the master plan and this plan).
- `integration-testing` skill: ADR-007 is decided; describes the support classes that now exist and `@TablesMustBeEmpty`.
- `dependency-upgrade` skill: "Today" and the support table moved to 4.1.2; the 3-to-4 ladder replaced by a note that it was paid in step 1b1; a new "Found in practice" list (starters, moved test packages, the Jackson 3 key, `server.error` rename, microsecond timestamps, nested-class scanning, migrator use).
- 1i2: stack lines in `flyway-migration`, `coding-standards`, `api-designer`, `architecture-designer` (text and diagram), `postgres-performance`, `security-reviewer`, and the `doc-generator`, `code-reviewer`, `test-writer` agents.

## Verification (real runs, 2026-11-05)
- `mvn -B test -DexcludedGroups=database`: **Tests run: 95, Failures: 0, Errors: 0, BUILD SUCCESS**. That is the 104 tests minus the 9 `database`-tagged ones. None of `KernelPersistenceDatabaseTest` or `WmAdminPortalApplicationTests` ran. Both boundary-test classes did.
- Hook self-tests: 41 passed, 0 failed.
- Grep over `.claude/` and `CLAUDE.md` for `Boot 3.5`, `3.5.16`, `Hibernate 6.6`, `Flyway 11`, "open decision (ADR-007": 3 hits, all intentional history in `dependency-upgrade`.

## NOT verified
- **`mvn verify` itself.** It failed locally at the `clean` step: my leftover live-check app (PID 29288) holds `target/wmadminportalbe.jar` open, and Windows cannot delete a jar in use. That is not a defect in the pipeline command. What I could run was the `test` phase with the same flags (above). `mvn package` passed earlier in step 1h4, before the app started, so test and package are each proven, but `verify` end to end is not. Stop the app and run `mvn -B clean verify -DexcludedGroups=database`.
- **The pipeline file has not run.** It needs the push (step 1a2) and a build-validation policy on `Dev`, set by a repository administrator.
- I was blocked from deleting `target/` subfolders (permission denied) and did not retry; I used a non-deleting test run instead.

## Rulings
None new.

## Limitations and follow-up
- CI never runs a migration (ADR-013), so a broken migration is caught only on a developer machine.
- `security-reviewer` text now names the POI and jjwt replacements as an example of a CVE-driven bump; edit it if you prefer a neutral tone.
