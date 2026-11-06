# WM Admin Portal BE — Project Brain

## Communication Style

Write plainly. The reader should not have to re-read a sentence to parse it. Applies everywhere:
chat replies, commit messages, PR descriptions, plan files, code comments.

- **One idea per sentence.** Split anything longer than ~25 words.
- **Say the point first, then explain.** Never make the reader hold three facts before the point lands.
- **No em-dashes.** Use a full stop and two sentences, or a comma/parenthesis, instead.
- **Plain words over abstractions.** Define jargon on first use, or don't use it.
- **Show the consequence, not the mechanism**, unless the mechanism was asked for.
- **Comments explain WHY, not WHAT.** Lead with the warning ("don't remove this: X breaks"), not
  the history. If removing a comment wouldn't confuse a future reader, don't write it.

## What This Repository Is

The **revamp** of the Wealthmax admin portal back end: the API behind the admin portal and the
public website forms (careers, leads, referrals, newsletter). It replaces the old repository,
`Wealthmax-Admin-Portal-BE` (package `com.wealthmax.adminPortal`), which keeps running in
production with safety fixes only until the cutover.

- Plan and decisions: `docs/revamp-audit-report.md`. Section 6.1 is the target layout, 6.2 the
  security model, 6.5 the phases, 6.6 the ADR register.
- What the old application does, endpoint by endpoint: `docs/admin-portal-module-documentation.md`.
- Delivery approach (ADR-009, recommended, still Proposed): new repository, new empty database,
  one rehearsed cutover. Each module brings a data-migration script from the old database.
- **Port behaviour, never code.** Nothing is imported from `com.wealthmax.adminPortal`, and the old
  flat packages are never copied.

## Stack
Java 21, Spring Boot 4.1.2 (Spring Framework 7, Hibernate 7.4, Flyway 12, Jackson 3, Spring
Security 7), PostgreSQL, Maven
Architecture: Modular Monolith (NOT microservices), **single tenant** (one organisation)
ADR-008: the newest Boot line in open-source support, chosen 2026-11-05 (4.1.2). Boot 4.0's own
support ends 2026-12-31, so check spring.io before each upgrade and follow the `dependency-upgrade`
skill.

## Maven Commands
```bash
mvn compile                                   # compile check
mvn test                                      # all tests (local PostgreSQL must be running for database tests)
mvn test -Dtest=ClassName                     # one test class
mvn test -Dtest=ModuleBoundaryArchitectureTest   # module boundary rules (ArchUnit), no database needed
mvn test -DexcludedGroups=database            # everything except database tests: what CI runs (ADR-013)
mvn verify                                    # compile + test + package
mvn dependency:analyze                        # check for unused/missing deps
mvn clean install                             # full rebuild
```
This is a single-module build: there's no `-pl <module>` scoping flag to use.

Database tests run on a PostgreSQL on your own machine (ADR-007). They are tagged `database` (the
`@PostgresDataJpaTest` annotation and `@SpringBootTest` classes add `@Tag("database")`), and CI skips
that tag. One-time set-up: `CREATE DATABASE wm_admin_portal_test;` on a local server. If it wants a
password, set `WM_TEST_DB_USER` and `WM_TEST_DB_PASSWORD` in your own environment. A guard refuses
any non-local database. On Windows, a running copy of the app locks `target/wmadminportalbe.jar` and
makes `mvn clean` fail: stop it first.

