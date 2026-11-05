---
name: modular-monolith
description: Keep WM-Admin-Portal-BE a single-tenant modular monolith. Use when adding or changing a module, deciding where a class belongs, letting one module use another, sharing code through common, or reviewing imports and table ownership. Replaces the old multi-tenant-isolation skill, because this application serves one organisation and so has no tenant id, tenant filter or per-request tenant context.
user-invocable: true
---

# Modular Monolith (Single Tenant): WM-Admin-Portal-BE

One deployable Spring Boot application, one database, **one organisation**. Nothing here is
multi-tenant: no `tenant_id` column, no `TenantContext`, no Hibernate filter, no tenant listener.
If a snippet from another project mentions any of those, don't port it.

What keeps a monolith from turning into a ball of mud is **module boundaries**. This skill is how
to draw and defend them.

Base package: `com.wealthmax`.

---

## What a Module Is

A **module** is a top-level feature package under `com.wealthmax` that owns one
business capability end to end: its HTTP endpoints, its logic, its tables.

The modules planned for this repository (`docs/revamp-audit-report.md` 6.1) are `identity`,
`careers`, `leads`, `content`, `reporting` and `notification`, plus the shared `common`. Each one
uses the layout of the old repository's `careers_in` module, which is the pattern source until
`careers` is ported here:

```
com.wealthmax.careers
├── controller/
├── dto/request/, dto/response/
├── entity/
├── mapper/
├── repository/       (+ specification/ for search)
├── service/          interfaces: the module's public API
│   └── impl/
└── event/            optional: events this module publishes
```

A module with two aggregates may split into sub-areas, each with the same layers (the old
`careers_in` had `applicant/`, `careers/` and a module-internal `common/`).

Layer rules inside a module (or a sub-area of one):

| Package | Holds | Visible to other modules? |
|---|---|---|
| `controller` | `@RestController`, HTTP only | No (nobody calls a controller) |
| `service` | Interfaces: the module's **public API** | **Yes** |
| `dto.request`, `dto.response` | Request and response objects | **Yes** (as service parameters and results) |
| enums used in DTOs | Status and type enums | Yes |
| `service.impl` | Business logic | **No** |
| `repository` | Spring Data repositories | **No** |
| `entity` | JPA entities | **No** |
| `mapper` | Entity and DTO mapping | **No** |

There is no flat layered code in this repository, and none is to be added. Every class lives
in `common` or in a module.

---

## Dependency Rules

1. **A module depends on `common` and on other modules' public API only.** Never on another
   module's `entity`, `repository`, `mapper` or `service.impl`.
2. **`common` depends on no module.** It holds shared kernel code only
   (`docs/revamp-audit-report.md` 6.1): `common.exception` (domain exceptions and the exception
   advice), `common.dto` (`PageResponseDto`), `common.entity` (`BaseEntity`, `AuditableEntity`),
   `common.config`, `common.security`, `common.storage`, `common.audit` and `common.logging`.
3. **Nothing is imported from the old repository.** Port the behaviour into the owning module
   or into `common`. There is no allow-list of legacy classes here.
4. **No dependency cycles** between modules. If A and B need each other, one of them is
   mis-drawn: merge them, or move the shared piece down into `common`.
5. **Promote on the second use.** Code used by one module stays inside it. When a second module needs it, move it into
   `common` rather than importing a sibling's internals, or copy-pasting it.

Don't add an exception to the ArchUnit rules to make a build pass. Needing one means the shared
code belongs in `common`.

---

## Data Ownership

- **A table belongs to exactly one module.** Only that module's repositories read or write it.
- **No JPA relationships across modules.** No `@ManyToOne` or `@OneToMany` to another module's
  entity. Store the other record's `Long` id in a plain column and fetch it through that
  module's service interface when you need it.
- **No cross-module joins** in JPQL or native SQL. If a screen needs data from two modules,
  the calling module's service combines two service calls (or reads a purpose-built read
  model).
- **Shared tables are a smell.** Two modules writing one table means the boundary is wrong.
- **Schema changes are Flyway migrations**, one per change, and a migration only touches tables
  its own module owns (`flyway-migration`). Name the module in the migration's header comment.
- Reporting that must read many modules' data (for example Excel export) goes through each
  module's service interface, or is its own module with its own read-only queries. Say which in
  the design.

---

## Talking Between Modules

Pick the lightest mechanism that fits:

1. **Call the other module's service interface** with ids and DTOs. Use it when the caller needs
   the answer now.
