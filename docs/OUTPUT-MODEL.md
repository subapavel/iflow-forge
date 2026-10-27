# po-xray — JSON output model (v0.1)

Scope: phase 1 — BPM processes (NW BPM Galaxy) + EJB inventory, read from one NWDI SCA.
Status: **accepted** (2026-10-09), BPM and EJB parts implemented.

## Decisions

1. Artificial uncontrolled joins are contracted directly in `flows` (`via` keeps the join IDs).
2. Expression step tree is not emitted; `text` + `reads` + `functions` only.
3. EJB sources are referenced (path + sha256), not embedded.
4. `dataTypes.fields` only for types referenced by processes, flattened to paths with cardinality.
5. Process identity = `Galaxy.Workflow.Collaboration`; the active (orchestration) pool is in `pool`.
6. Functions in expressions are resolved by MOF ID: first DC functions (`.function`), then the SAP built-in
   catalog (`docs/BPM-BUILTIN-FUNCTIONS.md`, never by name). Unknown IDs are kept and reported.
   The catalog covers DC `predefs/types` (220 functions) and `predefs/default` (principal functions, e.g.
   `getPrincipals` used in role assignments; the editor shows `getPrincipals("<uniqueName>")` as
   `<uniqueName>#<displayName>`).
10. Type names: `xsd:<name>` for XSD types (catalog `xsdTypes`), `sap:<name>` for SAP-defined types of the BPM
    content (catalog `sapTypes`, e.g. `sap:Principal`, `sap:TechnicalErrorType`, `sap:ProcessInstanceAttributes`),
    otherwise the type name from the DC's own XSD (named complex/simple types).
7. Service group per service reference comes from the deployed `.composite` (SDA) and the `mc` logical system.
8. All graph references (`flows.source/target/via`, node `scope`/`lane`, data object `scope`) are `{ref, name}`.
9. Every MappingPart is emitted with `category` / `expander` / `transformer` (also structural parts and
   function parameters); the expression step tree itself is still not emitted (decision 2).

## Principles

- **One JSON document per SCA** (`ScaInventory`). Self-contained, readable by human/LLM without the SCA.
- **IDs = MOF IDs** from the source (`m:i`), so elements pair 1:1 with the BPMN 2.0 export (`BPMelementID`) and the raw partition.
- **Cross-references** are `{ "ref": "<id>", "name": "<display name>" }` — the name is denormalised for readability, `ref` is authoritative.
- **Lossless-ish**: every element carries `source` (`<archive>!/<entry>`) so the raw partition can be found. Element types the reader does not know go to `unmapped[]` (metaclass + attributes), never silently dropped.
- **Source problems are reported in `issues[]`**, never fixed. Names/texts are emitted exactly as in source (typos included).
- **Graph as seen in NWDS**: artificial uncontrolled joins (editor artefacts) are contracted; the contracted flow keeps `via: [joinIds]`.
- Empty/default values are omitted (no `null`, no `[]`), to keep the output small, except inside
  the optional `sourceEvidence` extension, which preserves explicit empty collections and null fields.
  Separately versioned analysis reports below specify their own serialization rules.

## Top level

```jsonc
{
  "schemaVersion": "0.1",
  "generator": { "name": "po-xray", "version": "0.1.0", "readAt": "2026-10-09T12:00:00Z" },
  "source": {
    "file": "EONBPMDIGI00_0.sca",
    "sha256": "…",
    "softwareComponent": {                       // META-INF/SAP_MANIFEST.MF
      "name": "EON_BPM_DIGISIGN", "vendor": "eon.com", "release": "0.0",
      "servicelevel": "0", "patchlevel": "0", "updateVersion": "NIGHTBUILD.17915431",
      "location": "DXX_PODEV_D"
    }
  },
  "developmentComponents": [ /* DevelopmentComponent */ ],
  "bpm":   [ /* BpmComponent — one per Process Composer DC */ ],
  "ejb":   [ /* EjbModule — one per EJB module DC */ ],
  "issues": [ /* Issue */ ],
  "unmapped": [ /* UnmappedElement */ ]
}
```

### DevelopmentComponent

```jsonc
{
  "name": "bpm/digisign", "vendor": "eon.com", "caption": "Digisign BPM",
  "type": "Process Composer",                  // .dcdef component-type: Process Composer | EJB Module | Enterprise Application | Dictionary | …
  "archives": {
    "source": "SOURCEARCHIVES/eon.com~bpm~digisign~.dcsa",
    "deploy": "DEPLOYARCHIVES/eon.com~bpm~digisign~eon.com~bpm~digisign.sda"
  },
  "dependencies": [ { "vendor": "sap.com", "name": "tc/bpem/bi/bp/buildtool" } ],   // from .dcdef
  "deployDependencies": [ { "vendor": "sap.com", "name": "com.sap.aii.af.lib.facade" } ], // from SAP_MANIFEST dependencylist
  "libraries": [ "libraries/mvel2-2.5.2.Final.jar" ]    // External Library DC: jar files (third-party / custom code)
}
```

## BPM

### BpmComponent

```jsonc
{
  "dc": "eon.com/bpm/digisign",
  "processes": [ /* Process */ ],
  "catalog": {                                 // shared, DC-level objects referenced from processes
    "interfaces":        [ /* Interface */ ],
    "serviceReferences": [ /* ServiceReference */ ],
    "functions":         [ /* Function — DC functions (.function) */ ],
    "builtinFunctions":  [ /* BuiltinFunction — SAP built-ins called by the processes */ ],
    "eventDefinitions":  [ /* EventDefinition — DC triggers (src/triggers) */ ],
    "builtinEventDefinitions": [ /* EventDefinition — standard SAP triggers used (e.g. TechnicalError) */ ],
    "dataTypes":         [ /* DataType */ ]
  },
  "sourceEvidence": { /* BpmSourceEvidence; omitted when unavailable */ }
}
```

### BpmSourceEvidence

The optional fourth component of [BpmComponent](../po-xray-core/src/main/java/io/poxray/model/BpmComponent.java)
is [BpmSourceEvidence](../po-xray-core/src/main/java/io/poxray/model/BpmSourceEvidence.java), an independently
versioned snapshot of selected **parsed-source evidence**. New inventories read by `BpmReader` include it.
The shared `ScaInventory.schemaVersion` remains `"0.1"`; existing Process, Node, Flow and Mapping meanings
remain unchanged, including true-only default/terminate and false-only enabled projections.

Old JSON without `sourceEvidence` reads as null; null is omitted on serialization and means evidence is
unavailable in that inventory. An explicit extension with empty `elements` and `links` means that its
selection rule found no facts. Neither state establishes an empty or complete process. The existing
three-argument `BpmComponent` constructor delegates with null, preserving source compatibility for those
calls. Adding a record component does not guarantee binary compatibility, compatibility of record
patterns, or compatibility with strict consumers of the older JSON shape.

| Field | Meaning |
|---|---|
| `formatVersion` | Integer `1`, version of this extension's representation. |
| `selectionVersion` | `"bpm-source-v1"`, the selection rule below. |
| `indexedPartitions` | All actually indexed partition paths, in encounter order; not every archive entry. `.diagram` entries are excluded by the reader. |
| `processPartitions` | All indexed paths starting `src/bpmn/` and ending `.bpmn`, in index encounter order. Includes candidates from which no Process can be interpreted, for example without Collaboration. The legacy `processes` list retains its separate sorting/filtering. |
| `elements` | Selected full Element payloads, once per occurrence, in partition/element encounter order. |
| `links` | Selected Link occurrences in partition/link encounter order, including duplicate links. |

The nested record fields are:

| Record | Fields and meaning |
|---|---|
| `Locator` | `archive`: the DC source archive (`BpmContext.sourceArchive`); `partition`: indexed entry path; `ordinal`: one-based occurrence in that partition's element **or** link list, not an XML line number. |
| `Element` | `location`: Locator; `id`: parsed MOF ID; `metaclass`: parsed element tag; `attributes`: attribute-name/string map; `texts`: child-tag/`originalText` map; `values`: child-tag/list-of-`v`-strings map; `properties`: ordered Property entries from the parsed properties map. |
| `Property` | `key`, `value`: nullable parsed strings. A missing key remains JSON null, not the string `"null"`; a missing value remains null. |
| `Candidate` | `location`: element occurrence; `metaclass`: its parsed metaclass; `included`: whether its full Element payload is in this extension's `elements`. |
| `Endpoint` | `id`: normalized parsed endpoint ID; `resolution`: `unique`, `missing` or `ambiguous`; `candidates`: all matching indexed element occurrences, in partition/element encounter order. |
| `Link` | `location`: link occurrence; `association`: parsed association tag; `first`: the `f` Endpoint; `second`: the `s` Endpoint. Direction is preserved. |

Occurrence identity is the Locator together with the element/link list kind. IDs may repeat within or
across partitions; they do not uniquely identify retained occurrences. Endpoint resolution uses all
indexed occurrences: one candidate is `unique`, zero is `missing`, more than one is `ambiguous`.
Null and blank IDs remain as parsed and resolve as missing. There is no name fallback or first-match
choice. A unique candidate with `included=false` is a boundary reference whose payload was not selected;
it is neither a missing endpoint nor a full payload. Ambiguity is retained even when candidates are selected.

All extension and nested-record collections are deeply immutable snapshots, including lists inside
`values`. Parsed map iteration order and list encounter order are retained. Empty collections and
nullable fields are explicit in JSON throughout the extension. A selected element's null property key
retains `Property(null, value)` and emits `BPM_SOURCE_PROPERTY_KEY_MISSING` with its element occurrence
and one-based property-entry index; a valid key with a null value is retained without fabricated content.

### BPM source selection

`bpm-source-v1` is implemented by
[BpmEvidenceReader](../po-xray-core/src/main/java/io/poxray/bpm/BpmEvidenceReader.java) over the existing
MOIN index:

1. Seed every element occurrence in `processPartitions`, including unknown metaclasses. Also seed
   elements anywhere whose metaclass starts `Galaxy.Workflow.`, `Galaxy.Mapping.` or `Galaxy.Rule.`,
   or equals `Galaxy.ProcessSupplements.NotificationActivity`.
2. Select every link occurrence in a process partition. Elsewhere, select links whose association
   starts one of those three prefixes or whose endpoint ID matches an original seed ID. Selected links
   with both endpoints missing and repeated links remain visible.
3. Resolve selected endpoints against all indexed element occurrences. Add full payloads for direct
   endpoint candidates of metaclass `XSD1.key.QName` or `properties.PropertyBag`. Other non-seed
   candidates remain boundary references with `included=false`. Do not expand links of newly added
   supports or recursively expand WSDL/XSD trees.
4. Emit the selected elements and links in their original per-list order. `Candidate.included` reflects
   the final selected-element set.

The retained links preserve owner → mapping → root part → child part → QName/expression identities
where present in the parsed source. Flattened `Assignment.target` paths and `Expression.reads` names
do not replace those identities. Each missing endpoint emits `BPM_SOURCE_REFERENCE_UNRESOLVED`;
each ambiguous endpoint emits `BPM_SOURCE_REFERENCE_AMBIGUOUS`, in `ScaInventory.issues`. Diagnostics
retain association, `f`/`s` side, original ID and link occurrence; repeated bad links and both missing
sides remain separate warnings. A unique boundary reference alone produces no such warning.

This is a subset of the parsed index, not byte-exact XML or proof of runtime execution. The existing
[MoinParser](../po-xray-core/src/main/java/io/poxray/moin/MoinParser.java) omits MOIN-internal attributes,
strips `local:` from endpoint references, and collapses repeated map keys before selection. The
extension cannot restore those losses or XML line offsets. Keep the original source and its
`ScaInventory.source.sha256` digest for provenance. Raw flags preserve present string values such as
`"false"`, `"true"` and unsupported tokens separately from absent keys. Loop declarations and fault
attachments are retained facts; this slice does not establish current runtime behavior, reachability,
name-to-ID binding, loop execution, fault interception or data producers.

### BPM evidence output

