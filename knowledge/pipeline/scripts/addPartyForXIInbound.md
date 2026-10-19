# addPartyForXIInbound

## Purpose

When the XI sender header SapSenderParty is set, rewrites header SAP_Sender as `<party>.<service>` and sets header SAP_SenderParty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L10] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L11] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L14] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L15] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L16].
Script step "Add party" in the main process of Step01 XI, after "Fetch dynamic configuration" and before "Determine partner ID" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 2 "Fetch dynamic configuration"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 3 "Add party"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 4 "Determine partner ID"]; the Content Modifier "Set SAP headers" has set SAP_Sender from header sapsenderservice before [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"].

## Inputs

- Header SapSenderParty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L10].
- Header SapSenderService [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L11].
- Step01 XI admits both through its allowedHeaderList [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, property "allowedHeaderList"].

## Outputs

- Header SAP_Sender = party + "." + service [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L15], header SAP_SenderParty = party [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L16]; both only when party is neither null nor "" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L14].
- Header auditLogHeader: two entries, "Header SAP_Sender=..." and "Header SAP_SenderParty=...", through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L20] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L24] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/PipelineLogger.groovy#L69].

## Gotchas

- Without SapSenderParty, SAP_Sender keeps the value of step 1 and SAP_SenderParty is not set [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L14] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"].
- L15 concatenates service without a null check, also when SapSenderService is absent [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L11] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L15].
- L21 and L22 apply `?: "n/a"` to the result of `.toString()`, not to the header value [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L22].
- The header names are spelt SapSenderParty and SapSenderService here [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L10] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L11], sapsenderservice in step 1 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"].
- Lines 27 to 34 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/addPartyForXIInbound.groovy#L27].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 3 "Add party"] in [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, process "Integration Process"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi`).
