package io.poxray.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.DiscoveryReport.ComponentRow;
import io.poxray.model.DiscoveryReport.IcoRow;
import io.poxray.model.DiscoveryReport.InterfaceRow;
import io.poxray.model.DiscoveryReport.ProcessRow;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.Target;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.Ref;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ScenarioDiscovery} derives the ICOs around the NW BPM processes of {@code po-xray export} outputs: inbound
 * interfaces (start / intermediate usages) matched to ICOs whose receiver target interface is the interface, outbound
 * interfaces (outbound / notification usages) matched to ICOs whose sender interface is the interface, and the
 * communication components in the roles process, source and target.
 */
class ScenarioDiscoveryTest {

    /**
     * {@code Interface.Usage.process} holds the process NAME, not {@code Process.id}: ProcessReader passes
     * {@code processName = collab.name()} into {@code use(op, process, node, role)}. The fixture process therefore
     * has id {@code P1} and name {@code OrderApproval}, and the usages name {@code OrderApproval}.
     */
    private static final String PROCESS = "OrderApproval";

    private static final String DC = "synth/bpm";

    // ---------------------------------------------------------------- fixture builders (synthetic records)

    private static Interface.Usage use(String process, String node, String role) {
        return new Interface.Usage(process, new Ref(node, "Node " + node), role);
    }

    private static Interface iface(String name, String namespace, Interface.Usage... usages) {
        return new Interface("if-" + name, name, namespace, "rep/wsdl/" + name + ".services", List.of(),
                List.of(usages));
    }

    private static Process process(String id, String name) {
        return new Process(id, name, null, null, "src/bpmn/" + name + ".bpmn", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null);
    }

