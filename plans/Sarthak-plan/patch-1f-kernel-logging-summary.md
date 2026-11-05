# Patch 1f1 to 1f3: Kernel logging

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1f1, 1f2, 1f3

## Problem
No request id, requests rejected by security would not be logged with one, request logging could leak personal data, and production logs were plain text.

## Solution
- 1f1: `common/logging/TraceIdFilter`, order `HIGHEST_PRECEDENCE`, so it runs before the security chain (-100). It reuses an incoming `X-Request-Id` only if it is 8 to 64 letters, digits or dashes, otherwise it makes a UUID. It sets the MDC and the `X-Trace-Id` response header, and clears the MDC in `finally` (also when the chain throws).
- 1f2: `common/logging/RequestLogFilter`, order `HIGHEST_PRECEDENCE + 1`. One line per request: method, matched **route pattern**, status, duration. Never the real URL, query string or body. An unmatched request is `UNMATCHED`. INFO below 400, WARN from 400 up; a throwing chain is logged as 500.
- 1f3: `src/main/resources/logback-spring.xml`: `[%X{traceId:--}]` in the console and file patterns; new `CONSOLE_JSON` appender (Boot's `StructuredLogEncoder`, format `ecs`) used by the `staging` and `prod` profiles (root and `com.wealthmax`); file appenders kept; the unused `JSON_PATTERN` and the "SaaS" header removed.

## Verification (real runs, 2026-11-05)
- RED each time for the right reason (missing class; 4 of 5 config assertions failing before the edit).
- `mvn -B clean test`: **Tests run: 55, Failures: 0, Errors: 0, BUILD SUCCESS**. New: `TraceIdFilterTest` 6, `RequestLogFilterTest` 6, `LogbackConfigFileTest` 5.
- **The config test caught a real bug**: my first XML comment contained `traceId:--`, and `--` is illegal in an XML comment. Logback could not have parsed the file. Reworded and re-run.

## Rulings
None new.

## Limitations and follow-up
- **Not yet seen running:** the JSON output on stdout and the traceId in a real log line. `LogbackConfigFileTest` checks the file's structure only. The live check is in 1h4 (`SPRING_PROFILES_ACTIVE=local,prod`).
- `OncePerRequestFilter` skips error dispatches by default, so the `/error` dispatch has no trace id in the MDC. The handler adds the id to error bodies anyway.
- Both filters are `@Component`s. Slices that load filters (`@WebMvcTest` with `addFilters` on) will include them.
