# Pipeline v2 Generic Step08 - Outbound Processing for Idoc (com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc` 1.0.2, package generic-outbound, label 2026-06.

## Purpose

Generic IDoc receiver step at `{{ProcessDirect_Address}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_ProcessDirect_Sender" "Pipeline"]; the p2p-to-IDoc template calls `{{GenericIDocEndpoint}}` [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, parameter "GenericIDocEndpoint"], equal only by configuration (pitfalls). It validates the IDoc XML, completes EDI_DC40 from headers, Partner Directory and parameters, checks mandatory fields and sends over one of twelve channels chosen by content type (bulk forced to Text/XML, single with ARCKEY to x-sap.idoc, steps 7, 29), proxy and authentication type.

## Adapters

Receiver cells: calling step, IDoc type, authentication. All receivers: `${property.Receiver_IDocEndpoint}`, SAP message id from header UniqueID; On-Premise via sapcc and `${property.Receiver_IDocCCLocationId}`; Basic with `${property.Receiver_IDocCredentialName}`, Certificate with `${property.Receiver_IDocPrivateKeyAlias}`.

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Pipeline | `{{ProcessDirect_Address}}` [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_ProcessDirect_Sender" "Pipeline"] |
| Receiver | IDOC | OnPremise | 50: single Basic [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremBasic" "OnPremise"] |
| Receiver | IDOC | OnPremise | 52: single None [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremNone" "OnPremise"] |
| Receiver | IDOC | OnPremise | 51: single Principal Propagation [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremPrincipalPropagation" "OnPremise"] |
| Receiver | IDOC | Internet | 48: single Basic [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_InternetBasic" "Internet"] |
| Receiver | IDOC | Internet | 49: single None [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_InternetNone" "Internet"] |
| Receiver | IDOC | Internet | 47: single Client Certificate [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_InternetCertificate" "Internet"] |
| Receiver | IDOC | OnPremise | 33: package (text/xml) Basic [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_OnPremBasic" "OnPremise"] |
| Receiver | IDOC | OnPremise | 36: package None [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_OnPremNone" "OnPremise"] |
| Receiver | IDOC | OnPremise | 39: package Principal Propagation [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_OnPremPrincipalPropagation" "OnPremise"] |
| Receiver | IDOC | Internet | 35: package Basic [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_InternetBasic" "Internet"] |
| Receiver | IDOC | Internet | 34: package None [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_InternetNone" "Internet"] |
| Receiver | IDOC | Internet | 32: package Client Certificate [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Pack_Receiver_InternetCertificate" "Internet"] |

## Steps

1. Enricher "Set Default Properties": parameter defaults
2. ProcessCallElement "IDoc Basic Validation"
  3. Script "Check if XML body": `checkWellFormedXML.groovy`
  4. ExclusiveGateway "XML?": isWellFormedXml false to 13, else 5
  5. Script "Add missing EDI_DC40": `addEdidcNodeIfMissing.groovy`
  6. Enricher "Set XPath Properties": isPackage, isARCKEY, types
  7. ExclusiveGateway "Check ContentType": isPackage to 12; single with ARCKEY to 8; else 9
  8. Enricher "Force X-SAP.IDoc": header SAP_Receiver_IDocContentType
  9. ExclusiveGateway "Check if EOIO": no ARCKEY and SapPlainSoapQoS ExactlyOnceInOrder to 10, else end
  10. Enricher "Set custom status": IDocValidationException
  11. Script "Throw Exception": `throwException.groovy`
  12. Enricher "Force Text/XML for Bulk": then 9
  13. Enricher "Set custom status": IDocValidationException
  14. Script "Throw Exception": `throwException.groovy`
  15. ErrorEventSubProcessTemplate "Exception Subprocess at Validation"
    16. ProcessCallElement "Call Exception Handling Process"
      17. Script "Add Custom Headers": `addCustomHeaderProperties.groovy`
      18. ExclusiveGateway "Audit Log Header?": auditLogHeader set to 19, else 22
      19. Enricher "Add Exception to the Log"
      20. Script "Save in Audit Log": `auditLogHandler.groovy` addAuditLog
      21. Script "Log Message per Config": `logMessage.groovy`
      22. Enricher "Recover Audit Log": from property auditLogProperty, then 19
23. Script "Read and Set PD values": `setAllDefaultAndScenarioValuesFromPDForIDoc.groovy`
24. Script "Set Properties & Adjust EDIDC": `setEdidcFromParamsForIDoc.groovy`
25. Enricher "Set Monitoring Properties": EDIDC_*
26. Script "Add Combined IDoc Properties": `setIDocMonitoringProperties.groovy`
27. Enricher "Backup Log headers"
28. Enricher "Remove headers": customX*, customHeaderProperties, auditLogHeader, SAP_*
29. ExclusiveGateway "Content Type?": Receiver_IDocContentType text/xml to 30, else 45
30. ProcessCallElement "Send IDoc as a TextXML": then 42
  31. ExclusiveGateway "Location & Auth?": proxy and authentication type, else 37
  32. ExternalCall "Send IDoc Package Internet Certificate"
  33. ExternalCall "Send IDoc Package OnPrem BasicAuth"
  34. ExternalCall "Send IDoc Package Internet NoneAuth"
  35. ExternalCall "Send IDoc Package Internet BasicAuth"
  36. ExternalCall "Send IDoc Package OnPrem NoneAuth"
  37. Enricher "Set custom status": NotExpectedConfigurationOption
  38. Script "Throw Exception": `throwException.groovy`
  39. ExternalCall "Send IDoc Package OnPrem PP"
  40. ErrorEventSubProcessTemplate "Exception Subprocess TextXML IDoc"
    41. ProcessCallElement "Call Exception Handling Process"
42. Enricher "Set IDocs from Bulk Response": //*:DbId
43. Script "Set IDoc Numbers": `displayBulkIDs.groovy`
44. Script "Add Custom Headers": `addCustomHeaderProperties.groovy`, "End"
45. ProcessCallElement "Send IDoc as X-SAP.IDOC": then 43
  46. ExclusiveGateway "Location & Auth?": as 31; else 53
  47. ExternalCall "Send 1 IDoc Internet Certificate"
  48. ExternalCall "Send 1 IDoc Internet BasicAuth"
  49. ExternalCall "Send 1 IDoc Internet NoneAuth"
  50. ExternalCall "Send 1 IDoc OnPrem BasicAuth"
  51. ExternalCall "Send 1 IDoc OnPrem PP"
  52. ExternalCall "Send 1 IDoc OnPrem NoneAuth"
  53. Enricher "Set custom status ": NotExpectedConfigurationOption
  54. Script "Throw Exception": `throwException.groovy`
  55. ErrorEventSubProcessTemplate "Exception Subprocess X-SAP.IDoc"
    56. ProcessCallElement "Call Exception Handling Process"
57. ErrorEventSubProcessTemplate "Exception Subprocess at Main"
  58. ProcessCallElement "Call Exception Handling Process"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| default_* | property | 1, 23 | parameters [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Set Default Properties"], PD overrides (Partner directory), EDIDC lists [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L107] |
| isWellFormedXml | property | 3 | [scripts/checkWellFormedXML.groovy#L11] |
| SAP_Receiver_IDocContentType | header | 8, 12 | Application/x-sap.idoc, Text/XML [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Force Text/XML for Bulk"] |
| SAP_ReceiverInterface; SAP_IDoc_EDIDC_<field> | header | 23 | if missing MESTYP.IDOCTYP[.CIMTYP] [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L68]; from PD [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L128] |
| Receiver_IDoc* (7 keys) | property | 24 | header SAP_Receiver_IDoc<key>, else default_, else empty [scripts/setEdidcFromParamsForIDoc.groovy#L32] [scripts/setEdidcFromParamsForIDoc.groovy#L45] |
| override fields, customHeader_overrideResultValues | property, body | 24 | EDI_DC40 rewritten [scripts/setEdidcFromParamsForIDoc.groovy#L82] [scripts/setEdidcFromParamsForIDoc.groovy#L88] |
| customHeader_IDocSenderPartner, _IDocReceiverPartner, _IDocTypeDetails, _ARCKEY | property | 26 | [scripts/setIDocMonitoringProperties.groovy#L38] [scripts/setIDocMonitoringProperties.groovy#L48] |
| location, exceptionMessage, SAP_MessageProcessingLogCustomStatus | property | 10, 13, 37, 53 | for `throwException.groovy` [scripts/throwException.groovy#L14] |

### Read

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAP_ReceiverAlias, SAP_Receiver, SAP_SenderAlias, SAP_Sender, partnerID | header | 23 | PD keys [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L11] [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L119] |
| SAP_ReceiverInterface, else SAP_MessageType | header | 5, 23 | added EDI_DC40 [scripts/addEdidcNodeIfMissing.groovy#L13] |
| SAP_IDoc_EDIDC_overrideControlRecordParameters, SAP_IDoc_EDIDC_<field>, SAP_IDoc_EDIDC_pidParameterReadSuffixPattern, SAP_Receiver_IDoc<key> | header | 23, 24 | [scripts/setEdidcFromParamsForIDoc.groovy#L50] [scripts/setEdidcFromParamsForIDoc.groovy#L61] [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L76] |
| SapPlainSoapQoS, SapPlainSoapQueueId, UniqueID | header | 1, 9 | EOIO check, MPL, message id [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Check if EOIO"] |
| SapIDocDbId, SapIDocTransferId | header | 43 | MPL [scripts/displayBulkIDs.groovy#L16] |

## Partner directory

- No PD service: the script returns unchanged [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L31].
- `<key>_<SAP_Receiver>` (String) of PID SAP_ReceiverAlias for the seven IDoc* connectivity keys (IDocEndpoint …); missing: parameter default [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L40].
- IDocLogicalSystem_<SAP_Receiver> of SAP_ReceiverAlias, IDocLogicalSystem_<SAP_Sender> of SAP_SenderAlias: default RCVPRN, SNDPRN; missing: Default_RCVPRN, Default_SNDPRN [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L51] [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L58].
- `<field>_<suffix>` of partnerID, per field listed in SAP_IDoc_EDIDC_overrideControlRecordParameters and not sent as header [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L126]; suffix: receiver interface for pattern empty, MESTYP.IDOCTYP.CIMTYP or SAP_ReceiverInterface [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L77], else the named EDI_DC40 values joined by "." [scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L88]; missing: default_<field>, else empty [scripts/setEdidcFromParamsForIDoc.groovy#L65].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `checkWellFormedXML.groovy`, `addEdidcNodeIfMissing.groovy` | 3, 5 | XML check; adds EDI_DC40 [scripts/checkWellFormedXML.groovy#L10] [scripts/addEdidcNodeIfMissing.groovy#L30] |
| `setAllDefaultAndScenarioValuesFromPDForIDoc.groovy`, `setEdidcFromParamsForIDoc.groovy` | 23, 24 | PD reads; final connectivity, EDI_DC40 rewrite [scripts/setEdidcFromParamsForIDoc.groovy#L95] |
| `setIDocMonitoringProperties.groovy` | 26 | empty mandatory EDIDC field: exception [scripts/setIDocMonitoringProperties.groovy#L34] |
| `displayBulkIDs.groovy` | 43 | IDoc numbers as MPL headers [scripts/displayBulkIDs.groovy#L62] |
| `addCustomHeaderProperties.groovy`, `throwException.groovy` | 17, 44; 11, 14, 38, 54 | MPL headers; exception [scripts/addCustomHeaderProperties.groovy#L69] [scripts/throwException.groovy#L34] |
| `auditLogHandler.groovy`, `logMessage.groovy` | 20, 21 | audit log; MPL attachments [scripts/auditLogHandler.groovy#L28] [scripts/logMessage.groovy#L11] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| ProcessDirect_Address | — | entry [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, parameter "ProcessDirect_Address"] | `{{tenant.endpoint.idoc.outbound}}` |
| IDoc_Address, LocationID, IDoc_CredentialName, IDoc_PrivateKeyAlias | — | receiver, Cloud Connector location, credential, key [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Set Default Properties"] | `{{tenant.idoc.receiver.address}}`, `{{tenant.cloudconnector.location}}`, `{{tenant.idoc.receiver.credential}}`, `{{tenant.idoc.receiver.keyalias}}` |
| IDoc_ProxyType, IDoc_AuthenticationType, IDoc_ContentType | — | channel [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Set Default Properties"] | no |
| Default_SNDPOR, Default_SNDPRT, Default_SNDPRN; Default_RCVPOR, Default_RCVPRT, Default_RCVPRN | — | EDIDC partners [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, parameter "Default_RCVPRN"] | `{{tenant.idoc.edidc.sender}}`, `{{tenant.idoc.edidc.receiver}}` |
| Default_EDIDC_List_Names, Default_EDIDC_List_Values, pidParameterReadSuffixPattern | — | EDIDC defaults, PD suffix [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Set Default Properties"] | no |
| IDoc_CleanUpRequestHeaders, IDoc_CompressMessage, IDoc_ReturnHTTPResponseCodeAsHeader, IDoc_Timeout, IDoc_AllowChunking, IDoc_KeepAlive | — | HTTP, all receivers [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremBasic" "OnPremise"] | no |
| AllowedHeadersList, HTTPSessionReuse | — | [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, property "httpSessionHandling"] | no |
| Exception_LoggingConfiguration, IDocIndividualHeaderInBulk | —, true [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, resource "parameters.propdef"] | error attachments, bulk numbers [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Add Exception to the Log"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step "Set IDocs from Bulk Response"] | no |

## Error handling

- Four event subprocesses call the "Exception Handling Process" and end in an Error End [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step 58 "Call Exception Handling Process"].
- Raised here: non-XML body, EOIO without ARCKEY (IDocValidationException), unsupported proxy/authentication (NotExpectedConfigurationOption), empty mandatory control record field [scripts/setIDocMonitoringProperties.groovy#L31].
- No retry or DLQ; what the caller does with the error is not visible (pitfalls).

## Extension points

- Scenario headers SAP_Receiver_IDoc<key> and SAP_IDoc_EDIDC_* override connectivity and control record [scripts/setEdidcFromParamsForIDoc.groovy#L32] [scripts/setEdidcFromParamsForIDoc.groovy#L61], present even when empty (pitfalls); PD per receiver alias and partner ID (Partner directory).
- AllowedHeadersList "Enhance allowed header list to your needs" [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, parameter "AllowedHeadersList"].

## Version notes

- 2026-09 1.0.3: "Set SapMessageId" = UniqueID, the 12 IDoc channels on SapMessageIdDetermination Reuse, partner-directory keys read first from the business-system partner ID [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step 29 "Set SapMessageId"] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremBasic" "OnPremise"] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L48] — see versions/2026-09/changelog.md
