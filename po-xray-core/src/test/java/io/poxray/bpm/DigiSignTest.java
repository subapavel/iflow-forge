package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.PoXray;
import io.poxray.TestData;
import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.Flow;
import io.poxray.model.FunctionCall;
import io.poxray.sca.ScaArchive;
import io.poxray.model.Issue;
import io.poxray.model.Mapping;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Integration test against the DigiSign SCA (confidential, testdata/), validated against the NWDS BPMN 2.0 exports.
 * The JSON output is written to {@code target/testoutput/} for manual review (target/ is gitignored).
 */
class DigiSignTest {

    private static ScaInventory inventory;

    @BeforeAll
    static void read() {
        Path sca = TestData.require("EONBPMDIGI00_0.sca");
        inventory = PoXray.read(sca);
        Json.write(inventory, Path.of("target/testoutput/EONBPMDIGI00_0.json"));
    }

    @Test
    void customFunctionsKnowWhereTheyAreCalled() {
        var functions = inventory.bpm().getFirst().catalog().functions();
        java.util.function.Function<String, io.poxray.model.Function> fn =
                name -> functions.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();

        assertThat(fn.apply("createMetadata").usedBy()).extracting(u -> u.element().name() + ":" + u.part())
                .containsExactlyInAnyOrder("Create Envelope:inputMapping", "Create envelope recipient:inputMapping",
                        "File upload:outputMapping");
        assertThat(fn.apply("getDocumentsTagsIndexedDetail").usedBy()).singleElement().satisfies(u -> {
            assertThat(u.elementKind()).isEqualTo("boundaryEvent");
            assertThat(u.target()).isEqualTo("DO_ApplicationError/faultUrl");
        });
        assertThat(fn.apply("getRecipientIdIndex").usedBy()).singleElement().satisfies(u -> {
            assertThat(u.process()).isEqualTo("digisign_envelope_download_completed");
            assertThat(u.elementKind()).isEqualTo("flow");
            assertThat(u.part()).isEqualTo("condition");
            assertThat(u.element().name()).isEqualTo("Signer only? -> Download audit log? (Yes)");
        });
        assertThat(fn.apply("getRecipientIdByExtId").usedBy()).extracting(u -> u.element().name())
                .containsExactly("Create envelope tag");
    }

    @Test
    void readsComponentsAndProcesses() {
        assertThat(inventory.source().softwareComponent().name()).isEqualTo("EON_BPM_DIGISIGN");
        assertThat(inventory.developmentComponents()).extracting(dc -> dc.vendor() + "/" + dc.name())
                .containsExactlyInAnyOrder("eon.com/bpm/digisign", "eon.com/bpm/digisignfunctionseb",
                        "eon.com/bpm/digisignfunctionsea", "eon.com/applications/digisignvalidationeb",
                        "eon.com/applications/digisignvalidationea");
        assertThat(inventory.bpm()).singleElement().extracting(BpmComponent::dc).isEqualTo("eon.com/bpm/digisign");
        assertThat(bpm().processes()).extracting(Process::name)
                .containsExactly("digisign_envelope", "digisign_envelope_download_completed");
    }

    @ParameterizedTest
    @CsvSource({"digisign_envelope, 56", "digisign_envelope_download_completed, 29"})
    void graphMatchesBpmn2Export(String processName, int expectedFlows) {
        Bpmn2Export export = Bpmn2Export.read(TestData.require(processName + ".bpmn2"));
        Process process = process(processName);

        Set<Bpmn2Export.Edge> ours = process.flows().stream()
                .map(f -> new Bpmn2Export.Edge(f.source().ref(), f.target().ref())).collect(Collectors.toSet());
        assertThat(export.edges()).hasSize(expectedFlows);
        assertThat(ours).containsExactlyInAnyOrderElementsOf(export.edges());
        assertThat(process.flows()).hasSize(expectedFlows);
    }

