---
name: api-designer
description: "Use when designing a new REST API endpoint in WM-Admin-Portal-BE before writing code: a new controller, an endpoint change, request/response DTO shape, pagination, error handling, who may call it, or a service contract in the Java 21 / Spring Boot 3.4+ single-tenant modular monolith. Complements api-contract (documents an endpoint after it exists) and writing-plans (sequences the implementation tasks)."
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
  domain: api-architecture
  triggers: API design, new endpoint, controller, DTO, request, response, pagination, list endpoint, service contract, access rule
  role: architect
  scope: design
  output-format: java-skeleton
  related-skills: modular-monolith, architecture-designer, coding-standards, api-contract, writing-plans
---

# API Designer: WM-Admin-Portal-BE

Designs the shape of an endpoint (service contract, DTOs, controller skeleton, access level,
exception mapping) before implementation starts. The output is a Java skeleton, not OpenAPI YAML.

Two skills are easy to confuse with this one:
- `api-contract` writes the front-end-facing API doc **after** the controller and DTOs exist.
- `writing-plans` sequences the **implementation tasks** once this skill has fixed the shape.

This is a **single-tenant** application. An endpoint never carries a tenant id and never filters
by tenant.

## Stack Facts (checked against source)

- Stack: Java 21, Spring Boot 4.1.2 (see `dependency-upgrade` for the supported lines), Flyway,
  Maven. (`pom.xml` is the source of truth.)
- **No response envelope.** A controller returns the response DTO directly in a `ResponseEntity`.
  Lists return `PageResponseDto<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`,
  `last`). A simple confirmation returns a small message DTO (`ApiMessageDto`, `JsonResponseDto`).
- Services throw typed exceptions from `common.exception` (`ResourceNotFoundException` 404,
  `DuplicateResourceException` 409, `InvalidDataException` 400, `UnauthorizedException` 401).
  `GlobalExceptionHandler` maps them. Controllers never build error responses.
- Errors come back as `ErrorResponse` (`timestamp`, `status`, `error`, `message`, `path`). A failed
  `@Valid` comes back as `ValidationErrorResponse` (`violations`: `field`, `message`).
- Access: URL rules (Public, Authenticated, `ADMIN`) plus `@PreAuthorize` on admin service
  methods (`@EnableMethodSecurity`, `docs/revamp-audit-report.md` 6.2). Decide both per endpoint.
- OpenAPI via springdoc: `@Tag` on the controller, `@ParameterObject` for query DTOs,
  `@Schema` on DTO fields.
- Every route starts with `/api/v1/`.
- Validation: `@Valid` on the controller parameter, Jakarta constraints on request DTO fields.

## Core Workflow

0. Load `coding-standards` and `modular-monolith`.
1. **Pick the module.** Which module owns the resource? New capability: a new module package
   (see `modular-monolith`). There are no flat layered packages here.
2. **Model the resource:** entity, operations, which table it owns, and **who may call each
   operation** (Public, Authenticated or Admin).
3. **Design the service contract:** interface signatures. This is the module's public API.
4. **Design the DTOs:** a request DTO for each write, a response DTO for each read, a smaller list
   response if the list view needs fewer fields, a search request DTO for filterable lists.
5. **Design the controller actions:** HTTP method, route, status code, `@Tag`.
6. **Map failure paths to exceptions and HTTP** (table below).
7. **Write the access rule** for `SecurityConfiguration`, and where it sits relative to existing
   wildcards (first match wins).
8. If an existing request or response shape changes, flag it for `api-contract` and the front-end
   team.

## Constraints

### MUST DO
- Return DTOs from controllers, never entities.
- Use **separate** request and response DTOs. Requests carry validation and no server-managed
  fields. Responses carry ids and server-set fields.
- Throw typed exceptions from services.
- Paginate every collection that can grow: a search request DTO extending `PageSearchRequestDto`
  in, `PageResponseDto<T>` out.
