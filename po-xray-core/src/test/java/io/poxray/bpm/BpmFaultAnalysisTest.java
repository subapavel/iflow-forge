package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.bpm.BpmFaultFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.poxray.model.BpmComponent;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BpmFaultReport.*;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Source declarations and gaps; no test infers execution or runtime fault handling. */
class BpmFaultAnalysisTest {
    @TempDir Path dir;
    private int files;

    @Test
    void activityDeclarationsDoNotBecomeAttachedHandlers() {
        BpmFaultReport report = analyze(chain().error("error", "true", "false")
                .activity("map", "error").boundary("map", "boundary", "error"));
        ProcessFaults p = process(report);
        assertThat(p.coverage()).isEqualTo("complete");
        assertThat(p.declarations()).extracting(Declaration::role).containsExactly("activity-definition", "boundary-trigger");
        assertThat(p.declarations()).extracting(Declaration::ordinal).containsExactly(1, 2);
        Declaration activity = p.declarations().getFirst();
        Declaration boundary = p.declarations().get(1);
        assertThat(activity.owner().id()).isEqualTo("map");
        assertThat(activity.event()).isNull();
        assertThat(boundary.owner().id()).isEqualTo("map");
        assertThat(boundary.event().id()).isEqualTo("boundary");
        assertThat(activity.state()).isEqualTo("declared");
        assertThat(boundary.state()).isEqualTo("declared");
        assertThat(activity.evidence().path()).isNotEqualTo(boundary.evidence().path());
    }

    @Test
    void boundaryChainRetainsOwnerEventDefinitionAndScope() {
        ScaInventory inventory = read(nested().error("error", "true", "false")
                .boundary("child-map", "boundary", "error"));
        Declaration d = only(analyze(inventory));
        assertThat(d.owner().id()).isEqualTo("child-map");
        assertThat(d.event().id()).isEqualTo("boundary");
        assertThat(d.definition().id()).isEqualTo("error");
        assertThat(d.scopes()).hasSize(1);
        Scope scope = d.scopes().getFirst();
        assertThat(scope.owner()).isEqualTo(location(inventory, "child-map"));
        assertThat(scope.state()).isEqualTo("active");
        assertThat(scope.origin()).isEqualTo("explicit-membership");
        assertThat(scope.path()).containsExactly(location(inventory, "host"), location(inventory, "pool"));
        assertThat(scope.basis()).containsAll(linkFacts(inventory, W + "ScopeContainsScopeObjects", "child-map"));
        assertThat(scope.basis()).containsAll(linkFacts(inventory, W + "ScopeContainsScopeObjects", "host"));
        assertThat(scope.basis()).containsAll(linkFacts(inventory, W + "CollaborationContainsPools", "pool"));
        List<Fact> chain = d.evidence().path().stream().filter(f -> f.kind().equals("link"))
                .filter(f -> linkFacts(inventory, W + "ActivityHasBoundaryEvent", "boundary").contains(f)
                        || linkFacts(inventory, W + "CatchEventHasTrigger", "error").contains(f)).toList();
        assertThat(chain).containsExactly(linkFacts(inventory, W + "ActivityHasBoundaryEvent", "boundary").getFirst(),
                linkFacts(inventory, W + "CatchEventHasTrigger", "error").getFirst());
        assertRecoverable(d.evidence(), scope.basis());
        assertThat(d.evidence().elements()).extracting(Element::id).contains("child-map", "boundary", "error", "host", "pool");
    }

    @Test
    void sameDefinitionUnderTwoOwnersKeepsSeparateContexts() {
        BpmFaultReport r = analyze(chain().node("AutomatedActivity", "second", "pool").error("shared", "false", "true")
                .activity("map", "shared").activity("second", "shared"));
        assertThat(process(r).declarations()).hasSize(2).extracting(d -> d.owner().id()).containsExactly("map", "second");
        assertThat(process(r).declarations()).allSatisfy(d -> {
            assertThat(d.definition().id()).isEqualTo("shared");
            assertThat(d.state()).isEqualTo("declared");
        });
        assertThat(process(r).declarations().get(0).evidence().path()).isNotEqualTo(process(r).declarations().get(1).evidence().path());
    }