## Module Structure (Target Package Layout)
```
com.wealthmax/
├── WmAdminPortalApplication.java
├── common/                          # Shared kernel. Depends on no feature module.
│   ├── audit/                       # Append-only audit log (admin mutations, PII reads and deletes)
│   ├── config/                      # Jackson, CORS, OpenAPI, JPA auditing, Clock, @ConfigurationProperties
│   ├── constant/                    # Application constants
│   ├── dto/                         # PageResponseDto
│   ├── entity/                      # BaseEntity, AuditableEntity
│   ├── exception/                   # Shared exceptions + exception advice
│   ├── logging/                     # traceId / MDC filter
│   ├── security/                    # Filter chains, JWT validation, UserPrincipal, method security
│   ├── storage/                     # FileStorage + Azure Blob (SAS)
│   └── util/
├── identity/                        # Users, login (password + OTP challenge), tokens, roles, password reset
├── careers/                         # Jobs + applications (region UK | IN as an attribute, ADR-003)
├── leads/                           # Website leads, adviser, referral, excel data
├── content/                         # Newsletter, subscriber, author, page header, team, home stats, departments
├── reporting/                       # Dashboards, Excel export (read-only, through service interfaces)
└── notification/                    # Outbox, after-commit sender, Mailgun client, email templates
```
Today only `WmAdminPortalApplication` and an empty `common/` exist. Modules are added one at a
time (Phase 3). Inside every module:
```
<module>/
├── controller/       @RestController only
├── dto/request/      validated request DTOs, no server-owned fields
├── dto/response/     response DTOs
├── entity/
├── mapper/
├── repository/       (+ specification/ for search)
├── service/          interfaces: the module's public API
│   └── impl/
└── event/            optional
```
Another module's `entity`, `repository`, `mapper` and `service.impl` packages are off limits;
its service interfaces, DTOs, enums and events aren't. `ModuleBoundaryArchitectureTest` enforces
this. See the `modular-monolith` skill.

## Security Model (target: `docs/revamp-audit-report.md` 6.2)
- **Two filter chains:** public routes are an explicit allow-list (login and OTP, website form
  submits, published content reads); admin routes are `authenticated()`; the fallback is
  `denyAll()`.
- **Method security:** `@EnableMethodSecurity`, and `@PreAuthorize` on every admin use case. A URL
  rule alone lets any signed-in user through.
- **Access levels per route:** Public, Authenticated or Admin. A public route needs a one-line
  reason and must not return or delete personal data.
- **No public signup.** Accounts are provisioned by an admin.
- **Deferred by team decision:** rate limiting (Bucket4j) and CAPTCHA. The per-challenge OTP
  attempt cap is the brute-force defence until then.
- JSON 401 entry point and JSON 403 handler. Swagger and actuator details restricted outside
  `local`.

## Planning

**Every non-trivial task — new feature, API change, refactor, schema change, or anything touching
3+ files — gets a written plan before any code.** This project has **3 developers** (Ashish,
Sarthak, Sishu), so plans and patch summaries live per-developer, not in one shared flat folder.

- **Folder:** `plans/<DevName>-plan/` — e.g. `plans/Sarthak-plan/`. Both the pre-implementation plan
  and the post-implementation patch summary for a piece of work go in the same developer's folder.
- **Determine `<DevName>`:** read `currentUser` from `.claude/settings.local.json` (this file is
  gitignored and per-machine — each developer sets their own `currentUser` once). If it's absent,
  fall back to `git config user.name`, capitalized.
- **Plan filename:** `YYYY-MM-DD-<short-kebab-name>.md`. The date must be in the filename, not
  buried in the body — several skills filter plans by filename date.
- **Plan header:** `**Author:**`, `**Date:**`, `**Time:**`, `**Goal:**`, `**Module:**`, then
  Context, Files to Modify, Steps, Verification. See the `writing-plans` skill for the full
  template — this section states *where*, that skill states *how*.
- **Patch summary filename:** `patch-{N}{letter}-{feature-name}-summary.md` (one letter per phase,
  for example `patch-3a-...`), same folder, written after implementation. See Development
  Workflow below.
- **Implementing a plan:** follow the `executing-plans` skill (phase gates, the plan as the progress
  record, a patch summary per phase).

## Non-Negotiable Rules
1. NO cross-module access to another module's entities, repositories, mappers or service
   implementations. Use its service interfaces, DTOs, enums and events, and refer to its data by ID
   (`ModuleBoundaryArchitectureTest` enforces this)
