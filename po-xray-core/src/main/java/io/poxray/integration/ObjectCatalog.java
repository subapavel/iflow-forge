package io.poxray.integration;

import io.poxray.IssueLog;
import io.poxray.model.IntegrationInventory.ObjectDetail;
import io.poxray.model.IntegrationInventory.ObjectLink;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import io.poxray.po.Area;
import io.poxray.po.PoGateway;
import io.poxray.po.QueryRequest;
import io.poxray.po.QueryResult;
import io.poxray.xi.XiLink;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;

/**
 * Any ESR / Integration Directory object, independent of ICOs: search by type and name (SimpleQuery), read one
 * object (dedicated reader where available, otherwise links + raw content), and where-used via the reference lists
 * of SimpleQuery ({@code RA_LINK_LIST}, outgoing links of every object of the referring types, without reading them).
 */
public final class ObjectCatalog {

    /** Friendly names → type IDs (type IDs themselves are accepted as well). */
    public static final Map<String, String> ALIASES;
    /** Directory types; every other type is an ESR type. */
    public static final Set<String> DIRECTORY_TYPES = Set.of("AllInOne", "Channel", "Service", "Party",
            "DirectoryView", "InboundBinding", "OutboundBinding", "RoutingRelation", "MappingRelation", "RoutingRule",
            "P2PBinding", "ValueMapping", "AlertRule", "AgencySchemObj", "IFLOW", "AEName");
    /** Types whose objects can reference an object of the key type (verified links on xipod, PO 7.50). */
    static final Map<String, List<String>> REFERRERS;

    private static final List<String> ATTRIBUTES = List.of("RA_XILINK", "VERSIONID", "MODIFYDATE", "MODIFYUSER",
            "TEXT");
    private static final List<String> LINK_ATTRIBUTES = List.of("RA_XILINK", "VERSIONID", "MODIFYDATE",
            "MODIFYUSER", "TEXT", "RA_LINK_LIST");

    static {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("ico", "AllInOne");
        a.put("integratedConfiguration", "AllInOne");
        a.put("channel", "Channel");
        a.put("component", "Service");
        a.put("party", "Party");
        a.put("iflow", "IFLOW");
        a.put("configurationScenario", "DirectoryView");
        a.put("receiverDetermination", "RoutingRelation");
        a.put("interfaceDetermination", "MappingRelation");
        a.put("senderAgreement", "InboundBinding");
        a.put("receiverAgreement", "OutboundBinding");
        a.put("receiverRule", "RoutingRule");
        a.put("valueMapping", "ValueMapping");
        a.put("serviceInterface", "ifmmessif");
        a.put("operation", "ifmoper");
        a.put("messageType", "ifmmessage");
        a.put("faultMessage", "ifmfaultm");
        a.put("dataType", "ifmtypedef");
        a.put("externalDefinition", "ifmextdef");
        a.put("contextObject", "ifmcontobj");
        a.put("operationMapping", "MAPPING");
        a.put("messageMapping", "XI_TRAFO");
        a.put("functionLibrary", "FUNC_LIB");
        a.put("importedArchive", "TRAFO_JAR");
        a.put("archiveProgram", "MAP_ARCHIVE_PRG");
        a.put("integrationProcess", "RepBProcess");
        a.put("idoc", "idoc");
        a.put("rfc", "rfc");
        a.put("adapterMetadata", "AdapterMetaData");
        a.put("softwareComponentVersion", "workspace");
        ALIASES = Map.copyOf(a);

        Map<String, List<String>> r = new LinkedHashMap<>();
        List<String> interfaceUsers = List.of("MAPPING", "AllInOne", "RoutingRelation", "MappingRelation",
                "InboundBinding", "OutboundBinding");
        r.put("XI_TRAFO", List.of("MAPPING"));
        r.put("MAP_ARCHIVE_PRG", List.of("MAPPING"));
        r.put("TRAFO_JAR", List.of("FUNC_LIB", "XI_TRAFO", "MAPPING"));
        r.put("FUNC_LIB", List.of("XI_TRAFO"));
        r.put("MAPPING", List.of("AllInOne", "MappingRelation"));
        r.put("ifmmessif", interfaceUsers);
        r.put("ifmoper", interfaceUsers);
        r.put("ifmmessage", List.of("ifmmessif", "XI_TRAFO", "MAPPING"));
        r.put("ifmfaultm", List.of("ifmmessif", "XI_TRAFO", "MAPPING"));
        r.put("ifmextdef", List.of("ifmmessif", "XI_TRAFO", "MAPPING"));
        r.put("ifmtypedef", List.of("ifmtypedef", "ifmmessage", "ifmfaultm"));
        r.put("Channel", List.of("AllInOne", "InboundBinding", "OutboundBinding"));
        r.put("Service", List.of("AllInOne", "Channel", "RoutingRelation", "MappingRelation", "InboundBinding",
                "OutboundBinding"));
        REFERRERS = Map.copyOf(r);
    }

