# Pipeline v2 Template Step04 - Custom Receiver and Interface Determination via Extended Receiver Determination (com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd` 1.0.1, package templates, label 2026-06.

## Purpose

Scenario-specific receiver and interface determination in which a message mapping yields the receivers and XPath conditions in exchange properties yield their interfaces: a ProcessDirect sender on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, adapter "ProcessDirect" "Sender"], the conditions (step 1), a Mapping step without a mapping (step 2) and the script collection's interface script (step 3) [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L126]. Generic Step04 takes the endpoint from header customXRDEndpoint, else Partner Directory CustomXRDEndpoint, else ReuseXRDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35], branches on customXRDBoolean [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 32 "Custom RD?"] and calls it by Request Reply (step 71) to `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"]; the integrated runtimes do the same, async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverXRD"] and sync [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 33 "Custom RD?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ReceiverXRD"]. A scenario copies this template when: the receivers come from a message mapping of an extended receiver determination and the interfaces from XPath conditions, so that the reply carries both, realised by Mapping "Map to Receivers" followed by script "Merge interfaces with XML Receivers" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Map to Receivers"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Merge interfaces with XML Receivers"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}`, to start event "Start" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, adapter "ProcessDirect" "Sender"] |

## Steps

1. Enricher "Define interface conditions": eight properties with SAP's placeholder text as values (Headers and properties); body type constant, empty; runs before the mapping [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
2. Mapping "Map to Receivers": message mapping, static reference, no mapping named (mappinguri, mappingname empty)
3. Script "Merge interfaces with XML Receivers": `customInterfaceDeterminationViaXProperties.groovy` from the script collection; body = the mapping's Receivers XML with the interfaces of the true SAP-IFDET- conditions, then "End" [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L63] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L126] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
4. ErrorEventSubProcessTemplate "Exception Subprocess": "Error Start 1", step 5, error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
  5. Enricher "Set custom status": property SAP_MessageProcessingLogCustomStatus = RetryViaParentFlow

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| `SAP-IFDET-<Receiver name>~IFIndex<Index number>` | property | 1 | XPath, Boolean: interface condition [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| `SAP-IFENDPT-<Receiver name>~IFIndex<Index number>` | property | 1 | constant: endpoint of the scenario-specific outbound flow [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| `SAP-IFNAME-<Receiver name>~IFIndex<Index number>` | property | 1 | constant: receiver interface name, optional [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| SAP-NOREC-Type, SAP-NOREC-Default | property | 1 | `Ignore`, `Error` or `Default`; the default receiver name [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| `SAP-IFDET-Default~IFIndex<Index number>`, same with SAP-IFENDPT-, SAP-IFNAME- | property | 1 | optional interfaces of the default receiver [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| body | — | 2, 3 | Receivers XML of the mapping, then with interfaces [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L12] |
| SAP_MessageProcessingLogCustomStatus | property | 5 | RetryViaParentFlow, on error only [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Set custom status"] |

### Read

Besides the payload, the caller's headers SAP_Sender(.*), SAP_MessageType, SAP_ApplicationID, customX(.*), testMode, partnerID, customHeaderProperties, dc(.*) and tenantStage may enter the flow [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, property "allowedHeaderList"]; no step reads them.

| Name | Kind | Step | Used for |
|---|---|---|---|
| payload | body | 1, 2 | the XPath conditions, the mapping's source [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] |
| Receivers XML | body | 3 | `Receiver/Service` matched against the receiver part of each SAP-IFDET- key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L52] |
| SAP-Operation | property | 3 | operation filter [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L15] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L43]; no step of the template sets it [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"] (open questions) |

## Partner directory

None found in the artifact.

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `customInterfaceDeterminationViaXProperties.groovy` of `com.sap.integration.cloud.pipeline.v2.script.collection` | 3 | per `SAP-IFDET-` key `[<operation>~]<receiver>~IFIndex<n>` [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L34] an Interface with Index n, Service = the SAP-IFENDPT- value, Name = the SAP-IFNAME- value [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L47] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L60] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L62]; writes only receivers of the XML that have such a key, with Service and Interfaces, without Party [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L105] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L107]; if no receiver of the XML has a true interface condition, adds ReceiverNotDetermined with Type = SAP-NOREC-Type and, for Default, DefaultReceiver = SAP-NOREC-Default with the interfaces of that receiver's keys or of the `Default` keys [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L64] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L76] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L85]; referenced by scriptBundleId [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Merge interfaces with XML Receivers"] and an optional Require-Capability [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, manifest "Require-Capability"] |
| xsd/Receivers.xsd | — | Receivers structure in namespace http://sap.com/xi/XI/System (ReceiverNotDetermined, Receiver with Party, Service, Interfaces) [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "xsd/Receivers.xsd"] |
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

Source note: the script assigns receiverOperation without a declaration and does not reset it per key [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L35], so a key without operation after one with operation is compared with the earlier operation [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L43].

## Externalized parameters

Bold: the scenario author must set it; the propdef gives no value or description.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender, the value the caller finds as customXRDEndpoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, parameter "ProcessDirect_EndPoint"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L43] | `{{tenant.endpoint.receiver.determination.custom}}` |

## Error handling

- The exception subprocess sets the custom status RetryViaParentFlow and ends in error end event "Error End 1" [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Set custom status"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, property "returnExceptionToSender"].
- Caller side: Step04's Request Reply runs in a local process with "Exception Subprocess 3" (custom error handling if CustomXError_Enabled is 'true', else standard retry handling) [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 75 "Exception Subprocess 3"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 76 "Custom Extension?"]; whether the template's error reaches it: open questions.
- Not-found handling is the caller's: Step04 reads `ReceiverNotDetermined/Type` Default, Ignore or else error [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 43 "Not found type?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 67 "Set custom status"].

## Extension points

- The message mapping of step 2: none is referenced [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Map to Receivers"]; its result must be the Receivers XML the script parses [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/customInterfaceDeterminationViaXProperties.groovy#L12].
- The rows of step 1, SAP's placeholder text such as `<xpath condition>` and `<endpoint of scen-spec outbound flow>` [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Define interface conditions"]; the SAP-IFENDPT- value becomes Interface/Service, the outbound processing endpoint in Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 57 "Get interface"].
- Endpoint: ProcessDirect_EndPoint [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, parameter "ProcessDirect_EndPoint"].

## Version notes

2026-09 1.0.1: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09: com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, manifest "Bundle-Version"].
