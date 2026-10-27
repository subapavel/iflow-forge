package io.poxray.integration;

import static io.poxray.integration.SyntheticExternalContracts.*;
import static org.junit.jupiter.api.Assertions.*;

import io.poxray.IssueLog;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory.*;
import io.poxray.xi.XiRef;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalContractsTest {
    @TempDir Path temp;

    @Test
    void directExternalDefinitionKeepsOidAndFullMessageKeys() {
        var source = source(temp.resolve("direct.tpz"), definition(XSD));
        var reader = new IntegrationReader(source, new IssueLog());
        // OID must win over a deliberately stale key and version context.
        var result = reader.read(new XiRef("S", "bbbb", "Other", "ifmextdef", "definition-oid",
                List.of("Stale", "urn:stale")));
        assertInstanceOf(ExternalDefinition.class, result);
        var def = (ExternalDefinition) result;
        assertEquals("definition-oid", def.id().oid());
        assertEquals("aaaa", def.id().swcGuid());
        assertEquals(List.of("Contract", "urn:esr"), def.id().key());
        assertEquals(List.of("Same", "Same"), def.messages());
        assertEquals(List.of(A, B), def.messageReferences().stream().map(ObjRef::key).toList());
        assertEquals(List.of("message-a", "message-b"), def.messageReferences().stream().map(ObjRef::oid).toList());
        assertNotEquals(def.messageReferences().get(0), def.messageReferences().get(1));
    }

    @Test
    void operationAndMappingEndpointsKeepExternalMessageKey() {
        var programKey = List.of("Archive", "urn:esr", "class", "synthetic/Mapper");
        String operation = object("ifmoper", "operation", List.of("Service.Send", "urn:esr"),
                link("REQUEST", "ifmextmes", A) + link("RESPONSE", "ifmextmes", B)
                        + link("FAULT", "ifmextmes", B), "<operation isSynchronous=\"true\"/>", "");
        var source = source(temp.resolve("endpoints.tpz"), definition(XSD),
                object("ifmmessif", "service", List.of("Service", "urn:esr"), "", "<interface/>", operation),
                object("XI_TRAFO", "mm", List.of("Map", "urn:esr"),
                        link("SOURCE_IFR_MESS", "ifmextmes", A) + link("TARGET_IFR_MESS", "ifmextmes", B), "<mapping/>", ""),
                object("MAPPING", "om", List.of("OperationMap", "urn:esr"),
                        link("REQUEST_OUT_MES", "ifmextmes", A) + link("REQUEST_IN_MES", "ifmextmes", B)
                                + link("REQUEST_TRAFO", "MAP_ARCHIVE_PRG", programKey), "<mapping/>", ""));
        var reader = new IntegrationReader(source, new IssueLog());
        for (String type : List.of("ifmmessif", "XI_TRAFO", "MAPPING")) reader.read(source.ofType(type).getFirst().id());
        var op = reader.esr().serviceInterfaces().getFirst().operations().getFirst();
        assertEquals(A, op.request().key());
        assertEquals(B, op.response().key());
        assertEquals(B, op.faults().getFirst().key());
        assertNull(op.request().program());
        var mm = reader.esr().messageMappings().getFirst();
        assertEquals(A, mm.source().key());
        assertEquals(B, mm.target().key());
        var step = reader.esr().operationMappings().getFirst().steps().getFirst();
        assertEquals(A, step.sourceMessage().key());
        assertEquals(B, step.targetMessage().key());
        assertEquals(programKey, step.program().key());
        assertEquals("synthetic.Mapper", step.program().program());
    }

    @Test
    void externalSchemaIsResourceNotNativeMessageType() {
        var source = source(temp.resolve("schema.tpz"), definition(XSD));
        var reader = new IntegrationReader(source, new IssueLog());
        reader.read(source.ofType("ifmextdef").getFirst().id());
        var def = reader.esr().externalDefinitions().getFirst();
        assertTrue(def.content().contains("WireRoot"));
        assertEquals(1, def.resources().size());
        var resource = def.resources().getFirst();
        assertNull(resource.sourcePath());
        assertEquals("xsd", resource.kind());
        assertEquals(new XmlName("urn:wire", "WireRoot"), resource.declarations().getFirst().name());
        assertTrue(reader.esr().messageTypes().isEmpty());
        assertTrue(reader.esr().dataTypes().isEmpty());
        assertEquals(def, Json.mapper().readValue(Json.write(def), ExternalDefinition.class));
    }

    @Test
    void messageExpansionThenDirectOidReadReturnsSameDefinition() {
        var source = source(temp.resolve("read-order.tpz"), definition(XSD));
        var reader = new IntegrationReader(source, new IssueLog());
        reader.expand(new XiRef("S", "aaaa", "Synthetic", "ifmextmes", null, A));
        var expanded = reader.esr().externalDefinitions().getFirst();
        var direct = reader.read(source.ofType("ifmextdef").getFirst().id());
        assertEquals(expanded, direct);
        assertEquals(1, reader.esr().externalDefinitions().size());
    }

    @Test
    void inheritedQNameNamespaceSurvivesExternalContentSerialization() throws Exception {
        String xml = definition("<xs:schema targetNamespace=\"urn:wire\"><xs:element name=\"Root\" type=\"t:Shared\"/></xs:schema>")
                .replace("<p:xiObj xmlns:p=\"urn:sap-com:xi\">", "<p:xiObj xmlns:p=\"urn:sap-com:xi\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:t=\"urn:shared\">");
        var source = source(temp.resolve("inherited.tpz"), xml);
        var reader = new IntegrationReader(source, new IssueLog());
        reader.read(source.ofType("ifmextdef").getFirst().id());
        var def = reader.esr().externalDefinitions().getFirst();
        for (String serialized : List.of(def.content(), def.resources().getFirst().content())) {
            var schema = ContractResourcesTest.parse(serialized);
            var root = (org.w3c.dom.Element) schema.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);
            assertEquals("t:Shared", root.getAttribute("type"));
            assertEquals("urn:shared", root.lookupNamespaceURI("t"));
        }
    }

    @Test
    void unknownExternalContentDoesNotInventQName() {
        for (String content : List.of("", "<opaque><schema name=\"Same\"/></opaque>")) {
            var source = source(temp.resolve("opaque.tpz"), definition(content));
            var reader = new IntegrationReader(source, new IssueLog());
            reader.read(source.ofType("ifmextdef").getFirst().id());
            var def = reader.esr().externalDefinitions().getFirst();
            assertTrue(def.resources().isEmpty());
            assertEquals(List.of(A, B), def.messageReferences().stream().map(ObjRef::key).toList());
            if (content.isEmpty()) assertNull(def.content());
            else assertTrue(def.content().contains("opaque"));
        }
        var legacy = Json.mapper().readValue("{\"id\":null,\"messages\":[\"Same\"],\"description\":\"old\"}", ExternalDefinition.class);
        assertEquals(new ExternalDefinition(null, List.of("Same"), "old"), legacy);
        assertNull(legacy.content());
        assertEquals(List.of(), legacy.messageReferences());
        assertEquals(List.of(), legacy.resources());
        var six = new ObjRef("t", "n", "ns", "s", "g", "o");
        var seven = new ObjRef("t", "n", "ns", "s", "g", "o", "program");
        assertEquals(List.of(), six.key());
        assertEquals(List.of(), seven.key());
        assertEquals("program", seven.program());
        assertEquals(six, Json.mapper().readValue(Json.write(six), ObjRef.class));
        assertEquals(List.of(), Json.mapper().readValue("{\"type\":\"t\",\"key\":null}", ObjRef.class).key());
    }

    @Test
    void nativeMtDtDependencyEvidenceIsUnchanged() {
        var source = source(temp.resolve("native.tpz"),
                object("ifmmessage", "mt", List.of("Message", "urn:esr"), link("TYPE", "ifmtypedef", List.of("Parent", "urn:esr")),
                        "<message><xmlPayloadNs>urn:native-wire</xmlPayloadNs></message>", ""),
                object("ifmtypedef", "parent", List.of("Parent", "urn:esr"), link("TYPE", "ifmtypedef", List.of("Child", "urn:esr")),
                        "<datatype><schema>" + XSD + "</schema></datatype>", ""),
                object("ifmtypedef", "child", List.of("Child", "urn:esr"), "", "<datatype/>", ""));
        var reader = new IntegrationReader(source, new IssueLog());
        reader.read(source.ofType("ifmmessage").getFirst().id());
        var mt = reader.esr().messageTypes().getFirst();
        assertEquals("urn:native-wire", mt.xmlNamespace());
        assertEquals("Parent", mt.dataType().name());
        assertEquals(2, reader.esr().dataTypes().size());
        var parent = reader.esr().dataTypes().stream().filter(d -> d.id().name().equals("Parent")).findFirst().orElseThrow();
        assertEquals(List.of("Child"), parent.uses().stream().map(r -> r.name()).toList());
        assertTrue(parent.xsd().contains("WireRoot"));
        assertTrue(reader.esr().externalDefinitions().isEmpty());
    }

    @Test
    void newListsAreDefensiveSnapshots() {
        var keys = new ArrayList<>(A);
        var ref = new ObjRef("ifmextmes", "Contract", "urn:esr", "s", "g", "o", null, keys);
        var refs = new ArrayList<>(List.of(ref));
        var declarations = new ArrayList<>(List.of(new ContractDeclaration("element", new XmlName("n", "E"), "/schema[1]/element[1]")));
        var dependencies = new ArrayList<>(List.of(new ContractDependency("include", null, "child.xsd", "/schema[1]/include[1]")));
        var resource = new ContractResource("a.xsd", "/schema[1]", "xsd", XSD, declarations, dependencies);
        var resources = new ArrayList<>(List.of(resource));
        var def = new ExternalDefinition(ref, List.of("Same"), null, XSD, refs, resources);
        keys.clear(); refs.clear(); declarations.clear(); dependencies.clear(); resources.clear();
        assertEquals(A, ref.key());
        assertEquals(List.of(ref), def.messageReferences());
        assertEquals(List.of(resource), def.resources());
        assertEquals(1, resource.declarations().size());
        assertEquals(1, resource.dependencies().size());
        assertThrows(UnsupportedOperationException.class, () -> ref.key().clear());
        assertThrows(UnsupportedOperationException.class, () -> def.messageReferences().clear());
        assertThrows(UnsupportedOperationException.class, () -> def.resources().clear());
        assertThrows(UnsupportedOperationException.class, () -> resource.declarations().clear());
        assertThrows(UnsupportedOperationException.class, () -> resource.dependencies().clear());
        assertEquals(List.of(), new ObjRef("t", "n", null, null, null, null, null, null).key());
        var empty = new ExternalDefinition(null, List.of(), null, null, null, null);
        assertEquals(List.of(), empty.resources());
        assertEquals(List.of(), empty.messageReferences());
        var oldResource = Json.mapper().readValue("{\"kind\":\"xsd\",\"declarations\":null,\"dependencies\":null}", ContractResource.class);
        assertEquals(List.of(), oldResource.declarations());
        assertEquals(List.of(), oldResource.dependencies());
    }
}
