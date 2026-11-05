---
name: azure-pr
description: "Create or update an Azure DevOps pull request with a clean, informative title and a description with all merge-commit noise stripped out. Defaults to the current branch into Dev, which is where working branches merge; on Dev itself it drafts the Dev into Staging promotion. Pass any other pair like '/azure-pr dev-sarthak to sarthak.ctrl' to override. This project uses Azure DevOps, not GitHub, so never use gh or GitHub PR commands here. Runs on its own: it creates the PR, updates an existing one through the Azure DevOps MCP tools or the REST API with Git Credential Manager, or prints the description as a markdown fallback. The only question it ever asks is which branches to merge when a named branch cannot be verified."
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
  domain: developer-productivity
  triggers: create pr, raise pr, open pull request, azure pr, make a pull request, pr from one branch to another
  role: developer
  scope: git-history, azure-devops
  output-format: markdown
---
 
# Azure DevOps PR — WM-Admin-Portal-BE
 
Builds an Azure DevOps pull request whose title says what the PR actually does, and whose
description lists only real work. Merge commits (`Merged PR 1234: …`, `Merge branch 'main' into …`)
never appear in the output.
 
Default direction is **current branch → `Dev`**: that's where working branches merge. On `Dev`
itself, the default PR is the **`Dev` → `Staging`** promotion. `Stage` and `main` are stale, so
they are only ever targets when the user names one. The user can name any other pair.
 
**This project is hosted on Azure DevOps, not GitHub.** `origin` points at
the Azure DevOps repository for `WM-Admin-Portal-BE` (read it with `git remote get-url origin`). Never run `gh` or `gh pr create`.
PRs are created and updated either through the `mcp__azure-devops__*` tools or directly via the
Azure DevOps REST API using the stored Git credentials (`git credential fill`).
 
---
 
## Outcomes
 
This skill has three possible outcomes. It picks one autonomously:
 
1. **PR created or updated.** The PR was created or updated via the `mcp__azure-devops__*` tools
   or the Azure DevOps REST API using Git credentials. If an active PR already exists for the pair,
   it is updated in place. Print the PR number and URL.
2. **Markdown only.** Both MCP tools and the Azure DevOps REST API authentication/call failed.
   Print the drafted title and description as markdown so the user can paste it into a PR by hand,
   plus one line stating the reason.
3. **One question.** Only when a named branch cannot be verified against the remote. Use
   `AskUserQuestion` once to ask which two branches to merge, then continue.
 
Do not prompt for confirmation before creating or updating. Do not prompt to open a browser tab,
sign in, or supply a token. The single allowed question is outcome 3.
 
---
 
## Step 1 — Resolve Source and Target
 
Read the skill arguments (the text after `/azure-pr`).
 
| Argument form | Result |
|---|---|
| *(nothing)*, on a working branch | source = current branch (`git rev-parse --abbrev-ref HEAD`), target = `Dev` |
| *(nothing)*, while on `Dev` | source = `Dev`, target = `Staging` (the promotion) |
| `x to y`, `x -> y`, `x into y`, `from x to y`, `x y` | source = first, target = second |
| a single branch name that isn't an integration branch | source = that branch, target = `Dev` |
| a single integration branch name (`Dev`, `Staging`, `Stage`, `main`) | source = current branch, target = that branch |
 
Strip any `origin/` or `refs/heads/` prefix the user typed. Branch names are case-sensitive.
 
## Step 2 — Fetch and Verify Both Branches
 
```bash
git fetch origin <source> <target>
git rev-parse --verify origin/<source>
git rev-parse --verify origin/<target>
```
 
If either `rev-parse` fails, this is outcome 3: name the branch that could not be verified,
list the closest matches from `git branch -r`, and ask via `AskUserQuestion` which source and
target branches to merge. Use the answer, re-fetch and verify, then continue. This is the only
question the skill ever asks.
 
## Step 3 — Check for an Existing Active PR
 
Check if an active PR already exists for `refs/heads/<source>` -> `refs/heads/<target>`:
 
### Method A: Via MCP (if `mcp__azure-devops__*` is present)
Call `mcp__azure-devops__repo_pull_request` with:
- `action: "list"`
- `repositoryId` and `project` from Step 6
- `sourceRefName: "refs/heads/<source>"`
- `targetRefName: "refs/heads/<target>"`
- `status: "Active"`
 
