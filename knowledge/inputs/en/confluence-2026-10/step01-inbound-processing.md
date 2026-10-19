<!-- Translated from Confluence page "Step01 – Inbound Processing" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Step01 – Inbound Processing

The Step01 templates represent the sender adapter — the first flow of the pipeline, which you build per sender/protocol (HTTP, SOAP, SFTP…). XI and IDoc senders are handled by the ready-made generic Step01 flows from the Generic Inbound package.

> Integrated runtime: the target queue of these templates ({{JMS_Outgoing}}) is PIPX01.

For the common features of all templates see the parent page Cloud Integration Pipeline – Templates.

Subpages:

- Step01 – At Least Once
- Step01 – Exactly Once
- Step01 – Synchronous
