package io.poxray.bpm;

import static io.poxray.bpm.BpmDataAnalysisTest.declaration;
import static io.poxray.bpm.BpmDataAnalysisTest.mapping;
import static io.poxray.bpm.BpmDataAnalysisTest.origin;
import static io.poxray.bpm.BpmDataAnalysisTest.process;
import static io.poxray.bpm.BpmDataAnalysisTest.raw;
import static io.poxray.bpm.BpmDataAnalysisTest.readFor;
import static io.poxray.bpm.BpmDataAnalysisTest.state;
import static io.poxray.bpm.BpmDataFixture.M;
import static io.poxray.bpm.BpmDataFixture.R;
import static io.poxray.bpm.BpmDataFixture.chain;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmDataReport.Declaration;
import io.poxray.model.BpmDataReport.OriginSet;
import io.poxray.model.BpmDataReport.ProcessData;
import io.poxray.model.BpmDataReport.StepState;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent review regressions: retained uncertainty must respect its source and scope. */
class BpmDataCompletenessTest {
    @TempDir Path dir;
    private int files;

    @Test
    void nestedTextOnlyConditionBranchKeepsCandidateButAddsUncertainty() {
        BpmDataReport report = analyze(chain().object("object", "payload", "pool")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true")
                .expression("part", "outer")
                .raw(R + "Condition", "condition", "", "")
                .relation(R + "ExpressionHasSteps", "condition", "outer")
                .raw(R + "Expression", "predicate", "", "")
                .literalStep("predicate", "predicate-literal", "false")
                .relation(R + "ConditionHasConditionExpression", "predicate", "condition")
                .raw(R + "Expression", "positive", "", "")
                .referenceStep("positive", "positive-reference", "payload", "urn:positive")
                .relation(R + "ConditionHasPositiveExpression", "positive", "condition")
                .raw(R + "Expression", "negative", "stringExpression='payload'", "")
                .relation(R + "ConditionHasNegativeExpression", "negative", "condition"));

        ProcessData p = process(report);
        Declaration d = declaration(p, "part");
        assertThat(d.valueKind()).isEqualTo("expression");
        assertThat(d.candidacy()).isEqualTo("candidate");
        assertThat(raw(d.evidence(), "negative").attributes()).containsEntry("stringExpression", "payload");
        assertThat(p.reads()).hasSize(1);
        assertThat(readFor(p, "part").binding().objects()).containsExactly(1);
        assertThat(raw(readFor(p, "part").evidence(), "positive-reference-q").attributes())
                .containsEntry("name", "payload").containsEntry("namespace", "urn:positive");
        assertThat(d.reasons()).as("uninterpreted nested editor text remains a declaration gap").isNotEmpty();
        OriginSet downstream = origin(state(report, "end").before(), 1);
        assertThat(downstream.declarations()).containsExactly(d.ordinal());
        assertThat(downstream.coverage()).isEqualTo("incomplete");
        assertThat(downstream.reasons()).isNotEmpty();
    }

    @Test
    void unknownObjectVisibilityRemainsVisibleOnEmptyStepStates() {
        BpmDataReport report = analyze(chain().object("unscoped", "payload", null));
        ProcessData p = process(report);
        assertThat(report.paths().processes().getFirst().coverage()).isEqualTo("complete");
        assertThat(p.objects()).hasSize(1);
        assertThat(p.objects().getFirst().scopeState()).isEqualTo("unknown");
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(p.steps()).hasSize(3).allSatisfy(step -> {
            assertThat(step.connectivity()).isEqualTo("connected");
            assertThat(step.before()).isEmpty();
            assertThat(step.after()).isEmpty();
            assertThat(step.reasons()).contains("object-visibility-unknown");
        });
    }

    @Test
    void disconnectedChildInitializerDoesNotContaminateOuterObjects() {
        BpmDataReport report = analyze(chain().object("outer", "outerPayload", "pool")
                .node("EmbeddedScope", "island", "pool")
                .node("StartEvent", "child-start", "island").node("EndEvent", "child-end", "island")
                .sequence("child-edge", "child-start", "child-end", "island")
                .object("local", "localPayload", "island")
                .initial("local", "child-initial", "child-seed", "localPayload"));
        ProcessData p = process(report);
        assertThat(report.paths().processes().getFirst().coverage()).isEqualTo("complete");
        Declaration initializer = declaration(p, "child-seed");
        assertThat(initializer.target().state()).isEqualTo("owned");
        assertThat(initializer.target().objects()).containsExactly(2);
        assertThat(initializer.candidacy()).isEqualTo("candidate");
        for (String id : List.of("child-start", "child-end")) {
            StepState child = state(report, id);
            assertThat(child.connectivity()).isEqualTo("disconnected");
            assertThat(child.before()).extracting(OriginSet::object).containsExactly(1, 2);
            assertThat(child.after()).extracting(OriginSet::object).containsExactly(1, 2);
            for (List<OriginSet> side : List.of(child.before(), child.after())) {
                assertThat(side).allSatisfy(set -> {
                    assertThat(set.coverage()).isEqualTo("not-applicable");
                    assertThat(set.declarations()).isEmpty();
                });
            }
        }
        for (String id : List.of("start", "map", "end")) {
            StepState outer = state(report, id);
            assertThat(outer.connectivity()).isEqualTo("connected");
            assertThat(outer.before()).extracting(OriginSet::object).containsExactly(1);
            assertThat(outer.after()).extracting(OriginSet::object).containsExactly(1);
            for (OriginSet set : List.of(origin(outer.before(), 1), origin(outer.after(), 1))) {
                assertThat(set.declarations()).isEmpty();
                assertThat(set.coverage()).as(id + " outer provenance").isEqualTo("complete");
                assertThat(set.reasons()).doesNotContain("initial-entry-unknown");
            }
        }
    }

