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

## APIs (verified)
- JUnit Jupiter is 6.1.3 (javap): `Assumptions.assumeFalse(boolean, Supplier<String>)` exists.
- Aesh 3.18.2: several positional `@Argument` fields per command work with explicit `index = "0"`, `"1"` (javap:
  `ProcessedCommand.addArgument`, `validatePositionalIndexes`; exercised by CpiCommandsTest).
- Jackson 3.2.3: `JsonNode.asString()` / `stringValue()` (`asText()` is the legacy final alias), `readTree(byte[])`,
  JsonNode is Iterable; `Json` NON_EMPTY keeps primitive 0 / false (IFlowDumpTest), drops "" and empty lists.
- `io.poxray.sca.ZipArchive.open/of` throw UncheckedIOException; nested zip bytes via `ZipArchive.of(bytes, name)`.

## .iflw / SAP artifact facts (knowledge/sources/2026-06-trial)
- Some `.iflw` file names contain spaces: quote paths in shell loops (`find -print0 | xargs -0`).
- All 75 `bpmn2:subProcess` are event subprocesses (no incoming sequenceFlow). No duplicate `ifl:property` keys.
- `IdempotentProcessCall` steps also carry `processId` (a local process); the reader inlines under ANY step with a
  `processId` naming a process of the file (architect decision on KB-02).
- MANIFEST.MF: CRLF, folded at 72 bytes (continuation = one space + 71 bytes), like java.util.jar.Manifest.
