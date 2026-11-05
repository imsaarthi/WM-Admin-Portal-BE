---
name: review-branch
description: Project-specific review of everything on the current branch (all commits since it left the base branch, plus uncommitted and untracked files) for module-boundary violations, missing access rules, auth and token flaws, Spring pitfalls, unsafe Flyway migrations and PII in logs. Report only, no edits. Use when asked to review changes, find bugs, security-review or audit a branch before a PR.
user-invocable: true
---

# Review Branch: WM-Admin-Portal-BE

Method adapted from `find-bugs` in getsentry/skills (Apache License 2.0). The checklists are
WM-Admin-Portal-BE's own.

**Report only.** Don't edit code. The user decides what to fix.

This is a **single-tenant modular monolith** (Java 21, Spring Boot 3.4+, Flyway). There is no tenant
to leak between. The highest-stakes checks are: module boundaries and data ownership, who may call
each route, personal data exposure, and safe migrations.

If the harness also offers `/code-review` or `/security-review`, they're a useful second opinion.
This skill adds what they can't know: this codebase's module rules, access scheme and conventions.

---

## Phase 1: Gather the Whole Change

Review against the branch the PR targets (`azure-pr`: `Dev` by default) unless the user names
another base. The remote's branches are spelled `dev`, `staging`, `Aryan` and so on, and git refs
are case-sensitive, so check with `git branch -r` and use the exact name.

```bash
git fetch origin Dev
BASE=$(git merge-base origin/Dev HEAD)
git log --oneline "$BASE"..HEAD            # commits on this branch
git diff --stat "$BASE"                    # committed + uncommitted changes vs the base
git ls-files --others --exclude-standard   # untracked files: git diff never shows these
```

- Read every changed line. If the diff is long, write it to `target/review.diff` (gitignored) and
  read it in chunks. Never review from a truncated preview.
- For any non-trivial change, read the **whole file**, not just the hunk. A helper's behaviour
  only matters once you see who calls it.
- List every changed file, grouped by module, before going further.

## Phase 2: Map the Attack Surface

For each changed file, note what it touches:

- **Inputs:** request bodies, path variables, query parameters, headers, uploaded files, OTPs and
  reset tokens.
- **Database access:** derived queries, JPQL, native queries, `JdbcTemplate`, by-id loads.
- **Auth:** `SecurityConfiguration` rules (and their order), public (`permitAll`) paths.
- **Module edges:** imports of another module, entities crossing a layer, shared `common` code.
- **External calls:** Mailgun, Azure Blob, any `RestTemplate`, `RestClient` or HTTP client.
- **Secrets and crypto:** JWTs, OTPs, hashing, any key handling.
- **Logging, config and migrations.**

## Phase 3: Checklist (every item, for every file it applies to)

### A. Module boundaries and data ownership (the #1 structural risk)
- [ ] An import of another module's `entity`, `repository`, `mapper` or `service.impl`. Anything
      imported from the old repository's `com.wealthmax.adminPortal` packages.
- [ ] A JPA relationship, join, native query or foreign key reaching another module's table.
- [ ] A change to `ModuleBoundaryArchitectureTest` that adds an exception to make the build pass.
- [ ] A controller or service returning or accepting an **entity** instead of a DTO.
- [ ] Code used by two modules still sitting inside one of them (should be promoted to `common`).
- [ ] `common` importing a feature module.
- [ ] Any tenant concept in new code: `tenant`, `TenantContext`, `tenant_id`, a tenant filter.
      There is one tenant. Their presence is a sign something was ported from another project.
- [ ] A cache keyed on something that varies by caller (user, role) without that in the key.

### B. Access control
- [ ] Every new route has an explicit level in `SecurityConfiguration`: Public, Authenticated or
      `hasAuthority("ADMIN")`. Public rules are narrow (method and path) and commented.
- [ ] Rule **order**: a narrow rule sits above the wildcard that would also match it. A new route
      doesn't fall through to an old `permitAll` wildcard by accident.
- [ ] `authenticated()` means any role: is that really intended for a route that reads or deletes
      personal data or changes content? Can a self-registered user reach it?
- [ ] `hasAuthority(...)`, not `hasRole(...)`.
- [ ] A record that belongs to a user is checked against the authenticated user (ownership), not
      only fetched by id. Sequential ids are guessable.
- [ ] A public route never returns or deletes personal data (applicants, leads, subscribers,
      resumes).
- [ ] Security tests exist in both directions (open without a token, rejected without one).

### C. Authentication and tokens
- [ ] OTPs and tokens compared with `MessageDigest.isEqual`, never `equals`.
- [ ] OTPs, tokens, passwords and hashes never logged.
- [ ] OTP bound to the user and the attempt it was issued for, attempts capped, expiry enforced,
      single use.
- [ ] Refresh and reset tokens stored hashed. A bad or expired token doesn't break public routes.
- [ ] Key handling: no signing key literal in Java. (The **values** of secrets in
      `application*.yml` are DevOps's: don't ask for them to be changed here, and never
      quote them.)

### D. Input validation and injection
- [ ] `@Valid` on every `@RequestBody`. Constraints (`@NotBlank`, `@Size`, `@Email`...) on DTO
      fields. Collections and strings bounded.
- [ ] Uploads checked for type and size, and stored under a server-generated name.
- [ ] No string-built JPQL or SQL. Parameters only.
- [ ] No mass assignment: request DTOs can't carry `id`, `role`, `status` or other fields the
      caller shouldn't set.
- [ ] Values placed into email templates or HTML are escaped.

### E. Data integrity and transactions
- [ ] Money is `BigDecimal`, compared with `compareTo` (`equals` also compares scale).
- [ ] Multi-step writes share one transaction. Reads that need no writes don't hold one open.
- [ ] "Check then insert" (`existsBy...` then `save`) is backed by a unique constraint (in a
      Flyway migration), or two requests can both pass the check.
