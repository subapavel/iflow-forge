# Retrospective

Phase 7 of the flow (spec §7 item 7; [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.4 What the retrospective writes]). Entry: phase 6 is done, or a stop was reached (SKILL.md, "Phases"). Output: `retrospective.md` in `local/<customer>/scenarios/M-<n>/`, holding what was learned, which files of §8.1–§8.4 were written, and the request ids (spec §7 item 7). Locally it amends the profile and files the scenario; everything for the cloud goes out as requests.

## What was learned

1. Read `questions.md`: every answer and every assumption `assume <X>; verify at <event>`.
2. Read the plan's "Risks and assumptions" of the approved round (`references/plan.md`, "Proposal sections", item 6).
3. Read `analysis.md` and `intake.md` for the stops, the failed commands and the disagreements noted as lessons (`references/attributes.md`, "Attributes derived from the ICOs", step 3; `references/intake.md`, "Inputs present and missing"), and the missing pipeline facts the question round noted (`references/questions.md`, "Proposed answers and evidence", step 4).
4. Add what a page, a reference, a template or the tool could not give while working.
5. Write one line per lesson: the lesson, the phase it hit, its source (`Q-<n>`, a plan section, an `analysis.md` row) and the kind it will be filed as ("Requests by kind"), or "profile" when it only changes the tenant profile.

## Tenant profile amendments

Amend `local/<customer>/profile.md` (`templates/profile.md`; [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.1 Tenant profile (part 2, minimum contract — draft)]). Amend, never rewrite history: rows of items 1–4 are updated in place; every change is recorded as a Decisions line `<date> — M-<n> — <what changed>`.

1. **Input paths**: a path that changed or was `unknown` at intake.
2. **Runtime**: on the first migration, the answer that settled it, source `M-<n>, Q-<n>`.
3. **Installed pipeline versions**: each version read during the migration, with "read from" and the date; an update available on the tenant with its version and changelog lines (spec §10).
4. **Naming conventions**: each naming answer noted in the question round (`references/questions.md`, "One question at a time", step 6), as a row with source `M-<n>, Q-<n>`.
5. **Partner Directory conventions**: the partner ID form, alternative-partner use and keys the plan settled.
6. **Decisions**: one line `<yyyy-mm-dd> — M-<n> — <decision>` per tenant-level decision of the migration, appended.
7. **Tool and skill versions**: the line `M-<n> — runner jar <git hash> — skills/sap-po-migration <git hash>`, the hashes of the commits used.

Write into `retrospective.md` which profile sections changed.

## Scenario library entry

1. `local/<customer>/scenarios/M-<n>/` holds `intake.md`, `analysis.md`, `questions.md`, `plan.md`, each `scenario-<k>.md`, `artifacts/` (or "not available" in `retrospective.md`) and `retrospective.md` [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.2 Scenario library]. Check each is there; a missing one is named in `retrospective.md`.
2. The entry is found by the library search of a later intake (`references/intake.md`, "Scenario library search") only when `plan.md` carries `approved <date>` and each `scenario-<k>.md` has `sender_type`, `receiver_type`, `runtime` and `mapping` filled. A migration that stopped before approval is not found by the search; its `analysis.md` stays as the record.
3. The last field of the migration's line in `local/migrations.md` is set at "Exit".
4. Nothing of the entry is ever copied into the repository (spec §12 P-2).

## Requests by kind

One request per lesson of "What was learned" that the cloud has to act on, filed as `requests/REQ-<n>.md` from `templates/request.md`; `REQ-<n>` is the next free number in the index of `requests/README.md`. The kinds [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.4 What the retrospective writes]:

- When a page of `knowledge/pipeline/` was wrong, silent or ambiguous, file a `kb` request. The page is not edited locally.
- When a phase, reference or template of `sap-po-migration` misled or lacked something, file a `skill` request.
- When the migration found a generalisable way that another migration can reuse, file a `pattern` request in the form of [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.3 Pattern catalogue (committed, generic)], anonymised, drafted under `requests/`.
- When po-xray or a helper could not read or derive something, file a `tool` request; its evidence is a synthetic fixture under `requests/REQ-<n>/`, never the customer's file.

Then commit the requests on a branch `field/<date>` and run the forbidden-values check before the push: procedure in `references/requests.md`, rule in [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#9. Field requests and branches (statement 12)]. The commit touches only `requests/`. Write the request ids into `retrospective.md`, or "no requests".

## After a stop or the placeholder

1. **Human task stop** (SKILL.md, "Phases"): phase 7 still runs, with `analysis.md` as the record of the migration; the stop and the human tasks are a lesson. No plan and no `scenario-<k>.md` exist, so the entry holds `intake.md`, `analysis.md` and `retrospective.md`.
2. **Artifacts placeholder**: until the generator exists, write "not available" under artifacts in `retrospective.md` (SKILL.md, "Phases", item 6).
3. **Version answer**: a version answer "update the packages first" (spec §10) is not a stop: it is recorded as a profile amendment ("Tenant profile amendments", item 3) and the migration continues.

## Exit

1. The profile is amended ("Tenant profile amendments"), the scenario is filed ("Scenario library entry"), and the requests, if any, are written and on a field branch (spec §7 item 7).
2. Set the last field of the migration's line in `local/migrations.md` to `done`, or to the stop reached (`references/intake.md`, "Migration code", step 3).
3. Only then is the migration done.
