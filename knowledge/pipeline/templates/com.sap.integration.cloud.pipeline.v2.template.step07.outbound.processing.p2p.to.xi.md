# Pipeline v2 Template Step07 - Outbound Processing Point-to-Point to Generic XI Outbound (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Scenario-side outbound step for one XI receiver that delivers through the generic Step08 XI flow instead of a receiver adapter of its own. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Sender"] and is reached like the point-to-point template: Step06 outbound processing hands each message at its end event to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set from /Interface/Service of the determination [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or from the Partner Directory on the "P2P" branch of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. It sets XI connection and interface headers, maps, runs the post-processing exit and calls `{{GenericXIEndpoint}}` by Request Reply [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Receiver"]; Step08 XI listens on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "ProcessDirect" "Pipeline"], so both parameters must carry the same address. Step08 prefers a present header SAP_<key> for its Receiver_XI connection keys and the interface identity [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/setValuesForXI.groovy#L42]. After the call the template copies the XI message GUID headers into customHeader_ properties and writes the custom header properties to the message processing log [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L69]. A scenario copies this template when: an asynchronous message handed over by Step06 goes to one receiver over XI, delivered by the generic Step08 XI flow with scenario-specific connection and interface values, realised by the Request Reply to GenericXIEndpoint and the step "Set XI Headers" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set XI Headers"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | Receiver | `{{GenericXIEndpoint}}`, from step 13 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Receiver"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 10 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "CustomExit"] |

## Steps

1. Enricher "Set XI Headers": headers SAP_Receiver_XI Endpoint, ProxyType, CCLocationId, AuthenticationType, CredentialName, PrivateKeyAlias, SAP_ReceiverInterface, SAP_ReceiverInterfaceNamespace, SAP_MessageType as empty constants
2. ProcessCallElement "Call Mappings": runs "Local Integration Process - Sequence of Mappings" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, process "Local Integration Process - Sequence of Mappings"]
  3. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  4. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    5. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, process "Local Integration Process - Exception Handling"]
      6. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
7. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, process "Local Integration Process - Custom post-processing exit"]
  8. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  9. ExclusiveGateway "Exit?": "Yes" to 10 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 5" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "CustomExit"]
  11. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    12. ProcessCallElement "Call Exception Handling": as step 5
13. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Receiver"]
14. Enricher "Set properties with XI GUIDs": properties customHeader_SapMessageId, customHeader_SapMessageIdEx, customHeader_SapRefToMessageId, customHeader_SapRefToMessageIdEx from the headers SapMessageId, SapMessageIdEx, SapRefToMessageId, SapRefToMessageIdEx
15. Script "Add custom header properties": `addCustomHeaderProperties.groovy` from the script collection; merges the customHeaderProperties entries and the customHeader_ properties (prefix removed), adds each to the message processing log and stores them in header customHeaderProperties as Base64 JSON [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L20] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L46] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L69] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L75]; then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
16. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  17. ProcessCallElement "Call Exception Handling": as step 5

Order of the main process: Start, 1, 2, 7, 13, 14, 15, End [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_Receiver_XI* (6 keys), SAP_ReceiverInterface, SAP_ReceiverInterfaceNamespace | header | 1 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set XI Headers"]; Step08 takes a present SAP_ header before its default [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/setValuesForXI.groovy#L42] |
| SAP_MessageType | header | 1 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set XI Headers"]; Step08's interface fallback [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L67] |
| SAP_MessageProcessingLogCustomStatus | property | 6 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 8 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Initialize"] |
| customXPipelineStep | header | 8 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |
| customHeader_SapMessageId, customHeader_SapMessageIdEx, customHeader_SapRefToMessageId, customHeader_SapRefToMessageIdEx | property | 14 | the headers without the prefix [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set properties with XI GUIDs"] |
| customHeaderProperties | header | 15 | Base64 JSON of the merged map, only when the map is not empty [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L66] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L75] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits; the GUID headers of step 14 are read after the Step08 call.

| Name | Kind | Step | Used for |
|---|---|---|---|
| customHeaderProperties | header | 15 | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, property "allowedHeaderList"]; split at pipes, each part Base64 JSON or key:value pairs [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L20] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L27] |
| SapMessageId, SapMessageIdEx, SapRefToMessageId, SapRefToMessageIdEx | header | 14 | the customHeader_ properties [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set properties with XI GUIDs"]; their source is not in the artifact (open questions) |
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), UniqueID, testMode, partnerID, dc(.*), tenantStage, SapQualityOfService | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact. On the p2p bypass the copy's address is registered as String interfaceDetermination_<receiver alias> of the partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L30]. Step08 XI resolves an empty SAP_ReceiverInterface from Partner Directory InterfaceNameDetermination_<receiver> itself [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L64].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `addCustomHeaderProperties.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 15 | custom header properties to the message processing log [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L69]; referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Add custom header properties"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, manifest "Require-Capability"] |
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder of its own [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |

## Externalized parameters

Bold: the copy is not reachable or cannot deliver without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, to be delivered as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| **GenericXIEndpoint** | — | address of Step08 XI, equal to its ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, parameter "GenericXIEndpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, parameter "ProcessDirect_EndPoint"] | `{{tenant.endpoint.xi.outbound}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Three exception subprocesses: in the main process (16) and in the mappings (4) and post-processing (11) processes; each calls "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow) and ends in an error end event ("Error End", "Error End 1", "Error End 4") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 5; steps 12 and 17 call the same process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- Step 13 lies in the main process, covered by "Exception Subprocess 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; whether an error of Step08 reaches it, and then Step06, is not visible in the artifact (open questions).

## Extension points

- Mapping: step 3 passes the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Dummy Content Modifier"].
- Connection and interface overrides: the headers of step 1 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Set XI Headers"].
- Further custom header properties: any property named customHeader_<name> reaches the log [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L44].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
