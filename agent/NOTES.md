# Notes — facts the hand-off used to carry

Read by section, on demand (CLAUDE.md, Context hygiene). One section per area; the newest first.
Facts only; the next exact action and the state live in `agent/HANDOFF.md`.

## Sources layout (KB-00, 2026-10-19)

- `knowledge/sources/<label>/<package-short-name>/<Bundle-SymbolicName>/` with `META-INF/MANIFEST.MF`,
  `.project`, `src/main/resources/{scenarioflows/integrationflow/*.iflw, script/*.groovy, mapping/*,
  xsd/*, parameters.propdef}`; `CHANGELOG.txt` per package; `INDEX.md` per label (package, type, name,
  symbolic name, version). Package short names: `generic-inbound`, `generic-pipelines`,
  `generic-outbound`, `templates`.
- `2026-06-trial`: 29 artifacts (Step02 1.0.5, Step04 1.0.4, Step05 1.0.3, Templates 1.1.1 era).
  `2026-09-current`: 32 (Step02 1.0.6, Step04 1.0.5, Templates 1.2.0: adds the o2m-to-IDoc template,
  the skip-retry value mapping and the partner-directory upload helper). The three new artifacts
  had a creation-timestamp suffix in their symbolic names in the tenant export; stripped.
- `parameters.prop` never present (configured values); `.propdef` carries SAP defaults.
- A package export zip = `resources.cnt` (Base64 JSON, key `resources`, each entry `id`,
  `resourceType` ∈ {IFlow, ScriptCollection, ValueMapping, File, Url, ContentPackage,
  Configured_Custom_Tags}, `displayName`, `semanticVersion`, `contentType`) + `<id>_content`
  (a zip for deployable artifacts, plain text for the change log) + `contentmetadata.md` (Base64
  properties, carries the tenant environment name — never staged).

## iFlow file format (observed in the sources)

- `.iflw` is BPMN 2.0 XML (`bpmn2:` namespace) with SAP extensions as `ifl:property` elements
  (`key` / `value` children) under `bpmn2:extensionElements`. Participants are
  `bpmn2:participant`; adapters are `bpmn2:messageFlow` with properties `ComponentType`
  (JMS, ProcessDirect, XI, IDOC, SOAP, HTTP…), `direction` (Sender / Receiver), `Name`, and the
  adapter's configuration (e.g. `QueueName_outbound`, `address`). Steps are `bpmn2:callActivity`
  / `bpmn2:serviceTask` with property `activityType` (Script, Enricher, XSLTMapping,
  ProcessCallElement, ExternalCall, IdempotentProcessCall…) and `cmdVariantUri`; routers are
  `bpmn2:exclusiveGateway`, conditions on `bpmn2:sequenceFlow` (`expressionType`, body text).
  Local processes are `bpmn2:subProcess`; exception subprocesses carry `isForCompensation` /
  event definitions. Step order = `sequenceFlow` walk from `startEvent`, not XML order.
- Externalized parameters appear as `{{Name}}` inside property values; defaults in
  `parameters.propdef` (XML), configured values in `parameters.prop` (Java properties).
- Scripts referenced as `script=<file>.groovy` in the Script step's properties; some templates
  reference a shared script collection by `scriptBundleId`.

## Inputs provenance (KB-00)

- `knowledge/inputs/en/confluence-2026-10/`: 39 pages translated from the owner's Czech Confluence
  space (PDF export 2026-10-19), written from a reverse analysis at 1.0.4/1.0.5; the ten
  `generic-*` pages carry full script listings. Position-sorted PDF extraction was needed; the
  translations keep PDF line wraps inside code blocks in places.
- `knowledge/inputs/en/tenant-export-review-2026-09.md`: ChatGPT analysis of the September export,
  with a version table (1.0.5 → 1.0.6 etc.) and deviations found.
- Two tenant-specific pages (runtime configuration + naming convention, partner directory
  configuration) exist only under `testdata/` — input for part 2.

## Build and environment

- JDK 25, Maven 3.9; `mvn -B -ntp -q verify`; tests needing `testdata/` skip when absent
  (`io.poxray.TestData`, `-Dpoxray.testdata=<dir>`).
- Windows checkouts need `git config core.longpaths true` (source paths exceed 260 characters);
  `.gitattributes` stores `knowledge/sources/**` without EOL conversion.
- CLI: `po-xray` group command (`PoXrayCommand.groupCommands`), Quarkus + Aesh, uber-jar
  `po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar`; MCP server in `po-xray-mcp`;
  reflection registration for native images in `po-xray-native`.
