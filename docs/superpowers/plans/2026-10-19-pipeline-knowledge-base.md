# Pipeline Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the SAP Integration Suite pipeline knowledge base under `knowledge/pipeline/`, exposed as the Claude Code skill `sap-is-pipeline`, derived from the SAP pipeline packages and enriched from prior documentation.

**Architecture:** Two pieces of Java make the work mechanical and checkable. An iFlow reader in `po-xray-core` (package `io.poxray.cpi`, the seed of part 3) turns an iFlow (zip, package export or unpacked folder) into a step list, exposed as the CLI commands `iflow` and `unpack`. A test-only Maven module `iflow-forge-knowledge` verifies the knowledge base against the sources on every `mvn verify` (coverage, headings, placeholders, names, budget, leaked tenant values). Content is written in three passes: A (artifacts → pages), B (document claims → enrichment, pitfalls, changelog), C (procedures, decision tables, benchmark).

**Tech Stack:** Java 25, Maven multi-module (existing `po-xray-core`, `po-xray-cli` with Quarkus + Aesh, new `iflow-forge-knowledge`), JUnit 5 + AssertJ, Jackson (`io.poxray.json.Json`); Markdown; Mermaid; Claude Code skills. No Python anywhere in the repository.

**Spec:** `docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md`

**Changelog:** 2026-10-19 · F-1 · Task 2 `Participant` widened with `endpoint` and `properties` (the message-flow name alone does not identify the adapter; the dump is the reading surface for the pages). 2026-10-19 · D-016 · Task 2 step order covers local processes (inlined under the step whose `processId` names them) and event subprocesses (no incoming flow, `reached = true`). 2026-10-19 · D-015 · `parameters.propdef` carries no defaults; `Param.defaultValue` is the `<value>` child when present, else empty. 2026-10-19 · F-2 · Task 3 module pom defaults `kb.skip` to `coverage` until Task 6 clears it (the real-KB coverage check is red until the pages exist; `mvn verify` and CI must stay green); a relative `kb.forbidden` resolves against the repository root; Task 4 step 4 and Task 5 step 4 run coverage with `-Dkb.skip=`.

## Global Constraints

- Language of everything under `knowledge/`: English.
- No customer data under `knowledge/` or anywhere committed: no tenant names, personnel IDs, configured parameter values. `testdata/` is gitignored and is the only place for tenant exports, PDFs, the two tenant-specific pages and local scratch.
- Only scrubbed sources go under `knowledge/sources/<label>/` (spec §12.6, §13.2). The repository is private.
- Current pipeline version is primary; older version differences are inline "(vN: was X)" notes plus `versions/<label>/changelog.md`, built from the artifact diff and SAP's `CHANGELOG.txt` files (spec §13.1).
- Every behavioural fact cites an artifact: `[<symbolic-name>, step "<name>"]` or `[scripts/<file>#L<n>]`. No citation, no fact; it becomes an open question in `pitfalls.md`.
- Tenant-specific values are placeholders `{{tenant.<path>}}`; the complete list lives in `naming.md`.
- Loaded set (`SKILL.md` + `overview.md` + `contracts.md` + `scenario-contribution.md`) ≤ 5,000 words; any single page ≤ 1,500 words (spec §6, §12.8).
- Checklists are decision tables keyed on scenario attributes (quality of service, sender type, mapping kind, receiver count), not one linear path (spec §12.8).
- All tooling is Java inside the Maven modules with JUnit tests, in the style of the existing code (records, static readers, `io.poxray.json.Json` for JSON). Build: `mvn -q verify`; a single module: `mvn -q -pl <module> -am test`.
- Commit after every task, direct to `main` (no branches, no PRs; `docs/WORKFLOW.md` §3): Conventional Commits with the message the task gives as the subject, trailer `Task: KB-nn`; commit only the files the task names (never `git add -A`; `testdata/` must stay untracked).
- **Where tasks run:** Task 0 ran locally (it touched confidential folders) and is done. Tasks 1 to 13 run in Claude Code cloud sessions from the private GitHub repository `subapavel/iflow-forge`, so everything they need is committed: sources under `knowledge/sources/`, English inputs under `knowledge/inputs/en/`. Task 9 (tenant diff) is the one exception and runs locally. Cloud environment: JDK 25 and Maven 3.9 (setup script), network for Maven Central.
- Version labels are export dates, not package versions (each package carries its own): `2026-06-trial` (older, SAP trial tenant export), `2026-09-current` (current, scrubbed tenant export, to be replaced by the clean catalogue download `2026-10-catalog` when Pavol provides it). Spec §13.

## Review Focus

1. **Package exports that nest artifact zips** (a package export with `resources.cnt` plus one `<id>_content` per artifact) versus a single iFlow zip versus an already unpacked folder: `IFlowReader.readAll` must yield the same `IFlow` values for all three. Test in Task 2.
2. **BPMN with cycles or exception subprocesses**: ordering steps by sequence flows must terminate and must list steps inside subprocesses under their parent with depth 1. Test in Task 2.
3. **Configured tenant values leaking into pages**: a value from the tenant export's `parameters.prop` must never appear in `knowledge/`. `ForbiddenValuesTest` in Task 3, run locally with the forbidden list in every content task.
4. **Placeholder drift**: `{{tenant.jms.inbound}}` in one page and `{{tenant.jms.queue.inbound}}` in another. `PlaceholdersTest` in Task 3.
5. **Pages naming scripts or iFlows that do not exist in the sources** (typo or renamed in the new version). `NamesTest` in Task 3.

