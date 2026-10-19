# readReceiverSpecificQueueFromPD

## Purpose

Finds a receiver-specific JMS queue: Partner Directory `ReceiverSpecificQueue_<SAP_ReceiverAlias>` of the scenario partner, else ReceiverSpecificQueue of the receiver, into properties receiverSpecificQueueName and receiverSpecificQueueBoolean [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L19] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L28] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L31].
Script step before the router "Receiver specific?" in Step02 inbound, Step02 integrated messaging async, Step04 and Step05 [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 42 "Receiver specific?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 73 "Receiver specific?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 62 "Receiver specific?"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 51 "Receiver specific?"]; Step05 sends to `${property.receiverSpecificQueueName}` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, adapter "JMS_ReceiverSpecific" "ReceiverSpecificQueue"].

## Inputs

- Header SAP_ReceiverAlias: suffix of the first key and Partner ID of the second lookup [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L15] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L19] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Header partnerID: Partner ID of the first lookup [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L18].
- Partner Directory String `ReceiverSpecificQueue_<SAP_ReceiverAlias>` of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L19].
- Partner Directory String ReceiverSpecificQueue of the Partner ID equal to SAP_ReceiverAlias [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23].

## Outputs

- Property receiverSpecificQueueName = the queue name, or null [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L28].
- Property receiverSpecificQueueBoolean = 'true' when a queue was found, else 'false' [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L31].
- Header auditLogHeader: four entries through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L34] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L40] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L69].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L10]; no other exception is thrown by the script.

## Gotchas

- Precedence: scenario-specific key of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L19], then the receiver-wide key of the receiver alias [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L22] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23]; the second lookup uses the receiver alias as a Partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Neither partnerID nor SAP_ReceiverAlias is checked: a missing alias gives the key "ReceiverSpecificQueue_null" and a second lookup with a null Partner ID [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L15] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L18] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L19] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L23].
- Nothing found: receiverSpecificQueueName is set to null, although the comment says it "is not created" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L27] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L28].
- Lines 43 to 54 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readReceiverSpecificQueueFromPD.groovy#L43].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`: [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 40 "Read receiver specific queue"].
- `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`: [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 65 "Get Receiver specific queue from PD"] and [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 89 "Get Receiver specific queue from PD"].
- `com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination`: [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 55 "Get Receiver specific queue from Partner Directory"].
- `com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination`: [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 45 "Get Receiver specific queue from Partner Directory"].

Copies: byte-identical in 4 artifacts (`com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`, `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`, `com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination`, `com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination`).