    private static BpmComponent.Catalog catalog(List<Interface> interfaces) {
        return new BpmComponent.Catalog(interfaces, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static BpmComponent component(String dc, List<Process> processes, BpmComponent.Catalog catalog) {
        return new BpmComponent(dc, processes, catalog);
    }

    private static ScaInventory sca(List<BpmComponent> bpm) {
        return new ScaInventory(ScaInventory.SCHEMA_VERSION,
                new ScaInventory.Generator("po-xray", "t", "2026-01-01T00:00:00Z"),
                new ScaInventory.ScaSource("SYNTH.sca", null, null), List.of(), bpm, null, List.of(), List.of());
    }

    private static List<Interface> orderApprovalInterfaces() {
        return List.of(
                iface("OrderIn", "urn:synth:orders", use(PROCESS, "n1", "start")),
                iface("ShipOut", "urn:synth:ship", use(PROCESS, "n2", "outbound")),
                iface("NotifyOut", "urn:synth:notify", use(PROCESS, "n3", "notification")));
    }

    /** DC synth/bpm, process P1 / OrderApproval: OrderIn (start), ShipOut (outbound), NotifyOut (notification). */
    private static ScaInventory orderApprovalExport() {
        return sca(List.of(component(DC, List.of(process("P1", PROCESS)), catalog(orderApprovalInterfaces()))));
    }

    private static ObjRef ifaceRef(String name, String namespace) {
        return new ObjRef("ifmmessif", name, namespace, "SYNTH_SWC 1.0", null, null);
    }

    private static Endpoint sender(String component, String name, String namespace) {
        return new Endpoint(null, component, ifaceRef(name, namespace), List.of(), null);
    }

    private static Receiver receiver(String component, String targetName, String targetNamespace) {
        return new Receiver(null, component, null, null,
                List.of(new InterfaceAssignment(null, null, null,
                        List.of(new Target(ifaceRef(targetName, targetNamespace), "1")), null)),
                List.of());
    }

    private static Ico ico(String oid, Endpoint sender, List<Receiver> receivers) {
        return new Ico(oid, null, null, sender, receivers, null, null, null, null);
    }

    private static IntegrationInventory icos(List<Ico> icos) {
        return new IntegrationInventory("0.1", new ScaInventory.Generator("po-xray", "t", "2026-01-01T00:00:00Z"),
                null, null, List.of(), icos, List.of(), List.of(), null, List.of());
    }

    /** ICO1 → the process, ICO2 out of it, ICO3 unrelated, ICO4 same interface name in another namespace. */
    private static List<Ico> specIcos() {
        return List.of(
                ico("ICO1", sender("SRC_SYS", "OrderIn", "urn:synth:orders"),
                        List.of(receiver("BPM_CC", "OrderIn", "urn:synth:orders"))),
                ico("ICO2", sender("BPM_CC", "ShipOut", "urn:synth:ship"),
                        List.of(receiver("WMS_SYS", "ShipIn", "urn:synth:wms"),
                                receiver("ERP_SYS", "ShipIn", "urn:synth:erp"))),
                ico("ICO3", sender("OTHER", "Other", "urn:x"), List.of(receiver("X", "Other", "urn:x"))),
                ico("ICO4", sender("SRC_SYS", "OrderIn", "urn:other"),
                        List.of(receiver("OTHER_CC", "OrderIn", "urn:other"))));
    }

    private static DiscoveryReport discover() {
        return ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"), icos(specIcos()),
                "icos-SYNTH.json");
    }

    private static List<InterfaceRow> all(ProcessRow row) {
        List<InterfaceRow> rows = new ArrayList<>(row.inbound());
        rows.addAll(row.outbound());
        return rows;
    }

    private static InterfaceRow named(List<InterfaceRow> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no interface row " + name + " in " + rows));
    }

    private static List<Issue> issues(DiscoveryReport report, String code) {
        return report.issues().stream().filter(i -> code.equals(i.code())).toList();
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- matching

    @Test
    void processRowListsInboundAndOutboundInterfacesWithTheirMatchingIcos() {
        DiscoveryReport report = discover();

        assertThat(report.schemaVersion()).isEqualTo("0.1");
        assertThat(report.generator().name()).isEqualTo("po-xray");
        assertThat(report.inputs()).containsExactly("SYNTH.json");
        assertThat(report.icosInput()).isEqualTo("icos-SYNTH.json");
        assertThat(report.processes()).hasSize(1);
        ProcessRow row = report.processes().get(0);
        assertThat(row.file()).isEqualTo("SYNTH.json");
        assertThat(row.dc()).isEqualTo(DC);
        assertThat(row.id()).isEqualTo("P1");
        assertThat(row.name()).isEqualTo(PROCESS);
        assertThat(row.processComponents()).containsExactly("BPM_CC");
        assertThat(row.inbound()).containsExactly(new InterfaceRow("OrderIn", "urn:synth:orders", "start",
                List.of(new IcoRow("ICO1", null, "SRC_SYS", "OrderIn", "urn:synth:orders", List.of("BPM_CC")))));
        assertThat(row.outbound()).containsExactlyInAnyOrder(
                new InterfaceRow("ShipOut", "urn:synth:ship", "outbound",
                        List.of(new IcoRow("ICO2", null, "BPM_CC", "ShipOut", "urn:synth:ship",
                                List.of("ERP_SYS", "WMS_SYS")))),
                new InterfaceRow("NotifyOut", "urn:synth:notify", "notification", List.of()));
    }

    @Test
    void componentsCarrySortedRolesAndOnlyComponentsOfMatchedIcos() {
        DiscoveryReport report = discover();

        assertThat(report.components()).containsExactly(
                new ComponentRow("BPM_CC", List.of("process")),
                new ComponentRow("ERP_SYS", List.of("target")),
                new ComponentRow("SRC_SYS", List.of("source")),
                new ComponentRow("WMS_SYS", List.of("target")));
        assertThat(report.components()).extracting(ComponentRow::name).doesNotContain("OTHER", "X", "OTHER_CC");
    }

    @Test
    void matchingRequiresExactCaseSensitiveNameAndNamespace() {
        List<Ico> list = new ArrayList<>(specIcos());
        list.add(ico("ICO5", sender("SRC_SYS", "OrderIn", "URN:SYNTH:ORDERS"),
                List.of(receiver("UPPER_CC", "OrderIn", "URN:SYNTH:ORDERS"))));
        list.add(ico("ICO6", sender("SRC_SYS", "orderin", "urn:synth:orders"),
                List.of(receiver("LOWER_CC", "orderin", "urn:synth:orders"))));
        list.add(ico("ICO7", sender("BPM_CC", "ShipOut", "urn:synth:ship2"),
                List.of(receiver("SHIP2_SYS", "ShipIn", "urn:synth:wms"))));

        DiscoveryReport report = ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"),
                icos(list), "icos.json");

        ProcessRow row = report.processes().get(0);
        assertThat(named(row.inbound(), "OrderIn").icos()).extracting(IcoRow::oid).containsExactly("ICO1");
        assertThat(named(row.outbound(), "ShipOut").icos()).extracting(IcoRow::oid).containsExactly("ICO2");
        assertThat(all(row)).flatExtracting(InterfaceRow::icos).extracting(IcoRow::oid)
                .doesNotContain("ICO3", "ICO4", "ICO5", "ICO6", "ICO7");
        assertThat(row.processComponents()).containsExactly("BPM_CC");
        assertThat(report.components()).extracting(ComponentRow::name)
                .doesNotContain("OTHER_CC", "UPPER_CC", "LOWER_CC", "SHIP2_SYS");
    }

    @Test
    void icosAreSortedByOidAndReceiverComponentsRolesAndProcessComponentsAreDistinctAndSorted() {
        List<Ico> list = List.of(
                ico("ICO2", sender("BPM_CC", "ShipOut", "urn:synth:ship"),
                        List.of(receiver("WMS_SYS", "ShipIn", "urn:synth:wms"),
                                receiver("SRC_SYS", "ShipIn", "urn:synth:src"),
                                receiver("WMS_SYS", "ShipIn2", "urn:synth:wms"))),
                ico("B-ICO", sender("SRC_SYS", "OrderIn", "urn:synth:orders"),
                        List.of(receiver("BPM_CC_2", "OrderIn", "urn:synth:orders"))),
                ico("A-ICO", sender("PORTAL", "OrderIn", "urn:synth:orders"),
                        List.of(receiver("BPM_CC", "OrderIn", "urn:synth:orders"))));

        DiscoveryReport report = ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"),
                icos(list), "icos.json");

        ProcessRow row = report.processes().get(0);
        assertThat(named(row.inbound(), "OrderIn").icos()).extracting(IcoRow::oid).containsExactly("A-ICO", "B-ICO");
        assertThat(named(row.outbound(), "ShipOut").icos()).singleElement()
                .satisfies(i -> assertThat(i.receiverComponents()).containsExactly("SRC_SYS", "WMS_SYS"));
        assertThat(row.processComponents()).containsExactly("BPM_CC", "BPM_CC_2");
        assertThat(report.components()).containsExactly(
                new ComponentRow("BPM_CC", List.of("process")),
                new ComponentRow("BPM_CC_2", List.of("process")),
                new ComponentRow("PORTAL", List.of("source")),
                new ComponentRow("SRC_SYS", List.of("source", "target")),
                new ComponentRow("WMS_SYS", List.of("target")));
    }

