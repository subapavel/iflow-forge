# Progress journal

Append-only. One entry per task, newest at the bottom, in the format of `docs/WORKFLOW.md §2`.
Every check carries its evidence state: `VERIFIED_PASS | EXECUTED_FAIL | NOT_RUN |
ENVIRONMENT_BLOCKED`. Deviations name the finding.

## BOOTSTRAP · 2026-10-19 · (hash of the bootstrap commit)
Test first: none — documentation, configuration and agent definitions only
Changed: CLAUDE.md, AGENTS.md, docs/WORKFLOW.md, docs/GATE.md, docs/DECISIONS.md, docs/findings/TEMPLATE.md, agent/ (BACKLOG.md, HANDOFF.md, NOTES.md), PROGRESS.md, issues/README.md, .claude/agents/ (8 definitions), .claude/agent-memory/, .claude/settings.json, .mcp.json, deploy/claude-cloud/ (README.md, setup.sh); plan Global Constraints (direct to main)
Before it, with the owner in the local session (not autonomous): spec 418a8f7 + af6d43b, plan 5c32b76 + fc3f802, sources c4329e3, English inputs 0554b80 (KB-00)
Checks: mvn verify NOT_RUN (no code change) · knowledge module NOT_RUN (does not exist yet, KB-03)
Review: n/a (with the owner)
Decisions: D-001 … D-014
Deviation: none
Next: KB-02 (then KB-01, KB-03)

