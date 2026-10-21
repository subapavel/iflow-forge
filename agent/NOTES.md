# Notes — facts the hand-off used to carry

Read by section, on demand (CLAUDE.md, Context hygiene). One section per area; the newest first.
Facts only; the next exact action and the state live in `agent/HANDOFF.md`.

## Sources layout (KB-00, 2026-10-19)

- `knowledge/sources/<label>/<package-short-name>/<Bundle-SymbolicName>/` with `META-INF/MANIFEST.MF`,
  `.project`, `src/main/resources/{scenarioflows/integrationflow/*.iflw, script/*.groovy, mapping/*,
  xsd/*, parameters.propdef}`; `CHANGELOG.txt` per package; `INDEX.md` per label (package, type, name,
  symbolic name, version). Package short names: `generic-inbound`, `generic-pipelines`,
  `generic-outbound`, `templates`.
- `2026-06`: 29 artifacts (Step02 1.0.5, Step04 1.0.4, Step05 1.0.3, Templates 1.1.1 era).
  `2026-09`: 32 (Step02 1.0.6, Step04 1.0.5, Templates 1.2.0: adds the o2m-to-IDoc template,
  the skip-retry value mapping and the partner-directory upload helper). The three new artifacts
  had a creation-timestamp suffix in their symbolic names in the tenant export; stripped.
- `parameters.prop` never present (configured values); `.propdef` carries SAP defaults.
- A package export zip = `resources.cnt` (Base64 JSON, key `resources`, each entry `id`,
  `resourceType` ∈ {IFlow, ScriptCollection, ValueMapping, File, Url, ContentPackage,
  Configured_Custom_Tags}, `displayName`, `semanticVersion`, `contentType`) + `<id>_content`
  (a zip for deployable artifacts, plain text for the change log) + `contentmetadata.md` (Base64
  properties, carries the tenant environment name — never staged).

- Windows clones: the longest `.iflw` paths under `knowledge/sources/` are about 230 characters below the repository root, so a clone fails with "Filename too long" unless `git config --global core.longpaths true` is set first (owner, 2026-10-20); a short clone location such as `C:\src\iflow-forge` also helps. After a failed checkout, `git reset --hard HEAD` repairs the clone.
## iFlow file format (observed in the sources)

- `.iflw` is BPMN 2.0 XML (`bpmn2:` namespace) with SAP extensions as `ifl:property` elements
  (`key` / `value` children) under `bpmn2:extensionElements`. Participants are
  `bpmn2:participant`; adapters are `bpmn2:messageFlow` with properties `ComponentType`
  (JMS, ProcessDirect, XI, IDOC, SOAP, HTTP…), `direction` (Sender / Receiver), `Name`, and the
  adapter's configuration (e.g. `QueueName_outbound`, `address`). Steps are `bpmn2:callActivity`
  / `bpmn2:serviceTask` with property `activityType` (Script, Enricher, XSLTMapping,
  ProcessCallElement, ExternalCall, IdempotentProcessCall…) and `cmdVariantUri`; routers are
  `bpmn2:exclusiveGateway`, conditions on `bpmn2:sequenceFlow` (`expressionType`, body text).
  Local processes are `bpmn2:subProcess`; exception subprocesses carry `isForCompensation` /
  event definitions. Step order = `sequenceFlow` walk from `startEvent`, not XML order.
- Externalized parameters appear as `{{Name}}` inside property values; defaults in
  `parameters.propdef` (XML), configured values in `parameters.prop` (Java properties).
- Scripts referenced as `script=<file>.groovy` in the Script step's properties; some templates
  reference a shared script collection by `scriptBundleId`.

## Inputs provenance (KB-00)

- `knowledge/inputs/en/confluence-2026-10/`: 39 pages translated from the owner's Czech Confluence
  space (PDF export 2026-10-19), written from a reverse analysis at 1.0.4/1.0.5; the ten
  `generic-*` pages carry full script listings. Position-sorted PDF extraction was needed; the
  translations keep PDF line wraps inside code blocks in places.
