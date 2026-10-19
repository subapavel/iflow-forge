# readKeepBulkSettingFromPD

## Purpose

Reads whether an IDoc bulk is kept together: String parameter KeepBulk of the partner ID becomes property keepBulk, default 'false' [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L25].
Script step "Read keep bulk parameter" in the main process of Step01 IDoc, on the "Bulk IDoc" branch of the router "Bulk?" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 27 "Bulk?"]; the router "Keep bulk?" then sends `${property.keepBulk} = 'true'` to "Set unique ID" and everything else to the IDoc Splitter [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 46 "Keep bulk?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 47 "IDoc Splitter"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 48 "Set unique ID"].

## Inputs

- Header partnerID: Partner ID of the lookup [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L15].
- Partner Directory String KeepBulk of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L21].

## Outputs

- Property keepBulk = the KeepBulk value, or 'false' when it is missing or empty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L25].
- Header auditLogHeader: two entries through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L28] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L29] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L30] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L32] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/PipelineLogger.groovy#L69].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L17].

## Gotchas

- L25 copies the value without trimming or case change [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L25]; the router tests `${property.keepBulk} = 'true'` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 46 "Keep bulk?"], and its default branch leads to the IDoc Splitter [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 47 "IDoc Splitter"].
- The only source is the Partner Directory; no header or iFlow parameter override is read [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L14] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L21].
- Lines 35 to 42 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readKeepBulkSettingFromPD.groovy#L35].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 45 "Read keep bulk parameter"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`).
