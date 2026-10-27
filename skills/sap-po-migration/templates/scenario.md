# Scenario specification: M-<n>, scenario <k>

Draft — review at hand-over (spec §12 P-8): part 4 consumes exactly this file.

One `scenario-<k>.md` per pipeline scenario of the approved plan, in `local/<customer>/scenarios/M-<n>/` (spec §7 item 5). Replace every `<…>`; every value comes from the approved round or the profile, nothing is decided here.

## Scenario

- migration: `M-<n>`
- scenario: `<k>` of the plan's decomposition
- plan: round `<r>`, `approved <date>`
- `sender <component> <interface> → receivers <component>, …`, as the attributes report writes it

| attribute | value | source |
|---|---|---|
| `runtime` | | |
| `qos` | | |
| `sender_type` | | |
| `receiver_type` | | |
| `receiver_count` | | |
| `receiver_determination` | | |
| `interface_determination` | | |
| `mapping` | | |
| `error_handling` | | |
| `custom_exit` | | |
| `inbound_conversion` | | |

- Values: the D-025 set as [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes] lists it.
- Source: the `analysis.md` row's path (`icos[i]…`), `Q-<n>`, or `plan round <r>, <section>` (D-034).
- At phase-5 exit no value is `?` (spec §7 item 5).

## Source summary

`source: <ICO generatedFrom, else oid | BPM step name> — sender, receivers: analysis.md, "Source"`

By reference to `analysis.md`: what this scenario comes from, nothing beyond what the local files already hold.

## Target artifacts

| template | copy | parameter | placeholder | resolved value |
|---|---|---|---|---|
| `<SAP template id>` | `<copy iFlow id>` | `<parameter>` | `{{tenant.<path>}}` | `<value from the profile>` |

One row per parameter of each template copy in the plan's "Target artifacts" (the rows of [page: knowledge/pipeline/scenario-contribution.md#What a scenario contributes] it took). The template by its SAP id [page: knowledge/pipeline/naming.md#SAP's own naming]; the copy's id by the profile's "Naming conventions" [page: knowledge/pipeline/scenario-contribution.md#Naming]. An adapter setting without an externalized parameter (a sender SOAP address, a File receiver directory, a Step08 ProcessDirect call the template does not carry) is a row with `adapter "<name>" <setting>` in the parameter column and `—` as placeholder (D-039); a parameter the scenario does not use has `not set` and its reason as placeholder and counts as resolved (D-041). Exit: every placeholder resolves against the profile (spec §7 item 5); one that does not resolve means the phase does not exit (SKILL.md, "Phases"); a change after approval is a new plan round (`references/plan.md`, "Approval").

## Contract bindings

| Catalog revision | Artifact / contract IDs | Approved plan round / section | Choice / owner | Evidence |
|---|---|---|---|---|
| `<private contracts.md revision>` | `<exact IDs>` | `<round r, section, approved date>` | `<approved placement/disposition>` | `<source/decision locator>` |

Pin the approved contracts used by target artifacts, conversions and process data, including
request/response/fault roles, producer/consumer/default evidence and unresolved limitations from that
round. Follow [the contract reference](../references/contracts.md). No QName binding, artifact class,
input requirement or other choice is introduced in phase 5; a changed basis returns to a new plan round.

## Partner directory entries

| key | value type | value |
|---|---|---|
| `<key>` | `<String or Binary>` | `<value or {{tenant.<path>}}>` |

Keys from the plan's section, as [page: knowledge/pipeline/partner-directory.md#Keys per scenario] names them, under the partner ID of [page: knowledge/pipeline/partner-directory.md#Key scheme]; value types per [page: knowledge/pipeline/partner-directory.md#Value types].

## Conversions needed

| PO module or mapping | recipe |
|---|---|
| `<raw name>` | `knowledge/pipeline/conversion/<page>.md`, or "proposal, confirm" |

One row per module and mapping program of the plan's "Conversions needed". A recipe is a page of the form [page: knowledge/pipeline/conversion/README.md#Recipe pages] names; a row that is still a D-026 proposal of [page: knowledge/pipeline/conversion/README.md#Standard adapter modules] or [page: knowledge/pipeline/conversion/README.md#Mapping programs] is written "proposal, confirm".

## Target version

| package | installed (profile) | target (Q-<n>) | feature | since |
|---|---|---|---|---|
| `<package>` | `<version>` | `<version> (Q-<n>)` or `—` | `<feature>` | `<version>` |

One row per feature this scenario needs, its version from [page: knowledge/pipeline/versions/features.md#Features by package]. A feature newer than the installed version is a version item (spec §10: a blocking question), never assumed; a decided update names its target here with the `Q-<n>` (D-037); the installed column stays the profile's value.

## Risks and assumptions

Copy the approved round's relevant unresolved facts, accepted assumptions and risks: `Q-<n>`, owner, approval source, exact scope, permitted milestones, verification dependency, limitations and reopening triggers. Unknown remains unknown; a risk exception never claims technical evidence. [page: skills/sap-po-migration/references/readiness.md#Assumptions, bases and risks]

## Approval

`plan round <r>, approved <date>`: the round this specification is cut from. The specification is not approved separately (`references/plan.md`, "Approval").

When using a dossier, also record the consumed decision IDs, section IDs, revisions and SHA-256 digests in a `derived-document` event for this file. They must match the active approved scopes of that approved round. A stale or missing required specification fails the dossier check; partial section approval still cannot replace whole-plan approval. [page: skills/sap-po-migration/references/dossier.md#Approval and current state]

With opt-in v2, record the specification milestone and its one ready final-plan ancestor covering every exact required scope and connection. Check effective-section inventories, relevant dependencies and current answer bases before creating the specification; missing classification never implies readiness. A changed consumed answer requires a new revision and approval, preserving historical consent. Readiness grants no tenant operation permission. [page: skills/sap-po-migration/references/readiness.md#Readiness and approval]
