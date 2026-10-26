# MIG-29 — P3 configured integration and offline behavior report

Status: ACCEPTED2026-10-27; code reviewr2/finaldocs PASS,54new tests pass. Independent root623/0/0/26,exit0; fresh packagedCLI8/8. P3 complete; stop perD053.
Base: 3e2b716a5ca43a81a39b31ac234715c5c81fd55f (accepted MIG-28; independentroot569/0/0/26).
Scope D-053: complete P3 and stop. No P4–P7, no MIG-21 discover command or MIG-22 campaign.
Read docs/CODEX-WORKFLOW.md first. Separate test author, one writer, no nested delegation.

## Purpose and sources
Compose accepted source paths/data/fault declarations with existing ScenarioDiscovery and original
ICO/channel configuration. Distinguish configured delivery before process entry from process fault
declarations, without inferring observed rejection, handler invocation, acknowledgment or transactions.
Basic interface operations/faults and service references remain source projections; P4 owns recursive
schema/import/resource contracts. Existing reports and parser meanings remain unchanged.

Governed by migration-process-improvements P3 and retrospective-design §7.
API source notes target/mig-p3c-api-scout.log and working-design.log. Existing ScenarioDiscovery
is authoritative for catalog-interface/ICO matching; do not replace it.
Primary SAP configurable no-receiver policy (version-specific, not observed rejection):
https://help.sap.com/docs/SAP_NETWEAVER_750/d0a0a7cb51dc40529bfcac724dd05796/48cfac3c9bf23e49e10000000a421937.html
Fault semantics references are in MIG-28 packet; no cross-version propagation inference.

Before library-dependent edits each author resolves and queries Context7 for Java25, Jackson3.2.3,
JUnit6.1.3/AssertJ3.27.7, Quarkus3.40.1/Aesh/MCP APIs actually touched. No dependencies/POM changes.

## Allowed paths
Tests: new core migration/BehaviorAnalysisTest.java, BehaviorReportTest.java, BehaviorInputsTest.java
and public io/poxray/SyntheticBehavior.java; optional separate BehaviorChannelsTest.java and
BehaviorCallsTest.java; separate BehaviorCompletenessTest.java for review regressions below;
new CLI BehaviorCommandTest.java and MCP BehaviorToolTest.java; additive
NativeReflectionTest.java method/import. Existing helpers/tests read-only. Use real SyntheticBpmSca
and SyntheticTpz through actual readers for behavioral fixtures; hand-built records only for
immutability/admission/legacy missing-field and projection ambiguity variants.
Implementation: new core migration/BehaviorAnalysis.java, BehaviorInputs.java, BehaviorDiscovery.java,
BehaviorChannels.java, BehaviorCalls.java; model/BehaviorReport.java and public final
BehaviorSnapshots.java (private constructor, package-private copying methods; native registration only).
Additive native NativeReflection.java registration; new CLI BehaviorCommand.java,
additive PoXrayCommand.java registration; additive offline tool in MCP PoXrayTools.java.
Existing parsers/data/fault/path/ScenarioDiscovery/Reports/json/call behavior read-only.
Logs target/mig-p3c-behavior-* only. Stop with exact trace if legacy prerequisite fails.
Docs use separate packet: OUTPUT-MODEL, canonical references/bpm.md and intake.md, README and
CLI/MCP existing tool listing if needed; no skill frontmatter/mirrors edit or P4 claims.
Architect owns all packets/ledgers/git. No tenant/API/deploy/push/private input operations.

## Exact public API and records
migration.BehaviorAnalysis:
- of(List<ScaInventory> inventories, List<String> labels, IntegrationInventory icos,
  String icosLabel): BehaviorReport
- read(Path exportFileOrDirectory, Path icosJson): BehaviorReport throws IOException
- markdown(BehaviorReport report): String

Model records component order:
- BehaviorReport(int formatVersion, String analysisVersion, BpmDataReport data,
  BpmFaultReport faults, DiscoveryReport discovery, Integration integration,
  List<ProcessWiring> processes, List<Issue> issues)
- Integration(String input, String schemaVersion, String availability, String selection,
  IntegrationInventory.Source source, IntegrationInventory.Query query,
  List<IcoSource> icos, List<ChannelSource> channels, List<ChannelUse> channelUses,
  List<Issue> issues, List<String> reasons)
