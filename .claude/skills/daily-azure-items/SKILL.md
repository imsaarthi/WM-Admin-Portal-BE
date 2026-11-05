---
name: daily-azure-items
description: Generate a list of Azure Boards work items for today (or a date range) for the current developer, by reading their plans and patch summaries in plans/<DevName>-plan/ and correlating them with their git commits. Outputs a grouped, copy-paste-ready work item table per plan/patch.
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "1.0.0"
  domain: developer-productivity
  triggers: daily-azure-items, give me azure work items, azure work items today, generate azure items, what work items do we have today
  role: developer
  scope: git-history
  output-format: markdown
---

## Role & Context

Azure Boards work item generator for WM-Admin-Portal-BE.

- **Plans** live per developer at `plans/<DevName>-plan/YYYY-MM-DD-<name>.md` (per
  `writing-plans`), with a `**Goal:**` line. Older plans sit in the retired `docs/plans/`.
- **Patch summaries** live at `plans/<DevName>-plan/patch-{N}{letter}-{feature}-summary.md` (per
  `executing-plans`). Some older ones sit at `plans/patch-*.md`.
- Plans carry no pre-filled "Azure Boards Work Items" table, so this skill **derives** each work
  item from the plan's `**Goal:**` or the summary's heading, plus its matched commits.

**Three developers work in this repo** (Ashish, Sarthak, Sishu). Default to the current one:
- `<DevName>`: `currentUser` from `.claude/settings.local.json`, else `git config user.name`.
- Author filter: the first name, case-insensitive (`ashish` locally and `Ashish Manhas` on Azure
  DevOps merges are the same person): `-i --author="<first name>"`.

## Core Workflow

### Step 1 — Determine the Date Range

Default: **today**. Accepts an explicit date (`YYYY-MM-DD`) or range
(`YYYY-MM-DD to YYYY-MM-DD`) as an argument.

### Step 2 — Fetch Commits for the Range

```bash
git log --all -i --author="<first name>" --format="%h %ad %s" --date=short --since="<start> 00:00" --until="<end> 23:59"
```

Omit bare merge commits (`Merge branch ...`, `Merge pull request ...` with no feature description).
Keep `Merged PR NNNN: <description>` — this repo's Azure DevOps squash-merge pattern carries real
information.

### Step 3 — Find Plans and Patch Summaries for the Range

**Plans**: the date is in the **filename**:

```bash
find "plans/<DevName>-plan" -maxdepth 1 -name "<date>-*.md"
find docs/plans -maxdepth 1 -name "<date>-*.md" 2>/dev/null   # retired location
```

**Patch summaries** carry no date in the filename. Match them by the day they were first added:

```bash
git log --format="%ad %h" --date=short --diff-filter=A -- "plans/<DevName>-plan/patch-*-summary.md" \
  | awk -v d="<date>" '$1==d'
```

A summary is written once implementation is done, so its add date is the meaningful one. Summaries
written today and not yet committed won't appear in `git log`: check `git status --short plans/`
as well.

### Step 4 — Derive the Work Item from Each Plan/Summary

For each matching file:

1. **Title** — for a plan: strip the `YYYY-MM-DD-` prefix and `.md`, convert kebab-case to Title
   Case (e.g. `2026-05-06-user-management-v2-um-a.md` → `User Management V2 Um A`, tidy obvious
   acronyms by hand). For a patch summary: use its own top heading.
2. **Description** — for a plan: use the `**Goal:**` line verbatim. For a patch summary: use its
   "Problem"/"Solution" opening, condensed to one paragraph.
3. **Matched commits** — correlate by keyword overlap between the commit subject and the
   plan/summary filename or title. One commit matches at most one plan/summary (first match wins).

If a plan has neither a `**Goal:**` line nor any matched commit, still list it — title only — under
a trailing "Plans without a clear work item" note rather than silently dropping it.

### Step 5 — Render Output

```
## Azure Work Items — <date> [to <date>]

---

### <N>. <Title>
*Commits: `<hash>` — <subject>[, `<hash>` — <subject>]*

**Description:** <the Goal line or condensed summary, as a single ready-to-paste work item description>

---

### Additional Commits (no matching plan/summary)

| Commit | Description |
|---|---|
| `<hash>` | <subject, feat:/fix:/refactor: prefix stripped per the commit-message convention> |

---

### Plans Without a Clear Work Item
- <title>
```

**Rules:**

| Rule | Detail |
|---|---|
| Order | Chronological by plan/summary date, then filename |
| No commits matched | Still list the plan/summary with its Goal-derived description — a work item doesn't need a commit to exist |
| Empty sections | Omit "Additional Commits" / "Plans Without a Clear Work Item" entirely if empty |
| Multi-day range | Prefix each heading with its date: `### 1. (2026-05-06) User Management V2` |

## Constraints

### MUST DO
- Run the `git log` command before reading any plan/summary files
- Filter to the current developer (case-insensitive first name) unless asked for the team
- Derive the plan date from its **filename** (older plans have no `**Date:**` line)
- Search `plans/<DevName>-plan/` first, then the retired `docs/plans/`
- Preserve the `**Goal:**` text exactly when using it as a description

### MUST NOT DO
- Do not fabricate a work item title or description when neither a Goal line nor a summary heading
  exists — say so instead (see "Plans Without a Clear Work Item")
- Do not invent commit associations that aren't keyword-supported
- Do not include bare merge commits with no feature description
