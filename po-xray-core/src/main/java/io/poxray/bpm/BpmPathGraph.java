package io.poxray.bpm;

import static io.poxray.bpm.BpmPathConditions.ambiguous;
import static io.poxray.bpm.BpmPathConditions.fact;
import static io.poxray.bpm.BpmPathConditions.touches;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Endpoint;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Loop;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One process-local occurrence graph. Source IDs are never map keys for node resolution. */
final class BpmPathGraph {
    static final String LOOP = "Galaxy.Workflow.MultiInstanceLoopCharacteristics";
    static final String LOOP_ATTACHMENT = "Galaxy.Workflow.EmbeddedScopeContainsLoopCharcateristics";
    private static final Set<String> LOOP_RELATED = Set.of(
            "Galaxy.Workflow.MILoopCharacteristicsContainsExpression",
            "Galaxy.Workflow.MILoopCharacteristicsContainsCurrentCollectionItemDO",
            "Galaxy.Workflow.MILoopCharacteristicsHasNumberOfCompletedIterationsDO");
    private static final Set<String> STRUCTURAL = Set.of(Galaxy.COLLABORATION_CONTAINS_POOLS,
            Galaxy.SCOPE_CONTAINS_OBJECTS, Galaxy.SOURCE_OF_CONNECTORS, Galaxy.TARGET_OF_CONNECTORS,
            Galaxy.ACTIVITY_BOUNDARY_EVENT);
    private static final Set<String> METADATA = Set.of(Galaxy.LANE_HAS_OBJECTS, Galaxy.CONNECTOR_EXPRESSION,
            Galaxy.ACTIVITY_OPERATION, Galaxy.ACTIVITY_SERVICE_REFERENCE, Galaxy.ACTIVITY_EVENT_DEFINITIONS,
            Galaxy.CATCH_EVENT_TRIGGER, Galaxy.CATCH_EVENT_EXPRESSION, Galaxy.THROW_EVENT_RESULT,
            Galaxy.INPUT_MAPPING, Galaxy.OUTPUT_MAPPING, Galaxy.VIEW_MAPPING, Galaxy.DATA_CONTAINER_TYPE,
            Galaxy.ANNOTATION_OF_CONNECTOR, Galaxy.CONNECTOR_MODEL_ELEMENT, LOOP_ATTACHMENT);
    // Pool/Collaboration are not ordinary scope members: retain them as unknown steps when selected.
    private static final Set<String> NON_NODES = Set.of(Galaxy.DATA_OBJECT, Galaxy.TEXT_VARIABLE,
            Galaxy.LANE, Galaxy.PROCESS_ROLE, Galaxy.LANE_PROCESS_ROLE, Galaxy.ANNOTATION,
            Galaxy.ANNOTATION_CONNECTOR);

    private final BpmComponent component;
    private final Process process;
    private final ProcessKey key;
    private final BpmSourceEvidence source;
    private final BpmPathConditions conditions;
    private final List<Issue> issues;
    private final Map<Locator, Element> elements = new LinkedHashMap<>();
    private final Set<Locator> selected = new LinkedHashSet<>();
    private final Set<Locator> scopeLocations = new LinkedHashSet<>();
    private final Set<Locator> pools = new LinkedHashSet<>();
    private final Map<Locator, List<Link>> memberships = new LinkedHashMap<>();
    private final Map<Locator, List<Locator>> scopes = new LinkedHashMap<>();
    private final Set<Locator> invalidMembership = new HashSet<>();
    private final Map<Locator, List<String>> nodeReasons = new LinkedHashMap<>();
    private final LinkedHashSet<String> reasons = new LinkedHashSet<>();
    private final List<Arc> arcs = new ArrayList<>();
    private final Map<Integer, Locator[]> edges = new LinkedHashMap<>();
    private Locator root;

    BpmPathGraph(BpmComponent component, Process process, ProcessKey key, List<Issue> issues) {
        this.component = component;
        this.process = process;
        this.key = key;
        this.issues = issues;
        source = component.sourceEvidence();
        conditions = new BpmPathConditions(source);
        for (Element element : source.elements()) elements.put(element.location(), element);
    }

    /** Snapshot for data visibility; does not change public path extraction or its diagnostics. */
    record ScopeView(Map<Locator, List<Locator>> paths, Map<Locator, String> states, Set<Locator> members) { }

