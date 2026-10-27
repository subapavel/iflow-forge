# Behavior comparison

## Purpose and inputs

Use in analysis or plan review to inspect multiple implementations side by side. Keep input JSON,
comparison output and alignment evidence in the designated private migration directory. Preserve old
reports unchanged. The matrix projects retained source evidence and explicit supplied alignments; it
does not discover semantic equivalence, execute processes or approve a migration.

The input is `BehaviorComparisonInput` with `formatVersion=1`, an ordered list of `Implementation`
records and an ordered list of `Alignment` records. Each implementation has an exact ID, display label,
embedded P3 `BehaviorReport` and optional P4 `ContractCatalogReport`. Missing reports are diagnosed;
file paths inside evidence are inert attribution, not instructions to load files.
[page: docs/OUTPUT-MODEL.md#Behavior comparison API]

Build substantive inputs through the existing Java records: obtain each `BehaviorReport` with
`BehaviorAnalysis.read(exportPath, icosPath)` or the existing `of` API; construct the contract catalog
with `ContractCatalog.of(inventory, behavior.data(), supplied)` when available. Wrap those objects in
`BehaviorComparisonInput.Implementation`, explicitly construct the StepRef/Alignment/Evidence lists,
and persist the input with `io.poxray.json.Json.write(input, path)`. There is no comparison-input builder
CLI. Reuse the actual embedded reports rather than replacing them with paths or summaries.
[page: docs/OUTPUT-MODEL.md#Behavior comparison API]

Minimal readable JSON shape, intentionally incomplete and diagnostic-only:

```json
{
  "formatVersion": 1,
  "implementations": [
    {"id": "A", "label": "Implementation A", "behavior": null, "contracts": null}
  ],
  "alignments": []
}
```

This has no source steps to compare and is not an empty-system proof. Replace nulls with complete typed
reports for substantive comparison. The comparison envelope preserves empty source arrays separately
from null; this does not change standalone P3/P4 serialization semantics.
[page: docs/OUTPUT-MODEL.md#Behavior comparison API]

## Explicit alignment

Choose steps by `StepRef(implementation, process, step)`: exact case-sensitive implementation ID,
exact `BpmPathReport.ProcessKey` and the source Step's reported `ordinal`. Copy ordinals unchanged.
For example, a step at list index 1 may have ordinal 2: use `step=2`, never subtract one. Column indices
and JSON pointer/list positions are zero-based; those positions are not source ordinals.
[page: docs/OUTPUT-MODEL.md#Behavior comparison alignment]

Give each alignment a unique nonblank ID, label, status, member references and evidence. Status
`candidate` requires at least one evidence item with nonblank kind/source/locator/status. `confirmed`
additionally requires a qualifying evidence item whose status is `confirmed`. These are supplied
attributions, not authenticated approval or equivalence. Malformed supplemental evidence is diagnosed
but does not invalidate an otherwise qualified alignment. Preserve the original evidence for review.
[page: docs/OUTPUT-MODEL.md#Behavior comparison alignment]

Each reference must resolve uniquely; at most one member per implementation column is allowed.
Reuse of one occurrence in multiple alignment rows invalidates all affected rows. Names never cause
automatic grouping, and different names alone are no reason to reopen a prior approved choice.

## Reading the matrix

Read each selected cell's eight facets in order:

| Facet | Evidence to inspect |
|---|---|
| `presence` | Original step, location, scope, connectivity and source reasons |
| `conditions` | Incident arcs, conditions and default flags, including endpoint ambiguity |
| `data` | Before/after candidates, declarations, initial/structural mappings and reads |
| `loops` | Source loops associated with the host location |
| `faults` | Associated owner/event declarations and source uncertainty |
| `calls` | The exact step call evidence |
| `channels` | Associated interface/ICO/channel configuration candidates |
| `contracts` | Attributed contract ports, producers or consumers naming this step |

Pointers resolve against that implementation object, for example `/behavior/data/paths/processes/0/steps/1`.
Markdown shows pointers and their original values, including conditions, defaults and scope, with escaped
cells. `presence=available` says a retained source step exists; other facets are `candidate` without
reasons, otherwise `unknown`. Read reasons even when pointers are present. Neither state proves runtime
behavior, successful calls, transactions or actual data availability.
[page: docs/OUTPUT-MODEL.md#Behavior comparison facets and context]

Implementation Context separately retains data, faults, integration configuration, wiring and contract
catalogs. Evidence with no proven step association stays there. A source-only catalog without step
claims remains contextual without requiring a fabricated ancestry link. Do not copy global configuration
onto every step or treat the same context on both sides as equivalence.

## Unknowns and unmatched steps

Invalid alignments retain their row, IDs, evidence and any unambiguous selected cells, but consume no
occurrences. Supported source occurrences not consumed by valid rows appear as `unmatched`, including
disconnected steps. Every row retains all implementation columns; `not-aligned` means no selected cell,
not proof that the implementation lacks the behavior. Duplicate identities remain visible, never selected
by first match. Unsupported comparison versions produce diagnostics and no alignment/unmatched rows.
[page: docs/OUTPUT-MODEL.md#Behavior comparison alignment]

Report format-version and ancestry gates prevent coincident numbers from becoming associations. Path
occurrences require supported behavior/data/path ancestors. Data/fault associations check process source
identity beyond ProcessKey; faults also check input ancestry. Contract step claims require matching
embedded data; a source-only catalog is context. Missing, unsupported or ambiguous associations stay
unknown, retaining valid pointers alongside diagnostic reasons. Preserve upstream issues too.
[page: docs/OUTPUT-MODEL.md#Behavior comparison facets and context]

## Offline commands

Run `po-xray compare-behavior <comparison.json>` for Markdown, or append `--json` for the full report.
`po-xray` is the runner invocation used in [intake](intake.md). MCP
`behavior_comparison(inputPath)` accepts the same local embedded JSON. Neither entry point changes
input/source files, follows evidence paths or contacts a tenant.

Unreadable, missing, malformed or incompatible JSON fails: CLI stderr/exit 1, MCP tool error.
Representable semantic diagnostics return a complete report and CLI success intentionally. Inspect
rows, contexts and issues; exit 0 is no readiness or approval verdict. MCP returns compact JSON up to
80,000 characters; above that, its `message`/`file` envelope points to complete indented JSON at
`<temp>/po-xray/behavior_comparison-*.json`. Read that file; no result is truncated. Spill failure is a
tool error. [page: docs/OUTPUT-MODEL.md#Behavior comparison delivery]

## Plan use

Link the private comparison input/output and alignment evidence from `analysis.md` and relevant plan
sections. Record material differences and unknowns with their affected questions, sections and owners;
retain the source basis instead of asserting equivalence. Use [contract bindings](contracts.md) for
contract choices and [plan approval](plan.md#approval) for decisions. The matrix introduces no approval
gate, phase exit or exception to the human-task stop. Confirmed alignment does not grant phase 5 entry;
whole-plan approval and opt-in readiness still govern.
