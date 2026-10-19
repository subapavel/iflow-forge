# Pipeline Knowledge Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the SAP Integration Suite pipeline knowledge base under `knowledge/pipeline/`, exposed as the Claude Code skill `sap-is-pipeline`, derived from the SAP pipeline packages and enriched from two chat analyses and one Czech document.

**Architecture:** Two small Python tools make the work mechanical and checkable: `iflow_dump.py` turns an iFlow (zip, package zip or folder) into a step list the author writes pages from, and `kb_check.py` verifies the knowledge base against the sources (coverage, headings, placeholders, names, budget, leaked tenant values). Content is written in three passes: A (artifacts → pages), B (chat claims → enrichment, pitfalls, changelog), C (procedures, decision tables, benchmark).

**Tech Stack:** Python 3.14 standard library only (run with `python -I`), `unittest`; Markdown; Mermaid; Claude Code skills. The Maven/Java modules are not touched.

**Spec:** `docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md`

## Global Constraints

- Language of everything under `knowledge/`: English.
- No customer data under `knowledge/` or anywhere committed: no E.ON queue names, URLs, package names, configured parameter values. `testdata/` is gitignored and is the only place for tenant exports, chat exports, the Czech document and claim lists.
- Only clean catalogue downloads go under `knowledge/sources/<version>/` (spec §12.6). The repository is private.
- Current pipeline version is primary; older version differences are inline "(vN: was X)" notes plus `versions/<version>/changelog.md`; changelog entries from chats are marked `[source: analysis chat, unverified against v1 artifacts]` (spec §12.7).
- Every behavioural fact cites an artifact: `[<iflow-id>, step "<name>"]` or `[scripts/<file>#L<n>]`. No citation, no fact; it becomes an open question in `pitfalls.md`.
- Tenant-specific values are placeholders `{{tenant.<path>}}`; the complete list lives in `naming.md`.
- Loaded set (`SKILL.md` + `overview.md` + `contracts.md` + `scenario-contribution.md`) ≤ 5,000 words; any single page ≤ 1,500 words (spec §6, §12.8).
- Checklists are decision tables keyed on scenario attributes (quality of service, sender type, mapping kind, receiver count), not one linear path (spec §12.8).
- Python tools: stdlib only, run as `python -I tools/<tool>.py`, tests with `python -I -m unittest discover -s tools/tests`.
- Commit after every task; commit only the files the task names (never `git add -A`; `testdata/` must stay untracked).
- **Where tasks run:** Task 0 runs locally on Pavol's machine (it touches confidential folders). Tasks 1 to 13 run in
  Claude Code cloud sessions from the private GitHub repository, so everything they need must be committed by
  Task 0: sources under `knowledge/sources/`, English inputs under `knowledge/inputs/en/`. Task 9 (tenant diff) is
  the one exception and runs locally.
- Version labels are export dates, not package versions (each package carries its own): `2026-06-trial` (older,
  SAP trial tenant export), `2026-10-catalog` (current, clean catalogue download once Pavol provides it). Spec §13.

## Review Focus

1. **Package exports that nest iFlow zips** (a package zip holding `resources.cnt` plus one zip per artifact) versus a single iFlow zip versus an already unpacked folder: `iter_iflows` must yield the same `IFlow` objects for all three. Test added in Task 2.
2. **BPMN with cycles or exception subprocesses**: step ordering by following sequence flows must terminate and must list steps inside subprocesses under their parent. Test added in Task 2.
3. **Configured tenant values leaking into pages**: a value from the tenant export's `parameters.prop` must never appear in `knowledge/`. `kb_check --forbidden` test added in Task 3, run in every content task.
4. **Placeholder drift**: `{{tenant.jms.inbound}}` in one page and `{{tenant.jms.queue.inbound}}` in another. Placeholder check added in Task 3.
5. **Pages naming scripts or iFlows that do not exist in the sources** (typo or renamed in the new version). Name check added in Task 3.

---

### Task 0 (local): data collection, scrubbing, staging, push

**Files:**
- Create (untracked): `testdata/pipeline/README.md`, `testdata/pipeline/trial-2026-06/`, `testdata/pipeline/czint-2026-09/`, `testdata/pipeline/confluence-2026-10/{pdf,txt}/`, `testdata/pipeline/forbidden-values.txt`, `testdata/pipeline/stage.py` (throwaway unpack/scrub script)
- Create (committed): `knowledge/sources/2026-06-trial/<package>/<artifact-id>/...`, `knowledge/sources/README.md`, `knowledge/inputs/en/README.md`, `knowledge/inputs/en/**/*.md`
- Modify: `.gitignore` (`knowledge/sources/**/parameters.prop`, `knowledge/sources/**/Configured_Custom_Tags.txt`)