- `knowledge/inputs/en/tenant-export-review-2026-09.md`: ChatGPT analysis of the September export,
  with a version table (1.0.5 → 1.0.6 etc.) and deviations found.
- Two tenant-specific pages (runtime configuration + naming convention, partner directory
  configuration) exist only under `testdata/` — input for part 2.

## Build and environment

- JDK 25, Maven 3.9; `mvn -B -ntp -q verify`; tests needing `testdata/` skip when absent
  (`io.poxray.TestData`, `-Dpoxray.testdata=<dir>`).
- Windows checkouts need `git config core.longpaths true` (source paths exceed 260 characters);
  `.gitattributes` stores `knowledge/sources/**` without EOL conversion.
- CLI: `po-xray` group command (`PoXrayCommand.groupCommands`), Quarkus + Aesh, uber-jar
  `po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar`; MCP server in `po-xray-mcp`;
  reflection registration for native images in `po-xray-native`.
- iFlow reader (KB-02): `io.poxray.cpi` — `IFlowReader.read/readAll`, `PackageExport`, `PackageUnpacker`, `IFlowDump`;
  CLI `iflow <path> [--json]` is the reading surface for the pages, `unpack <package> <out> [--strip a,b]`
  stages a package export. Step order per D-016; `Participant` carries endpoint + adapter properties (F-1).
  Follow-ups: the reader does not yet report dangling references (a `processId` naming no process, a
  flow to a missing id) — add an `issues` list to `IFlow` when one is first observed in a source;
  the unpacker ignores `File` and unknown entry types silently; `iflow --json` prints one JSON object
  per iFlow (not an array); `UnpackCommand` counts by re-reading every artifact (count the export entries instead);
  a missing `Bundle-SymbolicName` gives `id = null` in the reader but throws in the unpacker; the Receiver
  fallback of the main-process choice and a subProcess reached by a flow are untested; `cycleTerminates`
  would hang rather than fail (consider `@Timeout`); the synthetic strip token `.CZTEST` in the tests is
  to be checked against the owner's forbidden list (rename to `.TENANT` if it hits). A PROGRESS heading takes its commit hash from the following task's commit.
- KB-01 follow-up for Task 11: `knowledge/pipeline/SKILL.md` says "load nothing else"; spec §6 loads SKILL + overview +
  contracts + scenario-contribution together — fix the wording when SKILL.md is rewritten (kb-reviewer remark).
- Every `mvn` log carries one stderr line `[Fatal Error] :1:9: XML document structures must start and end within the same entity.` — the JDK parser reporting the malformed-XML fixture of a po-xray-core negative test (present since KB-03 logs); not a failure, the build exit code decides.

## Knowledge module (KB-03)

- `iflow-forge-knowledge` has no `src/main`; `Checks` and `KnowledgeBase` live in `src/test/java/io/iflowforge/knowledge/`.
  Run: `mvn -B -ntp -q -pl iflow-forge-knowledge -am test [-Dkb.skip=a,b] [-Dkb.forbidden=<file>]`.
- `kb.skip` defaults to empty in the module pom since KB-06 (F-2 closed): every check runs; `-Dkb.skip=a,b` skips named
  checks. `kb.root` = `<repo>/knowledge`, `kb.sources` = `2026-06`.
- `ForbiddenValuesTest` skips unless `-Dkb.forbidden` names an existing file; a relative value resolves against the
  repository root. The owner's file stays under `testdata/` (gitignored). Messages never contain the value.
- `CitesTest` (MIG-04, skip name `cites`) resolves every `[page: <path>#<heading>]` (repo-relative path, exact H2/H3 text) and
  `[pattern: <id>]` under `skills/` and `patterns/`; cites containing `<`, `>` or `…` are form descriptions and are skipped.
- Synthetic sources for checks tests: `io.poxray.cpi.SyntheticArtifact` (public bridge to the package-private
  `IFlowFixture` of the po-xray-core test-jar), one Script step plus the given resources.
