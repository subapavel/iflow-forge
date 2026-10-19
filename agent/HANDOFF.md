# Handoff

state: NOT_STARTED — bootstrap of the way of working committed; first ready rows KB-01 (content) and KB-02 (code); owner starts the cloud session
last commit: bootstrap (this one, docs and agents); before it fc3f802 plan in Java, 0554b80 English inputs, c4329e3 sources, af6d43b spec revision
branch: main
next exact action: KB-02 first (the reader unblocks KB-03 and the dumps KB-04 needs): acceptance spec → test-author → packet → implementer; KB-01 may run after it (same writer rule)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md holds files, signatures, steps and tests per task; BACKLOG rows map KB-nn → Task nn
spec: docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md; §13 is the state of the inputs; §9 the gate (docs/GATE.md)
sources: knowledge/sources/2026-06-trial (29 artifacts) and 2026-09-current (32); INDEX.md + CHANGELOG.txt per package; never parameters.prop
inputs: knowledge/inputs/en/ (39 Confluence pages + tenant-export review, drafts only); claims and triage come in KB-10
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff) and the unpack byte-identity check of KB-02 need testdata/; record NOT_RUN in the cloud, the owner runs them
open issues: none
decisions pending review at hand-over: none yet (D-013 will produce the attribute set draft in KB-11)
roles: architect only; test-author / implementer / kb-author / reviewer / kb-reviewer on Opus 5.5, verifier Sonnet 5.5, scout Haiku 5.5, gate-reviewer Fable 5.1 (AGENTS.md)
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; CLI jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar
notes: agent/NOTES.md (sources layout, iFlow file format, inputs provenance, Windows quirks)
