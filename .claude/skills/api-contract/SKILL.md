---
name: api-contract
description: Generates and updates the front-end API doc (contract) from Spring Boot controllers and DTOs. Invoke after completing any new endpoint or modifying existing ones.
user-invocable: true
---

# API Contract Generator: WM-Admin-Portal-BE

Generates a human-readable, Angular-ready API doc from your Spring Boot controllers and DTOs. This
is the source of truth shared with the front-end team.

This is a **single-tenant** application, so no endpoint is documented as "tenant scoped" and no
request carries a tenant id.

**Contract location:** `docs/{module}-api.md`, one file per module. The precedent is
`docs/careers-applicant-in-api.md` (the `careers_in` module). A module's API doc covers every
controller in that module.

**There is no response envelope.** A successful response body is the response DTO itself (or a
`PageResponseDto<T>` for a list, or a small message DTO). Errors use the shapes under "Shared
Types". Don't wrap a body in `{ success, data }`: that isn't what the server sends.

---

## When Invoked

1. Identify which module/controller to document (from argument or recent git diff)
2. Read the controller file completely
3. Read ALL DTO files referenced (request + response)
4. Read enums referenced in DTOs
5. Read `SecurityConfiguration` to find which rule the route lands on (Public, Authenticated or
   `ADMIN`). Remember the first matching rule wins, and check the narrow rules above the
   wildcards.
6. Read the service method the controller calls, for every exception it can throw
7. Generate or update the doc file
8. Report what was added/changed

---

## What to Extract Per Endpoint

From the **Controller**:
- HTTP method + full path (`@GetMapping`, `@PostMapping`, etc. plus the class `@RequestMapping`)
- `@Valid`: confirms validation is enforced
- `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ParameterObject`: parameter sources
- `@Tag` and `@Operation`: description text
- The success status (`201` for create, `200` otherwise)

From **`SecurityConfiguration`**:
- Public (`permitAll`), Authenticated (valid JWT), or Admin (`hasAuthority("ADMIN")`)

From **Request DTOs**:
- Every field name (exact camelCase as Spring returns it)
- Field type, mapped to TypeScript (table below)
- Validation annotations, which become "Required/Optional + rules":
  - `@NotNull`, `@NotBlank`: Required
  - `@Size(min, max)`: min/max length
  - `@Min`, `@Max`: numeric range
  - `@Email`: must be valid email format
  - `@Pattern`: regex rule (show the pattern)

From **Response DTOs**:
- Every field name and type
- Nested objects (show full structure)
- List/Page wrappers (show pagination structure)

From **Enums**:
- All possible values, shown as a string literal type

From the **service**:
- Every `ResourceNotFoundException`, `DuplicateResourceException`, `InvalidDataException` and
  `UnauthorizedException`, with the exact message text where it is fixed

---

## Java to TypeScript Type Mapping

| Java Type | TypeScript Type |
|---|---|
| String | string |
| Long, Integer, int, long | number |
| Double, Float, BigDecimal | number |
| Boolean, boolean | boolean |
| UUID | string |
| LocalDateTime, Instant | string (ISO 8601) |
| LocalDate | string (YYYY-MM-DD) |
| List\<T\>, Set\<T\> | T[] |
| PageResponseDto\<T\> | PageResponse\<T\> (see pagination section) |
| Enum | string literal union |
| Optional\<T\> | T \| null |
| MultipartFile | File |

---

## Contract File Structure

Generate EXACTLY this structure for each module:

````markdown
# [Module Name] API
**Base URL:** `/api/v1/{resource}`
**Auth:** per endpoint: Public, Bearer token (any signed-in user), or Bearer token (ADMIN)
**Last Updated:** YYYY-MM-DD

---

## Authentication Header
Secured endpoints require:
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Shared Types

### Pagination Response

Verified against `PageResponseDto` (`common/dto`; the old repository kept it in `careers_in/common/dto`). Page numbers are 0-indexed. The
pagination fields sit **beside** `content`, not in a nested object:

```typescript
interface PageResponse<T> {
  content: T[];
  page: number;           // current page, 0-indexed
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
```

Query parameters shared by list endpoints (`PageSearchRequestDto`):

| Parameter | Type | Required | Default | Rules |
|---|---|---|---|---|
| search | string | No | none | Global search, case-insensitive, partial match |
| page | number | No | 0 | 0 or greater |
| size | number | No | 10 | 1 to 100 |

### Message Responses

Simple confirmations return one of these:

```typescript
interface ApiMessage { message: string; }       // ApiMessageDto
interface JsonResponse { response: string; }    // JsonResponseDto
```

### Error Responses

Checked against `common/response/ErrorResponse` and `ValidationErrorResponse`, produced by
`GlobalExceptionHandler`:

```typescript
interface ErrorResponse {
  timestamp: string;   // ISO 8601
  status: number;      // HTTP status
  error: string;       // HTTP reason phrase, e.g. "Not Found"
  message: string;     // human-readable
  path: string;        // request URI
}

// Only for a failed @Valid request body (HTTP 400)
interface ValidationErrorResponse {
  violations: { field: string; message: string }[];
}
```

