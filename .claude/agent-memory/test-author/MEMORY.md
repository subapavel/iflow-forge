# test-author memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.
- po-xray-core test fixture `io.poxray.cpi.IFlowFixture` (KB-02): builds unpacked iFlow/ScriptCollection folders, `zip(dir, zip)`, `packageExport(dir, artifactDirs, changelog)`, `manifestHeaders(path)`.
- Jackson 3.2.3 `JsonNode.propertyNames()` returns ordered `Collection<String>` (no `fieldNames()`); `ObjectMapper.readTree(String)` throws unchecked JacksonException.
- Pre-red type check: compile the tests with `javac -Xlint:all` against throwaway stubs in the scratchpad (classpath from `mvn -pl <module> dependency:build-classpath -Dmdep.outputFile=...` + `<module>/target/classes`), never in `src/`.
- Real .iflw: the sender messageFlow's targetRef is the main process's startEvent, not the IntegrationProcess participant; local processes may precede the main `bpmn2:process` in document order.
- `IFlowFixture` is package-private (io.poxray.cpi); other modules reach it via a public bridge in their own test sources under `io/poxray/cpi/` — iflow-forge-knowledge has `io.poxray.cpi.SyntheticArtifact.write(dir, id, resources...)` (one Script step, resources under src/main/resources/).
- `IFlowReader.readAll(folder)` recurses plain subfolders; `IFlow.resources()` are paths relative to src/main/resources/, sorted (e.g. `mapping/B.xsl`).