## KB-02 · 2026-10-19 · f9b5373
Test first: IFlowReaderTest (13: manifestFields … mainProcessIsTheOneTheSenderFlowTargetsEvenWhenNotFirst, participantEndpointEmptyWhenUnresolved), PackageUnpackerTest (4), IFlowDumpTest (2), SourcesAcceptanceTest (2); red observed as compile errors on io.poxray.cpi (target/kb-02-red.log; target/kb-02-red2.log for F-1); implementer added IFlowReaderEdgeCasesTest (7), PackageExportTest (5), CpiCommandsTest (4)
Changed: po-xray-core (io.poxray.cpi: IFlow, Participant, Step, Param, IFlowReader, PackageExport, PackageUnpacker, IFlowDump), po-xray-cli (IflowCommand, UnpackCommand, PoXrayCommand), po-xray-native (NativeReflection), root pom (surefire `iflowforge.sources`), README.md, docs/findings/F-1.md, plan Task 2 (changelog + Interfaces), docs/DECISIONS.md, agent/NOTES.md, agent memories
Checks: mvn verify VERIFIED_PASS (target/kb-02-verify.log, exit 0; surefire core 114/0 err/22 skipped (pre-existing testdata skips)/0 fail, cli 10/0/1/0, native 2/0/0/0, mcp 8/0/1/0) · io.poxray.cpi VERIFIED_PASS (33 tests, Skipped: 0, target/kb-02-cpi.log) · SourcesAcceptanceTest VERIFIED_PASS (29 artifacts of 2026-06-trial, 831 steps, 110 message flows with endpoint) · CLI smoke `iflow generic-pipelines` VERIFIED_PASS (target/kb-02-smoke.log, 5 headers, no stack trace) · `iflow --json templates` VERIFIED_PASS (19 iFlows) · unpack byte-identity NOT_RUN (export zips are not in the cloud; the owner's local check, plan Task 2 step 7)
Review: PASS (8 non-blocking remarks recorded as follow-ups in agent/NOTES.md "Build and environment")
Decisions: D-015 (propdef carries no default), D-016 (step order: main process, processId inlining, event subprocesses; draft — review at hand-over), D-017 (designated branch)
Deviation: F-1 (Participant widened with endpoint and adapter properties; plan Interfaces edited with a changelog line). Plan test list extended: event subprocess, local process, main-process choice, script collection (0 steps)
Next: KB-01 (skeleton), then KB-02b (CI workflow) and KB-03 (knowledge module)

## KB-01 · 2026-10-19 · a084eba
Test first: none — content task; kb-reviewer PASS is the acceptance
Changed: knowledge/pipeline/ (SKILL.md, 9 stub pages, 5 .gitkeep), .claude/skills/sap-is-pipeline/SKILL.md, README.md (one paragraph, 2 lines added), agent/HANDOFF.md (line-length fixes from the KB-02 rewrite)
Checks: `ls -R knowledge/pipeline` and `find` VERIFIED_PASS (15 files of plan Task 1, kb-author) · `git status --short | grep testdata` VERIFIED_PASS (empty) · `grep -rn "{{" knowledge/pipeline` VERIFIED_PASS (empty) · README diff VERIFIED_PASS (2 insertions, architect) · mvn verify NOT_RUN (no code change; KB-02b's run covers the tree)
Review: PASS (kb-reviewer; remark: SKILL.md "load nothing else" wording to be fixed when Task 11 rewrites SKILL.md — noted in agent/NOTES.md)
Decisions: none
Deviation: none
Next: KB-02b (commit of the CI workflow after its review), then KB-03

## KB-02b · 2026-10-19 · f627a21
Test first: none — a workflow file has no red test; acceptance = the run observed green on GitHub for this commit (recorded in the next entry) plus reviewer PASS
Changed: .github/workflows/verify.yml (new), docs/WORKFLOW.md §4 (CI wording), docs/DECISIONS.md (D-018), agent ledger
Checks: mvn verify VERIFIED_PASS (target/kb-02b-verify.log, exit 0, implementer) · YAML load VERIFIED_PASS (ruby -ryaml) · GitHub run NOT_RUN at commit time (triggered by this push; result in the next entry) · action versions checkout@v7 / setup-java@v6 taken from the actions' main README (Context7 unreachable; fallback v4/v5 if the run cannot resolve them)
Review: PASS (reviewer; remarks: D-011/WORKFLOW §4 wording updated in this commit)
Decisions: D-018 (CI exists; supersedes D-011)
Deviation: none
Next: KB-03 (knowledge module)

## KB-03 · 2026-10-19 · 6138223
Context compacted during this task (before the test-author's hand-back); HANDOFF re-read, git status and log re-verified.
Test first: ChecksTest (12: coverageReportsMissingPages, coveragePassesWhenEveryPageExists, headingsOrderMatters, headingsAcceptTheExactContract, placeholdersReportsMissingAndExtra, placeholdersPassWhenSetsMatch, namesReportsUnknownScript, budgetSkipsStubs, forbiddenValueFound, skipIfRequestedAbortsOnlyTheNamedChecks, forbiddenFileIsPresentOnlyWhenThePropertyIsSet, forbiddenFileResolvesRelativeValuesAgainstTheRepositoryRoot — the last added in review round 1, green on first run against the existing code), real-KB classes CoverageTest, HeadingsTest, PlaceholdersTest, NamesTest, BudgetTest, ForbiddenValuesTest; red observed as compile errors (target/kb-03-red.log)
Changed: iflow-forge-knowledge (new module: pom.xml, src/test io.iflowforge.knowledge Checks, KnowledgeBase, tests; io.poxray.cpi.SyntheticArtifact bridge), root pom.xml (module), docs/findings/F-2.md, plan (changelog, Task 3/4/5), BACKLOG (KB-06), NOTES, HANDOFF, .claude/agent-memory
Checks: mvn verify VERIFIED_PASS (target/kb-03-v-verify.log; knowledge module ChecksTest 12/0, CoverageTest and ForbiddenValuesTest skipped) · `-Dkb.skip=coverage,headings` VERIFIED_PASS (target/kb-03-v-skip.log) · `-Dkb.skip=` EXECUTED_FAIL as intended (target/kb-03-noskip.log, CoverageTest: 74 missing pages = 10 iflows, 19 templates, 45 scripts) · relative `-Dkb.forbidden` VERIFIED_PASS (target/kb-03-forbidden.log, 1 run 0 skipped) · KB-02b GitHub run 39293209159 VERIFIED_PASS (conclusion success on f627a21, 2026-10-19T17:21Z)
Review: PASS (round 2; round 1 FAIL: missing test for the relative kb.forbidden path, two evidence runs not shown, plan Task 4/5 steps stale — all fixed)
Decisions: none new (checks semantics C1–C6 as the acceptance spec in the architect's scratchpad; D-018 unchanged)
Deviation: F-2 (coverage check red until KB-06: module pom defaults `kb.skip=coverage`, KB-06 clears it; relative `kb.forbidden` resolves against the repo root; plan Task 3/4/5 and BACKLOG KB-06 edited)
Next: KB-04 (Pass A generic iFlow pages)

## KB-04 · 2026-10-19 · (hash: the commit `kb: generic iFlow pages step05, step06, step08 idoc, step08 xi (KB-04 B3)`; recorded in the next entry)
Test first: content task (D-014) — acceptance = kb-reviewer PASS per batch + green knowledge module (HeadingsTest, PlaceholdersTest, NamesTest, BudgetTest on the real pages)
Changed: knowledge/pipeline/iflows/ (10 pages, one per generic iFlow of 2026-06-trial), naming.md (placeholders section, 25 tokens), open-questions/kb-04.md (32 bullets, F-3), pitfalls.md (pointer), docs/DECISIONS.md (D-019 fact base and citation forms, D-020 self-cited Steps lines), agent/NOTES.md (reader follow-up: iFlow-level properties), agent ledger, .claude/agent-memory
Batches: B1 25f362c (step01 xi, step01 idoc, step02 inbound; review round 1 FAIL 11 findings → round 2 PASS), B2 67862af (step02 async, step02 sync, step04; round 1 FAIL 2 defects → round 2 PASS), B3 (step05, step06, step08 idoc, step08 xi; round 1 FAIL 5 citation findings → round 2 PASS; hash in the next entry)
Checks: mvn verify VERIFIED_PASS per batch (target/kb-04-b{1,2,3}-verify.log) · coverage run with -Dkb.skip= EXECUTED_FAIL as intended with 0 iflows pages missing after B3 (target/kb-04-b3-coverage.log; templates/ and scripts/ remain for KB-05/KB-06) · pages 1077–1481 words, all ≤ 1,500 · customer-data grep over the pages empty · GitHub Actions: B1 run 39304922469 success, B2 run 39309826894 success, B3 run recorded in the next entry
Review: PASS (kb-reviewer, per batch; recurring findings recorded in .claude/agent-memory/kb-reviewer/MEMORY.md)
Decisions: D-019 (Pass A fact base = kb.sources label; citation forms), D-020 (Steps lines self-cited; pages ≤ 1,500 words without splitting)
Deviation: F-3 (open questions moved to open-questions/kb-04.md; pitfalls.md kept within budget; spec §6 edited). The ≤ 1,350-word aim was missed on the 99-step async page, limit respected
Next: KB-05 (template pages), then KB-06 (script pages; clears the pom default kb.skip=coverage, F-2)
