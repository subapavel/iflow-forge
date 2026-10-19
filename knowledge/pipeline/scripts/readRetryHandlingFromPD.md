# readRetryHandlingFromPD

## Purpose

Sets header maxJMSRetries, the retry limit of the JMS retry handling, from Partner Directory MaxJMSRetries with default 5, unless the header is already present [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L28].
Script step "Read retry handling" in the main process of Step02 inbound and Step02 integrated messaging async; the limit is compared by the routers "Max # of retries reached?" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 19 "Max # of retries reached?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 6 "Max # of retries reached?"].

## Inputs

- Header partnerID: Partner ID of the lookup [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L15].
- Header maxJMSRetries [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L21].
- Partner Directory String MaxJMSRetries of partnerID, read only when the header is absent [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L24] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L25].

## Outputs

- Header maxJMSRetries = the Partner Directory value as Integer, else 5; written only when the header was absent [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L24] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L28].
- Header auditLogHeader: two entries through PipelineLogger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L32] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L36] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/PipelineLogger.groovy#L69].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L17].

## Gotchas

- Precedence: header maxJMSRetries [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L21], then MaxJMSRetries [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L25], then 5 [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L28].
- A header already present is kept as it is, with its own type; only the Partner Directory path converts with `toInteger()` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L24] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L28].
- A non-numeric MaxJMSRetries makes `toInteger()` fail; an empty one counts as missing and gives 5 [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L28].
- Lines 39 to 46 are a commented-out older logger [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readRetryHandlingFromPD.groovy#L39].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`: [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 32 "Read retry handling"].
- `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`: [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 5 "Read retry handling"].

Copies: byte-identical in 2 artifacts (`com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing`, `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`).
