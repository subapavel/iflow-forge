<!-- Translated from Confluence page "Pipeline v2 – Script Collection (přehled)" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Pipeline v2 – Script Collection (overview)

Artifact: ScriptCollection · Version: 1.0.0 · scriptBundleId: com.sap.integration.cloud.pipeline.v2.script.collection
Deployment: once per tenant, without changes. It must be deployed before the template copies that reference it cross-bundle.

## What the Script Collection is for

The generic pipeline flows have Groovy scripts bundled directly in the iflow as local resources. The templates (scenario-specific flows), on the other hand, do not contain their own copies of the scripts — they reference them as shared from this collection (cross-bundle reference). Thanks to this, a script fix is deployed once and applies to all template copies.

## Script overview

| Script | Used by | Purpose |
|---|---|---|
| mapMessageTypeToOperation.groovy | Step01 Exactly Once / At Least Once, Step03 | Mapping the message type from the XML root to an operation via properties SAP-MT2OP-<Operation> → header SAP_SenderOperation |
| customReceiverDeterminationViaXProperties.groovy | Step04 via Exchange Properties | Evaluation of Boolean properties SAP-DYNREC-<Receiver> → building the Receivers XML; support for operation-specific determination |
| customInterfaceDeterminationViaXProperties.groovy | Step04 via Exchange Properties, Step04 via XRD | Adding interfaces from SAP-IFDET-* / SAP-IFENDPT-* / SAP-IFNAME-*; handles SAP-NOREC-* and the default interface |
| addCalledByPipelineStep.groovy | Custom Exit | MPL custom header calledByStep from the header customXPipelineStep |
| setPropertyFromPdStringParameter.groovy | optionally anywhere | Declarative PD lookup: properties pidStringParameter_<name> → loading the PD string parameters of the same name |
| createPropertyFromCustomHeaderProperties.groovy | optionally anywhere | Parsing the header customHeaderProperties into exchange properties customHeader_* |
| addCustomHeaderProperties.groovy | shared | Building custom header properties for MPL monitoring (identical to the version in the generic flows) |
| removeLeadingZerosFromApplicationID.groovy | shared | Removing leading zeros from SAP_ApplicationID |
| throwException.groovy | shared | An exception of the type given by exceptionType with the message from exceptionMessage |
| auditLogHandler.groovy | utility | Audit log handling (identical to the version in the generic flows) |
| logMessage.groovy | utility | Message logging (identical to the version in the generic flows) |
| PipelineHelper.groovy | library | Static helpers: Base64/JSON decoding, validation of key:value strings |
| PipelineLogger.groovy | library | Structured audit log (addEntry / saveToMessage); imported by the other scripts |

## Deployment notes

- The collection is deployed once and without changes — no copy per scenario.
- Deployment order: Script Collection → only then the template copies (otherwise the reference fails).
- The source of truth is the package export / tenant; this page is a read-only copy. The version of the package the code comes from is stated on the page of each script.
