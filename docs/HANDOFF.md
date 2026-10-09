# PO → Integration Suite migration tool — handoff

Context carried over from the exploration session (2026-10-09). Read this before starting work.

## 1. Goal and current focus

Long-term: a tool that reads SAP PO content (ESR, Integration Directory, NW BPM, EJB/adapter modules) into a local snapshot / model, as the basis for migration to SAP Integration Suite (assessment, documentation, (semi)automatic conversion).

**Current focus (phase 1): BPM reader + EJB reader**, driven by a real client project: several NW BPM processes for DigiSign (E.ON) are being unified and **redesigned** in Integration Suite. The reader does NOT convert 1:1; it produces a **complete, readable process model** (JSON) + generated documentation that a human/LLM uses for the redesign.

Principles agreed:
- Don't over-engineer. One input format per reader, no generic abstraction layers up front.
- Library core in **Java** (CLI / MCP later as thin layers). Proposed: Maven, Java 21 (confirm with Pavel).
- Lossless: keep raw source next to the parsed model.
- Bugs/typos found in source are reported explicitly in the output, never silently fixed.
- Client data (SCA files, exports) is confidential: keep test data out of any public location (local / private only, consider .gitignore for test resources if repo is ever pushed).

## 2. BPM source: NWDI SCA

SCA = zip: `SOURCEARCHIVES/*.dcsa` (DC sources — primary), `DEPLOYARCHIVES/*.sda|*.ear`, `BUILDARCHIVES/*.dcia`, `META-INF/SAP_MANIFEST.MF` (SC name, vendor, track, DC list). Each DC has `.dcdef` (XML with dependencies on other DCs).

BPM DC (`.dcsa`) content:
- `src/bpmn/<process>.bpmn` — **Galaxy model (MOIN partition), not BPMN 2.0**. Also `.diagram` (layout), `.bpmn.xlf` (texts).
- `src/functions/**/*.function` — EJB/SDO function interfaces used in mappings.
- `src/triggers/*.eventdef` — message triggers / error (escalation) event definitions.
- `src/wsdl/*.wsdl|*.xsd`, `rep/wsdl/*.services|*.types` — imported WSDL/XSD as MOIN partitions (operations, faults, XSD element declarations).
- `mc/META-INF/servicereferences.servicerefs` — service references of automated activities (type WS, bindingType XI…).
- `src/brms/*` — BRM rulesets (PPAS_OTE sample has one).
- Deploy SDA contains `<dc>.bpem` (zip): compiled `gen_bpm/bpmn/*.csv` (Galaxy compiler output), and in DigiSign case also `src/bpmn` (sources). `.composite` lists SCA references incl. **Service Groups** (e.g. `sappo_om`) — their config lives in NWA (runtime), not in SCA.

### 2.1 MOIN partition format (verified)
```
<Partition xmlns:m='http://xml.sap.com/moin'>
  <C .../>                          metamodel containers
  <M c=.. q='Galaxy.Workflow.X' i=../>  metaclass / association declarations
  <Galaxy.Workflow.AutomatedActivity m:c='1' m:i='<MOF-ID>' ...attrs>
     <name originalText='Send Envelope' .../>
     <documentation originalText='...' .../>
  </...>                            elements are FLAT, identified by m:i
  <Galaxy.Workflow.SourceOfIncomingConnectors>
     <L f='<id>' s='<id>'/> ...      links grouped per association
  </...>
```
- Refs prefixed `local:` point to elements in **other partitions of the same DC** (`rep/wsdl/*.services`, `*.types`, `src/functions/*.function`, `src/triggers/*.eventdef`, `mc/...servicerefs`). Build a global index of all partitions in the DC (all files whose head contains `<Partition`).
- A few refs point to SAP built-in partitions not present in the DC (built-in functions, XSD simple types, SAP technical error trigger) → need static lookup tables.

