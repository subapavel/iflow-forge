package io.poxray.integration;

import io.poxray.model.IntegrationInventory.ContractDeclaration;
import io.poxray.model.IntegrationInventory.ContractDependency;
import io.poxray.model.IntegrationInventory.ContractResource;
import io.poxray.model.IntegrationInventory.XmlName;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Reads explicit XML contract evidence without resolving imports or ESR message bindings. */
public final class ContractResources {
    private static final String XSD = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final String WSDL = "http://schemas.xmlsoap.org/wsdl/";
    private static final Set<String> XSD_DECLARATIONS = Set.of("element", "attribute", "complexType",
            "simpleType", "group", "attributeGroup");
    private static final Set<String> WSDL_DECLARATIONS = Set.of("message", "portType", "binding", "service");

    private ContractResources() {
    }

    public static List<ContractResource> read(String sourcePath, String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            var factory = DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            var root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
            List<ContractResource> result = new ArrayList<>();
            walk(sourcePath, root, "/" + root.getTagName() + "[1]", result);
            return List.copyOf(result);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read XML contract resources: " + e.getMessage(), e);
        }
    }

    private static void walk(String sourcePath, Element element, String locator, List<ContractResource> result) {
        boolean xsd = XSD.equals(element.getNamespaceURI()) && "schema".equals(element.getLocalName());
        boolean wsdl = WSDL.equals(element.getNamespaceURI()) && "definitions".equals(element.getLocalName());
        if (xsd || wsdl) {
            String ns = xsd ? XSD : WSDL;
            Set<String> named = xsd ? XSD_DECLARATIONS : WSDL_DECLARATIONS;
            List<ContractDeclaration> declarations = new ArrayList<>();
            List<ContractDependency> dependencies = new ArrayList<>();
            for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element child) || !ns.equals(child.getNamespaceURI())) {
                    continue;
                }
                String kind = child.getLocalName();
                String at = locator + "/" + segment(child);
                String name = attribute(child, "name");
                if (named.contains(kind) && name != null && !name.isEmpty()) {
                    declarations.add(new ContractDeclaration(kind,
                            new XmlName(element.getAttribute("targetNamespace"), name), at));
                }
                if ("import".equals(kind) || xsd && ("include".equals(kind) || "redefine".equals(kind))) {
                    dependencies.add(new ContractDependency(kind, attribute(child, "namespace"),
                            attribute(child, xsd ? "schemaLocation" : "location"), at));
                }
            }
            result.add(new ContractResource(sourcePath, locator, xsd ? "xsd" : "wsdl", serialize(element),
                    declarations, dependencies));
        }
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element child) {
                walk(sourcePath, child, locator + "/" + segment(child), result);
            }
        }
    }

    /** Indexed source element path, counting siblings with the same qualified tag name. */
    private static String segment(Element element) {
        int index = 1;
        for (Node n = element.getPreviousSibling(); n != null; n = n.getPreviousSibling()) {
            if (n instanceof Element sibling && element.getTagName().equals(sibling.getTagName())) {
                index++;
            }
        }
        return element.getTagName() + "[" + index + "]";
    }

    private static String attribute(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    /** Preserve in-scope prefixes even when they are used only in QName attribute values. */
    static String serialize(Element element) {
        try {
            Element copy = (Element) element.cloneNode(true);
            Set<String> seen = new HashSet<>();
            for (Node n = element; n instanceof Element ancestor; n = n.getParentNode()) {
                var attributes = ancestor.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node a = attributes.item(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI()) && seen.add(a.getNodeName())) {
                        copy.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, a.getNodeName(), a.getNodeValue());
                    }
                }
            }
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            var out = new StringWriter();
            transformer.transform(new DOMSource(copy), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize XML contract resource", e);
        }
    }
}
