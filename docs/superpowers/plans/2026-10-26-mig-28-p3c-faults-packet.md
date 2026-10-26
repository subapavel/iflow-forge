# MIG-28 — source-declared BPM event and fault contexts

Status: accepted for selective localcommit; code r2/docs PASS; independentroot569/0/0/26 exit0,36newtests. See PROGRESS for observed evidence and limits.
Base: 0c5c6a0b422caa87b6c22c969d33cc5efe2614db (MIG-27 accepted data report).
Owner narrowed execution to completing P3 and stopping; P4–P7 will not be implemented. This P3c1 slice precedes P3c2 configured
integration/composite CLI/MCP delivery. It does not complete P3 or resume MIG-21/MIG-22.

Read docs/CODEX-WORKFLOW.md first. Architect owns this packet/ledgers; separate test-author,
implementer, verifier and reviewers. One writer, at most two subagents, no nested delegation.
Use actual available model, inherited; record unknown if not known. No private fixtures/tenant
operations/deployment/push. All parser tests use real synthetic MOIN/SCA input.

## Purpose and evidence

Expose the source declaration chain from an activity, attached boundary or end event to its
event definition and WSDL fault reference, preserving occurrence identity, conditions, scope
and gaps. It is not runtime exception propagation, handler selection or interception proof.
The following composition slice will keep ICO ingress configuration separate from these rows.

Governing spec: retrospective-design §7; migration-process-improvements plan P3. Reuse the
accepted BpmSourceEvidence, BpmPathAnalysis and immutable package-private BpmPathGraph scope
view. Do not use the lossy Node.trigger/errorEvents or CatalogReader projections as authority.
Source-reading notes: target/mig-p3c-api-scout.log and target/mig-p3c-working-design.log.

Primary semantics consulted 2026-10-26:
- https://help.sap.com/docs/SAP_NETWEAVER_740/6fc9ea54f6d3425183718b944038b882/54bf98c82cd84614a85cfda25d70b175.html
- https://help.sap.com/saphelp_em92/helpdata/en/54/bf98c82cd84614a85cfda25d70b175/content.htm?no_cache=true
- https://help.sap.com/saphelp_em92/helpdata/en/47/61470236013df6e10000000a42189b/content.htm?no_cache=true
The two fault pages differ on noncritical subprocess runtime behavior. Preserve that limit;
do not invent a release-independent handler/termination rule. An error end is not proof of a
synchronous response. Declaration, structural attachment and observed execution stay distinct.

Before library-dependent code, resolve/query Context7 for the exact Java/Jackson/native APIs.
MIG-27 official OpenJDK25/Jackson/Quarkus evidence is available in its implementer Context7
logs, but each author must perform the required lookup before their own library-dependent edits.
No new libraries, language, framework or build configuration.

## Allowed paths

Test-author: new core bpm/BpmFaultAnalysisTest.java, BpmFaultReportTest.java and BpmFaultFixture.java;
additive NativeReflectionTest.java method/import only. Existing synthetic fixture helpers read-only.
All paths are beneath po-xray-core/src/test/java/io/poxray/ or po-xray-native/src/test/java/io/poxray/.
No changed/disabled old tests, hand-filled replacement for parsed source facts or source edits.

Implementer: new po-xray-core/src/main/java/io/poxray/bpm/BpmFaultAnalysis.java,
BpmFaultContext.java, BpmFaultDeclarations.java, BpmFaultEvidence.java and model/BpmFaultReport.java;
additive po-xray-native/src/main/java/io/poxray/nativeimage/NativeReflection.java registration.
Existing graph/data/source/parser classes are read-only. If a legacy reader fails before analysis,
stop with the exact trace for a narrow prerequisite packet amendment; do not work around the test.
No CLI/MCP/discovery change in this slice. Target evidence logs may use mig-p3c-faults-* only.
Documentation author later gets a separate packet; source writers do not edit docs/ledgers/git.

## Exact public interfaces

Pure core methods, no IO/clock/input mutation:
- BpmFaultAnalysis.of(List<ScaInventory> inventories, List<String> labels): BpmFaultReport
- BpmFaultAnalysis.markdown(BpmFaultReport report): String

Input contract equals BpmPathAnalysis: non-null inventories/labels, equal sizes, no null members;
otherwise IllegalArgumentException. Empty input is valid. Compute the path report once for
stable process addressing and scope admission; it is not duplicated inside this report.

