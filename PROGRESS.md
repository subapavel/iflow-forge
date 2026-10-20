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

## KB-04 · 2026-10-19 · 598e578 (B3; B1 25f362c, B2 67862af)
Test first: content task (D-014) — acceptance = kb-reviewer PASS per batch + green knowledge module (HeadingsTest, PlaceholdersTest, NamesTest, BudgetTest on the real pages)
Changed: knowledge/pipeline/iflows/ (10 pages, one per generic iFlow of 2026-06-trial), naming.md (placeholders section, 25 tokens), open-questions/kb-04.md (32 bullets, F-3), pitfalls.md (pointer), docs/DECISIONS.md (D-019 fact base and citation forms, D-020 self-cited Steps lines), agent/NOTES.md (reader follow-up: iFlow-level properties), agent ledger, .claude/agent-memory
Batches: B1 25f362c (step01 xi, step01 idoc, step02 inbound; review round 1 FAIL 11 findings → round 2 PASS), B2 67862af (step02 async, step02 sync, step04; round 1 FAIL 2 defects → round 2 PASS), B3 598e578 (step05, step06, step08 idoc, step08 xi; round 1 FAIL 5 citation findings → round 2 PASS)
Checks: mvn verify VERIFIED_PASS per batch (target/kb-04-b{1,2,3}-verify.log) · coverage run with -Dkb.skip= EXECUTED_FAIL as intended with 0 iflows pages missing after B3 (target/kb-04-b3-coverage.log; templates/ and scripts/ remain for KB-05/KB-06) · pages 1077–1481 words, all ≤ 1,500 · customer-data grep over the pages empty · GitHub Actions: B1 run 39304922469 success, B2 run 39309826894 success, B3 run recorded in the next entry
Review: PASS (kb-reviewer, per batch; recurring findings recorded in .claude/agent-memory/kb-reviewer/MEMORY.md)
Decisions: D-019 (Pass A fact base = kb.sources label; citation forms), D-020 (Steps lines self-cited; pages ≤ 1,500 words without splitting)
Deviation: F-3 (open questions moved to open-questions/kb-04.md; pitfalls.md kept within budget; spec §6 edited). The ≤ 1,350-word aim was missed on the 99-step async page, limit respected
Next: KB-05 (template pages), then KB-06 (script pages; clears the pom default kb.skip=coverage, F-2)

## KB-05 · 2026-10-19 · 1731c25 (the commit of batch B5 `kb: template pages custom exit, custom error handling, script collection (KB-05 B5)`)
Context compacted once during B1; re-read HANDOFF, `git status`, `git log -3` before continuing.
Test first: content task (D-014) — acceptance = kb-reviewer PASS per batch + green knowledge module (headings, placeholders, names, budget on the real pages).
Changed: knowledge/pipeline/templates/ (19 pages: 18 template iFlows + the script collection, from 2026-06-trial), open-questions/kb-05.md and kb-05-outbound.md, naming.md (+9 placeholders, 34), SKILL.md row and pitfalls.md pointer (F-3 ext), agent ledger, agent/NOTES.md, issues/ISSUE-1.md, .claude/agent-memory.
Batches: B1 7dabf99 (step01 eo, alo, sync, step03; round 1 FAIL 2 + 4 minor → PASS), B2 0e659e2 (step04 ×4, step05; FAIL 3 + 3, FAIL 1, PASS), B3 876f706 (step07 p2p, p2p.to.idoc, p2p.to.xi, sync; FAIL 2 + 5 → PASS), B4 04422e7 (step07 o2mo, o2mm, moar; FAIL 3 + 2 → PASS), B5 (custom exit, custom error handling, script collection; FAIL 3 + 4 → PASS).
Checks: mvn verify VERIFIED_PASS per batch (target/kb-05-b<n>-verify.log; knowledge module 18 run, 0 fail) · coverage run with -Dkb.skip= EXECUTED_FAIL as intended, after B5 only the 45 scripts/ pages missing (target/kb-05-b5-coverage.log) · pages 700–1,469 words · customer-data and shortened-cite greps empty · GitHub Actions: B1 39317116137, B2 39321394110, B3 39325107963, B4 39327875094 all success; B5 run recorded in the next entry.
Review: PASS (kb-reviewer, per batch; rounds in the commit bodies).
Decisions: D-019 amended (B2) with the `[<id>, process "<name>"]` citation form; value mapping and Partner Directory upload helper exist only in 2026-09-current and get pages after KB-00a (D-019 label scope).
Deviation: F-3 extended (B3): a task's open questions may split per batch group as `open-questions/<task>[-<group>].md`; spec §6 layout line, SKILL.md row, pitfalls.md pointer updated.
Issues: ISSUE-1 (four tracked .py files from 2026-10-09 pre-date the Java-only rule; owner decision, nothing blocked).
Reader follow-ups (agent/NOTES.md): IdempotentProcessCall local process not inlined by the dump; a repeated local process inlined under its first call only; the known `[Fatal Error] :1:9` stderr line.
Next: KB-06 (45 script pages in five batches; the implementer clears the pom default kb.skip=coverage, F-2, after the last batch).

