package io.poxray.bpm;

import io.poxray.model.EventDefinition;
import io.poxray.model.Function;
import io.poxray.model.Interface;
import io.poxray.model.Node;
import io.poxray.model.QNameRef;
import io.poxray.model.ServiceReference;
import io.poxray.moin.MoinElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads DC-level catalog objects (interfaces, service references, functions, event definitions). */
final class CatalogReader {

    private static final Pattern LOOKUP_NAME = Pattern.compile("^(.*)/(?:LOCAL|REMOTE)/([^/]+)/(.+)$");
    static final String TRIGGERS_DIR = "src/triggers/";

    private final BpmContext ctx;
    private final XsdTypes xsd;

    CatalogReader(BpmContext ctx, XsdTypes xsd) {
        this.ctx = ctx;
        this.xsd = xsd;
    }

    // ---------------------------------------------------------------- references used by processes

    Node.OperationRef operationRef(String operationId) {
        MoinElement op = operationId == null ? null : ctx.index.get(operationId).orElse(null);
        if (op == null) {
            return operationId == null ? null : new Node.OperationRef(operationId, null, null);
        }
        Node.InterfaceRef iface = ctx.index.secondElement(Galaxy.INTERFACE_OPERATIONS, op.id())
                .map(i -> new Node.InterfaceRef(i.id(), i.attr("name"), i.attr("namespace")))
                .orElse(null);
        return new Node.OperationRef(op.id(), op.name(), iface);
    }

    /** Operation of a message event definition (association direction not verified, both ends tried). */
    String messageEventOperation(String eventDefinitionId) {
        return ctx.index.first(Galaxy.MESSAGE_EVENT_OPERATION, eventDefinitionId)
                .or(() -> ctx.index.second(Galaxy.MESSAGE_EVENT_OPERATION, eventDefinitionId))
                .orElse(null);
    }

    QNameRef elementName(MoinElement xsdElement) {
        return xsdElement == null ? null : new QNameRef(xsdElement.attr("name"), xsdElement.attr("namespace"));
    }

    // ---------------------------------------------------------------- catalog

    List<Interface> interfaces(Map<String, List<Interface.Usage>> usages) {
        List<Interface> out = new ArrayList<>();
        for (MoinElement i : ctx.index.elementsOf(Galaxy.WSDL_INTERFACE)) {
            List<Interface.Operation> ops = new ArrayList<>();
            for (MoinElement op : ctx.index.firstElements(Galaxy.INTERFACE_OPERATIONS, i.id())) {
                ops.add(operation(op));
            }
            out.add(new Interface(i.id(), i.attr("name"), i.attr("namespace"), ctx.source(i.partition()),
                    List.copyOf(ops), usages.getOrDefault(i.id(), List.of())));
        }
        return out;
    }

    private Interface.Operation operation(MoinElement op) {
        String pattern = ctx.index.firstElement(Galaxy.OPERATION_PATTERN, op.id())
                .map(p -> p.attr("kind"))
                .map(k -> k.toLowerCase(Locale.ROOT).replace('_', '-'))
                .orElse(null);
        QNameRef input = null;
        QNameRef output = null;
        for (MoinElement mr : ctx.index.firstElements(Galaxy.OPERATION_MESSAGES, op.id())) {
            QNameRef element = elementName(ctx.index.secondElement(Galaxy.REFERENCE_TO_ELEMENT, mr.id()).orElse(null));
            if ("IN".equals(mr.attr("direction"))) {
                input = element;
            } else if ("OUT".equals(mr.attr("direction"))) {
                output = element;
            }
        }
        List<Interface.Fault> faults = new ArrayList<>();
        for (MoinElement fr : ctx.index.firstElements(Galaxy.OPERATION_FAULTS, op.id())) {
            ctx.index.firstElement(Galaxy.REFERENCE_TO_INTERFACE_FAULT, fr.id()).ifPresent(f -> faults.add(
                    new Interface.Fault(f.id(), f.attr("name"),
                            elementName(ctx.index.secondElement(Galaxy.REFERENCE_TO_ELEMENT, f.id()).orElse(null)))));
        }
        return new Interface.Operation(op.id(), op.name(), pattern, input, output, List.copyOf(faults));
    }

