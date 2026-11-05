#!/usr/bin/env bash
# git pre-commit check for WealthMax. Looks only at the staged lines, in about a second.
# Blocks secrets and edits to migrations already on Dev or Staging, and warns about everything
# else (rules in wealthmax-checks.sh). Skip it for a deliberate exception: git commit --no-verify
#
# Don't delete or rename this file. Each developer's .git/hooks/pre-commit calls it by this path,
# and git refuses every commit when it's missing (patch 34M).

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
if ! . "$here/wealthmax-checks.sh"; then
    echo "pre-commit: couldn't load wealthmax-checks.sh, so nothing was checked." >&2
    exit 0
fi
cd "$(git rev-parse --show-toplevel)" || exit 0

paths=$(wm_check_paths commit)
if ! lines=$(wm_diff --cached | wm_check_diff commit); then
    echo "pre-commit: the line checks failed to run, so only file names were checked." >&2
fi
findings=$(printf '%s\n%s' "$paths" "$lines" | sed '/^$/d')
[ -n "$findings" ] || exit 0

printf 'WealthMax pre-commit check:\n%s\n' "$findings" >&2
case "$findings" in
    *"[BLOCK]"*)
        echo "Commit stopped by the [BLOCK] lines. Fix them, or use git commit --no-verify for a deliberate exception." >&2
        exit 1 ;;
esac
exit 0
