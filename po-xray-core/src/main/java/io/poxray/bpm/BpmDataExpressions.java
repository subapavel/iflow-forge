package io.poxray.bpm;

import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmSourceEvidence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Retained expression syntax and read roots; editor strings and function bodies are never evaluated. */
final class BpmDataExpressions {
    private static final Set<String> GRAMMAR = Set.of(Galaxy.REFERENCE, Galaxy.PARENT, Galaxy.LITERAL,
            Galaxy.FUNCTION_INVOCATION, Galaxy.CONDITION);
    private final BpmDataContext c;
    private final BpmDataMappings mappings;
    record Result(String kind, List<String> reasons) { }

    BpmDataExpressions(BpmDataContext context, BpmDataMappings mappings) { c = context; this.mappings = mappings; }

    Result part(Element part, MappingUse use, int declaration, BpmDataContext.Trace trace,
            Set<Locator> activeMappings, Set<Locator> activeExpressions) {
        List<Link> links = c.links(Galaxy.PART_EXPRESSION, part);
        if (links.isEmpty()) return new Result("structural", List.of());
        List<String> why = new ArrayList<>();
        String kind = "unknown";
        if (links.size() == 1) {
            Element expression = c.unique(links.getFirst().first());
            Element owner = c.unique(links.getFirst().second());
            if (owner != null && owner.location().equals(part.location())
                    && expression != null && Galaxy.EXPRESSION.equals(expression.metaclass())) {
                List<Link> steps = c.links(Galaxy.EXPRESSION_STEPS, expression);
                if (steps.isEmpty()) {
                    String editor = expression.attributes().getOrDefault("stringExpression", expression.texts().get("stringExpression"));
                    kind = editor == null || editor.isBlank() ? "structural" : "unknown";
                } else if (steps.stream().allMatch(l -> {
                    Element step = c.unique(l.first());
                    return step != null && GRAMMAR.contains(step.metaclass()) && c.unique(l.second()) != null;
                })) kind = "expression";
            }
        }
        if (kind.equals("unknown")) why.add("assignment-expression-unknown");
        for (Link link : links) {
            trace.follow(link);
            Element expression = c.unique(link.first());
            if (expression != null && Galaxy.EXPRESSION.equals(expression.metaclass()))
                why.addAll(walk(expression, use, declaration, trace, activeMappings, activeExpressions));
        }
        return new Result(kind, BpmDataMappings.distinct(why));
    }

