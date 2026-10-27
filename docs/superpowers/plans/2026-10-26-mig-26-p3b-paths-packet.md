# MIG-26 — P3b structural paths and declared control metadata

Status: accepted; revision2 preflight PASS;43 new tests approved; code r2/docs PASS; independent root483/0/0/26.
R1 found unsupported control relations, incomplete loop selection and unspecified boundary attachment validation; revision2 closes those rules below.
Base: c8b4f86821063b80aef936d3063e154385314286, accepted MIG-25/P3a.
Governing plan: migration-process-improvements P3, D-048 continuous authorization.
P3b is two bounded slices: this graph/metadata report, then data producers. P3c composes these
with configured integration/fault boundaries into BehaviorReport and CLI/MCP views.
P3 is incomplete until all slices pass. This permanent graph report is a reusable subreport,
not a placeholder claiming producer or runtime analysis is implemented.

## Ownership and limits

Read LOCAL-WORKFLOW first. Architect owns design/packet and approves separate red tests;
implementer owns source; independent verifier/reviewer follow. Two workers, one writer.
No private-input reads, tenant operations, deployment, push, new dependencies or languages.
Context7 resolve/query before library-dependent code; exact resolved-JAR fallback as documented.
Do not alter legacy ProcessReader, model meanings, P3a selection or the shared schema version.
Use source occurrences from BpmSourceEvidence, not a second XML parser or a name-based join.
No runtime simulator, XPath evaluator, fault priority, transaction or loop execution claims.

## Sources and design boundaries

P3a packet defines parsed occurrence evidence and its limits. Existing ProcessReader contracts
joins and falls back to the first pool when active flags are invalid; neither can determine
this graph's source completeness or entry. target/mig-p3b-scout.log records exact source tokens.
Galaxy.java defines the associations; BuiltinFunctionCatalog and its shipped JSON define
canonical Boolean types. ExpressionReader prefers editor text; that text alone is not typing.

The preliminary independent design challenge accepted the split, requiring explicit identity,
completeness, unknown propagation, condition and producer-handoff rules. The rules below close
those decisions for preflight; they remain a draft public API for review at hand-over.

## Public interfaces and records

New io.poxray.bpm.BpmPathAnalysis is a final utility with private constructor:
`public static BpmPathReport of(List<ScaInventory> inventories, List<String> labels)`;
`public static String markdown(BpmPathReport report)`.
One non-null label per non-null inventory; reject null arguments/elements or size mismatch with
IllegalArgumentException. Null optional fields inside a legacy inventory are evidence gaps,
not argument errors. Empty input returns explicit empty collections. No file IO or wall clock.
Package bpm allows reuse of Galaxy/BuiltinFunctionCatalog without widening those internals;
the later migration BehaviorAnalysis composes this report.

New io.poxray.model.BpmPathReport and nested records, exact component order:

- BpmPathReport(int formatVersion, String analysisVersion, List<Input> inputs,
  List<ProcessPaths> processes, List<Issue> issues)
- Input(int ordinal, String label, String sha256)
- ProcessKey(int input, int component, int process)
- Fact(String kind, BpmSourceEvidence.Locator location)
- Flag(boolean present, String value, String interpretation)
- Endpoint(String id, String resolution, List<BpmSourceEvidence.Locator> candidates)
- Condition(String state, String text, List<Fact> evidence, List<String> reasons)
- Step(int ordinal, BpmSourceEvidence.Locator location, String id, String kind, String name,
  List<BpmSourceEvidence.Locator> scopes, String connectivity, List<Integer> witness,
  Flag terminate, List<String> reasons)
- Arc(int ordinal, String kind, BpmSourceEvidence.Locator location, String id,
  List<Endpoint> sources, List<Endpoint> targets, String resolution, Condition condition,
  Flag isDefault, List<Fact> evidence, List<String> reasons)
- Loop(BpmSourceEvidence.Locator location, String id, List<BpmSourceEvidence.Locator> hosts,
  String mode, Flag sequential, String flowCondition, String completion,
  List<Fact> evidence, List<String> reasons)
