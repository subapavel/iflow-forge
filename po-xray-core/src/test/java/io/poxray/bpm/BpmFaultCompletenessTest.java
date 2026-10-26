package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;
import static io.poxray.bpm.BpmFaultFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BpmFaultReport.Declaration;
import io.poxray.model.BpmFaultReport.ProcessFaults;
import io.poxray.model.BpmFaultReport.Unattributed;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Separate review regressions over literal MOIN and actual source-reader output. */
class BpmFaultCompletenessTest {
    @TempDir Path dir;

    @Test
    void referenceQNameWithMappingPartPayloadCannotClaimCompleteCondition() {
        ScaInventory inventory = read(conditionFixture()
                .raw(R + "Reference", "bad-reference", "", "")
                .raw(M + "MappingPart", "wrong-qname", "name='not-a-QName'", "")
                .relation(R + "ExpressionHasSteps", "bad-reference", "condition")
                .relation(R + "ReferenceHasQName", "wrong-qname", "bad-reference"));
        assertUnknownGrammar(inventory, R + "ReferenceHasQName", "wrong-qname", "bad-reference",
                M + "MappingPart", R + "Reference");
    }

    @Test
    void mappingRootsWithConditionPayloadCannotClaimCompleteCondition() {
        ScaInventory inventory = read(conditionFixture()
                .raw(R + "FunctionInvocation", "call", "", "")
                .raw(R + "FunctionInterface", "function", "name='synthetic'", "")
                .raw(M + "Mapping", "arguments", "", "")
                .raw(R + "Condition", "wrong-part", "", "")
                .relation(R + "ExpressionHasSteps", "call", "condition")
                .relation(R + "FunctionInvocationHasFunctionInterface", "function", "call")
                .relation(R + "FunctionInvocationHasParameterMapping", "arguments", "call")
                .relation(M + "MappingContainsMappingParts", "wrong-part", "arguments"));
        assertUnknownGrammar(inventory, M + "MappingContainsMappingParts", "wrong-part", "arguments",
                R + "Condition", M + "Mapping");
    }

    @Test
    void referenceQNameOwnedByLiteralCannotClaimCompleteCondition() {
        ScaInventory inventory = read(conditionFixture()
                .raw(R + "Literal", "wrong-owner", "value='false'", "")
                .raw("XSD1.key.QName", "qname", "name='misowned' namespace='urn:synthetic'", "")
                .relation(R + "ExpressionHasSteps", "wrong-owner", "condition")
                .relation(R + "ReferenceHasQName", "qname", "wrong-owner"));
        assertUnknownGrammar(inventory, R + "ReferenceHasQName", "qname", "wrong-owner",
                "XSD1.key.QName", R + "Literal");
    }

    @Test
    void malformedBoundaryAttachmentCannotHideIndependentStartTrigger() {
        ScaInventory inventory = read(chain().error("error", "true", "false")
                .attach("map", "start").trigger("start", "error"));
        Link attachment = sourceLink(inventory, W + "ActivityHasBoundaryEvent", "start", "map");
        Link trigger = sourceLink(inventory, W + "CatchEventHasTrigger", "error", "start");
        assertThat(attachment.first().candidates().getFirst().metaclass()).isEqualTo(W + "StartEvent");
        ProcessFaults process = process(analyze(inventory));
        assertThat(process.declarations()).filteredOn(Declaration::role, "boundary-trigger").singleElement()
                .satisfies(row -> {
                    assertThat(row.state()).isEqualTo("unknown");
                    assertThat(row.owner().id()).isEqualTo("map");
                    assertThat(row.event().id()).isEqualTo("start");
                    assertThat(row.evidence().links()).contains(attachment);
                });
        assertThat(process.declarations()).filteredOn(Declaration::role, "start-trigger").singleElement()
                .satisfies(row -> {
                    assertThat(row.owner()).isEqualTo(trigger.second());
                    assertThat(row.event()).isNull();
                    assertThat(row.definition()).isEqualTo(trigger.first());
                    assertThat(row.state()).isEqualTo("declared");
                    assertThat(row.evidence().links()).contains(trigger);
                    assertThat(row.evidence().elements()).extracting(Element::id).contains("start", "error");
                });
        assertThat(process.coverage()).isEqualTo("incomplete");
    }

    @Test
    void boundaryScopeConflictWithinOneProcessCannotDeclareValidFault() {
        ScaInventory inventory = read(nested().error("error", "true", "false")
                .boundary("child-map", "boundary", "error").member("boundary", "pool"));
        Link membership = sourceLink(inventory, W + "ScopeContainsScopeObjects", "boundary", "pool");
        Link attachment = sourceLink(inventory, W + "ActivityHasBoundaryEvent", "boundary", "child-map");
        var paths = BpmPathAnalysis.of(List.of(inventory), List.of("synthetic"));
        assertThat(paths.processes()).hasSize(1);
        assertThat(paths.processes().getFirst().arcs()).anySatisfy(arc ->
                assertThat(arc.reasons()).contains("conflicting-boundary-scope"));
        ProcessFaults process = process(analyze(inventory));
        assertThat(process.declarations()).hasSize(1);
        Declaration row = process.declarations().getFirst();
        assertThat(row.owner().id()).isEqualTo("child-map");
        assertThat(row.event().id()).isEqualTo("boundary");
        assertThat(row.scopes()).singleElement().satisfies(scope -> {
            assertThat(scope.owner()).isEqualTo(location(inventory, "child-map"));
            assertThat(scope.path()).containsExactly(location(inventory, "host"), location(inventory, "pool"));
        });
        assertThat(row.state()).isEqualTo("unknown");
        assertThat(row.reasons()).isNotEmpty();
        assertThat(row.evidence().links()).contains(membership, attachment);
        assertThat(row.evidence().elements()).extracting(Element::location)
                .contains(location(inventory, "boundary"), location(inventory, "host"), location(inventory, "pool"));
        assertThat(process.coverage()).isEqualTo("incomplete");
    }

