# Pipeline v2 Template Step07 - Outbound Processing Point-to-Point to Generic IDoc Outbound (com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Scenario-side outbound step for one IDoc receiver that delivers through the generic Step08 IDoc flow instead of a receiver adapter of its own. It listens on `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Sender"] and is reached like the point-to-point template: Step06 outbound processing hands each message at its end event to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set from /Interface/Service of the determination [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or from the Partner Directory on the "P2P" branch of Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 38 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33] and Step02 integrated messaging async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L33]. After the mapping and the post-processing exit it sets IDoc control-record and connection headers and calls `{{GenericIDocEndpoint}}` by Request Reply [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"]; Step08 IDoc listens on `{{ProcessDirect_Address}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_ProcessDirect_Sender" "Pipeline"], so both parameters must carry the same address. Step08 prefers a present header SAP_<key> for its seven Receiver_IDoc connection keys [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L32] and takes SAP_IDoc_EDIDC_<field> only for the fields listed in SAP_IDoc_EDIDC_overrideControlRecordParameters [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L50] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L61]. A scenario copies this template when: an asynchronous message handed over by Step06 goes to one receiver that takes IDocs, delivered by the generic Step08 IDoc flow with scenario-specific control-record and connection values, realised by the Request Reply to GenericIDocEndpoint and the two IDoc header steps [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Connection"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Sender"] |
| Receiver | ProcessDirect | Receiver | `{{GenericIDocEndpoint}}`, from step 14 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"] |
| Receiver | ProcessDirect | CustomExit | `${property.CustomXPost_Endpoint}`, from step 9 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "CustomExit"] |

## Steps

1. ProcessCallElement "Call Mappings": runs "Local Integration Process - Sequence of Mappings" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, process "Local Integration Process - Sequence of Mappings"]
  2. Enricher "Dummy Content Modifier": body `${in.body}`, passed unchanged
  3. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    4. ProcessCallElement "Call Exception Handling": runs "Local Integration Process - Exception Handling", inlined here only [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, process "Local Integration Process - Exception Handling"]
      5. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow
6. ProcessCallElement "Call Post-Processing": runs "Local Integration Process - Custom post-processing exit" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, process "Local Integration Process - Custom post-processing exit"]
  7. Enricher "Initialize": properties CustomXPost_Endpoint = `{{CustomXPost_Endpoint}}`, CustomXPost_Enabled = `{{CustomXPost_Enabled}}`; header customXPipelineStep = 07
  8. ExclusiveGateway "Exit?": "Yes" to 9 when `${property.CustomXPost_Enabled} = 'true'`, default "No (Default)" to "End 5" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
  9. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "CustomExit"]
  10. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    11. ProcessCallElement "Call Exception Handling": as step 4
12. Enricher "Set IDoc Control Header": headers SAP_IDoc_EDIDC_overrideControlRecordParameters, SAP_IDoc_EDIDC_ SNDPOR, SNDPRT, SNDPRN, RCVPOR, RCVPRT, RCVPRN, MESTYP, IDOCTYP, MANDT, DIRECT and SAP_MessageType as empty constants; SAP_IDoc_EDIDC_CRETIM = `${date:now:HHmmss}`, SAP_IDoc_EDIDC_CREDAT = `${date:now:yyyyMMdd}`
13. Enricher "Set IDoc Connection": headers SAP_Receiver_IDoc ContentType, Endpoint, ProxyType, CCLocationId, AuthenticationType, CredentialName, PrivateKeyAlias as empty constants
14. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"]; then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]
15. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  16. ProcessCallElement "Call Exception Handling": as step 4

Order of the main process: Start, 1, 6, 12, 13, 14, End [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageProcessingLogCustomStatus | property | 5 | RetryViaParentFlow [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set custom status"] |
| CustomXPost_Endpoint, CustomXPost_Enabled | property | 7 | the parameters of the same names [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Initialize"] |
| customXPipelineStep | header | 7 | 07 [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Initialize"]; the custom exit template routes 07 to "Content Modifier 7" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |
| SAP_IDoc_EDIDC_overrideControlRecordParameters | header | 12 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"]; Step08 splits it at commas into the fields to override [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L54] |
| SAP_IDoc_EDIDC_ SNDPOR … DIRECT (10 fields) | header | 12 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"]; used by Step08 only for listed fields [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L61] |
| SAP_IDoc_EDIDC_CRETIM, SAP_IDoc_EDIDC_CREDAT | header | 12 | current time HHmmss and date yyyyMMdd [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"] |
| SAP_MessageType | header | 12 | empty constant [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"]; Step08's fallback for SAP_ReceiverInterface [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/addEdidcNodeIfMissing.groovy#L13], used only when an IDOC lacks EDI_DC40 [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/addEdidcNodeIfMissing.groovy#L18] |
| SAP_Receiver_IDoc* (7 keys) | header | 13 | empty constants [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Connection"]; Step08 takes a present SAP_ header before its default [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L32] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAP_Sender(.*), SAP_Receiver(.*), SAP_ApplicationID, customX(.*), UniqueID, testMode, partnerID, customHeaderProperties, dc(.*), tenantStage, SapQualityOfService | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact. On the p2p bypass the copy's address is registered as String interfaceDetermination_<receiver alias> of the partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L30]. Step08 IDoc reads the connection values of the receiver alias itself [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L40].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/P2P_ASYNC_0001.iflw | — | the flow model; no script folder and no Script step [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] |
| `com.sap.integration.cloud.pipeline.v2.script.collection` | — | named as an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, manifest "Require-Capability"]; no step references it |

## Externalized parameters

Bold: the copy is not reachable or cannot deliver without it; plain: needed only to switch on the post-processing exit.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_Endpoint** | — | address of the ProcessDirect sender, to be delivered as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, parameter "ProcessDirect_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] | `{{tenant.endpoint.outbound.scenario}}` |
| **GenericIDocEndpoint** | — | address of Step08 IDoc, equal to its ProcessDirect_Address [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, parameter "GenericIDocEndpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, parameter "ProcessDirect_Address"] | `{{tenant.endpoint.idoc.outbound}}` |
| CustomXPost_Endpoint | — | address of the post-processing exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, parameter "CustomXPost_Endpoint"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "CustomExit"] | `{{tenant.endpoint.custom.postprocessing}}` |
| CustomXPost_Enabled | — | 'true' calls the exit [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, parameter "CustomXPost_Enabled"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"] | no |

## Error handling

- Three exception subprocesses: in the main process (15) and in the mappings (3) and post-processing (10) processes; each calls "Local Integration Process - Exception Handling" (custom status RetryViaParentFlow) and ends in an error end event ("Error End", "Error End 1", "Error End 4") [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, property "returnExceptionToSender"].
- The dump inlines the exception-handling process only under step 4; steps 11 and 16 call the same process [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].
- Step 14 lies in the main process, covered by "Exception Subprocess 1" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]; whether an error of Step08 reaches it, and then Step06, is not visible in the artifact (open questions).

## Extension points

- Mapping: step 2 passes the body unchanged [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Dummy Content Modifier"].
- Control-record overrides: SAP_IDoc_EDIDC_overrideControlRecordParameters and the field headers [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Control Header"].
- Connection overrides: the SAP_Receiver_IDoc* headers [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step "Set IDoc Connection"].
- Post-processing exit: a custom exit copy at `{{CustomXPost_Endpoint}}`, listening on its `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.exit, adapter "ProcessDirect" "Sender"], called when CustomXPost_Enabled is 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