New model records, listed in component/JSON order:
- BpmFaultReport(int formatVersion, String analysisVersion, List<Input> inputs,
  List<ProcessFaults> processes, List<Unattributed> unattributed, List<Issue> issues)
- ProcessFaults(ProcessKey key, String dc, String id, String name, String source,
  String coverage, List<Declaration> declarations, List<String> reasons)
- Declaration(int ordinal, String role, Endpoint owner, Endpoint event, Endpoint definition,
  List<Scope> scopes, List<Definition> definitions, Builtin builtin, List<Condition> conditions,
  Evidence evidence, String state, List<String> reasons)
- Scope(Locator owner, String state, String origin, List<Locator> path, List<Fact> basis,
  List<String> reasons)
- Definition(Element source, String kind, Flag critical, Flag technical,
  List<FaultReference> faults, Evidence evidence, List<String> reasons)
- FaultReference(int ordinal, Link link, String state, Evidence evidence, List<String> reasons)
- Builtin(String id, String kind, String name, boolean critical, boolean technical,
  Parameter parameter, String file, String sourceDc, String resource)
- Parameter(String name, String namespace, String type)
- Evidence(List<Fact> path, List<Element> elements, List<Link> links)
- Unattributed(int input, int component, String dc, String kind, Evidence evidence,
  List<String> reasons)

Input/ProcessKey/Flag/Fact/Condition are BpmPathReport types. Element/Link/Endpoint/Locator are
BpmSourceEvidence types. Other named types above are nested in BpmFaultReport. formatVersion=1,
analysisVersion=bpm-faults-v1. New records retain explicit null/empty fields and deeply immutable
snapshots preserving null entries and encounter order. Their required collection arguments reject
null; analysis itself supplies non-null lists. Reused Issue/Issue.Location omission is unchanged.

## Occurrence and attribution contract

Input/component/process keys are existing one-based original-list ordinals, including skipped null
component/process positions. Source locators are qualified by that key; never join on name or a
bare ID. Copy source issues, then path issues, then fault diagnostics in deterministic order.
Unavailable/unsupported source evidence or unresolvable process source yields unavailable with no
invented declarations. An incomplete path graph remains visible and makes fault coverage incomplete.

Use the validated scope view for positive process membership. Include inactive-pool contexts with
their reasons; do not assert active handling. Ambiguous owner endpoints touching several processes
produce uncertain candidate contexts in each, retaining all endpoint candidates. Never choose first.
Physical partition fallback is allowed only when exactly one interpreted process has that source;
it produces unknown ownership, not a proved scope. Ownership overrides mere physical co-location.
Shared/multi-process/uninterpreted metadata without attributable ownership stays in Unattributed,
qualified by original input/component and exact raw evidence, with BPM_FAULT_UNATTRIBUTED_SOURCE.

An unowned ErrorEventDefinition in a uniquely interpreted process partition becomes an
orphan-definition row with null owner/event/definition endpoints and unknown state. Its definitions
list contains that actual occurrence and Evidence.path includes its element Fact; there is no
synthetic globally unique endpoint. Duplicate-ID orphan definitions remain separate occurrence rows.
Unused external event definitions
remain component-level unassigned-definition evidence; they do not acquire a process by name.
Malformed/unattributed ownership links add component-wide incomplete uncertainty; an otherwise
unreferenced external definition alone does not claim a process fault or poison a proved local chain.

Declaration order: primary ownership links in source order; boundary attachment expands its matching
trigger links in source order; local orphan elements/remaining links follow source order afterward.
Ordinals start at1 per process, fault-reference ordinals at1 per Definition. Definition candidates
and raw payload lists follow source element order, not hash-map/endpoint lookup order. Separate
source links and shared definitions used by different owners remain separate contextual rows.

Scopes are per owner occurrence, in source element order: never concatenate two candidate paths.
Scope.owner is the candidate owner Locator; path runs from its immediate enclosing scope to pool,
inner first. state is active/inactive/unknown from the validated scope view. An unknown ancestry
has an empty path and explicit reasons; raw candidate memberships remain in basis/Evidence.
basis identifies every relevant original ScopeContainsScopeObjects/CollaborationContainsPools
link, ancestor Element and any ActivityHasBoundaryEvent link needed for derivation. origin is
explicit-membership, attachment-derived, mixed or unknown; it describes the actual basis used,
not runtime support. Preserve those full raw payloads in Declaration.evidence because paths are
not embedded. For boundary-trigger scopes refer to the activity owner, not an invented boundary
scope. No owner endpoint gives an empty scopes list with unknown-owner reason. Several owner
candidates in different scopes give separate Scope rows and unknown declaration state.

