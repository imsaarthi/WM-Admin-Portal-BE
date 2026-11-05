---
name: integration-testing
description: Write database and integration tests in WM-Admin-Portal-BE. Use whenever a test needs real PostgreSQL behaviour (a query, a constraint, a sequence, a native query), the real security rules in front of controllers, or the whole application. They run on the developer's own local PostgreSQL, with safety guards (ADR-007, accepted 2026-11-05). No H2, no Testcontainers.
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
  domain: testing
  triggers: integration test, repository test, real database, constraint, native query, sequence, security rules test, Spring Boot Test, WebMvcTest
  role: test-infrastructure-specialist
  scope: implementation
  output-format: java-examples
  related-skills: coding-standards, modular-monolith, test-driven-development
---

# Database and Integration Testing: WM-Admin-Portal-BE

This is a **single-tenant** application, so there is nothing tenant-shaped to seed or assert. No
"two tenants" fixtures, no context to set and clear.

**There is no H2 or in-memory database, and no Testcontainers.** ADR-007 (`docs/adr/`) decided on
local PostgreSQL with guards: no Docker, and database tests run only on developers' machines. CI
cannot run them, so every database test is tagged `database` and the pipeline skips that tag
(`mvn test -DexcludedGroups=database`, ADR-013). Don't write a test that needs Docker.

One-time set-up: `CREATE DATABASE wm_admin_portal_test;` on a PostgreSQL on your machine. If the
server wants a password, set `WM_TEST_DB_USER` and `WM_TEST_DB_PASSWORD` in your own environment.

## The Test Support That Exists

It lives in `src/test/java/com/wealthmax/support/` (ported from the old repository in Phase 1 step
1h) and is shared, so every module uses the same guards. Copy the shape of
`support/kernelprobe/KernelPersistenceDatabaseTest`, the first database test. The pieces:

- `@PostgresDataJpaTest`: a custom annotation that makes a JPA slice test run on the local
  PostgreSQL database:
  - `@DataJpaTest` with **`ddl-auto=validate`**. `@DataJpaTest` runs Flyway, so every test run also
    proves the migrations apply and every entity matches them, and a missing migration fails the
    test. **Never `create`, `create-drop` or `update`.**
  - `@AutoConfigureTestDatabase(replace = NONE)`: stops Spring swapping in an embedded database.
  - `@Import(<Module>JpaTestConfig.class)`: loads only that module's entities and repositories, so
    its tests can't reach another module's tables.
  - `@ContextConfiguration(initializers = LocalDatabaseOnlyInitializer.class)`: **refuses to start
    unless the datasource host is `localhost`, `127.0.0.1` or `::1`**. It runs before Flyway, so a
    migration can never reach a shared database through a test.
  - `@ExtendWith(TablesMustBeEmptyExtension.class)`: stops with a message if a table named in the
    test class's `@TablesMustBeEmpty({"table_a", "table_b"})` already holds rows. It never deletes
    anything. Use it on classes that assert exact row counts or first generated numbers.
  - `@Tag("database")`: added by the annotation, so CI skips the test.
  - Each test runs in a transaction that rolls back, so nothing is left behind.
- `<Module>SecurityRulesTest`: `@WebMvcTest` with the **real** security configuration and JWT
  filter in front of the module's controllers, services mocked with `@MockitoBean`. No database.
- `*ControllerTest`: Mockito plus standalone `MockMvc` with the real exception advice and real bean
  validation. No Spring context, no database.
- `*ServiceImplTest`: plain Mockito unit tests.
- `ModuleBoundaryArchitectureTest`: the ArchUnit boundary rules (see `modular-monolith`). No
  database.
- `TestAuthentication` (an `admin()` request post-processor) and the `*TestData` builders.

## Writing a New Repository Test

```java
@PostgresDataJpaTest
class CareersINRepositoryTest {

    @Autowired
    private CareersINRepository repository;

    @Test
    @DisplayName("A second job with an existing job id violates the unique constraint")
    void duplicateJobId_failsAtTheDatabase() {
        repository.saveAndFlush(job(CareersTestData.fullJobRequest(), "WFA-IN-0001"));

        assertThatThrownBy(() -> repository.saveAndFlush(job(CareersTestData.fullJobRequest(), "WFA-IN-0001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Build entities from the shared test data, with every column the table needs filled in
    private CareersINEntity job(CareersCreateRequestDto request, String jobId) { ... }
}
```

