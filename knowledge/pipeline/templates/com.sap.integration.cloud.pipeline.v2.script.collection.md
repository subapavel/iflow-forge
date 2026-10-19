# Pipeline v2 - Script Collection (com.sap.integration.cloud.pipeline.v2.script.collection)

Artifact: `com.sap.integration.cloud.pipeline.v2.script.collection` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Shared Groovy scripts of the templates: a bundle of type ScriptCollection [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "SAP-BundleType"] that provides the capability `scriptcollection.<id>` [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "Provide-Capability"] and holds the 13 scripts listed under Scripts and resources, without BPMN. Eight templates require it with resolution optional: Step01 eo [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, manifest "Require-Capability"], Step01 alo [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, manifest "Require-Capability"], Step03 [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, manifest "Require-Capability"], Step04 .scripts [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, manifest "Require-Capability"], Step04 .xrd [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, manifest "Require-Capability"], Step07 p2p.to.idoc [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, manifest "Require-Capability"], Step07 p2p.to.xi [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, manifest "Require-Capability"] and the custom exit [com.sap.integration.cloud.pipeline.v2.template.custom.exit, manifest "Require-Capability"]; all but p2p.to.idoc load scripts from it by scriptBundleId in Script steps (Scripts and resources), p2p.to.idoc has no Script step [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, resource "scenarioflows/integrationflow/P2P_ASYNC_0001.iflw"]. No generic iFlow references it: their Script steps have an empty scriptBundleId and use their own script folder, in Step01 XI [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"], Step01 IDoc [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, resource "scenarioflows/integrationflow/PIP_01_InboundProcessing4Scenario1.iflw"], Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, resource "scenarioflows/integrationflow/PIP_02_Generic_InboundProcessing.iflw"], Step02 async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, resource "scenarioflows/integrationflow/PIP_02_Generic_IntegratedMessagingAsync.iflw"], Step02 sync [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, resource "scenarioflows/integrationflow/PIP_02_Generic_IntegratedMessagingSync.iflw"], Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, resource "scenarioflows/integrationflow/PIP_04_Generic_ReceiverDetermination.iflw"], Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, resource "scenarioflows/integrationflow/PIP_05_Generic_InterfaceDetermination.iflw"], Step06 [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, resource "scenarioflows/integrationflow/PIP_02_Generic_InboundProcessing.iflw"], Step08 IDoc [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, resource "scenarioflows/integrationflow/Pipeline v2 Generic Step08 - Outbound Processing for IDoc.iflw"] and Step08 XI [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, resource "scenarioflows/integrationflow/Pipeline v2 Generic Step08 - Outbound Processing for XI.iflw"]. A scenario references this collection when: its flow is a copy of one of these eight templates, whose Script steps name the collection as their script bundle [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "Provide-Capability"].

## Adapters

Not applicable: a script collection has no BPMN.

## Steps

Not applicable: a script collection has no BPMN.

## Headers and properties

### Set

| Name | Kind | Script | Value |
|---|---|---|---|
| calledByStep | MPL custom header property | `addCalledByPipelineStep.groovy` | header customXPipelineStep [scripts/addCalledByPipelineStep.groovy#L11] |
| SAP_SenderOperation | header | `mapMessageTypeToOperation.groovy` | the matching operation [scripts/mapMessageTypeToOperation.groovy#L24] |
| customHeaderProperties | header | `mapMessageTypeToOperation.groovy`; `addCustomHeaderProperties.groovy` | `SAP_SenderOperation:<operation>` appended after a `\|` [scripts/mapMessageTypeToOperation.groovy#L30]; the merged map as Base64 JSON [scripts/addCustomHeaderProperties.groovy#L75] |
| `<key>` | MPL custom header property | `addCustomHeaderProperties.groovy` | each merged entry [scripts/addCustomHeaderProperties.groovy#L69] |
| `customHeader_<key>` | property | `createPropertyFromCustomHeaderProperties.groovy` | each entry of customHeaderProperties [scripts/createPropertyFromCustomHeaderProperties.groovy#L38] |
| SAP_ApplicationID | header | `removeLeadingZerosFromApplicationID.groovy` | without leading zeros [scripts/removeLeadingZerosFromApplicationID.groovy#L8] |
| `pidStringParameter_<key>` | property | `setPropertyFromPdStringParameter.groovy` | the Partner Directory value [scripts/setPropertyFromPdStringParameter.groovy#L31] |
| auditLogHeader | header | `auditLogHandler.groovy` via `PipelineLogger.groovy` | the audit log [scripts/auditLogHandler.groovy#L30] [scripts/auditLogHandler.groovy#L40] [scripts/PipelineLogger.groovy#L25] [scripts/PipelineLogger.groovy#L69] |
| body | message | `customReceiverDeterminationViaXProperties.groovy`; `customInterfaceDeterminationViaXProperties.groovy` | Receivers XML [scripts/customReceiverDeterminationViaXProperties.groovy#L63] [scripts/customInterfaceDeterminationViaXProperties.groovy#L126] |

### Read

Everything the scripts read comes from the calling flow.

| Name | Kind | Script | Used for |
|---|---|---|---|
| customXPipelineStep | header | `addCalledByPipelineStep.groovy` | calledByStep [scripts/addCalledByPipelineStep.groovy#L8] |
| messageTypeFromXMLRoot, `SAP-MT2OP-<operation>` | property | `mapMessageTypeToOperation.groovy` | operation lookup [scripts/mapMessageTypeToOperation.groovy#L10] [scripts/mapMessageTypeToOperation.groovy#L16] |
| customHeaderProperties | header | `mapMessageTypeToOperation.groovy`, `addCustomHeaderProperties.groovy`, `createPropertyFromCustomHeaderProperties.groovy` | existing entries [scripts/mapMessageTypeToOperation.groovy#L27] [scripts/addCustomHeaderProperties.groovy#L11] [scripts/createPropertyFromCustomHeaderProperties.groovy#L8] |
| `customHeader_<name>` | property | `addCustomHeaderProperties.groovy` | merged under `<name>` [scripts/addCustomHeaderProperties.groovy#L58] |
| `SAP-DYNREC-<receiver>`, SAP-OperationSpecific, SAP-Operation | property | `customReceiverDeterminationViaXProperties.groovy` | receiver conditions [scripts/customReceiverDeterminationViaXProperties.groovy#L8] [scripts/customReceiverDeterminationViaXProperties.groovy#L10] [scripts/customReceiverDeterminationViaXProperties.groovy#L14] |
| SAP-IFDET-, SAP-IFNAME-, SAP-IFENDPT- keys, SAP-NOREC-Type, SAP-NOREC-Default, SAP-Operation; body | property; message | `customInterfaceDeterminationViaXProperties.groovy` | interface conditions, names, addresses, no-receiver rule [scripts/customInterfaceDeterminationViaXProperties.groovy#L18] [scripts/customInterfaceDeterminationViaXProperties.groovy#L60] [scripts/customInterfaceDeterminationViaXProperties.groovy#L62] [scripts/customInterfaceDeterminationViaXProperties.groovy#L21] [scripts/customInterfaceDeterminationViaXProperties.groovy#L22] [scripts/customInterfaceDeterminationViaXProperties.groovy#L15]; Receivers XML [scripts/customInterfaceDeterminationViaXProperties.groovy#L11] |
| SAP_ApplicationID | header | `removeLeadingZerosFromApplicationID.groovy` | [scripts/removeLeadingZerosFromApplicationID.groovy#L5] |
| pidToLookup; partnerID; `pidStringParameter_<key>` | property; header; property | `setPropertyFromPdStringParameter.groovy` | partner ID, else header partnerID [scripts/setPropertyFromPdStringParameter.groovy#L12]; keys to look up [scripts/setPropertyFromPdStringParameter.groovy#L23] |
| exceptionMessage, exceptionType, location | property | `throwException.groovy` | the exception [scripts/throwException.groovy#L6] [scripts/throwException.groovy#L7] [scripts/throwException.groovy#L8] |
| mplAttachmentName, mplAttachmentMediaType, messageText, location | property | `auditLogHandler.groovy` | attachment and entry [scripts/auditLogHandler.groovy#L8] [scripts/auditLogHandler.groovy#L9] [scripts/auditLogHandler.groovy#L13] [scripts/auditLogHandler.groovy#L14] |
| mplLoggingConfiguration, mplAttachmentName, mediaType | property | `logMessage.groovy` | attachments [scripts/logMessage.groovy#L12] [scripts/logMessage.groovy#L13] [scripts/logMessage.groovy#L14] |
| auditLogHeader | header | `PipelineLogger.groovy` | existing log continued [scripts/PipelineLogger.groovy#L29] |

## Partner directory

- `setPropertyFromPdStringParameter.groovy`: String parameter `<key>` of the partner ID in property pidToLookup, else header partnerID, for each property `pidStringParameter_<key>` [scripts/setPropertyFromPdStringParameter.groovy#L12] [scripts/setPropertyFromPdStringParameter.groovy#L29]; a missing value leaves the property as it is [scripts/setPropertyFromPdStringParameter.groovy#L31]; without the service or a partner ID the message is returned unchanged [scripts/setPropertyFromPdStringParameter.groovy#L15].
- No other script reads the Partner Directory.

## Scripts and resources

| File | Referenced by | Purpose |
|---|---|---|
| `PipelineHelper.groovy` | imported by three scripts [scripts/addCustomHeaderProperties.groovy#L2] [scripts/createPropertyFromCustomHeaderProperties.groovy#L2] [scripts/logMessage.groovy#L3] | static helpers: Base64 JSON to map [scripts/PipelineHelper.groovy#L15], `key:value` list check [scripts/PipelineHelper.groovy#L34], map as text [scripts/PipelineHelper.groovy#L53] |
| `PipelineLogger.groovy` | imported [scripts/auditLogHandler.groovy#L2] [scripts/logMessage.groovy#L2] | audit log in header `<type>Header` [scripts/PipelineLogger.groovy#L25]; entries `<TYPE>\|<GMT time>\|<location>\|<text>` [scripts/PipelineLogger.groovy#L49] joined by `<EOR>`, or `;;` when the existing log uses it [scripts/PipelineLogger.groovy#L32] [scripts/PipelineLogger.groovy#L34] |
| `addCalledByPipelineStep.groovy` | [com.sap.integration.cloud.pipeline.v2.template.custom.exit, step "Add called by step"] | logs the calling pipeline step [scripts/addCalledByPipelineStep.groovy#L11] |
| `addCustomHeaderProperties.groovy` | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, step "Add custom header properties"] | merges custom header entries into MPL custom headers [scripts/addCustomHeaderProperties.groovy#L69] |
| `auditLogHandler.groovy` | — | attachAuditLog attaches the audit log to the MPL [scripts/auditLogHandler.groovy#L22]; addAuditLog adds messageText and saves the header [scripts/auditLogHandler.groovy#L36] [scripts/auditLogHandler.groovy#L40] |
| `createPropertyFromCustomHeaderProperties.groovy` | — | custom header entries to properties [scripts/createPropertyFromCustomHeaderProperties.groovy#L38] |
| `customInterfaceDeterminationViaXProperties.groovy` | [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Add interfaces to XML"] [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, step "Merge interfaces with XML Receivers"] | rebuilds the body as a new Receivers XML [scripts/customInterfaceDeterminationViaXProperties.groovy#L72] [scripts/customInterfaceDeterminationViaXProperties.groovy#L126]: only input receivers named in SAP-IFDET- keys [scripts/customInterfaceDeterminationViaXProperties.groovy#L52] [scripts/customInterfaceDeterminationViaXProperties.groovy#L106], with Interfaces whose Service is the SAP-IFENDPT- value [scripts/customInterfaceDeterminationViaXProperties.groovy#L62], and ReceiverNotDetermined when none of them has a true interface condition [scripts/customInterfaceDeterminationViaXProperties.groovy#L65] [scripts/customInterfaceDeterminationViaXProperties.groovy#L77] |
| `customReceiverDeterminationViaXProperties.groovy` | [com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, step "Create Receivers XML"] | one Receiver per SAP-DYNREC- property that is true or empty [scripts/customReceiverDeterminationViaXProperties.groovy#L38] |
| `logMessage.groovy` | — | MPL attachments of payload, headers, properties or audit log; default AuditLog [scripts/logMessage.groovy#L12] [scripts/logMessage.groovy#L30] [scripts/logMessage.groovy#L39] [scripts/logMessage.groovy#L47] [scripts/logMessage.groovy#L54] |
| `mapMessageTypeToOperation.groovy` | [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, step "Map Message Type to Operation"] [com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, step "Map Message Type to Operation"] [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, step "Map Message Type to Operation"] | operation whose SAP-MT2OP- property equals messageTypeFromXMLRoot [scripts/mapMessageTypeToOperation.groovy#L19] |
| `removeLeadingZerosFromApplicationID.groovy` | — | strips leading zeros [scripts/removeLeadingZerosFromApplicationID.groovy#L8] |
| `setPropertyFromPdStringParameter.groovy` | — | Partner Directory String parameters to properties [scripts/setPropertyFromPdStringParameter.groovy#L31] |
| `throwException.groovy` | — | throws a configurable exception [scripts/throwException.groovy#L34] |

"—": no Script step of the label names the script with this collection's scriptBundleId.

## Externalized parameters

None: a script collection has no parameters.propdef.

## Error handling

- `throwException.groovy` always throws: IllegalArgumentException, RuntimeException, IOException or NullPointerException per exceptionType, else Exception [scripts/throwException.groovy#L19] [scripts/throwException.groovy#L31] [scripts/throwException.groovy#L34]; the message is exceptionMessage or the body, plus " at <location>." [scripts/throwException.groovy#L12] [scripts/throwException.groovy#L14].
- Parse failures of customHeaderProperties give an empty map, no error [scripts/addCustomHeaderProperties.groovy#L38] [scripts/createPropertyFromCustomHeaderProperties.groovy#L33]; a failing MPL write is ignored [scripts/addCustomHeaderProperties.groovy#L77].
- PipelineHelper.decodeIfBase64 returns the decoded text when it is not JSON and the input when decoding fails [scripts/PipelineHelper.groovy#L20] [scripts/PipelineHelper.groovy#L23].
- `setPropertyFromPdStringParameter.groovy` returns early instead of failing [scripts/setPropertyFromPdStringParameter.groovy#L15].

## Extension points

- Declarative Partner Directory lookup: a flow sets properties `pidStringParameter_<key>` with dummy values and calls `setPropertyFromPdStringParameter.groovy` [scripts/setPropertyFromPdStringParameter.groovy#L9].
- Monitoring: properties `customHeader_<name>` become MPL custom headers through `addCustomHeaderProperties.groovy` [scripts/addCustomHeaderProperties.groovy#L58].
- Logging: property mplLoggingConfiguration selects the attachments of `logMessage.groovy` [scripts/logMessage.groovy#L12].
- Errors: properties exceptionType, exceptionMessage, location shape `throwException.groovy` [scripts/throwException.groovy#L6] [scripts/throwException.groovy#L7] [scripts/throwException.groovy#L8].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
