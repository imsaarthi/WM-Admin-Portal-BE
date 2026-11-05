---
name: executing-plans
description: Use when implementing a written WM-Admin-Portal-BE plan from plans/<DevName>-plan/, phase by phase. Works each task in test-first order, compares every command against the plan's Expected line, records deviations in the plan, verifies at each phase end, writes the patch summary, and stops for approval before the next phase.
user-invocable: true
---

# Executing Plans: WM-Admin-Portal-BE

Based on `executing-plans` and `finishing-a-development-branch` from obra/superpowers (MIT
License, Copyright (c) 2025 Jesse Vincent). Rewritten for this project's phase gates: the upstream
version runs a whole plan without stopping, and this team's workflow doesn't allow that.

**Announce at start:** "I'm using the executing-plans skill to implement this plan."

## The Shape of the Work

- **Within a phase:** work continuously. Don't stop to ask "shall I continue?" between tasks.
- **At the end of each phase:** verify, write the patch summary, report, and **stop for
  approval**. Only an explicit instruction from the user to run several phases back to back
  removes this stop.
- **The plan did the thinking.** Follow it exactly. When it's wrong, decide, write the decision
  down, and keep going (see Rulings).

## Before Task 1

1. **Read the whole plan once.** Note its phases, file lists, Expected lines and constraints. If
   it points at a design note, ADR or audit finding, read that too. It's the authority the plan
   argues from.
2. **Check where you are.**
   ```bash
   git rev-parse --abbrev-ref HEAD
   git status --short
   ```
   - On an integration branch (`Dev`, `Staging`, `main`...)? Stop and ask for a feature branch.
     Never implement on an integration branch.
   - Uncommitted changes that aren't part of this plan? Leave them alone, note them, and keep
     them out of your patch.
3. **Check for earlier progress.** If some steps in the plan are already ticked (`- [x]`) or its
   Progress section has lines, that work is done. Confirm it in `git log`, `git diff` or the files
   themselves, then resume at the first unticked step. After a context compaction, trust the
   plan, git and the patch summaries over your memory.
4. **Load `test-driven-development`.** It governs every task.
5. **Check phase sizes.** A phase that touches more than 5 files breaks the phase limit. Stop and
   ask to split it before starting.
6. **Check the Java level.** If a task uses Java 21-only syntax, `maven.compiler.release` in
   `pom.xml` must already be 21. If it isn't, that's a plan problem: record a ruling.

## The Plan Is the Ledger

Your memory doesn't survive compaction. The plan file does, and the team can see it.

- Tick each step (`- [ ]` to `- [x]`) as soon as it's proven done, in the same turn as the work.
- Add one line per finished task under a `## Progress` heading at the end of the plan:
  `Task 3: complete. Files: X, Y. Tests: mvn test -Dtest=CareersINServiceImplTest → 6/6 pass.`
- Record every deviation under `## Rulings` (next section).

## Per Task

1. **Re-read the task's own text** before starting it. What you remember is a summary; the task
   has the exact names, values and test cases.
2. **Re-read every file before editing it** (context decays). Don't edit from memory.
3. **Work the steps in order.** The failing test comes first, and you watch it fail. A test that
   passes before the implementation exists is a finding about the test.
4. **Compare every command's output with the step's `Expected:` line.** Three outcomes:
   - **Matches:** next step.
   - **The code is wrong:** use `systematic-debugging`. Find the cause. Never bend the code just
     to make the output match.
   - **The plan is wrong** (a name that doesn't exist, an impossible command, a clash with an
     earlier task or with a project skill): make the smallest change that satisfies the goal,
     record a ruling, continue.
5. **Finish the task** only when every named test exists and ran, the task's test run passed,
   and every Expected line was checked against real output. Then tick it and add its Progress
   line.

## Rulings

A deviation without a written ruling is a decision made in secret. Record each one in the plan:

```
## Rulings
- Task 4: Ruling: called CareersINService.getJobDetailsById instead of the plan's repository call.
  Why: the plan's code imported another module's repository, which the module boundary test
  forbids (modular-monolith). Cost if wrong: one method call.
```

Every ruling also goes into the patch summary and your end-of-phase report.

## When to Stop Mid-Phase

Only these stop you before the phase ends. For each, stop and ask:

- An irreversible or destructive action (deleting data, dropping a table outside the plan,
  rewriting git history, editing an applied Flyway migration).
- A security-sensitive change the plan didn't spell out (auth rules, `SecurityConfiguration`,
  secrets, access levels).
- Anything touching the **values** of secrets in `application*.yml`. Those are DevOps's.
- Anything outward-facing: push, PR, merge, publishing, messages to people.
- The phase would go over 5 files.
- The plan is so broken that every way forward is a guess.

## End of Each Phase

1. **Verify** with `verification-before-completion`: `mvn compile -q`, the phase's tests, the
   full `mvn test` when local PostgreSQL is running, the manual class checks, and the grep gate
   for forbidden patterns. For a migration, the fresh-database check in `flyway-migration`.
2. **Check the file list.** `git status --short` must show only the phase's files (plus the plan
   and patch record). Anything else: explain it or take it out.
3. **Write the patch summary** at `plans/<DevName>-plan/patch-{N}{letter}-{feature}-summary.md`,
   following the team's `PATCH_WORKFLOW.md` if one exists in the plans folder. Quote real
   verification results. Add an entry to that folder's `PATCH_INDEX.md` if it has one.
4. **Report** in this order: what changed, verification results (with "not run" where true),
   rulings, anything deferred, what the next phase does.
5. **Stop** and wait for approval.

## After the Last Phase

1. **Review the whole branch** with `review-branch`, or dispatch the `code-reviewer` agent for a
   fresh pair of eyes. A review of your own diff by you is weaker. Say so if that's all you did.
2. **Sort the findings.** Critical and High: fix now. Each fix gets a test that fails first, then
   passes, then a green suite. Medium and Low: list them as deferred. The user decides.
3. **Hand over.** Offer, and wait for the user's choice:
   1. Leave the changes uncommitted for review.
   2. Commit (only when asked; use `commit-message`, and `git add` each file by name).
   3. Open a PR with `azure-pr` (only when asked).

Never merge into an integration branch locally, never push without being asked, never force-push,
and never use `gh` (this repo is on Azure DevOps).

## Excuses and Answers

| Excuse | Answer |
|---|---|
| "I remember what Task 5 says" | You remember a summary. Re-read the task. |
| "The plan's code is obviously right, skip the failing run" | A test you never saw fail proves nothing. |
| "The plan is wrong, I'll just do it properly" | Do it properly and write the ruling. |
| "I'll update the plan's ticks at the end" | Compaction won't wait. Tick as you go. |
| "The next phase is small, I'll roll straight into it" | This workflow requires approval between phases. Stop. |
| "Tests should pass, the change was trivial" | Run them, or report them as not run. |
| "I reviewed my own diff, no need for review-branch" | Same author, same blind spots. |
| "I'll fix the Low findings too while I'm here" | Each fix costs a test and a suite run nobody asked for. Defer them. |
