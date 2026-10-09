# po-xray — JSON output model (v0.1)

Scope: phase 1 — BPM processes (NW BPM Galaxy) + EJB inventory, read from one NWDI SCA.
Status: **accepted** (2026-10-09), BPM part implemented first, EJB part follows.

## Decisions

1. Artificial uncontrolled joins are contracted directly in `flows` (`via` keeps the join IDs).
2. Expression step tree is not emitted; `text` + `reads` + `functions` only.
3. EJB sources are referenced (path + sha256), not embedded.
4. `dataTypes.fields` only for types referenced by processes, flattened to paths with cardinality.
5. Process identity = `Galaxy.Workflow.Collaboration`; the active (orchestration) pool is in `pool`.
6. Built-in function names: static table (verified IDs) + inference from `stringExpression`
   (pre-order of FunctionInvocations ↔ order of `name(` tokens); conflicts/unknowns → issues.
7. Service group per service reference comes from the deployed `.composite` (SDA) and the `mc` logical system.
8. All graph references (`flows.source/target/via`, node `scope`/`lane`, data object `scope`) are `{ref, name}`.
9. Mapping parts without an expression (structural nodes) are not emitted.

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
  "ejb":   [ /* EjbModule — one per EJB DC */ ],
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
  "deployDependencies": [ { "vendor": "sap.com", "name": "com.sap.aii.af.lib.facade" } ] // from SAP_MANIFEST dependencylist
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
    "functions":         [ /* Function */ ],
    "eventDefinitions":  [ /* EventDefinition */ ],
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
  "trigger": { "kind": "error", "eventDefinition": { "ref": "…", "name": "EON_MT_Fault" },
               "builtin": "TECHNICAL_ERROR" },          // SAP built-in trigger not present in DC → static table
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
  "functions": [ "isSet" ]                     // built-in names or catalog function names
}
```
- `text` = `stringExpression`; if missing or empty, rendered from the tree (and `"rendered": true`).
- Editor-generated expressions (`#generated=true#<text>`) → prefix stripped, `"generated": true`.
- Infix operators are rendered as in the editor (`DO_TagsIndex < DO_TagsCount`).
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
    { "target": "EON_MT_SendEnvelope/Request/oauth_token",
      "expr": { "text": "DO_CreateEnvelopeInput/Request/oauth_token", "reads": [ "…" ] } },
    { "target": "Recipients/recipient", "mode": "append", "expander": "moveCorresponding",
      "expr": { … } },
    { "target": "Request/note", "enabled": false, "expr": { … } }
  ]
}
```
- Only parts with a non-empty expression are emitted (structural parts are implied by the target paths).
- Defaults omitted: `mode` (`category`) = `set`, `transformer` = `getValue`, `expander` = `currentNode`, `enabled` = `true`.

### Catalog objects

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
  "result": { "name": "result", "type": "…" } }

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

```jsonc
{
  "dc": "eon.com/applications/digisignvalidationeb",
  "ear": "eon.com/applications/digisignvalidationea",           // EAR DC wrapping this module
  "beans": [
    {
      "name": "ValidateCreateEnvelopeInput",
      "class": "com.eon.digisign.po.ValidateCreateEnvelopeInput",
      "type": "stateless",                     // stateless | stateful | singleton | messageDriven
      "jndiName": "ValidateCreateEnvelopeInput",
      "interfaces": { "local": [ "com.sap.aii.af.lib.mp.module.ModuleLocal" ], "remote": [ "…ModuleRemote" ] },
      "roles": [ "adapterModule" ],            // bpmFunction | adapterModule | xiProxyProvider | schedulerJob | other
      "moduleParameters": [ "showAudit", "faultInterface", "faultNamespace", "faultService", "senderId" ],  // adapterModule: getContextData("…") literals
      "usedBy": [ { "kind": "bpmFunction", "function": { "ref": "…", "name": "getRecipientIdIndex" } } ],     // bpmFunction only
      "source": { "path": "…!/ejbModule/com/eon/digisign/po/ValidateCreateEnvelopeInput.java", "sha256": "…" }
    }
  ],
  "classes": [ { "class": "com.eon.digisign.po.ValiadtionMessage", "source": { "path": "…" } } ],  // non-bean classes
  "technologies": [ "jco", "jpa" ],            // only when detected
  "jpaEntities": [ ]
}
```
Classification (from source, regex/lightweight parse — no compilation):
- `bpmFunction` — implements an interface with `invokeSdo(DataObject, InvocationContext)` **or** referenced by a `.function` lookupName.
- `adapterModule` — `implements Module` (com.sap.aii.af.lib.mp.module).
- `xiProxyProvider` — `@WebService` + `@XIEnabled`.
- `schedulerJob` — MDB extending `MDBJobImplementation`.

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
| `UNKNOWN_BUILTIN_FUNCTION` | built-in function ID missing from the lookup table |
| `UNKNOWN_NODE_TYPE` / `UNKNOWN_ELEMENT_TYPE` | metaclass not handled → also in `unmapped[]` |
| `EXPRESSION_TEXT_MISMATCH` | `stringExpression` differs from the rendered tree |
| `BUILTIN_FUNCTION_CONFLICT` | learned built-in name contradicts the static table / another expression |
| `UNEXPECTED_STRUCTURE` | graph shape the reader does not expect (e.g. artificial join with ≠ 1 outgoing flow) |
| `DUPLICATE_ID` | MOF ID declared in more than one partition |
| `SUSPICIOUS_MAPPING` | heuristic: same source expression assigned to two sibling targets in one mapping |
| `UNUSED_OBJECT` | interface, service reference, function, eventdef or data object not used by any process (SAP system data objects excluded) |
| `MISSING_SOURCE` | `.function` lookupName points to an EJB not found in the SCA |

Manually confirmed findings (typos, logic bugs in Java) are **not** auto-detected in v0.1; they belong to the
documentation, optionally via a `knownIssues` input file later.

## Not in v0.1 (deliberately)

- `.diagram` layout (positions), `.xlf` translations (only `originalText` is emitted).
- BRM rulesets (`src/brms`), Dictionary (`*.dtdbtable`), UME (`actions.xml`) — listed in `developmentComponents`, content later.
- Full expression step tree, full WSDL/XSD content (only what processes reference).
- Compiled `.bpem` csv (used only as secondary source for built-in function IDs).
