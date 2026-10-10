package io.poxray.bpm;

import io.poxray.model.Expression;
import io.poxray.model.Flow;
import io.poxray.model.Function;
import io.poxray.model.FunctionCall;
import io.poxray.model.Mapping;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.Ref;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the DC's custom functions are called: every expression of the processes (step input / output mappings and
 * conditions, flow conditions, role assignments, data object initial values) is scanned for calls of the function
 * (by MOF ID of the function interface).
 */
final class FunctionUsages {

    private final Map<String, List<Function.Usage>> byFunction = new LinkedHashMap<>();

    private FunctionUsages() {
    }

    /** The functions with {@code usedBy} filled from the processes. */
    static List<Function> attach(List<Function> functions, List<Process> processes) {
        FunctionUsages u = new FunctionUsages();
        for (Process p : processes) {
            u.process(p);
        }
        return functions.stream().map(f -> new Function(f.id(), f.name(), f.namespace(), f.kind(), f.lookupName(),
                f.ejb(), f.parameters(), f.result(), f.source(), List.copyOf(u.byFunction.getOrDefault(f.id(),
                        List.of())))).toList();
    }

    private void process(Process p) {
        for (Node n : p.nodes()) {
            Ref element = new Ref(n.id(), n.name());
            mapping(p, element, n.kind(), "inputMapping", n.inputMapping());
            mapping(p, element, n.kind(), "outputMapping", n.outputMapping());
            expression(p, element, n.kind(), "condition", null, n.condition());
        }
        for (Flow f : p.flows()) {
            String name = (f.source() == null ? "?" : f.source().name()) + " -> "
                    + (f.target() == null ? "?" : f.target().name()) + (f.name() == null ? "" : " (" + f.name() + ")");
            expression(p, new Ref(f.id(), name), "flow", "condition", null, f.condition());
        }
        if (p.roles() != null) {
            for (Process.Role r : p.roles()) {
                mapping(p, new Ref(r.id(), r.name()), "role", "assignment", r.assignment());
            }
        }
        if (p.dataObjects() != null) {
            for (Process.DataObject d : p.dataObjects()) {
                mapping(p, new Ref(d.id(), d.name()), d.kind(), "initialValue", d.initialValue());
            }
        }
    }

    private void mapping(Process p, Ref element, String kind, String part, Mapping m) {
        if (m == null || m.assignments() == null) {
            return;
        }
        for (Mapping.Assignment a : m.assignments()) {
            expression(p, element, kind, part, a.target(), a.expr());
        }
    }

    private void expression(Process p, Ref element, String kind, String part, String target, Expression e) {
        if (e == null || e.functions() == null) {
            return;
        }
        for (FunctionCall c : e.functions()) {
            if (c.id() != null && !"builtin".equals(c.source())) {
                List<Function.Usage> list = byFunction.computeIfAbsent(c.id(), k -> new ArrayList<>());
                Function.Usage usage = new Function.Usage(p.name(), element, kind, part, target, e.text());
                if (!list.contains(usage)) { // the same function twice in one expression: one usage
                    list.add(usage);
                }
            }
        }
    }
}