- ProcessPaths(ProcessKey key, String dc, String id, String name, String source, String coverage,
  List<String> reasons, List<Step> steps, List<Arc> arcs, List<Loop> loops)

formatVersion=1; analysisVersion=bpm-paths-v1. Every collection is a deeply immutable snapshot;
serialize null fields and empty collections explicitly. Register outer plus all ten nested records.
All ordinals are one-based. ProcessKey qualifies each locator by input/component/process;
Fact.kind is element or link because those two occurrence lists have independent ordinal spaces.
Step.location and sequence Arc.location denote elements; derived Arc.location is null and its
Fact list identifies the supporting relations. Never conflate MOF ID with occurrence identity.
Input order, component/process list order and source encounter order are stable. No timestamps.
Step/Arc ordinals address rows within a ProcessPaths; witness is a list of Arc ordinals.

## Evidence admission, scope and completeness

Admit only sourceEvidence format1/selection bpm-source-v1. Match Process.source exactly to
locator.archive + "!/" + locator.partition; do not guess a partition from its basename or ID.
Require one uniquely identified Collaboration matching Process.id in that partition. Resolve
CollaborationContainsPools and ScopeContainsScopeObjects against endpoint occurrences, never
MoinIndex first-match behavior or the legacy pool fallback. Exactly one explicit active=true
pool is the root; every other linked pool must explicitly say false. Missing/invalid flags,
duplicate pool links or ambiguous endpoints invalidate entry and negative conclusions.

Visit scope memberships with a visited/active set. EmbeddedScope is both a node in its parent
and the scope of its children. Preserve membership ambiguity and cycles, never recurse forever.
Use ProcessReader's role classification: connectors; data/text variables; lanes/roles;
annotations/annotation connectors; and nodes. Known node kinds match its existing kindOf table.
Keep contracted artificial joins as ordinary source join nodes. Unknown scope-member metaclasses
remain unknown steps and make negative conclusions unknown; never silently drop them.
Also retain known node/connector occurrences in the process partition that have no valid scope;
they are evidence gaps, not proof of disconnection. Cross-partition members may be included only
through unique valid scope relations. Cross-scope sequence endpoints are unsupported in v1.

coverage is complete, incomplete or unavailable, strictly for this supported parsed graph.
The following make the entire process incomplete: invalid root selection; unresolved, ambiguous,
multiple or cyclic membership; unknown scope-member kind; unscoped known control object;
missing/ambiguous/invalid connector endpoints or connector membership; unsupported cross-scope
sequence edge; no root StartEvent; or an embedded scope with no uniquely scoped StartEvent.
Missing condition/loop declarations do not alone invalidate structural coverage: report their
own unknown states. Any graph-relevant relation whose owner cannot be attributed unambiguously
also makes coverage incomplete. Do not turn unrelated mapping/type diagnostics into graph gaps.

Supported structural relations are exactly CollaborationContainsPools, ScopeContainsScopeObjects,
SourceOfIncomingConnectors, TargetOfOutgoingConnectors and ActivityHasBoundaryEvent, all with the
Galaxy.Workflow prefix. The following existing metadata relations are deliberately nonstructural:
Galaxy's LANE_HAS_OBJECTS, CONNECTOR_EXPRESSION, ACTIVITY_OPERATION, ACTIVITY_SERVICE_REFERENCE,
ACTIVITY_EVENT_DEFINITIONS, CATCH_EVENT_TRIGGER, CATCH_EVENT_EXPRESSION, THROW_EVENT_RESULT,
INPUT_MAPPING, OUTPUT_MAPPING, VIEW_MAPPING, DATA_CONTAINER_TYPE, ANNOTATION_OF_CONNECTOR and
CONNECTOR_MODEL_ELEMENT constants, plus the exact loop attachment association below.
For any other association touching a current control node, connector or scope (including any
candidate in an ambiguous endpoint), emit BPM_PATH_UNSUPPORTED_CONTROL_RELATION with the link
locator and all endpoints, and mark the process incomplete. Do not silently treat an unrecognized
control-associated relation as documentation. A link touching no control object does not invalidate
structural coverage merely because it describes unknown mapping/type metadata.

