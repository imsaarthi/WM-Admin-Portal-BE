---
name: security-reviewer
description: Conduct a structured security review of Java 21 / Spring Boot 3.4+ code in WM-Admin-Portal-BE, a single-tenant modular monolith. Covers OWASP Top 10 mapped to this codebase, JWT and OTP handling, URL-rule access control (Public, Authenticated, ADMIN), public endpoints, input validation, injection, secrets handling, file upload, email templates, Spring Boot sharp edges, and module boundaries as a data-access control. Produces a prioritized Critical/Major/Minor report.
license: MIT
user-invocable: true
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
  domain: security
  triggers: security review, security audit, OWASP, JWT, OTP, auth, secrets, injection, XSS, access control
  role: security-specialist
  scope: review
  output-format: report
  related-skills: modular-monolith, coding-standards, review-branch, dependency-upgrade
---

# Security Reviewer: WM-Admin-Portal-BE

Code-level security review for the WM-Admin-Portal-BE single-tenant modular monolith:
injection, auth bypass, secrets, personal-data exposure. For FCA and GDPR compliance and audit
logging, use the `compliance-reviewer` agent. For a general review of everything on a branch, use
`review-branch`. This skill is the security deep dive.

This is a **single-tenant** application: one organisation. "Who can see this data?" is answered by
**authentication and role**, not by tenant. There is no cross-tenant leak to look for. The
equivalent high-stakes questions are: which routes are open to the world, who is an admin, and
which personal data (applicants, leads, subscribers, resumes) can an unauthenticated caller read
or delete.

## When to Use

- An explicit security review request.
- Any change touching authentication, tokens, OTP, `SecurityConfiguration`, roles or user input.
- Any change touching Mailgun, Azure Blob files, image upload or email templates.
- Before merging a PR that adds or changes an endpoint.

Load `coding-standards` first. Its exception, DTO and access-rule conventions are what "correct"
means here.

**Ground truth for this codebase:** `docs/revamp-audit-report.md` section 2.1 lists every known
security finding with evidence (SEC-01 to SEC-36), and `docs/admin-portal-module-documentation.md`
describes the request pipeline and login flow. Read the finding before re-deriving it, and
re-check each one, because it may have been fixed since.

---

## Scope Rule: Secrets

All code enhancement is this team's. The **values** of the secrets in `application*.yml`
are DevOps's: this team doesn't rotate, move, edit or quote them (audit SEC-07).

- Report a committed secret by **file and key only**. Never write the value in a report, commit,
  plan or chat.
- Don't "fix" a value. Do fix the code around it: a key hard-coded in Java (SEC-06), a fallback,
  logging of a secret, a config class that reads it unsafely.
- A secret that was ever committed stays in git history. Say so, and say rotation is DevOps's.

---

## OWASP Top 10, Mapped to This Codebase

| Category | What to check here |
|---|---|
| A01 Broken Access Control | Every route has an explicit level in `SecurityConfiguration`: Public (`permitAll`, narrowest method and path, with a reason), Authenticated, or `hasAuthority("ADMIN")`. First match wins, so a narrow rule must sit above a wildcard. There is no method security, so a route is only as protected as its URL rule. Records that belong to a user are checked against the authenticated user (ownership), not just by id. Sequential `Long` ids are guessable. |
| A02 Cryptographic Failures | Passwords hashed with BCrypt through **one** `PasswordEncoder` bean. The JWT signing key comes from configuration, never a literal in Java. OTPs from `SecureRandom`, stored hashed. |
| A03 Injection | No JPQL or native SQL built by string concatenation. Derived queries, named parameters and `SearchSpecifications` (which escapes `LIKE` wildcards). Email template values are escaped. Uploaded file names are never used as paths. |
| A04 Insecure Design | Module boundaries hold (see below). Public forms are bounded (`@Size`). Business rules live in services. Rate limiting is **out of scope** by the team's decision: don't recommend Bucket4j or CAPTCHA, but do flag unbounded public input. |
| A05 Security Misconfiguration | Swagger UI and `/v3/api-docs` are public in every environment. CORS is one filter, not `@CrossOrigin("*")` on controllers. `ddl-auto` is `validate` (Flyway owns the schema), never `update` outside a developer's local run. Error bodies carry no stack traces or server paths. |
| A06 Vulnerable and Outdated Components | `pom.xml` is on Spring Boot 4.1.2 (ADR-008, set 2026-11-05). Check the support page on the day you review, and flag any dependency that has fallen out of support or has a known CVE (the old POI 5.2.3 and jjwt 0.12.5 were replaced for that reason). Follow `dependency-upgrade` for the process. Run OWASP Dependency-Check: `mvn dependency:tree` alone finds nothing. |
| A07 Identification and Authentication Failures | OTP bound to the user it was issued for, single use, attempt-capped, expiring. Token type, lifetime and revocation. The filter handles a bad or expired token without breaking public routes. Responses don't reveal whether an email exists. |
| A08 Software and Data Integrity | Every `@RequestBody` has `@Valid` and field constraints. No `ObjectMapper.readValue(input, Object.class)`, no polymorphic deserialization of user input. |
| A09 Security Logging Failures | No passwords, OTPs, tokens or personal data (names, emails, phone numbers, CTC) in logs. `GlobalExceptionHandler` returns `ErrorResponse`, never a stack trace. |
| A10 SSRF | Outbound calls (Mailgun, Azure Blob) use configured base URLs, never a user-supplied URL. |

