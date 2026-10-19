<!-- Translated from Confluence page "Step07 – Maintain Order At Runtime" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – Maintain Order At Runtime

Processing order controlled at runtime: two branches called sequentially (Branch 1 → Branch 2), each with Mappings → Outbound Conversion → Idempotent (${UniqueID}_Branch<n>) → Send.

It is useful when the receiver requires delivery in a defined order (e.g. header before items).