Source evidence absent/unsupported or an unresolvable process source gives unavailable: keep
legacy node rows in their original order with null locators, unknown connectivity and an explicit
reason; do not fabricate source arcs. Candidate process partitions with no interpreted Process
produce BPM_PATH_UNINTERPRETED_PARTITION issues, so an empty processes list never implies success.
All conclusions are restricted to retained parsed evidence; incomplete archive/runtime coverage
is not represented as a complete source or execution trace.

## Arcs and structural connectivity

Emit one sequence arc for each original SequenceConnector/ConditionalSequenceConnector occurrence.
Read SourceOfIncomingConnectors and TargetOfOutgoingConnectors with f=node,s=connector, retaining
every associated endpoint declaration in sources/targets and every source-link Fact. Empty lists
mean missing association, not empty-string IDs. Preserve null/blank IDs and all ambiguous candidates.
resolution is resolved only with exactly one association on each side, unique correct connector
identity, unique node endpoints and a shared valid scope. Otherwise use missing, ambiguous or invalid,
with reasons identifying the exact sides. Only resolved arcs participate in traversal.

After sequence arcs emit derived boundary-attachment arcs in link order (owner→boundary), then
embedded-entry arcs in host/start encounter order (host→start in its child scope). Their kind
must remain visible in JSON, Markdown and every witness. Derive only from unique valid attachments
or membership; malformed evidence stays visible as an invalid arc/issue and makes coverage incomplete.
These are potential structural relations, not fault selection or execution. Do not synthesize
child-end→parent-successor arcs or treat nested starts as independent roots.

A boundary attachment is valid only with exactly one source link for that boundary occurrence,
one unique owner whose kind is automatedActivity, mappingActivity, notificationActivity or
embeddedSubprocess, and one unique target of exact metaclass Galaxy.Workflow.BoundaryEvent.
Duplicate identical links and multiple owners are ambiguous, not deduplicated. Emit one derived
arc per attachment link, preserving unresolved/invalid/ambiguous status and both raw endpoints.
An absent boundary membership may derive exactly its uniquely scoped owner's scope, with reason
boundary-scope-from-owner and the supporting attachment fact. Apply this before the unscoped-node
check. Existing membership must uniquely agree with that scope; conflicts/multiple memberships
remain invalid, never overwritten. Invalid owner kinds, unknown owner scopes or wrong target kinds
make coverage incomplete and prevent traversal. Attribute an attachment to this process if its
link lies in its source partition or either endpoint candidate is one of its control objects.

Seed only StartEvent nodes uniquely belonging to the validated active root. Traverse resolved arcs
with a finite queue/visited set; connector cycles terminate. Conditions, including a literal false,
DO NOT prune structural connectivity. First witness uses stable arc order. A root start has an empty
witness and root-start reason; every other connected row carries an actual path of arc ordinals.
connectivity is connected for a valid witness, disconnected for no witness in a complete supported
graph, otherwise unknown. A valid positive witness survives an unrelated graph gap. Nodes in passive
pools are unknown with inactive-pool reason, not asserted unreachable in their own process.
Never call connected executed, guaranteed, feasible under all conditions or runtime-reachable.

## Conditions, booleans and loops

Flag preserves attribute presence and exact raw value. interpretation is true or false only for
those exact strings, otherwise unknown; missing is present=false/value=null, never false.
Use Flag for isDefault, terminate and sequential. Do not infer branch priority or executed termination.

ConditionalSequenceConnectorContainsExpression is f=expression,s=connector. Retain all expression
links as facts. Condition.state is missing (no association), unresolved (missing endpoint), ambiguous
(multiple association/candidates), constant-true, constant-false, unknown, or unconditional.
unconditional applies only to SequenceConnector without a condition association. Unexpected condition
links on that type yield unknown. Derived arcs use unknown with their structural-relation reason.

