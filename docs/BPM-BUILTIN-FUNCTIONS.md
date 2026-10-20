# NW BPM built-in functions catalog

Supersedes the partial operator table in `HANDOFF.md` section 2.5.

- Resource: `po-xray-core/src/main/resources/io/poxray/bpm/bpm-builtin-functions.json`
- Generator: `po-xray bpm-functions <nwds-plugins-dir> <output.json>`
- Sources (NWDS build 2025-08):
  - `com.sap.tc.ap.servicecomp_7.3.0.250825003525.jar` → DC `sap.com/tc/bpem/content/predefs/types`
    (`rep/functions/sap.com/bpem/glx/built-in/<category>/*.function`, 220 functions, + `rep/wsdl/xml_schema.types`)
  - `com.sap.tc.ap.bpemcontent_7.3.0.250825003525.jar` → DC `sap.com/tc/bpem/content/predefs/default`
    (`src/functions/**`: principal `getPrincipal`, `getPrincipalByUniqueName`, `getPrincipals`, and `getExternalUser`;
    `src/triggers/*.eventdef`: standard triggers; + `rep/wsdl/*.types`)
- Derived from SAP software: private repositories only.

## Content
- `functions[]` (224; each with `sourcePlugin` / `sourceDc`): `id` (MOF ID referenced by `Rule.FunctionInvocationHasFunctionInterface` as `local:<id>`),
  `namespace`, `category` (boolean, text, generic, finite-integer, infinite-integer, fuzzy-real, precise-real,
  date-time, duration, principal, default), `name`, `symbol` (editor notation, e.g. `=`, `<`, `!=`, `IF`), `infix`,
  `documentation` (SAP semantics), `parameters[]` (name + XSD type, in declaration order), `result` (XSD type),
  `javaClass` (runtime implementation).
- `triggers[]` (11): standard event definitions referenced by `Galaxy.Workflow.CatchEventHasTrigger` /
  `ThrowEventHasResult` but not contained in customer DCs: `id`, `kind` (error | message | timer), `name`,
  `documentation`, `isCritical`, `isTechnical`, `parameter` (XSD element name/namespace + type), `file`,
  `sourcePlugin` / `sourceDc`. Content: `TechnicalError` (critical, technical; parameter type `TechnicalErrorType`),
  `Start/CompletionDeadlineIs(Non)CriticalException` (parameter type `TaskException`),
  `DefaultEscalation(Non)Critical` (string payload), `DefaultService_Empty` / `_EmptyAsync` (message) with
  `DefaultService_Fault`, `DefaultTimer`.
- `xsdTypes` (91): MOF ID → XSD built-in type name (`xml_schema.types`); resolves `Rule.LiteralHasSimpleDataType` and parameter types.
- `sapTypes`: MOF ID → SAP-defined type name from the other `rep/wsdl/*.types` of both DCs (e.g. `Principal`, `ExternalusersType`).
- `source` = primary source (predefs/types), `additionalSources` = predefs/default.

## Notes
- Key on `id`, never on `name`: the same name exists per type category with different IDs
  (e.g. `numeric-equal` in finite-integer / infinite-integer / fuzzy-real / precise-real). The category tells the operand type.
- IDs are stable (persisted in every customer process; unchanged 2008 → 2025). Unknown IDs must be reported, not guessed.
- Verified against BPMN 2.0 exports of DigiSign: all 9 previously derived IDs match.
- Related SAP content in `com.sap.tc.ap.bpemcontent_*.jar` → DC `sap.com/tc/bpem/content/predefs/default`:
  standard triggers incl. `src/triggers/technicalerror.eventdef` (TechnicalError boundary events),
  deadline/escalation event definitions, principal functions, default task.