## Supported declaration grammar

Use these exact normalized directions:
- ActivityContainsEventDefinitions: f=definition, s=activity. Role activity-definition.
- ActivityHasBoundaryEvent: f=boundary, s=activity; then CatchEventHasTrigger:
  f=definition, s=boundary. Role boundary-trigger, owner=activity endpoint, event=boundary endpoint.
- CatchEventHasTrigger on a StartEvent: role start-trigger, owner=start, event=null.
- Other catch owners: unsupported-catch-trigger, owner=raw catch endpoint, event=null, unknown.
- ThrowEventHasResult: f=definition, s=EndEvent. Role end-result, owner=end, event=null.
- ErrorEventDefinitionHasWSDLFault: f=fault, s=definition; retained per Definition.

For activity-definition, known owner classes are AutomatedActivity, MappingActivity,
NotificationActivity and EmbeddedScope. This validates only the source declaration shape,
never runtime support of a boundary on that kind. Owner must resolve uniquely with valid ancestry
for declared state. Boundary event must be BoundaryEvent and resolve uniquely; its attachment
must be unique. Each boundary catch/start/end allows one declaration link; multiple links remain
separate unknown rows with ambiguity reasons. An activity may declare several distinct definitions.
Repeated identical ownership declarations remain visible and receive duplicate-declaration reason.

Match links through endpoint occurrence candidates. A nonunique attachment, wrong-kind endpoint,
cross-process conflict or missing reference remains an unknown row with all raw facts. When an
attachment has no usable trigger, emit a boundary-trigger placeholder with null definition and
missing-trigger reason. A boundary trigger without an attachment is unsupported-catch-trigger with
an orphan-boundary-trigger reason. Do not silently omit a missing/ambiguous boundary or definition.
For ambiguous boundaries, retain every touching trigger context, with unknown association and the
full original attachment endpoints; do not turn one matching candidate into a resolved attachment.

Each included definition candidate gets its own Definition row, own raw flags and fault links.
kind is error for the evidenced Galaxy.ERROR_EVENT_DEFINITION constant and message for
Galaxy.MESSAGE_EVENT_DEFINITION; all other source kinds, including timer-looking names, are
unknown. Catalogue kind may still be timer as a catalogue fact. Known non-error declarations
with a valid chain are retained as non-error state and never treated as handlers for an error. A wrong/unknown kind
is unknown. No representative critical/technical value is chosen across ambiguous definitions.
Read attributes isCritical/isTechnical with exact raw presence/value: true/false only for those
literal tokens, otherwise unknown. Missing is not false. Missing flags alone do not imply a missing
source association; their interpretation remains unknown even when extraction is complete.

Only a definition Endpoint with resolution=missing, zero source candidates and an exact ID in
BuiltinFunctionCatalog may carry Builtin fallback metadata. Preserve the original missing source
endpoint and catalogue provenance (resource/file/sourceDc); flags are catalogue facts, not exported
attributes. Any source candidate, including excluded or ambiguous payload, prevents this fallback.
No name-based fallback or projected Node.builtin authority. Recognized catalogue rows have state
catalogue; source rows have declared/non-error/unknown as above. No runtime catch inference.

State precedence is strict: any ownership/attachment/scope/definition ambiguity, malformed
required association, unknown source kind or unsupported relation gives unknown, even when a
candidate is a known message or catalogue trigger. Preserve all candidate metadata nonetheless.
Only otherwise valid source chains use declared for error or non-error for message. Only an
otherwise valid missing-source chain with permitted exact-ID fallback uses catalogue. Catalogue
substitution adds catalogue-source-substitution and keeps process coverage incomplete; retain
the original missing-source diagnostic. Missing flag interpretation, optional absent fault links
and fully retained unevaluated conditions alone are semantic limits, not malformed required chains.

## Fault, condition and evidence payload

