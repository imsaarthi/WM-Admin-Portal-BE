---
name: systematic-debugging
description: Use when encountering any bug, test failure, or unexpected behavior in WM-Admin-Portal-BE. Always before proposing fixes.
user-invocable: true
---

# Systematic Debugging: WM-Admin-Portal-BE

Based on `systematic-debugging` from obra/superpowers (MIT License, Copyright (c) 2025 Jesse
Vincent), adapted for this project.

## The Iron Law

```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

Random fixes waste time and create new bugs. This application holds applicant, lead and
subscriber personal data, and one deployment serves the whole organisation. A "quick fix" to an
access rule can open a hole to every record. Never guess.

---

## When to Use This

Use for ANY technical issue:
- Test failures (unit, controller, security-rules, database)
- A route that answers 403, or that is open when it shouldn't be
- JWT or OTP errors
- A Flyway migration or schema-validation failure
- A module boundary test failure
- `NullPointerException`, especially a service that is `null`
- N+1 query problems, slow list endpoints
- `@Transactional` not rolling back
- A Mailgun or Azure Blob failure that leaves half a write behind

**Use ESPECIALLY when:**
- You've already tried one fix and it didn't work
- The fix "seems obvious" (it usually isn't)
- The bug involves `SecurityConfiguration` or the JWT filter
- You're getting 403 when you expect 200 or vice versa

---

## The Four Phases

Complete each phase before moving to the next.

---

### Phase 1: Root Cause Investigation

**BEFORE attempting ANY fix, check whether it is already diagnosed.** Look in
`docs/admin-portal-module-documentation.md` (known issues) and `docs/revamp-audit-report.md`
(sections 2 and 5: findings with evidence and status). A matching finding means the cause is
already known: open that entry instead of re-deriving it.

#### 1. Read the full error, completely

Don't skim. Read: exception type and message, the full stack trace (the first line of **your**
code in the stack, not Spring internals), which test class and method, which line number.

```
Look for: com.wealthmax.*** in the stack trace
Skip:     org.springframework.***, org.hibernate.***, java.***
```

#### 2. Reproduce consistently

```bash
mvn test -Dtest=ClassName#methodName

# Does it fail every time?
# If flaky: look for shared state: a test that leaves rows behind (database tests), static
# mutable state, time-dependent logic (the applicant cooling period), or test order.
```

#### 3. Check recent changes

```bash
git log --oneline -10
BASE=$(git merge-base origin/Dev HEAD)          # use the exact base branch name: git branch -r
git diff --stat "$BASE"
git diff "$BASE" -- src/main/java/com/wealthmax/<module>/
```

#### 4. Gather evidence for multi-component bugs

Most bugs cross component boundaries. Add diagnostics before fixing. **Never log personal data
(names, emails, phone numbers, CTC), OTPs or tokens, even temporarily.** Log ids and counts.

**Access bug (wrong 403 or wrong open route):**
```java
// Temporarily, in a test or JwtAuthenticationFilter. Ids and booleans only
log.debug("request {} {}", request.getMethod(), request.getRequestURI());
log.debug("authenticated: {}", SecurityContextHolder.getContext().getAuthentication() != null);
```
Then walk `SecurityConfiguration` top to bottom and find the **first** rule that matches the method
and path. That rule, not the one you expected, decided the answer.

**Hibernate/JPA bug:** add the SQL loggers to your **local** config temporarily:
`logging.level.org.hibernate.SQL=DEBUG` and `logging.level.org.hibernate.orm.jdbc.bind=TRACE`.
Read the actual SQL. How many statements did one request run? A handful for one screen is normal.
Dozens is an N+1. Does a search use the column and operator you expected?

**Flyway migration bug:** read the startup log for `Flyway` and `Migrating schema` lines, and query
`flyway_schema_history` on the **local** database. See the Flyway pattern below.

#### 5. Trace data flow backward

Find where the bad value originates, not where it causes the error.

```
Error: NullPointerException in HomePageCountController
  -> the service field is null
  -> how was the controller constructed? Two constructors, and Spring used the no-arg one
     (Lombok generated a second constructor)
  -> Check: the constructors on the class, and which one Spring picks
```

Keep tracing UP until you find the SOURCE. Fix at the source.

---

### Phase 2: Pattern Analysis

**Find the pattern before fixing:**

#### 1. Find working examples in the codebase

The first ported module is the reference (until then, the old repository's `careers_in`). Compare a broken class with its equivalent there:

```bash
grep -rn "@Transactional\|TransactionTemplate" src/main/java --include="*.java" -l
grep -rn "@Valid" src/main/java --include="*.java" -l
```

A single `grep` can under-count. If it returns suspiciously few hits for something that should be
common, re-run with a broader pattern.

#### 2. Compare working vs broken

```
Working (careers_in controller):
  Takes a request DTO with @Valid; returns a response DTO; GlobalExceptionHandler maps errors

