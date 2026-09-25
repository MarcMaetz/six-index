#!/usr/bin/env bash
# PreToolUse hook (Bash): block `git commit` unless docs/APPROACH.md has changes (staged or not).
# Opt out for changes that need no log entry by putting [no-approach] in the commit message.
set -euo pipefail

cmd=$(jq -r '.tool_input.command // ""')

# Only guard commit commands.
[[ "$cmd" =~ git[[:space:]]+(-C[[:space:]]+[^[:space:]]+[[:space:]]+)?commit ]] || exit 0

# Explicit opt-out.
[[ "$cmd" == *"[no-approach]"* ]] && exit 0

cd "${CLAUDE_PROJECT_DIR:-.}"

# Changes vs HEAD cover both staged and unstaged edits (commits are often `git add -A && git commit`).
if ! git diff --quiet HEAD -- docs/APPROACH.md 2>/dev/null; then
  exit 0
fi

jq -n '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason: "docs/APPROACH.md has no changes in this commit. Per AGENT.md, add a Timeline row (and a D<n> decision / assumption / open question if the work involved one) and retry. If this change genuinely needs no log entry, add [no-approach] to the commit message and say why to the user."
  }
}'