    List<ServiceReference> serviceReferences(Map<String, Composite.Reference> composite) {
        List<ServiceReference> out = new ArrayList<>();
        for (MoinElement sr : ctx.index.elementsOf(Galaxy.SERVICE_REFERENCE)) {
            QNameRef iface = ctx.index.firstElement(Galaxy.SERVICE_REFERENCE_QNAME, sr.id())
                    .map(q -> new QNameRef(q.attr("name"), q.attr("namespace"))).orElse(null);
            String auth = ctx.index.firstElement(Galaxy.SERVICE_REFERENCE_AUTH_PROFILE, sr.id())
                    .map(MoinElement::name).orElse(null);
            String lsId = ctx.index.first(Galaxy.SERVICE_REFERENCE_LOGICAL_SYSTEM, sr.id()).orElse(null);
            String logicalSystem = ctx.index.get(lsId).map(MoinElement::name).orElse(null);
            if (lsId != null && logicalSystem == null) {
                ctx.issues.info("UNRESOLVED_REF",
                        "Service reference " + sr.attr("id") + " (" + (iface == null ? "?" : iface.name())
                                + ") points to logical system " + lsId + " which is not in the DC (SAP built-in?)",
                        ctx.at(null, sr, null), ctx.source(sr.partition()));
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            for (MoinElement a : ctx.index.firstElements(Galaxy.SERVICE_REFERENCE_ATTRIBUTES, sr.id())) {
                attrs.put(a.attr("key"), a.attr("value"));
            }
            Composite.Reference c = composite.get(sr.attr("id"));
            out.add(new ServiceReference(sr.id(), sr.attr("id"), sr.attr("type"), sr.attr("bindingType"), iface,
                    auth != null ? auth : c == null ? null : c.authenticationProfile(),
                    logicalSystem, c == null ? null : c.serviceGroup(), attrs));
        }
        return out;
    }

    List<Function> functions() {
        List<Function> out = new ArrayList<>();
        for (MoinElement fi : ctx.index.elementsOf(Galaxy.FUNCTION_INTERFACE)) {
            MoinElement impl = ctx.index.firstElements(Galaxy.FUNCTION_IMPLEMENTATIONS, fi.id()).stream()
                    .findFirst().orElse(null);
            String lookupName = impl == null ? null : impl.attr("lookupName");
            Function.EjbLink ejb = null;
            if (lookupName != null) {
                Matcher m = LOOKUP_NAME.matcher(lookupName);
                if (m.matches()) {
                    ejb = new Function.EjbLink(m.group(1), m.group(2), m.group(3));
                }
            }
            List<Function.Parameter> params = new ArrayList<>();
            for (MoinElement p : ctx.index.firstElements(Galaxy.FUNCTION_PARAMETER, fi.id())) {
                params.add(new Function.Parameter(p.attr("name"), xsd.typeOfElement(p)));
            }
            Function.Parameter result = ctx.index.firstElement(Galaxy.FUNCTION_RESULT, fi.id())
                    .map(r -> new Function.Parameter(r.attr("name"), xsd.typeOfElement(r))).orElse(null);
            String kind = impl == null ? null
                    : impl.is(Galaxy.EJB_FUNCTION) ? "ejb" : impl.simpleType();
            out.add(new Function(fi.id(), fi.name(), fi.attr("namespace"), kind, lookupName, ejb,
                    List.copyOf(params), result, ctx.source(fi.partition())));
        }
        return out;
    }

    List<EventDefinition> eventDefinitions() {
        List<EventDefinition> out = new ArrayList<>();
        List<MoinElement> defs = new ArrayList<>(ctx.index.elementsOf(Galaxy.MESSAGE_EVENT_DEFINITION));
        defs.addAll(ctx.index.elementsOf(Galaxy.ERROR_EVENT_DEFINITION));
        for (MoinElement d : defs) {
            if (!d.partition().startsWith(TRIGGERS_DIR)) {
                continue; // error event definitions of activities (WSDL faults) live in the process partition
            }
            boolean message = d.is(Galaxy.MESSAGE_EVENT_DEFINITION);
            QNameRef parameter = elementName(
                    ctx.index.firstElement(Galaxy.EVENT_DEFINITION_PARAMETER, d.id()).orElse(null));
            out.add(new EventDefinition(d.id(), d.name(), message ? "message" : "error",
                    emptyToNull(d.text("documentation")),
                    "true".equals(d.attr("isCritical")) ? Boolean.TRUE : null,
                    "true".equals(d.attr("isTechnical")) ? Boolean.TRUE : null,
                    parameter,
                    message ? operationRef(messageEventOperation(d.id())) : null,
                    ctx.source(d.partition())));
        }
        return out;
    }

    static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
