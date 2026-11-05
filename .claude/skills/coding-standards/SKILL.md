---
name: coding-standards
description: Coding standards and patterns for Java 21, Spring Boot 3.4+, Spring Data JPA, Flyway and Maven in the WM-Admin-Portal-BE single-tenant modular monolith. Load before writing any new service, controller, entity, repository, mapper or DTO.
user-invocable: true
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
related-skills: modular-monolith, api-designer, security-reviewer, flyway-migration
---

# Coding Standards: WM-Admin-Portal-BE

How to write a class in this codebase. Module boundaries and data ownership are in
`modular-monolith`, schema changes in `flyway-migration`.

This is a **single-tenant** application: one organisation, one database. There is no tenant id,
tenant context or tenant filter to handle in any class.

**Stack: Java 21, Spring Boot 4.1.2 (ADR-008), Spring Data JPA (Hibernate 7.4), Flyway 12,
PostgreSQL, Maven.** Java 21 language features (records, pattern matching for `switch`,
record patterns, sequenced collections) are all available.

There is little code in this repository yet. Examples come from the old repository's `careers_in`
module (`Wealthmax-Admin-Portal-BE`), the convention this repository follows, until `careers` is ported
here. Follow these standards for new work, and say so in the plan when you deliberately differ.
Never copy the old repository's flat packages (`controller/`, `service/`, `entity/`...): they
predate these standards.

## When to Activate

- Starting a new service, controller, entity, repository, mapper or DTO
- Reviewing code for naming and structure consistency
- Refactoring to match established conventions

## Principles

- **Readability first.** Self-documenting names. Comment only the non-obvious WHY.
- **KISS and YAGNI.** The simplest solution that works. No speculative generality.
- **DRY, within a module.** Shared code used by one module stays in that module. A second module
  that needs it triggers a move into `common` (see `modular-monolith`).
- **Modules own their data.** Another module's entity, repository, mapper and `service.impl` are
  off limits.

---

## Naming Conventions

```java
// GOOD: PascalCase, no "I" prefix, standard suffixes (real names from careers_in)
public interface CareersINService { }
public class CareersINServiceImpl implements CareersINService { }
public interface CareersINRepository extends JpaRepository<CareersINEntity, Long> { }
public class CareersINController { }              // @RestController
public class CareersDetailsMapper { }             // @Component mapper
public class CareersCreateRequestDto { }          // request DTO: ...RequestDto
public class CareersResponseDto { }               // response DTO: ...ResponseDto
public class CareersINEntity { }                  // entity: ...Entity
public enum JobStatusEnum { ACTIVE, INACTIVE }    // enum: ...Enum

// BAD
public interface ICareersService { }              // no I prefix in Java
public class CareersManager { }                   // use ...ServiceImpl
public class CareersDto { }                       // say whether it is a Request or a Response
```

```java
// GOOD: camelCase methods and fields, final fields set through the constructor
private final CareersINRepository careersINRepository;

// GOOD: UPPER_SNAKE_CASE constants
private static final String JOB_ID_PREFIX = "WFA-IN-";

// BAD: magic values
if (status == 3) { }       // use an enum
```

**Packages.** Lowercase under `com.wealthmax`. A module is a top-level package
(`careers`, `leads`). Inside it, one package per layer (`controller`, `service`, `service.impl`,
`repository`, `entity`, `mapper`, `dto.request`, `dto.response`). Match the casing of the module
you're adding to.

---

## Error Handling

### Exception Hierarchy (`com.wealthmax.common.exception`)

Four exceptions, all `RuntimeException`s that take **one message string**. `GlobalExceptionHandler`
(`@ControllerAdvice`) maps them to HTTP. Services **throw**. They never build an error response.

| Exception | HTTP | When to throw |
|---|---|---|
| `ResourceNotFoundException(message)` | 404 | The row doesn't exist |
| `DuplicateResourceException(message)` | 409 | A unique value or business duplicate (for example the applicant cooling period) |
| `InvalidDataException(message)` | 400 | A rule Bean Validation can't express (for example max experience below min) |
| `UnauthorizedException(message)` | 401 | Not authenticated, or bad credentials |

`GlobalExceptionHandler` also turns these into HTTP without help from you:
`IllegalArgumentException` (400), `DataIntegrityViolationException` (409 on a duplicate key, else
400), unreadable request bodies (400), a path or query value of the wrong type (400), a missing
required query parameter (400), `@Valid` failures (400), and anything unexpected (500 with a
generic message; the stack trace is only logged).

The error body is `ErrorResponse` (`timestamp`, `status`, `error`, `message`, `path`). A failed
`@Valid` returns `ValidationErrorResponse` instead, with a `violations` list of `field` and
`message`.

