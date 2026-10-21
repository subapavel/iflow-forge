package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.poxray.model.AttributesReport;
import io.poxray.model.AttributesReport.Attribute;
import io.poxray.model.AttributesReport.ModuleRef;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.Esr;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;

/** {@code po-xray attributes <ico.json>... [--json]} on the committed synthetic fixture of the migration skill. */
@QuarkusMainTest
class AttributesCommandTest {

    private static final Path ICOS = Path.of("..", "skills", "sap-po-migration", "fixtures", "icos-SND_SYS.json");

    private static String value(AttributesReport report, int scenario, String name) {
        return report.scenarios().get(scenario).attributes().stream().filter(a -> a.name().equals(name))
                .map(Attribute::value).findFirst().orElseThrow();
    }

    private static String question(AttributesReport report, int scenario, String name) {
        return report.scenarios().get(scenario).attributes().stream().filter(a -> a.name().equals(name))
                .map(Attribute::question).findFirst().orElseThrow();
    }

    private static String json(String stdout) {
        return stdout.substring(stdout.indexOf('{'), stdout.lastIndexOf('}') + 1);
    }

    @Test
    void printsMarkdownOfTheThreeFixtureScenarios(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes", ICOS.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("# Scenario attributes", "integrated-sync",
                "## IF_OrderQuery_to_RCV_SYS", "## IF_OrderIDoc_to_RCV_SYS_RCV2_SYS", "## IF_OrderBatch_to_RCV_SYS",
                "| attribute | value | source |", "### Questions", "### Modules",
                "receiver CC_RCV2_FILE #2 MyCustomModule", "sender CC_SND_SOAP #1 localejbs/MyPreModule");
    }

    @Test
    void printsJsonWithOneScenarioPerIco(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes", "--json", ICOS.toString());

        assertThat(result.exitCode()).isZero();
        AttributesReport report = Json.mapper().readValue(json(result.getOutput()), AttributesReport.class);
        assertThat(report.inputs()).containsExactly(ICOS.toString());
        assertThat(report.scenarios()).hasSize(3);
        assertThat(report.scenarios()).allSatisfy(s -> assertThat(s.attributes()).hasSize(11));

        // (a) sync BE, XI to XI, one 1:1 mapping, no modules
        assertThat(value(report, 0, "qos")).isEqualTo("BE");
        assertThat(value(report, 0, "runtime")).isEqualTo("integrated-sync");
        assertThat(value(report, 0, "sender_type")).isEqualTo("XI");
        assertThat(value(report, 0, "receiver_type")).isEqualTo("XI");
        assertThat(value(report, 0, "receiver_count")).isEqualTo("1");
        assertThat(value(report, 0, "receiver_determination")).isEqualTo("static");
        assertThat(value(report, 0, "mapping")).isEqualTo("one");
        assertThat(value(report, 0, "custom_exit")).isEqualTo("none");
        assertThat(value(report, 0, "inbound_conversion")).isEqualTo("none");
        // (b) async EO, IDoc sender, conditional second receiver, XI and File receivers, module after CallSapAdapter
        assertThat(value(report, 1, "qos")).isEqualTo("EO");
        assertThat(value(report, 1, "runtime")).isEqualTo("?");
        assertThat(value(report, 1, "sender_type")).isEqualTo("IDOC");
        assertThat(value(report, 1, "receiver_type")).isEqualTo("?");
        assertThat(value(report, 1, "receiver_count")).isEqualTo("n");
        assertThat(value(report, 1, "receiver_determination")).isEqualTo("xpath");
        assertThat(value(report, 1, "custom_exit")).isEqualTo("?");
        assertThat(question(report, 1, "custom_exit")).startsWith("receiver module MyCustomModule");
        assertThat(report.scenarios().get(1).modules())
                .containsExactly(new ModuleRef("receiver", "CC_RCV2_FILE", 2, "MyCustomModule"));
        // (c) async EO, SOAP sender with a module before CallSapAdapter, dynamic routing, 1:n of one interface
        assertThat(value(report, 2, "sender_type")).isEqualTo("OTHER");
        assertThat(value(report, 2, "receiver_count")).isEqualTo("n");
        assertThat(value(report, 2, "receiver_determination")).isEqualTo("reuse-xrd");
        assertThat(value(report, 2, "mapping")).isEqualTo("one-to-many-one-type");
        assertThat(value(report, 2, "custom_exit")).isEqualTo("?");
        assertThat(value(report, 2, "inbound_conversion")).isEqualTo("?");
        assertThat(question(report, 2, "custom_exit")).isEqualTo(question(report, 2, "inbound_conversion"))
                .startsWith("sender module localejbs/MyPreModule");
        assertThat(report.scenarios().get(2).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND_SOAP", 1, "localejbs/MyPreModule"));
    }

    @Test
    void theFixtureCarriesAnEsrSectionWithMappingProgramKinds() {
        IntegrationInventory inventory = Json.mapper().readerFor(IntegrationInventory.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(ICOS.toFile());

        Esr esr = inventory.esr();
        assertThat(esr).isNotNull();
        assertThat(esr.operationMappings()).extracting(m -> m.steps().get(0).program().type())
                .contains("MAP_ARCHIVE_PRG", "XI_TRAFO");
        assertThat(esr.importedArchives().get(0).programs().get(0).kind()).isEqualTo("javaMapping");
        // every mapping the ICOs reference (interface mappings and the dynamic-routing mapping) is in the section
        List<String> referenced = inventory.icos().stream()
                .flatMap(ico -> Stream.concat(
                        ico.receivers().stream().flatMap(r -> r.interfaces().stream()).map(a -> a.mapping()),
                        ico.dynamicRouting() == null ? Stream.empty() : ico.dynamicRouting().mappings().stream()))
                .map(ObjRef::name).toList();
        assertThat(esr.operationMappings()).extracting(m -> m.id().name())
                .containsExactlyInAnyOrderElementsOf(referenced);
    }

    /**
     * D-043 b: adapter settings of the fixture channels, as the ICO dump exports them (passwords never are). The keys
     * are synthetic placeholders ({@code synthetic.<setting>}) pending ISSUE-4, so values, not key names, are asserted.
     */
    @Test
    void everyFixtureChannelCarriesSyntheticAttributes() {
        IntegrationInventory inventory = Json.mapper().readerFor(IntegrationInventory.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(ICOS.toFile());

        assertThat(inventory.channels()).extracting(c -> c.id().name())
                .containsExactly("CC_SND_XI", "CC_SND_IDOC", "CC_SND_SOAP", "CC_RCV_XI", "CC_RCV2_FILE");
        assertThat(inventory.channels()).allSatisfy(c -> assertThat(c.attributes())
                .as("attributes of %s", c.id().name()).isNotEmpty().hasSizeBetween(2, 5));
        assertThat(attributes(inventory, "CC_SND_SOAP")).containsValue("/orderbatch/SND_SYS/inbound");
        assertThat(attributes(inventory, "CC_RCV2_FILE"))
                .containsValues("/out/orders", "OrderFile_${header.UniqueID}.xml");
        inventory.channels().forEach(c -> c.attributes().forEach((key, value) -> assertThat(key + "=" + value)
                .as("attribute of %s", c.id().name())
                .doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("secret")));
    }

    @Test
    void theFixtureMarkdownCarriesNoEsrDisagreement(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes", ICOS.toString());

        assertThat(result.exitCode()).isZero();
        // notes are rendered (scenario c's one-to-many note), and none of them is an esr disagreement
        assertThat(result.getOutput()).contains("ordered if PO keeps order at runtime (not in the dump)")
                .doesNotContain("esr: message mapping");
    }

    private static Map<String, String> attributes(IntegrationInventory inventory, String channel) {
        return inventory.channels().stream().filter(c -> c.id().name().equals(channel)).findFirst()
                .map(Channel::attributes).orElseThrow();
    }

    @Test
    void aMissingFileFailsAndNamesIt(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path missing = dir.resolve("absent-icos.json");

        LaunchResult result = launcher.launch("attributes", missing.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("absent-icos.json");
    }

    @Test
    void anUnparsableFileFailsAndNamesIt(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path broken = Files.writeString(dir.resolve("broken-icos.json"), "{ not json", StandardCharsets.UTF_8);

        LaunchResult result = launcher.launch("attributes", ICOS.toString(), broken.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("broken-icos.json");
    }

    @Test
    void withoutFilesItIsAUsageError(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getOutput()).doesNotContain("# Scenario attributes");
    }
}
