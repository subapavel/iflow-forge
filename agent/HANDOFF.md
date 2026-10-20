# Handoff

state: RUNNING — KB-13 done (hash: next entry; ISSUE-1 port, owner Phase B pending); KB-07 done (ae72101); next KB-08 (naming, versions/README, glossary)
last commit: (this commit) feat(bpm): Java generator of the BPM built-in function catalog replaces the Python tool (KB-13); before it ae72101 (KB-07), daea078
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through KB-07 ae72101 (run 39358167204); KB-13 run: next entry
next exact action: KB-08 — read plan Task 8 by section via the scout; write the page packet (naming.md placeholder list, versions/README.md, glossary.md)
then: kb-author → kb-reviewer → verifier (PlaceholdersTest, Budget) → commit; owner: Phase B of kb-13/local-agent-prompt.md, report into ISSUE-1
pages: iflows/templates ten headings; scripts/ five; registry pages cite full ids; D-019 forms (process, CHANGELOG.txt#L); ≤ 1,500 words; all checks on verify
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: same date -design.md (changelog F-3, F-5)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: ISSUE-1 waits for the owner Phase B (`po-xray bpm-functions <plugins> <out>` vs the committed JSON; caveats in the issue); follow-ups: NOTES.md
decisions pending review at hand-over: D-016 (step order), F-1 (Participant shape), D-019 (fact base + cite forms), D-020 (Steps lines), D-021 (contract names)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
