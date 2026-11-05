# ADR-011: Error Contract and Shared Exceptions

## Status
Accepted 2026-11-05 (gate D-04)

## Context
The old application returns several error shapes, and its catch-all handler turned wrong methods,
unknown URLs and bad content types into 500 responses. The skills name the shared exceptions
`ResourceNotFoundException`, `DuplicateResourceException`, `InvalidDataException` and
`UnauthorizedException`. The audit report (6.4) used different names.

## Decision
Every error response is an RFC 9457 `ProblemDetail` body from one `@RestControllerAdvice`
(`GlobalExceptionHandler`, extending `ResponseEntityExceptionHandler`). Beyond the standard fields it
carries a `code` (a stable machine-readable name), a `traceId` (the same id as in the logs), and for
validation failures an `errors` list of `{field, message}`.

| Exception | Status | `code` |
| --- | --- | --- |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `DuplicateResourceException` | 409 | `DUPLICATE_RESOURCE` |
| `InvalidDataException` | 400 | `INVALID_DATA` |
| `UnauthorizedException` | 401 | `UNAUTHORIZED` |
| Bean validation failure | 400 | `VALIDATION_FAILED` |
| Access denied (method security) | 403 | `ACCESS_DENIED` |
| Database foreign-key violation (SQLState `23503`) | 409 | `DATA_CONFLICT` |
| Anything unexpected | 500 | `INTERNAL_ERROR`, fixed message |

Framework errors keep their own status (404, 405, 413, 415, unreadable body 400), and their `code` is
the HTTP status name (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, `BAD_REQUEST`). A
database unique violation (SQLState `23505`) maps to 409 `DUPLICATE_RESOURCE` without exposing the
constraint name. Any other database integrity error, such as NOT NULL (`23502`), is our bug and is a 500. 4xx responses are logged at WARN without a
stack, 5xx at ERROR with one. Controllers never catch exceptions. A `BusinessRuleException` (422) is
added only if a module needs it.

## Consequences

### Positive
- One error shape for the front end. Every error can be matched to a log line by `traceId`.
- Wrong URLs and methods return their real status.

### Negative
- Changing a `code` later breaks front-end checks, so codes are treated as part of the contract.

### Neutral
- The names are the skills', so the skills already teach them.

## Alternatives Considered
**The audit report's names** (`NotFoundException`, `ConflictException`, `BusinessRuleException`,
`AuthenticationFailedException`): rejected. The skills would need rewriting for no behaviour gain.

## References
- `docs/revamp-audit-report.md`, section 6.4
- `.claude/skills/coding-standards/SKILL.md`
