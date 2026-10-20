# readCustomInterfaceDeterminationFromPD

## Purpose

Reads whether a scenario uses a custom interface determination flow: Partner Directory String CustomXIDEndpoint of the partner becomes properties customXIDBoolean and customXIDEndpoint [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L24] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L28].
Script step "Read custom interface determination" in the main process of Step05 only [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 32 "Read custom interface determination"].

## Inputs

- Header partnerID: Partner ID of the lookup [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L15].
- Partner Directory String CustomXIDEndpoint of partnerID [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21]; its value is the address of the custom flow, `{{tenant.endpoint.interface.determination.custom}}`.

## Outputs

- Property customXIDBoolean = 'true' when CustomXIDEndpoint has a value, else 'false' [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L24].
- Property customXIDEndpoint = the value, or null [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L28].
- Header auditLogHeader: three entries under the key "customInterfaceDetermination" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L31] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L33] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L39].
- IllegalStateException "Partner Directory Service not found" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L10]; IllegalStateException "Partner ID not found in sent message" when partnerID is missing [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L17].

## Gotchas

- The Partner Directory is the only source: there is no header override, unlike the header customXRDEndpoint read first by `readCustomReceiverDeterminationFromPD.groovy` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L21].
- Missing parameter: property customXIDEndpoint is set to null, although the comment says it "is not created" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L27] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L28].
- Uses the older logger form `newLogger(message)`, `getAuditLog()` and an explicit `setHeader('auditLogHeader', ...)` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L31] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L38] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L39], which the shared PipelineLogger keeps "For backwards compatibility" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/PipelineLogger.groovy#L72] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/PipelineLogger.groovy#L73].
- The `?: "n/a"` fallback is applied after `.toString()`, so a missing endpoint is logged as "null" [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L34].
- The router "Custom ID?" sends customXIDBoolean = 'true' to "Call custom extension option"; any other value takes "Call XSLT option", the Partner Directory XSLT [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 33 "Custom ID?"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 52 "Call custom extension option"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 34 "Call XSLT option"] [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 36 "Get XSLT mapping from Partner Directory"].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination`: [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 32 "Read custom interface determination"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination`).