**Interfaces:**
- Produces: the committed source tree Task 2's `unpack_package.py` must reproduce byte-for-byte from the zips (its acceptance test), and the English input pages Tasks 4, 5, 10 read.

Inputs found on disk (spec §13): trial-tenant package exports at `C:\Users\pavol\Documents\sap-is-pipeline` (zips + loose scripts + 13 Czech Markdown pages), CZINT exports at `C:\Users\pavol\Documents\SAP\Integration Suite\Pipeline concept` (zips, unpacked copy under `analyza/`, ChatGPT analysis), 41 Confluence PDFs in `C:\Users\pavol\Downloads\*191026-14*.pdf`.

- [ ] **Step 1: GitHub (Pavol confirms, then done here)**
  The repository has no remote. Create the private repository `iflow-forge` on GitHub (`gh repo create iflow-forge --private --source . --push`), which is also the rename decided in the brainstorm. Cloud sessions cannot start without it.
- [ ] **Step 2: Collect into `testdata/pipeline/`**
  Copy the three input locations as they are into `trial-2026-06/`, `czint-2026-09/`, `confluence-2026-10/pdf/`. Convert every PDF to text with `pypdf` (scratch install, not a repo dependency) into `confluence-2026-10/txt/<page-title>.txt`. Write `README.md` with the table "what, from where, which version, customer data yes/no".
- [ ] **Step 3: Forbidden-values list**
  From `czint-2026-09/analyza/**/parameters.prop` (right-hand sides), `Configured_Custom_Tags.txt` (IDs) and the tokens `CZINT`, `czint`: one value per line, generic values dropped (`true`, `false`, numbers, `PIP`, `/pip/...` SAP defaults that also appear in the trial export). This file is the input of `kb_check --forbidden` for the rest of the project.
- [ ] **Step 4: Stage the older sources**
  `stage.py`: for each of the four trial package zips, decode `resources.cnt` (Base64 JSON) to map `<id>_content` → artifact name and type, unpack each IFlow / ScriptCollection / ValueMapping zip into `knowledge/sources/2026-06-trial/<package-short-name>/<Bundle-SymbolicName>/`, copy `Change log` text as `<package-short-name>/CHANGELOG.txt`, skip `parameters.prop`. Package short names: `generic-inbound`, `generic-pipelines`, `generic-outbound`, `templates`.
  Verify: `find knowledge/sources -name parameters.prop | wc -l` → `0`; `grep -ril czint knowledge/sources | wc -l` → `0`.
- [ ] **Step 5: Translate the inputs to English and stage them**
  Pavol's decision: no Czech in the repository; the project language is English. Into `knowledge/inputs/en/`: `confluence-2026-10/` (the 39 generic Confluence pages, translated, named by title slug) and `analysis-2026-09.md` (the ChatGPT analysis, translated). The 13 local Markdown pages from June are older revisions of the same Confluence pages and are not translated; a page is taken from them only if Confluence lacks it. Translation rules: faithful, no additions or omissions, technical names (iFlow names, headers, properties, scripts, PD keys, queue placeholders) verbatim, Markdown structure kept, `CZINT`/`czint` → `TENANT`/`tenant`, each file starts with a provenance line (source page title, export date, generation). Then the forbidden check (`grep -F -f testdata/pipeline/forbidden-values.txt -r knowledge/inputs` → empty). The two CZINT pages stay in `testdata/` only (part 2 input, translated later with part 2). `knowledge/inputs/en/README.md` states provenance and that these are drafts, not sources of facts.
- [ ] **Step 6: Commit and push**
  `git add knowledge/sources knowledge/inputs .gitignore` then commit `Sources: SAP pipeline packages 2026-06 (trial export), Czech inputs` and push. Verify `git status --short | grep testdata` is empty before committing.
- [ ] **Step 7: Current version (Pavol, when convenient)**
  Clean catalogue download of the four packages from Discover → Integrations → `testdata/pipeline/catalog-2026-10/*.zip`; then rerun `stage.py` into `knowledge/sources/2026-10-catalog/` and commit. Until then every cloud task uses `2026-06-trial` as the primary version and the CZINT change logs for what changed since.
- [ ] **Step 8: Benchmark questions (Pavol)**
  Write ~20 questions with expected answers (message path, generic iFlow roles, which headers decide routing, what a scenario contributes for async XI → IDoc, what changes for sync, where the mapping goes with and without XSLT). Commit as `knowledge/pipeline/benchmark/questions.md` directly (no customer data in it).

---

### Task 1: Knowledge base skeleton and skill wrapper

