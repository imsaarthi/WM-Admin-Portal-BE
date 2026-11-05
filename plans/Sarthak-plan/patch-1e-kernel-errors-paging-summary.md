# Patch 1e1 to 1e3: Kernel errors and paging

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1e1, 1e2, 1e3

## Problem
No shared exceptions, no error contract, no paging type. The old application returned several error shapes and turned wrong URLs, methods and content types into 500s.

## Solution
- 1e1: `common/exception/{ResourceNotFoundException, DuplicateResourceException, InvalidDataException, UnauthorizedException}` (D-04).
- 1e2: `common/logging/MdcKeys` and `common/exception/GlobalExceptionHandler`, which extends `ResponseEntityExceptionHandler`. Every body is a `ProblemDetail` with `code` and `traceId`; validation adds `errors: [{field, message}]`.
  - Our exceptions: 404, 409, 400, 401. Spring Security `AccessDeniedException` is 403 and `AuthenticationException` is 401, both with fixed messages, so `@PreAuthorize` failures are never 500s.
  - Database: SQLState 23505 is 409 `DUPLICATE_RESOURCE`, 23503 is 409 `DATA_CONFLICT`. Anything else (including 23502 NOT NULL) is a 500. The constraint name is logged, never returned.
  - Anything unexpected is a 500 with a fixed message. 4xx is logged at WARN without a stack, 5xx at ERROR with one. Log lines carry the code and exception class, not the message.
- 1e3: `common/dto/PageResponseDto` (immutable record, `from(Page)`) and `PageGuard.pageable(...)`: page below 0, size outside 1 to 100, a sort field off the allow-list, or a direction other than asc or desc is a 400. Rejected values are never repeated in the message.

## Verification (real runs, 2026-11-05)
- RED each time for the right reason (missing classes only; every other import compiled).
- `mvn -B clean test`: **Tests run: 38, Failures: 0, Errors: 0, BUILD SUCCESS**
  (properties 5, clock 1, auditing 2, scan 2, `PageGuardTest` 7, `PageResponseDtoTest` 4, `GlobalExceptionHandlerTest` 17).
- The 17 handler tests include: unknown path 404, wrong method 405, wrong content type 415, unreadable JSON 400, the Phase 1 exit criterion "unknown path is a `ProblemDetail` 404 with a `traceId`", and the two log-level tests.

## Rulings
- 1d1/1e: the plan put `@ConfigurationPropertiesScan` on the application class. Moved it to `common/config/PropertiesScanConfig` (a `@Configuration`). Why: `@WebMvcTest` and `@DataJpaTest` slices load the application class, so every slice would bind `app.*` and `jwt.*` and fail without a full config. A test asserts the application class no longer carries the scan. Cost if wrong: one class.
- 1e2: `application.yml` line 59 changed. `spring.jackson.serialization.write-dates-as-timestamps` becomes `spring.jackson.datatype.datetime.write-dates-as-timestamps` (same value). Why: Jackson 3 moved the switch to `DateTimeFeature`, and the old key stopped the context from starting. This is the properties-migrator finding that 1h4 was going to look for. Non-secret key; no secret value was touched.
- 1e2: framework error codes are the HTTP status name (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `BAD_REQUEST`). The plan did not name them. ADR-011 updated to list them and `DATA_CONFLICT`.

## Debugging notes (so the next person doesn't repeat them)
- **Nested classes in a test are not component-scanned** (`TestTypeExcludeFilter`), so a probe controller declared inside a `@WebMvcTest` class needs an explicit `@Import`.
- **Stale compile.** After editing a test, `mvn test` once ran the old class and gave 17 phantom 404s. `mvn clean test` fixed it. Use `clean` when a result contradicts the source.
- **Framework errors arrive with `body == null`.** For 404/405/415 Spring builds the `ProblemDetail` after `handleExceptionInternal`, so the override builds it first with `ErrorResponse.updateAndGetBody`.
- A diagnostic test (`ZzDebugProbeTest`) was created to inspect the context and deleted afterwards.

## Limitations and follow-up
- Not handled specially (they fall back to the generic 400/500 paths): `HandlerMethodValidationException` (validation on `@RequestParam`/`@PathVariable`) and Jakarta `ConstraintViolationException` from `@Validated` services. Add handlers with tests when the first module needs them. No service exists yet.
- 413 (`MaxUploadSizeExceededException`) is covered by the base class but untested here. Phase 2f has the first upload.
- The handler test runs under the default profile `local`, so it reads `application-local.yml` if it exists. Step 1h1 moves tests to the `test` profile.
