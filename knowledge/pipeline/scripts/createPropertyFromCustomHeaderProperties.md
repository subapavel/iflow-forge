# createPropertyFromCustomHeaderProperties

## Purpose

The reverse of addCustomHeaderProperties: parses header customHeaderProperties and sets one exchange property `customHeader_<key>` per entry [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L6] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L8] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L36] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L38].
Shared script of the script collection; no Script step in the label runs it (Used by).

## Inputs

- Header customHeaderProperties, used only when it is a non-blank String [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L8] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L13].
- Split at `|`; each part goes to PipelineHelper.decodeIfBase64 [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L15] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L16]: a JSON object is merged as a map [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L17] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L18]; a part accepted by PipelineHelper.isValidKeyValueString is added as trimmed key and value [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L19] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L21] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L22] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L24].
- The parsing block, try and catch included (L11-L34), is the same code as addCustomHeaderProperties lines 16 to 39 [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L11] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L16].

## Outputs

- Property `customHeader_<key>` = the value, for every entry, overwriting a property of the same name [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L36] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L37] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L38].
- That is the only write: no header change, no MPL entry, no audit log [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L38].
- Parsing errors are caught; the map is then empty and no property is set [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L31] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L33] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L36].

## Gotchas

- Round trip: addCustomHeaderProperties reads these properties back as `customHeader_*` and lets them override header entries of the same key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L43] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L58].
- NodeList entries that addCustomHeaderProperties stored under `<key>_NdLstElmnt<i>` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L52] come back as separate String-keyed properties `customHeader_<key>_NdLstElmnt<i>`, not as a NodeList [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L38].
- JSON values keep their parsed type (number, list, map) as property value [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L18] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L38].
- A part that is neither a Base64 JSON object nor a valid `key:value` string is dropped silently [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/createPropertyFromCustomHeaderProperties.groovy#L28].

## Used by

- `com.sap.integration.cloud.pipeline.v2.script.collection`: shipped but not referenced by any step; the collection is a ScriptCollection bundle without BPMN [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "SAP-BundleType"], and a grep of every `.iflw` of the label finds no `createPropertyFromCustomHeaderProperties.groovy`.

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.script.collection`).
