# Handoff

state: RUNNING — KB-03 done (6138223, CI green); KB-04 in progress: B1 (step01 xi, step01 idoc, step02 inbound) with the kb-author; B2, B3 follow (D-019)
last commit: test(knowledge): consistency checks as a test module (KB-03); before it f627a21 CI workflow (KB-02b), a084eba KB-01, f9b5373 KB-02
branch: claude/eager-brown-uw61ft (D-017: the cloud session's designated branch; the owner fast-forwards main) — all commits pushed; CI green on f627a21
next exact action: KB-04 packet: scratchpad kb-04/page-packet.md (B1–B3); per batch kb-author → kb-reviewer PASS → commit `kb: generic iFlow pages <ids>`
then: KB-05 (template pages), KB-06 (script pages; clears the pom default `kb.skip=coverage`, F-2); KB-07 adds Checks.contractNames
checks: `mvn -B -ntp -q -pl iflow-forge-knowledge -am test [-Dkb.skip=] [-Dkb.forbidden=<file>]`; `-Dkb.skip=` lists the 74 missing pages (10/19/45)
reader: io.poxray.cpi per plan Task 2 + F-1 (Participant: endpoint, adapter properties); order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015)
dump surface: `java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar iflow <dir> [--json]` — reading surface for KB-04; JSON carries properties
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog: F-1, D-016, D-015, F-2); spec: docs/superpowers/specs/ (same date, -design.md)
sources: knowledge/sources/2026-06-trial (29 artifacts, 28 with an .iflw) and 2026-09-current (32); INDEX.md + CHANGELOG.txt per package; never parameters.prop
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
open issues: none; follow-ups in agent/NOTES.md ("Build and environment", "Knowledge module")
decisions pending review at hand-over: D-016 (step order rule, public io.poxray.cpi records), F-1 (Participant shape)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
