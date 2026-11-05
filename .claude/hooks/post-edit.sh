#!/usr/bin/env bash
# Claude Code hook (PostToolUse on Edit and Write, registered in .claude/settings.json).
# Checks the lines Claude just changed against the rules in wealthmax-checks.sh. Exit 2 hands the
# findings to Claude; the edit itself has already happened. Anything unexpected exits 0 quietly,
# because this hook must never get in the way of an edit.
# Each process start costs ~50-100 ms on Windows, so this avoids every one it can.

self=${BASH_SOURCE[0]//\\//}
case "$self" in */*) cd "${self%/*}" 2>/dev/null || exit 0 ;; esac
. ./wealthmax-checks.sh 2>/dev/null || exit 0
cd ../.. || exit 0

# No jq on Windows machines: take file_path straight from the JSON on stdin.
IFS= read -r -d '' input
re='"file_path"[[:space:]]*:[[:space:]]*"(([^"\\]|\\.)*)"'
[[ $input =~ $re ]] || exit 0
file=${BASH_REMATCH[1]//\\\\//}

# "H path" for a tracked file, "? path" for a new one. Empty for a file outside this repo, or one
# git ignores.
entry=$(git --literal-pathspecs ls-files -t -c -o --exclude-standard --full-name -- "$file" 2>/dev/null) || exit 0
entry=${entry%%$'\n'*}
tag=${entry%% *}
rel=${entry#* }
[ -n "$entry" ] && [ -f "$rel" ] || exit 0

if [ "$tag" = "?" ]; then
    diff=$(printf 'diff --git a/%s b/%s\n--- /dev/null\n+++ b/%s\n@@ -0,0 +1 @@\n' "$rel" "$rel" "$rel"
           sed 's/^/+/' -- "$rel")
else
    diff=$(wm_diff HEAD -- ":(literal)$rel")
fi
[ -n "$diff" ] || exit 0

findings=$(wm_check_diff edit <<<"$diff") || exit 0
if [ "$tag" != "?" ] && wm_is_migration "$rel" && git cat-file -e "HEAD:$rel" 2>/dev/null; then
    findings=$(wm_migration_finding edit "$rel"; printf '%s' "$findings")
fi
[ -n "$findings" ] || exit 0

{
    echo "WealthMax check on $rel (the edit is saved). Fix these, or say why they're fine:"
    printf '%s\n' "$findings"
} >&2
exit 2
