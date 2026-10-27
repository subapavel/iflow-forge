package io.poxray.migration;

import static io.poxray.migration.ContractCatalogFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogInput.*;
import io.poxray.model.IntegrationInventory.XmlName;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractCatalogClaimsTest {
    @TempDir Path temp;

    @Test void explicitKindsRemainDistinctButProcessDirectCannotClassifyPlacement() {
        var kinds = List.of("shared-message-mapping", "iflow-mapping", "script-collection", "local-script", "mapping-function");
        var claims = new java.util.ArrayList<ArtifactClaim>();
        for (String kind : kinds) claims.add(new ArtifactClaim(kind, kind, "owner|text", DESIGN));
        var transport = List.of(new Evidence("transport", "flow.iflw", "ProcessDirect", "observed"));
        claims.add(new ArtifactClaim("transport", "shared-message-mapping", null, transport));
        claims.add(new ArtifactClaim("unsupported", "magic-placement", null, DESIGN));
        claims.add(new ArtifactClaim("no-proof", "iflow-mapping", null, List.of()));
        claims.add(new ArtifactClaim("blank-proof", "local-script", null, List.of(new Evidence("design-decision", "", "section", "proposed"))));
        var report = ContractCatalog.of(null, null, new ContractCatalogInput(claims, null, null));
        assertEquals(9, report.artifacts().size());
        assertEquals(kinds, report.artifacts().subList(0, 5).stream().map(a -> a.kind()).toList());
        assertTrue(report.artifacts().subList(5, 9).stream().allMatch(a -> a.kind().equals("unknown")));
        assertTrue(report.artifacts().stream().allMatch(a -> a.basis().equals("supplied")));
        assertEquals("owner|text", report.artifacts().getFirst().owner());
        assertNull(report.artifacts().get(5).owner()); assertEquals(transport, report.artifacts().get(5).evidence());
        diagnostic(report, "shared-message-mapping", "transport", "magic-placement", "no-proof", "blank-proof");
    }

    @Test void suppliedQNameAndDispositionNeverOverwriteSourceOrPromoteProposalToApproval() {
        var inventory = read(temp.resolve("claims.tpz"), definition());
        var valid = new ContractClaim("proposed", "source:external-definition:0", "proposed-internal", null,
                new XmlName("urn:proposed", "Payload"), List.of(), List.of(), DESIGN);
        var invalid = new ContractClaim("unproven", "source:external-definition:0", "preserved-external", null,
                new XmlName("urn:unproven", "Claimed"), null, null, List.of());
        var unsupported = new ContractClaim("unsupported", "source:external-definition:0", "approved", null, null, null, null, DESIGN);
        var report = ContractCatalog.of(inventory, null, new ContractCatalogInput(null, List.of(valid, invalid, unsupported), null));
        assertNull(contract(report, "source:external-definition:0:message:0").xmlName());
        assertEquals("unknown", contract(report, "source:external-definition:0:message:0").disposition());
        var proposed = contract(report, "claim-contract:proposed");
        assertEquals(valid.xmlName(), proposed.xmlName()); assertEquals("proposed-internal", proposed.disposition());
        assertEquals(DESIGN, proposed.evidence());
        assertEquals("unknown", contract(report, "claim-contract:unproven").disposition());
        assertNull(contract(report, "claim-contract:unproven").xmlName());
        assertEquals("unknown", contract(report, "claim-contract:unsupported").disposition());
        diagnostic(report, "unproven", "preserved-external", "urn:unproven", "Claimed", "approved");
        assertTrue(ContractCatalog.markdown(report).contains("proposed"));
    }

    @Test void duplicateAndBlankIdsPreserveEveryClaimAndAmbiguousOwnerReferences() {
        var a = artifact("duplicate", "local-script");
        var claims = List.of(a, a, artifact("", "script-collection"));
        var c = new ContractClaim("duplicate", "duplicate", "unknown", null, null, null, null, DESIGN);
        var resource = resource("r", "a.xsd", "");
        var binding = new ResourceBinding("r", "duplicate", resource.resource(), DESIGN);
        var report = ContractCatalog.of(null, null, new ContractCatalogInput(claims, List.of(c, c), List.of(binding, binding)));
        assertTrue(report.artifacts().stream().anyMatch(x -> x.id().equals("claim:duplicate:0")));
        assertTrue(report.artifacts().stream().anyMatch(x -> x.id().equals("claim:duplicate:1")));
        assertTrue(report.artifacts().stream().anyMatch(x -> x.id().equals("claim:2")));
        assertEquals(List.of("claim-contract:duplicate:0", "claim-contract:duplicate:1"), report.contracts().stream().map(x -> x.id()).toList());
        assertTrue(report.artifacts().stream().anyMatch(x -> x.id().equals("supplied-resource:r:0")));
        assertTrue(report.artifacts().stream().anyMatch(x -> x.id().equals("supplied-resource:r:1")));
        diagnostic(report, "duplicate", "r");
        assertTrue(report.contracts().stream().noneMatch(x -> "claim:duplicate:0".equals(x.artifact())));
    }

    @Test void unsupportedPortAndValueRolesRemainVisibleWithIndexedDiagnostics() {
        var port = new PortClaim("not-a-port", "Participant", "Operation", null, DESIGN);
        var value = new ValueClaim("/odd", "not-a-value", null, null, "literal", DESIGN);
        var claim = new ContractClaim("bad-roles", "missing-owner", "unknown", null, null, List.of(port), List.of(value), DESIGN);
        var report = ContractCatalog.of(null, null, new ContractCatalogInput(null, List.of(claim), null));
        var saved = contract(report, "claim-contract:bad-roles");
        assertEquals(List.of(port), saved.ports()); assertEquals(List.of(value), saved.values());
        diagnostic(report, "bad-roles", "missing-owner", "not-a-port", "not-a-value", "[0]");
        String markdown = ContractCatalog.markdown(report);
        for (String text : List.of("not-a-port", "not-a-value", "/odd", "Participant", "Operation")) assertTrue(markdown.contains(text), text);
    }
}
