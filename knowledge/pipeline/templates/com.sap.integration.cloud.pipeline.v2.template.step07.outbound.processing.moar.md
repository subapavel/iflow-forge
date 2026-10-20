# Pipeline v2 Template Step07 - Outbound Processing Maintain Order At Runtime (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-side outbound step that sends one message along two branches in a fixed order: a sequential multicast runs "Branch 1" before "Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Sequential Multicast"], and each branch maps, converts and calls a receiver adapter the scenario adds, as the participants "Receiver1" and "Receiver2" have no message flow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, adapter "ProcessDirect" "Sender"]; Step06 outbound processing calls it at its end event through header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set from /Interface/Service in Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get interface"] and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or from the Partner Directory on the "P2P" bypass of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] and integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. A scenario copies this template when: an asynchronous message handed over by Step06 goes, mapped per branch, to two receiver calls of one message type each in a fixed order, each at most once per UniqueID and branch, realised by the sequential multicast, the SAP_MessageType headers and the idempotent calls keyed by SplitMessageID [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Sequential Multicast"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 2"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 2"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Idempotent Process Call 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Idempotent Process Call 2"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 16 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, adapter "ProcessDirect" "CustomExit"] |

No message flow from steps 20 and 35: the participants "Receiver1" and "Receiver2" are unconnected [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Steps

1. SequentialMulticast "Sequential Multicast": sequence 1 "Branch 1" (step 2), sequence 2 "Branch 2" (step 23) [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
2. Enricher "Set Headers 1": header SAP_MessageType = MessageType1
3. ProcessCallElement "Call Mappings 1": runs "Local Integration Process - Mapping Sequence for Branch 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Mapping Sequence for Branch 1"]
  4. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  5. ErrorEventSubProcessTemplate "Exception Subprocess 5"
    6. ProcessCallElement "Call Exception Handling": names no process (processId empty)
7. Enricher "Set unique ID 1": property SplitMessageID = `${header.UniqueID}_Branch1`
8. ProcessCallElement "Call Outbound Conversion 1": runs "Local Integration Process - Outbound Conversion for Branch 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Outbound Conversion for Branch 1"]
  9. Enricher "Dummy Content Modifier": as step 4
  10. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    11. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Exception Handling"]
      12. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
13. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Custom post-processing exit"]
  14. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  15. ExclusiveGateway "Exit?": "Yes" to 16 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 9" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  16. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, adapter "ProcessDirect" "CustomExit"]
  17. ErrorEventSubProcessTemplate "Exception Subprocess 7"
    18. ProcessCallElement "Call Exception Handling": as step 11
19. IdempotentProcessCall "Idempotent Process Call 1": message ID `${property.SplitMessageID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver for Branch 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Send Message to Receiver for Branch 1"]
  20. ExternalCall "Request Reply 1": the Branch 1 receiver call; no message flow
  21. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    22. ProcessCallElement "Call Exception Handling": as step 11
23. Enricher "Set Headers 2": header SAP_MessageType = MessageType2
24. ProcessCallElement "Call Mappings 2": runs "Local Integration Process - Mapping Sequence for Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Mapping Sequence for Branch 2"]
  25. Enricher "Dummy Content Modifier": as step 4
  26. ErrorEventSubProcessTemplate "Exception Subprocess 6"
    27. ProcessCallElement "Call Exception Handling": as step 11
28. Enricher "Set unique ID 2": property SplitMessageID = `${header.UniqueID}_Branch2`
29. ProcessCallElement "Call Outbound Conversion 2": runs "Local Integration Process - Outbound Conversion for Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Outbound Conversion for Branch 2"]
  30. Enricher "Dummy Content Modifier": as step 4
  31. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    32. ProcessCallElement "Call Exception Handling": as step 11
33. ProcessCallElement "Call Post-Processing": as step 13
34. IdempotentProcessCall "Idempotent Process Call 2": message ID `${property.SplitMessageID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver for Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, process "Local Integration Process - Send Message to Receiver for Branch 2"]
  35. ExternalCall "Request Reply 2": the Branch 2 receiver call; no message flow
  36. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    37. ProcessCallElement "Call Exception Handling": as step 11
38. ErrorEventSubProcessTemplate "Exception Subprocess"
  39. ProcessCallElement "Call Exception Handling": as step 11

Order of the main process: Start, 1; "Branch 1": 2, 3, 7, 8, 13, 19, End; "Branch 2": 23, 24, 28, 29, 33, 34, End; both branches share message end event "End", with no Join or Gather [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType | header | 2, 23 | MessageType1, MessageType2 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 2"] |
| SplitMessageID | property | 7, 28 | `${header.UniqueID}_Branch1`, `${header.UniqueID}_Branch2`, the idempotency keys of steps 19 and 34 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 2"] |
| SAP_MessageProcessingLogCustomStatus | property | 12 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 14 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Initialize"] |
| customXPipelineStep | header | 14 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| UniqueID | header | 7, 28 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, property "allowedHeaderList"]; first part of SplitMessageID [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set unique ID 2"]; Step06 sets it to its SAP_MessageProcessingLogID [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set unique identifier"] |
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder, no Script step and no Mapping step [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, the value the determination or the Partner Directory must deliver as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Eight exception subprocesses: in the main process (38) and in the mappings (5, 26), conversion (10, 31), post-processing (17) and delivery (21, 36) processes, each ending in an error end event ("Error End", "Error End 1" to "Error End 7") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. Seven call "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow); step 6 in "Exception Subprocess 5" names no process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 6 "Call Exception Handling"] (open questions). No retry step in the template; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 11 and the post-processing process only under step 13; steps 18, 22, 27, 32, 37 and 39 call the former, step 33 the latter [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Extension points

- Receiver adapters: the unconnected participants "Receiver1" (step 20) and "Receiver2" (step 35) [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- Mappings and conversion: steps 4, 9, 25 and 30 pass the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 4 "Dummy Content Modifier"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 9 "Dummy Content Modifier"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 25 "Dummy Content Modifier"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 30 "Dummy Content Modifier"].
- Message types: MessageType1 and MessageType2 stand for the branch message types [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step "Set Headers 2"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called by steps 13 and 33 when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

2026-09-current 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, manifest "Bundle-Version"].
