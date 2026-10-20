# readInboundQueueFromPD

## Purpose

Reads the scenario's inbound queue from the Partner Directory: String parameter InboundQueue of the partner ID becomes property inbQueue [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L25].
Script step "Read inbound queue" in the main process of Step01 IDoc and Step01 XI, on the EO branch of the router "QoS?" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 23 "QoS?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 23 "QoS?"]; the router "Which queue?" of the local process that writes to JMS then tests `${property.inbQueue} != null` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 34 "Which queue?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 30 "Which queue?"].

## Inputs

- Header partnerID: Partner ID of the lookup [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L15].
- Partner Directory String InboundQueue of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L21].

## Outputs

- Property inbQueue = the InboundQueue value, or null [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L25]; the JMS receiver InboundProcessingQueue sends to `${property.inbQueue}` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "InboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "InboundProcessingQueue"].
- Header auditLogHeader: two entries through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L28] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L29] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L30] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L32] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/PipelineLogger.groovy#L69].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L17].
- inbQueue null: the JMS receiver DefaultInboundProcessingQueue sends to `{{DefaultJMSQueuePrefix}}{{DefaultJMSQueueNumber}}` in Step01 IDoc and `{{PipelinePrefix}}{{DefaultJMSQueueNumber}}` in Step01 XI [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, adapter "JMS" "DefaultInboundProcessingQueue"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"].

## Gotchas

- The comment says the property "is not created" when the queue is missing [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L24], but the script always sets inbQueue, to null when InboundQueue is missing or empty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L25].
- The only source is the Partner Directory; no header or iFlow parameter override is read [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L14] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L21].
- L30 applies the `?: "n/a"` fallback to the result of `.toString()`, not to the property value [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L30].
- Lines 35 to 42 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L35].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 24 "Read inbound queue"].
- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 26 "Read inbound queue"].

Copies: byte-identical in 2 artifacts (`com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`, `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi`).
