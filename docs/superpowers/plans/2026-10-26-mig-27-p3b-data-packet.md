# MIG-27 — P3b declared data producers and structural candidate flow

Status: revision2 preflight PASS; separate test authoring authorized, implementation awaits test approval/red.
R1 FAIL: read/mapping payload storage, expression grammar, owner/direction pairs, intra-step
provenance, uncertainty transfer and orphan attribution. Revision2 closes each below.
Base: cb81f9506515ebbec54c87d0ca4770609d55c9e2, accepted MIG-26; independent root483/0/0/26.
Governing plan: migration-process-improvements P3, D-048 and D-051.
This completes the data portion of P3b; P3c integration/fault composition and CLI/MCP remain next.
Public report/types are draft for owner review at hand-over.

## Ownership, source boundaries and purpose

Read CODEX-WORKFLOW first. Architect designs/approves separately authored tests; implementer
writes source; independent verifier and reviewer follow. At most two workers, exactly one writer.
No private-input reads, tenant operations, deployment, push, new language or dependency.
Context7 resolve/query precedes library-dependent code, with exact resolved-runtime evidence
if the official identifier/docs are unavailable. Keep existing inventory meanings and schema.
Use BpmSourceEvidence and the accepted BpmPathAnalysis; no second parser or editor-text interpreter.

Report which exported declarations could contribute to each process data object before/after
a structural step. These are source candidates, never proof of execution, final value, definite
availability, overwrite, feasibility or completeness of an archive. Known candidates and missing
knowledge are independent dimensions. Missing schema default or producer is never a value.

Source facts: Galaxy.java, MappingReader, ExpressionReader, ProcessReader.dataObject and
BpmEvidenceReader; preparation target/mig-p3b-data-scout.log. The legacy flattened Assignment
loses occurrence, ownership path and QName namespace; never reconstruct identities from it.
Mapping flags come directly from each part; no proven ancestor/configuration precedence exists.

