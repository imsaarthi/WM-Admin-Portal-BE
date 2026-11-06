# Phase 1: Foundations Implementation Plan

**Author:** Sarthak

**Date:** 2026-11-05

**Time:** 12:05 UTC

**Status:** Complete. Marked by Sarthak on 2026-11-06. See the exit criteria and open item 5.

**Goal:** Put the repository under source control on Spring Boot 4.1.2, with the `common` kernel, the module boundary test, the local-PostgreSQL test guards, CI and the behaviour inventory in place.

**Module:** `common` (kernel) plus build, test support and docs. No feature module.

**Tables owned by this module:** none. One test-only table, `kernel_probe`, exists only in the test migration folder.

**Access level:** no routes are added.

**Migration required:** No production migration. One test-only migration, `src/test/resources/db/test-migration/V9000__kernel_probe.sql`.

**Tech stack:** Java 21, Spring Boot 4.1.2 (Spring Framework 7.0, Hibernate 7.4, Flyway 12.4, Jackson 3.1, Spring Security 7.1, JUnit 6), PostgreSQL, MapStruct 1.6.3, ArchUnit 1.5.1

**Master plan:** `Wealthmax-Admin-Portal-BE/docs/revamp-implementation-plan.md`, Phase 1.

---

## Gate decisions (Sarthak, 2026-11-05)

| Gate | Decision |
| --- | --- |
| D-01 | Move to the newest Boot 4.x now. Newest GA on Maven Central on 2026-11-05: **4.1.2** (4.2 is RC only) |
| D-02 | Local PostgreSQL with guards. Testcontainers is removed from the pom |
| D-04 | Skills' exception names: `ResourceNotFoundException`, `DuplicateResourceException`, `InvalidDataException`, `UnauthorizedException` |
| D-05 | MapStruct in every new module, `unmappedTargetPolicy = ERROR` |
| D-06 | ADR-001, ADR-002 and ADR-009 accepted |
| D-07 | CI from Phase 1, without Testcontainers: `mvn verify` on PRs into `Dev`, database tests excluded |

## Facts checked while planning (2026-11-05)

- D: is not a git repository. Docker is not installed. Java 21.0.12, Maven 3.9.11, PostgreSQL listening on 5432.
- A scratch Maven project on Boot 4.1.2 resolved every dependency below (`BUILD SUCCESS`).
- Managed by Boot 4.1.2: Hibernate 7.4.13, Flyway 12.4.0, Jackson 3.1.7 (Jackson 2.21.7 still managed), Spring Security 7.1.2, JUnit 6.0.3, Mockito 5.23.0, Lombok 1.18.48, PostgreSQL driver 42.7.14, compiler plugin 3.15.0, surefire 3.5.6.
- Boot 4 test annotations: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`, `org.springframework.boot.webmvc.test.autoconfigure.{WebMvcTest, AutoConfigureMockMvc}`, `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- Spring 7 `ResponseEntityExceptionHandler` still has `handleExceptionInternal`, `handleMethodArgumentNotValid`, `handleHandlerMethodValidationException`, `handleNoResourceFoundException`, `handleMaxUploadSizeExceededException`.
- Hibernate 7 `ConstraintViolationException(String, SQLException, String)` exists, with `getSQLState()` and `getConstraintName()`.

## How this plan is written

Each step lists its files (at most 5), the design and the proving command. The exact code is written
during execution, test first, and recorded in the step's patch summary. This is a deliberate
deviation from the `writing-plans` "full code in the plan" rule: Phase 1 is about 45 files, and
writing them twice would double the cost without adding review value. Steps are named after the
master plan's work package plus a digit (`1c1`, `1c2`). Each step ends with a patch summary
`patch-<step>-<name>-summary.md` in this folder and a stop for approval.

---

## 1a Source control

### Step 1a1: Clean leftovers, `git init`, pre-commit hook

