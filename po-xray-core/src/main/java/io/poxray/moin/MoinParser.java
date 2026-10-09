package io.poxray.moin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Parser of MOIN partition files (root {@code Partition}, namespace {@value #MOIN_NS} for {@code m:*}).
 * <p>
 * Top level children: {@code C} (containers) and {@code M} (metaclass declarations) are skipped;
 * elements with {@code m:i} are model elements; any other child is an association grouping {@code L} links.
 */
public final class MoinParser {

    public static final String MOIN_NS = "http://xml.sap.com/moin";
    private static final String LOCAL_PREFIX = "local:";

    private static final XMLInputFactory XML = createFactory();

    private MoinParser() {
    }

    /** True if the file content looks like a MOIN partition (checked on the first bytes). */
    public static boolean isPartition(byte[] content) {
        int len = Math.min(content.length, 512);
        return new String(content, 0, len, StandardCharsets.UTF_8).contains("<Partition");
    }

    /** Strips the {@code local:} prefix used for references into other partitions of the same DC. */
    public static String normalizeRef(String ref) {
        return ref != null && ref.startsWith(LOCAL_PREFIX) ? ref.substring(LOCAL_PREFIX.length()) : ref;
    }

    public static MoinPartition parse(String path, byte[] content) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            XMLStreamReader r = XML.createXMLStreamReader(in);
            try {
                return parse(path, r);
            } finally {
                r.close();
            }
        } catch (XMLStreamException | IOException e) {
            throw new MoinException("Cannot parse MOIN partition " + path + ": " + e.getMessage(), e);
        }
    }

    private static MoinPartition parse(String path, XMLStreamReader r) throws XMLStreamException {
        List<MoinElement> elements = new ArrayList<>();
        List<MoinLink> links = new ArrayList<>();
        int depth = 0;
        // state of the current top-level child
        String topTag = null;
        boolean topIsElement = false;
        boolean topIsAssociation = false;
        String id = null;
        Map<String, String> attrs = null;
        Map<String, String> texts = null;
        Map<String, List<String>> values = null;
        Map<String, String> props = null;

        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                depth++;
                String tag = r.getLocalName();
                if (depth == 1) {
                    if (!"Partition".equals(tag)) {
                        throw new MoinException("Not a MOIN partition (root <" + tag + ">): " + path, null);
                    }
                } else if (depth == 2) {
                    topTag = tag;
                    id = r.getAttributeValue(MOIN_NS, "i");
                    boolean header = "C".equals(tag) || "M".equals(tag);
                    topIsElement = !header && id != null;
                    topIsAssociation = !header && id == null;
                    if (topIsElement) {
                        attrs = new LinkedHashMap<>();
                        texts = new LinkedHashMap<>();
                        values = new LinkedHashMap<>();
                        props = new LinkedHashMap<>();
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if (!MOIN_NS.equals(r.getAttributeNamespace(i))) {
                                attrs.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                            }
                        }
                    }
                } else if (depth == 3) {
                    if (topIsElement) {
                        String originalText = r.getAttributeValue(null, "originalText");
                        String v = r.getAttributeValue(null, "v");
                        if (originalText != null) {
                            texts.put(tag, originalText);
                        } else if ("properties".equals(tag)) {
                            props.put(r.getAttributeValue(null, "key"), r.getAttributeValue(null, "value"));
                        } else if (v != null) {
                            values.computeIfAbsent(tag, k -> new ArrayList<>()).add(v);
                        }
                    } else if (topIsAssociation && "L".equals(tag)) {
                        links.add(new MoinLink(topTag,
                                normalizeRef(r.getAttributeValue(null, "f")),
                                normalizeRef(r.getAttributeValue(null, "s"))));
                    }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                if (depth == 2) {
                    if (topIsElement) {
                        elements.add(new MoinElement(id, topTag, attrs, texts, values, props, path));
                    }
                    topTag = null;
                    topIsElement = false;
                    topIsAssociation = false;
                }
                depth--;
            }
        }
        return new MoinPartition(path, List.copyOf(elements), List.copyOf(links));
    }

    private static XMLInputFactory createFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f;
    }
}
