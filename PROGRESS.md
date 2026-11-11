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
## KB-10 B1 · 2026-10-20 · c62dba8 (`kb: claims from prior documentation and triage table (KB-10 B1)`)
Task: Pass B step 1–2 — claims from `knowledge/inputs/en/` per spec Appendix A into `knowledge/inputs/claims/confluence-2026-10.md` (1,902 claims, 39 pages) and `claims/tenant-export-review-2026-09.md` (196 claims), `claims/triage.md` (one row per claim, dispositions per plan Task 10 step 2). No fact enters a page in B1. The tenant pages and `profile-draft-<tenant>.md` are local-only (testdata/ absent): NOT_RUN, recorded for the owner.
Content task: no red test; acceptance = kb-reviewer PASS (fidelity to the inputs, ids continuous, every claim triaged, no customer data) + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS (target/kb-10-b1-verify.log: core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0; knowledge module re-run after the triage fixes target/kb-10-b1-kb-3.log exit 0); claims C 1,902, R 196; triage rows 2,098 = claims (fact→ 1,217, duplicate 467, changelog 120, rationale→ 81, procedure→Task 11 67, pitfall 60, not pipeline 45, already on 21, tenant→profile 20); CI run 39374755769 failed before any step (ISSUE-2, 5th occurrence); hygiene sweep of inputs/, claims/, sources/2026-09-current (scout): one credential alias found in the review input L110 and replaced by `<credential-alias>` (input and claim R-115); otherwise none.
Review: kb-reviewer claims files PASS r1 (110 sampled: 104 OK; 7 one-token fixes); triage FAIL r1 (kind/disposition mismatches, per-iFlow version claims dropped as duplicates, procedure+tenant collision → Addendum 2) → PASS r2 (48-row fresh sample, 0 breaks). B3 follow-ups: C-1417 target, R-090/R-091, partial duplicates C-399, C-641, C-1702, C-1902.
Decisions: D-023 (changelog cites, Version notes direction; for B2) — draft, review at hand-over. Deviation: none (sub-batches B1a–B1d are one commit).
Issues: ISSUE-2 (CI runner) open; ISSUE-1 Phase B pending.
Next: KB-10 B2 (versions/2026-09-current/changelog.md from target/kb-10 diffs + SAP CHANGELOGs; Version notes on 29 pages) — packet kb-10/page-packet-b2.md; then B3 (enrichment, pitfalls.md).

## KB-10 B2 · 2026-10-20 · 9aae29f (`kb: changelog 2026-09-current from artifact diff and SAP change logs; version notes on 29 pages (KB-10 B2)`) — context compacted during the review rounds
Task: `versions/2026-09-current/changelog.md` (Scope, SAP's change log, generic-inbound/outbound artifact differences, New artifacts (3), Unchanged artifacts (18 templates, equal Bundle-Version)) and `changelog-generic-pipelines-step02.md` (Step02 inbound/async/sync), `changelog-generic-pipelines-step04-06.md` (Step04–06) built from the dump diff of both labels (target/kb-10/, 29 pairs, 11 changed, 847 changed dump lines, 26 changed scripts) plus the 26 new `CHANGELOG.txt` lines; D-023 cites; 120 `changelog` triage claims paired (R claims annotated on the pages; 33 C claims and R-113/114/130/169 not observable). `## Version notes` rewritten on all 29 fact-base pages (fallback bullets where no room: step01 xi/idoc, step02 inbound/async); script.collection difference as a Gotchas line on scripts/logMessage.md.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-10-b2-verify.log; core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0 — testdata skips); knowledge module VERIFIED_PASS (target/kb-10-b2-kb-r6.log); H2 diff 0; HANDOFF 0 lines > 160; words changelog 1,492 / step02 1,478 / step04-06 1,070; tightest pages step04 1,492, step05 1,490, async 1,475, custom.exit 1,475; `None yet; Task 10` 0. CI run 39383547567: attempt 1 failed in 3 s with no runner (ISSUE-2, sixth occurrence); attempt 2, re-run at the owner's request, SUCCEEDED (07:15Z).
Review: kb-reviewer FAIL r1 (changelog pages 6, Version notes 6 blocking) → FAIL r2 (part A 8, part B 7 blocking: wrong "new" names, missed steps, script-line cites) → FAIL r3 (one line each: ExceptionInfo has no HTTP body; "Contract names added" lists incomplete) → PASS r4 (part A and part B, closure checks on the fixed lines).
Decisions: D-023 (label-qualified cites, Version-notes direction) — draft, review at hand-over. Deviation: F-6 (changelog split per step group; spec §6 layout, plan Task 10 changelog lines). Packet correction: 11 changed artifacts (Step01 IDoc too), 18 unchanged.
Issues: ISSUE-2 open (CI runs 4–5 recorded); ISSUE-1 Phase B pending. B3 input: undocumented-by-SAP list, not-observable list, SkipRetryProfile/SkipRetriesProfile name mismatch (Step06 readRetryHandlingFromPDOutbound#L33 vs checkExceptionType#L140).
Next: KB-10 B3a (scripts pages enrichment) — packet kb-10/page-packet-b3.md.

## KB-10 B3a · 2026-10-20 · 5e577ac (`kb: scripts pages enriched from the triaged claims; applied column filled (KB-10 B3a)`)
Task: enrichment of `knowledge/pipeline/scripts/` from the triage rows targeting scripts pages (463 rows): 18 pages enriched with 45 added statements (≈115 new cites, every one at the fact-base artifact line), `applied (B3)` filled on every row (added 45, already on page 375, n/a 21, omitted (granularity) 13, pitfall 9, open question 0; C-486 and C-469 open points carried to B3d). Pitfalls recorded for B3d: C-1343, C-895, C-1835, C-121, C-621 (order half only), C-463, C-1003, C-1008, C-1009; open questions for B3d: C-486 (shipped default of CustomXPid_ResolveVirtualReceiver), C-469 (default "true"). Ledger lines of B2 (hash 9aae29f, CI run 39383547567 attempt 2 green) ride with this commit.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-10-b3a-verify.log; core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0 — testdata skips); knowledge module VERIFIED_PASS (target/kb-10-b3a-kb-r3.log); H2 diff 0; largest scripts page determinePartnerIDForIDocInbound 1,436 words; 463 scripts rows, 0 blank cells; HANDOFF 0 lines > 160. CI run 39387441607 green on the first attempt (07:35Z) — ISSUE-2 closed.
Review: kb-reviewer FAIL r1 (part A 3 blocking: `|` split before the validator, false "no propdef entry", over-broad pitfall; part B 2 blocking: dropped exception disjunct in a router condition, rationale rewritten as behaviour) → PASS r2 (both halves; two non-blocking minors carried to B3b: setAllDefault…IDoc.md:40 "and a suffix" (2026-09-current L175), C-1000 tilde-ID clause check).
Decisions: none new. Issues: ISSUE-2 — owner's re-run of 9aae29f succeeded; close on the next first-attempt green run.
Next: KB-10 B3b (Step01/Step02 iflows pages, ≈330 rows) — packet kb-10/page-packet-b3.md.

## KB-10 B3b · 2026-10-20 · 701b1fb (`kb: Step01 and Step02 iflows pages enriched from the triaged claims; applied column filled (KB-10 B3b)`)
Task: enrichment of the five Step01/Step02 iflows pages (XI, IDoc, inbound, async, sync) from the 297 triage rows that target them, `applied (B3)` filled on every row (added 17, already on page 222, omitted (granularity) 12, open question 30, pitfall 11, budget 5); two B3a carry-overs applied (setAll…IDoc.md:40 suffix condition; C-1000 tilde-ID clause). Pitfalls for B3d: C-515, C-707, C-519, C-898, C-916, C-924, C-571, C-1065, C-1326, C-194, C-1187, C-914 (propdef default vs script fallback). Open questions for B3d: parameter defaults absent from propdef and CHANGELOG (30 rows; implied wording "set to true if …" is not a default — ruling; kb-04.md lists them); Step02 queue/address prefixes C-1042, C-1301, C-1160. Budget (pages within 30 words of 1,500): C-052, C-596 (XI, no test mode), C-907, C-1051, C-1299. Ledger lines of B3a (hash 5e577ac, CI 39387441607 green, ISSUE-2 closed) ride with this commit.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-10-b3b-verify.log; core 118/22 skipped, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0 — testdata skips); knowledge module VERIFIED_PASS (target/kb-10-b3b-kb-r2.log); H2 diff 0; pages by wc: xi 1,477, idoc 1,480, inbound 1,477, async 1,479, sync 1,469 (all ≤ 1,480); 297 rows, 0 blank cells; HANDOFF 0 lines > 160. CI run 39390121404 green on the first attempt (07:59Z).
Review: kb-reviewer FAIL r1 (part A: uncited Content-Modifier clause; three "default" rows wrongly open when CHANGELOG/propdef state the default; part B: Step02 pages clean, the same C-914 row) → PASS r2 (part A closure check; part B clean in r1).
Decisions: propdef descriptions cited as `[<id>, resource "parameters.propdef"]`, CHANGELOG lines as `[<package-folder>/CHANGELOG.txt#L<n>]` (D-019 forms already present; no new row). Issues: ISSUE-2 closed.
Next: KB-10 B3c (Step04–08, templates, registry pages, naming, glossary, versions/README; ≈300 rows) — packet kb-10/page-packet-b3.md.

## KB-10 B3c · 2026-10-20 · (hash in the next entry)
Task: enrichment of the Step04–Step08 iflows pages, the templates, the registry pages (contracts.md, partner-directory.md, message-path.md), naming.md and glossary.md from the 495 triage rows that target them; `applied (B3)` filled: added 55, already on page 299, open question 75, budget 37, pitfall 19, omitted (granularity) 10; plus 131 rows `deferred (KB-11)` and 20 `profile`.
Pitfalls for B3d (19): C-086/C-1575 (SAP_OutboundProcessingEndpoint set in Step04 and the PD read), C-600 (testMode absent from the Step01 allowedHeaderList), C-008 (ProcessDirect_Endpoint vs _EndPoint), Step06 C-1657/C-1667, Step08 C-1831/C-1741, template step-order claims C-201, C-203, C-222, C-227, C-294, C-338, C-340, C-296, C-288, C-283, C-271. Reviewer-confirmed non-pitfalls: C-492/C-352/C-359 (reuse XRD is rejected in 2a/2s; the draft was right), C-001 (ContentPackage 1.0.0 vs change-log version is SAP's own inconsistency → B3d Lessons). Candidate pitfall C-612 (testMode branches in 7 generic flows).
Budget rows 37: rationale cap (message-path, partner-directory, contracts) and full pages (step04, step05, step08 IDoc, custom.exit, naming, versions/README cap 400) — KB-11 decides whether Version notes move to the changelog pages. Blank cells 692 (120 changelog, 60 pitfall, 512 dropped) → B3d. B3b ledger lines (701b1fb, CI 39390121404 green) ride with this commit; de9f2cf (docs: turn economy, D-024) sits between.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-10-b3c-verify.log, rerun after the last cite); knowledge module VERIFIED_PASS (target/kb-10-b3c-kb-r4.log); H2 diff 0; wc: step04 1,490, contracts 1,459 (loaded set 1,772), naming 1,474, moar/step06/step08 IDoc 1,470, partner-directory 1,138, glossary 971; HANDOFF 0 lines > 160. CI run: see next entry.
Review: kb-reviewer FAIL r1 (part A: a "reached via a template" sentence without the template's cite plus an unsupported Step02-sync pairing; part B: "reads ReuseXRDEndpoint" in 2a/2s where the artifact rejects it, on message-path and partner-directory; Q03/Q04 routes cited by adapter only; C-352/C-359/C-492/C-001 wrongly pitfall, C-034/C-495 wrongly open) → r2 part A PASS, part B FAIL (one missing XI cite on the InboundQueue row) → r3 PASS. Lessons → kb-reviewer memory 35–36 and the kb-author self-check list (uncommitted in .claude/, owner decides).
Decisions: D-024 (turn economy, committed separately as de9f2cf). Issues: none new.
Next: KB-10 B3d — pitfalls.md, open-questions/kb-10.md, the remaining 692 cells, triage Summary `applied` table; then KB-10 done, KB-11 ready.

## KB-10 B3d · 2026-10-20 · (hash in the next entry) — context compacted before B3c closure; re-verified with git status/log
Task: pitfalls.md rewritten from the stub (H2 Draft versus artifact → pointer, Undocumented by SAP 13 bullets, Announced not observable 5, Lessons 15); pitfalls-drafts.md (32 contradiction bullets grouped scripts/Step01/Step02/Step04–08/templates/registry, each with the version-change answer checked in 2026-09-current); open-questions/kb-10.md (23 bullets); kb-04.md bullet closed by generic-outbound/CHANGELOG.txt#L7; changelog.md line 86 cite corrected (#L63/#L65). The last 692 `applied (B3)` cells filled: n/a 512 (dropped rows) + 4, changelog (done (B2)) 82, changelog (not observable (B2)) 38, 60 former pitfall rows → pitfall 21, open question 7, already on page 28, n/a 4; R-110 and C-612 → pitfall. Summary `applied (B3)` table: already on page 924, n/a 537, deferred (KB-11) 131, added 118, open question 110, changelog done 82, pitfall 62, budget 42, changelog not observable 38, omitted 34, profile 20 = 2,098.
Deviation: F-7 (pitfalls split into pitfalls-drafts.md; spec §6 changelog line). B3c ledger: hash c98e0f7, CI 39399085902 green; de9f2cf (docs D-024) CI 39398446599 green; owner commits dc3644e (gated hook, author self-check) and a6bfbef (SessionStart installer) pulled.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-10-b3d-verify.log; core 118/22, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0); knowledge module VERIFIED_PASS (target/kb-10-b3d-kb-r3.log); wc: pitfalls 1,469, pitfalls-drafts 1,407, kb-10 1,463, kb-04 1,480, changelog 1,492; no claim ids or tenant tokens on the pages; blank cells 0, 2,163 lines; HANDOFF 0 lines > 160. CI run: see next entry.
Review: kb-reviewer r1 FAIL (part A 5: two wrong "version change? no" answers, p2p.to.idoc step order, SAP_Sender conditions, TenantName-in-Step02 missing from Undocumented; part B 4: open questions answered by propdef descriptions, CHANGELOG#L6 and checkExceptionType) → r2 FAIL (part A 3 new: commented-out #L29 cite, two multi-iFlow claims with one cite; part B 1: Step06 exception-subprocess route answers the skip-retry question) → r3 closure PASS. Lessons → kb-reviewer memory 37–38 and the kb-author self-check (dc3644e). Both r2 reviewers and the writer hit their turn limits once each; running hand-back files kept the work (WORKFLOW §10).
Follow-ups: a Java cite checker (`CiteCheckTest` in iflow-forge-knowledge resolving every bracket cite against the sources) proposed by the writer → BACKLOG candidate for KB-11/KB-13; local-only claims (tenant) and testdata/pipeline/profile-draft-<tenant>.md stay NOT_RUN in the cloud (owner, local). Windows clone note in NOTES.md (core.longpaths).
Decisions: none new (D-024 in de9f2cf). Issues: none new. KB-10 done; KB-11 ready.
Next: KB-11 — scenario attribute set (draft), scenario-contribution.md, migration procedures, overview.md, final SKILL.md; the 131 deferred rows; budget decision on Version notes vs changelog pages.

## KB-11 C1+C2 · 2026-10-20 · (hash in the next entry) — context compacted before C1 started; re-verified with git status/log
Task: C1 scenario-contribution.md (D-025 attribute table with "selects" cites, contributes table 18 rows, must not touch, naming, 7 steps) and overview.md (purpose, Mermaid sequenceDiagram, generic iFlows with short names, runtime variants) replace the stubs; open-questions/kb-11.md new (5 bullets). C2 migration/po-ico-to-pipeline.md (7 steps, ICO fields → attributes table with defaults), migration/is-iflow-to-pipeline.md (dump lines → attributes), migration/proposal-template.md (header block, 7 sections); variants.md not needed. naming.md used-by lists extended (27 prose words trimmed).
Ledger: B3d hash f8a2be4, CI 39420055798 green (a6bfbef 39405597120, dc3644e 39403826729 green).
Triage: 64 overview rows (29 added, 14 already, 15 omitted, 6 open question) and 67 procedure rows (26 profile, 16 already, 12 omitted, 6 n/a, 6 added, 1 open question) closed; deferred (KB-11) 131 → 0; Summary table recounted.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: knowledge module VERIFIED_PASS (target/kb-11-c1-kb-r3.log, target/kb-11-c2-kb-r2.log, 25 tests, ForbiddenValuesTest skipped NOT_RUN); checker counts scenario-contribution 1,478, overview 1,096, naming 1,474, po-ico 1,470, is-iflow 1,031, proposal 636; loaded set 4,336; `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-11-c12-verify.log; core 118/22, cli 12/1, knowledge 25/1, mcp 8/1, native 2/0); `_Stub,` only conversion/README.md; tenant-name grep 0.
Review: C1 kb-reviewer r1 FAIL 10 (Step01 XI BE fork at step 41, reuse-XRD path of ReceiverNotDetermined, Q04 writers, adapter cites on binding rows, CustomXType prefix step, Mermaid names, kb-11.md residual question); r2 FAIL 1 (a cited row deleted for budget); r3 PASS. C2 r1 FAIL 9+3 (listener-side cites, gateway-vs-read cites, defaults for every "human decides", multicast = receiver_count n, EOIO alignment, custom XID decoupled-only); r2 PASS. Reviewer memory items 39–49.
Decisions: D-025 draft (attribute set) amended after C1 evidence (qos ALO, custom_exit pre+post, inbound_conversion; EOIO no artifact; error_handling custom is tenant-level); D-026 (conversion/README.md module table as proposals). Issues: none new.
Next: KB-11 C3 — conversion/README.md and final SKILL.md (packet scratchpad kb-11/page-packet-c3.md); then KB-11 done, KB-12 pending (owner KB-00b).

## KB-11 C3 · 2026-10-20 · (hash in the next entry)
Task: conversion/README.md replaces the stub (purpose, recipe page scheme and H2 contract, placement rules with four cites, standard adapter modules table as proposals per D-026 — 7 rows proposal, MessageSplitterBean unknown —, mapping programs table, adding a recipe = spec §8); SKILL.md final (loading table 20 rows all resolving, Conventions 3 lines, Self-improvement 3 lines, Start here 2 lines; front matter unchanged). No `_Stub,` marker left under knowledge/pipeline.
Ledger: C1+C2 hash c5626fa, CI 39431540903 green.
Content task: no red test; acceptance = kb-reviewer PASS + green knowledge module.
Checks: knowledge module VERIFIED_PASS (target/kb-11-c3-kb-r2.log, exit 0); checker counts README 989, SKILL.md 450; loaded set 4,470 by wc (limit 5,000); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-11-c3-verify-r2.log; core 118/22, cli 12/1, knowledge 25/1 ForbiddenValuesTest, mcp 8/1, native 2/0); `_Stub,` 0; tenant-name grep 0.
Review: kb-reviewer r1 README FAIL 3 (Step03 Sender adapter cite missing behind "listens on"; placement rule wrong for integrated-sync — Step02 sync has no conversion call, the Step01 sync copy's "Call inbound conversion" has it; "exits are copies of the custom exit template" needed the template Sender and one caller cite per exit kind), SKILL.md PASS; r2 PASS (closure on the three items, row set unchanged). Reviewer memory items 50–51.
Decisions: D-026 applied. Issues: none new. KB-11 done; KB-12 pending (owner KB-00b); KB-09 owner/local.
Follow-ups: Java `CiteCheckTest` (BACKLOG candidate); recipe-page contract not yet checked by iflow-forge-knowledge; naming.md used-by list of {{tenant.endpoint.inbound.conversion}} does not name conversion/README.md (naming.md at 1,474 words).
Next: KB-GATE if every row of the phase is done or owner-blocked; else IDLE_NO_READY_TASK (KB-12 needs KB-00b, KB-09 local) — see BACKLOG.

## KB-12a · 2026-10-20 · (hash in the next entry)
Task: D-027 split of KB-12 — the fixture half. benchmark/sample-ico.json (synthetic ICO in po-xray output format: SND_SYS XI sender with AF_Modules/MessageTransformBean, one conditional EO receiver RCV_SYS over IDoc, one operation mapping; literals mirrored from IntegrationReader l.268/334/366/416/437 and Conditions), BenchmarkFixtureTest (7 tests: strict parse with FAIL_ON_UNKNOWN_PROPERTIES, shape a–e, no customer fragment), benchmark/expected-proposal.md by following migration/po-ico-to-pipeline.md steps 1–3 on the fixture (eleven attribute values with sources; 19 step, 10 script, 6 adapter cites).
Ledger: KB-11 C3 hash 0ac772e, CI 39435006417 green.
Red test: BenchmarkFixtureTest EXECUTED_FAIL 7/7 on the missing file (target/kb-12a-red.log); green after the fixture (target/kb-12a-green.log, 7/7); module 32 tests 0 failures 1 skipped (ForbiddenValuesTest).
Checks: `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-12a-verify.log, implementer; verifier rerun before commit exit 0 target/kb-12a-verify-r2.log: core 118/22, cli 12/1, knowledge 32/1 ForbiddenValuesTest, mcp 8/1, native 2/0); knowledge module VERIFIED_PASS (target/kb-12a-kb-r2.log); fixture grep <tenant>|http|.com|.cz|.corp = 0; expected-proposal 993 words by the checker.
Review: reviewer PASS (test real — each of 7 tests fails on a wrong fixture; departures EQ operator, multiplicity "1", CallSapAdapter justified by the reader and its tests); kb-reviewer r1 FAIL 3 (an "absent keys" row is not a contributes row; the copy's iFlow id is given by the template; Step01 XI "Set SAP headers" cite behind the partner-ID values), r2 PASS (closure on the three items + Q4 default). Reviewer memory 52–55.
Findings: the rehearsal surfaced 16 procedure gaps (field-name text, noReceiverBehaviour mapping, namespaces for condition prefixes, partner-ID derivation, mapping × receiver_type, multiplicity precedence, A:43 vs conversion/README.md, missing SC rows for xpath / IDoc receiver / async Interface Service, naming of the copy, source format) → KB-11b (ready; packet scratchpad kb-11b/page-packet.md). Not a deviation: the spec §8 loop working as designed.
Decisions: D-027. Issues: none new. KB-12a done; KB-12b pending (owner KB-00b).
Next: KB-11b — kb-author on the four procedure pages + expected-proposal open questions; kb-review; verifier; commit.

## KB-11b · 2026-10-20 · (hash in the next entry) — context compacted mid-review; re-verified with git status/log
Task: the 16 procedure findings of the KB-12a rehearsal applied to migration/po-ico-to-pipeline.md (A), proposal-template.md, scenario-contribution.md (SC), conversion/README.md; new migration/variants.md (V: one H2 per branch — runtime, partner ID and sender alias by sender type, no receiver found per gateway copy, mapping × receiver type, conversion placement, defaults in the form "human decides (default X)"); SKILL.md row for V; benchmark/expected-proposal.md re-derived (inbound_conversion = custom, Step03 copy, IDoc rows, xpath Target row with the Receivers namespace); open-questions/kb-11b.md.
Ledger: KB-12a hash 3bb4a67, CI 39440147847 green.
Checks: knowledge module VERIFIED_PASS (target/kb-11b-kb-r3.log, 32/0/0/1); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-11b-verify.log; core 118/22, cli 12/1, mcp 8/1, native 2/0, knowledge 32/1); words by the checker: SC 1,477, A 1,394, V 906, template 626, README 981, proposal 860, SKILL 447; loaded set 4,478; H2 lines unchanged.
Review: kb-reviewer set A (A, V, template) r1 FAIL 4 (Step02 sync "Not found type?" has no Ignore route; an uncited reuse-xrd sentence; "(no default)" form; XI-only partner-ID cites under a rule for both sender types), r2 PASS; set B (SC, README, proposal, open questions, SKILL) r1 FAIL 2 ("keys must be absent" row cited by gateways only; ns0 clash — the Receivers result is in http://sap.com/xi/XI/System), r2 PASS. Reviewer memory 56–63.
Findings: none new beyond the 16 (all closed; the residue is in open-questions/kb-11b.md). Not a deviation.
Decisions: none new. Issues: none new. KB-11b done.
Next: no ready row — KB-12b waits for KB-00b (owner), KB-GATE for KB-09 (owner, local) and KB-12b → IDLE_NO_READY_TASK. A real migration starts at SKILL.md "Start here" → migration/po-ico-to-pipeline.md, branches in migration/variants.md.

## KB-00b (draft, owner approves) · 2026-10-20 · (hash in the next entry)
Task: owner asked in the session for a draft of the benchmark questions (D-028). benchmark/questions.md = the 20 questions the benchmark session reads (groups: message path 3, iFlow roles 3, routing headers and partner IDs 3, async XI → IDoc contribution 4, sync 2, mapping 3, versions and conventions 2); benchmark/expected-answers-1.md (items 1–13) and -2.md (14–20) = the owner's key: Q, Expected (every fact derived from and cited to the 2026-06-trial artifacts, not the pages), Page. questions.md does not name the key; a transcript that reads it voids the run.
Ledger: KB-11b hash a51989f, CI 39446947709 green.
Checks: knowledge module VERIFIED_PASS (target/kb-00b-kb-r4.log, 32/0/0/1); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-00b-verify-r2.log; baseline counts); words: key 1,057 + 511 by the checker, questions 331; Q lines identical in both (diff 0); no H2.
Review: kb-reviewer r1 FAIL 12 (listener cites on every ProcessDirect hop; script cites on the line that does what the sentence says; "no default" unsupported since parameters.prop is never staged; the key named on the questions page), r2 FAIL 6 (Page lines cut for budget → key split in two files), r3 PASS. Reviewer memory 64–71.
Findings: five page gaps surfaced by deriving the key from the artifacts + the stale SKILL.md benchmark row → BACKLOG KB-11c (ready). Not a deviation (spec §9.5 "with expected answers" read as two files, D-028).
Decisions: D-028. Issues: none new. KB-00b in-progress until the owner approves the questions; KB-12b then ready.
Next: KB-11c (kb-author, page packet from the BACKLOG row) — then IDLE_NO_READY_TASK unless the owner approves KB-00b.

