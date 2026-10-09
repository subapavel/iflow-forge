package io.poxray.doc;

import static io.poxray.doc.Doc.code;
import static io.poxray.doc.Doc.line;
import static io.poxray.doc.Doc.text;

import io.poxray.model.BpmComponent;
import io.poxray.model.BuiltinFunction;
import io.poxray.model.DataType;
import io.poxray.model.DevelopmentComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.EventDefinition;
import io.poxray.model.Flow;
import io.poxray.model.Function;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.Mapping;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.Ref;
import io.poxray.model.ScaInventory;
import io.poxray.model.ServiceReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the human-readable documentation of an {@link ScaInventory} as a format-neutral {@link Doc} model:
 * overview, processes (steps in flow order, mappings with all part flags, conditions, boundary events),
 * DC catalog, EJB modules, issues and an appendix with data types.
 */
public final class DocumentBuilder {

    private static final int EDGE_LABEL_MAX = 40;

    private final Labels l;
    private final List<Doc.Block> out = new ArrayList<>();

    private DocumentBuilder(Locale locale) {
        this.l = new Labels(locale);
    }

    public static List<Doc.Block> build(ScaInventory inventory, Locale locale) {
        DocumentBuilder b = new DocumentBuilder(locale);
        b.inventory(inventory);
        return List.copyOf(b.out);
    }

    // ------------------------------------------------------------------------------------------ top level

    private void inventory(ScaInventory inv) {
        ScaInventory.SoftwareComponent sc = inv.source().softwareComponent();
        heading(1, l.get("title", sc.name()));
        bullets(
                line(new Doc.Text(l.get("sca.component") + ": "), new Doc.Code(sc.vendor() + "/" + sc.name()),
                        new Doc.Text(" " + l.get("sca.version", nz(sc.release()), nz(sc.servicelevel()),
                                nz(sc.patchlevel()), nz(sc.updateVersion())))),
                line(new Doc.Text(l.get("sca.file") + ": "), new Doc.Code(inv.source().file()),
                        new Doc.Text(" (SHA-256 " + inv.source().sha256().substring(0, 12) + "…)")),
                text(l.get("sca.generated", inv.generator().name(), inv.generator().version(),
                        inv.generator().readAt())));

        heading(2, l.get("overview"));
        long processes = inv.bpm().stream().mapToLong(b -> b.processes().size()).sum();
        Map<String, Long> bySeverity = inv.issues().stream()
                .collect(Collectors.groupingBy(Issue::severity, LinkedHashMap::new, Collectors.counting()));
        bullets(text(l.get("overview.counts", inv.developmentComponents().size(), processes, inv.ejb().size(),
                bySeverity.getOrDefault(Issue.ERROR, 0L), bySeverity.getOrDefault(Issue.WARNING, 0L),
                bySeverity.getOrDefault(Issue.INFO, 0L))));
        Doc.TableBuilder dcs = new Doc.TableBuilder(l.get("col.dc"), l.get("col.type"), l.get("col.caption"),
                l.get("col.libraries"));
        for (DevelopmentComponent dc : inv.developmentComponents()) {
            dcs.row(code(dc.vendor() + "/" + dc.name()), text(dc.type()), text(dc.caption()),
                    code(String.join(", ", dc.libraries() == null ? List.of() : dc.libraries())));
        }
        table(dcs);

        for (BpmComponent bpm : inv.bpm()) {
            heading(2, l.get("bpm.title", bpm.dc()));
            for (Process p : bpm.processes()) {
                process(p, bpm);
            }
            catalog(bpm);
        }
        if (!inv.ejb().isEmpty()) {
            heading(2, l.get("ejb.title"));
            for (EjbModule m : inv.ejb()) {
                ejbModule(m);
            }
        }
        issues(inv.issues());
        dataTypes(inv.bpm());
    }

    // ------------------------------------------------------------------------------------------ process

