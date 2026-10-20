# Intake and analysis record

Phase 1 of the flow (spec §7 item 1) and the reading surface of phase 2 (spec §7 item 2). `po-xray` stands for `java -jar <the runner jar>` built as `docs/inputs/README.md` §1 says. Every file named here lives under `local/` or `testdata/`, both gitignored; nothing from them is copied into a committed file.

## Migration code

1. Open `local/migrations.md`, the only map from a code to customer and scenario (spec §12 P-12). Create it when absent.
2. Take the highest `M-<n>` in it; the new code is `M-<n+1>` (`M-1` in an empty file). A code is never reused, also not after a stop.
3. Append one line per migration: `M-<n> | <yyyy-mm-dd> | <customer code> | <target: BPM process name, or the ICO group> | <phase or stop>`. The customer code is the directory name under `local/` (spec §5); update the last field when a phase exit holds or a stop is reached.
4. Create `local/<customer>/scenarios/M-<n>/`; every phase output of this migration goes there (spec §7). Start `intake.md` in it with the code, the date and the target as the migrator named it.

## Tenant profile

1. Read `local/<customer>/profile.md` when it exists.
2. Else create it from `templates/profile.md` and ask the profile questions first, before any input is listed (spec §7 item 1):
   - **Input paths**: where the BPM exports, the ICO dumps and any iFlow dumps are (default: the `testdata/` layout of `docs/inputs/README.md` §2–3, spec §5).
   - **Runtime**: the runtime the tenant uses for pipeline scenarios, as the migrator states it; the values are those of `runtime` in [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes].
   - **Installed pipeline versions**: the tenant's version of each of the four packages, as [page: knowledge/pipeline/versions/features.md#The tenant's installed version] describes them.
   - **Naming conventions**: the tenant's naming for the values [page: knowledge/pipeline/naming.md#Tenant placeholders] lists.
3. An answer the migrator does not know is written as "unknown" in the profile and becomes an open-list item in phase 2: type blocking for the runtime, version for the installed versions, naming for the naming conventions; an unknown input path leaves its input row `missing`. The other profile sections are filled when a migration settles them and amended in the retrospective (SKILL.md, "Local files").
4. Record in `intake.md`: profile read or created, and which answers are "unknown".

## Inputs present and missing

Check every row of spec §5 at the profile's input paths and write the table into `intake.md`, one row each, status `present`, `missing` or `not needed, because …`.

| Input (spec §5) | How to check | Fills the gap |
|---|---|---|
| BPM process, EJB modules, Java mappings | an export directory per software component | `po-xray export <sca> -o <export-dir>` (`docs/inputs/README.md` §2); not needed for an ICO-only target |
| ICOs around the process, including the one before it | an ICO JSON per communication component | `po-xray ico -s <system> -c <component> -d both -o <ico-dir>`, online or offline from `tpz=` (`docs/inputs/README.md` §3) |
| ICOs row, when the pre-process is an iFlow | its dump | `po-xray iflow <path>` (artifact folder, zip or package export) |
| Existing point-to-point iFlows | their dumps | `po-xray iflow <path>`; not needed until part 3 exists, or when the target has none |
| Tenant profile | `local/<customer>/profile.md` | the previous section |
| Scenario library | `local/<customer>/scenarios/` | nothing to fill: an empty library is "not needed, because first migration of this customer" |
| Pipeline knowledge | `knowledge/pipeline/SKILL.md` | part of the repository; read at the start of phase 2 |
| Version gate | `knowledge/pipeline/versions/features.md` | part of the repository |
| Pattern catalogue | `patterns/` | part of the repository; empty is "not needed, because no pattern filed yet" |

For a missing input, tell the migrator which command fills it, with its arguments (spec §7 item 1). A command that fails is noted with its error text as a `tool` lesson for the retrospective; the error text stays in local `intake.md`, and the request carries a synthetic reproduction, never the customer's path or system name (SKILL.md, "Learning"). Then run the two helpers over what is present (spec §6.3):

- `po-xray attributes <ico.json>...` over every ICO JSON of the target;
- `po-xray bpm-inventory <export-dir>` over the export directory of the process (BPM target only).

Both write Markdown, `--json` gives JSON. For `attributes`, a file that cannot be read or parsed fails the command with exit 1 [page: docs/OUTPUT-MODEL.md#Migration helpers]: the input row goes back to `missing`. For `bpm-inventory`, which files are skipped and when the command fails: step 1 of `references/bpm.md`, "Step inventory". Synthetic inputs for a dry run: `skills/sap-po-migration/fixtures/README.md`.

**Exit** (spec §7 item 1): every input row of §5 is present or explicitly marked "not needed, because …".

## Scenario library search

1. Take `sender_type`, `receiver_type`, `runtime` and `mapping` of each scenario from the attributes report (spec §8.2).
2. Read the `scenario-<k>.md` files under `local/<customer>/scenarios/M-<m>/` of earlier migrations whose plan carries `approved <date>`.
3. Rank them by how many of the four values agree; a `?` in the report agrees with a library value among its candidates.
4. Write into `intake.md`: the closest library scenario by code and number (`M-<m>`, scenario `<k>`), the four values side by side, and every difference. The plan of phase 4 is offered that scenario as its template (spec §8.2). Nothing of the library entry is copied into the repository.
5. No match: write "no library scenario with these four attributes".

## Reading order of the dumps

Read in this order; each step writes into `analysis.md` (next section).

1. **ICO dump** (`icos-<component>.json`): sender, receivers, channels, mappings, as [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Inputs] and [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Steps] list the fields.
2. **Attributes report**: the 11 rows per ICO and its modules; procedure in `references/attributes.md`.
3. **BPM inventory** (BPM target): step counts, human tasks, EJB roles; procedure in `references/bpm.md`. A human task found here leads to the stop in `references/bpm.md`.
4. **iFlow dumps**: the pre-process or existing iFlows, as [page: knowledge/pipeline/migration/is-iflow-to-pipeline.md#Inputs] describes the dump.

Before step 1 of phase 2, read `knowledge/pipeline/SKILL.md` and its loaded set (SKILL.md, "Start here").

## Analysis record

`analysis.md` in `local/<customer>/scenarios/M-<n>/`, written in phase 2, neutral about the customer's PO (spec §7 item 2). Sections, in order:

1. **Source**: the process or ICO group as the source describes it: name, components, interfaces, the ICO before the process; no assessment.
2. **Attributes**: per ICO the 11 rows of the attributes report with value and source (`references/attributes.md`).
3. **Modules and mappings**: per module and mapping program the recipe and its status (`references/attributes.md`).
4. **Step inventory and classification** (BPM): the report's tables and one row per step (`references/bpm.md`).
5. **EJB roles** (BPM): beans by role and their consequence (`references/bpm.md`).
6. **Version-gated features**: feature, package, since, tenant version (`references/attributes.md`).
7. **Stop** (only when reached): the human-task report (`references/bpm.md`).
8. **Open list**: one line per item in the format of `references/attributes.md`, "Open list entries".

**Exit** (spec §7 item 2): every attribute has a value or a `?`; every `?`, every open question of `knowledge/pipeline/open-questions/` the scenario touches, every version-gated feature and every recipe in `proposal` status is on the open list.