**There is no error-code field and no `ForbiddenException`, `BadRequestException` or
`BusinessException`.** Don't invent them. If a case needs a new exception, add it to
`common.exception`, give it a handler, and say why in the plan.

The message reaches the client verbatim, so write it for a person and keep internals out of it.

```java
// GOOD (real code): throw, let GlobalExceptionHandler answer
return careersINRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Job doesn't exist with the given id: " + id));

// BAD: building the error response by hand in a service or controller
return ResponseEntity.status(404).body(new ErrorResponse(...));
```

---

## Authorization

Access has two layers (`docs/revamp-audit-report.md` 6.2). The first is URL rules in
`common/security/SecurityConfiguration`:

- `permitAll()`: public. Use the narrowest method and path, with a comment saying why.
- `authenticated()`: any signed-in user (a valid JWT).
- `hasAuthority("ADMIN")`: administrators. Use `hasAuthority`, not `hasRole`.

`SecurityConfiguration` takes the **first** matching rule, so a narrow rule must come before the
wildcard that would also match it. A new endpoint under an existing wildcard silently inherits that
wildcard's access level, so check which rule it lands on.

The second is method security (`@EnableMethodSecurity`). Every admin use case also carries
`@PreAuthorize` on the **service** method, because services get called from more than one place
and a URL rule like `authenticated()` lets any signed-in user through.

---

## Controller Pattern

Controllers are thin: parse and validate the request, call **one** service method, return the
result. Routes start with `/api/v1/`, for example `/api/v1/careers/IN`.

There is **no response envelope.** A controller returns the response DTO directly. Lists return
`PageResponseDto<T>`, and simple confirmations return a small message DTO.

```java
@RestController
@RequestMapping("/api/v1/careers/IN")
@Tag(name = "CareersINController", description = "Careers IN api for structured job postings")
public class CareersINController {

    private final CareersINService careersINService;

    public CareersINController(CareersINService careersINService) {
        this.careersINService = careersINService;
    }

    @PostMapping("/createJob")
    public ResponseEntity<CareersResponseDto> saveJobDetails(@Valid @RequestBody CareersCreateRequestDto request) {
        return new ResponseEntity<>(careersINService.saveJobDetails(request), HttpStatus.CREATED);
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<CareersResponseDto> getJobDetailsById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(careersINService.getJobDetailsById(id));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiMessageDto> deleteJobDetails(@PathVariable("id") Long id) {
        return ResponseEntity.ok(careersINService.deleteJobDetailsById(id));
    }
}
```

Controllers must **never**:
- inject a repository or `EntityManager`
- return or accept an entity (always a DTO)
- catch an exception that should reach `GlobalExceptionHandler`
- build an error response by hand
- accept server-managed fields from the client (`id`, `createdDate`, `jobId`, status the caller
  shouldn't set)

Create returns `201 Created`. Everything else returns `200 OK`.

**CORS.** `careers_in` controllers carry `@CrossOrigin("*")`, which the audit report flags as too
wide. CORS is configured in one place (`ApplicationConfig`'s `CorsFilter`). Don't add a new
`@CrossOrigin("*")` to a new controller.

---

## Package Structure (per module)

```
<module>/<sub-area>/
├── controller/     # @RestController, thin HTTP layer only
├── dto/
│   ├── request/    # ...RequestDto: validation, no server-managed fields
│   └── response/   # ...ResponseDto: no validation
├── entity/         # @Entity classes and the enums they use
├── mapper/         # @Component mappers (entity <-> DTO)
├── repository/     # Spring Data interfaces
├── service/        # interfaces: the module's public API
└── service/impl/   # @Service implementations
```

`entity`, `repository`, `mapper` and `service.impl` are **internal** to the module. Only
`service` interfaces, DTOs and enums are visible to other modules (see `modular-monolith`).

---

## Domain Entities

The module convention, from `CareersINEntity`:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "careers_in")
public class CareersINEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String jobId;

    @Enumerated(EnumType.STRING)
    private JobStatusEnum status;

    private LocalDate datePosted;

    private Instant createdDate;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;
}
```

Rules:
- `Long id` with `GenerationType.IDENTITY` (BIGSERIAL), never UUID.
- Enums with `@Enumerated(EnumType.STRING)`, never ordinal.
- Timestamps as `Instant`, dates as `LocalDate`. Money as `BigDecimal`, never `double`.
- **Every entity change ships with a Flyway migration** (`flyway-migration`). Hibernate validates
  the entity against the schema (`ddl-auto=validate`), it doesn't create or alter tables. Mirror
  the schema's constraints with `@Column(...)` where it helps.
- Set `createdDate` and similar server fields in the **service**. There is no shared base entity
  and no JPA auditing today.
- Collections of values: `@ElementCollection` with `@BatchSize(size = 50)` to avoid N+1 loads.
- `@Data` generates `equals`, `hashCode` and `toString` over every field. On an entity that has a
  relationship, that can trigger lazy loading or loop forever. Use `@Getter` and `@Setter`
  instead, or exclude the relationship fields.
- **No relationship to another module's entity.** Store its `Long` id (see `modular-monolith`).

---

## Pagination

Input: a search request DTO that extends `PageSearchRequestDto` (`search`, `page` from 0, `size`
from 1 to 100, default 10). Bind it with `@Valid @ParameterObject` on the controller.

Output: `PageResponseDto<T>`, **not** Spring's `Page<T>`:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 150,
  "totalPages": 15,
  "last": false
}
```