- MIG-01 roots: `KnowledgeBase.repoRoot()` (parent of `kb.root`), `skills()`, `patterns()`, `requests()`; a missing folder is empty.
  `BudgetTest` runs `Checks.budgetPages` (every `*.md`, PAGE_LIMIT only, `_Stub,` counts 0), `ForbiddenValuesTest` `Checks.forbiddenIn`.
- `SkillMirrorTest` (`Checks.skillMirror`, skip `mirror`): pointer frontmatter = canonical line for line, body names the canonical
  path; canonical body free of `@file`, `/cmd` line starts, backticked tool calls. Pointer list: `repositorySkillsMirror`.
- Reader follow-up (KB-04 B2): `IFlow` does not expose the iFlow-level `ifl:property` entries of the collaboration/process (e.g. `allowedHeaderList`); pages cite them by grep of the .iflw (D-019). Expose them in a later reader task.
- Follow-ups: coverage treats a `generic-*` package by folder prefix and `templates` by exact name; a new package kind
  needs a rule. Headings also reports `unexpected "X" at position N` for extra H2s (beyond the spec, kept).
- Reviewer remarks (KB-03, not blocking): without a `.mvn` folder `maven.multiModuleProjectDirectory` is where mvn starts, so
  from the module folder the real-KB tests see an empty KB and pass (add an assume on `Files.isDirectory(pipeline())`);
  C6 scans contents only — a value in a file name is not found and a hit in such a file would leak it through the path
  (spec-level question, open); the coverage message `artifact without id in package <folder>` is untested; limits are
  tested at 1501 / 5200 only; `io.poxray.cpi.SyntheticArtifact` splits the package across modules (classpath only).
- Reader follow-up (KB-05 B1, dump order D-016): the local process of an `IdempotentProcessCall` is not inlined (template step01 eo: Send "Send" is step 1 at depth 0, outside the call), and a local process called from several places is inlined only under its first call (template step01 sync: steps 13 and 16 call the process inlined under step 6). Pages follow the dump numbering and say where the step sits; a later reader task may inline idempotent calls and mark repeated calls.
- `Checks.contractNames` (KB-07, D-021) follow-ups from the reviewer, none affecting today's unpacked sources: `artifacts()`
  walks folders holding `META-INF/MANIFEST.MF` and so skips zip and package-export artifacts that `readAll` reads; the `$`
  filter also drops Groovy literals (D-021 names only table cells); property-style access (`message.headers.X`,
  `message.properties.y`) and router conditions (`${header.X}`) are not extracted; `\bheaders.get` misses `msgheaders.get`.
  Test 6 (`contractNamesCoversEveryCallFormAndFirstArtifact`) was written after the code; the mutation logs
  `target/kb-07-mut1..4.log` stand in for its red run.

## Skill portability (MIG-00/MIG-01, 2026-10-20)

- Codex discovers repository skills under `.agents/skills/` (constants `AGENTS_DIR_NAME = ".agents"`, `SKILLS_DIR_NAME = "skills"`, repo-scoped roots between project root and cwd) [external: github.com/openai/codex codex-rs/ext/skills/src/host_roots.rs]; user scope `~/.agents/skills/`; it reads only `name` and `description` until the skill triggers.
- Gemini CLI reads workspace skills from `.gemini/skills/` or the `.agents/skills/` alias; agent skills are enabled by default (`skills.enabled` default true) [external: github.com/google-gemini/gemini-cli docs/cli/using-agent-skills.md "Discovery tiers"; packages/cli/src/config/settingsSchema.ts]. One `.agents/skills/` pointer therefore serves Codex and Gemini; spec §6.1 changelog 2026-10-20.
- The cloud environment's network policy blocks developers.openai.com (curl 403 via the proxy, WebFetch ENOTFOUND); Context7 (`/openai/codex`, `/google-gemini/gemini-cli`) is the reachable source for these facts.

