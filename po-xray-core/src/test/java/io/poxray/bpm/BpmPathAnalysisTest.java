package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Source-occurrence acceptance; connectivity describes retained structure, never execution. */
class BpmPathAnalysisTest {
    static final String BOOLEAN = "4743E048A5B22940980411DC83220011252E548A";
    static final String W = "Galaxy.Workflow.";
    static final String LOOP_ATTACHMENT = W + "EmbeddedScopeContainsLoopCharcateristics";
    @TempDir Path dir;
    private int files;

    @Test
    void chainHasStableWitnessesAndDisconnectedBranchWhileMissingConditionDoesNotPrune() {
        Fixture f = chain().node("EndEvent", "island", "pool").conditional("maybe", "middle", "end", "pool");
        ScaInventory inventory = read(f);
        ProcessPaths p = paths(inventory);
        assertThat(p.coverage()).isEqualTo("complete");
        assertThat(p.steps()).extracting(Step::id).containsExactly("start", "middle", "end", "island");
        assertThat(p.steps()).extracting(Step::ordinal).containsExactly(1, 2, 3, 4);
        assertThat(step(p, "start").witness()).isEmpty();
        assertThat(step(p, "start").reasons()).contains("root-start");
        assertConnected(p, "middle", 1);
        assertConnected(p, "end", 1, 2);
        assertThat(step(p, "island").connectivity()).isEqualTo("disconnected");
        assertThat(step(p, "island").witness()).isEmpty();
        assertThat(arc(p, "maybe").condition().state()).isEqualTo("missing");
        assertThat(arc(p, "first").condition().state()).isEqualTo("unconditional");
        assertThat(arc(p, "first").location()).isEqualTo(location(inventory, "first"));
        assertThat(step(p, "middle").scopes()).containsExactly(location(inventory, "pool"));
        assertThat(arc(p, "first").sources().getFirst().candidates()).containsExactly(location(inventory, "start"));
        assertThat(arc(p, "first").targets().getFirst().candidates()).containsExactly(location(inventory, "middle"));
        assertLinkFacts(inventory, arc(p, "first").evidence(), W + "SourceOfIncomingConnectors", "first");
        assertLinkFacts(inventory, arc(p, "first").evidence(), W + "TargetOfOutgoingConnectors", "first");
    }

    @Test
    void typedFalseAndTrueKeepLiteralTypeEvidenceAndRemainStructurallyConnected() {
        for (String value : List.of("false", "true")) {
            Fixture f = conditionFixture().literal("expr", "lit", value, BOOLEAN, null);
            ScaInventory inventory = read(f);
            ProcessPaths p = paths(inventory);
            Arc arc = arc(p, "condition");
            assertThat(p.coverage()).isEqualTo("complete");
            assertThat(arc.condition().state()).isEqualTo("constant-" + value);
            assertThat(arc.condition().text()).isEqualTo(value);
            assertConnected(p, "end", 1);
            assertThat(arc.condition().evidence()).contains(new Fact("element", location(inventory, "expr")),
                    new Fact("element", location(inventory, "lit")));
            assertLinkFacts(inventory, arc.condition().evidence(), "Galaxy.Rule.LiteralHasSimpleDataType", "lit");
            assertLinkFacts(inventory, arc.condition().evidence(), "Galaxy.Rule.ExpressionHasSteps", "expr");
            assertLinkFacts(inventory, arc.condition().evidence(), W + "ConditionalSequenceConnectorContainsExpression", "condition");
        }
    }

    @Test
    void renderedFalseNeverSubstitutesForTypedLiteralEvidence() {
        List<Fixture> fixtures = new ArrayList<>();
        fixtures.add(conditionFixture().raw("Galaxy.Rule.Expression", "expr", "stringExpression='false'", "")
                .raw("Galaxy.Rule.Reference", "ref", "", "")
                .raw("XSD1.key.QName", "qname", "name='false'", "")
                .relation("Galaxy.Rule.ExpressionHasSteps", "ref", "expr")
                .relation("Galaxy.Rule.ReferenceHasQName", "qname", "ref"));
        fixtures.add(conditionFixture().literal("expr", "lit", "false", null, null));
        fixtures.add(conditionFixture().literal("expr", "lit", "false", BOOLEAN, "true"));
        fixtures.add(conditionFixture().literal("expr", "lit", "false", "missing-boolean", null));
        fixtures.add(conditionFixture().literal("expr", "lit", "FALSE", BOOLEAN, null));
        fixtures.add(conditionFixture().literal("expr", "lit", "false", BOOLEAN, "false")
                .raw("Galaxy.Rule.Literal", "extra", "value='true'", "")
                .relation("Galaxy.Rule.ExpressionHasSteps", "extra", "expr"));
        fixtures.add(conditionFixture().literal("expr", "lit", "false", BOOLEAN, null)
                .raw("XSD1.XsdSimpleTypeDefinition", BOOLEAN, "name='boolean'", ""));
        for (Fixture f : fixtures) {
            ProcessPaths p = paths(read(f));
            assertThat(arc(p, "condition").condition().state()).isEqualTo("unknown");
            assertThat(arc(p, "condition").condition().reasons()).isNotEmpty();
            assertThat(p.coverage()).isEqualTo("complete");
            assertConnected(p, "end", 1);
        }
    }

