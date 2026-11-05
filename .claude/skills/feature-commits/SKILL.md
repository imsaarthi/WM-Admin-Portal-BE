---
name: feature-commits
description: Collect all commits for a given module, feature, or task and produce a copy-paste-ready Azure Boards work item description plus per-commit comments. Invoke with a module/feature name, e.g. "/feature-commits careers IN" or "/feature-commits applicant cooling period".
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "1.0.0"
  domain: developer-productivity
  triggers: feature-commits, commits for feature, commits for module, azure description for feature, commit comments for, give me all commits for
  role: developer
  scope: git-history
  output-format: markdown
---

## Role & Context

Given a **module, feature, or task name** (e.g. `careers IN`, `applicant cooling period`, `flyway
adoption`), find every commit that belongs to it, confirm relevance against the files each
commit actually touched (not just a keyword match), and produce:

- A clean table of the matching commits
- One **Azure Boards work item description** summarising the whole feature
- **Per-commit comments** ready to paste against each commit/PR

## Core Workflow

### Step 1 — Resolve Inputs

- **Feature/task name:** the text after the invocation. If none given, ask for it and stop.
- **Author scope:** three developers work in this repo, and a feature often spans more than one.
  By default include every author and show who wrote each commit. If the user asks for their own
  commits only, add `-i --author="<first name>"` (case-insensitive, because `ashish` locally and
  `Ashish Manhas` on Azure DevOps merges are the same person).
- Derive 2–4 search keywords from the feature name, preferring the most distinctive stem (e.g.
  `cooling` over the generic `applicant`) to avoid noisy matches.

### Step 2 — Find Candidate Commits

```bash
git log --all --regexp-ignore-case --pretty=format:"%h|%ad|%an|%s" --date=short \
  --grep="<keyword1>" --grep="<keyword2>"
```

`--grep` terms are OR-combined. If the most distinctive keyword alone returns too much noise,
re-run with just that one term.

### Step 3 — Confirm Relevance by Changed Files

For each candidate:

```bash
git show --stat --oneline <hash> | tail -n +2
```

- **Include** commits whose subject and/or changed files clearly belong to the feature (files
  under its package, e.g. `careers/`, or entities/migrations named after it).
- **Flag as partial** (`*`) commits that touch the feature only incidentally inside a broader
  change — list separately, let the user decide whether to include them.
- **Exclude** keyword matches whose files are unrelated.
- `Merged PR NNNN: ...` commits (this repo's Azure DevOps squash-merge pattern — see `azure-pr`):
  keep only to map work to PR numbers; base the actual comments on the underlying authored commits,
  not the merge commit's own subject.

### Step 4 — Optional Deep Check

If keyword matching seems to miss work, also search by file path:

```bash
git log --all --pretty=format:"%h|%ad|%s" --date=short -- "src/main/java/com/wealthmax/<package>/**"
```

### Step 5 — Read Commit Bodies

```bash
git show -s --format="===%n%h | %ad%n%s%n%b" --date=short <hash1> <hash2> ...
```

### Step 6 — Render the Report

Keep comments factual — describe what the commit actually changed (entities, services, migrations,
endpoints, DTOs) based on its subject, body, and changed files. Don't invent scope.

## Output Format

````markdown
## <Feature name> commits

| # | Hash | Date | Author | Subject |
|---|------|------|--------|---------|
| 1 | `<hash>` | YYYY-MM-DD | <author> | <subject> |
| 2* | `<hash>` | YYYY-MM-DD | <author> | <subject> (partial: touches the feature only incidentally) |

\* Partial commits — include only if you want them tracked under this feature.

These map to PR **#<n>** (when a `Merged PR NNNN:` commit exists in the range).

---

## Azure Boards — Work Item Description

> **Title:** <Feature name>
>
> **Description:** <one paragraph — what was built, the key entities/services/flows introduced,
> migrations added, endpoints exposed, and any notable behaviour, written so a reviewer understands
> the whole change without reading the diff>

---

## Commit-wise comments

**1. `<hash>`**
> <factual one-to-three-sentence description grounded in its actual files>

**2. `<hash>`** *(optional / partial)*
> <...>
````

End with a short offer to run the Step 4 deep check if it wasn't already run.

## Rules

- **Verify, don't assume.** A keyword match is a candidate, not a confirmed inclusion — always
  check the changed files (Step 3) before including a commit.
- **Be honest about partials** — never fold an incidental commit silently into the feature's scope.
- **No invented scope** — comments reflect what the commit's body and file stat actually show.
- **Stay copy-paste ready** — the Azure Boards description and commit comments must be usable
  verbatim, no placeholders left unfilled.
