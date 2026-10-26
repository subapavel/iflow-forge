package io.poxray.bpm;

import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmSourceEvidence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Each ownership and each root/child path is a distinct contextual mapping use. */
final class BpmDataMappings {
    private final BpmDataContext c;
    private final BpmDataExpressions expressions;

    BpmDataMappings(BpmDataContext context) { c = context; expressions = new BpmDataExpressions(c, this); }

    void read() {
        for (Link link : c.source.links()) {
            if (BpmDataContext.OWNERSHIP.contains(link.association()) && c.owns(link)) {
                c.usedLinks.add(link.location());
                Element owner = c.unique(link.second());
                Locator root = owner == null ? null : owner.location();
                List<String> why = new ArrayList<>();
                if (owner == null) why.add(link.second().candidates().size() > 1 ? "ambiguous-owner-attribution" : "mapping-owner-unknown");
                String[] domains = domains(owner, link.association());
                if (domains[0].equals("unknown") || domains[0].equals("unsupported-embedded")) why.add("unsupported-mapping-owner-direction");
                BpmDataContext.Trace trace = c.new Trace(); trace.follow(link);
                use(link, link.first(), root, domains[0], domains[1], domains[2], null, null, trace, why,
                        new HashSet<>(), new HashSet<>());
            }
        }
    }

    void orphan(Element element) {
        BpmDataContext.Trace trace = c.new Trace(); trace.element(element);
        if (Galaxy.MAPPING.equals(element.metaclass())) {
            use(null, BpmDataContext.endpoint(element), null, "unknown", "unknown", "unknown", null, null,
                    trace, List.of("unattributed-mapping"), new HashSet<>(), new HashSet<>());
        } else {
            int ordinal = c.mappings.size() + 1;
            c.mappings.add(new MappingUse(ordinal, null, null, null, null, null, "unknown", "unknown", "unknown",
                    trace.build(), List.of("unattributed-part")));
            c.problem("unattributed-part", element.location());
            part(ordinal, BpmDataContext.endpoint(element), List.of(), trace, new HashSet<>(), false,
                    new HashSet<>(), new HashSet<>());
        }
    }

    List<String> argument(Link link, MappingUse enclosing, int declaration, BpmDataContext.Trace trace,
            Set<Locator> activeMappings, Set<Locator> activeExpressions) {
        int firstUse = c.mappings.size(), firstDeclaration = c.declarations.size();
        BpmDataContext.Trace next = c.new Trace(trace); next.follow(link);
        use(link, link.first(), enclosing.rootOwner(), "argument", enclosing.readDomain(), "function-argument",
                enclosing.ordinal(), declaration, next, List.of(), activeMappings, activeExpressions);
        List<String> why = new ArrayList<>();
        for (int i = firstUse; i < c.mappings.size(); i++) why.addAll(c.mappings.get(i).reasons());
        for (int i = firstDeclaration; i < c.declarations.size(); i++) why.addAll(c.declarations.get(i).reasons());
        return distinct(why);
    }

    private void use(Link ownership, Endpoint endpoint, Locator owner, String role, String readDomain, String writeDomain,
            Integer parent, Integer parentDeclaration, BpmDataContext.Trace trace, List<String> initialReasons,
            Set<Locator> activeMappings, Set<Locator> activeExpressions) {
        int ordinal = c.mappings.size() + 1;
        List<String> why = new ArrayList<>(initialReasons);
        Element mapping = c.unique(endpoint);
        trace.endpoint(endpoint);
        if (endpoint != null) endpoint.candidates().forEach(candidate -> c.usedElements.add(candidate.location()));
        if (mapping == null || !Galaxy.MAPPING.equals(mapping.metaclass())) why.add("mapping-endpoint-unknown");
        boolean cycle = mapping != null && activeMappings.contains(mapping.location());
        if (cycle) why.add("mapping-context-cycle");
        if (mapping != null) {
            c.usedElements.add(mapping.location()); trace.follow(mapping);
            if (c.metadata(mapping, trace)) why.add("configuration-semantics-unknown");
        }
        List<Link> roots = mapping == null ? List.of() : c.links(Galaxy.MAPPING_PARTS, mapping);
        if (roots.isEmpty()) why.add("missing-mapping-roots");
        MappingUse use = new MappingUse(ordinal, parent, parentDeclaration, ownership, endpoint, owner,
                role, readDomain, writeDomain, trace.build(), why);
        c.mappings.add(use);
        for (String reason : why) c.problem(reason, mapping == null ? ownership == null ? null : ownership.location() : mapping.location());
        if (mapping == null || !Galaxy.MAPPING.equals(mapping.metaclass()) || cycle) return;
        Set<Locator> active = new HashSet<>(activeMappings); active.add(mapping.location());
        for (Link root : roots) {
            BpmDataContext.Trace next = c.new Trace(trace); next.follow(root);
            part(ordinal, root.first(), List.of(), next, new HashSet<>(), false, active, activeExpressions);
        }
    }

