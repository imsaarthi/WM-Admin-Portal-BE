# Patch 1g1 to 1g3: Module boundary test

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, steps 1g1, 1g2, 1g3

## Problem
The compiler does not enforce module boundaries (ADR-001, ADR-005), and the old per-module boundary test only guarded one direction for one module.

## Solution
- 1g1: `src/test/java/com/wealthmax/support/architecture/ModuleBoundaryRules.java` and `src/test/java/com/wealthmax/ModuleBoundaryArchitectureTest.java`. A module is the first package segment under `com.wealthmax`, so a new module is covered with no new test. Four rules:
  1. no module depends on another module's `entity`, `repository`, `mapper` or `service.impl` package;
  2. `common` depends on no module;
  3. no dependency cycles between top-level packages;
  4. nothing depends on `com.wealthmax.adminPortal`.
  The real test imports `com.wealthmax` without test classes, so it needs no database.
- 1g2: fixtures `archfixture/good/{common/util/Strings, shop/service/ShopService, billing/service/impl/BillingServiceImpl}` and `ModuleBoundaryRulesTest`: the allowed shape (a module calling another's service interface and `common`) passes every rule.
- 1g3: fixtures `archfixture/bad/{billing/entity/Invoice, billing/service/BillingService, shop/service/ShopService, common/util/Helper}`: each rule must report its violation.

The fixtures live outside `com.wealthmax`, so the real test never sees them.

## Verification (real runs, 2026-11-05)
- 1g1: `ModuleBoundaryArchitectureTest` 4/4 pass. **This alone proves nothing**: the application has no modules yet, so every rule passes vacuously.
- 1g3 RED: with the bad assertions added and no bad fixtures, 5 of 8 self-tests failed.
- GREEN: `ModuleBoundaryRulesTest` 8/8 and `ModuleBoundaryArchitectureTest` 4/4 (12 total).
- **Mutation check.** I disabled the violation reporting in rules 1 and 2 on purpose: `ruleOneCatchesAnEntityLeak` and `ruleTwoCatchesCommonDependingOnAModule` failed. The file was restored (byte-identical, checked with `cmp`) and the 12 tests passed again.

## Rulings
None new.

## Limitations and follow-up
- Rules 3 and 4 use ArchUnit built-ins and were not mutation-tested. Their bad fixtures do fail them.
- The rules match package names. A class placed in the wrong package escapes them.
- `ArchUnit core` is used with a plain `@Test` (no ArchUnit JUnit engine), as decided in 1b1.
- A violation seen by the first real module should be fixed in the module, never by adding an exception to the rules.
