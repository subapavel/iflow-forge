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

## KB-02 · 2026-10-19 · (hash: the commit `feat(cpi): iFlow reader, package unpacker, CLI iflow and unpack`; recorded in the next entry)
Test first: IFlowReaderTest (13: manifestFields … mainProcessIsTheOneTheSenderFlowTargetsEvenWhenNotFirst, participantEndpointEmptyWhenUnresolved), PackageUnpackerTest (4), IFlowDumpTest (2), SourcesAcceptanceTest (2); red observed as compile errors on io.poxray.cpi (target/kb-02-red.log; target/kb-02-red2.log for F-1); implementer added IFlowReaderEdgeCasesTest (7), PackageExportTest (5), CpiCommandsTest (4)
Changed: po-xray-core (io.poxray.cpi: IFlow, Participant, Step, Param, IFlowReader, PackageExport, PackageUnpacker, IFlowDump), po-xray-cli (IflowCommand, UnpackCommand, PoXrayCommand), po-xray-native (NativeReflection), root pom (surefire `iflowforge.sources`), README.md, docs/findings/F-1.md, plan Task 2 (changelog + Interfaces), docs/DECISIONS.md, agent/NOTES.md, agent memories
Checks: mvn verify VERIFIED_PASS (target/kb-02-verify.log, exit 0; surefire core 114/0 err/22 skipped (pre-existing testdata skips)/0 fail, cli 10/0/1/0, native 2/0/0/0, mcp 8/0/1/0) · io.poxray.cpi VERIFIED_PASS (33 tests, Skipped: 0, target/kb-02-cpi.log) · SourcesAcceptanceTest VERIFIED_PASS (29 artifacts of 2026-06-trial, 831 steps, 110 message flows with endpoint) · CLI smoke `iflow generic-pipelines` VERIFIED_PASS (target/kb-02-smoke.log, 5 headers, no stack trace) · `iflow --json templates` VERIFIED_PASS (19 iFlows) · unpack byte-identity NOT_RUN (export zips are not in the cloud; the owner's local check, plan Task 2 step 7)
Review: PASS (8 non-blocking remarks recorded as follow-ups in agent/NOTES.md "Build and environment")
Decisions: D-015 (propdef carries no default), D-016 (step order: main process, processId inlining, event subprocesses; draft — review at hand-over), D-017 (designated branch)
Deviation: F-1 (Participant widened with endpoint and adapter properties; plan Interfaces edited with a changelog line). Plan test list extended: event subprocess, local process, main-process choice, script collection (0 steps)
Next: KB-01 (skeleton), then KB-02b (CI workflow) and KB-03 (knowledge module)
