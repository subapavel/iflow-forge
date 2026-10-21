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

Copied unchanged from the approved round's "Risks and assumptions" (`references/plan.md`, "Proposal sections", item 6): one line per `Q-<n>`, `assume <X>; verify at <event>`.

## Approval

`plan round <r>, approved <date>`: the round this specification is cut from. The specification is not approved separately (`references/plan.md`, "Approval").
