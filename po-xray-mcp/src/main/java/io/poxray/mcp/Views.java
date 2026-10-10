package io.poxray.mcp;

import io.poxray.integration.XsdFields;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.DataType;
import io.poxray.model.EjbModule;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.Component;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Query;
import io.poxray.model.Issue;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.ServiceReference;
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent-sized views of the inventories: an overview first, single objects on request (MCP clients truncate large
 * tool results, an SCA inventory can be several hundred KB).
 */
final class Views {

    /** Kinds accepted by {@code get_object}. */
    static final List<String> INTEGRATION_KINDS = List.of("ico", "channel", "serviceInterface", "operationMapping",
            "messageMapping", "messageType", "dataType", "externalDefinition", "functionLibrary", "importedArchive");
    /** Kinds accepted by {@code get_sca_object}. */
    static final List<String> SCA_KINDS = List.of("process", "step", "processData", "catalog", "function",
            "interface", "serviceReference", "dataType", "ejbModule", "developmentComponent", "issues");

    private Views() {
    }

    // ------------------------------------------------------------------------------------------ integration

    /** ICOs with routing and mappings by name; channels without attributes; ESR objects by name only. */
    record IcoOverview(Query query, List<Component> components, List<Ico> icos, List<ChannelSummary> channels,
            Map<String, List<String>> esrObjects, List<Issue> issues, String next) {
    }

    record ChannelSummary(DirRef id, String direction, String adapter, List<String> modules) {
    }

    /** A data type with the fields derived from its XSD. */
    record DataTypeDetail(IntegrationInventory.DataType dataType, List<DataType.Field> fields) {
    }

    static IcoOverview overview(IntegrationInventory inv) {
        List<ChannelSummary> channels = inv.channels().stream()
                .map(c -> new ChannelSummary(c.id(), c.direction(), c.adapter(),
                        c.modules().stream().map(IntegrationInventory.Module::name).toList()))
                .toList();
        var esr = inv.esr();
        Map<String, List<String>> objects = new LinkedHashMap<>();
        put(objects, "serviceInterface", esr.serviceInterfaces(), s -> label(s.id()));
        put(objects, "operationMapping", esr.operationMappings(), s -> label(s.id()));
        put(objects, "messageMapping", esr.messageMappings(), s -> label(s.id()));
        put(objects, "functionLibrary", esr.functionLibraries(), s -> label(s.id()));
        put(objects, "messageType", esr.messageTypes(), s -> label(s.id()));
        put(objects, "dataType", esr.dataTypes(), s -> label(s.id()));
        put(objects, "externalDefinition", esr.externalDefinitions(), s -> label(s.id()));
        put(objects, "importedArchive", esr.importedArchives(), s -> label(s.id()));
        put(objects, "mappingProgram", esr.mappingPrograms(), s -> s.type() + " " + label(s)
                + (s.program() == null ? "" : " : " + s.program()));
        return new IcoOverview(inv.query(), inv.components(), inv.icos(), channels, objects, inv.issues(),
                "Details: get_object(kind, name) with kind one of " + String.join(", ", INTEGRATION_KINDS)
                        + "; channel names as 'component|name'.");
    }