Use existing `po-xray json <sca> [-o file]` or `po-xray export <sca> -o <export-dir> [-l cs|en]`.
Full JSON contains the extension. The existing MCP `write_sca_documentation` export includes it when
writing JSON. The offline [behavior command and MCP tool](#behavior-cli-and-mcp-delivery) compose
this evidence into source analysis and configured integration candidates.

[DocumentBuilder](../po-xray-core/src/main/java/io/poxray/doc/DocumentBuilder.java) adds a component-level
Markdown summary: selection version, indexed and selected process partitions, element IDs/metaclasses
and present attribute maps, then associations with directed `f`/`s` IDs, resolution status and candidate
payload selection. Locators render as `<archive>!/<partition>#E<n>` or `#L<n>`; these suffixes are
element/link ordinals. Raw loop flags remain visible. Detailed texts, values and properties remain in
JSON. A null extension is described as unavailable in this inventory.

Markdown mapping tables include DataObject initial-value mappings and structural assignments even
when `expr` is absent, and retain disabled rows. Absent `expr` is labelled structural; an expression
with null text is labelled unavailable. A blank expression does not mean deletion. Missing `enabled`
in the legacy projection does not establish execution. Supplied cell text is escaped for Markdown.

### BPM structural paths

The core Java helper
[BpmPathAnalysis](../po-xray-core/src/main/java/io/poxray/bpm/BpmPathAnalysis.java) exposes
`public static BpmPathReport of(List<ScaInventory> inventories, List<String> labels)` and
`public static String markdown(BpmPathReport report)`.
It builds a separate [BpmPathReport](../po-xray-core/src/main/java/io/poxray/model/BpmPathReport.java)
from retained source occurrences, without file IO, timestamps or input mutation. Supply one non-null
label per non-null inventory; null arguments/elements or differing list sizes throw
`IllegalArgumentException`. Empty input returns explicit empty collections. Missing optional legacy
fields are evidence gaps. This report does not change `ScaInventory` or its shared schema version.

Existing `po-xray json` and `po-xray export` provide the source evidence described above. The data
helper below embeds the path report, delivered at `data.paths` in the separate
[BehaviorReport](#bpm-configured-behavior).

The report uses `formatVersion=1`, `analysisVersion="bpm-paths-v1"`. All collections are deeply
immutable snapshots. The new BpmPathReport records serialize null fields and empty collections
explicitly; reused `Issue` and `Issue.Location` records retain their existing null omission. Fields
below are listed in record/JSON order. `Locator` retains the BpmSourceEvidence archive/partition/ordinal shape.

| Record | Fields and meaning |
|---|---|
| `BpmPathReport` | `formatVersion`, `analysisVersion`; `inputs`: Input rows; `processes`: ProcessPaths rows; `issues`: analysis Issue rows with `BPM_PATH_*` codes. |
| `Input` | `ordinal`: input position; `label`: supplied label; `sha256`: inventory source digest, or null when unavailable. |
| `ProcessKey` | `input`, `component`, `process`: positions in the input, BPM-component and process lists. |
| `Fact` | `kind`: `element` or `link`; `location`: source Locator. These two occurrence lists have separate ordinal spaces. |
| `Flag` | `present`: whether the attribute key exists; `value`: exact raw string or null; `interpretation`: `true`, `false` or `unknown`. |
| `Endpoint` | `id`: normalized source ID, including null/blank; `resolution`: source `unique`, `missing` or `ambiguous`; `candidates`: all source candidate Locators. |
| `Condition` | `state`: condition classification below; `text`: preserved editor text, literal fallback or null; `evidence`: Facts; `reasons`: uncertainty tokens. |
| `Step` | `ordinal`: row number; `location`: source element Locator or null for unavailable legacy evidence; `id`, `kind`, `name`: node identity/classification; `scopes`: declared scope Locators (or a valid inherited boundary scope); `connectivity`: `connected`, `disconnected` or `unknown`; `witness`: Arc ordinals; `terminate`: Flag; `reasons`: explanatory tokens. |
| `Arc` | `ordinal`: row number; `kind`: `sequence`, `boundary-attachment` or `embedded-entry`; `location`, `id`: source connector occurrence/ID, both null for derived arcs; `sources`, `targets`: Endpoint declaration lists; `resolution`: `resolved`, `missing`, `ambiguous` or `invalid`; `condition`: Condition; `isDefault`: Flag; `evidence`: Facts; `reasons`: explanatory tokens. |
| `Loop` | `location`, `id`: declaration occurrence/ID; `hosts`: all candidate host Locators; `mode`: `sequential`, `parallel` or `unknown`; `sequential`: Flag; `flowCondition`: raw attribute value; `completion`: `all` or `unknown`; `evidence`: Facts; `reasons`: explanatory tokens. |
| `ProcessPaths` | `key`: ProcessKey; `dc`, `id`, `name`, `source`: process provenance; `coverage`: `complete`, `incomplete` or `unavailable`; `reasons`: coverage tokens; `steps`, `arcs`, `loops`: the corresponding rows. |

All ordinals are one-based. Qualify every Locator by its ProcessKey and its element/link kind;
MOF IDs alone cannot connect occurrences across inputs or components. Input/component/process list
order is retained. Steps, original sequence arcs and loops follow source element encounter order;
boundary arcs follow source link order, then embedded-entry arcs follow host/start encounter order.
Step and Arc ordinals address rows within one ProcessPaths, and each witness is a list of those Arc
ordinals. Repeated analysis of the same inputs and labels is deterministic.

### BPM path coverage and witnesses

[BpmPathGraph](../po-xray-core/src/main/java/io/poxray/bpm/BpmPathGraph.java) reconstructs the supported
graph. Analysis admits source evidence only at format `1`/selection `bpm-source-v1`, matching
`Process.source` exactly to `archive + "!/" + partition`. It requires a unique matching Collaboration and resolves pool/scope
relations by occurrence. Exactly one linked pool must explicitly declare `active="true"`, and all
others must explicitly declare `"false"`. Duplicate links, ambiguity, missing/invalid flags or an
invalid root do not invoke the legacy first-pool fallback.

The supported structural relations have the `Galaxy.Workflow.` prefix:
`CollaborationContainsPools`, `ScopeContainsScopeObjects`, `SourceOfIncomingConnectors`,
`TargetOfOutgoingConnectors` and `ActivityHasBoundaryEvent`. Recognized metadata is kept separate.
Other relations touching a current control node, connector or scope produce
`BPM_PATH_UNSUPPORTED_CONTROL_RELATION` and incomplete coverage. Unrelated mapping/type diagnostics
alone do not create graph gaps.

Coverage describes only this supported graph within retained parsed evidence:

- `complete`: no detected structural gap in that graph; not a claim about the whole archive or runtime.
- `incomplete`: a root, membership, node-kind, endpoint, scope or supported-relation gap prevents
  process-wide negative conclusions. This includes missing/ambiguous/multiple/cyclic memberships,
  unscoped known control objects, a Pool declared as a scope member, unknown scope-member kinds,
  unsupported cross-scope sequence edges, no valid root StartEvent, or an embedded scope without a
  uniquely scoped StartEvent. Unknown nodes remain rows, and membership cycles terminate.
- `unavailable`: absent/unsupported source evidence or an unresolvable process source. Legacy node
  rows retain their order with null locators, unknown connectivity and reasons; no source arcs are invented.

Candidate source partitions without an interpreted Process produce
`BPM_PATH_UNINTERPRETED_PARTITION`; an empty process list is not silent proof of coverage. Missing
components/process entries have `BPM_PATH_COMPONENT_UNAVAILABLE`/`BPM_PATH_PROCESS_UNAVAILABLE`
warnings. Graph gaps use `BPM_PATH_GRAPH_INCOMPLETE`; link diagnostics retain occurrence, raw
endpoint IDs and candidates. Missing condition or loop metadata alone does not invalidate structural
coverage: it has its own unknown state.

Sequence arcs preserve each original SequenceConnector/ConditionalSequenceConnector occurrence,
including joins contracted by the legacy process projection. Source and target relations both use
`f=node,s=connector`. Every associated node endpoint declaration remains in `sources`/`targets`, with link
Facts; an empty list means a missing association. An arc resolves only with one unique declaration
on each side, correct endpoint kinds and a shared valid scope. Duplicate declarations remain
ambiguous; null/blank IDs, missing endpoints and all candidates remain visible.

Derived `boundary-attachment` arcs run owner → boundary, while the source
`ActivityHasBoundaryEvent` relation has `f=boundary,s=owner`. Each source link produces an arc.
Traversal requires one unique attachment to the exact BoundaryEvent kind, a uniquely scoped owner
of kind automatedActivity, mappingActivity, notificationActivity or embeddedSubprocess, and matching
scope. A boundary lacking membership may inherit that owner's scope with `boundary-scope-from-owner`;
conflicting or multiple memberships, including declarations in other partitions, remain invalid.
Duplicate links or multiple owners remain ambiguous. Derived `embedded-entry` arcs run host → child
StartEvent through valid memberships. Invalid derived relations remain arcs/issues with reasons.
Neither derivation proves fault selection or execution. No child-end → parent-successor arc is synthesized.

Traversal starts only at StartEvents uniquely scoped to the validated active root and follows only
resolved arcs with a finite visited set. Nested starts are not independent roots. Conditions never
prune connectivity: a `constant-false` arc can still supply a structural witness. A root start has an
empty witness and `root-start` reason; other connected rows have an actual Arc-ordinal path, choosing
the first witness in stable arc order. A valid positive witness survives an unrelated graph gap.
Without a witness, connectivity is `disconnected` only in a complete graph; otherwise it is `unknown`.
Nodes in passive pools are unknown with `inactive-pool`, not asserted unreachable in their own process.
Connected does not mean executed, condition-feasible, guaranteed or runtime-reachable.

### BPM path conditions and loop declarations

[BpmPathConditions](../po-xray-core/src/main/java/io/poxray/bpm/BpmPathConditions.java) preserves
attribute presence and exact strings. Only exact `"true"`/`"false"` give those Flag interpretations;
missing is `present=false,value=null,interpretation="unknown"`. `isDefault`, `terminate` and
`sequential` are source declarations, not branch priority, executed termination or loop execution.

`Condition.state` distinguishes `missing`, `unresolved`, `ambiguous`, `unknown`, `constant-true`,
`constant-false` and `unconditional`. A SequenceConnector is unconditional only without a condition
association; unexpected condition links make it unknown. Conditional connectors retain all
`ConditionalSequenceConnectorContainsExpression` links (`f=expression,s=connector`). An absent association
gives `missing`, unresolved endpoints give `unresolved`, and multiple associations/candidates give `ambiguous`.
Derived arcs have unknown conditions with their structural-relation reason.

Constant recognition requires one uniquely resolved `Galaxy.Rule.Expression`, one `ExpressionHasSteps`
link to a unique `Galaxy.Rule.Literal`, and one `LiteralHasSimpleDataType` link (`f=type,s=literal`).
The type ID must be recognized as `xsd:boolean` by the shipped BuiltinFunctionCatalog, with **no local
source candidate**. This is a deliberate catalog-reference exception: the retained source endpoint
may be missing while the catalog supplies the Boolean type; the type-link Fact remains evidence.
Any local/conflicting type candidate stays unknown; arbitrary XSD declarations are not inferred.
The literal value must be exact `true` or `false`. Missing/blank editor `stringExpression` permits
tree-only recognition; nonblank text must strip to the same literal. Original editor text, including
blank/padded text, is preserved, with the literal value used only when editor text is absent.
A reference named false, untyped literal, complex/multiple steps or editor/tree disagreement cannot
become a constant from rendered text alone. This is not an expression evaluator.

Loop rows retain every `Galaxy.Workflow.MultiInstanceLoopCharacteristics` occurrence in the process
partition and every such occurrence referenced by an attributable attachment, once per occurrence
per process. The exact source spelling is `Galaxy.Workflow.EmbeddedScopeContainsLoopCharcateristics`,
with `f=loop,s=host`. A row groups all attachment Facts and keeps every candidate host without choosing
a winner. Related expression, current-item and completed-iterations association Facts remain evidence.
An unattached declaration has empty hosts, unknown mode/completion and `unattached-loop`.

A valid attachment has one unique loop, one unique EmbeddedScope host belonging to the process and
exactly one attachment for both declaration and host. With that attachment, explicit sequential
true/false declares sequential/parallel mode, otherwise unknown; exact `flowCondition="all"` declares
completion `all`, otherwise unknown. The raw values remain visible independently of interpretation.
Duplicate attachments, multiple declarations on a host, missing/ambiguous endpoints or unsupported
kinds keep mode/completion unknown and emit `BPM_PATH_LOOP_UNRESOLVED`, `BPM_PATH_LOOP_AMBIGUOUS` or
`BPM_PATH_LOOP_UNSUPPORTED` as applicable. Dangling/unsupported attachments can produce diagnostics
even when no Loop row exists, so empty loops alone do not prove absence. A cross-process declaration
can appear under each attributable ProcessKey with its original Locator.

Markdown displays inputs/digests, process coverage/reasons, scopes, structural statuses, witnesses
with Arc kinds, source/derived arcs, condition/default distinctions, raw flag presence, loop
declarations and diagnostics. Cells are escaped. All source-evidence normalization/loss limits still
apply. The report establishes no fault priority/interception, iteration count, join timing,
successful completion, target name binding, data-producer availability or runtime feasibility.
Embedded exits are omitted: child effects at parent successors remain unknown unless separately
justified completion evidence is supplied to later analysis.

### BPM contextual data candidates

The core Java helper
[BpmDataAnalysis](../po-xray-core/src/main/java/io/poxray/bpm/BpmDataAnalysis.java) exposes
`public static BpmDataReport of(List<ScaInventory> inventories, List<String> labels)` and
`public static String markdown(BpmDataReport report)`. It computes BpmPathAnalysis once and embeds
that report for exact process, step and arc addressing. The input contract is the same: one non-null
label per non-null inventory, with `IllegalArgumentException` for null arguments/elements or differing
list sizes; empty input is valid. It performs no file IO, clock reads or input mutation.

This is a separate report of **declared source candidates and structural provenance**, not runtime
values, successful calls, execution, overwrites or definite availability. It does not change the
inventory schema. Existing `json`/`export` outputs provide source evidence; the offline
[BehaviorReport](#bpm-configured-behavior) embeds this report at `data`.

[BpmDataReport](../po-xray-core/src/main/java/io/poxray/model/BpmDataReport.java) uses
`formatVersion=1`, `analysisVersion="bpm-data-v1"`. New records have deeply immutable collection
snapshots and explicit null/empty JSON fields. Reused `Issue`/`Issue.Location` retain their existing
omission behavior. Fields below are in record/JSON order. Element, Link, Endpoint and Locator mean
BpmSourceEvidence types; ProcessKey and Fact mean BpmPathReport types.

| Record | Fields and meaning |
|---|---|
| `BpmDataReport` | `formatVersion`, `analysisVersion`; `paths`: the embedded BpmPathReport; `processes`: ProcessData rows; `issues`: source-inventory issues, then path issues, then data diagnostics in stable order. |
| `ProcessData` | `key`: ProcessKey; `dc`, `id`, `name`, `source`: process provenance; `coverage`: `complete`, `incomplete` or `unavailable`; `reasons`: uncertainty tokens; `objects`, `mappings`, `declarations`, `reads`, `steps`: the corresponding rows. |
| `ObjectData` | `ordinal`: object row; `source`: full DataObject/TextVariable Element; `memberships`: source Links; `scopePath`: immediate containing scope through ancestors to pool, inner first; `types`: type Links; `typeCandidates`: included candidate Elements; `scopeState`: `active`, `inactive` or `unknown`; `reasons`. |
| `MappingUse` | `ordinal`: contextual use; `parent`, `parentDeclaration`: enclosing use/declaration for arguments, null for top-level uses; `ownership`: source Link or null for an orphan; `mapping`: source Endpoint or null when no mapping fact exists; `rootOwner`: top-level owner Locator or null; `role`, `readDomain`, `writeDomain`: distinctions below; `evidence`: Evidence; `reasons`. |
| `Evidence` | `path`: contextual traversal as Facts; `elements`: retained full Element payloads; `links`: retained source Links. |
| `Name` | `links`: QName association Links; `candidates`: included candidate Elements, preserving raw name/namespace; `state`: `resolved`, `missing`, `ambiguous` or `invalid`. |
| `Binding` | `state`: `owned`, `lexical-candidates`, `external`, `unknown` or `none-observed`; `objects`: ObjectData ordinals; `reasons`. |
| `Declaration` | `ordinal`: contextual declaration; `mapping`: MappingUse ordinal; `part`: source Endpoint; `targetPath`: one Name per root/child part; `evidence`; `target`: Binding; `valueKind`: `expression`, `structural` or `unknown`; `candidacy`: `candidate`, `disabled`, `structural`, `external` or `unknown`; `reasons`. |
| `Read` | `ordinal`: read row; `declaration`: enclosing Declaration ordinal; `domain`: read domain; `evidence`; `binding`: Binding; `candidates`: OriginSets; `reasons`. |
| `OriginSet` | `object`: ObjectData ordinal; `declarations`: candidate Declaration ordinals; `coverage`: `complete`, `incomplete` or `not-applicable`; `reasons`. |
| `StepState` | `step`: embedded path Step ordinal; `location`: its Locator; `connectivity`: its structural status; `before`, `after`: OriginSets; `reasons`. |

Ordinals are one-based within ProcessData, except StepState.step addresses the matching path process's
Step. Qualify Locators with ProcessKey and distinguish element/link Facts. Process order follows
`paths`; objects follow source element order. Top-level ownership links follow source order, with
nested argument uses inserted depth-first. Roots and child links are walked depth-first in source
order; declarations/reads receive encounter ordinals. Remaining unattributed mappings/parts follow
source order after attributed uses. OriginSets follow object order, with producer IDs in ascending
declaration order. Repeated analysis of the same inputs/labels is deterministic.

### BPM data bindings and evidence

[BpmDataContext](../po-xray-core/src/main/java/io/poxray/bpm/BpmDataContext.java) uses admitted
format-1/`bpm-source-v1` evidence and the exact process-source match used by the path report.
Unavailable evidence gives ProcessData coverage `unavailable`, empty fact-derived lists and matching
path StepState rows with unknown connectivity and explicit reasons; it invents no declarations.
Object rows retain source membership/type evidence, including orphan or ambiguous scopes. Type
declarations, absent/excluded schemas and schema defaults never become initializers.

Attribution follows validated occurrence ancestry to this collaboration's pools, including passive
pools. Positive ownership takes precedence over a link's physical partition. Ambiguous owner
candidates touching several processes give an unknown use in each, with null rootOwner. Local
orphan/malformed metadata can be attributed as unknown only when its partition belongs to exactly
one interpreted process. Shared or otherwise unattributed component metadata produces
`BPM_DATA_UNATTRIBUTED_SOURCE`, qualified by input/component ordinal, DC and exact source occurrence;
it does not fabricate a process binding or copy the whole payload into that diagnostic. The original
sourceEvidence remains its raw evidence. Affected component processes carry
`unattributed-component-data` and incomplete coverage. Other uncertainties use `BPM_DATA_UNCERTAIN`
with process/occurrence context; source and path diagnostics remain visible.

Visibility requires unique acyclic scope ancestry to an active pool. Same-scope and ancestor objects
are visible; children, siblings and passive-pool objects are excluded only when scope evidence
supports that conclusion. Same-name inner/ancestor objects remain alternatives, with no shadowing
precedence. Unknown ancestry retains plausible lexical candidates with unknown binding, even when
the candidate list is empty.

[BpmDataMappings](../po-xray-core/src/main/java/io/poxray/bpm/BpmDataMappings.java) keeps contextual
ownership/root/child paths. A shared mapping or part under two owners or two diamond paths yields
separate uses/declarations. Cycles terminate with a visible terminal row and reason; there is no
legacy depth-64 truncation. Missing, ambiguous and wrong-kind part endpoints retain terminal
declarations, raw Endpoint candidates and gaps instead of selecting the first match.

`InputMappingOfMappableObject`, `OutputMappingOfMappableObject` and `ViewContainsMapping` use
`f=mapping,s=owner`; `MappingContainsMappingParts` uses `f=part,s=mapping`, and
`MappingPartHasChildren` uses `f=child,s=parent`. TargetPath retains every root/child QName state.
Initial target Binding is `owned` by the exact DataObject/TextVariable occurrence, independently
of the root QName. Other supported process writes use the root QName's exact raw name to find
visible object-name candidates; even one match remains `lexical-candidates`. QName namespace and
data-type namespace are retained separately and do not prove variable identity. A missing root
name cannot fall back to a child's name, type name, prefix stripping or a guessed identifier.
`none-observed` requires validated scope/root-name evidence and still does not prove runtime absence.

The helper assigns these roles/domains to source declarations:

| Ownership and owner | Role | Read domain | Write domain |
|---|---|---|---|
| ViewContainsMapping on DataObject/TextVariable | `initial` | `unknown` | `process-context` |
| Input/output on MappingActivity | `mapping` | `process-context` | `process-context` |
| Input on AutomatedActivity/NotificationActivity/EndEvent | `input` | `process-context` | `external-input` |
| Output on AutomatedActivity/NotificationActivity/StartEvent/BoundaryEvent | `output` | `external-output` | `process-context` |
| Input on StartEvent/BoundaryEvent, output on EndEvent, or other unsupported owner/direction | `unknown` | `unknown` | `unknown` |
| Mapping on EmbeddedScope | `unsupported-embedded` | `unknown` | `unknown` |
| ViewContainsMapping on process/lane role | `role-assignment` | `unknown` | `role` |
| FunctionInvocationHasParameterMapping | `argument` | inherited from enclosing expression | `function-argument` |

Argument uses retain parent/parentDeclaration and the top-level rootOwner; their targets are not
process writes. Unsupported directions retain declarations and uncertainty, not supported writes.
External-output reads never bind to same-named process objects.

Each Evidence.path preserves contextual traversal and can repeat a Locator for a cycle. Raw
Evidence.elements/links follow source order once per Locator within that Evidence; distinct source
occurrences remain distinct. Interpreted read paths retain every segment's QName payload, including
raw name and namespace, without requiring the original inventory to recover those values. Feature
references remain independent evidence; excluded payloads retain `included=false` in their endpoints.
MappingUse evidence includes mapping attributes/configuration even when it has no parts. Incident
metadata links are retained in either direction; further traversal is limited to
MappingPartConfiguration, NSPrefix and PropertyBag supports, with cycle protection. Unknown
configuration remains `configuration-semantics-unknown`; it does not establish effective flags or
precedence, and does not trigger traversal of a function implementation or XSD graph.

[BpmDataExpressions](../po-xray-core/src/main/java/io/poxray/bpm/BpmDataExpressions.java) extracts
syntax from source links, never from flattened Assignment/Expression.reads or parsed editor text.
`valueKind=expression` requires one unique part expression and a nonempty ordered sequence of
uniquely resolved Reference, Parent, Literal, FunctionInvocation or Condition steps. No expression,
or a unique empty tree with absent/blank editor text, is structural; nonblank text without a tree is
unknown. Structural rows do not imply scalar writes, deletion, object creation or defaults.
Raw part flags, literal values/types and expression text remain evidence. Missing literal data,
unknown function identity and malformed nested branches add uncertainty; independent reads and
otherwise supported outer assignment syntax remain visible. No function arity/body or expression
execution is evaluated.

An ordinary read root is a leading Reference with a unique retained nonblank QName; the whole
reference path is retained. A leading Parent gives unknown root, not lexical scope lookup.
References after a literal/function result are result paths, not new process roots. Condition
branches and argument expressions are traversed independently without pruning; unresolved,
ambiguous or cyclic paths stop with gaps while independent reads survive. Initializer reads retain
`initializer-order-unknown`. Contextual/process reads at loop hosts/contained scopes retain
`loop-local-context-unknown` and unknown Binding: an exported same-name object cannot disprove a
loop-local source. External-output reads retain external Binding.

### BPM data flow and uncertainty

[BpmDataFlow](../po-xray-core/src/main/java/io/poxray/bpm/BpmDataFlow.java) propagates two finite
monotone unions over resolved structural arcs: known declaration IDs and uncertainty reasons.
Joins retain alternatives; cycles reach a fixed point and may bring a declaration into its own
before state. Conditions, including typed false/default declarations, never prune these sets.
There is no destructive overwrite or removal based on set/append/merge flags or lexical guesses.

A declaration is a candidate only for a supported process-context write, expression syntax and
owned/lexical-candidate target. Exact local `enabled=false` remains a disabled row and contributes
no propagated producer ID; child extraction continues. Missing/invalid enabled, a disabled ancestor
and uninterpreted configuration retain uncertainty without inventing inherited flag values. A
child's otherwise supported candidate can therefore remain alongside that uncertainty.

Initial candidates seed their own validated scope entry before a connected StartEvent, never every
step globally. Ancestor candidates may enter child scopes; child-local initials/writes cannot leak
to parents or siblings. An absent valid entry adds uncertainty without a fabricated seed. Ordinary
candidate writes are added after their connected owner step. Disconnected/unknown owners remain
declarations without seeding an asserted connected flow.

StepState.before/after list proven-visible objects in object order. Connected steps retain supported
known candidates even when gaps remain. For a resolved boundary attachment, transfer the owner's
before/after union with `boundary-timing-unknown`; do not assert that its output completed first.
Embedded entry transfers visible parent candidates plus child-scope initials. No child exit is
synthesized: an embedded host's parent successor and downstream states carry
`child-effects-unmodeled`, without fabricated child producer IDs on a nonexistent return edge.

`OriginSet.coverage` separates these outcomes:

- `complete`: the supported selected-source calculation has no applicable uncertainty; this is not
  exhaustive runtime producer coverage or definite availability.
- `incomplete`: applicable scope/domain/graph/effect uncertainty remains alongside any known IDs.
- `not-applicable`: a structurally disconnected step in the complete modeled graph has empty sets
  and `not-reached-in-modeled-graph`; its declarations remain in the report.

Empty candidates mean only that no supported declaration was observed on these modeled paths.
They never establish no data, null, a scalar value or a schema default. Unknown connectivity or
visibility remains incomplete, including when no visible-object rows can be established. A passive
pool does not become a complete active state. ProcessData coverage is unavailable when evidence is
inadmissible, incomplete when path/data evidence has unresolved or unsupported interpretation, and
complete otherwise. Lexical-candidate binding is an explicit supported heuristic and alone need
not make the source calculation incomplete.

Missing roots, targets, parts, expressions or unsupported process-write effects can propagate
`opaque-write` without any producer ID. At a known connected owner this starts after that step,
affecting known target candidates or all proven-visible objects when the target is unknown, then
travels through outgoing arcs, joins, cycles and boundary transfer. Initializer uncertainty starts
at its known scope entry. Unknown owners/attribution or an incomplete graph introduce process-wide
uncertainty. Scope filtering never turns missing knowledge into a complete empty result. Supported
external-input, argument and role writes do not create opaque process writes merely by being
external; missing reads do not themselves erase established provenance.

Process reads normally use their enclosing step's before sets. MappingActivity reads also retain
other eligible same-step writes with `intra-step-order-unknown` and incomplete coverage. The read's
own declaration and argument-enclosing declaration chain are excluded from new same-step
alternatives, but remain candidates if they already reach before through a structural cycle.
A mapping-part parent is not automatically an argument-enclosing declaration. Ordinary activity
inputs do not acquire that activity's later output as an input producer. Initializer/unknown-step
reads stay incomplete; disconnected reads are not-applicable.

Markdown shows object/source/type/scope evidence, contextual mapping roles/domains, declarations,
read bindings and candidate sets, step before/after states and diagnostics, with escaped cells.
All parsed-source loss/selection limits still apply. Neither source declarations nor these finite
candidate sets establish successful calls, final values, exact intra-step ordering, loop execution,
fault interception, runtime feasibility or definite data availability.

### BPM source event and fault declarations

The pure core helper
[BpmFaultAnalysis](../po-xray-core/src/main/java/io/poxray/bpm/BpmFaultAnalysis.java) exposes
`public static BpmFaultReport of(List<ScaInventory> inventories, List<String> labels)` and
`public static String markdown(BpmFaultReport report)`. It validates the same input contract as
BpmPathAnalysis: non-null lists of equal size, with no null inventory or label; violations throw
`IllegalArgumentException`. Empty input is valid. It computes the path report once for process
addressing and scope admission, without embedding that report. It performs no IO, clock reads or
input mutation. `markdown(null)` also throws `IllegalArgumentException`.

This report exposes source declarations, not handler selection or observed fault handling.
Existing `json`/`export` provide source evidence; the offline
[BehaviorReport](#bpm-configured-behavior) embeds this separate report at `faults`.

[BpmFaultReport](../po-xray-core/src/main/java/io/poxray/model/BpmFaultReport.java) has
`formatVersion=1`, `analysisVersion="bpm-faults-v1"`. New records serialize null fields and empty
collections explicitly. Collection snapshots are deeply immutable, preserve null entries and
encounter order, and reject null collection arguments; analysis supplies non-null lists. Reused
`Issue`/`Issue.Location` keep their existing omission behavior. Fields below are in record/JSON order.
Input, ProcessKey, Flag, Fact and Condition reuse BpmPathReport types; Element, Link, Endpoint and
Locator reuse BpmSourceEvidence types.

| Record | Fields and meaning |
|---|---|
| `BpmFaultReport` | `formatVersion`, `analysisVersion`; `inputs`: original Input rows; `processes`: ProcessFaults rows; `unattributed`: component-qualified retained evidence; `issues`: source issues, then path issues, then fault diagnostics. |
| `ProcessFaults` | `key`: ProcessKey; `dc`, `id`, `name`, `source`: provenance; `coverage`: `complete`, `incomplete` or `unavailable`; `declarations`: contextual rows; `reasons`: coverage tokens. |
| `Declaration` | `ordinal`: row within the process; `role`: source context below; `owner`, `event`, `definition`: original Endpoints or explicit null; `scopes`: per-owner Scope rows; `definitions`: included candidate Definition rows; `builtin`: permitted catalogue fallback or null; `conditions`: retained Conditions; `evidence`: full contextual Evidence; `state`: `declared`, `non-error`, `catalogue` or `unknown`; `reasons`. |
| `Scope` | `owner`: candidate owner Locator; `state`: `active`, `inactive` or `unknown`; `origin`: `explicit-membership`, `attachment-derived`, `mixed` or `unknown`; `path`: immediate enclosing scope through ancestors to pool, inner first; `basis`: original Facts; `reasons`. |
| `Definition` | `source`: actual candidate Element; `kind`: `error`, `message` or `unknown`; `critical`, `technical`: raw-presence Flags from `isCritical`/`isTechnical`; `faults`: FaultReference rows; `evidence`; `reasons`. |
| `FaultReference` | `ordinal`: row within its Definition; `link`: original ErrorEventDefinitionHasWSDLFault Link; `state`: `included`, `identity-only`, `missing`, `ambiguous`, `wrong-kind` or `unknown`; `evidence`; `reasons`. |
| `Builtin` | `id`, `kind`, `name`: catalogue identity/metadata; `critical`, `technical`: catalogue booleans; `parameter`: Parameter or null; `file`, `sourceDc`, `resource`: catalogue provenance. |
| `Parameter` | `name`, `namespace`, `type`: catalogue parameter metadata, not an inferred source schema. |
| `Evidence` | `path`: contextual Fact walk; `elements`, `links`: full retained raw payloads, once per occurrence Locator in source order. |
| `Unattributed` | `input`, `component`: original list ordinals; `dc`; `kind`: retained context role; `evidence`: actual source occurrences; `reasons`. No process key is invented. |

Input/component/process ordinals are one-based original-list positions, including gaps for skipped
null internal component/process entries. Qualify source Locators by ProcessKey, or input/component
for Unattributed; element/link Fact kinds have independent ordinal spaces. Names and bare IDs do
not join contexts. Declarations follow primary source-link order, with boundary attachments expanded
by matching trigger-link order; local orphan elements and remaining links follow afterward. Definition
candidates and raw payload lists follow source element order. Declaration ordinals start at one per
process, fault-reference ordinals at one per Definition. Shared definitions under different owners
and repeated source links remain separate contexts. Repeated analysis is deterministic.

### BPM fault attribution and declaration states

[BpmFaultContext](../po-xray-core/src/main/java/io/poxray/bpm/BpmFaultContext.java) admits the same
format-1/`bpm-source-v1` evidence and exact process-source match as path analysis. Unavailable or
unsupported evidence, or an unresolvable process source, gives unavailable coverage without invented
declarations. An incomplete path graph also makes fault coverage incomplete.

Validated scope membership establishes positive process attribution, including inactive-pool
contexts with explicit reasons. It does not establish active handling. Ambiguous owners touching
several processes retain uncertain contexts in each. Positive ownership overrides physical location;
a partition belonging to exactly one interpreted process can retain otherwise unattributed local
facts as unknown, not as proved ownership. Shared/uninterpreted metadata remains in Unattributed
with `BPM_FAULT_UNATTRIBUTED_SOURCE`; malformed/unattributed ownership adds component-wide incomplete
uncertainty. A merely unreferenced external definition is retained as `unassigned-definition` and
does not by itself make a proved local declaration incomplete.

Scope rows stay separate for each candidate owner occurrence. Unknown ancestry has an empty path
and reasons, while raw candidate memberships remain in basis/Evidence. Basis retains relevant
ScopeContainsScopeObjects and CollaborationContainsPools links, ancestor Elements and boundary
attachment links needed for derivation. Origin describes that explicit/derived basis. Declaration
evidence contains its full raw payload because the path report is not embedded. Boundary-trigger
scopes describe the activity owner; no boundary scope is invented. No owner yields empty scopes
and `unknown-owner`; multiple candidates are never concatenated into one apparent scope path.

[BpmFaultDeclarations](../po-xray-core/src/main/java/io/poxray/bpm/BpmFaultDeclarations.java) interprets
the following source grammar; the association names here have the `Galaxy.Workflow.` prefix:

| Source chain | Role and endpoints |
|---|---|
| ActivityContainsEventDefinitions: `f=definition,s=activity` | `activity-definition`; owner is the activity, event is null. |
| ActivityHasBoundaryEvent: `f=boundary,s=activity`, then CatchEventHasTrigger: `f=definition,s=boundary` | `boundary-trigger`; owner is the activity and event is the boundary. |
| CatchEventHasTrigger on StartEvent | `start-trigger`; owner is the start event, event is null. Other catch owners use `unsupported-catch-trigger` with unknown state. |
| ThrowEventHasResult: `f=definition,s=EndEvent` | `end-result`; owner is the end event, event is null. An error end establishes no reply or handler. |

Supported activity owner kinds are AutomatedActivity, MappingActivity, NotificationActivity and
EmbeddedScope. This validates declaration shape only. Declared chains require unique correctly
typed endpoints and valid owner ancestry; boundary chains also require a unique BoundaryEvent
attachment. Each start/boundary catch/end allows one declaration link; multiple links stay separate
unknown rows. An activity may declare several distinct definitions. Identical duplicate ownership
links remain visible with `duplicate-declaration`.

Missing, ambiguous, wrong-kind or conflicting ownership/attachment/definition facts remain unknown
even when a candidate is a known message or catalogue trigger. A boundary attachment without a
usable trigger retains a row with null definition and `missing-trigger`; an unattached boundary
trigger is unsupported with `orphan-boundary-trigger`. Every included definition candidate retains
its own Definition row and flags; there is no representative value across ambiguous candidates.
Only exact source ErrorEventDefinition and MessageEventDefinition kinds are interpreted as error
and message. Timer-looking and other source kinds stay unknown. Otherwise valid error chains are
`declared`, valid message chains are `non-error`; neither state proves runtime handling.

Critical/technical Flags preserve actual exported attribute presence and exact raw text. Only exact
`true`/`false` tokens have those interpretations; missing/invalid values remain unknown. Missing
flags are not false and do not alone mean a source association is missing.

Builtin fallback requires a definition Endpoint with `resolution=missing`, zero source candidates
and an exact trigger ID in the shipped BuiltinFunctionCatalog. Any source candidate, including an
excluded or ambiguous one, prevents fallback; names and projected Node fields are not authority.
The original missing endpoint and source diagnostics remain. Builtin flags/kind, including a
catalogue timer kind, are catalogue facts, not exported attribute presence or interpreted source
kind. `resource`, `file` and `sourceDc` retain provenance. Only an otherwise valid chain has state
`catalogue`; malformed chains remain unknown while preserving catalogue metadata. Substitution
adds `catalogue-source-substitution` and makes coverage incomplete.

### BPM fault payload and unconsumed evidence

ErrorEventDefinitionHasWSDLFault uses `f=fault,s=definition`. A unique included WSDL2.Fault candidate
has state `included`; a unique excluded WSDL2.Fault candidate is `identity-only`, preserving its
identity without a complete payload contract. Missing, ambiguous and wrong-kind candidates remain
distinct, with all original endpoint evidence. No association means none declared in retained
evidence, not that the activity cannot fail or its WSDL has no faults.

Available direct `WSDL2.ReferenceToElement` links (`f=fault,s=XSD element`) and their selected
payloads are retained without recursive WSDL/XSD traversal, schema resolution or an inferred QName.
EventDefinitionHasParameter links and direct included parameter payloads are retained likewise.
Excluded payloads and malformed direct references remain gaps; identity-only evidence is not missing.

[BpmFaultEvidence](../po-xray-core/src/main/java/io/poxray/bpm/BpmFaultEvidence.java) creates a Condition
for each CatchEventContainsExpression link (`f=event,s=expression`) in attributable declaration
contexts. Unattributed links retain raw Evidence without Condition records. Unique valid expression
associations are `not-evaluated`; missing/ambiguous/wrong-kind associations are `unknown`. Text is
the raw `stringExpression` attribute, otherwise its same-named text. Empty conditions means none
declared, not true. Even literal false is not evaluated or used for pruning.

Declaration Evidence retains condition links, expression and reachable syntax payloads so the
Condition Fact locators are recoverable without the original inventory. The walk follows declared
child directions through expression steps, references, literal types, invocation identities and
parameter mappings, mapping parts/QNames/expressions, and condition branches. It stops at
QName/type/feature/function-interface payloads; it does not read function bodies or external
resources. Cycles terminate by occurrence without the legacy depth-64 cutoff. Unknown incident
rule/mapping links remain raw evidence with unsupported-relation uncertainty and no invented
traversal direction. Missing/excluded payloads stay explicit, and independent branches remain.

Unconsumed facts remain visible after normal chains:

- Local orphan event-definition Elements yield `orphan-definition` rows with null owner/event/
  definition Endpoints, unknown state, and the actual Element in definitions and Evidence.path.
  Duplicate IDs remain separate occurrences. The Workflow `EventDefinition` suffix is only a
  retention filter; it does not establish kind. External unowned definitions remain Unattributed.
- Unconsumed catch-expression links yield unknown `catch-condition-only` rows with their original
  owner endpoint, null event/definition and `condition-without-declaration`.
- Unconsumed fault/parameter links yield unknown `orphan-fault-reference` or
  `orphan-parameter-reference` rows when attributable to one process. Owner/event are null;
  definition is the original second endpoint, with candidate payloads even when wrong-kind.
- Unknown Workflow links incident on a retained definition enter every corresponding contextual
  Definition/Declaration evidence with `unsupported-workflow-relation`, unknown state and incomplete
  coverage. Without a contextual row they become `orphan-metadata` with null endpoints, or
  Unattributed when no process can be selected. Original links are never repaired by name.

Shared/ambiguous/uninterpreted unconsumed metadata keeps qualified Unattributed evidence and
component-wide uncertainty. Fault diagnostics use `BPM_FAULT_UNCERTAIN` or
`BPM_FAULT_UNATTRIBUTED_SOURCE`; source/path issues are preserved. Coverage `complete` means the
supported attributable retained extraction has no graph/ownership/payload gaps; `incomplete`
preserves known rows with reasons. Missing flag interpretations, absent optional fault links and
fully retained unevaluated conditions are explicit semantic limits, not invented missing associations.

Markdown summarizes input provenance, declaration roles/endpoints/states, per-owner scopes, raw
flags, fault-reference status, catalogue substitution, conditions, unattributed contexts and
diagnostics; cells are escaped. Full raw evidence remains in the report. All source-selection and
parser-loss limits apply. Neither complete extraction, matching IDs/payloads, lexical scopes nor
boundary arcs establish handler priority, interruption, propagation, retries, transactions, loop
instance, termination, synchronous response, acknowledgement or observed rejection.

### Process

One process = one `src/bpmn/*.bpmn` partition (Collaboration → Pool).

```jsonc
{
  "id": "E0…",                                 // Collaboration MOF ID
  "name": "digisign_envelope",                 // Collaboration name
  "documentation": "…",
  "pool": { "id": "…", "name": "digisign_envelope_orchestration",
            "identifier": "eon.com/bpm/digisign/digisign_envelope/digisign_envelope_orchestration",
            "subject": "{0}", "description": "Processing envelope id {0}" },
  "source": "SOURCEARCHIVES/eon.com~bpm~digisign~.dcsa!/src/bpmn/digisign_envelope.bpmn",
  "lanes": [ { "id": "…", "name": "…", "role": { "ref": "…", "name": "Administrators" } } ],
  "roles": [ { "id": "…", "name": "…", "kind": "process|lane", "dynamic": false, "assignment": Expression } ],
  "scopes": [                                  // the pool itself + embedded sub-processes
    { "id": "<pool id>", "kind": "process", "name": "digisign_envelope" },
    { "id": "…", "kind": "embeddedSubprocess", "name": "…", "parent": "<pool id>" }
  ],
  "dataObjects": [ /* DataObject */ ],
  "nodes": [ /* Node */ ],
  "flows": [ /* Flow */ ],
  "annotations": [ { "id": "…", "text": "…", "attachedTo": [ { "ref": "…", "name": "Send Envelope" } ] } ],
  "editorArtifacts": {
    "contractedJoins": [ { "id": "…", "name": "Artificial Uncontrolled Join (…)" } ]
  }
}
```

### DataObject

```jsonc
{
  "id": "…", "name": "DO_CreateEnvelopeInput", "kind": "dataObject|textVariable",
  "scope": { "ref": "<scope id>", "name": "…" }, "documentation": "…",
  "type": { "ref": "<XsdElementDeclaration id>", "name": "EON_MT_CreateEnvelopeInput", "namespace": "urn:…" },
  "initialValue": Mapping                      // only when an init mapping exists (ViewContainsMapping)
}
```

### Node

Common fields + kind-specific fields. `kind` values for phase 1 (seen in DigiSign):
`startEvent`, `endEvent`, `boundaryEvent`, `automatedActivity`, `mappingActivity`,
`notificationActivity` (Galaxy.ProcessSupplements.NotificationActivity, seen in PPAS_OTE),
`exclusiveGateway` (ExclusiveDataSplitGateway), `joinGateway` (UncontrolledJoinGateway, non-artificial),
`embeddedSubprocess` (EmbeddedScope).
Not yet seen → added when samples arrive: `humanActivity`, `parallelGateway`, `intermediateEvent` (timer/message), `subProcessCall`.
Unknown metaclass → `kind: "unknown"` + `metaclass` + `attributes` + issue `UNKNOWN_NODE_TYPE`.

```jsonc
// common
{ "id": "…", "kind": "automatedActivity", "name": "Send Envelope", "documentation": "…",
  "scope": { "ref": "<scope id>", "name": "…" }, "lane": { "ref": "<lane id>", "name": "Default" } }

// automatedActivity
{
  "operation": { "ref": "<WSDL2.Operation id>", "name": "EON_OI_SendEnvelope_BPM",
                 "interface": { "ref": "…", "name": "EON_OI_SendEnvelope_BPM", "namespace": "urn:eon.cz/po/digisign/bpm" } },
  "serviceReference": { "ref": "…", "name": "EON_OI_SendEnvelope_BPM" },
  "inputMapping":  Mapping,
  "outputMapping": Mapping,
  "errorEvents": [ { "ref": "<ErrorEventDefinition id>", "name": "EON_MT_Fault",
                     "fault": { "ref": "<WSDL2.Fault id>", "name": "EON_MT_Fault" } } ],
  "boundaryEvents": [ { "ref": "…", "name": "…" } ]
}

// mappingActivity
{ "inputMapping": Mapping, "outputMapping": Mapping }

// notificationActivity (e-mail); operation = generated per-activity NotificationInterface
{ "subject": "…({0})!", "body": "…", "operation": { … }, "inputMapping": Mapping, "outputMapping": Mapping }

// startEvent
{ "trigger": { "kind": "message", "eventDefinition": { "ref": "…", "name": "CreateEnvelopeInputMessage" } },
  "condition": Expression, "outputMapping": Mapping }       // trivial condition "true" omitted

// endEvent
{ "terminate": true, "throws": { "ref": "<eventdef id>", "name": "DigiSignApplicationEscalation" },
  "inputMapping": Mapping }

// boundaryEvent
{ "attachedTo": { "ref": "…", "name": "Send Envelope" },
  "trigger": { "kind": "error", "eventDefinition": { "ref": "…", "name": "EON_MT_Fault" } },
  // standard SAP trigger (catalog `triggers`, not in the DC):
  //   { "kind": "error", "eventDefinition": { "ref": "…", "name": "TechnicalError" }, "builtin": "TechnicalError" }
  //   its semantics (documentation, isCritical, isTechnical, parameter) is in catalog.builtinEventDefinitions
  "condition": Expression, "outputMapping": Mapping }

// exclusiveGateway: conditions live on outgoing flows; nothing extra
// embeddedSubprocess
{ "scopeId": "<scope id>", "boundaryEvents": [ … ], "inputMapping": Mapping }
```

### Flow

```jsonc
{
  "id": "<connector MOF id>",
  "source": { "ref": "<node id>", "name": "Signer only?" },
  "target": { "ref": "<node id>", "name": "Download audit log" },
  "name": "Yes",
  "condition": Expression,                     // ConditionalSequenceConnector only
  "isDefault": true,
  "via": [ { "ref": "<artificial join id>", "name": "Artificial Uncontrolled Join (…)" } ]   // only if contracted
}
```

### Expression

The Galaxy source stores both the expression tree (`Rule.ExpressionHasSteps` …) **and**, on top-level
expressions, the original editor text in attribute `stringExpression` (finding of this session, not in HANDOFF).

```jsonc
{
  "text": "if(isSet(DO_CreateEnvelopeInput/Envelope/extId)) then DO_CreateEnvelopeInput/Envelope/extId else DO_CreateEnvelopeInput/Request/requestId",
  "reads": [ "DO_CreateEnvelopeInput/Envelope/extId", "DO_CreateEnvelopeInput/Request/requestId" ],  // data paths, from the tree
  "functions": [                               // every invocation, pre-order (= text order)
    { "id": "492D235E1CAE0460BBA411DDA2350019991C7C01", "name": "isSet", "category": "generic", "source": "builtin",
      "arguments": [                           // parameter mapping parts, ordered by the formal parameters
        { "parameter": "arg", "text": "DO_CreateEnvelopeInput/Envelope/extId",
          "category": "merge", "expander": "currentNode", "transformer": "getValue" } ] }
  ]                                            // source: builtin | dc-function | unknown (id kept, name null)
}
```
- `text` = `stringExpression`; if missing or empty, rendered from the tree (and `"rendered": true`).
- Editor-generated expressions (`#generated=true#<text>`) → prefix stripped, `"generated": true`.
- Rendering from the tree: arguments ordered by the formal parameters (catalog / `.function`), functional notation
  (`numeric-less-than(a, b)`); when the editor text is known, an operator uses its catalog symbol (`a < b`) if the
  editor text does (the catalog's `infix` flag is false for all functions, so notation follows the editor text).
- A Reference without QName right after an invocation is the implicit function result (not rendered).
- The tree is always rendered as well; if it differs from `stringExpression` (ignoring whitespace and namespace
  prefixes like `dm0:`) → issue `EXPRESSION_TEXT_MISMATCH` (info).
- `reads` / `functions` enable data lineage ("where is DO_X used") without parsing text.
- The full step tree is **not** emitted in v0.1 (available in the raw partition); can be added if needed.

### Mapping

Hierarchical MappingParts are flattened into target paths.

```jsonc
{
  "id": "…", "name": "Send Envelope_InputMapping",
  "assignments": [                             // target = full path incl. root (message element / data object)
    { "target": "EON_MT_SendEnvelope", "category": "merge", "expander": "currentNode", "transformer": "discardValue" },
    { "target": "EON_MT_SendEnvelope/Request/oauth_token",
      "category": "set", "expander": "currentNode", "transformer": "getValue",
      "expr": { "text": "DO_CreateEnvelopeInput/Request/oauth_token", "reads": [ "…" ] } },
    { "target": "EON_MT_SendEnvelope/Envelope", "category": "set", "expander": "moveCorresponding",
      "transformer": "getValue", "expr": { "text": "DO_Envelope", … } },
    { "target": "EON_MT_SendEnvelope/Request/note", "category": "set", "expander": "currentNode",
      "transformer": "getValue", "enabled": false, "expr": { … } }
  ]
}
```
- Every MappingPart is emitted, in tree order (parent before children). Covers input/output mappings of
  activities, events, embedded scopes, mapping and notification activities, role/text variable views; function
  parameter mapping parts are in `expr.functions[].arguments`.
- `category` (set | merge | append), `expander` (currentNode | moveCorresponding) and `transformer`
  (getValue | discardValue) are always present. `moveCorresponding` = fields with equal names are copied
  automatically; documentation must show it explicitly.
- `enabled` only when `false`; `expr` absent for structural parts without an expression.

### Catalog objects

```jsonc
// BuiltinFunction (from the NW BPM built-in function catalog; only those called by the DC's processes)
{ "id": "481F3B2E935578A21AC311DDCA830018717335EB", "name": "numeric-less-than", "category": "finite-integer",
  "symbol": "<", "parameters": [ { "name": "left", "type": "xsd:long" }, { "name": "right", "type": "xsd:long" } ],
  "result": "xsd:boolean", "documentation": "Returns true if and only if left is less than right. …",
  "javaClass": "com.sap.glx.mapping.execution.implementation.function.builtin.finiteinteger.…" }
```

```jsonc
// Interface (rep/wsdl/*.services)
{ "id": "…", "name": "EON_OI_SendEnvelope_BPM", "namespace": "urn:eon.cz/po/digisign/bpm",
  "source": "…!/rep/wsdl/eon.cz.po.digisign.bpm.services",
  "operations": [ { "id": "…", "name": "EON_OI_SendEnvelope_BPM", "pattern": "in-out",
                    "input":  { "name": "EON_MT_SendEnvelope", "namespace": "…" },
                    "output": { "name": "…", "namespace": "…" },
                    "faults": [ { "id": "…", "name": "EON_MT_Fault", "element": { "name": "…", "namespace": "…" } } ] } ],
  "usedBy": [ { "process": "…", "node": { "ref": "…", "name": "Send Envelope" }, "role": "outbound|start|notification" } ] }

// ServiceReference (mc/META-INF/servicereferences.servicerefs)
{ "id": "…", "uuid": "0ba7e7fb-…", "type": "WS", "bindingType": "XI",
  "interface": { "name": "EON_OI_Dms_DocumentGet_BPM", "namespace": "urn:eon.cz/po/digisign/bpm" },
  "authenticationProfile": "Technical User",
  "logicalSystem": "sappo_out",                // mc logical system (= service group name)
  "serviceGroup": "sappo_out",                 // deployed .composite; its config is runtime (NWA), not in SCA
  "attributes": { "senderPartyName": "", "senderServiceName": "EON_BPM_DIGISIGN" } }

// Function (src/functions/**/*.function)
{ "id": "…", "name": "getRecipientIdIndex", "namespace": "eon.com/bpm/digisign", "kind": "ejb",
  "lookupName": "eon.com/bpm~digisignfunctionsea/LOCAL/GetRecipientIdIndex/com.eon.digisign.bpm.functions.GetRecipientIdIndexLocal",
  "ejb": { "application": "eon.com/bpm~digisignfunctionsea", "bean": "GetRecipientIdIndex",
           "interface": "com.eon.digisign.bpm.functions.GetRecipientIdIndexLocal" },   // parsed from lookupName; links to ejb[].beans
  "parameters": [ { "name": "recipientId", "type": "xsd:string" }, { "name": "recipientIds", "type": "…" } ],
  "result": { "name": "result", "type": "…" },
  "usedBy": [ { "process": "digisign_envelope_download_completed",       // every call in the DC's processes
                "element": { "ref": "…", "name": "Signer only? -> Download audit log? (Yes)" },
                "elementKind": "flow",                                    // step kind | flow | role | dataObject
                "part": "condition",                                      // inputMapping | outputMapping | condition | assignment | initialValue
                "target": null,                                           // mapping target path (null for conditions)
                "expression": "or(boolean-equal(DO_SignerOnly,false), …)" } ] }

// EventDefinition (src/triggers/*.eventdef)
{ "id": "…", "name": "DigiSignTechnicalEscacaltion", "kind": "error|message",
  "documentation": "DigiSign - technická chyba", "isCritical": true, "isTechnical": false,
  "parameter": { "name": "parameters", "namespace": "eon.com/bpm/digisign/DigiSignTechnicalEscacaltion" },
  "interface": { "ref": "…" } }                // message triggers: the inbound interface/operation

// DataType (XSD element declarations used by data objects / messages)
{ "id": "…", "name": "EON_MT_CreateEnvelopeInput", "namespace": "…", "type": "<named type, if any>",
  "fields": [ { "path": "Request/orgId", "type": "xsd:string", "min": 1, "max": 1 },
              { "path": "Recipients/recipient", "type": "…", "min": 0, "max": "unbounded" } ] }
```

## EJB inventory

One `EjbModule` per EJB module DC (`.dcdef` sub-type `EJBModule`). Sources come from the DC source archive;
the sources deployed in the EAR (`src.zip` → `<vendor>~<name>~ejbjar_srcarchive.zip`) are compared with them
(`DEPLOYED_SOURCE_DIFFERS`) and used as fallback when the source archive has no Java files.

```jsonc
{
  "dc": "eon.com/applications/digisignvalidationeb",
  "ear": "eon.com/applications/digisignvalidationea",           // EAR DC whose .dcdef depends on this module
  "application": "eon.com/applications~digisignvalidationea",   // deployed name, as in BPM function lookup names
  "beans": [
    {
      "name": "ValidateCreateEnvelopeInput",                    // bean annotation name, else simple class name
      "class": "com.eon.digisign.po.ValidateCreateEnvelopeInput",
      "type": "stateless",                     // stateless | stateful | singleton | messageDriven
      "jndiName": "ValidateCreateEnvelopeInput",                // META-INF/ejb-j2ee-engine.xml
      "interfaces": { "local": [ "com.sap.aii.af.lib.mp.module.ModuleLocal" ], "remote": [ "…ModuleRemote" ] },
      "roles": [ "adapterModule" ],            // bpmFunction | adapterModule | xiProxyProvider | webService | schedulerJob | other
      "technologies": [ "xiMessaging", "adapterFramework" ],    // from imports (jco, jpa, jdbc, jms, jaxws, http, mvel, …)
      "moduleParameters": [ "showAudit", "faultInterface", "faultNamespace", "faultService", "senderId" ],
      "webService": { "serviceName": "…", "portName": "…", "endpointInterface": "…", "targetNamespace": "…", "wsdlLocation": "…" },
      "job": { "name": "ZideOteMsgRefDeleteOldJob", "description": "…" },       // schedulerJob (job-definition.xml)
      "activationConfig": { "messageSelector": "JobDefinition='…' AND …" },     // messageDriven
      "properties": { "destination-name": "JobQueue" },                         // bean-props (ejb-j2ee-engine.xml)
      "rfcFunctions": [ "Z_ISU_INT_UI_DETERMINE_RFC" ],                          // jco: getFunction("…")
      "jcoDestinations": [ "SAPISU" ],                                          // jco: getDestination(…), constants resolved
      "injects": [ "ZideOteRoutingBean" ],                                      // @EJB fields resolved to bean names
      "persistenceUnit": "zide_ide_ote_routing",                                // @PersistenceContext
      "usedBy": [ { "kind": "bpmFunction", "dc": "eon.com/bpm/digisign",
                    "function": { "ref": "…", "name": "getRecipientIdIndex" } } ],
      "source": { "path": "SOURCEARCHIVES/…dcsa!/ejbModule/com/eon/digisign/po/ValidateCreateEnvelopeInput.java", "sha256": "…" }
    }
  ],
  "classes": [ { "class": "com.eon.digisign.po.ValiadtionMessage", "kind": "class", "source": { … } } ],  // non-bean types
  "jpaEntities": [ { "class": "cz.ppas.ote.sappo.db.ZideOteRouting", "table": "ZIDE_OTE_ROUTING" } ],
  "persistenceUnits": [ { "name": "zide_ide_ote_routing", "dataSource": "PODB", "classes": [ "…" ] } ],
  "jobDefinitions": [ { "name": "ZideOteMsgRefDeleteOldJob", "description": "…" } ],
  "technologies": [ "jpa", "jco", "scheduler" ]   // union over the module
}
```
Classification (lightweight source parsing, comments stripped, no compilation):
- `bpmFunction` — has `invokeSdo(...)` or is referenced by a `.function` lookup name.
- `adapterModule` — implements `com.sap.aii.af.lib.mp.module.Module`; parameters = `getContextData("…")` arguments.
- `xiProxyProvider` — `@WebService` + `@XIEnabled` (plain `@WebService` → `webService`).
- `schedulerJob` — extends `MDBJobImplementation`; job matched via `JobDefinition='…'` in the message selector.
- Business interfaces: `@Local`/`@Remote` on the bean or on the implemented interface; a single unannotated
  interface is local (EJB 3 default).
- BPM link: function `lookupName` `<application>/LOCAL|REMOTE/<bean>/<interface>` ↔ (`application`, bean `name`).

## Issues

```jsonc
{
  "severity": "error|warning|info",
  "code": "SUSPICIOUS_MAPPING",
  "message": "identificationNumber and function are mapped from the same source expression",
  "location": { "dc": "eon.com/bpm/digisign", "process": "digisign_envelope",
                "element": { "ref": "…", "name": "Create envelope recipient" }, "path": "identificationNumber" },
  "source": "…!/src/bpmn/digisign_envelope.bpmn"
}
```
Codes in v0.1 (automatically detected):
| Code | Meaning |
|---|---|
| `UNRESOLVED_REF` | reference to an ID not found in DC nor in static built-in tables |
| `UNKNOWN_BUILTIN_FUNCTION` | function ID neither in the DC nor in the built-in function catalog |
| `UNKNOWN_NODE_TYPE` / `UNKNOWN_ELEMENT_TYPE` | metaclass not handled → also in `unmapped[]` |
| `EXPRESSION_TEXT_MISMATCH` | `stringExpression` differs from the rendered tree |
| `UNEXPECTED_STRUCTURE` | graph shape the reader does not expect (e.g. artificial join with ≠ 1 outgoing flow) |
| `DUPLICATE_ID` | MOF ID declared in more than one partition |
| `BPM_SOURCE_REFERENCE_UNRESOLVED` | selected source link endpoint has no indexed element candidate; includes link occurrence and `f`/`s` side |
| `BPM_SOURCE_REFERENCE_AMBIGUOUS` | selected source link endpoint has multiple indexed element candidates; no target chosen |
| `BPM_SOURCE_PROPERTY_KEY_MISSING` | selected element has a null parsed property key; entry retained with occurrence and property index |
| `SUSPICIOUS_MAPPING` | heuristic: same source expression assigned to two sibling targets in one mapping |
| `UNUSED_OBJECT` | interface, service reference, function, eventdef or data object not used by any process (SAP system data objects excluded) |
| `MISSING_SOURCE` | `.function` lookupName points to an EJB not found in the SCA |
| `EJB_INTERFACE_MISMATCH` | function looks up an interface the bean does not declare as business interface |
| `DEPLOYED_SOURCE_DIFFERS` | Java sources deployed in the EAR differ from the DC source archive |
| `SOURCE_FROM_DEPLOYMENT` | DC source archive has no Java sources, EAR sources used (info) |
| `UNSUPPORTED_DESCRIPTOR` | `ejb-jar.xml` present but not interpreted (EJB 2.x beans would be missing) |

Manually confirmed findings (typos, logic bugs in Java) are **not** auto-detected in v0.1; they belong to the
documentation, optionally via a `knownIssues` input file later.

## Not in v0.1 (deliberately)

- `.diagram` layout (positions), `.xlf` translations (only `originalText` is emitted).
- BRM rulesets (`src/brms`), Dictionary (`*.dtdbtable`), UME (`actions.xml`) — listed in `developmentComponents`, content later.
- Full expression step tree, full WSDL/XSD content (only what processes reference).
- Compiled `.bpem` csv (used only as secondary source for built-in function IDs).

## Integration inventory (ESR / Integration Directory)

`IntegrationInventory` — result of `po-xray ico`: ICOs to/from a component, their channels and the ESR objects they
use. Read online (SimpleQuery + `read/ext`, snapshot cache) or from TPZ exports; same `xiObj` parser for both.

```jsonc
{
  "schemaVersion": "0.1", "generator": { … },
  "source": { "system": "xipod", "url": "http://…:54200" },          // or "files": [ "x.tpz" ]
  "query": { "component": "V3S_D", "direction": "both" },             // in | out | both
  "components": [ { "name": "V3S_D" } ],                             // components that matched
  "icos": [ {
    "oid": "…", "generatedFrom": "SAPCRM_ApplicationStatus",          // iFlow the ICO was generated from
    "sender": { "component": "V3S_D",
                "iface": { "type": "ifmmessif", "name": "Application_Out", "namespace": "urn:…", "swc": "PPAS_V3S, 1.0 of ppas.cz", "swcGuid": "…" },
                "operations": [ "ChangeRequest", "CreateRequest" ],
                "channel": { "component": "V3S_D", "name": "REST_SND_Application" } },
    "receivers": [ { "component": "V3S_D", "qualityOfService": "EO",
      "condition": { "text": "context:XHeaderName1 (http://sap.com/xi/XI/System/SOAP) = \"301\" OR (/Order/Type like \"Z*\" AND exists /Order/Id)",
                     "lines": [ { "group": 1, "left": "context:XHeaderName1 (…)", "operator": "EQ", "right": "\"301\"" }, … ] },  // null = unconditional
      "interfaces": [ { "operation": "ChangeRequest", "condition": null,
                        "mapping": { "type": "MAPPING", "name": "Application_ChangeRequest_to_…" },
                        "targets": [ { "iface": { "name": "ApplicationConfirmation_In" }, "multiplicity": "1" },
                                     { "iface": { "name": "Application_In" }, "multiplicity": "0..1" } ],   // 1:n mapping
                        "parameters": { "RFCCHANNEL": { "component": "CD1CLNT300", "name": "RFC_RCV" } } } ],
      "channels": [ { "component": "V3S_D", "name": "SOAP_RCV_LoopBack" } ] } ],
    "dynamicRouting": { "operationSpecific": false,                 // extended receiver determination (link RoutingProgram)
                        "mappings": [ { "type": "MAPPING", "name": "Response_to_ReceiverDetermination" } ] },
    "noReceiverBehaviour": "error" } ],
  "classicConfiguration": [ {                  // receiver / interface determinations, agreements (no ICO)
      "id": { "type": "RoutingRelation", "area": "dir",
              "key": { "FROMPARTY": "", "FROMSRVC": "SND", "FROMACTIONNS": "urn:a", "FROMACTION": "Order_Out", "TOPARTY": "", "TOSRVC": "" },
              "oid": "…", "versionId": "…", "modifiedAt": "…", "modifiedBy": "…" },
      "links": [ { "role": "…", "type": "Service", "key": "|RCV" } ],    // mappings, interfaces, channels are expanded below
      "content": "<…raw XML, not interpreted yet…>" } ],
  "channels": [ { "id": { … }, "direction": "sender|receiver", "adapter": "HTTP/REST",
                  "attributes": { "in.channel.endpoint": "/v3s/spds/application", … },   // passwords are never exported
                  "modules": [ { "position": 0, "name": "AF_Modules/DynamicConfigurationBean", "type": "L", "parameters": { … } } ] } ],
  "esr": {
    "serviceInterfaces": [ { "id": { … }, "category": "I|O|A", "operations": [ { "name": "…", "mode": "sync|async", "request": { … }, "response": { … }, "faults": [ … ] } ] } ],
    "operationMappings": [ { "id": { … }, "sourceOperation": { … }, "targetOperation": { … },
                             "steps": [ { "kind": "request|response|fault", "program": { "type": "XI_TRAFO|MAP_ARCHIVE_PRG|…" }, "sourceMessage": { … }, "targetMessage": { … } } ] } ],
    "messageMappings": [ { "id": { … }, "source": { … }, "target": { … }, "multiplicity": "1:1",
                           "logic": {                                                    // null if unreadable
                             "namespaces": { "ns0": "urn:…" },
                             "fields": [ { "target": "/ns0:T/B",
                                           "expression": "iF(if: stringEquals(Utils.check(/ns1:S/B, channel=$rfc).valid, \"X\"), then: /ns1:S/B, else: \"\")",
                                           "sources": [ "/ns1:S/B" ] } ],
                             "disabledFields": [ "/ns0:T/F" ],
                             "parameters": [ { "name": "rfc", "type": "channel:RFC", "direction": "import", "mandatory": true } ],
                             "libraries": [ { "type": "FUNC_LIB", "name": "Utils", "namespace": "urn:u" } ],
                             "localFunctions": { …FunctionLibrary without id… } } } ],
    "messageTypes": [ { "id": { … }, "dataType": { … }, "xmlNamespace": "urn:…" } ],
    "dataTypes": [ { "id": { … }, "uses": [ { … } ], "xsd": "<xsd:schema …>" } ],
    "externalDefinitions": [ { "id": { … }, "messages": [ "invokeRequest", … ] } ],
    "mappingPrograms": [ { "type": "MAP_ARCHIVE_PRG", "name": "…" } ],         // Java / XSLT programs, imported archives (TRAFO_JAR)
    "functionLibraries": [ { "id": { … }, "imports": [ "import javax.xml.stream.XMLInputFactory;" ],   // beyond SAP defaults
                             "globals": "…java…", "init": null, "cleanup": null,
                             "functions": [ { "key": "54865ab5-…", "name": "validateApplicationXml", "title": "…", "description": "…",
                                              "execution": "singleValues|contextValues|queueValues",
                                              "arguments": [ { "name": "inputXml", "kind": "input|result|parameter", "javaType": "String" } ],
                                              "code": "…java…", "sapApis": [ "LookupService", "DynamicConfiguration" ] } ],
                             "archives": [ { "type": "TRAFO_JAR", "name": "Java_V3SXmlValidation", "namespace": "urn:…" } ],
                             "sapApis": [ "LookupService" ] } ],
    "importedArchives": [ { "id": { … }, "fileName": "ote-routing-mapping.jar", "maven": "cz.ppas:ote-routing-mapping:1.0.0",
                            "programs": [ { "path": "cz/…/OteRoutingMapping.class", "kind": "javaMapping|class|xslt|javaSource",
                                            "className": "cz.….OteRoutingMapping", "superclass": "…", "interfaces": [ … ],
                                            "mappingApi": "AbstractTransformation|StreamTransformation",
                                            "sapApis": [ "Trace", "MessageHeader" ], "text": "<xsl:stylesheet …> (xslt / javaSource only)" } ],
                            "packages": { "cz.ppas.ote.routing.mapping": 12 }, "otherFiles": [ … ],
                            "sapApis": [ "Trace", "MessageHeader", "JNDI" ] } ]
  },
  "issues": [ … ]   // UNRESOLVED_OBJECT, OBJECT_NOT_READABLE (with the server's reason)
}
```
Facts verified on xipod (PO 7.50 SP28):
- ICO links: `MAP<r>` (r = receiver index, kpos = interface determination), `IIF<r>$<n>` (target interfaces,
  kpos = split item; plain `IIF<r>` for single determinations), `PAR<r>_<n>_<param>` (channel-bound mapping
  parameters), several `senderOperation` links per ICO.
- Routing conditions: `AllInOne/Conditions/RDS_CONDSHORT` (lines `COMPGROUP`, `COMPOP` EQ/NE/CP/NP/EX/NX, extractors
  `XP` XPath, `CT` context object, `KO` constant). Lines of one group = AND, groups = OR. Receivers are bound via
  `ReceiverAssignmentList/ReceiverAssignment` (`ReceiverConditionId` + `ReceiverIds` → `ReceiverConfiguration/ReceiverId`);
  several conditional assignments of one receiver = OR, any assignment without condition = unconditional. Interface
  determinations carry `InterfaceConditionId` directly (always empty on xipod: 54 conditional receivers, 0 conditional
  interface determinations out of 259 ICOs).
- Message mapping logic: `XiTrafo/MetaData/blob` = `!zip!` + base64 of a zip whose entry is another zip with the
  mapping editor XML `<mappingtool>` (function libraries: `FunctionLibrary/MetaData` → `<functionstorage>`, same format
  as the mapping's local functions). Target fields are `transformation/brick type="Dst"` trees of `Src` / `Func`
  bricks; `fns="dflt"` = standard function, otherwise the library alias from `libstorage/entry` (library functions are
  referenced by GUID key, resolved through the `UsedFuncLib` link) or `usernamespace` (local). The standard `iF` has
  pins then (0), if (1), else (2); the expression shows them as `iF(if: …, then: …, else: …)`. UDF argument `tp`:
  0 input, 1 result, 2 parameter; `cacheType` 0/1/2 = single values / context / queue. Channel parameters
  (`typetype DCOMCHAN`) point to a link `p_<name>` → adapter metadata (e.g. RFC).
- NW BPM calling ESR operation mappings: the deployed composite has a component with `implementation.piom`, one
  `service` per operation mapping with `sapextensions omguid` (object ID in the ESR) / `svguid` (SWCV) and input /
  output types; the process calls it through a service reference to the generated port type. The service reference
  gets `operationMapping { name, namespace, oid, swcGuid, input, output }` (PPAS_OTE archive BPM: 3 mappings, OIDs
  identical to the ESR of xipod).
- Imported archives (`TRAFO_JAR`): `Archive/blob type="jar" zipped="false"` = the uploaded jar / zip itself, plus
  `Encoding` (of Java sources) and link `FILE_NAME`. Classes are read from the byte code (constant pool), not
  decompiled: Java mappings extend `AbstractTransformation` or implement `StreamTransformation` (also through classes of
  the same archive); SAP / platform API use comes from class names in the constant pool (LookupService,
  DynamicConfiguration, Trace, ValueMapping, MessageHeader, MappingParameters, Attachments, AdapterFramework, JDBC,
  JNDI, HTTP, FileSystem). Archives are read for operation mapping steps (`MAP_ARCHIVE_PRG`
  `archive|namespace|class or xsl|path`) and UDF class paths.
- Extended receiver determination: ICO content `DynamicalRouting isOpSpecific counter` + link `RoutingProgram`
  (kpos = counter) to the operation mapping computing the receivers (31 of 260 ICOs on xipod).
- Value mapping groups (`show -t valueMapping`): `Valuemapping groupName groupid` with `Representation`
  (`system`, `objectTypeName`, `value`). Secrets are stored there in plain text (a client secret on xipod):
  values whose name looks like a secret are masked, here and in every raw content returned.
- Operation mapping roles `*_OUT_MES` / `*_IN_MES` are not consistent for faults: source/target of a step come from
  the message mapping (`SOURCE_IFR_MESS` / `TARGET_IFR_MESS`).
- Object search (`search` / `search_objects`) returns `ObjectSummary` (type, area, key elements by name from the
  SimpleQuery `typeInfo`, SWCV, OID, version, last change, description); `show` / `read_object` return the dedicated
  record or `ObjectDetail` (summary, links by role, raw content XML); where-used matches `RA_LINK_LIST` of the
  referring types by object ID (links carry the SWCV of the referring object, targets may live in an underlying SWCV).
- SimpleQuery: case-insensitive matching is also refused for ESR `NAME` (types are listed and filtered locally);
  date conditions need `<val><simple><date>yyyy-MM-ddTHH:mm:ss</date>` (seconds mandatory, other formats → HTTP 500);
  `typeInfo/keyElem` names the key elements even when nothing matches; the result page is not UTF-8 (charset from
  `Content-Type`), `read/ext` is.
- SimpleQuery: case-insensitive matching is refused for `SERVICE`, `FROMSRVC`, `TOSRVC`, `MODIFYUSER`; `TOSRVC` does
  not find ICO receivers (they are in the ICO content) → components and ICOs are listed and filtered locally.
- The JDK HTTP client must use HTTP/1.1: the h2c upgrade request is answered with 403 by SAP AS Java.

### Contract source identities and XML resources

MIG-30 adds fields to `IntegrationInventory` without changing schema version `0.1`.
`ObjRef(type, name, namespace, swc, swcGuid, oid, program, key)` appends `List<String> key`,
the full ordered ESR reference key. Existing six/seven-argument constructors remain; absent/null keys
normalize to immutable empty lists. Record equality now includes the key. ESR identity and XML QName
are separate: an ESR namespace or inner external-message key does not prove an XML namespace/name.

`ExternalDefinition(id, messages, description, content, messageReferences, resources)` retains the
existing message names/order and adds serialized source content, full inner `ObjRef` references and
`ContractResource` entries. Its old three-argument constructor supplies null content and empty new
lists; absent/null new lists normalize empty. Direct definition OID identity and operation/mapping
endpoint keys are retained. These nested records have the following component order:

| Record | Components |
|---|---|
| `XmlName` | `String namespace, String localName` |
| `ContractDeclaration` | `String kind, XmlName name, String locator` |
| `ContractDependency` | `String kind, String namespace, String location, String locator` |
| `ContractResource` | `String sourcePath, String locator, String kind, String content, List<ContractDeclaration> declarations, List<ContractDependency> dependencies` |

Resource lists are immutable snapshots, null normalized empty. The public Java helper
[`ContractResources.read(String sourcePath, String xml)`](../po-xray-core/src/main/java/io/poxray/integration/ContractResources.java)
returns `List<ContractResource>`. Null/blank XML yields an empty list; malformed XML throws
`IllegalArgumentException` with cause. DTD/external entity access is disabled. No filesystem/network
resource retrieval or schema compilation occurs.

The reader walks actual namespace-qualified XML elements in document order: XSD `schema` in
`http://www.w3.org/2001/XMLSchema` and WSDL 1.1 `definitions` in `http://schemas.xmlsoap.org/wsdl/`.
Kinds are `xsd`/`wsdl`; nested schemas are separate resources. Locators are indexed element paths;
serialized subtrees preserve in-scope namespaces, including prefixes used only in QName values.
XSD declarations are immediate named `element`, `attribute`, `complexType`, `simpleType`, `group`,
`attributeGroup`; WSDL declarations are immediate named `message`, `portType`, `binding`, `service`.
QNames use the declaring resource's `targetNamespace`, or empty namespace when absent. Foreign namespace
lookalikes and local nested element declarations are not top-level declarations.

Immediate XSD `import`/`include`/`redefine` retain namespace/schemaLocation; WSDL `import` retains
namespace/location. Missing attributes remain null; duplicates and order remain. No import is resolved
by this parser. `sourcePath` is caller-supplied provenance. IntegrationReader uses null for retained
ExternalDefinition content: it does not invent archive filenames, decode arbitrary resource wrappers
or bind external messages to QNames. External XSDs never synthesize native MT/DT records; native
MessageType-to-DataType behavior remains unchanged.

### Contract catalog API

MIG-31 adds Java helpers only, with no contract CLI or MCP command:

- `ContractCatalog.of(IntegrationInventory inventory, BpmDataReport data, ContractCatalogInput supplied)`
  returns `ContractCatalogReport`.
- `ContractCatalog.markdown(ContractCatalogReport report)` returns `String`.

All three `of` inputs may be null; missing coverage becomes an issue, not a completeness assertion.
There is no arbitrary document parser. Callers provide retained inventory/P3 records and explicit claims.
The public model types are in `io.poxray.model`; the helper is in `io.poxray.migration`.
[Implementation](../po-xray-core/src/main/java/io/poxray/migration/ContractCatalog.java).

`ContractCatalogInput(List<ArtifactClaim> artifacts, List<ContractClaim> contracts,
List<ResourceBinding> resources)` contains these nested records, in component order:

| Record | Components |
|---|---|
| `Evidence` | `String kind, String source, String locator, String status` |
| `StepRef` | `BpmPathReport.ProcessKey process, int step` |
| `ArtifactClaim` | `String id, String kind, String owner, List<Evidence> evidence` |
| `PortClaim` | `String role, String participant, String operation, StepRef step, List<Evidence> evidence` |
| `ValueClaim` | `String path, String role, StepRef producer, List<StepRef> consumers, String defaultValue, List<Evidence> evidence` |
| `ContractClaim` | `String id, String artifact, String disposition, IntegrationInventory.ObjRef esr, IntegrationInventory.XmlName xmlName, List<PortClaim> ports, List<ValueClaim> values, List<Evidence> evidence` |
| `ResourceBinding` | `String id, String ownerArtifact, IntegrationInventory.ContractResource resource, List<Evidence> evidence` |

`ContractCatalogReport(int formatVersion, String analysisVersion, List<Artifact> artifacts,
List<Contract> contracts, List<Dependency> dependencies, BpmDataReport data, List<Issue> issues)`
uses format `1`, analysis version `contract-catalog-v1`. Its nested records are:

| Record | Components |
|---|---|
| `Artifact` | `String id, String kind, IntegrationInventory.ObjRef esr, String owner, String basis, List<ContractCatalogInput.Evidence> evidence` |
| `Contract` | `String id, String artifact, String disposition, IntegrationInventory.ObjRef esr, IntegrationInventory.XmlName xmlName, List<ContractCatalogInput.PortClaim> ports, List<ContractCatalogInput.ValueClaim> values, List<ContractCatalogInput.Evidence> evidence` |
| `Dependency` | `String artifact, String kind, IntegrationInventory.ObjRef esr, String location, String namespace, List<String> candidates, String state, List<ContractCatalogInput.Evidence> evidence` |

Input/report lists normalize null to empty and snapshot defensively. The retained P3 report is already
deeply immutable. New catalog records serialize empty lists and null unknown scalars explicitly;
embedded inventory records retain their own serialization rules. Input ordering is deterministic and
inputs are not mutated. Existing inventory/P3 issues are retained; catalog warnings use code
`contract-catalog-unknown`, indexed locators and source `contract-catalog`.

### Contract catalog IDs

Source artifact IDs use `source:<kind>:<original-category-index>` with zero-based indices; names do not
deduplicate objects. Source kinds are `native-message-type`, `native-data-type`, `external-definition`,
`esr-message-mapping`, `esr-function-library`. Contained XML resources append `:resource:<index>` and
have kind `xml-resource`; contained functions append `:function:<index>` and have kind `mapping-function`.
Their owners are enclosing artifact IDs; other source owners remain null. Source artifacts have
`basis=source` and evidence `kind=export, source=inventory, status=observed` with indexed inventory
locators. Here observed means retained input evidence, not observed runtime behavior.

Native MT contracts append `:contract`; DT/resource declarations append `:declaration:<index>`.
Native DT declarations belong directly to the DT, flattened in resource/declaration order, without
inventing an extra resource artifact. External messages append `:message:<index>`; legacy name-only
contracts append `:legacy-message:<index>`. Source dispositions remain `unknown`. Native MT QName
uses nonblank `xmlNamespace` and the declared name; absent namespace leaves it unknown. DT/resource
QNames require explicit declarations. External-message QNames remain unknown, including when a resource
has a unique root. Declaration contracts and external-message contracts are separate.

Supplied artifact/contract/resource domains use `claim:`, `claim-contract:` and `supplied-resource:`.
Unique nonblank raw IDs follow the prefix unchanged. Duplicate IDs append the original list index to
every occurrence; blank IDs use that index alone. All prescribed bases are reserved before allocation;
colliding bases append `:occurrence:<original-index>` repeatedly until distinct from all reserved and
assigned IDs. Diagnostics retain original IDs. References use exact raw supplied artifact IDs or full
source artifact IDs, never parsed output suffixes; raw duplicates stay ambiguous. IDs are occurrence
identifiers for a particular ordered input: pin the catalog revision and source digest when consuming them.

### Contract catalog matching and dependencies

Matching first seeks nonblank exact OID within type; when none match, it tries the full ordered key
and compatible SWC context. Without keys, exact case-sensitive type/name/namespace and available SWC
context produce candidates. Matching nonblank GUIDs take precedence over SWC captions; otherwise known
captions must not conflict. Full-key identity is exact only with matching nonblank GUIDs or captions.
The results are `resolved` for one exact identity, `candidate` for one incomplete identity, `ambiguous`
for several, `missing` for none, `unknown` for a null reference. No first match is selected.

Dependencies retain artifact IDs, not declaration IDs: MT-to-DT, DT uses, message-mapping endpoints
and function-library references. Multiple DT declarations do not multiply artifact identity.
Service request/response/fault uses retain participant (service-interface reference), operation and null
step. Mapping endpoints use `mapping-input`/`mapping-output`. External-message matching uses the inner
reference and attaches ports to message contract candidates; dependencies target the containing
definition. Unresolved or ambiguous uses remain diagnostic. A DT artifact can resolve without a specific
declaration binding; the helper does not attach its port to every declaration. Absent response references
do not invent missing response ports.

Each resource dependency retains original kind, namespace/location, evidence and candidates. URI matching
lexically resolves location against sourcePath and normalizes paths; it does not fetch anything, decode
percent escapes, case-fold or reinterpret OS paths. Compare with normalized provided resource paths.
One matching path is only `candidate`; several are `ambiguous`; none are `missing`. Missing sourcePath
or location, including namespace-only imports, stays `unknown`; malformed/opaque URI values are diagnosed.
Nested schemas sharing a path deliberately remain multiple candidates. Namespace agreement alone cannot
resolve an import. Native DT dependencies attach directly to their DT, with declaration/resource locators.

Independent dependency edges expose transitive missing leaves and finite cycles. Even all-candidate
edges prove neither schema compatibility nor import completeness; there is no import-complete flag.

### Contract catalog supplied claims

Supplied artifacts have `basis=supplied`; accepted kinds are `shared-message-mapping`, `iflow-mapping`,
`script-collection`, `local-script`, `mapping-function`, `external-definition`, `xml-resource`,
`native-message-type`, `native-data-type`. An artifact-declaration or design-decision evidence item
with nonblank source, locator and status supports classification. Unsupported kinds or absent support
become `unknown`, retaining original claim and evidence in diagnostics. Transport-only evidence,
including ProcessDirect, cannot classify shared mapping placement. The helper validates evidence shape,
not source authenticity, truth or approval.

`ArtifactClaim.owner` is descriptive text, retained verbatim. `ResourceBinding.ownerArtifact` and
`ContractClaim.artifact` reference artifacts; unresolved/ambiguous owners become null with diagnostics
preserving original text. Resource/contract occurrences remain in the report.

Disposition accepts `preserved-external`, `proposed-internal`, `unknown`; non-unknown dispositions need
supporting evidence. Supplied XML names need that evidence, nonblank localName and non-null namespace
(empty namespace is allowed). Invalid claims become unknown/null with indexed original-claim diagnostics.
Supplied contracts never overwrite source contracts or external-message QName unknowns. An existing
catalog/proposal may be attributed as supplied evidence but cannot confer approval.

Port roles are `request`, `response`, `fault`, `mapping-input`, `mapping-output`, `producer`, `consumer`.
Value roles are `input`, `default`, `api-response`. Unsupported roles and missing evidence are diagnosed
without dropping the original ports/values. Port/value evidence requires nonblank kind/source/locator/status.
StepRefs validate exact ProcessKey and step ordinal against the retained data, without name fallback;
invalid references remain visible. Null port steps can represent non-step-specific interface uses.

### Contract catalog data and limits

Value claims retain explicit producers, consumers and exact default text. A missing producer for
`default` or `api-response` is diagnosed. An API response never becomes a required input merely by
appearing in a schema or catalog. Claimed defaults and producers remain attributed claims, not execution
proof or automatic requirement propagation.

`data` preserves the full P3 report unchanged: declarations, initial/structural mappings, read domains,
before/after origin candidates, scope and unknown reasons. Before/after describes structural analysis,
not actual runtime availability, ordering within a step or guaranteed production on every path.
The catalog does not repair P3 unknowns or infer external calls/side effects.

Markdown renders artifacts, contracts, dependencies, supplied attribution and diagnostics, followed by
per-process/step before/after candidates, declaration/mapping identities and reasons. Source/supplied
cells escape pipes and newlines; unsupported fields remain visible. Synthetic tests cover identity,
claim validation, candidate imports, collision-safe IDs, data preservation and JSON/Markdown behavior;
they establish no customer deployment or importability. There is no schema compilation, import execution,
runtime proof, generator or approval engine. Native reflection registration is not native-executable proof.

## Migration helpers

Read-only migration helpers turn po-xray outputs into decision inputs. The attributes and BPM inventory CLI
helpers write Markdown by default and JSON with `--json`. These reports and the core DiscoveryReport use
`schemaVersion` "0.1", `generator` as in the other outputs, maps sorted by key, `issues` always written
(`[]` = none found), other nulls and empty lists omitted. Behavior and dossier reports have separate contracts below.

### `po-xray attributes <ico.json>...` → `AttributesReport`

```jsonc
{
  "schemaVersion": "0.1", "generator": { … }, "inputs": [ "icos-SND_SYS.json" ],   // paths as given
  "scenarios": [ {                                   // one per icos[i], in file order
    "file": "icos-SND_SYS.json",
    "ico": { "oid", "generatedFrom", "description", "senderComponent", "senderInterface", "senderNamespace",
             "receiverComponents": [ … ] },
    "attributes": [                                  // always 11, D-025 order: runtime, qos, sender_type,
                                                     // receiver_type, receiver_count, receiver_determination,
                                                     // interface_determination, mapping, error_handling,
                                                     // custom_exit, inbound_conversion
      { "name": "runtime", "value": "?", "source": "icos[2].receivers[0].qualityOfService",
        "candidates": [ "integrated-async", "decoupled" ], "question": "tenant or scenario choice" },
      { "name": "qos", "value": "EO", "source": "icos[2].receivers[0].qualityOfService" }
    ],
    "modules": [ { "side": "sender", "channel": "CC_SND_SOAP", "position": 1, "name": "localejbs/MyPreModule" } ]
  } ],
  "issues": [ ]
}
```
Row contract: a resolved row has a `value` from the D-025 set and a `source`; an open row has `value` `?`, the
`candidates` still possible (in D-025 order) and a non-blank `question`, `source` = the path looked at or null.
`note` is optional (e.g. "from qos BE"). The tool never guesses: what the inventory cannot decide is `?`.
`source` is a path into the input file: `icos[i]…` for ICO fields, `channels[k].adapter` /
`channels[k].modules[m].name` for the channel a `DirRef` resolves to (same `oid` when both carry one, else same
party, component and name; an unresolvable reference counts as missing).
Rules in short: QoS normalised (ExactlyOnce → EO, ExactlyOnceInOrder → EOIO, BestEffort → BE; ALO never derived);
BE → `integrated-sync`, EO/EOIO → `?`; adapter `transport/message`, a part starting with XI → XI, with IDoc → IDOC,
else OTHER (`SOAP/XI` → XI); dynamic routing → `n` receivers and `reuse-xrd`, with BE receiver_determination `?`
{`custom`} and `receiver_count` `n` noted, with EO/EOIO the runtime question names the decoupled condition (D-040);
a receiver or assignment condition → `xpath`, its prefixes noted with the sender request message type's `xmlNamespace`
from `esr` or "ask" (D-040); mapping shape from targets and multiplicities (the esr's message-mapping multiplicity is
cross-checked; a difference is a note `esr: …`, D-043), "one type" only when every target interface has the same name and
namespace (`ordered` and receiver_determination `custom` are never derived);
`error_handling` is always `?` (tenant switch, D-025); `modules` = channel modules, raw names, except adapter beans (`CallSapAdapter`,
`CallAdapterWithMessageAsXMB`, `XISOAPAdapterBean`, `CallXIAdapter`, optional `localejbs/`); receiver module → exit `?` none/post with a
receiver-module question (D-033); sender `AF_Modules/MessageTransformBean` → `inbound_conversion` `custom`; another sender module → both
rows `?` with one sender-module question (D-032), exit none/pre; with a receiver module all four and both questions;
an unresolved receiver channel opens post, an unresolved sender channel opens pre.
Markdown: `# Scenario attributes`, per scenario `## <generatedFrom, else oid>`, the description, `sender … → receivers
…`, a table `| attribute | value | source |` (open rows show `? (candidates)`), `### Questions` and `### Modules`
(omitted when empty), `## Issues` when any. A file that cannot be read or parsed fails the command (exit 1).

### `po-xray bpm-inventory <export-dir>` → `BpmInventoryReport`

```jsonc
{
  "schemaVersion": "0.1", "generator": { … }, "inputs": [ "export/SYNTH_SCA.json" ],  // files read as inventories
  "processes": [ { "file", "dc": "synth/bpm", "id", "name": "OrderApproval", "source": "src/bpmn/….bpmn",
                   "steps": { "automatedActivity": 2, "unknown": 1, … }, "stepsTotal": 7, "humanTasks": 1,
                   "unknownMetaclasses": [ "HumanActivity" ] } ],
  "ejb": [ { "file", "dc": "synth/ejb", "beansByRole": { "bpmFunction": [ "BookOrderBean", "CheckCreditBean" ] } } ],
  "totals": { "processes": 1, "steps": 7, "humanTasks": 1, "beansByRole": { "bpmFunction": 2, … } },
  "issues": [ { "severity": "warning", "code": "NOT_SCA_INVENTORY", "message": "…/notes.json is not …; skipped" } ]
}
```
Input: every `*.json` directly in the directory, sorted by name (non-json files and subdirectories are ignored), or
the one file given. A `*.json` that does not parse as `ScaInventory` or has neither `bpm` nor `ejb` is skipped with
issue `NOT_SCA_INVENTORY` (warning, `source` = the file). No usable inventory → the command exits 1.
`steps` counts nodes per `kind`; `humanTasks` is a heuristic: nodes whose `metaclass` contains "human"
(case-insensitive). The SCA reader has no human-task kind, so these nodes are kind `unknown` today and their
metaclass also appears in `unknownMetaclasses`. A bean with two roles is listed under both in `beansByRole`.
Markdown: `# BPM inventory`, `## Processes` table `| dc | process | steps | human tasks | unknown metaclasses |`,
`### <process>` with `| kind | count |`, `## EJB roles` table `| dc | role | beans |`, `## Totals`, `## Issues` when any.

### `po-xray discover …` → `DiscoveryReport`

Core only (`io.poxray.migration.ScenarioDiscovery`); the CLI command comes with MIG-21.

```jsonc
{
  "schemaVersion": "0.1", "generator": { … }, "inputs": [ "export/SYNTH.json" ],   // export files read as inventories
  "icosInput": "icos-SYNTH.json",                    // the ICO inventory read; omitted when none or unreadable
  "processes": [ {                                   // one per bpm[b].processes[p], in export order
    "file": "export/SYNTH.json", "dc": "synth/bpm", "id": "P1", "name": "OrderApproval",
    "processComponents": [ "BPM_CC" ],               // distinct, sorted
    "inbound": [ {                                   // usage role start | intermediate, catalog order
      "name": "OrderIn", "namespace": "urn:synth:orders", "role": "start",
      "icos": [ { "oid": "ICO1", "senderComponent": "SRC_SYS", "senderInterface": "OrderIn",
                  "senderNamespace": "urn:synth:orders", "receiverComponents": [ "BPM_CC" ] } ] } ],   // icos by oid
    "outbound": [ { "name": "NotifyOut", "namespace": "urn:synth:notify", "role": "notification" } ]  // no ICO
  } ],
  "components": [ { "name": "BPM_CC", "roles": [ "process" ] }, { "name": "SRC_SYS", "roles": [ "source" ] } ],
  "issues": [ { "severity": "warning", "code": "INTERFACE_WITHOUT_ICO", "message": "process OrderApproval …" } ]
}
```
Input: the exports are listed as for `bpm-inventory` (every `*.json` directly in the directory, sorted by name, or the
one file; `NOT_SCA_INVENTORY` for a file that is not an inventory with `bpm` or `ejb`; an inventory without `bpm` gives
no rows). The ICO inventory is the JSON `po-xray ico` writes; a file that cannot be read or parsed, or has no `icos`,
is issue `NOT_ICO_INVENTORY` (warning, `source` = the file) and every interface is then without an ICO.
A process's interfaces are the `bpm[b].catalog.interfaces` with a `usedBy` entry whose `process` is the process
name (`Interface.Usage.process` holds the name, not the id); one row per distinct role. Inbound (start,
intermediate): an ICO matches when some `receivers[r].interfaces[a].targets[t].iface` has the interface's name and
namespace (exact, case-sensitive); that receiver's component gets role `process`, the ICO's sender component `source`.
Outbound (outbound, notification): an ICO matches when `sender.iface` has the name and namespace; the sender component
gets `process`, every receiver component `target`. `processComponents` are the components in role `process` of that
process's matches; `components` holds every component of a matched ICO, sorted by name, roles sorted. Null parts
(catalog, interfaces, usedBy, receivers, interfaces, targets, iface, sender) match nothing.
Issues (all warnings, `location` = dc and process, `source` = the export file): `INTERFACE_WITHOUT_ICO` (process,
interface, namespace, role; the row stays with no `icos`), `PROCESS_WITHOUT_INTERFACES` (no inbound or outbound row),
`UNKNOWN_INTERFACE_ROLE` (a usage role outside the four; the interface is not listed for it).
Markdown: `# Scenario discovery` with the inputs, `## Processes`, per process `### <name, else id>` with a line naming
dc, file and process components and a table `| Role | Interface | Namespace | ICO | Sender | Receivers |` (one row
per ICO, `—` in the last three cells for an interface without ICO; Sender is `party / component` when a party is
set), `## Components` table `| Component | Roles |`, `## Issues` with `none` or a table `| Code | Message |`.

### BPM configured behavior

[BehaviorAnalysis](../po-xray-core/src/main/java/io/poxray/migration/BehaviorAnalysis.java) provides
three public static methods:

```java
BehaviorReport of(List<ScaInventory> inventories, List<String> labels,
                  IntegrationInventory icos, String icosLabel)
BehaviorReport read(Path exportFileOrDirectory, Path icosJson) throws IOException
String markdown(BehaviorReport report)
```

The [BehaviorReport](../po-xray-core/src/main/java/io/poxray/model/BehaviorReport.java) uses
`formatVersion=1`, `analysisVersion="bpm-behavior-v1"`. It composes the accepted data report
(embedding paths once), the independent fault report and existing ScenarioDiscovery matching with
original ICO/channel configuration. Source declarations and configured candidates establish no
runtime delivery, rejection, handler invocation, acknowledgment or transaction behavior.

New record collections are required, immutable snapshots preserving encounter order and null entries.
Nested legacy ICO, channel, interface, service-reference and discovery collections/maps are copied too,
including Source.files. Legacy null versus empty fields, map null values and list holes are preserved;
null map keys throw `IllegalArgumentException`. New BehaviorReport records serialize null fields and
empty collections explicitly. Reused legacy records, including `Issue`/`Issue.Location`, retain their
existing omission rules. A null discovery generator is therefore omitted from JSON.

Analysis does not mutate inputs. Repeated `of` calls on identical inputs serialize identically.
ScenarioDiscovery internally reads a clock; the copied discovery report has `generator=null`, removing
that generated metadata. Original source values such as ICO `modifiedAt` remain. Top-level issues append
data, faults, discovery, integration and read diagnostics in that order. Repeated child issues remain
repeated; they do not demonstrate independent faults.

Fields below are in record/JSON order. `reasons` contains explanatory tokens, not runtime observations.

| Record | Fields |
|---|---|
| `BehaviorReport` | `formatVersion`, `analysisVersion`, `data`: BpmDataReport, `faults`: BpmFaultReport, `discovery`: DiscoveryReport, `integration`: Integration, `processes`: ProcessWiring list, `issues`. |
| `Integration` | `input`: original ICO label, `schemaVersion`: supplied inventory version, `availability`, `selection`, `source`: IntegrationInventory.Source, `query`: Query, `icos`: IcoSource list, `channels`: ChannelSource list, `channelUses`: ChannelUse list, `issues`: original integration issues, `reasons`. |
| `IcoSource` | `ordinal`, `configuration`: original Ico projection or null, `reasons`. |
| `ChannelSource` | `ordinal`, `configuration`: original Channel projection or null, `reasons`. |
| `ChannelUse` | `ordinal`, `ico`, `receiver`, `assignment`, `role`: `sender`, `receiver` or `parameter`, `parameter`: original map key or null, `reference`, `source`: original DirRef or null, `candidates`: channel ordinals, `resolution`, `association`, `direction`, `reasons`. |
| `ProcessWiring` | `key`: BpmPathReport.ProcessKey, `state`, `interfaces`: InterfaceUse list, `calls`: StepCall list, `reasons`. |
| `InterfaceUse` | `ordinal`, `direction`: `inbound` or `outbound`, `context`, `source`: DiscoveryReport.InterfaceRow, `candidates`: InterfaceCandidate list, `steps`: path Step ordinals, `matches`: IcoMatch list, `state`, `reasons`. |
| `InterfaceCandidate` | `ordinal`, `source`: full catalogue Interface projection. |
| `IcoMatch` | `discovery`, `ico`, `routes`: Route list, `state`, `reasons`. |
| `Route` | `receiver`, `assignment`, `target`, `relation`, `reasons`. |
| `StepCall` | `step`, `location`: source Locator, `relations`: raw Link list, `elements`: included Element payloads, `interfaces`: InterfaceUse ordinals, `services`: ServiceCandidate list, `state`, `acknowledgment`, `transaction`, `reasons`. |
| `ServiceCandidate` | `ordinal`, `source`: full catalogue ServiceReference projection, `state`, `reasons`. |

Numeric references are one-based and keep original list gaps:

- `ProcessKey(input, component, process)` addresses the input inventory and its original BPM component
  and process, as in `data.paths`. Duplicate labels, IDs and names never replace this key.
- `IcoSource.ordinal` and `ChannelSource.ordinal` address original IntegrationInventory lists.
  Every entry gets a wrapper, including null entries with `missing-ico` or `missing-channel`.
- `ChannelUse.ordinal` is report-wide encounter order; `ico` selects IcoSource. `receiver` selects
  `configuration.receivers`; `assignment` selects that receiver's `interfaces`. `reference` selects a
  receiver's channel list entry or a parameter map entry in encounter order, and is null for senders
  or an absent/empty receiver-channel list. `candidates` selects ChannelSource ordinals.
- `InterfaceUse.ordinal` starts at one per process, inbound then outbound. InterfaceCandidate and
  ServiceCandidate ordinals address the original component catalogue lists, not the candidate list.
- `IcoMatch.discovery` selects the row in `InterfaceUse.source.icos`; `ico` selects IcoSource or is
  null when reassociation fails. Route indices descend through that ICO's receiver, assignment and
  target lists. Absent/empty structures use null; a null list entry retains its original index.
- `StepCall.step` and InterfaceUse.steps select the same process's `data.paths` Steps. StepCall.interfaces
  selects that ProcessWiring's InterfaceUse ordinals. Calls preserve path step order, even when disconnected.

### Behavior inputs and configuration

`of` requires non-null inventory/label lists of equal size, without null members; empty input is valid.
SCA schema `"0.1"` and legacy null are admitted; another explicit version throws `IllegalArgumentException`
before analysis. Optional `icos` and `icosLabel` may be null; the supplied label is retained unchanged.
Absent or unsupported retained BPM evidence keeps the child reports' unavailable states.
`markdown(null)` throws `IllegalArgumentException`.

[BehaviorInputs](../po-xray-core/src/main/java/io/poxray/migration/BehaviorInputs.java) reads one local
file, or direct regular files in a directory with a case-insensitive `.json` suffix, sorted
lexicographically by filename; it does not recurse. A null, missing or unreadable root, or one that is
neither a regular file nor a directory, throws `IOException`. An unreadable/malformed/null export,
a document with both `bpm` and `ejb` null,
or an unsupported SCA version is skipped with `BEHAVIOR_INPUT_UNAVAILABLE`, naming the file and reason.
No usable exports produces an empty report with `BEHAVIOR_NO_USABLE_EXPORT`, including an empty directory.
Labels are the original path strings. No network fetch, automatic export or input-file mutation occurs.

Integration availability is independent of selection:

| `availability` | Meaning |
|---|---|
| `absent` | No ICO object/path supplied; selection is `unknown`. |
| `unavailable` | Optional ICO read, parse or null-document failure; `BEHAVIOR_ICO_UNAVAILABLE` is retained. |
| `unsupported-schema` | Explicit ICO schema other than `"0.1"`; original source/configuration snapshots remain, without matching or derived channel uses. |
| `missing-icos` | Admitted object with `icos=null`; source/query/channels remain, without matching or channel uses. |
| `available` | Admitted schema and non-null ICO list; an empty list is only an observed empty collection. |

Null ICO schema adds `legacy-schema`. Selection is `query-limited` when Query.component or direction
is nonblank; otherwise `source-issues` when any source Issue is non-null; otherwise `unknown`.
All source issues, including null entries, remain in Integration.issues. Query, Source and empty
collections never establish whole-system completeness. Unavailable configuration cannot prove no match.

Snapshots retain original receiver conditions, QoS, interface assignments, target multiplicities,
mappings/parameters, dynamicRouting, noReceiverBehaviour, channel attributes/modules and source/query
metadata. These are parsed projections, not byte-exact archives: prior reader omissions and defaults
remain unknown. Basic interface operations, message patterns, input/output/fault QNames and service
references are projections too. Recursive schema/import/resource contract completeness belongs to P4
and is not delivered here.

### Behavior interface and route candidates

[BehaviorDiscovery](../po-xray-core/src/main/java/io/poxray/migration/BehaviorDiscovery.java) calls
existing ScenarioDiscovery with the ICO inventory only when Integration.availability is `available`.
Process-row count and each row's file/DC/id/name must agree with the path report's encounter order.
A mismatch yields `discovery-process-mismatch`; it never shifts rows or recovers identity by name.
ProcessWiring.state is `candidate` with stable discovery mapping and available path source, otherwise
`unavailable`. Incomplete source may still yield candidates; child coverage and reasons remain decisive.

Interface candidates reverse the lossy discovery projection using exact name/namespace, process name
and usage role. Every matching original catalogue index remains. One gives `candidate`, several
`ambiguous`, none `unavailable`; no state means confirmed binding. Null namespaces follow existing matching,
not resolved QName proof. Candidate usage.node.ref selects all matching step IDs within the exact
ProcessKey, in path order. Missing, blank or nonunique refs retain reasons; there is no name fallback.
Unknown roles keep discovery diagnostics rather than a fabricated direction.

Interface context is `pre-process` for role `start`, `existing-instance` for `intermediate`, and
`process-outbound` for `outbound`/`notification` (`unknown` for an unrecognized role). These distinguish
configured delivery before process entry from delivery to an existing instance; neither proves
correlation or actual delivery. BPM fault declarations remain in `faults` and do not establish how
configured ingress failures are handled.

Each discovery ICO row is compared against the whole original summary: OID, sender party/component/
interface/namespace and sorted distinct non-null receiver components. Every source match yields an
IcoMatch: one is `candidate`, several `ambiguous`, no match `unavailable` with null `ico`. Duplicate or
null OIDs cannot select a representative. Even exact summary coincidence is only a coarse candidate.

Inbound Route rows contain only receiver/assignment/target triples with an exact target interface
name/namespace match; `relation=interface-candidate`. Other incomplete/unmatched branches stay in the
ICO snapshot and add `incomplete-other-route`, without becoming delivery candidates. Outbound rows
enumerate all target combinations as `configured-candidate`; missing/null/empty structures retain
placeholder indices with `relation=unknown` and `unknown-route`.

InterfaceAssignment.operation is the **sender-side** operation, not the target BPM operation.
Conditions, including literal false, remain unevaluated and never prune routes. No-receiver error
policy and dynamic-routing configuration remain separate from BPM faults and observed rejection.
Routing issues can indicate dropped unresolved alternatives; diagnostic prose is not an authoritative
route identity. Keep those issues and uncertainty with the configured candidates.

### Behavior channel references

[BehaviorChannels](../po-xray-core/src/main/java/io/poxray/migration/BehaviorChannels.java) emits source
uses in ICO order: one sender use even for a null ICO/channel, then each receiver's channel entries
(one placeholder for a null/empty list), followed by that receiver's assignment parameter entries in
map order. Null/empty parameter maps produce no invented entries. Null channel records remain
snapshots but cannot match.

`resolution` describes reference identity only:

- Null channel catalogue: `unavailable`, no candidates, `channels-unavailable` reason. Empty catalogue:
  `channels-observed-empty` on Integration and `observed-empty-catalogue` on uses; exact selectors can
  be `missing`, partial/absent selectors stay `unknown`. Neither proves whole-system absence.
- A nonblank OID selects all exact OID candidates and never falls back to a name. Supplied non-null
  party/component/name fields corroborate identity. No candidate gives `missing`; multiple give
  `ambiguous` even with conflicting fields; one conflicting candidate gives `conflict`, otherwise `unique`.
- Without OID, supplied non-null fields filter candidates. A complete key requires non-null party
  (empty is meaningful) and nonblank component/name: zero/one/multiple candidates give
  `missing`/`unique`/`ambiguous`. Partial keys remain `unknown` regardless of candidate count.
  Null/selectorless references stay `unknown`, without binding arbitrary catalogue entries.

`association` is separate from identity. Receiver party/component projections lost whether text was
literal or a dynamic extractor; receiver association therefore stays `unknown` with
`receiver-identity-projection`, even when strings agree or disagree. For senders with unique identity,
known owner/candidate party or component disagreement gives `conflict`; both parties and components known and
matching gives `compatible`; missing values give `unknown`. Other identity states keep association
unknown, retaining `candidate-owner-conflict` when applicable.

Transport `direction` is `compatible` only for unique identity with the exact sender/receiver direction;
the exact opposite gives `conflict`; otherwise it is `unknown`. Ambiguous/partial/conflicting identities
never select a channel, and preserve candidate-direction-conflict/unknown reasons. Parameter uses
always have `association=not-transport`, `direction=not-applicable`, even with a unique channel.
Adapter attributes and module positions are configuration, not evidence of execution, ordering or acknowledgment.

### Behavior step calls

[BehaviorCalls](../po-xray-core/src/main/java/io/poxray/migration/BehaviorCalls.java) produces one StepCall
per path Step, including disconnected steps. ActivityHasOperation and AutomatedActivityHasServiceReference
use `f=target,s=activity`, qualified by source occurrence candidates. Raw relations retain all endpoint
candidates; included endpoint payloads remain in elements. Raw lists deduplicate by occurrence in source order.

A supported direct operation relation requires an included unique AutomatedActivity or
ProcessSupplements.NotificationActivity owner and WSDL2.Operation target. A direct service-reference
relation requires an included unique AutomatedActivity and mc.servicereferences.ServiceReference target.
Each owner permits at most one relation of each kind; repeated or competing relations are unknown.
Other kinds, including MappingActivity with these relations, are unsupported call grammar.

CatchEventHasTrigger (`f=definition,s=catch`) plus incident MessageEventDefinitionHasOperation remains
two-hop raw evidence. StartEvent/BoundaryEvent with MessageEventDefinition is the supported shape,
but either operation-link orientation retains `unknown-operation-direction`; no normalized target is
invented. A message trigger without an operation link retains `missing-message-operation`. Non-message
triggers alone do not invent calls; their declarations are in the fault report.

Service candidates use only exact raw target ID = ServiceReference.id, retaining all matching catalogue
indices without UUID/name fallback. A valid unique target with one projection is `candidate`; several
projections are `ambiguous`. A missing/excluded/ambiguous or wrong-kind target makes retained projected
rows `unavailable`. With no catalogue match the list is empty and `service-projection-unavailable`
remains; the raw relation is not discarded. Interface hints refer only to InterfaceUse.steps candidates.

StepCall.state follows this precedence:

1. `unknown`: source unavailable/unsupported, malformed or ambiguous required evidence, multiplicity,
   missing/excluded endpoints or unverified catch operation direction, even alongside a valid direct link.
2. `source-declared`: at least one supported direct relation without those gaps; source association only.
3. `candidate`: catalogue interface hints only.
4. `no-interface-call-observed`: admissible source with no call evidence or hints. This does not exclude
   expression/function side effects or prove that a step cannot call anything at runtime.

Every step has `acknowledgment=unknown` and `transaction=unknown`. Async patterns, adapters, modules and
QoS do not establish these runtime properties. Source calls do not prove invocation, successful delivery,
handler selection, fault propagation, retries or actual execution.

### Behavior CLI and MCP delivery

[CLI](../po-xray-cli/src/main/java/io/poxray/cli/BehaviorCommand.java):
`po-xray behavior <export-dir-or-file> [--icos <inventory.json>] [--json]` reads local exported inventory
JSON only. Default stdout is Markdown; `--json` emits the full BehaviorReport. Missing/unreadable export
roots fail the command. Optional ICO failures and an empty usable export set return diagnostic reports
successfully; inspect availability, coverage and issues rather than treating exit zero as completeness.

[MCP](../po-xray-mcp/src/main/java/io/poxray/mcp/PoXrayTools.java): `bpm_behavior(exportPath, icosPath)`
uses the same reader; icosPath is optional (default empty). It returns compact JSON inline up to 80,000
characters. Larger results use the existing `message`/`file` envelope pointing to the full indented
`<temp>/po-xray/bpm_behavior-*.json`, without truncation. A bad export root becomes ToolCallException;
a bad optional ICO remains a diagnostic report. The tool needs no live PO connection or automatic export.
This documents the implemented tool, not deployment or reload of an existing MCP server.

Markdown includes structural paths, data candidates, source faults, configured ICO/channel snapshots,
interface/call candidates and diagnostics. Source-derived table cells escape pipes/newlines and retain
coverage, reasons and explicit unknown acknowledgment/transaction. The report supplies analysis evidence,
not owner approval, a target design, generated artifacts or runtime validation.

### `po-xray dossier-check <dossier.json> [--baseline <previous.json>] [--json]` → `DossierReport`

MIG-23/P1, extended compatibly by MIG-24/P2: `io.poxray.migration.DossierValidator` provides a read-only
projection of the optional private migration ledger. The input v1 schema and workflow are in
[`skills/sap-po-migration/references/dossier.md`](../skills/sap-po-migration/references/dossier.md).
Opt-in v2 fields and readiness rules are in
[`references/readiness.md`](../skills/sap-po-migration/references/readiness.md). Without readiness options,
the CLI and existing Java check methods keep the following P1 report shape, while validating v2 events too.
It is separate from `ScaInventory`; the omission rule in "Principles" above does not apply to this report.

| Field | Type and meaning |
|---|---|
| `formatVersion` | Integer input format version, `1` or `2`; malformed input may report `0`. |
| `migrationId` | Input `M-<n>` identity; may be null for invalid root input. |
| `ledgerSha256` | SHA-256 of the raw current ledger bytes. |
| `valid` | Boolean: no error diagnostics; warnings may remain. |
| `historyStatus` | `verified`, `not-checked`, `changed`, or `invalid-baseline`. |
| `decisions` | Array of `Decision` records, in first-introduction order. |
| `risks` | Array of separate `Risk` records, in event order. |
| `derivedDocuments` | Array of latest valid registrations per document ID, in first-introduction order. |
| `nextAction` | Latest valid `next-action` text, or null. |
| `diagnostics` | Array of `Diagnostic` records in deterministic check order. |

All top-level report fields are emitted, including null scalar fields. Empty arrays `[]` are retained
at the top level and in nested records; lists are immutable snapshots. The six nested records have
these exact fields (a null diagnostic `eventId` is omitted from JSON):

| Record | Fields |
|---|---|
| `Scope` | `sectionId`, `revision`, `path`, `sha256` (strings). |
| `Decision` | `id`, `status`, `text`, `source` (strings); `proposedScopes`, `activeScopes` (Scope arrays); `approvalEventIds` (string array). |
| `Risk` | `id`, `text`, `source` (strings); `sections` (string array). |
| `Dependency` | `decisionId`, `sectionId`, `revision`, `sha256` (strings). |
| `DerivedDocument` | `id`, `path`, `status` (strings); `required` (boolean); `consumes` (Dependency array). |
| `Diagnostic` | `severity`, `code`, `message` (strings); `eventId` (string when assigned, otherwise omitted in JSON; nullable in Java). |

Decision statuses: `proposed`, `deferred`, `rejected`, `approval-unresolved`, `approved`,
`partially-approved`, `superseded`, `partially-superseded`. Proposed scopes do not grant approval;
`activeScopes` identifies precisely the surviving approved scopes. Approval event IDs retain the evidence
references for applied approvals. Source locators are not opened or authenticated by the validator.

Derived status is `missing` when the path is not a regular file; otherwise `current` when every declared
dependency matches an active approved scope's decision, section, revision and digest, or `stale` when any
does not. In v2 a stale consumed answer basis also makes the document stale. Missing/stale required
documents produce errors; optional ones produce warnings. No prose
semantics are checked. Every ever-approved section snapshot is checked for existence and raw-byte digest,
including superseded scopes; a changed snapshot invalidates the report even if a derived dependency matches.

The caller explicitly selects the baseline; it must share identity and contain an event array that
is an unchanged structural prefix of the current ledger. Versions must match, except v1 baseline→v2 current
upgrade, which emits `HISTORY_FORMAT_UPGRADED`. Downgrades and other changes fail. Object key order/whitespace do not matter; array
order/values do. `verified` means that comparison passed, not that the design or approvals are authentic.
Without a baseline, `not-checked` and `HISTORY_NOT_CHECKED` are returned; the dossier can still be valid.
Relative evidence paths resolve against the current dossier directory regardless of baseline location.

Diagnostic severity is `error` or `warning`. Codes: `INVALID_DOCUMENT`, `UNSUPPORTED_FORMAT`,
`DUPLICATE_EVENT_ID`, `INVALID_REFERENCE`, `INVALID_TRANSITION`, `INVALID_SCOPE`, `APPROVAL_CONFLICT`,
`SUPERSESSION_CYCLE`, `SNAPSHOT_MISSING`, `SNAPSHOT_CHANGED`, `DERIVED_MISSING`, `DERIVED_STALE`,
`HISTORY_NOT_CHECKED`, `HISTORY_CHANGED`, `INVALID_BASELINE`; v2 adds warnings `HISTORY_FORMAT_UPGRADED`,
`ANSWER_BASIS_CHANGED` and errors `MILESTONE_CYCLE`, `LEGACY_IMPORT_INVALID`. Messages exclude snapshot payloads.
Unreadable or malformed main JSON, duplicate keys and trailing tokens fail before a report is emitted.

Default stdout is Markdown: `# Dossier current design`, ledger digest, history status, `## Decisions`,
`## Risks`, `## Derived documents`, `## Next action`, `## Diagnostics`. Invalid reports explicitly mark the
view as non-authoritative. Cells escape supplied text. `--json` emits the report; summary/errors use stderr.
Exit `0` means valid (possibly with warnings), `1` means invalid or input/parse failure, without a stack trace.
No input is written. This default report exposes no readiness result; validity does not establish question
closure, runtime behaviour or authentic consent. Exact-scoped approval never replaces whole-plan approval.

Public Java entry points are static `DossierValidator.check(Path)` and `check(Path, Path baseline)`, both
returning `DossierReport` and declaring `IOException`, plus `markdown(DossierReport)` returning `String`.
A null baseline means no history comparison. `DossierReader` is package-private. Native reflection includes
`DossierReport` and all six nested records.

### Dossier readiness assessment

MIG-24/P2 adds `--readiness` and `--milestone <id>` to `dossier-check`; the selector implies readiness.
With either option, `--json` emits `DossierAssessment`; otherwise stdout is assessment Markdown separating
question status, exceptions, work/completeness/approval/readiness and blockers, with escaped supplied text,
ledger digest and history status. Summary/selection diagnostics use stderr without a stack trace.
Without a selector, exit `0` means `dossier.valid`, even when milestones are blocked; with a selector,
exit `0` requires validity and that milestone's `ready=true`. Blocked, unknown or legacy-v1 selections exit `1`.
No input is modified, upgraded or selected automatically. Output remains private.

Public static methods on `DossierValidator`:

```java
DossierAssessment assess(Path file) throws IOException;
DossierAssessment assess(Path file, Path baseline) throws IOException;
String markdown(DossierAssessment assessment);
```

A null baseline omits comparison. Existing `check` methods and `markdown(DossierReport)` remain unchanged.
The wrapper and its nine nested public records have these exact component names, types and order:

```java
DossierAssessment(DossierReport dossier, String status, List<Question> questions,
    List<Assumption> assumptions, List<Risk> risks, List<Basis> scopeBases,
    List<Coverage> coverages, List<Milestone> milestones)
Block(String sectionId, String neededByMilestoneId)
Question(String id, String text, String topic, String owner, String expectedEvidence,
    List<String> sections, String classification, String classificationSource,
    List<Block> dependencies, String status, String answerEventId, String answer,
    String evidenceSource, String deferralReason, String reviewAt)
Assumption(String id, String questionId, String text, String owner, String approvalSource,
    List<DossierReport.Dependency> scope, List<String> milestones, String verifyAtMilestoneId)
Risk(String id, String detailEventId, String owner, String approvalSource,
    List<DossierReport.Dependency> scope, String limitations, List<String> triggers,
    List<String> milestones, List<String> questions, String status, String reopenEventId)
BasisInput(String questionId, String resolutionEventId)
Basis(String id, DossierReport.Dependency scope, List<BasisInput> answers, String status,
    String legacyApprovalEventId, String historicalEvidenceSource)
Coverage(String sectionId, String eventId, List<String> questions, String owner,
    String evidenceSource, String status)
Blocker(String code, String sectionId, String questionId, String message)
Milestone(String id, String definitionEventId, String kind, List<String> sections,
    List<DossierReport.Dependency> requiredScopes, List<DossierReport.Dependency> connections,
    String connectionRationale, List<String> requires, String status, boolean canWork,
    boolean complete, boolean approved, boolean ready, List<Blocker> blockers,
    List<String> exceptions, List<String> approvalInputs, String approvalEventId)
```

All lists are deeply immutable snapshots. The new wrapper/records serialize empty arrays and nullable
fields explicitly. The nested `dossier` retains its existing JSON contract, including omission of an
unassigned diagnostic `eventId`. `classificationReason` remains in input history; it is not a Question
record component. Native reflection registers the wrapper and all nine records alongside the six P1 records.

| Record | Status and scope |
|---|---|
| assessment | `assessed` for valid v2, `invalid` for invalid input, `not-assessed` for v1; v1 has empty new lists. |
| Question | `open`, `deferred`, `answered`; classification `scoped`, `nonblocking`, `unclassified` is independent of topic. |
| Risk | `accepted`, `reopened`; only detailed risks appear here, bare P1 risks remain in `dossier.risks`. |
| Basis | `current`, `stale`; exact scope and current resolutions determine usability. |
| Coverage | `current`, `stale`; latest valid registration per section, preserving first-introduction order and the applicable question subset. |
| Milestone | `ready`, `blocked`, `not-assessed`; missing classification or current inventory prevents assessment. |

`sections` preserves declared parts; `connections` preserves the separate exact dependency list. Their union
is the effective section set for all question, inventory, exception and basis membership. Other lists retain
input/introduction order. Milestone `requiredScopes` is the declared scope list. `approvalInputs` is a
deterministic first-encounter deduplicated set of current definitions through the prerequisite graph,
consumed scope approvals/bases, current coverage IDs, applicable latest classifications, current answers
or used exceptions, and current prerequisite milestone approval IDs where present. Approval input array
order is irrelevant; duplicates or missing/extra inputs fail.

`canWork` requires validity, current inventories, clear or explicitly excused dependencies and ready
prerequisites. Design can work before content approval/basis; other kinds also require complete approved
content. `complete` additionally requires active approved exact scopes/connections, verified snapshots and
current answer bases. Unpinned design is incomplete. `approved` binds the current definition and exact
inputs; its latest applied `approvalEventId` remains visible when stale. Approval cannot make incomplete
content complete. `ready` is canWork for design, complete for verification, complete+approved for final-plan,
and complete plus one ready final-plan ancestor for specification/implementation/deployment. That ancestor
must cover every exact required decision/section/revision/digest, including connections; neither a graph
edge alone nor combined unrelated partial plans suffices. Invalid assessments cannot affirm any boolean.

Readiness blockers do not themselves add error diagnostics: `QUESTION_OPEN`, `UNCLASSIFIED_QUESTION`,
`QUESTION_COVERAGE_MISSING`, `QUESTION_COVERAGE_STALE`, `SCOPE_NOT_APPROVED`, `STALE_BASIS`,
`PREREQUISITE_NOT_READY`, `CONNECTION_NOT_APPROVED`, `FINAL_APPROVAL_REQUIRED`, `FINAL_PLAN_REQUIRED`,
`INVALID_DOSSIER`. Stale bases also produce warning `ANSWER_BASIS_CHANGED`; required stale derived documents
retain P1 error behavior. An open/deferred question alone does not invalidate the dossier.

The checker evaluates declared dependencies and evidence integrity. It does not prove complete wiring,
approval-source authenticity, runtime behavior or correctness of prose. Assumptions preserve unknown facts
and verification debt. An explicit matching risk detail can waive that debt only at its exact named
milestone/scope, visibly; it neither transfers downstream nor becomes technical evidence. Readiness grants
no authorization to deploy or operate a tenant. See the readiness reference for immutable answer bases,
the evidence-bound legacy import exception and risk reopening.
