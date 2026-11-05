---
name: azure-boards
description: >
  Manipulate Azure Boards work items (get, create, update, state transition, comment,
  link, assign, query). Authenticates via Azure CLI Bearer token (az account get-access-token)
  — the same pattern used by the boards-sync/azure_client.py script in the older project.
  Falls back to MCP tools when available. Never asks for a PAT or browser login.
  The only question it ever asks is when required fields are genuinely unknown.
license: MIT
metadata:
  author: WM-Admin-Portal-BE
  version: "2.0.0"
  domain: developer-productivity
  triggers: >
    boards ticket, create work item, update ticket, update work item, add comment to ticket,
    transition work item, link ticket to pr, query boards, azure boards, work item, bug ticket,
    user story, task ticket, close ticket, reopen ticket, assign ticket, close work item
  role: developer
  scope: azure-devops, azure-boards
  output-format: markdown
---

# Azure Boards — WM-Admin-Portal-BE

Read and manipulate Azure Boards work items for this repository's Azure DevOps project (read from `git remote get-url origin`).

## Authentication (in priority order)

### Method A — MCP (preferred when tools are connected)
If `mcp__azure-devops__*` tools are available, use them directly for read operations.
For write operations (create, update, state, comment, link) always prefer Method B — the
MCP write tools for Boards are intentionally limited in scope.

### Method B — Azure CLI Bearer Token (primary write path)
This is the same pattern used by `scripts/boards-sync/azure_client.py` in the older project.

**Prerequisite check — run this first:**
```powershell
az account show --output json 2>&1 | ConvertFrom-Json | Select-Object name, user
```
If `az` is not found, stop and tell the user:
> Azure CLI is not installed. Install it with:
> `winget install Microsoft.AzureCLI`
> Then run `az login` once. After that, all Board operations work without any PAT.

**Getting the Bearer token:**
```powershell
# Azure DevOps resource ID (fixed, same for all orgs)
$AZURE_DEVOPS_RESOURCE = "499b84ac-1321-427f-aa17-267ca6975798"

$tokenJson = az account get-access-token --resource $AZURE_DEVOPS_RESOURCE --output json
$token     = ($tokenJson | ConvertFrom-Json).accessToken
$headers   = @{
    Authorization  = "Bearer $token"
    "Content-Type" = "application/json"
}
```

### Method C — Git Credential Manager (read-only fallback)
Only use for GET/QUERY if `az` is unavailable. GCM on this machine stores an OAuth token
that works for Git operations but returns 401 on the WIT REST API for write operations.

```powershell
$credInput = "protocol=https`nhost=dev.azure.com`npath=$org/$project/_git/$repo`n`n"
$cred      = $credInput | git credential fill
$token     = ($cred | Select-String "password=(.*)").Matches.Groups[1].Value.Trim()
$base64    = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(":$token"))
$headers   = @{ Authorization = "Basic $base64" }
```

> **Note:** If the machine stores a PAT in GCM (not an OAuth token), Method C also works
> for writes. Token length < 60 chars = PAT (works for REST). Length ~84 chars = OAuth token
> (Git only, 401 on REST API).

---

## Step 1 — Parse the Command

Read the text after `/azure-boards`. Map it to an operation:

| Command form | Action |
|---|---|
| `get <id>` | Fetch and display a work item |
| `create <type> "<title>"` | Create a new work item |
| `update <id> <field> "<value>"` | Update a single field |
| `state <id> <new-state>` | Transition the state of a work item |
| `comment <id> "<text>"` | Add a comment / discussion entry |
| `link <id> pr <pr-id>` | Link a work item to a PR |
| `link <id> relates-to <id2>` | Link two work items (Relates To) |
| `link <id> parent <id2>` | Set parent of a work item |
| `assign <id> <email>` | Assign a work item to a user |
| `query "<WIQL or plain English>"` | Run a WIQL query |
| *(nothing / ambiguous)* | Ask one clarifying question |

Aliases: `ticket`, `issue`, `item` are synonyms for "work item".
Natural language inference: "close ticket 1234" → `state 1234 Closed`, "add note to 456" → `comment 456 …`

If the operation requires a value the user has not supplied, ask **one** clarifying question.
That is the only question this skill ever asks.

---

## Step 2 — Resolve Organisation and Project