FaultReference retains the original ErrorEventDefinitionHasWSDLFault Link. State is included for
one included WSDL2.Fault candidate, identity-only for one excluded WSDL2.Fault candidate, missing,
ambiguous, wrong-kind or unknown otherwise. Keep every candidate; identity-only is not missing and
not a complete contract. An absent association means no fault reference was declared in the retained
evidence, never that the activity cannot fail or that its WSDL has no faults.
Retain any available direct WSDL2.ReferenceToElement links (f=fault,s=XSD element) and candidate
payload, without recursive WSDL/XSD traversal, schema resolution or inferred QName. Excluded payload
and malformed direct references stay explicit gaps. EventDefinitionHasParameter links and their
direct included payloads are retained likewise. P4 owns contract/import completeness.

CatchEventContainsExpression uses f=event,s=expression. For each link retain one Condition with
state not-evaluated for a unique Expression payload, unknown for missing/ambiguous/wrong-kind facts;
text is the raw stringExpression attribute, otherwise same-named text, with no text evaluation.
Absence gives an empty conditions list meaning none declared, not true. Do not prune even a literal
false condition. The declaration Evidence retains the raw condition link, expression and reachable
syntax facts, so Condition.evidence Fact locators are recoverable without the original inventory.

Evidence.path records the contextual ownership/attachment/trigger/definition/fault/condition walk;
raw elements/links are deduplicated only by their occurrence Locator, in source order. Retain every
candidate payload actually selected by bpm-source-v1, including ambiguous alternatives.
For condition syntax, follow the declared child direction of ExpressionHasSteps, ReferenceHasQName,
ReferencePointToXsdFeature, LiteralHasSimpleDataType, FunctionInvocationHasFunctionInterface,
FunctionInvocationHasParameterMapping, MappingContainsMappingParts, MappingPartHasChildren,
MappingPartHasQName, MappingPartContainsExpression and ConditionHasConditionExpression/
ConditionHasPositiveExpression/ConditionHasNegativeExpression. Stop at QName/type/feature/function
interface payload; never follow function bodies or external resources. Known child links use f=child,
s=parent. Cycles terminate by occurrence and remain visible; do not impose the legacy64 cutoff.
Unknown incident rule/mapping links remain evidence with unsupported-relation uncertainty and no
invented traversal semantics. Missing/excluded syntax payload stays explicit, independent branches
remain retained. This is bounded raw retention, not a second expression interpreter.

Account for every relevant unconsumed source item after normal chains, without double-discarding
facts shared by several contexts:
- A CatchEventContainsExpression link not consumed by a trigger declaration creates a
  catch-condition-only row. owner is its original first endpoint; event/definition are null;
  conditions and raw closure are retained. It is unknown with condition-without-declaration,
  never an inferred handler. Apply the same positive-ownership/physical-fallback attribution rules.
- An ErrorEventDefinitionHasWSDLFault or EventDefinitionHasParameter link not consumed by any
  Definition creates an orphan-fault-reference or orphan-parameter-reference row when attributable
  to exactly one process. owner/event are null; definition is the original second endpoint;
  evidence retains both endpoints and included candidates. State is unknown. An included candidate
  definition is still represented even when wrong-kind; no null endpoint is repaired by name.
- Unknown Galaxy.Workflow relations incident on a retained definition occurrence are added to
  every corresponding contextual Definition/Declaration evidence with unsupported-workflow-relation;
  declaration state and process coverage become unknown/incomplete. Do not invent endpoint roles.
  If no contextual row exists, use an orphan-metadata row with null owner/event/definition and the
  original link in Evidence, or Unattributed when the process cannot be selected.
- Unreferenced source ErrorEventDefinition/MessageEventDefinition and other Workflow metaclasses
  ending in EventDefinition are retained as orphan/unassigned definitions. That suffix is only an
  evidence-retention filter, never semantic kind recognition. Unknown kinds remain unknown.
All unconsumed links follow source order after primary declarations and local orphan elements.
Shared/ambiguous/uninterpreted ownership for these links creates Unattributed(kind equal to the
role above), BPM_FAULT_UNATTRIBUTED_SOURCE and component-wide incomplete uncertainty. Merely
unreferenced external definition Elements remain the non-poisoning exception stated earlier.

