package io.poxray.bpm;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmPathReport.Input;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure structural analysis of occurrence evidence, deliberately independent of runtime semantics. */
public final class BpmPathAnalysis {
    private BpmPathAnalysis() { }

    public static BpmPathReport of(List<ScaInventory> inventories, List<String> labels) {
        if (inventories == null || labels == null || inventories.size() != labels.size()
                || inventories.stream().anyMatch(i -> i == null) || labels.stream().anyMatch(l -> l == null)) {
            throw new IllegalArgumentException("One non-null label is required per non-null inventory");
        }
        List<Input> inputs = new ArrayList<>();
        List<ProcessPaths> processes = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < inventories.size(); i++) {
            ScaInventory inventory = inventories.get(i);
            inputs.add(new Input(i + 1, labels.get(i), inventory.source() == null ? null : inventory.source().sha256()));
            List<BpmComponent> components = orEmpty(inventory.bpm());
            for (int c = 0; c < components.size(); c++) {
                BpmComponent component = components.get(c);
                if (component == null) {
                    issues.add(new Issue(Issue.WARNING, "BPM_PATH_COMPONENT_UNAVAILABLE",
                            "Input " + (i + 1) + ", component " + (c + 1) + " is absent", null, null));
                    continue;
                }
                List<Process> members = orEmpty(component.processes());
                for (int p = 0; p < members.size(); p++) {
                    Process process = members.get(p);
                    ProcessKey key = new ProcessKey(i + 1, c + 1, p + 1);
                    if (process == null) {
                        issues.add(new Issue(Issue.WARNING, "BPM_PATH_PROCESS_UNAVAILABLE", key + " is absent", null, null));
                        continue;
                    }
                    String unavailable = unavailable(component.sourceEvidence(), process);
                    processes.add(unavailable == null ? new BpmPathGraph(component, process, key, issues).read()
                            : unavailable(component, process, key, unavailable));
                }
                uninterpreted(component, i + 1, c + 1, issues);
            }
        }
        return new BpmPathReport(1, "bpm-paths-v1", inputs, processes, issues);
    }

    private static String unavailable(BpmSourceEvidence evidence, Process process) {
        if (evidence == null) return "source-evidence-absent";
        if (evidence.formatVersion() != 1 || !"bpm-source-v1".equals(evidence.selectionVersion())) {
            return "unsupported-source-evidence-version";
        }
        if (process.source() == null || evidence.elements().stream().noneMatch(e ->
                process.source().equals(BpmPathGraph.sourcePath(e.location())))) return "unresolvable-process-source";
        return null;
    }

    private static ProcessPaths unavailable(BpmComponent component, Process process, ProcessKey key, String reason) {
        List<Step> steps = new ArrayList<>();
        for (var node : orEmpty(process.nodes())) {
            if (node == null) continue;
            steps.add(new Step(steps.size() + 1, null, node.id(), node.kind(), node.name(), List.of(),
                    "unknown", List.of(), BpmPathConditions.absentFlag(), List.of(reason)));
        }
        return new ProcessPaths(key, component.dc(), process.id(), process.name(), process.source(),
                "unavailable", List.of(reason), steps, List.of(), List.of());
    }

    private static void uninterpreted(BpmComponent component, int input, int ordinal, List<Issue> issues) {
        BpmSourceEvidence evidence = component.sourceEvidence();
        if (evidence == null) return;
        Set<String> archives = new LinkedHashSet<>();
        evidence.elements().forEach(e -> archives.add(e.location().archive()));
        evidence.links().forEach(l -> archives.add(l.location().archive()));
        for (String partition : evidence.processPartitions()) {
            Set<String> sources = new LinkedHashSet<>();
            evidence.elements().stream().filter(e -> partition.equals(e.location().partition()))
                    .forEach(e -> sources.add(BpmPathGraph.sourcePath(e.location())));
            evidence.links().stream().filter(l -> partition.equals(l.location().partition()))
                    .forEach(l -> sources.add(BpmPathGraph.sourcePath(l.location())));
            if (sources.isEmpty()) for (String archive : archives) sources.add(archive + "!/" + partition);
            if (sources.isEmpty()) sources.add(partition);
            for (String source : sources) {
                if (orEmpty(component.processes()).stream().noneMatch(p -> p != null && source.equals(p.source()))) {
                    issues.add(new Issue(Issue.WARNING, "BPM_PATH_UNINTERPRETED_PARTITION",
                            "Input " + input + ", component " + ordinal + ": candidate process partition has no interpreted process",
                            new Issue.Location(component.dc(), null, null, source), source));
                }
            }
        }
    }

    public static String markdown(BpmPathReport report) {
        StringBuilder out = new StringBuilder("# BPM structural paths\n\n");
        out.append("Coverage describes only the supported graph in retained parsed evidence. Connected means a structural witness, ")
                .append("not runtime execution, guaranteed feasibility, fault selection or loop completion. Conditions do not prune paths. ")
                .append("Embedded exits are omitted: child effects at parent successors remain unknown without a separately justified completion relation.\n\n");
        table(out, "Input", "Label", "SHA256");
        for (Input input : report.inputs()) row(out, input.ordinal(), input.label(), input.sha256());
        for (ProcessPaths process : report.processes()) {
            out.append("\n## Process ").append(cell(process.key())).append("\n\n");
            table(out, "DC", "ID", "Name", "Source", "Coverage", "Reasons");
            row(out, process.dc(), process.id(), process.name(), process.source(), process.coverage(), process.reasons());
            out.append("\n### Steps\n\n");
            table(out, "Step", "Occurrence", "ID", "Kind", "Name", "Scope", "Structural status", "Witness (arc:kind)", "Terminate", "Reasons");
            for (Step step : process.steps()) {
                String witness = step.witness().stream().map(n -> n + ":" + process.arcs().get(n - 1).kind()).collect(Collectors.joining(" → "));
                row(out, step.ordinal(), step.location(), step.id(), step.kind(), step.name(), step.scopes(),
                        step.connectivity(), witness, flag(step.terminate()), step.reasons());
            }
            out.append("\n### Arcs\n\n");
            table(out, "Arc", "Kind", "Occurrence", "ID", "Sources", "Targets", "Resolution", "Condition", "Text", "Default", "Evidence", "Reasons");
            for (Arc arc : process.arcs()) row(out, arc.ordinal(), arc.kind(), arc.location(), arc.id(), arc.sources(), arc.targets(),
                    arc.resolution(), arc.condition().state(), arc.condition().text(), flag(arc.isDefault()),
                    arc.evidence() + "; condition=" + arc.condition().evidence(), arc.reasons() + "; condition=" + arc.condition().reasons());
            out.append("\n### Declared loops\n\n");
            table(out, "Occurrence", "ID", "Hosts", "Mode", "Sequential", "Flow condition", "Completion", "Evidence", "Reasons");
            for (var loop : process.loops()) row(out, loop.location(), loop.id(), loop.hosts(), loop.mode(),
                    flag(loop.sequential()), loop.flowCondition(), loop.completion(), loop.evidence(), loop.reasons());
        }
        out.append("\n## Issues\n\n");
        table(out, "Code", "Source", "Location", "Evidence and uncertainty");
        for (Issue issue : report.issues()) row(out, issue.code(), issue.source(), issue.location(), issue.message());
        return out.toString();
    }

    private static String flag(Flag flag) {
        return (flag.present() ? "present raw='" + flag.value() + "'" : "missing raw=null") + " interpretation=" + flag.interpretation();
    }

    private static void table(StringBuilder out, String... headings) {
        row(out, (Object[]) headings);
        for (int i = 0; i < headings.length; i++) out.append("| --- ");
        out.append("|\n");
    }

    private static void row(StringBuilder out, Object... cells) {
        for (Object value : cells) out.append("| ").append(cell(value)).append(' ');
        out.append("|\n");
    }

    private static String cell(Object value) {
        if (value == null) return "null";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "\\\\").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
                .replace("\r\n", "<br>").replace("\r", "<br>").replace("\n", "<br>")
                .replace("`", "\\`").replace("*", "\\*").replace("_", "\\_");
    }

    private static <T> List<T> orEmpty(List<T> list) { return list == null ? List.of() : list; }
}
