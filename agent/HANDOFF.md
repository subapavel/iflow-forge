# Handoff

state: RUNNING — KB-02 done (review PASS, pushed); next KB-01 (content, ready); then KB-02b (CI workflow) and KB-03 (knowledge module), ready after KB-02
last commit: feat(cpi): iFlow reader, package unpacker, CLI iflow and unpack (KB-02); before it 465c4ec bootstrap, fc3f802 plan in Java, 0554b80 English inputs
branch: claude/eager-brown-uw61ft (D-017: the cloud session's designated branch; the owner fast-forwards main) — all commits pushed
next exact action: KB-01 — kb-author writes the stubs from the page packet (plan Task 1 + spec §6), kb-reviewer checks, commit `kb: knowledge base skeleton and skill wrapper`
then: KB-02b (GitHub Actions `mvn -B -ntp verify`, JDK 25; small code task), KB-03 (acceptance spec → test-author → implementer; module iflow-forge-knowledge)
reader: io.poxray.cpi per plan Task 2 + F-1 (Participant: endpoint + adapter properties); step order D-016; ScriptCollection reads with 0 steps; propdef has no defaults (D-015)
dump surface: `java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar iflow <folder> [--json]` — the reading surface for KB-04 pages; `--json` carries properties
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog at top); spec: docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md
sources: knowledge/sources/2026-06-trial (29 artifacts, 28 with an .iflw) and 2026-09-current (32); INDEX.md + CHANGELOG.txt per package; never parameters.prop
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff) and the unpack byte-identity check of KB-02 (plan Task 2 step 7) need testdata/; NOT_RUN in the cloud, the owner runs them
open issues: none; reader follow-ups (reviewer remarks) listed in agent/NOTES.md "Build and environment"
decisions pending review at hand-over: D-016 (step order rule, public io.poxray.cpi records), F-1 (Participant shape)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1 (AGENTS.md)
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; first run downloads dependencies; counts live in <module>/target/surefire-reports1`; JDK 25, Maven 3.9; first run downloads dependencies; counts in <module>/target/surefire-reports
notes: agent/NOTES.md (sources layout, iFlow file format, inputs provenance, build and reader follow-ups); PROGRESS headings take the hash from the next entry
