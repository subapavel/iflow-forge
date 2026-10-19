# readInboundConversionFromPD

## Purpose

Finds the address of the scenario's inbound conversion flow and sets properties inbConvEndpoint and inbConvBoolean [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L36] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L39].
Script step "Read inbound conversion" before the router "Conversion?" in Step02 inbound and in the local process "Execute inbound conversion" of Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 34 "Conversion?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 35 "Conversion?"].

## Inputs

- Header SAP_SenderAlias: suffix of the sender-specific key [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L15].
- Header partnerID: Partner ID of both lookups [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L16].
- Header customXInboundConversionEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22].
- Partner Directory String `InboundConversionEndpoint_<SAP_SenderAlias>` of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26].
- Partner Directory String InboundConversionEndpoint of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31]; the address `{{tenant.endpoint.inbound.conversion}}`.

## Outputs

- Property inbConvEndpoint = the endpoint found, or null [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L36].
- Property inbConvBoolean = 'true' when an endpoint was found, else 'false' [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L39].
- Header auditLogHeader: three entries through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L42] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L47] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L69].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L18].

## Gotchas

- Precedence: header customXInboundConversionEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22], then `InboundConversionEndpoint_<SAP_SenderAlias>` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26], then InboundConversionEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L30] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31].
- SAP_SenderAlias is not checked: when it is missing the key read is "InboundConversionEndpoint_null" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L15] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26].
- Nothing found: inbConvEndpoint is set to null, although the comment says it "is not created" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L35] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L36].
- Lines 50 to 59 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L50].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`: [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 33 "Read inbound conversion"].
- `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`: [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 34 "Read inbound conversion"] in [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, process "Execute inbound conversion"].

Copies: byte-identical in 2 artifacts (`com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`, `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`).