2. NOTHING tenant-shaped: no `tenant_id` column, no tenant context, no tenant filter. There is one
   organisation
3. NOTHING imported from the old repository (`com.wealthmax.adminPortal`). Port the behaviour
4. Flyway owns the schema. `ddl-auto` is `validate` in every profile; every change is a migration
5. Every route has an explicit access level, and every admin use case has `@PreAuthorize`
6. Azure Blob: private container; short-lived (<1hr) read SAS for CVs and other personal files
7. Monetary values: BigDecimal ONLY — never double or float
8. Constructor injection — never @Autowired field injection
9. DTOs for all API boundaries — never expose @Entity classes directly
10. Request DTOs never carry `id`, roles or audit fields (a client-set id can overwrite a row)
11. Email and other side effects run after commit (the `notification` outbox), never inside the
    transaction
12. No personal data (names, emails, phones, CTC, CVs), OTPs or tokens in logs
13. Secret **values** in `application*.yml` belong to DevOps: never edit, copy or quote them. Code
    that reads a secret is ours to improve

## Primary Key Strategy
- **BIGSERIAL (Long)** with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- **NOT UUID** — all entities use Long IDs (database auto-increment)
- Database column: `id BIGSERIAL PRIMARY KEY`
- Ordered business numbers (for example `WFA-IN-0001` job ids) come from a database sequence

## Entity Base Classes (planned in `common.entity`)
```java
@MappedSuperclass
BaseEntity
├── id: Long (IDENTITY generation)
├── createdAt: Instant (@CreatedDate)
└── updatedAt: Instant (@LastModifiedDate)

@MappedSuperclass
AuditableEntity extends BaseEntity
├── createdBy: Long (@CreatedBy, stores user ID)
└── updatedBy: Long (@LastModifiedBy, stores user ID)
```
Entities are classes, never records. Columns match the Flyway migration exactly, or `validate`
stops startup. See the `coding-standards` skill for the full entity, DTO, mapper and exception
conventions.

## Layered Architecture Per Module
| Layer | Responsibility | Annotations |
|-------|----------------|-------------|
| **Controller** | Routing, input validation, HTTP concerns | @RestController, @Valid, @Tag, @Operation |
| **Service** | Interface = the module's public API | (interface) |
| **Service impl** | ALL business logic, transaction boundaries, method security | @Service, @Transactional, @PreAuthorize |
| **Repository** | Data access only, no business logic | extends JpaRepository |
| **DTO** | API boundaries (request/response packages) | @Schema, validation annotations on requests |
| **Entity** | JPA entities owned by this module | @Entity |
| **Mapper** | DTO ↔ Entity conversion | one style per module (see `coding-standards`) |

**Transaction Guidelines:**
- Write operations: `@Transactional` (default propagation)
- Read operations: `@Transactional(readOnly = true)`
- Never call Mailgun or Azure Blob inside a transaction

## Coding Standards
- **OOP + SOLID** — apply where they reduce complexity, not everywhere
- **Self-documenting names** — minimal comments, only when intent isn't obvious
- **No over-engineering** — build only what's needed now
- **Pagination** on ALL list endpoints — `PageResponseDto`, no unbounded queries
- **FetchType.LAZY** on all @ManyToOne relationships (avoid N+1 queries)
- **@EqualsAndHashCode.Exclude** and **@ToString.Exclude** on ALL JPA relationships
- **@MockitoBean** in tests, never `@MockBean` (deprecated since Boot 3.4)

**Anti-Patterns to Avoid:**
- ❌ @Autowired field injection → ✅ Constructor injection
- ❌ Exposing @Entity in controllers → ✅ Use DTOs
- ❌ Business logic in controllers or repositories → ✅ Keep in services
- ❌ A JPA relationship to another module's entity → ✅ Store its Long id
- ❌ Unbounded queries → ✅ Always paginate list endpoints