Coverage complete means all attributable retained declarations fit the supported source grammar and
no graph/ownership/payload gaps remain; incomplete preserves known rows plus reasons; unavailable is
inadmissible source. Complete never means whole archive/runtime coverage. Missing flag interpretation,
none-declared optional fault links and unevaluated but fully retained conditions remain explicit
semantic limits without falsely requiring an association the source never declared. Unattributed
metadata and legacy diagnostics stay visible. Fault IDs/names, matching payloads, lexical scopes and
boundary arcs alone establish no handler priority, propagation, interruption, retries, transactions,
loop instance, synchronous response, acknowledgement, termination or actual rejection.

## Acceptance behaviours / proposed test names

Real synthetic SCA fixtures must cover these independently observable failures:
1. activityDeclarationsDoNotBecomeAttachedHandlers — distinct activity/error and boundary contexts.
2. boundaryChainRetainsOwnerEventDefinitionAndScope — nested scope, full ordered raw chain.
3. sameDefinitionUnderTwoOwnersKeepsSeparateContexts — no global-ID dedup.
4. endResultIsNotAReplyOrAHandler — error end versus message definition retained distinctly.
5. startTriggerAndUnsupportedCatchStayOutsideAttachedFaultScope — exact source owner kinds.
6. criticalTechnicalPresenceNeverDefaultsToFalse — true/false/missing/invalid raw flags.
7. ambiguousDefinitionsKeepEveryCandidateAndItsOwnFlags — duplicate IDs across partitions.
8. builtinFallbackRequiresExactMissingIdAndKeepsCatalogueProvenance — actual catalogue ID.
9. exportedOrAmbiguousDefinitionNeverFallsBackToBuiltin — source wins, no name guessing.
10. excludedFaultPayloadKeepsIdentityWithoutInventingContract — WSDL fault outside process.
11. includedFaultAndParameterKeepDirectPayloadWithoutSchemaInference — direct source evidence.
12. missingAmbiguousAndWrongKindFaultsRemainDistinct — original endpoints and all occurrences.
13. malformedAttachmentsAndMissingTriggersNeverDisappear — owner/boundary/trigger gaps.
14. multipleAttachmentsAndTriggersNeverChooseFirst — all contextual possibilities unknown.
15. catchConditionRetainsFalseAndOpaqueSyntaxWithoutPruning — raw condition tree and unknown text.
16. conditionClosureTerminatesCyclesAndRetainsIndependentBranches — deep/shared arguments, no64cap.
17. orphanDefinitionsAndLinksRemainQualifiedWithoutCrossBinding — local versus shared metadata.
18. duplicateInputAndProcessIdsStayOccurrenceQualified — repeated labels/IDs do not merge.
19. incompleteGraphAndLegacyEvidenceNeverClaimCompleteFaultCoverage — unavailable and unknown.
20. malformedMessageAndBuiltinChainsKeepUnknownPrecedence — valid kind does not repair attachment.
21. conditionWithoutTriggerAndDanglingFaultParameterLinksStayVisible — no complete empty report.
22. unknownWorkflowRelationOnDefinitionPreventsCompleteCoverage — retain both raw endpoints.
23. ambiguousOwnersKeepSeparateScopePathsAndMembershipEvidence — no concatenated singular scope.
24. duplicateOrphanDefinitionsNeverClaimGloballyUniqueEndpoints — null endpoint, actual occurrences.

Report tests: invalidArgumentsAndEmptyInput; deepImmutableEvidenceSnapshots; jsonKeepsNullEmptyAndRawFlags;
repeatIsDeterministicAndInputsUnchanged; markdownEscapesValuesAndNamesSemanticLimits.
Native reflection registration test is additive and checks the outer record and every nested type.
Exact methods may split closely related cases where assertions remain clear; architect reads all
written tests/helpers before approval. Red is missing introduced API compile error or failing
behaviour assertion, never skipped tests, infrastructure failure or swallowed parser exceptions.

## Verification and review

Test-author records Context7, observed red and SHA256. Independent verifier confirms approved red.
Implementer keeps approved hashes, runs focused new tests plus existing path/data/source/native
tests, logs observed counts/exits. Independent reviewer evaluates every test and concrete malformed
source cases, all blockers in one round; second FAIL requires a revised packet.
After docs, independent verifier runs full root mvn -B -ntp verify, records module totals and actual
skips, hashes, source links, knowledge citation/budget/mirror checks and scoped privacy/whitespace.
No new CLI command means no new CLI smoke until P3c2. Native executable/full forbidden-values may
be NOT_RUN only with actual environment reason; existing gated skips never become covered claims.
Only then selective local commit excluding unrelated governance/pilot work and subsequent drafts.

