# Pipeline v2 Generic Step01 - Inbound Processing for Idoc (com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc` 1.0.8, package generic-inbound, label 2026-06-trial.

## Purpose

First pipeline step for IDoc senders [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "IDOC" "Sender"]. It derives sender interface and partner ID [scripts/determinePartnerIDForIDocInbound.groovy#L603], the key of the later Partner Directory reads [scripts/readInboundQueueFromPD.groovy#L21], runs the exits and branches on QoS [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "QoS?"]:

- ExactlyOnceInOrder: refused [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 49 "Set custom status"].
- Otherwise (default ExactlyOnce [scripts/setIDocSenderInterface.groovy#L12]): a single IDoc is keyed SNDPRN_DOCNUM [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 29 "Set unique ID"]; a bulk [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Bulk?"] is split or, with PD KeepBulk, keyed by transfer ID [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 48 "Set unique ID"]. After the duplicate check it goes to PD InboundQueue or `{{DefaultJMSQueuePrefix}}{{DefaultJMSQueueNumber}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "DefaultInboundProcessingQueue"].
- X01 feeds Step02 async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"], Q01 Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"], with equal prefixes (see pitfalls).

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | IDOC | Sender | `{{IDocEntryPoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "IDOC" "Sender"] |
| Receiver | JMS | DefaultInboundProcessingQueue | `{{DefaultJMSQueuePrefix}}{{DefaultJMSQueueNumber}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "DefaultInboundProcessingQueue"] |
| Receiver | JMS | InboundProcessingQueue | `${property.inbQueue}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "InboundProcessingQueue"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Enricher "Read partner headers": EDIDC fields, testMode, parameters
2. Script "Set sender interface": `setIDocSenderInterface.groovy`
3. Script "Determine partner ID": `determinePartnerIDForIDocInbound.groovy`
4. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
5. Enricher "Initialize pre-processing exit": CustomXType = CustomXPre
6. ProcessCallElement "Call custom exit"
  7. Enricher "Initialize exit"
  8. Script "Check scenario-specific exit": `readCustomXFromPD.groovy` sets CustomXBoolean, CustomXEndpoint [scripts/readCustomXFromPD.groovy#L58]
  9. ExclusiveGateway "Global exit?": CustomXGlobalBoolean = 'true' to 10, else 11
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomXGlobal"]
  11. ExclusiveGateway "Scenario-specific exit?": CustomXBoolean = 'true' to 12, else end
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomXScenario"]
  13. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    14. ExclusiveGateway "Custom Extension?": CustomXError_Enabled = 'true' to 15, else 20
    15. ProcessCallElement "Call custom error handling"
      16. Enricher "Set headers": exception headers
      17. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomErrorFlow"]
      18. Enricher "Set custom status": from header customXError_Status
      19. ExclusiveGateway "End event?": customXError_EndEvent
    20. ProcessCallElement "Call standard retry handling"
      21. ExclusiveGateway "Test mode?": testMode = 'true' to 22, else "Error End 3"
      22. Enricher "Set custom status": ErrorInTestMode, "Escalation End 2"
23. ExclusiveGateway "QoS?": ExactlyOnceInOrder 49, default 24
24. Script "Read inbound queue": `readInboundQueueFromPD.groovy`
25. Enricher "Set nodelist": DOCNUM node list
26. Script "Set bulk IDs": `displayBulkIDs.groovy`
27. ExclusiveGateway "Bulk?": `count(//*:IDOC) > 1` to 45, else 28
28. Enricher "Read Idoc control header": 27 SAP_IDoc_EDIDC_* headers
29. Enricher "Set unique ID": uniqueID = SNDPRN_DOCNUM, SAP_ApplicationID = DOCNUM
30. Script "Remove leading zeros": `removeLeadingZerosFromApplicationID.groovy`
31. Enricher "Remove header": deletes customXPipelineStep
32. ExclusiveGateway "Skip duplicate check?": CustomXTest_SkipDuplicateCheck = 'true' to 33, else 42
33. ProcessCallElement "Process Call"
  34. ExclusiveGateway "Which queue?": inbQueue != null to 35, else 36
  35. Send "Send": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "InboundProcessingQueue"]
  36. Send "Send": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "DefaultInboundProcessingQueue"]
  37. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    38. ExclusiveGateway "Custom Extension?"
    39. ProcessCallElement "Call standard error handling"
    40. ProcessCallElement "Call custom error handling"
41. Enricher "Set custom status": DuplicateCheckSkipped
42. IdempotentProcessCall "Idempotent Process Call": key uniqueID, skip on duplicate
43. ExclusiveGateway "Duplicate?": CamelDuplicateMessage = 'true' to 44
44. Enricher "Set custom status": DuplicateDiscarded
45. Script "Read keep bulk parameter": `readKeepBulkSettingFromPD.groovy`
46. ExclusiveGateway "Keep bulk?": keepBulk = 'true' to 48, else 47
47. Splitter "IDoc Splitter": each IDoc to 28
48. Enricher "Set unique ID": uniqueID = SNDPRN_sapidoctransferid, then 30
49. Enricher "Set custom status": EOIO unsupported
50. Script "Throw exception": `throwException.groovy`
51. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  52. ExclusiveGateway "Custom Extension?"
  53. ProcessCallElement "Call custom error handling"
  54. ProcessCallElement "Call standard error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_IDoc_EDIDC_SNDPOR/SNDPRT/SNDPFC/SNDPRN, RCVPOR/RCVPRT/RCVPFC/RCVPRN, MANDT, MESTYP, IDOCTYP, CIMTYP; testMode | header | 1 | XPath; `//*:TEST != ''` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Read partner headers"] |
