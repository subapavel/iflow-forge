# Handoff

state: RUNNING — KB-10 B2 done (hash: next commit); B3a next; B1 c62dba8; KB-08 e145a84; KB-13 4d7ee0f (ISSUE-2 CI; ISSUE-1 Phase B owner)
last commit: KB-10 B2 kb: changelog 2026-09-current (3 files) + version notes on 29 pages; before it c62dba8 (B1), afd0a64 (ledger), e145a84 (KB-08)
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through ae72101; every later run fails with no runner (ISSUE-2)
next exact action: KB-10 B3a — kb-author on scratchpad kb-10/page-packet-b3.md §B3a (scripts pages, ≈450 triage rows); kb-review; verifier; commit; B3b–d
then: kb-author (batches by input group) → kb-reviewer per batch → verifier → commit per batch; KB-09 stays owner/local; KB-11 after KB-10
pages: iflows/templates ten headings; scripts/ five; registry pages cite full ids; D-019 forms (process, CHANGELOG.txt#L); ≤ 1,500 words; all checks on verify
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: same date -design.md (changelog F-3, F-5)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: ISSUE-2 (CI runs since ae72101 fail in ~3 s, no runner, 5 recorded; owner checks Actions quota/billing); ISSUE-1 waits for owner Phase B; NOTES.md
decisions pending review at hand-over: D-016, F-1, D-019, D-020, D-021, D-022, D-023 (label-qualified cites; root-level resource path), F-6 (changelog split)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