## MIG-21 — CLI `discover`: acceptance spec and scout facts (architect, 2026-10-21; owner continues locally)

Written in the cloud session before the hand-over; the next architect session starts MIG-21 from here (test-author first).

### Acceptance spec


Context: D-045 / F-13; MIG-20 built `ScenarioDiscovery` / `DiscoveryReport` (po-xray-core, commit 72d54e3). Facts
from the scout: scout-facts.md in this folder (read it). Modules: po-xray-cli (Quarkus + Aesh) and po-xray-core.

#### Command (draft — review at hand-over)

`po-xray discover (-s <system> | --icos <icos.json>) [-o <dir>] [-l cs|en] [--json] [--refresh] [--cache <dir>] <export-dir>...`
- `<export-dir>...` (1..n, required): directories written by `po-xray export -o`, or single inventory JSON files;
  each is read as `ScenarioDiscovery.read` reads one (same listing and `NOT_SCA_INVENTORY` rule). Core gains a public
  overload `ScenarioDiscovery.read(List<Path> exports, IntegrationInventory icos, String icosLabel)` (or equivalent)
  so the CLI never re-implements the listing.
- `-s <system>`: every ICO of the system — `po.query(system, cache, refresh).icos("*", Direction.BOTH)` exactly as
  IcoCommand does for one component (live simple query or `tpz=`); the inventory label is `system:<name>`.
- `--icos <file>`: an existing all-ICO inventory JSON (`po-xray ico -s <system> -c '*' -d both -f json`), offline;
  label = the file path. Exactly one of `-s` / `--icos` must be given, else exit 1 with
  `Error: give -s <system> or --icos <icos.json>` on stderr.
- stdout: the report's Markdown, or JSON with `--json` (as `bpm-inventory`). stderr: one summary line
  `Discovered <p> process(es), <c> component(s), <i> issue(s) from <n> export path(s)`.
- `-o <dir>`: additionally writes `discover.json` and `discover.md`, and for every component of the report (every
  role) `icos-<component>.json` and `icos-<component>.md` (base name via `PoService.baseName`, locale `-l`, default
  `cs`, Markdown through the same `DocFormat` path IcoCommand uses). The per-component inventory is a slice of the
  all-ICO inventory fetched once — a new core helper `IntegrationInventories.slice(IntegrationInventory all, String
  component)` (name free, in io.poxray.integration or io.poxray.migration): the ICOs whose sender component or any
  receiver component equals `component` (exact, case-sensitive), `components` = [that component], the other fields
  copied, issues copied. No second query per component. Each written path is reported on stderr as `Written <path>`.
- Exit 1 with `Error: ...` when an export path does not exist, when no export path holds an inventory with `bpm`
  (`No SCA inventory with bpm in <paths>`), or when `--icos` is unreadable (`NOT_ICO_INVENTORY` is an issue in core;
  the CLI turns it into exit 1 so the migrator notices).
- Registry: PoXrayCommand `groupCommands` and the usage text gain `discover`.

#### Systems directory override (core)

`PoSystem.defaultDirectory()` honours, in this order, the system property `poxray.systems`, the environment variable
`POXRAY_SYSTEMS`, then `~/.po-xray/systems`. Documented in PoSystem's Javadoc and docs/inputs/README.md §3.
(Reason: tests and CI must point `-s` at a synthetic system without touching the home directory.)

#### Docs

docs/inputs/README.md §3: a paragraph after the `ico` command — `discover` as the way to get the ICOs around a BPM
process from the exports (command line, what it writes), `ico -c` stays for a single component.
docs/OUTPUT-MODEL.md `discover` section: replace "the CLI command comes with MIG-21" with the command line and `-o`
files.

#### Red tests (test-author)

