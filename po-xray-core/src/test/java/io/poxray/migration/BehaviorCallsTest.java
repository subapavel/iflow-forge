package io.poxray.migration;

import static io.poxray.SyntheticBehavior.*;
import static io.poxray.migration.BehaviorAnalysisTest.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBehavior;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.ServiceCandidate;
import io.poxray.model.BehaviorReport.StepCall;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.ScaInventory;
import io.poxray.model.ServiceReference;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorCallsTest {
    @TempDir Path dir;
    private int files;

    @Test
    void sourceCallsRetainAllOperationServiceCandidates() {
        ScaInventory sca = read(new SyntheticBehavior());
        BehaviorReport report = analyze(sca, null);
        StepCall call = call(report, "call");
        assertThat(call.state()).isEqualTo("source-declared");
        assertThat(call.relations()).extracting(BpmSourceEvidence.Link::association)
                .containsExactly(W + "ActivityHasOperation", W + "AutomatedActivityHasServiceReference");
        assertThat(call.elements()).extracting(BpmSourceEvidence.Element::id).contains("call", "out-op", "service");
        assertThat(call.interfaces()).containsExactly(2);
        assertThat(call.services()).singleElement().satisfies(candidate -> {
            assertThat(candidate.ordinal()).isEqualTo(1);
            assertThat(candidate.source().id()).isEqualTo("service");
            assertThat(candidate.source().uuid()).isEqualTo("uuid-is-not-source-id");
            assertThat(candidate.state()).isEqualTo("candidate");
        });
        BpmComponent.Catalog c = sca.bpm().getFirst().catalog();
        ServiceReference original = c.serviceReferences().getFirst();
        ScaInventory duplicates = withCatalog(sca, new BpmComponent.Catalog(c.interfaces(), Arrays.asList(null, original, original),
                c.functions(), c.builtinFunctions(), c.eventDefinitions(), c.builtinEventDefinitions(), c.dataTypes()));
        List<ServiceCandidate> candidates = call(analyze(duplicates, null), "call").services();
        assertThat(candidates).extracting(ServiceCandidate::ordinal).containsExactly(2, 3);
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.state()).isEqualTo("ambiguous"));
        ServiceReference uuidOnly = new ServiceReference("different-source-id", original.id(), original.type(), original.bindingType(),
                original.iface(), original.authenticationProfile(), original.logicalSystem(), original.serviceGroup(), original.attributes(), original.operationMapping());
        ScaInventory catalogueMismatch = withCatalog(sca, new BpmComponent.Catalog(c.interfaces(), List.of(uuidOnly), c.functions(),
                c.builtinFunctions(), c.eventDefinitions(), c.builtinEventDefinitions(), c.dataTypes()));
        StepCall noIdMatch = call(analyze(catalogueMismatch, null), "call");
        assertThat(noIdMatch.services()).isEmpty();
        assertThat(noIdMatch.relations()).anySatisfy(l -> assertThat(l.first().id()).isEqualTo("service"));
        assertThat(noIdMatch.reasons()).isNotEmpty();
        BehaviorReport notification = analyze(read(new SyntheticBehavior()
                .activityKind("Galaxy.ProcessSupplements.NotificationActivity").service(false)), null);
        assertThat(call(notification, "call").state()).isEqualTo("source-declared");
        assertThat(wiring(notification).interfaces().get(1).source().role()).isEqualTo("notification");
        assertThat(wiring(notification).interfaces().get(1).context()).isEqualTo("process-outbound");
    }

    @Test
    void excludedMissingAndUuidOnlyServiceTargetsNeverBecomeSourceDeclared() {
        ScaInventory excluded = read(new SyntheticBehavior().externalTargets(true));
        StepCall excludedCall = call(analyze(excluded, null), "call");
        assertThat(excludedCall.state()).isEqualTo("unknown");
        assertThat(excludedCall.relations()).filteredOn(l -> l.association().equals(W + "AutomatedActivityHasServiceReference"))
                .singleElement().satisfies(l -> assertThat(l.first().candidates().getFirst().included()).isFalse());
        assertThat(excludedCall.elements()).extracting(BpmSourceEvidence.Element::id).doesNotContain("service", "out-op");
        assertThat(excludedCall.services()).singleElement().satisfies(s -> assertThat(s.state()).isEqualTo("unavailable"));
        for (String target : List.of("missing", "uuid-is-not-source-id")) {
            StepCall missing = call(analyze(read(new SyntheticBehavior().service(false)
                    .relation(W + "AutomatedActivityHasServiceReference", target, "call")), null), "call");
            assertThat(missing.state()).isEqualTo("unknown");
            assertThat(missing.relations()).anySatisfy(l -> assertThat(l.first().id()).isEqualTo(target));
            assertThat(missing.services()).isEmpty();
            assertThat(missing.reasons()).isNotEmpty();
        }
    }

    @Test
    void malformedDirectGrammarOverridesValidLinksAndCatalogueHints() {
        List<SyntheticBehavior> shapes = List.of(
                new SyntheticBehavior().activityKind(W + "MappingActivity"),
                new SyntheticBehavior().operation(false).relation(W + "ActivityHasOperation", "out-if", "call"),
                new SyntheticBehavior().service(false).relation(W + "AutomatedActivityHasServiceReference", "out-op", "call"),
                new SyntheticBehavior().operation(false).relation(W + "ActivityHasOperation", "missing", "call"),
                new SyntheticBehavior().raw("WSDL2.Operation", "out-op", "", "<name originalText='Duplicate'/>")
        );
        for (SyntheticBehavior fixture : shapes) {
            StepCall call = call(analyze(read(fixture), null), "call");
            assertThat(call.state()).isEqualTo("unknown");
            assertThat(call.relations()).hasSize(2);
            assertThat(call.reasons()).isNotEmpty();
        }
    }

    @Test
    void repeatedOrMultipleDirectRelationsNeverChooseFirst() {
        for (SyntheticBehavior fixture : List.of(
                new SyntheticBehavior().relation(W + "ActivityHasOperation", "out-op", "call"),
                new SyntheticBehavior().relation(W + "ActivityHasOperation", "in-op", "call"),
                new SyntheticBehavior().relation(W + "AutomatedActivityHasServiceReference", "service", "call"))) {
            StepCall call = call(analyze(read(fixture), null), "call");
            assertThat(call.state()).isEqualTo("unknown");
            assertThat(call.relations()).hasSize(3);
            assertThat(call.relations()).extracting(BpmSourceEvidence.Link::location).doesNotHaveDuplicates();
            assertThat(call.reasons()).isNotEmpty();
        }
        ScaInventory ambiguousOwner = read(new SyntheticBehavior().node(W + "AutomatedActivity", "call"));
        BehaviorReport report = analyze(ambiguousOwner, null);
        List<Integer> callSteps = report.data().paths().processes().getFirst().steps().stream().filter(s -> "call".equals(s.id())).map(s -> s.ordinal()).toList();
        assertThat(callSteps).hasSize(2);
        assertThat(wiring(report).interfaces().get(1).steps()).containsExactlyElementsOf(callSteps);
        assertThat(wiring(report).calls()).filteredOn(c -> callSteps.contains(c.step())).hasSize(2).allSatisfy(c -> {
            assertThat(c.state()).isEqualTo("unknown");
            assertThat(c.relations()).isNotEmpty().allSatisfy(l -> assertThat(l.second().candidates()).hasSize(2));
        });
    }

    @Test
    void triggerOperationDirectionRemainsUnknownInBothLegacyOrientations() {
        for (boolean reversed : List.of(false, true)) {
            ScaInventory sca = read(new SyntheticBehavior().reverseMessage(reversed));
            BehaviorReport report = analyze(sca, null);
            StepCall start = call(report, "start");
            assertThat(start.state()).isEqualTo("unknown");
            assertThat(start.reasons()).contains("unknown-operation-direction");
            assertThat(start.relations()).extracting(BpmSourceEvidence.Link::association)
                    .contains(W + "CatchEventHasTrigger", W + "MessageEventDefinitionHasOperation");
            assertThat(start.relations()).filteredOn(l -> l.association().equals(W + "MessageEventDefinitionHasOperation"))
                    .singleElement().satisfies(l -> {
                        assertThat(l.first().id()).isEqualTo(reversed ? "message" : "in-op");
                        assertThat(l.second().id()).isEqualTo(reversed ? "in-op" : "message");
                    });
            assertThat(start.interfaces()).containsExactly(1);
        }
        StepCall noOperation = call(analyze(read(new SyntheticBehavior().messageOperation(false)), null), "start");
        assertThat(noOperation.state()).isEqualTo("unknown");
        assertThat(noOperation.reasons()).contains("missing-message-operation");
    }

    @Test
    void malformedCatchOperationShapeOverridesValidDirectDeclaration() {
        ScaInventory wrongDefinition = read(new SyntheticBehavior()
                .relation(W + "MessageEventDefinitionHasOperation", "out-op", "error"));
        StepCall boundary = call(analyze(wrongDefinition, null), "boundary");
        assertThat(boundary.state()).isEqualTo("unknown");
        assertThat(boundary.relations()).extracting(BpmSourceEvidence.Link::association)
                .contains(W + "CatchEventHasTrigger", W + "MessageEventDefinitionHasOperation");
        assertThat(boundary.elements()).extracting(BpmSourceEvidence.Element::id).contains("error", "out-op", "boundary");
        ScaInventory wrongOwner = read(new SyntheticBehavior().relation(W + "CatchEventHasTrigger", "message", "call"));
        StepCall activity = call(analyze(wrongOwner, null), "call");
        assertThat(activity.state()).isEqualTo("unknown");
        assertThat(activity.relations()).extracting(BpmSourceEvidence.Link::association)
                .contains(W + "ActivityHasOperation", W + "AutomatedActivityHasServiceReference", W + "CatchEventHasTrigger");
        assertThat(activity.reasons()).isNotEmpty();
    }

    @Test
    void disconnectedStepsStillHaveCallMetadataAndNonMessageTriggersDoNotInventCalls() {
        ScaInventory sca = read(new SyntheticBehavior().node(W + "AutomatedActivity", "isolated")
                .relation(W + "ActivityHasOperation", "out-op", "isolated"));
        BehaviorReport report = analyze(sca, null);
        StepCall isolated = call(report, "isolated");
        assertThat(isolated.state()).isEqualTo("source-declared");
        assertThat(isolated.relations()).singleElement().satisfies(l -> assertThat(l.second().id()).isEqualTo("isolated"));
        assertThat(call(report, "boundary").state()).isEqualTo("no-interface-call-observed");
        assertThat(wiring(report).calls()).extracting(StepCall::location)
                .containsExactlyElementsOf(report.data().paths().processes().getFirst().steps().stream().map(s -> s.location()).toList());
    }

    @Test
    void asyncWithoutAckAndTransactionEvidenceStaysUnknown() {
        ScaInventory sca = read(new SyntheticBehavior());
        BehaviorReport report = analyze(sca, null);
        assertThat(wiring(report).interfaces().get(1).candidates().getFirst().source().operations()).singleElement().satisfies(op -> {
            assertThat(op.pattern()).isEqualTo("in-only");
            assertThat(op.input().name()).isEqualTo("Request");
            assertThat(op.faults()).singleElement().satisfies(f -> assertThat(f.element().name()).isEqualTo("FaultPayload"));
        });
        assertThat(wiring(report).calls()).isNotEmpty().allSatisfy(c -> {
            assertThat(c.acknowledgment()).isEqualTo("unknown");
            assertThat(c.transaction()).isEqualTo("unknown");
        });
    }

    @Test
    void oldDumpSourceUnavailableDoesNotInventCalls() {
        ScaInventory sca = read(new SyntheticBehavior());
        BpmComponent c = sca.bpm().getFirst();
        ScaInventory legacy = withComponents(sca, List.of(new BpmComponent(c.dc(), c.processes(), c.catalog(), null)));
        BehaviorReport report = analyze(legacy, null);
        assertThat(wiring(report).state()).isEqualTo("unavailable");
        assertThat(report.data().paths().processes().getFirst().coverage()).isEqualTo("unavailable");
        assertThat(wiring(report).interfaces()).isNotEmpty();
        assertThat(wiring(report).calls()).hasSameSizeAs(report.data().paths().processes().getFirst().steps())
                .allSatisfy(call -> assertThat(call.state()).isEqualTo("unknown"));
    }

    private ScaInventory read(SyntheticBehavior fixture) { return fixture.sca(dir.resolve("source-" + (++files) + ".sca")); }
    static StepCall call(BehaviorReport report, String id) {
        int ordinal = step(report, id);
        return wiring(report).calls().stream().filter(c -> c.step() == ordinal).findFirst().orElseThrow();
    }
}
