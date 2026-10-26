package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.partition;
import static io.poxray.bpm.BpmDataFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Declared structural provenance, never runtime values or definite availability. */
class BpmDataAnalysisTest {
    @TempDir Path dir;
    private int files;

    @Test
    void initialOwnershipUsesDataObjectOccurrenceRatherThanTargetRootName() {
        BpmDataReport r = analyze(chain().object("owned", "payload", "pool").object("other", "different", "pool")
                .initial("owned", "initial", "seed", "different"));
        ProcessData p = process(r);
        Declaration d = declaration(p, "seed");
        assertThat(d.target().state()).isEqualTo("owned");
        assertThat(d.target().objects()).containsExactly(1);
        assertThat(d.candidacy()).isEqualTo("candidate");
        assertThat(d.targetPath().getFirst().candidates().getFirst().attributes()).containsEntry("name", "different");
        assertThat(mapping(p, "initial").role()).isEqualTo("initial");
        assertThat(mapping(p, "initial").readDomain()).isEqualTo("unknown");
        assertThat(origin(state(r, "start").before(), 1).declarations()).containsExactly(d.ordinal());
        assertThat(origin(state(r, "start").before(), 2).declarations()).isEmpty();
    }

    @Test
    void schemaTypeDefaultsDoNotBecomeExportedInitializers() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .raw("XSD1.XsdElementDeclaration", "schema", "name='payload' namespace='urn:type' default='seed'", "")
                .relation(W + "DataContainerReferencesXsdElementDeclaration", "schema", "object"));
        ProcessData p = process(r);
        assertThat(p.objects()).hasSize(1);
        assertThat(p.objects().getFirst().types()).hasSize(1);
        assertThat(p.declarations()).isEmpty();
        assertThat(origin(state(r, "start").before(), 1).declarations()).isEmpty();
        assertThat(origin(state(r, "start").before(), 1).coverage()).isEqualTo("complete");
    }

    @Test
    void structuralAndTextOnlyMappingPartsDoNotInventScalarWrites() {
        BpmDataFixture f = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "no-expression", "payload", "true")
                .root("mapping", "empty", "payload", "true").expression("empty", "empty-expr")
                .root("mapping", "text", "payload", "true")
                .raw(R + "Expression", "text-expr", "stringExpression='true'", "")
                .relation(M + "MappingPartContainsExpression", "text-expr", "text");
        BpmDataReport r = analyze(f);
        ProcessData p = process(r);
        assertThat(declaration(p, "no-expression").valueKind()).isEqualTo("structural");
        assertThat(declaration(p, "empty").valueKind()).isEqualTo("structural");
        assertThat(declaration(p, "text").valueKind()).isEqualTo("unknown");
        assertThat(p.declarations()).extracting(Declaration::candidacy).doesNotContain("candidate");
        assertThat(origin(state(r, "end").before(), 1).declarations()).isEmpty();
        assertThat(origin(state(r, "end").before(), 1).coverage()).isEqualTo("incomplete");
        assertThat(Json.write(declaration(p, "text").evidence())).contains("stringExpression", "true");
    }

    @Test
    void rawEnabledFlagsAndDisabledAncestorDoNotInventInheritance() {
        BpmDataFixture f = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "off", "payload", "false").literal("off", "false")
                .child("off", "on-child", "leaf", "true").literal("on-child", "true")
                .root("mapping", "absent", "payload", null).literal("absent", "true")
                .root("mapping", "invalid", "payload", " TRUE ").literal("invalid", "true");
        BpmDataReport r = analyze(f);
        ProcessData p = process(r);
        assertThat(declaration(p, "off").candidacy()).isEqualTo("disabled");
        assertThat(declaration(p, "on-child").candidacy()).isEqualTo("candidate");
        assertThat(declaration(p, "on-child").reasons()).isNotEmpty();
        for (String id : List.of("absent", "invalid")) {
            assertThat(declaration(p, id).reasons()).isNotEmpty();
            assertThat(declaration(p, id).candidacy()).isEqualTo("candidate");
        }
        assertThat(raw(declaration(p, "off").evidence(), "off").attributes()).containsEntry("enabled", "false");
        assertThat(raw(declaration(p, "absent").evidence(), "absent").attributes()).doesNotContainKey("enabled");
        assertThat(raw(declaration(p, "invalid").evidence(), "invalid").attributes()).containsEntry("enabled", " TRUE ");
        assertThat(origin(state(r, "end").before(), 1).declarations()).containsExactly(2, 3, 4);
        assertThat(origin(state(r, "end").before(), 1).coverage()).isEqualTo("incomplete");
    }

    @Test
    void missingWrongKindAndAmbiguousPartsRemainTerminalEvidence() {
        BpmDataFixture f = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .relation(M + "MappingContainsMappingParts", "missing", "mapping")
                .raw(R + "Literal", "wrong", "value='true'", "").relation(M + "MappingContainsMappingParts", "wrong", "mapping")
                .part("duplicate", "payload", "true").raw(M + "MappingPart", "duplicate", "enabled='false'", "")
                .relation(M + "MappingContainsMappingParts", "duplicate", "mapping");
        ProcessData p = process(analyze(f));
        assertThat(p.declarations()).hasSize(3);
        assertThat(declaration(p, "missing").part().resolution()).isEqualTo("missing");
        assertThat(declaration(p, "wrong").part().candidates()).hasSize(1);
        assertThat(declaration(p, "duplicate").part().candidates()).hasSize(2);
        assertThat(p.declarations()).allSatisfy(d -> {
            assertThat(d.candidacy()).isEqualTo("unknown");
            assertThat(d.reasons()).isNotEmpty();
        });
        for (BpmDataFixture broken : List.of(
                chain().owns("missing-map", "map", "output"),
                chain().raw(R + "Literal", "bad-map", "value='true'", "").owns("bad-map", "map", "output"),
                chain().mapping("amb-map", "map", "output").raw(M + "Mapping", "amb-map", "", ""))) {
            ProcessData result = process(analyze(broken));
            assertThat(result.mappings()).hasSize(1);
            assertThat(result.mappings().getFirst().reasons()).isNotEmpty();
            assertThat(result.coverage()).isEqualTo("incomplete");
        }
    }

    @Test
    void missingRootQNameNeverFallsBackToDescendantOrTypeNamespace() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .raw("XSD1.XsdElementDeclaration", "type", "name='payload' namespace='urn:not-target'", "")
                .relation(W + "DataContainerReferencesXsdElementDeclaration", "type", "object")
                .mapping("mapping", "map", "output").root("mapping", "root", null, "true")
                .child("root", "leaf", "payload", "true").literal("leaf", "true")
                .root("mapping", "valid", "payload", "true").literal("valid", "true")
                .root("mapping", "literal-null", "null", "true").literal("literal-null", "true"));
        ProcessData p = process(r);
        assertThat(declaration(p, "leaf").targetPath()).hasSize(2);
        assertThat(declaration(p, "leaf").targetPath().getFirst().state()).isEqualTo("missing");
        assertThat(declaration(p, "leaf").target().state()).isEqualTo("unknown");
        assertThat(declaration(p, "leaf").target().objects()).isEmpty();
        assertThat(declaration(p, "valid").target().state()).isEqualTo("lexical-candidates");
        assertThat(declaration(p, "valid").target().objects()).containsExactly(1);
        assertThat(declaration(p, "literal-null").targetPath().getFirst().state()).isEqualTo("resolved");
        assertThat(declaration(p, "literal-null").target().state()).isEqualTo("none-observed");
    }

    @Test
    void sharedMappingAndPartUsesRemainOwnerQualified() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .node("MappingActivity", "second", "pool").sequence("next", "end", "second", "pool")
                .mapping("shared", "map", "output").owns("shared", "second", "output")
                .root("shared", "part", "payload", "true").literal("part", "true"));
        ProcessData p = process(r);
        assertThat(p.mappings()).hasSize(2);
        assertThat(p.mappings()).extracting(MappingUse::rootOwner).doesNotHaveDuplicates();
        assertThat(p.declarations()).hasSize(2).extracting(Declaration::mapping).containsExactly(1, 2);
        assertThat(p.declarations()).extracting(d -> d.part().id()).containsExactly("part", "part");
        assertThat(origin(state(r, "map").after(), 1).declarations()).containsExactly(1);
        assertThat(origin(state(r, "second").before(), 1).declarations()).containsExactly(1);
        assertThat(origin(state(r, "second").after(), 1).declarations()).containsExactly(1, 2);
        assertThat(p.declarations().get(0).evidence().path()).isNotEqualTo(p.declarations().get(1).evidence().path());
    }

    @Test
    void mappingDiamondAndCycleRetainContextAndTerminate() {
        BpmDataFixture f = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "root", "payload", "true")
                .child("root", "left", "left", "true").child("root", "right", "right", "true")
                .child("left", "shared", "leaf", "true").relation(M + "MappingPartHasChildren", "shared", "right")
                .literal("shared", "true");
        ProcessData diamond = process(analyze(f));
        assertThat(declarations(diamond, "shared")).hasSize(2);
        assertThat(declarations(diamond, "shared")).extracting(d -> d.targetPath().size()).containsExactly(3, 3);
        assertThat(declarations(diamond, "shared").get(0).evidence().path())
                .isNotEqualTo(declarations(diamond, "shared").get(1).evidence().path());
        BpmDataFixture cyclic = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "root", "payload", "true").child("root", "leaf", "leaf", "true")
                .literal("leaf", "true").relation(M + "MappingPartHasChildren", "root", "leaf");
        BpmDataReport cycle = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(cyclic));
        assertThat(process(cycle).coverage()).isEqualTo("incomplete");
        assertThat(process(cycle).declarations()).anySatisfy(d -> {
            assertThat(d.candidacy()).isEqualTo("unknown");
            assertThat(d.reasons()).isNotEmpty();
        });
        assertThat(cycle.issues()).isNotEmpty();
        // More than the legacy reader's MAX_DEPTH=64: the supported declaration must survive.
        BpmDataFixture deep = chain().object("object", "payload", "pool").mapping("deep", "map", "output")
                .root("deep", "p0", "payload", "true");
        for (int i = 1; i <= 70; i++) deep.child("p" + (i - 1), "p" + i, "leaf", "true");
        deep.literal("p70", "true");
        Declaration leaf = declaration(process(assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(deep))), "p70");
        assertThat(leaf.targetPath()).hasSize(71);
        assertThat(leaf.candidacy()).isEqualTo("candidate");
    }

    @Test
    void visibleScopeCandidatesKeepShadowedNamesAndExcludeSiblings() {
        BpmDataReport r = analyze(nested().node("EmbeddedScope", "sibling", "pool").pool("passive", "false")
                .object("outer", "payload", "pool").object("inner", "payload", "host")
                .object("sibling-local", "payload", "sibling").object("passive-local", "payload", "passive")
                .write("child-map", "mapping", "part", "payload"));
        ProcessData p = process(r);
        assertThat(declaration(p, "part").target().state()).isEqualTo("lexical-candidates");
        assertThat(declaration(p, "part").target().objects()).containsExactly(1, 2);
        assertThat(state(r, "child-map").before()).extracting(OriginSet::object).containsExactly(1, 2);
        assertThat(p.objects().get(1).scopePath()).hasSize(2);
        assertThat(p.objects().get(3).scopeState()).isEqualTo("inactive");
    }

    @Test
    void unknownAncestryCannotProveNoCandidate() {
        BpmDataReport r = analyze(chain().object("orphan", "payload", null)
                .write("map", "mapping", "part", "payload"));
        ProcessData p = process(r);
        assertThat(p.objects().getFirst().scopeState()).isEqualTo("unknown");
        assertThat(declaration(p, "part").target().state()).isEqualTo("unknown");
        assertThat(declaration(p, "part").target().objects()).contains(1);
        assertThat(p.coverage()).isEqualTo("incomplete");
        BpmDataReport noMatch = analyze(chain().active("invalid").object("object", "other", "pool")
                .write("map", "mapping", "part", "payload"));
        assertThat(declaration(process(noMatch), "part").target().state()).isEqualTo("unknown");
        assertThat(state(noMatch, "map").reasons()).isNotEmpty();
    }

    @Test
    void ordinaryOutputReferencesNeverBindToSameNamedProcessObject() {
        BpmDataReport r = analyze(ordinary().object("object", "payload", "pool").initial("object", "initial", "seed", "payload")
                .mapping("mapping", "activity", "output").root("mapping", "part", "payload", "true").reference("part", "payload"));
        ProcessData p = process(r);
        Read read = readFor(p, "part");
        assertThat(mapping(p, "mapping").readDomain()).isEqualTo("external-output");
        assertThat(read.domain()).isEqualTo("external-output");
        assertThat(read.binding().state()).isEqualTo("external");
        assertThat(read.binding().objects()).isEmpty();
        assertThat(read.candidates()).isEmpty();
        assertThat(declaration(p, "part").candidacy()).isEqualTo("candidate");
    }

    @Test
    void supportedInputRoleAndArgumentTargetsDoNotWriteProcessObjects() {
        BpmDataFixture f = ordinary().object("object", "payload", "pool")
                .named("ProcessRole", "role", "role", "pool")
                .mapping("input", "activity", "input").root("input", "input-part", "payload", "true")
                .invocation("input-part", "call", "args", true).argument("args", "arg", "payload", "payload")
                .mapping("role-map", "role", "view").root("role-map", "role-part", "payload", "true").literal("role-part", "true")
                .write("activity", "later-output", "output-part", "payload");
        BpmDataReport r = analyze(f);
        ProcessData p = process(r);
        assertThat(mapping(p, "input").writeDomain()).isEqualTo("external-input");
        assertThat(mapping(p, "args").writeDomain()).isEqualTo("function-argument");
        assertThat(mapping(p, "role-map").writeDomain()).isEqualTo("role");
        for (String id : List.of("input-part", "arg", "role-part"))
            assertThat(declaration(p, id).candidacy()).isEqualTo("external");
        assertThat(readFor(p, "arg").binding().objects()).containsExactly(1);
        assertThat(origin(readFor(p, "arg").candidates(), 1).declarations()).isEmpty();
        assertThat(origin(state(r, "activity").after(), 1).declarations()).containsExactly(declaration(p, "output-part").ordinal());
    }

    @Test
    void mappingActivityReadsAndWritesProcessContext() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool").initial("object", "initial", "seed", "payload")
                .mapping("mapping", "map", "input").root("mapping", "part", "payload", "true").reference("part", "payload"));
        ProcessData p = process(r);
        assertThat(mapping(p, "mapping").role()).isEqualTo("mapping");
        assertThat(mapping(p, "mapping").readDomain()).isEqualTo("process-context");
        assertThat(mapping(p, "mapping").writeDomain()).isEqualTo("process-context");
        assertThat(readFor(p, "part").binding().state()).isEqualTo("lexical-candidates");
        assertThat(origin(readFor(p, "part").candidates(), 1).declarations()).containsExactly(declaration(p, "seed").ordinal());
        assertThat(origin(state(r, "end").before(), 1).declarations()).containsExactly(1, 2);
    }

    @Test
    void sharedFunctionArgumentsInheritSeparateReadDomains() {
        BpmDataReport r = analyze(ordinary().object("object", "payload", "pool")
                .mapping("input", "activity", "input").root("input", "in-part", "payload", "true")
                .invocation("in-part", "in-call", "args", true)
                .mapping("output", "activity", "output").root("output", "out-part", "payload", "true")
                .invocation("out-part", "out-call", "args", true).argument("args", "arg", "payload", "payload"));
        ProcessData p = process(r);
        List<MappingUse> args = p.mappings().stream().filter(m -> "argument".equals(m.role())).toList();
        assertThat(args).hasSize(2).extracting(MappingUse::readDomain).containsExactly("process-context", "external-output");
        assertThat(args).extracting(MappingUse::parent).containsExactly(1, 3);
        assertThat(args).extracting(MappingUse::parentDeclaration).containsExactly(1, 3);
        assertThat(p.reads()).extracting(Read::domain).containsExactly("process-context", "external-output");
        assertThat(p.reads().getFirst().binding().objects()).containsExactly(1);
        assertThat(p.reads().get(1).binding().objects()).isEmpty();
        assertThat(p.reads().get(1).candidates()).isEmpty();
    }

    @Test
    void invocationResultPathDoesNotCreateAProcessRead() {
        ProcessData p = process(analyze(chain().object("object", "payload", "pool")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true")
                .invocation("part", "call", null, true)
                .referenceStep("part-expr", "result", "payload", "urn:result")));
        assertThat(p.reads()).isEmpty();
        assertThat(declaration(p, "part").valueKind()).isEqualTo("expression");
        assertThat(raw(declaration(p, "part").evidence(), "result-q").attributes()).containsEntry("namespace", "urn:result");
    }

    @Test
    void conditionBranchesAndParentStepsKeepReadUncertainty() {
        BpmDataFixture f = condition().raw(R + "Expression", "negative", "", "")
                .relation(R + "ConditionHasNegativeExpression", "negative", "condition")
                .raw(R + "Parent", "parent", "", "").relation(R + "ExpressionHasSteps", "parent", "negative")
                .referenceStep("negative", "tail", "payload", "urn:tail");
        ProcessData p = process(analyze(f));
        assertThat(p.reads()).hasSize(2);
        assertThat(p.reads()).anySatisfy(read -> assertThat(read.binding().objects()).containsExactly(1));
        assertThat(p.reads()).anySatisfy(read -> {
            assertThat(read.binding().state()).isEqualTo("unknown");
            assertThat(read.reasons()).isNotEmpty();
            assertThat(read.evidence().elements()).extracting(Element::id).contains("parent", "tail", "tail-q");
        });
        assertThat(declaration(p, "part").valueKind()).isEqualTo("expression");
    }

    @Test
    void readEvidenceRetainsEveryQNamePayload() {
        BpmDataFixture fixture = chain().object("object", "payload", "pool")
                .mapping("mapping", "map", "input").root("mapping", "part", "payload", "true")
                .reference("part", "payload").referenceStep("part-expr", "item-ref", "item", "urn:item")
                .relation(R + "ReferencePointToXsdFeature", "feature", "part-ref");
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, fixture.xml());
        partitions.put(METADATA, partition(element("XSD1.XsdElementDeclaration", "feature",
                "name='other' namespace='urn:schema'", "")));
        ScaInventory inventory = PoXray.read(SyntheticBpmSca.write(dir.resolve("read-evidence.sca"), partitions));
        assertThat(evidence(inventory).links()).filteredOn(l -> l.association().equals(R + "ReferencePointToXsdFeature"))
                .hasSize(1).allSatisfy(l -> {
                    assertThat(l.first().candidates()).hasSize(1);
                    assertThat(l.first().candidates().getFirst().location().partition()).isEqualTo(METADATA);
                    assertThat(l.first().candidates().getFirst().included()).isFalse();
                });
        ProcessData p = process(BpmDataAnalysis.of(List.of(inventory), List.of("synthetic")));
        Read read = readFor(p, "part");
        assertThat(raw(read.evidence(), "part-ref-q").attributes()).containsEntry("name", "payload").containsEntry("namespace", "urn:read");
        assertThat(raw(read.evidence(), "item-ref-q").attributes()).containsEntry("name", "item").containsEntry("namespace", "urn:item");
        assertThat(read.evidence().links()).filteredOn(l -> l.association().equals(R + "ReferencePointToXsdFeature"))
                .allSatisfy(l -> assertThat(l.first().candidates().getFirst().included()).isFalse()).hasSize(1);
        assertThat(read.binding().objects()).containsExactly(1);
        assertThat(read.evidence().elements()).extracting(Element::location).doesNotHaveDuplicates();
        assertThat(read.evidence().elements()).extracting(e -> e.location().ordinal()).isSorted();
    }

    @Test
    void emptyMappingRetainsConfigurationAndUnknownMetadata() {
        ProcessData p = process(analyze(chain().raw(M + "Mapping", "empty", "custom='raw|mapping'", "")
                .owns("empty", "map", "output")
                .raw(M + "MappingPartConfiguration", "config", "enabled='false'", "")
                .raw(M + "NSPrefix", "prefix", "prefix='p' namespace='urn:prefix'", "")
                .raw("properties.PropertyBag", "bag", "marker='raw-bag'", "")
                .relation(M + "UnknownConfiguration", "config", "empty")
                .relation(M + "UnknownPrefix", "prefix", "config")
                .relation(M + "UnknownProperties", "bag", "prefix")
                .relation(M + "UnknownBacklink", "empty", "bag")
                .relation(M + "UnknownMissing", "missing", "empty")));
        assertThat(p.declarations()).isEmpty();
        MappingUse m = mapping(p, "empty");
        assertThat(raw(m.evidence(), "empty").attributes()).containsEntry("custom", "raw|mapping");
        assertThat(m.evidence().elements()).extracting(Element::id).contains("config", "prefix", "bag");
        assertThat(m.evidence().elements()).extracting(Element::location).doesNotHaveDuplicates();
        assertThat(m.evidence().links()).extracting(l -> l.first().id()).contains("missing");
        assertThat(m.reasons()).contains("configuration-semantics-unknown");
        assertThat(p.coverage()).isEqualTo("incomplete");
    }

    @Test
    void missingFunctionIdentityKeepsArgumentReadsAndUncertainDeclaration() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true")
                .invocation("part", "call", "args", false).argument("args", "arg", "parameter", "payload"));
        ProcessData p = process(r);
        assertThat(declaration(p, "part").valueKind()).isEqualTo("expression");
        assertThat(declaration(p, "part").candidacy()).isEqualTo("candidate");
        assertThat(declaration(p, "part").reasons()).contains("function-identity-unknown");
        assertThat(readFor(p, "arg").binding().objects()).containsExactly(1);
        assertThat(origin(state(r, "end").before(), 1).declarations()).containsExactly(declaration(p, "part").ordinal());
        assertThat(origin(state(r, "end").before(), 1).coverage()).isEqualTo("incomplete");
    }

    @Test
    void malformedConditionBranchDoesNotEraseIndependentRead() {
        BpmDataReport r = analyze(condition().relation(R + "ConditionHasNegativeExpression", "missing", "condition"));
        ProcessData p = process(r);
        assertThat(p.reads()).anySatisfy(read -> assertThat(read.binding().objects()).containsExactly(1));
        assertThat(declaration(p, "part").valueKind()).isEqualTo("expression");
        assertThat(declaration(p, "part").candidacy()).isEqualTo("candidate");
        assertThat(declaration(p, "part").reasons()).isNotEmpty();
        assertThat(origin(state(r, "end").before(), 1).coverage()).isEqualTo("incomplete");
    }

    @Test
    void unsupportedEventMappingDirectionsDoNotBecomeSupportedWrites() {
        for (String owner : List.of("start", "end", "boundary")) {
            BpmDataFixture f = chain().object("object", "payload", "pool")
                    .node("BoundaryEvent", "boundary", null).relation(W + "ActivityHasBoundaryEvent", "boundary", "map")
                    .mapping("mapping", owner, owner.equals("end") ? "output" : "input")
                    .root("mapping", "part", "payload", "true").literal("part", "true");
            BpmDataReport r = analyze(f);
            MappingUse m = mapping(process(r), "mapping");
            assertThat(m.role()).isEqualTo("unknown");
            assertThat(m.readDomain()).isEqualTo("unknown");
            assertThat(m.writeDomain()).isEqualTo("unknown");
            assertThat(declaration(process(r), "part").candidacy()).isEqualTo("unknown");
            assertThat(origin(state(r, owner).after(), 1).coverage()).isEqualTo("incomplete");
            assertThat(origin(state(r, owner).after(), 1).declarations()).isEmpty();
        }
    }

    @Test
    void joinAndCycleKeepAlternativeProducerSets() {
        BpmDataFixture f = new BpmDataFixture().node("StartEvent", "start", "pool")
                .node("MappingActivity", "left", "pool").node("MappingActivity", "right", "pool")
                .node("UncontrolledJoinGateway", "join", "pool").node("EndEvent", "end", "pool")
                .sequence("a", "start", "left", "pool").sequence("b", "start", "right", "pool")
                .sequence("c", "left", "join", "pool").sequence("d", "right", "join", "pool")
                .sequence("e", "join", "end", "pool").sequence("back", "join", "left", "pool")
                .object("object", "payload", "pool").write("left", "lm", "lp", "payload").write("right", "rm", "rp", "payload");
        BpmDataReport r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(f));
        assertThat(origin(state(r, "join").before(), 1).declarations()).containsExactly(1, 2);
        assertThat(origin(state(r, "left").before(), 1).declarations()).containsExactly(1, 2);
        assertThat(origin(state(r, "end").before(), 1).declarations()).containsExactly(1, 2);
        assertThat(origin(state(r, "start").before(), 1).declarations()).isEmpty();
    }

    @Test
    void disconnectedProducerAndFalseConditionHaveDifferentStructuralResults() {
        BpmDataFixture f = chain().object("object", "payload", "pool").node("MappingActivity", "island", "pool")
                .write("island", "island-map", "island-part", "payload").write("map", "connected-map", "part", "payload")
                .node("EndEvent", "false-end", "pool").edge("ConditionalSequenceConnector", "false-edge", "map", "false-end", "pool")
                .raw(R + "Expression", "false-expr", "", "").literalStep("false-expr", "false-lit", "false")
                .relation(W + "ConditionalSequenceConnectorContainsExpression", "false-expr", "false-edge");
        BpmDataReport r = analyze(f);
        assertThat(state(r, "island").connectivity()).isEqualTo("disconnected");
        assertThat(origin(state(r, "island").after(), 1).declarations()).isEmpty();
        assertThat(origin(state(r, "island").after(), 1).coverage()).isEqualTo("not-applicable");
        assertThat(state(r, "island").reasons()).contains("not-reached-in-modeled-graph");
        assertThat(origin(state(r, "false-end").before(), 1).declarations()).containsExactly(declaration(process(r), "part").ordinal());
        assertThat(state(r, "false-end").connectivity()).isEqualTo("connected");
    }

    @Test
    void nestedInitialsAndChildWritesDoNotLeakAcrossScopes() {
        BpmDataReport r = analyze(nested().object("outer", "payload", "pool").object("inner", "local", "host")
                .initial("outer", "outer-initial", "outer-seed", "payload")
                .initial("inner", "inner-initial", "inner-seed", "local")
                .write("child-map", "child-output", "child-write", "payload"));
        assertThat(state(r, "start").before()).extracting(OriginSet::object).containsExactly(1);
        assertThat(origin(state(r, "start").before(), 1).declarations()).containsExactly(1);
        assertThat(origin(state(r, "child-start").before(), 1).declarations()).containsExactly(1);
        assertThat(origin(state(r, "child-start").before(), 2).declarations()).containsExactly(2);
        assertThat(origin(state(r, "child-end").before(), 1).declarations()).containsExactly(1, 3);
        assertThat(state(r, "after").before()).extracting(OriginSet::object).containsExactly(1);
        assertThat(origin(state(r, "after").before(), 1).declarations()).containsExactly(1);
    }

    @Test
    void embeddedSuccessorsCarryUnmodeledChildEffects() {
        BpmDataReport r = analyze(nested().object("object", "payload", "pool")
                .initial("object", "initial", "seed", "payload"));
        for (String id : List.of("after", "end")) {
            OriginSet set = origin(state(r, id).before(), 1);
            assertThat(set.declarations()).containsExactly(1);
            assertThat(set.coverage()).isEqualTo("incomplete");
            assertThat(set.reasons()).contains("child-effects-unmodeled");
        }
    }

    @Test
    void boundaryTransferRetainsBeforeAndAfterWithTimingUncertainty() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .initial("object", "initial", "seed", "payload").write("map", "mapping", "part", "payload")
                .node("BoundaryEvent", "boundary", null).relation(W + "ActivityHasBoundaryEvent", "boundary", "map")
                .node("EndEvent", "caught", "pool").sequence("caught-edge", "boundary", "caught", "pool"));
        for (String id : List.of("boundary", "caught")) {
            OriginSet set = origin(state(r, id).before(), 1);
            assertThat(set.declarations()).containsExactly(1, 2);
            assertThat(set.coverage()).isEqualTo("incomplete");
            assertThat(set.reasons()).contains("boundary-timing-unknown");
        }
    }

    @Test
    void loopLocalReadNamesNeverBecomeCertainProcessBindings() {
        ProcessData p = process(analyze(nested().object("object", "payload", "pool")
                .raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='false' flowCondition='all'", "")
                .relation(W + "EmbeddedScopeContainsLoopCharcateristics", "loop", "host")
                .mapping("mapping", "child-map", "input").root("mapping", "part", "payload", "true").reference("part", "payload")));
        Read read = readFor(p, "part");
        assertThat(read.reasons()).contains("loop-local-context-unknown");
        assertThat(read.binding().state()).isEqualTo("unknown");
        assertThat(read.candidates()).allSatisfy(c -> assertThat(c.coverage()).isEqualTo("incomplete"));
    }

    @Test
    void sameStepOtherDeclarationRemainsAnUncertainReadAlternative() {
        BpmDataFixture f = chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "writer", "payload", "true").literal("writer", "true")
                .child("writer", "caller", "leaf", "true").invocation("caller", "call", "args", true)
                .argument("args", "arg", "parameter", "payload");
        BpmDataReport r = analyze(f);
        ProcessData p = process(r);
        OriginSet first = origin(readFor(p, "arg").candidates(), 1);
        // The ordinary parent part is independently evaluated; argument enclosure excludes only caller.
        assertThat(first.declarations()).containsExactly(declaration(p, "writer").ordinal());
        assertThat(first.coverage()).isEqualTo("incomplete");
        assertThat(first.reasons()).contains("intra-step-order-unknown");
        BpmDataReport cycled = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> analyze(f.sequence("cycle", "end", "map", "pool")));
        ProcessData cp = process(cycled);
        assertThat(origin(readFor(cp, "arg").candidates(), 1).declarations())
                .containsExactly(declaration(cp, "writer").ordinal(), declaration(cp, "caller").ordinal());
        assertThat(origin(state(cycled, "map").before(), 1).declarations())
                .containsExactly(declaration(cp, "writer").ordinal(), declaration(cp, "caller").ordinal());
    }

    @Test
    void missingRootPartPropagatesOpaqueWriteWithoutProducerId() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .initial("object", "initial", "seed", "payload")
                .mapping("mapping", "map", "output").relation(M + "MappingContainsMappingParts", "missing", "mapping"));
        assertThat(origin(state(r, "map").before(), 1).coverage()).isEqualTo("complete");
        for (OriginSet set : List.of(origin(state(r, "map").after(), 1), origin(state(r, "end").before(), 1))) {
            assertThat(set.declarations()).containsExactly(1);
            assertThat(set.coverage()).isEqualTo("incomplete");
            assertThat(set.reasons()).contains("opaque-write");
        }
    }

    @Test
    void unresolvedOwnerMakesPotentialEffectsProcessWide() {
        BpmDataReport r = analyze(chain().object("object", "payload", "pool")
                .mapping("mapping", "missing-owner", "output").root("mapping", "part", "payload", "true").literal("part", "true"));
        assertThat(mapping(process(r), "mapping").rootOwner()).isNull();
        assertThat(mapping(process(r), "mapping").role()).isEqualTo("unknown");
        for (StepState step : process(r).steps()) {
            assertThat(origin(step.before(), 1).coverage()).isEqualTo("incomplete");
            assertThat(origin(step.before(), 1).reasons()).isNotEmpty();
        }
    }

    @Test
    void disconnectedAndUnknownStatesNeverBecomeEquivalentEmptyAvailability() {
        BpmDataFixture f = chain().object("object", "payload", "pool").node("MappingActivity", "island", "pool");
        BpmDataReport disconnected = analyze(f);
        BpmDataReport unknown = analyze(f.active("invalid"));
        assertThat(state(disconnected, "island").connectivity()).isEqualTo("disconnected");
        assertThat(origin(state(disconnected, "island").before(), 1).coverage()).isEqualTo("not-applicable");
        assertThat(state(unknown, "island").connectivity()).isEqualTo("unknown");
        assertThat(state(unknown, "island").reasons()).isNotEmpty();
        assertThat(state(unknown, "island").before()).allSatisfy(s -> assertThat(s.coverage()).isEqualTo("incomplete"));
        assertThat(process(unknown).coverage()).isEqualTo("incomplete");
    }

    @Test
    void orphanSharedMetadataUsesComponentQualifiedDiagnostic() {
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, chain().xml());
        partitions.put("src/bpmn/second.bpmn", chain().xml());
        partitions.put(METADATA, partition(element(M + "Mapping", "orphan", "custom='uninterpreted'", "")));
        ScaInventory inventory = PoXray.read(SyntheticBpmSca.write(dir.resolve("shared.sca"), partitions));
        BpmDataReport r = BpmDataAnalysis.of(List.of(inventory), List.of("synthetic"));
        assertThat(r.processes()).hasSize(2).allSatisfy(p -> {
            assertThat(p.mappings()).isEmpty();
            assertThat(p.coverage()).isEqualTo("incomplete");
            assertThat(p.reasons()).contains("unattributed-component-data");
        });
        assertThat(r.issues()).filteredOn(i -> "BPM_DATA_UNATTRIBUTED_SOURCE".equals(i.code())).hasSize(1).allSatisfy(i -> {
            assertThat(i.message()).contains(ARCHIVE, METADATA, "1", "element", inventory.bpm().getFirst().dc());
            assertThat(i.message()).containsIgnoringCase("input").containsIgnoringCase("component");
        });
    }

    @Test
    void duplicateInputsAndProcessIdsNeverCrossBindOccurrences() {
        ScaInventory written = read(chain().object("object", "payload", "pool").write("map", "mapping", "part", "payload"));
        ScaInventory empty = read(chain().object("object", "payload", "pool"));
        ScaInventory combined = withComponents(empty, List.of(empty.bpm().getFirst(), written.bpm().getFirst()));
        BpmDataReport r = BpmDataAnalysis.of(List.of(combined, written), List.of("same", "same"));
        assertThat(r.processes()).extracting(ProcessData::key).containsExactly(
                new BpmPathReport.ProcessKey(1, 1, 1), new BpmPathReport.ProcessKey(1, 2, 1), new BpmPathReport.ProcessKey(2, 1, 1));
        assertThat(r.processes().getFirst().declarations()).isEmpty();
        assertThat(r.processes().get(1).declarations()).hasSize(1);
        assertThat(r.processes().get(2).declarations()).hasSize(1);
        assertThat(r.processes().getFirst().steps()).allSatisfy(s -> assertThat(origin(s.after(), 1).declarations()).isEmpty());
        assertThat(r.processes().get(1).steps().getLast().after().getFirst().declarations()).containsExactly(1);
        assertThat(r.processes().get(2).steps().getLast().after().getFirst().declarations()).containsExactly(1);
    }

    @Test
    void legacyUnsupportedEvidenceKeepsUnknownStates() {
        ScaInventory original = read(chain().object("object", "payload", "pool").write("map", "mapping", "part", "payload"));
        BpmSourceEvidence e = evidence(original);
        for (BpmSourceEvidence unavailable : Arrays.asList(null,
                new BpmSourceEvidence(2, e.selectionVersion(), e.indexedPartitions(), e.processPartitions(), e.elements(), e.links()),
                new BpmSourceEvidence(1, "future", e.indexedPartitions(), e.processPartitions(), e.elements(), e.links()))) {
            BpmDataReport r = BpmDataAnalysis.of(List.of(withEvidence(original, unavailable)), List.of("synthetic"));
            ProcessData p = process(r);
            assertThat(p.coverage()).isEqualTo("unavailable");
            assertThat(p.objects()).isEmpty();
            assertThat(p.mappings()).isEmpty();
            assertThat(p.declarations()).isEmpty();
            assertThat(p.reads()).isEmpty();
            assertThat(p.steps()).hasSize(3).allSatisfy(s -> {
                assertThat(s.connectivity()).isEqualTo("unknown");
                assertThat(s.reasons()).isNotEmpty();
                assertThat(s.before()).isEmpty();
                assertThat(s.after()).isEmpty();
            });
            assertThat(r.issues()).containsAll(original.issues());
        }
    }

    @Test
    void malformedDirectExpressionsAndAmbiguousTargetNamesNeverSelectFirstCandidate() {
        for (String defect : List.of("missing-step", "wrong-step", "missing-expression", "duplicate-expression", "wrong-expression")) {
            BpmDataFixture f = chain().object("object", "payload", "pool")
                    .mapping("mapping", "map", "output").root("mapping", "part", "payload", "true");
            switch (defect) {
                case "missing-step" -> f.expression("part", "expr").relation(R + "ExpressionHasSteps", "absent", "expr");
                case "wrong-step" -> f.expression("part", "expr").raw(M + "NSPrefix", "wrong", "", "")
                        .relation(R + "ExpressionHasSteps", "wrong", "expr");
                case "missing-expression" -> f.relation(M + "MappingPartContainsExpression", "absent", "part");
                case "duplicate-expression" -> f.literal("part", "true")
                        .relation(M + "MappingPartContainsExpression", "part-expr", "part");
                case "wrong-expression" -> f.raw(R + "Literal", "wrong", "value='true'", "")
                        .relation(M + "MappingPartContainsExpression", "wrong", "part");
                default -> throw new AssertionError(defect);
            }
            BpmDataReport r = analyze(f);
            Declaration d = declaration(process(r), "part");
            assertThat(d.valueKind()).as(defect).isEqualTo("unknown");
            assertThat(d.candidacy()).as(defect).isEqualTo("unknown");
            assertThat(d.reasons()).as(defect).isNotEmpty();
            assertThat(origin(state(r, "end").before(), 1).declarations()).as(defect).isEmpty();
            assertThat(origin(state(r, "end").before(), 1).coverage()).as(defect).isEqualTo("incomplete");
        }
        for (boolean duplicateOccurrence : List.of(false, true)) {
            BpmDataFixture f = chain().object("object", "payload", "pool").write("map", "mapping", "part", "payload");
            if (duplicateOccurrence) f.qname("part-q", "other", "urn:other");
            else f.qname("other-q", "other", "urn:other").relation(M + "MappingPartHasQName", "other-q", "part");
            Declaration d = declaration(process(analyze(f)), "part");
            assertThat(d.valueKind()).isEqualTo("expression");
            assertThat(d.targetPath().getFirst().state()).isEqualTo("ambiguous");
            assertThat(d.targetPath().getFirst().candidates()).hasSize(2);
            assertThat(d.target().state()).isEqualTo("unknown");
            assertThat(d.candidacy()).isEqualTo("unknown");
        }
    }

    @Test
    void unresolvedAndAmbiguousReadRootsNeverUseFeatureNames() {
        for (String defect : List.of("feature-only", "ambiguous", "wrong-kind")) {
            BpmDataFixture f = chain().object("object", "payload", "pool")
                    .mapping("mapping", "map", "input").root("mapping", "part", "payload", "true")
                    .expression("part", "expr").referenceStep("expr", "ref", null, null)
                    .raw("XSD1.XsdElementDeclaration", "feature", "name='payload' namespace='urn:feature'", "")
                    .relation(R + "ReferencePointToXsdFeature", "feature", "ref");
            if (defect.equals("ambiguous")) f.qname("q1", "payload", "urn:first").qname("q2", "other", "urn:second")
                    .relation(R + "ReferenceHasQName", "q1", "ref").relation(R + "ReferenceHasQName", "q2", "ref");
            if (defect.equals("wrong-kind")) f.relation(R + "ReferenceHasQName", "feature", "ref");
            ProcessData p = process(analyze(f));
            Read read = readFor(p, "part");
            assertThat(read.binding().state()).as(defect).isEqualTo("unknown");
            assertThat(read.reasons()).as(defect).isNotEmpty();
            assertThat(declaration(p, "part").valueKind()).isEqualTo("expression");
            assertThat(declaration(p, "part").candidacy()).isEqualTo("candidate");
            if (defect.equals("ambiguous"))
                assertThat(read.evidence().elements()).extracting(Element::id).contains("q1", "q2");
        }
    }

    @Test
    void unsupportedEmbeddedMappingsAndLocalOrphansRemainVisible() {
        BpmDataReport r = analyze(nested().object("object", "payload", "pool")
                .mapping("embedded", "host", "input").root("embedded", "part", "payload", "true").literal("part", "true")
                .raw(M + "Mapping", "orphan-map", "marker='retained'", "")
                .part("orphan-part", "payload", "true").literal("orphan-part", "true"));
        ProcessData p = process(r);
        assertThat(mapping(p, "embedded").role()).isEqualTo("unsupported-embedded");
        assertThat(mapping(p, "embedded").writeDomain()).isEqualTo("unknown");
        assertThat(declaration(p, "part").candidacy()).isEqualTo("unknown");
        assertThat(mapping(p, "orphan-map").ownership()).isNull();
        assertThat(mapping(p, "orphan-map").reasons()).isNotEmpty();
        assertThat(declaration(p, "orphan-part").candidacy()).isEqualTo("unknown");
        assertThat(p.coverage()).isEqualTo("incomplete");
    }

    private ScaInventory read(BpmDataFixture f) { return f.read(dir.resolve("data-" + (++files) + ".sca")); }
    private BpmDataReport analyze(BpmDataFixture f) { return BpmDataAnalysis.of(List.of(read(f)), List.of("synthetic")); }
    static ProcessData process(BpmDataReport r) { return r.processes().getFirst(); }
    static Declaration declaration(ProcessData p, String part) {
        List<Declaration> matches = declarations(p, part);
        assertThat(matches).as("declaration for " + part).hasSize(1);
        return matches.getFirst();
    }
    static List<Declaration> declarations(ProcessData p, String part) {
        return p.declarations().stream().filter(d -> d.part() != null && part.equals(d.part().id())).toList();
    }
    static MappingUse mapping(ProcessData p, String id) {
        return p.mappings().stream().filter(m -> m.mapping() != null && id.equals(m.mapping().id())).findFirst().orElseThrow();
    }
    static Read readFor(ProcessData p, String part) {
        int ordinal = declaration(p, part).ordinal();
        List<Read> reads = p.reads().stream().filter(r -> r.declaration() == ordinal).toList();
        assertThat(reads).as("read of " + part).hasSize(1);
        return reads.getFirst();
    }
    static StepState state(BpmDataReport r, String id) {
        BpmPathReport.Step step = r.paths().processes().getFirst().steps().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow();
        StepState state = process(r).steps().stream().filter(s -> s.step() == step.ordinal()).findFirst().orElseThrow();
        assertThat(state.location()).isEqualTo(step.location());
        return state;
    }
    static OriginSet origin(List<OriginSet> sets, int object) {
        return sets.stream().filter(s -> s.object() == object).findFirst().orElseThrow();
    }
    static Element raw(Evidence evidence, String id) {
        return evidence.elements().stream().filter(e -> id.equals(e.id())).findFirst().orElseThrow();
    }
    private static BpmDataFixture ordinary() {
        return new BpmDataFixture().node("StartEvent", "start", "pool").node("AutomatedActivity", "activity", "pool")
                .node("EndEvent", "end", "pool").sequence("enter", "start", "activity", "pool")
                .sequence("leave", "activity", "end", "pool");
    }
    private static BpmDataFixture condition() {
        return chain().object("object", "payload", "pool").mapping("mapping", "map", "output")
                .root("mapping", "part", "payload", "true").expression("part", "expr")
                .raw(R + "Condition", "condition", "", "").relation(R + "ExpressionHasSteps", "condition", "expr")
                .raw(R + "Expression", "condition-expr", "", "").literalStep("condition-expr", "condition-literal", "false")
                .relation(R + "ConditionHasConditionExpression", "condition-expr", "condition")
                .raw(R + "Expression", "positive", "", "").referenceStep("positive", "positive-ref", "payload", "urn:positive")
                .relation(R + "ConditionHasPositiveExpression", "positive", "condition");
    }
}
