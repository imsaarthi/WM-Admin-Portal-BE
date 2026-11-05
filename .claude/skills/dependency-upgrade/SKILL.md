---
name: dependency-upgrade
description: Use when upgrading Spring Boot, Java, Hibernate or any Maven dependency in WM-Admin-Portal-BE, when a dependency has a known vulnerability, or when checking whether our versions are still supported. Covers support-status checks, a green baseline first, one version hop per PR, the Java 17 to 21 and Boot 3.2 to 3.4+ revamp hops, the order for adopting Flyway, the Boot 4 changes that hit this codebase, and runtime verification.
user-invocable: true
---

# Dependency Upgrade: WM-Admin-Portal-BE

**One hop per PR, on a green baseline, with the full test suite at every step.** An upgrade mixed
with feature work can't be reviewed or rolled back cleanly.

## 1. Know Where You Stand

```bash
mvn -v                                                                  # Maven and Java
mvn help:evaluate -Dexpression=project.parent.version -q -DforceStdout  # Spring Boot version
```

**Revamp target:** Java 21, the newest Spring Boot line in open-source support, Flyway (ADR-008 in
`docs/adr/`). Keep to a line that is still in support on the day you scope the ticket.

**Today** (`pom.xml`, re-read it rather than trusting this list, set 2026-11-05): Spring Boot parent
**4.1.2** (Spring Framework 7.0, Hibernate 7.4, Flyway 12.4, Jackson 3.1, Spring Security 7.1,
JUnit 6, Mockito 5.23, Lombok 1.18.48, PostgreSQL driver 42.7.14, all managed by Boot); Java 21;
MapStruct 1.6.3 (1.7 is beta only); springdoc 3.2.0; jjwt 0.13.0; azure-storage-blob 12.37.2;
poi-ooxml 5.5.1; ArchUnit core 1.5.1 (plain `@Test`, no JUnit engine). No Testcontainers (ADR-007).

**Check support status** at https://endoflife.date/spring-boot. As recorded in this skill's
earlier version (endoflife.date, 2026-08-21) and consistent with other trackers:

| Line | Open-source support | Latest |
|---|---|---|
| 3.2 (the old repository) | ended | |
| 3.3 | ended 2025-06-30 | 3.3.13 |
| 3.4 | ended 2025-12-31 | 3.4.13 |
| 3.5 (the pom before 2026-11-05) | ended 2026-06-30 | 3.5.16 |
| 4.0 | until 2026-12-31 | 4.0.8 |
| **4.1 (our `pom.xml` today)** | until 2027-07-31 | 4.1.2 |

**Every 3.x line gets no open-source security fixes.** The pom moved to 4.1.2 on 2026-11-05 (Phase 1
step 1b1; the patch summary is in `plans/Sarthak-plan/`). Dates move, so check the support page again
before planning.

## 2. Green Baseline First

On a fresh branch from the integration branch, with local PostgreSQL running and the IN tables
empty (`integration-testing`):

```bash
mvn clean verify > target/baseline.log 2>&1; echo "exit=$?"
grep -E "Tests run:|BUILD" target/baseline.log | tail -5
```

The pipeline doesn't run tests, so this local run **is** the baseline. Record the test count.
Don't start on a red baseline. Fix it first, or write down exactly which tests already fail so
they aren't blamed on the upgrade.

## 3. Inventory

```bash
mvn versions:display-dependency-updates > target/dep-updates.txt
mvn versions:display-plugin-updates     > target/plugin-updates.txt
mvn versions:display-property-updates   > target/property-updates.txt
```

(The `versions` plugin runs by name; nothing is added to the pom.)

**Vulnerabilities:** OWASP Dependency-Check:

```bash
mvn org.owasp:dependency-check-maven:check -DnvdApiKey="$NVD_API_KEY"
```

The first run downloads the whole NVD database and is slow without an API key. The report lands
in `target/dependency-check-report.html`. Never commit the key.

**Drop what's unused before upgrading it.** None of the old repository's dead dependencies
(ModelMapper, SendGrid, `commons-fileupload`, `commons-io`, the Azure Spring starter) were brought
across. Removing a dependency is a team decision. Raise it, don't just delete it.

## 4. The Path: One Hop per PR

Order for this repository. The Java 17 to 21 hop, the 3.x hops and the Flyway adoption are behind
us: the repository started on Java 21 and Flyway, and was set straight to Boot 4.1.2 before any
module existed (Phase 1 step 1b1). So the big 3-to-4 hop was paid once, with no code to migrate.

From here the path is the ordinary one: patch and minor releases of 4.1, then the next supported
line when 4.1 nears the end of its open-source support. One hop per PR (section 6). Use
`@MockitoBean`, never `@MockBean` (removed in Boot 4).

Java: Boot 3.5 and 4.0 support Java 17 to 25, and 4.1 supports 17 to 26. Staying on Java 21 is
fine.

## 5. Spring Boot 4.0: What Hits This Codebase

From the official Spring Boot 4.0 Migration Guide, checked against our pom and code.

Versions Boot 4.1.2 manages: Hibernate 7.4, Flyway 12.4, Spring Security 7.1, Jackson 3.1 (with
Jackson 2 still managed). Boot 4.0 managed Hibernate 7.2 and Flyway 11.x.