- IcoSource(int ordinal, IntegrationInventory.Ico configuration, List<String> reasons)
- ChannelSource(int ordinal, IntegrationInventory.Channel configuration, List<String> reasons)
- ChannelUse(int ordinal, int ico, Integer receiver, Integer assignment, String role,
  String parameter, Integer reference, IntegrationInventory.DirRef source,
  List<Integer> candidates, String resolution, String association, String direction,
  List<String> reasons)
- ProcessWiring(BpmPathReport.ProcessKey key, String state, List<InterfaceUse> interfaces,
  List<StepCall> calls, List<String> reasons)
- InterfaceUse(int ordinal, String direction, String context, DiscoveryReport.InterfaceRow source,
  List<InterfaceCandidate> candidates, List<Integer> steps, List<IcoMatch> matches,
  String state, List<String> reasons)
- InterfaceCandidate(int ordinal, Interface source)
- IcoMatch(int discovery, Integer ico, List<Route> routes, String state, List<String> reasons)
- Route(Integer receiver, Integer assignment, Integer target, String relation, List<String> reasons)
- StepCall(int step, BpmSourceEvidence.Locator location, List<BpmSourceEvidence.Link> relations,
  List<BpmSourceEvidence.Element> elements, List<Integer> interfaces, List<ServiceCandidate> services,
  String state, String acknowledgment, String transaction, List<String> reasons)
- ServiceCandidate(int ordinal, ServiceReference source, String state, List<String> reasons)

formatVersion1, analysisVersion=bpm-behavior-v1. data embeds paths once; faults independent.
All new record collections required/non-null; deeply immutable snapshots preserve null entries and
encounter order, including nested legacy ICO/channel/interface/service/discovery records, maps
and Source.files. Preserve legacy null versus empty collection fields and omission semantics.
New records explicit null/empty JSON. Existing Issue omission unchanged.
Legacy JSON map keys remain non-null strings (including empty strings where source permits); null
map values and list holes are preserved. New snapshot constructors reject a null map key with
IllegalArgumentException rather than invent a serializable name or silently drop it. Analysis/read
never produce null keys from JSON. Add a constructor rejection test alongside deep immutability.
No newly generated analysis time in report; original source fields such as ICO modifiedAt remain.
ScenarioDiscovery.of internally reads a clock; its copied report has
generator=null, removing volatile analysis metadata. Do not claim no internal clock invocation.
No mutation of source inventories/configuration; repeated of on identical inputs serializes identically.
Top issues append data.issues, faults.issues, discovery.issues, integration.issues, composition/read
diagnostics in that order; duplicates from child reports are retained, not proof of independent faults.

## Admission and offline input
of validates inventories/labels exactly as paths: non-null, equal size, no null members.
Empty valid. icos may be null, label may be null; retain original provided label.
ScaInventory0.1 or null legacy schema allowed; explicit other schema throws IllegalArgumentException
before analysis (unsupported shared inventory semantics). Legacy absent source is reported unavailable
by accepted data/fault helpers; do not synthesize source facts.
read requires non-null existing regular file or directory; missing/unreadable root throws IOException.
Directory reads direct regular *.json case-insensitive suffix, lexicographic filename order; no recurse.
Each unreadable/malformed/null/non-SCA (both bpm/ejb null)/unsupported schema export is skipped with
BEHAVIOR_INPUT_UNAVAILABLE warning naming file/reason. Empty usable set returns empty report with
BEHAVIOR_NO_USABLE_EXPORT warning, including empty directory; no false complete analysis.
Labels are original path strings. No automatic exports or network reads.

Optional ICO:
- null path/object: availability=absent, selection=unknown, original label retained.
- read/parse/null-document failure: unavailable, BEHAVIOR_ICO_UNAVAILABLE.
- object with icos=null: missing-icos; retain Source/Query/channels/config snapshot, no matching.
- explicit schema other than0.1/null: unsupported-schema, retain snapshot, no matching/channel derivation.
- otherwise available; empty icos means observed empty collection, never entire system empty.
schema=null is admitted legacy with legacy-schema reason and selection unknown unless limited below.
selection=query-limited if Query has any nonblank selector; otherwise source-issues if any nonnull
source issue; otherwise unknown. Query and Source never prove whole-system completeness.
Preserve all Integration source issues in Integration.issues, including null entries.
availability and selection are independent; absent/missing/unsupported states never become no-match proof.
Pass supported available ICO to discovery, else null. Keep derived unmatched rows labeled unavailable.
No reading source URLs or importing credentials.