    @Test
    void blankEditorAllowsTreeConstantAndNonblankEditorTextIsPreservedExactly() {
        for (String editor : List.of("   ", "  false  ")) {
            ProcessPaths p = paths(read(conditionFixture().literal("expr", "lit", "false", BOOLEAN, editor)));
            assertThat(arc(p, "condition").condition().state()).isEqualTo("constant-false");
            assertThat(arc(p, "condition").condition().text()).isEqualTo(editor);
        }
    }

    @Test
    void missingUnresolvedAndAmbiguousConditionsRemainDistinctAndRetainAllLinks() {
        Fixture missing = new Fixture().node("StartEvent", "start", "pool").node("EndEvent", "end", "pool")
                .conditional("condition", "start", "end", "pool");
        assertThat(arc(paths(read(missing)), "condition").condition().state()).isEqualTo("missing");
        ScaInventory unresolved = read(conditionFixture());
        assertThat(arc(paths(unresolved), "condition").condition().state()).isEqualTo("unresolved");
        assertLinkFacts(unresolved, arc(paths(unresolved), "condition").condition().evidence(),
                W + "ConditionalSequenceConnectorContainsExpression", "condition");
        for (Fixture f : List.of(
                conditionFixture().literal("expr", "lit", "false", BOOLEAN, null)
                        .relation(W + "ConditionalSequenceConnectorContainsExpression", "expr", "condition"),
                conditionFixture().literal("expr", "lit", "false", BOOLEAN, null)
                        .raw("Galaxy.Rule.Expression", "expr", "stringExpression='true'", ""))) {
            ScaInventory inventory = read(f);
            Arc a = arc(paths(inventory), "condition");
            assertThat(a.condition().state()).isEqualTo("ambiguous");
            assertLinkFacts(inventory, a.condition().evidence(), W + "ConditionalSequenceConnectorContainsExpression", "condition");
        }
    }

    @Test
    void unexpectedConditionOnSequenceConnectorIsUnknown() {
        Fixture f = new Fixture().node("StartEvent", "start", "pool").node("EndEvent", "end", "pool")
                .sequence("condition", "start", "end", "pool")
                .relation(W + "ConditionalSequenceConnectorContainsExpression", "expr", "condition")
                .literal("expr", "lit", "false", BOOLEAN, null);
        assertThat(arc(paths(read(f)), "condition").condition().state()).isEqualTo("unknown");
    }

    @Test
    void invalidRootSelectionsNeverReuseLegacyFirstPoolFallback() {
        List<Fixture> fixtures = new ArrayList<>();
        for (String active : Arrays.asList(null, "invalid", "false", "TRUE")) {
            fixtures.add(chain().active(active));
        }
        fixtures.add(chain().pool("other", "true"));
        fixtures.add(chain().pool("other", null));
        fixtures.add(chain().relation(W + "CollaborationContainsPools", "pool", "collab"));
        fixtures.add(chain().raw(W + "Pool", "pool", "active='false'", ""));
        for (Fixture f : fixtures) {
            ProcessPaths p = paths(read(f));
            assertThat(p.coverage()).isEqualTo("incomplete");
            assertThat(p.steps()).allSatisfy(s -> assertThat(s.connectivity()).isEqualTo("unknown"));
            assertThat(p.steps()).allSatisfy(s -> assertThat(s.witness()).isEmpty());
            assertThat(p.reasons()).isNotEmpty();
        }
    }

    @Test
    void passivePoolIsNotAnAdditionalRootAndDoesNotInvalidateAnActiveRoot() {
        ProcessPaths p = paths(read(chain().pool("passive", "false").node("StartEvent", "passive-start", "passive")
                .node("EndEvent", "passive-end", "passive").sequence("passive-edge", "passive-start", "passive-end", "passive")));
        assertThat(p.coverage()).isEqualTo("complete");
        assertConnected(p, "middle", 1);
        for (String id : List.of("passive-start", "passive-end")) {
            assertThat(step(p, id).connectivity()).isEqualTo("unknown");
            assertThat(step(p, id).reasons()).contains("inactive-pool");
            assertThat(step(p, id).witness()).isEmpty();
        }
    }

    @Test
    void noRootStartPreventsNegativeConclusion() {
        ProcessPaths p = paths(read(new Fixture().node("EndEvent", "end", "pool")));
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
    }

    @Test
    void duplicateEndpointIdsPreserveCandidatesAndDoNotChooseFirstOccurrence() {
        Fixture f = chain().node("EndEvent", "duplicate", "pool").node("MappingActivity", "duplicate", "pool")
                .sequence("bad", "middle", "duplicate", "pool");
        ScaInventory inventory = read(f);
        ProcessPaths p = paths(inventory);
        Arc bad = arc(p, "bad");
        assertThat(bad.resolution()).isEqualTo("ambiguous");
        assertThat(bad.targets().getFirst().id()).isEqualTo("duplicate");
        assertThat(bad.targets().getFirst().candidates()).containsExactlyElementsOf(locations(inventory, "duplicate"));
        assertThat(p.steps().stream().filter(s -> "duplicate".equals(s.id()))).hasSize(2)
                .allSatisfy(s -> assertThat(s.connectivity()).isEqualTo("unknown"));
        assertConnected(p, "middle", 1);
        assertThat(p.coverage()).isEqualTo("incomplete");
    }