Broken (a controller ported too literally from the old repository):
  Takes an entity directly, has no @Valid  <- found it: no validation, and a client-set id
                                              is merged over an existing row
```

#### 3. Project-specific comparison checklist

For access bugs:
- [ ] Which `SecurityConfiguration` rule is the first match for this method and path?
- [ ] Is the route covered by an older `permitAll` wildcard placed above the rule you added?
- [ ] `authenticated()` (any role) or `hasAuthority("ADMIN")`? Is the caller's role what you think?
- [ ] No token on a protected route is **403** here (no entry point is configured), not 401.
- [ ] Is it a CORS preflight (`OPTIONS`) with no `Authorization` header?
- [ ] Did an expired or malformed token break a request to a **public** route? The filter doesn't
      catch parse errors (audit SEC-23).

For module boundary bugs:
- [ ] Which import does the boundary test name? Is it a class that should be promoted to `common`,
      or a service-interface call that should replace it?
- [ ] Is someone adding an exception to the ArchUnit rules? That is a smell: fix the dependency instead.

For `@Transactional` bugs:
- [ ] Is `@Transactional` on the service class or method, and is it public?
- [ ] Is the service called through the Spring proxy (not `this.method()`)?
- [ ] Is the exception a `RuntimeException` (only these roll back by default)?
- [ ] Is a Mailgun or Azure Blob call inside the transaction?

---

### Phase 3: Hypothesis and Testing

**Form ONE hypothesis:**

```
"I think the root cause is: [specific thing]
Because: [evidence from Phase 1 and 2]
To test this: I will change [minimal change] and expect [specific result]"
```

**Test minimally, one change at a time:**

```bash
mvn test -Dtest=ClassName#methodName -q

# YES -> hypothesis confirmed -> go to Phase 4
# NO  -> form new hypothesis, do NOT add more fixes
```

**Never:** fix multiple things at once; "try this and see if it helps"; add fixes on top of fixes.

If the hypothesis fails: form a NEW single hypothesis. Return to Phase 2 if needed.

---

### Phase 4: Implementation

**Fix the root cause, not the symptom:**

1. Write a failing test that reproduces the bug. Run it. Confirm it fails. If it doesn't, the test
   doesn't actually reproduce the bug.
2. Implement the single fix: one change, at the root cause location, nothing extra.
3. Verify:
   ```bash
   mvn test -Dtest=ClassName#bugReproductionTest -q   # the reproduction test passes
   mvn test -q                                         # nothing else broke (local PostgreSQL needed)
   ```
   For a database bug, the reproduction test belongs on the real local PostgreSQL
   (`integration-testing`): a mocked repository can't observe a constraint or a query. For an
   access bug, it belongs in a security-rules test.
4. **If the fix doesn't work, STOP.** Count how many fixes you've tried.
   - If < 2 attempts: return to Phase 1. Re-read everything. You missed something.
   - If >= 2 attempts: **question the design and say something fundamentally different is
     needed** rather than trying a third variation of the same fix.

Patterns indicating a design problem, not a local bug:
- Each fix exposes a new gap in a different layer
- The fix requires changing 5+ files across multiple modules
- A rule works for some routes but not others (rule order, or a wildcard you didn't know about)
- The fix needs one module to reach into another's data (a boundary is drawn in the wrong place)

Stop. Describe what you found before attempting more fixes.

---

## Project-Specific Bug Patterns

### Pattern: "403 Forbidden unexpected" (or a route that is open when it shouldn't be)

```
Evidence needed:
  1. The exact method and path of the request
  2. The first SecurityConfiguration rule that matches it
  3. Whether a token was sent, and whether it is valid and unexpired
  4. The user's role (authorities), if a token was sent