    static ScopeView scopeView(BpmComponent component, Process process, ProcessKey key) {
        BpmPathGraph graph = new BpmPathGraph(component, process, key, new ArrayList<>());
        graph.selectRootAndMembers();
        graph.collectMemberships();
        graph.boundaries();
        Map<Locator, List<Locator>> paths = new LinkedHashMap<>();
        Map<Locator, String> states = new LinkedHashMap<>();
        for (Locator object : graph.elements.keySet()) {
            List<Locator> path = new ArrayList<>();
            if (graph.root != null && graph.validScope(object, new HashSet<>())) {
                Locator cursor = object;
                if (graph.pools.contains(cursor)) path.add(cursor);
                while (!graph.pools.contains(cursor)) {
                    cursor = graph.scopes.get(cursor).getFirst();
                    path.add(cursor);
                }
                states.put(object, graph.inactive(object) ? "inactive" : "active");
            } else states.put(object, "unknown");
            paths.put(object, List.copyOf(path));
        }
        return new ScopeView(java.util.Collections.unmodifiableMap(paths),
                java.util.Collections.unmodifiableMap(states), Set.copyOf(graph.selected));
    }

    ProcessPaths read() {
        selectRootAndMembers();
        collectMemberships();
        List<Boundary> boundaries = boundaries();
        validateMemberships();
        checkRelations();
        for (Element element : orderedSelected()) if (connector(element)) sequence(element);
        for (Boundary boundary : boundaries) boundaryArc(boundary);
        embeddedEntries();
        List<Loop> loops = loops();
        Map<Locator, List<Integer>> witnesses = walk();
        List<Step> steps = new ArrayList<>();
        for (Element element : orderedSelected()) {
            if (!node(element)) continue;
            List<String> why = new ArrayList<>(nodeReasons.getOrDefault(element.location(), List.of()));
            List<Integer> witness = witnesses.get(element.location());
            boolean inactive = inactive(element.location());
            if (inactive) why.add("inactive-pool");
            String status = witness != null ? "connected" : reasons.isEmpty() && !inactive ? "disconnected" : "unknown";
            if (witness == null && !inactive) why.add(status.equals("disconnected") ? "no-structural-path" : "incomplete-graph");
            steps.add(new Step(steps.size() + 1, element.location(), element.id(), kind(element), name(element),
                    scopes.getOrDefault(element.location(), List.of()), status,
                    witness == null ? List.of() : witness, BpmPathConditions.flag(element, "terminate"), why));
        }
        return new ProcessPaths(key, component.dc(), process.id(), process.name(), process.source(),
                reasons.isEmpty() ? "complete" : "incomplete", new ArrayList<>(reasons), steps, arcs, loops);
    }