**Files:** delete `.github/modernize/java-upgrade/.gitignore`, `.github/modernize/java-upgrade/hooks/scripts/recordToolUse.ps1`, `.github/modernize/java-upgrade/hooks/scripts/recordToolUse.sh` (leftover from an upgrade tool, conflict 6).

- `git init -b Dev`. The first commit lands on `Dev`, because nothing exists to branch from. All later work goes on feature branches.
- Install the pre-commit hook (CLAUDE.md "One-time git hook set-up").
- `bash .claude/hooks/test-hooks.sh`. Expected: all pass.
- `git add -A`, then `bash .claude/hooks/pre-commit.sh`. Expected: **blocked by C1** on `application.yml` until DevOps replaces the literal secrets. Nothing is committed.

### Step 1a2: First commit and push (BLOCKED on DevOps, then needs your go-ahead)

- DevOps replaces the literal secret values (conflict 1). Then `pre-commit.sh` exits 0.
- First commit on `Dev` (message via `commit-message`), only when you ask.
- You or DevOps create the Azure DevOps repository and give me its URL. I add `origin`, push `Dev`, create and push `Staging`. Push only after you confirm.
- Branch policies (PR required into `Dev`, build validation from 1i) are set in Azure DevOps by a repo admin.
- **Files:** `CLAUDE.md` (remote URL in Source Control; Current Phase points at the master plan).

## 1b Runtime line

### Step 1b1: Boot 4.1.2 pom

**Files:** `pom.xml`.

- Parent `3.5.16` to `4.1.2`.
- `spring-boot-starter-web` to `spring-boot-starter-webmvc`.
- `flyway-core` to `spring-boot-starter-flyway` (without it Flyway silently stops running in Boot 4). Keep `flyway-database-postgresql`.
- Test starters: keep `spring-boot-starter-test`; add `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test`; `spring-security-test` becomes `spring-boot-starter-security-test`.
- Remove the three Testcontainers dependencies (D-02).
- `archunit-junit5:1.3.0` to `archunit:1.5.1` (core only). The boundary test uses a plain `@Test`, so the ArchUnit JUnit engine and its JUnit 6 compatibility are not needed.
- springdoc `2.8.17` to `3.2.0`. jjwt `0.12.5` to `0.13.0` (no code uses it yet).
- azure-storage-blob `12.25.0` to `12.37.2` and poi-ooxml `5.2.3` to `5.5.1`: no code uses them yet, and the old POI has known CVEs.
- Remove the `postgresql.version` pin (42.7.12 is older than the managed 42.7.14). MapStruct stays 1.6.3 (1.7 is beta only).
- Compiler plugin: drop the `3.11.0` pin and `source`/`target` (the parent sets `release` 21). Add `-Amapstruct.unmappedTargetPolicy=ERROR` (D-05).

**Verify:** `mvn -B -q compile` and `mvn -B -q test-compile` exit 0. `mvn help:evaluate -Dexpression=project.parent.version -q -DforceStdout` prints `4.1.2`. `mvn dependency:tree` shows no `testcontainers`.
**Prove D-05:** add a throwaway mapper with an unmapped target field, `mvn compile` fails with "Unmapped target property", delete it, compile passes.
**Context load:** moved to 1h4, because it needs the database guard.

## 1c ADR files

Template: `.claude/skills/architecture-designer/references/adr-template.md`. Content from the register in `docs/revamp-audit-report.md` 6.6 plus the gate decisions above.

| Step | Files | Status written |
| --- | --- | --- |
| 1c1 | `docs/adr/README.md` (index), ADR-001 modular monolith single tenant, ADR-002 target layout, ADR-003 careers UK/IN merge, ADR-004 Flyway | 001, 002 Accepted (D-06). 003 Proposed (D-16 open). 004 Proposed (**open question for you**: your Flyway decisions match it; mark Accepted?) |
| 1c2 | ADR-005 ArchUnit boundaries, ADR-006 outbox, ADR-007 test infrastructure, ADR-008 runtime, ADR-009 delivery | 005 Accepted (generic test refinement included). 006 Proposed (D-09). 007 Accepted: local PostgreSQL with guards. 008 Accepted, follow-up: Boot 4.1.2. 009 Accepted (D-06) |
| 1c3 | ADR-011 error contract and exception names, ADR-012 MapStruct mapping, ADR-013 CI pipeline, README update | All Accepted 2026-11-05. **ADR-010 is reserved for D-03** (URL scheme), written when you decide it, before Phase 2a |

