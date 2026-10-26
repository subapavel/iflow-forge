package io.poxray.migration;

import io.poxray.bpm.BpmDataAnalysis;
import io.poxray.bpm.BpmFaultAnalysis;
import io.poxray.bpm.BpmPathAnalysis;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.*;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline composition of accepted source analysis and configured integration candidates. */
public final class BehaviorAnalysis {
    private BehaviorAnalysis() { }

    public static BehaviorReport of(List<ScaInventory> inventories, List<String> labels, IntegrationInventory icos, String icosLabel) {
        return compose(inventories, labels, icos, icosLabel, false, List.of());
    }
    public static BehaviorReport read(Path exportFileOrDirectory, Path icosJson) throws IOException {
        BehaviorInputs.Loaded input = BehaviorInputs.read(exportFileOrDirectory, icosJson);
        return compose(input.inventories(), input.labels(), input.icos(), input.icosLabel(), input.icoUnavailable(), input.issues());
    }
    private static BehaviorReport compose(List<ScaInventory> inventories, List<String> labels, IntegrationInventory icos,
            String icosLabel, boolean unavailable, List<Issue> diagnostics) {
        if (inventories == null || labels == null || inventories.size() != labels.size()
                || inventories.stream().anyMatch(Objects::isNull) || labels.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Non-null inventories and labels of equal size are required");
        for (ScaInventory inventory : inventories) if (!BehaviorInputs.supported(inventory.schemaVersion()))
            throw new IllegalArgumentException("Unsupported SCA schema: " + inventory.schemaVersion());
        var data = BpmDataAnalysis.of(inventories, labels);
        var faults = BpmFaultAnalysis.of(inventories, labels);
        Integration integration = BehaviorChannels.integration(icos, icosLabel, unavailable);
        var discovery = BehaviorDiscovery.discover(inventories, labels, icos, integration);
        List<ProcessWiring> processes = BehaviorDiscovery.processes(inventories, labels, data.paths(), discovery, integration);
        List<Issue> issues = new ArrayList<>(data.issues());
        issues.addAll(faults.issues()); issues.addAll(discovery.issues()); issues.addAll(integration.issues()); issues.addAll(diagnostics);
        return new BehaviorReport(1, "bpm-behavior-v1", data, faults, discovery, integration, processes, issues);
    }

    public static String markdown(BehaviorReport report) {
        if (report == null) throw new IllegalArgumentException("A behavior report is required");
        StringBuilder out = new StringBuilder("# BPM configured behavior\n\n");
        out.append("Source declarations and configured candidates do not establish runtime delivery, rejection, handler invocation, acknowledgment or transaction behavior.\n\n");
        if (report.data() != null) {
            out.append(BpmPathAnalysis.markdown(report.data().paths())).append('\n');
            out.append(BpmDataAnalysis.markdown(report.data())).append('\n');
        }
        if (report.faults() != null) {
            out.append(BpmFaultAnalysis.markdown(report.faults())).append('\n');
            // Display names are retained in the declaration payload, independent of runtime fault handling.
            out.append("\n## Fault source names\n\n| Process | Definition | Name |\n| --- | --- | --- |\n");
            for (var p : report.faults().processes()) if (p != null) for (var d : p.declarations()) if (d != null)
                for (var definition : d.definitions()) if (definition != null && definition.source() != null)
                    row(out, p.key(), definition.source().id(), definition.source().texts());
        }
        Integration integration = report.integration();
        if (integration != null) {
            out.append("\n## Configured integration\n\n| Input | Availability | Selection | Reasons |\n| --- | --- | --- | --- |\n");
            row(out, integration.input(), integration.availability(), integration.selection(), integration.reasons());
            out.append("\n| ICO | Configuration | Reasons |\n| --- | --- | --- |\n");
            for (var ico : integration.icos()) if (ico != null) row(out, ico.ordinal(), ico.configuration(), ico.reasons());
            out.append("\n| Channel | Configuration | Reasons |\n| --- | --- | --- |\n");
            for (var channel : integration.channels()) if (channel != null) row(out, channel.ordinal(), channel.configuration(), channel.reasons());
            out.append("\n| Use | ICO | Role | Reference | Candidates | Identity | Association | Direction | Reasons |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (var use : integration.channelUses()) if (use != null) row(out, use.ordinal(), use.ico(), use.role(), use.source(),
                    use.candidates(), use.resolution(), use.association(), use.direction(), use.reasons());
        }
        for (var process : report.processes()) if (process != null) {
            out.append("\n## Wiring ").append(escape(process.key())).append("\n\n");
            row(out, process.state(), process.reasons());
            out.append("\n| Interface | Name | Context | State | Candidates | Steps | ICO matches | Reasons |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (var iface : process.interfaces()) if (iface != null) row(out, iface.ordinal(), iface.source() == null ? null : iface.source().name(),
                    iface.context(), iface.state(), iface.candidates().stream().filter(Objects::nonNull).map(InterfaceCandidate::ordinal).toList(),
                    iface.steps(), iface.matches(), iface.reasons());
            out.append("\n| Step | State | Interfaces | Services | Acknowledgment | Transaction | Reasons |\n| --- | --- | --- | --- | --- | --- | --- |\n");
            for (var call : process.calls()) if (call != null) row(out, call.step(), call.state(), call.interfaces(), call.services(), call.acknowledgment(), call.transaction(), call.reasons());
        }
        out.append("\n## Behavior diagnostics\n\n| Code | Source | Message |\n| --- | --- | --- |\n");
        for (Issue issue : report.issues()) if (issue != null) row(out, issue.code(), issue.source(), issue.message());
        return out.toString();
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
}