    private void selectRootAndMembers() {
        List<Element> collaborations = source.elements().stream().filter(e -> local(e.location())
                && Galaxy.COLLABORATION.equals(e.metaclass()) && Objects.equals(process.id(), e.id())).toList();
        boolean valid = collaborations.size() == 1 && process.id() != null && !process.id().isBlank();
        Set<Locator> collaborationObjects = new LinkedHashSet<>();
        for (Link link : source.links()) {
            if (Galaxy.COLLABORATION_CONTAINS_POOLS.equals(link.association())
                    && collaborations.stream().anyMatch(e -> touches(link.second(), e.location()))) {
                link.first().candidates().forEach(c -> collaborationObjects.add(c.location()));
            }
        }
        // A further declaration about one of this collaboration's pools remains attributable,
        // even when its owner is wrong-kind or belongs to another source partition.
        List<Link> poolLinks = source.links().stream().filter(l -> Galaxy.COLLABORATION_CONTAINS_POOLS.equals(l.association())
                && (collaborations.stream().anyMatch(e -> touches(l.second(), e.location()))
                    || touchesAny(l.first(), collaborationObjects)
                    || local(l.location()) && conditions.unique(l.second()) == null)).toList();
        Set<Locator> seen = new HashSet<>();
        int active = 0;
        for (Link link : poolLinks) {
            Element pool = conditions.unique(link.first());
            Element collaboration = conditions.unique(link.second());
            for (var candidate : link.first().candidates()) {
                Element e = elements.get(candidate.location());
                if (e != null && Galaxy.POOL.equals(e.metaclass())) {
                    pools.add(e.location());
                    scopeLocations.add(e.location());
                }
            }
            if (pool == null || collaboration == null || !Galaxy.POOL.equals(pool.metaclass())
                    || collaborations.size() != 1 || !collaboration.location().equals(collaborations.getFirst().location())
                    || !seen.add(pool.location())) {
                valid = false;
                gap("invalid-pool-link", link);
                continue;
            }
            String flag = BpmPathConditions.flag(pool, "active").interpretation();
            if (flag.equals("true")) { active++; root = pool.location(); }
            else if (!flag.equals("false")) valid = false;
        }
        if (!valid || active != 1 || poolLinks.isEmpty()) {
            root = null;
            gap("invalid-root-selection", null);
        }
        // Retain original control occurrences even when the source omitted their membership.
        for (Element element : source.elements()) {
            if (local(element.location()) && (knownNode(element) || connector(element))) selected.add(element.location());
        }
        boolean changed;
        do {
            changed = false;
            for (Locator location : new ArrayList<>(selected)) {
                if (Galaxy.EMBEDDED_SCOPE.equals(elements.get(location).metaclass())) changed |= scopeLocations.add(location);
            }
            for (Link link : source.links()) {
                if (!Galaxy.SCOPE_CONTAINS_OBJECTS.equals(link.association()) || !touchesAny(link.second(), scopeLocations)) continue;
                boolean uniqueRelation = conditions.unique(link.first()) != null && conditions.unique(link.second()) != null;
                for (var candidate : link.first().candidates()) {
                    Element member = elements.get(candidate.location());
                    if (member != null && (local(member.location()) || uniqueRelation)) {
                        changed |= selected.add(member.location());
                    }
                }
            }
        } while (changed);
    }

    private void collectMemberships() {
        for (Link link : source.links()) {
            if (!Galaxy.SCOPE_CONTAINS_OBJECTS.equals(link.association())) continue;
            if (!attributable(link)) continue;
            Element object = conditions.unique(link.first());
            Element scope = conditions.unique(link.second());
            boolean valid = object != null && scope != null && scopeLocations.contains(scope.location());
            if (!valid) gap("unresolved-scope-membership", link);
            for (var candidate : link.first().candidates()) {
                Locator location = candidate.location();
                if (!selected.contains(location) && !pools.contains(location)) continue;
                memberships.computeIfAbsent(location, ignored -> new ArrayList<>()).add(link);
                List<Locator> parents = scopes.computeIfAbsent(location, ignored -> new ArrayList<>());
                for (var parent : link.second().candidates()) parents.add(parent.location());
                if (!valid) invalidMembership.add(location);
            }
        }
        for (var entry : memberships.entrySet()) {
            if (pools.contains(entry.getKey())) {
                invalidMembership.add(entry.getKey());
                gap("unsupported-pool-scope-membership", entry.getValue().getFirst());
            }
            if (entry.getValue().size() != 1) {
                invalidMembership.add(entry.getKey());
                gap("multiple-scope-memberships", entry.getValue().getFirst());
            }
        }
    }

    private record Boundary(Link link, Element owner, Element boundary, String resolution, List<String> reasons) { }