See the `coding-standards` skill for the full reference. This section is the summary, that skill is
the detail.

## Development Workflow

**Two checks run automatically**, each in about a second. After Claude edits a file,
`.claude/hooks/post-edit.sh` warns about what the changed lines break. Before each commit,
`.claude/hooks/pre-commit.sh` blocks secrets and edits to migrations already on Dev or Staging, and
warns on the rest. Neither compiles or tests anything, so they don't replace `mvn compile`,
`mvn test` or a review.

Rule IDs (in `.claude/hooks/wealthmax-checks.sh`, tested by `bash .claude/hooks/test-hooks.sh`):

| ID | Checks | Effect |
|---|---|---|
| J1 | import from the old repository (`com.wealthmax.adminPortal`) | warn |
| J2 | import of another module's entity/repository/mapper/service.impl, or common importing a module | warn |
| J5 | money or percentages as `double`/`float` | warn |
| J6 | `@Autowired` field injection | warn |
| J7 | `id`, role or audit field on a request DTO | warn |
| S1 | editing, deleting or renaming a committed migration | block if on Dev or Staging |
| S2 | migration conventions (`IF NOT EXISTS`, `TIMESTAMPTZ`, no UUID keys, destructive SQL) | warn |
| C1 | a secret with a literal value or fallback in `application*.yml` / `.properties` | block |
| C2 | private keys, Azure account keys, Mailgun keys anywhere | block |
| C3 | hardcoded secrets in Java | block |
| C4 | a staged `.env` file | block |
| C5 | `ddl-auto` other than `validate` | warn |

One-time git hook set-up per clone:
```bash
printf '%s\n' '#!/bin/sh' 'exec bash "$(git rev-parse --show-toplevel)/.claude/hooks/pre-commit.sh"' > .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

**After Every Patch:**
- **Create patch summary** (automatic): `plans/<DevName>-plan/patch-{N}{letter}-{feature}-summary.md`,
  plus an entry in that folder's `PATCH_INDEX.md`
- **Run /patch-review** or the `review-branch` skill (user decides when)
- **Commit changes** (user controls)

**Patch Documentation (Automatic):**
- **Location**: `plans/<DevName>-plan/patch-{number}{letter}-{feature}-summary.md`. See Planning
  above for how `<DevName>` is resolved
- **When**: After EVERY completed patch (Claude creates automatically)
- **Purpose**: Historical record of what changed, why, and how to test
- **Contents**: Problem, solution, files changed, testing scenarios, limitations, follow-up tasks
- **Verification results** quote what actually ran. "Not run" plus the reason is honest; a guessed
  pass is not (see `verification-before-completion`)

## Database Conventions
- **Built from scratch:** a new, empty PostgreSQL database. `spring.jpa.hibernate.ddl-auto: validate`,
  `spring.flyway.baseline-on-migrate: false`, `spring.flyway.locations: classpath:db/migration`.
  No baseline from the old database
- **Table/column names**: snake_case. Each table has exactly one owning module
- **Flyway migrations**: `V{version}__{module}_{description}.sql`. Check
  `src/main/resources/db/migration/` for the current highest version before creating one — don't
  trust a number written in this file
- **Indexes**: on the real query patterns (status, created_at, lookup keys)
- **Parameterized queries** only — no raw SQL string concatenation
- **Migration idempotency**: Use `IF NOT EXISTS` / `IF EXISTS` clauses
- **Never edit an applied migration.** Flyway checksums fail on every database that already ran it.
  Write a new migration instead
- **Old data** arrives through each module's data-migration script at the cutover (ADR-009), never
  through a Flyway migration
- Conventions (BIGSERIAL keys, `TIMESTAMPTZ`, `BIGINT` audit columns, money `NUMERIC(14,2)`) and
  locking rules live in the `flyway-migration` skill

## External Integrations
- **Azure Blob Storage**: `common.storage`; private container, server-generated names, short-lived SAS
- **Mailgun**: the `notification` module, through the outbox, after commit
- **Swagger/OpenAPI**: springdoc 3.2 (the Boot 4 line); every endpoint documented with @Tag and @Operation

## Configuration (application.yml)
Profiles: `local` (default, gitignored), `staging` (gitignored), `test` (`src/test/resources`).
`application.yml.example` is the template for a new `application-local.yml`.

```yaml
jwt:
  secret: ...                 # DevOps-owned value
  issuer: WM-Admin-Portal-BE
  access-token-expiry: ...
  refresh-token-expiry: ...

