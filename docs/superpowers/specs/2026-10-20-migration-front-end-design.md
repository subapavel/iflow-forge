# iflow-forge, migration front end: the skill `sap-po-migration`

Design, 2026-10-20. Status: **draft — review at hand-over** (MIG-00; owner go-ahead
`docs/inputs/2026-10-20-owner-migration-flow.md` statement 13). Every decision in §12 is marked for
the owner's review; nothing here is built yet.

**Changelog:** 2026-10-20 · MIG-01 preparation · §6.1, §13: Gemini CLI reads `.agents/skills/` as an alias of `.gemini/skills/` and agent skills are enabled by default, so one `.agents/skills/` pointer serves Codex and Gemini; the `.gemini/skills/` pointer and the experimental-flag question are dropped [external: github.com/openai/codex codex-rs/ext/skills/src/host_roots.rs] [external: github.com/google-gemini/gemini-cli docs/cli/using-agent-skills.md].

## 1. Where this sits

iflow-forge produces pipeline-based iFlows from two inputs; the spine is a **scenario specification**
that a human approves before anything is generated (`CLAUDE.md`, "What this is"). Part 1 (the
knowledge base, skill `sap-is-pipeline`) tells an agent how the pipeline behaves. This document
designs the part the migrator talks to: a second skill, `sap-po-migration`, that turns a customer's
PO artifacts into an approved scenario specification and a migration plan, and that learns from
every migration it runs. It is the front end of parts 2–5; where those parts do not exist yet, the
skill stops at the artifact that part would consume.

Inputs to this design: the owner's statements 1–13 (`docs/inputs/2026-10-20-owner-migration-flow.md`),
the handover guide (`docs/inputs/README.md`), BACKLOG rows MIG-00, SRC-00, KB-11e, the scenario
attribute set D-025, the proposal template (`knowledge/pipeline/migration/proposal-template.md`) and
the part-1 self-improvement rule (`knowledge/pipeline/SKILL.md` §Self-improvement).

## 2. Intent and why

The owner's primary target is the migration of **NW BPM processes and complex scenarios** (statement
6): SAP's own migration tool covers simple ICOs and does not use the newest pipeline. Four BPM
processes wait as the pilot. A BPM migration is not a mapping of one ICO to one pipeline scenario: the
process, its EJB modules and Java mappings, and the ICOs around it have to be understood together,
decided with the migrator, and only then specified. The owner pictures this as a brainstorming skill
(statement 7): analysis first, then every open question answered one at a time until nothing is
unclear, then a migration plan refined to approval, then artifacts. The skill must work in Claude
Code, Codex and Gemini CLI (statement 8), remember each customer across conversations (statement 9),
and grow a generic, sellable pattern catalogue (statement 10). Development continues in the cloud while
migrations run locally; what a migration finds missing must reach the cloud as a request (statement 12).

## 3. Goals

- G1 A migrator with the skill loaded and the po-xray outputs of a customer at hand reaches an
  **approved scenario specification and migration plan** for one BPM process or ICO group without
  the agent inventing pipeline behaviour: every pipeline claim in the plan cites a page of
  `knowledge/pipeline/` or a pattern; a claim with neither reopens the question round.
- G2 The **question round** ends only when the open list is empty; each question states why the
  analysis could not settle it, the options with evidence, the answer and its consequence. An item
  the migrator cannot settle is closed by an explicit assumption recorded as its answer; nothing
  open passes into the plan.
- G3 **Memory per customer**: the second scenario for a customer starts from the customer's tenant
  profile and scenario library; nothing of it is in the repository.
- G4 **Learning for the product**: every migration ends with a retrospective that proposes
  amendments to the knowledge base, the skill and the pattern catalogue in a generic, anonymised,
  cited form; the proposals go through the normal review loop before they are committed.
- G5 **Portability**: one skill directory in the Agent Skills format, loadable by Claude Code, Codex
  and Gemini CLI from thin pointer files; no agent-specific feature in the skill body.
- G6 **Field requests**: a local migration that hits a tool gap files a request the cloud session
  turns into a BACKLOG row; the loop is files and branches, no human relay.