2. **Publish a Spring application event** (`ApplicationEventPublisher`) when the caller shouldn't
   wait for, or depend on, the reaction: sending an email, writing an audit entry. Handle it with
   `@TransactionalEventListener(phase = AFTER_COMMIT)` so a rolled-back write never sends mail.
   (The codebase doesn't use events yet. When you introduce the first one, say so in the plan.)
3. Never: static helpers that reach into another module's repository, a shared "god" service, or
   reflection.

Vendor SDKs (Mailgun, Azure Blob, Apache POI, the JWT library) sit behind a service interface so a module depends on
the interface, not the vendor client.

---

## Single-Tenant Access Control

Single tenant removes the tenant question, not the access question. For every endpoint decide
which of three levels applies, and make `SecurityConfiguration` say so:

| Level | Meaning | How it's expressed |
|---|---|---|
| Public | Website visitors, no token | `permitAll()` on the exact method and path, with a comment on why |
| Authenticated | Any signed-in portal user | `authenticated()` |
| Admin | Portal administrators | `hasAuthority("ADMIN")` (not `hasRole`: `User.getAuthorities()` returns the literal `ADMIN`) |

- `SecurityConfiguration` takes the **first** matching rule. Put narrow rules (one method and
  path) above wildcard rules, or the wildcard wins.
- Admin use cases also carry `@PreAuthorize` on the service method, with `@EnableMethodSecurity`
  (`docs/revamp-audit-report.md` 6.2). A URL rule alone lets any signed-in user through.
- Looking up a row by id is just `repository.findById(id).orElseThrow(() -> new
  ResourceNotFoundException(...))`. There is no tenant to scope it to. The one extra check is
  **ownership**: if a record belongs to a particular user (their own profile, their own draft),
  compare it with the authenticated user before returning or changing it.
- `@Scheduled` jobs run without a request. Nothing needs setting up for them. They
  still must not call another module's repository.

---

## Enforcement

The boundary is only real if a failing test defends it.

- **`src/test/java/com/wealthmax/ModuleBoundaryArchitectureTest.java`** (ArchUnit, ADR-005). One
  test applies the rules to every top-level package under `com.wealthmax`, so a new module is
  covered the day it is created:
  - nothing outside a module depends on its `entity`, `repository`, `mapper` or `service.impl`
    packages;
  - `common` depends on no feature module;
  - controllers never depend on a repository or an entity;
  - vendor SDKs stay at the edge (Azure Blob in `common.storage`, Mailgun in `notification`,
    Apache POI in `reporting`, the JWT library in `common.security`);
  - no cycles between modules (`slices().matching("com.wealthmax.(*)..").should().beFreeOfCycles()`).

  It needs no database and no Spring context:

  ```bash
  mvn test -Dtest=ModuleBoundaryArchitectureTest
  ```

- Write it before the first module (Phase 1), so the rules hold from day one.
- Don't add a per-module exception to make a build pass. If a rule is wrong, change it in a plan and
  say why.

---

## Adding a New Module

1. Name it after the capability, one lowercase word where possible (`careers`, `leads`).
   Create `com.wealthmax.<module>/` with only the layers you need.
2. Write the **service interface** and the request and response DTOs first: that is the module's
   public API.
3. Write the Flyway migration for the module's tables, then create the entity, repository, mapper
   and `service.impl` behind the interface.
4. Add the controller, with the route under `/api/v1/<resource>`.
5. Add the `SecurityConfiguration` rule (Public / Authenticated / Admin), above any wildcard that
   would otherwise catch it.
6. Run `ModuleBoundaryArchitectureTest`. It covers the new module without changes.
7. Write the module's API doc under `docs/` (see `api-contract`).
8. Run `mvn compile -q` and the module's tests (see `verification-before-completion`).

## Porting a Feature From the Old Repository

1. Read the feature in the old repository (`Wealthmax-Admin-Portal-BE`) and in
   `docs/admin-portal-module-documentation.md`, and list its business rules. Port the rules, not
   the code: the old classes predate these standards.
2. Design the API fresh (`api-designer`, `docs/revamp-audit-report.md` 6.3 and 6.4). The front end
   switches at the cutover, so paths and response shapes may change. Record every change in the
   module's API doc.
3. Write the Flyway migration for the module's tables, then the entity, repository, mapper and
   `service.impl`.
4. Write the **data-migration script** that copies the feature's rows from the old database into
   the new tables, and test it against a copy of production on staging (ADR-009).
5. Run `ModuleBoundaryArchitectureTest` and the module's tests.

---

## Common Mistakes

```java
// WRONG: reaching into another module's data layer
import com.wealthmax.careers.repository.JobApplicationRepository;

// WRONG: a JPA relationship across modules
@ManyToOne
private CareersINEntity job;          // in another module's entity

// RIGHT: keep the id, ask the owning module through its service interface
private Long jobId;
CareersResponseDto job = careersINService.getJobDetailsById(jobId);

// WRONG: a controller or service returning an entity, so another module couples to table shape
public CareersINEntity getJob(Long id)

// RIGHT: return a response DTO
public CareersResponseDto getJobDetailsById(Long id)

// WRONG: porting multi-tenant code into a single-tenant app
TenantContext.getCurrentTenantId();   // does not exist here
@Filter(name = "tenantFilter")        // does not exist here
```

## Review Checklist

- [ ] Every new class sits in the right layer package of the right module.
- [ ] No import of another module's `entity`, `repository`, `mapper` or `service.impl`.
- [ ] No JPA relationship, join or foreign-key mapping across modules.
- [ ] Each table has one owning module.
- [ ] Anything shared by two modules is in `common`, not imported from a sibling.
- [ ] The module's boundary test exists and passes.
- [ ] The endpoint's access level (Public / Authenticated / Admin) is in `SecurityConfiguration`,
      in the right order.
- [ ] No `tenant`, `TenantContext`, `tenant_id` or tenant filter anywhere in new code:
      `git grep -in tenant -- src` prints nothing from this change.
