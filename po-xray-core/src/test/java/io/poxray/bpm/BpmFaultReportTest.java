package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.bpm.BpmFaultFixture.chain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BpmFaultReport.*;
import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmPathReport.Input;
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

class BpmFaultReportTest {
    @TempDir Path dir;

    @Test
    void invalidArgumentsAndEmptyInput() {
        ScaInventory inventory = chain().read(dir.resolve("input.sca"));
        assertThatThrownBy(() -> BpmFaultAnalysis.of(null, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmFaultAnalysis.of(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmFaultAnalysis.of(List.of(inventory), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmFaultAnalysis.of(Arrays.asList((ScaInventory) null), List.of("label")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BpmFaultAnalysis.of(List.of(inventory), Arrays.asList((String) null)))
                .isInstanceOf(IllegalArgumentException.class);
        BpmFaultReport report = BpmFaultAnalysis.of(List.of(), List.of());
        assertThat(report.formatVersion()).isEqualTo(1);
        assertThat(report.analysisVersion()).isEqualTo("bpm-faults-v1");
        assertThat(report.inputs()).isEmpty();
        assertThat(report.processes()).isEmpty();
        assertThat(report.unattributed()).isEmpty();
        assertThat(report.issues()).isEmpty();
        ScaInventory legacy = new ScaInventory("0.1", null, null, null, null, null, null, null);
        BpmFaultReport nullable = BpmFaultAnalysis.of(List.of(legacy), List.of("legacy"));
        assertThat(nullable.inputs()).containsExactly(new Input(1, "legacy", null));
        assertThat(nullable.processes()).isEmpty();
    }

    @Test
    void deepImmutableEvidenceSnapshots() {
        Locator at = new Locator(ARCHIVE, PROCESS, 1);
        Element element = new Element(at, "definition", "Galaxy.Workflow.ErrorEventDefinition", Map.of("isCritical", "false"),
                Map.of(), Map.of(), List.of());
        Endpoint endpoint = new Endpoint("definition", "missing", List.of());
        Link link = new Link(at, "Galaxy.Workflow.ActivityContainsEventDefinitions", endpoint, endpoint);
        List<Fact> facts = mutable(new Fact("element", at), new Fact("link", at), null);
        List<Element> elements = mutable(element, null);
        List<Link> links = mutable(link, null);
        List<Locator> path = mutable(at, null);
        List<String> reasons = mutable("unknown", null);
        Evidence evidence = new Evidence(facts, elements, links);
        List<Condition> conditions = mutable(new Condition("not-evaluated", "false", facts, reasons), null);
        Scope scope = new Scope(at, "unknown", "unknown", path, facts, reasons);
        List<Scope> scopes = mutable(scope, null);
        FaultReference fault = new FaultReference(1, link, "missing", evidence, reasons);
        List<FaultReference> faults = mutable(fault, null);
        Flag flag = new Flag(true, "false", "false");
        Definition definition = new Definition(element, "error", flag, flag, faults, evidence, reasons);
        List<Definition> definitions = mutable(definition, null);
        Declaration declaration = new Declaration(1, "unknown", endpoint, null, endpoint, scopes, definitions, null,
                conditions, evidence, "unknown", reasons);
        List<Declaration> declarations = mutable(declaration, null);
        ProcessFaults process = new ProcessFaults(new ProcessKey(1, 1, 1), "synthetic", "id", null, null,
                "incomplete", declarations, reasons);
        List<ProcessFaults> processes = mutable(process, null);
        Unattributed unassigned = new Unattributed(1, 1, "synthetic", "orphan-metadata", evidence, reasons);
        List<Unattributed> unattributed = mutable(unassigned, null);
        List<Input> inputs = mutable(new Input(1, "synthetic", null), null);
        List<Issue> issues = mutable(new Issue(Issue.WARNING, "SYNTHETIC", "retained", null, null), null);
        BpmFaultReport report = new BpmFaultReport(1, "bpm-faults-v1", inputs, processes, unattributed, issues);
        String snapshot = Json.write(report);
        for (List<?> list : List.of(facts, elements, links, path, reasons, conditions, scopes, faults, definitions,
                declarations, processes, unattributed, inputs, issues)) list.clear();
        assertThat(Json.write(report)).isEqualTo(snapshot);
        for (List<?> list : List.of(report.inputs(), report.processes(), report.unattributed(), report.issues(),
                process.declarations(), process.reasons(), declaration.scopes(), declaration.definitions(), declaration.conditions(),
                declaration.reasons(), scope.path(), scope.basis(), scope.reasons(), definition.faults(), definition.reasons(),
                fault.reasons(), evidence.path(), evidence.elements(), evidence.links(), unassigned.reasons())) {
            assertThat(list).containsNull();
            assertThatThrownBy(() -> list.add(null)).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(evidence.path()).containsExactly(new Fact("element", at), new Fact("link", at), null);
        // Required collection arguments reject null instead of silently fabricating empty evidence.
        for (Runnable invalid : List.<Runnable>of(
                () -> new Evidence(null, List.of(), List.of()), () -> new Evidence(List.of(), null, List.of()),
                () -> new Evidence(List.of(), List.of(), null),
                () -> new Scope(null, "unknown", "unknown", null, List.of(), List.of()),
                () -> new Scope(null, "unknown", "unknown", List.of(), null, List.of()),
                () -> new Scope(null, "unknown", "unknown", List.of(), List.of(), null),
                () -> new Definition(null, "unknown", flag, flag, null, evidence, List.of()),
                () -> new Definition(null, "unknown", flag, flag, List.of(), evidence, null),
                () -> new FaultReference(1, null, "unknown", evidence, null),
                () -> new Unattributed(1, 1, null, "unknown", evidence, null),
                () -> new ProcessFaults(null, null, null, null, null, "unknown", null, List.of()),
                () -> new ProcessFaults(null, null, null, null, null, "unknown", List.of(), null),
                () -> new Declaration(1, "unknown", null, null, null, null, List.of(), null, List.of(), evidence, "unknown", List.of()),
                () -> new Declaration(1, "unknown", null, null, null, List.of(), null, null, List.of(), evidence, "unknown", List.of()),
                () -> new Declaration(1, "unknown", null, null, null, List.of(), List.of(), null, null, evidence, "unknown", List.of()),
                () -> new Declaration(1, "unknown", null, null, null, List.of(), List.of(), null, List.of(), evidence, "unknown", null),
                () -> new BpmFaultReport(1, "bpm-faults-v1", null, List.of(), List.of(), List.of()),
                () -> new BpmFaultReport(1, "bpm-faults-v1", List.of(), null, List.of(), List.of()),
                () -> new BpmFaultReport(1, "bpm-faults-v1", List.of(), List.of(), null, List.of()),
                () -> new BpmFaultReport(1, "bpm-faults-v1", List.of(), List.of(), List.of(), null))) {
            assertThatThrownBy(invalid::run);
        }
    }

    @Test
    void jsonKeepsNullEmptyAndRawFlags() {
        Evidence evidence = new Evidence(List.of(), List.of(), List.of());
        Flag absent = new Flag(false, null, "unknown");
        Flag invalid = new Flag(true, " TRUE ", "unknown");
        Scope scope = new Scope(null, "unknown", "unknown", List.of(), List.of(), List.of());
        FaultReference fault = new FaultReference(1, null, "missing", evidence, List.of());
        Definition definition = new Definition(null, "unknown", absent, invalid, List.of(fault), evidence, List.of());
        Builtin builtin = new Builtin(null, null, null, false, false, new Parameter(null, null, null), null, null, null);
        Declaration declaration = new Declaration(1, "unknown", null, null, null, List.of(scope), List.of(definition), builtin,
                List.of(), evidence, "unknown", List.of());
        ProcessFaults process = new ProcessFaults(new ProcessKey(1, 1, 1), null, null, null, null, "unavailable", List.of(declaration), List.of());
        BpmFaultReport report = new BpmFaultReport(1, "bpm-faults-v1", List.of(new Input(1, "label", null)), List.of(process),
                List.of(new Unattributed(1, 1, null, "orphan-metadata", evidence, List.of())), List.of());
        JsonNode json = Json.mapper().readTree(Json.write(report));
        assertThat(json.propertyNames()).containsExactly("formatVersion", "analysisVersion", "inputs", "processes", "unattributed", "issues");
        assertThat(json.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(json.path("analysisVersion").asString()).isEqualTo("bpm-faults-v1");
        assertEmpty(json, "issues");
        assertNull(json.at("/inputs/0"), "sha256");
        JsonNode p = json.at("/processes/0");
        assertThat(p.propertyNames()).containsExactly("key", "dc", "id", "name", "source", "coverage", "declarations", "reasons");
        for (String key : List.of("dc", "id", "name", "source")) assertNull(p, key);
        assertEmpty(p, "reasons");
        JsonNode d = p.at("/declarations/0");
        assertThat(d.propertyNames()).containsExactly("ordinal", "role", "owner", "event", "definition", "scopes", "definitions", "builtin", "conditions", "evidence", "state", "reasons");
        for (String key : List.of("owner", "event", "definition")) assertNull(d, key);
        assertEmpty(d, "conditions");
        assertEmpty(d, "reasons");
        JsonNode s = d.at("/scopes/0");
        assertThat(s.propertyNames()).containsExactly("owner", "state", "origin", "path", "basis", "reasons");
        assertNull(s, "owner");
        for (String key : List.of("path", "basis", "reasons")) assertEmpty(s, key);
        JsonNode df = d.at("/definitions/0");
        assertThat(df.propertyNames()).containsExactly("source", "kind", "critical", "technical", "faults", "evidence", "reasons");
        assertNull(df, "source");
        assertThat(df.at("/critical/present").asBoolean()).isFalse();
        assertNull(df.path("critical"), "value");
        assertThat(df.at("/technical/value").asString()).isEqualTo(" TRUE ");
        assertThat(df.at("/technical/interpretation").asString()).isEqualTo("unknown");
        assertEmpty(df, "reasons");
        JsonNode f = df.at("/faults/0");
        assertThat(f.propertyNames()).containsExactly("ordinal", "link", "state", "evidence", "reasons");
        assertNull(f, "link");
        assertEmpty(f, "reasons");
        JsonNode b = d.path("builtin");
        assertThat(b.propertyNames()).containsExactly("id", "kind", "name", "critical", "technical", "parameter", "file", "sourceDc", "resource");
        for (String key : List.of("id", "kind", "name", "file", "sourceDc", "resource")) assertNull(b, key);
        assertThat(b.path("parameter").propertyNames()).containsExactly("name", "namespace", "type");
        for (String key : List.of("name", "namespace", "type")) assertNull(b.path("parameter"), key);
        JsonNode e = d.path("evidence");
        assertThat(e.propertyNames()).containsExactly("path", "elements", "links");
        for (String key : List.of("path", "elements", "links")) assertEmpty(e, key);
        JsonNode u = json.at("/unattributed/0");
        assertThat(u.propertyNames()).containsExactly("input", "component", "dc", "kind", "evidence", "reasons");
        assertNull(u, "dc");
        assertEmpty(u, "reasons");
        JsonNode issue = Json.mapper().readTree(Json.write(new Issue(Issue.WARNING, "SYNTHETIC", "retained", null, null)));
        assertThat(issue.path("location").isMissingNode()).isTrue();
        assertThat(issue.path("source").isMissingNode()).isTrue();
    }

    @Test
    void repeatIsDeterministicAndInputsUnchanged() {
        BpmFaultFixture fixture = chain().error("error", "true", "false").boundary("map", "boundary", "error")
                .condition("boundary", "condition", "false").literal("condition", "literal", "false")
                .fault("error", "missing-fault");
        Path file = SyntheticBpmSca.write(dir.resolve("repeat.sca"), fixture.partitions());
        ScaInventory inventory = PoXray.read(file);
        String original = Json.write(inventory);
        BpmFaultReport first = BpmFaultAnalysis.of(List.of(inventory), List.of("same"));
        BpmFaultReport second = BpmFaultAnalysis.of(List.of(inventory), List.of("same"));
        BpmFaultReport reread = BpmFaultAnalysis.of(List.of(PoXray.read(file)), List.of("same"));
        assertThat(Json.write(first)).isEqualTo(Json.write(second)).isEqualTo(Json.write(reread));
        assertThat(BpmFaultAnalysis.markdown(first)).isEqualTo(BpmFaultAnalysis.markdown(second)).isEqualTo(BpmFaultAnalysis.markdown(reread));
        assertThat(Json.write(inventory)).isEqualTo(original);
        assertThat(first.issues().subList(0, inventory.issues().size())).containsExactlyElementsOf(inventory.issues());
        assertThat(Json.write(first)).doesNotContain("readAt", "generatedAt", "timestamp");
    }

    @Test
    void markdownEscapesValuesAndNamesSemanticLimits() {
        ScaInventory inventory = chain().error("error|name", "false", "true")
                .boundary("map", "boundary", "error|name").condition("boundary", "expr", "opaque|predicate\nline")
                .read(dir.resolve("markdown.sca"));
        String markdown = BpmFaultAnalysis.markdown(BpmFaultAnalysis.of(List.of(inventory), List.of("input|label\nnext")));
        assertThat(markdown).contains("input\\|label", "error\\|name", "opaque\\|predicate");
        assertThat(markdown).doesNotContain("input|label\nnext", "opaque|predicate\nline");
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("source", "coverage", "runtime", "handler", "propagat", "synchronous", "acknowledg");
    }

    @SafeVarargs private static <T> List<T> mutable(T... values) { return new ArrayList<>(Arrays.asList(values)); }
    private static void assertNull(JsonNode node, String field) { assertThat(node.path(field).isNull()).as(field).isTrue(); }
    private static void assertEmpty(JsonNode node, String field) {
        assertThat(node.path(field).isArray()).as(field).isTrue();
        assertThat(node.path(field).isEmpty()).as(field).isTrue();
    }
}