### 2.2 Association semantics (f = first end, s = second end)
| Association | Meaning |
|---|---|
| `Workflow.ScopeContainsScopeObjects` | f=object, s=scope (Pool / EmbeddedScope) |
| `Workflow.LaneHasScopeObjects` | f=object, s=lane |
| `Workflow.SourceOfIncomingConnectors` | f=source node, s=connector |
| `Workflow.TargetOfOutgoingConnectors` | f=target node, s=connector |
| `Workflow.ConditionalSequenceConnectorContainsExpression` | f=Expression, s=connector |
| `Workflow.ActivityHasOperation` | f=WSDL2.Operation (local), s=activity |
| `Workflow.AutomatedActivityHasServiceReference` | f=ServiceReference (local), s=activity |
| `Workflow.ActivityHasBoundaryEvent` | f=BoundaryEvent, s=activity/EmbeddedScope |
| `Workflow.CatchEventHasTrigger` | f=ErrorEventDefinition/trigger, s=catch event |
| `Workflow.ThrowEventHasResult` | f=ErrorEventDefinition (eventdef), s=EndEvent |
| `Workflow.ErrorEventDefinitionHasWSDLFault` | f=WSDL2.Fault, s=ErrorEventDefinition |
| `Workflow.ActivityContainsEventDefinitions` | f=ErrorEventDefinition, s=activity |
| `Workflow.InputMappingOfMappableObject` / `OutputMappingOfMappableObject` | f=Mapping, s=activity/event/scope |
| `Workflow.DataContainerReferencesXsdElementDeclaration` | f=XsdElementDeclaration, s=DataObject |
| `Mapping.MappingContainsMappingParts` | f=MappingPart, s=Mapping |
| `Mapping.MappingPartHasChildren` | f=child part, s=parent part |
| `Mapping.MappingPartHasQName` | f=QName (target field), s=part |
| `Mapping.MappingPartContainsExpression` | f=Expression, s=part (attr `transformer`: getValue / discardValue …) |
| `Rule.ExpressionHasSteps` | f=step (Reference / Literal / FunctionInvocation / Condition), s=Expression; **file order = step order** |
| `Rule.ReferenceHasQName` | f=QName, s=Reference (path segment name) |
| `Rule.FunctionInvocationHasFunctionInterface` | f=FunctionInterface (local or SAP built-in), s=invocation |
| `Rule.FunctionInvocationHasParameterMapping` | f=Mapping (parts = params), s=invocation |
| `Rule.ConditionHas{Condition,Positive,Negative}Expression` | if/then/else |

Expression rendering: consecutive Reference steps form a path `DO_X/a/b`; FunctionInvocation renders `name(param=expr,…)`; a step after an invocation continues the path on its result.

### 2.3 Editor artefacts
- Nodes named `Artificial Uncontrolled Join (…)` are generated by the editor (multiple incoming flows). **Contract them** (pass-through) to get the graph as shown in NWDS / BPMN 2.0 export.

### 2.4 Validation against BPMN 2.0 export (NWDS export)
- Export contains `bpmn2_1:BPMelementID` = the same MOF ID → 1:1 pairing of nodes/flows.
- After contracting artificial joins: **digisign_envelope 56/56 flows, download_completed 29/29, 0 diffs.**
- Export has gateway conditions as text, but **no data mappings** → Galaxy source is the primary input; export = test oracle only.

### 2.5 Built-in function IDs (derived from export, consistent)
| ID | Name |
|---|---|
| 481F3B2A912070301AC311DDB6990018717335EB | and |
| 481F3B2B916CBB701AC311DD9D840018717335EB | or |
| 481F3B2A9148DFC11AC311DD9AF50018717335EB | boolean-equal |
| 481F3B2E933B39E31AC311DDAFAD0018717335EB | numeric-equal |
| 481F3B2E93496AB01AC311DDAC420018717335EB | numeric-greater-than |
| 481F3B3295D1EA511AC311DD8C180018717335EB | string-equal |
| 481F3B3295F355001AC311DD9D850018717335EB | string-length |
| 49182A9D15FC90A0AF2411DDB43A0019991C7C01 | get (list, index) |
| 492D235E1CAE0460BBA411DDA2350019991C7C01 | isSet |
Missing: infix `<` (numeric-less-than) and others — extend from more samples. Compiled `.bpem` csv also maps built-in IDs to Java classes (`…/built-in/<category>/<ID>`), usable as a secondary source.