    @Test
    void endResultIsNotAReplyOrAHandler() {
        BpmFaultReport r = analyze(chain().node("EndEvent", "message-end", "pool")
                .error("error", "true", "false").message("message")
                .result("end", "error").result("message-end", "message"));
        assertThat(process(r).declarations()).extracting(Declaration::role).containsExactly("end-result", "end-result");
        assertThat(process(r).declarations()).extracting(Declaration::state).containsExactly("declared", "non-error");
        assertThat(process(r).declarations()).allSatisfy(d -> assertThat(d.event()).isNull());
        assertThat(process(r).declarations()).extracting(d -> d.definitions().getFirst().kind()).containsExactly("error", "message");
    }

    @Test
    void startTriggerAndUnsupportedCatchStayOutsideAttachedFaultScope() {
        BpmFaultReport r = analyze(chain().error("error", "false", "false")
                .trigger("start", "error").trigger("map", "error"));
        assertThat(process(r).declarations()).extracting(Declaration::role).containsExactly("start-trigger", "unsupported-catch-trigger");
        assertThat(process(r).declarations()).extracting(Declaration::state).containsExactly("declared", "unknown");
        assertThat(process(r).declarations()).allSatisfy(d -> assertThat(d.event()).isNull());
        assertThat(process(r).declarations().get(1).reasons()).isNotEmpty();
        Declaration timer = only(analyze(chain().raw(W + "TimerEventDefinition", "timer", "", "").trigger("start", "timer")));
        assertThat(timer.state()).isEqualTo("unknown");
        assertThat(timer.definitions().getFirst().kind()).isEqualTo("unknown");
    }

    @Test
    void criticalTechnicalPresenceNeverDefaultsToFalse() {
        String[] values = {"true", "false", null, " TRUE "};
        for (String value : values) {
            BpmFaultReport r = analyze(chain().error("error", value, value).activity("map", "error"));
            Definition d = only(r).definitions().getFirst();
            Flag expected = new Flag(value != null, value, "true".equals(value) || "false".equals(value) ? value : "unknown");
            assertThat(d.critical()).isEqualTo(expected);
            assertThat(d.technical()).isEqualTo(expected);
            assertThat(only(r).state()).isEqualTo("declared");
            assertThat(process(r).coverage()).isEqualTo("complete");
        }
    }

    @Test
    void ambiguousDefinitionsKeepEveryCandidateAndItsOwnFlags() {
        ScaInventory inventory = read(chain().error("duplicate", "true", "false").activity("map", "duplicate")
                .external(METADATA, element(W + "ErrorEventDefinition", "duplicate", "isCritical='false' isTechnical='true'", "")));
        Declaration d = only(analyze(inventory));
        assertThat(d.definition().resolution()).isEqualTo("ambiguous");
        assertThat(d.state()).isEqualTo("unknown");
        assertThat(d.definitions()).hasSize(2).extracting(def -> def.source().location()).containsExactlyElementsOf(locations(inventory, "duplicate"));
        assertThat(d.definitions()).extracting(Definition::critical).containsExactly(new Flag(true, "true", "true"), new Flag(true, "false", "false"));
        assertThat(d.definitions()).extracting(Definition::technical).containsExactly(new Flag(true, "false", "false"), new Flag(true, "true", "true"));
        assertThat(d.builtin()).isNull();
    }

    @Test
    void builtinFallbackRequiresExactMissingIdAndKeepsCatalogueProvenance() {
        ScaInventory inventory = read(chain().trigger("start", TECHNICAL));
        BpmFaultReport r = analyze(inventory);
        Declaration d = only(r);
        assertThat(d.definition().resolution()).isEqualTo("missing");
        assertThat(d.definition().candidates()).isEmpty();
        assertThat(d.definitions()).isEmpty();
        assertThat(d.state()).isEqualTo("catalogue");
        assertThat(d.reasons()).contains("catalogue-source-substitution");
        Builtin b = d.builtin();
        assertThat(b.id()).isEqualTo(TECHNICAL);
        assertThat(b.kind()).isEqualTo("error");
        assertThat(b.name()).isEqualTo("TechnicalError");
        assertThat(b.critical()).isTrue();
        assertThat(b.technical()).isTrue();
        assertThat(b.parameter()).isEqualTo(new Parameter("parameters", "sap.com/tc/bpem/content/predefs/default/TechnicalError", "TechnicalErrorType"));
        assertThat(b.file()).isEqualTo("src/triggers/technicalerror.eventdef");
        assertThat(b.sourceDc()).isEqualTo("sap.com/tc/bpem/content/predefs/default");
        assertThat(b.resource()).endsWith("bpm-builtin-functions.json");
        assertThat(process(r).coverage()).isEqualTo("incomplete");
        assertThat(r.issues()).containsAll(inventory.issues());
        assertThat(r.issues()).isNotEmpty();
        Declaration named = only(analyze(chain().trigger("start", "TechnicalError")));
        assertThat(named.state()).isEqualTo("unknown");
        assertThat(named.builtin()).isNull();
    }