**Files:**
- Create: `knowledge/pipeline/SKILL.md`, `knowledge/pipeline/overview.md`, `contracts.md`, `partner-directory.md`, `scenario-contribution.md`, `naming.md`, `pitfalls.md`, `glossary.md`, `iflows/.gitkeep`, `templates/.gitkeep`, `scripts/.gitkeep`, `migration/.gitkeep`, `conversion/README.md`, `versions/README.md`, `benchmark/.gitkeep`
- Create: `.claude/skills/sap-is-pipeline/SKILL.md`
- Create: `knowledge/sources/<version>/` (unpacked clean download, see Step 3)
- Modify: `.gitignore` (add `knowledge/sources/**/parameters.prop`), `README.md` (one paragraph + link to the spec)

**Interfaces:**
- Produces: the folder layout of spec §6 that every later task writes into; skill name `sap-is-pipeline`.

- [ ] **Step 1: Create the folder tree and stub pages**
  Each stub page: H1 title and one line `_Stub, filled in Task N._` with the task number from this plan. `SKILL.md` frontmatter: `name: sap-is-pipeline`, `description: SAP Integration Suite pipeline concept: generic iFlows, contracts, what a scenario contributes, migration procedures from PO ICO or point-to-point iFlow. Load when designing or generating pipeline-based iFlows.` Body: table "when you need X, read Y" over the pages (filled now, pages come later).
- [ ] **Step 2: Skill wrapper**
  `.claude/skills/sap-is-pipeline/SKILL.md` has the same frontmatter and the body: "The content lives in `knowledge/pipeline/`. Read `knowledge/pipeline/SKILL.md` first." (My call per spec §11: pointer, not symlink; Windows-safe, one copy of content.)
- [ ] **Step 3: Sources are already staged by Task 0** (`knowledge/sources/2026-06-trial/`, later `2026-10-catalog/`). Add `knowledge/sources/README.md` if Task 0 did not: version labels, package short names, what was removed.
- [ ] **Step 4: Verify the tree**
  Run: `find knowledge/sources -name parameters.prop | wc -l` → Expected: `0`.
- [ ] **Step 5: Commit**
  `git add knowledge/pipeline .claude/skills/sap-is-pipeline README.md` then commit `Knowledge base skeleton and sap-is-pipeline skill wrapper`.

---

### Task 2: `iflow_dump.py`: iFlow → step list

**Files:**
- Create: `tools/iflow_dump.py`, `tools/unpack_package.py`
- Test: `tools/tests/__init__.py`, `tools/tests/test_iflow_dump.py`, `tools/tests/fixtures.py` (builds synthetic iFlow zips in a temp dir; no real SAP content in tests)

**Interfaces:**
- Produces (module `iflow_dump`):
  - `@dataclass IFlow(id: str, name: str, version: str, participants: list[Participant], steps: list[Step], parameters: list[Param], resources: list[str])`
  - `@dataclass Participant(name: str, component_type: str, direction: str)` — from `bpmn2:messageFlow` `ifl:property` `ComponentType` and `direction` (`Sender` / `Receiver`), e.g. `JMS`, `ProcessDirect`, `XI`, `IDOC`, `SOAP`.
  - `@dataclass Step(id: str, name: str, activity_type: str, depth: int, properties: dict[str, str])` — `activity_type` from `ifl:property` `activityType` (`Script`, `Enricher`, `XSLTMapping`, `ProcessCallElement`, `ExclusiveGateway`, ...); `depth` 0 for the main process, +1 inside each subprocess.
  - `@dataclass Param(name: str, default: str | None, configured: bool)` — from `parameters.propdef` (default) and `parameters.prop` if present (configured; value is **not** stored, only the flag).
  - `load_iflow(path: Path) -> IFlow` for an iFlow zip or an unpacked folder (has `META-INF/MANIFEST.MF`).
  - `iter_iflows(path: Path) -> Iterator[IFlow]` for an iFlow zip, a package zip (nested zips), or a folder of unpacked iFlows.
  - `to_markdown(iflow: IFlow) -> str` (header line `## <id> (<name>, <version>)`, then `- participant: <name> <component_type> <direction>`, numbered steps indented by depth, `- parameter: <name> = <default> [configured]`, `- resource: <path>`) and `to_json(iflow: IFlow) -> str` (keys in the dataclass order, lists in document order, so two dumps diff cleanly).
  - CLI: `python -I tools/iflow_dump.py <path> [--json]` prints one dump per iFlow.
  - `unpack_package.py <package.zip> <out-dir>`: nested zips → `<out-dir>/<artifact-id>/`, skipping `parameters.prop`.