IcoSource/ChannelSource for EVERY original index, including null configuration placeholders, one-based
ordinal; null reasons missing-ico/missing-channel. Source snapshots retain all Receiver conditions,
QoS, interface assignments, target multiplicities, mappings/parameters, dynamicRouting, noReceiverBehaviour,
channel attributes/modules. These are projections, not byte-exact source; dropped fields/defaults in
old readers stay unknown. No esr code/resource catalog needed in this slice.

## Discovery projection and process identity
Call ScenarioDiscovery.of exactly for matching. Copy its result deeply with null generator.
ProcessRows correspond to original nonnull input/component/process encounter order; validate count,
file label, dc, id and name against data.paths.ProcessKey. Mismatch gives unavailable ProcessWiring
with reason discovery-process-mismatch, never shift or join by names to recover.
Keep per-process original key, even duplicate IDs/names/labels and null list gaps.
InterfaceUse order is inbound rows followed by outbound rows; ordinal1 perprocess.
Reverse interface candidates using exact name/namespace and usage.process=process.name and exactrole,
preserving ALL original catalogue indices. This only recovers candidates from a lossy projection:
state=candidate when exactly1 valid projection, ambiguous when several, unavailable without source;
never state bound/confirmed. null namespaces match only as existing discovery does, not QName proof.
context=start -> pre-process; intermediate -> existing-instance; outbound/notification -> process-outbound.
Unknown usage roles retain legacy discovery diagnostics, not invented valid direction.
Candidate Interface retains operations/MEP, input/output/fault QName projections and all usages.
Candidate usage.node.ref identifies all matching path step IDs within exact key; no name fallback.
Missing/null/nonunique refs retain reasons; all candidate step ordinals in pathorder, no choose-first.

Each InterfaceUse.source.icos row has its own 1-based discovery ordinal. Reassociate its WHOLE
summary (oid, sender party/component/interface/namespace, sorted distinct nonnull receiver components)
against all original ICO configurations. For every matched original index produce IcoMatch;
duplicate/null OID cannot select first. Multiple source matches state=ambiguous; one=candidate;
none => ico=null,state=unavailable. Row coincidence is still a coarse candidate.
For inbound routes enumerate ONLY original receiver/assignment/target triples whose target interface
exact name/namespace matches InterfaceUse.source; relation=interface-candidate. Incomplete/unmatched
other branches remain in IcoSource.configuration, with incomplete-other-route reason on IcoMatch;
never make their placeholder an interface-candidate. Outbound enumerate all original receiver/
assignment/target combinations relation=configured-candidate. Missing/null/empty nested structures
retain placeholder indices (null for absent/empty list; originalordinal for nullentry), relation=unknown
and unknown-route reasons. Source snapshots retain every branch.
InterfaceAssignment.operation is SENDER operation; differing target BPM operation is not a mismatch.
Receiver conditions including literalfalse remain configuration, never evaluated or pruned.
No-receiver error policy stays raw configuration, separate from BPM faults; no observed rejection,
correlation delivery, dynamic target, transaction, acknowledgement or runtime fault inference.
A routing Issue can mean dropped unresolved condition alternatives; preserve global uncertainty,
do not recover authoritative route identity by parsing diagnostic prose.

## Channel reference resolution and association
Enumerate source uses in ICO order: sender-channel once even null; receivers in sourceorder with
each receiver.channels entry, placeholder for null/empty list; each interface-assignment parameters
entry in source map order (null/empty map remains raw snapshot, no fabricated parameter).
role sender/receiver/parameter; reference index for receiver list or parameter entry, null for sender.
receiver/assignment indices original one-based; parameter key retained as its original string.
candidates are original ChannelSource ordinals, preserving all matches.

