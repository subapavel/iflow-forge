# Handoff

state: RUNNING — KB-05 done; KB-06 in progress: B1 9b9a38f, B2 (IDoc) committed; next B3 (XI/mapping, 9 scripts); then B4, B5 + pom kb.skip (F-2)
last commit: kb: script pages IDoc (KB-06 B2); before it 9b9a38f (B1), 1731c25 (KB-05 B5), 04422e7, 876f706, 0e659e2, 7dabf99
branch: claude/eager-brown-uw61ft (D-017; the owner fast-forwards main) — all commits pushed; CI green through KB-06 B1 (run 39335349107)
next exact action: KB-06 B3 (9 XI/mapping scripts): packet in scratchpad kb-06/page-packet.md (+ addendum); kb-author → kb-reviewer → verify → commit
then: B4 helpers/logging (Variants: logMessage, throwException), B5 payload/custom; implementer clears pom kb.skip (F-2); then KB-13 (ISSUE-1)
pages: iflows/templates ten-heading contract; scripts/ five headings (Purpose, Inputs, Outputs, Gotchas, Used by); D-019 cite forms; ≤ 1,500 words
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015); iFlow-level ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2); spec: same date -design.md (changelog F-3, extended)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: none (ISSUE-1 → KB-13; owner runs the NWDS byte-identity check locally: scratchpad kb-13/local-agent-prompt.md); follow-ups in agent/NOTES.md
decisions pending review at hand-over: D-016 (step order rule), F-1 (Participant shape), D-019 (fact base label + cite forms), D-020 (self-cited Steps lines)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
