package io.poxray.bpm;

import io.poxray.IssueLog;
import io.poxray.model.BpmComponent;
import io.poxray.model.BuiltinFunction;
import io.poxray.model.DataType;
import io.poxray.model.EventDefinition;
import io.poxray.model.Function;
import io.poxray.model.Interface;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.ServiceReference;
import io.poxray.model.UnmappedElement;
import io.poxray.moin.MoinElement;
import io.poxray.moin.MoinIndex;
import io.poxray.moin.MoinParser;
import io.poxray.moin.MoinPartition;
import io.poxray.sca.ScaArchive;
import io.poxray.sca.ZipArchive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a Process Composer DC (NW BPM, Galaxy model) into {@link BpmComponent}.
 * All MOIN partitions of the DC source archive are indexed together, because processes reference
 * WSDL/XSD, functions, triggers and service references in other partitions ({@code local:} refs).
 */
public final class BpmReader {

    static final String PROCESS_DIR = "src/bpmn/";

    private BpmReader() {
    }

    /** True if the DC is a Process Composer DC. */
    public static boolean isBpm(ScaArchive.DcSource dc) {
        return io.poxray.sca.DcDefinition.PROCESS_COMPOSER.equals(dc.definition().type());
    }

    public static BpmComponent read(ScaArchive sca, ScaArchive.DcSource dc, IssueLog issues,
            List<UnmappedElement> unmapped) {
        Session s = new Session(dc, issues);
        BpmContext ctx = s.ctx;
        MoinIndex index = ctx.index;
        ExpressionReader expressions = s.expressions;
        MappingReader mappings = new MappingReader(ctx, expressions);
        XsdTypes xsd = new XsdTypes(ctx);
        CatalogReader catalog = new CatalogReader(ctx, xsd);
        ProcessReader.Usages usages = new ProcessReader.Usages();
        ProcessReader processReader = new ProcessReader(ctx, expressions, mappings, catalog, usages);

        List<Process> processes = new ArrayList<>();
        index.partitions().stream()
                .filter(p -> p.path().startsWith(PROCESS_DIR) && p.path().endsWith(".bpmn"))
                .sorted(Comparator.comparing(MoinPartition::path))
                .forEach(p -> {
                    Process process = processReader.read(p);
                    if (process != null) {
                        processes.add(process);
                    }
                });
        unmapped.addAll(usages.unmapped);

        Composite.Content composite = Composite.read(dc.deployEntry() == null ? null
                : sca.zip().get(dc.deployEntry()).map(b -> ZipArchive.of(b, dc.deployEntry())).orElse(null));

        List<Interface> interfaces = catalog.interfaces(usages.interfaces);
        List<Function> functions = catalog.functions();
        List<EventDefinition> eventDefinitions = catalog.eventDefinitions();
        List<DataType> dataTypes = new ArrayList<>();
        for (String id : usages.dataTypes) {
            index.get(id).ifPresent(e -> dataTypes.add(xsd.dataType(e)));
        }
        List<ServiceReference> serviceReferences = catalog.serviceReferences(composite);
        reportUnused(ctx, interfaces, serviceReferences, functions, eventDefinitions, usages);

        return new BpmComponent(ctx.dc, processes, new BpmComponent.Catalog(interfaces, serviceReferences,
                functions, builtinFunctions(usages), eventDefinitions, builtinEventDefinitions(usages), dataTypes));
    }