- Constrain every request DTO field that has a rule (`@NotBlank`, `@Size`, `@Email`, `@Min`...).
- Prefix routes with `/api/v1/`.
- Return `201 Created` for a POST that creates something, `200 OK` otherwise.
- Decide Public, Authenticated or Admin for every new route, and write the rule.
- Keep a module's DTOs, enums and service interface as its public surface. Everything else stays
  internal (see `modular-monolith`).

### MUST NOT DO
- Return or accept a JPA entity at the API boundary.
- Put `id`, `createdDate`, `jobId`, role or status fields the caller shouldn't set on a request
  DTO. The server resolves them.
- Build an error response in a controller.
- Return a raw `List<T>` or Spring's `Page<T>` from a growing collection.
- Add a tenant parameter, header or field. There is no tenant.
- Add a new `@CrossOrigin("*")`. CORS is configured centrally.
- Open a route with `permitAll()` without a one-line reason in `SecurityConfiguration`.

### Route style
`careers_in` uses action-style paths (`/createJob`, `/get/{id}`, `/update/{id}`,
`/delete/{id}`) and the front end already calls them. Stay consistent with an existing module's
style. For a brand-new module, say in the design whether you use resource-style REST
(`GET /resources/{id}`) or the action style, and why. Don't mix both in one module.

## Templates

### Controller Skeleton

```java
@RestController
@RequestMapping("/api/v1/{resource}")
@Tag(name = "{Resource}Controller", description = "{what this controller exposes}")
public class {Resource}Controller {

    private final {Resource}Service {camelResource}Service;

    public {Resource}Controller({Resource}Service {camelResource}Service) {
        this.{camelResource}Service = {camelResource}Service;
    }

    @PostMapping
    public ResponseEntity<{Resource}ResponseDto> create(@Valid @RequestBody {Resource}CreateRequestDto request) {
        return new ResponseEntity<>({camelResource}Service.create(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<{Resource}ResponseDto>> list(
            @Valid @ParameterObject {Resource}SearchRequestDto request) {
        return ResponseEntity.ok({camelResource}Service.list(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<{Resource}ResponseDto> get(@PathVariable("id") Long id) {
        // The service throws ResourceNotFoundException; GlobalExceptionHandler turns it into 404.
        return ResponseEntity.ok({camelResource}Service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<{Resource}ResponseDto> update(@PathVariable("id") Long id,
            @Valid @RequestBody {Resource}UpdateRequestDto request) {
        return ResponseEntity.ok({camelResource}Service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiMessageDto> delete(@PathVariable("id") Long id) {
        return ResponseEntity.ok({camelResource}Service.delete(id));
    }
}
```

### Service Skeleton

```java
public interface {Resource}Service {
    {Resource}ResponseDto create({Resource}CreateRequestDto request);       // DuplicateResourceException, InvalidDataException
    PageResponseDto<{Resource}ResponseDto> list({Resource}SearchRequestDto request);
    {Resource}ResponseDto getById(Long id);                                  // ResourceNotFoundException
    {Resource}ResponseDto update(Long id, {Resource}UpdateRequestDto request); // ResourceNotFoundException, InvalidDataException
    ApiMessageDto delete(Long id);                                           // ResourceNotFoundException
}

@Service
@Slf4j
public class {Resource}ServiceImpl implements {Resource}Service {

    private final {Resource}Repository {camelResource}Repository;
    private final {Resource}Mapper {camelResource}Mapper;

    public {Resource}ServiceImpl({Resource}Repository {camelResource}Repository,
                                 {Resource}Mapper {camelResource}Mapper) {
        this.{camelResource}Repository = {camelResource}Repository;
        this.{camelResource}Mapper = {camelResource}Mapper;
    }

    @Override
    public {Resource}ResponseDto getById(Long id) {
        return {camelResource}Mapper.toResponse(findOrThrow(id));
    }

    private {Resource}Entity findOrThrow(Long id) {
        return {camelResource}Repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("{Resource} doesn't exist with the given id: " + id));
    }
}
```

