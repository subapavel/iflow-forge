# Pipeline v2 Generic Step04 - Receiver Determination (com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination` 1.0.4, package generic-pipelines, label 2026-06.

## Purpose

Receiver determination of the decoupled pipeline [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "DefaultJMSQueueNumber"]: consumes `{{PipelineJMSQueuePrefix}}Q02` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverDeterminationQueue"], written by Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "ReceiverDeterminationQueue"]. Receivers: Partner Directory XSLT, custom flow or reused PO determination (step 32), split per receiver and stage. Without interfaces in the result: Q03 (step 54), read by Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"]; with them, per interface Q04 (Step06 suffix: see message-path.md) or a receiver-specific queue (step 62).

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | JMS | ReceiverDeterminationQueue | `{{PipelineJMSQueuePrefix}}Q02` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverDeterminationQueue"] |
| Receiver | JMS | InterfaceDeterminationQueue | `{{PipelineJMSQueuePrefix}}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"] |
| Receiver | JMS | OuboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"] |
| Receiver | JMS | ReceiverSpecificQueue | `${property.receiverSpecificQueueName}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverSpecificQueue"] |
| Receiver | JMS | DeadLetterQueue | `{{PipelineJMSQueuePrefix}}Q02_DLQ` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "DeadLetterQueue"] |
| Receiver | ProcessDirect | ReceiverXRD | `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
2. Enricher "Set custom extensions"
3. Script "Check well formed XML": `checkWellFormedXML.groovy`
4. Script "Store payload": `storePayloadInXProperty.groovy`
5. ExclusiveGateway "Max # of retries reached?": SAPJMSRetries > maxJMSRetries to 79
6. Enricher "Initialize pre-processing exit"
7. ProcessCallElement "Call custom exit"
  8. Enricher "Initialize exit"
  9. Script "Check scenario-specific exit": `readCustomXFromPD.groovy`
  10. ExclusiveGateway "Global exit?"
  11. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomXGlobal"]
  12. ExclusiveGateway "Scenario-specific exit?"
  13. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomXScenario"]
  14. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    15. ExclusiveGateway "Custom Extension?"
    16. ProcessCallElement "Call custom error handling"
      17. Enricher "Set headers"
      18. Script "Get payload": `fetchPayloadFromXProperty.groovy`
      19. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomErrorFlow"]
      20. Enricher "Set custom status"
      21. ExclusiveGateway "End event?"
    22. ProcessCallElement "Call standard retry handling"
      23. ExclusiveGateway "Test mode?": testMode
      24. ExclusiveGateway "Max # of retries reached?": MaxRetriesExceeded to 25
      25. Enricher "Define DLQ"
      26. Script "Attach additional information": `attachAdditionallnformation.groovy`
      27. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "DeadLetterQueue"]
      28. ExclusiveGateway "End event?"
      29. Enricher "Set custom status": ErrorInTestMode
      30. Script "Attach additional information": `attachAdditionallnformation.groovy`
31. Script "Read custom receiver determination": `readCustomReceiverDeterminationFromPD.groovy`
32. ExclusiveGateway "Custom RD?": customXRDBoolean or reuseXRDBoolean to 70, else 33
33. ProcessCallElement "Call XSLT option"
  34. ExclusiveGateway "XML?": not XML to 35
  35. Enricher "Define dummy xml": body `<dummy></dummy>`
  36. Enricher "Get XSLT mapping from Partner Directory": `pd:${header.partnerID}:receiverDetermination:Binary`
  37. Mapping "XSLT Mapping"
  38. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    39. ExclusiveGateway "Custom Extension?"
    40. ProcessCallElement "Call custom error handling"
    41. ProcessCallElement "Call standard retry handling"
42. ExclusiveGateway "Any Receiver?": none to 43, else 68
43. ExclusiveGateway "Not found type?": ReceiverNotDetermined/Type Default to 44, Ignore to 66, else 67
44. Enricher "Get default receiver name"
45. Enricher "Set custom status": ReceiverNotFoundDefault, then 46
46. ExclusiveGateway "Virtual Stage Routing?": tenantStageVirtual to 47, else 50
47. Enricher "Store Receivers XML"
48. Script "Map to Receiver Stage": `mapSenderStageToReceiverStage.groovy`
49. ExclusiveGateway "Multiple Stages?": several to 63, else 50
50. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
51. ExclusiveGateway "Bypass interface determination?": count(//Interface) > 0 to 55, else 52
52. Script "Get payload": `fetchPayloadFromXProperty.groovy`
53. Enricher "Remove header"
54. Script "Add Custom Header": `addCustomHeaderProperties.groovy`, then [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"]
55. Script "Get Receiver specific queue from Partner Directory": `readReceiverSpecificQueueFromPD.groovy`
56. Splitter "Iterating splitter": //Interfaces/Interface
57. Enricher "Get interface": bypassOption = skipIfDet
58. Script "Log outbound processing endpoint": `logOutboundProcessingEndpoint.groovy`
59. Script "Get payload": `fetchPayloadFromXProperty.groovy`
60. Enricher "Remove header"
61. Script "Add Custom Header": `addCustomHeaderProperties.groovy`
62. ExclusiveGateway "Receiver specific?": receiverSpecificQueueBoolean [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverSpecificQueue"], else [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"]
63. Splitter "Split Receiver Stages"
64. Enricher "Set Tenant Stage"
65. Enricher "Retrieve Receivers XML": then 50
66. Enricher "Set custom status": ReceiverNotFoundIgnored, "End"
67. Enricher "Set custom status": ReceiverNotFoundError, "Error End"
68. Splitter "Iterating splitter": /ns0:Receivers/Receiver
69. Enricher "Get receiver name": then 46
70. ProcessCallElement "Call custom X or XRD option"
  71. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "ReceiverXRD"]
  72. ExclusiveGateway "Reuse XRD?": reuseXRDBoolean to 73, else end
  73. Script "Read receiver not determined from Partner Directory": `readReceiverNotDeterminedFromPD.groovy`
  74. Mapping "Map to Receivers": message mapping AddReceiverNotDeterminedNode
  75. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    76. ExclusiveGateway "Custom Extension?"
    77. ProcessCallElement "Call custom error handling"
    78. ProcessCallElement "Call standard retry handling"
79. Enricher "Set custom status": MaxRetriesExceeded, "Error End 5"
80. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  81. ExclusiveGateway "Custom Extension?"
  82. ProcessCallElement "Call standard retry handling"
  83. ProcessCallElement "Call custom error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SAP_MessageType; parameter copies | header; property | 2 | SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Set custom extensions"] |
| isWellFormedXml; payload | header; property | 3; 4 | [scripts/checkWellFormedXML.groovy#L12] [scripts/storePayloadInXProperty.groovy#L15] |
| SAP_ReceiverAlias, SAP_ReceiverInterface, SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint, tenantStage, SAP_Receiver | header | 44, 69, 57, 48, 64, 50 | [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get receiver name"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Get interface"] [scripts/mapSenderStageToReceiverStage.groovy#L251] [scripts/mapReceiverAliasToBusinessSystemName.groovy#L55] |
| incomingQueue | property, header | 25, 17 | `${property.PipelineJMSQueuePrefix}Q02` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Define DLQ"] |

### Read

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAPJMSRetries, maxJMSRetries | header | 5 | retry limit; maxJMSRetries unset here (pitfalls) [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 5 "Max # of retries reached?"] |
| partnerID | header | 31 | missing: exception [scripts/readCustomReceiverDeterminationFromPD.groovy#L17] |
| tenantStage, SapQualityOfService | header | 48 | stage routing [scripts/mapSenderStageToReceiverStage.groovy#L42] [scripts/mapSenderStageToReceiverStage.groovy#L64] |
| testMode, customXRDEndpoint, customXError_Status, customXError_EndEvent | header | 23, 31, 20, 21 | [scripts/readCustomReceiverDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 20 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 21 "End event?"] |

## Partner directory

- CustomXPreEnabled, CustomXPreEndpoint; missing: no exit [scripts/readCustomXFromPD.groovy#L41].
- CustomXRDEndpoint; missing: ReuseXRDEndpoint [scripts/readCustomReceiverDeterminationFromPD.groovy#L35]; that missing: XSLT [scripts/readCustomReceiverDeterminationFromPD.groovy#L38].
- receiverDetermination (Binary): the XSLT (step 36); missing: open-questions/kb-12b.md. String form (static): read by Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L22], no Step04 script (grep).
- Reused XRD only: ReceiverNotDeterminedType; missing: 'Error' [scripts/readReceiverNotDeterminedFromPD.groovy#L25]. Type Default: ReceiverNotDeterminedDefault; missing: exception [scripts/readReceiverNotDeterminedFromPD.groovy#L31].
- Landscape stages: parameter `{{tenant.name}}` of SAP_Integration_Suite_Landscape; missing: "PRD" [scripts/mapSenderStageToReceiverStage.groovy#L40]. Receiver stages: alternative partner per stage; none: no stage routing [scripts/mapSenderStageToReceiverStage.groovy#L80].
- Known stage: stageRule_<stage>~<receiver alias>, stageRule_<stage> (partnerID, then alias) [scripts/mapSenderStageToReceiverStage.groovy#L145]; missing: exact stage [scripts/mapSenderStageToReceiverStage.groovy#L164], then the same with `{{tenant.name}}` [scripts/mapSenderStageToReceiverStage.groovy#L170], else exception [scripts/mapSenderStageToReceiverStage.groovy#L189]; value `>Stage1,Stage2`. Unknown stage: tenant rules, the only stage, else exception [scripts/mapSenderStageToReceiverStage.groovy#L112].
- ReceiverSpecificQueue_<SAP_ReceiverAlias> of partnerID, then ReceiverSpecificQueue of the alias; missing: Q04 [scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Receiver system: alternative partner, agency tenantStage or "PRD"; missing: alias kept [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `storePayloadInXProperty.groovy`, `fetchPayloadFromXProperty.groovy` | 4; 18, 52, 59 | payload backup, restore [scripts/storePayloadInXProperty.groovy#L13] [scripts/fetchPayloadFromXProperty.groovy#L15] |
| `readCustomXFromPD.groovy`, `readCustomReceiverDeterminationFromPD.groovy`, `readReceiverNotDeterminedFromPD.groovy`, `readReceiverSpecificQueueFromPD.groovy`, `mapReceiverAliasToBusinessSystemName.groovy`, `mapSenderStageToReceiverStage.groovy` | as in Steps | Partner directory |
| `checkWellFormedXML.groovy`, `addCustomHeaderProperties.groovy`, `logOutboundProcessingEndpoint.groovy`, `attachAdditionallnformation.groovy` | as in Steps | XML check, MPL headers, audit log, MPL attachment [scripts/checkWellFormedXML.groovy#L7] [scripts/addCustomHeaderProperties.groovy#L69] [scripts/logOutboundProcessingEndpoint.groovy#L9] [scripts/attachAdditionallnformation.groovy#L32] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy`; `logMessage.groovy`, `auditLogHandler.groovy` | library; none | [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |
| mapping/AddReceiverNotDeterminedNode.mmap, xsd/Receivers.xsd | 74 | mapping and its schema [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Map to Receivers"] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelineJMSQueuePrefix | — | queue prefix [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, parameter "PipelineJMSQueuePrefix"] | `{{tenant.pipeline.prefix}}` |
| JMS_ConcurrentProcesses, JMS_RetryInterval, JMS_MaxRetryInterval, JMS_ExpotentialBackoff, JMS_DLQ | — | consumption, retry [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverDeterminationQueue"] | no |
| JMS_*, JMS_OQ_*, JMS_RS_*, DLQ_* (CompressStoredMessage, EncryptStoredMessage, RetentionThreshold, ExpirationPeriod) | — | storage of Q03, Q04, receiver queue, DLQ [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "ReceiverSpecificQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS" "DeadLetterQueue"] | no |
| DLQ_EndEvent, CustomXPreGlobal_Enabled, CustomXError_Enabled | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Four event subprocesses; custom if CustomXError_Enabled = 'true' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 15 "Custom Extension?"].
- Standard: test messages escalate; status MaxRetriesExceeded (step 79) sends to the DLQ, other errors: "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 24 "Max # of retries reached?"].
- Custom: payload, exception headers to `{{CustomXError_Endpoint}}`; customXError_EndEvent picks Completed, Escalated or "Error End 4" [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 21 "End event?"].

## Extension points

- Parameters: global exit, error flow. PD per partner ID: scenario exit, receiver XSLT, custom determination flow [scripts/readCustomReceiverDeterminationFromPD.groovy#L25], reused XRD, receiver queue, stage rules.

## Version notes

- 2026-09 1.0.5: backup and restore of payload, headers, attachments [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/backupMessage.groovy#L32] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/backupMessage.groovy#L33] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/backupMessage.groovy#L34] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/restoreMessage.groovy#L11] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/restoreMessage.groovy#L12] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/restoreMessage.groovy#L13]; retry gateway on stopRetry or skipRetry; after the last retry the MPL error text read [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 25 "Max # of retries reached?"] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 83 "Get Original Exception"]; queue names as MPL custom headers [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 3 "Add sender queue to custom headers"] [2026-09: com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 56 "Add receiver queue to custom headers"] — see versions/2026-09/changelog-generic-pipelines-step04-06.md