```powershell
$remote  = git remote get-url origin
# Parses: https://[org@]dev.azure.com/org/project/_git/repo
$org     = ($remote -replace 'https://.*?dev\.azure\.com/([^/]+)/.*', '$1')   # wealthmax
$project = ($remote -replace 'https://.*?dev\.azure\.com/[^/]+/([^/]+)/.*', '$1')  # the project name
$repo    = ($remote -replace '.*/([^/]+)$', '$1')                              # WM-Admin-Portal-BE
```

Parse dynamically from `git remote get-url origin`. Do not hardcode.

Base URL for all Boards REST calls:
```
https://dev.azure.com/$org/$project/_apis/wit/
```
API version: **`7.1`** throughout.

---

## Step 3 — Execute the Operation

### GET — Fetch a Work Item

**Method A (MCP):**
```
mcp__azure-devops__wit_get_work_item(id: <id>, project: <project>)
```

**Method B (REST):**
```powershell
$wi = Invoke-RestMethod `
    -Uri     "https://dev.azure.com/$org/$project/_apis/wit/workitems/$id`?`$expand=all&api-version=7.1" `
    -Headers $headers `
    -Method  Get
```

Display: ID, Type, Title, State, Assigned To, Area Path, Iteration Path, Description, Tags, Relations.

---

### CREATE — New Work Item

Valid types: `Bug`, `User Story`, `Task`, `Feature`, `Epic`, `Issue`.
Required: `System.Title`. Optional: `System.Description`, `System.AssignedTo`, `System.AreaPath`, `System.Tags`, `Microsoft.VSTS.Scheduling.StoryPoints`.

**Method B (REST):** POST with `application/json-patch+json` content type:
```powershell
$type = [uri]::EscapeDataString($workItemType)   # e.g. "User%20Story"
$body = @(
    @{ op = "add"; path = "/fields/System.Title";       value = $title }
    @{ op = "add"; path = "/fields/System.Description"; value = $description }  # if provided
    @{ op = "add"; path = "/fields/System.AssignedTo";  value = $assignedTo }   # if provided
    @{ op = "add"; path = "/fields/System.Tags";        value = $tags }         # if provided
) | ConvertTo-Json -Depth 5

$wi = Invoke-RestMethod `
    -Uri         "https://dev.azure.com/$org/$project/_apis/wit/workitems/`$$type`?api-version=7.1" `
    -Headers     $headers `
    -Method      Post `
    -Body        $body `
    -ContentType "application/json-patch+json"
```

---

### UPDATE — Change a Field

Field reference name mapping:

| User input | Field reference name |
|---|---|
| title | `System.Title` |
| description / desc | `System.Description` |
| assigned to / assignee | `System.AssignedTo` |
| story points / points | `Microsoft.VSTS.Scheduling.StoryPoints` |
| priority | `Microsoft.VSTS.Common.Priority` |
| tags | `System.Tags` |
| area path / area | `System.AreaPath` |
| iteration / sprint | `System.IterationPath` |
| acceptance criteria | `Microsoft.VSTS.Common.AcceptanceCriteria` |
| state | `System.State` |

**Method B (REST):**
```powershell
$body = '[{"op":"replace","path":"/fields/<FieldReferenceName>","value":"<value>"}]'

$wi = Invoke-RestMethod `
    -Uri         "https://dev.azure.com/$org/$project/_apis/wit/workitems/$id`?api-version=7.1" `
    -Headers     $headers `
    -Method      Patch `
    -Body        $body `
    -ContentType "application/json-patch+json"
```

---

### STATE — Transition Work Item State

Always GET the item first to confirm its type, then determine the correct closed-state value:

| Type | Closed state | Other valid states |
|---|---|---|
| Bug | `Closed` | New, Active, Resolved |
| User Story | `Closed` | New, Active, Resolved |
| Task | `Done` | To Do, In Progress |
| Feature | `Closed` | New, In Progress, Done |
| Epic | `Closed` | New, In Progress, Done |
| Issue | `Closed` | Active |

Use the UPDATE flow targeting `System.State`.

---

### COMMENT — Add a Discussion Entry

**Method B (REST):**
```powershell
$body = '{"text":"<comment text>"}'

Invoke-RestMethod `
    -Uri         "https://dev.azure.com/$org/$project/_apis/wit/workitems/$id/comments`?api-version=7.1-preview.3" `
    -Headers     $headers `
    -Method      Post `
    -Body        $body `
    -ContentType "application/json"
```

---

### LINK — Add a Relation

