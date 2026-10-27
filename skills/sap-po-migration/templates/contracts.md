# Contract catalog: M-<n>

Private supporting evidence for phases 2, 4 and 5. Follow [the contract reference](../references/contracts.md).
Replace placeholders; use `unknown` with a question ID for missing facts. Preserve earlier revisions.

## Revision and evidence

- Catalog revision: `<revision>`; date: `<date>`; author/owner: `<owner>`.
- Inputs: `<inventory, XML resources, BPM data report, supplied catalog/proposal>`.
- Each input: `<private path, SHA-256, date if known, evidence locator, source/supplied, status>`.
- Coverage gaps: `<missing inputs and Q-IDs>`; helper versions: `<formatVersion, analysisVersion, P3 versions>`.
- Prior proposal: `<source/date/status; approval not inherited>`.
- Consuming plan: `<round/section/revision; proposed until explicitly approved>`.
- Approved plan link: `<round, approved date, exact scope; unknown until approval>`.

## Artifacts

| Artifact ID | Source/supplied basis | Class | Owner or containing artifact | Declaration/decision evidence | Unknowns/Q-ID |
|---|---|---|---|---|---|
| `<ID>` | `<basis>` | `<kind>` | `<owner>` | `<source, locator, status>` | `<gap>` |

Keep source ESR mapping, target shared-message-mapping, iflow-mapping, Script Collection, local script
and mapping function distinct. ProcessDirect alone is transport evidence. Record resources separately
from ExternalDefinition and native MT/DT. Descriptive owners and artifact references are separate fields.

## Contracts and ports

| Contract ID / artifact ID | ESR identity (type, key, OID, SWC) | XML QName / declaration locator | Disposition | Evidence/Q-ID |
|---|---|---|---|---|
| `<IDs>` | `<reference or unknown>` | `<namespace, local name or unknown>` | `<preserved-external / proposed-internal / unknown>` | `<basis>` |

Retain source and supplied claims separately; no external-message QName inference from resource names.

| Contract ID | Port role | Participant / operation | ProcessKey / step ordinal | Evidence / binding uncertainty |
|---|---|---|---|---|
| `<ID>` | `<request / response / fault / mapping-input / mapping-output / producer / consumer>` | `<identity>` | `<exact reference or unknown>` | `<locator, candidates, Q-ID>` |

## Values and timing

| Contract / value path | Role | Required-input evidence | Producer | Consumers | Exact default text | Evidence/Q-ID |
|---|---|---|---|---|---|---|
| `<ID/path>` | `<input / default / api-response>` | `<source/decision or unknown>` | `<ProcessKey/step>` | `<ProcessKey/steps>` | `<text or not applicable>` | `<locator/status>` |

API response values are not request requirements. Missing producers remain unknown.

| ProcessKey / step | Before candidates | After candidates | Declaration / initial mapping / read domain | Reasons / unresolved timing |
|---|---|---|---|---|
| `<exact identity>` | `<origins>` | `<origins>` | `<evidence IDs>` | `<P3 reasons, Q-ID>` |

All before/after rows are structural candidates, never runtime availability claims.

## Dependencies

| Owning artifact | Kind / ESR reference | Original location / namespace | Candidate artifact IDs | State | Evidence / Q-ID |
|---|---|---|---|---|---|
| `<ID>` | `<MT-DT / DT use / mapping / library / import / include / redefine>` | `<exact text>` | `<all candidates>` | `<state>` | `<locator>` |

Include transitive missing leaves and cycles. A unique URI path match remains a candidate; namespace-only
or pathless imports remain unknown. No import completeness, schema compilation or deployment is asserted.

## Plan bindings and unresolved items

| Plan round / section | Catalog revision | Artifact / contract IDs | Choice / owner / rationale | Approval source / Q-ID |
|---|---|---|---|---|
| `<target artifacts / conversions / process data>` | `<revision>` | `<IDs>` | `<choice>` | `<exact approved round or pending>` |

- Open items: `<gap, affected sections/connections, owner, expected evidence, Q-ID>`.
- Assumptions/risks: `<approved scope, limitations, verification/reopening conditions>`.
- Specification consumers: `<scenario files pinning this revision and approved plan round>`.

Whole-plan approval governs. The catalog grants no separate approval; phase 5 introduces no new choices.