- [ ] **Step 1: Write failing tests**
  ```python
  class LoadIflow(unittest.TestCase):
      def test_manifest_fields(self):            # id, name, version from MANIFEST.MF
      def test_participants_with_component_type(self):  # one JMS Sender, one ProcessDirect Receiver
      def test_steps_in_sequence_flow_order(self):      # start → Script "A" → Enricher "B" → end; order A, B
      def test_subprocess_steps_have_depth_1(self):     # exception subprocess with one Script at depth 1
      def test_cycle_terminates(self):                  # B → A back-edge; each step listed once
      def test_parameters_default_and_configured(self): # propdef default kept, prop value not stored, configured=True
      def test_resources_listed(self):                  # script/x.groovy, mapping/y.xsl
  class IterIflows(unittest.TestCase):
      def test_single_zip_folder_and_package_zip_yield_same(self):  # Review Focus 1
  class Unpack(unittest.TestCase):
      def test_unpack_skips_parameters_prop(self):
  ```
  Fixtures build minimal `.iflw` XML (bpmn2 namespace, `ifl:property` key/value pairs under `extensionElements`), `MANIFEST.MF`, `parameters.propdef`/`.prop`, resource files, zipped with the real layout `src/main/resources/scenarioflows/integrationflow/<id>.iflw`.
- [ ] **Step 2: Run tests, verify they fail**
  Run: `python -I -m unittest discover -s tools/tests -v` → Expected: `ImportError`/`AttributeError` failures, none passing.
- [ ] **Step 3: Implement `tools/iflow_dump.py` and `tools/unpack_package.py`**
  Parse with `xml.etree.ElementTree`; step order by depth-first walk over `sequenceFlow` from each `startEvent`, visited set for cycles, recursing into `subProcess` children with depth+1, then appending unreached steps at the end (marked `properties["unreached"]="true"`). Package zip detection: contains `resources.cnt` or every top-level entry is a zip.
- [ ] **Step 4: Run tests, verify they pass**
  Run: `python -I -m unittest discover -s tools/tests -v` → Expected: all OK.
- [ ] **Step 5: Acceptance on the real sources**
  Run: `python -I tools/iflow_dump.py knowledge/sources/2026-06-trial/generic-pipelines | head -80` → Expected: manifest line and a numbered step list for the first generic iFlow, no traceback. Fix parser gaps the real content reveals, adding a test per gap with a synthetic fixture. The package zips themselves are not in git; `unpack_package.py` is instead checked by Pavol locally once: `python -I tools/unpack_package.py "testdata/pipeline/trial-2026-06/Cloud Integration Pipeline - Generic Pipelines.zip" /tmp/gp && diff -r /tmp/gp knowledge/sources/2026-06-trial/generic-pipelines` → Expected: no differences.
- [ ] **Step 6: Commit**
  `git add tools/iflow_dump.py tools/unpack_package.py tools/tests` then commit `tools: iflow_dump and unpack_package`.

---

### Task 3: `kb_check.py`: knowledge base verifier

**Files:**
- Create: `tools/kb_check.py`
- Test: `tools/tests/test_kb_check.py` (builds a tiny knowledge base + sources in a temp dir)

**Interfaces:**
- Consumes: `iflow_dump.iter_iflows`.
- Produces (module `kb_check`), each returning `list[str]` of failure messages (empty = pass):
  - `check_coverage(kb: Path, sources: Path)`: every iFlow id in `sources/generic` has `iflows/<id>.md`, every id in `sources/templates` has `templates/<id>.md`, every `*.groovy`/`*.xsl` resource has `scripts/<file-stem>.md`.
  - `check_headings(kb: Path)`: every `iflows/*.md` and `templates/*.md` contains exactly these H2 headings in order: `Purpose`, `Adapters`, `Steps`, `Headers and properties`, `Partner directory`, `Scripts and resources`, `Externalized parameters`, `Error handling`, `Extension points`, `Version notes`; every `scripts/*.md`: `Purpose`, `Inputs`, `Outputs`, `Gotchas`, `Used by`.
  - `check_placeholders(kb: Path)`: set of `{{tenant.*}}` tokens across `kb` equals the set listed in `naming.md` (one per bullet line, backticked). Reports extra and missing.
  - `check_names(kb: Path, sources: Path)`: every backticked token that looks like an iFlow id or a resource file name (`*.groovy`, `*.xsl`) exists in the sources.
  - `check_budget(kb: Path)`: `SKILL.md`+`overview.md`+`contracts.md`+`scenario-contribution.md` ≤ 5,000 words; any page ≤ 1,500 words. Stub pages (containing `_Stub,`) are skipped.
  - `check_forbidden(kb: Path, forbidden: Path)`: no line of `forbidden` (one value per line, ≥ 4 chars) occurs anywhere under `kb`. Review Focus 3.
  - CLI: `python -I tools/kb_check.py --kb knowledge/pipeline --sources knowledge/sources/<version> [--forbidden testdata/pipeline/forbidden-values.txt] [--skip coverage,...]`; exit 1 with messages on any failure. `--skip` lets content tasks run before coverage is complete.

