package io.poxray.bpm;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contextual declared writes and structural candidate provenance in retained parsed BPM evidence. */
public final class BpmDataAnalysis {
    private BpmDataAnalysis() { }

    public static BpmDataReport of(List<ScaInventory> inventories, List<String> labels) {
        // This validates the common input contract and fixes every public step/arc address once.
        BpmPathReport paths = BpmPathAnalysis.of(inventories, labels);
        List<Issue> issues = new ArrayList<>();
        inventories.forEach(i -> issues.addAll(orEmpty(i.issues())));
        issues.addAll(paths.issues());
        List<ProcessData> results = new ArrayList<>();
        for (int input = 0; input < inventories.size(); input++) {
            List<BpmComponent> components = orEmpty(inventories.get(input).bpm());
            for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
                BpmComponent component = components.get(componentIndex);
                if (component == null) continue;
                int inputOrdinal = input + 1, componentOrdinal = componentIndex + 1;
                List<ProcessPaths> componentPaths = paths.processes().stream().filter(p -> p.key().input() == inputOrdinal
                        && p.key().component() == componentOrdinal).toList();
                List<BpmDataContext> contexts = new ArrayList<>();
                Set<Locator> usedElements = new LinkedHashSet<>(), usedLinks = new LinkedHashSet<>();
                for (ProcessPaths path : componentPaths) {
                    Process process = component.processes().get(path.key().process() - 1);
                    contexts.add(new BpmDataContext(component, process, path, issues, usedElements, usedLinks));
                }
                Map<BpmDataContext, BpmDataMappings> extractors = new LinkedHashMap<>();
                for (BpmDataContext context : contexts) {
                    context.peers = List.copyOf(contexts);
                    if (context.available()) { context.collectObjects(); extractors.put(context, new BpmDataMappings(context)); }
                }
                extractors.values().forEach(BpmDataMappings::read);
                unattributed(component, contexts, extractors, inputOrdinal, componentOrdinal, usedElements, usedLinks, issues);
                for (BpmDataContext context : contexts) {
                    ProcessPaths path = context.paths;
                    List<StepState> steps;
                    String coverage;
                    if (!context.available()) {
                        coverage = "unavailable";
                        steps = path.steps().stream().map(s -> new StepState(s.ordinal(), s.location(), "unknown", List.of(), List.of(),
                                List.of("source-evidence-unavailable"))).toList();
                    } else {
                        steps = new BpmDataFlow(context).read();
                        coverage = context.reasons.isEmpty() ? "complete" : "incomplete";
                    }
                    results.add(new ProcessData(path.key(), path.dc(), path.id(), path.name(), path.source(), coverage,
                            List.copyOf(context.reasons), context.objects, context.mappings, context.declarations, context.reads, steps));
                }
            }
        }
        return new BpmDataReport(1, "bpm-data-v1", paths, results, issues);
    }

    private static void unattributed(BpmComponent component, List<BpmDataContext> contexts,
            Map<BpmDataContext, BpmDataMappings> extractors, int input, int componentOrdinal,
            Set<Locator> usedElements, Set<Locator> usedLinks, List<Issue> issues) {
        BpmSourceEvidence source = component.sourceEvidence();
        if (source == null || source.formatVersion() != 1 || !"bpm-source-v1".equals(source.selectionVersion())) return;
        // Discover ownership without emitting rows, then retain source order for unrelated orphans.
        Map<Locator, BpmDataContext> orphanOwners = new LinkedHashMap<>();
        Set<Locator> claimedParts = new LinkedHashSet<>();
        for (Element element : source.elements()) {
            if (!Galaxy.MAPPING.equals(element.metaclass()) || usedElements.contains(element.location())) continue;
            List<BpmDataContext> physical = contexts.stream().filter(c -> c.available() && c.physical(element.location())).toList();
            if (physical.size() == 1) {
                BpmDataContext context = physical.getFirst(); orphanOwners.put(element.location(), context);
                claimParts(context, element, claimedParts);
            }
        }
        for (Element element : source.elements()) {
            if (!(Galaxy.MAPPING.equals(element.metaclass()) || Galaxy.MAPPING_PART.equals(element.metaclass()) || BpmDataContext.dataObject(element))
                    || usedElements.contains(element.location())) continue;
            List<BpmDataContext> physical = contexts.stream().filter(c -> c.available() && c.physical(element.location())).toList();
            if (orphanOwners.containsKey(element.location())) extractors.get(orphanOwners.get(element.location())).orphan(element);
            else if (Galaxy.MAPPING_PART.equals(element.metaclass()) && claimedParts.contains(element.location())) continue;
            else if (physical.size() == 1 && Galaxy.MAPPING_PART.equals(element.metaclass())) extractors.get(physical.getFirst()).orphan(element);
            else unattributedIssue(component, contexts, input, componentOrdinal, "element", element.location(), issues);
        }
        for (Link link : source.links()) {
            if (BpmDataContext.OWNERSHIP.contains(link.association()) && !usedLinks.contains(link.location()))
                unattributedIssue(component, contexts, input, componentOrdinal, "link", link.location(), issues);
        }
    }

    private static void claimParts(BpmDataContext context, Element mapping, Set<Locator> claimed) {
        java.util.ArrayDeque<Element> pending = new java.util.ArrayDeque<>(); pending.add(mapping);
        Set<Locator> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            Element current = pending.removeFirst();
            if (!visited.add(current.location())) continue;
            String relation = Galaxy.MAPPING.equals(current.metaclass()) ? Galaxy.MAPPING_PARTS : Galaxy.PART_CHILDREN;
            for (Link link : context.links(relation, current)) {
                link.first().candidates().forEach(candidate -> claimed.add(candidate.location()));
                Element child = context.unique(link.first());
                if (child != null && Galaxy.MAPPING_PART.equals(child.metaclass())) pending.addLast(child);
            }
        }
    }

    private static void unattributedIssue(BpmComponent component, List<BpmDataContext> contexts, int input, int ordinal,
            String kind, Locator location, List<Issue> issues) {
        String message = "Input " + input + ", component " + ordinal + ", dc=" + component.dc()
                + ": unattributed " + kind + " " + location;
        issues.add(new Issue(Issue.WARNING, "BPM_DATA_UNATTRIBUTED_SOURCE", message,
                new Issue.Location(component.dc(), null, null, kind + ":" + location), BpmPathGraph.sourcePath(location)));
        for (BpmDataContext context : contexts) {
            context.reasons.add("unattributed-component-data"); context.global.add("unattributed-component-data");
        }
    }

    public static String markdown(BpmDataReport report) {
        StringBuilder out = new StringBuilder("# BPM declared data candidates\n\n");
        out.append("These are structural source candidates, never runtime values, execution, definite availability or exhaustive archive coverage. ")
                .append("Empty sets mean no supported declaration was observed on the modeled paths; they do not mean absent data, null or a schema default. ")
                .append("Candidate IDs and uncertainty propagate independently, without destructive overwrites.\n\n");
        table(out, "Input", "Label", "SHA256");
        for (var input : report.paths().inputs()) row(out, input.ordinal(), input.label(), input.sha256());
        for (ProcessData process : report.processes()) {
            out.append("\n## Process ").append(cell(process.key())).append("\n\n");
            table(out, "DC", "ID", "Name", "Source", "Coverage", "Reasons");
            row(out, process.dc(), process.id(), process.name(), process.source(), process.coverage(), process.reasons());
            out.append("\n### Data objects\n\n");
            table(out, "Object", "Raw source", "Membership", "Scope path", "Scope state", "Type evidence", "Reasons");
            for (ObjectData object : process.objects()) row(out, object.ordinal(), object.source(), object.memberships(), object.scopePath(),
                    object.scopeState(), object.types() + "; candidates=" + object.typeCandidates(), object.reasons());
            out.append("\n### Contextual mappings\n\n");
            table(out, "Use", "Parent / assignment", "Owner", "Role", "Read domain", "Write domain", "Ownership", "Mapping", "Evidence", "Reasons");
            for (MappingUse use : process.mappings()) row(out, use.ordinal(), use.parent() + " / " + use.parentDeclaration(), use.rootOwner(),
                    use.role(), use.readDomain(), use.writeDomain(), use.ownership(), use.mapping(), use.evidence(), use.reasons());
            out.append("\n### Declarations\n\n");
            table(out, "Declaration", "Mapping", "Part", "Target path", "Target binding", "Value syntax", "Candidacy", "Evidence", "Reasons");
            for (Declaration declaration : process.declarations()) row(out, declaration.ordinal(), declaration.mapping(), declaration.part(),
                    declaration.targetPath(), declaration.target(), declaration.valueKind(), declaration.candidacy(), declaration.evidence(), declaration.reasons());
            out.append("\n### Reads\n\n");
            table(out, "Read", "Declaration", "Domain", "Binding", "Candidate sets and coverage", "Raw evidence", "Reasons");
            for (Read read : process.reads()) row(out, read.ordinal(), read.declaration(), read.domain(), read.binding(), read.candidates(), read.evidence(), read.reasons());
            out.append("\n### Structural step states\n\n");
            table(out, "Step", "Occurrence", "Connectivity", "Before candidates and coverage", "After candidates and coverage", "Reasons");
            for (StepState step : process.steps()) row(out, step.step(), step.location(), step.connectivity(), step.before(), step.after(), step.reasons());
        }
        out.append("\n## Issues\n\n");
        table(out, "Code", "Source", "Location", "Details");
        for (Issue issue : report.issues()) row(out, issue.code(), issue.source(), issue.location(), issue.message());
        return out.toString();
    }

    private static void table(StringBuilder out, String... headings) {
        row(out, (Object[]) headings);
        for (String ignored : headings) out.append("| --- "); out.append("|\n");
    }
    private static void row(StringBuilder out, Object... values) {
        for (Object value : values) out.append("| ").append(cell(value)).append(' '); out.append("|\n");
    }
    private static String cell(Object value) {
        return value == null ? "null" : value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "\\\\").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
                .replace("\r\n", "<br>").replace("\r", "<br>").replace("\n", "<br>")
                .replace("`", "\\`").replace("*", "\\*").replace("_", "\\_");
    }
    private static <T> List<T> orEmpty(List<T> values) { return values == null ? List.of() : values; }
}