    @Test
    void duplicateSourceLinksAndDuplicateConnectorOccurrencesAreNotDeduplicated() {
        ScaInventory inventory = read(chain().relation(W + "SourceOfIncomingConnectors", "start", "first"));
        Arc a = arc(paths(inventory), "first");
        assertThat(a.resolution()).isEqualTo("ambiguous");
        assertThat(a.sources()).extracting(BpmPathReport.Endpoint::id).containsExactly("start", "start");
        assertLinkFacts(inventory, a.evidence(), W + "SourceOfIncomingConnectors", "first");
        ProcessPaths duplicate = paths(read(chain().node("SequenceConnector", "first", "pool")));
        assertThat(duplicate.arcs()).filteredOn(row -> "first".equals(row.id())).hasSize(2)
                .allSatisfy(row -> assertThat(row.resolution()).isEqualTo("ambiguous"));
    }

    @Test
    void missingNullBlankAndWrongKindSequenceEndpointsRemainVisible() {
        for (String endpoint : Arrays.asList(null, "", "missing")) {
            ProcessPaths p = paths(read(new Fixture().node("StartEvent", "start", "pool")
                    .node("EndEvent", "end", "pool").sequence("broken", "start", endpoint, "pool")));
            Arc a = arc(p, "broken");
            assertThat(a.resolution()).isEqualTo("missing");
            assertThat(a.targets()).hasSize(1);
            assertThat(a.targets().getFirst().id()).isEqualTo(endpoint);
            assertThat(a.targets().getFirst().candidates()).isEmpty();
            assertThat(p.coverage()).isEqualTo("incomplete");
            assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
        }
        ProcessPaths absent = paths(read(new Fixture().node("StartEvent", "start", "pool")
                .node("SequenceConnector", "broken", "pool")
                .relation(W + "SourceOfIncomingConnectors", "start", "broken")));
        assertThat(arc(absent, "broken").targets()).isEmpty();
        assertThat(arc(absent, "broken").resolution()).isEqualTo("missing");
        ProcessPaths wrongKind = paths(read(chain().node("DataObject", "data", "pool")
                .sequence("wrong", "middle", "data", "pool")));
        assertThat(arc(wrongKind, "wrong").resolution()).isEqualTo("invalid");
        assertThat(wrongKind.coverage()).isEqualTo("incomplete");
    }

    @Test
    void originalArtificialJoinAndItsOutgoingArcSurviveLegacyContraction() {
        Fixture f = new Fixture().node("StartEvent", "start", "pool")
                .namedNode("UncontrolledJoinGateway", "join", "pool", "Artificial Uncontrolled Join 1")
                .node("EndEvent", "end", "pool").sequence("in", "start", "join", "pool")
                .sequence("out", "join", "end", "pool");
        ScaInventory inventory = read(f);
        assertThat(inventory.bpm().getFirst().processes().getFirst().nodes()).noneMatch(n -> "join".equals(n.id()));
        assertThat(inventory.bpm().getFirst().processes().getFirst().flows()).noneMatch(a -> "out".equals(a.id()));
        ProcessPaths p = paths(inventory);
        assertThat(step(p, "join").kind()).isEqualTo("joinGateway");
        assertThat(p.arcs()).extracting(Arc::id).containsExactly("in", "out");
        assertConnected(p, "join", 1);
        assertConnected(p, "end", 1, 2);
    }

    @Test
    void unknownMemberAndUnscopedKnownObjectMakeNegativeConclusionsUnknown() {
        for (Fixture f : List.of(chain().node("FutureControl", "unknown", "pool"),
                chain().node("EndEvent", "unscoped", null),
                chain().sequence("unscoped-edge", "middle", "end", null))) {
            ProcessPaths p = paths(read(f));
            assertThat(p.coverage()).isEqualTo("incomplete");
            assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
            assertConnected(p, "middle", 1);
        }
        ProcessPaths unknown = paths(read(chain().node("FutureControl", "unknown", "pool")));
        assertThat(step(unknown, "unknown").kind()).isEqualTo("unknown");
        assertThat(step(unknown, "unknown").connectivity()).isEqualTo("unknown");
    }

    @Test
    void unsupportedControlRelationIsDiagnosedWhileUnrelatedMetadataDoesNotCreateGraphGap() {
        Fixture f = chain().node("DataObject", "data", "pool")
                .raw("Galaxy.Mapping.Mapping", "mapping", "", "")
                .relation("Custom.MappingMetadata", "data", "mapping");
        assertThat(paths(read(f)).coverage()).isEqualTo("complete");
        f.relation(W + "FutureControlRelation", "middle", "end");
        ScaInventory inventory = read(f);
        BpmPathReport report = report(inventory);
        ProcessPaths p = report.processes().getFirst();
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
        assertConnected(p, "middle", 1);
        String diagnostics = Json.write(issues(report, "BPM_PATH_UNSUPPORTED_CONTROL_RELATION"));
        assertThat(issues(report, "BPM_PATH_UNSUPPORTED_CONTROL_RELATION")).hasSize(1);
        assertThat(diagnostics).contains("FutureControlRelation", "middle", "end", PROCESS);
    }