Primary SAP semantic sources (read 2026-10-26; the archive's exact runtime release is unknown):
- [Input/output mapping](https://help.sap.com/saphelp_em92/helpdata/en/ec/8ad8cbe7a941029f8f86130c780abd/content.htm?no_cache=true):
  ordinary input reads process context; output reads activity/event output.
- [Mapping activities](https://help.sap.com/saphelp_em900/helpdata/en/1d/2bc1853ff54c5d8c531719432d1749/content.htm?no_cache=true):
  mappings transform between process-context objects.
- [Embedded subprocess](https://help.sap.com/saphelp_em92/helpdata/en/29/cbf4a8a4f945dea506e6417df1b702/content.htm?no_cache=true):
  child context sees ancestors, parent cannot see child locals; no parent/embedded data mapping.
- [Loop-local context](https://help.sap.com/docs/SAP_NETWEAVER_750/6fc9ea54f6d3425183718b944038b882/3d786ec2dd1243c28aeffa4c1c70df25.html):
  loop-local source objects cannot silently be bound to same-named process objects.
These sources justify interpretation limits, not observed runtime acceptance. Do not import
Cloud workflow/HANA workflow or message-mapping Disable Field semantics into Galaxy BPM.

## Public interfaces and exact record components

New final utility io.poxray.bpm.BpmDataAnalysis, private constructor:
`public static BpmDataReport of(List<ScaInventory> inventories, List<String> labels)`;
`public static String markdown(BpmDataReport report)`.
Same argument contract as BpmPathAnalysis: null args/elements or count mismatch -> IAE.
Legacy nullable internal fields are gaps; empty input is valid. No IO, clock or input mutation.
Compute BpmPathAnalysis once and include that report for exact step/arc addressing.

New io.poxray.model.BpmDataReport, exact component order below. Abbreviations:
Locator, Element, Link, Endpoint mean BpmSourceEvidence nested types; ProcessKey and Fact mean
BpmPathReport nested types. Existing records retain their serialization behavior.

- BpmDataReport(int formatVersion, String analysisVersion, BpmPathReport paths,
  List<ProcessData> processes, List<Issue> issues)
- ProcessData(ProcessKey key, String dc, String id, String name, String source, String coverage,
  List<String> reasons, List<ObjectData> objects, List<MappingUse> mappings,
  List<Declaration> declarations, List<Read> reads, List<StepState> steps)
- ObjectData(int ordinal, Element source, List<Link> memberships, List<Locator> scopePath,
  List<Link> types, List<Element> typeCandidates, String scopeState, List<String> reasons)
- MappingUse(int ordinal, Integer parent, Integer parentDeclaration, Link ownership,
  Endpoint mapping, Locator rootOwner, String role, String readDomain, String writeDomain,
  Evidence evidence, List<String> reasons)
- Evidence(List<Fact> path, List<Element> elements, List<Link> links)
- Name(List<Link> links, List<Element> candidates, String state)
- Binding(String state, List<Integer> objects, List<String> reasons)
- Declaration(int ordinal, int mapping, Endpoint part, List<Name> targetPath,
  Evidence evidence, Binding target, String valueKind, String candidacy, List<String> reasons)
- Read(int ordinal, int declaration, String domain, Evidence evidence, Binding binding,
  List<OriginSet> candidates, List<String> reasons)
- OriginSet(int object, List<Integer> declarations, String coverage, List<String> reasons)
- StepState(int step, Locator location, String connectivity, List<OriginSet> before,
  List<OriginSet> after, List<String> reasons)

formatVersion=1, analysisVersion=bpm-data-v1. Deep immutable collection snapshots and explicit
null/empty JSON on all NEW records; do not change Issue/Issue.Location. Register outer plus
all10 nested types in NativeReflection. All ordinals one-based within ProcessData. MappingUse
parent/parentDeclaration are null for a top-level use; Read.declaration and OriginSet.declarations
address declaration ordinals, StepState.step addresses paths.processes' Step.ordinal.
ProcessKey qualifies every locator. Fact.kind separates element/link ordinal spaces.
Evidence.path is the contextual ownership/root/child/expression path; lists of raw facts retain
their occurrence order and duplicates. Shared IDs/parts/mappings do not merge contextual uses.
Each Evidence.elements includes included payloads of its interpreted subjects and their directly
referenced QName/feature/configuration candidates, not only locators. Read evidence contains its
expression, every path step, every segment's QName payload (raw name AND namespace) and reference
links; excluded feature payload stays visibly excluded in its Endpoint. Raw elements/links follow
source occurrence order, once per Locator within that Evidence; duplicate source occurrences
remain distinct. Evidence.path follows traversal order and can repeat a Locator when recording
a contextual cycle. No original inventory is needed to recover a retained QName value.

Output order: paths process order; objects source element order; top-level ownership links in
source order, followed immediately by nested argument uses in depth-first expression/part order.
Within one mapping, roots then child links depth-first in source order. Declarations/reads get
encounter ordinals. OriginSets follow object ordinal, producer IDs ascending declaration ordinal.
Remaining unattributed mappings/parts follow source order after attributed uses. No timestamps.

## Admission and scope identity

Accept only source format1/bpm-source-v1 and exact process source match as MIG-26. Include a
ProcessData with coverage unavailable and empty fact-derived lists when evidence is unavailable;
retain matching path StepState rows with unknown before/after reasons, not invented declarations.
Copy source inventory issues into outer issues, then path issues and BPM_DATA_* diagnostics in
stable order; do not silently drop source warnings. Do not deduplicate distinct occurrences.

ObjectData covers DataObject and TextVariable occurrences in the process partition and objects
attributed by source memberships to its pool/embedded scopes. Keep orphan/ambiguous objects with
scopeState unknown and diagnostics. Source type links/candidates are evidence only; absent or
excluded payload is not a complete contract and never an initializer.

Attribution is structural, not based on matching names or arbitrary first processes:
- A member with validated ancestry to this collaboration's pool belongs to this process, including
  its passive pools. An ownership link touching that member as owner is attributable even if its
  mapping endpoint is missing/wrong-kind. Cross-partition relation location does not exclude it.
- Ambiguous owner candidates touching several processes yield an unknown use in each touched
  process, with null rootOwner and ambiguous-owner-attribution; they never seed a certain owner.
- A malformed/orphan object, mapping, part or ownership link physically in a partition interpreted
  by exactly one process is attributed there as unknown. If an endpoint resolves to a different
  process's validated owner, that positive ownership takes precedence over physical placement.
- Reachable mapping/part/argument evidence follows its contextual owner across partitions.
  Unreached shared metadata or a partition interpreted by multiple processes cannot be assigned
  arbitrarily: emit an outer BPM_DATA_UNATTRIBUTED_SOURCE issue per source occurrence, qualified
  by input/component ordinal, dc, exact archive/partition and element/link Locator in its message.
  This component diagnostic does not project the whole raw payload or invent a ProcessData row;
  the unchanged original sourceEvidence remains the raw evidence for uninterpreted metadata.
  The component's processes get unattributed-component-data and incomplete coverage/unknown origin
  sets, because the possible affected process is unknown. This attribution is deterministic.

Validate a single acyclic ancestry through uniquely resolved ScopeContainsScopeObjects links
to a pool belonging uniquely to this process collaboration. Reuse the MIG-26 scope validation
where possible; do not use legacy Process.pool fallback or Process.scopes/name joins. A small
package-private read-only scope view on BpmPathGraph is allowed, with no public path change.
scopePath is immediate containing scope through outer ancestors to the pool, inner first;
ObjectData scopeState is active, inactive or unknown. Boundary inherited membership may use
only the already-validated owner derivation. Invalid pool/root ancestry stays unknown.

Visibility requires validated scope paths: same scope or an ancestor in the current active
pool is visible; child/sibling/passive-pool objects are not. Do not invent shadowing precedence:
same-name visible inner and ancestor objects both remain candidates. If ancestry is unknown,
a binding is unknown; preserve plausible lexical candidates without claiming exclusion based
on that unknown ancestry. This uncertainty survives an empty result. Retain source membership
and relevant reasons independently of path graph gaps unrelated to scope.

## Mapping ownership and contextual extraction

Use exact f/s directions in Galaxy.java. Primary ownership associations are
InputMappingOfMappableObject, OutputMappingOfMappableObject and ViewContainsMapping, f=mapping,
s=owner. Retain every attributable declaration including duplicate/missing/wrong-kind endpoints.
A mapping use is contextual: ownership link + mapping-root/child path, not only a mapping ID.
A source mapping with several owners gives separate uses. Ambiguous endpoints cannot select
the first candidate. Missing/ambiguous/wrong-kind parts remain terminal Declaration rows with
raw Endpoint/candidate facts and a gap. Do not silently omit them as the legacy reader does.

MappingContainsMappingParts f=part,s=mapping; MappingPartHasChildren f=child,s=parent.
Walk simple occurrence paths, terminate repeats with a visible cycle diagnostic/terminal row.
Do not silently truncate at legacy MAX_DEPTH64. An iterative walk is acceptable; retain finite
shared DAG uses, not a single global visited set. Excessive ambiguous structure must stay visible,
never be interpreted through an arbitrary first match. Unattributed local mappings/parts receive
unattributed uses/rows and gaps (null ownership/mapping where there is no such fact).

A Declaration.targetPath has one Name per root/child part, including absent/ambiguous names.
Name.links preserves MappingPartHasQName relations; candidates are retained QName elements,
with raw name and namespace. Name.state is resolved, missing, ambiguous or invalid. Missing
root name is never replaced by a child's name; literal string "null" is never a missing name.
Evidence includes part attributes/texts, associated QName, expression and configuration links
and included supporting elements. Preserve raw enabled/category/expander/transformer and
MappingPartConfiguration/NSPrefix evidence; do not invent configuration precedence.
MappingUse.evidence also includes the Mapping Element and its metadata even when there are zero
parts. For mapping/part metadata, start at that subject and collect incident retained links in
either direction; include their included endpoint payloads. Traverse further only through
MappingPartConfiguration, NSPrefix and properties.PropertyBag occurrences, using a visited-Locator
set. Keep unresolved/wrong-kind endpoints and unknown association names in the evidence. Do not
follow ownership, root/child, QName or expression relations as configuration traversal; their
payloads are collected by the appropriate explicit walk. Nonstructural incident links/configuration
are uninterpreted, add configuration-semantics-unknown, and never silently alter effective flags.
Never traverse a referenced function implementation or XSD graph through this metadata closure.

Roles/domains:
- Unique ViewContainsMapping owner DataObject/TextVariable: role initial, writeDomain
  process-context; target owned by that exact object ID/occurrence, regardless of root QName.
  readDomain unknown (initializer ordering/context not established).
- MappingActivity input/output: role mapping, both domains process-context.
- AutomatedActivity/NotificationActivity/EndEvent input:
  role input, readDomain process-context, writeDomain external-input.
- AutomatedActivity/NotificationActivity/StartEvent/BoundaryEvent output:
  role output, readDomain external-output,
  writeDomain process-context. Equal output/process object names do not establish a read.
- StartEvent/BoundaryEvent input and EndEvent output are unsupported pairs, both domains unknown,
  role unknown. Keep their declarations and opaque-write uncertainty; do not create a supported
  process-write candidate from these additional directions merely because the owner is known.
- EmbeddedScope mapping: role unsupported-embedded, both domains unknown, visible diagnostic.
  Do not model transfer between parent/embedded contexts contrary to the cited semantics.
- ViewContainsMapping on process/lane role: role role-assignment, readDomain unknown,
  writeDomain role; never a process data write.
- Other/wrong-kind/ambiguous owner: role unknown, domains unknown; facts retained.
- FunctionInvocationHasParameterMapping: role argument, writeDomain function-argument,
  readDomain inherited from enclosing expression; parent points to enclosing MappingUse and
  parentDeclaration to its declaration. Its rootOwner remains the top-level owner.
  Walk argument roots and children; argument target itself is never a process write.

valueKind is expression when exactly one MappingPartContainsExpression link (Galaxy.PART_EXPRESSION) resolves uniquely to a
Galaxy.Rule.Expression and its nonempty ordered ExpressionHasSteps links each resolve uniquely
to one of Reference, Parent, Literal, FunctionInvocation or Condition. These five metaclasses
are the complete syntax grammar for this slice; a missing/wrong-kind direct step or multiple/
unresolved part-expression link makes valueKind unknown. Do not choose an arbitrary expression.
Repeated step/link occurrences remain ordered source facts, not a reason to collapse the path.
No expression link, or one unique empty-tree expression with absent/blank editor text, is
structural; a text-only nonblank expression is unknown. Unsupported nesting cannot be evaluated.

The expression label asserts a retained assignment syntax, not a valid runtime expression:
- ReferenceHasQName is zero/one/many, preserving missing/ambiguous binding; ReferencePointToXsdFeature
  is independent raw evidence. Missing QName does not erase the assignment declaration.
- Parent retains its raw occurrence and marks unknown root when leading. Literal retains raw
  value/type; absent value or type adds uncertainty, never a fabricated scalar/Boolean.
- FunctionInvocationHasFunctionInterface requires one uniquely resolved FunctionInterface, or
  one catalog-only built-in function reference without a conflicting local candidate, to describe
  known function identity. Missing/multiple/wrong-kind identity adds function-identity-unknown.
  Parameter mapping links are zero-or-more source occurrences; no arity/body evaluation. Walk every
  independently resolved parameter mapping with inherited domain; unresolved ones create a gap.
- A Condition requires exactly one unique Expression through each of its Condition/Positive/
  Negative associations for complete nested evidence. Missing/multiple/wrong-kind/cyclic branches
  create gaps; traverse independently resolved branches, without choosing a missing branch's value.
- Cyclic nested expression/argument paths stop on the repeated contextual occurrence with a visible
  diagnostic. Unknown function identity or malformed nested branch does NOT erase the outer
  supported syntax or independent reads: its valueKind remains expression and target candidate
  may propagate, always with incomplete coverage. A malformed direct outer step remains unknown.
These aggregation rules preserve declared source provenance without claiming a successful call.
Structural rows are not scalar writes, deletion, creation or schema-default materialization.
Editor text is retained but never parsed as executable evidence.

## Candidate target/read binding

Binding.state: owned, lexical-candidates, external, unknown or none-observed.
Initial owned means declared target ownership only, never completed initialization.
For supported process-context mappings use the ROOT target QName's exact raw name to find
visible ObjectData.source name candidates. Even one match is lexical-candidates, never owned.
Preserve QName namespace and type namespace independently: do not equate them, strip prefixes
or use namespace/type matching to prove variable identity. No name uniqueness assumption.
No matching declared object is none-observed only with validated scope/root-name evidence;
otherwise unknown. Neither state proves that runtime context contains no such object.
external covers a supported external/function-argument target with no process object binding.

Read extraction traverses retained expression trees and associations, never Expression.reads
or editor text. One read root is a Reference at the start of an expression's ordinary path;
retain the whole path's raw step/link facts. ReferenceHasQName and ReferencePointToXsdFeature
remain distinct; only a unique retained QName with a nonblank name is a supported lexical
process root. A feature alone or missing QName stays unknown, not a guessed name.
A leading Parent is a data-tree parent operation: retain it with unknown root, not scope lookup.
References following function invocation/literal/unsupported non-reference steps are result
paths, not new process roots. Do not traverse a function implementation as process reads.
ConditionHasConditionExpression/PositiveExpression/NegativeExpression recurse separately,
without pruning. Invocation argument expressions recurse under inherited readDomain.
Unresolved/ambiguous/cyclic expression/parameter associations remain visible gaps, terminate,
and preserve known independent reads. A parameter mapping reused in two domains has two uses.
External-output reads have external binding and no process-producer candidates even if the
name collides. Unknown domain retains facts but cannot assert process-only binding.
At loop hosts/contained scopes preserve loop-local-context-unknown for contextual reads;
same-named exported objects cannot disprove a loop-local source.
Such a read has Binding.state unknown; any retained candidate IDs carry incomplete coverage.

## Structural producer sets and uncertainty

A declaration is a structural producer candidate only for supported process-context writes,
valueKind expression and an owned/lexical candidate target. candidacy is candidate, disabled,
structural, external or unknown. Exact local enabled=false is disabled; preserve it in output
but exclude that row from propagated producer IDs. Child extraction is never suppressed.
Missing/invalid enabled, a disabled ancestor, or uninterpreted configuration adds uncertainty;
do not infer inherited disabling, activation or effective flag values. A child's otherwise
known candidate remains accompanied by this uncertainty. No strong update or destructive kill:
lexical guesses/set/append/merge flags cannot remove earlier declarations from provenance.

Initial candidates seed their own validated scope entry (before its connected start), never
globally. Parent initial candidates may reach nested entries; child-local initials cannot
leak to parent or sibling. No valid entry -> no fabricated seed, unknown evidence retained.
Initializer reads have initializer-order-unknown; do not claim another initializer ran first.
Ordinary candidates attach to their source Step only. A disconnected/unknown owner is retained
as a declaration but does not seed an asserted connected flow; corresponding state is unknown
where connectivity is unknown. Known connected subgraphs may preserve candidates despite gaps.

Finite monotone union over resolved structural arcs, retaining all join/cycle alternatives.
Start before includes scope initial seeds; after includes before plus local candidate writes.
A candidate can reach its own before on a real structural cycle. Fixed-point iteration must
terminate without arbitrary path/depth cutoff; output order remains deterministic.
Conditions, including typed false/default, never prune candidates; these are structural sets.
For a resolved boundary-attachment, conservatively union owner's before/after and add
boundary-timing-unknown, rather than asserting owner output completed before the boundary.
For embedded-entry propagate parent candidates plus child scope seeds. No synthesized child
exit: the embedded host's parent successor and all its descendants carry
child-effects-unmodeled. Do not fabricate child producer IDs on a nonexistent return edge.

OriginSet lists known declaration candidates and independently carries coverage complete,
incomplete or not-applicable. Complete means the supported selected-source calculation is
accounted for, NOT exhaustive runtime producers or definite availability. Empty means no
supported declaration observed on these modeled paths, never no data or a null/default.
StepState lists all proven-visible objects in object order, for both before and after:
- connected: compute known sets; complete only when no applicable uncertainty has reached them;
- disconnected in a complete graph: empty sets with not-applicable, and step reason
  not-reached-in-modeled-graph; local declarations remain in the report without seeding flow;
- unknown connectivity/visibility: incomplete sets and step reasons, retaining any supported
  positive candidates. An empty list because visibility is unknown is explicitly incomplete in
  the step/process reasons. A passive-pool state is not silently a complete active state.
ProcessData.coverage is unavailable for inadmissible source evidence; otherwise incomplete if
the path report is not complete or any attributed data relation/tree/ownership/scope/flag/config/
effect is unresolved, unsupported or uncertain; complete otherwise. Lexical-candidate identity
alone is an explicit supported heuristic and need not make structural coverage incomplete.

Uncertainty transfer is a second monotone union alongside producer IDs:
- An incomplete graph or unknown process attribution starts process-wide uncertainty at every
  step. Keep known connected candidates; unknown roots must not create a complete empty state.
- For a process-context/unknown-domain mapping at a uniquely known connected owner, missing roots,
  target, part, expression or unsupported write semantics creates opaque-write at that owner's
  after state. It affects every proven-visible object if the target is unknown, or the target
  candidates when those are known. No producer ID is required for this transfer. Propagate it
  through every resolved outgoing arc and joins/cycles; include it in boundary transfer.
- An initializer with unknown target or structure introduces uncertainty at its known scope
  entry; if the scope/owner cannot be established, apply process-wide uncertainty. An unresolved
  mapping owner cannot confine the possible write to an arbitrary step: process-wide unknown.
- A local unsupported node effect starts opaque-write for visible objects at its after state.
  A declaration's missing flag/disabled ancestor/config uncertainty affects its candidate targets
  at generation, or all visible targets if unknown. Retain candidate IDs alongside these flags.
- Supported external-input/function-argument/role writes do not become opaque process writes.
  Missing reads affect read/process coverage, but do not by themselves alter already known
  producer provenance. Unknown writeDomain still requires opaque-write handling.
- Embedded-exit and boundary-timing uncertainty transfers as specified above. Apply scope
  visibility when entering a child; never erase uncertainty merely because a candidate list is
  filtered or empty. Record all applicable reason tokens in stable encounter order.

Read.candidates normally uses its enclosing step's before sets for process reads. MappingActivity
has no established assignment order: union before with OTHER eligible process-write declarations
at that same step for matching objects, and add intra-step-order-unknown with incomplete coverage
when any such alternative exists. Exclude the enclosing declaration and its ancestor declaration
chain from NEW same-step alternatives (an argument cannot be fed by its own enclosing assignment's
not-yet-computed output). Preserve that same declaration if it already reaches before through a
structural cycle. Follow argument-enclosure ancestry, not the mapping-part parent chain: a parent
mapping part evaluated independently is not automatically an argument's enclosing assignment.
Ordinary input mapping does not acquire that activity's later output mapping as an input producer.
Initializer/unknown-step reads remain incomplete; disconnected reads are not-applicable. No
Read claims exact runtime availability or a single established intra-step execution order.

## Acceptance and verification

Separate test-author: real synthetic MOIN-in-SCA through PoXray.read except documented unsafe
legacy recursion cases; helpers may extend SyntheticBpmSca additively without changing fixtures.
Tests should cover each behavior once or grouped when assertions are genuinely inseparable:
1. initial ownership by exact DataObject despite different root QName; no XSD default invention;
2. structural parent, explicit empty and missing expression remain distinct from scalar writes;
3. missing/invalid/local false flags and disabled parent with enabled child retain facts/uncertainty;
4. missing/duplicate/wrong-kind mapping, part, QName, expression and configuration retain gaps;
5. reused mapping/part under two owners and diamond paths stay contextual; cycle terminates;
6. root QName missing cannot use child name, namespace differs from data-type namespace;
7. same names in ancestor/current/sibling/passive scopes preserve candidates and scope exclusions;
8. ordinary output-context reference colliding with process name is not a process read;
9. ordinary input/role/argument targets are not process writes; MappingActivity is context-to-context;
10. argument reads inherit enclosing domain, shared argument has separate contexts, result path
    is not a new context root; Condition branches preserve reads; Parent is not scope ancestry;
11. join retains both producer alternatives; cycle reaches fixed point including own before;
12. disconnected producer does not enter connected sets; false condition remains structural;
13. nested initials seed own entry only, child writes don't acquire a synthetic parent exit;
14. downstream embedded gap, boundary timing and loop-local context are visible;
15. unknown graph/missing scope keeps known candidates with uncertainty, no false absence claim;
16. unsupported/unattributed mapping or data object visible; duplicate input/process IDs isolated;
17. legacy unavailable, deterministic repeat, input preservation, deep immutability, explicit JSON,
    escaped honest Markdown and complete native reflection registration.
Revision2-specific assertions: Read alone retains both QName names/namespaces of payload/item;
zero-part mapping retains attributes/configuration/unknown links; missing function identity keeps
valid independent argument reads and an uncertain declared candidate; malformed Condition branch
does not erase another branch; unsupported event mapping directions never become supported writes;
same-step write/read lists the other local candidate with intra-step-order-unknown; a missing root
part propagates opaque-write without a producer ID; disconnected and unknown state differ; shared
unowned metadata across two processes yields a component-qualified diagnostic, no arbitrary owner.
Architect reads all written tests before implementation; observed red is missing API compile
failure or failed behavioral assertion, not tooling failure. Preserve accepted test hashes.

Allowed production: new bpm/BpmDataAnalysis.java, bpm/BpmDataContext.java,
bpm/BpmDataMappings.java, bpm/BpmDataExpressions.java, bpm/BpmDataFlow.java,
model/BpmDataReport.java; a small package-private read-only scope view in BpmPathGraph.java;
additive NativeReflection entries. No broad parser/model/path refactor.
Allowed tests: new bpm/BpmDataAnalysisTest.java, BpmDataReportTest.java and BpmDataFixture.java,
additive SyntheticBpmSca/NativeReflectionTest only. No dependency/POM/CLI/MCP changes.
Documentation will use a separate bounded packet after source release; no premature command.
Verifier: focused acceptance plus existing source/path/legacy/native tests, full root
mvn -B -ntp verify without new skips; actual skip reasons, scope/hash/privacy checks.
Independent code/docs review before commit; full native executable remains separately reported.

## Acceptance method names for the test author

The behavioral list above governs expectations. Proposed separate source tests (group only when
one inseparable fixture/assertion requires it; explain any adjustment in handback):
- initialOwnershipUsesDataObjectOccurrenceRatherThanTargetRootName
- schemaTypeDefaultsDoNotBecomeExportedInitializers
- structuralAndTextOnlyMappingPartsDoNotInventScalarWrites
- rawEnabledFlagsAndDisabledAncestorDoNotInventInheritance
- missingWrongKindAndAmbiguousPartsRemainTerminalEvidence
- missingRootQNameNeverFallsBackToDescendantOrTypeNamespace
- sharedMappingAndPartUsesRemainOwnerQualified
- mappingDiamondAndCycleRetainContextAndTerminate
- visibleScopeCandidatesKeepShadowedNamesAndExcludeSiblings
- unknownAncestryCannotProveNoCandidate
- ordinaryOutputReferencesNeverBindToSameNamedProcessObject
- supportedInputRoleAndArgumentTargetsDoNotWriteProcessObjects
- mappingActivityReadsAndWritesProcessContext
- sharedFunctionArgumentsInheritSeparateReadDomains
- invocationResultPathDoesNotCreateAProcessRead
- conditionBranchesAndParentStepsKeepReadUncertainty
- readEvidenceRetainsEveryQNamePayload
- emptyMappingRetainsConfigurationAndUnknownMetadata
- missingFunctionIdentityKeepsArgumentReadsAndUncertainDeclaration
- malformedConditionBranchDoesNotEraseIndependentRead
- unsupportedEventMappingDirectionsDoNotBecomeSupportedWrites
- joinAndCycleKeepAlternativeProducerSets
- disconnectedProducerAndFalseConditionHaveDifferentStructuralResults
- nestedInitialsAndChildWritesDoNotLeakAcrossScopes
- embeddedSuccessorsCarryUnmodeledChildEffects
- boundaryTransferRetainsBeforeAndAfterWithTimingUncertainty
- loopLocalReadNamesNeverBecomeCertainProcessBindings
- sameStepOtherDeclarationRemainsAnUncertainReadAlternative
- missingRootPartPropagatesOpaqueWriteWithoutProducerId
- unresolvedOwnerMakesPotentialEffectsProcessWide
- disconnectedAndUnknownStatesNeverBecomeEquivalentEmptyAvailability
- orphanSharedMetadataUsesComponentQualifiedDiagnostic
- duplicateInputsAndProcessIdsNeverCrossBindOccurrences
- legacyUnsupportedEvidenceKeepsUnknownStates
Report tests: inputContractAndExplicitEmptyCollections; deeplyImmutableSnapshots;
jsonRetainsNullEmptyAndSourcePayloads; repeatIsDeterministicAndInputUnchanged;
markdownEscapesEvidenceAndLabelsCandidateLimits. Add native registration test preserving old ones.
Cycle regression includes same declaration already reaching before: do not lose it under the
argument-enclosure exclusion. Timeout guards test actual termination without changing source input.

Preflight r2 PASS; exact association spelling and argument-enclosure/cycle precision incorporated.
Test-author captures observed red plus SHA256 manifests; architect reads every test/helper first.

## Architect test approval

Architect read all analysis tests, complete fixture/report files and the additive native diff.
Approved43 new tests:37 source analysis (34 proposed plus3 malformed/orphan edge-case groups),
5 report and1 native. Every source case parses a real synthetic SCA; no scope-cycle replacement.
Cycles/diamonds use independent fixtures; the deep mapping case exceeds the legacy depth64 cap.
Before approval, corrected the excluded-feature fixture to put the XSD feature in METADATA:
all PROCESS elements are selected by P3a. Architect re-read the final method, including the new
source precondition included=false. No weakened assertion or production change.
Final SHA256 (all four files immutable during implementation):
- BpmDataAnalysisTest: BB5B7333F95571D30B3DE09DAAFDE15E4AC981F03EFD110862AF812F37923A0A
- BpmDataReportTest: 259C7A7140162BC16E3F7CBE63001FE6F736BF13B30ADBA3EA5C456D5D70AC76
- BpmDataFixture: 7F43C058F1A50DAC19286227A24A10851C945A736316A9ABC8A283A2D7E1D07A
- NativeReflectionTest: 0AF2CD05AF56E4220A377E3EAA54AA50CE995100DF648958198765EA462976F3
Author observed missing-API compile-red before final fixture relocation; final independent red
and scope check must confirm the frozen files before implementation starts. The interrupted
sandbox attempt/final repeat are not acceptance evidence. Context7 configured-endpoint queries
and exact resolved JDK/JAR fallback are in target/mig-p3b-data-test-author-context7-summary.log.
Original four path-test hashes and SyntheticBpmSca remain unchanged; native assertions are additive.
Independent verifier confirmed final compile-red exit1/100 missing-API compiler errors/0 tests
in target/mig-p3b-data-red-verifier.log; native module not reached. All4 approved hashes and4
prior path hashes match; production/POM/SyntheticBpmSca diff empty, native14 additions only.
Bounded implementation is now authorized; do not alter approved tests or hide source-reader errors.

## Review round1 — separate completeness regressions

Independent static review found four combinations absent from the approved fixtures. Final132
focused tests passed; these counterexamples have not yet been executed. Add a separate
po-xray-core/src/test/java/io/poxray/bpm/BpmDataCompletenessTest.java, using existing read-only
BpmDataFixture or literal synthetic MOIN helpers inside the new file. No original test edits.
Test-author owns only that new test file and target/mig-p3b-data-completeness-* logs.

- nestedTextOnlyConditionBranchKeepsCandidateButAddsUncertainty: fully resolved outer Condition
  with three branch expressions; one has nonblank editor text and no tree, another a valid read.
  Retain outer expression/candidate and independent read/raw editor payload; declaration and
  downstream OriginSet must be incomplete. Unsupported nested syntax cannot disappear merely
  because the direct outer steps are valid. No text evaluation.
- unknownObjectVisibilityRemainsVisibleOnEmptyStepStates: complete connected chain with an
  unscoped DataObject and no mappings. Before/after may have no proven-visible objects, but each
  connected step explicitly carries object-visibility-unknown. The process gap alone is insufficient.
  This does not require poisoning proven provenance of a different, correctly scoped object.
- disconnectedChildInitializerDoesNotContaminateOuterObjects: connected root chain/outer object,
  disconnected EmbeddedScope with a valid child StartEvent and local initializer. Outer object
  before/after stays complete without child initial-entry-unknown or producer IDs; child steps
  stay not-applicable, and their initializer declaration remains visible. Do not invent global
  uncertainty for a known child scope simply because its valid entry is structurally disconnected.
- malformedInitializerPartKeepsKnownObjectTarget: ViewContainsMapping owned by A with a missing
  root part, plus unrelated B. Retain target Binding owned/[A], unknown value/candidacy and no
  producer ID. A receives incomplete opaque-write evidence at its entry; B stays complete and
  uncontaminated. Cover ambiguous/wrong-kind terminal parts too if they expose the same early return.

Architect reads the whole new file and helper code before approval; author and independent verifier
must observe assertion-red for these cases, not compile/infrastructure failures. Same implementer
then corrects only allowed new helpers, preserving all frozen hashes plus this regression hash.
Run regression selection and the prior132 selection plus the new cases; same reviewer round2.
Full reactor remains pending until these source changes and bounded documentation correction finish.

R1 addendum: ambiguousOrphanPartExpressionOwnerRemainsUnknown is reachable through two local
unreferenced MappingPart occurrences sharing an ID, with one unique expression and an ambiguous
PART_EXPRESSION second endpoint. Per-occurrence orphan admission must not erase the original
association ambiguity. Both rows keep unknown valueKind, expression uncertainty, raw endpoint
candidates and unknown/no-producer candidacy. This was authorized to the test-author in a message
while it owned the writer; the architect appends the same requirement after its release.

Architect read the complete BpmDataCompletenessTest and all its fixture/helper assertions; approved7
regressions: the four initial cases, separate missing/ambiguous/wrong-kind initializer cases and the
orphan-expression addendum. Observed author EXECUTED_FAIL7/7/0/0,exit1 with assertion failures only,
target/mig-p3b-data-completeness-author-final-red.log. SHA256
176DC48037C5C23EBE1CAA20A4D84A867D92AB6AA1A259EF26BEB3ED8DFA208B.
Original4 approved data/native and4 path hashes remain unchanged. Independent assertion-red
confirmation is next; no implementation correction until it confirms this frozen file.
Independent verifier confirmed assertion-red7/7/0/0,exit1 in
target/mig-p3b-data-completeness-verifier.log. New hash, original4 data/native and4 path hashes
match;8 production snapshots unchanged. Same implementer is authorized for a bounded correction
inside the already-allowed new helpers after the architect grants sole-writer ownership.
