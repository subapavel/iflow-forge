package io.poxray.bpm;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BpmFaultReport.*;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure occurrence-qualified source declarations; runtime handling is deliberately not inferred. */
public final class BpmFaultAnalysis {
    private BpmFaultAnalysis() { }

    public static BpmFaultReport of(List<ScaInventory> inventories, List<String> labels) {
        BpmPathReport paths = BpmPathAnalysis.of(inventories, labels);
        List<Issue> issues = new ArrayList<>();
        for (ScaInventory inventory : inventories) issues.addAll(orEmpty(inventory.issues()));
        issues.addAll(paths.issues());
        Map<ProcessKey, ProcessPaths> byKey = new LinkedHashMap<>();
        paths.processes().forEach(p -> byKey.put(p.key(), p));
        List<ProcessFaults> processes = new ArrayList<>();
        List<Unattributed> unattributed = new ArrayList<>();
        for (int input = 0; input < inventories.size(); input++) {
            List<BpmComponent> components = orEmpty(inventories.get(input).bpm());
            for (int ordinal = 0; ordinal < components.size(); ordinal++) {
                BpmComponent component = components.get(ordinal);
                if (component == null) continue;
                BpmFaultContext c = new BpmFaultContext(input + 1, ordinal + 1, component, unattributed, issues);
                List<Process> members = orEmpty(component.processes());
                for (int p = 0; p < members.size(); p++) {
                    Process process = members.get(p);
                    if (process == null) continue;
                    ProcessPaths path = byKey.get(new ProcessKey(input + 1, ordinal + 1, p + 1));
                    c.members.add(new BpmFaultContext.Member(component, process, path));
                }
                if (c.valid()) new BpmFaultDeclarations(c).read();
                c.members.forEach(m -> processes.add(m.report()));
            }
        }
        return new BpmFaultReport(1, "bpm-faults-v1", paths.inputs(), processes, unattributed, issues);
    }

    public static String markdown(BpmFaultReport report) {
        if (report == null) throw new IllegalArgumentException("A fault report is required");
        StringBuilder out = new StringBuilder("# BPM source event and fault declarations\n\n");
        out.append("Coverage describes retained source evidence only. Declarations and structural attachment do not establish runtime handler selection, ")
                .append("propagation, interruption, retries, transactions, termination, a synchronous response or acknowledgement. ")
                .append("Conditions are unevaluated; absent fault references mean none declared in this evidence.\n\n");
        out.append("| Input | Label | SHA256 |\n| --- | --- | --- |\n");
        for (var input : report.inputs()) if (input != null) row(out, input.ordinal(), input.label(), input.sha256());
        for (ProcessFaults process : report.processes()) {
            if (process == null) continue;
            out.append("\n## Process ").append(escape(process.key())).append(" — ").append(escape(process.name())).append("\n\n");
            out.append("Source: ").append(escape(process.source())).append("; coverage: ").append(escape(process.coverage()))
                    .append("; reasons: ").append(escape(process.reasons())).append(".\n\n");
            out.append("| Declaration | Role | Owner | Event | Definition | State | Reasons |\n| --- | --- | --- | --- | --- | --- | --- |\n");
            for (Declaration declaration : process.declarations()) {
                if (declaration == null) continue;
                row(out, declaration.ordinal(), declaration.role(), endpoint(declaration.owner()), endpoint(declaration.event()),
                        endpoint(declaration.definition()), declaration.state(), declaration.reasons());
                for (Scope scope : declaration.scopes()) if (scope != null)
                    out.append("\nScope ").append(escape(scope.owner())).append(": ").append(escape(scope.state()))
                            .append("; ").append(escape(scope.origin())).append("; path ").append(escape(scope.path())).append(".\n");
                for (Definition definition : declaration.definitions()) if (definition != null) {
                    out.append("\nDefinition ").append(escape(definition.source() == null ? null : definition.source().id()))
                            .append(": ").append(escape(definition.kind())).append("; critical ").append(escape(definition.critical()))
                            .append("; technical ").append(escape(definition.technical())).append(".\n");
                    for (FaultReference fault : definition.faults()) if (fault != null)
                        out.append("\nFault reference ").append(fault.ordinal()).append(": ")
                                .append(escape(fault.link() == null ? null : endpoint(fault.link().first())))
                                .append("; ").append(escape(fault.state())).append("; ").append(escape(fault.reasons())).append(".\n");
                }
                if (declaration.builtin() != null) out.append("\nCatalogue substitution: ").append(escape(declaration.builtin())).append(".\n");
                for (var condition : declaration.conditions()) if (condition != null)
                    out.append("\nCondition: ").append(escape(condition.state())).append("; ").append(escape(condition.text()))
                            .append("; ").append(escape(condition.reasons())).append(".\n");
            }
        }
        out.append("\n## Unattributed evidence\n\n| Input | Component | DC | Kind | Reasons |\n| --- | --- | --- | --- | --- |\n");
        for (Unattributed item : report.unattributed()) if (item != null) row(out, item.input(), item.component(), item.dc(), item.kind(), item.reasons());
        out.append("\n## Diagnostics\n\n| Code | Message |\n| --- | --- |\n");
        for (Issue issue : report.issues()) if (issue != null) row(out, issue.code(), issue.message());
        return out.toString();
    }

    private static String endpoint(Endpoint endpoint) {
        return endpoint == null ? null : endpoint.id() + " (" + endpoint.resolution() + ")";
    }
    private static void row(StringBuilder out, Object... cells) {
        out.append('|'); for (Object cell : cells) out.append(' ').append(escape(cell)).append(" |"); out.append('\n');
    }
    private static String escape(Object value) {
        if (value == null) return "—";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "\\\\").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
                .replace("`", "\\`").replace("*", "\\*").replace("_", "\\_").replace("\r", " ").replace("\n", " ");
    }
    private static <T> List<T> orEmpty(List<T> list) { return list == null ? List.of() : list; }
}
