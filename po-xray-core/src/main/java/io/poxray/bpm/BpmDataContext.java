package io.poxray.bpm;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.*;
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

/** Per-process occurrence identity, lexical visibility and self-contained evidence. */
final class BpmDataContext {
    static final Set<String> OWNERSHIP = Set.of(Galaxy.INPUT_MAPPING, Galaxy.OUTPUT_MAPPING, Galaxy.VIEW_MAPPING);
    private static final Set<String> EXPLICIT = Set.of(Galaxy.INPUT_MAPPING, Galaxy.OUTPUT_MAPPING, Galaxy.VIEW_MAPPING,
            Galaxy.MAPPING_PARTS, Galaxy.PART_CHILDREN, Galaxy.PART_QNAME, Galaxy.PART_EXPRESSION,
            Galaxy.INVOCATION_PARAMETERS);
    private static final Set<String> CONFIG = Set.of(Galaxy.MAPPING_PART_CONFIGURATION, Galaxy.NS_PREFIX, Galaxy.PROPERTY_BAG);
    final BpmComponent component;
    final Process process;
    final ProcessPaths paths;
    final BpmSourceEvidence source;
    final BpmPathGraph.ScopeView scopes;
    final Map<Locator, Element> elements = new LinkedHashMap<>();
    final List<Issue> issues;
    final List<ObjectData> objects = new ArrayList<>();
    final List<MappingUse> mappings = new ArrayList<>();
    final List<Declaration> declarations = new ArrayList<>();
    final List<Read> reads = new ArrayList<>();
    final Set<String> reasons = new LinkedHashSet<>();
    final Set<String> global = new LinkedHashSet<>();
    final Set<Locator> usedElements;
    final Set<Locator> usedLinks;
    List<BpmDataContext> peers = List.of();

    BpmDataContext(BpmComponent component, Process process, ProcessPaths paths, List<Issue> issues,
            Set<Locator> usedElements, Set<Locator> usedLinks) {
        this.component = component; this.process = process; this.paths = paths; this.issues = issues;
        this.usedElements = usedElements; this.usedLinks = usedLinks;
        source = component.sourceEvidence();
        if (!paths.coverage().equals("unavailable")) {
            source.elements().forEach(e -> elements.put(e.location(), e));
            scopes = BpmPathGraph.scopeView(component, process, paths.key());
        } else scopes = null;
        if (!paths.coverage().equals("complete")) {
            reasons.add("incomplete-path-graph"); global.add("incomplete-path-graph");
        }
    }

    boolean available() { return scopes != null; }
    boolean local(Locator locator) { return Objects.equals(process.source(), BpmPathGraph.sourcePath(locator)); }
    String scopeState(Locator locator) { return scopes.states().getOrDefault(locator, "unknown"); }
    List<Locator> scopePath(Locator locator) { return scopes.paths().getOrDefault(locator, List.of()); }
    boolean belongs(Locator locator) { return !scopeState(locator).equals("unknown"); }
    boolean physical(Locator locator) {
        return local(locator) && peers.stream().filter(c -> c.local(locator)).count() == 1;
    }

    Element unique(Endpoint endpoint) {
        if (endpoint == null || !"unique".equals(endpoint.resolution()) || endpoint.candidates().size() != 1) return null;
        Candidate candidate = endpoint.candidates().getFirst();
        return candidate.included() ? elements.get(candidate.location()) : null;
    }

    static boolean touches(Endpoint endpoint, Locator locator) {
        return endpoint != null && endpoint.candidates().stream().anyMatch(c -> c.location().equals(locator));
    }

    List<Link> links(String association, Element second) {
        return source.links().stream().filter(l -> association.equals(l.association()) && touches(l.second(), second.location())).toList();
    }

    List<Link> memberships(Element element) {
        return source.links().stream().filter(l -> Galaxy.SCOPE_CONTAINS_OBJECTS.equals(l.association())
                && touches(l.first(), element.location())).toList();
    }

    static Endpoint endpoint(Element element) {
        return new Endpoint(element.id(), "unique", List.of(new Candidate(element.location(), element.metaclass(), true)));
    }

    List<Element> candidates(List<Link> links, boolean first) {
        Set<Locator> locations = new HashSet<>();
        for (Link link : links) for (Candidate candidate : (first ? link.first() : link.second()).candidates()) {
            if (candidate.included()) locations.add(candidate.location());
        }
        return source.elements().stream().filter(e -> locations.contains(e.location())).toList();
    }

    /** Positive owner attribution takes precedence over an ownership link's physical partition. */
    boolean owns(Link link) {
        Set<BpmDataContext> owners = new LinkedHashSet<>();
        for (Candidate candidate : link.second().candidates()) {
            for (BpmDataContext peer : peers) if (peer.available() && peer.belongs(candidate.location())) owners.add(peer);
        }
        if (link.second().candidates().size() > 1) {
            for (Candidate candidate : link.second().candidates()) {
                for (BpmDataContext peer : peers) {
                    if (peer.available() && (peer.scopes.members().contains(candidate.location()) || peer.local(candidate.location()))) owners.add(peer);
                }
            }
            if (!owners.isEmpty()) return owners.contains(this);
        }
        if (!owners.isEmpty()) return owners.contains(this);
        return physical(link.location());
    }

    void collectObjects() {
        for (Element element : source.elements()) {
            if (!dataObject(element)) continue;
            List<BpmDataContext> validated = peers.stream().filter(c -> c.available() && c.belongs(element.location())).toList();
            boolean related = memberships(element).stream().anyMatch(l -> l.second().candidates().stream()
                    .anyMatch(candidate -> belongs(candidate.location())));
            boolean admitted = validated.isEmpty() ? physical(element.location()) || related : validated.contains(this);
            if (!admitted) continue;
            usedElements.add(element.location());
            String state = scopeState(element.location());
            List<String> why = state.equals("unknown") ? List.of("object-scope-unknown") : List.of();
            if (!why.isEmpty()) problem(why.getFirst(), element.location());
            List<Link> types = links(Galaxy.DATA_CONTAINER_TYPE, element);
            objects.add(new ObjectData(objects.size() + 1, element, memberships(element), scopePath(element.location()),
                    types, candidates(types, true), state, why));
        }
    }

