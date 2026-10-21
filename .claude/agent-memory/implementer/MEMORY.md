# implementer memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.

## Build
- `mvn -q` prints nothing on success: take counts from `<module>/target/surefire-reports/TEST-*.xml` (`tests`,
  `skipped`, `failures`, `errors` attributes of `<testsuite>`).
- Never `mvn install -pl <module>` alone: it installs the module without the parent pom and later `-pl` runs fail
  on dependency resolution. Run a CLI test through the reactor:
  `mvn -B -ntp -q -pl po-xray-cli -am test -Dtest=X -Dsurefire.failIfNoSpecifiedTests=false`.
- `po-xray-cli` tests see core test classes via the core test-jar, but package-private fixtures (e.g.
  `io.poxray.cpi.IFlowFixture`) are not usable from `io.poxray.cli`: build synthetic inputs inline.
- iflow-forge-knowledge: module surefire `<systemPropertyVariables combine.children="append">` keeps the root's
  `poxray.testdata` / `iflowforge.sources`; `-Dkb.skip=...` / `-Dkb.forbidden=...` pass through; `-Dkb.skip=` (empty) overrides the pom default; relative kb.forbidden resolves against the repo root.
  Synthetic CPI artifacts from there via the public bridge `io.poxray.cpi.SyntheticArtifact` (its test sources).
- `[Fatal Error] :1:9: XML document structures...` on stderr is a po-xray-core test's expected parse error.
- CLI jar after `verify`: `po-xray-cli/target/po-xray-cli-<version>-runner.jar` (uber-jar, no `quarkus-app/`).
- Reactor modules with surefire reports: po-xray-core, po-xray-native, po-xray-cli, po-xray-mcp (not "iflow-forge-mcp"), iflow-forge-knowledge.
- Every new class under `io.poxray.model` (records AND nested records) must be added to `targets` of
  `po-xray-native/.../nativeimage/NativeReflection.java`, else `NativeReflectionTest` fails root `verify`. Ask for that
  path in the packet when a task adds a model type.

## APIs (verified)
- JUnit Jupiter is 6.1.3 (javap): `Assumptions.assumeFalse(boolean, Supplier<String>)` exists.
- Aesh 3.18.2: several positional `@Argument` fields per command work with explicit `index = "0"`, `"1"` (javap:
  `ProcessedCommand.addArgument`, `validatePositionalIndexes`; exercised by CpiCommandsTest).
- Jackson 3.2.3: `JsonNode.asString()` / `stringValue()` (`asText()` is the legacy final alias), `readTree(byte[])`,
  JsonNode is Iterable; `Json` NON_EMPTY keeps primitive 0 / false (IFlowDumpTest), drops "" and empty lists.
- Aesh 3.18.2 `@Arguments List<String>` (javap) takes several positional values; `required = true` with none
  given exits 2 before `execute`.
- Json NON_EMPTY drops an empty `issues` list, so a CLI test reading the JSON back sees null: a report whose
  consumers assert on `issues()` carries `@JsonInclude(NON_NULL)` on that component (AttributesReport, BpmInventoryReport).
- `tools.jackson.core.JacksonException` (unchecked) has `getOriginalMessage()` without the location suffix.
- `io.poxray.sca.ZipArchive.open/of` throw UncheckedIOException; nested zip bytes via `ZipArchive.of(bytes, name)`.
- CLI stdout: `invocation.println(...)` (LaunchResult.getOutput); stderr notes via System.err; user errors as
  `ScaService.CliException` inside `service.run` (exit 1, "Error: <msg>").
- `XmlDocs.parse(bytes, name)`: namespace-aware DOM, DOCTYPE disallowed, IllegalArgumentException on bad XML.
- `git diff` misses untracked files and staged `git rm`: build target/<task>.diff from `git diff HEAD -- <paths>`
  plus `git diff --no-index /dev/null <new file>`.

## .iflw / SAP artifact facts (knowledge/sources/2026-06; label was 2026-06-trial before KB-14)
- Some `.iflw` file names contain spaces: quote paths in shell loops (`find -print0 | xargs -0`).
- All 75 `bpmn2:subProcess` are event subprocesses (no incoming sequenceFlow). No duplicate `ifl:property` keys.
- `IdempotentProcessCall` steps also carry `processId` (a local process); the reader inlines under ANY step with a
  `processId` naming a process of the file (architect decision on KB-02).
- MANIFEST.MF: CRLF, folded at 72 bytes (continuation = one space + 71 bytes), like java.util.jar.Manifest.
- `IFlow` record (id, name, version, participants, steps, parameters, resources) keeps no source path and no resource
  bytes; `resources()` are paths relative to `src/main/resources/`. To read a resource's text, find artifact folders
  (dir with `META-INF/MANIFEST.MF`) yourself and `IFlowReader.read(dir)` each (Checks.artifacts does this).
- `Step.properties()` values are entity-decoded (`headerTable` = `<row><cell id='Name'>X</cell>...</row>`, single
  quotes). Delete-action rows carry patterns as Name: `$name=SAP_Sender`, `$include=SAP_*`, `$include=dc(.*)`.
- Mutation checks: `git diff | grep -c '^[-+] '` does not change when the mutated line is already an added line;
  restore from a baseline copy in the scratchpad and compare `sha1sum` instead.
- Hand-written po-xray JSON: verify the style by round-trip — `mvn -pl po-xray-core dependency:build-classpath
  -Dmdep.outputFile=<scratch>/cp.txt`, then a jshell script reading the file into the record and `Json.write`, diff.
  `Json.write` emits `"key" : value`, 2-space indent, arrays `[ {` inline, no trailing newline.
- Reader literals: condition `operator` is the SAP code (`EQ`, `CP`, `EX`), constants quoted (`"\"X\""`), `text`
  renders `=`/`like`; ICO target `multiplicity` from real data is `1` (message mapping's is `1:1`); XI sender's
  standard module is `CallSapAdapter` (type `L`, param `ignoreDuplicate`).
- po-xray ObjRef types: service interface `ifmmessif`, message type `ifmmessage` (TypeKeys); match esr refs on
  name + namespace, never on type. `po-xray-core` alone tests fine with `-pl po-xray-core test -Dtest=X` (no `-am`).