---

## Detailed Check Areas

### Access Control: URL Rules

```java
// GOOD: narrow public rule first, then the protected wildcard (first match wins)
.requestMatchers(HttpMethod.POST, "/api/v1/applicant/IN/addApplicant").permitAll()
.requestMatchers("/api/v1/applicant/IN/**", "/api/v1/careers/IN/**").authenticated()

// BAD: a wildcard permitAll above the narrower rule silently opens everything under it
.requestMatchers("/api/v1/careers/**").permitAll()
.requestMatchers("/api/v1/careers/IN/**").authenticated()   // never reached
```

- List every controller action and the `SecurityConfiguration` rule it lands on. A route that
  lands on a `permitAll` wildcard by accident is a finding.
- `authenticated()` means **any** role. If a route should be admin-only, it needs
  `hasAuthority("ADMIN")`. Check whether self-registered users can reach it.
- `hasAuthority`, not `hasRole`: `User.getAuthorities()` returns the literal `ADMIN`.
- A **public** route that reads or deletes personal data is Critical. Public routes that write
  need bounds and escaping (see below).

### Public Endpoints

Everything in `SecurityConfiguration` marked `permitAll` is public. In the old repository that
included most controller roots (audit SEC-01). Here, public routes are an explicit allow-list:
login and OTP, website form submits (applications, leads, referrals, subscribe) and published
content reads (`docs/revamp-audit-report.md` 6.2). Everything else is authenticated, and the
fallback is `denyAll()`. A **new**
public route needs a stated reason, and it must not return or delete personal data.

### Authentication: JWT and OTP

- There is **one** JWT realm: `JwtAuthenticationFilter` with `JwtService`. There is no separate
  platform and business token.
- Check: key source (SEC-06), token lifetime and whether access and refresh tokens are told apart
  (SEC-11), whether the filter catches a malformed or expired token instead of breaking public
  routes (SEC-23), and whether a deactivated or role-changed user's token still works.
- OTP: random (`SecureRandom`), bound to the user and the login attempt it was issued for, single
  use, attempt-capped, short-lived, never logged, and **not** returned by a public "validate"
  endpoint (SEC-04, SEC-05, SEC-12).
- Compare secrets and codes in constant time (`MessageDigest.isEqual`).

### Input Validation and Mass Assignment

```java
// GOOD
public class JobDetailsRequestDto {
    @NotBlank(message = "Job title is required") @Size(max = 200) private String jobTitle;
}

// BAD: unbounded, and a privileged field accepted from the client
public class UserCreateRequestDto {
    private String name;
    private Role role;   // a caller chooses their own role
}
```

A request DTO with `id`, `role`, `status`, `createdDate` or similar server-owned fields as
client-settable is a Critical finding when it reaches an entity. Controllers must not accept or
return entities.

### Injection

```java
// GOOD: derived query, or named parameter
List<CareersINEntity> findAllByStatus(JobStatusEnum status, Sort sort);

// BAD: concatenation, Critical whatever the route
@Query("SELECT c FROM CareersINEntity c WHERE c.jobTitle = '" + title + "'")
```

### Files, Images and Email

- Uploads: allow-listed type, size cap, a **server-generated** name, never the client's file name
  as a path (SEC-16, SEC-35). Don't serve user content back with the client's content type.
