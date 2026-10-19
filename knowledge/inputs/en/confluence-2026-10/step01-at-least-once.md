<!-- Translated from Confluence page "Step01 – At Least Once" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step01 – At Least Once

The same skeleton as Exactly Once, but without the Idempotent Process Call (without deduplication) — the sender guarantees at-least-once, duplicates are tolerated or handled elsewhere.

Otherwise identical: Set SAP headers → Parse XML → (operation) → custom headers → JMS {{JMS_Outgoing}}.
