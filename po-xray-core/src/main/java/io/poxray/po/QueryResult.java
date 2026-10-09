package io.poxray.po;

import io.poxray.sca.XmlDocs;
import io.poxray.xi.XiObjectParser;
import io.poxray.xi.XiRef;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.w3c.dom.Element;

/**
 * SimpleQuery result ({@code queryResult}): rows of named columns. Cells are plain values ({@code simple}),
 * object references ({@code qref}, e.g. RA_XILINK) or reference lists ({@code array}, e.g. RA_LINK_LIST).
 * {@code keyNames}: names of the key elements per type ({@code typeInfo}, e.g. AllInOne: FROMPARTY, FROMSRVC,
 * FROMACTIONNS, FROMACTION, TOPARTY, TOSRVC), present even when no rows match.
 */
public record QueryResult(List<String> columns, List<Row> rows, Map<String, List<String>> keyNames) {

    /** One result row: column name → value ({@code String}, {@link XiRef} or {@code List<XiRef>}). */
    public record Row(Map<String, Object> cells) {

        public String text(String column) {
            Object v = cells.get(column);
            return v instanceof String s ? s : null;
        }

        public XiRef ref(String column) {
            Object v = cells.get(column);
            return v instanceof XiRef r ? r : null;
        }

        @SuppressWarnings("unchecked")
        public List<XiRef> refs(String column) {
            Object v = cells.get(column);
            return v instanceof List<?> l ? (List<XiRef>) l : List.of();
        }

        /** The object reference of the row (RA_XILINK). */
        public XiRef xiLink() {
            return ref("RA_XILINK");
        }
    }

    public static QueryResult parse(String xml) {
        Element root = XmlDocs.parse(xml.getBytes(StandardCharsets.UTF_8), "queryResult").getDocumentElement();
        Map<Integer, String> byPos = new TreeMap<>();
        Element header = XmlDocs.child(root, "headerInfo");
        Element colDef = header == null ? null : XmlDocs.child(header, "colDef");
        if (colDef != null) {
            for (Element def : XmlDocs.children(colDef, "def")) {
                byPos.put(Integer.parseInt(def.getAttribute("pos")), def.getAttribute("type"));
            }
        }
        List<String> columns = new ArrayList<>(byPos.values());
        List<Row> rows = new ArrayList<>();
        Element matrix = XmlDocs.child(root, "matrix");
        if (matrix != null) {
            for (Element r : XmlDocs.children(matrix, "r")) {
                Map<String, Object> cells = new LinkedHashMap<>();
                List<Element> cs = XmlDocs.children(r, "c");
                for (int i = 0; i < cs.size(); i++) {
                    String column = byPos.getOrDefault(i, "COL" + i);
                    Object value = cell(cs.get(i));
                    if (value != null) {
                        cells.put(column, value);
                    }
                }
                rows.add(new Row(cells));
            }
        }
        Map<String, List<String>> keyNames = new LinkedHashMap<>();
        Element typeInfo = XmlDocs.child(root, "typeInfo");
        for (Element type : typeInfo == null ? List.<Element>of() : XmlDocs.children(typeInfo, "type")) {
            Map<Integer, String> names = new TreeMap<>();
            for (Element k : XmlDocs.children(type, "keyElem")) {
                names.put(Integer.parseInt(k.getAttribute("pos")), k.getAttribute("name"));
            }
            keyNames.put(type.getAttribute("id"), List.copyOf(names.values()));
        }
        return new QueryResult(List.copyOf(columns), List.copyOf(rows), Map.copyOf(keyNames));
    }

    private static Object cell(Element c) {
        Element simple = XmlDocs.child(c, "simple");
        if (simple != null) {
            for (org.w3c.dom.Node n = simple.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    return e.getTextContent();
                }
            }
            return null;
        }
        Element qref = XmlDocs.child(c, "qref");
        if (qref != null) {
            Element ref = XmlDocs.child(qref, "ref");
            return ref == null ? null : ref(ref);
        }
        Element array = XmlDocs.child(c, "array");
        if (array != null) {
            List<XiRef> refs = new ArrayList<>();
            for (Element ref : XmlDocs.children(array, "ref")) {
                refs.add(ref(ref));
            }
            return List.copyOf(refs);
        }
        return null;
    }

    /** {@code <ref><vc .../><key typeID oid><elem/>...</key><vspec/></ref>} */
    private static XiRef ref(Element ref) {
        Element vc = XmlDocs.child(ref, "vc");
        Element key = XmlDocs.child(ref, "key");
        List<String> elems = new ArrayList<>();
        if (key != null) {
            for (Element e : XmlDocs.children(key, "elem")) {
                elems.add(e.getTextContent());
            }
        }
        String guid = vc == null ? null : emptyToNull(vc.getAttribute("swcGuid"));
        return new XiRef(vc == null ? null : emptyToNull(vc.getAttribute("vcType")),
                guid == null ? null : XiObjectParser.normalizeGuid(guid),
                vc == null ? null : emptyToNull(vc.getAttribute("caption")),
                key == null ? null : emptyToNull(key.getAttribute("typeID")),
                key == null ? null : emptyToNull(key.getAttribute("oid")),
                List.copyOf(elems));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
