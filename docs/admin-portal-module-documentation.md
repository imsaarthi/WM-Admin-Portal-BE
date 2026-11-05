# Admin Portal Backend — Technical Documentation

**Author:** sarthak  
**Date:** 2026-10-31  
**Source Path:** `src/main/java/com/wealthmax/adminPortal/`  
**Base Package:** `com.wealthmax.adminPortal`  
**Based On:** branch `sarthak`, commit `e9c7775`

> This document describes what the code does today, including the parts that do not work as intended.
> Problems are listed in [section 11](#11-known-issues-and-risks). The Careers/Applicant IN API contract
> stays in [careers-applicant-in-api.md](careers-applicant-in-api.md); this document only explains how
> that module fits into the system.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Module Structure](#2-module-structure)
3. [Configuration and Bean Registration](#3-configuration-and-bean-registration)
4. [Shared Infrastructure](#4-shared-infrastructure)
   - [4.1 Security and JWT](#41-security-and-jwt)
   - [4.2 CORS and Web Config](#42-cors-and-web-config)
   - [4.3 Exception Handling](#43-exception-handling)
   - [4.4 Response Shapes](#44-response-shapes)
   - [4.5 Persistence](#45-persistence)
   - [4.6 Object Mapping](#46-object-mapping)
   - [4.7 Email](#47-email)
   - [4.8 Blob Storage](#48-blob-storage)
   - [4.9 Scheduled Jobs](#49-scheduled-jobs)
   - [4.10 Logging](#410-logging)
   - [4.11 Utilities](#411-utilities)
   - [4.12 OpenAPI](#412-openapi)
5. [Feature Modules](#5-feature-modules)
   - [5.1 Authentication and OTP](#51-authentication-and-otp)
   - [5.2 Users and Admin](#52-users-and-admin)
   - [5.3 Roles and Access](#53-roles-and-access)
   - [5.4 Appointed Representative (Website Leads)](#54-appointed-representative-website-leads)
   - [5.5 Adviser](#55-adviser)
   - [5.6 Careers (v1)](#56-careers-v1)
   - [5.7 Applicant (v1)](#57-applicant-v1)
   - [5.8 Careers IN](#58-careers-in)
   - [5.9 Applicant IN](#59-applicant-in)
   - [5.10 Newsletter](#510-newsletter)
   - [5.11 Author](#511-author)
   - [5.12 Referral](#512-referral)
   - [5.13 Team](#513-team)
   - [5.14 Page Header](#514-page-header)
   - [5.15 Department](#515-department)
   - [5.16 Home Page Count](#516-home-page-count)
   - [5.17 Image](#517-image)
   - [5.18 Excel Data and Excel Export](#518-excel-data-and-excel-export)
6. [Request Pipeline Flow](#6-request-pipeline-flow)
7. [Admin Login Flow](#7-admin-login-flow)
8. [API Endpoint Reference](#8-api-endpoint-reference)
9. [Data Model](#9-data-model)
10. [Testing](#10-testing)
11. [Known Issues and Risks](#11-known-issues-and-risks)

---

## 1. Overview

This is the backend for the Wealthmax admin portal and the public Wealthmax website. It is a single
Spring Boot REST API that does three jobs:

- Takes submissions from the public website (enquiry forms, job applications, referrals, newsletter sign-ups)
- Serves website content (jobs, newsletters, authors, team, page headers, home page counters)
- Gives the admin portal a place to manage that content and read the submissions

| Area        | Technology                                                                   |
| ----------- | ---------------------------------------------------------------------------- |
| Language    | Java 17                                                                      |
| Framework   | Spring Boot 3.2.5 (Web, Data JPA, Security, Validation, Actuator)            |
| Database    | PostgreSQL, through Spring Data JPA and Hibernate                            |
| Auth        | Stateless JWT (JJWT 0.11.5, HS256) with an emailed OTP at login              |
| File storage | Azure Blob Storage (`azure-storage-blob` 12.22.0)                           |
| Email       | Mailgun over REST (active); SendGrid is wired but never called               |
| Mapping     | ModelMapper 2.4.4 and hand-written mappers (MapStruct is present but unused) |
| Excel       | Apache POI 5.2.5                                                             |
| API docs    | springdoc-openapi 2.5.0 (Swagger UI)                                         |
| Build       | Maven (`pom.xml`, `mvnw`), Lombok for boilerplate                            |

The code is organised in two styles:

- **The original code ("v1")** is layered by technical type: `controller/`, `service/`, `repository/`,
  `entity/`, `dto/`. All features share these folders.
- **The `careers_in` module** is packaged by feature, with its own controllers, services, DTOs and tables,
  and does not import any v1 feature code. `INModuleBoundaryTest` enforces this.

In both styles a request travels the same way:

```
Controller  -->  Service (interface + impl)  -->  Repository  -->  PostgreSQL
                     |
                     +-->  MailgunServices   (email)
                     +-->  MyBlobService     (Azure Blob)
```

Dependency injection is by constructor everywhere. There is no `@Autowired` in the main source.

---

## 2. Module Structure

```
src/main/java/com/wealthmax/adminPortal/
 |
 +-- AdminPortalApplication.java            Entry point; also turns on @EnableScheduling
 |
 +-- config/                                Application-wide configuration
 |    +-- SecurityConfiguration.java        URL access rules, filter chain, auth provider
 |    +-- JwtAuthenticationFilter.java      Reads the Bearer token on every request
 |    +-- ApplicationConfig.java            Global CORS filter, BCrypt encoder (strength 8)
 |    +-- AppConfig.java                    RestTemplate, octet-stream JSON converter
 |    +-- AzureBlobProperties.java          Blob connection string + container name
 |    +-- SendgridConfig.java               SendGrid client bean
 |    +-- SwaggerConfig.java                OpenAPI info, servers, bearerAuth scheme
 |
 +-- common/                                Infrastructure shared by v1 and IN
 |    +-- config/RequestLoggingFilter.java  Logs method, path, status, duration
 |    +-- exception/
 |    |    +-- GlobalExceptionHandler.java  @ControllerAdvice: exception --> HTTP response
 |    |    +-- ResourceNotFoundException    --> 404
 |    |    +-- DuplicateResourceException   --> 409
 |    |    +-- InvalidDataException         --> 400
 |    |    +-- UnauthorizedException        --> 401
 |    +-- response/
 |         +-- ErrorResponse.java           Standard error body
 |         +-- ValidationErrorResponse.java Body for @Valid failures
 |         +-- Violation.java               One field error
 |
 +-- controller/                            20 v1 REST controllers (section 8)
 |
 +-- service/                               Service interfaces
 |    +-- MyBlobService.java                Concrete class: Azure Blob operations
 |    +-- impl/                             Implementations
 |         +-- OtpService.java              Concrete class, no interface
 |         +-- ExcelExportService.java      Concrete class, no interface
 |         +-- CreateEmailTemplateImpl.java Fills {{placeholders}} in HTML templates
 |         +-- MailgunServiceImpl.java      Active email transport
 |         +-- EmailServiceImpl.java        SendGrid transport (never called)
 |         +-- ...ServiceImpl.java          One per feature
 |
 +-- repository/                            18 Spring Data JPA repositories
 +-- entity/                                18 JPA entities, enums, and Message (a plain response class)
 +-- dto/                                   Request and response objects (one flat folder)
 +-- mapper/MapperConfiguration.java        ModelMapper bean and JSON converters
 +-- utils/
 |    +-- AppUtils.java                     OTP and referral number generation
 |    +-- CommonDateTimeReq.java            @MappedSuperclass: created_at, updated_at
 +-- CronJobs/CronJobs.java                 Scheduled job: expires v1 jobs
 |
 +-- careers_in/                            Self-contained IN module
      +-- careers/
      |    +-- controller/                  CareersINController
      |    +-- service/ + service/impl/     CareersINService
      |    +-- repository/                  CareersINRepository
      |    +-- entity/                      CareersINEntity, JobStatusEnum
      |    +-- dto/request/, dto/response/  Separate request and response DTOs
      |    +-- mapper/                      CareersDetailsMapper (hand-written)
      +-- applicant/
      |    +-- (same sub-packages)          ApplicantIN*, four enums, ApplicantDetailsMapper
      +-- common/
           +-- dto/                         PageResponseDto, PageSearchRequestDto, message DTOs
           +-- search/SearchSpecifications  Reusable JPA Specifications

src/main/resources/
 |
 +-- application.properties                 All configuration
 +-- schema.sql                             Creates the careers_in job number sequence
 +-- logback-spring.xml                     Console + rolling file logging
 +-- templates/                             15 HTML email templates (section 4.7)
```

---

## 3. Configuration and Bean Registration

### Beans

There is no module registration step. Spring Boot component-scans everything under the base package.
All beans are singletons.

| Bean                                    | Defined In              | Description                                        |
| --------------------------------------- | ----------------------- | -------------------------------------------------- |
| `SecurityFilterChain`                   | `SecurityConfiguration` | URL rules, stateless sessions, JWT filter          |
| `AuthenticationProvider`                | `SecurityConfiguration` | `DaoAuthenticationProvider` backed by `users`      |
| `AuthenticationManager`                 | `SecurityConfiguration` | Used by the OTP and change-password flows          |
| `PasswordEncoder`                       | `SecurityConfiguration` | BCrypt, default strength (10)                      |
| `BCryptPasswordEncoder`                 | `ApplicationConfig`     | BCrypt, strength 8                                 |
| `FilterRegistrationBean<CorsFilter>`    | `ApplicationConfig`     | Global CORS at highest precedence                  |
| `RestTemplate`                          | `AppConfig`             | Used by Mailgun; no timeouts set                   |
| `ModelMapper`                           | `MapperConfiguration`   | STRICT matching plus two JSON converters           |
| `SendGrid`                              | `SendgridConfig`        | Needed to start the app, not used for sending      |
| `OpenAPI`                               | `SwaggerConfig`         | Swagger metadata                                   |
| `JwtAuthenticationFilter`               | `@Component`            | JWT filter                                         |
| `RequestLoggingFilter`                  | `@Component`            | Request log filter                                 |
| `AzureBlobProperties`                   | `@Component`            | Blob settings                                      |

`AppConfig.extendMessageConverters` adds `application/octet-stream` to the Jackson converter, so a JSON
multipart part sent without its own content type (such as `authorData` on the Author endpoints) can
still be read.

Two settings exist because of circular dependencies around authentication:
`spring.main.allow-circular-references=true`, and `@Lazy AuthenticationManager` in `UserServiceImpl`.

### Properties

Everything lives in `application.properties`. No value uses an environment placeholder, so the file
contains the real credentials and it is tracked by Git ([S6](#security)).

| Property                                         | Purpose                                                        |
| ------------------------------------------------ | -------------------------------------------------------------- |
| `server.port`                                    | `8080`                                                         |
| `spring.datasource.url` / `username` / `password` | PostgreSQL connection                                         |
| `spring.jpa.hibernate.ddl-auto`                  | `update`: Hibernate alters tables at startup                   |
| `spring.jpa.show-sql`                            | `true`: SQL is printed to the log                              |
| `spring.sql.init.mode`                           | `always`: `schema.sql` runs on every start                     |
| `spring.jpa.defer-datasource-initialization`     | `true`: `schema.sql` runs after Hibernate creates the tables   |
| `spring.main.allow-circular-references`          | `true`                                                         |
| `spring.servlet.multipart.*`                     | 10 MB per file and per request                                 |
| `spring.cloud.azure.storage.blob.*`              | Connection string and container name                           |
| `mailgun.api.key` / `mailgun.api.url`            | Mailgun credentials and endpoint                               |
| `mailgun.domain`                                 | Injected into `MailgunServiceImpl` but not used                |
| `spring.sendgrid.api-key`                        | Required by the `SendGrid` bean                                |
| `project.image`                                  | Folder for image uploads; points inside `src/main/java`        |
| `springdoc.packagesToScan` / `pathsToMatch`      | Limit Swagger to the controllers and `/api/v1/**`              |
| `spring.security.user.*`                         | No effect: the app defines its own `AuthenticationProvider`    |

---

## 4. Shared Infrastructure

---

### 4.1 Security and JWT

**Package:** `com.wealthmax.adminPortal.config`

#### SecurityConfiguration

CSRF is off and sessions are stateless. Spring Security checks rules from top to bottom and the first
match wins, so the order below matters.

| Order | Matcher                                                                 | Rule                  |
| ----- | ----------------------------------------------------------------------- | --------------------- |
| 1     | `/swagger-ui/**`, `/webjars/**`, `/swagger-resources/**`, `/v3/api-docs/**` | Public            |
| 2     | `/api/v1/auth/**`, `/api/v1/author/**`                                  | Public                |
| 3     | `POST /api/v1/applicant/IN/addApplicant`                                | Public                |
| 4     | `GET /api/v1/careers/IN/getAll`, `GET /api/v1/careers/IN/get/**`        | Public                |
| 5     | `/api/v1/applicant/IN/**`, `/api/v1/careers/IN/**`                      | Any valid token       |
| 6     | `/api/v1/careers/**`                                                    | Public                |
| 7     | `/api/v1/otp/**`, `image`, `admin`, `roles-access`, `user`, `AppointedRepresentative`, `page-header`, `adviser`, `team-members`, `applicant`, `newsletters`, `blob`, `departments`, `homepagecount`, `export`, `ExcelData` | Public |
| 8     | `OPTIONS /**`                                                           | Public                |
| 9     | `POST /api/v1/referral/submit`                                          | Public                |
| 10    | `/api/v1/referral/**`                                                   | `ADMIN` authority     |
| 11    | Anything else (including `/actuator/**`)                                | Any valid token       |

What this means in practice:

- Only two groups of endpoints are protected: the IN write and read-applicant routes (any token), and
  the Referral admin routes (`ADMIN`).
- Every other route is public, including the ones that change or delete data ([S1](#security)).
- There is no method-level security. `@PreAuthorize` and `@Secured` are not used anywhere.
- `hasAuthority("ADMIN")` is used rather than `hasRole`, because `User.getAuthorities()` returns the plain
  role name without a `ROLE_` prefix.
- No `AuthenticationEntryPoint` is configured, so a missing token gets an **empty `403`** rather than `401`.

#### JwtAuthenticationFilter

Runs once per request, before `UsernamePasswordAuthenticationFilter`.

```
Incoming Request
     |
     +-- Authorization header missing, or not "Bearer ..."?
     |        YES --> continue as anonymous
     |
     +-- jwtService.extractUsername(token)     parses and verifies the signature
     |        |
     |        +-- expired / malformed / bad signature
     |                 --> JwtException escapes the filter (no try/catch)
     |                     Happens on public routes too. See B4.
     |
     +-- Username present and nothing authenticated yet?
              |
              v
         userService.userDetailsService().loadUserByUsername(email)
              -- reads the users table on every authenticated request
              |
              v
         jwtService.isTokenValid(token, user)
              |
              +-- true  --> set Authentication in SecurityContext
              +-- false --> continue as anonymous
```

Because the user is loaded from the database on each request, a role change takes effect straight away,
even on tokens that were already issued.

#### JwtServiceImpl

| Item          | Access Token (`generateToken`) | Refresh Token (`generateRefreshToken`) |
| ------------- | ------------------------------ | -------------------------------------- |
| Algorithm     | HS256                          | HS256                                  |
| Subject       | User email                     | User email                             |
| Lifetime      | 24 hours                       | 7 days                                 |
| Extra claims  | None                           | None (an empty map is passed)          |
| Stored in DB  | No                             | No                                     |

The signing key is a hard-coded literal inside `JwtServiceImpl.getSignatureKey()` ([S4](#security)).
The two token types are identical apart from their expiry, so either one works in the place of the other
([S8](#security)).

#### Password Hashing

| Flow                        | Encoder used                      | Strength |
| --------------------------- | --------------------------------- | -------- |
| `POST /auth/signup`         | `PasswordEncoder` bean            | 10       |
| `POST /admin`               | `BCryptPasswordEncoder` bean      | 8        |
| Change / forgot password    | `new BCryptPasswordEncoder()`     | 10       |

All three verify correctly, because BCrypt stores the strength inside the hash.

---

### 4.2 CORS and Web Config

`ApplicationConfig.customCorsFilter()` registers a `CorsFilter` at `Ordered.HIGHEST_PRECEDENCE` for `/**`,
with credentials allowed and all headers allowed. Allowed methods are `GET`, `POST`, `DELETE`, `PUT`, `PATCH`.

Allowed origins: `stageadmin`, `stagefe`, `stage`, `adminportal` and `adminbe` under `wealthmax.co.uk`,
`wealthmax.co.uk` itself, `localhost:8080`, `localhost:4200`, and the two `z35.web.core.windows.net`
static sites.

Most controllers also carry `@CrossOrigin("*")`. That annotation does not widen access: the filter runs
first and rejects any origin outside its list. `ExcelExportController` has no `@CrossOrigin` and works
the same way.

---

### 4.3 Exception Handling

**Package:** `com.wealthmax.adminPortal.common.exception`

`GlobalExceptionHandler` is a `@ControllerAdvice` and is the single place that turns exceptions into
responses. Controllers do not catch exceptions, apart from `ImageController` and `MyBlobRestController`.

| Exception                                | HTTP | Client Message                                                       |
| ---------------------------------------- | ---- | -------------------------------------------------------------------- |
| `ResourceNotFoundException`              | 404  | Exception message                                                    |
| `DuplicateResourceException`             | 409  | Exception message                                                    |
| `InvalidDataException`                   | 400  | Exception message                                                    |
| `UnauthorizedException`                  | 401  | Exception message (nothing throws this today)                        |
| `IllegalArgumentException`               | 400  | Exception message                                                    |
| `DataIntegrityViolationException`        | 409  | `A record with the same value already exists` (when "duplicate key") |
| `DataIntegrityViolationException`        | 400  | `The request could not be saved because it violates a data constraint` |
| `HttpMessageNotReadableException`        | 400  | `<Field> is required`, `<Field> has an invalid value`, or `Malformed request body` |
| `MethodArgumentTypeMismatchException`    | 400  | `<Field> must be a valid <Type>`                                     |
| `MissingServletRequestParameterException` | 400 | `<Field> is required`                                                |
| `MethodArgumentNotValidException`        | 400  | `ValidationErrorResponse` with a list of violations                  |
| `Exception` (everything else)            | 500  | `An unexpected error occurred`; the stack trace goes to the log      |

Field names are turned into labels by `toLabel`, so `firstName` becomes `First name`.

The last row catches more than crashes. It also catches Spring MVC's own exceptions, so these all
return `500` ([B3](#bugs)):

- Wrong HTTP method (would normally be `405`)
- Unsupported content type (would normally be `415`)
- Unknown URL (would normally be `404`)
- Upload over the 10 MB limit
- Bean validation failing on an entity at save time, because `ConstraintViolationException` is not handled

Errors raised before a controller is reached, in the security filter chain, do not pass through this
class. See [B4](#bugs).

---

### 4.4 Response Shapes

The API has no single response envelope. Each kind of response has its own shape.

**Errors**

```json
// ErrorResponse
{
  "timestamp": "2026-10-31T17:37:12.03",
  "status": 404,
  "error": "Not Found",
  "message": "Job doesn't exist with the given id: 5",
  "path": "/api/v1/careers/get/5"
}

// ValidationErrorResponse (HTTP 400)
{
  "violations": [
    { "fieldName": "size", "message": "size must be at most 100" }
  ]
}
```

**Success**

| Shape                  | JSON                                   | Used By                                        |
| ---------------------- | -------------------------------------- | ---------------------------------------------- |
| Raw DTO / list         | The object or array itself             | Most read and write endpoints                  |
| `ApiResponse`          | `{ "message": "..." }`                 | Delete operations, `POST /admin`               |
| `JSONResponse`         | `{ "response": "..." }`                | Delete operations, status updates, blob upload |
| `Message`              | `{ "message": "..." }`                 | Department and Roles delete                    |
| `TeamDtoMessage`       | `{ "message": "done" }`                | Team delete                                    |
| `CommonResponse`       | `{ "statusCode", "message", "otpResponse" }` | OTP endpoints                            |
| Plain string           | Text                                   | Signout, image delete, blob delete             |

The IN module has its own equivalents (`ApiMessageDto`, `JsonResponseDto`) so it does not import v1 DTOs,
plus a paging wrapper:

```json
// PageResponseDto<T>
{
  "content": [ { }, { } ],
  "page": 0,
  "size": 10,
  "totalElements": 143,
  "totalPages": 15,
  "last": false
}
```

Only the two IN list endpoints are paged. Every v1 list endpoint returns all rows.

---

### 4.5 Persistence

**Schema management.** There are no migrations. `ddl-auto=update` lets Hibernate add tables and columns at
startup, but it never drops or changes existing ones. `schema.sql` runs after that on every start, so
each statement in it must be safe to repeat. It holds one object:

```
schema.sql (every start, after Hibernate)
     |
     +-- CREATE SEQUENCE IF NOT EXISTS careers_in_job_number_seq
     |
     +-- If the sequence has never been used:
              move it past the highest existing WFA-IN-#### job id
```

**Naming.** Hibernate's default strategy lowercases and adds underscores, so `@Table(name = "AppointedRepresentative")`
becomes `appointed_representative`. This matters in the native queries, which use the final name.

**Timestamps.** `utils/CommonDateTimeReq` is a `@MappedSuperclass` that adds `created_at` and `updated_at`,
filled in by Hibernate. These entities extend it: Adviser, AppointedRepresentative, Author, Image,
Newsletter, PageHeader, Referral, Team. Several DTOs extend it too, which is how those fields reach the JSON.

**Primary keys.** Every entity uses `GenerationType.IDENTITY`.

**Transactions.** Most services have no transaction of their own, so each repository call commits by itself.
A service that makes several writes is not atomic. There are only two explicit transactions:

| Location                                     | Mechanism               | Covers                       |
| -------------------------------------------- | ----------------------- | ---------------------------- |
| `OtpService.sendOtp`                         | `@Transactional`        | Replace OTP row, send email  |
| `ApplicantINServiceImpl.saveApplicantDetails` | `TransactionTemplate`  | Lock, cooldown check, insert |

**Lazy loading.** `NewsletterEntity.author` is the only lazy association. It is read while mapping to a DTO,
outside any transaction. This works only because Spring's Open Session in View is on by default
(`spring.jpa.open-in-view` is not set). Turning that setting off would break the Newsletter and Author
endpoints.

**Search.** The IN list endpoints use JPA `Specification`s ([section 5.8](#58-careers-in)). Everywhere else
uses derived query methods, or native SQL for date ranges:

```sql
WHERE TO_CHAR(created_at, 'YYYY-MM-DD') BETWEEN ?1 AND ?2
```

Comparing strings keeps the whole of the last day in the range, but the query cannot use an index on
`created_at`.

---

### 4.6 Object Mapping

Three approaches exist side by side.

| Approach                   | Where                                                              | Why                                                       |
| -------------------------- | ------------------------------------------------------------------ | --------------------------------------------------------- |
| ModelMapper                | Most v1 services                                                   | Field names match between DTO and entity                  |
| Manual mapping in service  | `ReferralServiceImpl`                                              | `List<String>` to enum list fails at runtime in ModelMapper |
| Manual mapper class        | `ApplicantDetailsMapper`, `CareersDetailsMapper`                   | Nested DTO sections map to a flat entity                  |

`MapperConfiguration` sets up the shared `ModelMapper`:

- STRICT matching, ambiguity ignored, protected fields and methods accessible
- A `JsonNode --> String` converter, which throws if serialization fails, so a submission is never dropped
  silently
- A `String --> JsonNode` converter, which logs and returns `null` on bad data, so one corrupt row cannot
  break a whole list

Those converters exist for `AppointedRepresentative.formData`, which is `JsonNode` on the DTO and a
`TEXT` column on the entity.

MapStruct is in `pom.xml` and the annotation processor is configured, but no class uses it.

---

### 4.7 Email

**Packages:** `service/MailgunServices`, `service/impl/MailgunServiceImpl`, `service/impl/CreateEmailTemplateImpl`

Mailgun is the only provider in use. `EmailService` (SendGrid) is injected into five services, but
`sendEmail` is never called on it.

```
Service
     |
     v
getEmailTemplate("templates/....html")
     -- reads the file from the classpath
     -- on failure: logs the error and returns ""
     |
     v
CreateEmailTemplates.getEmail...(html, data)
     -- replaces each {{placeholder}} with a value
     -- a null value leaves the placeholder in the text
     |
     v
MailgunServices.sendEmail(to, subject, html)
     |
     v
POST mailgun.api.url
     Authorization: Basic api:<key>
     form: from, to, subject, html
     from is fixed: it.department@mg.wealthmaxcommercial.co.uk
     |
     +-- 2xx      --> return
     +-- non-2xx  --> RestTemplate throws
```

Sending is synchronous, on the request thread, with no retry and no timeout. The two `sendEmail`
overloads that take attachments are never called.

The Applicant IN service does not use `CreateEmailTemplates`. It has its own `fillTemplate`, which
HTML-escapes every value. The v1 path does not escape, so text from a public form goes into the HTML as
typed ([S11](#security)).

The private helper `getEmailTemplate` is copied into seven services.

#### Templates

| Template (`EmailTemplateOf...`)        | Placeholders                                         | Used By                                     |
| -------------------------------------- | ---------------------------------------------------- | ------------------------------------------- |
| `LoginOTP`                             | `name`, `otp`                                        | `OtpService`                                |
| `ForgotPassword`                       | `name`, `password`                                   | `UserServiceImpl`                           |
| `ContactUsForm`                        | `name`                                               | AR, category Customer                       |
| `AppointedRepresentative`              | `name`                                               | AR, category AppointedRepresentative        |
| `Adviser`                              | `name`                                               | AR, category Adviser                        |
| `Introducer`                           | `name`                                               | AR, category Introducer                     |
| `Leads`                                | `name`, `email`, `phoneNumber`, `products`           | AR Customer, lead notification              |
| `IntroducerLead`                       | `name`, `email`, `phoneNumber`, `networkInterested`  | AR other categories, lead notification      |
| `Careers`                              | `name`                                               | Applicant v1 and IN, to the applicant       |
| `LeadFromApplicant`                    | `name`, `email`, `phoneNumber`, `position`           | Applicant v1 and IN, lead notification      |
| `newsLetter`                           | None                                                 | Newsletter subscribe                        |
| `Referral`                             | `name`, `referredName`, `referralNumber`             | Referral, to the referrer                   |
| `Subscribers`                          | None                                                 | Only by a private method that is never called |
| `CallBack`, `CallBackLead`             | `name`; `name`, `email`, `phoneNumber`, `availableTime` | Not used                                 |

#### What Happens When Mailgun Fails

Only the Referral service catches the failure.

| Flow                  | Mail Sent                | Result of a Mailgun Failure                                      |
| --------------------- | ------------------------ | ---------------------------------------------------------------- |
| Referral submit       | After save               | Logged, request still succeeds                                   |
| AR create             | After save               | `500`, row kept                                                  |
| Applicant v1          | After save               | `500`, row kept                                                  |
| Applicant IN          | After commit             | `500`, row kept, a retry gets `409` from the cooling period      |
| Newsletter subscribe  | After save               | `500`, row kept, a retry gets `400` (email exists)               |
| Forgot password       | After the password changed | `500`, old password is gone and the new one was never delivered |
| OTP send              | Inside the transaction   | `404` "Please Enter Valid Email id or password", OTP rolled back |

---

### 4.8 Blob Storage

**Class:** `service/MyBlobService`  
**Controller:** `MyBlobRestController` at `/api/v1/blob`

Holds resumes and images in one Azure container, set by `spring.cloud.azure.storage.blob.container-name`.
A new `BlobServiceClient` is built on every call.

| Method                  | Behaviour                                                                 |
| ----------------------- | ------------------------------------------------------------------------- |
| `listFiles()`           | All blob names in the container                                           |
| `downloadFile(name)`    | Reads the whole blob into memory; any failure becomes a 404               |
| `storeFile(name, ...)`  | Uploads; if the name already exists it logs a warning and keeps the old file |
| `deleteBlob(name)`      | Deletes; any failure becomes a 404                                        |
| `downloadMultipleFiles` | Builds a ZIP in memory; skips files it cannot read                        |

The rest of the system stores the blob name as a string in its own tables, so there is no foreign key
and no cleanup, apart from Author, which deletes its own blobs.

```
Client uploads a file first
     |
     v
POST /api/v1/blob/upload  (multipart field: file)
     -- name = Instant.now() + original file name
     |
     v
{ "response": "2026-10-31T10:15:30.123Zcv.pdf" }
     |
     v
Client sends that name in a later request
     -- applicant.fileName, personalDetails.resumeFileName, team.fileName, ...
     |
     v
GET /api/v1/blob/download/{filename}  when someone needs the file
```

`AuthorServiceImpl` is the exception: it takes the files in the same multipart request and builds its
own safe name, `<uuid>-<sanitised original name>`.

---

### 4.9 Scheduled Jobs

**Class:** `CronJobs/CronJobs.java`  
**Enabled by:** `@EnableScheduling` on `AdminPortalApplication`

There is one job. It covers v1 jobs only; IN jobs never expire by themselves.

```
@Scheduled(cron = "* * * 1 * *")
     |
     v
careersService.getJobByStatus("Active")
     |
     v
foreach job:
     applyBy before today?
          YES --> careersService.updateJobStatus(id, "Inactive")
```

The cron fields are second, minute, hour, day of month, month, weekday. This expression therefore runs
**every second for the whole of the 1st of each month**, and not at all on other days. See [B2](#bugs).

---

### 4.10 Logging

`logback-spring.xml` sends everything at `INFO` to the console and to `logs/admin-portal.log`, rolling
at 10 MB, keeping 30 days, capped at 1 GB. The `logs/` folder is ignored by Git.

`RequestLoggingFilter` writes one line per request:

```
POST /api/v1/referral/submit -> 201 (42ms)
```

It has no explicit order, so it runs after the Spring Security chain. Requests that security rejects,
and CORS preflights answered by the CORS filter, are not logged by it.

Services log at `INFO` after each create, update and delete. Two things in the log need care:

- `OtpService.sendOtp` writes each OTP in clear text ([S5](#security)).
- `spring.jpa.show-sql=true` prints all SQL.

---

### 4.11 Utilities

**`AppUtils`**

| Method                         | Output                 | Notes                                                    |
| ------------------------------ | ---------------------- | -------------------------------------------------------- |
| `generateOtp()`                | Four digits            | Uses `java.util.Random`                                  |
| `generateReferralNumber(name)` | `JOH-REF-7K2Q9M`       | Uses `SecureRandom`; suffix avoids `0`, `O`, `1`, `I`    |

The referral prefix is the first three letters of the referred person's name, padded with `X`, or `WMX`
if the name has no letters.

---

### 4.12 OpenAPI

`SwaggerConfig` sets the title, the Local and Production servers, and a global `bearerAuth` scheme.
Swagger UI is public at `/swagger-ui/index.html`. `springdoc.packagesToScan` lists `controller` and
`careers_in`, so a controller placed anywhere else will not appear in it.

---

## 5. Feature Modules

Each section below covers how the module is built. Routes and access levels are in
[section 8](#8-api-endpoint-reference).

---

### 5.1 Authentication and OTP

**Classes:** `AuthenticationController`, `OtpController`, `AuthenticationServiceImpl`, `OtpService`, `JwtServiceImpl`  
**Entities:** `User`, `Otp`  
**Tables:** `users`, `otp`

Login has two steps: `POST /otp/sendOtp` checks the password and emails a code, then `POST /auth/signin`
exchanges the email and code for tokens. The whole flow is in [section 7](#7-admin-login-flow).

`User` implements Spring Security's `UserDetails`, with the email as the username. All account status
methods return `true`, so there is no lockout or disabled state.

`Otp` holds one row per email: `otp`, `email`, `createdAt`, `expiresAt` (5 minutes). Requesting a new code
replaces the row for that email. Rows are not removed at login or at expiry.

`signup` always creates a `USER`. The only ways to get `ADMIN` are `POST /admin` or `PUT /user/update/{id}`.

---

### 5.2 Users and Admin

**Classes:** `AdminController`, `UserController`, `UserServiceImpl`  
**Entity:** `User`  
**Table:** `users`

| Operation          | Implementation                                                                    |
| ------------------ | --------------------------------------------------------------------------------- |
| List users         | `findAll` mapped to `UserDto`, which has no password field                        |
| Create user        | `UserCreateRequestDto` with `@Valid`, role required, email checked for duplicates |
| Update user        | Overwrites name, email, role, fileName, country from the request                  |
| Change password    | Checks the old password through `AuthenticationManager`                           |
| Forgot password    | Sets a random 10-letter password at once, then emails it in clear text            |

`User.role` has no `@Enumerated`, so it is stored as a number: `USER` is 0, `ADMIN` is 1. Reordering the
`Role` enum would silently swap every user's role.

`updateUser` copies `role` even when the request leaves it out. A null role then causes a
`NullPointerException` in `getAuthorities()` for that user.

`getLoggedInUser()` is on the interface but nothing calls it.

---

### 5.3 Roles and Access

**Classes:** `RolesAndAccessController`, `RolesAndAccessServiceImpl`  
**Entity:** `RolesAndAccessEntity`  
**Table:** `roles_and_access`

A list of people and a role label (`fullName`, `emailId`, `roleAssigned`, `fileName`).

**This table does not grant anything.** It has no link to `users`, and Spring Security never reads it. Real
access comes only from `users.role`.

Delete is soft: `delete/bulk` sets `isDelete = true`, and `getAll` filters those rows out. The duplicate
check in `save` still sees deleted rows, so a removed email cannot be added again (`409`).

---

### 5.4 Appointed Representative (Website Leads)

**Classes:** `AppointedRepresentative` (controller), `AppointedRepresentativeServiceImpl`, `ExcelExportService`  
**Entity:** `AppointedRepresentativeEntity`  
**Table:** `appointed_representative`

Despite the name, this module stores **every enquiry form on the website**. The `category` field says
which kind it is, using the names in `RoleEnum`:

| `category`                | Submitter Email Template  | Lead Email Template | Subject to Submitter                                         |
| ------------------------- | ------------------------- | ------------------- | ------------------------------------------------------------ |
| `Customer`                | `ContactUsForm`           | `Leads`             | Thanks for Getting in Touch \| Your Enquiry Has Been Received |
| `AppointedRepresentative` | `AppointedRepresentative` | `IntroducerLead`    | Welcome to Wealthmax \| Your Application Is Received         |
| `Adviser`                 | `Adviser`                 | `IntroducerLead`    | Welcome to Wealthmax \| Your Application Is Received         |
| `Introducer`              | `Introducer`              | `IntroducerLead`    | Welcome to Wealthmax \| Your Application Is Received         |
| Anything else             | None                      | None                | Row is saved, no email                                       |

Matching is exact and case-sensitive. Lead notifications go to `leads@wealthmax.co.uk` with the subject
`New Application Received`.

```
POST /api/v1/AppointedRepresentative/addAppointedRepresentative
     |
     v
firstName = first word of fullName
     |
     v
Save row      -- the duplicate email check is commented out
     |
     v
CreateEmails(): choose templates by category, send both emails
     |
     v
200 AppointedRepresentativeDto
```

The `{{products}}` value in the `Leads` template depends on the form: for `pageRequest = "contact-us-page"`
it is the comma-joined product list; otherwise it is the `pageRequest` value.

`formData` keeps the whole submitted form as JSON ([section 4.6](#46-object-mapping)), so a new form field
can be stored without a schema change. `products` is a `List<String>` with no `@ElementCollection`, so
Hibernate keeps it in a single column on the row.

`update` changes only `firstName`, `lastName`, `emailId` and `contactNumber`.

The dashboard endpoints return a `DashboardDto` built from four count queries, one per category.
`getAllCountFromStartToLastDate` takes `startDate` and `lastDate` as query parameters in `YYYY-MM-DD` form.

---

### 5.5 Adviser

**Classes:** `AdviserController`, `AdviserServiceImpl`  
**Entity:** `AdviserEntity`  
**Table:** `advisers`

A separate list of advisers, unrelated to the `Adviser` category in [section 5.4](#54-appointed-representative-website-leads).

**This module currently fails on most calls.** `AdviserServiceImpl` has a `modelMapper` field, but its
constructor only sets the repository, so the field is `null` ([B1](#bugs)).

| Operation            | Result Today                                                   |
| -------------------- | -------------------------------------------------------------- |
| `create`             | `500` before saving                                            |
| `getAll`, `getByCategory`, `search` | `500`, unless no rows match (then `[]`)         |
| `update`             | Row is updated, then `500` while building the response         |
| `delete`             | Works                                                          |
| `counts`             | Works for `flag` 0, 1, 2 and for custom dates                  |

`counts` returns a map of category to number within a period. `flag` 0 is today, 1 is this month, 2 is
this year; `customStartDate` and `customEndDate` override it. Any other `flag` returns `null` from the
service, and the controller then fails with a `NullPointerException`.

`contactNumber` is an `Integer`, so an 11-digit UK phone number does not fit and the request is rejected.

---

### 5.6 Careers (v1)

**Classes:** `CareersController`, `CareersServiceImpl`, `CronJobs`  
**Entity:** `Careers`  
**Table:** `careers`

The original UK job postings. `status` is free text; the code uses `Active` and `Inactive`.

```
POST /api/v1/careers/createJob
     |
     v
datePosted  = today
createdDate = now
jobId       = generateCaseId()
                  -- read all jobs, newest first
                  -- take the first, parse its number, add one
                  -- "WFA0001" when there are none
     |
     v
Save and return 201
```

Because the id comes from reading the last row, two simultaneous creates can get the same `jobId`. The
column has no unique constraint, so both are stored.

`updateJob` is a partial update: only non-null fields are copied. It can overwrite `jobId` and
`datePosted`, and it cannot change `role`, `jobLocation` or `status`. Status has its own endpoint.

---

### 5.7 Applicant (v1)

**Classes:** `ApplicantController`, `ApplicantServiceImpl`  
**Entity:** `ApplicantEntity`  
**Table:** `applicant`

Applications for v1 jobs, with a simple flat shape.

```
POST /api/v1/applicant/addApplicant
     |
     v
Save row        -- no @Valid, no duplicate check
     |
     v
Email the applicant  (Careers template)
Email leads@         (LeadFromApplicant template)
     |
     v
200 ApplicantDto
```

`validateApplicant` is a private duplicate check that nothing calls, so one person can apply any number
of times. The resume is uploaded first through the Blob API, and its name goes in `fileName`.

---

### 5.8 Careers IN

**Package:** `com.wealthmax.adminPortal.careers_in.careers`  
**Entity:** `CareersINEntity`  
**Tables:** `careers_in`, `careers_in_skill_options`  
**Contract:** [careers-applicant-in-api.md](careers-applicant-in-api.md)

Job postings for the India market. Together with Applicant IN it replaces nothing: v1 stays in place and
both run side by side.

| Aspect       | v1                               | IN                                                  |
| ------------ | -------------------------------- | --------------------------------------------------- |
| Package      | Shared `controller/`, `dto/`...  | Own `careers_in` module                             |
| DTOs         | One class for input and output   | Separate request and response DTOs                  |
| Validation   | None on job or applicant         | Bean Validation on every request DTO                |
| Mapping      | ModelMapper                      | Hand-written mapper                                 |
| Job id       | `WFA0001`, from the last row     | `WFA-IN-0001`, from a database sequence             |
| Job status   | Free text                        | `JobStatusEnum`: `ACTIVE`, `INACTIVE`               |
| List         | All rows                         | Paged, with search and filters                      |
| Access       | Public                           | Public website reads; token for everything else     |
| Tests        | None                             | Full suite ([section 10](#10-testing))              |

#### Job Id

```
saveJobDetails()
     |
     v
careersINRepository.nextJobNumber()
     -- select nextval('careers_in_job_number_seq')
     |
     v
String.format("%s%04d", "WFA-IN-", number)   -->  WFA-IN-0042
```

A sequence gives each caller a different number, even under concurrency, and never reuses the number of
a deleted job. `job_id` also has a unique constraint.

#### Update

`CareersUpdateRequestDto` makes every field optional. `CareersDetailsMapper.applyPartialUpdate` copies
only non-null values, so a field cannot be cleared; `skillOptions`, if sent, replaces the list. The request
cannot carry `status` or any server-set field.

Validation runs at two levels, because a request may send just one side of a range:

```
PUT /api/v1/careers/IN/update/{id}
     |
     v
@Valid on the DTO          -- checks the values sent
     |
     v
applyPartialUpdate()       -- merges them onto the stored job
     |
     v
requireValidRanges()       -- checks the merged job (min <= max for experience and CTC)
     |                        InvalidDataException --> 400
     v
Save
```

#### Search

`GET /getAll` accepts `search`, `department`, `jobType`, `workStyle`, `page` and `size`.
`SearchSpecifications` provides the building blocks, and a blank value means "no restriction":

| Method             | SQL Effect                                                                              |
| ------------------ | --------------------------------------------------------------------------------------- |
| `equalsIgnoreCase` | `lower(column) = value`                                                                 |
| `containsAny`      | `lower(column) LIKE %term%` across the columns, OR `EXISTS` on an element collection    |

`%` and `_` in the search term are escaped, so they match literally. Collections use `EXISTS` rather than
a join so a row with several matching skills is counted once and paging stays correct.

Jobs sort by `jobId` descending. `getAll` has no status filter, so `INACTIVE` jobs are in the public list;
the public site should call `GET /get/status/ACTIVE`. `status` is optional on create, and a job created
without one appears in neither status list.

---

### 5.9 Applicant IN

**Package:** `com.wealthmax.adminPortal.careers_in.applicant`  
**Entity:** `ApplicantINEntity`  
**Tables:** `applicant_in`, `applicant_in_industry_experience`, `applicant_in_skills`  
**Contract:** [careers-applicant-in-api.md](careers-applicant-in-api.md)

A five-step application form. The request nests one object per step, and `ApplicantDetailsMapper`
flattens them into a single row.

| Section               | Required   | Holds                                                         |
| --------------------- | ---------- | ------------------------------------------------------------- |
| `personalDetails`     | Yes        | Name, email, mobile, location, links, resume blob name        |
| `educationDetails`    | Yes        | Qualification, course, college, year, gap                     |
| `experienceDetails`   | Yes        | Years and months, company, skills, industries, gap            |
| `availabilityDetails` | No         | Notice period, CTC, other offers, relocation, shifts          |
| `finalDetails`        | Yes        | How they heard about us, declaration                          |

#### Submission Flow

```
POST /api/v1/applicant/IN/addApplicant     (public)
     |
     v
@Valid on the five sections   --> 400 violations
     |
     v
ApplicantDetailsMapper.toEntity()
     -- trims name, email, mobile
     -- turns the CTC text into BigDecimal
     |
     v
TransactionTemplate.execute:
     |
     +-- pg_advisory_xact_lock(hash of lower(email) + "|" + mobile)
     |        requests for the same person now run one at a time
     |
     +-- Latest row with same email (ignoring case) + same mobile
     |   whose cooldownUntil is after now?
     |        |
     |        +-- YES --> DuplicateResourceException --> 409
     |        |           "...Please try again after N month(s)."
     |        |
     |        +-- NO  --> cooldownUntil = now + 6 months (UTC)
     |                    save the row
     |
     +-- commit, which releases the lock
     |
     v
Email the applicant, email leads@     -- outside the transaction
     |
     v
200 ApplicantResponseDto
```

#### Why It Is Built This Way

- **The cooling period replaced a unique key.** A unique constraint blocks a repeat for ever and cannot
  depend on time, while the rule is time-limited: one application per email and mobile every six months,
  for any job and any name.
- **A check alone would race.** Two simultaneous requests could both find no row. The advisory lock makes
  the check and insert run one at a time for that person, and it is released automatically at commit.
- **The lock key is a hash.** If two different people share a hash, they only wait for each other briefly.
- **Emails are sent after commit**, so a Mailgun failure does not undo the application.
- **N is rounded up**, so a few days left reads "1 month", never "0 months".

#### Validation Worth Knowing

| Rule                                     | How It Is Done                                                                    |
| ---------------------------------------- | --------------------------------------------------------------------------------- |
| "Details required when gap/offer = true" | `@AssertTrue` on a method; the method name becomes the reported field             |
| CTC in LPA                               | `String` with `@Pattern` (`4.5`, `12`), converted to `BigDecimal` by the mapper   |
| Declaration must be accepted             | `@NotNull` and `@AssertTrue` together, because `@AssertTrue` alone accepts null   |
| Enum given as empty string               | `HttpMessageNotReadableException` --> `400 <Field> is required`                   |

`RequestResponseContractTest` keeps server-set fields such as `id` and `createdAt` out of the request DTOs.

`jobId` is not checked against `careers_in`, so an application can reference a job that does not exist.
The `skills` and `industryExperience` lists are `EAGER` with `@BatchSize(50)`, which keeps a page of results
from running one extra query per row.

---

### 5.10 Newsletter

**Classes:** `NewsletterController`, `NewsletterServiceImpl`  
**Entities:** `NewsletterEntity`, `NewsLetterReply`, `NewsLetterSubscribersEntity`  
**Tables:** `newsletters`, `newslettersreply`, `news_letter_subscribers`

Holds website articles, reader replies and email subscribers. It is the only part of the code with a JPA
relationship: each newsletter has one required `AuthorEntity`.

#### Status

```
          create
             |
             v
         +---------+   PATCH    +-----------+   PATCH    +-------------+
         | CREATED | ---------> | PUBLISHED | <--------> | UNPUBLISHED |
         +---------+            +-----------+            +-------------+

CREATED can also move straight to UNPUBLISHED.
Status cannot be set back to CREATED (400).
```

`GET /findAll?status=PUBLISHED` filters by status. With no parameter it returns everything.
`GET /slug/{slug}`, `/find/{id}` and `/find/category/{category}` do not check status, so an unpublished
article is reachable by its slug.

#### Creating an Article

```
POST /api/v1/newsletters/save
     |
     v
@Valid: headline, urlSlug, authorId required
     |
     v
urlSlug already exists?  YES --> 409
     |
     v
Author exists?           NO  --> 404
     |
     v
status = CREATED, save, return 201
```

The client supplies the slug. The code that emailed every subscriber on create is commented out.

#### Startup Backfill

`@PostConstruct backfillNewsletterDefaults()` reads every newsletter at each start, and gives a slug and a
status to any row missing them. It never replaces an existing slug, so published URLs stay stable.

#### Subscribers and Replies

| Feature      | Behaviour                                                                                        |
| ------------ | ------------------------------------------------------------------------------------------------ |
| Subscribe    | Case-insensitive duplicate check, `400` if it exists, sends the welcome email                    |
| Reply        | Stored with the newsletter id as a plain number; no foreign key                                  |
| Reply counts | `populateReplyCounts` loads the replies for a list and counts them in memory                     |
| Category count | One count query for each of the five categories in `NewsLetterCategoryEnum`                    |

Deleting a newsletter does not delete its replies.

---

### 5.11 Author

**Classes:** `AuthorController`, `AuthorServiceImpl`  
**Entity:** `AuthorEntity`  
**Table:** `authors`

Author profiles for the website, with a public page at `author/{slug}`.

Create and update are `multipart/form-data`: a JSON part called `authorData`, plus optional file parts
`authorPicture`, `credential1`, `credential2`, `credential3`.

**Slug.** Built from the name: lowercase, symbols removed, spaces become hyphens. If it is taken, `-1`,
`-2` and so on are added. A rename changes the slug only if the base slug changes, so correcting
capitals keeps the URL. `metaTitle` is always `<Name> | Wealthmax Financial Advisers`.

**Update rules.**

| Field Type    | `null`          | Value            | Other                                        |
| ------------- | --------------- | ---------------- | -------------------------------------------- |
| `name`        | Leave unchanged | Set (trimmed)    | Blank --> `400`                              |
| Other text    | Leave unchanged | Set (trimmed)    | Blank --> clear the field                    |
| File          | Leave unchanged | Replace          | `removeX = true` with no file --> clear it   |

**Keeping blobs and rows consistent.** A database save cannot roll back a blob upload, so the service
does it by hand:

```
Upload the new files          (remember them in uploadedBlobs)
Note the old file names       (remember them in replacedBlobs)
     |
     v
authorRepository.save()
     |
     +-- FAILS   --> delete uploadedBlobs, rethrow
     |
     +-- SUCCESS --> delete replacedBlobs
```

Blob deletion is best-effort: a failure is logged and does not fail the request.

**Profile page.** `GET /{slug}` returns the author, an `h2` heading and the latest four **published** articles.

An author with newsletters cannot be deleted: the `author_id` foreign key rejects it, and the client gets `400`.

---

### 5.12 Referral

**Classes:** `ReferralController`, `ReferralServiceImpl`  
**Entity:** `ReferralEntity`  
**Tables:** `referrals`, `referral_services`

The "Refer a Friend" form. It has the only role-protected endpoints in the system: `POST /submit` is
public and all other routes need `ADMIN`.

```
POST /api/v1/referral/submit
     |
     v
@Valid on ReferralCreateRequestDto    --> 400 violations
     |
     v
toEntity(): trim fields, convert services
     -- accepts PROTECTION or "Protection"
     -- removes repeats
     -- unknown service --> 400
     |
     v
Same referred email + same service already today?
     YES --> 409, the whole submission is rejected
     NO  --> continue
     |
     v
generateUniqueReferralNumber()
     -- up to 5 attempts, then 500
     |
     v
Save row
     |
     v
Referrer gave an email?
     YES --> send the referral email; a failure is only logged
     |
     v
201 ReferralDto
```

Design points already explained in the code comments:

- The duplicate rule uses only the **referred person's email**, so two different referrers naming the same
  person for the same service on one day count as a double claim. A different service, or a later day, is allowed.
- The email failure is swallowed on purpose. The referral is saved and the number is in the response, so
  failing would just make the visitor resubmit.
- The referral number uses `SecureRandom`, since a guessable number could be used to claim the voucher.
- Mapping is manual ([section 4.6](#46-object-mapping)). `ReferralDto.servicesLookingFor` returns display labels
  such as `Wills & Estate Planning`.

**Admin search.** `GET /getAll` takes optional `query`, `startDate` and `endDate`, and they combine:

| Filters Given        | Implementation                                                              |
| -------------------- | --------------------------------------------------------------------------- |
| Date range and query | Date range in SQL, then the query in memory                                 |
| Date range only      | Date range in SQL                                                           |
| Query only           | JPQL `search`, across names, emails, phones, number and adviser name        |
| Neither              | `findAll`                                                                   |

Both dates are needed for a date range. If only one is given it is ignored. The result is always newest first.

---

### 5.13 Team

**Classes:** `TeamController`, `TeamServiceImpl`  
**Entity:** `TeamEntity`  
**Table:** `team_members`

Plain CRUD for the website team page. `fileName` holds the blob name of the photo. `update` changes `name`,
`designation`, `category` and `fileName`; `linkedInUrl` and `emailUrl` can be set on create but not updated.

---

### 5.14 Page Header

**Classes:** `PageHeaderController`, `PageHeaderServiceImpl`  
**Entity:** `PageHeaderEntity`  
**Table:** `page_headers`

Plain CRUD for page titles and subtitles. `update` changes `pageName`, `pageHeading` and `pageSubHeading`.
`fileName` can be set on create but not updated.

---

### 5.15 Department

**Classes:** `DepartmentController`, `DepartmentServiceimpl`  
**Entity:** `Department`  
**Table:** `departments`

A list of department names, with no update. Duplicate names get `409`. The controller returns the entity
directly, with no DTO. Jobs store the department as text, so nothing links the two.

---

### 5.16 Home Page Count

**Classes:** `HomePageCountController`, `HomePageCountServiceImpl`  
**Entity:** `HomePageCountEntity`  
**Table:** `home_page_count_entity`

The four counters on the home page. It is meant to hold one row, though nothing enforces that. `add`
returns the DTO it was given, without the new `id`.

A missing `id` is not handled: `get` returns `400` ("source cannot be null") and `update` returns `500`.

---

### 5.17 Image

**Classes:** `ImageController`, `ImageServiceImpl`  
**Entity:** `ImageEntity`  
**Table:** `images`

Stores each upload twice: as a file in the `project.image` folder, and as bytes in a `@Lob` column.

```
POST /api/v1/image/upload-image
     |
     v
Create the folder if needed
     |
     v
Files.copy(stream, folder + original file name)
     -- fails if the file exists --> 500 "File already exists."
     |
     v
Save name, path, content type and bytes to the images table
     |
     v
200 { "fileName": "<server path>", "message": "..." }
```

The response gives the server path, not the database `id` that `GET /image/{id}` needs.

`GET /image/{id}` reads the bytes from the database. Its `notFound()` branch cannot run: the service passes
null to ModelMapper, which throws, so a missing `id` returns `400`.

`project.image` points inside `src/main/java`, which does not exist in a packaged deployment; the folder
is created on first upload. Other modules store files in Blob Storage ([section 4.8](#48-blob-storage)).

---

### 5.18 Excel Data and Excel Export

These two are unrelated, despite the similar names.

**Excel Data** (`ExcelDataController`, `ExcelDataServiceImpl`, table `excel_data`) stores a record of a file:
`fileName`, `title`, `description`, `createdAt`. It does not read Excel files. The file itself is
uploaded through the Blob API.

**Excel Export** (`ExcelExportController`, `ExcelExportService`) builds a spreadsheet of website leads.

```
GET /api/v1/export/{category}
     |
     v
findByCategoryIgnoreCase(category)  on appointed_representative
     |
     v
POI XSSFWorkbook, sheet "Leads"
     ID, Name, Email, Category, Contact Number, Experience, Available Time, Is From UK
     |
     v
Download: employees_<category>.xlsx
```

The route is public, so anyone with the URL can download the leads ([S1](#security)).

---

## 6. Request Pipeline Flow

Order of execution for a request:

```
 Client (Public Website / Admin Portal)
         |
         |  HTTPS Request
         v
 +------------------------------------------------------------------+
 |  Servlet Filters                                                 |
 |                                                                  |
 |  1.  CorsFilter  (highest precedence)                            |
 |          -- answers preflights                                   |
 |          -- rejects origins outside the allowed list             |
 |                                                                  |
 |  2.  Spring Security filter chain                                |
 |          a.  JwtAuthenticationFilter                             |
 |                  -- no token: continue as anonymous              |
 |                  -- valid token: load user, set Authentication   |
 |                  -- bad token: exception leaves the chain        |
 |          b.  AuthorizationFilter                                 |
 |                  -- first matching rule wins (section 4.1)       |
 |                  -- denied: empty 403                            |
 |                                                                  |
 |  3.  RequestLoggingFilter                                        |
 |          -- times the rest of the request                        |
 |          -- writes one log line when it finishes                 |
 +------------------------------------------------------------------+
         |
         v
 +------------------------------------------------------------------+
 |  Spring MVC                                                      |
 |                                                                  |
 |  4.  DispatcherServlet routes to the controller method           |
 |          -- path variables and query parameters bound            |
 |          -- @RequestBody parsed by Jackson                       |
 |          -- @Valid, where present                                |
 |                                                                  |
 |  5.  Controller --> Service --> Repository                       |
 |          -- business rules                                       |
 |          -- each repository call in its own transaction,         |
 |             unless section 4.5 says otherwise                    |
 |          -- CommonDateTimeReq timestamps set by Hibernate        |
 |                                                                  |
 |  6.  Side effects, on the same thread                            |
 |          -- Mailgun                                              |
 |          -- Azure Blob                                           |
 |                                                                  |
 |  7.  Response                                                    |
 |          -- DTO serialized to JSON                               |
 |          -- on exception: GlobalExceptionHandler builds the body |
 +------------------------------------------------------------------+
         |
         |  HTTPS Response
         v
 Client
```

---

## 7. Admin Login Flow

Login is password first, then an emailed code. Both endpoints are public.

---

### Phase A -- Account Creation

```
POST /api/v1/auth/signup                 POST /api/v1/admin
     { firstName, lastName,                   { firstName, lastName, email,
       email, password, country }               password, role, country }
         |                                        |
         v                                        v
     role is always USER                      role comes from the request
         |                                        |
         +------------------+---------------------+
                            |
                            v
                  Duplicate email?  YES --> 409
                            |
                            v
                  BCrypt the password, save to users
```

---

### Phase B -- Login Step 1 (password check, send OTP)

```
POST /api/v1/otp/sendOtp
     Body: { "email": "...", "password": "..." }
         |
         v
OtpService.sendOtp()   (@Transactional)
    1. Delete any existing OTP row for this email
    2. otp = AppUtils.generateOtp()  (4 digits)
    3. authenticationManager.authenticate(email, password)
    4. Load the User
    5. Save Otp { email, otp, expiresAt = now + 5 minutes }
    6. Send EmailTemplateOfLoginOTP through Mailgun
         |
         +-- SUCCESS:
         |        200 { "statusCode": 200, "message": "Otp has been send to Your Email!" }
         |
         +-- ANY exception (wrong password, unknown email, Mailgun down):
                  Transaction rolls back
                  404 "Please Enter Valid Email id or password"
```

---

### Phase C -- Login Step 2 (verify OTP, receive tokens)

```
POST /api/v1/auth/signin
     Body: { "email": "...", "otp": "1234" }
         |
         v
AuthenticationServiceImpl.signin()
         |
         +-- otp equals the fixed master code (ADMIN_MASTER_OTP)?
         |        |
         |        +-- email belongs to an ADMIN --> issue tokens
         |        +-- otherwise                 --> 400 "Email  is invalid"
         |
         +-- Find the OTP row by code alone  (otpRepository.findByOtp)
                  |
                  +-- none found --> 404 "Otp is not valid"
                  +-- expired    --> 404 "Please generate new Otp, it is expired now!"
                  |
                  +-- Find the user by the email in the request
                           |
                           +-- none found --> 400 "Email  is invalid"
                           |
                           +-- found --> issue tokens:
                                 200 {
                                   "token":        "eyJ...",   (24 hours)
                                   "refreshToken": "eyJ...",   (7 days)
                                   "user":         { UserDto }
                                 }
```

The OTP row's own `email` is never compared with the email in the request, and the row is not deleted
after use ([S5](#security)).

`POST /otp/validateOtp` is a separate check that says whether a code exists and has not expired. Sign-in
does not depend on it.

---

### Phase D -- Authenticated Request

```
GET /api/v1/referral/getAll
     Authorization: Bearer eyJ...
         |
         v
[JwtAuthenticationFilter]
     -- verify signature and expiry
     -- load the User from the database
     -- set Authentication with authority "ADMIN" or "USER"
         |
         v
[AuthorizationFilter]
     -- /api/v1/referral/** needs ADMIN
     -- 403 if the user does not have it
         |
         v
[ReferralController] --> [ReferralServiceImpl]
         |
         v
200 List<ReferralDto>
```

---

### Phase E -- Token Refresh

```
POST /api/v1/auth/refresh-token
     Body: { "token": "<refresh token>" }
         |
         v
Extract the email, load the User, check the token
         |
         +-- valid --> 200 {
         |               "token":        "<new access token>",
         |               "refreshToken": "<same token that was sent>",
         |               "user":         null
         |             }
         |
         +-- expired or bad signature --> exception --> 500
```

The refresh token is not rotated.

---

### Phase F -- Sign Out

```
POST /api/v1/auth/signout
     Body: { "token": "..." }
         |
         v
Checks the token, then does nothing else
         |
         v
200 "Logout successfully"
```

Nothing is stored or revoked, so the token keeps working until it expires. Sign-out on the client side
means discarding the tokens.

---

### Phase G -- Password Changes

```
Change password (user knows the old one)
PUT /api/v1/user/update/password/{userid}
     Body: { "oldPassword": "...", "newPassword": "..." }
         |
         v
authenticate(email, oldPassword)
     fail    --> 400 "Invalid current password"
     success --> save the new hash, 200


Forgot password
PUT /api/v1/user/forgetpassword/{EmailId}
         |
         v
Find the user       (404 if none)
         |
         v
Generate a 10-letter password, save its hash    <-- old password stops working here
         |
         v
Email the new password in clear text
         |
         v
200 { "response": "Password sent to Email id" }
```

Neither flow ends existing tokens.

---

## 8. API Endpoint Reference

All routes are under `/api/v1`. There are 107 endpoints.

**Access:** `Public` = no token needed. `Token` = any valid JWT, whatever the role. `ADMIN` = valid JWT
with the `ADMIN` role.

---

### Auth

Base route: `/api/v1/auth`

| Method | Route            | Access | Request                  | Success                       | Description                  |
| ------ | ---------------- | ------ | ------------------------ | ----------------------------- | ---------------------------- |
| POST   | `/signup`        | Public | `SignupRequest`          | `200 UserDto`                 | Self-register as `USER`      |
| POST   | `/signin`        | Public | `{ email, otp }`         | `200 JwtAuthenticationRes`    | Step 2 of login              |
| POST   | `/refresh-token` | Public | `{ token }`              | `200 JwtAuthenticationRes`    | New access token             |
| POST   | `/signout`       | Public | `{ token }`              | `200 "Logout successfully"`   | Does not revoke anything     |

### OTP

Base route: `/api/v1/otp`

| Method | Route          | Access | Request               | Success              | Description               |
| ------ | -------------- | ------ | --------------------- | -------------------- | ------------------------- |
| POST   | `/sendOtp`     | Public | `{ email, password }` | `200 CommonResponse` | Step 1 of login           |
| POST   | `/validateOtp` | Public | `{ otp, email }`      | `200 CommonResponse` | Check a code is still valid |

### Admin

Base route: `/api/v1/admin`

| Method | Route | Access | Request                | Success            | Description            |
| ------ | ----- | ------ | ---------------------- | ------------------ | ---------------------- |
| GET    | `/`   | Public | --                     | `200 List<UserDto>` | List all users        |
| POST   | `/`   | Public | `UserCreateRequestDto` | `200 ApiResponse`  | Create a user with any role |

### User

Base route: `/api/v1/user`

| Method | Route                       | Access | Request       | Success            | Description            |
| ------ | --------------------------- | ------ | ------------- | ------------------ | ---------------------- |
| GET    | `/{userid}`                 | Public | --            | `200 UserDto`      | Get a user             |
| PUT    | `/update/{id}`              | Public | `UserDto`     | `200 UserDto`      | Update, including role |
| PUT    | `/update/password/{userid}` | Public | `PasswordDto` | `200 JSONResponse` | Change password        |
| PUT    | `/forgetpassword/{EmailId}` | Public | --            | `200 JSONResponse` | Reset and email password |

### Roles and Access

Base route: `/api/v1/roles-access`

| Method | Route          | Access | Request              | Success                      | Description     |
| ------ | -------------- | ------ | -------------------- | ---------------------------- | --------------- |
| POST   | `/save`        | Public | `RolesAndAccessDto`  | `200 RolesAndAccessDto`      | Create          |
| GET    | `/getAll`      | Public | --                   | `200 List<RolesAndAccessDto>` | List, not deleted |
| PUT    | `/update/{id}` | Public | `RolesAndAccessDto`  | `200 RolesAndAccessDto`      | Update          |
| DELETE | `/delete/bulk` | Public | `{ ids: [] }`        | `200 Message`                | Soft delete     |

### Appointed Representative

Base route: `/api/v1/AppointedRepresentative`

| Method | Route                                                   | Access | Request                      | Success                  | Description                 |
| ------ | ------------------------------------------------------- | ------ | ---------------------------- | ------------------------ | --------------------------- |
| POST   | `/addAppointedRepresentative`                           | Public | `AppointedRepresentativeDto` | `200` DTO                | Submit a website form       |
| GET    | `/getAllAppointedRepresentatives`                       | Public | --                           | `200` list               | List all                    |
| GET    | `/getAllAppointedRepresentatives/{category}`            | Public | --                           | `200` list               | List by category            |
| PUT    | `/update/{id}`                                          | Public | `AppointedRepresentativeDto` | `200` DTO                | Update name, email, contact |
| DELETE | `/delete/{id}`                                          | Public | --                           | `200 JSONResponse`       | Delete one                  |
| DELETE | `/deleteAll`                                            | Public | --                           | `200 JSONResponse`       | **Delete every row**        |
| GET    | `/dashboard/getCount`                                   | Public | --                           | `200 DashboardDto`       | Totals per category         |
| GET    | `/dashboard/getCount/filter`                            | Public | `?startDate&lastDate`        | `200 DashboardDto`       | Totals in a date range      |
| GET    | `/dashboard/getCount/filter/{category}/{startDate}/{lastDate}` | Public | --                    | `200` list               | Rows in a date range        |

### Adviser

Base route: `/api/v1/adviser`

| Method | Route                     | Access | Request                                     | Success                 | Description         |
| ------ | ------------------------- | ------ | ------------------------------------------- | ----------------------- | ------------------- |
| POST   | `/create`                 | Public | `AdviserDto`                                | `201 AdviserDto`        | Create              |
| GET    | `/getAll`                 | Public | --                                          | `200 List<AdviserDto>`  | List all            |
| GET    | `/getByCategory/{category}` | Public | --                                        | `200 List<AdviserDto>`  | List by category    |
| PUT    | `/update/{id}`            | Public | `AdviserDto`                                | `200 AdviserDto`        | Update              |
| DELETE | `/delete/{id}`            | Public | --                                          | `200 ApiResponse`       | Delete              |
| GET    | `/counts`                 | Public | `?flag&customStartDate&customEndDate`       | `200 Map<String,Integer>` | Counts per category |
| GET    | `/search/{email}`         | Public | --                                          | `200 List<AdviserDto>`  | Find by exact email |

### Careers (v1)

Base route: `/api/v1/careers`

| Method | Route                  | Access | Request      | Success                | Description     |
| ------ | ---------------------- | ------ | ------------ | ---------------------- | --------------- |
| POST   | `/createJob`           | Public | `CareersDto` | `201 CareersDto`       | Create          |
| GET    | `/getAll`              | Public | --           | `200 List<CareersDto>` | List all        |
| GET    | `/get/{id}`            | Public | --           | `200 CareersDto`       | Get one         |
| GET    | `/get/status/{status}` | Public | --           | `200 List<CareersDto>` | List by status  |
| PUT    | `/update/{id}`         | Public | `CareersDto` | `200 CareersDto`       | Partial update  |
| PUT    | `/update/{id}/{status}` | Public | --          | `200 JSONResponse`     | Set status      |
| DELETE | `/delete/{id}`         | Public | --           | `200 ApiResponse`      | Delete          |

### Applicant (v1)

Base route: `/api/v1/applicant`

| Method | Route                | Access | Request        | Success                  | Description |
| ------ | -------------------- | ------ | -------------- | ------------------------ | ----------- |
| POST   | `/addApplicant`      | Public | `ApplicantDto` | `200 ApplicantDto`       | Apply       |
| GET    | `/getApplicant/{id}` | Public | --             | `200 ApplicantDto`       | Get one     |
| GET    | `/getAllApplicant`   | Public | --             | `200 List<ApplicantDto>` | List all    |
| DELETE | `/delete/{id}`       | Public | --             | `200 JSONResponse`       | Delete      |

### Careers IN

Base route: `/api/v1/careers/IN`. Request and response fields are in [careers-applicant-in-api.md](careers-applicant-in-api.md).

| Method | Route                   | Access | Request                                                | Success                             | Description    |
| ------ | ----------------------- | ------ | ------------------------------------------------------ | ----------------------------------- | -------------- |
| POST   | `/createJob`            | Token  | `CareersCreateRequestDto`                              | `201 CareersResponseDto`            | Create         |
| GET    | `/getAll`               | Public | `?search&department&jobType&workStyle&page&size`       | `200 PageResponseDto`               | Paged list     |
| GET    | `/get/{id}`             | Public | --                                                     | `200 CareersResponseDto`            | Get one        |
| GET    | `/get/status/{status}`  | Public | --                                                     | `200 List<CareersResponseDto>`      | List by status |
| PUT    | `/update/{id}`          | Token  | `CareersUpdateRequestDto`                              | `200 CareersResponseDto`            | Partial update |
| PUT    | `/update/{id}/{status}` | Token  | --                                                     | `200 JsonResponseDto`               | Set status     |
| DELETE | `/delete/{id}`          | Token  | --                                                     | `200 ApiMessageDto`                 | Delete         |

### Applicant IN

Base route: `/api/v1/applicant/IN`

| Method | Route                | Access | Request                     | Success                    | Description |
| ------ | -------------------- | ------ | --------------------------- | -------------------------- | ----------- |
| POST   | `/addApplicant`      | Public | `ApplicantCreateRequestDto` | `200 ApplicantResponseDto` | Apply       |
| GET    | `/getApplicant/{id}` | Token  | --                          | `200 ApplicantResponseDto` | Get one     |
| GET    | `/getAllApplicant`   | Token  | `?search&page&size`         | `200 PageResponseDto`      | Paged list  |
| DELETE | `/delete/{id}`       | Token  | --                          | `200 JsonResponseDto`      | Delete      |

### Newsletter

Base route: `/api/v1/newsletters`

| Method | Route                          | Access | Request                      | Success                    | Description               |
| ------ | ------------------------------ | ------ | ---------------------------- | -------------------------- | ------------------------- |
| POST   | `/save`                        | Public | `NewsletterCreateRequestDto` | `201 NewsletterDto`        | Create                    |
| GET    | `/findAll`                     | Public | `?status`                    | `200 List<NewsletterDto>`  | List, optional status     |
| GET    | `/find/{id}`                   | Public | --                           | `200 NewsletterDto`        | Get by id                 |
| GET    | `/slug/{slug}`                 | Public | --                           | `200 NewsletterDto`        | Get by slug               |
| GET    | `/find/category/{category}`    | Public | --                           | `200 List<NewsletterDto>`  | List by category          |
| GET    | `/category/count`              | Public | --                           | `200 NewsLetterCategoryCountDto` | Count per category  |
| GET    | `/filter/{startDate}/{lastDate}` | Public | --                         | `200 List<NewsletterDto>`  | List by date range        |
| PUT    | `/update/{id}`                 | Public | `NewsletterDto`              | `200 NewsletterDto`        | Partial update            |
| PATCH  | `/{id}/status`                 | Public | `?status`                    | `200 NewsletterDto`        | Publish or unpublish      |
| DELETE | `/delete`                      | Public | `{ id: [] }`                 | `200 ApiResponse`          | Delete several            |
| POST   | `/add/subscribers`             | Public | `{ emailId }`                | `200 JSONResponse`         | Subscribe                 |
| GET    | `/subscribers`                 | Public | --                           | `200 NewsLetterSubscribersListDto` | List subscribers  |
| POST   | `/save/reply`                  | Public | `NewsLetterReplyDto`         | `201 NewsLetterReplyDto`   | Add a reply               |
| GET    | `/getall/reply/{newsLetterId}` | Public | --                           | `200 List<NewsLetterReplyDto>` | List replies          |
| GET    | `/{id}/reply-count`            | Public | --                           | `200 Long`                 | Count replies             |
| DELETE | `/delete/reply/{id}`           | Public | --                           | `200 JSONResponse`         | Delete a reply            |

### Author

Base route: `/api/v1/author`

| Method | Route          | Access | Request                                          | Success                | Description    |
| ------ | -------------- | ------ | ------------------------------------------------ | ---------------------- | -------------- |
| POST   | `/create`      | Public | Multipart: `authorData` + optional files         | `201 AuthorDto`        | Create         |
| GET    | `/getAll`      | Public | --                                               | `200 List<AuthorDto>`  | List all       |
| GET    | `/{slug}`      | Public | --                                               | `200 AuthorProfileDto` | Profile page   |
| PUT    | `/{authorId}`  | Public | Multipart: `authorData` + optional files         | `200 AuthorDto`        | Update         |
| DELETE | `/delete/{id}` | Public | --                                               | `200 ApiResponse`      | Delete         |

### Referral

Base route: `/api/v1/referral`

| Method | Route                      | Access | Request                    | Success                 | Description          |
| ------ | -------------------------- | ------ | -------------------------- | ----------------------- | -------------------- |
| POST   | `/submit`                  | Public | `ReferralCreateRequestDto` | `201 ReferralDto`       | Refer a friend       |
| GET    | `/getAll`                  | ADMIN  | `?query&startDate&endDate` | `200 List<ReferralDto>` | Search               |
| GET    | `/get/{id}`                | ADMIN  | --                         | `200 ReferralDto`       | Get by id            |
| GET    | `/number/{referralNumber}` | ADMIN  | --                         | `200 ReferralDto`       | Get by number        |
| DELETE | `/delete/{id}`             | ADMIN  | --                         | `200 JSONResponse`      | Delete               |

### Team

Base route: `/api/v1/team-members`

| Method | Route          | Access | Request   | Success             | Description |
| ------ | -------------- | ------ | --------- | ------------------- | ----------- |
| POST   | `/save`        | Public | `TeamDto` | `201 TeamDto`       | Create      |
| GET    | `/getAll`      | Public | --        | `200 List<TeamDto>` | List all    |
| GET    | `/get/{id}`    | Public | --        | `200 TeamDto`       | Get one     |
| PUT    | `/update/{id}` | Public | `TeamDto` | `200 TeamDto`       | Update      |
| DELETE | `/delete/{id}` | Public | --        | `200 TeamDtoMessage` | Delete     |

### Page Header

Base route: `/api/v1/page-header`

| Method | Route          | Access | Request         | Success                   | Description |
| ------ | -------------- | ------ | --------------- | ------------------------- | ----------- |
| POST   | `/save`        | Public | `PageHeaderDto` | `201 PageHeaderDto`       | Create      |
| GET    | `/getAll`      | Public | --              | `200 List<PageHeaderDto>` | List all    |
| GET    | `/get/{id}`    | Public | --              | `200 PageHeaderDto`       | Get one     |
| PUT    | `/update/{id}` | Public | `PageHeaderDto` | `200 PageHeaderDto`       | Update      |
| DELETE | `/delete/{id}` | Public | --              | `200 ApiResponse`         | Delete      |

### Department

Base route: `/api/v1/departments`

| Method | Route   | Access | Request | Success                | Description |
| ------ | ------- | ------ | ------- | ---------------------- | ----------- |
| GET    | `/`     | Public | --      | `200 List<Department>` | List all    |
| POST   | `/`     | Public | `?name` | `200 Department`       | Create      |
| DELETE | `/{id}` | Public | --      | `200 Message`          | Delete      |

### Home Page Count

Base route: `/api/v1/homepagecount`

| Method | Route          | Access | Request            | Success                | Description |
| ------ | -------------- | ------ | ------------------ | ---------------------- | ----------- |
| POST   | `/add`         | Public | `HomePageCountDto` | `201 HomePageCountDto` | Create      |
| GET    | `/get/{id}`    | Public | --                 | `200 HomePageCountDto` | Get         |
| PUT    | `/update/{id}` | Public | `HomePageCountDto` | `200 HomePageCountDto` | Update      |

### Image

Base route: `/api/v1/image`

| Method | Route           | Access | Request               | Success              | Description   |
| ------ | --------------- | ------ | --------------------- | -------------------- | ------------- |
| POST   | `/upload-image` | Public | Multipart: `image`    | `200 FileResponse`   | Upload        |
| GET    | `/image/{id}`   | Public | --                    | `200` image bytes    | Get the image |
| DELETE | `/delete/{id}`  | Public | --                    | `200` text           | Delete        |

### Blob

Base route: `/api/v1/blob`

| Method | Route                  | Access | Request                 | Success            | Description                 |
| ------ | ---------------------- | ------ | ----------------------- | ------------------ | --------------------------- |
| GET    | `/` (slash required)   | Public | --                      | `200 List<String>` | List all blob names         |
| GET    | `/download/{filename}` | Public | --                      | `200` file bytes   | Download one                |
| POST   | `/download/bulk`       | Public | `{ filenames: [] }`     | `200` ZIP          | Download several            |
| POST   | `/upload`              | Public | Multipart: `file`       | `200 JSONResponse` | Upload; returns stored name |
| DELETE | `/blob/{blobName}`     | Public | --                      | `200` text         | Delete                      |

### Excel Data

Base route: `/api/v1/ExcelData`

| Method | Route     | Access | Request        | Success                  | Description |
| ------ | --------- | ------ | -------------- | ------------------------ | ----------- |
| POST   | `/add`    | Public | `ExcelDataDTO` | `200 ExcelDataDTO`       | Create      |
| GET    | `/getAll` | Public | --             | `200 List<ExcelDataDTO>` | List all    |

### Excel Export

Base route: `/api/v1/export`

| Method | Route         | Access | Request | Success           | Description                |
| ------ | ------------- | ------ | ------- | ----------------- | -------------------------- |
| GET    | `/{category}` | Public | --      | `200` `.xlsx` file | Download leads by category |

---

## 9. Data Model

Twenty entities map to 24 tables, including four element-collection tables, plus one sequence.

| Table                              | Entity                          | Notes                                                        |
| ---------------------------------- | ------------------------------- | ------------------------------------------------------------ |
| `users`                            | `User`                          | `email` unique; `role` stored as a number                    |
| `otp`                              | `Otp`                           | One row per email in normal use                              |
| `roles_and_access`                 | `RolesAndAccessEntity`          | `email_id` unique; soft delete with `is_delete`              |
| `appointed_representative`         | `AppointedRepresentativeEntity` | All website leads; `form_data` is `TEXT` JSON                |
| `advisers`                         | `AdviserEntity`                 | `email` unique                                               |
| `careers`                          | `Careers`                       | `job_description` limited to 500 characters                  |
| `applicant`                        | `ApplicantEntity`               | `job_id` is text, no foreign key                             |
| `careers_in`                       | `CareersINEntity`               | `job_id` unique; CTC `NUMERIC(15,2)`                         |
| `careers_in_skill_options`         | Collection of `CareersINEntity` | Column `skill`                                               |
| `applicant_in`                     | `ApplicantINEntity`             | `cooldown_until` is `timestamptz`; no unique key             |
| `applicant_in_industry_experience` | Collection of `ApplicantINEntity` | Column `industry`                                          |
| `applicant_in_skills`              | Collection of `ApplicantINEntity` | Column `skill`                                             |
| `newsletters`                      | `NewsletterEntity`              | `url_slug` unique; `author_id` required foreign key          |
| `newslettersreply`                 | `NewsLetterReply`               | `news_letter_id` is a plain number                           |
| `news_letter_subscribers`          | `NewsLetterSubscribersEntity`   | No unique constraint; the service checks                     |
| `authors`                          | `AuthorEntity`                  | `url_slug` unique; image columns hold blob names             |
| `referrals`                        | `ReferralEntity`                | `referral_number` unique                                     |
| `referral_services`                | Collection of `ReferralEntity`  | Enum names as text                                           |
| `team_members`                     | `TeamEntity`                    |                                                              |
| `page_headers`                     | `PageHeaderEntity`              |                                                              |
| `departments`                      | `Department`                    | `name` unique                                                |
| `home_page_count_entity`           | `HomePageCountEntity`           | Intended as one row                                          |
| `images`                           | `ImageEntity`                   | Image bytes in a `@Lob` column                               |
| `excel_data`                       | `ExcelDataEntity`               |                                                              |

Sequence: `careers_in_job_number_seq`, created by `schema.sql`.

```
Only one real relationship exists in the schema:

    authors  1 ---------- *  newsletters
                             (author_id NOT NULL)

These links exist in the data but the database does not enforce them:

    newsletters.id      ...  newslettersreply.news_letter_id
    careers.job_id      ...  applicant.job_id
    careers_in.job_id   ...  applicant_in.job_id
    Azure blob name     ...  applicant.file_name, team_members.file_name,
                             applicant_in.resume_file_name, authors.author_picture, ...
```

---

## 10. Testing

Tests are in `src/test/java/com/wealthmax/adminPortal/`. There are 181 test methods. All but one are in
`careers_in/`, so the v1 code has no tests beyond `AdminPortalApplicationTests.contextLoads`.

| Test                                                 | Database | What It Covers                                                 |
| ---------------------------------------------------- | -------- | -------------------------------------------------------------- |
| `*ControllerTest`                                    | No       | Routes, status codes, validation errors                        |
| `*ServiceImplTest`                                   | No       | Business rules, cooldown, emails, with mocks                   |
| `*MapperTest`                                        | No       | Request to entity, entity to response                          |
| `*RequestValidationTest`                             | No       | DTO validation rules                                           |
| `INSecurityRulesTest`                                | No       | Real security config: which IN and v1 paths are open           |
| `INModuleBoundaryTest`                               | No       | IN imports nothing from v1 except the allowed classes          |
| `RequestResponseContractTest`                        | No       | Request and response DTOs stay separate                        |
| `*RepositoryTest`, `*SearchTest`                     | Yes      | Queries, specifications, paging on PostgreSQL                  |
| `INFullApplicationTest`                              | Yes      | Whole app end to end with mail mocked                          |

`INModuleBoundaryTest` allows only these imports from outside the module: the three exceptions in
`common.exception`, and `service.MailgunServices`.

The database tests run on the local PostgreSQL database from `application.properties`, and have two guards:

- `LocalDatabaseOnlyInitializer` refuses to start them unless the datasource host is this machine.
- `INTablesMustBeEmptyExtension` stops them if `careers_in` or `applicant_in` hold rows. It never deletes anything.

Run the IN suite with:

```
mvn test -Dtest='com.wealthmax.adminPortal.careers_in.**.*Test'
```

The repository has no CI pipeline definition or Dockerfile.

---

## 11. Known Issues and Risks

Found while writing this document. Nothing here has been fixed.

**How each item was checked:** `Run` means the behaviour was confirmed by executing it. Everything else
comes from reading the code. Secret values are deliberately not repeated here.

---

### Security

| ID  | Issue                                                                                                                  | Impact                                                                        | Where                                  |
| --- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | -------------------------------------- |
| S1  | Nearly every route is `permitAll`, including writes and deletes: `DELETE /AppointedRepresentative/deleteAll`, blob list/download/delete, applicant and lead lists, subscriber list, Excel export | Anyone on the internet can read personal data and resumes, and can delete records | `SecurityConfiguration`  |
| S2  | `POST /admin` and `PUT /user/update/{id}` are public and accept a role                                                 | Anyone can create an `ADMIN` or promote a user, with no login                 | `AdminController`, `UserController`    |
| S3  | A fixed master code in the source signs in any `ADMIN` with only their email                                           | No password or real OTP needed; anyone with source access can use it          | `AuthenticationServiceImpl.ADMIN_MASTER_OTP` |
| S4  | The JWT signing key is a literal in the source                                                                         | Anyone with source access can forge a token for any user                      | `JwtServiceImpl.getSignatureKey`       |
| S5  | Sign-in does not check that the OTP belongs to the email. The OTP is four digits from `java.util.Random`, has no attempt limit, is not deleted after use, and is written to the log | Any live code plus any email gives a token for that email; only 10,000 codes to try | `signin`, `OtpService`    |
| S6  | Database, Azure, Mailgun and SendGrid credentials are in a Git-tracked file                                            | Anyone with repository access has them; they also stay in Git history         | `application.properties`               |
| S7  | `PUT /user/forgetpassword/{email}` is public and changes the password at once                                          | Anyone can lock a user out; the new password travels by email                 | `UserServiceImpl.forgetPassword`       |
| S8  | Access and refresh tokens are interchangeable, nothing is stored, and sign-out does nothing                            | A leaked token works for up to 7 days and cannot be revoked                   | `JwtServiceImpl`, `deleteToken`        |
| S9  | "Token" routes accept any role, and `signup` is public                                                                 | A self-registered `USER` can read and delete IN applicants and edit IN jobs   | `SecurityConfiguration`                |
| S10 | Public image upload builds a server path from the client's file name                                                   | Possible path traversal outside the image folder (not tested)                 | `ImageServiceImpl.uploadImage`         |
| S11 | v1 emails insert form text into HTML without escaping                                                                  | Crafted form input can inject content into emails sent to staff               | `CreateEmailTemplateImpl`              |

---

### Bugs

| ID  | Issue                                                                                                 | Impact                                                                     | Where                                  | Checked |
| --- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | -------------------------------------- | ------- |
| B1  | `modelMapper` is never assigned                                                                       | Most Adviser endpoints return `500`; `update` saves first, then fails      | `AdviserServiceImpl`                   |         |
| B2  | Cron `* * * 1 * *` means every second on the 1st                                                      | 86,400 runs on the 1st; one job with a null `applyBy` stops the run. A daily job was probably intended | `CronJobs` | Run (schedule) |
| B3  | The catch-all handler turns Spring MVC errors into `500`                                              | Wrong method, wrong content type and unknown URL all return `500`, not `405`/`415`/`404` | `GlobalExceptionHandler` | Run  |
| B4  | JWT exceptions are not caught in the filter                                                           | An expired or bad token fails the request even on a public route, without the standard error body | `JwtAuthenticationFilter` | Run |
| B5  | `findByOtp` expects one row, but codes are not unique and old rows stay in the table                  | When a new code matches another user's row, sign-in fails with `500`, and this gets likelier as users grow | `OtpRepository`, `AppUtils.generateOtp` |  |
| B6  | `EmailTemplateOfIntroducerLead` uses `{{networkInterested}}`, which is commented out in the code      | Staff see the raw placeholder in lead emails                               | `CreateEmailTemplateImpl`              |         |
| B7  | Missing ids are passed to ModelMapper as null                                                         | `GET /image/{id}` and `GET /homepagecount/get/{id}` return `400` "source cannot be null", not `404`; `PUT /homepagecount/update/{id}` returns `500` | `ImageServiceImpl`, `HomePageCountServiceImpl` | Run (ModelMapper) |
| B8  | A Mailgun failure after a save fails the request                                                      | See the table in [section 4.7](#47-email). The worst case is forgot password, which leaves the user with no working password | Several services |     |
| B9  | Update methods skip fields                                                                            | Team `linkedInUrl`/`emailUrl`, Page Header `fileName` and v1 job `role`/`jobLocation` cannot be changed | `TeamServiceImpl`, `PageHeaderServiceImpl`, `CareersServiceImpl` | |
| B10 | `GET /adviser/counts` with `flag` other than 0, 1, 2                                                  | `500`, from a `NullPointerException`                                       | `AdviserController`                    |         |
| B11 | Newsletter read routes ignore status                                                                  | Unpublished articles are reachable by slug, id or category                 | `NewsletterServiceImpl`                |         |
| B12 | v1 job ids are read from the last row, and the column is not unique                                   | Simultaneous creates can produce the same `jobId`                          | `CareersServiceImpl.generateCaseId`    |         |
| B13 | Roles duplicate check includes soft-deleted rows                                                      | A removed person cannot be added again                                     | `RolesAndAccessServiceImpl`            |         |
| B14 | `RequestLoggingFilter` runs after the security chain                                                  | Rejected requests do not appear in the request log, so attacks are not visible there | `RequestLoggingFilter`      |         |
| B15 | Job lists sort on the text `jobId`                                                                    | Order breaks once ids pass four digits (`WFA-IN-10000` sorts below `WFA-IN-9999`) | `CareersINServiceImpl`, `CareersServiceImpl` | |

---

### Maintenance

| ID  | Issue                                                                                                                                  |
| --- | -------------------------------------------------------------------------------------------------------------------------------------- |
| M1  | No schema migrations. `ddl-auto=update` cannot drop or alter columns, so those changes have to be run by hand on each environment.     |
| M2  | SendGrid is dead code: `EmailServiceImpl`, `SendgridConfig`, the dependency, and the `emailService` field in five services. The app still needs its API key to start. |
| M3  | MapStruct is configured but unused. `EmailDetails.java` is entirely commented out.                                                     |
| M4  | Unused code: `getLoggedInUser`, `validateApplicant`, `sendEmailsOfCreatingNewsLetters`, `getEmailTemplateDataOfCallBackForm`, `findByCreatedDate`, `countOfAppointedRepresentativePercentage`, `findTop4ByAuthor_IdOrderByCreatedAtDesc`, `countByAuthor_Id`, `findByReferrerEmailIgnoreCase`, the attachment `sendEmail` overloads, and the `path` field in three controllers. |
| M5  | `getEmailTemplate` is copied into seven services.                                                                                      |
| M6  | Two BCrypt strengths are in use (8 and 10).                                                                                            |
| M7  | `CommonDateTimeReq` is a JPA `@MappedSuperclass` that is also extended by DTOs, so request bodies accept `createdAt` and `updatedAt`.  |
| M8  | No v1 list endpoint is paged. Each loads the whole table and sorts it in Java.                                                         |
| M9  | No database indexes are declared for search or filter columns.                                                                         |
| M10 | `Open Session in View` is relied on implicitly ([section 4.5](#45-persistence)), and `allow-circular-references=true` hides a dependency cycle. |
| M11 | `RestTemplate` has no timeouts, so a hung Mailgun call blocks the request thread.                                                      |
| M12 | `README.md` is still the Azure DevOps template. `Message` sits in `entity/` although it is not an entity.                              |

---

*End of Admin Portal Backend Documentation*