1. po-xray-cli/src/test/java/io/poxray/cli/DiscoverCommandTest.java (@QuarkusMainTest, QuarkusMainLauncher,
   @TempDir): the test builds its own export (a ScaInventory with one process `OrderApproval`, interfaces
   OrderIn/urn:synth:orders start, ShipOut/urn:synth:ship outbound, NotifyOut/urn:synth:notify notification — mirror
   the MIG-20 core test fixture, Json.write into `<tmp>/export/SYNTH.json` plus a `notes.json`) and its own all-ICO
   inventory (ICO1 SRC_SYS→BPM_CC on OrderIn, ICO2 BPM_CC→WMS_SYS,ERP_SYS on ShipOut, ICO3 unrelated OTHER→X) written
   to `<tmp>/icos-all.json`. Cases:
   - `discover --icos <icos-all.json> <export>`: exit 0; stdout has `# Scenario discovery`, `### OrderApproval`,
     `ICO1`, `| BPM_CC |`; stderr has `Discovered 1 process(es), 4 component(s), 1 issue(s)`.
   - `--json`: stdout parses as DiscoveryReport; processes[0].processComponents == [BPM_CC]; components names
     [BPM_CC, ERP_SYS, SRC_SYS, WMS_SYS]; icosInput ends with `icos-all.json`.
   - `-o <out> -l en`: `discover.json`, `discover.md`, `icos-BPM_CC.json`, `icos-BPM_CC.md`, `icos-SRC_SYS.json`,
     `icos-WMS_SYS.json`, `icos-ERP_SYS.json` exist; `icos-BPM_CC.json` parses as IntegrationInventory with exactly
     ICO1 and ICO2 and components [BPM_CC]; `icos-SRC_SYS.json` has only ICO1; no `icos-OTHER.json`, no `icos-X.json`.
   - two export paths (a directory and a single file) → both in `inputs`.
   - neither `-s` nor `--icos` → exit 1, stderr `give -s <system> or --icos`; both given → exit 1.
   - missing export path → exit 1, stderr names the path; export without bpm → exit 1 `No SCA inventory with bpm`.
   - unreadable `--icos` file → exit 1, stderr contains `NOT_ICO_INVENTORY` or the file name.
   - `-s` on a synthetic tpz system: ONLY if FakePo/SyntheticTpz (po-xray-core test-jar is on the cli test classpath)
     already offer an AllInOne/ICO XML builder that TpzGateway + IntegrationReader read into an Ico with sender and
     receiver interfaces (look in TpzGatewayTest, ObjectCatalogTest, BpmIntegrationTest, IntegrationReaderTest for
     an existing AllInOne xiObj). If it exists: write `systems/<name>.properties` with `tpz=../exports` in @TempDir,
     set `System.setProperty("poxray.systems", ...)` in the test (restore in @AfterEach), run `discover -s <name>
     <export>` and assert ICO1 in stdout. If no such builder exists, do not invent one: say so in the handback and
     leave `-s` to the shared IcoCommand path.
2. po-xray-core/src/test/java/io/poxray/po/PoSystemTest.java (new or extended if one exists): `poxray.systems`
   property → `PoSystem.load(name)` reads `<dir>/<name>.properties` (restore the property in @AfterEach); property
   absent + env absent → default under `user.home` (assert the path shape, no file access).
3. po-xray-core/src/test/java/io/poxray/migration/ScenarioDiscoveryTest.java: at most two new tests for the overload
   `read(List<Path>, IntegrationInventory, String)` (two paths, inputs order follows the argument order) and for the
   slice helper (sender match, receiver match, exactness, components = [component], unrelated ICO dropped). The
   existing 17 tests stay untouched.
Red evidence: `mvn -B -ntp -q -pl po-xray-core,po-xray-cli test -Dtest='DiscoverCommandTest,PoSystemTest,ScenarioDiscoveryTest' -Dsurefire.failIfNoSpecifiedTests=false > target/mig-21-red.log 2>&1`
(compile failure on the missing command/helper is the expected red; if Quarkus main tests need the cli module built,
say what you ran). Handback ≤ 40 lines: files, test names, the red's last lines, choices made (esp. the `-s` test).
No production code, no fixture files under skills/.