Preflight r1 found five contract gaps: unsupported timer source kind, state precedence, unconsumed
metadata, ambiguous owner scopes and orphan endpoint derivation. Revision2 closes all five;
same reviewer r2 PASS. No owner reconfirmation is needed inside the approved P3 scope (D-053).

Architect test approval: all24 analysis tests,5 report tests, fixture and additive native test read in full.
Frozen manifest target/mig-p3c-faults-test-author-hashes.log;30 new tests, native14 additions0 deletions.
Author observed missing-API compilation failure; numeric exit unavailable from truncated completion,
so independent verifier must capture it before implementation. No runtime/fixture pass is claimed.

## Code review r1 — completeness regressions before correction

Independent r1 FAIL names four static counterexamples; runtime red is not yet claimed.
Separate test-author may add ONLY core bpm/BpmFaultCompletenessTest.java and designated target logs.
Original30 tests and all helpers remain frozen. Architect reads/approves the new file and independent
verifier observes assertion-red before the same implementer corrects source. No weakened assertions.

1. Condition closure validates BOTH parent and child kinds for every supported association.
   f=child,s=parent; supported grammar below. Any mismatched candidate or nonunique/missing/excluded
   endpoint remains raw evidence plus condition-syntax-payload-unknown/unsupported-shape uncertainty;
   process coverage must be incomplete. Known syntax metaclass alone never validates a wrong role.
   Tests cover ReferenceHasQName to MappingPart, MappingContainsMappingParts to Condition, and a
   QName relation owned by a Literal rather than Reference. Retain original links/candidates and
   an independent valid branch. No function-body traversal or depth cap is introduced.
2. A malformed ActivityHasBoundaryEvent(f=start,s=activity) must not suppress the independent
   StartEvent CatchEventHasTrigger. Keep start-trigger plus the unknown malformed boundary row.
3. Boundary membership conflicting with its attached activity's ancestry, even within one process,
   yields unknown boundary declaration. Retain event membership links and their candidate scope
   payload in evidence; owner scopes still refer to the activity. Nested fixture: boundary in pool,
   attached child-map inside EmbeddedScope; accepted path reason conflicting-boundary-scope remains.
4. An unconsumed catch-condition link attributable to multiple process candidates is Unattributed
   kind=catch-condition-only, with BPM_FAULT_UNATTRIBUTED_SOURCE and component-wide incomplete
   uncertainty. Do not duplicate it into locally attributed declaration rows. Use two real process
   partitions with distinct roots and duplicate catch IDs, not hand-filled replacement source facts.

Bounded condition grammar (parent -> child):
- ExpressionHasSteps: Expression -> Reference/Parent/Literal/FunctionInvocation/Condition.
- ReferenceHasQName: Reference -> XSD1.key.QName.
- ReferencePointToXsdFeature: Reference -> XSD1.XsdElementDeclaration; other feature metaclasses
  remain retained unsupported-kind uncertainty in this version, not guessed supported shapes.
- LiteralHasSimpleDataType: Literal -> XSD1.XsdSimpleTypeDefinition.
- FunctionInvocationHasFunctionInterface: FunctionInvocation -> FunctionInterface.
- FunctionInvocationHasParameterMapping: FunctionInvocation -> Mapping.
- MappingContainsMappingParts: Mapping -> MappingPart.
- MappingPartHasChildren: MappingPart -> MappingPart.
- MappingPartHasQName: MappingPart -> XSD1.key.QName.
- MappingPartContainsExpression: MappingPart -> Expression.
- ConditionHasConditionExpression/PositiveExpression/NegativeExpression: Condition -> Expression.
All Rule/Mapping metaclass prefixes are Galaxy.Rule/Galaxy.Mapping as already defined by Galaxy.
This is source syntax retention, no evaluator or new schema completeness claim.
Expected new regressions6 (three grammar variants plus three context defects); split if needed to
keep one clear observable failure per test. Same implementer correction limited to BpmFaultEvidence
and BpmFaultDeclarations; report unexpected prerequisite before changing other files.