    private final PoGateway client;
    private final XiObjectSource source;
    private final Map<String, List<String>> keyNames = new ConcurrentHashMap<>();
    private final Map<String, QueryResult> listings = new ConcurrentHashMap<>();

    public ObjectCatalog(PoGateway client, XiObjectSource source) {
        this.client = client;
        this.source = source;
    }

    /** A matched object: reference (for reading) and summary. */
    public record Hit(XiRef ref, ObjectSummary summary) {
    }

    /** {@code total} matches, of which the first {@code limit} are returned. */
    public record SearchResult(int total, List<ObjectSummary> objects) {
    }

    /** Type ID for a friendly name or type ID (case-insensitive). */
    public static String typeId(String typeOrAlias) {
        String t = typeOrAlias.strip();
        for (var e : ALIASES.entrySet()) {
            if (e.getKey().equalsIgnoreCase(t) || e.getValue().equalsIgnoreCase(t)) {
                return e.getValue();
            }
        }
        for (String d : DIRECTORY_TYPES) {
            if (d.equalsIgnoreCase(t)) {
                return d;
            }
        }
        return t;
    }

    public static Area area(String typeId) {
        return DIRECTORY_TYPES.contains(typeId) ? Area.DIRECTORY : Area.REPOSITORY;
    }

    /**
     * Objects of the given types whose name matches {@code namePattern} (plain text = substring, {@code *} /
     * {@code ?} wildcards, case-insensitive; empty = all) and whose namespace matches {@code namespacePattern}.
     * The PO refuses case-insensitive conditions on NAME, so the types are listed completely (once per catalog) and
     * filtered locally, on any key element or the whole key {@code a|b|c}.
     */
    public SearchResult search(List<String> types, String namePattern, String namespacePattern, int limit) {
        List<Hit> hits = hits(types, namePattern, namespacePattern, false);
        return new SearchResult(hits.size(), hits.stream().limit(limit).map(Hit::summary).toList());
    }

    /**
     * Objects of a type with exactly this name (case-insensitive), optionally in a namespace. ESR names are tried
     * case-sensitively on the server first (cheap), then case-insensitively in the full listing.
     */
    public List<Hit> find(String type, String name, String namespace) {
        String id = typeId(type);
        if (area(id) == Area.REPOSITORY && !name.contains("*") && !name.contains("?")) {
            QueryResult r = client.query(new QueryRequest(Area.REPOSITORY, List.of(id), "NAME", name.strip(), "EQ",
                    false, ATTRIBUTES));
            keyNames.putAll(r.keyNames());
            Pattern ns = namespace == null || namespace.isBlank() ? null : exact(namespace.strip());
            Pattern exactName = exact(name.strip());
            List<Hit> direct = new ArrayList<>();
            for (QueryResult.Row row : r.rows()) {
                if (row.xiLink() != null) {
                    ObjectSummary s = summary(row.xiLink(), row);
                    if (matches(s, row.xiLink(), exactName, ns)) {
                        direct.add(new Hit(row.xiLink(), s));
                    }
                }
            }
            if (!direct.isEmpty()) {
                return direct;
            }
        }
        return hits(List.of(type), name, namespace, true);
    }