## KB-06 · 2026-10-20 · daea078 (the closing commit `kb: script pages payload, validation and custom determination; F-2 closed (KB-06 B5)`; CI run 39350932246 success)
Task: Pass A script pages — 45 `scripts/<stem>.md` (five-H2 contract), one per distinct Groovy stem of 2026-06-trial (152 copies, no XSLT; 3 stems with variants); `open-questions/kb-06.md` (30 bullets, 1,282 words); pom default `kb.skip=coverage` removed (F-2 closed), plain `mvn verify` runs every check.
Context compacted once after B1's verification (before its commit); re-verified with git status / git log -3.
Batches: B1 9b9a38f (PD readers; FAIL r1 5 findings → PASS r2), B2 5f12052 (IDoc; FAIL r1 4 → PASS r2), B3 711b5f0 (XI/mapping; FAIL r1 7 → PASS r2), B4 ba478a4 (helpers/logging; r1 turn limit without content defect, follow-up FAIL 1 cite → PASS; throwException has 3 variants), B5 (this commit; payload/validation/custom; FAIL r1 4 → PASS r2; pom edit by the implementer).
Checks: `mvn verify` VERIFIED_PASS per batch (target/kb-06-b<n>-verify.log) · acceptance `mvn -B -ntp -q verify` with no -D flag VERIFIED_PASS, CoverageTest run with Skipped 0 (target/kb-06-verify.log) · `-Dkb.skip=coverage` still skips it (target/kb-06-skip.log) · pages 217–1,225 words · customer-data and shortened-cite greps empty per batch · byte identity of every multi-copy stem observed by md5.
CI (GitHub Actions verify.yml): B1 39335349107, B2 39338722570, B3 39342034966, B4 39347779378 success; B5 run recorded in the next entry.
Review: kb-reviewer PASS per batch (rounds in the commit bodies); reviewer memory items 14–16 written by the architect.
Decisions: D-019 amended — `[<package-folder>/CHANGELOG.txt#L<n>]` for SAP's change-log lines (B3). Canonical copy rule for a script page: collection → generic step02.inbound.processing → first full id alphabetically (packet addendum, not a decision row).
Deviation: none (F-2 closed as the finding foresaw).
For Task 7 (contracts.md): header/property names spelt as the code does, incl. `customHeader_partnerID` (determinePartnerID) vs `customHeader_PartnerID` (determinePartnerIDForIDocInbound).
Issues: none open; ISSUE-1 → KB-13 (owner's local Phase A: Python output byte-identical to the committed catalog; Phase B after the port).
Next: KB-07 (`contracts.md`, `partner-directory.md`, `Checks.contractNames` + `ContractNamesTest`; code+content, red test first) — ready; KB-13 ready as well.

## KB-07 · 2026-10-20 · ae72101 (`feat(knowledge): contracts.md, message-path.md, partner-directory.md and contractNames check (KB-07)`; CI run 39358167204 success)
Task: `Checks.contractNames` + `ContractNamesTest` (D-021, draft — review at hand-over); `contracts.md` (registry: Headers, Exchange properties — 222 names, Delete-row notes, 1,336 words), `message-path.md` (Message path, Queues and endpoints, Synchronous path; F-5 split, 650 words), `partner-directory.md` (key scheme, value types, 21 keys, missing-key behaviour, 1,004 words); stubs replaced; `open-questions/kb-07.md` (7 bullets); `naming.md` + `{{tenant.jms.queue.receiver.specific}}`; SKILL.md routing row.
Context compacted once before the Delete-row decision; re-verified with git status / git log -3.
Red tests: ChecksTest.contractNamesReportsMissing, contractNamesPassesWhenEveryNameIsListed, contractNamesCountsOnlyContractsPage, contractNamesTreatsStubAsEmpty, ContractNamesTest.everyContractNameIsDocumented — EXECUTED_FAIL compile error (target/kb-07-red.log); round 2 contractNamesSkipsDeletePatternRows — EXECUTED_FAIL five tests (target/kb-07-red2.log); round 3 contractNamesCoversEveryCallFormAndFirstArtifact + stub token written after the code (reviewer r1), red stood in by mutation logs target/kb-07-mut1..4.log (each mutation fails a test); approved by the architect.
Inventory: 249 names → 222 after D-021 amendment (27 Content Modifier Delete patterns `$name=`/`$include=` skipped; every `$name=` target is also a plain name) — target/kb-07-inventory.txt.
Checks: `mvn -B -ntp -q verify` with no -D flag VERIFIED_PASS (target/kb-07-verify-final.log): ChecksTest 18/0, ContractNamesTest 1/0 on the real KB, Budget/Coverage/Headings/Names/Placeholders 1/0; ForbiddenValuesTest NOT_RUN (testdata/ absent). CI run 39358167204 success.
Review: reviewer FAIL r1 (four unpinned rules: stub guard, first artifact id, name filter, four call forms) → PASS r2; kb-reviewer FAIL r1 (script cites without id on a page without own artifact, Delete rows missing on name rows, incomplete rows, one-sided hop cites) → FAIL r2 (sync-only claim cited the async copy; two setters uncited) → PASS r3. Memory items 17–18 (kb-reviewer) and one reviewer line written by the architect.
Decisions: D-021 (contract names: table Name cells + Groovy literal first arguments; `$` Delete patterns skipped; contracts.md the only registry). Cite rule sharpened: a cite names the artifact the claim is about; the canonical copy only for claims true of every copy.
Deviation: F-4 (contracts.md ≤ 1,500 words, not the plan's 2,000) and F-5 (page split contracts.md + message-path.md; spec §6 layout and plan Task 7 step 5 changelog).
Follow-ups (agent/NOTES.md): `artifacts()` skips zip/package-export artifacts; property-style access and router `${header.X}` not extracted; `\bheaders.get` misses `msgheaders.get`.
Issues: none open; ISSUE-1 → KB-13 (owner's Phase A done: 224/11/91/78, SHA-256 f4680de9…).
Next: KB-13 (ISSUE-1 port of the BPM built-in catalog generator to Java; red test on a synthetic plugin fixture) — first ready row; then KB-08.

## KB-13 · 2026-10-20 · 4d7ee0f (`feat(bpm): Java generator of the BPM built-in function catalog replaces the Python tool (KB-13, ISSUE-1)`; CI run 39361570407: attempt 1 failed before any step — no runner assigned, 2 s, no logs; re-run attempt 2 failed the same way 02:56:46Z — ISSUE-2, owner-only: Actions quota/billing/runner; local verify green)
Task: ISSUE-1 option (a) — `io.poxray.bpm.BuiltinFunctionGenerator` (po-xray-core) + CLI `bpm-functions <plugins> <out.json>` (po-xray-cli) replace tools/gen_bpm_builtin_functions.py; the four .py files deleted; BuiltinFunctionCatalog javadoc/message and docs/BPM-BUILTIN-FUNCTIONS.md name the Java command.
Red tests: BuiltinFunctionGeneratorTest.renderMatchesPythonOutputByteForByte (synthetic NWDS plugins: two jars, inner DC zips, 3 functions, 3 triggers, 2 XSD + 1 SAP type, escaping incl. control chars), missingJarIsReported, summaryLineFormat; BpmFunctionsCommandTest.generatesCatalog — EXECUTED_FAIL exit 1, `cannot find symbol BuiltinFunctionGenerator` (target/kb-13-red.log); approved by the architect. Not pinned: sort tie-break on id, `C`/`M` elements, default particle position 999.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS (target/kb-13-verify-final.log: core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2; BuiltinFunctionGeneratorTest 4/0, BpmFunctionsCommandTest 2/0); `git ls-files '*.py'` empty (target/kb-13-py.txt); CLI --help smoke NOT_RUN (no packaged jar; QuarkusMainTest covers the command); byte-identity on the real NWDS plugins NOT_RUN in the cloud (owner's Phase B; reference 224/11/91/78, SHA-256 f4680de9…). CI run 39361570407 (failed before any step, twice — ISSUE-2).
Review: reviewer FAIL r1 (test javadoc named the Python file against the spec gate; ls-files evidence file missing; port fidelity line by line: no divergence) → PASS r2. The javadoc reword came after the final verify; the class re-ran 4/0 (target/kb-13-green-3.log). Two error-branch tests (missingServicecompJarIsReported, missingJarFailsWithMessage) written after the code, approved by the architect as added coverage.
Decisions: none new. Deviation: none.
Issues: ISSUE-1 waits for the owner's Phase B (`po-xray bpm-functions <plugins> <out>` vs the committed JSON); six caveats for reading a difference recorded in the issue; docs/START_PROMPT.md:6 (owner's document) still points to docs/prototype/ — hand-over note.
Next: KB-08 (naming.md, versions/README.md, glossary.md).

## KB-08 · 2026-10-20 · e145a84 (`kb: naming.md, versions/README.md, glossary.md (KB-08)`)
Context compacted and the container restarted mid-review; HANDOFF, `git status`, `git log -3` re-read before continuing (tree as left, HEAD 4d7ee0f).
Task: `naming.md` (SAP's own naming + 35 tenant placeholders grouped by prefix, D-022; `tenant.deviations.*` reserved), `versions/README.md` (current label, labels table, folder contents, new-version procedure per spec §13.1), `glossary.md` (pipeline / PO / Integration Suite terms); stubs replaced; `open-questions/kb-08.md` (TENANT_NAME source). `knowledge/sources/README.md` lines 28, 30–31 corrected (version source, `po-xray unpack` syntax).
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module (PlaceholdersTest declared 35 = used 35).
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS (target/kb-08-verify-final-r2.log: core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0); words naming 1,421 / versions 392 / glossary 799 (target/kb-08-wc-r2.txt); `_Stub,` 0. CI run 39365787352: failed before any step (3 s, no runner) — ISSUE-2 third occurrence, not re-run.
Review: kb-reviewer FAIL r1 (5 blocking: second-side parameter cites missing on five placeholders; behaviour clauses cited only by template parameters; sources/README line for "stripped"; versions 439 > 400; template claim over-broad for Step01) → PASS r2 (~60 changed cites re-checked, 0 failed; 30 unchanged spot-checked).
Decisions: D-022 (placeholder names kept, prefix grouping; cite forms `[<label>/INDEX.md#L<n>]` and `[knowledge/sources/README.md#L<n>]`) — draft, review at hand-over. Deviation: plan Task 8 step 1 grouping adjusted (changelog line).
Issues: ISSUE-2 (CI run 39361570407 for 4d7ee0f: no runner twice; owner); ISSUE-1 Phase B pending (owner).
Next: KB-10 (Pass B: claims from inputs/en/, triage table, pitfalls.md, versions/<label>/changelog.md) — ready; KB-09 owner/local.