    @Test
    void connectedEmbeddedHostUsesDerivedEntryAndDisconnectedHostDoesNotSeedChild() {
        Fixture f = nested().node("EmbeddedScope", "isolated-host", "pool")
                .node("StartEvent", "isolated-start", "isolated-host");
        ScaInventory inventory = read(f);
        ProcessPaths p = paths(inventory);
        assertThat(p.coverage()).isEqualTo("complete");
        assertThat(p.arcs()).extracting(Arc::kind).containsExactly("sequence", "sequence", "embedded-entry", "embedded-entry");
        assertConnected(p, "host", 1);
        assertConnected(p, "child-start", 1, 3);
        assertConnected(p, "child-end", 1, 3, 2);
        assertThat(step(p, "isolated-host").connectivity()).isEqualTo("disconnected");
        assertThat(step(p, "isolated-start").connectivity()).isEqualTo("disconnected");
        Arc entry = p.arcs().get(2);
        assertThat(entry.location()).isNull();
        assertThat(entry.sources().getFirst().candidates()).containsExactly(location(inventory, "host"));
        assertThat(entry.targets().getFirst().candidates()).containsExactly(location(inventory, "child-start"));
        assertThat(entry.condition().state()).isEqualTo("unknown");
        assertThat(entry.evidence()).containsAll(evidence(inventory).links().stream()
                .filter(l -> l.association().equals(W + "ScopeContainsScopeObjects")
                        && "child-start".equals(l.first().id()) && "host".equals(l.second().id()))
                .map(l -> new Fact("link", l.location())).toList());
        assertThat(p.arcs()).noneMatch(a -> a.sources().stream().anyMatch(e -> "child-end".equals(e.id())));
    }

    @Test
    void crossScopeSequenceAndEmbeddedScopeWithoutStartDoNotProveDisconnection() {
        ProcessPaths cross = paths(read(nested().sequence("cross", "start", "child-end", "pool")));
        assertThat(cross.coverage()).isEqualTo("incomplete");
        assertThat(arc(cross, "cross").resolution()).isEqualTo("invalid");
        assertConnected(cross, "host", 1);
        ProcessPaths noStart = paths(read(chain().node("EmbeddedScope", "empty-host", "pool")));
        assertThat(noStart.coverage()).isEqualTo("incomplete");
        assertThat(step(noStart, "empty-host").connectivity()).isEqualTo("unknown");
    }

    @Test
    void uniqueBoundaryAttachmentInheritsOwnerScopeAndNamesStructuralWitness() {
        ScaInventory inventory = read(boundary());
        ProcessPaths p = paths(inventory);
        assertThat(p.coverage()).isEqualTo("complete");
        assertConnected(p, "boundary", 1, 2);
        assertThat(step(p, "boundary").scopes()).containsExactly(location(inventory, "pool"));
        assertThat(step(p, "boundary").reasons()).contains("boundary-scope-from-owner");
        Arc attachment = p.arcs().get(1);
        assertThat(attachment.kind()).isEqualTo("boundary-attachment");
        assertThat(attachment.location()).isNull();
        assertThat(attachment.sources().getFirst().id()).isEqualTo("middle");
        assertThat(attachment.targets().getFirst().id()).isEqualTo("boundary");
        assertThat(attachment.resolution()).isEqualTo("resolved");
        assertLinkFacts(inventory, attachment.evidence(), W + "ActivityHasBoundaryEvent", "middle");
        assertThat(attachment.condition().state()).isEqualTo("unknown");
    }

    @Test
    void duplicateBoundaryLinksOwnersAndWrongKindsCannotBeTraversed() {
        List<Fixture> bad = List.of(
                boundary().relation(W + "ActivityHasBoundaryEvent", "boundary", "middle"),
                boundary().node("MappingActivity", "other", "pool").relation(W + "ActivityHasBoundaryEvent", "boundary", "other"),
                chain().node("BoundaryEvent", "boundary", null).relation(W + "ActivityHasBoundaryEvent", "boundary", "start"),
                chain().relation(W + "ActivityHasBoundaryEvent", "end", "middle"),
                boundary().pool("passive", "false").member("boundary", "passive"),
                boundary().member("boundary", "pool").pool("passive", "false").member("boundary", "passive"));
        for (Fixture f : bad) {
            ProcessPaths p = paths(read(f));
            assertThat(p.coverage()).isEqualTo("incomplete");
            assertThat(p.arcs()).filteredOn(a -> a.kind().equals("boundary-attachment")).isNotEmpty()
                    .allSatisfy(a -> assertThat(a.resolution()).isIn("invalid", "ambiguous"));
            if (p.steps().stream().anyMatch(s -> "boundary".equals(s.id()))) {
                assertThat(step(p, "boundary").connectivity()).isEqualTo("unknown");
            }
            assertConnected(p, "middle", 1);
        }
        ProcessPaths repeated = paths(read(bad.getFirst()));
        assertThat(repeated.arcs()).filteredOn(a -> a.kind().equals("boundary-attachment")).hasSize(2);
    }

