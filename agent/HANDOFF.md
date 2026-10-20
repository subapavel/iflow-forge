# Handoff

state: RUNNING — KB-11 C1+C2 committed (hash in next PROGRESS entry); C3 next (conversion/README.md, final SKILL.md); KB-08 e145a84 (ISSUE-1 Phase B owner)
last commit: kb: scenario contribution, overview and migration procedures (KB-11 C1+C2); before it f8a2be4 (B3d), a6bfbef, dc3644e (owner), de9f2cf, c98e0f7
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through f8a2be4 (39420055798); ISSUE-2 closed
pages: iflows/templates ten headings; scripts/ five; migration/ and scenario-contribution: contract per packet; D-019 forms; checker counts `→`/`—` as words
then: KB-11 done (no stub under knowledge/pipeline, loaded set ≤ 5,000); KB-12 needs owner KB-00b; KB-09 owner/local; owner wants a real migration trial now
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: -design.md (F-3, F-5, F-7)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: ISSUE-1 waits for owner Phase B; ISSUE-2 closed 2026-10-20; follow-ups: NOTES.md, Java CiteCheckTest proposal (BACKLOG candidate)
decisions pending at hand-over: D-016, F-1, D-019, D-020, D-021, D-022, D-023, D-024, D-025 (attribute set), D-026 (module table as proposals), F-6, F-7
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
