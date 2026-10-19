# ISSUE-1 — Four tracked Python files pre-date the "Java only" rule
Task: none blocked (found by the KB-05 B2 verifier's `git status` check)
Date: 2026-10-19

## What is needed
A decision on the four `.py` files committed on 2026-10-09, before CLAUDE.md's "Java only — a Python
file is a review failure" rule existed: `docs/prototype/cmp.py`, `docs/prototype/moin.py`,
`docs/prototype/proc.py` (reference prototypes of the MOIN/Galaxy format, per `docs/START_PROMPT.md`)
and `tools/gen_bpm_builtin_functions.py` (the generator of the BPM built-in function catalogue, referenced
by `po-xray-core/src/main/java/io/poxray/bpm/BuiltinFunctionCatalog.java` lines 17 and 116 and by
`docs/BPM-BUILTIN-FUNCTIONS.md`). Options: (a) delete the prototypes and port the generator to a Java
tool in `po-xray-cli` (a code task row with a red test); (b) delete all four and keep the generated JSON
as the artifact of record; (c) grant them an explicit exception in CLAUDE.md.

## Why the agent cannot decide it
Deleting files the owner committed and changing CLAUDE.md are the owner's calls; porting the generator
needs the NWDS plugins directory (customer-side input) to verify the output, which the cloud session
does not have.

## What was tried
Nothing changed. The files are untouched; no KB task depends on them.

## Default if the owner does not answer
Leave them as they are; the part-1 gate review records them as a known pre-existing deviation.

## Owner's answer
2026-10-19, owner (in the session): option (a) — delete the three prototypes under `docs/prototype/` and port
`tools/gen_bpm_builtin_functions.py` to a Java tool; then remove the Python file. Backlog row KB-13 added (code task, red
test first); the generator's output check against the NWDS plugins directory runs locally with the owner (`NOT_RUN` in the
cloud is accepted, as for KB-02's byte-identity check).