    private List<String> walk(Element expression, MappingUse use, int declaration, BpmDataContext.Trace trace,
            Set<Locator> activeMappings, Set<Locator> activeExpressions) {
        List<String> why = new ArrayList<>();
        trace.follow(expression);
        if (activeExpressions.contains(expression.location())) return List.of("expression-context-cycle");
        Set<Locator> active = new HashSet<>(activeExpressions); active.add(expression.location());
        List<Link> steps = c.links(Galaxy.EXPRESSION_STEPS, expression);
        if (steps.isEmpty()) {
            String editor = expression.attributes().getOrDefault("stringExpression", expression.texts().get("stringExpression"));
            if (editor != null && !editor.isBlank()) why.add("assignment-expression-unknown");
        }
        Name root = null;
        Element first = steps.isEmpty() ? null : c.unique(steps.getFirst().first());
        boolean leadingParent = first != null && Galaxy.PARENT.equals(first.metaclass());
        // Capture the complete ordinary path (including each segment QName) before publishing its read.
        for (Link link : steps) {
            trace.follow(link);
            Element step = c.unique(link.first());
            if (step == null) { why.add("expression-step-unresolved"); continue; }
            trace.follow(step);
            switch (step.metaclass()) {
                case Galaxy.REFERENCE -> {
                    Name name = c.name(step, Galaxy.REFERENCE_QNAME, trace);
                    if (step == first) root = name;
                    for (Link feature : c.links(Galaxy.REFERENCE_FEATURE, step)) trace.link(feature);
                }
                case Galaxy.LITERAL -> {
                    List<Link> types = c.links(Galaxy.LITERAL_TYPE, step);
                    types.forEach(trace::link);
                    Element localType = types.size() == 1 ? c.unique(types.getFirst().first()) : null;
                    boolean typed = types.size() == 1 && (localType != null && Galaxy.XSD_SIMPLE_TYPE.equals(localType.metaclass())
                            || types.getFirst().first().candidates().isEmpty()
                            && BuiltinFunctionCatalog.get().typeName(types.getFirst().first().id()) != null);
                    if (!typed || !step.attributes().containsKey("value")) why.add("literal-value-or-type-unknown");
                }
                case Galaxy.FUNCTION_INVOCATION -> {
                    List<Link> functions = c.links(Galaxy.INVOCATION_FUNCTION, step);
                    functions.forEach(trace::link);
                    boolean known = false;
                    if (functions.size() == 1) {
                        Endpoint endpoint = functions.getFirst().first();
                        Element function = c.unique(endpoint);
                        known = function != null && Galaxy.FUNCTION_INTERFACE.equals(function.metaclass())
                                || endpoint.candidates().isEmpty() && BuiltinFunctionCatalog.get().function(endpoint.id()).isPresent();
                    }
                    if (!known) why.add("function-identity-unknown");
                    c.links(Galaxy.INVOCATION_PARAMETERS, step).forEach(trace::link);
                }
                case Galaxy.PARENT, Galaxy.CONDITION -> { }
                default -> why.add("unsupported-expression-step");
            }
        }
        if (root != null || leadingParent) read(use, declaration, trace, root, leadingParent);
        for (Link link : steps) {
            Element step = c.unique(link.first());
            if (step == null) continue;
            if (Galaxy.FUNCTION_INVOCATION.equals(step.metaclass())) {
                for (Link parameter : c.links(Galaxy.INVOCATION_PARAMETERS, step)) {
                    Element mapping = c.unique(parameter.first());
                    if (mapping == null || !Galaxy.MAPPING.equals(mapping.metaclass())) why.add("argument-mapping-unknown");
                    why.addAll(mappings.argument(parameter, use, declaration, trace, activeMappings, active));
                }
            } else if (Galaxy.CONDITION.equals(step.metaclass())) {
                for (String association : List.of(Galaxy.CONDITION_CONDITION, Galaxy.CONDITION_POSITIVE, Galaxy.CONDITION_NEGATIVE)) {
                    List<Link> branches = c.links(association, step);
                    if (branches.size() != 1) why.add("condition-branch-unknown");
                    for (Link branch : branches) {
                        BpmDataContext.Trace nested = c.new Trace(trace);
                        int prefix = nested.path.size(); nested.follow(branch);
                        Element target = c.unique(branch.first());
                        if (target == null || !Galaxy.EXPRESSION.equals(target.metaclass())) why.add("condition-branch-unknown");
                        else why.addAll(walk(target, use, declaration, nested, activeMappings, active));
                        trace.path.addAll(nested.path.subList(prefix, nested.path.size()));
                        trace.rawElements.addAll(nested.rawElements); trace.rawLinks.addAll(nested.rawLinks);
                    }
                }
            }
        }
        return BpmDataMappings.distinct(why);
    }

    private void read(MappingUse use, int declaration, BpmDataContext.Trace trace, Name root, boolean leadingParent) {
        List<String> why = new ArrayList<>();
        Binding binding;
        if (use.readDomain().equals("external-output")) {
            binding = new Binding("external", List.of(), List.of());
            if (leadingParent || root == null || !root.state().equals("resolved")) why.add("external-read-root-unknown");
        }
        else {
            binding = leadingParent ? new Binding("unknown", List.of(), List.of("leading-parent-root-unknown")) : c.bind(root, use.rootOwner());
            why.addAll(binding.reasons());
            if (!use.readDomain().equals("process-context")) {
                MappingUse enclosing = use;
                while (enclosing.parent() != null) enclosing = c.mappings.get(enclosing.parent() - 1);
                why.add(enclosing.role().equals("initial") ? "initializer-order-unknown" : "read-domain-unknown");
                binding = new Binding("unknown", binding.objects(), why);
            }
            if (c.loopContext(use.rootOwner())) {
                why.add("loop-local-context-unknown"); binding = new Binding("unknown", binding.objects(), why);
            }
        }
        why = BpmDataMappings.distinct(why);
        c.reads.add(new Read(c.reads.size() + 1, declaration, use.readDomain(), trace.build(), binding, List.of(), why));
        for (String reason : why) c.problem(reason, use.rootOwner());
    }
}
