# Careers & Applicant — IN API Design

> **Status:** Implemented in the self-contained `careers_in` module (`com.wealthmax.adminPortal.careers_in`)
> **Scope:** New structured **request and response DTOs** (separate classes), **new database tables**, **new controllers, services, repositories and mappers** for job postings (`/api/v1/careers/IN`) and job applications (`/api/v1/applicant/IN`).
> **Golden rule:** Nothing that exists today is touched or reused. IN is its own module with its own DTOs, entities, repositories, services and mappers (replicating the v1 logic), and its own tables. The v1 code, tables and endpoints stay exactly as they are.
> **Naming:** `IN` is the India market. The existing code (v1) serves the UK market and stays as it is; the two are parallel and neither replaces the other. This module was first called `v2`; it was renamed to `IN` everywhere ([Decision R](#16-decisions)).

---

## Table of contents

1. [Summary](#1-summary)
2. [What does not change](#2-what-does-not-change)
3. [Resume upload flow (Blob API)](#3-resume-upload-flow-blob-api)
4. [New entities & tables](#4-new-entities--tables)
5. [DTOs](#5-dtos)
6. [Applicant IN API](#6-applicant-in-api)
7. [Careers IN API](#7-careers-in-api)
8. [Controller layer](#8-controller-layer)
9. [Service layer](#9-service-layer)
10. [Mapper layer](#10-mapper-layer)
11. [Validation & error responses](#11-validation--error-responses)
12. [Duplicate-application protection (cooling period)](#12-duplicate-application-protection-cooling-period)
13. [Job ID generation (`WFA-IN-####`)](#13-job-id-generation-wfa-in-)
14. [Database impact & deployment](#14-database-impact--deployment)
15. [File list](#15-file-list)
16. [Decisions](#16-decisions)
17. [Test checklist](#17-test-checklist)
18. [Known limitations & follow-ups](#18-known-limitations--follow-ups)

---

## 1. Summary

Today both DTOs are flat lists of fields. The IN API organises the applicant form into **five steps** (matching the Figma design) stored flat in `applicant_in`, and the careers form into **three sections** in `careers_in`:

| Module | Section 1 | Section 2 | Section 3 | Section 4 | Section 5 | New table |
|---|---|---|---|---|---|---|
| **Applicant** (people apply) | `personalDetails` | `educationDetails` | `experienceDetails` | `availabilityDetails` | `finalDetails` | `applicant_in` |
| **Careers** (admin posts jobs) | `jobDetails` | `experienceRequirements` | `salaryDetails` | — | — | `careers_in` |

> **Note:** The five applicant sections map to the five Figma form steps: Step 01 Personal details (including the resume and cover-letter documents), Step 02 Education, Step 03 Experience, Step 04 Availability & pay, Step 05 Final details. All sections are stored flat in a single `applicant_in` table.

Key design choices:

- **A separate module.** All IN code lives under `com.wealthmax.adminPortal.careers_in` (the package holds both the careers and the applicant parts; `careers_in.applicant`, `careers_in.careers`, `careers_in.common`), split into `controller`, `service`, `repository`, `entity`, `dto` and `mapper` sub-packages. No IN class imports a v1 entity, DTO, repository, service or mapper.
- **Separate request and response DTOs.** Each endpoint takes a request DTO and returns a different response DTO ([section 5](#5-dtos)). The request carries the validation and has no server-managed fields (`id`, `createdAt`, a job's own `jobId`, `datePosted`, `createdDate`); the response carries those fields and no validation. A client therefore cannot set a server-managed field, validation lives only where input is read, and the job update endpoint has its own request type whose rules let an unsent (`null`) field through, so a partial update still works.
- **New tables, old tables untouched.** The new entities `ApplicantINEntity` and `CareersINEntity` map to `applicant_in` and `careers_in`. The v1 entities, tables and data are not modified at all.
- **New controllers, new paths.** `ApplicantINController` serves `/api/v1/applicant/IN/...` and `CareersINController` serves `/api/v1/careers/IN/...`. Only the calls the public website needs are open (`POST /applicant/IN/addApplicant` and the three `GET` job endpoints); every other IN call needs a JWT. `SecurityConfiguration` has explicit rules for this, placed **before** the old `permitAll` wildcards `/api/v1/applicant/**` and `/api/v1/careers/**`, because the first matching rule wins ([Decision M](#16-decisions), [section 6](#6-applicant-in-api), [section 7](#7-careers-in-api)).
- **Replicated business logic.** IN follows the same steps as v1: the same defaults (`datePosted`, `createdDate`), sorting, emails, response shapes and error messages. The logic is **copied into the IN services**, not called from v1 (including the email-sending and template-filling code). Only shared infrastructure is reused: the `common.exception` classes and `GlobalExceptionHandler`, the `MailgunServices` transport, and the HTML email template files.
- **Paged, searchable lists.** `GET /careers/IN/getAll` and `GET /applicant/IN/getAllApplicant` return one page (`content`, `totalElements`, …) and accept a global `search`; the careers list also filters by `department`, `jobType` and `workStyle` ([sections 5.4, 6.3 and 7.2](#54-list-endpoint-query-and-page-dtos)). This is a deliberate difference from v1, whose list endpoints return everything.
- **Same resume flow.** The resume is still uploaded via `POST /api/v1/blob/upload` first; the applicant API only receives the returned file name.
- **v1 and IN data are separate.** v1 list endpoints show only v1 records; IN list endpoints show only IN records. No data is migrated.
- **Cooling period for applicants.** After applying, the same email + mobile number cannot apply again, for any job, for 6 months (`cooldown_until`, [section 12](#12-duplicate-application-protection-cooling-period)). There is no unique key on `applicant_in`.
- **Own job-id series.** IN jobs get ids in the form `WFA-IN-0001`, `WFA-IN-0002`, … from their own PostgreSQL sequence (`careers_in_job_number_seq`), so they can never clash with v1 ids (`WFA0001`, …), are never handed out twice, and are not reused after a job is deleted ([section 13](#13-job-id-generation-wfa-in-)).
- **Job status is an enum.** A job is `ACTIVE` or `INACTIVE` (`JobStatusEnum`); any other status is rejected with `400` instead of being stored as free text ([section 4.3](#43-new-enums)).
- **Update is validated.** `PUT /careers/IN/update/{id}` applies the same rules as create to whatever it is sent, and also checks the min/max ranges of the finished job ([section 5.2.2](#522-update-request-dtorequest), [section 7.4](#74-put-apiv1careersinupdateid)).

---

## 2. What does not change

| Item | Status |
|---|---|
| `dto/ApplicantDto.java`, `dto/CareersDto.java` | Untouched |
| `entity/ApplicantEntity.java`, `entity/Careers.java` | Untouched |
| Tables `applicant` and `careers` (columns, data, constraints) | Untouched |
| `repository/ApplicantRepository.java`, `repository/CareersRepository.java` | Untouched |
| `controller/ApplicantController.java`, `controller/CareersController.java` and every v1 endpoint | Untouched |
| `service/ApplicantService`, `CareersService` and their `impl` classes | Untouched |
| `MyBlobRestController` / `MyBlobService` | Untouched |
| `MapperConfiguration` (ModelMapper setup) | Untouched |
| `SecurityConfiguration` | **Edited**: explicit access rules for the IN paths, ahead of the old wildcards ([Decision M](#16-decisions)). The v1 rules are unchanged |
| `GlobalExceptionHandler` and the `common.exception` classes | Untouched (reused as shared infrastructure) |
| `MailgunServices`, email templates (`EmailTemplateOfCareers.html`, `EmailTemplateOfLeadFromApplicant.html`) and `CreateEmailTemplates` | Untouched (`CreateEmailTemplates` is not used by IN; the other two are reused) |

**One existing Java source file is edited: `SecurityConfiguration`** (the access rules for the IN paths). The other changes outside the new `careers_in` packages are in `application.properties` (the `springdoc.packagesToScan` line that makes Swagger UI show the IN controllers, see [section 8](#8-controller-layer), and two properties that run `schema.sql`, see [section 14](#14-database-impact--deployment)), a new `src/main/resources/schema.sql` (the job-number sequence) and test scaffolding in `src/test` only. `INModuleBoundaryTest` also allows IN to use one more shared class, `InvalidDataException`. The IN tests run on the application's own local PostgreSQL database ([section 15](#new-test-files)); no test-only database is added to `pom.xml`.

---

## 3. Resume upload flow (Blob API)

The v1 flow is unchanged, and IN uses the same contract.

```
Frontend                         Backend                                  Azure Blob
   │                                │                                         │
   │ 1. POST /api/v1/blob/upload    │                                         │
   │    multipart/form-data         │                                         │
   │    part name: "file"           │                                         │
   │───────────────────────────────▶│ fileName = Instant.now() + original     │
   │                                │ myBlobService.storeFile(...) ──────────▶│ stored
   │◀───────────────────────────────│                                         │
   │   { "response": "2026-10-16T10:15:30.123Zjohn_cv.pdf" }                  │
   │                                │                                         │
   │ 2. POST /api/v1/applicant/IN/addApplicant                                │
   │    personalDetails.resumeFileName = "<value of response>"                │
   │───────────────────────────────▶│ saved into applicant_in.resume_file_name│
   │◀───────────────────────────────│                                         │
   │                                                                          │
   │ Admin: GET /api/v1/blob/download/{resumeFileName}  → works unchanged     │
```

Notes:

- The blob endpoint prefixes the original file name with an ISO timestamp, without a separator (for example `2026-10-16T10:15:30.123Zjohn_cv.pdf`). The frontend must send the `response` value **exactly as returned**.
- The optional cover letter goes through the same endpoint; its returned name is sent as `personalDetails.coverLetterFileName`.
- Resumes for v1 and IN live in the same Azure container, so `/blob/download/{filename}` and `/blob/download/bulk` work for both.
- Max upload size is 10 MB (`spring.servlet.multipart.max-file-size`).
- The applicant API does **not** check that the blob exists, which matches v1 behaviour.

---

## 4. New entities & tables

Column names follow Spring Boot's default snake_case naming (for example `preferredJobLocation` → `preferred_job_location`). Lombok annotations follow the existing entities (`@Data @NoArgsConstructor @AllArgsConstructor`). Entities live in `careers_in.applicant.entity` and `careers_in.careers.entity`.

### 4.1 `ApplicantINEntity` → table `applicant_in`

| Field | Java type | Column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | PK, `IDENTITY` |
| `jobId` | `String` | `job_id` | The job applied for (for example `WFA-IN-0012`) |
| `jobTitle` | `String` | `job_title` | |
| `createdAt` | `LocalDateTime` | `created_at` | `@CreationTimestamp` |
| `cooldownUntil` | `Instant` | `cooldown_until` | `timestamp with time zone`, nullable. Set by the service only (never from a request) to the submit time in UTC + 6 months; until then the same email + mobile cannot apply again ([section 12](#12-duplicate-application-protection-cooling-period)) |

**Personal details** *(Figma Step 01)*

| Field | Java type | Column | Notes |
|---|---|---|---|
| `fullName` | `String` | `full_name` | **not null** |
| `emailId` | `String` | `email_id` | **not null**, part of the cooling-period check (case-insensitive) |
| `mobileNumber` | `String` | `mobile_number` | **not null**, part of the cooling-period check (exact) |
| `location` | `String` | `location` | Current city — **not null** (required in Figma Step 01) |
| `hometown` | `String` | `hometown` | Hometown / preferred job location — **not null** (Figma "Hometown" field, required) |
| `linkedInUrl` | `String` | `linked_in_url` | |
| `portfolioUrl` | `String` | `portfolio_url` | Portfolio / GitHub / personal website *(optional)* |
| `gender` | `GenderEnum` | `gender` | `@Enumerated(EnumType.STRING)` |
| `maritalStatus` | `MaritalStatusEnum` | `marital_status` | `@Enumerated(EnumType.STRING)` |
| `resumeFileName` | `String` | `resume_file_name` | Blob name returned by `/blob/upload` (Figma Step 01 Documents) |
| `coverLetterFileName` | `String` | `cover_letter_file_name` | Optional cover-letter blob name (Figma Step 01 Documents) |

**Education details** *(Figma Step 02)*

| Field | Java type | Column | Notes |
|---|---|---|---|
| `highestQualification` | `HighestQualificationEnum` | `highest_qualification` | `@Enumerated(EnumType.STRING)`, **not null** |
| `degreeCourse` | `String` | `degree_course` | e.g. "B.Com, BCA, MBA, CA" — **not null** |
| `specialisation` | `String` | `specialisation` | Optional |
| `collegeUniversityBoard` | `String` | `college_university_board` | **not null** |
| `yearOfPassing` | `Integer` | `year_of_passing` | **not null** |
| `percentageOrCgpa` | `String` | `percentage_or_cgpa` | Optional, stored as String to allow "72%" or "7.8 CGPA" |
| `otherQualifications` | `String` | `other_qualifications` | `columnDefinition = "TEXT"`, optional — other certs / courses |
| `hasEducationGap` | `Boolean` | `has_education_gap` | **not null** (required Yes/No) |
| `educationGapDetails` | `String` | `education_gap_details` | `columnDefinition = "TEXT"` — required when `hasEducationGap = true` |

**Experience details** *(Figma Step 03)*

| Field | Java type | Column | Notes |
|---|---|---|---|
| `currentCompany` | `String` | `current_company` | **not null** |
| `currentDesignation` | `String` | `current_designation` | **not null** |
| `totalExperienceYears` | `Integer` | `total_experience_years` | |
| `totalExperienceMonths` | `Integer` | `total_experience_months` | 0–11 |
| `relevantExperienceYears` | `Integer` | `relevant_experience_years` | |
| `relevantExperienceMonths` | `Integer` | `relevant_experience_months` | 0–11 |
| `previousCompanies` | `String` | `previous_companies` | `columnDefinition = "TEXT"`, one per line (company, role, from–to year) — **not null** |
| `industryExperience` | `List<String>` | table `applicant_in_industry_experience` (`applicant_id`, `industry`) | `@ElementCollection(fetch = EAGER)` with `@BatchSize(size = 50)` — multi-select domain tags (Financial Services, IT/Software, …) |
| `skills` | `List<String>` | table `applicant_in_skills` (`applicant_id`, `skill`) | `@ElementCollection(fetch = EAGER)` with `@BatchSize(size = 50)` — key skills chosen from the job's `skillOptions` |
| `otherSkills` | `String` | `other_skills` | Free-text additional skills not in the preset list |
| `hasEmploymentGap` | `Boolean` | `has_employment_gap` | |
| `employmentGapDetails` | `String` | `employment_gap_details` | `columnDefinition = "TEXT"` — required when `hasEmploymentGap = true` |

**Availability & compensation details** *(Figma Step 04)*

| Field | Java type | Column | Notes |
|---|---|---|---|
| `noticePeriod` | `String` | `notice_period` | Dropdown value e.g. "30 days", "Immediate" |
| `currentCtc` | `BigDecimal` | `current_ctc` | `precision = 15, scale = 2`, annual, in LPA (lakhs per annum, e.g. `4.5`); the request sends it as text matching `^\d{1,2}(\.\d+)?$` ([Decision S](#16-decisions)) |
| `expectedCtc` | `BigDecimal` | `expected_ctc` | `precision = 15, scale = 2`, annual, in LPA (as `currentCtc`) |
| `hasOtherOffer` | `Boolean` | `has_other_offer` | Other offer in hand? |
| `otherOfferDetails` | `String` | `other_offer_details` | `columnDefinition = "TEXT"` — required when `hasOtherOffer = true` |
| `willingToRelocate` | `Boolean` | `willing_to_relocate` | |
| `shiftComfort` | `ShiftComfortEnum` | `shift_comfort` | `@Enumerated(EnumType.STRING)` — YES / NO / WANT_TO_DISCUSS |

**Final details** *(Figma Step 05)*

| Field | Java type | Column | Notes |
|---|---|---|---|
| `howDidYouHearAboutUs` | `String` | `how_did_you_hear_about_us` | Dropdown — required per Figma |
| `declarationAccepted` | `Boolean` | `declaration_accepted` | Must be `true` to submit (checked on the request DTO) |

**No unique key** (see [section 12](#12-duplicate-application-protection-cooling-period)): the table is declared as plain `@Table(name = "applicant_in")`. The earlier `uk_applicant_v2_identity` on `(full_name, email_id, mobile_number)` was removed because a unique key blocks a person forever, while the rule is a time-limited cooling period. `ddl-auto=update` does not drop it from an existing database; see the manual SQL in [section 14](#14-database-impact--deployment).

### 4.2 `CareersINEntity` → table `careers_in`

| Field | Java type | Column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` | PK, `IDENTITY` |
| `jobId` | `String` | `job_id` | `unique = true`, generated as `WFA-IN-0001`, `WFA-IN-0002`, … from a database sequence ([section 13](#13-job-id-generation-wfa-in-)) |
| `status` | `JobStatusEnum` | `status` | `@Enumerated(EnumType.STRING)`: `ACTIVE` or `INACTIVE` ([section 4.3](#43-new-enums)). v1 stores free text |
| `datePosted` | `LocalDate` | `date_posted` | Server-set |
| `createdDate` | `Instant` | `created_date` | Server-set |

**Job details** (the same fields as v1 `Careers`)

| Field | Java type | Column | Notes |
|---|---|---|---|
| `jobTitle` | `String` | `job_title` | |
| `department` | `String` | `department` | |
| `role` | `String` | `role` | |
| `jobType` | `String` | `job_type` | |
| `workStyle` | `String` | `work_style` | |
| `jobLocation` | `String` | `job_location` | |
| `jobDescription` | `String` | `job_description` | `columnDefinition = "TEXT"` (v1 is limited to 500 characters) |
| `applyBy` | `LocalDate` | `apply_by` | |
| `note` | `String` | `note` | |

**Experience requirements**

| Field | Java type | Column | Notes |
|---|---|---|---|
| `officeTiming` | `String` | `office_timing` | The required shift / timings |
| `officeDay` | `String` | `office_day` | |
| `requiredSkill` | `String` | `required_skill` | The skill an applicant reports years of experience in |
| `skillOptions` | `List<String>` | table `careers_in_skill_options` (`careers_id`, `skill`) | `@ElementCollection(fetch = EAGER)` with `@BatchSize(size = 50)`. The pre-selected skills applicants pick from |
| `minExperienceYears` | `Integer` | `min_experience_years` | |
| `maxExperienceYears` | `Integer` | `max_experience_years` | |
| `noticePeriodAccepted` | `String` | `notice_period_accepted` | For example "Up to 60 days" |

**Salary details**

| Field | Java type | Column | Notes |
|---|---|---|---|
| `ctcMin` | `BigDecimal` | `ctc_min` | `precision = 15, scale = 2` |
| `ctcMax` | `BigDecimal` | `ctc_max` | `precision = 15, scale = 2` |
| `relocationRequired` | `Boolean` | `relocation_required` | |

> There is no composite unique key on `careers_in`. A job posting has no name, email or mobile fields. See [Decision D](#16-decisions).

### 4.3 New enums

These follow the existing naming convention (`NewsletterStatusEnum`, `RoleEnum`, …). The four applicant enums live in `careers_in.applicant.entity`:

```java
public enum GenderEnum               { MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY }
public enum MaritalStatusEnum        { SINGLE, MARRIED, DIVORCED, WIDOWED, PREFER_NOT_TO_SAY }
public enum HighestQualificationEnum { HIGH_SCHOOL, DIPLOMA, BACHELORS, MASTERS, PHD, OTHER }
public enum ShiftComfortEnum         { YES, NO, WANT_TO_DISCUSS }
```

The careers module has one, in `careers_in.careers.entity`:

```java
public enum JobStatusEnum            { ACTIVE, INACTIVE }
```

`JobStatusEnum` replaces the free-text status v1 uses. It is used by the entity (`@Enumerated(EnumType.STRING)`), by the create request and the response, and by the two status endpoints, where it is read straight from the path. A status that is not one of the two values returns `400` on create, on `PUT /update/{id}/{status}` and on `GET /get/status/{status}`. Matching is **case-sensitive** (`ACTIVE`, not `active`), so a wrong-case status is rejected instead of silently matching nothing. Because the column is an enum, `status` is no longer one of the global search columns of `getAll` ([section 7.2](#72-get-apiv1careersingetall)); the status endpoint is the way to filter by it.

> **Removed:** `EmploymentStatusEnum` is not created. The Figma review removed the "current employment status" field, so nothing uses it.

Enums are stored as strings, and Hibernate 6 also puts a `CHECK` constraint on each enum column listing the allowed values (see [section 14](#14-database-impact--deployment)). `ddl-auto=update` creates that constraint with the table but never changes it afterwards, so **adding an enum value later needs a manual SQL change** to that constraint. An unknown value in a request returns `400` with "Gender has an invalid value" (handled by the existing `GlobalExceptionHandler`).

### 4.4 New repositories

```java
@Repository
public interface ApplicantINRepository
        extends JpaRepository<ApplicantINEntity, Long>, JpaSpecificationExecutor<ApplicantINEntity> {
    Optional<ApplicantINEntity> findFirstByEmailIdIgnoreCaseAndMobileNumberAndCooldownUntilAfterOrderByCooldownUntilDesc(
            String emailId, String mobileNumber, Instant now);     // cooling period not over yet; latest one first

    @Query(value = "select 1 from pg_advisory_xact_lock(:key)", nativeQuery = true)
    Integer lockApplicant(@Param("key") long key);                 // one request per email + mobile at a time
}

@Repository
public interface CareersINRepository
        extends JpaRepository<CareersINEntity, Long>, JpaSpecificationExecutor<CareersINEntity> {
    List<CareersINEntity> findAllByStatus(JobStatusEnum status, Sort sort);

    @Query(value = "select nextval('careers_in_job_number_seq')", nativeQuery = true)
    long nextJobNumber();                                   // next number of the WFA-IN-#### id
}
```

Both repositories also extend `JpaSpecificationExecutor`, which gives `findAll(Specification, Pageable)`. The list endpoints use it to combine the optional search and filters into one query ([section 5.4](#54-list-endpoint-query-and-page-dtos)).

---

## 5. DTOs

Every endpoint uses the **request / response approach**: a *request DTO* describes what a client sends, a *response DTO* describes what the server returns, and they are **separate classes**. No class does both jobs.

| | Request DTO | Response DTO |
|---|---|---|
| Direction | Client → server (`@RequestBody`) | Server → client (`ResponseEntity<…>`) |
| Server-managed fields (`id`, `createdAt`, a job's own `jobId`, `datePosted`, `createdDate`) | **Not present.** A client cannot set them: if one is sent anyway, JSON parsing ignores it | Present |
| Validation annotations | Yes (`@NotBlank`, `@Valid`, `@AssertTrue`, …) | None: the server builds it from a stored row |
| Built by | Jackson, from the request body | The mapper, from the entity |
| Package | `…dto.request` | `…dto.response` |

All IN DTOs use Lombok `@Data @NoArgsConstructor @AllArgsConstructor`, live in the `request` and `response` sub-packages of `careers_in.applicant.dto` and `careers_in.careers.dto`, and are separate classes from the v1 DTOs.

**Naming.** The repository already names input classes `…RequestDto` (`ReferralCreateRequestDto`, `NewsletterCreateRequestDto`), so IN follows that:

| Role | Applicant | Careers |
|---|---|---|
| Create request | `ApplicantCreateRequestDto` | `CareersCreateRequestDto` |
| Update request | none (there is no applicant update endpoint) | `CareersUpdateRequestDto` |
| Response | `ApplicantResponseDto` | `CareersResponseDto` |
| Section classes | `Applicant<Section>DetailsRequestDto` / `Applicant<Section>DetailsResponseDto` | `Job<Section>RequestDto` / `Job<Section>UpdateRequestDto` / `Job<Section>ResponseDto` |

### 5.1 Applicant DTOs (`careers_in.applicant.dto`)

#### 5.1.1 Request (`dto.request`)

```
ApplicantCreateRequestDto
├── String  jobId                                          (required)
├── String  jobTitle
├── ApplicantPersonalDetailsRequestDto      personalDetails      (required, @Valid)
├── ApplicantEducationDetailsRequestDto     educationDetails     (required, @Valid)
├── ApplicantExperienceDetailsRequestDto    experienceDetails    (required, @Valid)
├── ApplicantAvailabilityDetailsRequestDto  availabilityDetails  (@Valid)
└── ApplicantFinalDetailsRequestDto         finalDetails         (required, @Valid)
```

`id` and `createdAt` are not on the request.

**`ApplicantPersonalDetailsRequestDto`** (Figma Step 01)

| Field | Type | Validation |
|---|---|---|
| `fullName` | String | `@NotBlank` |
| `emailId` | String | `@NotBlank`, `@Email` |
| `mobileNumber` | String | `@NotBlank`, `@Pattern("^\\+?[0-9 ()-]{7,20}$")` |
| `location` | String | `@NotBlank` — current city |
| `hometown` | String | `@NotBlank` — hometown / preferred location |
| `linkedInUrl` | String | `@URL` (optional) |
| `portfolioUrl` | String | `@URL` (optional) |
| `gender` | GenderEnum | optional |
| `maritalStatus` | MaritalStatusEnum | optional |
| `resumeFileName` | String | `@NotBlank` — blob name from `/blob/upload` |
| `coverLetterFileName` | String | optional — cover letter blob name |

**`ApplicantEducationDetailsRequestDto`** (Figma Step 02)

| Field | Type | Validation |
|---|---|---|
| `highestQualification` | HighestQualificationEnum | `@NotNull` |
| `degreeCourse` | String | `@NotBlank` |
| `specialisation` | String | optional |
| `collegeUniversityBoard` | String | `@NotBlank` |
| `yearOfPassing` | Integer | `@NotNull`, `@Min(1950)` |
| `percentageOrCgpa` | String | optional |
| `otherQualifications` | String | optional — textarea |
| `hasEducationGap` | Boolean | `@NotNull` |
| `educationGapDetails` | String | Required when `hasEducationGap = true` (`@AssertTrue`, `@JsonIgnore`) |

**`ApplicantExperienceDetailsRequestDto`** (Figma Step 03)

| Field | Type | Validation |
|---|---|---|
| `totalExperienceYears` | Integer | `@Min(0)` |
| `totalExperienceMonths` | Integer | `@Min(0) @Max(11)` |
| `relevantExperienceYears` | Integer | `@Min(0)` |
| `relevantExperienceMonths` | Integer | `@Min(0) @Max(11)` |
| `currentCompany` | String | `@NotBlank` |
| `currentDesignation` | String | `@NotBlank` |
| `previousCompanies` | String | `@NotBlank` — textarea, one entry per line |
| `industryExperience` | List&lt;String&gt; | `@NotEmpty` — domain/industry tags |
| `skills` | List&lt;String&gt; | chosen from the job's `skillOptions` |
| `otherSkills` | String | optional — free-text extra skills |
| `hasEmploymentGap` | Boolean | |
| `employmentGapDetails` | String | Required when `hasEmploymentGap = true` (`@AssertTrue`, `@JsonIgnore`) |

**`ApplicantAvailabilityDetailsRequestDto`** (Figma Step 04)

| Field | Type | Validation |
|---|---|---|
| `noticePeriod` | String | `@NotBlank` |
| `currentCtc` | String | optional; `@Pattern("^\\d{1,2}(\\.\\d+)?$")`: in LPA, 1 or 2 digits before the decimal point and an optional decimal part (`4.5`, `5`, `12`). A JSON number is accepted too. The mapper turns it into the entity's `BigDecimal` ([Decision S](#16-decisions)) |
| `expectedCtc` | String | optional; same rule as `currentCtc` |
| `hasOtherOffer` | Boolean | |
| `otherOfferDetails` | String | Required when `hasOtherOffer = true` (`@AssertTrue`, `@JsonIgnore`) |
| `willingToRelocate` | Boolean | |
| `shiftComfort` | ShiftComfortEnum | Yes / No / Want to discuss |

**`ApplicantFinalDetailsRequestDto`** (Figma Step 05)

| Field | Type | Validation |
|---|---|---|
| `howDidYouHearAboutUs` | String | `@NotBlank` — dropdown selection |
| `declarationAccepted` | Boolean | `@NotNull` + `@AssertTrue` — must be `true` (`@AssertTrue` alone accepts a missing value); message: "You must accept the declaration to submit your application" |

`finalDetails` itself is `@NotNull` on the root request DTO, so the declaration cannot be skipped by leaving the whole section out.

#### 5.1.2 Response (`dto.response`)

```
ApplicantResponseDto
├── Long          id
├── String        jobId
├── String        jobTitle
├── LocalDateTime createdAt
├── ApplicantPersonalDetailsResponseDto      personalDetails
├── ApplicantEducationDetailsResponseDto     educationDetails
├── ApplicantExperienceDetailsResponseDto    experienceDetails
├── ApplicantAvailabilityDetailsResponseDto  availabilityDetails
└── ApplicantFinalDetailsResponseDto         finalDetails
```

Each response section has **exactly the same fields** (names and types) as its request section in the tables above, with no validation annotations and none of the `…Present` check methods. `ApplicantResponseDto` always contains all five sections: they are built from the stored row, so a section the applicant skipped (for example `availabilityDetails`) comes back with its fields set to `null`. The `skills` and `industryExperience` lists are copies, not the entity's own collections.

### 5.2 Careers DTOs (`careers_in.careers.dto`)

#### 5.2.1 Create request (`dto.request`)

```
CareersCreateRequestDto
├── JobStatusEnum  status                (ACTIVE or INACTIVE; optional)
├── JobDetailsRequestDto                 jobDetails              (required, @Valid)
├── JobExperienceRequirementsRequestDto  experienceRequirements  (@Valid)
└── JobSalaryDetailsRequestDto           salaryDetails           (@Valid)
```

`id`, `jobId`, `datePosted` and `createdDate` are not on the request: the server sets them ([section 7.1](#71-post-apiv1careersincreatejob)).

**`JobDetailsRequestDto`**

| Field | Type | Validation |
|---|---|---|
| `jobTitle` | String | `@NotBlank` |
| `department` | String | |
| `role` | String | |
| `jobType` | String | |
| `workStyle` | String | |
| `jobLocation` | String | |
| `jobDescription` | String | |
| `applyBy` | LocalDate | |
| `note` | String | |

**`JobExperienceRequirementsRequestDto`**

| Field | Type | Validation |
|---|---|---|
| `officeTiming` | String | The required shift/timings |
| `officeDay` | String | |
| `requiredSkill` | String | |
| `skillOptions` | List&lt;String&gt; | |
| `minExperienceYears` | Integer | `@Min(0)` |
| `maxExperienceYears` | Integer | `@Min(0)`, must be ≥ min (`@AssertTrue`, `@JsonIgnore`) |
| `noticePeriodAccepted` | String | |

**`JobSalaryDetailsRequestDto`**

| Field | Type | Validation |
|---|---|---|
| `ctcMin` | BigDecimal | `@PositiveOrZero` |
| `ctcMax` | BigDecimal | `@PositiveOrZero`, must be ≥ min (`@AssertTrue`, `@JsonIgnore`) |
| `relocationRequired` | Boolean | |

#### 5.2.2 Update request (`dto.request`)

```
CareersUpdateRequestDto
├── JobDetailsUpdateRequestDto                 jobDetails
├── JobExperienceRequirementsUpdateRequestDto  experienceRequirements
└── JobSalaryDetailsUpdateRequestDto           salaryDetails
```

Update (`PUT`) is a partial update, so it has its **own request type**. Every section and every field is optional: `null` means "not sent, leave as it is" ([section 7.4](#74-put-apiv1careersinupdateid)). The three section classes have the same fields as their create counterparts above. A value that **is** sent follows the same rules as on create, so an update cannot store what create would reject (the endpoint uses `@Valid`):

| Field | Rule, if it is sent |
|---|---|
| `jobDetails.jobTitle` | Not blank (`@Pattern`; a `null` is still "not sent", which is why it is not `@NotBlank`) |
| `experienceRequirements.minExperienceYears`, `maxExperienceYears` | `@Min(0)`; when both are sent, max ≥ min (`@AssertTrue`, `@JsonIgnore`) |
| `salaryDetails.ctcMin`, `ctcMax` | `@PositiveOrZero`; when both are sent, max ≥ min (`@AssertTrue`, `@JsonIgnore`) |

A request can send only one side of a range, so these checks cannot see the stored value. The service therefore also checks the job **as it is after the update**, before saving ([section 9.4](#94-careersinserviceimpl)). Two things are deliberately missing:

- `status`: a job's status has its own endpoint (`PUT /update/{id}/{status}`).
- `id`, `jobId`, `datePosted`, `createdDate`: server-managed.

Because update has its own type, it carries only rules that suit a partial update (no `@NotNull` or `@NotBlank` on a field that may be absent), and the controller can call `@Valid` on it without applying the create-only rules. v1 has no validation on update; IN adds it ([Decision P](#16-decisions)).

#### 5.2.3 Response (`dto.response`)

```
CareersResponseDto
├── Long      id
├── String    jobId          (generated, WFA-IN-0001…)
├── JobStatusEnum status     (ACTIVE or INACTIVE)
├── LocalDate datePosted     (set to today)
├── Instant   createdDate    (set to now)
├── JobDetailsResponseDto                 jobDetails
├── JobExperienceRequirementsResponseDto  experienceRequirements
└── JobSalaryDetailsResponseDto           salaryDetails
```

Each response section has the same fields as its create counterpart, with no validation annotations and no range-check methods. `skillOptions` is a copy of the entity's list.

### 5.3 Shared response DTOs (`careers_in.common.dto`)

Replicas of the two small v1 response wrappers, so that IN imports nothing from v1. They were already response-only, so the request/response split does not change them:

| Class | JSON | Used by |
|---|---|---|
| `JsonResponseDto` | `{ "response": "..." }` | applicant delete, job status update |
| `ApiMessageDto` | `{ "message": "..." }` | job delete |

### 5.4 List-endpoint query and page DTOs

`GET /getAll` and `GET /getAllApplicant` take their search, filters and paging as **query parameters**, which Spring binds onto a query DTO (no `@RequestBody`), and return one **page** of response DTOs.

| Class | Package | Role |
|---|---|---|
| `PageSearchRequestDto` | `common.dto` | Query parameters shared by both list endpoints: `search`, `page`, `size` |
| `CareersSearchRequestDto` | `careers.dto.request` | Extends it with the careers filters `department`, `jobType`, `workStyle` |
| `ApplicantSearchRequestDto` | `applicant.dto.request` | Extends it with nothing yet (no applicant filters); filters would be added here |
| `PageResponseDto<T>` | `common.dto` | The answer: `content`, `page`, `size`, `totalElements`, `totalPages`, `last` |

| Parameter | Type | Default | Rule |
|---|---|---|---|
| `search` | text | none | Blank or missing = no search |
| `page` | integer | `0` | Zero-based; `@Min(0)` |
| `size` | integer | `10` | `@Min(1) @Max(100)` |
| `department`, `jobType`, `workStyle` (careers only) | text | none | Blank or missing = no filter |

`PageResponseDto` is our own class rather than Spring's `PageImpl`, so the JSON shape does not depend on how Spring Data serializes pages. The search DTOs are query-parameter objects, not request bodies, so they have no response twin (`RequestResponseContractTest` skips them).

---

## 6. Applicant IN API

Base path: **`/api/v1/applicant/IN`** · Table: **`applicant_in`** · Auth: `POST /addApplicant` is public, the other three endpoints need a JWT · CORS: `*`

| Method | Path | Purpose | v1 equivalent |
|---|---|---|---|
| POST | `/addApplicant` | Submit an application | `POST /api/v1/applicant/addApplicant` |
| GET | `/getApplicant/{id}` | Get one application | `GET /api/v1/applicant/getApplicant/{id}` |
| GET | `/getAllApplicant` | List applications: paged, with global search | `GET /api/v1/applicant/getAllApplicant` (unpaged) |
| DELETE | `/delete/{id}` | Delete an application | `DELETE /api/v1/applicant/delete/{id}` |

**Access.** Only `POST /addApplicant` is open, because the public careers page submits applications. Reading applications (`getApplicant`, `getAllApplicant`) and deleting one (`delete`) return personal data or remove records, so they need a valid JWT; without one the request gets `403`. The rules are in `SecurityConfiguration` ([Decision M](#16-decisions)). The old v1 `/api/v1/applicant/**` endpoints stay open as they were.

### 6.1 `POST /api/v1/applicant/IN/addApplicant`

Before calling this endpoint, upload the resume via `POST /api/v1/blob/upload` ([section 3](#3-resume-upload-flow-blob-api)).

**Request** (`ApplicantCreateRequestDto`)

```json
{
  "jobId": "WFA-IN-0012",
  "jobTitle": "Java Developer",
  "personalDetails": {
    "fullName": "Jane Doe",
    "emailId": "jane.doe@example.com",
    "mobileNumber": "+44 7700 900123",
    "location": "London",
    "hometown": "Manchester",
    "linkedInUrl": "https://www.linkedin.com/in/janedoe",
    "portfolioUrl": "https://github.com/janedoe",
    "gender": "FEMALE",
    "maritalStatus": "SINGLE",
    "resumeFileName": "2026-10-16T10:15:30.123Zjane_doe_cv.pdf",
    "coverLetterFileName": "2026-10-16T10:15:31.000Zjane_doe_cover.pdf"
  },
  "educationDetails": {
    "highestQualification": "MASTERS",
    "degreeCourse": "MBA",
    "specialisation": "Finance",
    "collegeUniversityBoard": "University of Manchester",
    "yearOfPassing": 2022,
    "percentageOrCgpa": "7.8 CGPA",
    "otherQualifications": "CeMAP",
    "hasEducationGap": true,
    "educationGapDetails": "One-year gap between school and university while retaking A-levels."
  },
  "experienceDetails": {
    "totalExperienceYears": 4,
    "totalExperienceMonths": 6,
    "relevantExperienceYears": 3,
    "relevantExperienceMonths": 0,
    "currentCompany": "Acme Ltd",
    "currentDesignation": "Software Engineer",
    "previousCompanies": "Brightline Outsourcing, Process Associate, 2021 to 2023",
    "industryExperience": ["Financial Services", "IT / Software"],
    "skills": ["Java", "Spring Boot", "PostgreSQL"],
    "otherSkills": "Docker, Kubernetes",
    "hasEmploymentGap": true,
    "employmentGapDetails": "Six-month career break in 2023 for further study."
  },
  "availabilityDetails": {
    "noticePeriod": "30 days",
    "currentCtc": "4.5",
    "expectedCtc": "12",
    "hasOtherOffer": false,
    "otherOfferDetails": null,
    "willingToRelocate": true,
    "shiftComfort": "YES"
  },
  "finalDetails": {
    "howDidYouHearAboutUs": "LinkedIn",
    "declarationAccepted": true
  }
}
```

**Response `200 OK`**: the saved record as an `ApplicantResponseDto`, with `id` and `createdAt` filled in. The status code is `200`, the same as v1 `addApplicant`.

```json
{
  "id": 1,
  "jobId": "WFA-IN-0012",
  "jobTitle": "Java Developer",
  "createdAt": "2026-10-16T10:16:02.481",
  "personalDetails":     { "...": "as submitted" },
  "educationDetails":    { "...": "as submitted" },
  "experienceDetails":   { "...": "as submitted" },
  "availabilityDetails": { "...": "as submitted" },
  "finalDetails":        { "...": "as submitted" }
}
```

**Side effects** (identical to v1, but sent by IN's own code):

1. An email to the applicant: *"Welcome to Wealthmax | Your Application Is Received"*, using `templates/EmailTemplateOfCareers.html`.
2. An email to `leads@wealthmax.co.uk`: *"New Application Received"*, using `templates/EmailTemplateOfLeadFromApplicant.html`, with name, email, phone and position.

**Errors**

| Status | When |
|---|---|
| 400 | Validation failure (`violations` list; see [section 11](#11-validation--error-responses)) |
| 400 | Invalid enum or date value, for example `"gender": "X"` |
| 409 | The same email + mobile applied within the last 6 months (whatever the name, for any job): "An application with this email and mobile number was submitted recently. Please try again after 4 months." The number is the whole months left until the existing row's `cooldown_until` (UTC), rounded up and never below 1 ("1 month", "2 months", ...) |

### 6.2 `GET /api/v1/applicant/IN/getApplicant/{id}`

- **200**: one `ApplicantResponseDto` from `applicant_in`.
- **404**: `"Applicant not found with id: {id}"` (same message as v1).

### 6.3 `GET /api/v1/applicant/IN/getAllApplicant`

Query parameters (all optional): `search`, `page`, `size` ([section 5.4](#54-list-endpoint-query-and-page-dtos)). There are no filters yet.

```
GET /api/v1/applicant/IN/getAllApplicant?search=jane&page=0&size=10
```

- **200**: a `PageResponseDto<ApplicantResponseDto>` from `applicant_in`, newest first (`id` descending):

```json
{
  "content": [ { "id": 5, "personalDetails": { "fullName": "Jane Doe" } } ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

- **Global search** (`search`): an applicant is returned if the term appears, ignoring case and as part of the text, in any of: `fullName`, `emailId`, `mobileNumber`, `jobId`, `jobTitle`, `location`, `hometown`, `currentCompany`, `currentDesignation`, or in any one of their `skills`. Leading and trailing spaces in the term are ignored. The term is matched literally: `%` and `_` are ordinary characters. Other columns (degree, college, previous companies, the free-text detail fields, …) are not searched.
- A page past the end is `200` with an empty `content` and the real `totalElements`; a search with no match is `200` with an empty `content` and `totalElements` `0`.
- **400**: `page` below 0, `size` below 1 or above 100, or a value that is not a number. Same `violations` shape as [section 11](#11-validation--error-responses).
- v1 applicants (in the `applicant` table) are **not** included.
- **Breaking change:** this endpoint used to return a bare `ApplicantResponseDto[]`. Any client that read the array must now read `content`.

### 6.4 `DELETE /api/v1/applicant/IN/delete/{id}`

- **200**: `{ "response": "User with  id {id} deleted successfully." }` (same shape and text as v1).
- **404**: `"No user found with the given id: {id}"`.
- The applicant's rows in `applicant_in_skills` and `applicant_in_industry_experience` are deleted with it. The resume blob is **not** deleted, which matches v1.

---

## 7. Careers IN API

Base path: **`/api/v1/careers/IN`** · Table: **`careers_in`** · Auth: the three `GET` job endpoints are public; create, update, status update and delete need a JWT · CORS: `*`

| Method | Path | Purpose | v1 equivalent |
|---|---|---|---|
| POST | `/createJob` | Create a job posting | `POST /createJob` |
| GET | `/getAll` | List jobs: paged, with global search and department / jobType / workStyle filters | `GET /getAll` (unpaged) |
| GET | `/get/{id}` | Get one job | `GET /get/{id}` |
| PUT | `/update/{id}` | Partial update of a job | `PUT /update/{id}` |
| PUT | `/update/{id}/{status}` | Change job status | `PUT /update/{id}/{status}` |
| DELETE | `/delete/{id}` | Delete a job | `DELETE /delete/{id}` |
| GET | `/get/status/{status}` | List jobs by status | `GET /get/status/{status}` |

**Access.** The public careers page needs to list and read jobs, so `GET /getAll`, `GET /get/{id}` and `GET /get/status/{status}` are open. Everything that changes a job (`POST /createJob`, both `PUT /update/...` routes and `DELETE /delete/{id}`) needs a valid JWT; without one the request gets `403`. The rules are in `SecurityConfiguration` ([Decision M](#16-decisions)); any new path under `/api/v1/careers/IN/` also needs a JWT until a rule opens it. The old v1 `/api/v1/careers/**` endpoints stay open as they were.

### 7.1 `POST /api/v1/careers/IN/createJob`

**Request** (`CareersCreateRequestDto`)

```json
{
  "status": "ACTIVE",
  "jobDetails": {
    "jobTitle": "Java Developer",
    "department": "Technology",
    "role": "Backend Engineer",
    "jobType": "Full-time",
    "workStyle": "Hybrid",
    "jobLocation": "London",
    "jobDescription": "Build and maintain the Wealthmax admin platform...",
    "applyBy": "2026-12-31",
    "note": "Immediate joiners preferred"
  },
  "experienceRequirements": {
    "officeTiming": "09:00 - 17:30",
    "officeDay": "Monday - Friday",
    "requiredSkill": "Java",
    "skillOptions": ["Java", "Spring Boot", "PostgreSQL", "Azure", "React"],
    "minExperienceYears": 3,
    "maxExperienceYears": 6,
    "noticePeriodAccepted": "Up to 60 days"
  },
  "salaryDetails": {
    "ctcMin": 45000,
    "ctcMax": 60000,
    "relocationRequired": false
  }
}
```

**Server-set fields** (same as v1 `setDefaultAndSave`):

| Field | Value |
|---|---|
| `jobId` | Next `WFA-IN-####` number, taken from a database sequence ([section 13](#13-job-id-generation-wfa-in-)) |
| `datePosted` | `LocalDate.now()` |
| `createdDate` | `Instant.now()` |

`CareersCreateRequestDto` has no `jobId`, `datePosted` or `createdDate` field, so a value sent for any of them is ignored.

**Response `201 Created`**: the full `CareersResponseDto`, including `id` and `jobId`.

### 7.2 `GET /api/v1/careers/IN/getAll`

Query parameters (all optional): `search`, `department`, `jobType`, `workStyle`, `page`, `size` ([section 5.4](#54-list-endpoint-query-and-page-dtos)).

```
GET /api/v1/careers/IN/getAll?search=java&department=Technology&jobType=Full-time&workStyle=Hybrid&page=0&size=10
```

- **200**: a `PageResponseDto<CareersResponseDto>` from `careers_in`, sorted by `jobId` descending (same order as v1), in the same page shape as [section 6.3](#63-get-apiv1applicantingetallapplicant).
- **Global search** (`search`): a job is returned if the term appears, ignoring case and as part of the text, in any of: `jobId`, `jobTitle`, `department`, `role`, `jobType`, `workStyle`, `jobLocation`, `requiredSkill`, or in any one of its `skillOptions`. (`status` is not searched: it is an enum, use `GET /get/status/{status}`.) Leading and trailing spaces are ignored, and `%` and `_` are ordinary characters. The description, note and the other columns are not searched.
- **Filters** (`department`, `jobType`, `workStyle`): each is an **exact** match, ignoring case and surrounding spaces (`technology` finds `Technology`; `Tech` does not). A blank or missing filter is not applied. Filters take one value each.
- The search and the three filters all apply together (**AND**): `search=java&workStyle=Remote` returns the remote jobs that match "java".
- **400**: `page` below 0, `size` below 1 or above 100, or a value that is not a number.
- **Breaking change:** this endpoint used to return a bare `CareersResponseDto[]`. Any client that read the array must now read `content`.
- `GET /get/status/{status}` is unchanged and still returns the whole list for that status.

### 7.3 `GET /api/v1/careers/IN/get/{id}`

- **200**: one `CareersResponseDto`.
- **404**: `"Job doesn't exist with the given id: {id}"`.

### 7.4 `PUT /api/v1/careers/IN/update/{id}`

The body is a `CareersUpdateRequestDto` (the same three optional sections as the create request, with no `status`). Nothing is required, but whatever is sent must follow the rules in [section 5.2.2](#522-update-request-dtorequest). Partial update: only non-null fields are applied, the same rule as v1. A section that is missing or `null` is skipped entirely, and within a section each `null` field is skipped. As in v1, a field cannot be cleared to `null` through this endpoint. If `skillOptions` is sent, it replaces the whole list.

```json
{
  "jobDetails": { "applyBy": "2027-01-15" },
  "salaryDetails": { "ctcMax": 65000 }
}
```

- **200**: the updated `CareersResponseDto`.
- **400**: a value that breaks a rule of [section 5.2.2](#522-update-request-dtorequest) (a blank `jobTitle`, a negative CTC or number of years, a range sent inverted), with the dotted `fieldName` as on create. **Also `400`, with a plain message, when the range is inverted only against the stored value**: the job is checked as it will be after the update, before it is saved, so with a stored CTC range of 45000–60000, `{"salaryDetails":{"ctcMax":1000}}` returns `400` "Maximum CTC must not be less than minimum CTC" and `{"experienceRequirements":{"maxExperienceYears":2}}` against a stored 3–6 years returns "Maximum experience must not be less than minimum experience". Nothing is saved when the request is refused.
- **404**: job not found.

> **Relation to v1 (Decision E):** the null-skipping update logic is replicated in `CareersDetailsMapper.applyPartialUpdate`; it does not call v1 code. Two deliberate differences: IN also updates `role` and `jobLocation` (v1 skips them), and IN never changes `id`, `jobId`, `datePosted`, `createdDate` or `status` through this endpoint (v1 lets a request overwrite `jobId` and `datePosted`). Status has its own endpoint.

### 7.5 `PUT /api/v1/careers/IN/update/{id}/{status}`

Same logic as v1 `updateJobStatus`, applied to `careers_in`. `{status}` must be `ACTIVE` or `INACTIVE` (`JobStatusEnum`, case-sensitive). Returns **200** `{ "response": "Status Update successfully" }`, **400** for any other status, or **404**.

### 7.6 `DELETE /api/v1/careers/IN/delete/{id}`

Same logic as v1 `deleteJobById`, applied to `careers_in`. Returns **200** `{ "message": "Job deleted successfully" }`, or **404**. The job's rows in `careers_in_skill_options` are deleted with it.

### 7.7 `GET /api/v1/careers/IN/get/status/{status}`

- **200**: `CareersResponseDto[]` from `careers_in` filtered by status, sorted by `jobId` descending. `{status}` must be `ACTIVE` or `INACTIVE` (case-sensitive); any other value is **400**, not an empty list.

---

## 8. Controller layer

Two new controller classes. The v1 controllers are not edited.

### 8.1 `ApplicantINController` (`careers_in.applicant.controller`)

```java
@RestController
@RequestMapping("/api/v1/applicant/IN")
@Tag(name = "ApplicantINController", description = "Applicant IN api for job applications")
@CrossOrigin("*")
public class ApplicantINController {

    @PostMapping("/addApplicant")
    public ResponseEntity<ApplicantResponseDto> createApplicantDetails(
            @Valid @RequestBody ApplicantCreateRequestDto request) {
        return ResponseEntity.ok(applicantINService.saveApplicantDetails(request));
    }

    @GetMapping("/getApplicant/{id}")
    public ResponseEntity<ApplicantResponseDto> getApplicantDetailsById(@PathVariable Long id) { ... }

    @GetMapping("/getAllApplicant")      // query parameters: search, page, size
    public ResponseEntity<PageResponseDto<ApplicantResponseDto>> getAllApplicantDetails(
            @Valid @ParameterObject ApplicantSearchRequestDto request) { ... }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<JsonResponseDto> deleteApplicantDetails(@PathVariable("id") Long id) { ... }
}
```

### 8.2 `CareersINController` (`careers_in.careers.controller`)

```java
@RestController
@RequestMapping("/api/v1/careers/IN")
@CrossOrigin("*")
public class CareersINController {

    @PostMapping("/createJob")           // 201, @Valid CareersCreateRequestDto -> CareersResponseDto
    @GetMapping("/getAll")               // @Valid @ParameterObject CareersSearchRequestDto -> PageResponseDto<CareersResponseDto>
    @GetMapping("/get/{id}")
    @PutMapping("/update/{id}")          // @Valid CareersUpdateRequestDto: partial update, what is sent is validated
    @PutMapping("/update/{id}/{status}") // status is a JobStatusEnum path variable
    @DeleteMapping("/delete/{id}")
    @GetMapping("/get/status/{status}")
}
```

`@Valid` is applied on both body endpoints, create and update. Update takes its own `CareersUpdateRequestDto`, whose constraints skip anything that is not sent, so a partial update is still allowed ([section 5.2.2](#522-update-request-dtorequest)). The two list endpoints also use `@Valid`, on their query DTO (the page and size limits). `@ParameterObject` (springdoc) makes Swagger UI show each query-DTO field as its own query parameter.

The paths do not collide with the v1 controllers: every IN path has the literal `IN` segment right after `/api/v1/applicant` or `/api/v1/careers`.

**Swagger UI.** The project restricts springdoc to listed packages (`springdoc.packagesToScan` in `application.properties`, previously only `com.wealthmax.adminPortal.controller`), so controllers in any other package are silently left out of Swagger UI. The IN package was added to that list:

```properties
springdoc.packagesToScan=com.wealthmax.adminPortal.controller,com.wealthmax.adminPortal.careers_in
```

Any future controller outside these two packages needs the same addition. If the deployed environment overrides the property (for example with a `SPRINGDOC_PACKAGES_TO_SCAN` environment variable), the override must include the IN package too. `INFullApplicationTest` checks that `/v3/api-docs` lists every v1 and IN path above.

---

## 9. Service layer

Two new service interfaces with their implementations, following the existing `service` / `service/impl` convention. They work only on the new repositories.

### 9.1 `ApplicantINService` (`careers_in.applicant.service`)

```java
ApplicantResponseDto       saveApplicantDetails(ApplicantCreateRequestDto request);
ApplicantResponseDto       getApplicantDetailsById(Long id);
PageResponseDto<ApplicantResponseDto> getAllApplicantDetails(ApplicantSearchRequestDto request);
JsonResponseDto            deleteApplicantDetails(Long id);
```

### 9.2 `ApplicantINServiceImpl`

Dependencies: `ApplicantINRepository`, `ApplicantDetailsMapper`, `MailgunServices` (shared email transport), `PlatformTransactionManager` (wrapped in a `TransactionTemplate`).

**`saveApplicantDetails(request)`**

```
1. entity = applicantDetailsMapper.toEntity(request)              ← name, email and mobile are trimmed
2. transactionTemplate.execute(...)                            ← steps 3-6 in one transaction (section 12)
3.   applicantINRepository.lockApplicant(key)                 ← key = hash of lower-case email + "|" + mobile
4.   now = Instant.now()
     applicantINRepository.findFirstByEmailIdIgnoreCaseAndMobileNumberAndCooldownUntilAfterOrderByCooldownUntilDesc(email, mobile, now)
     → present: throw DuplicateResourceException("An application with this email and mobile number was
             submitted recently. Please try again after " + months left until its cooldownUntil (UTC, rounded up, at least 1)
             + " month(s).")
             → 409, transaction rolled back
5.   entity.cooldownUntil = now in UTC + 6 months             ← COOLDOWN = Period.ofMonths(6)
6.   saved = applicantINRepository.save(entity)               ← createdAt set by @CreationTimestamp
   commit                                                     ← the lock is released here
7. log.info("Created Applicant IN with id {}", saved.getId())
8. sendEmailsToApplicant(saved)                               ← IN's own copy of the v1 email logic, after the commit
9. return applicantDetailsMapper.toResponse(saved)
```

`sendEmailsToApplicant` is a private method of `ApplicantINServiceImpl`, a replica of the v1 one: it loads the two HTML templates from the classpath (`getEmailTemplate`), replaces the `{{name}}`, `{{email}}`, `{{phoneNumber}}` and `{{position}}` placeholders (`fillTemplate`) and sends through `MailgunServices`. Like v1, the application is already saved when the emails go out, so an email failure returns `500` with the row saved.

**`getApplicantDetailsById(id)`**: `applicantINRepository.findById` or throw `ResourceNotFoundException("Applicant not found with id: " + id)`, then `toResponse`.

**`getAllApplicantDetails(request)`**

```
1. search   = SearchSpecifications.containsAny(request.search, SEARCH_COLUMNS, ["skills"])    ← match-all when blank
2. pageable = PageRequest.of(request.page, request.size, Sort.by(DESC, "id"))
3. page     = applicantINRepository.findAll(search, pageable)       ← one query for the rows, one for the total
4. return PageResponseDto.from(page, applicantDetailsMapper::toResponse)
```

`SEARCH_COLUMNS` is `fullName, emailId, mobileNumber, jobId, jobTitle, location, hometown, currentCompany, currentDesignation`. A column list is a constant in the service, so a new searchable column is a one-word change.

**`deleteApplicantDetails(id)`**: if `!existsById(id)`, throw `ResourceNotFoundException("No user found with the given id: " + id)`. Otherwise `deleteById(id)`, log, and return `JsonResponseDto("User with  id " + id + " deleted successfully.")`.

### 9.3 `CareersINService` (`careers_in.careers.service`)

```java
CareersResponseDto       saveJobDetails(CareersCreateRequestDto request);
PageResponseDto<CareersResponseDto> getAllJobDetails(CareersSearchRequestDto request);
CareersResponseDto       getJobDetailsById(Long id);
CareersResponseDto       updateJobDetails(Long id, CareersUpdateRequestDto request);
JsonResponseDto          updateJobDetailsStatus(Long id, JobStatusEnum status);
ApiMessageDto            deleteJobDetailsById(Long id);
List<CareersResponseDto> getJobDetailsByStatus(JobStatusEnum status);
```

### 9.4 `CareersINServiceImpl`

Dependencies: `CareersINRepository`, `CareersDetailsMapper`.

**`saveJobDetails(request)`**

```
1. entity = careersDetailsMapper.toEntity(request)
2. entity.setDatePosted(LocalDate.now())                      ┐
3. entity.setJobId(generateJobDetailsId())                    ├ same defaults as v1 setDefaultAndSave
4. entity.setCreatedDate(Instant.now())                       ┘
5. saved = careersINRepository.save(entity)
6. log.info("Created Job IN with id {}", saved.getId())
7. return careersDetailsMapper.toResponse(saved)
```

**`getAllJobDetails(request)`**

```
1. search     = SearchSpecifications.containsAny(request.search, SEARCH_COLUMNS, ["skillOptions"])
2. department = SearchSpecifications.equalsIgnoreCase("department", request.department)
3. jobType    = SearchSpecifications.equalsIgnoreCase("jobType",    request.jobType)
4. workStyle  = SearchSpecifications.equalsIgnoreCase("workStyle",  request.workStyle)
5. pageable   = PageRequest.of(request.page, request.size, Sort.by(DESC, "jobId"))
6. page       = careersINRepository.findAll(search.and(department).and(jobType).and(workStyle), pageable)
7. return PageResponseDto.from(page, careersDetailsMapper::toResponse)
```

`SEARCH_COLUMNS` is `jobId, jobTitle, department, role, jobType, workStyle, jobLocation, requiredSkill`. `status` is left out on purpose: the search lower-cases each column, which does not work on an enum column. A blank search or filter becomes a "match everything" specification, so the four are always combined the same way.

**`getJobDetailsByStatus(status)`**: `findAllByStatus(status, Sort.by(DESC, "jobId"))`, then map each with `toResponse` (not paged).

**Search helper (`careers_in.common.search.SearchSpecifications`).** Two static methods build the JPA `Specification`s above:

- `equalsIgnoreCase(attribute, value)`: `lower(column) = lower(value)`.
- `containsAny(term, attributes, collectionAttributes)`: `lower(column) like '%term%'` for each text attribute, combined with OR. For an element collection (`skillOptions`, `skills`) it uses an `EXISTS` sub-query instead of a join, so a row with several matching elements is counted once and `totalElements` and the page sizes stay correct. The term is escaped (`\`, `%`, `_`) before it is put in the `LIKE`.

**`getJobDetailsById(id)`**: `findJobDetailsById(id)`, then `toResponse`.

**`updateJobDetails(id, request)`**

```
1. careers = findJobDetailsById(id)                           ← same 404 message as v1
2. careersDetailsMapper.applyPartialUpdate(request, careers)      ← copies only non-null values
3. requireValidRanges(careers)                                    ← the job as it is now: max experience ≥ min, max CTC ≥ min,
                                                                     else InvalidDataException (400); nothing is saved
4. updated = careersINRepository.save(careers)
5. log.info("Updated Job IN with id {}", id)
6. return careersDetailsMapper.toResponse(updated)
```

Step 3 is needed because a request may send only one side of a range, which the request validation cannot compare with the stored value. `updateJobDetails` is deliberately not `@Transactional`: the entity is changed in memory and only written by the `save` in step 4, so a refused request leaves the row as it was.

**`updateJobDetailsStatus(id, status)`**: `findJobDetailsById`, `setStatus`, save, log, return `JsonResponseDto("Status Update successfully")`.

**`deleteJobDetailsById(id)`**: `findJobDetailsById`, `deleteById`, log, return `ApiMessageDto("Job deleted successfully")`.

**Private helpers:**

- `findJobDetailsById(Long id)`: `careersINRepository.findById` or throw `ResourceNotFoundException("Job doesn't exist with the given id: " + id)`.
- `requireValidRanges(CareersINEntity job)`: throws `InvalidDataException` ("Maximum experience must not be less than minimum experience" or "Maximum CTC must not be less than minimum CTC") when both ends of a range are set and the maximum is lower.
- `generateJobDetailsId()`: see [section 13](#13-job-id-generation-wfa-in-).

---

## 10. Mapper layer

The mappers are hand-written Spring `@Component` classes in `careers_in.applicant.mapper` and `careers_in.careers.mapper`. ModelMapper is not used for IN because it cannot reliably map nested sections onto flat entity columns in `STRICT` mode, and changing its global configuration would put v1 at risk.

Each mapper converts in two directions only: **request DTO → entity** (on the way in) and **entity → response DTO** (on the way out). Controllers and services never return an entity or a request DTO to the client.

### 10.1 `ApplicantDetailsMapper`

| Method | Purpose |
|---|---|
| `ApplicantINEntity toEntity(ApplicantCreateRequestDto request)` | Flattens all five sections into entity columns and trims name, email and mobile. The request has no `id` or `createdAt`, so they stay `null` until the database fills them. Null sections are tolerated. |
| `ApplicantResponseDto toResponse(ApplicantINEntity e)` | Builds `id`, `createdAt` and all five response sections from the flat entity. Lists are copied. |

There is no conversion to the v1 `ApplicantDto`: the IN service builds its emails straight from the entity.

### 10.2 `CareersDetailsMapper`

| Method | Purpose |
|---|---|
| `CareersINEntity toEntity(CareersCreateRequestDto request)` | Flattens `jobDetails`, `experienceRequirements` and `salaryDetails` into entity columns. The request has no server-managed fields. |
| `CareersResponseDto toResponse(CareersINEntity e)` | Builds `id`, `jobId`, `status`, `datePosted`, `createdDate` and the three response sections from the flat entity. |
| `void applyPartialUpdate(CareersUpdateRequestDto request, CareersINEntity target)` | Copies only non-null values (null-safe per section). The update request has no server-managed fields or `status`, so `id`, `jobId`, `datePosted`, `createdDate` and `status` can never change here. |

---

## 11. Validation & error responses

The existing `GlobalExceptionHandler` is reused as-is.

### 11.1 Conditionally required detail fields

Three Yes/No questions each unlock a free-text follow-up. The follow-up is only required when its flag is `true`; when the flag is `false` or `null`, the field may be absent or `null`.

| Section (DTO) | Flag | Detail field |
|---|---|---|
| `educationDetails` (`ApplicantEducationDetailsRequestDto`) | `hasEducationGap` | `educationGapDetails` |
| `experienceDetails` (`ApplicantExperienceDetailsRequestDto`) | `hasEmploymentGap` | `employmentGapDetails` |
| `availabilityDetails` (`ApplicantAvailabilityDetailsRequestDto`) | `hasOtherOffer` | `otherOfferDetails` |

Each pair is enforced inside its own request section DTO with an `@AssertTrue` method, so the violation stays in the right section. `@JsonIgnore` keeps the synthetic property out of the JSON altogether (a request DTO that is serialised, for example for Swagger's request example, would otherwise show it). Response DTOs have no such checks:

```java
@JsonIgnore
@AssertTrue(message = "Education gap details are required when there is an education gap")
public boolean isEducationGapDetailsPresent() {
    return !Boolean.TRUE.equals(hasEducationGap)
            || (educationGapDetails != null && !educationGapDetails.isBlank());
}
```

The method name drives the reported `fieldName`: `isEducationGapDetailsPresent` surfaces as `educationDetails.educationGapDetailsPresent`.

**Validation failure: `400`** (`ValidationErrorResponse`). Nested field names use dot notation:

```json
{
  "violations": [
    { "fieldName": "personalDetails.emailId", "message": "Email must be a valid email address" },
    { "fieldName": "personalDetails.resumeFileName", "message": "Resume is required" },
    { "fieldName": "experienceDetails.employmentGapDetailsPresent", "message": "Employment gap details are required when there is an employment gap" },
    { "fieldName": "educationDetails.educationGapDetailsPresent", "message": "Education gap details are required when there is an education gap" },
    { "fieldName": "availabilityDetails.otherOfferDetailsPresent", "message": "Other offer details are required when another offer is in hand" }
  ]
}
```

**Other errors** (`ErrorResponse`):

```json
{
  "timestamp": "2026-10-16T10:16:02.481",
  "status": 409,
  "error": "Conflict",
  "message": "An application with this email and mobile number was submitted recently. Please try again after 4 months.",
  "path": "/api/v1/applicant/IN/addApplicant"
}
```

| Status | Source |
|---|---|
| 400 | `MethodArgumentNotValidException` (bean validation) |
| 400 | `HttpMessageNotReadableException` (bad JSON, unknown enum, bad date). For example "Gender has an invalid value" |
| 400 | `InvalidDataException` (a job update that would leave a min/max range inverted, see [section 7.4](#74-put-apiv1careersinupdateid)) |
| 400 | `MethodArgumentTypeMismatchException` (a job status in the URL that is not `ACTIVE` or `INACTIVE`) |
| 403 | A call to a protected IN endpoint without a valid JWT. Spring Security answers before the controller runs ([section 6](#6-applicant-in-api), [section 7](#7-careers-in-api)) |
| 404 | `ResourceNotFoundException` |
| 409 | `DuplicateResourceException` (applicant cooling period) or `DataIntegrityViolationException` with "duplicate key" (a DB unique constraint, for example `careers_in.job_id` in a race) |
| 500 | Anything unhandled: generic message, full trace in server logs |

---

## 12. Duplicate-application protection (cooling period)

**Decided:** after a person applies, the same **email + mobile number** cannot apply again, for **any job**, until a **6-month cooling period** is over. After that they can apply again, and the new application starts a new cooling period. `id` stays the primary key and there is **no unique key** on `applicant_in`.

- **Why not a unique key:** a unique constraint blocks a repeat forever and cannot depend on time, while the rule is time-limited. The earlier `uk_applicant_v2_identity` on `(full_name, email_id, mobile_number)` was therefore removed (manual SQL in [section 14](#14-database-impact--deployment)).
- **Who counts as the same applicant:** the same email (letter case ignored) and the same mobile number (exact), whatever the name. The same email with a different mobile number, or the same mobile number with a different email, is a different applicant and is accepted.
- **Where the end of the cooling period lives:** a column `cooldown_until` (`timestamptz`), set by the service to the submit time in UTC + 6 months (`OffsetDateTime` at UTC `plusMonths(6)`, stored as an `Instant`). It is stored, not calculated from `created_at`, so changing the 6 months later only affects new applications unless the existing rows are updated, and an admin could end one person's cooling period early by updating that row.
- **Rows without a value:** rows saved before this change have `cooldown_until = NULL` and never block anyone (they were left as they are).
- **No effect on v1.** The check reads only `applicant_in`, so someone who applied through v1 can still apply through IN, and v1 behaves exactly as today.
- **Deleting an application** (`DELETE /delete/{id}`) removes its row and therefore its cooling period.

**How it is enforced** (`ApplicantINServiceImpl.saveApplicantDetails`, [section 9.2](#92-applicantinserviceimpl)): in one transaction the service

1. takes a PostgreSQL **advisory lock** (`pg_advisory_xact_lock`) on a key computed from the lower-case email and the mobile number, so two requests for the same applicant run one at a time; the lock is released automatically at commit or rollback. Two different applicants whose keys happen to collide only wait for each other briefly;
2. runs one query: the row with this email + mobile whose `cooldown_until` is after now (the latest one if there are several). If there is one, `409`, and the message shows the whole months left until its `cooldown_until` (both in UTC), rounded up so that a few days left is "1 month", never "0 months";
3. sets `cooldown_until` and inserts the row.

The emails are sent after the commit, so a Mailgun failure does not undo the application (as in v1).

**Careers:** no composite key on `careers_in`, because it has no name, email or mobile columns ([Decision D](#16-decisions)).

---

## 13. Job ID generation (`WFA-IN-####`)

IN jobs use their own id series, in the format **`WFA-IN-0001`**: the prefix `WFA-IN-` followed by a zero-padded 4-digit number. Applicants refer to a job by this `jobId`.

**IN generator (`generateJobDetailsId`)**: the number comes from a PostgreSQL sequence, `careers_in_job_number_seq`. (v1 `generateCaseId()` works out "latest row + 1" in code; IN does not copy that, see [Decision N](#16-decisions).)

```
n     = careersINRepository.nextJobNumber()                     (select nextval('careers_in_job_number_seq'))
jobId = String.format("WFA-IN-%04d", n)                         → WFA-IN-0001, WFA-IN-0002, …
```

Why a sequence: "latest row + 1" had two faults.

- **No duplicates under load.** Two jobs created at the same moment both read the same latest row and computed the same number, so the second insert failed the unique constraint. `nextval` hands every caller a different number, so there is nothing to retry.
- **No reuse.** Deleting the newest job made the next new job take its `jobId`, so an applicant's stored `jobId` (a plain string) would then point at an unrelated job. A sequence never goes back.
- The cost is that numbers can have **gaps**: a number is not given back if the insert fails afterwards, or when a test run is rolled back. The ids are unique and increasing, not gap-free.

**Where the sequence is created.** `ddl-auto=update` creates tables but not stand-alone sequences, so `src/main/resources/schema.sql` creates it. Spring runs that file on every startup (`spring.sql.init.mode=always`), after Hibernate has created the tables (`spring.jpa.defer-datasource-initialization=true`), so every statement in it is safe to run again:

```sql
CREATE SEQUENCE IF NOT EXISTS careers_in_job_number_seq;

SELECT setval('careers_in_job_number_seq', GREATEST(existing.max_number, 1), existing.max_number > 0)
FROM (SELECT COALESCE(MAX(CAST(SUBSTRING(job_id FROM 8) AS BIGINT)), 0) AS max_number
      FROM careers_in
      WHERE job_id ~ '^WFA-IN-[0-9]+$') existing
WHERE NOT (SELECT is_called FROM careers_in_job_number_seq);
```

- On a **new database** the sequence starts at 1, so the first job is `WFA-IN-0001`.
- On a database that **already has IN jobs** (every environment that ran the first version of this module), the second statement moves the sequence to the highest existing number the first time, so the next job continues after it and cannot hit the unique `job_id`. No manual SQL is needed.
- That statement only runs while the sequence has never been used (`is_called` is false), so a restart never moves the sequence backwards, and a deleted job's number is not handed out again.
- v1 ids (`WFA0001`, …) and IN ids (`WFA-IN-0001`, …) are different series, so they can never clash. The v1 table and repository are not read.
- `careers_in.job_id` keeps its unique constraint as a safeguard (for example against a row inserted by hand).
- Because the number is zero-padded to 4 digits, sorting `jobId` descending is correct up to `WFA-IN-9999`. Beyond that the ids still generate (`WFA-IN-10000`) but string sorting would misplace them. This is the same limit v1 has.

---

## 14. Database impact & deployment

`spring.jpa.hibernate.ddl-auto=update` creates the new tables automatically on startup. The DDL below is the PostgreSQL DDL generated by Hibernate from the IN entities, so every entity field has a column and every column has an entity field (see the [test checklist](#17-test-checklist)).

```sql
CREATE TABLE applicant_in (
    id                              bigserial NOT NULL,
    job_id                          varchar(255),
    job_title                       varchar(255),
    created_at                      timestamp(6),
    cooldown_until                  timestamp(6) with time zone,
    -- Personal details (Step 01)
    full_name                       varchar(255) NOT NULL,
    email_id                        varchar(255) NOT NULL,
    mobile_number                   varchar(255) NOT NULL,
    location                        varchar(255) NOT NULL,
    hometown                        varchar(255) NOT NULL,
    linked_in_url                   varchar(255),
    portfolio_url                   varchar(255),
    gender                          varchar(255) CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
    marital_status                  varchar(255) CHECK (marital_status IN ('SINGLE','MARRIED','DIVORCED','WIDOWED','PREFER_NOT_TO_SAY')),
    resume_file_name                varchar(255),
    cover_letter_file_name          varchar(255),
    -- Education details (Step 02)
    highest_qualification           varchar(255) NOT NULL CHECK (highest_qualification IN ('HIGH_SCHOOL','DIPLOMA','BACHELORS','MASTERS','PHD','OTHER')),
    degree_course                   varchar(255) NOT NULL,
    specialisation                  varchar(255),
    college_university_board        varchar(255) NOT NULL,
    year_of_passing                 integer NOT NULL,
    percentage_or_cgpa              varchar(255),
    other_qualifications            TEXT,
    has_education_gap               boolean NOT NULL,
    education_gap_details           TEXT,
    -- Experience details (Step 03)
    current_company                 varchar(255) NOT NULL,
    current_designation             varchar(255) NOT NULL,
    total_experience_years          integer,
    total_experience_months         integer,
    relevant_experience_years       integer,
    relevant_experience_months      integer,
    previous_companies              TEXT NOT NULL,
    other_skills                    varchar(255),
    has_employment_gap              boolean,
    employment_gap_details          TEXT,
    -- Availability & pay (Step 04)
    notice_period                   varchar(255),
    current_ctc                     numeric(15,2),
    expected_ctc                    numeric(15,2),
    has_other_offer                 boolean,
    other_offer_details             TEXT,
    willing_to_relocate             boolean,
    shift_comfort                   varchar(255) CHECK (shift_comfort IN ('YES','NO','WANT_TO_DISCUSS')),
    -- Final details (Step 05)
    how_did_you_hear_about_us       varchar(255),
    declaration_accepted            boolean,
    PRIMARY KEY (id)
);

CREATE TABLE applicant_in_industry_experience (
    applicant_id bigint NOT NULL REFERENCES applicant_in(id),
    industry     varchar(255)
);

CREATE TABLE applicant_in_skills (
    applicant_id bigint NOT NULL REFERENCES applicant_in(id),
    skill        varchar(255)
);

CREATE TABLE careers_in (
    id                     bigserial NOT NULL,
    job_id                 varchar(255) UNIQUE,
    status                 varchar(255) CHECK (status IN ('ACTIVE','INACTIVE')),
    date_posted            date,
    created_date           timestamp(6) with time zone,
    -- Job details
    job_title              varchar(255),
    department             varchar(255),
    role                   varchar(255),
    job_type               varchar(255),
    work_style             varchar(255),
    job_location           varchar(255),
    job_description        TEXT,
    apply_by               date,
    note                   varchar(255),
    -- Experience requirements
    office_timing          varchar(255),
    office_day             varchar(255),
    required_skill         varchar(255),
    min_experience_years   integer,
    max_experience_years   integer,
    notice_period_accepted varchar(255),
    -- Salary details
    ctc_min                numeric(15,2),
    ctc_max                numeric(15,2),
    relocation_required    boolean,
    PRIMARY KEY (id)
);

CREATE TABLE careers_in_skill_options (
    careers_id bigint NOT NULL REFERENCES careers_in(id),
    skill      varchar(255)
);

-- Created by src/main/resources/schema.sql, not by Hibernate (see section 13)
CREATE SEQUENCE IF NOT EXISTS careers_in_job_number_seq;
```

The columns are listed by form section for readability; Hibernate emits them alphabetically and names the foreign keys itself (`FK…` hashes), which does not change the schema.

- The existing `applicant` and `careers` tables receive **no DDL at all**.
- No data migration is needed, and the new tables start empty.
- **What `ddl-auto=update` does and does not do here.** On the first startup it creates every table above that does not exist yet, including the foreign keys, so the IN module works with the current setting. It only ever *adds*: it never drops or alters an existing column or constraint. So a later change to an IN entity (a new NOT NULL column on a table that already has rows, a changed column type, or a new enum value, which has to be added to the `CHECK` constraint) needs a manual SQL change. The repository tests run with the same `ddl-auto=update` on the local PostgreSQL database, where the tables already exist, so they check that the tables, the `cooldown_until` column and the absence of the old unique key are there but no longer prove that the tables can be created from nothing.
- **Rename from `v2` to `IN` (manual SQL on every database that already has the old tables; run it first, before starting the new build and before the database tests).** `ddl-auto=update` never renames a table: on its own it creates new, empty `careers_in` and `applicant_in` tables and leaves the old rows in `careers_v2` and `applicant_v2`. The primary keys are `IDENTITY` columns, so they keep working after a rename. Run once:

  ```sql
  ALTER TABLE careers_v2 RENAME TO careers_in;
  ALTER TABLE careers_v2_skill_options RENAME TO careers_in_skill_options;
  ALTER TABLE applicant_v2 RENAME TO applicant_in;
  ALTER TABLE applicant_v2_industry_experience RENAME TO applicant_in_industry_experience;
  ALTER TABLE applicant_v2_skills RENAME TO applicant_in_skills;
  ALTER SEQUENCE careers_v2_job_number_seq RENAME TO careers_in_job_number_seq;
  ```

  If the new build already started, the empty `careers_in` and `applicant_in` tables exist and these statements fail with "already exists"; drop the empty new tables first (only if they hold no rows). A database that never had the old tables needs none of this. The next bullet's `DROP CONSTRAINT` uses the new table name, so run it after these statements.
- **Cooling-period change (manual SQL on every database that already has `applicant_in`).** `ddl-auto=update` adds the nullable `cooldown_until` column by itself but never drops the old unique key, so run once:

  ```sql
  ALTER TABLE applicant_in DROP CONSTRAINT uk_applicant_v2_identity;
  ```

  Existing rows keep `cooldown_until = NULL` and do not block anyone. To give them a cooling period as well: `UPDATE applicant_in SET cooldown_until = created_at + interval '6 months' WHERE cooldown_until IS NULL;` (`created_at` has no time zone, so it is read in the database session's zone). `ApplicantINRepositoryTest` fails with this `ALTER` in its message while the old key still exists.
- **Job-number sequence and `schema.sql` (no manual SQL).** `src/main/resources/schema.sql` creates `careers_in_job_number_seq` and, on a database that already has IN jobs, moves it past the highest existing `WFA-IN-####` the first time ([section 13](#13-job-id-generation-wfa-in-)). It needs two properties in `application.properties`, which are global for the application: `spring.sql.init.mode=always` (Spring Boot otherwise only runs SQL scripts on an embedded database) and `spring.jpa.defer-datasource-initialization=true` (so the script runs after Hibernate has created `careers_in`, which the script reads). The file runs on every startup, so each statement in it must stay safe to run again. The database user needs the right to create a sequence.
- **Job status migration (manual SQL on every database that already has `careers_in` rows).** `status` is now an enum column, so Hibernate cannot read a row whose status is anything other than `ACTIVE` or `INACTIVE` (for example `Active` or `CLOSED` from before). Look first, then fix what you find; a `NULL` status is allowed and is left alone:

  ```sql
  SELECT DISTINCT status FROM careers_in;
  UPDATE careers_in SET status = upper(status) WHERE status IS NOT NULL;
  UPDATE careers_in SET status = 'INACTIVE' WHERE status IS NOT NULL AND status NOT IN ('ACTIVE','INACTIVE');
  ```

  A database created from scratch gets the `CHECK (status IN ('ACTIVE','INACTIVE'))` shown above; `ddl-auto=update` does not add it to a `status` column that already exists.
- **Batch fetching needs no database change.** `@BatchSize(size = 50)` on `skills`, `industryExperience` and `skillOptions` only changes how Hibernate loads the lists ([section 18](#18-known-limitations--follow-ups)).
- **Rollback:** drop the five new tables (`applicant_in`, `applicant_in_skills`, `applicant_in_industry_experience`, `careers_in`, `careers_in_skill_options`) and the sequence (`DROP SEQUENCE careers_in_job_number_seq`), and remove the `schema.sql` file and its two properties. v1 is unaffected.

---

## 15. File list

All paths are under `src/main/java/com/wealthmax/adminPortal/careers_in/` (tests under `src/test/java/...`).

### New files — `common`

| File | Purpose |
|---|---|
| `common/dto/JsonResponseDto.java` | `{ "response": "..." }` wrapper |
| `common/dto/ApiMessageDto.java` | `{ "message": "..." }` wrapper |
| `common/dto/PageSearchRequestDto.java` | Query parameters of the list endpoints: `search`, `page`, `size` (with their limits) |
| `common/dto/PageResponseDto.java` | One page of results: `content`, `page`, `size`, `totalElements`, `totalPages`, `last` |
| `common/search/SearchSpecifications.java` | Builds the case-insensitive contains-search and exact-match filters as JPA `Specification`s |

### New files — `applicant`

| File | Purpose |
|---|---|
| `applicant/entity/ApplicantINEntity.java` | Entity for table `applicant_in` |
| `applicant/entity/GenderEnum.java` | Enum |
| `applicant/entity/MaritalStatusEnum.java` | Enum |
| `applicant/entity/HighestQualificationEnum.java` | Enum |
| `applicant/entity/ShiftComfortEnum.java` | Enum — YES / NO / WANT_TO_DISCUSS |
| `applicant/repository/ApplicantINRepository.java` | Repository for `applicant_in` |
| `applicant/dto/request/ApplicantCreateRequestDto.java` | Request root: `jobId`, `jobTitle` and the five sections |
| `applicant/dto/request/ApplicantPersonalDetailsRequestDto.java` | Step 01 — personal details and documents |
| `applicant/dto/request/ApplicantEducationDetailsRequestDto.java` | Step 02 — education |
| `applicant/dto/request/ApplicantExperienceDetailsRequestDto.java` | Step 03 — work experience |
| `applicant/dto/request/ApplicantAvailabilityDetailsRequestDto.java` | Step 04 — availability & pay |
| `applicant/dto/request/ApplicantFinalDetailsRequestDto.java` | Step 05 — how did you hear + declaration |
| `applicant/dto/request/ApplicantSearchRequestDto.java` | Query parameters of `getAllApplicant` (search + paging; no filters yet) |
| `applicant/dto/response/ApplicantResponseDto.java` | Response root: adds `id` and `createdAt` |
| `applicant/dto/response/ApplicantPersonalDetailsResponseDto.java` | Step 01 response |
| `applicant/dto/response/ApplicantEducationDetailsResponseDto.java` | Step 02 response |
| `applicant/dto/response/ApplicantExperienceDetailsResponseDto.java` | Step 03 response |
| `applicant/dto/response/ApplicantAvailabilityDetailsResponseDto.java` | Step 04 response |
| `applicant/dto/response/ApplicantFinalDetailsResponseDto.java` | Step 05 response |
| `applicant/mapper/ApplicantDetailsMapper.java` | Request → entity, entity → response |
| `applicant/service/ApplicantINService.java` | Service interface |
| `applicant/service/impl/ApplicantINServiceImpl.java` | Service implementation (includes the replicated email logic) |
| `applicant/controller/ApplicantINController.java` | `/api/v1/applicant/IN` endpoints |

### New files — `careers`

| File | Purpose |
|---|---|
| `careers/entity/CareersINEntity.java` | Entity for table `careers_in` |
| `careers/entity/JobStatusEnum.java` | Enum: `ACTIVE`, `INACTIVE` |
| `careers/repository/CareersINRepository.java` | Repository for `careers_in`, including `nextJobNumber()` (the job-number sequence) |
| `careers/dto/request/CareersCreateRequestDto.java` | Create request root: `status` and the three sections |
| `careers/dto/request/JobDetailsRequestDto.java` | Create section 1 |
| `careers/dto/request/JobExperienceRequirementsRequestDto.java` | Create section 2 |
| `careers/dto/request/JobSalaryDetailsRequestDto.java` | Create section 3 |
| `careers/dto/request/CareersUpdateRequestDto.java` | Update request root: the three optional sections, no `status` |
| `careers/dto/request/JobDetailsUpdateRequestDto.java` | Update section 1 (title must not be blank, if sent) |
| `careers/dto/request/JobExperienceRequirementsUpdateRequestDto.java` | Update section 2 (years `@Min(0)`, max ≥ min if both sent) |
| `careers/dto/request/JobSalaryDetailsUpdateRequestDto.java` | Update section 3 (CTC `@PositiveOrZero`, max ≥ min if both sent) |
| `careers/dto/request/CareersSearchRequestDto.java` | Query parameters of `getAll`: search, paging and the `department` / `jobType` / `workStyle` filters |
| `careers/dto/response/CareersResponseDto.java` | Response root: adds `id`, `jobId`, `datePosted`, `createdDate` |
| `careers/dto/response/JobDetailsResponseDto.java` | Section 1 response |
| `careers/dto/response/JobExperienceRequirementsResponseDto.java` | Section 2 response |
| `careers/dto/response/JobSalaryDetailsResponseDto.java` | Section 3 response |
| `careers/mapper/CareersDetailsMapper.java` | Create request → entity, entity → response, partial update |
| `careers/service/CareersINService.java` | Service interface |
| `careers/service/impl/CareersINServiceImpl.java` | Service implementation |
| `careers/controller/CareersINController.java` | `/api/v1/careers/IN` endpoints |

### New test files

Under `src/test/java/com/wealthmax/adminPortal/careers_in/`:

| File | Covers |
|---|---|
| `INModuleBoundaryTest.java` | No IN source imports a v1 class (only the shared infrastructure listed in [section 1](#1-summary)) |
| `INSecurityRulesTest.java` | The real `SecurityConfiguration` and JWT filter in front of the IN controllers (services mocked, **no database needed**): the website calls are open without a token, every admin call gets `403` without one and works with a logged-in admin, an unknown IN path is not opened by the old wildcards, and the v1 paths stay open |
| `INFullApplicationTest.java` | The whole application on the local PostgreSQL database (mail mocked; transactional, so everything is rolled back; writes only to the IN tables): routing, 409, the `WFA-IN-####` id series (checked relative to the first number the sequence returns, because a sequence is not rolled back; admin calls are sent as a logged-in admin), search, filters and paging, that Swagger (`/v3/api-docs`) lists every v1 and IN endpoint, and that server-managed fields sent in a request body are ignored |
| `RequestResponseContractTest.java` | Request DTOs have no server-managed fields; response DTOs have no validation; controllers accept only request DTOs and return only response DTOs |
| `applicant/mapper/ApplicantDetailsMapperTest.java` | Every request field reaches the entity and comes back unchanged in the response DTO (the CTC text compared with the stored number by value), and no entity column is left unfed; trimming; the CTC text becomes a `BigDecimal`; response lists are copies |
| `applicant/dto/ApplicantCreateRequestValidationTest.java` | Required fields, the three conditional follow-ups, declaration, dotted field names; the CTC pattern (accepted and rejected values, still optional, messages) |
| `applicant/service/impl/ApplicantINServiceImplTest.java` | Cooling period: 409 during it (any name, any job) without saving or emailing, `cooldownUntil` = now in UTC + 6 months, the months left in the message (rounded up, "1 month" singular, never 0), lock taken before the check and the save, lock key ignores email case; both emails, HTML escaping, not-found paths |
| `applicant/controller/ApplicantINControllerTest.java` | Request parsing (CTC as a JSON number or as text), `400` violations, bad enum messages, `409`, `404`, response shapes |
| `applicant/repository/ApplicantINRepositoryTest.java` | The tables and the `timestamptz` `cooldown_until` column exist and the old unique key does not; persistence, both element collections, `cooldownUntil` round trip, the same applicant can be stored twice, the cooling-period query (not over / over / NULL, email case, exact mobile), the advisory lock; delete (local PostgreSQL, rolled back) |
| `applicant/repository/ApplicantINSearchTest.java` | `getAllApplicant` through the real service and queries on the local PostgreSQL database (rolled back): search columns and skills, case and partial match, `%`/`_` literal, no duplicates from skills, paging and totals |
| `careers/repository/CareersINSearchTest.java` | `getAll` through the real service and queries on the local PostgreSQL database (rolled back): search columns and skill options, the three filters (exact, case-insensitive), AND combination, `%`/`_` literal, paging and totals |
| `careers/mapper/CareersDetailsMapperTest.java` | Round trip (create request → entity → response), partial update rules with the update request |
| `careers/dto/CareersCreateRequestValidationTest.java` | `@NotBlank` title, min/max checks on the create request; on the update request, an empty or partial update is valid, and a blank title, negative values and an inverted range sent together are rejected |
| `careers/service/impl/CareersINServiceImplTest.java` | `WFA-IN-####` built from the sequence number (padding, one new number per create, past four digits), defaults, update range checks against the stored values (only one side sent), not-found paths |
| `careers/controller/CareersINControllerTest.java` | `201` on create, validation on create and on update (a partial update is fine, a blank title or inverted range is `400` and the service is not called), status/delete response shapes, `404` |
| `careers/repository/CareersINRepositoryTest.java` | The tables exist (local PostgreSQL, rolled back), persistence, `skillOptions` replace on a detached job, unique `job_id`, `findAllByStatus`, `nextJobNumber()` always increasing and not going back after the newest job is deleted |
| `support/LocalDatabaseOnlyInitializerTest.java` | The localhost-only rule: which datasource URLs are accepted and which are refused (no database needed) |
| `support/` | `TestAuthentication` (makes a request look like it carried an admin JWT), `ReflectionAsserts`, `INJpaTestConfig`, `INPostgresDataJpaTest` (the JPA test slice that uses the application's PostgreSQL database instead of an embedded one), `INTablesMustBeEmptyExtension` and `LocalDatabaseOnlyInitializer` (test helpers) |

**Running the tests.** They run on the same local PostgreSQL database as the application (the datasource in `application.properties`, currently `wmadminportal`), so that server must be running:

```
mvn test -Dtest='com.wealthmax.adminPortal.careers_in.**.*Test'
```

- The tests only use the IN tables (`careers_in`, `applicant_in` and their three child tables). The repository and search tests load only the IN entities, so they cannot reach a v1 table. `INFullApplicationTest` starts the whole application, which runs `ddl-auto=update` over every entity exactly as a normal startup does, but its requests only read and write IN tables.
- Every test runs in a transaction that is rolled back, so a passing run leaves the tables as it found them. PostgreSQL id counters (`id` columns) and the job-number sequence still move forward, so the next real row may skip some `id` numbers and the next real job may skip some `WFA-IN-####` numbers (a gap in the ids is harmless).
- The tests assert exact row counts, so `careers_in` and `applicant_in` must be **empty** when they start. (Job ids are not asserted as `WFA-IN-0001`: the sequence is never rolled back, so the tests check them relative to the first number they get.) `INTablesMustBeEmptyExtension` checks this and stops with a message if a table has rows (for example after trying the API in Swagger). It never deletes anything: remove those rows (and their rows in `careers_in_skill_options`, `applicant_in_skills` and `applicant_in_industry_experience`) yourself first.
- **Local database only.** `LocalDatabaseOnlyInitializer` stops the test application before it starts (so before any connection or schema change) unless the resolved `spring.datasource.url` is a `jdbc:postgresql://` URL whose host is `localhost`, `127.0.0.1` or `::1`. It reads the URL Spring actually uses, so an override such as `SPRING_DATASOURCE_URL`, `-Dspring.datasource.url` or an external config file pointing at dtg or prod is refused too. The message names only the host, never the whole URL (it could contain a password). There is no switch to turn it off. `LocalDatabaseOnlyInitializerTest` covers the rule.
- The tests are not part of the deployed application (they are not in the jar) and the pipeline does not run them; they only run when a developer runs Maven locally.
- Never set `spring.jpa.hibernate.ddl-auto` to `create` or `create-drop` for these tests; it would drop the real tables.

The existing `AdminPortalApplicationTests.contextLoads` needs the real database too and is not part of this set.

### Modified files

| File | Change |
|---|---|
| `src/main/resources/application.properties` | `com.wealthmax.adminPortal.careers_in` added to `springdoc.packagesToScan` so the IN controllers appear in Swagger UI; `spring.sql.init.mode=always` and `spring.jpa.defer-datasource-initialization=true` so `schema.sql` runs after Hibernate has created the tables ([section 14](#14-database-impact--deployment)) |
| `src/main/java/com/wealthmax/adminPortal/config/SecurityConfiguration.java` | Explicit access rules for the IN paths, ahead of the old `/api/v1/applicant/**` and `/api/v1/careers/**` wildcards ([Decision M](#16-decisions)) |

New file outside the `careers_in` packages: `src/main/resources/schema.sql` (creates and aligns `careers_in_job_number_seq`, [section 13](#13-job-id-generation-wfa-in-)).

No v1 controller, service, repository, entity, DTO or mapper is modified. `SecurityConfiguration` is the only existing Java source file that is edited, and its v1 rules are unchanged. `INModuleBoundaryTest` also allows IN to use the shared `InvalidDataException`.

---

## 16. Decisions

| # | Decision | Status |
|---|---|---|
| **A** | Composite key type | ❌ **Superseded by L:** the composite unique key was removed; `id` stays the primary key |
| **B** | Composite key columns | ❌ **Superseded by L:** the applicant is identified by `emailId + mobileNumber` (any name) for the cooling period |
| **C** | Where the data is stored | ✅ **Decided:** **new tables** `applicant_in` and `careers_in`. The existing tables are not touched, so the IN rules have no effect on v1 |
| **D** | Composite key on `careers_in` | ✅ **Decided:** none, because a job posting has no person fields. IN uses its own separate DTOs, entity and repository |
| **E** | IN job update coverage | ✅ **Decided:** IN does **not** call v1 code; the update logic is replicated in the IN module (`CareersDetailsMapper.applyPartialUpdate`). It also updates `role` and `jobLocation`, and never lets a request change `jobId`, `datePosted` or `status` |
| **F** | Careers DTO grouping | ✅ **Decided:** `jobDetails` / `experienceRequirements` / `salaryDetails`, in their own IN DTO classes (`careers_in.careers.dto`) |
| **G** | Job ID sequence | ✅ **Decided:** IN has its own series in the format `WFA-IN-0000` (`WFA-IN-0001`, …), independent of the v1 `WFA####` series ([section 13](#13-job-id-generation-wfa-in-)); the number comes from a database sequence, see N |
| **H** | Module boundary | ✅ **Decided:** IN is a separate module with its own controllers, services, repositories, entities, DTOs and mappers; no v1 class is imported. Only shared infrastructure (`common.exception`, `MailgunServices`, email template files) is reused |
| **I** | Request / response DTOs | ✅ **Decided:** every endpoint takes a request DTO and returns a separate response DTO ([section 5](#5-dtos)); no class is used for both. Request DTOs carry the validation and have no server-managed fields; response DTOs carry the server-managed fields and no validation. *Proposed with the implementation, open to change:* the `…CreateRequestDto` / `…UpdateRequestDto` / `…ResponseDto` names, splitting the **section** classes as well as the roots, and a dedicated `CareersUpdateRequestDto` instead of reusing the create request |
| **J** | Duplicate by email + mobile | ✅ **Decided, now part of L:** the same `emailId + mobileNumber` is the same applicant whatever the name. The separate name + email + mobile check and its message were removed with L |
| **K** | Pagination, global search and filters on the list endpoints | ✅ **Decided (requirement):** `GET /careers/IN/getAll` gets pagination, a global search and filters on department, jobType and workStyle; `GET /applicant/IN/getAllApplicant` gets pagination and a global search, with no filter for now ([sections 6.3 and 7.2](#63-get-apiv1applicantingetallapplicant)). *Chosen with the implementation, open to change:* query parameters `search` / `page` / `size` (0-based, default `0` / `10`, `size` at most 100); a custom `PageResponseDto` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`) in place of a bare array; the searched columns listed in sections 6.3 and 7.2 (including `skills` / `skillOptions`); partial, case-insensitive matching for the search and exact, case-insensitive matching for filters; one value per filter; filters and search combined with AND; a fixed sort order (no client-chosen `sort`); `400` for a bad `page` / `size` instead of silently correcting it |
| **L** | Cooling period instead of a unique key | ✅ **Decided:** remove `uk_applicant_v2_identity`; a server-set column `cooldown_until` (UTC, `Instant` / `timestamptz`) = submit time + 6 months; the service rejects the same email + mobile while `cooldown_until` is after now, for any job; one request at a time per applicant; existing rows left as they are (`NULL`); the old key is dropped by hand ([section 12](#12-duplicate-application-protection-cooling-period)). *Proposed with the implementation, open to change:* the column stays nullable; the lock is a PostgreSQL advisory lock keyed on a hash of lower-case email + mobile, held only for lock + check + insert (`TransactionTemplate`), with the emails sent after the commit; the 6 months is a constant (`Period.ofMonths(6)`) in the service, not a property; `cooldownUntil` is not in the response DTO. *Message:* the learner asked for the months left to be shown in the 409 message; rounding up with a minimum of 1, "month" / "months" and dropping the date were proposed with the implementation |
| **M** | Who may call the IN endpoints | ✅ **Decided:** narrow the IN rules. Public: `POST /applicant/IN/addApplicant`, `GET /careers/IN/getAll`, `GET /careers/IN/get/{id}`, `GET /careers/IN/get/status/{status}` (what the public website calls). Needs a JWT: `GET /applicant/IN/getApplicant/{id}`, `GET /applicant/IN/getAllApplicant`, `DELETE /applicant/IN/delete/{id}`, `POST /careers/IN/createJob`, both `PUT /careers/IN/update/...` routes, `DELETE /careers/IN/delete/{id}`, and any new path under the two IN prefixes. Reason: under the old `permitAll` wildcards anyone could read every applicant's personal data (name, email, mobile, CTC, resume) and delete applicants or jobs without logging in. *Chosen with the implementation, open to change:* the protected rule is `authenticated()` (any logged-in user), not `hasAuthority("ADMIN")` like the referral rule; the rules sit before the old wildcards because the first match wins; v1 paths are unchanged; the status and by-id job reads are public for every status, so an `INACTIVE` job can still be read by anyone who knows its id (see [section 18](#18-known-limitations--follow-ups)) |
| **N** | Job id from a database sequence | ✅ **Decided:** `WFA-IN-####` comes from the PostgreSQL sequence `careers_in_job_number_seq`, created by `schema.sql`, instead of "latest row + 1" ([section 13](#13-job-id-generation-wfa-in-)). It removes duplicate ids from simultaneous creates and the reuse of a deleted job's id. *Chosen with the implementation, open to change:* a startup SQL file rather than a manual step; the first start on a database with existing jobs aligns the sequence by itself; gaps in the numbers are accepted |
| **O** | Job status as an enum | ✅ **Decided:** `JobStatusEnum` with `ACTIVE` and `INACTIVE` instead of free text; any other status is `400`. *Chosen with the implementation, open to change:* case-sensitive matching; `status` removed from the global search columns (it cannot be lower-cased as an enum); existing rows with other values are fixed by hand ([section 14](#14-database-impact--deployment)); adding a value later needs the `CHECK` constraint changed by hand |
| **P** | Validation on job update | ✅ **Decided:** `PUT /update/{id}` validates what it is sent with the same rules as create, and the service also checks the min/max ranges of the finished job. *Chosen with the implementation, open to change:* rules on the update DTOs that let `null` through (so a partial update still works), and a plain `InvalidDataException` (`400`) for a range that is only inverted against the stored value, rather than a field-level violation; nothing is saved when the check fails |
| **Q** | Fewer queries for the skill lists | ✅ **Decided:** `@BatchSize(size = 50)` on the three `@ElementCollection` lists (`skills`, `industryExperience`, `skillOptions`), which stay `EAGER`. *Why not the alternatives:* `LAZY` would fail where the mapper reads the lists outside a transaction, and `JOIN FETCH` with paging makes Hibernate page in memory. A global `hibernate.default_batch_fetch_size` would do the same for every collection and was not chosen |
| **R** | Name: `IN` instead of `v2` | ✅ **Decided:** every `v2` becomes `IN`: the URL segment (`/api/v1/applicant/IN`, `/api/v1/careers/IN`), the package and folder, the class and test names, the tables and the sequence, and these docs. The existing UK code (v1) is not touched and stays. Lowercase names for the package, tables and sequence (`careers_in`, `applicant_in`, `careers_in_job_number_seq`) were proposed and accepted at the implementation step. The constraint name `uk_applicant_v2_identity` is deliberately unchanged: it is the name of an object that already exists in databases, not a name this code chooses. Databases that already have the old tables need the manual rename SQL in [section 14](#14-database-impact--deployment) |
| **S** | CTC format (LPA) | ✅ **Decided (requirement and regex given by the learner):** `currentCtc` and `expectedCtc` are in LPA and must match `^\d{1,2}(\.\d+)?$` (1 or 2 digits, optional decimal part: `4.5`, `5`, `12`). Because `@Pattern` only validates text, the two request-DTO fields are `String`; the entity column and the response stay `BigDecimal`, and `ApplicantDetailsMapper` converts. *Consequences, open to change:* the field stays optional (`null` passes); a JSON number is still accepted; values in the old rupee style (`45000`) are now `400`; the regex allows any number of decimals but the column is `numeric(15,2)`, so `4.555` is stored as `4.56`; `00` and `05` pass; the error text ("... must be in LPA with up to 2 digits before the decimal point, e.g. 4.5, 5 or 12") was proposed with the implementation |

---

## 17. Test checklist

Legend: ✅ covered by an automated test (run with `mvn test -Dtest='com.wealthmax.adminPortal.careers_in.**.*Test'` on a local PostgreSQL with empty IN tables, see [section 15](#new-test-files)) · ☐ needs a manual check against a real environment (mail or blob storage, v1 tables, a fresh database).

**Regression (v1 must be unchanged)**

The automated IN tests only touch the IN tables, so the v1 checks below are manual.

- ✅ No v1 controller, service, repository, entity or DTO is modified; the only existing Java file that is edited is `SecurityConfiguration` (`git status` shows the `careers_in` files, `SecurityConfiguration`, `application.properties`, `schema.sql` and this document); `INModuleBoundaryTest` keeps IN from importing v1 classes other than the shared ones it lists.
- ✅ The v1 paths `/api/v1/careers/**` and `/api/v1/applicant/**` are still open without a token (`INSecurityRulesTest`).
- ☐ `GET /api/v1/applicant/getAllApplicant` still returns only v1 rows, and the IN list only IN rows (by construction the IN list reads only `applicant_in`; not tested with v1 rows).
- ☐ A v1 job (`WFA0001`) and IN jobs (`WFA-IN-####`) do not affect each other's lists or ids (by construction: the IN numbering reads only `careers_in`; not tested with v1 rows).
- ☐ `POST /api/v1/applicant/addApplicant` with the old payload: saved in `applicant`, both real emails sent, response shape unchanged.
- ☐ All v1 careers endpoints return the same responses as before.
- ☐ Startup logs show no DDL for the `applicant` or `careers` tables on the real database.

**Access (security)** (`INSecurityRulesTest`, no database needed)

- ✅ Without a token, `POST /applicant/IN/addApplicant` and the three `GET` job endpoints are let through.
- ✅ Without a token, `getApplicant`, `getAllApplicant`, applicant `delete`, `createJob`, both `PUT /update/...` routes and job `delete` return `403`.
- ✅ With a logged-in admin, all of those are let through to the controller.
- ✅ An unknown path under `/api/v1/careers/IN/` or `/api/v1/applicant/IN/` returns `403` (it is not opened by the old wildcards).
- ☐ A real JWT from `/api/v1/auth/...` on a protected call, and that the admin front end sends it, are not tested automatically.

**Request / response DTOs**

- ✅ A request DTO has no server-managed field: `ApplicantCreateRequestDto` has no `id` or `createdAt`; `CareersCreateRequestDto` and `CareersUpdateRequestDto` have no `id`, `jobId`, `datePosted` or `createdDate` (`RequestResponseContractTest`).
- ✅ No request DTO is returned by a controller and no response DTO is accepted as a body; the response DTOs carry no validation annotations (`RequestResponseContractTest`).
- ✅ Every request field reaches the entity and comes back unchanged in the response DTO; the response adds only the server-managed fields (`ApplicantDetailsMapperTest`, `CareersDetailsMapperTest`).
- ✅ Through the real application, a create body that contains `id`, `createdAt`, `jobId`, `datePosted` or `createdDate` is accepted and those values are ignored (`INFullApplicationTest`).
- ✅ `PUT /update/{id}` accepts `{}` or a single field, and a `status` or `jobId` sent in the body does not change the job (`INFullApplicationTest`, `CareersDetailsMapperTest`).

**Applicant IN**

- ☐ Upload a resume via `/blob/upload`, then submit IN with the returned name. The row is saved in `applicant_in`, `resume_file_name` is populated, and `/blob/download/{resumeFileName}` works.
- ✅ Cover letter upload is optional — submitting without it is valid.
- ✅ Both emails sent with the correct name, email, phone and position; applicant text is HTML-escaped; a missing job title leaves no `{{position}}` in the email (`ApplicantINServiceImplTest`). ☐ Real delivery through Mailgun.
- ✅ Missing `fullName`, invalid email or missing `personalDetails.resumeFileName` → 400 with dotted `fieldName`s.
- ✅ Missing `location` (current city) or `hometown` → 400.
- ✅ Missing `highestQualification`, `degreeCourse`, `collegeUniversityBoard` or `yearOfPassing` → 400.
- ✅ Missing `currentCompany`, `currentDesignation` or `previousCompanies`, or an empty `industryExperience` → 400.
- ✅ `hasEmploymentGap = true` without `employmentGapDetails` → 400.
- ✅ `hasEducationGap = true` without `educationGapDetails` → 400; `hasEducationGap = false` without it → valid.
- ✅ `hasOtherOffer = true` without `otherOfferDetails` → 400; `hasOtherOffer = false` without it → valid.
- ✅ `declarationAccepted = false` or missing → 400; `finalDetails` missing altogether → 400.
- ✅ `howDidYouHearAboutUs` missing → 400.
- ✅ Invalid `gender` value → 400 "Gender has an invalid value".
- ✅ Invalid `shiftComfort` value (not YES / NO / WANT_TO_DISCUSS) → 400.
- ✅ `currentCtc` / `expectedCtc`: `4.5`, `5`, `12`, `99.99` (and a JSON number such as `4.5`) are valid; `100`, `-1`, `4.`, `.5`, `1e1`, blank and text → 400 with the LPA message; `null` / missing is still valid (`ApplicantCreateRequestValidationTest`, `ApplicantINControllerTest`). ☐ More than 2 decimals (`4.555`) is accepted and stored rounded to 2 by the `numeric(15,2)` column.
- ✅ Same email + mobile again within the cooling period → 409, for the same job and for a different job, whatever the name (`ApplicantINServiceImplTest`, `INFullApplicationTest`).
- ✅ The 409 message shows the months left: 6 right after applying, 4 for 3 months and 10 days left, "1 month" for a few days or hours left, never 0 (`ApplicantINServiceImplTest`).
- ✅ `cooldownUntil` is set by the server to now in UTC + 6 months; a `cooldownUntil` sent in the request body is ignored (`ApplicantINServiceImplTest`, `INFullApplicationTest`).
- ✅ A cooling period that is over, or `NULL` (rows from before the change), does not block (`ApplicantINRepositoryTest`).
- ✅ The email's letter case is ignored (for example `JANE.DOE@EXAMPLE.COM`); the mobile number must match exactly.
- ✅ The advisory lock is taken before the check and the insert (`ApplicantINServiceImplTest`) and works on PostgreSQL (`ApplicantINRepositoryTest`). ☐ Two truly simultaneous requests for the same applicant (one `200`, one `409`) are not tested automatically.
- ✅ Same email but a different mobile number → accepted; the same mobile number with a different email → accepted.
- ☐ A person who applied through v1 can apply through IN (by construction: the duplicate checks read only `applicant_in`; not tested with v1 rows).
- ✅ `skills` and `industryExperience` round-trip as arrays.
- ✅ Delete removes the row and its skill rows and industry experience rows.

**Careers IN**

- ✅ Create → 201, `jobId` = `WFA-IN-` + the next sequence number padded to 4 digits (`WFA-IN-0001` on a new database), then the following numbers; 5-digit numbers (`WFA-IN-10000`) work; `datePosted` = today; client-sent `jobId`, `datePosted` and `createdDate` are ignored (`CareersINServiceImplTest`, `INFullApplicationTest`).
- ✅ `nextJobNumber()` always increases and does not go back after the newest job is deleted (`CareersINRepositoryTest`).
- ✅ `schema.sql` creates the sequence and, on a database that already has IN jobs, starts it after the highest existing number; running it again does not move the sequence back (checked by hand on the local PostgreSQL: it ran twice and the second run continued where the first stopped; not an automated test).
- ☐ Two truly simultaneous `createJob` requests get different ids (guaranteed by `nextval`, not tested with parallel requests).
- ☐ A v1 job (`WFA0007`) in `careers` has no effect on the IN numbering (by construction: the numbering reads only the sequence and `careers_in`; not tested with v1 rows).
- ✅ `skillOptions` round-trips; sending a new list on update replaces it (also on a detached job, as the service does).
- ✅ Partial update of one field in one section leaves everything else untouched; `jobId`, `datePosted` and `status` cannot be changed through update; `role` and `jobLocation` can.
- ✅ `ctcMin > ctcMax` or `minExperienceYears > maxExperienceYears` on create → 400.
- ✅ Update (`PUT /update/{id}`): `{}` and a single field are still valid; a blank `jobTitle`, a negative CTC or number of years, and an inverted range sent together → 400 and the service is not called (`CareersCreateRequestValidationTest`, `CareersINControllerTest`).
- ✅ Update: sending only `ctcMax` below the stored `ctcMin`, or only `maxExperienceYears` below the stored minimum → 400 `InvalidDataException`, nothing saved; a change that keeps the range valid is saved (`CareersINServiceImplTest`).
- ✅ Status is `ACTIVE` or `INACTIVE`; status update, delete and get-by-status work on `careers_in` only (`CareersINControllerTest`, `CareersINServiceImplTest`, `CareersINRepositoryTest`). ☐ A status that is not one of the two returning `400` through the whole application is not tested automatically.
- ✅ `status` is no longer a search column of `getAll`.
- ✅ Job descriptions longer than 500 characters are stored.

**List endpoints: pagination, search and filters** (`ApplicantINSearchTest`, `CareersINSearchTest`, the controller and service tests, `INFullApplicationTest`)

- ✅ With no parameters, both endpoints return page 0 of size 10, newest first, with the real `totalElements` / `totalPages` / `last`.
- ✅ `page` and `size` cut the list correctly; a page past the end is empty with the real total; `page` < 0, `size` < 1, `size` > 100 or a non-number → 400 and the service is not called.
- ✅ The search matches part of any searched column, ignoring case and surrounding spaces; a blank search returns everything; no match returns an empty page with `totalElements` 0.
- ✅ A search on a skill finds the row; a row with several matching skills is returned once and counted once; the returned row still has its full skill list.
- ✅ `%` and `_` typed in the search are matched literally; `\` does not cause an error.
- ✅ Columns outside the searched list (description, note, degree, college, free-text details) are not searched.
- ✅ Careers filters `department`, `jobType`, `workStyle`: exact match ignoring case and spaces, no partial match, blank = not applied, and filters and search apply together (AND).
- ✅ Swagger (`/v3/api-docs`) lists `search`, `page`, `size` (and the three filters on careers) as query parameters, and no filter on the applicant list.
- ☐ Check the speed of a search on the real PostgreSQL with realistic row counts (see [section 18](#18-known-limitations--follow-ups)).
- ☐ The number of queries for a page of 100 applicants / jobs after `@BatchSize(size = 50)` (expected: the page and count queries plus about 2 per list, instead of one per row and list) is not measured; check with `spring.jpa.show-sql=true` or Hibernate statistics.

**Database**

- ☐ Run the database tests (`CareersINRepositoryTest`, `CareersINSearchTest`, `ApplicantINRepositoryTest`, `ApplicantINSearchTest`, `INFullApplicationTest`) on a local database whose IN tables are empty. They were updated for the status enum, the sequence and the new access rules, but were not run after those changes because the local tables already held rows.
- ☐ On every existing database, check `SELECT DISTINCT status FROM careers_in` before deploying ([section 14](#14-database-impact--deployment)).
- ✅ The five new tables exist on the local PostgreSQL database that the tests use (`ApplicantINRepositoryTest`, `CareersINRepositoryTest`). ☐ Creation from an empty database is no longer tested automatically; confirm on a fresh database at the first startup of a new environment.
- ✅ `applicant_in` has `cooldown_until` as `timestamp with time zone` and no `uk_applicant_v2_identity` (`ApplicantINRepositoryTest`; fails until the manual `ALTER` in section 14 has been run).
- ✅ Every entity field has a column in the DDL above, and every column has an entity field: the DDL in [section 14](#14-database-impact--deployment) was compared column by column (type, NOT NULL, `CHECK`, unique key) with the DDL Hibernate generates for the PostgreSQL dialect from the IN entities, and `ApplicantDetailsMapperTest` / `CareersDetailsMapperTest` prove that every request field reaches an entity field and back into the response DTO.

---

## 18. Known limitations & follow-ups

| Item | Detail |
|---|---|
| **IN jobs are not auto-deactivated** | The existing scheduled job (`CronJobs`) only reads and updates v1 `careers`. An IN job stays `ACTIVE` after its `applyBy` date until an admin changes its status. Adding the same behaviour for IN would be a separate scheduled job in the IN module. |
| **Email failure after save** | As in v1, the application is saved before the emails are sent. If Mailgun fails, the request returns `500` but the row exists, and a retry then returns `409` (the cooling period has started). |
| **Gaps in job ids** | `WFA-IN-####` comes from a database sequence, so two simultaneous creates never clash and a deleted job's id is never reused. The price is that numbers can be skipped (a failed insert, a rolled-back test run), so the ids are unique and increasing but not gap-free ([section 13](#13-job-id-generation-wfa-in-)). |
| **Access is "logged in", not "admin"** | The protected IN endpoints use `authenticated()`: any valid JWT works, whatever the user's role. Restricting them to `ADMIN` (as the referral rule does with `hasAuthority("ADMIN")`) is a one-line change in `SecurityConfiguration` ([Decision M](#16-decisions)). |
| **Inactive jobs are still readable** | `GET /get/{id}` and `GET /get/status/{status}` are public for every status, so anyone can read an `INACTIVE` job by id or by asking for `INACTIVE`. Showing only `ACTIVE` jobs to the public would need a check in the service. |
| **Update on an already inverted range** | `updateJobDetails` checks the whole job after the change. A row whose stored min/max range is already inverted (only possible if it was written outside the API) refuses every update, even one that does not touch the range, until the range is fixed. |
| **Existing job statuses** | A `careers_in` row whose status is not `ACTIVE` or `INACTIVE` cannot be read until it is fixed by hand ([section 14](#14-database-impact--deployment)). |
| **Job-id ordering past 9999** | Ids sort correctly as text up to `WFA-IN-9999`; beyond that they still generate but sort out of order (same limit as v1). |
| **Schema changes later** | `ddl-auto=update` only adds. New NOT NULL columns on tables with data, changed types and new enum values (the `CHECK` constraints) need manual SQL ([section 14](#14-database-impact--deployment)). |
| **One application per cooling period** | By decision L, the same email + mobile can apply once every 6 months, across all jobs. A person who changes either their email or their mobile number is treated as a new applicant. |
| **Cooling period depends on the lock** | No database constraint backs the rule; two requests for the same applicant are kept apart by the advisory lock in `saveApplicantDetails`. Any other code path that inserts into `applicant_in` without taking the lock (a script, a future endpoint) is not protected. |
| **Changing the 6 months** | `cooldown_until` is stored per row, so a new length applies only to new applications unless existing rows are updated by SQL. |
| **Clients must send the JWT** | The admin front end now has to send the JWT on applicant list / get / delete and on job create / update / status update / delete; without it those calls return `403`. The public website needs no change. |
| **List endpoints changed shape** | `getAll` and `getAllApplicant` now return a page object (`content`, `page`, …) instead of an array. Any client written against the array (the front end that calls these IN endpoints, not known to this plan) must read `content` and send the new query parameters. |
| **Search speed** | The search is `lower(column) like '%term%'` over several columns, which a normal PostgreSQL index cannot serve, so it scans the table. That is fine for a few thousand rows; if the tables grow large, a trigram index (`pg_trgm`) or a full-text column would be the next step. |
| **Skill lookups** | The skill lists are `EAGER` but loaded in batches of 50 rows (`@BatchSize(size = 50)` on `skills`, `industryExperience` and `skillOptions`), so a page of 100 costs about 2 extra queries per list instead of one per row (about 4 for applicants, 2 for jobs). Not measured yet ([section 17](#17-test-checklist)). Making the lists `LAZY` was not chosen because the mapper reads them outside a transaction. |
| **One value per filter** | `department`, `jobType` and `workStyle` take a single value each, matched exactly (`Full-time`, not `Full`). A multi-select filter would need a list parameter. |
| **Fixed sort** | The list order is fixed (`jobId` / `id` descending). `jobId` sorts as text, so the [job-id ordering limit](#13-job-id-generation-wfa-in-) applies. |
| **Existing context test** | `AdminPortalApplicationTests.contextLoads` needs the real database; `INFullApplicationTest` also starts the whole application against it (and rolls its writes back). |
| **Tests need the local database and empty IN tables** | `mvn test` for the IN tests fails if the local PostgreSQL is not running, and stops with a message if `careers_in` or `applicant_in` already holds rows (see [section 15](#new-test-files)). They are written for a developer's own local database only: `LocalDatabaseOnlyInitializer` refuses to start them against any other host. |
