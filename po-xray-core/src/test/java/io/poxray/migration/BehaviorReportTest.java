package io.poxray.migration;

import static io.poxray.migration.BehaviorAnalysisTest.analyze;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.SyntheticBehavior;
import io.poxray.json.Json;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.*;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.QNameRef;
import io.poxray.model.ScaInventory;
import io.poxray.model.ServiceReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BehaviorReportTest {
    @TempDir Path dir;

    @Test
    void invalidArgumentsAndLegacySchemasAreExplicit() {
        ScaInventory source = new SyntheticBehavior().sca(dir.resolve("source.sca"));
        assertThatThrownBy(() -> BehaviorAnalysis.of(null, List.of(), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BehaviorAnalysis.of(List.of(), null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BehaviorAnalysis.of(List.of(source), List.of(), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BehaviorAnalysis.of(Arrays.asList((ScaInventory) null), List.of("s"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BehaviorAnalysis.of(List.of(source), Arrays.asList((String) null), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analyze(BehaviorInputsTest.schema(source, "future"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(analyze(BehaviorInputsTest.schema(source, null), null).processes()).hasSize(1);
        BehaviorReport empty = BehaviorAnalysis.of(List.of(), List.of(), null, null);
        assertThat(empty.formatVersion()).isEqualTo(1);
        assertThat(empty.analysisVersion()).isEqualTo("bpm-behavior-v1");
        assertThat(empty.processes()).isEmpty();
        assertThat(empty.data().paths().inputs()).isEmpty();
        assertThat(empty.integration().availability()).isEqualTo("absent");
    }

    @Test
    void snapshotsDetachEveryLegacyCollectionAndPreserveHolesAndNullValues() {
        List<String> strings = mutable("first", null, "last");
        List<String> reasons = mutable("reason", null);
        Map<String, String> attributes = new LinkedHashMap<>(); attributes.put("first", "value"); attributes.put("empty", null);
        DirRef ref = new DirRef("", "COMP", "channel", "oid");
        Map<String, DirRef> parameters = new LinkedHashMap<>(); parameters.put("LOOKUP", ref); parameters.put("", null);
        List<ConditionLine> lines = mutable(new ConditionLine(1, "false", "EQ", "true"), null);
        List<Target> targets = mutable(new Target(new ObjRef("ifmmessif", "Target", "urn:test", null, null, null), "0..1"), null);
        List<InterfaceAssignment> assignments = mutable(new InterfaceAssignment("SenderOperation", new Condition("false", lines), null, targets, parameters), null);
        List<DirRef> refs = mutable(ref, null);
        List<Receiver> receivers = mutable(new Receiver("", "COMP", new Condition("false", lines), "EOIO", assignments, refs), null);
        List<ObjRef> mappings = mutable(new ObjRef("MAPPING", "Mapping", "urn:test", null, null, null), null);
        Ico ico = new Ico("ico", null, null, new Endpoint("", "SOURCE", null, strings, ref), receivers,
                new DynamicRouting(true, mappings), "error", "synthetic-author", "original-source-time");
        List<IntegrationInventory.Module> modules = mutable(new IntegrationInventory.Module(7, "module", "L", "ns", attributes), null);
        Channel channel = new Channel(ref, "sender", "SOAP/XI", "SOAP", "XI", "AE", attributes, modules, null);
        List<Interface.Fault> faults = mutable(new Interface.Fault("fault", "Fault", new QNameRef("Payload", "urn:test")), null);
        List<Interface.Operation> operations = mutable(new Interface.Operation("op", "operation", "in-only", null, null, faults), null);
        List<Interface.Usage> usages = mutable(new Interface.Usage("process", null, "start"), null);
        Interface iface = new Interface("interface", "Interface", "urn:test", null, operations, usages);
        ServiceReference service = new ServiceReference("service", "uuid", "WS", "XI", null, null, null, null, attributes, null);
        List<DiscoveryReport.IcoRow> discoveredIcos = mutable(new DiscoveryReport.IcoRow("ico", "", "SOURCE", "Interface", "urn:test", strings), null);
        List<DiscoveryReport.InterfaceRow> discoveredInterfaces = mutable(new DiscoveryReport.InterfaceRow("Interface", "urn:test", "start", discoveredIcos), null);
        List<DiscoveryReport.ProcessRow> discoveredProcesses = mutable(new DiscoveryReport.ProcessRow("file", "dc", "id", "process", strings, discoveredInterfaces, discoveredInterfaces), null);
        List<DiscoveryReport.ComponentRow> discoveredComponents = mutable(new DiscoveryReport.ComponentRow("COMP", strings), null);
        List<Issue> issues = mutable(new Issue(Issue.WARNING, "SYNTHETIC", "kept", null, null), null);
        DiscoveryReport discovery = new DiscoveryReport("0.1", null, strings, null, discoveredProcesses, discoveredComponents, issues);
        List<IcoSource> icos = mutable(new IcoSource(1, ico, reasons), null);
        List<ChannelSource> channels = mutable(new ChannelSource(1, channel, reasons), null);
        List<Integer> ordinals = mutable(1, null, 3);
        List<ChannelUse> channelUses = mutable(new ChannelUse(1, 1, null, null, "sender", null, null, ref, ordinals,
                "ambiguous", "unknown", "unknown", reasons), null);
        Integration integration = new Integration(null, "0.1", "available", "unknown", new Source(null, null, strings), null,
                icos, channels, channelUses, issues, reasons);
        List<Route> routes = mutable(new Route(1, null, null, "configured-candidate", reasons), null);
        List<IcoMatch> matches = mutable(new IcoMatch(1, 1, routes, "candidate", reasons), null);
        List<InterfaceCandidate> candidates = mutable(new InterfaceCandidate(1, iface), null);
        List<InterfaceUse> interfaces = mutable(new InterfaceUse(1, "inbound", "pre-process", discoveredInterfaces.getFirst(),
                candidates, ordinals, matches, "ambiguous", reasons), null);
        List<ServiceCandidate> services = mutable(new ServiceCandidate(1, service, "candidate", reasons), null);
        List<io.poxray.model.BpmSourceEvidence.Link> links = mutable((io.poxray.model.BpmSourceEvidence.Link) null);
        List<io.poxray.model.BpmSourceEvidence.Element> elements = mutable((io.poxray.model.BpmSourceEvidence.Element) null);
        List<StepCall> calls = mutable(new StepCall(1, null, links, elements, ordinals, services, "unknown", "unknown", "unknown", reasons), null);
        List<ProcessWiring> processes = mutable(new ProcessWiring(null, "candidate", interfaces, calls, reasons), null);
        BehaviorReport report = new BehaviorReport(1, "bpm-behavior-v1", null, null, discovery, integration, processes, issues);
        String snapshot = Json.write(report);
        for (List<?> list : List.of(strings, reasons, lines, targets, assignments, refs, receivers, mappings, modules, faults, operations,
                usages, discoveredIcos, discoveredInterfaces, discoveredProcesses, discoveredComponents, issues, icos, channels, ordinals,
                channelUses, routes, matches, candidates, interfaces, services, links, elements, calls, processes)) list.clear();
        attributes.clear(); parameters.clear();
        assertThat(Json.write(report)).isEqualTo(snapshot);
        Ico saved = report.integration().icos().getFirst().configuration();
        assertThat(saved.sender().operations()).containsExactly("first", null, "last");
        assertThat(saved.modifiedAt()).isEqualTo("original-source-time");
        assertThat(saved.receivers().getFirst().interfaces().getFirst().parameters()).containsEntry("", null);
        assertThat(report.integration().channels().getFirst().configuration().attributes()).containsEntry("empty", null);
        assertThat(saved.receivers().getFirst().interfaces().getFirst().parameters().keySet()).containsExactly("LOOKUP", "");
        assertThat(report.integration().channels().getFirst().configuration().attributes().keySet()).containsExactly("first", "empty");
        for (List<?> list : List.of(report.processes(), report.issues(), report.discovery().inputs(), report.discovery().processes(),
                report.discovery().components(), report.discovery().processes().getFirst().inbound(),
                report.discovery().processes().getFirst().inbound().getFirst().icos(), report.integration().source().files(),
                report.integration().icos(), report.integration().channels(), report.integration().channelUses(), integration.reasons(),
                saved.sender().operations(), saved.receivers(), saved.receivers().getFirst().interfaces(),
                saved.receivers().getFirst().interfaces().getFirst().targets(), saved.receivers().getFirst().condition().lines(),
                saved.receivers().getFirst().channels(), saved.dynamicRouting().mappings(), report.integration().channels().getFirst().configuration().modules(),
                report.processes().getFirst().interfaces(), report.processes().getFirst().calls(),
                report.processes().getFirst().interfaces().getFirst().candidates().getFirst().source().operations(),
                report.processes().getFirst().interfaces().getFirst().candidates().getFirst().source().usedBy(),
                report.processes().getFirst().interfaces().getFirst().candidates().getFirst().source().operations().getFirst().faults(),
                report.processes().getFirst().interfaces().getFirst().matches().getFirst().routes(),
                report.processes().getFirst().calls().getFirst().relations(), report.processes().getFirst().calls().getFirst().elements(),
                report.processes().getFirst().calls().getFirst().services(), report.processes().getFirst().calls().getFirst().interfaces())) {
            assertThat(list).containsNull();
            assertThatThrownBy(() -> list.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThatThrownBy(() -> saved.receivers().getFirst().interfaces().getFirst().parameters().put("changed", null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.integration().channels().getFirst().configuration().modules().getFirst().parameters().put("changed", null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.processes().getFirst().calls().getFirst().services().getFirst().source().attributes().put("changed", null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullLegacyCollectionsStayNullAndNullMapKeysAreRejected() {
        Ico absent = new Ico(null, null, null, new Endpoint(null, null, null, null, null), null, null, null, null, null);
        Ico empty = new Ico(null, null, null, new Endpoint(null, null, null, List.of(), null), List.of(), null, null, null, null);
        assertThat(new IcoSource(1, absent, List.of()).configuration().receivers()).isNull();
        assertThat(new IcoSource(1, absent, List.of()).configuration().sender().operations()).isNull();
        assertThat(new IcoSource(1, empty, List.of()).configuration().receivers()).isEmpty();
        Map<String, String> bad = new LinkedHashMap<>(); bad.put(null, "value");
        Channel channel = new Channel(null, null, null, null, null, null, bad, List.of(), null);
        assertThatThrownBy(() -> new ChannelSource(1, channel, List.of())).isInstanceOf(IllegalArgumentException.class);
        Channel module = new Channel(null, null, null, null, null, null, Map.of(),
                List.of(new IntegrationInventory.Module(1, null, null, null, bad)), null);
        assertThatThrownBy(() -> new ChannelSource(1, module, List.of())).isInstanceOf(IllegalArgumentException.class);
        ServiceReference service = new ServiceReference(null, null, null, null, null, null, null, null, bad, null);
        assertThatThrownBy(() -> new ServiceCandidate(1, service, "unknown", List.of())).isInstanceOf(IllegalArgumentException.class);
        Map<String, DirRef> badParameters = new LinkedHashMap<>(); badParameters.put(null, null);
        Ico ico = new Ico(null, null, null, null, List.of(new Receiver(null, null, null, null,
                List.of(new InterfaceAssignment(null, null, null, null, badParameters)), null)), null, null, null, null);
        assertThatThrownBy(() -> new IcoSource(1, ico, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredNewCollectionsRejectNull() {
        for (Runnable invalid : List.<Runnable>of(
                () -> new BehaviorReport(1, "v", null, null, null, null, null, List.of()),
                () -> new BehaviorReport(1, "v", null, null, null, null, List.of(), null),
                () -> new Integration(null, null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of()),
                () -> new Integration(null, null, null, null, null, null, List.of(), null, List.of(), List.of(), List.of()),
                () -> new Integration(null, null, null, null, null, null, List.of(), List.of(), null, List.of(), List.of()),
                () -> new Integration(null, null, null, null, null, null, List.of(), List.of(), List.of(), null, List.of()),
                () -> new Integration(null, null, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null),
                () -> new IcoSource(1, null, null), () -> new ChannelSource(1, null, null),
                () -> new ChannelUse(1, 1, null, null, null, null, null, null, null, null, null, null, List.of()),
                () -> new ChannelUse(1, 1, null, null, null, null, null, null, List.of(), null, null, null, null),
                () -> new ProcessWiring(null, null, null, List.of(), List.of()),
                () -> new ProcessWiring(null, null, List.of(), null, List.of()),
                () -> new ProcessWiring(null, null, List.of(), List.of(), null),
                () -> new InterfaceUse(1, null, null, null, null, List.of(), List.of(), null, List.of()),
                () -> new InterfaceUse(1, null, null, null, List.of(), null, List.of(), null, List.of()),
                () -> new InterfaceUse(1, null, null, null, List.of(), List.of(), null, null, List.of()),
                () -> new InterfaceUse(1, null, null, null, List.of(), List.of(), List.of(), null, null),
                () -> new IcoMatch(1, null, null, null, List.of()), () -> new IcoMatch(1, null, List.of(), null, null),
                () -> new Route(null, null, null, null, null),
                () -> new StepCall(1, null, null, List.of(), List.of(), List.of(), null, null, null, List.of()),
                () -> new StepCall(1, null, List.of(), null, List.of(), List.of(), null, null, null, List.of()),
                () -> new StepCall(1, null, List.of(), List.of(), null, List.of(), null, null, null, List.of()),
                () -> new StepCall(1, null, List.of(), List.of(), List.of(), null, null, null, null, List.of()),
                () -> new StepCall(1, null, List.of(), List.of(), List.of(), List.of(), null, null, null, null),
                () -> new ServiceCandidate(1, null, null, null))) assertThatThrownBy(invalid::run);
    }

    @Test
    void jsonKeepsAllNewFieldsNullsAndEmptyListsWithoutChangingLegacyOmission() {
        Route route = new Route(null, null, null, null, List.of());
        InterfaceUse iface = new InterfaceUse(1, null, null, null, List.of(new InterfaceCandidate(1, null)), List.of(),
                List.of(new IcoMatch(1, null, List.of(route), null, List.of())), null, List.of());
        StepCall call = new StepCall(1, null, List.of(), List.of(), List.of(), List.of(new ServiceCandidate(1, null, null, List.of())), null, null, null, List.of());
        Integration integration = new Integration(null, null, null, null, null, null, List.of(new IcoSource(1, null, List.of())),
                List.of(new ChannelSource(1, null, List.of())), List.of(new ChannelUse(1, 1, null, null, null, null, null, null,
                List.of(), null, null, null, List.of())), List.of(), List.of());
        JsonNode json = Json.mapper().readTree(Json.write(new BehaviorReport(1, "bpm-behavior-v1", null, null, null, integration,
                List.of(new ProcessWiring(null, null, List.of(iface), List.of(call), List.of())), List.of())));
        fields(json, "formatVersion", "analysisVersion", "data", "faults", "discovery", "integration", "processes", "issues");
        fields(json.path("integration"), "input", "schemaVersion", "availability", "selection", "source", "query", "icos", "channels", "channelUses", "issues", "reasons");
        fields(json.at("/integration/icos/0"), "ordinal", "configuration", "reasons");
        fields(json.at("/integration/channels/0"), "ordinal", "configuration", "reasons");
        fields(json.at("/integration/channelUses/0"), "ordinal", "ico", "receiver", "assignment", "role", "parameter", "reference", "source", "candidates", "resolution", "association", "direction", "reasons");
        fields(json.at("/processes/0"), "key", "state", "interfaces", "calls", "reasons");
        fields(json.at("/processes/0/interfaces/0"), "ordinal", "direction", "context", "source", "candidates", "steps", "matches", "state", "reasons");
        fields(json.at("/processes/0/interfaces/0/candidates/0"), "ordinal", "source");
        fields(json.at("/processes/0/interfaces/0/matches/0"), "discovery", "ico", "routes", "state", "reasons");
        fields(json.at("/processes/0/interfaces/0/matches/0/routes/0"), "receiver", "assignment", "target", "relation", "reasons");
        fields(json.at("/processes/0/calls/0"), "step", "location", "relations", "elements", "interfaces", "services", "state", "acknowledgment", "transaction", "reasons");
        fields(json.at("/processes/0/calls/0/services/0"), "ordinal", "source", "state", "reasons");
        assertThat(json.path("data").isNull()).isTrue();
        assertThat(json.at("/integration/icos/0/configuration").isNull()).isTrue();
        assertThat(json.at("/processes/0/calls/0/location").isNull()).isTrue();
        assertThat(json.at("/processes/0/calls/0/relations").isArray()).isTrue();
        assertThat(json.at("/processes/0/calls/0/relations").isEmpty()).isTrue();
        JsonNode legacy = Json.mapper().readTree(Json.write(new Issue(Issue.WARNING, "SYNTHETIC", "kept", null, null)));
        assertThat(legacy.path("source").isMissingNode()).isTrue();
        assertThat(legacy.path("location").isMissingNode()).isTrue();
    }

    @Test
    void repeatedAnalysisIsDeterministicAndInputsUnchanged() throws IOException {
        var pair = new SyntheticBehavior().write(dir);
        String sca = Json.write(pair.sca()), icos = Json.write(pair.icos());
        BehaviorReport first = analyze(pair.sca(), pair.icos()), second = analyze(pair.sca(), pair.icos());
        assertThat(Json.write(first)).isEqualTo(Json.write(second));
        assertThat(BehaviorAnalysis.markdown(first)).isEqualTo(BehaviorAnalysis.markdown(second));
        assertThat(Json.write(pair.sca())).isEqualTo(sca);
        assertThat(Json.write(pair.icos())).isEqualTo(icos);
        assertThat(first.discovery().generator()).isNull();
    }

    @Test
    void markdownEscapesSourceCellsAndNamesConfiguredRuntimeLimits() throws IOException {
        var pair = new SyntheticBehavior().write(dir, "value|with\nnewline");
        BehaviorReport report = BehaviorAnalysis.of(List.of(pair.sca()), List.of("input|label\nnext"), pair.icos(), "ico|label\nnext");
        String markdown = BehaviorAnalysis.markdown(report);
        assertThat(markdown).contains("input\\|label", "Inbound\\|Contract", "Call\\|external", "Fault\\|Declared");
        assertThat(markdown).doesNotContain("input|label\nnext", "ico|label\nnext", "value|with\nnewline");
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("pre-process", "process-outbound", "coverage", "candidate", "unknown", "acknowledg", "transaction", "configured", "fault");
    }

    @SafeVarargs private static <T> List<T> mutable(T... values) { return new ArrayList<>(Arrays.asList(values)); }
    private static void fields(JsonNode node, String... names) { assertThat(node.propertyNames()).containsExactly(names); }
}