    @Test
    void malformedInitializerPartKeepsKnownObjectTarget() {
        assertMalformedInitializerTarget("missing");
    }

    @Test
    void ambiguousInitializerPartKeepsKnownObjectTarget() {
        assertMalformedInitializerTarget("ambiguous");
    }

    @Test
    void wrongKindInitializerPartKeepsKnownObjectTarget() {
        assertMalformedInitializerTarget("wrong-kind");
    }

    @Test
    void ambiguousOrphanPartExpressionOwnerRemainsUnknown() {
        BpmDataReport report = analyze(chain().object("object", "payload", "pool")
                .raw(M + "MappingPart", "p", "enabled='true'", "")
                .raw(M + "MappingPart", "p", "enabled='true'", "")
                .expression("p", "e").literalStep("e", "literal", "true"));
        ProcessData p = process(report);
        assertThat(p.declarations()).hasSize(2);
        assertThat(p.declarations()).extracting(d -> d.part().candidates().getFirst().location()).doesNotHaveDuplicates();
        assertThat(p.declarations()).allSatisfy(d -> {
            assertThat(d.part().id()).isEqualTo("p");
            assertThat(d.evidence().links()).filteredOn(l -> l.association().equals(M + "MappingPartContainsExpression"))
                    .hasSize(1).allSatisfy(link -> {
                        assertThat(link.first().id()).isEqualTo("e");
                        assertThat(link.first().resolution()).isEqualTo("unique");
                        assertThat(link.second().id()).isEqualTo("p");
                        assertThat(link.second().resolution()).isEqualTo("ambiguous");
                        assertThat(link.second().candidates()).hasSize(2);
                    });
            assertThat(d.valueKind()).as("ambiguous source owner cannot become a unique expression assignment").isEqualTo("unknown");
            assertThat(d.reasons()).anySatisfy(reason -> assertThat(reason).contains("expression"));
            assertThat(d.candidacy()).isEqualTo("unknown");
        });
        assertThat(p.mappings()).isNotEmpty().allSatisfy(use -> {
            assertThat(use.role()).isEqualTo("unknown");
            assertThat(use.readDomain()).isEqualTo("unknown");
            assertThat(use.writeDomain()).isEqualTo("unknown");
        });
        assertThat(p.steps()).hasSize(3).allSatisfy(step -> {
            for (List<OriginSet> side : List.of(step.before(), step.after())) {
                assertThat(side).hasSize(1);
                assertThat(origin(side, 1).declarations()).isEmpty();
            }
        });
    }

    private void assertMalformedInitializerTarget(String defect) {
        BpmDataFixture fixture = chain().object("a", "payloadA", "pool").object("b", "payloadB", "pool")
                .mapping("initial", "a", "view")
                .relation(M + "MappingContainsMappingParts", "terminal", "initial");
        if (defect.equals("ambiguous")) {
            fixture.raw(M + "MappingPart", "terminal", "enabled='true'", "")
                    .raw(M + "MappingPart", "terminal", "enabled='false'", "");
        } else if (defect.equals("wrong-kind")) {
            fixture.raw(R + "Literal", "terminal", "value='false'", "");
        }
        BpmDataReport report = analyze(fixture);
        ProcessData p = process(report);
        assertThat(mapping(p, "initial").role()).isEqualTo("initial");
        Declaration d = declaration(p, "terminal");
        if (defect.equals("missing")) {
            assertThat(d.part().resolution()).isEqualTo("missing");
            assertThat(d.part().candidates()).isEmpty();
        } else if (defect.equals("ambiguous")) {
            assertThat(d.part().resolution()).isEqualTo("ambiguous");
            assertThat(d.part().candidates()).hasSize(2);
        } else {
            assertThat(d.part().candidates()).hasSize(1);
            assertThat(d.part().candidates().getFirst().metaclass()).isEqualTo(R + "Literal");
        }
        assertThat(d.valueKind()).isEqualTo("unknown");
        assertThat(d.candidacy()).isEqualTo("unknown");
        assertThat(d.target().state()).as(defect + " initializer retains its explicit object owner").isEqualTo("owned");
        assertThat(d.target().objects()).containsExactly(1);
        for (String id : List.of("start", "map", "end")) {
            StepState step = state(report, id);
            assertThat(step.connectivity()).isEqualTo("connected");
            for (List<OriginSet> side : List.of(step.before(), step.after())) {
                OriginSet owned = origin(side, 1);
                assertThat(owned.declarations()).isEmpty();
                assertThat(owned.coverage()).isEqualTo("incomplete");
                assertThat(owned.reasons()).contains("opaque-write");
                OriginSet unrelated = origin(side, 2);
                assertThat(unrelated.declarations()).isEmpty();
                assertThat(unrelated.coverage()).isEqualTo("complete");
                assertThat(unrelated.reasons()).doesNotContain("opaque-write", "initial-entry-unknown");
            }
        }
    }

    private BpmDataReport analyze(BpmDataFixture fixture) {
        return BpmDataAnalysis.of(List.of(fixture.read(dir.resolve("completeness-" + (++files) + ".sca"))),
                List.of("synthetic"));
    }
}
