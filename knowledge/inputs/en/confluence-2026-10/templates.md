<!-- Translated from Confluence page "Templates" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Templates

Package: Cloud Integration Pipeline – Templates · Package version: 1.1.1 · Contents: 18 templates + the shared Script Collection

> Labelling. We use the integrated messaging runtime. Templates/variants marked [FULLY DECOUPLED PIPELINE ONLY] are not used in the integrated runtime — we keep them for completeness.

## What the templates are for

Unlike the generic flows (deployed once, without changes), the templates are starting skeletons for scenario-specific flows — you copy them, rename them, add mappings/scripts/conditions for the specific scenario and deploy them per scenario. They cover all places in the pipeline where scenario-specific logic is needed: Step01 (sender adapter), Step03 (inbound conversion), Step04/05 (custom determination), Step07 (mapping + connection to outbound) and custom exits / error handling.

## Common features of all templates

ProcessDirect entry {{ProcessDirect_Endpoint}} (for Step07/03/04/05; it must be set uniquely per scenario), an Exception Handling process with the status RetryViaParentFlow (the error is propagated to the calling generic step, which owns the retry — the template itself never retries), and for Step07 a built-in custom post-processing exit (customXPipelineStep = 07, parameters CustomXPost_Enabled/_Endpoint, ProcessDirect on ${property.CustomXPost_Endpoint}).

## Deployment notes

- Each template copy must have a unique ProcessDirect endpoint — otherwise deployment fails.
- The Step07 copy's endpoint is registered in the determination (XSLT/properties: /Interface/Service, or SAP-IFENDPT-*, or PD interfaceDetermination_<Receiver> for the p2p bypass).
- The templates contain no Groovy scripts of their own — they reference the shared Pipeline v2 – Script Collection (scriptBundleId = com.sap.integration.cloud.pipeline.v2.script.collection), which is deployed once and must be deployed on the tenant before the template copies.
- The status RetryViaParentFlow in the MPL means: this (scenario-specific) instance failed, but the retry is controlled by the calling generic step — so in monitoring do not deal with this log, but with the parent's log.

## Subpages

- Custom Exit and Custom Error Handling
- Pipeline v2 – Script Collection (overview)
- Step01 – Inbound Processing
- Step03 – Inbound Conversion
- Step04/05 – Custom determination
- Step07 – Outbound Processing