- **Flyway needs its own starter.** Once Flyway is adopted (`flyway-core` plus
  `flyway-database-postgresql`), replace `flyway-core` with `spring-boot-starter-flyway` and keep
  `flyway-database-postgresql`. **Without the starter, Flyway auto-configuration doesn't run and
  migrations silently stop running.**
- **`spring-boot-starter-web` is deprecated.** Use `spring-boot-starter-webmvc`.
- **Test starters are per technology.** Replace `spring-security-test` and friends with the
  matching `spring-boot-starter-*-test` starters, and add the ones for what's under test.
- **`@MockBean` is gone.** Use `@MockitoBean` (already the rule here).
- **`@SpringBootTest` no longer sets up MockMvc.** Tests need `@AutoConfigureMockMvc`.
- **Jackson 3.** `com.fasterxml.jackson.databind`, `.core` and `.datatype` move to
  `tools.jackson.*` (`common/exception/GlobalExceptionHandler` imports `JsonMappingException`;
  `grep -rn "com.fasterxml.jackson" src/main` finds the rest). `com.fasterxml.jackson.annotation`
  stays. `spring.jackson.use-jackson2-defaults=true` eases the switch. jjwt's `jjwt-jackson` uses
  Jackson 2, which Boot 4 still manages.
- **Hibernate 7.** Re-run every repository test and check each `Specification` search
  (`SearchSpecifications`).
- **Jakarta EE 11, Servlet 6.1, Spring Framework 7, Spring Security 7.** `SecurityConfiguration`
  and `JwtAuthenticationFilter` are the likely hotspots.
- **Stopgap:** `spring-boot-starter-classic` and `spring-boot-starter-test-classic` bring back the
  old all-in-one classpath. Use them to get compiling, then move to the specific starters.

**Found in practice during the Boot 4.1.2 set-up** (each one stopped the build or the start-up):
- Flyway needs `spring-boot-starter-flyway`. The web starter is `spring-boot-starter-webmvc`. Test
  starters are per technology: `webmvc-test`, `data-jpa-test`, `security-test`, `validation-test`.
- Test classes moved packages: `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.webmvc.test.autoconfigure.{WebMvcTest, AutoConfigureMockMvc}`,
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`, and `@EntityScan` is
  `org.springframework.boot.persistence.autoconfigure.EntityScan`. `@MockitoBean` is
  `org.springframework.test.context.bean.override.mockito.MockitoBean`.
- `spring.jackson.serialization.write-dates-as-timestamps` is now
  `spring.jackson.datatype.datetime.write-dates-as-timestamps`. An old key stops the whole context
  from starting.
- `server.error.*` is now `spring.web.error.*`, and `hibernate.dialect` no longer needs to be set.
- Hibernate 7 and PostgreSQL keep microseconds. Cut Java timestamps to microseconds before saving, or a
  saved entity and the reloaded row differ (see `JpaAuditingConfig`).
- Nested classes inside a test class are not component-scanned: `@Import` a probe controller by hand.
- Run `spring-boot-properties-migrator` once (runtime scope) and remove it in the same step. It
  reported every renamed key above that the tests had not.

**Third-party libraries to check at this hop:** springdoc (2.8.x is the Boot 3 line; 3.x is for Boot 4),
jjwt 0.12.5, MapStruct, Lombok, azure-storage-blob, Apache POI, the Flyway
modules.

## 6. Each Hop, Step by Step

1. Branch from the integration branch.
2. Bump the version (and any library the hop needs).
3. Add the properties migrator for this hop only:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-properties-migrator</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
4. `mvn clean verify`. Fix compile errors, then test failures.
5. Start the app locally and read the startup log. The migrator lists renamed and removed
   properties. Fix the **non-secret** keys in `application*.yml`. If a renamed key holds a
   secret, tell DevOps the new key name: the secret values are theirs and this team doesn't touch
   them.
6. Check each pinned version. If Boot's managed version has caught up, delete the override.
7. Remove the migrator.
8. Full suite again. The test count must match the baseline.
9. Runtime check (section 7).
10. Patch summary, then a PR (`azure-pr`) when the user asks. Let each hop settle before starting
    the next.

## 7. Verify at Runtime

Tests aren't enough for an upgrade. Start the app locally and confirm:
- It starts with no new warnings you can't explain.
- Flyway (once adopted) ran: the log shows migrations applied, or "Schema ... is up to date",
  and `ddl-auto=validate` passed.
- Login works (password then OTP).
- Swagger UI loads.
- A public IN call (`GET /api/v1/careers/IN/getAll`) returns data, and a protected IN call
  without a token returns 403.
- A list endpoint pages correctly (`PageResponseDto`).

Then follow `verification-before-completion` for the report.

## Rules

- Never upgrade on a red baseline.
- One hop per PR. No feature work in the same PR. A Java hop is never combined with a Boot hop.
- Prefer Boot's managed versions. Pin a version only for a reason (a security fix), and say why in
  a pom comment.
- Remove the properties migrator before merging.
- Rollback is reverting the PR. Keep hops small so that stays cheap.
- Secrets in `application*.yml` are DevOps's. An upgrade PR never edits their values.
