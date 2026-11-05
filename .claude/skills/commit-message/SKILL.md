---
name: commit-message
description: Generate a correctly formatted Conventional Commit message for the currently staged changes. Invoke whenever you need a ready-to-copy git commit subject for staged work in WM-Admin-Portal-BE.
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "1.0.0"
  domain: developer-productivity
  triggers: commit message, staged changes, git commit, what should I commit, commit this
  role: developer
  scope: git-history
  output-format: markdown
---

# Commit Message Generator — WM-Admin-Portal-BE

Produces a single, ready-to-copy commit message matching this repo's actual convention:
**Conventional Commits with a scope**, for example:

```
feat(careers): add status and work-style filters to the IN job list endpoint
refactor(applicant): move the cooling-period check into one database call
docs(careers): document the IN careers and applicant endpoints for the front end
```

This is **not** the bracket `[TAG] - Description` style some other internal projects use — don't
use that format here.

**Note:** the most recent commits in this repository (for example `Applicant IN: show months left
in the cooling-period error`) use a plain `<Module>: description` style rather than Conventional
Commits. This skill's format is the team's stated preference. If the team wants the plain style
instead, change this skill.

## Core Workflow

1. **Check for staged changes** — `git diff --cached --stat`. If empty, print `Nothing staged —
   stage your changes first.` and stop.
2. **Read the full diff** — `git diff --cached`. Understand what was added, modified, removed, or
   refactored.
3. **Pick the type** (see table below). When a diff spans multiple types, use the type of the most
   significant change — new feature code outweighs an incidental docs tweak.
4. **Pick the scope** — the module or feature area touched, lowercase, matching the package/folder
   name where the bulk of the change lives (`careers`, `applicant`, `auth`, `otp`, `docs`,
   `newsletter`, `leads`, `flyway`). If the change spans multiple top-level packages with no single feature
   tying them together, omit the scope rather than guessing one.
5. **Draft the subject** — imperative mood, ≤ 72 characters:
   ```
   type(scope): short description
   ```
   Start the description with a verb: *add*, *update*, *fix*, *remove*, *implement*, *restructure*.
6. **Output** — print only the commit message in a fenced code block. No prose, no explanation.

## Type Reference

| Type | Use when… |
|---|---|
| `feat` | New feature, endpoint, entity, or capability |
| `fix` | Bug fix — something was broken and is now corrected |
| `refactor` | Restructuring with no observable behaviour change (renames, package moves) |
| `docs` | Documentation-only changes (`docs/`, `plans/`, `.md` files, Javadoc) |
| `test` | Test-only changes — new tests or test fixes with no production code change |
| `chore` | Tooling, config, dependency bumps, CI/pipeline changes |
| `perf` | Performance improvement with no behaviour change |

**Plan/docs-only rule:** changes limited to `docs/plans/`, `plans/`, or `.md` files with no source
changes → `docs`. Docs changes alongside source changes → use the source change's type; the docs
update is incidental.

## Multi-Concern Diffs

When a diff covers two distinct concerns (e.g. a feature plus an unrelated cleanup), use the type
of the primary concern. Mention the secondary concern in the subject only if it still fits in 72
characters; otherwise name only the primary one.

## Constraints

### MUST DO
- Always run `git diff --cached` before generating anything — never guess from conversation context
- Always format as `type(scope): description` or `type: description` (scope optional, never
  invented when the change doesn't cleanly map to one module)
- Use imperative mood: *add*, *fix*, *implement* — not *added*, *fixes*, *implementing*
- Keep the subject line ≤ 72 characters
- Output only the commit message — no surrounding explanation

### MUST NOT DO
- Do not use the `[TAG] - Description` bracket style
- Do not add a body/footer unless the subject alone can't convey a genuinely multi-part change —
  and even then, keep it to a short bullet list, not prose
- Do not add `Co-Authored-By` or any other trailer — that's handled separately per this session's
  attribution settings, not by this skill
- Do not ask for confirmation — just output the message

## Output Format

~~~
```
type(scope): short description of what changed
```
~~~
