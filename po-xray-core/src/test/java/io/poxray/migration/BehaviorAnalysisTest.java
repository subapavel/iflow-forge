package io.poxray.migration;

import static io.poxray.SyntheticBehavior.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBehavior;
import io.poxray.SyntheticBehavior.Pair;
import io.poxray.bpm.BpmDataAnalysis;
import io.poxray.bpm.BpmFaultAnalysis;
import io.poxray.json.Json;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.*;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.Ref;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorAnalysisTest {
    @TempDir Path dir;

    @Test
    void composeAcceptedReportsKeepsStructuralPathsDataFaults() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        assertThat(pair.sca().bpm().getFirst().catalog().interfaces()).hasSize(2);
        assertThat(pair.icos().icos()).hasSize(2);
        assertThat(pair.icos().channels()).hasSize(3);
        BehaviorReport report = analyze(pair.sca(), pair.icos());
        assertThat(Json.write(report.data())).isEqualTo(Json.write(BpmDataAnalysis.of(List.of(pair.sca()), List.of("synthetic"))));
        assertThat(Json.write(report.faults())).isEqualTo(Json.write(BpmFaultAnalysis.of(List.of(pair.sca()), List.of("synthetic"))));
        assertThat(report.data().processes().getFirst().declarations()).isNotEmpty();
        assertThat(report.faults().processes().getFirst().declarations()).isNotEmpty();
        assertThat(wiring(report).key()).isEqualTo(new ProcessKey(1, 1, 1));
        assertThat(wiring(report).calls()).extracting(StepCall::step)
                .containsExactlyElementsOf(report.data().paths().processes().getFirst().steps().stream().map(s -> s.ordinal()).toList());
        List<Issue> prefix = new ArrayList<>();
        prefix.addAll(report.data().issues()); prefix.addAll(report.faults().issues());
        prefix.addAll(report.discovery().issues()); prefix.addAll(report.integration().issues());
        assertThat(report.issues().subList(0, prefix.size())).containsExactlyElementsOf(prefix);
    }

    @Test
    void ingressPolicyIsSeparateFromBoundaryFault() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BehaviorReport report = analyze(pair.sca(), pair.icos());
        InterfaceUse ingress = inbound(report);
        assertThat(ingress.context()).isEqualTo("pre-process");
        assertThat(ingress.source().role()).isEqualTo("start");
        assertThat(ingress.state()).isEqualTo("candidate");
        assertThat(ingress.matches()).singleElement().satisfies(match -> {
            assertThat(match.ico()).isEqualTo(1);
            assertThat(match.state()).isEqualTo("candidate");
        });
        assertThat(report.integration().icos().getFirst().configuration().noReceiverBehaviour()).isEqualTo("error");
        assertThat(report.faults().processes().getFirst().declarations()).filteredOn(d -> "boundary-trigger".equals(d.role()))
                .singleElement().satisfies(d -> {
                    assertThat(d.owner().id()).isEqualTo("call");
                    assertThat(d.event().id()).isEqualTo("boundary");
                    assertThat(d.definition().id()).isEqualTo("error");
                });
    }

    @Test
    void intermediateIngressIsExistingInstance() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Interface source = pair.sca().bpm().getFirst().catalog().interfaces().getFirst();
        Interface intermediate = copyInterface(source, List.of(new Interface.Usage("Synthetic behavior", new Ref("start", "Start"), "intermediate")));
        BehaviorReport report = analyze(catalogInterfaces(pair.sca(), List.of(intermediate)), pair.icos());
        assertThat(inbound(report).context()).isEqualTo("existing-instance");
        assertThat(inbound(report).source().role()).isEqualTo("intermediate");
        assertThat(inbound(report).steps()).containsExactly(step(report, "start"));
    }

    @Test
    void discoveryMatchesAreReusedAndGeneratorRemoved() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        DiscoveryReport original = ScenarioDiscovery.of(List.of(pair.sca()), List.of("synthetic"), pair.icos(), "icos");
        BehaviorReport report = analyze(pair.sca(), pair.icos());
        DiscoveryReport expected = new DiscoveryReport(original.schemaVersion(), null, original.inputs(), original.icosInput(),
                original.processes(), original.components(), original.issues());
        assertThat(Json.write(report.discovery())).isEqualTo(Json.write(expected));
        assertThat(report.discovery().generator()).isNull();
        assertThat(wiring(report).interfaces()).extracting(InterfaceUse::ordinal).containsExactly(1, 2);
        assertThat(wiring(report).interfaces()).extracting(InterfaceUse::context).containsExactly("pre-process", "process-outbound");
    }

    @Test
    void duplicateLabelsIdsAndNullGapsKeepProcessKeys() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BpmComponent c = pair.sca().bpm().getFirst();
        BpmComponent positioned = new BpmComponent(c.dc(), Arrays.asList(null, c.processes().getFirst()), c.catalog(), c.sourceEvidence());
        ScaInventory gaps = withComponents(pair.sca(), Arrays.asList(null, positioned, c));
        BehaviorReport report = BehaviorAnalysis.of(List.of(gaps, pair.sca()), List.of("same", "same"), pair.icos(), "icos");
        assertThat(report.processes()).extracting(ProcessWiring::key).containsExactly(
                new ProcessKey(1, 2, 2), new ProcessKey(1, 3, 1), new ProcessKey(2, 1, 1));
        assertThat(report.processes()).allSatisfy(p -> {
            assertThat(p.state()).isEqualTo("candidate");
            assertThat(p.interfaces()).hasSize(2);
        });
    }

    @Test
    void duplicateIcoSummariesKeepEveryOriginalCandidate() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Ico original = pair.icos().icos().getFirst();
        Ico duplicate = new Ico(null, original.description(), original.generatedFrom(), original.sender(), original.receivers(),
                original.dynamicRouting(), original.noReceiverBehaviour(), original.modifiedBy(), original.modifiedAt());
        var sender = original.sender();
        Ico decoy = new Ico(null, null, null, new IntegrationInventory.Endpoint(sender.party(), "DECOY", sender.iface(),
                sender.operations(), sender.channel()), List.of(), null, null, null, null);
        IntegrationInventory config = withConfiguration(pair.icos(), Arrays.asList(null, duplicate, duplicate, pair.icos().icos().get(1), decoy), pair.icos().channels());
        BehaviorReport report = analyze(pair.sca(), config);
        assertThat(report.integration().icos()).extracting(IcoSource::ordinal).containsExactly(1, 2, 3, 4, 5);
        assertThat(report.integration().icos().getFirst().configuration()).isNull();
        assertThat(inbound(report).matches()).extracting(IcoMatch::discovery).containsExactly(1, 1, 2, 2);
        assertThat(inbound(report).matches()).extracting(IcoMatch::ico).containsExactly(2, 3, 2, 3);
        assertThat(inbound(report).matches()).allSatisfy(m -> assertThat(m.state()).isEqualTo("ambiguous"));
    }

    @Test
    void routeConditionsAndSenderOperationRemainConfiguration() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BehaviorReport report = analyze(pair.sca(), pair.icos());
        Ico raw = report.integration().icos().getFirst().configuration();
        assertThat(raw.receivers().getFirst().condition().text()).isEqualTo("\"false\" = \"true\"");
        assertThat(raw.receivers().getFirst().interfaces().getFirst().operation()).isEqualTo("SenderOperation");
        assertThat(inbound(report).candidates().getFirst().source().operations().getFirst().name()).isEqualTo("Consume");
        assertThat(raw.dynamicRouting().operationSpecific()).isTrue();
        assertThat(inbound(report).matches().getFirst().routes()).singleElement().satisfies(route -> {
            assertThat(route.receiver()).isEqualTo(1);
            assertThat(route.assignment()).isEqualTo(1);
            assertThat(route.target()).isEqualTo(1);
            assertThat(route.relation()).isEqualTo("interface-candidate");
        });
    }

    @Test
    void unmatchedReceiverBranchesNeverBecomeBpmDelivery() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Ico ico = pair.icos().icos().getFirst();
        List<Receiver> branches = Arrays.asList(ico.receivers().getFirst(), null, ico.receivers().get(1));
        Ico holes = new Ico(ico.oid(), ico.description(), ico.generatedFrom(), ico.sender(), branches, ico.dynamicRouting(),
                ico.noReceiverBehaviour(), ico.modifiedBy(), ico.modifiedAt());
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), List.of(holes), pair.icos().channels()));
        assertThat(inbound(report).matches().getFirst().routes()).extracting(Route::receiver).containsExactly(1);
        assertThat(inbound(report).matches().getFirst().routes()).extracting(Route::target).containsExactly(1);
        assertThat(inbound(report).matches().getFirst().reasons()).contains("incomplete-other-route");
        assertThat(report.integration().icos().getFirst().configuration().receivers()).containsExactlyElementsOf(branches);
        assertThat(report.integration().icos().getFirst().configuration().receivers().getFirst().interfaces().getFirst().targets())
                .hasSize(2).extracting(t -> t.iface().name()).containsExactly(IN, "Unrelated");
    }

    @Test
    void outboundMissingStructuresKeepOriginalPlaceholderIndices() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Ico out = pair.icos().icos().get(1);
        Receiver empty = out.receivers().getFirst();
        Receiver missingAssignments = new Receiver(empty.party(), empty.component(), null, null, null, null);
        Receiver nullAssignment = new Receiver(empty.party(), empty.component(), null, null, Arrays.asList((IntegrationInventory.InterfaceAssignment) null), null);
        Ico holes = new Ico(out.oid(), null, null, out.sender(), Arrays.asList(null, missingAssignments, nullAssignment), null, null, null, null);
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), List.of(holes), pair.icos().channels()));
        InterfaceUse outbound = wiring(report).interfaces().get(1);
        List<Route> routes = outbound.matches().getFirst().routes();
        assertThat(routes).extracting(Route::receiver).containsExactly(1, 2, 3);
        assertThat(routes).extracting(Route::assignment).containsExactly(null, null, 1);
        assertThat(routes).allSatisfy(r -> {
            assertThat(r.target()).isNull();
            assertThat(r.relation()).isEqualTo("unknown");
            assertThat(r.reasons()).contains("unknown-route");
        });
    }

    @Test
    void duplicateInterfaceProjectionsKeepEveryCatalogueIndex() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Interface in = pair.sca().bpm().getFirst().catalog().interfaces().getFirst();
        BehaviorReport report = analyze(catalogInterfaces(pair.sca(), Arrays.asList(null, in, in)), pair.icos());
        assertThat(wiring(report).interfaces()).hasSize(2).allSatisfy(use -> {
            assertThat(use.state()).isEqualTo("ambiguous");
            assertThat(use.candidates()).extracting(InterfaceCandidate::ordinal).containsExactly(2, 3);
            assertThat(use.steps()).containsExactly(step(report, "start"));
        });
    }

    @Test
    void unknownUsageAndMissingInterfaceNodeRemainDiagnostics() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Interface in = pair.sca().bpm().getFirst().catalog().interfaces().getFirst();
        for (Ref node : Arrays.asList(null, new Ref(null, "Start"), new Ref("missing", "Start"))) {
            Interface projected = copyInterface(in, List.of(new Interface.Usage("Synthetic behavior", node, "start"),
                    new Interface.Usage("Synthetic behavior", new Ref("start", "Start"), "future-role")));
            BehaviorReport report = analyze(catalogInterfaces(pair.sca(), List.of(projected)), pair.icos());
            assertThat(inbound(report).steps()).isEmpty();
            assertThat(inbound(report).reasons()).isNotEmpty();
            assertThat(report.discovery().issues()).extracting(Issue::code).contains("UNKNOWN_INTERFACE_ROLE");
        }
    }

    static BehaviorReport analyze(ScaInventory sca, IntegrationInventory config) {
        return BehaviorAnalysis.of(List.of(sca), List.of("synthetic"), config, "icos");
    }
    static ProcessWiring wiring(BehaviorReport report) { assertThat(report.processes()).hasSize(1); return report.processes().getFirst(); }
    static InterfaceUse inbound(BehaviorReport report) { return wiring(report).interfaces().getFirst(); }
    static int step(BehaviorReport report, String id) {
        return report.data().paths().processes().getFirst().steps().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow().ordinal();
    }
    static Interface copyInterface(Interface source, List<Interface.Usage> usages) {
        return new Interface(source.id(), source.name(), source.namespace(), source.source(), source.operations(), usages);
    }
    static ScaInventory catalogInterfaces(ScaInventory sca, List<Interface> interfaces) {
        BpmComponent.Catalog c = sca.bpm().getFirst().catalog();
        return withCatalog(sca, new BpmComponent.Catalog(interfaces, c.serviceReferences(), c.functions(), c.builtinFunctions(),
                c.eventDefinitions(), c.builtinEventDefinitions(), c.dataTypes()));
    }
}
