package io.poxray.integration;

import io.poxray.IssueLog;
import io.poxray.PoXray;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Component;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.ScaInventory;
import io.poxray.po.Area;
import io.poxray.po.PoException;
import io.poxray.po.PoGateway;
import io.poxray.po.QueryRequest;
import io.poxray.po.TypeKeys;
import io.poxray.xi.XiLink;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import io.poxray.sca.XmlDocs;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Online queries against a PO system: communication components by name and ICOs to / from components, expanded
 * into channels and ESR objects.
 * <p>
 * Verified on xipod (PO 7.50): SimpleQuery allows case-insensitive matching only for some keys, and the receiver of
 * an ICO is not queryable ({@code TOSRVC} finds nothing, receivers live in the ICO content). Components and ICOs
 * are therefore listed completely and filtered locally; ICO contents come through the snapshot cache, so only the
 * first query reads them from the system.
 */
public final class IntegrationQuery {

    /** in = component is a receiver, out = component is the sender, both = either. */
    public enum Direction { IN, OUT, BOTH }

    private final PoGateway client;
    private final XiObjectSource source;
    private final List<String> notes = new ArrayList<>();

    static final List<String> CLASSIC_TYPES = List.of("RoutingRelation", "MappingRelation", "InboundBinding",
            "OutboundBinding");
    /** Classic configuration types with their key elements (SimpleQuery typeInfo, PO 7.50). */
    static final Map<String, List<String>> CLASSIC_KEYS = TypeKeys.NAMES;
    /** Link targets of classic configuration objects expanded into the inventory. */
    private static final Set<String> EXPANDED_LINKS = Set.of("MAPPING", "ifmmessif", "Channel");

    public IntegrationQuery(PoGateway client, XiObjectSource source) {
        this.client = client;
        this.source = source;
    }

