package io.poxray.migration;

import io.poxray.IssueLog;
import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.SyntheticTpz;
import io.poxray.bpm.BpmDataAnalysis;
import io.poxray.integration.ContractResources;
import io.poxray.integration.IntegrationReader;
import io.poxray.model.BpmDataReport;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogInput.*;
import io.poxray.model.ContractCatalogReport;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import io.poxray.xi.InMemoryObjectSource;
import io.poxray.xi.TpzArchive;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Literal synthetic exports and explicit attributed claims; no catalog logic here. */
final class ContractCatalogFixtures {
    static final List<String> A = List.of("Contract", "urn:esr", "Same", "urn:key:a", "");
    static final List<String> B = List.of("Contract", "urn:esr", "Same", "urn:key:b", "tail");
    static final List<Evidence> DESIGN = List.of(new Evidence("design-decision", "proposal.md", "section 7", "proposed"));
    static final ContractCatalogInput EMPTY = new ContractCatalogInput(null, null, null);
    static String schema(String body) {
        return "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:wire'>" + body + "</xs:schema>";
    }
    static String key(String type, String oid, List<String> key) {
        return "<p:key typeID='" + type + "'" + (oid == null ? "" : " oid='" + oid + "'") + ">"
                + key.stream().map(k -> "<p:elem>" + k + "</p:elem>").reduce("", String::concat) + "</p:key>";
    }
    static String object(String type, String oid, List<String> key, String links, String content, String inner) {
        return "<p:xiObj xmlns:p='urn:sap-com:xi'><p:idInfo><p:vc vcType='S' swcGuid='aaaa' caption='Synthetic'/>"
                + key(type, oid, key) + "</p:idInfo><p:generic><p:lnks>" + links
                + "</p:lnks></p:generic><p:content>" + content + "</p:content><p:inner>" + inner + "</p:inner></p:xiObj>";
    }
    static String link(String role, String type, List<String> key) {
        return "<p:lnkRole role='" + role + "'><p:lnk><p:vc vcType='S' swcGuid='aaaa'/>"
                + key(type, null, key) + "</p:lnk></p:lnkRole>";
    }
    static String definition() {
        return object("ifmextdef", "def", List.of("Contract", "urn:esr"), "",
                schema("<xs:element name='WireRoot'/><xs:complexType name='Record'/>"),
                object("ifmextmes", "a", A, "", "", "") + object("ifmextmes", "b", B, "", "", ""));
    }
    static String metadata(String xml) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("metadata.xml"));
            zip.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return "<mapping><MetaData><blob>" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()) + "</blob></MetaData></mapping>";
    }
    static IntegrationInventory read(Path path, String... objects) {
        var source = new InMemoryObjectSource().addAll(TpzArchive.open(SyntheticTpz.write(path, "", List.of(objects))).objects());
        var issues = new IssueLog();
        var reader = new IntegrationReader(source, issues);
        // Function libraries are reached through mapping references; do not read them twice by a different key.
        for (String type : List.of("ifmextdef", "ifmmessage", "ifmtypedef", "ifmmessif", "XI_TRAFO"))
            for (var object : source.ofType(type)) {
                // MT expansion can already have read a DT by key, while this enumeration uses its OID.
                if (type.equals("ifmtypedef") && reader.esr().dataTypes().stream()
                        .anyMatch(dt -> java.util.Objects.equals(dt.id().oid(), object.id().oid()))) continue;
                reader.read(object.id());
            }
        return inventory(reader.esr(), issues.issues());
    }
    static IntegrationInventory inventory(Esr esr, List<io.poxray.model.Issue> issues) {
        return new IntegrationInventory("0.1", null, null, null, List.of(), List.of(), List.of(), List.of(), esr, issues);
    }
    static BpmDataReport data(Path path) {
        return BpmDataAnalysis.of(List.of(PoXray.read(SyntheticBpmSca.write(path, SyntheticBpmSca.mappedProcess()))), List.of("synthetic"));
    }
    static ArtifactClaim artifact(String id, String kind) { return new ArtifactClaim(id, kind, null, DESIGN); }
    static ResourceBinding resource(String id, String path, String body) {
        return new ResourceBinding(id, "xml", ContractResources.read(path, schema(body)).getFirst(), DESIGN);
    }
    static ContractCatalogReport resources(ResourceBinding... resources) {
        return ContractCatalog.of(null, null, new ContractCatalogInput(List.of(artifact("xml", "xml-resource")), List.of(), List.of(resources)));
    }
    static ContractCatalogReport.Contract contract(ContractCatalogReport report, String id) {
        return report.contracts().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
    static ContractCatalogReport.Dependency dependency(ContractCatalogReport report, String artifact, String location) {
        return report.dependencies().stream().filter(d -> d.artifact().equals(artifact) && java.util.Objects.equals(d.location(), location))
                .findFirst().orElseThrow();
    }
    static String diagnostics(ContractCatalogReport report) { return io.poxray.json.Json.write(report.issues()); }
    static void diagnostic(ContractCatalogReport report, String... retained) {
        assertFalse(report.issues().isEmpty());
        String text = diagnostics(report);
        for (String token : retained) assertTrue(text.contains(token), "diagnostics must retain " + token + ": " + text);
    }
}
