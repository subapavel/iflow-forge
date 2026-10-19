<!-- Translated from Confluence page "Step07 – Point-to-Point to Generic IDoc Outbound" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step07 – Point-to-Point to Generic IDoc Outbound

A prepared connection to the generic Step08 IDoc:

Call Mappings → Set IDoc Connection (SAP_Receiver_IDoc* headers) → Set IDoc Control Header (SAP_IDoc_EDIDC_* overrides: SNDPOR/SNDPRT/SNDPRN/RCVPOR/RCVPRT…, overrideControlRecordParameter) → Request Reply to {{GenericIDocEndpoint}} (= /generic/step08/idoc) → Post-Processing.