## 3. EJB / modules (from SCA)
- DigiSign SCA DCs: `bpm/digisign` (2 processes), `bpm/digisignfunctionseb` (EJB functions CreateMetadata, GetDocumentsTagsIndexedDetail, GetRecipientIdByExtId, GetRecipientIdIndex), `applications/digisignvalidationeb` (adapter module `ValidateCreateEnvelopeInput`), `*ea` = EAR wrappers.
- Classify beans: BPM function (EJB behind `.function`), adapter module (`implements Module`, dep `com.sap.aii.af.lib.mp.module`), XI proxy provider (`@WebService` + `@XIEnabled`), NW Scheduler job (MDB extends `MDBJobImplementation`), JCo/RFC usage, JPA entities.
- Deployed EARs may contain `src.zip` with sources (PPAS_OTE sample does).
- Dictionary DC: `*.dtdbtable` (XML table definitions). UME DC: `actions.xml`.

## 4. Plan
1. Draft JSON output model (process → scopes → nodes/edges with conditions → activities: operation, interface, input/output mappings, boundary/error events → data objects (XSD types), functions, triggers, interfaces; plus EJB inventory). Review with Pavel.
2. Java BPM reader: SCA → DCSA → MOIN parser → Galaxy model → JSON.
3. Tests: DigiSign + PPAS_OTE SCAs as fixtures; BPMN 2.0 exports as oracle (graph + conditions automated comparison).
4. EJB reader: bean classification, `.function` → EJB link, module parameters, include source.
5. Doc generator from JSON (Markdown; Jira wiki markup is Pavel's usual format — confirm).
6. Validate against Pavel's existing manual DigiSign docs.

## 5. Open items / waiting on Pavel
- Build tool / JDK confirmation (proposed Maven + Java 21).
- Target doc format for the client (Jira wiki markup?).
- More BPM SCAs with other patterns (human tasks, timers, parallel gateway, sub-process calls) + their BPMN 2.0 exports.
- His manual DigiSign documentation as a reference.
- Verify suspected mapping bug: activity *Create envelope recipient* maps `identificationNumber <- get(DO_CreateEnvelopeInput/Recipients/recipient, DO_RecipientsIndex)/function` (same source as `function` field — likely copy-paste).
- Known source issues already found earlier: class name typo `ValiadtionMessage`; loop in tag iteration uses outer index `i` instead of `j` (adapter module).

## 6. Later phases (explored, not now)
**Full details: `SOURCES-ESR-DIRECTORY.md`** (SimpleQuery request/response, read/ext URLs, xiObj envelope, mapping blobs, ICO/iFlow/channel content, env-specific attributes, Directory WS API comparison, TPZ binary format). Summary:
- **ESR & Directory**: use `SimpleQuery` + `read/ext` for both (`/rep/...` and `/dir/...`), not the Directory WS API. Both return the same `p1:xiObj` envelope (identity typeID/key/OID/VID, typed `lnks`, content by typeID) as TPZ exports. Directory online read includes URLs/users (TPZ omits env-specific attrs; passwords never available). SimpleQuery accepts XML requests via POST form fields (`useXMLL`, `queryRequestXMLL`, `responseXMLL`); a `condition` is mandatory; `IS_NOT_NULL` caused HTTP 500. `IFLOW` type is queryable via XML request (not in form list).
- TPZ format: zip → `.tpt` binary container, last 8 bytes = catalog offset, catalog XML lists segments; `modelElement` segments = `p1:xiObj`.
- Runtime-only items (NWA): Service Groups, JCo destinations, scheduler jobs, table data, keystores.

## 7. Prototype
`prototype/` contains the Python exploration scripts (reference only, not product code):
- `moin.py` — MOIN partition loader (elements + associations).
- `proc.py <dc-src-dir> <process.bpmn> [-v]` — renders scopes, nodes, edges with conditions, boundary events, input mappings.
- `cmp.py <process.bpmn> <process.bpmn2>` — compares graph with BPMN 2.0 export, contracts artificial joins, derives operator table.

## 8. Working preferences
- Pavel prefers step-by-step, concise answers; Czech in conversation.
- Ask before big decisions; don't implement ahead of agreed scope.
