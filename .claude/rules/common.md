---
paths:
  - "common/**/*.java"
  - "src/main/java/**/common/**/*.java"
---

# Common Module Rules

You are editing shared infrastructure code. This module is the foundation everything else builds
on. Keep it clean and free of feature dependencies. The application is single tenant: nothing in
here knows about tenants.

## What belongs here (`docs/revamp-audit-report.md` 6.1)
- `common.entity`: base entity classes (`BaseEntity`, `AuditableEntity`). They hold ids,
  timestamps and audit fields.
- `common.security`: the filter chains, JWT validation, `UserPrincipal`, method security
  configuration, JSON 401 and 403 handlers.
- `common.exception`: the shared exception classes and the exception advice.
- `common.dto`: `PageResponseDto` and other response types every module uses.
- `common.storage`: the file storage interface and its Azure Blob implementation.
- `common.audit`: the append-only audit log.
- `common.logging`: the `traceId` / MDC filter.
- `common.config`: Jackson, CORS, OpenAPI, JPA auditing, `Clock`, typed `@ConfigurationProperties`.
- `common.util` and `common.constant`: small shared helpers.

## What does NOT belong here
- Business logic of any kind.
- Feature-specific code, entities or tables (jobs, applications, leads, newsletters...): they
  belong in their own module.
- Imports from any feature module (`identity`, `careers`, `leads`, `content`, `reporting`,
  `notification`).
- Anything imported from the old repository (`com.wealthmax.adminPortal`). Port the behaviour.
- Controllers. Shared code has no endpoints of its own.

## Dependency rule
- Common has ZERO dependencies on feature modules. `ModuleBoundaryArchitectureTest` enforces this,
  and the J2 hook check warns as you edit.
- Don't add an exception to the ArchUnit rules. Invert the dependency instead, so the feature
  module calls common (or registers itself with it).
- Common may depend on Spring Boot, Hibernate, the JWT library and the Azure Blob SDK only.
- If you find yourself importing from a feature module in common, stop and restructure.

## Promote on the second use
Code used by one module stays inside that module. Move it here only when a second module needs it,
never by importing a sibling's internals or copy-pasting it.

## Stability
- Common is the most imported module: breaking changes here break everything.
- Be conservative with changes to base classes and utilities.
- Any change to base entity fields needs a Flyway migration for every affected table.
- Run `mvn test -Dtest=ModuleBoundaryArchitectureTest` (no database needed) after any change here.
