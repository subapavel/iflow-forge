<!-- Translated from Confluence page "Generic Pipelines" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Pipelines

Package: Cloud Integration Pipeline – Generic Pipelines · Contents: 5 generic flows

> Notation. We use the integrated messaging runtime. Flows and passages marked [FULLY DECOUPLED PIPELINE ONLY] are not used in the integrated runtime — we keep them for completeness.

## What the package contains

This package handles message processing between the inbound (Step01) and the outbound (Step06) and contains two ways of doing it:

What we use — integrated messaging runtime (2 flows):

- Integrated Messaging Runtime Async — one step that merges the work of Step02 + Step04 + Step05 (inbound conversion + combined determination of receivers and interfaces by a single XSLT). Input PIPX01, output PIPQ04.
- Integrated Messaging Runtime Sync — synchronous runtime (Best Effort) for request/reply; no JMS queues, strictly 1 receiver + 1 interface.

[FULLY DECOUPLED PIPELINE ONLY] — three separate steps that do the same thing spread across the queues PIPQ01–PIPQ03. They are not used in the integrated runtime:

- Step02 – Inbound Processing (PIPQ01 → conversion, partner ID, bypass)
- Step04 – Receiver Determination (PIPQ02 → list of receivers)
- Step05 – Interface Determination (PIPQ03 → list of interfaces; the header SAP_OutboundProcessingEndpoint is born here)

## How it fits together

Integrated Async does in one step what the fully decoupled pipeline does in three (Step02 + Step04 + Step05). When we run integrated, we therefore do not deploy these three separate flows — determination of both receivers and interfaces takes place in one combined XSLT mapping.

## Subpages

- Generic Step02 – Inbound Processing
- Generic Step02 – Integrated Messaging Runtime Async
- Generic Step02 – Integrated Messaging Runtime Sync
- Generic Step04 – Receiver Determination
- Generic Step05 – Interface Determination
