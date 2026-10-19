# readReceiverNotDeterminedFromPD

## Purpose

Reads how Step04 handles a message without receiver on the reused-XRD branch: property receiverNotDeterminedType from the Partner Directory, default 'Error', and for 'Default' the default receiver [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L28] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L29] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L33].
Script step after the router "Reuse XRD?" in the local process "Determine receivers either via custom flow or based on XRD reused from PO" of Step04 [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 72 "Reuse XRD?"].

## Inputs

- Header partnerID: Partner ID of both lookups [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L15].
- Partner Directory String ReceiverNotDeterminedType of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L21].
- Partner Directory String ReceiverNotDeterminedDefault of partnerID, read only when the type is 'Default' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L28] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L29].

## Outputs

- Property receiverNotDeterminedType = the value, else 'Error' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L25].
- Property receiverNotDeterminedDefault = the default receiver, only for type 'Default' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L33].
- IllegalStateException "Partner ID <partnerID> not found or ReceiverNotDeterminedDefault parameter not found in the Partner Directory for the partner ID <partnerID>" when type is 'Default' and the default is missing [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L30] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L31].
- Header auditLogHeader: three entries [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L37] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L44] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L45].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L17].

## Gotchas

- The type is not validated: any value other than 'Default' is passed on as is, and the comparison is case-sensitive [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L28].
- An empty type counts as missing and becomes 'Error' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L25].
- The audit log shows "null" for receiverNotDeterminedDefault unless the type is 'Default' [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L42].
- Uses the older logger form `newLogger(message)` with `getAuditLog()` and an explicit `setHeader('auditLogHeader', ...)` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L37] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L44] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readReceiverNotDeterminedFromPD.groovy#L45], kept by PipelineLogger "For backwards compatibility" [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/PipelineLogger.groovy#L72].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination`: [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, step 73 "Read receiver not determined from Partner Directory"] in [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, process "Determine receivers either via custom flow or based on XRD reused from PO"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination`).