    @Test
    void interfaceUsedThroughSeveralNodesAppearsOncePerRole() {
        Interface orderIn = iface("OrderIn", "urn:synth:orders", use(PROCESS, "n1", "start"),
                use(PROCESS, "n4", "start"), use(PROCESS, "n5", "intermediate"));
        Interface shipOut = iface("ShipOut", "urn:synth:ship", use(PROCESS, "n2", "outbound"),
                use(PROCESS, "n6", "outbound"));
        ScaInventory export = sca(List.of(component(DC, List.of(process("P1", PROCESS)),
                catalog(List.of(orderIn, shipOut)))));

        DiscoveryReport report = ScenarioDiscovery.of(List.of(export), List.of("SYNTH.json"), icos(specIcos()),
                "icos.json");

        ProcessRow row = report.processes().get(0);
        assertThat(row.inbound()).extracting(InterfaceRow::name, InterfaceRow::role)
                .containsExactlyInAnyOrder(tuple("OrderIn", "start"),
                        tuple("OrderIn", "intermediate"));
        assertThat(row.inbound()).allSatisfy(r -> assertThat(r.icos()).extracting(IcoRow::oid)
                .containsExactly("ICO1"));
        assertThat(row.outbound()).extracting(InterfaceRow::name, InterfaceRow::role)
                .containsExactly(tuple("ShipOut", "outbound"));
    }

    // ---------------------------------------------------------------- issues