| HTTP | Body | When |
|---|---|---|
| 400 | `ValidationErrorResponse` | A field of the request body failed validation |
| 400 | `ErrorResponse` | Unreadable or malformed body, a path or query value of the wrong type, a missing required query parameter, `InvalidDataException`, `IllegalArgumentException`, or a database constraint failure other than a duplicate |
| 401 | `ErrorResponse` | `UnauthorizedException` thrown by a service (for example a wrong password) |
| 403 | Spring Security's default (no `ErrorResponse`) | A protected route called without a valid token. Spring answers before the controller runs |
| 404 | `ErrorResponse` | `ResourceNotFoundException` |
| 409 | `ErrorResponse` | `DuplicateResourceException`, or a duplicate-key database error |
| 500 | `ErrorResponse` | Unexpected failure. The message is generic |

**A missing, invalid or expired token on a protected route is `403`, not `401`.** The IN
security test (`<Module>SecurityRulesTest`) asserts this. The front end should treat a `403` on a
protected call as "not signed in or not allowed".

---

## Endpoints

---

### [N]. [HTTP METHOD] [Full Path]
**Description:** [what this endpoint does in plain English, from the Angular developer's view]
**Access:** Public / Any signed-in user (Bearer token) / ADMIN (Bearer token)

#### Request

**Path Variables:**
| Variable | Type | Description |
|---|---|---|
| id | number | [Entity] ID |

**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| page | number | No | 0 | Page number (0-indexed) |
| size | number | No | 10 | Page size, 1 to 100 |

**Request Body:**
```typescript
interface [RequestName] {
  fieldName: string;           // Required. Max 100 chars
  optionalField?: string;      // Optional
  status: 'ACTIVE' | 'INACTIVE';  // Required. Enum values
  amount: number;              // Required. BigDecimal on backend
}
```

#### Response: 200 OK (201 Created for a create)

Body: `[ResponseName]` directly, where:

```typescript
interface [ResponseName] {
  id: number;
  fieldName: string;
  createdDate: string;         // ISO 8601 datetime
}
```

#### Error Responses
| HTTP | When it happens |
|---|---|
| 400 | [which fields, which rules, or which `InvalidDataException` message] |
| 403 | No valid token (protected endpoints only) |
| 404 | [Entity] not found |
| 409 | [Duplicate condition, with the message text] |

---

## Rules for Generating Contracts

**Field names must be exact.** Copy from DTO fields exactly as they are. camelCase as Spring Boot
serializes by default. If `@JsonProperty` is present, use that name instead.

**Every enum must list all values.** The Angular developer cannot guess. Show all options.

**Nested objects must be fully expanded.** No `object` or `any` types. Show the full structure
inline or as a separate named interface.

**Required vs Optional must be clear.** `field: string` is required, `field?: string` is
optional. Derive this from `@NotNull` and `@NotBlank` in the DTO. For an update request where
every field is optional, say so, and say whether omitted fields are left unchanged.

**Pagination endpoints:** always show the query params (`search`, `page`, `size`, plus the
module's filters) AND the full `PageResponse` wrapper. Angular developers trip on the 0-indexed
page number constantly.

**Error messages must be complete.** Read the service for every exception it can throw, and
quote fixed message text, because the front end may show it.

**Angular example is mandatory.** One HttpClient snippet per endpoint, typed with the response
DTO directly:

```typescript
getJob(id: number): Observable<CareersResponse> {
  return this.http.get<CareersResponse>(`${environment.apiUrl}/api/v1/careers/IN/get/${id}`);
}
```

---

## Updating an Existing Contract

When a controller or DTO changes:
1. Open the existing doc
2. Find the affected endpoint section
3. Update ONLY what changed. Preserve everything else
4. Add `**Updated:** YYYY-MM-DD: [what changed]` below the endpoint header
5. Update the `Last Updated` date in the file header

Never regenerate the entire file for a small change. Surgical updates only.

---

## What NOT to Include

- Internal implementation details (service class names, repository logic)
- Java-specific type information (`@Column`, `@Entity`, etc.)
- Error stack traces or exception class names
- Internal IDs used only server-side
- Anything that could expose security implementation (secret names, key handling)
- Any value from `application*.yml`

---

## Self-Check Before Saving

- [ ] Every field has a TypeScript type (no `any`)
- [ ] Every enum lists all possible values
- [ ] Required vs optional is marked correctly
- [ ] At least one Angular HttpClient example per endpoint
- [ ] Responses are documented as the DTO itself (no invented envelope)
- [ ] Error cases list the status and, where fixed, the message text
- [ ] A missing token is documented as 403 (not 401)
- [ ] Pagination shape shown for list endpoints
- [ ] Access level is explicit per endpoint (Public, Bearer, or Bearer + ADMIN)
- [ ] No tenant fields or "tenant scope" lines anywhere
- [ ] File saved to `docs/{module}-api.md`
