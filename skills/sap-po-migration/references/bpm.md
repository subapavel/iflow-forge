# BPM process analysis

Phase 2 for an NW BPM process (spec §7 item 2): the step inventory, each step classified, the EJB roles, and the human-task stop. Everything goes into `analysis.md` (`references/intake.md`, "Analysis record"); the ICOs around the process are analysed by `references/attributes.md`. The analysis describes the process as the source does and stays neutral about it.

## Step inventory

1. Run `po-xray bpm-inventory <export-dir>` over the export directory of the process (the output of `po-xray export`, `docs/inputs/README.md` §2). It reads every `*.json` directly in the directory, sorted by name, or the one file given; a file that is not an SCA inventory, or has neither `bpm` nor `ejb`, is skipped with the warning `NOT_SCA_INVENTORY`; with no usable inventory the command exits 1 [page: docs/OUTPUT-MODEL.md#Migration helpers]: back to intake.
2. Copy into `analysis.md`, "Step inventory and classification" [page: docs/OUTPUT-MODEL.md#Migration helpers]:
   - the `## Processes` table: dc, process, steps, human tasks, unknown metaclasses;
   - per process the `| kind | count |` table;
   - the `## EJB roles` table: dc, role, beans; a bean with two roles is listed under both;
   - `## Totals` and `## Issues` verbatim.
3. **Human tasks** in the report are a heuristic: nodes whose metaclass contains "human", case-insensitive. The SCA reader has no human-task kind, so these nodes are kind `unknown` and their metaclass also appears under unknown metaclasses [page: docs/OUTPUT-MODEL.md#Migration helpers]. Every unknown metaclass is therefore looked at by hand in step classification.
4. The report counts; it does not list the nodes. Read the nodes one by one from the process's JSON model in the export directory: id, kind, name, scope, and the kind-specific fields [page: docs/OUTPUT-MODEL.md#Node].

A dry run on synthetic data: `po-xray bpm-inventory skills/sap-po-migration/fixtures/bpm-export` (one process with one human task, one EJB module, one file that is skipped; `skills/sap-po-migration/fixtures/README.md`).

## Step classification

Each step goes into one of four classes (spec §7 item 2): **1:1 pipeline scenario**, **orchestration outside the pipeline**, **conversion**, or **human task → stop**. Write one row per node into `analysis.md`: node id and name, kind, class, the reason (the field that decided), and the rule's status.

The node kinds are those the reader emits [page: docs/OUTPUT-MODEL.md#Node]. Every rule below is `proposal — confirm on first migration`: no migration has confirmed it yet, so the status stays visible in each row, and the classification of each process is one open-list item of type blocking ("confirm the step classification").

| kind | rule |
|---|---|
| `automatedActivity` | classify as 1:1 pipeline scenario, the ICO of the ICO dump whose interface is the activity's service interface (name and namespace); unless no ICO carries it: then an open-list item "no ICO for this call" |
| `mappingActivity` | classify as orchestration outside the pipeline (it maps process data between steps); unless its output feeds only the next `automatedActivity`'s request: then conversion, a mapping of that scenario as [page: knowledge/pipeline/conversion/README.md#Mapping programs] lists the kinds |
| `notificationActivity` | classify as orchestration outside the pipeline (an e-mail with a subject and a body); no pipeline page covers it, so it is also an open-list item |
| `exclusiveGateway` | classify as orchestration outside the pipeline; its conditions live on the outgoing flows [page: docs/OUTPUT-MODEL.md#Node], copy them as the source states them; unless the gateway only chooses between receivers of one message: then name the candidate `receiver_determination` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes] and add an open-list item |
| `joinGateway` | classify as orchestration outside the pipeline |
| `embeddedSubprocess` | classify the scope as orchestration outside the pipeline and each node inside it by this table |
| `startEvent` | classify as orchestration outside the pipeline (the start of the process); the ICO that runs before it (spec §5) is a 1:1 pipeline scenario of the decomposition |
| `endEvent`, `boundaryEvent` | classify as orchestration outside the pipeline; a boundary event is an error path of the step it is attached to when its trigger kind is `error` [page: docs/OUTPUT-MODEL.md#Node] |
| `unknown` | classify as human task when its metaclass contains "human"; unless it does not: then an open-list item with the metaclass |

A node kind the reader names as not yet seen (a parallel gateway, an intermediate event, a sub-process call) is kind `unknown` until the reader knows it [page: docs/OUTPUT-MODEL.md#Node]; the `unknown` row applies.

## EJB roles

The roles come from the export's EJB inventory, as [page: docs/OUTPUT-MODEL.md#EJB inventory] classifies them; the report lists the beans by role [page: docs/OUTPUT-MODEL.md#Migration helpers]. Write into `analysis.md`, "EJB roles", one row per bean: dc, bean, role, consequence.

| role | what the reader saw | consequence for the plan |
|---|---|---|
| `bpmFunction` | the bean calls `invokeSdo(...)` or a BPM function names it | conversion; no recipe page covers it: open-list item (conversion) |
| `adapterModule` | the bean implements the adapter module interface | conversion of a channel module: its row in [page: knowledge/pipeline/conversion/README.md#Standard adapter modules], else placement by [page: knowledge/pipeline/conversion/README.md#Rules]; open-list item (conversion) with the status |
| `xiProxyProvider` | a web service enabled for XI | no recipe: open-list item (conversion) |
| `webService` | a plain web service | no recipe: open-list item (conversion) |
| `schedulerJob` | a scheduler job implementation | no recipe: open-list item (conversion) |
| `other` | none of the above | no recipe: open-list item (conversion) |

The source of a bean is read only when the owner supplies the EJB module (spec §5).

## Human task stop

A human task ends the migration as out of scope (spec §12 P-5; SKILL.md, "Human task stop").

1. **When**: the report counts at least one human task, or step classification puts a node into the class human task.
2. **Confirm** each such node in the process's JSON model: id, name, metaclass, scope, lane.
3. **Report** in `analysis.md`, section "Stop": "Human task stop: out of scope (P-5)"; the process; each human-task node with id, name, metaclass and scope; the human-task count of the report; the step inventory and the classification as far as they were written.
4. **No plan**: phases 3 to 6 are not run and no partial plan is written (P-5). The open list stays as written.
5. Set the last field of the migration's line in `local/migrations.md` to the stop, then go to phase 7, `references/retrospective.md`; the stop is itself a learning (spec §7).

## Orchestration outside the pipeline

What phase 2 records, per step classified as orchestration outside the pipeline, in `analysis.md`:

- the node and its kind, the order of the steps along the flows, and the conditions on the outgoing flows as the source states them;
- the data the process keeps between steps (the data objects read and written by the step's mappings);
- the error paths (boundary events whose trigger kind is `error`, and the error events of activities).

Nothing is designed here: no target construct is named and no pipeline statement is made. The plan's process decomposition (spec §7 item 4) places these steps; until parts 4 and 5 exist, no generator or conversion produces them (spec §7 item 6). No pipeline page covers orchestration outside the pipeline, so each recorded step is an open-list item of type blocking.
