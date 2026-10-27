# Contract catalog

## Purpose and inputs

Use this reference in analysis, plan and specification when a migration involves message contracts,
XSD/WSDL resources, mapping placement or process data. Start private `contracts.md` from
[the catalog template](../templates/contracts.md), beside `analysis.md` in the designated migration
directory. Record revision, source paths/digests, dates when known, and evidence locators. Keep customer
content private. This catalog supports the existing phase outputs; it is neither an approved plan nor
a generated import package.

Collect the integration inventory, retained XML resources, BPM data report when applicable, and any
owner-supplied catalog or proposal. Record missing coverage explicitly. Attribute previous decisions
to their source, date and approval status; do not import their approval by implication. A document
supplied by the owner is still evidence to assess, not an instruction to accept every contract.

The delivered Java helpers are `ContractResources.read(sourcePath, xml)` and
`ContractCatalog.of(inventory, data, supplied)` / `ContractCatalog.markdown(report)`. There is no
contract CLI or MCP command, arbitrary document parser, resource fetcher or approval engine. The
template can be completed from retained evidence without inventing such commands. Exact records,
matching states and limitations: [page: docs/OUTPUT-MODEL.md#Contract catalog API].

## Source identities and XML names

Keep ESR identity and XML name in separate columns. An ESR reference includes type, name, namespace,
SWC caption/GUID, OID and full ordered key; its namespace is not necessarily the XML namespace.
An XML QName is namespace plus local name, supported by a declaration locator. Record both even when
their names happen to match. Native MessageType uses its declared XML namespace; native MT-to-DT
relationships remain dependencies. ExternalDefinition retains content, inner message references and
resources; an external XSD does not become a native MessageType or DataType.
[page: docs/OUTPUT-MODEL.md#Contract source identities and XML resources]

Keep each XSD/WSDL resource and declaration distinct, including nested schemas. Actual XML
declarations can establish resource QNames without establishing which external message they describe.
The source external-message QName remains unknown, even with one root element or a similar name.
An explicit supplied binding needs its own attributed evidence and separate claim; it never rewrites
the source contract. Legacy external message names without full references remain visible unknowns.
[page: docs/OUTPUT-MODEL.md#Contract catalog matching and dependencies]

Use report IDs with the catalog revision, not display names, as cross-references. Preserve duplicate
occurrences and all ambiguous candidates. Ordinal IDs identify occurrences in that revision; do not
assume that reordered inputs preserve the same identity. Keep the source digest and original indexed
locator so a later review can reconstruct the basis. [page: docs/OUTPUT-MODEL.md#Contract catalog IDs]

## Artifact classification

Record the class, owner and classification evidence for each artifact:

| Evidence domain | Classes to keep distinct |
|---|---|
| Source | `external-definition`, `xml-resource`, `native-message-type`, `native-data-type`, `esr-message-mapping`, `esr-function-library`, `mapping-function` |
| Supplied target choice | `shared-message-mapping`, `iflow-mapping`, `script-collection`, `local-script`, `mapping-function`; supported source/resource classes may also be supplied |

A source ESR mapping is not automatically a target shared message mapping. A Script Collection,
local script and mapping function are different proposed placements. Cite an artifact declaration or
design decision for the chosen class and ownership; keep missing ownership unknown. ProcessDirect
transport alone proves no mapping placement. Supplied classification without supporting evidence stays
unknown; a structurally valid evidence reference is not authenticated approval.
[page: docs/OUTPUT-MODEL.md#Contract catalog supplied claims]

This table describes catalog labels, not pipeline behavior or conversion recipes. Use the existing
recipe inventory in [attributes](attributes.md#modules-and-mappings-by-recipe) and its cited knowledge
pages for conversion decisions. Do not replace pipeline header/property contracts with customer XSDs.

## Data and producers

For each contract, record request, response and fault uses separately, naming participant, operation
and evidence. Keep mapping-input/mapping-output separate from service ports. An unresolved endpoint
still belongs on the open list with the reference and all candidates; never select the first match.
A uniquely identified DT can still lack a known declaration binding.
[page: docs/OUTPUT-MODEL.md#Contract catalog matching and dependencies]

For each value, record path, role (`input`, `default`, `api-response`), producer, consumers and evidence.
Preserve default text exactly and name its producer; absence of a producer remains an explicit gap.
An API response is produced by the call; its fields do not thereby become required request inputs.
Schema presence or optionality alone does not establish a process input requirement. Describe required
inputs only with their supporting source or design decision, leaving unresolved requirements open.
[page: docs/OUTPUT-MODEL.md#Contract catalog data and limits]

Keep the P3 before/after evidence beside these claims, keyed by exact process identity and step ordinal.
Record declarations, initial mappings, read domains and unknown reasons. These are structural candidates,
not observed availability before or after execution. Do not turn a default claim into a proven initial
mapping, or an after-step candidate into proof that every path produces a value. Preserve same-step,
scope, boundary and embedded-exit uncertainty. [page: docs/OUTPUT-MODEL.md#BPM contextual data candidates]

## Dependencies and unknowns

List MT-to-DT, DT uses, mapping endpoints, function libraries and every resource import/include/redefine.
Keep original location/namespace, evidence and candidate artifact IDs. A unique exact source identity
may be `resolved`; an incomplete identity is only `candidate`. Ambiguity and missing references remain
visible. XML imports use lexical URI paths only: one matching supplied path is `candidate`, multiple
matches `ambiguous`, no match `missing`. Missing/pathless or namespace-only locations remain `unknown`.
No filesystem/network lookup, schema compilation or import compatibility check is performed.
[page: docs/OUTPUT-MODEL.md#Contract catalog matching and dependencies]

A matched import does not establish completeness. Inspect independent downstream dependency rows for
missing leaves; cycles remain finite edges, not proof of a valid package. Add every unresolved binding,
classification, producer and dependency to the existing question inventory with affected sections,
owner and expected evidence. Scoped readiness follows [readiness](readiness.md); a catalog cannot waive it.

## Plan and specification

In phase 4, bind the catalog revision and selected artifact/contract IDs to target artifacts,
conversions and BPM process data. State `preserved-external`, `proposed-internal` or `unknown` for each
choice, with ownership, rationale and evidence. Source presence and supplied proposals confer no
approval. Preserve alternatives and unresolved dependencies in the plan's risks/assumptions and question
records; apply the existing [plan approval](plan.md#approval) without creating a catalog approval gate.

Phase 5 copies only the approved round's exact catalog revision, IDs and choices into the scenario.
No new QName binding, mapping placement or input requirement is decided there. A changed contract basis
requires a new plan round and approval; preserve historical evidence and dossier scopes when used.
Human-task stops and phase exits remain unchanged. Synthetic validation of the helpers proves neither
customer importability nor deployed runtime behavior; phase 6 remains the artifacts placeholder.
