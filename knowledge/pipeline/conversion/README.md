# Conversion

How PO adapter modules and mapping programs become Integration Suite constructs in a pipeline scenario on the `2026-06` fact base; attributes per D-025, the tables per D-026.

## Purpose

A recipe says how one PO adapter module, Java mapping or mapping pattern becomes an Integration Suite construct: which construct, in which template copy. The agent reads it at step 6 of [migration/po-ico-to-pipeline.md](../migration/po-ico-to-pipeline.md) or [migration/is-iflow-to-pipeline.md](../migration/is-iflow-to-pipeline.md), one per Conversions needed row.

## Recipe pages

One page per module or pattern: `conversion/<module-or-pattern>.md` (names such as `conversion/payload-zip-bean.md`; no recipe page exists yet). H2 contract, in order:

- `## Source`: the PO module or mapping kind, with the po-xray fields that identify it: `channels[].modules[].name`, `.namespace`, `.parameters` (keys that change the recipe); for mappings `ArchiveProgram.kind` of `esr.importedArchives[].programs[]` or the type of `OperationMapping.steps[].program`.
- `## Target`: the Integration Suite construct and the template copy it sits in, named by relative link to the template page, e.g. [templates/…p2p.md](../templates/com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.md) or [templates/…step03.inbound.conversion.md](../templates/com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion.md).
- `## Steps`: numbered, one action per line, what the agent or the generator does.
- `## Example`: one module or mapping with synthetic names only (no customer token, tenant values as naming.md placeholders).
- `## Pitfalls`: what went wrong or stayed open on a migration; the lesson also goes to pitfalls.md.

Checked by the kb-reviewer, not yet by iflow-forge-knowledge.

## Rules

- A custom module or Java mapping becomes a Groovy script unless a native Integration Suite step does the same; where the logic is structural, prefer a message mapping or an XSLT mapping.
- Sender-side modules go into the inbound conversion flow (`inbound_conversion` custom): a Step03 copy, listening on `{{tenant.endpoint.inbound.conversion}}` [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, parameter "ProcessDirect_EndPoint"]; integrated-sync: the Step01 sync copy's "Call inbound conversion" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 2 "Call inbound conversion"]; or a pre-processing exit.
- Receiver-side modules go into the Step07 copy's outbound conversion call, as in the p2p template [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 7 "Call Outbound Conversion"] (whether another Step07 template has one: its templates/ page); or into the post-processing exit.
- Pre- and post-processing exits are custom exit copies, listening on ProcessDirect [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"]: pre as CustomXType CustomXPre [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 5 "Initialize pre-processing exit"], its Enabled flag read from the Partner Directory when no header sets it [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L41], post at the p2p template's CustomExit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "CustomExit"]. The template branches on header customXPipelineStep: '01' to '07' each to its own Content Modifier, any other value to the end [com.sap.integration.cloud.pipeline.v2.template.custom.exit, step 2 "Which pipeline step?"].
- Mapping programs go into the Step07 copy's mapping call, as in the p2p template [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 2 "Call Mappings"]; the one-to-many and synchronous templates: the `mapping` row of migration/po-ico-to-pipeline.md.
- Split of responsibilities, confirmed by the owner from practice: the generic Step01 sets the SAP sender and interface and finds the Partner Directory partner, with no fragile logic; Step03 does what the PO sender channel did before the runtime (sender-channel modules, format conversion such as JSON→XML); Step07 carries the main logic (mappings, outbound conversion, receiver-channel modules, XML→JSON) [owner, 2026-10-20]. In Step01 XI, SAP_Sender and SAP_SenderInterface are set [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"] and the partner ID determined [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 4 "Determine partner ID"]; the p2p template calls mappings and outbound conversion [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 2 "Call Mappings"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 7 "Call Outbound Conversion"]. This confirms the placement of the proposals below from practice; each module row remains a proposal until a migration confirms it (D-026).

## Standard adapter modules

These rows are not derived from an artifact: no artifact under `knowledge/sources` is a PO module and no triage row names one, so each row is a proposal until a migration confirms it as a recipe (D-026). Placement: Rules.

| module (AF_Modules/…) | typical use in PO | Integration Suite construct | status |
|---|---|---|---|
| `AF_Modules/MessageTransformBean` | content conversion, content type or attachment name in a channel (`Transform.Class`, `Transform.ContentType`) | CSV to XML or XML to CSV Converter for plain/XML conversion; Content Modifier header for the content type; Groovy for any other `Transform.Class` | proposal — confirm on first migration |
| `AF_Modules/DynamicConfigurationBean` | copy values between adapter-specific message attributes (dynamic configuration) and message headers | Content Modifier writing headers or exchange properties; Groovy where the value is computed | proposal — confirm on first migration |
| `AF_Modules/XMLAnonymizerBean` | remove or rewrite XML namespaces and prefixes | XSLT mapping that strips or maps the namespaces | proposal — confirm on first migration |
| `AF_Modules/PayloadZipBean` | zip or unzip the payload or attachments | ZIP Compression or ZIP Decompression step; Groovy for attachments | proposal — confirm on first migration |
| `AF_Modules/StrictXml2PlainBean` | XML to flat file (delimited or fixed length) with strict structure checks | XML to CSV Converter for delimited output; Groovy for fixed-length records | proposal — confirm on first migration |
| `AF_Modules/TextCodepageConversionBean` | convert the character encoding of a text payload | Groovy script re-encoding the body | proposal — confirm on first migration |
| `AF_Modules/PayloadSwapBean` | swap the main payload with an attachment | Groovy script moving the attachment into the body | proposal — confirm on first migration |
| `AF_Modules/MessageSplitterBean` | … | … | unknown |
| `CallSapAdapter` | the adapter call itself, not a conversion | nothing to convert | proposal — confirm on first migration |

## Mapping programs

Status rule as for modules: proposals, not derived from an artifact. Archive programs of kind `class` and `javaSource` are converted together with the Java mapping that uses them.

| ArchiveProgram.kind / mapping type | Integration Suite construct | status |
|---|---|---|
| `javaMapping` (step program type `MAP_ARCHIVE_PRG`, class in `program`; `mappingApi` AbstractTransformation or StreamTransformation) | Groovy script reimplementing the transformation; a message mapping or XSLT mapping where the logic is structural | proposal — confirm on first migration |
| `xslt` (`MAP_ARCHIVE_PRG` whose `program` is the stylesheet file; `text` holds it) | XSLT Mapping step with the stylesheet as a resource | proposal — confirm on first migration |
| message mapping (ESR `MessageMapping`, step program type `XI_TRAFO`) | Message Mapping step with the ESR mapping imported or rebuilt; user-defined functions as Groovy | proposal — confirm on first migration |
| operation mapping (`OperationMapping.steps[].kind` request, response, fault) | one mapping per step: request before the receiver call, response on the reply, fault: open | proposal — confirm on first migration |

## Adding a recipe

1. A recipe page `conversion/<module-or-pattern>.md` for every module or mapping pattern converted, with the five H2 above; the confirmed table row then names the page in its status cell.
2. A pitfall in pitfalls.md where this base was wrong or silent.
3. A note in iflows/ or contracts.md for what it showed about the pipeline.
4. One commit naming the scenario, anonymised if customer-specific (spec §8).
