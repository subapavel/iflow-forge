<!-- Translated from Confluence page "Step07 – One-to-Many (One Message Type)" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – One-to-Many (One Message Type)

One message → N messages of the same type:

One-to-Many Message Mapping (a multiplying structure Messages/Message1) → Remove XML Declaration → Iterating Splitter → per message: Set unique ID ${header.UniqueID}_${CamelSplitIndex} (SplitMessageID for deduplication of partial messages) → Idempotent → Outbound Conversion → Send → Post-Processing.
