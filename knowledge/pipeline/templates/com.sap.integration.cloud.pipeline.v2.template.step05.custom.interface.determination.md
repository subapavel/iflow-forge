# Pipeline v2 Template Step05 - Custom Interface Determination (com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination` 1.0.0, package templates, label 2026-06.

## Purpose

Empty skeleton of a scenario-specific interface determination flow: a ProcessDirect sender on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, adapter "ProcessDirect" "Sender"], an integration process from "Start" straight to "End" and an exception subprocess [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; the scenario writes the determination logic. Generic Step05 reads Partner Directory CustomXIDEndpoint of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21], branches on customXIDBoolean [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 33 "Custom ID?"] and calls it by Request Reply (step 53) to `${property.customXIDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "ReceiverXID"]. Step05 is fed by Step04 when its result carries no interfaces [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 51 "Bypass interface determination?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"]; the integrated runtimes determine receivers and interfaces in one local process [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 45 "Determine receivers & interfaces"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 31 "Determine receivers & interfaces"]. A scenario copies this template when: the interfaces of a receiver are determined in Step05, not in an integrated runtime, by logic the scenario writes itself instead of the Partner Directory XSLT, realised by the ProcessDirect entry with no determination step [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}`, to start event "Start" [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, adapter "ProcessDirect" "Sender"] |

## Steps

1. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1", step 2, error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
  2. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow

The integration process has no step: "Start" leads to "End" [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageProcessingLogCustomStatus | property | 2 | RetryViaParentFlow, on error only [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, step "Set custom status"] |

### Read

Besides the payload, the caller's headers SAP_Sender(.*), SAP_Receiver(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*) and tenantStage may enter the flow [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, property "allowedHeaderList"]; the Step04 templates do not allow SAP_Receiver(.*) [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, property "allowedHeaderList"]. No step of the template reads them.

| Name | Kind | Step | Used for |
|---|---|---|---|
| payload | body | — | input of the logic the scenario adds; the message the caller sends [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "ReceiverXID"] |
| SAP_ReceiverAlias | header | — | the receiver whose interfaces are wanted; Step05 itself keys its XSLT by it [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 36 "Get XSLT mapping from Partner Directory"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| xsd/Interfaces.xsd | — | Interfaces XML in namespace http://sap.com/xi/XI/System: Interface (Index, Service, Name), zero or more [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "xsd/Interfaces.xsd"] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

No script step [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Externalized parameters

Bold: the scenario author must set it; the propdef gives no value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, adapter "ProcessDirect" "Sender"]; the caller reads it from Partner Directory CustomXIDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21] and keeps it in property customXIDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L28] | `{{tenant.endpoint.interface.determination.custom}}` |

## Error handling

- The exception subprocess sets the custom status RetryViaParentFlow and ends in error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, step "Set custom status"] [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, property "returnExceptionToSender"].
- Caller side: Step05's Request Reply runs in local process "Call custom extension option" with "Exception Subprocess 2" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 52 "Call custom extension option"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 54 "Exception Subprocess 2"]; whether the template's error reaches it: open questions.
- A reply without `//Interface` ends in Step05 with custom status InterfaceNotFoundError [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 43 "Router"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 44 "Set custom status"].

## Extension points

- The whole determination between "Start" and "End" [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]. Step05 splits the reply by `/ns0:Interfaces/Interface` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 46 "Iterating splitter"], ns0 = http://sap.com/xi/XI/System [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, property "namespaceMapping"], and takes Name as SAP_ReceiverInterface, Index as SAP_ReceiverInterfaceIndex and Service as SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 47 "Get interface"].
- Endpoint: ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, parameter "ProcessDirect_EndPoint"].

## Version notes

2026-09 1.0.0: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, manifest "Bundle-Version"].