Root cause is almost always one of:
  A. Rule order: a wildcard above the narrow rule matched first (first match wins)
  B. No token (or an expired one) on a protected route: Spring answers 403 before the controller
  C. authenticated() where ADMIN was intended, or hasAuthority/hasRole mixed up
     (User.getAuthorities() returns the literal "ADMIN", so hasRole("ADMIN") looks for ROLE_ADMIN)
  D. A CORS preflight (OPTIONS) without an Authorization header
  E. The path isn't what you think: class-level @RequestMapping plus the method path
  F. A test that loads the controller without the real security chain (it can't produce a 403)
```

### Pattern: "NullPointerException on a service or dependency"

```
Evidence needed:
  1. Which field is null?
  2. How was the bean built: constructor injection, or a second constructor?
  3. Is it a test with @InjectMocks and a missing @Mock?

Root cause is almost always one of:
  A. Two constructors on a class (for example Lombok-generated plus an explicit one): Spring may
     pick the no-arg one, leaving final fields null
  B. @InjectMocks with an unmocked constructor dependency
  C. A bean created with `new` instead of through Spring
  D. Self-invocation expecting a proxy
```

### Pattern: "Data not found, or duplicate rows"

```
Evidence needed:
  1. The SQL that ran (SQL logging) and its bound parameters
  2. For duplicates: is the uniqueness rule enforced by a database constraint, or only by an
     existsBy... check?

Root cause is almost always one of:
  A. "Check then insert" with no unique constraint: two concurrent requests both pass the check
  B. A case-sensitive comparison where the rule is case-insensitive (emails)
  C. Normalisation missing: "+91" prefix, spaces, plus-addressing make equal values look different
  D. A client-supplied id merged over an existing row (a legacy create endpoint accepting `id`)
```

### Pattern: "Flyway migration failed"

```
Evidence needed:
  1. The exact error message and failing SQL statement
  2. flyway_schema_history on the LOCAL database (which versions ran, success flags, checksums)
  3. The migration folder: any version clash with another branch?

Root cause is almost always one of:
  A. "Unsupported Database: PostgreSQL": flyway-database-postgresql is missing (Flyway 10 needs it)
  B. "Found non-empty schema(s) ... without schema history table": an existing database was never
     baselined (see the baseline decision in flyway-migration)
  C. "Migration checksum mismatch": an applied migration file was edited. Restore it, and write a
     new migration instead. Run `flyway repair` only if the edit is harmless and every
     environment agrees
  D. "Schema-validation: missing column / wrong column type": an entity changed with no migration
     (ddl-auto=validate caught it), or the migration and entity disagree
  E. Version number collision between two branches
  F. NOT NULL added to a non-empty table with no DEFAULT, or a foreign key to a table that
     doesn't exist yet
  G. A CREATE INDEX CONCURRENTLY in a file with other statements
```

### Pattern: "Database test won't run, or is stopped"

```
Root cause is almost always one of:
  A. Local PostgreSQL isn't running (connection refused): report the tests as not run
  B. LocalDatabaseOnlyInitializer refused the datasource: it isn't localhost. That is the guard
     working. Don't bypass it
  C. TablesMustBeEmptyExtension stopped the run: the module's tables hold rows. Clear your own
     local rows. Never script a delete that could reach a shared database
  D. The schema is behind the entities: with Flyway, a missing migration; before it, ddl-auto
     differences
```

### Pattern: "@Transactional not rolling back"

```
Root cause is almost always one of:
  A. Checked exception thrown (only RuntimeException rolls back by default)
     Fix: @Transactional(rollbackFor = Exception.class)
  B. Self-invocation: this.method() bypasses the Spring proxy
     Fix: restructure so the call goes through the bean
  C. @Transactional on a private method (Spring AOP proxies can't intercept it)
     Fix: make the method public
  D. The exception was caught inside the transaction and swallowed. The transaction is marked
     rollback-only, and the commit fails later with "Transaction silently rolled back"
     Fix: let it propagate, or move the risky call into its own transaction
  E. The work isn't in a transaction at all (no annotation, no TransactionTemplate)
```

### Pattern: "Row saved, but the email failed"

```
Root cause: the email is sent inside, or right after, the write, and a Mailgun failure turns the
request into a 500 with the row already committed. A retry then hits the duplicate rule.
Fix direction: send after commit (TransactionTemplate, or an event handled after commit), and
decide what a failed email means for the user. Don't swallow the error silently.
```

### Pattern: "Module boundary test fails"

```
Root cause is almost always one of:
  A. A new import of another module's entity, repository, mapper or service.impl
  B. A class used by two modules that should have been promoted to common
  C. A controller depending on a repository or entity, or a vendor SDK outside its edge package
Fix: call the other module's service interface with ids and DTOs, or promote the shared class.
Do not add an exception to the rules to make it pass.
```

---

## Red Flags: STOP and Return to Phase 1

If you find yourself thinking:
- "Let me just try changing X"
- "This might fix it"
- "I'll add a null check and see"
- "One more fix attempt"
- "I don't fully understand but this should work"
- "It worked before, let me revert and re-apply"

**ALL of these mean: STOP. Return to Phase 1.**

---

## Quick Reference

| Phase | Key Question | Success Criteria |
|---|---|---|
| 1. Root Cause | What EXACTLY is wrong and WHY? | Can explain root cause in one sentence |
| 2. Pattern | What does working code look like vs broken? | Found the specific difference |
| 3. Hypothesis | What single change will fix the root cause? | Hypothesis confirmed or rejected cleanly |
| 4. Implementation | Fix root cause, verify, test | Bug resolved, tests pass, no regressions |
