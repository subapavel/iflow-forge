# Pipeline v2 Generic Step02 - Integrated Messaging Runtime Sync (com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync)

Artifact: `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync` 1.0.5, package generic-pipelines, label 2026-06-trial.

## Purpose

Synchronous integrated runtime: Step01 XI calls `/{{PipelinePrefix}}/XS01` for BestEffort messages on the default (CBR) route [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 41 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "SyncPipeline"], the address of this flow's sender [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "InboundProcessingSync"]. It runs the exits, determines exactly one receiver and one interface by the Partner Directory XSLT (steps 46, 55) and calls the scenario-specific outbound flow, whose reply it returns without the pipeline headers (step 60) [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"]. No JMS queue, no retry; skipRcvDet and reused XRD fail (steps 35, 71). One receiver only: a synchronous reply cannot fan out [rationale, from analysis 2026]. "Not found type?" offers no Ignore [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Not found type?"]: the caller needs a reply [rationale, from analysis 2026].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | InboundProcessingSync | `/{{PipelinePrefix}}/XS01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "InboundProcessingSync"] |
| Receiver | ProcessDirect | ScenarioSpecificOutbound | `${header.SAP_OutboundProcessingEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"] |
| Receiver | ProcessDirect | ReceiverXRD | `${property.customXRDEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ReceiverXRD"] |
| Receiver | ProcessDirect | CustomXGlobal | `${property.CustomXGlobalEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomXGlobal"] |
| Receiver | ProcessDirect | CustomXScenario | `${property.CustomXEndpoint}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomXScenario"] |
| Receiver | ProcessDirect | CustomErrorFlow | `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomErrorFlow"] |

## Steps

1. Enricher "Set custom extensions"
2. Script "Determine partner ID": `determinePartnerID.groovy`
3. Enricher "Initialize pre-processing exit"
4. ProcessCallElement "Call custom exit"
  5. Enricher "Initialize exit"
  6. Script "Check scenario-specific exit": `readCustomXFromPD.groovy`
  7. ExclusiveGateway "Global exit?": to 10, else 8
  8. ExclusiveGateway "Scenario-specific exit?": to 9, else end
  9. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomXScenario"]
  10. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomXGlobal"], then 8
  11. ErrorEventSubProcessTemplate "Exception Subprocess 3"
    12. ExclusiveGateway "Custom Extension?": CustomXError_Enabled = 'true' to 13, else 22
    13. ProcessCallElement "Call custom error handling"
      14. Enricher "Set headers & fetch original payload": exception headers, body from property payload
      15. ExternalCall "Send to custom error flow": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomErrorFlow"]
      16. Enricher "Set custom status": from header customXError_Status
      17. ExclusiveGateway "End event?": customXError_EndEvent Escalated to 18, Completed to 20, else 21
      18. ProcessCallElement "Call remove headers": "Escalation End 4"
        19. Enricher "Remove headers"
      20. ProcessCallElement "Call remove headers": "End 14"
      21. ProcessCallElement "Call remove headers": "Error End 4"
    22. ProcessCallElement "Call standard retry handling": process "Standard error handling"
      23. ExclusiveGateway "Test mode?": to 26, else 24
      24. Script "Attach additional information": `attachAdditionallnformation.groovy`
      25. ProcessCallElement "Call remove headers": "Error End 7"
      26. Enricher "Set custom status": ErrorInTestMode, then 24
27. Enricher "Remove header": customXPipelineStep
28. Script "Add custom header properties": `addCustomHeaderProperties.groovy`
29. Script "Read bypass option": `readBypassOptionFromPD.groovy`
30. ExclusiveGateway "Bypass option?": p2p to 68, skipRcvDet to 71, else 31
31. ProcessCallElement "Determine receivers & interfaces"
  32. Script "Read custom receiver determination": `readCustomReceiverDeterminationFromPD.groovy`
  33. ExclusiveGateway "Custom RD?": customXRDBoolean to 34, reuseXRDBoolean to 35, else 37
  34. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ReceiverXRD"]
  35. Enricher "Set custom status": ConfigurationOptionNotSupported
  36. Script "Throw exception": `throwException.groovy`
  37. Script "Check well formed XML": `checkWellFormedXML.groovy`
  38. ExclusiveGateway "XML?": not XML to 39
  39. Enricher "Define dummy xml"
  40. Enricher "Get XSLT mapping from Partner Directory": `pd:${header.partnerID}:receiverDetermination:Binary`
  41. Mapping "Run XSLT Mapping"
  42. ErrorEventSubProcessTemplate "Exception Subprocess 2"
    43. ExclusiveGateway "Custom Extension?"
    44. ProcessCallElement "Call custom error handling"
    45. ProcessCallElement "Call standard error handling"
46. ExclusiveGateway "Any Receiver?": several to 47, none to 49, else 66
47. Enricher "Set custom status": ReceiverNotFoundError, too many receivers
48. Script "Throw exception": `throwException.groovy`
49. ExclusiveGateway "Not found type?": Default to 50, else 65
50. Enricher "Get default receiver name": SAP_ReceiverAlias
51. Filter "Filter": DefaultReceiver/Interfaces
52. Enricher "Set custom status": ReceiverNotFoundDefault, then 53
53. ExclusiveGateway "Virtual Stage Routing?": tenantStageVirtual to 64, else 54
54. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`
55. ExclusiveGateway "Any Interface?": several to 62, none to 63, else 56
56. Enricher "Get interface": SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint; bypassOption = skipIfDet
57. Script "Log outbound processing endpoint": `logOutboundProcessingEndpoint.groovy`
58. Enricher "Get payload": body from property payload
59. ExternalCall "Request Reply": [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "ScenarioSpecificOutbound"]
60. ProcessCallElement "Call remove headers"
61. Script "Add custom header props": `addCustomHeaderProperties.groovy`, then end
62. Enricher "Set custom status": InterfaceNotFoundError, too many interfaces, to 48
63. Enricher "Set custom status": InterfaceNotFoundError, no interface, to 48
64. Script "Map to Receiver Stage": `mapSenderStageToReceiverStage.groovy`, then 54
65. Enricher "Set custom status": ReceiverNotFoundError, to 48
66. Enricher "Get receiver name": SAP_ReceiverAlias
67. Filter "Filter": Receiver/Interfaces, then 53
68. ExclusiveGateway "Virtual Stage Routing?": to 70, else 69
69. Script "Map to Business Name": `mapReceiverAliasToBusinessSystemName.groovy`, then 59
70. Script "Map to Receiver Stage": `mapSenderStageToReceiverStage.groovy`, then 69
71. Enricher "Set custom status": ConfigurationOptionNotSupported, to 48
72. ErrorEventSubProcessTemplate "Exception Subprocess 1"
  73. ExclusiveGateway "Custom Extension?"
  74. ProcessCallElement "Call standard error handling"
  75. ProcessCallElement "Call custom error handling"

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| SapQualityOfService, SAP_MessageType | header | 1 | BestEffort, SAP_SenderInterface [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Set custom extensions"] |
| payload, customHeader_QualityOfService, CustomXPreGlobal_*, CustomXError_Enabled, CustomXPid_SwitchOffTilde | property | 1 | body; header; parameters [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Set custom extensions"] |
| tenantStage, tenantStageVirtual, partnerID | header | 2, 64, 70 | [scripts/determinePartnerID.groovy#L146] [scripts/determinePartnerID.groovy#L342] [scripts/mapSenderStageToReceiverStage.groovy#L251] |
| SAP_ReceiverAlias, SAP_OutboundProcessingEndpoint; bypassOption | header; property | 29, 50, 56, 66 | [scripts/readBypassOptionFromPD.groovy#L25] [scripts/readBypassOptionFromPD.groovy#L36] |
| customXRDBoolean, reuseXRDBoolean, customXRDEndpoint | property | 32 | [scripts/readCustomReceiverDeterminationFromPD.groovy#L43] |
| isWellFormedXml, XSLTMapping | header | 37, 40 | [scripts/checkWellFormedXML.groovy#L12] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Get XSLT mapping from Partner Directory"] |
| SAP_Receiver, customHeaderProperties | header | 54, 69 | [scripts/mapReceiverAliasToBusinessSystemName.groovy#L55] |

Step 19 deletes iswellformedxml, XSLTMapping, sap_receiverinterfaceindex, customxerror*, dc*, customheaderproperties, tenantstage, sap_senderinterface, sap_sender, sap_receiveralias, sap_receiver, sap_outboundprocessingendpoint, partnerid, auditlogheader before a reply [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Remove headers"].

### Read

Headers and properties from outside this flow (Step01 XI, custom flows).

| Name | Kind | Step | Used for |
|---|---|---|---|
| tenantStage, partnerID | header | 2 | both set: kept [scripts/determinePartnerID.groovy#L18] |
| testMode | header | 23 | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Test mode?"] |
| customXRDEndpoint | header | 32 | overrides the PD [scripts/readCustomReceiverDeterminationFromPD.groovy#L21] |
| customXError_Status, customXError_EndEvent | header | 16, 17 | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 16 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 17 "End event?"] |

## Partner directory

- Partner ID, stage as Step01 XI [scripts/determinePartnerID.groovy#L323]; stages: parameter `{{tenant.name}}` of SAP_Integration_Suite_Landscape, missing: "PRD" [scripts/determinePartnerID.groovy#L126].
- CustomXPreEnabled, CustomXPreEndpoint; missing: no exit [scripts/readCustomXFromPD.groovy#L41].
- receiverDetermination (String); missing: default route [scripts/readBypassOptionFromPD.groovy#L22]. interfaceDetermination_<receiver>: p2p; missing: skipRcvDet [scripts/readBypassOptionFromPD.groovy#L36].
- CustomXRDEndpoint; missing: ReuseXRDEndpoint is read [scripts/readCustomReceiverDeterminationFromPD.groovy#L35]; that missing: XSLT [scripts/readCustomReceiverDeterminationFromPD.groovy#L38].
- receiverDetermination (Binary): the XSLT (step 40); missing: open-questions/kb-12b.md.
- Receiver stages: alternative partner per stage; none: no stage routing [scripts/mapSenderStageToReceiverStage.groovy#L80].
- Known stage: stageRule_<stage>~<receiver alias>, stageRule_<stage> (partnerID, then alias) [scripts/mapSenderStageToReceiverStage.groovy#L145]; missing: exact stage [scripts/mapSenderStageToReceiverStage.groovy#L164], then the same with `{{tenant.name}}` [scripts/mapSenderStageToReceiverStage.groovy#L170], else exception [scripts/mapSenderStageToReceiverStage.groovy#L189]; value `>Stage1,Stage2`. Unknown stage: tenant rules, the only stage, else exception [scripts/mapSenderStageToReceiverStage.groovy#L112]. Several stages throw for BestEffort [scripts/mapSenderStageToReceiverStage.groovy#L260].
- Receiver system: alternative partner, agency tenantStage or "PRD"; missing: alias kept [scripts/mapReceiverAliasToBusinessSystemName.groovy#L36].

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| `determinePartnerID.groovy`, `readCustomXFromPD.groovy`, `readBypassOptionFromPD.groovy`, `readCustomReceiverDeterminationFromPD.groovy`, `mapReceiverAliasToBusinessSystemName.groovy`, `mapSenderStageToReceiverStage.groovy` | as in Steps | PD reads (Partner directory) |
| `checkWellFormedXML.groovy` | 37 | XML check [scripts/checkWellFormedXML.groovy#L7] |
| `addCustomHeaderProperties.groovy` | 28, 61 | MPL custom headers [scripts/addCustomHeaderProperties.groovy#L69] |
| `throwException.groovy` | 36, 48 | throws exceptionMessage [scripts/throwException.groovy#L9] |
| `logOutboundProcessingEndpoint.groovy` | 57 | audit log [scripts/logOutboundProcessingEndpoint.groovy#L9] |
| `attachAdditionallnformation.groovy` | 24 | MPL attachment [scripts/attachAdditionallnformation.groovy#L32] |
| `PipelineLogger.groovy`, `PipelineHelper.groovy`; `logMessage.groovy`, `auditLogHandler.groovy` | library; none | [scripts/PipelineLogger.groovy#L69] [scripts/PipelineHelper.groovy#L6] [scripts/logMessage.groovy#L11] [scripts/auditLogHandler.groovy#L4] |

## Externalized parameters

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| PipelinePrefix | — | address prefix [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, parameter "PipelinePrefix"] | `{{tenant.pipeline.prefix}}` |
| CustomXPreGlobal_Enabled, CustomXError_Enabled, CustomXPid_SwitchOffTilde | — | switches [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Set custom extensions"] | no |
| CustomXPreGlobal_Endpoint, CustomXError_Endpoint | — | global exit, error flow [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, parameter "CustomXPreGlobal_Endpoint"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, parameter "CustomXError_Endpoint"] | `{{tenant.endpoint.custom.preprocessing.global}}`, `{{tenant.endpoint.custom.error}}` |

## Error handling

- Three event subprocesses; custom if CustomXError_Enabled = 'true', else standard [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 12 "Custom Extension?"].
- Standard: test messages get ErrorInTestMode; all get an MPL attachment, lose the pipeline headers and end in "Error End 7" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Test mode?"]. No DLQ, no retry. Under Step01 XI standard handling the caller then gets `${exception.message}`, status SyncCallFailed [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step "Return exception message"].
- Custom: original payload and exception headers to `{{CustomXError_Endpoint}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step "Set headers & fetch original payload"]; customXError_EndEvent picks Escalated, Completed or "Error End 4" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 17 "End event?"].
- Configuration and determination errors set a status and throw (steps 36, 48) [scripts/throwException.groovy#L9].

## Extension points

- Parameters: global exit, error flow. PD per partner ID: scenario exit, receiver XSLT, custom determination flow [scripts/readCustomReceiverDeterminationFromPD.groovy#L25], p2p, stage rules.

## Version notes

- 2026-09-current 1.0.6: step 1 also sets TenantName = ${sysenv.TENANT_NAME} [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 1 "Set custom extensions"] — see versions/2026-09-current/changelog-generic-pipelines-step02.md
- 2026-09-current 1.0.6: determinePartnerID reads the landscape with TenantName [2026-09-current: com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, scripts/determinePartnerID.groovy#L129] — see versions/2026-09-current/changelog-generic-pipelines-step02.md
