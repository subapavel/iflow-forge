---
name: sap-is-pipeline
description: SAP Integration Suite pipeline concept: generic iFlows, contracts, what a scenario contributes, migration procedures from PO ICO or point-to-point iFlow. Load when designing or generating pipeline-based iFlows.
---

# sap-is-pipeline

The knowledge base of the SAP Integration Suite pipeline concept, written from SAP's pipeline packages and read page by page.

## When you need …

| When you need … | Read |
|---|---|
| the pipeline in one page, with its diagram | `overview.md` |
| every header and exchange-property name between steps: set by, read by | `contracts.md` |
| the message path hop by hop: queues, endpoints, synchronous differences | `message-path.md` |
| what is stored per scenario in the Partner Directory, key scheme, value types | `partner-directory.md` |
| what a scenario adds and must not touch | `scenario-contribution.md` |
| SAP's own naming and the tenant placeholders | `naming.md` |
| one generic iFlow: steps, contract, errors, parameters | `iflows/<id>.md` |
| one SAP scenario template (inbound, outbound, mapping) | `templates/<id>.md` |
| one Groovy or XSLT resource | `scripts/<name>.md` |
| to migrate a PO Integrated Configuration | `migration/po-ico-to-pipeline.md` |
| to migrate a point-to-point iFlow | `migration/is-iflow-to-pipeline.md` |
| the document the human approves before generation | `migration/proposal-template.md` |
| how PO modules and Java mappings are converted | `conversion/README.md` |
| which SAP version is current, and what changed between versions | `versions/README.md`, `versions/<label>/changelog[-<group>].md` |
| lessons learned, contradictions, open questions | `pitfalls.md` |
| draft claims the artifacts contradict | `pitfalls-drafts.md` |
| open questions per content task (uncited draft claims) | `open-questions/<task>[-<group>].md` |
| a term explained | `glossary.md` |
| the benchmark questions and the sample ICO (empty until Task 12) | `benchmark/` |

## Conventions

- Every behavioural statement cites its artifact in a D-019 form such as `[<symbolic-name>, step <n> "<name>"]`; a *Steps* line is its own cite (D-020); a newer-label copy reads `[<label>: <symbolic-name>, …]` (D-023); uncited means open question.
- Tenant-specific values are `{{tenant.<path>}}` placeholders, all listed in `naming.md`; never a real value.
- Load the loaded set (this page, `overview.md`, `contracts.md`, `scenario-contribution.md`; ≤ 5,000 words); every other page on demand (≤ 1,500 words each).

## Self-improvement

- A new or amended recipe in `conversion/` for every module or mapping pattern a migration converted (`conversion/README.md`).
- A pitfall in `pitfalls.md` wherever this knowledge base was wrong, silent or ambiguous.
- A note in the `iflows/` page or `contracts.md` for what it showed about the pipeline; each a commit naming the scenario (anonymised); a migration is done only when they are committed.

## Start here

- A migration starts at `migration/po-ico-to-pipeline.md` or `migration/is-iflow-to-pipeline.md`.
- A question about the pipeline starts at `overview.md`.
