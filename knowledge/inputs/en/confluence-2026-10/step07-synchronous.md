<!-- Translated from Confluence page "Step07 – Synchronous" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – Synchronous

For the sync pipeline (called from the Sync runtime):

Remove headers from request (pipeline headers must not reach the receiver) → local process Mapping/conversion of request → Request Reply to the receiver adapter → local process Mapping/conversion of response → Set SAP headers → response back.

Exception: SyncCallFailed + ${exception.message}.