    private void process(Process p, BpmComponent bpm) {
        heading(3, l.get("process.title", p.name()));
        paragraph(p.documentation());
        List<List<Doc.Inline>> facts = new ArrayList<>();
        if (p.pool() != null) {
            facts.add(line(new Doc.Text(l.get("process.identifier") + ": "), new Doc.Code(p.pool().identifier())));
            if (p.pool().subject() != null || p.pool().description() != null) {
                facts.add(text(l.get("process.subject") + ": " + nz(p.pool().subject())
                        + (p.pool().description() == null ? "" : " — " + p.pool().description())));
            }
        }
        facts.add(line(new Doc.Text(l.get("source") + ": "), new Doc.Code(p.source())));
        bulletList(facts);

        Map<String, Node> nodes = new LinkedHashMap<>();
        p.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<String, List<Flow>> outgoing = new LinkedHashMap<>();
        p.flows().forEach(f -> outgoing.computeIfAbsent(ref(f.source()), k -> new ArrayList<>()).add(f));

        heading(4, l.get("process.flow"));
        out.add(new Doc.CodeBlock("mermaid", mermaid(p, nodes)));
        Doc.TableBuilder flows = new Doc.TableBuilder(l.get("col.from"), l.get("col.to"), l.get("col.condition"));
        for (Node n : stepOrder(p, nodes, outgoing)) {
            for (Flow f : outgoing.getOrDefault(n.id(), List.of())) {
                flows.row(text(name(f.source())), text(name(f.target())), condition(f));
            }
        }
        table(flows);

        heading(4, l.get("process.steps"));
        for (Node n : stepOrder(p, nodes, outgoing)) {
            if (!"boundaryEvent".equals(n.kind())) {
                step(n, p, bpm, nodes, outgoing);
            }
        }

        if (!p.dataObjects().isEmpty()) {
            heading(4, l.get("process.dataObjects"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.name"), l.get("col.type"), l.get("col.description"));
            for (Process.DataObject d : p.dataObjects()) {
                t.row(code(d.name()), code(d.type() == null ? null : d.type().name()), text(d.documentation()));
            }
            table(t);
        }
        List<Process.Role> roles = p.roles().stream().filter(r -> "process".equals(r.kind())).toList();
        if (!roles.isEmpty()) {
            heading(4, l.get("process.roles"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.name"), l.get("col.description"),
                    l.get("col.assignment"));
            for (Process.Role r : roles) {
                t.row(text(r.name()), text(r.documentation()), code(assignmentSummary(r.assignment())));
            }
            table(t);
        }
        if (!p.annotations().isEmpty()) {
            heading(4, l.get("process.annotations"));
            List<List<Doc.Inline>> items = new ArrayList<>();
            for (Process.Annotation a : p.annotations()) {
                String target = a.attachedTo().stream().map(Ref::name).collect(Collectors.joining(", "));
                items.add(text((target.isEmpty() ? "" : target + ": ") + nz(a.text())));
            }
            bulletList(items);
        }
    }

    /** Nodes in flow order: breadth-first from start events (boundary event targets follow their activity). */
    private static List<Node> stepOrder(Process p, Map<String, Node> nodes, Map<String, List<Flow>> outgoing) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        p.nodes().stream().filter(n -> "startEvent".equals(n.kind())).forEach(n -> queue.add(n.id()));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!nodes.containsKey(id) || !seen.add(id)) {
                continue;
            }
            Node n = nodes.get(id);
            List<String> next = new ArrayList<>();
            if (n.boundaryEvents() != null) {
                n.boundaryEvents().forEach(b -> next.add(b.ref()));
            }
            outgoing.getOrDefault(id, List.of()).forEach(f -> next.add(ref(f.target())));
            // regular successors first, then boundary events
            next.sort((a, b) -> Boolean.compare(isBoundary(nodes, a), isBoundary(nodes, b)));
            queue.addAll(next);
        }
        List<Node> ordered = new ArrayList<>();
        seen.forEach(id -> ordered.add(nodes.get(id)));
        p.nodes().stream().filter(n -> !seen.contains(n.id())).forEach(ordered::add);
        return ordered;
    }

    private static boolean isBoundary(Map<String, Node> nodes, String id) {
        Node n = nodes.get(id);
        return n != null && "boundaryEvent".equals(n.kind());
    }

