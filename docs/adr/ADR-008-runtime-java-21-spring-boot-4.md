# ADR-008: Runtime: Java 21 and the Newest Supported Spring Boot

## Status
Accepted 2026-11-05. Follow-up the same day (gate D-01): the pom is set to Spring Boot 4.1.2.

## Context
The old repository runs Spring Boot 3.2.5, which is out of open-source support (SEC-30). The new
repository had no code yet, so its runtime could be set to any line without an upgrade cost. On
2026-11-05 Spring Boot 3.5's open-source support had ended (2026-06-30), and the newest release on
Maven Central was 4.1.2 (4.2 was still a release candidate).

## Decision
Java 21 and the newest Spring Boot line still in open-source support, chosen while there is no code.
The pom is set to **Spring Boot 4.1.2**. That brings Spring Framework 7, Hibernate 7.4, Flyway 12.4,
Jackson 3, Spring Security 7.1 and JUnit 6, all managed by the Boot parent. Three Boot 4 changes
matter for this project: `spring-boot-starter-flyway` is needed for Flyway to run, the web starter is
`spring-boot-starter-webmvc`, and test starters are per technology. springdoc moves to the 3.x line.

## Consequences

### Positive
- Security fixes are available, and the 4.x hop is paid once, before any module exists.

### Negative
- Fewer worked examples exist for Boot 4 than for Boot 3.
- Boot 4.0's own support ends 2026-12-31, so the supported lines move. Check spring.io support dates
  before each upgrade, and follow the `dependency-upgrade` skill for the hop.

### Neutral
- The pom comments name why each override exists, and drop one when Boot's managed version catches up.

## Alternatives Considered
**Stay on 3.5.x:** rejected. Its open-source support has ended, and the hop to 4.x would then happen
with modules already written.

## References
- `docs/revamp-audit-report.md`, section 6.6
- `.claude/skills/dependency-upgrade/SKILL.md`
- `plans/Sarthak-plan/patch-1b1-boot-4-pom-summary.md`
