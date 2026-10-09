package io.poxray.mcp;

import io.poxray.integration.XsdFields;
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
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent-sized views of the inventories: an overview first, single objects on request (MCP clients truncate large
 * tool results, an SCA inventory can be several hundred KB).
 */
final class Views {

    /** Kinds accepted by {@code get_object}. */
    static final List<String> INTEGRATION_KINDS = List.of("ico", "channel", "serviceInterface", "operationMapping",
            "messageMapping", "messageType", "dataType", "externalDefinition", "functionLibrary");
    /** Kinds accepted by {@code get_sca_object}. */
    static final List<String> SCA_KINDS = List.of("process", "catalog", "ejbModule", "developmentComponent", "issues");

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
        put(objects, "mappingProgram", esr.mappingPrograms(), s -> s.type() + " " + label(s));
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
                        + " (process by name or id, catalog / developmentComponent / ejbModule by DC name, issues: name = severity"
                        + " or empty).");
    }

    static List<Object> find(ScaInventory inv, String kind, String name) {
        String n = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        List<Object> out = new ArrayList<>();
        switch (kind) {
            case "process" -> inv.bpm().stream().flatMap(b -> b.processes().stream())
                    .filter(p -> eq(p.name(), n) || eq(p.id(), n)).forEach(out::add);
            case "catalog" -> inv.bpm().stream().filter(b -> eq(b.dc(), n) && b.catalog() != null)
                    .map(BpmComponent::catalog).forEach(out::add);
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