## KB-11c · 2026-10-20 · (hash in the next entry)
Task: the six page gaps found while deriving the benchmark key (BACKLOG row 32): overview.md:39 async XI → IDoc chain cited caller and listener per hop; message-path.md:36 synchronous return leg (Step02 sync steps 56/59, Step07 sync template listener and its step 14 without a message flow, InboundProcessingSync back to Step01 XI step 43); "default X01" → "implied by its description" on message-path.md:30 and partner-directory.md:24; partner-directory.md:51 no default queue prefix stated (sources/README.md#L14) and the "Which queue?" fallback on both Step01 copies; migration/variants.md:40 and open-questions/kb-11b.md:5 note o2m.to.idoc of 2026-09-current (D-023 form, trial default kept); SKILL.md:33 routes only to the benchmark inputs.
Ledger: KB-00b hash 6d69048, CI 39463123245 green.
Checks: knowledge module VERIFIED_PASS (target/kb-11c-kb-r2.log, 32/0/0/1); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-11c-verify-r2.log; baseline counts); loaded set 4,565 by the checker (SKILL 443, overview 1,181, contracts 1,463, SC 1,478); H2 lines unchanged (diff grep 0).
Review: kb-reviewer r1 FAIL 1 — my packet's SKILL.md row named the key and expected-proposal.md, making the benchmark open-book (D-028); fixed to the inputs only; r2 PASS. D-028 amended (expected-proposal.md also voids the run; SKILL routes only to inputs). Reviewer memory 72–75.
Findings: none new. Not a deviation. Decisions: D-028 amended. Issues: none new. KB-11c done.
Next: no ready row — KB-00b awaits the owner's approval of questions.md, then KB-12b; KB-GATE after KB-09 (owner, local) and KB-12b → IDLE_NO_READY_TASK.

