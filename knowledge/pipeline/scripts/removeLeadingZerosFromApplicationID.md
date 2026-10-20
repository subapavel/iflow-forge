# removeLeadingZerosFromApplicationID

## Purpose

Removes the leading zeros from header SAP_ApplicationID [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L5] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L8].
Script step "Remove leading zeros" in the main process of Step01 IDoc, reached from both "Set unique ID" steps, which copy SAP_IDoc_EDIDC_DOCNUM (single IDoc) or sapidoctransferid (kept bulk) into SAP_ApplicationID [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 29 "Set unique ID"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 48 "Set unique ID"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 30 "Remove leading zeros"].

## Inputs

- Header SAP_ApplicationID [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L5].

## Outputs

- Header SAP_ApplicationID = the value after `replaceFirst("^0+", "")`, only when the header is not null [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L7] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L8].
- The script throws no exception of its own and writes no audit log entry; it imports only Message [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L1].

## Gotchas

- Header absent: nothing is set [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L7].
- `^0+` matches every leading zero, so a value of zeros only becomes "" [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/removeLeadingZerosFromApplicationID.groovy#L8].
- The collection copy is referenced by no template (grep of every template `.iflw`); Step01 IDoc runs its own copy (Used by).
- Leading zeros are removed so that monitoring can be searched by the IDoc number without zeros [rationale, from analysis 2026].

## Used by

- `com.sap.integration.cloud.pipeline.v2.script.collection`: shipped but not referenced by any step; the collection is a ScriptCollection bundle without BPMN [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "SAP-BundleType"], and a grep of every template `.iflw` of the label finds no `removeLeadingZerosFromApplicationID.groovy`.
- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 30 "Remove leading zeros"] in [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, process "Integration Process"].

Copies: byte-identical in 2 artifacts (`com.sap.integration.cloud.pipeline.v2.script.collection`, `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`).
