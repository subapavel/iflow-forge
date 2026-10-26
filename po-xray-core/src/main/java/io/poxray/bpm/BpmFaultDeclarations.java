package io.poxray.bpm;

import io.poxray.model.BpmFaultReport.*;
import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmSourceEvidence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Source grammar only: association shape, contextual candidates and explicit missing evidence. */
final class BpmFaultDeclarations {
    private static final Set<String> ACTIVITIES = Set.of(Galaxy.AUTOMATED_ACTIVITY, Galaxy.MAPPING_ACTIVITY,
            Galaxy.NOTIFICATION_ACTIVITY, Galaxy.EMBEDDED_SCOPE);
    private static final Set<String> WORKFLOW = Set.of(Galaxy.ACTIVITY_EVENT_DEFINITIONS, Galaxy.ACTIVITY_BOUNDARY_EVENT,
            Galaxy.CATCH_EVENT_TRIGGER, Galaxy.CATCH_EVENT_EXPRESSION, Galaxy.THROW_EVENT_RESULT,
            Galaxy.ERROR_EVENT_FAULT, Galaxy.EVENT_DEFINITION_PARAMETER);
    private final BpmFaultContext c;

    BpmFaultDeclarations(BpmFaultContext context) { c = context; }

    void read() {
        for (Link link : c.source.links()) {
            switch (link.association()) {
                case Galaxy.ACTIVITY_EVENT_DEFINITIONS -> primary("activity-definition", link.second(), null,
                        link.first(), List.of(link), duplicate(link));
                case Galaxy.ACTIVITY_BOUNDARY_EVENT -> boundary(link);
                case Galaxy.CATCH_EVENT_TRIGGER -> {
                    Element owner = c.unique(link.second());
                    boolean start = owner != null && owner.metaclass().equals(Galaxy.START_EVENT);
                    if (start || attachments(link.second()).isEmpty()) {
                        List<String> why = new ArrayList<>(duplicate(link));
                        if (count(link) > 1) why.add("multiple-trigger-declarations");
                        if (!start) why.add("unsupported-catch-owner");
                        if (c.candidates(link.second()).stream().anyMatch(e -> e.metaclass().equals(Galaxy.BOUNDARY_EVENT)))
                            why.add("orphan-boundary-trigger");
                        primary(start ? "start-trigger" : "unsupported-catch-trigger", link.second(), null,
                                link.first(), List.of(link), why);
                    }
                }
                case Galaxy.THROW_EVENT_RESULT -> {
                    List<String> why = new ArrayList<>(duplicate(link));
                    if (count(link) > 1) why.add("multiple-result-declarations");
                    primary("end-result", link.second(), null, link.first(), List.of(link), why);
                }
                default -> { }
            }
        }
        // Local orphan occurrences precede unconsumed associations; no synthetic unique endpoint.
        for (Element element : c.source.elements()) {
            if (!BpmFaultContext.definition(element) || c.usedDefinitions.contains(element.location())) continue;
            List<BpmFaultContext.Member> owners = c.physical(element.location());
            if (owners.size() == 1) {
                add(owners.getFirst(), "orphan-definition", null, null, null, List.of(),
                        List.of("orphan-definition"), List.of(element), List.of());
            } else {
                BpmFaultEvidence trace = new BpmFaultEvidence(c); trace.follow(element);
                c.unassigned("unassigned-definition", trace, "unassigned-definition", false);
            }
        }
        for (Link link : c.source.links()) {
            if (c.usedLinks.contains(link.location())) continue;
            switch (link.association()) {
                case Galaxy.CATCH_EVENT_EXPRESSION -> {
                    List<BpmFaultContext.Member> owners = c.owners(link.first(), link.location());
                    if (owners.size() != 1) unassigned("catch-condition-only", link, null, List.of(link));
                    else add(owners.getFirst(), "catch-condition-only", link.first(), null,
                            null, List.of(), List.of("condition-without-declaration"), List.of(), List.of(link));
                }
                case Galaxy.ERROR_EVENT_FAULT, Galaxy.EVENT_DEFINITION_PARAMETER -> {
                    String role = link.association().equals(Galaxy.ERROR_EVENT_FAULT) ? "orphan-fault-reference" : "orphan-parameter-reference";
                    List<BpmFaultContext.Member> owners = c.owners(link.second(), link.location());
                    if (owners.size() == 1) add(owners.getFirst(), role, null, null, link.second(), List.of(link),
                            List.of(role), List.of(), List.of());
                    else unassigned(role, link, link.second(), List.of());
                }
                default -> {
                    if (!unknownWorkflow(link)) continue;
                    List<Element> definitions = c.source.elements().stream().filter(BpmFaultContext::definition)
                            .filter(e -> incident(link, e)).toList();
                    if (definitions.isEmpty()) continue;
                    List<BpmFaultContext.Member> owners = c.members.stream().filter(m -> m.available()
                            && definitions.stream().anyMatch(e -> c.physical(e.location()).contains(m))).toList();
                    if (owners.size() == 1) add(owners.getFirst(), "orphan-metadata", null, null, null, List.of(link),
                            List.of("unsupported-workflow-relation"), List.of(), List.of());
                    else unassigned("orphan-metadata", link, null, List.of());
                }
            }
        }
    }

