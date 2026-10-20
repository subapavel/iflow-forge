# Pipeline v2 Generic Step05 - Interface Determination (com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination` 1.0.3, package generic-pipelines, label 2026-06-trial.

## Purpose

Interface determination per receiver: consumes `{{PipelineJMSQueuePrefix}}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"], written by Step02 inbound on bypass option skipRcvDet [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InterfaceDeterminationQueue"] and by Step04 when its result carries no interfaces [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_InterfaceDetermination" "InterfaceDeterminationQueue"]. Interfaces come from a Partner Directory XSLT or a custom flow (step 33); the message is split per interface (step 47 sets SAP_OutboundProcessingEndpoint) and goes to `{{PipelineJMSQueuePrefix}}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_Default" "OuboundProcessingQueue"] (Step06 reads a suffixed name, see pitfalls) or a receiver-specific queue (step 51).

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | JMS | InterfaceDeterminationQueue | `{{PipelineJMSQueuePrefix}}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"] |
| Receiver | JMS | OuboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q04`, from end event "End" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_Default" "OuboundProcessingQueue"] |
| Receiver | JMS | ReceiverSpecificQueue | `${property.receiverSpecificQueueName}`, from end event "End 2" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_ReceiverSpecific" "ReceiverSpecificQueue"] |
| Receiver | JMS | DeadLetterQueue | `{{PipelineJMSQueuePrefix}}Q03_DLQ` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "DeadLetterQueue"] |
| Receiver | ProcessDirect | ReceiverXID | `${property.customXIDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "ReceiverXID"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
2. Enricher "Set custom extensions"
3. Script "Check well formed XML": `checkWellFormedXML.groovy`
4. Script "Store payload": `storePayloadInXProperty.groovy`
5. ExclusiveGateway "Max # of retries reached?": SAPJMSRetries > maxJMSRetries to 58
6. Enricher "Initialize pre-processing exit": CustomXType = CustomXPre
7. ProcessCallElement "Call custom exit"
  8. Enricher "Initialize exit"
  9. Script "Check scenario-specific exit": `readCustomXFromPD.groovy`
  10. ExclusiveGateway "Global exit?": CustomXGlobalBoolean to 13, else 11
  11. ExclusiveGateway "Scenario-specific exit?": CustomXBoolean to 12, else end
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomXScenario"]
  13. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomXGlobal"], then 11
  14. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    15. ExclusiveGateway "Custom Extension?"
    16. ProcessCallElement "Call standard retry handling"
      17. ExclusiveGateway "Test mode?": testMode to 24
      18. ExclusiveGateway "Max # of retries reached?": MaxRetriesExceeded to 19, else 23
      19. Enricher "Define DLQ"
      20. Script "Attach additional information": `attachAdditionallnformation.groovy`
      21. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "DeadLetterQueue"]
      22. ExclusiveGateway "End event?": DLQ_EndEvent Completed to "End 1", else "Escalation End 1"
      23. Enricher "Set custom status": AutomaticRetry, "Error End 1"
      24. Enricher "Set custom status": ErrorInTestMode
      25. Script "Attach additional information": `attachAdditionallnformation.groovy`, "Escalation End 4"
    26. ProcessCallElement "Call custom error handling"
      27. Enricher "Set headers"
      28. Script "Get payload": `fetchPayloadFromXProperty.groovy`
      29. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomErrorFlow"]
      30. Enricher "Set custom status": from header customXError_Status
      31. ExclusiveGateway "End event?": customXError_EndEvent Completed, Escalated, else "Error End 3"
32. Script "Read custom interface determination": `readCustomInterfaceDeterminationFromPD.groovy`
33. ExclusiveGateway "Custom ID?": customXIDBoolean to 52, else 34
34. ProcessCallElement "Call XSLT option"
  35. ExclusiveGateway "XML?": isWellFormedXml false to 38, else 36
  36. Enricher "Get XSLT mapping from Partner Directory": `pd:${header.partnerID}:interfaceDetermination_${header.SAP_ReceiverAlias}:Binary`
  37. Mapping "XSLT Mapping": XSLT from header XSLTMapping
  38. Enricher "Define dummy xml": `<dummy></dummy>`, then 36
  39. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    40. ExclusiveGateway "Custom Extension?"
    41. ProcessCallElement "Call standard retry handling"
    42. ProcessCallElement "Call custom error handling"
43. ExclusiveGateway "Router": count(//Interface) = 0 to 44, else 45
44. Enricher "Set custom status": InterfaceNotFoundError, "Error End"
45. Script "Get Receiver specific queue from Partner Directory": `readReceiverSpecificQueueFromPD.groovy`
46. Splitter "Iterating splitter": /ns0:Interfaces/Interface, streaming, sequential, stop on exception
47. Enricher "Get interface"
48. Script "Log outbound processing endpoint": `logOutboundProcessingEndpoint.groovy`
49. Script "Get payload": `fetchPayloadFromXProperty.groovy`
50. Enricher "Remove header": customXPipelineStep
51. ExclusiveGateway "Receiver specific?": receiverSpecificQueueBoolean to "End 2" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_ReceiverSpecific" "ReceiverSpecificQueue"], else "End" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_Default" "OuboundProcessingQueue"]
52. ProcessCallElement "Call custom extension option": then 43
  53. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "ReceiverXID"]
  54. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    55. ExclusiveGateway "Custom Extension?"
    56. ProcessCallElement "Call custom error handling"
    57. ProcessCallElement "Call standard retry handling"
58. Enricher "Set custom status": MaxRetriesExceeded, "Error End 4"
59. ErrorEventSubProcessTemplate "Exception Subprocess"
  60. ExclusiveGateway "Custom Extension?"
  61. ProcessCallElement "Call standard retry handling"
  62. ProcessCallElement "Call custom error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| partnerIDOld; parameter copies | property | 2 | `${header.SAP_Sender}~${header.SAP_SenderInterface}~${header.SAP_Receiver}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Set custom extensions"] |