Real examples to copy: `CareersINRepositoryTest` (constraint, sort and filter by status) and
`CareersINSearchTest` (the `Specification` search).

- The slice is transactional, so writes roll back by themselves.
- `saveAndFlush()` forces the `INSERT` now. A plain `save()` may not reach the database before the
  assertion, so a constraint violation would go unseen.
- **Assert the constraint.** A `DataIntegrityViolationException` can come from a different
  constraint than the one you meant. Check the constraint name in the message, so the test passes
  for the right reason.
- Build entities with a `*TestData` helper, with every column the table needs filled in.

## When a Real Database Matters

Use a database test when the thing under test is database behaviour:
- a unique constraint, index, or foreign key;
- a sequence or any `nativeQuery = true`;
- a JPA `Specification` search (case-insensitive match, element-collection search, paging);
- column mapping (a `TEXT` column, an enum stored as a string);
- anything where a mocked repository would hide the answer.

Pure logic (validation, branching, mapping) is better as a Mockito unit test, and the request and
validation path as a controller test with `MockMvc`.

## Testing Security Rules

URL rules in `SecurityConfiguration` are only proven by the real configuration in front of the real
controller. Follow `<Module>SecurityRulesTest`:

```java
@WebMvcTest(controllers = {CareersINController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class})
class CareersSecurityRulesTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JobService jobService;
    @MockitoBean private JwtService jwtService;
    // Deep stubs: SecurityConfiguration needs userService.userDetailsService() to return something
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) private UserService userService;

    @Test
    void protectedCall_withoutToken_isRejectedBySecurity() throws Exception {
        mockMvc.perform(post("/api/v1/careers/IN/createJob")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());   // 403: Spring answers before the controller
    }
}
```

- A missing or invalid token on a protected route is **403**, not 401.
- A request that passes security and then fails validation or finds nothing (400, 404) still counts
  as "let through". Only 401 and 403 mean security stopped it.
- Assert **both** directions for every new rule: the public call works without a token, the
  protected call fails without one. Also assert that a new path isn't swallowed by an older
  wildcard (rule order matters: first match wins).

## Testing a Migration

A Flyway migration is proven by running it, not by reading it:

- **Apply on an empty database:** start the application or a database slice test against a new,
  empty local database. The Flyway log lists each migration, and `ddl-auto=validate` then
  confirms every entity matches.
- **Re-run:** a second start applies nothing.
- **Existing data:** for a migration that changes or backfills data, insert a few representative
  rows first (in a throwaway local database) and check the result.

Never run these against dtg or prod, and never from a test that bypasses
`LocalDatabaseOnlyInitializer`.

## Cleanup

Prefer the slice's own rollback. If the code under test manages its own transactions (for example
`ApplicantINServiceImpl` uses a `TransactionTemplate`), a wrapping transaction would hide that, so
delete the rows your test created in `@AfterEach`, and only those. Never add `@DirtiesContext`:
it reloads the Spring context and slows every later class.

## Running

```bash
# One class (needs local PostgreSQL running for database tests)
mvn test -Dtest=JobRepositoryTest

# One whole module
mvn test -Dtest='com.wealthmax.<module>.**.*Test'

# No database needed: boundary scan, security rules, controller and service unit tests
mvn test -Dtest='ModuleBoundaryArchitectureTest,*SecurityRulesTest,*ControllerTest,*ServiceImplTest'
```

Keep the quotes around a pattern in Git Bash.

## New Module: Test Support

The guards live once, in `com.wealthmax.support`. Each module adds only its own small
`<Module>JpaTestConfig` listing its entities and repositories. That keeps each module's tests
unable to reach another module's tables, the same rule the production code follows.

## Gotchas

1. **Local PostgreSQL must be running.** Without it, database tests fail to connect. Report them
   as not run, and don't call that a pass.
2. **The guard refuses non-local databases.** If it stops a run, that's the guard working. Don't
   bypass it.
3. **Non-empty tables stop the run.** `TablesMustBeEmptyExtension` stops with a message if
   the module's tables hold rows. Clear your own local rows. Never script a delete that
   could run against a shared database.
4. **`validate` proves the migrations.** The slice uses `validate` and runs Flyway, so a passing
   database test proves the migration exists and matches the entity. A new migration is also
   verified by a fresh-database start (see `flyway-migration`, "Verify").
5. **Hibernate may answer from its cache** without running SQL. Call `flush()` and `clear()` on
   the `EntityManager` before reading back, when the point of the test is what the database does.
6. **No `@DirtiesContext`.**