    private List<Link> attachments(Endpoint boundary) {
        return c.source.links().stream().filter(l -> l.association().equals(Galaxy.ACTIVITY_BOUNDARY_EVENT)
                && BpmFaultContext.overlaps(l.first(), boundary)).toList();
    }

    private long count(Link link) {
        return c.source.links().stream().filter(l -> l.association().equals(link.association())
                && (BpmFaultContext.overlaps(l.second(), link.second()) || l.second().equals(link.second()))).count();
    }

    private List<String> duplicate(Link link) {
        long count = c.source.links().stream().filter(l -> l.association().equals(link.association())
                && l.first().equals(link.first()) && l.second().equals(link.second())).count();
        return count > 1 ? List.of("duplicate-declaration") : List.of();
    }

    private void boundary(Link attachment) {
        List<Link> triggers = c.source.links().stream().filter(l -> l.association().equals(Galaxy.CATCH_EVENT_TRIGGER)
                && BpmFaultContext.overlaps(l.second(), attachment.first())).toList();
        List<String> why = new ArrayList<>(duplicate(attachment));
        if (attachments(attachment.first()).size() != 1) why.add("boundary-attachment-ambiguous");
        if (triggers.size() > 1) why.add("multiple-trigger-declarations");
        if (triggers.isEmpty()) {
            why.add("missing-trigger");
            primary("boundary-trigger", attachment.second(), attachment.first(), null, List.of(attachment), why);
        } else for (Link trigger : triggers) {
            List<String> reasons = new ArrayList<>(why); reasons.addAll(duplicate(trigger));
            if (c.unique(trigger.second()) == null) reasons.add("trigger-owner-ambiguous");
            primary("boundary-trigger", attachment.second(), attachment.first(), trigger.first(), List.of(attachment, trigger), reasons);
        }
    }

    private void primary(String role, Endpoint owner, Endpoint event, Endpoint definition, List<Link> chain, List<String> reasons) {
        List<BpmFaultContext.Member> owners = c.owners(owner, chain.getFirst().location());
        List<String> why = new ArrayList<>(reasons);
        if (owners.size() > 1) why.add("ambiguous-process-attribution");
        if (c.unique(owner) == null) c.members.stream().filter(BpmFaultContext.Member::available)
                .forEach(m -> m.reasons.add("malformed-fault-ownership"));
        if (owners.isEmpty()) {
            Declaration row = build(null, role, owner, event, definition, chain, why, List.of(), List.of());
            BpmFaultEvidence trace = new BpmFaultEvidence(c);
            row.evidence().path().forEach(trace.path::add);
            row.evidence().elements().forEach(trace::element);
            row.evidence().links().forEach(l -> trace.links.add(l.location()));
            c.unassigned(role, trace, "unattributed-ownership", true);
        } else for (BpmFaultContext.Member member : owners) add(member, role, owner, event, definition, chain, why, List.of(), List.of());
    }

    private void add(BpmFaultContext.Member member, String role, Endpoint owner, Endpoint event, Endpoint definition,
            List<Link> chain, List<String> why, List<Element> orphan, List<Link> conditions) {
        Declaration row = build(member, role, owner, event, definition, chain, why, orphan, conditions);
        member.declarations.add(row);
        for (String reason : row.reasons()) c.problem(member, reason, chain.isEmpty() ? null : chain.getFirst().location());
    }

