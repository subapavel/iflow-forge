package io.poxray.integration;

import io.poxray.model.IntegrationInventory.FieldMapping;
import io.poxray.model.IntegrationInventory.FunctionArgument;
import io.poxray.model.IntegrationInventory.FunctionLibrary;
import io.poxray.model.IntegrationInventory.MappingLogic;
import io.poxray.model.IntegrationInventory.MappingParameter;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.UserFunction;
import io.poxray.sca.XmlDocs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads the mapping editor metadata of a message mapping ({@code <mappingtool>}) and user-defined function storages
 * ({@code <functionstorage>}, same format in function libraries and in the mapping's local functions).
 * <p>
 * Mapping metadata, verified on xipod (PO 7.50): {@code project/libstorage/entry} = local functions
 * ({@code usernamespace}) or a reference to a function library ({@code libref}, entry name = alias used in bricks);
 * {@code project/transformation/brick type="Dst" path=...} = one target field with its argument tree of
 * {@code Src} (source path), {@code Func} ({@code fns} = {@code dflt} standard library, alias or
 * {@code usernamespace}; {@code fname} = function name or key) bricks; {@code arg pin} = input position,
 * {@code arg from} = output index of multi-output functions; {@code object_uid} defines a brick that is referenced
 * elsewhere by an empty {@code brick object_uid}; {@code bindings/param} = function parameters (constant values,
 * {@code ext_bind} mapping parameters, {@code properties} tables); {@code properties/property switchedOff} = disabled
 * target field.
 */
final class MappingLogicReader {

    /** Imports present in every function storage (mapping editor defaults). */
    private static final Set<String> DEFAULT_IMPORTS = Set.of("import com.sap.aii.mapping.api.*;",
            "import com.sap.aii.mapping.lookup.*;", "import com.sap.aii.mappingtool.tf7.rt.*;", "import java.io.*;",
            "import java.lang.reflect.*;", "import java.util.*;");
    /** SAP APIs worth knowing for a migration, detected in Java code (label → pattern). */
    private static final Map<String, Pattern> SAP_APIS = new LinkedHashMap<>();
    private static final Set<String> BRICK_ATTRIBUTES = Set.of("type", "gid", "path", "fname", "fns", "object_uid",
            "asXml");
    private static final Pattern CHANNEL_ADAPTER = Pattern.compile("<p1:elem>([^<]*)</p1:elem>");

    static {
        SAP_APIS.put("LookupService", Pattern.compile("\\b(LookupService|SystemAccessor|RfcAccessor)\\b"));
        SAP_APIS.put("DynamicConfiguration", Pattern.compile("\\bDynamicConfiguration"));
        SAP_APIS.put("Trace", Pattern.compile("\\b(getTrace|AbstractTrace|MappingTrace)\\b"));
        SAP_APIS.put("GlobalContainer", Pattern.compile("\\b(GlobalContainer|getGlobalContainer)\\b"));
        SAP_APIS.put("StructureNode", Pattern.compile("\\b(StructureNode|STRUCTURE_NODE)\\b"));
        SAP_APIS.put("ValueMapping", Pattern.compile("\\b(XIVMService|ValueMapping\\w*|executeMapping)\\b"));
        SAP_APIS.put("JDBC", Pattern.compile("\\b(java\\.sql|DataSource|DriverManager|getConnection)\\b"));
        SAP_APIS.put("MessageHeader", Pattern.compile("\\b(InputHeader|OutputHeader|getMessageId|MESSAGE_ID)\\b"));
        SAP_APIS.put("MappingParameters", Pattern.compile("\\b(getInputParameters|getOutputParameters)\\b"));
        SAP_APIS.put("ContextChange", Pattern.compile("ResultList\\.CC|ResultList\\.SUPPRESS|addContextChange"));
    }

    private MappingLogicReader() {
    }

    // ------------------------------------------------------------------------------------------ function storage

    /** A {@code <functionstorage>} element as function library ({@code id} null for local functions). */
    static FunctionLibrary functionStorage(Element fs, ObjRef id, String description) {
        List<String> imports = new ArrayList<>();
        String importText = javaText(XmlDocs.child(fs, "imports"));
        if (importText != null) {
            importText.lines().map(String::strip).filter(s -> !s.isEmpty() && !DEFAULT_IMPORTS.contains(s))
                    .forEach(imports::add);
        }
        String globals = javaText(XmlDocs.child(XmlDocs.child(fs, "globals"), "javaText"));
        String init = null;
        Element initModel = XmlDocs.child(XmlDocs.child(fs, "init"), "functionmodel");
        if (initModel != null) {
            init = javaText(XmlDocs.child(XmlDocs.child(initModel, "implementation"), "javaText"));
        }
        String cleanup = javaText(XmlDocs.child(XmlDocs.child(fs, "cleanup"), "javaText"));
        List<UserFunction> functions = new ArrayList<>();
        for (Element fm : XmlDocs.children(fs, "functionmodel")) {
            functions.add(function(fm));
        }
        List<ObjRef> archives = new ArrayList<>();
        Element usedJars = XmlDocs.child(fs, "usedjars");
        if (usedJars != null) {
            for (Element ref : XmlDocs.children(usedJars, "ref")) {
                ObjRef r = keyRef(ref);
                if (r != null) {
                    archives.add(r);
                }
            }
        }
        return new FunctionLibrary(id, List.copyOf(imports), globals, init, cleanup, List.copyOf(functions),
                List.copyOf(archives), sapApis(String.join("\n", nz(globals), nz(init), nz(cleanup))), description);
    }

    private static UserFunction function(Element fm) {
        Element signature = XmlDocs.child(fm, "signature");
        List<FunctionArgument> args = new ArrayList<>();
        if (signature != null) {
            for (Element a : XmlDocs.children(signature, "argument")) {
                String kind = switch (a.getAttribute("tp")) {
                    case "0" -> "input";
                    case "1" -> "result";
                    case "2" -> "parameter";
                    default -> "tp:" + a.getAttribute("tp");
                };
                args.add(new FunctionArgument(a.getAttribute("nm"), kind, a.getAttribute("jtp"),
                        XmlDocs.childText(a, "uiTitle")));
            }
        }
        String cacheType = signature == null ? "" : signature.getAttribute("cacheType");
        String execution = switch (cacheType) {
            case "0", "" -> "singleValues";
            case "1" -> "contextValues";
            case "2" -> "queueValues";
            default -> "cacheType:" + cacheType;
        };
        String code = javaText(XmlDocs.child(XmlDocs.child(fm, "implementation"), "javaText"));
        return new UserFunction(XmlDocs.childText(fm, "key"), XmlDocs.childText(fm, "name"),
                XmlDocs.childText(fm, "title"), XmlDocs.childText(fm, "uiTitle"), execution, List.copyOf(args), code,
                sapApis(code));
    }

    static List<String> sapApis(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        SAP_APIS.forEach((label, p) -> {
            if (p.matcher(code).find()) {
                out.add(label);
            }
        });
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------ mapping

    /**
     * Mapping logic from the {@code <mappingtool>} root.
     *
     * @param parameters mapping parameters (from the object's {@code container})
     * @param libraries  resolves a referenced function library (null if unavailable)
     * @param warn       receives problems (unknown bricks, unresolved functions)
     */
    static MappingLogic mapping(Element mappingtool, List<MappingParameter> parameters,
            Function<ObjRef, FunctionLibrary> libraries, Consumer<String> warn) {
        Element project = XmlDocs.child(mappingtool, "project");
        if (project == null) {
            warn.accept("mapping metadata without <project>");
            return null;
        }
        FunctionLibrary local = null;
        Map<String, FunctionLibrary> aliases = new HashMap<>();
        Map<String, ObjRef> aliasRefs = new HashMap<>();
        List<ObjRef> used = new ArrayList<>();
        Element storage = XmlDocs.child(project, "libstorage");
        for (Element entry : storage == null ? List.<Element>of() : XmlDocs.children(storage, "entry")) {
            String alias = entry.getAttribute("name");
            Element fs = XmlDocs.child(entry, "functionstorage");
            if (fs != null) {
                FunctionLibrary lib = functionStorage(fs, null, null);
                aliases.put(alias, lib);
                if (local == null && (!lib.functions().isEmpty() || lib.globals() != null || lib.init() != null
                        || lib.cleanup() != null)) {
                    local = lib;
                }
                continue;
            }
            Element ref = XmlDocs.child(XmlDocs.child(entry, "libref"), "ref");
            ObjRef r = ref == null ? null : keyRef(ref);
            if (r != null) {
                used.add(r);
                aliasRefs.put(alias, r);
                FunctionLibrary lib = libraries.apply(r);
                if (lib != null) {
                    aliases.put(alias, lib);
                }
            }
        }
        Element tr = XmlDocs.child(project, "transformation");
        Map<String, String> namespaces = new TreeMap<>();
        List<FieldMapping> fields = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        if (tr != null) {
            Element nsProps = XmlDocs.child(XmlDocs.child(tr, "namespaces"), "properties");
            for (Element p : nsProps == null ? List.<Element>of() : XmlDocs.children(nsProps, "property")) {
                String prefix = p.getTextContent().strip();
                if (!prefix.isEmpty()) {
                    namespaces.put(prefix, p.getAttribute("name"));
                }
            }
            Terms terms = new Terms(tr, aliases, aliasRefs, warn);
            for (Element dst : XmlDocs.children(tr, "brick")) {
                String path = dst.getAttribute("path");
                if (switchedOff(dst)) {
                    disabled.add(path);
                    continue;
                }
                List<Element> args = XmlDocs.children(dst, "arg");
                if (args.isEmpty()) {
                    continue;
                }
                Set<String> sources = new LinkedHashSet<>();
                String expression = args.stream().map(a -> terms.arg(a, sources, 0)).collect(Collectors.joining(", "));
                fields.add(new FieldMapping(path, expression, List.copyOf(sources)));
            }
        }
        return new MappingLogic(namespaces, List.copyOf(fields), List.copyOf(disabled), parameters, List.copyOf(used),
                local);
    }

    /**
     * Mapping parameters from the {@code container} of an {@code XiTrafo} content. Channel parameters
     * ({@code typetype DCOMCHAN}) name a link role ({@code p_<name>}) whose target is the adapter metadata;
     * {@code linkedAdapter} resolves that role to the adapter type (e.g. RFC).
     */
    static List<MappingParameter> parameters(Element xiTrafo, Function<String, String> linkedAdapter) {
        Element container = xiTrafo == null ? null : XmlDocs.child(xiTrafo, "container");
        List<MappingParameter> out = new ArrayList<>();
        for (Element e : container == null ? List.<Element>of() : XmlDocs.children(container, "element")) {
            String type = e.getAttribute("type");
            if ("DCOMCHAN".equals(e.getAttribute("typetype"))) {
                Matcher m = CHANNEL_ADAPTER.matcher(type);
                String adapter = m.find() ? m.group(1) : linkedAdapter.apply(type);
                type = "channel:" + (adapter == null ? "?" : adapter);
            }
            out.add(new MappingParameter(e.getAttribute("name"), type,
                    "true".equals(e.getAttribute("isOutput")) ? "export" : "import",
                    "true".equals(e.getAttribute("isMandatory"))));
        }
        return List.copyOf(out);
    }

    private static boolean switchedOff(Element brick) {
        Element props = XmlDocs.child(brick, "properties");
        for (Element p : props == null ? List.<Element>of() : XmlDocs.children(props, "property")) {
            if ("switchedOff".equals(p.getAttribute("name")) && "true".equals(p.getTextContent().strip())) {
                return true;
            }
        }
        return false;
    }

    /** Renders argument trees of one transformation as function terms. */
    private static final class Terms {

        private static final int MAX_DEPTH = 200;

        private final Map<String, Element> definitions = new HashMap<>();
        private final Map<String, FunctionLibrary> aliases;
        private final Map<String, ObjRef> aliasRefs;
        private final Consumer<String> warn;
        private final Set<String> reported = new LinkedHashSet<>();

        Terms(Element transformation, Map<String, FunctionLibrary> aliases, Map<String, ObjRef> aliasRefs,
                Consumer<String> warn) {
            this.aliases = aliases;
            this.aliasRefs = aliasRefs;
            this.warn = warn;
            collect(transformation);
        }

        private void collect(Element e) {
            for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element c) {
                    if ("brick".equals(c.getLocalName()) && c.hasAttribute("object_uid") && c.hasAttribute("type")) {
                        definitions.put(c.getAttribute("object_uid"), c);
                    }
                    collect(c);
                }
            }
        }

        String arg(Element arg, Set<String> sources, int depth) {
            Element brick = XmlDocs.child(arg, "brick");
            if (brick == null) {
                return "?";
            }
            Term t = brick(brick, sources, depth + 1);
            int from = parseInt(arg.getAttribute("from"));
            List<FunctionArgument> results = t.function == null ? List.of()
                    : t.function.arguments().stream().filter(a -> "result".equals(a.kind())).toList();
            if (results.size() > 1 && from < results.size()) {
                return t.text + "." + results.get(from).name();
            }
            return from > 0 ? t.text + "[" + from + "]" : t.text;
        }

        private Term brick(Element b, Set<String> sources, int depth) {
            if (depth > MAX_DEPTH) {
                report("mapping term nested deeper than " + MAX_DEPTH + " levels");
                return new Term("…", null);
            }
            if (!b.hasAttribute("type") && b.hasAttribute("object_uid")) {
                Element def = definitions.get(b.getAttribute("object_uid"));
                if (def == null) {
                    report("brick reference " + b.getAttribute("object_uid") + " has no definition");
                    return new Term("?" + b.getAttribute("object_uid"), null);
                }
                return brick(def, sources, depth + 1);
            }
            String type = b.getAttribute("type");
            return switch (type) {
                case "Src" -> {
                    String path = b.getAttribute("path");
                    sources.add(path);
                    String text = "x".equals(b.getAttribute("asXml")) ? "asXml(" + path + ")" : path;
                    yield new Term(text + extras(b), null);
                }
                case "Func" -> function(b, sources, depth);
                case "Dst" -> new Term(b.getAttribute("path"), null);
                default -> {
                    report("unknown brick type '" + type + "'");
                    yield new Term("?" + type, null);
                }
            };
        }

        private Term function(Element b, Set<String> sources, int depth) {
            String fns = b.getAttribute("fns");
            String fname = b.getAttribute("fname");
            String name = fname;
            UserFunction fn = null;
            if (!"dflt".equals(fns)) {
                FunctionLibrary lib = aliases.get(fns);
                fn = lib == null ? null : lib.functions().stream().filter(f -> fname.equals(f.key())).findFirst()
                        .orElse(null);
                boolean local = lib != null && lib.id() == null && !aliasRefs.containsKey(fns);
                if (fn == null) {
                    ObjRef r = aliasRefs.get(fns);
                    report("function " + fname + " of " + (r == null ? fns : "library " + r.name() + " | " + r.namespace())
                            + " not resolved");
                    name = fns + "." + fname;
                } else {
                    name = local ? fn.name() : fns + "." + fn.name();
                }
            }
            List<Element> args = new ArrayList<>(XmlDocs.children(b, "arg"));
            args.sort(Comparator.comparingInt(a -> parseInt(a.getAttribute("pin"))));
            List<String> parts = new ArrayList<>();
            for (Element a : args) {
                parts.add(arg(a, sources, depth));
            }
            if ("dflt".equals(fns) && "iF".equals(fname) && parts.size() == 3) {
                // pins of the standard iF are then (0), if (1), else (2)
                List<String> pins = List.copyOf(parts);
                parts.clear();
                parts.addAll(List.of("if: " + pins.get(1), "then: " + pins.get(0), "else: " + pins.get(2)));
            }
            Map<String, String> params = bindings(b);
            if ("dflt".equals(fns) && "const".equals(fname) && args.isEmpty() && params.size() == 1
                    && params.containsKey("value")) {
                return new Term(params.get("value"), null);
            }
            params.forEach((k, v) -> parts.add(k + "=" + v));
            return new Term(name + "(" + String.join(", ", parts) + ")" + extras(b), fn);
        }

        /** Function parameters: {@code "constant"}, {@code $mappingParameter} or a table {@code {key: "value"}}. */
        private static Map<String, String> bindings(Element b) {
            Map<String, String> out = new LinkedHashMap<>();
            Element bindings = XmlDocs.child(b, "bindings");
            for (Element p : bindings == null ? List.<Element>of() : XmlDocs.children(bindings, "param")) {
                List<String> values = new ArrayList<>();
                for (Element v : XmlDocs.children(p, "value")) {
                    if (v.hasAttribute("ext_bind")) {
                        values.add("$" + v.getAttribute("ext_bind"));
                        continue;
                    }
                    Element props = XmlDocs.child(v, "properties");
                    if (props != null) {
                        values.add(XmlDocs.children(props, "property").stream()
                                .map(pr -> quote(pr.getAttribute("name")) + ": " + quote(pr.getTextContent()))
                                .collect(Collectors.joining(", ", "{", "}")));
                    } else {
                        values.add(quote(v.getTextContent()));
                    }
                }
                out.put(p.getAttribute("name"), values.size() == 1 ? values.getFirst()
                        : values.stream().collect(Collectors.joining(", ", "[", "]")));
            }
            return out;
        }

        /** Attributes the reader does not interpret are kept visible as {@code {name=value}} and reported once. */
        private String extras(Element b) {
            List<String> extra = new ArrayList<>();
            var attrs = b.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node a = attrs.item(i);
                if (!BRICK_ATTRIBUTES.contains(a.getNodeName())) {
                    extra.add(a.getNodeName() + "=" + a.getNodeValue());
                    report("brick attribute '" + a.getNodeName() + "' not interpreted");
                }
            }
            for (Element c : childElements(b)) {
                String n = c.getLocalName();
                if (!"viewData".equals(n) && !"arg".equals(n) && !"bindings".equals(n) && !"group".equals(n)
                        && !"properties".equals(n)) {
                    extra.add(n);
                    report("brick element <" + n + "> not interpreted");
                }
            }
            return extra.isEmpty() ? "" : "{" + String.join(", ", extra) + "}";
        }

        private void report(String message) {
            if (reported.add(message)) {
                warn.accept(message);
            }
        }
    }

    private record Term(String text, UserFunction function) {
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** {@code ref/key/key typeID oid + elem...} → reference by name / namespace. */
    private static ObjRef keyRef(Element ref) {
        Element key = XmlDocs.child(XmlDocs.child(ref, "key"), "key");
        if (key == null) {
            return null;
        }
        List<Element> elems = XmlDocs.children(key, "elem");
        String name = elems.isEmpty() ? null : elems.get(0).getTextContent().strip();
        String ns = elems.size() < 2 ? null : elems.get(1).getTextContent().strip();
        String oid = key.getAttribute("oid");
        return new ObjRef(key.getAttribute("typeID"), name, ns, null, null, oid.isEmpty() ? null : oid);
    }

    /** Java code of a {@code javaText} (or imports) element, common indentation removed; null if blank. */
    private static String javaText(Element e) {
        if (e == null) {
            return null;
        }
        String t = e.getTextContent().replace("\r\n", "\n").stripIndent().strip();
        return t.isEmpty() ? null : t;
    }

    private static List<Element> childElements(Element e) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                out.add(c);
            }
        }
        return out;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static int parseInt(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
