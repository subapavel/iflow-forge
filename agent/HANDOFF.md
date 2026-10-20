# Handoff

state: RUNNING — KB-10 done (B3d hash in next PROGRESS entry; B3c c98e0f7); KB-11 ready/next; KB-08 e145a84 (ISSUE-1 Phase B owner)
last commit: kb: pitfalls and open questions of KB-10; applied column complete (B3d); before it a6bfbef, dc3644e (owner), de9f2cf, c98e0f7 (B3c)
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green from 9aae29f (re-run) and 5e577ac (first attempt); ISSUE-2 closed
next exact action: KB-11 — scout reads plan Task 11 by section; acceptance spec + page packet (attribute set draft, scenario-contribution, procedures, SKILL)
then: kb-author per page group → kb-reviewer (split by file set, ≤ 65 turns) → verifier → one commit per group; KB-09 owner/local; KB-12 needs KB-00b
pages: iflows/templates ten headings; scripts/ five; registry pages cite full ids; D-019 forms (process, CHANGELOG.txt#L); ≤ 1,500 words; all checks on verify
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: same date -design.md (changelog F-3, F-5)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: ISSUE-1 waits for owner Phase B; ISSUE-2 closed 2026-10-20 (Actions runner, owner side); follow-ups: NOTES.md
decisions pending at hand-over: D-016, F-1, D-019, D-020, D-021, D-022, D-023 (label-qualified cites; root resource path), D-024, F-6
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