    @Test
    void exportedOrAmbiguousDefinitionNeverFallsBackToBuiltin() {
        Declaration exported = only(analyze(chain().error(TECHNICAL, "false", "false").trigger("start", TECHNICAL)));
        assertThat(exported.builtin()).isNull();
        assertThat(exported.state()).isEqualTo("declared");
        assertThat(exported.definitions().getFirst().critical()).isEqualTo(new Flag(true, "false", "false"));
        Declaration ambiguous = only(analyze(chain().error(TECHNICAL, "false", "false").trigger("start", TECHNICAL)
                .external(METADATA, element(W + "ErrorEventDefinition", TECHNICAL, "isCritical='true'", ""))));
        assertThat(ambiguous.definition().candidates()).hasSize(2);
        assertThat(ambiguous.builtin()).isNull();
        assertThat(ambiguous.state()).isEqualTo("unknown");
        Declaration excluded = only(analyze(chain().trigger("start", TECHNICAL)
                .external(METADATA, element("WSDL2.Fault", TECHNICAL, "name='TechnicalError'", ""))));
        assertThat(excluded.definition().candidates()).hasSize(1);
        assertThat(excluded.definition().candidates().getFirst().included()).isFalse();
        assertThat(excluded.builtin()).isNull();
        assertThat(excluded.state()).isEqualTo("unknown");
    }

    @Test
    void excludedFaultPayloadKeepsIdentityWithoutInventingContract() {
        ScaInventory inventory = read(chain().error("error", "true", "false").activity("map", "error").fault("error", "fault")
                .external(METADATA, element("WSDL2.Fault", "fault", "name='RemoteFault'", "")));
        Link source = evidence(inventory).links().stream().filter(l -> l.association().equals(W + "ErrorEventDefinitionHasWSDLFault"))
                .findFirst().orElseThrow();
        assertThat(source.first().candidates()).hasSize(1);
        assertThat(source.first().candidates().getFirst().included()).isFalse();
        BpmFaultReport report = analyze(inventory);
        FaultReference fault = only(report).definitions().getFirst().faults().getFirst();
        assertThat(fault.ordinal()).isEqualTo(1);
        assertThat(fault.link()).isEqualTo(source);
        assertThat(fault.state()).isEqualTo("identity-only");
        assertThat(fault.evidence().elements()).extracting(Element::id).doesNotContain("fault");
        assertThat(fault.reasons()).isNotEmpty();
        assertThat(process(report).coverage()).isEqualTo("incomplete");
    }

    @Test
    void includedFaultAndParameterKeepDirectPayloadWithoutSchemaInference() {
        Declaration d = only(analyze(chain().error("error", "true", "false").activity("map", "error").fault("error", "fault")
                .raw("WSDL2.Fault", "fault", "name='Fault|name'", "")
                .raw("XSD1.XsdElementDeclaration", "payload", "name='FaultPayload' namespace='urn:fault'", "")
                .raw("XSD1.XsdElementDeclaration", "parameter", "name='Parameter' namespace='urn:parameter'", "")
                .raw("XSD1.XsdComplexTypeDefinition", "not-direct", "name='PrivateTypeGraph'", "")
                .relation("WSDL2.ReferenceToElement", "fault", "payload")
                .relation(W + "EventDefinitionHasParameter", "parameter", "error")
                .relation("XSD1.TypeDefinitionForElementDeclaration", "payload", "not-direct")));
        Definition definition = d.definitions().getFirst();
        FaultReference fault = definition.faults().getFirst();
        assertThat(fault.state()).isEqualTo("included");
        assertThat(raw(fault.evidence(), "fault").attributes()).containsEntry("name", "Fault|name");
        assertThat(raw(fault.evidence(), "payload").attributes()).containsEntry("namespace", "urn:fault");
        assertThat(raw(definition.evidence(), "parameter").attributes()).containsEntry("namespace", "urn:parameter");
        assertThat(d.evidence().elements()).extracting(Element::id).doesNotContain("not-direct");
        assertThat(fault.evidence().links()).extracting(Link::association).contains("WSDL2.ReferenceToElement");
        assertThat(d.state()).isEqualTo("declared");
    }