### DTO Skeleton

```java
// Request: validation, no server-managed fields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class {Resource}CreateRequestDto {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}

// Decide per module whether an update replaces every editable field or is partial (every field
// optional, whatever is sent must pass the create rules, as careers_in does). Write the choice
// into the API doc.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class {Resource}UpdateRequestDto {
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
}

// Response: no validation, carries the id and server-set fields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class {Resource}ResponseDto {
    private Long id;
    private String name;
    private String description;
    private Instant createdDate;
}

// Java 21 option: a request or response body can be a record instead (immutable, no Lombok,
// validation on the components). Use one style per module. See "Java 21 Idioms" in coding-standards.
// public record {Resource}CreateRequestDto(@NotBlank String name, @Size(max = 500) String description) { }

// Search: extends the shared page DTO (search, page, size). Stays a class: a record can't extend.
@Data
@EqualsAndHashCode(callSuper = true)
public class {Resource}SearchRequestDto extends PageSearchRequestDto {
    private String status;
}
```

### `PageResponseDto<T>` on the wire

```json
{
  "content": [ { "...": "..." } ],
  "page": 0,
  "size": 10,
  "totalElements": 150,
  "totalPages": 15,
  "last": false
}
```

```java
Page<{Resource}Entity> page = {camelResource}Repository.findAll(specification, pageable);
return PageResponseDto.from(page, {camelResource}Mapper::toResponse);
```

### Exceptions to HTTP

| Throw in the service | HTTP |
|---|---|
| `new ResourceNotFoundException("... doesn't exist with the given id: " + id)` | 404 |
| `new DuplicateResourceException("message")` | 409 |
| `new InvalidDataException("message")` | 400 |
| `new UnauthorizedException("message")` | 401 |
| `@Valid` failure (automatic) | 400, `ValidationErrorResponse` |
| Unreadable body, wrong path-variable type, missing query parameter (automatic) | 400, `ErrorResponse` |
| Anything unexpected (automatic) | 500, generic message |

Exceptions take a single message string. There is no error-code field, no `ForbiddenException`
and no `BusinessException`. A 403 comes from Spring Security when a rule denies the caller, not
from the service.

## Access Rules

Single tenant removes the tenant question, not the access question. Pick one level per route:

| Level | `SecurityConfiguration` rule |
|---|---|
| Public (website visitors) | `.requestMatchers(HttpMethod.POST, "/api/v1/{resource}/submit").permitAll()`, narrow, with a reason comment |
| Authenticated | `.requestMatchers("/api/v1/{resource}/**").authenticated()` |
| Admin only | `.requestMatchers("/api/v1/{resource}/**").hasAuthority("ADMIN")` |

Rules are matched top to bottom and the first match wins. Put the narrow public rule **above**
the wildcard, or the wildcard answers instead. Check which existing wildcard a new route would
land on before assuming it is protected.

## Output Checklist

1. Resource model: entity, owning module, owned table.
2. Service interface, with the exceptions per method.
3. DTOs: create, update (full or partial, stated), response, search.
4. Controller skeleton with the `/api/v1/...` route and `@Tag`.
5. Exception-to-HTTP table for each failure path.
6. Access level per route and the `SecurityConfiguration` rule, with its position.
7. Front-end contract impact for `api-contract`.
8. The Flyway migration (`flyway-migration`) for any new or changed table or column, listed as the
   first task of the plan.

## Reference Guide

| Topic | Where | Load when |
|---|---|---|
| Naming, exceptions, entities, DTOs | `coding-standards` skill | Always, first |
| Module placement, boundaries, data ownership | `modular-monolith` skill | Always |
| Module and ADR design | `architecture-designer` skill | Unsure which package owns it |
| Schema changes | `flyway-migration` skill | New table or column |
| Front-end API doc | `api-contract` skill | After the endpoint exists |
| Task sequencing | `writing-plans` skill | Before writing code |
