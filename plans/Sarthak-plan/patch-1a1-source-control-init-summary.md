# Patch 1a1: Source control init

**Author:** Sarthak · **Date:** 2026-11-05 · **Plan:** `2026-11-05-phase-1-foundations.md`, step 1a1

## Problem
`D:\WM-Admin-Portal-BE` was not a git repository and still held `.github/modernize/`, hook scripts left by a VS Code app-modernisation extension (master plan conflict 6).

## Solution
- Deleted `.github/modernize/java-upgrade/` (`.gitignore`, `hooks/scripts/recordToolUse.ps1`, `hooks/scripts/recordToolUse.sh`) and the then-empty `.github/`.
- `git init -b Dev`.
- Installed `.git/hooks/pre-commit`, which runs `.claude/hooks/pre-commit.sh`.
- Staged everything (`git add -A`). Nothing committed.

## Verification (run 2026-11-05)
- `bash .claude/hooks/test-hooks.sh`: `passed: 41  failed: 0`.
- `git add -A`: 65 files staged. None of `application-local.yml`, `application-staging.yml`, `.env`, `logs/`, `target/`, `.idea/`, `.vscode/`, `settings.local.json` is staged.
- `bash .claude/hooks/pre-commit.sh`: exit 1, three `[BLOCK]` lines:
  - C1 `src/main/resources/application.yml:19`: the database password placeholder has a fallback value. **DevOps.**
  - C2 `src/main/resources/application.yml:171`: a Mailgun API key. **DevOps.**
  - C2 `src/test/resources/application-test.yml:30`: an Azure storage account key in the test profile. The value is fake, but the file is ours. **Fixed in step 1h1**, which rewrites that file. The Azure block isn't needed until Phase 2f.

## Limitations and follow-up
- **`safe.directory`.** Git refuses this repository ("dubious ownership") because the `D:` drive doesn't record file ownership.
  - Commands in this step passed `-c safe.directory=D:/WM-Admin-Portal-BE`.
  - The permanent fix is `git config --global --add safe.directory D:/WM-Admin-Portal-BE`. That is a change to the developer's global git config, so it is left to them.
- **Line endings.** Git warns that LF becomes CRLF on checkout. This is harmless, and `.gitattributes` decides it. Check it before the first commit if shell scripts must keep LF.
- **1a2** (first commit, Azure DevOps repository, push, `Staging`) waits for DevOps to clear the two `application.yml` blocks, and for the user's go-ahead to push.
