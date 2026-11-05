---
name: daily-summary
description: Show a brief bulleted summary of yesterday's (or last workday's) work for the current developer, based on their git commits and their plans and patch summaries in plans/<DevName>-plan/. Run first thing to get a quick recap.
user-invocable: true
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "1.0.0"
  domain: developer-productivity
  triggers: daily-summary, what did we do yesterday, morning summary, yesterday's work, give me yesterday's work
  role: developer
  scope: git-history
  output-format: markdown
---

## Role & Context

Morning recap for WM-Admin-Portal-BE: pulls the last workday's git commits and matches them against the
developer's plans and patch summaries to produce a scannable bulleted summary. Plan titles and
`**Goal:**` lines are preferred over raw commit subjects, because they're more descriptive.

**Three developers work in this repo** (Ashish, Sarthak, Sishu), so filter to the current one:
- `<DevName>`: `currentUser` from `.claude/settings.local.json`, else `git config user.name`
  (the same rule as `writing-plans`).
- Author filter: the first name, case-insensitive. The same person appears as `ashish` (local
  commits) and `Ashish Manhas` (Azure DevOps merges), so `-i --author="ashish"` catches both.
- If the user asks for the whole team, drop the author filter and group by author.

## Core Workflow

### Step 1 — Determine the Date Range

| Today is… | Range to summarise |
|---|---|
| Monday, last Saturday was the 1st or 3rd of the month's Saturdays | Saturday only |
| Monday, last Saturday was the 2nd or 4th | Friday + Saturday + Sunday (long weekend) |
| Any other weekday | Yesterday only |

To find which Saturday it was: compute last Saturday's date, then `ceil(day-of-month / 7)` gives
the 1st/2nd/3rd/4th occurrence (day 8–14 = 2nd, day 22–28 = 4th).

### Step 2 — Fetch Commits

```bash
git log --all -i --author="<first name>" --format="%H %ai %s" --since="<date> 00:00" --until="<date> 23:59"
```

`--all` catches work on every branch, not just the current one. Run it per day in the range. If
no commits exist across the whole range, report `No commits found for <range>.` and stop.

### Step 3 — Find Plans and Patch Summaries for the Range

**Plans** live in `plans/<DevName>-plan/YYYY-MM-DD-<name>.md`. Take the date from the
**filename** (newer plans also have a `**Date:**` line, older ones don't):

```bash
find "plans/<DevName>-plan" -maxdepth 1 -name "<date>-*.md"
find docs/plans -maxdepth 1 -name "<date>-*.md" 2>/dev/null   # retired location, older plans only
```

**Patch summaries** live in `plans/<DevName>-plan/patch-*-summary.md` (some older ones sit at
`plans/patch-*.md`). Match them by the day the file was first added:

```bash
git log --format="%ad %h" --date=short --diff-filter=A -- "plans/<DevName>-plan/patch-*-summary.md" \
  | awk -v d="<date>" '$1==d'
```

An uncommitted summary written today won't show in `git log`. Also check the folder's
modification dates for files that aren't committed yet (`git status --short plans/`).

### Step 4 — Extract

For each matching plan: derive the title (strip date prefix + `.md`, kebab-case → Title Case) and
read its `**Goal:**` line. For each matching patch summary: use its top heading and opening
sentence.

### Step 5 — Correlate

Match commits to plans/summaries by keyword overlap between commit subject and filename/title. A
matched plan/summary supersedes listing its commit(s) individually.

### Step 6 — Render

Single day:
```
**Yesterday's Work — <YYYY-MM-DD>**

- **<Title>** — <Goal line or one-line summary>
- **<Title>** — <...>
```

Multi-day (Monday long weekend):
```
**Work Summary — <start> to <end>**

**<YYYY-MM-DD>**
- **<Title>** — <...>

**<YYYY-MM-DD>**
- **<Title>** — <...>
```

## Formatting Rules

| Rule | Detail |
|---|---|
| Plan/summary over commit | When one exists, use its title + Goal/summary line, not the raw commit subject |
| No matching plan | Use the commit subject; strip Conventional Commit type prefixes (`feat:`, `fix:`, `refactor:` — see `commit-message`) but keep the rest |
| Deduplication | Multiple commits mapping to the same plan/summary → list it once |
| One line per bullet | No sub-bullets, no wrapping |
| Merge commits | Omit unless the subject itself is descriptive (e.g. a meaningful `Merged PR NNNN: ...` title) |
| Order | Chronological, earliest first |

## Constraints

### MUST DO
- Run the git log command before reading any plan/summary files
- Filter commits to the current developer (case-insensitive first name) unless asked for the team
- Derive plan dates from the **filename**
- Search `plans/<DevName>-plan/` first, then the retired `docs/plans/`
- On Mondays, compute which Saturday it was before deciding the range

### MUST NOT DO
- Do not fabricate a commit subject or plan/summary title
- Do not include sub-bullets or multi-line descriptions per item
- Do not list a merge commit with no descriptive subject
- Do not assume Sunday is a work-off on a 2nd/4th-Saturday weekend — include it in those cases
