# Pipeline v2 Template Step04 - Reuse Extended Receiver Determination (com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Receiver determination of the fully decoupled pipeline by one message mapping: a ProcessDirect sender on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, adapter "ProcessDirect" "Sender"] and a Mapping step without a mapping [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Message Mapping"]. Generic Step04 reads Partner Directory ReuseXRDEndpoint only when neither header customXRDEndpoint nor Partner Directory CustomXRDEndpoint is set, and then sets reuseXRDBoolean [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L38]; it branches on it [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 32 "Custom RD?"], calls the copy by Request Reply (step 71) to `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"] and completes the reply itself: for reuseXRDBoolean it reads the not-determined behaviour from the Partner Directory [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 72 "Reuse XRD?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L21] and maps the reply with message mapping AddReceiverNotDeterminedNode [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 74 "Map to Receivers"]. The integrated runtimes stop on reuseXRDBoolean with custom status ConfigurationOptionNotSupported, "Extended Receiver Determination option not supported in integrated messaging runtime" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L38] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 49 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readCustomReceiverDeterminationFromPD.groovy#L38] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 33 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 35 "Set custom status"]. A scenario copies this template when: the receivers of an asynchronous scenario on the fully decoupled pipeline come from a message mapping alone, with the not-determined behaviour left to Step04, realised by the single Mapping step [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Message Mapping"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 74 "Map to Receivers"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverDeterminationQueue"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}`, to start event "Start" [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, adapter "ProcessDirect" "Sender"] |

## Steps

1. Mapping "Message Mapping": message mapping, static reference, no mapping named (mappinguri, mappingname empty); "Start" to step 1 to "End" [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
2. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1", step 3, error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
  3. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| body | — | 1 | result of the mapping the scenario adds [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Message Mapping"] |
| SAP_MessageProcessingLogCustomStatus | property | 3 | RetryViaParentFlow, on error only [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Set custom status"] |

### Read

Besides the payload, the caller's headers SAP_Sender(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*) and tenantStage may enter the flow [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, property "allowedHeaderList"]; no step reads them.

| Name | Kind | Step | Used for |
|---|---|---|---|
| payload | body | 1 | the mapping's source [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Message Mapping"] |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| wsdl/Receivers.wsdl | — | message Receivers in namespace http://sap.com/xi/XI/System with ESR version annotations: ReceiverNotDetermined (Type, DefaultReceiver), Receiver with Party, Service and one Interface string [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "wsdl/Receivers.wsdl"] |
| xsd/Receivers.xsd | — | the same root, but Receiver with Party, Service and Interfaces/Interface (Index, Service, Name) [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "xsd/Receivers.xsd"] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

No script step [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]. Which of the two structures the mapping should target is not stated (open questions).

## Externalized parameters

Bold: the scenario author must set it; the propdef gives no value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender, the value the caller finds as ReuseXRDEndpoint [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35] | `{{tenant.endpoint.receiver.determination.reuse}}` |

## Error handling

- The exception subprocess sets the custom status RetryViaParentFlow and ends in error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Set custom status"] [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, property "returnExceptionToSender"].
- Caller side: Step04's Request Reply and the follow-up mapping run in a local process with "Exception Subprocess 3" (custom error handling if CustomXError_Enabled is 'true', else standard retry handling) [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 75 "Exception Subprocess 3"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 76 "Custom Extension?"]; whether the template's error reaches it: open questions.
- Not determined: Partner Directory ReceiverNotDeterminedType of partnerID, 'Error' if missing [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L25]; for 'Default' ReceiverNotDeterminedDefault, exception if missing [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L29] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L31]; the mapping AddReceiverNotDeterminedNode reads both values [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, resource "mapping/AddReceiverNotDeterminedNode.mmap"].

## Extension points

- The message mapping of step 1: none is referenced [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, step "Message Mapping"]. Step04 then splits `ns0:Receivers/Receiver` and takes `Receiver/Service` as the receiver [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 68 "Iterating splitter"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 69 "Get receiver name"], ns0 = http://sap.com/xi/XI/System [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, property "namespaceMapping"].
- Endpoint: ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, parameter "ProcessDirect_EndPoint"].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