    @Test
    void connectorCycleTerminatesAndUsesFirstStableWitness() {
        ScaInventory inventory = read(chain().sequence("back", "middle", "start", "pool")
                .sequence("finish", "middle", "end", "pool").sequence("shorter-later", "start", "end", "pool"));
        ProcessPaths p = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> paths(inventory));
        assertThat(p.coverage()).isEqualTo("complete");
        assertConnected(p, "middle", 1);
        assertConnected(p, "end", 4);
        assertThat(step(p, "start").witness()).isEmpty();
    }

    @Test
    void multipleMembershipsAndCapturedScopeCycleRemainFiniteAndUnknown() {
        ProcessPaths multiple = paths(read(nested().member("child-end", "pool")));
        assertThat(multiple.coverage()).isEqualTo("incomplete");
        assertThat(step(multiple, "child-end").scopes()).hasSize(2);
        assertThat(step(multiple, "child-end").connectivity()).isEqualTo("unknown");

        // ProcessReader.visitScope currently has no cycle guard. Capture safe parsed evidence first;
        // only this adversarial scope-cycle case adds a source link to that immutable evidence copy.
        ScaInventory parsed = read(nested().node("EmbeddedScope", "inner", "host").node("StartEvent", "inner-start", "inner"));
        BpmSourceEvidence original = evidence(parsed);
        List<BpmSourceEvidence.Link> links = new ArrayList<>(original.links());
        links.add(new BpmSourceEvidence.Link(new Locator(ARCHIVE, PROCESS, links.size() + 1),
                W + "ScopeContainsScopeObjects", endpoint(parsed, "host"), endpoint(parsed, "inner")));
        BpmSourceEvidence cyclic = new BpmSourceEvidence(1, "bpm-source-v1", original.indexedPartitions(),
                original.processPartitions(), original.elements(), links);
        ProcessPaths p = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> paths(withEvidence(parsed, cyclic)));
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(step(p, "host").scopes()).hasSize(2);
        assertThat(step(p, "host").connectivity()).isEqualTo("unknown");
        assertThat(p.reasons()).isNotEmpty();
        assertThat(original.links()).hasSize(links.size() - 1);
    }

    @Test
    void loopDeclarationsPreserveRawFlagsAndOnlyExactSupportedCompletion() {
        String[] raw = {"true", "false", null, "TRUE"};
        String[] modes = {"sequential", "parallel", "unknown", "unknown"};
        String[] completion = {"all", "one", null, "ALL"};
        for (int i = 0; i < raw.length; i++) {
            Fixture f = nested().raw(W + "MultiInstanceLoopCharacteristics", "loop",
                    attr("sequential", raw[i]) + " " + attr("flowCondition", completion[i]), "")
                    .relation(LOOP_ATTACHMENT, "loop", "host")
                    .raw("Galaxy.Rule.Expression", "loop-expression", "stringExpression='items'", "")
                    .node("DataObject", "item", "host")
                    .node("DataObject", "completed", "host")
                    .relation(W + "MILoopCharacteristicsContainsExpression", "loop-expression", "loop")
                    .relation(W + "MILoopCharacteristicsContainsCurrentCollectionItemDO", "item", "loop")
                    .relation(W + "MILoopCharacteristicsHasNumberOfCompletedIterationsDO", "completed", "loop");
            ScaInventory inventory = read(f);
            ProcessPaths p = paths(inventory);
            assertThat(p.coverage()).isEqualTo("complete");
            assertThat(p.loops()).hasSize(1);
            BpmPathReport.Loop loop = p.loops().getFirst();
            assertThat(loop.id()).isEqualTo("loop");
            assertThat(loop.location()).isEqualTo(location(inventory, "loop"));
            assertThat(loop.hosts()).containsExactly(location(inventory, "host"));
            assertThat(loop.mode()).isEqualTo(modes[i]);
            assertThat(loop.sequential()).isEqualTo(new BpmPathReport.Flag(raw[i] != null, raw[i],
                    i == 0 ? "true" : i == 1 ? "false" : "unknown"));
            assertThat(loop.flowCondition()).isEqualTo(completion[i]);
            assertThat(loop.completion()).isEqualTo(i == 0 ? "all" : "unknown");
            assertLinkFacts(inventory, loop.evidence(), LOOP_ATTACHMENT, "host");
            for (String association : List.of("MILoopCharacteristicsContainsExpression",
                    "MILoopCharacteristicsContainsCurrentCollectionItemDO", "MILoopCharacteristicsHasNumberOfCompletedIterationsDO")) {
                assertLinkFacts(inventory, loop.evidence(), W + association, "loop");
            }
        }
    }

    @Test
    void unattachedLoopIsRetainedAsUnknownWithoutInventingHost() {
        ProcessPaths p = paths(read(chain().raw(W + "MultiInstanceLoopCharacteristics", "orphan-loop",
                "sequential='true' flowCondition='all'", "")));
        assertThat(p.coverage()).isEqualTo("complete");
        assertThat(p.loops()).hasSize(1);
        BpmPathReport.Loop loop = p.loops().getFirst();
        assertThat(loop.id()).isEqualTo("orphan-loop");
        assertThat(loop.hosts()).isEmpty();
        assertThat(loop.mode()).isEqualTo("unknown");
        assertThat(loop.completion()).isEqualTo("unknown");
        assertThat(loop.sequential().interpretation()).isEqualTo("true");
        assertThat(loop.flowCondition()).isEqualTo("all");
        assertThat(loop.reasons()).contains("unattached-loop");
    }

    @Test
    void duplicateLoopAttachmentsAndDistinctDeclarationsOnSameHostRemainAmbiguous() {
        Fixture single = nested().raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='true' flowCondition='all'", "")
                .relation(LOOP_ATTACHMENT, "loop", "host").relation(LOOP_ATTACHMENT, "loop", "host");
        Fixture distinct = nested().raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='true' flowCondition='all'", "")
                .raw(W + "MultiInstanceLoopCharacteristics", "loop-two", "sequential='false' flowCondition='all'", "")
                .relation(LOOP_ATTACHMENT, "loop", "host").relation(LOOP_ATTACHMENT, "loop-two", "host");
        for (Fixture f : List.of(single, distinct)) {
            ScaInventory inventory = read(f);
            BpmPathReport report = report(inventory);
            ProcessPaths p = report.processes().getFirst();
            assertThat(p.loops()).hasSize(f == single ? 1 : 2).allSatisfy(loop -> {
                assertThat(loop.mode()).isEqualTo("unknown");
                assertThat(loop.completion()).isEqualTo("unknown");
                assertThat(loop.reasons()).isNotEmpty();
                assertThat(loop.hosts()).contains(location(inventory, "host"));
            });
            assertThat(issues(report, "BPM_PATH_LOOP_AMBIGUOUS")).isNotEmpty();
            if (f == single) assertLinkFacts(inventory, p.loops().getFirst().evidence(), LOOP_ATTACHMENT, "host");
        }
    }

    @Test
    void danglingUnsupportedAndAmbiguousLoopEndpointsProduceExplicitDiagnostics() {
        Fixture dangling = nested().relation(LOOP_ATTACHMENT, "missing-loop", "host");
        Fixture unsupported = nested().raw(W + "StandardLoopCharacteristics", "unsupported-loop", "", "")
                .relation(LOOP_ATTACHMENT, "unsupported-loop", "host");
        for (Fixture f : List.of(dangling, unsupported)) {
            BpmPathReport report = report(read(f));
            String code = f == dangling ? "BPM_PATH_LOOP_UNRESOLVED" : "BPM_PATH_LOOP_UNSUPPORTED";
            assertThat(report.processes().getFirst().loops()).isEmpty();
            assertThat(issues(report, code)).isNotEmpty();
            assertThat(Json.write(issues(report, code))).contains(PROCESS, "host",
                    f == dangling ? "missing-loop" : "unsupported-loop");
        }
        ScaInventory ambiguous = read(nested().raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='true'", "")
                .raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='false'", "")
                .relation(LOOP_ATTACHMENT, "loop", "host"));
        BpmPathReport report = report(ambiguous);
        assertThat(report.processes().getFirst().loops()).hasSize(2)
                .allSatisfy(loop -> assertThat(loop.mode()).isEqualTo("unknown"));
        assertThat(issues(report, "BPM_PATH_LOOP_AMBIGUOUS")).isNotEmpty();
        assertThat(report.processes().getFirst().loops()).extracting(BpmPathReport.Loop::location)
                .containsExactlyElementsOf(locations(ambiguous, "loop"));
    }

    @Test
    void missingAndWrongKindLoopHostsKeepRawFlagsWithoutClaimingMode() {
        for (String host : List.of("missing-host", "middle")) {
            ScaInventory inventory = read(chain().raw(W + "MultiInstanceLoopCharacteristics", "loop",
                    "sequential='false' flowCondition='all'", "").relation(LOOP_ATTACHMENT, "loop", host));
            BpmPathReport report = report(inventory);
            BpmPathReport.Loop loop = report.processes().getFirst().loops().getFirst();
            assertThat(loop.mode()).isEqualTo("unknown");
            assertThat(loop.completion()).isEqualTo("unknown");
            assertThat(loop.sequential()).isEqualTo(new BpmPathReport.Flag(true, "false", "false"));
            assertLinkFacts(inventory, loop.evidence(), LOOP_ATTACHMENT, host);
            assertThat(issues(report, host.equals("missing-host") ? "BPM_PATH_LOOP_UNRESOLVED" : "BPM_PATH_LOOP_UNSUPPORTED"))
                    .isNotEmpty();
        }
    }

    @Test
    void uniquelyRelatedCrossPartitionNodesAndLoopKeepTheirOriginalLocators() {
        Fixture f = new Fixture().node("StartEvent", "start", "pool").node("EmbeddedScope", "host", "pool")
                .sequence("enter", "start", "host", "pool").member("external-start", "host")
                .relation(LOOP_ATTACHMENT, "external-loop", "host");
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, f.xml());
        partitions.put(METADATA, partition(element(W + "StartEvent", "external-start", "", "")
                + element(W + "MultiInstanceLoopCharacteristics", "external-loop", "sequential='false' flowCondition='all'", "")));
        ScaInventory inventory = read(partitions);
        ProcessPaths p = paths(inventory);
        assertThat(p.coverage()).isEqualTo("complete");
        assertThat(step(p, "external-start").location()).isEqualTo(new Locator(ARCHIVE, METADATA, 1));
        assertConnected(p, "external-start", 1, 2);
        assertThat(p.loops().getFirst().location()).isEqualTo(new Locator(ARCHIVE, METADATA, 2));
        assertThat(p.loops().getFirst().mode()).isEqualTo("parallel");
    }

    @Test
    void unavailableEvidencePreservesLegacyNodeOrderWithUnknownAndNoFabricatedArcs() {
        ScaInventory original = read(chain());
        BpmSourceEvidence old = evidence(original);
        List<BpmSourceEvidence> unavailable = Arrays.asList(null,
                new BpmSourceEvidence(2, "bpm-source-v1", old.indexedPartitions(), old.processPartitions(), old.elements(), old.links()),
                new BpmSourceEvidence(1, "future-selection", old.indexedPartitions(), old.processPartitions(), old.elements(), old.links()));
        for (BpmSourceEvidence e : unavailable) {
            ProcessPaths p = paths(withEvidence(original, e));
            assertThat(p.coverage()).isEqualTo("unavailable");
            assertThat(p.steps()).extracting(Step::id).containsExactly("start", "middle", "end");
            assertThat(p.steps()).allSatisfy(s -> {
                assertThat(s.location()).isNull();
                assertThat(s.connectivity()).isEqualTo("unknown");
                assertThat(s.reasons()).isNotEmpty();
                assertThat(s.witness()).isEmpty();
            });
            assertThat(p.arcs()).isEmpty();
            assertThat(p.reasons()).isNotEmpty();
        }
    }

    @Test
    void exactProcessSourceIsRequiredAndUninterpretedCandidatePartitionIsNeverSilentSuccess() {
        ScaInventory original = read(chain());
        BpmComponent component = original.bpm().getFirst();
        Process p = component.processes().getFirst();
        for (String source : Arrays.asList(null, PROCESS, "elsewhere!/" + PROCESS)) {
            Process changed = new Process(p.id(), p.name(), p.documentation(), p.pool(), source, p.lanes(), p.roles(),
                    p.scopes(), p.dataObjects(), p.nodes(), p.flows(), p.annotations(), p.editorArtifacts());
            ProcessPaths result = paths(withComponents(original, List.of(
                    new BpmComponent(component.dc(), List.of(changed), component.catalog(), component.sourceEvidence()))));
            assertThat(result.coverage()).isEqualTo("unavailable");
            assertThat(result.arcs()).isEmpty();
        }
        String broken = "src/bpmn/uninterpreted.bpmn";
        BpmPathReport report = report(read(Map.of(broken, partition(element("Unknown.Process", "lost", "", "")))));
        assertThat(report.processes()).isEmpty();
        assertThat(issues(report, "BPM_PATH_UNINTERPRETED_PARTITION")).hasSize(1);
        assertThat(Json.write(issues(report, "BPM_PATH_UNINTERPRETED_PARTITION"))).contains(ARCHIVE, broken);
    }

    @Test
    void duplicateIdsAcrossInputsAndComponentsCannotSupplyMissingEndpoint() {
        ScaInventory connected = read(chain());
        ScaInventory broken = read(new Fixture().node("StartEvent", "start", "pool")
                .node("EndEvent", "end", "pool").sequence("first", "start", "middle", "pool"));
        ScaInventory combined = withComponents(broken, List.of(broken.bpm().getFirst(), connected.bpm().getFirst()));
        BpmPathReport report = BpmPathAnalysis.of(List.of(combined, connected), List.of("same label", "same label"));
        assertThat(report.inputs()).extracting(BpmPathReport.Input::ordinal).containsExactly(1, 2);
        assertThat(report.inputs()).extracting(BpmPathReport.Input::label).containsExactly("same label", "same label");
        assertThat(report.inputs()).extracting(BpmPathReport.Input::sha256)
                .containsExactly(broken.source().sha256(), connected.source().sha256());
        assertThat(report.processes()).extracting(ProcessPaths::key).containsExactly(
                new BpmPathReport.ProcessKey(1, 1, 1), new BpmPathReport.ProcessKey(1, 2, 1), new BpmPathReport.ProcessKey(2, 1, 1));
        assertThat(arc(report.processes().get(0), "first").resolution()).isEqualTo("missing");
        assertThat(report.processes().get(0).coverage()).isEqualTo("incomplete");
        assertConnected(report.processes().get(1), "middle", 1);
        assertConnected(report.processes().get(2), "middle", 1);
    }

    @Test
    void argumentsRejectNullsAndMismatchedLabelsButLegacyOptionalFieldsAreEvidenceGaps() {
        ScaInventory inventory = read(chain());
        assertThatThrownBy(() -> BpmPathAnalysis.of(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmPathAnalysis.of(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmPathAnalysis.of(List.of(inventory), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmPathAnalysis.of(Arrays.asList((ScaInventory) null), List.of("label")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmPathAnalysis.of(List.of(inventory), Arrays.asList((String) null)))
                .isInstanceOf(IllegalArgumentException.class);
        ScaInventory legacy = new ScaInventory("0.1", null, null, null, null, null, null, null);
        BpmPathReport report = report(legacy);
        assertThat(report.inputs()).containsExactly(new BpmPathReport.Input(1, "synthetic", null));
        assertThat(report.processes()).isEmpty();
        BpmPathReport empty = BpmPathAnalysis.of(List.of(), List.of());
        assertThat(empty.formatVersion()).isEqualTo(1);
        assertThat(empty.analysisVersion()).isEqualTo("bpm-paths-v1");
        assertThat(empty.inputs()).isEmpty();
        assertThat(empty.processes()).isEmpty();
        assertThat(empty.issues()).isEmpty();
    }

    private ScaInventory read(Fixture fixture) {
        return read(Map.of(PROCESS, fixture.xml()));
    }

    private ScaInventory read(Map<String, String> partitions) {
        return PoXray.read(SyntheticBpmSca.write(dir.resolve("paths-" + (++files) + ".sca"), partitions));
    }

    static BpmPathReport report(ScaInventory inventory) {
        return BpmPathAnalysis.of(List.of(inventory), List.of("synthetic"));
    }

    static ProcessPaths paths(ScaInventory inventory) {
        return report(inventory).processes().getFirst();
    }

    static Step step(ProcessPaths p, String id) {
        return p.steps().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow();
    }

    static Arc arc(ProcessPaths p, String id) {
        return p.arcs().stream().filter(a -> id.equals(a.id())).findFirst().orElseThrow();
    }

    static void assertConnected(ProcessPaths p, String id, Integer... witness) {
        assertThat(step(p, id).connectivity()).as(id).isEqualTo("connected");
        assertThat(step(p, id).witness()).as(id + " witness").containsExactly(witness);
    }

    static BpmSourceEvidence evidence(ScaInventory inventory) {
        return inventory.bpm().getFirst().sourceEvidence();
    }

    static Locator location(ScaInventory inventory, String id) {
        return locations(inventory, id).getFirst();
    }

    static List<Locator> locations(ScaInventory inventory, String id) {
        return evidence(inventory).elements().stream().filter(e -> id.equals(e.id()))
                .map(BpmSourceEvidence.Element::location).toList();
    }

    static void assertLinkFacts(ScaInventory inventory, List<Fact> actual, String association, String second) {
        List<Fact> expected = evidence(inventory).links().stream()
                .filter(l -> association.equals(l.association()) && second.equals(l.second().id()))
                .map(l -> new Fact("link", l.location())).toList();
        assertThat(expected).as("fixture has source links").isNotEmpty();
        assertThat(actual).containsAll(expected);
    }

    static List<Issue> issues(BpmPathReport report, String code) {
        return report.issues().stream().filter(i -> code.equals(i.code())).toList();
    }

    static ScaInventory withEvidence(ScaInventory inventory, BpmSourceEvidence replacement) {
        BpmComponent old = inventory.bpm().getFirst();
        return withComponents(inventory, List.of(new BpmComponent(old.dc(), old.processes(), old.catalog(), replacement)));
    }

    static ScaInventory withComponents(ScaInventory old, List<BpmComponent> components) {
        return new ScaInventory(old.schemaVersion(), old.generator(), old.source(), old.developmentComponents(),
                components, old.ejb(), old.issues(), old.unmapped());
    }

    private static BpmSourceEvidence.Endpoint endpoint(ScaInventory inventory, String id) {
        BpmSourceEvidence.Element element = evidence(inventory).elements().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
        return new BpmSourceEvidence.Endpoint(id, "unique", List.of(
                new BpmSourceEvidence.Candidate(element.location(), element.metaclass(), true)));
    }

    static Fixture chain() {
        return new Fixture().node("StartEvent", "start", "pool").node("MappingActivity", "middle", "pool")
                .node("EndEvent", "end", "pool").sequence("first", "start", "middle", "pool");
    }

    static Fixture nested() {
        return new Fixture().node("StartEvent", "start", "pool").node("EmbeddedScope", "host", "pool")
                .node("StartEvent", "child-start", "host").node("EndEvent", "child-end", "host")
                .sequence("enter", "start", "host", "pool").sequence("child", "child-start", "child-end", "host");
    }

    static Fixture boundary() {
        return chain().node("BoundaryEvent", "boundary", null).relation(W + "ActivityHasBoundaryEvent", "boundary", "middle");
    }

    static Fixture conditionFixture() {
        return new Fixture().node("StartEvent", "start", "pool").node("EndEvent", "end", "pool")
                .conditional("condition", "start", "end", "pool")
                .relation(W + "ConditionalSequenceConnectorContainsExpression", "expr", "condition");
    }

    /** Literal MOIN builder: membership and endpoint directions are part of each source fixture. */
    static final class Fixture {
        private String active = "true";
        private final StringBuilder elements = new StringBuilder();
        private final StringBuilder links = new StringBuilder();

        Fixture active(String value) { active = value; return this; }
        Fixture raw(String type, String id, String attributes, String children) {
            elements.append(element(type, id, attributes, children));
            return this;
        }
        Fixture node(String type, String id, String scope) { return namedNode(type, id, scope, id); }
        Fixture namedNode(String type, String id, String scope, String name) {
            raw(W + type, id, "", "<name originalText='" + name + "'/>");
            if (scope != null) member(id, scope);
            return this;
        }
        Fixture member(String object, String scope) { return relation(W + "ScopeContainsScopeObjects", object, scope); }
        Fixture pool(String id, String active) {
            raw(W + "Pool", id, attr("active", active), "");
            return relation(W + "CollaborationContainsPools", id, "collab");
        }
        Fixture relation(String type, String first, String second) {
            links.append(link(type, first, second));
            return this;
        }
        Fixture sequence(String id, String from, String to, String scope) { return edge("SequenceConnector", id, from, to, scope); }
        Fixture conditional(String id, String from, String to, String scope) { return edge("ConditionalSequenceConnector", id, from, to, scope); }
        private Fixture edge(String type, String id, String from, String to, String scope) {
            node(type, id, scope);
            relation(W + "SourceOfIncomingConnectors", from, id);
            return relation(W + "TargetOfOutgoingConnectors", to, id);
        }
        Fixture literal(String expression, String literal, String value, String type, String editor) {
            raw("Galaxy.Rule.Expression", expression, attr("stringExpression", editor), "");
            raw("Galaxy.Rule.Literal", literal, attr("value", value), "");
            relation("Galaxy.Rule.ExpressionHasSteps", literal, expression);
            if (type != null) relation("Galaxy.Rule.LiteralHasSimpleDataType", type, literal);
            return this;
        }
        String xml() {
            return partition(element(W + "Collaboration", "collab", "", "<name originalText='Synthetic paths'/>")
                    + element(W + "Pool", "pool", attr("active", active), "") + elements
                    + link(W + "CollaborationContainsPools", "pool", "collab") + links);
        }
    }

    static String attr(String key, String value) { return value == null ? "" : key + "='" + value + "'"; }
}
