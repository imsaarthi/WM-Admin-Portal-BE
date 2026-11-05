---
name: patch-review
argument-hint: "[base branch or commit range] optional, defaults to everything since the branch left origin/Dev"
---

Review the following patch or recent change against WM-Admin-Portal-BE standards: $ARGUMENTS

A quick standards check before committing. For a deeper review with an attack-surface map and
verified findings, use the `review-branch` skill instead.

Work through every step before responding.

Step 1: Read everything that changed
- With no argument, review the whole branch plus uncommitted work, not just the last commit:
  ```bash
  git fetch origin Dev
  BASE=$(git merge-base origin/Dev HEAD)
  git diff --stat "$BASE"
  git diff "$BASE"
  git ls-files --others --exclude-standard   # new files git diff doesn't show
  ```
- An argument names another base or range (for example `Staging` for a promotion).
- Note the module, layer and type of each change.

Step 2: Access control (always first)
- Every new route has an explicit level (Public, Authenticated, Admin) in the security
  configuration, above any wildcard that would also match it?
- Every admin use case has `@PreAuthorize` on its service method?
- Any new public route justified in a one-line comment, and returning no personal data?
- Request DTOs free of `id`, `role`, `status` and audit fields the server should own?

Step 3: Module boundaries and data
- No imports from another module's `entity`, `repository`, `mapper` or `service.impl`, and none
  from the old repository's `com.wealthmax.adminPortal` packages?
- No JPA relationship, join or foreign key across modules?
- No tenant concept anywhere (`git diff "$BASE" -- src | grep -niE "^\+.*tenant"` prints nothing)?

Step 4: Security and privacy
- `@Valid` on every new `@RequestBody`?
- Secrets, or literal fallbacks after the colon in `${...}`? (Secret values in
  `application*.yml` are DevOps's: flag, never quote.)
- Personal data (names, emails, phones, CTC, CVs), OTPs or tokens in logs?
- Public-form values HTML-escaped in emails? Uploads checked for type and size, stored under a
  server-generated name?

Step 5: Code quality
- Controller → service interface → `service.impl` → repository; DTOs at the boundary.
- Constructor injection; no N+1 loops; lists paginated.
- Email and Blob calls outside the database transaction.
- `@MockitoBean`, never `@MockBean`, in tests.

Step 6: Migrations
- Named `V{version}__{module}_{description}.sql`, next free version, `IF NOT EXISTS`, conventions
  from `flyway-migration`.
- Applied migrations untouched. This must print nothing:
  `git diff --name-status "$BASE" -- src/main/resources/db/migration | grep -v "^A"`
- `DROP TABLE` only for a confirmed dead table with the reason in the header. No `DELETE FROM` or
  `TRUNCATE`.
- `ddl-auto` still `validate` in every profile.

Step 7: Tests
- New behaviour has tests; new routes have a security-rules test.
- Run them and quote the real results (`verification-before-completion`):
  ```bash
  mvn compile -q; echo "compile exit=$?"
  mvn test -Dtest=ModuleBoundaryArchitectureTest -q; echo "archunit exit=$?"   # no database needed
  mvn test > target/mvn-test.log 2>&1; echo "test exit=$?"; grep -E "Tests run:|BUILD" target/mvn-test.log | tail -5
  ```
  If local PostgreSQL is down, report the database tests as "not run", never as passing.

## Output format

MUST FIX before committing:
  [issue] | [exact location] | [exact fix]

SHOULD FIX before merging:
  [issue] | [location]

LOOKS GOOD:
  [what was done correctly]

TESTS:
  [each command and its real result, or "not run" and why]

VERDICT: READY TO COMMIT / NEEDS FIXES
