<!-- Translated from Confluence page "Step07 – Outbound Processing" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – Outbound Processing

The Step07 templates (scenario-specific mapping and delivery) are called by the generic Step06 via ProcessDirect ${header.SAP_OutboundProcessingEndpoint} (value = /Interface/Service from the determination).

Common elements: ProcessDirect entry {{ProcessDirect_Endpoint}}, local processes Mappings and Outbound Conversion with an empty Content Modifier (a place to fill in — this is where message mappings and conversions belong), Custom post-processing exit (step 07), Exception Handling with the status RetryViaParentFlow (the retry is owned by Step06), and a final Request Reply / Send to the receiver adapter (you add it — HTTP, SOAP, OData…).

Subpages:

- Step07 – Maintain Order At Runtime
- Step07 – One-to-Many (Multiple Message Types)
- Step07 – One-to-Many (One Message Type)
- Step07 – Point-to-Point
- Step07 – Point-to-Point to Generic IDoc Outbound
- Step07 – Point-to-Point to Generic XI Outbound
- Step07 – Synchronous
