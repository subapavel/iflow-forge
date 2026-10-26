package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.CATALOG;
import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;
import static io.poxray.SyntheticBpmSca.process;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Candidate;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.BpmSourceEvidence.Property;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Public read-path acceptance for parsed-source evidence; fixtures make no runtime claims. */
class BpmSourceEvidenceTest {
    @TempDir Path dir;

    @Test
    void sourceEvidenceIncludesUninterpretedProcessPartitionsInIndexOrder() {
        String broken = "src/bpmn/broken.bpmn";
        String alpha = "src/bpmn/a-process.bpmn";
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, process("z-", "Zeta", "", ""));
        partitions.put(broken, partition(element("Unknown.Extension", "unknown", "active='odd'", "")));
        partitions.put(alpha, process("a-", "Alpha", "", ""));
        partitions.put("src/model/other.model", partition(element("Unrelated.Type", "outside", "", "")));
        partitions.put("src/bpmn/layout.diagram", partition(element("Galaxy.Workflow.Hidden", "diagram", "", "")));

        ScaInventory inventory = read(partitions);
        BpmComponent bpm = inventory.bpm().getFirst();
        BpmSourceEvidence evidence = bpm.sourceEvidence();

        assertThat(inventory.schemaVersion()).isEqualTo("0.1");
        assertThat(evidence.formatVersion()).isEqualTo(1);
        assertThat(evidence.selectionVersion()).isEqualTo("bpm-source-v1");
        assertThat(evidence.indexedPartitions()).containsExactly(PROCESS, broken, alpha, "src/model/other.model");
        assertThat(evidence.processPartitions()).containsExactly(PROCESS, broken, alpha);
        assertThat(bpm.processes()).extracting(Process::name).containsExactly("Alpha", "Zeta");
        assertThat(evidence.elements()).extracting(Element::id)
                .containsExactly("z-collab", "z-pool", "unknown", "a-collab", "a-pool");
        assertThat(fact(evidence, "unknown").location()).isEqualTo(new Locator(ARCHIVE, broken, 1));
        assertThat(fact(evidence, "unknown").metaclass()).isEqualTo("Unknown.Extension");
        assertThat(fact(evidence, "unknown").attributes()).containsEntry("active", "odd");
        assertThat(inventory.issues()).anySatisfy(i -> assertThat(i.message()).contains("without Collaboration"));
    }

    @Test
    void sourceEvidencePreservesFalseTrueAbsentAndInvalidWithoutChangingLegacyProjections() {
        StringBuilder elements = new StringBuilder(element("Galaxy.Workflow.StartEvent", "start", "", ""));
        StringBuilder links = new StringBuilder();
        List<String> objects = new ArrayList<>(List.of("start"));
        String[] suffixes = {"false", "true", "absent", "other"};
        String[] values = {"false", "true", null, "not-a-boolean"};
        for (int i = 0; i < suffixes.length; i++) {
            String end = "end-" + suffixes[i];
            String flow = "flow-" + suffixes[i];
            elements.append(element("Galaxy.Workflow.EndEvent", end, attribute("terminate", values[i]), ""));
            elements.append(element("Galaxy.Workflow.SequenceConnector", flow, attribute("isDefault", values[i]), ""));
            elements.append(element("Galaxy.Mapping.MappingPart", "part-" + suffixes[i], attribute("enabled", values[i]), ""));
            objects.add(end);
            objects.add(flow);
            links.append(link("Galaxy.Workflow.SourceOfIncomingConnectors", "start", flow));
            links.append(link("Galaxy.Workflow.TargetOfOutgoingConnectors", end, flow));
        }
        ScaInventory inventory = read(Map.of(PROCESS,
                process("p-", "Presence", elements.toString(), links.toString(), objects.toArray(String[]::new))));
        BpmComponent bpm = inventory.bpm().getFirst();
        BpmSourceEvidence evidence = bpm.sourceEvidence();
        for (int i = 0; i < suffixes.length; i++) {
            for (String prefix : List.of("end-", "flow-", "part-")) {
                String key = prefix.equals("end-") ? "terminate" : prefix.equals("flow-") ? "isDefault" : "enabled";
                Map<String, String> attributes = fact(evidence, prefix + suffixes[i]).attributes();
                if (values[i] == null) {
                    assertThat(attributes).doesNotContainKey(key);
                } else {
                    assertThat(attributes).containsEntry(key, values[i]);
                }
            }
            String suffix = suffixes[i];
            Boolean legacy = "true".equals(values[i]) ? Boolean.TRUE : null;
            assertThat(bpm.processes().getFirst().nodes().stream().filter(n -> n.id().equals("end-" + suffix))
                    .findFirst().orElseThrow().terminate()).isEqualTo(legacy);
            assertThat(bpm.processes().getFirst().flows().stream().filter(f -> f.id().equals("flow-" + suffix))
                    .findFirst().orElseThrow().isDefault()).isEqualTo(legacy);
        }
    }

    @Test
    void sourceLinksKeepMissingAmbiguousAndExcludedEndpointsWithOccurrenceIdentity() {
        ScaInventory inventory = read(referencePartitions());
        BpmSourceEvidence evidence = evidence(inventory);
        assertThat(evidence.links()).hasSize(5);
        Link first = evidence.links().getFirst();
        assertThat(first.location()).isEqualTo(new Locator(ARCHIVE, PROCESS, 1));
        assertThat(first.first()).isEqualTo(new Endpoint("ghost", "missing", List.of()));
        assertThat(first.second().id()).isEqualTo("duplicate");
        assertThat(first.second().resolution()).isEqualTo("ambiguous");
        assertThat(first.second().candidates()).containsExactly(
                new Candidate(new Locator(ARCHIVE, PROCESS, 1), "Unknown.First", true),
                new Candidate(new Locator(ARCHIVE, PROCESS, 2), "Unknown.Second", true),
                new Candidate(new Locator(ARCHIVE, CATALOG, 1), "WSDL1.Operation", false));
        Link boundary = evidence.links().get(4);
        assertThat(boundary.second()).isEqualTo(new Endpoint("wsdl", "unique", List.of(
                new Candidate(new Locator(ARCHIVE, CATALOG, 2), "WSDL1.Message", false))));
        assertThat(evidence.elements()).extracting(Element::id).doesNotContain("wsdl");
        assertThat(sourceIssues(inventory, "BPM_SOURCE_REFERENCE_AMBIGUOUS")).hasSize(2);
        assertThat(sourceIssues(inventory, "BPM_SOURCE_REFERENCE_UNRESOLVED")).hasSize(6);
        assertThat(inventory.issues()).anySatisfy(i -> assertThat(i.code()).isEqualTo("DUPLICATE_ID"));
        assertThat(inventory.issues().stream().filter(i -> i.code().startsWith("BPM_SOURCE_REFERENCE")))
                .noneSatisfy(i -> assertThat(diagnosticIdentity(i)).contains("wsdl"));
    }

    @Test
    void repeatedBadLinksAndBothMissingSidesProduceDistinctDiagnostics() {
        ScaInventory inventory = read(referencePartitions());
        List<Issue> missing = sourceIssues(inventory, "BPM_SOURCE_REFERENCE_UNRESOLVED");
        List<Issue> ambiguous = sourceIssues(inventory, "BPM_SOURCE_REFERENCE_AMBIGUOUS");
        assertDiagnostic(missing, "Example.Edge", "ghost", 1, "f");
        assertDiagnostic(missing, "Example.Edge", "ghost", 2, "f");
        assertDiagnostic(ambiguous, "Example.Edge", "duplicate", 1, "s");
        assertDiagnostic(ambiguous, "Example.Edge", "duplicate", 2, "s");
        assertDiagnostic(missing, "Example.Both", "absent", 3, "f");
        assertDiagnostic(missing, "Example.Both", "absent", 3, "s");
        assertDiagnostic(missing, "Example.Empty", null, 4, "f");
        assertDiagnostic(missing, "Example.Empty", "", 4, "s");
        assertThat(missing.stream().map(BpmSourceEvidenceTest::diagnosticIdentity).distinct()).hasSize(6);
        assertThat(missing).allSatisfy(i -> assertThat(i.severity()).isEqualTo(Issue.WARNING));
    }

    @Test
    void sourceOccurrencesKeepDuplicateLinksNullAndBlankIdsAndStableJson() {
        Path sca = SyntheticBpmSca.write(dir.resolve("stable.sca"), referencePartitions());
        BpmSourceEvidence first = evidence(PoXray.read(sca));
        BpmSourceEvidence second = evidence(PoXray.read(sca));
        assertThat(Json.write(first)).isEqualTo(Json.write(second));
        assertThat(first.links()).extracting(l -> l.location().ordinal()).containsExactly(1, 2, 3, 4, 5);
        assertThat(first.links().get(0).association()).isEqualTo(first.links().get(1).association());
        assertThat(first.links().get(0).first()).isEqualTo(first.links().get(1).first());
        assertThat(first.links().get(0).second()).isEqualTo(first.links().get(1).second());
        assertThat(first.links().get(3).first()).isEqualTo(new Endpoint(null, "missing", List.of()));
        assertThat(first.links().get(3).second()).isEqualTo(new Endpoint("", "missing", List.of()));
        assertThat(first.elements()).extracting(Element::location).containsExactly(
                new Locator(ARCHIVE, PROCESS, 1), new Locator(ARCHIVE, PROCESS, 2), new Locator(ARCHIVE, PROCESS, 3));
    }

    @Test
    void mappingEvidenceRetainsOwnerRootChildQNameAndExpressionIdentities() {
        Map<String, String> partitions = SyntheticBpmSca.mappedProcess();
        partitions.put("src/schema/unrelated.xsd", partition(element("XSD1.key.QName", "unrelated-qname", "name='leaf'", "")));
        ScaInventory inventory = read(partitions);
        BpmSourceEvidence evidence = evidence(inventory);
        assertLink(evidence, "Galaxy.Workflow.InputMappingOfMappableObject", "mapping", "owner");
        assertLink(evidence, "Galaxy.Mapping.MappingContainsMappingParts", "root", "mapping");
        assertLink(evidence, "Galaxy.Mapping.MappingPartHasChildren", "child", "root");
        assertLink(evidence, "Galaxy.Mapping.MappingPartHasQName", "root-qname", "root");
        assertLink(evidence, "Galaxy.Mapping.MappingPartHasQName", "child-qname", "child");
        assertLink(evidence, "Galaxy.Mapping.MappingPartContainsExpression", "expr", "child");
        assertThat(fact(evidence, "expr").attributes()).containsEntry("stringExpression", "\"mapped|value\"");
        assertThat(fact(evidence, "child-qname").attributes()).containsEntry("name", "leaf");
        assertThat(evidence.elements()).extracting(Element::id).doesNotContain("unrelated-qname");
        Link missingPart = evidence.links().stream().filter(l -> "missing-part".equals(l.first().id())).findFirst().orElseThrow();
        assertThat(missingPart.first().resolution()).isEqualTo("missing");
        assertThat(missingPart.first().candidates()).isEmpty();
        assertThat(sourceIssues(inventory, "BPM_SOURCE_REFERENCE_UNRESOLVED"))
                .anySatisfy(i -> assertThat(diagnosticIdentity(i)).contains("missing-part"));
        var assignments = inventory.bpm().getFirst().processes().getFirst().nodes().stream()
                .filter(n -> n.id().equals("owner")).findFirst().orElseThrow().inputMapping().assignments();
        assertThat(assignments).extracting(a -> a.target()).containsExactly("target", "target/leaf");
        assertThat(assignments.getFirst().expr()).isNull();
        assertThat(assignments.getFirst().enabled()).isFalse();
        assertThat(assignments.get(1).enabled()).isNull();
    }

    @Test
    void loopAndBoundaryEvidenceRetainsExactSourceSpellingAndDirections() {
        String body = element("Galaxy.Workflow.MultiInstanceLoopCharacteristics", "loop-false", "sequential='false' flowCondition='all'", "")
                + element("Galaxy.Workflow.MultiInstanceLoopCharacteristics", "loop-true", "sequential='true' flowCondition='unsupported-token'", "")
                + element("Galaxy.Workflow.MultiInstanceLoopCharacteristics", "loop-missing", "", "")
                + element("Galaxy.Workflow.MultiInstanceLoopCharacteristics", "loop-other", "sequential='unknown-token'", "")
                + element("Galaxy.Workflow.EmbeddedScope", "scope", "", "")
                + element("Galaxy.Rule.Expression", "loop-expression", "stringExpression='collection'", "")
                + element("Galaxy.Workflow.DataObject", "item", "", "")
                + element("Galaxy.Workflow.DataObject", "completed", "", "")
                + element("Galaxy.Workflow.AutomatedActivity", "activity", "", "")
                + element("Galaxy.Workflow.BoundaryEvent", "boundary", "", "")
                + element("Galaxy.Workflow.ErrorEventDefinition", "error", "isCritical='false' isTechnical='true'", "")
                + link("Galaxy.Workflow.EmbeddedScopeContainsLoopCharcateristics", "loop-false", "scope")
                + link("Galaxy.Workflow.MILoopCharacteristicsContainsExpression", "loop-expression", "loop-false")
                + link("Galaxy.Workflow.MILoopCharacteristicsContainsCurrentCollectionItemDO", "item", "loop-false")
                + link("Galaxy.Workflow.MILoopCharacteristicsHasNumberOfCompletedIterationsDO", "completed", "loop-false")
                + link("Galaxy.Workflow.ActivityHasBoundaryEvent", "boundary", "activity")
                + link("Galaxy.Workflow.CatchEventHasTrigger", "error", "boundary")
                + link("Galaxy.Workflow.ErrorEventDefinitionHasWSDLFault", "missing-fault", "error");
        BpmSourceEvidence evidence = evidence(read(Map.of(PROCESS, partition(body))));
        assertThat(fact(evidence, "loop-false").attributes()).containsEntry("sequential", "false").containsEntry("flowCondition", "all");
        assertThat(fact(evidence, "loop-true").attributes()).containsEntry("sequential", "true").containsEntry("flowCondition", "unsupported-token");
        assertThat(fact(evidence, "loop-missing").attributes()).isEmpty();
        assertThat(fact(evidence, "loop-other").attributes()).containsEntry("sequential", "unknown-token");
        assertLink(evidence, "Galaxy.Workflow.EmbeddedScopeContainsLoopCharcateristics", "loop-false", "scope");
        assertLink(evidence, "Galaxy.Workflow.MILoopCharacteristicsContainsExpression", "loop-expression", "loop-false");
        assertLink(evidence, "Galaxy.Workflow.MILoopCharacteristicsContainsCurrentCollectionItemDO", "item", "loop-false");
        assertLink(evidence, "Galaxy.Workflow.MILoopCharacteristicsHasNumberOfCompletedIterationsDO", "completed", "loop-false");
        assertLink(evidence, "Galaxy.Workflow.ActivityHasBoundaryEvent", "boundary", "activity");
        assertLink(evidence, "Galaxy.Workflow.CatchEventHasTrigger", "error", "boundary");
        assertLink(evidence, "Galaxy.Workflow.ErrorEventDefinitionHasWSDLFault", "missing-fault", "error");
        assertThat(Json.write(evidence)).doesNotContain("runtimeSupported", "executionMode", "handlerPriority");
    }

    @Test
    void selectionIncludesExactSeedPrefixesAndDirectSupportsWithoutRecursion() {
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put("src/model/seeds.model", partition(
                element("Galaxy.Workflow.Unknown", "workflow", "", "")
                + element("Galaxy.Mapping.Unknown", "mapping", "", "")
                + element("Galaxy.Rule.Unknown", "rule", "", "")
                + element("Galaxy.ProcessSupplements.NotificationActivity", "notice", "", "")
                + element("Galaxy.ProcessSupplements.Other", "not-notice", "", "")
                + element("Galaxy.Workflowish.Other", "not-workflow", "", "")
                + link("Other.QName", "qname", "workflow")
                + link("Other.Property", "rule", "bag")
                + link("Galaxy.Rule.Orphan", "missing-a", "missing-b")));
        partitions.put(CATALOG, partition(
                element("XSD1.key.QName", "qname", "name='type'", "")
                + element("properties.PropertyBag", "bag", "", "<properties key='origin' value='synthetic'/>")
                + element("WSDL1.Message", "wsdl-tree", "", "")
                + element("Unrelated.Type", "unrelated", "", "")
                + element("XSD1.key.QName", "recursive-qname", "name='nested'", "")
                + link("Other.Recursive", "qname", "recursive-qname")
                + link("Other.WsdlTree", "bag", "wsdl-tree")));
        BpmSourceEvidence evidence = evidence(read(partitions));
        assertThat(evidence.indexedPartitions()).containsExactly("src/model/seeds.model", CATALOG);
        assertThat(evidence.processPartitions()).isEmpty();
        assertThat(evidence.elements()).extracting(Element::id).containsExactly("workflow", "mapping", "rule", "notice", "qname", "bag");
        assertThat(evidence.links()).extracting(Link::association).containsExactly("Other.QName", "Other.Property", "Galaxy.Rule.Orphan");
        assertThat(evidence.links().getFirst().first().candidates()).containsExactly(
                new Candidate(new Locator(ARCHIVE, CATALOG, 1), "XSD1.key.QName", true));
        assertThat(evidence.links().get(1).second().candidates()).containsExactly(
                new Candidate(new Locator(ARCHIVE, CATALOG, 2), "properties.PropertyBag", true));
        assertThat(evidence.links().get(2).first().resolution()).isEqualTo("missing");
        assertThat(evidence.links().get(2).second().resolution()).isEqualTo("missing");
    }

    @Test
    void parsedMapsKeepEncounterOrderTextsAndRepeatedValuesAndNormalizeLocalReferences() {
        String body = element("Unknown.Payload", "payload", "zeta='last' alpha='first'",
                "<name originalText='Synthetic payload'/><documentation originalText='line one&#10;line two'/>"
                + "<items v='second'/><items v='first'/><other v='tail'/>"
                + "<properties key='zeta' value='one'/><properties key='alpha' value='two'/>")
                + link("Other.Normalized", "local:payload", "local:missing");
        BpmSourceEvidence evidence = evidence(read(Map.of(PROCESS, partition(body))));
        Element payload = fact(evidence, "payload");
        assertThat(payload.attributes().keySet()).containsExactly("zeta", "alpha");
        assertThat(payload.texts().keySet()).containsExactly("name", "documentation");
        assertThat(payload.texts()).containsEntry("documentation", "line one\nline two");
        assertThat(payload.values().keySet()).containsExactly("items", "other");
        assertThat(payload.values().get("items")).containsExactly("second", "first");
        assertThat(payload.properties()).containsExactly(new Property("zeta", "one"), new Property("alpha", "two"));
        assertThat(evidence.links().getFirst().first().id()).isEqualTo("payload");
        assertThat(evidence.links().getFirst().second().id()).isEqualTo("missing");
        assertThat(payload.attributes()).doesNotContainKey("i");
    }

    @Test
    void malformedPropertyKeysRemainVisibleWithDiagnosticsAndSerializableNullValues() {
        String body = element("Unknown.Payload", "malformed", "", "<properties key='valid' value='kept'/>"
                + "<properties value='keyless'/><properties key='nullable'/>")
                + element("Unknown.Payload", "unaffected", "flag='present'", "");
        ScaInventory inventory = read(Map.of(PROCESS, partition(body)));
        Element malformed = fact(evidence(inventory), "malformed");
        assertThat(malformed.properties()).containsExactly(new Property("valid", "kept"),
                new Property(null, "keyless"), new Property("nullable", null));
        assertThat(fact(evidence(inventory), "unaffected").attributes()).containsEntry("flag", "present");
        List<Issue> warnings = sourceIssues(inventory, "BPM_SOURCE_PROPERTY_KEY_MISSING");
        assertThat(warnings).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Issue.WARNING);
            assertThat(diagnosticIdentity(issue)).contains(PROCESS, "malformed", "1", "2");
        });
        var json = Json.mapper().readTree(Json.write(inventory)).path("bpm").get(0).path("sourceEvidence");
        var properties = json.path("elements").get(0).path("properties");
        assertThat(properties.size()).isEqualTo(3);
        assertThat(properties.get(1).has("key")).isTrue();
        assertThat(properties.get(1).path("key").isNull()).isTrue();
        assertThat(properties.get(1).path("value").asString()).isEqualTo("keyless");
        assertThat(properties.get(2).has("value")).isTrue();
        assertThat(properties.get(2).path("value").isNull()).isTrue();
    }

    @Test
    void evidenceIsDeeplyImmutableAndDetachedFromMutableInputs() {
        Map<String, String> attributes = new LinkedHashMap<>(Map.of("flag", "false"));
        Map<String, String> texts = new LinkedHashMap<>(Map.of("name", "before"));
        List<String> nested = new ArrayList<>(List.of("before"));
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("items", nested);
        List<Property> properties = new ArrayList<>(List.of(new Property(null, "before")));
        Element element = new Element(new Locator(ARCHIVE, PROCESS, 1), "seed", "Unknown.Type", attributes, texts, values, properties);
        List<Candidate> candidates = new ArrayList<>(List.of(new Candidate(element.location(), element.metaclass(), true)));
        Endpoint endpoint = new Endpoint("seed", "unique", candidates);
        List<Element> elements = new ArrayList<>(List.of(element));
        List<Link> links = new ArrayList<>(List.of(new Link(new Locator(ARCHIVE, PROCESS, 1), "Example.Edge", endpoint, endpoint)));
        List<String> indexed = new ArrayList<>(List.of(PROCESS));
        List<String> selected = new ArrayList<>(List.of(PROCESS));
        BpmSourceEvidence evidence = new BpmSourceEvidence(1, "bpm-source-v1", indexed, selected, elements, links);
        String before = Json.write(evidence);

        attributes.put("flag", "true");
        texts.clear();
        nested.add("after");
        values.clear();
        properties.clear();
        candidates.clear();
        indexed.clear();
        selected.clear();
        elements.clear();
        links.clear();
        assertThat(Json.write(evidence)).isEqualTo(before);
        assertThatThrownBy(() -> evidence.indexedPartitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evidence.processPartitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evidence.elements().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evidence.links().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> element.attributes().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> element.texts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> element.values().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> element.values().get("items").add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> element.properties().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> endpoint.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void newEvidenceSerializesExplicitEmptyCollectionsAndNullableFields() {
        Element empty = new Element(new Locator(null, PROCESS, 1), null, null, Map.of(), Map.of(), Map.of(), List.of());
        Endpoint missing = new Endpoint(null, "missing", List.of());
        BpmSourceEvidence evidence = new BpmSourceEvidence(1, "bpm-source-v1", List.of(), List.of(), List.of(empty),
                List.of(new Link(new Locator(ARCHIVE, PROCESS, 1), null, missing, missing)));
        var tree = Json.mapper().readTree(Json.write(evidence));
        assertThat(tree.path("indexedPartitions").isArray()).isTrue();
        assertThat(tree.path("processPartitions").isArray()).isTrue();
        var fact = tree.path("elements").get(0);
        assertThat(fact.path("attributes").isObject()).isTrue();
        assertThat(fact.path("texts").isObject()).isTrue();
        assertThat(fact.path("values").isObject()).isTrue();
        assertThat(fact.path("properties").isArray()).isTrue();
        assertThat(fact.path("id").isNull()).isTrue();
        assertThat(fact.path("metaclass").isNull()).isTrue();
        assertThat(fact.path("location").path("archive").isNull()).isTrue();
        assertThat(tree.path("links").get(0).path("association").isNull()).isTrue();
        assertThat(tree.path("links").get(0).path("first").path("id").isNull()).isTrue();
        assertThat(tree.path("links").get(0).path("first").path("candidates").isArray()).isTrue();
        BpmSourceEvidence emptyEvidence = new BpmSourceEvidence(1, "bpm-source-v1", List.of(), List.of(), List.of(), List.of());
        var emptyTree = Json.mapper().readTree(Json.write(emptyEvidence));
        assertThat(emptyTree.path("elements").isArray()).isTrue();
        assertThat(emptyTree.path("links").isArray()).isTrue();
        assertThat(Json.mapper().readValue(Json.write(evidence), BpmSourceEvidence.class)).isEqualTo(evidence);
    }

    @Test
    void legacyConstructorAndOldJsonKeepEvidenceUnavailableAndOmitted() {
        BpmComponent legacy = new BpmComponent("example.test/evidence/bpm", List.of(), emptyCatalog());
        assertThat(legacy.sourceEvidence()).isNull();
        var shape = Json.mapper().readTree(Json.write(legacy));
        assertThat(shape.has("sourceEvidence")).isFalse();
        assertThat(shape.propertyNames()).containsExactly("dc", "catalog");
        BpmComponent oldJson = Json.mapper().readValue("{\"dc\":\"old/bpm\",\"processes\":[],\"catalog\":{}}", BpmComponent.class);
        assertThat(oldJson.sourceEvidence()).isNull();
        assertThat(Json.mapper().readTree(Json.write(oldJson)).has("sourceEvidence")).isFalse();
    }

    @Test
    void noSelectedFactsIsAnExplicitSelectionResultRatherThanUnavailableEvidence() {
        BpmSourceEvidence evidence = evidence(read(Map.of(CATALOG,
                partition(element("WSDL1.Message", "unrelated", "", "")))));
        assertThat(evidence.indexedPartitions()).containsExactly(CATALOG);
        assertThat(evidence.processPartitions()).isEmpty();
        assertThat(evidence.elements()).isEmpty();
        assertThat(evidence.links()).isEmpty();
        assertThat(evidence.selectionVersion()).isEqualTo("bpm-source-v1");
    }

    @Test
    void markdownShowsInitialValuesStructuralAssignmentsDisabledFlagsAndEscaping() {
        ScaInventory inventory = read(SyntheticBpmSca.mappedProcess());
        assertThat(inventory.bpm().getFirst().processes().getFirst().dataObjects().getFirst().initialValue())
                .isNotNull();
        String markdown = Documentation.render(inventory, DocFormat.MARKDOWN, Locale.ENGLISH);
        assertThat(markdown).contains("seed\\|value", "mapped\\|value", "target/leaf", "bpm-source-v1", PROCESS,
                "sequential", "false", "flowCondition", "all", "missing-target", "missing-part");
        String structural = markdown.lines().filter(line -> line.contains("`target`") && line.startsWith("|")).findFirst().orElseThrow();
        assertThat(structural.toLowerCase(Locale.ROOT)).contains("structural", "disabled");
        assertThat(structural.toLowerCase(Locale.ROOT)).doesNotContain("delete", "null");
        assertThat(markdown).doesNotContain("\"seed|value\"", "\"mapped|value\"");
    }

    @Test
    void markdownRetainsRawLinkDirectionsResolutionAndLocators() {
        String markdown = Documentation.render(read(referencePartitions()), DocFormat.MARKDOWN, Locale.ENGLISH);
        assertThat(markdown).contains("bpm-source-v1", ARCHIVE, PROCESS, CATALOG, "Example.Edge", "ghost", "duplicate",
                "missing", "ambiguous", "wsdl", "unique");
        assertThat(markdown).containsPattern("(?is)(?:f\\s*[:=]|first)");
        assertThat(markdown).containsPattern("(?is)(?:s\\s*[:=]|second)");
        assertThat(markdown).containsPattern("(?is)(?:ordinal\\D*|[#:]\\s*|\\[|\\b[EL])1\\b");
    }

    @Test
    void legacyMarkdownSaysEvidenceIsUnavailable() {
        ScaInventory current = read(Map.of(PROCESS, process("p-", "Legacy", "", "")));
        BpmComponent bpm = current.bpm().getFirst();
        ScaInventory old = new ScaInventory(current.schemaVersion(), current.generator(), current.source(),
                current.developmentComponents(), List.of(new BpmComponent(bpm.dc(), bpm.processes(), bpm.catalog())),
                current.ejb(), current.issues(), current.unmapped());
        String markdown = Documentation.render(old, DocFormat.MARKDOWN, Locale.ENGLISH).toLowerCase(Locale.ROOT);
        assertThat(markdown).contains("unavailable", "inventory");
        assertThat(markdown).doesNotContain("evidence complete", "evidence verified");
    }

    private ScaInventory read(Map<String, String> partitions) {
        return PoXray.read(SyntheticBpmSca.write(dir.resolve("synthetic.sca"), partitions));
    }

    private static BpmSourceEvidence evidence(ScaInventory inventory) {
        assertThat(inventory.bpm()).hasSize(1);
        assertThat(inventory.bpm().getFirst().sourceEvidence()).isNotNull();
        return inventory.bpm().getFirst().sourceEvidence();
    }

    private static Element fact(BpmSourceEvidence evidence, String id) {
        return evidence.elements().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
    }

    private static String attribute(String key, String value) {
        return value == null ? "" : key + "='" + value + "'";
    }

    private static Map<String, String> referencePartitions() {
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, partition(
                element("Unknown.First", "duplicate", "", "")
                + element("Unknown.Second", "duplicate", "", "")
                + element("Unknown.Seed", "seed", "", "")
                + link("Example.Edge", "ghost", "duplicate")
                + link("Example.Edge", "ghost", "duplicate")
                + link("Example.Both", "absent", "absent")
                + link("Example.Empty", null, "")
                + link("Example.Boundary", "seed", "wsdl")));
        partitions.put(CATALOG, partition(element("WSDL1.Operation", "duplicate", "", "")
                + element("WSDL1.Message", "wsdl", "", "")));
        return partitions;
    }

    private static void assertLink(BpmSourceEvidence evidence, String association, String first, String second) {
        assertThat(evidence.links()).anySatisfy(link -> {
            assertThat(link.association()).isEqualTo(association);
            assertThat(link.first().id()).isEqualTo(first);
            assertThat(link.second().id()).isEqualTo(second);
        });
    }

    private static List<Issue> sourceIssues(ScaInventory inventory, String code) {
        return inventory.issues().stream().filter(i -> i.code().equals(code)).toList();
    }

    /** Deliberately excludes source: IssueLog does not use that field in its deduplication key. */
    private static String diagnosticIdentity(Issue issue) {
        return issue.message() + " " + issue.location();
    }

    private static void assertDiagnostic(List<Issue> issues, String association, String id, int ordinal, String side) {
        assertThat(issues).anySatisfy(issue -> {
            String identity = diagnosticIdentity(issue);
            assertThat(identity).contains(association, PROCESS);
            assertThat(identity).containsPattern("(?s).*\\b" + ordinal + "\\b.*");
            assertThat(identity).containsPattern("(?s).*\\b" + side + "\\b.*");
            if (id != null && !id.isEmpty()) {
                assertThat(identity).contains(id);
            }
        });
    }

    private static BpmComponent.Catalog emptyCatalog() {
        return new BpmComponent.Catalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