Constant recognition requires exactly one uniquely resolved Galaxy.Rule.Expression, exactly one
ExpressionHasSteps link to a uniquely resolved Galaxy.Rule.Literal, and one LiteralHasSimpleDataType
link f=type,s=literal whose ID the shipped BuiltinFunctionCatalog qualifies as xsd:boolean. Accept
that catalog reference only when it has no local source candidate; a conflicting/ambiguous/local
type declaration stays unknown. Literal value must be exactly true or false. Keep type-link evidence
even though its endpoint is catalog-resolved rather than selected-source-resolved. No arbitrary XSD
type inference. Missing/blank editor stringExpression permits tree-only recognition; nonblank text
must strip to the same literal, otherwise unknown. Preserve original editor text as text, or the
literal value when absent; a Reference named false, untyped literal, multiple/unsupported steps,
invalid value or generated/complex expression never becomes a constant by rendered text alone.

Retain every MultiInstanceLoopCharacteristics occurrence in the process partition and every such
occurrence referenced by an attributable loop attachment, once per occurrence per process. Attachments
use the exact source typo EmbeddedScopeContainsLoopCharcateristics (f=loop,s=host), with Galaxy.Workflow
prefix. A link is attributable if in the process partition or any endpoint candidate is a current
scope/control object or one of the selected loop occurrences. Retain its related expression/item links.
Those related associations are exactly Galaxy.Workflow.MILoopCharacteristicsContainsExpression,
Galaxy.Workflow.MILoopCharacteristicsContainsCurrentCollectionItemDO and
Galaxy.Workflow.MILoopCharacteristicsHasNumberOfCompletedIterationsDO, with f=related object,s=loop.
One Loop row groups all attachment facts for that declaration; hosts lists all candidate host locators
in encounter order without choosing a winner. An unattached declaration has an empty hosts list and
unknown mode/completion with unattached-loop reason. A valid attachment has exactly one link, one unique
loop occurrence and one unique host of metaclass Galaxy.Workflow.EmbeddedScope in this process.
The host must also have exactly one loop attachment in total; several distinct loop declarations
on the same host are ambiguous for every affected row, even when each individual link resolves.
Any missing/ambiguous/duplicate/wrong-kind endpoint makes mode/completion unknown while retaining raw
flags and facts. Emit BPM_PATH_LOOP_UNRESOLVED, BPM_PATH_LOOP_AMBIGUOUS or BPM_PATH_LOOP_UNSUPPORTED
as applicable, naming the attachment locator, original endpoint IDs and all candidate locators.
An unsupported loop metaclass or a dangling attachment with no known loop occurrence gets that
explicit unknown diagnostic even when no Loop row can be made; never silently treat empty loops as
no loop when such diagnostics exist. A cross-process declaration may appear in each attributable
process with its original locator and separately qualified ProcessKey.
mode is sequential/parallel for explicit sequential=true/false, else unknown. completion is all
only for flowCondition=all, else unknown, subject to valid attachment above; flowCondition retains the original value. Multiple loop
declarations/duplicate attachment links remain separate facts with ambiguity reasons, never first-win.
No iteration count, execution support, join timing or successful completion is proved by these flags.

Producer handoff: initial mapping ownership identifies a target, not execution. This graph omits
embedded exit semantics; the later data pass MUST keep child effects at parent successors unknown
unless a separate justified completion relation is supplied. Empty candidate sets cannot prove absence.

## Diagnostics, presentation and acceptance

Use stable BPM_PATH_* issue codes and reason tokens; include occurrence/source evidence and all
uncertainties. No mutation of input inventories/evidence. Markdown must show scope/coverage,
each step and its structural status/witness, original and derived arc kinds, condition/default
distinctions, loop declarations, raw missing/invalid flags and limitations. Escape Markdown cells.