---

### Task 0 (local, done 2026-10-19): data collection, scrubbing, staging, push

**Files:**
- Created (untracked): `testdata/pipeline/README.md`, `testdata/pipeline/trial-2026-06/`, `testdata/pipeline/czint-2026-09/`, `testdata/pipeline/confluence-2026-10/{pdf,txt}/`, `testdata/pipeline/forbidden-values.txt`, local throwaway staging scripts
- Created (committed): `knowledge/sources/2026-06-trial/<package>/<symbolic-name>/...`, `knowledge/sources/2026-09-current/...`, `knowledge/sources/README.md`, `knowledge/inputs/en/README.md`, `knowledge/inputs/en/confluence-2026-10/*.md` (39 pages), `knowledge/inputs/en/tenant-export-review-2026-09.md`
- Modified: `.gitignore`, `.gitattributes` (`knowledge/sources/** -text`)

**Interfaces:**
- Produces: the committed source tree that Task 2's `PackageUnpacker` must reproduce byte-for-byte from the package exports (its local acceptance test), and the English input pages Tasks 4, 5, 10 read.

- [x] **Step 1: GitHub.** Private repository `subapavel/iflow-forge`, `main` pushed; cloud sessions start from it.
- [x] **Step 2: Collect into `testdata/pipeline/`** (trial export, tenant export, 41 Confluence PDFs converted to position-sorted text).
- [x] **Step 3: Forbidden-values list** (`testdata/pipeline/forbidden-values.txt`: tenant tokens, personnel IDs, tenant hostnames).
- [x] **Step 4: Stage the sources** into `knowledge/sources/2026-06-trial` and `2026-09-current`: `resources.cnt` decoded, each artifact unpacked under its `Bundle-SymbolicName`, `parameters.prop` and `Configured_Custom_Tags.txt` skipped, tenant suffixes stripped from manifests and `.project`, `CHANGELOG.txt` and `INDEX.md` per package.
- [x] **Step 5: Translate the inputs to English** (Pavol's decision: no Czech in the repository). 39 Confluence pages and the tenant-export review, technical names verbatim, code blocks untranslated, tenant identifiers replaced; the two tenant-specific pages stay in `testdata/` (part 2 input).
- [x] **Step 6: Commit and push.**
- [ ] **Step 7: Current version (Pavol, when convenient).** Clean catalogue download of the four packages (Discover → Integrations) → `testdata/pipeline/catalog-2026-10/`; then stage with `po-xray unpack` (Task 2) into `knowledge/sources/2026-10-catalog/`, diff against `2026-09-current` (expected: empty), commit, and switch the `kb.sources` default (Task 3) to the new label.
- [ ] **Step 8: Benchmark questions (Pavol).** ~20 questions with expected answers (message path, generic iFlow roles, which headers decide routing, what a scenario contributes for async XI → IDoc, what changes for sync, where the mapping goes with and without XSLT). Commit as `knowledge/pipeline/benchmark/questions.md` (no customer data in it).

---

### Task 1: Knowledge base skeleton and skill wrapper

**Files:**
- Create: `knowledge/pipeline/SKILL.md`, `overview.md`, `contracts.md`, `partner-directory.md`, `scenario-contribution.md`, `naming.md`, `pitfalls.md`, `glossary.md`, `iflows/.gitkeep`, `templates/.gitkeep`, `scripts/.gitkeep`, `migration/.gitkeep`, `conversion/README.md`, `versions/README.md`, `benchmark/.gitkeep`
- Create: `.claude/skills/sap-is-pipeline/SKILL.md`
- Modify: `README.md` (one paragraph on iflow-forge and the knowledge base, links to the spec and plan)

**Interfaces:**
- Produces: the folder layout of spec §6 that every later task writes into; skill name `sap-is-pipeline`.

- [ ] **Step 1: Create the folder tree and stub pages.** Each stub page: H1 title and one line `_Stub, filled in Task N._` with the task number from this plan. `SKILL.md` frontmatter: `name: sap-is-pipeline`, `description: SAP Integration Suite pipeline concept: generic iFlows, contracts, what a scenario contributes, migration procedures from PO ICO or point-to-point iFlow. Load when designing or generating pipeline-based iFlows.` Body: table "when you need X, read Y" over the pages.
- [ ] **Step 2: Skill wrapper.** `.claude/skills/sap-is-pipeline/SKILL.md` has the same frontmatter and the body: "The content lives in `knowledge/pipeline/`. Read `knowledge/pipeline/SKILL.md` first." (Pointer, not symlink; Windows-safe; one copy of content.)
- [ ] **Step 3: README.** Add the paragraph and links to `README.md`.
- [ ] **Step 4: Verify:** `ls knowledge/pipeline` shows every file above; `git status --short | grep testdata` is empty.
- [ ] **Step 5: Commit.** `git add knowledge/pipeline .claude/skills/sap-is-pipeline README.md` then commit `Knowledge base skeleton and sap-is-pipeline skill wrapper`.

---

### Task 2: iFlow reader in `po-xray-core` and CLI commands `iflow`, `unpack`

**Files:**
- Create: `po-xray-core/src/main/java/io/poxray/cpi/IFlow.java`, `IFlowReader.java`, `PackageExport.java`, `PackageUnpacker.java`, `IFlowDump.java`
- Create: `po-xray-cli/src/main/java/io/poxray/cli/IflowCommand.java`, `UnpackCommand.java`
- Modify: `po-xray-cli/src/main/java/io/poxray/cli/PoXrayCommand.java:15-16` (add both to `groupCommands`), `po-xray-core/pom.xml` (surefire system property `iflowforge.sources` = `${maven.multiModuleProjectDirectory}/knowledge/sources`), `po-xray-native` (reflection registration of the new records, same pattern as the existing output model), `README.md` (two CLI rows)
- Test: `po-xray-core/src/test/java/io/poxray/cpi/IFlowFixture.java`, `IFlowReaderTest.java`, `PackageUnpackerTest.java`, `IFlowDumpTest.java`, `SourcesAcceptanceTest.java`

**Interfaces:**
- Produces (package `io.poxray.cpi`, all records immutable, lists in document order):
  - `record IFlow(String id, String name, String version, List<Participant> participants, List<Step> steps, List<Param> parameters, List<String> resources)` — `id` = `Bundle-SymbolicName` (first `;`-separated token), `name` = `Bundle-Name`, `version` = `Bundle-Version`, all from `META-INF/MANIFEST.MF` with continuation lines unfolded.
  - `record Participant(String name, String componentType, String direction, String endpoint, Map<String, String> properties)` — from each `bpmn2:messageFlow`: `ifl:property` `Name`, `ComponentType` (`JMS`, `ProcessDirect`, `XI`, `IDOC`, `SOAP`, …) and `direction` (`Sender` / `Receiver`); `endpoint` = the `name` of the `bpmn2:participant` the flow connects to (`sourceRef` for a Sender, `targetRef` for a Receiver, `""` when unresolved); `properties` = every `ifl:property` pair of the flow in document order (F-1).
  - `record Step(String id, String name, String activityType, int depth, Map<String, String> properties, boolean reached)` — `activityType` from `ifl:property` `activityType` (`Script`, `Enricher`, `XSLTMapping`, `ProcessCallElement`, `ExclusiveGateway`, `ExternalCall`, …); `depth` 0 in the main process, +1 inside each `subProcess`; `reached` false for steps no sequence flow leads to. Order per D-016: the main process is the one containing the Sender flow's target; any step whose `processId` names a process inlines it once at depth + 1; event subprocesses (no incoming flow) follow the flow steps of their process with `reached = true`; orphans last.
  - `record Param(String name, String defaultValue, boolean configured)` — from `parameters.propdef` (default) and, if present, `parameters.prop` (`configured` = true; the configured value is **never** stored).
  - `final class IFlowReader { static IFlow read(Path iflowZipOrDir); static List<IFlow> readAll(Path path); }` — `readAll` accepts an iFlow zip, a package export (zip or folder with `resources.cnt`) or a folder of unpacked iFlows (each with `META-INF/MANIFEST.MF`).
  - `record PackageExport(List<Entry> entries)` with `record Entry(String id, String resourceType, String displayName, String semanticVersion, Path content)`; `static PackageExport read(Path zipOrDir)` decodes `resources.cnt` (Base64 JSON, key `resources`).
  - `final class PackageUnpacker { static void unpack(Path packageZipOrDir, Path outDir, List<String> stripTokens); }` — artifact zips (`IFlow`, `ScriptCollection`, `ValueMapping`) into `<outDir>/<symbolic-name>/`, `Change log` file as `<outDir>/CHANGELOG.txt` (CRLF → LF), `parameters.prop` and `Configured_Custom_Tags.txt` skipped, `stripTokens` removed from the symbolic name, `MANIFEST.MF` and `.project`; `Url`, `ContentPackage`, `Configured_Custom_Tags` entries ignored.
  - `final class IFlowDump { static String markdown(IFlow f); static String json(IFlow f); }` — Markdown: `## <id> (<name>, <version>)`, `- participant: <name> <componentType> <direction> -> <endpoint>`, numbered steps indented two spaces per depth as `n. <activityType> "<name>"` (`[unreached]` suffix when not reached), `- parameter: <name> = <default>` (` [configured]` suffix), `- resource: <path>`; JSON via `io.poxray.json.Json`, keys in record order.
  - CLI: `po-xray iflow <path> [--json]` prints one dump per iFlow to standard output; `po-xray unpack <package> <out-dir> [--strip a,b]`; both report a one-line summary on standard error, exit 0/1 like the existing commands.

- [ ] **Step 1: Write the fixture and the failing tests.** `IFlowFixture` builds a minimal iFlow into a `@TempDir`: `META-INF/MANIFEST.MF`, `src/main/resources/scenarioflows/integrationflow/<id>.iflw` (bpmn2 namespace, `ifl:property` key/value under `bpmn2:extensionElements`, `messageFlow`s with `ComponentType`/`direction`, `sequenceFlow`s, optional `subProcess`), `parameters.propdef`/`.prop`, `script/x.groovy`, `mapping/y.xsl`; with `zip()` and `packageExport()` helpers (the latter writes `resources.cnt` as Base64 JSON plus `<id>_content`). Tests (JUnit 5, AssertJ):
  ```java
  class IFlowReaderTest {
      @Test void manifestFields()                 // id, name, version from MANIFEST.MF, continuation line unfolded
      @Test void participantsWithComponentType()  // one JMS Sender, one ProcessDirect Receiver
      @Test void stepsInSequenceFlowOrder()       // start → Script "A" → Enricher "B" → end gives A, B
      @Test void subprocessStepsHaveDepthOne()    // exception subprocess with one Script at depth 1
      @Test void cycleTerminates()                // B → A back-edge; each step listed once
      @Test void unreachedStepFlagged()           // step with no incoming flow: reached = false, listed last
      @Test void parametersDefaultAndConfigured() // propdef default kept, prop value not stored, configured = true
      @Test void resourcesListed()                // script/x.groovy, mapping/y.xsl
      @Test void zipFolderAndPackageYieldSameIFlows()  // Review Focus 1
  }
  class PackageUnpackerTest {
      @Test void unpacksUnderSymbolicNameAndSkipsParametersProp()
      @Test void stripsTokensFromSymbolicNameManifestAndProject()
      @Test void writesChangelogWithLf()
  }
  class IFlowDumpTest { @Test void markdownLayout(); @Test void jsonKeysInRecordOrder(); }
  ```
- [ ] **Step 2: Run the tests, verify they fail.** `mvn -q -pl po-xray-core test -Dtest='io.poxray.cpi.*Test'` → Expected: compilation errors (classes missing).
- [ ] **Step 3: Implement the records, `IFlowReader`, `PackageExport`, `PackageUnpacker`, `IFlowDump`.** Parse with the JDK `javax.xml` (DOM or StAX, namespace-aware; the existing readers show the house style); step order by depth-first walk over `sequenceFlow` from each `startEvent`, visited set for cycles, recursion into `subProcess` children with depth + 1, unreached steps appended with `reached = false`; package detection = `resources.cnt` present or every top-level entry a zip.
- [ ] **Step 4: Run the tests, verify they pass.** Same command → Expected: all green.
- [ ] **Step 5: Acceptance on the real sources.** `SourcesAcceptanceTest`: `assumeTrue` that `System.getProperty("iflowforge.sources")` points to an existing `2026-06-trial`; `readAll` of every package folder must return 29 iFlows/collections in total, each with ≥ 1 step and no exception; one named assertion on a known iFlow (`com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination` has a participant with `componentType = "JMS"` and a step with `activityType = "Script"`). Fix parser gaps the real content reveals, adding a fixture-based test per gap.
- [ ] **Step 6: CLI commands and native registration.** `IflowCommand`, `UnpackCommand`, registration in `PoXrayCommand`, reflection config in `po-xray-native` for the four records; `mvn -q package -DskipTests` then `java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar iflow knowledge/sources/2026-06-trial/generic-pipelines | head -60` → Expected: a header line and a numbered step list, no stack trace. README rows added.
- [ ] **Step 7: Local-only check (Pavol or the local session, not the cloud).** `po-xray unpack "testdata/pipeline/trial-2026-06/Cloud Integration Pipeline - Generic Pipelines.zip" /tmp/gp` then `diff -r /tmp/gp knowledge/sources/2026-06-trial/generic-pipelines` → Expected: no differences. Record the result in the PR.
- [ ] **Step 8: Commit.** `git add po-xray-core/src/main/java/io/poxray/cpi po-xray-core/src/test/java/io/poxray/cpi po-xray-core/pom.xml po-xray-cli/src/main/java/io/poxray/cli/IflowCommand.java po-xray-cli/src/main/java/io/poxray/cli/UnpackCommand.java po-xray-cli/src/main/java/io/poxray/cli/PoXrayCommand.java po-xray-native README.md` then commit `cpi: iFlow reader, package unpacker, CLI iflow and unpack`.

---

### Task 3: `iflow-forge-knowledge`: knowledge base checks as a Maven test module

**Files:**
- Create: `iflow-forge-knowledge/pom.xml` (packaging `jar`, no main sources, test dependencies `po-xray-core`, JUnit 5, AssertJ; surefire system properties `kb.root` = `${maven.multiModuleProjectDirectory}/knowledge`, `kb.sources` = `2026-06-trial`; `kb.skip` and `kb.forbidden` passed through from the command line; `kb.skip` defaults to `coverage` until Task 6 clears it, F-2)
- Create: `iflow-forge-knowledge/src/test/java/io/iflowforge/knowledge/KnowledgeBase.java` (paths, page loading, skip handling), `Checks.java` (the check functions), `ChecksTest.java` (unit tests on a synthetic knowledge base in a `@TempDir`), and one JUnit class per check run against the real `knowledge/`: `CoverageTest`, `HeadingsTest`, `PlaceholdersTest`, `NamesTest`, `BudgetTest`, `ForbiddenValuesTest`
- Modify: `pom.xml` (root: add `<module>iflow-forge-knowledge</module>`)

**Interfaces:**
- Consumes: `IFlowReader.readAll`, `IFlow.resources()`.
- Produces (`Checks`, every method returns `List<String>` of failure messages, empty = pass):
  - `coverage(Path kb, Path sources)`: every iFlow id under `sources/generic-*` has `iflows/<id>.md`, every id under `sources/templates` has `templates/<id>.md`, every `*.groovy` / `*.xsl` resource has `scripts/<file-stem>.md`.
  - `headings(Path kb)`: every `iflows/*.md` and `templates/*.md` has exactly these H2 headings in order: `Purpose`, `Adapters`, `Steps`, `Headers and properties`, `Partner directory`, `Scripts and resources`, `Externalized parameters`, `Error handling`, `Extension points`, `Version notes`; every `scripts/*.md`: `Purpose`, `Inputs`, `Outputs`, `Gotchas`, `Used by`.
  - `placeholders(Path kb)`: the set of `{{tenant.*}}` tokens across `kb` equals the set listed in `naming.md` (one per bullet line, backticked); reports extra and missing.
  - `names(Path kb, Path sources)`: every backticked token that looks like an iFlow id (`com.sap.integration.cloud.pipeline.*`) or a resource file name (`*.groovy`, `*.xsl`) exists in the sources.
  - `budget(Path kb)`: `SKILL.md` + `overview.md` + `contracts.md` + `scenario-contribution.md` ≤ 5,000 words; any page ≤ 1,500 words; stub pages (containing `_Stub,`) skipped.
  - `forbidden(Path kb, Path forbiddenFile)`: no line of the file (one value per line, ≥ 4 characters) occurs anywhere under `kb`, case-insensitive. Review Focus 3.
  - Skipping: `-Dkb.skip=coverage,headings` makes the named real-KB test classes pass with an `Assumptions` skip; `ForbiddenValuesTest` runs only when `-Dkb.forbidden=<file>` is given (the file is local, absent in the cloud).
  - Run: `mvn -q -pl iflow-forge-knowledge -am test [-Dkb.skip=...] [-Dkb.forbidden=testdata/pipeline/forbidden-values.txt]`; `mvn verify` at the root runs it too.

- [ ] **Step 1: Write failing tests.** `ChecksTest`: one `@Test` per check with a positive and a negative case on a synthetic KB (`placeholdersReportsMissingAndExtra`, `forbiddenValueFound`, `budgetSkipsStubs`, `headingsOrderMatters`, `coverageReportsMissingPages`, `namesReportsUnknownScript`). The six real-KB test classes each call one check and assert the list is empty, after `KnowledgeBase.skipIfRequested("<check>")`.
- [ ] **Step 2: Run, verify they fail.** `mvn -q -pl iflow-forge-knowledge -am test` → Expected: compilation errors.
- [ ] **Step 3: Implement `KnowledgeBase` and `Checks`** (regexes: placeholders `\{\{tenant\.[a-z0-9_.]+\}\}`; names: backticked tokens ending in `.groovy`/`.xsl` or starting with `com.sap.integration.cloud.pipeline.`).
- [ ] **Step 4: Run, verify `ChecksTest` passes and the real-KB tests behave:** `mvn -q -pl iflow-forge-knowledge -am test -Dkb.skip=coverage,headings` → Expected: BUILD SUCCESS (the skeleton has no pages yet; placeholders, names, budget pass trivially; forbidden skipped).
- [ ] **Step 5: Commit.** `git add pom.xml iflow-forge-knowledge` then commit `knowledge: consistency checks as a test module`.

---

### Task 4: Pass A, generic iFlow pages

**Files:**
- Create: `knowledge/pipeline/iflows/<id>.md`, one per iFlow in `knowledge/sources/<label>/generic-inbound`, `generic-pipelines`, `generic-outbound` (10 iFlows)

**Interfaces:**
- Consumes: `po-xray iflow` output, `Checks.headings`.
- Produces: the page set that `contracts.md` (Task 7) and `overview.md` (Task 11) summarize; facts cited as `[<id>, step "<name>"]`.

- [ ] **Step 1: Dump every generic iFlow.** `mvn -q package -DskipTests` once, then for each of the three generic packages `java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar iflow knowledge/sources/<label>/<package> >> /tmp/dump-generic.md`.
- [ ] **Step 2: Write each page** with the ten H2 headings from Task 3, in message-path order (inbound XI and IDoc first, then Step02 variants, Step04, Step05, Step06, Step08 XI and IDoc). Start from the matching page in `knowledge/inputs/en/confluence-2026-10/` as a draft, then check every statement against the dump and the scripts, keep it only with a citation, and note in `pitfalls.md` where the draft and the artifact disagree. Under *Steps*: numbered list, one line per step `n. <activity type> "<name>": what it does [cite]`, subprocess steps indented. Under *Headers and properties*: two tables, `Set` (name, value or source, step) and `Read` (name, step, used for). Under *Partner directory*: every PD lookup (key pattern, value type, what happens when missing). Under *Externalized parameters*: name, default from `.propdef`, meaning, placeholder `{{tenant.<path>}}` if tenant-specific. *Extension points*: steps SAP visibly intends for customization (named "Custom", exit routers, ProcessDirect calls to configurable endpoints) plus "none found" if none. *Version notes*: empty until Task 10. ≤ 1,500 words each.
- [ ] **Step 3: Check after every three pages.** `mvn -q -pl iflow-forge-knowledge -am test -Dkb.skip=coverage` → Expected: BUILD SUCCESS. Commit each batch: `git add knowledge/pipeline/iflows knowledge/pipeline/pitfalls.md` then `KB: generic iFlow pages <ids>`.
- [ ] **Step 4: Coverage for generic.** Run with `-Dkb.skip=` (empty, overriding the pom default of F-2); expected failures mention only `templates/` and `scripts/`.

---

### Task 5: Pass A, template pages

**Files:**
- Create: `knowledge/pipeline/templates/<id>.md`, one per iFlow in `knowledge/sources/<label>/templates` (19 templates plus the script collection, value mapping and upload helper; the last three get a page under `templates/` too, with "Adapters" and "Steps" reduced to "not applicable" where that is the truth)

**Interfaces:**
- Produces: the pages that define what a scenario contributes (spec §12.1); `scenario-contribution.md` (Task 11) is derived from them.

- [ ] **Step 1: Dump templates:** `java -jar ... iflow knowledge/sources/<label>/templates > /tmp/dump-templates.md`.
- [ ] **Step 2: Write each page** with the same ten headings. Additionally, under *Purpose*, one sentence "A scenario copies this template when: <condition on scenario attributes>", and under *Externalized parameters* mark which parameters the scenario author must set (bold) versus leave (plain). Drafts: the one-page template entries in `knowledge/inputs/en/confluence-2026-10/step0*.md` and `templates.md`.
- [ ] **Step 3: Check and commit** as in Task 4; commit `KB: template pages`.
- [ ] **Step 4: Coverage:** run with `-Dkb.skip=` (empty, overriding the pom default of F-2); expected failures mention only `scripts/`.

---

### Task 6: Pass A, script pages

**Files:**
- Create: `knowledge/pipeline/scripts/<stem>.md`, one per distinct Groovy or XSLT resource in either package set

- [ ] **Step 1: List resources:** `grep -h "^- resource:" /tmp/dump-*.md | sort -u`.
- [ ] **Step 2: Write each page** with headings `Purpose`, `Inputs` (headers/properties/body read, with line cites), `Outputs` (headers/properties/body set), `Gotchas`, `Used by` (iFlow ids, step names). Three lines are enough for a trivial script. Identical scripts shipped in several iFlows get one page listing all users; scripts that differ between iFlows under the same name get one page with a "Variants" paragraph.
- [ ] **Step 3: Check and commit:** full test run with no skip → BUILD SUCCESS. Commit `KB: script pages`.

---

### Task 7: `contracts.md` and `partner-directory.md`

**Files:**
- Modify: `knowledge/pipeline/contracts.md`, `knowledge/pipeline/partner-directory.md`
- Modify: `iflow-forge-knowledge/src/test/java/io/iflowforge/knowledge/Checks.java`, `ChecksTest.java`; create `ContractNamesTest.java`

**Interfaces:**
- Produces: `Checks.contractNames(Path kb, Path sources)`: every header/property name found in the sources (content modifier `ifl:property` header/property table rows; Groovy `setHeader("X"`, `getHeaders().get("X"`, `getHeader("X"`, `setProperty("X"`, `getProperty("X"`) appears in `contracts.md`.

- [ ] **Step 1: Write the failing test** `contractNamesReportsMissing` in `ChecksTest` with a synthetic script setting `SAP_Receiver` and a `contracts.md` without it; `ContractNamesTest` on the real KB.
- [ ] **Step 2: Run, verify fail.** **Step 3: Implement the check.** **Step 4: Run, verify pass.**
- [ ] **Step 5: Write `contracts.md`**: sections *Message path* (one table: hop, from iFlow, to iFlow, transport `{{tenant.jms.queue.<x>}}` / `{{tenant.endpoint.<x>}}`), *Headers* (name, set by, read by, meaning, cite), *Exchange properties* (same), *Queues and endpoints* (placeholder, role, SAP default from `.propdef`), *Synchronous path* (what differs). Under 2,000 words.
- [ ] **Step 6: Write `partner-directory.md`**: key scheme (PID, parameter id patterns), value types (string, binary/XSLT), per-scenario entries as a table (key pattern, who reads it, cite), behaviour when missing.
- [ ] **Step 7: Check and commit:** full test run → BUILD SUCCESS. `git add knowledge/pipeline/contracts.md knowledge/pipeline/partner-directory.md iflow-forge-knowledge/src/test`, commit `KB: contracts and partner directory`.

---

### Task 8: `naming.md`, `versions/README.md`, `glossary.md`

**Files:**
- Modify: `knowledge/pipeline/naming.md`, `versions/README.md`, `glossary.md`

- [ ] **Step 1: `naming.md`**: section *SAP's own naming* (how the packages name iFlows, scripts, parameters, PD keys; cite), section *Tenant placeholders*: one bullet per placeholder, backticked, with meaning and example shape (never a real value), grouped `tenant.package.*`, `tenant.jms.queue.*`, `tenant.endpoint.*`, `tenant.naming.*` (prefix/suffix rules for scenario artifacts, filled by the profile), `tenant.deviations.*` (reserved, spec §12.2).
- [ ] **Step 2: `versions/README.md`**: current label, what `sources/<label>/` holds, how a new SAP version is processed (spec §8, last paragraph, as numbered steps using `po-xray unpack`).
- [ ] **Step 3: `glossary.md`**: pipeline terms, PO terms they correspond to (ICO, receiver determination, interface determination, agreement), IS terms (partner directory, ProcessDirect, externalized parameter).
- [ ] **Step 4: Check and commit:** full test run → BUILD SUCCESS (placeholder set must now match). Commit `KB: naming, versions, glossary`.

---

### Task 9: Local tenant comparison (not committed)

**Files:**
- Create (untracked): `testdata/pipeline/deviations-czint.md`
- Modify: `knowledge/pipeline/iflows/*.md` (*Extension points* only, if the comparison reveals a customization point)

- [ ] **Step 1: Dump the tenant export** (local): `po-xray iflow --json testdata/pipeline/czint-2026-09/analyza/<package> > testdata/pipeline/dump-czint-<package>.json` and the staged sources the same way.
- [ ] **Step 2: Compare** step lists and participants (`IFlowDump.json` is stable-ordered for this reason). Record per iFlow: identical / steps added or removed / parameters configured. Write `deviations-czint.md`.
- [ ] **Step 3: Feed back only structure**: where the tenant added a step at a place SAP left open, add that place to the generic page's *Extension points* described generically ("after step n, before the router"), without the tenant's step name or logic. Run the tests with `-Dkb.forbidden=testdata/pipeline/forbidden-values.txt`.
- [ ] **Step 4: Commit** only `knowledge/pipeline/iflows` changes: `KB: extension points from tenant comparison`. Verify `git status --short | grep testdata` is empty.

---

### Task 10: Pass B, claims from prior documentation

**Files:**
- Create: `knowledge/inputs/claims/confluence-2026-10.md`, `knowledge/inputs/claims/tenant-export-review-2026-09.md`, `knowledge/inputs/claims/triage.md`
- Create (untracked): `testdata/pipeline/claims/czint-pages.md`
- Modify: any `knowledge/pipeline/**/*.md` (enrichment), `pitfalls.md`
- Create: `knowledge/pipeline/versions/<label>/changelog.md`

- [ ] **Step 1: Extract claims**: inputs are the documents in `knowledge/inputs/en/` (Confluence pages and the 2026-09 review) and, locally only, the two tenant pages in `testdata/pipeline/confluence-2026-10/txt/czint-*.txt`. For each input set a fresh agent session with Appendix A of the spec as the prompt and the documents attached; save the output under `knowledge/inputs/claims/` (committed; no tenant values after Task 0's scrubbing) and the tenant pages' claims under `testdata/`.
- [ ] **Step 2: Triage table** `claims/triage.md`: columns claim id, source, disposition ∈ {`fact→<page>`, `rationale→<page>`, `procedure→Task 11`, `pitfall`, `changelog`, `tenant→profile`, `dropped (reason)`}. Every claim gets a row.
- [ ] **Step 3: Apply**: `fact→` rows: locate the artifact, add the sentence with its cite; if the artifact disagrees, the row becomes `pitfall` ("doc said X, artifact shows Y"). `rationale→` rows: add with tag `[rationale, from analysis 2026]`. `changelog` rows: entries in `versions/<label>/changelog.md` alongside the artifact diff (`2026-06-trial` versus `2026-09-current`, compared with `po-xray iflow --json` on both) and SAP's `CHANGELOG.txt`; the inline "(v1: was X)" note on the affected page under *Version notes*. `tenant→profile` rows: copy to `testdata/pipeline/profile-draft-czint.md` (input to part 2).
- [ ] **Step 4: Verify**: every claim has a triage row; full test run → BUILD SUCCESS (budget!). Commit `KB: enrichment from prior documentation, changelog <label>` (only `knowledge/`).

---

### Task 11: Pass C, procedures, decision tables, overview, SKILL.md

**Files:**
- Modify: `knowledge/pipeline/overview.md`, `scenario-contribution.md`, `SKILL.md`, `conversion/README.md`
- Create: `migration/po-ico-to-pipeline.md`, `migration/is-iflow-to-pipeline.md`, `migration/proposal-template.md`, `migration/variants.md` (only if the budget needs it)

**Interfaces:**
- Produces: the **scenario attribute set** (names and allowed values) that parts 2 to 4 will use as the scenario specification's top-level fields. Initial set (revised with Pavol in Step 1): `qos ∈ {EO, EOIO, BE}`, `sender_type ∈ {XI, IDOC, SOAP, OTHER}`, `receiver_type` same, `receiver_count ∈ {1, n}`, `mapping ∈ {none, xslt-pd, mapping-iflow}`, `routing ∈ {static, conditional, dynamic}`, `runtime ∈ {integrated-async, integrated-sync, decoupled}`.

- [ ] **Step 1: Interview Pavol** (one question at a time) on the attribute set and on the steps of a real migration he did by hand; record decisions at the top of `scenario-contribution.md` under *Scenario attributes*.
- [ ] **Step 2: `scenario-contribution.md`**: table *What a scenario contributes*: rows = artifacts (template copies, PD entries, configuration), columns = attribute conditions, cells = required / optional / not applicable, with cite to the template page. Section *What a scenario must not touch* (generic iFlows, shared parameters). Section *Naming* referencing `{{tenant.naming.*}}`.
- [ ] **Step 3: `migration/po-ico-to-pipeline.md`**: numbered procedure: 1 read ICO JSON (po-xray `ico` output; fields used: `sender.iface`, `sender.channel`, `receivers[].qualityOfService`, `receivers[].condition`, `receivers[].interfaces[].mapping`, `channels[].adapter`, `channels[].modules`), 2 derive attributes (table ICO field → attribute), 3 fill proposal template, 4 human approval, 5 produce artifacts per `scenario-contribution.md`, 6 convert modules/mappings per `conversion/`, 7 **append to the knowledge base** (spec §8: recipe, pitfall, page note) before done.
- [ ] **Step 4: `migration/is-iflow-to-pipeline.md`**: same shape; step 1 reads `po-xray iflow` output (participants → sender/receiver types, steps of mapping types → mapping kind, routers → routing).
- [ ] **Step 5: `migration/proposal-template.md`**: header block with the attribute values, then sections *Source summary*, *Target artifacts* (table from Step 2 filtered), *Partner directory entries*, *Conversions needed*, *Open questions*, *Approval* (name, date).
- [ ] **Step 6: `conversion/README.md`**: how a recipe page is structured (`Source` PO module or mapping kind, `Target` IS construct, `Steps`, `Example`, `Pitfalls`); table of standard `AF_Modules/*` → IS equivalent as far as known from the claims (Task 10 `procedure→Task 11` rows); rule "custom module or Java mapping → Groovy unless a native step exists; prefer message mapping or XSLT where the logic is structural".
- [ ] **Step 7: `overview.md`**: one page: purpose, message path with a Mermaid `sequenceDiagram` (participants = generic iFlows, arrows labelled with placeholders), list of generic iFlows with one line each linking to pages, the three runtime variants in one paragraph each.
- [ ] **Step 8: Final `SKILL.md`**: loading table (need → page), the citation and placeholder conventions in three lines, the self-improvement rule in three lines.
- [ ] **Step 9: Check and commit:** full test run → BUILD SUCCESS (budget ≤ 5,000 words for the loaded set; move branches to `variants.md` if not). Commit `KB: procedures, decision tables, overview`.

---

### Task 12: Benchmark

**Files:**
- Create: `knowledge/pipeline/benchmark/questions.md` (from Task 0 step 8), `benchmark/sample-ico.json`, `benchmark/expected-proposal.md`, `benchmark/results-<date>.md`
- Create: `iflow-forge-knowledge/src/test/java/io/iflowforge/knowledge/BenchmarkFixtureTest.java` (parses `sample-ico.json` with Jackson into `io.poxray.model.IntegrationInventory` and asserts the fields Task 11 Step 3 names are present)

**Interfaces:**
- Produces: `sample-ico.json`, valid against `IntegrationInventory` (`docs/OUTPUT-MODEL.md`), invented names (`SND_SYS`, `RCV_SYS`, `urn:example:...`), one async XI → IDoc scenario with one conditional receiver and one `AF_Modules/MessageTransformBean` on the sender channel; later the first test input of part 4.

- [ ] **Step 1: Questions** in place (`benchmark/questions.md`, format: numbered, `**Q:**` / `**Expected:**`).
- [ ] **Step 2: Write `sample-ico.json`** by hand per the interface above; `BenchmarkFixtureTest` green. Write `expected-proposal.md` by filling `proposal-template.md` for it.
- [ ] **Step 3: Run the benchmark**: a fresh Claude Code session in the repository with the single instruction: "Load skill sap-is-pipeline. Answer every question in knowledge/pipeline/benchmark/questions.md in a file /tmp/benchmark-answers.md. Then follow knowledge/pipeline/migration/po-ico-to-pipeline.md for knowledge/pipeline/benchmark/sample-ico.json up to and including the proposal, into /tmp/benchmark-proposal.md." No other context, no MCP.
- [ ] **Step 4: Judge**: Pavol scores answers (correct / partial / wrong) and compares the proposal with `expected-proposal.md`. Record in `benchmark/results-<date>.md`: score table and one line per wrong answer naming the page to fix.
- [ ] **Step 5: Fix pages** for every wrong answer; rerun Step 3; stop when every answer is correct or partial with a documented reason. Full test run → BUILD SUCCESS.
- [ ] **Step 6: Commit** `git add knowledge/pipeline iflow-forge-knowledge/src/test` then `KB: benchmark and fixes`.

---

### Task 13: Done check against the spec

- [ ] **Step 1: Walk spec §9** criteria 1 to 7, each with the command or file that proves it (test run output, `versions/<label>/changelog.md`, `naming.md`, `benchmark/results-<date>.md`, triage table with no unhandled rows, `grep -ri czint knowledge/` empty).
- [ ] **Step 2: Update the spec status line** to `Status: implemented <date>` and `README.md` to mention the skill. Commit `KB: part 1 done`.
