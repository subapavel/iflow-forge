<!-- Translated from Confluence page "Step04 – Custom Reciever determination a interface determination via Exchange Properties" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step04 – Custom Reciever determination and interface determination via Exchange Properties

Determination without XSLT — the conditions are defined declaratively in the Content Modifier Define receiver and interface conditions via properties following a convention:

- SAP-DYNREC-<ReceiverName> (Boolean, XPath) — receiver condition
- SAP-IFDET-<ReceiverName>~IFIndex<n> (Boolean, XPath) — interface condition
- SAP-IFENDPT-<ReceiverName>~IFIndex<n> — endpoint of the scenario-specific outbound flow
- SAP-IFNAME-<ReceiverName>~IFIndex<n> — interface name (optional)
- SAP-NOREC-Type (Ignore|Error|Default) + SAP-NOREC-Default — behaviour when nothing is found

The scripts Create Receivers XML (customReceiverDeterminationViaXProperties.groovy) and Add interfaces to XML (customInterfaceDeterminationViaXProperties.groovy) then build the resulting XML from the properties. It returns receivers and interfaces at once → in the generic Step04 "Bypass interface determination" is activated and Step05 is skipped.

> Applies to the integrated runtime too (returns receivers and interfaces at once).
