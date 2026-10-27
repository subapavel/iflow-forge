---
name: sap-po-migration
description: Guides a migrator from the po-xray outputs of one SAP PO integration scenario or NW BPM process, through analysis, a question round and an approved plan, to a scenario specification for the SAP Integration Suite pipeline, and files what it learned as requests. Load when migrating an SAP PO integration scenario or NW BPM process to SAP Integration Suite with the pipeline concept.
---

# sap-po-migration

The migration front end: from a customer's PO artifacts to an approved migration plan and a scenario specification on the SAP Integration Suite pipeline. It consumes the knowledge base `sap-is-pipeline`; it never reads `knowledge/sources/` and never states pipeline behaviour of its own.

## When you need …

| When you need … | Read |
|---|---|
| to start a migration, allocate its code, list the inputs | phase 1, `references/intake.md` |
| to analyse the dumps and build the open list | phase 2, `references/intake.md`, `references/attributes.md`, `references/bpm.md` |
| to settle the open list with the migrator | phase 3, `references/questions.md` |
| to write and iterate the migration plan | phase 4, `references/plan.md` |
| to inventory contracts, XML imports, mapping placement and data producers | `references/contracts.md`, `templates/contracts.md` |
| to compare implementation behavior with explicit step alignments | `references/comparison.md` (private analysis/plan evidence) |
| to track exact approval revisions and project current decisions | optional `references/dossier.md`, `templates/dossier.json` |
| to import an existing pilot into a dossier | `references/legacy-import.md` |
| to continue independent work while questions wait, or check a milestone | opt-in `references/readiness.md`, `templates/dossier-v2.json` |
| to package, review and cite local evidence, or plan a PoC | `references/evidence.md`, `templates/experiment.md` |
| to write the scenario specification per pipeline scenario | phase 5, `templates/scenario.md` |
| the generated package and converted code | phase 6 (placeholder) |
| to close the migration and file what was learned | phase 7, `references/retrospective.md`, `references/requests.md` |
| the design behind a rule a reference cites | the spec section the `[page: docs/…#…]` cite names, read by section, never whole |

## Two rules

1. **No pipeline claim without a cite.** Every statement about what the pipeline does or needs cites `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]`, or it is an item on the open list. In private `plan.md` and `scenario-<k>.md` only, a statement of the exact section revision a use names may instead cite `[evidence: <use-id>]` when the opt-in v3 dossier check reports that use `supported` (draft, D-056; `references/evidence.md`, "Citing local evidence"); `questions.md` may list such a use only as option evidence, never as the answer. A plan statement with none of these reopens the question round.
2. **Nothing customer-specific outside `local/` and `testdata/`.** Customer, tenant, system, host and person names, configured values and customer files stay in those two gitignored directories. A committed file names a migration only by its code `M-<n>`.

## Phases

Each phase has an entry, a private output file and an exit. The default sequence remains conservative; opt-in v2 readiness permits independent design work by explicit section/milestone dependencies (`references/readiness.md`). A missing classification or inventory grants no readiness. One question to the human at a time remains; waiting does not stop independent work.

