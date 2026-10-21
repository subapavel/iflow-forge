# Pipeline v2 Template Step07 - Outbound Processing One-to-Many with Multiple Message Types (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm` 1.0.1, package templates, label 2026-06.

## Purpose

Scenario-side outbound step that turns one message into messages of two types: a one-to-many message mapping, then a parallel multicast into "Branch 1" and "Branch 2" that each split, convert and call a receiver adapter the scenario adds, as the participants "Receiver1" and "Receiver2" have no message flow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, adapter "ProcessDirect" "Sender"] and is reached like the point-to-point template: Step06 outbound processing hands each message at its end event to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set from /Interface/Service of the determination in Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get interface"] and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or from the Partner Directory on the "P2P" branch of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. A scenario copies this template when: an asynchronous message handed over by Step06 is mapped one-to-many into messages of two message types, delivered per type in parallel branches, each part at most once per UniqueID, branch and split index, realised by the mapping, the parallel multicast, the two iterating splitters and the idempotent process calls keyed by SplitMessageID [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "One-to-Many Message Mapping"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Parallel Multicast"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Iterating Splitter 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Iterating Splitter 2"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step 7 "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step 25 "Set unique ID 2"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step 19 "Idempotent Process Call 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step 31 "Idempotent Process Call 2"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 16 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, adapter "ProcessDirect" "CustomExit"] |

No message flow from steps 20 and 32: the participants "Receiver1" and "Receiver2" are unconnected [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Steps

1. XmlModifier "Remove XML Declaration": removes the XML declaration
2. Enricher "Add Messages nodes": wraps `${in.body}` in ns0:Messages/ns0:Message1, namespace http://sap.com/xi/XI/SplitAndMerge
3. Mapping "One-to-Many Message Mapping": message mapping, static reference, mapping URI empty [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
4. Multicast "Parallel Multicast": parallel, to "Branch 1" (step 5) and "Branch 2" (step 23) [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
5. Splitter "Iterating Splitter 1": XPath `//rootNodeOfTargetMessageType1`, streaming, not parallel, stop on exception
6. Enricher "Set Headers 1": header SAP_MessageType = MessageType1
7. Enricher "Set unique ID 1": property SplitMessageID = `${header.UniqueID}_Branch1_${header.CamelSplitIndex}`
8. ProcessCallElement "Call Outbound Conversion 1": runs "Local Integration Process - Outbound Conversion for Branch 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Outbound Conversion for Branch 1"]
  9. Enricher "Dummy Content Modifier 1": body `${in.body}`, passed unchanged
  10. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    11. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Exception Handling"]
      12. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
13. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Custom post-processing exit"]
  14. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  15. ExclusiveGateway "Exit?": "Yes" to 16 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 7" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  16. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, adapter "ProcessDirect" "CustomExit"]
  17. ErrorEventSubProcessTemplate "Exception Subprocess 5"
    18. ProcessCallElement "Call Exception Handling": as step 11
19. IdempotentProcessCall "Idempotent Process Call 1": message ID `${property.SplitMessageID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver for Branch 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Send Message to Receiver for Branch 1"]
  20. ExternalCall "Request Reply 1": the Branch 1 receiver call; no message flow
  21. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    22. ProcessCallElement "Call Exception Handling": as step 11
23. Splitter "Iterating Splitter 2": XPath `//rootNodeOfTargetMessageType2`, streaming, not parallel, stop on exception
24. Enricher "Set Headers 2": header SAP_MessageType = MessageType2
25. Enricher "Set unique ID 2": property SplitMessageID = `${header.UniqueID}_Branch2_${header.CamelSplitIndex}`
26. ProcessCallElement "Call Outbound Conversion 2": runs "Local Integration Process - Outbound Conversion for Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Outbound Conversion for Branch 2"]
  27. Enricher "Dummy Content Modifier 2": body `${in.body}`, passed unchanged
  28. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    29. ProcessCallElement "Call Exception Handling": as step 11
30. ProcessCallElement "Call Post-Processing": as step 13
31. IdempotentProcessCall "Idempotent Process Call 2": message ID `${property.SplitMessageID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver for Branch 2" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, process "Local Integration Process - Send Message to Receiver for Branch 2"]
  32. ExternalCall "Request Reply 2": the Branch 2 receiver call; no message flow
  33. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    34. ProcessCallElement "Call Exception Handling": as step 11
35. ErrorEventSubProcessTemplate "Exception Subprocess"
  36. ProcessCallElement "Call Exception Handling": as step 11

Order of the main process: Start, 1, 2, 3, 4; "Branch 1": 5, 6, 7, 8, 13, 19, End; "Branch 2": 23, 24, 25, 26, 30, 31, End; both branches share message end event "End", with no Join or Gather [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType | header | 6, 24 | MessageType1, MessageType2 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set Headers 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set Headers 2"] |
| SplitMessageID | property | 7, 25 | `${header.UniqueID}_Branch<n>_${header.CamelSplitIndex}`, the idempotency key of steps 19 and 31 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set unique ID 2"] |
| SAP_MessageProcessingLogCustomStatus | property | 12 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 14 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Initialize"] |
| customXPipelineStep | header | 14 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| UniqueID | header | 7, 25 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, property "allowedHeaderList"]; first part of SplitMessageID [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set unique ID 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set unique ID 2"]; Step06 sets it to its SAP_MessageProcessingLogID [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set unique identifier"] |
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder, no Script step and no mapping file: step 3 names no mapping [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, the value the determination or the Partner Directory must deliver as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Six exception subprocesses: in the main process (35) and in the conversion (10, 28), post-processing (17) and delivery (21, 33) processes; each calls "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow) and ends in an error end event ("Error End", "Error End 1" to "Error End 5") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. No retry step in the template; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 11 and the post-processing process only under step 13; steps 18, 22, 29, 34 and 36 call the former, step 30 the latter [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Extension points

- Receiver adapters: the unconnected participants "Receiver1" (step 20) and "Receiver2" (step 32) [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- One-to-many mapping: step 3 names no mapping [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; the split XPaths `//rootNodeOfTargetMessageType1` and `//rootNodeOfTargetMessageType2` and the message types MessageType1 and MessageType2 stand for the target types [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Iterating Splitter 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Iterating Splitter 2"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set Headers 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Set Headers 2"].
- Conversion: steps 9 and 27 pass the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Dummy Content Modifier 1"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step "Dummy Content Modifier 2"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called by steps 13 and 30 when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

2026-09 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, manifest "Bundle-Version"].