- [ ] **Step 1: Write failing tests**, one `test_*` per check with a positive and a negative case (e.g. `test_placeholders_reports_missing_and_extra`, `test_forbidden_value_found`, `test_budget_skips_stubs`, `test_headings_order_matters`).
- [ ] **Step 2: Run tests, verify they fail** → Expected: `ImportError`.
- [ ] **Step 3: Implement `tools/kb_check.py`** (regexes: placeholders `\{\{tenant\.[a-z0-9_.]+\}\}`; names: backticked tokens ending in `.groovy`/`.xsl` or matching an id from `iter_iflows`).
- [ ] **Step 4: Run tests, verify they pass** → Expected: all OK.
- [ ] **Step 5: Build the forbidden-values file (local only)**
  `python -I tools/iflow_dump.py --json testdata/pipeline/tenant-export/<country> > /dev/null` is not enough (values are not stored by design); instead run: `grep -h -o '=.*' testdata/pipeline/tenant-export/**/parameters.prop | cut -c2- | sort -u > testdata/pipeline/forbidden-values.txt` (PowerShell equivalent acceptable). Review the file, drop generic values (`true`, `false`, numbers, `EO`).
- [ ] **Step 6: Run the checker on the skeleton**
  Run: `python -I tools/kb_check.py --kb knowledge/pipeline --sources knowledge/sources/<version> --forbidden testdata/pipeline/forbidden-values.txt --skip coverage,headings` → Expected: exit 0.
- [ ] **Step 7: Commit**
  `git add tools/kb_check.py tools/tests/test_kb_check.py` then commit `tools: kb_check verifier`.

---

### Task 4: Pass A, generic iFlow pages

**Files:**
- Create: `knowledge/pipeline/iflows/<id>.md`, one per iFlow in `knowledge/sources/<version>/generic`

**Interfaces:**
- Consumes: `iflow_dump` CLI output, `kb_check` headings.
- Produces: the page set that `contracts.md` (Task 7) and `overview.md` (Task 11) summarize; facts cited as `[<id>, step "<name>"]`.

- [ ] **Step 1: Dump every generic iFlow**
  Run: `python -I tools/iflow_dump.py knowledge/sources/<version>/generic > testdata/pipeline/dump-generic.md`.
- [ ] **Step 2: Write each page** with the ten H2 headings from Task 3, in this order of work: the inbound/entry iFlow first, then in message-path order. Start from the matching page in `knowledge/inputs/en/confluence-2026-10/` as a draft, then check every statement against the dump and the scripts, keep it only with a citation, and note in `pitfalls.md` where the draft and the artifact disagree. Under *Steps*: numbered list, one line per step `n. <activity type> "<name>": what it does [cite]`, subprocess steps indented. Under *Headers and properties*: two tables, `Set` (name, value or source, step) and `Read` (name, step, used for). Under *Partner directory*: every PD lookup (key pattern, value type, what happens when missing). Under *Externalized parameters*: name, default from `.propdef`, meaning, placeholder `{{tenant.<path>}}` if tenant-specific. *Extension points*: steps SAP visibly intends for customization (named "Custom", empty script hooks, routers with a disabled branch) plus "none found" if none. *Version notes*: empty until Task 10. ≤ 1,500 words each.
- [ ] **Step 3: Check after every three pages**
  Run: `python -I tools/kb_check.py --kb knowledge/pipeline --sources knowledge/sources/<version> --forbidden testdata/pipeline/forbidden-values.txt --skip coverage` → Expected: exit 0. Commit each batch: `git add knowledge/pipeline/iflows` then `KB: generic iFlow pages <ids>`.
- [ ] **Step 4: Coverage for generic**
  Run the checker without `--skip coverage`; expected failures mention only `templates/` and `scripts/`.

---

### Task 5: Pass A, template pages

**Files:**
- Create: `knowledge/pipeline/templates/<id>.md`, one per iFlow in `knowledge/sources/<version>/templates`

**Interfaces:**
- Produces: the pages that define what a scenario contributes (spec §12.1); `scenario-contribution.md` (Task 11) is derived from them.