    private List<Boundary> boundaries() {
        List<Link> links = source.links().stream().filter(l -> Galaxy.ACTIVITY_BOUNDARY_EVENT.equals(l.association())
                && attributable(l)).toList();
        List<Boundary> result = new ArrayList<>();
        for (Link link : links) {
            Element owner = conditions.unique(link.second());
            Element boundary = conditions.unique(link.first());
            String resolution = endpointResolution(link);
            List<String> why = new ArrayList<>();
            if (boundary != null && source.links().stream().filter(l -> Galaxy.ACTIVITY_BOUNDARY_EVENT.equals(l.association())
                    && touches(l.first(), boundary.location())).count() != 1) {
                resolution = "ambiguous";
                why.add("multiple-boundary-attachments");
            }
            if (resolution.equals("resolved")) {
                boolean ownerKind = Set.of("automatedActivity", "mappingActivity", "notificationActivity", "embeddedSubprocess")
                        .contains(kind(owner));
                if (!ownerKind || !Galaxy.BOUNDARY_EVENT.equals(boundary.metaclass())
                        || !selected.contains(owner.location()) || !validScope(owner.location(), new HashSet<>())) {
                    resolution = "invalid";
                    why.add("invalid-boundary-owner-or-target");
                } else {
                    List<Locator> ownerScopes = scopes.get(owner.location());
                    // Check all declarations, including external memberships not admitted to this process.
                    List<Link> ownMemberships = source.links().stream().filter(l -> Galaxy.SCOPE_CONTAINS_OBJECTS.equals(l.association())
                            && touches(l.first(), boundary.location())).toList();
                    if (ownMemberships.isEmpty()) {
                        selected.add(boundary.location());
                        scopes.put(boundary.location(), new ArrayList<>(ownerScopes));
                        nodeReasons.computeIfAbsent(boundary.location(), ignored -> new ArrayList<>()).add("boundary-scope-from-owner");
                    } else if (!validScope(boundary.location(), new HashSet<>())
                            || !ownerScopes.equals(scopes.get(boundary.location()))) {
                        resolution = "invalid";
                        why.add("conflicting-boundary-scope");
                    }
                }
            }
            if (!resolution.equals("resolved")) {
                why.add("invalid-boundary-attachment");
                gap("invalid-boundary-attachment", link);
            }
            result.add(new Boundary(link, owner, boundary, resolution, why));
        }
        return result;
    }

    private void validateMemberships() {
        for (Element element : orderedSelected()) {
            if (!node(element) && !connector(element)) continue;
            if (node(element) && !knownNode(element)) gap("unknown-scope-member-kind", firstMembership(element));
            if (!validScope(element.location(), new HashSet<>())) {
                nodeReasons.computeIfAbsent(element.location(), ignored -> new ArrayList<>()).add("invalid-or-missing-scope");
                gap("invalid-or-missing-scope", firstMembership(element));
            }
        }
    }

    /** Recursive ancestry is bounded by the active set; duplicate memberships never choose a parent. */
    private boolean validScope(Locator object, Set<Locator> active) {
        if (invalidMembership.contains(object)) return false;
        if (pools.contains(object)) return true;
        if (!active.add(object)) return false;
        List<Locator> parents = scopes.getOrDefault(object, List.of());
        boolean valid = parents.size() == 1 && scopeLocations.contains(parents.getFirst())
                && validScope(parents.getFirst(), active);
        active.remove(object);
        return valid;
    }

    private Link firstMembership(Element element) {
        List<Link> links = memberships.getOrDefault(element.location(), List.of());
        return links.isEmpty() ? null : links.getFirst();
    }

    private void checkRelations() {
        for (Link link : source.links()) {
            boolean controlTouch = touchesAny(link.first(), controlLocations()) || touchesAny(link.second(), controlLocations());
            if (!STRUCTURAL.contains(link.association()) && !METADATA.contains(link.association()) && controlTouch) {
                reasons.add("unsupported-control-relation");
                issue("BPM_PATH_UNSUPPORTED_CONTROL_RELATION", "unsupported-control-relation", link);
            }
            if (Galaxy.COLLABORATION_CONTAINS_POOLS.equals(link.association()) && attributable(link)) {
                Element pool = conditions.unique(link.first());
                Element owner = conditions.unique(link.second());
                if (pool == null || owner == null || !Galaxy.POOL.equals(pool.metaclass())
                        || !Galaxy.COLLABORATION.equals(owner.metaclass())) {
                    // A malformed unrelated declaration is a coverage gap, not grounds to erase a valid root.
                    gap("unattributed-pool-relation", link);
                }
            }
            if ((Galaxy.SOURCE_OF_CONNECTORS.equals(link.association()) || Galaxy.TARGET_OF_CONNECTORS.equals(link.association()))
                    && attributable(link)) {
                Element connector = conditions.unique(link.second());
                if (connector == null || !connector(connector) || !selected.contains(connector.location())) {
                    gap("unattributed-connector-relation", link);
                }
            }
        }
    }