1. **Intake.** Entry: the migrator names the target (a BPM process, or ICOs); allocate the next `M-<n>` in `local/migrations.md`, read or create the profile, list inputs present and missing. Output: `intake.md`. Exit: every input row is present or marked "not needed, because …".
2. **Analysis.** Entry: intake exit holds; read `knowledge/pipeline/SKILL.md` and its loaded set, then the dumps. Output: `analysis.md`. Exit: every attribute has a value or a `?`; every `?`, touched open question, absent or unknown version-gated feature and recipe in `proposal` status is on the open list.
3. **Question round.** Entry: the open list from phase 2 or an uncited plan statement. Output: append-only `questions.md`, one record per question. Actual answers amend analysis/profile promptly (`references/questions.md`, step 6). Unknowns remain unknown; explicit assumptions permit only named design work and retain verification debt. Exit: relevant dependencies are clear under `references/questions.md`, "Exit".
4. **Plan.** Entry: the question exit for the work's scope holds. Output: `plan.md`, each iteration a dated section. Exit: the migrator explicitly approves the whole round with `approved <date>`; v2 additionally binds its final-plan milestone to all required parts and connections.
5. **Scenario specification.** Entry: the whole plan is approved; v2 also requires the specification milestone ready against that exact final-plan ancestor. Output: `scenario-<k>.md` per pipeline scenario. Exit: every attribute has a value and every placeholder resolves against the profile; an unused template parameter is a `not set` row with its reason.
6. **Artifacts.** Entry: the specifications exist. Output: `artifacts/`. Exit: until the generator and the code conversion exist, write "not available" into `retrospective.md` and go on to phase 7.
7. **Retrospective.** Entry: phase 6 is done, or a stop was reached. Output: `retrospective.md`. Exit: profile amendments listed (the ones made during the migration and the retrospective's own), scenario filed, requests, if any, written and on a field branch; only then is the migration done.

Two named stops still apply:

- **Human task stop.** A human task found in phase 2 ends the migration as out of scope: `analysis.md` records the stop and the human tasks, no plan is written, and phase 7 still runs.
- **Artifacts placeholder.** Phase 6 is a named placeholder until the generator exists; the specification and the plan are the deliverable.

## Routing

| Phase | Reference |
|---|---|
| 1 Intake | `references/intake.md`, `templates/profile.md`, `templates/experiment.md` |
| 2 Analysis | `references/intake.md`, `references/attributes.md`, `references/bpm.md`, `references/contracts.md`; `references/comparison.md` for multiple implementations; `references/evidence.md`, `templates/experiment.md` |
| 3 Question round | `references/questions.md`, `templates/question.md`, `references/evidence.md` |
| 4 Plan | `references/plan.md`, `references/contracts.md`, `references/comparison.md` for supporting comparison evidence, `references/evidence.md` |
| Optional dossier alongside phases 3–5 | `references/dossier.md`; v1 history, opt-in v2 `references/readiness.md` or opt-in v3 `references/evidence.md`; whole-plan approval remains |
| 5 Scenario specification | `templates/scenario.md`, `references/contracts.md` (approved catalog bindings), `references/evidence.md` |
| 6 Artifacts | none yet (placeholder) |
| 7 Retrospective | `references/retrospective.md`, `references/requests.md`, `templates/request.md` |

## Local files

- `local/<customer>/profile.md`: the tenant profile, read at intake, amended by the question round and the retrospective, never committed (`templates/profile.md`).
- `local/<customer>/scenarios/M-<n>/`: the phase outputs of one migration, the scenario library searched at intake.
- `contracts.md` beside those outputs: private revisioned contract evidence (`templates/contracts.md`), supporting analysis, plan and specification; no separate approval.
- Optional `dossier.json`, immutable `snapshots/` and generated views stay in the private migration directory; `references/dossier.md` defines ownership, `references/readiness.md` the opt-in dependencies. Existing evidence may remain in the owner's designated external private directory.
- Evidence packets, their reviews, experiment cards (`templates/experiment.md`) and export provenance sidecars stay private beside the migration's outputs or in that external directory (`references/evidence.md`).
- `local/migrations.md`: the only map from a code `M-<n>` to customer and scenario.
- `testdata/`: the customer's PO artifacts in the layout of `docs/inputs/README.md`; the profile records the input paths.
- `local/` and `testdata/` are gitignored; nothing in them is ever copied into a committed file.

## Learning

- The retrospective files each lesson as a request `requests/REQ-<n>.md` (`templates/request.md`) of one kind: `kb` (a page was wrong, silent or ambiguous), `skill` (a phase, reference or template of this skill misled or lacked), `pattern` (a generalisable way the migration found, anonymised), `tool` (po-xray or a helper could not read or derive something; evidence is a synthetic fixture, never the customer's file).
- Pattern pages under `patterns/` cite migration evidence as `[migration M-<n>, <date>]` (D-030); plan and pattern statements cite `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]`; private plan and specification statements may also cite `[evidence: <use-id>]` as rule 1 allows, never a committed page.
- Requests are committed locally on a `field/<date>` branch and reviewed in the cloud, which commits the work on the designated branch and marks each request done with the hash; the local side never commits under `knowledge/pipeline/`, `patterns/` or `src/`. A pattern draft goes under `requests/`.
- Before a push, run the knowledge module's forbidden-values check over `requests/`, `patterns/` and `skills/` (`requests/README.md`).

## Start here

- Every migration starts at phase 1 with `references/intake.md`.
- At the start of phase 2, read `knowledge/pipeline/SKILL.md` and its loaded set; every pipeline fact comes from there, from `patterns/`, or (in plan and specification statements) from a supported local evidence use cited as rule 1 allows.
