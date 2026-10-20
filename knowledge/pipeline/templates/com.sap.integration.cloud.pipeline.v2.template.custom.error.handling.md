# Pipeline v2 Template - Custom Error Handling (com.sap.integration.cloud.pipeline.v2.template.custom.error.handling)

Artifact: `com.sap.integration.cloud.pipeline.v2.template.custom.error.handling` 1.0.0, package templates, label 2026-06-trial.

## Purpose

Scenario-side custom error flow listening on `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, adapter "ProcessDirect" "Sender"]: when the JMS retries of the failed message are exhausted it writes the message to the dead letter queue `${header.incomingQueue}_DLQ` [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step "Set dead letter queue name"] [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, adapter "JMS" "DLQ"], and in both cases it returns the headers customXError_Status and customXError_EndEvent to the caller [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 2 "Return headers to calling process"] [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 5 "Return headers to calling process"]. The eight generic iFlows of Step01 to Step06 call a copy by Request Reply "Send to custom error flow" at `{{CustomXError_Endpoint}}` from their exception subprocesses when CustomXError_Enabled = 'true', instead of their standard retry handling (table "Callers" under Adapters). A scenario copies this template when: errors of the generic steps are to be handled by the scenario's own error flow, keeping the choice between retry and dead letter queue realised by the gateway on the retry headers [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, manifest "Bundle-Name"] [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step "Max retries exceeded?"].

## Adapters

| Direction | Adapter | Endpoint | Configuration |
|---|---|---|---|
| Sender | ProcessDirect | Sender | `{{ProcessDirect_EndPoint}}` [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, adapter "ProcessDirect" "Sender"] |
| Receiver | JMS | DLQ | queue `${property.deadLetterQueue}` from step 3; ExpirationPeriod 30, RetentionThresholdAlerting 2, EncryptMessage 1, UseMessageCompression 1, TransferExchangeProperties 0 [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, adapter "JMS" "DLQ"] |

### Callers

Each caller sets exception headers in "Set headers" (exceptionSourceMplId, exceptionTimestamp, exceptionStacktrace, exceptionMessage, pipelineStepID = camelId; Step02 async, Step04 and Step05 also exceptionCustomStatus), calls the copy and selects its end event with gateway "End event?" on customXError_EndEvent: 'Completed' to an end event "End <n>", 'Escalated' to "Escalation End <n>", the default route "Failed (Default)" to the error end (Step02 sync: all three after "Call remove headers").

| Caller | Switch | incomingQueue | Call | Reply used by |
|---|---|---|---|---|
| Step01 XI | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 14 "Custom Extension?"] | not set [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 20 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 22 "End event?"], default "Error End 1" |
| Step01 IDoc | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 14 "Custom Extension?"] | not set [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 16 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 18 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 19 "End event?"], default "Error End 1" |
| Step02 inbound | [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 14 "Custom Extension?"] | `${property.PipelineJMSQueuePrefix}Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 26 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 29 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 30 "End event?"], default "Error End 3" |
| Step02 async | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 16 "Custom Extension?"] | `${property.PipelineJMSQueuePrefix}X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 27 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 31 "End event?"], default "Error End 4" |
| Step02 sync | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 12 "Custom Extension?"] | not set [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 14 "Set headers & fetch original payload"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 16 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.sync, step 17 "End event?"], default step 21 "Call remove headers", then "Error End 4" |
| Step04 | [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 15 "Custom Extension?"] | `${property.PipelineJMSQueuePrefix}Q02` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 17 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 20 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 21 "End event?"], default "Error End 4" |
| Step05 | [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 15 "Custom Extension?"] | `${property.PipelineJMSQueuePrefix}Q03` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 27 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 31 "End event?"], default "Error End 3" |
| Step06 | [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 14 "Custom Extension?"] | `${property.PipelineJMSQueuePrefix}Q04` [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 27 "Set headers"] | [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, adapter "ProcessDirect" "CustomErrorFlow"] | [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 30 "Set custom status"] [com.sap.integration.cloud.pipeline.v2.generic.step06.outbound.processing, step 31 "End event?"], default "Error End 2" |

"Set custom status" makes customXError_Status the caller's SAP_MessageProcessingLogCustomStatus; Step01 XI has no such step. Step08 IDoc and XI do not call the copy [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, resource "scenarioflows/integrationflow/Pipeline v2 Generic Step08 - Outbound Processing for IDoc.iflw"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.xi, resource "scenarioflows/integrationflow/Pipeline v2 Generic Step08 - Outbound Processing for XI.iflw"].

## Steps

1. ExclusiveGateway "Max retries exceeded?": "Yes" to 3 when `${header.SAPJMSRetries} > ${header.maxJMSRetries} and ${header.maxJMSRetries} != 'unlimited'`, default "No" to 2 [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
2. Enricher "Return headers to calling process": headers customXError_Status = AutomaticRetry, customXError_EndEvent = Failed, then message end event "End" [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]
3. Enricher "Set dead letter queue name": property deadLetterQueue = `${header.incomingQueue}_DLQ`
4. Send "Send to DLQ": [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, adapter "JMS" "DLQ"]
5. Enricher "Return headers to calling process": headers customXError_Status = MaxRetriesExceeded, customXError_EndEvent = Escalated, then "End" [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]

Order: Start, 1, then 2 or 3, 4, 5, End [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].

## Headers and properties

### Set

| Name | Kind | Step | Value |
|---|---|---|---|
| customXError_Status | header | 2; 5 | AutomaticRetry [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 2 "Return headers to calling process"]; MaxRetriesExceeded [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 5 "Return headers to calling process"] |
| customXError_EndEvent | header | 2; 5 | Failed [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 2 "Return headers to calling process"]; Escalated [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step 5 "Return headers to calling process"] |
| deadLetterQueue | property | 3 | `${header.incomingQueue}_DLQ` [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step "Set dead letter queue name"] |

### Read

From outside the flow come the payload and the caller's headers that the allowed header list admits.

| Name | Kind | Step | Used for |
|---|---|---|---|
| SAPJMSRetries, maxJMSRetries | header | 1 | admitted [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, property "allowedHeaderList"]; retry decision [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |
| incomingQueue | header | 3 | admitted [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, property "allowedHeaderList"]; dead letter queue name [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step "Set dead letter queue name"]; set by the callers (Callers) |
| pipelineStepID, exception(.*), SAP_Sender(.*), SAP_MessageType, SAP_Receiver(.*), partnerID, testMode | header | — | admitted [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, property "allowedHeaderList"]; no step of the template reads them |

## Partner directory

None found in the artifact. The callers address a copy through their parameter CustomXError_Endpoint (Callers, column "Call").

## Scripts and resources

| File | Steps | Purpose |
|---|---|---|
| scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw | — | the flow model; no script folder and no Script step; the file name is that of the Step03 template [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"] |

## Externalized parameters

Bold: the copy is not reachable without it.

| Name | Default | Meaning | Tenant-specific? |
|---|---|---|---|
| **ProcessDirect_EndPoint** | — | address of the ProcessDirect sender [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, parameter "ProcessDirect_EndPoint"]; it must equal the callers' CustomXError_Endpoint (Callers) | `{{tenant.endpoint.custom.error}}` |

## Error handling

- No exception subprocess and no error end event: both paths end in message end event "End" [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"]; returnExceptionToSender is false [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, property "returnExceptionToSender"].
- The outcome is returned as headers: Failed takes the callers' default route "Failed (Default)" of "End event?" to their error end, Escalated their escalation end (Callers, column "Reply used by").

## Extension points

- The two branches of step 1, each ending in the return headers of step 2 or 5, where a copy adds its own handling [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, resource "scenarioflows/integrationflow/PIP_03_InboundConversion4Scenario1.iflw"].
- The returned headers are the contract with the callers: customXError_Status becomes their custom status, customXError_EndEvent = 'Completed' sends them to their end event "End <n>" (Callers).
- The DLQ name follows the caller's incomingQueue [com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, step "Set dead letter queue name"].

## Version notes

2026-09-current 1.0.0: no change in the dump; same Bundle-Version, only the manifest's Origin-ModifiedDate differs [2026-09-current: com.sap.integration.cloud.pipeline.v2.template.custom.error.handling, manifest "Bundle-Version"].
