<!-- Translated from Confluence page "Step04 – Custom Receiver determination a interface determination via Extended Receiver Determination" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step04 – Custom Receiver determination and interface determination via Extended Receiver Determination

For migrating Extended Receiver Determination from PO: the step Map to Receivers (Message Mapping — this is where you upload the XRD mapping from PO, the output is the standard PO Receivers structure) → Define interface conditions (the same SAP-IFDET/IFENDPT/IFNAME/NOREC convention) → the script Merge interfaces with XML Receivers (customInterfaceDeterminationViaXProperties.groovy) combines both.

> Applies to the integrated runtime too (returns receivers and interfaces at once — unlike Reuse Extended Receiver Determination).
