<!-- Translated from Confluence page "Generic Inbound" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Inbound

Package: Cloud Integration Pipeline – Generic Inbound · Contents: 2 generic inbound flows (Step01 XI, Step01 IDoc) · Technical name of both: PIP_01_InboundProcessing4Scenario1

> Notation. We use the integrated messaging runtime. Passages marked [FULLY DECOUPLED PIPELINE ONLY] are not used in the integrated runtime — we keep them for completeness.

## What the package contains

The generic pipeline entry point — the first flow through which messages enter the pipeline. There are two variants depending on the sender type; both are deployed once and serve all inbound scenarios of the given type:

- Step01 – Inbound Processing for XI — for messages via the XI adapter (ABAP proxy).
- Step01 – Inbound Processing for IDoc — for IDoc messages (IDoc SOAP over HTTP).

## What they have in common

Both variants: receive the message → Set SAP headers (pipeline headers) → Determine partner ID (configuration scenario in the Partner Directory) → optional custom exits → write to the inbound JMS queue. They share the local process Call custom exits and the Exception Subprocess.

> Integrated runtime: the default inbound queue is PIPX01. It can be overridden via the PD parameter InboundQueue; routing to the fully decoupled pipeline (PIPQ01) is therefore optional and is not used for the integrated runtime.

## XI vs. IDoc — main differences

| Aspect | XI flow (1.0.5) | IDoc flow (1.0.8) |
|---|---|---|
| Sender adapter | XI (/PIP/XI) | IDOC – IDoc SOAP (/PIP/IDoc) |
| Synchronous processing (Best Effort) | Yes – Request Reply to the sync runtime /PIP/XS01 or p2p | No – everything asynchronously via JMS |
| Bypass options (p2p / skipRcvDet) | Yes (from PD) | No |
| Bulk handling + Splitter | No | Yes – router "Bulk?", PD parameter KeepBulk, IDoc Splitter |
| Deduplication (key) | SapMessageIdEx | SNDPRN_DOCNUM (single) / SNDPRN_transferID (kept bulk); can optionally be switched off |
| Application ID | SapMessageId (uppercase) | DOCNUM without leading zeros |
| Test mode | No | Yes – EDIDC field TEST → errors end with ErrorInTestMode / Escalated |
| Partner ID script | determinePartnerID.groovy | determinePartnerIDForIDocInbound.groovy |
| EOIO | Not supported | Not supported |

## Subpages

- Generic Step01 – Inbound Processing for IDoc
- Generic Step01 – Inbound Processing for XI