    @Test
    void interfaceWithoutIcoIsReportedOnceAndKeepsAnEmptyRow() {
        DiscoveryReport report = discover();

        assertThat(issues(report, "INTERFACE_WITHOUT_ICO")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Issue.WARNING);
            assertThat(i.message()).contains(PROCESS, "NotifyOut", "urn:synth:notify", "notification");
        });
        assertThat(named(report.processes().get(0).outbound(), "NotifyOut").icos()).isNotNull().isEmpty();
        assertThat(report.issues()).extracting(Issue::code).containsExactly("INTERFACE_WITHOUT_ICO");
    }

    @Test
    void processWithoutUsagesIsReportedAsProcessWithoutInterfacesAndGetsNoForeignInterfaces() {
        ScaInventory export = sca(List.of(component(DC, List.of(process("P1", PROCESS), process("P2", "Idle")),
                catalog(orderApprovalInterfaces()))));

        DiscoveryReport report = ScenarioDiscovery.of(List.of(export), List.of("SYNTH.json"), icos(specIcos()),
                "icos.json");

        assertThat(report.processes()).extracting(ProcessRow::name).containsExactly(PROCESS, "Idle");
        ProcessRow idle = report.processes().get(1);
        assertThat(idle.id()).isEqualTo("P2");
        assertThat(idle.inbound()).isEmpty();
        assertThat(idle.outbound()).isEmpty();
        assertThat(idle.processComponents()).isEmpty();
        assertThat(issues(report, "PROCESS_WITHOUT_INTERFACES")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Issue.WARNING);
            assertThat(i.message()).contains("Idle");
        });
        assertThat(report.processes().get(0).inbound()).extracting(InterfaceRow::name).containsExactly("OrderIn");
    }

    @Test
    void unknownUsageRoleIsReportedAndTheInterfaceIsNotListed() {
        List<Interface> interfaces = new ArrayList<>(orderApprovalInterfaces());
        interfaces.add(iface("EscalateOut", "urn:synth:escalate", use(PROCESS, "n7", "boundary")));
        ScaInventory export = sca(List.of(component(DC, List.of(process("P1", PROCESS)), catalog(interfaces))));

        DiscoveryReport report = ScenarioDiscovery.of(List.of(export), List.of("SYNTH.json"), icos(specIcos()),
                "icos.json");

        assertThat(issues(report, "UNKNOWN_INTERFACE_ROLE")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Issue.WARNING);
            assertThat(i.message()).contains(PROCESS, "EscalateOut", "boundary");
        });
        assertThat(all(report.processes().get(0))).extracting(InterfaceRow::name)
                .containsExactlyInAnyOrder("OrderIn", "ShipOut", "NotifyOut");
    }

    // ---------------------------------------------------------------- null-safety

    @Test
    void nullCatalogInterfacesUsedByOrBpmYieldNoRowsAndNoException() {
        Interface noUsages = new Interface("if-X", "NoUsages", "urn:synth:x", null, List.of(), null);
        ScaInventory export = sca(List.of(
                component("synth/nocatalog", List.of(process("P1", "NoCatalog")), null),
                component("synth/nointerfaces", List.of(process("P2", "NoInterfaces")),
                        new BpmComponent.Catalog(null, null, null, null, null, null, null)),
                component("synth/nousedby", List.of(process("P3", "NoUsedBy")), catalog(List.of(noUsages))),
                component("synth/noprocesses", null, catalog(orderApprovalInterfaces()))));
        ScaInventory noBpm = sca(null);

        List<ScaInventory> exports = List.of(export, noBpm);
        List<String> labels = List.of("SYNTH.json", "NOBPM.json");
        assertThatCode(() -> ScenarioDiscovery.of(exports, labels, icos(specIcos()), "icos.json"))
                .doesNotThrowAnyException();
        DiscoveryReport report = ScenarioDiscovery.of(exports, labels, icos(specIcos()), "icos.json");

        assertThat(report.processes()).extracting(ProcessRow::name)
                .containsExactly("NoCatalog", "NoInterfaces", "NoUsedBy");
        assertThat(report.processes()).allSatisfy(r -> {
            assertThat(r.file()).isEqualTo("SYNTH.json");
            assertThat(r.inbound()).isEmpty();
            assertThat(r.outbound()).isEmpty();
            assertThat(r.processComponents()).isEmpty();
        });
        assertThat(report.components()).isEmpty();
        assertThat(issues(report, "PROCESS_WITHOUT_INTERFACES")).hasSize(3);
    }

    @Test
    void nullIcoPartsMatchNothingAndNeverThrow() {
        Endpoint senderWithoutIface = new Endpoint(null, "SRC_SYS", null, List.of(), null);
        List<Ico> list = List.of(
                ico("N1", null, null),
                ico("N2", senderWithoutIface, List.of(new Receiver(null, "BPM_CC", null, null, null, null))),
                ico("N3", sender("SRC_SYS", "Unrelated", "urn:x"), List.of(new Receiver(null, "BPM_CC", null, null,
                        List.of(new InterfaceAssignment(null, null, null, null, null)), null))),
                ico("N4", sender("SRC_SYS", "Unrelated", "urn:x"), List.of(new Receiver(null, "BPM_CC", null, null,
                        List.of(new InterfaceAssignment(null, null, null, List.of(new Target(null, "1")), null)),
                        null))),
                ico("N5", sender("BPM_CC", "ShipOut", "urn:synth:ship"), null));
        IntegrationInventory inventory = icos(list);
        IntegrationInventory noIcos = icos(null);

        assertThatCode(() -> ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"), noIcos,
                "empty.json")).doesNotThrowAnyException();
        assertThatCode(() -> ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"), inventory,
                "icos.json")).doesNotThrowAnyException();
        DiscoveryReport report = ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"),
                inventory, "icos.json");

        ProcessRow row = report.processes().get(0);
        assertThat(named(row.inbound(), "OrderIn").icos()).isEmpty();
        assertThat(named(row.outbound(), "ShipOut").icos()).containsExactly(
                new IcoRow("N5", null, "BPM_CC", "ShipOut", "urn:synth:ship", List.of()));
        assertThat(named(row.outbound(), "NotifyOut").icos()).isEmpty();
        assertThat(report.components()).containsExactly(new ComponentRow("BPM_CC", List.of("process")));
    }

    @Test
    void withoutAnIcoInventoryEveryInterfaceIsWithoutIco() {
        DiscoveryReport report = ScenarioDiscovery.of(List.of(orderApprovalExport()), List.of("SYNTH.json"), null,
                null);

        assertThat(report.icosInput()).isNull();
        assertThat(all(report.processes().get(0))).allSatisfy(r -> assertThat(r.icos()).isEmpty());
        assertThat(issues(report, "INTERFACE_WITHOUT_ICO")).hasSize(3);
        assertThat(report.components()).isEmpty();
    }

    // ---------------------------------------------------------------- reading files

    @Test
    void readListsExportsLikeBpmInventoryAndMatchesAgainstTheIcoJson(@TempDir Path dir) throws IOException {
        Path exports = dir.resolve("export");
        Json.write(orderApprovalExport(), exports.resolve("SYNTH.json"));
        write(exports.resolve("notes.json"), "{\"note\":\"not an inventory\"}");
        write(exports.resolve("readme.txt"), "not json at all");
        Path icosJson = dir.resolve("icos-SYNTH.json");
        Json.write(icos(specIcos()), icosJson);

        DiscoveryReport report = ScenarioDiscovery.read(exports, icosJson);

        assertThat(report.inputs()).singleElement().satisfies(f -> assertThat(f).endsWith("SYNTH.json"));
        assertThat(report.icosInput()).endsWith("icos-SYNTH.json");
        assertThat(issues(report, "NOT_SCA_INVENTORY")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("notes.json"));
        assertThat(report.issues()).noneSatisfy(i -> assertThat(i.message()).contains("readme.txt"));
        ProcessRow row = report.processes().get(0);
        assertThat(report.processes()).hasSize(1);
        assertThat(row.file()).endsWith("SYNTH.json");
        assertThat(named(row.inbound(), "OrderIn").icos()).extracting(IcoRow::oid).containsExactly("ICO1");
        assertThat(named(row.outbound(), "ShipOut").icos()).singleElement()
                .satisfies(i -> assertThat(i.receiverComponents()).containsExactly("ERP_SYS", "WMS_SYS"));
        assertThat(row.processComponents()).containsExactly("BPM_CC");
        assertThat(report.components()).extracting(ComponentRow::name)
                .containsExactly("BPM_CC", "ERP_SYS", "SRC_SYS", "WMS_SYS");
    }

    @Test
    void readOfOneExportFileReadsThatFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("SYNTH.json");
        Json.write(orderApprovalExport(), file);
        Json.write(orderApprovalExport(), dir.resolve("OTHER.json"));
        Path icosJson = dir.resolve("icos").resolve("icos.json");
        Json.write(icos(specIcos()), icosJson);

        DiscoveryReport report = ScenarioDiscovery.read(file, icosJson);

        assertThat(report.inputs()).singleElement().satisfies(f -> assertThat(f).endsWith("SYNTH.json"));
        assertThat(report.processes()).singleElement().satisfies(r -> assertThat(r.file()).endsWith("SYNTH.json"));
    }

    @Test
    void readOfAnUnreadableIcoFileReportsNotIcoInventoryAndLeavesInterfacesWithoutIco(@TempDir Path dir)
            throws IOException {
        Path exports = dir.resolve("export");
        Json.write(orderApprovalExport(), exports.resolve("SYNTH.json"));
        Path icosJson = write(dir.resolve("icos-broken.json"), "{ not json");

        DiscoveryReport report = ScenarioDiscovery.read(exports, icosJson);

        assertThat(issues(report, "NOT_ICO_INVENTORY")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("icos-broken.json"));
        ProcessRow row = report.processes().get(0);
        assertThat(named(row.inbound(), "OrderIn").icos()).isEmpty();
        assertThat(all(row)).allSatisfy(r -> assertThat(r.icos()).isEmpty());
        assertThat(issues(report, "INTERFACE_WITHOUT_ICO")).hasSize(3);
    }

    // ---------------------------------------------------------------- JSON and Markdown

    @Test
    void jsonRoundTripEqualsTheReportAndAlwaysWritesIssues() {
        DiscoveryReport report = discover();

        String json = Json.write(report);
        DiscoveryReport back = Json.mapper().readValue(json, DiscoveryReport.class);

        assertThat(back).isEqualTo(report);
        assertThat(json).contains("\"issues\"", "INTERFACE_WITHOUT_ICO");

        DiscoveryReport clean = ScenarioDiscovery.of(List.of(sca(List.of(component(DC, List.of(process("P1", PROCESS)),
                catalog(orderApprovalInterfaces().subList(0, 2)))))), List.of("SYNTH.json"), icos(specIcos()),
                "icos.json");
        assertThat(clean.issues()).isNotNull().isEmpty();
        String cleanJson = Json.write(clean);
        assertThat(cleanJson).contains("\"issues\" : [ ]");
        assertThat(Json.mapper().readValue(cleanJson, DiscoveryReport.class).issues()).isNotNull().isEmpty();
    }

    @Test
    void markdownShowsProcessTableComponentsAndIssues() {
        String md = ScenarioDiscovery.markdown(discover());
        List<String> lines = Arrays.asList(md.split("\n"));

        assertThat(lines).contains("# Scenario discovery", "## Processes", "### OrderApproval",
                "| Role | Interface | Namespace | ICO | Sender | Receivers |", "## Components", "| Component | Roles |",
                "## Issues", "| Code | Message |");
        assertThat(lines).anySatisfy(l -> assertThat(l).contains(DC, "BPM_CC").doesNotStartWith("|"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("|").contains("OrderIn", "urn:synth:orders",
                "ICO1", "SRC_SYS", "BPM_CC"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("|").contains("ShipOut", "ICO2", "ERP_SYS",
                "WMS_SYS"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("|")
                .contains("NotifyOut", "urn:synth:notify", "—"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| BPM_CC |").contains("process"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| ERP_SYS |").contains("target"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| SRC_SYS |").contains("source"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("| WMS_SYS |").contains("target"));
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("|").contains("INTERFACE_WITHOUT_ICO", "NotifyOut"));
        assertThat(md.indexOf("## Processes")).isLessThan(md.indexOf("### OrderApproval"));
        assertThat(md.indexOf("### OrderApproval")).isLessThan(md.indexOf("## Components"));
        assertThat(md.indexOf("## Components")).isLessThan(md.indexOf("## Issues"));
    }

    @Test
    void markdownSaysNoneUnderIssuesWhenThereAreNone() {
        DiscoveryReport clean = ScenarioDiscovery.of(List.of(sca(List.of(component(DC, List.of(process("P1", PROCESS)),
                catalog(orderApprovalInterfaces().subList(0, 2)))))), List.of("SYNTH.json"), icos(specIcos()),
                "icos.json");

        String md = ScenarioDiscovery.markdown(clean);

        assertThat(md).contains("## Issues");
        String issuesSection = md.substring(md.indexOf("## Issues"));
        assertThat(issuesSection).contains("none").doesNotContain("| Code | Message |");
    }
}