    private Declaration build(BpmFaultContext.Member member, String role, Endpoint owner, Endpoint event, Endpoint endpoint,
            List<Link> chain, List<String> initial, List<Element> orphan, List<Link> forcedConditions) {
        BpmFaultEvidence trace = new BpmFaultEvidence(c);
        List<String> why = new ArrayList<>(initial);
        boolean unknown = !initial.isEmpty();
        for (Link link : chain) { trace.follow(link); c.usedLinks.add(link.location()); }
        trace.endpoint(owner); trace.endpoint(event); trace.endpoint(endpoint);
        List<Scope> scopes = trace.scopes(member, owner);
        Element resolvedOwner = c.unique(owner);
        if (resolvedOwner == null) { why.add("unknown-owner"); unknown = true; }
        else {
            boolean kind = switch (role) {
                case "activity-definition", "boundary-trigger" -> ACTIVITIES.contains(resolvedOwner.metaclass());
                case "start-trigger" -> Galaxy.START_EVENT.equals(resolvedOwner.metaclass());
                case "end-result" -> Galaxy.END_EVENT.equals(resolvedOwner.metaclass());
                default -> false;
            };
            if (!kind) { why.add("unsupported-owner-kind"); unknown = true; }
        }
        for (Scope scope : scopes) {
            why.addAll(scope.reasons());
            if (scope.state().equals("unknown")) unknown = true;
        }
        if (event != null) {
            trace.retainBoundaryScope(member, event);
            Element boundary = c.unique(event);
            if (boundary == null || !Galaxy.BOUNDARY_EVENT.equals(boundary.metaclass())) { why.add("boundary-endpoint-unknown"); unknown = true; }
            if (member != null && boundary != null) {
                List<BpmFaultContext.Member> eventOwners = c.owners(event, boundary.location());
                if (!eventOwners.contains(member) || eventOwners.size() != 1) { why.add("cross-process-boundary-conflict"); unknown = true; }
            }
            if (member != null) for (Link attachment : chain) {
                if (!attachment.association().equals(Galaxy.ACTIVITY_BOUNDARY_EVENT)) continue;
                var arcs = member.paths.arcs().stream().filter(arc -> arc.kind().equals("boundary-attachment")
                        && arc.evidence().stream().anyMatch(fact -> fact.kind().equals("link")
                        && fact.location().equals(attachment.location()))).toList();
                if (arcs.size() != 1) { why.add("boundary-validation-unknown"); unknown = true; }
                else if (!arcs.getFirst().resolution().equals("resolved")) {
                    why.addAll(arcs.getFirst().reasons()); why.add("invalid-boundary-attachment"); unknown = true;
                }
            }
        }
        List<Definition> definitions = new ArrayList<>();
        for (Element source : orphan.isEmpty() ? c.candidates(endpoint) : orphan) {
            Definition definition = definition(source, trace);
            definitions.add(definition); why.addAll(definition.reasons());
            if (definition.kind().equals("unknown") || definition.reasons().contains("unsupported-workflow-relation")) unknown = true;
        }
        Builtin builtin = builtin(endpoint);
        if (c.unique(endpoint) == null) {
            if (builtin == null) { why.add("definition-endpoint-unknown"); unknown = true; }
            else { why.add("definition-source-missing"); why.add("catalogue-source-substitution"); }
        }
        List<Condition> conditions = new ArrayList<>();
        List<Link> conditionLinks = forcedConditions;
        if (conditionLinks.isEmpty() && owner != null) {
            Endpoint catchOwner = event == null ? owner : event;
            conditionLinks = c.source.links().stream().filter(l -> l.association().equals(Galaxy.CATCH_EVENT_EXPRESSION)
                    && BpmFaultContext.overlaps(l.first(), catchOwner)).toList();
        }
        for (Link link : conditionLinks) {
            Condition condition = trace.condition(link, why); conditions.add(condition);
            if (condition.state().equals("unknown")) unknown = true;
        }
        if (why.contains("unsupported-relation") || why.contains("unsupported-condition-syntax")
                || why.contains("unsupported-condition-shape")) unknown = true;
        String state = unknown ? "unknown" : builtin != null ? "catalogue"
                : definitions.size() == 1 && definitions.getFirst().kind().equals("error") ? "declared" : "non-error";
        return new Declaration(member == null ? 0 : member.declarations.size() + 1, role, owner, event, endpoint,
                scopes, definitions, builtin, conditions, trace.build(), state, BpmFaultContext.distinct(why));
    }