| isWellFormedXml; payload | header; property | 3; 4 | [scripts/checkWellFormedXML.groovy#L12] [scripts/storePayloadInXProperty.groovy#L15] |
| customXPipelineStep | header | 9, removed 50 | two digits after "generic.step" of camelId [scripts/readCustomXFromPD.groovy#L28] |
| customXIDBoolean, customXIDEndpoint | property | 32 | [scripts/readCustomInterfaceDeterminationFromPD.groovy#L24] [scripts/readCustomInterfaceDeterminationFromPD.groovy#L28] |
| receiverSpecificQueueName, receiverSpecificQueueBoolean | property | 45 | [scripts/readReceiverSpecificQueueFromPD.groovy#L28] [scripts/readReceiverSpecificQueueFromPD.groovy#L31] |
| SAP_ReceiverInterface, SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint | header | 47 | /Interface/Name, /Interface/Index, /Interface/Service [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] |
| incomingQueue | property, header | 19, 27 | `${property.PipelineJMSQueuePrefix}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Define DLQ"] |
| exceptionSourceMplId, exceptionCustomStatus, exceptionTimestamp, exceptionStacktrace, exceptionMessage, pipelineStepID | header | 27 | [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Set headers"] |
| customHeaderProperties; auditLogHeader | header | 1; scripts | Base64 JSON [scripts/addCustomHeaderProperties.groovy#L75]; audit log [scripts/PipelineLogger.groovy#L69] |

### Read

Headers and properties from outside this flow (Step02 inbound, Step04, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAPJMSRetries, maxJMSRetries | header | 5 | retry limit; maxJMSRetries is in the allowed header list [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, property "allowedHeaderList"] |
| partnerID | header | 9, 32, 36, 45 | missing: exception in 9, 32 [scripts/readCustomXFromPD.groovy#L21] [scripts/readCustomInterfaceDeterminationFromPD.groovy#L17] |
| SAP_ReceiverAlias | header | 36, 45 | XSLT and queue key [scripts/readReceiverSpecificQueueFromPD.groovy#L15] |
| CustomXPreEnabled, CustomXPreEndpoint | header | 9 | override the PD [scripts/readCustomXFromPD.groovy#L38] [scripts/readCustomXFromPD.groovy#L45] |
| customHeaderProperties | header | 1 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L11] |
| testMode, customXError_Status, customXError_EndEvent | header | 17, 30, 31 | [scripts/attachAdditionallnformation.groovy#L16] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 31 "End event?"] |

## Partner directory

- CustomXPreEnabled, CustomXPreEndpoint (String) of partnerID, unless set as headers [scripts/readCustomXFromPD.groovy#L41] [scripts/readCustomXFromPD.groovy#L48]; Enabled is a `|` list of step numbers matched against customXPipelineStep [scripts/readCustomXFromPD.groovy#L53]; either missing: no scenario exit [scripts/readCustomXFromPD.groovy#L52].
- CustomXIDEndpoint (String) of partnerID: custom determination flow; missing: customXIDBoolean 'false', XSLT option [scripts/readCustomInterfaceDeterminationFromPD.groovy#L21] [scripts/readCustomInterfaceDeterminationFromPD.groovy#L24].
- interfaceDetermination_<SAP_ReceiverAlias> (Binary) of partnerID: the XSLT (step 36); missing: no step checks it (pitfalls).
- ReceiverSpecificQueue_<SAP_ReceiverAlias> of partnerID, then ReceiverSpecificQueue of the alias; missing: Q04 [scripts/readReceiverSpecificQueueFromPD.groovy#L19] [scripts/readReceiverSpecificQueueFromPD.groovy#L23]. Read before the split (step 45).

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `storePayloadInXProperty.groovy`, `fetchPayloadFromXProperty.groovy` | 4; 28, 49 | payload backup, restore [scripts/storePayloadInXProperty.groovy#L13] [scripts/fetchPayloadFromXProperty.groovy#L15] |
| `readCustomXFromPD.groovy`, `readCustomInterfaceDeterminationFromPD.groovy`, `readReceiverSpecificQueueFromPD.groovy` | 9, 32, 45 | PD reads (Partner directory) |
| `checkWellFormedXML.groovy`, `addCustomHeaderProperties.groovy`, `logOutboundProcessingEndpoint.groovy`, `attachAdditionallnformation.groovy` | as in Steps | XML check, MPL headers, audit log, MPL attachment [scripts/checkWellFormedXML.groovy#L7] [scripts/addCustomHeaderProperties.groovy#L69] [scripts/logOutboundProcessingEndpoint.groovy#L9] [scripts/attachAdditionallnformation.groovy#L32] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | audit log, Base64 helper [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |
| `logMessage.groovy`, `auditLogHandler.groovy` | none | no step or script references them [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelineJMSQueuePrefix | — | queue prefix [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, parameter "PipelineJMSQueuePrefix"] | `{{tenant.pipeline.prefix}}` |
| JMS_ConcurrentProcesses, JMS_RetryInterval, JMS_MaxRetryInterval, JMS_ExponentialBackoff, JMS_DLQ | — | consumption, retry of Q03 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "InterfaceDeterminationQueue"] | no |
| JMS_OQ_*, JMS_RS_*, DLQ_* (CompressStoredMessage, EncryptStoredMessage, RetentionThreshold, ExpirationPeriod) | — | storage of Q04, receiver queue, DLQ [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_Default" "OuboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_ReceiverSpecific" "ReceiverSpecificQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS" "DeadLetterQueue"] | no |
| DLQ_EndEvent, CustomXPreGlobal_Enabled, CustomXError_Enabled | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Four event subprocesses; custom if CustomXError_Enabled = 'true' [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 15 "Custom Extension?"].
- Standard: test messages escalate; status MaxRetriesExceeded (step 58, then "Error End 4"; that this triggers the event subprocess is not shown) sends the payload to `{{PipelineJMSQueuePrefix}}Q03_DLQ`, other errors get AutomaticRetry and "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 18 "Max # of retries reached?"].
- Custom: payload, exception headers to `{{CustomXError_Endpoint}}`; customXError_EndEvent picks Completed, Escalated or "Error End 3" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 31 "End event?"].

## Extension points

- Parameters: global exit, error flow. PD per partner ID: scenario exit at step 05, custom determination flow `${property.customXIDEndpoint}` [scripts/readCustomInterfaceDeterminationFromPD.groovy#L21], interface XSLT, receiver queue.
- The XSLT or custom flow returns `/ns0:Interfaces/Interface` with Name, Index, Service (steps 46, 47); ns0 is `http://sap.com/xi/XI/System` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, property "namespaceMapping"].

## Version notes

- 2026-09-current 1.0.4: backup/restore of payload, headers, attachments [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 5 "Backup Message"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 28 "Restore Message"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/backupMessage.groovy#L32] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/backupMessage.groovy#L33] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/backupMessage.groovy#L34] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/restoreMessage.groovy#L11] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/restoreMessage.groovy#L12] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/restoreMessage.groovy#L13]; sender queue as MPL custom header [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 3 "Add sender queue to custom headers"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/addSenderJMSQueueToCustomHeaders.groovy#L11] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: the retry gateway tests stopRetry or skipRetry; "interface not determined" sets skipRetry [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 19 "Max # of retries reached?"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 45 "Set custom status"] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: MPL error text read after the last retry via CI_ODataAPI_* [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 61 "Get Original Exception"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 60 "Set custom status & API"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, parameter "CI_ODataAPI_URL"] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