If source channels=null, channel catalogue is unavailable: every source use has resolution=unavailable,
no candidates, direction/association unknown except parameter's not-transport/not-applicable.
If channels=[] it is an observed empty catalogue: nonblank OID or complete key gives missing,
partial/selectorless reference stays unknown with observed-empty-catalogue reason; neither
state proves whole-system absence. Add channels-unavailable or channels-observed-empty to Integration.reasons.
For unsupported-schema no derived channelUses are created. Null channel entries are retained snapshots,
never matches. For all remaining supported cases use the exact precedence below.
Identity: nonblank ref.oid selects ALL exactOID candidates, never falls back byname.
Corroborate every supplied nonnull party/component/name against candidate id. Mismatch conflict.
No OID: supplied nonnull fields filter compatible channel ids; complete key requires nonnullparty
(empty party meaningful) and nonblank component/name. Complete exact single candidate -> unique;
multiple -> ambiguous; none -> missing. Partial -> unknown even one candidate. Null/no selector ref
-> unknown with no arbitrary all-channel binding. Candidate null ID cannot match.
Resolution precedence: no usable ref => unknown; OID/completekey with no candidates => missing;
OID multiple => ambiguous even if other supplied fields conflict (keep reference-identity-conflict reason);
OID single with supplied-field mismatch => conflict; OID single fully corroborated => unique;
noOID partialkey => unknown regardless count, otherwise completekey one/multiple => unique/ambiguous.
No elimination silently repairs duplicate identity.
association and direction are separate. Parameter always not-transport/not-applicable.
Receiver.party/component may be literal or dynamic extractor text; current model lost that distinction.
Therefore receiver association is ALWAYS unknown with receiver-identity-projection reason, even if
text agrees/disagrees with channel identity. Preserve both strings; do not infer extractor kind from
syntax, conflict or compatible transport from them, or expand parser scope. This also prevents the
legacy component-only channel association from becoming certain. Reference identity conflict remains
independently visible in resolution/reasons. Sender owner fields are Directory key projections:
for unique resolution only, supplied known party/component mismatch => association conflict;
otherwise missing owner/candidate identity value => unknown; all known matching => compatible.
For sender nonunique/conflict/unavailable/missing resolution association=unknown; preserve reason
candidate-owner-conflict if any inspected candidate disagrees, without selecting a representative.
Transport direction aggregate is compatible only for unique identity and exact matching direction;
conflict only for unique identity and exact contrary sender/receiver value; otherwise unknown.
For ambiguous/partial/conflicting candidates direction=unknown, retaining candidate-direction-conflict
if any contrary value and candidate-direction-unknown for absent/unrecognized metadata. Never collapse
mixed candidate outcomes to a chosen channel. No candidate => direction unknown.
Missing channel prevents any certain target claim; module position and adapter attrs remain configured
values, not execution/ordering/ack proof.

## Step call metadata
One StepCall per accepted path Step, same ordinal/location order, including disconnectedsteps.
From retained raw evidence match directed ActivityHasOperation and AutomatedActivityHasServiceReference
f=target/s=activity through occurrence candidates; retain full rawlinks and all included endpointpayload.
CatchEventHasTrigger f=definition/s=catch and incident MessageEventDefinitionHasOperation links retained
as twohop evidence for candidate catchstep. Legacy reader accepts both operation-link directions:
keep raw endpoints and unknown-operation-direction, never invent a normalized target.
Raw lists dedup by occurrence only, sourceorder. Missing/ambiguous/excluded endpoints are retainedgaps.
Supported direct grammar: ActivityHasOperation owner is Galaxy.Workflow.AutomatedActivity or
Galaxy.ProcessSupplements.NotificationActivity, target WSDL2.Operation. AutomatedActivityHasServiceReference
owner is Galaxy.Workflow.AutomatedActivity, target mc.servicereferences.ServiceReference. All other kinds
unknown, including MappingActivity carrying these relations. Each owner allows at most one operation
link and at most one service-reference link; repeated same link or multiple distincttargets is unknown.
A direct declaration needs unique included owner and target of supportedkind; source-declared is
source association only, not runtime invocation. All endpoint candidates/payload remain retained.
Catch operation evidence requires StartEvent/BoundaryEvent + MessageEventDefinition for knownshape;
otherkind with incident operation link is unknown, no guessed intermediate metaclass. Any incident
MessageEventDefinitionHasOperation remains unknown-operation-direction in this version. A message
trigger without a retained operation relation is unknown with missing-message-operation. Non-message
triggers alone are not call evidence (their declarations are in faults).
Interface indices refer to InterfaceUse.steps candidates; no proof of runtime call from catalogue.
ServiceCandidate: only raw service-target candidate ID exactequals source ServiceReference.id; all
catalogindices, never UUID/name fallback. state=candidate for single projectedmatch, ambiguous formultiple,
unavailable if source targetexcluded/missing or no matchingcatalog. Keep rawlink even no ServiceCandidate.
Each direct service relation targetgap adds reason; services never take first representative.
State precedence: unavailable/unsupported source => unknown, never no-interface-call-observed;
ANY malformed owner/target, multiplicity, missing/excluded/ambiguous required endpoint or unverified
operation direction => unknown even alongside a valid direct link or cataloguehint. Otherwise at least
one supported direct activity link => source-declared; only catalogue interface hints => candidate;
no call evidence/hints with admissible source => no-interface-call-observed. Never infer no expression/function
sideeffects from that last state. acknowledgment=unknown and transaction=unknown for every step;
existing async/pattern/module config is not sufficient proof.
ProcessWiring.state=candidate when admissible source and stable discoverymapping, unavailable otherwise;
gaps aggregated in reasons, never complete runtime meaning. Data/fault reports supply separate coverage.

