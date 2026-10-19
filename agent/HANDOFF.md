# Handoff

state: RUNNING — KB-02 and KB-01 done; KB-02b (CI workflow) written, review pending, commits next; then KB-03 (knowledge module, ready)
last commit: kb: knowledge base skeleton and sap-is-pipeline skill wrapper (KB-01); before it f9b5373 feat(cpi) KB-02, 465c4ec bootstrap
branch: claude/eager-brown-uw61ft (D-017: the cloud session's designated branch; the owner fast-forwards main) — all commits pushed
next exact action: commit KB-02b (.github/workflows/verify.yml, chore(build)) after reviewer PASS, push, record the GitHub run; then KB-03 from the drafted acceptance spec
then: KB-03 (acceptance spec → test-author → implementer; module iflow-forge-knowledge); KB-04 needs KB-01 + KB-03
reader: io.poxray.cpi per plan Task 2 + F-1 (Participant: endpoint, adapter properties); order D-016; ScriptCollection: 0 steps; propdef: no defaults (D-015)
dump surface: `java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar iflow <dir> [--json]` — reading surface for KB-04; JSON carries properties
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog at top); spec: docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md
sources: knowledge/sources/2026-06-trial (29 artifacts, 28 with an .iflw) and 2026-09-current (32); INDEX.md + CHANGELOG.txt per package; never parameters.prop
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff) and the unpack byte-identity check of KB-02 (plan Task 2 step 7) need testdata/; NOT_RUN in the cloud, the owner runs them
open issues: none; reader follow-ups (reviewer remarks) listed in agent/NOTES.md "Build and environment"
decisions pending review at hand-over: D-016 (step order rule, public io.poxray.cpi records), F-1 (Participant shape)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer on Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; first run downloads dependencies; counts in <module>/target/surefire-reports
notes: agent/NOTES.md (sources layout, iFlow file format, inputs provenance, build and reader follow-ups); PROGRESS headings take the hash from the next entry