- [ ] **Step 1: Dump templates**: `python -I tools/iflow_dump.py knowledge/sources/<version>/templates > testdata/pipeline/dump-templates.md`.
- [ ] **Step 2: Write each page** with the same ten headings. Additionally, under *Purpose*, one sentence "A scenario copies this template when: <condition on scenario attributes>", and under *Externalized parameters* mark which parameters the scenario author must set (bold) versus leave (plain).
- [ ] **Step 3: Check and commit** as in Task 4; commit `KB: template pages`.
- [ ] **Step 4: Coverage**: checker without `--skip coverage`; expected failures mention only `scripts/`.

---

### Task 6: Pass A, script pages

**Files:**
- Create: `knowledge/pipeline/scripts/<stem>.md`, one per Groovy or XSLT resource in either package

- [ ] **Step 1: List resources**: `grep -h "^- resource:" testdata/pipeline/dump-*.md | sort -u` (format from `to_markdown`).
- [ ] **Step 2: Write each page** with headings `Purpose`, `Inputs` (headers/properties/body read, with line cites), `Outputs` (headers/properties/body set), `Gotchas`, `Used by` (iFlow ids, step names). Three lines are enough for a trivial script. Identical scripts shipped in several iFlows get one page listing all users.
- [ ] **Step 3: Check and commit**: checker with no `--skip` → Expected: exit 0. Commit `KB: script pages`.

---

### Task 7: `contracts.md` and `partner-directory.md`

**Files:**
- Modify: `knowledge/pipeline/contracts.md`, `knowledge/pipeline/partner-directory.md`
- Modify: `tools/kb_check.py` (new check), `tools/tests/test_kb_check.py`

**Interfaces:**
- Produces: `check_contract_names(kb, sources)`: every header/property name found in the sources (content modifier `ifl:property` entries of type header/property; Groovy `setHeader("X"`, `getHeaders().get("X"`, `setProperty("X"`, `getProperty("X"`) appears in `contracts.md`.

- [ ] **Step 1: Write failing test** `test_contract_names_reports_missing` with a synthetic script setting `SAP_Receiver` and a `contracts.md` without it.
- [ ] **Step 2: Run, verify fail.** **Step 3: Implement the check** and add it to the CLI. **Step 4: Run, verify pass.**
- [ ] **Step 5: Write `contracts.md`**: sections *Message path* (one table: hop, from iFlow, to iFlow, transport `{{tenant.jms.queue.<x>}}` / `{{tenant.endpoint.<x>}}`), *Headers* (name, set by, read by, meaning, cite), *Exchange properties* (same), *Queues and endpoints* (placeholder, role, default name from `.propdef` as "SAP default"), *Synchronous path* (what differs). Under 2,000 words.
- [ ] **Step 6: Write `partner-directory.md`**: key scheme (PID, parameter id patterns), value types (string, binary/XSLT), per-scenario entries as a table (key pattern, who reads it, cite), behaviour when missing.
- [ ] **Step 7: Check and commit**: full checker → exit 0. `git add knowledge/pipeline/contracts.md knowledge/pipeline/partner-directory.md tools/kb_check.py tools/tests/test_kb_check.py`, commit `KB: contracts and partner directory`.

---

### Task 8: `naming.md`, `versions/README.md`, `glossary.md`

**Files:**
- Modify: `knowledge/pipeline/naming.md`, `versions/README.md`, `glossary.md`

- [ ] **Step 1: `naming.md`**: section *SAP's own naming* (how the package names iFlows, scripts, parameters, PD keys; cite), section *Tenant placeholders*: one bullet per placeholder, backticked, with meaning and example shape (never a real value), grouped `tenant.package.*`, `tenant.jms.queue.*`, `tenant.endpoint.*`, `tenant.naming.*` (prefix/suffix rules for scenario artifacts, to be filled by the profile), `tenant.deviations.*` (reserved, spec §12.2).
- [ ] **Step 2: `versions/README.md`**: current version label, what `sources/<version>/` holds, how a new SAP version is processed (spec §8, last paragraph, as numbered steps).
- [ ] **Step 3: `glossary.md`**: pipeline terms, PO terms they correspond to (ICO, receiver determination, interface determination, agreement), IS terms (partner directory, ProcessDirect, externalized parameter).
- [ ] **Step 4: Check and commit**: full checker → exit 0 (placeholder set must now match). Commit `KB: naming, versions, glossary`.

---

### Task 9: Local deviations diff (not committed)

**Files:**
- Create (untracked): `testdata/pipeline/deviations-<country>.md` per tenant export
- Modify: `knowledge/pipeline/iflows/*.md` (*Extension points* only, if the diff reveals a customization point)