app:
  frontend: { base-url, reset-password-path }
  security:
    token-pepper: ...         # DevOps-owned value (refresh and password reset token hashing)
```
Key names match the real `application.yml`; values are never written here. Known gap: some secret
keys in the tracked `application.yml` still have literal fallbacks after the colon, so the C1
pre-commit check blocks the first commit until DevOps replaces them with `${VARIABLE}` and no
fallback. Never add a new fallback for a secret.

## Current Phase
Revamp **Phase 1: foundations is complete** (marked by Sarthak on 2026-11-06). The master plan is `docs/revamp-implementation-plan.md` in the
old repository (`Wealthmax-Admin-Portal-BE`), and the detailed Phase 1 plan is
`plans/Sarthak-plan/2026-11-05-phase-1-foundations.md` with its progress table. Phase 1 built source
control, Boot 4.1.2, the ADR files, the `common` kernel, `ModuleBoundaryArchitectureTest`, the test
infrastructure (ADR-007), CI and the behaviour inventory. Then Phase 2 (`common.security`,
`notification`, storage), Phase 3 (identity) and the modules one at a time (careers, leads, content,
reporting).

For what's actually shipped, in progress, or not started: check `plans/*/patch-*-summary.md`
(the real per-change record) rather than trusting a status list here.

## Security Best Practices
- Store password hashes (BCrypt) — never plaintext
- Store refresh token and password reset token hashes — never raw tokens
- Short-lived access tokens + rotating refresh tokens; logout revokes the family
- OTP: `SecureRandom`, 6 digits, hashed, bound to the user and challenge, 5-minute expiry, max 5
  attempts, single use, never logged
- Same response whether or not an email exists (login, forgot password)
- Public-form values HTML-escaped in every email template
- Uploads checked for type, size and magic bytes; stored under a server-generated name

## Common Troubleshooting
**Startup fails with "Schema-validation: missing table/column":**
- Cause: an entity without a matching Flyway migration (`ddl-auto: validate`)
- Fix: write the migration (`/add-migration`). Never switch `ddl-auto` to `update`

**Startup fails with "Unsupported Database: PostgreSQL":**
- Cause: `flyway-database-postgresql` missing from the pom
- Fix: keep both `flyway-core` and `flyway-database-postgresql`

**Flyway refuses a database that already has tables:**
- Cause: pointing the app at the old database. `baseline-on-migrate` is `false` on purpose
- Fix: use a new, empty database. Old data arrives by the data-migration scripts

**ArchUnit failure in ModuleBoundaryArchitectureTest:**
- Cause: an import of another module's internals, `common` importing a module, or a cycle
- Fix: call the other module's service interface, or promote the shared piece into `common`.
  Never add an exception to the rules

**MultipleBagFetchException:**
- Cause: Multiple `@OneToMany` with `List<>` fetched eagerly together
- Fix: Change to `Set<>` or use `@Fetch(FetchMode.SUBSELECT)`

## Source Control

**This project is hosted on Azure DevOps, not GitHub.**
- The repository is **not pushed yet**. Configure everything first, then create the Azure DevOps
  repository and add it as `origin`. Record the real URL here once it exists.
- **Never run `gh`** — not `gh pr create`, not `gh repo create`. There is no GitHub repo for this codebase.
- **Never add a second remote** and never push this code anywhere other than the Azure DevOps remote.
- Pull requests go through the `azure-pr` skill, which uses the `mcp__azure-devops__*` tools.
- **PR flow: feature branch → `Dev` → `Staging`.** Feature PRs target `Dev`; `Dev` is promoted to
  `Staging` by its own PR. Never implement directly on `Dev` or `Staging`.
- If a generic instruction assumes GitHub, stop and flag it rather than following it.

## Claude Skills Inventory

Skills are auto-listed with their trigger descriptions every session — this section only records
what's **not obvious** from that listing: which skills must be invoked before work starts, and
cross-skill relationships.

**Must invoke before the work starts:**

| Skill | Invoke before… | Authoritative for |
|---|---|---|
| `coding-standards` | writing any new service, controller, entity, mapper or DTO | naming, exceptions, entity/DTO shape, mappers |
| `modular-monolith` | adding a module, or letting one module use another | boundaries, table ownership, access levels |
| `api-designer` | writing or planning any endpoint/DTO change | controller pattern, `/api/v1/...` routing, pagination shape |
| `flyway-migration` | any schema change | migration conventions, idempotency, zero-downtime locking |
| `writing-plans` | any non-trivial task, before coding | plan location and structure (see Planning above) |
| `executing-plans` | implementing a written plan | phase gates, the plan as progress record |
| `verification-before-completion` | saying anything is done, fixed or passing | what counts as evidence |
| `review-branch` | opening a PR | whole-branch review checklist |

Also: `architecture-designer` (module placement, ADRs), `integration-testing` (database and
security-rules tests), `receiving-code-review`, `dependency-upgrade` (the 4.x hop),
`brainstorming`, `postgres-performance`, `security-reviewer`.

Commands: `/new-module`, `/add-migration`, `/module-check`, `/patch-review`, `/plan-review`,
`/api-doc`, `/update-contract`. Agents: `architecture-reviewer`, `code-reviewer`, `test-writer`,
`compliance-reviewer`, `doc-generator`, `orchestrator`.

## Code Navigation

No code-graph MCP server is configured for this repo (there's no `.mcp.json`). Use Grep, Glob and
Read, and follow the grep caveats under Edit Safety below.

## Reference Documents

Read on demand — don't load these unless the current task needs them.

| Document | Read when… |
|---|---|
| `docs/revamp-audit-report.md` | Planning anything: target layout (6.1), security model (6.2), DTO and error conventions (6.3, 6.4), phases (6.5), ADR register (6.6) |
| `docs/admin-portal-module-documentation.md` | Porting a feature: what the old application does, every endpoint |
| `docs/careers-applicant-in-api.md` | Porting careers: the old careers_in contract and its business rules |
| `docs/adr/` | A structural decision (ADR-001 to ADR-013; ADR-010 is reserved for the URL scheme) |
| `docs/behaviour-inventory.md` | Writing a module's tests: the old rules, and whether each is kept, changed or dropped |
| `docs/diagnosis/INDEX.md` | **First stop for any bug**, once it exists |
| `plans/*/patch-*-summary.md` | What actually shipped, and why — the real project-status record |

---

## Agent Behaviour — Production Directives

These directives override the default bias toward minimal, fast output.
Violating them produces broken, incomplete, or unsafe code.

### Before Starting Any Work

**Step 0: Clean Before Building**
Before any structural refactor on a file over 300 lines, first remove dead code —
unused imports, dead methods, debug log statements. Commit this cleanup
separately before the real work begins.

**Phased Execution — Max 5 Files Per Phase**
Never attempt multi-file changes in a single response.
Break work into explicit phases. Complete Phase 1, run `mvn compile`, confirm
it passes, then wait for approval before Phase 2.
Each phase touches no more than 5 files.
The user may explicitly ask to run several phases back to back. Each phase still keeps its 5-file
limit, its own verification and its own patch summary.

**Plan and Build Are Separate Steps**
When asked to plan — output only the plan. No code until explicitly told to proceed.
When given a written plan — follow it exactly.
If instructions are vague (e.g. "add the careers module") — outline what you'd
build and where it goes. Get approval first.

### Context Management (Critical for Long Sessions)

**Context Decay — Re-Read Before Every Edit**
After 10+ messages in a conversation, re-read any file before editing it.
Do not trust memory of file contents. Auto-compaction silently destroys context
mid-session. Editing against stale state produces broken output that is hard to debug.

**File Read Budget**
The file read tool caps at 2,000 lines. For any file over 500 lines, use offset and limit
parameters to read in chunks. Never assume a single read captured the complete file.

**Tool Result Blindness**
Large `mvn`/grep/bash output can get truncated to a short preview by some tool configurations.
If grep returns suspiciously few results for a method that should have many callers — re-run with a
narrower scope. State when truncation is suspected.

**Sub-Agent Swarming for Large Refactors**
For tasks touching more than 5 independent files, launch parallel sub-agents
(5-8 files per agent). One agent processing 20 files sequentially guarantees
context decay.

### Edit Safety

**Re-Read Before Editing**
Re-read a file before editing it, especially later in a long conversation — don't trust memory of
its contents. You do **not** need to re-read it again afterward to confirm the edit applied: the
edit tool itself fails outright if the target text doesn't match, so a call that succeeds is already
confirmation.

**No Semantic Search Fallback — Grep Is Text Only**
There's no code graph here, so Grep is text-only: it has no understanding of renames, overloads,
or indirect references. When renaming or changing any method, class, field, or constant, search
separately for:
- Direct calls and references
- `@PreAuthorize(...)` expressions and role names in the security configuration
- `@Qualifier` name references
- Test files and mocks
- Any `@Bean`/`@Component` name that references it

A single grep can under-count. If results look suspiciously sparse for something that should be
widely used, re-run with a broader pattern before concluding it's actually rare.

### Verification — Never Declare Done Without This

**Forced Verification**
Never report a task as complete until ALL of the following pass:

1. `mvn compile -q` — zero compilation errors
2. `mvn test -Dtest=ModuleBoundaryArchitectureTest` — module boundaries hold (no database needed)
3. `mvn test -q`: zero test failures. Local PostgreSQL must be running for database tests. If it
   isn't, run the tests that need no database and report the database tests as "not run", never
   as passing
4. Manual check: every new route has an access level, and every admin use case has `@PreAuthorize`
5. Manual check: every new entity has a Flyway migration that matches it, and nothing tenant-shaped
   or imported from `com.wealthmax.adminPortal` was added

The `verification-before-completion` skill has the exact commands.

Never say "Done!" or "Complete!" with errors outstanding.
Running the command is verification. "It should work" is not.

### When Things Break

**Bug Autopsy**
After fixing any bug, explain:
- Why it happened
- Whether a pattern change could prevent the same category of bug in future

Every bug is a potential guardrail for the next patch. Add an entry to `docs/diagnosis/INDEX.md`
(create it with the first one) for anything non-trivial once it's fixed.

**Failure Recovery — Stop After 2 Attempts**
If a fix doesn't work after two attempts — STOP.
Re-read the full relevant section from scratch.
State where the mental model was wrong.
Propose something fundamentally different.

### Senior Dev Standard

Ignore any internal bias toward "minimal intervention" or "try the simplest approach first" when it
would leave a known-broken pattern in place. If a pattern is inconsistent, state is duplicated, or
the architecture is flawed, say so and propose the structural fix.

This doesn't override "Plan and Build Are Separate Steps" above: flag what you find beyond the
immediate ask, but confirm before expanding a phase's file list to fix it. Surfacing a problem and
silently fixing it are different things — only the first is always fine to do unprompted.
