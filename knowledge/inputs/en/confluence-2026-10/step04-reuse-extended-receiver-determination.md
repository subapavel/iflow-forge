<!-- Translated from Confluence page "Step04 – Reuse Extended Receiver Determination" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step04 – Reuse Extended Receiver Determination

> [FULLY DECOUPLED PIPELINE ONLY] — not used in the integrated runtime. Replacement: Custom RD+IF via Extended Receiver Determination (also returns interfaces).

A minimalistic variant: a single Message Mapping step — you upload the XRD mapping from PO unchanged, and the generic Step04 then converts the output (the PO Receivers format) itself with its own "Map to Receivers" mapping (the branch reuse XRD + readReceiverNotDeterminedFromPD).
