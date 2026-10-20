# Pipeline v2 Template Step04 - Custom Receiver Determination (com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Empty skeleton of a scenario-specific receiver determination flow: a ProcessDirect sender on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, adapter "ProcessDirect" "Sender"], an integration process from "Start" straight to "End" and an exception subprocess [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; the scenario writes the determination logic. Generic Step04 takes the endpoint from header customXRDEndpoint, else from Partner Directory CustomXRDEndpoint, else ReuseXRDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35], branches on customXRDBoolean [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 32 "Custom RD?"] and calls it by Request Reply (step 71) to `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"]. The integrated runtimes call it the same way: async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverXRD"], sync [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 33 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ReceiverXRD"]. A scenario copies this template when: the receivers are determined by logic the scenario writes itself instead of the Partner Directory XSLT, realised by the ProcessDirect entry with no determination step [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}`, to start event "Start" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, adapter "ProcessDirect" "Sender"] |

## Steps

1. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1", step 2, error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
  2. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow

The integration process has no step: "Start" leads to "End" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageProcessingLogCustomStatus | property | 2 | RetryViaParentFlow, on error only [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, step "Set custom status"] |

### Read

Besides the payload, the caller's headers SAP_Sender(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*) and tenantStage may enter the flow [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, property "allowedHeaderList"]; no step of the template reads them.

| Name | Kind | Step | Used for |
|---|---|---|---|
| payload | body | — | input of the logic the scenario adds; the message the caller sends [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| xsd/Receivers.xsd | — | Receivers XML in namespace http://sap.com/xi/XI/System: optional ReceiverNotDetermined (Type, DefaultReceiver), Receiver (Party, Service, Interfaces/Interface with Index, Service, Name) [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "xsd/Receivers.xsd"] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

No script step [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Externalized parameters

Bold: the scenario author must set it; the propdef gives no value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender, the value the caller finds as customXRDEndpoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L43] | `{{tenant.endpoint.receiver.determination.custom}}` |

## Error handling

- The exception subprocess sets the custom status RetryViaParentFlow and ends in error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, step "Set custom status"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, property "returnExceptionToSender"].
- Caller side: Step04's Request Reply runs in local process "Call custom X or XRD option" with "Exception Subprocess 3": custom error handling if CustomXError_Enabled is 'true', else standard retry handling [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 75 "Exception Subprocess 3"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 76 "Custom Extension?"]. Whether the template's error reaches it: open questions.

## Extension points

- The whole determination between "Start" and "End" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]. The caller reads from the reply, in namespace ns0 = http://sap.com/xi/XI/System [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, property "namespaceMapping"]:
  - Step04: no `ns0:Receivers/Receiver` leads to `ReceiverNotDetermined/Type` Default, Ignore or else error [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 42 "Any Receiver?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 43 "Not found type?"], with `DefaultReceiver/Service` for Default [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 44 "Get default receiver name"]; `Receiver/Service` is the receiver [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 69 "Get receiver name"]; any `Interface` bypasses Step05 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 51 "Bypass interface determination?"], each `Interface` giving Name, Index and Service (the outbound processing endpoint) [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 57 "Get interface"]; else the message goes to Step05 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"].
  - Integrated async: no `Interfaces/Interface` ends in InterfaceNotFoundError [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 66 "Any Interface?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 74 "Set custom status"]; the reply must carry the interfaces.
  - Integrated sync: more than one receiver is an error [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 46 "Any Receiver?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 47 "Set custom status"].
- Endpoint: ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, parameter "ProcessDirect_EndPoint"].

## Version notes

2026-09-current 1.0.0: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, manifest "Bundle-Version"].