- [ ] List endpoints paginate (`PageResponseDto`). No N+1 loops.
- [ ] No Mailgun or Azure Blob call inside a transaction: send after commit.

### F. Spring and Java sharp edges
- [ ] **Self-invocation:** calling a `@Transactional`, `@Async` or `@Cacheable` method from the
      same class skips the proxy, so the annotation does nothing.
- [ ] `@Transactional` on a private or final method does nothing.
- [ ] Catching an exception inside a transaction and carrying on can leave it rollback-only, so
      the commit fails later with a confusing error.
- [ ] Checked exceptions don't roll back by default.
- [ ] `@TransactionalEventListener` runs after commit by default: writes inside it need
      `REQUIRES_NEW`, and events published outside a transaction are dropped.
- [ ] `Long` ids compared with `==`. It works up to 127 and fails above it. Use `Objects.equals`.
- [ ] Lazy associations touched outside a transaction, or entities serialized by Jackson.
- [ ] `Optional.get()` without a check.
- [ ] HTTP clients without timeouts. Any trust-all TLS code.
- [ ] `@Value("${x:}")`-style empty defaults for secrets (fail open).
- [ ] Java 21: no `record` as a JPA entity. Records for DTOs are fine, one style per module.
      No Java 21-only syntax while `maven.compiler.release` is below 21.
- [ ] If virtual threads are enabled, no `synchronized` blocks around blocking calls (thread
      pinning) and no `ThreadLocal` assumptions.

### G. Migrations (Flyway)
- [ ] Applied migrations untouched. This must print nothing:
      `git diff --name-status "$BASE" -- src/main/resources/db/migration | grep -v "^A"`
- [ ] New files follow `flyway-migration`: next version, `IF NOT EXISTS`, `BIGSERIAL`,
      `TIMESTAMPTZ`, named constraints, locking rules for big tables. No version-number clash
      with another branch.
- [ ] **Every entity change has a migration**, and `ddl-auto` is still `validate` (never `update`
      or `create` in a committed config).
- [ ] A migration touches only its own module's tables. No foreign key to another module's table
      unless the plan records why.
- [ ] No `DELETE FROM`, `TRUNCATE`, secret or real personal data in a migration.
- [ ] A new table has an index for each real query pattern, and no speculative ones.
- [ ] Verified on a fresh local database (see `flyway-migration`, "Verify"), or reported as not run.

### H. Config and secrets
- [ ] No literal secrets in code. No literal fallback after the colon in `${...}` for a secret.
- [ ] No new fail-open defaults. No wider actuator or Swagger exposure. No SQL parameter logging
      turned on.
- [ ] `spring.flyway.*` and `ddl-auto` are code-side, non-secret config and fine to change.
      Secret values in `application*.yml` are DevOps's and stay untouched.

### I. Errors, logging and personal data (FCA, GDPR)
- [ ] Typed exceptions from services (`ResourceNotFoundException`, `DuplicateResourceException`,
      `InvalidDataException`, `UnauthorizedException`). Nothing swallowed.
- [ ] Clients never see stack traces, server paths or internal messages.
- [ ] No personal data, financial data, OTPs or tokens in logs (log ids).

### J. API contract
- [ ] DTOs only, never entities. Separate request and response DTOs. `PageResponseDto` for lists,
      201 on create.
- [ ] `@Tag` present. The module's API doc under `docs/` updated (`api-contract`) if a shape or a
      route changed.

### K. Tests
- [ ] New behaviour has tests. Database claims (constraints, search, sequences) are proven on the
      real local PostgreSQL, not by mocks. Access rules are proven by a security-rules test.
- [ ] Bug fixes have a regression test.
- [ ] The module boundary test exists and passes.

## Phase 4: Verify Every Finding

For each candidate:
- Read the surrounding code. Is it already handled elsewhere in the change?
- Trace the callers: `grep -rn "methodName(" src/main`.
- Look for a test that covers it.
- Try to disprove it. Keep it only if it survives.
- Write a concrete failure scenario: which input, which state, what goes wrong.
- Can't confirm it? List it under "Needs verification", not as a finding.

## Phase 5: Audit Yourself Before Concluding

1. List every changed file and whether you read it fully or only its diff.
2. For each checklist section (A to K): issues found, clean, or not applicable.
3. List what you couldn't verify, and why.
4. Only then write the report.

## Report Format

Lead with one line: how many findings at each severity, or "no significant issues found".

Then, most severe first:

```
### [Critical] Public route deletes applicant records
**File:** src/main/java/com/wealthmax/common/security/SecurityConfiguration.java:59
**Problem:** the /api/v1/applicant/** wildcard is permitAll and the new DELETE route lands on it.
**Failure scenario:** an anonymous caller sends DELETE /api/v1/applicant/delete/5 and removes an
applicant's record and personal data.
**Evidence:** no rule above the wildcard covers the route; no security-rules test.
**Fix:** a rule above the wildcard requiring ADMIN, and a test that a call without a token is 403.
```

Severity guide:
- **Critical:** unauthenticated read or write of personal data, auth bypass, a committed secret
  that is still live, data loss, a migration that breaks existing data.
- **High:** a route open to the wrong role, personal data in logs, unvalidated external input,
  partial writes, fail-open config, a module boundary breach that exposes another module's data.
- **Medium:** N+1 or missing pagination on a list, wrong status code, missing test for new
  behaviour, a migration without locking care on a big table.
- **Low:** small inconsistencies. Skip pure style and formatting.

Finish with **Checked and clean** (sections with nothing found) and **Needs verification**.
Don't invent issues to fill the report.
