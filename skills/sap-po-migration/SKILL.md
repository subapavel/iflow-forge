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
| to write the scenario specification per pipeline scenario | phase 5, `templates/scenario.md` |
| the generated package and converted code | phase 6 (placeholder) |
| to close the migration and file what was learned | phase 7, `references/retrospective.md`, `references/requests.md` |

## Two rules

1. **No pipeline claim without a cite.** Every statement about what the pipeline does or needs cites `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]`, or it is an item on the open list. A plan statement with neither reopens the question round.
2. **Nothing customer-specific outside `local/` and `testdata/`.** Customer, tenant, system, host and person names, configured values and customer files stay in those two gitignored directories. A committed file names a migration only by its code `M-<n>`.

## Phases

Each phase has an entry, an output file under `local/<customer>/scenarios/M-<n>/` and an exit. A phase starts only when the previous exit holds.

1. **Intake.** Entry: the migrator names the target (a BPM process, or ICOs); allocate the next `M-<n>` in `local/migrations.md`, read or create the profile, list inputs present and missing. Output: `intake.md`. Exit: every input row is present or marked "not needed, because …".
2. **Analysis.** Entry: intake exit holds; read `knowledge/pipeline/SKILL.md` and its loaded set, then the dumps. Output: `analysis.md`. Exit: every attribute has a value or a `?`; every `?`, touched open question, version-gated feature and recipe in `proposal` status is on the open list.
3. **Question round.** Entry: the open list from phase 2 (or a plan statement without a cite). Output: `questions.md`, append-only, one record per question, asked one at a time. Exit: the open list is empty; an item nobody can settle is closed by a recorded assumption.
4. **Plan.** Entry: the open list is empty. Output: `plan.md`, each iteration a dated section. Exit: the migrator writes `approved <date>` in the approval section.
5. **Scenario specification.** Entry: the plan is approved. Output: `scenario-<k>.md` per pipeline scenario of the plan's decomposition. Exit: every attribute has a value and every placeholder resolves against the profile.
6. **Artifacts.** Entry: the specifications exist. Output: `artifacts/`. Exit: until the generator and the code conversion exist, write "not available" into `retrospective.md` and go on to phase 7.
7. **Retrospective.** Entry: phase 6 is done, or a stop was reached. Output: `retrospective.md`. Exit: profile amended, scenario filed, requests, if any, written and on a field branch; only then is the migration done.

The flow leaves this order only at two named stops:

- **Human task stop.** A human task found in phase 2 ends the migration as out of scope: `analysis.md` records the stop and the human tasks, no plan is written, and phase 7 still runs.
- **Artifacts placeholder.** Phase 6 is a named placeholder until the generator exists; the specification and the plan are the deliverable.

## Routing

| Phase | Reference |
|---|---|
| 1 Intake | `references/intake.md`, `templates/profile.md` |
| 2 Analysis | `references/intake.md`, `references/attributes.md`, `references/bpm.md` |
| 3 Question round | `references/questions.md`, `templates/question.md` |
| 4 Plan | `references/plan.md` |
| 5 Scenario specification | `templates/scenario.md` |
| 6 Artifacts | none yet (placeholder) |
| 7 Retrospective | `references/retrospective.md`, `references/requests.md`, `templates/request.md` |

## Local files

- `local/<customer>/profile.md`: the tenant profile, read at intake, amended in the retrospective (`templates/profile.md`).
- `local/<customer>/scenarios/M-<n>/`: the phase outputs of one migration, the scenario library searched at intake.
- `local/migrations.md`: the only map from a code `M-<n>` to customer and scenario.
- `testdata/`: the customer's PO artifacts in the layout of `docs/inputs/README.md`; the profile records the input paths.
- `local/` and `testdata/` are gitignored; nothing in them is ever copied into a committed file.

## Learning

- The retrospective files each lesson as a request `requests/REQ-<n>.md` (`templates/request.md`) of one kind: `kb` (a page was wrong, silent or ambiguous), `skill` (a phase, reference or template of this skill misled or lacked), `pattern` (a generalisable way the migration found, anonymised), `tool` (po-xray or a helper could not read or derive something; evidence is a synthetic fixture, never the customer's file).
- Pattern pages under `patterns/` cite migration evidence as `[migration M-<n>, <date>]` (D-030); plan and pattern statements cite `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]`.
- Requests are committed locally on a `field/<date>` branch and reviewed in the cloud, which commits the work on the designated branch and marks each request done with the hash; the local side never commits under `knowledge/pipeline/`, `patterns/` or `src/`. A pattern draft goes under `requests/`.
- Before a push, run the knowledge module's forbidden-values check over `requests/`, `patterns/` and `skills/` (`requests/README.md`).

## Start here

- Every migration starts at phase 1 with `references/intake.md`.
- At the start of phase 2, read `knowledge/pipeline/SKILL.md` and its loaded set; every pipeline fact comes from there or from `patterns/`.