    /**
     * Communication components whose name (or party) matches {@code pattern}, case-insensitively: a plain text
     * matches as substring, {@code *} / {@code ?} are wildcards. Null or blank pattern = all.
     */
    public List<Component> components(String pattern) {
        Pattern p = pattern == null || pattern.isBlank() ? null : glob(pattern, true);
        List<Component> out = new ArrayList<>();
        List<XiRef> services;
        try {
            services = list("Service");
        } catch (PoException e) {
            if (e.getMessage() != null && e.getMessage().contains("authentication failed")) {
                throw e;
            }
            return componentsOfIcos(p, e);
        }
        for (XiRef ref : services) {
            String party = blank(ref.key(0));
            String name = ref.key(ref.key().size() > 1 ? 1 : 0);
            if (p == null || p.matcher(name).matches() || (party != null && p.matcher(party).matches())) {
                out.add(new Component(party, name, null));
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /**
     * ICOs where a component matching {@code componentPattern} (exact name, case-insensitive, or wildcard
     * pattern) is the sender ({@link Direction#OUT}), a receiver ({@link Direction#IN}) or either, expanded into
     * channels and ESR objects.
     */
    public IntegrationInventory icos(String componentPattern, Direction direction) {
        Pattern p = glob(componentPattern, false);
        IssueLog issues = new IssueLog();
        IntegrationReader reader = new IntegrationReader(source, issues);
        Set<String> matchedComponents = new LinkedHashSet<>();
        List<XiObject> selected = new ArrayList<>();
        for (XiRef ref : list("AllInOne")) {
            XiObject ico = source.resolve(ref).orElse(null);
            if (ico == null) {
                // e.g. inconsistent ICOs the PO itself cannot render (operation removed from the interface)
                issues.warn("OBJECT_NOT_READABLE", "ICO " + ref.label() + " cannot be read (so it is unknown whether"
                        + " it concerns the component)" + source.failure(ref).map(f -> ": " + f).orElse(""), null, null);
                continue;
            }
            boolean match = false;
            String sender = ico.id().key(1);
            if (direction != Direction.IN && p.matcher(sender).matches()) {
                matchedComponents.add(sender);
                match = true;
            }
            if (direction != Direction.OUT) {
                for (String receiver : receivers(ico)) {
                    if (p.matcher(receiver).matches()) {
                        matchedComponents.add(receiver);
                        match = true;
                    }
                }
            }
            if (match) {
                selected.add(ico);
            }
        }
        List<Ico> icos = reader.icos(selected);
        List<IntegrationInventory.ObjectDetail> classic = classicConfiguration(p, direction, reader,
                matchedComponents, issues);
        for (String note : source.offlineNotes()) {
            issues.warn("OFFLINE_SNAPSHOT", note, null, null);
        }
        List<Component> components = matchedComponents.stream().map(c -> new Component(null, c, null)).toList();
        return new IntegrationInventory(
                ScaInventory.SCHEMA_VERSION,
                new ScaInventory.Generator(PoXray.NAME, PoXray.VERSION,
                        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()),
                new IntegrationInventory.Source(client.system().name(), client.system().location(), List.of()),
                new IntegrationInventory.Query(componentPattern, direction.name().toLowerCase(Locale.ROOT)),
                components, icos, reader.channels(), classic, reader.esr(), issues.issues());
    }

    /**
     * Classic configuration (dual-stack / pre-7.5 style, no ICO): receiver and interface determinations, sender and
     * receiver agreements whose sender ({@code FROMSRVC}) or receiver ({@code TOSRVC}, or a receiver component linked
     * from a receiver determination) matches. Their interfaces, operation mappings and channels are expanded like
     * those of ICOs; the content itself (e.g. routing conditions) is kept as XML until verified on real data.
     */
    private List<IntegrationInventory.ObjectDetail> classicConfiguration(Pattern p, Direction direction,
            IntegrationReader reader, Set<String> matchedComponents, IssueLog issues) {
        List<IntegrationInventory.ObjectDetail> out = new ArrayList<>();
        for (String type : CLASSIC_TYPES) {
            List<String> names = CLASSIC_KEYS.get(type);
            for (XiRef ref : list(type)) {
                boolean match = false;
                String from = element(ref, names, "FROMSRVC");
                String to = element(ref, names, "TOSRVC");
                if (direction != Direction.IN && p.matcher(from).matches()) {
                    matchedComponents.add(from);
                    match = true;
                }
                if (direction != Direction.OUT && !to.isEmpty() && p.matcher(to).matches()) {
                    matchedComponents.add(to);
                    match = true;
                }
                XiObject o = null;
                if (!match && direction != Direction.OUT && "RoutingRelation".equals(type)) {
                    o = source.resolve(ref).orElse(null);
                    for (XiLink l : o == null ? List.<XiLink>of() : o.links()) {
                        String receiver = l.target().key(1);
                        if ("Service".equals(l.target().typeId()) && p.matcher(receiver).matches()) {
                            matchedComponents.add(receiver);
                            match = true;
                        }
                    }
                }
                if (!match) {
                    continue;
                }
                if (o == null) {
                    o = source.resolve(ref).orElse(null);
                }
                if (o == null) {
                    issues.warn("OBJECT_NOT_READABLE", ref.label() + " cannot be read"
                            + source.failure(ref).map(f -> ": " + f).orElse(""), null, null);
                    continue;
                }
                for (XiLink l : o.links()) {
                    if (EXPANDED_LINKS.contains(l.target().typeId())) {
                        reader.read(l.target());
                    }
                }
                out.add(ObjectCatalog.detail(o, names));
            }
        }
        if (!out.isEmpty()) {
            issues.info("CLASSIC_CONFIGURATION", out.size() + " classic configuration objects (receiver / interface"
                    + " determinations, agreements): their interfaces, mappings and channels are expanded, the content"
                    + " (e.g. routing conditions) is included as XML, not yet interpreted", null, null);
        }
        return out;
    }

    private static String element(XiRef ref, List<String> names, String name) {
        int i = names.indexOf(name);
        return i < 0 ? "" : ref.key(i);
    }

    /** All objects of a Directory type (references only). The condition is mandatory; {@code LIKE *} matches all. */
    private List<XiRef> list(String type) {
        QueryRequest all = new QueryRequest(Area.DIRECTORY, List.of(type), "MODIFYUSER", "*", "LIKE", false,
                List.of("RA_XILINK"));
        return source.list(type,
                () -> client.query(all).rows().stream().map(r -> r.xiLink()).filter(r -> r != null).toList());
    }

    /** Notes when results came from the offline snapshot because the PO system could not be reached. */
    public List<String> offlineNotes() {
        List<String> out = new ArrayList<>(source.offlineNotes());
        out.addAll(notes);
        return List.copyOf(out);
    }

    /** Offline fallback: components occurring as sender or receiver of the ICOs in the snapshot. */
    private List<Component> componentsOfIcos(Pattern p, PoException cause) {
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (XiRef ref : list("AllInOne")) {
            source.resolve(ref).ifPresent(ico -> {
                names.add(ico.id().key(1));
                names.addAll(receivers(ico));
            });
        }
        notes.add(cause.getMessage() + " - components derived from the ICOs in the snapshot (components without"
                + " ICO are missing)");
        return names.stream().filter(n -> !n.isBlank() && (p == null || p.matcher(n).matches()))
                .map(n -> new Component(null, n, null)).toList();
    }

    /** Receiver components of an ICO: receiver configurations (constant extractors) and the optional key part. */
    static List<String> receivers(XiObject ico) {
        List<String> out = new ArrayList<>();
        if (!ico.id().key(5).isEmpty()) {
            out.add(ico.id().key(5));
        }
        Element content = ico.content();
        Element configs = content == null ? null : XmlDocs.child(content, "ReceiverConfigurations");
        if (configs != null) {
            for (Element rc : XmlDocs.children(configs, "ReceiverConfiguration")) {
                Element receiver = XmlDocs.child(rc, "Receiver");
                Element ex = receiver == null ? null : XmlDocs.child(receiver, "ServiceExtractor");
                Element trd = ex == null ? null : XmlDocs.child(ex, "TRD_EXTRACTOR");
                String value = trd == null ? null : XmlDocs.childText(trd, "VALUE");
                if (value != null) {
                    out.add(value);
                }
            }
        }
        return out;
    }

    /**
     * Case-insensitive glob ({@code *}, {@code ?}). Without wildcards: substring if {@code containsByDefault},
     * else exact match.
     */
    static Pattern glob(String pattern, boolean containsByDefault) {
        String p = pattern.strip();
        boolean wildcard = p.contains("*") || p.contains("?");
        StringBuilder regex = new StringBuilder();
        for (char ch : p.toCharArray()) {
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        String body = !wildcard && containsByDefault ? ".*" + regex + ".*" : regex.toString();
        return Pattern.compile(body, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