- [ ] **Step 1: Dump the tenant export** (local): `python -I tools/iflow_dump.py --json testdata/pipeline/czint-2026-09/analyza > testdata/pipeline/dump-czint.json` and the clean sources the same way.
- [ ] **Step 2: Diff** step lists and participants (a 20-line script in `testdata/pipeline/` or a manual compare is fine; `to_json` output is stable-ordered for this reason). Record per iFlow: identical / steps added or removed / parameters configured. Write `deviations-<country>.md`.
- [ ] **Step 3: Feed back only structure**: where the tenant added a step at a place SAP left open, add that place to the generic page's *Extension points* described generically ("after step n, before the router"), without the tenant's step name or logic. Run the checker with `--forbidden`.
- [ ] **Step 4: Commit** only `knowledge/pipeline/iflows` changes: `KB: extension points from tenant comparison`. Verify `git status --short | grep testdata` is empty.

---

### Task 10: Pass B, chat and document claims

**Files:**
- Create (untracked): `testdata/pipeline/claims/<export>.md`, `testdata/pipeline/claims/triage.md`
- Modify: any `knowledge/pipeline/**/*.md` (enrichment), `pitfalls.md`
- Create: `knowledge/pipeline/versions/<version>/changelog.md`

- [ ] **Step 1: Extract claims**: inputs are the documents in `knowledge/inputs/en/` (Confluence pages and the 2026-09 analysis) and, locally only, the two CZINT pages in `testdata/pipeline/confluence-2026-10/txt/`. For each of the two input sets a fresh agent session with Appendix A of the spec as the prompt and the documents attached; save the output as `claims/<input-set>.md` (in the cloud: under `knowledge/inputs/claims/`, committed, since they contain no tenant values after Task 0's scrubbing; the CZINT pages' claims stay in `testdata/`). Chat transcripts are optional extras if Pavol exports the ChatGPT one; the Claude Code one is not on disk.
- [ ] **Step 2: Triage table** `claims/triage.md`: columns claim id, export, disposition ∈ {`fact→<page>`, `rationale→<page>`, `procedure→Task 11`, `pitfall`, `changelog`, `tenant→profile`, `dropped (reason)`}. Every claim gets a row.
- [ ] **Step 3: Apply**: `fact→` rows: locate the artifact, add the sentence with its cite; if the artifact disagrees, row becomes `pitfall` ("chat said X, artifact shows Y"). `rationale→` rows: add with tag `[rationale, from analysis 2026]`. `changelog` rows: entries in `versions/<version>/changelog.md` with the `[source: analysis chat, unverified against v1 artifacts]` mark and the inline "(v1: was X)" note on the affected page under *Version notes*. `tenant→profile` rows: copy to `testdata/pipeline/profile-draft-<country>.md` (input to part 2).
- [ ] **Step 4: Verify**: `grep -c "" testdata/pipeline/claims/triage.md` equals total claims + header lines; full checker → exit 0 (budget!). Commit `KB: enrichment from analysis chats, changelog <version>` (only `knowledge/pipeline`).

---

### Task 11: Pass C, procedures, decision tables, overview, SKILL.md

**Files:**
- Modify: `knowledge/pipeline/overview.md`, `scenario-contribution.md`, `SKILL.md`, `conversion/README.md`
- Create: `migration/po-ico-to-pipeline.md`, `migration/is-iflow-to-pipeline.md`, `migration/proposal-template.md`, `migration/variants.md` (only if the budget needs it)

**Interfaces:**
- Produces: the **scenario attribute set** (names and allowed values) that parts 2 to 4 will use as the scenario specification's top-level fields. Initial set (my call, revised with Pavol in Step 1): `qos ∈ {EO, EOIO, BE}`, `sender_type ∈ {XI, IDOC, SOAP, OTHER}`, `receiver_type` same, `receiver_count ∈ {1, n}`, `mapping ∈ {none, xslt-pd, mapping-iflow}`, `routing ∈ {static, conditional, dynamic}`.