    private Definition definition(Element source, BpmFaultEvidence outer) {
        c.usedDefinitions.add(source.location());
        BpmFaultEvidence trace = new BpmFaultEvidence(c); trace.follow(source);
        String kind = source.metaclass().equals(Galaxy.ERROR_EVENT_DEFINITION) ? "error"
                : source.metaclass().equals(Galaxy.MESSAGE_EVENT_DEFINITION) ? "message" : "unknown";
        List<String> why = new ArrayList<>();
        if (kind.equals("unknown")) why.add("unknown-definition-kind");
        List<FaultReference> faults = new ArrayList<>();
        for (Link link : c.source.links()) {
            if (link.association().equals(Galaxy.ERROR_EVENT_FAULT) && BpmFaultContext.touches(link.second(), source.location())) {
                FaultReference fault = fault(faults.size() + 1, link, trace);
                faults.add(fault); why.addAll(fault.reasons()); c.usedLinks.add(link.location());
            } else if (link.association().equals(Galaxy.EVENT_DEFINITION_PARAMETER) && BpmFaultContext.touches(link.second(), source.location())) {
                trace.follow(link); c.usedLinks.add(link.location());
                Element parameter = c.unique(link.first());
                if (parameter == null || !Galaxy.XSD_ELEMENT.equals(parameter.metaclass()) || c.unique(link.second()) == null)
                    why.add("parameter-reference-unknown");
            } else if (unknownWorkflow(link) && incident(link, source)) {
                trace.follow(link); c.usedLinks.add(link.location()); why.add("unsupported-workflow-relation");
            }
        }
        outer.merge(trace);
        return new Definition(source, kind, flag(source, "isCritical"), flag(source, "isTechnical"), faults,
                trace.build(), BpmFaultContext.distinct(why));
    }

    private FaultReference fault(int ordinal, Link link, BpmFaultEvidence outer) {
        BpmFaultEvidence trace = new BpmFaultEvidence(c); trace.follow(link);
        String state;
        Endpoint endpoint = link.first();
        if (endpoint.resolution().equals("ambiguous") || endpoint.candidates().size() > 1) state = "ambiguous";
        else if (endpoint.resolution().equals("missing") && endpoint.candidates().isEmpty()) state = "missing";
        else if (endpoint.candidates().size() == 1 && endpoint.resolution().equals("unique")) {
            Candidate candidate = endpoint.candidates().getFirst();
            state = !Galaxy.WSDL_FAULT.equals(candidate.metaclass()) ? "wrong-kind" : candidate.included() ? "included" : "identity-only";
        } else state = "unknown";
        List<String> why = new ArrayList<>();
        if (!state.equals("included")) why.add("fault-reference-" + state);
        if (c.unique(link.second()) == null) why.add("fault-definition-owner-unknown");
        for (Link direct : c.source.links()) {
            if (!direct.association().equals(Galaxy.REFERENCE_TO_ELEMENT) || !BpmFaultContext.overlaps(direct.first(), endpoint)) continue;
            trace.follow(direct);
            Element payload = c.unique(direct.second());
            if (c.unique(direct.first()) == null || payload == null || !Galaxy.XSD_ELEMENT.equals(payload.metaclass())) why.add("fault-payload-reference-unknown");
        }
        outer.merge(trace);
        return new FaultReference(ordinal, link, state, trace.build(), BpmFaultContext.distinct(why));
    }

    private void unassigned(String role, Link link, Endpoint definition, List<Link> conditions) {
        BpmFaultEvidence trace = new BpmFaultEvidence(c); trace.follow(link); c.usedLinks.add(link.location());
        for (Element candidate : c.candidates(definition)) definition(candidate, trace);
        List<String> why = new ArrayList<>();
        for (Link condition : conditions) trace.condition(condition, why);
        c.unassigned(role, trace, "unattributed-metadata", true);
    }

    private static Flag flag(Element source, String name) {
        String value = source.attributes().get(name);
        return new Flag(source.attributes().containsKey(name), value,
                "true".equals(value) || "false".equals(value) ? value : "unknown");
    }

    private static Builtin builtin(Endpoint endpoint) {
        if (endpoint == null || !endpoint.resolution().equals("missing") || !endpoint.candidates().isEmpty()) return null;
        return BuiltinFunctionCatalog.get().trigger(endpoint.id()).map(t -> new Builtin(t.id(), t.kind(), t.name(),
                t.isCritical(), t.isTechnical(), t.parameter() == null ? null : new Parameter(t.parameter().name(),
                t.parameter().namespace(), t.parameter().type()), t.file(), t.sourceDc(),
                "io/poxray/bpm/" + BuiltinFunctionCatalog.RESOURCE)).orElse(null);
    }

    private static boolean incident(Link link, Element source) {
        return BpmFaultContext.touches(link.first(), source.location()) || BpmFaultContext.touches(link.second(), source.location());
    }
    private static boolean unknownWorkflow(Link link) {
        return link.association().startsWith("Galaxy.Workflow.") && !WORKFLOW.contains(link.association());
    }
}