    /** Objects of {@code kind} whose name equals {@code name} (case-insensitive); channels also by component|name. */
    static List<Object> find(IntegrationInventory inv, String kind, String name) {
        String n = name.strip().toLowerCase(Locale.ROOT);
        var esr = inv.esr();
        List<Object> out = new ArrayList<>();
        switch (kind) {
            case "ico" -> inv.icos().stream().filter(i -> i.sender() != null && i.sender().iface() != null
                    && eq(i.sender().iface().name(), n)).forEach(out::add);
            case "channel" -> inv.channels().stream().filter(c -> matches(c, n)).forEach(out::add);
            case "serviceInterface" -> esr.serviceInterfaces().stream().filter(o -> eq(o.id().name(), n)).forEach(out::add);
            case "operationMapping" -> esr.operationMappings().stream().filter(o -> eq(o.id().name(), n)).forEach(out::add);
            case "messageMapping" -> esr.messageMappings().stream().filter(o -> eq(o.id().name(), n)).forEach(out::add);
            case "messageType" -> esr.messageTypes().stream().filter(o -> eq(o.id().name(), n)).forEach(out::add);
            case "dataType" -> esr.dataTypes().stream().filter(o -> eq(o.id().name(), n))
                    .map(d -> new DataTypeDetail(d, XsdFields.fields(d.xsd(), d.id().name()))).forEach(out::add);
            case "externalDefinition" -> esr.externalDefinitions().stream().filter(o -> eq(o.id().name(), n))
                    .forEach(out::add);
            case "functionLibrary" -> esr.functionLibraries().stream().filter(o -> eq(o.id().name(), n))
                    .forEach(out::add);
            case "importedArchive" -> esr.importedArchives().stream().filter(o -> eq(o.id().name(), n))
                    .forEach(out::add);
            default -> throw new IllegalArgumentException("Unknown kind '" + kind + "', use one of "
                    + String.join(", ", INTEGRATION_KINDS));
        }
        return out;
    }

    private static boolean matches(Channel c, String n) {
        DirRef id = c.id();
        return eq(id.name(), n) || eq(id.component() + "|" + id.name(), n)
                || eq((id.party() == null ? "" : id.party()) + "|" + id.component() + "|" + id.name(), n);
    }

    // ------------------------------------------------------------------------------------------ SCA

    /** SCA overview: software component, DCs, processes and EJB modules by name, issue counts. */
    record ScaOverview(ScaInventory.ScaSource source, List<String> developmentComponents,
            List<ProcessSummary> processes, List<String> catalogs, List<EjbSummary> ejbModules,
            Map<String, Long> issues, String next) {
    }

    record ProcessSummary(String dc, String name, String id, int nodes, int flows) {
    }

    record EjbSummary(String dc, String application, int beans) {
    }

    static ScaOverview overview(ScaInventory inv) {
        List<ProcessSummary> processes = new ArrayList<>();
        List<String> catalogs = new ArrayList<>();
        for (BpmComponent b : inv.bpm()) {
            b.processes().forEach(p -> processes.add(new ProcessSummary(b.dc(), p.name(), p.id(),
                    p.nodes().size(), p.flows().size())));
            if (b.catalog() != null) {
                catalogs.add(b.dc());
            }
        }
        List<EjbSummary> ejb = inv.ejb().stream()
                .map(m -> new EjbSummary(m.dc(), m.application(), m.beans().size())).toList();
        Map<String, Long> issues = inv.issues().stream()
                .collect(Collectors.groupingBy(Issue::severity, LinkedHashMap::new, Collectors.counting()));
        return new ScaOverview(inv.source(), inv.developmentComponents().stream().map(d -> d.name()).toList(),
                processes, catalogs, ejb, issues,
                "Details: get_sca_object(kind, name) with kind one of " + String.join(", ", SCA_KINDS)
                        + " (process / processData by process name or id; step as '<process>/<step name or id>' with"
                        + " its input / output mappings; catalog / developmentComponent / ejbModule by DC name;"
                        + " issues: name = severity or empty).");
    }

