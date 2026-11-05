# Patch 1b1: Boot 4.1.2 pom

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1b1

## Problem
The pom was on Boot 3.5.16, whose open-source support ended 2026-06-30, and it still carried Testcontainers, which the team dropped (D-02).

## Solution (`pom.xml` only)
- Parent `3.5.16` to `4.1.2` (D-01).
- `spring-boot-starter-web` to `spring-boot-starter-webmvc`. `flyway-core` to `spring-boot-starter-flyway`, with `flyway-database-postgresql` kept.
- Test starters: `spring-boot-starter-test` plus `webmvc-test`, `data-jpa-test`, `security-test`, `validation-test`. `spring-security-test` is gone.
- Testcontainers removed. `archunit-junit5:1.3.0` replaced by core `archunit:1.5.1`.
- springdoc `3.2.0`, jjwt `0.13.0`, azure-storage-blob `12.37.2`, poi-ooxml `5.5.1`, each as a property.
- The `postgresql.version` pin is dropped (Boot manages 42.7.14).
- The compiler plugin takes its version and `release` 21 from the parent.
- `-Amapstruct.unmappedTargetPolicy=ERROR` added (D-05).

## Verification (real runs, 2026-11-05)
- Baseline before the change: `mvn -o -q compile` exit 0 on 3.5.16.
- After: `mvn -B -q compile` exit 0. `mvn -B -q test-compile` exit 0.
- `project.parent.version` prints `4.1.2`. `mvn dependency:tree` has 0 lines matching "testcontainers".
- D-05 proof:
  - RED: with a throwaway mapper that has an unmapped target field, `mvn compile` exited 1 with `Unmapped target property: "neverMapped"`.
  - GREEN: after deleting the probe, compile exit 0. The probe is gone; the only source file is `WmAdminPortalApplication.java`.
- **First attempt invalid.** The first "red" run came back exit 0, but the file write had been denied by the gate, so the probe never existed. I discarded that result and re-ran in sequence.

## Rulings
See the plan's `## Rulings`: steps run back to back, work stays uncommitted on `Dev`, `@MockitoBean`, `safe.directory`.

## Limitations and follow-up
- The context-load check is in 1h4, because it needs the database guard.
- springdoc 3.2.0, jjwt 0.13.0, azure 12.37.2 and POI 5.5.1 resolve and compile, but nothing uses them yet. Their runtime behaviour is proven in the phases that first use them (Phase 2 and 3, and the Excel export in Phase 7).
- The `spring-boot-properties-migrator` run is in 1h4.
