# Pipeline v2 Generic Step06 - Outbound Processing (com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing` 1.0.3, package generic-outbound, label 2026-06-trial.

## Purpose

Generic outbound dispatcher: consumes `{{PipelineJMSQueuePrefix}}Q04{{PipelineJMSQueueSuffix}}` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "OutboundProcessingQueue"]; Step02 inbound on bypass option p2p [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "OutboundProcessingQueue"], Step02 async [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"], Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"] and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_Default" "OuboundProcessingQueue"] write `{{PipelineJMSQueuePrefix}}Q04`, so they connect only with an empty suffix (pitfalls). After payload backup and the pre-processing exits it hands the message to the ProcessDirect address in header SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"], set by Step05 [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step "Get interface"] or by Step02 inbound for p2p [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L33]. It owns the retry and dead-letter handling of Q04.

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | JMS | OutboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q04{{PipelineJMSQueueSuffix}}` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "OutboundProcessingQueue"] |
| Receiver | ProcessDirect | Receiver | `${header.SAP_OutboundProcessingEndpoint}`, from end event "End" [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] |
| Receiver | JMS | DeadLetterQueue | `{{PipelineJMSQueuePrefix}}Q04{{PipelineJMSQueueSuffix}}_DLQ` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "DeadLetterQueue"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
2. Script "Check well formed XML": `checkWellFormedXML.groovy`
3. Script "Store payload": `storePayloadInXProperty.groovy`
4. Enricher "Set custom extensions"
5. Enricher "Initialize pre-processing exit": CustomXType = CustomXPre
6. ProcessCallElement "Call custom exit"
  7. Enricher "Initialize exit"
  8. Script "Check scenario-specific exit": `readCustomXFromPD.groovy`
  9. ExclusiveGateway "Global exit?": CustomXGlobalBoolean to 12, else 10
  10. ExclusiveGateway "Scenario-specific exit?": CustomXBoolean to 11, else end
  11. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomXScenario"]
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomXGlobal"], then 10
  13. ErrorEventSubProcessTemplate "Exception Subprocess 1"
    14. ExclusiveGateway "Custom Extension?"
    15. ProcessCallElement "Call standard retry handling"
      16. ExclusiveGateway "Test mode?": testMode to 24
      17. ExclusiveGateway "Max # of retries reached?": SAPJMSRetries = maxJMSRetries or maxJMSRetries = '0' to 18, else 23
      18. Enricher "Set custom status": MaxRetriesExceeded; incomingQueue `${property.PipelineJMSQueuePrefix}Q04`
      19. Script "Fetch original payload": `fetchPayloadFromXProperty.groovy`
      20. Script "Attach additional information": `attachAdditionallnformation.groovy`
      21. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "DeadLetterQueue"]
      22. ExclusiveGateway "End event?": DLQ_EndEvent Completed to "End 1", else "Escalation End 1"
      23. Enricher "Set custom status": AutomaticRetry, "Error End 1"
      24. Enricher "Set custom status": ErrorInTestMode
      25. Script "Attach additional information": `attachAdditionallnformation.groovy`, "Escalation End 3"
    26. ProcessCallElement "Call custom error handling"
      27. Enricher "Set headers"
      28. Script "Fetch original payload": `fetchPayloadFromXProperty.groovy`
      29. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomErrorFlow"]
      30. Enricher "Set custom status": from header customXError_Status
      31. ExclusiveGateway "End event?": customXError_EndEvent Completed, Escalated, else "Error End 2"
32. Enricher "Set unique identifier": header UniqueID = SAP_MessageProcessingLogID
33. Enricher "Remove header": customXPipelineStep, then "End" [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"]
34. ErrorEventSubProcessTemplate "Exception Subprocess"
  35. ExclusiveGateway "Custom Extension?"
  36. ProcessCallElement "Call standard retry handling"
  37. ProcessCallElement "Call custom error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| isWellFormedXml; payload | header; property | 2; 3 | [scripts/checkWellFormedXML.groovy#L12] [scripts/storePayloadInXProperty.groovy#L15] |
| CustomXPreGlobal_Enabled, CustomXPreGlobal_Endpoint, PipelineJMSQueuePrefix, DLQ_EndEvent, CustomXError_Enabled | property | 4 | parameter copies [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set custom extensions"] |
| customXPipelineStep | header | 8, removed 33 | two digits after "generic.step" of camelId [scripts/readCustomXFromPD.groovy#L28] |
| CustomXBoolean, CustomXEndpoint | property | 8 | [scripts/readCustomXFromPD.groovy#L58] |
| UniqueID | header | 32 | MPL id [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set unique identifier"] |
| incomingQueue | property, header | 18, 27 | `${property.PipelineJMSQueuePrefix}Q04`, no suffix [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set headers"] |
| SAP_MessageProcessingLogCustomStatus | property | 18, 23, 24, 30 | MaxRetriesExceeded, AutomaticRetry, ErrorInTestMode, customXError_Status [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 23 "Set custom status"] |
| exceptionSourceMplId, exceptionTimestamp, exceptionStacktrace, exceptionMessage, pipelineStepID | header | 27 | [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set headers"] |
| customHeaderProperties; auditLogHeader | header | 1; scripts | [scripts/addCustomHeaderProperties.groovy#L75] [scripts/PipelineLogger.groovy#L69] |

### Read

Headers and properties from outside this flow (pipeline headers from Q04, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAP_OutboundProcessingEndpoint | header | end event "End" | target address [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"] |
| SAPJMSRetries, maxJMSRetries | header | 17 | DLQ decision; maxJMSRetries is in the allowed header list [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, property "allowedHeaderList"] |
| partnerID | header | 8 | missing: exception [scripts/readCustomXFromPD.groovy#L21] |
| CustomXPreEnabled, CustomXPreEndpoint | header | 8 | override the PD [scripts/readCustomXFromPD.groovy#L38] [scripts/readCustomXFromPD.groovy#L45] |
| customHeaderProperties | header | 1 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L11] |
| testMode, customXError_Status, customXError_EndEvent | header | 16, 30, 31 | [scripts/attachAdditionallnformation.groovy#L16] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 31 "End event?"] |

## Partner directory

- CustomXPreEnabled, CustomXPreEndpoint (String) of partnerID, unless set as headers [scripts/readCustomXFromPD.groovy#L41] [scripts/readCustomXFromPD.groovy#L48]; Enabled is a `|` list of step numbers matched against customXPipelineStep (here 06) [scripts/readCustomXFromPD.groovy#L53]; either missing: no scenario exit [scripts/readCustomXFromPD.groovy#L52].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `storePayloadInXProperty.groovy`, `fetchPayloadFromXProperty.groovy` | 3; 19, 28 | payload backup, restore before DLQ and custom error flow [scripts/storePayloadInXProperty.groovy#L13] [scripts/fetchPayloadFromXProperty.groovy#L15] |
| `readCustomXFromPD.groovy` | 8 | exit settings (Partner directory) |
| `checkWellFormedXML.groovy`, `addCustomHeaderProperties.groovy` | 2; 1 | XML check, MPL custom headers [scripts/checkWellFormedXML.groovy#L7] [scripts/addCustomHeaderProperties.groovy#L69] |
| `attachAdditionallnformation.groovy` | 20, 25 | MPL attachment; outside test mode with incomingQueue it logs the move to incomingQueue + `_DLQ` [scripts/attachAdditionallnformation.groovy#L11] [scripts/attachAdditionallnformation.groovy#L25] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy` | library | audit log, Base64 helper [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] |
| `throwException.groovy`, `logMessage.groovy`, `auditLogHandler.groovy` | none | no step or script references them [scripts/throwException.groovy#L17] [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |
| scenarioflows/integrationflow/PIP_02_Generic_InboundProcessing.iflw | — | the flow model; named like Step02 inbound [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, resource "scenarioflows/integrationflow/PIP_02_Generic_InboundProcessing.iflw"] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelineJMSQueuePrefix | — | queue prefix [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, parameter "PipelineJMSQueuePrefix"] | `{{tenant.pipeline.prefix}}` |
| PipelineJMSQueueSuffix | — | suffix of Q04 and its DLQ, for a receiver-specific outbound flow; else empty [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, parameter "PipelineJMSQueueSuffix"] | `{{tenant.pipeline.outbound.suffix}}` |
| JMS_ConcurrentProcesses, JMS_RetryInterval, JMS_MaxRetryInterval, JMS_ExponentialBackoff, JMS_DLQ | — | consumption, retry of Q04 [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "OutboundProcessingQueue"] | no |
| DLQ_CompressStoredMessage, DLQ_EncryptStoredMessage, DLQ_RetentionThreshold, DLQ_ExpirationPeriod | — | DLQ storage [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "DeadLetterQueue"] | no |
| DLQ_EndEvent, CustomXPreGlobal_Enabled, CustomXError_Enabled | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Two event subprocesses; custom if CustomXError_Enabled = 'true' [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 35 "Custom Extension?"].
- Standard: test messages get ErrorInTestMode and escalate; when SAPJMSRetries equals maxJMSRetries or maxJMSRetries is '0', the original payload goes to the DLQ and DLQ_EndEvent picks "End 1" or "Escalation End 1"; else AutomaticRetry and "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 17 "Max # of retries reached?"]. No retry gateway in the main flow; Step05 has one [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 5 "Max # of retries reached?"].
- Custom: original payload, exception headers to `{{CustomXError_Endpoint}}`; customXError_EndEvent picks Completed, Escalated or "Error End 2" [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 31 "End event?"].
- Whether an error of the called outbound flow reaches these subprocesses is not visible in the artifact (pitfalls).

## Extension points

- The outbound flow itself: any ProcessDirect address delivered in SAP_OutboundProcessingEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "Receiver"].
- PipelineJMSQueueSuffix: "if you like to define a receiver-specific outbound processing flow" [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, parameter "PipelineJMSQueueSuffix"].
- Parameters: global exit, error flow; PD per partner ID: scenario exit at step 06.

