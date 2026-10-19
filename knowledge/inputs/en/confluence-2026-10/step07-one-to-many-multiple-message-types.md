<!-- Translated from Confluence page "Step07 – One-to-Many (Multiple Message Types)" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – One-to-Many (Multiple Message Types)

One message → N messages of two (or more) types:

One-to-Many mapping → two branches (Branch 1/2), each with its own Iterating Splitter, Set Headers (MessageType1/MessageType2), Set unique ID ${UniqueID}_Branch<n>_${index}, Idempotent Process Call, Outbound Conversion and Send to Receiver. For further types the branches are duplicated.
