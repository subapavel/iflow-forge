package io.poxray.integration;

import io.poxray.model.DataType;
import io.poxray.sca.XmlDocs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 * Flattens the XSD of an ESR data type into field paths ({@code Header/Id}, {@code Items/Item/Qty}) with type and
 * cardinality. Inline complex types are followed; references to other data types are shown by type name.
 */
public final class XsdFields {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema";
    private static final int MAX_DEPTH = 20;

    private XsdFields() {
    }

    /** Fields of the complex type named {@code typeName} (or of the first complex type) in {@code xsd}. */
    public static List<DataType.Field> fields(String xsd, String typeName) {
        if (xsd == null || xsd.isBlank()) {
            return List.of();
        }
        Element schema;
        try {
            schema = XmlDocs.parse(xsd.getBytes(StandardCharsets.UTF_8), typeName).getDocumentElement();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        Element type = null;
        for (Element ct : XmlDocs.children(schema, "complexType")) {
            if (type == null || ct.getAttribute("name").equals(typeName)) {
                type = ct;
            }
        }
        List<DataType.Field> out = new ArrayList<>();
        if (type != null) {
            collect(type, "", out, 0);
        }
        return out;
    }

    private static void collect(Element parent, String prefix, List<DataType.Field> out, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !XSD.equals(e.getNamespaceURI())) {
                continue;
            }
            switch (e.getLocalName()) {
                case "sequence", "all", "choice", "complexContent", "extension", "restriction" ->
                        collect(e, prefix, out, depth + 1);
                case "attribute" -> out.add(new DataType.Field(
                        (prefix.isEmpty() ? "" : prefix + "/") + "@" + e.getAttribute("name"),
                        blank(e.getAttribute("type")), "required".equals(e.getAttribute("use")) ? 1 : 0, null));
                case "element" -> {
                    String name = e.hasAttribute("ref") ? e.getAttribute("ref") : e.getAttribute("name");
                    String path = prefix.isEmpty() ? name : prefix + "/" + name;
                    Element inline = XmlDocs.child(e, "complexType");
                    String type = blank(e.getAttribute("type"));
                    if (type == null && XmlDocs.child(e, "simpleType") != null) {
                        Element restriction = XmlDocs.child(XmlDocs.child(e, "simpleType"), "restriction");
                        type = restriction == null ? null : blank(restriction.getAttribute("base"));
                    }
                    out.add(new DataType.Field(path, type, min(e.getAttribute("minOccurs")), max(e.getAttribute("maxOccurs"))));
                    if (inline != null) {
                        collect(inline, path, out, depth + 1);
                    }
                }
                default -> {
                    // annotation, documentation, ...
                }
            }
        }
    }

    private static Integer min(String v) {
        return v.isEmpty() || "1".equals(v) ? null : Integer.valueOf(v);
    }

    private static String max(String v) {
        return v.isEmpty() || "1".equals(v) ? null : v;
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
