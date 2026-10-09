package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.PoXray;
import io.poxray.TestData;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.Flow;
import io.poxray.model.Issue;
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
        assertThat(issues("BUILTIN_FUNCTION_CONFLICT")).isEmpty();
        // the only unknown built-in: a zero-arg function in editor-generated role principals, whose editor text
        // is empty, so its name cannot be learned (reported, not guessed)
        assertThat(issues("UNKNOWN_BUILTIN_FUNCTION")).extracting(Issue::message)
                .allMatch(m -> m.contains("481F3B33967D93A01AC311DDCFD20018717335EB"));
        assertThat(inventory.issues())
                .filteredOn(i -> !i.severity().equals(Issue.INFO))
                .extracting(Issue::code)
                .containsOnly("SUSPICIOUS_MAPPING", "UNKNOWN_BUILTIN_FUNCTION");
        assertThat(inventory.unmapped()).isEmpty();
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
                    assertThat(c.functions()).containsExactly("numeric-less-than");
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