### Scout facts

- PoService.java (37 lines): L21 `IntegrationQuery query(String system, String cache, boolean refresh)` = PoConnection.open(PoSystem.load(system), cache, refresh) → new IntegrationQuery(gateway, source); L27 `catalog(system)`; L33 `static String baseName(component)` = "icos-" + component with [^A-Za-z0-9_.-] → "_". No file writes.
- IcoCommand.java: -s req (27-29), -c req (31-33), -d both|in|out (35-37), -f json|markdown|jira|all (39-41), -l cs|en default cs (43-45), -o default "." (47-48), --refresh (50-51), --cache (53-54); execute 63-65 `po.query(system, cache, refresh).icos(component, Direction)`; stderr summary 67-74; writes 75-88 via ScaService.write (76-87): json 78-80 `Json.write(inv)` → icos-<base>.json; docs 81-87 per DocFormat (MARKDOWN .md, JIRA .jira.txt) with Locale.forLanguageTag(lang); "Written <path>" stderr; nothing on stdout.
- ScaService.run (34-43): CliException → "Error: msg" stderr, exit 1; CliException 25-28.
- No CLI test uses a PO system; no IcoCommandTest. SyntheticTpz.write(Path file, String metadata, List<String> xiObjs) (core test, L20) generic zip (Metadata.properties + content.tpt); XML via FakePo.xiObj(vcType, type, oid, key, content, links) (FakePo.java:136-137). TpzGatewayTest: exports(dir) 28-39 builds esr.tpz + directory.tpz (Service, RoutingRelation; no AllInOne), L43 `new TpzGateway(PoSystem.tpz(name, dir))`, L66-80 writes systems/<name>.properties `tpz=../exports`, `PoSystem.load(name, systems, Map.of())` (package-private, io.poxray.po), `PoConnection.open(system, null, false)`.
- PoSystem.load: defaultDirectory() 67-70 = user.home/.po-xray/systems, no override property; load(name) 72-74 public; load(name, dir, env) 76 package-private; tpz key 92-96 relative to systems dir; url 97-101; env POXRAY_USER/_PASSWORD, POXRAY_<SYS>_USER/_PASSWORD.
- IntegrationQuery.icos("*", BOTH) selects every ICO with non-null key(1) (glob 262-275 `*`→`.*`; loop 98-122); list(type) 207-212 via source.list with MODIFYUSER LIKE *. TpzGateway.query 94-120 enumerates objects.ofType(type), `*`/blank value → true (145-147); AllInOne keys TypeKeys.java:24-25 (FROMPARTY, FROMSRVC, FROMACTIONNS, FROMACTION, TOPARTY, TOSRVC); default list = XiObjectSource.java:25.
- Registry: PoXrayCommand.java:15-17 groupCommands {..., IcoCommand, ..., AttributesCommand, BpmInventoryCommand}; usage text 22-24 lists commands — add discover to both.
- Tests: BpmInventoryCommandTest (@QuarkusMainTest, launcher.launch(args), exitCode/getOutput/getErrorOutput, fixture ../skills/sap-po-migration/fixtures/bpm-export); CliTest (SyntheticSca.write, launch("export", ..., "-o", out, "-l", "en")).
- docs/inputs/README.md 23-33 ico section (properties file url=/tpz=, command line 31-33); docs/OUTPUT-MODEL.md 525-530 "## Migration helpers" intro says "Two read-only helpers" — becomes three.
- Design choice (architect): a systems-directory override for tests and CI — system property `poxray.systems` (then env `POXRAY_SYSTEMS`) read in PoSystem.defaultDirectory(); the CLI test writes systems/<name>.properties with tpz= and a synthetic directory.tpz holding AllInOne objects (FakePo.xiObj), sets the property, launches `discover -s <name> -o <dir> <fixture-export>`. Also `--icos <icos.json>` offline alternative to -s (reads an existing all-ICO dump) — decide in the acceptance spec.
