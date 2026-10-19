<!-- Translated from Confluence page "Step01 – Synchronous" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step01 – Synchronous

For synchronous (BestEffort) scenarios:

(sender adapter) → Set SAP headers → local process Inbound conversion (Parse XML, custom headers — a place for converting the request) → Request Reply to ProcessDirect {{Sync_Pipeline_Endpoint}} (= /PIP/XS01, the sync runtime) → local process Outbound conversion (conversion of the response) → Remove headers from response (SAP_Sender*, SAP_MessageType, SAP_SenderInterface) → response to the sender.

Exception Handling returns ${exception.message} with the status SyncCallFailed.
