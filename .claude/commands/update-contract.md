---
name: update-contract
argument-hint: "[module] e.g. careers, leads, content"
---

Update the FE API contract for: $ARGUMENTS

The contract format is defined by the `api-contract` skill. Load it first and follow it exactly.

## Step 1: Locate the controllers and DTOs

```bash
find src/main/java/com/wealthmax/$ARGUMENTS -name "*Controller.java" | head -10
find src/main/java/com/wealthmax/$ARGUMENTS -path "*dto*" -name "*.java" | sort
find src/main/java/com/wealthmax/$ARGUMENTS -path "*entity*" -name "*.java" | xargs grep -l "enum " 2>/dev/null
```

Read every file found completely, plus the service interface and implementation each controller
calls.

## Step 2: Check whether the contract exists

Contract file path: `docs/<module>-api.md`, one file per module.

```bash
ls docs/$ARGUMENTS-api.md 2>/dev/null || echo "no contract yet for $ARGUMENTS"
```

## Step 3: Decide what to do

**No contract yet:** generate it in full, in the `api-contract` format.

**Contract exists:** compare it with the current controllers and DTOs.
- New endpoints: add them.
- Changed endpoints (fields, types, validation, status codes): update that section only.
- Removed endpoints: mark `**DEPRECATED**` with the date; don't delete them.
- Update `Last Updated` in the header.

**Ported feature:** add a short "Changes from the old API" section listing the old path and
shape for each endpoint, so the front end can switch at the cutover.

## Step 4: For each endpoint

**Route:** combine the class-level and method-level mappings. Note the HTTP method.

**Description:** from `@Operation`, or derived from the method name.

**Access:** the URL rule the route lands on in the security configuration (Public,
Authenticated, Admin), plus any `@PreAuthorize` on the service method it calls.

**Request body:** map every DTO field to TypeScript (see the `api-contract` type table). Required
vs optional comes from `@NotNull`, `@NotBlank`, `@NotEmpty`. Record `@Size`, `@Min`, `@Max`,
`@Email`, `@Pattern`.

**Response:** the response DTO itself, with no envelope. Unwrap `ResponseEntity` and expand fully:
- `PageResponseDto<T>` → `content`, `page` (0-indexed), `size`, `totalElements`, `totalPages`,
  `last`, with `T` expanded.
- `List<T>` → `T[]` with `T` expanded.
- A small message DTO for confirmations, as the code returns it.

**Status codes:** exactly what the controller returns (201 for creates).

**Path and query parameters:** each with its type; query parameters with required/optional and
default.

**Errors:** read the service method and list the exceptions it can throw, mapped through the
exception advice (see the `api-contract` error table): `ValidationErrorResponse` for a failed
`@Valid`, `ErrorResponse` for the rest. State what a missing or expired token returns on this
route.

## Step 5: Write the Angular usage example

One `HttpClient` call per endpoint, typed with the response DTO (there is no envelope to unwrap):

```typescript
// GET list with pagination
getJobs(page = 0, size = 20): Observable<PageResponseDto<JobResponse>> {
  const params = new HttpParams().set('page', page).set('size', size);
  return this.http.get<PageResponseDto<JobResponse>>(`${environment.apiUrl}/api/v1/jobs`, { params });
}

// POST create (201)
createJob(request: JobCreateRequest): Observable<JobResponse> {
  return this.http.post<JobResponse>(`${environment.apiUrl}/api/v1/jobs`, request);
}
```

## Step 6: Save the contract

Write to `docs/<module>-api.md`.

## Step 7: Report

```
Contract updated: docs/<module>-api.md

Added endpoints:    [list]
Updated endpoints:  [list]
Deprecated:         [list]
No change:          [list]

Share this file with the FE team.
```

## Step 8: Check for other stale contracts

```bash
BASE=$(git merge-base origin/Dev HEAD)
git diff --name-only "$BASE" | grep -E "(Controller|Request|Response|Dto|DTO)\.java$"
git ls-files --others --exclude-standard | grep -E "(Controller|Request|Response|Dto|DTO)\.java$"
```

If any controller or DTO changed on this branch outside $ARGUMENTS, warn:
```
WARNING: these files changed but their contracts were not updated:
  [list of files]
Run /update-contract <module> for each.
```
