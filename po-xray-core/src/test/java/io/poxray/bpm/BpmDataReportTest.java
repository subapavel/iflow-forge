package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.bpm.BpmDataAnalysisTest.*;
import static io.poxray.bpm.BpmDataFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class BpmDataReportTest {
    @TempDir Path dir;

    @Test
    void inputContractAndExplicitEmptyCollections() {
        ScaInventory inventory = chain().read(dir.resolve("contract.sca"));
        assertThatThrownBy(() -> BpmDataAnalysis.of(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmDataAnalysis.of(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmDataAnalysis.of(List.of(inventory), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmDataAnalysis.of(Arrays.asList((ScaInventory) null), List.of("label")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmDataAnalysis.of(List.of(inventory), Arrays.asList((String) null)))
                .isInstanceOf(IllegalArgumentException.class);
        BpmDataReport empty = BpmDataAnalysis.of(List.of(), List.of());
        assertThat(empty.formatVersion()).isEqualTo(1);
        assertThat(empty.analysisVersion()).isEqualTo("bpm-data-v1");
        assertThat(empty.processes()).isEmpty();
        assertThat(empty.issues()).isEmpty();
        assertThat(empty.paths()).isEqualTo(BpmPathAnalysis.of(List.of(), List.of()));
        ScaInventory legacy = new ScaInventory("0.1", null, null, null, null, null, null, null);
        BpmDataReport legacyReport = BpmDataAnalysis.of(List.of(legacy), List.of("legacy"));
        assertThat(legacyReport.processes()).isEmpty();
        assertThat(legacyReport.paths().inputs()).containsExactly(new BpmPathReport.Input(1, "legacy", null));
    }

    @Test
    void deeplyImmutableSnapshots() {
        Locator at = new Locator(ARCHIVE, PROCESS, 1);
        Element element = new Element(at, "object", W + "DataObject", Map.of("name", "payload"), Map.of(), Map.of(), List.of());
        Endpoint endpoint = new Endpoint("object", "missing", List.of());
        Link link = new Link(at, W + "ScopeContainsScopeObjects", endpoint, endpoint);
        List<Locator> scope = mutable(at);
        List<Element> elements = mutable(element);
        List<Link> links = mutable(link);
        List<Fact> facts = mutable(new Fact("element", at), new Fact("link", at));
        List<Integer> ids = mutable(1);
        List<String> reasons = mutable("uncertain");
        Evidence evidence = new Evidence(facts, elements, links);
        Name name = new Name(links, elements, "resolved");
        List<Name> names = mutable(name);
        Binding binding = new Binding("unknown", ids, reasons);
        OriginSet origin = new OriginSet(1, ids, "incomplete", reasons);
        List<OriginSet> origins = mutable(origin);
        ObjectData object = new ObjectData(1, element, links, scope, links, elements, "unknown", reasons);
        MappingUse mapping = new MappingUse(1, null, null, link, endpoint, at, "mapping", "process-context", "process-context", evidence, reasons);
        Declaration declaration = new Declaration(1, 1, endpoint, names, evidence, binding, "expression", "candidate", reasons);
        Read read = new Read(1, 1, "process-context", evidence, binding, origins, reasons);
        StepState step = new StepState(1, at, "unknown", origins, origins, reasons);
        List<ObjectData> objects = mutable(object);
        List<MappingUse> mappings = mutable(mapping);
        List<Declaration> declarations = mutable(declaration);
        List<Read> reads = mutable(read);
        List<StepState> steps = mutable(step);
        ProcessData process = new ProcessData(new ProcessKey(1, 1, 1), "synthetic", "process", null, null,
                "incomplete", reasons, objects, mappings, declarations, reads, steps);
        List<ProcessData> processes = mutable(process);
        List<Issue> issues = mutable(new Issue(Issue.WARNING, "SYNTHETIC", "retained", null, null));
        BpmDataReport report = new BpmDataReport(1, "bpm-data-v1", BpmPathAnalysis.of(List.of(), List.of()), processes, issues);
        String snapshot = Json.write(report);
        for (List<?> input : List.of(scope, elements, links, facts, ids, reasons, names, origins,
                objects, mappings, declarations, reads, steps, processes, issues)) input.clear();
        assertThat(Json.write(report)).isEqualTo(snapshot);
        for (List<?> frozen : List.of(report.processes(), report.issues(), process.reasons(), process.objects(), process.mappings(),
                process.declarations(), process.reads(), process.steps(), object.memberships(), object.scopePath(), object.types(),
                object.typeCandidates(), object.reasons(), mapping.reasons(), evidence.path(), evidence.elements(), evidence.links(),
                name.links(), name.candidates(), binding.objects(), binding.reasons(), declaration.targetPath(), declaration.reasons(),
                read.candidates(), read.reasons(), origin.declarations(), origin.reasons(), step.before(), step.after(), step.reasons())) {
            assertThatThrownBy(() -> frozen.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(evidence.path()).containsExactly(new Fact("element", at), new Fact("link", at));
    }

    @Test
    void jsonRetainsNullEmptyAndSourcePayloads() {
        Evidence evidence = new Evidence(List.of(), List.of(), List.of());
        Binding binding = new Binding("unknown", List.of(), List.of());
        Name name = new Name(List.of(), List.of(), "missing");
        ObjectData object = new ObjectData(1, null, List.of(), List.of(), List.of(), List.of(), "unknown", List.of());
        MappingUse mapping = new MappingUse(1, null, null, null, null, null, "unknown", "unknown", "unknown", evidence, List.of());
        Declaration declaration = new Declaration(1, 1, null, List.of(name), evidence, binding, "unknown", "unknown", List.of());
        OriginSet origin = new OriginSet(1, List.of(), "incomplete", List.of());
        Read read = new Read(1, 1, "unknown", evidence, binding, List.of(origin), List.of());
        StepState step = new StepState(1, null, "unknown", List.of(origin), List.of(), List.of());
        ProcessData process = new ProcessData(new ProcessKey(1, 1, 1), null, null, null, null, "unavailable", List.of(),
                List.of(object), List.of(mapping), List.of(declaration), List.of(read), List.of(step));
        BpmDataReport report = new BpmDataReport(1, "bpm-data-v1", BpmPathAnalysis.of(List.of(), List.of()), List.of(process), List.of());
        JsonNode json = Json.mapper().readTree(Json.write(report));
        assertThat(json.propertyNames()).containsExactly("formatVersion", "analysisVersion", "paths", "processes", "issues");
        assertThat(json.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(json.path("analysisVersion").asString()).isEqualTo("bpm-data-v1");
        assertEmpty(json, "issues");
        JsonNode p = json.at("/processes/0");
        assertThat(p.propertyNames()).containsExactly("key", "dc", "id", "name", "source", "coverage", "reasons", "objects", "mappings", "declarations", "reads", "steps");
        for (String field : List.of("dc", "id", "name", "source")) assertNull(p, field);
        assertEmpty(p, "reasons");
        JsonNode o = p.at("/objects/0");
        assertThat(o.propertyNames()).containsExactly("ordinal", "source", "memberships", "scopePath", "types", "typeCandidates", "scopeState", "reasons");
        assertNull(o, "source");
        for (String field : List.of("memberships", "scopePath", "types", "typeCandidates", "reasons")) assertEmpty(o, field);
        JsonNode m = p.at("/mappings/0");
        assertThat(m.propertyNames()).containsExactly("ordinal", "parent", "parentDeclaration", "ownership", "mapping", "rootOwner", "role", "readDomain", "writeDomain", "evidence", "reasons");
        for (String field : List.of("parent", "parentDeclaration", "ownership", "mapping", "rootOwner")) assertNull(m, field);
        assertEmpty(m, "reasons");
        JsonNode e = m.path("evidence");
        assertThat(e.propertyNames()).containsExactly("path", "elements", "links");
        for (String field : List.of("path", "elements", "links")) assertEmpty(e, field);
        JsonNode d = p.at("/declarations/0");
        assertThat(d.propertyNames()).containsExactly("ordinal", "mapping", "part", "targetPath", "evidence", "target", "valueKind", "candidacy", "reasons");
        assertNull(d, "part");
        assertEmpty(d, "reasons");
        JsonNode n = d.at("/targetPath/0");
        assertThat(n.propertyNames()).containsExactly("links", "candidates", "state");
        assertEmpty(n, "links");
        assertEmpty(n, "candidates");
        JsonNode b = d.path("target");
        assertThat(b.propertyNames()).containsExactly("state", "objects", "reasons");
        assertEmpty(b, "objects");
        assertEmpty(b, "reasons");
        JsonNode rd = p.at("/reads/0");
        assertThat(rd.propertyNames()).containsExactly("ordinal", "declaration", "domain", "evidence", "binding", "candidates", "reasons");
        assertEmpty(rd, "reasons");
        JsonNode set = rd.at("/candidates/0");
        assertThat(set.propertyNames()).containsExactly("object", "declarations", "coverage", "reasons");
        assertEmpty(set, "declarations");
        assertEmpty(set, "reasons");
        JsonNode s = p.at("/steps/0");
        assertThat(s.propertyNames()).containsExactly("step", "location", "connectivity", "before", "after", "reasons");
        assertNull(s, "location");
        assertEmpty(s, "after");
        assertEmpty(s, "reasons");
        ScaInventory inventory = chain().object("object", "payload", "pool")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true")
                .reference("part", "payload").referenceStep("part-expr", "item", "item", "urn:item").read(dir.resolve("json.sca"));
        Read actual = readFor(process(BpmDataAnalysis.of(List.of(inventory), List.of("synthetic"))), "part");
        String readJson = Json.write(actual);
        assertThat(readJson).contains("payload", "urn:read", "item", "urn:item", "ReferenceHasQName");
        assertThat(Json.mapper().readTree(readJson).at("/evidence/elements").size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void repeatIsDeterministicAndInputUnchanged() {
        BpmDataFixture f = chain().object("object", "payload", "pool").initial("object", "initial", "seed", "payload")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true").reference("part", "payload")
                .sequence("cycle", "end", "map", "pool");
        Path file = SyntheticBpmSca.write(dir.resolve("repeat.sca"), Map.of(PROCESS, f.xml()));
        ScaInventory inventory = PoXray.read(file);
        String original = Json.write(inventory);
        BpmDataReport first = BpmDataAnalysis.of(List.of(inventory), List.of("same"));
        BpmDataReport second = BpmDataAnalysis.of(List.of(inventory), List.of("same"));
        BpmDataReport reread = BpmDataAnalysis.of(List.of(PoXray.read(file)), List.of("same"));
        assertThat(Json.write(first)).isEqualTo(Json.write(second)).isEqualTo(Json.write(reread));
        assertThat(BpmDataAnalysis.markdown(first)).isEqualTo(BpmDataAnalysis.markdown(second)).isEqualTo(BpmDataAnalysis.markdown(reread));
        assertThat(Json.write(inventory)).isEqualTo(original);
        assertThat(first.paths()).isEqualTo(BpmPathAnalysis.of(List.of(inventory), List.of("same")));
        assertThat(Json.write(first)).doesNotContain("readAt", "generatedAt", "timestamp");
        assertThat(first.issues()).containsAll(inventory.issues());
    }

    @Test
    void markdownEscapesEvidenceAndLabelsCandidateLimits() {
        ScaInventory inventory = chain().object("object", "payload|line\nnext", "pool")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload|line\nnext", "true")
                .literal("part", "true").read(dir.resolve("markdown.sca"));
        BpmDataReport report = BpmDataAnalysis.of(List.of(inventory), List.of("input|label\nnext"));
        String markdown = BpmDataAnalysis.markdown(report);
        assertThat(markdown).contains("input\\|label", "payload\\|line");
        assertThat(markdown).doesNotContain("input|label\nnext", "payload|line\nnext");
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("candidate", "structur", "runtime", "coverage", "empty");
    }

    @SafeVarargs private static <T> List<T> mutable(T... elements) { return new ArrayList<>(Arrays.asList(elements)); }
    private static void assertEmpty(JsonNode node, String field) {
        assertThat(node.path(field).isArray()).as(field).isTrue();
        assertThat(node.path(field).isEmpty()).as(field).isTrue();
    }
    private static void assertNull(JsonNode node, String field) { assertThat(node.path(field).isNull()).as(field).isTrue(); }
}
