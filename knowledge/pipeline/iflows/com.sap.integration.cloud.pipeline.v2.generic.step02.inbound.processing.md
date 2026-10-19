# Pipeline v2 Generic Step02 - Inbound Processing (com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing` 1.0.5, package generic-pipelines, label 2026-06-trial.

## Purpose

Fully decoupled second step: consumes `{{PipelineJMSQueuePrefix}}Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"], fed by Step01 when its queue number is Q01 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "DefaultJMSQueueNumber"] (prefixes: see pitfalls). It determines the partner ID [scripts/determinePartnerID.groovy#L18], runs the exits and an optional inbound conversion [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "ReceiverConvert"], then routes by bypass option [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Bypass option?"]:

- default: receiver determination, Step04 [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverDeterminationQueue"];
- skipRcvDet: interface determination, Step05 [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InterfaceDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"];
- p2p: outbound processing on `{{PipelineJMSQueuePrefix}}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "OutboundProcessingQueue"]; Step06 reads a suffixed name (see pitfalls) [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "OutboundProcessingQueue"], or a receiver-specific queue [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverSpecificOutboundQueue"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | JMS | InboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q01`, `{{JMS_ConcurrentProcesses}}`, retry and DLQ parameters [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"] |
| Receiver | JMS | ReceiverDeterminationQueue | `{{PipelineJMSQueuePrefix}}Q02` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverDeterminationQueue"] |
| Receiver | JMS | InterfaceDeterminationQueue | `{{PipelineJMSQueuePrefix}}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InterfaceDeterminationQueue"] |
| Receiver | JMS | OutboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "OutboundProcessingQueue"] |
| Receiver | JMS | ReceiverSpecificOutboundQueue | `${property.receiverSpecificQueueName}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverSpecificOutboundQueue"] |
| Receiver | JMS | DeadLetterQueue | `{{PipelineJMSQueuePrefix}}Q01_DLQ` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "DeadLetterQueue"] |
| Receiver | ProcessDirect | ReceiverConvert | `${property.inbConvEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "ReceiverConvert"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Enricher "Set custom extensions": QoS ExactlyOnce, parameters
2. Script "Check well formed XML": `checkWellFormedXML.groovy` sets isWellFormedXml [scripts/checkWellFormedXML.groovy#L12]
3. Script "Store payload": `storePayloadInXProperty.groovy` [scripts/storePayloadInXProperty.groovy#L13]
4. Script "Determine partner ID": `determinePartnerID.groovy`
5. Enricher "Initialize pre-processing exit": CustomXType = CustomXPre
6. ProcessCallElement "Call custom exit"
  7. Enricher "Initialize exit"
  8. Script "Check scenario-specific exit": `readCustomXFromPD.groovy` sets CustomXBoolean, CustomXEndpoint [scripts/readCustomXFromPD.groovy#L58]
  9. ExclusiveGateway "Global exit?": CustomXGlobalBoolean = 'true' to 10, else 11
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomXGlobal"]
  11. ExclusiveGateway "Scenario-specific exit?": CustomXBoolean = 'true' to 12, else end
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomXScenario"]
  13. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    14. ExclusiveGateway "Custom Extension?": CustomXError_Enabled = 'true' to 25, else 15
    15. ProcessCallElement "Call standard retry handling"
      16. ExclusiveGateway "Test mode?": testMode = 'true' to 17, else 19
      17. Enricher "Set custom status": ErrorInTestMode
      18. Script "Attach additional information": `attachAdditionallnformation.groovy`, then "Escalation End 4" [scripts/attachAdditionallnformation.groovy#L32]
      19. ExclusiveGateway "Max # of retries reached?": yes to 20, else "Error End 2"
      20. Enricher "Set custom status": MaxRetriesExceeded, incomingQueue
      21. Script "Fetch original payload": `fetchPayloadFromXProperty.groovy` [scripts/fetchPayloadFromXProperty.groovy#L15]
      22. Script "Attach additional information": `attachAdditionallnformation.groovy` [scripts/attachAdditionallnformation.groovy#L25]
      23. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "DeadLetterQueue"]
      24. ExclusiveGateway "End event?": DLQ_EndEvent = 'Completed' to "End 2", else "Escalation End 2"
    25. ProcessCallElement "Call custom error handling"
      26. Enricher "Set headers": exception headers, incomingQueue
      27. Script "Fetch original payload": `fetchPayloadFromXProperty.groovy`
      28. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomErrorFlow"]
      29. Enricher "Set custom status": from header customXError_Status
      30. ExclusiveGateway "End event?": customXError_EndEvent
31. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
32. Script "Read retry handling": `readRetryHandlingFromPD.groovy`
33. Script "Read inbound conversion": `readInboundConversionFromPD.groovy`
34. ExclusiveGateway "Conversion?": inbConvBoolean = 'true' to 35, else 36
35. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "ReceiverConvert"]
36. Enricher "Remove header": deletes customXPipelineStep
37. Script "Read bypass option": `readBypassOptionFromPD.groovy`
38. ExclusiveGateway "Bypass option?": default to an end event [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverDeterminationQueue"], skipRcvDet 39, p2p 40
39. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`, then an end event [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InterfaceDeterminationQueue"]
40. Script "Read receiver specific queue": `readReceiverSpecificQueueFromPD.groovy`
41. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
42. ExclusiveGateway "Receiver specific?": 'true' [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverSpecificOutboundQueue"], else [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "OutboundProcessingQueue"]
43. ErrorEventSubProcessTemplate "Exception Subprocess"
  44. ExclusiveGateway "Custom Extension?"
  45. ProcessCallElement "Call custom error handling"
  46. ProcessCallElement "Call standard retry handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SapQualityOfService, SAP_MessageType | header | 1 | ExactlyOnce; SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Set custom extensions"] |
| customHeader_QualityOfService, CustomXPreGlobal_*, PipelineJMSQueuePrefix, DLQ_EndEvent, CustomXError_Enabled, CustomXPid_SwitchOffTilde | property | 1 | header SapQualityOfService; parameters [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Set custom extensions"] |
| isWellFormedXml | header | 2 | 'true' or 'false' [scripts/checkWellFormedXML.groovy#L12] |
| payload | property | 3 | body, Base64 if not XML [scripts/storePayloadInXProperty.groovy#L15] |
| tenantStage, SAP_SenderAlias, partnerID | header | 4 | as Step01 XI unless tenantStage and partnerID both arrive [scripts/determinePartnerID.groovy#L18] |
| maxJMSRetries | header | 32 | PD MaxJMSRetries, else 5 [scripts/readRetryHandlingFromPD.groovy#L28] |
| inbConvEndpoint, inbConvBoolean | property | 33 | [scripts/readInboundConversionFromPD.groovy#L36] |
| SAP_ReceiverAlias, SAP_OutboundProcessingEndpoint, bypassOption | header, property | 37 | [scripts/readBypassOptionFromPD.groovy#L36] |
| receiverSpecificQueueName, receiverSpecificQueueBoolean | property | 40 | [scripts/readReceiverSpecificQueueFromPD.groovy#L28] |
| SAP_Receiver | header | 39, 41 | [scripts/mapReceiverAliasToBusinessSystemName.groovy#L55] |
| incomingQueue | property, header | 20, 26 | `${property.PipelineJMSQueuePrefix}Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 20 "Set custom status"] |

### Read

Headers and properties that arrive from outside this flow (sender adapter, earlier flows, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAPJMSRetries, maxJMSRetries | header | 19 | retry limit [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Max # of retries reached?"] |
| testMode | header | 16 | test-mode branch [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Test mode?"] |
| customXInboundConversionEndpoint | header | 33 | overrides the PD address [scripts/readInboundConversionFromPD.groovy#L22] |
| customXError_Status, customXError_EndEvent | header | 29, 30 | status, end event [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 29 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 30 "End event?"] |

## Partner directory

- Partner ID and stage: as Step01 XI, same script [scripts/determinePartnerID.groovy#L323].
- CustomXPreEnabled, CustomXPreEndpoint of partnerID; missing: no exit [scripts/readCustomXFromPD.groovy#L41].
- MaxJMSRetries; missing: 5 [scripts/readRetryHandlingFromPD.groovy#L25].
- InboundConversionEndpoint_<SAP_SenderAlias>, then InboundConversionEndpoint; missing: no conversion [scripts/readInboundConversionFromPD.groovy#L31].
- receiverDetermination; missing: default route [scripts/readBypassOptionFromPD.groovy#L22]. Then interfaceDetermination_<receiver>: p2p, missing: skipRcvDet, to step 39 and Q03 [scripts/readBypassOptionFromPD.groovy#L36].
- ReceiverSpecificQueue_<SAP_ReceiverAlias> of partnerID, then ReceiverSpecificQueue of the receiver alias; missing: Q04 [scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Receiver system: alternative partner, scheme BusinessSystemName [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `checkWellFormedXML.groovy` | 2 | XML check [scripts/checkWellFormedXML.groovy#L7] |
| `storePayloadInXProperty.groovy` | 3 | payload backup [scripts/storePayloadInXProperty.groovy#L13] |
| `fetchPayloadFromXProperty.groovy` | 21, 27 | payload restore [scripts/fetchPayloadFromXProperty.groovy#L15] |
| `determinePartnerID.groovy` | 4 | partner ID, stage [scripts/determinePartnerID.groovy#L34] |
| `readCustomXFromPD.groovy` | 8 | scenario exit [scripts/readCustomXFromPD.groovy#L52] |
| `addCustomHeaderProperties.groovy` | 31 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L69] |
| `readRetryHandlingFromPD.groovy` | 32 | retry limit [scripts/readRetryHandlingFromPD.groovy#L25] |
| `readInboundConversionFromPD.groovy` | 33 | conversion endpoint [scripts/readInboundConversionFromPD.groovy#L26] |
| `readBypassOptionFromPD.groovy` | 37 | bypass option [scripts/readBypassOptionFromPD.groovy#L36] |
| `mapReceiverAliasToBusinessSystemName.groovy` | 39, 41 | receiver system [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36] |
| `readReceiverSpecificQueueFromPD.groovy` | 40 | receiver queue [scripts/readReceiverSpecificQueueFromPD.groovy#L19] |
| `attachAdditionallnformation.groovy` | 18, 22 | MPL attachment [scripts/attachAdditionallnformation.groovy#L32] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | audit log, Base64/JSON [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |
| `logMessage.groovy`, `auditLogHandler.groovy` | none | MPL and audit log attachments [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelineJMSQueuePrefix | — | queue prefix [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "PipelineJMSQueuePrefix"] | `{{tenant.pipeline.prefix}}` |
| JMS_ConcurrentProcesses, JMS_RetryInterval, JMS_MaxRetryInterval, JMS_ExponentialBackoff, JMS_DLQ | — | consumption, retry [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"] | no |
| JMS_IQ_*, JMS_RQ_*, JMS_OQ_*, JMS_RS_*, DLQ_* (CompressStoredMessage, EncryptStoredMessage, RetentionThreshold, ExpirationPeriod each) | — | storage of Q03, Q02, Q04, receiver-specific queue, DLQ [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InterfaceDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "OutboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverSpecificOutboundQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "DeadLetterQueue"] | no |
| DLQ_EndEvent | — | Escalated or Completed after DLQ [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "DLQ_EndEvent"] | no |
| CustomXPreGlobal_Enabled, CustomXError_Enabled, CustomXPid_SwitchOffTilde | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Both event subprocesses use custom error handling if CustomXError_Enabled = 'true', else standard [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 14 "Custom Extension?"].
- Test messages escalate with ErrorInTestMode and an MPL attachment [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Test mode?"].
- Below the retry limit: "Error End 2"; at the limit, when the limit is '0', or on a `determinePartnerID.groovy` error, the original payload goes to the DLQ with MaxRetriesExceeded [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Max # of retries reached?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "DeadLetterQueue"].
- Custom: original payload and exception headers to `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomErrorFlow"]; returned customXError_Status is the MPL status, customXError_EndEvent selects Completed, Escalated or "Error End 3" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 30 "End event?"].

## Extension points

- Global exit: CustomXPreGlobal_Enabled = 'true' and CustomXPreGlobal_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "CustomXPreGlobal_Enabled"].
- Scenario exit per partner ID: PD CustomXPreEnabled and CustomXPreEndpoint [scripts/readCustomXFromPD.groovy#L53].
- Inbound conversion flow: PD InboundConversionEndpoint, or header customXInboundConversionEndpoint [scripts/readInboundConversionFromPD.groovy#L22].
- Retry limit per partner ID: PD MaxJMSRetries [scripts/readRetryHandlingFromPD.groovy#L25].
- Bypass per partner ID: PD receiverDetermination, interfaceDetermination_<receiver> [scripts/readBypassOptionFromPD.groovy#L30].
- Receiver-specific outbound queue: PD ReceiverSpecificQueue [scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Custom error flow: CustomXError_Enabled and CustomXError_Endpoint [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, parameter "CustomXError_Enabled"].

## Version notes

None yet; Task 10 (KB-10) adds the differences to the later labels.
