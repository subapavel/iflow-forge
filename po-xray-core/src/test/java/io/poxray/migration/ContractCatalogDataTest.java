package io.poxray.migration;

import static io.poxray.migration.ContractCatalogFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.poxray.json.Json;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogInput.*;
import io.poxray.model.ContractCatalogReport;
import io.poxray.model.BpmPathReport.ProcessKey;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractCatalogDataTest {
    @TempDir Path temp;

    @Test void apiResponseAndDefaultKeepExplicitProducersWithoutBecomingRequestInputs() {
        var data = data(temp.resolve("timing.sca"));
        var process = data.processes().getFirst();
        assertFalse(process.steps().isEmpty(), "real P3 must expose a step for timing claims");
        var producer = new StepRef(process.key(), process.steps().getFirst().step());
        var values = List.of(new ValueClaim("/request", "input", null, List.of(producer), null, DESIGN),
                new ValueClaim("/reply", "api-response", producer, List.of(), null, DESIGN),
                new ValueClaim("/flag", "default", producer, List.of(producer), " false | literal ", DESIGN));
        var ports = List.of(new PortClaim("request", "Caller", "lookup", producer, DESIGN),
                new PortClaim("response", "API", "lookup", producer, DESIGN));
        var claim = new ContractClaim("timing", "script", "proposed-internal", null, null, ports, values, DESIGN);
        var report = ContractCatalog.of(null, data, new ContractCatalogInput(List.of(artifact("script", "local-script")), List.of(claim), null));
        var result = contract(report, "claim-contract:timing");
        assertEquals(values, result.values()); assertEquals(ports, result.ports());
        assertEquals(1, result.values().stream().filter(v -> v.role().equals("input")).count());
        assertEquals(producer, result.values().get(1).producer());
        assertEquals(" false | literal ", result.values().get(2).defaultValue());
        assertEquals(Json.write(data), Json.write(report.data()));
        String md = ContractCatalog.markdown(report);
        for (String text : List.of("api-response", "default", "/reply", "/flag", "lookup", "proposed", "candidate")) assertTrue(md.contains(text), text);
        assertFalse(md.contains("false | literal"), "pipe inside default must be escaped");
        assertTrue(md.contains("false \\| literal") || md.contains("false &#124; literal"));
    }

    @Test void missingProducerAndForeignProcessOrStepAreDiagnosedWithoutNameFallback() {
        var data = data(temp.resolve("invalid-timing.sca"));
        var process = data.processes().getFirst();
        var badProcess = new StepRef(new ProcessKey(999, 1, 1), process.steps().getFirst().step());
        var badStep = new StepRef(process.key(), 999);
        var values = List.of(new ValueClaim("/missing-default", "default", null, List.of(), "original", DESIGN),
                new ValueClaim("/missing-response", "api-response", null, List.of(), null, DESIGN),
                new ValueClaim("/bad-process", "api-response", badProcess, List.of(badStep), null, DESIGN),
                new ValueClaim("/no-evidence", "input", null, List.of(badStep), null, List.of()));
        var ports = List.of(new PortClaim("producer", "API", "call", badStep, DESIGN));
        var claim = new ContractClaim("invalid", "a", "unknown", null, null, ports, values, DESIGN);
        var report = ContractCatalog.of(null, data, new ContractCatalogInput(List.of(artifact("a", "local-script")), List.of(claim), null));
        assertEquals(values, contract(report, "claim-contract:invalid").values());
        assertEquals(ports, contract(report, "claim-contract:invalid").ports());
        diagnostic(report, "/missing-default", "/missing-response", "/bad-process", "/no-evidence", "999", "invalid");
        assertEquals(Json.write(data), Json.write(report.data()));
    }

    @Test void completeP3SnapshotAndMarkdownRetainDeclarationsCandidatesAndUnknownReasons() {
        var data = data(temp.resolve("structural.sca"));
        var process = data.processes().getFirst();
        assertFalse(process.declarations().isEmpty()); assertFalse(process.mappings().isEmpty());
        assertFalse(process.steps().isEmpty());
        var reasons = new ArrayList<>(process.reasons());
        process.declarations().forEach(d -> reasons.addAll(d.reasons()));
        process.steps().forEach(s -> reasons.addAll(s.reasons()));
        assertFalse(reasons.isEmpty(), "synthetic incomplete source must exercise unknown reasons");
        var report = ContractCatalog.of(null, data, EMPTY);
        assertEquals(data, report.data());
        assertEquals(process.steps(), report.data().processes().getFirst().steps());
        String md = ContractCatalog.markdown(report);
        assertEquals(md, ContractCatalog.markdown(report));
        for (String word : List.of("before", "after", "candidate", "initial")) assertTrue(md.toLowerCase(java.util.Locale.ROOT).contains(word), word);
        for (var reason : reasons) assertTrue(md.contains(reason), reason);
        for (var mapping : process.mappings()) {
            assertTrue(md.contains(mapping.mapping().id()), "mapping identity " + mapping.mapping().id());
            assertTrue(md.contains(mapping.readDomain()), "read domain");
        }
        for (var declaration : process.declarations()) assertTrue(md.contains(declaration.part().id()), "declaration identity");
        assertEquals(report, Json.mapper().readValue(Json.write(report), ContractCatalogReport.class));
    }

    @Test void constructorsSnapshotNestedListsAndJsonRetainsNullUnknowns() {
        var evidence = new ArrayList<>(DESIGN);
        var consumers = new ArrayList<StepRef>();
        var values = new ArrayList<>(List.of(new ValueClaim("/path", "input", null, consumers, null, evidence)));
        var ports = new ArrayList<>(List.of(new PortClaim("consumer", "Reader", null, null, evidence)));
        var contracts = new ArrayList<>(List.of(new ContractClaim("c", "a", "unknown", null, null, ports, values, evidence)));
        var artifacts = new ArrayList<>(List.of(new ArtifactClaim("a", "local-script", null, evidence)));
        var input = new ContractCatalogInput(artifacts, contracts, null);
        String inputJson = Json.write(input);
        evidence.clear(); consumers.add(new StepRef(new ProcessKey(1, 1, 1), 999)); values.clear(); ports.clear(); contracts.clear(); artifacts.clear();
        assertEquals(inputJson, Json.write(input));
        assertEquals(input, Json.mapper().readValue(inputJson, ContractCatalogInput.class));
        var data = data(temp.resolve("snapshot.sca"));
        var report = ContractCatalog.of(null, data, input);
        // Existing P3 constructors already deep-freeze retained Element maps and value lists.
        // Verify the catalog cannot expose a mutable alias through those nested source payloads.
        var element = report.data().processes().getFirst().objects().getFirst().source();
        assertThrows(UnsupportedOperationException.class, () -> element.attributes().put("changed", "value"));
        assertThrows(UnsupportedOperationException.class, () -> element.values().put("changed", List.of("value")));
        assertThrows(UnsupportedOperationException.class, () -> element.texts().put("changed", "value"));
        var reportArtifacts = new ArrayList<>(report.artifacts());
        var reportContracts = new ArrayList<>(report.contracts());
        var snapshot = new ContractCatalogReport(report.formatVersion(), report.analysisVersion(), reportArtifacts,
                reportContracts, report.dependencies(), report.data(), report.issues());
        String snapshotJson = Json.write(snapshot);
        reportArtifacts.clear(); reportContracts.clear();
        assertEquals(snapshotJson, Json.write(snapshot));
        for (List<?> frozen : List.of(input.artifacts(), input.contracts(), input.resources(), input.contracts().getFirst().values(),
                input.contracts().getFirst().ports(), input.contracts().getFirst().values().getFirst().consumers(),
                input.artifacts().getFirst().evidence(), report.artifacts(), report.contracts(), report.dependencies(), report.issues(),
                report.data().processes(), report.data().processes().getFirst().steps()))
            assertThrows(UnsupportedOperationException.class, frozen::clear);
        String json = Json.write(report);
        assertTrue(json.contains("\"xmlName\" : null")); assertTrue(json.contains("\"owner\" : null"));
        assertTrue(json.contains("\"producer\" : null")); assertTrue(json.contains("\"defaultValue\" : null"));
        assertEquals(report, Json.mapper().readValue(json, ContractCatalogReport.class));
        assertEquals(List.of(), new ContractCatalogInput(null, null, null).contracts());
        assertEquals(List.of(), new ContractCatalogReport(1, "contract-catalog-v1", null, null, null, null, null).issues());
    }
}