Test-author writes meaningful extraction/report tests using SyntheticBpmSca + PoXray.read:
- connected chain and disconnected branch; connected edge with unknown condition is not false;
- typed false condition remains structurally connected and visibly constant-false;
- reference named false, untyped false, editor/tree disagreement and missing Boolean stay unknown;
- missing/multiple/unresolved/ambiguous condition links preserve distinct states and facts;
- root active flag missing, invalid, all-false, multiple-true and a second unknown pool prevent fallback;
- duplicate endpoint IDs, duplicate relation links, missing endpoints and cross-scope edges are retained;
- original contracted join/outgoing arc retained; unknown member prevents a false negative;
- disconnected embedded host does not seed its children; connected host witness names derived entry;
- boundary witness names attachment without claiming fault interception; passive pool not root;
- boundary scope inherited only from one valid owner; conflicting scope, duplicate owners/links and wrong endpoint kind stay unknown;
- unrecognized control-associated relation prevents a false disconnected verdict;
- connector cycle terminates with stable witnesses; membership cycle/multiple parents cannot hang;
- raw loop parallel/sequential/missing/invalid/completion variants and duplicate declarations retained;
- unattached loop, unsupported loop metaclass and dangling/ambiguous attachment produce visible unknown evidence;
- legacy/unsupported evidence gives unknown; uninterpreted candidate partition creates an issue;
- duplicate IDs across input/component identities cannot cross-connect; labels count validated;
- immutable explicit-null/empty JSON, repeat determinism, honest escaped Markdown and native records.

Most tests must read synthetic SCA, not manually assemble projections. A scope cycle cannot safely
be fed through current ProcessReader.visitScope (no guard); for that adversarial analysis case only,
replace captured sourceEvidence on a parsed synthetic inventory, and state why. Do not silently fix
ProcessReader or weaken the test. A separate repair may be proposed if needed, outside this packet.
Observed red is missing promised API or a behavioral assertion; architect reads all tests before code.

Allowed production: new bpm/BpmPathAnalysis.java, bpm/BpmPathGraph.java, bpm/BpmPathConditions.java,
model/BpmPathReport.java and additive native registry entries. Helpers may be package-private.
Allowed tests: new bpm/BpmPathAnalysisTest.java, bpm/BpmPathReportTest.java, additive SyntheticBpmSca
helper methods and NativeReflectionTest only; retain all previous assertions and fixture behavior.
Documentation will use a separate bounded packet after code. No new CLI/MCP command in this slice.
Verifier: focused synthetic tests, full root mvn -B -ntp verify without new skips, native registry,
determinism/input-preservation checks and independent review. Full native executable is a separate
environment-dependent check, never implied by reflection registration.

## F-14 — narrowly authorized prerequisite repair

The approved tests now reach two existing parser NPEs before analysis; architect read both stack
traces and the unchanged source methods. Preserve the real SCA fixtures and all37 approved tests.
Additional allowed production paths are moin/MoinIndex.java, ONLY its first method's selected-null
Optional handling, and bpm/ExpressionReader.java, ONLY literal's null-type membership guard.
MoinIndex.first must return empty when the first selected endpoint is null, while firsts/raw
evidence still retains that occurrence and a later endpoint is not substituted. Existing get
already uses the nullable Optional pattern. Do not widen this repair to second or other lookups.
ExpressionReader.literal must retain the raw literal value when type is absent/unknown without
calling the null-rejecting quoted-type set; known quoted/unquoted types stay as before. This
does not establish Boolean typing; the new report still returns unknown for such literals.
Apply and verify these two independent corrections one at a time using the respective already
observed-red test, then rerun all37 acceptance tests and affected legacy suites. Source/expectation
changes outside these exact guards require another explicit packet decision. See docs/findings/F-14.md.

## Architect test approval