    @ParameterizedTest
    @CsvSource({"digisign_envelope", "digisign_envelope_download_completed"})
    void nodesAndNamesMatchBpmn2Export(String processName) {
        Bpmn2Export export = Bpmn2Export.read(TestData.require(processName + ".bpmn2"));
        Map<String, Node> nodes = process(processName).nodes().stream()
                .collect(Collectors.toMap(Node::id, Function.identity()));

        for (String id : export.flowNodes()) {
            assertThat(nodes).as("node %s (%s)", id, export.names.get(id)).containsKey(id);
            assertThat(nodes.get(id).name()).isEqualTo(export.names.get(id));
        }
    }

    @ParameterizedTest
    @CsvSource({"digisign_envelope", "digisign_envelope_download_completed"})
    void conditionsMatchBpmn2Export(String processName) {
        Bpmn2Export export = Bpmn2Export.read(TestData.require(processName + ".bpmn2"));
        Map<String, Flow> flows = process(processName).flows().stream()
                .collect(Collectors.toMap(Flow::id, Function.identity()));

        assertThat(export.conditions).isNotEmpty();
        export.conditions.forEach((flowId, text) -> {
            Flow flow = flows.get(flowId);
            assertThat(flow).as("flow %s", flowId).isNotNull();
            assertThat(flow.condition()).as("condition of flow %s", flowId).isNotNull();
            assertThat(normalize(flow.condition().text())).isEqualTo(normalize(text));
        });
    }

    @Test
    void expressionTextsMatchTheTreeAndFunctionsResolve() {
        assertThat(issues("EXPRESSION_TEXT_MISMATCH")).isEmpty();
        // all function IDs resolve (DC functions + built-in catalog incl. principal functions of predefs/default)
        assertThat(issues("UNKNOWN_BUILTIN_FUNCTION")).isEmpty();
        assertThat(bpm().processes()).flatExtracting(Process::nodes)
                .flatExtracting(n -> java.util.stream.Stream.of(n.inputMapping(), n.outputMapping())
                        .filter(java.util.Objects::nonNull).flatMap(m -> m.assignments().stream())
                        .filter(a -> a.expr() != null).flatMap(a -> a.expr().functions().stream()).toList())
                .extracting(FunctionCall::source).doesNotContain("unknown");
        assertThat(inventory.issues())
                .filteredOn(i -> !i.severity().equals(Issue.INFO))
                .extracting(Issue::code)
                .containsOnly("SUSPICIOUS_MAPPING");
        assertThat(inventory.unmapped()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"digisign_envelope", "digisign_envelope_download_completed"})
    void conditionsRenderedFromTreeMatchBpmn2Export(String processName) {
        Bpmn2Export export = Bpmn2Export.read(TestData.require(processName + ".bpmn2"));
        ScaArchive sca = ScaArchive.open(TestData.require("EONBPMDIGI00_0.sca"));
        ScaArchive.DcSource dc = sca.developmentComponents().stream().filter(BpmReader::isBpm).findFirst().orElseThrow();
        Map<String, String> rendered = BpmReader.renderConditions(dc);

        // names and operands come from the tree + catalogs; only notation (infix/functional) and whitespace may differ
        export.conditions.forEach((flowId, text) -> {
            String tree = rendered.get(flowId);
            assertThat(tree).as("rendered condition of flow %s", flowId).isNotNull().doesNotContain("unknown:");
            assertThat(ExpressionReader.equivalent(text, tree)).as("export: %s%n tree: %s", text, tree).isTrue();
        });
    }