    private void part(int mapping, Endpoint endpoint, List<Name> parents, BpmDataContext.Trace trace,
            Set<Locator> activeParts, boolean disabledAncestor, Set<Locator> activeMappings, Set<Locator> activeExpressions) {
        MappingUse use = c.mappings.get(mapping - 1);
        int ordinal = c.declarations.size() + 1;
        c.declarations.add(null); // Reserve the outer assignment before its nested argument declarations.
        List<String> why = new ArrayList<>();
        List<Name> path = new ArrayList<>(parents);
        Element part = c.unique(endpoint);
        trace.endpoint(endpoint);
        endpoint.candidates().forEach(candidate -> c.usedElements.add(candidate.location()));
        boolean valid = part != null && Galaxy.MAPPING_PART.equals(part.metaclass());
        boolean cycle = part != null && activeParts.contains(part.location());
        if (part != null) { trace.follow(part); c.usedElements.add(part.location()); }
        if (!valid || cycle) {
            why.add(cycle ? "mapping-part-cycle" : "mapping-part-endpoint-unknown");
            path.add(new Name(List.of(), List.of(), "missing"));
            Binding target = use.role().equals("initial") ? target(use, path.getFirst())
                    : new Binding("unknown", List.of(), why);
            why.addAll(target.reasons());
            c.declarations.set(ordinal - 1, new Declaration(ordinal, mapping, endpoint, path, trace.build(),
                    target, "unknown", "unknown", distinct(why)));
            c.problem(why.getFirst(), part == null ? null : part.location());
            return;
        }
        Name name = c.name(part, Galaxy.PART_QNAME, trace); path.add(name);
        if (!name.state().equals("resolved")) why.add("target-path-name-unknown");
        if (c.metadata(part, trace)) why.add("configuration-semantics-unknown");
        if (use.reasons().contains("configuration-semantics-unknown")) why.add("configuration-semantics-unknown");
        String enabled = part.attributes().get("enabled");
        if (!"true".equals(enabled) && !"false".equals(enabled)) why.add("enabled-unknown");
        if (disabledAncestor) why.add("disabled-ancestor-unknown");
        Binding target = target(use, path.getFirst());
        why.addAll(target.reasons());
        BpmDataExpressions.Result value = expressions.part(part, use, ordinal, trace, activeMappings, activeExpressions);
        why.addAll(value.reasons());
        String candidacy;
        if ("false".equals(enabled)) candidacy = "disabled";
        else if (value.kind().equals("structural")) candidacy = "structural";
        else if (external(use.writeDomain())) candidacy = "external";
        else if (value.kind().equals("expression") && use.writeDomain().equals("process-context")
                && (target.state().equals("owned") || target.state().equals("lexical-candidates"))) candidacy = "candidate";
        else candidacy = "unknown";
        if (candidacy.equals("unknown")) why.add("write-candidacy-unknown");
        c.declarations.set(ordinal - 1, new Declaration(ordinal, mapping, endpoint, path, trace.build(), target,
                value.kind(), candidacy, distinct(why)));
        for (String reason : distinct(why)) c.problem(reason, part.location());
        Set<Locator> active = new HashSet<>(activeParts); active.add(part.location());
        for (Link child : c.links(Galaxy.PART_CHILDREN, part)) {
            BpmDataContext.Trace next = c.new Trace(trace); next.follow(child);
            part(mapping, child.first(), path, next, active, disabledAncestor || "false".equals(enabled), activeMappings, activeExpressions);
        }
    }

    private Binding target(MappingUse use, Name name) {
        if (use.role().equals("initial")) {
            List<Integer> ids = c.objects.stream().filter(o -> o.source().location().equals(use.rootOwner())).map(ObjectData::ordinal).toList();
            return new Binding(ids.size() == 1 ? "owned" : "unknown", ids, ids.size() == 1 ? List.of() : List.of("initial-owner-unknown"));
        }
        if (external(use.writeDomain())) return new Binding("external", List.of(), List.of());
        Binding lexical = c.bind(name, use.rootOwner());
        return use.writeDomain().equals("process-context") ? lexical
                : new Binding("unknown", lexical.objects(), List.of("write-domain-unknown"));
    }

    static boolean external(String domain) { return Set.of("external-input", "function-argument", "role").contains(domain); }
    static List<String> distinct(List<String> values) { return List.copyOf(new java.util.LinkedHashSet<>(values)); }

    private static String[] domains(Element owner, String relation) {
        if (owner == null) return new String[]{"unknown", "unknown", "unknown"};
        String kind = owner.metaclass();
        if (Galaxy.VIEW_MAPPING.equals(relation) && BpmDataContext.dataObject(owner)) return new String[]{"initial", "unknown", "process-context"};
        if (Galaxy.VIEW_MAPPING.equals(relation) && Set.of(Galaxy.PROCESS_ROLE, Galaxy.LANE_PROCESS_ROLE).contains(kind))
            return new String[]{"role-assignment", "unknown", "role"};
        if (Galaxy.EMBEDDED_SCOPE.equals(kind)) return new String[]{"unsupported-embedded", "unknown", "unknown"};
        if (Galaxy.MAPPING_ACTIVITY.equals(kind) && !Galaxy.VIEW_MAPPING.equals(relation)) return new String[]{"mapping", "process-context", "process-context"};
        if (Galaxy.INPUT_MAPPING.equals(relation) && Set.of(Galaxy.AUTOMATED_ACTIVITY, Galaxy.NOTIFICATION_ACTIVITY, Galaxy.END_EVENT).contains(kind))
            return new String[]{"input", "process-context", "external-input"};
        if (Galaxy.OUTPUT_MAPPING.equals(relation) && Set.of(Galaxy.AUTOMATED_ACTIVITY, Galaxy.NOTIFICATION_ACTIVITY, Galaxy.START_EVENT, Galaxy.BOUNDARY_EVENT).contains(kind))
            return new String[]{"output", "external-output", "process-context"};
        return new String[]{"unknown", "unknown", "unknown"};
    }
}
