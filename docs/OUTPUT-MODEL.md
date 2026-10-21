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
- Empty/default values are omitted (no `null`, no `[]`), to keep the output small.

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
  }
}
```

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
  "initialValue": Expression                   // only when an init mapping exists (ViewContainsMapping)
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

## Migration helpers

Two read-only helpers of the migration skill (`sap-po-migration`) turn po-xray outputs into decision inputs. Both
write Markdown by default and JSON with `--json`; `schemaVersion` "0.1", `generator` as in the other outputs, maps
sorted by key, `issues` always written (`[]` = none found), other nulls and empty lists omitted.

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
from `esr` or "ask" (D-040); mapping shape from targets and multiplicities, "one type" only when every target interface has the
same name and namespace (`ordered` and receiver_determination `custom` are never derived);
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