Architect read both new test files in full, including all fixtures/assertion helpers, and the
complete additive native diff. Approved37 tests:31 extraction/graph cases,5 report/immutability/
JSON/determinism/Markdown cases,1 native registration case. Existing native assertions and
SyntheticBpmSca remain unchanged. Most cases parse literal MOIN SCA; the documented scope-cycle
case alone adds a graph link to captured evidence, with a five-second timeout. Legacy/unavailable
format cases replace the extension to verify that absence cannot create fabricated paths.
Author observed compile-red exit1/100 missing BpmPathReport/BpmPathAnalysis errors,0 tests reached;
target/mig-p3b-paths-red.log. Context7 calls and exact-JDK fallback are recorded under
target/mig-p3b-paths-context7-*.log. Independent red/scope verification precedes implementation.
Approved SHA256:
- BpmPathAnalysisTest: 51EE91268D155A7B244414108D67A4EEAA29C6084C1D967349FBD299C21D6437
- BpmPathReportTest: CB8B3A9E251EE6F7345060D6635557DC3F2AFC36C56F139DD6A7271F3748CB25
- NativeReflectionTest: 6D49039973AB69642D3D18DD1FCA711958EBB667C79B6F1D106687BFC64CA5A8
Approved tests are immutable; report a wrong expectation before changing it. Implementer runs
focused new and relevant legacy tests; independent verifier owns final root verify after docs.
Independent red confirmed exit1/100 missing API errors/0 tests;3 hashes match and production/POM/
SyntheticBpmSca diff from base is empty. Evidence target/mig-p3b-paths-red-verifier-summary.log
and target/mig-p3b-paths-red-verifier.log; no native tests reached in compile-red.

## Additional independent integrity coverage before review

Implementation self-review identified three adversarial forms needing explicit regression
assertions in addition to the unchanged37 initial red-first tests. The separate test-author may
write ONLY new bpm/BpmPathIntegrityTest.java: (1) boundary scope membership declared in a second
indexed partition must still conflict with its owner's scope; (2) a loop metadata relation with
a control node/scope in place of the loop owner must be treated as an unsupported control relation,
preventing a disconnected verdict; (3) a Pool declared as a scope member cannot supply validated
root ancestry, including root-pool membership inside its embedded child. Use synthetic SCA and
assert evidence/coverage/connectivity, not private implementation methods. Existing fixtures and
the three approved test hashes remain unchanged. No source mutations or weakened assertions.
These tests are post-implementation contract verification; report actual green/red honestly,
never relabel a green-first addition as an observed-red initial acceptance test.

Architect read BpmPathIntegrityTest in full and approved its3 assertions/fixtures after observed
green3/0/0/0, exit0, target/mig-p3b-paths-integrity.log. All use real PoXray.read; the root-Pool
cycle is safe because the old reader recurses only into EmbeddedScope. SHA256:
857BB5E64A3D4B28A12E27CD66807A4F21F7A97963B23207AF43BF9A99CA544A.
Total new tests40:37 initial red-first acceptance plus3 independently authored integrity checks.
Original three approved hashes remain unchanged; final code/docs review and root verification pending.

## Code review round1 — completeness regressions before correction

Independent review FAIL identified two implementation gaps in the existing contract, not a new
interpretation scope: a malformed CollaborationContainsPools link with f=current pool and
s=uniquely resolved wrong-kind current node is ignored; an unlinked Pool or Collaboration used
as a ScopeContainsScopeObjects member is silently discarded. Both can leave coverage complete
and create a false disconnected verdict. Preserve those source occurrences/relations, report
the gaps and retain unsupported scope-member objects as unknown steps per the classification rule.
The test-author may write ONLY new bpm/BpmPathCompletenessTest.java with three separate synthetic
SCA regressions for those forms. Assert public evidence, incomplete coverage and no false negative;
the Pool/Collaboration member cases also assert their retained unknown step identity. Do not require
a private implementation method or an unpromised exact diagnostic code. Existing four approved
test hashes remain immutable. Observe red against current implementation before correction;
architect reads the tests, then resumes the same implementer within existing graph paths.

Architect read the entire BpmPathCompletenessTest including fixtures/helpers and approves all3
regressions. Observed red:3 tests/3 failures/0 errors/0 skips, exit1; each source-evidence assertion
passed before expected incomplete versus actual complete. Log target/mig-p3b-paths-completeness-red.log.
SHA256 cb12ee7750822c89006947c1b82c2801d01d16b2af4303e84160e191e070a079.
Existing four approved hashes unchanged. All five files are immutable during correction.
Same implementer corrects only existing graph production paths, after independent red confirmation;
run these3 first, then combined graph/report/source/legacy/native suites. Same reviewer owns round2.
