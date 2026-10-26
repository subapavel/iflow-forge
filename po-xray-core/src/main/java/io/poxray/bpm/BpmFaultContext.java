package io.poxray.bpm;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmFaultReport.*;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.*;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One input/component identity domain; positive scope ownership precedes partition fallback. */
final class BpmFaultContext {
    final int input;
    final int componentOrdinal;
    final BpmComponent component;
    final BpmSourceEvidence source;
    final Map<Locator, Element> elements = new LinkedHashMap<>();
    final List<Member> members = new ArrayList<>();
    final Set<Locator> usedDefinitions = new HashSet<>();
    final Set<Locator> usedLinks = new HashSet<>();
    final List<Unattributed> unattributed;
    final List<Issue> issues;

    static final class Member {
        final ProcessPaths paths;
        final BpmPathGraph.ScopeView scopes;
        final List<Declaration> declarations = new ArrayList<>();
        final Set<String> reasons = new LinkedHashSet<>();

        Member(BpmComponent component, Process process, ProcessPaths paths) {
            this.paths = paths;
            scopes = paths.coverage().equals("unavailable") ? null : BpmPathGraph.scopeView(component, process, paths.key());
            if (!paths.coverage().equals("complete")) {
                reasons.addAll(paths.reasons()); reasons.add("incomplete-path-graph");
            }
        }
        boolean available() { return scopes != null; }
        String state(Locator at) { return available() ? scopes.states().getOrDefault(at, "unknown") : "unknown"; }
        boolean local(Locator at) { return Objects.equals(paths.source(), BpmPathGraph.sourcePath(at)); }
        ProcessFaults report() {
            return new ProcessFaults(paths.key(), paths.dc(), paths.id(), paths.name(), paths.source(),
                    !available() ? "unavailable" : reasons.isEmpty() ? "complete" : "incomplete", declarations, List.copyOf(reasons));
        }
    }

    BpmFaultContext(int input, int ordinal, BpmComponent component, List<Unattributed> unattributed, List<Issue> issues) {
        this.input = input; componentOrdinal = ordinal; this.component = component;
        source = component.sourceEvidence(); this.unattributed = unattributed; this.issues = issues;
        if (valid()) source.elements().forEach(e -> elements.put(e.location(), e));
    }

    boolean valid() { return source != null && source.formatVersion() == 1 && "bpm-source-v1".equals(source.selectionVersion()); }

    Element unique(Endpoint endpoint) {
        if (endpoint == null || !endpoint.resolution().equals("unique") || endpoint.candidates().size() != 1) return null;
        Candidate candidate = endpoint.candidates().getFirst();
        return candidate.included() ? elements.get(candidate.location()) : null;
    }

    static boolean touches(Endpoint endpoint, Locator at) {
        return endpoint != null && endpoint.candidates().stream().anyMatch(c -> c.location().equals(at));
    }

    static boolean overlaps(Endpoint first, Endpoint second) {
        return first != null && second != null && first.candidates().stream().anyMatch(c -> touches(second, c.location()));
    }

    List<Element> candidates(Endpoint endpoint) {
        return endpoint == null ? List.of() : source.elements().stream().filter(e -> touches(endpoint, e.location())).toList();
    }

    List<Link> links(String association, Element owner) {
        return source.links().stream().filter(l -> association.equals(l.association()) && touches(l.second(), owner.location())).toList();
    }

    List<Member> physical(Locator at) {
        List<Member> matches = members.stream().filter(m -> m.local(at)).toList();
        return matches.size() == 1 && matches.getFirst().available() ? matches : List.of();
    }

    List<Member> owners(Endpoint owner, Locator fallback) {
        Set<Member> result = new LinkedHashSet<>();
        if (owner != null) {
            for (Candidate candidate : owner.candidates()) {
                for (Member member : members) if (!member.state(candidate.location()).equals("unknown")) result.add(member);
            }
            // Ambiguity cannot discard a possible process merely because its ancestry is malformed.
            if (owner.candidates().size() > 1) for (Candidate candidate : owner.candidates()) {
                for (Member member : members) if (member.available() &&
                        (member.scopes.members().contains(candidate.location()) || member.local(candidate.location()))) result.add(member);
            }
            if (result.isEmpty()) for (Candidate candidate : owner.candidates()) result.addAll(physical(candidate.location()));
        }
        if (result.isEmpty()) result.addAll(physical(fallback));
        return members.stream().filter(result::contains).toList();
    }

    void problem(Member member, String reason, Locator at) {
        member.reasons.add(reason);
        issues.add(new Issue(Issue.WARNING, "BPM_FAULT_UNCERTAIN", member.paths.key() + ": " + reason + "; occurrence=" + at,
                new Issue.Location(component.dc(), member.paths.id(), null, String.valueOf(at)),
                at == null ? member.paths.source() : BpmPathGraph.sourcePath(at)));
    }

    void unassigned(String kind, BpmFaultEvidence trace, String reason, boolean poison) {
        unattributed.add(new Unattributed(input, componentOrdinal, component.dc(), kind, trace.build(), List.of(reason)));
        issues.add(new Issue(Issue.WARNING, "BPM_FAULT_UNATTRIBUTED_SOURCE",
                "Input " + input + ", component " + componentOrdinal + ": " + kind + "; " + reason,
                new Issue.Location(component.dc(), null, null, null), null));
        if (poison) members.stream().filter(Member::available).forEach(m -> m.reasons.add("unattributed-fault-source"));
    }

    static boolean definition(Element element) {
        return element.metaclass().startsWith("Galaxy.Workflow.") && element.metaclass().endsWith("EventDefinition");
    }

    static List<String> distinct(List<String> values) { return List.copyOf(new LinkedHashSet<>(values)); }
}