### Method B: Via Azure DevOps REST API (if MCP is not connected)
Query the Azure DevOps REST API using Git credentials:
```powershell
$cred = "protocol=https`nhost=dev.azure.com`npath=<org>/<project>`n`n" | git credential fill
$token = ($cred | Select-String "password=(.*)").Matches.Groups[1].Value
$base64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(":$token"))
$headers = @{ Authorization = "Basic $base64" }
$url = "https://dev.azure.com/<org>/<project>/_apis/git/repositories/<repo>/pullrequests?searchCriteria.sourceRefName=refs/heads/<source>&searchCriteria.targetRefName=refs/heads/<target>&searchCriteria.status=active&api-version=7.1"
$activePr = (Invoke-RestMethod -Uri $url -Headers $headers -Method Get).value | Select-Object -First 1
```
 
If found, record its `pullRequestId`. Step 7 updates that PR in place instead of creating a duplicate.
 
## Step 4 — Collect the Real Commits
 
```bash
git log --no-merges --pretty=format:"%h|%an|%s" origin/<target>..origin/<source>
git diff --stat origin/<target>...origin/<source>
```
 
The three dots in `git diff` diff against the merge base, showing only what this PR brings in.
 
If `git log` returns nothing, stop with: `<target> is already up to date with <source> — nothing to merge.`
 
**Strip the noise:**
- Drop any commit subject matching:
  - `^Merged PR \d+:`
  - `^Merge branch`
  - `^Merge remote-tracking branch`
  - `^Merge pull request`
- If a `Merged PR 1234: <text>` commit is the *only* record of a change, keep `<text>` and drop the `Merged PR 1234:` prefix.
- De-duplicate near-identical subjects (e.g. feature commit and squash merge commit).
- Strip conventional commit prefixes (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, `perf:`) from description bullets so they read as plain English.
 
## Step 5 — Draft Title and Description
 
### Title
 
One line, ≤110 characters, imperative mood:
- **Feature branch PRs**: Describe the change directly (e.g., `Add Flyway baseline and move leads into a module`).
- **Branch-sync PRs** (e.g., `dev-sarthak` → `sarthak.ctrl`): `<source> → <target>: <theme 1>, <theme 2> + N more` or name up to 3 themes.
- No commit hashes, no PR numbers, no `feat:`/`fix:` prefixes. Never just "Merge x into y".
 
### Description
 
Group cleaned commits by module, derived from changed file paths:
 
| Path | Heading |
|---|---|
| `src/main/java/com/wealthmax/common/security/` | Security |
| `src/main/java/com/wealthmax/common/` | Common / Shared Infrastructure |
| `src/main/java/com/wealthmax/<module>/` | {Module} (`identity`, `careers`, `leads`, `content`, `reporting`, `notification`) |
| `src/main/resources/templates/` | Email Templates |
| `src/main/resources/db/migration/` | Database (Flyway migrations) |
| `src/test/` | Tests |
| `docs/`, `plans/` | Docs & Plans — one line, last section |
 
Format:
```markdown
> **Heads-up:** <one-line alert for any new Flyway migration, application*.yml key change, or modified API contract>
 
Brings <N> changes from `<source>` into `<target>`.
 
### <Module / theme>
- <what changed, one line, plain English>
 
### Docs & Plans
- <one line, only if there are doc-only changes>
```
 
Keep each bullet to one line. Max 4000 characters (Azure DevOps API limit).
 
## Step 6 — Resolve Repository and Project
 
Parse `git remote get-url origin`. For `https://[<org>@]dev.azure.com/<org>/<project>/_git/<repo>`:
- `<org>` = segment after `dev.azure.com/` (default: `wealthmax`)
- `<project>` = segment before `_git` (no default: always read it from the remote)
- `<repo>` = segment after `_git/` (default: `WM-Admin-Portal-BE`)
 
Parse dynamically from git remote rather than hardcoding.
 
## Step 6.5 — Determine Author and Reviewer
 
Before creating or updating, resolve who is making the PR:
 
```bash
git config user.email
```
 
Capture the result as `$authorEmail` (lowercase it for comparison).
 
**Reviewer rule (applies only when `target` is `Dev`):**
- If `$authorEmail` is **not** `ashish.m@wealthmax.co.uk`, add `ashish.m@wealthmax.co.uk` as a required reviewer.
- If `$authorEmail` **is** `ashish.m@wealthmax.co.uk`, do **not** add any auto-reviewer (a PR author cannot review their own PR).
 
This rule is enforced in both MCP and REST API paths below.
 
