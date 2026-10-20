# Pipeline v2 Generic Step08 - Outbound Processing for XI (com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi` 1.0.2, package generic-outbound, label 2026-06-trial.

## Purpose

Generic XI receiver step at `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "ProcessDirect" "Pipeline"]; the p2p-to-XI template calls `{{GenericXIEndpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, parameter "GenericXIEndpoint"], equal only by configuration (pitfalls). It builds the XI connectivity and the sender, receiver and interface identity from scenario headers, Partner Directory and parameters, removes the pipeline headers (step 10) and ends on one of six XI receiver channels chosen by proxy and authentication type (step 11). The pipeline headers must not reach the receiver [rationale, from analysis 2026]. The template com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi calls it at `{{GenericXIEndpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi, adapter "ProcessDirect" "Receiver"]; Step06 [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], fed also by Step02 async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"], calls Step07 at `${header.SAP_OutboundProcessingEndpoint}`.

## Adapters

All receivers: address `${property.Receiver_XIEndpoint}`; sender, receiver, parties, interface and namespace from properties Sender, SenderParty, Receiver, ReceiverParty, ReceiverInterface, ReceiverInterfaceNamespace; XI message id from header UniqueID; fixed RetryInterval 1, MaxRetryInterval 60, ExponentialBackoff, dead letter queue, DataStore storage, ExpirationPeriod 30; `{{XI_*}}` HTTP settings. On-Premise ones proxy sapcc with `${property.Receiver_XICCLocationId}`. Receiver cells: end event, authentication, quality of service.

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Pipeline | `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "ProcessDirect" "Pipeline"] |
| Receiver | XI | OnPremise | "End": Basic, `${property.Receiver_XICredentialName}`, HandledByIflow [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"] |
| Receiver | XI | OnPremise | "End 2": None, HandledByIflow [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremNone" "OnPremise"] |
| Receiver | XI | OnPremise | "End 3": Principal Propagation, BestEffort [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremPrincipalPropagation" "OnPremise"] |
| Receiver | XI | Internet | "End 4": Basic, `${property.Receiver_XICredentialName}`, HandledByIflow [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_InternetBasic" "Internet"] |
| Receiver | XI | Internet | "End 5": Client Certificate, `${property.Receiver_XIPrivateKeyAlias}`, HandledByIflow [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_InternetCertificate" "Internet"] |
| Receiver | XI | Internet | "End 6": None, HandledByIflow [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_InternetNone" "Internet"] |

## Steps

1. Script "Create dynamic configurations": `createSapDynamicConfigurationFromHeaders.groovy`
2. Enricher "Set Default Properties": parameter defaults, customHeader_* copies
3. Script "Check if XML body": `checkWellFormedXML.groovy`
4. ExclusiveGateway "XML?": isWellFormedXml false to 6, else 5
5. Enricher "Set XPath Properties": default_ReceiverInterface, default_ReceiverInterfaceNamespace from the root element
6. Script "Read PD values": `readPDValuesForXI.groovy`
7. Script "Set values for XI as properties": `setValuesForXI.groovy`
8. Script "Add Custom Headers": `addCustomHeaderProperties.groovy`
9. Enricher "Backup Log headers": auditLogProperty
10. Enricher "Remove headers": dc_*, customX*, customHeaderProperties, auditLogHeader, SAP_*
11. ExclusiveGateway "Location & Auth?": Receiver_XIProxyType and Receiver_XIAuthenticationType (On-Premise Principal Propagation only with SapQualityOfService BestEffort) to the end events, else 12
12. Enricher "Set custom status": NotExpectedConfigurationOption
13. Script "Throw Exception": `throwException.groovy`, "Error End 2"
14. ErrorEventSubProcessTemplate "Exception Subprocess"
  15. ExclusiveGateway "Audit Log Header?": auditLogHeader set to 17, else 16
  16. Enricher "Recover Audit Log": from property auditLogProperty
  17. Enricher "Add Exception to the Log": `{{Exception_LoggingConfiguration}}`, exception message, attachment ExceptionSubProcess_
  18. Script "Save in Audit Log": `auditLogHandler.groovy` addAuditLog
  19. Script "Log Message per Config": `logMessage.groovy`, "Error End 1"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SapDynamicConfiguration_<key> | property | 1 | from header dc_SapDynamicConfiguration_<key> [scripts/createSapDynamicConfigurationFromHeaders.groovy#L26]; the XI receiver adapter adds them as dynamic XI headers [generic-outbound/CHANGELOG.txt#L7] |
| default_* | property | 2, 5, 6 | parameters [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Set Default Properties"], root element [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Set XPath Properties"], PD (Partner directory) |
| isWellFormedXml | property | 3 | [scripts/checkWellFormedXML.groovy#L11] |
| SAP_Receiver, SAP_Sender | header | 6 | if missing: default_Receiver, default_Sender [scripts/readPDValuesForXI.groovy#L17] [scripts/readPDValuesForXI.groovy#L21] |
| SAP_ReceiverInterface, SAP_ReceiverInterfaceNamespace | header | 6 | [scripts/readPDValuesForXI.groovy#L70] [scripts/readPDValuesForXI.groovy#L82] |
| Receiver_XI* (6 keys), SenderParty, Sender, ReceiverParty, Receiver, ReceiverInterface, ReceiverInterfaceNamespace | property | 7 | header SAP_<key>, else default_<key>, else empty [scripts/setValuesForXI.groovy#L42] [scripts/setValuesForXI.groovy#L52]; Sender, Receiver "Party.System" keep the system [scripts/setValuesForXI.groovy#L60] |
| customHeader_SenderParty, _ReceiverParty, _ReceiverInterface, _ReceiverInterfaceNamespace, _SenderSystem, _ReceiverSystem | property | 7 | MPL search values [scripts/setValuesForXI.groovy#L69] [scripts/setValuesForXI.groovy#L61] |
| location, exceptionMessage, SAP_MessageProcessingLogCustomStatus | property | 12 | for `throwException.groovy` [scripts/throwException.groovy#L14] |

### Read

Headers from the calling flow.

| Name | Kind | Step | Used for |
|---|---|---|---|
| dc_SapDynamicConfiguration_* | header | 1 | [scripts/createSapDynamicConfigurationFromHeaders.groovy#L15] |
| SAP_ReceiverAlias, SAP_Receiver, SAP_Sender, partnerID, SAP_MessageType | header | 6 | PD keys; interface fallback [scripts/readPDValuesForXI.groovy#L10] [scripts/readPDValuesForXI.groovy#L58] [scripts/readPDValuesForXI.groovy#L67] |
| SAP_<key> (as step 7) | header | 7 | override [scripts/setValuesForXI.groovy#L42] |
| SapQualityOfService, SapQueueId, UniqueID | header | 2, 11 | MPL; channel choice; XI message id [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Location & Auth?"] |

## Partner directory

In `readPDValuesForXI.groovy`, in this order:

- No PD service: the script returns before every read [scripts/readPDValuesForXI.groovy#L40].
- `<key>_<receiver system>` (String) of PID SAP_ReceiverAlias, keys XIEndpoint, XICCLocationId, XICredentialName, XIPrivateKeyAlias, XIProxyType, XIAuthenticationType, only with SAP_ReceiverAlias and a receiver; missing: parameter default [scripts/readPDValuesForXI.groovy#L48].
- Without header SAP_ReceiverInterface: InterfaceNameDetermination_<SAP_ReceiverAlias, else receiver system> of partnerID [scripts/readPDValuesForXI.groovy#L64]; missing: SAP_MessageType [scripts/readPDValuesForXI.groovy#L67]; then root element name (step 5) [scripts/setValuesForXI.groovy#L21].
- Without header SAP_ReceiverInterfaceNamespace: InterfaceNamespaceDetermination_<interface> of partnerID [scripts/readPDValuesForXI.groovy#L77]; missing: root element namespace (step 5).

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `createSapDynamicConfigurationFromHeaders.groovy` | 1 | dc_ headers to properties [scripts/createSapDynamicConfigurationFromHeaders.groovy#L17] |
| `checkWellFormedXML.groovy` | 3 | XML check [scripts/checkWellFormedXML.groovy#L10] |
| `readPDValuesForXI.groovy`, `setValuesForXI.groovy` | 6, 7 | PD reads; final adapter values [scripts/setValuesForXI.groovy#L65] |
| `addCustomHeaderProperties.groovy`, `throwException.groovy` | 8; 13 | MPL headers; exception [scripts/addCustomHeaderProperties.groovy#L69] [scripts/throwException.groovy#L34] |
| `auditLogHandler.groovy`, `logMessage.groovy` | 18, 19 | audit log; MPL attachments [scripts/auditLogHandler.groovy#L28] [scripts/logMessage.groovy#L11] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| ProcessDirect_EndPoint | — | entry [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, parameter "ProcessDirect_EndPoint"] | `{{tenant.endpoint.xi.outbound}}` |
| XI_Address, LocationID, CredentialName, XI_PrivateKeyAlias | — | receiver, Cloud Connector location, credential, key [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Set Default Properties"] | `{{tenant.xi.receiver.address}}`, `{{tenant.cloudconnector.location}}`, `{{tenant.xi.receiver.credential}}`, `{{tenant.xi.receiver.keyalias}}` |
| XI_Sender, XI_SenderParty; XI_Receiver, XI_ReceiverParty | — | default identities [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, parameter "XI_Sender"] | `{{tenant.xi.sender.default}}`, `{{tenant.xi.receiver.default}}` |
| XI_ProxyType, XI_AuthenticationType | — | channel [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Set Default Properties"] | no |
| XI_CleanUpRequestHeaders, XI_CompressMessage, XI_ReturnHTTPResponseCodeAsHeader, XI_Timeout, XI_AllowChunking, XI_KeepAlive | — | HTTP, all receivers [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"] | no |
| AllowedHeadersList, HTTPSessionReuse | — | [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, property "httpSessionHandling"] | no |
| Exception_LoggingConfiguration | — | error attachments [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Add Exception to the Log"] | no |

## Error handling

- One event subprocess: audit log restored from property auditLogProperty if the header is gone, exception text and attachments, then "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step "Log Message per Config"].
- Raised here: unsupported proxy/authentication, or Principal Propagation without BestEffort (NotExpectedConfigurationOption, step 11).
- No retry or DLQ step; the receiver channels carry fixed retry and dead letter settings [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"]; how they and the caller act on errors is not visible (pitfalls).

## Extension points

- Scenario headers SAP_<key> override connectivity and identity [scripts/setValuesForXI.groovy#L42]; dc_SapDynamicConfiguration_* headers [scripts/createSapDynamicConfigurationFromHeaders.groovy#L7]; PD per receiver alias and partner ID (Partner directory).
- AllowedHeadersList "Enhance allowed header list to your needs" [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, parameter "AllowedHeadersList"].

## Version notes

- 2026-09-current 1.0.4: "Reuse sender id" reuses SAP_SenderMessageIdEx only when PassMessageIdFromE2E = 'true', the value is a valid UUID and there is no split [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 10 "Reuse sender id"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L38] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L39]; else, with a UniqueID, it sets MapUniqueIdToMessageIdEx [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L48] and "Reuse sender id?" routes to "Map unique Id" [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 11 "Reuse sender id?"], which maps UniqueID + SAP_ReceiverInterface to SapMessageIdEx [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L7] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 17 "Map unique Id"]; the six XI receiver channels use XiMessageIdDetermination Reuse, e.g. [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"] — see versions/2026-09-current/changelog.md
- 2026-09-current 1.0.4: the ID Mapper source is UniqueID + SAP_ReceiverInterface cut at 120, the context 'C' + the namespace's alphanumerics (else IDMapper1) cut at 40 [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L7] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L12] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L14] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L17] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L18] — see versions/2026-09-current/changelog.md
- 2026-09-current 1.0.4: "Override Application ID" sets SAP_ApplicationID from SapMessageIdEx [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 18 "Override Application ID"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/overrideSAPApplicationId.groovy#L9] — see versions/2026-09-current/changelog.md
- 2026-09-current 1.0.4: readPDValuesForXI reads keys first from the business-system partner ID and fills absent SAP_Receiver_<key> from scenario keys [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L54] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L101] — see versions/2026-09-current/changelog.md
- 2026-09-current 1.0.4: logMessage logs an exception only when the configuration names Exception or Error [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/logMessage.groovy#L63]; adding "exception" on a caught exception is commented out [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/logMessage.groovy#L29] — see versions/2026-09-current/changelog.md
