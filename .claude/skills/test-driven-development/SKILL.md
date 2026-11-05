---
name: test-driven-development
description: Use when implementing any feature or bugfix in WM-Admin-Portal-BE. Write the failing test first, always. Covers unit tests (Mockito), controller tests with MockMvc, repository tests on a real local PostgreSQL database (the only way to prove query and constraint behaviour), security-rule tests with the real security chain, and the module boundary test.
user-invocable: true
---

# Test-Driven Development: WM-Admin-Portal-BE

Based on `test-driven-development` from obra/superpowers (MIT License, Copyright (c) 2025 Jesse
Vincent), rewritten for this project.

This is a **single-tenant** modular monolith. Tests have no tenant to set up, and no isolation to
prove. What they do prove: the module's own logic, its database behaviour, its access rules, and
that its boundaries hold.

## The Rule

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

Wrote code before the test? Delete it and start again from the test. Don't "adapt" it.

If you didn't watch the test fail, you don't know it tests the right thing.

See `testing-anti-patterns.md` in this folder before writing mocks or test utilities.

## When

Always: every new service method, repository query, endpoint, bug fix, or change to business
logic.

## Red, Green, Refactor

```
RED       Write one failing test. Watch it fail for the RIGHT reason.
GREEN     Write the least code that passes. Watch it pass.
REFACTOR  Clean up. Stay green.
REPEAT
```

---

## Which Kind of Test

| You want to prove | Use | Why |
|---|---|---|
| The service's own logic (rules, branching, mapping, exceptions) | Unit test, Mockito | Fast, no Spring, no database |
| Request parsing, validation, status codes, the error body | Controller test, standalone `MockMvc` with the real `GlobalExceptionHandler` | Real validation and real error mapping, service mocked |
| A query, a constraint, a sequence, a `Specification` search, column mapping | Repository test on local PostgreSQL (`@PostgresDataJpaTest` style) | Only the real database runs the SQL and the constraints |
| Who may call a route (Public, Authenticated, Admin) | Security-rules test: `@WebMvcTest` plus the real `SecurityConfiguration` and `JwtAuthenticationFilter` | A mocked service can't test URL rules |
| A module doesn't import another module's internals | The module boundary test | The compiler doesn't enforce it |

A Mockito test **cannot** prove a database claim: a mocked repository never runs SQL, so it can't
see a unique constraint, a case-insensitive match or a paging query.

### Layer 1: Unit tests (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class CareersINServiceImplTest {

    @Mock private CareersINRepository careersINRepository;
    @Spy  private CareersDetailsMapper careersDetailsMapper = new CareersDetailsMapper();

    @InjectMocks private CareersINServiceImpl service;

    @Test
    @DisplayName("getJobDetailsById throws when the job does not exist")
    void getJobDetailsById_whenMissing_throwsResourceNotFound() {
        when(careersINRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobDetailsById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(careersINRepository, never()).save(any());
    }
}
```

- Mock every other constructor dependency too, or `@InjectMocks` leaves it null.
- Use real mappers (`@Spy`) rather than mocking them. Mocking a mapper tests nothing.
- Unit tests cover logic. Access rules live in `SecurityConfiguration`, so they aren't visible
  here.

### Layer 2: Controller tests (real validation, real error mapping)

`CareersINControllerTest` builds `MockMvc` standalone around the controller, with the real
`GlobalExceptionHandler` and a mocked service. No Spring context, no database.

```java
@Test
@DisplayName("createJob with a blank title returns 400 and a violation")
void createJob_withBlankTitle_returns400() throws Exception {
    mockMvc.perform(post(BASE + "/createJob")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobDetails\":{\"jobTitle\":\"\"}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].field").exists());

    verifyNoInteractions(service);   // validation stopped it before the service
}
```

### Layer 3: Repository tests on a real database

Use the `@PostgresDataJpaTest`-style slice: local PostgreSQL, rollback after each test, refuses
non-local databases. Details, guards and gotchas: `integration-testing`.

```bash
mvn test -Dtest=CareersINRepositoryTest      # local PostgreSQL must be running
```

If PostgreSQL isn't running, say the database tests didn't run. Don't call it a pass.

### Layer 4: Security-rules tests

The old repository's `<Module>SecurityRulesTest` is the model: `@WebMvcTest` with the security
configuration and the JWT filter imported, services mocked with `@MockitoBean`, and `TestAuthentication.admin()`
for an authenticated call. A protected route without a token is **403**. Every new route gets one
test for "works as intended" and one for "rejected otherwise". Also assert that a new path isn't
caught by an older wildcard.

**Mocking annotation.** Use `@MockitoBean`
(`org.springframework.test.context.bean.override.mockito`). `@MockBean` is deprecated since Boot
3.4 and removed in 4.0, so never write it here. See `dependency-upgrade`.

### Layer 5: Module boundary test

`ModuleBoundaryArchitectureTest` (ArchUnit, see `modular-monolith`) covers every module. It runs
with no database:

```bash
mvn test -Dtest=ModuleBoundaryArchitectureTest
```

---

## RED: the failing test

- One behaviour per test.
- Name it `subject_condition_expectedResult`, plus a `@DisplayName` sentence.
- Real objects where possible. Mocks only for collaborators and external services (Mailgun, Azure
  Blob).

```bash
mvn test -Dtest=ClassName#methodName -q
```

It must **fail**, not error: a compile error or `NullPointerException` means the test setup is
wrong. It must fail because the behaviour doesn't exist yet. A test that passes straight away is
testing something that already exists. Rewrite it.

## GREEN: the least code

Exactly what the test demands. No extra options, no "while I'm here" helpers, no refactoring of
nearby code.

## REFACTOR: clean up

Only after green, never adding behaviour: extract repeated validation, rename unclear methods,
remove duplicate null checks, name magic strings. Run the tests after each step.

## Running Tests

```bash
mvn test -Dtest=ClassName#methodName -q   # one method
mvn test -Dtest=ClassName -q              # one class
mvn test -Dtest='ModuleBoundaryArchitectureTest,*SecurityRulesTest,*ControllerTest,*ServiceImplTest' -q   # no database needed
mvn test -Dtest='com.wealthmax.<module>.**.*Test' -q   # whole IN module, local PostgreSQL needed
```

This is a single-module Maven build: there's no `-pl`. Narrow a run with `-Dtest`. Quote patterns
in Git Bash. Any test using the database needs local PostgreSQL running, and the IN tables empty
(see `integration-testing`).

## Checklist Before Calling a Task Done

- [ ] Every new method has a test.
- [ ] I watched each test fail before implementing, for the right reason.
- [ ] The implementation is the least that passes.
- [ ] The tests pass now, or I've said plainly which ones couldn't run and why.
- [ ] Database behaviour (constraints, search, sequences) is proven on the real database, not by
      mocks.
- [ ] A schema change has its Flyway migration, and it applied cleanly on a fresh local database.
- [ ] A new route has security-rule tests in both directions.
- [ ] A new module has its boundary test, and it passes.
- [ ] Removal behaviour matches the plan: hard delete, soft delete or deactivate.

Can't tick them all? The task isn't done.

## Bug Fix Workflow

Reproduce the bug in a test first:

```java
@Test
@DisplayName("A blank resume file name is rejected with 400, not saved")
void addApplicant_withBlankResumeName_isRejected() {
    // the exact conditions that trigger the bug: fails before the fix, passes after
}
```

Run it and watch it fail. Fix. Watch it pass. The test stays forever, so the bug can't return.

Never fix a bug without a test.
