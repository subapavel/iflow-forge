package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.bpm.BpmPathAnalysisTest.BOOLEAN;
import static io.poxray.bpm.BpmPathAnalysisTest.LOOP_ATTACHMENT;
import static io.poxray.bpm.BpmPathAnalysisTest.W;
import static io.poxray.bpm.BpmPathAnalysisTest.arc;
import static io.poxray.bpm.BpmPathAnalysisTest.attr;
import static io.poxray.bpm.BpmPathAnalysisTest.conditionFixture;
import static io.poxray.bpm.BpmPathAnalysisTest.nested;
import static io.poxray.bpm.BpmPathAnalysisTest.paths;
import static io.poxray.bpm.BpmPathAnalysisTest.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Endpoint;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmPathReport.Input;
import io.poxray.model.BpmPathReport.Loop;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BpmPathReportTest {
    @TempDir Path dir;

    @Test
    void rawDefaultAndTerminationFlagsDistinguishAbsentFalseTrueAndInvalid() {
        String[] values = {null, "false", "true", " false "};
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            BpmPathAnalysisTest.Fixture f = new BpmPathAnalysisTest.Fixture().node("StartEvent", "start", "pool")
                    .raw(W + "EndEvent", "end", attr("terminate", value), "").member("end", "pool")
                    .raw(W + "SequenceConnector", "edge", attr("isDefault", value), "").member("edge", "pool")
                    .relation(W + "SourceOfIncomingConnectors", "start", "edge")
                    .relation(W + "TargetOfOutgoingConnectors", "end", "edge");
            ProcessPaths p = paths(read("flags-" + i, f));
            Flag expected = new Flag(value != null, value, i == 1 ? "false" : i == 2 ? "true" : "unknown");
            assertThat(step(p, "end").terminate()).isEqualTo(expected);
            assertThat(arc(p, "edge").isDefault()).isEqualTo(expected);
            assertThat(step(p, "end").connectivity()).isEqualTo("connected");
            assertThat(arc(p, "edge").condition().state()).isEqualTo("unconditional");
        }
    }

    @Test
    void everyCollectionTakesAnImmutableSnapshotIncludingNestedCandidatesAndFacts() {
        Locator location = new Locator(ARCHIVE, PROCESS, 3);
        List<Locator> locators = new ArrayList<>(List.of(location));
        List<Integer> witness = new ArrayList<>(List.of(1));
        List<String> reasons = new ArrayList<>(List.of("retained-source"));
        List<Fact> facts = new ArrayList<>(List.of(new Fact("element", location), new Fact("link", location)));
        List<Endpoint> endpoints = new ArrayList<>(List.of(new Endpoint("node", "unique", locators)));
        Condition condition = new Condition("unknown", null, facts, reasons);
        Flag absent = new Flag(false, null, "unknown");
        List<Step> steps = new ArrayList<>(List.of(new Step(1, location, "node", "endEvent", null,
                locators, "connected", witness, absent, reasons)));
        List<Arc> arcs = new ArrayList<>(List.of(new Arc(1, "sequence", location, "arc", endpoints, endpoints,
                "resolved", condition, absent, facts, reasons)));
        List<Loop> loops = new ArrayList<>(List.of(new Loop(location, "loop", locators, "unknown", absent,
                null, "unknown", facts, reasons)));
        List<ProcessPaths> processes = new ArrayList<>(List.of(new ProcessPaths(new ProcessKey(1, 1, 1),
                "synthetic", "process", null, null, "incomplete", reasons, steps, arcs, loops)));
        List<Input> inputs = new ArrayList<>(List.of(new Input(1, "synthetic", null)));
        List<Issue> issues = new ArrayList<>(List.of(new Issue(Issue.WARNING, "BPM_PATH_SYNTHETIC", "test", null, null)));
        BpmPathReport report = new BpmPathReport(1, "bpm-paths-v1", inputs, processes, issues);
        String before = Json.write(report);

        for (List<?> mutable : List.of(locators, witness, reasons, facts, endpoints, steps, arcs, loops, processes, inputs, issues)) {
            mutable.clear();
        }
        assertThat(Json.write(report)).isEqualTo(before);
        ProcessPaths p = report.processes().getFirst();
        Step s = p.steps().getFirst();
        Arc a = p.arcs().getFirst();
        Loop l = p.loops().getFirst();
        for (List<?> frozen : List.of(report.inputs(), report.processes(), report.issues(), p.reasons(), p.steps(),
                p.arcs(), p.loops(), s.scopes(), s.witness(), s.reasons(), a.sources(), a.targets(),
                a.sources().getFirst().candidates(), a.targets().getFirst().candidates(), a.evidence(), a.reasons(),
                a.condition().evidence(), a.condition().reasons(), l.hosts(), l.evidence(), l.reasons())) {
            assertThatThrownBy(() -> frozen.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(a.evidence()).containsExactly(new Fact("element", location), new Fact("link", location));
    }

    @Test
    void jsonUsesVersionedExactRecordFieldsAndExplicitNullsAndEmptyCollections() {
        Flag absent = new Flag(false, null, "unknown");
        Endpoint endpoint = new Endpoint(null, "missing", List.of());
        Condition condition = new Condition("missing", null, List.of(), List.of());
        Step step = new Step(1, null, null, "unknown", null, List.of(), "unknown", List.of(), absent, List.of());
        Arc arc = new Arc(1, "boundary-attachment", null, null, List.of(endpoint), List.of(), "missing",
                condition, absent, List.of(), List.of());
        Loop loop = new Loop(null, null, List.of(), "unknown", absent, null, "unknown", List.of(), List.of());
        ProcessPaths process = new ProcessPaths(new ProcessKey(1, 1, 1), null, null, null, null, "unavailable",
                List.of(), List.of(step), List.of(arc), List.of(loop));
        BpmPathReport report = new BpmPathReport(1, "bpm-paths-v1", List.of(new Input(1, "label", null)),
                List.of(process), List.of());
        JsonNode json = Json.mapper().readTree(Json.write(report));

        assertThat(json.propertyNames()).containsExactly("formatVersion", "analysisVersion", "inputs", "processes", "issues");
        assertThat(json.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(json.path("analysisVersion").asString()).isEqualTo("bpm-paths-v1");
        assertThat(json.path("inputs").get(0).propertyNames()).containsExactly("ordinal", "label", "sha256");
        assertThat(json.at("/inputs/0/sha256").isNull()).isTrue();
        assertThat(json.path("issues").isArray()).isTrue();
        assertThat(json.path("issues").isEmpty()).isTrue();
        JsonNode p = json.path("processes").get(0);
        assertThat(p.propertyNames()).containsExactly("key", "dc", "id", "name", "source", "coverage", "reasons", "steps", "arcs", "loops");
        assertThat(p.path("key").propertyNames()).containsExactly("input", "component", "process");
        for (String key : List.of("dc", "id", "name", "source")) assertThat(p.path(key).isNull()).as(key).isTrue();
        JsonNode s = p.path("steps").get(0);
        assertThat(s.propertyNames()).containsExactly("ordinal", "location", "id", "kind", "name", "scopes", "connectivity", "witness", "terminate", "reasons");
        for (String key : List.of("location", "id", "name")) assertThat(s.path(key).isNull()).as(key).isTrue();
        for (String key : List.of("scopes", "witness", "reasons")) assertEmptyArray(s, key);
        assertThat(s.path("terminate").propertyNames()).containsExactly("present", "value", "interpretation");
        assertThat(s.path("terminate").path("value").isNull()).isTrue();
        assertThat(s.path("terminate").path("present").asBoolean()).isFalse();
        JsonNode a = p.path("arcs").get(0);
        assertThat(a.propertyNames()).containsExactly("ordinal", "kind", "location", "id", "sources", "targets", "resolution", "condition", "isDefault", "evidence", "reasons");
        assertThat(a.path("location").isNull()).isTrue();
        assertThat(a.path("id").isNull()).isTrue();
        assertThat(a.path("sources").get(0).propertyNames()).containsExactly("id", "resolution", "candidates");
        assertThat(a.at("/sources/0/id").isNull()).isTrue();
        assertEmptyArray(a.path("sources").get(0), "candidates");
        for (String key : List.of("targets", "evidence", "reasons")) assertEmptyArray(a, key);
        assertThat(a.path("condition").propertyNames()).containsExactly("state", "text", "evidence", "reasons");
        assertThat(a.path("condition").path("text").isNull()).isTrue();
        assertEmptyArray(a.path("condition"), "evidence");
        assertEmptyArray(a.path("condition"), "reasons");
        JsonNode l = p.path("loops").get(0);
        assertThat(l.propertyNames()).containsExactly("location", "id", "hosts", "mode", "sequential", "flowCondition", "completion", "evidence", "reasons");
        for (String key : List.of("location", "id", "flowCondition")) assertThat(l.path(key).isNull()).as(key).isTrue();
        for (String key : List.of("hosts", "evidence", "reasons")) assertEmptyArray(l, key);
        JsonNode empty = Json.mapper().readTree(Json.write(BpmPathAnalysis.of(List.of(), List.of())));
        for (String key : List.of("inputs", "processes", "issues")) assertEmptyArray(empty, key);
    }

    @Test
    void repeatedAnalysisAndRepeatedReadAreDeterministicAndDoNotMutateInput() {
        Path file = SyntheticBpmSca.write(dir.resolve("repeat.sca"), Map.of(PROCESS,
                conditionFixture().literal("expr", "lit", "false", BOOLEAN, null).xml()));
        ScaInventory inventory = PoXray.read(file);
        String original = Json.write(inventory);
        BpmPathReport first = BpmPathAnalysis.of(List.of(inventory), List.of("same"));
        BpmPathReport second = BpmPathAnalysis.of(List.of(inventory), List.of("same"));
        BpmPathReport reread = BpmPathAnalysis.of(List.of(PoXray.read(file)), List.of("same"));
        assertThat(Json.write(first)).isEqualTo(Json.write(second)).isEqualTo(Json.write(reread));
        assertThat(BpmPathAnalysis.markdown(first)).isEqualTo(BpmPathAnalysis.markdown(second))
                .isEqualTo(BpmPathAnalysis.markdown(reread));
        assertThat(Json.write(inventory)).isEqualTo(original);
        assertThat(Json.write(first)).doesNotContain("readAt", "generatedAt", "timestamp");
    }

    @Test
    void markdownEscapesCellsShowsDerivedWitnessesConditionsFlagsLoopsAndLimits() {
        BpmPathAnalysisTest.Fixture f = nested().namedNode("BoundaryEvent", "boundary", null, "Boundary|name")
                .relation(W + "ActivityHasBoundaryEvent", "boundary", "host")
                .raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='invalid' flowCondition='all'", "")
                .relation(LOOP_ATTACHMENT, "loop", "host")
                .node("EndEvent", "false-end", "pool").conditional("false-arc", "start", "false-end", "pool")
                .relation(W + "ConditionalSequenceConnectorContainsExpression", "expr", "false-arc")
                .literal("expr", "lit", "false", BOOLEAN, "false");
        BpmPathReport report = BpmPathAnalysis.of(List.of(read("markdown", f)), List.of("input|label\nsecond line"));
        String markdown = BpmPathAnalysis.markdown(report);
        String lower = markdown.toLowerCase(Locale.ROOT);
        assertThat(markdown).contains("input\\|label", "Boundary\\|name", "boundary-attachment", "embedded-entry",
                "constant-false", "unknown", "invalid", "loop");
        assertThat(markdown).doesNotContain("input|label\nsecond line", "Boundary|name");
        assertThat(lower).contains("coverage", "scope", "witness", "structur", "runtime", "default", "terminate", "sequential");
        assertThat(lower).contains("complete", "connected", "all");
        // The attachment and entry remain visible as distinct arc rows and as witnessed structure.
        assertThat(report.processes().getFirst().arcs()).extracting(Arc::kind)
                .contains("sequence", "boundary-attachment", "embedded-entry");
        assertThat(step(report.processes().getFirst(), "boundary").witness()).hasSize(2);
        assertThat(step(report.processes().getFirst(), "child-start").witness()).hasSize(2);
    }

    private ScaInventory read(String name, BpmPathAnalysisTest.Fixture fixture) {
        return PoXray.read(SyntheticBpmSca.write(dir.resolve(name + ".sca"), Map.of(PROCESS, fixture.xml())));
    }

    private static void assertEmptyArray(JsonNode node, String field) {
        assertThat(node.path(field).isArray()).as(field + " is explicit array").isTrue();
        assertThat(node.path(field).isEmpty()).as(field + " is empty").isTrue();
    }
}