    @Test
    void missingAmbiguousAndWrongKindFaultsRemainDistinct() {
        Declaration d = only(analyze(chain().error("error", "false", "true").activity("map", "error")
                .fault("error", "missing").fault("error", "duplicate").fault("error", "wrong")
                .raw("WSDL2.Fault", "duplicate", "name='First'", "").raw("WSDL2.Fault", "duplicate", "name='Second'", "")
                .raw("XSD1.key.QName", "wrong", "name='NotAFault'", "")));
        List<FaultReference> faults = d.definitions().getFirst().faults();
        assertThat(faults).extracting(FaultReference::ordinal).containsExactly(1, 2, 3);
        assertThat(faults).extracting(FaultReference::state).containsExactly("missing", "ambiguous", "wrong-kind");
        assertThat(faults.get(0).link().first().candidates()).isEmpty();
        assertThat(faults.get(1).link().first().candidates()).hasSize(2);
        assertThat(faults.get(1).evidence().elements()).filteredOn(e -> "duplicate".equals(e.id()))
                .extracting(e -> e.attributes().get("name")).containsExactly("First", "Second");
        assertThat(faults.get(2).link().first().candidates().getFirst().metaclass()).isEqualTo("XSD1.key.QName");
        assertThat(faults).allSatisfy(f -> assertThat(f.reasons()).isNotEmpty());
    }

    @Test
    void malformedAttachmentsAndMissingTriggersNeverDisappear() {
        for (BpmFaultFixture fixture : List.of(
                chain().attach("map", "missing-boundary"),
                chain().node("BoundaryEvent", "boundary", null).attach("map", "boundary"),
                chain().boundary("map", "boundary", "missing-definition"),
                chain().error("error", "true", "false").boundary("missing-owner", "boundary", "error"),
                chain().error("error", "true", "false").node("MappingActivity", "wrong-boundary", "pool")
                        .attach("map", "wrong-boundary").trigger("wrong-boundary", "error"))) {
            ProcessFaults p = process(analyze(fixture));
            assertThat(p.declarations()).isNotEmpty().anySatisfy(d -> {
                assertThat(d.role()).isEqualTo("boundary-trigger");
                assertThat(d.state()).isEqualTo("unknown");
                assertThat(d.reasons()).isNotEmpty();
                assertThat(d.evidence().links()).extracting(Link::association).contains(W + "ActivityHasBoundaryEvent");
            });
            assertThat(p.coverage()).isEqualTo("incomplete");
        }
        Declaration placeholder = only(analyze(chain().node("BoundaryEvent", "boundary", null).attach("map", "boundary")));
        assertThat(placeholder.definition()).isNull();
        assertThat(placeholder.reasons()).contains("missing-trigger");
        Declaration orphan = only(analyze(chain().error("error", "false", "false").node("BoundaryEvent", "boundary", "pool")
                .trigger("boundary", "error")));
        assertThat(orphan.role()).isEqualTo("unsupported-catch-trigger");
        assertThat(orphan.reasons()).contains("orphan-boundary-trigger");
    }

    @Test
    void multipleAttachmentsAndTriggersNeverChooseFirst() {
        BpmFaultReport report = analyze(chain().node("AutomatedActivity", "second", "pool")
                .error("first-error", "true", "false").error("second-error", "false", "true")
                .node("BoundaryEvent", "boundary", null).attach("map", "boundary").attach("second", "boundary")
                .trigger("boundary", "first-error").trigger("boundary", "second-error"));
        List<Declaration> declarations = process(report).declarations();
        assertThat(declarations).hasSize(4).extracting(d -> d.owner().id()).containsExactly("map", "map", "second", "second");
        assertThat(declarations).extracting(d -> d.definition().id()).containsExactly("first-error", "second-error", "first-error", "second-error");
        assertThat(declarations).allSatisfy(d -> { assertThat(d.state()).isEqualTo("unknown"); assertThat(d.reasons()).isNotEmpty(); });
        ProcessFaults duplicate = process(analyze(chain().error("error", "true", "false")
                .activity("map", "error").activity("map", "error")));
        assertThat(duplicate.declarations()).hasSize(2).allSatisfy(d -> assertThat(d.reasons()).contains("duplicate-declaration"));
        ProcessFaults ambiguousBoundary = process(analyze(chain().error("error", "true", "false")
                .node("BoundaryEvent", "boundary", null).node("BoundaryEvent", "boundary", null)
                .attach("map", "boundary").trigger("boundary", "error")));
        assertThat(ambiguousBoundary.declarations()).isNotEmpty().allSatisfy(d -> {
            assertThat(d.state()).isEqualTo("unknown");
            assertThat(d.event().candidates()).hasSize(2);
            assertThat(d.evidence().links()).extracting(Link::association).contains(W + "CatchEventHasTrigger");
        });
    }

