package io.poxray.bpm;

import io.poxray.sca.XmlDocs;
import io.poxray.sca.ZipArchive;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Generates the NW BPM built-in function and standard trigger catalog ({@code bpm-builtin-functions.json}, read
 * by {@code BuiltinFunctionCatalog}) from an NWDS plugins folder: {@code po-xray bpm-functions <nwds-plugins-dir>
 * <output.json>}. The output is derived from SAP software: keep it in private repositories only.
 *
 * <p>Sources: {@code com.sap.tc.ap.servicecomp_*.jar} (DC {@code sap.com/tc/bpem/content/predefs/types}:
 * {@code rep/functions/**}{@code /*.function}, {@code rep/wsdl/*.types}) and {@code com.sap.tc.ap.bpemcontent_*.jar}
 * (DC {@code sap.com/tc/bpem/content/predefs/default}: {@code src/functions/**}, {@code src/triggers/*.eventdef});
 * of several versions of a jar the last by name is read. {@code .function} and {@code .eventdef} files are MOIN
 * partitions (XML); {@code .types} files are scanned by regular expression, as they may hold characters an XML parser
 * rejects. The JSON text is the one the former Python generator wrote ({@code json.dump(indent=1,
 * ensure_ascii=False)}): one space per level, insertion-ordered keys, non-ASCII raw, no trailing newline.
 */
public final class BuiltinFunctionGenerator {

    /** Counts of one generation run. */
    public record Result(int functions, int triggers, int xsdTypes, int sapTypes) {

        /** {@code <f> functions, <t> triggers, <x> XSD types, <s> SAP types}. */
        public String summary() {
            return functions + " functions, " + triggers + " triggers, " + xsdTypes + " XSD types, " + sapTypes
                    + " SAP types";
        }
    }

    private static final String MOIN_NS = "http://xml.sap.com/moin";
    private static final String MOIN = "{" + MOIN_NS + "}";

    /** Plugin jar pattern, inner DC archive suffix, path prefix of the {@code .function} files in the DC archive. */
    private record SourceSpec(String jarPattern, String innerSuffix, String functionPrefix) {
    }

    private static final List<SourceSpec> SOURCES = List.of(
            new SourceSpec("com.sap.tc.ap.servicecomp_*.jar", "sap.com~tc~bpem~content~predefs~types~def_cmp.zip",
                    "rep/functions/"),
            new SourceSpec("com.sap.tc.ap.bpemcontent_*.jar", "sap.com~tc~bpem~content~predefs~default~def_cmp.zip",
                    "src/functions/"));

    private static final Set<String> NOT_ADJACENT = Set.of("FunctionInterfaceHasFormalParameter",
            "FunctionInterfaceHasResult", "FunctionInterfaceHasFunctions");
    private static final String TYPE_OF = "TypeDefinitionForElementDeclaration";
    private static final Map<String, String> EVENT_DEFINITIONS = Map.of("ErrorEventDefinition", "error",
            "MessageEventDefinition", "message", "TimerEventDefinition", "timer");

    private static final Pattern TYPE_DEFINITION =
            Pattern.compile("<XSD1\\.Xsd(?:Simple|Complex)TypeDefinition[^>]*m:i='([0-9A-F]{40})'[^>]*>");
    private static final Pattern NAME = Pattern.compile("name='([^']*)'");

    /** Python's {@code str} ordering: by code point. */
    private static final Comparator<String> CODE_POINTS = BuiltinFunctionGenerator::compareCodePoints;

    private record Source(String plugin, String dc, Map<String, byte[]> entries, String functionPrefix) {
    }

    private record Link(String assoc, String f, String s) {
    }

    private record Elem(String metaclass, Map<String, String> attrs) {
    }

    private record Partition(Map<String, Elem> elements, List<Link> links) {
    }

    private record Neighbour(String id, String assoc) {
    }

    private record Field(String name, String type, int pos) {
    }

    private record Rendered(String json, Result result) {
    }

    private BuiltinFunctionGenerator() {
    }

    /** The catalog JSON text for the plugins folder. */
    public static String render(Path pluginsDir) throws IOException {
        return build(pluginsDir).json();
    }

    /** Writes {@link #render(Path)} to {@code out} (UTF-8, LF, no trailing newline) and returns the counts. */
    public static Result generate(Path pluginsDir, Path out) throws IOException {
        Rendered r = build(pluginsDir);
        Files.write(out, r.json().getBytes(StandardCharsets.UTF_8));
        return r.result();
    }

    private static Rendered build(Path pluginsDir) throws IOException {
        List<Source> sources = new ArrayList<>();
        for (SourceSpec spec : SOURCES) {
            sources.add(open(pluginsDir, spec));
        }
        Map<String, String> xsd = new HashMap<>();
        Map<String, String> sap = new HashMap<>();
        readTypes(sources, xsd, sap);
        Map<String, String> allTypes = new HashMap<>(sap);
        allTypes.putAll(xsd);

        List<Map<String, Object>> functions = new ArrayList<>();
        List<Map<String, Object>> triggers = new ArrayList<>();
        for (Source src : sources) {
            for (String name : sortedNames(src, src.functionPrefix(), ".function")) {
                functions.addAll(readFunctions(load(src, name), name, allTypes, src.plugin(), src.dc()));
            }
            for (String name : sortedNames(src, "src/triggers/", ".eventdef")) {
                triggers.addAll(readTriggers(load(src, name), name, allTypes, src.plugin(), src.dc()));
            }
        }
        functions.sort(byKeys("category", "name", "id"));
        triggers.sort(byKeys("kind", "name", "id"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", plugin(sources.getFirst()));
        out.put("additionalSources", sources.subList(1, sources.size()).stream().map(s -> (Object) plugin(s)).toList());
        out.put("functions", functions);
        out.put("triggers", triggers);
        out.put("xsdTypes", sortedByKey(xsd));
        out.put("sapTypes", sortedByKey(sap));
        StringBuilder json = new StringBuilder();
        writeJson(json, out, 0);
        return new Rendered(json.toString(), new Result(functions.size(), triggers.size(), xsd.size(), sap.size()));
    }

    // ---- sources ----

    private static Source open(Path dir, SourceSpec spec) throws IOException {
        int star = spec.jarPattern().indexOf('*');
        String head = spec.jarPattern().substring(0, star);
        String tail = spec.jarPattern().substring(star + 1);
        List<String> jars = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString())
                        .filter(n -> n.length() >= head.length() + tail.length() && n.startsWith(head)
                                && n.endsWith(tail))
                        .forEach(jars::add);
            }
        }
        if (jars.isEmpty()) {
            throw new IllegalArgumentException(spec.jarPattern() + " not found in " + dir);
        }
        jars.sort(CODE_POINTS);
        String plugin = jars.getLast();
        Path jar = dir.resolve(plugin);
        String innerName = null;
        byte[] inner = null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements() && inner == null) {
                ZipEntry e = en.nextElement();
                if (e.getName().endsWith(spec.innerSuffix())) {
                    innerName = e.getName();
                    try (InputStream in = zip.getInputStream(e)) {
                        inner = in.readAllBytes();
                    }
                }
            }
        }
        if (inner == null) {
            throw new IllegalArgumentException(spec.innerSuffix() + " not found in " + jar);
        }
        String dc = spec.innerSuffix().replace("~def_cmp.zip", "").replace('~', '/');
        return new Source(plugin, dc, ZipArchive.of(inner, innerName).entries(), spec.functionPrefix());
    }

    private static List<String> sortedNames(Source src, String prefix, String suffix) {
        return src.entries().keySet().stream()
                .filter(n -> n.startsWith(prefix) && n.endsWith(suffix))
                .sorted(CODE_POINTS)
                .toList();
    }

    private static Map<String, Object> plugin(Source s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plugin", s.plugin());
        m.put("dc", s.dc());
        return m;
    }

    /**
     * XSD built-in types (id to name) from {@code rep/wsdl/xml_schema.types}, SAP-defined types from the other
     * {@code rep/wsdl/*.types}; the first name of an id wins, XSD ids are removed from the SAP types.
     */
    private static void readTypes(List<Source> sources, Map<String, String> xsd, Map<String, String> sap)
            throws IOException {
        for (Source src : sources) {
            for (Map.Entry<String, byte[]> e : src.entries().entrySet()) {
                String n = e.getKey();
                if (!(n.startsWith("rep/wsdl/") && n.endsWith(".types"))) {
                    continue;
                }
                Map<String, String> target = n.endsWith("/xml_schema.types") ? xsd : sap;
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(e.getValue())).toString();
                Matcher m = TYPE_DEFINITION.matcher(text);
                while (m.find()) {
                    Matcher nm = NAME.matcher(m.group(0));
                    if (nm.find()) {
                        target.putIfAbsent(m.group(1), nm.group(1));
                    }
                }
            }
        }
        sap.keySet().removeAll(xsd.keySet());
    }

    // ---- MOIN partitions ----

    private static Partition load(Source src, String name) {
        Element root = XmlDocs.parse(src.entries().get(name), name).getDocumentElement();
        Map<String, Elem> elements = new LinkedHashMap<>();
        List<Link> links = new ArrayList<>();
        for (Element e : childElements(root)) {
            String tag = tag(e);
            if (tag.equals("C") || tag.equals("M")) {
                continue;
            }
            Attr id = e.getAttributeNodeNS(MOIN_NS, "i");
            if (id != null) {
                Map<String, String> d = new HashMap<>();
                NamedNodeMap attrs = e.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    Attr a = (Attr) attrs.item(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) {
                        continue;
                    }
                    String key = qualified(a.getNamespaceURI(), a.getLocalName());
                    if (!key.startsWith(MOIN)) {
                        d.put(key, a.getValue());
                    }
                }
                for (Element c : childElements(e)) {
                    String ct = tag(c);
                    if (ct.equals("name") || ct.equals("documentation")) {
                        Attr text = c.getAttributeNodeNS(null, "originalText");
                        d.put(ct, text == null ? null : text.getValue());
                    }
                }
                elements.put(id.getValue(), new Elem(lastSegment(tag), d));
            } else {
                for (Element l : childElements(e)) {
                    links.add(new Link(lastSegment(tag), end(l, "f", name), end(l, "s", name)));
                }
            }
        }
        return new Partition(elements, links);
    }

    private static List<Element> childElements(Element parent) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    /** ElementTree form of a name: {@code {uri}local}, or {@code local} without namespace. */
    private static String qualified(String uri, String local) {
        return uri == null ? local : "{" + uri + "}" + local;
    }

    private static String tag(Element e) {
        return qualified(e.getNamespaceURI(), e.getLocalName());
    }

    private static String lastSegment(String tag) {
        return tag.substring(tag.lastIndexOf('.') + 1);
    }

    private static String end(Element link, String attr, String file) {
        Attr a = link.getAttributeNodeNS(null, attr);
        if (a == null) {
            throw new IllegalArgumentException("Link without attribute '" + attr + "' in " + tag(link) + " of "
                    + file);
        }
        return a.getValue().replace("local:", "");
    }

    // ---- functions ----

    private static List<Map<String, Object>> readFunctions(Partition p, String file, Map<String, String> types,
            String plugin, String dc) {
        Map<String, Elem> el = p.elements();
        Map<String, Set<Neighbour>> adj = new HashMap<>();
        Map<String, List<Link>> byAssoc = new HashMap<>();
        for (Link l : p.links()) {
            byAssoc.computeIfAbsent(l.assoc(), k -> new ArrayList<>()).add(l);
            if (NOT_ADJACENT.contains(l.assoc())) {
                continue;
            }
            adj.computeIfAbsent(l.f(), k -> new LinkedHashSet<>()).add(new Neighbour(l.s(), l.assoc()));
            adj.computeIfAbsent(l.s(), k -> new LinkedHashSet<>()).add(new Neighbour(l.f(), l.assoc()));
        }

        // parameter order: ParticlesForModelGroup is an ordered association (link order = sequence order)
        Map<String, Integer> particlePos = new HashMap<>();
        List<Link> particles = byAssoc.getOrDefault("ParticlesForModelGroup", List.of());
        for (int idx = 0; idx < particles.size(); idx++) {
            particlePos.put(particles.get(idx).f(), idx);
            particlePos.put(particles.get(idx).s(), idx);
        }
        Map<String, Integer> elemPos = new HashMap<>();
        for (Link l : byAssoc.getOrDefault("ContentForParticle", List.of())) {
            if (particlePos.containsKey(l.f())) {
                elemPos.put(l.s(), particlePos.get(l.f()));
            }
            if (particlePos.containsKey(l.s())) {
                elemPos.put(l.f(), particlePos.get(l.s()));
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Elem> entry : el.entrySet()) {
            if (!entry.getValue().metaclass().equals("FunctionInterface")) {
                continue;
            }
            String fid = entry.getKey();
            Map<String, String> d = entry.getValue().attrs();
            List<String> req = ends(byAssoc, "FunctionInterfaceHasFormalParameter", fid).stream()
                    .filter(el::containsKey).toList();
            List<String> resp = ends(byAssoc, "FunctionInterfaceHasResult", fid).stream()
                    .filter(el::containsKey).toList();
            List<String> java = ends(byAssoc, "FunctionInterfaceHasFunctions", fid).stream()
                    .filter(el::containsKey).map(x -> el.get(x).attrs().get("className")).toList();
            List<Field> result = resp.isEmpty() ? List.of() : fields(resp.getFirst(), el, adj, elemPos, types);
            List<Field> params = req.isEmpty() ? List.of() : fields(req.getFirst(), el, adj, elemPos, types);
            String ns = d.getOrDefault("namespace", "");
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", fid);
            f.put("namespace", ns);
            f.put("category", lastPathSegment(ns));
            f.put("name", d.get("name"));
            f.put("symbol", d.get("symbol"));
            f.put("infix", "true".equals(d.get("infix")));
            f.put("documentation", d.get("documentation"));
            List<Object> parameters = new ArrayList<>();
            for (Field fd : params) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("name", fd.name());
                pm.put("type", fd.type());
                parameters.add(pm);
            }
            f.put("parameters", parameters);
            f.put("result", result.isEmpty() ? null : result.getFirst().type());
            f.put("javaClass", java.isEmpty() ? null : java.getFirst());
            f.put("file", file);
            f.put("sourcePlugin", plugin);
            f.put("sourceDc", dc);
            out.add(f);
        }
        return out;
    }

    private static String lastPathSegment(String ns) {
        return ns.substring(ns.lastIndexOf('/') + 1);
    }

    private static String typeOf(String decl, Map<String, Set<Neighbour>> adj) {
        for (Neighbour n : adj.getOrDefault(decl, Set.of())) {
            if (n.assoc().equals(TYPE_OF)) {
                return n.id();
            }
        }
        return null;
    }

    /** Element declarations inside the wrapper element's (request/response) complex type, in particle order. */
    private static List<Field> fields(String wrapper, Map<String, Elem> el, Map<String, Set<Neighbour>> adj,
            Map<String, Integer> elemPos, Map<String, String> types) {
        String typeId = typeOf(wrapper, adj);
        if (typeId == null || typeId.isEmpty()) {
            return List.of();
        }
        List<Field> res = new ArrayList<>();
        Set<String> seen = new HashSet<>(List.of(typeId, wrapper));
        List<String> stack = new ArrayList<>(List.of(typeId));
        while (!stack.isEmpty()) {
            String x = stack.removeLast();
            for (Neighbour n : adj.getOrDefault(x, Set.of())) {
                String y = n.id();
                if (seen.contains(y) || !el.containsKey(y)) {
                    continue;
                }
                seen.add(y);
                Elem e = el.get(y);
                if (e.metaclass().equals("XsdElementDeclaration")) {
                    String tid = typeOf(y, adj);
                    res.add(new Field(e.attrs().get("name"), types.getOrDefault(tid, tid),
                            elemPos.getOrDefault(y, 999)));
                } else {
                    stack.add(y);
                }
            }
        }
        res.sort(Comparator.comparingInt(Field::pos));
        return res;
    }

    private static List<String> ends(Map<String, List<Link>> byAssoc, String assoc, String owner) {
        List<Link> links = byAssoc.getOrDefault(assoc, List.of());
        List<String> fromF = links.stream().filter(l -> l.s().equals(owner)).map(Link::f).toList();
        return !fromF.isEmpty() ? fromF : links.stream().filter(l -> l.f().equals(owner)).map(Link::s).toList();
    }

    // ---- triggers ----

    /**
     * Standard event definitions of an {@code .eventdef} partition (one file may hold several, e.g. a message trigger
     * with its fault). Parameter = XSD element linked via EventDefinitionHasParameter.
     */
    private static List<Map<String, Object>> readTriggers(Partition p, String file, Map<String, String> types,
            String plugin, String dc) {
        Map<String, Elem> el = p.elements();
        Map<String, String> paramOf = new HashMap<>();
        Map<String, String> typeOf = new HashMap<>();
        for (Link l : p.links()) {
            if (l.assoc().equals("EventDefinitionHasParameter")) {
                paramOf.put(l.s(), l.f());
            }
            if (l.assoc().equals(TYPE_OF)) {
                typeOf.put(l.f(), l.s());
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Elem> entry : el.entrySet()) {
            String kind = EVENT_DEFINITIONS.get(entry.getValue().metaclass());
            if (kind == null) {
                continue;
            }
            String tid = entry.getKey();
            Map<String, String> d = entry.getValue().attrs();
            String param = paramOf.get(tid);
            Map<String, Object> parameter = null;
            if (param != null && el.containsKey(param)) {
                Map<String, String> pd = el.get(param).attrs();
                String typeId = typeOf.get(param);
                parameter = new LinkedHashMap<>();
                parameter.put("name", pd.get("name"));
                parameter.put("namespace", pd.get("namespace"));
                parameter.put("type", types.getOrDefault(typeId, typeId));
            }
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", tid);
            t.put("kind", kind);
            t.put("name", d.get("name"));
            t.put("documentation", d.get("documentation"));
            t.put("isCritical", "true".equals(d.get("isCritical")));
            t.put("isTechnical", "true".equals(d.get("isTechnical")));
            t.put("parameter", parameter);
            t.put("file", file);
            t.put("sourcePlugin", plugin);
            t.put("sourceDc", dc);
            out.add(t);
        }
        return out;
    }

    // ---- ordering ----

    private static int compareCodePoints(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return i < a.length() ? 1 : j < b.length() ? -1 : 0;
    }

    /** Tuple order of the given string fields; a missing value cannot be ordered against a present one. */
    private static Comparator<Map<String, Object>> byKeys(String... keys) {
        return (x, y) -> {
            for (String k : keys) {
                String a = (String) x.get(k);
                String b = (String) y.get(k);
                if (a == null && b == null) {
                    continue;
                }
                if (a == null || b == null) {
                    throw new IllegalArgumentException("Cannot order by '" + k + "': missing in " + x.get("file")
                            + " or " + y.get("file"));
                }
                int c = compareCodePoints(a, b);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        };
    }

    private static Map<String, Object> sortedByKey(Map<String, String> m) {
        TreeMap<String, Object> sorted = new TreeMap<>(CODE_POINTS);
        sorted.putAll(m);
        return new LinkedHashMap<>(sorted);
    }

    // ---- JSON (Python json.dump, indent=1, ensure_ascii=False) ----

    private static void writeJson(StringBuilder sb, Object v, int level) {
        switch (v) {
            case null -> sb.append("null");
            case Boolean b -> sb.append(b ? "true" : "false");
            case Number n -> sb.append(n);
            case String s -> quote(sb, s);
            case Map<?, ?> m -> {
                if (m.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append('{');
                String sep = "";
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    sb.append(sep).append('\n').append(" ".repeat(level + 1));
                    quote(sb, (String) e.getKey());
                    sb.append(": ");
                    writeJson(sb, e.getValue(), level + 1);
                    sep = ",";
                }
                sb.append('\n').append(" ".repeat(level)).append('}');
            }
            case List<?> l -> {
                if (l.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append('[');
                String sep = "";
                for (Object item : l) {
                    sb.append(sep).append('\n').append(" ".repeat(level + 1));
                    writeJson(sb, item, level + 1);
                    sep = ",";
                }
                sb.append('\n').append(" ".repeat(level)).append(']');
            }
            default -> throw new IllegalArgumentException("Not a JSON value: " + v.getClass().getName());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
