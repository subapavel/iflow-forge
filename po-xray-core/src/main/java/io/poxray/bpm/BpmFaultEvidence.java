package io.poxray.bpm;

import io.poxray.model.BpmFaultReport.Evidence;
import io.poxray.model.BpmFaultReport.Scope;
import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmSourceEvidence.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Contextual walks with raw payloads deduplicated by occurrence, never by ID or text. */
final class BpmFaultEvidence {
    private static final Set<String> CHILDREN = Set.of(Galaxy.EXPRESSION_STEPS, Galaxy.REFERENCE_QNAME,
            Galaxy.REFERENCE_FEATURE, Galaxy.LITERAL_TYPE, Galaxy.INVOCATION_FUNCTION, Galaxy.INVOCATION_PARAMETERS,
            Galaxy.MAPPING_PARTS, Galaxy.PART_CHILDREN, Galaxy.PART_QNAME, Galaxy.PART_EXPRESSION,
            Galaxy.CONDITION_CONDITION, Galaxy.CONDITION_POSITIVE, Galaxy.CONDITION_NEGATIVE);
    private static final Set<String> LEAVES = Set.of(Galaxy.REFERENCE_QNAME, Galaxy.REFERENCE_FEATURE,
            Galaxy.LITERAL_TYPE, Galaxy.INVOCATION_FUNCTION, Galaxy.PART_QNAME);
    private static final Set<String> SYNTAX = Set.of(Galaxy.EXPRESSION, Galaxy.REFERENCE, Galaxy.PARENT,
            Galaxy.LITERAL, Galaxy.FUNCTION_INVOCATION, Galaxy.CONDITION, Galaxy.MAPPING, Galaxy.MAPPING_PART);
    final BpmFaultContext c;
    final List<Fact> path = new ArrayList<>();
    final Set<Locator> elements = new LinkedHashSet<>();
    final Set<Locator> links = new LinkedHashSet<>();

    BpmFaultEvidence(BpmFaultContext context) { c = context; }

    void element(Element element) { if (element != null) elements.add(element.location()); }
    void endpoint(Endpoint endpoint) { c.candidates(endpoint).forEach(this::element); }
    void follow(Element element) {
        if (element != null) { path.add(new Fact("element", element.location())); element(element); }
    }
    void follow(Link link) {
        path.add(new Fact("link", link.location())); links.add(link.location());
        endpoint(link.first()); endpoint(link.second());
    }
    void merge(BpmFaultEvidence other) {
        path.addAll(other.path); elements.addAll(other.elements); links.addAll(other.links);
    }
    Evidence build() {
        return new Evidence(path, c.source.elements().stream().filter(e -> elements.contains(e.location())).toList(),
                c.source.links().stream().filter(l -> links.contains(l.location())).toList());
    }

    List<Scope> scopes(BpmFaultContext.Member member, Endpoint owner) {
        if (owner == null) return List.of();
        List<Locator> candidates = new ArrayList<>();
        c.candidates(owner).forEach(e -> candidates.add(e.location()));
        for (Candidate candidate : owner.candidates()) if (!candidates.contains(candidate.location())) candidates.add(candidate.location());
        List<Scope> result = new ArrayList<>();
        for (Locator candidate : candidates) {
            BpmFaultEvidence basis = new BpmFaultEvidence(c);
            ArrayDeque<Locator> pending = new ArrayDeque<>(); pending.add(candidate);
            Set<Locator> visited = new HashSet<>();
            boolean explicit = false, attached = false;
            while (!pending.isEmpty()) {
                Locator at = pending.removeFirst();
                if (!visited.add(at)) continue;
                basis.follow(c.elements.get(at));
                for (Link link : c.source.links()) {
                    if (!BpmFaultContext.touches(link.first(), at)) continue;
                    boolean membership = link.association().equals(Galaxy.SCOPE_CONTAINS_OBJECTS);
                    boolean pool = link.association().equals(Galaxy.COLLABORATION_CONTAINS_POOLS);
                    boolean attachment = link.association().equals(Galaxy.ACTIVITY_BOUNDARY_EVENT);
                    if (!membership && !pool && !attachment) continue;
                    if (at.equals(candidate)) { explicit |= membership; attached |= attachment; }
                    basis.follow(link);
                    for (Candidate parent : link.second().candidates()) pending.addLast(parent.location());
                }
            }
            String state = member == null ? "unknown" : member.state(candidate);
            List<String> why = state.equals("unknown") ? List.of("owner-scope-unknown")
                    : state.equals("inactive") ? List.of("inactive-owner-scope") : List.of();
            String origin = explicit && attached ? "mixed" : explicit ? "explicit-membership" : attached ? "attachment-derived" : "unknown";
            List<Locator> ancestry = member == null || state.equals("unknown") ? List.of() : member.scopes.paths().get(candidate);
            result.add(new Scope(candidate, state, origin, ancestry, basis.path, why));
            merge(basis);
        }
        return result;
    }

    /** Boundary membership is evidence, while Declaration.scopes continues to describe its activity. */
    void retainBoundaryScope(BpmFaultContext.Member member, Endpoint boundary) {
        BpmFaultEvidence basis = new BpmFaultEvidence(c);
        basis.scopes(member, boundary);
        for (Fact fact : basis.path) if (!path.contains(fact)) path.add(fact);
        elements.addAll(basis.elements); links.addAll(basis.links);
    }

