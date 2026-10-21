# Pipeline v2 Template Step03 - Inbound Conversion (com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion` 1.0.1, package templates, label 2026-06.

## Purpose

Scenario-specific inbound conversion, called synchronously by the generic Step02 flows: Step02 inbound processing and Step02 integrated messaging async each call `${property.inbConvEndpoint}` by Request Reply [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "ReceiverConvert"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverConvert"]. That address comes from header customXInboundConversionEndpoint, else Partner Directory InboundConversionEndpoint_<SAP_SenderAlias>, else InboundConversionEndpoint of the partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L26] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L31]; the gateway "Conversion?" routes to that call only when an address was found [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 34 "Conversion?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 35 "Conversion?"]. The template listens on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, adapter "ProcessDirect" "Sender"], maps the message type of the XML root to an operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] and returns the message through message end event "End", which has no message flow [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]. A scenario copies this template when: the inbound message must be converted or enriched (format, operation from message type) before Step02 routes it, realised by the ProcessDirect entry that Step02 calls when the Partner Directory holds an inbound conversion address or the header customXInboundConversionEndpoint names one [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 34 "Conversion?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 35 "Conversion?"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, adapter "ProcessDirect" "Sender"] |

## Steps

1. Enricher "Parse XML": nothing configured (no header, no property, empty constant body)
2. Enricher "Set custom header attributes": header customHeaderProperties, empty expression
3. Enricher "Define Service Interface Operations": properties SAP-MT2OP-Operation1 = MessageType1, SAP-MT2OP-Operation2 = MessageType2, messageTypeFromXMLRoot = XPath `/*/local-name()`
4. Script "Map Message Type to Operation": `mapMessageTypeToOperation.groovy` from the script collection; header SAP_SenderOperation = the operation whose SAP-MT2OP- property equals messageTypeFromXMLRoot, appended to customHeaderProperties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L30]; then end event "End" [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
5. ErrorEventSubProcessTemplate "Exception Subprocess"
  6. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow; then "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| customHeaderProperties | header | 2, 4 | empty expression; the script appends `SAP_SenderOperation:<operation>`, pipe-separated [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L28] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L29] |
| SAP-MT2OP-Operation1, SAP-MT2OP-Operation2 | property | 3 | example message types MessageType1, MessageType2 [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Define Service Interface Operations"] |
| messageTypeFromXMLRoot | property | 3 | local name of the payload's root element [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Define Service Interface Operations"] |
| SAP_SenderOperation | header | 4 | the property name after `SAP-MT2OP-` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L15]; null when no property matches [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L24] |
| SAP_MessageProcessingLogCustomStatus | property | 6 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Set custom status"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAP_Sender(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted from the caller [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, property "allowedHeaderList"]; no step of the template reads them, except that step 4 reads customHeaderProperties after step 2 has created it [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L27] |
| root element | body | 3 | messageTypeFromXMLRoot [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Define Service Interface Operations"] |

## Partner directory

None found in the artifact. The address of a copy is registered in the Partner Directory read by Step02: InboundConversionEndpoint_<SAP_SenderAlias> or InboundConversionEndpoint, String, per partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L26] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L31]; when these and the header customXInboundConversionEndpoint are missing, inbConvBoolean is false and Step02 skips the conversion [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L39] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 34 "Conversion?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L39] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 35 "Conversion?"].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `mapMessageTypeToOperation.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 4 | message type to operation via the `SAP-MT2OP-` properties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L9]; referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Map Message Type to Operation"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, manifest "Require-Capability"] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; no script folder of its own [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

## Externalized parameters

Bold: the scenario author must set it; the template references it without a value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender, the value to register as InboundConversionEndpoint [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31] | `{{tenant.endpoint.inbound.conversion}}` |

## Error handling

- One exception subprocess: custom status RetryViaParentFlow, then "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Set custom status"]; no retry step in the template [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, property "returnExceptionToSender"].
- The callers run the conversion call inside their own error handling: Step02 async in "Exception Subprocess 3" of "Call inbound conversion" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Call inbound conversion"]; Step02 inbound calls it in the main flow [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 35 "Request Reply"], covered by its main-flow "Exception Subprocess" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 43 "Exception Subprocess"]. Whether the error of a copy reaches them is not visible in the artifact (open questions).

## Extension points

- The conversion itself: step "Parse XML" is configured empty [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Parse XML"].
- Values left empty or as examples: customHeaderProperties [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Set custom header attributes"], the SAP-MT2OP-<operation> properties (the script takes every property whose name contains the prefix [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L11]).
- Registration per sender alias or per partner ID in the Partner Directory [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26].
- Override per message by header customXInboundConversionEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L22].

## Version notes

2026-09 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, manifest "Bundle-Version"].