    /** SAP built-in functions called by the DC's processes, with their catalog semantics, ordered by name. */
    private static List<BuiltinFunction> builtinFunctions(ProcessReader.Usages usages) {
        BuiltinFunctionCatalog catalog = BuiltinFunctionCatalog.get();
        return usages.functions.stream()
                .map(catalog::function).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(BuiltinFunctionCatalog.Entry::name)
                        .thenComparing(BuiltinFunctionCatalog.Entry::category))
                .map(e -> new BuiltinFunction(e.id(), e.name(), e.category(), e.symbol(),
                        e.parameters().stream()
                                .map(p -> new Function.Parameter(p.name(), catalog.qualify(p.type())))
                                .toList(),
                        catalog.qualify(e.result()),
                        e.documentation(), e.javaClass()))
                .toList();
    }

    /** Standard SAP triggers used by the DC's processes (e.g. TechnicalError), with SAP documentation. */
    private static List<EventDefinition> builtinEventDefinitions(ProcessReader.Usages usages) {
        BuiltinFunctionCatalog catalog = BuiltinFunctionCatalog.get();
        return usages.builtinTriggers.stream()
                .map(catalog::trigger).flatMap(java.util.Optional::stream)
                .map(t -> new EventDefinition(t.id(), t.name(), t.kind(), t.documentation(),
                        t.isCritical() ? Boolean.TRUE : null, t.isTechnical() ? Boolean.TRUE : null,
                        t.parameter() == null ? null : new QNameRef(t.parameter().name(), t.parameter().namespace()),
                        null, t.sourceDc() + "!/" + t.file()))
                .toList();
    }

    /**
     * Test seam: conditions of all conditional sequence connectors (connector ID → expression rendered from the
     * tree, notation adapted to the editor text).
     */
    static Map<String, String> renderConditions(ScaArchive.DcSource dc) {
        Session s = new Session(dc, new IssueLog());
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (MoinElement c : s.ctx.index.elementsOf(Galaxy.CONDITIONAL_SEQUENCE_CONNECTOR)) {
            s.ctx.index.first(Galaxy.CONNECTOR_EXPRESSION, c.id())
                    .ifPresent(x -> out.put(c.id(), s.expressions.renderTree(x)));
        }
        return out;
    }

    /** Index and expression reader of one DC. */
    private static final class Session {
        final BpmContext ctx;
        final ExpressionReader expressions;

        Session(ScaArchive.DcSource dc, IssueLog issues) {
            MoinIndex index = new MoinIndex();
            for (var e : dc.sources().entries().entrySet()) {
                // .diagram partitions hold layout only (not part of the output model, see docs/OUTPUT-MODEL.md)
                if (!e.getKey().endsWith(".diagram") && MoinParser.isPartition(e.getValue())) {
                    index.add(MoinParser.parse(e.getKey(), e.getValue()));
                }
            }
            ctx = new BpmContext(dc.definition().fullName(), dc.sourceEntry(), index, issues);
            for (String id : index.duplicateIds()) {
                issues.warn("DUPLICATE_ID", "MOF ID " + id + " is declared in more than one partition",
                        ctx.at(null, index.get(id).orElse(null), null), null);
            }
            BuiltinFunctionCatalog catalog = BuiltinFunctionCatalog.get();
            expressions = new ExpressionReader(ctx, new FunctionResolver(ctx, catalog), catalog);
        }
    }

    private static void reportUnused(BpmContext ctx, List<Interface> interfaces,
            List<ServiceReference> serviceReferences, List<Function> functions,
            List<EventDefinition> eventDefinitions, ProcessReader.Usages usages) {
        Set<QNameRef> triggerInterfaces = new HashSet<>();
        for (Interface i : interfaces) {
            if (i.usedBy().stream().anyMatch(u -> !"outbound".equals(u.role()))) {
                triggerInterfaces.add(new QNameRef(i.name(), i.namespace()));
            }
        }
        for (ServiceReference sr : serviceReferences) {
            // automated activities reference it directly; message triggers (start events) via the interface
            if (!usages.serviceReferences.contains(sr.id()) && !triggerInterfaces.contains(sr.iface())) {
                ctx.issues.info("UNUSED_OBJECT", "Service reference '" + (sr.iface() == null ? sr.uuid()
                        : sr.iface().name()) + "' is not used by any process",
                        ctx.at(null, element(ctx, sr.id()), null), null);
            }
        }
        for (Interface i : interfaces) {
            if (i.usedBy().isEmpty()) {
                ctx.issues.info("UNUSED_OBJECT", "Interface '" + i.name() + "' is not used by any process",
                        ctx.at(null, element(ctx, i.id()), null), i.source());
            }
        }
        for (Function f : functions) {
            if (!usages.functions.contains(f.id())) {
                ctx.issues.info("UNUSED_OBJECT", "Function '" + f.name() + "' is not called by any process",
                        ctx.at(null, element(ctx, f.id()), null), f.source());
            }
        }
        for (EventDefinition d : eventDefinitions) {
            if (!usages.eventDefinitions.contains(d.id())) {
                ctx.issues.info("UNUSED_OBJECT", "Event definition '" + d.name() + "' is not used by any process",
                        ctx.at(null, element(ctx, d.id()), null), d.source());
            }
        }
    }

    private static MoinElement element(BpmContext ctx, String id) {
        return ctx.index.get(id).orElse(null);
    }
}