## KB-00b owner review · 2026-10-20 · (hash in the next entry)
Task: the owner answered Q1–Q20 in the session; the key corrected (Q4 both Step01 copies reject EOIO — XI steps 50/51 and 24/25, IDoc steps 23/49/50, generic-inbound/CHANGELOG#L18; Q2 sync via p2p.to.xi step 13 Request Reply to Step08 XI "Pipeline", six channel QoS values descriptive only; Q3 queue counts cited to the eight JMS adapters, SAP's recommendation `[external …]` + `[owner …]`; Q8 uniqueness rule `[owner, 2026-10-20]`, determinePartnerID#L126; Q11 PIPX01/PIPQ01 external; Q15 Step07 sync "mapping and conversion" processes, Step01 sync copy for OTHER senders only, listener cite; Q19 2026-09 changes incl. generic-pipelines#L8, templates#L9); Q7 and Q19 reworded in questions.md and key. D-029 (external and owner cite forms). Customer name neutralised in spec, plan, backlog, decisions, PROGRESS, docs/HANDOFF.md; ISSUE-3 for the rest (src/, OUTPUT-MODEL). Owner's migration-flow statements → docs/inputs/2026-10-20-owner-migration-flow.md. Rows KB-11d (ready), FIX-03 (blocked, ISSUE-3) added; KB-12b ready.
Ledger: KB-11c hash 721597b, CI 39465906270 green.
Where the artifacts differ from the owner: Q15 (conversion sits in the Step01 sync copy for OTHER senders and in Step07 sync's "Mapping and conversion" processes, not all in one iFlow); Q4 (artifact text "not yet supported in the Pipeline for Cloud Integration"); Q2 (HandledByIflow proves nothing about sync; the reply rests on the Request Reply caller).
Checks: knowledge module VERIFIED_PASS (target/kb-00b-kb-r5/r6/r7.log, 32/0/0/1); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-00b-verify-r3.log, -r4.log; baseline counts); checker words EA1 1,444, EA2 757, Q 337; Q lines identical (diff empty); questions.md names no key; customer-name grep 0 in markdown, 2 in src/ (ISSUE-3).
Review: kb-reviewer owner-round r1 FAIL 5 (sync reply inferred from channel QoS; queue counts on external cites only; Step07 sync local-process names; missing listener cite; two 2026-09 error-handling lines), r2 PASS; two observations applied (r7). Reviewer memory 76–79.
Findings: none. Not a deviation. Decisions: D-029 added, D-028 unchanged. Issues: ISSUE-3 (owner). KB-00b done.
Next: KB-12b ready — run the benchmark in a fresh session with only the skill (plan Task 12 step 3), judge against the key, results-2026-10-20.md; KB-11d ready after it.

## KB-12b · 2026-10-20 · (hash in the next entry)
Task: benchmark run (plan Task 12 step 3, GATE check 5): fresh Opus 5.5 agent, skill only, no key read (transcript grep 0, D-028; hook-injected CLAUDE.md/HANDOFF disclosed on the page); 20 questions 10 correct / 9 partial / 1 wrong (Q10) + proposal judged by the kb-reviewer; results-2026-10-20.md written; nine page fixes (static RD optional only decoupled; sync reply via p2p.to.xi step 13; prefix default; queue counts; SAP_Sender #L476; alternative-partner uniqueness; readBypassOptionFromPD#L22; Interface/Name → SAP_ReceiverInterface + lookup order; empty header counts as present #L32/#L61).
Ledger: KB-00b owner review hash 287527b, CI 39487719616 green.
Reruns: seven questions against the fixed pages 4 correct / 2 partial / 1 wrong; exposed contracts.md:12 (templates never set the namespace) and ten dangling "see <page>" pointers → fixed (r4); Q10 rerun against r4 pages correct. open-questions/kb-12b.md: missing Binary receiverDetermination entry (no check in Step04 36→37, 2a 53→54, 2s 40→41).
Checks: knowledge module VERIFIED_PASS (target/kb-12b-kb-r1..r7.log, 32/0/0/1); `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/kb-12b-verify-r2.log, before the r7 one-line page edit; baseline counts; knowledge module re-run green r7); checker words (author; BudgetTest green): SC 1,479, contracts 1,478, Step04 1,479, 2a 1,484, 2s 1,473, 1X 1,485, 2i 1,484, Step05 1,498, 1I 1,480, pitfalls 1,477, po-ico 1,480, results 1,480, open-questions/kb-12b 156; loaded set 4,659 (verifier `wc -w` 4,640 — the checker also counts `—`/`→` tokens); H2 lines unchanged; customer grep 0 on changed files.
Review: kb-reviewer r1 FAIL 2 (SC:54 implied sync optional; pitfalls L42 CRETIM/CREDAT), r2 FAIL 1 (SC:54 lost #L22/#L30 cites), r3 PASS; after the rerun r4 FAIL 1 (SC:62 merged the two precedence halves), r5 PASS; r6 (Rerun section) FAIL 1 (L79 audit source), r7 PASS. Reviewer memory 80–87.
Findings: none. Not a deviation. Decisions: none new (D-028, D-029 applied). Issues: none new. KB-12b done pending the owner's judgement (GATE check 5: gate row stays in-progress).
Next: MIG-00 spec (docs/superpowers/specs/<date>-migration-front-end-design.md, draft) per the owner's go-ahead; then KB-11e, KB-11d remainder.

## MIG-00 · 2026-10-20 · (hash in the next entry)
Task: design spec docs/superpowers/specs/2026-10-20-migration-front-end-design.md (draft — review at hand-over): skill `sap-po-migration` at skills/ with pointer files for Claude Code, Codex, Gemini CLI; inputs from po-xray dumps; seven phases (intake, analysis, question round one at a time, plan, scenario specification, artifacts placeholder, retrospective); learning as tenant profile + scenario library under gitignored local/, generic pattern catalogue patterns/ with cite `[migration M-<n>, <date>]` (D-030 draft); field requests requests/REQ-<n>.md on field/<date> branches; two new runner commands (attributes, bpm-inventory); decisions P-1…P-12; owner questions §13.
Ledger: KB-12b hash 06b08eb, CI 39501020052 green.
Changed: the spec (new), docs/DECISIONS.md (D-030: three cite forms), agent/BACKLOG.md (MIG-00 done; rows MIG-01…MIG-08 pending with depends-on, MIG-03 also on KB-11e), agent/HANDOFF.md, PROGRESS.md.
Checks: mvn verify NOT_RUN (documentation only; no file under src/, knowledge/ or the Maven modules changed) · customer grep 0 on the spec.
Review: reviewer r1 FAIL 7 (question-round escape; silent testdata layout change; scenario file collision and missing phase outputs; "never skips" vs the stops; untestable ready criteria; row gaps and depends-on; §12 missing expensive-to-reverse decisions) → fixed; r2 FAIL 6 (inconsistencies left by the fixes: plan "open questions" section, profile input paths, scenario file name, check scope, BACKLOG rows vs §11, MIG-08 dependency) → fixed; r3 PASS (two non-blocking row observations applied: MIG-04 plan column §7.3–7.4, MIG-08 kind config).
Findings: none. Not a deviation (a design row produces a spec; WORKFLOW §6 L137). Decisions: D-030 (draft), P-1…P-12 in the spec (draft). Issues: none new. MIG-00 done.
Next: MIG-01 ready (skill skeleton, pointer files, SkillMirrorTest, local/ gitignore) — code+content; then KB-11e, KB-11d when the owner's questions do not block.

## MIG-01 · 2026-10-20 · (hash in the next entry)
Test first: SkillMirrorTest (12: correctPairsProduceNoMessages, pointerFrontmatterThatDiffersFailsNamingFileAndLine, …, repositorySkillsMirror), ChecksTest +8 (budgetPages…, forbiddenIn…, newRootsAreSiblingsOfTheKnowledgeRootUnderTheRepositoryRoot), BudgetTest +1, ForbiddenValuesTest +1; red observed as 42 compile errors on the missing API (target/mig-01-red.log, exit 1)
Changed: iflow-forge-knowledge (Checks: skillMirror, budgetPages, forbiddenIn; KnowledgeBase: repoRoot, skills, patterns, requests), skills/sap-po-migration/ (SKILL.md, 7 reference stubs, 4 template stubs), patterns/README.md, requests/README.md, pointer files .claude/skills/sap-po-migration, .agents/skills/{sap-po-migration,sap-is-pipeline}, .gitignore (local/), agent/NOTES.md (skill portability; knowledge module roots), spec §6.1 changelog (Gemini reads .agents/skills/), BACKLOG, HANDOFF
Ledger: MIG-00 hash 8401615, CI 39503180896 green.
Checks: knowledge module VERIFIED_PASS (target/mig-01-green.log; knowledge 54/0/0/2 — the two skips are ForbiddenValuesTest with kb.forbidden unset, as designed; re-run after the r2 page fix target/mig-01-kb-r2.log) · `mvn -B -ntp -q verify` VERIFIED_PASS exit 0 (target/mig-01-verify.log, -r2.log after the page fix; knowledge 54/0/0/2, cli 12/0/0/1, core 118/0/0/22, mcp 8/0/0/1, native 2/0/0/0) · SKILL.md 963 words (wc -w; author), patterns/README 288, requests/README 324, stubs 0; customer grep 0 on the new files; git status shows nothing under local/ or testdata/.
Review: reviewer PASS (gap noted: canonical without frontmatter untested → MIG-04 row); kb-reviewer r1 FAIL 2 (SKILL.md:68 commit split vs §9; :37 "if any") → fixed; r2 PASS. Reviewer memory 88.
Findings: none. Not a deviation. Decisions: none new (P-1, P-9, P-10 applied; D-030 unchanged). Issues: none new. MIG-01 done.
Next: MIG-02 ready (helpers `po-xray attributes`, `po-xray bpm-inventory` on committed synthetic fixtures under skills/sap-po-migration/fixtures/); KB-11e, KB-11d ready.

## MIG-02 · 2026-10-20 · (hash in the next entry)
Test first: ScenarioAttributesTest (40→47), BpmInventoryTest (12→13), AttributesCommandTest (5), BpmInventoryCommandTest (5); red as compile errors (target/mig-02-red.log); §8 tests added after the code (green first run); §9 test red (target/mig-02-red3.log) then green.
Changed: io.poxray.migration.{ScenarioAttributes,BpmInventory}; io.poxray.model.{AttributesReport,BpmInventoryReport}; AttributesCommand, BpmInventoryCommand, PoXrayCommand; NativeReflection; docs/OUTPUT-MODEL.md "Migration helpers"; fixtures skills/sap-po-migration/fixtures/ (synthetic).
Rules: D-025 attributes with the source field or `?` + candidates + question; adapter class on either part of transport/message (SOAP/XI → XI); sender MessageTransformBean → inbound_conversion custom, not an exit; `custom` (receiver and interface determination), `ordered`, `ALO` never derived; error_handling always `?` (tenant-level).
Checks: mvn verify exit 0 (verifier target/mig-02-verify-r4.log); surefire core 178/0/0/22 (was 118), cli 22/0/0/1 (was 12), native 2/0/0/0, knowledge 54/0/0/2; CLI smoke exit 0 on both fixtures, exit 1 on a missing path (target/mig-02-smoke-*.md); sample-ico.json: custom_exit none, inbound_conversion custom (= expected-proposal.md); forbidden-pattern grep 0.
Review: reviewer r1 PASS, r2 PASS on the delta (r1 PASS with observations → mapping one-type needs name+namespace, decided §9, red test r3 then green; nits taken; not taken: untested honesty branches, null-name NPE in BpmInventory — non-blocking)
Findings: the §6.3 rules were under-specified for Java-PO XI channels (`SOAP/XI`) and for MessageTransformBean; decided in the acceptance spec §8, documented in OUTPUT-MODEL — no spec text contradicted, no deviation.
Ledger: MIG-01 hash 144bcc5, CI 39508095025 green.
Next: MIG-03 needs KB-11e (versions/features.md) → KB-11e next, then MIG-03.

## KB-11e · 2026-10-20 · (hash in the next entry)
Content task (no red test): acceptance = kb-reviewer PASS + green knowledge module.
Changed: knowledge/pipeline/versions/features.md (new: features by package from the four CHANGELOG.txt, every dated entry of both labels, cites [2026-09-current/<pkg>/CHANGELOG.txt#L<n>], one 2026-06-trial-only line; "The tenant's installed version"); SKILL.md routing row; versions/README.md pointer; naming.md placeholder {{tenant.pipeline.version.<package-folder>}}; po-ico-to-pipeline.md and is-iflow-to-pipeline.md Inputs bullet; BACKLOG, HANDOFF.
Checks: knowledge module exit 0 (target/kb-11e-kb-r1.log, -r2.log; PlaceholdersTest 1/0/0/0, BudgetTest 2/0/0/0, SkillMirrorTest 12/0/0/0); mvn verify exit 0 (verifier target/kb-11e-verify-r2.log after r3; core 178/0/0/22, cli 22/0/0/1, native 2/0/0/0, knowledge 54/0/0/2); tokens features 1,476, naming 1,478, po-ico 1,478, is-iflow 1,042, SKILL 458, loaded set 4,674/5,000; 109 distinct CHANGELOG cites; forbidden-pattern grep 0 (ForbiddenValuesTest NOT_RUN, kb.forbidden unset).
Review: kb-reviewer r1 FAIL 3 (features.md:53 "fixes:" over a non-fix clause; :49 and :26 a flow prefix carried into unscoped clauses) → fixed, sweep fixed :38, :52, :65 too; r2 PASS (budget confirmed by the verifier: 1,476). Reviewer memory 89–93.
Findings: one row per bullet did not fit (1,994 tokens) → one row per change-log entry, every bullet cited (39 rows); a page split rejected. The `<package-folder>` placeholder was invisible to PlaceholdersTest → four concrete underscore names. Spec/plan text unchanged; not a deviation (the row said "one row per feature or behaviour change" — amended to "per entry" on the BACKLOG row). Bullets needing interpretation left as SAP wrote them ("reworked"; DLQ-instead-of-endless-retry stays a fix).
Ledger: MIG-02 hash 65fc51b, CI 39513402531 green.
Next: MIG-03 (references intake.md, attributes.md, bpm.md) now ready; KB-11d ready.
## MIG-03 · 2026-10-20 · (hash in the next entry)
Content task (no red test): acceptance = kb-reviewer PASS (every pipeline claim points to a page) + green knowledge module.
Changed: skills/sap-po-migration/references/intake.md, attributes.md, bpm.md (stubs → procedures for phases 1–2: inputs and M-<n> allocation, attribute derivation from `po-xray attributes`, BPM step classification from `po-xray bpm-inventory`, human-task stop P-5); docs/findings/F-8.md; DECISIONS D-031, D-032; BACKLOG (+MIG-02b), HANDOFF.
Checks: knowledge module exit 0 (target/mig-03-kb-r3.log); mvn verify exit 0 (verifier target/mig-03-verify-r2.log; core 178/0/0/22, cli 22/0/0/1, native 2/0/0/0, knowledge 54/0/0/2, mcp 8/0/0/1); tokens intake 1,180, attributes 1,274, bpm 1,195 (SKILL 965); 21 distinct `[page:` cites, 0 missing file or heading (target/mig-03-cites-r2.txt); stub H2 outline unchanged; forbidden-pattern and agent-syntax greps 0.
Review: kb-reviewer r1 FAIL 3 (intake.md:44 exit-1 rule stated for both helpers; attributes.md:17/:27/:28 never-derived set overreached, known disagreement not stated; bpm.md:31 startEvent not one of the four classes) → kb-author r2 fixed all plus 5 non-blocking items → r2 PASS; two wording fixes after PASS (attributes.md:7 "read or parsed", :27–28 "until MIG-02b"), kb module r3 exit 0.
Findings: F-8 — the helper derives custom_exit pre / inbound_conversion from sender modules where po-ico-to-pipeline.md:44 and variants.md:50 call it a human decision → D-032 (sender module other than MessageTransformBean → both attributes `?`), follow-up row MIG-02b; attributes.md states both sources meanwhile. D-031 records the skill conventions (migrations.md line, `?` matches any candidate, "unknown" → open list, open-list line, classification rules proposal). Not a spec deviation (§6.3 unchanged).
Ledger: KB-11e hash 84ba389, CI 39515866770 green.
Next: MIG-02b (D-032 red test, helper rule) ready; then MIG-04 (questions.md, plan.md, template question.md, Java cite check) and KB-11d.

## MIG-02b · 2026-10-21 · (hash in the next entry)
Test first (two red rounds: D-032, then D-033 after the kb-reviewer): ScenarioAttributesTest — 3 renamed (senderModuleOtherThanMtbMakesCustomExitAQuestionBetweenNoneAndPre, senderAndReceiverModulesMakeCustomExitAQuestionBetweenPostAndPrePlusPost, messageTransformBeanWithAnotherSenderModuleKeepsCustomConversionAndAsksAboutTheExit), 3 added (…InboundConversionAQuestionEvenForAnXiSender, …ReplacesTheGenericConversionQuestionForAnOtherSender, senderModuleWithAnUnresolvedReceiverChannelLeavesAllFourExitCandidates), markdown and JSON round-trip tests adjusted; AttributesCommandTest fixture (c) custom_exit `?`. Red observed: core 181/8, CLI 22/1 (target/mig-02b-red.log, -red-cli.log); round 2: receiver module → `?` {none, post} (3 renamed incl. MTB on a receiver, 1 added, fixture (b) `?`), core 182/4, CLI 22/1 (target/mig-02b-red2.log).
Changed: po-xray-core ScenarioAttributes (D-032: a sender module other than MessageTransformBean → `custom_exit` `?` {none, pre} / {post, pre+post} / all four with an unresolved receiver channel, `inbound_conversion` `?` {none, custom}, one shared question "sender module <name>: conversion to XML (Step03, default custom) or pre-processing exit"; MTB stays custom; D-033 after the kb-reviewer: a receiver module → `?` {none, post} "outbound conversion (Step07) or post-processing exit", both → all four and both questions); docs/OUTPUT-MODEL.md "Rules in short"; knowledge/pipeline/migration/po-ico-to-pipeline.md:44; skills/sap-po-migration/references/attributes.md:27–28 (known-disagreement note dropped); docs/findings/F-8.md closed.
Checks: mvn verify exit 0 (round 2 target/mig-02b-verify2.log, final verifier run target/mig-02b-verify-v2.log; core 182/0/0/22, cli 22/0/0/1, native 2/0/0/0, mcp 8/0/0/1, knowledge 54/0/0/2); CLI smoke on the fixtures exit 0 (target/mig-02b-smoke2-attr.md: (b) custom_exit `? (none, post)`, (c) `? (none, pre)` and inbound_conversion `? (none, custom)`); tokens po-ico 1,477 (was 1,478), attributes.md 1,298; customer-data grep 0.
Review: reviewer r1 PASS, r2 PASS after the evidence files (status/stat/cli diff) — observations: S + receiver module + unresolved receiver → {post, pre+post}, not spelled out in OUTPUT-MODEL; IDOC/open sender type with S untested; unresolved sender + receiver module → all four and the three-part question untested, accepted); kb-reviewer r1 FAIL 2 (po-ico:44 did not exclude adapter beans; "receiver module → post" contradicts conversion/README.md "Rules" and variants.md:46) → D-033, second red/green round, page round 2 → kb-reviewer r2 FAIL 1 (po-ico:59 cut dropped the spec §8 condition "about the pipeline") → restored, r3 PASS. Decision in the task: an open attribute row is an open-list item of type blocking (attributes.md:53), not conversion.
Ledger: MIG-03 hash 6218ac9, CI 39517967726 green.
Next: MIG-04 (questions.md, plan.md, template question.md, Java cite check) ready; KB-11d ready.

## MIG-04 · 2026-10-21 · (hash in the next entry)
Test first: CitesTest (real repository: every `[page: …#heading]` and `[pattern: <id>]` under skills/ and patterns/ resolves), ChecksTest +7 synthetic cases for `Checks.cites`, SkillMirrorTest +1 "canonical without frontmatter" (reviewer MIG-01 gap). Red observed: testCompile 10× "cannot find symbol cites" (target/mig-04-red.log); the SkillMirror case was already green (regression guard).
Changed: iflow-forge-knowledge Checks.cites (page regex, heading = exact H2/H3 text, pattern → patterns/<id>.md, form descriptions with `<`, `>`, `…` skipped, messages `<file>:<line> — …`); skills/sap-po-migration/references/questions.md, plan.md, templates/question.md (stubs → procedures for phases 3–4); DECISIONS D-034 (question-round and plan bookkeeping, incl. the conventions the kb-author fixed); agent/NOTES.md knowledge-module line.
Checks: knowledge module exit 0 (target/mig-04-kb-green.log; 54 → 63 tests, 56 real cites resolve); mvn verify exit 0 (target/mig-04-verify.log, final verifier run target/mig-04-verify-v.log, knowledge module after content round 2 target/mig-04-kb-final.log exit 0; core 182/0/0/22, cli 22/0/0/1, native 2/0/0/0, mcp 8/0/0/1, knowledge 67/0/0/2 after the round-2 pins); tokens questions 1,042, plan 1,126, question.md 152 (after content round 2); 25 real page cites, 23 distinct, all resolve (CitesTest); forbidden-pattern grep 1 hit = the form `{{tenant.<path>}}` on plan.md:12 (no value), agent-syntax grep 0.
Review: reviewer r1 FAIL 2 (several cites of one kind per line and cross-file sort unpinned) → test-author pinned both + 3 extras (ChecksTest 37) → r2 PASS; kb-reviewer r1 FAIL 3 (plan.md flow gap: no step created the alias/partner-ID items; two round layouts; cite-check scope "references and templates only") → D-034 amended, kb-author round 2 → r2 PASS. Context compacted once during the close-out; re-verified with git status and git log -3.
Ledger: MIG-02b hash 3b88963, CI 39520985125 green.
Open: spec §10 L288 calls a version gap a "blocking question" while §7 item 3 and attributes.md type it `version` — spec-internal, no page retypes it (kb-reviewer item 102).
Next: KB-11d ready; MIG-05, MIG-06 after it.

## KB-11d · 2026-10-21 · (hash in the next entry)
Task: remaining follow-ups from the owner's benchmark review of 2026-10-20 (items 1–2 were done in KB-12b). Content only, no red test (CLAUDE.md "Tests": a content task's acceptance is the kb-reviewer's PASS plus a green knowledge run).
Changed: conversion/README.md "Rules" — new last bullet: the owner-confirmed split of responsibilities (generic Step01: SAP sender/interface, Partner Directory partner, no fragile logic; Step03: what the PO sender channel did before the runtime; Step07: mappings, outbound conversion, receiver-channel modules) `[owner, 2026-10-20]` paired with the artifact cites step01 XI step 1 "Set SAP headers", step 4 "Determine partner ID", p2p step 2 "Call Mappings", step 7 "Call Outbound Conversion"; module rows stay proposals (D-026). open-questions/kb-11b.md and migration/variants.md: one-to-many and ordered scenarios exist in the owner's landscape `[owner, 2026-10-20]`, receiver types not stated.
Check-only: versions/2026-09-current/changelog.md (1,499 words, at the limit) — covers the owner's "value mapping of errors" (L55 templates 1.2.0 Skip Retry Configuration; L113–114 value.mapping.skipretry read by Step06 checkExceptionType.groovy#L117, upload helper; L46/L51 no retry → DLQ); unchanged.
Skipped: the BACKLOG wish items on scenario-contribution.md (pointer on row 62, "PID" on row 50): SC is at 1,479 words, no room.
Checks: knowledge module exit 0 (target/kb-11d-kb.log, -kb-r2.log, verifier -kb-v.log; 67/0/0/2); words README 1,121, kb-11b 229, variants 947 (SC 1,479, contracts 1,478, po-ico 1,477 untouched); forbidden grep 0; no heading changed; diff target/kb-11d.diff (3 files +3 −2).
Review: kb-reviewer r1 FAIL 2 (kb-11b.md and variants.md stretched the owner's Q18 answer to IDoc/XI receivers: "live for the pilot", "Such scenarios"), observation Step01 → "the generic Step01"; round 2 fixed all three → r2 PASS. Reviewer memory 104–105.
Ledger: MIG-04 hash 4ec942e, CI 39523214189 green.
Findings: none. Not a deviation. Decisions: none new (D-026, D-029 applied). Issues: none new. KB-11d done.
Next: MIG-05 ready; MIG-06 after it.

## MIG-05 · 2026-10-21 · (hash in the next entry)
Task: phase-5 and phase-7 files of sap-po-migration (spec §7 items 5–7, §8, §9): templates/profile.md (§8.1 part-2 contract, P-2), templates/scenario.md (D-025 attributes + proposal sections, P-8 draft), templates/request.md (§9 record, P-10), references/retrospective.md (§8.4 kinds, profile amendment, library entry, exit). Content only, no red test; the scenario-library search rule stays in intake.md and is pointed to.
Changed: templates/profile.md (seven §8.1 sections with fields and sources; versions from the package CHANGELOG entry per features.md — F-9), templates/scenario.md ("## Attributes" folded into "## Scenario": identity lines + the eleven D-025 rows with `Q-<n>` / `plan round <k>, <section>` sources; P-8 draft marker; target artifacts as `{{tenant.<path>}}` rows resolved from the profile; Partner Directory entries by key; conversions by recipe; target version against features.md), templates/request.md (§9 record: header kind/migration/date/urgent/status, evidence as synthetic fixture, acceptance), references/retrospective.md (what was learned; profile amendments updated in place + Decisions line; library entry; the four §8.4 request kinds; the two named stops and the placeholder; exit sets local/migrations.md). docs/findings/F-9.md; spec §8.1 reworded + changelog line.
Checks: knowledge module exit 0 (target/mig-05-kb.log, -kb-r2.log, -kb-r3.log, verifier -kb-v.log (before the one-line round-3 cut); 67/0/0/2; CitesTest and SkillMirrorTest green); words profile 447, scenario 550, request 271, retrospective 850; cites opened: spec §8.1–8.4/§9 headings, scenario-contribution.md, naming.md, partner-directory.md, versions/features.md, conversion/README.md; placeholders only {{tenant.pipeline.version.*}}; forbidden grep 0; H2 contracts kept; diff target/mig-05.diff.
Review: kb-reviewer r1 FAIL 2 (a "version-gate stop" my packet introduced — spec §7 has two named stops; a phase-5 → phase-3 route no page defines) + four observations carried → r2 FAIL 1 (my fix wording "or ends as its plan says" was a new route) → r3 PASS. Reviewer memory 106–107.
Ledger: KB-11d hash df2df5b, CI 39524351034 green.
Findings: F-9 (spec §8.1 took the installed version from the MANIFEST Bundle-Version; features.md keys the gate on the package CHANGELOG entry — profile.md follows features.md, spec §8.1 reworded with a changelog line). Deviation: F-9. Decisions: none new (P-8 stays draft — review at hand-over). Issues: none new. MIG-05 done.
Next: MIG-06 ready (references/requests.md, requests/README.md, field branch protocol P-11); then MIG-07/MIG-08 are owner-only (pilot).

## MIG-06 · 2026-10-21 · (hash in the next entry)
Task: field loop (spec §9, P-10, P-11): docs/WORKFLOW.md §1 step 1 (fetch and merge `field/*`, read `requests/` before picking a row) and new §11; references/requests.md; D-035 (field branch protocol; D-030 cite forms confirmed as written); the architect's dry run of the skill over the synthetic fixtures (spec §11 acceptance). Content + documents, no red test.
Changed: docs/WORKFLOW.md §1 step 1 (+3 lines) and §11 "Field loop" (request, before a push incl. the ForbiddenValuesTest skip and 4-character caveats, field branch, cloud side, back to the field, automation); docs/DECISIONS.md D-035; skills/sap-po-migration/references/requests.md (stub → six sections, 700 words, cites to spec §9, WORKFLOW §11, requests/README.md); skills/sap-po-migration/fixtures/dry-run-2026-10-21.md.
Dry run: fresh Opus 5.5 agent, skill only, customer `demo`, the three fixture ICOs: `po-xray attributes` exit 0; open list 32 after phase 2 (19 blocking, 4 version, 7 conversion, 2 naming) + 7 always-asked + 1 raised = 40, all closed; plan round 1 cite check → OL-41/42 → phase 3 re-entered → round 2 `approved 2026-10-21`; 42 questions (29 answers, 13 assumptions); ICO-2 split by receiver type → 4 scenario files, all 11 attributes valued; phase-5 exit not met for scenario 4 (placeholder `{{tenant.endpoint.receiver.determination.reuse}}` unknown: the profile is amended only in phase 7); 23 gaps listed → rows MIG-09–MIG-13.
Checks: knowledge module exit 0 (target/mig-06-kb.log, -kb-r2.log, -kb-r3.log, verifier -kb-v.log (before the two-word round-3 change); 67/0/0/2; CitesTest resolves the new cites to spec §9, WORKFLOW §11 and requests/README.md); requests.md 700 words (at the ≤ 700 budget); the forbidden check exercised by the kb-author (no-hit list exit 0 and skipped=0; a hit exit 1 with number and length only); forbidden grep 0 over the changed files; dry-run report 899 words, customer-pattern grep 0; diffs target/mig-06-docs.diff, -skill.diff.
Review: reviewer (WORKFLOW/DECISIONS) r1 FAIL 3 (a commit cannot carry its own hash; "urgent first" contradicted §1 step 2; three rules not in the spec) → `done <hash>` by the next commit, urgent row inserted as the first `ready` row, the added rules recorded in D-035 "Added here beyond the spec" → r2 PASS; kb-reviewer (requests.md) r1 FAIL 1 (requests.md:48 `done <hash>` in the closing commit — §11 changed while the page was written) → r2 FAIL 1 (a trim made "the owner starts it by hand" refer to the automation) → r3 PASS. Reviewer memory 108–109.
Ledger: MIG-05 hash aa144d6, CI 39525701048 green.
Findings: none filed here; the dry-run gaps (dynamic-routing contradiction between attributes.md, po-ico-to-pipeline.md and variants.md; profile amendment timing vs the phase-5 exit; silent pages; version rows) go to MIG-09–MIG-13. Decisions: D-035 (draft — review at hand-over). Issues: none new. MIG-06 done — the skill is ready to test (spec §11): MIG-07 (pilot) and MIG-08 (automation) are owner rows.
Next: MIG-09 (dynamic routing and same-interface mapping rules) ready; MIG-10 (profile amendment timing, F-10) ready; MIG-11–MIG-13 ready.

## MIG-09 · 2026-10-21 · (hash in the next entry)
Task: dry-run gaps A — one rule for dynamic routing and for the mapping shape across po-ico-to-pipeline.md, variants.md, is-iflow-to-pipeline.md and references/attributes.md (spec §7 item 2). Content, no red test. D-036.
Decision: D-036 — `dynamicRouting.mappings` non-empty → `reuse-xrd` derived (source `dynamicRouting.mappings[0]`), `custom` never derived; precondition decoupled runtime (only Step04 routes ReuseXRDEndpoint to the reuse copy; async/sync take "Not supported route"): EO → {custom, decoupled} written into the runtime question; BE has no decoupled route → {custom}, the copy returning one receiver (sync rejects n), or the proposal stops; dynamic routing wins over conditions; mapping "one type" only when every target interface has the same name and namespace. Helper, OUTPUT-MODEL, D-025 and tests already agreed; the pages diverged three ways (po-ico:39 `custom`, variants:32 "never derived", attributes:23 reuse-xrd).
Changed: po-ico-to-pipeline.md L39 (reuse-xrd derived, decoupled only; custom never derived, the custom copy on "Custom RD?" Yes) and L42 (one type when all targets share interface name and namespace; rule pointer OUTPUT-MODEL "Migration helpers" before both branches), prose trims L7/L16/L32/L51/L61 → 1,478 words; variants.md L32 "Derived from dynamic routing … the human confirms it, weighing: decoupled only … EO: custom or decoupled; BE: custom only, returning one receiver, or the proposal stops" (988); is-iflow-to-pipeline.md L38 "not derived from an iFlow" (1,052); references/attributes.md L23 precondition in the check-by-hand column, L52 disagreement clause (1,362); DECISIONS D-036; BACKLOG MIG-12/MIG-13 rows extended.
Checks: knowledge module exit 0 (verifier, target/mig-09-kb-v.log; also -kb.log, -kb-r2.log; 67/0/0/2, CitesTest skipped 0); the three "Custom RD?" gateways read in the dumps target/mig-09-step02.integrated.messaging.async.md, -sync.md, -step04.receiver.determination.md (async/sync Yes on customXRDBoolean, "Not supported route" on reuseXRDBoolean; Step04 Yes on either; readCustomReceiverDeterminationFromPD.groovy#L35/L38 shared); Step01 XI "QoS?" BE → "Bypass option?" → sync or P2P; customer grep 0; no cite removed; H2s unchanged; diffs target/mig-09.diff, -r2.diff.
Review: kb-reviewer r1 FAIL 1 (variants.md:32 and attributes.md:23 offered `decoupled` for a BE scenario — the error was in my D-036: Step01 XI "QoS?" BE leads only to Step02 sync or P2P) → D-036 amended, pages scoped (EO: custom or decoupled; BE: custom only, one receiver, or stop) → r2 PASS; observations carried: EO entry as a line or as "why open" → MIG-12. Reviewer memory 110–111.
Ledger: MIG-06 hash 801bbb9, CI 39528327851 green.
Findings: none (OUTPUT-MODEL and the helper unchanged). Decisions: D-036. Issues: none. MIG-13 (code) gains the BE + dynamic-routing disagreement flag.
Next: MIG-10 (profile amendment timing, F-10, spec §8.1 changelog) ready; MIG-11, MIG-12 ready; MIG-13 pending on this row.

## MIG-10 · 2026-10-21 · (hash in the next entry) · deviation
Task: dry-run gaps B — profile amendment timing (spec §7 items 3/5/7, §8.1). Content, no red test. F-10, D-037, spec §8.1 reworded + changelog line.
Decision: D-037 — an answer that settles a profile value amends the profile row when recorded (step 6), source `M-<n>, Q-<n>` + Decisions line; a decided package update is a Decisions line only (Installed row as read, target version named in the specification with its Q-<n>); the retrospective lists the amendments and adds the rest.
Changed: spec §8.1 sentence ("amended when a question-round answer settles one of its values (§7 item 3, step 6; F-10) and in the retrospective") + changelog line; docs/findings/F-10.md; DECISIONS D-037; SKILL.md phases 3 and 7 and "Local files" (997 words); references/questions.md step 6 rewritten, "Question record" consequence names the profile row (1,166); templates/profile.md L3, "Installed pipeline versions" decided-update line, "Decisions" line form (506); references/retrospective.md "Tenant profile amendments" (lists the question round's amendments, adds items 1/3/5/7), "After a stop" item 3 (995); templates/question.md "## Consequence" (160).
Dry run 2 (reduced): fresh Opus 5.5 agent, skill only, local/demo from run 1: step 6 applied to Q-27/Q-39/Q-40 → profile Naming row and copy-id bullet set (M-1, Q-39/Q-40), three Decisions lines, Installed Templates row stays 1.0.1 as read; phase-5 exit met for scenarios 1–4 (11 attributes, 6 placeholders resolve); scenario 1/2 name "target version Templates 1.1.1, update decided Q-27"; gaps: step 6 omits the Naming scheme bullets (no source field) and templates/scenario.md has no target-version column → MIG-12; recorded in fixtures/dry-run-2026-10-21.md "Second run (MIG-10)" (file 1,019 words).
Checks: knowledge module exit 0 (kb-author target/mig-10-kb.log, -kb-r2.log; 67/0/0/2; CitesTest skipped 0; SkillMirrorTest 13 green — no frontmatter change, no mirror edit); H2 sets unchanged on the five pages; customer grep 0; diffs target/mig-10.diff, -r2.diff.
Review: kb-author r1 reported three conflicts (retrospective.md "After a stop" item 3 still a profile amendment; profile tables without a source field — kept, the Decisions line carries the Q-<n>; question record consequence silent on the profile row) → fix round → kb-reviewer r1 PASS; observations → MIG-12 (scenario.md target-version column, intake.md:20 stale clause, assumed values at step 6, consequence names the Decisions line); D-037 notes it supersedes the D-034 naming clause. Reviewer memory 112.
Ledger: MIG-09 hash f836cc3, CI 39530283773 green.
Findings: F-10 (minor, deviation). Decisions: D-037. Issues: none.
Next: MIG-11 (knowledge pages silent, features.md rows) ready; MIG-12 ready; MIG-13 pending.

## MIG-11 · 2026-10-21 · (hash in the next entry)
Task: dry-run gaps C — five silent page questions (OTHER-sender partner ID, Step07 for BE→XI, decoupled sender queue, reuse-XRD interfaces, one-to-many "stop") and `present in` rows in features.md so a present feature is not a question (spec §7 item 2, §10). Content, no red test. D-038.
Decision: D-038 — (a) a page's "proposal stops" for an uncovered case is a blocking entry the human closes, not a third skill stop; (b) `present in <version>` rows for artifacts without a CHANGELOG entry, gate treats them as present at or above that version; (c) a question no phase asks becomes a row of attributes.md "Attributes that need a question".
Changed: variants.md (988 → 1,308): "OTHER sender" bullet (eo copy step 2 "Set SAP headers" leaves SAP_Sender/SAP_SenderInterface empty; Step02 inbound determinePartnerID.groovy L155/L214/L219/L241 alias → BusinessSystemName lookup → SAP_Sender, throws L165/L217; partner ID per the XI rule L318/L338); BE → XI receiver takes p2p.to.xi (templates CHANGELOG L10 sync and async; Step02 sync calls it, SapQualityOfService BestEffort; the Step07 sync template's Receiver is unconnected) — human confirms, default p2p.to.xi; runtime default rewritten: no InboundQueue entry → the Step01 XI/IDoc copy's configured DefaultJMSQueueNumber selects the runtime (X01 → integrated-async, SAP's recommendation; Q01 → decoupled; xi/idoc propdef descriptions), an entry only for another runtime or queue; po-ico-to-pipeline.md L33 runtime row swapped word-neutrally (1,474); one-to-many to IDOC/XI = blocking entry, not a stop (D-038 a); scenario-contribution.md L57 pointer (1,480); open-questions/mig-11.md "Interfaces from the reuse-XRD copy" (Receivers.xsd optional Interfaces, Step04 step 74 maps them, step 51 routes on count(//Interface) > 0, Step05 step 36 reads interfaceDetermination_<alias>; open: does the migrated PO mapping write them) (164); versions/present.md new: four `present in` rows (1.1.1: eo, Step03; alo, Step04/Step05 custom, custom error handling; 1.0.0: Step07 o2mo, o2mm, moar, p2p, sync — CHANGELOG L17/L23, Bundle-Version 1.0.1; 1.0.0: the reuse template) (280); open-questions/kb-11b.md:5 "(a blocking open-list entry the human closes, D-038)"; features.md "How to read" pointer (1,473); attributes.md: sender-queue question row, version gate steps 1–2 and 4 cover present.md (1,474); DECISIONS D-038.
Checks: knowledge module exit 0 (verifier target/mig-11-kb-v.log; kb-author -kb.log, -kb-r2.log; 67/0/0/2, CitesTest and BudgetTest skipped 0); 13 dumps target/mig-11-*.md; customer grep 0 (incl. the two new pages); no H2 changed in tracked files; no Step03 generic exists (template only) — packet item corrected; no new placeholder (DefaultJMSQueueNumber is the copy's parameter).
Review: kb-author r1 put the present-in rows into open-questions (features.md at budget) → architect: new page versions/present.md, queue wording = the copy's parameter → r2; kb-reviewer r2 FAIL 4 (variants.md:25 default-queue paragraph contradicted its new sentence; po-ico:33 default rested on SAP's recommendation not the configured value; present.md put the Step07 templates in 1.1.1 though the 1.0.0 entry L23 notes them; kb-11b.md:5 still "the proposal stops") + verb lost at scenario-contribution.md:86 → D-038 (b) amended, round 3 → kb-reviewer r3 PASS; observations: benchmark/expected-proposal.md:61 "tenant default queue" → add "(DefaultJMSQueueNumber X01)" at the next benchmark edit (KB-12b/MIG-12 note). Verifier flagged the two baseline ForbiddenValuesTest skips (testdata/ absent). Reviewer memory 113–114.
Ledger: MIG-10 hash 80d82c6, CI 39531684969 green.
Findings: none. Decisions: D-038. Issues: none.
Next: MIG-12 (skill references and templates gaps) ready; MIG-13 (code) pending on MIG-09 — now unblocked, ready after MIG-12.

## MIG-12 · 2026-10-21 · (hash in the next entry)
Task: dry-run gaps D — skill references and templates (spec §7 items 2–5): eleven places where the two dry runs guessed, each given one stated convention. Content, no red test. D-039.
Decision: D-039 — Naming scheme bullets with a source; `target (Q-<n>)` column in the scenario's Target version; assumed profile values written like answers; consequence names the profile row or the Decisions line; EO dynamic-routing disagreement rides the runtime `?` item; "touched" open question = title/Unknown names a scenario value; ICO split listed with its criterion, shared Partner Directory keys owned by the sender/receiver scenario; adapter-setting rows; profile runtime = tenant default (X01/Q01) proposed for the `?` row; SKILL.md routes to spec sections; intake.md and benchmark question 1 aligned with D-037/D-038.
Changed: templates/profile.md Naming scheme bullets with `— source: M-<n>, Q-<n>`, Runtime = tenant default for EO senders (X01 → integrated-async, Q01 → decoupled; BE integrated-sync by rule; `?` row proposed from the profile) (506 → 590); references/questions.md step 6 names the scheme bullets, Assumptions step 2 writes assumed profile values, consequence names the profile row or the Decisions line (1,166 → 1,207); templates/question.md consequence (171); references/attributes.md L21 runtime proposed from the profile, L23 EO case rides the runtime item, Open list step 1 touch rule + BE-only disagreement line, paid by dropping the duplicated L31 helper sentence (1,474 → 1,478); references/plan.md ICO split sentence and shared-partner-ID ownership with a partner-directory.md "Keys per scenario" cite (1,185); templates/scenario.md Target version `target (Q-<n>)` column, adapter-setting row form (616); SKILL.md "Read" row routing to the cited spec section (1,021); references/intake.md L20 (1,188); benchmark/expected-proposal.md L61 "(DefaultJMSQueueNumber X01)" (869); DECISIONS D-039.
Checks: knowledge module exit 0 (verifier target/mig-12-kb-v3.log; kb-author -kb.log, -kb-r2.log, -kb-r3.log; 67/0/0/2 — ForbiddenValuesTest baseline skips, CitesTest skipped 0, SkillMirrorTest 13); words SKILL 1,021, intake 1,188, questions 1,207, attributes 1,478, plan 1,185, profile 590, scenario 616, question 171, expected-proposal 869; customer grep 0; no heading changed; "stops" only at SKILL.md:40.
Review: kb-author r1 reported two conflicts (EOIO wording; step 1 disagreement line) → fix round; kb-reviewer r2 FAIL 3 (attributes.md:21 proposed the profile runtime for EOIO; profile.md:19 "the qos row stops it" read as a third stop; questions.md:45 example contradicted the default-queue rule) → D-039 (9) amended → r3 PASS; non-blocking for a later skill round: questions.md:45 "a default the knowledge base states" wording, attributes.md:21 qos blocking entry = the EOIO runtime `?` item itself. Memory item 115 appended by the architect.
Ledger: MIG-11 hash 1453946, CI 39534344113 green.
Findings: none. Decisions: D-039. Issues: none.
Next: MIG-13 (code: fixture `esr` section, same-interface rule, BE + dynamic-routing disagreement flag, condition-namespace rule) ready — acceptance spec, test-author red tests first.

## MIG-13 · 2026-10-21 · (hash in the next entry)
Task: dry-run gaps E (tool, spec §6.3): BE + dynamic routing flagged as a disagreement, condition-prefix namespace noted on xpath rows, fixture `esr` section with mapping program kinds; "same interface, several targets" confirmed already implemented (D-036). Code, red tests first. Context compacted before the MIG-12 close-out; HANDOFF, `git status`, `git log -3` re-read.
Decision: D-040 — (a) BE + dynamic routing → `receiver_determination` `?` {custom}, `receiver_count` `n` noted; (b) EO/EOIO + dynamic routing → the runtime question carries the decoupled condition (D-039 (5)); (c) xpath row with prefixes → note with the sender request message type `xmlNamespace` from `esr` or "ask"; (d) fixture `esr` section; (e) same-interface rule needs no change.
Red tests: ScenarioAttributesTest — dynamicRoutingWithQosBeMakesReceiverDeterminationAQuestionWithCustomAsTheOnlyCandidate, dynamicRoutingWithQosEoOrEoioAddsTheDecoupledConditionToTheRuntimeQuestion, xpathConditionWithPrefixesNotesTheSenderMessageTypeNamespaceFromTheEsr, xpathConditionWithPrefixesWithoutAnEsrMessageTypeAsks (six esr cases), xpathConditionWithoutPrefixesHasNoNote, xpathConditionPrefixesComeFromTheLeftOfEveryLineDistinctInOrderOfFirstAppearance, interfaceDeterminationXpathNotesPrefixesTheSameWay — red observed (7 assertion failures, core 190/7/0; target/mig-13-red.log); guards dynamicRoutingWithMixedQosKeepsTodaysRows and AttributesCommandTest.theFixtureCarriesAnEsrSectionWithMappingProgramKinds green by design; approved by reading the test files.
Changed: ScenarioAttributes.java (+124/−14): RECEIVER_DETERMINATION value set {static, xpath, custom, reuse-xrd}; constants Q_BE_DYNAMIC_ROUTING, NOTE_BE_DYNAMIC_COUNT, Q_DYNAMIC_RUNTIME; receiverDetermination(qos) BE+DR → open {custom}; receiverCount(qos) note for BE; runtime(qos) appends Q_DYNAMIC_RUNTIME for EO/EOIO+DR; prefixes(Condition) (PREFIX, QUOTED, CONTEXT_OBJECT patterns; `context:` operands skipped, §2b); prefixNote/senderMessageType esr lookup (interface and message type by name+namespace, operation by sender.operations[0] else first, xmlNamespace). OUTPUT-MODEL "Rules in short" +4/−2. Tests: ScenarioAttributesTest 52 → 61 (9 new, 7 red + 2 guards + round 2), AttributesCommandTest +1 (fixture esr). Fixture icos-SND_SYS.json: `esr` section (6 serviceInterfaces, 8 messageTypes, 5 operationMappings — 4 XI_TRAFO, 1 MAP_ARCHIVE_PRG —, 4 messageMappings, importedArchives example-routing.jar with one javaMapping); ICOs and channels byte-identical. D-040; BACKLOG.
Checks: `mvn -B -ntp -q verify` exit 0 (verifier target/mig-13-verify-final.log; earlier -verify-v.log, implementer -verify.log; red logs -red.log core 190/7, -red2.log 192/1, -red3.log 192/0): core 192/0/0/22 (testdata-gated skips, unchanged), cli 23/0/0/1, knowledge 67/0/0/2 (baseline), mcp 8/0/0/1, native 2/0/0/0; ScenarioAttributesTest 61, AttributesCommandTest 6; smoke target/mig-13-smoke-attr.md exit 0 (ICO 3 runtime question carries the dynamic-routing text); attributes.md 1,471 and questions.md 1,237 words after kb r2 (knowledge module re-run by the kb-author, target/mig-13-kb-r2.log, 67/0/0/2), no "stop"; OUTPUT-MODEL +4/−2; customer grep 0; fixture 831 lines, esr at L288.
Review: test-author r1 (7 red, 2 guards; findings: no receiver_determination value set, `ifmmessage` type, smoke changes for ICO 3) → implementer r1 green, finding `context:<name>` operands read as prefixes → §2b, test-author r2 (1 red) → implementer r2 green; reviewer PASS (non-blocking: untested " (<ns>)" suffix → test-author r3 case added, green; `<TYPE>:<value>` operands of Conditions.java L133 and `a:b:c` lookbehind left untested, out of §2b); verifier VERIFIED_PASS twice; kb-author attributes.md at budget → kb-reviewer r1 FAIL 1 (L25 quoted note text the cite does not carry) → trimmed, questions.md:11 prefix item added → kb-reviewer r2 PASS. Memory: reviewer +2, kb-reviewer 116.
Ledger: MIG-12 hash ad2ce7a, CI 39536047507 green.
Findings: none. Decisions: D-040. Issues: none.
Next: no ready row — KB-00a, KB-09, MIG-07, MIG-08, PR-00, SRC-00 wait on the owner; KB-12b judgement (GATE 5) and KB-GATE after it; FIX-03 blocked on ISSUE-3 → IDLE_NO_READY_TASK. A third dry run over the fixtures would re-validate the skill with D-036…D-040 in place (owner may ask for it).

## MIG-14 · 2026-10-21 · (hash in the next entry)
Task: third dry run (owner's request 2026-10-21): a fresh Opus 5.5 migrator, skill only, customer `demo3`, the three fixture ICOs, phases 1–5, after D-036…D-040 (MIG-09…MIG-13). Content (the record section), no red test.
Dry run 3: fresh Opus 5.5 agent, skill only, customer `demo3` (M-2), phases 1–5: open list 47 after phase 2 (19 blocking, 11 version, 6 conversion, 11 naming) + 14 always-asked = 61, all closed; plan round 1 reopened 3 (Q-62…Q-64) → round 2 `approved 2026-10-21`; 64 questions (15 by assumption); phase-5 exit met 4/4 (every attribute valued, every placeholder resolves); ICO-3 (EO, dynamic routing): runtime integrated-async (Q-10), receiver_determination custom (Q-27), the reuse-xrd/decoupled disagreement recorded on the runtime question, analysis O-10, plan and a scenario-4 note (D-036, D-039 (5) hold); profile amended at step 6 (Q-20, Q-51…Q-61; Q-62 queue Q01 → X01 at the plan re-entry, D-037 holds); 11 version questions (Q-34…Q-44) for features the pages list as present, all "present, no update" — D-038 (b) exempts only `present in` rows, features.md rows at or below the installed version still become questions.
Gaps: 14 reported; kb-reviewer classification: page defects 1, 2, 3, 4, 5, 6, 8, 12, 13 → MIG-15 (skill references and templates) and MIG-16 (knowledge pages); tool/fixture defects 7, 9 → MIG-17; known 10 (open-questions/kb-11b.md); runner errors 11 (questions.md already orders reopened items) and 14 (intake.md makes migrations.md the one map across customers on purpose).
Changed: skills/sap-po-migration/fixtures/dry-run-2026-10-21.md "## Third run (MIG-14): full run after the gap fixes" (380 words; file 1,019 → 1,399); agent/BACKLOG.md MIG-14 row (reason: owner's request). local/demo3/ (gitignored) holds the run's files.
Checks: knowledge module exit 0 (verifier target/mig-14-kb-v.log; 67/0/0/2 baseline; the "[Fatal Error] XML" log line is present in every earlier green log, a test fixture); dry-run file 1,399 words after the fix round (section 380 ≤ 380; architect awk re-count), seven H2 unchanged plus the new one; git: only BACKLOG.md and the fixtures file modified, local/ gitignored; customer grep 0; local/demo3/scenarios/M-2 holds intake, attributes-report, analysis, questions, plan, scenario-1…4.
Review: kb-reviewer r1 FAIL 4 (gap lines without an existing file and heading: attributes.md "Modules and mappings by recipe", OUTPUT-MODEL "Migration helpers" instead of helper output under local/, po-ico-to-pipeline.md for the OTHER-sender/SFTP line, open-questions/kb-11b.md with its entry title) → runner fixed → r2 PASS (a bold open-questions entry title counts as a heading for a gap pointer, not for a cite); the kb-reviewer verified the counts against local/demo3 (O-1…O-64, 64 records, 15 assumptions, plan.md:484 approval). Memory 117.
Ledger: MIG-13 hash 71fc425, CI 39539381995 green.
Findings: none. Decisions: none. Issues: none.
Next: MIG-15, MIG-16 (content) and MIG-17 (code) ready; then IDLE_NO_READY_TASK — KB-12b judgement, KB-00a, KB-09, MIG-07, MIG-08, PR-00, SRC-00, FIX-03 wait on the owner.

## MIG-15 · 2026-10-21 · (hash in the next entry)
Context compacted before this task; re-verified with `git status` and `git log -3` (HEAD 08893f4, clean).
Task: dry-run-3 gaps F (skill references and templates): six conventions the run left to the runner. Content, no red test. Deviation: spec §7 item 2 (F-11).
D-041: (a) open-list touch only by title or Unknown line, a "Pass A (drafts vs artifacts)" list never, a `?` candidate like a value; (b) one item per recipe row, adapter beans no rows; (c) a features.md row at or below the installed version raises no item (like `present in`, D-038 b); (d) scheme rows in the profile, profile questions T-1…T-4 with source `M-<n>, T-<n>`; (e) an unused template parameter is a `not set` row and counts as resolved; (f) a profile row assumed "not needed".
Changed: skills/sap-po-migration/SKILL.md (1,021 → 1,044), references/attributes.md (1,471 → 1,478; prose cut), intake.md (1,188 → 1,220), questions.md (1,237 → 1,273), retrospective.md (995 → 1,011; intake-asked sources), templates/profile.md (590 → 655), templates/scenario.md (616 → 636); docs/DECISIONS.md D-041; docs/findings/F-11.md; spec §7 item 2 + changelog line; agent/BACKLOG.md.
Checks: knowledge module exit 0 (verifier target/mig-15-kb-v.log; 67/0/0/2 baseline); every page ≤ 1,480; "stop" lines 18, unchanged from HEAD; customer grep 0; local/ and testdata/ untouched.
Review: kb-reviewer r1 PASS (2026-10-21); non-blocking: kb-06 is headed "Pass A (drafts vs scripts)" — excluded by the title/Unknown rule, not the literal; questions.md:13 conversion bullet still reads per module; profile.md L40–42 bullets lack `not needed`; SKILL.md:33 and intake.md:80 exits name only `proposal` recipes (pre-existing).
Ledger: MIG-14 hash 08893f4, CI 39566640773 green.
Findings: F-11 (version gate: §7 item 2 listed present features too; §10 intent kept). Decisions: D-041. Issues: none.
Next: MIG-16 (knowledge pages: jms queue shape, request-only BE mapping, interface_determination contradiction), then MIG-17 (code); then IDLE_NO_READY_TASK.

## MIG-16 · 2026-10-21 · (hash in the next entry)
Task: dry-run-3 gaps G (knowledge pages): the inbound queue shape, a request-only BE mapping, the custom interface determination contradiction. Content, no red test. No deviation.
D-042: (a) `{{tenant.jms.queue.inbound}}` is `<prefix>X01` (integrated-async, step02.integrated.messaging.async InboundProcessingQueueX) or `<prefix>Q01` (decoupled, step02.inbound.processing InboundProcessingQueue), by the scenario's runtime; (b) BE with a request step only: the request call takes the program, the response call stays as shipped (sync template step 16 "Content Modifier"), value `one`; (c) `interface_determination` custom is the Step05 copy, decoupled only; integrated interfaces come from the XSLT result or a `receiver_determination` scripts or xrd copy's result (step 3 "Add interfaces to XML" / "Merge interfaces with XML Receivers").
Changed: knowledge/pipeline/naming.md L70 (1,478 → 1,480), migration/po-ico-to-pipeline.md L41 (1,474 → 1,480), scenario-contribution.md L29 (1,480 → 1,480), migration/variants.md L39 (1,308 → 1,338); prose cuts on the three pages at the ceiling; docs/DECISIONS.md D-042; agent/BACKLOG.md. kb-04.md L8 (default queue name) stays open — (a) does not answer it.
Checks: knowledge module exit 0 (verifier target/mig-16-kb-v2.log; 67/0/0/2 baseline); every page ≤ 1,480; heading lines changed 0; customer grep 0.
Review: kb-reviewer r1 FAIL 3 (po-ico L59 cut dropped the spec §8 condition "about the pipeline"; "no Step05 (Q03 grep)" an uncited negative claim; "custom copy" generalised "scripts or xrd" — the plain Step04 custom copy has no interface step) → kb-author r2 → kb-reviewer r2 PASS (2026-10-21). Non-blocking: scenario-contribution L29 cites async step 45 only; sync has the same step (L1758).
Ledger: MIG-15 hash 3e20251, CI 39568803472 green.
Findings: none. Decisions: D-042. Issues: none.
Next: MIG-17 (code: mapping-shape cross-check against `esr.messageMappings[].multiplicity`, fixture channel `attributes`, intake.md source of channel attributes; acceptance spec, red tests first); then IDLE_NO_READY_TASK.

## MIG-17 · 2026-10-21 · (hash in the next entry)
Task: dry-run-3 gaps H (tool and fixture): esr cross-check of the `mapping` row (Q-14), channel `attributes` on the fixture (Q-63, Q-64), intake.md names their source. Code; red first. No deviation.
Red tests (test-author, approved): ScenarioAttributesTest mappingNoteNamesAMessageMappingWhoseMultiplicityDisagreesWithTheTargets, anUnboundedTargetWithAOneToOneMessageMappingIsADisagreementAppendedToTheOneToManyNote, severalDisagreeingStepsAreJoinedInOrder, anOpenMappingRowKeepsItsQuestionAndGetsTheDisagreementAsNote (red observed 68/4, target/mig-17-red-core.log); guards mappingHasNoNoteWhenTheMessageMappingMultiplicityAgreesWithTheTargets (the Q-14 case agrees), mappingNoteIsUnchangedWithoutAnEsrOrWhenTheOperationOrMessageMappingIsNotInIt, onlyRequestXiTrafoStepsAreCompared; AttributesCommandTest everyFixtureChannelCarriesSyntheticAttributes (red before the fixture edit, target/mig-17-red-cli.log), theFixtureMarkdownCarriesNoEsrDisagreement (guard).
D-043: (a) the `mapping` row keeps the targets' value and gets a note "esr: message mapping <name> is <multiplicity>, the targets say <1:1 or 1:n>" per disagreeing request XI_TRAFO step, joined "; " after NOTE_ONE_TO_MANY; nothing when the esr lacks the mapping; (b) fixture keys `synthetic.<setting>` — no verifiable PO attribute names in the repository, SAP pages unreachable → ISSUE-4 (owner: names only); (c) intake.md names `channels[].attributes` as the source of adapter settings.
Changed: po-xray-core ScenarioAttributes.java (esrDisagreements, multiplicityClass, unbounded, join, esrDisagreement text); docs/OUTPUT-MODEL.md "Rules in short" L564–566; ScenarioAttributesTest (+184, builders mappingEsr/operationMapping/step/messageMapping/inv), AttributesCommandTest (+38); fixture icos-SND_SYS.json (+23, five `attributes` blocks, 0 removed lines); skills references/attributes.md, intake.md; docs/DECISIONS.md D-043; issues/ISSUE-4.md; agent/BACKLOG.md.
Checks: mvn verify exit 0 (verifier, target/mig-17-verify-v.log): core 199/0/0/22, cli 25/0/0/1, native 2/0/0/0, mcp 8/0/0/1, knowledge 67/0/0/2 — baseline skips; red observed core 68/4 (target/mig-17-red-core.log), cli 8/1 (target/mig-17-red-cli.log) → green; attributes.md 1,477, intake.md 1,287 words; fixture +23/−0 lines, 5 attributes blocks; customer grep 0; smoke exit 0, "esr: message mapping" 0 in the fixture markdown, the one-to-many note rendered.
Review: reviewer r1 FAIL 1 (null-program guard untested) → test-author r3 → reviewer r2 PASS; kb-reviewer r1 FAIL 4 ("starts" vs "carries", claim beyond its cite, masked values must be asked, custom_exit clause vs OUTPUT-MODEL L571) → D-043 "carries", ISSUE-4 masking note, kb-author r2 → kb-reviewer r2 PASS; verifier VERIFIED_PASS. Non-blocking: when ISSUE-4 closes, move the `<hidden>` sentence into OUTPUT-MODEL "Integration inventory".
Ledger: MIG-16 hash b69e2f2, CI 39571099472 green.
Findings: none. Decisions: D-043. Issues: ISSUE-4 (PO adapter attribute names, owner-only).
Next: MIG-18 — a docs guide for the first real run (owner asked 2026-10-21); every other row waits on the owner (KB-12b, KB-00a, KB-09, MIG-07/08, PR-00, SRC-00, FIX-03, ISSUE-4).

## FIX-04 · 2026-10-21 · (hash in the next entry)
Task: neutralise a dry-run profile prefix in the D-042 reason (`docs/DECISIONS.md` row D-042) → `<prefix>Q01`. Docs, architect edit, no agents, no review (one token; the kb-reviewer memory keeps no item).
Cause: the architect quoted the dry-run-3 profile's queue name in MIG-16 (b69e2f2); the staged-file customer grep before the MIG-17 commit printed 1 hit but the commit was chained after the grep, not guarded by it, so 7ee752c carries the row too.
Fix: the value replaced; a case-insensitive `git grep` of the prefix over tracked files outside `testdata/` and `local/` is empty. The architect's commit guard is now `[ "$(grep -il <patterns> $FILES | wc -l)" -eq 0 ] &&` before `git commit` (same form as the HANDOFF guard).
History: two pushed commits keep the token; rewriting pushed history is owner-only → issues/ISSUE-5.md.
Ledger: MIG-17 hash 7ee752c (CI pending at the time of writing; result in the next entry).
Next: MIG-18 — docs/FIRST-REAL-RUN.md guide (owner asked 2026-10-21): scout running, then page packet → kb-author → kb-reviewer → verifier.

## MIG-18 · 2026-10-21 · (hash in the next entry)
Task: owner-facing guide for the first real migration run, `docs/FIRST-REAL-RUN.md` (owner asked 2026-10-21). Content; page packet → kb-author → kb-reviewer. No deviation.
Page: H1 + 8 H2 (what exists today, prepare the machine, produce the dumps, start the skill, phases 1–5, what never leaves the machine, after the run, which scenario first); 1,459 words; every statement cites a page heading, the spec, a decision or a code/docs line; placeholders only.
Findings handled in the same commit: docs/inputs/README.md:27 named the per-system credential variables without the `POXRAY_` prefix the code requires (PoSystem.envPrefix, PoSystem.java:113–116) → corrected; README.md:76 and :91 carried a customer name in examples → `<customer>`, `po-dev`; the remaining identifiers outside testdata/ (javadocs, tests, a Windows script, the hostname in docs/SOURCES-ESR-DIRECTORY.md:3) → ISSUE-3 scope addendum, FIX-03 stays blocked on the owner.
Correction of the architect's chat claim: spec §11's pilot is the owner's first BPM process (MIG-07), not a single ICO; the guide says so and offers the single-ICO run as a warm-up (BACKLOG row reworded).
Checks: words 1,459 ≤ 1,480; `grep -c '^## '` 8; 16 `[page:]` cites found verbatim; "trial" only inside one cite path (D-044); customer grep 0 over the page, README.md, docs/inputs/README.md, ISSUE-3; knowledge module NOT_RUN (no file under knowledge/ or skills/ changed); no Maven.
Review: kb-reviewer r1 FAIL 1 (fact-base sentences claimed more than knowledge/sources/README.md:9–10 says) + 9 notes → kb-author r2; owner rule D-044 → kb-author r3 (package versions, no tenant kind in prose); kb-reviewer r2 FAIL 2 (BACKLOG line cites shifted by the KB-14 row) → kb-author r4 (section and row-id cites) → kb-reviewer r3 PASS. Word count 1,459 observed by the architect (awk), 8 H2, customer grep 0.
Ledger: MIG-17 hash 7ee752c (CI 39575259102 green); FIX-04 hash 8841b1e (CI 39575581940 green).
Findings: none filed. Decisions: D-044 (owner 2026-10-21: no tenant kind in any text, the package versions identify the knowledge base) → row KB-14 relabel. Issues: ISSUE-3 addendum.
Next: KB-14 — relabel the source folders by date, rewrite cites, prose and `kb.sources`, "trial" leaves every page (D-044); then idle unless the owner answers ISSUE-3/4/5, KB-12b, KB-00a, MIG-07.

## KB-14 · 2026-10-21 · (hash in the next entry)
Task: D-044 relabel (owner 2026-10-21): the source folders lose the tenant kind — `knowledge/sources/2026-06-trial` → `2026-06`, `2026-09-current` → `2026-09`, `versions/2026-09-current` → `versions/2026-09`; every cite, path, `kb.sources` default and the prose follow; a Checks rule keeps the words out. Code + content; red first. Deviation: F-12 (spec §13 changelog line; plan L28).
Red tests (test-author, approved): ChecksTest tenantKindFlagsAPageNamingTheTenantKind, tenantKindMatchesCaseInsensitivelyAndTheStandaloneWordTrialOnly, tenantKindCoversSkillPages, defaultSourcesLabelIsTheDate; TenantKindTest noPipelineOrSkillPageNamesTheTenantKind (real pages) — red observed as 6 compile errors (target/kb-14-red.log); with a throwaway rule the real-page test showed 128 hits.
Implementer: 608 git mv renames (sources byte-identical, 100% similarity), labels replaced in 70 files with identical line counts, Checks.tenantKind + shared markdownByName; ChecksTest 41/0/0/0 (target/kb-14-green-unit.log); reactor red only on TenantKindTest, 5 prose lines (target/kb-14-verify.log). Architect: agent/BACKLOG, HANDOFF, NOTES relabelled; plan L28 and L51 reworded, plan changelog line; spec changelog line (F-12).
kb-author: 7 prose lines (versions/README.md L13–15 with a new "package versions (D-044)" column cited to both INDEX.md files, knowledge/sources/README.md L9–10, versions/2026-09/changelog-generic-pipelines-step02.md L19, changelog.md L7, kb-10.md L27, reworded in r2 to keep README L17's fact); knowledge module 72/0/0/2 (target/kb-14-kb.log, kb2.log); budgets 438, 313, 1,480, 1,497 (was 1,499; under the checker's 1,500, accepted), 1,479.
Checks: mvn verify exit 0 (verifier, target/kb-14-verify-v.log): knowledge 72/0/0/2 (67 + 5 new; TenantKindTest runs, not skipped), core 199/0/0/22, cli 25/0/0/1, mcp 8/0/0/1, native 2/0/0/0 — baseline skips; old-label grep outside the exclusions hits only ChecksTest.java:722 (the old-label fixture, kept on purpose); tenant-kind grep over knowledge/pipeline, sources README, skills, docs (less DECISIONS, findings, superpowers) empty; 608 renames, 0 bare deletions; budgets 438 / 1,497 (changelog.md, under the checker's 1,500, was 1,499) / 1,480 / 1,466; smoke exit 0 (target/kb-14-smoke.md).
Review: reviewer PASS (code and tests; non-blocking: untested if/while and whitespace branches in tenantKind → reviewer memory); kb-reviewer r1 FAIL 1 (kb-10.md:27 lost README L17's fact) → kb-author r2 → kb-reviewer r2 PASS (versions/README.md 438 stands: no repository rule under 1,500); verifier VERIFIED_PASS except its step 5 margin note (changelog.md 1,497 accepted).
Ledger: MIG-18 hash 9a3d8f3 (CI run cancelled by the next push), 3b2414d (CI 39578403487 green).
Findings: F-12. Decisions: D-044 (written in MIG-18). Issues: none.
Next: IDLE_NO_READY_TASK unless the owner answers — ISSUE-3/FIX-03, ISSUE-4, ISSUE-5, KB-12b, KB-00a (download → label `2026-10`), KB-09, MIG-07 pilot.

## MIG-19 · 2026-10-21 · (hash in the next entry)
Task: quick start for the owner's own first run (owner 2026-10-21: the full guide is too long; several BPM processes with their follow-on ICOs to try) — `docs/QUICK-START.md`, one screen, eight numbered steps, each one command or one file with its cite; `docs/FIRST-REAL-RUN.md` points to it. Content; page packet → kb-author → kb-reviewer. No deviation. Context compacted once during the review wait.
Page: 389 words, H1 + 5 H2 (Before the first run, Dumps, Run, When it stops or ends, Then), steps 1–8 (3/2/3), nine `[page:]` cites and four FIRST-REAL-RUN section cites; six packet departures accepted where the sources decide (the skill allocates M-1, `unknown` in the profile, per-component output folders, step 1 cites docs/inputs/README.md:9–10, no page names where components are found, the field branch is pushed only after the forbidden-values check); `docs/FIRST-REAL-RUN.md:3` pointer, `git diff --numstat` 2 0.
Checks: 389 words (awk), 5 H2, 8 steps, customer grep 0 over the five changed files, the word tenant only inside the exact cite heading `#Tenant profile` (D-031; not a tenant kind, D-044); knowledge module not run (no knowledge/ or skills/ file changed).
Review: kb-reviewer r1 PASS (2026-10-21; every cite opened; six non-blocking notes — `<SYSTEM>` defined only in FIRST-REAL-RUN.md:43–44, the `passes` of step 8 could name a zero skip count, `(its name)` inside the start prompt is a sixth placeholder — left for the field loop).
Ledger: KB-14 hash e39fac5 (CI run 39581894366: attempt 1 Maven Central 429 before any build step, attempt 2 green).
Findings: none. Decisions: none. Issues: none.
Next: IDLE_NO_READY_TASK unless the owner answers — ISSUE-3/FIX-03, ISSUE-4, ISSUE-5, KB-12b, KB-00a, KB-09, MIG-07 pilot; the owner's first real run (BPM + ICOs) will produce requests for the field loop.

## MIG-20 · 2026-10-21 · (hash in the next entry)
Task: scenario discovery, core (owner 2026-10-21: the migrator hands over artifacts, never the wiring — D-045): `io.poxray.migration.ScenarioDiscovery` → `io.poxray.model.DiscoveryReport` — the interfaces of each BPM process by usage role (start/intermediate inbound, outbound/notification outbound) matched against the receiver target interfaces and sender interfaces of the system's ICOs; ICOs before and after the process, components by role (process, source, target), interfaces without an ICO as issues; JSON + Markdown; OUTPUT-MODEL.md section. Code; TDD. Deviation: F-13 (spec §5, §6.3, §7 item 1 assumed hand-named components and ICOs; changelog line added).
Red: `ScenarioDiscoveryTest` (17 tests, test-author; approved by the architect) — observed red as a compile failure, target/mig-20-red.log, exit 1 (`DiscoveryReport`, `ScenarioDiscovery` missing).
Green: implementer target/mig-20-green.log exit 0 (17/0/0/0); first root verify EXECUTED_FAIL in NativeReflectionTest (DiscoveryReport unregistered) → packet widened to NativeReflection.java → root verify target/mig-20-verify.log exit 0: core 216/0/0/22, native 2/0/0/0, cli 25/0/0/1, mcp 8/0/0/1, knowledge 72/0/0/2 (baseline skips).
Checks: verifier VERIFIED_PASS root verify exit 0 (target/mig-20-verify-v.log): core 216/0/0/22 (ScenarioDiscoveryTest 17/0/0/0), native 2/0/0/0, cli 25/0/0/1, mcp 8/0/0/1, knowledge 72/0/0/2 — baseline skips; CLI smoke exit 0 (target/mig-20-smoke.md); customer grep 0 over the new files and F-13; diff target/mig-20.diff 4 files +500/-1.
Review: reviewer r1 PASS (tests real: id≠name fixture, namespace and case cases, unsorted inputs pin the sorts; rules 1–6 and public types match; non-blocking: role sorting and catalog order unpinned, IllegalArgumentException on label count mismatch and the null-export skip untested, `of` extras in Markdown, shallow immutability as BpmInventoryReport; implementer decisions without a test listed for a later row — MIG-21 test-author may pick them up). Packet deviation: NativeReflection.java registration added after the first root verify failed (NativeReflectionTest requires every io.poxray.model class).
Ledger: MIG-19 hash 33707a2 (CI run 39584178282 green).
Findings: F-13. Decisions: D-045. Issues: none.
Next: MIG-21 CLI `discover` (every ICO of the system through PoService, report + `icos-<component>` dumps with `-o`), then MIG-22 intake and quick start.

## HAND-OVER · 2026-10-21 · (hash in the next entry)
Owner 2026-10-21: stop the cloud loop, continue locally this week. MIG-20 committed as 72d54e3 (CI run 39587302862 green). MIG-21 not started: its acceptance spec and the scout facts (CLI `discover`, systems-directory override, offline `--icos`, slice helper, test plan) are in agent/NOTES.md, last section, so the local session starts at the test-author. BACKLOG: MIG-20 done, MIG-21 ready, MIG-22 pending; no other ready row. Owner inputs still open: ISSUE-3/FIX-03, ISSUE-4, ISSUE-5, KB-12b, KB-00a, KB-09, MIG-07.

## CODEX-01 / MIG-07 intake · 2026-10-21 · uncommitted
Task: owner resumed local work; adapt Codex execution rules and import prior pilot evidence privately as M-1.
Test first: none — workflow documentation and preparatory evidence import; no product code changed.
Changed: Codex override and entry links; neutral ledgers; private migration register, profile, intake, continuation and question record.
Checks: VERIFIED_PASS workflow links, whitespace, scope and privacy (target/codex-01-review.log); private dossier provenance, four source hashes and ignore coverage VERIFIED_PASS (M-1 review.log).
Checks: Maven NOT_RUN — workflow-only documentation and private evidence import; migration helpers NOT_RUN — local runner unavailable.
Review: PASS — independent workflow review and private dossier review; no blocking findings.
Decisions: D-046; owner-selected pilot precedes unrelated ready tasks; new pilot answer retained privately with its unverified API follow-up.
State: MIG-07 in-progress; formal intake incomplete; no approved plan, generated artifacts, commit or push claimed.
Next: continue private open questions; complete profile and tool prerequisites. MIG-21 ready and MIG-22 pending remain unchanged.

## MIG-23 P1 preparation · 2026-10-26 · uncommitted
Owner: approved retrospective design and implementation of P1 only; D-047. P2–P7 not authorized for execution.
Packet: docs/superpowers/plans/2026-10-26-mig-23-p1-packet.md; base 1a25886; scoped tests and interfaces fixed before source edits.
Checks: VERIFIED_PASS baseline root verify exit0, 323 tests / 0 failures / 0 errors / 26 baseline skips; target/mig-p1-baseline-unrestricted.log.
Checks: initial sandbox run EXECUTED_FAIL with 14 loopback permission errors; unrestricted local-test rerun resolved them, no test changes.
Skips: 24 absent private-input assumptions; 2 unset forbidden-values checks NOT_RUN; kb.skip empty, KB artifact checks ran.
Environment: Java25.0.2, Maven3.9.11; actual POM JUnit6.1.3/Jackson3.2.3 overrides stale narrative version labels, no dependency change.
Review: proposal independent review PASS (target/migration-retro-audit-review.log); product implementation review pending.
Test first: pending test-author; no new production source written. Preserve prior local work; no push or tenant operation.
Next: observed synthetic red, architect approval of test files, then bounded implementation and documentation.

## MIG-23 P1 red tests · 2026-10-26 · uncommitted
Test first: 44 new tests (core validator22, reader12, CLI9, native1); all four files read and approved by architect.
Checks: EXECUTED_FAIL expected compilation red on absent DossierValidator/DossierReport, exit1; target/mig-p1-red.log.
Scope: synthetic @TempDir fixtures only; no production stub/source change; native assertions additive; test hashes in packet.
Context7: JUnit, AssertJ, Jackson3, Quarkus and Java25 resolve+query performed before test code; target/mig-p1-context7-tests.log.
Review: packet preflight PASS (independent design_reviewer); product review still pending.
Checks: verifier reproduced expected red exit1 (target/mig-p1-red-verifier.log), zero new production files; not an environment failure.
Next: implementer on exact packet; no changes to P2 gates.

## MIG-23 P1 implementation handback · 2026-10-26 · uncommitted
Changed: strict dossier reader/report/projection, CLI command, native registration; four extra synthetic atomicity tests.
Checks: author VERIFIED_PASS focused50/0/0/0; final root verify371/0/0/26 baseline skips, target/mig-p1-verify-implementation-final.log.
Checks: packaged CLI smoke VERIFIED_PASS6 expected outcomes; 7 synthetic input files unchanged, target/mig-p1-cli-smoke-implementation.log.
Test first: original44 approved test hashes unchanged; architect read additional4 atomicity tests; intentional mutation EXECUTED_FAIL4/4 then removed.
Fixes: Markdown diagnostic underscore escaping, deterministic field-validation order; no weakened tests or dependency changes.
Context7: recorded before code; official Quarkus Aesh integration docs plus resolved-JAR fallback for standalone lookup ambiguity; target/mig-p1-context7-implementation.log.
Native executable: NOT_RUN; reflection registry tests passed. Forbidden-values suite remains baseline NOT_RUN without configured private list.
Review: independent product review pending; scoped code diff target/mig-p1-code.diff; implementation not yet accepted as complete.
Next: verifier focused/smoke; docs author; independent review; final root verification.

## MIG-23 P1 acceptance · 2026-10-26 · (hash in the next entry)
Delivered: strict opt-in dossier history, exact approval scope/revision, atomic supersession, current JSON/Markdown projection, stale dependencies and read-only CLI.
Docs: canonical skill routing, dossier reference and empty synthetic template, plan/scenario provenance, OUTPUT-MODEL and original spec changelog; no P2 gate changes.
Checks: verifier VERIFIED_PASS root verify exit0, 371 tests / 0 failures / 0 errors / 26 baseline skips; target/mig-p1-verify-final-verifier.log.
Counts: core254/0/0/22, native3/0/0/0, CLI34/0/0/1, MCP8/0/0/1, knowledge72/0/0/2.
Checks: after two documentation wording fixes, verifier VERIFIED_PASS Budget/Cites/SkillMirror16/0/0/0; target/mig-p1-doc-fix-verifier.log. Source/tests unchanged.
Checks: verifier VERIFIED_PASS fresh packaged CLI smoke6 cases and template2 cases; all inputs unchanged; target/mig-p1-smoke-final-verifier.log and mig-p1-template-checks.log.
Checks: approved44 test hashes unchanged; additional4 atomicity tests accepted and mutation caught4/4; independent focused50/0/0/0.
Checks: VERIFIED_PASS skill frontmatter unchanged, tracked/untracked whitespace checks, targeted privacy scan of additions0 hits; target/mig-p1-static-final-verifier.log.
Review: code r1 PASS; docs r1 FAIL on nullable diagnostic eventId JSON wording, corrected by same author; docs r2 PASS. No remaining blockers.
Limits: native executable NOT_RUN (registry tested); 24 skips for absent private fixtures; 2 forbidden-values checks NOT_RUN without configured list. Targeted scan is not full forbidden-values coverage.
Preservation: prior AGENTS/CLAUDE/WORKFLOW/CODEX-WORKFLOW hashes unchanged; existing private pilot evidence untouched. No tenant operation, deployment or push.
Decision: D-047; P1 only. P2–P7 remain planned, not authorized for execution. No whole customer migration plan or runtime result is claimed.
Next: owner may authorize P2 separately; MIG-21 ready and MIG-22 pending unchanged. Scoped local commit only; unrelated prior work stays outside it.

## Migration-process continuation / MIG-24 preparation · 2026-10-26 · uncommitted
Owner: continue remaining P2–P7 until stop; D-048 extends authorization, preserves original exclusions and role separation.
Previous task: MIG-23/P1 committed461ae13; code/docs/CLI checks and independent review are recorded above, not rerun or claimed for P2.
Task: scoped questions, assumptions, risks and milestone readiness; P2 packet in preparation. No P2 implementation/test result yet.
Baseline: P1 final independent371/0/0/26 applies to unchanged code at461ae13; prior local governance/pilot edits remain outside scoped commits.
Next: exact versioned input/API and acceptance, test-author red, implementation, docs, independent verification/review; proceed through remaining plan.

P2 design: D-049, opt-in v2 and separate DossierAssessment; explicit prerequisite graph, answer-bound revisions and whole-plan approval inputs.
Preflight dependencies: P1→P2 preserves existing record/JSON and adds v2; P2→P6 keeps provenance locators distinct from runtime evidence; P3→P4→P5 consumes source reports before comparison; P1–P6→P7 reads originals without rewriting. No parser dependency is required for P2.
Packet: docs/superpowers/plans/2026-10-26-mig-24-p2-packet.md. Known deliberate compatibility update: the old unsupported-version test moves its sentinel2 to999; v1 remains supported.
Preflight: r1 FAIL (final-plan exact scope, legacy consent import, explicit inventory), corrected; r2 FAIL (late connection-only coverage edge). Packet rewritten as revision3 around one effective-section union for all membership/readiness rules; no code existed during these reviews.

## MIG-24 P2 test approval · 2026-10-26 · uncommitted
Context compacted; actual checkout/status and last3 commits re-read; HEAD461ae13, unrelated local work preserved.
Review: rewritten packet revision3 preflight PASS; no production code existed during preflight.
Test first: architect read all new readiness18, reader16, CLI5 tests and additive native1; 40 approved, no production stub.
Compatibility: approved P1 unsupported-version fixture2→999 only; original assertion and native tests retained.
Checks: EXECUTED_FAIL independent core-reactor exit1 at testCompile,87 errors on missing DossierAssessment/assess; no tests executed.
Evidence: target/mig-p2-red-verifier.log tail read by architect; target/mig-p2-red-checks-verifier.log confirms5/5 hashes and allowed diff.
Author: EXECUTED_FAIL core red; ENVIRONMENT_BLOCKED standalone CLI due stale installed artifacts, not acceptance red; use reactor with-am.
Context7: author lookup recorded in target/mig-p2-context7-tests.log; production implementer must perform own API lookup.
Next: bounded implementation on revision3; independent checks, separate documentation author and review before commit, then continue P3–P7.

## MIG-24 P2 implementation handback · 2026-10-26 · uncommitted
Changed: strict v2 projection, immutable assessment, milestone graph/readiness, CLI flags/Markdown and native registry; P1 report shape retained.
Test first: original40 approved tests unchanged; architect read/approved4 additional DossierReadinessBoundaryTest regressions.
Checks: author VERIFIED_PASS focused94/0/0/0 (core76, CLI14, native4); architect read target/mig-p2-implementation-focused-final.log tail.
Checks: EXECUTED_FAIL additional red3/1 then4/2 for uppercase-digest stale-basis detection and exact verification-risk waiver; log tails read.
Evidence: target/mig-p2-boundary-red.log and mig-p2-boundary-red-r2.log; corrected code included in focused94 green.
Ruling: only an explicitly matching accepted risk may excuse assumption-created verification debt at the named scope/milestone; fact remains unknown, reopening blocks again.
Context7: direct public MCP resolve/query succeeded; exact Jackson3/Aesh JAR APIs inspected; target/mig-p2-context7-implementation.log.
Scope: approved5 test hashes intact; extra4 tests meaningful; no author docs/ledger/POM/private changes; writer released.
Next: documentation author; independent reactor/packaged CLI and code/docs review. P2 not yet accepted or committed.

## MIG-24 P2 independent focused checks and documentation · 2026-10-26 · uncommitted
Checks: verifier VERIFIED_PASS package exit0,94/0/0/0 (core76, CLI14, native4); target/mig-p2-focused-verifier.log and focused-checks-verifier.log tails read.
Checks: VERIFIED_PASS8/8 actual packaged CLI cases, correct selected/blocked/unknown/legacy exits and unchanged input bytes; target/mig-p2-smoke-verifier.log tail read.
Compatibility: original5 test hashes match; DossierReport unchanged from461ae13; no POM/dependency changes; scoped whitespace clean.
Docs: canonical migration skill references/templates/routes, OUTPUT-MODEL and original spec updated; v2 opt-in and unknown/exception distinctions explicit.
Checks: author static VERIFIED_PASS page budget max1419,164 concrete citations/0 errors, JSON example matches CLI fixture; logs target/mig-p2-doc-*.
Preservation: frontmatter/v1template hashes unchanged; CODEX-WORKFLOW and skill pointers untouched; no private evidence modified.
Review: independent code round1 running; documentation review pending. Potential chronological approval-history edge is under review, not accepted as fixed.
Full reactor/knowledge: NOT_RUN yet on final P2 docs; formal forbidden-values file absent, no full privacy-coverage claim.
Next: finish reviews and any bounded fixes, independent final reactor, scoped commit, then P3 metadata/behavior work.

## MIG-24 P2 code review round 1 · 2026-10-26 · uncommitted
Review: FAIL — whole-file parser errors collected before replay incorrectly suppress an earlier valid milestone approval.
Evidence: reviewer DossierValidator:44 / DossierReadiness:325; malformed later section-declare makes approvalEventId null; source inspection, runtime regression pending.
Required result: retain earlier applied approval ID, invalidate all final readiness flags; errors before approval still prevent it. Duplicate event IDs require positional care.
Acceptance: packet correction adds before/after/duplicate chronology and isolated known-question malformed evidence/reviewAt tests; no approved assertions weakened.
Nonblocking review: all44 new tests meaningful; other reviewed scope/basis/readiness/JSON/native boundaries had no blocker; isolated shape cases strengthen previously masked negatives.
Ruling: new revision means full Dependency identity; a genuinely new proposal/basis/approval may retain unchanged content, never transfer old consent silently.
Next: same implementer writes observed red and bounded correction; same reviewer round2; docs review continues independently.

## MIG-24 P2 review correction · 2026-10-26 · uncommitted
Test first: architect read all5 DossierReadinessChronologyTest cases and invalid-flags helper; observed red5/2/0/0 in target/mig-p2-review-fix-red.log.
Changed: reader retains per-occurrence parse results; validator replays diagnostics in event order. Existing invalid-prefix approval guard retained.
Checks: author VERIFIED_PASS focused99/0/0/0 (core81, CLI14, native4); architect read target/mig-p2-review-fix-focused.log tail.
Preservation: original5 approved hashes unchanged; boundary4 untouched;7-file manifest target/mig-p2-review-fix-hashes.log. New P2 tests total49.
Docs review: PASS independently across13 changed/new files; public contract, citations, unknowns, exceptions and human-task stop consistent.
Nonblocking docs wording: exact Dependency tuple is defined; packet clarifies new proposals may reuse unchanged content only with their own basis/approval.
Next: same code reviewer round2 and independent final root verify/packaged smoke; no P2 completion claim until these pass.

## MIG-24 P2 final reviews · 2026-10-26 · uncommitted
Code review: PASS round2; ordered per-occurrence diagnostics close the history bug without removing prefix/root/history/snapshot checks.
Docs review: PASS; no blocking discrepancies. Additional chronology5 assertions independently reviewed; all49 new tests meaningful.
Checks: verifier static VERIFIED_PASS7/7 test hashes, unchanged v1/frontmatter,4 governance hashes and4 pointer files; DossierReport unchanged, POM changes0.
Checks: scoped whitespace clean; targeted427 added lines plus10 new files privacy scan0 hits; target/mig-p2-static-final-verifier.log tail read.
Limits: targeted scan is not full forbidden-values coverage; configured forbidden-values file absent. Full reactor/fresh packaged smoke still running.
Next: observe final outputs, accept/commit P2 only if green; proceed to P3a. Draft metadata packet under target/mig-p3a-packet-draft.md is in read-only preflight.

## MIG-24 P2 acceptance · 2026-10-26 · (hash in the next entry)
Delivered: opt-in v2 scoped questions, explicit assumptions/risks, inventories, answer-bound revisions, milestone graph and exact whole-plan approval; P1 output/consent retained.
Tests:49 new synthetic tests read/approved; observed initial API red, boundary red4/2 and chronology red5/2; corrected focused99/0/0/0.
Checks: independent VERIFIED_PASS root verify exit0,420/0/0/26; core297/0/0/22, native4/0/0/0, CLI39/0/0/1, MCP8/0/0/1, knowledge72/0/0/2.
Evidence: target/mig-p2-verify-final-verifier.log; actual counts/skips and final tail read by architect. No kb.skip.
Checks: VERIFIED_PASS fresh packaged CLI10/10, actual expected selection exits and JSON/Markdown; all3 input hashes unchanged; target/mig-p2-smoke-final-verifier.log.
Checks: VERIFIED_PASS Budget2/Cites1/SkillMirror13 in root run;7/7 approved test hashes, v1/frontmatter,4 governance hashes/4 pointers, P1 report and POMs preserved.
Checks: VERIFIED_PASS scoped whitespace and targeted privacy427 added lines+10 new files,0 hits; target/mig-p2-static-final-verifier.log.
Review: code r1 FAIL chronology, fixed with observed red then r2 PASS; docs r1 PASS. No blocking findings remain.
Limits:24 absent-private-input skips and2 unset kb.forbidden skips verified from XML; full forbidden-values coverage and native executable NOT_RUN; registry tested.
Preservation: original private evidence and unrelated pending governance/pilot work untouched; no tenant operation/deployment/push/customer-contract change.
Decisions: D-048 authorization and D-049 v2 contract; revision identity and exact accepted-risk verification waiver clarified in packet.
Next: scoped local P2 commit, then P3a metadata packet preflight corrections; P3–P7 continue under owner authorization until stop.

## MIG-24 commit / MIG-25 P3a preparation · 2026-10-26
MIG-24 committed125865f3efd4a1643948c0f38cf94142e5930d4f:33 files,2949 insertions132 deletions; no push.
Checks: verifier VERIFIED_PASS precommit exact scope,7/7 hashes,26/26 product index/worktree bytes, staged privacy0; target/mig-p2-precommit-verifier.log tail read.
Preservation: CODEX-01/MIG-07/D-046 and unrelated governance edits remain unstaged/untracked; no prior work discarded.
MIG-25 selected under D-048: P3a retained parsed BPM source evidence, before P3b paths/data and P3c configured integration boundaries.
Preflight: r1 FAIL on process-partition selection ambiguity and nullable PropertyBag keys; revision2 fixes candidate set/order and adds Property records without losing null evidence.
Preflight: r2 PASS; exact source occurrence identity, selective support closure, explicit unresolved diagnostics, legacy compatibility and synthetic extraction acceptance approved.
Decision: D-050; public sourceEvidence extension draft review at hand-over; original model meanings and shared schema0.1 retained.
Packet: docs/superpowers/plans/2026-10-26-mig-25-p3a-packet.md; base125865f; no P3 production or test result yet.
Next: independent test-author with Context7, observed red, architect test reading, bounded implementation, verification and review.

## MIG-25 P3a test approval · 2026-10-26 · uncommitted
Test first: architect read all4 files and approved20 new tests (core17, CLI2, native1) plus synthetic Process Composer SCA fixture.
Checks: author EXECUTED_FAIL exit1 at core testCompile on missing BpmSourceEvidence/sourceEvidence API; target/mig-p3a-red.log tail read; no tests executed.
Fixture: author javac exit0/JShell actual PoXray read observed1 BPM/1 process, initial mapping and disabled structural root/child; target/mig-p3a-fixture-check.log tail read.
Scope: original native assertions retained, no production stub/source changes;4 approved hashes in packet and target/mig-p3a-approved-test-hashes.log.
Context7: official lookups before test code; ambiguous Java ZIP lookup resolved by exact JDK/JAR APIs; target/mig-p3a-context7-tests.log.
Doc status: retrospective design's historical 'command does not exist' sentence updated to reflect completed P1/P2; no behavior changed.
Next: independent red/scope verification, then bounded metadata implementation. P3b public-source scouting continues without claiming runtime behavior.

## MIG-25 continuation · 2026-10-26 · uncommitted
Context compacted; re-read CODEX-WORKFLOW/HANDOFF, git status and log -3. HEAD remains125865f; approved P3a tests and unrelated edits preserved.
Independent verifier assigned the approved20-test compile-red check; production implementation has not started.
Checks: independent EXECUTED_FAIL exit1,95 missing API compile errors,0 executed tests; target/mig-p3a-red-verifier.log counts/tail read.
Scope:4/4 hashes match; production/POM diff empty. Implementation authorized under canonical packet; final root verification remains pending.

## MIG-25 P3a implementation handback · 2026-10-26 · uncommitted
Writer released: parsed occurrence evidence, immutable explicit-null/empty model, reader selection, diagnostics, Markdown and native registration implemented.
Checks: author first focused EXECUTED_FAIL core17/0/2/0 from missing Pool.identifier rendering; fixed within DocumentBuilder, approved tests unchanged.
Checks: author focused+legacy VERIFIED_PASS56/0/0/16; target/mig-p3a-implementer-focused-legacy.log tail read; all20 new tests pass.
Limits:16 private-input skips in this bounded run; final root verify, packaged smoke and independent code/docs review remain pending.
Scope:4 approved test hashes unchanged; no dependency/POM changes. Context7 calls logged in target/mig-p3a-implementer-context7.log.
Next: bounded documentation author and independent code reviewer, then final independent verification. P3b graph/producer split reviewed as sound but exact packet remains pending.

## MIG-25 P3a independent review · 2026-10-26 · uncommitted
Review: code r1 PASS; all20 tests assessed against wrong behavior,4 approved hashes retained, no blocking findings.
Documentation writer released: OUTPUT-MODEL and canonical bpm/intake references only; independent documentation review next.
Checks: final independent root verify and packaged CLI smoke authorized after documentation release; results pending.
Evidence: target/mig-p3a-code.diff, target/mig-p3a-doc.diff; final root and smoke outputs will be recorded before commit.
Review: documentation r1 PASS; exact source fields/commands/citation headings and limitations verified independently; budgets1423/1437.
Checks: first independent root EXECUTED_FAIL core314/0/14/22 from local loopback sandbox restriction; failing section/tail read in target/mig-p3a-verify-final-verifier.log.
Retry: same root verify authorized with local sandbox escalation, no source/test/config changes; target/mig-p3a-verify-final-verifier-escalated.log pending.

## MIG-25 P3a acceptance · 2026-10-26 · ready to commit
Test first:20 approved tests, independent compile-red exit1/95 missing API errors;4 approved hashes retained.
Changed: parsed BPM occurrence evidence/diagnostics, immutable model, compatible constructor, initial/structural Markdown mappings, native registry, output/skill docs.
Checks: independent root VERIFIED_PASS440/0/0/26; target/mig-p3a-verify-final-verifier-escalated.log tail and target/mig-p3a-final-counts.log read.
Modules: core314/0/0/22,native5/0/0/0,CLI41/0/0/1,MCP8/0/0/1,KB72/0/0/2. No kb.skip or new skips.
CLI: VERIFIED_PASS fresh packaged json/English export/Czech export,3/3 exit0; source SHA unchanged; target/mig-p3a-smoke-export-checks.log read.
Observed: schema0.1,extension1/bpm-source-v1,15 elements/15 links,2 unresolved endpoints; initial/structural/disabled/escaped Markdown and raw locators/flags present.
Review: code r1 PASS; documentation r1 PASS. Doc budgets1423/1437; root citation/budget/mirror checks included.
Preservation: governance4/4,test hashes4/4 unchanged; no POM/skill-frontmatter/pointer changes; targeted added-text privacy0 hits.
Limits: actual skipped reasons confirm24 absent private inputs and2 unset kb.forbidden checks; native executable/full forbidden-values NOT_RUN.
Initial sandbox root EXECUTED_FAIL14 local-loopback errors; identical approved escalation retry passed. Logs preserve both outcomes.
Decisions: D-050; no runtime, producer or whole-P3 completion claim. No private pilot evidence read/modified; no push.
Next: scoped local commit, then MIG-26 structural path packet revision2 preflight and separate red tests; P3–P7 continue until owner stop.

## MIG-25 commit / MIG-26 preparation · 2026-10-26
MIG-25 committedc8b4f86821063b80aef936d3063e154385314286:22 files,1486 insertions23 deletions; no push.
Checks: independent precommit VERIFIED_PASS22/22 paths,4/4 test hashes,19/19 worktree and3/3 staged-ledger copies,privacy0; target/mig-p3a-precommit-verifier.log tail read.
Preservation: unrelated CODEX-01/MIG-07/D-046/governance edits remain unstaged; MIG-26 draft excluded from MIG-25 commit.
MIG-26 selected under D-048/D-051: structural graph/condition/loop declarations first, candidate data producers next, integration boundaries and composite views after.
Preflight: r1 FAIL unsupported control relations, loop selection/attribution and boundary validation; revision2 defines exact conservative handling and adversarial cases.
Preflight: r2 PASS, public record/interface contract and source-cycle exception accepted; basec8b4f86 filled before test-author dispatch.
Packet: docs/superpowers/plans/2026-10-26-mig-26-p3b-paths-packet.md; no MIG-26 tests/code or runtime acceptance claimed yet.
Next: separate test-author with Context7 and observed red, architect reads/approves tests, bounded implementation, independent verification/review.

## MIG-26 test approval · 2026-10-26 · uncommitted
Test first: architect read both full new files/helpers and additive native diff; approved37 tests (31 graph,5 report,1 native).
Checks: author EXECUTED_FAIL exit1/100 missing BpmPath API compile errors,0 executed tests; target/mig-p3b-paths-red.log tail/counts read.
Fixtures: literal MOIN/SCA extraction; scope-cycle case alone augments captured evidence to avoid unguarded legacy reader recursion; five-second cycle guards.
Scope: existing native assertions and SyntheticBpmSca unchanged; no production/POM edits. Exact3 hashes in packet and target/mig-p3b-paths-test-hashes.log.
Context7: direct tools available for author resolve/query; Java official match unavailable, exact JDK fallback recorded; target/mig-p3b-paths-context7-*.log.
Next: independent compile-red/scope evidence, then bounded implementation; final root verification/review pending.
Checks: independent EXECUTED_FAIL exit1/100 missing API errors/0 tests; log counts and summary read under target/mig-p3b-paths-red-verifier*.
Scope:3/3 hashes match; production/POM/fixture-helper diff empty; native assertions unchanged. Bounded implementation authorized.

## MIG-26 prerequisite reader repair · 2026-10-26 · uncommitted
Deviation: F-14. Writer released partial graph/report implementation;37 approved tests remain unchanged.
Checks: author EXECUTED_FAIL36/0/2/0; two approved cases stop before analysis at Optional.of(null) and quoted-type Set.contains(null).
Evidence: target/mig-p3b-paths-implementer-focused-r2.log traces/counts read; unchanged MoinIndex.first and ExpressionReader.literal inspected.
Checks: author reports legacy/native41/0/0/0 on identical escalated retry; quiet log contains warnings only, so independent count evidence remains pending.
Decision: narrow packet amendment permits exactly the two null guards, each verified against its existing observed-red test, then full focused acceptance/legacy checks.
Preserve raw endpoint lists, first-match order, unknown typing and all approved expectations. No broad parser refactor or fixture bypass.
Next: resume same implementer under F-14; independent root verification and code/docs review still pending.

## MIG-26 focused implementation acceptance · 2026-10-26 · uncommitted
Writer released graph/report/native implementation and exactly two F-14 guards;37 approved tests and3 hashes unchanged.
Checks: author VERIFIED_PASS F-14 endpoint1/0/0/0 then literal1/0/0/0; both log tails with actual counts/exit read.
Checks: author final focused VERIFIED_PASS83/0/0/0,exit0; target/mig-p3b-paths-implementer-final-focused-r2.log tail read.
Scope: MoinIndex.first only Optional.ofNullable; ExpressionReader.literal only null guard. No second()/other parser changes; original source fields preserved.
Self-review follow-up: separate test-author will add three public-contract integrity cases for external boundary membership, malformed loop/control links and pool-as-member ancestry.
These are post-implementation regression checks, not falsely reported as initial red-first tests. Initial37 acceptance remains red-first.
Next: architect reads added tests; independent code/docs review and full reactor verification remain required before commit.

## MIG-26 integrity approval / review handoff · 2026-10-26 · uncommitted
Architect read the full independent BpmPathIntegrityTest and approved3 synthetic cases, including all helper assertions.
Checks: author VERIFIED_PASS3/0/0/0,exit0; target/mig-p3b-paths-integrity.log tail read. These were post-implementation checks, no red-first claim.
Scope: only one new test file; original3 approved hashes unchanged. New integrity SHA857BB5E64A3D4B28A12E27CD66807A4F21F7A97963B23207AF43BF9A99CA544A.
Total new tests40:37 initial red-first plus3 independent integrity checks. Full root expected480 tests if no further changes, not yet run.
Next: bounded documentation author and independent code review using target/mig-p3b-paths-code.diff, then final independent root verification.

## MIG-26 review round1 · 2026-10-26 · uncommitted
Review: code FAIL — malformed pool relation with wrong-kind owner is ignored; unlinked Pool/Collaboration scope members are discarded, allowing false complete/disconnected results.
Evidence: independent static review at BpmPathGraph selectRootAndMembers/checkRelations and NON_NODES/collectMemberships; counterexamples not yet executed.
Next: separate test-author adds3 synthetic observed-red regressions, architect reads them, same implementer corrects graph, same reviewer returns round2 verdict.
Docs writer released: OUTPUT-MODEL plus canonical bpm reference only; bpm1473/1500, bounded checks pass; independent docs review next.
Full root verification has not started; no repeated build before correcting the known review findings.

## MIG-26 completeness regression approval · 2026-10-26 · uncommitted
Context compacted; re-read Codex override/HANDOFF, actual status and last3 commits; base remains c8b4f86.
Architect read complete new test file/helpers; approved3 regressions with public source evidence, unknown connectivity and retained unknown member identity.
Checks: author EXECUTED_FAIL3/3/0/0,exit1; target/mig-p3b-paths-completeness-red.log counts/tail read; each failed expected incomplete versus actual complete.
SHA cb12ee7750822c89006947c1b82c2801d01d16b2af4303e84160e191e070a079; prior4 hashes unchanged. Total new tests43.
Next: independent red confirmation, same implementer bounded correction, same reviewer round2; final root still pending.
Checks: independent EXECUTED_FAIL3/3/0/0,exit1; same three coverage assertions; verifier log counts and summary read.
Preservation:5/5 approved hashes and7/7 production snapshots unchanged during independent red. Bounded graph correction authorized.

## MIG-26 review correction · 2026-10-26 · uncommitted
Writer released; correction changes only BpmPathGraph. Attributable malformed pool declarations create gaps; Pool/Collaboration members retain unknown rows and identity.
Checks: author VERIFIED_PASS3/0/0/0 completeness regressions and89/0/0/0 combined focused (core83/native6),exit0; correction log tails/counts read.
Preservation:5 approved test hashes unchanged;6 other production hashes unchanged. Known local-loopback fixtures used escalation without skips/config changes.
Review: documentation r1 PASS, bpm1473 words; nonblocking explicit-null wording will be clarified for new records only, preserving shared Issue behavior.
Next: same code reviewer round2; narrow documentation clarification, then independent full reactor verification. Root verify NOT_RUN so far.
Review: code r2 PASS; both blockers closed,5 approved hashes match, original3 regressions detect their defects and positive membership witnesses remain.
Docs precision author released: only new-report serialization sentence clarified; whitespace/source links/privacy pass. Final independent root and precision review dispatched.
Review: final documentation precision PASS; no remaining documentation blockers. Author links/privacy/text evidence read; no shared Issue serialization change.

## MIG-26 P3b paths acceptance · 2026-10-26 · ready to commit
Changed: occurrence-based structural graph, conditions, declared loops, immutable report/Markdown, native registry and docs; exact two F-14 null guards.
Test first:37 initial acceptance tests;3 post-implementation integrity checks;3 independently confirmed red review regressions. Total43 new tests;5 approved hashes retained.
Checks: independent root VERIFIED_PASS483/0/0/26,exit0,05:31; target/mig-p3b-paths-verify-final-verifier.log tail and final-counts/summary logs read.
Modules: core356/0/0/22,native6/0/0/0,CLI41/0/0/1,MCP8/0/0/1,KB72/0/0/2. No kb.skip or new skips.
Review: code r1 FAIL on two completeness gaps, separate red3/3/0/0, correction green3/0/0/0 and focused89/0/0/0, code r2 PASS. Documentation PASS including final precision.
Preservation: test hashes5/5,governance4/4,production/docs9/9 unchanged during verification; POM,fixture helper,skill pointers/frontmatter and P1/P2 contracts unchanged.
Docs: Budget2/Cites1/SkillMirror13 pass; bpm1473 words;4 source links resolve. Scoped whitespace clean; restricted added-line privacy0 hits.
Limits: actual XML skips24 absent private-input cases and2 unset kb.forbidden. Native executable/full forbidden-values NOT_RUN, reasons recorded in final-summary.log.
No new CLI/MCP command in this slice; existing CLI41 tests pass. No runtime/whole-P3 completion claim, private pilot modification or push.
Next: selective local commit; MIG-27 data-producer draft preflight running, then separate red tests/implementation under continuous owner authorization.

## MIG-26 commit / MIG-27 preparation · 2026-10-26
MIG-26 committed cb81f9506515ebbec54c87d0ca4770609d55c9e2:23 files,2872 insertions21 deletions; no push.
Checks: precommit independent VERIFIED_PASS23/23 paths,20/20 worktree blobs,3/3 selective ledger copies,5/5 test hashes,9/9 verified snapshots; evidence tail read.
Preservation: unrelated CODEX-01/MIG-07/D-046/governance changes and MIG-27 draft excluded from commit, remain local.
MIG-27 selected under D-048/D-051: contextual mapping declarations, source read domains and structural candidate producer sets.
Preflight r1 FAIL on7 contract gaps: read/mapping payload, grammar, owner-direction pairs, intra-step order, uncertainty transfer and orphan attribution.
Revision2 specifies evidence closure, exact grammar/pairs, candidate-plus-uncertainty union and component-qualified orphan handling; same reviewer round2 next.
No MIG-27 tests/code yet; accepted MIG-26 reactor483/0/0/26 is baseline, not verification of the new feature.
Preflight r2 PASS; exact MappingPartContainsExpression spelling and argument-enclosure versus cycle-before precision incorporated. Proposed acceptance method names added.
Decision D-052: independently versioned contextual data report embeds the accepted path report; known producer candidates and uncertainty remain separate.
Next: separate test-author writes synthetic source/report/native cases and observes red; implementation awaits architect reading/approval and independent red verification.

## MIG-27 test approval · 2026-10-26 · uncommitted
Architect read all37 analysis tests, complete fixture/report files and additive native diff; approved43 tests (42 core,1 native).
Correction before approval: external excluded-feature fixture moved from PROCESS to METADATA; final method/source-precondition assertions re-read. No weakened expectations or source edits.
Checks: author EXECUTED_FAIL missing BpmData API compile errors,0 tests executed; red-escalated log inspected. Frozen final files require independent red confirmation next.
Author initial sandbox access-denied/stall and interrupted final repeat are not acceptance evidence; no fullroot run before implementation.
Context7 deferred tools unavailable; configured endpoint resolve/query succeeded after DNS escalation; exact JDK/JAR fallback documented, no new dependencies.
Scope:4 frozen test/helper hashes in packet; existing4 path tests and SyntheticBpmSca unchanged; production/POM diff empty. Independent verifier next, then bounded implementer.
Checks: independent EXECUTED_FAIL exit1/100 missing-API compiler errors/0 tests; red-verifier log tail and summary read, offline escalated run01:27.
Preservation:4/4 approved hashes,4/4 prior path hashes; production/POM/helper diff empty; native14 additions/0 deletions. Implementation authorized within packet paths.

## MIG-27 implementation handback · 2026-10-26 · uncommitted
Context compacted; re-read Codex override/HANDOFF, actual status and last3 commits; base remains cb81f95.
Writer released: six new data helper/model files, additive27-line immutable graph scope view and native registration; tests/parser/POM unchanged.
Checks: author VERIFIED_PASS49/0/0/0 focused and final132/0/0/0 combined (core125/native7),exit0; final log tail/counts read.
Preservation:4 approved data/native hashes and4 original path hashes match; Context7 official OpenJDK25/Jackson/Quarkus retrieval summary read.
Limits: source candidates and uncertainty only; no runtime values, ordering proof, CLI/MCP entry or whole-P3 completion claim.
Next: independent code review of target/mig-p3b-data-code.diff and bounded documentation author; final independent reactor pending.

## MIG-27 review round1 · 2026-10-26 · uncommitted
Code review FAIL: nested text-only Condition branch loses uncertainty; empty StepState loses unknown object visibility; disconnected child initializer contaminates unrelated scopes; malformed initializer part loses its known object target.
These are static counterexamples, not yet observed failing tests. Packet adds separate source-based completeness regressions before correction; original43 tests remain frozen.
Docs writer released: only OUTPUT-MODEL/bpm reference,205 additions7 deletions,1456 words,6 source links and targeted privacy0; author evidence read.
Docs review FAIL on one sentence: loop-local unknown applies to contextual reads; external-output stays external. Same author will make the narrow clarification.
Next: test-author observed-red regressions, architect approval and independent confirmation, same implementer correction, same reviewers round2. Fullroot NOT_RUN.
Preparation only: MIG-28 fault-declaration packet drafted separately; no tests/code/acceptance claim and excluded from MIG-27 commit.

## MIG-27 completeness regression approval · 2026-10-26 · uncommitted
Reviewer r1 addendum found ambiguous PART_EXPRESSION owner reachable through orphan parts; accepted as fifth defect, without claiming false complete provenance for that case.
Architect read full new BpmDataCompletenessTest/helpers; approved7 synthetic regressions (initializer terminal variants are separate cases).
Checks: author EXECUTED_FAIL7/7/0/0,exit1,assertion failures only; final-red log tail read. SHA176DC48037C5C23EBE1CAA20A4D84A867D92AB6AA1A259EF26BEB3ED8DFA208B.
Original4 approved data/native and4 path hashes unchanged. New total50 tests:43 initial plus7 review regressions; fullroot not yet run.
Next: independent red confirmation; source correction and narrow docs precision after writer grants. MIG-28 draft preflight is read-only preparation.
Checks: independent EXECUTED_FAIL7/7/0/0,exit1,assertion failures only; verifier log tail and summary read. Approved9 test/helper hashes and8 production snapshots unchanged.
Bounded source correction authorized; finalroot remains pending. Separate docs precision uses the same author before source writer resumes.

## Owner scope amendment · 2026-10-26
Owner explicitly requested: finish P3 and then stop. This supersedes D-048 continuous P4–P7 execution authorization for this run.
Complete the current data corrections, source fault declarations, configured integration composition and CLI/MCP delivery with required verification/review; then stop.
P4–P7 remain unimplemented; prior source research is preparation only. No tenant operations or push.
Previous ledger write was not executed because automatic approval review could not complete due to account usage limit; no unsafe-action rejection or bypass.
The same normal write succeeded after the owner's renewed instruction; work resumed without an alternate approval path.
Docs precision writer released: only loop-read sentence qualified, preserving external-output binding; changed text/privacy evidence read. Independent r2 precision review pending.
MIG-28 design preflight r1 FAIL on5 contract gaps; revision2 closes kinds/state precedence/unconsumed evidence/per-owner scopes/orphan occurrence representation. Same reviewer r2 pending; no MIG-28 tests/code yet.

## MIG-27 correction handback · 2026-10-26 · uncommitted
Same implementer released: only BpmDataExpressions/Mappings/Flow corrected;5 other production snapshots unchanged,9 approved test/helper hashes retained.
Checks: author VERIFIED_PASS7/0/0/0 regressions and139/0/0/0 combined (core132/native7),exit0; both log tails/counts read.
Docs r2 precision review PASS; original single wording blocker closed. Same code reviewer r2 and independent root verify are next.
MIG-28 draft preflight r2 PASS on revision2, all5 design blockers closed; test/implementation still gated on MIG-27 acceptance/commit.
Review: MIG-27 code r2 PASS; all5 findings closed,7 new tests protect their counterexamples,9 hashes independently match. Final reactor is running against frozen source/docs.

## MIG-27 P3b data acceptance · 2026-10-26 · ready to commit
Changed: contextual mapping/read evidence, domains and structural candidate producer sets with independent uncertainty; immutable report/Markdown, native registry and documentation.
Test first:43 initial tests plus7 independently confirmed assertion-red review regressions;50 new tests total. Five r1 findings corrected; code r2 and docs r2 PASS.
Checks: independent root VERIFIED_PASS533/0/0/26,exit0,03:37; target/mig-p3b-data-verify-final-verifier.log tail and final-counts/summary read.
Modules: core405/0/0/22,native7/0/0/0,CLI41/0/0/1,MCP8/0/0/1,KB72/0/0/2. No kb.skip or new skips.
Preservation:9/9 approved hashes,4/4 governance hashes,10/10 production/docs snapshots unchanged; POM,old core tests,fixture helper,P1/P2 contracts and skill pointers/frontmatter preserved.
Docs: bpm1456 words,6 source links, Budget2/Cites1/SkillMirror13 pass; scoped whitespace clean,0 sensitive added-line matches (4 public SAP links excluded from raw matches).
Limits:24 absent private-input cases and2 unset kb.forbidden skips; native executable/full forbidden-values NOT_RUN with reasons in final-summary. No new CLI command in this slice.
Scope D-053: complete remaining P3 source faults and configured integration/CLI/MCP, then stop; P4–P7 unimplemented. No tenant operation, private pilot edit or push.
Next: selective local commit, excluding unrelated governance/pilot changes and both MIG-28 drafts. MIG-28 revision2 preflight PASS; its tests wait for the accepted base.

## MIG-27 commit / MIG-28 preparation · 2026-10-26
MIG-27 committed 0c5c6a0b422caa87b6c22c969d33cc5efe2614db:22 files,3324 insertions23 deletions; no push.
Checks: independent precommit VERIFIED_PASS22/22 paths,19/19 worktree blobs,3/3 selective ledger copies,9 approved hashes and10 verified snapshots; evidence tail read.
Preserved: unrelated CODEX-01/MIG-07/D-046/governance edits remain local; both MIG-28 drafts were excluded.
MIG-28 selected under D-053: source-declared activity/boundary/start/end fault contexts and raw payload/uncertainty, before configured integration and CLI/MCP composition.
Preflight r1 FAIL on5 contract gaps; revision2 closes source kinds,state precedence,unconsumed metadata,per-owner scopes and orphan representation; same reviewer r2 PASS.
Next: separate test-author writes source/report/native regressions and observes red; architect approval and independent red precede implementation. Baseline533/0/0/26 belongs to MIG-27, not this new feature.

## MIG-28 test approval · 2026-10-26 · uncommitted
Context compacted; re-read Codex override/HANDOFF, actual status and last3 commits; base0c5c6a0 preserved.
Architect read full24 analysis tests,5 report tests, helper and additive native test; approved30 new tests, four frozen hashes in target/mig-p3c-faults-test-author-hashes.log.
Author EXECUTED_FAIL missing BpmFaultAnalysis/BpmFaultReport compiler diagnostics; red log tail read. Numeric exit unavailable from truncated completion; not invented.
Nine prior core/helper hashes unchanged; native14 additions0 deletions. Runtime fixture/native acceptance NOT_RUN until implementation.
Next: independent compile-red with numeric exit, then bounded implementation. Scope remains finish P3 then stop.
Checks: independent EXECUTED_FAIL compile-red exit1,100 missing introduced API diagnostics,0 runtime tests; red summary/tail read. Frozen4/4 and prior9/9 hashes match; production/POM unchanged.
Automatic review initially misclassified P3c1 as outsideP3; original approvedP3 scope evidence resolved it and the identical normal request was accepted. No bypass or unresolved approval blocker.
Bounded MIG-28 implementation now authorized. MIG-29 composite offline report draft is independent preflight preparation only; no tests/code yet.

## MIG-28 implementation handback · 2026-10-26 · uncommitted
Writer released: five new core helper/model files and additive5-line native registration only; no parser/graph/data/test/POM changes.
Checks: author VERIFIED_PASS37/0/0/0 focused (core29/native8) and169/0/0/0 combined (core161/native8),exit0; log tails/counts read.
Frozen4/prior9 hashes13/13 unchanged. Context7 Java25/Jackson/Quarkus evidence summary read; no prerequisite failure or expectation change.
Next: independent code review, bounded docs, then independent root verify. Fullroot/native executable NOT_RUN; no acceptance claim yet.
MIG-29 draft r1 preflight found4 contract gaps; revision2 closes call grammar/precedence, receiver extractor projection, route placeholders and channel availability/aggregate states. Final file preflight pending; no tests/code authorized.
Docs writer released: OUTPUT-MODEL and canonicalbpm only,169 additions3 deletions; observed whitespace/source-links/headings/privacy exit0, bpm1476/1500 and sensitive added-line matches0. Independent review pending.
MIG-29 revision2 saved, including null-map-key rejection instead of inventing JSON keys; source projection null values/list holes remain preserved. This is preparation only, excluded from MIG-28 commit.

## MIG-28 review round1 · 2026-10-26 · uncommitted
Code review FAIL: condition relation parent/child kinds unchecked; malformed boundary attachment suppresses independent start-trigger; conflicting boundary membership inside one process remains declared; shared condition-only metadata bypasses Unattributed rule.
All30 tests were meaningful and13 hashes matched; these additional combinations were absent. Findings are static counterexamples, not yet observed failures.
Packet adds bounded grammar and six separate completeness regressions before source correction. Original30 tests/helper hashes stay frozen; independent red and same reviewer r2 required.
MIG-29 final saved revision2 design preflight PASS, four r1 blockers closed; tests still wait for accepted MIG-28 commit. Owner scope remains completeP3thenstop.

## MIG-28 regression approval · 2026-10-26 · uncommitted
Architect read full BpmFaultCompletenessTest and helpers; approved six real-source regressions, SHA5684C21AFE9F7913A9C4E6B80E0BA777CBAC61DA607CB47E7A6A6F1208303C71.
Author EXECUTED_FAIL6/6/0/0,exit1; counts and failing sections read: three false-complete grammar cases, missingstartcontext, falselydeclared boundaryscope, missingunattributed condition. No parser failures.
Writer released;13 originaltest/helper and6 production hashes unchanged. Independent assertion-red confirmation precedes correction of Evidence/Declarations only.
Documentation wording r1 PASS,1476words; final scoped-diff/source consistency review remains after correction. Fullroot acceptance still NOT_RUN.
Checks: independent EXECUTED_FAIL6/6/0/0,exit1,1.127s suite; verifier summary/failure excerpts and logtail read. New+prior14 hashes and sixproduction snapshots match before/after.
Same implementer now authorized to correct only BpmFaultEvidence/BpmFaultDeclarations; immutable approved regressions remain acceptance.

## MIG-28 correction handback · 2026-10-26 · uncommitted
Writer released: only Evidence/Declarations changed; four otherproduction snapshots retained,14 approvedtest/helper hashes unchanged.
Checks: author VERIFIED_PASS6/0/0/0 regressions and175/0/0/0 combined(core167/native8),exit0; finaltails/counts andpreservation read.
All four review defects corrected without changing expectations; same code reviewer r2 and finaldocs scoped-diff/sourceconsistency review now pending, then independentroot verification.
Final docs consistency review found one wording blocker: attributable catch-condition links become Condition rows, while Unattributed has raw evidence only. Same author receives this single-sentence correction; no source/model expansion.
Code review r2 PASS: all four findings closed, six regressions meaningful,14 approvedhashes andfour untouchedproduction independently retained.
Docs author released narrowparagraph correction; observed whitespaceexit0 andprivacy0matches. Same reviewerfinalcheck andindependentroot nowpending; source/docs frozen.

## MIG-28 source fault acceptance · 2026-10-26 · ready to commit
Changed: occurrence-qualified source activity/boundary/start/end declarations, per-owner scope evidence, raw conditions/fault payload, catalogue gaps, immutable report/Markdown and native registry.
Test first:30 initialtests plus6 independently confirmed assertion-red completeness regressions;36newtests. Code reviewr2 PASS; finaldocs PASS afteroneprecisioncorrection.
Checks: independentroot VERIFIED_PASS569/0/0/26,exit0,02:18; target/mig-p3c-faults-verify-final-verifier.log tail/counts/summary read.
Modules:core440/0/0/22,native8/0/0/0,CLI41/0/0/1,MCP8/0/0/1,KB72/0/0/2. New36allpass, no newskips orweakenedchecks.
Preservation:14approvedhashes,8source/docssnapshots,4governancehashes unchangedbefore/after. Parser/graph/data/POM/oldcoretests/CLI/MCP preserved; nativeadditiveonly.
Docs:Budget2/Cites1/SkillMirror13 pass; bpm1476/1500words;5sourcelinksresolve/headingspreserved; whitespaceclean,0sensitiveprivacyhits (3publicSAPURLs excluded).
Limits:24absentprivate-fixture and2unsetkb.forbidden skips remainunverified; nativeexecutable/fullforbiddenvalues NOT_RUN withenvironmentreasons. No runtime orwholeP3completion claim.
Next: selective localcommit19ownedpaths excludinggovernance/pilot/MIG29drafts, thenfinalP3configuredICO/CLI/MCP delivery perapprovedMIG29revision2. StopafterP3;noP4–P7/tenant/push.

## MIG-28 commit / MIG-29 preparation · 2026-10-26
MIG-28 committed3e2b716a5ca43a81a39b31ac234715c5c81fd55f:19files,2496insertions17deletions; no push.
Checks: independentprecommit VERIFIED_PASS19/19paths,16worktreeblobs+3selectiveledgers,14approvedhashes/8verifiedsnapshots/4governancehashes,whitespaceexit0/privacy0; audit tailread.
UnrelatedCODEX01/MIG07/D046/governance preserved; MIG29drafts excluded. Product/test workingtree clean aftercommit.
MIG-29 selected underD053: finalP3 composite source/configuredintegration report andofflineCLI/MCP; base3e2b716.
Revision2 independentdesignpreflight PASS after4contractclarifications; separate realSCA/TPZ/report/CLI/MCP/native tests authorized, thenarchitectapproval/independentred. No implementationyet.
Baseline569/0/0/26 is MIG-28 evidence, not newbehavior delivery. P4–P7 remain unimplemented; stopafterP3acceptance.

## MIG-29 test approval · 2026-10-26 · uncommitted
Architect read all nine files and finaldeltas;51newtests approved (40core,6CLI,4MCP,1native). Frozen manifest target/mig-p3c-behavior-test-author-hashes.log.
Pre-freeze expectation corrected to unknown for incompleteoutboundroutes; maporder asserts added. No production workaround or weakened sourcecontract.
Author EXECUTED_FAIL finalcompile-red exit1 forintroduced BehaviorReport/BehaviorAnalysis; logtailread,no runtimetests executed. Independentconfirmation pending.
Real fixture prerequisite VERIFIED_PASS readerexit0:1process,2interfaces,1service,2ICOs,3channels. Helper compileexit0 withone missingJacksonannotationclasspath warning; actualrun fullruntime dependencies passed.
227previous source/test hashes unchanged; native14additions0deletions. Runtimebehavior/CLI/MCP/native acceptance NOT_RUN untilimplementation; no platformACL assumptions orskippedtests.
Next: independentred, thenboundedimplementation on3e2b716. FinalP3only; noP4–P7/tenant/push.
Checks: independent EXECUTED_FAIL compile-red exit1,100missing introducedAPI diagnostics,0runtime tests; summary/tailread. Frozen9/9 andprior227/227 hashes matchbefore/after; production/POM emptydiff,native14additions0deletions.
BoundedMIG-29 implementation authorized with frozenacceptance; source/configurationsemantics remainpacketrevision2. Fullroot/newruntime acceptance stillpending.

## MIG-29 implementation handback · 2026-10-26 · uncommitted
Context compacted; CODEX-WORKFLOW/HANDOFF reread, status and log3 reconfirmed before changes.
Writer released: eight new and three additive production files; existing parsers/analyses/ScenarioDiscovery/JSON helpers/POM/tests unchanged.
Checks: author VERIFIED_PASS243/0/0/0 combined(core224/native9/CLI6/MCP4),exit0; summary/counts/logtails read. SeparateCLI6/MCP4 passed with actual inline and full-file spill.
First focused EXECUTED_FAIL native9/1/0/0 after core40pass: existing allModelClassesAreRegistered requires BehaviorSnapshots in targets. Root read test and assertion section.
Ruling: helper public final/private constructor/package-private copying methods and native registration; smallest compatibility change, no public operations or expectation changes. Packet amended; independent review must check it.
Preservation: frozen9/9 and prior224/224 hashes retained; source whitespacepass. Context7 Java/Jackson/Quarkus/MCP plus exactJDK/JAR fallback recorded; no newdependencies.
Next: independent code review and bounded four-file documentation, then independent rootverify/fresh packagedCLI. WholeP3acceptance stillpending; stopafterP3.

## MIG-29 review round1 · 2026-10-26 · uncommitted
Code review FAIL2 static counterexamples: empty-party-only channel reference loses compatible candidates; nested ICO/route/service gaps omitted from process reasons.
All51 original tests meaningful and frozen9/review20 hashes retained. Findings not yet assertion-red; three separate completeness regressions authorized before two-file correction.
Docs writer released four allowed paths,274 additions25 deletions. Observed whitespace/budget/privacy/links/headings/scope exit0; logs read.
Canonical budgets bpm1470/intake1459 of1500; privacy0;13addedlinks and37citations resolve;6headingsadded,0removed. Initial linkchecker error on rootREADME fixed in checker-r2; no document fault.
Next: test-author adds BehaviorCompletenessTest only, root approval/independent red, sameimplementer Channels/Discovery correction; final code/docs review and fullroot pending.
Scope unchanged: finishP3 andstop; noP4–P7/tenant/push or runtime proof.

## MIG-29 regression approval · 2026-10-26 · uncommitted
Architect read all159lines of BehaviorCompletenessTest; three real-source regressions approved, SHAEB9804B7DDFBF07838BF8EA804036497540FD781B3252D81E3D9ECE7F34D19F5.
Author EXECUTED_FAIL3/3/0/0,exit1; summary/failuresection/logtail read. Expected channels[2,5] wereempty; process omitted route and serviceambiguity reasons.
All preceding fixture/snapshot/index/state/nested-reason assertions passed. Protected24/24 hashes unchanged (11production,9originaltests,4docs); writer released.
Documentation review PASS: exact contract/source/tests/links checked, hashes4/4 and budgets1470/1459; final two-file sourceconsistency closure follows correction.
Next: independent assertion-red, sameimplementer onlyChannels/Discovery correction, code r2/finaldocs and fullroot/CLI. No completion claim yet.
Checks: independent EXECUTED_FAIL3/3/0/0,exit1,1.304s; verifier summary/excerpts/logtail read. All three expected assertion failures reproduced, noerrors/skips.
New approvedhash, original9/9 andprotected24/24 matchbefore/after. Sameimplementer now authorized for onlyChannels/Discovery correction; sourcecontract/tests remain frozen.

## MIG-29 correction handback · 2026-10-26 · uncommitted
Writer released: only BehaviorChannels/BehaviorDiscovery corrected. Empty-party selector retains candidates; nested interface/match/route/call/service reasons reach process summary deterministically.
Checks: author VERIFIED_PASS3/0/0/0 regressions and246/0/0/0 combined(core227/native9/CLI6/MCP4),exit0; summary/counts/logtails read.
Preservation:23/23 protected files unchanged (10test/helper,4docs,9otherproduction); originalbaseline retained, no expectation/state/index/snapshot/grammar changes.
Next: same code reviewer r2 and finaldocs sourceconsistency, then independent rootverify/fresh packagedCLI and selectivelocalcommit. P3 not yet accepted; D053stop remains binding.

## MIG-29 independent review closure · 2026-10-27 · uncommitted
Code reviewr2 PASS: both findings closed; three regressions meaningful;21/21 current snapshots and23/23 protectedfiles independently retained.
Final docs consistency PASS: four frozen docs match corrected empty-party and nested-reason semantics; no remaining blockers.
Independent fullroot mvn -B -ntp verify and fresh packagedCLI now in progress; no final acceptance claim before observed output.

## MIG-29 / P3 final acceptance · 2026-10-27 · ready to commit
Changed: stable BehaviorReport composes source paths/data/faults with configured ICO/channel/interface/service candidates; offline behavior CLI and bpm_behavior MCP, immutable snapshots and native registry.
Test first:51 original tests plus3 independently confirmed assertion-red regressions; all54new pass. Code reviewr2 and finaldocs PASS; two review defects corrected without weakening expectations.
Checks: independent root VERIFIED_PASS623/0/0/26,exit0,01:43; target/mig-p3c-behavior-verify-final-verifier.log and final summary/counts/logtail read.
Modules:core483/0/0/22,native9/0/0/0,CLI47/0/0/1,MCP12/0/0/1,KB72/0/0/2. Actual MCP inline/full-file spill passed.
CLI: fresh packaged8/8 logical cases passed, including expected missing-export failure; parsedJSON/Markdown semantics and four input hashes verified unchanged.
Preservation:21source/test+4docs snapshots,10approved hashes,224priorfiles and4governance files unchanged; parser/analysis/ScenarioDiscovery/POM/JSON helpers/pointers preserved.
Docs:Budget2/Cites1/SkillMirror13 pass; canonical budgets1470/1459 of1500; links/whitespace clean,0 sensitive added-line privacyhits (one publicSAPcitation excluded).
Limits:24 absent private-input and2 unsetkb.forbidden skips remain unverified; native executable/fullforbidden values NOT_RUN for documented environment reasons. No liveMCP reload/deployment or runtime-delivery claim.
Verifier harness correction: first malformed-ICO fixture was not created; exact single retry observed a real parse diagnostic. Accidental own file removed, original logs retained; no source/test changes or Maven rerun.
P3 accepted; original plan updated to STOP underD053. P4–P7/MIG21/MIG22 campaign, tenant operations and push were not performed.
Next: selective localcommit32 ownedpaths after independentprecommit audit; preserve unrelatedCODEX01/MIG07/D046/governance. No further implementation after this commit.

## P4 preparation · 2026-10-27 · uncommitted
Owner authorizes only P4, followed by verified local commits and stop; P5–P7 and remote/tenant work excluded.
Observed main HEAD c840a66961ada1446af156a0767e42e33fba559d; one worktree. Initial diff saved target/mig-p4-initial-uncommitted.patch.
Preserve unrelated CODEX-01/MIG-07/D-046 and governance changes; never stage whole shared ledgers.
Selected MIG-30 reader evidence, then MIG-31 catalog and MIG-32 skill integration. Model inherited; actual model unknown.
Checks: NOT_RUN — preparation only. Prior 623/0/0/26 remains historical P3 evidence.
Preflight: P4a supplies resource identity to P4b; P4b supplies catalog semantics to P4c; sequential writers avoid overlapping model/docs edits.
Ruling: P3 data remains structural candidates; P4 must not relabel them runtime availability. Legacy unknown metadata cannot establish QName.
Next: bounded MIG-30 packet, separate red tests, architect approval, independent verification and review.

## MIG-30 red test approval · 2026-10-27 · uncommitted
Independent design preflight PASS. Architect read all four test files and final foreign-schema delta;12 new tests approved, hashes in packet.
Test first: ExternalContractsTest8, ContractResourcesTest3, additive NativeReflectionTest1; synthetic TPZ/XML and legacy JSON only.
Author EXECUTED_FAIL compile-red exit1 for new types/accessors, no runtime tests; target/mig-p4a-red.log tail read.
Author native-only attempt ENVIRONMENT_BLOCKED by stale installed core; not counted as acceptance red.
Next: independent red confirmation, bounded implementation. Source/P3/governance untouched.
Checks: independent EXECUTED_FAIL compile-red exit1 before Surefire; target/mig-p4a-independent-red.log and summary/tail read. Frozen4/4 match; production unchanged.
Bounded MIG-30 implementation authorized; no completion claim.

## MIG-30 implementation and review · 2026-10-27 · uncommitted
Source: full ESR keys, compatible ExternalDefinition resource evidence, secure standard XML parsing, canonical OID lookup, native registration.
Author focused VERIFIED_PASS21/0/0/0; root VERIFIED_PASS635/0/0/26 exit0,01:57; target/mig-p4a-implement-verify-unsandboxed.log tail read.
Earlier sandbox root ENVIRONMENT_BLOCKED by14 existing localhost connection errors; same unchanged build passed with approved permission.
Independent code review PASS: all12 new tests sensitive, frozen4/4 unchanged, source identities and ancestor namespace bindings retained.
Native executable NOT_RUN; external-message QName remains unresolved without binding evidence.24 missing-private and2 unsetkb.forbidden skips remain unverified.
Next: independent root verification and selective MIG-30 local commit. MIG-31 packet revision2 addresses preflight identity/reference ambiguities.

## MIG-30 acceptance · 2026-10-27 · ready to commit
Independent VERIFIED_PASS root635/0/0/26 exit0,01:39; target/mig-p4a-independent-verify.log and summary/tail read.
All12 new tests pass; core494/0/0/22,native10/0/0/0,CLI47/0/0/1,MCP12/0/0/1,knowledge72/0/0/2.
Independent code review PASS; approved4/4 hashes retained; scope/whitespace/privacy checks pass.
Limits:24 absent private-input and2 unsetkb.forbidden skips; native executable/full forbidden-values NOT_RUN.
P4a accepted; next MIG-31 catalog, then MIG-32 docs and stop. No P5–P7, tenant, deployment or push.

## MIG-31 preparation · 2026-10-27 · uncommitted
MIG-30 committed8f834dc after independentprecommit PASS13paths/8verifiedblobs/4frozenhashes; initial governance/pilot remain unstaged.
MIG-31 revision2 design preflight PASS after explicit identity, reference, claim-retention and URI candidate rules.
Source artifact identity and XML declaration identity remain distinct; supplied choices are attributed claims, not approval or runtime proof.
Test-author authorized within packet; implementation NOT_RUN until approved independent red. P4 only.

## MIG-31 test approval · 2026-10-27 · uncommitted
Architect read all5 files and final fixture/WSDL/identity deltas;20 tests approved (19core+1native), hashes target/mig-p4b-test-hashes.log.
Author EXECUTED_FAIL compile-red exit1 for introduced catalog types; no runtime tests; target/mig-p4b-red.log tail read.
Prerequisite harness VERIFIED_PASS exit0: P3 one step/object,four declarations,two mappings; mapping fixture one library,two mappings with local functions/library refs.
Evidence: target/mig-p4b-fixture-prerequisites.log read. Earlier JShell registry denial ENVIRONMENT_BLOCKED; direct Java alternative passed.
Zero-based original indices confirmed; source fixtures avoid duplicate reader expansion, not catalog deduplication. No P3 semantics changes.
Next: independent red, then bounded implementation. Source untouched; no P4 completion claim.
Checks: independent EXECUTED_FAIL compile-red exit1 beforeSurefire; target/mig-p4b-independent-red.log/summary read.5approved hashes match, production unchanged against8f834dc.
Bounded MIG-31 implementation authorized. MIG-32 documentation packet prepared; no author edits yet.

## MIG-31 ID allocation clarification · 2026-10-27 · uncommitted
Early independent review found raw IDs can collide with generated duplicate/blank IDs. Packet clarified reserved-base/occurrence allocation; reviewer agreed.
Implementer paused and released writer before claims/ID helper implementation; existing partial source retained, no Maven result yet.
Separate test-author regression covers all3ID domains and original reference resolution; original5files frozen. No scope/P3 semantic change.
Architect read collision test entirely;4 tests approved, hash1FAB95C2DE020CB8D99023F50C5407931277340B0D6A6E3CEAEEDD7210478BF7. Original5hashes unchanged.
Author EXECUTED_FAIL compile-red exit1 on unfinished introduced helpers, target/mig-p4b-id-red.log tail read; no runtime assertion claim.
Native DT declaration ownership clarified as direct DT, not synthesized extra resource artifacts; independent red then resume same implementer.
Checks: independent EXECUTED_FAIL incrementalcompile-red exit1, target/mig-p4b-id-independent-red.log/summary read;6frozenhashes match. No runtime assertion-red claimed.
Same implementer resumed with24approved tests and collision/direct-DT clarification.

## MIG-31 implementation handback in progress · 2026-10-27 · uncommitted
Source frozen/writer released:6new catalog classes plus native registration. Root verify running; authorfocused VERIFIED_PASS23/0/0/0, logtailread.
All6 approved test hashes retained; sourcehash manifest target/mig-p4b-implement-hashes.log. Independent code review dispatched.
No parser/P3/JSON/POM changes; source vs supplied claims and structural timing retained. Fullroot/native acceptance pending.
Author root VERIFIED_PASS659/0/0/26 exit0,01:50; target/mig-p4b-implement-summary.log/root tail read.24new tests pass including native registration.
Independent root and code review now running; acceptance pending. Baseline26skips unchanged; native executable NOT_RUN.

## MIG-31 acceptance · 2026-10-27 · ready to commit
Independent code review PASS, all24 tests sensitive; independent root VERIFIED_PASS659/0/0/26 exit0,01:37; summary/root tail read.
Evidence target/mig-p4b-independent-verify.log; core517/0/0/22,native11/0/0/0,CLI47/0/0/1,MCP12/0/0/1,knowledge72/0/0/2.
All24new tests pass;6frozen hashes/7production snapshots match; no parser/P3/POM changes, whitespace/privacy/scope pass.
Catalog retains source identities/XML declarations, attributed artifact choices/owners/ports/default/API producers, unresolved imports and structural P3 before/after.
Public input/report helpers draft at hand-over; no CLI/MCP/import/deploy/runtime proof.24private+2unsetkb.forbidden skips and native executable remain unverified.
Next: selective local MIG-31 commit, then MIG-32 skill docs and final P4 closure. No P5–P7 or push.

## MIG-32 preparation · 2026-10-27 · uncommitted
MIG-31 committed1053910 after independentprecommit PASS18paths/13blobs/6frozenhashes. P4 code accepted; documentation remains.
MIG-32 packet preflight PASS. Baseline retrieval gaps observed: absent contract reference/template and no explicit placement/API-output/import workflow.
Author now permitted only canonical skill/output docs perpacket, with unchanged frontmatter/pointers/phasegates. No new KB claims or source edits.
Next: documentation author, independent checks/review, final P4 localcommit and STOP.

## MIG-32 author handback · 2026-10-27 · uncommitted
Writer released9allowed docs: new contract reference/template, canonical routing/intake/attributes/BPM/plan/scenario and OUTPUT-MODEL.
Author VERIFIED_PASS scoped whitespace and all8skill page budgets; logs target/mig-p4c-author-counts.log/whitespace read;9hash manifest saved.
Budgets reference1056/template584/intake1477/attributes1444/BPM1485 of1500. Frontmatter/pointers/phasegates/source/tests preserved.
Next: independent docs retrieval/API review, skill checks and final rootverify; completion not yet claimed.

## MIG-32 / P4 final acceptance · 2026-10-27 · ready to commit
Delivered: external source identities/XML resources, evidence-based contract catalog and private contract workflow/reference/template;36new synthetic tests across MIG-30/MIG-31.
Commits:8f834dc MIG-30,1053910 MIG-31; this documentation commit completes P4. P5–P7 were not started.
Independent documentation review PASS; routing replay covers catalog/template, placement, API/default timing, imports and approval boundaries.
Final root VERIFIED_PASS659/0/0/26 exit0,01:42; target/mig-p4c-independent-verify.log and summary/tail read.
Skill checks: BudgetTest2,CitesTest1,SkillMirrorTest13 pass;47Markdownlinks+127concrete citations resolve;4syntax placeholders excluded explicitly.
Docs:9hashes retained; reference1056/template584/intake1477/attributes1444/BPM1485 words; headings/frontmatter/whitespace/privacy pass.
Source/tests/POM unchanged against1053910. Prior code reviews PASS; P3 remains structural candidates, no runtime delivery/ack/transaction claims.
Limits:24absent private fixtures+2unsetkb.forbidden skips unverified; native executable/fullforbidden NOT_RUN. No import/deploy/tenant operations.
Original CODEX-01/MIG-07/D-046/governance preserved unstaged; selective final staging only. No push or unrelated campaign.
STOP after verified local documentation commit. No next implementation authorized in this session.

## P5–P7 continuation · 2026-10-27 · uncommitted
Owner: continue further until STOP; resumes approved plan after accepted P4. P5→P6→P7 authorized, no unrelated backlog campaign or remote/tenant operations.
Observed main HEAD7a09a15658b663371a4f63a7764b0eb1a0942970; prior three P4 commits present, original governance/pilot changes remain.
Initial diff saved target/mig-p5-initial-uncommitted.patch; shared ledger baselines target/mig-p5a-initial-*.md.
Selected MIG-33 comparison core then MIG-34 offline entrypoints/skill. Role/model inherited, actual model unknown; one writer/max2subagents.
Checks: NOT_RUN for P5; historical P4 root659/0/0/26 is not new comparison evidence.
Next: bounded P5 packet based on read-only scout, separate red tests, independent review/verify and local commits.

## MIG-33 preflight revision2 · 2026-10-27 · uncommitted
Independentpreflight FAIL identified6packet ambiguities before tests: per-facet ancestry, ambiguous occurrences, JSONbinding boundary, ICO resolution gate, versions and mixed completeness.
Revision2 fixes all6 without source/API scope expansion; ProcessWiring remains embedded candidate evidence, unsupported facets stay contextual.
No tests/implementation yet. Recheck required before author dispatch.
Recheck revision2 PASS; exact publicAPI/association rules accepted for tests. Separate test-author now authorized; source implementation remains NOT_RUN.

## MIG-33 test approval · 2026-10-27 · uncommitted
Architect read all5files and final fault-scope/same-name/renamed-only deltas;16tests approved (15core+1native), hashes in packet/targetmanifest.
Author EXECUTED_FAIL corecompile-red exit1 missing comparison types, target/mig-p5a-red.log tailread; no core runtime counts.
Author EXECUTED_FAIL native directprobe missing BehaviorComparisonInput registration, target/mig-p5a-native-probe.log assertionread.
Fixture prerequisite VERIFIED_PASS3variants x5steps plus renamed-only same arcs/loops/scopes, exit0; target/mig-p5a-fixture-probe.log read.
Next: independentred then boundedimplementation; no source changes yet. P3/P4 preserved.
Checks: independent EXECUTED_FAIL corecompile-red exit1 and fresh nativeprobe assertion-red exit1; target/mig-p5a-independent-red-summary.log read.5frozenhashes retained, source unchanged7a09a15.
Same bounded semantics authorized for implementation; MIG-34 offline delivery/docs draft prepared. No runtime comparison verification yet.

## MIG-33 implementation freeze · 2026-10-27 · uncommitted
context compacted; HANDOFF, git status and log3 reread before edits, HEAD7a09a15 preserved.
Author focused EXECUTED_FAIL15/3/0/0: call reason propagation and empty-list/null JSON roundtrip; no frozen test changes.
Comparison-local serializer approved within Input model; existing P3/P4 models and shared Json semantics unchanged.
Author final focused VERIFIED_PASS core15/0/0/0 plus native12/0/0/0, exit0 BUILD SUCCESS; target/mig-p5a-implement-final-focused.log tailread.
All5 approved test hashes retained;5source files frozen, target/mig-p5a-implement-source-hashes.log.
Independent reactor/review pending; native executable NOT_RUN. MIG-34 preflight PASS; no delivery code yet.

## MIG-33 verification authorization · 2026-10-27 · uncommitted
ENVIRONMENT_BLOCKED independent reactor before execution: automatic approval saw original P4-only scope, rejected later continuation context.
Observed target/mig-p5a-independent-verify-summary.log: Maven NOT_RUN, no exit/counts;5test+5source hashes match; no workaround attempted.
Owner explicitly reconfirmed P5–P7, local verification and local commits after the refusal; push/deploy/tenant operations remain excluded.
Verifier resuming through normal approval with this new direct authorization. Code review still in progress; no acceptance claim.

## MIG-33 review r1 · 2026-10-27 · uncommitted
Independent code review FAIL: malformed supplemental evidence wrongly invalidates otherwise qualified alignment;
source-only catalogs without step claims wrongly receive contract ancestry mismatch diagnostics.
Both corrections preserve existing packet meanings; additive regression acceptance recorded in MIG-33 packet.
Reviewer confirms existing16tests meaningful; new edge cases need separate observed-red tests before fixes.
Initial independent reactor running under newly approved owner authorization; its result will not accept unfixed review findings.
Initial independent reactor VERIFIED_PASS675/0/0/26 exit0,02:06; target/mig-p5a-independent-reauthorized-summary.log and reactor tail read;10hashes retained.
Architect read both added regression methods: candidate/confirmed mixed evidence and both source-only/unbound catalog cases approved.
Author regression EXECUTED_FAIL2/2/0/0 exit1, target/mig-p5a-r1-regression-red.log tail read; exact review defects observed.
Frozen regression hashes target/mig-p5a-r1-regression-hashes.txt; remaining3testfiles unchanged. Independent red before fixes.
Independent regression EXECUTED_FAIL2/2/0/0 exit1; target/mig-p5a-r1-independent-red-summary.log/tail read,5test+5source hashes unchanged. Bounded fixes authorized.

## MIG-33 correction freeze · 2026-10-27 · uncommitted
Only comparison alignment/facet helpers changed for both review findings;5approved regression hashes retained.
Author focused VERIFIED_PASS core17/0/0/0 plus native12/0/0/0 exit0 BUILD SUCCESS; target/mig-p5a-r1-implement-focused.log tail read.
Source frozen in target/mig-p5a-r1-implement-source-hashes.log; independent final reactor and reviewr2 pending.

## MIG-33 acceptance · 2026-10-27 · ready to commit
Delivered: explicit evidenced alignment, eight source facets, unmatched occurrences, implementation context, offline JSON/Markdown and native registration.
Independent code reviewr2 PASS; bothr1 defects fixed after separate approved independentlyred regressions; no source semantic expansion.
Final independent VERIFIED_PASS677/0/0/26 exit0 BUILD SUCCESS,01:51; target/mig-p5a-final-independent-summary.log and reactor tail read.
Modules:core534/0/0/22,native12/0/0/0,CLI47/0/0/1,MCP12/0/0/1,knowledge72/0/0/2;18newtests pass.
All5approved test+5production hashes stable; scope/whitespace/privacy pass. P3/P4/shared Json semantics preserved.
Limits:24missing-private+2unsetkb.forbidden skips unverified; native executable/fullforbidden NOT_RUN; no delivery/ack/equivalence proof.
Original CODEX-01/MIG-07/D-046/governance preserved; exact paths and selective ledgers only. No push/deploy/tenant operation.
Next MIG-34 offline entrypoints and workflow reference; P5 still incomplete. Owner explicitly permits P5–P7 local work until STOP.

## MIG-34 selection · 2026-10-27 · uncommitted
MIG-33 committed3817886 after independent precommit PASS,15exactpaths; original governance/pilot changes preserved unstaged.
MIG-34 packet preflight PASS; same core read/markdown and existing error/spill behavior, no duplicate semantics.
Shared ledger baselines target/mig-p5b-initial-*.md. Separate CLI/MCP test author now selected, source/docs not yet authorized.

## MIG-34 test approval · 2026-10-27 · uncommitted
Architect read all3files and approved8tests:4CLI+4MCP with actual helpers/launcher/spill, errors, semantics, values and input hashes.
Author fixture VERIFIED_PASS exit0, actual SCA/TPZ through P3/P4:1column60903chars/2columns122085, target/mig-p5b-red-fixture-probe.log read.
Author CLI EXECUTED_FAIL4/4/0/0 missing registration; MCP compile-red missing behavior_comparison(String), no runtime assertions yet.
Logs target/mig-p5b-red-cli.log and -mcp.log tails read; hashes in packet and target/mig-p5b-red-test-hashes.txt.
Independent red next; production/doc implementation still NOT_RUN.
Independent EXECUTED_FAIL CLI4/4/0/0 exit1, missingregistration; MCP missing-method compile-red exit1, no runtime counts.
Observed target/mig-p5b-independent-red-summary.log and MCPtail;3approvedhashes stable, production unchanged3817886. Bounded wrappers authorized.

## MIG-34 source freeze · 2026-10-27 · uncommitted
Only CompareBehaviorCommand, command registration and MCP behavior_comparison added; shared core/error/spill helpers unchanged.
Author focused VERIFIED_PASS CLI4/0/0/0 and MCP4/0/0/0 exit0; target/mig-p5b-implement-counts.log and both logtails read.
MCP log contains access-denied line despite fresh passing Surefire; independent verifier must assess, no hidden success inference.
All3approved test hashes retained; source frozen. Independent code review and bounded documentation follow; finalroot/smoke pending.

## MIG-34 documentation freeze · 2026-10-27 · uncommitted
Independent code review PASS: thin wrappers and8observabletests match packet; final verifier checks ToolArg.required default and flagged access log.
Author completed8allowed docs: new comparison reference plus routing/intake/BPM/plan/contracts/outputmodel/README; source/frontmatter/mirrors unchanged.
Author VERIFIED_PASS whitespace and pagewordcounts:comparison985,intake1489,BPM1490,plan1356,contracts1084; logs target/mig-p5b-docs-counts.log/whitespace.log read.
Docs frozen target/mig-p5b-docs-hashes.log; independent docs review, finalreactor, citations/links and packagedCLI smoke pending.

## MIG-34 / P5 acceptance · 2026-10-27 · ready to commit
Delivered: compare-behavior CLI, behavior_comparison MCP and comparison skill/reference/routing/outputmodel; P5 complete with MIG-33.
Independent code and documentation reviews PASS; exact8docs/API/routing/evidence limits checked.
Final root VERIFIED_PASS685/0/0/26 exit0 BUILD SUCCESS,02:03; target/mig-p5b-final-independent-summary.log and reactor tail read.
Modules core534/0/0/22,native12/0/0/0,CLI51/0/0/1,MCP16/0/0/1,knowledge72/0/0/2;8newtests pass.
Fresh packaged CLI VERIFIED_PASS6/6 help/JSON/Markdown/missing/malformed/semantic-success; actualcontents/exits and unchanged inputs checked.
Budget2/Cites1/Mirror13 pass;58links+77concretecitations resolve,4syntaxplaceholders excluded;3source+3test+8doc hashes stable.
ToolArg.required defaulttrue confirmed from resolvedJAR; author access-denied line did not recur. Whitespace/privacy/headings/frontmatter pass.
Limits:24missing-private+2unsetkb.forbidden skips unverified; native executable/fullforbidden NOT_RUN. No equivalence/approval/runtime proof.
Preserve original CODEX-01/MIG-07/D-046/governance unstaged; verified local commit only, no push/deploy/tenant operation.
Next P6 thenP7 under explicit owner confirmation until STOP; no unrelated backlog campaign.

## MIG-35 / P6 selection · 2026-10-27 · uncommitted
P5 saved3817886/657030c after independent precommit audits; original governance/pilot modifications remain unstaged.
Selected P6a dossier evidence, thenP6b export metadata andP6c workflow/docs; detailed MIG-35 draft sent for preflight before tests.
Ruling: explicit evidence-register/use and exact scope guard, no automatic evidence-answer workflow; existing question/decision/risk events remain authoritative.
Reason: current file validity cannot prove historical admission; declared uses/bases retain honest current applicability without invented history.
Packet covers scoped external-unavailability warnings, unchanged structural-error rules, explicit replacement and separated source/version kinds.
Checks: NOT_RUN for P6; earlier685/0/0/26 is P5 only. Baselines target/mig-p6a-initial-*.md; no P6 source/tests yet.

## MIG-35 preflight revision2 · 2026-10-27 · uncommitted
Independent preflight FAIL4: mutable milestone-approval admission, replacement-chain ancestry, network/link boundaries and external-reference severity.
Revision2 separates evidence-independent admission from final scoped guards; approvals remain attributed, not runtime proof.
Replacement chains have immutable linear ancestry and current supported-node selection; unavailable files cannot create structural errors.
UNC/device paths rejected before access; NOFOLLOW component checks, mounted-volume locality explicitly not authenticated.
External document references resolve at registration prefix as warning/unavailability; dossier event references retain structural errors.
Additional regression acceptance covers all4; no tests/source yet, independent recheck required.
Second preflight FAIL found stale acceptance wording conflicting with new chain semantics; architecture otherwise accepted.
Architect rewrote replacement acceptance contract as revision3: declared tails versus current supported node explicit; no third review of unchanged packet.
Rewritten revision3 independent preflight PASS; separate test-author authorized. Implementation remains NOT_RUN.

## MIG-35 test approval · 2026-10-27 · uncommitted
Architect read all6files:27newtests (24core/2CLI/1native) and narrow old-v2 JSON-field assertion approved; hashes in packet/targetmanifest.
Explicit amendment adds evidence property and empty packets/uses to existing DossierReadinessTest only; no weakened earlier assertions.
Author core EXECUTED_FAIL25/22/0/0 exit1, target/mig-p6a-red-core-final-approved.log tail read; v3 unsupported and missing evidence shape.
CLI EXECUTED_FAIL2/2/0/0, native1/1/0/0; separate logs target/mig-p6a-red-cli.log/-native.log; independent confirmation next.
Initial fixtures had invalid P2 prerequisite ordering, corrected before approval; v2 approved-plan/PoC/risk sanity passes before v3 assertions.
Junction initial ENVIRONMENT_BLOCKED; approved rerun setup exit0 directly observed in target/mig-p6a-red-link.log line33/finalcore line350.
Link rejection remains assertion-red, not verified boundary success. No production edits; independent red required before implementation.
Independent EXECUTED_FAIL core25/22/0/0,CLI2/2/0/0,native1/1/0/0 exit1 each; target/mig-p6a-independent-red-summary.log/tail read.
Independent junction setup exit0 precedes feature assertion failure;6approvedhashes stable, production unchanged657030c. Implementation authorized.

## MIG-35 partial implementation · 2026-10-27 · uncommitted
Author EXECUTED_FAIL focused Dossier*105/1/0/0 after standard escalation, target/mig-p6a-implement-core-escalated.log counts read.
Remaining test fixture declares same section/revision with different snapshot; P1 correctly rejects INVALID_SCOPE. No production relaxation allowed.
Test-author correction authorized only for external-hash scope variant and v2 prerequisite checks; prior source frozen7files,6testhashes unchanged.
Author boundary checks VERIFIED_PASS actual junction rejection and non-default filesystem under escalation; target/mig-p6a-implement-boundary-results.log read.
Initial restricted run105/2/1/0 included fixture filesystem restrictions, not accepted green. CLI/native/finalreactor pending.
Process deviation: Jackson Context7 lookup preceded code; Quarkus lookup followed native registry insertion. API verified afterward; order not misrepresented.
Corrected method diff read/approved: wrong hash now external-only, valid event dependency, actual review binding and all7v2 prerequisite checks.
Author VERIFIED_PASS correctedmethod1/0/0/0 exit0, target/mig-p6a-corrected-test.log tailread; newhashD44E43A2, other5tests unchanged.
Independent isolated baseline red and current method verification next; worktree source stays frozen.

## MIG-36 preflight preparation · 2026-10-27 · uncommitted
Metadata draft reviewed read-only while MIG-35 runs; no metadata tests/source authorized.
Preflight FAIL4: input/output alias protection, unsafe artifact classification, generation timestamp meaning, exact CLI diagnostics/exit contract.
Revision2 adds opt-in destination preflight/shared ScaService planning, no link following for alias checks, only-new outputs when linked artifact identity is unresolved.
Manifest timestamp is generation time; optional artifact gaps persist as issues with success, output/descriptor failures remain failures with visible partial paths.
Independent recheck required before future MIG-36 execution; MIG-35 remains current implementation task.

## Owner stopping boundary · 2026-10-27 · uncommitted
Owner reports credits running low and requests completing work in progress plus a continuation prompt.
Scope now finish current MIG-35 through independent verify/review and local commit, then stop; no MIG-36/MIG-37/P7 implementation.
MIG-36 revision2 preflight interrupted to avoid spending on future work; draft preserved, not accepted or implemented.
Independent correction check: currentmethod VERIFIED_PASS1/0/0/0; isolated657030c compile0/probe EXECUTED_FAIL1 unsupportedv3 after validv2 prerequisites.
Observed target/mig-p6a-correction-independent-summary.log; baseline stops firstvariant, not all7.6test+7source hashes unchanged.
Current method log access-denied line retained; final reactor must verify independently. Resume remaining CLI/native checks, then final review/reactor only.

## MIG-35 final implementation freeze · 2026-10-27 · uncommitted
Source7files unchanged from partialfreeze;6correctedtesthashes retained. No further implementation edits after fixture repair.
Author VERIFIED_PASS CLI2/0/0/0 and native13/0/0/0 exit0; target/mig-p6a-implement-final-counts.log read.
Both focused logs have leading access-denied line despite fresh passing reports; independent root owns final acceptance evidence.
Independent code review/reactor next; native executable NOT_RUN. Stop after verified MIG-35 local commit per latest owner boundary.

## MIG-35 code review r1 · 2026-10-27 · uncommitted
Independent review FAIL1: FAIL verdict for claimB incorrectly blocks separate PASSclaimA through packet-wide reasons.
Packet now distinguishes document integrity from per-claim support; mixed review preserves FAIL attribution, A remains usable.
Single-claim FAIL remains unavailable; new two-scope regression must cover both uses and unusedB before correction.
Reviewer found no second numeric-format defect; existing tests meaningful. Current reactor is pre-correction evidence, not acceptance.

## MIG-35 acceptance · 2026-10-27 · committed locally
Architect read/approved regression mixedReviewFailureBlocksOnlyItsUsedClaimAndScope; SHA256 8F040944 matches manifest.
Independent EXECUTED_FAIL 1/1/0/0 (class 14/1/0/0) exit1, target/mig-p6a-r1-independent-red.log tail read; 6 test hashes stable.
Fix limited to DossierEvidence.register: per-claim FAIL is no longer a packet integrity reason; all-FAIL review keeps one summary reason.
Implementer focused Dossier* 65/0/0/0 exit0. No library API added; Context7 not needed. Test files unchanged by implementer.
Code review r2 PASS; non-blocking: review-scope branch of use check untested, all-FAIL summary hidden by integrity reason, use reason generic, unavailable() rebuilds report.
Independent `mvn -B -ntp clean verify` VERIFIED_PASS exit0 713/0/0/26 (core559/22,native13/0,cli53/1,mcp16/1,kb72/2); log tail read.
Skips: 24 missing testdata + 2 unset kb.forbidden; not verified coverage. No Dossier*/Native skips. Native executable NOT_RUN.
Deviation: the requested clean deleted earlier target/mig-p6a-* logs, baselines and mig-p5 patch; earlier PROGRESS results stand as recorded.
Staging selective: MIG-35 source/tests/packet, P6 PROGRESS hunk, MIG-35..37 BACKLOG rows, plan, HANDOFF. CODEX-01/MIG-07/D-046/governance left unstaged.
MIG-36 draft packet uncommitted, revision2 preflight unfinished; MIG-37 and P7 not started. No push.

## MIG-36 acceptance · 2026-10-27 · committed locally
Owner asked to continue MIG-36. Preflight r2 FAIL4 (preflight owner, collision scope, link/"exists", Issue codes); second FAIL → architect revision3 R1–R8; r3 PASS.
Architect read/approved 15 red tests (core9/CLI5/native1); author + independent EXECUTED_FAIL compile red on new types; target/mig-p6b-independent-red-*.log.
Deviation: test-author validated assertions with a throwaway scratch implementation and briefly overwrote ~/.m2 core SNAPSHOT jars (reinstalled from HEAD copy).
Implementation: model ExportProvenanceInput/ExportManifest, migration ExportMetadata(+Descriptor/Paths), Reports.timestamp, ScaService.documentationPaths, ExportCommand --provenance-input, native registry.
Context7 not exposed to implementer subagent (ENVIRONMENT_BLOCKED); javap fallback on ~/.m2 jars before code. Stricter path choices recorded in packet.
Code review r1 FAIL1: preflight checked normalized paths, export used raw ones (POSIX `..`-after-link). Regression optInUsesOneNormalizedPathSet… independent red 6/1/0/0.
Fix ExportCommand.withProvenance only; review r2 PASS. Non-blocking open: root-path NPE in create, colon-in-name rejection, trailing-dot aliasing, untested BLOCKED/FAILED branches.
Independent `mvn -B -ntp verify` (no clean) VERIFIED_PASS exit0 729/0/0/26 (core568/22,native14/0,cli59/1,mcp16/1,kb72/2); target/mig-p6b-final-reactor.log tail read.
Skips: 24 missing testdata + 2 unset kb.forbidden, grouped from XML; not verified coverage. Packaged CLI smoke VERIFIED_PASS 5/5, inputs unchanged; target/mig-p6b-final-smoke.log.
Native executable and full forbidden-values NOT_RUN. Agent-memory edits (implementer/test-author) left unstaged: not in packet allowed paths. MIG-37/P7 not started; no push.

## MIG-37 acceptance · 2026-10-27 · committed locally
Context compacted during the task; re-read HANDOFF and re-verified with git status / git log -3 before ledgers.
Owner asked to continue MIG-37 (P6c docs). Packet preflight r1 FAIL7, r2 FAIL3 → architect revision3; r3 FAIL1 wording fix, then PASS.
Author (general-purpose, single writer): references/evidence.md, templates/experiment.md, profile, retrospective, SKILL, questions, plan, dossier,
OUTPUT-MODEL migration helpers, spec P-3/§7 item 4/§10. Architect: D-056 (private-only `[evidence: <use-id>]` cite, draft) and spec changelog line.
Docs review r1 FAIL2 (PoC return path lacked new revision + approval), r2 FAIL1 (packet "none" clause wrong → packet revision4), r3 PASS.
Non-blocking open: experiment.md "none" case should name the affected revision's Dependency. Packet error code corrected to INVALID_DOCUMENT for shape.
Frozen docs (hashes target/mig-p6c-docs-hashes.log unchanged): knowledge module VERIFIED_PASS 72/0/0/2 (2 = kb.forbidden unset); links 0 unresolved.
Root `mvn -B -ntp verify` VERIFIED_PASS 729/0/0/26 (target/mig-p6c-final-reactor.log) ran during the last docs edit round; docs-only change.
Skips: 24 missing testdata + 2 unset kb.forbidden; not verified coverage. Native executable and full forbidden-values NOT_RUN. No code changed.
Gates unchanged: skill frontmatter, Phases list, seven profile H2s, §10 first sentence (target/mig-p6c-final-gates.diff). P6 complete; P7 not authorized. No push.

## CODEX-01 commit · 2026-10-27 · committed locally
Owner asked to commit the local workflow override as it stands, then rename it to an agent-neutral name (MIG-39).
Committed unchanged: docs/CODEX-WORKFLOW.md, entry links in AGENTS.md/CLAUDE.md/docs/WORKFLOW.md, D-046, CODEX-01 row, its intake entry.
The original review log target/codex-01-review.log was deleted by the MIG-35 clean; independent precommit audit re-run: AUDIT_PASS (paths, privacy grep, links, whitespace).
MIG-07 row and MIG-07-only PROGRESS entries stay uncommitted (pilot in progress). No Maven run: documentation only. No push.

## MIG-39 acceptance · 2026-10-27 · committed locally
Owner: the override must not be named after one product; it should work with any coding agent (Codex, Claude Code, Gemini, Copilot).
Author (general-purpose, single writer): git mv docs/CODEX-WORKFLOW.md → docs/LOCAL-WORKFLOW.md, agent-neutral wording, "owner-led"
throughout; entry notes in AGENTS.md/CLAUDE.md/docs/WORKFLOW.md; path references in plan packets and the retrospective spec.
Architect: D-057 (scope widens to local, owner-led sessions of any agent; no rule text added/removed/changed), BACKLOG, plan status.
Docs review r1 FAIL1 (path swap falsified three dated observations → old name kept with pointer), r2 PASS. Links resolve; ≤100 cols.
PROGRESS/DECISIONS/CODEX-01 row keep the old name as history. Maven NOT_RUN: governance docs only, no skill/KB page changed. No push.

## MIG-38 acceptance · 2026-10-27 · committed locally
Task: P7a — private audit view of pilot M-1 under local/<customer>/ (gitignored) and replay of the plan §P7 cases. Deviation: F-15, F-16.
Packet preflight r1 FAIL13, r2 FAIL5 → architect revision3; r3 FAIL5 fixed in place; r3b PASS. Test first: none — pilot, no code changed.
Writer W imported M-1 into a v3 dossier: 169 records, 108 mapped, 61 unresolved; approvals 18 explicit, 37 unresolved, 4 wordings not imported.
Reviewer R (≠ W, ≠ evidence reviewer E): import r1 FAIL4 (superseded approvals shown current, partial deferral) → r2 PASS; replay table r1 FAIL2 → r2 PASS.
Replay (runner jar after 66769ae, verifier logs): case 0 valid, 60/60 snapshot digests; 1 deferred question blocks only M-b (QUESTION_OPEN);
2 superseded + partially-approved, SCOPE_NOT_APPROVED only; 6a evidence use supported, cache/idempotence question stays QUESTION_OPEN, no deployed version;
7 originals unchanged (462 hashes + repo status identical); 8 architect question append, history verified. All PASS.
NOT_RUN: 3, 4, 5, 6b — no SCA/inventory in M-1, no catalog CLI; covering synthetic tests are not pilot evidence. Cases 1, 2, 6a use synthetic dossiers.
Limits: no runtime/tenant data, no request executed, no overhead baseline (execution ~40 min, owner questions 0; no speedup claim).
F-15: v3 cannot hold partial supersession/deferral and question sub-states; F-16: reviewer agent cannot hash. Maven NOT_RUN: no code. P7 complete. No push.

## MIG-40 acceptance · 2026-10-27 · committed locally
Task: F-15 follow-up — legacy import rules in the migration skill; owner decision D-058 (dossier v3 unchanged, no new event/state/field).
Context compacted earlier in this session; state re-read from HANDOFF, git status and git log -3 before the packet.
Packet preflight r1 FAIL8, r2 FAIL4 → architect revision3; r3 PASS, five notes absorbed. Test first: none — docs only (skill text).
Writer (general-purpose, single): new references/legacy-import.md (795 words; conservative pass, narrow excerpts, split questions,
owner confirmation, outside the dossier); dossier.md line 11 → pointer with every clause moved (1169 words); SKILL.md routing row (1370).
VERIFIED_PASS knowledge 72/0/0/2 (target/mig-40/knowledge.log, exit 0); VERIFIED_PASS reactor 729/0/0/26 (target/mig-40/verify.log, exit 0);
verify.log ends with an "UncheckedIOException: Cannot write …provenance.json" line at exit 0 — origin not traced. Status vs baseline: allowed paths only.
Review r1 PASS (target/mig-40/review-r1.md). Note for MIG-41: two explicit approvals of a decision and its successor conflict until supersede.
Limits: the 26 skips are not verified coverage; native executable not verified; rules not yet exercised on a pilot (MIG-41). No push.

## MON-01 acceptance · 2026-11-11 · committed locally
Task: owner-approved po-xray message monitoring (D-059 draft); spec 2026-11-11-message-monitoring-design.md, plan and MON-01 packet written by the architect.
Owner decisions 2026-11-11: 21 read operations incl. user-defined search, no write operations, PROD normal read-only, save only on request, agent live calls on QA only.
Writer (general-purpose): anonymous WSDL GETs QA + PROD (6× HTTP 200, no credentials, no operation call); private copies under local/<customer>/monitoring-probe/.
F-17 (major): §4 data-type namespaces and fault child order corrected; AdapterFilter order confirmed; 8 required filter fields → architect's call: always sent, defaults false/0, checked in MON-07.
Deviation: F-17. Review r1 FAIL5 (spec/plan inconsistencies, PROD WSDL read vs "no PROD calls") → fixed; r2 PASS (target/mon-01/review-r2.md).
Clarification for hand-over: anonymous WSDL reads are not operation calls (spec §9, D-059). Maven NOT_RUN: docs only. Privacy grep clean. No push.
Next: MON-02 (SOAP transport and getMessageList) — test-author first.

## MON-02 acceptance · 2026-11-11 · committed locally
Task: SOAP transport, error mapping, PoHttp extraction and getMessageList (packet 2026-11-11-mon-02-packet.md).
Test first: MonitoringClientSearchTest (13), PoHttpTest (6) by the test-author; red observed — compilation failure (target/mon-02/red.log).
Implementer: PoHttp (client, authorization + moved describe/charset/isLogonPage/errorText), io.poxray.monitoring (client, Soap, MessageFilter 43 fields, records); JDK only.
Deviation (accepted by review): Soap.parse(response, operation, system); PoHttp exposes the moved helpers publicly; logon-page check after the SOAP-envelope check.
Review r1 FAIL: filter order tested for 14 of 43 fields → test-author added everyFilterFieldIsSentInOrderWithItsValue (passes unchanged code); r2 PASS.
VERIFIED_PASS reactor 749/0/0/26 (target/mon-02/verifier-verify-r2.log, exit 0); write-operation grep in src/main 0. No live call. No push.
Carry to MON-03: SearchResult honesty on unparseable values; repeated/nil children; tests for SOAP 200 with j_username, missing Response, non-200 without fault, InvalidKeyException, XML-declared encoding, control characters in escape.

## MON-03 acceptance · 2026-11-11 · committed locally
Task: by-id lookups, audit log, payload with MIME parts, ID→key resolution, plus the MON-02 review carry-over (packet 2026-11-11-mon-03-packet.md).
Test first: MonitoringClientDetailTest, MimeTest, KeyResolverTest, SoapEdgeCasesTest, 3 MonitoringClientSearchTest methods; red observed — test-compile failure (target/mon-03/red.log).
Architect calls: null required-nillable params → xsi:nil; PayloadPart index from 0, contentId without <>; text for text/*, */xml, */json, +xml, +json when strictly decodable, else base64.
Implementer (no deviation): 8 read methods, Mime, KeyResolver, SearchResult/LogEntry otherFields, repeated/nil children kept, XML-declared encoding, XML 1.0 char check. Beyond packet: Locale always sends 3 children; +json as text.
Tests added after green (red not observed, read by review): 9 for implementer-reported gaps; review r1 FAIL (xml/json → text untested) → 7 MimeTest methods; r2 PASS.
VERIFIED_PASS reactor 802/0/0/26 (target/mon-03/verifier-verify-r2.log, exit 0); write-operation grep 0. No live call. No push.
Carry: KeyResolver drops a message without key silently; nil/missing warning → false not kept; LogResult drops non-AuditLogEntryData items; Locale empty children to check in MON-07.

## MON-04 acceptance · 2026-11-11 · committed locally
Task: user-defined search, 11 lookups, read-only surface guard, MON-03 carry-over (packet 2026-11-11-mon-04-packet.md).
Test first: MonitoringClientLookupTest, ReadOnlySurfaceTest, new KeyResolver/Search/Detail methods; red observed — test-compile failure (target/mon-04/red.log).
Implementer: Lookup<T> with otherFields, AttributeMetadata, IntegrationFlow, StatusDetail, 12 methods; KeyResolver NotFound(idOrKey, reason); nil/missing flags and non-entry log items kept.
Deviation: nil message and log items are now kept as "(nil)" instead of skipped (changes MON-03 behaviour; honesty rule; pinned by tests). Two KeyResolverTest checks adapted to the 2-field NotFound (packet D1).
11 tests added after green to pin implementer behaviour (red not observed; read by review). Review r1 PASS with gap: call/envelope accepted any operation name.
Architect closed it: red test callRefusesAnOperationOutsideTheReadSet observed (target/mon-04/red-r2.log) → Soap.READ_OPERATIONS allow-list of 21; r2 PASS.
VERIFIED_PASS reactor 840/0/0/26 (target/mon-04/verify-r2.log, exit 0); write-operation grep 0. No live call. No push.
Carry to MON-05/06: an Ambiguous candidate may have no key — show it, never offer it as a key; Locale 3 empty children to check in MON-07.

## MON-05 acceptance · 2026-11-11 · committed locally
Task: CLI `messages` group (11 subcommands), default system, save target and archive (packet 2026-11-11-mon-05-packet.md).
Test first: MonitoringSystemsTest, SaveTargetTest, MessageArchiveTest, TimeArgTest, MessagesCommandTest; red observed — core test-compile (target/mon-05/red.log), CLI red in a stub run.
Architect calls: env only through MonitoringEnvironment bean (@Mock in tests, no assumeTrue); archive dir name sanitised, dots-only refused; --key takes a value without lookup.
Deviation: --save / saveDir only on log, payload, uds-attributes (a search is not one message); spec §7 and plan MON-05/06 edited with changelog.
26 tests added after green to pin implementer behaviour (incl. repeated --attr/--status). Review r1 FAIL: PoSystem.load(name) let the machine's POXRAY_*_PASSWORD reach the test fake; filter options not checked per field.
Fix: PoSystem.load(name, dir, env) public, CLI loads with the bean env; red observed (target/mon-05/red-r2.log, red-r2-cli.log); per-field filter test; r2 PASS.
VERIFIED_PASS reactor 919/0/0/26 (target/mon-05/verify-r2.log, exit 0); CLI smoke lists 11 subcommands. Javap on aesh-3.18.2 (Context7 unavailable to agents). No live call. No push.
Limits: no symlink test for SaveTarget/MessageArchive (Windows, no skips). Carry to MON-06: MCP must load systems through an env seam, not PoSystem.load(name).

## MON-06 acceptance · 2026-11-11 · committed locally
Task: MCP `message_*` tools (11), size budget, native registration (packet 2026-11-11-mon-06-packet.md).
Test first: MessageToolsTest, McpBudgetTest, NativeReflectionTest.allPublicMonitoringRecordsAreRegistered; red observed (target/mon-06/red.log, red-mcp.log).
Architect calls: MessageTimes copies the CLI TimeArg forms (MCP does not depend on CLI); a payload page at a surrogate pair takes the whole pair (maxChars+1) so paging always advances — red observed (target/mon-06/r3-red.log).
Deviation: env seam is a reflection-injected TestMonitoringEnvironment subclass, not an @Mock (the module has no Quarkus test runtime); 9 result records registered in the existing McpReflection.
23 tests added after green to pin implementer behaviour (red not observed; read by review). Review r1 FAIL: payload cap above 80,000, saveDir refusal on uds_attributes, MessageTimes untested.
Fix: tests only (payload shrink, uds_attributes refusal, MessageTimesTest, McpReflectionTest); no code defect found; r2 PASS.
VERIFIED_PASS reactor 983/0/0/26 (target/mon-06/verify-r2.log, exit 0). Javap decided the MCP API (Context7 unavailable to agents). Native build NOT_RUN (native profile). No live call. No push.
Carry to MON-07: smoke the MCP server — a JSON array binds to List<String> (statuses, attributes, ids, codes).

## MON-07 acceptance · 2026-11-11 · committed locally
Task: live read-only acceptance on QA with the built CLI and MCP (packet 2026-11-11-mon-07-packet.md); deviation: F-18.
Verifier ran checks 1–7 on QA only; architect added uds-search variants, lookup interfaces --json, an invented status and 40 payload reads (target/mon-07/run.log c8–c10).
Settled: message statuses server-validated (unknown → SOAP fault); wasEdited=false does not narrow; --from alone accepted; key 6 parts usable as given; Locale accepted; logLocations AM/MS/BI; MCP JSON array binds and applies.
Open (owner hand-over): payload version meaning, dateType, timesFailed/retries/archive/nodeId/retryInterval defaults, search without a window.
Contradiction: default payload part by position (parts>1 → 1) assumes an XI header part; QA returned none in 40 messages → row MON-08 (red test first).
No SOAP fault or authorisation error on the intended calls. Docs review r1 FAIL (status values vs status details, open points dropped, XI header, getInterfaces nil unsupported); fixed with probes; r2 PASS.
Evidence private under local/<customer>/monitoring-acceptance/; tracked files neutral. No PROD/DEV call by an agent; Maven NOT_RUN (docs only). No push.
