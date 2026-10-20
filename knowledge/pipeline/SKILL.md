---
name: sap-is-pipeline
description: SAP Integration Suite pipeline concept: generic iFlows, contracts, what a scenario contributes, migration procedures from PO ICO or point-to-point iFlow. Load when designing or generating pipeline-based iFlows.
---

# sap-is-pipeline

This skill is the knowledge base for the SAP Integration Suite pipeline concept: read the page in the right-hand column that matches what you need, and load nothing else.

| When you need … | Read |
|---|---|
| the pipeline in one page: purpose, message path, generic iFlows, diagram | `overview.md` |
| registry of every header and exchange-property name between steps: set by, read by, cite | `contracts.md` |
| the message path hop by hop, JMS queues, ProcessDirect endpoints, synchronous-path differences | `message-path.md` |
| what is stored per scenario in the Partner Directory, key scheme, value types | `partner-directory.md` |
| the checklist of what a scenario adds and must not touch | `scenario-contribution.md` |
| SAP's own naming in the package and the tenant naming placeholders | `naming.md` |
| one generic iFlow: purpose, steps, contract, errors, parameters | `iflows/<id>.md` |
| one SAP scenario template (inbound, outbound, mapping) | `templates/<id>.md` |
| one Groovy or XSLT resource: inputs, outputs, gotchas | `scripts/<name>.md` |
| to migrate a PO Integrated Configuration | `migration/po-ico-to-pipeline.md` |
| to migrate a point-to-point iFlow | `migration/is-iflow-to-pipeline.md` |
| the document the human approves before generation | `migration/proposal-template.md` |
| how PO modules and Java mappings are converted | `conversion/README.md` |
| which SAP version is current, and what changed between versions | `versions/README.md`, `versions/<label>/changelog.md` |
| lessons learned, contradictions, open questions | `pitfalls.md` |
| open questions per content task (uncited draft claims) | `open-questions/<task>[-<group>].md` |
| a term explained | `glossary.md` |
| the benchmark questions and the sample ICO | `benchmark/` |
