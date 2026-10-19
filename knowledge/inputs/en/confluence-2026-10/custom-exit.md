<!-- Translated from Confluence page "Custom Exit" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Custom Exit

A skeleton for a pre/post-processing exit (global as well as scenario-specific). ProcessDirect entry {{ProcessDirect_EndPoint}} → router "Which pipeline step?" based on the header customXPipelineStep (01–07) → seven empty Content Modifiers (Route 1–7) — into the respective branch you add the logic for the step from which the exit is called. A single exit flow can thus serve all pipeline steps.
