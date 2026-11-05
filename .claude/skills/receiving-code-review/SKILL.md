---
name: receiving-code-review
description: Use when acting on code review feedback for WM-Admin-Portal-BE (from the user, a teammate's Azure DevOps PR comment, the code-reviewer agent, review-branch or any AI reviewer), before changing any code. Check each point against the codebase and project rules, clarify unclear items first, push back with evidence when a suggestion is wrong, then fix one item at a time with a test.
user-invocable: true
---

# Receiving Code Review: WM-Admin-Portal-BE

Based on `receiving-code-review` from obra/superpowers (MIT License, Copyright (c) 2025 Jesse
Vincent), rewritten for this project.

## The Rule

**Check before you change.** A review comment is a claim about the code. Confirm it against the
real code and the project rules before acting on it. Being technically right matters more than
being agreeable.

## The Sequence

1. **Read all of it** before reacting.
2. **Restate each point** in your own words. If you can't, it's unclear (see below).
3. **Check it against the code:** open the file, read the lines, trace the callers.
4. **Judge it for this codebase:** the project skills and the module boundary tests win over
   generic advice.
5. **Respond:** a short technical acknowledgement, or a reasoned pushback.
6. **Fix one item at a time,** each with a test, then run the suite.

## Unclear Items Come First

If any item is unclear, don't implement any of them yet. Ask about the unclear ones first. Items
are often linked, and a half-understood set produces a wrong fix.

> "I understand items 1, 2, 3 and 6. Before I change anything: in item 4, do you mean the
> controller or the service check? And item 5, which endpoint?"

## Where the Feedback Came From

**The user:** trusted. Implement once you understand it. Still ask when the scope is unclear.

**A teammate's PR comment (Azure DevOps):** check it like any other claim. They may lack
context, or be right about something you missed.

**AI reviewers** (the `code-reviewer` agent, `review-branch`, `/code-review`, anything
automated): verify every finding before fixing it. They can be confidently wrong, and they repeat
stale rules, including rules from other projects (multi-tenant ones don't apply here). Reproduce
the problem, or read the code that proves it.

For anything you can't verify, say so: "I can't confirm this without running the database
tests, and local PostgreSQL is down. Shall I leave it open?"

## Suggestions That Break Project Rules

Some reasonable-sounding suggestions are wrong here. Push back and cite the rule:

| Suggestion | Answer |
|---|---|
| "Scope this by tenant" / "add a tenant id" | There is one tenant. No tenant column, context or filter (`modular-monolith`). |
| "Use `hasRole('ADMIN')`" | `User.getAuthorities()` returns the literal `ADMIN`, so `hasRole` looks for `ROLE_ADMIN` and fails. Use `hasAuthority("ADMIN")` (`coding-standards`). |
| "Let Hibernate create the table (`ddl-auto=update`)" | Flyway owns the schema. Write a migration (`flyway-migration`). |
| "Edit the old migration, it's only a typo" | An applied migration is never edited (the checksum would fail). Write a new one. |
| "Make the entity a record" | JPA needs a no-arg constructor and mutable state. Records are for DTOs (`coding-standards`). |
| "Add `@PreAuthorize` and call it done" | Both layers are needed: the URL rule in `SecurityConfiguration` and `@PreAuthorize` on the service method, each with a test (`modular-monolith`). |
| "Rotate or edit that secret in `application*.yml`" | Those values are DevOps's, not this team's (audit SEC-07). |
| "Use `double` for the amount" | `BigDecimal` (`coding-standards`). |
| "Accept the `id` or `role` from the request body" | Server-managed fields never come from the client (`coding-standards`). |
| "Return the entity, it's simpler" | DTOs at every API boundary (`coding-standards`). |
| "Catch it and log it" | Throw the typed exception and let `GlobalExceptionHandler` map it. |
| "Import the other module's repository" | Fails the module boundary test. Use its service interface, DTOs or events (`modular-monolith`). |

If the reviewer argues that the rule itself is wrong, that's an architecture decision. Take it to
the user.

## YAGNI Check

When a reviewer asks to "do it properly" (more options, more endpoints, more abstraction), check
whether anything uses it:

```bash
grep -rn "methodOrEndpointName" src/main src/test
```

Unused? Say so: "Nothing calls this. Remove it instead?" Used? Then do it properly.

## Pushing Back

Push back when a suggestion:
- breaks existing behaviour or tests
- breaks a project rule (table above)
- rests on missing context
- adds something unused
- conflicts with a decision the user already made

How: give evidence, not opinion. Name the file and line, the failing test, or the rule. Ask a
specific question. Keep it short.

> "This would import `ApplicantINRepository` into the careers module, which the boundary test
> forbids (same rule as `ModuleBoundaryArchitectureTest`). Keeping the call through `ApplicantINService`. OK?"

If you pushed back and turn out to be wrong, say it plainly and fix it: "Checked again: you're
right, that endpoint does fall through to the old `permitAll` wildcard. Changing it."

## Order of Work

1. Clarify everything unclear.
2. Then, in this order:
   - blocking: open routes, personal-data exposure, security, data loss, broken build
   - simple: names, imports, small logic
   - complex: refactors
3. Test each fix on its own. A behaviour change gets a test that fails first.
4. Run the related tests, then the full suite (local PostgreSQL needed), then follow
   `verification-before-completion`.

## Acknowledging Correct Feedback

State the fix, not the feeling. Example wording:

- "Fixed: `<what changed>` (`<File>:<line>`). `<test name>` failed before the change and passes
  now."
- "Changed as suggested: `<what>`. Related tests: `<command>` → `<result>`."

Skip performative lines ("You're absolutely right!", "Great point!"). The fix shows you listened.

## Azure DevOps PR Threads

- Read threads through the `mcp__azure-devops__*` tools if they're connected. Tool names vary by
  server version, so check which ones exist first.
- Reply **inside** the thread the comment is on, not as a new top-level comment.
- A reply is visible to the whole team. Show the user the draft and get their OK before posting.
- Resolve a thread only when you fixed it and the user agrees. Never resolve someone else's
  concern on their behalf.
- Never use `gh`. This repo is on Azure DevOps.

## Common Mistakes

| Mistake | Instead |
|---|---|
| Agreeing before checking | Read the code first |
| Fixing everything in one batch | One item, one test, then the next |
| Assuming the reviewer is right | Reproduce it or disprove it |
| Staying quiet about a bad suggestion | Push back with evidence |
| Fixing the clear items while unclear ones are pending | Clarify first |
| Proceeding on something you can't verify | Say so and ask |
