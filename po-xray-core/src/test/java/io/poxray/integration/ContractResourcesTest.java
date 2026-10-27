package io.poxray.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.poxray.model.IntegrationInventory.*;
import java.io.StringReader;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

class ContractResourcesTest {
    @Test
    void schemaDependenciesAndDeclarationsKeepOrder() {
        String xml = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:foreign" targetNamespace="urn:wire">
                  <xs:import namespace="urn:other" schemaLocation="../other.xsd"/>
                  <xs:include schemaLocation="local.xsd"/><xs:import namespace="urn:missing"/>
                  <xs:redefine schemaLocation="base.xsd"/><xs:include schemaLocation="local.xsd"/>
                  <xs:element name="Root"><xs:complexType><xs:sequence><xs:element name="Local"/></xs:sequence></xs:complexType></xs:element>
                  <xs:attribute name="Flag"/><xs:complexType name="Record"/><xs:simpleType name="Code"/>
                  <xs:group name="Group"/><xs:attributeGroup name="Attrs"/>
                  <f:element name="Fake"/><f:import schemaLocation="fake.xsd"/><xs:element/>
                </xs:schema>
                """;
        var all = ContractResources.read("provided/contracts.xsd", xml);
        assertEquals(1, all.size());
        var r = all.getFirst();
        assertEquals("provided/contracts.xsd", r.sourcePath());
        assertEquals("xsd", r.kind());
        assertEquals(List.of("element", "attribute", "complexType", "simpleType", "group", "attributeGroup"),
                r.declarations().stream().map(ContractDeclaration::kind).toList());
        assertEquals(List.of("Root", "Flag", "Record", "Code", "Group", "Attrs"),
                r.declarations().stream().map(d -> d.name().localName()).toList());
        assertTrue(r.declarations().stream().allMatch(d -> "urn:wire".equals(d.name().namespace())));
        assertEquals(List.of("import", "include", "import", "redefine", "include"),
                r.dependencies().stream().map(ContractDependency::kind).toList());
        assertEquals("urn:other", r.dependencies().get(0).namespace());
        assertEquals("../other.xsd", r.dependencies().get(0).location());
        assertNull(r.dependencies().get(1).namespace());
        assertNull(r.dependencies().get(2).location());
        assertEquals("base.xsd", r.dependencies().get(3).location());
        assertEquals("local.xsd", r.dependencies().get(4).location());
        assertNotEquals(r.dependencies().get(1).locator(), r.dependencies().get(4).locator());
        assertEquals(all, ContractResources.read("provided/contracts.xsd", xml));
        assertTrue(r.locator().contains("[1]"));
        assertTrue(r.declarations().stream().allMatch(d -> d.locator().startsWith(r.locator() + "/")));
        assertTrue(r.dependencies().stream().allMatch(d -> d.locator().startsWith(r.locator() + "/")));
        assertEquals(new XmlName("", "NoNamespace"), ContractResources.read(null,
                "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"><element name=\"NoNamespace\"/></schema>")
                .getFirst().declarations().getFirst().name());
    }

    @Test
    void wsdlAndNestedSchemasRemainSeparateResources() throws Exception {
        String xml = """
                <wrapper xmlns:w="http://schemas.xmlsoap.org/wsdl/" xmlns:x="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:shared">
                  <w:definitions targetNamespace="urn:service">
                    <w:import namespace="urn:one" location="one.wsdl"/><w:import namespace="urn:two"/>
                    <w:types><x:schema targetNamespace="urn:first"><x:element name="First" type="t:Shared"/></x:schema>
                      <schema xmlns="urn:foreign"><element name="NotAnXsdDeclaration"/></schema>
                      <x:schema targetNamespace="urn:second"><x:element name="Second"/></x:schema></w:types>
                    <w:message name="Request"/><w:portType name="Port"><w:operation name="NotDeclaration"/></w:portType>
                    <w:binding name="Binding"/><w:service name="Service"/>
                  </w:definitions>
                  <definitions xmlns="urn:foreign"><message name="Fake"/></definitions>
                </wrapper>
                """;
        var resources = ContractResources.read("provided/service.wsdl", xml);
        assertEquals(List.of("wsdl", "xsd", "xsd"), resources.stream().map(ContractResource::kind).toList());
        assertEquals(3, resources.stream().map(ContractResource::locator).distinct().count());
        assertEquals(resources, ContractResources.read("provided/service.wsdl", xml));
        var wsdl = resources.getFirst();
        assertEquals(List.of("message", "portType", "binding", "service"), wsdl.declarations().stream().map(ContractDeclaration::kind).toList());
        assertTrue(wsdl.declarations().stream().allMatch(d -> "urn:service".equals(d.name().namespace())));
        assertEquals(2, wsdl.dependencies().size());
        assertEquals("import", wsdl.dependencies().getFirst().kind());
        assertEquals("urn:one", wsdl.dependencies().getFirst().namespace());
        assertEquals("one.wsdl", wsdl.dependencies().getFirst().location());
        assertNull(wsdl.dependencies().get(1).location());
        assertEquals(new XmlName("urn:first", "First"), resources.get(1).declarations().getFirst().name());
        assertEquals(new XmlName("urn:second", "Second"), resources.get(2).declarations().getFirst().name());
        for (var r : resources) {
            assertEquals("provided/service.wsdl", r.sourcePath());
            assertNotNull(parse(r.content()).getNamespaceURI());
        }
        Element schema = parse(resources.get(1).content());
        Element root = (Element) schema.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element").item(0);
        assertEquals("t:Shared", root.getAttribute("type"));
        assertEquals("urn:shared", root.lookupNamespaceURI("t"));
    }

    @Test
    void resourcesRejectMalformedXmlAndDoNotResolveExternalEntities() {
        assertTrue(ContractResources.read(null, null).isEmpty());
        assertTrue(ContractResources.read(null, "  ").isEmpty());
        assertTrue(ContractResources.read(null, "<schema><element name=\"Fake\"/></schema>").isEmpty());
        assertNotNull(assertThrows(IllegalArgumentException.class,
                () -> ContractResources.read("bad.xml", "<broken>")).getCause());
        // Reject the declaration before the nonexistent file/loopback URL could be opened.
        for (String uri : List.of("file:///nonexistent-synthetic-contract.dtd", "http://127.0.0.1:1/synthetic.dtd")) {
            String hostile = "<!DOCTYPE schema SYSTEM \"" + uri + "\"><schema xmlns=\"http://www.w3.org/2001/XMLSchema\"/>";
            try {
                assertTrue(ContractResources.read("hostile.xml", hostile).isEmpty(), "DTD input must be rejected or produce no resources");
            } catch (IllegalArgumentException rejected) {
                assertNotNull(rejected.getCause());
                String message = String.valueOf(rejected.getCause().getMessage()).toLowerCase(java.util.Locale.ROOT);
                assertTrue(message.contains("doctype") || message.contains("dtd"), "must reject DTD, not fail fetching it: " + message);
            }
        }
    }

    static Element parse(String xml) throws Exception {
        return DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }
}
