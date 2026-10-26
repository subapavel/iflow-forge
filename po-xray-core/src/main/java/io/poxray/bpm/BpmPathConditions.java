package io.poxray.bpm;

import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves declaration evidence without evaluating expressions or deciding connectivity. */
final class BpmPathConditions {
    private final BpmSourceEvidence evidence;
    private final Map<Locator, Element> elements = new LinkedHashMap<>();

    BpmPathConditions(BpmSourceEvidence evidence) {
        this.evidence = evidence;
        for (Element element : evidence.elements()) elements.put(element.location(), element);
    }

    static Flag flag(Element element, String name) {
        String value = element.attributes().get(name);
        return new Flag(element.attributes().containsKey(name), value,
                "true".equals(value) || "false".equals(value) ? value : "unknown");
    }

    static Flag absentFlag() { return new Flag(false, null, "unknown"); }

    static Fact fact(Link link) { return new Fact("link", link.location()); }
    static Fact fact(Element element) { return new Fact("element", element.location()); }

    static boolean touches(Endpoint endpoint, Locator location) {
        return endpoint.candidates().stream().anyMatch(c -> c.location().equals(location));
    }

    Element unique(Endpoint endpoint) {
        if (!"unique".equals(endpoint.resolution()) || endpoint.candidates().size() != 1) return null;
        return elements.get(endpoint.candidates().getFirst().location());
    }

    List<Link> links(String association, Element second) {
        return evidence.links().stream().filter(l -> association.equals(l.association())
                && touches(l.second(), second.location())).toList();
    }

    Condition read(Element connector) {
        List<Link> links = links(Galaxy.CONNECTOR_EXPRESSION, connector);
        List<Fact> facts = new ArrayList<>();
        links.forEach(l -> facts.add(fact(l)));
        if (Galaxy.SEQUENCE_CONNECTOR.equals(connector.metaclass())) {
            return condition(links.isEmpty() ? "unconditional" : "unknown", null, facts,
                    links.isEmpty() ? null : "unexpected-sequence-condition");
        }
        if (links.isEmpty()) return condition("missing", null, facts, "missing-condition-association");
        if (links.size() != 1 || ambiguous(links.getFirst().first()) || ambiguous(links.getFirst().second())) {
            return condition("ambiguous", null, facts, "ambiguous-condition-association");
        }
        Link attachment = links.getFirst();
        Element expression = unique(attachment.first());
        if (expression == null || unique(attachment.second()) == null) {
            return condition("unresolved", null, facts, "unresolved-condition-endpoint");
        }
        facts.add(fact(expression));
        String editor = expression.attributes().get("stringExpression");
        if (editor == null) editor = expression.texts().get("stringExpression");
        if (!Galaxy.EXPRESSION.equals(expression.metaclass())) {
            return condition("unknown", editor, facts, "unsupported-condition-expression");
        }
        List<Link> steps = links(Galaxy.EXPRESSION_STEPS, expression);
        steps.forEach(l -> facts.add(fact(l)));
        if (steps.size() != 1 || unique(steps.getFirst().second()) == null) {
            return condition("unknown", editor, facts, "nonunique-expression-step");
        }
        Element literal = unique(steps.getFirst().first());
        if (literal == null || !Galaxy.LITERAL.equals(literal.metaclass())) {
            return condition("unknown", editor, facts, "unsupported-expression-step");
        }
        facts.add(fact(literal));
        String value = literal.attributes().get("value");
        String text = editor == null ? value : editor;
        List<Link> types = links(Galaxy.LITERAL_TYPE, literal);
        types.forEach(l -> facts.add(fact(l)));
        if (types.size() != 1 || unique(types.getFirst().second()) == null
                || !types.getFirst().first().candidates().isEmpty()
                || !"xsd:boolean".equals(BuiltinFunctionCatalog.get().typeName(types.getFirst().first().id()))) {
            return condition("unknown", text, facts, "unproven-boolean-type");
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            return condition("unknown", text, facts, "invalid-boolean-literal");
        }
        if (editor != null && !editor.isBlank() && !editor.strip().equals(value)) {
            return condition("unknown", text, facts, "editor-tree-disagreement");
        }
        return condition("constant-" + value, text, facts, null);
    }

    static boolean ambiguous(Endpoint endpoint) {
        return endpoint.candidates().size() > 1 || "ambiguous".equals(endpoint.resolution());
    }

    static Condition structural(String kind) {
        return condition("unknown", null, List.of(), kind);
    }

    private static Condition condition(String state, String text, List<Fact> facts, String reason) {
        return new Condition(state, text, facts, reason == null ? List.of() : List.of(reason));
    }
}