```java
// GOOD (real code): the search is a JPA Specification, the page comes from PageRequest
Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), NEWEST_JOB_ID_FIRST);
Page<CareersINEntity> jobs = careersINRepository.findAll(search.and(department), pageable);
return PageResponseDto.from(jobs, careersDetailsMapper::toResponse);

// BAD: manual skip and limit, or returning a raw List or Spring's Page
```

`PageResponseDto` lives in `common.dto` from the start, because every module's list endpoint
needs it (the old repository kept it in `careers_in.common.dto`).

---

## Repository and Data Access

Services inject `XxxRepository extends JpaRepository<T, Long>` (plus `JpaSpecificationExecutor<T>`
for searchable lists). Controllers never do.

```java
@Repository
public interface CareersINRepository
        extends JpaRepository<CareersINEntity, Long>, JpaSpecificationExecutor<CareersINEntity> {

    List<CareersINEntity> findAllByStatus(JobStatusEnum status, Sort sort);

    // Next number of the WFA-IN-#### job id, from a database sequence
    @Query(value = "select nextval('careers_in_job_number_seq')", nativeQuery = true)
    long nextJobNumber();
}
```

- Single tenant, so `findById(id).orElseThrow(...)` is the normal by-id lookup.
- Derived queries and named parameters only. Never build JPQL or SQL by concatenating input.
- For search and filter lists, use the module's `SearchSpecifications` pattern.
- A repository is only used by its own module's services.

---

## Mapper Pattern

Every module maps by hand in a `@Component` class under `mapper/`. `careers_in` does this
(`CareersDetailsMapper`, `ApplicantDetailsMapper`). MapStruct 1.6.3 is in
`pom.xml` with its processor configured, and the audit report (6.3) proposes it for new modules.
Which one wins is a team decision. Never mix the two in one module. ModelMapper is not used here.

```java
@Component
public class CareersDetailsMapper {
    public CareersINEntity toEntity(CareersCreateRequestDto request) { ... }
    public CareersResponseDto toResponse(CareersINEntity entity) { ... }
}
```

Rules:
- Match the module you're in. Don't mix styles inside one module.
- The mapper never sets server-managed fields (`id`, `jobId`, `datePosted`). The service does.
- Mapping never happens in a controller.
- A new module may choose MapStruct. Say so in the plan, so reviewers expect it.

---

## Service / ServiceImpl Pattern

```java
public interface CareersINService {
    CareersResponseDto saveJobDetails(CareersCreateRequestDto request);
    PageResponseDto<CareersResponseDto> getAllJobDetails(CareersSearchRequestDto request);
    CareersResponseDto getJobDetailsById(Long id);
}

@Service
@Slf4j
public class CareersINServiceImpl implements CareersINService {

    private final CareersINRepository careersINRepository;
    private final CareersDetailsMapper careersDetailsMapper;

    public CareersINServiceImpl(CareersINRepository careersINRepository,
                                CareersDetailsMapper careersDetailsMapper) {
        this.careersINRepository = careersINRepository;
        this.careersDetailsMapper = careersDetailsMapper;
    }

    @Override
    public CareersResponseDto saveJobDetails(CareersCreateRequestDto request) {
        CareersINEntity entity = careersDetailsMapper.toEntity(request);
        entity.setDatePosted(LocalDate.now());
        entity.setJobId(generateJobDetailsId());
        entity.setCreatedDate(Instant.now());
        CareersINEntity saved = careersINRepository.save(entity);
        log.info("Created Job IN with id {}", saved.getId());
        return careersDetailsMapper.toResponse(saved);
    }
}
```

- The **interface** is the module's public API. The `Impl` is internal.
- A write that does more than one change (several tables, or a write plus a check) needs one
  transaction. `ApplicantINServiceImpl` uses a `TransactionTemplate` so it can send the email
  **after commit**. Elsewhere, `@Transactional` on the service method is fine. Don't call
  Mailgun or Azure Blob inside a transaction.
- Self-invocation (`this.method()`) skips the Spring proxy, so `@Transactional` on the called
  method does nothing.
