# Handoff

state: RUNNING — KB-11 done (C3 hash in next PROGRESS entry; C1+C2 c5626fa); KB-GATE needs KB-09 (owner/local), KB-12 (owner KB-00b) → IDLE_NO_READY_TASK
last commit: kb: conversion recipes README and final SKILL.md (KB-11 C3); before it c5626fa (C1+C2), f8a2be4 (B3d), a6bfbef, dc3644e (owner), de9f2cf
then: KB-12 once KB-00b exists; KB-GATE (gate-reviewer) after KB-09, KB-12; a real migration starts at SKILL.md "Start here" → migration/po-ico-to-pipeline.md
pages: iflows/templates ten headings; scripts/ five; migration/, conversion/, scenario-contribution per packet; D-019 forms; checker counts `→`/`—`
then: KB-12 (sample-ico.json, BenchmarkFixtureTest, expected-proposal.md, benchmark run) once KB-00b exists; KB-GATE (gate-reviewer) after KB-09, KB-12
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
