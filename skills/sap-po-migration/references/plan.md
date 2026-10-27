# Migration plan

Phase 4 (spec §7 item 4; P-3). Entry: `references/questions.md`, "Exit", holds for the work's scope. Opt-in v2 permits independent design with `canWork`; unknown/unclassified dependencies never imply readiness. Output: private `plan.md`, using the proposal template and closest library scenario from intake. Describe the source neutrally; never recommend changing the customer's PO. Round layout: "Iterations", step 2.

## Proposal sections

What the plan writes into each of the template's sections. Sections 1–5 form one block per pipeline scenario `<k>` (an ICO-only target has one per scenario; a BPM target takes them from the process decomposition); sections 6 and 7 are written once per round, last. An ICO that becomes several scenarios lists them with the split criterion (receiver type, qos) and the `Q-<n>` that decided it (D-039).

1. **Scenario** [page: knowledge/pipeline/migration/proposal-template.md#Scenario]: the 11 attribute rows with value and source from `analysis.md`, "Attributes": the dump field, or `Q-<n>` for a value phase 3 settled; `Status: proposed`, approved by the round's approval. What a value selects: [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes].
2. **Source summary** [page: knowledge/pipeline/migration/proposal-template.md#Source summary]: sender, receivers and configuration from the ICO dump, as step 1 of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Steps] names the fields; descriptive, no assessment.
3. **Target artifacts** [page: knowledge/pipeline/migration/proposal-template.md#Target artifacts]: every row of [page: knowledge/pipeline/scenario-contribution.md#What a scenario contributes] whose condition the Scenario values meet, with that page's cite cell, checked against [page: knowledge/pipeline/scenario-contribution.md#What a scenario must not touch]. Copy ids and packages follow the profile's naming conventions or a naming answer `Q-<n>`.
4. **Partner directory entries** [page: knowledge/pipeline/migration/proposal-template.md#Partner directory entries]: the template's rows the Scenario values need, under the partner ID of [page: knowledge/pipeline/partner-directory.md#Key scheme]; the sender alias and partner ID from the `Q-<n>` that settled them (`references/questions.md`, "Order of questions", step 1) [page: knowledge/pipeline/migration/variants.md#Partner ID and sender alias]; values as `{{tenant.<path>}}` placeholders where the template has them. A key under a shared partner ID is owned by the scenario carrying the sender (sender-side keys) or the receiver (receiver-side keys) of [page: knowledge/pipeline/partner-directory.md#Keys per scenario] and listed once; the others write `shared with S-<k>` (D-039).
5. **Conversions needed** [page: knowledge/pipeline/migration/proposal-template.md#Conversions needed]: one row per module and mapping program of `analysis.md`, "Modules and mappings", with the recipe row of [page: knowledge/pipeline/conversion/README.md#Standard adapter modules] or [page: knowledge/pipeline/conversion/README.md#Mapping programs] and its status as answered in phase 3; "no recipe" stays visible.
6. **Risks and assumptions** replaces the template's [page: knowledge/pipeline/migration/proposal-template.md#Open questions]: list unresolved facts and dependencies, explicitly accepted assumptions/risks with owner, approval locator, exact scope, limitations and verification/reopening conditions. Their `Q-<n>` sources must exist. Keep unknowns visible; assumptions never become answers. The always-asked human decisions still enter the inventory. [page: skills/sap-po-migration/references/readiness.md#Assumptions, bases and risks]
7. **Approval** [page: knowledge/pipeline/migration/proposal-template.md#Approval]: see "Approval" below.

## Contract bindings

Use private `contracts.md` as supporting input ([contract workflow](contracts.md)). In Target artifacts
and Conversions needed, pin its revision, artifact/contract IDs, classification evidence, ownership and
the `preserved-external` / `proposed-internal` choice. In BPM Process data, bind the same revision to
request/response/fault contracts, input/default/API-response producers and consumers, and structural
before/after evidence. Keep source facts, supplied proposals and approved choices distinct.

Record unresolved QNames, imports, identities and timing in the affected question inventory and
Risks and assumptions. A source catalog or earlier proposal carries its provenance and status, never
inherited approval. These bindings are part of the existing whole-round approval below; no separate
catalog approval is introduced. A changed catalog basis requires a new round and approval before phase 5.

## BPM extensions

For a BPM target (spec §7 item 4), placed in the round as "Iterations", step 2, says:

1. **Process decomposition**: from the confirmed classification rows of `analysis.md` (`references/bpm.md`, "Step classification"): the numbered pipeline scenarios (each 1:1 step's ICO, and the ICO that runs before the process), the steps of orchestration outside the pipeline, and the order of both along the process flows. `<k>` here is the number `scenario-<k>.md` takes in phase 5 (spec §7 item 5).
2. **Process data**: objects recorded by `references/bpm.md`, "Orchestration outside the pipeline". No knowledge page names a target store: cite an actual `Q-<n>` decision or an explicitly accepted scoped design assumption. Otherwise open the relevant dependency; independent work can continue.
3. **Orchestration outside the pipeline**: per step, its target placement and supporting `Q-<n>` answer or scoped assumption, visibly distinguishing the two.
4. **Error paths**: the process's error boundary events and error events from the analysis; per pipeline scenario its `error_handling` value [page: knowledge/pipeline/migration/variants.md#error_handling and custom_exit]; the target of a process error path as its `Q-<n>`.
5. **Cut-over order**: the order in which scenarios and orchestration steps are switched, as the migrator decides it. A statement in it about what the pipeline needs (for example, that an entry must exist before a switch) is a target statement and cites its page, as "Cite rule" says.
6. **Test cases per scenario**: from the ICO list only, never invented: per ICO of the scenario, one case per receiver and condition (`icos[i].receivers[j].condition`) and per interface of that receiver, naming the sender interface, the condition and the expected receiver and interface; and one no-receiver case per ICO, its expected outcome by the row for its `icos[i].noReceiverBehaviour` in [page: knowledge/pipeline/migration/variants.md#No receiver found]: a value set (`error`, or another code with its `Q-<n>`), or absent (treated as error, human confirms: its `Q-<n>`).

## Cite rule

1. Every target statement, that is every statement about what the pipeline does or needs, cites `[page: knowledge/pipeline/<path>#<heading>]` or `[pattern: <id>]` (spec §12 P-3, D-030; SKILL.md, rule 1). A source statement cites its dump field or `analysis.md` row; a decision cites its `Q-<n>`.
2. An uncited target statement opens a question (source `plan round <k>, <section>`). Resolve it before approving the affected content; independent drafting continues. No missing pipeline evidence is excused by changing a topic label.
3. The knowledge module's cite check resolves every such cite in every markdown page under `skills/` and `patterns/` (D-034). The migrator's `plan.md` lies under `local/` and is checked by reading: before showing a round, open each cited page and confirm the exact H2 or H3 heading exists and states what the plan says.

## Iterations

1. Each round is a dated section `## Round <k> — <date>`, appended; an earlier round is never overwritten (spec §7 item 4; D-034).
2. **Layout of a round**, the only one: "Changes since round `<k-1>`"; the process decomposition (BPM only); one block per pipeline scenario with sections 1–5 of "Proposal sections"; the other BPM extensions (process data, orchestration outside the pipeline, error paths, cut-over order, test cases per scenario); then, once per round, section 6 "Risks and assumptions" and section 7 "Approval".
3. "Changes since round `<k-1>`": one line per change, with its reason (a migrator remark or a `Q-<n>`). Round 1 writes "first round".
4. An unsettled remark opens a scoped dependency. An answer change stales dependent bases; create a new content revision and approval, preserving the old round and its historical consent.

## Approval

1. The approval section is the last of a round. It carries the template's fields: the migrator fills Name and Date and writes `approved <date>` as the approval line, in place of the template's "approved for generation"; the skill never writes it (spec §7 item 4).
2. The approval holds for the round it sits in and every scenario block in it. An approved round is not edited: a change after approval is a new round and needs a new approval (D-034).
   The dossier records exact section/revision/digest approvals and explicit scoped supersession; preserve prior rounds and snapshots. Partial approval alone never starts phase 5. [page: skills/sap-po-migration/references/dossier.md#Approval and current state]
3. In v2, the whole round also needs a complete final-plan milestone and explicit `milestone-approve` binding all parts, connections and current inputs. A later specification/implementation/deployment milestone needs one ready final-plan ancestor covering every exact consumed revision/digest; unrelated partial plans cannot be combined. [page: skills/sap-po-migration/references/readiness.md#Readiness and approval]
4. Exit: explicit whole-round approval, plus v2 final-plan readiness when opted in. Phase 5 additionally checks its own dependencies. Record the reached phase in `local/migrations.md`. Readiness is no permission to operate a tenant.
