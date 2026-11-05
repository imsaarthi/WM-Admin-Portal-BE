---
name: brainstorming
description: Use before building any new feature, component or behaviour change in WM-Admin-Portal-BE, when the idea isn't yet a design. Classifies the request (spike, bounded, architectural), clarifies intent one question at a time, proposes approaches with trade-offs, checks WM-Admin-Portal-BE's non-functional requirements, and gets approval before any code or plan.
user-invocable: true
---

# Brainstorming: WM-Admin-Portal-BE

Based on `brainstorming` from obra/superpowers (MIT License, Copyright (c) 2025 Jesse Vincent),
rewritten for this project.

Turn an idea into a design the user recognises and approves, before anyone writes code or a plan.

The application is a **single-tenant modular monolith** (Java 21, Spring Boot 3.4+, Flyway). Designs
are about modules, access levels and data ownership. There is no tenant dimension.

## Step 1: Classify, Out Loud

Say which path this is, so the user can overrule you:

- **Spike:** a feasibility question ("can we...?", "is it possible to...?"). The output is an
  answer, not code to keep. Describe the question and the probe in 2 or 3 sentences, get a nod,
  investigate cheaply, report a recommendation. Anything built is throwaway.
- **Bounded:** a small change to a flow that already exists in this repo (a new filter on an
  existing list endpoint, one more field on an applicant form). Ask the questions that matter,
  present a short design in chat (approach, files, tests), and **stop until the user says yes**.
- **Architectural:** a new module, a new cross-module flow, a new external integration, a schema
  change that other code depends on, or a change to interfaces others depend on. Full process
  below, ending with a written design and then `writing-plans`.

Unsure between two? Take the heavier one. If hidden complexity shows up mid-task, stop, say so, and
move up a path. Never down.

**The gate:** no code, scaffolding, dependency or plan until the user has approved the design for
the chosen path. Approving the idea isn't approving a design that doesn't exist yet. Reading the
codebase is always allowed.

## Step 2: Understand the Intent

1. Look at the current state first: the module, its entities and services, recent commits, and
   any plan or doc on the topic (`plans/*/`, `docs/`).
2. If the request describes several independent pieces, say so and split it. Each piece gets its
   own design, plan and patches.
3. Ask **one question per message**, multiple choice where possible. Focus on purpose, who uses
   it (website visitors? portal admins? HR? the leads team?), constraints, and what success looks like.
4. Write back your understanding in a few lines: the goal, the constraints, the success criteria.
   Separate what the user said from what you assumed. Let them correct it.

## Step 3: Propose Approaches (architectural)

- Offer 2 or 3 approaches with their trade-offs. Lead with your recommendation and why.
- Cut features nobody asked for (YAGNI).
- Follow existing patterns (the first ported module; until then, the old repository's `careers_in`). Improve code the work touches
  when it's in the way. Don't propose unrelated refactors.

## Step 4: Check the WM-Admin-Portal-BE Non-Functional Requirements

Every design answers these. Skip one only by saying why it doesn't apply.

| Area | Question | Where the rules are |
|---|---|---|
| Module placement | Which module owns it, or is it a new module? What does it need from other modules, and through which service interface or event? | `modular-monolith`, `architecture-designer` |
| Data ownership | Which tables does the module own? Does anything read or write another module's tables, or join across modules? Any `@Scheduled` work? | `modular-monolith` |
| Access | Public, Authenticated or Admin for each route? Which `SecurityConfiguration` rule, and does it sit above any wildcard that would also match? Any record-level ownership check? | `api-designer` (Access Rules) |
| Audit | Which actions need an audit trail (reads or deletes of personal data, role changes)? | `compliance-reviewer` (agent), audit report SEC-29 |
| Personal and financial data | What personal data is stored, and why? Masked in logs? How long is it kept, and can it be erased? | `compliance-reviewer` (agent) |
| Money | Any amounts? `BigDecimal` in Java, `NUMERIC(14,2)` in SQL. | `coding-standards` |
| API | Routes, request and response DTOs, pagination (`PageResponseDto`), error cases, front-end doc changes. | `api-designer`, `api-contract` |
| Schema | New tables or columns? A Flyway migration, safe on a table with data (locking, backfills)? | `flyway-migration` |
| Failure modes | What happens when Mailgun or Azure Blob fails, a request is retried, or two users act at once? | `review-branch` (sharp edges) |
| Performance | Expected volumes, indexes, N+1 risks, list sizes. | `postgres-performance` |
| Stack | Does it need Java 21-only syntax, or a dependency the Boot 3.4+ target doesn't manage? | `coding-standards`, `dependency-upgrade` |
| Testing | Which database, access-rule and module-boundary tests prove it works? | `test-driven-development`, `integration-testing` |

## Step 5: Present the Design

- In sections sized to their complexity: a few sentences where it's simple, up to a few short
  paragraphs where it's subtle. Ask after each section whether it's right so far.
- Cover components, data flow, API, data model, the table above, and how it'll be tested.
- Design small units with one purpose each, clear interfaces, testable on their own.

## Step 6: After Approval

- **Bounded:** implement through the normal workflow (`test-driven-development`;
  `writing-plans` still requires a written plan once a change touches 3 or more files).
- **Architectural:** save the approved design as
  `plans/<DevName>-plan/YYYY-MM-DD-<topic>-design.md` (same folder rule as plans; `<DevName>` from
  `currentUser` in `.claude/settings.local.json`, else `git config user.name`). Re-read it once for
  placeholders, contradictions, scope and ambiguity, and fix what you find. Ask the user to review
  the file. After their approval, the next and only step is `writing-plans`. A decision that
  changes a module boundary or adds a dependency also gets an ADR (`architecture-designer`).
- **Spike:** report the answer as a recommendation.

Don't commit anything unless the user asks.

## Red Flags

| Thought | Reality |
|---|---|
| "Too simple to need a design" | Bounded work still gets a short design and a yes. |
| "I'll call it bounded and skip the design doc" | Reaching for the lighter label to skip work is the doubt. Take the heavier path. |
| "The design's obvious, I'll start while they read it" | The gate is the approval, not the design's length. |
| "It's one more endpoint on the existing controller" | A new capability is a new module. Check placement first (`modular-monolith`). |
| "Another module's repository is right there" | Use its service interface. A repository import fails the boundary test. |
| "We'll add the access rule later" | A route with no explicit rule lands on whichever wildcard matches first, often `permitAll`. Decide now. |
| "Hibernate will create the table" | Flyway owns the schema. The migration is the first task. |
| "The spike works, I'll keep the code" | Keeping it is a new request. Classify it. |