## CLI/MCP and Markdown
CLI: po-xray behavior <export-dir-or-file> [--icos <inventory.json>] [--json].
Existing ScaService.run error/output conventions. JSON prints full BehaviorReport; default Markdown
uses same report, includes paths/data/faults plus configuredintegration/steps/channel uncertainty andissues.
Register additive command. Optional ICO failures are diagnostic report/CLI success; missing exportroot
is nonzero. Emptyinput explicitwarning. No live services, credentialflags or hiddenexports.

MCP: public String bpm_behavior(String exportPath, String icosPath) with @Tool/description and
@ToolArg/defaultValue="" for optionalicosPath following existing local tool pattern.
Same BehaviorAnalysis.read, existing call/json envelope with80000 threshold; no truncation/new spooler.
Tool name bpm_behavior. Missingroot ToolCallException; optionalbadICO diagnostic report.
Tests actual tool content inline and real spilled JSON envelope, reading resultfile and checking no
truncation/inputmutation. No live serverrestart required for codeacceptance.

Markdown escapes pipe/newline in all source-derived tablecells, includes key/coverage/candidates/gaps,
configured pre-process vs existinginstance vs processoutbound, faultsource sections and runtime limits.
Reuse data/fault markdown only if escaping preserved; do not use unsafe raw discoverymarkdown blindly.
Explicit unknown acknowledgment/transaction; no assertions about actual production.
Native reflection registers outer and all new nestedrecords; no nativeexecutableclaim without run.

## Acceptance test outline
Real synthetic parsed source/TPZ pair, no private customerfixtures:
1 composeAcceptedReportsKeepsStructuralPathsDataFaults — exact childcontent, no erased gaps.
2 ingressPolicyIsSeparateFromBoundaryFault — configurednoReceiver, pre-process interface + boundary.
3 intermediateIngressIsExistingInstance — legacy Usage.role fixture may wrap parsedcatalogue.
4 discoveryMatchesAreReusedAndGeneratorRemoved — unchangedmatching rows sans volatilegenerator.
5 duplicateLabelsIdsAndNullGapsKeepProcessKeys — exact key, no crossbinding.
6 duplicateIcoSummariesKeepEveryOriginalCandidate — duplicate/nullOID and listgaps.
7 routeConditionsAndSenderOperationRemainConfiguration — differingoperation, falsecondition, dynamicroute.
8 unmatchedReceiverBranchesNeverBecomeBpmDelivery — matchingbranchindices only inbound.
9 optionalIcoStatesStayDistinct — absent/unavailable/missingicos/empty/limited/issues/unsupported.
10 channelOidNeverFallsBackToName — missingOID otherOID matchingname.
11 channelConflictsDuplicatesAndPartialKeysStayUncertain — allcandidates, emptyvsnullparty, selectorless.
12 receiverPartyAndDirectionAreChecked — legacycomponent-onlyassociation cannotprove transport.
13 parameterChannelsAreNotDeliveryTransports — roles and maporder.
14 originalConfigRetainsRoutingModulesMappingsAndHoles — no inferred ack/moduleexecution.
15 sourceCallsRetainAllOperationServiceCandidates — directlinks, excludedtargets, duplicatecatalogue.
16 triggerOperationDirectionRemainsUnknown — bothlegacyorientations raw.
17 disconnectedStepsStillHaveCallMetadata — sourcepresence notconnectivityproof.
18 asyncWithoutAckAndTransactionEvidenceStaysUnknown — parsed interface MEP config no runtimeclaim.
19 oldDumpSourceUnavailableDoesNotInventCalls — candidatecatalogue separated.
20 unknownUsageAndMissingInterfaceNodeRemainDiagnostics — no first/namebinding.
Report: invalidargs/empties, deepimmutable alllegacy nestedlists/maps nullholes, deterministic/inputunchanged,
explicit JSON null/empty fieldorder, Markdown escapedcontent/runtimeboundaries.
Inputs: sorted directjson mixedvalid/invalid/unreadable/unsupported; missingroot; optionalICO states;
zero usable; no filemutation/network.
CLI: registration/help,json actualsource/configcontent, Markdown content, errorpath, optionalbadICO.
MCP: actualinline, actualspill/fullcontent, ToolCallException error, inputhashunchanged.
Native: additive allrecordregistration. Exact tests may split related variants; root reads allbeforeapproval.
Compile-red missingintroducedAPI or assertionred, never infrastructure/skip. Existingtesthashes preserved.
Independent review checks every test and adversarial ambiguity examples. Full root mvn -B -ntp verify,
docschecks/privacy/whitespace and fresh packaged CLI synthetic smoke required before localcommit.
Baseline private/forbidden skips remain NOTverified; no weakenchecks. After P3acceptance stop.

