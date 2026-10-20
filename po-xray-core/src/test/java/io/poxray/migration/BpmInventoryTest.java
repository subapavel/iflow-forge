package io.poxray.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmInventoryReport;
import io.poxray.model.BpmInventoryReport.EjbRow;
import io.poxray.model.BpmInventoryReport.ProcessRow;
import io.poxray.model.EjbModule;
import io.poxray.model.Issue;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/**
 * {@link BpmInventory} sizes the NW BPM processes and EJB modules of {@code po-xray export} outputs: steps per node
 * kind, human tasks by metaclass, metaclasses the reader does not interpret, beans per role, totals.
 */
class BpmInventoryTest {

    // ---------------------------------------------------------------- fixture builders (synthetic records)

    private static Node node(String id, String kind, String metaclass) {
        return new Node.Builder(id, kind).metaclass(metaclass).name("Node " + id).build();
    }

    private static Node node(String id, String kind) {
        return node(id, kind, null);
    }

    private static Process process(String id, String name, Node... nodes) {
        return new Process(id, name, null, null, "src/bpmn/" + name + ".bpmn", List.of(), List.of(), List.of(),
                List.of(), List.of(nodes), List.of(), List.of(), null);
    }

    private static EjbModule.Bean bean(String name, String... roles) {
        return new EjbModule.Bean(name, "com.example.synth." + name, "stateless", null, null, List.of(roles),
                List.of(), List.of(), null, null, null, null, List.of(), List.of(), List.of(), null, List.of(), null);
    }

