# Pipeline v2 Template Step01 - Inbound Processing Synchronous (com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-side first step of the pipeline for a synchronous sender. The template has no sender adapter: its participant "Sender" has no message flow, so the scenario adds one [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. The flow sets the SAP sender headers, runs a local inbound conversion, calls `{{Sync_Pipeline_Endpoint}}` by Request Reply [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, adapter "ProcessDirect" "Receiver"], runs a local outbound conversion on the response, removes the SAP sender headers and ends in message end event "End", which has no message flow [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. The generic synchronous runtime, Step02 integrated messaging sync, listens on `/{{PipelinePrefix}}/XS01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "InboundProcessingSync"]; the template does not name that address (open questions). A scenario copies this template when: the sender is synchronous and waits for the receiver's response, realised by the Request Reply to the synchronous pipeline entry inside one flow [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, adapter "ProcessDirect" "Receiver"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Receiver | ProcessDirect | Receiver | `{{Sync_Pipeline_Endpoint}}`, from ExternalCall "Request Reply" (step 9) [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, adapter "ProcessDirect" "Receiver"] |

No sender message flow: the participant "Sender" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Steps

1. Enricher "Set SAP headers": headers SAP_Sender, SAP_SenderInterface (empty constants); SAP_MessageType = header SAP_SenderInterface
2. ProcessCallElement "Call inbound conversion": local process "Local Integration Process: Inbound conversion"
  3. Enricher "Parse incoming XML": body `${body}`
  4. Enricher "Set custom header attributes": header customHeaderProperties, empty expression
  5. ErrorEventSubProcessTemplate "Exception Subprocess 1": ends in "Error End 2" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
    6. ProcessCallElement "Call Exception Handling": local process "Local Integration Process - Exception Handling"
      7. Enricher "Return exception message": body `${exception.message}`; property SAP_MessageProcessingLogCustomStatus = SyncCallFailed
      8. Enricher "Remove headers from response": deletes headers SAP_MessageType, SAP_SenderInterface, SAP_Sender
9. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, adapter "ProcessDirect" "Receiver"]
10. ProcessCallElement "Call outbound conversion": local process "Local Integration Process: Outbound conversion"
  11. Enricher "Content Modifier": body `${body}`
  12. ErrorEventSubProcessTemplate "Exception Subprocess 2": ends in "Error End 3" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
    13. ProcessCallElement "Call Exception Handling": the local process of step 6
14. Enricher "Remove headers from response": deletes headers SAP_MessageType, SAP_SenderInterface, SAP_Sender; then end event "End" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
15. ErrorEventSubProcessTemplate "Exception Subprocess": ends in "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
  16. ProcessCallElement "Call Exception Handling": the local process of step 6

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_Sender, SAP_SenderInterface | header | 1; deleted 8, 14 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Set SAP headers"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 14 "Remove headers from response"] |
| SAP_MessageType | header | 1; deleted 8, 14 | copy of header SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Set SAP headers"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 8 "Remove headers from response"] |
| customHeaderProperties | header | 4 | empty expression [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Set custom header attributes"] |
| SAP_MessageProcessingLogCustomStatus | property | 7 | SyncCallFailed [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Return exception message"] |

### Read

Nothing from outside the flow except the payload, the response of step 9 and the exception; the allowed header list is empty [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, property "allowedHeaderList"].

| Name | Kind | Step | Used for |
|---|---|---|---|
| `${exception.message}` | exception | 7 | body returned on error [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Return exception message"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw | — | the flow model; no script step and no script folder [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] |

## Externalized parameters

Bold: the scenario author must set it; the template references it without a value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **Sync_Pipeline_Endpoint** | — | ProcessDirect address the Request Reply calls, the synchronous pipeline entry [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, parameter "Sync_Pipeline_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.pipeline.sync}}` |

## Error handling

- Three exception subprocesses, one in the main process and one in each conversion process; each calls "Local Integration Process - Exception Handling" and ends in an error end event [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 6 "Call Exception Handling"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 13 "Call Exception Handling"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 16 "Call Exception Handling"].
- The exception handling sets the body to the exception message, custom status SyncCallFailed, and deletes the three SAP sender headers [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Return exception message"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step 8 "Remove headers from response"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, property "returnExceptionToSender"]. What the sender receives depends on the sender adapter the scenario adds; not in the artifact.
- The dump inlines the exception-handling process only under step 6; steps 13 and 16 call the same process [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Extension points

- Sender adapter: the unconnected participant "Sender" [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].
- Request conversion: local process "Inbound conversion", body passed unchanged (`${body}`) [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Parse incoming XML"].
- Response conversion: local process "Outbound conversion", body passed unchanged [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Content Modifier"].
- Values left empty: SAP_Sender and SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Set SAP headers"], customHeaderProperties [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, step "Set custom header attributes"].
- Pipeline entry: Sync_Pipeline_Endpoint [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, parameter "Sync_Pipeline_Endpoint"].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