Preflight r1 four blockers: call grammar/precedence, receiver extractor projection, inbound route
placeholders, channel availability/aggregate states. Revision2 closes all four with adversarial cases.

Architect test approval: entire nine files read, including final deltas for complete ICO summaries,
outbound placeholder unknown state, channel null-ID/direction/projection cases, notification and
UUID/service candidates, and defensive map encounter order. Frozen SHA256 manifest:
target/mig-p3c-behavior-test-author-hashes.log.51tests=40core+6CLI+4MCP+1native.
Real fixture reader prerequisite passed (1process,2interfaces,1service,2ICOs,3channels); final core
compile-red exit1 misses introduced BehaviorReport/BehaviorAnalysis, no runtime tests executed.
Native compatibility amendment: the existing allModelClassesAreRegistered test requires every model
class in RegisterForReflection.targets, including non-record helpers. First focused run observed
core40/0/0/0 and native9/1/0/0, exit1, missing BehaviorSnapshots. The architect approved public class
visibility solely for its cross-package class literal; no public operations, report fields or test
expectations changed. Combined243/0/0/0 confirmed the correction; independent review remains required.

Physical permission-denied fixtures are not used; deterministic malformed exports and optional-ICO
directory cover error paths without ACL assumptions/skips. Implementation must still retain every
specified IOException/admission behavior.227previous source/test files remain unchanged; nativeadditive.

## Review round1 correction acceptance
Both findings are static counterexamples, not observed red until the separate test-author/verifier run.
Update: architect read and approved all159lines; author observed3/3/0/0 assertion-red,exit1.
Frozen regression SHA256 EB9804B7DDFBF07838BF8EA804036497540FD781B3252D81E3D9ECE7F34D19F5;
all24 protected production/original-test/doc hashes unchanged. Independent confirmation observed
3/3/0/0 assertion-red,exit1,1.304s, with all hashes retained; bounded two-file correction authorized.
Original nine approved files remain byte-for-byte frozen. The sole additional test path is
po-xray-core/src/test/java/io/poxray/migration/BehaviorCompletenessTest.java. Reuse SyntheticBehavior
and actual parsed inventories; wrapping lossy catalogue/configuration variants is allowed as above.
1. emptyPartyOnlyReferenceKeepsCompatibleCandidatesButRemainsUnknown: a DirRef with empty party
   and no OID/component/name retains channels with empty party, excludes a nonempty party and a null
   channel ID; resolution remains unknown. Empty party is a supplied selector, never selectorless.
2. processReasonsIncludeNestedIcoRouteGaps: incomplete inbound other branches and incomplete outbound
   structures retain their existing raw configurations/placeholder indices and candidate states;
   ProcessWiring.reasons also contains incomplete-other-route and unknown-route from nested matches/routes.
3. processReasonsIncludeAmbiguousServiceCandidateGaps: duplicate service catalogue matches retain all
   candidates and their source/candidate states; the nested ambiguity reason reaches ProcessWiring.reasons.
Tests assert the exact existing reason tokens from the report, not invented new terminology.
Observed red must be failing assertions in these three tests, with production and original tests unchanged.
Root reads/approves the new file; independent red confirmation precedes the same implementer's fix.
Correction allowed only in BehaviorChannels.java and BehaviorDiscovery.java, plus designated evidence logs.
Aggregate all owned interface/match/route and call/service reasons deterministically without dropping
existing immediate reasons or changing states, indices, snapshots, call grammar or serialization contract.
After correction run the regressions and prior combined suites, check all frozen/source/doc hashes,
then same code reviewer round2, final docs review, independent fullroot and fresh packaged CLI.