    @Test
    void catalogListsUsedBuiltinFunctionsWithSemantics() {
        assertThat(bpm().catalog().builtinFunctions()).extracting(io.poxray.model.BuiltinFunction::name)
                .contains("get", "isSet", "numeric-less-than", "string-equal", "concat");
        assertThat(bpm().catalog().builtinFunctions())
                .filteredOn(f -> f.name().equals("numeric-less-than")).singleElement()
                .satisfies(f -> {
                    assertThat(f.symbol()).isEqualTo("<");
                    assertThat(f.documentation()).isNotBlank();
                    assertThat(f.parameters()).extracting(io.poxray.model.Function.Parameter::type)
                            .containsExactly("xsd:long", "xsd:long");
                });
        assertThat(bpm().processes()).flatExtracting(Process::nodes)
                .filteredOn(n -> n.kind().equals("boundaryEvent") && "TechnicalError".equals(n.name())).isNotEmpty()
                .allSatisfy(n -> assertThat(n.trigger().builtin()).isEqualTo("TechnicalError"));
        assertThat(bpm().catalog().builtinEventDefinitions()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("TechnicalError");
            assertThat(d.isTechnical()).isTrue();
            assertThat(d.documentation()).isNotBlank();
        });
    }

    @Test
    void mappingsCarryAllPartFlags() {
        Node recipient = node("digisign_envelope", "Create envelope recipient");
        assertThat(recipient.inputMapping().assignments()).filteredOn(a -> a.expr() != null).hasSize(37);
        assertThat(recipient.outputMapping().assignments()).filteredOn(a -> a.expr() != null).singleElement()
                .satisfies(a -> {
                    assertThat(a.target()).isEqualTo("DO_Recipient");
                    assertThat(a.expr().text()).startsWith("concat(");
                });

        Node increment = node("digisign_envelope", "Increment index");
        assertThat(increment.outputMapping().assignments()).filteredOn(a -> a.expr() != null).singleElement()
                .satisfies(a -> {
                    assertThat(a.target()).isEqualTo("DO_RecipientsIndex");
                    assertThat(a.expr().functions()).singleElement().satisfies(f -> {
                        assertThat(f.name()).isEqualTo("numeric-add");
                        assertThat(f.arguments()).extracting(FunctionCall.Argument::text)
                                .containsExactly("DO_RecipientsIndex", "1");
                        assertThat(f.arguments()).allSatisfy(arg -> assertThat(arg.category()).isNotNull());
                    });
                });

        // every part and every function argument carries category / expander / transformer
        java.util.List<Mapping.Assignment> all = bpm().processes().stream().flatMap(p -> p.nodes().stream())
                .flatMap(n -> java.util.stream.Stream.of(n.inputMapping(), n.outputMapping()))
                .filter(java.util.Objects::nonNull).flatMap(m -> m.assignments().stream()).toList();
        assertThat(all).isNotEmpty().allSatisfy(a -> {
            assertThat(a.category()).isIn("set", "merge", "append");
            assertThat(a.expander()).isIn("currentNode", "moveCorresponding");
            assertThat(a.transformer()).isIn("getValue", "discardValue");
        });
        assertThat(all).anyMatch(a -> a.expander().equals("moveCorresponding"));
        assertThat(all).filteredOn(a -> a.expr() != null).flatExtracting(a -> a.expr().functions())
                .flatExtracting(FunctionCall::arguments)
                .anyMatch(arg -> "moveCorresponding".equals(arg.expander()))
                .allSatisfy(arg -> assertThat(arg.transformer()).isNotNull());
    }

    @Test
    void reportsUnusedServiceReferences() {
        // second service reference for EON_OI_GetEnvelopeAuditLog_BPM (WS, sappo_in) besides the used XI one,
        // and the SAP SampleTimer reference
        assertThat(issues("UNUSED_OBJECT")).extracting(Issue::message).contains(
                "Service reference 'EON_OI_GetEnvelopeAuditLog_BPM' is not used by any process",
                "Service reference 'SampleTimer' is not used by any process");
    }

    @Test
    void infixOperatorIsRenderedAsInEditor() {
        assertThat(bpm().processes()).flatExtracting(Process::flows)
                .extracting(Flow::condition).filteredOn(java.util.Objects::nonNull)
                .anySatisfy(c -> {
                    assertThat(c.text()).isEqualTo("DO_TagsIndex < DO_TagsCount");
                    assertThat(c.functions()).singleElement().satisfies(f -> {
                        assertThat(f.id()).isEqualTo("481F3B2E935578A21AC311DDCA830018717335EB");
                        assertThat(f.name()).isEqualTo("numeric-less-than");
                        assertThat(f.category()).isEqualTo("finite-integer");
                        assertThat(f.source()).isEqualTo("builtin");
                        assertThat(f.arguments()).extracting(FunctionCall.Argument::parameter, FunctionCall.Argument::text)
                                .containsExactly(tuple("left", "DO_TagsIndex"), tuple("right", "DO_TagsCount"));
                    });
                    assertThat(c.reads()).containsExactly("DO_TagsIndex", "DO_TagsCount");
                });
    }

    @Test
    void reportsKnownSuspiciousMapping() {
        // docs/HANDOFF.md §5: identificationNumber <- get(...)/function, same source as field 'function'
        assertThat(issues("SUSPICIOUS_MAPPING"))
                .anySatisfy(i -> {
                    assertThat(i.location().element().name()).isEqualTo("Create envelope recipient");
                    assertThat(i.message()).contains("identificationNumber").contains("function");
                });
    }

    @Test
    void readsCatalog() {
        BpmComponent.Catalog catalog = bpm().catalog();
        assertThat(catalog.functions()).extracting(io.poxray.model.Function::name).containsExactlyInAnyOrder(
                "createMetadata", "getDocumentsTagsIndexedDetail", "getRecipientIdByExtId", "getRecipientIdIndex");
        assertThat(catalog.functions()).allSatisfy(f -> {
            assertThat(f.kind()).isEqualTo("ejb");
            assertThat(f.ejb().application()).isEqualTo("eon.com/bpm~digisignfunctionsea");
        });
        assertThat(catalog.eventDefinitions()).extracting(e -> e.name() + ":" + e.kind()).containsExactlyInAnyOrder(
                "CreateEnvelopeInputMessage:message", "WebhookEnvelopeCompleted:message",
                "DigiSignApplicationEscalation:error", "DigiSignTechnicalEscacaltion:error");
        assertThat(catalog.interfaces()).hasSize(15);
        assertThat(catalog.serviceReferences()).isNotEmpty().allSatisfy(sr -> {
            assertThat(sr.iface()).isNotNull();
            assertThat(sr.serviceGroup()).isNotNull();
        });
        assertThat(catalog.dataTypes()).isNotEmpty();
    }

    @Test
    void automatedActivitiesHaveOperationAndMappings() {
        for (Process p : bpm().processes()) {
            assertThat(p.nodes()).filteredOn(n -> n.kind().equals("automatedActivity")).isNotEmpty()
                    .allSatisfy(n -> {
                        assertThat(n.operation()).as(n.name()).isNotNull();
                        assertThat(n.operation().iface()).as(n.name()).isNotNull();
                        assertThat(n.serviceReference()).as(n.name()).isNotNull();
                        assertThat(n.inputMapping()).as(n.name()).isNotNull();
                    });
            assertThat(p.nodes()).noneMatch(n -> n.kind().equals("unknown"));
        }
    }

    @Test
    void readsEjbModulesAndLinksBpmFunctions() {
        assertThat(inventory.ejb()).extracting(EjbModule::dc).containsExactlyInAnyOrder(
                "eon.com/bpm/digisignfunctionseb", "eon.com/applications/digisignvalidationeb");

        EjbModule functions = ejb("eon.com/bpm/digisignfunctionseb");
        assertThat(functions.ear()).isEqualTo("eon.com/bpm/digisignfunctionsea");
        assertThat(functions.application()).isEqualTo("eon.com/bpm~digisignfunctionsea");
        assertThat(functions.beans()).hasSize(4).allSatisfy(b -> {
            assertThat(b.roles()).containsExactly("bpmFunction");
            assertThat(b.usedBy()).singleElement().satisfies(u -> {
                assertThat(u.dc()).isEqualTo("eon.com/bpm/digisign");
                assertThat(u.function().name()).isEqualToIgnoringCase(b.name());
            });
            assertThat(b.interfaces().local()).containsExactly(b.className() + "Local");
            assertThat(b.source().path()).startsWith("SOURCEARCHIVES/eon.com~bpm~digisignfunctionseb~.dcsa!/ejbModule/");
        });

        EjbModule validation = ejb("eon.com/applications/digisignvalidationeb");
        assertThat(validation.beans()).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("ValidateCreateEnvelopeInput");
            assertThat(b.roles()).containsExactly("adapterModule");
            assertThat(b.jndiName()).isEqualTo("ValidateCreateEnvelopeInput");
            assertThat(b.moduleParameters())
                    .containsExactly("showAudit", "faultInterface", "faultNamespace", "faultService", "senderId");
            assertThat(b.interfaces().local()).containsExactly("com.sap.aii.af.lib.mp.module.ModuleLocal");
            assertThat(b.interfaces().remote()).containsExactly("com.sap.aii.af.lib.mp.module.ModuleRemote");
        });
        // class name typo in source is kept as is (docs/HANDOFF.md §5)
        assertThat(validation.classes()).extracting(EjbModule.JavaClass::className)
                .contains("com.eon.digisign.po.ValiadtionMessage");

        assertThat(issues("MISSING_SOURCE")).isEmpty();
        assertThat(issues("EJB_INTERFACE_MISMATCH")).isEmpty();
        assertThat(issues("DEPLOYED_SOURCE_DIFFERS")).isEmpty();
    }

    @Test
    void generatesDocumentationInBothFormats() throws java.io.IOException {
        String md = Documentation.render(inventory, DocFormat.MARKDOWN);
        String jira = Documentation.render(inventory, DocFormat.JIRA);
        java.nio.file.Files.writeString(Path.of("target/testoutput/EONBPMDIGI00_0.md"), md);
        java.nio.file.Files.writeString(Path.of("target/testoutput/EONBPMDIGI00_0.jira.txt"), jira);

        assertThat(md).startsWith("# Obsah SAP PO: EON\\_BPM\\_DIGISIGN")
                .contains("### Proces digisign\\_envelope", "```mermaid", "##### Create envelope recipient",
                        "**moveCorresponding** (automatické mapování stejnojmenných polí)",
                        "`DO_TagsIndex < DO_TagsCount`", "## Nálezy", "## Příloha: datové typy");
        assertThat(jira).startsWith("h1. Obsah SAP PO: EON\\_BPM\\_DIGISIGN")
                .contains("h3. Proces digisign\\_envelope", "h5. Create envelope recipient",
                        "*moveCorresponding* (automatické mapování stejnojmenných polí)", "||Cíl||Zdroj||Režim||")
                .doesNotContain("flowchart");
        assertTablesConsistent(md, "| ");
        assertTablesConsistent(jira, "|");

        String english = Documentation.render(inventory, DocFormat.MARKDOWN, java.util.Locale.ENGLISH);
        assertThat(english).contains("### Process digisign\\_envelope", "fields with equal names are mapped automatically");
    }

    /** Every row of a table has the same number of unescaped cell separators as the table's first line. */
    private static void assertTablesConsistent(String doc, String rowPrefix) {
        int expected = -1;
        for (String lineText : doc.split("\n")) {
            if (!lineText.startsWith(rowPrefix.substring(0, 1))) {
                expected = -1;
                continue;
            }
            int pipes = lineText.replace("\\\\", "").replaceAll("\\\\\\|", "").replace("||", "|").length()
                    - lineText.replace("\\\\", "").replaceAll("\\\\\\|", "").replace("||", "|").replace("|", "").length();
            if (expected < 0) {
                expected = pipes;
            } else {
                assertThat(pipes).as("cells in row: %s", lineText).isEqualTo(expected);
            }
        }
    }

    @Test
    void jsonIsValidAndOmitsEmptyValues() {
        String json = Json.write(inventory);
        var tree = Json.mapper().readTree(json);
        assertThat(tree.get("schemaVersion").asString()).isEqualTo("0.1");
        assertThat(json).doesNotContain(": null").doesNotContain("[ ]").doesNotContain("{ }");
        // declaration order, not alphabetical
        assertThat(json.indexOf("\"schemaVersion\"")).isLessThan(json.indexOf("\"generator\""));
    }

    private static EjbModule ejb(String dc) {
        return inventory.ejb().stream().filter(m -> m.dc().equals(dc)).findFirst().orElseThrow();
    }

    private static BpmComponent bpm() {
        return inventory.bpm().getFirst();
    }

    private static Node node(String process, String name) {
        return process(process).nodes().stream().filter(n -> name.equals(n.name())).findFirst().orElseThrow();
    }

    private static Process process(String name) {
        return bpm().processes().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    private static java.util.List<Issue> issues(String code) {
        return inventory.issues().stream().filter(i -> i.code().equals(code)).toList();
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "");
    }
}