    @Test
    void catchConditionRetainsFalseAndOpaqueSyntaxWithoutPruning() {
        for (String editor : List.of("false", "opaque|predicate()")) {
            Declaration d = only(analyze(chain().error("error", "true", "false").boundary("map", "boundary", "error")
                    .condition("boundary", "condition", editor).literal("condition", "literal", "false")));
            assertThat(d.state()).isEqualTo("declared");
            assertThat(d.conditions()).hasSize(1);
            BpmPathReport.Condition condition = d.conditions().getFirst();
            assertThat(condition.state()).isEqualTo("not-evaluated");
            assertThat(condition.text()).isEqualTo(editor);
            assertThat(raw(d.evidence(), "literal").attributes()).containsEntry("value", "false");
            assertRecoverable(d.evidence(), condition.evidence());
        }
        Declaration absent = only(analyze(chain().error("error", "false", "false").trigger("start", "error")));
        assertThat(absent.conditions()).isEmpty();
        Declaration text = only(analyze(chain().error("error", "false", "false").trigger("start", "error")
                .raw(R + "Expression", "text", "", "<stringExpression originalText='  raw text  '/>")
                .relation(W + "CatchEventContainsExpression", "start", "text")));
        assertThat(text.conditions().getFirst().text()).isEqualTo("  raw text  ");
        Declaration missing = only(analyze(chain().error("error", "false", "false").trigger("start", "error")
                .relation(W + "CatchEventContainsExpression", "start", "missing")));
        assertThat(missing.conditions().getFirst().state()).isEqualTo("unknown");
    }

