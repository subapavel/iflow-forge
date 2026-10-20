# Pipeline v2 Template Step01 - Inbound Processing At Least Once (com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-side first step of the pipeline for an asynchronous sender, without duplicate check. The template has no sender adapter: its participant "Sender" has no message flow, so the scenario adds one [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. The flow sets the SAP sender headers, maps the message type of the XML root to an operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] and its end event "End" writes the message to the JMS queue `{{JMS_Outgoing}}` [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, adapter "JMS" "Receiver"]. The generic consumers of pipeline inbound queues are Step02 inbound processing on `{{PipelineJMSQueuePrefix}}Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"] and Step02 integrated messaging async on `{{PipelineJMSQueuePrefix}}X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"]; the template does not say which of them JMS_Outgoing names (open questions). It differs from the exactly-once template by the missing "Set unique ID" and "Idempotent Process Call" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step 7 "Set unique ID"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step 8 "Idempotent Process Call"]. A scenario copies this template when: an asynchronous sender needs quality of service at least once, realised by a direct JMS hand-over from the end event with no idempotent call [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, adapter "JMS" "Receiver"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Receiver | JMS | Receiver | `{{JMS_Outgoing}}`, from end event "End"; message compression and encryption on, expiration period 30, retention threshold alerting 2, exchange properties not transferred [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, adapter "JMS" "Receiver"] |

No sender message flow: the participant "Sender" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Steps

1. Enricher "Set SAP headers": headers SAP_Sender, SAP_SenderInterface (empty constants); SAP_MessageType = header SAP_SenderInterface
2. Enricher "Parse incoming XML": nothing configured (no header, no property, empty constant body)
3. Enricher "Set custom header attributes": header customHeaderProperties, empty expression
4. Enricher "Define Service Interface Operations": properties SAP-MT2OP-Operation1 = MessageType1, SAP-MT2OP-Operation2 = MessageType2, messageTypeFromXMLRoot = XPath `/*/local-name()`
5. Script "Map Message Type to Operation": `mapMessageTypeToOperation.groovy` from the script collection; header SAP_SenderOperation = the operation whose SAP-MT2OP- property equals messageTypeFromXMLRoot, appended to customHeaderProperties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L18] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L30]; then end event "End" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, adapter "JMS" "Receiver"]
6. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1" straight to "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_Sender, SAP_SenderInterface | header | 1 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Set SAP headers"] |
| SAP_MessageType | header | 1 | copy of header SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Set SAP headers"] |
| customHeaderProperties | header | 3, 5 | empty expression; the script appends `SAP_SenderOperation:<operation>`, pipe-separated [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L28] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L29] |
| SAP-MT2OP-Operation1, SAP-MT2OP-Operation2 | property | 4 | example message types MessageType1, MessageType2 [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Define Service Interface Operations"] |
| messageTypeFromXMLRoot | property | 4 | local name of the payload's root element [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Define Service Interface Operations"] |
| SAP_SenderOperation | header | 5 | the property name after `SAP-MT2OP-` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L15]; null when no property matches [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L24] |

### Read

Only the payload comes from outside the flow: the allowed header list is empty [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, property "allowedHeaderList"], and the headers the steps read are set in the flow itself.

| Name | Kind | Step | Used for |
|---|---|---|---|
| root element | body | 4 | messageTypeFromXMLRoot [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Define Service Interface Operations"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `mapMessageTypeToOperation.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 5 | message type to operation via the `SAP-MT2OP-` properties [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L9]; referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Map Message Type to Operation"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, manifest "Require-Capability"] |
| scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw | — | the flow model; no script folder of its own [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] |

## Externalized parameters

Bold: the scenario author must set it; the template references it without a value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **JMS_Outgoing** | — | queue name of the JMS receiver, the pipeline inbound queue [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, parameter "JMS_Outgoing"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, adapter "JMS" "Receiver"] | `{{tenant.jms.queue.inbound}}` |

## Error handling

- One exception subprocess without steps: an error ends in "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Exception Subprocess"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, property "returnExceptionToSender"].
- What a sender sees on error depends on the sender adapter the scenario adds; not in the artifact.

## Extension points

- Sender adapter: the unconnected participant "Sender" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].
- Values left empty or as examples: SAP_Sender and SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Set SAP headers"], customHeaderProperties [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Set custom header attributes"], the SAP-MT2OP-<operation> properties (the script takes every property whose name contains the prefix [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/mapMessageTypeToOperation.groovy#L11]).
- Step "Parse incoming XML": configured empty [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Parse incoming XML"].
- Target queue: JMS_Outgoing [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, parameter "JMS_Outgoing"].

## Version notes

2026-09-current 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, manifest "Bundle-Version"].