    private void sequence(Element connector) {
        List<Link> incoming = conditions.links(Galaxy.SOURCE_OF_CONNECTORS, connector);
        List<Link> outgoing = conditions.links(Galaxy.TARGET_OF_CONNECTORS, connector);
        List<Fact> facts = new ArrayList<>(List.of(fact(connector)));
        List<String> why = new ArrayList<>();
        List<Endpoint> sources = endpoints(incoming, facts);
        List<Endpoint> targets = endpoints(outgoing, facts);
        String left = sequenceSide(incoming, "source", why);
        String right = sequenceSide(outgoing, "target", why);
        String resolution = combine(left, right);
        Element from = incoming.size() == 1 ? conditions.unique(incoming.getFirst().first()) : null;
        Element to = outgoing.size() == 1 ? conditions.unique(outgoing.getFirst().first()) : null;
        if (resolution.equals("resolved") && (!validScope(connector.location(), new HashSet<>())
                || !scopes.get(connector.location()).equals(scopes.get(from.location()))
                || !scopes.get(connector.location()).equals(scopes.get(to.location())))) {
            resolution = "invalid";
            why.add("invalid-connector-scope-or-cross-scope-edge");
        }
        if (!resolution.equals("resolved")) gap("invalid-sequence-arc", incoming.isEmpty() ? null : incoming.getFirst());
        int ordinal = arcs.size() + 1;
        arcs.add(new Arc(ordinal, "sequence", connector.location(), connector.id(), sources, targets, resolution,
                conditions.read(connector), BpmPathConditions.flag(connector, "isDefault"), facts, why));
        if (resolution.equals("resolved")) edges.put(ordinal, new Locator[]{from.location(), to.location()});
    }

    private String sequenceSide(List<Link> links, String side, List<String> why) {
        String resolution = "resolved";
        if (links.isEmpty()) resolution = "missing";
        else if (links.size() > 1) resolution = "ambiguous";
        else {
            Link link = links.getFirst();
            resolution = endpointResolution(link);
            Element node = conditions.unique(link.first());
            Element connector = conditions.unique(link.second());
            if (resolution.equals("resolved") && (!knownNode(node) || !selected.contains(node.location())
                    || !connector(connector) || !validScope(node.location(), new HashSet<>()))) resolution = "invalid";
        }
        if (!resolution.equals("resolved")) why.add(side + "-" + resolution);
        return resolution;
    }

