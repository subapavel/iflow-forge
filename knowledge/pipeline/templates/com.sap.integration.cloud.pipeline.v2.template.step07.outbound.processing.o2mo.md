# Pipeline v2 Template Step07 - Outbound Processing One-to-Many with One Message Type (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-side outbound step that turns one message into several messages of one type for one receiver: a one-to-many message mapping, an iterating splitter, and per part a conversion and a call to a receiver adapter that the scenario adds, because the participant "Receiver" has no message flow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, adapter "ProcessDirect" "Sender"] and is reached like the point-to-point template: Step06 outbound processing hands each message at its end event to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set from /Interface/Service of the determination in Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get interface"] and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or from the Partner Directory on the "P2P" branch of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. A scenario copies this template when: an asynchronous message handed over by Step06 goes to one receiver as several messages of one message type, produced by a one-to-many mapping and split, each part delivered at most once per UniqueID and split index, realised by the mapping, the iterating splitter and the idempotent process call [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "One-to-Many Message Mapping"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Iterating Splitter"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set unique ID for split messages"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Idempotent Process Call"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"]. A resend with the same UniqueID and the same split yields the same SplitMessageID per part [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set unique ID for split messages"], which step 18 skips as a duplicate [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Idempotent Process Call"]. Each part needs its own key, or the first would be delivered and the rest skipped [rationale, from analysis 2026]; built on UniqueID, the key stays per part when a flow sets UniqueID to a business value [rationale, from analysis 2026].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 15 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, adapter "ProcessDirect" "CustomExit"] |

No message flow from step 19: the participant "Receiver" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Steps

1. Enricher "Set Headers": header SAP_MessageType, empty constant
2. XmlModifier "Remove XML Declaration": removes the XML declaration
3. Enricher "Add Messages nodes": wraps `${in.body}` in ns0:Messages/ns0:Message1, namespace http://sap.com/xi/XI/SplitAndMerge
4. Mapping "One-to-Many Message Mapping": message mapping, static reference, mapping URI empty [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
5. Splitter "Iterating Splitter": XPath `//rootNodeOfTargetMessage`, streaming, not parallel, stop on exception
6. Enricher "Set unique ID for split messages": property SplitMessageID = `${header.UniqueID}_${header.CamelSplitIndex}`
7. ProcessCallElement "Call Outbound Conversion": runs "Local Integration Process - Outbound Conversion" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, process "Local Integration Process - Outbound Conversion"]
  8. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  9. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    10. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, process "Local Integration Process - Exception Handling"]
      11. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
12. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, process "Local Integration Process - Custom post-processing exit"]
  13. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  14. ExclusiveGateway "Exit?": "Yes" to 15 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 5" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  15. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, adapter "ProcessDirect" "CustomExit"]
  16. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    17. ProcessCallElement "Call Exception Handling": as step 10
18. IdempotentProcessCall "Idempotent Process Call": message ID `${property.SplitMessageID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver", then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, process "Local Integration Process - Send Message to Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  19. ExternalCall "Request Reply": the receiver call; no message flow
  20. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    21. ProcessCallElement "Call Exception Handling": as step 10
22. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  23. ProcessCallElement "Call Exception Handling": as step 10

Order of the main process: Start, 1, 2, 3, 4, 5, 6, 7, 12, 18, End; no Gather follows the splitter, so steps 6 to 18 and "End" run per split part [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Iterating Splitter"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType | header | 1 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set Headers"] |
| SplitMessageID | property | 6 | `${header.UniqueID}_${header.CamelSplitIndex}`, the idempotency key of step 18 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set unique ID for split messages"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Idempotent Process Call"] |
| SAP_MessageProcessingLogCustomStatus | property | 11 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 13 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Initialize"] |
| customXPipelineStep | header | 13 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| UniqueID | header | 6 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, property "allowedHeaderList"]; first part of SplitMessageID [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set unique ID for split messages"]; Step06 sets it to its SAP_MessageProcessingLogID [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set unique identifier"] |
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder, no Script step and no mapping file: step 4 names no mapping [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, the value the determination or the Partner Directory must deliver as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Four exception subprocesses: in the main process (22) and in the conversion (9), post-processing (16) and delivery (20) processes; each calls "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow) and ends in an error end event ("Error End", "Error End 2", "Error End 4", "Error End 3") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. No retry step in the template; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, property "returnExceptionToSender"].
- Steps 9 and 20 carry the same name "Exception Subprocess 3" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step 9 "Exception Subprocess 3"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step 20 "Exception Subprocess 3"].
- The splitter is set to stop on exception [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Iterating Splitter"].
- The dump inlines the exception-handling process only under step 10; steps 17, 21 and 23 call the same process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Extension points

- Receiver adapter: the unconnected participant "Receiver" called by step 19 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- One-to-many mapping: step 4 names no mapping [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; the split XPath `//rootNodeOfTargetMessage` stands for the target root node [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Iterating Splitter"].
- Conversion: step 8 passes the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Dummy Content Modifier"].
- SAP_MessageType left empty [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step "Set Headers"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called once per part when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

2026-09-current 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, manifest "Bundle-Version"].
