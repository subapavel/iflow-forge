# Pipeline v2 Template Step07 - Outbound Processing Synchronous (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-side outbound step for a synchronous receiver: it maps the request, calls a receiver adapter that the scenario adds (the participant "Receiver" has no message flow), maps the response and returns it through message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "Sender"]. Step02 integrated messaging sync calls the ProcessDirect address in header SAP_OutboundProcessingEndpoint by Request Reply [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 59 "Request Reply"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"]; the header is /Interfaces/Interface/Service of the determination result [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Get interface"] or, on the p2p bypass, the Partner Directory value interfaceDetermination_<receiver alias> [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readBypassOptionFromPD.groovy#L33]. Step01 XI makes the same synchronous call on its p2p bypass [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 41 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "ActualReceiver"]. A copy is reached when that header equals its ProcessDirect_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "Sender"]. A scenario copies this template when: a synchronous receiver answers within the call and its response goes back to the caller, realised by the receiver Request Reply followed by the response mapping before the end event [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step 14 "Request Reply"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Call response mapping"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 10 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "CustomExit"] |

No message flow from step 14: the participant "Receiver" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Steps

1. Enricher "Set SAP headers": header SAP_MessageType, empty constant
2. ProcessCallElement "Call request mapping": runs "Local Integration Process: Mapping and conversion of request message" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, process "Local Integration Process: Mapping and conversion of request message"]
  3. Enricher "Content Modifier": body `${body}`, body type expression
  4. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    5. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, process "Local Integration Process - Exception Handling"]
      6. Enricher "Return exception message": property SAP_MessageProcessingLogCustomStatus = SyncCallFailed; body `${exception.message}`
7. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, process "Local Integration Process - Custom post-processing exit"]
  8. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  9. ExclusiveGateway "Exit?": "Yes" to 10 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 5" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "CustomExit"]
  11. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    12. ProcessCallElement "Call Exception Handling": as step 5
13. Enricher "Remove headers from request": deletes headers testMode, SAP_Receiver(.*), SAP_MessageType, SAP_Sender(.*)
14. ExternalCall "Request Reply": the receiver call; no message flow
15. ProcessCallElement "Call response mapping": runs "Local Integration Process: Mapping and conversion of response message", then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, process "Local Integration Process: Mapping and conversion of response message"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]
  16. Enricher "Content Modifier": body `${body}`, body type constant
  17. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    18. ProcessCallElement "Call Exception Handling": as step 5
19. ErrorEventSubProcessTemplate "Exception Subprocess"
  20. ProcessCallElement "Call Exception Handling": as step 5

Order of the main process: Start, 1, 2, 7, 13, 14, 15, End [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]. The file keeps the Step01 name PIP_01_InboundProcessing4Scenario1.iflw [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType | header | 1, 13 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Set SAP headers"], deleted again before the receiver call [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Remove headers from request"] |
| SAP_MessageProcessingLogCustomStatus | property | 6 | SyncCallFailed [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Return exception message"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 8 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Initialize"] |
| customXPipelineStep | header | 8 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| testMode, SAP_Receiver(.*), SAP_Sender(.*) | header | 13 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, property "allowedHeaderList"]; deleted before the receiver call [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Remove headers from request"] |
| SAP_ApplicationID, customX(.*), partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, property "allowedHeaderList"]; no step reads or deletes them |

## Partner directory

None found in the artifact. On the p2p bypass the copy's address is registered as String interfaceDetermination_<receiver alias> of the partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readBypassOptionFromPD.groovy#L30].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw | — | the flow model; no script folder and no Script step [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, to be delivered as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"] | `{{tenant.endpoint.outbound.scenario}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"] | no |

## Error handling

- Four exception subprocesses: in the main process (19) and in the request mapping (4), post-processing (11) and response mapping (17) processes; each calls "Local Integration Process - Exception Handling" (custom status SyncCallFailed, body = exception message) and ends in an error end event ("Error End 1" to "Error End 4") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 5; steps 12, 18 and 20 call the same process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].
- What the caller's Request Reply receives on an error is not visible in the artifact (open questions).

## Extension points

- Receiver adapter: the unconnected participant "Receiver" called by step 14 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].
- Request and response mapping: steps 3 and 16 set the body to `${body}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step 3 "Content Modifier"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step 16 "Content Modifier"].
- SAP_MessageType left empty [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Set SAP headers"]; the header list removed before the call [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, step "Remove headers from request"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"].

## Version notes

2026-09-current 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync, manifest "Bundle-Version"].