Non-goals: generating iFlow BPMN XML (part 4; the rule "an agent never hand-writes iFlow BPMN XML"
stands); human tasks in BPM (statement 7: out of scope; the skill reports them and stops); pushing to
a tenant; migrating the PO configuration itself (the tool never recommends changing the customer's PO).

## 4. Constraints

- Java only (`CLAUDE.md`): every helper the skill calls is the po-xray runner jar or a Java tool in
  this repository; the skill body is Markdown. No Python, no shell logic beyond one-line commands.
- No customer data in the repository: customer names, system names, PIDs, hostnames, personnel IDs,
  configured values live under `testdata/` (gitignored today) and `local/` (added to `.gitignore` by
  MIG-01, §6.4). The migration code `M-<n>` is the only reference to a migration that may appear in a
  committed file.
- Citation discipline of part 1 applies to everything the retrospective proposes: a pipeline fact
  cites the artifact (D-019/D-020/D-023), an external or owner fact uses D-029, a migration
  observation cites `[migration M-<n>, <date>]` (§8.3, new form, draft).
- Page budgets of part 1 apply to pattern pages and skill references (≤ 1,500 checker words each;
  the skill's loaded set ≤ 5,000, measured as in `iflow-forge-knowledge`).
- The licence of the SAP package sources in `knowledge/sources/` is an owner-only question before any
  sale (statement 10/11; SRC-00). The skill reads pages, never `knowledge/sources/` directly, so this
  design depends on the answer only through the pages that quote SAP script and XSD text (statement
  11, SRC-00 settles that).

## 5. Inputs the skill works from

| Input | Produced by | Where (local) | What the skill reads |
|---|---|---|---|
| BPM process, EJB modules, Java mappings | `po-xray export <sca>` | `testdata/bpm/out/` (handover guide §2) | process inventory, roles (adapterModule, bpmFunction), module list; an EJB module source when the owner supplies one (statement 6) |
| ICOs around the process, including the ICO that runs before the BPM process (statement 5; when that pre-process is an iFlow, the owner supplies its `po-xray iflow` dump) | `po-xray ico -s -c -d both -o` (online or `tpz=`) | `testdata/ico/` (handover guide §3) | ICO JSON per interface: sender/receiver channels, modules, mappings, routing |
| Existing point-to-point iFlows (part 3, when it exists) | `po-xray iflow` | `testdata/iflows/` (future part-3 path, not in the handover guide yet) | the dump (participants, steps, params) |
| Tenant profile | the skill itself (§8.1) | `local/<customer>/profile.md` | runtime, naming, Partner Directory conventions, installed pipeline versions, decisions, the input paths |
| Scenario library | the skill itself (§8.2) | `local/<customer>/scenarios/M-<n>/` | approved specifications and plans of earlier migrations |
| Pipeline knowledge | part 1 | `knowledge/pipeline/` via the skill `sap-is-pipeline` | pages by the loaded-set rule |
| Version gate | KB-11e | `knowledge/pipeline/versions/features.md` | which feature exists since which package version |
| Pattern catalogue | §8.3 | `patterns/` | problem → options → trade-offs → where used |

The `testdata/` layout is the handover guide's (`docs/inputs/README.md` §2–3), where the owner's
data already is (statement 8); the profile records the input paths, so a per-customer subdirectory
is possible later without a change here (owner question §13.1). The customer code is the directory
name under `local/`, chosen by the owner locally; it never appears in a committed file.

## 6. Shape of the skill

### 6.1 Layout (Agent Skills format)

```
skills/sap-po-migration/
  SKILL.md                      frontmatter name + description; the flow in ≤ 1,500 words; routes to references
  references/
    intake.md                   what to read from the po-xray outputs, in which order; the analysis record
    attributes.md               how each D-025 attribute is derived from the inputs, and which need a question
    bpm.md                      BPM-specific analysis: process steps, EJB roles, human tasks (stop rule)
    questions.md                the question-round protocol and the question record format
    plan.md                     the migration plan template (proposal template extended for BPM)
    retrospective.md            the learning step: what it writes where, and the request format
    requests.md                 field requests and the field branch (mirrors the WORKFLOW §11 that MIG-06 writes)
  templates/
    profile.md                  tenant profile skeleton (part 2 contract, minimum)
    scenario.md                 scenario specification skeleton (D-025 attributes + proposal sections)
    question.md                 one question record
    request.md                  one REQ-<n> record
.claude/skills/sap-po-migration/SKILL.md     pointer (frontmatter + "read skills/sap-po-migration/SKILL.md")
.agents/skills/sap-po-migration/SKILL.md     same pointer, for Codex and Gemini CLI
```

Codex discovers repository skills under `.agents/skills/` [external: github.com/openai/codex
codex-rs/ext/skills/src/host_roots.rs, constants AGENTS_DIR_NAME and SKILLS_DIR_NAME]; Gemini CLI reads
the same directory as an alias of `.gemini/skills/`, with agent skills enabled by default [external:
github.com/google-gemini/gemini-cli docs/cli/using-agent-skills.md, "Discovery tiers"]. Both read only
the frontmatter `name` and `description` until the skill triggers, as Claude Code does.

The canonical skill lives at the repository root under `skills/` because it is a product, not
knowledge about the pipeline; `knowledge/pipeline/` stays the fact base of `sap-is-pipeline`. The
two pointer files follow the existing `.claude/skills/sap-is-pipeline/SKILL.md` (a frontmatter and
one sentence). A Java test (`SkillMirrorTest`, in `iflow-forge-knowledge`) asserts that the two
pointers carry the canonical frontmatter verbatim and that the skill body uses no agent-specific
syntax (no `@file` includes, no tool names, no slash commands); the same module's budget and
forbidden-values checks run over `skills/`, `patterns/` and `requests/`.

### 6.2 What the skill body says

SKILL.md states the flow (§7) as numbered phases with an exit condition each, the two rules that are
never broken (no pipeline claim without a page cite or an open question; nothing customer-specific
outside `local/` and `testdata/`), and the routing table: which reference to read in which phase.
It loads `sap-is-pipeline` by reading `knowledge/pipeline/SKILL.md` and its loaded set at the start
of phase 2; it never reads `knowledge/sources/`.

### 6.3 Helpers

All helpers are commands of the runner jar built by `docs/inputs/README.md` §1: `po-xray export`,
`po-xray ico`, `po-xray iflow`, plus two new ones designed here and built in MIG-02:
`po-xray attributes <ico.json>...` (D-025 attributes derivable from the ICOs, with the field each
value came from, and `?` where a question is needed) and `po-xray bpm-inventory <export-dir>`
(process steps by type, EJB roles, human-task count). Both print the same JSON and Markdown the
other commands do, so the skill reads a dump, never raw files.

### 6.4 Local files and gitignore

MIG-01 adds `local/` to `.gitignore` next to `testdata/`. It holds, per customer code, the tenant
profile, the scenario library and `local/migrations.md`, the only map from `M-<n>` to customer and
scenario (P-12). `requests/` and `patterns/` are committed; MIG-01 extends the knowledge module's
forbidden-values check (`-Dkb.forbidden`, a local file of the customer's identifiers, never committed)
and its budget check to `skills/`, `patterns/` and `requests/`, run before every push.

## 7. The migration flow

Each phase has an entry, an output file under `local/<customer>/scenarios/M-<n>/`, and an exit
condition. The skill never starts a phase before the previous exit holds, and leaves the sequence
only at two named stops: a **human task** found in phase 2 ends the migration as out of scope
(P-5) — `analysis.md` records the stop and phase 7 still runs, because the stop is itself a learning;
phase 6 is a **placeholder** until part 4 exists — the skill writes "not available" into
`retrospective.md` and goes on to phase 7.

1. **Intake.** The migrator names the target (a BPM process, or ICOs). The skill allocates the next
   `M-<n>` in `local/migrations.md`, reads the tenant profile if one exists (else creates it from the
   template and asks the profile questions first), lists the inputs present at the profile's input
   paths (default: the handover guide's `testdata/` layout) and the ones missing against §5, and tells the migrator which `po-xray` commands fill the gaps
   (`docs/inputs/README.md` §2–3). Output: `intake.md`. Exit: every input row of §5 is present or
   explicitly marked "not needed, because …".
2. **Analysis.** The skill reads the dumps, loads `sap-is-pipeline`, and writes `analysis.md`: the
   process or ICO group as the source describes it; for each ICO the D-025 attributes with the source
   field each value came from (helper `po-xray attributes`); for each module and mapping the matching
   `conversion/` recipe and its status (`proposal — confirm on first migration` stays visible); for a
   BPM process the step inventory (`po-xray bpm-inventory`), each step classified: 1:1 to a pipeline
   scenario, orchestration outside the pipeline, conversion, or **human task → stop** (statement 7: the
   skill reports the human tasks and ends the migration as out of scope). The analysis is neutral about
   the customer's PO (reader rule). Exit: every attribute has a value or a `?`; every `?`, every open
   question of `knowledge/pipeline/open-questions/` the scenario touches, every version-gated feature
   (`versions/features.md` against the profile's installed versions) and every recipe in `proposal`
   status is on the **open list**.
3. **Question round.** One question at a time (statement 7), in the order: blocking (no plan without
   it) → version → conversion → naming. Each question is a record (`templates/question.md`): id `Q-<n>`,
   the open-list item it settles, why the analysis could not settle it, the options with the evidence
   for each (page cite, dump field, or "no evidence — migrator's call"), the answer, the consequence,
   and any new open-list items it raised. The skill proposes an answer when the evidence supports one
   and says so; the migrator decides. An item nobody can settle now is closed by an explicit
   assumption ("assume X; verify at <event>") recorded as its answer, and the plan's risk section
   carries the assumption. Output: `questions.md` (append-only). Exit: the open list is empty.
4. **Plan.** `plan.md` from `references/plan.md`: the proposal template's sections (scenario, source
   summary, target artifacts, Partner Directory entries, conversions, approval; the template's "open
   questions" section becomes "risks and assumptions" — the assumptions recorded in phase 3, never an
   open item) extended for BPM with: process decomposition (which pipeline scenarios, which orchestration outside the
   pipeline, in which order), the data the process keeps between steps and where it lives in the
   target, the error paths, the cut-over order and the test cases per scenario (from the ICO list, not
   invented). Every target statement cites a page or a pattern in the form
   `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]` (P-3; MIG-04 adds a Java check
   that each such cite in the templates and references resolves to an existing file and heading); a
   statement with neither is an open question that reopens phase 3. The plan is iterated with the migrator; each
   round is a dated section, never an overwrite. Exit: the migrator writes `approved <date>` in the
   approval section.
5. **Scenario specification.** `scenario-<k>.md` per pipeline scenario of the plan, `<k>` the
   scenario's number in the plan's decomposition (`templates/scenario.md`):
   the D-025 attributes, the Partner Directory entries by key, the template copies and their parameters
   as `{{tenant.<path>}}` placeholders resolved from the profile, the conversions by recipe, the
   version it targets. This is the spine the part-4 generator consumes; until part 4 exists the
   phase ends here and the specification plus plan are the deliverable. Exit: every attribute of
   D-025 has a value and every placeholder resolves against the profile.
6. **Artifacts.** Part 4 (generator) consumes the `scenario-<k>.md` files and produces the importable package; part
   5 converts the modules and Java mappings per the recipes. Not designed here; the phase is a named
   placeholder so the skill's flow does not change when they arrive. Output: `artifacts/` (the
   importable package and the converted code, when part 4 and 5 exist).
7. **Retrospective.** §8. Output: `retrospective.md` (what was learned, which files §8.1–§8.4 were
   written, the request ids). Exit: the files of §8.1–§8.4 are written and the requests, if any, are
   on a field branch (§9). A migration is done only then (mirrors the part-1 self-improvement rule).

## 8. Learning

### 8.1 Tenant profile (part 2, minimum contract — draft)

`local/<customer>/profile.md`, sections: input paths (the `testdata/` directories of §5), runtime (integrated or decoupled, from the first migration's
answer or the tenant's deployed generic iFlows), installed pipeline versions per package (from
`META-INF/MANIFEST.MF` Bundle-Version of the tenant's download, KB-11e), naming conventions (iFlow,
package, Partner Directory alias, queue prefix and suffix as the tenant uses them), Partner Directory
conventions (key scheme in use, alternative-partner use), decisions with dates (one line each, with the
`M-<n>` that made it), tool and skill versions used per migration (git hash of the runner jar's
commit and of `skills/sap-po-migration/`). The profile is read at intake and amended in the
retrospective; a later part-2 design may extend it but not rename these sections.

### 8.2 Scenario library

`local/<customer>/scenarios/M-<n>/` keeps the phase outputs: `intake.md`, `analysis.md`,
`questions.md`, `plan.md`, `scenario-<k>.md`, `artifacts/`, `retrospective.md`. At intake the skill searches the
library for scenarios with the same `sender_type`, `receiver_type`, `runtime` and `mapping` and
offers the closest as the template for the plan, naming the differences. A library entry is never
copied into the repository.

### 8.3 Pattern catalogue (committed, generic)

`patterns/<id>.md`, one problem per page: the problem in pipeline terms (no customer, no system
names), the options, each with its trade-offs and the pipeline facts it rests on (page cites), where
each option was used as `[migration M-<n>, <date>]` (new cite form, draft, §12 P-3), and the open
points. `patterns/README.md` is the index and states the cite form and the rule that the code-to-
customer map lives only in `local/migrations.md`. A pattern page is born as a request (§9), written by
the kb-author in the cloud from the request's generic text, reviewed by the kb-reviewer, committed;
the local skill may draft it under `requests/` but never commits under `patterns/` itself.

### 8.4 What the retrospective writes

In the cloud's terms, requests (§9) of four kinds: `kb` (a page was wrong, silent or ambiguous — the
part-1 self-improvement rule, now filed instead of edited locally), `skill` (a phase, reference or
template of `sap-po-migration` misled or lacked), `pattern` (a generalisable way the migration found,
in the §8.3 form, anonymised), `tool` (po-xray or a helper could not read or derive something; the
evidence as a synthetic fixture the request author built, never the customer's file). Locally it
amends the profile (§8.1) and files the scenario (§8.2).

## 9. Field requests and branches (statement 12)

- `requests/REQ-<n>.md` (`templates/request.md`): kind, the migration code, what the tool or page could
  not do, the evidence as a synthetic fixture under `requests/REQ-<n>/` (never under `src/`, never the
  customer's file) or a generic statement, the expected behaviour, and the acceptance the cloud can
  test. `requests/README.md` indexes them with status open → row `<id>` → done `<hash>`.
- Before a push the local checkout runs the knowledge module with `-Dkb.forbidden=<local list>`
  over `requests/`, `patterns/` and `skills/`; a hit blocks the push. The commit touches only
  `requests/`, pattern drafts under `requests/`, and synthetic fixtures; never `src/`, never
  `knowledge/pipeline/`.
- The branch is `field/<date>`; the cloud architect reads `requests/` at session start (WORKFLOW §1
  gains this step, §11 documents the loop), turns each open request into a BACKLOG row of the matching
  kind (code: red test + implementer + reviewer; content: page packet + kb-author + kb-reviewer),
  commits on the designated branch, and marks the request done with the hash. The owner fast-forwards
  `main` (D-017), pulls locally, rebuilds the runner jar, resumes the migration; the profile records
  the new tool version.
- There is no separate "urgent fix" path: with `src/` and the pages closed to the local side, an
  urgent need is an ordinary request marked `urgent` in its header, which the cloud architect takes
  first. The cloud session fetches every `field/*` branch at session start, merges it into the
  designated branch (a merge, never a rewrite) and then reads `requests/`.
- The optional automation (statement 13: switched on when the tool is ready to test): a scheduled
  cloud session that starts when a `field/*` branch is pushed, runs the loop over the new requests, and
  stops at `IDLE_NO_READY_TASK`. Until then the owner starts the cloud session by hand.

## 10. Version gate

The profile's installed versions (§8.1) are compared in phase 2 with `versions/features.md` (KB-11e):
a feature the plan relies on that the tenant's version lacks becomes a blocking question ("update the
pipeline packages to ≥ vX, or plan without the feature"); an update available on the tenant (BACKLOG
row KB-00a, `[owner, 2026-10-20]`) is recorded in the profile with its version and the changelog lines
it brings. The skill never assumes the newest version; it asks.

## 11. Pilot and done criteria

- The pilot is the owner's first BPM process, run locally in Claude Code against
  the handover guide's `testdata/` layout; the owner follows `docs/inputs/README.md` and the skill's phases.
- MIG-00 (this design) is done when the spec is committed as draft and the implementation rows below
  are in `agent/BACKLOG.md`.
- The skill is ready to test (statement 13 trigger) when MIG-01 to MIG-06 and KB-11e are done, which
  means, testably: `SkillMirrorTest` and the two helper command tests are green on the committed
  synthetic fixtures under `skills/sap-po-migration/fixtures/` (a synthetic ICO set in the style of the
  benchmark's `sample-ico.json`, a synthetic BPM export; never under `testdata/`, which is customer
  data and gitignored); the budget, cite and forbidden-values checks cover `skills/`, `patterns/` and
  `requests/`; and the architect's **dry run** — a fresh session with the skill loaded, run like the
  benchmark, over the fixtures — reaches an empty open list and an approved synthetic plan, recorded
  in `skills/sap-po-migration/fixtures/dry-run-<date>.md` (MIG-06 acceptance). Whether the pointer
  files load in Claude Code is the owner's first local step of MIG-07 (`docs/inputs/README.md` §4
  gains the line).
- Implementation rows (one commit each, TDD where code; depends-on in brackets): MIG-01 [MIG-00]
  skill skeleton, pointer files, `SkillMirrorTest`, `local/` gitignore, `patterns/README.md`,
  `requests/README.md`, budget and forbidden-values checks over the new directories; MIG-02 [MIG-01]
  helpers `po-xray attributes` and `po-xray bpm-inventory` (red tests on the synthetic fixtures);
  MIG-03 [MIG-02, KB-11e] references intake, attributes, bpm (content, kb-reviewer); MIG-04 [MIG-03]
  question round and plan references, the question template, the page/pattern cite check (code+content); MIG-05 [MIG-04]
  retrospective, profile and scenario templates, request template; MIG-06 [MIG-05] reference
  `requests.md`, WORKFLOW §1 step and §11 field loop, D-030 confirmation, the dry run; MIG-07 [MIG-06,
  owner] pilot (owner, local) and its retrospective's requests; MIG-08 [MIG-06, KB-11e, owner] the optional
  field automation of §9 switched on as soon as the tool is ready to test (statement 13).

## 12. Decisions made in this design (all draft — review at hand-over)

- P-1 The front end is a second skill `sap-po-migration` at `skills/`, not a chapter of
  `sap-is-pipeline`; it consumes the knowledge base, never the sources. (Expensive to reverse:
  directory and skill names are public.)
- P-2 Customer memory lives in `local/<customer>/` (gitignored); the repository holds only migration
  codes `M-<n>`. (Expensive to reverse: the profile is the part-2 contract.)
- P-3 New cite forms: `[migration M-<n>, <date>]` for pattern pages, `[page: knowledge/pipeline/<path>#<heading>]`
  and `[pattern: <id>]` for plan and pattern statements; a page using them is reviewed like any
  knowledge page; D-029's limit of two external cites per section does not apply to them. (Public
  cite forms → D-030, draft.)
- P-4 The question round is one question at a time, ordered blocking → version → conversion → naming,
  with a record per question; an unsettled item is closed by a recorded assumption, never deferred.
- P-5 Human tasks end the migration as out of scope with a report; no partial plan is written.
- P-6 Learning is filed as requests and reviewed in the cloud; the local side never commits under
  `knowledge/pipeline/`, `patterns/` or `src/`.
- P-7 The two new runner commands return the same JSON + Markdown shape as the existing po-xray
  commands (the agent reads a dump, never raw files).
- P-8 The scenario specification file is the D-025 attribute set plus the proposal template's sections
  (`templates/scenario.md`); part 4 consumes exactly this file. (Expensive to reverse: public type.)
- P-9 A committed top-level `patterns/` with the page contract of §8.3 (problem, options with
  trade-offs and page cites, where used by migration code, open points; ≤ 1,500 checker words;
  reviewed by the kb-reviewer). (Expensive to reverse: public directory and page contract.)
- P-10 A committed top-level `requests/` with the REQ record of §9 and fixtures under
  `requests/REQ-<n>/`. (Expensive to reverse: the local–cloud interface.)
- P-11 The `field/<date>` branch protocol of §9 beside D-017: local commits touch only `requests/`;
  the cloud session merges every `field/*` branch at session start, never rewrites it; WORKFLOW §1
  gains the step and a new §11 documents the loop (written in MIG-06). (Branch rule; DECISIONS row
  when MIG-06 confirms it.)
- P-12 The migration code `M-<n>`, allocated locally, with `local/migrations.md` as the only map to
  customer and scenario; the same code names the scenario-library directory and the pattern cites
  (D-030). (Expensive to reverse: it is in every committed pattern page.)
- The `testdata/` layout is not changed by this design (the handover guide's layout stays; the
  profile carries the paths).

## 13. Open questions for the owner

1. Customer memory location: is a gitignored `local/<code>/` beside `testdata/` acceptable, or should
   the profile and scenario library live under `testdata/` too (one gitignored tree)?
2. Codex and Gemini CLI are served by the one `.agents/skills/` pointer; is Claude Code alone the tested
   path (statement 8), or should the owner try one of the other two once on the pilot?
3. The order of the four BPM pilots, and whether the first one has human tasks (P-5 would stop it).
4. The SAP-sources licence (SRC-00) before any sale — unchanged, owner-only.
5. Whether `patterns/` pages may name SAP product features by their documented names (external cites
   D-029) when no artifact in `knowledge/sources/` shows them.

## 14. Left to the builder

The exact wording of the references and templates; the JSON field names of the two helpers (follow
`docs/OUTPUT-MODEL.md`); the search heuristic of §8.2 beyond the four attributes; the request
numbering when two field branches are open at once (propose: `REQ-<date>-<n>`).
