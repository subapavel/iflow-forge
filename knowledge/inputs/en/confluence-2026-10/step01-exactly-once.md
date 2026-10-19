<!-- Translated from Confluence page "Step01 – Exactly Once" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step01 – Exactly Once

For asynchronous EO scenarios.

Structure: (sender adapter — you add it) → Set SAP headers (fill in the constants SAP_Sender, SAP_SenderInterface; SAP_MessageType is copied from the interface) → Parse incoming XML → optionally Define Service Interface Operations + script Map Message Type to Operation (placeholder mapMessageTypeToOperation.groovy — mapping the message type from the XML root to an operation via properties SAP-MT2OP-<Operation>) → Set unique ID (constant/expression for deduplication) → Set custom header attributes → Idempotent Process Call (deduplication) → JMS Send to {{JMS_Outgoing}} (= the input queue of the pipeline; in the integrated runtime PIPX01, in the fully decoupled pipeline PIPQ01).

Customization: sender adapter, values of the SAP headers, unique ID, target queue, operation mapping.