**Verify:** every ADR has the template headings (`grep -c "^## " docs/adr/ADR-*.md`), README lists every file.

## 1d Kernel: persistence and config

| Step | Files | Design |
| --- | --- | --- |
| 1d1 | `common/config/AppProperties.java`, `common/config/JwtProperties.java`, `WmAdminPortalApplication.java` (`@ConfigurationPropertiesScan`), `common/config/AppPropertiesBindingTest.java` | Records bound to the real keys: `app.{name, version, frontend.{base-url, reset-password-path}, security.token-pepper}` and `jwt.{secret, issuer, access-token-expiry, refresh-token-expiry}` (expiries as `Duration`, plain numbers read as milliseconds). `@Validated`, `@NotBlank` on secret and pepper. Test: `ApplicationContextRunner` binds, and a missing secret fails |
| 1d2 | `common/entity/BaseEntity.java`, `common/entity/AuditableEntity.java`, `common/config/ClockConfig.java`, `common/config/ClockConfigTest.java` | `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`, `Long id` IDENTITY, `Instant createdAt` (`@CreatedDate`, not updatable), `updatedAt` (`@LastModifiedDate`), `createdBy`/`updatedBy` `Long`. Clock bean `Clock.systemUTC()`. Entities proven on PostgreSQL in 1h3 |
| 1d3 | `common/config/JpaAuditingConfig.java`, `common/config/JpaAuditingConfigTest.java` | Separate `@Configuration` with `@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")`. `AuditorAware<Long>` returns empty until Phase 3. Date provider uses the `Clock`, so timestamps are UTC `Instant`s. Kept off the application class so `@WebMvcTest` slices don't need JPA |

## 1e Kernel: errors and paging

| Step | Files | Design |
| --- | --- | --- |
| 1e1 | `common/exception/ResourceNotFoundException.java`, `DuplicateResourceException.java`, `InvalidDataException.java`, `UnauthorizedException.java` | Unchecked, message only. Tested through 1e2 |
| 1e2 | `common/logging/MdcKeys.java`, `common/exception/GlobalExceptionHandler.java`, `common/exception/GlobalExceptionHandlerTest.java` | Extends `ResponseEntityExceptionHandler`. Every body is a `ProblemDetail` with `code` and `traceId` (from MDC). Mapping: not found 404 `RESOURCE_NOT_FOUND`, duplicate 409 `DUPLICATE_RESOURCE`, invalid 400 `INVALID_DATA`, unauthorized 401 `UNAUTHORIZED`, Spring Security `AccessDeniedException` 403 `ACCESS_DENIED` and `AuthenticationException` 401 (so `@PreAuthorize` failures are not 500s), validation 400 `VALIDATION_FAILED` with `errors: [{field, message}]`, `DataIntegrityViolationException` with SQLState `23505` or `23503` 409 (constraint name logged, never returned), anything else 500 `INTERNAL_ERROR` with a fixed message. Framework errors keep their status (404, 405, 413, 415) and get `code`/`traceId` through `handleExceptionInternal`. 4xx logged at WARN without stack, 5xx at ERROR with stack. Test: `@WebMvcTest` on a test controller, filters off; covers each mapping, unknown path 404, 405, 415, and the log levels |
| 1e3 | `common/dto/PageResponseDto.java`, `common/dto/PageGuard.java`, and their two tests | `PageResponseDto<T>` record (`content, page, size, totalElements, totalPages, last`) with `from(Page<T>)`. `PageGuard.pageable(page, size, sortBy, direction, allowedSortFields, defaultSort)`: page below 0, size outside 1 to 100, or a sort field off the allow-list throws `InvalidDataException` (400) |

