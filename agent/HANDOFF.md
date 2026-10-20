# Handoff

state: RUNNING — KB-12a done (hash in next PROGRESS entry; KB-11 0ac772e, c5626fa); KB-11b ready/next (16 rehearsal findings); KB-12b owner
last commit: kb: benchmark fixture, parsing test and expected proposal (KB-12a); before it 0ac772e (C3), c5626fa (C1+C2), f8a2be4, a6bfbef
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through 0ac772e (39435006417); ISSUE-2 closed
next exact action: KB-11b — kb-author with scratchpad kb-11b/page-packet.md (16 decided findings); batch marker; kb-review; verifier; commit
then: KB-12b once KB-00b exists (owner); KB-GATE (gate-reviewer) after KB-09, KB-12b; a real migration starts at SKILL.md "Start here"
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019, D-020, D-021, D-022, D-023, D-024, D-025 (attribute set), D-026 (module table as proposals), F-6, F-7
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
