# storeOriginalPayloadInXProperty

## Purpose

Saves the unconverted message body in the exchange property originalPayload: unchanged when header isWellFormedXml is "true", otherwise as Base64 text [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L6] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L12] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L13] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L15].
Script step "Store original payload" at the start of the Step02 asynchronous runtime, after the first "Check well formed XML" and before "Determine partner ID"; the custom error flow gets this payload back through `fetchOriginalPayloadFromXProperty.groovy` (Used by).

## Inputs

- Body, read with `getBody()` without a type [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L6].
- Header isWellFormedXml [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L9] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L10], set by [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 2 "Check well formed XML"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/checkWellFormedXML.groovy#L8] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/checkWellFormedXML.groovy#L12].

## Outputs

- Property originalPayload = the body object as returned by getBody() when isWellFormedXml is "true" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L13].
- Otherwise property originalPayload = `body.bytes.encodeBase64().toString()`, a Base64 String [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L15].
- Body and headers are not changed; the script throws no exception of its own [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L18].

## Gotchas

- The code is that of `storePayloadInXProperty.groovy` with the property name originalPayload instead of payload [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L13] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L15] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storePayloadInXProperty.groovy#L13] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storePayloadInXProperty.groovy#L15].
- The test is string equality with "true" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L12]: a missing header takes the Base64 branch [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/storeOriginalPayloadInXProperty.groovy#L15].
- The header that chose the encoding here is written again after the inbound conversion [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 41 "Check well formed XML"]; the reader decodes by the header it finds at its own run [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/fetchOriginalPayloadFromXProperty.groovy#L12] (Gotchas of `fetchOriginalPayloadFromXProperty.groovy`).
- SAP's change log: since 1.0.2 the original payload at the generic flow's inbound is forwarded to the custom error flow [generic-pipelines/CHANGELOG.txt#L18].
- The original payload is kept so that the custom error flow has the input message before conversion [rationale, from analysis 2026].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`: [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 3 "Store original payload"] in [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, process "Integration Process - Integrated Messaging Runtime including inbound conversion and combined receiver/interface determination"], after [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 2 "Check well formed XML"] and before [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 4 "Determine partner ID"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async`).
