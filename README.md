# WM Admin Portal BE

Back end for the Wealthmax admin portal and its public website forms (careers, leads, referrals,
newsletter). A single-tenant modular monolith: Java 21, Spring Boot 3.5, PostgreSQL, Flyway.

It replaces the old `Wealthmax-Admin-Portal-BE` repository. The plan is in
`docs/revamp-audit-report.md`.

# Getting Started

1. Install JDK 21 and Maven 3.9+, and run a local PostgreSQL with an **empty** database.
2. Copy `src/main/resources/application.yml.example` to `application-local.yml` (gitignored) and
   fill in your local values.
3. Run `mvn spring-boot:run`. The `local` profile is the default, and Flyway builds the schema on
   the first start.

# Build and Test

```bash
mvn compile
mvn test                                         # database tests need local PostgreSQL
mvn test -Dtest=ModuleBoundaryArchitectureTest   # module boundaries, no database (once written)
```

# Contribute

Read `CLAUDE.md` first. Plans go in `plans/<DevName>-plan/`. Branch flow: feature branch, then
`Dev`, then `Staging`. Set up the pre-commit check once per clone (see CLAUDE.md, Development
Workflow).