## 1f Kernel: logging

| Step | Files | Design |
| --- | --- | --- |
| 1f1 | `common/logging/TraceIdFilter.java`, `TraceIdFilterTest.java` | `OncePerRequestFilter`, `@Order(Ordered.HIGHEST_PRECEDENCE)` so it runs before the security chain (order -100). Reuses an incoming `X-Request-Id` only if it matches `[A-Za-z0-9-]{8,64}`, else a new UUID. Puts it in MDC and the `X-Trace-Id` response header, clears MDC in `finally`. Test asserts the order is below the security filter order |
| 1f2 | `common/logging/RequestLogFilter.java`, `RequestLogFilterTest.java` | Order `HIGHEST_PRECEDENCE + 1`. Logs method, **route pattern** (`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, or `UNMATCHED`), status and duration. Never the URI, query string or body, because path values and query strings can hold personal data. Test captures the log and asserts an email in the query and the body never appear |
| 1f3 | `src/main/resources/logback-spring.xml`, `common/logging/LogbackConfigFileTest.java` | `traceId` (`%X{traceId:--}`) in the console and file patterns. New `CONSOLE_JSON` appender with Boot's `StructuredLogEncoder` (`ecs`), used by the `staging` and `prod` profiles (OTH-23); file appenders kept. Drop the unused `JSON_PATTERN` and the "SaaS" header. Test reads the file and asserts those facts. Manual JSON check in 1h4 |

## 1g Boundary test

| Step | Files | Design |
| --- | --- | --- |
| 1g1 | `src/test/java/com/wealthmax/support/architecture/ModuleBoundaryRules.java`, `src/test/java/com/wealthmax/ModuleBoundaryArchitectureTest.java` | Rules take a base package. A module is the first package segment under the base. (1) No class in module A depends on another module's `entity`, `repository`, `mapper` or `service.impl` package. (2) `common` depends on no module. (3) No cycles between top-level packages. (4) Nothing depends on `com.wealthmax.adminPortal`. Real test imports `com.wealthmax` without tests; empty `should` allowed |
| 1g2 | fixtures `src/test/java/archfixture/good/common/util/Strings.java`, `good/shop/service/ShopService.java`, `good/billing/service/impl/BillingServiceImpl.java`, `com/wealthmax/support/architecture/ModuleBoundaryRulesTest.java` | Proves the allowed shape passes: a module calling another module's service interface and `common` |
| 1g3 | fixtures `archfixture/bad/common/util/Helper.java`, `bad/shop/service/ShopService.java`, `bad/billing/service/BillingService.java`, `bad/billing/entity/Invoice.java`, `ModuleBoundaryRulesTest.java` | Proves each rule reports a violation: shop to billing entity, common to shop, shop and billing service cycle, legacy package (the self-test passes `archfixture.bad.billing.entity` as the forbidden package) |

Fixtures live outside `com.wealthmax`, so the real test never sees them.

## 1h Test infrastructure (D-02: local PostgreSQL with guards)

| Step | Files | Design |
| --- | --- | --- |
| 1h1 | `src/test/java/com/wealthmax/support/LocalDatabaseOnlyInitializer.java`, `LocalDatabaseOnlyInitializerTest.java`, `src/test/resources/application-test.yml` | Ported from the old repository's `careers_in/support`, wording made module-neutral. Test datasource: `jdbc:postgresql://localhost:5432/wm_admin_portal_test`, username `${WM_TEST_DB_USER:postgres}`, password `${WM_TEST_DB_PASSWORD}` with no fallback (rule C1). **Proposal for you:** a dedicated local test database rather than your dev database. Testcontainers comment removed |
| 1h2 | `support/PostgresDataJpaTest.java`, `support/TablesMustBeEmptyExtension.java`, `src/test/resources/logback-test.xml` | Composed annotation: `@DataJpaTest`, `ddl-auto=validate`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Import(JpaAuditingConfig.class)`, the initializer, the extension, `@Tag("database")`. The extension reads table names from `@TablesMustBeEmpty({...})` on the test class, only counts rows, never deletes. Drop the `org.testcontainers` logger |
| 1h3 | `support/kernelprobe/KernelProbeEntity.java`, `KernelProbeRepository.java`, `KernelProbeJpaTestConfig.java`, `src/test/resources/db/test-migration/V9000__kernel_probe.sql`, `KernelPersistenceDatabaseTest.java` | First database test. Flyway applies `db/migration` plus the test folder to an empty local database. `validate` passes for an `AuditableEntity` subclass. A saved row gets `id`, `createdAt` and `updatedAt` from auditing; a duplicate on a unique column raises `DataIntegrityViolationException` with SQLState 23505 (feeds the 1e2 mapping) |
| 1h4 | `src/test/java/com/wealthmax/WmAdminPortalApplicationTests.java`, `pom.xml` (properties migrator added then removed) | `@SpringBootTest` + `@AutoConfigureMockMvc`, test profile, guard, `@Tag("database")`, a test-only `SecurityFilterChain` that permits everything (real security arrives in 2a). Asserts: context loads, `GET /no-such-path` returns a `ProblemDetail` 404 with `traceId` and an `X-Trace-Id` header (Phase 1 exit criterion). Run once with `spring-boot-properties-migrator`, fix non-secret keys it reports, then remove it. Manual: start with profiles `local,prod` and confirm JSON on stdout |

**Database prerequisite (you):** create the local database `wm_admin_portal_test` and set `WM_TEST_DB_PASSWORD`. Without it, database tests are reported "not run".

Between now and Phase 2a the running app answers 401 to everything (Boot's default security). Nothing is deployed before Phase 2.

## 1i CI

### Step 1i1: Pipeline and doc sync

**Files:** `azure-pipelines.yml`, `CLAUDE.md`, `.claude/skills/integration-testing/SKILL.md`, `.claude/skills/dependency-upgrade/SKILL.md`.

- Pipeline: PR trigger on `Dev`, `ubuntu-latest`, `Maven@4` with JDK 21, `verify -B -DexcludedGroups=database`, publish surefire results.
- `CLAUDE.md`: stack line (Boot 4.1.2, Hibernate 7.4, Flyway 12, springdoc 3), CI command, `@Tag("database")` rule.
- The two skills: ADR-007 decided; status table "today = 4.1.2".

**Verify:** `mvn -B verify -DexcludedGroups=database` passes locally. The pipeline itself runs only after 1a2's push. Build validation on `Dev` is set in Azure DevOps.

### Step 1i2: Remaining stale stack mentions

**Files:** `flyway-migration`, `coding-standards`, `api-designer`, `architecture-designer`, `postgres-performance` skills (`security-reviewer` and `agents/code-reviewer.md` in a follow-up step if needed). One line each: "3.5.16 / Hibernate 6.6 / Flyway 11" to the 4.1.2 stack.

## 1j Behaviour inventory

| Step | Files | Design |
| --- | --- | --- |
| 1j1 | `docs/behaviour-inventory.md` (identity, careers) | Table per module: `ID` (`BR-<MODULE>-nn`), rule, source (`admin-portal-module-documentation.md` section), test phase. Sources: sections 5.1 to 5.3, 5.6 to 5.9, 5.15, 7. Seeds: IN 6-month cooldown on lowercased email + exact mobile across any job; `WFA-IN-0001` job number series; CTC in LPA |
| 1j2 | `docs/behaviour-inventory.md` (leads, content, reporting, platform) | Sections 5.4, 5.5, 5.10 to 5.14, 5.16 to 5.18, 4.7 to 4.9. Seeds: lead email routing by category, referral numbers, newsletter slug rules |

**Verify:** every rule row has an ID, a source section and a phase between 3 and 7. Every section listed above has at least one row.

## 1k Audit timestamps: no microsecond cut

### Step 1k1: Remove the microsecond cut from `JpaAuditingConfig`

**Files:** `common/config/JpaAuditingConfig.java`, `common/config/JpaAuditingConfigTest.java`, `support/kernelprobe/KernelPersistenceDatabaseTest.java`.

- Why: Sarthak decided on 2026-11-06 that the cooling-off rule compares dates only. A difference of a few nanoseconds between a saved entity and its reloaded row cannot change a date, so the cut (added in 1h3) is no longer needed.
- `auditingDateTimeProvider` returns `clock.instant()` as it is. It still reads the `Clock`, so tests can fix the time.
- `auditorAware()` stays and still returns empty. Phase 3 (identity) replaces it with the signed-in user's id.
- `JpaAuditingConfigTest`: the microsecond test is deleted. The `Clock` test and the empty-auditor test stay.
- `KernelPersistenceDatabaseTest`: the reloaded `createdAt` is compared with a 1 ms tolerance. The saved object keeps the Java clock's nanoseconds, and PostgreSQL stores microseconds, so an exact match no longer holds.

**Verify:** `mvn -B test -Dtest="JpaAuditingConfigTest,ModuleBoundaryArchitectureTest,KernelPersistenceDatabaseTest"`, then `mvn -B test`.

## 1l Plain `@Configuration`

### Step 1l1: Remove `proxyBeanMethods = false` from the three config classes

**Files:** `common/config/ClockConfig.java`, `common/config/JpaAuditingConfig.java`, `common/config/PropertiesScanConfig.java`.

- Why: Sarthak decided on 2026-11-06 to remove it. The setting only skips a CGLIB proxy. None of the three classes calls one `@Bean` method from another, so it saved a little startup time and changed no behaviour.
- `@Configuration(proxyBeanMethods = false)` becomes `@Configuration`. Nothing else changes.
- Rule going forward: a `@Bean` method gets another bean through a parameter, as `auditingDateTimeProvider(Clock clock)` does. It never calls the other `@Bean` method. That stays correct with or without a proxy.

**Verify:** `mvn -B test`. `WmAdminPortalApplicationTests` starts the whole app, so it proves the beans still wire.

---

## Exit criteria (from the master plan)

- [x] On Azure DevOps with `Dev` and `Staging`, CI green on `Dev`. Ticked on Sarthak's word (2026-11-06). Claude did not see a CI run. `origin` is `https://github.com/imsaarthi/WM-Admin-Portal-BE.git` (not Azure DevOps), and `origin/Dev` and `origin/Staging` exist. See open item 5
- [x] `mvn verify` passes, including `ModuleBoundaryArchitectureTest` and the first database test. `mvn -B clean verify` on 2026-11-06: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS, jar built. The earlier jar lock is gone
- [x] Unknown path returns `ProblemDetail` 404 with `traceId` (1h4: `WmAdminPortalApplicationTests`, and seen live on the packaged app)
- [x] ADR files for every decision made so far (1c: ADR-001 to 009 and 011 to 013; ADR-010 is reserved because gate D-03 is undecided)

## Open items for you

1. ADR-004: mark Accepted? (1c1)
2. Dedicated local test database `wm_admin_portal_test`? (1h1)
3. DevOps: replace the literal secrets in `application.yml` (1a2)
4. D-03 URL scheme before Phase 2a (ADR-010)
5. `origin` points to GitHub, but `CLAUDE.md` says this repository lives on Azure DevOps and must not be pushed anywhere else. Which is right? If GitHub is wrong, the remote needs fixing before the next push (1a2)

## Progress record

| Step | Status | Patch summary |
| --- | --- | --- |
| 1a1 | done 2026-11-05. Deviation: git needs `safe.directory` on `D:`; the hook also blocks the fake Azure key in `application-test.yml` (moved into 1h1) | `patch-1a1-source-control-init-summary.md` |
| 1a2 | done per Sarthak, 2026-11-06. Seen in git: commit `66eab33 first commit` on `Dev`, and `origin/Dev` and `origin/Staging` exist. `origin` is a GitHub URL, not Azure DevOps (open item 5). Not checked: whether DevOps replaced the secret values in `application.yml` | |
| 1b1 | done 2026-11-05. Compile and test-compile exit 0 on Boot 4.1.2; parent version prints 4.1.2; 0 testcontainers lines in `dependency:tree`; D-05 proven (RED `Unmapped target property: "neverMapped"`, GREEN after the probe was removed) | `patch-1b1-boot-4-pom-summary.md` |
| 1c1 | done 2026-11-05. README + ADR-001 to 004; all headings present, links resolve | `patch-1c1-adr-001-to-004-summary.md` |
| 1c2, 1c3 | done 2026-11-05. ADR-005 to 009 and 011 to 013 plus full README; 12 ADRs, `bad=0`. ADR-010 reserved for D-03 | `patch-1c2-1c3-adr-005-to-013-summary.md` |
| 1d1, 1d2, 1d3 | done 2026-11-05. `mvn -B test`: 8 run, 0 failures. Entities unverified until 1h3 | `patch-1d-kernel-persistence-config-summary.md` |
| 1e1, 1e2, 1e3 | done 2026-11-05. `mvn -B clean test`: 38 run, 0 failures. Two rulings below (scan moved off the application class; Jackson 3 key in `application.yml`) | `patch-1e-kernel-errors-paging-summary.md` |
| 1f1, 1f2, 1f3 | done 2026-11-05. `mvn -B clean test`: 55 run, 0 failures. JSON output not yet seen running (1h4) | `patch-1f-kernel-logging-summary.md` |
| 1g1, 1g2, 1g3 | done 2026-11-05. Boundary tests 12/12 (4 real + 8 self-tests). Mutation check: disabling the reporting turned 2 self-tests red; restored | `patch-1g-boundary-test-summary.md` |
| 1h1, 1h2, 1h3 | done 2026-11-05. `mvn -B clean test`: 101 run, 0 failures, 6 against real PostgreSQL. Both guards proven on a real start. Found and fixed a nanosecond-vs-microsecond audit timestamp bug (the cut was removed again in 1k1) | `patch-1h1-1h3-test-infrastructure-summary.md` |
| 1h4 | done 2026-11-05 except cleanup: `mvn -B clean test` 104 run, 0 failures; whole app starts; unknown path is a ProblemDetail 404 with traceId; migrator clean and removed; live JSON log seen. **Open:** my live-check app (PID 29288, port 18081) and scratch DB `wm_json_check` need stopping/dropping by you | `patch-1h4-app-start-and-live-checks-summary.md` |
| 1i1, 1i2 | done 2026-11-05 except the end-to-end `verify`: `mvn -B test -DexcludedGroups=database` 95 run, 0 failures; `clean verify` blocked locally by my leftover app locking the jar. Pipeline file written, not run (needs push + admin policy) | `patch-1i-ci-and-doc-sync-summary.md` |
| 1j1, 1j2 | done 2026-11-05. `docs/behaviour-inventory.md`: 98 rules (66 Keep, 29 Change, 3 Drop), all 21 source sections cited, no malformed row or duplicate ID. Section 7 (login flow) not covered. A `sed` mistake corrupted the file and was repaired from source | `patch-1j-behaviour-inventory-summary.md` |
| 1k1 | done 2026-11-06. `mvn -B test`: 103 run, 0 failures, database tests included (104 minus the deleted microsecond test). `auditorAware()` kept for Phase 3. `clean verify` not run. **Open:** `dependency-upgrade` skill line 134 still says to cut timestamps to microseconds | `patch-1k-audit-timestamp-no-cut-summary.md` |
| 1l1 | done 2026-11-06. `mvn -B test`: 103 run, 0 failures, database tests included. No other file mentioned `proxyBeanMethods`. `clean verify` not run | `patch-1l-plain-configuration-summary.md` |
| **Phase 1** | **Complete, marked by Sarthak on 2026-11-06.** Last full run: `mvn -B clean verify`, 103 run, 0 failures, BUILD SUCCESS. Not seen by Claude: a CI run on any remote. Patches 1k and 1l are not committed yet (the log has one commit, `66eab33`) | |

**End-of-phase check (fresh, 2026-11-05):** `mvn -B -q compile` exit 0. `ModuleBoundaryArchitectureTest` 4/4. `mvn -B test` 104 run, 0 failures, including 9 against real PostgreSQL. No old-repo import, tenant word, `@Autowired` field or `double`/`float` in `src`. The pre-commit hook blocks only on the two DevOps lines in `application.yml`. 142 files staged, nothing committed.

## Rulings

- All steps: Ruling: run the steps back to back, without stopping between them. Why: the user said "implement this plan" for the whole plan after being told the per-step stop could only be removed by an explicit instruction. Each step still gets its own verification, patch summary and ledger line. The stops that remain are the ones `executing-plans` lists: DevOps secrets, push and other outward-facing actions, destructive actions. Cost if wrong: the user reviews several steps at once.
- All steps: Ruling: work stays uncommitted on `Dev`. Why: the repository has no commits, so no feature branch can be cut from anything. The first commit is step 1a2 and needs the user. Cost if wrong: none, nothing is committed.
- All steps: Ruling: new tests use `@MockitoBean`, not `@MockBean`. Why: the `test-driven-development` skill still says `@MockBean` until the Boot 3.4 hop, but this repository is on Boot 4.1.2, where `@MockBean` is gone. Cost if wrong: none.
- 1d1: Ruling: `@ConfigurationPropertiesScan` lives on `common/config/PropertiesScanConfig`, not on the application class. Why: test slices load the application class and would bind `app.*` and `jwt.*`. Cost if wrong: one class.
- 1e2: Ruling: `application.yml` key `spring.jackson.serialization.write-dates-as-timestamps` moved to `spring.jackson.datatype.datetime.write-dates-as-timestamps`. Why: Jackson 3 removed it from `SerializationFeature`, and the old key stopped the context starting. Non-secret. Cost if wrong: one key.
- 1e2: Ruling: framework error `code` is the HTTP status name, and a foreign-key violation is `DATA_CONFLICT`. Why: the plan left these unnamed. ADR-011 updated. Cost if wrong: rename a constant.
- 1h4: Ruling: `spring.web.error.include-message` and `include-binding-errors` set to `never` (they were `always`). Why: template leftovers; `GlobalExceptionHandler` defines the error contract. Cost if wrong: two words.
- 1h4: Ruling: the live check used a scratch database and a packaged jar, not the `local` profile. Why: `local` would connect to a developer database and let Flyway touch it. Cost if wrong: none.
- 1b1: Ruling: git commands use `-c safe.directory=D:/WM-Admin-Portal-BE`. Why: the `D:` drive records no file ownership, so git refuses the repository. The permanent fix is a global git config change, which is the user's call.
- 1k1: Ruling: audit timestamps are not cut to microseconds, and the one database assertion that needed an exact match uses a 1 ms tolerance instead. Why: Sarthak decided that cooling-off checks compare dates only, so nanosecond differences cannot change a result. Cost if wrong: if a later rule compares exact instants across a database round trip, the cut (one `truncatedTo` call) has to return.
- 1l1: Ruling: config classes use plain `@Configuration`, without `proxyBeanMethods = false`. Why: Sarthak decided on 2026-11-06. The three classes never call one `@Bean` method from another, so the proxy changes nothing. Cost if wrong: a few milliseconds of startup. If someone later calls a `@Bean` method directly from another, that call creates a second object only when the proxy is off, so keep passing beans in as parameters.
