# Pipeline v2 Template Step07 - Outbound Processing Point-to-Point (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p` 1.0.1, package templates, label 2026-06.

## Purpose

Scenario-side outbound step for one receiver: it maps and converts the message and delivers it to a receiver adapter that the scenario adds, because the participant "Receiver" has no message flow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "Sender"]. Step06 outbound processing hands each message at its end event to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"]; that header is /Interface/Service of the determination result in Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get interface"] and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"], or, on the "P2P" branch (`${property.bypassOption} = 'p2p'`) of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"], the Partner Directory value interfaceDetermination_<receiver alias> [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. A copy is reached when that value equals its ProcessDirect_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "Sender"]. A scenario copies this template when: an asynchronous message handed over by Step06 goes to exactly one receiver over an adapter the scenario adds, delivered at most once per UniqueID, realised by the idempotent process call around the receiver call [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Idempotent Process Call"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"]. Built into the template, the exit, idempotent call and error status are not left to each copy [rationale, from analysis 2026].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 14 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "CustomExit"] |

No message flow from step 18: the participant "Receiver" is unconnected [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Steps

1. Enricher "Set Headers": header SAP_MessageType, empty constant
2. ProcessCallElement "Call Mappings": runs "Local Integration Process - Sequence of Mappings" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, process "Local Integration Process - Sequence of Mappings"]
  3. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  4. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    5. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, process "Local Integration Process - Exception Handling"]
      6. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
7. ProcessCallElement "Call Outbound Conversion": runs "Local Integration Process - Outbound Conversion" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, process "Local Integration Process - Outbound Conversion"]
  8. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  9. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    10. ProcessCallElement "Call Exception Handling": as step 5
11. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, process "Local Integration Process - Custom post-processing exit"]
  12. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  13. ExclusiveGateway "Exit?": "Yes" to 14 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 5" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  14. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "CustomExit"]
  15. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    16. ProcessCallElement "Call Exception Handling": as step 5
17. IdempotentProcessCall "Idempotent Process Call": message ID `${header.UniqueID}`, skip on duplicate; runs "Local Integration Process - Send Message to Receiver", then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, process "Local Integration Process - Send Message to Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  18. ExternalCall "Request Reply": the receiver call; no message flow
  19. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    20. ProcessCallElement "Call Exception Handling": as step 5
21. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  22. ProcessCallElement "Call Exception Handling": as step 5

Order of the main process: Start, 1, 2, 7, 11, 17, End [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType | header | 1 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Set Headers"] |
| SAP_MessageProcessingLogCustomStatus | property | 6 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 12 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Initialize"] |
| customXPipelineStep | header | 12 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| UniqueID | header | 17 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, property "allowedHeaderList"]; idempotency key [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Idempotent Process Call"]; Step06 sets it to its SAP_MessageProcessingLogID [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set unique identifier"] |
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*), tenantStage | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact. For the p2p bypass the copy's address is registered as String interfaceDetermination_<receiver alias> of the partner ID, the alias being receiverDetermination of the same partner ID, read by Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L30] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L30]; when it is missing, bypassOption becomes skipRcvDet [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L36].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder and no Script step [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, the value the determination or the Partner Directory must deliver as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Five exception subprocesses: in the main process (21) and in the mappings (4), conversion (9), post-processing (15) and delivery (19) processes; each calls "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow) and ends in an error end event ("Error End", "Error End 1" to "Error End 4") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. No retry step in the template; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 5; steps 10, 16, 20 and 22 call the same process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- Whether the error of a copy reaches Step06's retry handling is not visible in the artifact (open questions).

## Extension points

- Receiver adapter: the unconnected participant "Receiver" called by step 18 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- Mappings and conversion: steps 3 and 8 pass the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 3 "Dummy Content Modifier"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 8 "Dummy Content Modifier"].
- SAP_MessageType left empty [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step "Set Headers"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

2026-09 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, manifest "Bundle-Version"].
