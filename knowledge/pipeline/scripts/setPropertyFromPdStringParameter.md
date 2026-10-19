# setPropertyFromPdStringParameter

## Purpose

Declarative Partner Directory lookup: for every exchange property `pidStringParameter_<key>` it reads String parameter `<key>` of a Partner ID and overwrites the property with the value [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L7] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L19] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L22] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L29] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L31].
Shared script of the script collection; no Script step in the label calls it (Used by).

## Inputs

- Property pidToLookup, else header partnerID: the Partner ID [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L12].
- Properties whose name starts with `pidStringParameter_` (any value, e.g. a dummy) [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L9] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L19] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L20]; the name without the prefix is the key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L22].
- Partner Directory String `<key>` of that Partner ID, per key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L28] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L29].

## Outputs

- Property `pidStringParameter_<key>` = the Partner Directory value, when it is not null [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L30] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L31].
- No header, no audit log entry and no exception: the script imports no PipelineLogger [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L1] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L3] and returns the message in every path [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L15] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L36].

## Gotchas

- Without the Partner Directory service or a Partner ID the message is returned unchanged, silently [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L14] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L15].
- A missing parameter leaves the property's earlier value in place, so a flow cannot tell a found value from its own dummy [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L30].
- pidToLookup wins only when it is truthy; an empty pidToLookup falls back to header partnerID [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L12].
- The value of each prefixed property is collected but never used [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L23] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L28]; the check at L27 tests the map twice (`setStringParametersProperties && !isEmpty()`) [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/setPropertyFromPdStringParameter.groovy#L27].

## Used by

- `com.sap.integration.cloud.pipeline.v2.script.collection`: shipped but not referenced by any step; the collection is a ScriptCollection bundle without BPMN [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "SAP-BundleType"], and no `.iflw` of the label names `setPropertyFromPdStringParameter.groovy`.

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.script.collection`).
