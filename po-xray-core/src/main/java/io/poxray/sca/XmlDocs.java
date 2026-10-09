package io.poxray.sca;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Small DOM helpers for descriptor files (.dcdef, .composite, ...). Namespace aware, DTDs disabled. */
public final class XmlDocs {

    private XmlDocs() {
    }

    public static Document parse(byte[] content, String name) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(content));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Cannot parse XML " + name + ": " + e.getMessage(), e);
        }
    }

    /** Direct child elements with the given local name (any namespace); empty for a null parent. */
    public static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    public static Element child(Element parent, String localName) {
        List<Element> l = children(parent, localName);
        return l.isEmpty() ? null : l.getFirst();
    }

    /** Trimmed text of the first direct child with the given local name, or null. */
    public static String childText(Element parent, String localName) {
        Element c = parent == null ? null : child(parent, localName);
        if (c == null) {
            return null;
        }
        String t = c.getTextContent().trim();
        return t.isEmpty() ? null : t;
    }
}
