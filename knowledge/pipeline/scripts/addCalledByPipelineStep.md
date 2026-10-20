# addCalledByPipelineStep

## Purpose

Writes the value of header customXPipelineStep to the message processing log as custom header property calledByStep [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L8] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L10] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L11].
Script step 1 of the custom exit template, before its router "Which pipeline step?" [com.sap.integration.cloud.pipeline.v2.template.custom.exit, step 1 "Add called by step"] [com.sap.integration.cloud.pipeline.v2.template.custom.exit, step 2 "Which pipeline step?"].

## Inputs

- Header customXPipelineStep [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L7] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L8]; the custom exit lets it in through `customX(.*)` in its allowedHeaderList [com.sap.integration.cloud.pipeline.v2.template.custom.exit, property "allowedHeaderList"].
- Who sets it: readCustomXFromPD in the generic iFlows, when the header is absent [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L30]; the Step07 templates set '07' in "Initialize", for example [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p, step 12 "Initialize"].
- The body is read into a variable that is never used [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L4].

## Outputs

- MPL custom header property calledByStep = the header value, when the header is truthy [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L10] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L11].
- No header, property or body change; the message is returned [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L13].

## Gotchas

- L11 calls addCustomHeaderProperty on messageLog without a null check (L5), unlike addCustomHeaderProperties, which tests messageLog first [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L5] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L11] [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCustomHeaderProperties.groovy#L66].
- Without the header nothing is logged and nothing is thrown [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L10].
- The value goes to the log as it is, without trim [com.sap.integration.cloud.pipeline.v2.script.collection, scripts/addCalledByPipelineStep.groovy#L11].

## Used by

- `com.sap.integration.cloud.pipeline.v2.script.collection`: shipped in the ScriptCollection bundle [com.sap.integration.cloud.pipeline.v2.script.collection, manifest "SAP-BundleType"]; run from the custom exit template through scriptBundleId (below).
- `com.sap.integration.cloud.pipeline.v2.template.custom.exit`: [com.sap.integration.cloud.pipeline.v2.template.custom.exit, step 1 "Add called by step"] in [com.sap.integration.cloud.pipeline.v2.template.custom.exit, process "Integration Process"], script of the collection [com.sap.integration.cloud.pipeline.v2.template.custom.exit, manifest "Require-Capability"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.script.collection`).