## Version notes

- 2026-09-current 1.0.4: step 1 reads incomingQueue = prefix + Q04 + suffix and the size flag from parameter PayloadSizeMeasurement [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 1 "Fetch JMS Queue & Conf."] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: parameters SkipRetriesGlobal_Enabled, SkipRetriesGlobal_Source, SkipRetriesGlobal_SourcePidProfile feed CustomXSkipRetries, CustomXSkipRetriesSource, CustomXSkipRetriesProfile [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 2 "Set custom extensions"]; a partner-directory parameter SkipRetriesProfile of the partner ID [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/readRetryHandlingFromPDOutbound.groovy#L27] sets header CustomXSkipRetriesProfile and property CustomXSkipRetryProfile and returns before CustomXSkipRetries and CustomXSkipRetriesSource are checked [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/readRetryHandlingFromPDOutbound.groovy#L31] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/readRetryHandlingFromPDOutbound.groovy#L33] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/readRetryHandlingFromPDOutbound.groovy#L37] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: outside test mode [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 19 "Test mode?"] "Check Exception Type" classifies the error by default classes [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/checkExceptionType.groovy#L88], value mapping [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/checkExceptionType.groovy#L98] or partner directory [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/checkExceptionType.groovy#L137]; "Skip retries?" sends it straight to the DLQ with custom status RetrySkipped [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 20 "Check Exception Type"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 21 "Skip retries?"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 22 "Set Custom Status and Define DLQ"] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: backup and restore of payload, headers and attachments [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 5 "Backup Message"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 23 "Restore Message"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 33 "Restore Message"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/backupMessage.groovy#L32] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/backupMessage.groovy#L33] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/backupMessage.groovy#L34] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/restoreMessage.groovy#L11] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/restoreMessage.groovy#L12] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/restoreMessage.groovy#L13]; sender queue as MPL custom header [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 6 "Add sender queue to custom headers"] [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, scripts/addSenderJMSQueueToCustomHeaders.groovy#L11] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
- 2026-09-current 1.0.4: getOriginalExceptionFromMplApi is a resource no step calls [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, resource "script/getOriginalExceptionFromMplApi.groovy"]; logMessage is no longer a resource [2026-06-trial: com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, resource "script/logMessage.groovy"] — see versions/2026-09-current/changelog-generic-pipelines-step04-06.md
