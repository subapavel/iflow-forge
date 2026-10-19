<!-- Translated from Confluence page "Step04/05 – Custom determinace" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step04/05 – Custom determination

Templates called by the generic steps via the PD parameters CustomXRDEndpoint / ReuseXRDEndpoint / CustomXIDEndpoint. All of them return to the caller a Receivers/Interfaces XML in the pipeline format.

> Why "04/05" and what applies in the integrated runtime. The pipeline has two determination steps: Step04 = Receiver Determination (where to) and Step05 = Interface Determination (by which interface). In the integrated runtime they are merged into a single step — interfaces are determined right together with the receivers. In the integrated runtime you therefore use only the templates that return both receivers and interfaces at once: Custom Receiver Determination, Custom RD+IF via Exchange Properties, Custom RD+IF via Extended Receiver Determination. Conversely, Reuse Extended Receiver Determination and Custom Interface Determination are [FULLY DECOUPLED PIPELINE ONLY].

For the common features of all templates see the parent page Cloud Integration Pipeline – Templates.

Subpages:

- Step04 – Custom Receiver Determination
- Step04 – Custom Receiver determination and interface determination via Extended Receiver Determination
- Step04 – Custom Reciever determination and interface determination via Exchange Properties
- Step04 – Reuse Extended Receiver Determination
- Step05 – Custom Interface Determination
