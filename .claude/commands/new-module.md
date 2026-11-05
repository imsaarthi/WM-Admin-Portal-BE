---
name: new-module
argument-hint: "[module-name] e.g. careers, leads, content"
---

Scaffold a complete new WM-Admin-Portal-BE module for $ARGUMENTS.

This touches more than 3 files, so CLAUDE.md requires a plan first (`writing-plans`) and phases of
at most 5 files. If no approved plan exists for this module, write one and stop for approval
instead of scaffolding.

Load `modular-monolith`, `coding-standards` and `api-designer` first.

Follow these steps exactly:

Step 1: Check the module
- The name is one lowercase word where possible (`identity`, `careers`, `leads`, `content`,
  `reporting`, `notification`; see `docs/revamp-audit-report.md` 6.1).
- If it ports a feature from the old repository (`Wealthmax-Admin-Portal-BE`), read that feature
  and `docs/admin-portal-module-documentation.md` first, and list the business rules to keep. Port
  the rules, never import or copy the old flat classes.
- State which tables the module will own. A table already owned by another module is a design
  error.

Step 2: Create the package structure
```
src/main/java/com/wealthmax/<module>/
  controller/
  dto/
    request/
    response/
  entity/
  mapper/
  repository/
  service/          (interfaces: the module's public API)
  service/impl/
  event/            (only if the module publishes events)
```
No module-level `exception/` package. Every module throws the shared exceptions in
`common/exception`. Add a new one there only if none fits.

Step 3: Write the service interface and DTOs first
- The interface is the module's public API to other modules.
- Request DTOs: Bean Validation on every field. Never `id`, `status` (except a dedicated
  status-change request), audit fields or roles.
- Response DTOs: ids, timestamps and server-derived fields. No validation annotations, no entity
  references.

Step 4: Create the Flyway migration
- `/add-migration <module> <description>`: next free version, `CREATE TABLE IF NOT EXISTS`,
  `BIGSERIAL`, `TIMESTAMPTZ`, named constraints, no tenant column, no foreign key to another
  module's table.

Step 5: Create the entity
- `Long` id with `GenerationType.IDENTITY` (BIGSERIAL). A class, never a record.
- Columns match the migration exactly (`ddl-auto=validate` fails startup otherwise).
- Relationships only inside the module. Another module's record is a `Long` id.
- `FetchType.LAZY` on every `@ManyToOne`; `@EqualsAndHashCode.Exclude` and `@ToString.Exclude` on
  every relationship.

Step 6: Create the repository and mapper
- Extend `JpaRepository<Entity, Long>`. `findById(id).orElseThrow(...)` is the normal by-id
  lookup (single tenant).
- List queries take a `Pageable`. Searches use a `Specification` in `repository/specification/`.
- One mapping style per module (see `coding-standards`).

Step 7: Create the service implementation
- Constructor injection (`@RequiredArgsConstructor`).
- `@Transactional` on writes, `@Transactional(readOnly = true)` on reads.
- `@PreAuthorize` on every admin use case.
- Email and other side effects go through an event handled after commit, never inside the
  transaction.
- Throw the typed exceptions from `common/exception`.

Step 8: Create the controller
- `@RestController`, `@RequestMapping("/api/v1/<resource>")`.
- `@Valid` on every `@RequestBody`.
- Return the response DTO in a `ResponseEntity`, `PageResponseDto<T>` for lists, 201 on create.
- No business logic. `@Tag` and `@Operation` for Swagger.

Step 9: Add the access rules
- A URL rule per route in the security configuration: Public (with a one-line reason),
  Authenticated or Admin, above any wildcard that would also match it.

Step 10: Write the tests
- Service unit tests (Mockito) and controller tests (standalone `MockMvc`).
- `<Module>SecurityRulesTest`: each route works for the intended caller and is rejected otherwise.
- Repository tests with `@PostgresDataJpaTest` for every query, constraint and sequence.

Step 11: Write the API doc
- `/update-contract <module>` → `docs/<module>-api.md`.

Step 12: Verify
```bash
mvn compile -q; echo "compile exit=$?"
mvn test -Dtest=ModuleBoundaryArchitectureTest -q; echo "archunit exit=$?"   # no database needed
mvn test -Dtest='com.wealthmax.<module>.**.*Test' -q; echo "module tests exit=$?"   # local PostgreSQL
```
`ModuleBoundaryArchitectureTest` fails the build if the module reaches into another module's
entity, repository, mapper or `service.impl` packages, if `common` depends on it, or if a
controller depends on a repository. Report what was created and every result, following
`verification-before-completion` ("not run" when local PostgreSQL is down).
