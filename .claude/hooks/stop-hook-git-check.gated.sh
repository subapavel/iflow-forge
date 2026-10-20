#!/bin/bash
# Gated front for the cloud session's Stop hook ~/.claude/stop-hook-git-check.sh (installed by the
# environment manager, not by this repository). The original reminds the agent to "commit and push"
# on every idle. In an autonomous session a batch stays uncommitted for hours while subagents
# review it, and every reminder costs the architect a full turn over the whole context. This front
# skips the reminder while a batch is under review and otherwise throttles it; the signing and
# unpushed-commit checks of the original still run on a clean tree.
#
# Install (owner, once, in the cloud environment's Setup script — it runs at container start):
#   cp ~/.claude/stop-hook-git-check.sh ~/.claude/stop-hook-git-check.orig.sh
#   cp "$PWD/.claude/hooks/stop-hook-git-check.gated.sh" ~/.claude/stop-hook-git-check.sh
# The agent does not install it itself: rewriting its own hook is a self-modification the
# permission classifier refuses, and rightly so. See docs/WORKFLOW.md §10.
input=$(cat)
[[ "$(printf '%s' "$input" | jq -r '.stop_hook_active' 2>/dev/null)" == "true" ]] && exit 0
top=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0

# Gate 1 — a batch is under review. The architect creates target/.batch-in-review when it hands a
# batch to the reviewer and removes it in the same command as `git commit` (docs/WORKFLOW.md §10).
# target/ is gitignored, so the marker never reaches the repository.
[[ -f "$top/target/.batch-in-review" ]] && exit 0

# Gate 2 — the autonomous branch with a dirty tree. The workflow commits one batch per review PASS
# and green verify, never per idle (CLAUDE.md "one task = one commit", "green before push"), so a
# dirty tree on claude/* is the normal mid-batch state. A clean tree falls through to the original,
# which still flags unpushed or unsigned commits.
if [[ "$(git branch --show-current 2>/dev/null)" == claude/* ]]; then
  if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
    exit 0
  fi
fi

# Gate 3 — at most one reminder per 30 minutes.
stamp=/tmp/stop-hook-git-check.last
if [[ -f "$stamp" && $(( $(date +%s) - $(stat -c %Y "$stamp" 2>/dev/null || echo 0) )) -lt 1800 ]]; then exit 0; fi
touch "$stamp"

orig=~/.claude/stop-hook-git-check.orig.sh
[[ -x "$orig" ]] || exit 0
printf '%s' "$input" | exec "$orig"
