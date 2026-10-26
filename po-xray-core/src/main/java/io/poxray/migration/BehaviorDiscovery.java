package io.poxray.migration;

import io.poxray.model.BehaviorReport.*;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import io.poxray.model.Interface;
import io.poxray.model.ScaInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import static io.poxray.migration.Reports.list;
import static io.poxray.migration.BehaviorChannels.addReason;

/** Reassociates the existing lossy discovery projection without replacing its matching rules. */
final class BehaviorDiscovery {
    static DiscoveryReport discover(List<ScaInventory> inventories, List<String> labels, IntegrationInventory raw,
            Integration integration) {
        DiscoveryReport d = ScenarioDiscovery.of(inventories, labels,
                "available".equals(integration.availability()) ? raw : null, integration.input());
        return new DiscoveryReport(d.schemaVersion(), null, d.inputs(), d.icosInput(), d.processes(), d.components(), d.issues());
    }

    static List<ProcessWiring> processes(List<ScaInventory> inventories, List<String> labels, BpmPathReport paths,
            DiscoveryReport discovery, Integration integration) {
        List<ProcessWiring> result = new ArrayList<>();
        boolean countMatches = paths.processes().size() == discovery.processes().size();
        for (int p = 0; p < paths.processes().size(); p++) {
            BpmPathReport.ProcessPaths path = paths.processes().get(p);
            BpmComponent component = inventories.get(path.key().input() - 1).bpm().get(path.key().component() - 1);
            DiscoveryReport.ProcessRow row = p < discovery.processes().size() ? discovery.processes().get(p) : null;
            boolean stable = countMatches && row != null && Objects.equals(row.file(), labels.get(path.key().input() - 1))
                    && Objects.equals(row.dc(), path.dc()) && Objects.equals(row.id(), path.id()) && Objects.equals(row.name(), path.name());
            List<String> reasons = new ArrayList<>();
            List<InterfaceUse> interfaces = new ArrayList<>();
            if (stable) {
                for (DiscoveryReport.InterfaceRow i : row.inbound()) interfaces.add(iface(interfaces.size() + 1, "inbound", i, path, component, integration));
                for (DiscoveryReport.InterfaceRow i : row.outbound()) interfaces.add(iface(interfaces.size() + 1, "outbound", i, path, component, integration));
            } else reasons.add("discovery-process-mismatch");
            boolean sourceAvailable = !"unavailable".equals(path.coverage());
            if (!sourceAvailable) reasons.add("source-unavailable");
            List<StepCall> calls = BehaviorCalls.calls(component, path, interfaces, sourceAvailable);
            for (InterfaceUse i : interfaces) {
                for (String reason : i.reasons()) addReason(reasons, reason);
                for (IcoMatch match : i.matches()) {
                    for (String reason : match.reasons()) addReason(reasons, reason);
                    for (Route route : match.routes()) for (String reason : route.reasons()) addReason(reasons, reason);
                }
            }
            for (StepCall call : calls) {
                for (String reason : call.reasons()) addReason(reasons, reason);
                for (ServiceCandidate service : call.services()) for (String reason : service.reasons()) addReason(reasons, reason);
            }
            result.add(new ProcessWiring(path.key(), stable && sourceAvailable ? "candidate" : "unavailable", interfaces, calls, reasons));
        }
        return result;
    }

