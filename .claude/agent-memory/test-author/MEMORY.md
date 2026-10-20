# test-author memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.
- po-xray-core test fixture `io.poxray.cpi.IFlowFixture` (KB-02): builds unpacked iFlow/ScriptCollection folders, `zip(dir, zip)`, `packageExport(dir, artifactDirs, changelog)`, `manifestHeaders(path)`.
- Jackson 3.2.3 `JsonNode.propertyNames()` returns ordered `Collection<String>` (no `fieldNames()`); `ObjectMapper.readTree(String)` throws unchecked JacksonException.
- Pre-red type check: compile the tests with `javac -Xlint:all` against throwaway stubs in the scratchpad (classpath from `mvn -pl <module> dependency:build-classpath -Dmdep.outputFile=...` + `<module>/target/classes`), never in `src/`.
- Real .iflw: the sender messageFlow's targetRef is the main process's startEvent, not the IntegrationProcess participant; local processes may precede the main `bpmn2:process` in document order.
- `IFlowFixture` is package-private (io.poxray.cpi); other modules reach it via a public bridge in their own test sources under `io/poxray/cpi/` — iflow-forge-knowledge has `io.poxray.cpi.SyntheticArtifact.write(dir, id, resources...)` (one Script step, resources under src/main/resources/).
- `IFlowReader.readAll(folder)` recurses plain subfolders; `IFlow.resources()` are paths relative to src/main/resources/, sorted (e.g. `mapping/B.xsl`).
- `SyntheticArtifact.withStep(dir, id, stepName, activityType, Map stepProperties, Map resources)` (KB-07): one step with extra `ifl:property` pairs (IFlowFixture `esc` entity-encodes values, so table row XML lands as `&lt;row&gt;...` like real .iflw) and resources with exact content.
- Real content-modifier table value: `<row><cell id='Action'>Create</cell><cell id='Type'>constant</cell><cell id='Value'>..</cell><cell id='Default'></cell><cell id='Name'>X</cell><cell id='Datatype'></cell></row>`; empty table is `<value/>`.
- po-xray-core test fixture `io.poxray.bpm.SyntheticNwdsPlugins.write(dir)` (KB-13): servicecomp/bpemcontent plugin jars with MOIN `.function`/`.eventdef`/`.types`; public id/file constants; reachable from po-xray-cli tests (core test-jar).
- XML 1.0 cannot carry U+0001..U+001F (except tab/LF/CR) even as `&#N;`; put such chars in text read by regex (e.g. `.types`), and use `&#10;`/`&#9;`/`&#13;` for LF/TAB/CR inside attributes (literal ones are normalized to spaces).
- Cross-check a hand-written byte-exact expectation with a throwaway reference port compiled with javac in the scratchpad (never in src/); run it against the fixture and diff line by line.
- Jackson 3.2.3 `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` defaults to false (javap: ctor arg iconst_0); strict read = `Json.mapper().readerFor(T.class).with(FAIL_ON_UNKNOWN_PROPERTIES).readValue(path)`.
- iflow-forge-knowledge pom sets `kb.root` literally (not `${kb.root}`), so `-Dkb.root` on the mvn line does not override; to try a test against a scratch KB, run the compiled test class via a throwaway reflective main in the same package with `java -Dkb.root=...` (classpath: module test-classes + po-xray-core/target/classes + `mvn -pl po-xray-core dependency:build-classpath`).
- `KnowledgeBase.skipIfRequested(name)` matches comma-separated check names in `kb.skip`; `-Dkb.skip=true` skips nothing. ForbiddenValuesTest is skipped (1) when `kb.forbidden` is unset.
