---
name: verification-before-completion
description: Use before saying any WM-Admin-Portal-BE work is done, fixed, passing or ready, and before writing a patch summary, committing, opening a PR or starting the next phase. Requires running the proving command in this session and quoting its real result. Evidence first, claim second.
user-invocable: true
---

# Verification Before Completion: WM-Admin-Portal-BE

Based on `verification-before-completion` from obra/superpowers (MIT License, Copyright (c) 2025
Jesse Vincent), rewritten for this project.

## The Rule

**No "done", "fixed", "passing" or "works" without evidence produced in this session.**

If you didn't run the command since your last change, you don't know the result. Say what you
actually know instead.

## The Gate

Before any claim about the state of the work:

1. **Name the proof.** Which command or check would show the claim is true?
2. **Run it now,** in full, after your last edit.
3. **Read the output.** Exit code, failure count, the actual lines. Not the first screen.
4. **Compare.** Does the output prove the claim?
   - Yes: make the claim and quote the evidence.
   - No: report the real state, with the evidence.
5. **Only then** say it.

Skipping a step turns a report into a guess.

## What Proves What

| Claim | Proof | Not proof |
|---|---|---|
| It compiles | `mvn compile -q` exit 0, in this session | "the IDE shows no red", an earlier build |
| Tests pass | `mvn test` shows `Tests run: N, Failures: 0, Errors: 0` and `BUILD SUCCESS` | a run before your last edit, "should pass" |
| This test passes | `mvn test -Dtest=Class#method` passing now | the class passing yesterday |
| A migration is correct | a fresh local database started: Flyway applied it, `ddl-auto=validate` passed, a second start applied nothing | `mvn compile` (it never reads SQL), reading the file |
| A database claim holds (constraint, search, sequence) | a repository test on the real local PostgreSQL | a Mockito unit test (a mocked repository runs no SQL) |
| Module boundaries hold | the module boundary test passes (`mvn test -Dtest=ModuleBoundaryArchitectureTest`) | "I checked the imports by eye" |
| A route is open or protected | a security-rules test with the real `SecurityConfiguration` | the controller "looks right", a standalone `MockMvc` controller test |
| Bug is fixed | the original symptom reproduced first, and now gone | "I changed the code that looked wrong" |
| Regression test is real | it failed with the fix reverted, and passes with it | it passes once |
| Endpoint works | a controller or integration test hitting it, or a real request with the response shown | the controller "looks right" |
| Sub-agent finished | `git status` and `git diff` show the changes it claims | the agent's own summary |
| Phase is complete | re-read the plan's file list and steps, and tick each against real evidence | "tests pass" |

## WM-Admin-Portal-BE Checks

Run these before calling any code phase complete:

```bash
mvn compile -q; echo "compile exit=$?"
```

```bash
mvn test > target/mvn-test.log 2>&1; echo "test exit=$?"
grep -E "Tests run:|BUILD|\[ERROR\]" target/mvn-test.log | tail -20
```

- Write long Maven output to `target/` (gitignored) and read its tail. Tool output can be cut
  short, and a truncated log hides the failures.
- `mvn test -q` prints nothing on success. Use the exit code, or run without `-q` for counts.
- **Local PostgreSQL down means the database tests can't run.** They fail to connect. That's not a
  code failure, and it's not a pass. Report "database tests not run: local PostgreSQL isn't
  running", and never write "tests pass". Docker isn't involved while ADR-007 keeps the local-PostgreSQL approach.
- **The guards can stop a run on purpose.** `LocalDatabaseOnlyInitializer` refuses a non-local
  datasource, and `TablesMustBeEmptyExtension` stops if the module's tables hold rows. Report
  that as "stopped by the safety guard", never bypass it.
- **What still runs without a database:** the module boundary test, the security-rules test,
  controller tests and service unit tests. Run them and quote the result:
  ```bash
  mvn test -Dtest='ModuleBoundaryArchitectureTest,*SecurityRulesTest,*ControllerTest,*ServiceImplTest' > target/nodb.log 2>&1; echo "exit=$?"
  grep -E "Tests run:|BUILD" target/nodb.log | tail -3
  ```

Then the manual checks on every new or changed class:
- An entity is a class with `Long id` (`IDENTITY`), not a record, and has a Flyway migration.
- A controller returns a response DTO, never an entity.
- A new module has its `<Module>ModuleBoundaryTest`.
- A new route has an explicit `SecurityConfiguration` rule above any wildcard that would catch it.

And a grep over the branch's changes for patterns that must not appear in new code (tested in Git
Bash):

```bash
BASE=$(git merge-base origin/Dev HEAD)   # use the exact base branch name: git branch -r
# Single tenant: no tenant concepts anywhere
git diff "$BASE" -- src | grep -niE "^\+.*(tenant|TenantContext)"
# No new wide-open CORS
git diff "$BASE" -- src/main | grep -nE '^\+.*@CrossOrigin\("\*"\)'
# Hibernate must not change the schema: no ddl-auto update or create in committed config
git diff "$BASE" -- 'src/main/resources/*.properties' | grep -nE '^\+.*ddl-auto=(update|create)'
# Controllers must not take or return entities
git diff "$BASE" -- '*/controller/*' | grep -nE "^\+.*Entity\b"
# Applied migrations untouched (only additions are allowed). Must print nothing:
git diff --name-status "$BASE" -- src/main/resources/db/migration | grep -v "^A"
# git diff skips untracked files, so check new files too
git ls-files --others --exclude-standard -- src/main | xargs -r grep -niE "tenant|@CrossOrigin\(\"\*\"\)"
```

No output means no hits. Every hit needs a reason, or a fix.

If the change touches secrets, the **values** in `application*.yml` are DevOps's: verify that
the diff doesn't include any change to them.

## Say It Straight

When reporting, lead with the result, then the evidence:

- "Compile passes (`mvn compile -q`, exit 0). Database tests not run: local PostgreSQL isn't running."
- "3 of 4 new tests pass. `duplicateJobId_failsAtTheDatabase` fails: expected a constraint
  violation, got none."
- "Fixed. The reproduction test failed before the change and passes after."

The patch summary's Verification Results section follows the same rule. Quote what ran and what it
printed. "Not run" plus the reason is an honest result. A guessed pass is not.

## Red Flags: Stop and Verify

- About to write "should work", "probably", "looks good", "seems fine".
- About to write "Done!", "All set", "Perfect" before running anything.
- About to commit, open a PR or start the next phase without a fresh run.
- Trusting a sub-agent's success message without looking at the diff.
- Running a narrow test and implying the whole suite passes.
- Reasoning that this change is "too small to break anything".
- Changing the claim's wording so the rule seems not to apply.

## Excuses and Answers

| Excuse | Answer |
|---|---|
| "It's a one-line change" | One line can break the build. Compile takes seconds. Run it. |
| "The tests passed before my last edit" | That result belongs to the old code. |
| "PostgreSQL is down, so it's probably fine" | Probably is not evidence. Say the database tests didn't run. |
| "The migration looks right" | Run it on a fresh local database. Reading SQL proves nothing. |
| "The agent said it succeeded" | Check `git diff`. Agents report intent, not outcome. |
| "It's only docs" | Then say what you did check (grep gates, `mvn compile -q`) and that tests weren't needed. |
| "I'm confident" | Confidence is not output. |

## When This Applies

Always before:
- any statement that work is complete, fixed, passing, working or ready
- writing a patch summary or a PATCH_INDEX entry
- committing, or asking the user to commit
- opening or updating a PR
- starting the next task or phase
- relaying a sub-agent's result to the user
