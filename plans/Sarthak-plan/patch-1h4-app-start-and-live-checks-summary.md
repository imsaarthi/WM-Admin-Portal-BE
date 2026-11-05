# Patch 1h4: Whole-application start, properties migrator, live JSON log check

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1h4

## Problem
Only test slices had started so far. Nothing proved the whole application boots on Spring Boot 4.1.2, that the Phase 1 exit criterion (unknown path is a `ProblemDetail` 404 with a `traceId`) holds, or that the JSON log config works.

## Solution
- `src/test/java/com/wealthmax/WmAdminPortalApplicationTests.java`: `@SpringBootTest` + `@AutoConfigureMockMvc`, `test` profile, local-only guard, `@Tag("database")`, and a test-only permit-all `SecurityFilterChain` (Boot's default would answer 401 to everything until Phase 2a). Three tests: the context starts; an unknown path is a `ProblemDetail` 404 with `code NOT_FOUND`, a `traceId` in the body equal to the `X-Trace-Id` header; a well-formed `X-Request-Id` comes back as the trace id.
- `spring-boot-properties-migrator` was added to `pom.xml` for one run and **removed** (checked: 0 occurrences left). It reported four renamed keys, and a Hibernate warning showed one setting that is no longer needed. Fixed in `application.yml` (non-secret keys only):
  - `server.error.include-*` moved to `spring.web.error.include-*`.
  - `hibernate.dialect` removed.

## Verification (real runs, 2026-11-05)
- `WmAdminPortalApplicationTests`: 3/3 on the first start. The app boots with Flyway, auditing, springdoc 3.2.0, actuator and the filters.
- After the key fixes: 0 migrator report entries, 0 Hibernate dialect warnings.
- `mvn -B clean test`: **Tests run: 104, Failures: 0, Errors: 0, BUILD SUCCESS**.
- **Live check of the packaged app** (`java -jar target/wmadminportalbe.jar --spring.profiles.active=prod`, port 18081, a scratch database `wm_json_check`, synthetic property values):
  - Response to `GET /no-such-path`: `401` with `X-Trace-Id: json-check-0001`. The 401 is Boot's default security (Phase 2a is not built yet). The header proves `TraceIdFilter` runs before the security chain.
  - Stdout was JSON (ECS): `{"@timestamp":...,"log":{"level":"WARN","logger":"...RequestLogFilter"},...,"message":"GET UNMATCHED -> 401 in 47 ms","traceId":"json-check-0001",...}`. The traceId is a field, the route is `UNMATCHED`, and the raw path never appears.

## Rulings
- `spring.web.error.include-message` and `include-binding-errors` are now `never` (they were `always`). The two keys came from the template this repository was copied from, they make Boot's fallback `/error` page print exception messages, and `GlobalExceptionHandler` already defines the error contract. Cost if wrong: change two words back.

## NOT done: needs you
- **The app I started for the live check is still running.** My `kill` could not stop the native Java process, and the tool that would have inspected it was blocked by the permission classifier, so I left it alone. PID **29288**, port **18081**, started from `target/wmadminportalbe.jar`. Stop it with `taskkill /PID 29288 /F`.
- **The scratch database `wm_json_check` still exists**, because the running app holds sessions on it. After stopping the app: `DROP DATABASE wm_json_check;` (on the local PostgreSQL 18, port 5432).

## Limitations and follow-up
- Logback prints three harmless warnings from the jar: `scan="true"` cannot watch a file inside a jar, and the `CONSOLE` appender is unused under `prod`. Dropping `scan="true"` is a one-word cleanup for a later step.
- MapStruct prints "options not recognized" on every build until the first `@Mapper` exists. It goes away by itself.
- Boot's default security answers 401 to every route (and logs a generated password) until Phase 2a.
- Not tried: the `local` and `staging` profiles. Their files are gitignored and DevOps-owned, and `local` would point at a developer database.