    Condition condition(Link link, List<String> declarationReasons) {
        BpmFaultEvidence trace = new BpmFaultEvidence(c); trace.follow(link);
        c.usedLinks.add(link.location());
        Element expression = c.unique(link.second());
        boolean valid = expression != null && Galaxy.EXPRESSION.equals(expression.metaclass()) && c.unique(link.first()) != null;
        List<String> why = new ArrayList<>();
        if (!valid) why.add("condition-expression-unknown");
        for (Element candidate : c.candidates(link.second())) {
            if (Galaxy.EXPRESSION.equals(candidate.metaclass())) trace.syntax(candidate, why);
            else trace.follow(candidate);
        }
        String text = expression == null ? null : expression.attributes().getOrDefault("stringExpression", expression.texts().get("stringExpression"));
        declarationReasons.addAll(why); merge(trace);
        return new Condition(valid ? "not-evaluated" : "unknown", text, trace.path, BpmFaultContext.distinct(why));
    }

    private record Visit(Element element, Link link, boolean exit, boolean leaf) { }

    /** Iterative depth-first retention with an active-path cycle check and no depth cutoff. */
    private void syntax(Element root, List<String> why) {
        ArrayDeque<Visit> pending = new ArrayDeque<>(); pending.push(new Visit(root, null, false, false));
        Set<Locator> active = new HashSet<>(), finished = new HashSet<>();
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            if (visit.link() != null) {
                Link link = visit.link(); follow(link);
                if (c.unique(link.first()) == null || c.unique(link.second()) == null) why.add("condition-syntax-payload-unknown");
                else if (!supportedShape(link)) why.add("unsupported-condition-shape");
                continue;
            }
            Element element = visit.element();
            if (visit.exit()) { active.remove(element.location()); finished.add(element.location()); continue; }
            if (visit.leaf()) { follow(element); continue; }
            if (active.contains(element.location())) { follow(element); why.add("condition-syntax-cycle"); continue; }
            if (finished.contains(element.location())) continue;
            follow(element);
            if (!SYNTAX.contains(element.metaclass())) { why.add("unsupported-condition-syntax"); continue; }
            active.add(element.location()); pending.push(new Visit(element, null, true, false));
            List<Link> related = c.source.links().stream().filter(l ->
                    CHILDREN.contains(l.association()) && BpmFaultContext.touches(l.second(), element.location())
                    || !CHILDREN.contains(l.association()) && ruleOrMapping(l)
                    && (BpmFaultContext.touches(l.first(), element.location()) || BpmFaultContext.touches(l.second(), element.location()))).toList();
            for (int i = related.size() - 1; i >= 0; i--) {
                Link link = related.get(i);
                if (!CHILDREN.contains(link.association())) {
                    // Raw incident facts have no invented child direction.
                    follow(link); why.add("unsupported-relation"); continue;
                }
                List<Element> children = c.candidates(link.first());
                for (int j = children.size() - 1; j >= 0; j--)
                    pending.push(new Visit(children.get(j), null, false, LEAVES.contains(link.association())));
                pending.push(new Visit(null, link, false, false));
            }
        }
    }

    private boolean supportedShape(Link link) {
        String parent = c.unique(link.second()).metaclass();
        String child = c.unique(link.first()).metaclass();
        return switch (link.association()) {
            case Galaxy.EXPRESSION_STEPS -> parent.equals(Galaxy.EXPRESSION)
                    && Set.of(Galaxy.REFERENCE, Galaxy.PARENT, Galaxy.LITERAL, Galaxy.FUNCTION_INVOCATION, Galaxy.CONDITION).contains(child);
            case Galaxy.REFERENCE_QNAME -> parent.equals(Galaxy.REFERENCE) && child.equals(Galaxy.QNAME);
            case Galaxy.REFERENCE_FEATURE -> parent.equals(Galaxy.REFERENCE) && child.equals(Galaxy.XSD_ELEMENT);
            case Galaxy.LITERAL_TYPE -> parent.equals(Galaxy.LITERAL) && child.equals(Galaxy.XSD_SIMPLE_TYPE);
            case Galaxy.INVOCATION_FUNCTION -> parent.equals(Galaxy.FUNCTION_INVOCATION) && child.equals(Galaxy.FUNCTION_INTERFACE);
            case Galaxy.INVOCATION_PARAMETERS -> parent.equals(Galaxy.FUNCTION_INVOCATION) && child.equals(Galaxy.MAPPING);
            case Galaxy.MAPPING_PARTS -> parent.equals(Galaxy.MAPPING) && child.equals(Galaxy.MAPPING_PART);
            case Galaxy.PART_CHILDREN -> parent.equals(Galaxy.MAPPING_PART) && child.equals(Galaxy.MAPPING_PART);
            case Galaxy.PART_QNAME -> parent.equals(Galaxy.MAPPING_PART) && child.equals(Galaxy.QNAME);
            case Galaxy.PART_EXPRESSION -> parent.equals(Galaxy.MAPPING_PART) && child.equals(Galaxy.EXPRESSION);
            case Galaxy.CONDITION_CONDITION, Galaxy.CONDITION_POSITIVE, Galaxy.CONDITION_NEGATIVE ->
                    parent.equals(Galaxy.CONDITION) && child.equals(Galaxy.EXPRESSION);
            default -> false;
        };
    }

    private static boolean ruleOrMapping(Link link) {
        return link.association().startsWith("Galaxy.Rule.") || link.association().startsWith("Galaxy.Mapping.");
    }
}
