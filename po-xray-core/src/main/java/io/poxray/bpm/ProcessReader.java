package io.poxray.bpm;

import io.poxray.model.Expression;
import io.poxray.model.Flow;
import io.poxray.model.Interface;
import io.poxray.model.Mapping;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.Ref;
import io.poxray.model.UnmappedElement;
import io.poxray.moin.MoinElement;
import io.poxray.moin.MoinPartition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads one process ({@code src/bpmn/*.bpmn} partition) into {@link Process}. */
final class ProcessReader {

    static final String ARTIFICIAL_JOIN_PREFIX = "Artificial Uncontrolled Join";
    private static final int MAX_CONTRACTION = 100;

    private final BpmContext ctx;
    private final ExpressionReader expressions;
    private final MappingReader mappings;
    private final CatalogReader catalog;
    private final Usages usages;

    ProcessReader(BpmContext ctx, ExpressionReader expressions, MappingReader mappings, CatalogReader catalog,
            Usages usages) {
        this.ctx = ctx;
        this.expressions = expressions;
        this.mappings = mappings;
        this.catalog = catalog;
        this.usages = usages;
    }

    /** Cross-process usage collected while reading processes (for usedBy / unused checks in the catalog). */
    static final class Usages {
        final Map<String, List<Interface.Usage>> interfaces = new LinkedHashMap<>();
        final Set<String> eventDefinitions = new HashSet<>();
        final Set<String> functions = new HashSet<>();
        final Set<String> dataTypes = new LinkedHashSet<>();
        final Set<String> serviceReferences = new HashSet<>();
        final List<UnmappedElement> unmapped = new ArrayList<>();
    }

    /** Objects of one scope tree, grouped by role; each entry remembers its scope. */
    private record Scoped(MoinElement element, String scope) {
    }

    Process read(MoinPartition partition) {
        MoinElement collab = partition.elements().stream()
                .filter(e -> e.is(Galaxy.COLLABORATION)).findFirst().orElse(null);
        if (collab == null) {
            ctx.issues.warn("UNKNOWN_ELEMENT_TYPE", "Process partition without Collaboration",
                    ctx.at(null, null, null), ctx.source(partition.path()));
            return null;
        }
        String processName = collab.name();
        List<MoinElement> pools = ctx.index.firstElements(Galaxy.COLLABORATION_CONTAINS_POOLS, collab.id());
        MoinElement active = pools.stream().filter(p -> "true".equals(p.attr("active"))).findFirst()
                .orElse(pools.isEmpty() ? null : pools.getFirst());
        if (pools.stream().filter(p -> "true".equals(p.attr("active"))).count() != 1) {
            ctx.issues.warn("UNEXPECTED_STRUCTURE", "Expected exactly one active pool, found "
                    + pools.stream().filter(p -> "true".equals(p.attr("active"))).count(),
                    ctx.at(processName, collab, null), ctx.source(partition.path()));
        }

        // ---- collect scope objects
        List<Process.Scope> scopes = new ArrayList<>();
        List<Scoped> objects = new ArrayList<>();
        if (active != null) {
            visitScope(active, "process", null, scopes, objects);
        }
        for (MoinElement p : pools) {
            if (p != active) {
                visitScope(p, "pool", null, scopes, objects);
            }
        }
        Map<String, String> scopeOf = new LinkedHashMap<>();
        for (Scoped s : objects) {
            scopeOf.putIfAbsent(s.element().id(), s.scope());
        }
        // boundary events are attached to activities; make sure none is missed
        for (Scoped s : List.copyOf(objects)) {
            for (MoinElement be : ctx.index.firstElements(Galaxy.ACTIVITY_BOUNDARY_EVENT, s.element().id())) {
                if (!scopeOf.containsKey(be.id())) {
                    scopeOf.put(be.id(), s.scope());
                    objects.add(new Scoped(be, s.scope()));
                }
            }
        }

        // ---- artificial joins
        Set<String> artificial = new LinkedHashSet<>();
        for (Scoped s : objects) {
            MoinElement e = s.element();
            if (e.is(Galaxy.UNCONTROLLED_JOIN) && e.name() != null && e.name().startsWith(ARTIFICIAL_JOIN_PREFIX)) {
                int outgoing = ctx.index.seconds(Galaxy.SOURCE_OF_CONNECTORS, e.id()).size();
                if (outgoing == 1) {
                    artificial.add(e.id());
                } else {
                    ctx.issues.warn("UNEXPECTED_STRUCTURE", "Artificial join with " + outgoing
                            + " outgoing connectors is kept as a node", ctx.at(processName, e, null),
                            ctx.source(e.partition()));
                }
            }
        }

        // ---- classify
        List<Node> nodes = new ArrayList<>();
        List<Flow> flows = new ArrayList<>();
        List<Process.DataObject> dataObjects = new ArrayList<>();
        List<Process.Lane> lanes = new ArrayList<>();
        List<Process.Role> roles = new ArrayList<>();
        List<Process.Annotation> annotations = new ArrayList<>();
        List<Ref> contracted = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (Scoped s : objects) {
            MoinElement e = s.element();
            if (!done.add(e.id())) {
                continue;
            }
            switch (e.metaclass()) {
                case Galaxy.SEQUENCE_CONNECTOR, Galaxy.CONDITIONAL_SEQUENCE_CONNECTOR -> {
                    Flow f = flow(e, artificial, processName);
                    if (f != null) {
                        flows.add(f);
                    }
                }
                case Galaxy.DATA_OBJECT, Galaxy.TEXT_VARIABLE -> dataObjects.add(dataObject(e, refOf(s.scope()), processName));
                case Galaxy.LANE -> lanes.add(new Process.Lane(e.id(), e.name(), blank(e.text("documentation"))));
                case Galaxy.PROCESS_ROLE, Galaxy.LANE_PROCESS_ROLE -> roles.add(new Process.Role(e.id(), e.name(),
                        e.is(Galaxy.PROCESS_ROLE) ? "process" : "lane", blank(e.text("documentation")),
                        "true".equals(e.attr("dynamic")) ? Boolean.TRUE : null,
                        mappings.readLinked(Galaxy.VIEW_MAPPING, e.id(), processName, e)));
                case Galaxy.ANNOTATION -> annotations.add(annotation(e));
                case Galaxy.ANNOTATION_CONNECTOR -> {
                    // consumed by annotation()
                }
                default -> {
                    if (artificial.contains(e.id())) {
                        contracted.add(ctx.ref(e));
                    } else {
                        nodes.add(node(e, refOf(s.scope()), processName));
                    }
                }
            }
        }

        reportUnmapped(partition, processName);
        reportUnusedDataObjects(dataObjects, nodes, flows, roles, processName);

        Process.Pool pool = active == null ? null : new Process.Pool(active.id(), active.name(),
                active.attr("identifier"), blank(active.text("subject")), blank(active.text("description")));
        return new Process(collab.id(), processName, blank(collab.text("documentation")), pool,
                ctx.source(partition.path()), lanes, roles, scopes, dataObjects, nodes, flows, annotations,
                contracted.isEmpty() ? null : new Process.EditorArtifacts(contracted));
    }

    private void visitScope(MoinElement scope, String kind, String parent, List<Process.Scope> scopes,
            List<Scoped> objects) {
        scopes.add(new Process.Scope(scope.id(), kind, scope.name(), parent));
        for (MoinElement o : ctx.index.firstElements(Galaxy.SCOPE_CONTAINS_OBJECTS, scope.id())) {
            objects.add(new Scoped(o, scope.id()));
            if (o.is(Galaxy.EMBEDDED_SCOPE)) {
                visitScope(o, "embeddedSubprocess", scope.id(), scopes, objects);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ flows

    private Flow flow(MoinElement c, Set<String> artificial, String process) {
        String source = ctx.index.first(Galaxy.SOURCE_OF_CONNECTORS, c.id()).orElse(null);
        String target = ctx.index.first(Galaxy.TARGET_OF_CONNECTORS, c.id()).orElse(null);
        if (source != null && artificial.contains(source)) {
            return null; // outgoing connector of an artificial join: replaced by contraction
        }
        if (source == null || target == null) {
            ctx.issues.warn("UNEXPECTED_STRUCTURE", "Sequence connector without " + (source == null ? "source" : "target"),
                    ctx.at(process, c, null), ctx.source(c.partition()));
        }
        List<Ref> via = new ArrayList<>();
        while (target != null && artificial.contains(target) && via.size() < MAX_CONTRACTION) {
            via.add(refOf(target));
            String next = ctx.index.seconds(Galaxy.SOURCE_OF_CONNECTORS, target).getFirst();
            target = ctx.index.first(Galaxy.TARGET_OF_CONNECTORS, next).orElse(null);
        }
        Expression condition = c.is(Galaxy.CONDITIONAL_SEQUENCE_CONNECTOR)
                ? expressions.read(ctx.index.first(Galaxy.CONNECTOR_EXPRESSION, c.id()).orElse(null), process)
                : null;
        return new Flow(c.id(), refOf(source), refOf(target), blank(c.name()), condition,
                "true".equals(c.attr("isDefault")) ? Boolean.TRUE : null, via.isEmpty() ? null : via);
    }

    // ------------------------------------------------------------------------------------------ nodes

    private Node node(MoinElement e, Ref scope, String process) {
        Node.Builder b = new Node.Builder(e.id(), kindOf(e))
                .name(e.name())
                .documentation(blank(e.text("documentation")))
                .scope(scope)
                .lane(refOf(ctx.index.second(Galaxy.LANE_HAS_OBJECTS, e.id()).orElse(null)));
        switch (e.metaclass()) {
            case Galaxy.START_EVENT -> {
                Node.Trigger trigger = trigger(e, process);
                b.trigger(trigger).condition(catchCondition(e, process))
                        .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e));
                if (trigger != null && trigger.eventDefinition() != null && "message".equals(trigger.kind())) {
                    Node.OperationRef op = catalog.operationRef(
                            catalog.messageEventOperation(trigger.eventDefinition().ref()));
                    use(op, process, e, "start");
                }
            }
            case Galaxy.END_EVENT -> {
                b.terminate("true".equals(e.attr("terminate")) ? Boolean.TRUE : null)
                        .throwsEvent(eventDefinitionRef(ctx.index.first(Galaxy.THROW_EVENT_RESULT, e.id()), process, e))
                        .inputMapping(mappings.readLinked(Galaxy.INPUT_MAPPING, e.id(), process, e));
            }
            case Galaxy.BOUNDARY_EVENT -> b
                    .attachedTo(ctx.ref(ctx.index.secondElement(Galaxy.ACTIVITY_BOUNDARY_EVENT, e.id()).orElse(null)))
                    .trigger(trigger(e, process))
                    .condition(catchCondition(e, process))
                    .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e));
            case Galaxy.AUTOMATED_ACTIVITY -> {
                Node.OperationRef op = catalog.operationRef(ctx.index.first(Galaxy.ACTIVITY_OPERATION, e.id()).orElse(null));
                use(op, process, e, "outbound");
                b.operation(op)
                        .serviceReference(serviceReferenceRef(e))
                        .inputMapping(mappings.readLinked(Galaxy.INPUT_MAPPING, e.id(), process, e))
                        .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e))
                        .errorEvents(errorEvents(e))
                        .boundaryEvents(boundaryEvents(e));
            }
            case Galaxy.MAPPING_ACTIVITY -> b
                    .inputMapping(mappings.readLinked(Galaxy.INPUT_MAPPING, e.id(), process, e))
                    .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e));
            case Galaxy.NOTIFICATION_ACTIVITY -> {
                // e-mail notification; its operation is a generated per-activity NotificationInterface
                Node.OperationRef op = catalog.operationRef(ctx.index.first(Galaxy.ACTIVITY_OPERATION, e.id()).orElse(null));
                use(op, process, e, "notification");
                b.subject(blank(e.text("subject"))).body(blank(e.text("messageBody")))
                        .inputMapping(mappings.readLinked(Galaxy.INPUT_MAPPING, e.id(), process, e))
                        .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e));
            }
            case Galaxy.EMBEDDED_SCOPE -> b
                    .scopeId(e.id())
                    .inputMapping(mappings.readLinked(Galaxy.INPUT_MAPPING, e.id(), process, e))
                    .outputMapping(mappings.readLinked(Galaxy.OUTPUT_MAPPING, e.id(), process, e))
                    .boundaryEvents(boundaryEvents(e));
            case Galaxy.EXCLUSIVE_GATEWAY, Galaxy.UNCONTROLLED_JOIN -> {
                // conditions live on the outgoing flows
            }
            default -> {
                b.metaclass(e.metaclass()).attributes(e.attributes());
                ctx.issues.warn("UNKNOWN_NODE_TYPE", "Node type " + e.metaclass() + " is not interpreted yet",
                        ctx.at(process, e, null), ctx.source(e.partition()));
            }
        }
        return b.build();
    }

    private static String kindOf(MoinElement e) {
        return switch (e.metaclass()) {
            case Galaxy.START_EVENT -> "startEvent";
            case Galaxy.END_EVENT -> "endEvent";
            case Galaxy.BOUNDARY_EVENT -> "boundaryEvent";
            case Galaxy.AUTOMATED_ACTIVITY -> "automatedActivity";
            case Galaxy.MAPPING_ACTIVITY -> "mappingActivity";
            case Galaxy.NOTIFICATION_ACTIVITY -> "notificationActivity";
            case Galaxy.EXCLUSIVE_GATEWAY -> "exclusiveGateway";
            case Galaxy.UNCONTROLLED_JOIN -> "joinGateway";
            case Galaxy.EMBEDDED_SCOPE -> "embeddedSubprocess";
            default -> "unknown";
        };
    }

    private Node.Trigger trigger(MoinElement event, String process) {
        String id = ctx.index.first(Galaxy.CATCH_EVENT_TRIGGER, event.id()).orElse(null);
        if (id == null) {
            return null;
        }
        MoinElement def = ctx.index.get(id).orElse(null);
        if (def != null) {
            usages.eventDefinitions.add(def.id());
            String kind = def.is(Galaxy.MESSAGE_EVENT_DEFINITION) ? "message"
                    : def.is(Galaxy.ERROR_EVENT_DEFINITION) ? "error" : def.simpleType();
            return new Node.Trigger(kind, ctx.ref(def), null);
        }
        String builtin = Builtins.TRIGGERS.get(id);
        if (builtin != null) {
            return new Node.Trigger("error", new Ref(id, builtin), builtin);
        }
        ctx.issues.warn("UNRESOLVED_REF", "Trigger " + id + " is neither in the DC nor a known SAP built-in",
                ctx.at(process, event, null), ctx.source(event.partition()));
        return new Node.Trigger("unknown", new Ref(id, null), null);
    }

    /** Catch event condition; the trivial {@code true} is omitted. */
    private Expression catchCondition(MoinElement event, String process) {
        Expression x = expressions.read(ctx.index.second(Galaxy.CATCH_EVENT_EXPRESSION, event.id()).orElse(null), process);
        return x == null || "true".equals(x.text() == null ? null : x.text().strip()) ? null : x;
    }

    private Ref eventDefinitionRef(Optional<String> id, String process, MoinElement owner) {
        if (id.isEmpty()) {
            return null;
        }
        MoinElement def = ctx.index.get(id.get()).orElse(null);
        if (def == null) {
            ctx.issues.warn("UNRESOLVED_REF", "Event definition " + id.get() + " not found",
                    ctx.at(process, owner, null), ctx.source(owner.partition()));
            return new Ref(id.get(), null);
        }
        usages.eventDefinitions.add(def.id());
        return ctx.ref(def);
    }

    private Ref serviceReferenceRef(MoinElement activity) {
        Ref ref = ctx.index.firstElement(Galaxy.ACTIVITY_SERVICE_REFERENCE, activity.id())
                .map(sr -> new Ref(sr.id(), ctx.qname(Galaxy.SERVICE_REFERENCE_QNAME, sr.id())))
                .orElse(null);
        if (ref != null) {
            usages.serviceReferences.add(ref.ref());
        }
        return ref;
    }

    private List<Node.ErrorEvent> errorEvents(MoinElement activity) {
        List<Node.ErrorEvent> out = new ArrayList<>();
        for (MoinElement d : ctx.index.firstElements(Galaxy.ACTIVITY_EVENT_DEFINITIONS, activity.id())) {
            Ref fault = ctx.index.firstElement(Galaxy.ERROR_EVENT_FAULT, d.id())
                    .map(f -> new Ref(f.id(), f.attr("name"))).orElse(null);
            out.add(new Node.ErrorEvent(d.id(), d.name(), fault));
        }
        return out;
    }

    private List<Ref> boundaryEvents(MoinElement activity) {
        return ctx.index.firstElements(Galaxy.ACTIVITY_BOUNDARY_EVENT, activity.id()).stream().map(ctx::ref).toList();
    }

    private void use(Node.OperationRef op, String process, MoinElement node, String role) {
        if (op != null && op.iface() != null) {
            usages.interfaces.computeIfAbsent(op.iface().ref(), k -> new ArrayList<>())
                    .add(new Interface.Usage(process, ctx.ref(node), role));
        }
    }

    // ------------------------------------------------------------------------------------------ data, annotations

    private Process.DataObject dataObject(MoinElement e, Ref scope, String process) {
        MoinElement type = ctx.index.firstElement(Galaxy.DATA_CONTAINER_TYPE, e.id()).orElse(null);
        if (type != null) {
            usages.dataTypes.add(type.id());
        }
        return new Process.DataObject(e.id(), e.name(), e.is(Galaxy.TEXT_VARIABLE) ? "textVariable" : "dataObject",
                scope, blank(e.text("documentation")),
                type == null ? null : new Process.TypeRef(type.id(), type.attr("name"), type.attr("namespace")),
                mappings.readLinked(Galaxy.VIEW_MAPPING, e.id(), process, e));
    }

    private Process.Annotation annotation(MoinElement a) {
        List<Ref> attached = new ArrayList<>();
        for (String connector : ctx.index.seconds(Galaxy.ANNOTATION_OF_CONNECTOR, a.id())) {
            for (MoinElement target : ctx.index.firstElements(Galaxy.CONNECTOR_MODEL_ELEMENT, connector)) {
                attached.add(ctx.ref(target));
            }
        }
        String text = blank(a.text("documentation"));
        return new Process.Annotation(a.id(), text != null ? text : blank(a.name()), attached);
    }

    // ------------------------------------------------------------------------------------------ checks

    private void reportUnmapped(MoinPartition partition, String process) {
        Set<String> reported = new HashSet<>();
        for (MoinElement e : partition.elements()) {
            if (Galaxy.HANDLED_IN_PROCESS.contains(e.metaclass()) || Galaxy.isXsdOrWsdl(e.metaclass())) {
                continue;
            }
            usages.unmapped.add(new UnmappedElement(ctx.dc, e.metaclass(), e.id(), ctx.source(e.partition()),
                    e.attributes()));
            if (reported.add(e.metaclass())) {
                ctx.issues.info("UNKNOWN_ELEMENT_TYPE", "Element type " + e.metaclass() + " is not interpreted",
                        ctx.at(process, e, null), ctx.source(e.partition()));
            }
        }
    }

    /** A data object is unused when no expression reads it and no mapping writes it. */
    private void reportUnusedDataObjects(List<Process.DataObject> dataObjects, List<Node> nodes, List<Flow> flows,
            List<Process.Role> roles, String process) {
        Set<String> used = new HashSet<>();
        for (Node n : nodes) {
            collect(n.inputMapping(), used);
            collect(n.outputMapping(), used);
            collect(n.condition(), used);
        }
        for (Flow f : flows) {
            collect(f.condition(), used);
        }
        for (Process.Role r : roles) {
            collect(r.assignment(), used);
        }
        for (Process.DataObject d : dataObjects) {
            collect(d.initialValue(), used);
        }
        for (Process.DataObject d : dataObjects) {
            if ("dataObject".equals(d.kind()) && !used.contains(d.name()) && !isSapSystemType(d.type())) {
                ctx.issues.info("UNUSED_OBJECT", "Data object '" + d.name() + "' is never read nor written",
                        ctx.at(process, ctx.index.get(d.id()).orElse(null), null), null);
            }
        }
    }

    /** System data objects generated by the editor (e.g. ProcessInstanceAttributes) have SAP built-in types. */
    private boolean isSapSystemType(Process.TypeRef type) {
        return type != null && ctx.index.second(Galaxy.ELEMENT_TYPE, type.ref())
                .map(BuiltinFunctionCatalog.get()::typeName).map(t -> t.startsWith("sap:")).orElse(false);
    }

    private void collect(Mapping m, Set<String> used) {
        if (m == null) {
            return;
        }
        for (Mapping.Assignment a : m.assignments()) {
            used.add(firstSegment(a.target()));
            collect(a.expr(), used);
        }
    }

    private void collect(Expression x, Set<String> used) {
        if (x == null) {
            return;
        }
        for (String r : x.reads()) {
            used.add(firstSegment(r));
        }
        x.functions().forEach(f -> usages.functions.add(f.id()));
    }

    private static String firstSegment(String path) {
        int i = path.indexOf('/');
        return i < 0 ? path : path.substring(0, i);
    }

    /** {@code {ref, name}} for an ID; the name is null if the ID is not in the DC. */
    private Ref refOf(String id) {
        return id == null ? null : new Ref(id, ctx.index.get(id).map(MoinElement::name).orElse(null));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
