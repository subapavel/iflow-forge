# Pipeline v2 Template Step01 - Inbound Processing Exactly Once (com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo` 1.0.1, package templates, label 2026-06.

## Purpose

Scenario-side first step of the pipeline for an asynchronous sender. The template has no sender adapter: its participant "Sender" has no message flow, so the scenario adds one [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. The flow sets the SAP sender headers, maps the message type of the XML root to an operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] and forwards each uniqueID once to the JMS queue `{{JMS_Outgoing}}` [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Idempotent Process Call"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, adapter "JMS" "Receiver"]. The generic consumers of pipeline inbound queues are Step02 inbound processing on `{{PipelineJMSQueuePrefix}}Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"] and Step02 integrated messaging async on `{{PipelineJMSQueuePrefix}}X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"]; the template does not say which of them JMS_Outgoing names (open questions). A scenario copies this template when: an asynchronous sender needs quality of service exactly once, realised by the idempotent process call that skips a duplicate uniqueID before the JMS hand-over [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Idempotent Process Call"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Receiver | JMS | Receiver | `{{JMS_Outgoing}}`, from Send "Send" (step 1); message compression and encryption on, expiration period 30, retention threshold alerting 2, exchange properties not transferred [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, adapter "JMS" "Receiver"] |

No sender message flow: the participant "Sender" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Steps

1. Send "Send": the only step of local process "Local Integration Process to forward message to JMS queue", run by step 8; the dump lists it first [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, adapter "JMS" "Receiver"]
2. Enricher "Set SAP headers": headers SAP_Sender, SAP_SenderInterface (empty constants); SAP_MessageType = header SAP_SenderInterface
3. Enricher "Parse incoming XML": nothing configured (no header, no property, empty constant body)
4. Enricher "Set custom header attributes": header customHeaderProperties, empty expression
5. Enricher "Define Service Interface Operations": properties SAP-MT2OP-Operation1 = MessageType1, SAP-MT2OP-Operation2 = MessageType2, messageTypeFromXMLRoot = XPath `/*/local-name()`
6. Script "Map Message Type to Operation": `mapMessageTypeToOperation.groovy` from the script collection; header SAP_SenderOperation = the operation whose SAP-MT2OP- property equals messageTypeFromXMLRoot, appended to customHeaderProperties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L30]
7. Enricher "Set unique ID": property uniqueID, empty constant
8. IdempotentProcessCall "Idempotent Process Call": message ID `${property.uniqueID}`, skip on duplicate; runs the local process of step 1, then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
9. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1" straight to "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_Sender, SAP_SenderInterface | header | 2 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Set SAP headers"] |
| SAP_MessageType | header | 2 | copy of header SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Set SAP headers"] |
| customHeaderProperties | header | 4, 6 | empty expression; the script appends `SAP_SenderOperation:<operation>`, pipe-separated [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L28] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L29] |
| SAP-MT2OP-Operation1, SAP-MT2OP-Operation2 | property | 5 | example message types MessageType1, MessageType2 [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Define Service Interface Operations"] |
| messageTypeFromXMLRoot | property | 5 | local name of the payload's root element [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Define Service Interface Operations"] |
| SAP_SenderOperation | header | 6 | the property name after `SAP-MT2OP-` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L15]; null when no property matches [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L24] |
| uniqueID | property | 7 | empty constant; idempotency key of step 8 [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Idempotent Process Call"] |

### Read

Only the payload comes from outside the flow: the allowed header list is empty [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, property "allowedHeaderList"], and the headers the steps read are set in the flow itself.

| Name | Kind | Step | Used for |
|---|---|---|---|
| root element | body | 5 | messageTypeFromXMLRoot [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Define Service Interface Operations"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `mapMessageTypeToOperation.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 6 | message type to operation via the `SAP-MT2OP-` properties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L9]; referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Map Message Type to Operation"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, manifest "Require-Capability"] |
| scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw | — | the flow model; no script folder of its own [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] |

## Externalized parameters

Bold: the scenario author must set it; the template references it without a value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **JMS_Outgoing** | — | queue name of the JMS receiver, the pipeline inbound queue [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, parameter "JMS_Outgoing"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, adapter "JMS" "Receiver"] | `{{tenant.jms.queue.inbound}}` |

## Error handling

- One exception subprocess without steps: an error ends in "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Exception Subprocess"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, property "returnExceptionToSender"].
- The local process of step 1 has no exception subprocess of its own [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. What a sender sees on error depends on the sender adapter the scenario adds; not in the artifact.

## Extension points

- Sender adapter: the unconnected participant "Sender" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].
- Values left empty or as examples: SAP_Sender and SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Set SAP headers"], customHeaderProperties [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Set custom header attributes"], the SAP-MT2OP-<operation> properties (the script takes every property whose name contains the prefix [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L11]), uniqueID [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Set unique ID"].
- Step "Parse incoming XML": configured empty [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Parse incoming XML"].
- Target queue: JMS_Outgoing [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, parameter "JMS_Outgoing"].

## Version notes

2026-09 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, manifest "Bundle-Version"].