---
 
## Step 7 — Create or Update
 
### Method A: Via MCP (if `mcp__azure-devops__*` tools are connected)
 
First, resolve the Azure DevOps descriptor for `ashish.m@wealthmax.co.uk` if a reviewer needs to be added:
- Call `mcp__azure-devops__user_entitlement` (or equivalent user-lookup tool) to get the `id` (GUID) for `ashish.m@wealthmax.co.uk`. Store it as `$ashishId`.
 
- **If existing PR found**: Call `mcp__azure-devops__repo_pull_request_write` with `action: "update"`, `pullRequestId`, `title`, and `description`.
- **Otherwise**: Call `mcp__azure-devops__repo_pull_request_write` with `action: "create"`, `sourceRefName: "refs/heads/<source>"`, `targetRefName: "refs/heads/<target>"`, `title`, `description`, `isDraft: false`.
 
After the PR is created/updated, **if `target` is `Dev` and author is not Ashish**, add the reviewer via:
```
mcp__azure-devops__repo_pull_request_write  action: "add_reviewer"  pullRequestId: <id>  reviewerId: <$ashishId>  isRequired: true
```
(Skip the add_reviewer call if the MCP tool does not support it — fall through to the REST API reviewer step.)
 
### Method B: Via Azure DevOps REST API (if MCP tools are missing)
Run via PowerShell:
```powershell
$cred = "protocol=https`nhost=dev.azure.com`npath=<org>/<project>`n`n" | git credential fill
$token = ($cred | Select-String "password=(.*)").Matches.Groups[1].Value
$base64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(":$token"))
$headers = @{
    Authorization = "Basic $base64"
    "Content-Type" = "application/json"
}
 
# If updating existing PR:
$updateBody = @{ title = $title; description = $desc } | ConvertTo-Json -Depth 5
$pr = Invoke-RestMethod -Uri "https://dev.azure.com/<org>/<project>/_apis/git/repositories/<repo>/pullrequests/<pullRequestId>?api-version=7.1" -Headers $headers -Method Patch -Body $updateBody
 
# Check if reviewer should be added:
$authorEmail = (git config user.email).ToLower()
$reviewers = @()
$ashishId = "ed9ebfe6-3a8b-4d71-bfc3-d48ae0bf6a0c" # ashish.m@wealthmax.co.uk

if ($target -eq "Dev" -and $authorEmail -ne "ashish.m@wealthmax.co.uk") {
    $reviewers += @{ id = $ashishId; isRequired = $true }
}

# If creating new PR:
$createBody = @{
    sourceRefName = "refs/heads/<source>"
    targetRefName = "refs/heads/<target>"
    title = $title
    description = $desc
    reviewers = $reviewers
} | ConvertTo-Json -Depth 5
$pr = Invoke-RestMethod -Uri "https://dev.azure.com/<org>/<project>/_apis/git/repositories/<repo>/pullrequests?api-version=7.1" -Headers $headers -Method Post -Body $createBody
```
 
**On success (Outcome 1)** print:
```
PR #<id> created — <title>
https://dev.azure.com/<org>/<project>/_git/<repo>/pullrequest/<id>
```
(or `updated` for existing PR).
 
**On failure of both methods (Outcome 2)** print:
```
Could not <create/update> the PR automatically (<one-line reason>). Draft below — paste it into
<a new PR / PR #<id>> for refs/heads/<source> -> refs/heads/<target>.
 
## <title>
 
<description>
```
 
---
 
## Rules
 
### MUST DO
- Always `git fetch` first — stale local refs cause inaccurate commit lists.
- Always use `--no-merges` AND the subject-pattern filter.
- Always check for an existing active PR before creating; update in place if found.
- If MCP is absent, always try the Azure DevOps REST API with Git credentials before falling back to markdown.
- Base every bullet on a real commit or file change in the range.
- When `target` is `Dev`, always add `ashish.m@wealthmax.co.uk` as a required reviewer — **unless** the PR author's git email is `ashish.m@wealthmax.co.uk` (Azure DevOps does not allow an author to review their own PR).
 
### MUST NOT DO
- Do not prompt the user to confirm, sign in, open a browser, or provide a token.
- Do not put `Merged PR …` or `Merge branch …` noise in the title or description.
- Do not include commit hashes in the description.
- Do not create a duplicate PR if an active one already exists.
- Do not push, merge, complete, or abandon branches or PRs.
- Do not set the PR to auto-complete.
 
 