    private List<Hit> hits(List<String> types, String namePattern, String namespacePattern, boolean exact) {
        Map<Area, List<String>> byArea = new TreeMap<>();
        for (String t : types) {
            String id = typeId(t);
            byArea.computeIfAbsent(area(id), k -> new ArrayList<>()).add(id);
        }
        String name = namePattern == null ? "" : namePattern.strip();
        Pattern namePredicate = name.isEmpty() ? null : exact ? exact(name) : IntegrationQuery.glob(name, true);
        Pattern nsPredicate = namespacePattern == null || namespacePattern.isBlank() ? null
                : exact ? exact(namespacePattern.strip()) : IntegrationQuery.glob(namespacePattern, true);
        List<Hit> out = new ArrayList<>();
        for (var e : byArea.entrySet()) {
            for (QueryResult.Row row : listing(e.getKey(), e.getValue(), false).rows()) {
                XiRef ref = row.xiLink();
                if (ref == null) {
                    continue;
                }
                ObjectSummary s = summary(ref, row);
                if (matches(s, ref, namePredicate, nsPredicate)) {
                    out.add(new Hit(ref, s));
                }
            }
        }
        out.sort((a, b) -> label(a.ref()).compareToIgnoreCase(label(b.ref())));
        return out;
    }

    private static boolean matches(ObjectSummary s, XiRef ref, Pattern name, Pattern namespace) {
        if (name != null) {
            boolean any = name.matcher(String.join("|", ref.key().stream().filter(k -> !k.isEmpty()).toList()))
                    .matches() || ref.key().stream().anyMatch(k -> name.matcher(k).matches());
            if (!any) {
                return false;
            }
        }
        if (namespace != null) {
            return s.key().entrySet().stream().anyMatch(e -> (e.getKey().equals("NAMESPACE")
                    || e.getKey().endsWith("NS")) && namespace.matcher(e.getValue()).matches());
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ read

    /**
     * The object itself: the record of the dedicated reader (service interface, operation / message mapping,
     * message / data type, external definition, function library, channel, ICO) or, for other types, a generic
     * {@link ObjectDetail}. Null if it cannot be read (reason in {@code issues}).
     */
    public Object read(Hit hit, IssueLog issues) {
        IntegrationReader reader = new IntegrationReader(source, issues);
        Object specific = reader.read(hit.ref());
        if (specific != null) {
            return specific;
        }
        return source.resolve(hit.ref()).map(o -> detail(o, keyNames.get(o.typeId()))).orElse(null);
    }

    /** Generic view: summary, links by role and the content as XML (values that look like secrets masked). */
    public static ObjectDetail detail(XiObject o, List<String> keyNames) {
        List<ObjectLink> links = new ArrayList<>();
        for (XiLink l : o.links()) {
            XiRef t = l.target();
            links.add(new ObjectLink(l.role(), t.typeId(), String.join("|", t.key()), t.swcCaption(), t.oid()));
        }
        XiRef id = o.id();
        ObjectSummary s = new ObjectSummary(id.typeId(), id.isDirectory() ? "dir" : "rep", keys(id, keyNames),
                id.swcCaption(), id.swcGuid(), id.oid(), o.vid(), o.modifiedAt(), o.modifiedBy(), o.description());
        String content = null;
        if (o.content() != null) {
            Element copy = (Element) o.content().cloneNode(true);
            Secrets.mask(copy);
            content = serialize(copy);
        }
        return new ObjectDetail(s, List.copyOf(links), content);
    }

    // ------------------------------------------------------------------------------------------ where-used

    /** Types searched for objects referencing an object of {@code typeId}; empty if where-used is not supported. */
    public static List<String> referrerTypes(String typeId) {
        return REFERRERS.getOrDefault(typeId, List.of());
    }

    /**
     * Objects referencing any of the targets: the referring types are listed with their outgoing links
     * ({@code RA_LINK_LIST}) and matched against the targets (service interfaces also through their operations,
     * external definitions through their messages). Listings are kept for the lifetime of this catalog.
     */
    public List<ObjectSummary> whereUsed(List<Hit> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        String typeId = targets.getFirst().ref().typeId();
        List<String> referrers = referrerTypes(typeId);
        if (referrers.isEmpty()) {
            throw new IllegalArgumentException("Where-used is not supported for type " + typeId + "; supported: "
                    + String.join(", ", REFERRERS.keySet()));
        }
        Map<String, ObjectSummary> out = new LinkedHashMap<>();
        Map<Area, List<String>> byArea = new TreeMap<>();
        referrers.forEach(t -> byArea.computeIfAbsent(area(t), k -> new ArrayList<>()).add(t));
        for (var e : byArea.entrySet()) {
            QueryResult r = listing(e.getKey(), e.getValue(), true);
            for (QueryResult.Row row : r.rows()) {
                XiRef ref = row.xiLink();
                if (ref == null) {
                    continue;
                }
                for (XiRef link : row.refs("RA_LINK_LIST")) {
                    if (targets.stream().anyMatch(t -> references(link, t.ref()))) {
                        out.putIfAbsent(label(ref) + "|" + ref.swcGuid(), summary(ref, row));
                        break;
                    }
                }
            }
        }
        return List.copyOf(out.values());
    }

    /** All objects of the types (with outgoing links if {@code links}), kept for the lifetime of this catalog. */
    private QueryResult listing(Area area, List<String> types, boolean links) {
        String key = area + "|" + links + "|" + String.join(",", types);
        return listings.computeIfAbsent(key, k -> {
            List<String> attributes = links ? LINK_ATTRIBUTES : ATTRIBUTES;
            QueryResult r = area == Area.REPOSITORY
                    ? client.query(new QueryRequest(Area.REPOSITORY, types, "NAME", "*", "LIKE", false, attributes))
                    : client.query(new QueryRequest(Area.DIRECTORY, types, "MODIFYUSER", "*", "LIKE", false,
                            attributes));
            keyNames.putAll(r.keyNames());
            return r;
        });
    }

    /**
     * Whether {@code link} points to {@code target}: same object ID, or same type and key when an ID is missing.
     * The SWCV is not compared: links carry the SWCV of the referring object, while the target may live in an
     * underlying SWCV (e.g. a function library in a common SWC).
     */
    static boolean references(XiRef link, XiRef target) {
        if (link.typeId() == null || target.typeId() == null) {
            return false;
        }
        if (link.typeId().equals(target.typeId())) {
            return link.oid() != null && target.oid() != null ? link.oid().equalsIgnoreCase(target.oid())
                    : link.keyString().equals(target.keyString());
        }
        if ("ifmmessif".equals(target.typeId()) && "ifmoper".equals(link.typeId())) {
            return link.key(0).startsWith(target.key(0) + ".") && link.key(1).equals(target.key(1));
        }
        if ("ifmextdef".equals(target.typeId()) && "ifmextmes".equals(link.typeId())) {
            return link.key(0).equals(target.key(0)) && link.key(1).equals(target.key(1));
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------ helpers

    private ObjectSummary summary(XiRef ref, QueryResult.Row row) {
        return new ObjectSummary(ref.typeId(), ref.isDirectory() ? "dir" : "rep", keys(ref, keyNames.get(ref.typeId())),
                ref.swcCaption(), ref.swcGuid(), ref.oid(), row.text("VERSIONID"), row.text("MODIFYDATE"),
                row.text("MODIFYUSER"), row.text("TEXT"));
    }

    /** Key elements by name ({@code typeInfo}), {@code KEY<i>} when the names are unknown; empty elements kept. */
    static Map<String, String> keys(XiRef ref, List<String> names) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < ref.key().size(); i++) {
            String name = names != null && i < names.size() ? names.get(i) : "KEY" + i;
            out.put(name, ref.key(i));
        }
        return out;
    }

    private static String label(XiRef r) {
        return r.typeId() + " " + String.join("|", r.key());
    }

    private static Pattern exact(String value) {
        return Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static String serialize(Element e) {
        try {
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(e), new StreamResult(w));
            return w.toString();
        } catch (TransformerException ex) {
            return null;
        }
    }
}
