package io.poxray.migration;

import static io.poxray.migration.ContractCatalogFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.IntegrationInventory.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractCatalogTest {
    @TempDir Path temp;

    @Test void parsedNativeAndExternalIdentitiesNeverInferExternalQName() {
        var inventory = read(temp.resolve("native-external.tpz"), definition(),
                object("ifmmessage", "mt", List.of("Native", "urn:esr"), link("TYPE", "ifmtypedef", List.of("Missing", "urn:esr")),
                        "<message><xmlPayloadNs>urn:native</xmlPayloadNs></message>", ""),
                object("ifmmessage", "unknown", List.of("Unknown", "urn:esr"), "", "<message/>", ""),
                object("ifmtypedef", "dt", List.of("Data", "urn:esr"), "", "<datatype><schema>"
                        + schema("<xs:element name='NativeRoot'/>") + "</schema></datatype>", ""));
        var report = ContractCatalog.of(inventory, null, EMPTY);
        assertEquals(1, report.formatVersion());
        assertEquals("contract-catalog-v1", report.analysisVersion());
        assertEquals(new XmlName("urn:native", "Native"), contract(report, "source:native-message-type:0:contract").xmlName());
        assertNull(contract(report, "source:native-message-type:1:contract").xmlName());
        var a = contract(report, "source:external-definition:0:message:0");
        var b = contract(report, "source:external-definition:0:message:1");
        assertEquals(A, a.esr().key()); assertEquals(B, b.esr().key());
        assertNull(a.xmlName()); assertNull(b.xmlName());
        assertEquals("source:external-definition:0", a.artifact());
        assertTrue(report.contracts().stream().anyMatch(c -> new XmlName("urn:wire", "WireRoot").equals(c.xmlName())));
        assertEquals(2, report.artifacts().stream().filter(x -> x.kind().equals("native-message-type")).count());
        assertTrue(report.artifacts().stream().anyMatch(x -> x.kind().equals("native-data-type")));
        assertTrue(report.contracts().stream().allMatch(c -> c.disposition().equals("unknown")));
        var nativeArtifact = report.artifacts().stream().filter(x -> x.id().equals("source:native-message-type:0")).findFirst().orElseThrow();
        assertNull(nativeArtifact.owner()); assertEquals("source", nativeArtifact.basis());
        assertTrue(nativeArtifact.evidence().stream().anyMatch(e -> "export".equals(e.kind()) && "inventory".equals(e.source())
                && "observed".equals(e.status()) && e.locator().contains("messageTypes[0]")));
        assertTrue(report.dependencies().stream().anyMatch(d -> d.artifact().equals(nativeArtifact.id()) && d.state().equals("missing")
                && d.esr().name().equals("Missing") && d.candidates().isEmpty()));
        assertTrue(report.issues().containsAll(inventory.issues()));
    }

    @Test void operationRolesBindFullExternalKeysAndMappingUsesStaySeparate() {
        String operation = object("ifmoper", "op", List.of("Service.Send", "urn:esr"),
                link("REQUEST", "ifmextmes", A) + link("RESPONSE", "ifmextmes", B) + link("FAULT", "ifmextmes", B),
                "<operation isSynchronous='true'/>", "");
        var inventory = read(temp.resolve("ports.tpz"), definition(),
                object("ifmmessif", "service", List.of("Service", "urn:esr"), "", "<interface/>", operation),
                object("XI_TRAFO", "map", List.of("Map", "urn:esr"), link("SOURCE_IFR_MESS", "ifmextmes", A)
                        + link("TARGET_IFR_MESS", "ifmextmes", B), "<mapping/>", ""));
        var report = ContractCatalog.of(inventory, null, EMPTY);
        var a = contract(report, "source:external-definition:0:message:0");
        var b = contract(report, "source:external-definition:0:message:1");
        assertEquals(List.of("request", "mapping-input"), a.ports().stream().map(p -> p.role()).toList());
        assertEquals(List.of("response", "fault", "mapping-output"), b.ports().stream().map(p -> p.role()).toList());
        var request = a.ports().getFirst();
        assertTrue(request.participant().contains("Service"));
        assertEquals(inventory.esr().serviceInterfaces().getFirst().operations().getFirst().name(), request.operation());
        assertNull(request.step());
        assertTrue(report.contracts().stream().filter(c -> c.id().contains(":declaration:")).allMatch(c -> c.ports().isEmpty()));
        assertTrue(report.dependencies().stream().filter(d -> "source:esr-message-mapping:0".equals(d.artifact()))
                .allMatch(d -> d.candidates().equals(List.of("source:external-definition:0"))));
        assertEquals(2, report.dependencies().stream().filter(d -> "source:esr-message-mapping:0".equals(d.artifact())).count());
    }

    @Test void duplicateSourceIdentitiesRemainAmbiguousInsteadOfFirstMatch() {
        var parsed = read(temp.resolve("duplicates.tpz"),
                object("ifmmessage", "mt", List.of("Native", "urn:esr"), link("TYPE", "ifmtypedef", List.of("Data", "urn:esr")), "<message/>", ""),
                object("ifmtypedef", "dt", List.of("Data", "urn:esr"), "", "<datatype/>", ""));
        var e = parsed.esr();
        var duplicate = new Esr(e.serviceInterfaces(), e.operationMappings(), e.messageMappings(), e.messageTypes(),
                List.of(e.dataTypes().getFirst(), e.dataTypes().getFirst()), e.externalDefinitions(), e.mappingPrograms(), e.functionLibraries(), e.importedArchives());
        var report = ContractCatalog.of(inventory(duplicate, parsed.issues()), null, EMPTY);
        var edge = report.dependencies().stream().filter(d -> d.artifact().equals("source:native-message-type:0")).findFirst().orElseThrow();
        assertEquals("ambiguous", edge.state());
        assertEquals(List.of("source:native-data-type:0", "source:native-data-type:1"), edge.candidates());
        assertEquals(2, report.artifacts().stream().filter(a -> a.kind().equals("native-data-type")).count());
        diagnostic(report, "Data");
    }

    @Test void lexicalImportsRetainTransitiveMissingLeafAndFiniteCycles() {
        var report = resources(resource("a", "contracts/a.xsd", "<xs:include schemaLocation='./b.xsd'/><xs:import namespace='urn:wire'/>") ,
                resource("b", "contracts/b.xsd", "<xs:include schemaLocation='a.xsd'/><xs:include schemaLocation='missing.xsd'/>") );
        assertEquals(4, report.dependencies().size());
        var ab = dependency(report, "supplied-resource:a", "./b.xsd");
        assertEquals("candidate", ab.state()); assertEquals(List.of("supplied-resource:b"), ab.candidates());
        assertEquals("candidate", dependency(report, "supplied-resource:b", "a.xsd").state());
        assertEquals("missing", dependency(report, "supplied-resource:b", "missing.xsd").state());
        var namespaceOnly = dependency(report, "supplied-resource:a", null);
        assertEquals("unknown", namespaceOnly.state()); assertTrue(namespaceOnly.candidates().isEmpty());
        diagnostic(report, "missing.xsd", "urn:wire");
        assertFalse(ab.evidence().isEmpty());
    }

    @Test void duplicatePathsCaseAndMissingSourcePathCannotResolveByNamespace() {
        var report = resources(resource("a", "schemas/a.xsd", "<xs:include schemaLocation='b.xsd'/><xs:include schemaLocation='B.xsd'/>") ,
                resource("b1", "schemas/b.xsd", ""), resource("b2", "schemas/./b.xsd", ""),
                resource("pathless", null, "<xs:include schemaLocation='b.xsd'/>") );
        var ambiguous = dependency(report, "supplied-resource:a", "b.xsd");
        assertEquals("ambiguous", ambiguous.state());
        assertEquals(List.of("supplied-resource:b1", "supplied-resource:b2"), ambiguous.candidates());
        assertEquals("missing", dependency(report, "supplied-resource:a", "B.xsd").state());
        assertEquals("unknown", dependency(report, "supplied-resource:pathless", "b.xsd").state());
        assertTrue(dependency(report, "supplied-resource:pathless", "b.xsd").candidates().isEmpty());
        diagnostic(report, "b.xsd");
    }

    @Test void nullInventoryAndLegacyExternalNamesRemainExplicitUnknowns() {
        var empty = ContractCatalog.of(null, null, null);
        assertTrue(empty.artifacts().isEmpty()); assertTrue(empty.contracts().isEmpty()); assertFalse(empty.issues().isEmpty());
        var legacy = new ExternalDefinition(new ObjRef("ifmextdef", "Legacy", "urn:esr", null, null, null), List.of("LegacyMessage"), null);
        var report = ContractCatalog.of(inventory(new Esr(null, null, null, null, null, List.of(legacy), null, null, null), List.of()), null, EMPTY);
        var contract = contract(report, "source:external-definition:0:legacy-message:0");
        assertNull(contract.xmlName()); assertEquals("unknown", contract.disposition());
        diagnostic(report, "LegacyMessage");
        assertTrue(ContractCatalog.markdown(report).contains("LegacyMessage"));
    }

    @Test void parsedFunctionsKeepTheirEnclosingOwnersAndLibraryDependency() throws Exception {
        String function = "<functionstorage version='XI7.1'><functionmodel><signature cacheType='0'/>"
                + "<name>sameName</name><key>same-key</key><implementation type='udf'><javaText>return null;</javaText>"
                + "</implementation></functionmodel></functionstorage>";
        String mapping = "<mappingtool><project><libstorage><entry name='usernamespace'>" + function + "</entry>"
                + "<entry name='Library'><libref><ref role='UsedFuncLib'><key><key typeID='FUNC_LIB' oid='lib'>"
                + "<elem>Library</elem><elem>urn:esr</elem></key></key></ref></libref></entry></libstorage><transformation/></project></mappingtool>";
        var inventory = read(temp.resolve("functions.tpz"),
                object("FUNC_LIB", "lib", List.of("Library", "urn:esr"), "", metadata(function), ""),
                object("XI_TRAFO", "map1", List.of("First", "urn:esr"), link("UsedFuncLib", "FUNC_LIB", List.of("Library", "urn:esr")), metadata(mapping), ""),
                object("XI_TRAFO", "map2", List.of("Second", "urn:esr"), link("UsedFuncLib", "FUNC_LIB", List.of("Library", "urn:esr")), metadata(mapping), ""));
        assertEquals(1, inventory.esr().functionLibraries().size());
        assertEquals(1, inventory.esr().messageMappings().getFirst().logic().localFunctions().functions().size());
        var report = ContractCatalog.of(inventory, null, EMPTY);
        var functions = report.artifacts().stream().filter(a -> a.kind().equals("mapping-function")).toList();
        assertEquals(3, functions.size());
        for (String owner : List.of("source:esr-message-mapping:0", "source:esr-message-mapping:1", "source:esr-function-library:0"))
            assertTrue(functions.stream().anyMatch(a -> a.id().equals(owner + ":function:0") && a.owner().equals(owner)));
        for (String owner : List.of("source:esr-message-mapping:0", "source:esr-message-mapping:1"))
            assertTrue(report.dependencies().stream().anyMatch(d -> d.artifact().equals(owner)
                    && d.candidates().equals(List.of("source:esr-function-library:0"))));
    }

    @Test void multipleDtDeclarationsDoNotMultiplyIdentityAndUnboundPortsStayDiagnostic() {
        String operation = object("ifmoper", "op", List.of("Service.Send", "urn:esr"),
                link("REQUEST", "ifmmessage", List.of("Absent", "urn:esr")), "<operation/>", "");
        var inventory = read(temp.resolve("dt-binding.tpz"),
                object("ifmtypedef", "dt", List.of("Data", "urn:esr"), "", "<datatype><schema>"
                        + schema("<xs:element name='One'/><xs:element name='Two'/>") + "</schema></datatype>", ""),
                object("ifmmessage", "mt", List.of("Native", "urn:esr"), link("TYPE", "ifmtypedef", List.of("Data", "urn:esr")), "<message/>", ""),
                object("XI_TRAFO", "map", List.of("Map", "urn:esr"), link("SOURCE_IFR_MESS", "ifmtypedef", List.of("Data", "urn:esr")), "<mapping/>", ""),
                object("ifmmessif", "service", List.of("Service", "urn:esr"), "", "<interface/>", operation));
        var report = ContractCatalog.of(inventory, null, EMPTY);
        var edge = report.dependencies().stream().filter(d -> d.artifact().equals("source:native-message-type:0")).findFirst().orElseThrow();
        assertEquals("resolved", edge.state()); assertEquals(List.of("source:native-data-type:0"), edge.candidates());
        var declarations = report.contracts().stream().filter(c -> c.xmlName() != null && List.of("One", "Two").contains(c.xmlName().localName())).toList();
        assertEquals(2, declarations.size()); assertTrue(declarations.stream().allMatch(c -> c.ports().isEmpty()));
        diagnostic(report, "Absent", "request", "Service", "Send", "Data");
        assertTrue(report.dependencies().stream().anyMatch(d -> d.artifact().equals("source:esr-message-mapping:0")
                && d.candidates().equals(List.of("source:native-data-type:0"))));
    }

    @Test void uriMatchingDoesNotDecodeOrReinterpretMalformedAndOpaquePaths() {
        var report = resources(resource("a", "schemas/a.xsd", "<xs:include schemaLocation='b%20c.xsd'/><xs:include schemaLocation='bad[.xsd'/>") ,
                resource("encoded", "schemas/b%20c.xsd", ""), resource("opaque", "urn:opaque", "<xs:include schemaLocation='child.xsd'/>") );
        assertEquals(List.of("supplied-resource:encoded"), dependency(report, "supplied-resource:a", "b%20c.xsd").candidates());
        assertEquals("candidate", dependency(report, "supplied-resource:a", "b%20c.xsd").state());
        assertNotEquals("resolved", dependency(report, "supplied-resource:a", "bad[.xsd").state());
        assertTrue(dependency(report, "supplied-resource:opaque", "child.xsd").candidates().isEmpty());
        diagnostic(report, "bad[.xsd", "urn:opaque");
    }

    @Test void oidPrecedenceVersionContextAndLegacyCandidatesStayDistinct() {
        var parsed = read(temp.resolve("identity.tpz"),
                object("ifmmessage", "mt", List.of("Native", "urn:esr"), "", "<message/>", ""),
                object("ifmtypedef", "dt", List.of("Data", "urn:esr"), "", "<datatype/>", ""));
        var e = parsed.esr();
        var original = e.dataTypes().getFirst();
        var other = new DataType(new ObjRef("ifmtypedef", "Data", "urn:esr", "Other", "bbbb", "other", null,
                original.id().key()), original.uses(), original.xsd(), original.description());
        var references = List.of(
                new ObjRef("ifmtypedef", "Stale", "urn:stale", "Other", "bbbb", "dt", null, List.of("Stale", "urn:stale")),
                new ObjRef("ifmtypedef", "Data", "urn:esr", "Synthetic", "aaaa", null, null, original.id().key()),
                new ObjRef("ifmtypedef", "Data", "urn:esr", null, null, null),
                new ObjRef("ifmtypedef", "Data", "urn:esr", "Synthetic", "aaaa", null),
                new ObjRef("ifmtypedef", "data", "urn:esr", null, null, null));
        var expectedStates = List.of("resolved", "resolved", "ambiguous", "candidate", "missing");
        var expectedCandidates = List.of(List.of("source:native-data-type:0"), List.of("source:native-data-type:0"),
                List.of("source:native-data-type:0", "source:native-data-type:1"), List.of("source:native-data-type:0"), List.<String>of());
        for (int index = 0; index < references.size(); index++) {
            var mt = new MessageType(e.messageTypes().getFirst().id(), references.get(index), null, null);
            var esr = new Esr(e.serviceInterfaces(), e.operationMappings(), e.messageMappings(), List.of(mt), List.of(original, other),
                    e.externalDefinitions(), e.mappingPrograms(), e.functionLibraries(), e.importedArchives());
            var report = ContractCatalog.of(inventory(esr, List.of()), null, EMPTY);
            var edge = report.dependencies().stream().filter(d -> d.artifact().equals("source:native-message-type:0")).findFirst().orElseThrow();
            assertEquals(expectedStates.get(index), edge.state(), "identity variant " + index);
            assertEquals(expectedCandidates.get(index), edge.candidates(), "identity variant " + index);
        }
    }

    @Test void nestedWsdlSchemasRemainSeparateAndSharedPathCandidatesAreAmbiguous() {
        String wsdl = "<w:definitions xmlns:w='http://schemas.xmlsoap.org/wsdl/' xmlns:x='http://www.w3.org/2001/XMLSchema'>"
                + "<w:types><x:schema targetNamespace='urn:one'><x:element name='One'/></x:schema>"
                + "<x:schema targetNamespace='urn:two'><x:element name='Two'/></x:schema></w:types></w:definitions>";
        var parsed = io.poxray.integration.ContractResources.read("contracts/all.wsdl", wsdl);
        assertEquals(3, parsed.size());
        var bindings = new java.util.ArrayList<io.poxray.model.ContractCatalogInput.ResourceBinding>();
        bindings.add(resource("entry", "contracts/entry.xsd", "<xs:import namespace='urn:one' schemaLocation='all.wsdl'/>"));
        for (int i = 0; i < parsed.size(); i++) bindings.add(new io.poxray.model.ContractCatalogInput.ResourceBinding("nested" + i, "xml", parsed.get(i), DESIGN));
        var report = resources(bindings.toArray(io.poxray.model.ContractCatalogInput.ResourceBinding[]::new));
        var edge = dependency(report, "supplied-resource:entry", "all.wsdl");
        assertEquals("ambiguous", edge.state());
        assertEquals(List.of("supplied-resource:nested0", "supplied-resource:nested1", "supplied-resource:nested2"), edge.candidates());
        assertTrue(report.contracts().stream().anyMatch(c -> new XmlName("urn:one", "One").equals(c.xmlName())));
        assertTrue(report.contracts().stream().anyMatch(c -> new XmlName("urn:two", "Two").equals(c.xmlName())));
    }
}