| User intent | `rel` value |
|---|---|
| link to PR | `ArtifactLink` |
| relates to | `System.LinkTypes.Related` |
| parent | `System.LinkTypes.Hierarchy-Reverse` |
| child | `System.LinkTypes.Hierarchy-Forward` |
| duplicate of | `System.LinkTypes.Duplicate-Forward` |
| blocks | `Microsoft.VSTS.Common.Blocks-Forward` |

**Linking to a PR** — build the artifact URI:
```powershell
# Get repo GUID first
$repoInfo = Invoke-RestMethod -Uri "https://dev.azure.com/$org/$project/_apis/git/repositories/$repo`?api-version=7.1" -Headers $headers -Method Get
$repoId   = $repoInfo.id

$artifactUri = "vstfs:///Git/PullRequestId/$org/$project/$repoId/$prId"
```

**PATCH body for all link types:**
```powershell
$body = @(
    @{
        op    = "add"
        path  = "/relations/-"
        value = @{
            rel        = $relTypeName
            url        = $url        # work item URL or $artifactUri for PRs
            attributes = @{ comment = "" }
        }
    }
) | ConvertTo-Json -Depth 6

Invoke-RestMethod `
    -Uri         "https://dev.azure.com/$org/$project/_apis/wit/workitems/$id`?api-version=7.1" `
    -Headers     $headers `
    -Method      Patch `
    -Body        $body `
    -ContentType "application/json-patch+json"
```

---

### ASSIGN — Set Assigned To

Look up the identity first:
```powershell
$search   = Invoke-RestMethod -Uri "https://vssps.dev.azure.com/$org/_apis/identities`?searchFilter=MailAddress&filterValue=$email&api-version=7.1" -Headers $headers -Method Get
$identity = $search.value | Select-Object -First 1
```

Then UPDATE `System.AssignedTo` with the resolved display name or email.

---

### QUERY — WIQL or Plain-English Search

Translate plain English to WIQL if needed. Common pattern:
```sql
SELECT [System.Id], [System.Title], [System.State], [System.AssignedTo], [System.WorkItemType]
FROM WorkItems
WHERE [System.TeamProject] = '<project>'
  AND [System.WorkItemType] IN ('Bug', 'User Story', 'Task')
  AND [System.State] NOT IN ('Closed', 'Done', 'Resolved')
  AND [System.IterationPath] = @CurrentIteration
  AND [System.AssignedTo] = @Me
ORDER BY [System.ChangedDate] DESC
```

**Method B (REST):**
```powershell
$body    = '{"query":"<WIQL string>"}'
$result  = Invoke-RestMethod -Uri "https://dev.azure.com/$org/$project/_apis/wit/wiql`?api-version=7.1" -Headers $headers -Method Post -Body $body -ContentType "application/json"

# Batch-fetch details for up to 50 results
$ids     = ($result.workItems | Select-Object -First 50).id -join ","
$details = Invoke-RestMethod -Uri "https://dev.azure.com/$org/$project/_apis/wit/workitems`?ids=$ids&api-version=7.1" -Headers $headers -Method Get
```

Display as markdown table: `ID | Type | Title | State | Assigned To`.

---

## Step 4 — Output

**Success:**
```
✅ Work Item #<id> — <title>
State: <new-state> | Type: <type> | Assigned: <assignee>
https://dev.azure.com/<org>/<project>/_workitems/edit/<id>
```

**Az CLI not installed:**
```
❌ Azure CLI not found. Install it to enable Boards write operations:

    winget install Microsoft.AzureCLI
    az login

After login, re-run this command.
```

**Other failure:**
```
❌ Could not complete the operation (<one-line reason>).
Manual: https://dev.azure.com/<org>/<project>/_workitems/edit/<id>
```

---

## Rules

### MUST DO
- Check `az account show` before attempting any write operation.
- Use `az account get-access-token --resource 499b84ac-1321-427f-aa17-267ca6975798` for the Bearer token.
- Resolve org/project/repo from `git remote get-url origin` dynamically.
- Always GET the work item before a state transition to determine valid target states.
- Use `application/json-patch+json` content type for all PATCH/POST to work items.
- Use `api-version=7.1` for all REST calls.

### MUST NOT DO
- Do not prompt for a PAT, browser sign-in, or any manual token.
- Do not expose the Bearer token in any output.
- Do not delete work items.
- Do not create duplicate items — query first if in doubt.
- Do not modify `System.Id`, `System.Rev`, or `System.CreatedDate` (read-only).
- Do not ask more than one clarifying question per invocation.
