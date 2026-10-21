# ISSUE-5 — a dry-run profile prefix reached the history of docs/DECISIONS.md (owner-only)
Task: FIX-04 (the working tree is clean of it since FIX-04; only the history question stays open)
Date: 2026-10-21

## What happened
The reason column of D-042 (`docs/DECISIONS.md`, row D-042) quoted the queue name of the dry-run-3 profile with its
tenant-style prefix instead of the `<prefix>Q01` placeholder. The architect wrote it in MIG-16 (commit b69e2f2) and the
row travelled unchanged into MIG-17 (commit 7ee752c). The staged-file customer grep before the MIG-17 commit printed the
hit, but the commit command was chained after it instead of guarded by it, so the commit ran. FIX-04 replaces the value
with `<prefix>Q01`; the architect's commit guard now stops on a non-empty grep (WORKFLOW §1 practice, PROGRESS FIX-04).

## Why the owner
The prefix is a dry-run value the owner asked to keep out of the repository, and it is in two pushed commits of
`claude/eager-brown-uw61ft`. Removing it from history means rewriting pushed history, which the agents never do
(CLAUDE.md "Never"). The owner decides: leave the history as it is (the prefix is a demo value, not a customer
identifier), or rewrite the branch before fast-forwarding `main` (D-017).

## What happens when it is answered
Nothing else changes in the tree. If the owner rewrites, the ledger hashes in PROGRESS.md for MIG-16 and MIG-17 need the
new values (one docs commit).
