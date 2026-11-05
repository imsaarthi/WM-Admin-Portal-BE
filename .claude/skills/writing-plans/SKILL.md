---
name: writing-plans
description: Use before implementing any non-trivial feature, patch, API change, refactor or schema change, or anything touching 3+ files. Creates a step-by-step implementation plan with exact files, real code and a verification command per step, saved to plans/<DevName>-plan/.
user-invocable: true
---

# Writing Plans: WM-Admin-Portal-BE

## Overview

Before touching any code, build a complete implementation plan.
Each task takes 2 to 5 minutes. Every step has exact file paths, real code and a verification
command. No placeholders. No "implement later". No "similar to Task N".

The application is a **single-tenant modular monolith** on Java 21, Spring Boot 3.4+ and Flyway.
Plans organise work by **module**, and put the Flyway migration first.

**Announce at start:** "I'm using the writing-plans skill to create the implementation plan."

---

## Plan File Location

Plans live per developer (the team has several developers).

1. Determine `<DevName>`: read `currentUser` from `.claude/settings.local.json` if that file and
   key exist. Otherwise use `git config user.name`, capitalized.
2. Save to: `plans/<DevName>-plan/YYYY-MM-DD-<short-kebab-name>.md` (create the folders if they
   don't exist).

Example: `plans/Sarthak-plan/2026-11-04-add-leads-module.md`.

---

## Scope Check: Do This First

If the feature touches several independent modules (for example careers, leads and reporting),
split it into one plan per module. Each plan must produce working, testable, compilable software
on its own.

One cohesive module means one plan.

**Phases.** Group tasks into phases of at most **5 files**. Each phase ends with `mvn compile -q`
passing and a stop for approval (`executing-plans`).

---

## Research First: When Scope Is Uncertain

If the affected modules, existing patterns or entity relationships aren't clear from the
conversation, launch **Explore agents in parallel** (one message, several tool calls) before
mapping files:

- **1 agent** when the task is isolated to known files, or is a small targeted change.
- **Up to 3 agents** when scope is uncertain or several modules are involved. Give each a distinct
  focus, for example "find the existing service patterns in careers_in", "map the tables the
  applicant module owns", "check how similar list endpoints paginate".

Skip this for a change confined to 1 or 2 known files. Just read them.

---

## File Structure: Map Before Tasking

Before writing tasks, list every file that will be created or modified (paths under
`src/main/java/com/wealthmax/`):

```
Files to CREATE:
  src/main/resources/db/migration/V{N}__<description>.sql
  <module>/<area>/entity/<Entity>.java
  <module>/<area>/repository/<Entity>Repository.java
  <module>/<area>/service/<Area>Service.java                    (public API of the module)
  <module>/<area>/service/impl/<Area>ServiceImpl.java
  <module>/<area>/controller/<Area>Controller.java
  <module>/<area>/dto/request/<Area>CreateRequestDto.java
  <module>/<area>/dto/request/<Area>UpdateRequestDto.java
  <module>/<area>/dto/response/<Area>ResponseDto.java
  <module>/<area>/mapper/<Area>Mapper.java

Files to MODIFY:
  common/security/SecurityConfiguration.java   (the access rule, in the right position)
  [every existing file that changes]

Test files to CREATE (src/test/java/com/wealthmax/):
  <module>/<area>/service/impl/<Area>ServiceImplTest.java       (unit, Mockito)
  <module>/<area>/controller/<Area>ControllerTest.java          (MockMvc, real validation)
  <module>/<area>/repository/<Entity>RepositoryTest.java        (local PostgreSQL slice)
  <module>/<Module>SecurityRulesTest.java                       (real security chain)
  <module>/<Module>ModuleBoundaryTest.java                      (import scan, new modules)
```

No per-module exception classes. Every module throws the shared ones in `common.exception`
(`ResourceNotFoundException`, `DuplicateResourceException`, `InvalidDataException`,
`UnauthorizedException`). Add a new one there only if none fits, and say why in the plan.

---

## Plan Document Header

Every plan starts with:

```markdown
# [Feature Name] Implementation Plan

**Author:** [<DevName>, see Plan File Location above]

**Date:** [YYYY-MM-DD, must match the filename date]

**Time:** [HH:MM UTC]

**Goal:** [One sentence: what this builds]

**Module:** [e.g. careers, leads, or common]

**Tables owned by this module:** [table names, or none]

**Access level:** Public / Authenticated / Admin (per endpoint, listed)

**Migration required:** Yes (V{N}) / No

**Tech stack:** Java 21, Spring Boot 3.4+, PostgreSQL, Flyway, JUnit 5, Mockito

---
```

---

## Task Structure

Every task follows this format:

````markdown
### Task N: [What this task builds]

**Files:**
- Create: `exact/path/to/File.java`
- Modify: `exact/path/to/Existing.java`
- Test: `exact/path/to/FileTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void getJobDetailsById_whenMissing_throwsResourceNotFound() {
    when(careersINRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getJobDetailsById(99L))
        .isInstanceOf(ResourceNotFoundException.class);
}
```

- [ ] **Step 2: Run the test. Verify it FAILS.**

```bash
mvn test -Dtest=<TestClassName>#<methodName> -q
```
Expected: FAIL, with "method not defined" or "class not found".

- [ ] **Step 3: Write the minimal implementation**

```java
// exact code, no placeholders
@Override
public CareersResponseDto getJobDetailsById(Long id) {
    CareersINEntity entity = careersINRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Job doesn't exist with the given id: " + id));
    return careersDetailsMapper.toResponse(entity);
}
```

- [ ] **Step 4: Run the test. Verify it PASSES.**

```bash
mvn test -Dtest=<TestClassName>#<methodName> -q
```
Expected: PASS.

- [ ] **Step 5: Run the related tests. Verify nothing broke.**

```bash
mvn test -Dtest='Careers*' -q
```
Expected: all pass. (Surefire pattern. Keep the quotes in Git Bash. Database tests need the
local PostgreSQL running.)

- [ ] **Step 6: Commit (only when the user asks)**

```bash
git add <each file this task changed>
git commit -m "<message in the repository's style, see commit-message>"
```
````

This is a single-module Maven build. There is no `-pl <module>` flag. Narrow a run with `-Dtest`.

---

## WM-Admin-Portal-BE Task Order

For a new module or area, build in this order:

```
Task 1:  Flyway migration (schema first, everything depends on it)
Task 2:  Entity class, then mvn compile -q
Task 3:  Repository interface + repository test on local PostgreSQL
Task 4:  Request DTOs with Bean Validation (+ validation test)
Task 5:  Response DTOs + mapper (+ mapper test)
Task 6:  Service interface + read methods + unit tests
Task 7:  Service write methods + unit tests
Task 8:  Service remove (hard delete, soft delete or deactivate, per the plan) + unit test
Task 9:  Controller: GET endpoints + controller tests
Task 10: Controller: POST endpoint + controller test
Task 11: Controller: PUT/PATCH endpoint + controller test
Task 12: Controller: DELETE endpoint + controller test
Task 13: SecurityConfiguration rule (Public / Authenticated / Admin) + security-rules test
Task 14: Module boundary test (new modules)
Task 15: Module API doc under docs/ (api-contract skill)
```

Not every plan needs all 15. Skip what doesn't apply. Each task must compile without the next one
being done: that's why the DTOs come before the service that returns them.
For the test setup of each layer, see `test-driven-development` and `integration-testing`.

---

## Mandatory WM-Admin-Portal-BE Checks Per Task

Include these checks in every task they apply to:

**Migration task must verify:**
- Version is one higher than the highest file in `src/main/resources/db/migration/`.
- `id BIGSERIAL PRIMARY KEY`. `TIMESTAMPTZ` for timestamps. `VARCHAR(n)` for enums and short text.
- Named constraints and indexes (`uk_`, `fk_`, `idx_`), indexes only for real query patterns.
- `IF NOT EXISTS` / `IF EXISTS` on every create and drop.
- The tables belong to **this module** only: no foreign key to another module's table.
- No `DROP TABLE`, `DELETE FROM` or `TRUNCATE`, unless the plan says why.
- Verified on a fresh local database (`flyway-migration`, "Verify").

**Entity task must verify:**
```java
// A plain class (never a record): Long id with IDENTITY, enums as STRING, Instant timestamps,
// BigDecimal money. No relationship to another module's entity: store the Long id.
@Entity
@Table(name = "careers_in")
public class CareersINEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private JobStatusEnum status;
}
// The entity matches the Flyway migration: ddl-auto=validate will fail the start otherwise.
```

**Repository task must verify:**
```java
// Derived queries or named parameters only. Never concatenate input into a query.
List<CareersINEntity> findAllByStatus(JobStatusEnum status, Sort sort);
// Only this module's services use this repository.
```

**Service task must verify:**
```java
// Typed exceptions from common.exception; the service returns DTOs, never entities
throw new ResourceNotFoundException("Job doesn't exist with the given id: " + id);
// No Mailgun or Azure Blob call inside a transaction: send after commit.
// Another module's data is reached through its service interface, never its repository.
```

**Controller task must verify:**
- Takes a request DTO with `@Valid`, returns a response DTO (separate classes). `@Tag` present.
- `201 Created` for a create, `200 OK` otherwise. Lists use `PageResponseDto`.
- No `@CrossOrigin("*")`. No repository injected. No entity in or out.

**SecurityConfiguration task must verify:**
- The route has an explicit level. A public rule is narrow (method and path) and has a reason
  comment.
- The rule sits **above** any wildcard that also matches (first match wins).
- A test proves both directions: allowed as intended, rejected otherwise (403 without a token).

---

## No Placeholders Rule

These are plan failures. Never write them:

- "TBD", "TODO", "implement later"
- "Add appropriate validation" without the validation code
- "Handle exceptions" without the exception class and throw statement
- "Write tests for the above" without the test method code
- "Similar to Task N". Repeat the code: tasks get read on their own.
- "Use the standard WM-Admin-Portal-BE pattern" without the actual code

Every step that changes code shows the complete code for that step.

---

## Maven Test Commands Reference

```bash
# One test method
mvn test -Dtest=ClassName#methodName -q

# One test class
mvn test -Dtest=ClassName -q

# Every class matching a pattern (quote it in Git Bash)
mvn test -Dtest='Careers*' -q

# No database needed: boundary scan, security rules, controller and service unit tests
mvn test -Dtest='ModuleBoundaryArchitectureTest,*SecurityRulesTest,*ControllerTest,*ServiceImplTest' -q

# Whole suite (local PostgreSQL must be running for the database tests)
mvn test -q

# Compile check only (fast)
mvn compile -q

# With full output, when debugging
mvn test -Dtest=ClassName#methodName
```

---

## Self-Review: Run After Writing the Complete Plan

Before handing off, check:

1. **Spec coverage:** does every requirement have a task? List any gaps.
2. **Placeholder scan:** search for "TBD", "TODO", "implement later", "similar to". Fix all.
3. **Module boundaries:** does any task import another module's entity, repository, mapper or
   `service.impl`, or add a relationship or join across modules? Replace it with a service-interface
   call or a `common` promotion.
4. **Single tenant:** is there any `tenant`, `TenantContext` or `tenant_id`? Remove it.
5. **Access:** does every new route have an explicit rule, in the right position, and a test?
6. **Migration:** is every table or column change in a Flyway migration (version checked against
   the folder), and does the entity match it?
7. **Test first:** does every task write the test before the implementation?
8. **Compile chain:** can each task compile without the next one being done?
9. **Type consistency:** do the repository method names in one task match what the service calls
   in the next?
10. **Java level:** does any task use Java 21-only syntax while `maven.compiler.release` is still
    below 21?
11. **Phase size:** does every phase touch at most 5 files?

Fix issues inline. Then offer the handoff.

---

## Handoff After Saving the Plan

After saving to `plans/<DevName>-plan/`:

```
Plan saved to plans/<DevName>-plan/YYYY-MM-DD-<feature>.md

Ready to implement with the executing-plans skill: one phase at a time, stopping for approval
after each. Before calling any task or phase done, follow verification-before-completion.
Review the whole branch with review-branch after the final phase, before committing.
```
