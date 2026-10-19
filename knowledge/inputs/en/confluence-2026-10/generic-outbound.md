<!-- Translated from Confluence page "Generic Outbound" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Outbound

Package: Cloud Integration Pipeline – Generic Outbound · Contents: 3 generic flows (Step06 Outbound Processing, Step08 XI, Step08 IDoc)

> Notation. We use the integrated messaging runtime. Passages marked [FULLY DECOUPLED PIPELINE ONLY] are not used in the integrated runtime — we keep them for completeness.

## What the package contains

The output side of the pipeline — from taking over the message from the outbound queue up to the physical sending to the receiver:

- Step06 – Outbound Processing — dispatcher: reads from the queue PIPQ04, handles retry and dead-letter handling, passes the message to the scenario-specific outbound flow (Step07).
- Step08 – Outbound Processing for XI — physical sending via the XI protocol to the receiving ABAP system.
- Step08 – Outbound Processing for IDoc — physical sending of an IDoc (single and bulk) to the ABAP system.

## How it fits together

PIPQ04 → Step06 (dispatcher) → Step07 (scenario-specific mapping and delivery) → (optionally) Step08 XI / IDoc → ABAP receiver.

- Integrated and fully decoupled pipeline: all three flows are common to both runtimes. Step06 reads from PIPQ04, which is filled both by the integrated runtime and (in the fully decoupled pipeline) by Step05. No step here is runtime-specific — that is why there are no markers here.

## Subpages

- Generic Step06 – Outbound Processing
- Generic Step08 – Outbound Processing for IDoc
- Generic Step08 – Outbound Processing for XI