- Resumes and other personal files are not publicly listable or downloadable (SEC-08).
- Email templates: every value from a public form is HTML-escaped (careers_in does it with
  `HtmlUtils.htmlEscape`). A client-supplied address must be a single, validated address
  (SEC-17, SEC-18).

### Secrets and Fail-Open Defaults

See the scope rule above. In code, look for: a key or password literal in Java, a `@Value("${x:default}")`
with a real-looking default for a secret, secrets in log lines, and secrets echoed in error
bodies. Cite the file and line only, never the value.

### Module Boundaries as a Security Control

A module that reads another module's tables, or returns entities, widens who can see what. Check
(see `modular-monolith`):
- no import of another module's `entity`, `repository`, `mapper` or `service.impl`;
- no controller or service returning an entity;
- no native query or join across modules' tables;
- personal data stays in the module that owns it, and other modules receive only the DTO fields
  they need.

### XSS and Deserialization

The API returns JSON, so direct XSS risk is low. Stored XSS arrives through content served back
(images, SVG, HTML) and through email templates. CSRF protection is disabled because auth is
stateless bearer tokens. Cookie-based session auth would be a Major finding.

---

## Spring Boot Sharp Edges

Things that look fine in review and fail in production:

- **Dev shortcuts that can reach production:** a fixed or "master" OTP, "skip email" flags, test
  users (SEC-05).
- **Fail-open defaults:** a property that defaults to a permissive value when missing.
- **Trust-all TLS:** a custom `X509TrustManager` that accepts every certificate, or a hostname
  verifier that returns `true`.
- **HTTP clients without timeouts:** one slow provider ties up threads.
- **`ddl-auto=update`** outside local runs: Hibernate changes the schema without a review, behind
  Flyway's back.
- **A Flyway migration that stores real personal data or a secret** (seed data included), or one
  that edits an applied migration.
- **Swagger and actuator exposure** in production.
- **Secrets, OTPs or tokens in logs,** including at DEBUG.
- **Auth steps not bound together:** an OTP must be tied to the login attempt that passed the
  password check.
- **Password reset that changes the password before proving mailbox ownership** (SEC-10).
- **`@Transactional` on a method called from the same class:** the proxy is skipped and the
  annotation does nothing.
- **A bad token breaking a public route** because the filter doesn't catch the parse exception.

## Known Open Issues

All tracked in `docs/revamp-audit-report.md` (section 2.1, and section 5 for status). Re-check
each before reporting it. Themes at the time of writing: default-open `SecurityConfiguration`
(SEC-01); unauthenticated user creation and edit with a client-chosen role (SEC-02, SEC-03); OTP
not bound to the user and a master OTP (SEC-04, SEC-05); a JWT key literal in source (SEC-06);
public blob and personal-data endpoints (SEC-08, SEC-09); unescaped email templates (SEC-17).

---

## Review Workflow

1. Load `coding-standards` and `modular-monolith`.
2. List every controller action and the `SecurityConfiguration` rule it lands on. Flag any
   personal-data route that is public, and any admin action reachable by a plain authenticated user.
3. Check request DTOs for `@Valid`, constraints and server-owned fields.
4. Grep for concatenated `@Query` strings, `nativeQuery = true`, `JdbcTemplate`.
5. Grep for secret literals in Java and for secrets in log calls (file and line only in the report).
6. Grep log calls for passwords, OTPs, tokens and personal data.
7. Check file upload, image and email-template code for the items above.
8. Check module boundaries: imports across modules, entities at the API.
9. Walk the sharp-edges list against the change.
10. Write the report.

## Output Template

```markdown
# Security Review: <target / PR>

**Reviewed:** <YYYY-MM-DD>
**Scope:** <files or PR>

## Critical (must fix before merge)
- [ ] <File:line>: <problem>. Failure scenario: <who does what, and what they get>. Fix: <fix>.

## Major (should fix)
- [ ] ...

## Minor (observation)
- [ ] ...

## Checked and clean
- <area>: <what was checked>

## Verdict
Approve / Request Changes / Block
```

## Related Skills

- `modular-monolith`: module boundaries and single-tenant access control.
- `coding-standards`: exceptions, DTOs, access rules.
- `review-branch`: whole-branch review with a wider checklist.
- `dependency-upgrade`: moving off unsupported versions.
- `compliance-reviewer` (agent): FCA, GDPR and audit logging.