- Log ids, never personal data (names, emails, phone numbers, CTC, resumes).

---

## DTO Patterns

**Separate request and response DTOs.** Every endpoint takes a request DTO and returns a different
response DTO.

```java
// Request: validation lives here, and there are NO server-managed fields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailsRequestDto {

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    private String department;
    private LocalDate applyBy;
}

// Response: no validation, carries id and server-set fields
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareersResponseDto { /* id, jobId, datePosted, createdDate, ... */ }
```

- Validate with Jakarta constraints (`@NotBlank`, `@Size`, `@Email`, `@Min`...) and a clear
  `message`. Controllers use `@Valid`.
- An update request is its own class (`CareersUpdateRequestDto`): fields are optional, and
  whatever is sent must pass the same rules as on create. Write the choice (partial or full
  replace) into the API doc.
- Nested sections of a big form are nested DTOs (`JobDetailsRequestDto` inside
  `CareersCreateRequestDto`).
- Never put server-managed or privileged fields on a request DTO: `id`, `createdDate`, `jobId`,
  `role`, `status` the caller shouldn't choose.

---

## Java 21 Idioms

Use them where they make code shorter or safer, never for show.

- **Records for new request and response DTOs.** A record is immutable, needs no Lombok, and
  Jakarta validation works on its components:

  ```java
  public record JobDetailsRequestDto(
          @NotBlank(message = "Job title is required") String jobTitle,
          String department,
          LocalDate applyBy) { }
  ```

  Don't mix records and Lombok classes inside one module's DTOs. `careers_in` uses Lombok classes,
  so match it there. A search DTO that extends `PageSearchRequestDto` stays a class, because a
  record can't extend another class. Entities are **never** records (JPA needs a no-arg
  constructor and mutable state).
- `Stream.toList()` instead of `collect(Collectors.toList())` (already used in `PageResponseDto`).
- Text blocks (`"""`) for multi-line JPQL or SQL strings.
- Pattern matching for `instanceof`, and `switch` with patterns, once the compiler release is 21.
- `var` only where the type is obvious from the right-hand side.
- **Virtual threads** (`spring.threads.virtual.enabled=true`, Boot 3.2+) are an option for
  blocking I/O such as Mailgun and Azure Blob calls. They change threading behaviour for the
  whole application, so adopt them as a deliberate, tested decision (ADR), not as a side effect.

---

## Dependency Injection

```java
// GOOD: constructor injection (explicit constructor, or Lombok @RequiredArgsConstructor)
private final CareersINRepository careersINRepository;

// BAD: field injection hides dependencies and is hard to test
@Autowired
private CareersINRepository careersINRepository;
```

---

## Configuration and Properties

- Non-secret settings go in `application*.yml`, and a new `@ConfigurationProperties`
  prefix gets its keys in the same commit. Flyway settings (`spring.flyway.*`) and
  `spring.jpa.hibernate.ddl-auto=validate` are non-secret code-side config: ours to set.
- **Secrets in `application*.yml` belong to DevOps. This team doesn't edit, copy or quote
  them.** Never paste a secret value into code, a doc, a commit message, a plan or chat. Cite the
  file and key only. (Code that reads a secret is ours to improve. The value is not.)

---

## Code Smell Detection

**Long methods.** Split anything over about 40 lines into focused private methods.

**Deep nesting.** Use guard clauses.

**Module boundary violations.** Another module's `entity`, `repository`, `mapper` and
`service.impl` are off limits. Its service interface, DTOs and enums are not.

```java
// BAD: reaching into another module's data layer (fails the module boundary test)
import com.wealthmax.careers.repository.JobApplicationRepository;

// GOOD: call the other module's service interface with ids and DTOs
careersINService.getJobDetailsById(jobId);
```

**N+1 queries.**

```java
// BAD: one query per id
for (Long id : ids) { results.add(repo.findById(id).orElseThrow()); }

// GOOD: one query
List<CareersINEntity> results = repo.findAllById(ids);
```

---

## Reference Guide

| Topic | Where | Load when |
|---|---|---|
| Module layout, boundaries, data ownership | `modular-monolith` skill | Always for a new or moved class |
| Endpoint design | `api-designer` skill | New controller or DTO set |
| Database schema changes | `flyway-migration` skill | Any table or column change |
| API doc for the front end | `api-contract` skill | After the endpoint exists |
| What the system does today, all endpoints | `docs/admin-portal-module-documentation.md` | Before touching an unfamiliar area |
| Known problems and the revamp plan | `docs/revamp-audit-report.md` | Planning refactors |

**Remember:** every feature is a vertical slice. Own your data, expose only DTOs and service
interfaces, throw typed exceptions from services, return DTOs from controllers, and never reach
into another module's internals.
