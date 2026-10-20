# Scenario attributes

Phase 2 for the ICOs of a migration (spec §7 item 2): the D-025 attributes with the source field each value came from, the recipe per module and mapping, the version-gated features, and the open list. Everything goes into `analysis.md` (`references/intake.md`, "Analysis record"). What a value selects in the pipeline is never restated here: the pointer is [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes], one row per attribute.

## Attributes derived from the ICOs

1. Run `po-xray attributes <ico.json>...` over every ICO JSON listed in `intake.md`. The report has one section per ICO (`## <generatedFrom, else oid>`), the description, sender and receivers, a table `| attribute | value | source |`, then `### Questions` and `### Modules` when not empty, and `## Issues` when any [page: docs/OUTPUT-MODEL.md#Migration helpers]. Exit 1 means a file could not be read or parsed: back to intake.
2. Copy per ICO the 11 rows, in the report's order (runtime, qos, sender_type, receiver_type, receiver_count, receiver_determination, interface_determination, mapping, error_handling, custom_exit, inbound_conversion) [page: docs/OUTPUT-MODEL.md#Migration helpers], into `analysis.md`, "Attributes": value and source exactly as reported. Copy the report's issues too.
3. **A resolved row** has a value from the D-025 set and a `source`: a path into the input file, `icos[i]…` for an ICO field, `channels[k].adapter` or `channels[k].modules[m].name` for the channel a reference resolves to; `note` may say how the value was reached (e.g. "from qos BE") [page: docs/OUTPUT-MODEL.md#Migration helpers]. Check it by hand: open the field the source names in the ICO dump and compare with the rule of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]. Where the report and the page rule disagree, keep the report's value, add an open-list item (blocking) naming both, and note the disagreement as a `kb` or `tool` lesson for the retrospective.
4. **An open row** has value `?`, the `candidates` still possible (in D-025 order), a non-blank `question`, and `source` the path looked at or null [page: docs/OUTPUT-MODEL.md#Migration helpers]. Write it into "Attributes" as `? (candidates)` and add one open-list item per row with the report's question as "why open".
5. The helper never guesses: what the inventory cannot decide is `?` [page: docs/OUTPUT-MODEL.md#Migration helpers]. Never replace a `?` in phase 2; only the question round settles it (spec §7 item 3).

A dry run on synthetic data: `po-xray attributes skills/sap-po-migration/fixtures/icos-SND_SYS.json` (`skills/sap-po-migration/fixtures/README.md`).

## Attributes that need a question

Some values the helper never derives: `qos` `ALO`, `mapping` `ordered` and `receiver_determination` `custom`; `error_handling` is always `?`, and `runtime` is `?` for EO and EOIO [page: docs/OUTPUT-MODEL.md#Migration helpers]. Each of these needs a question: add the open-list item. For the other values, the row is the helper's reading of the ICO; check it by hand on the page in the right column.

| attribute | what the helper does | check by hand |
|---|---|---|
| `runtime` | BE → `integrated-sync`; EO or EOIO → `?` | `runtime` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#runtime: integrated-async or decoupled] |
| `qos` | normalises ExactlyOnce, ExactlyOnceInOrder, BestEffort to EO, EOIO, BE; ALO is never derived | `qos` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; EOIO: `qos` row of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]; ALO: [page: knowledge/pipeline/migration/variants.md#qos: ALO] |
| `receiver_determination` | `custom` is never derived; dynamic routing gives `reuse-xrd` | `receiver_determination` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#receiver_determination: reuse-xrd] |
| `interface_determination` | a receiver or assignment condition gives `xpath`; OUTPUT-MODEL states no rule for `custom` | [page: knowledge/pipeline/migration/variants.md#interface_determination: custom] |
| `mapping` | shape from targets and multiplicities, "one type" only when every target interface has the same name and namespace; `ordered` is never derived | `mapping` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#mapping: one-to-many and ordered] |
| `error_handling` | always `?` (tenant switch, D-025) | [page: knowledge/pipeline/migration/variants.md#error_handling and custom_exit] |
| `custom_exit` | channel modules that are not adapter beans → `pre`, `post` or `pre+post` [page: docs/OUTPUT-MODEL.md#Migration helpers] | the page: no ICO field, the human decides, default none [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]; [page: knowledge/pipeline/migration/variants.md#error_handling and custom_exit]; placement: [page: knowledge/pipeline/conversion/README.md#Rules]. Known disagreement (finding F-8, decision D-032): always a blocking open-list item until MIG-02b changes the helper (F-8) — write the helper's value and the page's default side by side and ask |
| `inbound_conversion` | `AF_Modules/MessageTransformBean` on the sender channel → `custom`, not an exit [page: docs/OUTPUT-MODEL.md#Migration helpers] | the page: every sender-side module but `CallSapAdapter` → the inbound conversion copy, the human decides, default custom when there is one [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes] [page: knowledge/pipeline/migration/variants.md#inbound_conversion: custom]. Known disagreement (finding F-8, decision D-032): always a blocking open-list item until MIG-02b changes the helper (F-8) — write the helper's value and the page's default side by side and ask |

The helper's rules are in [page: docs/OUTPUT-MODEL.md#Migration helpers]; the pipeline's rule for each value is on the pages in the right column. A value the helper derived from a rule the page does not state is checked as step 3 above says.

## Modules and mappings by recipe

1. **Modules.** The report's `### Modules` lists every non-adapter module with its side, channel, position and raw name [page: docs/OUTPUT-MODEL.md#Migration helpers]. For each, find its row in [page: knowledge/pipeline/conversion/README.md#Standard adapter modules]; a custom module has no row there, its placement follows [page: knowledge/pipeline/conversion/README.md#Rules].
2. **Mappings.** The attributes report does not list mapping programs. Take them from the ICO dump, the operation mappings and imported archives as step 6 of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Steps] names the fields, and find each in [page: knowledge/pipeline/conversion/README.md#Mapping programs].
3. Write into `analysis.md`, "Modules and mappings", one row per module or mapping program: name (raw), side or ICO, the recipe row, and its status cell verbatim. `proposal — confirm on first migration` stays visible as written; `unknown`, or no row at all, is written as "no recipe".
4. Every recipe in `proposal` status, every `unknown` and every "no recipe" is an open-list item of type conversion (spec §7 item 2).

## Version-gated features

1. For each pipeline feature the analysis relies on (the template or step a value selects, as [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes] cites it), find its row in [page: knowledge/pipeline/versions/features.md#Features by package].
2. Compare the row's version with the profile's installed version of that package, read as [page: knowledge/pipeline/versions/features.md#The tenant's installed version] says.
3. Write into `analysis.md`, "Version-gated features": feature, package, version of the row, tenant version, and "present", "absent" or "unknown" (the profile has no version).
4. Every such feature is an open-list item of type version (spec §7 item 2); "absent" and "unknown" say so in "why open".

## Open list entries

The open list is the last section of `analysis.md`; phase 3 settles it one item at a time (spec §7 item 3). One line per item:

`- [ ] <item> — source: <dump field, report row or page cite> — why open: <reason> — type: <blocking | version | conversion | naming>`

1. **What goes on it** (spec §7 item 2): every `?` of the attributes; every open question of `knowledge/pipeline/open-questions/` the scenario touches (search those pages for the template and step names the attribute rows cite; source = the page path); every version-gated feature; every recipe in `proposal` status; every disagreement of step 3 and every profile answer "unknown".
2. **Type.** blocking: no plan without it (an attribute `?`, a disagreement, a touched open question, an unknown runtime in the profile); version: a version-gated feature or an unknown installed version; conversion: a module or mapping recipe; naming: a tenant name or convention the profile lacks.
3. **Order**: blocking, then version, then conversion, then naming (spec §7 item 3); within a type, in the order found.
4. An item is never removed in phase 2; phase 3 closes it with an answer or a recorded assumption (spec §7 item 3).
