# Pipeline v2 Generic Step02 - Integrated Messaging Runtime Async (com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async` 1.0.5, package generic-pipelines, label 2026-06-trial.

## Purpose

Asynchronous integrated runtime, fed by Step01 queue number X01 [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, parameter "DefaultJMSQueueNumber"] (prefixes: see pitfalls). One flow: exits, conversion, one Partner Directory XSLT for receivers and interfaces [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Get XSLT mapping from Partner Directory"], split per receiver, stage and interface. Output: Q04, read by Step06 with a suffix (see pitfalls) [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "JMS" "OutboundProcessingQueue"], or a receiver-specific queue.

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | JMS | InboundProcessingQueueX | `{{PipelineJMSQueuePrefix}}X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"] |
| Receiver | JMS | OuboundProcessingQueue | `{{PipelineJMSQueuePrefix}}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"] |
| Receiver | JMS | ReceiverSpecificQueue | `${property.receiverSpecificQueueName}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "ReceiverSpecificQueue"] |
| Receiver | JMS | DeadLetterQueue | `{{PipelineJMSQueuePrefix}}X01_DLQ` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "DeadLetterQueue"] |
| Receiver | ProcessDirect | ReceiverConvert | `${property.inbConvEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverConvert"] |
| Receiver | ProcessDirect | ReceiverXRD | `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverXRD"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Enricher "Set custom extensions"
2. Script "Check well formed XML": `checkWellFormedXML.groovy`
3. Script "Store original payload": `storeOriginalPayloadInXProperty.groovy`
4. Script "Determine partner ID": `determinePartnerID.groovy`
5. Script "Read retry handling": `readRetryHandlingFromPD.groovy`
6. ExclusiveGateway "Max # of retries reached?": SAPJMSRetries > maxJMSRetries to 95
7. Enricher "Initialize pre-processing exit"
8. ProcessCallElement "Call custom exit"
  9. Enricher "Initialize exit"
  10. Script "Check scenario-specific exit": `readCustomXFromPD.groovy`
  11. ExclusiveGateway "Global exit?"
  12. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomXGlobal"]
  13. ExclusiveGateway "Scenario-specific exit?"
  14. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomXScenario"]
  15. ErrorEventSubProcessTemplate "Exception Subprocess 4"
    16. ExclusiveGateway "Custom Extension?"
    17. ProcessCallElement "Call standard retry handling"
      18. ExclusiveGateway "Test mode?"
      19. ExclusiveGateway "Max # of retries reached?"
      20. Enricher "Define DLQ"
      21. Script "Attach additional information": `attachAdditionallnformation.groovy`
      22. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "DeadLetterQueue"]
      23. ExclusiveGateway "End event?"
      24. Enricher "Set custom status"
      25. Script "Attach additional information": `attachAdditionallnformation.groovy`
    26. ProcessCallElement "Call custom error handling"
      27. Enricher "Set headers"
      28. Script "Fetch original payload": `fetchOriginalPayloadFromXProperty.groovy`
      29. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomErrorFlow"]
      30. Enricher "Set custom status"
      31. ExclusiveGateway "End event?"
32. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
33. ProcessCallElement "Call inbound conversion"
  34. Script "Read inbound conversion": `readInboundConversionFromPD.groovy`
  35. ExclusiveGateway "Conversion?"
  36. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverConvert"]
  37. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    38. ExclusiveGateway "Custom Extension?"
    39. ProcessCallElement "Call custom error handling"
    40. ProcessCallElement "Call standard retry handling"
41. Script "Check well formed XML": `checkWellFormedXML.groovy`
42. Script "Store converted payload": `storePayloadInXProperty.groovy`
43. Script "Read bypass option": `readBypassOptionFromPD.groovy`
44. ExclusiveGateway "Bypass option?": p2p to 85, skipRcvDet to 93, else 45
45. ProcessCallElement "Determine receivers & interfaces"
  46. Script "Read custom receiver determination": `readCustomReceiverDeterminationFromPD.groovy`
  47. ExclusiveGateway "Custom RD?": customXRDBoolean to 48, reuseXRDBoolean to 49, else 51
  48. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverXRD"]
  49. Enricher "Set custom status": ConfigurationOptionNotSupported
  50. Script "Throw exception": `throwException.groovy`
  51. ExclusiveGateway "XML?": isWellFormedXml = 'false' to 52
  52. Enricher "Define dummy xml"
  53. Enricher "Get XSLT mapping from Partner Directory"
  54. Mapping "Run XSLT Mapping"
  55. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    56. ExclusiveGateway "Custom Extension?"
    57. ProcessCallElement "Call custom error handling"
    58. ProcessCallElement "Call standard retry handling"
59. ExclusiveGateway "Any Receiver?": none to 60, else 83
60. ExclusiveGateway "Not found type?": Default to 61, Ignore to 81, else 82
61. Enricher "Get default receiver name"
62. Enricher "Set custom status": ReceiverNotFoundDefault
63. ExclusiveGateway "Virtual Stage Routing?": tenantStageVirtual to 75, else 64
64. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
65. Script "Get Receiver specific queue from PD": `readReceiverSpecificQueueFromPD.groovy`
66. ExclusiveGateway "Any Interface?": none to 74
67. Splitter "Split interfaces"
68. Enricher "Get interface": bypassOption = skipIfDet
69. Script "Log outbound processing endpoint": `logOutboundProcessingEndpoint.groovy`
70. Script "Get payload": `fetchPayloadFromXProperty.groovy`
71. Enricher "Remove header"
72. Script "Add Custom Header ": `addCustomHeaderProperties.groovy`
73. ExclusiveGateway "Receiver specific?": receiverSpecificQueueBoolean [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "ReceiverSpecificQueue"], else [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"]
74. Enricher "Set custom status": InterfaceNotFoundError, "Error End 6"
75. Enricher "Store Receivers XML"
76. Script "Map to Receiver Stage": `mapSenderStageToReceiverStage.groovy`
77. ExclusiveGateway "Multiple Stages?": several to 78, else 64
78. Splitter "Split Receiver Stages"
79. Enricher "Set Tenant Stage"
80. Enricher "Retrieve Receivers XML": to 64
81. Enricher "Set custom status": ReceiverNotFoundIgnored, "End"
82. Enricher "Set custom status": ReceiverNotFoundError, "Error End"
83. Splitter "Split receivers"
84. Enricher "Get receiver name": to 63
85. ExclusiveGateway "Virtual Stage Routing?": to 86, else 88
86. Script "Map to Receiver Stage": `mapSenderStageToReceiverStage.groovy`
87. ExclusiveGateway "Multiple Stages?": several to 90, else 88
88. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
89. Script "Get Receiver specific queue from PD": `readReceiverSpecificQueueFromPD.groovy`, to 71
90. Splitter "Split Receiver Stages"
91. Enricher "Set Tenant Stage"
92. Script "Restore payload": `fetchPayloadFromXProperty.groovy`, to 88
93. Enricher "Set custom status": ConfigurationOptionNotSupported
94. Script "Throw exception": `throwException.groovy`
95. Enricher "Set custom status": MaxRetriesExceeded, "Error End 5"
96. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  97. ExclusiveGateway "Custom Extension?"
  98. ProcessCallElement "Call standard retry handling"
  99. ProcessCallElement "Call custom error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SapQualityOfService, SAP_MessageType | header | 1 | ExactlyOnce, SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Set custom extensions"] |
| isWellFormedXml | header | 2, 41 | [scripts/checkWellFormedXML.groovy#L12] |
| originalPayload, payload | property | 3, 42 | [scripts/storeOriginalPayloadInXProperty.groovy#L15] [scripts/storePayloadInXProperty.groovy#L15] |
| tenantStage, tenantStageVirtual, partnerID | header | 4, 76, 79, 86, 91 | [scripts/determinePartnerID.groovy#L146] [scripts/determinePartnerID.groovy#L342] [scripts/mapSenderStageToReceiverStage.groovy#L251] |
| SAP_ReceiverAlias, SAP_ReceiverInterface, SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint, SAP_Receiver | header | 43, 61, 84, 68, 64, 88 | [scripts/readBypassOptionFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Get interface"] [scripts/mapReceiverAliasToBusinessSystemName.groovy#L55] |

PD scripts set further properties (Partner directory).

### Read

From outside this flow (adapter, earlier or custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAPJMSRetries, testMode | header | 6, 18 | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 6 "Max # of retries reached?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Test mode?"] |
| tenantStage, partnerID | header | 4 | both set: kept [scripts/determinePartnerID.groovy#L18] |
| customXInboundConversionEndpoint, customXRDEndpoint | header | 34, 46 | override the PD [scripts/readInboundConversionFromPD.groovy#L22] [scripts/readCustomReceiverDeterminationFromPD.groovy#L21] |
| customXError_Status, customXError_EndEvent | header | 30, 31 | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 31 "End event?"] |

## Partner directory

- Partner ID, stage as Step01 XI [scripts/determinePartnerID.groovy#L323]; stages: parameter `{{tenant.name}}` of SAP_Integration_Suite_Landscape, missing: "PRD" [scripts/determinePartnerID.groovy#L126].
- Key (missing): CustomXPreEnabled, CustomXPreEndpoint (no exit) [scripts/readCustomXFromPD.groovy#L41]; MaxJMSRetries (5) [scripts/readRetryHandlingFromPD.groovy#L28]; InboundConversionEndpoint_<SAP_SenderAlias>, then InboundConversionEndpoint (no conversion) [scripts/readInboundConversionFromPD.groovy#L31]; receiverDetermination, String (default route) [scripts/readBypassOptionFromPD.groovy#L22]; interfaceDetermination_<receiver>, p2p (skipRcvDet) [scripts/readBypassOptionFromPD.groovy#L36].
- CustomXRDEndpoint; missing: ReuseXRDEndpoint is read [scripts/readCustomReceiverDeterminationFromPD.groovy#L35]; that missing: XSLT [scripts/readCustomReceiverDeterminationFromPD.groovy#L38].
- receiverDetermination (Binary): the XSLT (step 53); missing: see pitfalls.
- Receiver stages: alternative partner per stage; none: no stage routing [scripts/mapSenderStageToReceiverStage.groovy#L80].
- Known stage: stageRule_<stage>~<receiver alias>, stageRule_<stage> (partnerID, then alias) [scripts/mapSenderStageToReceiverStage.groovy#L145]; missing: exact stage [scripts/mapSenderStageToReceiverStage.groovy#L164], then the same with `{{tenant.name}}` [scripts/mapSenderStageToReceiverStage.groovy#L170], else exception [scripts/mapSenderStageToReceiverStage.groovy#L189]; value `>Stage1,Stage2`. Unknown stage: tenant rules, the only stage, else exception [scripts/mapSenderStageToReceiverStage.groovy#L112].
- ReceiverSpecificQueue_<SAP_ReceiverAlias> of partnerID, then ReceiverSpecificQueue of the alias; missing: Q04 [scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Receiver system: alternative partner, agency tenantStage or "PRD"; missing: alias kept [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `storeOriginalPayloadInXProperty.groovy`, `fetchOriginalPayloadFromXProperty.groovy`, `storePayloadInXProperty.groovy`, `fetchPayloadFromXProperty.groovy` | 3, 28; 42, 70, 92 | unconverted, converted payload [scripts/storeOriginalPayloadInXProperty.groovy#L13] [scripts/fetchOriginalPayloadFromXProperty.groovy#L13] [scripts/storePayloadInXProperty.groovy#L13] [scripts/fetchPayloadFromXProperty.groovy#L15] |
| `determinePartnerID.groovy`, `readRetryHandlingFromPD.groovy`, `readCustomXFromPD.groovy`, `readInboundConversionFromPD.groovy`, `readBypassOptionFromPD.groovy`, `readCustomReceiverDeterminationFromPD.groovy`, `readReceiverSpecificQueueFromPD.groovy`, `mapReceiverAliasToBusinessSystemName.groovy`, `mapSenderStageToReceiverStage.groovy` | as in Steps | PD reads (Partner directory) |
| `checkWellFormedXML.groovy`, `addCustomHeaderProperties.groovy`, `throwException.groovy`, `logOutboundProcessingEndpoint.groovy`, `attachAdditionallnformation.groovy` | as in Steps | XML check, MPL headers, exception, audit log, MPL attachment [scripts/checkWellFormedXML.groovy#L7] [scripts/addCustomHeaderProperties.groovy#L69] [scripts/throwException.groovy#L34] [scripts/logOutboundProcessingEndpoint.groovy#L9] [scripts/attachAdditionallnformation.groovy#L32] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy`; `logMessage.groovy`, `auditLogHandler.groovy` | library; none | [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelineJMSQueuePrefix | — | queue prefix [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, parameter "PipelineJMSQueuePrefix"] | `{{tenant.pipeline.prefix}}` |
| JMS_ConcurrentProcesses, JMS_RetryInterval, JMS_MaxRetryInterval, JMS_ExpotentialBackoff, JMS_DLQ | — | consumption, retry [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"] | no |
| JMS_OQ_, JMS_RS_, DLQ_ (storage four) | — | storage [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS_OuboundProcessing" "OuboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "ReceiverSpecificQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "DeadLetterQueue"] | no |
| DLQ_EndEvent, CustomXPreGlobal_Enabled, CustomXError_Enabled, CustomXPid_SwitchOffTilde | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, parameter "CustomXPreGlobal_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Four event subprocesses; custom if CustomXError_Enabled [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 16 "Custom Extension?"]. Standard: test messages escalate; DLQ if the exception names `determinePartnerID.groovy` or the status is MaxRetriesExceeded or *OptionNotSupported, else "Error End 1" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 19 "Max # of retries reached?"].
- Custom: unconverted payload [scripts/fetchOriginalPayloadFromXProperty.groovy#L13], exception headers to `{{CustomXError_Endpoint}}`; customXError_EndEvent picks Completed, Escalated or "Error End 4" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 31 "End event?"].

## Extension points

- Parameters: global exit, error flow. PD per partner ID: scenario exit, conversion, receiver XSLT, custom determination flow [scripts/readCustomReceiverDeterminationFromPD.groovy#L25], retry limit, p2p, receiver queue, stage rules.

## Version notes

- 2026-09-current 1.0.6: see versions/2026-09-current/changelog-generic-pipelines-step02.md § com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async
