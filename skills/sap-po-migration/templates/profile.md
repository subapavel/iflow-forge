# Tenant profile: `<customer>`

This file lives as `local/<customer>/profile.md`: read at intake, amended in the retrospective, never committed [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.1 Tenant profile (part 2, minimum contract — draft)] (spec §12 P-2). Replace every `<…>`; an answer nobody knows is `unknown`, an open-list item (D-031). A later design may extend the seven sections, never rename them.

## Input paths

| input (spec §5) | directory | default |
|---|---|---|
| BPM exports (`po-xray export`) | `<directory>` or `unknown` | `testdata/bpm/out/` |
| ICO dumps (`po-xray ico`) | `<directory>` or `unknown` | `testdata/ico/` |
| iFlow dumps (`po-xray iflow`) | `<directory>` or `unknown` | `testdata/iflows/` |

Defaults: `docs/inputs/README.md` §2–3; the iFlow path is spec §5's future part-3 path. `unknown` leaves the input row `missing` at intake (`references/intake.md`, "Tenant profile", step 3).

## Runtime

`runtime: <value | unknown> — source: <M-<n>, Q-<n> | deployed generic iFlows of the tenant>`

Values: `runtime` of [page: knowledge/pipeline/scenario-contribution.md#Scenario attributes]; `unknown` is a blocking open-list item.

## Installed pipeline versions

| package | version | read from | read on |
|---|---|---|---|
| Generic Inbound | `{{tenant.pipeline.version.generic_inbound}}`: `<version>` | `<source>` | `<date>` |
| Generic Outbound | `{{tenant.pipeline.version.generic_outbound}}`: `<version>` | `<source>` | `<date>` |
| Generic Pipelines | `{{tenant.pipeline.version.generic_pipelines}}`: `<version>` | `<source>` | `<date>` |
| Templates | `{{tenant.pipeline.version.templates}}`: `<version>` | `<source>` | `<date>` |

A value is a version or `unknown`; packages and versions as [page: knowledge/pipeline/versions/features.md#The tenant's installed version] defines them; an artifact's MANIFEST Bundle-Version is its own number, so "read from" names the file. Update available on the tenant: `<package> <version> — <changelog lines it brings>` (spec §10).

## Naming conventions

| placeholder | the tenant's value | source |
|---|---|---|
| `{{tenant.<path>}}` | `<value>` or `unknown` | `<M-<n>, Q-<n>>` |

One row per path of [page: knowledge/pipeline/naming.md#Tenant placeholders], by its groups endpoint, xi, idoc, security, pipeline, jms, name, cloudconnector; the queue prefix and suffix are rows of the pipeline group. Then:

- iFlow id of a template copy: `<scheme>` (SAP's: [page: knowledge/pipeline/naming.md#SAP's own naming]);
- package: `<scheme>`;
- Partner Directory alias, sender and receiver: `<scheme>`.

## Partner Directory conventions

- Partner ID form in use: `<form>`, one of [page: knowledge/pipeline/partner-directory.md#Key scheme].
- Alternative partners: `<yes | no>`; agency `<form>`, scheme `<form>`, as the lookups of [page: knowledge/pipeline/partner-directory.md#Key scheme] name them.
- Landscape stages: `<stage list | none>` (the stage entry of [page: knowledge/pipeline/partner-directory.md#Key scheme]).
- Keys in use: `<key patterns>` of [page: knowledge/pipeline/partner-directory.md#Keys per scenario].

## Decisions

Appended by the retrospective, never edited:

`<yyyy-mm-dd> — M-<n> — <decision>`

## Tool and skill versions

One line per migration, appended by the retrospective, and after a field request changed the tool (spec §9):

`M-<n> — runner jar <git hash> — skills/sap-po-migration <git hash>`