    private static EjbModule ejb(String dc, EjbModule.Bean... beans) {
        return new EjbModule(dc, "synth/ear", "synth/ear~ejb", List.of(beans), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static ScaInventory sca(List<BpmComponent> bpm, List<EjbModule> ejb) {
        return new ScaInventory(ScaInventory.SCHEMA_VERSION,
                new ScaInventory.Generator("po-xray", "t", "2026-01-01T00:00:00Z"),
                new ScaInventory.ScaSource("SYNTH.sca", null, null), List.of(), bpm, ejb, List.of(), List.of());
    }

    /** One BPM DC {@code synth/bpm} with OrderApproval (7 nodes, 1 human task) and one EJB DC {@code synth/ejb}. */
    private static ScaInventory orderApproval() {
        Process p = process("P1", "OrderApproval",
                node("n1", "startEvent"),
                node("n2", "automatedActivity"),
                node("n3", "mappingActivity"),
                node("n4", "exclusiveGateway"),
                node("n5", "unknown", "Galaxy.Workflow.HumanActivity"),
                node("n6", "automatedActivity"),
                node("n7", "endEvent"));
        return sca(List.of(new BpmComponent("synth/bpm", List.of(p), null)),
                List.of(ejb("synth/ejb", bean("CheckCreditBean", "bpmFunction"), bean("BookOrderBean", "bpmFunction"),
                        bean("SynthModuleBean", "adapterModule"), bean("SynthHelperBean", "other"))));
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- processes

    @Test
    void processRowCountsStepsPerKindSortedByKind() {
        BpmInventoryReport report = BpmInventory.of(List.of(orderApproval()), List.of("SYNTH.json"));

        assertThat(report.schemaVersion()).isEqualTo("0.1");
        assertThat(report.generator().name()).isEqualTo("po-xray");
        ProcessRow row = report.processes().get(0);
        assertThat(report.processes()).hasSize(1);
        assertThat(row.file()).isEqualTo("SYNTH.json");
        assertThat(row.dc()).isEqualTo("synth/bpm");
        assertThat(row.id()).isEqualTo("P1");
        assertThat(row.name()).isEqualTo("OrderApproval");
        assertThat(row.source()).isEqualTo("src/bpmn/OrderApproval.bpmn");
        assertThat(row.steps()).containsExactly(entry("automatedActivity", 2), entry("endEvent", 1),
                entry("exclusiveGateway", 1), entry("mappingActivity", 1), entry("startEvent", 1),
                entry("unknown", 1));
        assertThat(row.stepsTotal()).isEqualTo(7);
    }

    @Test
    void humanTasksAreNodesWhoseMetaclassContainsHumanIgnoringCase() {
        Process p = process("P2", "Review",
                node("a", "unknown", "Galaxy.Workflow.HumanActivity"),
                node("b", "unknown", "Galaxy.Tasks.humanTaskReference"),
                node("c", "unknown", "Galaxy.Workflow.IntermediateTimerEvent"),
                node("d", "automatedActivity"));
        ProcessRow row = BpmInventory.of(List.of(sca(List.of(new BpmComponent("synth/bpm", List.of(p), null)),
                List.of())), List.of("r.json")).processes().get(0);

        assertThat(row.humanTasks()).isEqualTo(2);
    }

    @Test
    void unknownMetaclassesAreTheDistinctSortedMetaclassesOfUnknownNodes() {
        Process p = process("P3", "Mixed",
                node("a", "unknown", "Galaxy.Workflow.ParallelSplitGateway"),
                node("b", "unknown", "Galaxy.Workflow.HumanActivity"),
                node("c", "unknown", "Galaxy.Workflow.ParallelSplitGateway"),
                node("d", "automatedActivity", "Galaxy.Workflow.AutomatedActivity"));
        ProcessRow row = BpmInventory.of(List.of(sca(List.of(new BpmComponent("synth/bpm", List.of(p), null)),
                List.of())), List.of("m.json")).processes().get(0);

        assertThat(row.unknownMetaclasses())
                .containsExactly("Galaxy.Workflow.HumanActivity", "Galaxy.Workflow.ParallelSplitGateway");
        assertThat(row.steps()).containsExactly(entry("automatedActivity", 1), entry("unknown", 3));
    }

    // ---------------------------------------------------------------- EJB and totals

    @Test
    void beansByRoleListsSortedNamesAndABeanWithTwoRolesUnderBoth() {
        ScaInventory inv = sca(List.of(), List.of(ejb("synth/ejb", bean("ZetaBean", "bpmFunction"),
                bean("AlphaBean", "bpmFunction", "webService"), bean("ModBean", "adapterModule"))));

        EjbRow row = BpmInventory.of(List.of(inv), List.of("e.json")).ejb().get(0);

        assertThat(row.file()).isEqualTo("e.json");
        assertThat(row.dc()).isEqualTo("synth/ejb");
        assertThat(row.beansByRole()).containsExactly(entry("adapterModule", List.of("ModBean")),
                entry("bpmFunction", List.of("AlphaBean", "ZetaBean")), entry("webService", List.of("AlphaBean")));
    }

    @Test
    void totalsSumProcessesStepsHumanTasksAndBeansPerRoleOverAllInventories() {
        ScaInventory second = sca(List.of(new BpmComponent("synth/bpm2", List.of(
                process("P9", "Escalation", node("x", "startEvent"), node("y", "unknown", "HumanActivity"),
                        node("z", "endEvent"))), null)),
                List.of(ejb("synth/ejb2", bean("TwoRoleBean", "bpmFunction", "adapterModule"))));

        BpmInventoryReport report = BpmInventory.of(List.of(orderApproval(), second), List.of("a.json", "b.json"));

        assertThat(report.processes()).extracting(ProcessRow::file, ProcessRow::name)
                .containsExactly(tuple("a.json", "OrderApproval"),
                        tuple("b.json", "Escalation"));
        assertThat(report.ejb()).extracting(EjbRow::dc).containsExactly("synth/ejb", "synth/ejb2");
        assertThat(report.totals().processes()).isEqualTo(2);
        assertThat(report.totals().steps()).isEqualTo(10);
        assertThat(report.totals().humanTasks()).isEqualTo(2);
        assertThat(report.totals().beansByRole()).containsExactly(entry("adapterModule", 2),
                entry("bpmFunction", 3), entry("other", 1));
    }

    // ---------------------------------------------------------------- reading export directories

    @Test
    void readOfADirectoryReadsInventoriesSortedByNameAndReportsOtherJson(@TempDir Path dir) throws IOException {
        Json.write(orderApproval(), dir.resolve("b-SYNTH.json"));
        Json.write(sca(List.of(), List.of(ejb("synth/ejb0", bean("FirstBean", "other")))), dir.resolve("a-EJB.json"));
        write(dir.resolve("notes.json"), "{\"note\":\"not an inventory\"}");
        write(dir.resolve("broken.json"), "{ not json");
        Json.write(sca(null, null), dir.resolve("empty-sca.json"));
        write(dir.resolve("readme.txt"), "not json at all");
        Json.write(orderApproval(), dir.resolve("nested").resolve("c-SYNTH.json"));

        BpmInventoryReport report = BpmInventory.read(dir);

        assertThat(report.processes()).singleElement().satisfies(r -> {
            assertThat(r.file()).endsWith("b-SYNTH.json");
            assertThat(r.name()).isEqualTo("OrderApproval");
            assertThat(r.humanTasks()).isEqualTo(1);
        });
        assertThat(report.ejb()).extracting(EjbRow::dc).containsExactly("synth/ejb0", "synth/ejb");
        assertThat(report.ejb().get(0).file()).endsWith("a-EJB.json");
        assertThat(report.issues()).extracting(Issue::code).containsOnly("NOT_SCA_INVENTORY");
        assertThat(report.issues()).hasSize(3);
        assertThat(report.issues()).anySatisfy(i -> assertThat(i.message()).contains("notes.json"));
        assertThat(report.issues()).anySatisfy(i -> assertThat(i.message()).contains("broken.json"));
        assertThat(report.issues()).anySatisfy(i -> assertThat(i.message()).contains("empty-sca.json"));
        assertThat(report.issues()).noneSatisfy(i -> assertThat(i.message()).contains("readme.txt"));
        assertThat(report.totals().processes()).isEqualTo(1);
    }

    @Test
    void readOfAFileReadsThatOneFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("SYNTH.json");
        Json.write(orderApproval(), file);
        Json.write(orderApproval(), dir.resolve("OTHER.json"));

        BpmInventoryReport report = BpmInventory.read(file);

        assertThat(report.processes()).singleElement().satisfies(r -> assertThat(r.file()).endsWith("SYNTH.json"));
        assertThat(report.totals().processes()).isEqualTo(1);
        assertThat(report.totals().beansByRole()).containsExactly(entry("adapterModule", 1),
                entry("bpmFunction", 2), entry("other", 1));
        assertThat(report.issues()).isNullOrEmpty();
    }

    @Test
    void readOfAnEmptyDirectoryReturnsAReportWithZeroProcesses(@TempDir Path dir) throws IOException {
        BpmInventoryReport report = BpmInventory.read(dir);

        assertThat(report.processes()).isNullOrEmpty();
        assertThat(report.ejb()).isNullOrEmpty();
        assertThat(report.totals().processes()).isZero();
        assertThat(report.totals().steps()).isZero();
        assertThat(report.totals().humanTasks()).isZero();
    }

    @Test
    void readOfAMissingPathFailsWithAnIoExceptionNamingIt(@TempDir Path dir) {
        Path missing = dir.resolve("no-such-export");

        assertThatThrownBy(() -> BpmInventory.read(missing)).isInstanceOf(IOException.class)
                .hasMessageContaining("no-such-export");
    }

    // ---------------------------------------------------------------- Markdown and JSON

    @Test
    void markdownShowsProcessesKindsEjbRolesTotalsAndIssues(@TempDir Path dir) throws IOException {
        Json.write(orderApproval(), dir.resolve("SYNTH.json"));
        write(dir.resolve("notes.json"), "{\"note\":\"not an inventory\"}");

        String md = BpmInventory.markdown(BpmInventory.read(dir));
        List<String> lines = Arrays.asList(md.split("\n"));

        assertThat(lines).contains("# BPM inventory", "## Processes",
                "| dc | process | steps | human tasks | unknown metaclasses |", "### OrderApproval", "| kind | count |",
                "## EJB roles", "| dc | role | beans |", "## Totals", "## Issues");
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| synth/bpm |").contains("OrderApproval", "| 7 |",
                "| 1 |", "Galaxy.Workflow.HumanActivity"));
        assertThat(lines).contains("| automatedActivity | 2 |", "| mappingActivity | 1 |", "| unknown | 1 |");
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| synth/ejb | bpmFunction |")
                .contains("BookOrderBean", "CheckCreditBean"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| synth/ejb | adapterModule |")
                .contains("SynthModuleBean"));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("NOT_SCA_INVENTORY", "notes.json"));
        assertThat(md.indexOf("## Processes")).isLessThan(md.indexOf("### OrderApproval"));
        assertThat(md.indexOf("### OrderApproval")).isLessThan(md.indexOf("## EJB roles"));
        assertThat(md.indexOf("## EJB roles")).isLessThan(md.indexOf("## Totals"));
        assertThat(md.indexOf("## Totals")).isLessThan(md.indexOf("## Issues"));
    }

    @Test
    void markdownOmitsIssuesWhenThereAreNone() {
        String md = BpmInventory.markdown(BpmInventory.of(List.of(orderApproval()), List.of("SYNTH.json")));

        assertThat(md.split("\n")).contains("# BPM inventory", "## Totals").doesNotContain("## Issues");
    }

    @Test
    void jsonRoundTripsThroughTheMapper() {
        BpmInventoryReport report = BpmInventory.of(List.of(orderApproval()), List.of("SYNTH.json"));

        String json = Json.write(report);
        BpmInventoryReport back = Json.mapper().readValue(json, BpmInventoryReport.class);

        assertThat(Json.write(back)).isEqualTo(json);
        assertThat(back.totals().humanTasks()).isEqualTo(1);
        assertThat(back.processes().get(0).steps()).containsEntry("automatedActivity", 2);
    }

    @Test
    void jsonAlwaysCarriesAnEmptyIssuesListThatRoundTripsAsEmptyNotNull() {
        BpmInventoryReport report = BpmInventory.of(List.of(orderApproval()), List.of("SYNTH.json"));
        assertThat(report.issues()).isNotNull().isEmpty();

        String json = Json.write(report);
        assertThat(json).contains("\"issues\" : [ ]");
        JsonNode issues = Json.mapper().readTree(json).get("issues");
        assertThat(issues).isNotNull();
        assertThat(issues.isArray()).isTrue();
        assertThat(issues.size()).isZero();

        BpmInventoryReport back = Json.mapper().readValue(json, BpmInventoryReport.class);
        assertThat(back.issues()).isNotNull().isEmpty();
    }
}
