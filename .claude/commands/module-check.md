---
name: module-check
argument-hint: "[module or file path] e.g. careers, or src/main/java/com/wealthmax/leads/service/impl/LeadServiceImpl.java"
---

Verify module boundaries and data ownership in: $ARGUMENTS

Load the `modular-monolith` skill first. This application is single tenant, so there is no tenant
isolation to check: the boundaries that matter are between **modules**.

Step 1: Run the boundary test (no database needed)
```bash
mvn test -Dtest=ModuleBoundaryArchitectureTest > target/archunit.log 2>&1; echo "exit=$?"
grep -E "Tests run:|Architecture Violation|was violated" target/archunit.log | head -20
```

Step 2: Read the code
- Every Java file under `src/main/java/com/wealthmax/<module>/` (or the file given).
- Every Flyway migration that names the module.

Step 3: Check imports
- No import of another module's `entity`, `repository`, `mapper` or `service.impl` package.
  Allowed: another module's `service` interfaces, `dto`, enums and `event` classes.
- No import from the old repository (`com.wealthmax.adminPortal`).
- If the module is `common`: no import from any feature module.
```bash
grep -rnE "^import (static )?com\.wealthmax\." src/main/java/com/wealthmax/$ARGUMENTS | grep -vE "com\.wealthmax\.($ARGUMENTS|common)\."
```
Read every line that prints and classify it as allowed or a violation.

Step 4: Check data ownership
- Every table the module's entities map to is created by a migration that names this module.
- No JPA relationship (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`) to another module's
  entity. Cross-module references are `Long` ids.
- No JPQL or native query that joins or names another module's table.
- No migration that alters another module's table, and no foreign key to one.

Step 5: Check layering
- Controllers depend on service interfaces only, never on a repository or entity.
- Controllers return response DTOs, never entities.
- Vendor SDKs stay at the edge: Azure Blob in `common.storage`, Mailgun in `notification`, Apache
  POI in `reporting`, the JWT library in `common.security`.

Step 6: Check that nothing tenant-shaped crept in
```bash
grep -rniE "tenant|TenantContext|tenant_id" src/main/java/com/wealthmax/$ARGUMENTS src/main/resources/db/migration
```
This must print nothing.

## Output format

ARCHUNIT:
  [tests run, failures, or "not run" and why]

VIOLATIONS (fix before committing):
  [file:line] | [rule broken] | [fix: which service interface, event or common promotion to use]

OWNERSHIP:
  [table] | owned by [module] | [ok / problem]

CLEAN:
  [what was checked and found correct]