    static boolean dataObject(Element element) {
        return Galaxy.DATA_OBJECT.equals(element.metaclass()) || Galaxy.TEXT_VARIABLE.equals(element.metaclass());
    }

    static String name(Element element) { return element.texts().getOrDefault("name", element.attributes().get("name")); }

    Name name(Element subject, String relation, Trace trace) {
        List<Link> links = links(relation, subject);
        for (Link link : links) trace.link(link);
        List<Element> candidates = candidates(links, true);
        String state;
        if (links.isEmpty()) state = "missing";
        else if (links.size() > 1 || links.getFirst().first().candidates().size() > 1
                || links.getFirst().second().candidates().size() > 1) state = "ambiguous";
        else {
            Element qname = unique(links.getFirst().first());
            state = qname != null && Galaxy.QNAME.equals(qname.metaclass())
                    && qname.attributes().get("name") != null && !qname.attributes().get("name").isBlank()
                    ? "resolved" : "invalid";
        }
        return new Name(links, candidates, state);
    }

    Binding bind(Name name, Locator owner) {
        if (name == null || !name.state().equals("resolved")) return new Binding("unknown", List.of(), List.of("root-name-unknown"));
        String lexical = name.candidates().getFirst().attributes().get("name");
        List<Integer> matches = new ArrayList<>();
        boolean ownerUnknown = owner == null || !scopeState(owner).equals("active");
        boolean uncertain = ownerUnknown;
        for (ObjectData object : objects) {
            if (!Objects.equals(lexical, name(object.source()))) continue;
            if (ownerUnknown || object.scopeState().equals("unknown")) {
                matches.add(object.ordinal()); uncertain = true;
            } else if (visible(owner, object)) matches.add(object.ordinal());
        }
        if (uncertain) return new Binding("unknown", matches, List.of("binding-scope-unknown"));
        return new Binding(matches.isEmpty() ? "none-observed" : "lexical-candidates", matches, List.of());
    }

    boolean visible(Locator owner, ObjectData object) {
        return owner != null && scopeState(owner).equals("active") && object.scopeState().equals("active")
                && !object.scopePath().isEmpty() && scopePath(owner).contains(object.scopePath().getFirst());
    }

    List<ObjectData> visible(Locator owner) { return objects.stream().filter(o -> visible(owner, o)).toList(); }

    boolean loopContext(Locator owner) {
        if (owner == null) return false;
        List<Locator> ancestry = new ArrayList<>(scopePath(owner)); ancestry.add(owner);
        return source.links().stream().filter(l -> BpmPathGraph.LOOP_ATTACHMENT.equals(l.association()))
                .anyMatch(l -> l.second().candidates().stream().anyMatch(c -> ancestry.contains(c.location())));
    }

    void problem(String reason, Locator at) {
        reasons.add(reason);
        String sourcePath = at == null ? process.source() : BpmPathGraph.sourcePath(at);
        issues.add(new Issue(Issue.WARNING, "BPM_DATA_UNCERTAIN", paths.key() + ": " + reason + "; occurrence=" + at,
                new Issue.Location(component.dc(), process.id(), null, String.valueOf(at)), sourcePath));
    }

    final class Trace {
        final List<Fact> path = new ArrayList<>();
        final Set<Locator> rawElements = new LinkedHashSet<>();
        final Set<Locator> rawLinks = new LinkedHashSet<>();
        Trace() { }
        Trace(Trace other) { path.addAll(other.path); rawElements.addAll(other.rawElements); rawLinks.addAll(other.rawLinks); }
        void element(Element element) { if (element != null) rawElements.add(element.location()); }
        void endpoint(Endpoint endpoint) {
            if (endpoint != null) for (Candidate candidate : endpoint.candidates()) if (candidate.included()) rawElements.add(candidate.location());
        }
        void link(Link link) { rawLinks.add(link.location()); endpoint(link.first()); endpoint(link.second()); }
        void follow(Link link) { path.add(new Fact("link", link.location())); link(link); }
        void follow(Element element) { path.add(new Fact("element", element.location())); element(element); }
        void merge(Trace other) { path.addAll(other.path); rawElements.addAll(other.rawElements); rawLinks.addAll(other.rawLinks); }
        Evidence build() {
            return new Evidence(path, source.elements().stream().filter(e -> rawElements.contains(e.location())).toList(),
                    source.links().stream().filter(l -> rawLinks.contains(l.location())).toList());
        }
    }

    /** Configuration payload is retained but never interpreted as an effective inherited flag. */
    boolean metadata(Element subject, Trace trace) {
        boolean unknown = false;
        ArrayDeque<Element> queue = new ArrayDeque<>(); queue.add(subject);
        Set<Locator> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Element next = queue.removeFirst();
            if (!visited.add(next.location())) continue;
            trace.element(next);
            for (Link link : source.links()) {
                if (EXPLICIT.contains(link.association()) || !(touches(link.first(), next.location()) || touches(link.second(), next.location()))) continue;
                unknown = true; trace.link(link);
                for (Endpoint end : List.of(link.first(), link.second())) for (Candidate candidate : end.candidates()) {
                    Element support = elements.get(candidate.location());
                    if (support != null && CONFIG.contains(support.metaclass())) queue.addLast(support);
                }
            }
        }
        return unknown;
    }
}
