# Pipeline v2 Template Step04 - Custom Receiver and Interface Determination via Exchange Properties (com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts` 1.0.1, package templates, label 2026-06-trial.

## Purpose

Scenario-specific receiver and interface determination without a mapping: a ProcessDirect sender on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, adapter "ProcessDirect" "Sender"], XPath conditions in exchange properties (step 1), and two scripts of the script collection that turn them into a Receivers XML with interfaces [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L63] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L126]. Generic Step04 takes the endpoint from header customXRDEndpoint, else Partner Directory CustomXRDEndpoint, else ReuseXRDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35], branches on customXRDBoolean [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 32 "Custom RD?"] and calls it by Request Reply (step 71) to `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"]; the integrated runtimes do the same, async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverXRD"] and sync [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 33 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ReceiverXRD"]. A reply with interfaces makes Step04 bypass Step05 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 51 "Bypass interface determination?"]. A scenario copies this template when: receivers and their interfaces are chosen by XPath conditions on the payload, one per receiver and per interface, realised by the properties of step 1 and the scripts of steps 2 and 3 [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Create Receivers XML"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Add interfaces to XML"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}`, to start event "Start" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, adapter "ProcessDirect" "Sender"] |

## Steps

1. Enricher "Define receiver and interface conditions": nine properties with SAP's placeholder text as values (Headers and properties); body type constant, empty
2. Script "Create Receivers XML": `customReceiverDeterminationViaXProperties.groovy` from the script collection; body = Receivers XML of the SAP-DYNREC- conditions that are true or empty [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L33] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L63]
3. Script "Add interfaces to XML": `customInterfaceDeterminationViaXProperties.groovy` from the script collection; body = Receivers XML with the interfaces of the true SAP-IFDET- conditions, then "End" [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L63] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L126] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
4. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1", step 5, error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
  5. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| `SAP-DYNREC-<Receiver name>` | property | 1 | XPath, Boolean: receiver condition [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| `SAP-IFDET-<Receiver name>~IFIndex<Index number>` | property | 1 | XPath, Boolean: interface condition [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| `SAP-IFENDPT-<Receiver name>~IFIndex<Index number>` | property | 1 | constant: endpoint of the scenario-specific outbound flow [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| `SAP-IFNAME-<Receiver name>~IFIndex<Index number>` | property | 1 | constant: receiver interface name, optional [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| SAP-NOREC-Type, SAP-NOREC-Default | property | 1 | `Ignore`, `Error` or `Default`; the default receiver name [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| `SAP-IFDET-Default~IFIndex<Index number>`, same with SAP-IFENDPT-, SAP-IFNAME- | property | 1 | optional interfaces of the default receiver [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| body | — | 2, 3 | Receivers XML (Purpose) |
| SAP_MessageProcessingLogCustomStatus | property | 5 | RetryViaParentFlow, on error only [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Set custom status"] |

### Read

Besides the payload, the caller's headers SAP_Sender(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*) and tenantStage may enter the flow [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, property "allowedHeaderList"]; no step reads them.

| Name | Kind | Step | Used for |
|---|---|---|---|
| payload | body | 1 | the XPath conditions [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] |
| SAP-OperationSpecific | property | 2 | operation-specific keys if "true" [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L10] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L24]; no step of the template sets it [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] (open questions) |
| SAP-Operation | property | 2, 3 | operation filter [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L14] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L15]; no step of the template sets it [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"] (open questions) |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `customReceiverDeterminationViaXProperties.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 2 | one Receiver per property containing `SAP-DYNREC-` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L8] that is true or empty [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L33]: empty Party, Service = text after the last "-" of the key, Operation = SAP-Operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L20] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L53] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L36]; if SAP-OperationSpecific is "true" the key is `<operation>~<receiver>` and other operations are skipped [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L24] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L28] |
| `customInterfaceDeterminationViaXProperties.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 3 | reads the body as Receivers XML [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L12]; per `SAP-IFDET-` key `[<operation>~]<receiver>~IFIndex<n>` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L34] an Interface with Index n, Service = the SAP-IFENDPT- value, Name = the SAP-IFNAME- value [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L47] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L60] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L62]; writes only receivers of the XML that have such a key, with Service and Interfaces, without Party and Operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L52] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L105] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L107]; if no receiver of the XML has a true interface condition, adds ReceiverNotDetermined with Type = SAP-NOREC-Type and, for Default, DefaultReceiver = SAP-NOREC-Default with the interfaces of that receiver's keys or of the `Default` keys [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L64] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L76] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L85] |
| xsd/Receivers.xsd | — | Receivers structure, namespace http://sap.com/xi/XI/System; its Receiver has Party, Service, Interfaces and no Operation element, which step 2 writes [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, resource "xsd/Receivers.xsd"] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customReceiverDeterminationViaXProperties.groovy#L55] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

Both scripts are referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Create Receivers XML"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Add interfaces to XML"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, manifest "Require-Capability"]. Source note: step 3's script assigns receiverOperation without a declaration and does not reset it per key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L35], so a key without operation after one with operation is compared with the earlier operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L43].

## Externalized parameters

Bold: the scenario author must set it; the propdef gives no value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender, the value the caller finds as customXRDEndpoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L43] | `{{tenant.endpoint.receiver.determination.custom}}` |

## Error handling

- The exception subprocess sets the custom status RetryViaParentFlow and ends in error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Set custom status"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, property "returnExceptionToSender"].
- Caller side: Step04's Request Reply runs in a local process with "Exception Subprocess 3" (custom error handling if CustomXError_Enabled is 'true', else standard retry handling) [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 75 "Exception Subprocess 3"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 76 "Custom Extension?"]; whether the template's error reaches it: open questions.
- Not-found handling is the caller's: Step04 reads `ReceiverNotDetermined/Type` Default, Ignore or else error [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 43 "Not found type?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 67 "Set custom status"].

## Extension points

- The rows of step 1: SAP's placeholder text such as `<receiver xpath condition>` and `<endpoint of scen-spec outbound flow>`, one row set per receiver and interface [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Define receiver and interface conditions"]. The SAP-IFENDPT- value becomes Interface/Service, which Step04 uses as the outbound processing endpoint [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 57 "Get interface"].
- Operation-specific conditions via SAP-OperationSpecific and SAP-Operation (Scripts and resources).
- Endpoint: ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, parameter "ProcessDirect_EndPoint"].

## Version notes

2026-09-current 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, manifest "Bundle-Version"].
