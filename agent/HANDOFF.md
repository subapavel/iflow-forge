# Handoff

state: RUNNING — KB-04 done (B1 25f362c, B2 67862af, B3 598e578); KB-05 in progress: B1 (step01 eo, alo, sync, step03) with the kb-author; B2–B5 follow
last commit: 598e578 kb: generic iFlow pages step05, step06, step08 idoc/xi (KB-04 B3); before it 67862af B2, 25f362c B1, 6138223 KB-03
branch: claude/eager-brown-uw61ft (D-017; the owner fast-forwards main) — all commits pushed; CI green through KB-04 (run 39314126206)
next exact action: KB-05 B1 (step01 eo, alo, sync, step03): packet in scratchpad kb-05/page-packet.md; kb-author → kb-reviewer PASS → commit
then: KB-05 B2–B5 (step04 ×4 + step05; step07 ×4; step07 ×3; custom exit, custom error, script collection); KB-06 clears pom kb.skip=coverage (F-2)
pages: ten-heading contract, D-019 citation forms, D-020 self-cited Steps lines, ≤ 1,500 words (aim 1,350); open questions → open-questions/<task>.md (F-3)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2); spec: same date -design.md (changelog F-3)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019) and 2026-09-current (32; value mapping + upload helper only there, pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: none; follow-ups in agent/NOTES.md ("Build and environment", "Knowledge module"); reviewer memories in .claude/agent-memory/
decisions pending review at hand-over: D-016 (step order rule), F-1 (Participant shape), D-019 (fact base label), D-020 (self-cited Steps lines)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
