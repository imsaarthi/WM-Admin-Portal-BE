---
name: api-doc
argument-hint: "[controller file path or module] e.g. careers"
---

Generate API documentation for: $ARGUMENTS

This produces **internal** backend documentation in `docs/api/`. For the contract the FE team
builds against, use `/update-contract` (the `api-contract` format) instead. Both describe the same
wire format, below.

Step 1: Find the controllers
- Locate every `@RestController` under `src/main/java/com/wealthmax/<module>/` and read each one
  fully.
- Read the service method each endpoint calls: `@PreAuthorize` checks and thrown exceptions
  usually live there.

Step 2: For each endpoint, document:

```
## [HTTP METHOD] [full path]

**Description:** [what this endpoint does, in plain English]

**Access:** Public / Authenticated / Admin  (the URL rule in the security configuration)
**Method security:** [the @PreAuthorize expression on the service method], or none

**Request Body** (if any):
  fieldName (Type, required/optional): description, validation rules

**Path Variables** (if any):
  {id} (Long): description

**Query Parameters** (if any):
  page (int, optional, default 0): page number, 0-indexed
  size (int, optional, default 20): page size

**Response 200/201:** the response DTO itself (no envelope):
  fieldName (Type): description
  Lists: PageResponseDto<T> { content, page, size, totalElements, totalPages, last }

**Error Responses:**
  400 ValidationErrorResponse: [which fields]
  400 ErrorResponse: [malformed body, bad parameter, InvalidDataException]
  401/403: missing or expired token, or not allowed (state which this route returns)
  404 ErrorResponse: [entity] not found
  409 ErrorResponse: [duplicate or cooldown cases]

**Notes:** [side effects: emails sent after commit, audit entries, files stored]
```

Step 3: Group the endpoints
- By resource (all `/jobs` endpoints together).
- Order: GET list, GET by id, POST, PUT, PATCH, DELETE.

Step 4: Write the output
- Save to `docs/api/<module>-api.md`.
- If the file exists, merge the changes; don't replace it.

Step 5: Report
- List every endpoint documented.
- Flag any admin endpoint whose service method lacks `@PreAuthorize`, any public endpoint without
  a stated reason, and any `@RequestBody` without `@Valid`.
