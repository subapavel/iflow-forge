# Handoff

state: RUNNING — KB-06 done (B1 9b9a38f, B2 5f12052, B3 711b5f0, B4 ba478a4, B5 this commit; F-2 closed); next KB-07 (first ready row), KB-13 ready too
last commit: kb: script pages payload, validation and custom determination; F-2 closed (KB-06 B5); before it ba478a4, 711b5f0, 5f12052, 9b9a38f
branch: claude/eager-brown-uw61ft (D-017; the owner fast-forwards main) — all commits pushed; CI green through KB-06 B4 (run 39347779378)
next exact action: KB-07 — read plan Task 7 by section via the scout; write the acceptance spec (Checks.contractNames + ContractNamesTest) and the page packet
then: test-author (red) → implementer → kb-author (contracts.md, partner-directory.md) → reviewer + kb-reviewer → verify → commit; then KB-13
pages: iflows/templates ten headings; scripts/ five headings; D-019 cite forms (incl. process, CHANGELOG.txt#L); ≤ 1,500 words; every check runs on verify
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2); spec: same date -design.md (changelog F-3, extended)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: none (ISSUE-1 → KB-13; owner Phase A done, Phase B after the port: scratchpad kb-13/local-agent-prompt.md); follow-ups in agent/NOTES.md
decisions pending review at hand-over: D-016 (step order), F-1 (Participant shape), D-019 (fact base + cite forms incl. process, CHANGELOG), D-020 (Steps lines)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
