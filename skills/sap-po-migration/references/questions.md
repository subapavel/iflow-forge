# Question round

Phase 3 of the flow (spec §7 item 3; spec §12 P-4). Entry: the open list of `analysis.md` from phase 2, or a plan statement without a cite, which goes onto the open list first (SKILL.md, rule 1). Output: `questions.md` in `local/<customer>/scenarios/M-<n>/`, append-only: a record is never edited or deleted once its answer is written; a correction is a new record that names the one it replaces. Records are numbered `Q-1`, `Q-2`, … per migration and continue across re-entries from phase 4. The round is neutral about the customer's PO: no question asks the migrator to change it (CLAUDE.md, reader rule).

## Order of questions

1. Before the first question, add the proposal template's always-asked rows to the open list as blocking items (D-034): the sender alias and partner ID, which every proposal asks [page: knowledge/pipeline/migration/variants.md#Partner ID and sender alias] [page: knowledge/pipeline/migration/proposal-template.md#Open questions]; and every other "human decides" or "human confirms" row of the proposal template that phase 2 did not place. Source: the page that names the row.
2. Take the open items (lines `- [ ] …` of the open list, format of `references/attributes.md`, "Open list entries") in type order: blocking (no plan without it), then version, then conversion, then naming (spec §7 item 3); within a type, in the order found (`references/attributes.md`, "Open list entries", step 3).
3. An item a later answer raises takes its place by type: a new blocking item is asked before any remaining version item.
4. What each type asks:
   - **blocking**: an attribute `?` (the report's question and candidates), a report-versus-page disagreement, a touched open question of `knowledge/pipeline/open-questions/`, an unknown runtime, a BPM step classification to confirm, an orchestration step (`references/bpm.md`), an always-asked row of step 1.
   - **version**: names the feature, its package and the row's version from [page: knowledge/pipeline/versions/features.md#Features by package], and the tenant's installed version of that package from the profile, read as [page: knowledge/pipeline/versions/features.md#The tenant's installed version] says; "unknown" in the profile is asked as such. A feature the tenant's version lacks offers two options: update the pipeline packages to the version that has the feature, or plan without the feature (spec §10); the skill never assumes the newest version.
   - **conversion**: a module or mapping with a recipe in `proposal` status, `unknown` or "no recipe", with its row in [page: knowledge/pipeline/conversion/README.md#Standard adapter modules] or [page: knowledge/pipeline/conversion/README.md#Mapping programs], placement by [page: knowledge/pipeline/conversion/README.md#Rules].
   - **naming**: a tenant name or convention the profile lacks, for a value [page: knowledge/pipeline/naming.md#Tenant placeholders] lists. The value stays under `local/`.

## One question at a time

For an agent "one at a time" (spec §7 item 3) means:

1. Pick the next item by the order above. Write its record into `questions.md` from `templates/question.md` up to and including the options.
2. Ask the migrator that one question: the item, why it is open, the options with their evidence, the proposal if any. Never two questions in one message.
3. Wait for the migrator's answer. Nothing else is asked, analysed or written in the meantime; a reply that does not answer is not an answer: ask again.
4. Record: the answer, the consequence, any new open-list items (template fields).
5. Apply the answer to `analysis.md` (D-034):
   - an attribute row gets the value and the source `Q-<n>`;
   - a BPM classification row gets its confirmed class and the rule's status `confirmed M-<n>`;
   - the settled open-list line becomes `- [x] …` with ` — closed: Q-<n>` appended; it is never deleted;
   - each new item is appended to the open list in the line format, source `Q-<n>`.
6. An answer that settles a profile value — the runtime, a `{{tenant.<path>}}` naming row, an input path, a Partner Directory convention — amends `local/<customer>/profile.md` now, not in the retrospective (D-037; [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.1 Tenant profile (part 2, minimum contract — draft)]): the value goes into its row, with source `M-<n>, Q-<n>` where the row has a source field ([page: skills/sap-po-migration/templates/profile.md#Runtime], [page: skills/sap-po-migration/templates/profile.md#Naming conventions], [page: skills/sap-po-migration/templates/profile.md#Input paths], [page: skills/sap-po-migration/templates/profile.md#Partner Directory conventions]), and the line `<yyyy-mm-dd> — M-<n> — <what changed> (Q-<n>)` is appended to [page: skills/sap-po-migration/templates/profile.md#Decisions]. A decided package update is that Decisions line only: [page: skills/sap-po-migration/templates/profile.md#Installed pipeline versions] keeps the version read from the tenant until the update is observed, and the scenario specification names its target version with the `Q-<n>`. Amend, never rewrite history; the retrospective lists these amendments (`references/retrospective.md`, "Tenant profile amendments").
7. Then the next question.

## Question record

One record per question, the fields of `templates/question.md` (spec §7 item 3):

- `# Q-<n>`; the open-list item it settles, copied verbatim, and its type;
- why the analysis could not settle it: the report's question, the missing input, or the page that leaves it to a human;
- the options, each with its evidence: a page cite `[page: knowledge/pipeline/<path>#<heading>]`, a dump field such as `icos[0].receivers[0].qualityOfService`, an `analysis.md` row, or "no evidence — migrator's call";
- the answer, in one of the two forms of "Assumptions" below or `answer: <value>`;
- the consequence: the `analysis.md` row changed (section and row) and the profile row amended (step 6), or "none";
- the new open-list items it raised, each in the open-list line format, or "none".

## Proposed answers and evidence

1. Propose an answer only when the evidence supports one, and say which evidence: e.g. a default the knowledge base states, such as `runtime` integrated-async for EO in [page: knowledge/pipeline/migration/variants.md#runtime: integrated-async or decoupled], or `qos` EO for an ALO question in [page: knowledge/pipeline/migration/variants.md#qos: ALO].
2. Mark the proposed option in the options table; with no supporting evidence, propose nothing and write "no evidence — migrator's call" (spec §7 item 3).
3. The migrator decides. A proposal is never written as the answer before the migrator states it.
4. Every statement about pipeline behaviour inside an option cites its page (SKILL.md, rule 1); one the knowledge base does not state is not offered as evidence. A pipeline fact found missing is a `kb` lesson for the retrospective (SKILL.md, "Learning").

## Assumptions

1. An item nobody can settle now is closed by an explicit assumption, never deferred (spec §12 P-4): the answer reads `assume <X>; verify at <event>`, `<event>` a named point such as the first test on the tenant or the scenario specification of phase 5.
2. The assumption is the record's answer; its consequence applies to `analysis.md` as an answer does, with the source `Q-<n>`.
3. The plan's "Risks and assumptions" section carries every assumption with its `Q-<n>` and event (`references/plan.md`; D-034).
4. "Later", "to be clarified" or an empty answer is not an assumption: ask again or write the assumption.

## Exit

1. The open list of `analysis.md` has no `- [ ]` line left: every item is closed by an answer or a recorded assumption (spec §7 item 3).
2. Every closed line names its `Q-<n>`, and every `Q-<n>` has an answer and a consequence.
3. Then phase 4, `references/plan.md`. A plan statement found without a cite later reopens this phase: add the item, continue the numbering.
