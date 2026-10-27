package io.poxray.migration;

import static org.junit.jupiter.api.Assertions.*;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogInput.*;
import io.poxray.model.ContractCatalogReport;
import io.poxray.model.IntegrationInventory.ContractResource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractCatalogIdCollisionTest {
    // Index 5 reserves the first fallback for index 0; blank index 3 collides with literal "3".
    private static final List<String> RAW = List.of("x", "x", "x:0", "", "3", "x:0:occurrence:0");
    private static final List<String> SUFFIXES = List.of("x:0:occurrence:0:occurrence:0", "x:1",
            "x:0:occurrence:2", "3:occurrence:3", "3:occurrence:4", "x:0:occurrence:0");

    @Test void artifactIdsReserveLiteralAndFallbackBasesWithoutLosingOccurrences() {
        var input = new ContractCatalogInput(artifacts(), null, null);
        var report = ContractCatalog.of(null, null, input);
        assertIds("claim:", report.artifacts().stream().map(a -> a.id()).toList());
        for (int i = 0; i < RAW.size(); i++) {
            assertEquals("descriptive-owner-" + i, report.artifacts().get(i).owner());
            assertEquals(evidence("artifacts", i), report.artifacts().get(i).evidence());
        }
        assertEquals(report, ContractCatalog.of(null, null, input));
    }

    @Test void contractIdsReserveLiteralAndFallbackBasesIndependentlyOfArtifactIds() {
        var contracts = new ArrayList<ContractClaim>();
        for (int i = 0; i < RAW.size(); i++) contracts.add(new ContractClaim(RAW.get(i), "x:0", "unknown",
                null, null, null, null, evidence("contracts", i)));
        var input = new ContractCatalogInput(artifacts(), contracts, null);
        var report = ContractCatalog.of(null, null, input);
        assertIds("claim-contract:", report.contracts().stream().map(c -> c.id()).toList());
        assertEquals(RAW.size(), report.artifacts().size());
        for (int i = 0; i < RAW.size(); i++) {
            assertEquals("claim:x:0:occurrence:2", report.contracts().get(i).artifact());
            assertEquals(evidence("contracts", i), report.contracts().get(i).evidence());
        }
        assertEquals(report, ContractCatalog.of(null, null, input));
    }

    @Test void resourceIdsReserveLiteralAndFallbackBasesWithoutLosingOwnerOrEvidence() {
        var resources = new ArrayList<ResourceBinding>();
        for (int i = 0; i < RAW.size(); i++) resources.add(new ResourceBinding(RAW.get(i), "x:0",
                resource(i), evidence("resources", i)));
        var input = new ContractCatalogInput(artifacts(), null, resources);
        var report = ContractCatalog.of(null, null, input);
        var saved = report.artifacts().stream().filter(a -> a.id().startsWith("supplied-resource:")).toList();
        assertIds("supplied-resource:", saved.stream().map(a -> a.id()).toList());
        assertEquals(RAW.size() * 2, report.artifacts().size());
        for (int i = 0; i < RAW.size(); i++) {
            assertEquals("claim:x:0:occurrence:2", saved.get(i).owner());
            assertEquals(evidence("resources", i), saved.get(i).evidence());
        }
        assertEquals(report, ContractCatalog.of(null, null, input));
    }

    @Test void ownerLookupUsesUniqueRawIdsAndNeverSelectsDuplicateByOutputSuffix() {
        var contracts = List.of(
                new ContractClaim("unique", "x:0", "unknown", null, null, null, null, evidence("contracts", 2)),
                new ContractClaim("ambiguous", "x", "unknown", null, null, null, null, evidence("contracts", 0)),
                new ContractClaim("fallback-raw", "x:0:occurrence:0", "unknown", null, null, null, null, evidence("contracts", 5)));
        var resources = List.of(new ResourceBinding("unique", "x:0", resource(2), evidence("resources", 2)),
                new ResourceBinding("ambiguous", "x", resource(0), evidence("resources", 0)),
                new ResourceBinding("fallback-raw", "x:0:occurrence:0", resource(5), evidence("resources", 5)));
        var report = ContractCatalog.of(null, null, new ContractCatalogInput(artifacts(), contracts, resources));
        assertEquals(3, report.contracts().size());
        assertEquals(9, report.artifacts().size());
        assertEquals("claim:x:0:occurrence:2", report.contracts().get(0).artifact());
        assertEquals("claim:x:0:occurrence:0", report.contracts().get(2).artifact());
        var saved = report.artifacts().stream().filter(a -> a.id().startsWith("supplied-resource:")).toList();
        assertEquals("claim:x:0:occurrence:2", saved.get(0).owner());
        assertEquals("claim:x:0:occurrence:0", saved.get(2).owner());
        var artifactIds = report.artifacts().stream().filter(a -> a.id().startsWith("claim:")).map(a -> a.id()).toList();
        assertFalse(artifactIds.contains(report.contracts().get(1).artifact()), "duplicate raw owner must not choose any artifact");
        assertFalse(artifactIds.contains(saved.get(1).owner()), "duplicate resource owner must not choose any artifact");
        String diagnostics = io.poxray.json.Json.write(report.issues());
        assertTrue(diagnostics.contains("ambiguous"));
        assertTrue(diagnostics.contains("x"));
        assertEquals(evidence("contracts", 0), report.contracts().get(1).evidence());
        assertEquals(evidence("resources", 0), saved.get(1).evidence());
    }

    private static List<ArtifactClaim> artifacts() {
        var result = new ArrayList<ArtifactClaim>();
        for (int i = 0; i < RAW.size(); i++) result.add(new ArtifactClaim(RAW.get(i), "xml-resource",
                "descriptive-owner-" + i, evidence("artifacts", i)));
        return result;
    }
    private static List<Evidence> evidence(String domain, int index) {
        return List.of(new Evidence("artifact-declaration", "synthetic-claims.json",
                domain + "[" + index + "] raw-id='" + RAW.get(index) + "'", "proposed"));
    }
    private static ContractResource resource(int index) {
        return new ContractResource("synthetic/" + index + ".xsd", "/schema[1]", "xsd", null, null, null);
    }
    private static void assertIds(String prefix, List<String> ids) {
        assertEquals(SUFFIXES.stream().map(s -> prefix + s).toList(), ids);
        assertEquals(RAW.size(), ids.size());
        assertEquals(RAW.size(), ids.stream().distinct().count());
    }
}
