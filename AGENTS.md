# Antigravity Agent Guidelines

This project inherits all developer rules, stack constraints, and workflows from Claude Code configuration:

- Core Project Brain & Stack Guidelines: [CLAUDE.md](CLAUDE.md)
- Common Module Guidelines: [.claude/rules/common.md](.claude/rules/common.md)
- Database & Migration Rules: [.claude/rules/database.md](.claude/rules/database.md)
- Skills (coding standards, modular monolith, Flyway, testing): [.claude/skills/](.claude/skills/)

Always follow the architecture, module boundaries, and coding standards defined in these documents.
This application is a **single-tenant** modular monolith: never add a tenant column, tenant context
or tenant filter, and never import code from the old repository (`com.wealthmax.adminPortal`).
