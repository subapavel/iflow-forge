# Handoff

state: RUNNING — KB-07 done (hash in PROGRESS next entry; D-021 amended, F-4, F-5); next KB-13 (ISSUE-1 port, first ready row), then KB-08
last commit: (this commit) feat(knowledge): contracts.md, message-path.md, partner-directory.md and contractNames check (KB-07); before it daea078, ba478a4
branch: claude/eager-brown-uw61ft (D-017; the owner fast-forwards main) — all commits pushed; CI green through KB-06 B5 (run 39350932246); KB-07 run: next entry
next exact action: KB-13 — scout tools/gen_bpm_builtin_functions.py + BuiltinFunctionCatalog; write the acceptance spec (synthetic plugin fixture, same JSON)
then: test-author (red) → implementer (Java tool in po-xray-cli, delete the four .py) → reviewer → verify → commit; owner: Phase B (kb-13 prompt)
pages: iflows/templates ten headings; scripts/ five; registry pages cite full ids; D-019 forms (process, CHANGELOG.txt#L); ≤ 1,500 words; all checks on verify
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec: same date -design.md (changelog F-3, F-5)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: none (ISSUE-1 → KB-13; owner Phase A done: 224/11/91/78, SHA-256 f4680de9…; Phase B after the port); follow-ups: agent/NOTES.md (contractNames)
decisions pending review at hand-over: D-016 (step order), F-1 (Participant shape), D-019 (fact base + cite forms), D-020 (Steps lines), D-021 (contract names)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