| CustomXPid_*, CustomXPreGlobal_*, CustomXError_Enabled, Default_LogicalSystemName | property | 1 | parameters [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Read partner headers"] |
| SAP_SenderInterface, SAP_SenderInterfaceNamespace | header | 2 | `<MESTYP>.<IDOCTYP>[.<CIMTYP>]`, `urn:sap-com:document:sap:idoc:messages` [scripts/setIDocSenderInterface.groovy#L10] |
| SapQualityOfService, customHeader_QualityOfService | header, property | 2 | SapPlainSoapQoS, else ExactlyOnce [scripts/setIDocSenderInterface.groovy#L12] |
| tenantStage, tenantStageVirtual | header | 3 | [scripts/determinePartnerIDForIDocInbound.groovy#L199] |
| SAP_Sender, SAP_SenderAlias | header | 3 | business or logical system [scripts/determinePartnerIDForIDocInbound.groovy#L455] |
| partnerID, customHeader_PartnerID | header, property | 3 | [scripts/determinePartnerIDForIDocInbound.groovy#L603] |
| inbQueue | property | 24 | [scripts/readInboundQueueFromPD.groovy#L25] |
| SAP_MessageType | header | 25 | SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Set nodelist"] |
| uniqueID, SAP_ApplicationID | property, header | 29, 48, 30 | duplicate key; zeros stripped [scripts/removeLeadingZerosFromApplicationID.groovy#L8] |
| keepBulk | property | 45 | PD KeepBulk, else 'false' [scripts/readKeepBulkSettingFromPD.groovy#L25] |

### Read

Headers and properties that arrive from outside this flow (sender adapter, earlier flows, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SapPlainSoapQoS | header | 2 | QoS [scripts/setIDocSenderInterface.groovy#L12] |
| SapIDocDbId, SapIDocTransferId | header | 26 | MPL custom headers [scripts/displayBulkIDs.groovy#L16] |
| customXError_Status, customXError_EndEvent | header | 18, 19 | status, end event [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 18 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "End event?"] |

## Partner directory

- Stage: as for XI [scripts/determinePartnerIDForIDocInbound.groovy#L178].
- Sender logical system: SNDPRN if SNDPRT = LS, else `<SNDPOR without SAP>CLNT<MANDT>` [scripts/determinePartnerIDForIDocInbound.groovy#L106].
- Sender alias: alternative partner, agency stage, scheme LogicalSystemName, then SNDPRN [scripts/determinePartnerIDForIDocInbound.groovy#L437]; business system via scheme BusinessSystemName, missing: logical system [scripts/determinePartnerIDForIDocInbound.groovy#L449].
- Sender party (SNDPRT not LS): agency alias, scheme `ALE#<SNDPRT>[#<SNDPFC>]`, id SNDPRN [scripts/determinePartnerIDForIDocInbound.groovy#L473].
- Virtual receiver, only if CustomXPid_ResolveVirtualReceiver = 'true': same lookups for RCVPRN [scripts/determinePartnerIDForIDocInbound.groovy#L486].
- Partner ID: alternative partner by interface [scripts/determinePartnerIDForIDocInbound.groovy#L750], agencies `<sender>:<receiver>`, `[party.]alias`, alias, SNDPRN in turn [scripts/determinePartnerIDForIDocInbound.groovy#L528]; missing: `<alias>~<interface>` [scripts/determinePartnerIDForIDocInbound.groovy#L586], or an exception when CustomXPid_SwitchOffTilde = 'true' [scripts/determinePartnerIDForIDocInbound.groovy#L599].
- Strings of partnerID: CustomXPreEnabled/Endpoint [scripts/readCustomXFromPD.groovy#L41], InboundQueue [scripts/readInboundQueueFromPD.groovy#L21], KeepBulk, missing = false [scripts/readKeepBulkSettingFromPD.groovy#L25].

PD scripts throw when the header partnerID is missing [scripts/readInboundQueueFromPD.groovy#L17].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `setIDocSenderInterface.groovy` | 2 | interface, QoS [scripts/setIDocSenderInterface.groovy#L8] |
| `determinePartnerIDForIDocInbound.groovy` | 3 | stage, sender, partner ID [scripts/determinePartnerIDForIDocInbound.groovy#L44] |
| `addCustomHeaderProperties.groovy` | 4 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L69] |
| `readCustomXFromPD.groovy` | 8 | scenario exit [scripts/readCustomXFromPD.groovy#L52] |
| `readInboundQueueFromPD.groovy` | 24 | inbound queue [scripts/readInboundQueueFromPD.groovy#L21] |
| `displayBulkIDs.groovy` | 26 | IDoc numbers to MPL [scripts/displayBulkIDs.groovy#L62] |
| `removeLeadingZerosFromApplicationID.groovy` | 30 | strips zeros [scripts/removeLeadingZerosFromApplicationID.groovy#L8] |
| `readKeepBulkSettingFromPD.groovy` | 45 | keep bulk [scripts/readKeepBulkSettingFromPD.groovy#L21] |
| `throwException.groovy` | 50 | throws exceptionType [scripts/throwException.groovy#L17] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | audit log, Base64/JSON [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| IDocEntryPoint | — | sender address [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "IDocEntryPoint"] | `{{tenant.endpoint.idoc.inbound}}` |
| DefaultJMSQueuePrefix, DefaultJMSQueueNumber | — | prefix; X01 or Q01 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "DefaultJMSQueuePrefix"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "DefaultJMSQueueNumber"] | prefix: `{{tenant.pipeline.prefix}}` |
| UserRole, SubjectDN, IssuerDN | — | role, certificate [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "IDOC" "Sender"] | `{{tenant.security.sender.role}}`, `{{tenant.security.cert.subjectdn}}`, `{{tenant.security.cert.issuerdn}}` |
| SenderAuthType, InvalidXMLCharHandling, BodySize, AttachmentSize; JMS_CompressStoredMessage, JMS_EncryptStoredMessage, JMS_RetentionThreshold, JMS_ExpirationPeriod | — | sender [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "IDOC" "Sender"]; JMS storage [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "DefaultInboundProcessingQueue"] | no |
| CustomXPreGlobal_Enabled, CustomXError_Enabled, CustomXPid_SwitchOffTilde, CustomXPid_ResolveVirtualReceiver | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Read partner headers"] | no |
| CustomXTest_SkipDuplicateCheck, IDocIndividualHeaderInBulk | — | test switch; bulk header form [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "CustomXTest_SkipDuplicateCheck"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "IDocIndividualHeaderInBulk"] | no |
| Default_LogicalSystemName | — | set, read by no script [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Read partner headers"] | `{{tenant.idoc.logicalsystem.default}}` |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Three event subprocesses call custom error handling when CustomXError_Enabled = 'true', else standard error handling [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 14 "Custom Extension?"].
- Standard: a test IDoc gets status ErrorInTestMode and escalates; otherwise "Error End 3" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Test mode?"].
- Custom: exception headers to `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomErrorFlow"]; returned customXError_Status is the MPL status [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 18 "Set custom status"], customXError_EndEvent selects Completed, Escalated or "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "End event?"].
- EOIO sets ConfigurationOptionNotSupported and throws [scripts/throwException.groovy#L34].
- Partner ID errors restore the headers, then rethrow [scripts/determinePartnerIDForIDocInbound.groovy#L48].
- Duplicates end with status DuplicateDiscarded [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step "Duplicate?"]; a skipped check is marked DuplicateCheckSkipped [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 41 "Set custom status"].

## Extension points

- Global exit: CustomXPreGlobal_Enabled = 'true' and CustomXPreGlobal_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "CustomXPreGlobal_Enabled"].
- Scenario exit per partner ID: PD CustomXPreEnabled listing step "01", and CustomXPreEndpoint [scripts/readCustomXFromPD.groovy#L53].
- Custom error flow: CustomXError_Enabled and CustomXError_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, parameter "CustomXError_Enabled"].
- Partner-specific inbound queue: PD InboundQueue [scripts/readInboundQueueFromPD.groovy#L21].
- Bulk kept as one message: PD KeepBulk [scripts/readKeepBulkSettingFromPD.groovy#L21].
- Virtual receiver in the partner ID: CustomXPid_ResolveVirtualReceiver [scripts/determinePartnerIDForIDocInbound.groovy#L486].
- Monitoring: any `customHeader_<name>` property becomes an MPL custom header [scripts/addCustomHeaderProperties.groovy#L44].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
