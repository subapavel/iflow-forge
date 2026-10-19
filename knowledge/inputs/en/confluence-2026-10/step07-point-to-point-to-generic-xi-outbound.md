<!-- Translated from Confluence page "Step07 – Point-to-Point to Generic XI Outbound" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – Point-to-Point to Generic XI Outbound

A prepared connection to the generic Step08 XI:

Call Mappings → Set XI Headers (constants SAP_Receiver_XIEndpoint/ProxyType/CCLocationId/AuthenticationType/CredentialName/PrivateKeyAlias — a scenario-specific override of the Step08 defaults) → Set properties with XI GUIDs (SapMessageId(Ex), SapRefToMessageId(Ex) into custom headers) → Add custom header properties → Request Reply to {{GenericXIEndpoint}} (= /generic/step08/xi) → Post-Processing.
