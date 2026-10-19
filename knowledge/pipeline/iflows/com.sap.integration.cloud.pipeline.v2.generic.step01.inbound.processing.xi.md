# Pipeline v2 Generic Step01 - Inbound Processing for XI (com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi` 1.0.5, package generic-inbound, label 2026-06-trial.

## Purpose

First pipeline step for XI sender messages [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "XI" "Sender"]. It determines the partner ID [scripts/determinePartnerID.groovy#L342] that keys the later Partner Directory reads [scripts/readInboundQueueFromPD.groovy#L21], runs the pre-processing exits and branches on SapQualityOfService [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 23 "QoS?"]:

- ExactlyOnce: stored once per SapMessageIdEx [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Idempotent Process Call"] in the PD queue InboundQueue or in `{{PipelinePrefix}}{{DefaultJMSQueueNumber}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"]. X01 feeds Step02 async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"], Q01 Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"] (prefixes: see pitfalls).
- BestEffort: synchronous call of `/{{PipelinePrefix}}/XS01` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "SyncPipeline"] of Step02 sync [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "InboundProcessingSync"], or, with bypass p2p, the outbound endpoint [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "ActualReceiver"]; the reply loses the pipeline headers [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 44 "Remove headers from response"].
- EOIO [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 50 "Set custom status"], empty or unknown QoS [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 24 "Set custom status"]: refused.

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | XI | Sender | `{{XIEntryPoint}}`, QoS HandledByIflow, DataStore, retry 1 to 60 exponential, DLQ, auth `{{SenderAuthType}}`/`{{UserRole}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "XI" "Sender"] |
| Receiver | JMS | DefaultInboundProcessingQueue | `{{PipelinePrefix}}{{DefaultJMSQueueNumber}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"] |
| Receiver | JMS | InboundProcessingQueue | `${property.inbQueue}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "InboundProcessingQueue"] |
| Receiver | ProcessDirect | SyncPipeline | `/{{PipelinePrefix}}/XS01` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "SyncPipeline"] |
| Receiver | ProcessDirect | ActualReceiver | `${header.SAP_OutboundProcessingEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "ActualReceiver"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Enricher "Set SAP headers": SAP_* headers, parameters to properties
2. Script "Fetch dynamic configuration": `createHeadersFromSapDynamicConfiguration.groovy`
3. Script "Add party": `addPartyForXIInbound.groovy` prefixes the party [scripts/addPartyForXIInbound.groovy#L15]
4. Script "Determine partner ID": `determinePartnerID.groovy`
5. Enricher "Initialize pre-processing exit": CustomXType = CustomXPre
6. ProcessCallElement "Call custom exit"
  7. Enricher "Initialize exit"
  8. Script "Check scenario-specific exit": `readCustomXFromPD.groovy` sets CustomXBoolean, CustomXEndpoint [scripts/readCustomXFromPD.groovy#L58]
  9. ExclusiveGateway "Global exit?": CustomXGlobalBoolean = 'true' to 10, else 11
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomXGlobal"]
  11. ExclusiveGateway "Scenario-specific exit?": CustomXBoolean = 'true' to 12, else end
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomXScenario"]
  13. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    14. ExclusiveGateway "Custom Extension?": CustomXError_Enabled = 'true' to 19, else 15
    15. ProcessCallElement "Call standard retry handling"
      16. ExclusiveGateway "QoS?": BestEffort to 17, else "Error End 3"
      17. Enricher "Return exception message": body `${exception.message}`, status SyncCallFailed
      18. Enricher "Remove headers from response": then "Error End 3"
    19. ProcessCallElement "Call custom error handling"
      20. Enricher "Set headers": exception headers
      21. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomErrorFlow"]
      22. ExclusiveGateway "End event?": customXError_EndEvent
23. ExclusiveGateway "QoS?": ExactlyOnce 26, BestEffort 39, ExactlyOnceInOrder 50, default 24
24. Enricher "Set custom status": QoS empty or unknown
25. Script "Throw exception": `throwException.groovy`
26. Script "Read inbound queue": `readInboundQueueFromPD.groovy`
27. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
28. Enricher "Remove header": deletes customXPipelineStep
29. IdempotentProcessCall "Idempotent Process Call": key `${header.SapMessageIdEx}`, skip on duplicate
  30. ExclusiveGateway "Which queue?": inbQueue != null to 32, else 31
  31. Send "Send": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"]
  32. Send "Send": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "InboundProcessingQueue"]
  33. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    34. ExclusiveGateway "Custom Extension?"
    35. ProcessCallElement "Call custom error handling"
    36. ProcessCallElement "Call standard error handling"
37. ExclusiveGateway "Duplicate?": CamelDuplicateMessage = 'true' to 38, else end
38. Enricher "Set custom status": DuplicateDiscarded
39. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
40. Script "Read bypass option": `readBypassOptionFromPD.groovy`, p2p or skipRcvDet [scripts/readBypassOptionFromPD.groovy#L36]
41. ExclusiveGateway "Bypass option?": default "CBR" 42, p2p 47, skipRcvDet 45
42. Enricher "Remove header": deletes customXPipelineStep
43. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "SyncPipeline"]
44. Enricher "Remove headers from response": deletes eight pipeline headers
45. Enricher "Set custom status": skipRcvDet unsupported
46. Script "Throw exception": `throwException.groovy`
47. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
48. Enricher "Remove header": deletes customXPipelineStep
49. ExternalCall "Request Reply": then 44 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "ActualReceiver"]
50. Enricher "Set custom status": EOIO unsupported
51. Script "Throw exception": `throwException.groovy`
52. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  53. ExclusiveGateway "Custom Extension?"
  54. ProcessCallElement "Call custom error handling"
  55. ProcessCallElement "Call standard error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_Sender, SAP_SenderInterface(Namespace), SAP_MessageType, SAP_ApplicationID | header | 1 | XI service, interface, namespace; SapMessageId upper-cased [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Set SAP headers"] |
| dc_SapDynamicConfiguration_<k> | header | 2 | property SapDynamicConfiguration_<k> [scripts/createHeadersFromSapDynamicConfiguration.groovy#L25] |
| SAP_Sender, SAP_SenderParty | header | 3 | `<party>.<service>` [scripts/addPartyForXIInbound.groovy#L15] |
| tenantStage, tenantStageVirtual, SAP_SenderAlias, partnerID | header | 4 | [scripts/determinePartnerID.groovy#L342] |
| customXPipelineStep | header | 8 | "01" from `${camelId}` [scripts/readCustomXFromPD.groovy#L28] |
| inbQueue | property | 26 | [scripts/readInboundQueueFromPD.groovy#L25] |
| customHeaderProperties | header | 27 | Base64 JSON [scripts/addCustomHeaderProperties.groovy#L75] |
| SAP_ReceiverAlias, SAP_OutboundProcessingEndpoint, bypassOption | header, property | 40 | [scripts/readBypassOptionFromPD.groovy#L36] |
| SAP_Receiver | header | 47 | [scripts/mapReceiverAliasToBusinessSystemName.groovy#L55] |

### Read

Headers and properties that arrive from outside this flow (sender adapter, earlier flows, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SapQualityOfService | header | 23 | branch [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 23 "QoS?"] |
| SapMessageIdEx | header | 29 | duplicate key [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Idempotent Process Call"] |
| SapSenderParty | header | 3 | party [scripts/addPartyForXIInbound.groovy#L10] |
| tenantStage, partnerID | header | 4 | both set: determination skipped [scripts/determinePartnerID.groovy#L18] |
| SapAuthenticatedUserName | header | 4 | user to partner [scripts/determinePartnerID.groovy#L372] |
| customXError_EndEvent | header | 22 | end event [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "End event?"] |

## Partner directory

- Stage: landscapeStage of the sender [scripts/determinePartnerID.groovy#L78] or user partner [scripts/determinePartnerID.groovy#L374], else PID SAP_Integration_Suite_Landscape by tenant name, default "PRD" [scripts/determinePartnerID.groovy#L126].
- Sender alias: alternative partner, agency stage, scheme BusinessSystemName or LogicalSystemName [scripts/determinePartnerID.groovy#L183] [scripts/determinePartnerID.groovy#L205].
- Partner ID: alternative partner by sender alias and interface [scripts/determinePartnerID.groovy#L323]; missing: `<agency>~<interface>` [scripts/determinePartnerID.groovy#L338], or an exception when CustomXPid_SwitchOffTilde = "true" [scripts/determinePartnerID.groovy#L332].
- CustomXPreEnabled, CustomXPreEndpoint; headers win [scripts/readCustomXFromPD.groovy#L41].
- InboundQueue; missing: default queue [scripts/readInboundQueueFromPD.groovy#L21].
- receiverDetermination; missing: CBR route [scripts/readBypassOptionFromPD.groovy#L22]. Then interfaceDetermination_<receiver>: p2p, missing: skipRcvDet [scripts/readBypassOptionFromPD.groovy#L36], refused here (steps 45, 46).
- Receiver system: alternative partner, scheme BusinessSystemName; missing: alias kept [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36].

PD scripts throw when the header partnerID is missing [scripts/readInboundQueueFromPD.groovy#L17].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `createHeadersFromSapDynamicConfiguration.groovy` | 2 | dynamic configuration [scripts/createHeadersFromSapDynamicConfiguration.groovy#L25] |
| `addPartyForXIInbound.groovy` | 3 | sender party [scripts/addPartyForXIInbound.groovy#L15] |
| `determinePartnerID.groovy` | 4 | stage, sender, partner ID; header rollback on error [scripts/determinePartnerID.groovy#L47] |
| `readCustomXFromPD.groovy` | 8 | scenario exit [scripts/readCustomXFromPD.groovy#L52] |
| `readInboundQueueFromPD.groovy` | 26 | inbound queue [scripts/readInboundQueueFromPD.groovy#L21] |
| `addCustomHeaderProperties.groovy` | 27, 39 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L69] |
| `readBypassOptionFromPD.groovy` | 40 | bypass option [scripts/readBypassOptionFromPD.groovy#L36] |
| `mapReceiverAliasToBusinessSystemName.groovy` | 47 | receiver system [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36] |
| `throwException.groovy` | 25, 46, 51 | throws exceptionType [scripts/throwException.groovy#L17] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | audit log, Base64/JSON [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |
| `logMessage.groovy`, `auditLogHandler.groovy` | none | MPL and audit log attachments [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| XIEntryPoint | — | sender address [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "XIEntryPoint"] | `{{tenant.endpoint.xi.inbound}}` |
| PipelinePrefix | — | queue and sync prefix [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "PipelinePrefix"] | `{{tenant.pipeline.prefix}}` |
| DefaultJMSQueueNumber | — | X01 or Q01 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "DefaultJMSQueueNumber"] | no |
| UserRole, SubjectDN, IssuerDN, CommunicationParty | — | role, certificate, party [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "XI" "Sender"] | `{{tenant.security.sender.role}}`, `{{tenant.security.cert.subjectdn}}`, `{{tenant.security.cert.issuerdn}}`, `{{tenant.xi.communication.party}}` |
| SenderAuthType, BodySize, AttachmentSize; JMS_CompressStoredMessage, JMS_EncryptStoredMessage, JMS_RetentionThreshold, JMS_ExpirationPeriod | — | sender [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "XI" "Sender"]; JMS storage [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"] | no |
| CustomXPreGlobal_Enabled, CustomXError_Enabled, CustomXPid_SwitchOffTilde | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Set SAP headers"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Three event subprocesses use custom error handling if CustomXError_Enabled = 'true', else standard [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 14 "Custom Extension?"].
- Standard: under BestEffort the caller gets `${exception.message}` as body, status SyncCallFailed; then "Error End 3" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Return exception message"].
- Custom: exception headers to `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomErrorFlow"]; its header customXError_EndEvent selects Completed, Escalated or, by default, "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "End event?"].
- Refused options (EOIO, unknown QoS, skipRcvDet under BestEffort) set ConfigurationOptionNotSupported and throw [scripts/throwException.groovy#L34].
- A partner ID error restores the original headers before rethrowing [scripts/determinePartnerID.groovy#L47].

## Extension points

- Global exit: CustomXPreGlobal_Enabled = 'true' and CustomXPreGlobal_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "CustomXPreGlobal_Enabled"].
- Scenario exit per partner ID: PD CustomXPreEnabled listing step "01", and CustomXPreEndpoint [scripts/readCustomXFromPD.groovy#L53].
- Custom error flow: CustomXError_Enabled and CustomXError_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "CustomXError_Enabled"].
- Bypass per partner ID for BestEffort: PD receiverDetermination plus interfaceDetermination_<receiver> [scripts/readBypassOptionFromPD.groovy#L36].
- Partner-specific inbound queue: PD InboundQueue [scripts/readInboundQueueFromPD.groovy#L21].
- Monitoring: any `customHeader_<name>` property becomes an MPL custom header [scripts/addCustomHeaderProperties.groovy#L44].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
