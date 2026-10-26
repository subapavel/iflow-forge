# MIG-27 — contextual data report documentation

Status: prepared; start only after implementation writer releases ownership.
Base cb81f95; canonical contract is mig-27-p3b-data-packet.md revision2 and approved43 tests.
Read CODEX-WORKFLOW first; one writer, no nested delegation/private inputs/git/ledger edits.

Allowed edits: docs/OUTPUT-MODEL.md and skills/sap-po-migration/references/bpm.md only.
Inputs: canonical packet, implemented BpmDataAnalysis/BpmDataReport/helpers and approved tests.
No knowledge-page, inventory-format, CLI/MCP command or canonical skill/frontmatter changes.

Document the pure Java helper, exact new record fields/order, version1/bpm-data-v1 and embedded
path report. Show how occurrence-qualified contextual mapping paths preserve shared parts,
source QName/configuration facts, missing/ambiguous endpoints and argument contexts.
Explain initial target ownership versus lexical name candidates; type namespace is not variable
identity. Keep ordinary input/output, MappingActivity, role, argument and unsupported direction
domains distinct. Source declarations do not prove actual execution, valid calls or availability.

Explain finite structural producer unions and separate uncertainty before/after each step,
join/cycle alternatives, same-step ordering limits, disabled/structural rows, opaque missing
writes, scope visibility, local initialization, boundary timing and absent embedded exits.
Conditions do not prune candidate sets. Unknown/default/absent values cannot be synthesized.
Complete source calculation, incomplete calculation and disconnected not-applicable differ;
empty candidates never establish no data. Report source/domain/scope gaps and orphan metadata.

New records explicitly retain null/empty fields and immutable collections. Reused Issue types
keep their existing omission behavior. All interpreted read QName payloads and mapping-level
metadata must be recoverable from the report, including mappings with no parts; component-only
unattributed metadata has a qualified diagnostic rather than a fabricated process binding.

There is no new CLI/MCP entry yet; P3c composition remains pending. Keep the canonical bpm
reference within1500 words (current1473): consolidate the existing path-helper paragraph and add
a concise data-helper pointer/limits, with details in OUTPUT-MODEL. Preserve needed migration
guidance and citation headings. Do not silently expand budget or duplicate the full reference.

Run bounded whitespace, source-link, citation-heading, word-budget and added-line privacy checks.
Final reactor/citation/mirror checks belong to independent verifier after source/docs release.
Handback <=40 lines with actual commands/results/logs and explicit writer release. Independent
documentation review must compare claims with final source/tests, not merely with this packet.
