# Testing Anti-Patterns: WM-Admin-Portal-BE

**Load this when:** writing Mockito mocks, creating test utilities, or adding test-only behaviour.

## Core Principle

Tests must verify real behaviour, not mock behaviour.
Mocks are a tool to isolate, not the thing being tested.

---

## Anti-Pattern 1: "Proving" Database Behaviour With a Mocked Repository

**The violation:**
```java
// BAD: the repository is a mock, so no SQL runs and no constraint exists
@Test
void shouldRejectDuplicateJobId() {
    when(careersINRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> service.saveJobDetails(request))
            .isInstanceOf(DataIntegrityViolationException.class);   // proves only the mock
}
```

**Why this is wrong:** whether `job_id` is unique, whether a search is case-insensitive, whether a
sequence yields the next number: those live in PostgreSQL and in the JPA mapping, not in your
service. A `@Mock` repository has none of them, however cleverly it is stubbed. This isn't "write a
better mock". It's the wrong test type for the claim.

**The fix:** move the claim to a repository test on the real local database (see
`integration-testing`):

```java
@PostgresDataJpaTest
class CareersINRepositoryTest {
    @Test
    void duplicateJobId_failsAtTheDatabase() {
        repository.saveAndFlush(job(CareersTestData.fullJobRequest(), "WFA-IN-0001"));

        assertThatThrownBy(() -> repository.saveAndFlush(job(CareersTestData.fullJobRequest(), "WFA-IN-0001")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

A Mockito test still earns its keep for the service's **own** logic: for example, that it throws
`DuplicateResourceException` when `existsBy...` says a duplicate exists. Just don't ask a unit test
to prove something only the database can prove.

---

## Anti-Pattern 2: Test-Only Methods on Production Classes

**The violation:**
```java
// BAD: this method only exists for tests
public class ApplicantINServiceImpl {
    public void setCooldownForTesting(Period cooldown) { ... }   // WRONG
}
```

**Why this is wrong:**
- Production class polluted with test infrastructure
- Suggests the real configuration path isn't the one used
- Separate naming implies different behaviour where there is none

**The fix:**
```java
// GOOD: inject what varies (a Clock, a Period, a collaborator) through the constructor, and
// pass a different one from the test. Hypothetical: ApplicantINServiceImpl takes four
// collaborators today and uses a fixed COOLDOWN constant. If a test needs to control time or the
// period, add a Clock or Period constructor parameter instead of a setter.
new ApplicantINServiceImpl(repository, mapper, mailgun, transactionManager /*, fixedClock */);
```

---

## Anti-Pattern 3: Mocking Without Understanding What You're Bypassing

**The violation:**
```java
// BAD: expecting a standalone MockMvc controller test to prove 401/403
mockMvc.perform(delete("/api/v1/applicant/IN/delete/5"))
        .andExpect(status().isForbidden());   // no security chain is loaded, so this can't happen
```

The trap in this codebase: access is decided by URL rules in `SecurityConfiguration`, applied by
the security filter chain. A standalone `MockMvc` built around one controller, as in
`CareersINControllerTest`, never loads that chain. It can prove validation and error mapping. It can
never prove who may call a route.

**The fix:**
```java
// GOOD: controller test proves request shape, validation and error mapping
@Test
void createJob_withBlankTitle_returns400() throws Exception {
    mockMvc.perform(post(BASE + "/createJob").contentType(APPLICATION_JSON).content("{...}"))
            .andExpect(status().isBadRequest());
    verifyNoInteractions(service);   // @Valid stopped it first
}

// GOOD: access rules are proven with the real SecurityConfiguration in front of the controller
// (see <Module>SecurityRulesTest): protected call without a token -> 403, with admin() -> let through
```

---

## Anti-Pattern 4: Incomplete Mock Data Structures

**The violation:**
```java
// BAD: partial response, only the fields you think you need
CareersResponseDto mockResponse = new CareersResponseDto();
mockResponse.setId(1L);
// missing: jobId, status, datePosted and the three nested sections a real response always has
```

**Why this is wrong:**
- Other code reading the response may hit a field that's silently null here
- The test passes, but production fails because the real response is always fully populated

**The fix:**
```java
// GOOD: build complete objects with the shared test data helper
CareersResponseDto response = CareersTestData.fullJobResponse(1L, "WFA-IN-0001");
```

---

## Anti-Pattern 5: Adding a Module Without Its Boundary Test

**The violation:**
```java
// BAD: a new module, with unit tests, and no check that it stays inside its boundary
// leads/ ... imports com.wealthmax.careers.repository.JobApplicationRepository  (another module's repository)
```

**Why this is wrong:** in a modular monolith the compiler doesn't enforce module boundaries. A
module that quietly imports another module's repository works today and couples the two for good.
Passing unit tests say nothing about it.

**The fix:** `ModuleBoundaryArchitectureTest` (ArchUnit) covers every module, so run it with every
change and never add an exception to make it pass. See `modular-monolith`.

---

## Anti-Pattern 6: Using `any()` Where a Specific Value Matters

**The violation:**
```java
// BAD: any() hides a wrong-status bug
verify(careersINRepository).findAllByStatus(any(), any());
```

**The fix:**
```java
// GOOD: verify the exact value the service was supposed to pass
verify(careersINRepository).findAllByStatus(eq(JobStatusEnum.ACTIVE), any(Sort.class));
```

Use `any()` for what the test doesn't care about, and exact values for what it does.

---

## Anti-Pattern 7: Tests That Can Touch a Shared Database

**The violation:**
```java
// BAD: a database test with no guard, run against whatever the datasource points at
@SpringBootTest
class ApplicantCleanupTest {
    @Test void clearsOldRows() { repository.deleteAll(); }
}
```

**Why this is wrong:** the datasource comes from `application*.yml`, and an override can
point it at a shared environment. `deleteAll()` there is a data-loss incident.

**The fix:** database tests use the guarded slice (`@PostgresDataJpaTest` style:
`LocalDatabaseOnlyInitializer`, rollback, empty-table check) and never `create` or `create-drop`.
Never weaken or bypass the guard. See `integration-testing`.

---

## Quick Reference

| Anti-Pattern | Fix |
|---|---|
| Mocked repository "proving" a constraint, search or sequence | Repository test on the real local PostgreSQL |
| Test-only method on a production class | Inject the thing that varies, through the constructor |
| Standalone controller test asserting 401/403 | Security-rules test with the real `SecurityConfiguration` |
| Incomplete mock objects | Build complete objects with the `*TestData` helpers |
| New module with no boundary test | Add `<Module>ModuleBoundaryTest` in the same change |
| `any()` where the value matters | Use `eq(...)` or a captor |
| Database test with no guard | Use the guarded slice. Never bypass the guard |

## Gate Function: Run Before Writing Any Mock

```
BEFORE mocking any method:

1. What does the real method DO?
2. Does my test depend on any of those side effects?
3. Am I testing real behaviour or mock behaviour?
4. If this is a database claim (constraint, search, sequence, paging): can a mock even see the
   thing I'm asserting, or does this need the real database?
5. If this is an access claim (who may call): is the real security chain loaded?

If testing mock behaviour -> rewrite the test
If unsure -> run the test against the real implementation first, then add minimal mocking
```