    @Test
    void conditionSharedAcrossRealProcessesRemainsUnattributed() {
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put("src/bpmn/a-process.bpmn", sharedCatchProcess("a-", "First synthetic process"));
        partitions.put("src/bpmn/b-process.bpmn", sharedCatchProcess("b-", "Second synthetic process"));
        partitions.put(METADATA, partition(element(R + "Expression", "condition", "stringExpression='payload'", "")
                + element(R + "Reference", "read", "", "")
                + element("XSD1.key.QName", "read-q", "name='payload' namespace='urn:synthetic'", "")
                + link(W + "CatchEventContainsExpression", "shared-catch", "condition")
                + link(R + "ExpressionHasSteps", "read", "condition")
                + link(R + "ReferenceHasQName", "read-q", "read")));
        ScaInventory inventory = PoXray.read(SyntheticBpmSca.write(dir.resolve("shared.sca"), partitions));
        assertThat(inventory.bpm().getFirst().processes()).hasSize(2);
        Link condition = sourceLink(inventory, W + "CatchEventContainsExpression", "shared-catch", "condition");
        assertThat(condition.first().resolution()).isEqualTo("ambiguous");
        assertThat(condition.first().candidates()).hasSize(2).allSatisfy(candidate -> assertThat(candidate.included()).isTrue());
        BpmFaultReport report = analyze(inventory);
        assertThat(report.processes()).hasSize(2);
        assertThat(report.unattributed()).filteredOn(Unattributed::kind, "catch-condition-only").singleElement()
                .satisfies(row -> {
                    assertThat(row.input()).isEqualTo(1);
                    assertThat(row.component()).isEqualTo(1);
                    assertThat(row.evidence().links()).contains(condition);
                    assertThat(row.evidence().elements()).extracting(Element::location)
                            .containsAll(locations(inventory, "shared-catch"));
                    assertThat(row.evidence().elements()).extracting(Element::id).contains("condition", "read", "read-q");
                    assertThat(row.reasons()).isNotEmpty();
                });
        assertThat(report.processes()).allSatisfy(process -> {
            assertThat(process.declarations()).isEmpty();
            assertThat(process.coverage()).isEqualTo("incomplete");
            assertThat(process.reasons()).isNotEmpty();
        });
        assertThat(report.issues()).anySatisfy(issue -> assertThat(issue.code()).isEqualTo("BPM_FAULT_UNATTRIBUTED_SOURCE"));
    }

    private static BpmFaultFixture conditionFixture() {
        return chain().error("error", "true", "false").trigger("start", "error")
                .condition("start", "condition", "payload")
                .reference("condition", "independent", "payload", "urn:synthetic");
    }

    private void assertUnknownGrammar(ScaInventory inventory, String association, String child, String parent,
            String childKind, String parentKind) {
        Link original = sourceLink(inventory, association, child, parent);
        assertThat(original.first().resolution()).isEqualTo("unique");
        assertThat(original.second().resolution()).isEqualTo("unique");
        assertThat(original.first().candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.metaclass()).isEqualTo(childKind);
            assertThat(candidate.included()).isTrue();
        });
        assertThat(original.second().candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.metaclass()).isEqualTo(parentKind);
            assertThat(candidate.included()).isTrue();
        });
        ProcessFaults process = process(analyze(inventory));
        assertThat(process.declarations()).hasSize(1);
        Declaration row = process.declarations().getFirst();
        assertThat(row.evidence().links()).contains(original);
        assertThat(row.evidence().elements()).extracting(Element::id)
                .contains(child, parent, "independent", "independent-q");
        assertThat(row.conditions()).hasSize(1);
        assertThat(row.conditions().getFirst().evidence()).contains(new Fact("link", original.location()));
        assertThat(process.coverage()).as("wrong parent/child role must prevent complete condition coverage").isEqualTo("incomplete");
        assertThat(row.conditions().getFirst().reasons()).isNotEmpty();
        assertThat(row.reasons()).isNotEmpty();
    }

    private ScaInventory read(BpmFaultFixture fixture) { return fixture.read(dir.resolve("faults.sca")); }
    private static BpmFaultReport analyze(ScaInventory inventory) {
        return BpmFaultAnalysis.of(List.of(inventory), List.of("synthetic"));
    }
    private static ProcessFaults process(BpmFaultReport report) {
        assertThat(report.processes()).hasSize(1);
        return report.processes().getFirst();
    }
    private static Link sourceLink(ScaInventory inventory, String association, String first, String second) {
        List<Link> links = evidence(inventory).links().stream().filter(candidate -> association.equals(candidate.association())
                && first.equals(candidate.first().id()) && second.equals(candidate.second().id())).toList();
        assertThat(links).as("actual source link").hasSize(1);
        return links.getFirst();
    }
    private static String sharedCatchProcess(String prefix, String name) {
        String nodes = element(W + "StartEvent", "shared-catch", "", "")
                + element(W + "EndEvent", prefix + "end", "", "")
                + element(W + "SequenceConnector", prefix + "flow", "", "");
        String edges = link(W + "SourceOfIncomingConnectors", "shared-catch", prefix + "flow")
                + link(W + "TargetOfOutgoingConnectors", prefix + "end", prefix + "flow");
        return SyntheticBpmSca.process(prefix, name, nodes, edges, "shared-catch", prefix + "end", prefix + "flow");
    }
}