    private static InterfaceUse iface(int ordinal, String direction, DiscoveryReport.InterfaceRow row,
            BpmPathReport.ProcessPaths path, BpmComponent component, Integration integration) {
        List<InterfaceCandidate> candidates = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        List<String> references = new ArrayList<>();
        List<Interface> catalog = component.catalog() == null ? List.of() : list(component.catalog().interfaces());
        for (int c = 0; c < catalog.size(); c++) {
            Interface iface = catalog.get(c);
            if (iface == null || !Objects.equals(row.name(), iface.name()) || !Objects.equals(row.namespace(), iface.namespace())) continue;
            boolean matches = false;
            for (Interface.Usage usage : list(iface.usedBy())) if (usage != null && path.name() != null && path.name().equals(usage.process())
                    && Objects.equals(usage.role(), row.role())) {
                matches = true;
                String ref = usage.node() == null ? null : usage.node().ref();
                if (ref == null || ref.isBlank()) addReason(reasons, "missing-interface-node");
                else {
                    references.add(ref);
                    long count = path.steps().stream().filter(s -> ref.equals(s.id())).count();
                    if (count == 0) addReason(reasons, "missing-interface-step");
                    if (count > 1) addReason(reasons, "ambiguous-interface-step");
                }
            }
            if (matches) candidates.add(new InterfaceCandidate(c + 1, iface));
        }
        List<Integer> steps = path.steps().stream().filter(s -> references.contains(s.id())).map(BpmPathReport.Step::ordinal).toList();
        String state = candidates.isEmpty() ? "unavailable" : candidates.size() > 1 ? "ambiguous" : "candidate";
        if (!"candidate".equals(state)) addReason(reasons, "interface-projection-" + state);
        if (!"available".equals(integration.availability())) addReason(reasons, "integration-unavailable");
        String context = switch (row.role()) {
            case "start" -> "pre-process";
            case "intermediate" -> "existing-instance";
            case "outbound", "notification" -> "process-outbound";
            default -> "unknown";
        };
        List<IcoMatch> matches = new ArrayList<>();
        for (int d = 0; d < row.icos().size(); d++) {
            DiscoveryReport.IcoRow projection = row.icos().get(d);
            List<IcoSource> sources = integration.icos().stream().filter(i -> i.configuration() != null && summary(i.configuration()).equals(projection)).toList();
            if (sources.isEmpty()) matches.add(new IcoMatch(d + 1, null, List.of(), "unavailable", List.of("ico-projection-unavailable")));
            for (IcoSource source : sources) {
                List<String> gaps = new ArrayList<>();
                List<Route> routes = routes(source.configuration(), row, "inbound".equals(direction), gaps);
                matches.add(new IcoMatch(d + 1, source.ordinal(), routes, sources.size() > 1 ? "ambiguous" : "candidate", gaps));
            }
        }
        return new InterfaceUse(ordinal, direction, context, row, candidates, steps, matches, state, reasons);
    }

    private static DiscoveryReport.IcoRow summary(Ico ico) {
        Endpoint sender = ico.sender();
        ObjRef iface = sender == null ? null : sender.iface();
        TreeSet<String> receivers = new TreeSet<>();
        for (Receiver receiver : list(ico.receivers())) if (receiver != null && receiver.component() != null) receivers.add(receiver.component());
        return new DiscoveryReport.IcoRow(ico.oid(), sender == null ? null : sender.party(), sender == null ? null : sender.component(),
                iface == null ? null : iface.name(), iface == null ? null : iface.namespace(), List.copyOf(receivers));
    }

    private static List<Route> routes(Ico ico, DiscoveryReport.InterfaceRow row, boolean inbound, List<String> reasons) {
        List<Route> result = new ArrayList<>();
        List<Receiver> receivers = list(ico.receivers());
        if (receivers.isEmpty()) gap(result, inbound, null, null, null, reasons);
        for (int r = 0; r < receivers.size(); r++) {
            Receiver receiver = receivers.get(r);
            List<InterfaceAssignment> assignments = receiver == null ? List.of() : list(receiver.interfaces());
            if (assignments.isEmpty()) gap(result, inbound, r + 1, null, null, reasons);
            for (int a = 0; a < assignments.size(); a++) {
                InterfaceAssignment assignment = assignments.get(a);
                List<Target> targets = assignment == null ? List.of() : list(assignment.targets());
                if (targets.isEmpty()) gap(result, inbound, r + 1, a + 1, null, reasons);
                for (int t = 0; t < targets.size(); t++) {
                    Target target = targets.get(t);
                    ObjRef iface = target == null ? null : target.iface();
                    if (iface == null || inbound && (!Objects.equals(iface.name(), row.name()) || !Objects.equals(iface.namespace(), row.namespace())))
                        gap(result, inbound, r + 1, a + 1, t + 1, reasons);
                    else result.add(new Route(r + 1, a + 1, t + 1, inbound ? "interface-candidate" : "configured-candidate", List.of()));
                }
            }
        }
        return result;
    }
    private static void gap(List<Route> result, boolean inbound, Integer r, Integer a, Integer t, List<String> reasons) {
        if (inbound) addReason(reasons, "incomplete-other-route");
        else { result.add(new Route(r, a, t, "unknown", List.of("unknown-route"))); addReason(reasons, "unknown-route"); }
    }
    private BehaviorDiscovery() { }
}