- [ ] **Step 1: Interview Pavol** (one question at a time) on the attribute set and on steps of a real migration he did by hand; record decisions at the top of `scenario-contribution.md` under *Scenario attributes*.
- [ ] **Step 2: `scenario-contribution.md`**: table *What a scenario contributes*: rows = artifacts (template copies, PD entries, configuration), columns = attribute conditions, cells = required / optional / not applicable, with cite to the template page. Section *What a scenario must not touch* (generic iFlows, shared parameters). Section *Naming* referencing `{{tenant.naming.*}}`.
- [ ] **Step 3: `migration/po-ico-to-pipeline.md`**: numbered procedure: 1 read ICO JSON (po-xray `ico` output; fields used: `sender.iface`, `sender.channel`, `receivers[].qualityOfService`, `receivers[].condition`, `receivers[].interfaces[].mapping`, `channels[].adapter`, `channels[].modules`), 2 derive attributes (table ICO field → attribute), 3 fill proposal template, 4 human approval, 5 produce artifacts per `scenario-contribution.md`, 6 convert modules/mappings per `conversion/`, 7 **append to the knowledge base** (spec §8: recipe, pitfall, page note) before done.
- [ ] **Step 4: `migration/is-iflow-to-pipeline.md`**: same shape; step 1 reads an iFlow dump (`iflow_dump` output: participants → sender/receiver types, steps of type mapping → mapping kind, routers → routing).
- [ ] **Step 5: `migration/proposal-template.md`**: header block with the attribute values, then sections *Source summary*, *Target artifacts* (table from Step 2 filtered), *Partner directory entries*, *Conversions needed*, *Open questions*, *Approval* (name, date).
- [ ] **Step 6: `conversion/README.md`**: how a recipe page is structured (`Source` PO module or mapping kind, `Target` IS construct, `Steps`, `Example`, `Pitfalls`); table of standard `AF_Modules/*` → IS equivalent as far as known from the claims (Task 10 `procedure→Task 11` rows); rule "custom module or Java mapping → Groovy unless a native step exists; prefer message mapping or XSLT where the logic is structural".
- [ ] **Step 7: `overview.md`**: one page: purpose, message path with a Mermaid `sequenceDiagram` (participants = generic iFlows, arrows labelled with placeholders), list of generic iFlows with one line each linking to pages, sync vs async in one paragraph.
- [ ] **Step 8: Final `SKILL.md`**: loading table (need → page), the citation and placeholder conventions in three lines, the self-improvement rule in three lines.
- [ ] **Step 9: Check and commit**: full checker → exit 0 (budget ≤ 5,000 words for the loaded set; move branches to `variants.md` if not). Commit `KB: procedures, decision tables, overview`.

---

### Task 12: Benchmark

**Files:**
- Create: `knowledge/pipeline/benchmark/questions.md`, `benchmark/sample-ico.json`, `benchmark/expected-proposal.md`, `benchmark/results-<date>.md`

**Interfaces:**
- Produces: `sample-ico.json`, valid against the `IntegrationInventory` shape in `docs/OUTPUT-MODEL.md` (fields listed in Task 11 Step 3 present), invented names (`SND_SYS`, `RCV_SYS`, `urn:example:...`), one async XI → IDoc scenario with one conditional receiver and one `AF_Modules/MessageTransformBean` on the sender channel; later the first test input of part 4.

- [ ] **Step 1: Move the questions** from `testdata/pipeline/benchmark-questions.md` to `benchmark/questions.md`, format: numbered, `**Q:**` / `**Expected:**`.
- [ ] **Step 2: Write `sample-ico.json`** by hand per the interface above; verify with `python -I -c "import json,sys; json.load(open('knowledge/pipeline/benchmark/sample-ico.json'))"` → no error. Write `expected-proposal.md` by filling `proposal-template.md` for it.
- [ ] **Step 3: Run the benchmark**: a fresh Claude Code session in the repository with the single instruction: "Load skill sap-is-pipeline. Answer every question in knowledge/pipeline/benchmark/questions.md in a file testdata/pipeline/benchmark-answers.md. Then follow knowledge/pipeline/migration/po-ico-to-pipeline.md for knowledge/pipeline/benchmark/sample-ico.json up to and including the proposal, into testdata/pipeline/benchmark-proposal.md." No other context, no MCP.
- [ ] **Step 4: Judge**: Pavol scores answers (correct / partial / wrong) and compares the proposal with `expected-proposal.md`. Record in `benchmark/results-<date>.md`: score table and one line per wrong answer naming the page to fix.
- [ ] **Step 5: Fix pages** for every wrong answer; rerun Step 3; stop when every answer is correct or partial with a documented reason. Full checker → exit 0.
- [ ] **Step 6: Commit** `git add knowledge/pipeline/benchmark knowledge/pipeline` then `KB: benchmark and fixes`.

---

### Task 13: Done check against the spec

- [ ] **Step 1: Walk spec §9** criteria 1 to 7, each with the command or file that proves it (checker output, `versions/<version>/changelog.md`, `naming.md`, `benchmark/results-<date>.md`, triage table with no unhandled rows, `grep -ri eon knowledge/` empty).
- [ ] **Step 2: Update the spec status line** to `Status: implemented <date>` and `README.md` to mention the skill. Commit `KB: part 1 done`.
