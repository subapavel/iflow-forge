# Versions

Which SAP pipeline version the knowledge base describes, what a label folder under `knowledge/sources/` holds, and how a new SAP version enters the pages.

## Current label

`2026-06` is the fact base of every page (D-019): 29 artifacts in four package folders [knowledge/sources/README.md#L9]. Versions come from its INDEX.md, cited as `[2026-06/INDEX.md#L<n>]`, a D-019-compatible form for source index lines (D-022): Step01 XI 1.0.5 [2026-06/INDEX.md#L5], Step01 IDoc 1.0.8 [2026-06/INDEX.md#L7], the Generic Inbound change log 1.0.7 [2026-06/INDEX.md#L3], its first dated entry [generic-inbound/CHANGELOG.txt#L5].

## Labels

| label | origin | package versions (D-044) | artifacts | status |
|---|---|---|---|---|
| `2026-06` | package export, June 2026 [knowledge/sources/README.md#L9] | Generic Inbound 1.0.7 [2026-06/INDEX.md#L3], Generic Pipelines 1.0.4 [2026-06/INDEX.md#L16], Generic Outbound 1.0.3 [2026-06/INDEX.md#L14], Templates 1.1.1 [2026-06/INDEX.md#L36] | 29 [knowledge/sources/README.md#L9] | fact base, `kb.sources` default |
| `2026-09` | package export, September 2026, scrubbed [knowledge/sources/README.md#L10] | Generic Inbound 1.0.8 [2026-09/INDEX.md#L6], Generic Pipelines 1.0.5 [2026-09/INDEX.md#L19], Generic Outbound 1.0.5 [2026-09/INDEX.md#L15], Templates 1.2.0 [2026-09/INDEX.md#L41] | 32, Templates 1.2.0 [knowledge/sources/README.md#L10] | staged; to be replaced by a clean catalogue download [knowledge/sources/README.md#L21]; pages after KB-00a |
| `2026-10-catalog` | clean catalogue download of the four packages | — | — | planned (agent/BACKLOG.md KB-00a) |

Which package version brought which feature or fix, from the change logs of both labels: versions/features.md.

## What a label folder holds

- `INDEX.md`: one row per package entry with package, type, name, symbolic name, version [2026-06/INDEX.md#L1].
- One folder per package, `generic-inbound`, `generic-outbound`, `generic-pipelines`, `templates` [2026-06/INDEX.md#L3] [2026-06/INDEX.md#L9] [2026-06/INDEX.md#L16] [2026-06/INDEX.md#L25], with `CHANGELOG.txt`, SAP's change log [knowledge/sources/README.md#L4], newest entry first [generic-inbound/CHANGELOG.txt#L5] [generic-inbound/CHANGELOG.txt#L8], and one folder per artifact named by its Bundle-SymbolicName [knowledge/sources/README.md#L3].
- Per artifact: `META-INF/MANIFEST.MF` and `src/main/resources` (`parameters.propdef`, the `.iflw`, `script/`).
- Scrubbed: `parameters.prop` (configured values) is never staged, `parameters.propdef` (SAP defaults) is [knowledge/sources/README.md#L14]; `Configured_Custom_Tags.txt` is never staged [knowledge/sources/README.md#L16]; in `2026-09` tenant suffixes were stripped, everything else is byte-identical to the export [knowledge/sources/README.md#L19] [knowledge/sources/README.md#L20].

## Processing a new SAP version

1. Export or download the four packages into `testdata/` (gitignored), never into the repository.
2. Per package: `java -jar po-xray-cli/target/po-xray-cli-*-runner.jar unpack <package.zip> knowledge/sources/<label>/<package-folder>`; it writes one folder per symbolic name plus `CHANGELOG.txt`, without `parameters.prop` and `Configured_Custom_Tags.txt`; `--strip <token>[,<token>]` removes a tenant suffix.
3. Check the scrub against knowledge/sources/README.md: no tenant names, no configured values.
4. Write `<label>/INDEX.md` in the columns above.
5. Dump both labels with `po-xray iflow <path>`, diff dumps and trees, read the new `CHANGELOG.txt` entries, and write `versions/<label>/changelog.md`: iFlows added, removed, changed, contract changes (spec §13.1; §12.7 is withdrawn).
6. Switch `kb.sources` in iflow-forge-knowledge/pom.xml to the new label.
7. Run `mvn -B -ntp verify`.
8. Update the pages: the new version is primary, older values become inline "(vN: was X)" notes.
