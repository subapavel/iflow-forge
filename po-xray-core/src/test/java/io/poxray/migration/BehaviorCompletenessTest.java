package io.poxray.migration;

import static io.poxray.SyntheticBehavior.withCatalog;
import static io.poxray.SyntheticBehavior.withConfiguration;
import static io.poxray.migration.BehaviorAnalysisTest.analyze;
import static io.poxray.migration.BehaviorAnalysisTest.step;
import static io.poxray.migration.BehaviorAnalysisTest.wiring;
import static io.poxray.migration.BehaviorChannelsTest.channel;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBehavior;
import io.poxray.SyntheticBehavior.Pair;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.ChannelSource;
import io.poxray.model.BehaviorReport.ChannelUse;
import io.poxray.model.BehaviorReport.IcoMatch;
import io.poxray.model.BehaviorReport.InterfaceUse;
import io.poxray.model.BehaviorReport.ProcessWiring;
import io.poxray.model.BehaviorReport.Route;
import io.poxray.model.BehaviorReport.ServiceCandidate;
import io.poxray.model.BehaviorReport.StepCall;
import io.poxray.model.BpmComponent;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.ScaInventory;
import io.poxray.model.ServiceReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorCompletenessTest {
    @TempDir Path dir;

    @Test
    void emptyPartyOnlyReferenceKeepsCompatibleCandidatesButRemainsUnknown() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel source = pair.icos().channels().getFirst();
        Channel firstEmptyParty = channel(source, new DirRef("", "SOURCE", "SND", "empty-one"), "sender");
        Channel secondEmptyParty = channel(source, new DirRef("", "OTHER", "SECOND", "empty-two"), "sender");
        List<Channel> channels = Arrays.asList(source, firstEmptyParty, channel(source, null, "sender"), null, secondEmptyParty);
        DirRef reference = new DirRef("", null, null, null);
        Ico raw = pair.icos().icos().getFirst();
        Endpoint sender = raw.sender();
        Ico projected = new Ico(raw.oid(), raw.description(), raw.generatedFrom(),
                new Endpoint("", sender.component(), sender.iface(), sender.operations(), reference),
                raw.receivers(), raw.dynamicRouting(), raw.noReceiverBehaviour(), raw.modifiedBy(), raw.modifiedAt());
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), List.of(projected), channels));
        ChannelUse use = report.integration().channelUses().getFirst();

        assertThat(report.integration().channels()).extracting(ChannelSource::ordinal).containsExactly(1, 2, 3, 4, 5);
        assertThat(report.integration().channels()).extracting(ChannelSource::configuration).containsExactlyElementsOf(channels);
        assertThat(use.role()).isEqualTo("sender");
        assertThat(use.ico()).isEqualTo(1);
        assertThat(use.source()).isEqualTo(reference);
        assertThat(use.resolution()).isEqualTo("unknown");
        assertThat(use.association()).isEqualTo("unknown");
        assertThat(use.direction()).isEqualTo("unknown");
        assertThat(use.candidates()).containsExactly(2, 5);
        assertThat(use.reasons()).contains("partial-channel-key").doesNotContain("missing-channel-selector");
    }

    @Test
    void processReasonsIncludeNestedIcoRouteGaps() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Ico inboundRaw = pair.icos().icos().getFirst();
        List<Receiver> inboundBranches = Arrays.asList(inboundRaw.receivers().getFirst(), null, inboundRaw.receivers().get(1));
        Ico inbound = new Ico(inboundRaw.oid(), inboundRaw.description(), inboundRaw.generatedFrom(), inboundRaw.sender(),
                inboundBranches, inboundRaw.dynamicRouting(), inboundRaw.noReceiverBehaviour(), inboundRaw.modifiedBy(), inboundRaw.modifiedAt());
        Ico outboundRaw = pair.icos().icos().get(1);
        Receiver receiver = outboundRaw.receivers().getFirst();
        Receiver missingAssignments = new Receiver(receiver.party(), receiver.component(), receiver.condition(), receiver.qualityOfService(), null, receiver.channels());
        Receiver nullAssignment = new Receiver(receiver.party(), receiver.component(), receiver.condition(), receiver.qualityOfService(),
                Arrays.asList((InterfaceAssignment) null), receiver.channels());
        List<Receiver> outboundBranches = Arrays.asList(null, missingAssignments, nullAssignment);
        Ico outbound = new Ico(outboundRaw.oid(), outboundRaw.description(), outboundRaw.generatedFrom(), outboundRaw.sender(),
                outboundBranches, outboundRaw.dynamicRouting(), outboundRaw.noReceiverBehaviour(), outboundRaw.modifiedBy(), outboundRaw.modifiedAt());
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), Arrays.asList(inbound, null, outbound), pair.icos().channels()));
        ProcessWiring process = wiring(report);

        assertThat(process.state()).isEqualTo("candidate");
        assertThat(process.interfaces()).extracting(InterfaceUse::direction).containsExactly("inbound", "outbound");
        assertThat(process.interfaces()).allSatisfy(use -> assertThat(use.state()).isEqualTo("candidate"));
        assertThat(report.integration().icos().getFirst().configuration()).isEqualTo(inbound);
        assertThat(report.integration().icos().get(1).configuration()).isNull();
        assertThat(report.integration().icos().get(2).configuration()).isEqualTo(outbound);

        InterfaceUse ingress = process.interfaces().getFirst();
        assertThat(ingress.matches()).hasSize(1);
        IcoMatch ingressMatch = ingress.matches().getFirst();
        assertThat(ingressMatch.discovery()).isEqualTo(1);
        assertThat(ingressMatch.ico()).isEqualTo(1);
        assertThat(ingressMatch.state()).isEqualTo("candidate");
        assertThat(ingressMatch.reasons()).contains("incomplete-other-route");
        assertThat(ingressMatch.routes()).singleElement().satisfies(route -> {
            assertThat(route.receiver()).isEqualTo(1);
            assertThat(route.assignment()).isEqualTo(1);
            assertThat(route.target()).isEqualTo(1);
            assertThat(route.relation()).isEqualTo("interface-candidate");
        });

        InterfaceUse egress = process.interfaces().get(1);
        assertThat(egress.matches()).hasSize(1);
        IcoMatch egressMatch = egress.matches().getFirst();
        assertThat(egressMatch.discovery()).isEqualTo(1);
        assertThat(egressMatch.ico()).isEqualTo(3);
        assertThat(egressMatch.state()).isEqualTo("candidate");
        assertThat(egressMatch.reasons()).contains("unknown-route");
        assertThat(egressMatch.routes()).extracting(Route::receiver).containsExactly(1, 2, 3);
        assertThat(egressMatch.routes()).extracting(Route::assignment).containsExactly(null, null, 1);
        assertThat(egressMatch.routes()).allSatisfy(route -> {
            assertThat(route.target()).isNull();
            assertThat(route.relation()).isEqualTo("unknown");
            assertThat(route.reasons()).contains("unknown-route");
        });
        assertThat(process.reasons()).contains("unknown-operation-direction", "incomplete-other-route", "unknown-route");
    }

    @Test
    void processReasonsIncludeAmbiguousServiceCandidateGaps() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BehaviorReport original = analyze(pair.sca(), pair.icos());
        StepCall originalCall = call(original);
        assertThat(originalCall.state()).isEqualTo("source-declared");
        assertThat(originalCall.services()).singleElement().satisfies(candidate -> assertThat(candidate.state()).isEqualTo("candidate"));
        BpmComponent.Catalog catalog = pair.sca().bpm().getFirst().catalog();
        ServiceReference service = catalog.serviceReferences().getFirst();
        ScaInventory projected = withCatalog(pair.sca(), new BpmComponent.Catalog(catalog.interfaces(), Arrays.asList(null, service, service),
                catalog.functions(), catalog.builtinFunctions(), catalog.eventDefinitions(), catalog.builtinEventDefinitions(), catalog.dataTypes()));
        assertThat(projected.bpm().getFirst().sourceEvidence()).isSameAs(pair.sca().bpm().getFirst().sourceEvidence());
        BehaviorReport report = analyze(projected, pair.icos());
        ProcessWiring process = wiring(report);
        StepCall call = call(report);

        assertThat(process.state()).isEqualTo("candidate");
        assertThat(call.step()).isEqualTo(originalCall.step());
        assertThat(call.state()).isEqualTo("source-declared");
        assertThat(call.relations()).isEqualTo(originalCall.relations());
        assertThat(call.elements()).isEqualTo(originalCall.elements());
        assertThat(call.services()).extracting(ServiceCandidate::ordinal).containsExactly(2, 3);
        assertThat(call.services()).allSatisfy(candidate -> {
            assertThat(candidate.source()).isEqualTo(service);
            assertThat(candidate.state()).isEqualTo("ambiguous");
            assertThat(candidate.reasons()).contains("service-projection-ambiguous");
        });
        assertThat(process.reasons()).contains("unknown-operation-direction", "service-projection-ambiguous");
    }

    private static StepCall call(BehaviorReport report) {
        int ordinal = step(report, "call");
        return wiring(report).calls().stream().filter(call -> call.step() == ordinal).findFirst().orElseThrow();
    }
}
