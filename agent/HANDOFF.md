# Handoff

state: RUNNING — KB-05 in progress: B1 7dabf99, B2 (this commit; step04 ×4, step05); B3 (step07 p2p, p2p.to.idoc, p2p.to.xi, sync) next; B4, B5 follow
last commit: 7dabf99 kb: template pages inbound (KB-05 B1); before it 598e578 KB-04 B3, 67862af B2, 25f362c B1, 6138223 KB-03
branch: claude/eager-brown-uw61ft (D-017; the owner fast-forwards main) — all commits pushed; CI green through KB-04 (run 39314126206)
next exact action: KB-05 B3 (step07 p2p, p2p.to.idoc, p2p.to.xi, sync): packet + addenda in scratchpad kb-05/page-packet.md; kb-author → kb-reviewer PASS → verify → commit
then: KB-05 B4 (step07 o2mo, o2mm, moar), B5 (custom exit, custom error, script collection); close KB-05; KB-06 clears pom kb.skip=coverage (F-2)
pages: ten-heading contract, D-019 citation forms, D-020 self-cited Steps lines, ≤ 1,500 words (aim 1,350); open questions → open-questions/<task>.md (F-3)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2); spec: same date -design.md (changelog F-3)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019) and 2026-09-current (32; value mapping + upload helper only there, pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: ISSUE-1 (pre-existing .py files, owner decision); follow-ups in agent/NOTES.md; reviewer memories in .claude/agent-memory/
decisions pending review at hand-over: D-016 (step order rule), F-1 (Participant shape), D-019 (fact base label), D-020 (self-cited Steps lines)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