    @Test
    void conditionClosureTerminatesCyclesAndRetainsIndependentBranches() {
        BpmFaultFixture fixture = chain().error("error", "true", "false").trigger("start", "error")
                .condition("start", "expr", null).raw(R + "FunctionInvocation", "call", "", "")
                .relation(R + "ExpressionHasSteps", "call", "expr")
                .raw(R + "FunctionInterface", "function", "", "")
                .relation(R + "FunctionInvocationHasFunctionInterface", "function", "call")
                .raw(R + "Expression", "implementation-body", "stringExpression='must not traverse'", "")
                .relation(R + "FunctionInterfaceHasFunctions", "implementation-body", "function")
                .raw(M + "Mapping", "arguments", "", "")
                .relation(R + "FunctionInvocationHasParameterMapping", "arguments", "call")
                .raw(M + "MappingPart", "part0", "", "")
                .relation(M + "MappingContainsMappingParts", "part0", "arguments")
                .raw(R + "Expression", "independent", "", "")
                .relation(M + "MappingPartContainsExpression", "independent", "part0")
                .reference("independent", "ref", "payload", "urn:payload");
        for (int i = 1; i <= 70; i++) fixture.raw(M + "MappingPart", "part" + i, "", "")
                .relation(M + "MappingPartHasChildren", "part" + i, "part" + (i - 1));
        fixture.relation(M + "MappingPartContainsExpression", "expr", "part70");
        BpmFaultReport report = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(fixture));
        Declaration d = only(report);
        assertThat(d.evidence().elements()).extracting(Element::id).contains("part70", "independent", "ref", "ref-q", "function")
                .doesNotContain("implementation-body");
        assertThat(d.evidence().elements()).extracting(Element::location).doesNotHaveDuplicates();
        assertThat(d.evidence().links()).extracting(Link::location).doesNotHaveDuplicates();
        assertThat(d.reasons()).isNotEmpty();
        assertRecoverable(d.evidence(), d.conditions().getFirst().evidence());
    }

    @Test
    void orphanDefinitionsAndLinksRemainQualifiedWithoutCrossBinding() {
        BpmFaultReport local = analyze(chain().error("local", "true", "false"));
        Declaration orphan = only(local);
        assertThat(orphan.role()).isEqualTo("orphan-definition");
        assertThat(orphan.owner()).isNull();
        assertThat(orphan.event()).isNull();
        assertThat(orphan.definition()).isNull();
        assertThat(orphan.definitions().getFirst().source().id()).isEqualTo("local");
        assertThat(orphan.state()).isEqualTo("unknown");
        BpmFaultReport external = analyze(chain().error("attached", "true", "false").activity("map", "attached")
                .external(METADATA, element(W + "ErrorEventDefinition", "external", "isCritical='true'", "")));
        assertThat(process(external).coverage()).isEqualTo("complete");
        assertThat(process(external).declarations()).hasSize(1);
        assertThat(external.unattributed()).hasSize(1);
        Unattributed unused = external.unattributed().getFirst();
        assertThat(unused.input()).isEqualTo(1);
        assertThat(unused.component()).isEqualTo(1);
        assertThat(unused.kind()).isEqualTo("unassigned-definition");
        assertThat(unused.evidence().elements()).extracting(Element::id).containsExactly("external");
        assertThat(unused.evidence().elements().getFirst().location().partition()).isEqualTo(METADATA);
        BpmFaultReport unresolved = analyze(chain().error("attached", "true", "false").activity("map", "attached")
                .external(METADATA, link(W + "ErrorEventDefinitionHasWSDLFault", "missing-fault", "missing-definition")));
        assertThat(unresolved.unattributed()).hasSize(1);
        assertThat(unresolved.unattributed().getFirst().kind()).isEqualTo("orphan-fault-reference");
        assertThat(unresolved.unattributed().getFirst().evidence().links().getFirst().location().partition()).isEqualTo(METADATA);
        assertThat(process(unresolved).coverage()).isEqualTo("incomplete");
        assertThat(unresolved.issues()).anySatisfy(issue -> assertThat(issue.code()).isEqualTo("BPM_FAULT_UNATTRIBUTED_SOURCE"));
    }

    @Test
    void duplicateInputAndProcessIdsStayOccurrenceQualified() {
        ScaInventory inventory = read(chain().error("error", "true", "false").activity("map", "error"));
        BpmComponent component = inventory.bpm().getFirst();
        // Legacy nullable list positions must remain part of original occurrence keys.
        BpmComponent positioned = new BpmComponent(component.dc(), Arrays.asList(null, component.processes().getFirst()),
                component.catalog(), component.sourceEvidence());
        ScaInventory withGaps = BpmDataFixture.withComponents(inventory, Arrays.asList(null, positioned, component));
        BpmFaultReport report = BpmFaultAnalysis.of(List.of(withGaps, inventory), List.of("same", "same"));
        assertThat(report.inputs()).extracting(BpmPathReport.Input::ordinal).containsExactly(1, 2);
        assertThat(report.processes()).extracting(ProcessFaults::key).containsExactly(
                new ProcessKey(1, 2, 2), new ProcessKey(1, 3, 1), new ProcessKey(2, 1, 1));
        assertThat(report.processes()).allSatisfy(p -> {
            assertThat(p.declarations()).hasSize(1);
            assertThat(p.declarations().getFirst().ordinal()).isEqualTo(1);
            assertThat(p.declarations().getFirst().definition().id()).isEqualTo("error");
        });
        ScaInventory missing = read(chain().activity("map", "error"));
        BpmFaultReport separate = BpmFaultAnalysis.of(List.of(missing, inventory), List.of("same", "same"));
        assertThat(separate.processes().getFirst().declarations().getFirst().definitions()).isEmpty();
        assertThat(separate.processes().getFirst().declarations().getFirst().state()).isEqualTo("unknown");
        assertThat(separate.processes().get(1).declarations().getFirst().state()).isEqualTo("declared");
    }

    @Test
    void incompleteGraphAndLegacyEvidenceNeverClaimCompleteFaultCoverage() {
        BpmFaultReport broken = analyze(chain().error("error", "true", "false").activity("map", "error")
                .sequence("broken", "map", "missing", "pool"));
        assertThat(process(broken).coverage()).isEqualTo("incomplete");
        assertThat(process(broken).declarations()).hasSize(1);
        ScaInventory original = read(chain().error("error", "true", "false").activity("map", "error"));
        BpmSourceEvidence source = evidence(original);
        for (BpmSourceEvidence unavailable : Arrays.asList(null,
                new BpmSourceEvidence(2, source.selectionVersion(), source.indexedPartitions(), source.processPartitions(), source.elements(), source.links()),
                new BpmSourceEvidence(1, "future-selection", source.indexedPartitions(), source.processPartitions(), source.elements(), source.links()))) {
            BpmFaultReport report = analyze(BpmDataFixture.withEvidence(original, unavailable));
            assertThat(process(report).coverage()).isEqualTo("unavailable");
            assertThat(process(report).declarations()).isEmpty();
            assertThat(process(report).reasons()).isNotEmpty();
            assertThat(report.issues()).containsAll(original.issues());
        }
        BpmComponent c = original.bpm().getFirst();
        Process p = c.processes().getFirst();
        Process wrongSource = new Process(p.id(), p.name(), p.documentation(), p.pool(), "different!/" + PROCESS,
                p.lanes(), p.roles(), p.scopes(), p.dataObjects(), p.nodes(), p.flows(), p.annotations(), p.editorArtifacts());
        BpmFaultReport mismatch = analyze(BpmDataFixture.withComponents(original, List.of(
                new BpmComponent(c.dc(), List.of(wrongSource), c.catalog(), c.sourceEvidence()))));
        assertThat(process(mismatch).coverage()).isEqualTo("unavailable");
        assertThat(process(mismatch).declarations()).isEmpty();
    }

    @Test
    void malformedMessageAndBuiltinChainsKeepUnknownPrecedence() {
        for (boolean builtin : List.of(false, true)) {
            BpmFaultFixture f = chain();
            if (!builtin) f.message("message");
            Declaration d = only(analyze(f.boundary("end", "boundary", builtin ? TECHNICAL : "message")));
            assertThat(d.state()).isEqualTo("unknown");
            assertThat(d.reasons()).isNotEmpty();
            if (builtin) assertThat(d.builtin().id()).isEqualTo(TECHNICAL);
            else assertThat(d.definitions().getFirst().kind()).isEqualTo("message");
        }
        ProcessFaults start = process(analyze(chain().message("one").message("two")
                .trigger("start", "one").trigger("start", "two")));
        assertThat(start.declarations()).hasSize(2).allSatisfy(d -> assertThat(d.state()).isEqualTo("unknown"));
        ProcessFaults end = process(analyze(chain().message("one").message("two")
                .result("end", "one").result("end", "two")));
        assertThat(end.declarations()).hasSize(2).allSatisfy(d -> assertThat(d.state()).isEqualTo("unknown"));
    }

    @Test
    void conditionWithoutTriggerAndDanglingFaultParameterLinksStayVisible() {
        BpmFaultReport report = analyze(chain().condition("start", "condition", "false").literal("condition", "literal", "false")
                .fault("missing-definition", "missing-fault")
                .relation(W + "EventDefinitionHasParameter", "missing-parameter", "missing-definition"));
        List<Declaration> declarations = process(report).declarations();
        assertThat(declarations).extracting(Declaration::role)
                .containsExactly("catch-condition-only", "orphan-fault-reference", "orphan-parameter-reference");
        assertThat(declarations).allSatisfy(d -> assertThat(d.state()).isEqualTo("unknown"));
        Declaration condition = declarations.getFirst();
        assertThat(condition.owner().id()).isEqualTo("start");
        assertThat(condition.event()).isNull();
        assertThat(condition.definition()).isNull();
        assertThat(condition.reasons()).contains("condition-without-declaration");
        assertThat(condition.conditions().getFirst().state()).isEqualTo("not-evaluated");
        assertThat(condition.conditions().getFirst().text()).isEqualTo("false");
        assertRecoverable(condition.evidence(), condition.conditions().getFirst().evidence());
        for (Declaration d : declarations.subList(1, 3)) {
            assertThat(d.owner()).isNull();
            assertThat(d.event()).isNull();
            assertThat(d.definition().id()).isEqualTo("missing-definition");
            assertThat(d.evidence().links()).isNotEmpty();
        }
        assertThat(process(report).coverage()).isEqualTo("incomplete");
    }

    @Test
    void unknownWorkflowRelationOnDefinitionPreventsCompleteCoverage() {
        BpmFaultReport report = analyze(chain().error("error", "true", "false").activity("map", "error")
                .node("AutomatedActivity", "other", "pool").activity("other", "error")
                .raw("XSD1.key.QName", "metadata", "name='opaque' namespace='urn:opaque'", "")
                .relation(W + "FutureDefinitionMetadata", "metadata", "error"));
        assertThat(process(report).declarations()).hasSize(2).allSatisfy(d -> {
            assertThat(d.state()).isEqualTo("unknown");
            assertThat(d.reasons()).contains("unsupported-workflow-relation");
            assertThat(d.evidence().links()).extracting(Link::association).contains(W + "FutureDefinitionMetadata");
            assertThat(d.definitions().getFirst().evidence().links()).extracting(Link::association).contains(W + "FutureDefinitionMetadata");
            assertThat(raw(d.evidence(), "metadata").attributes()).containsEntry("namespace", "urn:opaque");
        });
        assertThat(process(report).coverage()).isEqualTo("incomplete");
    }

    @Test
    void ambiguousOwnersKeepSeparateScopePathsAndMembershipEvidence() {
        ScaInventory inventory = read(nested().node("MappingActivity", "duplicate-owner", "host")
                .node("MappingActivity", "duplicate-owner", "pool").error("error", "true", "false")
                .activity("duplicate-owner", "error"));
        Declaration d = only(analyze(inventory));
        assertThat(d.owner().resolution()).isEqualTo("ambiguous");
        assertThat(d.state()).isEqualTo("unknown");
        assertThat(d.scopes()).hasSize(2).extracting(Scope::owner).containsExactlyElementsOf(locations(inventory, "duplicate-owner"));
        assertThat(d.scopes()).allSatisfy(scope -> {
            assertThat(scope.state()).isEqualTo("unknown");
            assertThat(scope.path()).isEmpty();
            assertThat(scope.reasons()).isNotEmpty();
            assertThat(scope.basis()).containsAll(linkFacts(inventory, W + "ScopeContainsScopeObjects", "duplicate-owner"));
            assertRecoverable(d.evidence(), scope.basis());
        });
        ScaInventory passive = read(chain().pool("passive", "false").node("MappingActivity", "passive-owner", "passive")
                .error("error", "false", "false").activity("passive-owner", "error"));
        Scope passiveScope = only(analyze(passive)).scopes().getFirst();
        assertThat(passiveScope.state()).isEqualTo("inactive");
        assertThat(passiveScope.path()).containsExactly(location(passive, "passive"));
        assertThat(passiveScope.reasons()).isNotEmpty();
    }

    @Test
    void duplicateOrphanDefinitionsNeverClaimGloballyUniqueEndpoints() {
        ScaInventory inventory = read(chain().error("duplicate", "true", "false").error("duplicate", "false", "true"));
        List<Declaration> rows = process(analyze(inventory)).declarations();
        assertThat(rows).hasSize(2).extracting(d -> d.definitions().getFirst().source().location())
                .containsExactlyElementsOf(locations(inventory, "duplicate"));
        assertThat(rows).allSatisfy(d -> {
            assertThat(d.role()).isEqualTo("orphan-definition");
            assertThat(d.owner()).isNull();
            assertThat(d.event()).isNull();
            assertThat(d.definition()).isNull();
            assertThat(d.scopes()).isEmpty();
            assertThat(d.definitions()).hasSize(1);
            assertThat(d.evidence().path()).contains(new Fact("element", d.definitions().getFirst().source().location()));
            assertThat(d.state()).isEqualTo("unknown");
        });
    }

    private ScaInventory read(BpmFaultFixture fixture) { return fixture.read(dir.resolve("faults-" + (++files) + ".sca")); }
    private BpmFaultReport analyze(BpmFaultFixture fixture) { return analyze(read(fixture)); }
    static BpmFaultReport analyze(ScaInventory inventory) { return BpmFaultAnalysis.of(List.of(inventory), List.of("synthetic")); }
    static ProcessFaults process(BpmFaultReport report) { return report.processes().getFirst(); }
    static Declaration only(BpmFaultReport report) { assertThat(process(report).declarations()).hasSize(1); return process(report).declarations().getFirst(); }
    static Element raw(Evidence evidence, String id) { return evidence.elements().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow(); }
    static List<Fact> linkFacts(ScaInventory inventory, String association, String first) {
        List<Fact> facts = evidence(inventory).links().stream().filter(l -> association.equals(l.association()) && first.equals(l.first().id()))
                .map(l -> new Fact("link", l.location())).toList();
        assertThat(facts).as("fixture link " + association + " / " + first).isNotEmpty();
        return facts;
    }
    static void assertRecoverable(Evidence evidence, List<Fact> facts) {
        for (Fact fact : facts) {
            if (fact.kind().equals("element")) assertThat(evidence.elements()).extracting(Element::location).contains(fact.location());
            else assertThat(evidence.links()).extracting(Link::location).contains(fact.location());
        }
    }
}
