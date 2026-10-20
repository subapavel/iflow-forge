# Changelog 2026-09-current

Step numbers are the 2026-09-current dump's. Step02 to Step06: [changelog-generic-pipelines-step02.md](changelog-generic-pipelines-step02.md), [changelog-generic-pipelines-step04-06.md](changelog-generic-pipelines-step04-06.md).

## Scope

Compared: po-xray dumps of the 29 shared artifacts, raw trees, four CHANGELOG.txt. The 2026-09-current tree is a tenant export, tenant suffixes stripped, otherwise byte-identical to it [knowledge/sources/README.md#L17]–[knowledge/sources/README.md#L20]. Versions [2026-06-trial/INDEX.md#L3]–[2026-06-trial/INDEX.md#L47] [2026-09-current/INDEX.md#L3]–[2026-09-current/INDEX.md#L54] (also in the drafts: R-013 to R-015, R-064 to R-077, R-079 to R-082):

| artifact | 2026-06-trial | 2026-09-current |
|---|---|---|
| generic-inbound change log | 1.0.7 | 1.0.8 |
| com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi | 1.0.5 | 1.0.6 |
| com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc | 1.0.8 | 1.0.9 |
| generic-outbound change log | 1.0.3 | 1.0.5 |
| com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing | 1.0.3 | 1.0.4 |
| com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc | 1.0.2 | 1.0.3 |
| com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi | 1.0.2 | 1.0.4 |
| generic-pipelines change log | 1.0.4 | 1.0.5 |
| com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync | 1.0.5 | 1.0.6 |
| com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination | 1.0.4 | 1.0.5 |
| com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination | 1.0.3 | 1.0.4 |
| templates change log | 1.1.1 | 1.2.0 |
| com.sap.integration.cloud.pipeline.v2.script.collection | 1.0.0 | 1.0.1 |
| com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination, com.sap.integration.cloud.pipeline.v2.template.step04.reuse.extended.receiver.determination, com.sap.integration.cloud.pipeline.v2.template.step05.custom.interface.determination, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.xi | 1.0.0 | 1.0.0 |
| com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.alo, com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.eo, com.sap.integration.cloud.pipeline.v2.template.step01.inbound.processing.sync, com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.scripts, com.sap.integration.cloud.pipeline.v2.template.step04.custom.receiver.determination.xrd, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.sync | 1.0.1 | 1.0.1 |
| com.sap.integration.cloud.pipeline.v2.template.custom.exit | 1.0.2 | 1.0.2 |
| com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration | — | 1.0.0 |
| com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry | — | 1.0.1 |

## SAP's change log

generic-inbound 1.0.8 [2026-09-current/generic-inbound/CHANGELOG.txt#L5]:
- Step01 XI: landscape stages on the p2p bypass [2026-09-current/generic-inbound/CHANGELOG.txt#L6].
- Step01 XI, asynchronous: response body nulled after the JMS store [2026-09-current/generic-inbound/CHANGELOG.txt#L7].
- Step01 XI, IDoc: tenant name from a Content Modifier expression, not a Groovy class [2026-09-current/generic-inbound/CHANGELOG.txt#L8].
- Step01 XI, IDoc: allowed header list enhanced [2026-09-current/generic-inbound/CHANGELOG.txt#L9].
- Step01 XI, IDoc: receiver queue name as custom header [2026-09-current/generic-inbound/CHANGELOG.txt#L10].
- L23: typo fixed in the 1.0.5 entry [2026-09-current/generic-inbound/CHANGELOG.txt#L23].

generic-outbound 1.0.5 [2026-09-current/generic-outbound/CHANGELOG.txt#L5], 1.0.4 [2026-09-current/generic-outbound/CHANGELOG.txt#L8]:
- Step08 XI: ID Mapper source UniqueID + receiver interface cut at 120, context namespace cut at 40 [2026-09-current/generic-outbound/CHANGELOG.txt#L6].
- Step08 XI, IDoc: connection parameters also on the business-system partner ID, some on a scenario partner ID [2026-09-current/generic-outbound/CHANGELOG.txt#L9].
- Step06: sender queue name as custom header [2026-09-current/generic-outbound/CHANGELOG.txt#L10].
- Step08 XI: UniqueID to SapMessageIdEx, adapter Reuse not Map; optional end-to-end sender message ID [2026-09-current/generic-outbound/CHANGELOG.txt#L11].
- Step08 IDoc: UniqueID to SapMessageId, adapter Reuse not Map [2026-09-current/generic-outbound/CHANGELOG.txt#L12].
- Step06: no retry for some errors, straight to the DLQ [2026-09-current/generic-outbound/CHANGELOG.txt#L13].
- Step06: optional original payload size [2026-09-current/generic-outbound/CHANGELOG.txt#L14].

generic-pipelines 1.0.5 [2026-09-current/generic-pipelines/CHANGELOG.txt#L5]:
- asynchronous flows: sender and receiver queue names as custom headers [2026-09-current/generic-pipelines/CHANGELOG.txt#L6].
- Step02 async: no retry for some errors, straight to the DLQ [2026-09-current/generic-pipelines/CHANGELOG.txt#L7].
- Step02 async, Step04, Step05: OData API settings to read the original error after the last retry [2026-09-current/generic-pipelines/CHANGELOG.txt#L8].
- DLQ: payload, headers, attachments restored to their JMS-inbound state [2026-09-current/generic-pipelines/CHANGELOG.txt#L9].

templates 1.2.0 [2026-09-current/templates/CHANGELOG.txt#L5]: new template Step07 One-to-Many to Generic IDoc Outbound [2026-09-current/templates/CHANGELOG.txt#L6]; new value mapping Skip Retry Configuration [2026-09-current/templates/CHANGELOG.txt#L7]; new Partner Directory Upload Helper for it [2026-09-current/templates/CHANGELOG.txt#L8]; collection logMessage.groovy logs exceptions as MPL attachment [2026-09-current/templates/CHANGELOG.txt#L9].

## Artifact differences


### com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi

1.0.5 → 1.0.6 [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, manifest "Bundle-Version"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, manifest "Bundle-Version"].
- p2p bypass: on tenantStageVirtual = 'true' to the receiver stage, then "Map to Business Name" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 49 "Virtual Stage Routing?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 50 "Map to Receiver Stage"]; announced: L6 (also in the drafts: R-084).
- Body emptied after both JMS sends [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 33 "Clean up body in XI response"]; announced: L7 (also in the drafts: R-085, R-086).
- Custom error status from header customXError_Status [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 22 "Set custom status"]; **undocumented by SAP**.
- Step 1 adds properties TenantName (${sysenv.TENANT_NAME}), PipelinePrefix, DefaultJMSQueueNumber, headers SAP_SenderProtocol = XI, SAP_SenderMessageId, SAP_SenderMessageIdEx [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"]; TenantName announced: L8 (also in the drafts: R-087); headers **undocumented by SAP**.
- determinePartnerID reads TenantName, not System.getenv [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L129], announced: L8 (also in the drafts: R-088, R-174); empty alias = not found [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L233], **undocumented by SAP**.
- readInboundQueueFromPD: MPL custom header ReceiverJMSQueue, else PipelinePrefix + DefaultJMSQueueNumber [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/readInboundQueueFromPD.groovy#L33] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/readInboundQueueFromPD.groovy#L35]; announced: L10 (also in the drafts: R-093).
- allowedHeaderList adds dc_(.), customHeaderProperties [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, property "allowedHeaderList"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, property "allowedHeaderList"]; announced: L9 (also in the drafts: R-090).
- Contract names added: the six of step 1.

### com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc

1.0.8 → 1.0.9 [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, manifest "Bundle-Version"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, manifest "Bundle-Version"].
- Parameter and property Default_LogicalSystemName removed [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "Default_LogicalSystemName"]; **undocumented by SAP**.
- Step 1 adds TenantName, PipelinePrefix (from DefaultJMSQueuePrefix), DefaultJMSQueueNumber, header SAP_SenderProtocol = IDoc [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 1 "Read partner headers"]; TenantName announced: L8; header **undocumented by SAP**.
- determinePartnerIDForIDocInbound reads TenantName [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/determinePartnerIDForIDocInbound.groovy#L182], announced: L8; empty alias = not found [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/determinePartnerIDForIDocInbound.groovy#L287], **undocumented by SAP**.
- readInboundQueueFromPD: ReceiverJMSQueue as Step01 XI [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L33]; announced: L10. allowedHeaderList adds dc_(.), customHeaderProperties [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, property "allowedHeaderList"]; announced: L9.
- Contract names added: the four of step 1; removed: Default_LogicalSystemName.

### com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc

1.0.2 → 1.0.3 [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, manifest "Bundle-Version"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, manifest "Bundle-Version"].
- SapMessageId = UniqueID [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step 29 "Set SapMessageId"]; 12 IDoc receiver channels: SapMessageIdDetermination Map to Reuse [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremBasic" "OnPremise"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_IDOC_Receiver_OnPremBasic" "OnPremise"]; announced: L12 (also in the drafts: R-155, R-156).
- setAllDefaultAndScenarioValuesFromPDForIDoc reads the business-system partner ID first [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L48], pidParameterReadSuffixPattern and overrideControlRecordParameters_<suffix> from the scenario partner ID [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L99] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L148], fills absent SAP_Receiver_<key> (IDocCredentialName, IDocPrivateKeyAlias, IDocAuthenticationType, IDocContentType) from <key>_<alias or receiver>_<suffix> [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L176] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L178]; announced: L9 (also in the drafts: R-141, R-143, R-146). EDI_DC40 under any root [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L112]: **undocumented by SAP**.
- logMessage: exception attachment [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/logMessage.groovy#L63] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/logMessage.groovy#L65] only when Exception_LoggingConfiguration [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, step 19 "Add Exception to the Log"] names Exception or Error; no automatic promotion [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/logMessage.groovy#L29]; **undocumented by SAP**.
- Contract names added: SapMessageId, SAP_Receiver_<key>.

### com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi

1.0.2 → 1.0.4 [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, manifest "Bundle-Version"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, manifest "Bundle-Version"]; holds the 1.0.5 fix (L6).
- After "Backup Log headers": sender-ID reuse, on MapUniqueIdToMessageIdEx = 'true' an IDMapper to SapMessageIdEx, the application ID [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 10 "Reuse sender id"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 11 "Reuse sender id?"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 16 "Prep ID Mapper"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 17 "Map unique Id"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 18 "Override Application ID"]; six XI receiver channels: XiMessageIdDetermination Map to Reuse [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, adapter "CC_XI_Receiver_OnPremBasic" "OnPremise"]; announced: L11 (also in the drafts: R-147, R-149).
- reuseMessageIdFromSender: PD PassMessageIdFromE2E = 'true', a UUID SAP_SenderMessageIdEx and no SapSplitExpression give SapMessageIdEx, else UniqueID sets MapUniqueIdToMessageIdEx [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L38] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L48]; split: MPL header ParentIdBeforeMessageSplit [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/reuseMessageIdFromSender.groovy#L30]; announced: L11 (also in the drafts: R-148).
- prepIdMapperForXiMessageId: source UniqueID + SAP_ReceiverInterface cut at 120, context 'C' + namespace alphanumerics (else IDMapper1) cut at 40 [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L7] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L12] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L17] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/prepIdMapperForXiMessageId.groovy#L18]; announced: L6 (also in the drafts: R-150, R-152, R-153, R-154).
- overrideSAPApplicationId: SAP_ApplicationID = SapMessageIdEx, hyphens removed, upper case [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/overrideSAPApplicationId.groovy#L9]; **undocumented by SAP**.
- readPDValuesForXI reads the business-system partner ID first [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L54], fills absent SAP_Receiver_<key> (XICredentialName, XIPrivateKeyAlias, XIAuthenticationType) from <key>_<alias or receiver>_<interface> [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L99] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L101]; announced: L9 (also in the drafts: R-142). Lower-case fallbacks interfaceNameDetermination_, interfaceNamespaceDetermination_ [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L74] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/readPDValuesForXI.groovy#L87]: **undocumented by SAP**. logMessage as Step08 IDoc [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, step 22 "Add Exception to the Log"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/logMessage.groovy#L29] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/logMessage.groovy#L63].
- Contract names added: MapUniqueIdToMessageIdEx, IdMapperSource, IdMapperContext, SapMessageIdEx, ParentIdBeforeMessageSplit; read: SAP_SenderMessageIdEx, SapSplitExpression.

### com.sap.integration.cloud.pipeline.v2.script.collection

1.0.0 → 1.0.1 [2026-06-trial: com.sap.integration.cloud.pipeline.v2.script.collection, manifest "Bundle-Version"] [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "Bundle-Version"]; only MANIFEST.MF and logMessage.groovy differ (also in the drafts: R-162).
- logMessage.groovy adds "exception" to the configuration on CamelExceptionCaught [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/logMessage.groovy#L29], writes an ExceptionInfo attachment (class, message, stack trace, cause, HTTP status or SOAP fault) [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/logMessage.groovy#L65] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/logMessage.groovy#L136] and drops CamelExceptionCaught from the property attachment [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/logMessage.groovy#L16]; announced: templates L9 (also in the drafts: R-163, R-164, R-165).

### Undocumented and not observable

**Undocumented by SAP** (this page): Step01 XI custom status, sender-ID headers; Step01 alias check, SAP_SenderProtocol, Default_LogicalSystemName removal; Step08 any-root EDI_DC40, exception logging, SAP_ApplicationID override, lower-case PD keys.

**Not observable in the artifacts**: generic-inbound L8 in part: mapSenderStageToReceiverStage calls System.getenv("TENANT_NAME") [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/mapSenderStageToReceiverStage.groovy#L37]. Draft claims: R-113, R-114, R-130, R-169 (parameters.prop is not staged [knowledge/sources/README.md#L14]); R-146 in part (key lists of setValuesForXI [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, scripts/setValuesForXI.groovy#L10] and setEdidcFromParamsForIDoc [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setEdidcFromParamsForIDoc.groovy#L15] are not named aliasKeys or scenarioKeys); the C claims: [changelog-generic-pipelines-step04-06.md](changelog-generic-pipelines-step04-06.md).

## New artifacts

- com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc 1.0.0 [2026-09-current/INDEX.md#L33]: splits at //IDOC [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, step 14 "General Splitter"], sets UniqueID = ${header.UniqueID}_${header.CamelSplitIndex} [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, step 15 "Override unique ID for split messages"] and the IDoc headers [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, step 12 "Set IDoc Control Header"], calls the generic IDoc outbound [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2m.to.idoc, parameter "GenericIDocEndpoint"]; announced: templates L6 (also in the drafts: R-157, R-158, R-161).
- com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry 1.0.1 [2026-09-current/INDEX.md#L48]: 22 entries IFlow/OutboundProcessing to Error/Connection, Generic or Mapping, only com.sap.xi.mapping.camel.XiMappingException true [com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry, resource "value_mapping.xml"]; read by Step06 [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/checkExceptionType.groovy#L117]; announced: templates L7 (also in the drafts: R-109, R-159).
- com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration 1.0.0 [2026-09-current/INDEX.md#L49]: reads the value mapping by API [com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration, step 2 "Retrieve VM"], compares it with PartnerDirectory_PID string parameters [com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration, step 7 "Compare Existing PD and VM configuration"], updates [com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration, step 14 "Update in PD"] and creates entries [com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration, step 20 "Post to PD"]; PD keys absent from the mapping are ignored [com.sap.integration.cloud.pipeline.v2.template.partnerdirectoryupload.skipretryconfiguration, scripts/CompareValueMapping.groovy#L43]; announced: templates L8 (also in the drafts: R-111, R-112, R-160).

## Unchanged artifacts

The 18 templates of equal version: empty dump diff; only the manifest's Origin-ModifiedDate differs, e.g. [2026-06-trial: com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, manifest "Origin-ModifiedDate"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, manifest "Origin-ModifiedDate"].
