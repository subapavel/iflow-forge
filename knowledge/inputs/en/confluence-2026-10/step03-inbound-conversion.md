<!-- Translated from Confluence page "Step03 – Inbound Conversion" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step03 – Inbound Conversion

A scenario-specific conversion flow called by the generic Step02 (PD parameter InboundConversionEndpoint[_<SenderAlias>]). ProcessDirect entry {{ProcessDirect_EndPoint}}.

Skeleton: Parse XML → (this is where the conversion steps belong: JSON→XML, the adapter's module chain, …) → optionally Define Service Interface Operations + Map Message Type to Operation → Set custom header attributes. The response (the converted payload) is returned to the caller via Request-Reply.