    static List<Object> find(ScaInventory inv, String kind, String name) {
        String n = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        List<Object> out = new ArrayList<>();
        switch (kind) {
            case "process" -> processes(inv, n).forEach(p -> out.add(outline(p)));
            case "processData" -> processes(inv, n).forEach(p -> out.add(new ProcessData(p.name(), p.dataObjects(),
                    p.roles())));
            case "step" -> {
                int slash = n.lastIndexOf('/');
                String step = slash < 0 ? n : n.substring(slash + 1);
                List<Process> scope = slash < 0 ? inv.bpm().stream().flatMap(b -> b.processes().stream()).toList()
                        : processes(inv, n.substring(0, slash));
                for (BpmComponent b : inv.bpm()) {
                    for (Process p : b.processes()) {
                        if (!scope.contains(p)) {
                            continue;
                        }
                        p.nodes().stream().filter(node -> eq(node.name(), step) || eq(node.id(), step))
                                .forEach(node -> out.add(new StepDetail(node, serviceReference(b, node))));
                    }
                }
            }
            case "catalog" -> inv.bpm().stream().filter(b -> eq(b.dc(), n) && b.catalog() != null)
                    .map(b -> catalogOverview(b.dc(), b.catalog())).forEach(out::add);
            case "function" -> catalogs(inv).flatMap(c -> c.functions().stream()).filter(f -> eq(f.name(), n))
                    .forEach(out::add);
            case "interface" -> catalogs(inv).flatMap(c -> c.interfaces().stream()).filter(i -> eq(i.name(), n))
                    .forEach(out::add);
            case "serviceReference" -> catalogs(inv).flatMap(c -> c.serviceReferences().stream())
                    .filter(s -> eq(s.uuid(), n) || (s.iface() != null && eq(s.iface().name(), n))).forEach(out::add);
            case "dataType" -> dataTypes(catalogs(inv).flatMap(c -> c.dataTypes().stream())
                    .filter(d -> eq(d.name(), n)).toList()).forEach(out::add);
            case "ejbModule" -> inv.ejb().stream().filter((EjbModule m) -> eq(m.dc(), n) || eq(m.application(), n))
                    .forEach(out::add);
            case "developmentComponent" -> inv.developmentComponents().stream().filter(d -> eq(d.name(), n))
                    .forEach(out::add);
            case "issues" -> inv.issues().stream().filter(i -> n.isEmpty() || eq(i.severity(), n)).forEach(out::add);
            default -> throw new IllegalArgumentException("Unknown kind '" + kind + "', use one of "
                    + String.join(", ", SCA_KINDS));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ catalog

    /**
     * Catalog of a BPM DC by name only (the full catalog of a real DC exceeds what MCP clients accept, mostly
     * because of the data types); details with the kinds function, interface, serviceReference, dataType.
     */
    record CatalogOverview(String dc, List<InterfaceSummary> interfaces, List<ServiceReference> serviceReferences,
            List<FunctionSummary> functions, List<String> builtinFunctions, List<String> eventDefinitions,
            List<DataTypeSummary> dataTypes, String next) {
    }

    record InterfaceSummary(String name, String namespace, List<String> operations, int usages) {
    }

    /** {@code implementation}: EJB bean @ application or lookup name; {@code usages}: number of calls. */
    record FunctionSummary(String name, String implementation, int usages) {
    }

    /** {@code occurrences}: in how many process / pool namespaces a type of this name and structure is defined. */
    record DataTypeSummary(String name, String type, int fields, int occurrences) {
    }

    /**
     * A data type defined identically in several namespaces (process-local types repeat per process / pool), with
     * one line per field: {@code path : type [min..max]} (type omitted for anonymous structures, cardinality
     * only when not 1..1).
     */
    record DataTypeView(String name, String type, List<String> namespaces, List<String> fields) {
    }

    static CatalogOverview catalogOverview(String dc, BpmComponent.Catalog c) {
        List<InterfaceSummary> interfaces = c.interfaces().stream().map(i -> new InterfaceSummary(i.name(),
                i.namespace(), i.operations().stream().map(o -> o.name()).toList(),
                i.usedBy() == null ? 0 : i.usedBy().size())).toList();
        List<FunctionSummary> functions = c.functions().stream().map(f -> new FunctionSummary(f.name(),
                f.ejb() == null ? f.lookupName() : f.ejb().bean() + " @ " + f.ejb().application(),
                f.usedBy() == null ? 0 : f.usedBy().size())).toList();
        List<DataTypeSummary> types = dataTypes(c.dataTypes()).stream().map(t -> new DataTypeSummary(t.name(),
                t.type(), t.fields().size(), t.namespaces().size())).toList();
        return new CatalogOverview(dc, interfaces, c.serviceReferences(), functions,
                c.builtinFunctions().stream().map(b -> b.name()).distinct().toList(),
                java.util.stream.Stream.concat(c.eventDefinitions().stream(), c.builtinEventDefinitions().stream())
                        .map(e -> e.name()).toList(),
                types, "Details: get_sca_object with kind function (incl. where it is called), interface,"
                        + " serviceReference (by interface name), dataType (fields) and the name.");
    }

    private static java.util.stream.Stream<BpmComponent.Catalog> catalogs(ScaInventory inv) {
        return inv.bpm().stream().map(BpmComponent::catalog).filter(java.util.Objects::nonNull);
    }

    /** Data types grouped by name, type and fields; fields as compact lines. */
    static List<DataTypeView> dataTypes(List<DataType> types) {
        Map<String, DataTypeView> out = new LinkedHashMap<>();
        for (DataType t : types) {
            List<String> fields = t.fields() == null ? List.of() : t.fields().stream().map(Views::field).toList();
            String key = t.name() + "|" + t.type() + "|" + fields;
            DataTypeView v = out.get(key);
            List<String> namespaces = new ArrayList<>(v == null ? List.of() : v.namespaces());
            namespaces.add(t.namespace());
            out.put(key, new DataTypeView(t.name(), t.type(), List.copyOf(namespaces), fields));
        }
        return List.copyOf(out.values());
    }

    private static String field(DataType.Field f) {
        boolean single = (f.min() == null || f.min() == 1) && (f.max() == null || "1".equals(f.max()));
        return f.path() + (f.type() == null ? "" : " : " + f.type()) + (single ? "" : " [" + (f.min() == null ? 1
                : f.min()) + ".." + (f.max() == null ? "1" : f.max()) + "]");
    }

    /** A step with the service reference it calls (binding, service group, ESR operation mapping if any). */
    record StepDetail(Node step, ServiceReference serviceReference) {
    }

    private static ServiceReference serviceReference(BpmComponent b, Node node) {
        if (node.serviceReference() == null || b.catalog() == null) {
            return null;
        }
        return b.catalog().serviceReferences().stream().filter(s -> s.id().equals(node.serviceReference().ref()))
                .findFirst().orElse(null);
    }

    /** Data objects and roles of a process (kept out of the outline). */
    record ProcessData(String process, Object dataObjects, Object roles) {
    }

    private static List<Process> processes(ScaInventory inv, String lowerName) {
        return inv.bpm().stream().flatMap(b -> b.processes().stream())
                .filter(p -> eq(p.name(), lowerName) || eq(p.id(), lowerName)).toList();
    }

    /**
     * Process without the bulky parts: step mappings are replaced by the list of their kinds
     * ({@code "mappings": ["inputMapping", ...]}, fetch with kind step), data objects and roles by their names
     * (kind processData).
     */
    static JsonNode outline(Process p) {
        ObjectNode o = Json.mapper().valueToTree(p);
        JsonNode nodes = o.get("nodes");
        if (nodes != null) {
            for (JsonNode node : nodes) {
                ObjectNode n = (ObjectNode) node;
                List<String> mappings = new ArrayList<>();
                for (String field : List.copyOf(n.propertyNames())) {
                    if (field.endsWith("Mapping") || field.endsWith("Mappings")) {
                        mappings.add(field);
                        n.remove(field);
                    }
                }
                if (!mappings.isEmpty()) {
                    ArrayNode a = n.putArray("mappings");
                    mappings.forEach(a::add);
                }
            }
        }
        for (String field : List.of("dataObjects", "roles")) {
            JsonNode items = o.get(field);
            if (items != null && items.isArray()) {
                ArrayNode names = Json.mapper().createArrayNode();
                items.forEach(i -> names.add(i.path("name").asString(i.path("id").asString(""))));
                o.set(field, names);
            }
        }
        return o;
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static <T> void put(Map<String, List<String>> out, String kind, List<T> items, Function<T, String> label) {
        if (!items.isEmpty()) {
            out.put(kind, items.stream().map(label).toList());
        }
    }

    private static String label(ObjRef r) {
        return r.name() + (r.namespace() == null ? "" : " | " + r.namespace());
    }

    private static boolean eq(String value, String lowerName) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(lowerName);
    }
}