    private List<Endpoint> endpoints(List<Link> links, List<Fact> facts) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Link link : links) { endpoints.add(endpoint(link.first())); facts.add(fact(link)); }
        return endpoints;
    }

    private void boundaryArc(Boundary boundary) {
        int ordinal = arcs.size() + 1;
        arcs.add(new Arc(ordinal, "boundary-attachment", null, null,
                List.of(endpoint(boundary.link().second())), List.of(endpoint(boundary.link().first())), boundary.resolution(),
                BpmPathConditions.structural("boundary-attachment"), BpmPathConditions.absentFlag(),
                List.of(fact(boundary.link())), boundary.reasons()));
        if (boundary.resolution().equals("resolved")) {
            edges.put(ordinal, new Locator[]{boundary.owner().location(), boundary.boundary().location()});
        }
    }

    private void embeddedEntries() {
        for (Element host : orderedSelected()) {
            if (!Galaxy.EMBEDDED_SCOPE.equals(host.metaclass())) continue;
            List<Element> starts = orderedSelected().stream().filter(e -> Galaxy.START_EVENT.equals(e.metaclass())
                    && scopes.getOrDefault(e.location(), List.of()).contains(host.location())).toList();
            if (starts.isEmpty()) gap("embedded-scope-without-start", firstMembership(host));
            boolean anyValid = false;
            for (Element start : starts) {
                boolean valid = validScope(host.location(), new HashSet<>()) && validScope(start.location(), new HashSet<>());
                anyValid |= valid;
                int ordinal = arcs.size() + 1;
                List<Fact> facts = new ArrayList<>();
                memberships.getOrDefault(host.location(), List.of()).forEach(l -> facts.add(fact(l)));
                memberships.getOrDefault(start.location(), List.of()).forEach(l -> facts.add(fact(l)));
                arcs.add(new Arc(ordinal, "embedded-entry", null, null, List.of(endpoint(host)), List.of(endpoint(start)),
                        valid ? "resolved" : "invalid", BpmPathConditions.structural("embedded-entry"),
                        BpmPathConditions.absentFlag(), facts, valid ? List.of() : List.of("invalid-embedded-membership")));
                if (valid) edges.put(ordinal, new Locator[]{host.location(), start.location()});
            }
            if (!anyValid) gap("embedded-scope-without-uniquely-scoped-start", firstMembership(host));
        }
    }

    private Map<Locator, List<Integer>> walk() {
        Map<Locator, List<Integer>> witnesses = new LinkedHashMap<>();
        ArrayDeque<Locator> queue = new ArrayDeque<>();
        if (root != null) {
            for (Element element : orderedSelected()) {
                if (Galaxy.START_EVENT.equals(element.metaclass()) && validScope(element.location(), new HashSet<>())
                        && scopes.get(element.location()).equals(List.of(root))) {
                    witnesses.put(element.location(), List.of());
                    queue.add(element.location());
                    nodeReasons.computeIfAbsent(element.location(), ignored -> new ArrayList<>()).add("root-start");
                }
            }
        }
        if (queue.isEmpty()) gap("no-valid-root-start", null);
        Map<Locator, List<Integer>> outgoing = new LinkedHashMap<>();
        edges.forEach((ordinal, ends) -> outgoing.computeIfAbsent(ends[0], ignored -> new ArrayList<>()).add(ordinal));
        while (!queue.isEmpty()) {
            Locator current = queue.removeFirst();
            for (int ordinal : outgoing.getOrDefault(current, List.of())) {
                Locator target = edges.get(ordinal)[1];
                if (witnesses.containsKey(target)) continue;
                List<Integer> path = new ArrayList<>(witnesses.get(current));
                path.add(ordinal);
                witnesses.put(target, path);
                queue.addLast(target);
            }
        }
        return witnesses;
    }

    private List<Loop> loops() {
        Set<Locator> selectedLoops = new LinkedHashSet<>();
        for (Element element : source.elements()) if (local(element.location()) && LOOP.equals(element.metaclass())) selectedLoops.add(element.location());
        List<Link> attachments = new ArrayList<>();
        // Close attribution over selected declarations so cross-partition duplicate attachments are retained.
        boolean changed;
        do {
            changed = false;
            for (Link link : source.links()) {
                if (!LOOP_ATTACHMENT.equals(link.association())) continue;
                if (attributable(link) || touchesAny(link.first(), selectedLoops) || touchesAny(link.second(), selectedLoops)) {
                    if (!attachments.contains(link)) { attachments.add(link); changed = true; }
                    for (var candidate : link.first().candidates()) {
                        Element element = elements.get(candidate.location());
                        if (element != null && LOOP.equals(element.metaclass())) changed |= selectedLoops.add(element.location());
                    }
                }
            }
        } while (changed);
        attachments.sort((a, b) -> Integer.compare(source.links().indexOf(a), source.links().indexOf(b)));
        Map<Link, String> problems = new LinkedHashMap<>();
        for (Link link : attachments) {
            Element loop = conditions.unique(link.first());
            Element host = conditions.unique(link.second());
            String code = null;
            if (ambiguous(link.first()) || ambiguous(link.second())
                    || loop != null && attachments.stream().filter(l -> touches(l.first(), loop.location())).count() != 1
                    || host != null && source.links().stream().filter(l -> LOOP_ATTACHMENT.equals(l.association())
                        && touches(l.second(), host.location())).count() != 1) code = "BPM_PATH_LOOP_AMBIGUOUS";
            else if (loop == null || host == null) code = "BPM_PATH_LOOP_UNRESOLVED";
            else if (!LOOP.equals(loop.metaclass()) || !Galaxy.EMBEDDED_SCOPE.equals(host.metaclass())
                    || !selected.contains(host.location())) code = "BPM_PATH_LOOP_UNSUPPORTED";
            if (code != null) { problems.put(link, code); issue(code, "unknown-loop-attachment", link); }
        }
        List<Loop> result = new ArrayList<>();
        for (Element loop : source.elements()) {
            if (!selectedLoops.contains(loop.location())) continue;
            List<Link> own = attachments.stream().filter(l -> touches(l.first(), loop.location())).toList();
            List<Locator> hosts = new ArrayList<>();
            List<Fact> facts = new ArrayList<>(List.of(fact(loop)));
            List<String> why = new ArrayList<>();
            for (Link link : own) {
                facts.add(fact(link));
                for (var candidate : link.second().candidates()) hosts.add(candidate.location());
                if (problems.containsKey(link)) why.add(problems.get(link));
            }
            for (Link link : source.links()) {
                if (LOOP_RELATED.contains(link.association()) && touches(link.second(), loop.location())) facts.add(fact(link));
            }
            if (own.isEmpty()) why.add("unattached-loop");
            boolean valid = own.size() == 1 && why.isEmpty();
            var flag = BpmPathConditions.flag(loop, "sequential");
            String mode = valid ? switch (flag.interpretation()) {
                case "true" -> "sequential";
                case "false" -> "parallel";
                default -> "unknown";
            } : "unknown";
            String flowCondition = loop.attributes().get("flowCondition");
            String completion = valid && "all".equals(flowCondition) ? "all" : "unknown";
            if (mode.equals("unknown") && valid) why.add("missing-or-invalid-sequential");
            if (completion.equals("unknown") && valid) why.add("unsupported-loop-completion");
            result.add(new Loop(loop.location(), loop.id(), hosts, mode, flag, flowCondition, completion, facts, why));
        }
        return result;
    }

    private boolean inactive(Locator location) {
        Set<Locator> visited = new HashSet<>();
        while (visited.add(location)) {
            if (pools.contains(location)) return "false".equals(BpmPathConditions.flag(elements.get(location), "active").interpretation());
            List<Locator> parents = scopes.getOrDefault(location, List.of());
            if (parents.size() != 1) return false;
            location = parents.getFirst();
        }
        return false;
    }

    private List<Element> orderedSelected() {
        return source.elements().stream().filter(e -> selected.contains(e.location())).toList();
    }

    private Set<Locator> controlLocations() {
        Set<Locator> locations = new HashSet<>(scopeLocations);
        for (Element e : orderedSelected()) if (node(e) || connector(e)) locations.add(e.location());
        return locations;
    }

    private boolean attributable(Link link) {
        Set<Locator> controls = controlLocations();
        return local(link.location()) || touchesAny(link.first(), controls) || touchesAny(link.second(), controls);
    }

    private static boolean touchesAny(BpmSourceEvidence.Endpoint endpoint, Set<Locator> locations) {
        return endpoint.candidates().stream().anyMatch(c -> locations.contains(c.location()));
    }

    private boolean local(Locator location) { return process.source().equals(sourcePath(location)); }

    static String sourcePath(Locator location) { return location.archive() + "!/" + location.partition(); }

    private void gap(String reason, Link link) {
        reasons.add(reason);
        issue("BPM_PATH_GRAPH_INCOMPLETE", reason, link);
    }

    private void issue(String code, String reason, Link link) {
        String message = key + ": " + reason;
        String path = process.source();
        if (link != null) {
            path = sourcePath(link.location()) + "#link[" + link.location().ordinal() + "]";
            message += "; " + link.association() + "; f=" + link.first() + "; s=" + link.second();
        }
        issues.add(new Issue(Issue.WARNING, code, message,
                new Issue.Location(component.dc(), process.id(), null, path), process.source()));
    }

    private static Endpoint endpoint(BpmSourceEvidence.Endpoint endpoint) {
        return new Endpoint(endpoint.id(), endpoint.resolution(), endpoint.candidates().stream().map(c -> c.location()).toList());
    }

    private static Endpoint endpoint(Element element) { return new Endpoint(element.id(), "unique", List.of(element.location())); }

    private String endpointResolution(Link link) {
        if (ambiguous(link.first()) || ambiguous(link.second())) return "ambiguous";
        if (conditions.unique(link.first()) == null || conditions.unique(link.second()) == null) return "missing";
        return "resolved";
    }

    private static String combine(String a, String b) {
        if (a.equals("ambiguous") || b.equals("ambiguous")) return "ambiguous";
        if (a.equals("missing") || b.equals("missing")) return "missing";
        if (a.equals("invalid") || b.equals("invalid")) return "invalid";
        return "resolved";
    }

    private static boolean connector(Element e) {
        return Galaxy.SEQUENCE_CONNECTOR.equals(e.metaclass()) || Galaxy.CONDITIONAL_SEQUENCE_CONNECTOR.equals(e.metaclass());
    }

    private static boolean node(Element e) { return !NON_NODES.contains(e.metaclass()) && !connector(e); }
    private static boolean knownNode(Element e) { return !kind(e).equals("unknown"); }
    private static String name(Element e) { return e.texts().getOrDefault("name", e.attributes().get("name")); }

    private static String kind(Element e) {
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
}