    private void step(Node n, Process p, BpmComponent bpm, Map<String, Node> nodes, Map<String, List<Flow>> outgoing) {
        heading(5, n.name() == null ? l.get("kind." + n.kind()) : n.name() + " (" + l.get("kind." + n.kind()) + ")");
        paragraph(n.documentation());
        List<List<Doc.Inline>> facts = new ArrayList<>();
        if (n.scope() != null && p.pool() != null && !p.pool().id().equals(n.scope().ref())) {
            facts.add(text(l.get("step.scope") + ": " + nz(n.scope().name())));
        }
        if (n.trigger() != null) {
            facts.add(text(l.get("step.trigger") + ": " + trigger(n.trigger())));
            String iface = messageInterface(n.trigger(), bpm);
            if (iface != null) {
                facts.add(line(new Doc.Text(l.get("step.interface") + ": "), new Doc.Code(iface)));
            }
        }
        if (n.condition() != null) {
            facts.add(line(new Doc.Text(l.get("col.condition") + ": "), new Doc.Code(n.condition().text())));
        }
        if (n.operation() != null) {
            Node.InterfaceRef i = n.operation().iface();
            boolean sameName = i == null || i.name().equals(n.operation().name());
            facts.add(line(new Doc.Text(l.get("step.operation") + ": "),
                    new Doc.Code(sameName ? n.operation().name() : i.name() + " / " + n.operation().name()),
                    new Doc.Text(i == null ? "" : " (" + i.namespace() + ")")));
        }
        if (n.serviceReference() != null) {
            ServiceReference sr = bpm.catalog().serviceReferences().stream()
                    .filter(s -> s.id().equals(n.serviceReference().ref())).findFirst().orElse(null);
            facts.add(text(l.get("step.serviceReference") + ": " + (sr == null ? nz(n.serviceReference().name())
                    : l.get("step.serviceReference.value", nz(sr.bindingType()), nz(sr.serviceGroup()),
                    nz(sr.authenticationProfile())))));
        }
        if (n.subject() != null) {
            facts.add(text(l.get("step.subject") + ": " + n.subject()));
        }
        if (n.body() != null) {
            facts.add(text(l.get("step.body") + ": " + n.body()));
        }
        if (Boolean.TRUE.equals(n.terminate())) {
            facts.add(text(l.get("step.terminate")));
        }
        if (n.throwsEvent() != null) {
            facts.add(text(l.get("step.throws") + ": " + nz(n.throwsEvent().name())));
        }
        List<Flow> next = outgoing.getOrDefault(n.id(), List.of());
        if (!"exclusiveGateway".equals(n.kind()) && !next.isEmpty()) {
            facts.add(text(l.get("step.next") + ": "
                    + next.stream().map(f -> name(f.target())).collect(Collectors.joining(", "))));
        }
        if (!facts.isEmpty()) {
            bulletList(facts);
        }
        if ("exclusiveGateway".equals(n.kind())) {
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.to"), l.get("col.condition"));
            for (Flow f : next) {
                t.row(text(name(f.target())), condition(f));
            }
            table(t);
        }
        mapping(l.get("step.inputMapping"), n.inputMapping());
        mapping(l.get("step.outputMapping"), n.outputMapping());
        if (n.errorEvents() != null && !n.errorEvents().isEmpty()) {
            bullets(text(l.get("step.faults") + ": " + n.errorEvents().stream()
                    .map(e -> e.fault() == null ? e.name() : e.fault().name()).distinct()
                    .collect(Collectors.joining(", "))));
        }
        if (n.boundaryEvents() != null && !n.boundaryEvents().isEmpty()) {
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.boundaryEvent"), l.get("step.trigger"),
                    l.get("step.next"));
            List<Node> withMapping = new ArrayList<>();
            for (Ref b : n.boundaryEvents()) {
                Node be = nodes.get(b.ref());
                if (be == null) {
                    continue;
                }
                t.row(text(be.name()), text(be.trigger() == null ? null : trigger(be.trigger())),
                        text(outgoing.getOrDefault(be.id(), List.of()).stream().map(f -> name(f.target()))
                                .collect(Collectors.joining(", "))));
                if (be.outputMapping() != null) {
                    withMapping.add(be);
                }
            }
            table(t);
            for (Node be : withMapping) {
                mapping(l.get("step.boundaryMapping", be.name()), be.outputMapping());
            }
        }
    }

    private String trigger(Node.Trigger t) {
        String name = t.eventDefinition() == null ? null : t.eventDefinition().name();
        String s = l.get("trigger." + t.kind()) + (name == null ? "" : " " + name);
        return t.builtin() != null ? s + " (" + l.get("trigger.standard") + ")" : s;
    }

    /** Interface of a message trigger (start event), via the DC's event definitions. */
    private static String messageInterface(Node.Trigger t, BpmComponent bpm) {
        if (!"message".equals(t.kind()) || t.eventDefinition() == null) {
            return null;
        }
        return bpm.catalog().eventDefinitions().stream()
                .filter(d -> d.id().equals(t.eventDefinition().ref()) && d.operation() != null)
                .map(d -> d.operation().iface() == null ? d.operation().name()
                        : d.operation().iface().name() + " / " + d.operation().name())
                .findFirst().orElse(null);
    }

    private List<Doc.Inline> condition(Flow f) {
        List<Doc.Inline> cell = new ArrayList<>();
        if (f.name() != null) {
            cell.add(new Doc.Strong(f.name()));
            cell.add(new Doc.Text(" "));
        }
        if (Boolean.TRUE.equals(f.isDefault())) {
            cell.add(new Doc.Text(l.get("flow.default")));
        } else if (f.condition() != null) {
            cell.add(new Doc.Code(f.condition().text()));
        }
        return cell;
    }

    /** Mapping table: target, source expression, explicit part flags (moveCorresponding explained). */
    private void mapping(String title, Mapping m) {
        if (m == null) {
            return;
        }
        List<Mapping.Assignment> rows = m.assignments().stream().filter(a -> a.expr() != null).toList();
        if (rows.isEmpty()) {
            return;
        }
        paragraph(List.of(new Doc.Strong(title)));
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.target"), l.get("col.source"), l.get("col.mode"));
        for (Mapping.Assignment a : rows) {
            t.row(code(a.target()), code(a.expr().text()), mode(a));
        }
        table(t);
    }

    private List<Doc.Inline> mode(Mapping.Assignment a) {
        List<Doc.Inline> cell = new ArrayList<>();
        cell.add(new Doc.Text(nz(a.category())));
        if ("moveCorresponding".equals(a.expander())) {
            cell.add(new Doc.Text(", "));
            cell.add(new Doc.Strong("moveCorresponding"));
            cell.add(new Doc.Text(" (" + l.get("mapping.moveCorresponding") + ")"));
        }
        if ("discardValue".equals(a.transformer())) {
            cell.add(new Doc.Text(", discardValue"));
        }
        if (Boolean.FALSE.equals(a.enabled())) {
            cell.add(new Doc.Text(", " + l.get("mapping.disabled")));
        }
        // flags of function parameter mapping parts stay in the JSON (expr.functions[].arguments): repeating e.g.
        // "get.list: moveCorresponding" on every row would only add noise to the document
        return cell;
    }

    private static String assignmentSummary(Mapping m) {
        if (m == null) {
            return null;
        }
        return m.assignments().stream().filter(a -> a.expr() != null && !Boolean.TRUE.equals(a.expr().generated())
                        && !Boolean.FALSE.equals(a.enabled()))
                .map(a -> a.expr().text()).distinct().collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------------------------------------ mermaid

    private String mermaid(Process p, Map<String, Node> nodes) {
        Map<String, String> ids = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (Node n : p.nodes()) {
            String id = "n" + ids.size();
            ids.put(n.id(), id);
            String label = mermaidText(n.name() == null ? l.get("kind." + n.kind()) : n.name());
            String shape = switch (n.kind()) {
                case "startEvent" -> "((\"" + label + "\"))";
                case "endEvent" -> Boolean.TRUE.equals(n.terminate()) ? "(((\"" + label + "\")))" : "((\"" + label + "\"))";
                case "exclusiveGateway", "joinGateway" -> "{\"" + label + "\"}";
                case "boundaryEvent" -> "([\"" + label + "\"])";
                default -> "[\"" + label + "\"]";
            };
            sb.append("  ").append(id).append(shape).append('\n');
        }
        for (Node n : p.nodes()) {
            if (n.boundaryEvents() != null) {
                for (Ref b : n.boundaryEvents()) {
                    if (ids.containsKey(b.ref())) {
                        sb.append("  ").append(ids.get(n.id())).append(" -.- ").append(ids.get(b.ref())).append('\n');
                    }
                }
            }
        }
        for (Flow f : p.flows()) {
            String from = ids.get(ref(f.source()));
            String to = ids.get(ref(f.target()));
            if (from == null || to == null) {
                continue;
            }
            String label = f.name() != null ? f.name()
                    : Boolean.TRUE.equals(f.isDefault()) ? l.get("flow.default")
                    : f.condition() != null ? abbreviate(f.condition().text()) : null;
            sb.append("  ").append(from).append(" -->");
            if (label != null && !label.isBlank()) {
                sb.append("|\"").append(mermaidText(label)).append("\"|");
            }
            sb.append(' ').append(to).append('\n');
        }
        return sb.toString();
    }

    private static String mermaidText(String s) {
        return s.replaceAll("\\s+", " ").strip().replace("\"", "#quot;").replace("|", "#124;");
    }

    private static String abbreviate(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= EDGE_LABEL_MAX ? one : one.substring(0, EDGE_LABEL_MAX - 1) + "…";
    }

    // ------------------------------------------------------------------------------------------ catalog

    private void catalog(BpmComponent bpm) {
        BpmComponent.Catalog c = bpm.catalog();
        heading(3, l.get("catalog.title", bpm.dc()));

        heading(4, l.get("catalog.interfaces"));
        Doc.TableBuilder it = new Doc.TableBuilder(l.get("col.interface"), l.get("col.pattern"), l.get("col.input"),
                l.get("col.output"), l.get("col.faults"), l.get("col.usedBy"));
        for (Interface i : c.interfaces()) {
            for (Interface.Operation op : i.operations()) {
                it.row(code(i.name() + (op.name().equals(i.name()) ? "" : " / " + op.name())), text(op.pattern()),
                        code(qname(op.input())), code(qname(op.output())),
                        code(op.faults().stream().map(Interface.Fault::name).distinct().collect(Collectors.joining(", "))),
                        text(i.usedBy().stream().map(u -> u.process() + ": " + u.node().name() + " (" + u.role() + ")")
                                .collect(Collectors.joining("; "))));
            }
        }
        table(it);

        heading(4, l.get("catalog.serviceReferences"));
        Doc.TableBuilder st = new Doc.TableBuilder(l.get("col.interface"), l.get("col.binding"),
                l.get("col.serviceGroup"), l.get("col.authProfile"));
        for (ServiceReference sr : c.serviceReferences()) {
            st.row(code(sr.iface() == null ? sr.uuid() : sr.iface().name()), text(sr.bindingType()),
                    text(sr.serviceGroup()), text(sr.authenticationProfile()));
        }
        table(st);

        if (!c.functions().isEmpty()) {
            heading(4, l.get("catalog.functions"));
            Doc.TableBuilder ft = new Doc.TableBuilder(l.get("col.function"), l.get("col.parameters"),
                    l.get("col.result"), l.get("col.implementation"));
            for (Function f : c.functions()) {
                ft.row(code(f.name()), code(params(f.parameters())),
                        code(f.result() == null ? null : f.result().type()),
                        code(f.ejb() == null ? f.lookupName() : f.ejb().bean() + " @ " + f.ejb().application()));
            }
            table(ft);
        }
        if (!c.builtinFunctions().isEmpty()) {
            heading(4, l.get("catalog.builtinFunctions"));
            Doc.TableBuilder bt = new Doc.TableBuilder(l.get("col.function"), l.get("col.category"),
                    l.get("col.symbol"), l.get("col.parameters"), l.get("col.result"), l.get("col.semantics"));
            for (BuiltinFunction f : c.builtinFunctions()) {
                bt.row(code(f.name()), text(f.category()), code(f.symbol()), code(params(f.parameters())),
                        code(f.result()), text(f.documentation()));
            }
            table(bt);
        }
        List<EventDefinition> events = new ArrayList<>(c.eventDefinitions());
        events.addAll(c.builtinEventDefinitions());
        if (!events.isEmpty()) {
            heading(4, l.get("catalog.eventDefinitions"));
            Doc.TableBuilder et = new Doc.TableBuilder(l.get("col.name"), l.get("col.kind"), l.get("col.critical"),
                    l.get("col.description"));
            for (EventDefinition e : events) {
                boolean standard = c.builtinEventDefinitions().contains(e);
                et.row(code(e.name()), text(l.get("trigger." + e.kind()) + (standard ? " (" + l.get("trigger.standard")
                                + ")" : "")),
                        text(Boolean.TRUE.equals(e.isCritical()) ? l.get("yes") : l.get("no")), text(e.documentation()));
            }
            table(et);
        }
    }

    private static String params(List<Function.Parameter> ps) {
        return ps == null ? null : ps.stream().map(p -> p.name() + ": " + nz(p.type())).collect(Collectors.joining(", "));
    }

    private static String qname(QNameRef q) {
        return q == null ? null : q.name();
    }

    // ------------------------------------------------------------------------------------------ EJB

    private void ejbModule(EjbModule m) {
        heading(3, l.get("ejb.module", m.dc()));
        List<List<Doc.Inline>> facts = new ArrayList<>();
        if (m.ear() != null) {
            facts.add(line(new Doc.Text(l.get("ejb.ear") + ": "), new Doc.Code(m.ear()),
                    new Doc.Text(" (" + l.get("ejb.application") + " "), new Doc.Code(m.application()),
                    new Doc.Text(")")));
        }
        if (!m.technologies().isEmpty()) {
            facts.add(text(l.get("ejb.technologies") + ": " + String.join(", ", m.technologies())));
        }
        if (!facts.isEmpty()) {
            bulletList(facts);
        }
        Doc.TableBuilder bt = new Doc.TableBuilder(l.get("col.bean"), l.get("col.class"), l.get("col.type"),
                l.get("col.roles"), l.get("col.details"));
        for (EjbModule.Bean b : m.beans()) {
            bt.row(code(b.name()), code(b.className()), text(b.type()),
                    text(b.roles().stream().map(r -> l.get("role." + r)).collect(Collectors.joining(", "))),
                    text(beanDetails(b)));
        }
        table(bt);
        if (!m.jpaEntities().isEmpty()) {
            Doc.TableBuilder jt = new Doc.TableBuilder(l.get("col.entity"), l.get("col.table"));
            m.jpaEntities().forEach(e -> jt.row(code(e.className()), code(e.table())));
            paragraph(List.of(new Doc.Strong(l.get("ejb.jpa"))));
            table(jt);
        }
        for (EjbModule.PersistenceUnit u : m.persistenceUnits()) {
            bullets(text(l.get("ejb.persistenceUnit", u.name(), nz(u.dataSource()), u.classes().size())));
        }
        if (!m.classes().isEmpty()) {
            bullets(text(l.get("ejb.classes", m.classes().size())));
        }
    }

    private String beanDetails(EjbModule.Bean b) {
        List<String> d = new ArrayList<>();
        if (b.jndiName() != null) {
            d.add("JNDI " + b.jndiName());
        }
        if (b.moduleParameters() != null && !b.moduleParameters().isEmpty()) {
            d.add(l.get("bean.moduleParameters") + ": " + String.join(", ", b.moduleParameters()));
        }
        if (b.webService() != null) {
            d.add(l.get("bean.webService") + ": " + nz(b.webService().serviceName()) + " ("
                    + nz(b.webService().endpointInterface()) + ")");
        }
        if (b.job() != null) {
            d.add(l.get("bean.job") + ": " + b.job().name()
                    + (b.job().description() == null ? "" : " — " + b.job().description()));
        }
        if (b.rfcFunctions() != null && !b.rfcFunctions().isEmpty()) {
            d.add("RFC: " + String.join(", ", b.rfcFunctions()) + " @ " + String.join(", ", b.jcoDestinations()));
        }
        if (b.usedBy() != null && !b.usedBy().isEmpty()) {
            d.add(l.get("bean.usedBy") + ": " + b.usedBy().stream().map(u -> u.function().name())
                    .collect(Collectors.joining(", ")));
        }
        if (b.injects() != null && !b.injects().isEmpty()) {
            d.add(l.get("bean.injects") + ": " + String.join(", ", b.injects()));
        }
        if (b.persistenceUnit() != null) {
            d.add("JPA " + b.persistenceUnit());
        }
        return String.join("; ", d);
    }

    // ------------------------------------------------------------------------------------------ issues, types

    private void issues(List<Issue> issues) {
        heading(2, l.get("issues.title"));
        if (issues.isEmpty()) {
            paragraph(l.get("issues.none"));
            return;
        }
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.severity"), l.get("col.code"), l.get("col.description"),
                l.get("col.location"));
        List<Issue> sorted = new ArrayList<>(issues);
        sorted.sort((a, b) -> Integer.compare(severityRank(a.severity()), severityRank(b.severity())));
        for (Issue i : sorted) {
            t.row(text(l.get("severity." + i.severity())), code(i.code()), text(i.message()), text(location(i)));
        }
        table(t);
    }

    private static int severityRank(String s) {
        return switch (s) {
            case Issue.ERROR -> 0;
            case Issue.WARNING -> 1;
            default -> 2;
        };
    }

    private static String location(Issue i) {
        if (i.location() == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (i.location().process() != null) {
            parts.add(i.location().process());
        }
        if (i.location().element() != null && i.location().element().name() != null) {
            parts.add(i.location().element().name());
        }
        if (i.location().path() != null) {
            parts.add(i.location().path());
        }
        if (parts.isEmpty() && i.location().dc() != null) {
            parts.add(i.location().dc());
        }
        return String.join(" / ", parts);
    }

    private void dataTypes(List<BpmComponent> bpm) {
        List<DataType> types = bpm.stream().flatMap(b -> b.catalog().dataTypes().stream())
                .filter(t -> !t.fields().isEmpty()).toList();
        if (types.isEmpty()) {
            return;
        }
        heading(2, l.get("types.title"));
        for (DataType t : types) {
            heading(3, t.name() + (t.type() == null ? "" : " (" + t.type() + ")"));
            paragraph(List.of(new Doc.Code(nz(t.namespace()))));
            Doc.TableBuilder tb = new Doc.TableBuilder(l.get("col.field"), l.get("col.type"), l.get("col.cardinality"));
            for (DataType.Field f : t.fields()) {
                tb.row(code(f.path()), code(f.type()),
                        text((f.min() == null ? "1" : f.min().toString()) + ".." + (f.max() == null ? "1" : f.max())));
            }
            table(tb);
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private void heading(int level, String text) {
        out.add(new Doc.Heading(level, text));
    }

    private void paragraph(String text) {
        if (text != null && !text.isBlank()) {
            out.add(new Doc.Paragraph(List.of(new Doc.Text(text))));
        }
    }

    private void paragraph(List<Doc.Inline> content) {
        out.add(new Doc.Paragraph(content));
    }

    @SafeVarargs
    private void bullets(List<Doc.Inline>... items) {
        List<List<Doc.Inline>> list = new ArrayList<>(items.length);
        for (List<Doc.Inline> item : items) {
            list.add(item);
        }
        bulletList(list);
    }

    private void bulletList(List<List<Doc.Inline>> items) {
        out.add(new Doc.Bullets(List.copyOf(items)));
    }

    private void table(Doc.TableBuilder t) {
        if (!t.isEmpty()) {
            out.add(t.build());
        }
    }

    private static String ref(Ref r) {
        return r == null ? null : r.ref();
    }

    private static String name(Ref r) {
        return r == null ? "?" : r.name() != null ? r.name() : r.ref();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
