# Scenario attributes

Phase 2 for the ICOs of a migration (spec §7 item 2): the D-025 attributes, the recipes, the version-gated features and the open list, all into `analysis.md` (`references/intake.md`, "Analysis record"). What a value selects: [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes].

## Attributes derived from the ICOs

1. Run `po-xray attributes <ico.json>...` over every ICO JSON listed in `intake.md`. The report has one section per ICO (`## <generatedFrom, else oid>`), the description, sender and receivers, a table attribute, value, source, then `### Questions` and `### Modules` when not empty, and `## Issues` when any [page: docs/OUTPUT-MODEL.md#Migration helpers]. Exit 1: a file could not be read or parsed, back to intake.
2. Copy per ICO the 11 rows, in the report's order (runtime, qos, sender_type, receiver_type, receiver_count, receiver_determination, interface_determination, mapping, error_handling, custom_exit, inbound_conversion) [page: docs/OUTPUT-MODEL.md#Migration helpers], into `analysis.md`, "Attributes": value and source as reported, plus the report's issues.
3. **A resolved row** has a value from the D-025 set and a `source`: a path into the input file, `icos[i]…` for an ICO field, `channels[k].adapter` or `channels[k].modules[m].name` for the channel a reference resolves to; `note` may say how the value was reached [page: docs/OUTPUT-MODEL.md#Migration helpers]. Check by hand the field the source names in the ICO dump against the rule of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]. Where the report and the page rule disagree, keep the report's value, add an open-list item (blocking) naming both, and note a `kb` or `tool` lesson for the retrospective.
4. **An open row** has value `?`, the `candidates` still possible (in D-025 order), a non-blank `question`, and `source` the path looked at or null [page: docs/OUTPUT-MODEL.md#Migration helpers]. Write it into "Attributes" as `? (candidates)` and add one open-list item per row, the report's question as "why open".
5. The helper never guesses: what the inventory cannot decide is `?` [page: docs/OUTPUT-MODEL.md#Migration helpers]; only the question round replaces it (spec §7 item 3).

Dry run (synthetic): `po-xray attributes skills/sap-po-migration/fixtures/icos-SND_SYS.json` (`skills/sap-po-migration/fixtures/README.md`).

## Attributes that need a question

Some values the helper never derives: `qos` `ALO`, `mapping` `ordered` and `receiver_determination` `custom`; `error_handling` is always `?`, and `runtime` is `?` for EO and EOIO [page: docs/OUTPUT-MODEL.md#Migration helpers]; the queue of a decoupled XI or IDOC sender is no attribute (last row). Each is an open-list item; check the rest by the right column.

| attribute | what the helper does | check by hand |
|---|---|---|
| `runtime` | BE → `integrated-sync`; EO → `?` (proposed: the profile's runtime, D-039); EOIO → `?` with no proposal (the `qos` row's blocking entry); EO/EOIO with dynamic routing: the question names the decoupled condition (D-040) [page: docs/OUTPUT-MODEL.md#Migration helpers] | `runtime` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#runtime: integrated-async or decoupled] |
| `qos` | normalises ExactlyOnce, ExactlyOnceInOrder, BestEffort to EO, EOIO, BE; ALO is never derived | `qos` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; EOIO: `qos` row of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]; ALO: [page: knowledge/pipeline/migration/variants.md#qos: ALO] |
| `receiver_determination` | `custom` is never derived; dynamic routing gives `reuse-xrd`; BE with dynamic routing: `?` {custom}, `receiver_count` `n` with a note (D-040) [page: docs/OUTPUT-MODEL.md#Migration helpers] | `receiver_determination` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#receiver_determination: reuse-xrd]; `reuse-xrd` is decoupled only: EO → the runtime `?` item's 'why open' carries {custom, decoupled} (D-039); BE → the custom copy must return one receiver (Step02 sync rejects n: `receiver_count` row of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]), else a blocking entry (D-038); dynamic routing wins over receiver conditions |
| `interface_determination` | a receiver or assignment condition gives `xpath`; OUTPUT-MODEL states no rule for `custom` | [page: knowledge/pipeline/migration/variants.md#interface_determination: custom] |
| condition prefixes | an `xpath` row's note names the prefixes and the sender request message type's `xmlNamespace` from `esr`, or "ask" [page: docs/OUTPUT-MODEL.md#Migration helpers] | confirm or supply it: step 1 of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Steps]; one open-list item (type blocking) |
| `mapping` | shape from targets and multiplicities, "one type" only when every target interface has the same name and namespace; `ordered` is never derived | `mapping` row of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; [page: knowledge/pipeline/migration/variants.md#mapping: one-to-many and ordered] |
| `error_handling` | always `?` (tenant switch, D-025) | [page: knowledge/pipeline/migration/variants.md#error_handling and custom_exit] |
| `custom_exit` | non-adapter modules only (adapter beans such as `CallSapAdapter` excluded); a receiver module → `?` {none, post} with a receiver-module question (D-033); a sender module other than `AF_Modules/MessageTransformBean` → `?` {none, pre} with a sender-module question (D-032); both → all four candidates and both questions; all four also on an unresolved receiver channel [page: docs/OUTPUT-MODEL.md#Migration helpers] | receiver module → post exit or Step07 outbound conversion, sender module → pre exit or Step03 conversion, human decides, default none [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes]; [page: knowledge/pipeline/migration/variants.md#error_handling and custom_exit]; placement: [page: knowledge/pipeline/conversion/README.md#Rules]; the helper's `?` row is the open-list item (type blocking) |
| `inbound_conversion` | `AF_Modules/MessageTransformBean` on the sender channel → `custom`, not an exit; another sender module → `?` {none, custom} with the same sender-module question (D-032) [page: docs/OUTPUT-MODEL.md#Migration helpers] | every sender-side module but `CallSapAdapter` → the inbound conversion copy, the human decides, default custom when there is one [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#ICO fields to scenario attributes] [page: knowledge/pipeline/migration/variants.md#inbound_conversion: custom]; the helper's `?` row is the open-list item (type blocking) |
| queue of a decoupled XI or IDOC sender | no attribute row: not derived | default no `InboundQueue` entry: the Step01 XI or IDoc iFlow's default queue, decoupled only when its configured DefaultJMSQueueNumber is Q01 (the Step02 inbound queue); otherwise an `InboundQueue` entry names the queue, on the human's choice [page: knowledge/pipeline/migration/variants.md#runtime: integrated-async or decoupled] |

A rule the page omits: check as step 3 says.

## Modules and mappings by recipe

1. **Modules.** The report's `### Modules` lists every non-adapter module with its side, channel, position and raw name; adapter beans are no rows, the report excludes them [page: docs/OUTPUT-MODEL.md#Migration helpers]. For each, find its row in [page: knowledge/pipeline/conversion/README.md#Standard adapter modules]; a custom module has none; its placement follows [page: knowledge/pipeline/conversion/README.md#Rules].
2. **Mappings.** The attributes report lists no mapping programs: take them from the ICO dump (operation mappings, imported archives; fields: step 6 of [page: knowledge/pipeline/migration/po-ico-to-pipeline.md#Steps]) and find each in [page: knowledge/pipeline/conversion/README.md#Mapping programs].
3. Write into `analysis.md`, "Modules and mappings", one row per module or mapping program: name (raw), side or ICO, the recipe row, and its status cell verbatim; `unknown` or no row is written "no recipe".
4. Each recipe row in `proposal` status, `unknown` or "no recipe" is one open-list item of type conversion naming the modules or programs it covers, not one per module or channel (spec §7 item 2).

## Version-gated features

1. For each pipeline feature the analysis relies on (the template or step a value selects, as [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes] cites it), find its row in [page: knowledge/pipeline/versions/features.md#Features by package] or, for an artifact without a CHANGELOG entry, in [page: knowledge/pipeline/versions/present.md#Present by package].
2. Compare the row's version with the profile's installed version of that package, read as [page: knowledge/pipeline/versions/features.md#The tenant's installed version] says; a `present in` row as [page: knowledge/pipeline/versions/present.md#How to read] says.
3. Write into `analysis.md`, "Version-gated features": feature, package, row version, tenant version, and "present", "absent" or "unknown" (the profile has no version).
4. A row at or below the profile's installed version is "present" and raises no open-list item (D-041), like a `present in` row [page: knowledge/pipeline/versions/present.md#How to read]; only "absent" and "unknown" are items of type version (spec §7 item 2), saying so in "why open".

## Open list entries

The open list ends `analysis.md`, one line per item:

`- [ ] <item> — source: <dump field, report row or page cite> — why open: <reason> — type: <blocking | version | conversion | naming>`

1. **What goes on it** (spec §7 item 2): every `?` of the attributes; every open question of `knowledge/pipeline/open-questions/` the scenario touches (an entry is touched only when its title or its Unknown line names a value the scenario has: an attribute value or a `?` row's candidate, a template copy, a sender or receiver type; an iFlow id alone or a "Pass A (drafts vs artifacts)" entry is never a touch; source = the page path); every version-gated feature "absent" or "unknown"; every recipe in `proposal` status; every `xpath` row whose note names prefixes; every disagreement of step 3; every profile answer "unknown".
2. **Type.** blocking: no plan without it (an attribute `?`, a prefix note, a disagreement, a touched open question, an unknown runtime in the profile); version: an "absent" or "unknown" version-gated feature; conversion: a module or mapping recipe; naming: a tenant name or convention the profile lacks.
3. **Order**: blocking, version, conversion, naming (spec §7 item 3); within a type, in the order found; an item a candidate touches follows its `?` row and closes "not chosen, Q-<n>" when the answer is another candidate.
4. An item is never removed in phase 2; phase 3 closes it with an answer or a recorded assumption (spec §7 item 3).
